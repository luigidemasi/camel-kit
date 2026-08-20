package io.github.luigidemasi.camelkit.ship.evidence;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.github.luigidemasi.camelkit.ship.security.ProjectEvidenceFiles;
import io.github.luigidemasi.camelkit.ship.security.ProjectSnapshot;
import io.github.luigidemasi.camelkit.ship.security.ShipTreePolicy;

/**
 * Runs controller-selected commands as direct, unattested child JVMs on a frozen read-only copy of the accepted
 * candidate tree, with a scrubbed environment, bounded digested logs, and process-tree termination.
 */
public final class EvidenceRunner {

    /** Absolute path of the controller's own Java executable used for every evidence command. */
    public static final String JAVA_EXECUTABLE
            = Path.of(System.getProperty("java.home", ""), "bin", "java").toString();

    private static final long MAX_LOG_BYTES = 64L * 1024 * 1024;
    private static final Duration TERMINATION_GRACE = Duration.ofMillis(500);
    private static final Duration TERMINATION_TIMEOUT = Duration.ofSeconds(5);

    private final Clock clock;
    private final ProcessLauncher launcher;
    private final PayloadMaterializer payloads;

    public EvidenceRunner() {
        this(Clock.systemUTC(), EvidenceRunner::launchDirectly, JvmPayloadArchive::materialize);
    }

    EvidenceRunner(Clock clock, ProcessLauncher launcher, PayloadMaterializer payloads) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.launcher = Objects.requireNonNull(launcher, "launcher");
        this.payloads = Objects.requireNonNull(payloads, "payloads");
    }

    /**
     * Runs one command with an atomically claimed, previously absent evidence directory. Each concurrent or sibling run
     * must use its own directory.
     */
    public CommandEvidence run(Path projectRoot, Path evidenceDirectory, EvidenceCommand command) throws IOException {
        Path candidate = realDirectory(projectRoot, "project root");
        Path logs = createEvidenceDirectory(evidenceDirectory);
        boolean handedToCollector = false;
        boolean quarantined = false;
        try {
            Path workingDirectory = resolveWorkingDirectory(candidate, command.relativeWorkingDirectory());
            Path sandboxRoot = privateTempDirectory(logs, "." + command.id() + "-sandbox-");
            Path privateHome = privateDirectory(sandboxRoot.resolve("home"));
            Path privateTemporaryDirectory = privateDirectory(sandboxRoot.resolve("tmp"));
            Path acceptedSnapshot = acceptedSnapshot(candidate, sandboxRoot, command);
            Path java = requireExecutable(Path.of(JAVA_EXECUTABLE), "controller java");
            JvmPayloadArchive.Identity payload = payloads.materialize(sandboxRoot, command.jvmPayload());
            List<String> head = List.of(
                    JAVA_EXECUTABLE, "-cp", JvmPayloadArchive.ARCHIVE_NAME,
                    JvmPayloadArchive.BOOTSTRAP_CLASS, "--launcher=" + command.jvmPayload().launcherClass());
            if (command.arguments().size() < head.size()
                    || !command.arguments().subList(0, head.size()).equals(head)) {
                throw new IOException("Controller command does not name the direct controller JVM payload launch");
            }
            List<String> argv = new ArrayList<>(command.arguments());
            argv.set(0, java.toString());
            argv.set(2, payload.archive().toString());
            argv.add(head.size(), "--accepted-root=" + acceptedSnapshot);
            Map<String, String> environment
                    = controlledEnvironment(privateHome, privateTemporaryDirectory, command);
            Path launchDirectory = acceptedSnapshot.resolve(candidate.relativize(workingDirectory));
            Launch launch = new Launch(List.copyOf(argv), launchDirectory, environment);

            LogFile stdout = createLog(logs, sandboxRoot, ".stdout.log");
            LogFile stderr = createLog(logs, sandboxRoot, ".stderr.log");
            Instant startedAt = clock.instant();
            boolean launched = false;
            boolean timedOut = false;
            Integer exitCode = null;
            String launchError = null;
            Process process = null;
            boolean processTreeReaped = false;
            boolean logPumpsQuiesced = false;

            ExecutorService pumps = Executors.newFixedThreadPool(2, runnable -> {
                Thread thread = new Thread(runnable, "camel-kit-ship-evidence-log");
                thread.setDaemon(true);
                return thread;
            });
            try (FileChannel stdoutChannel = openLog(stdout.path());
                 FileChannel stderrChannel = openLog(stderr.path())) {
                process = launcher.launch(launch);
                launched = true;
                Process running = process;
                Future<?> stdoutPump = pumps.submit(() -> {
                    copy(running.getInputStream(), stdoutChannel);
                    return null;
                });
                Future<?> stderrPump = pumps.submit(() -> {
                    copy(running.getErrorStream(), stderrChannel);
                    return null;
                });
                if (waitForExecution(process, stdoutPump, stderrPump, command.timeout())) {
                    exitCode = process.exitValue();
                    processTreeReaped = terminateDescendantsAndWait(process);
                    if (!processTreeReaped) {
                        launchError = appendFailure(
                                launchError, residualProcessFailure("Command", process));
                    }
                } else {
                    timedOut = true;
                    processTreeReaped = terminateAndWait(process);
                    if (!processTreeReaped) {
                        launchError = appendFailure(
                                launchError, residualProcessFailure("Timed-out command", process));
                    }
                }
                awaitPump(stdoutPump);
                awaitPump(stderrPump);
                logPumpsQuiesced = true;
                stdoutChannel.force(true);
                stderrChannel.force(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                launchError = appendFailure(launchError, "Interrupted while waiting for the command");
                processTreeReaped = process != null && terminateAndWait(process);
                if (!processTreeReaped) {
                    launchError = appendFailure(
                            launchError, residualProcessFailure("Interrupted command", process));
                }
            } catch (IOException e) {
                launchError = appendFailure(launchError, e.getClass().getSimpleName() + ": " + e.getMessage());
                processTreeReaped = process != null && terminateAndWait(process);
                if (!processTreeReaped) {
                    launchError = appendFailure(
                            launchError, residualProcessFailure("Failed command", process));
                }
            } catch (ExecutionException e) {
                launchError = appendFailure(
                        launchError, "Could not retain command logs: " + e.getCause().getMessage());
                processTreeReaped = process != null && terminateAndWait(process);
                if (!processTreeReaped) {
                    launchError = appendFailure(
                            launchError, residualProcessFailure("Log-flooding command", process));
                }
            } finally {
                pumps.shutdownNow();
            }

            quarantined = !(processTreeReaped && logPumpsQuiesced);
            if (quarantined) {
                launchError = appendFailure(
                        launchError, "Ephemeral evidence quarantined at " + sandboxRoot);
            }

            verifyLog(stdout);
            verifyLog(stderr);
            Instant endedAt = clock.instant();
            CommandEvidence evidence = new CommandEvidence(
                    command.id(),
                    java.toString(),
                    command.arguments(),
                    workingDirectory.toString(),
                    command.inputDigests(),
                    startedAt,
                    endedAt,
                    launched,
                    timedOut,
                    exitCode,
                    launchError,
                    stdout.path().toString(),
                    digest(stdout.path()),
                    stderr.path().toString(),
                    digest(stderr.path()),
                    quarantined,
                    sandboxRoot);
            handedToCollector = true;
            return evidence;
        } finally {
            if (!handedToCollector && !quarantined) {
                cleanupAbandoned(logs);
            }
        }
    }

    /** Removes raw logs and the exact controller-created sandbox after CAS retention, unless quarantined. */
    public static void cleanupEphemeral(CommandEvidence evidence) throws IOException {
        if (evidence.quarantined()) {
            return;
        }
        Path sandbox = evidence.sandboxRoot();
        if (sandbox == null || sandbox.getParent() == null) {
            throw new IOException("Command evidence is missing its ephemeral sandbox root");
        }
        Path root = realDirectory(sandbox.getParent(), "evidence directory");
        Path normalized = sandbox.toAbsolutePath().normalize();
        if (!root.equals(normalized.getParent())) {
            throw new IOException("Command evidence sandbox escaped its evidence directory");
        }
        Set<String> logs = new LinkedHashSet<>();
        logs.add(ephemeralFile(root, evidence.stdoutLog(), "stdout log"));
        logs.add(ephemeralFile(root, evidence.stderrLog(), "stderr log"));
        for (String log : logs) {
            deleteRelativeFile(root, log);
        }
        deleteRelativeTree(root, normalized.getFileName().toString());
        Files.delete(root);
    }

    /** Removes an exclusive evidence root after a failed, non-quarantined executor. */
    public static void cleanupAbandoned(Path evidenceDirectory) throws IOException {
        if (evidenceDirectory == null
                || !Files.exists(evidenceDirectory, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        Path root = realDirectory(evidenceDirectory, "evidence directory");
        Path parent = root.getParent();
        if (parent == null) {
            throw new IOException("Evidence directory has no protected parent");
        }
        deleteRelativeTree(
                realDirectory(parent, "evidence parent"),
                root.getFileName().toString());
    }

    private static Process launchDirectly(Launch launch) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(launch.arguments());
        builder.directory(launch.workingDirectory().toFile());
        Map<String, String> environment = builder.environment();
        environment.clear();
        environment.putAll(launch.environment());
        return builder.start();
    }

    private static Map<String, String> controlledEnvironment(
            Path privateHome, Path privateTemporaryDirectory, EvidenceCommand command) {
        Map<String, String> environment = new LinkedHashMap<>();
        environment.put("LANG", "C");
        environment.put("LC_ALL", "C");
        environment.put("HOME", privateHome.toString());
        environment.put("TMPDIR", privateTemporaryDirectory.toString());
        environment.put(
                "JAVA_TOOL_OPTIONS",
                "-Duser.home=" + privateHome + " -Djava.io.tmpdir=" + privateTemporaryDirectory);
        environment.putAll(command.environment());
        return Map.copyOf(environment);
    }

    private static String ephemeralFile(Path root, String value, String label) throws IOException {
        if (value == null) {
            throw new IOException("Command evidence is missing its raw " + label);
        }
        Path path;
        try {
            path = Path.of(value).toAbsolutePath().normalize();
        } catch (RuntimeException e) {
            throw new IOException("Command evidence has an invalid raw " + label, e);
        }
        if (!path.getParent().equals(root)) {
            throw new IOException("Command evidence raw " + label + " escaped its evidence directory");
        }
        return path.getFileName().toString();
    }

    private static String residualProcessFailure(String phase, Process process) {
        return phase + " process tree could not be reaped (" + processIdentity(process) + ")";
    }

    private static String processIdentity(Process process) {
        if (process == null) {
            return "process handle unavailable";
        }
        try {
            ProcessHandle handle = process.toHandle();
            String start = handle.info().startInstant().map(Instant::toString).orElse("start time unavailable");
            return "pid " + handle.pid() + ", " + start;
        } catch (RuntimeException e) {
            return "process identity unavailable";
        }
    }

    private static Path realDirectory(Path path, String label) throws IOException {
        if (path == null) {
            throw new IOException(label + " is required");
        }
        Path normalized = path.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(normalized)
                || !Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException(label + " must be a non-symbolic-link directory: " + path);
        }
        return normalized.toRealPath();
    }

    private static Path createEvidenceDirectory(Path path) throws IOException {
        if (path == null) {
            throw new IOException("Evidence directory is required");
        }
        Path absolute = path.toAbsolutePath().normalize();
        Path parent = absolute.getParent();
        if (parent == null) {
            throw new IOException("Evidence directory has no parent");
        }
        Path existingParent = parent;
        while (!Files.exists(existingParent, LinkOption.NOFOLLOW_LINKS)) {
            existingParent = existingParent.getParent();
            if (existingParent == null) {
                throw new IOException("Evidence directory has no existing ancestor: " + absolute);
            }
        }
        Path realParent = existingParent.toRealPath();
        if (!Files.isDirectory(realParent, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Evidence directory ancestor must be a directory: " + existingParent);
        }
        realParent = realParent.resolve(existingParent.relativize(parent));
        Files.createDirectories(realParent);
        realParent = realParent.toRealPath();
        Path directory = realParent.resolve(absolute.getFileName());
        try {
            Files.createDirectory(directory, PosixFilePermissions.asFileAttribute(
                    PosixFilePermissions.fromString("rwx------")));
        } catch (FileAlreadyExistsException e) {
            throw new IOException("Evidence directory must be new and exclusive to one command run: " + absolute, e);
        }
        return privateDirectory(directory);
    }

    private static Path privateTempDirectory(Path parent, String prefix) throws IOException {
        Path directory = Files.createTempDirectory(parent, prefix);
        return privateDirectory(directory);
    }

    private static Path privateDirectory(Path directory) throws IOException {
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            Files.createDirectory(directory);
        }
        if (Files.isSymbolicLink(directory) || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Private sandbox path is unsafe: " + directory);
        }
        if (Files.getFileAttributeView(directory, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS) == null) {
            throw new IOException("Evidence storage requires POSIX permission support: " + directory);
        }
        Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"));
        return directory.toRealPath(LinkOption.NOFOLLOW_LINKS);
    }

    private static Path acceptedSnapshot(Path candidate, Path sandboxRoot, EvidenceCommand command)
            throws IOException {
        ProjectSnapshot before = ProjectEvidenceFiles.capture(candidate);
        if (!command.inputDigests().contains(before.digest())) {
            throw new IOException("Evidence command is not bound to the accepted candidate tree digest");
        }
        Path copy = privateDirectory(sandboxRoot.resolve("accepted-snapshot"));
        ProjectSnapshot copied = ProjectEvidenceFiles.materializeMaterial(candidate, copy);
        ProjectSnapshot after = ProjectEvidenceFiles.capture(candidate);
        if (!ProjectEvidenceFiles.unchangedMaterialTree(before, copied)
                || !ProjectEvidenceFiles.unchanged(before, after)) {
            throw new IOException("Candidate changed while the accepted evidence snapshot was frozen");
        }
        makeReadOnly(copy);
        return copy;
    }

    /** Read-only input guarantee: the frozen snapshot replaces the lost read-only bind mount. */
    private static void makeReadOnly(Path root) throws IOException {
        List<Path> directories = new ArrayList<>();
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted().toList()) {
                if (Files.isSymbolicLink(path)) {
                    continue;
                }
                if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                    directories.add(path);
                } else if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("r--------"));
                }
            }
        }
        for (Path directory : directories) {
            Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("r-x------"));
        }
    }

    private static void deleteRelativeFile(Path root, String relative) throws IOException {
        Path file = controlledPath(root, relative);
        if (Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Refusing to delete an evidence directory as a file: " + relative);
        }
        Files.deleteIfExists(file);
    }

    private static void deleteRelativeTree(Path root, String relative) throws IOException {
        Path directory = controlledPath(root, relative);
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        Files.walkFileTree(directory, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path visited, BasicFileAttributes attributes)
                    throws IOException {
                // Restore writability so read-only accepted snapshots stay deletable.
                if (Files.getFileAttributeView(
                        visited, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS)
                    != null) {
                    Files.setPosixFilePermissions(visited, PosixFilePermissions.fromString("rwx------"));
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path visited, IOException failure) throws IOException {
                if (failure != null) {
                    throw failure;
                }
                Files.delete(visited);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static Path controlledPath(Path requestedRoot, String relative) throws IOException {
        Path root = requestedRoot.toRealPath(LinkOption.NOFOLLOW_LINKS);
        String canonical = ShipTreePolicy.requireCanonicalRelativePath(relative);
        Path path = root.resolve(canonical).normalize();
        if (!path.startsWith(root)) {
            throw new IOException("Evidence path escapes its controller-owned root");
        }
        Path current = root;
        Path parent = path.getParent();
        if (parent != null) {
            for (Path component : root.relativize(parent)) {
                current = current.resolve(component);
                if (Files.isSymbolicLink(current)
                        || Files.exists(current, LinkOption.NOFOLLOW_LINKS)
                                && !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("Evidence path contains an unsafe component: " + current);
                }
            }
        }
        return path;
    }

    private static Path resolveWorkingDirectory(Path root, String relative) throws IOException {
        Path directory = relative == null || relative.isBlank() ? root : root.resolve(relative).normalize();
        if (!directory.startsWith(root)
                || Files.isSymbolicLink(directory)
                || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Evidence command working directory is not a safe project directory: " + relative);
        }
        Path real = directory.toRealPath();
        if (!real.startsWith(root)) {
            throw new IOException("Evidence command working directory escapes the project root");
        }
        return real;
    }

    private static Path requireExecutable(Path path, String label) throws IOException {
        if (path == null || Files.isSymbolicLink(path)
                || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                || !Files.isExecutable(path)) {
            throw new IOException(label + " executable is missing, symbolic, non-regular, or not executable: " + path);
        }
        return path.toRealPath();
    }

    private static boolean waitForExecution(
            Process process, Future<?> stdout, Future<?> stderr, Duration timeout)
            throws InterruptedException, ExecutionException {
        long deadline = Math.addExact(System.nanoTime(), timeout.toNanos());
        while (true) {
            requireHealthy(stdout);
            requireHealthy(stderr);
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                return false;
            }
            long slice = Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(100));
            if (process.waitFor(slice, TimeUnit.NANOSECONDS)) {
                return true;
            }
        }
    }

    private static void requireHealthy(Future<?> pump) throws InterruptedException, ExecutionException {
        if (pump.isDone()) {
            pump.get();
        }
    }

    private static boolean terminateAndWait(Process process) {
        List<ProcessHandle> children = descendants(process);
        children.forEach(ProcessHandle::destroy);
        process.destroy();
        if (awaitTermination(process, children, TERMINATION_GRACE)) {
            return true;
        }
        children.forEach(handle -> {
            if (handle.isAlive()) {
                handle.destroyForcibly();
            }
        });
        if (process.isAlive()) {
            process.destroyForcibly();
        }
        return awaitTermination(process, children, TERMINATION_TIMEOUT);
    }

    private static boolean terminateDescendantsAndWait(Process process) {
        List<ProcessHandle> children = descendants(process);
        children.forEach(ProcessHandle::destroy);
        if (awaitHandles(children, TERMINATION_GRACE)) {
            return true;
        }
        children.forEach(handle -> {
            if (handle.isAlive()) {
                handle.destroyForcibly();
            }
        });
        return awaitHandles(children, TERMINATION_TIMEOUT);
    }

    private static boolean awaitTermination(
            Process process, List<ProcessHandle> children, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        boolean interrupted = false;
        try {
            while (process.isAlive()) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    return false;
                }
                try {
                    if (!process.waitFor(remaining, TimeUnit.NANOSECONDS)) {
                        return false;
                    }
                } catch (InterruptedException e) {
                    interrupted = true;
                    Thread.interrupted();
                }
            }
            return awaitHandles(children, Duration.ofNanos(Math.max(0, deadline - System.nanoTime())));
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static boolean awaitHandles(List<ProcessHandle> handles, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        boolean interrupted = false;
        try {
            for (ProcessHandle handle : handles) {
                while (handle.isAlive()) {
                    long remaining = deadline - System.nanoTime();
                    if (remaining <= 0) {
                        return false;
                    }
                    try {
                        handle.onExit().get(remaining, TimeUnit.NANOSECONDS);
                    } catch (InterruptedException e) {
                        interrupted = true;
                        Thread.interrupted();
                    } catch (ExecutionException | TimeoutException e) {
                        return false;
                    }
                }
            }
            return handles.stream().noneMatch(ProcessHandle::isAlive);
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static List<ProcessHandle> descendants(Process process) {
        try {
            return process.toHandle().descendants()
                    .sorted(Comparator.comparingLong(ProcessHandle::pid).reversed())
                    .toList();
        } catch (UnsupportedOperationException e) {
            return List.of();
        }
    }

    private static LogFile createLog(Path directory, Path sandbox, String suffix) throws IOException {
        String sandboxName = sandbox.getFileName().toString();
        if (!sandbox.getParent().equals(directory)
                || !sandboxName.matches("\\.[a-z0-9][a-z0-9-]*-sandbox-[A-Za-z0-9._-]+")
                || !(".stdout.log".equals(suffix) || ".stderr.log".equals(suffix))) {
            throw new IOException("Evidence log is not bound to a recognized sandbox");
        }
        Path path = directory.resolve(sandboxName + suffix);
        Files.createFile(path, PosixFilePermissions.asFileAttribute(
                PosixFilePermissions.fromString("rw-------")));
        BasicFileAttributes attributes
                = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (attributes.isSymbolicLink() || !attributes.isRegularFile()) {
            throw new IOException("Evidence log is not a regular file: " + path);
        }
        return new LogFile(path, attributes.fileKey());
    }

    private static FileChannel openLog(Path path) throws IOException {
        return FileChannel.open(path, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
    }

    private static void copy(InputStream input, FileChannel output) throws IOException {
        byte[] bytes = new byte[8192];
        long total = 0;
        int read;
        while ((read = input.read(bytes)) != -1) {
            total = Math.addExact(total, read);
            if (total > MAX_LOG_BYTES) {
                throw new IOException("Command log exceeds " + MAX_LOG_BYTES + " bytes");
            }
            ByteBuffer buffer = ByteBuffer.wrap(bytes, 0, read);
            while (buffer.hasRemaining()) {
                output.write(buffer);
            }
        }
    }

    private static void awaitPump(Future<?> pump) throws InterruptedException, ExecutionException, IOException {
        try {
            pump.get(30, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            pump.cancel(true);
            throw new IOException("Timed out while retaining command logs", e);
        }
    }

    private static void verifyLog(LogFile log) throws IOException {
        BasicFileAttributes attributes
                = Files.readAttributes(log.path(), BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (attributes.isSymbolicLink() || !attributes.isRegularFile()
                || (log.fileKey() != null && !log.fileKey().equals(attributes.fileKey()))) {
            throw new IOException("Evidence log identity changed during execution: " + log.path());
        }
    }

    private static String appendFailure(String current, String additional) {
        return current == null ? additional : current + "; " + additional;
    }

    private static String digest(Path file) throws IOException {
        MessageDigest digest = sha256();
        try (InputStream input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return "sha256:" + HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    /** Exact direct-launch request: full argv, working directory, and complete child environment. */
    record Launch(List<String> arguments, Path workingDirectory, Map<String, String> environment) {

        Launch {
            arguments = List.copyOf(arguments);
            environment = Map.copyOf(environment);
        }
    }

    @FunctionalInterface
    interface ProcessLauncher {

        Process launch(Launch launch) throws IOException;
    }

    @FunctionalInterface
    interface PayloadMaterializer {

        JvmPayloadArchive.Identity materialize(Path sandboxRoot, JvmPayloadRequest request) throws IOException;
    }

    private record LogFile(Path path, Object fileKey) {
    }
}
