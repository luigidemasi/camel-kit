package io.github.luigidemasi.camelkit.ship.controller;

import java.io.RandomAccessFile;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.github.luigidemasi.camelkit.ship.ShipDigest;
import io.github.luigidemasi.camelkit.ship.context.ShipContext;
import io.github.luigidemasi.camelkit.ship.controller.ShipRun.Oversight;
import io.github.luigidemasi.camelkit.ship.controller.ShipRun.RunStatus;
import io.github.luigidemasi.camelkit.ship.controller.ShipRun.Stage;
import io.github.luigidemasi.camelkit.ship.controller.ShipRun.StageStatus;
import io.github.luigidemasi.camelkit.ship.evidence.ShipLocalStamp;
import io.github.luigidemasi.camelkit.ship.evidence.ShipLocalStamp.Outcome;
import io.github.luigidemasi.camelkit.ship.evidence.ShipLocalStampStore;
import io.github.luigidemasi.camelkit.ship.security.ProjectEvidenceFiles;
import io.github.luigidemasi.camelkit.ship.security.ProjectSnapshot;
import io.github.luigidemasi.camelkit.ship.security.ShipFilesystemException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class ShipControllerTest {

    @TempDir
    Path directory;

    @AfterEach
    void makePublishedWorkspacesRemovable() throws Exception {
        if (!directory.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.filter(item -> {
                String name = String.valueOf(item.getFileName());
                return "workspace".equals(name) || ".workspace-stale".equals(name);
            })
                    .toList()) {
                if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                    Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwx------"));
                }
            }
        }
    }

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
    void rejectsSensitiveInlineContextBeforeCreatingRunState() throws Exception {
        String secret = "inline-context-secret-12345678";
        Path project = Files.createDirectory(directory.resolve("project"));
        Path state = directory.resolve("state");
        ShipController controller = new ShipController(
                state, Map.of("SHIP_TEST_TOKEN", secret));

        ShipController.Failure failure = assertThrows(
                ShipController.Failure.class,
                () -> controller.start(
                        project,
                        Oversight.NEVER,
                        List.of(new ShipContext.TextInput("requirements: " + secret))));

        assertEquals("context-secret-detected", failure.code());
        assertFalse(failure.toString().contains(secret));
        assertNull(failure.getCause());
        assertFalse(Files.exists(state));
    }

    @Test
    void rejectsSensitiveDocumentContextBeforeCreatingStartFromState()
            throws Exception {
        String secret = "document-context-secret-12345678";
        Path project = Files.createDirectory(directory.resolve("project"));
        Path document = Files.writeString(
                project.resolve("requirements.md"), "requirements: " + secret);
        Path state = directory.resolve("state");
        ShipController controller = new ShipController(
                state, Map.of("SHIP_TEST_TOKEN", secret));

        ShipController.Failure failure = assertThrows(
                ShipController.Failure.class,
                () -> controller.startFrom(
                        project,
                        Stage.DESIGN,
                        Oversight.NEVER,
                        List.of(new ShipContext.DocumentInput(document))));

        assertEquals("context-secret-detected", failure.code());
        assertFalse(failure.toString().contains(secret));
        assertNull(failure.getCause());
        assertFalse(Files.exists(state));
    }

    @Test
    void rejectsSensitiveDocumentPathBeforeCreatingRunState()
            throws Exception {
        String secret = "document-path-secret-12345678";
        Path project = Files.createDirectory(directory.resolve("project"));
        Path document = Files.writeString(
                project.resolve(secret + ".md"), "safe requirements");
        Path state = directory.resolve("state");
        ShipController controller = new ShipController(
                state, Map.of("SHIP_TEST_TOKEN", secret));

        ShipController.Failure failure = assertThrows(
                ShipController.Failure.class,
                () -> controller.start(
                        project,
                        Oversight.NEVER,
                        List.of(new ShipContext.DocumentInput(document))));

        assertEquals("context-secret-detected", failure.code());
        assertFalse(failure.toString().contains(secret));
        assertNull(failure.getCause());
        assertFalse(Files.exists(state));
    }

    @Test
    void rejectsSensitiveDocumentRefreshWithoutChangingRunState()
            throws Exception {
        String secret = "refreshed-context-secret-12345678";
        Path project = Files.createDirectory(directory.resolve("project"));
        Path document = Files.writeString(
                project.resolve("requirements.md"), "safe requirements");
        Path state = directory.resolve("state");
        ShipController controller = new ShipController(
                state, Map.of("SHIP_TEST_TOKEN", secret));
        ShipRun started = controller.start(
                project,
                Oversight.NEVER,
                List.of(new ShipContext.DocumentInput(document)));
        Files.writeString(document, "requirements: " + secret);

        ShipController.Failure failure = assertThrows(
                ShipController.Failure.class,
                () -> controller.resume(started.id()));

        assertEquals("context-secret-detected", failure.code());
        assertFalse(failure.toString().contains(secret));
        assertNull(failure.getCause());
        assertEquals(started, controller.status(started.id()));
        assertFalse(Files.readString(
                state.resolve(started.id()).resolve("state.json")).contains(secret));
    }

    @Test
    void rejectsControllerStateInsideTheProjectBeforeCreatingIt() throws Exception {
        Path project = Files.createDirectory(directory.resolve("project"));
        Path state = project.resolve("ship-state");

        assertFailure(
                "state-project-overlap",
                () -> new ShipController(state).start(project, Oversight.NEVER, List.of()));
        assertFalse(Files.exists(state));

        Path embeddedState = Files.createDirectory(project.resolve("embedded-state"));
        Path linkedState = Files.createSymbolicLink(
                directory.resolve("linked-state"), embeddedState);
        assertFailure(
                "state-project-overlap",
                () -> new ShipController(linkedState).start(
                        project, Oversight.NEVER, List.of()));
        try (var entries = Files.list(embeddedState)) {
            assertTrue(entries.findAny().isEmpty());
        }

        Path linkedProject = Files.createSymbolicLink(
                directory.resolve("linked-project"), project);
        Path nestedState = linkedProject.resolve("nested-state");
        assertFailure(
                "state-project-overlap",
                () -> new ShipController(nestedState).start(
                        project, Oversight.NEVER, List.of()));
        assertFalse(Files.exists(project.resolve("nested-state")));

        Path metadata = Files.createDirectories(project.resolve(".camel-kit"));
        Files.writeString(
                metadata.resolve("pipeline.json"),
                "{\"mode\":\"manual\",\"activePipeline\":\"149-import\"}\n");
        Path documents = Files.createDirectories(project.resolve("docs/camel-kit/149-import"));
        Files.writeString(documents.resolve("design-spec.md"), "design");
        Files.writeString(documents.resolve("implementation-plan.md"), "plan");
        Files.writeString(documents.resolve("execution-report.md"), "execution");
        Path importedState = project.resolve("imported-ship-state");

        assertFailure(
                "state-project-overlap",
                () -> new ShipController(importedState).startFrom(
                        project, Stage.VALIDATE, Oversight.NEVER, List.of()));
        assertFalse(Files.exists(importedState));
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
        Path realParent = Files.createDirectory(directory.resolve("real-parent"));
        Path realProject = Files.createDirectory(realParent.resolve("project"));
        Path rootLink = directory.resolve("project-link");
        Files.createSymbolicLink(rootLink, realProject);
        assertFailure("project-invalid",
                () -> controller.start(rootLink, Oversight.NEVER, List.of()));

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
    void startsAProjectBelowASymlinkedAncestorAtItsCanonicalPath() throws Exception {
        Path realParent = Files.createDirectory(directory.resolve("real-parent"));
        Path realProject = Files.createDirectory(realParent.resolve("project"));
        Path parentLink = Files.createSymbolicLink(
                directory.resolve("parent-link"), realParent);

        ShipRun run = controller("state").start(
                parentLink.resolve("project"), Oversight.NEVER, List.of());

        assertEquals(realProject.toRealPath().toString(), run.projectDirectory());
    }

    @Test
    void completesAndReadsExecuteWithStateBelowASymlinkedAncestor() throws Exception {
        Path project = Files.createDirectory(directory.resolve("project"));
        Path realStateParent = Files.createDirectory(directory.resolve("real-state-parent"));
        Path linkedStateParent = Files.createSymbolicLink(
                directory.resolve("linked-state-parent"), realStateParent);
        Path stateRoot = linkedStateParent.resolve("state");
        ShipController controller = new ShipController(stateRoot);
        ShipRun run = controller.start(project, Oversight.NEVER, List.of());
        run = complete(controller, run, "discovery");
        run = complete(controller, run, "design");
        run = complete(controller, run, "plan");

        Path workspace = controller.prepareWorkspace(
                run.id(),
                run.stage(Stage.EXECUTE).attempts(),
                run.stage(Stage.EXECUTE).inputDigest());
        Path route = Files.writeString(
                workspace.resolve("route.yaml"), "from:\n  uri: direct:start\n");
        ShipRun validating = controller.completeExecuteStage(
                run.id(),
                run.stage(Stage.EXECUTE).attempts(),
                run.stage(Stage.EXECUTE).inputDigest(),
                List.of(route),
                false);

        assertEquals(validating, new ShipController(stateRoot).status(run.id()));
        assertEquals(
                realStateParent.resolve("state").toRealPath()
                        .resolve(run.id()).resolve("workspace/candidate").toString(),
                validating.stage(Stage.EXECUTE).artifacts().get(0).path());
    }

    @Test
    void rejectsUnreadableProjectAndArtifact() throws Exception {
        Assumptions.assumeTrue(
                Files.getFileStore(directory).supportsFileAttributeView("posix"));
        Path unreadableProject = Files.createDirectory(directory.resolve("unreadable-project"));
        Files.setPosixFilePermissions(unreadableProject, PosixFilePermissions.fromString("---------"));
        try {
            assertFalse(Files.isReadable(unreadableProject));
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
            assertFalse(Files.isReadable(artifact));
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
            if (stage == Stage.VALIDATE) {
                ShipController.StageAttempt attempt
                        = controller.prepareAttempt(never.id());
                ShipLocalStamp stamp = localStamp(never.id(), Outcome.PASS);
                ShipLocalStampStore.write(
                        attempt.evidenceDirectory(), never.id(), stamp);
                never = controller.completeValidationStage(
                        never.id(),
                        never.stage(stage).attempts(),
                        never.stage(stage).inputDigest(),
                        stamp);
            } else {
                never = complete(controller, never, "never-" + stage);
            }
        }
        assertEquals(RunStatus.COMPLETED, never.status());
        assertEquals(Stage.VALIDATE, never.currentStage());
        assertTrue(never.stages().stream().allMatch(record -> record.status() == StageStatus.COMPLETED));
    }

    @Test
    void rejectsGenericWorkerCompletionForValidation() throws Exception {
        Path project = Files.createDirectory(directory.resolve("project"));
        ShipController controller = controller("state");
        ShipRun validating = advanceToValidation(
                controller, project, Oversight.NEVER);

        ShipController.Failure failure = assertThrows(
                ShipController.Failure.class,
                () -> controller.completeStage(
                        validating.id(),
                        Stage.VALIDATE,
                        validating.stage(Stage.VALIDATE).attempts(),
                        validating.stage(Stage.VALIDATE).inputDigest(),
                        digest("worker-prose-pass"),
                        List.of(),
                        false));

        assertEquals("validation-controller-owned", failure.code());
        assertEquals(validating, controller.status(validating.id()));
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
    void retainsAndReloadsAPassingLocalStamp() throws Exception {
        Path project = Files.createDirectory(directory.resolve("project"));
        Path state = directory.resolve("state");
        ShipController controller = new ShipController(state);
        ShipRun validating = advanceToValidation(
                controller, project, Oversight.ALWAYS);
        ShipController.StageAttempt attempt = controller.prepareAttempt(validating.id());
        ShipLocalStamp stamp = localStamp(validating.id(), Outcome.PASS);
        Path stampPath = ShipLocalStampStore.write(
                attempt.evidenceDirectory(), validating.id(), stamp);

        ShipRun paused = controller.completeValidationStage(
                validating.id(),
                validating.stage(Stage.VALIDATE).attempts(),
                validating.stage(Stage.VALIDATE).inputDigest(),
                stamp);

        ShipRun.StageRecord validation = paused.stage(Stage.VALIDATE);
        assertEquals(RunStatus.PAUSED, paused.status());
        assertEquals(StageStatus.COMPLETED, validation.status());
        assertEquals(List.of(stampPath.toRealPath().toString()),
                validation.artifacts().stream().map(ShipRun.ArtifactRef::path).toList());
        assertEquals(
                ShipDigest.sha256(Files.readAllBytes(stampPath)),
                validation.outputDigest());
        ShipController reloaded = new ShipController(state);
        assertEquals(paused, reloaded.status(paused.id()));

        ShipRun completed = reloaded.resume(paused.id());
        assertEquals(RunStatus.COMPLETED, completed.status());
        assertEquals(validation, completed.stage(Stage.VALIDATE));
    }

    @Test
    void restartsValidationWhenAPassingLocalStampIsMissingOrCorrupt()
            throws Exception {
        for (String condition : List.of("missing", "corrupt")) {
            Path project = Files.createDirectory(
                    directory.resolve(condition + "-project"));
            Path state = directory.resolve(condition + "-state");
            ShipController controller = new ShipController(state);
            ShipRun validating = advanceToValidation(
                    controller, project, Oversight.ALWAYS);
            ShipController.StageAttempt attempt
                    = controller.prepareAttempt(validating.id());
            ShipLocalStamp stamp = localStamp(validating.id(), Outcome.PASS);
            Path stampPath = ShipLocalStampStore.write(
                    attempt.evidenceDirectory(), validating.id(), stamp);
            ShipRun paused = controller.completeValidationStage(
                    validating.id(),
                    validating.stage(Stage.VALIDATE).attempts(),
                    validating.stage(Stage.VALIDATE).inputDigest(),
                    stamp);

            if ("missing".equals(condition)) {
                Files.delete(stampPath);
            } else {
                Files.writeString(stampPath, "not a local Stamp");
            }

            ShipRun resumed = controller.resume(paused.id());

            assertEquals(RunStatus.RUNNING, resumed.status(), condition);
            assertEquals(Stage.VALIDATE, resumed.currentStage(), condition);
            assertEquals(
                    StageStatus.RUNNING,
                    resumed.stage(Stage.VALIDATE).status(),
                    condition);
            assertEquals(
                    2,
                    resumed.stage(Stage.VALIDATE).attempts(),
                    condition);
            assertTrue(
                    resumed.stage(Stage.VALIDATE).artifacts().isEmpty(),
                    condition);
        }
    }

    @Test
    void completedRunRestartsOnlyWhenItsLocalStampIsMissingOrCorrupt()
            throws Exception {
        for (String condition : List.of(
                "intact", "missing", "changed-bytes", "corrupt-stamp", "corrupt-log")) {
            Path project = Files.createDirectory(
                    directory.resolve("completed-" + condition + "-project"));
            Path state = directory.resolve("completed-" + condition + "-state");
            ShipController controller = new ShipController(state);
            ShipRun validating = advanceToValidation(
                    controller, project, Oversight.NEVER);
            ShipController.StageAttempt attempt
                    = controller.prepareAttempt(validating.id());
            ShipLocalStamp stamp = "corrupt-log".equals(condition)
                    ? localStampWithLogs(validating.id(), attempt.evidenceDirectory())
                    : localStamp(validating.id(), Outcome.PASS);
            Path stampPath = ShipLocalStampStore.write(
                    attempt.evidenceDirectory(), validating.id(), stamp);
            ShipRun completed = controller.completeValidationStage(
                    validating.id(),
                    validating.stage(Stage.VALIDATE).attempts(),
                    validating.stage(Stage.VALIDATE).inputDigest(),
                    stamp);
            assertEquals(RunStatus.COMPLETED, completed.status(), condition);

            if ("intact".equals(condition)) {
                assertFailure(
                        "run-completed",
                        () -> controller.resume(completed.id()));
                assertEquals(completed, controller.status(completed.id()));
                continue;
            }
            if ("missing".equals(condition)) {
                Files.delete(stampPath);
            } else if ("changed-bytes".equals(condition)) {
                Files.writeString(
                        stampPath, Files.readString(stampPath) + "\n");
            } else if ("corrupt-log".equals(condition)) {
                Files.writeString(
                        attempt.evidenceDirectory().resolve("stdout.log"),
                        "corrupt");
            } else {
                Files.writeString(stampPath, "not a local Stamp");
            }

            ShipRun resumed = controller.resume(completed.id());

            assertEquals(RunStatus.RUNNING, resumed.status(), condition);
            assertEquals(Stage.VALIDATE, resumed.currentStage(), condition);
            assertEquals(StageStatus.RUNNING,
                    resumed.stage(Stage.VALIDATE).status(), condition);
            assertEquals(2, resumed.stage(Stage.VALIDATE).attempts(), condition);
            assertTrue(resumed.stage(Stage.VALIDATE).artifacts().isEmpty(), condition);
        }
    }

    @Test
    void completedRunRefreshesContextBeforeAcceptingItsLocalStamp()
            throws Exception {
        Path project = Files.createDirectory(directory.resolve("context-project"));
        Path document = Files.writeString(
                project.resolve("requirements.md"), "requirements-v1");
        ShipController controller = controller("context-state");
        ShipRun run = controller.start(
                project,
                Oversight.NEVER,
                List.of(new ShipContext.DocumentInput(document)));
        while (run.currentStage() != Stage.VALIDATE) {
            run = complete(controller, run, run.currentStage().name().toLowerCase(Locale.ROOT));
        }
        ShipController.StageAttempt attempt = controller.prepareAttempt(run.id());
        ShipLocalStamp stamp = localStamp(run.id(), Outcome.PASS);
        ShipLocalStampStore.write(attempt.evidenceDirectory(), run.id(), stamp);
        ShipRun completed = controller.completeValidationStage(
                run.id(),
                run.stage(Stage.VALIDATE).attempts(),
                run.stage(Stage.VALIDATE).inputDigest(),
                stamp);
        assertEquals(RunStatus.COMPLETED, completed.status());

        Files.writeString(document, "requirements-v2");
        ShipRun resumed = controller.resume(completed.id());

        assertEquals(RunStatus.RUNNING, resumed.status());
        assertEquals(Stage.DISCOVERY, resumed.currentStage());
        assertEquals(2, resumed.stage(Stage.DISCOVERY).attempts());
        assertEquals(StageStatus.PENDING, resumed.stage(Stage.VALIDATE).status());
    }

    @Test
    void completedRunRefreshesPredecessorArtifactsBeforeAcceptingItsLocalStamp()
            throws Exception {
        Path project = Files.createDirectory(directory.resolve("artifact-project"));
        Path discovery = Files.writeString(project.resolve("discovery.md"), "discovery-v1");
        Path design = Files.writeString(project.resolve("design.md"), "design-v1");
        Path plan = Files.writeString(project.resolve("plan.md"), "plan-v1");
        ShipController controller = controller("artifact-state");
        ShipRun run = controller.start(project, Oversight.NEVER, List.of());
        run = complete(controller, run, "discovery", discovery);
        run = complete(controller, run, "design", design);
        run = complete(controller, run, "plan", plan);
        run = complete(controller, run, "execute");
        ShipController.StageAttempt attempt = controller.prepareAttempt(run.id());
        ShipLocalStamp stamp = localStamp(run.id(), Outcome.PASS);
        ShipLocalStampStore.write(attempt.evidenceDirectory(), run.id(), stamp);
        ShipRun completed = controller.completeValidationStage(
                run.id(),
                run.stage(Stage.VALIDATE).attempts(),
                run.stage(Stage.VALIDATE).inputDigest(),
                stamp);
        assertEquals(RunStatus.COMPLETED, completed.status());

        Files.writeString(design, "design-v2");
        ShipRun resumed = controller.resume(completed.id());

        assertEquals(RunStatus.RUNNING, resumed.status());
        assertEquals(Stage.PLAN, resumed.currentStage());
        assertEquals(2, resumed.stage(Stage.PLAN).attempts());
        assertEquals(StageStatus.COMPLETED, resumed.stage(Stage.DESIGN).status());
        assertEquals(ShipDigest.sha256(Files.readAllBytes(design)),
                resumed.stage(Stage.DESIGN).artifacts().get(0).digest());
        assertEquals(StageStatus.PENDING, resumed.stage(Stage.VALIDATE).status());
    }

    @Test
    void retainsAndReloadsAFailingLocalStampBeforeRetry() throws Exception {
        Path project = Files.createDirectory(directory.resolve("project"));
        Path state = directory.resolve("state");
        ShipController controller = new ShipController(state);
        ShipRun validating = advanceToValidation(
                controller, project, Oversight.NEVER);
        ShipController.StageAttempt attempt = controller.prepareAttempt(validating.id());
        ShipLocalStamp stamp = localStamp(validating.id(), Outcome.FAIL);
        Path stampPath = ShipLocalStampStore.write(
                attempt.evidenceDirectory(), validating.id(), stamp);

        ShipRun failed = controller.completeValidationStage(
                validating.id(),
                validating.stage(Stage.VALIDATE).attempts(),
                validating.stage(Stage.VALIDATE).inputDigest(),
                stamp);

        ShipRun.StageRecord validation = failed.stage(Stage.VALIDATE);
        assertEquals(RunStatus.FAILED, failed.status());
        assertEquals(StageStatus.FAILED, validation.status());
        assertEquals(stampPath.toRealPath().toString(),
                validation.artifacts().get(0).path());
        assertEquals(
                ShipDigest.sha256(Files.readAllBytes(stampPath)),
                validation.outputDigest());
        ShipController reloaded = new ShipController(state);
        assertEquals(failed, reloaded.status(failed.id()));

        ShipRun retried = reloaded.resume(failed.id());
        assertEquals(RunStatus.RUNNING, retried.status());
        assertEquals(StageStatus.RUNNING, retried.stage(Stage.VALIDATE).status());
        assertEquals(2, retried.stage(Stage.VALIDATE).attempts());
        assertTrue(retried.stage(Stage.VALIDATE).artifacts().isEmpty());
    }

    @Test
    void usesTheInjectedEnvironmentToRejectChangedExecuteSecrets() throws Exception {
        String secret = "correct-horse-battery-staple";
        Map<String, String> environment = new HashMap<>();
        environment.put("SHIP_TEST_TOKEN", secret);
        Path project = Files.createDirectory(directory.resolve("project"));
        ShipController controller = new ShipController(
                directory.resolve("state"), environment);
        environment.clear();
        ShipRun run = controller.start(project, Oversight.NEVER, List.of());
        run = complete(controller, run, "discovery");
        run = complete(controller, run, "design");
        run = complete(controller, run, "plan");
        Path workspace = controller.prepareWorkspace(
                run.id(),
                run.stage(Stage.EXECUTE).attempts(),
                run.stage(Stage.EXECUTE).inputDigest());
        Path route = Files.writeString(
                workspace.resolve("route.yaml"), "password: " + secret);
        ShipRun executing = run;

        assertFailure(
                "workspace-secret-detected",
                () -> controller.completeExecuteStage(
                        executing.id(),
                        executing.stage(Stage.EXECUTE).attempts(),
                        executing.stage(Stage.EXECUTE).inputDigest(),
                        List.of(route),
                        false));
        assertEquals(executing, controller.status(executing.id()));
    }

    @Test
    void preparesPrivateWorkerDirectoriesAndReleasesTheRunLock() throws Exception {
        Assumptions.assumeTrue(
                Files.getFileStore(directory).supportsFileAttributeView("posix"));
        Path project = Files.createDirectory(directory.resolve("project"));
        Path state = directory.resolve("state");
        ShipController controller = new ShipController(state);
        ShipRun run = controller.start(project, Oversight.NEVER, List.of());

        ShipController.StageAttempt attempt = controller.prepareAttempt(run.id());
        Path runDirectory = state.resolve(run.id()).toRealPath();

        assertEquals(runDirectory, attempt.runDirectory());
        assertEquals(runDirectory.resolve("sessions"), attempt.sessionDirectory());
        assertEquals(runDirectory.resolve("inputs"), attempt.inputDirectory());
        assertEquals(
                runDirectory.resolve("evidence/discovery-1"),
                attempt.evidenceDirectory());
        for (Path privateDirectory : List.of(
                attempt.sessionDirectory(),
                attempt.inputDirectory(),
                attempt.evidenceDirectory().getParent(),
                attempt.evidenceDirectory())) {
            assertEquals(
                    PosixFilePermissions.fromString("rwx------"),
                    Files.getPosixFilePermissions(privateDirectory));
            assertEquals(Files.getOwner(runDirectory), Files.getOwner(privateDirectory));
        }
        try (ShipRunStore.LockedRun locked = new ShipRunStore(state).lock(run.id())) {
            assertEquals(run, locked.read());
        }
        assertTrue(Files.isRegularFile(runDirectory.resolve("run.lock")));
    }

    @Test
    void resumeRestartsCompletedValidateWhenItsArtifactChanges() throws Exception {
        Path project = Files.createDirectory(directory.resolve("project"));
        ShipController controller = controller("state");
        ShipRun run = controller.start(project, Oversight.ALWAYS, List.of());
        run = complete(controller, run, "discovery");
        run = complete(controller, run, "design");
        run = controller.resume(run.id());
        run = complete(controller, run, "plan");
        run = controller.resume(run.id());
        run = complete(controller, run, "execute");
        run = controller.resume(run.id());
        ShipController.StageAttempt attempt = controller.prepareAttempt(
                run.id());
        ShipLocalStamp stamp = localStampWithLogs(
                run.id(), attempt.evidenceDirectory());
        ShipLocalStampStore.write(
                attempt.evidenceDirectory(), run.id(), stamp);
        ShipRun paused = controller.completeValidationStage(
                run.id(),
                run.stage(Stage.VALIDATE).attempts(),
                run.stage(Stage.VALIDATE).inputDigest(),
                stamp);
        assertEquals(RunStatus.PAUSED, paused.status());

        Files.writeString(
                attempt.evidenceDirectory().resolve("stdout.log"),
                "changed after validation");
        ShipRun resumed = controller.resume(paused.id());

        assertEquals(Stage.VALIDATE, resumed.currentStage());
        assertEquals(StageStatus.RUNNING, resumed.stage(Stage.VALIDATE).status());
        assertEquals(2, resumed.stage(Stage.VALIDATE).attempts());
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
    void acceptsOnlyBoundWorkspaceArtifactsAndRevalidatesTheirMutation() throws Exception {
        Path project = Files.createDirectory(directory.resolve("project"));
        Path liveArtifact = Files.writeString(project.resolve("unbound.md"), "unbound");
        ShipController controller = controller("state");
        ShipRun run = controller.start(project, Oversight.NEVER, List.of());
        run = complete(controller, run, "discovery");
        run = complete(controller, run, "design");
        run = complete(controller, run, "plan");
        assertEquals(Stage.EXECUTE, run.currentStage());

        Path workspace = controller.prepareWorkspace(
                run.id(),
                run.stage(Stage.EXECUTE).attempts(),
                run.stage(Stage.EXECUTE).inputDigest());
        Path report = Files.writeString(
                workspace.resolve("execution-report.md"), "executed");
        Path route = Files.writeString(workspace.resolve("orders.camel.yaml"), "version one");
        ShipRun executing = run;

        assertFailure(
                "workspace-required",
                () -> controller.completeStage(
                        executing.id(),
                        Stage.EXECUTE,
                        executing.stage(Stage.EXECUTE).attempts(),
                        executing.stage(Stage.EXECUTE).inputDigest(),
                        digest("execute"),
                        List.of(),
                        false));
        assertFailure(
                "artifact-invalid",
                () -> controller.completeExecuteStage(
                        executing.id(),
                        executing.stage(Stage.EXECUTE).attempts(),
                        executing.stage(Stage.EXECUTE).inputDigest(),
                        List.of(liveArtifact),
                        false));
        assertFailure(
                "artifact-invalid",
                () -> controller.completeExecuteStage(
                        executing.id(),
                        executing.stage(Stage.EXECUTE).attempts(),
                        executing.stage(Stage.EXECUTE).inputDigest(),
                        List.of(workspace),
                        false));
        Path credential = Files.writeString(workspace.resolve(".env"), "TOKEN=secret");
        assertFailure(
                "artifact-invalid",
                () -> controller.completeExecuteStage(
                        executing.id(),
                        executing.stage(Stage.EXECUTE).attempts(),
                        executing.stage(Stage.EXECUTE).inputDigest(),
                        List.of(report),
                        false));
        Files.delete(credential);
        Files.createDirectories(workspace.resolve("target"));
        Files.writeString(workspace.resolve("target/build.log"), "volatile output");

        ShipRun validating = controller.completeExecuteStage(
                run.id(),
                run.stage(Stage.EXECUTE).attempts(),
                run.stage(Stage.EXECUTE).inputDigest(),
                List.of(report),
                false);
        ProjectSnapshot acceptedSnapshot = ProjectEvidenceFiles.captureStaged(workspace);
        assertEquals(Stage.VALIDATE, validating.currentStage());
        assertTrue(ShipDigest.isSha256(validating.stage(Stage.EXECUTE).outputDigest()));
        String executeOutputDigest = validating.stage(Stage.EXECUTE).outputDigest();
        String candidateDigest = validating.stage(Stage.EXECUTE).artifacts().stream()
                .filter(artifact -> artifact.path().equals(workspace.toString()))
                .findFirst()
                .orElseThrow()
                .digest();
        String reportDigest = validating.stage(Stage.EXECUTE).artifacts().stream()
                .filter(artifact -> artifact.path().equals(report.toString()))
                .findFirst()
                .orElseThrow()
                .digest();
        assertEquals(acceptedSnapshot.digest(), candidateDigest);
        assertEquals(
                acceptedSnapshot.files().get("execution-report.md").digest(),
                reportDigest);
        assertTrue(validating.stage(Stage.EXECUTE).artifacts().stream()
                .anyMatch(artifact -> artifact.path().equals(report.toString())));

        Files.writeString(route, "version two");
        ShipRun resumed = controller.resume(validating.id());

        assertEquals(Stage.VALIDATE, resumed.currentStage());
        assertEquals(StageStatus.COMPLETED, resumed.stage(Stage.EXECUTE).status());
        assertNotEquals(
                candidateDigest,
                resumed.stage(Stage.EXECUTE).artifacts().stream()
                        .filter(artifact -> artifact.path().equals(workspace.toString()))
                        .findFirst()
                        .orElseThrow()
                        .digest());
        assertNotEquals(
                executeOutputDigest,
                resumed.stage(Stage.EXECUTE).outputDigest());
        assertEquals(StageStatus.RUNNING, resumed.stage(Stage.VALIDATE).status());
        assertEquals(2, resumed.stage(Stage.VALIDATE).attempts());
    }

    @Test
    void rejectsPersistedProjectOverlapWithTheRunDirectory() throws Exception {
        Path project = Files.createDirectory(directory.resolve("project"));
        ShipController controller = controller("state");
        ShipRun run = controller.start(project, Oversight.NEVER, List.of());
        run = complete(controller, run, "discovery");
        run = complete(controller, run, "design");
        run = complete(controller, run, "plan");
        Path workspace = controller.prepareWorkspace(
                run.id(),
                run.stage(Stage.EXECUTE).attempts(),
                run.stage(Stage.EXECUTE).inputDigest());
        run = controller.completeExecuteStage(
                run.id(),
                run.stage(Stage.EXECUTE).attempts(),
                run.stage(Stage.EXECUTE).inputDigest(),
                List.of(),
                false);

        String runId = run.id();
        Path state = directory.resolve("state").resolve(runId).resolve("state.json");
        ObjectMapper json = new ObjectMapper();
        ObjectNode document = (ObjectNode) json.readTree(state.toFile());
        document.put("projectDirectory", workspace.toString());
        byte[] tampered = json.writeValueAsBytes(document);
        Files.write(state, tampered);

        assertFailure("state-corrupt", () -> controller.resume(runId));
        assertArrayEquals(tampered, Files.readAllBytes(state));
    }

    @Test
    void reportsAnExhaustedPersistedAttemptAsCorruptState() throws Exception {
        Path project = Files.createDirectory(directory.resolve("project"));
        ShipController controller = controller("state");
        ShipRun run = controller.start(project, Oversight.NEVER, List.of());
        Path state = directory.resolve("state").resolve(run.id()).resolve("state.json");
        ObjectMapper json = new ObjectMapper();
        ObjectNode document = (ObjectNode) json.readTree(state.toFile());
        ((ObjectNode) document.withArray("stages").get(Stage.DISCOVERY.ordinal()))
                .put("attempts", Integer.MAX_VALUE - 1);
        Files.write(state, json.writeValueAsBytes(document));

        assertFailure("state-corrupt", () -> controller.resume(run.id()));

        Path nextProject = Files.createDirectory(directory.resolve("next-project"));
        ShipController advancing = controller("next-state");
        ShipRun active = advancing.start(nextProject, Oversight.NEVER, List.of());
        Path nextState = directory.resolve("next-state")
                .resolve(active.id())
                .resolve("state.json");
        document = (ObjectNode) json.readTree(nextState.toFile());
        ((ObjectNode) document.withArray("stages").get(Stage.DESIGN.ordinal()))
                .put("attempts", Integer.MAX_VALUE - 1);
        Files.write(nextState, json.writeValueAsBytes(document));

        assertFailure(
                "state-corrupt",
                () -> advancing.completeStage(
                        active.id(),
                        Stage.DISCOVERY,
                        active.stage(Stage.DISCOVERY).attempts(),
                        active.stage(Stage.DISCOVERY).inputDigest(),
                        digest("discovery"),
                        List.of(),
                        false));
    }

    @Test
    void reportsAChangedWorkspaceBaselineAsStaleInput() throws Exception {
        Path project = Files.createDirectory(directory.resolve("project"));
        Files.writeString(project.resolve("README.md"), "baseline");
        ShipController controller = controller("state");
        ShipRun run = controller.start(project, Oversight.NEVER, List.of());
        run = complete(controller, run, "discovery");
        run = complete(controller, run, "design");
        run = complete(controller, run, "plan");
        Path workspace = controller.prepareWorkspace(
                run.id(),
                run.stage(Stage.EXECUTE).attempts(),
                run.stage(Stage.EXECUTE).inputDigest());
        Files.writeString(project.resolve("README.md"), "changed");
        ShipRun executing = run;

        assertFailure(
                "stale-stage-input",
                () -> controller.completeExecuteStage(
                        executing.id(),
                        executing.stage(Stage.EXECUTE).attempts(),
                        executing.stage(Stage.EXECUTE).inputDigest(),
                        List.of(),
                        false));
        assertEquals(executing, controller.status(executing.id()));

        ShipRun resumed = controller.resume(executing.id());
        assertEquals(2, resumed.stage(Stage.EXECUTE).attempts());
        Path refreshed = controller.prepareWorkspace(
                resumed.id(),
                resumed.stage(Stage.EXECUTE).attempts(),
                resumed.stage(Stage.EXECUTE).inputDigest());
        assertEquals(workspace, refreshed);
        assertEquals("changed", Files.readString(refreshed.resolve("README.md")));
    }

    @Test
    void rebindsTheWorkspaceWhenUpstreamInputRestartsExecute() throws Exception {
        Path project = Files.createDirectory(directory.resolve("project"));
        Path plan = Files.writeString(project.resolve("plan.md"), "plan one");
        ShipController controller = controller("state");
        ShipRun run = controller.start(project, Oversight.NEVER, List.of());
        run = complete(controller, run, "discovery");
        run = complete(controller, run, "design");
        run = complete(controller, run, "plan one", plan);
        Path workspace = controller.prepareWorkspace(
                run.id(),
                run.stage(Stage.EXECUTE).attempts(),
                run.stage(Stage.EXECUTE).inputDigest());
        Files.writeString(workspace.resolve("partial.txt"), "interrupted edit");

        Files.writeString(plan, "plan two");
        ShipRun executing = controller.resume(run.id());
        assertEquals(Stage.EXECUTE, executing.currentStage());
        assertEquals(2, executing.stage(Stage.EXECUTE).attempts());

        Path rebound = controller.prepareWorkspace(
                executing.id(),
                executing.stage(Stage.EXECUTE).attempts(),
                executing.stage(Stage.EXECUTE).inputDigest());
        assertEquals(workspace, rebound);
        assertFalse(Files.exists(rebound.resolve("partial.txt")));
        assertEquals("plan two", Files.readString(rebound.resolve("plan.md")));
    }

    @Test
    void requiresTheWorkspaceBindingToMatchTheActiveExecuteAttempt() throws Exception {
        Path project = Files.createDirectory(directory.resolve("project"));
        ShipController controller = controller("state");
        ShipRun run = controller.start(project, Oversight.NEVER, List.of());
        run = complete(controller, run, "discovery");
        run = complete(controller, run, "design");
        run = complete(controller, run, "plan");
        Path workspace = controller.prepareWorkspace(
                run.id(),
                run.stage(Stage.EXECUTE).attempts(),
                run.stage(Stage.EXECUTE).inputDigest());
        Files.writeString(workspace.resolve("partial.txt"), "preserve me");

        ShipRun resumed = controller.resume(run.id());
        assertEquals(2, resumed.stage(Stage.EXECUTE).attempts());
        assertFailure(
                "artifact-invalid",
                () -> controller.completeExecuteStage(
                        resumed.id(),
                        resumed.stage(Stage.EXECUTE).attempts(),
                        resumed.stage(Stage.EXECUTE).inputDigest(),
                        List.of(),
                        false));
        assertEquals(resumed, controller.status(resumed.id()));

        Path rebound = controller.prepareWorkspace(
                resumed.id(),
                resumed.stage(Stage.EXECUTE).attempts(),
                resumed.stage(Stage.EXECUTE).inputDigest());
        assertEquals(workspace, rebound);
        assertEquals("preserve me", Files.readString(rebound.resolve("partial.txt")));
        ShipRun validating = controller.completeExecuteStage(
                resumed.id(),
                resumed.stage(Stage.EXECUTE).attempts(),
                resumed.stage(Stage.EXECUTE).inputDigest(),
                List.of(),
                false);
        assertEquals(Stage.VALIDATE, validating.currentStage());
    }

    @Test
    void rejectsAnEmptyExecuteArtifactWithoutChangingTheRun() throws Exception {
        Path project = Files.createDirectory(directory.resolve("project"));
        ShipController controller = controller("state");
        ShipRun run = controller.start(project, Oversight.NEVER, List.of());
        run = complete(controller, run, "discovery");
        run = complete(controller, run, "design");
        run = complete(controller, run, "plan");
        Path workspace = controller.prepareWorkspace(
                run.id(),
                run.stage(Stage.EXECUTE).attempts(),
                run.stage(Stage.EXECUTE).inputDigest());
        Path empty = Files.createFile(workspace.resolve("execution-report.md"));
        ShipRun executing = run;

        assertFailure(
                "artifact-invalid",
                () -> controller.completeExecuteStage(
                        executing.id(),
                        executing.stage(Stage.EXECUTE).attempts(),
                        executing.stage(Stage.EXECUTE).inputDigest(),
                        List.of(empty),
                        false));
        assertEquals(executing, controller.status(executing.id()));
    }

    @Test
    void mapsAnUnsafeWorkspaceFilenameToATypedControllerFailure() throws Exception {
        Path project = Files.createDirectory(directory.resolve("project"));
        ShipController controller = controller("state");
        ShipRun run = controller.start(project, Oversight.NEVER, List.of());
        run = complete(controller, run, "discovery");
        run = complete(controller, run, "design");
        run = complete(controller, run, "plan");
        Path workspace = controller.prepareWorkspace(
                run.id(),
                run.stage(Stage.EXECUTE).attempts(),
                run.stage(Stage.EXECUTE).inputDigest());
        Files.writeString(workspace.resolve("line\nbreak"), "content");
        ShipRun executing = run;

        ShipController.Failure failure = assertThrows(
                ShipController.Failure.class,
                () -> controller.completeExecuteStage(
                        executing.id(),
                        executing.stage(Stage.EXECUTE).attempts(),
                        executing.stage(Stage.EXECUTE).inputDigest(),
                        List.of(),
                        false));

        assertEquals("artifact-invalid", failure.code());
        ShipFilesystemException cause = assertInstanceOf(
                ShipFilesystemException.class, failure.getCause());
        assertEquals(ShipFilesystemException.UNSAFE_ENTRY, cause.code());
        assertEquals(executing, controller.status(executing.id()));
    }

    @Test
    void rejectsInvalidMissingOversizedAndEmptyArtifacts() throws Exception {
        Path project = Files.createDirectory(directory.resolve("project"));
        Path outside = directory.resolve("outside");
        Path empty = Files.createFile(project.resolve("empty"));
        Path duplicate = Files.writeString(project.resolve("duplicate"), "duplicate");
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
        assertFailure("artifact-missing",
                () -> complete(controller, run, "missing", project.resolve("missing")));
        assertFailure("artifact-too-large",
                () -> complete(controller, run, "oversized", oversized));
        assertFailure("artifact-invalid", () -> complete(controller, run, "empty", empty));
        assertFailure("artifact-invalid",
                () -> complete(controller, run, "duplicate", duplicate, duplicate));

        List<Path> aggregate = new ArrayList<>();
        for (int index = 0; index < 17; index++) {
            Path artifact = project.resolve("aggregate-" + index);
            try (RandomAccessFile file = new RandomAccessFile(artifact.toFile(), "rw")) {
                file.setLength(64L * 1024 * 1024);
            }
            aggregate.add(artifact);
        }
        assertFailure(
                "artifact-too-large",
                () -> controller.completeStage(
                        run.id(),
                        run.currentStage(),
                        run.stage(run.currentStage()).attempts(),
                        run.stage(run.currentStage()).inputDigest(),
                        digest("aggregate"),
                        aggregate,
                        false));
        assertEquals(run, controller.status(run.id()));
    }

    @Test
    void rejectsAVolatileArtifactHardlinkedToDeniedContent() throws Exception {
        Path project = Files.createDirectory(directory.resolve("project"));
        Path credential = Files.writeString(project.resolve(".env"), "TOKEN=secret");
        Path target = Files.createDirectory(project.resolve("target"));
        Path alias = Files.createLink(target.resolve("validation.txt"), credential);
        ShipController controller = controller("state");
        ShipRun run = controller.start(project, Oversight.NEVER, List.of());

        assertFailure(
                "artifact-unreadable",
                () -> complete(controller, run, "validation", alias));
        assertEquals(run, controller.status(run.id()));
    }

    @Test
    void rejectsConcurrentArtifactSizeChangesAndReplacement() throws Exception {
        assertConcurrentArtifactMutationRejected("size-change", false);
        assertConcurrentArtifactMutationRejected("replacement", true);
    }

    @Test
    void rejectsProjectDependentMutationsButAllowsAbortAfterDeletion() throws Exception {
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
        assertEquals(RunStatus.ABORTED, controller.abort(run.id()).status());
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
        ShipController controller = controller("state");

        ShipRun run = controller.startFrom(
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

        Files.writeString(plan, "plan two");
        ShipRun resumed = controller.resume(run.id());

        assertEquals(Stage.EXECUTE, resumed.currentStage());
        assertEquals(2, resumed.stage(Stage.EXECUTE).attempts());
        assertEquals(
                ShipDigest.sha256(Files.readAllBytes(plan)),
                resumed.stage(Stage.PLAN).outputDigest());
    }

    @Test
    void startFromPlanRefreshesImportedDesignOutputBeforeRetry() throws Exception {
        Path project = Files.createDirectory(directory.resolve("project"));
        Path metadata = Files.createDirectories(project.resolve(".camel-kit"));
        Files.writeString(
                metadata.resolve("pipeline.json"),
                "{\"mode\":\"manual\",\"activePipeline\":\"149-import\"}");
        Path documents = Files.createDirectories(project.resolve("docs/camel-kit/149-import"));
        Path design = Files.writeString(documents.resolve("design-spec.md"), "design");
        ShipController controller = controller("state");
        ShipRun run = controller.startFrom(
                project,
                Stage.PLAN,
                Oversight.NEVER,
                List.of(new ShipContext.TextInput("build the route")));

        Files.writeString(design, "design two");
        ShipRun resumed = controller.resume(run.id());

        assertEquals(Stage.PLAN, resumed.currentStage());
        assertEquals(2, resumed.stage(Stage.PLAN).attempts());
        assertEquals(
                ShipDigest.sha256(Files.readAllBytes(design)),
                resumed.stage(Stage.DESIGN).outputDigest());
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
    void startFromDesignRequiresAnActivePipeline() throws Exception {
        Path project = Files.createDirectory(directory.resolve("project"));

        ShipController.Failure failure = assertThrows(
                ShipController.Failure.class,
                () -> controller("state").startFrom(
                        project,
                        Stage.DESIGN,
                        Oversight.NEVER,
                        List.of(new ShipContext.TextInput("design the route"))));

        assertEquals("start-from-pipeline-missing", failure.code());
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
        Path executionReport = Files.writeString(
                documents.resolve("execution-report.md"), "execution");
        Path route = Files.writeString(project.resolve("route.yaml"), "version one");
        ProjectSnapshot importedSnapshot = ProjectEvidenceFiles.capture(project);
        ShipController controller = controller("state");

        ShipRun validating = controller.startFrom(
                project,
                Stage.VALIDATE,
                Oversight.ALWAYS,
                List.of(new ShipContext.TextInput("build the route")));
        assertEquals(2, validating.stage(Stage.EXECUTE).artifacts().size());
        assertEquals(project.toAbsolutePath().normalize().toString(),
                validating.stage(Stage.EXECUTE).artifacts().get(1).path());
        assertEquals(
                importedSnapshot.files()
                        .get("docs/camel-kit/149-local-controller/execution-report.md")
                        .digest(),
                validating.stage(Stage.EXECUTE).artifacts().get(0).digest());
        assertEquals(
                importedSnapshot.digest(),
                validating.stage(Stage.EXECUTE).artifacts().get(1).digest());
        String snapshotDigest = validating.stage(Stage.EXECUTE).artifacts().get(1).digest();
        String executeOutputDigest = validating.stage(Stage.EXECUTE).outputDigest();

        ShipController.StageAttempt attempt = controller.prepareAttempt(
                validating.id());
        ShipLocalStamp stamp = localStamp(validating.id(), Outcome.PASS);
        ShipLocalStampStore.write(
                attempt.evidenceDirectory(), validating.id(), stamp);
        ShipRun paused = controller.completeValidationStage(
                validating.id(),
                validating.stage(Stage.VALIDATE).attempts(),
                validating.stage(Stage.VALIDATE).inputDigest(),
                stamp);
        assertEquals(RunStatus.PAUSED, paused.status());
        Files.writeString(route, "version two");
        Files.writeString(executionReport, "execution two");
        ProjectSnapshot changedSnapshot = ProjectEvidenceFiles.capture(project);

        ShipRun resumed = controller.resume(paused.id());
        assertEquals(Stage.VALIDATE, resumed.currentStage());
        assertEquals(StageStatus.RUNNING, resumed.stage(Stage.VALIDATE).status());
        assertEquals(2, resumed.stage(Stage.VALIDATE).attempts());
        assertNotEquals(
                snapshotDigest, resumed.stage(Stage.EXECUTE).artifacts().get(1).digest());
        assertNotEquals(
                executeOutputDigest, resumed.stage(Stage.EXECUTE).outputDigest());
        assertEquals(
                changedSnapshot.files()
                        .get("docs/camel-kit/149-local-controller/execution-report.md")
                        .digest(),
                resumed.stage(Stage.EXECUTE).artifacts().get(0).digest());
        assertEquals(
                changedSnapshot.digest(),
                resumed.stage(Stage.EXECUTE).artifacts().get(1).digest());
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
    void rejectsFinalAndIntermediateArtifactSymlinksInsideTheProject() throws Exception {
        Path project = Files.createDirectory(directory.resolve("project"));
        Path realDirectory = Files.createDirectory(project.resolve("real"));
        Path target = Files.writeString(realDirectory.resolve("result.md"), "result");
        Path finalLink = Files.createSymbolicLink(project.resolve("result-link.md"), target);
        Path directoryLink = Files.createSymbolicLink(project.resolve("directory-link"), realDirectory);
        ShipController controller = controller("state");
        ShipRun run = controller.start(project, Oversight.NEVER, List.of());

        assertFailure(
                "artifact-invalid",
                () -> complete(controller, run, "final-link", finalLink));
        assertFailure(
                "artifact-invalid",
                () -> complete(
                        controller,
                        run,
                        "directory-link",
                        directoryLink.resolve("result.md")));
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

    private ShipRun advanceToValidation(
            ShipController controller, Path project, Oversight oversight) {
        ShipRun run = controller.start(project, oversight, List.of());
        while (run.currentStage() != Stage.VALIDATE) {
            if (run.status() == RunStatus.PAUSED) {
                run = controller.resume(run.id());
            }
            run = complete(
                    controller,
                    run,
                    run.currentStage().name().toLowerCase(Locale.ROOT));
        }
        return run.status() == RunStatus.PAUSED
                ? controller.resume(run.id())
                : run;
    }

    private static ShipLocalStamp localStamp(String runId, Outcome outcome) {
        return ShipLocalStamp.create(
                runId,
                List.of(new ShipLocalStamp.ToolVersion(
                        "pi",
                        null,
                        "0.80.6",
                        ShipLocalStamp.Support.SUPPORTED,
                        null)),
                List.of(new ShipLocalStamp.Check(
                        "artifact-policy",
                        true,
                        outcome,
                        "Artifact policy result",
                        null,
                        null)),
                Instant.parse("2026-07-29T12:00:00Z"));
    }

    private static ShipLocalStamp localStampWithLogs(
            String runId, Path evidenceDirectory)
            throws Exception {
        Path stdout = Files.writeString(
                evidenceDirectory.resolve("stdout.log"), "stdout");
        Path stderr = Files.writeString(
                evidenceDirectory.resolve("stderr.log"), "stderr");
        Files.setPosixFilePermissions(
                stdout, PosixFilePermissions.fromString("rw-------"));
        Files.setPosixFilePermissions(
                stderr, PosixFilePermissions.fromString("rw-------"));
        ShipLocalStamp.CommandRun command = new ShipLocalStamp.CommandRun(
                "/usr/bin/java",
                "17",
                List.of("-version"),
                evidenceDirectory.toString(),
                List.of(digest("input")),
                true,
                false,
                false,
                0,
                "2026-07-29T11:59:58Z",
                "2026-07-29T11:59:59Z",
                stdout.toString(),
                ShipDigest.sha256(Files.readAllBytes(stdout)),
                stderr.toString(),
                ShipDigest.sha256(Files.readAllBytes(stderr)));
        return ShipLocalStamp.create(
                runId,
                List.of(new ShipLocalStamp.ToolVersion(
                        "pi",
                        null,
                        "0.80.6",
                        ShipLocalStamp.Support.SUPPORTED,
                        null)),
                List.of(new ShipLocalStamp.Check(
                        "artifact-policy",
                        true,
                        Outcome.PASS,
                        "Artifact policy result",
                        null,
                        command)),
                Instant.parse("2026-07-29T12:00:00Z"));
    }

    private void assertConcurrentArtifactMutationRejected(String name, boolean replace)
            throws Exception {
        Path project = Files.createDirectory(directory.resolve(name + "-project"));
        Path target = Files.createDirectory(project.resolve("target"));
        Path artifact = target.resolve("validation.txt");
        try (RandomAccessFile file = new RandomAccessFile(artifact.toFile(), "rw")) {
            file.setLength(32L * 1024 * 1024);
        }
        Path replacement = directory.resolve(name + "-replacement");
        ShipController controller = controller(name + "-state");
        ShipRun run = controller.start(project, Oversight.NEVER, List.of());
        AtomicBoolean stop = new AtomicBoolean();
        AtomicReference<Throwable> mutationFailure = new AtomicReference<>();
        CountDownLatch started = new CountDownLatch(1);
        Thread mutator = new Thread(() -> {
            try {
                if (replace) {
                    long size = 48L * 1024 * 1024;
                    while (!stop.get()) {
                        try (RandomAccessFile file = new RandomAccessFile(replacement.toFile(), "rw")) {
                            file.setLength(size);
                        }
                        Files.move(
                                replacement,
                                artifact,
                                StandardCopyOption.ATOMIC_MOVE,
                                StandardCopyOption.REPLACE_EXISTING);
                        started.countDown();
                        size = size == 48L * 1024 * 1024
                                ? 32L * 1024 * 1024
                                : 48L * 1024 * 1024;
                    }
                } else {
                    try (RandomAccessFile file = new RandomAccessFile(artifact.toFile(), "rw")) {
                        file.setLength(48L * 1024 * 1024);
                        started.countDown();
                        long size = 32L * 1024 * 1024;
                        while (!stop.get()) {
                            file.setLength(size);
                            size = size == 32L * 1024 * 1024
                                    ? 48L * 1024 * 1024
                                    : 32L * 1024 * 1024;
                        }
                    }
                }
            } catch (Throwable failure) {
                mutationFailure.set(failure);
                started.countDown();
            } finally {
                try {
                    Files.deleteIfExists(replacement);
                } catch (Throwable cleanupFailure) {
                    mutationFailure.compareAndSet(null, cleanupFailure);
                }
            }
        }, "ship-artifact-mutator");
        mutator.setDaemon(true);
        mutator.start();
        assertTrue(started.await(5, TimeUnit.SECONDS));

        ShipController.Failure failure;
        try {
            failure = assertThrows(
                    ShipController.Failure.class,
                    () -> complete(controller, run, "validation", artifact));
        } finally {
            stop.set(true);
            mutator.join(TimeUnit.SECONDS.toMillis(5));
        }

        assertFalse(mutator.isAlive());
        assertNull(mutationFailure.get());
        assertEquals("artifact-unreadable", failure.code());
        assertEquals(run, controller.status(run.id()));
    }

    private ShipRun complete(
            ShipController controller, ShipRun run, String result, Path... artifacts) {
        Stage stage = run.currentStage();
        if (stage == Stage.EXECUTE) {
            controller.prepareWorkspace(
                    run.id(),
                    run.stage(stage).attempts(),
                    run.stage(stage).inputDigest());
            return controller.completeExecuteStage(
                    run.id(),
                    run.stage(stage).attempts(),
                    run.stage(stage).inputDigest(),
                    List.of(artifacts),
                    false);
        }
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
