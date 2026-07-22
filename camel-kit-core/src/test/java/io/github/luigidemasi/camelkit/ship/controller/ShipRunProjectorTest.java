package io.github.luigidemasi.camelkit.ship.controller;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import io.github.luigidemasi.camelkit.ship.ShipDigest;
import io.github.luigidemasi.camelkit.ship.artifact.ArtifactManifest;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogSubject;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogTestVerifier;
import io.github.luigidemasi.camelkit.ship.context.InitialContext;
import io.github.luigidemasi.camelkit.ship.context.InitialContextRequest;
import io.github.luigidemasi.camelkit.ship.controller.ShipAttemptFactory.AttemptInputs;
import io.github.luigidemasi.camelkit.ship.controller.ShipBlobStore.BlobReference;
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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        acceptValidation(flow);
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
        acceptValidation(flow);
        flow.commit(
                ShipAuthorityCommand.empty(ShipEventType.STAMP_STARTED),
                new ShipEventPayloads.NoData());
        assertEquals(ShipState.STAMP_RUNNING, flow.view.state());

        BlobReference stamp = flow.blobs.writeBytes(
                "ship-stamp", "controller stamp".getBytes(StandardCharsets.UTF_8));
        flow.commit(
                ShipAuthorityCommand.value(ShipEventType.RUN_COMPLETED, stamp.digest()),
                new ShipEventPayloads.StampRecorded(stamp, null, null));

        assertEquals(ShipState.COMPLETED, flow.view.state());
        assertEquals(stamp, flow.view.stamp());
        assertEquals(flow.view, flow.projector().replay());
    }

    @Test
    void waivableFailurePreservesInteractionsThroughWaiverAndStampReplay() throws Exception {
        DurableFlow flow = validationRunningFlow("waiver-replay");
        BlobReference originalBundle = flow.view.interactionBundle();
        BlobReference evidence = flow.blobs.writeBytes(
                "validation-report", "waivable validation failure".getBytes(StandardCharsets.UTF_8));
        String checkId = ShipDigest.sha256("waivable-check".getBytes(StandardCharsets.UTF_8));
        String policy = flow.view.authority().authority().basis().policy().value();
        ShipAuthorityCommand failureCommand = ShipAuthorityCommand.waiver(
                checkId, evidence.digest(), policy);
        flow.commit(
                failureCommand,
                new ShipEventPayloads.Failure(
                        ShipStage.VALIDATE,
                        "waivable-check-failed",
                        "The validation check requires explicit waiver",
                        flow.view.activeRequestReference(),
                        null,
                        evidence,
                        null,
                        null));

        assertEquals(ShipState.WAIVER_ELIGIBLE, flow.view.state());
        assertEquals(originalBundle, flow.view.interactionBundle());

        String subjectDigest = ShipDigest.sha256("waiver-subject".getBytes(StandardCharsets.UTF_8));
        String nonce = flow.signer.nonce();
        WaiverChallenge unsignedChallenge = new WaiverChallenge(
                Interaction.SCHEMA_VERSION,
                flow.view.runId(),
                checkId,
                evidence.digest(),
                policy,
                subjectDigest,
                "validation-report",
                "The failed check may affect runtime safety",
                "The user accepts the documented risk",
                nonce,
                SYNTACTIC_MAC);
        WaiverChallenge challenge = new WaiverChallenge(
                unsignedChallenge.schemaVersion(),
                unsignedChallenge.runId(),
                unsignedChallenge.checkId(),
                unsignedChallenge.evidenceDigest(),
                unsignedChallenge.eligibilityPolicyDigest(),
                unsignedChallenge.subjectDigest(),
                unsignedChallenge.subjectReference(),
                unsignedChallenge.risk(),
                unsignedChallenge.consequence(),
                unsignedChallenge.nonce(),
                flow.signer.sign(Interaction.waiverChallengeMacFields(unsignedChallenge)));
        BlobReference pendingBundle = flow.interactions.record(
                flow.blobs, flow.view.interactionBundle(), challenge);
        flow.commit(
                ShipAuthorityCommand.empty(ShipEventType.WAIVER_REQUESTED),
                new ShipEventPayloads.WaiverRequested(challenge, pendingBundle));

        WaiverResponse unsignedResponse = new WaiverResponse(
                Interaction.SCHEMA_VERSION,
                flow.view.runId(),
                challenge.checkId(),
                challenge.evidenceDigest(),
                challenge.eligibilityPolicyDigest(),
                challenge.subjectDigest(),
                challenge.nonce(),
                WaiverDecision.WAIVE,
                "Accept the bounded risk",
                "uid:1000",
                "terminal-v1",
                "cli",
                NOW,
                SYNTACTIC_MAC);
        WaiverResponse response = new WaiverResponse(
                unsignedResponse.schemaVersion(),
                unsignedResponse.runId(),
                unsignedResponse.checkId(),
                unsignedResponse.evidenceDigest(),
                unsignedResponse.eligibilityPolicyDigest(),
                unsignedResponse.subjectDigest(),
                unsignedResponse.nonce(),
                unsignedResponse.decision(),
                unsignedResponse.reason(),
                unsignedResponse.controllerObservedProcessPrincipal(),
                unsignedResponse.declaredCliUiProfile(),
                unsignedResponse.channel(),
                unsignedResponse.answeredAt(),
                flow.signer.sign(Interaction.waiverResponseMacFields(unsignedResponse)));
        BlobReference responseReference = fixtureBlob(
                flow.blobs, "interaction-response", response);
        BlobReference completedBundle = flow.interactions.record(
                flow.blobs, flow.view.interactionBundle(), response);
        flow.commit(
                ShipAuthorityCommand.decision(ShipEventType.WAIVER_RECORDED, null),
                new ShipEventPayloads.WaiverRecorded(
                        response, responseReference, completedBundle));
        assertEquals(ShipState.WAIVER_RECORDED, flow.view.state());

        flow.commit(
                ShipAuthorityCommand.empty(ShipEventType.WAIVER_STAMP_STARTED),
                new ShipEventPayloads.NoData());
        BlobReference stamp = flow.blobs.writeBytes(
                "ship-stamp", "controller waiver stamp".getBytes(StandardCharsets.UTF_8));
        flow.commit(
                ShipAuthorityCommand.value(
                        ShipEventType.RUN_COMPLETED_WITH_WAIVER, stamp.digest()),
                new ShipEventPayloads.StampRecorded(stamp, null, evidence));

        assertEquals(ShipState.COMPLETED_WITH_WAIVER, flow.view.state());
        assertEquals(stamp, flow.view.stamp());
        assertEquals(completedBundle, flow.view.interactionBundle());
        assertEquals(flow.view, flow.projector().replay());
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
        ShipRunView continued = controller.submitStageResult(
                discovery.runId(),
                discovery.eventDigest(),
                ShipJson.mapper().writeValueAsBytes(analysisResult(
                        discovery.activeRequest(),
                        StageResult.Outcome.NEEDS_DISCOVERY,
                        first,
                        List.of(new CatalogSubject(
                                CatalogSubject.Kind.COMPONENT, "direct")))),
                CatalogTestVerifier.mainSnapshot(catalogRoot));
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
        byte[] content = "generated route".getBytes(StandardCharsets.UTF_8);
        BlobReference artifact = flow.blobs.writeBytes("route", content);
        ProducedArtifact claim = new ProducedArtifact(
                "route", "routes/generated.camel.yaml", artifact.digest(), artifact.byteSize());
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
                List.of(),
                List.of(),
                List.of(),
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
                List.of(claim),
                manifest,
                null,
                null);
        BlobReference resultReference = fixtureBlob(flow.blobs, "stage-result", result);
        BlobReference manifestReference = fixtureBlob(
                flow.blobs, "artifact-manifest", manifest);
        BlobReference catalogUsage = fixtureBlob(
                flow.blobs, "catalog-usage", java.util.Map.of());
        Path candidateDirectory = Path.of(request.candidateDirectory());
        BlobReference candidateSnapshot = fixtureBlob(
                flow.blobs,
                "project-snapshot",
                ProjectEvidenceFiles.captureSealed(candidateDirectory));
        ShipWorkspaceService.AcceptedArtifact accepted = new ShipWorkspaceService.AcceptedArtifact(
                claim, artifact, 0100644);
        flow.commit(
                ShipAuthorityCommand.value(
                        ShipEventType.EXECUTION_VALIDATED, resultReference.digest()),
                new ShipEventPayloads.StageAccepted(
                        request.stage(),
                        flow.view.activeRequestReference(),
                        resultReference,
                        List.of(accepted),
                        null,
                        manifestReference,
                        null,
                        catalogUsage,
                        null,
                        null,
                        candidateSnapshot,
                        candidateDirectory.toString(),
                        null));
    }

    private static void acceptValidation(DurableFlow flow) throws Exception {
        StageRequest request = flow.view.activeRequest();
        byte[] content = "validation passed".getBytes(StandardCharsets.UTF_8);
        BlobReference artifact = flow.blobs.writeBytes("validation", content);
        ProducedArtifact claim = new ProducedArtifact(
                "validation", "validation.md", artifact.digest(), artifact.byteSize());
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
        flow.commit(
                ShipAuthorityCommand.value(
                        ShipEventType.VALIDATION_PASSED, resultReference.digest()),
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
                        null,
                        null,
                        null,
                        null));
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

    private static final class DurableFlow {

        private final FileShipEventStore events;
        private final ShipBlobStore blobs;
        private final ShipInteractionBundleService interactions;
        private final ShipInteractionSigner signer;
        private ShipRunView view;

        private DurableFlow(
                            FileShipEventStore events,
                            ShipBlobStore blobs,
                            ShipInteractionBundleService interactions,
                            ShipInteractionSigner signer,
                            ShipRunView view) {
            this.events = events;
            this.blobs = blobs;
            this.interactions = interactions;
            this.signer = signer;
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
