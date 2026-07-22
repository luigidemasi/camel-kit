package io.github.luigidemasi.camelkit.ship.controller;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import io.github.luigidemasi.camelkit.ship.ShipDigest;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogSubject;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogTestVerifier;
import io.github.luigidemasi.camelkit.ship.context.InitialContext;
import io.github.luigidemasi.camelkit.ship.context.InitialContextRequest;
import io.github.luigidemasi.camelkit.ship.ledger.DecisionLedger;
import io.github.luigidemasi.camelkit.ship.ledger.DecisionLedger.Category;
import io.github.luigidemasi.camelkit.ship.ledger.DecisionLedger.GapReviewStatus;
import io.github.luigidemasi.camelkit.ship.ledger.DecisionLedger.JavaPolicy;
import io.github.luigidemasi.camelkit.ship.ledger.DecisionLedger.RequirementsPolicy;
import io.github.luigidemasi.camelkit.ship.ledger.DecisionLedger.RouteContract;
import io.github.luigidemasi.camelkit.ship.ledger.DecisionLedger.SourceRef;
import io.github.luigidemasi.camelkit.ship.ledger.LedgerStatus;
import io.github.luigidemasi.camelkit.ship.ledger.LedgerValidator;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.DiscoveryChallenge;
import io.github.luigidemasi.camelkit.ship.protocol.ShipStage;
import io.github.luigidemasi.camelkit.ship.protocol.StageRequest;
import io.github.luigidemasi.camelkit.ship.protocol.StageResult;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledOnOs(OS.LINUX)
class ShipControllerTest {

    private static final String CITRUS_VERSION = "5.0.0-M2";
    private static final List<String> CITRUS_DEPENDENCIES = List.of(
            "org.citrusframework:citrus-camel:5.0.0-M2",
            "org.citrusframework:citrus-junit-jupiter:5.0.0-M2",
            "org.citrusframework:citrus-yaml:5.0.0-M2");

    @TempDir
    Path temporaryDirectory;

    @Test
    void durableControllerReconstructsBeforeEveryExactHeadMutation() throws Exception {
        Path project = Files.createDirectory(temporaryDirectory.resolve("project"))
                .toAbsolutePath()
                .normalize();
        Path state = temporaryDirectory.resolve("state").toAbsolutePath().normalize();
        Files.writeString(project.resolve("route.camel.yaml"), "- from: {uri: timer:tick}\n");
        ShipController controller = controller(state);
        ShipRunView created = controller.start(prepared(project));

        ShipRunView resolving = controller.beginContextResolution(
                created.runId(), created.eventDigest(), new InitialContextRequest.Text("context"));
        ShipRunView recorded = controller.continueContextResolution(
                created.runId(), resolving.eventDigest(), List.of());

        ShipRunView restarted = controller(state).status(created.runId());
        assertEquals(recorded, restarted);
        assertEquals(ShipState.CONTEXT_RECORDED, restarted.state());
        assertEquals(2, restarted.revision());
        assertEquals(recorded.authority().head(), restarted.authority().head());
        assertEquals(
                "- from: {uri: timer:tick}\n",
                Files.readString(restarted.sourceDirectory().resolve("route.camel.yaml")));
    }

    @Test
    void staleMutationCannotAppendOrReplaceTheDurableHead() throws Exception {
        Path project = Files.createDirectory(temporaryDirectory.resolve("stale-project"))
                .toAbsolutePath()
                .normalize();
        Path state = temporaryDirectory.resolve("stale-state").toAbsolutePath().normalize();
        ShipController controller = controller(state);
        ShipRunView created = controller.start(prepared(project));
        ShipRunView resolving = controller.beginContextResolution(
                created.runId(), created.eventDigest(), new InitialContextRequest.Text("context"));

        ShipControllerException failure = assertThrows(
                ShipControllerException.class,
                () -> controller.continueContextResolution(
                        created.runId(), created.eventDigest(), List.of()));

        assertEquals("stale-run-head", failure.code());
        ShipRunView unchanged = controller.status(created.runId());
        assertEquals(resolving.eventDigest(), unchanged.eventDigest());
        assertEquals(ShipState.CONTEXT_RESOLVING, unchanged.state());
        assertEquals(2, FileShipEventStore.open(state, created.runId()).replay().size());
    }

    @Test
    void lifecycleCannotBeSkippedThroughTheControllerSurface() throws Exception {
        Path project = Files.createDirectory(temporaryDirectory.resolve("skip-project"))
                .toAbsolutePath()
                .normalize();
        Path state = temporaryDirectory.resolve("skip-state").toAbsolutePath().normalize();
        ShipController controller = controller(state);
        ShipRunView created = controller.start(prepared(project));

        Exception failure = assertThrows(
                Exception.class,
                () -> controller.continueContextResolution(
                        created.runId(), created.eventDigest(), List.of()));

        assertTrue(failure.getMessage().contains("requires state"));
        assertEquals(created, controller.status(created.runId()));
    }

    @Test
    void absoluteProjectDocumentIsNormalizedAndBoundToTheImmutableSource() throws Exception {
        Path project = Files.createDirectory(temporaryDirectory.resolve("document-project"))
                .toAbsolutePath().normalize();
        Path document = Files.writeString(project.resolve("requirements.md"), "orders");
        Path state = temporaryDirectory.resolve("document-state").toAbsolutePath().normalize();
        ShipController controller = controller(state);
        ShipRunView created = controller.start(prepared(project));
        ShipRunView resolving = controller.beginContextResolution(
                created.runId(),
                created.eventDigest(),
                new InitialContextRequest.Documents(document.toString()));

        ShipRunView recorded = controller.continueContextResolution(
                created.runId(), resolving.eventDigest(), List.of());
        InitialContext context = ShipJson.mapper().readValue(
                ShipBlobStore.open(state, created.runId())
                        .readBytes(recorded.context(), ShipJson.MAX_DOCUMENT_BYTES),
                InitialContext.class);

        assertEquals("project:requirements.md", context.sources().get(0).provenance());
        assertEquals("orders", context.sources().get(0).content());
    }

    @Test
    void changedLiveProjectDocumentCannotEnterTheDurableContext() throws Exception {
        Path project = Files.createDirectory(temporaryDirectory.resolve("changed-project"))
                .toAbsolutePath().normalize();
        Path document = Files.writeString(project.resolve("requirements.md"), "before");
        Path state = temporaryDirectory.resolve("changed-state").toAbsolutePath().normalize();
        ShipController controller = controller(state);
        ShipRunView created = controller.start(prepared(project));
        ShipRunView resolving = controller.beginContextResolution(
                created.runId(),
                created.eventDigest(),
                new InitialContextRequest.Documents("requirements.md"));
        Files.writeString(document, "after");

        ShipControllerException failure = assertThrows(
                ShipControllerException.class,
                () -> controller.continueContextResolution(
                        created.runId(), resolving.eventDigest(), List.of()));

        assertEquals("context-source-changed", failure.code());
        assertEquals(resolving, controller.status(created.runId()));
        assertEquals(2, FileShipEventStore.open(state, created.runId()).replay().size());
    }

    @Test
    void externalDocumentConsentRemainsDurablyDeferredInThisSlice() throws Exception {
        Path project = Files.createDirectory(temporaryDirectory.resolve("external-project"))
                .toAbsolutePath().normalize();
        Path external = Files.writeString(temporaryDirectory.resolve("external.md"), "external")
                .toRealPath();
        Path state = temporaryDirectory.resolve("external-state").toAbsolutePath().normalize();
        ShipController controller = controller(state);
        ShipRunView created = controller.start(prepared(project));
        ShipRunView resolving = controller.beginContextResolution(
                created.runId(),
                created.eventDigest(),
                new InitialContextRequest.Documents(external.toString()));

        ShipControllerException failure = assertThrows(
                ShipControllerException.class,
                () -> controller.continueContextResolution(
                        created.runId(), resolving.eventDigest(), List.of()));

        assertEquals("document-consent-boundary-unavailable", failure.code());
        assertEquals(resolving, controller.status(created.runId()));
        assertEquals(2, FileShipEventStore.open(state, created.runId()).replay().size());
    }

    @Test
    void artifactFreeDiscoveryAndGapReviewReplayToRequirementsReady() throws Exception {
        Path project = Files.createDirectory(temporaryDirectory.resolve("flow-project"))
                .toAbsolutePath().normalize();
        Path state = temporaryDirectory.resolve("flow-state").toAbsolutePath().normalize();
        String requirements = "main 4.21.0 yaml simple forbidden " + CITRUS_VERSION + ' '
                              + String.join(" ", CITRUS_DEPENDENCIES) + " resolved";
        ShipController controller = controller(state);
        ShipRunView discovery = startDiscovery(controller, project, requirements);
        assertEquals(ShipState.DISCOVERY_ANALYZING, discovery.state());
        assertEquals(2, discovery.activeRequest().attempt());

        SourceRef source = contextSource(state, discovery);
        DecisionLedger first = readyLedger(1, GapReviewStatus.NOT_RUN, source);
        StageResult catalogRequest = result(
                discovery.activeRequest(),
                StageResult.Outcome.NEEDS_DISCOVERY,
                first,
                null,
                List.of(new CatalogSubject(CatalogSubject.Kind.COMPONENT, "direct")));
        Path catalog = Files.createDirectory(temporaryDirectory.resolve("flow-catalog"));
        ShipRunView continued = controller.submitStageResult(
                discovery.runId(),
                discovery.eventDigest(),
                encoded(catalogRequest),
                CatalogTestVerifier.mainSnapshot(catalog));
        assertEquals(ShipState.DISCOVERY_ANALYZING, continued.state());
        assertEquals(3, continued.activeRequest().attempt());
        assertTrue(continued.catalogEvidence() != null);

        DecisionLedger candidate = readyLedger(2, GapReviewStatus.NOT_RUN, source);
        ShipRunView reviewing = controller.submitStageResult(
                continued.runId(),
                continued.eventDigest(),
                encoded(result(
                        continued.activeRequest(),
                        StageResult.Outcome.COMPLETED,
                        candidate,
                        null,
                        List.of())));
        assertEquals(ShipState.REVIEW_RUNNING, reviewing.state());
        assertEquals(ShipStage.REVIEW, reviewing.activeRequest().stage());
        assertEquals(4, reviewing.activeRequest().attempt());

        DecisionLedger reviewed = readyLedger(2, GapReviewStatus.PASSED, source);
        ShipRunView ready = controller.submitStageResult(
                reviewing.runId(),
                reviewing.eventDigest(),
                encoded(result(
                        reviewing.activeRequest(),
                        StageResult.Outcome.COMPLETED,
                        reviewed,
                        null,
                        List.of())));

        assertEquals(ShipState.REQUIREMENTS_READY, ready.state());
        assertEquals(ready.ledger().digest(), ready.requirementsDigest());
        assertNull(ready.activeRequest());
        assertEquals(ready, controller(state).status(ready.runId()));
        assertEquals(7, FileShipEventStore.open(state, ready.runId()).replay().size());
    }

    @Test
    void discoveryQuestionAndControllerSignedAnswerSurviveRestart() throws Exception {
        Path project = Files.createDirectory(temporaryDirectory.resolve("question-project"))
                .toAbsolutePath().normalize();
        Path state = temporaryDirectory.resolve("question-state").toAbsolutePath().normalize();
        ShipController controller = controller(state);
        ShipRunView discovery = startDiscovery(controller, project, "yaml");
        SourceRef source = contextSource(state, discovery);
        DecisionLedger.Question question = new DecisionLedger.Question(
                "question-runtime",
                "runtime-choice",
                "Which runtime?",
                List.of("main", "quarkus"),
                "",
                LedgerStatus.OPEN);
        DecisionLedger ledger = questionLedger(source, question);

        ShipRunView waiting = controller.submitStageResult(
                discovery.runId(),
                discovery.eventDigest(),
                encoded(result(
                        discovery.activeRequest(),
                        StageResult.Outcome.NEEDS_USER_INPUT,
                        ledger,
                        question,
                        List.of())));
        DiscoveryChallenge challenge = waiting.pendingInteraction().discovery().challenge();
        assertEquals(ShipState.WAITING_FOR_DISCOVERY_ANSWER, waiting.state());
        assertNull(challenge.recommendation());

        assertThrows(
                IllegalArgumentException.class,
                () -> controller.answerDiscovery(
                        waiting.runId(),
                        waiting.eventDigest(),
                        challenge,
                        "x".repeat(Interaction.MAX_RESPONSE_CHARS + 1),
                        "test-console"));
        assertEquals(waiting, controller.status(waiting.runId()));

        ShipRunView answered = controller.answerDiscovery(
                waiting.runId(),
                waiting.eventDigest(),
                challenge,
                "main",
                "test-console");

        assertEquals(ShipState.DISCOVERY_ANALYZING, answered.state());
        assertEquals(3, answered.activeRequest().attempt());
        assertEquals("interaction-response", answered.latestInteraction().kind());
        assertEquals(answered, controller(state).status(answered.runId()));
    }

    @Test
    void discoveryQuestionCapacityFailureCannotCommitOrConsumeBlobs() throws Exception {
        Path project = Files.createDirectory(temporaryDirectory.resolve("question-capacity-project"))
                .toAbsolutePath().normalize();
        Path state = temporaryDirectory.resolve("question-capacity-state")
                .toAbsolutePath().normalize();
        ShipController controller = controller(state);
        ShipRunView active = startDiscovery(controller, project, "yaml");
        SourceRef context = contextSource(state, active);
        List<SourceRef> answers = new ArrayList<>();
        String response = "a" + "\u0001".repeat(Interaction.MAX_RESPONSE_CHARS - 1);
        String channel = "\u0001".repeat(Interaction.MAX_CHANNEL_CHARS);

        for (int index = 0; index < 3; index++) {
            DecisionLedger.Question question = capacityQuestion(index);
            ShipRunView waiting = controller.submitStageResult(
                    active.runId(),
                    active.eventDigest(),
                    encoded(result(
                            active.activeRequest(),
                            StageResult.Outcome.NEEDS_USER_INPUT,
                            capacityLedger(index + 1, context, answers, index),
                            question,
                            List.of())));
            active = controller.answerDiscovery(
                    waiting.runId(),
                    waiting.eventDigest(),
                    waiting.pendingInteraction().discovery().challenge(),
                    response,
                    channel);
            answers.add(capacityAnswerSource(index + 1, response));
        }

        DecisionLedger.Question nextQuestion = capacityQuestion(3);
        StageResult nextResult = result(
                active.activeRequest(),
                StageResult.Outcome.NEEDS_USER_INPUT,
                capacityLedger(4, context, answers, 3),
                nextQuestion,
                List.of());
        ShipRunView before = active;
        List<String> blobsBefore = blobInventory(state, active.runId());
        int eventsBefore = FileShipEventStore.open(state, active.runId()).replay().size();

        java.io.IOException failure = assertThrows(
                java.io.IOException.class,
                () -> controller.submitStageResult(
                        before.runId(), before.eventDigest(), encoded(nextResult)));

        assertTrue(failure.getMessage().contains("interaction bundle exceeds"));
        assertEquals(before, controller.status(before.runId()));
        assertEquals(eventsBefore, FileShipEventStore.open(state, before.runId()).replay().size());
        assertEquals(blobsBefore, blobInventory(state, before.runId()));
    }

    @Test
    void missingControllerCatalogSnapshotCannotAdvanceOrChangeTheHead() throws Exception {
        Path project = Files.createDirectory(temporaryDirectory.resolve("missing-catalog-project"))
                .toAbsolutePath().normalize();
        Path state = temporaryDirectory.resolve("missing-catalog-state")
                .toAbsolutePath().normalize();
        String requirements = "main 4.21.0 yaml simple forbidden " + CITRUS_VERSION + ' '
                              + String.join(" ", CITRUS_DEPENDENCIES) + " resolved";
        ShipController controller = controller(state);
        ShipRunView discovery = startDiscovery(controller, project, requirements);
        DecisionLedger ledger = readyLedger(
                1, GapReviewStatus.NOT_RUN, contextSource(state, discovery));
        StageResult result = result(
                discovery.activeRequest(),
                StageResult.Outcome.NEEDS_DISCOVERY,
                ledger,
                null,
                List.of(new CatalogSubject(CatalogSubject.Kind.COMPONENT, "direct")));
        List<String> blobsBefore = blobInventory(state, discovery.runId());

        ShipControllerException failure = assertThrows(
                ShipControllerException.class,
                () -> controller.submitStageResult(
                        discovery.runId(), discovery.eventDigest(), encoded(result)));

        assertEquals("catalog-snapshot-required", failure.code());
        assertEquals(discovery, controller.status(discovery.runId()));
        assertEquals(4, FileShipEventStore.open(state, discovery.runId()).replay().size());
        assertEquals(blobsBefore, blobInventory(state, discovery.runId()));
    }

    @Test
    void malformedTypedContextCannotCommitOrConsumeBlobCapacity() throws Exception {
        Path project = Files.createDirectory(temporaryDirectory.resolve("invalid-context-project"))
                .toAbsolutePath().normalize();
        Files.write(project.resolve("empty.txt"), new byte[0]);
        Files.write(project.resolve("invalid-utf8.txt"), new byte[]{(byte) 0xc3, 0x28});
        Files.write(project.resolve("nul.txt"), new byte[]{'a', 0, 'b'});
        Files.write(
                project.resolve("oversized.txt"),
                new byte[InitialContext.MAX_DOCUMENT_BYTES + 1]);
        byte[] aggregateLimit = new byte[InitialContext.MAX_DOCUMENT_BYTES];
        java.util.Arrays.fill(aggregateLimit, (byte) 'a');
        Files.write(project.resolve("aggregate-limit.txt"), aggregateLimit);
        Path state = temporaryDirectory.resolve("invalid-context-state")
                .toAbsolutePath().normalize();
        ShipController controller = controller(state);
        ShipRunView created = controller.start(prepared(project));
        List<String> blobsBefore = blobInventory(state, created.runId());

        for (InitialContextRequest invalid : List.of(
                new InitialContextRequest.Text("x".repeat(InitialContext.MAX_TOTAL_BYTES + 1)),
                new InitialContextRequest.Text("invalid-\ud800"),
                new InitialContextRequest.Documents(""),
                new InitialContextRequest.Documents("../escape"),
                new InitialContextRequest.Documents("empty.txt"),
                new InitialContextRequest.Documents("invalid-utf8.txt"),
                new InitialContextRequest.Documents("nul.txt"),
                new InitialContextRequest.Documents("oversized.txt"),
                new InitialContextRequest.Composite(
                        List.of(
                                new InitialContextRequest.UserText("x"),
                                new InitialContextRequest.DocumentReference("aggregate-limit.txt"),
                                new InitialContextRequest.DocumentReference("aggregate-limit.txt"))))) {
            assertThrows(
                    java.io.IOException.class,
                    () -> controller.beginContextResolution(
                            created.runId(), created.eventDigest(), invalid));
            assertEquals(created, controller.status(created.runId()));
            assertEquals(blobsBefore, blobInventory(state, created.runId()));
        }

        ShipControllerException unavailable = assertThrows(
                ShipControllerException.class,
                () -> controller.beginContextResolution(
                        created.runId(),
                        created.eventDigest(),
                        new InitialContextRequest.Documents("missing.txt")));
        assertEquals("context-source-unavailable", unavailable.code());
        assertEquals(created, controller.status(created.runId()));
        assertEquals(blobsBefore, blobInventory(state, created.runId()));
    }

    @Test
    void failedStartRemovesItsUncommittedPrivateRunRoot() throws Exception {
        Path state = temporaryDirectory.resolve("failed-start-state")
                .toAbsolutePath().normalize();
        Path missingProject = temporaryDirectory.resolve("missing-project")
                .toAbsolutePath().normalize();

        assertThrows(
                java.io.IOException.class,
                () -> controller(state).start(prepared(missingProject)));

        try (Stream<Path> entries = Files.list(state)) {
            assertEquals(0, entries.filter(path -> path.getFileName().toString().startsWith("ship-")).count());
        }
    }

    @Test
    void validManualPipelineStateIsPreservedAndExcludedFromTheRunSource() throws Exception {
        Path project = Files.createDirectory(temporaryDirectory.resolve("manual-pipeline-project"))
                .toAbsolutePath().normalize();
        Path state = temporaryDirectory.resolve("manual-pipeline-state")
                .toAbsolutePath().normalize();
        Path pipeline = Files.createDirectories(project.resolve(".camel-kit"))
                .resolve("pipeline.json");
        byte[] content = "{\"mode\":\"manual\",\"phase\":\"design\"}"
                .getBytes(StandardCharsets.UTF_8);
        Files.write(pipeline, content);

        ShipRunView created = controller(state).start(prepared(project));

        assertEquals(ShipState.CREATED, created.state());
        assertArrayEquals(content, Files.readAllBytes(pipeline));
        assertFalse(Files.exists(
                created.sourceDirectory().resolve(".camel-kit/pipeline.json"),
                LinkOption.NOFOLLOW_LINKS));
    }

    @Test
    void preReleasePipelineStatesAreRejectedAndPreservedBeforeRunCreation() throws Exception {
        List<String> rejected = List.of(
                "{\"mode\":\"ship\"}",
                "{\"mode\":\"manual\",\"mode\":\"manual\"}",
                "{\"mode\":\"manual\"} trailing",
                "{}",
                "[]",
                "{\"mode\":1}",
                "{\"mode\":\"unknown\"}");
        for (int index = 0; index < rejected.size(); index++) {
            Path project = Files.createDirectory(
                    temporaryDirectory.resolve("rejected-pipeline-project-" + index))
                    .toAbsolutePath().normalize();
            Path state = temporaryDirectory.resolve("rejected-pipeline-state-" + index)
                    .toAbsolutePath().normalize();
            Path pipeline = Files.createDirectories(project.resolve(".camel-kit"))
                    .resolve("pipeline.json");
            byte[] content = rejected.get(index).getBytes(StandardCharsets.UTF_8);
            Files.write(pipeline, content);

            ShipControllerException failure = assertThrows(
                    ShipControllerException.class,
                    () -> controller(state).start(prepared(project)));

            assertEquals("pre-release-ship-state", failure.code());
            assertTrue(failure.getMessage().contains("Archive or move it outside the project"));
            assertArrayEquals(content, Files.readAllBytes(pipeline));
            assertNoRunRoot(state);
        }
    }

    @Test
    void oversizedAndUnsafePipelineStateIsRejectedWithoutFollowingOrChangingIt() throws Exception {
        Path oversizedProject = Files.createDirectory(temporaryDirectory.resolve("oversized-pipeline-project"))
                .toAbsolutePath().normalize();
        Path oversizedState = temporaryDirectory.resolve("oversized-pipeline-state")
                .toAbsolutePath().normalize();
        Path oversized = Files.createDirectories(oversizedProject.resolve(".camel-kit"))
                .resolve("pipeline.json");
        byte[] oversizedContent = ("{\"mode\":\"manual\",\"padding\":\""
                                   + "x".repeat(ShipLegacyStateGuard.MAX_PIPELINE_BYTES) + "\"}")
                .getBytes(StandardCharsets.UTF_8);
        Files.write(oversized, oversizedContent);

        ShipControllerException oversizedFailure = assertThrows(
                ShipControllerException.class,
                () -> controller(oversizedState).start(prepared(oversizedProject)));
        assertEquals("pre-release-ship-state", oversizedFailure.code());
        assertArrayEquals(oversizedContent, Files.readAllBytes(oversized));
        assertNoRunRoot(oversizedState);

        Path linkedProject = Files.createDirectory(temporaryDirectory.resolve("linked-pipeline-project"))
                .toAbsolutePath().normalize();
        Path linkedState = temporaryDirectory.resolve("linked-pipeline-state")
                .toAbsolutePath().normalize();
        Path external = temporaryDirectory.resolve("external-pipeline.json");
        byte[] externalContent = "{\"mode\":\"manual\"}".getBytes(StandardCharsets.UTF_8);
        Files.write(external, externalContent);
        Path linked = Files.createDirectories(linkedProject.resolve(".camel-kit"))
                .resolve("pipeline.json");
        Files.createSymbolicLink(linked, external);

        ShipControllerException linkedFailure = assertThrows(
                ShipControllerException.class,
                () -> controller(linkedState).start(prepared(linkedProject)));
        assertEquals("pre-release-ship-state", linkedFailure.code());
        assertTrue(Files.isSymbolicLink(linked));
        assertArrayEquals(externalContent, Files.readAllBytes(external));
        assertNoRunRoot(linkedState);
    }

    @Test
    void anyShipStateEntryIsRejectedAndPreservedBeforeRunCreation() throws Exception {
        Path project = Files.createDirectory(temporaryDirectory.resolve("ship-state-project"))
                .toAbsolutePath().normalize();
        Path state = temporaryDirectory.resolve("ship-state-controller")
                .toAbsolutePath().normalize();
        Path shipState = Files.createDirectories(
                project.resolve(".camel-kit/ship-state.json"));
        Path sentinel = Files.writeString(shipState.resolve("sentinel"), "preserve");

        ShipControllerException failure = assertThrows(
                ShipControllerException.class,
                () -> controller(state).start(prepared(project)));

        assertEquals("pre-release-ship-state", failure.code());
        assertEquals("preserve", Files.readString(sentinel));
        assertNoRunRoot(state);
    }

    @Test
    void startRechecksLegacyStateImmediatelyBeforeCommitting() throws Exception {
        Path project = Files.createDirectory(temporaryDirectory.resolve("rechecked-pipeline-project"))
                .toAbsolutePath().normalize();
        Path state = temporaryDirectory.resolve("rechecked-pipeline-state")
                .toAbsolutePath().normalize();
        Path pipeline = Files.createDirectories(project.resolve(".camel-kit"))
                .resolve("pipeline.json");
        byte[] manual = "{\"mode\":\"manual\"}".getBytes(StandardCharsets.UTF_8);
        byte[] ship = "{\"mode\":\"ship\"}".getBytes(StandardCharsets.UTF_8);
        Files.write(pipeline, manual);
        Clock mutatingClock = new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                try {
                    Files.write(pipeline, ship);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                return Instant.parse("2025-01-01T00:00:00Z");
            }
        };

        ShipControllerException failure = assertThrows(
                ShipControllerException.class,
                () -> new ShipController(state, mutatingClock).start(prepared(project)));

        assertEquals("pre-release-ship-state", failure.code());
        assertArrayEquals(ship, Files.readAllBytes(pipeline));
        assertNoRunRoot(state);
    }

    private static void assertNoRunRoot(Path state) throws IOException {
        assertTrue(Files.isDirectory(state, LinkOption.NOFOLLOW_LINKS));
        try (Stream<Path> entries = Files.list(state)) {
            assertEquals(0, entries
                    .filter(path -> path.getFileName().toString().startsWith("ship-"))
                    .count());
        }
    }

    @Test
    void controllerStateAndProjectRootsMustBeDisjointBeforeCreation() throws Exception {
        Path project = Files.createDirectory(temporaryDirectory.resolve("overlap-project"))
                .toAbsolutePath().normalize();
        Path stateInsideProject = project.resolve("state");

        ShipControllerException nestedState = assertThrows(
                ShipControllerException.class,
                () -> controller(stateInsideProject).start(prepared(project)));
        assertEquals("state-project-overlap", nestedState.code());
        assertFalse(Files.exists(stateInsideProject, LinkOption.NOFOLLOW_LINKS));

        Path state = Files.createDirectory(temporaryDirectory.resolve("overlap-state"))
                .toAbsolutePath().normalize();
        Path projectInsideState = Files.createDirectory(state.resolve("project"))
                .toAbsolutePath().normalize();
        ShipControllerException nestedProject = assertThrows(
                ShipControllerException.class,
                () -> controller(state).start(prepared(projectInsideState)));
        assertEquals("state-project-overlap", nestedProject.code());
        try (Stream<Path> entries = Files.list(state)) {
            assertEquals(0, entries.filter(path -> path.getFileName().toString().startsWith("ship-")).count());
        }
    }

    @Test
    void oversizedStageResultIsRejectedBeforeRunLookupOrCopy() {
        ShipController controller = controller(
                temporaryDirectory.resolve("oversized-result-state").toAbsolutePath().normalize());

        java.io.IOException failure = assertThrows(
                java.io.IOException.class,
                () -> controller.submitStageResult(
                        "not-a-run",
                        "sha256:" + "0".repeat(64),
                        new byte[ShipStageResultReader.MAX_RESULT_BYTES + 1]));

        assertTrue(failure.getMessage().contains("Stage result size"));
    }

    @Test
    void gapReviewFeedbackAtomicallyReopensDiscoveryAndClearsCatalogEvidence() throws Exception {
        Path project = Files.createDirectory(temporaryDirectory.resolve("review-gap-project"))
                .toAbsolutePath().normalize();
        Path state = temporaryDirectory.resolve("review-gap-state")
                .toAbsolutePath().normalize();
        String requirements = "main 4.21.0 yaml simple forbidden " + CITRUS_VERSION + ' '
                              + String.join(" ", CITRUS_DEPENDENCIES) + " resolved";
        ShipController controller = controller(state);
        ShipRunView discovery = startDiscovery(controller, project, requirements);
        SourceRef source = contextSource(state, discovery);
        Path catalog = Files.createDirectory(temporaryDirectory.resolve("review-gap-catalog"));
        ShipRunView continued = controller.submitStageResult(
                discovery.runId(),
                discovery.eventDigest(),
                encoded(result(
                        discovery.activeRequest(),
                        StageResult.Outcome.NEEDS_DISCOVERY,
                        readyLedger(1, GapReviewStatus.NOT_RUN, source),
                        null,
                        List.of(new CatalogSubject(CatalogSubject.Kind.COMPONENT, "direct")))),
                CatalogTestVerifier.mainSnapshot(catalog));
        ShipRunView reviewing = controller.submitStageResult(
                continued.runId(),
                continued.eventDigest(),
                encoded(result(
                        continued.activeRequest(),
                        StageResult.Outcome.COMPLETED,
                        readyLedger(2, GapReviewStatus.NOT_RUN, source),
                        null,
                        List.of())));

        ShipRunView reopened = controller.submitStageResult(
                reviewing.runId(),
                reviewing.eventDigest(),
                encoded(result(
                        reviewing.activeRequest(),
                        StageResult.Outcome.NEEDS_DISCOVERY,
                        readyLedger(3, GapReviewStatus.NOT_RUN, source),
                        null,
                        List.of())));

        assertEquals(ShipState.DISCOVERY_ANALYZING, reopened.state());
        assertEquals(ShipStage.DISCOVERY, reopened.activeRequest().stage());
        assertEquals(5, reopened.activeRequest().attempt());
        assertNull(reopened.catalogEvidence());
        assertNull(reopened.requirementsDigest());
        assertEquals(reopened, controller(state).status(reopened.runId()));
    }

    @Test
    void retryKeepsTheInteractionHistoryAndBindsFailureContextToTheNextAttempt() throws Exception {
        Path project = Files.createDirectory(temporaryDirectory.resolve("retry-project"))
                .toAbsolutePath().normalize();
        Path state = temporaryDirectory.resolve("retry-state").toAbsolutePath().normalize();
        ShipController controller = controller(state);
        String requirements = "main 4.21.0 yaml simple forbidden " + CITRUS_VERSION + ' '
                              + String.join(" ", CITRUS_DEPENDENCIES) + " resolved";
        ShipRunView discovery = startDiscovery(controller, project, requirements);
        StageRequest request = discovery.activeRequest();
        StageResult failure = new StageResult(
                StageResult.SCHEMA_VERSION,
                request.runId(),
                request.stage(),
                request.attemptId(),
                request.challenge(),
                request.inputDigest(),
                StageResult.Outcome.FAILED,
                null,
                null,
                List.of(),
                List.of(),
                null,
                "worker-unavailable",
                "Worker exited before producing a result");

        ShipRunView failed = controller.submitStageResult(
                discovery.runId(), discovery.eventDigest(), encoded(failure));
        assertEquals(ShipState.DISCOVERY_FAILED_RETRYABLE, failed.state());
        assertEquals(discovery.interactionBundle(), failed.interactionBundle());

        ShipRunView retry = controller.retryStage(failed.runId(), failed.eventDigest());
        assertEquals(ShipState.DISCOVERY_ANALYZING, retry.state());
        assertEquals(3, retry.activeRequest().attempt());
        assertEquals("worker-unavailable", retry.activeRequest().failureCode());
        assertEquals(
                "Worker exited before producing a result",
                retry.activeRequest().failureMessage());
        assertEquals(failed.interactionBundle(), retry.interactionBundle());

        DecisionLedger ledger = readyLedger(
                1, GapReviewStatus.NOT_RUN, contextSource(state, retry));
        Path catalog = Files.createDirectory(temporaryDirectory.resolve("retry-catalog"));
        ShipRunView continued = controller.submitStageResult(
                retry.runId(),
                retry.eventDigest(),
                encoded(result(
                        retry.activeRequest(),
                        StageResult.Outcome.NEEDS_DISCOVERY,
                        ledger,
                        null,
                        List.of(new CatalogSubject(CatalogSubject.Kind.COMPONENT, "direct")))),
                CatalogTestVerifier.mainSnapshot(catalog));
        assertNull(continued.activeRequest().failureCode());
        assertNull(continued.activeRequest().failureMessage());
        assertEquals(continued, controller(state).status(continued.runId()));
    }

    @Test
    void retryWithoutAFailedAttemptUsesTheCodedLifecycleBoundary() throws Exception {
        Path project = Files.createDirectory(temporaryDirectory.resolve("invalid-retry-project"))
                .toAbsolutePath().normalize();
        Path state = temporaryDirectory.resolve("invalid-retry-state")
                .toAbsolutePath().normalize();
        ShipController controller = controller(state);
        ShipRunView created = controller.start(prepared(project));

        ShipControllerException failure = assertThrows(
                ShipControllerException.class,
                () -> controller.retryStage(created.runId(), created.eventDigest()));

        assertEquals("lifecycle-boundary", failure.code());
        assertEquals(created, controller.status(created.runId()));
    }

    private static ShipController controller(Path state) {
        return new ShipController(
                state,
                Clock.fixed(Instant.parse("2026-07-22T00:00:00Z"), ZoneOffset.UTC));
    }

    private static ShipController.PreparedRun prepared(Path project) {
        return new ShipController.PreparedRun(project, "test-adapter");
    }

    private static ShipRunView startDiscovery(
            ShipController controller, Path project, String context)
            throws Exception {
        ShipRunView created = controller.start(prepared(project));
        ShipRunView resolving = controller.beginContextResolution(
                created.runId(), created.eventDigest(), new InitialContextRequest.Text(context));
        ShipRunView recorded = controller.continueContextResolution(
                created.runId(), resolving.eventDigest(), List.of());
        return controller.startDiscovery(recorded.runId(), recorded.eventDigest());
    }

    private static SourceRef contextSource(Path state, ShipRunView run) throws Exception {
        InitialContext context = ShipJson.mapper().readValue(
                ShipBlobStore.open(state, run.runId())
                        .readBytes(run.context(), ShipJson.MAX_DOCUMENT_BYTES),
                InitialContext.class);
        InitialContext.Source source = context.sources().get(0);
        return new SourceRef(
                source.id(), source.provenance(), source.digest(), source.content());
    }

    private static StageResult result(
            StageRequest request,
            StageResult.Outcome outcome,
            DecisionLedger ledger,
            DecisionLedger.Question question,
            List<CatalogSubject> catalogRequests) {
        return new StageResult(
                StageResult.SCHEMA_VERSION,
                request.runId(),
                request.stage(),
                request.attemptId(),
                request.challenge(),
                request.inputDigest(),
                outcome,
                ledger,
                question,
                catalogRequests,
                List.of(),
                null,
                null,
                null);
    }

    private static byte[] encoded(StageResult result) throws Exception {
        return ShipJson.mapper().writeValueAsBytes(result);
    }

    private static List<String> blobInventory(Path state, String runId) throws Exception {
        try (Stream<Path> files = Files.list(state.resolve(runId).resolve("blobs"))) {
            return files.sorted()
                    .map(path -> path.getFileName() + ":" + size(path))
                    .toList();
        }
    }

    private static long size(Path path) {
        try {
            return Files.size(path);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static DecisionLedger readyLedger(
            long revision, GapReviewStatus reviewStatus, SourceRef source) {
        List<Category> categories = LedgerValidator.REQUIRED_CATEGORIES.stream()
                .sorted()
                .map(category -> new Category(
                        category, LedgerStatus.RESOLVED, null, List.of(source)))
                .toList();
        List<DecisionLedger.Entry> decisions = LedgerValidator.REQUIRED_CATEGORIES.stream()
                .sorted()
                .map(category -> new DecisionLedger.Entry(
                        "decision-" + category,
                        category,
                        value(category),
                        LedgerStatus.RESOLVED,
                        List.of(source),
                        null))
                .toList();
        RequirementsPolicy policy = new RequirementsPolicy(
                "main",
                CatalogTestVerifier.CAMEL_VERSION,
                null,
                null,
                "yaml",
                "simple",
                CITRUS_VERSION,
                CITRUS_DEPENDENCIES,
                JavaPolicy.FORBIDDEN,
                List.of(),
                List.of(new RouteContract(
                        "orders",
                        "src/main/resources/routes/orders.camel.yaml",
                        "test/orders.camel.it.yaml")),
                true,
                true);
        return new DecisionLedger(
                DecisionLedger.SCHEMA_VERSION,
                revision,
                List.of(),
                decisions,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                categories,
                List.of(),
                reviewStatus,
                policy);
    }

    private static DecisionLedger questionLedger(
            SourceRef source, DecisionLedger.Question question) {
        DecisionLedger.Entry runtime = new DecisionLedger.Entry(
                question.openItemId(),
                "runtime",
                null,
                LedgerStatus.NEEDS_USER_DECISION,
                List.of(),
                null);
        DecisionLedger.Entry dsl = new DecisionLedger.Entry(
                "dsl-choice",
                "dsl",
                "yaml",
                LedgerStatus.RESOLVED,
                List.of(source),
                null);
        return new DecisionLedger(
                DecisionLedger.SCHEMA_VERSION,
                1,
                List.of(),
                List.of(runtime, dsl),
                List.of(),
                List.of(),
                List.of(),
                List.of(question),
                List.of(
                        new Category("runtime", LedgerStatus.NEEDS_USER_DECISION, null, List.of()),
                        new Category("dsl", LedgerStatus.RESOLVED, null, List.of(source))),
                List.of(runtime.id()),
                GapReviewStatus.NOT_RUN,
                null);
    }

    private static DecisionLedger.Question capacityQuestion(int index) {
        String prompt = "p" + "\u0001".repeat(Interaction.MAX_PROMPT_CHARS - 1);
        List<String> options = java.util.stream.IntStream.range(0, 24)
                .mapToObj(option -> {
                    String prefix = "option-" + option + '-';
                    return prefix + "\u0001".repeat(Interaction.MAX_OPTION_CHARS - prefix.length());
                })
                .toList();
        return new DecisionLedger.Question(
                "capacity-question-" + index,
                "capacity-item-" + index,
                prompt,
                options,
                options.get(0),
                LedgerStatus.OPEN);
    }

    private static DecisionLedger capacityLedger(
            long revision,
            SourceRef context,
            List<SourceRef> answers,
            int currentQuestion) {
        List<DecisionLedger.Entry> decisions = new ArrayList<>();
        decisions.add(new DecisionLedger.Entry(
                "dsl-choice", "dsl", "yaml", LedgerStatus.RESOLVED, List.of(context), null));
        for (int index = 0; index < 4; index++) {
            boolean resolved = index < currentQuestion;
            decisions.add(new DecisionLedger.Entry(
                    "capacity-item-" + index,
                    "flows",
                    resolved ? "a" : null,
                    resolved ? LedgerStatus.RESOLVED : LedgerStatus.NEEDS_USER_DECISION,
                    resolved ? List.of(answers.get(index)) : List.of(),
                    null));
        }
        List<String> blockers = java.util.stream.IntStream.range(currentQuestion, 4)
                .mapToObj(index -> "capacity-item-" + index)
                .toList();
        return new DecisionLedger(
                DecisionLedger.SCHEMA_VERSION,
                revision,
                List.of(),
                decisions,
                List.of(),
                List.of(),
                List.of(),
                List.of(capacityQuestion(currentQuestion)),
                List.of(
                        new Category("dsl", LedgerStatus.RESOLVED, null, List.of(context)),
                        new Category("flows", LedgerStatus.NEEDS_USER_DECISION, null, List.of())),
                blockers,
                GapReviewStatus.NOT_RUN,
                null);
    }

    private static SourceRef capacityAnswerSource(int ordinal, String response) {
        String digest = ShipDigest.sha256(response.getBytes(StandardCharsets.UTF_8));
        return new SourceRef(
                "interaction-" + ordinal + '-' + digest.substring("sha256:".length()),
                "controller:interaction-bundle#exchanges/" + ordinal
                                                                                       + "/discovery/answer/response",
                digest,
                "a");
    }

    private static String value(String category) {
        return switch (category) {
            case "runtime" -> "main";
            case "camel-version" -> CatalogTestVerifier.CAMEL_VERSION;
            case "dsl" -> "yaml";
            case "expression-language" -> "simple";
            case "java-policy" -> "forbidden";
            case "citrus-version" -> CITRUS_VERSION;
            case "citrus-dependencies" -> String.join(", ", CITRUS_DEPENDENCIES);
            default -> "resolved";
        };
    }
}
