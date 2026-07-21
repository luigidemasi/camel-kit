package io.github.luigidemasi.camelkit.ship.evidence;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.github.luigidemasi.camelkit.ship.evidence.EvidenceSandboxLauncher.Access;
import io.github.luigidemasi.camelkit.ship.evidence.EvidenceSandboxLauncher.Invocation;
import io.github.luigidemasi.camelkit.ship.evidence.EvidenceSandboxLauncher.Mount;
import io.github.luigidemasi.camelkit.ship.security.ProjectEvidenceFiles;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class EvidenceRunnerTest {

    @TempDir
    Path tempDir;

    @Test
    void usesOnlyTheFrozenCandidateAndControllerOwnedJvmPayload() throws Exception {
        Path project = Files.createDirectory(tempDir.resolve("candidate"));
        Path candidateFile = Files.writeString(project.resolve("route.camel.yaml"), "route fixture");
        Path emptyDirectory = Files.createDirectories(project.resolve("config/empty"));
        Files.setPosixFilePermissions(emptyDirectory, PosixFilePermissions.fromString("rwxr-x---"));
        Path candidateSibling = Files.writeString(tempDir.resolve("candidate-sibling.secret"), "secret");
        Path controllerRoot = Files.createDirectories(tempDir.resolve("controller/run"));
        Path controllerSentinel = Files.writeString(controllerRoot.resolve("signing.key"), "secret");
        Path fakeBubblewrap = executable(tempDir.resolve("fake-bwrap"), "fixture");
        RecordingLauncher launcher = new RecordingLauncher(fakeBubblewrap, candidateSibling, controllerSentinel);
        EvidenceCommand command = command(project, "route-schema", Duration.ofSeconds(5));

        CommandEvidence result = runner(launcher).run(project, controllerRoot.resolve("evidence"), command);

        assertTrue(result.passed(), result::toString);
        assertEquals(2, launcher.invocations.size(), "version and evidence commands must both be sandboxed");
        for (Invocation invocation : launcher.invocations) {
            assertFalse(invocation.exposes(candidateSibling));
            assertFalse(invocation.exposes(controllerSentinel));
            Mount workspace = invocation.mounts().stream()
                    .filter(mount -> "/workspace".equals(mount.target()))
                    .findFirst().orElseThrow();
            assertEquals(Access.READ_ONLY, workspace.access());
            assertNotEquals(project.toRealPath(), workspace.source());
            assertEquals(Files.readString(candidateFile),
                    Files.readString(workspace.source().resolve("route.camel.yaml")));
            Path frozenEmptyDirectory = workspace.source().resolve("config/empty");
            assertTrue(Files.isDirectory(frozenEmptyDirectory));
            assertEquals(
                    Files.getPosixFilePermissions(emptyDirectory),
                    Files.getPosixFilePermissions(frozenEmptyDirectory));
            assertTrue(invocation.mounts().stream().noneMatch(mount -> "/usr".equals(mount.target())));
            assertTrue(invocation.mounts().stream().anyMatch(mount -> "/usr/lib".equals(mount.target())));
            assertTrue(invocation.mounts().stream().anyMatch(mount -> "/usr/lib64".equals(mount.target())));
        }
        assertEquals("Camel direct YAML validator 4.21.0", result.executableVersion());
        assertEquals("PASS\n", Files.readString(Path.of(result.stdoutLog())));
        assertEquals(result.toolchainDigest(), result.postToolchainDigest());
        assertNotNull(result.toolchainSnapshot());
        assertEquals("/home/camel-kit", result.controlledEnvironment().get("HOME"));

        List<String> bubblewrapArguments = BubblewrapSandboxLauncher.arguments(
                fakeBubblewrap, launcher.invocations.get(1));
        assertTrue(bubblewrapArguments.containsAll(List.of(
                "--unshare-user", "--unshare-pid", "--unshare-ipc", "--unshare-uts",
                "--unshare-cgroup", "--unshare-net", "--die-with-parent", "--new-session", "--clearenv")));
        assertTrue(bubblewrapArguments.containsAll(List.of("usr/lib", "/lib", "usr/lib64", "/lib64")));
        assertFalse(bubblewrapArguments.contains("/bin"));
        assertFalse(bubblewrapArguments.contains("/sbin"));
        int separator = bubblewrapArguments.indexOf("--");
        assertEquals(command.arguments(), bubblewrapArguments.subList(separator + 1, bubblewrapArguments.size()));
        assertFalse(bubblewrapArguments.contains("-c"));

        Path rawStdout = Path.of(result.stdoutLog());
        Path rawStderr = Path.of(result.stderrLog());
        EvidenceRunner.cleanupEphemeral(controllerRoot.resolve("evidence"), command, result);
        assertFalse(Files.exists(rawStdout));
        assertFalse(Files.exists(rawStderr));
        try (var entries = Files.list(controllerRoot.resolve("evidence"))) {
            assertTrue(entries.noneMatch(path -> path.getFileName().toString().contains("-sandbox-")));
        }
    }

    @Test
    void recordsNonzeroAndTimeoutOutcomesFromSandbox() throws Exception {
        Path project = Files.createDirectory(tempDir.resolve("candidate"));
        Path fakeBubblewrap = executable(tempDir.resolve("fake-bwrap"), "fixture");
        RecordingLauncher nonzero = new RecordingLauncher(fakeBubblewrap, null, null);
        nonzero.executionExitCode = 7;
        CommandEvidence failed = runner(nonzero).run(
                project, tempDir.resolve("evidence-a"), command(project, "build-check", Duration.ofSeconds(2)));
        assertFalse(failed.passed());
        assertEquals(7, failed.exitCode());

        RecordingLauncher timeout = new RecordingLauncher(fakeBubblewrap, null, null);
        timeout.executionCompletes = false;
        CommandEvidence timedOut = runner(timeout).run(
                project, tempDir.resolve("evidence-b"), command(project, "slow-check", Duration.ofMillis(10)));
        assertFalse(timedOut.passed());
        assertTrue(timedOut.timedOut());
        assertNull(timedOut.exitCode());
        assertNotNull(timeout.lastProcess);
        assertTrue(timeout.lastProcess.destroyed);
        assertFalse(timeout.lastProcess.isAlive());
    }

    @Test
    void failedVersionQueryCannotLaunchOrPassOnStderrNoise() throws Exception {
        Path project = Files.createDirectory(tempDir.resolve("candidate"));
        Path fakeBubblewrap = executable(tempDir.resolve("fake-bwrap"), "fixture");
        RecordingLauncher launcher = new RecordingLauncher(fakeBubblewrap, null, null);
        launcher.versionStdout = "misleading version\n";
        launcher.versionExitCode = 9;

        CommandEvidence result = runner(launcher).run(
                project, tempDir.resolve("evidence"), command(project, "version-check", Duration.ofSeconds(2)));

        assertFalse(result.passed());
        assertFalse(result.launched());
        assertNull(result.exitCode());
        assertEquals("exit-9", result.executableVersion());
        assertTrue(result.launchError().contains("Version query failed: exit-9"));
        assertEquals(1, launcher.invocations.size(), "the evidence command must not launch after a failed query");
    }

    @Test
    void zeroExitVersionQueryWithOnlyStderrCannotLaunchOrPass() throws Exception {
        Path project = Files.createDirectory(tempDir.resolve("candidate"));
        Path fakeBubblewrap = executable(tempDir.resolve("fake-bwrap"), "fixture");
        RecordingLauncher launcher = new RecordingLauncher(fakeBubblewrap, null, null);
        launcher.versionStdout = " \n";
        launcher.versionStderr = "Picked up JAVA_TOOL_OPTIONS: fixture\n";

        CommandEvidence result = runner(launcher).run(
                project, tempDir.resolve("evidence"), command(project, "version-check", Duration.ofSeconds(2)));

        assertFalse(result.passed());
        assertFalse(result.launched());
        assertNull(result.exitCode());
        assertEquals("unreported", result.executableVersion());
        assertTrue(result.launchError().contains("Version query failed: unreported"));
        assertEquals(1, launcher.invocations.size(), "stderr must not become executable version evidence");
    }

    @Test
    void rejectsReuseOfAnotherRunsEvidenceDirectoryWithoutDeletingIt() throws Exception {
        Path project = Files.createDirectory(tempDir.resolve("candidate"));
        Path evidenceDirectory = tempDir.resolve("evidence");
        Path fakeBubblewrap = executable(tempDir.resolve("fake-bwrap"), "fixture");
        EvidenceCommand command = command(project, "first-check", Duration.ofSeconds(2));
        RecordingLauncher firstLauncher = new RecordingLauncher(fakeBubblewrap, null, null);
        CommandEvidence first = runner(firstLauncher).run(project, evidenceDirectory, command);
        Path firstSandbox = firstLauncher.invocations.get(1).privateHome().getParent();

        IOException rejected = assertThrows(IOException.class, () -> runner(
                new RecordingLauncher(fakeBubblewrap, null, null))
                .run(project, evidenceDirectory, command(project, "sibling-check", Duration.ofSeconds(2))));

        assertTrue(rejected.getMessage().contains("new and exclusive"));
        assertTrue(Files.isRegularFile(Path.of(first.stdoutLog())));
        assertTrue(Files.isRegularFile(Path.of(first.stderrLog())));
        assertTrue(Files.isDirectory(firstSandbox));
    }

    @Test
    void midRunAuthorityTamperAlwaysFailsClosed() throws Exception {
        Path project = Files.createDirectory(tempDir.resolve("candidate"));
        for (AuthorityTamper tamper : AuthorityTamper.values()) {
            String id = tamper.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
            Path fakeBubblewrap = executable(
                    tempDir.resolve("fake-bwrap-" + id), "fixture");
            RecordingLauncher launcher = new RecordingLauncher(fakeBubblewrap, null, null);
            launcher.executionTamper = tamper;

            CommandEvidence result = runner(launcher).run(
                    project,
                    tempDir.resolve("evidence-" + id),
                    command(project, "tamper-" + id, Duration.ofSeconds(2)));

            assertFalse(result.passed(), tamper.name());
            switch (tamper) {
                case PAYLOAD_ARCHIVE -> assertNull(result.postToolchainDigest());
                case TOOLCHAIN_EXECUTABLE ->
                    assertNotEquals(result.executableDigest(), result.postExecutableDigest());
                case SANDBOX_EXECUTABLE -> assertNotEquals(
                        result.sandbox().executableDigest(), result.sandbox().postExecutableDigest());
            }
        }
    }

    @Test
    void interruptionStillForciblyReapsAParentThatIgnoresGracefulTermination() throws Exception {
        Path project = Files.createDirectory(tempDir.resolve("candidate"));
        Path fakeBubblewrap = executable(tempDir.resolve("fake-bwrap"), "fixture");
        RecordingLauncher launcher = new RecordingLauncher(fakeBubblewrap, null, null);
        launcher.executionCompletes = false;
        launcher.executionInterruptsWait = true;
        launcher.executionRequiresForcibleTermination = true;

        CommandEvidence interrupted = runner(launcher).run(
                project, tempDir.resolve("evidence"), command(project, "interrupted-check", Duration.ofSeconds(2)));
        boolean interruptPreserved = Thread.interrupted();

        assertFalse(interrupted.passed());
        assertTrue(interrupted.launchError().contains("Interrupted while waiting for the command"));
        assertNotNull(launcher.lastProcess);
        assertTrue(launcher.lastProcess.destroyed);
        assertTrue(launcher.lastProcess.forciblyDestroyed);
        assertFalse(launcher.lastProcess.isAlive());
        assertTrue(interruptPreserved, "the caller's interrupted status must be preserved");
    }

    @Test
    void interruptionReportsAParentThatCannotBeReaped() throws Exception {
        Path project = Files.createDirectory(tempDir.resolve("candidate"));
        Path fakeBubblewrap = executable(tempDir.resolve("fake-bwrap"), "fixture");
        Path evidenceRoot = Files.createDirectory(tempDir.resolve("evidence"));
        Path evidenceDirectory = evidenceRoot.resolve("unreaped-run");
        RecordingLauncher launcher = new RecordingLauncher(fakeBubblewrap, null, null);
        launcher.executionCompletes = false;
        launcher.executionInterruptsWait = true;
        launcher.executionRequiresForcibleTermination = true;
        launcher.executionIgnoresForcibleTermination = true;

        CommandEvidence interrupted = runner(launcher).run(
                project, evidenceDirectory, command(project, "unreaped-check", Duration.ofSeconds(2)));
        boolean interruptPreserved = Thread.interrupted();
        Path retainedSandbox = launcher.invocations.get(1).privateHome().getParent();
        Path retainedStdout = Path.of(interrupted.stdoutLog());
        Path retainedStderr = Path.of(interrupted.stderrLog());

        assertTrue(interrupted.launchError().contains("Interrupted command process tree could not be reaped"));
        assertTrue(interrupted.launchError().contains("Ephemeral evidence quarantined at"));
        assertNotNull(launcher.lastProcess);
        assertTrue(launcher.lastProcess.forciblyDestroyed);
        assertTrue(launcher.lastProcess.isAlive());
        assertTrue(interruptPreserved, "the caller's interrupted status must be preserved");

        EvidenceRunner.cleanupEphemeral(
                evidenceDirectory, command(project, "unreaped-check", Duration.ofSeconds(2)), interrupted);
        assertTrue(Files.isDirectory(retainedSandbox));
        assertTrue(Files.isRegularFile(retainedStdout));
        assertTrue(Files.isRegularFile(retainedStderr));

        RecordingLauncher nextLauncher = new RecordingLauncher(fakeBubblewrap, null, null);
        EvidenceCommand nextCommand = command(project, "next-check", Duration.ofSeconds(2));
        Path nextEvidenceDirectory = evidenceRoot.resolve("next-run");
        CommandEvidence next = runner(nextLauncher).run(project, nextEvidenceDirectory, nextCommand);
        EvidenceRunner.cleanupEphemeral(nextEvidenceDirectory, nextCommand, next);

        assertTrue(Files.isDirectory(retainedSandbox), "a sibling run must preserve quarantined sandboxes");
        assertTrue(Files.isRegularFile(retainedStdout), "a sibling run must preserve quarantined stdout");
        assertTrue(Files.isRegularFile(retainedStderr), "a sibling run must preserve quarantined stderr");
    }

    @Test
    void unreapedVersionQueryIsQuarantinedBeforeTheCommandCanLaunch() throws Exception {
        Path project = Files.createDirectory(tempDir.resolve("candidate"));
        Path fakeBubblewrap = executable(tempDir.resolve("fake-bwrap"), "fixture");
        Path evidenceDirectory = tempDir.resolve("evidence");
        RecordingLauncher launcher = new RecordingLauncher(fakeBubblewrap, null, null);
        launcher.versionCompletes = false;
        launcher.versionInterruptsWait = true;
        launcher.versionRequiresForcibleTermination = true;
        launcher.versionIgnoresForcibleTermination = true;
        EvidenceCommand evidenceCommand = command(project, "version-check", Duration.ofSeconds(2));

        CommandEvidence result = runner(launcher).run(project, evidenceDirectory, evidenceCommand);
        boolean interruptPreserved = Thread.interrupted();
        Path retainedSandbox = launcher.invocations.get(0).privateHome().getParent();

        assertEquals(1, launcher.invocations.size(),
                "the evidence command must not launch after a residual version process");
        assertFalse(result.launched());
        assertTrue(result.launchError().contains("Version query process tree could not be reaped"));
        assertTrue(result.launchError().contains("Ephemeral evidence quarantined at"));
        assertTrue(interruptPreserved, "the caller's interrupted status must be preserved");

        EvidenceRunner.cleanupEphemeral(evidenceDirectory, evidenceCommand, result);
        assertTrue(Files.isDirectory(retainedSandbox));
        assertTrue(Files.isRegularFile(Path.of(result.stdoutLog())));
        assertTrue(Files.isRegularFile(Path.of(result.stderrLog())));
    }

    @Test
    void failsClosedForMissingSandboxLegacyExecutableAndWorkingDirectoryEscape() throws Exception {
        Path project = Files.createDirectory(tempDir.resolve("candidate"));
        EvidenceCommand command = command(project, "build-check", Duration.ofSeconds(1));
        EvidenceRunner missingSandbox = new EvidenceRunner(
                Clock.systemUTC(), new BubblewrapSandboxLauncher(tempDir.resolve("missing-bwrap")),
                new FixtureToolchainResolver(), new FixtureJdkResolver());
        assertThrows(Exception.class, () -> missingSandbox.run(
                project, tempDir.resolve("evidence-a"), command));
        try (var entries = Files.list(tempDir.resolve("evidence-a"))) {
            assertTrue(entries.noneMatch(path -> path.getFileName().toString().contains("-sandbox-")));
        }

        Path fakeBubblewrap = executable(tempDir.resolve("fake-bwrap"), "fixture");
        RecordingLauncher launcher = new RecordingLauncher(fakeBubblewrap, null, null);
        Exception legacyFailure = assertThrows(IllegalArgumentException.class, () -> new EvidenceCommand(
                "legacy", List.of("/usr/bin/mvn", "--version"), List.of(), null,
                Duration.ofSeconds(1), List.of(ProjectEvidenceFiles.capture(project).digest()),
                Map.of(), List.of(), null, null));
        assertTrue(legacyFailure.getMessage().contains("direct JVM"));

        EvidenceCommand escaped = new EvidenceCommand(
                "escaped", command.arguments(), command.versionArguments(), "..", Duration.ofSeconds(1),
                command.inputDigests(), Map.of(), List.of(), null, command.jvmPayload());
        assertThrows(Exception.class, () -> runner(launcher)
                .run(project, tempDir.resolve("evidence-c"), escaped));
        assertTrue(launcher.invocations.isEmpty());
    }

    @Test
    void freezesIndependentExactJdkBytesWithoutFollowingInternalLinks() throws Exception {
        Path live = Files.createDirectories(tempDir.resolve("live-jdk"));
        Path bin = Files.createDirectory(live.resolve("bin"));
        Path java = executable(bin.resolve("java"), "java-bytes");
        Path lib = Files.createDirectories(live.resolve("lib/security"));
        Path policy = Files.writeString(lib.resolve("default.policy"), "accepted-policy");
        Path conf = Files.createDirectory(live.resolve("conf"));
        Files.createSymbolicLink(conf.resolve("security"), Path.of("../lib/security"));
        Path sandbox = Files.createDirectory(tempDir.resolve("jdk-sandbox"));

        EvidenceRunner.JdkIdentity frozen = EvidenceRunner.freezeJdk(live, sandbox);
        Files.writeString(java, "mutated-java");
        Files.writeString(policy, "mutated-policy");

        assertNotEquals(live.toRealPath(), frozen.root());
        assertEquals("java-bytes", Files.readString(frozen.root().resolve("bin/java")));
        assertEquals("accepted-policy", Files.readString(frozen.root().resolve("lib/security/default.policy")));
        assertTrue(Files.isSymbolicLink(frozen.root().resolve("conf/security")));
        assertEquals(Path.of("../lib/security"), Files.readSymbolicLink(frozen.root().resolve("conf/security")));
        assertTrue(frozen.digest().matches("sha256:[0-9a-f]{64}"));
        assertTrue(frozen.externalMounts().isEmpty());
    }

    private EvidenceRunner runner(RecordingLauncher launcher) throws IOException {
        Path libraries = Files.createDirectories(tempDir.resolve("fixture-system-libraries"));
        Path lib = Files.createDirectories(libraries.resolve("lib"));
        Path lib64 = Files.createDirectories(libraries.resolve("lib64"));
        return new EvidenceRunner(
                Clock.systemUTC(), launcher, new FixtureToolchainResolver(), new FixtureJdkResolver(),
                List.of(
                        new Mount(lib, "/usr/lib", Access.READ_ONLY),
                        new Mount(lib64, "/usr/lib64", Access.READ_ONLY)));
    }

    private EvidenceCommand command(Path project, String id, Duration timeout) throws Exception {
        JvmPayloadRequest payload = JvmPayloadRequest.yamlValidator("4.21.0");
        List<String> arguments = List.of(
                EvidenceRunner.JAVA_EXECUTABLE, "-cp", JvmPayloadArchive.SANDBOX_ARCHIVE,
                ShipJvmPayloadBootstrap.class.getName());
        List<String> version = new ArrayList<>(arguments);
        version.add("--payload-version");
        return new EvidenceCommand(
                id, arguments, version, null, timeout,
                List.of(ProjectEvidenceFiles.capture(project).digest()),
                Map.of("LANG", "C", "LC_ALL", "C"), List.of(), null, payload);
    }

    private static Path executable(Path path, String content) throws Exception {
        Files.writeString(path, content);
        assertTrue(path.toFile().setExecutable(true, true) || Files.isExecutable(path));
        return path.toRealPath();
    }

    private static final class FixtureToolchainResolver implements EvidenceRunner.ToolchainResolver {

        @Override
        public EvidenceRunner.ResolvedToolchain resolve(
                Path candidate,
                Path sandboxRoot,
                EvidenceCommand command,
                EvidenceRunner.JdkIdentity jdk)
                throws java.io.IOException {
            String jdkDigest = jdk.digest();
            JvmPayloadArchive.Identity identity = JvmPayloadTestFixture.create(
                    sandboxRoot.resolve("fixture-payload"), command.jvmPayload(), jdkDigest);
            Path java = jdk.root().resolve("bin/java").toRealPath();
            return new EvidenceRunner.ResolvedToolchain(
                    java, EvidenceRunner.JAVA_EXECUTABLE,
                    List.of(new Mount(identity.archive().getParent(), "/opt/camel-kit/payload", Access.READ_ONLY)),
                    identity.aggregateDigest(), identity.archive(), identity.archiveDigest(),
                    () -> JvmPayloadArchive.verify(identity.archive(), command.jvmPayload(), jdkDigest));
        }
    }

    private static final class FixtureJdkResolver implements EvidenceRunner.JdkResolver {

        @Override
        public EvidenceRunner.JdkIdentity freeze(Path sandboxRoot) throws java.io.IOException {
            Path root = Files.createDirectories(sandboxRoot.resolve("fixture-jdk"));
            Path bin = Files.createDirectory(root.resolve("bin"));
            Path java = Files.writeString(bin.resolve("java"), "fixture-java");
            if (!java.toFile().setExecutable(true, true) && !Files.isExecutable(java)) {
                throw new java.io.IOException("Could not create fixture Java executable");
            }
            return new EvidenceRunner.JdkIdentity(
                    root.toRealPath(), "sha256:" + "3".repeat(64), List.of());
        }
    }

    private static final class RecordingLauncher implements EvidenceSandboxLauncher {

        private final Path executable;
        private final Path candidateSentinel;
        private final Path controllerSentinel;
        private final List<Invocation> invocations = new ArrayList<>();
        private int executionExitCode;
        private String versionStdout = "Camel direct YAML validator 4.21.0\n";
        private String versionStderr = "Picked up JAVA_TOOL_OPTIONS: fixture\n";
        private int versionExitCode;
        private boolean versionCompletes = true;
        private boolean versionInterruptsWait;
        private boolean versionRequiresForcibleTermination;
        private boolean versionIgnoresForcibleTermination;
        private boolean executionCompletes = true;
        private boolean executionInterruptsWait;
        private boolean executionRequiresForcibleTermination;
        private boolean executionIgnoresForcibleTermination;
        private AuthorityTamper executionTamper;
        private FakeProcess lastProcess;

        private RecordingLauncher(Path executable, Path candidateSentinel, Path controllerSentinel) {
            this.executable = executable;
            this.candidateSentinel = candidateSentinel;
            this.controllerSentinel = controllerSentinel;
        }

        @Override
        public Identity identity() {
            return new Identity(EvidenceRunner.SANDBOX_PROVIDER, executable);
        }

        @Override
        public String profileId() {
            return BubblewrapSandboxLauncher.PROFILE_ID;
        }

        @Override
        public Process launch(Invocation invocation) throws IOException {
            invocations.add(invocation);
            if (candidateSentinel != null) {
                assertFalse(invocation.exposes(candidateSentinel));
            }
            if (controllerSentinel != null) {
                assertFalse(invocation.exposes(controllerSentinel));
            }
            boolean version = invocation.arguments().contains("--payload-version");
            CheckedAction completionAction = !version && executionTamper != null
                    ? () -> executionTamper.apply(invocation, executable)
                    : null;
            boolean completes = version ? versionCompletes : executionCompletes;
            if (completionAction != null) {
                completes = false;
            }
            lastProcess = new FakeProcess(
                    version ? versionStdout : "PASS\n",
                    version ? versionStderr : "", version ? versionExitCode : executionExitCode,
                    completes,
                    version ? versionInterruptsWait : executionInterruptsWait,
                    version ? versionRequiresForcibleTermination : executionRequiresForcibleTermination,
                    version ? versionIgnoresForcibleTermination : executionIgnoresForcibleTermination,
                    completionAction);
            return lastProcess;
        }
    }

    @FunctionalInterface
    private interface CheckedAction {

        void run() throws IOException;
    }

    private enum AuthorityTamper {
        PAYLOAD_ARCHIVE,
        TOOLCHAIN_EXECUTABLE,
        SANDBOX_EXECUTABLE;

        private void apply(Invocation invocation, Path sandboxExecutable) throws IOException {
            switch (this) {
                case PAYLOAD_ARCHIVE -> {
                    Path archive = invocation.mounts().stream()
                            .filter(mount -> "/opt/camel-kit/payload".equals(mount.target()))
                            .findFirst().orElseThrow()
                            .source().resolve("payload.jar");
                    Files.delete(archive);
                    Files.writeString(archive, "mutated-payload");
                }
                case TOOLCHAIN_EXECUTABLE -> Files.writeString(
                        invocation.toolchainExecutable(), "mutated-toolchain");
                case SANDBOX_EXECUTABLE -> Files.writeString(sandboxExecutable, "mutated-sandbox");
            }
        }
    }

    private static final class FakeProcess extends Process {

        private final InputStream stdout;
        private final InputStream stderr;
        private final int exitCode;
        private boolean alive;
        private boolean destroyed;
        private boolean interruptWait;
        private final boolean requiresForcibleTermination;
        private final boolean ignoresForcibleTermination;
        private final CheckedAction completionAction;
        private boolean completionActionRun;
        private boolean forciblyDestroyed;

        private FakeProcess(
                            String stdout,
                            String stderr,
                            int exitCode,
                            boolean completes,
                            boolean interruptWait,
                            boolean requiresForcibleTermination,
                            boolean ignoresForcibleTermination,
                            CheckedAction completionAction) {
            this.stdout = new ByteArrayInputStream(stdout.getBytes(StandardCharsets.UTF_8));
            this.stderr = new ByteArrayInputStream(stderr.getBytes(StandardCharsets.UTF_8));
            this.exitCode = exitCode;
            this.alive = !completes;
            this.interruptWait = interruptWait;
            this.requiresForcibleTermination = requiresForcibleTermination;
            this.ignoresForcibleTermination = ignoresForcibleTermination;
            this.completionAction = completionAction;
        }

        @Override
        public OutputStream getOutputStream() {
            return new ByteArrayOutputStream();
        }

        @Override
        public InputStream getInputStream() {
            return stdout;
        }

        @Override
        public InputStream getErrorStream() {
            return stderr;
        }

        @Override
        public int waitFor() {
            alive = false;
            return exitCode;
        }

        @Override
        public boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException {
            if (interruptWait) {
                interruptWait = false;
                throw new InterruptedException("fixture interruption");
            }
            if (Thread.interrupted()) {
                throw new InterruptedException("fixture observed caller interruption");
            }
            if (completionAction != null && !completionActionRun) {
                completionActionRun = true;
                try {
                    completionAction.run();
                } catch (IOException e) {
                    throw new AssertionError("Could not apply execution-time authority tamper", e);
                }
                alive = false;
            }
            return !alive;
        }

        @Override
        public int exitValue() {
            if (alive)
                throw new IllegalThreadStateException();
            return exitCode;
        }

        @Override
        public void destroy() {
            destroyed = true;
            if (!requiresForcibleTermination) {
                alive = false;
            }
        }

        @Override
        public Process destroyForcibly() {
            forciblyDestroyed = true;
            if (!ignoresForcibleTermination) {
                alive = false;
            }
            return this;
        }

        @Override
        public boolean isAlive() {
            return alive;
        }
    }
}
