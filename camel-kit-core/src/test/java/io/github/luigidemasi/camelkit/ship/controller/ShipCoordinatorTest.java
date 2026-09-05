package io.github.luigidemasi.camelkit.ship.controller;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.github.luigidemasi.camelkit.config.DistributionConfig;
import io.github.luigidemasi.camelkit.ship.ShipDigest;
import io.github.luigidemasi.camelkit.ship.artifact.ArtifactManifest;
import io.github.luigidemasi.camelkit.ship.artifact.ArtifactManifest.DeclaredArtifact;
import io.github.luigidemasi.camelkit.ship.artifact.ArtifactManifest.JavaPolicy;
import io.github.luigidemasi.camelkit.ship.artifact.ArtifactManifest.RouteArtifact;
import io.github.luigidemasi.camelkit.ship.artifact.ArtifactManifest.TestArtifact;
import io.github.luigidemasi.camelkit.ship.artifact.ArtifactPolicy;
import io.github.luigidemasi.camelkit.ship.artifact.ArtifactPolicy.RouteContract;
import io.github.luigidemasi.camelkit.ship.artifact.CitrusDependencyPolicy;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogTestVerifier;
import io.github.luigidemasi.camelkit.ship.catalog.ShipCatalogService;
import io.github.luigidemasi.camelkit.ship.context.ShipContext;
import io.github.luigidemasi.camelkit.ship.controller.ShipController.StageAttempt;
import io.github.luigidemasi.camelkit.ship.controller.ShipRun.Oversight;
import io.github.luigidemasi.camelkit.ship.controller.ShipRun.RunStatus;
import io.github.luigidemasi.camelkit.ship.controller.ShipRun.Stage;
import io.github.luigidemasi.camelkit.ship.controller.ShipRun.StageStatus;
import io.github.luigidemasi.camelkit.ship.evidence.CommandEvidence;
import io.github.luigidemasi.camelkit.ship.evidence.EvidenceCommand;
import io.github.luigidemasi.camelkit.ship.evidence.ShipLocalStamp;
import io.github.luigidemasi.camelkit.ship.evidence.ShipLocalStampStore;
import io.github.luigidemasi.camelkit.ship.worker.PiWorker;
import io.github.luigidemasi.camelkit.ship.worker.PiWorker.Outcome;
import io.github.luigidemasi.camelkit.ship.worker.PiWorker.Request;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledOnOs(OS.LINUX)
class ShipCoordinatorTest {

    @TempDir
    Path directory;

    private Path fixture;
    private Path nodeExecutable;
    private Path project;
    private Path state;
    private ShipController controller;
    private PiWorker worker;
    private ShipCoordinator coordinator;
    private DistributionConfig distribution;

    @BeforeEach
    void prepareFixture() throws Exception {
        fixture = Files.createDirectory(directory.resolve("fake-pi"));
        try (var script = Objects.requireNonNull(
                ShipCoordinatorTest.class.getResourceAsStream(
                        "/io/github/luigidemasi/camelkit/ship/worker/fake-pi-rpc.sh"))) {
            writeExecutable(
                    fixture.resolve("pi-rpc"),
                    new String(script.readAllBytes(), StandardCharsets.UTF_8));
        }
        nodeExecutable = writeExecutable(
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
        Files.writeString(fixture.resolve("version"), "0.84.2\n");
        Files.writeString(fixture.resolve("mode"), "success\n");
        writeDesignResult();

        project = Files.createDirectory(directory.resolve("project"));
        state = directory.resolve("state");
        Map<String, String> environment = Map.of();
        distribution = DistributionConfig.load(new Properties());
        controller = new ShipController(
                state,
                Clock.systemUTC(),
                environment);
        worker = new PiWorker(
                fixture.resolve("pi-rpc"),
                List.of(distribution.piVersion()),
                nodeExecutable,
                distribution.nodeVersion(),
                Duration.ofSeconds(30),
                environment);
        coordinator = new ShipCoordinator(
                state,
                controller,
                worker,
                new ShipCatalogService(directory.resolve("m2"))::snapshot,
                new ShipMainValidator(),
                distribution,
                Map.of(),
                true,
                Clock.systemUTC());
    }

    @Test
    void recordsUnansweredQuestionsUnderEveryOversightPolicyWithoutChangingPauses() throws Exception {
        Path metadata = Files.createDirectories(project.resolve(".camel-kit"));
        Files.writeString(metadata.resolve("pipeline.json"),
                "{\"mode\":\"manual\",\"activePipeline\":\"149-coordinator\"}\n");
        List<ShipRun.UnansweredQuestion> questions = List.of(
                new ShipRun.UnansweredQuestion("Which retry limit?", "Three attempts"),
                new ShipRun.UnansweredQuestion("May the source be retired?", null));
        for (Oversight oversight : Oversight.values()) {
            writeAmbiguousDesignResult(questions);
            ShipRun run = controller.startFrom(project, Stage.DESIGN, oversight,
                    List.of(new ShipContext.TextInput("Design the migration")));
            ShipRun result = coordinator.run(run.id());

            assertTrue(result.stage(Stage.DESIGN).materialAmbiguity());
            assertEquals(questions, result.stage(Stage.DESIGN).unansweredQuestions());
            assertEquals(result, new ShipController(state).status(run.id()));
            assertEquals(Stage.PLAN, result.currentStage());
            if (oversight == Oversight.NEVER) {
                // The fixed DESIGN fixture is invalid for PLAN: reaching its failed attempt proves continuation.
                assertEquals(RunStatus.FAILED, result.status());
                assertEquals(1, result.stage(Stage.PLAN).attempts());
            } else {
                assertEquals(RunStatus.PAUSED, result.status());
                assertEquals(0, result.stage(Stage.PLAN).attempts());
            }
            String prompt = Files.readString(fixture.resolve("prompt"));
            assertTrue(prompt.contains("The active oversight policy is "
                                       + oversight.name().toLowerCase(Locale.ROOT)));
            assertTrue(prompt.contains("unansweredQuestions"));
            if (oversight == Oversight.NEVER) {
                assertTrue(prompt.contains("record each default in defaultApplied"));
                assertTrue(prompt
                        .contains("do not produce output or perform work that depends on that unresolved answer"));
            }
        }
    }

    @Test
    void recoversStructuredQuestionAuditFromTheDurableWorkerResult() throws Exception {
        List<ShipRun.UnansweredQuestion> questions = List.of(
                new ShipRun.UnansweredQuestion("Which retry limit?", null));
        writeAmbiguousDesignResult(questions);
        ShipRun run = designRun(List.of(new ShipContext.TextInput("Recover the migration design")));
        seedDurableResult(run);
        Files.writeString(fixture.resolve("mode"), "nonzero\n");

        ShipRun recovered = coordinator.run(run.id());

        assertDesignPaused(recovered, 1);
        assertTrue(recovered.stage(Stage.DESIGN).materialAmbiguity());
        assertEquals(questions, recovered.stage(Stage.DESIGN).unansweredQuestions());
        assertEquals(recovered, new ShipController(state).status(run.id()));
        assertEquals(List.of(sessionId(run)), Files.readAllLines(fixture.resolve("session-ids")));
    }

    @Test
    void configuredOverridesCannotPromoteUnmaintainedWorkerTools()
            throws Exception {
        Properties overrides = new Properties();
        overrides.setProperty("pi.version", "0.81.1");
        overrides.setProperty("node.version", "23.0.0");
        ShipCoordinator publicCoordinator = new ShipCoordinator(
                state,
                fixture.resolve("pi-rpc"),
                nodeExecutable,
                Files.createDirectory(directory.resolve("override-m2")),
                DistributionConfig.load(overrides),
                Duration.ofSeconds(5),
                false);

        Files.writeString(fixture.resolve("version"), "0.81.1\n");
        ShipRun unmaintainedPi = controller.start(
                project,
                Oversight.NEVER,
                List.of(new ShipContext.TextInput("unmaintained Pi")));
        ShipRun piFailed = publicCoordinator.run(unmaintainedPi.id());

        assertEquals(RunStatus.FAILED, piFailed.status());
        assertTrue(piFailed.message().contains(
                "Pi 0.81.1 is unverified; install maintained Pi 0.84.2"));
        assertFalse(Files.exists(fixture.resolve("args")));

        Files.writeString(fixture.resolve("version"), "0.84.2\n");
        Files.writeString(fixture.resolve("node-version"), "v23.0.0\n");
        ShipRun unmaintainedNode = controller.start(
                project,
                Oversight.NEVER,
                List.of(new ShipContext.TextInput("unmaintained Node")));
        ShipRun nodeFailed = publicCoordinator.run(unmaintainedNode.id());

        assertEquals(RunStatus.FAILED, nodeFailed.status());
        assertTrue(nodeFailed.message().contains(
                "Node 23.0.0 is unverified; install maintained Node 22.22.2"));
        assertFalse(Files.exists(fixture.resolve("args")));
    }

    @Test
    void runRecoversTheExactDurableAttemptBeforeRelaunchingPi()
            throws Exception {
        ShipRun run = designRun(List.of(
                new ShipContext.TextInput("recover this result")));
        seedDurableResult(run);
        Files.writeString(fixture.resolve("mode"), "nonzero\n");

        ShipRun completed = coordinator.run(run.id());

        assertDesignPaused(completed, 1);
        assertEquals(
                List.of(sessionId(run)),
                Files.readAllLines(fixture.resolve("session-ids")));
    }

    @Test
    void resumeRecoversTheExactDurableAttemptBeforeIncrementingIt()
            throws Exception {
        ShipRun run = designRun(List.of(
                new ShipContext.TextInput("recover before resume")));
        seedDurableResult(run);
        Files.writeString(fixture.resolve("mode"), "nonzero\n");

        ShipRun completed = coordinator.resume(run.id(), List.of());

        assertDesignPaused(completed, 1);
        assertEquals(
                List.of(sessionId(run)),
                Files.readAllLines(fixture.resolve("session-ids")));
        assertFalse(Files.exists(fixture.resolve("resumed-sessions")));
    }

    @Test
    void recoveredDurableNulFailureUsesFallbackWithoutWedging()
            throws Exception {
        ShipRun run = designRun(List.of(
                new ShipContext.TextInput("recover failed result")));
        Files.writeString(fixture.resolve("mode"), "stop-error\n");
        Path marker = seedDurableResult(run, Outcome.FAILED);
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode document = (ObjectNode) mapper.readTree(marker.toFile());
        ((ObjectNode) document.path("result"))
                .put("failure", String.valueOf('\0'));
        mapper.writerWithDefaultPrettyPrinter()
                .writeValue(marker.toFile(), document);

        ShipRun failed = coordinator.run(run.id());

        assertEquals(RunStatus.FAILED, failed.status());
        assertEquals(StageStatus.FAILED,
                failed.stage(Stage.DESIGN).status());
        assertEquals(1, failed.stage(Stage.DESIGN).attempts());
        assertEquals("Ship stage failed", failed.message());
        assertEquals(failed, controller.status(run.id()));
        assertEquals(failed, coordinator.run(run.id()));
    }

    @Test
    void resumeOnAnAlreadyInterruptedThreadDoesNotStartAnotherAttempt()
            throws Exception {
        ShipRun run = designRun(List.of(
                new ShipContext.TextInput("do not resume while interrupted")));

        try {
            Thread.currentThread().interrupt();

            assertThrows(
                    InterruptedException.class,
                    () -> coordinator.resume(run.id(), List.of()));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }

        assertEquals(run, controller.status(run.id()));
        assertFalse(Files.exists(fixture.resolve("session-ids")));
        assertFalse(Files.exists(
                state.resolve(run.id()).resolve("evidence/design-2")));
    }

    @Test
    void interruptedResumeLeavesADurableResultForTheNextInvocation()
            throws Exception {
        ShipRun run = designRun(List.of(
                new ShipContext.TextInput("preserve durable result")));
        seedDurableResult(run);

        try {
            Thread.currentThread().interrupt();
            assertThrows(
                    InterruptedException.class,
                    () -> coordinator.resume(run.id(), List.of()));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
        assertEquals(run, controller.status(run.id()));

        Files.writeString(fixture.resolve("mode"), "nonzero\n");
        ShipRun completed = coordinator.resume(run.id(), List.of());

        assertDesignPaused(completed, 1);
        assertEquals(
                List.of(sessionId(run)),
                Files.readAllLines(fixture.resolve("session-ids")));
    }

    @Test
    void unavailableGeneratedPredecessorRestartsBeforeTheActiveStage()
            throws Exception {
        for (String condition : List.of("missing", "corrupt")) {
            Files.deleteIfExists(fixture.resolve("session-ids"));
            Files.deleteIfExists(fixture.resolve("resumed-sessions"));
            ShipRun run = designRun(List.of(
                    new ShipContext.TextInput(
                            "restart " + condition + " predecessor")));
            ShipRun planned = coordinator.run(run.id());
            ShipRun activePlan = controller.resume(run.id());
            Path marker = state.resolve(run.id())
                    .resolve("sessions/.camel-kit-results")
                    .resolve(run.id() + "-design-"
                             + planned.stage(Stage.DESIGN).inputDigest()
                                     .substring("sha256:".length())
                             + "-1.json");
            assertTrue(Files.isRegularFile(marker));
            if ("missing".equals(condition)) {
                Files.delete(marker);
            } else {
                Files.writeString(marker, "{}\n");
            }

            ShipRun restarted = coordinator.resume(activePlan.id(), List.of());

            assertDesignPaused(restarted, 2);
            assertEquals(StageStatus.PENDING,
                    restarted.stage(Stage.PLAN).status());
            assertEquals(1, restarted.stage(Stage.PLAN).attempts());
            String sessionId = sessionId(run);
            assertEquals(
                    List.of(sessionId, sessionId),
                    Files.readAllLines(fixture.resolve("session-ids")));
            assertEquals(
                    List.of(sessionId),
                    Files.readAllLines(fixture.resolve("resumed-sessions")));
        }
    }

    @Test
    void unreadableGeneratedPredecessorDoesNotResetTheRun()
            throws Exception {
        ShipRun run = designRun(List.of(
                new ShipContext.TextInput("retry unreadable predecessor")));
        ShipRun planned = coordinator.run(run.id());
        ShipRun activePlan = controller.resume(run.id());
        Path marker = state.resolve(run.id())
                .resolve("sessions/.camel-kit-results")
                .resolve(run.id() + "-design-"
                         + planned.stage(Stage.DESIGN).inputDigest()
                                 .substring("sha256:".length())
                         + "-1.json");
        Files.setPosixFilePermissions(
                marker, PosixFilePermissions.fromString("---------"));

        try {
            IOException failure = assertThrows(
                    IOException.class,
                    () -> coordinator.resume(activePlan.id(), List.of()));

            assertFalse(failure instanceof PiWorker.UntrustedResultException);
            assertEquals(activePlan, controller.status(run.id()));
        } finally {
            Files.setPosixFilePermissions(
                    marker, PosixFilePermissions.fromString("rw-------"));
        }
    }

    @Test
    void interruptionLeavesTheAttemptRunningAndResumeReusesItsSession()
            throws Exception {
        ShipRun run = designRun(List.of(
                new ShipContext.TextInput("resume interrupted work")));
        Files.writeString(fixture.resolve("mode"), "block\n");
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicBoolean interrupted = new AtomicBoolean();
        Thread invocation = new Thread(() -> {
            try {
                coordinator.run(run.id());
                failure.set(new AssertionError(
                        "Ship coordinator unexpectedly completed"));
            } catch (InterruptedException expected) {
                interrupted.set(Thread.currentThread().isInterrupted());
            } catch (Throwable unexpected) {
                failure.set(unexpected);
            }
        });

        invocation.start();
        awaitFile(fixture.resolve("ready"));
        invocation.interrupt();
        invocation.join(Duration.ofSeconds(10).toMillis());

        assertFalse(invocation.isAlive());
        assertNull(failure.get());
        assertTrue(interrupted.get());
        ShipRun stopped = controller.status(run.id());
        assertEquals(RunStatus.RUNNING, stopped.status());
        assertEquals(Stage.DESIGN, stopped.currentStage());
        assertEquals(StageStatus.RUNNING, stopped.stage(Stage.DESIGN).status());
        assertEquals(1, stopped.stage(Stage.DESIGN).attempts());
        assertFixtureProcessesGone();

        Files.writeString(fixture.resolve("mode"), "success\n");
        ShipRun completed = coordinator.resume(run.id(), List.of());

        assertDesignPaused(completed, 2);
        String sessionId = sessionId(run);
        assertEquals(
                List.of(sessionId, sessionId),
                Files.readAllLines(fixture.resolve("session-ids")));
        assertEquals(
                List.of(sessionId),
                Files.readAllLines(fixture.resolve("resumed-sessions")));
    }

    @Test
    void abortWatcherSurvivesTransientReadAndReapsActiveWorker()
            throws Exception {
        ShipRun run = designRun(List.of(
                new ShipContext.TextInput("abort active work")));
        Files.writeString(fixture.resolve("mode"), "block\n");
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicBoolean interrupted = new AtomicBoolean();
        Thread invocation = new Thread(() -> {
            try {
                coordinator.run(run.id());
                failure.set(new AssertionError(
                        "Ship coordinator unexpectedly completed"));
            } catch (InterruptedException expected) {
                interrupted.set(Thread.currentThread().isInterrupted());
            } catch (Throwable unexpected) {
                failure.set(unexpected);
            }
        });

        invocation.start();
        try {
            awaitFile(fixture.resolve("ready"));
            Thread watcher = awaitThread(
                    "camel-kit-ship-abort-" + run.id(),
                    Thread.State.TIMED_WAITING);
            Path stateFile = state.resolve(run.id()).resolve("state.json");
            Path unavailable = stateFile.resolveSibling(
                    "state.json.transient");
            Files.move(
                    stateFile, unavailable, StandardCopyOption.ATOMIC_MOVE);
            try {
                watcher.join(Duration.ofSeconds(1).toMillis());
                assertTrue(watcher.isAlive());
            } finally {
                Files.move(
                        unavailable,
                        stateFile,
                        StandardCopyOption.ATOMIC_MOVE);
            }
            ShipRun aborted = new ShipController(
                    state,
                    Clock.systemUTC(),
                    Map.of())
                    .abort(run.id());
            invocation.join(Duration.ofSeconds(10).toMillis());

            assertFalse(invocation.isAlive());
            assertNull(failure.get());
            assertTrue(interrupted.get());
            assertEquals(aborted, controller.status(run.id()));
            assertEquals(RunStatus.ABORTED, aborted.status());
            assertEquals(StageStatus.ABORTED,
                    aborted.stage(Stage.DESIGN).status());
            assertEquals(
                    "{\"id\":\"abort-1\",\"type\":\"abort\"}",
                    Files.readString(fixture.resolve("abort")).trim());
            assertFixtureProcessesGone();

            assertEquals(aborted, coordinator.run(run.id()));
            ShipController.Failure cannotResume = assertThrows(
                    ShipController.Failure.class,
                    () -> coordinator.resume(run.id(), List.of()));
            assertEquals("run-aborted", cannotResume.code());
            assertFalse(Thread.currentThread().isInterrupted());
        } finally {
            if (invocation.isAlive()) {
                invocation.interrupt();
                invocation.join(Duration.ofSeconds(10).toMillis());
            }
        }
    }

    @Test
    void busyPiSessionLeavesTheAttemptRunningWithoutProgress()
            throws Exception {
        ShipRun run = designRun(List.of(
                new ShipContext.TextInput("busy session")));
        StageAttempt attempt = controller.prepareAttempt(run.id());
        Path lock = attempt.sessionDirectory()
                .resolve("." + sessionId(run) + ".lock");

        try (FileChannel channel = FileChannel.open(
                lock,
                Set.of(StandardOpenOption.CREATE, StandardOpenOption.WRITE),
                PosixFilePermissions.asFileAttribute(
                        PosixFilePermissions.fromString("rw-------")));
             FileLock ignored = channel.lock()) {
            ShipRun blocked = coordinator.run(run.id());

            assertEquals(run, blocked);
            assertEquals(run, controller.status(run.id()));
            assertFalse(Files.exists(fixture.resolve("session-ids")));
            assertFalse(Files.exists(fixture.resolve("args")));
        }
    }

    @Test
    void redactsSensitivePiFailureBeforeWritingRunState()
            throws Exception {
        String secret = "Pi assistant settled with stop reason error";
        Files.writeString(fixture.resolve("mode"), "stop-error\n");
        ShipRun run = designRun(List.of(
                new ShipContext.TextInput("redact failure")));
        ShipCoordinator screened = new ShipCoordinator(
                state,
                controller,
                worker,
                new ShipCatalogService(directory.resolve("m2"))::snapshot,
                new ShipMainValidator(),
                distribution,
                Map.of("API_TOKEN", secret),
                true,
                Clock.systemUTC());

        ShipRun failed = screened.run(run.id());

        assertEquals(RunStatus.FAILED, failed.status());
        assertEquals("Failed", failed.message());
        assertFalse(Files.readString(
                state.resolve(run.id()).resolve("state.json"))
                .contains(secret));
    }

    @Test
    void busyCoordinatorLeaseMakesRunAndResumeReadOnly()
            throws Exception {
        ShipRun run = designRun(List.of(
                new ShipContext.TextInput("busy coordinator")));
        Path lock = state.resolve(run.id()).resolve(".coordinator.lock");

        try (FileChannel channel = FileChannel.open(
                lock,
                Set.of(StandardOpenOption.CREATE, StandardOpenOption.WRITE),
                PosixFilePermissions.asFileAttribute(
                        PosixFilePermissions.fromString("rw-------")));
             FileLock ignored = channel.lock()) {
            assertEquals(run, coordinator.run(run.id()));
            assertEquals(run, coordinator.resume(run.id(), List.of()));
            IOException rejected = assertThrows(
                    IOException.class,
                    () -> coordinator.resume(
                            run.id(),
                            List.of(new ShipContext.TextInput("must not be dropped"))));
            assertEquals(
                    "Ship run is already active; context was not added",
                    rejected.getMessage());
            assertEquals(run, controller.status(run.id()));
            assertFalse(Files.exists(fixture.resolve("session-ids")));
        }
    }

    @Test
    void resumeAfterInterruptionRefreshesStaleDocumentAndRelaunchesPi()
            throws Exception {
        Path document = Files.writeString(
                project.resolve("requirements.md"), "original requirements");
        ShipRun run = controller.start(
                project,
                Oversight.ALWAYS,
                List.of(new ShipContext.DocumentInput(
                        document.getFileName())));
        Files.writeString(fixture.resolve("mode"), "block\n");
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicBoolean interrupted = new AtomicBoolean();
        Thread invocation = new Thread(() -> {
            try {
                coordinator.run(run.id());
                failure.set(new AssertionError(
                        "Ship coordinator unexpectedly completed"));
            } catch (InterruptedException expected) {
                interrupted.set(Thread.currentThread().isInterrupted());
            } catch (Throwable unexpected) {
                failure.set(unexpected);
            }
        });

        invocation.start();
        awaitFile(fixture.resolve("ready"));
        invocation.interrupt();
        invocation.join(Duration.ofSeconds(10).toMillis());

        assertFalse(invocation.isAlive());
        assertNull(failure.get());
        assertTrue(interrupted.get());
        ShipRun stopped = controller.status(run.id());
        assertEquals(RunStatus.RUNNING, stopped.status());
        assertEquals(1, stopped.stage(Stage.DISCOVERY).attempts());
        assertFixtureProcessesGone();

        Files.writeString(document, "changed requirements");
        Files.writeString(fixture.resolve("mode"), "success\n");
        writeDiscoveryResult();

        ShipRun completed = coordinator.resume(run.id(), List.of());

        assertEquals(RunStatus.PAUSED, completed.status());
        assertEquals(Stage.DESIGN, completed.currentStage());
        assertEquals(StageStatus.COMPLETED,
                completed.stage(Stage.DISCOVERY).status());
        assertEquals(2, completed.stage(Stage.DISCOVERY).attempts());
        assertEquals(StageStatus.PENDING,
                completed.stage(Stage.DESIGN).status());
        assertNotEquals(
                run.stage(Stage.DISCOVERY).inputDigest(),
                completed.stage(Stage.DISCOVERY).inputDigest());
        assertNotEquals(run.context(), completed.context());
        assertEquals(
                List.of(
                        sessionId(run, Stage.DISCOVERY),
                        sessionId(completed, Stage.DISCOVERY)),
                Files.readAllLines(fixture.resolve("session-ids")));
        assertFalse(Files.exists(fixture.resolve("resumed-sessions")));
    }

    @Test
    void resumeWithAddedContextRunsAFreshAttemptAndRetainsItsReport()
            throws Exception {
        writeDiscoveryResult(
                "Question: Which deployment region should be used?");
        ShipRun run = controller.start(
                project,
                Oversight.SMART,
                List.of(new ShipContext.TextInput("Deploy the integration")));

        ShipRun paused = coordinator.run(run.id());

        assertEquals(RunStatus.PAUSED, paused.status());
        assertEquals(
                "Question: Which deployment region should be used?",
                paused.message());
        String firstDigest = paused.stage(Stage.DISCOVERY).inputDigest();

        writeDiscoveryResult(
                "Region accepted. Question: Which deployment window should be used?");
        ShipRun resumed = coordinator.resume(
                run.id(),
                List.of(new ShipContext.TextInput("Use eu-west")));

        assertEquals(RunStatus.PAUSED, resumed.status());
        assertEquals(Stage.DESIGN, resumed.currentStage());
        assertEquals(StageStatus.COMPLETED,
                resumed.stage(Stage.DISCOVERY).status());
        assertEquals(2, resumed.stage(Stage.DISCOVERY).attempts());
        assertNotEquals(
                firstDigest,
                resumed.stage(Stage.DISCOVERY).inputDigest());
        assertEquals(2, resumed.context().sources().size());
        assertEquals(
                "Use eu-west",
                resumed.context().sources().get(1).value());
        assertEquals(
                "Region accepted. Question: Which deployment window should be used?",
                resumed.message());

        String secondDigest = resumed.stage(Stage.DISCOVERY).inputDigest();
        Path briefing = state.resolve(run.id())
                .resolve("inputs")
                .resolve("discovery-"
                         + secondDigest.substring("sha256:".length()) + ".md");
        assertTrue(Files.readString(briefing).contains("Use eu-west"));
        assertTrue(Files.readString(fixture.resolve("prompt")).contains(
                "Do not ask for information already resolved by any context source."));
        assertEquals(
                List.of(
                        sessionId(paused, Stage.DISCOVERY),
                        sessionId(resumed, Stage.DISCOVERY)),
                Files.readAllLines(fixture.resolve("session-ids")));
        assertFalse(Files.exists(fixture.resolve("resumed-sessions")));
    }

    @Test
    void reportsWhenContextWasRecordedBeforeContinuationFailed()
            throws Exception {
        writeDiscoveryResult(
                "Question: Which deployment region should be used?");
        ShipRun run = controller.start(
                project,
                Oversight.SMART,
                List.of(new ShipContext.TextInput("Deploy the integration")));
        ShipRun paused = coordinator.run(run.id());
        assertEquals(RunStatus.PAUSED, paused.status());
        Path inputs = state.resolve(run.id()).resolve("inputs");
        Files.setPosixFilePermissions(
                inputs, PosixFilePermissions.fromString("rwxr-xr-x"));
        try {
            ShipController.Failure failure = assertThrows(
                    ShipController.Failure.class,
                    () -> coordinator.resume(
                            run.id(),
                            List.of(new ShipContext.TextInput("Use eu-west"))));

            assertEquals("worker-preparation-failed", failure.code());
            assertEquals(
                    "Ship context was recorded, but the run could not continue: "
                         + "Could not prepare the Ship worker attempt for " + run.id(),
                    failure.getMessage());
            assertTrue(failure.getCause() instanceof ShipController.Failure);
            ShipRun committed = controller.status(run.id());
            assertEquals(RunStatus.RUNNING, committed.status());
            assertEquals(2, committed.context().sources().size());
            assertEquals(
                    "Use eu-west",
                    committed.context().sources().get(1).value());
        } finally {
            Files.setPosixFilePermissions(
                    inputs, PosixFilePermissions.fromString("rwx------"));
        }
    }

    @Test
    void invalidTypedResultCanResumeWithADistinctAttemptPrompt()
            throws Exception {
        ShipRun run = designRun(List.of(
                new ShipContext.TextInput("retry invalid result")));
        Files.writeString(
                fixture.resolve("assistant-text"),
                "not a typed Ship result");

        ShipRun failed = coordinator.run(run.id());

        assertEquals(RunStatus.FAILED, failed.status());
        assertEquals(StageStatus.FAILED, failed.stage(Stage.DESIGN).status());
        assertEquals(1, failed.stage(Stage.DESIGN).attempts());
        String firstPrompt = Files.readString(fixture.resolve("prompt"));

        writeDesignResult();
        ShipRun completed = coordinator.resume(run.id(), List.of());

        assertDesignPaused(completed, 2);
        String secondPrompt = Files.readString(fixture.resolve("prompt"));
        assertNotEquals(firstPrompt, secondPrompt);
        String sessionId = sessionId(run);
        assertEquals(
                List.of(sessionId, sessionId),
                Files.readAllLines(fixture.resolve("session-ids")));
        assertEquals(
                List.of(sessionId),
                Files.readAllLines(fixture.resolve("resumed-sessions")));
    }

    @Test
    void missingExecuteManifestFailsTheActiveAttempt()
            throws Exception {
        Path metadata = Files.createDirectories(project.resolve(".camel-kit"));
        Files.writeString(
                metadata.resolve("pipeline.json"),
                "{\"mode\":\"manual\",\"activePipeline\":\"149-coordinator\"}\n");
        Path documents = Files.createDirectories(
                project.resolve("docs/camel-kit/149-coordinator"));
        Files.writeString(documents.resolve("design-spec.md"), "Imported design");
        writeWorkerResult("Implementation plan", mainPolicy());
        ShipRun run = controller.startFrom(
                project,
                Stage.PLAN,
                Oversight.ALWAYS,
                List.of());
        ShipRun planned = coordinator.run(run.id());
        assertEquals(RunStatus.PAUSED, planned.status());
        assertEquals(Stage.EXECUTE, planned.currentStage());
        controller.resume(run.id());
        writeWorkerResult("Execution report", null);

        ShipRun failed = coordinator.run(run.id());

        assertEquals(RunStatus.FAILED, failed.status());
        assertEquals(Stage.EXECUTE, failed.currentStage());
        assertEquals(StageStatus.FAILED, failed.stage(Stage.EXECUTE).status());
        assertEquals(1, failed.stage(Stage.EXECUTE).attempts());
        assertTrue(failed.message().contains("artifact manifest"));
    }

    @Test
    void generatedPlanExecuteThenValidatorCommitsPassStampFromStubEvidence()
            throws Exception {
        Path metadata = Files.createDirectories(project.resolve(".camel-kit"));
        Files.writeString(
                metadata.resolve("pipeline.json"),
                "{\"mode\":\"manual\",\"activePipeline\":\"149-coordinator\"}\n");
        Path documents = Files.createDirectories(
                project.resolve("docs/camel-kit/149-coordinator"));
        Files.writeString(documents.resolve("design-spec.md"), "Imported design");

        ArtifactPolicy policy = mainPolicy();
        writeWorkerResult("Implementation plan", policy);
        var snapshot = CatalogTestVerifier.mainSnapshot(
                Files.createDirectory(directory.resolve("catalog-fixture")));
        DeterministicEvidenceStub evidence = new DeterministicEvidenceStub();
        ShipCoordinator deterministic = new ShipCoordinator(
                state,
                controller,
                worker,
                target -> {
                    assertEquals(snapshot.target(), target);
                    return snapshot;
                },
                new ShipMainValidator(evidence),
                distribution,
                Map.of(),
                true,
                Clock.fixed(
                        Instant.parse("2026-07-29T12:00:00Z"),
                        ZoneOffset.UTC));
        ShipRun run = controller.startFrom(
                project,
                Stage.PLAN,
                Oversight.SMART,
                List.of());

        ShipRun planned = deterministic.run(run.id());

        assertEquals(RunStatus.PAUSED, planned.status());
        assertEquals(Stage.EXECUTE, planned.currentStage());
        assertEquals(StageStatus.COMPLETED,
                planned.stage(Stage.PLAN).status());
        Path inputDirectory = state.resolve(run.id()).resolve("inputs");
        Path policyContract = fileWithPrefix(
                inputDirectory, "artifact-policy-");
        assertTrue(Files.readString(fixture.resolve("prompt"))
                .contains(policyContract.toString()));
        assertTrue(Files.readString(fixture.resolve("prompt"))
                .contains("exact canonical project-relative routePath"));
        var fixedPolicy = new ObjectMapper().readTree(
                Files.readAllBytes(policyContract));
        assertEquals(distribution.camelMainVersion(),
                fixedPolicy.path("camelVersion").textValue());
        assertEquals(distribution.citrusVersion(),
                fixedPolicy.path("citrusVersion").textValue());
        assertTrue(fixedPolicy.path("routes").isEmpty());
        ShipRun executing = controller.resume(run.id());
        StageAttempt executeAttempt = controller.prepareAttempt(run.id());
        writeGeneratedMainCandidate(
                executeAttempt.workingDirectory(), policy);
        writeWorkerResult("Execution report", null);

        ShipRun executed = deterministic.run(run.id());

        assertEquals(RunStatus.PAUSED, executed.status());
        assertEquals(Stage.VALIDATE, executed.currentStage());
        assertEquals(StageStatus.COMPLETED,
                executed.stage(Stage.EXECUTE).status());
        assertEquals(1, executing.stage(Stage.EXECUTE).attempts());
        Path manifestSchema = fileWithPrefix(
                inputDirectory, "artifact-manifest-schema-");
        assertTrue(Files.readString(fixture.resolve("prompt"))
                .contains(manifestSchema.toString()));
        assertTrue(Files.readString(manifestSchema)
                .contains("artifact-manifest.schema.json"));

        ShipCoordinator unavailableCatalog = new ShipCoordinator(
                state,
                controller,
                worker,
                target -> {
                    throw new IOException("Catalog unavailable");
                },
                new ShipMainValidator(evidence),
                distribution,
                Map.of(),
                true,
                Clock.systemUTC());
        ShipRun validating = controller.resume(run.id());
        List<String> sessionIds = Files.readAllLines(
                fixture.resolve("session-ids"));
        String executeSessionId = sessionIds.get(sessionIds.size() - 1);
        Path sessionLock = state.resolve(run.id())
                .resolve("sessions")
                .resolve("." + executeSessionId + ".lock");
        try (FileChannel channel = FileChannel.open(
                sessionLock,
                Set.of(StandardOpenOption.CREATE, StandardOpenOption.WRITE),
                PosixFilePermissions.asFileAttribute(
                        PosixFilePermissions.fromString("rw-------")));
             FileLock ignored = channel.lock()) {
            ShipRun blocked = deterministic.run(run.id());

            assertEquals(validating, blocked);
            assertNull(blocked.message());
            assertFalse(Files.exists(
                    state.resolve(run.id())
                            .resolve("evidence/validate-1/stamp.json")));
        }

        ShipRun failed = unavailableCatalog.run(run.id());

        assertEquals(RunStatus.FAILED, failed.status());
        assertEquals(Stage.VALIDATE, failed.currentStage());
        assertEquals(StageStatus.FAILED,
                failed.stage(Stage.VALIDATE).status());
        assertEquals(1, failed.stage(Stage.VALIDATE).attempts());
        assertEquals(
                "Ship validation could not run: Catalog unavailable",
                failed.message());
        assertFalse(Files.exists(
                state.resolve(run.id())
                        .resolve("evidence/validate-1/stamp.json")));

        ShipRun completed = deterministic.resume(run.id(), List.of());

        assertEquals(RunStatus.COMPLETED, completed.status());
        assertEquals(StageStatus.COMPLETED,
                completed.stage(Stage.VALIDATE).status());
        assertEquals(2, completed.stage(Stage.VALIDATE).attempts());
        Path validationEvidence = state.resolve(run.id())
                .resolve("evidence/validate-2");
        ShipLocalStamp stamp = ShipLocalStampStore.read(
                validationEvidence, run.id());
        assertEquals(ShipLocalStamp.Status.PASS, stamp.status());
        assertEquals(List.of(
                "artifact-policy",
                "catalog-usage",
                "route-schema",
                "main-runtime-resolve-and-start",
                "citrus-integration-test-001"),
                stamp.checks().stream()
                        .map(ShipLocalStamp.Check::id)
                        .toList());
        assertEquals(List.of(
                "route-schema",
                "main-runtime-resolve-and-start",
                "citrus-integration-test-001"),
                evidence.commandIds);
        Path stampPath = validationEvidence.resolve("stamp.json")
                .toRealPath();
        assertEquals(
                stampPath.toString(),
                completed.stage(Stage.VALIDATE)
                        .artifacts().get(0).path());
        assertEquals(
                ShipDigest.sha256(Files.readAllBytes(stampPath)),
                completed.stage(Stage.VALIDATE)
                        .artifacts().get(0).digest());

        assertNotNull(completed.publication());
        Path publicationRecord = Path.of(completed.publication().path());
        assertEquals(
                state.resolve(run.id()).resolve("publication/publication.json").toRealPath(),
                publicationRecord.toRealPath());
        assertEquals(
                ShipDigest.sha256(Files.readAllBytes(publicationRecord)),
                completed.publication().digest());
        assertTrue(Files.isRegularFile(
                state.resolve(run.id()).resolve("publication/journal.json")),
                "the committed journal is retained as evidence");
        Path candidateRoot = Path.of(
                completed.stage(Stage.EXECUTE).artifacts().get(0).path());
        for (String relative : List.of(
                "src/main/resources/routes/orders.camel.yaml",
                "test/orders.camel.it.yaml",
                ".camel-kit/config.properties",
                "pom.xml",
                "docs/camel-kit/149-coordinator/artifact-manifest.json")) {
            assertEquals(
                    Files.readString(candidateRoot.resolve(relative)),
                    Files.readString(project.resolve(relative)),
                    "published " + relative);
        }
    }

    @Test
    void distributionDriftRestartsGeneratedPlanInsteadOfWedgingValidation()
            throws Exception {
        Path metadata = Files.createDirectories(project.resolve(".camel-kit"));
        Files.writeString(
                metadata.resolve("pipeline.json"),
                "{\"mode\":\"manual\",\"activePipeline\":\"149-coordinator\"}\n");
        Path documents = Files.createDirectories(
                project.resolve("docs/camel-kit/149-coordinator"));
        Files.writeString(documents.resolve("design-spec.md"), "Imported design");
        writeWorkerResult("Implementation plan", mainPolicy());
        ShipRun run = controller.startFrom(
                project,
                Stage.PLAN,
                Oversight.SMART,
                List.of());
        ShipRun planned = coordinator.run(run.id());
        assertEquals(RunStatus.PAUSED, planned.status());
        assertEquals(Stage.EXECUTE, planned.currentStage());
        controller.resume(run.id());

        Properties changed = new Properties();
        changed.setProperty("camel.main.version", "4.99.0");
        DistributionConfig upgradedDistribution
                = DistributionConfig.load(changed);
        ShipCoordinator upgraded = new ShipCoordinator(
                state,
                controller,
                worker,
                new ShipCatalogService(directory.resolve("m2"))::snapshot,
                new ShipMainValidator(),
                upgradedDistribution,
                Map.of(),
                true,
                Clock.systemUTC());

        ShipRun failed = upgraded.run(run.id());

        assertEquals(RunStatus.FAILED, failed.status());
        assertEquals(Stage.PLAN, failed.currentStage());
        assertEquals(2, failed.stage(Stage.PLAN).attempts());
        assertEquals(StageStatus.PENDING,
                failed.stage(Stage.EXECUTE).status());
        assertTrue(failed.message().contains(
                "invalid plan result"));
    }

    @Test
    void planContractUpgradeRestartsLegacyDurablePlanResult()
            throws Exception {
        Path metadata = Files.createDirectories(project.resolve(".camel-kit"));
        Files.writeString(
                metadata.resolve("pipeline.json"),
                "{\"mode\":\"manual\",\"activePipeline\":\"149-coordinator\"}\n");
        Path documents = Files.createDirectories(
                project.resolve("docs/camel-kit/149-coordinator"));
        Files.writeString(documents.resolve("design-spec.md"), "Imported design");
        writeWorkerResult("Implementation plan", mainPolicy());
        ShipRun run = controller.startFrom(
                project,
                Stage.PLAN,
                Oversight.ALWAYS,
                List.of());
        ShipRun planned = coordinator.run(run.id());
        assertEquals(RunStatus.PAUSED, planned.status());
        assertEquals(Stage.EXECUTE, planned.currentStage());

        Path runDirectory = state.resolve(run.id());
        Path policyContract = fileWithPrefix(
                runDirectory.resolve("inputs"), "artifact-policy-");
        String legacyDigest = ShipDigest.sha256(
                (planned.stage(Stage.PLAN).inputDigest()
                 + "\nplan:1:"
                 + ShipDigest.sha256(Files.readAllBytes(policyContract)))
                        .getBytes(StandardCharsets.UTF_8));
        Path markers = runDirectory.resolve("sessions/.camel-kit-results");
        Path currentMarker = fileWithPrefix(
                markers, run.id() + "-plan-");
        String currentDigest = new ObjectMapper()
                .readTree(currentMarker.toFile())
                .path("inputDigest")
                .textValue();
        assertNotEquals(legacyDigest, currentDigest);
        Files.writeString(
                currentMarker,
                Files.readString(currentMarker)
                        .replace(currentDigest, legacyDigest));
        String legacyMarkerName = run.id() + "-plan-"
                                  + legacyDigest.substring("sha256:".length())
                                  + "-1.json";
        Path legacyMarker = currentMarker.resolveSibling(legacyMarkerName);
        Files.move(currentMarker, legacyMarker);
        Request legacyRequest = new Request(
                run.id(),
                Stage.PLAN,
                1,
                project,
                runDirectory.resolve("sessions"),
                runDirectory.resolve("evidence/plan-1"),
                legacyDigest,
                true,
                "Recover a legacy PLAN contract result.");
        assertTrue(worker.recover(legacyRequest).isPresent());
        Files.writeString(fixture.resolve("mode"), "nonzero\n");

        ShipRun restarted = coordinator.resume(run.id(), List.of());

        assertEquals(RunStatus.FAILED, restarted.status());
        assertEquals(Stage.PLAN, restarted.currentStage());
        assertEquals(2, restarted.stage(Stage.PLAN).attempts());
        assertEquals(StageStatus.PENDING,
                restarted.stage(Stage.EXECUTE).status());
    }

    @Test
    void writesOrderedContextToADurableBriefingWithoutPuttingItInArgsOrLogs()
            throws Exception {
        Path document = Files.writeString(
                project.resolve("requirements.md"),
                "second-document-context");
        ShipRun run = designRun(List.of(
                new ShipContext.TextInput("first-inline-context"),
                new ShipContext.DocumentInput(document.getFileName()),
                new ShipContext.TextInput("third-inline-context")));

        ShipRun completed = coordinator.run(run.id());

        assertDesignPaused(completed, 1);
        String digest = run.stage(Stage.DESIGN).inputDigest();
        Path briefing = state.resolve(run.id())
                .resolve("inputs")
                .resolve("design-" + digest.substring("sha256:".length()) + ".md")
                .toRealPath();
        assertEquals(
                String.format(Locale.ROOT, """
                        # Controller input

                        Run: %s
                        Stage: DESIGN
                        Pipeline: 149-coordinator
                        Input digest: %s

                        ## Initial context

                        ### Source 1 (TEXT)

                        first-inline-context

                        ### Source 2 (DOCUMENT)

                        Path: %s

                        second-document-context

                        ### Source 3 (TEXT)

                        third-inline-context

                        ## DISCOVERY result

                        No imported artifact content.
                        """, run.id(), digest, document.toAbsolutePath()),
                Files.readString(briefing));

        String prompt = Files.readString(fixture.resolve("prompt"));
        assertTrue(prompt.contains(briefing.toString()));
        assertTrue(prompt.contains(digest));
        assertTrue(prompt.contains(
                "pipelineId is a JSON string only for DISCOVERY and JSON null otherwise"));
        assertTrue(prompt.contains(
                "report is one non-empty JSON string, never an object or array"));
        assertTrue(prompt.contains(
                "artifactPolicy is the policy object only for PLAN and JSON null otherwise"));
        assertTrue(prompt.contains(
                "materialAmbiguity is one JSON boolean, never an object or array"));
        assertFalse(prompt.contains("first-inline-context"));
        assertFalse(prompt.contains("second-document-context"));
        assertFalse(prompt.contains("third-inline-context"));

        String arguments = Files.readString(fixture.resolve("args"));
        String logs = retainedEvidence(run);
        for (String retained : List.of(
                briefing.toString(),
                "You are the bounded Pi worker",
                "first-inline-context",
                "second-document-context",
                "third-inline-context")) {
            assertFalse(arguments.contains(retained), retained);
            assertFalse(logs.contains(retained), retained);
        }
    }

    @Test
    void preplantedPassingStampCannotPromoteValidation()
            throws Exception {
        ShipRun run = generatedValidationRun();
        StageAttempt attempt = controller.prepareAttempt(run.id());
        ShipLocalStamp stamp = ShipLocalStamp.create(
                run.id(),
                List.of(new ShipLocalStamp.ToolVersion(
                        "pi",
                        null,
                        null,
                        ShipLocalStamp.Support.UNTESTED,
                        "Recovered fixture has no Pi diagnostic")),
                List.of(
                        new ShipLocalStamp.Check(
                                "forged-validation",
                                true,
                                ShipLocalStamp.Outcome.PASS,
                                "Preplanted result must not be trusted",
                                null,
                                null)),
                Instant.parse("2026-07-29T12:00:00Z"));
        ShipLocalStampStore.write(
                attempt.evidenceDirectory(), run.id(), stamp);
        int piSessions = Files.readAllLines(
                fixture.resolve("session-ids")).size();
        ShipCoordinator unavailableCatalog = new ShipCoordinator(
                state,
                controller,
                worker,
                target -> {
                    throw new IOException("Catalog unavailable");
                },
                new ShipMainValidator(),
                distribution,
                Map.of(),
                true,
                Clock.systemUTC());

        ShipRun failed = unavailableCatalog.resume(run.id(), List.of());

        assertEquals(RunStatus.FAILED, failed.status());
        assertEquals(Stage.VALIDATE, failed.currentStage());
        assertEquals(StageStatus.FAILED,
                failed.stage(Stage.VALIDATE).status());
        assertEquals(2, failed.stage(Stage.VALIDATE).attempts());
        assertEquals(
                "Ship validation could not run: Catalog unavailable",
                failed.message());
        assertTrue(failed.stage(Stage.VALIDATE).artifacts().isEmpty());
        assertEquals(stamp, ShipLocalStampStore.read(
                attempt.evidenceDirectory(), run.id()));
        assertFalse(Files.exists(
                state.resolve(run.id())
                        .resolve("evidence/validate-2/stamp.json")));
        assertEquals(
                piSessions,
                Files.readAllLines(fixture.resolve("session-ids")).size());
    }

    @Test
    void discoveryReusesAnExistingControllerBoundPipeline()
            throws Exception {
        Path metadata = Files.createDirectories(project.resolve(".camel-kit"));
        Files.writeString(
                metadata.resolve("pipeline.json"),
                "{\"mode\":\"manual\",\"activePipeline\":\"149-coordinator\"}\n");
        ShipRun run = controller.start(
                project, Oversight.SMART, List.of());
        String result = "{\"schemaVersion\":1,"
                        + "\"pipelineId\":\"149-coordinator\","
                        + "\"report\":\"Discovery report\","
                        + "\"artifactPolicy\":null,"
                        + "\"materialAmbiguity\":true}";
        Files.writeString(
                fixture.resolve("assistant-text"),
                result.replace("\"", "\\\""));

        ShipRun paused = coordinator.run(run.id());

        assertEquals(RunStatus.PAUSED, paused.status());
        assertEquals(Stage.DESIGN, paused.currentStage());
        assertEquals("149-coordinator", paused.pipelineId());
        assertTrue(Files.readString(fixture.resolve("prompt"))
                .contains("Reuse the controller-bound pipeline ID 149-coordinator"));
    }

    @Test
    void discoveryRejectsADifferentControllerBoundPipelineAsRetryableFailure()
            throws Exception {
        Path metadata = Files.createDirectories(project.resolve(".camel-kit"));
        Files.writeString(
                metadata.resolve("pipeline.json"),
                "{\"mode\":\"manual\",\"activePipeline\":\"149-coordinator\"}\n");
        ShipRun run = controller.start(
                project, Oversight.SMART, List.of());
        String result = "{\"schemaVersion\":1,"
                        + "\"pipelineId\":\"150-different\","
                        + "\"report\":\"Discovery report\","
                        + "\"artifactPolicy\":null,"
                        + "\"materialAmbiguity\":true}";
        Files.writeString(
                fixture.resolve("assistant-text"),
                result.replace("\"", "\\\""));

        ShipRun failed = coordinator.run(run.id());

        assertEquals(RunStatus.FAILED, failed.status());
        assertEquals(Stage.DISCOVERY, failed.currentStage());
        assertEquals(StageStatus.FAILED,
                failed.stage(Stage.DISCOVERY).status());
        assertEquals(1, failed.stage(Stage.DISCOVERY).attempts());
        assertEquals("149-coordinator", failed.pipelineId());
        assertTrue(failed.message().contains("controller-owned"));

        String corrected = result.replace(
                "150-different", "149-coordinator");
        Files.writeString(
                fixture.resolve("assistant-text"),
                corrected.replace("\"", "\\\""));

        ShipRun paused = coordinator.resume(run.id(), List.of());

        assertEquals(RunStatus.PAUSED, paused.status());
        assertEquals(Stage.DESIGN, paused.currentStage());
        assertEquals(StageStatus.COMPLETED,
                paused.stage(Stage.DISCOVERY).status());
        assertEquals(2, paused.stage(Stage.DISCOVERY).attempts());
        assertEquals("149-coordinator", paused.pipelineId());
    }

    private ShipRun designRun(List<? extends ShipContext.Input> context)
            throws IOException {
        Path metadata = Files.createDirectories(project.resolve(".camel-kit"));
        Files.writeString(
                metadata.resolve("pipeline.json"),
                "{\"mode\":\"manual\",\"activePipeline\":\"149-coordinator\"}\n");
        return controller.startFrom(
                project,
                Stage.DESIGN,
                Oversight.ALWAYS,
                context);
    }

    private ShipRun generatedValidationRun() throws Exception {
        Path metadata = Files.createDirectories(project.resolve(".camel-kit"));
        Files.writeString(
                metadata.resolve("pipeline.json"),
                "{\"mode\":\"manual\",\"activePipeline\":\"149-coordinator\"}\n");
        Path documents = Files.createDirectories(
                project.resolve("docs/camel-kit/149-coordinator"));
        Files.writeString(documents.resolve("design-spec.md"), "Imported design");
        ArtifactPolicy policy = mainPolicy();
        writeWorkerResult("Implementation plan", policy);
        ShipRun run = controller.startFrom(
                project,
                Stage.PLAN,
                Oversight.ALWAYS,
                List.of());
        ShipRun planned = coordinator.run(run.id());
        assertEquals(RunStatus.PAUSED, planned.status());
        assertEquals(Stage.EXECUTE, planned.currentStage());
        controller.resume(run.id());
        StageAttempt execute = controller.prepareAttempt(run.id());
        writeGeneratedMainCandidate(
                execute.workingDirectory(), policy);
        writeWorkerResult("Execution report", null);
        ShipRun executed = coordinator.run(run.id());
        assertEquals(RunStatus.PAUSED, executed.status());
        assertEquals(Stage.VALIDATE, executed.currentStage());
        return controller.resume(run.id());
    }

    private ArtifactPolicy mainPolicy() {
        String camelVersion = distribution.camelMainVersion();
        String citrusVersion = distribution.citrusVersion();
        return new ArtifactPolicy(
                "main",
                camelVersion,
                null,
                null,
                "yaml",
                "simple",
                citrusVersion,
                CitrusDependencyPolicy.required(citrusVersion),
                JavaPolicy.FORBIDDEN,
                List.of(),
                List.of(new RouteContract(
                        "orders",
                        "src/main/resources/routes/orders.camel.yaml",
                        "test/orders.camel.it.yaml")),
                true,
                true);
    }

    private void writeGeneratedMainCandidate(
            Path candidate, ArtifactPolicy policy)
            throws Exception {
        String routePath = "src/main/resources/routes/orders.camel.yaml";
        String testPath = "test/orders.camel.it.yaml";
        write(candidate, routePath, """
                - route:
                    id: orders
                    from:
                      uri: direct:orders
                """);
        write(candidate, testPath, """
                name: orders-test
                actions:
                  - send:
                      endpoint: "camel:sync:direct:camel-kit-ship-test-orders"
                      message:
                        body:
                          data: exercise route
                  - receive:
                      endpoint: "camel:sync:direct:camel-kit-ship-test-orders"
                      message:
                        body:
                          data: exercise route
                """);
        write(candidate, ".camel-kit/config.properties", String.format(Locale.ROOT, """
                project.runtime=main
                project.camelVersion=%s
                project.platformBomVersion=%s
                citrus.version=%s
                """,
                policy.camelVersion(),
                policy.camelVersion(),
                policy.citrusVersion()));
        write(candidate, "pom.xml", String.format(Locale.ROOT, """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId>
                  <artifactId>orders</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>org.apache.camel</groupId>
                      <artifactId>camel-main</artifactId>
                      <version>%1$s</version>
                    </dependency>
                    <dependency>
                      <groupId>org.apache.camel</groupId>
                      <artifactId>camel-yaml-dsl</artifactId>
                      <version>%1$s</version>
                    </dependency>
                    <dependency>
                      <groupId>org.apache.camel</groupId>
                      <artifactId>camel-direct</artifactId>
                      <version>%1$s</version>
                    </dependency>
                  </dependencies>
                </project>
                """, policy.camelVersion()));
        RouteArtifact route = new RouteArtifact(
                "orders",
                routePath,
                digest(candidate.resolve(routePath)));
        TestArtifact test = new TestArtifact(
                "orders",
                testPath,
                digest(candidate.resolve(testPath)));
        ArtifactManifest manifest = new ArtifactManifest(
                ArtifactManifest.SCHEMA_VERSION,
                policy.runtime(),
                policy.camelVersion(),
                policy.platformVersion(),
                policy.springBootVersion(),
                policy.routeDsl(),
                policy.expressionLanguage(),
                policy.citrusVersion(),
                policy.citrusDependencies(),
                policy.javaPolicy(),
                policy.approvedJavaExceptions(),
                List.of(route),
                List.of(test),
                List.of(
                        new DeclaredArtifact(
                                "config",
                                ".camel-kit/config.properties",
                                digest(candidate.resolve(
                                        ".camel-kit/config.properties")),
                                true),
                        new DeclaredArtifact(
                                "pom",
                                "pom.xml",
                                digest(candidate.resolve("pom.xml")),
                                true)),
                policy.citrusTestsRequired(),
                policy.oneCitrusTestPerRoute());
        Path manifestPath = candidate.resolve(
                "docs/camel-kit/149-coordinator/artifact-manifest.json");
        Files.createDirectories(manifestPath.getParent());
        new ObjectMapper().writerWithDefaultPrettyPrinter()
                .writeValue(manifestPath.toFile(), manifest);
    }

    private static void write(Path root, String relative, String content)
            throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private Path seedDurableResult(ShipRun run) throws Exception {
        return seedDurableResult(run, Outcome.SUCCEEDED);
    }

    private Path seedDurableResult(ShipRun run, Outcome expected)
            throws Exception {
        StageAttempt attempt = controller.prepareAttempt(run.id());
        Request request = new Request(
                run.id(),
                Stage.DESIGN,
                1,
                attempt.workingDirectory(),
                attempt.sessionDirectory(),
                attempt.evidenceDirectory(),
                run.stage(Stage.DESIGN).inputDigest(),
                true,
                "Seed the exact durable controller attempt.");
        assertEquals(expected, worker.run(request).outcome());
        assertEquals(expected, worker.recover(request).orElseThrow().outcome());
        return fileWithPrefix(
                request.sessionDirectory().resolve(".camel-kit-results"),
                sessionId(run));
    }

    private String retainedEvidence(ShipRun run) throws IOException {
        try (var files = Files.walk(
                state.resolve(run.id()).resolve("evidence/design-1"))) {
            StringBuilder retained = new StringBuilder();
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                retained.append(Files.readString(file));
            }
            return retained.toString();
        }
    }

    private static void assertDesignPaused(ShipRun run, int attempts) {
        assertEquals(RunStatus.PAUSED, run.status());
        assertEquals(Stage.PLAN, run.currentStage());
        assertEquals(StageStatus.COMPLETED, run.stage(Stage.DESIGN).status());
        assertEquals(attempts, run.stage(Stage.DESIGN).attempts());
    }

    private static String sessionId(ShipRun run) {
        return sessionId(run, Stage.DESIGN);
    }

    private static String sessionId(ShipRun run, Stage stage) {
        return run.id() + '-'
               + stage.name().toLowerCase(java.util.Locale.ROOT) + '-'
               + run.stage(stage).inputDigest()
                       .substring("sha256:".length());
    }

    private void writeDiscoveryResult() throws IOException {
        writeDiscoveryResult("Discovery report");
    }

    private void writeDiscoveryResult(String report) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        var result = mapper.createObjectNode();
        result.put("schemaVersion", 1);
        result.put("pipelineId", "149-coordinator");
        result.put("report", report);
        result.putNull("artifactPolicy");
        result.put("materialAmbiguity", true);
        Files.writeString(
                fixture.resolve("assistant-text"),
                mapper.writeValueAsString(result).replace("\"", "\\\""));
    }

    private void writeAmbiguousDesignResult(List<ShipRun.UnansweredQuestion> questions) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode result = mapper.createObjectNode();
        result.put("schemaVersion", ShipStageResult.SCHEMA_VERSION);
        result.putNull("pipelineId");
        result.put("report", "Unanswered design decisions");
        result.putNull("artifactPolicy");
        result.put("materialAmbiguity", true);
        result.set("unansweredQuestions", mapper.valueToTree(questions));
        Files.writeString(fixture.resolve("assistant-text"),
                mapper.writeValueAsString(result).replace("\"", "\\\""));
    }

    private void writeDesignResult() throws IOException {
        writeWorkerResult("Design report", null);
    }

    private void writeWorkerResult(
            String report, ArtifactPolicy policy)
            throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        var result = mapper.createObjectNode();
        result.put("schemaVersion", 1);
        result.putNull("pipelineId");
        result.put("report", report);
        if (policy == null) {
            result.putNull("artifactPolicy");
        } else {
            result.set("artifactPolicy", mapper.valueToTree(policy));
        }
        result.put("materialAmbiguity", false);
        Files.writeString(
                fixture.resolve("assistant-text"),
                mapper.writeValueAsString(result)
                        .replace("\"", "\\\""));
    }

    private final class DeterministicEvidenceStub
            implements ShipMainValidator.EvidenceExecutor {

        private final List<String> commandIds = new ArrayList<>();

        @Override
        public CommandEvidence run(
                Path candidate, Path evidence, EvidenceCommand command)
                throws IOException {
            commandIds.add(command.id());
            Files.createDirectory(evidence);
            Files.setPosixFilePermissions(
                    evidence,
                    PosixFilePermissions.fromString("rwx------"));
            Path java = writeExecutable(
                    evidence.resolve("java"), "java");
            byte[] stdout = "PASS\n".getBytes(StandardCharsets.UTF_8);
            Path stdoutPath = writePrivateFile(
                    evidence.resolve("raw.stdout.log"), stdout);
            Path stderrPath = writePrivateFile(
                    evidence.resolve("raw.stderr.log"), new byte[0]);
            Instant started = Instant.parse("2026-07-29T11:59:58Z");
            Instant ended = Instant.parse("2026-07-29T11:59:59Z");
            return new CommandEvidence(
                    command.id(),
                    java.toString(),
                    command.arguments(),
                    candidate.toString(),
                    command.inputDigests(),
                    started,
                    ended,
                    true,
                    false,
                    0,
                    null,
                    stdoutPath.toString(),
                    ShipDigest.sha256(stdout),
                    stderrPath.toString(),
                    ShipDigest.sha256(new byte[0]),
                    false,
                    evidence.resolve("sandbox"));
        }
    }

    private static String digest(Path file) throws IOException {
        return ShipDigest.sha256(Files.readAllBytes(file));
    }

    private static Path writePrivateFile(
            Path path, byte[] content)
            throws IOException {
        Files.write(path, content);
        Files.setPosixFilePermissions(
                path, PosixFilePermissions.fromString("rw-------"));
        return path;
    }

    private static void awaitFile(Path path) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (!Files.exists(path)) {
            if (System.nanoTime() >= deadline) {
                throw new AssertionError("Timed out waiting for " + path);
            }
            Thread.sleep(10);
        }
    }

    private static Thread awaitThread(
            String name, Thread.State state)
            throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (true) {
            Thread thread = Thread.getAllStackTraces().keySet().stream()
                    .filter(candidate -> name.equals(candidate.getName())
                            && candidate.getState() == state)
                    .findFirst()
                    .orElse(null);
            if (thread != null) {
                return thread;
            }
            if (System.nanoTime() >= deadline) {
                throw new AssertionError(
                        "Timed out waiting for " + name + " to enter " + state);
            }
            Thread.sleep(10);
        }
    }

    private static Path fileWithPrefix(Path directory, String prefix)
            throws IOException {
        try (var files = Files.list(directory)) {
            return files.filter(path -> path.getFileName().toString()
                    .startsWith(prefix))
                    .findFirst()
                    .orElseThrow();
        }
    }

    private void assertFixtureProcessesGone() throws Exception {
        for (String file : List.of("parent-pid", "child-pid")) {
            long pid = Long.parseLong(
                    Files.readString(fixture.resolve(file)).strip());
            long deadline = System.nanoTime()
                            + Duration.ofSeconds(5).toNanos();
            while (ProcessHandle.of(pid)
                    .map(ProcessHandle::isAlive)
                    .orElse(false)) {
                if (System.nanoTime() >= deadline) {
                    throw new AssertionError(
                            "Fake Pi process is still alive: " + pid);
                }
                Thread.sleep(10);
            }
        }
    }

    private static Path writeExecutable(Path path, String script)
            throws IOException {
        Files.writeString(path, script);
        Files.setPosixFilePermissions(
                path, PosixFilePermissions.fromString("rwx------"));
        return path;
    }
}
