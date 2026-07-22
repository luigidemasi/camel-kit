package io.github.luigidemasi.camelkit.ship.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.ProviderMismatchException;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.github.luigidemasi.camelkit.ship.ShipDigest;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogEvidenceSet;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogTarget;
import io.github.luigidemasi.camelkit.ship.catalog.ShipCatalogService;
import io.github.luigidemasi.camelkit.ship.context.ContextFilesystemPolicy;
import io.github.luigidemasi.camelkit.ship.context.ContextResolution;
import io.github.luigidemasi.camelkit.ship.context.InitialContext;
import io.github.luigidemasi.camelkit.ship.context.InitialContextException;
import io.github.luigidemasi.camelkit.ship.context.InitialContextRequest;
import io.github.luigidemasi.camelkit.ship.context.InitialContextRequest.DocumentReference;
import io.github.luigidemasi.camelkit.ship.context.InitialContextRequest.UserText;
import io.github.luigidemasi.camelkit.ship.context.InitialContextResolver;
import io.github.luigidemasi.camelkit.ship.controller.ShipAttemptFactory.AttemptInputs;
import io.github.luigidemasi.camelkit.ship.controller.ShipBlobStore.BlobReference;
import io.github.luigidemasi.camelkit.ship.ledger.DecisionLedger;
import io.github.luigidemasi.camelkit.ship.ledger.LedgerValidationException;
import io.github.luigidemasi.camelkit.ship.ledger.LedgerValidator;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.DiscoveryAnswer;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.DiscoveryChallenge;
import io.github.luigidemasi.camelkit.ship.protocol.ShipStage;
import io.github.luigidemasi.camelkit.ship.protocol.StageRequest;
import io.github.luigidemasi.camelkit.ship.protocol.StageResult;
import io.github.luigidemasi.camelkit.ship.security.ProjectEvidenceFiles;
import io.github.luigidemasi.camelkit.ship.security.ProjectSnapshot;

/** Internal durable orchestration shell; no public command reaches this slice. */
final class ShipController {

    private static final String UNBOUND_INTERACTION_MAC = "hmac-sha256:" + "0".repeat(64);

    private final Path stateRoot;
    private final Clock clock;

    ShipController(Path stateRoot) {
        this(stateRoot, Clock.systemUTC());
    }

    ShipController(Path stateRoot, Clock clock) {
        this.stateRoot = Objects.requireNonNull(stateRoot, "stateRoot").toAbsolutePath().normalize();
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    ShipRunView start(PreparedRun prepared) throws IOException {
        Objects.requireNonNull(prepared, "prepared");
        requireDisjointRoots(stateRoot, prepared.projectRoot());
        ShipRun authority = ShipLifecycleReducer.start();
        boolean storageCreated = false;
        boolean committed = false;
        try {
            FileShipEventStore events = FileShipEventStore.create(stateRoot, authority.id());
            storageCreated = true;
            ShipBlobStore blobs = ShipBlobStore.create(stateRoot, authority.id().toString());
            ShipInteractionSigner signer = ShipInteractionSigner.create(
                    ShipControllerPaths.requireRunRoot(stateRoot, authority.id()));
            try (ShipOperationLock.Lease run
                    = ShipOperationLock.acquireRun(stateRoot, authority.id().toString());
                 ShipOperationLock.Lease project
                         = ShipOperationLock.acquireProject(stateRoot, prepared.projectRoot())) {
                Path sourceDirectory = ShipControllerPaths.requireRunRoot(
                        stateRoot, authority.id()).resolve("source");
                Files.createDirectory(
                        sourceDirectory,
                        PosixFilePermissions.asFileAttribute(
                                PosixFilePermissions.fromString("rwx------")));
                ProjectSnapshot baselineValue = ProjectEvidenceFiles.capture(prepared.projectRoot());
                ProjectSnapshot sourceValue = ProjectEvidenceFiles.materializeMaterial(
                        prepared.projectRoot(), sourceDirectory);
                if (!ProjectEvidenceFiles.unchangedMaterialTree(baselineValue, sourceValue)) {
                    throw new IOException("Immutable Ship source differs from the captured project baseline");
                }
                ProjectSourceManifest manifestValue = ProjectSourceManifest.from(sourceValue);
                BlobReference baseline = blobs.writeBytes(
                        "project-snapshot", ShipJson.mapper().writeValueAsBytes(baselineValue));
                BlobReference source = blobs.writeBytes(
                        "project-snapshot", ShipJson.mapper().writeValueAsBytes(sourceValue));
                BlobReference manifest = blobs.writeBytes(
                        "project-source-manifest", ShipJson.mapper().writeValueAsBytes(manifestValue));
                ShipAuthorityCommand command = ShipAuthorityCommand.empty(ShipEventType.RUN_CREATED);
                ShipEventPayloads.RunCreated data = new ShipEventPayloads.RunCreated(
                        prepared.projectRoot().toString(),
                        prepared.adapterId(),
                        null,
                        baseline,
                        source,
                        manifest,
                        sourceDirectory.toString());
                projector(events, blobs, signer).preflightCreated(authority, data);
                events.appendIfLatest(
                        ShipEventHead.genesis(),
                        new ShipEventDraft(
                                ShipEventType.RUN_CREATED,
                                ShipState.CREATED,
                                null,
                                authority.head(),
                                now(),
                                ShipStoredEventCodec.encode(command, data)));
                committed = true;
            }
            return projector(events, blobs, signer).replay();
        } catch (IOException | RuntimeException e) {
            if (!committed && storageCreated) {
                try {
                    ShipControllerPaths.deleteFreshRunRoot(stateRoot, authority.id());
                } catch (IOException cleanupFailure) {
                    e.addSuppressed(cleanupFailure);
                }
            }
            throw e;
        }
    }

    ShipRunView status(String runId) throws IOException {
        OpenRun run = open(runId);
        return projector(run.events(), run.blobs(), run.signer()).replay();
    }

    ShipRunView beginContextResolution(
            String runId, String expectedEventDigest, InitialContextRequest request)
            throws IOException {
        Objects.requireNonNull(request, "initial-context request");
        ShipInitialContextRequestCodec.validate(request);
        return mutate(runId, expectedEventDigest, current -> {
            requireState(current.view(), ShipState.CREATED);
            InitialContextRequest normalized = normalizeRequest(
                    request, current.view().projectRoot());
            byte[] input = ShipInitialContextRequestCodec.encode(normalized);
            preflightImmutableRequestSources(current.blobs(), current.view(), normalized);
            BlobReference reference = current.blobs().writeBytes("initial-context-request", input);
            return new Mutation(
                    ShipAuthorityCommand.value(
                            ShipEventType.CONTEXT_RESOLUTION_STARTED, reference.digest()),
                    new ShipEventPayloads.ContextResolutionStarted(reference));
        });
    }

    ShipRunView continueContextResolution(
            String runId,
            String expectedEventDigest,
            List<Path> credentialRoots)
            throws IOException, InitialContextException {
        OpenRun run = open(runId);
        ShipRunView current = projector(run.events(), run.blobs(), run.signer()).replay();
        if (!Objects.equals(expectedEventDigest, current.eventDigest())) {
            throw new ShipControllerException(
                    "stale-run-head", "Ship run changed before context resolution could begin");
        }
        requireState(current, ShipState.CONTEXT_RESOLVING);
        if (current.contextRequest() == null) {
            throw new IOException("Context resolution has no protected typed request");
        }
        InitialContextRequest request = ShipInitialContextRequestCodec.decode(
                run.blobs().readBytes(current.contextRequest(), ShipJson.MAX_DOCUMENT_BYTES));
        InitialContextResolver resolver = new InitialContextResolver(
                new ContextFilesystemPolicy(
                        stateRoot, List.copyOf(
                                Objects.requireNonNull(credentialRoots, "credential roots"))));
        ContextResolution resolution = resolver.resolve(current.projectRoot(), request);
        if (resolution instanceof ContextResolution.Pending pending) {
            throw new ShipControllerException(
                    "document-consent-boundary-unavailable",
                    "External context requires " + pending.consents().size()
                                                             + " protected document-consent decision(s)");
        }
        InitialContext context = ((ContextResolution.Resolved) resolution).context();
        requireImmutableContextSources(run.blobs(), current, context);
        return recordContext(runId, expectedEventDigest, context);
    }

    ShipRunView startDiscovery(String runId, String expectedEventDigest) throws IOException {
        return mutate(runId, expectedEventDigest, current -> {
            requireState(current.view(), ShipState.CONTEXT_RECORDED);
            return startStage(
                    current,
                    ShipAuthorityCommand.empty(ShipEventType.DISCOVERY_STARTED),
                    ShipStage.DISCOVERY,
                    AttemptInputs.from(current.view()));
        });
    }

    ShipRunView startDesign(String runId, String expectedEventDigest) throws IOException {
        return mutate(runId, expectedEventDigest, current -> {
            requireState(current.view(), ShipState.REQUIREMENTS_READY);
            return startStage(
                    current,
                    ShipAuthorityCommand.empty(ShipEventType.DESIGN_STARTED),
                    ShipStage.DESIGN,
                    AttemptInputs.from(current.view()));
        });
    }

    ShipRunView retryStage(String runId, String expectedEventDigest) throws IOException {
        return mutate(runId, expectedEventDigest, current -> {
            ShipAuthorityCommand command = ShipAuthorityCommand.empty(ShipEventType.RETRY_STARTED);
            ShipRun successor;
            try {
                successor = command.apply(current.view().authority());
            } catch (IllegalStateException e) {
                throw new ShipControllerException("lifecycle-boundary", e.getMessage(), e);
            }
            Attempt attempt = ShipLifecycleReducer.attempt(successor);
            return startStage(
                    current,
                    command,
                    stage(attempt.phase()),
                    AttemptInputs.from(current.view()));
        });
    }

    ShipRunView submitStageResult(
            String runId, String expectedEventDigest, byte[] encodedResult)
            throws IOException {
        return submitStageResult(runId, expectedEventDigest, encodedResult, null);
    }

    ShipRunView submitStageResult(
            String runId,
            String expectedEventDigest,
            byte[] encodedResult,
            ShipCatalogService.Snapshot catalogSnapshot)
            throws IOException {
        byte[] supplied = ShipStageResultReader.boundedCopy(encodedResult);
        return mutate(runId, expectedEventDigest, current -> acceptResult(
                current, supplied, catalogSnapshot));
    }

    ShipRunView answerDiscovery(
            String runId,
            String expectedEventDigest,
            DiscoveryChallenge issuedChallenge,
            String response,
            String channel)
            throws IOException {
        Objects.requireNonNull(issuedChallenge, "issued discovery challenge");
        if (response == null
                || response.length() > Interaction.MAX_RESPONSE_CHARS
                || channel == null
                || channel.length() > Interaction.MAX_CHANNEL_CHARS) {
            throw new IllegalArgumentException("Discovery answer exceeds its bounded interaction envelope");
        }
        return mutate(runId, expectedEventDigest, current -> {
            requireState(current.view(), ShipState.WAITING_FOR_DISCOVERY_ANSWER);
            ShipInteractionBundle.Exchange pending = current.view().pendingInteraction();
            if (pending == null
                    || pending.discovery() == null
                    || !pending.discovery().challenge().equals(issuedChallenge)) {
                throw new ShipControllerException(
                        "discovery-challenge-mismatch",
                        "Discovery answer does not match the exact pending challenge");
            }
            Instant answeredAt = now();
            DiscoveryAnswer answer = signedDiscoveryAnswer(
                    current, issuedChallenge, response, channel, answeredAt);
            current.interactions().preflightRecord(
                    current.blobs(), current.view().interactionBundle(), answer);
            BlobReference answerReference = write(current.blobs(), "interaction-response", answer);
            BlobReference bundle = current.interactions().record(
                    current.blobs(), current.view().interactionBundle(), answer);
            ShipAuthorityCommand command = ShipAuthorityCommand.value(
                    ShipEventType.DISCOVERY_ANSWER_RECORDED, answerReference.digest());
            AttemptInputs inputs = inputs(
                    current.view(),
                    current.view().ledger(),
                    current.view().catalogEvidence(),
                    bundle);
            ShipEventPayloads.StageStarted continuation = issueStage(
                    current, command, ShipStage.DISCOVERY, inputs);
            return new Mutation(
                    command,
                    new ShipEventPayloads.DiscoveryAnswerRecorded(
                            answer,
                            answerReference,
                            continuation.request(),
                            bundle));
        });
    }

    private ShipRunView recordContext(
            String runId, String expectedEventDigest, InitialContext context)
            throws IOException {
        Objects.requireNonNull(context, "initial context");
        return mutate(runId, expectedEventDigest, current -> {
            requireState(current.view(), ShipState.CONTEXT_RESOLVING);
            byte[] encoded = ShipJson.mapper().writeValueAsBytes(context);
            BlobReference reference = current.blobs().writeBytes("initial-context", encoded);
            BlobReference interactionBundle = current.interactions().create(
                    current.blobs(), current.view().runId(), context.digest());
            return new Mutation(
                    ShipAuthorityCommand.value(ShipEventType.CONTEXT_RECORDED, reference.digest()),
                    new ShipEventPayloads.ContextRecorded(reference, interactionBundle));
        });
    }

    private Mutation acceptResult(
            MutationContext current,
            byte[] encodedResult,
            ShipCatalogService.Snapshot catalogSnapshot)
            throws IOException {
        StageRequest request = current.view().activeRequest();
        if (request == null || current.view().activeRequestReference() == null) {
            throw new ShipControllerException(
                    "worker-result-unexpected", "Ship run has no active worker request");
        }
        StageResult result = ShipStageResultReader.read(
                request, Path.of(request.outputDirectory()), encodedResult);
        if (result.ledger() != null) {
            validateLedger(current, request, result);
        }
        return switch (result.outcome()) {
            case FAILED -> failedResult(
                    current, result, write(current.blobs(), "stage-result", result));
            case NEEDS_USER_INPUT -> discoveryQuestion(current, result);
            case NEEDS_DISCOVERY -> switch (result.stage()) {
                case DISCOVERY -> discoveryContinuation(current, result, catalogSnapshot);
                case REVIEW -> reviewReopened(current, result);
                case DESIGN -> designGaps(current, result);
                default -> throw new ShipControllerException(
                        "worker-result-outcome-invalid",
                        "Stage cannot request another discovery attempt: " + result.stage());
            };
            case COMPLETED -> switch (result.stage()) {
                case DISCOVERY -> gapReviewStarted(current, result);
                case REVIEW -> requirementsReady(current, result);
                default -> throw new ShipControllerException(
                        "artifact-broker-required",
                        "Completed " + result.stage()
                                                    + " results require the protected artifact broker");
            };
        };
    }

    private static void validateLedger(
            MutationContext current, StageRequest request, StageResult result)
            throws IOException {
        DecisionLedger previous = current.view().ledger() == null
                ? null
                : read(current.blobs(), current.view().ledger(), "decision-ledger", DecisionLedger.class);
        ShipLedgerSources.Snapshot sources = ShipLedgerSources.load(
                current.blobs(), current.view(), current.interactions());
        LedgerProvenanceValidator.EvolutionAuthorization authorization = null;
        if (request.stage() == ShipStage.DISCOVERY && previous != null) {
            if (sources.latestDiscoveryAnswer() != null
                    && sources.latestDiscoveryAnswer().answer().ledgerRevision()
                       == previous.revision()) {
                authorization = sources.latestDiscoveryAnswer();
            } else if (sources.latestRequirementsChange() != null
                    && sources.latestRequirementsChange().ledgerRevision()
                       == previous.revision()) {
                authorization = sources.latestRequirementsChange();
            }
        }
        LedgerProvenanceValidator.validate(
                request, result, sources.sources(), previous, authorization);
    }

    private Mutation discoveryQuestion(MutationContext current, StageResult result)
            throws IOException {
        DecisionLedger ledger = Objects.requireNonNull(result.ledger(), "discovery ledger");
        DecisionLedger.Question question = Objects.requireNonNull(
                result.question(), "discovery question");
        String nonce = current.interactions().signer().nonce();
        String mac = current.interactions().signer().sign(
                Interaction.discoveryChallengeMacFields(
                        current.view().runId(),
                        ledger.revision(),
                        question.id(),
                        question.openItemId(),
                        question.prompt(),
                        question.options(),
                        question.recommendation(),
                        nonce));
        DiscoveryChallenge challenge = new DiscoveryChallenge(
                Interaction.SCHEMA_VERSION,
                current.view().runId(),
                ledger.revision(),
                question.id(),
                question.openItemId(),
                question.prompt(),
                question.options(),
                question.recommendation(),
                nonce,
                mac);
        DiscoveryAnswer maximumAnswer = signedDiscoveryAnswer(
                current,
                challenge,
                "\u0001".repeat(Interaction.MAX_RESPONSE_CHARS),
                "\u0001".repeat(Interaction.MAX_CHANNEL_CHARS),
                Instant.MAX);
        current.interactions().preflightRecord(
                current.blobs(), current.view().interactionBundle(), challenge, maximumAnswer);
        BlobReference resultReference = write(current.blobs(), "stage-result", result);
        BlobReference ledgerReference = write(current.blobs(), "decision-ledger", ledger);
        BlobReference bundle = current.interactions().record(
                current.blobs(), current.view().interactionBundle(), challenge);
        return new Mutation(
                ShipAuthorityCommand.value(
                        ShipEventType.DISCOVERY_QUESTION_PRESENTED, question.id()),
                new ShipEventPayloads.DiscoveryQuestionPresented(
                        current.view().activeRequestReference(),
                        resultReference,
                        ledgerReference,
                        challenge,
                        bundle));
    }

    private static DiscoveryAnswer signedDiscoveryAnswer(
            MutationContext current,
            DiscoveryChallenge challenge,
            String response,
            String channel,
            Instant answeredAt)
            throws IOException {
        DiscoveryAnswer provisional = new DiscoveryAnswer(
                Interaction.SCHEMA_VERSION,
                current.view().runId(),
                challenge.ledgerRevision(),
                challenge.questionId(),
                challenge.openItemId(),
                challenge.nonce(),
                response,
                channel,
                answeredAt,
                UNBOUND_INTERACTION_MAC);
        String mac = current.interactions().signer().sign(
                Interaction.discoveryAnswerMacFields(provisional));
        return new DiscoveryAnswer(
                provisional.schemaVersion(),
                provisional.runId(),
                provisional.ledgerRevision(),
                provisional.questionId(),
                provisional.openItemId(),
                provisional.nonce(),
                provisional.response(),
                provisional.channel(),
                provisional.answeredAt(),
                mac);
    }

    private Mutation discoveryContinuation(
            MutationContext current,
            StageResult result,
            ShipCatalogService.Snapshot catalogSnapshot)
            throws IOException {
        if (catalogSnapshot == null) {
            throw new ShipControllerException(
                    "catalog-snapshot-required",
                    "Discovery catalog requests require one frozen controller catalog snapshot");
        }
        DecisionLedger ledger = Objects.requireNonNull(result.ledger(), "discovery ledger");
        try {
            LedgerValidator.validateCatalogPrerequisites(ledger);
        } catch (LedgerValidationException e) {
            throw new ShipControllerException(
                    "catalog-prerequisites-unresolved", e.getMessage(), e);
        }
        CatalogTarget target = catalogTarget(ledger);
        CatalogEvidenceSet evidence = CatalogEvidenceValidator.resolveEvidence(
                catalogSnapshot, target, result.catalogRequests());
        BlobReference resultReference = write(current.blobs(), "stage-result", result);
        BlobReference ledgerReference = write(current.blobs(), "decision-ledger", ledger);
        BlobReference evidenceReference = write(current.blobs(), "catalog-evidence", evidence);
        ShipAuthorityCommand command = ShipAuthorityCommand.value(
                ShipEventType.DISCOVERY_CONTINUED, evidenceReference.digest());
        AttemptInputs inputs = inputs(
                current.view(),
                ledgerReference,
                evidenceReference,
                current.view().interactionBundle());
        ShipEventPayloads.StageStarted continuation = issueStage(
                current, command, ShipStage.DISCOVERY, inputs);
        return new Mutation(
                command,
                accepted(
                        current,
                        result,
                        resultReference,
                        ledgerReference,
                        evidenceReference,
                        null,
                        continuation));
    }

    private Mutation gapReviewStarted(MutationContext current, StageResult result)
            throws IOException {
        if (current.view().catalogEvidence() == null) {
            throw new ShipControllerException(
                    "catalog-evidence-required",
                    "Discovery cannot complete before controller-owned catalog evidence is recorded");
        }
        BlobReference resultReference = write(current.blobs(), "stage-result", result);
        BlobReference ledgerReference = write(
                current.blobs(), "decision-ledger", result.ledger());
        ShipAuthorityCommand command = ShipAuthorityCommand.value(
                ShipEventType.GAP_REVIEW_STARTED, ledgerReference.digest());
        AttemptInputs inputs = inputs(
                current.view(),
                ledgerReference,
                current.view().catalogEvidence(),
                current.view().interactionBundle());
        ShipEventPayloads.StageStarted continuation = issueStage(
                current, command, ShipStage.REVIEW, inputs);
        return new Mutation(
                command,
                accepted(
                        current,
                        result,
                        resultReference,
                        ledgerReference,
                        current.view().catalogEvidence(),
                        null,
                        continuation));
    }

    private Mutation reviewReopened(MutationContext current, StageResult result)
            throws IOException {
        if (current.view().ledger() == null) {
            throw new IOException("Gap review has no protected candidate ledger");
        }
        BlobReference resultReference = write(current.blobs(), "stage-result", result);
        BlobReference ledgerReference = write(
                current.blobs(), "decision-ledger", result.ledger());
        ShipAuthorityCommand command = ShipAuthorityCommand.review(
                current.view().ledger().digest(), resultReference.digest());
        AttemptInputs inputs = inputs(
                current.view(), ledgerReference, null, current.view().interactionBundle());
        ShipEventPayloads.StageStarted continuation = issueStage(
                current, command, ShipStage.DISCOVERY, inputs);
        return new Mutation(
                command,
                accepted(
                        current,
                        result,
                        resultReference,
                        ledgerReference,
                        null,
                        null,
                        continuation));
    }

    private Mutation designGaps(MutationContext current, StageResult result)
            throws IOException {
        BlobReference resultReference = write(current.blobs(), "stage-result", result);
        BlobReference ledgerReference = write(
                current.blobs(), "decision-ledger", result.ledger());
        ShipAuthorityCommand command = ShipAuthorityCommand.designGaps(
                current.view().activeRequest().inputDigest(), resultReference.digest());
        AttemptInputs inputs = inputs(
                current.view(), ledgerReference, null, current.view().interactionBundle());
        ShipEventPayloads.StageStarted continuation = issueStage(
                current, command, ShipStage.DISCOVERY, inputs);
        return new Mutation(
                command,
                accepted(
                        current,
                        result,
                        resultReference,
                        ledgerReference,
                        null,
                        null,
                        continuation));
    }

    private Mutation requirementsReady(MutationContext current, StageResult result)
            throws IOException {
        if (current.view().context() == null
                || current.view().catalogEvidence() == null
                || current.view().baselineSnapshot() == null) {
            throw new IOException("Gap review lacks its protected authority inputs");
        }
        BlobReference resultReference = write(current.blobs(), "stage-result", result);
        BlobReference ledgerReference = write(
                current.blobs(), "decision-ledger", result.ledger());
        AuthorityBasis basis = new AuthorityBasis(
                new ContentId(current.view().context().digest()),
                new ContentId(ledgerReference.digest()),
                result.ledger().revision(),
                new ContentId(ledgerReference.digest()),
                new ContentId(current.view().activeRequest().policyDigest()),
                new ContentId(current.view().baselineSnapshot().digest()));
        ShipAuthorityCommand command = ShipAuthorityCommand.requirements(basis);
        return new Mutation(
                command,
                accepted(
                        current,
                        result,
                        resultReference,
                        ledgerReference,
                        current.view().catalogEvidence(),
                        ledgerReference.digest(),
                        null));
    }

    private static Mutation failedResult(
            MutationContext current, StageResult result, BlobReference resultReference) {
        return new Mutation(
                ShipAuthorityCommand.empty(ShipEventType.ATTEMPT_FAILED_RETRYABLE),
                new ShipEventPayloads.Failure(
                        result.stage(),
                        result.failureCode(),
                        result.failureMessage(),
                        current.view().activeRequestReference(),
                        resultReference,
                        null,
                        null,
                        null));
    }

    private static ShipEventPayloads.StageAccepted accepted(
            MutationContext current,
            StageResult result,
            BlobReference resultReference,
            BlobReference ledger,
            BlobReference catalogEvidence,
            String requirementsDigest,
            ShipEventPayloads.StageStarted continuation) {
        return new ShipEventPayloads.StageAccepted(
                result.stage(),
                current.view().activeRequestReference(),
                resultReference,
                List.of(),
                ledger,
                null,
                catalogEvidence,
                null,
                requirementsDigest,
                null,
                null,
                null,
                continuation);
    }

    private static CatalogTarget catalogTarget(DecisionLedger ledger) {
        DecisionLedger.RequirementsPolicy policy = Objects.requireNonNull(
                ledger.requirementsPolicy(), "catalog requirements policy");
        return new CatalogTarget(
                policy.runtime(),
                policy.camelVersion(),
                policy.platformVersion(),
                policy.springBootVersion());
    }

    private static Mutation startStage(
            MutationContext current,
            ShipAuthorityCommand command,
            ShipStage stage,
            AttemptInputs inputs)
            throws IOException {
        return new Mutation(command, issueStage(current, command, stage, inputs));
    }

    private static ShipEventPayloads.StageStarted issueStage(
            MutationContext current,
            ShipAuthorityCommand command,
            ShipStage stage,
            AttemptInputs inputs)
            throws IOException {
        ShipRun successor = command.apply(current.view().authority());
        Attempt attempt = ShipLifecycleReducer.attempt(successor);
        if (stage(attempt.phase()) != stage) {
            throw new IOException("Lifecycle successor does not issue the requested worker stage");
        }
        StageRequest request = new ShipAttemptFactory().createRunnable(
                current.view(),
                current.blobs(),
                stage,
                Math.toIntExact(attempt.number()),
                inputs);
        BlobReference requestReference = write(current.blobs(), "stage-request", request);
        return new ShipEventPayloads.StageStarted(
                stage, requestReference, request.outputDirectory());
    }

    private static AttemptInputs inputs(
            ShipRunView run,
            BlobReference ledger,
            BlobReference catalogEvidence,
            BlobReference interactionBundle) {
        return new AttemptInputs(
                run.context(),
                ledger,
                catalogEvidence,
                run.design(),
                run.plan(),
                interactionBundle,
                run.artifactManifest(),
                run.candidateSnapshot(),
                run.candidateDirectory(),
                run.evidence(),
                null,
                null);
    }

    private static ShipStage stage(ShipPhase phase) {
        return switch (phase) {
            case DISCOVERY -> ShipStage.DISCOVERY;
            case REVIEW -> ShipStage.REVIEW;
            case DESIGN -> ShipStage.DESIGN;
            case PLAN -> ShipStage.PLAN;
            case EXECUTE -> ShipStage.EXECUTE;
            case VALIDATE -> ShipStage.VALIDATE;
            case CONTEXT, STAMP, WAIVER_STAMP -> throw new ShipControllerException(
                    "lifecycle-boundary", "Lifecycle phase has no worker stage: " + phase);
        };
    }

    private static <T> BlobReference write(ShipBlobStore blobs, String kind, T value)
            throws IOException {
        return blobs.writeBytes(kind, ShipJson.mapper().writeValueAsBytes(value));
    }

    private static <T> T read(
            ShipBlobStore blobs, BlobReference reference, String kind, Class<T> type)
            throws IOException {
        if (reference == null || !kind.equals(reference.kind())) {
            throw new IOException("Expected protected " + kind + " blob reference");
        }
        return ShipJson.mapper().readValue(
                blobs.readBytes(reference, ShipJson.MAX_DOCUMENT_BYTES), type);
    }

    private ShipRunView mutate(
            String runId, String expectedEventDigest, MutationFactory factory)
            throws IOException {
        OpenRun run = open(runId);
        ShipRunView before = projector(run.events(), run.blobs(), run.signer()).replay();
        try (ShipOperationLock.Lease runLease = ShipOperationLock.acquireRun(stateRoot, runId);
             ShipOperationLock.Lease projectLease
                     = ShipOperationLock.acquireProject(stateRoot, before.projectRoot())) {
            ShipRunView current = projector(run.events(), run.blobs(), run.signer()).replay();
            if (!Objects.equals(expectedEventDigest, current.eventDigest())) {
                throw new ShipControllerException(
                        "stale-run-head", "Ship run changed before the requested operation could commit");
            }
            ShipEventHead expectedHead = run.events().currentHead();
            if (!expectedHead.eventDigest().equals(current.eventDigest())
                    || !expectedHead.authorityHead().equals(current.authority().head())) {
                throw new IOException("Durable event head differs from the reconstructed lifecycle authority");
            }
            Mutation mutation = factory.create(new MutationContext(
                    current,
                    run.blobs(),
                    new ShipInteractionBundleService(run.signer())));
            ShipRun successor = mutation.authority().apply(current.authority());
            projector(run.events(), run.blobs(), run.signer()).preflight(
                    mutation.authority(), mutation.data(), successor);
            run.events().appendIfLatest(
                    expectedHead,
                    new ShipEventDraft(
                            mutation.authority().type(),
                            successor.state(),
                            current.authority().head(),
                            successor.head(),
                            now(),
                            ShipStoredEventCodec.encode(mutation.authority(), mutation.data())));
            return projector(run.events(), run.blobs(), run.signer()).replay();
        }
    }

    private OpenRun open(String runId) throws IOException {
        ShipRunId parsed;
        try {
            parsed = ShipRunId.fromStorageId(runId);
        } catch (IllegalArgumentException e) {
            throw new ShipControllerException("run-id-invalid", "Invalid Ship run ID", e);
        }
        FileShipEventStore events = FileShipEventStore.open(stateRoot, parsed);
        ShipBlobStore blobs = ShipBlobStore.open(stateRoot, runId);
        ShipInteractionSigner signer = ShipInteractionSigner.open(
                ShipControllerPaths.requireRunRoot(stateRoot, parsed));
        return new OpenRun(events, blobs, signer);
    }

    private static ShipRunProjector projector(
            FileShipEventStore events, ShipBlobStore blobs, ShipInteractionSigner signer) {
        return new ShipRunProjector(events, blobs, new ShipInteractionBundleService(signer));
    }

    private Instant now() {
        return Instant.now(clock);
    }

    private static void requireState(ShipRunView view, ShipState expected) {
        if (view.state() != expected) {
            throw new ShipControllerException(
                    "lifecycle-boundary",
                    "Ship operation requires state " + expected.stableId()
                                          + " but run is " + view.state().stableId());
        }
    }

    private static InitialContextRequest normalizeRequest(
            InitialContextRequest request, Path projectRoot) {
        List<InitialContextRequest.Source> sources = request.sources()
                .stream().<InitialContextRequest.Source>map(source -> source instanceof UserText text
                        ? text
                        : normalizeDocument((DocumentReference) source, projectRoot))
                .toList();
        return request instanceof InitialContextRequest.None ? new InitialContextRequest.None()
                : request instanceof InitialContextRequest.Text
                        ? new InitialContextRequest.Text(sources.stream().map(UserText.class::cast).toList())
                : request instanceof InitialContextRequest.Documents
                        ? new InitialContextRequest.Documents(
                                sources.stream().map(DocumentReference.class::cast).toList())
                : new InitialContextRequest.Composite(sources);
    }

    private static DocumentReference normalizeDocument(
            DocumentReference document, Path projectRoot) {
        try {
            Path path = Path.of(document.value());
            if (path.isAbsolute() && path.normalize().equals(path) && path.startsWith(projectRoot)) {
                String relative = projectRoot.relativize(path).toString()
                        .replace(path.getFileSystem().getSeparator(), "/");
                return new DocumentReference(relative);
            }
        } catch (InvalidPathException ignored) {
            // The resolver owns the deterministic malformed-reference diagnostic.
        }
        return document;
    }

    private static void requireImmutableContextSources(
            ShipBlobStore blobs, ShipRunView run, InitialContext context)
            throws IOException {
        if (run.projectSourceManifest() == null) {
            throw new IOException("Ship run has no protected project-source manifest");
        }
        ProjectSourceManifest manifest = ShipJson.mapper().readValue(
                blobs.readBytes(run.projectSourceManifest(), ShipJson.MAX_DOCUMENT_BYTES),
                ProjectSourceManifest.class);
        for (InitialContext.Source source : context.sources()) {
            if (source.kind() != InitialContext.SourceKind.DOCUMENT) {
                continue;
            }
            String prefix = "project:";
            String relative = source.provenance().substring(prefix.length());
            boolean exact = manifest.sources().stream().anyMatch(candidate -> candidate.relativePath().equals(relative)
                    && candidate.digest().equals(source.digest())
                    && candidate.byteSize() == source.byteSize());
            if (!exact) {
                throw new ShipControllerException(
                        "context-source-changed",
                        "Resolved context document differs from the immutable Ship project source");
            }
        }
    }

    private static void preflightImmutableRequestSources(
            ShipBlobStore blobs, ShipRunView run, InitialContextRequest request)
            throws IOException {
        if (run.projectSourceManifest() == null) {
            throw new IOException("Ship run has no protected project-source manifest");
        }
        ProjectSourceManifest manifest = ShipJson.mapper().readValue(
                blobs.readBytes(run.projectSourceManifest(), ShipJson.MAX_DOCUMENT_BYTES),
                ProjectSourceManifest.class);
        List<InitialContextRequest.Source> protectedSources = new ArrayList<>();
        for (InitialContextRequest.Source source : request.sources()) {
            if (source instanceof UserText) {
                protectedSources.add(source);
                continue;
            }
            DocumentReference document = (DocumentReference) source;
            Path path = Path.of(document.value());
            if (path.isAbsolute()) {
                continue;
            }
            if (manifest.sources().stream().noneMatch(
                    candidate -> candidate.relativePath().equals(document.value()))) {
                throw new ShipControllerException(
                        "context-source-unavailable",
                        "Project context document is absent from the immutable Ship source");
            }
            protectedSources.add(document);
        }
        if (protectedSources.isEmpty()) {
            return;
        }
        InitialContextRequest protectedRequest = protectedRequest(protectedSources);
        try {
            InitialContextResolver.resolveProtectedProjectRequest(
                    protectedRequest,
                    (relativePath, maximumBytes) -> {
                        ProjectSourceManifest.FileSource source = manifest.sources().stream()
                                .filter(candidate -> candidate.relativePath().equals(relativePath))
                                .findFirst()
                                .orElseThrow(() -> new ShipControllerException(
                                        "context-source-unavailable",
                                        "Project context document is absent from the immutable Ship source"));
                        byte[] content = ProjectEvidenceFiles.readMaterial(
                                run.sourceDirectory(), relativePath, maximumBytes);
                        if (content.length != source.byteSize()
                                || !ShipDigest.sha256(content).equals(source.digest())) {
                            throw new ShipControllerException(
                                    "context-source-changed",
                                    "Protected context document differs from the immutable Ship source");
                        }
                        return content;
                    });
        } catch (InitialContextException e) {
            throw new IOException(
                    "Initial-context project source is not durably resolvable: " + e.getMessage(), e);
        }
    }

    private static InitialContextRequest protectedRequest(
            List<InitialContextRequest.Source> sources) {
        boolean hasText = sources.stream().anyMatch(UserText.class::isInstance);
        boolean hasDocuments = sources.stream().anyMatch(DocumentReference.class::isInstance);
        if (hasText && hasDocuments) {
            return new InitialContextRequest.Composite(sources);
        }
        if (hasDocuments) {
            return new InitialContextRequest.Documents(
                    sources.stream().map(DocumentReference.class::cast).toList());
        }
        return new InitialContextRequest.Text(
                sources.stream().map(UserText.class::cast).toList());
    }

    private static void requireDisjointRoots(Path stateRoot, Path projectRoot) {
        try {
            if (stateRoot.startsWith(projectRoot) || projectRoot.startsWith(stateRoot)) {
                throw new ShipControllerException(
                        "state-project-overlap",
                        "Ship controller state and project roots must be disjoint");
            }
        } catch (ProviderMismatchException ignored) {
            // Paths on distinct providers cannot contain one another.
        }
    }

    record PreparedRun(Path projectRoot, String adapterId) {

        PreparedRun {
            Path suppliedRoot = Objects.requireNonNull(projectRoot, "projectRoot");
            Path normalizedRoot = suppliedRoot.toAbsolutePath().normalize();
            if (!suppliedRoot.equals(normalizedRoot)
                    || adapterId == null
                    || !adapterId.matches("[a-z][a-z0-9-]{0,63}")) {
                throw new IllegalArgumentException("Invalid prepared Ship run identity");
            }
            projectRoot = normalizedRoot;
        }
    }

    private interface MutationFactory {

        Mutation create(MutationContext current) throws IOException;
    }

    private record MutationContext(
            ShipRunView view,
            ShipBlobStore blobs,
            ShipInteractionBundleService interactions) {
    }

    private record Mutation(ShipAuthorityCommand authority, ShipEventPayloads.Payload data) {

        private Mutation {
            Objects.requireNonNull(authority, "authority");
            Objects.requireNonNull(data, "data");
        }
    }

    private record OpenRun(
            FileShipEventStore events, ShipBlobStore blobs, ShipInteractionSigner signer) {
    }
}
