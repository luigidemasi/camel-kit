package io.github.luigidemasi.camelkit.command.ship;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.channels.ClosedByInterruptException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import io.github.luigidemasi.camelkit.ship.context.ShipContext.Kind;
import io.github.luigidemasi.camelkit.ship.controller.ShipController;
import io.github.luigidemasi.camelkit.ship.controller.ShipRun;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipCommandTest {

    @TempDir
    Path tempDir;

    @Test
    void bareStartUsesSmartOversightAndPrintsStableSummary() throws Exception {
        Path project = Files.createDirectory(tempDir.resolve("project"));
        ShipController controller = controller("state");
        RecordingLauncher launcher = RecordingLauncher.passThrough(controller);

        RunResult result = run(controller, launcher, "--project-dir", project.toString());
        String id = runId(result.output());

        assertEquals(0, result.exitCode(), result.error());
        assertEquals("", result.error());
        assertEquals(summary(id, "RUNNING", "DISCOVERY", "SMART"), result.output());
        assertEquals(List.of("run:" + id), launcher.invocations);

        ShipRun run = controller.status(id);
        assertEquals(project.toAbsolutePath().normalize().toString(), run.projectDirectory());
        assertTrue(run.context().sources().isEmpty());
    }

    @Test
    void acceptsEveryOversightValue() throws Exception {
        Path project = Files.createDirectory(tempDir.resolve("project"));
        ShipController controller = controller("state");

        for (String policy : List.of("always", "smart", "never")) {
            RunResult result = run(
                    controller,
                    RecordingLauncher.passThrough(controller),
                    "--project-dir", project.toString(),
                    "--ask", policy);

            assertEquals(0, result.exitCode(), result.error());
            assertTrue(result.output().endsWith(
                    "Oversight: " + policy.toUpperCase(Locale.ROOT) + System.lineSeparator()));
        }
    }

    @Test
    void preservesMixedRepeatedContextOrder() throws Exception {
        Path project = Files.createDirectory(tempDir.resolve("project"));
        Path document = Files.writeString(project.resolve("requirements.md"), "from document");
        ShipController controller = controller("state");

        RunResult result = run(
                controller,
                RecordingLauncher.passThrough(controller),
                "--project-dir", project.toString(),
                "--text", "first",
                "--document", "requirements.md",
                "--text", "last");

        assertEquals(0, result.exitCode(), result.error());
        ShipRun run = controller.status(runId(result.output()));
        assertEquals(List.of(Kind.TEXT, Kind.DOCUMENT, Kind.TEXT),
                run.context().sources().stream().map(source -> source.kind()).toList());
        assertEquals("first", run.context().sources().get(0).value());
        assertEquals(document.toAbsolutePath().normalize().toString(),
                run.context().sources().get(1).value());
        assertEquals("last", run.context().sources().get(2).value());
    }

    @Test
    void supportsStartFromResumeStatusAndAbort() throws Exception {
        Path project = Files.createDirectory(tempDir.resolve("project"));
        Path metadata = Files.createDirectories(
                project.resolve(".camel-kit"));
        Files.writeString(
                metadata.resolve("pipeline.json"),
                "{\"mode\":\"manual\",\"activePipeline\":\"149-command\"}\n");
        ShipController controller = controller("state");
        RecordingLauncher launcher = RecordingLauncher.passThrough(controller);

        RunResult started = run(
                controller,
                launcher,
                "--project-dir", project.toString(),
                "--start-from", "design",
                "--text", "Build an integration");
        String id = runId(started.output());
        assertEquals(0, started.exitCode(), started.error());
        assertEquals(summary(id, "RUNNING", "DESIGN", "SMART"), started.output());

        RunResult resumed = run(controller, launcher, "--resume", id);
        assertEquals(0, resumed.exitCode(), resumed.error());
        assertEquals(started.output(), resumed.output());
        assertEquals(2, controller.status(id).stage(ShipRun.Stage.DESIGN).attempts());
        assertEquals(List.of("run:" + id, "resume:" + id), launcher.invocations);

        RunResult status = run(controller, launcher, "--status", id);
        assertEquals(0, status.exitCode(), status.error());
        assertEquals(started.output(), status.output());

        RunResult aborted = run(controller, launcher, "--abort", id);
        assertEquals(0, aborted.exitCode(), aborted.error());
        assertEquals(summary(id, "ABORTED", "DESIGN", "SMART"), aborted.output());
        assertEquals(List.of("run:" + id, "resume:" + id), launcher.invocations);

        RunResult rejected = run(controller, launcher, "--resume", id);
        assertEquals(1, rejected.exitCode());
        assertEquals("", rejected.output());
        assertTrue(rejected.error().matches("(?is)Error \\[[^]]+]: .*aborted.*\\R"),
                rejected.error());
    }

    @Test
    void statusAndAbortNeverLaunchTheRuntime() throws Exception {
        Path project = Files.createDirectory(tempDir.resolve("project"));
        ShipController controller = controller("state");
        RecordingLauncher starter = RecordingLauncher.passThrough(controller);
        String id = runId(run(
                controller, starter, "--project-dir", project.toString()).output());

        ShipCommand.WorkflowLauncher rejecting = settings -> {
            throw new AssertionError("status and abort must not launch the runtime");
        };
        RunResult status = run(controller, rejecting, "--status", id);
        assertEquals(0, status.exitCode(), status.error());

        RunResult aborted = run(controller, rejecting, "--abort", id);
        assertEquals(0, aborted.exitCode(), aborted.error());
    }

    @Test
    void launchFailureBeforeStartCreatesNoRunState() throws Exception {
        Path project = Files.createDirectory(tempDir.resolve("project"));
        Path state = tempDir.resolve("state");
        ShipController controller = new ShipController(state);
        ShipCommand.WorkflowLauncher unavailable = settings -> {
            throw new IllegalArgumentException(
                    "Pi is missing or not executable; install Pi and configure its executable path");
        };

        RunResult result = run(controller, unavailable, "--project-dir", project.toString());

        assertEquals(1, result.exitCode());
        assertEquals("", result.output());
        assertEquals("Error [runtime-unavailable]: Pi is missing or not executable;"
                     + " install Pi and configure its executable path" + System.lineSeparator(),
                result.error());
        assertFalse(Files.exists(state), "no run state may exist after a launch failure");
    }

    @Test
    void unsupportedRuntimeReportsItsOwnCode() throws Exception {
        Path project = Files.createDirectory(tempDir.resolve("project"));
        ShipController controller = controller("state");
        ShipCommand.WorkflowLauncher unsupported = settings -> {
            throw new IllegalStateException("The first Pi Ship worker supports Linux only");
        };

        RunResult result = run(controller, unsupported, "--project-dir", project.toString());

        assertEquals(1, result.exitCode());
        assertEquals("Error [runtime-unsupported]: The first Pi Ship worker supports Linux only"
                     + System.lineSeparator(),
                result.error());
    }

    @Test
    void controllerFailureSkipsTheWorkflow() throws Exception {
        Path project = Files.createDirectory(tempDir.resolve("project"));
        ShipController controller = controller("state");
        RecordingLauncher launcher = RecordingLauncher.passThrough(controller);

        RunResult result = run(
                controller,
                launcher,
                "--project-dir", project.toString(),
                "--document", project.resolve("missing.txt").toString());

        assertEquals(1, result.exitCode());
        assertEquals("", result.output());
        assertEquals(1, launcher.launches, "launch precedes the controller call");
        assertTrue(launcher.invocations.isEmpty(), "workflow must not run after a start failure");
    }

    @Test
    void workflowIoFailureReportsTheRunIdentifierForRecovery() throws Exception {
        Path project = Files.createDirectory(tempDir.resolve("project"));
        ShipController controller = controller("state");
        RecordingLauncher launcher = new RecordingLauncher(new ShipCommand.Workflow() {

            @Override
            public ShipRun run(String runId) throws IOException {
                throw new IOException(
                        "Node is installed but `node --version` failed;"
                                      + " reinstall Node and verify the configured executable");
            }

            @Override
            public ShipRun resume(String runId) {
                throw new AssertionError("not resumed");
            }
        });

        RunResult result = run(controller, launcher, "--project-dir", project.toString());

        assertEquals(1, result.exitCode());
        assertEquals("", result.output());
        assertTrue(result.error().matches(
                "(?s)Error \\[workflow-failed]: Node is installed.*\\(run ship-[0-9a-f]{32}\\)\\R"),
                result.error());
        String id = result.error().replaceAll("(?s).*\\(run (ship-[0-9a-f]{32})\\).*", "$1");
        assertEquals(id, controller.status(id).id(), "the reported run must be recoverable");
    }

    @Test
    void messageLessIoFailureReportsTheExceptionTypeInsteadOfNull() throws Exception {
        Path project = Files.createDirectory(tempDir.resolve("project"));
        ShipController controller = controller("state");
        RecordingLauncher launcher = new RecordingLauncher(new ShipCommand.Workflow() {

            @Override
            public ShipRun run(String runId) throws IOException {
                throw new IOException();
            }

            @Override
            public ShipRun resume(String runId) {
                throw new AssertionError("not resumed");
            }
        });

        RunResult result = run(controller, launcher, "--project-dir", project.toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.error().startsWith("Error [workflow-failed]: IOException (run ship-"),
                result.error());
    }

    @Test
    void closedByInterruptReportsTheDurableStateLikeAnAbort() throws Exception {
        Path project = Files.createDirectory(tempDir.resolve("project"));
        ShipController controller = controller("state");
        RecordingLauncher launcher = new RecordingLauncher(new ShipCommand.Workflow() {

            @Override
            public ShipRun run(String runId) throws IOException {
                throw new ClosedByInterruptException();
            }

            @Override
            public ShipRun resume(String runId) {
                throw new AssertionError("not resumed");
            }
        });

        try {
            RunResult result = run(controller, launcher, "--project-dir", project.toString());

            assertTrue(Thread.currentThread().isInterrupted(), "interrupt status must survive");
            assertEquals(0, result.exitCode(), result.error());
            String id = runId(result.output());
            assertEquals(summary(id, "RUNNING", "DISCOVERY", "SMART"), result.output());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void workflowFailureAfterResumeReportsTheRunIdentifier() throws Exception {
        Path project = Files.createDirectory(tempDir.resolve("project"));
        ShipController controller = controller("state");
        String id = runId(run(
                controller,
                RecordingLauncher.passThrough(controller),
                "--project-dir", project.toString()).output());
        RecordingLauncher launcher = new RecordingLauncher(new ShipCommand.Workflow() {

            @Override
            public ShipRun run(String runId) {
                throw new AssertionError("not started");
            }

            @Override
            public ShipRun resume(String runId) throws IOException {
                throw new IOException("Pi stage session is already running");
            }
        });

        RunResult result = run(controller, launcher, "--resume", id);

        assertEquals(1, result.exitCode());
        assertTrue(result.error().startsWith(
                "Error [workflow-failed]: Pi stage session is already running (run " + id + ")"),
                result.error());
    }

    @Test
    void interruptedWorkflowRestoresTheFlagAndPrintsTheLatestState() throws Exception {
        Path project = Files.createDirectory(tempDir.resolve("project"));
        ShipController controller = controller("state");
        RecordingLauncher launcher = new RecordingLauncher(new ShipCommand.Workflow() {

            @Override
            public ShipRun run(String runId) throws InterruptedException {
                throw new InterruptedException("aborted by another controller");
            }

            @Override
            public ShipRun resume(String runId) {
                throw new AssertionError("not resumed");
            }
        });

        try {
            RunResult result = run(controller, launcher, "--project-dir", project.toString());

            assertTrue(Thread.currentThread().isInterrupted(), "interrupt status must survive");
            assertEquals(0, result.exitCode(), result.error());
            String id = runId(result.output());
            assertEquals(summary(id, "RUNNING", "DISCOVERY", "SMART"), result.output());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void forwardsRuntimeOptionsToTheLauncher() throws Exception {
        Path project = Files.createDirectory(tempDir.resolve("project"));
        ShipController controller = controller("state");
        RecordingLauncher launcher = RecordingLauncher.passThrough(controller);

        RunResult result = run(
                controller,
                launcher,
                "--project-dir", project.toString(),
                "--pi", "/opt/pi/bin/pi",
                "--node", "/opt/node/bin/node",
                "--maven-repository", "/tmp/ship-repo",
                "--stage-timeout", "90s",
                "--accept-experimental");

        assertEquals(0, result.exitCode(), result.error());
        ShipCommand.RuntimeSettings settings = launcher.settings;
        assertEquals(Path.of("/opt/pi/bin/pi"), settings.piExecutable());
        assertEquals(Path.of("/opt/node/bin/node"), settings.nodeExecutable());
        assertEquals(Path.of("/tmp/ship-repo"), settings.mavenRepository());
        assertEquals(Duration.ofSeconds(90), settings.stageTimeout());
        assertTrue(settings.acceptExperimental());
    }

    @Test
    void defaultsRuntimeSettingsToUnsetValues() throws Exception {
        Path project = Files.createDirectory(tempDir.resolve("project"));
        ShipController controller = controller("state");
        RecordingLauncher launcher = RecordingLauncher.passThrough(controller);

        RunResult result = run(controller, launcher, "--project-dir", project.toString());

        assertEquals(0, result.exitCode(), result.error());
        ShipCommand.RuntimeSettings settings = launcher.settings;
        assertNull(settings.piExecutable());
        assertNull(settings.nodeExecutable());
        assertNull(settings.mavenRepository());
        assertNull(settings.stageTimeout());
        assertFalse(settings.acceptExperimental());
    }

    @Test
    void rejectsRuntimeOptionsOnStatusAndAbort() throws Exception {
        ShipController controller = controller("state");
        String id = "ship-0123456789abcdef0123456789abcdef";

        for (String[] arguments : List.of(
                new String[]{"--status", id, "--pi", "/opt/pi"},
                new String[]{"--status", id, "--stage-timeout", "10m"},
                new String[]{"--abort", id, "--accept-experimental"},
                new String[]{"--abort", id, "--maven-repository", "/tmp/repo"},
                new String[]{"--status", id, "--node", "/opt/node"})) {
            RunResult result = run(controller, RecordingLauncher.passThrough(controller), arguments);
            assertEquals(CommandLine.ExitCode.USAGE, result.exitCode());
            assertTrue(result.error().contains(
                    "only valid when starting or resuming a run"), result.error());
        }
    }

    @Test
    void rejectsMalformedStageTimeouts() throws Exception {
        ShipController controller = controller("state");

        for (String timeout : List.of("bananas", "0s", "-5m", "10", "3d", "99999999999999999999s",
                "9223372036854775807m")) {
            RunResult result = run(
                    controller,
                    RecordingLauncher.passThrough(controller),
                    "--stage-timeout", timeout);
            assertEquals(CommandLine.ExitCode.USAGE, result.exitCode(), timeout);
            assertTrue(result.error().contains("expected a duration like 90s, 10m, or 1h"),
                    result.error());
        }
    }

    @Test
    void advertisesTheStageTimeoutDefaultInHelp() throws Exception {
        ShipController controller = controller("state");

        RunResult help = run(controller, RecordingLauncher.passThrough(controller), "--help");

        assertEquals(0, help.exitCode(), help.error());
        assertTrue(help.output().contains("(default: " + ShipRuntime.DEFAULT_STAGE_TIMEOUT.toMinutes() + "m)"),
                help.output());
    }

    @Test
    void startFromAdvertisesSupportedStagesAndReportsTheDomainBoundary() throws Exception {
        Path project = Files.createDirectory(tempDir.resolve("project"));
        ShipController controller = controller("state");
        RecordingLauncher launcher = RecordingLauncher.passThrough(controller);

        RunResult help = run(controller, launcher, "--help");
        assertEquals(0, help.exitCode(), help.error());
        assertTrue(help.output().contains("Start at discovery, design, or plan"), help.output());

        RunResult unknown = run(
                controller,
                launcher,
                "--project-dir", project.toString(),
                "--start-from", "unknown");
        assertEquals(CommandLine.ExitCode.USAGE, unknown.exitCode());
        assertTrue(unknown.error().contains(
                "expected discovery, design, or plan"), unknown.error());

        for (String stage : List.of("execute", "validate")) {
            RunResult result = run(
                    controller,
                    launcher,
                    "--project-dir", project.toString(),
                    "--start-from", stage);

            assertEquals(1, result.exitCode(), result.error());
            assertEquals("", result.output());
            assertTrue(result.error().startsWith(
                    "Error [start-from-stage-unsupported]: Starting from EXECUTE or VALIDATE"),
                    result.error());
            assertTrue(result.error().contains("start from PLAN instead"), result.error());
        }
    }

    @Test
    void rejectsExclusiveOperationsAndContextOnNonStartingOperations() throws Exception {
        ShipController controller = controller("state");
        String id = "ship-0123456789abcdef0123456789abcdef";

        RunResult exclusive = run(controller, rejectingLauncher(), "--resume", id, "--status", id);
        assertEquals(CommandLine.ExitCode.USAGE, exclusive.exitCode());
        assertTrue(exclusive.error().contains("mutually exclusive"), exclusive.error());

        RunResult context = run(controller, rejectingLauncher(), "--status", id, "--text", "not allowed");
        assertEquals(CommandLine.ExitCode.USAGE, context.exitCode());
        assertTrue(context.error().contains(
                "--ask, --text, and --document are only valid when starting a run"),
                context.error());

        RunResult oversight = run(controller, rejectingLauncher(), "--abort", id, "--ask", "never");
        assertEquals(CommandLine.ExitCode.USAGE, oversight.exitCode());
        assertTrue(oversight.error().contains(
                "--ask, --text, and --document are only valid when starting a run"),
                oversight.error());
    }

    @Test
    void keepsAtFileExpansionDisabled() throws Exception {
        Path arguments = Files.writeString(tempDir.resolve("arguments"), "--ask never");
        ShipController controller = controller("state");

        RunResult result = run(controller, rejectingLauncher(), "@" + arguments);

        assertEquals(CommandLine.ExitCode.USAGE, result.exitCode());
        assertTrue(result.error().contains("Unmatched argument"), result.error());
    }

    @Test
    void reportsDistinctDocumentFailuresAsOperationalErrors() throws Exception {
        Path project = Files.createDirectory(tempDir.resolve("project"));
        Path empty = Files.createFile(project.resolve("empty.txt"));
        Path malformed = Files.write(project.resolve("malformed.txt"), new byte[]{(byte) 0xc3, 0x28});
        ShipController controller = controller("state");

        assertDocumentFailure(controller, project, project.resolve("missing.txt"), "missing");
        assertDocumentFailure(controller, project, empty, "empty");
        assertDocumentFailure(controller, project, malformed, "malformed");
    }

    @Test
    void reportsUnreadableDocumentAsOperationalError() throws Exception {
        Path project = Files.createDirectory(tempDir.resolve("project"));
        Assumptions.assumeTrue(Files.getFileStore(project)
                .supportsFileAttributeView("posix"));
        Path unreadable = Files.writeString(project.resolve("unreadable.txt"), "secret");
        Files.setPosixFilePermissions(unreadable, PosixFilePermissions.fromString("---------"));
        try {
            assertDocumentFailure(controller("state"), project, unreadable, "unreadable");
        } finally {
            Files.setPosixFilePermissions(
                    unreadable, PosixFilePermissions.fromString("rw-------"));
        }
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void realRuntimeDrivesTheCoordinatorThroughTheCommand() throws Exception {
        Path fixture = Files.createDirectory(tempDir.resolve("fake-pi"));
        try (var script = Objects.requireNonNull(
                ShipCommandTest.class.getResourceAsStream(
                        "/io/github/luigidemasi/camelkit/ship/worker/fake-pi-rpc.sh"))) {
            writeExecutable(
                    fixture.resolve("pi-rpc"),
                    new String(script.readAllBytes(), StandardCharsets.UTF_8));
        }
        Path node = writeExecutable(
                fixture.resolve("node"),
                """
                        #!/bin/sh
                        if [ "${1:-}" = "--version" ]; then
                          cat "$(dirname "$0")/node-version"
                        else
                          exec "$@"
                        fi
                        """);
        Files.writeString(fixture.resolve("node-version"), "v22.22.2\n");
        Files.writeString(fixture.resolve("version"), "0.81.1\n");
        Path project = Files.createDirectory(tempDir.resolve("project"));
        Path state = tempDir.resolve("state");
        ShipController controller = new ShipController(state);

        RunResult result = run(
                controller,
                new ShipRuntime(state),
                "--project-dir", project.toString(),
                "--pi", fixture.resolve("pi-rpc").toString(),
                "--node", node.toString(),
                "--stage-timeout", "30s",
                "--text", "unverified Pi must fail the stage");

        assertEquals(1, result.exitCode(), result.error());
        String id = runId(result.output());
        assertEquals(summary(id, "FAILED", "DISCOVERY", "SMART"), result.output());
        RunResult failedStatus = run(controller, new ShipRuntime(state), "--status", id);
        assertEquals(0, failedStatus.exitCode(),
                "status queries report failed runs without a failing exit code");
        assertTrue(controller.status(id).message().contains(
                "Pi 0.81.1 is unverified; install maintained Pi"),
                controller.status(id).message());
        assertFalse(Files.exists(fixture.resolve("args")),
                "the gate must fire before any Pi process starts");
    }

    private void assertDocumentFailure(
            ShipController controller, Path project, Path document, String expectedCode) {
        RunResult result = run(
                controller,
                RecordingLauncher.passThrough(controller),
                "--project-dir", project.toString(),
                "--document", document.toString());

        assertEquals(1, result.exitCode(), result.error());
        assertEquals("", result.output());
        assertTrue(result.error().matches(
                "(?is)Error \\[[^]]*" + expectedCode + "[^]]*]: .*\\R"),
                result.error());
    }

    private ShipController controller(String stateDirectory) {
        return new ShipController(tempDir.resolve(stateDirectory));
    }

    private static ShipCommand.WorkflowLauncher rejectingLauncher() {
        return settings -> {
            throw new AssertionError("the runtime must not be launched");
        };
    }

    private static RunResult run(
            ShipController controller, ShipCommand.WorkflowLauncher launcher, String... args) {
        StringWriter output = new StringWriter();
        StringWriter error = new StringWriter();
        CommandLine commandLine = new CommandLine(new ShipCommand(controller, launcher));
        commandLine.setExpandAtFiles(false);
        commandLine.setOut(new PrintWriter(output, true));
        commandLine.setErr(new PrintWriter(error, true));
        int exitCode = commandLine.execute(args);
        return new RunResult(exitCode, output.toString(), error.toString());
    }

    private static Path writeExecutable(Path path, String script) throws IOException {
        Files.writeString(path, script);
        Files.setPosixFilePermissions(
                path, PosixFilePermissions.fromString("rwx------"));
        return path;
    }

    private static String runId(String output) {
        assertTrue(output.startsWith("Run: "), output);
        return output.lines().findFirst().orElseThrow().substring("Run: ".length());
    }

    private static String summary(String id, String status, String stage, String oversight) {
        return String.join(System.lineSeparator(),
                "Run: " + id,
                "Status: " + status,
                "Stage: " + stage,
                "Oversight: " + oversight,
                "");
    }

    /** Records launches and workflow invocations; the pass-through variant mirrors today's state. */
    private static final class RecordingLauncher implements ShipCommand.WorkflowLauncher {

        final List<String> invocations = new ArrayList<>();
        final ShipCommand.Workflow delegate;
        ShipCommand.RuntimeSettings settings;
        int launches;

        RecordingLauncher(ShipCommand.Workflow delegate) {
            this.delegate = delegate;
        }

        static RecordingLauncher passThrough(ShipController controller) {
            return new RecordingLauncher(new ShipCommand.Workflow() {

                @Override
                public ShipRun run(String runId) {
                    return controller.status(runId);
                }

                @Override
                public ShipRun resume(String runId) {
                    return controller.resume(runId);
                }
            });
        }

        @Override
        public ShipCommand.Workflow launch(ShipCommand.RuntimeSettings launchSettings) {
            this.settings = launchSettings;
            launches++;
            return new ShipCommand.Workflow() {

                @Override
                public ShipRun run(String runId) throws IOException, InterruptedException {
                    invocations.add("run:" + runId);
                    return delegate.run(runId);
                }

                @Override
                public ShipRun resume(String runId) throws IOException, InterruptedException {
                    invocations.add("resume:" + runId);
                    return delegate.resume(runId);
                }
            };
        }
    }

    private record RunResult(int exitCode, String output, String error) {
    }
}
