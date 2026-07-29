package io.github.luigidemasi.camelkit.command.ship;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Locale;

import io.github.luigidemasi.camelkit.ship.context.ShipContext.Kind;
import io.github.luigidemasi.camelkit.ship.controller.ShipController;
import io.github.luigidemasi.camelkit.ship.controller.ShipRun;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipCommandTest {

    @TempDir
    Path tempDir;

    @Test
    void bareStartUsesSmartOversightAndPrintsStableSummary() throws Exception {
        Path project = Files.createDirectory(tempDir.resolve("project"));
        ShipController controller = controller("state");

        RunResult result = run(controller, "--project-dir", project.toString());
        String id = runId(result.output());

        assertEquals(0, result.exitCode(), result.error());
        assertEquals("", result.error());
        assertEquals(summary(id, "RUNNING", "DISCOVERY", "SMART"), result.output());

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

        RunResult started = run(
                controller,
                "--project-dir", project.toString(),
                "--start-from", "design",
                "--text", "Build an integration");
        String id = runId(started.output());
        assertEquals(0, started.exitCode(), started.error());
        assertEquals(summary(id, "RUNNING", "DESIGN", "SMART"), started.output());

        RunResult resumed = run(controller, "--resume", id);
        assertEquals(0, resumed.exitCode(), resumed.error());
        assertEquals(started.output(), resumed.output());
        assertEquals(2, controller.status(id).stage(ShipRun.Stage.DESIGN).attempts());

        RunResult status = run(controller, "--status", id);
        assertEquals(0, status.exitCode(), status.error());
        assertEquals(started.output(), status.output());

        RunResult aborted = run(controller, "--abort", id);
        assertEquals(0, aborted.exitCode(), aborted.error());
        assertEquals(summary(id, "ABORTED", "DESIGN", "SMART"), aborted.output());

        RunResult rejected = run(controller, "--resume", id);
        assertEquals(1, rejected.exitCode());
        assertEquals("", rejected.output());
        assertTrue(rejected.error().matches("(?is)Error \\[[^]]+]: .*aborted.*\\R"),
                rejected.error());
    }

    @Test
    void rejectsExclusiveOperationsAndContextOnNonStartingOperations() throws Exception {
        ShipController controller = controller("state");
        String id = "ship-0123456789abcdef0123456789abcdef";

        RunResult exclusive = run(controller, "--resume", id, "--status", id);
        assertEquals(CommandLine.ExitCode.USAGE, exclusive.exitCode());
        assertTrue(exclusive.error().contains("mutually exclusive"), exclusive.error());

        RunResult context = run(controller, "--status", id, "--text", "not allowed");
        assertEquals(CommandLine.ExitCode.USAGE, context.exitCode());
        assertTrue(context.error().contains(
                "--ask, --text, and --document are only valid when starting a run"),
                context.error());

        RunResult oversight = run(controller, "--abort", id, "--ask", "never");
        assertEquals(CommandLine.ExitCode.USAGE, oversight.exitCode());
        assertTrue(oversight.error().contains(
                "--ask, --text, and --document are only valid when starting a run"),
                oversight.error());
    }

    @Test
    void keepsAtFileExpansionDisabled() throws Exception {
        Path arguments = Files.writeString(tempDir.resolve("arguments"), "--ask never");
        ShipController controller = controller("state");

        RunResult result = run(controller, "@" + arguments);

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

    private void assertDocumentFailure(
            ShipController controller, Path project, Path document, String expectedCode) {
        RunResult result = run(
                controller,
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

    private static RunResult run(ShipController controller, String... args) {
        StringWriter output = new StringWriter();
        StringWriter error = new StringWriter();
        CommandLine commandLine = new CommandLine(new ShipCommand(controller));
        commandLine.setExpandAtFiles(false);
        commandLine.setOut(new PrintWriter(output, true));
        commandLine.setErr(new PrintWriter(error, true));
        int exitCode = commandLine.execute(args);
        return new RunResult(exitCode, output.toString(), error.toString());
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

    private record RunResult(int exitCode, String output, String error) {
    }
}
