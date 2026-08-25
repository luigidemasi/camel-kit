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
import java.util.PriorityQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private static final String SENSITIVE_NAME_EXPRESSION
            = "docker[-_]?auth[-_]?config|npm[-_]?config[-_]*auth"
              + "|password|passwd|pass|passphrase|pwd"
              + "|secret|token|credentials?|auth(?:orization)?"
              + "|api[-_]?key"
              + "|access[-_]?key(?:[-_]?id)?|private[-_]?key|jwt|pat";
    private static final Pattern SENSITIVE_ASSIGNMENT = Pattern.compile(
            "(?i)(" + SENSITIVE_NAME_EXPRESSION + ")"
                                                                        + "([\"']?\\s*[:=]\\s*)"
                                                                        + "(\"[^\"\\r\\n]*\"|'[^'\\r\\n]*'|[^\"'\\r\\n,}]+)");
    private static final Pattern SENSITIVE_ASSIGNMENT_PREFIX = Pattern.compile(
            "(?i)(" + SENSITIVE_NAME_EXPRESSION + ")"
                                                                               + "([\"']?\\s*[:=]\\s*)(?=[^\\r\\n,}])");
    private static final Pattern AUTHORIZATION_SCHEME = Pattern.compile(
            "(?i)[\"']?\\b(Bearer|Basic)\\s+[\"']?([^\\s\"',}]+)[\"']?");
    private static final Pattern AUTHORIZATION_PREFIX = Pattern.compile(
            "(?i)[\"']?\\b(Bearer|Basic)\\s+[\"']?");
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
        List<String> redacted = arguments.stream()
                .map(argument -> redact(argument, secrets))
                .toList();
        return ShipLocalStamp.redactSensitiveArguments(arguments, redacted);
    }

    private static String redact(String value, List<String> secrets) {
        StringBuilder result = new StringBuilder(value.length());
        redact(value, secrets, (part, start, end, atomic) -> {
            result.append(part, start, end);
            return true;
        });
        return result.toString();
    }

    private static byte[] redactBounded(
            String value, List<String> secrets, int maximumBytes) {
        BoundedUtf8Output result = new BoundedUtf8Output(maximumBytes);
        redact(value, secrets, result::append);
        return result.toByteArray();
    }

    private static void redact(
            String value, List<String> secrets, RedactionSink output) {
        RedactionCursor cursor = new RedactionCursor(
                value, secretRepresentations(secrets));
        int offset = 0;
        while (offset < value.length()) {
            RedactionMatch match = cursor.next(offset);
            int unchangedEnd = match == null ? value.length() : match.start();
            if (!output.append(value, offset, unchangedEnd, false) || match == null) {
                return;
            }
            String replacement = match.replacement();
            if (!output.append(replacement, 0, replacement.length(), true)) {
                return;
            }
            offset = match.end();
        }
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

    private record CursorMatch(int source, RedactionMatch match) {
    }

    private static final class RedactionCursor {

        private static final int ASSIGNMENT_SOURCE = 0;
        private static final int AUTHORIZATION_SOURCE = 1;
        private static final int URL_SOURCE = 2;
        private static final int LITERAL_SOURCE = 3;

        private final String value;
        private final List<String> literals;
        private final PriorityQueue<LiteralCursor> literalMatches;
        private final Matcher assignment;
        private final Matcher authorization;
        private final Matcher url;
        private RedactionMatch assignmentMatch;
        private RedactionMatch authorizationMatch;
        private RedactionMatch urlMatch;
        private int assignmentBoundary = -1;
        private int authorizationBoundary = -1;

        private RedactionCursor(String value, List<String> literals) {
            this.value = value;
            this.literals = literals;
            literalMatches = new PriorityQueue<>(
                    Comparator.comparingInt(LiteralCursor::start)
                            .thenComparingInt(LiteralCursor::priority));
            for (int index = 0; index < literals.size(); index++) {
                LiteralCursor literal = new LiteralCursor(
                        value, literals.get(index), index);
                literal.advance();
                if (literal.start() >= 0) {
                    literalMatches.add(literal);
                }
            }
            assignment = SENSITIVE_ASSIGNMENT_PREFIX.matcher(value);
            authorization = AUTHORIZATION_PREFIX.matcher(value);
            url = URL_USERINFO.matcher(value);
            advanceAssignment();
            advanceAuthorization();
            advanceUrl();
        }

        private RedactionMatch next(int offset) {
            advanceTo(offset);
            CursorMatch selected = earliest();
            if (selected == null) {
                return null;
            }
            RedactionMatch result = selected.match();
            advance(selected.source());
            while (result.end() < value.length()) {
                selected = earliest();
                if (selected == null || selected.match().start() >= result.end()) {
                    break;
                }
                RedactionMatch candidate = selected.match();
                result = new RedactionMatch(
                        result.start(),
                        Math.max(result.end(), candidate.end()),
                        result.replacement());
                advance(selected.source());
            }
            if (!ShipLocalStamp.REDACTED.equals(result.replacement())
                    && literals.stream().anyMatch(result.replacement()::contains)) {
                result = new RedactionMatch(
                        result.start(), result.end(), ShipLocalStamp.REDACTED);
            }
            return result;
        }

        private void advanceTo(int offset) {
            while (assignmentMatch != null && assignmentMatch.start() < offset) {
                advanceAssignment();
            }
            while (authorizationMatch != null
                    && authorizationMatch.start() < offset) {
                advanceAuthorization();
            }
            while (urlMatch != null && urlMatch.start() < offset) {
                advanceUrl();
            }
            while (!literalMatches.isEmpty()
                    && literalMatches.peek().start() < offset) {
                LiteralCursor literal = literalMatches.remove();
                do {
                    literal.advance();
                } while (literal.start() >= 0 && literal.start() < offset);
                if (literal.start() >= 0) {
                    literalMatches.add(literal);
                }
            }
        }

        private CursorMatch earliest() {
            CursorMatch result = candidate(ASSIGNMENT_SOURCE, assignmentMatch);
            result = earlier(
                    result,
                    candidate(AUTHORIZATION_SOURCE, authorizationMatch));
            result = earlier(result, candidate(URL_SOURCE, urlMatch));
            LiteralCursor literal = literalMatches.peek();
            if (literal != null) {
                result = earlier(
                        result,
                        candidate(
                                LITERAL_SOURCE + literal.priority(),
                                new RedactionMatch(
                                        literal.start(),
                                        literal.start() + literal.length(),
                                        ShipLocalStamp.REDACTED)));
            }
            return result;
        }

        private static CursorMatch candidate(
                int source, RedactionMatch match) {
            return match == null ? null : new CursorMatch(source, match);
        }

        private static CursorMatch earlier(
                CursorMatch current, CursorMatch candidate) {
            if (current == null) {
                return candidate;
            }
            if (candidate == null) {
                return current;
            }
            int currentStart = current.match().start();
            int candidateStart = candidate.match().start();
            return candidateStart < currentStart
                    || (candidateStart == currentStart
                            && candidate.source() < current.source())
                                    ? candidate
                                    : current;
        }

        private void advance(int source) {
            switch (source) {
                case ASSIGNMENT_SOURCE -> advanceAssignment();
                case AUTHORIZATION_SOURCE -> advanceAuthorization();
                case URL_SOURCE -> advanceUrl();
                default -> advanceLiteral(source - LITERAL_SOURCE);
            }
        }

        private void advanceAssignment() {
            assignmentMatch = null;
            while (assignment.find()) {
                int valueStart = assignment.end();
                int end = assignmentEnd(valueStart);
                if (end <= valueStart) {
                    continue;
                }
                String quote = isQuote(value.charAt(valueStart))
                        ? value.substring(valueStart, valueStart + 1)
                        : "";
                assignmentMatch = new RedactionMatch(
                        assignment.start(),
                        end,
                        assignment.group(1) + assignment.group(2)
                             + quote + ShipLocalStamp.REDACTED + quote);
                return;
            }
        }

        private int assignmentEnd(int start) {
            if (start >= value.length()) {
                return -1;
            }
            char first = value.charAt(start);
            if (isQuote(first)) {
                for (int index = start + 1; index < value.length(); index++) {
                    char current = value.charAt(index);
                    if (current == '\r' || current == '\n') {
                        return -1;
                    }
                    if (current == first) {
                        return index + 1;
                    }
                }
                return -1;
            }
            if (start < assignmentBoundary) {
                return assignmentBoundary;
            }
            int end = start;
            while (end < value.length()
                    && !isAssignmentDelimiter(value.charAt(end))) {
                end++;
            }
            assignmentBoundary = end;
            return end;
        }

        private void advanceAuthorization() {
            authorizationMatch = null;
            while (authorization.find()) {
                int credentialStart = authorization.end();
                int end = authorizationEnd(credentialStart);
                if (end <= credentialStart) {
                    continue;
                }
                authorizationMatch = new RedactionMatch(
                        authorization.start(),
                        end,
                        authorization.group(1) + " " + ShipLocalStamp.REDACTED);
                return;
            }
        }

        private int authorizationEnd(int start) {
            if (start >= value.length()) {
                return -1;
            }
            int end;
            if (start < authorizationBoundary) {
                end = authorizationBoundary;
            } else {
                end = start;
                while (end < value.length()
                        && !isAuthorizationDelimiter(value.charAt(end))) {
                    end++;
                }
                authorizationBoundary = end;
            }
            if (end == start) {
                return -1;
            }
            return end < value.length() && isQuote(value.charAt(end))
                    ? end + 1
                    : end;
        }

        private void advanceUrl() {
            urlMatch = url.find()
                    ? new RedactionMatch(
                            url.start(),
                            url.end(),
                            ShipLocalStamp.REDACTED + "@")
                    : null;
        }

        private void advanceLiteral(int priority) {
            LiteralCursor literal = literalMatches.poll();
            if (literal == null || literal.priority() != priority) {
                throw new IllegalStateException(
                        "Literal redaction cursor is inconsistent");
            }
            literal.advance();
            if (literal.start() >= 0) {
                literalMatches.add(literal);
            }
        }

        private static boolean isQuote(char value) {
            return value == '\'' || value == '"';
        }

        private static boolean isAssignmentDelimiter(char value) {
            return isQuote(value)
                    || value == '\r'
                    || value == '\n'
                    || value == ','
                    || value == '}';
        }

        private static boolean isAuthorizationDelimiter(char value) {
            return value == ' '
                    || value == '\t'
                    || value == '\n'
                    || value == '\u000b'
                    || value == '\f'
                    || value == '\r'
                    || isQuote(value)
                    || value == ','
                    || value == '}';
        }

        private static final class LiteralCursor {

            private final String value;
            private final String literal;
            private final int priority;
            private final int[] failure;
            private int offset;
            private int matched;
            private int start = -1;

            private LiteralCursor(
                                  String value, String literal, int priority) {
                this.value = value;
                this.literal = literal;
                this.priority = priority;
                failure = failureTable(literal);
            }

            private void advance() {
                start = -1;
                while (offset < value.length()) {
                    char current = value.charAt(offset++);
                    while (matched > 0
                            && literal.charAt(matched) != current) {
                        matched = failure[matched - 1];
                    }
                    if (literal.charAt(matched) == current) {
                        matched++;
                    }
                    if (matched == literal.length()) {
                        start = offset - literal.length();
                        matched = failure[matched - 1];
                        return;
                    }
                }
            }

            private int start() {
                return start;
            }

            private int priority() {
                return priority;
            }

            private int length() {
                return literal.length();
            }

            private static int[] failureTable(String literal) {
                int[] result = new int[literal.length()];
                int matched = 0;
                for (int index = 1; index < literal.length(); index++) {
                    char current = literal.charAt(index);
                    while (matched > 0
                            && literal.charAt(matched) != current) {
                        matched = result[matched - 1];
                    }
                    if (literal.charAt(matched) == current) {
                        matched++;
                    }
                    result[index] = matched;
                }
                return result;
            }
        }
    }

    @FunctionalInterface
    private interface RedactionSink {

        boolean append(String value, int start, int end, boolean atomic);
    }

    private static final class BoundedUtf8Output {

        private final ByteArrayOutputStream output;
        private final int maximumBytes;

        private BoundedUtf8Output(int maximumBytes) {
            this.maximumBytes = maximumBytes;
            output = new ByteArrayOutputStream(Math.min(maximumBytes, 8192));
        }

        private boolean append(
                String value, int start, int end, boolean atomic) {
            if (atomic && encodedLength(value, start, end)
                          > maximumBytes - output.size()) {
                return false;
            }
            for (int index = start; index < end;) {
                int codePoint = value.codePointAt(index);
                int encodedBytes = codePoint <= 0x7f
                        ? 1
                        : codePoint <= 0x7ff ? 2 : codePoint <= 0xffff ? 3 : 4;
                if (output.size() + encodedBytes > maximumBytes) {
                    return false;
                }
                if (encodedBytes == 1) {
                    output.write(codePoint);
                } else if (encodedBytes == 2) {
                    output.write(0xc0 | codePoint >> 6);
                    output.write(0x80 | codePoint & 0x3f);
                } else if (encodedBytes == 3) {
                    output.write(0xe0 | codePoint >> 12);
                    output.write(0x80 | codePoint >> 6 & 0x3f);
                    output.write(0x80 | codePoint & 0x3f);
                } else {
                    output.write(0xf0 | codePoint >> 18);
                    output.write(0x80 | codePoint >> 12 & 0x3f);
                    output.write(0x80 | codePoint >> 6 & 0x3f);
                    output.write(0x80 | codePoint & 0x3f);
                }
                index += Character.charCount(codePoint);
            }
            return true;
        }

        private static int encodedLength(String value, int start, int end) {
            int length = 0;
            for (int index = start; index < end;) {
                int codePoint = value.codePointAt(index);
                length += codePoint <= 0x7f
                        ? 1
                        : codePoint <= 0x7ff ? 2 : codePoint <= 0xffff ? 3 : 4;
                index += Character.charCount(codePoint);
            }
            return length;
        }

        private byte[] toByteArray() {
            return output.toByteArray();
        }
    }

}
