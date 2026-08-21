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

import io.github.luigidemasi.camelkit.ship.security.ProjectEvidenceFiles;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class EvidenceRunnerTest {

    @TempDir
    Path tempDir;

    @Test
    void runsDirectlyOnAFrozenReadOnlySnapshotWithAScrubbedEnvironment() throws Exception {
        Path project = Files.createDirectory(tempDir.resolve("candidate"));
        Path candidateFile = Files.writeString(project.resolve("route.camel.yaml"), "route fixture");
        Files.createDirectories(project.resolve("config/empty"));
        Path controllerRoot = Files.createDirectories(tempDir.resolve("controller/run"));
        RecordingLauncher launcher = new RecordingLauncher();
        EvidenceCommand command = command(project, "route-schema", Duration.ofSeconds(5));

        CommandEvidence result = runner(launcher).run(project, controllerRoot.resolve("evidence"), command);

        assertTrue(result.passed(), result::toString);
        assertEquals(1, launcher.launches.size(), "exactly one direct evidence launch is expected");
        EvidenceRunner.Launch launch = launcher.launches.get(0);
        Path snapshot = launch.workingDirectory();
        assertNotEquals(project.toRealPath(), snapshot);
        assertTrue(snapshot.startsWith(controllerRoot.resolve("evidence").toRealPath()));
        assertEquals(Files.readString(candidateFile), Files.readString(snapshot.resolve("route.camel.yaml")));
        assertEquals(
                PosixFilePermissions.fromString("r--------"),
                Files.getPosixFilePermissions(snapshot.resolve("route.camel.yaml")));
        assertEquals(
                PosixFilePermissions.fromString("r-x------"),
                Files.getPosixFilePermissions(snapshot));
        assertTrue(Files.isDirectory(snapshot.resolve("config/empty")));
        assertEquals(
                PosixFilePermissions.fromString("r-x------"),
                Files.getPosixFilePermissions(snapshot.resolve("config/empty")));

        Path sandbox = snapshot.getParent();
        assertEquals(sandbox, result.sandboxRoot());
        assertEquals(EvidenceRunner.JAVA_EXECUTABLE, result.executable());
        assertEquals(Path.of(EvidenceRunner.JAVA_EXECUTABLE).toRealPath().toString(), launch.arguments().get(0));
        assertEquals("-Duser.home=" + sandbox.resolve("home"), launch.arguments().get(1));
        assertEquals("-Djava.io.tmpdir=" + sandbox.resolve("tmp"), launch.arguments().get(2));
        assertEquals("-cp", launch.arguments().get(3));
        Path payloadArchive = Path.of(launch.arguments().get(4));
        assertEquals(JvmPayloadArchive.ARCHIVE_NAME, payloadArchive.getFileName().toString());
        assertTrue(payloadArchive.startsWith(sandbox), "payload archive must live in the run's sandbox");
        assertTrue(Files.isRegularFile(payloadArchive));
        assertEquals(ShipJvmPayloadBootstrap.class.getName(), launch.arguments().get(5));
        assertEquals("--launcher=" + command.jvmPayload().launcherClass(), launch.arguments().get(6));
        assertEquals("--accepted-root=" + snapshot, launch.arguments().get(7));
        assertEquals(8, launch.arguments().size());

        assertEquals(
                Map.of(
                        "LANG", "C",
                        "LC_ALL", "C",
                        "HOME", sandbox.resolve("home").toString(),
                        "TMPDIR", sandbox.resolve("tmp").toString()),
                launch.environment(),
                "the child environment must be exactly the scrubbed controller set");
        assertEquals("PASS\n", Files.readString(Path.of(result.stdoutLog())));
        assertFalse(result.quarantined());

        Path rawStdout = Path.of(result.stdoutLog());
        Path rawStderr = Path.of(result.stderrLog());
        EvidenceRunner.cleanupEphemeral(result);
        assertFalse(Files.exists(rawStdout));
        assertFalse(Files.exists(rawStderr));
        assertFalse(Files.exists(controllerRoot.resolve("evidence")));
    }

    @Test
    void abandonedEvidenceCleanupDoesNotFollowLinksOutsideItsExclusiveRoot() throws Exception {
        Path evidenceDirectory = Files.createDirectory(tempDir.resolve("partial-evidence"));
        Path outside = Files.createDirectory(tempDir.resolve("outside"));
        Path sentinel = Files.writeString(outside.resolve("sentinel"), "preserve");
        Files.writeString(evidenceDirectory.resolve("partial.log"), "partial");
        Files.createSymbolicLink(evidenceDirectory.resolve("outside-link"), outside);

        EvidenceRunner.cleanupAbandoned(evidenceDirectory);

        assertFalse(Files.exists(evidenceDirectory, java.nio.file.LinkOption.NOFOLLOW_LINKS));
        assertEquals("preserve", Files.readString(sentinel));
    }

    @Test
    void recordsNonzeroAndTimeoutOutcomes() throws Exception {
        Path project = Files.createDirectory(tempDir.resolve("candidate"));
        RecordingLauncher nonzero = new RecordingLauncher();
        nonzero.exitCode = 7;
        CommandEvidence failed = runner(nonzero).run(
                project, tempDir.resolve("evidence-a"), command(project, "build-check", Duration.ofSeconds(2)));
        assertFalse(failed.passed());
        assertEquals(7, failed.exitCode());
        assertFalse(failed.quarantined());

        RecordingLauncher timeout = new RecordingLauncher();
        timeout.completes = false;
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
    void rejectsReuseOfAnotherRunsEvidenceDirectoryWithoutDeletingIt() throws Exception {
        Path project = Files.createDirectory(tempDir.resolve("candidate"));
        Path evidenceDirectory = tempDir.resolve("evidence");
        EvidenceCommand command = command(project, "first-check", Duration.ofSeconds(2));
        RecordingLauncher firstLauncher = new RecordingLauncher();
        CommandEvidence first = runner(firstLauncher).run(project, evidenceDirectory, command);
        Path firstSandbox = first.sandboxRoot();

        IOException rejected = assertThrows(IOException.class, () -> runner(new RecordingLauncher())
                .run(project, evidenceDirectory, command(project, "sibling-check", Duration.ofSeconds(2))));

        assertTrue(rejected.getMessage().contains("new and exclusive"));
        assertTrue(Files.isRegularFile(Path.of(first.stdoutLog())));
        assertTrue(Files.isRegularFile(Path.of(first.stderrLog())));
        assertTrue(Files.isDirectory(firstSandbox));
    }

    @Test
    void acceptsSymlinkedEvidenceParentButRejectsSymlinkLeaf() throws Exception {
        Path project = Files.createDirectory(tempDir.resolve("candidate"));
        Path realParent = Files.createDirectory(tempDir.resolve("real-evidence-parent"));
        Path parentAlias = Files.createSymbolicLink(tempDir.resolve("evidence-parent-alias"), realParent);
        Path evidenceDirectory = parentAlias.resolve("evidence");
        EvidenceCommand aliasCommand = command(project, "alias-check", Duration.ofSeconds(2));

        CommandEvidence result = runner(new RecordingLauncher()).run(
                project, evidenceDirectory, aliasCommand);

        Path realEvidenceDirectory = realParent.resolve("evidence").toRealPath();
        assertTrue(result.passed(), result::toString);
        assertEquals(realEvidenceDirectory, Path.of(result.stdoutLog()).getParent());
        assertEquals(
                PosixFilePermissions.fromString("rwx------"),
                Files.getPosixFilePermissions(realEvidenceDirectory));
        EvidenceRunner.cleanupEphemeral(result);
        assertFalse(Files.exists(realEvidenceDirectory));

        Path target = Files.createDirectory(realParent.resolve("existing-target"));
        Path symlinkLeaf = parentAlias.resolve("symlink-evidence");
        Files.createSymbolicLink(symlinkLeaf, target);
        RecordingLauncher rejectedLauncher = new RecordingLauncher();

        IOException rejected = assertThrows(IOException.class, () -> runner(rejectedLauncher).run(
                project, symlinkLeaf, command(project, "symlink-check", Duration.ofSeconds(2))));

        assertTrue(rejected.getMessage().contains("new and exclusive"));
        assertTrue(Files.isSymbolicLink(symlinkLeaf));
        assertTrue(Files.isDirectory(target));
        assertTrue(rejectedLauncher.launches.isEmpty());
    }

    @Test
    void createsMissingEvidenceParentDirectories() throws Exception {
        Path project = Files.createDirectory(tempDir.resolve("candidate"));
        Path evidenceDirectory = tempDir.resolve("missing/nested/evidence");
        EvidenceCommand command = command(project, "nested-parent-check", Duration.ofSeconds(2));

        CommandEvidence result = runner(new RecordingLauncher()).run(
                project, evidenceDirectory, command);

        assertTrue(result.passed(), result::toString);
        assertEquals(evidenceDirectory.toRealPath(), Path.of(result.stdoutLog()).getParent());
        assertEquals(
                PosixFilePermissions.fromString("rwx------"),
                Files.getPosixFilePermissions(evidenceDirectory));
        EvidenceRunner.cleanupEphemeral(result);
        assertFalse(Files.exists(evidenceDirectory));
        assertTrue(Files.isDirectory(tempDir.resolve("missing/nested")));
    }

    @Test
    void canonicalizesProjectPathsBelowASymlinkedAncestor() throws Exception {
        Path realParent = Files.createDirectory(tempDir.resolve("real-project-parent"));
        Path project = Files.createDirectory(realParent.resolve("candidate"));
        Path parentAlias = Files.createSymbolicLink(tempDir.resolve("project-parent-alias"), realParent);
        Path projectAlias = parentAlias.resolve("candidate");
        RecordingLauncher launcher = new RecordingLauncher();

        CommandEvidence result = runner(launcher).run(
                projectAlias,
                tempDir.resolve("evidence"),
                command(projectAlias, "project-alias-check", Duration.ofSeconds(2)));

        assertTrue(result.passed(), result::toString);
        assertEquals(project.toRealPath().toString(), result.workingDirectory());
        assertEquals(
                result.sandboxRoot().resolve("accepted-snapshot"),
                launcher.launches.get(0).workingDirectory());
    }

    @Test
    void rejectsEvidenceNestedInTheProjectThroughAnAlternateAlias() throws Exception {
        Path realParent = Files.createDirectory(tempDir.resolve("real-project-parent"));
        Path project = Files.createDirectory(realParent.resolve("candidate"));
        Path parentAlias = Files.createSymbolicLink(tempDir.resolve("project-parent-alias"), realParent);
        Path nestedEvidenceAlias = parentAlias.resolve("candidate/evidence");
        RecordingLauncher launcher = new RecordingLauncher();

        assertThrows(IOException.class, () -> runner(launcher).run(
                project,
                nestedEvidenceAlias,
                command(project, "nested-evidence-check", Duration.ofSeconds(2))));

        assertFalse(Files.exists(project.resolve("evidence")));
        assertTrue(launcher.launches.isEmpty());
    }

    @Test
    void interruptionStillForciblyReapsAParentThatIgnoresGracefulTermination() throws Exception {
        Path project = Files.createDirectory(tempDir.resolve("candidate"));
        RecordingLauncher launcher = new RecordingLauncher();
        launcher.completes = false;
        launcher.interruptsWait = true;
        launcher.requiresForcibleTermination = true;

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
    void interruptionReportsAndQuarantinesAParentThatCannotBeReaped() throws Exception {
        Path project = Files.createDirectory(tempDir.resolve("candidate"));
        Path evidenceRoot = Files.createDirectory(tempDir.resolve("evidence"));
        Path evidenceDirectory = evidenceRoot.resolve("unreaped-run");
        RecordingLauncher launcher = new RecordingLauncher();
        launcher.completes = false;
        launcher.interruptsWait = true;
        launcher.requiresForcibleTermination = true;
        launcher.ignoresForcibleTermination = true;

        CommandEvidence interrupted = runner(launcher).run(
                project, evidenceDirectory, command(project, "unreaped-check", Duration.ofSeconds(2)));
        boolean interruptPreserved = Thread.interrupted();
        Path retainedSandbox = interrupted.sandboxRoot();
        Path retainedStdout = Path.of(interrupted.stdoutLog());
        Path retainedStderr = Path.of(interrupted.stderrLog());

        assertTrue(interrupted.quarantined());
        assertTrue(interrupted.launchError().contains("Interrupted command process tree could not be reaped"));
        assertTrue(interrupted.launchError().contains("Ephemeral evidence quarantined at"));
        assertNotNull(launcher.lastProcess);
        assertTrue(launcher.lastProcess.forciblyDestroyed);
        assertTrue(launcher.lastProcess.isAlive());
        assertTrue(interruptPreserved, "the caller's interrupted status must be preserved");

        EvidenceRunner.cleanupEphemeral(interrupted);
        assertTrue(Files.isDirectory(retainedSandbox), "quarantined sandboxes must survive cleanup");
        assertTrue(Files.isRegularFile(retainedStdout));
        assertTrue(Files.isRegularFile(retainedStderr));

        RecordingLauncher nextLauncher = new RecordingLauncher();
        EvidenceCommand nextCommand = command(project, "next-check", Duration.ofSeconds(2));
        CommandEvidence next = runner(nextLauncher).run(project, evidenceRoot.resolve("next-run"), nextCommand);
        EvidenceRunner.cleanupEphemeral(next);

        assertTrue(Files.isDirectory(retainedSandbox), "a sibling run must preserve quarantined sandboxes");
        assertTrue(Files.isRegularFile(retainedStdout), "a sibling run must preserve quarantined stdout");
        assertTrue(Files.isRegularFile(retainedStderr), "a sibling run must preserve quarantined stderr");
    }

    @Test
    void timedOutProcessThatCannotBeReapedIsQuarantined() throws Exception {
        Path project = Files.createDirectory(tempDir.resolve("candidate"));
        Path evidenceDirectory = tempDir.resolve("evidence");
        RecordingLauncher launcher = new RecordingLauncher();
        launcher.completes = false;
        launcher.requiresForcibleTermination = true;
        launcher.ignoresForcibleTermination = true;
        EvidenceCommand evidenceCommand = command(project, "unreapable-check", Duration.ofMillis(10));

        CommandEvidence result = runner(launcher).run(project, evidenceDirectory, evidenceCommand);

        assertFalse(result.passed());
        assertTrue(result.timedOut());
        assertTrue(result.quarantined());
        assertTrue(result.launchError().contains("Timed-out command process tree could not be reaped"));
        assertTrue(result.launchError().contains("Ephemeral evidence quarantined at"));

        EvidenceRunner.cleanupEphemeral(result);
        assertTrue(Files.isDirectory(result.sandboxRoot()));
        assertTrue(Files.isRegularFile(Path.of(result.stdoutLog())));
        assertTrue(Files.isRegularFile(Path.of(result.stderrLog())));
    }

    @Test
    void failsClosedForFailedPayloadLegacyExecutableAndWorkingDirectoryEscape() throws Exception {
        Path project = Files.createDirectory(tempDir.resolve("candidate"));
        EvidenceCommand command = command(project, "build-check", Duration.ofSeconds(1));
        EvidenceRunner failingPayload = new EvidenceRunner(
                Clock.systemUTC(), new RecordingLauncher(),
                (sandboxRoot, payload) -> {
                    throw new IOException("fixture payload failure");
                });
        assertThrows(IOException.class, () -> failingPayload.run(
                project, tempDir.resolve("evidence-a"), command));
        assertFalse(Files.exists(tempDir.resolve("evidence-a")));

        Exception legacyFailure = assertThrows(IllegalArgumentException.class, () -> new EvidenceCommand(
                "legacy", List.of("/usr/bin/mvn", "--version"), null,
                Duration.ofSeconds(1), List.of(ProjectEvidenceFiles.capture(project).digest()),
                Map.of(), null));
        assertTrue(legacyFailure.getMessage().contains("direct JVM"));

        RecordingLauncher launcher = new RecordingLauncher();
        EvidenceCommand escaped = new EvidenceCommand(
                "escaped", command.arguments(), "..", Duration.ofSeconds(1),
                command.inputDigests(), Map.of(), command.jvmPayload());
        assertThrows(Exception.class, () -> runner(launcher)
                .run(project, tempDir.resolve("evidence-c"), escaped));
        assertTrue(launcher.launches.isEmpty());
    }

    private EvidenceRunner runner(RecordingLauncher launcher) {
        return new EvidenceRunner(
                Clock.systemUTC(), launcher,
                (sandboxRoot, payload) -> JvmPayloadTestFixture.create(
                        sandboxRoot.resolve("fixture-payload"), payload));
    }

    private EvidenceCommand command(Path project, String id, Duration timeout) throws Exception {
        JvmPayloadRequest payload = JvmPayloadRequest.yamlValidator("4.21.0");
        List<String> arguments = List.of(
                EvidenceRunner.JAVA_EXECUTABLE, "-cp", JvmPayloadArchive.ARCHIVE_NAME,
                ShipJvmPayloadBootstrap.class.getName(), "--launcher=" + payload.launcherClass());
        return new EvidenceCommand(
                id, arguments, null, timeout,
                List.of(ProjectEvidenceFiles.capture(project).digest()),
                Map.of("LANG", "C", "LC_ALL", "C"), payload);
    }

    private static final class RecordingLauncher implements EvidenceRunner.ProcessLauncher {

        private final List<EvidenceRunner.Launch> launches = new ArrayList<>();
        private int exitCode;
        private boolean completes = true;
        private boolean interruptsWait;
        private boolean requiresForcibleTermination;
        private boolean ignoresForcibleTermination;
        private FakeProcess lastProcess;

        @Override
        public Process launch(EvidenceRunner.Launch launch) {
            launches.add(launch);
            lastProcess = new FakeProcess(
                    "PASS\n", "", exitCode, completes, interruptsWait,
                    requiresForcibleTermination, ignoresForcibleTermination);
            return lastProcess;
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
        private boolean forciblyDestroyed;

        private FakeProcess(
                            String stdout,
                            String stderr,
                            int exitCode,
                            boolean completes,
                            boolean interruptWait,
                            boolean requiresForcibleTermination,
                            boolean ignoresForcibleTermination) {
            this.stdout = new ByteArrayInputStream(stdout.getBytes(StandardCharsets.UTF_8));
            this.stderr = new ByteArrayInputStream(stderr.getBytes(StandardCharsets.UTF_8));
            this.exitCode = exitCode;
            this.alive = !completes;
            this.interruptWait = interruptWait;
            this.requiresForcibleTermination = requiresForcibleTermination;
            this.ignoresForcibleTermination = ignoresForcibleTermination;
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
