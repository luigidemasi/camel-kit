package io.github.luigidemasi.camelkit.ship.controller;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import io.github.luigidemasi.camelkit.ship.ShipDigest;
import io.github.luigidemasi.camelkit.ship.controller.LedgerProvenanceValidator.DiscoveryAnswerAuthorization;
import io.github.luigidemasi.camelkit.ship.controller.LedgerProvenanceValidator.EvolutionAuthorization;
import io.github.luigidemasi.camelkit.ship.controller.LedgerProvenanceValidator.RequirementsChangeAuthorization;
import io.github.luigidemasi.camelkit.ship.controller.LedgerProvenanceValidator.SourceKind;
import io.github.luigidemasi.camelkit.ship.controller.LedgerProvenanceValidator.SourceMaterial;
import io.github.luigidemasi.camelkit.ship.ledger.DecisionLedger;
import io.github.luigidemasi.camelkit.ship.ledger.DecisionLedger.Assumption;
import io.github.luigidemasi.camelkit.ship.ledger.DecisionLedger.Category;
import io.github.luigidemasi.camelkit.ship.ledger.DecisionLedger.Entry;
import io.github.luigidemasi.camelkit.ship.ledger.DecisionLedger.GapReviewStatus;
import io.github.luigidemasi.camelkit.ship.ledger.DecisionLedger.Question;
import io.github.luigidemasi.camelkit.ship.ledger.DecisionLedger.RequirementsPolicy;
import io.github.luigidemasi.camelkit.ship.ledger.DecisionLedger.SourceRef;
import io.github.luigidemasi.camelkit.ship.ledger.LedgerStatus;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.DesignDecision;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.DesignResponse;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.DiscoveryAnswer;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.PlanDecision;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.PlanResponse;
import io.github.luigidemasi.camelkit.ship.protocol.ShipStage;
import io.github.luigidemasi.camelkit.ship.protocol.StageCapability;
import io.github.luigidemasi.camelkit.ship.protocol.StageRequest;
import io.github.luigidemasi.camelkit.ship.protocol.StageResult;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LedgerProvenanceValidatorTest {

    private static final String RUN_ID = "ship-" + "1".repeat(32);
    private static final String ATTEMPT_ID = "discovery-1-" + "2".repeat(16);
    private static final String CHALLENGE = "3".repeat(43);
    private static final String INPUT_DIGEST = digest("input");
    private static final String POLICY_DIGEST = digest("policy");
    private static final String SOURCE_CONTENT = "The required runtime is Camel Main.";

    @Test
    void acceptsExactControllerOwnedEvidenceAndAnInitialContextFragment() {
        SourceMaterial source = source(SourceKind.INITIAL_CONTEXT, "source-runtime", SOURCE_CONTENT);
        DecisionLedger ledger = ledger(1, GapReviewStatus.NOT_RUN, "runtime", "main", reference(
                source, source.canonicalLocator() + "#runtime", "Camel Main"));

        assertDoesNotThrow(() -> validate(request(ShipStage.DISCOVERY), ledger, List.of(source), null));
    }

    @Test
    void rejectsFabricatedUnknownAndCrossBoundReferences() {
        SourceMaterial source = source(SourceKind.INITIAL_CONTEXT, "source-runtime", SOURCE_CONTENT);

        assertCode("ledger-provenance-invalid", () -> validate(
                request(ShipStage.DISCOVERY),
                ledger(1, GapReviewStatus.NOT_RUN, "runtime", "main", reference(
                        source, source.canonicalLocator(), "camel main")),
                List.of(source),
                null));
        assertCode("ledger-provenance-invalid", () -> validate(
                request(ShipStage.DISCOVERY),
                ledger(1, GapReviewStatus.NOT_RUN, "runtime", "main", new SourceRef(
                        "unknown", source.canonicalLocator(), source.digest(), "Camel Main")),
                List.of(source),
                null));
        assertCode("ledger-provenance-invalid", () -> validate(
                request(ShipStage.DISCOVERY),
                ledger(1, GapReviewStatus.NOT_RUN, "runtime", "main", new SourceRef(
                        source.sourceId(), "context:other", source.digest(), "Camel Main")),
                List.of(source),
                null));
        assertCode("ledger-provenance-invalid", () -> validate(
                request(ShipStage.DISCOVERY),
                ledger(1, GapReviewStatus.NOT_RUN, "runtime", "main", new SourceRef(
                        source.sourceId(), source.canonicalLocator(), digest("other"), "Camel Main")),
                List.of(source),
                null));
        assertCode("ledger-provenance-invalid", () -> validate(
                request(ShipStage.DISCOVERY),
                ledger(1, GapReviewStatus.NOT_RUN, "runtime", "main", reference(
                        source, source.canonicalLocator() + "#bad\nfragment", "Camel Main")),
                List.of(source),
                null));
    }

    @Test
    void requiresEveryNonemptyInitialContextSourceButNotOptionalSources() {
        SourceMaterial runtime = source(SourceKind.INITIAL_CONTEXT, "source-runtime", SOURCE_CONTENT);
        SourceMaterial ignored = source(SourceKind.INITIAL_CONTEXT, "source-dsl", "The route DSL is YAML.");
        SourceMaterial empty = source(SourceKind.INITIAL_CONTEXT, "source-empty", "");
        SourceMaterial blank = source(SourceKind.INITIAL_CONTEXT, "source-blank", " \n\t");
        SourceMaterial project = source(SourceKind.PROJECT, "project-readme", "Unrelated project text.");
        DecisionLedger ledger = ledger(1, GapReviewStatus.NOT_RUN, "runtime", "main", reference(
                runtime, runtime.canonicalLocator(), "Camel Main"));

        assertCode("initial-context-unanalysed", () -> validate(
                request(ShipStage.DISCOVERY), ledger, List.of(runtime, ignored, empty, blank, project), null));
        assertDoesNotThrow(() -> validate(
                request(ShipStage.DISCOVERY), ledger, List.of(runtime, empty, blank, project), null));
    }

    @Test
    void allowsFragmentsOnlyForInitialContextLocators() {
        SourceMaterial project = source(SourceKind.PROJECT, "project-runtime", SOURCE_CONTENT);
        SourceMaterial interaction = source(SourceKind.INTERACTION, "interaction-runtime", SOURCE_CONTENT);

        assertDoesNotThrow(() -> validate(
                request(ShipStage.DISCOVERY),
                ledger(1, GapReviewStatus.NOT_RUN, "runtime", "main", reference(
                        project, project.canonicalLocator(), "Camel Main")),
                List.of(project),
                null));
        assertDoesNotThrow(() -> validate(
                request(ShipStage.DISCOVERY),
                ledger(1, GapReviewStatus.NOT_RUN, "runtime", "main", reference(
                        interaction, interaction.canonicalLocator(), "Camel Main")),
                List.of(interaction),
                null));
        assertCode("ledger-provenance-invalid", () -> validate(
                request(ShipStage.DISCOVERY),
                ledger(1, GapReviewStatus.NOT_RUN, "runtime", "main", reference(
                        project, project.canonicalLocator() + "#runtime", "Camel Main")),
                List.of(project),
                null));
        assertCode("ledger-provenance-invalid", () -> validate(
                request(ShipStage.DISCOVERY),
                ledger(1, GapReviewStatus.NOT_RUN, "runtime", "main", reference(
                        interaction, interaction.canonicalLocator() + "#runtime", "Camel Main")),
                List.of(interaction),
                null));
    }

    @Test
    void admitsTheFullControllerLocatorEnvelope() {
        String maximumPath = String.join("/", java.util.Collections.nCopies(17, "p".repeat(240)));
        assertEquals(4096, maximumPath.length());
        String canonicalLocator = "project:" + maximumPath;
        byte[] bytes = SOURCE_CONTENT.getBytes(StandardCharsets.UTF_8);
        SourceMaterial source = new SourceMaterial(
                SourceKind.INITIAL_CONTEXT,
                "source-max-locator",
                canonicalLocator,
                ShipDigest.sha256(bytes),
                bytes);
        SourceRef reference = reference(
                source, canonicalLocator + "#" + "f".repeat(4096), "Camel Main");

        assertDoesNotThrow(() -> validate(
                request(ShipStage.DISCOVERY),
                ledger(1, GapReviewStatus.NOT_RUN, "runtime", "main", reference),
                List.of(source),
                null));

        SourceMaterial project = new SourceMaterial(
                SourceKind.PROJECT,
                "project-max-locator",
                "controller:project-source#" + maximumPath,
                ShipDigest.sha256(bytes),
                bytes);
        assertDoesNotThrow(() -> validate(
                request(ShipStage.DISCOVERY),
                ledger(1, GapReviewStatus.NOT_RUN, "runtime", "main", reference(
                        project, project.canonicalLocator(), "Camel Main")),
                List.of(project),
                null));
    }

    @Test
    void requiresResolvedValuesAndEveryCsvMemberToBeSupported() {
        SourceMaterial runtime = source(SourceKind.INITIAL_CONTEXT, "source-runtime", SOURCE_CONTENT);
        assertCode("ledger-value-unsupported", () -> validate(
                request(ShipStage.DISCOVERY),
                ledger(1, GapReviewStatus.NOT_RUN, "runtime", "spring-boot", reference(
                        runtime, runtime.canonicalLocator(), "Camel Main")),
                List.of(runtime),
                null));

        String dependencies = "Use citrus-camel and citrus-junit5.";
        SourceMaterial citrus = source(SourceKind.INITIAL_CONTEXT, "source-citrus", dependencies);
        SourceRef citrusReference = reference(citrus, citrus.canonicalLocator(), dependencies);
        assertDoesNotThrow(() -> validate(
                request(ShipStage.DISCOVERY),
                ledger(1, GapReviewStatus.NOT_RUN, "citrus-dependencies",
                        "citrus-camel, citrus-junit5", citrusReference),
                List.of(citrus),
                null));
        assertCode("ledger-value-unsupported", () -> validate(
                request(ShipStage.DISCOVERY),
                ledger(1, GapReviewStatus.NOT_RUN, "citrus-dependencies",
                        "citrus-camel, citrus-http", citrusReference),
                List.of(citrus),
                null));
    }

    @Test
    void bindsRevisionsAndGapReviewToTheActiveAttempt() {
        SourceMaterial source = source(SourceKind.INITIAL_CONTEXT, "source-runtime", SOURCE_CONTENT);
        SourceRef reference = reference(source, source.canonicalLocator(), "Camel Main");
        DecisionLedger first = ledger(1, GapReviewStatus.NOT_RUN, "runtime", "main", reference);
        DecisionLedger second = ledger(2, GapReviewStatus.NOT_RUN, "runtime", "main", reference);

        assertDoesNotThrow(() -> validate(request(ShipStage.DISCOVERY), second, List.of(source), first));
        assertCode("ledger-provenance-invalid", () -> validate(
                request(ShipStage.DISCOVERY), second, List.of(source), null));
        assertCode("ledger-provenance-invalid", () -> validate(
                request(ShipStage.DISCOVERY),
                ledger(1, GapReviewStatus.PASSED, "runtime", "main", reference),
                List.of(source),
                null));

        SourceRef invented = new SourceRef(
                "invented", "context:invented", digest("invented"), "Camel Main");
        DecisionLedger untrustedPrevious = ledger(
                1, GapReviewStatus.NOT_RUN, "runtime", "main", invented);
        assertCode("ledger-provenance-invalid", () -> validate(
                request(ShipStage.DISCOVERY), second, List.of(source), untrustedPrevious));

        DecisionLedger reviewed = ledger(1, GapReviewStatus.PASSED, "runtime", "main", reference);
        assertDoesNotThrow(() -> validate(request(ShipStage.REVIEW), reviewed, List.of(source), first));
        assertCode("ledger-provenance-invalid", () -> validate(
                request(ShipStage.REVIEW), reviewed, List.of(source), null));
        DecisionLedger changed = ledger(1, GapReviewStatus.PASSED, "runtime", "Camel Main!", reference);
        assertCode("ledger-provenance-invalid", () -> validate(
                request(ShipStage.REVIEW), changed, List.of(source), first));

        StageRequest reviewRequest = request(ShipStage.REVIEW);
        DecisionLedger invalidReopening = ledger(
                2, GapReviewStatus.PASSED, "runtime", "main", reference);
        StageResult reopening = result(
                reviewRequest,
                invalidReopening,
                reviewRequest.attemptId(),
                StageResult.Outcome.NEEDS_DISCOVERY);
        assertCode("ledger-provenance-invalid", () -> LedgerProvenanceValidator.validate(
                reviewRequest, reopening, List.of(source), first, null));

        StageRequest request = request(ShipStage.DISCOVERY);
        StageResult wrongAttempt = result(request, second, ATTEMPT_ID + "-other");
        assertCode("ledger-provenance-invalid", () -> LedgerProvenanceValidator.validate(
                request, wrongAttempt, List.of(source), first, null));
    }

    @Test
    void bindsAnAnswerDeltaToTheExactCurrentInteractionSource() {
        SourceMaterial context = source(
                SourceKind.INITIAL_CONTEXT,
                "source-context",
                "The route DSL is YAML. Camel Main is also mentioned.");
        SourceMaterial answerSource = source(
                SourceKind.INTERACTION, "interaction-current-answer", "Use Camel Main.");
        SourceRef contextReference = reference(context, context.canonicalLocator(), "YAML");
        SourceRef answerReference = reference(answerSource, answerSource.canonicalLocator(), "Camel Main");
        DecisionLedger previous = openRuntimeLedger(contextReference);
        DecisionLedger proposed = answeredRuntimeLedger(previous, answerReference);
        DiscoveryAnswer answer = discoveryAnswer("Use Camel Main.");
        DiscoveryAnswerAuthorization authorization = new DiscoveryAnswerAuthorization(answer, answerSource);
        StageRequest request = request(ShipStage.DISCOVERY);

        assertCode("ledger-evolution-invalid", () -> LedgerProvenanceValidator.validate(
                request,
                result(request, proposed, request.attemptId()),
                List.of(context, answerSource),
                previous,
                null));
        assertDoesNotThrow(() -> LedgerProvenanceValidator.validate(
                request,
                result(request, proposed, request.attemptId()),
                List.of(context, answerSource),
                previous,
                authorization));

        Entry changedDsl = new Entry(
                previous.decisions().get(1).id(),
                previous.decisions().get(1).category(),
                previous.decisions().get(1).value(),
                previous.decisions().get(1).status(),
                previous.decisions().get(1).sourceRefs(),
                "unrelated mutation");
        DecisionLedger unrelatedMutation = copyLedger(
                proposed, List.of(proposed.decisions().get(0), changedDsl));
        assertCode("discovery-answer-delta-invalid", () -> LedgerProvenanceValidator.validate(
                request,
                result(request, unrelatedMutation, request.attemptId()),
                List.of(context, answerSource),
                previous,
                authorization));

        SourceRef oldContextReference = reference(context, context.canonicalLocator(), "Camel Main");
        Entry runtimeFromOldContext = new Entry(
                "runtime-choice",
                "runtime",
                "main",
                LedgerStatus.RESOLVED,
                List.of(oldContextReference),
                null);
        DecisionLedger oldEvidenceOnly = copyLedger(
                proposed, List.of(runtimeFromOldContext, proposed.decisions().get(1)));
        assertCode("discovery-answer-delta-invalid", () -> LedgerProvenanceValidator.validate(
                request,
                result(request, oldEvidenceOnly, request.attemptId()),
                List.of(context, answerSource),
                previous,
                authorization));

        SourceMaterial wrongAnswerSource = source(
                SourceKind.INTERACTION, "interaction-wrong-answer", "Use Quarkus.");
        DiscoveryAnswerAuthorization wrongAuthorization
                = new DiscoveryAnswerAuthorization(answer, wrongAnswerSource);
        assertCode("ledger-provenance-invalid", () -> LedgerProvenanceValidator.validate(
                request,
                result(request, proposed, request.attemptId()),
                List.of(context, answerSource, wrongAnswerSource),
                previous,
                wrongAuthorization));
    }

    @Test
    void authorizesOnlyExactDesignOrPlanRequirementsFeedback() throws Exception {
        assertRequirementsFeedbackAccepted(true);
        assertRequirementsFeedbackAccepted(false);
    }

    @Test
    void rejectsUnboundOrUnsupportedRequirementsFeedbackDeltas() throws Exception {
        SourceMaterial baseline = source(
                SourceKind.PROJECT,
                "project-requirements",
                "The runtime is Camel Main. Quarkus is also discussed.");
        SourceRef baselineReference = reference(
                baseline, baseline.canonicalLocator(), "runtime is Camel Main");
        DecisionLedger previous = requirementsLedger(
                1,
                GapReviewStatus.PASSED,
                "main",
                List.of(baselineReference));
        String feedback = "Change the runtime to Quarkus.";
        SourceMaterial feedbackSource = requirementsFeedbackSource("design", feedback);
        SourceRef feedbackReference = reference(
                feedbackSource, feedbackSource.canonicalLocator(), feedback);
        DecisionLedger proposed = requirementsLedger(
                2,
                GapReviewStatus.NOT_RUN,
                "quarkus",
                List.of(baselineReference, feedbackReference));
        String previousDigest = canonicalLedgerDigest(previous);
        StageRequest request = request(ShipStage.DISCOVERY);

        for (DesignResponse response : List.of(
                designRequirementsResponse(
                        "ship-" + "2".repeat(32), 1, previousDigest, previousDigest, feedback),
                designRequirementsResponse(
                        RUN_ID, 2, previousDigest, previousDigest, feedback),
                designRequirementsResponse(
                        RUN_ID, 1, digest("other-ledger"), previousDigest, feedback),
                designRequirementsResponse(
                        RUN_ID, 1, previousDigest, digest("other-requirements"), feedback))) {
            RequirementsChangeAuthorization mismatched = RequirementsChangeAuthorization.design(
                    response, feedbackSource);
            assertCode("requirements-change-delta-invalid", () -> LedgerProvenanceValidator.validate(
                    request,
                    result(request, proposed, request.attemptId()),
                    List.of(baseline, feedbackSource),
                    previous,
                    mismatched));
        }

        RequirementsChangeAuthorization authorization = RequirementsChangeAuthorization.design(
                designRequirementsResponse(previousDigest, feedback), feedbackSource);
        StageRequest wrongStage = request(ShipStage.DESIGN);
        assertCode("requirements-change-delta-invalid", () -> LedgerProvenanceValidator.validate(
                wrongStage,
                result(wrongStage, proposed, wrongStage.attemptId()),
                List.of(baseline, feedbackSource),
                previous,
                authorization));

        DecisionLedger unreviewed = requirementsLedger(
                1, GapReviewStatus.NOT_RUN, "main", List.of(baselineReference));
        RequirementsChangeAuthorization unreviewedAuthorization = RequirementsChangeAuthorization.design(
                designRequirementsResponse(canonicalLedgerDigest(unreviewed), feedback), feedbackSource);
        assertCode("requirements-change-delta-invalid", () -> LedgerProvenanceValidator.validate(
                request,
                result(request, proposed, request.attemptId()),
                List.of(baseline, feedbackSource),
                unreviewed,
                unreviewedAuthorization));

        DecisionLedger pendingQuestion = withOpenRequirementsQuestion(previous);
        RequirementsChangeAuthorization pendingAuthorization = RequirementsChangeAuthorization.design(
                designRequirementsResponse(canonicalLedgerDigest(pendingQuestion), feedback), feedbackSource);
        assertCode("requirements-change-delta-invalid", () -> LedgerProvenanceValidator.validate(
                request,
                result(request, proposed, request.attemptId()),
                List.of(baseline, feedbackSource),
                pendingQuestion,
                pendingAuthorization));

        DecisionLedger uncited = requirementsLedger(
                2,
                GapReviewStatus.NOT_RUN,
                "quarkus",
                List.of(reference(
                        baseline,
                        baseline.canonicalLocator(),
                        "Quarkus is also discussed")));
        assertCode("requirements-change-delta-invalid", () -> LedgerProvenanceValidator.validate(
                request,
                result(request, uncited, request.attemptId()),
                List.of(baseline, feedbackSource),
                previous,
                authorization));

        SourceMaterial wrongSource = requirementsFeedbackSource(
                "design", "Change the runtime to Quarkus immediately.");
        RequirementsChangeAuthorization sourceMismatch = RequirementsChangeAuthorization.design(
                designRequirementsResponse(previousDigest, feedback), wrongSource);
        assertCode("ledger-provenance-invalid", () -> LedgerProvenanceValidator.validate(
                request,
                result(request, proposed, request.attemptId()),
                List.of(baseline, feedbackSource, wrongSource),
                previous,
                sourceMismatch));
    }

    @Test
    void preservesExistingAnalysisWithoutAnAuthorizedAnswer() {
        SourceMaterial source = source(
                SourceKind.INITIAL_CONTEXT,
                "source-runtime",
                "Camel Main and YAML are both mentioned.");
        DecisionLedger previous = ledger(
                1,
                GapReviewStatus.NOT_RUN,
                "runtime",
                "main",
                reference(source, source.canonicalLocator(), "Camel Main"));
        DecisionLedger rewritten = ledger(
                2,
                GapReviewStatus.NOT_RUN,
                "runtime",
                "yaml",
                reference(source, source.canonicalLocator(), "YAML"));

        assertCode("ledger-evolution-invalid", () -> validate(
                request(ShipStage.DISCOVERY), rewritten, List.of(source), previous));

        for (ShipStage stage : List.of(ShipStage.DESIGN, ShipStage.REVIEW)) {
            StageRequest gapRequest = request(stage);
            assertCode("ledger-evolution-invalid", () -> LedgerProvenanceValidator.validate(
                    gapRequest,
                    result(
                            gapRequest,
                            rewritten,
                            gapRequest.attemptId(),
                            StageResult.Outcome.NEEDS_DISCOVERY),
                    List.of(source),
                    previous,
                    null));
        }
    }

    @Test
    void gapStagesMayAddBlockersAndRegressCompletenessButMayNotSilentlyImproveIt() {
        SourceMaterial source = source(
                SourceKind.INITIAL_CONTEXT,
                "source-security",
                "TLS is required, but the client trust policy is unknown.");
        SourceRef reference = reference(
                source, source.canonicalLocator(), "TLS is required");
        Entry decision = new Entry(
                "decision-security",
                "security",
                "TLS is required",
                LedgerStatus.RESOLVED,
                List.of(reference),
                null);
        DecisionLedger previous = new DecisionLedger(
                DecisionLedger.SCHEMA_VERSION,
                1,
                List.of(),
                List.of(decision),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(new Category(
                        "security", LedgerStatus.RESOLVED, null, List.of(reference))),
                List.of(),
                GapReviewStatus.NOT_RUN,
                null);
        Assumption gap = new Assumption(
                "trust-policy-gap",
                "security",
                "Use the platform trust store",
                LedgerStatus.NEEDS_USER_DECISION,
                List.of(),
                "The exact trust policy is not specified");
        DecisionLedger reopened = new DecisionLedger(
                DecisionLedger.SCHEMA_VERSION,
                2,
                previous.facts(),
                previous.decisions(),
                previous.conflicts(),
                List.of(gap),
                previous.catalogEvidence(),
                List.of(),
                List.of(new Category(
                        "security", LedgerStatus.NEEDS_USER_DECISION,
                        "The exact trust policy is not specified", List.of(reference))),
                List.of(gap.id()),
                GapReviewStatus.NOT_RUN,
                null);
        StageRequest reviewRequest = request(ShipStage.REVIEW);

        assertDoesNotThrow(() -> LedgerProvenanceValidator.validate(
                reviewRequest,
                result(
                        reviewRequest,
                        reopened,
                        reviewRequest.attemptId(),
                        StageResult.Outcome.NEEDS_DISCOVERY),
                List.of(source),
                previous,
                null));

        RequirementsPolicy injectedPolicy = new RequirementsPolicy(
                "main",
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                null,
                List.of("worker-injected-exception"),
                List.of(),
                false,
                false);
        DecisionLedger policyChanged = new DecisionLedger(
                DecisionLedger.SCHEMA_VERSION,
                3,
                reopened.facts(),
                reopened.decisions(),
                reopened.conflicts(),
                reopened.assumptions(),
                reopened.catalogEvidence(),
                reopened.openQuestions(),
                reopened.completeness(),
                reopened.blockingOpenItemIds(),
                reopened.gapReviewStatus(),
                injectedPolicy);
        assertCode("ledger-evolution-invalid", () -> LedgerProvenanceValidator.validate(
                reviewRequest,
                result(
                        reviewRequest,
                        policyChanged,
                        reviewRequest.attemptId(),
                        StageResult.Outcome.NEEDS_DISCOVERY),
                List.of(source),
                reopened,
                null));

        DecisionLedger silentlyImproved = new DecisionLedger(
                DecisionLedger.SCHEMA_VERSION,
                3,
                reopened.facts(),
                reopened.decisions(),
                reopened.conflicts(),
                reopened.assumptions(),
                reopened.catalogEvidence(),
                reopened.openQuestions(),
                List.of(new Category(
                        "security", LedgerStatus.RESOLVED, null, List.of(reference))),
                reopened.blockingOpenItemIds(),
                GapReviewStatus.NOT_RUN,
                null);

        assertCode("ledger-evolution-invalid", () -> LedgerProvenanceValidator.validate(
                reviewRequest,
                result(
                        reviewRequest,
                        silentlyImproved,
                        reviewRequest.attemptId(),
                        StageResult.Outcome.NEEDS_DISCOVERY),
                List.of(source),
                reopened,
                null));
    }

    @Test
    void permitsDirectCitrusPolicyFieldsFromTheirExactAnswers() {
        RequirementsPolicy citrusVersion = new RequirementsPolicy(
                null,
                null,
                null,
                null,
                null,
                null,
                "4.7.0",
                List.of(),
                null,
                List.of(),
                List.of(),
                false,
                false);
        assertPolicyAnswerAccepted(
                "citrus-version", "4.7.0", "Use Citrus 4.7.0.", citrusVersion);

        RequirementsPolicy citrusDependencies = new RequirementsPolicy(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of("citrus-camel", "citrus-junit5"),
                null,
                List.of(),
                List.of(),
                false,
                false);
        assertPolicyAnswerAccepted(
                "citrus-dependencies",
                "citrus-camel, citrus-junit5",
                "Use citrus-camel and citrus-junit5.",
                citrusDependencies);
    }

    @Test
    void rejectsDuplicateOrDigestMismatchedControllerSources() {
        SourceMaterial source = source(SourceKind.INITIAL_CONTEXT, "source-runtime", SOURCE_CONTENT);
        DecisionLedger ledger = ledger(1, GapReviewStatus.NOT_RUN, "runtime", "main", reference(
                source, source.canonicalLocator(), "Camel Main"));

        assertCode("ledger-provenance-invalid", () -> validate(
                request(ShipStage.DISCOVERY), ledger, List.of(source, source), null));
        assertThrows(IllegalArgumentException.class, () -> new SourceMaterial(
                SourceKind.INITIAL_CONTEXT,
                "source-runtime",
                "context:source-runtime",
                digest("different"),
                SOURCE_CONTENT.getBytes(StandardCharsets.UTF_8)));
    }

    private static void validate(
            StageRequest request,
            DecisionLedger ledger,
            List<SourceMaterial> sources,
            DecisionLedger previous) {
        LedgerProvenanceValidator.validate(
                request, result(request, ledger, request.attemptId()), sources, previous, null);
    }

    private static StageRequest request(ShipStage stage) {
        return new StageRequest(
                StageRequest.SCHEMA_VERSION,
                RUN_ID,
                stage,
                ATTEMPT_ID,
                1,
                digest("idempotency"),
                CHALLENGE,
                INPUT_DIGEST,
                POLICY_DIGEST,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                null,
                null,
                "worker-contract",
                "/ship/output",
                new StageCapability(
                        StageCapability.RepositoryAccess.READ_ONLY,
                        List.of(),
                        List.of(),
                        List.of(
                                StageCapability.Operation.READ,
                                StageCapability.Operation.SEARCH,
                                StageCapability.Operation.RETURN_STRUCTURED_RESULT),
                        false,
                        false));
    }

    private static StageResult result(StageRequest request, DecisionLedger ledger, String attemptId) {
        return result(request, ledger, attemptId, StageResult.Outcome.COMPLETED);
    }

    private static StageResult result(
            StageRequest request,
            DecisionLedger ledger,
            String attemptId,
            StageResult.Outcome outcome) {
        return new StageResult(
                StageResult.SCHEMA_VERSION,
                request.runId(),
                request.stage(),
                attemptId,
                request.challenge(),
                request.inputDigest(),
                outcome,
                ledger,
                null,
                List.of(),
                List.of(),
                null,
                null,
                null);
    }

    private static DecisionLedger openRuntimeLedger(SourceRef contextReference) {
        Entry runtime = new Entry(
                "runtime-choice",
                "runtime",
                null,
                LedgerStatus.NEEDS_USER_DECISION,
                List.of(),
                null);
        Entry dsl = new Entry(
                "dsl-choice", "dsl", "yaml", LedgerStatus.RESOLVED, List.of(contextReference), null);
        Question question = new Question(
                "question-runtime",
                runtime.id(),
                "Which runtime?",
                List.of("Camel Main", "Quarkus"),
                "Camel Main",
                LedgerStatus.OPEN);
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
                        new Category("dsl", LedgerStatus.RESOLVED, null, List.of(contextReference))),
                List.of(runtime.id()),
                GapReviewStatus.NOT_RUN,
                null);
    }

    private static DecisionLedger answeredRuntimeLedger(
            DecisionLedger previous, SourceRef answerReference) {
        Entry runtime = new Entry(
                "runtime-choice",
                "runtime",
                "main",
                LedgerStatus.RESOLVED,
                List.of(answerReference),
                null);
        RequirementsPolicy policy = new RequirementsPolicy(
                "main",
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                null,
                List.of(),
                List.of(),
                false,
                false);
        return new DecisionLedger(
                DecisionLedger.SCHEMA_VERSION,
                2,
                List.of(),
                List.of(runtime, previous.decisions().get(1)),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(
                        new Category("runtime", LedgerStatus.RESOLVED, null, List.of(answerReference)),
                        previous.completeness().get(1)),
                List.of(),
                GapReviewStatus.NOT_RUN,
                policy);
    }

    private static DecisionLedger copyLedger(DecisionLedger source, List<Entry> decisions) {
        return new DecisionLedger(
                source.schemaVersion(),
                source.revision(),
                source.facts(),
                decisions,
                source.conflicts(),
                source.assumptions(),
                source.catalogEvidence(),
                source.openQuestions(),
                source.completeness(),
                source.blockingOpenItemIds(),
                source.gapReviewStatus(),
                source.requirementsPolicy());
    }

    private static void assertPolicyAnswerAccepted(
            String category, String value, String response, RequirementsPolicy policy) {
        SourceMaterial context = source(
                SourceKind.INITIAL_CONTEXT, "source-baseline-" + category, "Requirements baseline.");
        SourceRef contextReference = reference(context, context.canonicalLocator(), "Requirements baseline");
        Entry baseline = new Entry(
                "baseline-" + category,
                "business-purpose",
                "baseline",
                LedgerStatus.RESOLVED,
                List.of(contextReference),
                null);
        String itemId = "choice-" + category;
        String questionId = "question-" + category;
        Entry open = new Entry(
                itemId, category, null, LedgerStatus.NEEDS_USER_DECISION, List.of(), null);
        DecisionLedger previous = new DecisionLedger(
                DecisionLedger.SCHEMA_VERSION,
                1,
                List.of(baseline),
                List.of(open),
                List.of(),
                List.of(),
                List.of(),
                List.of(new Question(
                        questionId,
                        itemId,
                        "Choose " + category,
                        List.of(value),
                        value,
                        LedgerStatus.OPEN)),
                List.of(new Category(category, LedgerStatus.NEEDS_USER_DECISION, null, List.of())),
                List.of(itemId),
                GapReviewStatus.NOT_RUN,
                null);
        SourceMaterial answerSource = source(
                SourceKind.INTERACTION, "interaction-" + category, response);
        SourceRef answerReference = reference(
                answerSource, answerSource.canonicalLocator(), response);
        Entry resolved = new Entry(
                itemId, category, value, LedgerStatus.RESOLVED, List.of(answerReference), null);
        DecisionLedger proposed = new DecisionLedger(
                DecisionLedger.SCHEMA_VERSION,
                2,
                List.of(baseline),
                List.of(resolved),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(new Category(category, LedgerStatus.RESOLVED, null, List.of(answerReference))),
                List.of(),
                GapReviewStatus.NOT_RUN,
                policy);
        DiscoveryAnswer answer = discoveryAnswer(response, questionId, itemId);
        StageRequest request = request(ShipStage.DISCOVERY);

        assertDoesNotThrow(() -> LedgerProvenanceValidator.validate(
                request,
                result(request, proposed, request.attemptId()),
                List.of(context, answerSource),
                previous,
                new DiscoveryAnswerAuthorization(answer, answerSource)));
    }

    private static void assertRequirementsFeedbackAccepted(boolean design) throws Exception {
        String family = design ? "design" : "plan";
        SourceMaterial baseline = source(
                SourceKind.PROJECT,
                "project-requirements-" + family,
                "The approved runtime is Camel Main.");
        SourceRef baselineReference = reference(
                baseline, baseline.canonicalLocator(), "runtime is Camel Main");
        DecisionLedger previous = requirementsLedger(
                1,
                GapReviewStatus.PASSED,
                "main",
                List.of(baselineReference));
        String feedback = "Change the runtime to Quarkus.";
        SourceMaterial feedbackSource = requirementsFeedbackSource(family, feedback);
        SourceRef feedbackReference = reference(
                feedbackSource, feedbackSource.canonicalLocator(), feedback);
        DecisionLedger proposed = requirementsLedger(
                2,
                GapReviewStatus.NOT_RUN,
                "quarkus",
                List.of(baselineReference, feedbackReference));
        String previousDigest = canonicalLedgerDigest(previous);
        EvolutionAuthorization authorization = design
                ? RequirementsChangeAuthorization.design(
                        designRequirementsResponse(previousDigest, feedback), feedbackSource)
                : RequirementsChangeAuthorization.plan(
                        planRequirementsResponse(previousDigest, feedback), feedbackSource);
        StageRequest request = request(ShipStage.DISCOVERY);

        assertDoesNotThrow(() -> LedgerProvenanceValidator.validate(
                request,
                result(request, proposed, request.attemptId()),
                List.of(baseline, feedbackSource),
                previous,
                authorization));
    }

    private static DecisionLedger requirementsLedger(
            long revision,
            GapReviewStatus reviewStatus,
            String runtime,
            List<SourceRef> references) {
        Entry runtimeDecision = new Entry(
                "runtime-choice", "runtime", runtime, LedgerStatus.RESOLVED, references, null);
        RequirementsPolicy policy = new RequirementsPolicy(
                runtime,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                null,
                List.of(),
                List.of(),
                false,
                false);
        return new DecisionLedger(
                DecisionLedger.SCHEMA_VERSION,
                revision,
                List.of(),
                List.of(runtimeDecision),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(new Category("runtime", LedgerStatus.RESOLVED, null, references)),
                List.of(),
                reviewStatus,
                policy);
    }

    private static DesignResponse designRequirementsResponse(
            String ledgerDigest, String requestedChanges) {
        return designRequirementsResponse(
                RUN_ID, 1, ledgerDigest, ledgerDigest, requestedChanges);
    }

    private static DesignResponse designRequirementsResponse(
            String runId,
            long ledgerRevision,
            String ledgerDigest,
            String requirementsDigest,
            String requestedChanges) {
        return new DesignResponse(
                Interaction.SCHEMA_VERSION,
                runId,
                digest("context"),
                ledgerRevision,
                ledgerDigest,
                requirementsDigest,
                digest("policy"),
                digest("baseline"),
                digest("design"),
                CHALLENGE,
                DesignDecision.REQUEST_REQUIREMENTS_CHANGES,
                requestedChanges,
                "uid:1000",
                "terminal-v1",
                "cli",
                Instant.parse("2026-07-22T00:00:00Z"),
                "hmac-sha256:" + "5".repeat(64));
    }

    private static DecisionLedger withOpenRequirementsQuestion(DecisionLedger source) {
        Entry open = new Entry(
                "security-choice",
                "security",
                null,
                LedgerStatus.NEEDS_USER_DECISION,
                List.of(),
                null);
        return new DecisionLedger(
                source.schemaVersion(),
                source.revision(),
                source.facts(),
                List.of(source.decisions().get(0), open),
                source.conflicts(),
                source.assumptions(),
                source.catalogEvidence(),
                List.of(new Question(
                        "question-security",
                        open.id(),
                        "Which security policy applies?",
                        List.of("strict", "standard"),
                        "strict",
                        LedgerStatus.OPEN)),
                List.of(
                        source.completeness().get(0),
                        new Category("security", LedgerStatus.NEEDS_USER_DECISION, null, List.of())),
                List.of(open.id()),
                source.gapReviewStatus(),
                source.requirementsPolicy());
    }

    private static PlanResponse planRequirementsResponse(
            String ledgerDigest, String requestedChanges) {
        return new PlanResponse(
                Interaction.SCHEMA_VERSION,
                RUN_ID,
                digest("context"),
                1,
                ledgerDigest,
                ledgerDigest,
                digest("policy"),
                digest("baseline"),
                digest("design"),
                digest("plan"),
                CHALLENGE,
                PlanDecision.REQUEST_REQUIREMENTS_CHANGES,
                requestedChanges,
                "uid:1000",
                "terminal-v1",
                "cli",
                Instant.parse("2026-07-22T00:00:00Z"),
                "hmac-sha256:" + "6".repeat(64));
    }

    private static SourceMaterial requirementsFeedbackSource(String family, String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return new SourceMaterial(
                SourceKind.INTERACTION,
                "interaction-requirements-" + family,
                "controller:interaction-bundle#exchanges/1/" + family
                                                      + "/response/requestedChanges",
                ShipDigest.sha256(bytes),
                bytes);
    }

    private static String canonicalLedgerDigest(DecisionLedger ledger) throws Exception {
        return ShipDigest.sha256(ShipJson.mapper().writeValueAsBytes(ledger));
    }

    private static DiscoveryAnswer discoveryAnswer(String response) {
        return discoveryAnswer(response, "question-runtime", "runtime-choice");
    }

    private static DiscoveryAnswer discoveryAnswer(
            String response, String questionId, String openItemId) {
        return new DiscoveryAnswer(
                Interaction.SCHEMA_VERSION,
                RUN_ID,
                1,
                questionId,
                openItemId,
                CHALLENGE,
                response,
                "test-console",
                Instant.parse("2026-07-22T00:00:00Z"),
                "hmac-sha256:" + "4".repeat(64));
    }

    private static DecisionLedger ledger(
            long revision,
            GapReviewStatus reviewStatus,
            String category,
            String value,
            SourceRef reference) {
        Entry decision = new Entry(
                "decision-" + category,
                category,
                value,
                LedgerStatus.RESOLVED,
                List.of(reference),
                null);
        return new DecisionLedger(
                DecisionLedger.SCHEMA_VERSION,
                revision,
                List.of(),
                List.of(decision),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                reviewStatus,
                null);
    }

    private static SourceMaterial source(SourceKind kind, String id, String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        String locator = switch (kind) {
            case INITIAL_CONTEXT -> "context:" + id;
            case PROJECT -> "controller:project-source#" + id;
            case INTERACTION -> "controller:interaction-bundle#" + id;
        };
        return new SourceMaterial(kind, id, locator, ShipDigest.sha256(bytes), bytes);
    }

    private static SourceRef reference(SourceMaterial source, String locator, String excerpt) {
        return new SourceRef(source.sourceId(), locator, source.digest(), excerpt);
    }

    private static String digest(String value) {
        return ShipDigest.sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    private static void assertCode(String code, org.junit.jupiter.api.function.Executable executable) {
        ShipControllerException error = assertThrows(ShipControllerException.class, executable);
        assertEquals(code, error.code());
    }
}
