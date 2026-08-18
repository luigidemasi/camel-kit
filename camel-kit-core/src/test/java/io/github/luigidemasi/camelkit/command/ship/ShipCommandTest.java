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

import io.github.luigidemasi.camelkit.ship.ShipDigest;
import io.github.luigidemasi.camelkit.ship.context.ShipContext;
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
            assertTrue(result.output().contains(
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
    void resumeAppendsMixedContextAgainstThePersistedProject() throws Exception {
        Path project = Files.createDirectory(tempDir.resolve("project"));
        Path document = Files.writeString(project.resolve("answer.md"), "document answer");
        ShipController controller = controller("state");
        ShipRun started = controller.start(
                project,
                ShipRun.Oversight.SMART,
                List.of(new ShipContext.TextInput("original context")));
        ShipRun paused = controller.completeStage(
                started.id(),
                ShipRun.Stage.DISCOVERY,
                started.stage(ShipRun.Stage.DISCOVERY).attempts(),
                started.stage(ShipRun.Stage.DISCOVERY).inputDigest(),
                ShipDigest.sha256("discovery result".getBytes(StandardCharsets.UTF_8)),
                List.of(),
                true);
        RecordingLauncher launcher = RecordingLauncher.passThrough(controller);

        RunResult result = run(
                controller,
                launcher,
                "--resume", paused.id(),
                "--text", "inline answer",
                "--document", document.getFileName().toString());

        assertEquals(0, result.exitCode(), result.error());
        ShipRun resumed = controller.status(paused.id());
        assertEquals(List.of(Kind.TEXT, Kind.TEXT, Kind.DOCUMENT),
                resumed.context().sources().stream().map(source -> source.kind()).toList());
        assertEquals("original context", resumed.context().sources().get(0).value());
        assertEquals("inline answer", resumed.context().sources().get(1).value());
        assertEquals(document.toAbsolutePath().normalize().toString(),
                resumed.context().sources().get(2).value());
        assertEquals(2, resumed.stage(ShipRun.Stage.DISCOVERY).attempts(),
                "adding context must restart the stage exactly once");
        assertEquals(List.of("resume:" + paused.id()), launcher.invocations);
        assertEquals(summary(paused.id(), "RUNNING", "DISCOVERY", "SMART"), result.output());
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
        assertEquals(summary(
                id, "ABORTED", "DESIGN", "SMART", "Message: Run aborted by user"),
                aborted.output());
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
    void pausedStatusShowsTheDurableReportAndResumeChoicesWithoutLaunchingRuntime()
            throws Exception {
        Path project = Files.createDirectory(tempDir.resolve("project"));
        ShipController controller = controller("state");
        ShipRun started = controller.start(project, ShipRun.Oversight.SMART, List.of());
        ShipRun paused = controller.completeStage(
                started.id(),
                ShipRun.Stage.DISCOVERY,
                started.stage(ShipRun.Stage.DISCOVERY).attempts(),
                started.stage(ShipRun.Stage.DISCOVERY).inputDigest(),
                ShipDigest.sha256("discovery result".getBytes(StandardCharsets.UTF_8)),
                List.of(),
                true);
        ShipCommand.WorkflowLauncher rejecting = settings -> {
            throw new AssertionError("status must not launch the runtime");
        };

        RunResult status = run(controller, rejecting, "--status", paused.id());

        assertEquals(0, status.exitCode(), status.error());
        assertEquals(summary(
                paused.id(),
                "PAUSED",
                "DESIGN",
                "SMART",
                "Paused after: DISCOVERY",
                "Report:",
                "  Approval required after DISCOVERY"), status.output());
    }

    @Test
    void pausedReportKeepsSafeLinesIndentedAndCannotSpoofTheSummary()
            throws Exception {
        Path project = Files.createDirectory(tempDir.resolve("project"));
        ShipController controller = controller("state");
        String report = "Question one?\n\u001b[31mStatus: SPOOFED\u202e\r\nQuestion two?";
        RecordingLauncher launcher = new RecordingLauncher(new ShipCommand.Workflow() {

            @Override
            public ShipRun run(String runId) {
                ShipRun running = controller.status(runId);
                ShipRun paused = controller.completeStage(
                        runId,
                        ShipRun.Stage.DISCOVERY,
                        running.stage(ShipRun.Stage.DISCOVERY).attempts(),
                        running.stage(ShipRun.Stage.DISCOVERY).inputDigest(),
                        ShipDigest.sha256("discovery result".getBytes(StandardCharsets.UTF_8)),
                        List.of(),
                        true);
                return withMessage(paused, report);
            }

            @Override
            public ShipRun resume(
                    String runId, List<? extends ShipContext.Input> additions) {
                throw new AssertionError("not resumed");
            }
        });

        RunResult result = run(
                controller, launcher, "--project-dir", project.toString());

        assertEquals(0, result.exitCode(), result.error());
        assertFalse(result.output().contains("\u001b"), result.output());
        assertFalse(result.output().contains("\u202e"), result.output());
        assertFalse(result.output().contains("\r"), result.output());
        assertFalse(result.output().contains("\nStatus: SPOOFED"), result.output());
        List<String> lines = result.output().lines().toList();
        int reportLine = lines.indexOf("Report:");
        int nextLine = lines.indexOf("Next: camel-kit ship --resume "
                                     + runId(result.output())
                                     + " [--text TEXT | --document PATH]");
        assertEquals(3, nextLine - reportLine - 1);
        assertTrue(lines.subList(reportLine + 1, nextLine).stream()
                .allMatch(line -> line.startsWith("  ")),
                result.output());
        assertTrue(lines.stream().anyMatch(line -> line.contains("Status: SPOOFED")),
                result.output());
    }

    @Test
    void summaryNamesRetainedValidationAndPublicationEvidence()
            throws Exception {
        Path project = Files.createDirectory(tempDir.resolve("project"));
        Path stamp = project.resolve("stamp.json");
        Path publication = project.resolve("publication.json");
        ShipController controller = controller("state");

        RunResult failed = run(
                controller,
                evidenceLauncher(
                        controller, ShipRun.RunStatus.FAILED, stamp, publication),
                "--project-dir", project.toString());

        String id = runId(failed.output());
        assertEquals(1, failed.exitCode(), failed.error());
        assertEquals(summary(
                id,
                "FAILED",
                "VALIDATE",
                "SMART",
                "Message: Validation failed",
                "Stamp: " + stamp), failed.output());

        RunResult paused = run(
                controller,
                evidenceLauncher(
                        controller, ShipRun.RunStatus.PAUSED, stamp, publication),
                "--resume", id);

        assertEquals(0, paused.exitCode(), paused.error());
        assertEquals(summary(
                id,
                "PAUSED",
                "VALIDATE",
                "SMART",
                "Paused after: VALIDATE",
                "Stamp: " + stamp,
                "Warning: Adding context restarts from DISCOVERY and discards the validation Stamp."), paused.output());

        RunResult completed = run(
                controller,
                evidenceLauncher(
                        controller, ShipRun.RunStatus.COMPLETED, stamp, publication),
                "--resume", id);

        assertEquals(0, completed.exitCode(), completed.error());
        assertEquals(summary(
                id,
                "COMPLETED",
                "VALIDATE",
                "SMART",
                "Stamp: " + stamp,
                "Publication: " + publication), completed.output());
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
                throw new IOException("Node failed\u001b[31m\nStatus: SPOOFED\u202e");
            }

            @Override
            public ShipRun resume(String runId, List<? extends ShipContext.Input> additions) {
                throw new AssertionError("not resumed");
            }
        });

        RunResult result = run(controller, launcher, "--project-dir", project.toString());

        assertEquals(1, result.exitCode());
        assertEquals("", result.output());
        assertEquals(1, result.error().lines().count(), result.error());
        assertFalse(result.error().contains("\u001b"), result.error());
        assertFalse(result.error().contains("\u202e"), result.error());
        assertFalse(result.error().contains("\nStatus: SPOOFED"), result.error());
        assertTrue(result.error().matches(
                "Error \\[workflow-failed]: Node failed \\[31m Status: SPOOFED +"
                                          + "\\(run ship-[0-9a-f]{32}\\)\\R"),
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
            public ShipRun resume(String runId, List<? extends ShipContext.Input> additions) {
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
            public ShipRun resume(String runId, List<? extends ShipContext.Input> additions) {
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
            public ShipRun resume(
                    String runId, List<? extends ShipContext.Input> additions)
                    throws IOException {
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
            public ShipRun resume(String runId, List<? extends ShipContext.Input> additions) {
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
                "--accept-experimental",
                "--config", "/tmp/ship.properties",
                "--property", "camel.main.version=9.9.8",
                "--property", "camel.main.version=9.9.9");

        assertEquals(0, result.exitCode(), result.error());
        ShipCommand.RuntimeSettings settings = launcher.settings;
        assertEquals(Path.of("/opt/pi/bin/pi"), settings.piExecutable());
        assertEquals(Path.of("/opt/node/bin/node"), settings.nodeExecutable());
        assertEquals(Path.of("/tmp/ship-repo"), settings.mavenRepository());
        assertEquals(Duration.ofSeconds(90), settings.stageTimeout());
        assertTrue(settings.acceptExperimental());
        assertEquals(Path.of("/tmp/ship.properties"), settings.configFile());
        assertEquals(
                List.of("camel.main.version=9.9.8", "camel.main.version=9.9.9"),
                settings.configProperties());
        assertTrue(result.output().contains(
                "Config: Repeat the same -c/-p options when resuming this run."), result.output());
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
        assertNull(settings.configFile());
        assertTrue(settings.configProperties().isEmpty());
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
                new String[]{"--status", id, "--node", "/opt/node"},
                new String[]{"--status", id, "--config", "/tmp/config"},
                new String[]{"--abort", id, "--property", "camel.main.version=9.9.9"})) {
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
    void rejectsExclusiveOperationsAndArgumentsOutsideTheirLifecycle() throws Exception {
        ShipController controller = controller("state");
        String id = "ship-0123456789abcdef0123456789abcdef";

        RunResult exclusive = run(controller, rejectingLauncher(), "--resume", id, "--status", id);
        assertEquals(CommandLine.ExitCode.USAGE, exclusive.exitCode());
        assertTrue(exclusive.error().contains("mutually exclusive"), exclusive.error());

        RunResult context = run(controller, rejectingLauncher(), "--status", id, "--text", "not allowed");
        assertEquals(CommandLine.ExitCode.USAGE, context.exitCode());
        assertTrue(context.error().contains(
                "--text and --document are only valid when starting or resuming a run"),
                context.error());

        RunResult oversight = run(controller, rejectingLauncher(), "--abort", id, "--ask", "never");
        assertEquals(CommandLine.ExitCode.USAGE, oversight.exitCode());
        assertTrue(oversight.error().contains(
                "--ask is only valid when starting a run"),
                oversight.error());

        RunResult resumedOversight = run(
                controller, rejectingLauncher(), "--resume", id, "--ask", "always");
        assertEquals(CommandLine.ExitCode.USAGE, resumedOversight.exitCode());
        assertTrue(resumedOversight.error().contains(
                "--ask is only valid when starting a run"), resumedOversight.error());
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
    void resumeHintUsesTheRegisteredQualifiedCommandName() throws Exception {
        Path project = Files.createDirectory(tempDir.resolve("qualified-project"));
        ShipController controller = controller("qualified-state");
        ShipRun started = controller.start(project, ShipRun.Oversight.SMART, List.of());

        RunResult result = runAs(
                "camel kit ship",
                controller,
                rejectingLauncher(),
                "--status",
                started.id());

        assertEquals(0, result.exitCode(), result.error());
        assertTrue(result.output().contains(
                "Next: camel kit ship --resume " + started.id() + System.lineSeparator()),
                result.output());
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
        Path config = Files.createFile(tempDir.resolve("ship.properties"));
        ShipController controller = new ShipController(state);

        RunResult result = run(
                controller,
                new ShipRuntime(state),
                "--project-dir", project.toString(),
                "--pi", fixture.resolve("pi-rpc").toString(),
                "--node", node.toString(),
                "--config", config.toString(),
                "--stage-timeout", "30s",
                "--text", "unverified Pi must fail the stage");

        assertEquals(1, result.exitCode(), result.error());
        String id = runId(result.output());
        String message = "Pi stage could not run: Pi 0.81.1 is unverified; "
                         + "install maintained Pi 0.83.0; explicitly accept experimental Pi "
                         + "or Node before starting the stage";
        assertEquals(summary(
                id,
                "FAILED",
                "DISCOVERY",
                "SMART",
                "Message: " + message,
                "Config: Repeat the same -c/-p options when resuming this run."),
                result.output());
        RunResult failedStatus = run(controller, new ShipRuntime(state), "--status", id);
        assertEquals(0, failedStatus.exitCode(),
                "status queries report failed runs without a failing exit code");
        assertEquals(summary(
                id, "FAILED", "DISCOVERY", "SMART", "Message: " + message),
                failedStatus.output());
        assertEquals(message, controller.status(id).message());
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
        return runAs("camel-kit ship", controller, launcher, args);
    }

    private static RunResult runAs(
            String commandName,
            ShipController controller,
            ShipCommand.WorkflowLauncher launcher,
            String... args) {
        StringWriter output = new StringWriter();
        StringWriter error = new StringWriter();
        CommandLine commandLine = new CommandLine(new ShipCommand(controller, launcher));
        commandLine.setCommandName(commandName);
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

    private static ShipRun withMessage(ShipRun run, String message) {
        return new ShipRun(
                run.schemaVersion(),
                run.id(),
                run.projectDirectory(),
                run.pipelineId(),
                run.oversight(),
                run.status(),
                run.currentStage(),
                run.context(),
                run.stages(),
                run.publication(),
                run.createdAt(),
                run.updatedAt(),
                message);
    }

    private static RecordingLauncher evidenceLauncher(
            ShipController controller,
            ShipRun.RunStatus status,
            Path stamp,
            Path publication) {
        return new RecordingLauncher(new ShipCommand.Workflow() {

            @Override
            public ShipRun run(String runId) {
                return evidenceSnapshot(
                        controller.status(runId), status, stamp, publication);
            }

            @Override
            public ShipRun resume(
                    String runId, List<? extends ShipContext.Input> additions) {
                return evidenceSnapshot(
                        controller.status(runId), status, stamp, publication);
            }
        });
    }

    private static ShipRun evidenceSnapshot(
            ShipRun run,
            ShipRun.RunStatus status,
            Path stamp,
            Path publication) {
        String stampDigest = ShipDigest.sha256("stamp".getBytes(StandardCharsets.UTF_8));
        List<ShipRun.StageRecord> stages = java.util.Arrays.stream(ShipRun.Stage.values())
                .map(stage -> {
                    boolean validation = stage == ShipRun.Stage.VALIDATE;
                    return new ShipRun.StageRecord(
                            stage,
                            validation && status == ShipRun.RunStatus.FAILED
                                    ? ShipRun.StageStatus.FAILED
                                    : ShipRun.StageStatus.COMPLETED,
                            1,
                            ShipDigest.sha256(
                                    (stage + " input").getBytes(StandardCharsets.UTF_8)),
                            validation
                                    ? stampDigest
                                    : ShipDigest.sha256(
                                            (stage + " output")
                                                    .getBytes(StandardCharsets.UTF_8)),
                            validation
                                    ? List.of(new ShipRun.ArtifactRef(
                                            stamp.toString(), stampDigest))
                                    : List.of());
                })
                .toList();
        ShipRun.ArtifactRef publicationRef = status == ShipRun.RunStatus.COMPLETED
                ? new ShipRun.ArtifactRef(publication.toString(), stampDigest)
                : null;
        return new ShipRun(
                run.schemaVersion(),
                run.id(),
                run.projectDirectory(),
                run.pipelineId(),
                run.oversight(),
                status,
                ShipRun.Stage.VALIDATE,
                run.context(),
                stages,
                publicationRef,
                run.createdAt(),
                run.updatedAt(),
                status == ShipRun.RunStatus.FAILED ? "Validation failed" : null);
    }

    private static String summary(
            String id, String status, String stage, String oversight, String... details) {
        List<String> lines = new ArrayList<>(
                List.of(
                        "Run: " + id,
                        "Status: " + status,
                        "Stage: " + stage,
                        "Oversight: " + oversight));
        lines.addAll(List.of(details));
        if ("PAUSED".equals(status)) {
            lines.add("Next: camel-kit ship --resume " + id
                      + " [--text TEXT | --document PATH]");
        } else if ("RUNNING".equals(status) || "FAILED".equals(status)) {
            lines.add("Next: camel-kit ship --resume " + id);
        }
        lines.add("");
        return String.join(System.lineSeparator(), lines);
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
                public ShipRun resume(
                        String runId, List<? extends ShipContext.Input> additions) {
                    return controller.resume(runId, additions);
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
                public ShipRun resume(
                        String runId, List<? extends ShipContext.Input> additions)
                        throws IOException, InterruptedException {
                    invocations.add("resume:" + runId);
                    return delegate.resume(runId, additions);
                }
            };
        }
    }

    private record RunResult(int exitCode, String output, String error) {
    }
}
