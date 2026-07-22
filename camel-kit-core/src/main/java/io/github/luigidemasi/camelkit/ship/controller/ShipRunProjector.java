package io.github.luigidemasi.camelkit.ship.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.github.luigidemasi.camelkit.ship.ShipDigest;
import io.github.luigidemasi.camelkit.ship.artifact.ArtifactManifest;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogEvidenceSet;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogTarget;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogUsageRecord;
import io.github.luigidemasi.camelkit.ship.context.InitialContext;
import io.github.luigidemasi.camelkit.ship.controller.ShipBlobStore.BlobReference;
import io.github.luigidemasi.camelkit.ship.ledger.DecisionLedger;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.ConsentDecision;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.DesignDecision;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.PlanDecision;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.WaiverDecision;
import io.github.luigidemasi.camelkit.ship.protocol.ShipStage;
import io.github.luigidemasi.camelkit.ship.protocol.StageRequest;
import io.github.luigidemasi.camelkit.ship.protocol.StageResult;
import io.github.luigidemasi.camelkit.ship.protocol.StageResultValidator;
import io.github.luigidemasi.camelkit.ship.security.ProjectEvidenceFiles;
import io.github.luigidemasi.camelkit.ship.security.ProjectSnapshot;

/** Reconstructs the only trusted run view from MAC-verified events and content-addressed blobs. */
final class ShipRunProjector {

    private final FileShipEventStore events;
    private final ShipBlobStore blobs;
    private final ShipInteractionBundleService interactions;
    private final ShipAttemptFactory attemptFactory = new ShipAttemptFactory();

    ShipRunProjector(
                     FileShipEventStore events,
                     ShipBlobStore blobs,
                     ShipInteractionBundleService interactions) {
        this.events = Objects.requireNonNull(events, "events");
        this.blobs = Objects.requireNonNull(blobs, "blobs");
        this.interactions = Objects.requireNonNull(interactions, "interactions");
    }

    ShipRunView replay() throws IOException {
        return replayResult().view();
    }

    ReplayResult replayResult() throws IOException {
        List<ShipEvent> history = List.copyOf(events.replay());
        return new ReplayResult(replayProjection(history).view(), history);
    }

    void preflightCreated(
            ShipRun expectedCreated, ShipEventPayloads.RunCreated data)
            throws IOException {
        Objects.requireNonNull(expectedCreated, "expected created run");
        ShipStoredEventCodec.StoredEvent stored = decodeExact(
                ShipAuthorityCommand.empty(ShipEventType.RUN_CREATED), data);
        if (expectedCreated.state() != ShipState.CREATED
                || expectedCreated.revision() != 0
                || expectedCreated.lastEvent() != ShipEventType.RUN_CREATED) {
            throw new IOException("Expected lifecycle authority is not a canonical created run");
        }
        Projection projection = new Projection();
        applyCreated(
                projection,
                expectedCreated,
                ShipEventHead.GENESIS_DIGEST,
                stored);
        projection.view();
    }

    void preflight(
            ShipAuthorityCommand command,
            ShipEventPayloads.Payload data,
            ShipRun expectedSuccessor)
            throws IOException {
        Objects.requireNonNull(command, "authority command");
        Objects.requireNonNull(expectedSuccessor, "expected successor");
        ShipStoredEventCodec.StoredEvent stored = decodeExact(command, data);
        Projection projection = replayProjection();
        attemptFactory.beginVerificationScope();
        ShipRun previous = projection.authority;
        try {
            ShipRun successor = stored.authority().apply(previous, expectedSuccessor.head());
            if (!successor.equals(expectedSuccessor)) {
                throw new IOException("Expected lifecycle successor disagrees with its authority command");
            }
            applyData(projection, command.type(), stored, successor);
            projection.authority = successor;
            projection.view();
        } catch (IOException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IOException(
                    "Invalid preflight for Ship event " + command.type().stableId() + ": "
                                  + e.getMessage(),
                    e);
        }
    }

    private Projection replayProjection() throws IOException {
        return replayProjection(List.copyOf(events.replay()));
    }

    private Projection replayProjection(List<ShipEvent> history) throws IOException {
        attemptFactory.beginVerificationScope();
        if (history.isEmpty()) {
            throw new IOException("Ship run has no authoritative events");
        }
        Projection projection = new Projection();
        for (ShipEvent event : history) {
            apply(projection, event);
        }
        return projection;
    }

    private static ShipStoredEventCodec.StoredEvent decodeExact(
            ShipAuthorityCommand command, ShipEventPayloads.Payload data)
            throws IOException {
        try {
            return ShipStoredEventCodec.decode(
                    command.type(), ShipStoredEventCodec.encode(command, data));
        } catch (IOException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IOException(
                    "Invalid encoded Ship event " + command.type().stableId() + ": "
                                  + e.getMessage(),
                    e);
        }
    }

    private void apply(Projection projection, ShipEvent event) throws IOException {
        ShipStoredEventCodec.StoredEvent stored = ShipStoredEventCodec.decode(event);
        try {
            if (projection.authority == null) {
                if (event.sequence() != 1
                        || event.type() != ShipEventType.RUN_CREATED
                        || event.previousState() != null
                        || event.previousAuthorityHead() != null
                        || event.state() != ShipState.CREATED) {
                    throw new IOException("First Ship event must be the canonical run creation");
                }
                applyCreated(
                        projection,
                        new ShipRun(
                                event.runId(),
                                event.authorityHead(),
                                ShipState.CREATED,
                                0,
                                ShipEventType.RUN_CREATED,
                                AuthorityData.empty()),
                        event.eventDigest(),
                        stored);
                return;
            }
            ShipRun previous = projection.authority;
            if (!previous.id().equals(event.runId())
                    || previous.state() != event.previousState()
                    || !previous.head().equals(event.previousAuthorityHead())
                    || event.sequence() != Math.addExact(previous.revision(), 2)) {
                throw new IOException("Ship event does not extend the reconstructed lifecycle head");
            }
            ShipRun successor = stored.authority().apply(previous, event.authorityHead());
            if (!successor.id().equals(event.runId())
                    || successor.state() != event.state()
                    || successor.lastEvent() != event.type()
                    || successor.revision() != event.sequence() - 1
                    || !successor.head().equals(event.authorityHead())) {
                throw new IOException("Ship event metadata disagrees with its lifecycle transition");
            }
            applyData(projection, event.type(), stored, successor);
            projection.authority = successor;
            projection.eventDigest = event.eventDigest();
        } catch (IOException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IOException(
                    "Invalid Ship event " + event.sequence() + " (" + event.type().stableId() + "): "
                                  + e.getMessage(),
                    e);
        }
    }

    private void applyCreated(
            Projection projection,
            ShipRun createdAuthority,
            String eventDigest,
            ShipStoredEventCodec.StoredEvent stored)
            throws IOException {
        if (stored.authority().type() != ShipEventType.RUN_CREATED
                || !(stored.data() instanceof ShipEventPayloads.RunCreated created)) {
            throw new IOException("First Ship event must be the canonical run creation");
        }
        Path projectRoot = absolutePath(created.projectRoot(), "project root");
        Path sourceDirectory = absolutePath(created.sourceDirectory(), "source directory");
        requireAdapter(created.adapterId());
        if (!sourceDirectory.equals(blobs.expectedSourceDirectory())) {
            throw new IOException("Immutable project source is outside the protected run directory");
        }
        if (created.nativeSessionEvidence() != null) {
            NativeSessionEvidence.verify(
                    blobs,
                    interactions.signer(),
                    createdAuthority.id().toString(),
                    created.adapterId(),
                    created.nativeSessionEvidence());
        }
        ProjectSnapshot baseline = read(created.baselineSnapshot(), "project-snapshot", ProjectSnapshot.class);
        ProjectSnapshot source = read(created.sourceSnapshot(), "project-snapshot", ProjectSnapshot.class);
        ProjectSourceManifest manifest = read(
                created.projectSourceManifest(),
                "project-source-manifest",
                ProjectSourceManifest.class);
        if (!Path.of(baseline.root()).equals(projectRoot)
                || !Path.of(source.root()).equals(sourceDirectory)
                || !ProjectEvidenceFiles.unchangedMaterialTree(baseline, source)
                || !ProjectSourceManifest.from(source).equals(manifest)) {
            throw new IOException("Protected project source does not match its captured baseline and manifest");
        }

        projection.authority = createdAuthority;
        projection.eventDigest = eventDigest;
        projection.projectRoot = projectRoot;
        projection.adapterId = created.adapterId();
        projection.nativeSessionEvidence = created.nativeSessionEvidence();
        projection.baselineSnapshot = created.baselineSnapshot();
        projection.sourceSnapshot = created.sourceSnapshot();
        projection.projectSourceManifest = created.projectSourceManifest();
        projection.sourceDirectory = sourceDirectory;
    }

    private void applyData(
            Projection projection,
            ShipEventType event,
            ShipStoredEventCodec.StoredEvent stored,
            ShipRun successor)
            throws IOException {
        ShipEventPayloads.Payload data = stored.data();
        switch (event) {
            case RUN_CREATED -> throw new IOException("RUN_CREATED can occur only once");
            case CONTEXT_RESOLUTION_STARTED -> {
                ShipEventPayloads.ContextResolutionStarted value = cast(
                        data, ShipEventPayloads.ContextResolutionStarted.class);
                verify(value.input(), "initial-context-request");
                ShipInitialContextRequestCodec.decode(
                        blobs.readBytes(value.input(), ShipJson.MAX_DOCUMENT_BYTES));
                requireContent(stored.authority(), value.input().digest(), "context input");
                projection.contextRequest = value.input();
            }
            case CONTEXT_RECORDED -> {
                ShipEventPayloads.ContextRecorded value = cast(
                        data, ShipEventPayloads.ContextRecorded.class);
                verify(value.context(), "initial-context");
                verify(value.interactionBundle(), "interaction-bundle");
                requireContent(stored.authority(), value.context().digest(), "initial context");
                InitialContext context = read(value.context(), InitialContext.class);
                ShipInteractionBundle bundle = interactions.read(blobs, value.interactionBundle());
                if (!bundle.runId().equals(successor.id().toString())
                        || !bundle.contextDigest().equals(context.digest())) {
                    throw new IOException(
                            "Initial interaction bundle does not bind the recorded Ship context");
                }
                projection.context = value.context();
                projection.interactionBundle = value.interactionBundle();
                projection.pendingInteraction = null;
            }
            case DISCOVERY_STARTED,
                    DESIGN_STARTED,
                    PLAN_STARTED,
                    EXECUTION_STARTED,
                    VALIDATION_STARTED,
                    RETRY_STARTED ->
                applyStageStarted(
                        projection,
                        cast(data, ShipEventPayloads.StageStarted.class),
                        successor,
                        event);
            case REQUIREMENTS_INPUTS_CHANGED -> {
                invalidateRequirementsAndLater(projection);
                applyStageStarted(
                        projection,
                        cast(data, ShipEventPayloads.StageStarted.class),
                        successor,
                        event);
            }
            case DESIGN_INPUTS_CHANGED -> {
                invalidateDesignAndLater(projection);
                applyStageStarted(
                        projection,
                        cast(data, ShipEventPayloads.StageStarted.class),
                        successor,
                        event);
            }
            case PLAN_INPUTS_CHANGED -> {
                invalidatePlanAndLater(projection);
                applyStageStarted(
                        projection,
                        cast(data, ShipEventPayloads.StageStarted.class),
                        successor,
                        event);
            }
            case DOCUMENT_CONSENT_REQUESTED -> {
                ShipEventPayloads.DocumentConsentRequested value = cast(
                        data, ShipEventPayloads.DocumentConsentRequested.class);
                requireRun(value.challenge().runId(), successor);
                requireContent(
                        stored.authority(), value.challenge().physicalIdentityDigest(), "document consent");
                ShipInteractionBundle.Exchange exchange = applyPending(
                        projection, value.interactionBundle());
                if (exchange.documentConsent() == null
                        || !exchange.documentConsent().challenge().equals(value.challenge())) {
                    throw new IOException("Interaction bundle does not bind the document-consent challenge");
                }
            }
            case DOCUMENT_CONSENT_ACCEPTED, DOCUMENT_CONSENT_DENIED -> {
                ShipEventPayloads.DocumentConsentRecorded value = cast(
                        data, ShipEventPayloads.DocumentConsentRecorded.class);
                requireRun(value.response().runId(), successor);
                boolean accepted = value.response().decision() == ConsentDecision.AUTHORIZE;
                if (accepted != (event == ShipEventType.DOCUMENT_CONSENT_ACCEPTED)) {
                    throw new IOException("Document-consent response disagrees with its lifecycle event");
                }
                requireBlobValue(value.responseReference(), value.response());
                ShipInteractionBundle.Exchange exchange = applyResponse(
                        projection, value.responseReference(), value.interactionBundle());
                if (exchange.documentConsent() == null
                        || !exchange.documentConsent().response().equals(value.response())) {
                    throw new IOException("Interaction bundle does not bind the document-consent response");
                }
            }
            case REMOTE_USE_CONSENT_REQUESTED -> {
                ShipEventPayloads.RemoteProviderConsentRequested value = cast(
                        data, ShipEventPayloads.RemoteProviderConsentRequested.class);
                requireRun(value.challenge().runId(), successor);
                requireContent(stored.authority(), value.challenge().contentDigest(), "remote consent");
                ShipInteractionBundle.Exchange exchange = applyPending(
                        projection, value.interactionBundle());
                if (exchange.remoteProviderConsent() == null
                        || !exchange.remoteProviderConsent().challenge().equals(value.challenge())) {
                    throw new IOException("Interaction bundle does not bind the remote-consent challenge");
                }
                projection.activeRequest = null;
                projection.activeRequestReference = null;
            }
            case REMOTE_USE_CONSENT_ACCEPTED, REMOTE_USE_CONSENT_DENIED -> {
                ShipEventPayloads.RemoteProviderConsentRecorded value = cast(
                        data, ShipEventPayloads.RemoteProviderConsentRecorded.class);
                requireRun(value.response().runId(), successor);
                boolean accepted = value.response().decision() == ConsentDecision.AUTHORIZE;
                if (accepted != (event == ShipEventType.REMOTE_USE_CONSENT_ACCEPTED)) {
                    throw new IOException("Remote-consent response disagrees with its lifecycle event");
                }
                requireBlobValue(value.responseReference(), value.response());
                ShipInteractionBundle.Exchange exchange = applyResponse(
                        projection, value.responseReference(), value.interactionBundle());
                if (exchange.remoteProviderConsent() == null
                        || !exchange.remoteProviderConsent().response().equals(value.response())) {
                    throw new IOException("Interaction bundle does not bind the remote-consent response");
                }
                applyContinuation(
                        projection,
                        value.continuation(),
                        successor,
                        event,
                        true);
            }
            case DISCOVERY_QUESTION_PRESENTED -> {
                ShipEventPayloads.DiscoveryQuestionPresented value = cast(
                        data, ShipEventPayloads.DiscoveryQuestionPresented.class);
                requireRun(value.challenge().runId(), successor);
                requireContent(stored.authority(), value.challenge().questionId(), "discovery question");
                requireActiveRequest(projection, value.request());
                verify(value.result(), "stage-result");
                verify(value.ledger(), "decision-ledger");
                StageResult result = read(value.result(), StageResult.class);
                StageResultValidator.validateEnvelope(
                        projection.activeRequest,
                        result,
                        Path.of(projection.activeRequest.outputDirectory()));
                DecisionLedger ledger = read(value.ledger(), DecisionLedger.class);
                DecisionLedger.Question question = result.question();
                if (result.outcome() != StageResult.Outcome.NEEDS_USER_INPUT
                        || !ledger.equals(result.ledger())
                        || question == null
                        || value.challenge().ledgerRevision() != ledger.revision()
                        || !value.challenge().questionId().equals(question.id())
                        || !value.challenge().openItemId().equals(question.openItemId())
                        || !value.challenge().prompt().equals(question.prompt())
                        || !value.challenge().options().equals(question.options())
                        || !Objects.equals(
                                value.challenge().recommendation(), question.recommendation())) {
                    throw new IOException(
                            "Discovery challenge does not bind the exact worker question and ledger");
                }
                validateLedgerProvenance(projection, projection.activeRequest, result);
                projection.ledger = value.ledger();
                clearFailure(projection);
                ShipInteractionBundle.Exchange exchange = applyPending(
                        projection, value.interactionBundle());
                if (exchange.discovery() == null
                        || !exchange.discovery().challenge().equals(value.challenge())) {
                    throw new IOException("Interaction bundle does not bind the discovery challenge");
                }
                projection.activeRequest = null;
                projection.activeRequestReference = null;
            }
            case DISCOVERY_ANSWER_RECORDED -> {
                ShipEventPayloads.DiscoveryAnswerRecorded value = cast(
                        data, ShipEventPayloads.DiscoveryAnswerRecorded.class);
                requireRun(value.answer().runId(), successor);
                verify(value.answerReference(), "interaction-response");
                requireContent(stored.authority(), value.answerReference().digest(), "discovery answer");
                verify(value.request(), "stage-request");
                requireBlobValue(value.answerReference(), value.answer());
                ShipInteractionBundle.Exchange exchange = applyResponse(
                        projection, value.answerReference(), value.interactionBundle());
                if (exchange.discovery() == null
                        || !exchange.discovery().answer().equals(value.answer())) {
                    throw new IOException("Interaction bundle does not bind the discovery answer");
                }
                StageRequest request = read(value.request(), StageRequest.class);
                applyStageStarted(
                        projection,
                        new ShipEventPayloads.StageStarted(
                                ShipStage.DISCOVERY,
                                value.request(),
                                request.outputDirectory()),
                        successor,
                        event);
            }
            case DISCOVERY_CONTINUED,
                    GAP_REVIEW_STARTED,
                    GAP_REVIEW_REOPENED,
                    DESIGN_GAPS_FOUND,
                    REQUIREMENTS_READY,
                    DESIGN_READY,
                    PLAN_VALIDATED,
                    EXECUTION_VALIDATED,
                    VALIDATION_PASSED ->
                applyStageAccepted(
                        projection,
                        cast(data, ShipEventPayloads.StageAccepted.class),
                        successor,
                        stored.authority(),
                        event);
            case DESIGN_APPROVAL_REQUESTED -> {
                ShipEventPayloads.DesignApprovalRequested value = cast(
                        data, ShipEventPayloads.DesignApprovalRequested.class);
                requireRun(value.challenge().runId(), successor);
                requireApprovalBasis(
                        projection,
                        successor.authority().basis(),
                        value.challenge().contextDigest(),
                        value.challenge().ledgerRevision(),
                        value.challenge().ledgerDigest(),
                        value.challenge().requirementsDigest(),
                        value.challenge().policyDigest(),
                        value.challenge().baselineDigest(),
                        "design approval");
                requireDigest(value.challenge().designDigest(), projection.design, "design approval");
                ShipInteractionBundle.Exchange exchange = applyPending(
                        projection, value.interactionBundle());
                if (exchange.design() == null
                        || !exchange.design().challenge().equals(value.challenge())) {
                    throw new IOException("Interaction bundle does not bind the design challenge");
                }
            }
            case DESIGN_APPROVED,
                    DESIGN_APPROVAL_DENIED,
                    DESIGN_REQUIREMENTS_CHANGES_REQUESTED,
                    DESIGN_APPROVAL_ABORTED -> {
                ShipEventPayloads.DesignApprovalRecorded value = cast(
                        data, ShipEventPayloads.DesignApprovalRecorded.class);
                requireRun(value.response().runId(), successor);
                if (designEvent(value.response().decision()) != event) {
                    throw new IOException("Design response disagrees with its lifecycle event");
                }
                verify(value.design(), "design");
                requireDigest(value.response().designDigest(), value.design(), "design response");
                requireBlobValue(value.responseReference(), value.response());
                ShipInteractionBundle.Exchange exchange = applyResponse(
                        projection, value.responseReference(), value.interactionBundle());
                if (exchange.design() == null
                        || !exchange.design().response().equals(value.response())) {
                    throw new IOException("Interaction bundle does not bind the design response");
                }
                applyDesignDecisionProjection(
                        projection, value, successor, stored.authority(), event);
            }
            case PLAN_APPROVAL_REQUESTED -> {
                ShipEventPayloads.PlanApprovalRequested value = cast(
                        data, ShipEventPayloads.PlanApprovalRequested.class);
                requireRun(value.challenge().runId(), successor);
                requireApprovalBasis(
                        projection,
                        successor.authority().basis(),
                        value.challenge().contextDigest(),
                        value.challenge().ledgerRevision(),
                        value.challenge().ledgerDigest(),
                        value.challenge().requirementsDigest(),
                        value.challenge().policyDigest(),
                        value.challenge().baselineDigest(),
                        "plan approval");
                requireDigest(value.challenge().planDigest(), projection.plan, "plan approval");
                requireDigest(value.challenge().approvedDesignDigest(), projection.design, "plan approval design");
                ShipInteractionBundle.Exchange exchange = applyPending(
                        projection, value.interactionBundle());
                if (exchange.plan() == null
                        || !exchange.plan().challenge().equals(value.challenge())) {
                    throw new IOException("Interaction bundle does not bind the plan challenge");
                }
            }
            case PLAN_APPROVED,
                    PLAN_APPROVAL_DENIED,
                    PLAN_DESIGN_CHANGES_REQUESTED,
                    PLAN_REQUIREMENTS_CHANGES_REQUESTED,
                    PLAN_APPROVAL_ABORTED -> {
                ShipEventPayloads.PlanApprovalRecorded value = cast(
                        data, ShipEventPayloads.PlanApprovalRecorded.class);
                requireRun(value.response().runId(), successor);
                if (planEvent(value.response().decision()) != event) {
                    throw new IOException("Plan response disagrees with its lifecycle event");
                }
                verify(value.plan(), "plan");
                requireDigest(value.response().planDigest(), value.plan(), "plan response");
                requireBlobValue(value.responseReference(), value.response());
                ShipInteractionBundle.Exchange exchange = applyResponse(
                        projection, value.responseReference(), value.interactionBundle());
                if (exchange.plan() == null
                        || !exchange.plan().response().equals(value.response())) {
                    throw new IOException("Interaction bundle does not bind the plan response");
                }
                applyPlanDecisionProjection(
                        projection, value, successor, stored.authority(), event);
            }
            case ATTEMPT_FAILED_RETRYABLE ->
                applyAttemptFailure(
                        projection, cast(data, ShipEventPayloads.Failure.class));
            case WAIVABLE_FAILURE_RECORDED ->
                applyWaivableFailure(
                        projection, cast(data, ShipEventPayloads.Failure.class));
            case RUN_FAILED_TERMINAL,
                    RUN_ABORTED ->
                applyFailure(
                        projection, cast(data, ShipEventPayloads.Failure.class));
            case WAIVER_REQUESTED -> {
                ShipEventPayloads.WaiverRequested value = cast(
                        data, ShipEventPayloads.WaiverRequested.class);
                requireRun(value.challenge().runId(), successor);
                ShipInteractionBundle.Exchange exchange = applyPending(
                        projection, value.interactionBundle());
                if (exchange.waiver() == null
                        || !exchange.waiver().challenge().equals(value.challenge())) {
                    throw new IOException("Interaction bundle does not bind the waiver challenge");
                }
            }
            case WAIVER_RECORDED, WAIVER_DENIED -> {
                ShipEventPayloads.WaiverRecorded value = cast(
                        data, ShipEventPayloads.WaiverRecorded.class);
                requireRun(value.response().runId(), successor);
                boolean approved = value.response().decision() == WaiverDecision.WAIVE;
                if (approved != (event == ShipEventType.WAIVER_RECORDED)) {
                    throw new IOException("Waiver response disagrees with its lifecycle event");
                }
                requireBlobValue(value.responseReference(), value.response());
                ShipInteractionBundle.Exchange exchange = applyResponse(
                        projection, value.responseReference(), value.interactionBundle());
                if (exchange.waiver() == null
                        || !exchange.waiver().response().equals(value.response())) {
                    throw new IOException("Interaction bundle does not bind the waiver response");
                }
            }
            case STAMP_STARTED, WAIVER_STAMP_STARTED ->
                cast(data, ShipEventPayloads.NoData.class);
            case RUN_COMPLETED, RUN_COMPLETED_WITH_WAIVER -> {
                ShipEventPayloads.StampRecorded value = cast(
                        data, ShipEventPayloads.StampRecorded.class);
                verify(value.stamp(), "ship-stamp");
                verify(value.publishedSnapshot(), "project-snapshot");
                verify(value.validationReport(), "validation-report");
                requireContent(stored.authority(), value.stamp().digest(), "stamp completion");
                if (!value.validationReport().equals(projection.validationReport)
                        || projection.artifactManifest == null
                        || projection.candidateSnapshot == null
                        || projection.catalogUsage == null
                        || projection.candidateDirectory == null) {
                    throw new IOException("Stamp completion differs from validated durable inputs");
                }
                ShipValidationReport report = read(
                        value.validationReport(), ShipValidationReport.class);
                ShipStamp stamp = read(value.stamp(), ShipStamp.class);
                ShipStamp.Status expectedStatus = stored.authority().type() == ShipEventType.RUN_COMPLETED
                        ? ShipStamp.Status.PASS : ShipStamp.Status.COMPLETED_WITH_WAIVER;
                if (stamp.status() != expectedStatus
                        || !successor.id().toString().equals(stamp.runId())
                        || !projection.adapterId.equals(stamp.adapterId())
                        || !projection.requirementsDigest.equals(stamp.requirementsDigest())
                        || !projection.designDigest.equals(stamp.designDigest())
                        || !projection.artifactManifest.digest().equals(stamp.artifactManifestDigest())
                        || !projection.candidateSnapshot.digest().equals(stamp.candidateSnapshotDigest())
                        || !projection.catalogUsage.digest().equals(stamp.catalogUsageDigest())
                        || !report.checks().equals(stamp.checks())) {
                    throw new IOException("Ship Stamp differs from exact controller evidence");
                }
                if (expectedStatus == ShipStamp.Status.COMPLETED_WITH_WAIVER
                        && (projection.latestInteraction == null
                                || stamp.waivers().size() != 1
                                || !projection.latestInteraction.digest().equals(
                                        stamp.waivers().get(0).responseDigest()))) {
                    throw new IOException("Waiver Stamp differs from the signed waiver response");
                }
                ProjectSnapshot candidate = read(
                        projection.candidateSnapshot, ProjectSnapshot.class);
                ProjectSnapshot published = read(
                        value.publishedSnapshot(), ProjectSnapshot.class);
                if (!ProjectEvidenceFiles.unchangedMaterialTree(candidate, published)) {
                    throw new IOException("Published snapshot differs from the validated candidate");
                }
                projection.stamp = value.stamp();
                projection.candidateSnapshot = value.publishedSnapshot();
                projection.candidateDirectory = null;
                projection.validationReport = value.validationReport();
                projection.activeRequest = null;
                projection.activeRequestReference = null;
            }
        }
    }

    private void applyStageStarted(
            Projection projection,
            ShipEventPayloads.StageStarted value,
            ShipRun successor,
            ShipEventType event)
            throws IOException {
        verify(value.request(), "stage-request");
        StageRequest request = read(value.request(), StageRequest.class);
        requireRun(request.runId(), successor);
        Attempt attempt = ShipLifecycleReducer.attempt(successor);
        if (request.attempt() != attempt.number()
                || request.stage() != stage(attempt.phase())
                || value.stage() != request.stage()
                || !absolutePath(value.attemptOutputDirectory(), "attempt output directory")
                        .equals(absolutePath(request.outputDirectory(), "request output directory"))) {
            throw new IOException("Stage request does not match the exact lifecycle attempt for " + event.stableId());
        }
        attemptFactory.validateIssuedDuringReplay(
                projection.view(successor),
                blobs,
                request,
                value.stage(),
                Math.toIntExact(attempt.number()),
                value.attemptOutputDirectory());
        projection.activeRequest = request;
        projection.activeRequestReference = value.request();
        projection.pendingInteraction = null;
    }

    private void applyStageAccepted(
            Projection projection,
            ShipEventPayloads.StageAccepted value,
            ShipRun successor,
            ShipAuthorityCommand authority,
            ShipEventType event)
            throws IOException {
        requireActiveRequest(projection, value.request());
        if (projection.activeRequest.stage() != value.stage()) {
            throw new IOException("Accepted stage differs from the controller-issued request");
        }
        verify(value.result(), "stage-result");
        StageResult result = read(value.result(), StageResult.class);
        StageResultValidator.validateEnvelope(
                projection.activeRequest,
                result,
                Path.of(projection.activeRequest.outputDirectory()));
        if (!result.artifacts().equals(value.artifacts().stream()
                .map(ShipWorkspaceService.AcceptedArtifact::claim)
                .toList())) {
            throw new IOException("Accepted artifacts differ from the exact worker result");
        }
        for (ShipWorkspaceService.AcceptedArtifact artifact : value.artifacts()) {
            blobs.verify(artifact.blob());
        }
        verifyOptional(value.ledger(), "decision-ledger");
        verifyOptional(value.artifactManifest(), "artifact-manifest");
        verifyOptional(value.catalogEvidence(), "catalog-evidence");
        verifyOptional(value.catalogUsage(), "catalog-usage");
        verifyOptional(value.candidateSnapshot(), "project-snapshot");
        for (BlobReference evidence : value.evidence()) {
            verify(evidence, "ship-check-evidence");
        }
        verifyOptional(value.validationReport(), "validation-report");
        if ((result.ledger() == null) != (value.ledger() == null)
                || result.ledger() != null && !result.ledger().equals(read(value.ledger(), DecisionLedger.class))) {
            throw new IOException("Accepted ledger differs from the exact worker result");
        }
        if ((result.artifactManifest() == null) != (value.artifactManifest() == null)
                || result.artifactManifest() != null
                        && !result.artifactManifest().equals(read(
                                value.artifactManifest(),
                                ArtifactManifest.class))) {
            throw new IOException("Accepted artifact manifest differs from the exact worker result");
        }
        if (value.stage() == ShipStage.DISCOVERY
                || value.stage() == ShipStage.REVIEW
                || event == ShipEventType.DESIGN_GAPS_FOUND) {
            validateLedgerProvenance(projection, projection.activeRequest, result);
        }

        switch (event) {
            case DISCOVERY_CONTINUED -> {
                requireStageOutcome(value, result, ShipStage.DISCOVERY, StageResult.Outcome.NEEDS_DISCOVERY);
                requireLedgerAndCatalogEvidence(value);
                validateCatalogContinuation(value, result);
                requireContent(authority, value.catalogEvidence().digest(), "discovery continuation");
                projection.ledger = value.ledger();
                projection.catalogEvidence = value.catalogEvidence();
            }
            case GAP_REVIEW_STARTED -> {
                requireStageOutcome(value, result, ShipStage.DISCOVERY, StageResult.Outcome.COMPLETED);
                requireLedgerAndCatalogEvidence(value);
                if (!value.catalogEvidence().equals(projection.catalogEvidence)) {
                    throw new IOException("Discovery candidate changed its issued catalog evidence");
                }
                requireContent(authority, value.ledger().digest(), "discovery candidate");
                projection.ledger = value.ledger();
                projection.catalogEvidence = value.catalogEvidence();
            }
            case GAP_REVIEW_REOPENED -> {
                requireStageOutcome(value, result, ShipStage.REVIEW, StageResult.Outcome.NEEDS_DISCOVERY);
                requireLedgerWithoutCatalogEvidence(value);
                if (projection.ledger == null
                        || authority.content() == null
                        || !authority.content().value().equals(projection.ledger.digest())
                        || authority.feedback() == null
                        || !authority.feedback().value().equals(value.result().digest())) {
                    throw new IOException("Gap-review feedback does not bind its exact candidate and result");
                }
                projection.ledger = value.ledger();
                projection.catalogEvidence = null;
            }
            case DESIGN_GAPS_FOUND -> {
                requireStageOutcome(value, result, ShipStage.DESIGN, StageResult.Outcome.NEEDS_DISCOVERY);
                requireLedgerWithoutCatalogEvidence(value);
                if (authority.content() == null
                        || !authority.content().value().equals(projection.activeRequest.inputDigest())
                        || authority.feedback() == null
                        || !authority.feedback().value().equals(value.result().digest())) {
                    throw new IOException("Design-gap feedback does not bind its exact input and result");
                }
                projection.ledger = value.ledger();
                projection.catalogEvidence = null;
                projection.requirementsDigest = null;
                projection.design = null;
                projection.designDigest = null;
                projection.plan = null;
            }
            case REQUIREMENTS_READY -> {
                requireStageOutcome(value, result, ShipStage.REVIEW, StageResult.Outcome.COMPLETED);
                requireLedgerAndCatalogEvidence(value);
                if (!value.catalogEvidence().equals(projection.catalogEvidence)) {
                    throw new IOException("Gap review changed its issued catalog evidence");
                }
                AuthorityBasis basis = authority.basis();
                if (basis == null || value.ledger() == null || value.requirementsDigest() == null) {
                    throw new IOException("Accepted requirements are missing their exact authority basis");
                }
                DecisionLedger ledger = read(value.ledger(), DecisionLedger.class);
                if (projection.context == null
                        || !basis.context().value().equals(projection.context.digest())
                        || !basis.ledger().value().equals(value.ledger().digest())
                        || basis.ledgerRevision() != ledger.revision()
                        || !value.ledger().digest().equals(value.requirementsDigest())
                        || !basis.requirements().value().equals(value.requirementsDigest())
                        || !basis.policy().value().equals(projection.activeRequest.policyDigest())
                        || !basis.baseline().value().equals(projection.baselineSnapshot.digest())) {
                    throw new IOException("Requirements authority basis does not match accepted controller evidence");
                }
                projection.ledger = value.ledger();
                projection.catalogEvidence = value.catalogEvidence();
                projection.requirementsDigest = value.requirementsDigest();
                projection.artifactManifest = value.artifactManifest();
                projection.catalogUsage = value.catalogUsage();
            }
            case DESIGN_READY -> {
                requireStageOutcome(value, result, ShipStage.DESIGN, StageResult.Outcome.COMPLETED);
                requireNoValidationEvidence(value);
                projection.design = artifact(value, "design");
                requireContent(authority, projection.design.digest(), "accepted design");
                requireDigest(value.designDigest(), projection.design, "accepted design");
                projection.designDigest = value.designDigest();
            }
            case PLAN_VALIDATED -> {
                requireStageOutcome(value, result, ShipStage.PLAN, StageResult.Outcome.COMPLETED);
                requireNoValidationEvidence(value);
                projection.plan = artifact(value, "plan");
                requireContent(authority, projection.plan.digest(), "accepted plan");
            }
            case EXECUTION_VALIDATED -> {
                requireStageOutcome(value, result, ShipStage.EXECUTE, StageResult.Outcome.COMPLETED);
                requireNoValidationEvidence(value);
                if (value.artifactManifest() == null
                        || value.catalogUsage() == null
                        || value.candidateSnapshot() == null
                        || value.candidateDirectory() == null) {
                    throw new IOException("Accepted execution lacks its sealed controller bindings");
                }
                Path candidateDirectory = absolutePath(
                        value.candidateDirectory(), "candidate directory");
                blobs.verifyCandidateDirectory(candidateDirectory);
                ProjectSnapshot expectedCandidate = read(
                        value.candidateSnapshot(), ProjectSnapshot.class);
                ProjectSnapshot observedCandidate = ProjectEvidenceFiles.captureSealed(
                        candidateDirectory);
                if (!expectedCandidate.equals(observedCandidate)) {
                    throw new IOException("Accepted execution candidate changed before projection");
                }
                requireContent(authority, value.result().digest(), "accepted execution");
                projection.executionResult = value.result();
                projection.candidateSnapshot = value.candidateSnapshot();
                projection.candidateDirectory = candidateDirectory;
                projection.artifactManifest = value.artifactManifest();
                projection.catalogUsage = value.catalogUsage();
            }
            case VALIDATION_PASSED -> {
                requireStageOutcome(value, result, ShipStage.VALIDATE, StageResult.Outcome.COMPLETED);
                BlobReference workerValidation = artifact(value, "validation");
                if (value.validationReport() == null
                        || value.evidence().isEmpty()
                        || projection.artifactManifest == null
                        || projection.catalogUsage == null
                        || projection.candidateSnapshot == null
                        || projection.candidateDirectory == null) {
                    throw new IOException("Accepted validation lacks controller evidence bindings");
                }
                ShipValidationReport report = read(
                        value.validationReport(), ShipValidationReport.class);
                ArtifactManifest manifest = read(
                        projection.artifactManifest,
                        ArtifactManifest.class);
                if (projection.ledger == null) {
                    throw new IOException(
                            "Accepted validation has no approved requirements ledger");
                }
                DecisionLedger ledger = read(
                        projection.ledger, DecisionLedger.class);
                DecisionLedger.RequirementsPolicy policy = ledger.requirementsPolicy();
                if (policy == null) {
                    throw new IOException(
                            "Accepted validation has no resolved requirements policy");
                }
                ProjectSnapshot candidate = read(
                        projection.candidateSnapshot, ProjectSnapshot.class);
                CatalogUsageRecord usage = read(
                        projection.catalogUsage, CatalogUsageRecord.class);
                ShipValidationService.VerificationInputs verificationInputs
                        = new ShipValidationService.VerificationInputs(
                                projection.authority.id().toString(),
                                new ShipValidationService.CandidateInput(
                                        projection.candidateDirectory,
                                        candidate,
                                        projection.candidateSnapshot),
                                new ShipValidationService.ManifestInput(
                                        manifest, projection.artifactManifest),
                                policy,
                                new ShipValidationService.UsageInput(
                                        usage, projection.catalogUsage),
                                workerValidation);
                Map<String, BlobReference> evidenceById = ShipValidationService.verifyReport(
                        blobs,
                        verificationInputs,
                        report,
                        value.evidence());
                requireContent(authority, value.validationReport().digest(), "accepted validation");
                projection.evidence.clear();
                projection.evidence.addAll(value.evidence());
                projection.evidenceById.clear();
                projection.evidenceById.putAll(evidenceById);
                projection.validationReport = value.validationReport();
            }
            default -> throw new IOException("Event is not a stage acceptance: " + event.stableId());
        }
        projection.activeRequest = null;
        projection.activeRequestReference = null;
        clearFailure(projection);
        boolean continuationRequired = event == ShipEventType.DISCOVERY_CONTINUED
                || event == ShipEventType.GAP_REVIEW_STARTED
                || event == ShipEventType.GAP_REVIEW_REOPENED
                || event == ShipEventType.DESIGN_GAPS_FOUND;
        applyContinuation(
                projection,
                value.continuation(),
                successor,
                event,
                continuationRequired);
    }

    private void validateLedgerProvenance(
            Projection projection, StageRequest request, StageResult result)
            throws IOException {
        ShipLedgerSources.Snapshot sourceSnapshot = ShipLedgerSources.load(
                blobs, projection.view(), interactions);
        DecisionLedger previous = projection.ledger == null
                ? null : read(projection.ledger, DecisionLedger.class);
        LedgerProvenanceValidator.EvolutionAuthorization authorization = null;
        if (request.stage() == ShipStage.DISCOVERY && previous != null) {
            if (sourceSnapshot.latestDiscoveryAnswer() != null
                    && sourceSnapshot.latestDiscoveryAnswer().answer().ledgerRevision()
                       == previous.revision()) {
                authorization = sourceSnapshot.latestDiscoveryAnswer();
            } else if (sourceSnapshot.latestRequirementsChange() != null
                    && sourceSnapshot.latestRequirementsChange().ledgerRevision()
                       == previous.revision()) {
                authorization = sourceSnapshot.latestRequirementsChange();
            }
        }
        LedgerProvenanceValidator.validate(
                request,
                result,
                sourceSnapshot.sources(),
                previous,
                authorization);
    }

    private void requireLedgerAndCatalogEvidence(ShipEventPayloads.StageAccepted value)
            throws IOException {
        if (value.ledger() == null || value.catalogEvidence() == null) {
            throw new IOException("Accepted analysis lacks its exact ledger or catalog evidence");
        }
        read(value.ledger(), DecisionLedger.class);
        read(value.catalogEvidence(), CatalogEvidenceSet.class);
    }

    private void validateCatalogContinuation(
            ShipEventPayloads.StageAccepted value, StageResult result)
            throws IOException {
        DecisionLedger ledger = read(value.ledger(), DecisionLedger.class);
        DecisionLedger.RequirementsPolicy policy = ledger.requirementsPolicy();
        if (policy == null) {
            throw new IOException("Catalog continuation has no resolved requirements policy");
        }
        CatalogTarget target;
        try {
            target = new CatalogTarget(
                    policy.runtime(),
                    policy.camelVersion(),
                    policy.platformVersion(),
                    policy.springBootVersion());
        } catch (IllegalArgumentException e) {
            throw new IOException("Catalog continuation target is invalid", e);
        }
        CatalogEvidenceValidator.validateEvidence(
                read(value.catalogEvidence(), CatalogEvidenceSet.class),
                target,
                result.catalogRequests());
    }

    private void requireLedgerWithoutCatalogEvidence(ShipEventPayloads.StageAccepted value)
            throws IOException {
        if (value.ledger() == null || value.catalogEvidence() != null) {
            throw new IOException("Reopened discovery must invalidate prior catalog evidence");
        }
        read(value.ledger(), DecisionLedger.class);
    }

    private static void requireStageOutcome(
            ShipEventPayloads.StageAccepted value,
            StageResult result,
            ShipStage stage,
            StageResult.Outcome outcome)
            throws IOException {
        if (value.stage() != stage || result.stage() != stage || result.outcome() != outcome) {
            throw new IOException("Accepted result outcome does not match its lifecycle event");
        }
    }

    private void applyContinuation(
            Projection projection,
            ShipEventPayloads.StageStarted continuation,
            ShipRun successor,
            ShipEventType event,
            boolean required)
            throws IOException {
        if (required != (continuation != null)) {
            throw new IOException(
                    required
                            ? "Lifecycle transition lacks its exact continuation request"
                            : "Lifecycle transition unexpectedly carries a continuation request");
        }
        if (continuation != null) {
            applyStageStarted(projection, continuation, successor, event);
        }
    }

    private void applyDesignDecisionProjection(
            Projection projection,
            ShipEventPayloads.DesignApprovalRecorded value,
            ShipRun successor,
            ShipAuthorityCommand authority,
            ShipEventType event)
            throws IOException {
        boolean continuation = event == ShipEventType.DESIGN_APPROVAL_DENIED
                || event == ShipEventType.DESIGN_REQUIREMENTS_CHANGES_REQUESTED;
        requireApprovalFeedback(
                value.response().requestedChanges(),
                authority,
                event,
                continuation);
        if (continuation) {
            projection.design = null;
            projection.designDigest = null;
            projection.plan = null;
        }
        if (event == ShipEventType.DESIGN_REQUIREMENTS_CHANGES_REQUESTED) {
            projection.requirementsDigest = null;
            projection.catalogEvidence = null;
        }
        projection.activeRequest = null;
        projection.activeRequestReference = null;
        applyContinuation(projection, value.continuation(), successor, event, continuation);
    }

    private void applyPlanDecisionProjection(
            Projection projection,
            ShipEventPayloads.PlanApprovalRecorded value,
            ShipRun successor,
            ShipAuthorityCommand authority,
            ShipEventType event)
            throws IOException {
        boolean continuation = event == ShipEventType.PLAN_APPROVAL_DENIED
                || event == ShipEventType.PLAN_DESIGN_CHANGES_REQUESTED
                || event == ShipEventType.PLAN_REQUIREMENTS_CHANGES_REQUESTED;
        requireApprovalFeedback(
                value.response().requestedChanges(),
                authority,
                event,
                continuation);
        if (continuation) {
            projection.plan = null;
        }
        if (event == ShipEventType.PLAN_DESIGN_CHANGES_REQUESTED
                || event == ShipEventType.PLAN_REQUIREMENTS_CHANGES_REQUESTED) {
            projection.design = null;
            projection.designDigest = null;
        }
        if (event == ShipEventType.PLAN_REQUIREMENTS_CHANGES_REQUESTED) {
            projection.requirementsDigest = null;
            projection.catalogEvidence = null;
        }
        projection.activeRequest = null;
        projection.activeRequestReference = null;
        applyContinuation(projection, value.continuation(), successor, event, continuation);
    }

    private static void requireApprovalFeedback(
            String requestedChanges,
            ShipAuthorityCommand authority,
            ShipEventType event,
            boolean required)
            throws IOException {
        String digest = requestedChanges == null || requestedChanges.isBlank()
                ? null
                : ShipDigest.sha256(requestedChanges.getBytes(StandardCharsets.UTF_8));
        boolean matches = required
                ? digest != null
                        && authority.feedback() != null
                        && digest.equals(authority.feedback().value())
                : digest == null && authority.feedback() == null;
        if (!matches) {
            throw new IOException("Approval feedback shape disagrees with event " + event.stableId());
        }
    }

    private static ShipEventType designEvent(DesignDecision decision) {
        return switch (decision) {
            case APPROVE -> ShipEventType.DESIGN_APPROVED;
            case REQUEST_DESIGN_CHANGES -> ShipEventType.DESIGN_APPROVAL_DENIED;
            case REQUEST_REQUIREMENTS_CHANGES -> ShipEventType.DESIGN_REQUIREMENTS_CHANGES_REQUESTED;
            case ABORT -> ShipEventType.DESIGN_APPROVAL_ABORTED;
        };
    }

    private static ShipEventType planEvent(PlanDecision decision) {
        return switch (decision) {
            case APPROVE -> ShipEventType.PLAN_APPROVED;
            case REQUEST_PLAN_CHANGES -> ShipEventType.PLAN_APPROVAL_DENIED;
            case REQUEST_DESIGN_CHANGES -> ShipEventType.PLAN_DESIGN_CHANGES_REQUESTED;
            case REQUEST_REQUIREMENTS_CHANGES -> ShipEventType.PLAN_REQUIREMENTS_CHANGES_REQUESTED;
            case ABORT -> ShipEventType.PLAN_APPROVAL_ABORTED;
        };
    }

    private void applyFailure(Projection projection, ShipEventPayloads.Failure value)
            throws IOException {
        verifyOptional(value.request(), "stage-request");
        verifyOptional(value.result(), "stage-result");
        verifyOptional(value.validationReport(), "validation-report");
        verifyOptional(value.stamp(), "ship-stamp");
        verifyOptional(value.interactionBundle(), "interaction-bundle");
        for (BlobReference evidence : value.evidence()) {
            verify(evidence, "ship-check-evidence");
        }
        if (value.validationReport() != null) {
            if (projection.artifactManifest == null
                    || projection.catalogUsage == null
                    || projection.candidateSnapshot == null
                    || projection.candidateDirectory == null) {
                throw new IOException("Validation failure lacks its durable execution bindings");
            }
            ShipValidationReport report = read(
                    value.validationReport(), ShipValidationReport.class);
            ArtifactManifest manifest = read(
                    projection.artifactManifest,
                    ArtifactManifest.class);
            if (projection.ledger == null) {
                throw new IOException(
                        "Validation failure has no approved requirements ledger");
            }
            DecisionLedger ledger = read(
                    projection.ledger, DecisionLedger.class);
            DecisionLedger.RequirementsPolicy policy = ledger.requirementsPolicy();
            if (policy == null) {
                throw new IOException(
                        "Validation failure has no resolved requirements policy");
            }
            ProjectSnapshot candidate = read(
                    projection.candidateSnapshot, ProjectSnapshot.class);
            CatalogUsageRecord usage = read(
                    projection.catalogUsage, CatalogUsageRecord.class);
            ShipValidationService.VerificationInputs verificationInputs
                    = new ShipValidationService.VerificationInputs(
                            projection.authority.id().toString(),
                            new ShipValidationService.CandidateInput(
                                    projection.candidateDirectory,
                                    candidate,
                                    projection.candidateSnapshot),
                            new ShipValidationService.ManifestInput(
                                    manifest, projection.artifactManifest),
                            policy,
                            new ShipValidationService.UsageInput(
                                    usage, projection.catalogUsage),
                            report.workerValidation());
            Map<String, BlobReference> evidenceById = ShipValidationService.verifyReport(
                    blobs,
                    verificationInputs,
                    report,
                    value.evidence());
            projection.evidence.clear();
            projection.evidence.addAll(value.evidence());
            projection.evidenceById.clear();
            projection.evidenceById.putAll(evidenceById);
        } else if (!value.evidence().isEmpty()) {
            throw new IOException("Failure evidence has no controller validation report");
        }
        projection.failedStage = value.failedStage();
        projection.failureCode = requiredText(value.code(), "failure code");
        projection.failureMessage = requiredText(value.message(), "failure message");
        projection.failedRequest = value.request();
        projection.validationReport = value.validationReport();
        projection.stamp = value.stamp();
        projection.interactionBundle = value.interactionBundle();
        projection.activeRequest = null;
        projection.activeRequestReference = null;
        projection.pendingInteraction = null;
    }

    private static void requireNoValidationEvidence(ShipEventPayloads.StageAccepted value)
            throws IOException {
        if (!value.evidence().isEmpty() || value.validationReport() != null) {
            throw new IOException("Pre-validation stage cannot carry controller validation evidence");
        }
    }

    private void applyAttemptFailure(
            Projection projection, ShipEventPayloads.Failure value)
            throws IOException {
        requireActiveRequest(projection, value.request());
        verify(value.result(), "stage-result");
        StageResult result = read(value.result(), StageResult.class);
        StageResultValidator.validateEnvelope(
                projection.activeRequest,
                result,
                Path.of(projection.activeRequest.outputDirectory()));
        if (result.outcome() != StageResult.Outcome.FAILED
                || result.stage() != value.failedStage()
                || !Objects.equals(result.failureCode(), value.code())
                || !Objects.equals(result.failureMessage(), value.message())
                || value.validationReport() != null
                || value.stamp() != null
                || value.interactionBundle() != null) {
            throw new IOException("Retryable failure does not bind the exact failed worker result");
        }
        BlobReference interactionBundle = projection.interactionBundle;
        applyFailure(projection, value);
        projection.interactionBundle = interactionBundle;
    }

    private void applyWaivableFailure(
            Projection projection, ShipEventPayloads.Failure value)
            throws IOException {
        BlobReference interactionBundle = projection.interactionBundle;
        if (value.interactionBundle() != null
                && !value.interactionBundle().equals(interactionBundle)) {
            throw new IOException("Waivable failure changed the signed interaction bundle");
        }
        applyFailure(projection, value);
        projection.interactionBundle = interactionBundle;
    }

    private static void clearFailure(Projection projection) {
        projection.failureCode = null;
        projection.failureMessage = null;
        projection.failedStage = null;
        projection.failedRequest = null;
    }

    private static void invalidateRequirementsAndLater(Projection projection) {
        projection.ledger = null;
        projection.catalogEvidence = null;
        projection.requirementsDigest = null;
        invalidateDesignAndLater(projection);
    }

    private static void invalidateDesignAndLater(Projection projection) {
        projection.design = null;
        projection.designDigest = null;
        invalidatePlanAndLater(projection);
    }

    private static void invalidatePlanAndLater(Projection projection) {
        projection.plan = null;
        projection.artifactManifest = null;
        projection.catalogUsage = null;
        projection.executionResult = null;
        projection.candidateSnapshot = null;
        projection.candidateDirectory = null;
        projection.evidence.clear();
        projection.evidenceById.clear();
        projection.validationReport = null;
        projection.stamp = null;
        clearFailure(projection);
    }

    private ShipInteractionBundle.Exchange applyPending(
            Projection projection, BlobReference bundleReference)
            throws IOException {
        verify(bundleReference, "interaction-bundle");
        ShipInteractionBundle previous = currentInteractionBundle(projection);
        ShipInteractionBundle bundle = interactions.read(blobs, bundleReference);
        if (!sameBundleIdentity(previous, bundle)
                || bundle.exchanges().size() != previous.exchanges().size() + 1
                || !bundle.exchanges().subList(0, previous.exchanges().size())
                        .equals(previous.exchanges())) {
            throw new IOException("Interaction challenge does not exactly extend the current bundle");
        }
        ShipInteractionBundle.Exchange exchange = bundle.exchanges().get(bundle.exchanges().size() - 1);
        if (!exchange.pending()) {
            throw new IOException("Interaction bundle's latest exchange is not pending");
        }
        projection.interactionBundle = bundleReference;
        projection.pendingInteraction = exchange;
        return exchange;
    }

    private ShipInteractionBundle.Exchange applyResponse(
            Projection projection, BlobReference response, BlobReference bundleReference)
            throws IOException {
        verify(response, "interaction-response");
        verify(bundleReference, "interaction-bundle");
        ShipInteractionBundle previous = currentInteractionBundle(projection);
        ShipInteractionBundle bundle = interactions.read(blobs, bundleReference);
        if (!sameBundleIdentity(previous, bundle)
                || previous.exchanges().isEmpty()
                || bundle.exchanges().size() != previous.exchanges().size()
                || !bundle.exchanges().subList(0, bundle.exchanges().size() - 1)
                        .equals(previous.exchanges().subList(0, previous.exchanges().size() - 1))) {
            throw new IOException("Interaction response does not complete the current bundle");
        }
        ShipInteractionBundle.Exchange pending = previous.exchanges().get(previous.exchanges().size() - 1);
        ShipInteractionBundle.Exchange exchange = bundle.exchanges().get(bundle.exchanges().size() - 1);
        if (!pending.pending()
                || projection.pendingInteraction == null
                || !projection.pendingInteraction.equals(pending)
                || exchange.pending()
                || !sameChallenge(pending, exchange)) {
            throw new IOException("Interaction bundle does not contain the recorded response");
        }
        projection.latestInteraction = response;
        projection.interactionBundle = bundleReference;
        projection.pendingInteraction = null;
        return exchange;
    }

    private ShipInteractionBundle currentInteractionBundle(Projection projection)
            throws IOException {
        if (projection.interactionBundle == null) {
            throw new IOException("Ship projection has no current interaction bundle");
        }
        verify(projection.interactionBundle, "interaction-bundle");
        return interactions.read(blobs, projection.interactionBundle);
    }

    private static boolean sameBundleIdentity(
            ShipInteractionBundle previous, ShipInteractionBundle candidate) {
        return previous.schemaVersion() == candidate.schemaVersion()
                && previous.runId().equals(candidate.runId())
                && previous.contextRevision() == candidate.contextRevision()
                && previous.contextDigest().equals(candidate.contextDigest());
    }

    private static boolean sameChallenge(
            ShipInteractionBundle.Exchange previous,
            ShipInteractionBundle.Exchange completed) {
        return previous.ordinal() == completed.ordinal()
                && (previous.discovery() != null
                        && completed.discovery() != null
                        && previous.discovery().challenge().equals(completed.discovery().challenge())
                        || previous.documentConsent() != null
                                && completed.documentConsent() != null
                                && previous.documentConsent().challenge()
                                        .equals(completed.documentConsent().challenge())
                        || previous.remoteProviderConsent() != null
                                && completed.remoteProviderConsent() != null
                                && previous.remoteProviderConsent().challenge()
                                        .equals(completed.remoteProviderConsent().challenge())
                        || previous.design() != null
                                && completed.design() != null
                                && previous.design().challenge().equals(completed.design().challenge())
                        || previous.plan() != null
                                && completed.plan() != null
                                && previous.plan().challenge().equals(completed.plan().challenge())
                        || previous.waiver() != null
                                && completed.waiver() != null
                                && previous.waiver().challenge().equals(completed.waiver().challenge()));
    }

    private void requireActiveRequest(Projection projection, BlobReference request) throws IOException {
        verify(request, "stage-request");
        if (projection.activeRequestReference == null
                || !projection.activeRequestReference.equals(request)
                || projection.activeRequest == null) {
            throw new IOException("Result does not bind the currently active stage request");
        }
    }

    private BlobReference artifact(ShipEventPayloads.StageAccepted accepted, String kind)
            throws IOException {
        List<BlobReference> matching = accepted.artifacts().stream()
                .map(ShipWorkspaceService.AcceptedArtifact::blob)
                .filter(reference -> kind.equals(reference.kind()))
                .toList();
        if (matching.size() != 1) {
            throw new IOException("Accepted stage requires exactly one " + kind + " artifact");
        }
        blobs.verify(matching.get(0));
        return matching.get(0);
    }

    private <T> T read(BlobReference reference, Class<T> type) throws IOException {
        return ShipJson.mapper().readValue(
                blobs.readBytes(reference, ShipJson.MAX_DOCUMENT_BYTES), type);
    }

    private <T> T read(BlobReference reference, String kind, Class<T> type) throws IOException {
        verify(reference, kind);
        return read(reference, type);
    }

    private void requireBlobValue(BlobReference reference, Object expected) throws IOException {
        Object actual = read(reference, expected.getClass());
        if (!expected.equals(actual)) {
            throw new IOException("Protected interaction response differs from the event payload");
        }
    }

    private void verify(BlobReference reference, String kind) throws IOException {
        if (reference == null || kind != null && !kind.equals(reference.kind())) {
            throw new IOException("Expected protected " + kind + " blob reference");
        }
        blobs.verify(reference);
    }

    private void verifyOptional(BlobReference reference, String kind) throws IOException {
        if (reference != null) {
            verify(reference, kind);
        }
    }

    private static void requireContent(
            ShipAuthorityCommand authority, String expected, String label)
            throws IOException {
        if (authority.content() == null || !authority.content().value().equals(expected)) {
            throw new IOException("Authority command does not bind the exact " + label);
        }
    }

    private static void requireDigest(String digest, BlobReference reference, String label)
            throws IOException {
        if (reference == null || !reference.digest().equals(digest)) {
            throw new IOException(label + " digest does not match its protected blob");
        }
    }

    private static void requireRun(String runId, ShipRun successor) throws IOException {
        if (!successor.id().toString().equals(runId)) {
            throw new IOException("Controller record belongs to another Ship run");
        }
    }

    private void requireApprovalBasis(
            Projection projection,
            AuthorityBasis basis,
            String contextDigest,
            long ledgerRevision,
            String ledgerDigest,
            String requirementsDigest,
            String policyDigest,
            String baselineDigest,
            String label)
            throws IOException {
        if (basis == null
                || projection.context == null
                || projection.ledger == null
                || projection.requirementsDigest == null
                || projection.baselineSnapshot == null
                || !basis.context().value().equals(projection.context.digest())
                || !read(projection.context, InitialContext.class).digest().equals(contextDigest)
                || basis.ledgerRevision() != ledgerRevision
                || !basis.ledger().value().equals(ledgerDigest)
                || !basis.ledger().value().equals(projection.ledger.digest())
                || !basis.requirements().value().equals(requirementsDigest)
                || !basis.requirements().value().equals(projection.requirementsDigest)
                || !basis.policy().value().equals(policyDigest)
                || !basis.baseline().value().equals(baselineDigest)
                || !basis.baseline().value().equals(projection.baselineSnapshot.digest())) {
            throw new IOException("Ship " + label + " does not bind the current authority basis");
        }
    }

    private static Path absolutePath(String value, String label) throws IOException {
        try {
            Path path = Path.of(requiredText(value, label));
            if (!path.isAbsolute() || !path.normalize().equals(path) || !path.toString().equals(value)) {
                throw new IOException("Ship " + label + " must be canonical, absolute, and normalized");
            }
            return path;
        } catch (InvalidPathException e) {
            throw new IOException("Invalid Ship " + label, e);
        }
    }

    private static String requiredText(String value, String label) throws IOException {
        if (value == null || value.isBlank()) {
            throw new IOException("Ship " + label + " is required");
        }
        return value;
    }

    private static void requireAdapter(String adapterId) throws IOException {
        if (adapterId == null || !adapterId.matches("[a-z][a-z0-9-]{0,63}")) {
            throw new IOException("Ship adapter ID is invalid");
        }
    }

    private static ShipStage stage(ShipPhase phase) throws IOException {
        return switch (phase) {
            case DISCOVERY -> ShipStage.DISCOVERY;
            case REVIEW -> ShipStage.REVIEW;
            case DESIGN -> ShipStage.DESIGN;
            case PLAN -> ShipStage.PLAN;
            case EXECUTE -> ShipStage.EXECUTE;
            case VALIDATE -> ShipStage.VALIDATE;
            case CONTEXT, STAMP, WAIVER_STAMP ->
                throw new IOException("Lifecycle phase has no worker-stage request: " + phase);
        };
    }

    private static <T extends ShipEventPayloads.Payload> T cast(
            ShipEventPayloads.Payload value, Class<T> type)
            throws IOException {
        if (!type.isInstance(value)) {
            throw new IOException("Ship event has the wrong typed controller payload");
        }
        return type.cast(value);
    }

    record ReplayResult(ShipRunView view, List<ShipEvent> history) {

        ReplayResult {
            Objects.requireNonNull(view, "replayed view");
            history = List.copyOf(history);
        }
    }

    private static final class Projection {

        private ShipRun authority;
        private String eventDigest;
        private Path projectRoot;
        private String adapterId;
        private BlobReference nativeSessionEvidence;
        private BlobReference baselineSnapshot;
        private BlobReference sourceSnapshot;
        private BlobReference projectSourceManifest;
        private Path sourceDirectory;
        private BlobReference contextRequest;
        private BlobReference context;
        private BlobReference ledger;
        private BlobReference catalogEvidence;
        private String requirementsDigest;
        private BlobReference design;
        private String designDigest;
        private BlobReference plan;
        private BlobReference latestInteraction;
        private BlobReference interactionBundle;
        private StageRequest activeRequest;
        private BlobReference activeRequestReference;
        private ShipInteractionBundle.Exchange pendingInteraction;
        private BlobReference artifactManifest;
        private BlobReference catalogUsage;
        private BlobReference executionResult;
        private BlobReference candidateSnapshot;
        private Path candidateDirectory;
        private final List<BlobReference> evidence = new ArrayList<>();
        private final Map<String, BlobReference> evidenceById = new LinkedHashMap<>();
        private BlobReference validationReport;
        private BlobReference stamp;
        private BlobReference failedRequest;
        private ShipStage failedStage;
        private String failureCode;
        private String failureMessage;

        private ShipRunView view() {
            return view(authority);
        }

        private ShipRunView view(ShipRun projectedAuthority) {
            if (projectedAuthority == null) {
                throw new IllegalStateException("Ship projection has no lifecycle authority");
            }
            return new ShipRunView(
                    projectedAuthority,
                    eventDigest,
                    projectRoot,
                    adapterId,
                    nativeSessionEvidence,
                    baselineSnapshot,
                    sourceSnapshot,
                    projectSourceManifest,
                    sourceDirectory,
                    contextRequest,
                    context,
                    ledger,
                    catalogEvidence,
                    requirementsDigest,
                    design,
                    designDigest,
                    plan,
                    latestInteraction,
                    interactionBundle,
                    activeRequest,
                    activeRequestReference,
                    pendingInteraction,
                    artifactManifest,
                    catalogUsage,
                    executionResult,
                    candidateSnapshot,
                    candidateDirectory,
                    evidence,
                    evidenceById,
                    validationReport,
                    stamp,
                    failedRequest,
                    failedStage,
                    failureCode,
                    failureMessage);
        }
    }
}
