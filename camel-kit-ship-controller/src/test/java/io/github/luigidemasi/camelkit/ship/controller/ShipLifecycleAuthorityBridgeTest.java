package io.github.luigidemasi.camelkit.ship.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ShipLifecycleAuthorityBridgeTest {

    @Test
    void acceptedResultFactoryPreservesEveryExactAttemptBinding() {
        Attempt attempt = new Attempt(
                ShipRunId.create(),
                head('1'),
                7,
                ShipPhase.DESIGN,
                PhaseInput.initial(id("input")));
        AuthorityBasis basis = new AuthorityBasis(
                id("context"), id("ledger"), 3, id("requirements"), id("policy"), id("baseline"));
        Attempt reviewAttempt = new Attempt(
                attempt.runId(),
                attempt.authorityHead(),
                attempt.number(),
                ShipPhase.REVIEW,
                PhaseInput.initial(id("candidate")));

        assertCapability(AcceptedStageResultFactory.context(attempt, id("context-result")), attempt,
                id("context-result"));
        assertCapability(
                AcceptedStageResultFactory.discoveryCandidate(attempt, id("candidate")),
                attempt,
                id("candidate"));
        assertCapability(
                AcceptedStageResultFactory.discoveryContinuation(attempt, id("continuation")),
                attempt,
                id("continuation"));
        assertCapability(
                AcceptedStageResultFactory.review(
                        reviewAttempt, id("candidate"), id("feedback")),
                reviewAttempt,
                new GapReviewResult(id("candidate"), id("feedback")));
        assertCapability(
                AcceptedStageResultFactory.designGaps(
                        attempt, id("design-candidate"), id("design-feedback")),
                attempt,
                new DesignGapResult(id("design-candidate"), id("design-feedback")));
        assertCapability(AcceptedStageResultFactory.requirements(attempt, basis), attempt, basis);
        assertCapability(AcceptedStageResultFactory.design(attempt, id("design")), attempt, id("design"));
        assertCapability(AcceptedStageResultFactory.plan(attempt, id("plan")), attempt, id("plan"));
        assertCapability(AcceptedStageResultFactory.execution(attempt, id("execution")), attempt, id("execution"));
        assertCapability(AcceptedStageResultFactory.validation(attempt, id("validation")), attempt, id("validation"));
        assertCapability(AcceptedStageResultFactory.stamp(attempt, id("stamp")), attempt, id("stamp"));
    }

    @Test
    void persistedHeadRebindsAnActiveAttemptForRestart() {
        ShipRun reduced = ShipLifecycleReducer.startContextResolution(
                ShipLifecycleReducer.start(), id("context-input"));
        AuthorityHeadId persisted = head('2');

        ShipRun restarted = ShipLifecycleReducer.rebindAuthorityHead(reduced, persisted);

        assertStableIdentity(reduced, restarted, persisted);
        assertEquals(persisted, ShipLifecycleReducer.attempt(restarted).authorityHead());
        assertEquals(ShipLifecycleReducer.attempt(reduced).input(), ShipLifecycleReducer.attempt(restarted).input());
    }

    @Test
    void persistedHeadRebindsAPendingInteractionForRestart() {
        ShipRun resolving = ShipLifecycleReducer.startContextResolution(
                ShipLifecycleReducer.start(), id("context-input"));
        ShipRun reduced = ShipLifecycleReducer.requestDocumentConsent(
                resolving, ShipLifecycleReducer.attempt(resolving), id("document"));
        AuthorityHeadId persisted = head('3');

        ShipRun restarted = ShipLifecycleReducer.rebindAuthorityHead(reduced, persisted);

        assertStableIdentity(reduced, restarted, persisted);
        PendingInteraction pending = ShipLifecycleReducer.pending(restarted);
        assertEquals(persisted, pending.requestHead());
        assertEquals(restarted.revision(), pending.requestRevision());
        assertEquals(ShipLifecycleReducer.pending(reduced).binding(), pending.binding());
    }

    @Test
    void persistedHeadRebindsRetryAuthorityButKeepsTheFailedAttemptIdentity() {
        ShipRun running = ShipLifecycleReducer.startContextResolution(
                ShipLifecycleReducer.start(), id("context-input"));
        Attempt failedAttempt = ShipLifecycleReducer.attempt(running);
        ShipRun reduced = ShipLifecycleReducer.failRetryable(running, failedAttempt);
        AuthorityHeadId persisted = head('4');

        ShipRun restarted = ShipLifecycleReducer.rebindAuthorityHead(reduced, persisted);

        assertStableIdentity(reduced, restarted, persisted);
        RetryBinding retry = ShipLifecycleReducer.retryBinding(restarted);
        assertEquals(persisted, retry.failureHead());
        assertSame(failedAttempt, retry.failedAttempt());
        assertEquals(failedAttempt.authorityHead(), retry.failedAttempt().authorityHead());

        ShipRun retried = ShipLifecycleReducer.retry(restarted, retry);
        assertEquals(ShipState.CONTEXT_RESOLVING, retried.state());
        assertEquals(failedAttempt.input(), ShipLifecycleReducer.attempt(retried).input());
    }

    @Test
    void verifiedControllerInputsCannotSelectADifferentPendingInteraction() {
        ShipRun resolving = ShipLifecycleReducer.startContextResolution(
                ShipLifecycleReducer.start(), id("context-input"));
        ShipRun waiting = ShipLifecycleReducer.requestDocumentConsent(
                resolving, ShipLifecycleReducer.attempt(resolving), id("document"));

        ShipRun accepted = ShipLifecycleReducer.applyVerifiedDecision(
                waiting, ShipEventType.DOCUMENT_CONSENT_ACCEPTED, null);

        assertEquals(ShipState.CONTEXT_RESOLVING, accepted.state());
        assertEquals(
                new ConsentOutcomeBinding(
                        ShipInteractionKind.DOCUMENT_READ_CONSENT,
                        id("document"),
                        InteractionDecisionValue.ACCEPTED),
                ShipLifecycleReducer.attempt(accepted).input().bindings().get(1));
        assertThrows(
                IllegalArgumentException.class,
                () -> ShipLifecycleReducer.applyVerifiedDecision(
                        waiting, ShipEventType.DESIGN_APPROVED, null));
    }

    @Test
    void verifiedDiscoveryAnswerRemainsBoundToThePendingQuestion() {
        ShipRun resolving = ShipLifecycleReducer.startContextResolution(
                ShipLifecycleReducer.start(), id("context-input"));
        ShipRun recorded = ShipLifecycleReducer.recordContext(
                resolving,
                AcceptedStageResultFactory.context(
                        ShipLifecycleReducer.attempt(resolving), id("context")));
        ShipRun discovery = ShipLifecycleReducer.startDiscovery(recorded);
        ShipRun waiting = ShipLifecycleReducer.requestDiscoveryAnswer(
                discovery, ShipLifecycleReducer.attempt(discovery), id("question"));

        ShipRun answered = ShipLifecycleReducer.applyVerifiedDiscoveryAnswer(
                waiting, id("answer"));

        assertEquals(ShipState.DISCOVERY_ANALYZING, answered.state());
        assertEquals(
                new DiscoveryAnswerBinding(id("question"), id("answer")),
                ShipLifecycleReducer.attempt(answered).input().bindings().get(1));
    }

    @Test
    void verifiedApprovalOutcomesRemainTypedByApprovalFamily() {
        ShipRun waitingDesign = waitingDesign("bridge-design");
        assertEquals(
                ShipEventType.DESIGN_APPROVAL_DENIED,
                ShipLifecycleReducer.applyVerifiedDesignDecision(
                        waitingDesign,
                        ShipEventType.DESIGN_APPROVAL_DENIED,
                        id("design-feedback"))
                        .lastEvent());
        assertEquals(
                ShipState.DISCOVERY_ANALYZING,
                ShipLifecycleReducer.applyVerifiedDesignDecision(
                        waitingDesign,
                        ShipEventType.DESIGN_REQUIREMENTS_CHANGES_REQUESTED,
                        id("requirements-feedback"))
                        .state());
        assertEquals(
                ShipState.ABORTED,
                ShipLifecycleReducer.applyVerifiedDesignDecision(
                        waitingDesign,
                        ShipEventType.DESIGN_APPROVAL_ABORTED,
                        null)
                        .state());
        assertThrows(
                IllegalArgumentException.class,
                () -> ShipLifecycleReducer.applyVerifiedPlanDecision(
                        waitingDesign, ShipEventType.PLAN_APPROVED, null));

        ShipRun waitingPlan = waitingPlan("bridge-plan");
        assertEquals(
                ShipState.PLAN_RUNNING,
                ShipLifecycleReducer.applyVerifiedPlanDecision(
                        waitingPlan,
                        ShipEventType.PLAN_APPROVAL_DENIED,
                        id("plan-feedback"))
                        .state());
        assertEquals(
                ShipState.DESIGN_RUNNING,
                ShipLifecycleReducer.applyVerifiedPlanDecision(
                        waitingPlan,
                        ShipEventType.PLAN_DESIGN_CHANGES_REQUESTED,
                        id("design-feedback"))
                        .state());
        assertEquals(
                ShipState.DISCOVERY_ANALYZING,
                ShipLifecycleReducer.applyVerifiedPlanDecision(
                        waitingPlan,
                        ShipEventType.PLAN_REQUIREMENTS_CHANGES_REQUESTED,
                        id("requirements-feedback"))
                        .state());
        assertEquals(
                ShipState.ABORTED,
                ShipLifecycleReducer.applyVerifiedPlanDecision(
                        waitingPlan,
                        ShipEventType.PLAN_APPROVAL_ABORTED,
                        null)
                        .state());
    }

    private static void assertStableIdentity(
            ShipRun reduced, ShipRun restarted, AuthorityHeadId persisted) {
        assertSame(reduced.id(), restarted.id());
        assertNotEquals(reduced.head(), restarted.head());
        assertEquals(persisted, restarted.head());
        assertEquals(reduced.state(), restarted.state());
        assertEquals(reduced.revision(), restarted.revision());
        assertEquals(reduced.lastEvent(), restarted.lastEvent());
    }

    private static ShipRun waitingDesign(String suffix) {
        ShipRun resolving = ShipLifecycleReducer.startContextResolution(
                ShipLifecycleReducer.start(), id("input-" + suffix));
        ShipRun recorded = ShipLifecycleReducer.recordContext(
                resolving,
                AcceptedStageResultFactory.context(
                        ShipLifecycleReducer.attempt(resolving), id("context-" + suffix)));
        ShipRun discovery = ShipLifecycleReducer.startDiscovery(recorded);
        ShipRun review = ShipLifecycleReducer.startGapReview(
                discovery,
                AcceptedStageResultFactory.discoveryCandidate(
                        ShipLifecycleReducer.attempt(discovery), id("candidate-" + suffix)));
        AuthorityBasis basis = new AuthorityBasis(
                id("context-" + suffix),
                id("ledger-" + suffix),
                1,
                id("requirements-" + suffix),
                id("policy"),
                id("baseline"));
        ShipRun requirements = ShipLifecycleReducer.requirementsReady(
                review,
                AcceptedStageResultFactory.requirements(
                        ShipLifecycleReducer.attempt(review), basis));
        ShipRun design = ShipLifecycleReducer.startDesign(requirements);
        ShipRun ready = ShipLifecycleReducer.designReady(
                design,
                AcceptedStageResultFactory.design(
                        ShipLifecycleReducer.attempt(design), id("design-" + suffix)));
        return ShipLifecycleReducer.requestDesignApproval(ready);
    }

    private static ShipRun waitingPlan(String suffix) {
        ShipRun waitingDesign = waitingDesign(suffix);
        ShipRun approved = ShipLifecycleReducer.applyVerifiedDesignDecision(
                waitingDesign, ShipEventType.DESIGN_APPROVED, null);
        ShipRun plan = ShipLifecycleReducer.startPlan(approved);
        ShipRun validated = ShipLifecycleReducer.planValidated(
                plan,
                AcceptedStageResultFactory.plan(
                        ShipLifecycleReducer.attempt(plan), id("plan-" + suffix)));
        return ShipLifecycleReducer.requestPlanApproval(validated);
    }

    private static <T> void assertCapability(
            AcceptedStageResult<T> capability, Attempt attempt, T value) {
        assertSame(attempt, capability.attempt());
        assertEquals(value, capability.value());
    }

    private static AuthorityHeadId head(char digit) {
        return AuthorityHeadId.fromStorageId("head-" + String.valueOf(digit).repeat(32));
    }

    private static ContentId id(String value) {
        return new ContentId(value);
    }
}
