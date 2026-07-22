package io.github.luigidemasi.camelkit.ship.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.luigidemasi.camelkit.ship.ShipDigest;
import io.github.luigidemasi.camelkit.ship.artifact.ArtifactManifest;
import io.github.luigidemasi.camelkit.ship.artifact.ArtifactManifest.DeclaredArtifact;
import io.github.luigidemasi.camelkit.ship.artifact.ArtifactManifest.RouteArtifact;
import io.github.luigidemasi.camelkit.ship.artifact.ArtifactManifest.TestArtifact;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogEvidenceSet;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogSubject;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogTestVerifier;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogUsageRecord;
import io.github.luigidemasi.camelkit.ship.catalog.ShipCatalogService.Snapshot;
import io.github.luigidemasi.camelkit.ship.context.InitialContext;
import io.github.luigidemasi.camelkit.ship.context.InitialContextRequest;
import io.github.luigidemasi.camelkit.ship.controller.ShipAttemptFactory.AttemptInputs;
import io.github.luigidemasi.camelkit.ship.controller.ShipBlobStore.BlobReference;
import io.github.luigidemasi.camelkit.ship.evidence.CommandEvidence;
import io.github.luigidemasi.camelkit.ship.evidence.CommandEvidence.SandboxIdentity;
import io.github.luigidemasi.camelkit.ship.evidence.EvidenceCommand;
import io.github.luigidemasi.camelkit.ship.evidence.EvidenceRunner;
import io.github.luigidemasi.camelkit.ship.evidence.JvmPayloadRequest;
import io.github.luigidemasi.camelkit.ship.evidence.launcher.ShipMainPackageMain;
import io.github.luigidemasi.camelkit.ship.ledger.DecisionLedger;
import io.github.luigidemasi.camelkit.ship.ledger.DecisionLedger.Category;
import io.github.luigidemasi.camelkit.ship.ledger.DecisionLedger.Entry;
import io.github.luigidemasi.camelkit.ship.ledger.DecisionLedger.GapReviewStatus;
import io.github.luigidemasi.camelkit.ship.ledger.DecisionLedger.JavaPolicy;
import io.github.luigidemasi.camelkit.ship.ledger.DecisionLedger.Question;
import io.github.luigidemasi.camelkit.ship.ledger.DecisionLedger.RequirementsPolicy;
import io.github.luigidemasi.camelkit.ship.ledger.DecisionLedger.RouteContract;
import io.github.luigidemasi.camelkit.ship.ledger.DecisionLedger.SourceRef;
import io.github.luigidemasi.camelkit.ship.ledger.LedgerStatus;
import io.github.luigidemasi.camelkit.ship.ledger.LedgerValidator;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.DesignChallenge;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.DesignDecision;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.DesignResponse;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.DiscoveryChallenge;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.PlanChallenge;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.PlanDecision;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.PlanResponse;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.WaiverChallenge;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.WaiverDecision;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.WaiverResponse;
import io.github.luigidemasi.camelkit.ship.protocol.ProducedArtifact;
import io.github.luigidemasi.camelkit.ship.protocol.ShipStage;
import io.github.luigidemasi.camelkit.ship.protocol.StageRequest;
import io.github.luigidemasi.camelkit.ship.protocol.StageResult;
import io.github.luigidemasi.camelkit.ship.security.ProjectEvidenceFiles;
import io.github.luigidemasi.camelkit.ship.security.ProjectSnapshot;
import io.github.luigidemasi.camelkit.ship.security.StagedArtifactSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledOnOs(OS.LINUX)
class ShipRunProjectorTest {

    private static final Instant NOW = Instant.parse("2026-07-22T00:00:00Z");
    private static final String SYNTACTIC_MAC = "hmac-sha256:" + "0".repeat(64);
    private static final String CITRUS_VERSION = "5.0.0-M2";
    private static final List<String> CITRUS_DEPENDENCIES = List.of(
            "org.citrusframework:citrus-camel:5.0.0-M2",
            "org.citrusframework:citrus-junit-jupiter:5.0.0-M2",
            "org.citrusframework:citrus-yaml:5.0.0-M2");

    @TempDir
    Path temporaryDirectory;

    @Test
    void protectedControllerPassFlowPublishesEvidenceBoundCandidate() throws Exception {
        ProtectedControllerFlow flow = executionRunningControllerFlow("protected-controller-pass", true);
        ShipRunView executed = flow.broker().submit(
                flow.view().runId(), flow.view().eventDigest(), flow.catalogSnapshot());
        ShipRunView validating = flow.controller().startValidation(
                executed.runId(), executed.eventDigest());
        ShipRunView passed = flow.broker().submit(
                validating.runId(), validating.eventDigest(), flow.catalogSnapshot());

        ShipRunView completed = flow.controller().complete(
                passed.runId(), passed.eventDigest());

        assertEquals(ShipState.COMPLETED, completed.state());
        assertEquals(4, flow.backend().closedCustodies());
        assertNotNull(completed.artifactManifest());
        assertNotNull(completed.catalogUsage());
        assertNotNull(completed.candidateSnapshot());
        assertNotNull(completed.validationReport());
        assertNotNull(completed.stamp());
        assertEquals(routeYaml(), Files.readString(
                completed.projectRoot().resolve("src/main/resources/routes/orders.camel.yaml")));
        assertEquals(testYaml(), Files.readString(
                completed.projectRoot().resolve("test/orders.camel.it.yaml")));

        FileShipEventStore events = FileShipEventStore.open(
                stateRoot(flow), completed.runId());
        ShipEvent terminal = events.replay().get(events.replay().size() - 1);
        ShipStoredEventCodec.StoredEvent stored = ShipStoredEventCodec.decode(terminal);
        ShipEventPayloads.StampRecorded recorded
                = (ShipEventPayloads.StampRecorded) stored.data();
        assertEquals(ShipEventType.RUN_COMPLETED, terminal.type());
        assertEquals(completed.stamp(), recorded.stamp());
        assertNotNull(recorded.publishedSnapshot());
        ShipBlobStore blobs = ShipBlobStore.open(stateRoot(flow), completed.runId());
        ShipStamp stamp = ShipJson.mapper().readValue(
                blobs.readBytes(completed.stamp(), ShipJson.MAX_DOCUMENT_BYTES),
                ShipStamp.class);
        assertEquals(ShipStamp.Status.PASS, stamp.status());
        assertTrue(blobs.readBytes(
                recorded.publishedSnapshot(), ShipJson.MAX_DOCUMENT_BYTES).length > 0);
    }

    @Test
    void protectedControllerWaivableFlowStopsAtProtectedWaiverBoundary() throws Exception {
        ProtectedControllerFlow flow = executionRunningControllerFlow("protected-controller-waivable", false);
        ShipRunView executed = flow.broker().submit(
                flow.view().runId(), flow.view().eventDigest(), flow.catalogSnapshot());
        ShipRunView validating = flow.controller().startValidation(
                executed.runId(), executed.eventDigest());

        ShipRunView eligible = flow.broker().submit(
                validating.runId(), validating.eventDigest(), flow.catalogSnapshot());

        assertEquals(ShipState.WAIVER_ELIGIBLE, eligible.state());
        assertNotNull(eligible.validationReport());
        assertFalse(eligible.evidenceById().get("citrus-integration-test-001") == null);
        assertEquals(4, flow.backend().closedCustodies());
        assertFalse(java.util.Arrays.stream(ShipController.class.getDeclaredMethods())
                .anyMatch(method -> method.getName().equals("requestWaiver")
                        || method.getName().equals("recordWaiver")));
    }

    @Test
    void definiteAppendFailureRollsBackExecutionCandidate() throws Exception {
        ProtectedControllerFlow flow = executionRunningControllerFlow("append-definite", true);
        ShipController faulting = faultingController(flow, AppendFault.UNCHANGED_HEAD);
        ShipProtectedWorkerBroker broker = new ShipProtectedWorkerBroker(faulting, flow.backend());

        IOException failure = assertThrows(
                IOException.class,
                () -> broker.submit(
                        flow.view().runId(), flow.view().eventDigest(), flow.catalogSnapshot()));

        assertEquals("injected append failure", failure.getMessage());
        assertEquals(ShipState.EXECUTE_RUNNING,
                flow.controller().status(flow.view().runId()).state());
        assertTrue(publicationTransactions(flow).isEmpty());
    }

    @Test
    void committedAppendReportedAsFailureFinishesExecutionCandidate() throws Exception {
        ProtectedControllerFlow flow = executionRunningControllerFlow("append-successor", true);
        ShipController faulting = faultingController(flow, AppendFault.SUCCESSOR_HEAD);
        ShipProtectedWorkerBroker broker = new ShipProtectedWorkerBroker(faulting, flow.backend());

        ShipRunView accepted = broker.submit(
                flow.view().runId(), flow.view().eventDigest(), flow.catalogSnapshot());

        assertEquals(ShipState.EXECUTE_VALIDATED, accepted.state());
        assertEquals(1, publicationTransactions(flow).size());
        assertEquals(accepted.candidateDirectory().getParent(),
                publicationTransactions(flow).get(0));
    }

    @Test
    void unresolvedAppendRetainsCandidateUntilStatusReconcilesIt() throws Exception {
        ProtectedControllerFlow flow = executionRunningControllerFlow("append-unresolved", true);
        ShipController faulting = faultingController(flow, AppendFault.HEAD_READ_FAILURE);
        ShipProtectedWorkerBroker broker = new ShipProtectedWorkerBroker(faulting, flow.backend());

        IOException failure = assertThrows(
                IOException.class,
                () -> broker.submit(
                        flow.view().runId(), flow.view().eventDigest(), flow.catalogSnapshot()));

        assertEquals("injected append failure", failure.getMessage());
        assertEquals(1, failure.getSuppressed().length);
        assertEquals(1, publicationTransactions(flow).size());
        ShipRunView recovered = flow.controller().status(flow.view().runId());
        assertEquals(ShipState.EXECUTE_RUNNING, recovered.state());
        assertTrue(publicationTransactions(flow).isEmpty());
    }

    @Test
    void unexpectedAuthenticatedHeadRetainsCandidateForExplicitReconciliation() throws Exception {
        ProtectedControllerFlow flow = executionRunningControllerFlow("append-unexpected", true);
        ShipController faulting = faultingController(flow, AppendFault.UNEXPECTED_AUTHENTICATED_HEAD);
        ShipProtectedWorkerBroker broker = new ShipProtectedWorkerBroker(faulting, flow.backend());

        ShipControllerException failure = assertThrows(
                ShipControllerException.class,
                () -> broker.submit(
                        flow.view().runId(), flow.view().eventDigest(), flow.catalogSnapshot()));

        assertEquals("event-append-outcome-unknown", failure.code());
        assertEquals(1, publicationTransactions(flow).size());
        FileShipEventStore events = FileShipEventStore.open(
                stateRoot(flow), flow.view().runId());
        List<ShipEvent> authenticated = events.replay();
        assertEquals(ShipEventType.STAMP_STARTED,
                authenticated.get(authenticated.size() - 1).type());
        ShipProjectPublisher.reconcileCandidates(
                ShipBlobStore.open(stateRoot(flow), flow.view().runId()), authenticated);
        assertTrue(publicationTransactions(flow).isEmpty());
    }

    @Test
    void replayReconstructsTheExactPersistedAttemptHead() throws Exception {
        Fixture fixture = fixture("restart");
        BlobReference input = fixture.blobs.writeBytes(
                "initial-context-request",
                ShipInitialContextRequestCodec.encode(new InitialContextRequest.Text("input")));
        ShipAuthorityCommand command = ShipAuthorityCommand.value(
                ShipEventType.CONTEXT_RESOLUTION_STARTED, input.digest());
        ShipRun successor = append(
                fixture,
                fixture.authority,
                command,
                new ShipEventPayloads.ContextResolutionStarted(input));

        ShipRunView restored = fixture.projector().replay();

        assertEquals(successor.id(), restored.authority().id());
        assertEquals(successor.state(), restored.state());
        assertEquals(successor.revision(), restored.revision());
        assertEquals(successor.head(), restored.authority().head());
        assertEquals(
                successor.head(), ShipLifecycleReducer.attempt(restored.authority()).authorityHead());
        assertEquals(
                ShipLifecycleReducer.attempt(successor).input(),
                ShipLifecycleReducer.attempt(restored.authority()).input());
    }

    @Test
    void replayRestoresControllerDataOnlyAfterTheMatchingAuthorityTransition() throws Exception {
        Fixture fixture = fixture("context");
        BlobReference input = fixture.blobs.writeBytes(
                "initial-context-request",
                ShipInitialContextRequestCodec.encode(new InitialContextRequest.Text("input")));
        fixture.authority = append(
                fixture,
                fixture.authority,
                ShipAuthorityCommand.value(ShipEventType.CONTEXT_RESOLUTION_STARTED, input.digest()),
                new ShipEventPayloads.ContextResolutionStarted(input));
        InitialContext contextValue = InitialContext.fromSources(
                java.util.List.of(InitialContext.userText("context")));
        BlobReference context = fixture.blobs.writeBytes(
                "initial-context", ShipJson.mapper().writeValueAsBytes(contextValue));
        BlobReference interactions = fixture.interactions.create(
                fixture.blobs, fixture.authority.id().toString(), contextValue.digest());
        fixture.authority = append(
                fixture,
                fixture.authority,
                ShipAuthorityCommand.value(ShipEventType.CONTEXT_RECORDED, context.digest()),
                new ShipEventPayloads.ContextRecorded(context, interactions));

        ShipRunView restored = fixture.projector().replay();

        assertEquals(ShipState.CONTEXT_RECORDED, restored.state());
        assertEquals(context, restored.context());
        assertEquals(3, fixture.events.replay().size());
    }

    @Test
    void authenticatedButLifecycleIllegalEventFailsClosed() throws Exception {
        Fixture fixture = fixture("illegal");
        ShipAuthorityCommand command = ShipAuthorityCommand.empty(ShipEventType.DESIGN_STARTED);
        AuthorityHeadId fabricatedHead = AuthorityHeadId.create();
        BlobReference request = fixture.blobs.writeBytes(
                "stage-request", "{}".getBytes(StandardCharsets.UTF_8));
        fixture.events.appendIfLatest(
                fixture.events.currentHead(),
                new ShipEventDraft(
                        ShipEventType.DESIGN_STARTED,
                        ShipState.DESIGN_RUNNING,
                        fixture.authority.head(),
                        fabricatedHead,
                        NOW.plusSeconds(1),
                        ShipStoredEventCodec.encode(
                                command,
                                new ShipEventPayloads.StageStarted(
                                        io.github.luigidemasi.camelkit.ship.protocol.ShipStage.DESIGN,
                                        request,
                                        temporaryDirectory.resolve("output")
                                                .toAbsolutePath()
                                                .normalize()
                                                .toString()))));

        Exception failure = assertThrows(Exception.class, () -> fixture.projector().replay());
        assertTrue(failure.getMessage().contains("DESIGN_STARTED")
                || failure.getMessage().contains("design-started"));
    }

    @Test
    void authenticatedDataCannotSwapTheAuthorityBoundContextInput() throws Exception {
        Fixture fixture = fixture("swap");
        BlobReference authorityInput = fixture.blobs.writeBytes(
                "initial-context-request",
                ShipInitialContextRequestCodec.encode(new InitialContextRequest.Text("one")));
        BlobReference swappedInput = fixture.blobs.writeBytes(
                "initial-context-request",
                ShipInitialContextRequestCodec.encode(new InitialContextRequest.Text("two")));
        ShipAuthorityCommand command = ShipAuthorityCommand.value(
                ShipEventType.CONTEXT_RESOLUTION_STARTED, authorityInput.digest());
        ShipRun successor = command.apply(fixture.authority);
        fixture.events.appendIfLatest(
                fixture.events.currentHead(),
                draft(
                        command,
                        new ShipEventPayloads.ContextResolutionStarted(swappedInput),
                        fixture.authority,
                        successor));

        Exception failure = assertThrows(Exception.class, () -> fixture.projector().replay());
        assertTrue(failure.getMessage().contains("exact context input"));
    }

    @Test
    void semanticPreflightRejectsInvalidDataWithoutChangingAuthenticatedHistory() throws Exception {
        Fixture fixture = fixture("preflight");
        BlobReference authorityInput = fixture.blobs.writeBytes(
                "initial-context-request",
                ShipInitialContextRequestCodec.encode(new InitialContextRequest.Text("one")));
        BlobReference swappedInput = fixture.blobs.writeBytes(
                "initial-context-request",
                ShipInitialContextRequestCodec.encode(new InitialContextRequest.Text("two")));
        ShipAuthorityCommand command = ShipAuthorityCommand.value(
                ShipEventType.CONTEXT_RESOLUTION_STARTED, authorityInput.digest());
        ShipRun successor = command.apply(fixture.authority);
        ShipEventHead headBefore = fixture.events.currentHead();
        int eventCountBefore = fixture.events.replay().size();
        ShipRunView viewBefore = fixture.projector().replay();

        Exception failure = assertThrows(
                Exception.class,
                () -> fixture.projector().preflight(
                        command,
                        new ShipEventPayloads.ContextResolutionStarted(swappedInput),
                        successor));

        assertTrue(failure.getMessage().contains("exact context input"));
        assertEquals(headBefore, fixture.events.currentHead());
        assertEquals(eventCountBefore, fixture.events.replay().size());
        ShipRunView viewAfter = fixture.projector().replay();
        assertEquals(viewBefore.authority(), viewAfter.authority());
        assertEquals(viewBefore.eventDigest(), viewAfter.eventDigest());
    }

    @Test
    void creationPreflightReusesTheExactRunCreationChecks() throws Exception {
        Fixture fixture = fixture("creation-preflight");
        ShipStoredEventCodec.StoredEvent first = ShipStoredEventCodec.decode(
                fixture.events.replay().get(0));
        ShipEventPayloads.RunCreated created = (ShipEventPayloads.RunCreated) first.data();
        ShipEventPayloads.RunCreated invalid = new ShipEventPayloads.RunCreated(
                created.projectRoot(),
                "INVALID",
                created.nativeSessionEvidence(),
                created.baselineSnapshot(),
                created.sourceSnapshot(),
                created.projectSourceManifest(),
                created.sourceDirectory());
        ShipEventHead headBefore = fixture.events.currentHead();

        Exception failure = assertThrows(
                Exception.class,
                () -> fixture.projector().preflightCreated(fixture.authority, invalid));

        assertTrue(failure.getMessage().contains("adapter ID"));
        assertEquals(headBefore, fixture.events.currentHead());
        assertEquals(fixture.authority, fixture.projector().replay().authority());
    }

    @Test
    void questionPreflightRejectsAChallengeThatDoesNotMatchTheProtectedResult()
            throws Exception {
        Path stateRoot = temporaryDirectory.resolve("question-state").toAbsolutePath().normalize();
        Path project = Files.createDirectory(temporaryDirectory.resolve("question-project"))
                .toAbsolutePath()
                .normalize();
        ShipController controller = new ShipController(
                stateRoot, Clock.fixed(NOW, ZoneOffset.UTC));
        ShipRunView created = controller.start(
                new ShipController.PreparedRun(project, "test-adapter"));
        ShipRunView resolving = controller.beginContextResolution(
                created.runId(),
                created.eventDigest(),
                new InitialContextRequest.Text("Use YAML. Choose runtime."));
        ShipRunView contextRecorded = controller.continueContextResolution(
                created.runId(), resolving.eventDigest(), List.of());
        ShipRunView discovery = controller.startDiscovery(
                created.runId(), contextRecorded.eventDigest());
        StageRequest request = discovery.activeRequest();
        ShipBlobStore blobs = ShipBlobStore.open(stateRoot, created.runId());
        InitialContext context = ShipJson.mapper().readValue(
                blobs.readBytes(discovery.context(), ShipJson.MAX_DOCUMENT_BYTES),
                InitialContext.class);
        InitialContext.Source source = context.sources().get(0);
        SourceRef sourceRef = new SourceRef(
                source.id(), source.provenance(), source.digest(), "YAML");
        Entry runtime = new Entry(
                "runtime-choice",
                "runtime",
                null,
                LedgerStatus.NEEDS_USER_DECISION,
                List.of(),
                null);
        Question question = new Question(
                "question-runtime",
                runtime.id(),
                "Which runtime?",
                List.of("Camel Main", "Quarkus"),
                "Camel Main",
                LedgerStatus.OPEN);
        DecisionLedger ledger = new DecisionLedger(
                DecisionLedger.SCHEMA_VERSION,
                1,
                List.of(),
                List.of(
                        runtime,
                        new Entry(
                                "dsl-choice",
                                "dsl",
                                "yaml",
                                LedgerStatus.RESOLVED,
                                List.of(sourceRef),
                                null)),
                List.of(),
                List.of(),
                List.of(),
                List.of(question),
                List.of(
                        new Category(
                                "runtime",
                                LedgerStatus.NEEDS_USER_DECISION,
                                null,
                                List.of()),
                        new Category("dsl", LedgerStatus.RESOLVED, null, List.of(sourceRef))),
                List.of(runtime.id()),
                DecisionLedger.GapReviewStatus.NOT_RUN,
                null);
        StageResult result = new StageResult(
                StageResult.SCHEMA_VERSION,
                request.runId(),
                request.stage(),
                request.attemptId(),
                request.challenge(),
                request.inputDigest(),
                StageResult.Outcome.NEEDS_USER_INPUT,
                ledger,
                question,
                List.of(),
                List.of(),
                null,
                null,
                null);
        BlobReference resultReference = fixtureBlob(blobs, "stage-result", result);
        BlobReference ledgerReference = fixtureBlob(blobs, "decision-ledger", ledger);
        ShipInteractionSigner signer = ShipInteractionSigner.open(
                stateRoot.resolve(created.runId()));
        String nonce = signer.nonce();
        String prompt = "Forged prompt";
        DiscoveryChallenge challenge = new DiscoveryChallenge(
                Interaction.SCHEMA_VERSION,
                created.runId(),
                ledger.revision(),
                question.id(),
                question.openItemId(),
                prompt,
                question.options(),
                question.recommendation(),
                nonce,
                signer.sign(Interaction.discoveryChallengeMacFields(
                        created.runId(),
                        ledger.revision(),
                        question.id(),
                        question.openItemId(),
                        prompt,
                        question.options(),
                        question.recommendation(),
                        nonce)));
        ShipInteractionBundleService interactionService = new ShipInteractionBundleService(signer);
        BlobReference interactionBundle = interactionService.record(
                blobs, discovery.interactionBundle(), challenge);
        FileShipEventStore events = FileShipEventStore.open(stateRoot, created.runId());
        ShipRunProjector projector = new ShipRunProjector(events, blobs, interactionService);
        ShipAuthorityCommand command = ShipAuthorityCommand.value(
                ShipEventType.DISCOVERY_QUESTION_PRESENTED, question.id());
        ShipRun successor = command.apply(discovery.authority());
        ShipEventHead headBefore = events.currentHead();

        Exception failure = assertThrows(
                Exception.class,
                () -> projector.preflight(
                        command,
                        new ShipEventPayloads.DiscoveryQuestionPresented(
                                discovery.activeRequestReference(),
                                resultReference,
                                ledgerReference,
                                challenge,
                                interactionBundle),
                        successor));

        assertTrue(failure.getMessage().contains("exact worker question"));
        assertEquals(headBefore, events.currentHead());
        assertEquals(discovery.authority(), projector.replay().authority());
    }

    @Test
    void retryableWorkerFailurePreservesTheSignedInteractionBundle() throws Exception {
        Path stateRoot = temporaryDirectory.resolve("failure-state").toAbsolutePath().normalize();
        Path project = Files.createDirectory(temporaryDirectory.resolve("failure-project"))
                .toAbsolutePath()
                .normalize();
        ShipController controller = new ShipController(
                stateRoot, Clock.fixed(NOW, ZoneOffset.UTC));
        ShipRunView created = controller.start(
                new ShipController.PreparedRun(project, "test-adapter"));
        ShipRunView resolving = controller.beginContextResolution(
                created.runId(),
                created.eventDigest(),
                new InitialContextRequest.Text("context"));
        ShipRunView contextRecorded = controller.continueContextResolution(
                created.runId(), resolving.eventDigest(), List.of());
        ShipRunView discovery = controller.startDiscovery(
                created.runId(), contextRecorded.eventDigest());
        StageRequest request = discovery.activeRequest();
        StageResult failed = new StageResult(
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
                "worker-failed",
                "Worker failed safely");

        ShipRunView retryable = controller.submitStageResult(
                created.runId(),
                discovery.eventDigest(),
                ShipJson.mapper().writeValueAsBytes(failed));

        assertEquals(ShipState.DISCOVERY_FAILED_RETRYABLE, retryable.state());
        assertEquals(discovery.interactionBundle(), retryable.interactionBundle());
        assertEquals(discovery.activeRequestReference(), retryable.failedRequest());
        assertEquals(null, retryable.activeRequest());
        assertEquals(retryable, controller.status(created.runId()));
    }

    @Test
    void inputInvalidationsClearTheExactSupersededProjectionAndRetryContext()
            throws Exception {
        DurableFlow flow = planValidatedFlow("input-invalidation");
        BlobReference requirements = flow.view.ledger();
        BlobReference catalog = flow.view.catalogEvidence();
        BlobReference design = flow.view.design();

        startChangedInputs(
                flow,
                ShipEventType.PLAN_INPUTS_CHANGED,
                ShipStage.PLAN,
                new AttemptInputs(
                        flow.view.context(),
                        requirements,
                        catalog,
                        design,
                        null,
                        flow.view.interactionBundle(),
                        null,
                        null,
                        null,
                        List.of(),
                        null,
                        null));
        assertEquals(ShipState.PLAN_RUNNING, flow.view.state());
        assertEquals(design, flow.view.design());
        assertNull(flow.view.plan());

        failActiveAttempt(flow);
        assertEquals(ShipState.PLAN_FAILED_RETRYABLE, flow.view.state());
        assertEquals("worker-failed", flow.view.failureCode());
        startChangedInputs(
                flow,
                ShipEventType.PLAN_INPUTS_CHANGED,
                ShipStage.PLAN,
                new AttemptInputs(
                        flow.view.context(),
                        requirements,
                        catalog,
                        design,
                        null,
                        flow.view.interactionBundle(),
                        null,
                        null,
                        null,
                        List.of(),
                        null,
                        null));
        assertNull(flow.view.failureCode());
        assertNull(flow.view.failureMessage());
        assertNull(flow.view.failedRequest());
        acceptArtifact(flow, "plan", ShipEventType.PLAN_VALIDATED);

        startChangedInputs(
                flow,
                ShipEventType.DESIGN_INPUTS_CHANGED,
                ShipStage.DESIGN,
                new AttemptInputs(
                        flow.view.context(),
                        requirements,
                        catalog,
                        null,
                        null,
                        flow.view.interactionBundle(),
                        null,
                        null,
                        null,
                        List.of(),
                        null,
                        null));
        assertEquals(requirements, flow.view.ledger());
        assertEquals(catalog, flow.view.catalogEvidence());
        assertNull(flow.view.design());
        assertNull(flow.view.designDigest());
        assertNull(flow.view.plan());
        acceptArtifact(flow, "design", ShipEventType.DESIGN_READY);
        approveDesign(flow);
        startStage(flow, ShipEventType.PLAN_STARTED, ShipStage.PLAN, AttemptInputs.from(flow.view));
        acceptArtifact(flow, "plan", ShipEventType.PLAN_VALIDATED);

        startChangedInputs(
                flow,
                ShipEventType.REQUIREMENTS_INPUTS_CHANGED,
                ShipStage.DISCOVERY,
                new AttemptInputs(
                        flow.view.context(),
                        null,
                        null,
                        null,
                        null,
                        flow.view.interactionBundle(),
                        null,
                        null,
                        null,
                        List.of(),
                        null,
                        null));
        assertEquals(ShipState.DISCOVERY_ANALYZING, flow.view.state());
        assertNull(flow.view.ledger());
        assertNull(flow.view.catalogEvidence());
        assertNull(flow.view.requirementsDigest());
        assertNull(flow.view.design());
        assertNull(flow.view.designDigest());
        assertNull(flow.view.plan());
        assertEquals(flow.view, flow.projector().replay());
    }

    @Test
    void planInputInvalidationClearsPopulatedExecutionAndValidationProjection()
            throws Exception {
        DurableFlow flow = planValidatedFlow("downstream-invalidation");
        approvePlan(flow);
        startStage(
                flow,
                ShipEventType.EXECUTION_STARTED,
                ShipStage.EXECUTE,
                AttemptInputs.from(flow.view));
        acceptExecution(flow);

        assertNotNull(flow.view.artifactManifest());
        assertNotNull(flow.view.catalogUsage());
        assertNotNull(flow.view.executionResult());
        assertNotNull(flow.view.candidateSnapshot());
        assertNotNull(flow.view.candidateDirectory());

        startStage(
                flow,
                ShipEventType.VALIDATION_STARTED,
                ShipStage.VALIDATE,
                AttemptInputs.from(flow.view));
        recordValidation(flow, true);
        assertNotNull(flow.view.validationReport());

        BlobReference requirements = flow.view.ledger();
        BlobReference catalog = flow.view.catalogEvidence();
        BlobReference design = flow.view.design();
        startChangedInputs(
                flow,
                ShipEventType.PLAN_INPUTS_CHANGED,
                ShipStage.PLAN,
                new AttemptInputs(
                        flow.view.context(),
                        requirements,
                        catalog,
                        design,
                        null,
                        flow.view.interactionBundle(),
                        null,
                        null,
                        null,
                        List.of(),
                        null,
                        null));

        assertEquals(ShipState.PLAN_RUNNING, flow.view.state());
        assertEquals(design, flow.view.design());
        assertNull(flow.view.plan());
        assertNull(flow.view.artifactManifest());
        assertNull(flow.view.catalogUsage());
        assertNull(flow.view.executionResult());
        assertNull(flow.view.candidateSnapshot());
        assertNull(flow.view.candidateDirectory());
        assertTrue(flow.view.evidence().isEmpty());
        assertTrue(flow.view.evidenceById().isEmpty());
        assertNull(flow.view.validationReport());
        assertNull(flow.view.stamp());
        assertEquals(flow.view, flow.projector().replay());
    }

    @Test
    void approvalChallengesMustBindTheExactCurrentAuthorityBasis() throws Exception {
        DurableFlow flow = requirementsReadyFlow("approval-basis");
        startStage(flow, ShipEventType.DESIGN_STARTED, ShipStage.DESIGN, AttemptInputs.from(flow.view));
        acceptArtifact(flow, "design", ShipEventType.DESIGN_READY);

        ShipEventHead designHead = flow.events.currentHead();
        for (ApprovalBasisField field : ApprovalBasisField.values()) {
            DesignChallenge forgedDesign = designChallenge(
                    flow, flow.view.authority().authority().basis(), field);
            BlobReference forgedDesignBundle = forgedDesignBundle(flow, forgedDesign);
            ShipAuthorityCommand designCommand = ShipAuthorityCommand.empty(
                    ShipEventType.DESIGN_APPROVAL_REQUESTED);
            Exception designFailure = assertThrows(
                    Exception.class,
                    () -> flow.projector().preflight(
                            designCommand,
                            new ShipEventPayloads.DesignApprovalRequested(
                                    forgedDesign, forgedDesignBundle),
                            designCommand.apply(flow.view.authority())),
                    field.name());
            assertTrue(
                    designFailure.getMessage().contains("current authority basis"),
                    field.name());
            assertEquals(designHead, flow.events.currentHead());
        }

        approveDesign(flow);
        startStage(flow, ShipEventType.PLAN_STARTED, ShipStage.PLAN, AttemptInputs.from(flow.view));
        acceptArtifact(flow, "plan", ShipEventType.PLAN_VALIDATED);

        ShipEventHead planHead = flow.events.currentHead();
        for (ApprovalBasisField field : ApprovalBasisField.values()) {
            PlanChallenge forgedPlan = planChallenge(
                    flow, flow.view.authority().authority().basis(), field);
            BlobReference forgedPlanBundle = forgedPlanBundle(flow, forgedPlan);
            ShipAuthorityCommand planCommand = ShipAuthorityCommand.empty(
                    ShipEventType.PLAN_APPROVAL_REQUESTED);
            Exception planFailure = assertThrows(
                    Exception.class,
                    () -> flow.projector().preflight(
                            planCommand,
                            new ShipEventPayloads.PlanApprovalRequested(
                                    forgedPlan, forgedPlanBundle),
                            planCommand.apply(flow.view.authority())),
                    field.name());
            assertTrue(
                    planFailure.getMessage().contains("current authority basis"),
                    field.name());
            assertEquals(planHead, flow.events.currentHead());
        }
        assertEquals(flow.view, flow.projector().replay());
    }

    @Test
    void designReadyMustBindItsProjectedDigestToTheAcceptedArtifact() throws Exception {
        DurableFlow flow = requirementsReadyFlow("design-digest-binding");
        startStage(flow, ShipEventType.DESIGN_STARTED, ShipStage.DESIGN, AttemptInputs.from(flow.view));
        StageRequest request = flow.view.activeRequest();
        BlobReference artifact = flow.blobs.writeBytes(
                "design", "design".getBytes(StandardCharsets.UTF_8));
        ProducedArtifact claim = new ProducedArtifact(
                "design", "design.md", artifact.digest(), artifact.byteSize());
        StageResult result = new StageResult(
                StageResult.SCHEMA_VERSION,
                request.runId(),
                request.stage(),
                request.attemptId(),
                request.challenge(),
                request.inputDigest(),
                StageResult.Outcome.COMPLETED,
                null,
                null,
                List.of(),
                List.of(claim),
                null,
                null,
                null);
        BlobReference resultReference = fixtureBlob(flow.blobs, "stage-result", result);
        ShipAuthorityCommand command = ShipAuthorityCommand.value(
                ShipEventType.DESIGN_READY, artifact.digest());
        ShipEventHead headBefore = flow.events.currentHead();

        Exception failure = assertThrows(
                Exception.class,
                () -> flow.projector().preflight(
                        command,
                        new ShipEventPayloads.StageAccepted(
                                request.stage(),
                                flow.view.activeRequestReference(),
                                resultReference,
                                List.of(new ShipWorkspaceService.AcceptedArtifact(
                                        claim, artifact, 0100644)),
                                null,
                                null,
                                null,
                                null,
                                null,
                                ShipDigest.sha256("forged-design".getBytes(StandardCharsets.UTF_8)),
                                null,
                                null,
                                null),
                        command.apply(flow.view.authority())));

        assertTrue(failure.getMessage().contains("accepted design digest"));
        assertEquals(headBefore, flow.events.currentHead());
    }

    @Test
    void designGapLedgerAuthorityIsRevalidatedDuringDurableReplay() throws Exception {
        DurableFlow flow = requirementsReadyFlow("design-gap-provenance");
        startStage(flow, ShipEventType.DESIGN_STARTED, ShipStage.DESIGN, AttemptInputs.from(flow.view));
        DecisionLedger previous = ShipJson.mapper().readValue(
                flow.blobs.readBytes(flow.view.ledger(), ShipJson.MAX_DOCUMENT_BYTES),
                DecisionLedger.class);
        List<Entry> rewrittenDecisions = previous.decisions().stream()
                .map(entry -> entry.equals(previous.decisions().get(0))
                        ? new Entry(
                                entry.id(),
                                entry.category(),
                                entry.value(),
                                entry.status(),
                                entry.sourceRefs(),
                                "rewritten without authority")
                        : entry)
                .toList();
        DecisionLedger rewritten = new DecisionLedger(
                previous.schemaVersion(),
                previous.revision() + 1,
                previous.facts(),
                rewrittenDecisions,
                previous.conflicts(),
                previous.assumptions(),
                previous.catalogEvidence(),
                previous.openQuestions(),
                previous.completeness(),
                previous.blockingOpenItemIds(),
                GapReviewStatus.NOT_RUN,
                previous.requirementsPolicy());
        StageRequest request = flow.view.activeRequest();
        StageResult result = analysisResult(
                request, StageResult.Outcome.NEEDS_DISCOVERY, rewritten, List.of());
        BlobReference resultReference = fixtureBlob(flow.blobs, "stage-result", result);
        BlobReference ledgerReference = fixtureBlob(flow.blobs, "decision-ledger", rewritten);
        ShipAuthorityCommand command = ShipAuthorityCommand.designGaps(
                request.inputDigest(), resultReference.digest());
        ShipRun successor = command.apply(flow.view.authority());
        Attempt attempt = ShipLifecycleReducer.attempt(successor);
        StageRequest continuationRequest = new ShipAttemptFactory().createRunnable(
                flow.view,
                flow.blobs,
                ShipStage.DISCOVERY,
                Math.toIntExact(attempt.number()),
                new AttemptInputs(
                        flow.view.context(),
                        ledgerReference,
                        null,
                        flow.view.design(),
                        flow.view.plan(),
                        flow.view.interactionBundle(),
                        flow.view.artifactManifest(),
                        flow.view.candidateSnapshot(),
                        flow.view.candidateDirectory(),
                        flow.view.evidence(),
                        null,
                        null));
        BlobReference continuationReference = fixtureBlob(
                flow.blobs, "stage-request", continuationRequest);
        ShipEventPayloads.StageAccepted payload = new ShipEventPayloads.StageAccepted(
                request.stage(),
                flow.view.activeRequestReference(),
                resultReference,
                List.of(),
                ledgerReference,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new ShipEventPayloads.StageStarted(
                        ShipStage.DISCOVERY,
                        continuationReference,
                        continuationRequest.outputDirectory()));
        ShipRun previousAuthority = flow.view.authority();
        flow.events.appendIfLatest(
                flow.events.currentHead(), draft(command, payload, previousAuthority, successor));

        Exception failure = assertThrows(Exception.class, () -> flow.projector().replay());
        assertTrue(failure.getMessage().contains("Existing ledger decisions"));
    }

    @Test
    void stampStartAndCompletionAreReconstructedFromDurableEvents() throws Exception {
        DurableFlow flow = validationRunningFlow("stamp-replay");
        ValidationFixture validation = recordValidation(flow, true);
        flow.commit(
                ShipAuthorityCommand.empty(ShipEventType.STAMP_STARTED),
                new ShipEventPayloads.NoData());
        assertEquals(ShipState.STAMP_RUNNING, flow.view.state());

        BlobReference stamp = completeRun(
                flow,
                validation,
                ShipStamp.Status.PASS,
                List.of(),
                ShipEventType.RUN_COMPLETED);

        assertEquals(ShipState.COMPLETED, flow.view.state());
        assertEquals(stamp, flow.view.stamp());
        assertTrue(Files.isRegularFile(
                flow.view.projectRoot().resolve("src/main/resources/routes/orders.camel.yaml")));
        assertEquals(flow.view, flow.projector().replay());
    }

    @Test
    void waivableFailurePreservesInteractionsThroughWaiverAndStampReplay() throws Exception {
        PendingWaiver pending = requestWaiver("waiver-replay");
        DurableFlow flow = pending.flow();
        ShipEventPayloads.WaiverRecorded approval = waiverResponse(pending, WaiverDecision.WAIVE);
        flow.commit(
                ShipAuthorityCommand.decision(ShipEventType.WAIVER_RECORDED, null),
                approval);
        assertEquals(ShipState.WAIVER_RECORDED, flow.view.state());

        flow.commit(
                ShipAuthorityCommand.empty(ShipEventType.WAIVER_STAMP_STARTED),
                new ShipEventPayloads.NoData());
        ShipStamp.Check failed = pending.validation().result().failedCheck();
        BlobReference stamp = completeRun(
                flow,
                pending.validation(),
                ShipStamp.Status.COMPLETED_WITH_WAIVER,
                List.of(new ShipStamp.Waiver(
                        failed.id(),
                        failed.evidenceDigest(),
                        approval.responseReference().digest())),
                ShipEventType.RUN_COMPLETED_WITH_WAIVER);

        assertEquals(ShipState.COMPLETED_WITH_WAIVER, flow.view.state());
        assertEquals(stamp, flow.view.stamp());
        assertEquals(approval.interactionBundle(), flow.view.interactionBundle());
        assertEquals(flow.view, flow.projector().replay());
    }

    @Test
    void deniedWaiverIsReconstructedFromDurableEvents() throws Exception {
        PendingWaiver pending = requestWaiver("waiver-denied-replay");
        DurableFlow flow = pending.flow();
        ShipEventPayloads.WaiverRecorded denial = waiverResponse(pending, WaiverDecision.DENY);

        flow.commit(
                ShipAuthorityCommand.decision(ShipEventType.WAIVER_DENIED, null),
                denial);

        assertEquals(ShipState.FAILED_TERMINAL, flow.view.state());
        assertEquals(denial.interactionBundle(), flow.view.interactionBundle());
        assertEquals(flow.view, flow.projector().replay());
    }

    @Test
    void waiverResponseDecisionMustMatchItsLifecycleEvent() throws Exception {
        PendingWaiver pending = requestWaiver("waiver-decision-mismatch");
        DurableFlow flow = pending.flow();
        ShipEventPayloads.WaiverRecorded approval = waiverResponse(pending, WaiverDecision.WAIVE);
        ShipAuthorityCommand command = ShipAuthorityCommand.decision(
                ShipEventType.WAIVER_DENIED, null);
        ShipRun previous = flow.view.authority();
        ShipRun successor = command.apply(previous);
        flow.events.appendIfLatest(
                flow.events.currentHead(), draft(command, approval, previous, successor));

        Exception failure = assertThrows(Exception.class, () -> flow.projector().replay());
        assertTrue(failure.getMessage().contains("Waiver response disagrees"));
    }

    @Test
    void waivableFailureCannotSwapTheSignedInteractionBundle() throws Exception {
        DurableFlow flow = validationRunningFlow("waiver-bundle-swap");
        ValidationFixture validation = validationFixture(flow, false);
        ShipStamp.Check failed = validation.result().failedCheck();
        String checkId = failed.id();
        BlobReference swappedBundle = flow.interactions.amend(
                flow.blobs,
                flow.view.interactionBundle(),
                ShipDigest.sha256("swapped-context".getBytes(StandardCharsets.UTF_8)));
        ShipAuthorityCommand command = ShipAuthorityCommand.waiver(
                checkId,
                failed.evidenceDigest(),
                flow.view.authority().authority().basis().policy().value());
        ShipEventPayloads.Failure payload = new ShipEventPayloads.Failure(
                ShipStage.VALIDATE,
                "waivable-check-failed",
                "The validation check requires explicit waiver",
                flow.view.activeRequestReference(),
                validation.resultReference(),
                validation.result().reportReference(),
                null,
                swappedBundle,
                validation.result().evidence());
        ShipRun previous = flow.view.authority();
        ShipRun successor = command.apply(previous);
        flow.events.appendIfLatest(
                flow.events.currentHead(), draft(command, payload, previous, successor));

        Exception failure = assertThrows(Exception.class, () -> flow.projector().replay());
        assertTrue(failure.getMessage().contains("changed the signed interaction bundle"));
    }

    @Test
    void authenticatedMalformedContextRequestFailsClosedDuringReplay() throws Exception {
        Fixture fixture = fixture("malformed-context-request");
        BlobReference malformed = fixture.blobs.writeBytes(
                "initial-context-request",
                "{\"kind\":\"text\",\"sources\":[{\"kind\":\"unknown\",\"value\":\"x\"}]}"
                        .getBytes(StandardCharsets.UTF_8));
        ShipAuthorityCommand command = ShipAuthorityCommand.value(
                ShipEventType.CONTEXT_RESOLUTION_STARTED, malformed.digest());
        ShipRun successor = command.apply(fixture.authority);
        fixture.events.appendIfLatest(
                fixture.events.currentHead(),
                draft(
                        command,
                        new ShipEventPayloads.ContextResolutionStarted(malformed),
                        fixture.authority,
                        successor));

        Exception failure = assertThrows(Exception.class, () -> fixture.projector().replay());
        assertTrue(failure.getMessage().contains("Invalid Ship event"));
    }

    @Test
    void recordedContextRequiresItsRunBoundInteractionBundle() throws Exception {
        Fixture fixture = fixture("missing-context-bundle");
        BlobReference input = fixture.blobs.writeBytes(
                "initial-context-request",
                ShipInitialContextRequestCodec.encode(new InitialContextRequest.Text("context")));
        fixture.authority = append(
                fixture,
                fixture.authority,
                ShipAuthorityCommand.value(ShipEventType.CONTEXT_RESOLUTION_STARTED, input.digest()),
                new ShipEventPayloads.ContextResolutionStarted(input));
        InitialContext contextValue = InitialContext.fromSources(
                java.util.List.of(InitialContext.userText("context")));
        BlobReference context = fixture.blobs.writeBytes(
                "initial-context", ShipJson.mapper().writeValueAsBytes(contextValue));
        ShipAuthorityCommand command = ShipAuthorityCommand.value(
                ShipEventType.CONTEXT_RECORDED, context.digest());
        ShipRun successor = command.apply(fixture.authority);
        fixture.events.appendIfLatest(
                fixture.events.currentHead(),
                draft(
                        command,
                        new ShipEventPayloads.ContextRecorded(context, null),
                        fixture.authority,
                        successor));

        Exception failure = assertThrows(Exception.class, () -> fixture.projector().replay());
        assertTrue(failure.getMessage().contains("interaction-bundle"));
    }

    @Test
    void attemptVerificationCacheDoesNotOutliveOneReplay() throws Exception {
        DurableFlow flow = requirementsReadyFlow("replay-cache");
        startStage(
                flow,
                ShipEventType.DESIGN_STARTED,
                ShipStage.DESIGN,
                AttemptInputs.from(flow.view));
        ShipRunProjector projector = flow.projector();
        assertEquals(flow.view, projector.replay());

        Files.writeString(flow.view.sourceDirectory().resolve("tampered.txt"), "tampered\n");

        Exception failure = assertThrows(Exception.class, projector::replay);
        assertTrue(failure.getMessage().contains("project source differs"));
    }

    private PendingWaiver requestWaiver(String name) throws Exception {
        DurableFlow flow = validationRunningFlow(name);
        BlobReference originalBundle = flow.view.interactionBundle();
        ValidationFixture validation = recordValidation(flow, false);
        BlobReference evidence = validation.failedEvidence();
        ShipStamp.Check failed = validation.result().failedCheck();
        String checkId = failed.id();
        String policy = flow.view.authority().authority().basis().policy().value();

        assertEquals(ShipState.WAIVER_ELIGIBLE, flow.view.state());
        assertEquals(originalBundle, flow.view.interactionBundle());

        String subjectDigest = ShipDigest.sha256("waiver-subject".getBytes(StandardCharsets.UTF_8));
        String nonce = flow.signer.nonce();
        WaiverChallenge unsigned = new WaiverChallenge(
                Interaction.SCHEMA_VERSION,
                flow.view.runId(),
                checkId,
                evidence.digest(),
                policy,
                subjectDigest,
                failed.id(),
                "The failed check may affect runtime safety",
                "The user accepts the documented risk",
                nonce,
                SYNTACTIC_MAC);
        WaiverChallenge challenge = new WaiverChallenge(
                unsigned.schemaVersion(),
                unsigned.runId(),
                unsigned.checkId(),
                unsigned.evidenceDigest(),
                unsigned.eligibilityPolicyDigest(),
                unsigned.subjectDigest(),
                unsigned.subjectReference(),
                unsigned.risk(),
                unsigned.consequence(),
                unsigned.nonce(),
                flow.signer.sign(Interaction.waiverChallengeMacFields(unsigned)));
        BlobReference pendingBundle = flow.interactions.record(
                flow.blobs, flow.view.interactionBundle(), challenge);
        flow.commit(
                ShipAuthorityCommand.empty(ShipEventType.WAIVER_REQUESTED),
                new ShipEventPayloads.WaiverRequested(challenge, pendingBundle));
        return new PendingWaiver(flow, evidence, challenge, validation);
    }

    private static ShipEventPayloads.WaiverRecorded waiverResponse(
            PendingWaiver pending, WaiverDecision decision)
            throws Exception {
        DurableFlow flow = pending.flow();
        WaiverChallenge challenge = pending.challenge();
        WaiverResponse unsigned = new WaiverResponse(
                Interaction.SCHEMA_VERSION,
                flow.view.runId(),
                challenge.checkId(),
                challenge.evidenceDigest(),
                challenge.eligibilityPolicyDigest(),
                challenge.subjectDigest(),
                challenge.nonce(),
                decision,
                decision == WaiverDecision.WAIVE ? "Accept the bounded risk" : null,
                "uid:1000",
                "terminal-v1",
                "cli",
                NOW,
                SYNTACTIC_MAC);
        WaiverResponse response = new WaiverResponse(
                unsigned.schemaVersion(),
                unsigned.runId(),
                unsigned.checkId(),
                unsigned.evidenceDigest(),
                unsigned.eligibilityPolicyDigest(),
                unsigned.subjectDigest(),
                unsigned.nonce(),
                unsigned.decision(),
                unsigned.reason(),
                unsigned.controllerObservedProcessPrincipal(),
                unsigned.declaredCliUiProfile(),
                unsigned.channel(),
                unsigned.answeredAt(),
                flow.signer.sign(Interaction.waiverResponseMacFields(unsigned)));
        BlobReference responseReference = fixtureBlob(
                flow.blobs, "interaction-response", response);
        BlobReference completedBundle = flow.interactions.record(
                flow.blobs, flow.view.interactionBundle(), response);
        return new ShipEventPayloads.WaiverRecorded(
                response, responseReference, completedBundle);
    }

    private DurableFlow planValidatedFlow(String name) throws Exception {
        DurableFlow flow = requirementsReadyFlow(name);
        startStage(flow, ShipEventType.DESIGN_STARTED, ShipStage.DESIGN, AttemptInputs.from(flow.view));
        acceptArtifact(flow, "design", ShipEventType.DESIGN_READY);
        approveDesign(flow);
        startStage(flow, ShipEventType.PLAN_STARTED, ShipStage.PLAN, AttemptInputs.from(flow.view));
        acceptArtifact(flow, "plan", ShipEventType.PLAN_VALIDATED);
        return flow;
    }

    private DurableFlow validationRunningFlow(String name) throws Exception {
        DurableFlow flow = planValidatedFlow(name);
        approvePlan(flow);
        startStage(
                flow,
                ShipEventType.EXECUTION_STARTED,
                ShipStage.EXECUTE,
                AttemptInputs.from(flow.view));
        acceptExecution(flow);
        startStage(
                flow,
                ShipEventType.VALIDATION_STARTED,
                ShipStage.VALIDATE,
                AttemptInputs.from(flow.view));
        return flow;
    }

    private DurableFlow requirementsReadyFlow(String name) throws Exception {
        Path stateRoot = temporaryDirectory.resolve(name + "-state")
                .toAbsolutePath()
                .normalize();
        Path project = Files.createDirectory(temporaryDirectory.resolve(name + "-project"))
                .toAbsolutePath()
                .normalize();
        String requirements = "main 4.21.0 yaml simple forbidden " + CITRUS_VERSION + ' '
                              + String.join(" ", CITRUS_DEPENDENCIES) + " resolved";
        ShipController controller = new ShipController(
                stateRoot, Clock.fixed(NOW, ZoneOffset.UTC));
        ShipRunView created = controller.start(
                new ShipController.PreparedRun(project, "test-adapter"));
        ShipRunView resolving = controller.beginContextResolution(
                created.runId(),
                created.eventDigest(),
                new InitialContextRequest.Text(requirements));
        ShipRunView context = controller.continueContextResolution(
                created.runId(), resolving.eventDigest(), List.of());
        ShipRunView discovery = controller.startDiscovery(
                created.runId(), context.eventDigest());
        SourceRef source = contextSource(stateRoot, discovery);

        DecisionLedger first = readyLedger(1, GapReviewStatus.NOT_RUN, source);
        Path catalogRoot = Files.createDirectory(
                temporaryDirectory.resolve(name + "-catalog"));
        Snapshot catalogSnapshot = CatalogTestVerifier.mainSnapshot(catalogRoot);
        ShipRunView continued = controller.submitStageResult(
                discovery.runId(),
                discovery.eventDigest(),
                ShipJson.mapper().writeValueAsBytes(analysisResult(
                        discovery.activeRequest(),
                        StageResult.Outcome.NEEDS_DISCOVERY,
                        first,
                        List.of(
                                new CatalogSubject(
                                        CatalogSubject.Kind.COMPONENT, "direct"),
                                new CatalogSubject(
                                        CatalogSubject.Kind.EIP, "from"),
                                new CatalogSubject(
                                        CatalogSubject.Kind.EIP, "route")))),
                catalogSnapshot);
        ShipRunView reviewing = controller.submitStageResult(
                continued.runId(),
                continued.eventDigest(),
                ShipJson.mapper().writeValueAsBytes(analysisResult(
                        continued.activeRequest(),
                        StageResult.Outcome.COMPLETED,
                        readyLedger(2, GapReviewStatus.NOT_RUN, source),
                        List.of())));
        ShipRunView ready = controller.submitStageResult(
                reviewing.runId(),
                reviewing.eventDigest(),
                ShipJson.mapper().writeValueAsBytes(analysisResult(
                        reviewing.activeRequest(),
                        StageResult.Outcome.COMPLETED,
                        readyLedger(2, GapReviewStatus.PASSED, source),
                        List.of())));
        FileShipEventStore events = FileShipEventStore.open(stateRoot, ready.runId());
        ShipBlobStore blobs = ShipBlobStore.open(stateRoot, ready.runId());
        ShipInteractionSigner signer = ShipInteractionSigner.open(
                stateRoot.resolve(ready.runId()));
        return new DurableFlow(
                events,
                blobs,
                new ShipInteractionBundleService(signer),
                signer,
                catalogSnapshot,
                ready);
    }

    private static void startChangedInputs(
            DurableFlow flow,
            ShipEventType event,
            ShipStage stage,
            AttemptInputs projected)
            throws Exception {
        startStage(flow, event, stage, projected);
    }

    private static void startStage(
            DurableFlow flow,
            ShipEventType event,
            ShipStage stage,
            AttemptInputs projected)
            throws Exception {
        ShipAuthorityCommand command = ShipAuthorityCommand.empty(event);
        ShipRun successor = command.apply(flow.view.authority());
        Attempt attempt = ShipLifecycleReducer.attempt(successor);
        StageRequest request = new ShipAttemptFactory().createRunnable(
                flow.view,
                flow.blobs,
                stage,
                Math.toIntExact(attempt.number()),
                projected);
        BlobReference reference = fixtureBlob(flow.blobs, "stage-request", request);
        flow.commit(
                command,
                new ShipEventPayloads.StageStarted(
                        stage, reference, request.outputDirectory()),
                successor);
    }

    private static BlobReference acceptArtifact(
            DurableFlow flow, String kind, ShipEventType event)
            throws Exception {
        StageRequest request = flow.view.activeRequest();
        byte[] content = (kind + " revision " + flow.view.revision())
                .getBytes(StandardCharsets.UTF_8);
        BlobReference artifact = flow.blobs.writeBytes(kind, content);
        ProducedArtifact claim = new ProducedArtifact(
                kind, kind + ".md", artifact.digest(), artifact.byteSize());
        StageResult result = new StageResult(
                StageResult.SCHEMA_VERSION,
                request.runId(),
                request.stage(),
                request.attemptId(),
                request.challenge(),
                request.inputDigest(),
                StageResult.Outcome.COMPLETED,
                null,
                null,
                List.of(),
                List.of(claim),
                null,
                null,
                null);
        BlobReference resultReference = fixtureBlob(flow.blobs, "stage-result", result);
        ShipWorkspaceService.AcceptedArtifact accepted = new ShipWorkspaceService.AcceptedArtifact(
                claim, artifact, 0100644);
        ShipAuthorityCommand command = ShipAuthorityCommand.value(event, artifact.digest());
        flow.commit(
                command,
                new ShipEventPayloads.StageAccepted(
                        request.stage(),
                        flow.view.activeRequestReference(),
                        resultReference,
                        List.of(accepted),
                        null,
                        null,
                        null,
                        null,
                        null,
                        "design".equals(kind) ? artifact.digest() : null,
                        null,
                        null,
                        null));
        return artifact;
    }

    private static void acceptExecution(DurableFlow flow) throws Exception {
        StageRequest request = flow.view.activeRequest();
        Path candidateDirectory = Path.of(request.candidateDirectory());
        Path route = candidateDirectory.resolve(
                "src/main/resources/routes/orders.camel.yaml");
        Path test = candidateDirectory.resolve("test/orders.camel.it.yaml");
        Path pom = candidateDirectory.resolve("pom.xml");
        Path config = candidateDirectory.resolve(".camel-kit/config.properties");
        Files.createDirectories(route.getParent());
        Files.createDirectories(test.getParent());
        Files.createDirectories(config.getParent());
        Files.writeString(route, """
                - route:
                    id: orders
                    from:
                      uri: direct:start
                """, StandardCharsets.UTF_8);
        Files.writeString(test, """
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
                """, StandardCharsets.UTF_8);
        Files.writeString(pom, """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.example</groupId>
                  <artifactId>orders</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>org.apache.camel</groupId>
                      <artifactId>camel-main</artifactId>
                      <version>4.21.0</version>
                    </dependency>
                    <dependency>
                      <groupId>org.apache.camel</groupId>
                      <artifactId>camel-yaml-dsl</artifactId>
                      <version>4.21.0</version>
                    </dependency>
                    <dependency>
                      <groupId>org.apache.camel</groupId>
                      <artifactId>camel-direct</artifactId>
                      <version>4.21.0</version>
                    </dependency>
                  </dependencies>
                </project>
                """, StandardCharsets.UTF_8);
        Files.writeString(config, """
                project.runtime=main
                project.camelVersion=4.21.0
                project.platformBomVersion=4.21.0
                citrus.version=5.0.0-M2
                """, StandardCharsets.UTF_8);

        BlobReference routeBlob = flow.blobs.writeBytes(
                "route", Files.readAllBytes(route));
        BlobReference testBlob = flow.blobs.writeBytes(
                "citrus-test", Files.readAllBytes(test));
        BlobReference pomBlob = flow.blobs.writeBytes(
                "pom", Files.readAllBytes(pom));
        BlobReference configBlob = flow.blobs.writeBytes(
                "runtime-config", Files.readAllBytes(config));
        List<ProducedArtifact> claims = List.of(
                new ProducedArtifact(
                        "route",
                        "src/main/resources/routes/orders.camel.yaml",
                        routeBlob.digest(),
                        routeBlob.byteSize()),
                new ProducedArtifact(
                        "citrus-test",
                        "test/orders.camel.it.yaml",
                        testBlob.digest(),
                        testBlob.byteSize()),
                new ProducedArtifact(
                        "pom", "pom.xml", pomBlob.digest(), pomBlob.byteSize()),
                new ProducedArtifact(
                        "runtime-config",
                        ".camel-kit/config.properties",
                        configBlob.digest(),
                        configBlob.byteSize()));
        List<ShipWorkspaceService.AcceptedArtifact> accepted = List.of(
                new ShipWorkspaceService.AcceptedArtifact(
                        claims.get(0), routeBlob, 0100644),
                new ShipWorkspaceService.AcceptedArtifact(
                        claims.get(1), testBlob, 0100644),
                new ShipWorkspaceService.AcceptedArtifact(
                        claims.get(2), pomBlob, 0100644),
                new ShipWorkspaceService.AcceptedArtifact(
                        claims.get(3), configBlob, 0100644));
        ArtifactManifest manifest = new ArtifactManifest(
                ArtifactManifest.SCHEMA_VERSION,
                "main",
                CatalogTestVerifier.CAMEL_VERSION,
                null,
                null,
                "yaml",
                "simple",
                CITRUS_VERSION,
                CITRUS_DEPENDENCIES,
                ArtifactManifest.JavaPolicy.FORBIDDEN,
                List.of(),
                List.of(new RouteArtifact(
                        "orders",
                        "src/main/resources/routes/orders.camel.yaml",
                        routeBlob.digest())),
                List.of(new TestArtifact(
                        "orders",
                        "test/orders.camel.it.yaml",
                        testBlob.digest())),
                List.of(
                        new DeclaredArtifact(
                                "pom", "pom.xml", pomBlob.digest(), true),
                        new DeclaredArtifact(
                                "runtime-config",
                                ".camel-kit/config.properties",
                                configBlob.digest(),
                                true)),
                true,
                true);
        StageResult result = new StageResult(
                StageResult.SCHEMA_VERSION,
                request.runId(),
                request.stage(),
                request.attemptId(),
                request.challenge(),
                request.inputDigest(),
                StageResult.Outcome.COMPLETED,
                null,
                null,
                List.of(),
                claims,
                manifest,
                null,
                null);
        BlobReference resultReference = fixtureBlob(flow.blobs, "stage-result", result);
        BlobReference manifestReference = fixtureBlob(
                flow.blobs, "artifact-manifest", manifest);
        ProjectSnapshot candidateValue = ProjectEvidenceFiles.captureSealed(
                candidateDirectory);
        BlobReference candidateReference = fixtureBlob(
                flow.blobs,
                "project-snapshot",
                candidateValue);
        DecisionLedger ledger = ShipJson.mapper().readValue(
                flow.blobs.readBytes(
                        flow.view.ledger(), ShipJson.MAX_DOCUMENT_BYTES),
                DecisionLedger.class);
        CatalogEvidenceSet approvedEvidence = ShipJson.mapper().readValue(
                flow.blobs.readBytes(
                        flow.view.catalogEvidence(), ShipJson.MAX_DOCUMENT_BYTES),
                CatalogEvidenceSet.class);
        CatalogUsageRecord usage = CatalogEvidenceValidator.deriveUsage(
                flow.catalogSnapshot,
                ledger,
                approvedEvidence,
                manifest,
                candidateDirectory,
                flow.view.runId(),
                flow.view.catalogEvidence().digest(),
                manifestReference.digest(),
                candidateReference.digest(),
                candidateValue.digest());
        BlobReference usageReference = fixtureBlob(
                flow.blobs, "catalog-usage", usage);
        flow.commit(
                ShipAuthorityCommand.value(
                        ShipEventType.EXECUTION_VALIDATED, resultReference.digest()),
                new ShipEventPayloads.StageAccepted(
                        request.stage(),
                        flow.view.activeRequestReference(),
                        resultReference,
                        accepted,
                        null,
                        manifestReference,
                        null,
                        usageReference,
                        null,
                        null,
                        candidateReference,
                        candidateDirectory.toString(),
                        null));
    }

    private static ValidationFixture recordValidation(
            DurableFlow flow, boolean citrusPassed)
            throws Exception {
        ValidationFixture validation = validationFixture(flow, citrusPassed);
        ShipValidationService.Verdict expected = citrusPassed
                ? ShipValidationService.Verdict.PASS
                : ShipValidationService.Verdict.WAIVABLE;
        assertEquals(expected, validation.result().verdict());
        if (citrusPassed) {
            flow.commit(
                    ShipAuthorityCommand.value(
                            ShipEventType.VALIDATION_PASSED,
                            validation.result().reportReference().digest()),
                    new ShipEventPayloads.StageAccepted(
                            flow.view.activeRequest().stage(),
                            flow.view.activeRequestReference(),
                            validation.resultReference(),
                            List.of(validation.accepted()),
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            validation.result().evidence(),
                            validation.result().reportReference(),
                            null));
        } else {
            ShipStamp.Check failed = validation.result().failedCheck();
            String stableCheckId = failed.id();
            flow.commit(
                    ShipAuthorityCommand.waiver(
                            stableCheckId,
                            failed.evidenceDigest(),
                            flow.view.authority().authority().basis().policy().value()),
                    new ShipEventPayloads.Failure(
                            ShipStage.VALIDATE,
                            "waivable-check-failed",
                            "The validation check requires explicit waiver",
                            flow.view.activeRequestReference(),
                            validation.resultReference(),
                            validation.result().reportReference(),
                            null,
                            null,
                            validation.result().evidence()));
        }
        return validation;
    }

    private static ValidationFixture validationFixture(
            DurableFlow flow, boolean citrusPassed)
            throws Exception {
        StageRequest request = flow.view.activeRequest();
        byte[] content = (citrusPassed
                ? "validation passed"
                : "validation requires waiver").getBytes(StandardCharsets.UTF_8);
        BlobReference workerValidation = flow.blobs.writeBytes("validation", content);
        ProducedArtifact claim = new ProducedArtifact(
                "validation",
                "validation.md",
                workerValidation.digest(),
                workerValidation.byteSize());
        StageResult result = new StageResult(
                StageResult.SCHEMA_VERSION,
                request.runId(),
                request.stage(),
                request.attemptId(),
                request.challenge(),
                request.inputDigest(),
                StageResult.Outcome.COMPLETED,
                null,
                null,
                List.of(),
                List.of(claim),
                null,
                null,
                null);
        BlobReference resultReference = fixtureBlob(flow.blobs, "stage-result", result);
        ShipWorkspaceService.AcceptedArtifact accepted = new ShipWorkspaceService.AcceptedArtifact(
                claim, workerValidation, 0100644);
        ArtifactManifest manifest = ShipJson.mapper().readValue(
                flow.blobs.readBytes(
                        flow.view.artifactManifest(), ShipJson.MAX_DOCUMENT_BYTES),
                ArtifactManifest.class);
        ProjectSnapshot candidateValue = ShipJson.mapper().readValue(
                flow.blobs.readBytes(
                        flow.view.candidateSnapshot(), ShipJson.MAX_DOCUMENT_BYTES),
                ProjectSnapshot.class);
        DecisionLedger ledger = ShipJson.mapper().readValue(
                flow.blobs.readBytes(
                        flow.view.ledger(), ShipJson.MAX_DOCUMENT_BYTES),
                DecisionLedger.class);
        CatalogUsageRecord usage = ShipJson.mapper().readValue(
                flow.blobs.readBytes(
                        flow.view.catalogUsage(), ShipJson.MAX_DOCUMENT_BYTES),
                CatalogUsageRecord.class);
        CatalogEvidenceSet approvedEvidence = ShipJson.mapper().readValue(
                flow.blobs.readBytes(
                        flow.view.catalogEvidence(), ShipJson.MAX_DOCUMENT_BYTES),
                CatalogEvidenceSet.class);
        ShipValidationService.Result validation;
        try (ShipBlobStore.Transaction transaction = flow.blobs.beginTransaction()) {
            validation = new ShipValidationService(
                    (candidate, evidenceDirectory, command) -> deterministicEvidence(
                            candidate,
                            evidenceDirectory,
                            command,
                            citrusPassed),
                    Clock.fixed(NOW, ZoneOffset.UTC))
                    .validate(
                            flow.blobs,
                            transaction,
                            new ShipValidationService.Inputs(
                                    flow.view.runId(),
                                    new ShipValidationService.CandidateInput(
                                            flow.view.candidateDirectory(),
                                            candidateValue,
                                            flow.view.candidateSnapshot()),
                                    new ShipValidationService.ManifestInput(
                                            manifest,
                                            flow.view.artifactManifest()),
                                    ledger.requirementsPolicy(),
                                    new ShipValidationService.CatalogInput(
                                            new ShipValidationService.UsageInput(
                                                    usage,
                                                    flow.view.catalogUsage()),
                                            flow.catalogSnapshot,
                                            new ShipValidationService.ApprovalInput(
                                                    approvedEvidence,
                                                    flow.view.catalogEvidence())),
                                    workerValidation));
            transaction.commit();
        }
        BlobReference failedEvidence = validation.failedCheck() == null
                ? null
                : validation.evidence().stream()
                        .filter(reference -> reference.digest().equals(
                                validation.failedCheck().evidenceDigest()))
                        .findFirst()
                        .orElseThrow();
        return new ValidationFixture(
                validation,
                resultReference,
                accepted,
                failedEvidence);
    }

    private static CommandEvidence deterministicEvidence(
            Path candidate,
            Path evidenceDirectory,
            EvidenceCommand command,
            boolean citrusPassed)
            throws IOException {
        Files.createDirectory(evidenceDirectory);
        Path sandboxDirectory = Files.createDirectory(
                evidenceDirectory.resolve("." + command.id() + "-sandbox-fixture"));
        Path sandbox = Files.writeString(
                sandboxDirectory.resolve("sandbox"), "sandbox\n", StandardCharsets.UTF_8);
        Path toolchain = Files.writeString(
                sandboxDirectory.resolve("toolchain"), "toolchain\n", StandardCharsets.UTF_8);
        Path executable = Files.writeString(
                sandboxDirectory.resolve("executable"), "executable\n", StandardCharsets.UTF_8);
        byte[] stdoutBytes = new byte[0];
        if (command.jvmPayload().kind() == JvmPayloadRequest.Kind.MAIN_PACKAGE_INSPECT) {
            Path archive = sandboxDirectory.resolve("main-routes.jar");
            try {
                stdoutBytes = ShipMainPackageMain.packageAndInspect(
                        candidate,
                        archive,
                        command.arguments().subList(4, command.arguments().size()))
                        .encode();
            } finally {
                Files.deleteIfExists(archive);
            }
        }
        Path stdout = Files.write(
                evidenceDirectory.resolve("stdout.log"), stdoutBytes);
        Path stderr = Files.write(
                evidenceDirectory.resolve("stderr.log"), new byte[0]);
        String sandboxDigest = ShipDigest.sha256(Files.readAllBytes(sandbox));
        String toolchainDigest = ShipDigest.sha256(Files.readAllBytes(toolchain));
        String executableDigest = ShipDigest.sha256(Files.readAllBytes(executable));
        String stdoutDigest = ShipDigest.sha256(Files.readAllBytes(stdout));
        String stderrDigest = ShipDigest.sha256(Files.readAllBytes(stderr));
        String jdkDigest = ShipDigest.sha256(
                "fixture-jdk".getBytes(StandardCharsets.UTF_8));
        boolean passed = citrusPassed
                || command.jvmPayload().kind() != JvmPayloadRequest.Kind.CITRUS_YAML;
        String version = command.jvmPayload().kind()
                         == JvmPayloadRequest.Kind.MAIN_PACKAGE_INSPECT
                                 ? ShipMainPackageMain.PAYLOAD_VERSION
                                 : command.jvmPayload().camelVersion()
                                   + (command.jvmPayload().citrusVersion() == null
                                           ? ""
                                           : " " + command.jvmPayload().citrusVersion());
        return new CommandEvidence(
                CommandEvidence.SCHEMA_VERSION,
                command.id(),
                new SandboxIdentity(
                        EvidenceRunner.SANDBOX_PROVIDER,
                        sandbox.toString(),
                        sandboxDigest,
                        sandboxDigest,
                        null,
                        EvidenceRunner.expectedSandboxProfileDigest(command, jdkDigest)),
                toolchainDigest,
                toolchainDigest,
                toolchain.toString(),
                toolchainDigest,
                executable.toString(),
                executableDigest,
                executableDigest,
                null,
                version,
                command.arguments(),
                candidate.toAbsolutePath().normalize().toString(),
                EvidenceRunner.expectedEnvironment(command, jdkDigest),
                NOW,
                NOW,
                true,
                false,
                passed ? 0 : 1,
                null,
                stdout.toString(),
                stdoutDigest,
                stderr.toString(),
                stderrDigest,
                command.inputDigests());
    }

    private ProtectedControllerFlow executionRunningControllerFlow(
            String name, boolean citrusPassed)
            throws Exception {
        DurableFlow durable = requirementsReadyFlow(name);
        ShipValidationService validation = new ShipValidationService(
                (candidate, evidenceDirectory, command) -> deterministicEvidence(
                        candidate, evidenceDirectory, command, citrusPassed),
                Clock.fixed(NOW, ZoneOffset.UTC));
        ShipController controller = new ShipController(
                durable.blobs.runRoot().getParent(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                validation);
        ProtectedBackend backend = new ProtectedBackend();
        ShipProtectedWorkerBroker broker = new ShipProtectedWorkerBroker(controller, backend);

        durable.view = controller.startDesign(
                durable.view.runId(), durable.view.eventDigest());
        durable.view = broker.submit(
                durable.view.runId(), durable.view.eventDigest(), durable.catalogSnapshot);
        approveDesign(durable);
        durable.view = controller.startPlan(
                durable.view.runId(), durable.view.eventDigest());
        durable.view = broker.submit(
                durable.view.runId(), durable.view.eventDigest(), durable.catalogSnapshot);
        approvePlan(durable);
        durable.view = controller.startExecution(
                durable.view.runId(), durable.view.eventDigest());
        return new ProtectedControllerFlow(
                durable, controller, broker, backend, durable.catalogSnapshot, durable.view);
    }

    private static ShipController faultingController(
            ProtectedControllerFlow flow, AppendFault fault) {
        return new ShipController(
                stateRoot(flow),
                Clock.fixed(NOW, ZoneOffset.UTC),
                new ShipValidationService(
                        (candidate, evidenceDirectory, command) -> deterministicEvidence(
                                candidate, evidenceDirectory, command, true),
                        Clock.fixed(NOW, ZoneOffset.UTC)),
                new FaultingEventStoreAccess(fault));
    }

    private static Path stateRoot(ProtectedControllerFlow flow) {
        return flow.durable().blobs.runRoot().getParent();
    }

    private static List<Path> publicationTransactions(ProtectedControllerFlow flow)
            throws IOException {
        Path publication = flow.durable().blobs.runRoot().resolve("publication");
        if (!Files.exists(publication)) {
            return List.of();
        }
        try (var entries = Files.list(publication)) {
            return entries.sorted().toList();
        }
    }

    private static String routeYaml() {
        return """
                - route:
                    id: orders
                    from:
                      uri: direct:start
                """;
    }

    private static String testYaml() {
        return """
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
                """;
    }

    private static String pomXml() {
        return """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.example</groupId>
                  <artifactId>orders</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>org.apache.camel</groupId>
                      <artifactId>camel-main</artifactId>
                      <version>4.21.0</version>
                    </dependency>
                    <dependency>
                      <groupId>org.apache.camel</groupId>
                      <artifactId>camel-yaml-dsl</artifactId>
                      <version>4.21.0</version>
                    </dependency>
                    <dependency>
                      <groupId>org.apache.camel</groupId>
                      <artifactId>camel-direct</artifactId>
                      <version>4.21.0</version>
                    </dependency>
                  </dependencies>
                </project>
                """;
    }

    private static String runtimeConfig() {
        return """
                project.runtime=main
                project.camelVersion=4.21.0
                project.platformBomVersion=4.21.0
                citrus.version=5.0.0-M2
                """;
    }

    private static WorkerOutput workerOutput(StageRequest request) throws IOException {
        Path root = Path.of(request.outputDirectory());
        Files.createDirectories(root);
        List<ProducedArtifact> artifacts;
        ArtifactManifest manifest = null;
        if (request.stage() == ShipStage.DESIGN || request.stage() == ShipStage.PLAN) {
            String kind = request.stage() == ShipStage.DESIGN ? "design" : "plan";
            artifacts = List.of(writeWorkerArtifact(
                    root, kind, kind + ".md",
                    (kind + " approved\n").getBytes(StandardCharsets.UTF_8)));
        } else if (request.stage() == ShipStage.EXECUTE) {
            ProducedArtifact route = writeWorkerArtifact(
                    root, "route", "src/main/resources/routes/orders.camel.yaml",
                    routeYaml().getBytes(StandardCharsets.UTF_8));
            ProducedArtifact test = writeWorkerArtifact(
                    root, "citrus-test", "test/orders.camel.it.yaml",
                    testYaml().getBytes(StandardCharsets.UTF_8));
            ProducedArtifact pom = writeWorkerArtifact(
                    root, "pom", "pom.xml", pomXml().getBytes(StandardCharsets.UTF_8));
            ProducedArtifact config = writeWorkerArtifact(
                    root, "config", ".camel-kit/config.properties",
                    runtimeConfig().getBytes(StandardCharsets.UTF_8));
            artifacts = List.of(route, test, pom, config);
            manifest = new ArtifactManifest(
                    ArtifactManifest.SCHEMA_VERSION,
                    "main",
                    CatalogTestVerifier.CAMEL_VERSION,
                    null,
                    null,
                    "yaml",
                    "simple",
                    CITRUS_VERSION,
                    CITRUS_DEPENDENCIES,
                    ArtifactManifest.JavaPolicy.FORBIDDEN,
                    List.of(),
                    List.of(new RouteArtifact("orders", route.relativePath(), route.digest())),
                    List.of(new TestArtifact("orders", test.relativePath(), test.digest())),
                    List.of(
                            new DeclaredArtifact("pom", pom.relativePath(), pom.digest(), true),
                            new DeclaredArtifact(
                                    "config", config.relativePath(), config.digest(), true)),
                    true,
                    true);
        } else if (request.stage() == ShipStage.VALIDATE) {
            artifacts = List.of(writeWorkerArtifact(
                    root, "validation", "validation.md",
                    "validation complete\n".getBytes(StandardCharsets.UTF_8)));
        } else {
            throw new IOException("Unexpected protected test stage " + request.stage());
        }
        StageResult result = new StageResult(
                StageResult.SCHEMA_VERSION,
                request.runId(),
                request.stage(),
                request.attemptId(),
                request.challenge(),
                request.inputDigest(),
                StageResult.Outcome.COMPLETED,
                null,
                null,
                List.of(),
                artifacts,
                manifest,
                null,
                null);
        return new WorkerOutput(root, ShipJson.mapper().writeValueAsBytes(result));
    }

    private static ProducedArtifact writeWorkerArtifact(
            Path root, String kind, String relativePath, byte[] content)
            throws IOException {
        Path target = root.resolve(relativePath);
        Files.createDirectories(target.getParent());
        Files.write(target, content);
        return new ProducedArtifact(
                kind, relativePath, ShipDigest.sha256(content), content.length);
    }

    private enum AppendFault {
        UNCHANGED_HEAD,
        SUCCESSOR_HEAD,
        HEAD_READ_FAILURE,
        UNEXPECTED_AUTHENTICATED_HEAD
    }

    private static final class FaultingEventStoreAccess
            implements ShipController.EventStoreAccess {

        private final AppendFault fault;

        private FaultingEventStoreAccess(AppendFault fault) {
            this.fault = fault;
        }

        @Override
        public ShipEvent appendIfLatest(
                FileShipEventStore events,
                ShipEventHead expectedHead,
                ShipEventDraft draft)
                throws IOException {
            if (fault == AppendFault.SUCCESSOR_HEAD) {
                events.appendIfLatest(expectedHead, draft);
            } else if (fault == AppendFault.UNEXPECTED_AUTHENTICATED_HEAD) {
                ShipAuthorityCommand alternate
                        = ShipAuthorityCommand.empty(ShipEventType.STAMP_STARTED);
                events.appendIfLatest(
                        expectedHead,
                        new ShipEventDraft(
                                alternate.type(),
                                draft.state(),
                                draft.previousAuthorityHead(),
                                AuthorityHeadId.create(),
                                draft.occurredAt(),
                                ShipStoredEventCodec.encode(
                                        alternate, new ShipEventPayloads.NoData())));
            }
            throw new IOException("injected append failure");
        }

        @Override
        public ShipEventHead currentHead(FileShipEventStore events) throws IOException {
            if (fault == AppendFault.HEAD_READ_FAILURE) {
                throw new IOException("injected current-head failure");
            }
            return events.currentHead();
        }
    }

    private static final class ProtectedBackend
            implements ShipProtectedWorkerBroker.Backend {

        private final AtomicInteger closed = new AtomicInteger();

        @Override
        public ShipProtectedWorkerBroker.Custody stopAndAcquireExclusiveCustody(
                StageRequest request)
                throws IOException {
            WorkerOutput output = workerOutput(request);
            return new ShipProtectedWorkerBroker.Custody() {
                @Override
                public void requireExactAttempt(StageRequest expected) throws IOException {
                    if (!request.equals(expected)) {
                        throw new IOException("Protected test custody attempt mismatch");
                    }
                }

                @Override
                public byte[] readResultBytes() {
                    return output.result();
                }

                @Override
                public StagedArtifactSource.Session openArtifactSource() throws IOException {
                    return StagedArtifactSource.open(output.root());
                }

                @Override
                public void close() {
                    closed.incrementAndGet();
                }
            };
        }

        private int closedCustodies() {
            return closed.get();
        }
    }

    private record WorkerOutput(Path root, byte[] result) {
    }

    private record ProtectedControllerFlow(
            DurableFlow durable,
            ShipController controller,
            ShipProtectedWorkerBroker broker,
            ProtectedBackend backend,
            Snapshot catalogSnapshot,
            ShipRunView view) {
    }

    private static BlobReference completeRun(
            DurableFlow flow,
            ValidationFixture validation,
            ShipStamp.Status status,
            List<ShipStamp.Waiver> waivers,
            ShipEventType completionType)
            throws Exception {
        ShipStamp stampValue = new ShipStamp(
                ShipStamp.SCHEMA_VERSION,
                flow.view.runId(),
                status,
                flow.view.adapterId(),
                flow.view.requirementsDigest(),
                flow.view.designDigest(),
                flow.view.artifactManifest().digest(),
                flow.view.candidateSnapshot().digest(),
                flow.view.catalogUsage().digest(),
                validation.result().report().checks(),
                waivers,
                NOW,
                null,
                null);
        BlobReference stamp = fixtureBlob(flow.blobs, "ship-stamp", stampValue);
        try (ShipPublicationService.LivePublication publication
                = ShipPublicationService.apply(flow.view, flow.blobs, stamp, completionType)) {
            BlobReference publishedSnapshot = fixtureBlob(
                    flow.blobs,
                    "project-snapshot",
                    publication.publishedSnapshot());
            flow.commit(
                    ShipAuthorityCommand.value(completionType, stamp.digest()),
                    new ShipEventPayloads.StampRecorded(
                            stamp,
                            publishedSnapshot,
                            validation.result().reportReference()));
            publication.finish();
        }
        return stamp;
    }

    private static void failActiveAttempt(DurableFlow flow) throws Exception {
        StageRequest request = flow.view.activeRequest();
        StageResult result = new StageResult(
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
                "worker-failed",
                "Worker failed safely");
        BlobReference resultReference = fixtureBlob(flow.blobs, "stage-result", result);
        flow.commit(
                ShipAuthorityCommand.empty(ShipEventType.ATTEMPT_FAILED_RETRYABLE),
                new ShipEventPayloads.Failure(
                        request.stage(),
                        result.failureCode(),
                        result.failureMessage(),
                        flow.view.activeRequestReference(),
                        resultReference,
                        null,
                        null,
                        null));
    }

    private static void approveDesign(DurableFlow flow) throws Exception {
        AuthorityBasis basis = flow.view.authority().authority().basis();
        DesignChallenge challenge = designChallenge(flow, basis);
        BlobReference pendingBundle = flow.interactions.record(
                flow.blobs, flow.view.interactionBundle(), challenge);
        flow.commit(
                ShipAuthorityCommand.empty(ShipEventType.DESIGN_APPROVAL_REQUESTED),
                new ShipEventPayloads.DesignApprovalRequested(challenge, pendingBundle));

        DesignResponse unsigned = new DesignResponse(
                Interaction.SCHEMA_VERSION,
                flow.view.runId(),
                challenge.contextDigest(),
                basis.ledgerRevision(),
                basis.ledger().value(),
                basis.requirements().value(),
                basis.policy().value(),
                basis.baseline().value(),
                flow.view.design().digest(),
                challenge.nonce(),
                DesignDecision.APPROVE,
                null,
                "uid:1000",
                "terminal-v1",
                "cli",
                NOW,
                SYNTACTIC_MAC);
        DesignResponse response = new DesignResponse(
                unsigned.schemaVersion(),
                unsigned.runId(),
                unsigned.contextDigest(),
                unsigned.ledgerRevision(),
                unsigned.ledgerDigest(),
                unsigned.requirementsDigest(),
                unsigned.policyDigest(),
                unsigned.baselineDigest(),
                unsigned.designDigest(),
                unsigned.nonce(),
                unsigned.decision(),
                unsigned.requestedChanges(),
                unsigned.controllerObservedProcessPrincipal(),
                unsigned.declaredCliUiProfile(),
                unsigned.channel(),
                unsigned.answeredAt(),
                flow.signer.sign(Interaction.designResponseMacFields(unsigned)));
        BlobReference responseReference = fixtureBlob(
                flow.blobs, "interaction-response", response);
        BlobReference completedBundle = flow.interactions.record(
                flow.blobs, flow.view.interactionBundle(), response);
        flow.commit(
                ShipAuthorityCommand.decision(ShipEventType.DESIGN_APPROVED, null),
                new ShipEventPayloads.DesignApprovalRecorded(
                        response,
                        responseReference,
                        flow.view.design(),
                        completedBundle,
                        null));
    }

    private static void approvePlan(DurableFlow flow) throws Exception {
        AuthorityBasis basis = flow.view.authority().authority().basis();
        PlanChallenge challenge = planChallenge(flow, basis);
        BlobReference pendingBundle = flow.interactions.record(
                flow.blobs, flow.view.interactionBundle(), challenge);
        flow.commit(
                ShipAuthorityCommand.empty(ShipEventType.PLAN_APPROVAL_REQUESTED),
                new ShipEventPayloads.PlanApprovalRequested(challenge, pendingBundle));

        PlanResponse unsigned = new PlanResponse(
                Interaction.SCHEMA_VERSION,
                flow.view.runId(),
                challenge.contextDigest(),
                basis.ledgerRevision(),
                basis.ledger().value(),
                basis.requirements().value(),
                basis.policy().value(),
                basis.baseline().value(),
                flow.view.design().digest(),
                flow.view.plan().digest(),
                challenge.nonce(),
                PlanDecision.APPROVE,
                null,
                "uid:1000",
                "terminal-v1",
                "cli",
                NOW,
                SYNTACTIC_MAC);
        PlanResponse response = new PlanResponse(
                unsigned.schemaVersion(),
                unsigned.runId(),
                unsigned.contextDigest(),
                unsigned.ledgerRevision(),
                unsigned.ledgerDigest(),
                unsigned.requirementsDigest(),
                unsigned.policyDigest(),
                unsigned.baselineDigest(),
                unsigned.approvedDesignDigest(),
                unsigned.planDigest(),
                unsigned.nonce(),
                unsigned.decision(),
                unsigned.requestedChanges(),
                unsigned.controllerObservedProcessPrincipal(),
                unsigned.declaredCliUiProfile(),
                unsigned.channel(),
                unsigned.answeredAt(),
                flow.signer.sign(Interaction.planResponseMacFields(unsigned)));
        BlobReference responseReference = fixtureBlob(
                flow.blobs, "interaction-response", response);
        BlobReference completedBundle = flow.interactions.record(
                flow.blobs, flow.view.interactionBundle(), response);
        flow.commit(
                ShipAuthorityCommand.decision(ShipEventType.PLAN_APPROVED, null),
                new ShipEventPayloads.PlanApprovalRecorded(
                        response,
                        responseReference,
                        flow.view.plan(),
                        completedBundle,
                        null));
    }

    private static DesignChallenge designChallenge(
            DurableFlow flow, AuthorityBasis advertisedBasis)
            throws Exception {
        return designChallenge(flow, advertisedBasis, null);
    }

    private static DesignChallenge designChallenge(
            DurableFlow flow,
            AuthorityBasis advertisedBasis,
            ApprovalBasisField forgedField)
            throws Exception {
        String nonce = flow.signer.nonce();
        String reference = "design.md";
        ApprovalBindings bindings = approvalBindings(flow, advertisedBasis, forgedField);
        String mac = flow.signer.sign(Interaction.designChallengeMacFields(
                flow.view.runId(),
                bindings.contextDigest(),
                bindings.ledgerRevision(),
                bindings.ledgerDigest(),
                bindings.requirementsDigest(),
                bindings.policyDigest(),
                bindings.baselineDigest(),
                flow.view.design().digest(),
                reference,
                nonce));
        return new DesignChallenge(
                Interaction.SCHEMA_VERSION,
                flow.view.runId(),
                bindings.contextDigest(),
                bindings.ledgerRevision(),
                bindings.ledgerDigest(),
                bindings.requirementsDigest(),
                bindings.policyDigest(),
                bindings.baselineDigest(),
                flow.view.design().digest(),
                reference,
                nonce,
                mac);
    }

    private static PlanChallenge planChallenge(
            DurableFlow flow, AuthorityBasis advertisedBasis)
            throws Exception {
        return planChallenge(flow, advertisedBasis, null);
    }

    private static PlanChallenge planChallenge(
            DurableFlow flow,
            AuthorityBasis advertisedBasis,
            ApprovalBasisField forgedField)
            throws Exception {
        String nonce = flow.signer.nonce();
        String reference = "plan.md";
        ApprovalBindings bindings = approvalBindings(flow, advertisedBasis, forgedField);
        String mac = flow.signer.sign(Interaction.planChallengeMacFields(
                flow.view.runId(),
                bindings.contextDigest(),
                bindings.ledgerRevision(),
                bindings.ledgerDigest(),
                bindings.requirementsDigest(),
                bindings.policyDigest(),
                bindings.baselineDigest(),
                flow.view.design().digest(),
                flow.view.plan().digest(),
                reference,
                nonce));
        return new PlanChallenge(
                Interaction.SCHEMA_VERSION,
                flow.view.runId(),
                bindings.contextDigest(),
                bindings.ledgerRevision(),
                bindings.ledgerDigest(),
                bindings.requirementsDigest(),
                bindings.policyDigest(),
                bindings.baselineDigest(),
                flow.view.design().digest(),
                flow.view.plan().digest(),
                reference,
                nonce,
                mac);
    }

    private static String currentContextDigest(DurableFlow flow) throws Exception {
        return ShipJson.mapper()
                .readValue(
                        flow.blobs.readBytes(
                                flow.view.context(), ShipJson.MAX_DOCUMENT_BYTES),
                        InitialContext.class)
                .digest();
    }

    private static ApprovalBindings approvalBindings(
            DurableFlow flow,
            AuthorityBasis basis,
            ApprovalBasisField forgedField)
            throws Exception {
        ApprovalBindings current = new ApprovalBindings(
                currentContextDigest(flow),
                basis.ledgerRevision(),
                basis.ledger().value(),
                basis.requirements().value(),
                basis.policy().value(),
                basis.baseline().value());
        if (forgedField == null) {
            return current;
        }
        String forged = ShipDigest.sha256(
                ("forged-" + forgedField.name()).getBytes(StandardCharsets.UTF_8));
        return switch (forgedField) {
            case CONTEXT -> new ApprovalBindings(
                    forged,
                    current.ledgerRevision(),
                    current.ledgerDigest(),
                    current.requirementsDigest(),
                    current.policyDigest(),
                    current.baselineDigest());
            case LEDGER_REVISION -> new ApprovalBindings(
                    current.contextDigest(),
                    current.ledgerRevision() + 1,
                    current.ledgerDigest(),
                    current.requirementsDigest(),
                    current.policyDigest(),
                    current.baselineDigest());
            case LEDGER -> new ApprovalBindings(
                    current.contextDigest(),
                    current.ledgerRevision(),
                    forged,
                    current.requirementsDigest(),
                    current.policyDigest(),
                    current.baselineDigest());
            case REQUIREMENTS -> new ApprovalBindings(
                    current.contextDigest(),
                    current.ledgerRevision(),
                    current.ledgerDigest(),
                    forged,
                    current.policyDigest(),
                    current.baselineDigest());
            case POLICY -> new ApprovalBindings(
                    current.contextDigest(),
                    current.ledgerRevision(),
                    current.ledgerDigest(),
                    current.requirementsDigest(),
                    forged,
                    current.baselineDigest());
            case BASELINE -> new ApprovalBindings(
                    current.contextDigest(),
                    current.ledgerRevision(),
                    current.ledgerDigest(),
                    current.requirementsDigest(),
                    current.policyDigest(),
                    forged);
        };
    }

    private static BlobReference forgedDesignBundle(
            DurableFlow flow, DesignChallenge challenge)
            throws Exception {
        return fixtureBlob(
                flow.blobs,
                "interaction-bundle",
                new ShipInteractionBundle(
                        ShipInteractionBundle.SCHEMA_VERSION,
                        flow.view.runId(),
                        1,
                        challenge.contextDigest(),
                        List.of(ShipInteractionBundle.Exchange.design(1, challenge))));
    }

    private static BlobReference forgedPlanBundle(
            DurableFlow flow, PlanChallenge challenge)
            throws Exception {
        return fixtureBlob(
                flow.blobs,
                "interaction-bundle",
                new ShipInteractionBundle(
                        ShipInteractionBundle.SCHEMA_VERSION,
                        flow.view.runId(),
                        1,
                        challenge.contextDigest(),
                        List.of(ShipInteractionBundle.Exchange.plan(1, challenge))));
    }

    private static StageResult analysisResult(
            StageRequest request,
            StageResult.Outcome outcome,
            DecisionLedger ledger,
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
                null,
                catalogRequests,
                List.of(),
                null,
                null,
                null);
    }

    private static SourceRef contextSource(Path stateRoot, ShipRunView run) throws Exception {
        InitialContext context = ShipJson.mapper().readValue(
                ShipBlobStore.open(stateRoot, run.runId())
                        .readBytes(run.context(), ShipJson.MAX_DOCUMENT_BYTES),
                InitialContext.class);
        InitialContext.Source source = context.sources().get(0);
        return new SourceRef(
                source.id(), source.provenance(), source.digest(), source.content());
    }

    private static DecisionLedger readyLedger(
            long revision, GapReviewStatus reviewStatus, SourceRef source) {
        List<Category> categories = LedgerValidator.REQUIRED_CATEGORIES.stream()
                .sorted()
                .map(category -> new Category(
                        category, LedgerStatus.RESOLVED, null, List.of(source)))
                .toList();
        List<Entry> decisions = LedgerValidator.REQUIRED_CATEGORIES.stream()
                .sorted()
                .map(category -> new Entry(
                        "decision-" + category,
                        category,
                        decisionValue(category),
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

    private static String decisionValue(String category) {
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

    private Fixture fixture(String name) throws Exception {
        Path stateRoot = temporaryDirectory.resolve(name).toAbsolutePath().normalize();
        Path project = Files.createDirectory(temporaryDirectory.resolve(name + "-project"))
                .toAbsolutePath()
                .normalize();
        Files.writeString(project.resolve("README.md"), "fixture\n");
        ShipRunView created = new ShipController(
                stateRoot, Clock.fixed(NOW, ZoneOffset.UTC))
                .start(new ShipController.PreparedRun(project, "test-adapter"));
        ShipRun authority = created.authority();
        FileShipEventStore events = FileShipEventStore.open(stateRoot, authority.id());
        ShipBlobStore blobs = ShipBlobStore.open(stateRoot, authority.id().toString());
        ShipInteractionSigner signer = ShipInteractionSigner.open(
                stateRoot.resolve(authority.id().toString()));
        ShipInteractionBundleService interactions = new ShipInteractionBundleService(signer);
        return new Fixture(events, blobs, interactions, authority);
    }

    private static ShipRun append(
            Fixture fixture,
            ShipRun previous,
            ShipAuthorityCommand command,
            ShipEventPayloads.Payload data)
            throws Exception {
        ShipRun successor = command.apply(previous);
        fixture.events.appendIfLatest(
                fixture.events.currentHead(), draft(command, data, previous, successor));
        return successor;
    }

    private static BlobReference fixtureBlob(
            ShipBlobStore blobs, String kind, Object value)
            throws Exception {
        return blobs.writeBytes(kind, ShipJson.mapper().writeValueAsBytes(value));
    }

    private static ShipEventDraft draft(
            ShipAuthorityCommand command,
            ShipEventPayloads.Payload data,
            ShipRun previous,
            ShipRun successor) {
        return new ShipEventDraft(
                command.type(),
                successor.state(),
                previous.head(),
                successor.head(),
                NOW.plusSeconds(successor.revision()),
                ShipStoredEventCodec.encode(command, data));
    }

    private enum ApprovalBasisField {
        CONTEXT,
        LEDGER_REVISION,
        LEDGER,
        REQUIREMENTS,
        POLICY,
        BASELINE
    }

    private record ApprovalBindings(
            String contextDigest,
            long ledgerRevision,
            String ledgerDigest,
            String requirementsDigest,
            String policyDigest,
            String baselineDigest) {
    }

    private record PendingWaiver(
            DurableFlow flow,
            BlobReference evidence,
            WaiverChallenge challenge,
            ValidationFixture validation) {
    }

    private record ValidationFixture(
            ShipValidationService.Result result,
            BlobReference resultReference,
            ShipWorkspaceService.AcceptedArtifact accepted,
            BlobReference failedEvidence) {
    }

    private static final class DurableFlow {

        private final FileShipEventStore events;
        private final ShipBlobStore blobs;
        private final ShipInteractionBundleService interactions;
        private final ShipInteractionSigner signer;
        private final Snapshot catalogSnapshot;
        private ShipRunView view;

        private DurableFlow(
                            FileShipEventStore events,
                            ShipBlobStore blobs,
                            ShipInteractionBundleService interactions,
                            ShipInteractionSigner signer,
                            Snapshot catalogSnapshot,
                            ShipRunView view) {
            this.events = events;
            this.blobs = blobs;
            this.interactions = interactions;
            this.signer = signer;
            this.catalogSnapshot = catalogSnapshot;
            this.view = view;
        }

        private ShipRunProjector projector() {
            return new ShipRunProjector(events, blobs, interactions);
        }

        private void commit(
                ShipAuthorityCommand command, ShipEventPayloads.Payload data)
                throws Exception {
            commit(command, data, command.apply(view.authority()));
        }

        private void commit(
                ShipAuthorityCommand command,
                ShipEventPayloads.Payload data,
                ShipRun successor)
                throws Exception {
            ShipRun previous = view.authority();
            projector().preflight(command, data, successor);
            events.appendIfLatest(
                    events.currentHead(), draft(command, data, previous, successor));
            view = projector().replay();
        }
    }

    private static final class Fixture {

        private final FileShipEventStore events;
        private final ShipBlobStore blobs;
        private final ShipInteractionBundleService interactions;
        private ShipRun authority;

        private Fixture(
                        FileShipEventStore events,
                        ShipBlobStore blobs,
                        ShipInteractionBundleService interactions,
                        ShipRun authority) {
            this.events = events;
            this.blobs = blobs;
            this.interactions = interactions;
            this.authority = authority;
        }

        private ShipRunProjector projector() {
            return new ShipRunProjector(events, blobs, interactions);
        }
    }
}
