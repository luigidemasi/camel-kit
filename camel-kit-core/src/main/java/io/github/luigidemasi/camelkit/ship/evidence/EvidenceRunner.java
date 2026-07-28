package io.github.luigidemasi.camelkit.ship.evidence;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
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
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.github.luigidemasi.camelkit.ship.evidence.CommandEvidence.SandboxIdentity;
import io.github.luigidemasi.camelkit.ship.evidence.EvidenceSandboxLauncher.Access;
import io.github.luigidemasi.camelkit.ship.evidence.EvidenceSandboxLauncher.Invocation;
import io.github.luigidemasi.camelkit.ship.evidence.EvidenceSandboxLauncher.Mount;
import io.github.luigidemasi.camelkit.ship.security.ProjectEvidenceFiles;
import io.github.luigidemasi.camelkit.ship.security.ProjectSnapshot;
import io.github.luigidemasi.camelkit.ship.security.ShipTreePolicy;

/** Runs controller-selected commands in a fail-closed OS filesystem/process sandbox. */
public final class EvidenceRunner {

    public static final String JAVA_EXECUTABLE = "/opt/camel-kit/jdk/bin/java";
    public static final String SANDBOX_PROVIDER = BubblewrapSandboxLauncher.PROVIDER;

    private static final String SANDBOX_WORKSPACE = "/workspace";
    private static final String SANDBOX_HOME = "/home/camel-kit";
    private static final String SANDBOX_TMP = "/tmp";
    private static final String SANDBOX_JDK = "/opt/camel-kit/jdk";
    private static final String PROCESS_RETENTION_MARKER = ".camel-kit-process-retention";
    private static final String PROCESS_RETENTION_TEMPORARY = ".camel-kit-process-retention.tmp";
    private static final String SANDBOX_PROFILE = BubblewrapSandboxLauncher.PROFILE_ID
                                                  + ":ro-system-libraries-only:ro-accepted-snapshot=/workspace"
                                                  + ":private-home=/home/camel-kit:private-tmp=/tmp"
                                                  + ":exact-jdk-ro:payload-ro:no-network:no-dns:no-cache:no-controller-home:no-global-path";
    private static final Duration VERSION_TIMEOUT = Duration.ofSeconds(15);
    private static final int MAX_VERSION_BYTES = 64 * 1024;
    private static final long MAX_LOG_BYTES = 64L * 1024 * 1024;
    private static final Duration TERMINATION_GRACE = Duration.ofMillis(500);
    private static final Duration TERMINATION_TIMEOUT = Duration.ofSeconds(5);

    private final Clock clock;
    private final EvidenceSandboxLauncher sandboxLauncher;
    private final ToolchainResolver toolchains;
    private final JdkResolver jdks;
    private final List<Mount> systemLibraries;

    public EvidenceRunner() {
        this(Clock.systemUTC(), new BubblewrapSandboxLauncher(), new ControllerToolchainResolver(),
             EvidenceRunner::freezeControllerJdk);
    }

    EvidenceRunner(Clock clock) {
        this(clock, new BubblewrapSandboxLauncher(), new ControllerToolchainResolver(),
             EvidenceRunner::freezeControllerJdk);
    }

    EvidenceRunner(Clock clock, EvidenceSandboxLauncher sandboxLauncher) {
        this(clock, sandboxLauncher, new ControllerToolchainResolver(), EvidenceRunner::freezeControllerJdk);
    }

    EvidenceRunner(Clock clock, EvidenceSandboxLauncher sandboxLauncher, ToolchainResolver toolchains) {
        this(clock, sandboxLauncher, toolchains, EvidenceRunner::freezeControllerJdk);
    }

    EvidenceRunner(
                   Clock clock,
                   EvidenceSandboxLauncher sandboxLauncher,
                   ToolchainResolver toolchains,
                   JdkResolver jdks) {
        this(clock, sandboxLauncher, toolchains, jdks, linuxSystemLibraries());
    }

    EvidenceRunner(
                   Clock clock,
                   EvidenceSandboxLauncher sandboxLauncher,
                   ToolchainResolver toolchains,
                   JdkResolver jdks,
                   List<Mount> systemLibraries) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.sandboxLauncher = Objects.requireNonNull(sandboxLauncher, "sandboxLauncher");
        this.toolchains = Objects.requireNonNull(toolchains, "toolchains");
        this.jdks = Objects.requireNonNull(jdks, "jdks");
        this.systemLibraries = List.copyOf(systemLibraries);
    }

    public static String expectedSandboxProfileDigest(EvidenceCommand command) throws IOException {
        return sandboxProfileDigest(command, expectedEnvironment(command));
    }

    /** Rebuilds a historical sandbox profile from its authenticated, recorded JDK identity. */
    public static String expectedSandboxProfileDigest(EvidenceCommand command, String jdkDigest) {
        return sandboxProfileDigest(command, expectedEnvironment(command, jdkDigest));
    }

    private static String sandboxProfileDigest(
            EvidenceCommand command, Map<String, String> controlledEnvironment) {
        MessageDigest digest = sha256();
        update(digest, SANDBOX_PROFILE);
        update(digest, command.id());
        command.arguments().forEach(value -> update(digest, value));
        command.versionArguments().forEach(value -> update(digest, value));
        update(digest, command.relativeWorkingDirectory() == null ? "" : command.relativeWorkingDirectory());
        controlledEnvironment.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    update(digest, entry.getKey());
                    update(digest, entry.getValue());
                });
        update(digest,
                command.requiredToolchainDigest() == null ? "external-toolchain" : command.requiredToolchainDigest());
        update(digest, command.jvmPayload() == null ? "no-jvm-payload" : command.jvmPayload().digest());
        return digest(digest);
    }

    public static Map<String, String> expectedEnvironment(EvidenceCommand command) throws IOException {
        return expectedEnvironment(command, controllerJdk().digest());
    }

    /** Rebuilds the deterministic environment without consulting the controller's current JDK. */
    public static Map<String, String> expectedEnvironment(EvidenceCommand command, String jdkDigest) {
        if (jdkDigest == null || !jdkDigest.matches("sha256:[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Evidence environment requires an authenticated JDK digest");
        }
        Map<String, String> environment = new LinkedHashMap<>();
        environment.put("HOME", SANDBOX_HOME);
        environment.put("TMPDIR", SANDBOX_TMP);
        environment.put("JAVA_HOME", SANDBOX_JDK);
        environment.put("JAVA_TOOL_OPTIONS", "-Duser.home=" + SANDBOX_HOME + " -Djava.io.tmpdir=" + SANDBOX_TMP);
        environment.put("CAMEL_KIT_JDK_DIGEST", jdkDigest);
        environment.put("LANG", "C");
        environment.put("LC_ALL", "C");
        environment.putAll(command.environment());
        return Map.copyOf(environment);
    }

    /**
     * Runs one command with an atomically claimed, previously absent evidence directory. Each concurrent or sibling run
     * must use its own directory.
     */
    public CommandEvidence run(Path projectRoot, Path evidenceDirectory, EvidenceCommand command) throws IOException {
        Path candidate = realDirectory(projectRoot, "project root");
        Path logs = createEvidenceDirectory(evidenceDirectory);
        boolean handedToCollector = false;
        try {
            Path workingDirectory = resolveWorkingDirectory(candidate, command.relativeWorkingDirectory());
            Path sandboxRoot = privateTempDirectory(logs, "." + command.id() + "-sandbox-");
            Path privateHome = privateDirectory(sandboxRoot.resolve("home"));
            Path privateTemporaryDirectory = privateDirectory(sandboxRoot.resolve("tmp"));
            Path acceptedSnapshot = acceptedSnapshot(candidate, sandboxRoot, command);
            JdkIdentity jdk = jdks.freeze(sandboxRoot);

            EvidenceSandboxLauncher.Identity launcherIdentity = sandboxLauncher.identity();
            Path sandboxExecutable = requireExecutable(launcherIdentity.executable(), "sandbox");
            String sandboxExecutableDigest = digest(sandboxExecutable);

            ResolvedToolchain toolchain = toolchains.resolve(candidate, sandboxRoot, command, jdk);
            Path executable = requireExecutable(toolchain.executable(), "toolchain");
            if (!command.arguments().get(0).equals(toolchain.sandboxExecutable())) {
                throw new IOException("Controller command does not name the resolved in-sandbox toolchain executable");
            }
            if (!command.versionArguments().isEmpty()
                    && !command.versionArguments().get(0).equals(toolchain.sandboxExecutable())) {
                throw new IOException("Version command does not name the resolved in-sandbox toolchain executable");
            }
            requireExpectedToolchain(command, toolchain.digest());
            String executableDigest = digest(executable);
            Map<String, String> controlledEnvironment = expectedEnvironment(command, jdk.digest());
            List<Mount> mounts = mounts(
                    candidate, acceptedSnapshot, logs, privateHome, privateTemporaryDirectory,
                    toolchain, jdk, systemLibraries);
            String sandboxWorkingDirectory = sandboxWorkingDirectory(candidate, workingDirectory);
            Invocation invocation = new Invocation(
                    candidate,
                    workingDirectory,
                    sandboxWorkingDirectory,
                    privateHome,
                    privateTemporaryDirectory,
                    executable,
                    mounts,
                    command.arguments(),
                    controlledEnvironment);
            String sandboxProfileDigest = sandboxProfileDigest(command, controlledEnvironment);
            createProcessRetentionMarker(sandboxRoot, command.id());
            VersionResult versionResult = readVersion(invocation, command.versionArguments());

            LogFile stdout = createLog(logs, sandboxRoot, ".stdout.log");
            LogFile stderr = createLog(logs, sandboxRoot, ".stderr.log");
            Instant startedAt = clock.instant();
            boolean launched = false;
            boolean timedOut = false;
            Integer exitCode = null;
            String launchError = versionResult.failure();
            Process process = null;
            boolean processTreeReaped = versionResult.reaped();
            boolean logPumpsQuiesced = versionResult.failure() != null;

            ExecutorService pumps = Executors.newFixedThreadPool(2, runnable -> {
                Thread thread = new Thread(runnable, "camel-kit-ship-evidence-log");
                thread.setDaemon(true);
                return thread;
            });
            if (versionResult.failure() == null) {
                try (FileChannel stdoutChannel = openLog(stdout.path());
                     FileChannel stderrChannel = openLog(stderr.path())) {
                    process = sandboxLauncher.launch(invocation);
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
            } else {
                pumps.shutdownNow();
            }

            if (processTreeReaped && logPumpsQuiesced) {
                clearProcessRetentionMarker(sandboxRoot);
            } else {
                launchError = appendFailure(
                        launchError, "Ephemeral evidence quarantined at " + sandboxRoot);
            }

            verifyLog(stdout);
            verifyLog(stderr);
            String postExecutableDigest = null;
            String postToolchainDigest = null;
            String postSandboxExecutableDigest = null;
            try {
                postExecutableDigest = digest(executable);
                postToolchainDigest = toolchain.identitySource().identity().aggregateDigest();
                postSandboxExecutableDigest = digest(sandboxExecutable);
            } catch (IOException e) {
                launchError
                        = appendFailure(launchError, "Execution authority could not be reverified: " + e.getMessage());
            }
            Instant endedAt = clock.instant();
            CommandEvidence evidence = new CommandEvidence(
                    CommandEvidence.SCHEMA_VERSION,
                    command.id(),
                    new SandboxIdentity(
                            launcherIdentity.provider(),
                            sandboxExecutable.toString(),
                            sandboxExecutableDigest,
                            postSandboxExecutableDigest,
                            null,
                            sandboxProfileDigest),
                    toolchain.digest(),
                    postToolchainDigest,
                    toolchain.snapshot().toString(),
                    toolchain.snapshotDigest(),
                    executable.toString(),
                    executableDigest,
                    postExecutableDigest,
                    null,
                    versionResult.value(),
                    command.arguments(),
                    workingDirectory.toString(),
                    controlledEnvironment,
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
                    command.inputDigests());
            handedToCollector = true;
            return evidence;
        } finally {
            if (!handedToCollector) {
                cleanupAbandoned(logs, command);
            }
        }
    }

    /** Removes raw logs and the exact controller-created sandbox after CAS retention. */
    public static void cleanupEphemeral(
            Path evidenceDirectory, EvidenceCommand command, CommandEvidence evidence)
            throws IOException {
        Path root = realDirectory(evidenceDirectory, "evidence directory");
        Set<String> sandboxes = new LinkedHashSet<>();
        collectSandbox(root, command.id(), evidence.toolchainSnapshot(), sandboxes);
        collectSandbox(root, command.id(), evidence.executable(), sandboxes);
        if (sandboxes.size() > 1) {
            throw new IOException("Command evidence references multiple ephemeral sandbox roots");
        }
        for (String sandbox : sandboxes) {
            if (hasProcessRetentionMarker(root.resolve(sandbox))) {
                return;
            }
        }
        Set<String> logs = new LinkedHashSet<>();
        logs.add(ephemeralFile(root, evidence.stdoutLog(), "stdout log"));
        logs.add(ephemeralFile(root, evidence.stderrLog(), "stderr log"));
        for (String log : logs) {
            deleteRelativeFile(root, log);
        }
        for (String sandbox : sandboxes) {
            deleteRelativeTree(root, sandbox);
        }
        Files.delete(root);
    }

    /** Removes an exclusive evidence root after a failed executor, unless process state is quarantined. */
    public static void cleanupAbandoned(
            Path evidenceDirectory, EvidenceCommand command)
            throws IOException {
        if (evidenceDirectory == null
                || !Files.exists(evidenceDirectory, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        Path root = realDirectory(evidenceDirectory, "evidence directory");
        String sandboxPrefix = "." + command.id() + "-sandbox-";
        try (var entries = Files.newDirectoryStream(root)) {
            for (Path entry : entries) {
                if (entry.getFileName().toString().startsWith(sandboxPrefix)
                        && hasProcessRetentionMarker(entry)) {
                    return;
                }
            }
        }
        Path parent = root.getParent();
        if (parent == null) {
            throw new IOException("Evidence directory has no protected parent");
        }
        deleteRelativeTree(
                realDirectory(parent, "evidence parent"),
                root.getFileName().toString());
    }

    private static void createProcessRetentionMarker(Path sandbox, String commandId) throws IOException {
        byte[] content = ("camel-kit-evidence-process-retention-v1\n"
                          + "command=" + commandId + "\n"
                          + "state=process-lifecycle-unresolved\n")
                .getBytes(StandardCharsets.UTF_8);
        writeBytesAtomically(
                sandbox, PROCESS_RETENTION_MARKER, content, "r--------", PROCESS_RETENTION_TEMPORARY);
    }

    private static void clearProcessRetentionMarker(Path sandbox) throws IOException {
        deleteRelativeFile(sandbox, PROCESS_RETENTION_MARKER);
    }

    /** Any marker-shaped entry is a permanent fail-safe quarantine until an operator inspects it. */
    private static boolean hasProcessRetentionMarker(Path sandbox) {
        if (sandbox == null) {
            return true;
        }
        try {
            if (!Files.exists(sandbox, LinkOption.NOFOLLOW_LINKS)) {
                return false;
            }
            if (Files.isSymbolicLink(sandbox) || !Files.isDirectory(sandbox, LinkOption.NOFOLLOW_LINKS)) {
                return true;
            }
            try (var entries = Files.newDirectoryStream(sandbox)) {
                for (Path entry : entries) {
                    if (PROCESS_RETENTION_MARKER.equals(entry.getFileName().toString())) {
                        return true;
                    }
                }
            }
            return false;
        } catch (IOException | SecurityException e) {
            return true;
        }
    }

    private static void collectSandbox(
            Path root, String commandId, String value, Set<String> sandboxes)
            throws IOException {
        if (value == null) {
            return;
        }
        Path path;
        try {
            path = Path.of(value).toAbsolutePath().normalize();
        } catch (RuntimeException e) {
            throw new IOException("Command evidence contains an invalid ephemeral path", e);
        }
        if (!path.startsWith(root) || root.equals(path)) {
            return;
        }
        Path relative = root.relativize(path);
        String first = relative.getName(0).toString();
        if (first.startsWith("." + commandId + "-sandbox-")) {
            sandboxes.add(first);
        }
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

    private VersionResult readVersion(Invocation invocation, List<String> versionArguments) {
        if (versionArguments.isEmpty()) {
            return failedVersionResult("unreported", true, null);
        }
        Invocation versionInvocation = new Invocation(
                invocation.candidate(), invocation.workingDirectory(), invocation.sandboxWorkingDirectory(),
                invocation.privateHome(), invocation.privateTemporaryDirectory(), invocation.toolchainExecutable(),
                invocation.mounts(), versionArguments, invocation.environment());
        Process process = null;
        ExecutorService reader = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "camel-kit-ship-version-output");
            thread.setDaemon(true);
            return thread;
        });
        try {
            process = sandboxLauncher.launch(versionInvocation);
            Process running = process;
            Future<byte[]> output = reader.submit(() -> readBounded(running.getInputStream(), MAX_VERSION_BYTES));
            Future<byte[]> errors = reader.submit(() -> readBounded(running.getErrorStream(), MAX_VERSION_BYTES));
            if (!waitForExecution(process, output, errors, VERSION_TIMEOUT)) {
                boolean reaped = terminateAndWait(process);
                return failedVersionResult("version-query-timeout", reaped, process);
            }
            if (!terminateDescendantsAndWait(process)) {
                return failedVersionResult("version-query-descendants-not-reaped", false, process);
            }
            byte[] stdout = output.get(5, TimeUnit.SECONDS);
            errors.get(5, TimeUnit.SECONDS);
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                return failedVersionResult("exit-" + exitCode, true, process);
            }
            String value = new String(stdout, StandardCharsets.UTF_8).strip();
            if (value.isEmpty()) {
                return failedVersionResult("unreported", true, process);
            }
            return new VersionResult(value.lines().findFirst().orElseThrow(), true, null);
        } catch (IOException e) {
            boolean reaped = process == null || terminateAndWait(process);
            return failedVersionResult("version-query-error:" + e.getClass().getSimpleName(), reaped, process);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            boolean reaped = process == null || terminateAndWait(process);
            return failedVersionResult("version-query-interrupted", reaped, process);
        } catch (ExecutionException | TimeoutException e) {
            boolean reaped = process == null || terminateAndWait(process);
            return failedVersionResult("version-query-error:" + e.getClass().getSimpleName(), reaped, process);
        } finally {
            reader.shutdownNow();
        }
    }

    private static VersionResult failedVersionResult(String value, boolean reaped, Process process) {
        String failure = "Version query failed: " + value;
        if (!reaped) {
            failure = appendFailure(failure, residualProcessFailure("Version query", process));
        }
        return new VersionResult(
                value,
                reaped,
                failure);
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

    private static List<Mount> mounts(
            Path candidate,
            Path acceptedSnapshot,
            Path evidenceDirectory,
            Path privateHome,
            Path privateTemporaryDirectory,
            ResolvedToolchain toolchain,
            JdkIdentity jdk,
            List<Mount> configuredSystemLibraries)
            throws IOException {
        List<Mount> systemLibraries = new ArrayList<>();
        for (Mount configured : configuredSystemLibraries) {
            if (configured.access() != Access.READ_ONLY) {
                throw new IOException("System library mounts must be read-only");
            }
            systemLibraries.add(new Mount(
                    realDirectory(configured.source(), "system library root"),
                    configured.target(), Access.READ_ONLY));
        }
        for (Mount system : systemLibraries) {
            if (candidate.startsWith(system.source()) || evidenceDirectory.startsWith(system.source())) {
                throw new IOException("Candidate and controller evidence must be outside system library mounts");
            }
        }
        if (candidate.startsWith(evidenceDirectory) || evidenceDirectory.startsWith(candidate)) {
            throw new IOException("Candidate and controller evidence directories must be disjoint");
        }
        List<Mount> mounts = new ArrayList<>();
        mounts.addAll(systemLibraries);
        if (jdk.root().startsWith(candidate) || candidate.startsWith(jdk.root())
                || evidenceDirectory.startsWith(jdk.root())) {
            throw new IOException("Controller JDK overlaps candidate or controller state");
        }
        mounts.add(new Mount(jdk.root(), SANDBOX_JDK, Access.READ_ONLY));
        mounts.addAll(jdk.externalMounts());
        mounts.add(new Mount(acceptedSnapshot, SANDBOX_WORKSPACE, Access.READ_ONLY));
        mounts.add(new Mount(privateHome, SANDBOX_HOME, Access.READ_WRITE));
        mounts.add(new Mount(privateTemporaryDirectory, SANDBOX_TMP, Access.READ_WRITE));
        for (Mount mount : toolchain.mounts()) {
            if (mount.access() != Access.READ_ONLY) {
                throw new IOException("Controller toolchains must be mounted read-only");
            }
            Path source = mount.source();
            if (source.startsWith(candidate) || candidate.startsWith(source)
                    || source.equals(evidenceDirectory) || evidenceDirectory.startsWith(source)) {
                throw new IOException("External toolchain mount overlaps candidate or controller state");
            }
            mounts.add(mount);
        }
        return List.copyOf(mounts);
    }

    private static List<Mount> linuxSystemLibraries() {
        return List.of(
                new Mount(Path.of("/usr/lib"), "/usr/lib", Access.READ_ONLY),
                new Mount(Path.of("/usr/lib64"), "/usr/lib64", Access.READ_ONLY));
    }

    private static String sandboxWorkingDirectory(Path candidate, Path workingDirectory) {
        Path relative = candidate.relativize(workingDirectory);
        if (relative.getNameCount() == 0) {
            return SANDBOX_WORKSPACE;
        }
        StringBuilder value = new StringBuilder(SANDBOX_WORKSPACE);
        for (Path part : relative) {
            value.append('/').append(part);
        }
        return value.toString();
    }

    private static void requireExpectedToolchain(EvidenceCommand command, String actual) throws IOException {
        if (command.requiredToolchainDigest() != null
                && !command.requiredToolchainDigest().equals(actual)) {
            throw new IOException("Resolved toolchain differs from the controller-required digest");
        }
    }

    private static byte[] readBounded(InputStream input, int maximum) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maximum, 8192));
        byte[] bytes = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(bytes)) != -1) {
            total = Math.addExact(total, read);
            if (total > maximum) {
                throw new IOException("Version output exceeds " + maximum + " bytes");
            }
            output.write(bytes, 0, read);
        }
        return output.toByteArray();
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
        return copy;
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

    private static void writeBytesAtomically(
            Path root, String relative, byte[] content, String mode, String temporaryName)
            throws IOException {
        Path target = controlledPath(root, relative);
        Path temporary = controlledPath(root, temporaryName);
        Files.write(temporary, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        boolean moved = false;
        try {
            Files.setPosixFilePermissions(temporary, PosixFilePermissions.fromString(mode));
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(temporary);
            }
        }
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

    private static JdkIdentity controllerJdk() throws IOException {
        Path root = realDirectory(Path.of(System.getProperty("java.home", "")), "controller JDK");
        return jdkIdentity(root);
    }

    private static JdkIdentity freezeControllerJdk(Path sandboxRoot) throws IOException {
        return freezeJdk(
                realDirectory(Path.of(System.getProperty("java.home", "")), "controller JDK"), sandboxRoot);
    }

    static JdkIdentity freezeJdk(Path liveRoot, Path sandboxRoot) throws IOException {
        Path source = realDirectory(liveRoot, "controller JDK");
        JdkIdentity before = jdkIdentity(source);
        Path snapshot = privateDirectory(sandboxRoot.resolve("jdk-snapshot"));
        copyTree(source, snapshot);

        List<Mount> externalSnapshots = new ArrayList<>();
        if (!before.externalMounts().isEmpty()) {
            Path externalRoot = privateDirectory(sandboxRoot.resolve("jdk-external-snapshots"));
            int index = 0;
            for (Mount mount : before.externalMounts()) {
                Path target = externalRoot.resolve(String.format(Locale.ROOT, "%03d", index++));
                copyTree(mount.source(), target);
                externalSnapshots.add(new Mount(target, mount.target(), Access.READ_ONLY));
            }
        }
        JdkIdentity frozen = jdkIdentity(snapshot, externalSnapshots);
        JdkIdentity after = jdkIdentity(source);
        if (!before.digest().equals(frozen.digest()) || !before.digest().equals(after.digest())) {
            throw new IOException("Controller JDK changed while its immutable evidence snapshot was frozen");
        }
        return frozen;
    }

    private static JdkIdentity jdkIdentity(Path root) throws IOException {
        List<Mount> externalMounts = externalJdkMounts(root);
        return jdkIdentity(root, externalMounts);
    }

    private static JdkIdentity jdkIdentity(Path root, List<Mount> externalMounts) throws IOException {
        MessageDigest digest = sha256();
        update(digest, "camel-kit.ship.jdk-tree.v1");
        long[] totals = {0, 0};
        updateTree(digest, root, SANDBOX_JDK, totals);
        for (Mount mount : externalMounts) {
            updateTree(digest, mount.source(), mount.target(), totals);
        }
        if (totals[0] == 0 || !Files.isExecutable(root.resolve("bin/java"))) {
            throw new IOException("Controller JDK tree is incomplete");
        }
        return new JdkIdentity(root, digest(digest), externalMounts);
    }

    private static void copyTree(Path source, Path target) throws IOException {
        if (Files.isSymbolicLink(source)) {
            throw new IOException("Controller JDK snapshot source cannot itself be symbolic");
        }
        if (Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
            copyFile(source, target);
            return;
        }
        if (!Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Controller JDK snapshot source is not regular: " + source);
        }
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            Files.createDirectory(target);
        }
        List<Path> directories = new ArrayList<>();
        try (var paths = Files.walk(source, 64)) {
            for (Path path : paths.sorted().toList()) {
                Path relative = source.relativize(path);
                Path copy = target.resolve(relative.toString()).normalize();
                if (!copy.startsWith(target) || Files.exists(copy, LinkOption.NOFOLLOW_LINKS)
                        && !source.equals(path)) {
                    throw new IOException("Controller JDK snapshot destination is unsafe: " + copy);
                }
                if (Files.isSymbolicLink(path)) {
                    Files.createSymbolicLink(copy, Files.readSymbolicLink(path));
                } else if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                    if (!source.equals(path)) {
                        Files.createDirectory(copy);
                    }
                    directories.add(path);
                } else if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    copyFile(path, copy);
                } else {
                    throw new IOException("Controller JDK contains a non-regular object: " + path);
                }
            }
        }
        for (Path directory : directories.stream()
                .sorted(Comparator.comparingInt(Path::getNameCount).reversed()).toList()) {
            copyPermissions(directory, target.resolve(source.relativize(directory).toString()));
        }
    }

    private static void copyFile(Path source, Path target) throws IOException {
        Path parent = target.getParent();
        if (parent == null || Files.isSymbolicLink(parent)
                || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Controller JDK snapshot file has an unsafe parent");
        }
        try (InputStream input = Files.newInputStream(source, LinkOption.NOFOLLOW_LINKS)) {
            Files.copy(input, target);
        }
        copyPermissions(source, target);
    }

    private static void copyPermissions(Path source, Path target) throws IOException {
        if (Files.getFileAttributeView(source, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS) != null
                && Files.getFileAttributeView(target, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS)
                   != null) {
            Files.setPosixFilePermissions(
                    target, Files.getPosixFilePermissions(source, LinkOption.NOFOLLOW_LINKS));
        } else if (Files.isExecutable(source) && !target.toFile().setExecutable(true, true)) {
            throw new IOException("Could not preserve controller JDK executable permission");
        }
    }

    private static List<Mount> externalJdkMounts(Path root) throws IOException {
        TreeMap<String, Path> mounts = new TreeMap<>();
        Set<String> scanned = new LinkedHashSet<>();
        scanExternalLinks(root, SANDBOX_JDK, root, mounts);
        while (true) {
            Map.Entry<String, Path> next = mounts.entrySet().stream()
                    .filter(entry -> !scanned.contains(entry.getKey()))
                    .findFirst().orElse(null);
            if (next == null) {
                break;
            }
            scanned.add(next.getKey());
            if (Files.isDirectory(next.getValue(), LinkOption.NOFOLLOW_LINKS)) {
                scanExternalLinks(next.getValue(), next.getKey(), root, mounts);
            }
            if (mounts.size() > 64) {
                throw new IOException("Controller JDK has too many external link targets");
            }
        }
        return mounts.entrySet().stream()
                .map(entry -> new Mount(entry.getValue(), entry.getKey(), Access.READ_ONLY))
                .toList();
    }

    private static void scanExternalLinks(
            Path sourceRoot, String sandboxRoot, Path jdkRoot, Map<String, Path> mounts)
            throws IOException {
        try (var paths = Files.walk(sourceRoot, 64)) {
            for (Path path : paths.sorted().toList()) {
                if (!Files.isSymbolicLink(path)) {
                    continue;
                }
                Path link = Files.readSymbolicLink(path);
                Path sandboxPath = Path.of(sandboxRoot)
                        .resolve(sourceRoot.relativize(path).toString()).normalize();
                Path target = link.isAbsolute()
                        ? link.normalize()
                        : sandboxPath.getParent().resolve(link).normalize();
                if (!target.isAbsolute()) {
                    throw new IOException("Controller JDK contains an unresolved symbolic link");
                }
                Path hostTarget;
                if (sandboxRoot.equals(SANDBOX_JDK) && target.startsWith(Path.of(SANDBOX_JDK))) {
                    hostTarget = jdkRoot.resolve(Path.of(SANDBOX_JDK).relativize(target)).normalize();
                } else {
                    hostTarget = target;
                }
                if (hostTarget.startsWith(jdkRoot)) {
                    continue;
                }
                if (!allowedExternalJdkTarget(target)) {
                    throw new IOException("Controller JDK symbolic link escapes to an unapproved target: " + target);
                }
                Path real = hostTarget.toRealPath();
                if (!allowedExternalJdkTarget(real)
                        || Files.isSymbolicLink(real)
                        || (!Files.isRegularFile(real, LinkOption.NOFOLLOW_LINKS)
                                && !Files.isDirectory(real, LinkOption.NOFOLLOW_LINKS))) {
                    throw new IOException("Controller JDK external target is unsafe: " + target);
                }
                Path previous = mounts.putIfAbsent(target.toString(), real);
                if (previous != null && !previous.equals(real)) {
                    throw new IOException("Controller JDK external target identity is ambiguous: " + target);
                }
            }
        }
    }

    private static boolean allowedExternalJdkTarget(Path target) {
        return target.startsWith(Path.of("/etc/java"))
                || target.startsWith(Path.of("/etc/pki"))
                || target.startsWith(Path.of("/usr/share/javazi-1.8"));
    }

    private static void updateTree(
            MessageDigest digest, Path source, String namespace, long[] totals)
            throws IOException {
        if (Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
            boundedJdkFile(totals, source);
            updateFile(digest, namespace, source);
            return;
        }
        try (var paths = Files.walk(source, 64)) {
            for (Path path : paths.sorted().toList()) {
                String relative = source.equals(path)
                        ? ""
                        : source.relativize(path).toString().replace('\\', '/');
                String name = relative.isEmpty() ? namespace : namespace + '/' + relative;
                if (Files.isSymbolicLink(path)) {
                    update(digest, "link:" + name);
                    update(digest, Files.readSymbolicLink(path).toString());
                } else if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                    update(digest, "directory:" + name);
                } else if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    boundedJdkFile(totals, path);
                    updateFile(digest, name, path);
                } else {
                    throw new IOException("Controller JDK contains a non-regular object: " + path);
                }
            }
        }
    }

    private static void boundedJdkFile(long[] totals, Path path) throws IOException {
        totals[0] = Math.addExact(totals[0], 1);
        totals[1] = Math.addExact(totals[1], Files.size(path));
        if (totals[0] > 8_192 || totals[1] > 2L * 1024 * 1024 * 1024) {
            throw new IOException("Controller JDK tree exceeds the evidence identity bounds");
        }
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
        return digest(digest);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static String digest(MessageDigest digest) {
        return "sha256:" + HexFormat.of().formatHex(digest.digest());
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    @FunctionalInterface
    interface ToolchainResolver {

        ResolvedToolchain resolve(
                Path candidate, Path sandboxRoot, EvidenceCommand command, JdkIdentity jdk)
                throws IOException;
    }

    @FunctionalInterface
    interface JdkResolver {

        JdkIdentity freeze(Path sandboxRoot) throws IOException;
    }

    @FunctionalInterface
    interface IdentitySource {

        JvmPayloadArchive.Identity identity() throws IOException;
    }

    record ResolvedToolchain(
            Path executable,
            String sandboxExecutable,
            List<Mount> mounts,
            String digest,
            Path snapshot,
            String snapshotDigest,
            IdentitySource identitySource) {

        ResolvedToolchain {
            mounts = List.copyOf(mounts);
        }
    }

    private static final class ControllerToolchainResolver implements ToolchainResolver {

        @Override
        public ResolvedToolchain resolve(
                Path candidate, Path sandboxRoot, EvidenceCommand command, JdkIdentity jdk)
                throws IOException {
            if (!JAVA_EXECUTABLE.equals(command.arguments().get(0))) {
                throw new IOException(
                        "Only controller-owned direct JVM evidence is supported; Maven, Camel CLI, JBang, "
                                      + "Citrus JBang, and candidate executables are forbidden");
            }
            JvmPayloadArchive.Identity identity
                    = JvmPayloadArchive.materialize(sandboxRoot, command.jvmPayload(), jdk.digest());
            return new ResolvedToolchain(
                    requireExecutable(jdk.root().resolve("bin/java"), "controller JDK java"),
                    JAVA_EXECUTABLE,
                    List.of(new Mount(
                            identity.archive().getParent(), "/opt/camel-kit/payload", Access.READ_ONLY)),
                    identity.aggregateDigest(),
                    identity.archive(),
                    identity.archiveDigest(),
                    () -> JvmPayloadArchive.verify(
                            identity.archive(), command.jvmPayload(),
                            jdkIdentity(jdk.root(), jdk.externalMounts()).digest()));
        }

    }

    private static void updateFile(MessageDigest digest, String name, Path file) throws IOException {
        update(digest, "file:" + name);
        digest.update(ByteBuffer.allocate(Long.BYTES).putLong(Files.size(file)).array());
        update(digest, "executable:" + Files.isExecutable(file));
        try (InputStream input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
    }

    record JdkIdentity(Path root, String digest, List<Mount> externalMounts) {

        JdkIdentity {
            externalMounts = List.copyOf(externalMounts);
        }
    }

    private record VersionResult(String value, boolean reaped, String failure) {
    }

    private record LogFile(Path path, Object fileKey) {
    }
}
