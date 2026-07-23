package io.github.luigidemasi.camelkit.ship.controller;

import java.io.RandomAccessFile;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import io.github.luigidemasi.camelkit.ship.ShipDigest;
import io.github.luigidemasi.camelkit.ship.context.ShipContext;
import io.github.luigidemasi.camelkit.ship.controller.ShipRun.Oversight;
import io.github.luigidemasi.camelkit.ship.controller.ShipRun.RunStatus;
import io.github.luigidemasi.camelkit.ship.controller.ShipRun.Stage;
import io.github.luigidemasi.camelkit.ship.controller.ShipRun.StageStatus;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class ShipControllerTest {

    @TempDir
    Path directory;

    @Test
    void startsWithNoInputTextOrDocumentContext() throws Exception {
        Path project = Files.createDirectory(directory.resolve("project"));
        ShipController controller = controller("state");

        ShipRun none = controller.start(project, Oversight.SMART, List.of());
        assertTrue(none.context().sources().isEmpty());
        assertEquals(RunStatus.RUNNING, none.status());
        assertEquals(Stage.DISCOVERY, none.currentStage());

        ShipRun text = controller.start(
                project, Oversight.SMART, List.of(new ShipContext.TextInput("orders")));
        assertEquals(1, text.context().sources().size());
        assertEquals(ShipContext.Kind.TEXT, text.context().sources().get(0).kind());
        assertEquals("orders", text.context().sources().get(0).value());

        Path document = Files.writeString(project.resolve("requirements.md"), "route requirements");
        ShipRun documented = controller.start(
                project, Oversight.SMART, List.of(new ShipContext.DocumentInput(document)));
        assertEquals(1, documented.context().sources().size());
        ShipContext.Source source = documented.context().sources().get(0);
        assertEquals(ShipContext.Kind.DOCUMENT, source.kind());
        assertEquals(document.toAbsolutePath().normalize().toString(), source.value());
        assertEquals(ShipDigest.sha256(Files.readAllBytes(document)), source.digest());
    }

    @Test
    void rejectsInvalidProjectRootsWithExactCodes() throws Exception {
        ShipController controller = controller("state");

        assertFailure("project-invalid",
                () -> controller.start(null, Oversight.NEVER, List.of()));
        assertFailure("project-missing",
                () -> controller.start(directory.resolve("missing"), Oversight.NEVER, List.of()));
        Path regularFile = Files.writeString(directory.resolve("file"), "not a project");
        assertFailure("project-invalid",
                () -> controller.start(regularFile, Oversight.NEVER, List.of()));

        Path brokenPath = (Path) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{Path.class},
                (proxy, method, arguments) -> {
                    throw new IllegalStateException("path conversion failed");
                });
        assertFailure("project-invalid",
                () -> controller.start(brokenPath, Oversight.NEVER, List.of()));
    }

    @Test
    void rejectsUnreadableProjectAndArtifact() throws Exception {
        Assumptions.assumeTrue(
                Files.getFileStore(directory).supportsFileAttributeView("posix"));
        Path unreadableProject = Files.createDirectory(directory.resolve("unreadable-project"));
        Files.setPosixFilePermissions(unreadableProject, PosixFilePermissions.fromString("---------"));
        try {
            Assumptions.assumeFalse(Files.isReadable(unreadableProject));
            assertFailure("project-unreadable",
                    () -> controller("project-state").start(
                            unreadableProject, Oversight.NEVER, List.of()));
        } finally {
            Files.setPosixFilePermissions(
                    unreadableProject, PosixFilePermissions.fromString("rwx------"));
        }

        Path project = Files.createDirectory(directory.resolve("project"));
        Path artifact = Files.writeString(project.resolve("artifact"), "result");
        ShipController controller = controller("artifact-state");
        ShipRun run = controller.start(project, Oversight.NEVER, List.of());
        Files.setPosixFilePermissions(artifact, PosixFilePermissions.fromString("---------"));
        try {
            Assumptions.assumeFalse(Files.isReadable(artifact));
            assertFailure("artifact-unreadable",
                    () -> complete(controller, run, "result", artifact));
        } finally {
            Files.setPosixFilePermissions(artifact, PosixFilePermissions.fromString("rw-------"));
        }
    }

    @Test
    void advancesLegallyAndAppliesEveryOversightPolicy() throws Exception {
        Path project = Files.createDirectory(directory.resolve("project"));
        ShipController controller = controller("state");

        ShipRun always = controller.start(project, Oversight.ALWAYS, List.of());
        always = complete(controller, always, "always-discovery");
        assertEquals(Stage.DESIGN, always.currentStage());
        assertEquals(RunStatus.RUNNING, always.status());
        always = complete(controller, always, "always-design");
        assertEquals(Stage.PLAN, always.currentStage());
        assertEquals(RunStatus.PAUSED, always.status());
        assertEquals(StageStatus.PENDING, always.stage(Stage.PLAN).status());

        ShipRun smart = controller.start(project, Oversight.SMART, List.of());
        smart = complete(controller, smart, "smart-discovery");
        smart = complete(controller, smart, "smart-design");
        assertEquals(RunStatus.RUNNING, smart.status());
        smart = complete(controller, smart, "smart-plan");
        assertEquals(Stage.EXECUTE, smart.currentStage());
        assertEquals(RunStatus.PAUSED, smart.status());

        ShipRun never = controller.start(project, Oversight.NEVER, List.of());
        for (Stage stage : Stage.values()) {
            assertEquals(stage, never.currentStage());
            never = complete(controller, never, "never-" + stage);
        }
        assertEquals(RunStatus.COMPLETED, never.status());
        assertEquals(Stage.VALIDATE, never.currentStage());
        assertTrue(never.stages().stream().allMatch(record -> record.status() == StageStatus.COMPLETED));
    }

    @Test
    void resumeRestartsTheEarliestStaleOrIncompleteStage() throws Exception {
        Path project = Files.createDirectory(directory.resolve("project"));
        Path discovery = Files.writeString(project.resolve("discovery.md"), "discovery-v1");
        Path design = Files.writeString(project.resolve("design.md"), "design-v1");
        Path plan = Files.writeString(project.resolve("plan.md"), "plan-v1");
        ShipController controller = controller("state");

        ShipRun run = controller.start(project, Oversight.SMART, List.of());
        run = complete(controller, run, "discovery", discovery);
        run = complete(controller, run, "design", design);
        run = complete(controller, run, "plan", plan);
        assertEquals(RunStatus.PAUSED, run.status());

        Files.writeString(design, "design-v2");
        ShipRun resumed = controller.resume(run.id());
        assertEquals(Stage.PLAN, resumed.currentStage());
        assertEquals(StageStatus.RUNNING, resumed.stage(Stage.PLAN).status());
        assertEquals(2, resumed.stage(Stage.PLAN).attempts());
        assertEquals(StageStatus.COMPLETED, resumed.stage(Stage.DESIGN).status());
        assertEquals(ShipDigest.sha256(Files.readAllBytes(design)),
                resumed.stage(Stage.DESIGN).artifacts().get(0).digest());

        Files.delete(discovery);
        ShipRun missingProducer = controller.resume(run.id());
        assertEquals(Stage.DISCOVERY, missingProducer.currentStage());
        assertEquals(StageStatus.RUNNING, missingProducer.stage(Stage.DISCOVERY).status());
        assertEquals(2, missingProducer.stage(Stage.DISCOVERY).attempts());
        assertEquals(StageStatus.PENDING, missingProducer.stage(Stage.DESIGN).status());
    }

    @Test
    void resumeRejectsTheInterruptedAttemptResult() throws Exception {
        Path project = Files.createDirectory(directory.resolve("project"));
        ShipController controller = controller("state");
        ShipRun started = controller.start(project, Oversight.NEVER, List.of());

        ShipRun resumed = controller.resume(started.id());
        assertEquals(2, resumed.stage(Stage.DISCOVERY).attempts());

        ShipController.Failure failure = assertThrows(
                ShipController.Failure.class,
                () -> controller.completeStage(
                        started.id(),
                        Stage.DISCOVERY,
                        1,
                        started.stage(Stage.DISCOVERY).inputDigest(),
                        digest("late-result"),
                        List.of(),
                        false));
        assertEquals("stale-stage-attempt", failure.code());
        assertEquals(2, controller.status(started.id()).stage(Stage.DISCOVERY).attempts());
    }

    @Test
    void rejectsAStageResultWithTheWrongInputDigest() throws Exception {
        Path project = Files.createDirectory(directory.resolve("project"));
        ShipController controller = controller("state");
        ShipRun run = controller.start(project, Oversight.NEVER, List.of());

        ShipController.Failure failure = assertThrows(
                ShipController.Failure.class,
                () -> controller.completeStage(
                        run.id(),
                        Stage.DISCOVERY,
                        1,
                        digest("wrong-input"),
                        digest("result"),
                        List.of(),
                        false));

        assertEquals("stale-stage-attempt", failure.code());
        assertEquals(run, controller.status(run.id()));
    }

    @Test
    void resumesAFailedStageWithANewAttempt() throws Exception {
        Path project = Files.createDirectory(directory.resolve("project"));
        ShipController controller = controller("state");
        ShipRun run = controller.start(project, Oversight.NEVER, List.of());

        ShipRun failed = controller.failStage(
                run.id(),
                Stage.DISCOVERY,
                1,
                run.stage(Stage.DISCOVERY).inputDigest(),
                "worker failed");
        assertEquals(RunStatus.FAILED, failed.status());
        assertEquals(StageStatus.FAILED, failed.stage(Stage.DISCOVERY).status());

        ShipRun resumed = controller.resume(run.id());
        assertEquals(RunStatus.RUNNING, resumed.status());
        assertEquals(StageStatus.RUNNING, resumed.stage(Stage.DISCOVERY).status());
        assertEquals(2, resumed.stage(Stage.DISCOVERY).attempts());
    }

    @Test
    void rejectsCompletionWhenDocumentContextChangedMidAttempt() throws Exception {
        Path project = Files.createDirectory(directory.resolve("project"));
        Path document = Files.writeString(project.resolve("requirements.md"), "version one");
        ShipController controller = controller("state");
        ShipRun run = controller.start(
                project, Oversight.NEVER, List.of(new ShipContext.DocumentInput(document)));

        Files.writeString(document, "version two");
        ShipController.Failure failure = assertThrows(
                ShipController.Failure.class,
                () -> controller.completeStage(
                        run.id(),
                        Stage.DISCOVERY,
                        1,
                        run.stage(Stage.DISCOVERY).inputDigest(),
                        digest("result"),
                        List.of(),
                        false));

        assertEquals("stale-stage-input", failure.code());
        assertEquals(run, controller.status(run.id()));
    }

    @Test
    void rejectsCompletionWhenAPredecessorArtifactChangedMidAttempt() throws Exception {
        Path project = Files.createDirectory(directory.resolve("project"));
        Path discovery = Files.writeString(project.resolve("discovery.md"), "version one");
        ShipController controller = controller("state");
        ShipRun run = controller.start(project, Oversight.NEVER, List.of());
        run = complete(controller, run, "discovery", discovery);

        Files.writeString(discovery, "version two");
        ShipRun design = run;
        ShipController.Failure failure = assertThrows(
                ShipController.Failure.class,
                () -> controller.completeStage(
                        design.id(),
                        Stage.DESIGN,
                        design.stage(Stage.DESIGN).attempts(),
                        design.stage(Stage.DESIGN).inputDigest(),
                        digest("design"),
                        List.of(),
                        false));

        assertEquals("stale-stage-input", failure.code());
        assertEquals(design, controller.status(design.id()));
    }

    @Test
    void rejectsInvalidMissingOversizedAndEmptyArtifacts() throws Exception {
        Path project = Files.createDirectory(directory.resolve("project"));
        Path outside = Files.writeString(directory.resolve("outside"), "outside");
        Path empty = Files.createFile(project.resolve("empty"));
        Path oversized = project.resolve("oversized");
        try (RandomAccessFile file = new RandomAccessFile(oversized.toFile(), "rw")) {
            file.setLength(64L * 1024 * 1024 + 1);
        }
        ShipController controller = controller("state");
        ShipRun run = controller.start(project, Oversight.NEVER, List.of());

        ShipController.Failure escaped = assertThrows(
                ShipController.Failure.class,
                () -> complete(
                        controller,
                        run,
                        "outside",
                        project.resolve("..").resolve(outside.getFileName())));
        assertEquals("artifact-invalid", escaped.code());
        assertTrue(escaped.getMessage().contains("outside the project"));
        assertFailure("artifact-missing",
                () -> complete(controller, run, "missing", project.resolve("missing")));
        assertFailure("artifact-too-large",
                () -> complete(controller, run, "oversized", oversized));
        assertFailure("artifact-invalid", () -> complete(controller, run, "empty", empty));
        assertEquals(run, controller.status(run.id()));
    }

    @Test
    void rejectsMutationsAfterTheProjectIsDeletedWithoutChangingState() throws Exception {
        Path project = Files.createDirectory(directory.resolve("project"));
        ShipController controller = controller("state");
        ShipRun run = controller.start(project, Oversight.NEVER, List.of());
        Files.delete(project);

        assertFailure("project-missing", () -> controller.resume(run.id()));
        assertFailure("project-missing", () -> complete(controller, run, "result"));
        assertFailure("project-missing",
                () -> controller.failStage(
                        run.id(),
                        Stage.DISCOVERY,
                        1,
                        run.stage(Stage.DISCOVERY).inputDigest(),
                        "worker failed"));
        assertEquals(run, controller.status(run.id()));
    }

    @Test
    void mutationTimeNeverMovesBackward() throws Exception {
        Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
        Instant t2 = Instant.parse("2026-01-01T02:00:00Z");
        Instant tMinusOne = Instant.parse("2025-12-31T23:00:00Z");
        Path project = Files.createDirectory(directory.resolve("project"));
        Path state = directory.resolve("state");

        ShipRun created = new ShipController(state, Clock.fixed(t0, ZoneOffset.UTC))
                .start(project, Oversight.NEVER, List.of());
        ShipRun failed = new ShipController(state, Clock.fixed(t2, ZoneOffset.UTC))
                .failStage(
                        created.id(),
                        Stage.DISCOVERY,
                        1,
                        created.stage(Stage.DISCOVERY).inputDigest(),
                        "worker failed");
        ShipRun resumed = new ShipController(state, Clock.fixed(tMinusOne, ZoneOffset.UTC))
                .resume(created.id());

        assertEquals(t0.toString(), created.createdAt());
        assertEquals(t0.toString(), created.updatedAt());
        assertEquals(t2.toString(), failed.updatedAt());
        assertEquals(t2.toString(), resumed.updatedAt());
    }

    @Test
    void statusReadsAndAbortTerminatesThePersistedRun() throws Exception {
        Path project = Files.createDirectory(directory.resolve("project"));
        ShipController controller = controller("state");
        ShipRun started = controller.start(project, Oversight.NEVER, List.of());

        assertEquals(started, controller.status(started.id()));
        ShipRun aborted = controller.abort(started.id());
        assertEquals(RunStatus.ABORTED, aborted.status());
        assertEquals(StageStatus.ABORTED, aborted.stage(Stage.DISCOVERY).status());
        assertEquals(RunStatus.ABORTED, controller.status(started.id()).status());
        assertFailure("run-aborted", () -> controller.resume(started.id()));
    }

    @Test
    void startFromImportsExistingArtifactsAtTheirCurrentDigests() throws Exception {
        Path project = Files.createDirectory(directory.resolve("project"));
        Path metadata = Files.createDirectories(project.resolve(".camel-kit"));
        byte[] pipeline = """
                {"mode":"manual","activePipeline":"149-local-controller"}
                """.getBytes(StandardCharsets.UTF_8);
        Files.write(metadata.resolve("pipeline.json"), pipeline);
        Path documents = Files.createDirectories(
                project.resolve("docs/camel-kit/149-local-controller"));
        Path design = Files.writeString(documents.resolve("design-spec.md"), "design");
        Path plan = Files.writeString(documents.resolve("implementation-plan.md"), "plan");

        ShipRun run = controller("state").startFrom(
                project,
                Stage.EXECUTE,
                Oversight.NEVER,
                List.of(new ShipContext.TextInput("build the route")));

        assertEquals("149-local-controller", run.pipelineId());
        assertEquals(Stage.EXECUTE, run.currentStage());
        assertEquals(StageStatus.RUNNING, run.stage(Stage.EXECUTE).status());
        assertEquals(StageStatus.COMPLETED, run.stage(Stage.DISCOVERY).status());
        assertEquals(StageStatus.COMPLETED, run.stage(Stage.DESIGN).status());
        assertEquals(StageStatus.COMPLETED, run.stage(Stage.PLAN).status());
        assertEquals(design.toAbsolutePath().normalize().toString(),
                run.stage(Stage.DESIGN).artifacts().get(0).path());
        assertEquals(ShipDigest.sha256(Files.readAllBytes(plan)),
                run.stage(Stage.PLAN).artifacts().get(0).digest());
        assertArrayEquals(pipeline, Files.readAllBytes(metadata.resolve("pipeline.json")));
    }

    @Test
    void startFromDesignRequiresContext() throws Exception {
        Path project = Files.createDirectory(directory.resolve("project"));

        ShipController.Failure failure = assertThrows(
                ShipController.Failure.class,
                () -> controller("state").startFrom(
                        project, Stage.DESIGN, Oversight.NEVER, List.of()));

        assertEquals("start-from-context-missing", failure.code());
    }

    @Test
    void startFromLaterStagesRequiresAnActivePipeline() throws Exception {
        Path project = Files.createDirectory(directory.resolve("project"));
        ShipController controller = controller("state");

        for (Stage stage : List.of(Stage.PLAN, Stage.EXECUTE, Stage.VALIDATE)) {
            assertFailure("start-from-artifact-missing",
                    () -> controller.startFrom(project, stage, Oversight.NEVER, List.of()));
        }
    }

    @Test
    void startFromValidateRecordsAndRechecksTheProjectSnapshot() throws Exception {
        Path project = Files.createDirectory(directory.resolve("project"));
        Path metadata = Files.createDirectories(project.resolve(".camel-kit"));
        Files.writeString(
                metadata.resolve("pipeline.json"),
                "{\"mode\":\"manual\",\"activePipeline\":\"149-local-controller\"}\n");
        Path documents = Files.createDirectories(
                project.resolve("docs/camel-kit/149-local-controller"));
        Files.writeString(documents.resolve("design-spec.md"), "design");
        Files.writeString(documents.resolve("implementation-plan.md"), "plan");
        Files.writeString(documents.resolve("execution-report.md"), "execution");
        Path route = Files.writeString(project.resolve("route.yaml"), "version one");
        ShipController controller = controller("state");

        ShipRun validating = controller.startFrom(
                project,
                Stage.VALIDATE,
                Oversight.ALWAYS,
                List.of(new ShipContext.TextInput("build the route")));
        assertEquals(2, validating.stage(Stage.EXECUTE).artifacts().size());
        assertEquals(project.toAbsolutePath().normalize().toString(),
                validating.stage(Stage.EXECUTE).artifacts().get(1).path());
        String snapshotDigest = validating.stage(Stage.EXECUTE).artifacts().get(1).digest();

        ShipRun paused = complete(controller, validating, "validated");
        assertEquals(RunStatus.PAUSED, paused.status());
        Files.writeString(route, "version two");

        ShipRun resumed = controller.resume(paused.id());
        assertEquals(Stage.VALIDATE, resumed.currentStage());
        assertEquals(StageStatus.RUNNING, resumed.stage(Stage.VALIDATE).status());
        assertEquals(2, resumed.stage(Stage.VALIDATE).attempts());
        assertNotEquals(
                snapshotDigest, resumed.stage(Stage.EXECUTE).artifacts().get(1).digest());
    }

    @Test
    void rejectsAnArtifactSymlinkThatEscapesTheProject() throws Exception {
        Path project = Files.createDirectory(directory.resolve("project"));
        Path outside = Files.writeString(directory.resolve("outside.md"), "outside");
        Path link = Files.createSymbolicLink(project.resolve("result.md"), outside);
        ShipController controller = controller("state");
        ShipRun run = controller.start(project, Oversight.NEVER, List.of());

        ShipController.Failure failure = assertThrows(
                ShipController.Failure.class,
                () -> controller.completeStage(
                        run.id(),
                        Stage.DISCOVERY,
                        1,
                        run.stage(Stage.DISCOVERY).inputDigest(),
                        digest("result"),
                        List.of(link),
                        false));

        assertEquals("artifact-invalid", failure.code());
        assertEquals(run, controller.status(run.id()));
    }

    @Test
    void rejectsAndPreservesPreReleaseProjectState() throws Exception {
        Path incompatiblePipeline = Files.createDirectory(directory.resolve("incompatible-pipeline"));
        Path incompatibleMetadata = Files.createDirectories(incompatiblePipeline.resolve(".camel-kit"));
        byte[] pipeline = """
                {"mode":"autonomous","activePipeline":"149-old"}
                """.getBytes(StandardCharsets.UTF_8);
        Path pipelineFile = Files.write(incompatibleMetadata.resolve("pipeline.json"), pipeline);

        ShipController.Failure pipelineFailure = assertThrows(
                ShipController.Failure.class,
                () -> controller("pipeline-state").start(
                        incompatiblePipeline, Oversight.SMART, List.of()));
        assertEquals("pre-release-ship-state", pipelineFailure.code());
        assertArrayEquals(pipeline, Files.readAllBytes(pipelineFile));

        Path shipStateProject = Files.createDirectory(directory.resolve("ship-state-project"));
        Path shipMetadata = Files.createDirectories(shipStateProject.resolve(".camel-kit"));
        byte[] shipState = "{\"old\":true}\n".getBytes(StandardCharsets.UTF_8);
        Path shipStateFile = Files.write(shipMetadata.resolve("ship-state.json"), shipState);

        ShipController.Failure shipStateFailure = assertThrows(
                ShipController.Failure.class,
                () -> controller("ship-state").start(
                        shipStateProject, Oversight.SMART, List.of()));
        assertEquals("pre-release-ship-state", shipStateFailure.code());
        assertArrayEquals(shipState, Files.readAllBytes(shipStateFile));
    }

    private ShipController controller(String stateDirectory) {
        return new ShipController(directory.resolve(stateDirectory));
    }

    private static ShipRun complete(
            ShipController controller, ShipRun run, String result, Path... artifacts) {
        Stage stage = run.currentStage();
        return controller.completeStage(
                run.id(),
                stage,
                run.stage(stage).attempts(),
                run.stage(stage).inputDigest(),
                digest(result),
                List.of(artifacts),
                false);
    }

    private static String digest(String value) {
        return ShipDigest.sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    private static void assertFailure(String code, Executable operation) {
        ShipController.Failure failure = assertThrows(ShipController.Failure.class, operation);
        assertEquals(code, failure.code());
    }
}
