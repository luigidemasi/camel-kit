package io.github.luigidemasi.camelkit.ship.worker;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.luigidemasi.camelkit.ship.ShipDigest;
import io.github.luigidemasi.camelkit.ship.evidence.ShipLocalStamp;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Runs one bounded local command without a shell. */
final class LocalCommandRunner {

    private static final Duration TERMINATION_GRACE = Duration.ofMillis(500);
    private static final Duration TERMINATION_TIMEOUT = Duration.ofSeconds(5);
    private static final Path SETSID = Path.of("/usr/bin/setsid");
    private static final Path KILL = Path.of("/bin/kill");
    private static final byte[] OUTPUT_LIMIT_LOG
            = "<output-limit-exceeded>\n".getBytes(StandardCharsets.UTF_8);
    private static final Pattern SENSITIVE_ASSIGNMENT = Pattern.compile(
            "(?i)(docker[-_]?auth[-_]?config|npm[-_]?config[-_]*auth"
                                                                        + "|password|passwd|pass|passphrase|pwd"
                                                                        + "|secret|token|credentials?|auth(?:orization)?"
                                                                        + "|api[-_]?key"
                                                                        + "|access[-_]?key(?:[-_]?id)?|private[-_]?key|jwt|pat)"
                                                                        + "([\"']?\\s*[:=]\\s*)"
                                                                        + "(\"[^\"\\r\\n]*\"|'[^'\\r\\n]*'|[^\"'\\r\\n,}]+)");
    private static final Pattern AUTHORIZATION_SCHEME = Pattern.compile(
            "(?i)[\"']?\\b(Bearer|Basic)\\s+[\"']?([^\\s\"',}]+)[\"']?");
    private static final Pattern URL_USERINFO = Pattern.compile(
            "(?<=://)([^/@\\s]*)@");
    private static final ObjectMapper JSON = new ObjectMapper();

    private final Clock clock;
    private final Map<String, String> environment;

    LocalCommandRunner() {
        this(Clock.systemUTC());
    }

    LocalCommandRunner(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.environment = null;
    }

    LocalCommandRunner(Clock clock, Map<String, String> environment) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.environment = Map.copyOf(
                Objects.requireNonNull(environment, "environment"));
    }

    Result run(Command command) throws IOException, InterruptedException {
        Objects.requireNonNull(command, "command");
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("Local command invocation was already interrupted");
        }
        Path executable = realExecutable(command.executable());
        Path setsid = trustedHelper(SETSID, "setsid");
        Path kill = trustedHelper(KILL, "kill");
        Path workingDirectory = realDirectory(command.workingDirectory(), "working directory");
        Path evidenceDirectory = realDirectory(command.evidenceDirectory(), "evidence directory");
        if (workingDirectory.startsWith(evidenceDirectory)
                || evidenceDirectory.startsWith(workingDirectory)) {
            throw new IOException(
                    "Local command working and evidence directories must be disjoint");
        }
        List<String> arguments = command.arguments();
        List<String> vector = new ArrayList<>(arguments.size() + 4);
        vector.add(setsid.toString());
        vector.add("--wait");
        vector.add("--");
        vector.add(executable.toString());
        vector.addAll(arguments);

        Process process = null;
        Thread shutdownHook = null;
        ExecutorService pumps = Executors.newFixedThreadPool(2, task -> {
            Thread thread = new Thread(task, "camel-kit-local-command-io");
            thread.setDaemon(true);
            return thread;
        });
        Future<byte[]> stdoutPump = null;
        Future<byte[]> stderrPump = null;
        Instant startedAt = clock.instant();
        long deadline = deadline(command.timeout());
        boolean interrupted = false;
        AtomicBoolean outputLimited = new AtomicBoolean();
        try {
            ProcessBuilder builder = new ProcessBuilder(vector)
                    .directory(workingDirectory.toFile());
            if (environment != null) {
                builder.environment().clear();
                builder.environment().putAll(environment);
            }
            process = builder.start();
            Process launched = process;
            launched.getOutputStream().close();
            stdoutPump = pumps.submit(() -> readBounded(
                    launched.getInputStream(),
                    command.maximumOutputBytesPerStream(),
                    outputLimited));
            stderrPump = pumps.submit(() -> readBounded(
                    launched.getErrorStream(),
                    command.maximumOutputBytesPerStream(),
                    outputLimited));
            shutdownHook = new Thread(
                    () -> terminateProcessGroup(launched, kill),
                    "camel-kit-local-command-shutdown");
            Runtime.getRuntime().addShutdownHook(shutdownHook);

            boolean timedOut = false;
            while (true) {
                if (outputLimited.get()) {
                    if (!terminateProcessGroup(process, kill)) {
                        throw new IOException(
                                "Local command exceeded its output limit and its process group could not be reaped");
                    }
                    break;
                }
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    timedOut = true;
                    if (!terminateProcessGroup(process, kill)) {
                        throw new IOException(
                                "Local command timed out and its process group could not be reaped");
                    }
                    break;
                }
                if (process.waitFor(
                        Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(100)),
                        TimeUnit.NANOSECONDS)) {
                    break;
                }
            }

            if (!terminateProcessGroup(process, kill)) {
                throw new IOException("Local command process group could not be reaped");
            }
            Integer exitCode = timedOut ? null : process.exitValue();
            Instant endedAt = clock.instant();
            if (endedAt.isBefore(startedAt)) {
                endedAt = startedAt;
            }
            byte[] capturedStdout = awaitPump(stdoutPump);
            byte[] capturedStderr = awaitPump(stderrPump);
            List<String> secrets = secretValues(command.sensitiveValues());
            byte[] stdoutEvidence = outputLimited.get()
                    ? OUTPUT_LIMIT_LOG
                    : capturedStdout;
            byte[] stderrEvidence = outputLimited.get()
                    ? OUTPUT_LIMIT_LOG
                    : capturedStderr;
            RetainedLog stdout = retain(
                    stdoutEvidence,
                    evidenceDirectory,
                    ".stdout.log",
                    command.maximumOutputBytesPerStream(),
                    secrets);
            RetainedLog stderr;
            try {
                stderr = retain(
                        stderrEvidence,
                        evidenceDirectory,
                        ".stderr.log",
                        command.maximumOutputBytesPerStream(),
                        secrets);
            } catch (IOException | RuntimeException e) {
                Files.deleteIfExists(stdout.path());
                throw e;
            }
            return new Result(
                    executable,
                    redactArguments(arguments, secrets),
                    workingDirectory,
                    startedAt,
                    endedAt,
                    timedOut,
                    outputLimited.get(),
                    exitCode,
                    capturedStdout,
                    stdout.path(),
                    stdout.digest(),
                    stderr.path(),
                    stderr.digest());
        } catch (InterruptedException e) {
            interrupted = true;
            if (process != null && !terminateProcessGroup(process, kill)) {
                e.addSuppressed(new IOException("Interrupted local command process group could not be reaped"));
            }
            throw e;
        } catch (IOException | RuntimeException e) {
            if (process != null && !terminateProcessGroup(process, kill)) {
                e.addSuppressed(new IOException("Failed local command process group could not be reaped"));
            }
            throw e;
        } finally {
            if (shutdownHook != null) {
                try {
                    Runtime.getRuntime().removeShutdownHook(shutdownHook);
                } catch (IllegalStateException ignored) {
                    // JVM shutdown already owns the hook.
                }
            }
            closeProcessStreams(process);
            if (stdoutPump != null && !stdoutPump.isDone()) {
                stdoutPump.cancel(true);
            }
            if (stderrPump != null && !stderrPump.isDone()) {
                stderrPump.cancel(true);
            }
            shutdownPumps(pumps);
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    static RetainedLog retain(
            byte[] captured,
            Path evidenceDirectory,
            String suffix,
            int maximumOutputBytes,
            List<String> secrets)
            throws IOException {
        byte[] retained = redactBounded(
                new String(captured, StandardCharsets.UTF_8),
                secrets,
                maximumOutputBytes);
        Path log = Files.createTempFile(
                evidenceDirectory, "local-command-", suffix, fileAttributes(evidenceDirectory));
        try {
            Files.write(
                    log,
                    retained,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE,
                    LinkOption.NOFOLLOW_LINKS);
            return new RetainedLog(log, ShipDigest.sha256(retained), retained.length);
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(log);
            throw e;
        }
    }

    private static byte[] readBounded(
            InputStream input, int maximumOutputBytes, AtomicBoolean limited)
            throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(
                Math.min(maximumOutputBytes, 8192));
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) {
            int remaining = maximumOutputBytes - output.size();
            if (remaining > 0) {
                output.write(buffer, 0, Math.min(remaining, read));
            }
            if (read > remaining) {
                limited.set(true);
            }
        }
        return output.toByteArray();
    }

    private static byte[] awaitPump(Future<byte[]> pump)
            throws IOException, InterruptedException {
        try {
            return pump.get(TERMINATION_TIMEOUT.toNanos(), TimeUnit.NANOSECONDS);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException io) {
                throw io;
            }
            throw new IOException("Could not retain local command output", cause);
        } catch (TimeoutException e) {
            throw new IOException(
                    "Local command output stream did not close after process termination",
                    e);
        }
    }

    static List<String> redactArguments(
            List<String> arguments, List<String> secrets) {
        return arguments.stream().map(argument -> redact(argument, secrets)).toList();
    }

    private static String redact(String value, List<String> secrets) {
        return redact(value, secrets, Integer.MAX_VALUE);
    }

    private static String redact(
            String value, List<String> secrets, int maximumCharacters) {
        List<String> literals = secretRepresentations(secrets);
        Pattern literalPattern = null;
        String expression = "(?:" + SENSITIVE_ASSIGNMENT.pattern() + ')'
                            + "|(?:" + AUTHORIZATION_SCHEME.pattern() + ')'
                            + "|(?:" + URL_USERINFO.pattern() + ')';
        if (!literals.isEmpty()) {
            String literalExpression = String.join(
                    "|", literals.stream().map(Pattern::quote).toList());
            literalPattern = Pattern.compile(literalExpression);
            expression += "|(?:" + literalExpression + ')';
        }
        Matcher search = Pattern.compile("(?=(?:" + expression + "))").matcher(value);

        StringBuilder redacted = new StringBuilder(
                Math.min(value.length(), maximumCharacters));
        int position = 0;
        while (search.find(position)) {
            RedactionMatch match = redactionAt(
                    value, search.start(), literalPattern);
            int candidateStart = match.start() + 1;
            while (search.find(candidateStart)) {
                RedactionMatch candidate = redactionAt(
                        value, search.start(), literalPattern);
                if (candidate.start() >= match.end()) {
                    break;
                }
                match = new RedactionMatch(
                        match.start(),
                        Math.max(match.end(), candidate.end()),
                        match.replacement());
                candidateStart = candidate.start() + 1;
            }

            int remaining = maximumCharacters - redacted.length();
            int unchanged = match.start() - position;
            if (unchanged > remaining) {
                appendPrefix(redacted, value, position, remaining);
                return redacted.toString();
            }
            redacted.append(value, position, match.start());
            if (match.replacement().length()
                > maximumCharacters - redacted.length()) {
                return redacted.toString();
            }
            redacted.append(match.replacement());
            position = match.end();
        }
        appendPrefix(
                redacted,
                value,
                position,
                maximumCharacters - redacted.length());
        return redacted.toString();
    }

    private static byte[] redactBounded(
            String value, List<String> secrets, int maximumBytes) {
        String result = redact(value, secrets, maximumBytes);
        byte[] encoded = result.getBytes(StandardCharsets.UTF_8);
        if (encoded.length <= maximumBytes) {
            return encoded;
        }
        int end = maximumBytes;
        while (end > 0 && (encoded[end] & 0xc0) == 0x80) {
            end--;
        }
        String prefix = new String(encoded, 0, end, StandardCharsets.UTF_8);
        for (int length = 1; length < ShipLocalStamp.REDACTED.length(); length++) {
            if (prefix.endsWith(ShipLocalStamp.REDACTED.substring(0, length))) {
                prefix = prefix.substring(0, prefix.length() - length);
                break;
            }
        }
        return prefix.getBytes(StandardCharsets.UTF_8);
    }

    private static RedactionMatch redactionAt(
            String value, int start, Pattern literalPattern) {
        int end = start;
        String replacement = null;

        Matcher assignment = at(SENSITIVE_ASSIGNMENT, value, start);
        if (assignment.lookingAt()) {
            end = assignment.end();
            replacement = redactedAssignment(assignment);
        }
        Matcher authorization = at(AUTHORIZATION_SCHEME, value, start);
        if (authorization.lookingAt()) {
            end = Math.max(end, authorization.end());
            if (replacement == null) {
                replacement = authorization.group(1) + " " + ShipLocalStamp.REDACTED;
            }
        }
        Matcher url = at(URL_USERINFO, value, start);
        if (url.lookingAt()) {
            end = Math.max(end, url.end());
            if (replacement == null) {
                replacement = ShipLocalStamp.REDACTED + "@";
            }
        }
        if (literalPattern != null) {
            Matcher literal = at(literalPattern, value, start);
            if (literal.lookingAt()) {
                end = Math.max(end, literal.end());
                if (replacement == null) {
                    replacement = ShipLocalStamp.REDACTED;
                }
            }
        }
        if (literalPattern != null
                && !ShipLocalStamp.REDACTED.equals(replacement)
                && literalPattern.matcher(replacement).find()) {
            replacement = ShipLocalStamp.REDACTED;
        }
        return new RedactionMatch(start, end, replacement);
    }

    private static Matcher at(Pattern pattern, String value, int start) {
        return pattern.matcher(value)
                .region(start, value.length())
                .useTransparentBounds(true);
    }

    private static void appendPrefix(
            StringBuilder target, String value, int start, int maximumCharacters) {
        int end = start + Math.min(maximumCharacters, value.length() - start);
        if (end > start
                && end < value.length()
                && Character.isHighSurrogate(value.charAt(end - 1))
                && Character.isLowSurrogate(value.charAt(end))) {
            end--;
        }
        target.append(value, start, end);
    }

    private static String redactedAssignment(MatchResult match) {
        String supplied = match.group(3);
        String value = unquote(supplied);
        String quote = value.equals(supplied) ? "" : supplied.substring(0, 1);
        return match.group(1) + match.group(2)
               + quote + ShipLocalStamp.REDACTED + quote;
    }

    private static List<String> secretRepresentations(List<String> secrets) {
        List<String> literals = new ArrayList<>();
        for (String secret : secrets) {
            if (secret == null || secret.isEmpty()) {
                continue;
            }
            literals.add(secret);
            try {
                String encoded = JSON.writeValueAsString(secret);
                literals.add(encoded.substring(1, encoded.length() - 1));
            } catch (IOException ignored) {
                // The unescaped representation remains protected.
            }
        }
        return literals.stream()
                .filter(value -> !value.isEmpty())
                .distinct()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();
    }

    private static List<String> secretValues(List<String> knownValues) {
        List<String> values = new ArrayList<>();
        knownValues.forEach(value -> addSecretValue(values, value, true));
        System.getenv().entrySet().stream()
                .filter(entry -> isSensitiveEnvironmentValue(
                        entry.getKey(), entry.getValue()))
                .map(Map.Entry::getValue)
                .forEach(value -> addSecretValue(values, value, true));
        return values.stream()
                .distinct()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();
    }

    static boolean isSensitiveEnvironmentValue(String name, String value) {
        if (!ShipLocalStamp.isSensitiveEnvironmentName(name)
                || value == null
                || value.isEmpty()) {
            return false;
        }
        String normalizedName = name.toUpperCase(Locale.ROOT);
        boolean featureToggle = normalizedName.startsWith("ENABLE_")
                || normalizedName.startsWith("DISABLE_")
                || normalizedName.startsWith("USE_")
                || normalizedName.endsWith("_ENABLED")
                || normalizedName.endsWith("_DISABLED");
        if (!featureToggle) {
            return true;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "0", "1", "false", "true", "no", "yes", "off", "on" -> false;
            default -> true;
        };
    }

    private static void addMatches(List<String> values, Matcher matcher, int group) {
        while (matcher.find()) {
            String value = matcher.group(group);
            if (!value.isEmpty() && !ShipLocalStamp.REDACTED.equals(value)) {
                addSecretValue(values, value, false);
            }
        }
    }

    private static void addUrlMatches(List<String> values, Matcher matcher) {
        while (matcher.find()) {
            String userInfo = matcher.group(1);
            addSecretValue(values, userInfo, false);
            int colon = userInfo.indexOf(':');
            if (colon >= 0) {
                addSecretValue(values, userInfo.substring(colon + 1), false);
            }
        }
    }

    private static void addSecretValue(
            List<String> values, String supplied, boolean structured) {
        if (supplied == null || supplied.isEmpty() || ShipLocalStamp.REDACTED.equals(supplied)) {
            return;
        }
        values.add(supplied);
        String value = unquote(supplied.trim());
        if (!value.equals(supplied.trim())) {
            if (!value.isEmpty()) {
                values.add(value);
            }
        }
        addMatches(values, SENSITIVE_ASSIGNMENT.matcher(value), 3);
        addMatches(values, AUTHORIZATION_SCHEME.matcher(value), 2);
        addUrlMatches(values, URL_USERINFO.matcher(value));
        if (structured) {
            try {
                collectJsonValues(JSON.readTree(value), values);
            } catch (IOException ignored) {
                // Non-JSON credentials remain protected by their exact representation.
            }
        }
    }

    private static void collectJsonValues(
            com.fasterxml.jackson.databind.JsonNode node, List<String> values) {
        if (node == null) {
            return;
        }
        if (node.isTextual()) {
            addSecretValue(values, node.textValue(), false);
        } else if (node.isContainerNode()) {
            node.elements().forEachRemaining(child -> collectJsonValues(child, values));
        }
    }

    private static String unquote(String value) {
        return value.length() >= 2
                && (value.charAt(0) == '"' || value.charAt(0) == '\'')
                && value.charAt(value.length() - 1) == value.charAt(0)
                        ? value.substring(1, value.length() - 1)
                        : value;
    }

    private static Path realExecutable(Path supplied) throws IOException {
        Path executable = Objects.requireNonNull(supplied, "executable").toRealPath();
        if (!Files.isRegularFile(executable, LinkOption.NOFOLLOW_LINKS)
                || !Files.isExecutable(executable)) {
            throw new IOException("Local command executable is missing or not executable");
        }
        return executable;
    }

    static Path trustedHelper(Path path, String name) throws IOException {
        try {
            return realExecutable(path);
        } catch (IOException | RuntimeException e) {
            throw new IOException(
                    "Required Linux process-group helper `" + name + "` is unavailable",
                    e);
        }
    }

    private static Path realDirectory(Path supplied, String label) throws IOException {
        if (supplied == null) {
            throw new IOException("Local command " + label + " is required");
        }
        Path normalized = supplied.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(normalized)
                || !Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Local command " + label + " must be a real directory");
        }
        return normalized.toRealPath();
    }

    private static long deadline(Duration duration) {
        try {
            return Math.addExact(System.nanoTime(), duration.toNanos());
        } catch (ArithmeticException e) {
            return Long.MAX_VALUE;
        }
    }

    static boolean terminateProcessGroup(Process process, Path kill) {
        long groupId = process.pid();
        process.toHandle().destroy();
        signalProcessGroup(kill, "TERM", groupId);
        if (awaitProcessGroup(process, kill, groupId, TERMINATION_GRACE)) {
            return true;
        }
        if (process.isAlive()) {
            process.toHandle().destroyForcibly();
        }
        signalProcessGroup(kill, "KILL", groupId);
        return awaitProcessGroup(process, kill, groupId, TERMINATION_TIMEOUT);
    }

    private static boolean awaitProcessGroup(
            Process process, Path kill, long groupId, Duration timeout) {
        long deadline = deadline(timeout);
        boolean interrupted = false;
        try {
            while (process.isAlive() || processGroupExists(kill, groupId)) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    return false;
                }
                try {
                    if (process.isAlive()) {
                        process.waitFor(
                                Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(25)),
                                TimeUnit.NANOSECONDS);
                    } else {
                        TimeUnit.NANOSECONDS.sleep(
                                Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(25)));
                    }
                } catch (InterruptedException e) {
                    interrupted = true;
                    Thread.interrupted();
                }
            }
            return true;
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static boolean processGroupExists(Path kill, long groupId) {
        Integer result = signalProcessGroup(kill, "0", groupId);
        return result == null || result == 0;
    }

    private static Integer signalProcessGroup(Path kill, String signal, long groupId) {
        Process helper = null;
        boolean interrupted = false;
        try {
            helper = new ProcessBuilder(
                    kill.toString(),
                    "--signal",
                    signal,
                    "--",
                    "-" + groupId)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            helper.getOutputStream().close();
            long deadline = deadline(TERMINATION_GRACE);
            while (helper.isAlive()) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    return null;
                }
                try {
                    helper.waitFor(remaining, TimeUnit.NANOSECONDS);
                } catch (InterruptedException e) {
                    interrupted = true;
                    Thread.interrupted();
                }
            }
            return helper.exitValue();
        } catch (IOException | RuntimeException e) {
            return null;
        } finally {
            if (helper != null && helper.isAlive()) {
                helper.destroyForcibly();
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void closeProcessStreams(Process process) {
        if (process == null) {
            return;
        }
        try {
            process.getOutputStream().close();
        } catch (IOException ignored) {
            // Best-effort cleanup after command completion or termination.
        }
        try {
            process.getInputStream().close();
        } catch (IOException ignored) {
            // Best-effort cleanup after command completion or termination.
        }
        try {
            process.getErrorStream().close();
        } catch (IOException ignored) {
            // Best-effort cleanup after command completion or termination.
        }
    }

    private static void shutdownPumps(ExecutorService pumps) {
        pumps.shutdownNow();
        boolean interrupted = false;
        long deadline = deadline(TERMINATION_GRACE);
        try {
            while (!pumps.isTerminated()) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    return;
                }
                try {
                    if (!pumps.awaitTermination(remaining, TimeUnit.NANOSECONDS)) {
                        return;
                    }
                } catch (InterruptedException e) {
                    interrupted = true;
                    Thread.interrupted();
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static FileAttribute<?>[] fileAttributes(Path path) {
        return path.getFileSystem().supportedFileAttributeViews().contains("posix")
                ? new FileAttribute<?>[]{
                        PosixFilePermissions.asFileAttribute(
                                PosixFilePermissions.fromString("rw-------"))}
                : new FileAttribute<?>[0];
    }

    record Command(
            Path executable,
            List<String> arguments,
            Path workingDirectory,
            Path evidenceDirectory,
            Duration timeout,
            int maximumOutputBytesPerStream,
            List<String> sensitiveValues) {

        Command(
                Path executable,
                List<String> arguments,
                Path workingDirectory,
                Path evidenceDirectory,
                Duration timeout,
                int maximumOutputBytesPerStream) {
            this(
                 executable,
                 arguments,
                 workingDirectory,
                 evidenceDirectory,
                 timeout,
                 maximumOutputBytesPerStream,
                 List.of());
        }

        Command {
            Objects.requireNonNull(executable, "executable");
            arguments = arguments == null ? List.of() : List.copyOf(arguments);
            if (arguments.stream().anyMatch(
                    argument -> argument == null || argument.indexOf('\0') >= 0)) {
                throw new IllegalArgumentException("Local command arguments are invalid");
            }
            Objects.requireNonNull(workingDirectory, "workingDirectory");
            Objects.requireNonNull(evidenceDirectory, "evidenceDirectory");
            if (timeout == null || timeout.isZero() || timeout.isNegative()) {
                throw new IllegalArgumentException("Local command timeout must be positive");
            }
            if (maximumOutputBytesPerStream <= 0) {
                throw new IllegalArgumentException("Local command output limit must be positive");
            }
            if (sensitiveValues != null
                    && sensitiveValues.stream().anyMatch(value -> value == null)) {
                throw new IllegalArgumentException(
                        "Local command sensitive values are invalid");
            }
            sensitiveValues = sensitiveValues == null
                    ? List.of()
                    : List.copyOf(sensitiveValues);
        }

        @Override
        public String toString() {
            return "Command[executable=" + executable
                   + ", arguments=<redacted>"
                   + ", workingDirectory=" + workingDirectory
                   + ", evidenceDirectory=" + evidenceDirectory
                   + ", timeout=" + timeout
                   + ", maximumOutputBytesPerStream=" + maximumOutputBytesPerStream + "]";
        }
    }

    record Result(
            Path executable,
            List<String> redactedArguments,
            Path workingDirectory,
            Instant startedAt,
            Instant endedAt,
            boolean timedOut,
            boolean outputLimited,
            Integer exitCode,
            byte[] capturedStdout,
            Path stdoutLog,
            String stdoutDigest,
            Path stderrLog,
            String stderrDigest) {

        Result {
            Objects.requireNonNull(executable, "executable");
            redactedArguments = List.copyOf(redactedArguments);
            Objects.requireNonNull(workingDirectory, "workingDirectory");
            Objects.requireNonNull(startedAt, "startedAt");
            Objects.requireNonNull(endedAt, "endedAt");
            capturedStdout = Objects.requireNonNull(
                    capturedStdout, "capturedStdout").clone();
            Objects.requireNonNull(stdoutLog, "stdoutLog");
            Objects.requireNonNull(stdoutDigest, "stdoutDigest");
            Objects.requireNonNull(stderrLog, "stderrLog");
            Objects.requireNonNull(stderrDigest, "stderrDigest");
            if (timedOut != (exitCode == null)) {
                throw new IllegalArgumentException(
                        "Only a timed-out local command can omit its exit code");
            }
        }

        @Override
        public byte[] capturedStdout() {
            return capturedStdout.clone();
        }

        @Override
        public String toString() {
            return "Result[executable=" + executable
                   + ", redactedArguments=" + redactedArguments
                   + ", workingDirectory=" + workingDirectory
                   + ", startedAt=" + startedAt
                   + ", endedAt=" + endedAt
                   + ", timedOut=" + timedOut
                   + ", outputLimited=" + outputLimited
                   + ", exitCode=" + exitCode
                   + ", stdoutLog=" + stdoutLog
                   + ", stdoutDigest=" + stdoutDigest
                   + ", stderrLog=" + stderrLog
                   + ", stderrDigest=" + stderrDigest + "]";
        }

        void deleteLogs() throws IOException {
            IOException failure = null;
            try {
                Files.deleteIfExists(stdoutLog);
            } catch (IOException e) {
                failure = e;
            }
            try {
                Files.deleteIfExists(stderrLog);
            } catch (IOException e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
            if (failure != null) {
                throw failure;
            }
        }
    }

    record RetainedLog(Path path, String digest, long size) {
    }

    private record RedactionMatch(int start, int end, String replacement) {
    }

}
