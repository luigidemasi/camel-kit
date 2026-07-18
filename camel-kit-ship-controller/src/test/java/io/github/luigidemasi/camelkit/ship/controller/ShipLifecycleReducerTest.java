package io.github.luigidemasi.camelkit.ship.controller;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShipLifecycleReducerTest {

    @Test
    void happyPathRequiresSeparateDesignAndPlanApprovals() {
        ShipRun designReady = designReady("happy");
        assertEquals(ShipState.DESIGN_READY, designReady.state());
        assertThrows(IllegalStateException.class,
                () -> ShipLifecycleReducer.startPlan(designReady));

        ShipRun waitingDesign = ShipLifecycleReducer.requestDesignApproval(designReady);
        assertEquals(ShipState.WAITING_FOR_DESIGN_APPROVAL, waitingDesign.state());
        assertEquals(
                ShipInteractionKind.DESIGN_APPROVAL,
                waitingDesign.state().pendingInteraction().orElseThrow());
        ShipRun designApproved = ShipLifecycleReducer.approveDesign(waitingDesign, accepted(waitingDesign));
        assertEquals(ShipState.DESIGN_APPROVED, designApproved.state());

        ShipRun planRunning = ShipLifecycleReducer.startPlan(designApproved);
        ShipRun planValidated = ShipLifecycleReducer.planValidated(
                planRunning, planResult(planRunning, id("plan-v1")));
        assertEquals(ShipState.PLAN_VALIDATED, planValidated.state());
        assertThrows(IllegalStateException.class,
                () -> ShipLifecycleReducer.startExecution(planValidated));

        ShipRun waitingPlan = ShipLifecycleReducer.requestPlanApproval(planValidated);
        assertEquals(ShipState.WAITING_FOR_PLAN_APPROVAL, waitingPlan.state());
        ShipRun rejectedPlan = ShipLifecycleReducer.denyPlanApproval(waitingPlan, denied(waitingPlan));
        assertEquals(
                java.util.List.of(
                        new ContentBinding(id("design-happy")),
                        new ApprovalFeedbackBinding(
                                ShipInteractionKind.PLAN_APPROVAL,
                                id("plan-v1"),
                                id("feedback-plan-approval"))),
                ShipLifecycleReducer.attempt(rejectedPlan).input().bindings());
        ShipRun planApproved = ShipLifecycleReducer.approvePlan(waitingPlan, accepted(waitingPlan));
        assertEquals(ShipState.PLAN_APPROVED, planApproved.state());

        ShipRun execute = ShipLifecycleReducer.startExecution(planApproved);
        ShipRun executeValidated = ShipLifecycleReducer.executionValidated(
                execute, executionResult(execute, id("execution-result")));
        ShipRun validate = ShipLifecycleReducer.startValidation(executeValidated);
        ShipRun validatePassed = ShipLifecycleReducer.validationPassed(
                validate, validationResult(validate, id("validation-evidence")));
        ShipRun stamp = ShipLifecycleReducer.startStamp(validatePassed);
        ShipRun completed = ShipLifecycleReducer.completeStamp(
                stamp, stampCompletion(stamp, id("stamp-evidence")));

        assertEquals(ShipState.COMPLETED, completed.state());
        assertEquals(ShipEventType.RUN_COMPLETED, completed.lastEvent());
        assertTrue(completed.terminal());
        assertSame(designReady.id(), completed.id());
        assertTrue(completed.revision() > designReady.revision());
    }

    @Test
    void pendingInteractionsRequireTheExactRunRevisionKindAndBinding() {
        ShipRun context
                = ShipLifecycleReducer.startContextResolution(ShipLifecycleReducer.start(), id("context-input"));
        Attempt contextAttempt = ShipLifecycleReducer.attempt(context);
        ShipRun waiting
                = ShipLifecycleReducer.requestDocumentConsent(context, contextAttempt, id("external-document"));
        PendingInteraction exact = ShipLifecycleReducer.pending(waiting);

        assertThrows(IllegalStateException.class,
                () -> ShipLifecycleReducer.requestDocumentConsent(
                        waiting, contextAttempt, id("second-document")));
        assertThrows(IllegalStateException.class,
                () -> ShipLifecycleReducer.acceptDocumentConsent(waiting, denied(waiting)));
        assertThrows(IllegalStateException.class,
                () -> ShipLifecycleReducer.denyDocumentConsent(waiting, accepted(waiting)));
        assertThrows(IllegalStateException.class,
                () -> ShipLifecycleReducer.acceptDocumentConsent(
                        waiting,
                        accepted(new PendingInteraction(
                                ShipLifecycleReducer.start().id(),
                                exact.requestRevision(),
                                exact.requestHead(),
                                exact.kind(),
                                exact.binding()))));
        assertThrows(IllegalStateException.class,
                () -> ShipLifecycleReducer.acceptDocumentConsent(
                        waiting,
                        accepted(new PendingInteraction(
                                exact.runId(),
                                exact.requestRevision() + 1,
                                exact.requestHead(),
                                exact.kind(),
                                exact.binding()))));
        assertThrows(IllegalStateException.class,
                () -> ShipLifecycleReducer.acceptDocumentConsent(
                        waiting,
                        accepted(new PendingInteraction(
                                exact.runId(),
                                exact.requestRevision(),
                                exact.requestHead(),
                                ShipInteractionKind.REMOTE_USE_CONSENT,
                                exact.binding()))));
        assertThrows(IllegalStateException.class,
                () -> ShipLifecycleReducer.acceptDocumentConsent(
                        waiting,
                        accepted(new PendingInteraction(
                                exact.runId(),
                                exact.requestRevision(),
                                exact.requestHead(),
                                exact.kind(),
                                new ContentDecision(
                                        id("other-document"),
                                        ((ContentDecision) exact.binding()).resumeInput())))));

        ShipRun resumed = ShipLifecycleReducer.acceptDocumentConsent(waiting, accepted(exact));
        assertEquals(ShipState.CONTEXT_RESOLVING, resumed.state());
        assertTrue(ShipLifecycleReducer.attempt(resumed).number() > contextAttempt.number());
        assertEquals(
                java.util.List.of(
                        new ContentBinding(id("context-input")),
                        new ConsentOutcomeBinding(
                                ShipInteractionKind.DOCUMENT_READ_CONSENT,
                                id("external-document"),
                                InteractionDecisionValue.ACCEPTED)),
                ShipLifecycleReducer.attempt(resumed).input().bindings());
        assertThrows(IllegalStateException.class,
                () -> ShipLifecycleReducer.acceptDocumentConsent(resumed, accepted(exact)));

        ShipRun recorded = ShipLifecycleReducer.recordContext(
                resumed, contextResult(resumed, id("context")));
        ShipRun discovery = ShipLifecycleReducer.startDiscovery(recorded);
        Attempt firstDiscoveryAttempt = ShipLifecycleReducer.attempt(discovery);
        ShipRun waitingRemote = ShipLifecycleReducer.requestRemoteUseConsent(
                discovery, firstDiscoveryAttempt, id("remote-request"));
        ShipRun afterRemoteAcceptance
                = ShipLifecycleReducer.acceptRemoteUseConsent(waitingRemote, accepted(waitingRemote));
        ShipRun afterRemoteDenial = ShipLifecycleReducer.denyRemoteUseConsent(waitingRemote, denied(waitingRemote));
        assertNotEquals(
                ShipLifecycleReducer.attempt(afterRemoteAcceptance).input(),
                ShipLifecycleReducer.attempt(afterRemoteDenial).input());
        assertEquals(ShipState.DISCOVERY_ANALYZING, afterRemoteDenial.state());
        Attempt afterRemoteAttempt = ShipLifecycleReducer.attempt(afterRemoteDenial);
        ShipRun waitingAnswer = ShipLifecycleReducer.requestDiscoveryAnswer(
                afterRemoteDenial, afterRemoteAttempt, id("question-1"));
        ShipRun afterAnswer = ShipLifecycleReducer.recordDiscoveryAnswer(
                waitingAnswer, answer(ShipLifecycleReducer.pending(waitingAnswer), id("answer-1")));
        assertEquals(ShipState.DISCOVERY_ANALYZING, afterAnswer.state());
        Attempt afterAnswerAttempt = ShipLifecycleReducer.attempt(afterAnswer);
        assertTrue(afterAnswerAttempt.number() > afterRemoteAttempt.number());
        assertNotEquals(firstDiscoveryAttempt, afterAnswerAttempt);
        assertEquals(
                java.util.List.of(
                        new ContentBinding(id("context")),
                        new ConsentOutcomeBinding(
                                ShipInteractionKind.REMOTE_USE_CONSENT,
                                id("remote-request"),
                                InteractionDecisionValue.DENIED),
                        new DiscoveryAnswerBinding(id("question-1"), id("answer-1"))),
                afterAnswerAttempt.input().bindings());
    }

    @Test
    void interactionsReserveCapacityForEveryLegalResponseBeforeWaiting() {
        ShipRun context = ShipLifecycleReducer.startContextResolution(
                ShipLifecycleReducer.start(), id("capacity-context-input"));
        ShipRun recorded = ShipLifecycleReducer.recordContext(
                context, contextResult(context, id("capacity-context")));
        ShipRun discovery = ShipLifecycleReducer.startDiscovery(recorded);
        Attempt attempt = ShipLifecycleReducer.attempt(discovery);

        PhaseInput nearLimit = new PhaseInput(
                java.util.stream.IntStream.range(0, 255)
                        .mapToObj(index -> new ContentBinding(id("binding-" + index)))
                        .map(PhaseBinding.class::cast)
                        .toList());
        AuthorityCopy nearLimitAuthority = new AuthorityCopy(discovery.authority());
        nearLimitAuthority.attempt = new Attempt(
                attempt.runId(),
                attempt.authorityHead(),
                attempt.number(),
                attempt.phase(),
                nearLimit);
        ShipRun nearLimitRun = reconstruct(discovery, nearLimitAuthority);

        ShipRun waitingQuestion = ShipLifecycleReducer.requestDiscoveryAnswer(
                nearLimitRun,
                ShipLifecycleReducer.attempt(nearLimitRun),
                id("last-question"));
        ShipRun answered = ShipLifecycleReducer.recordDiscoveryAnswer(
                waitingQuestion,
                answer(ShipLifecycleReducer.pending(waitingQuestion), id("last-answer")));
        assertEquals(
                new DiscoveryAnswerBinding(id("last-question"), id("last-answer")),
                ShipLifecycleReducer.attempt(answered).input().bindings().get(255));
        ShipRun waiting = ShipLifecycleReducer.requestRemoteUseConsent(
                nearLimitRun,
                ShipLifecycleReducer.attempt(nearLimitRun),
                id("last-consent"));
        assertEquals(
                256,
                ShipLifecycleReducer.attempt(
                        ShipLifecycleReducer.acceptRemoteUseConsent(waiting, accepted(waiting)))
                        .input()
                        .bindings()
                        .size());
        assertEquals(
                256,
                ShipLifecycleReducer.attempt(
                        ShipLifecycleReducer.denyRemoteUseConsent(waiting, denied(waiting)))
                        .input()
                        .bindings()
                        .size());

        java.util.ArrayList<PhaseBinding> fullBindings
                = new java.util.ArrayList<>(nearLimit.bindings());
        fullBindings.add(new ContentBinding(id("final-binding")));
        PhaseInput full = new PhaseInput(fullBindings);
        PendingInteraction pending = ShipLifecycleReducer.pending(waiting);
        ContentDecision decision = (ContentDecision) pending.binding();
        AuthorityCopy unresolvableWaiting = new AuthorityCopy(waiting.authority());
        unresolvableWaiting.pending = new PendingInteraction(
                pending.runId(),
                pending.requestRevision(),
                pending.requestHead(),
                pending.kind(),
                new ContentDecision(decision.subject(), full));
        assertMalformedSnapshot(waiting, unresolvableWaiting);

        AuthorityCopy fullAuthority = new AuthorityCopy(discovery.authority());
        fullAuthority.attempt = new Attempt(
                attempt.runId(),
                attempt.authorityHead(),
                attempt.number(),
                attempt.phase(),
                full);
        ShipRun fullRun = reconstruct(discovery, fullAuthority);
        assertThrows(IllegalStateException.class,
                () -> ShipLifecycleReducer.requestRemoteUseConsent(
                        fullRun, ShipLifecycleReducer.attempt(fullRun), id("unresolvable-consent")));
        assertThrows(IllegalStateException.class,
                () -> ShipLifecycleReducer.requestDiscoveryAnswer(
                        fullRun, ShipLifecycleReducer.attempt(fullRun), id("unresolvable-question")));
    }

    @Test
    void staleOrMismatchedApprovalsCannotAdvance() {
        ShipRun ready = designReady("bindings");
        ShipRun waiting = ShipLifecycleReducer.requestDesignApproval(ready);
        PendingInteraction exact = ShipLifecycleReducer.pending(waiting);
        PendingInteraction wrongDesign = new PendingInteraction(
                exact.runId(),
                exact.requestRevision(),
                exact.requestHead(),
                exact.kind(),
                new DesignDecisionBinding(basis("bindings"), id("other-design")));
        assertThrows(IllegalStateException.class,
                () -> ShipLifecycleReducer.approveDesign(waiting, accepted(wrongDesign)));

        ShipRun rejected = ShipLifecycleReducer.denyDesignApproval(waiting, denied(waiting));
        assertEquals(
                java.util.List.of(
                        new ContentBinding(id("requirements-bindings")),
                        new ApprovalFeedbackBinding(
                                ShipInteractionKind.DESIGN_APPROVAL,
                                id("design-bindings"),
                                id("feedback-design-approval"))),
                ShipLifecycleReducer.attempt(rejected).input().bindings());

        ShipRun approved = ShipLifecycleReducer.approveDesign(waiting, accepted(exact));
        ShipRun changed = ShipLifecycleReducer.designInputsChanged(approved);
        ShipRun newReady = ShipLifecycleReducer.designReady(
                changed, designResult(changed, id("new-design")));
        ShipRun newWaiting = ShipLifecycleReducer.requestDesignApproval(newReady);
        assertThrows(IllegalStateException.class,
                () -> ShipLifecycleReducer.approveDesign(newWaiting, accepted(exact)));
        assertEquals(ShipState.WAITING_FOR_DESIGN_APPROVAL, newWaiting.state());
    }

    @Test
    void symmetricForksCannotShareAttemptOrApprovalAuthority() {
        ShipRun context = ShipLifecycleReducer.startContextResolution(
                ShipLifecycleReducer.start(), id("fork-context-input"));
        ShipRun recorded = ShipLifecycleReducer.recordContext(
                context, contextResult(context, id("fork-context")));
        ShipRun discovery = ShipLifecycleReducer.startDiscovery(recorded);
        Attempt discoveryAttempt = ShipLifecycleReducer.attempt(discovery);

        AuthorityBasis basisA = new AuthorityBasis(
                id("fork-context"),
                id("fork-ledger"),
                7,
                id("shared-requirements"),
                id("policy-a"),
                id("baseline-a"));
        AuthorityBasis basisB = new AuthorityBasis(
                id("fork-context"),
                id("fork-ledger"),
                7,
                id("shared-requirements"),
                id("policy-b"),
                id("baseline-b"));
        ShipRun requirementsA = ShipLifecycleReducer.requirementsReady(
                discovery, requirementsResult(discoveryAttempt, basisA));
        ShipRun requirementsB = ShipLifecycleReducer.requirementsReady(
                discovery, requirementsResult(discoveryAttempt, basisB));
        ShipRun designA = ShipLifecycleReducer.startDesign(requirementsA);
        ShipRun designB = ShipLifecycleReducer.startDesign(requirementsB);
        Attempt attemptA = ShipLifecycleReducer.attempt(designA);
        Attempt attemptB = ShipLifecycleReducer.attempt(designB);

        assertEquals(attemptA.number(), attemptB.number());
        assertEquals(attemptA.input(), attemptB.input());
        assertNotEquals(attemptA.authorityHead(), attemptB.authorityHead());
        assertNotEquals(attemptA, attemptB);
        assertThrows(IllegalStateException.class,
                () -> ShipLifecycleReducer.designReady(
                        designB, designResult(attemptA, id("shared-design"))));

        ShipRun readyA = ShipLifecycleReducer.designReady(
                designA, designResult(attemptA, id("shared-design")));
        ShipRun readyB = ShipLifecycleReducer.designReady(
                designB, designResult(attemptB, id("shared-design")));
        ShipRun waitingA = ShipLifecycleReducer.requestDesignApproval(readyA);
        ShipRun waitingB = ShipLifecycleReducer.requestDesignApproval(readyB);
        PendingInteraction pendingA = ShipLifecycleReducer.pending(waitingA);
        PendingInteraction pendingB = ShipLifecycleReducer.pending(waitingB);

        assertEquals(waitingA.revision(), waitingB.revision());
        assertNotEquals(waitingA.head(), waitingB.head());
        assertNotEquals(pendingA, pendingB);
        assertThrows(IllegalStateException.class,
                () -> ShipLifecycleReducer.approveDesign(waitingB, accepted(pendingA)));
        assertEquals(
                ShipState.DESIGN_APPROVED,
                ShipLifecycleReducer.approveDesign(waitingB, accepted(pendingB)).state());
    }

    @Test
    void reconstructedSnapshotsRejectMalformedAuthorityBindings() {
        ShipRun waiting = ShipLifecycleReducer.requestDesignApproval(designReady("rehydrate-pending"));
        PendingInteraction pending = ShipLifecycleReducer.pending(waiting);
        ShipRunId otherRun = ShipLifecycleReducer.start().id();
        for (PendingInteraction malformed : java.util.List.of(
                new PendingInteraction(
                        otherRun,
                        pending.requestRevision(),
                        pending.requestHead(),
                        pending.kind(),
                        pending.binding()),
                new PendingInteraction(
                        pending.runId(),
                        pending.requestRevision() + 1,
                        pending.requestHead(),
                        pending.kind(),
                        pending.binding()),
                new PendingInteraction(
                        pending.runId(),
                        pending.requestRevision(),
                        AuthorityHeadId.create(),
                        pending.kind(),
                        pending.binding()))) {
            AuthorityCopy copy = new AuthorityCopy(waiting.authority());
            copy.pending = malformed;
            assertMalformedSnapshot(waiting, copy);
        }

        ShipRun resolving = ShipLifecycleReducer.startContextResolution(
                ShipLifecycleReducer.start(), id("rehydrate-attempt"));
        Attempt attempt = ShipLifecycleReducer.attempt(resolving);
        for (Attempt malformed : java.util.List.of(
                new Attempt(
                        otherRun,
                        attempt.authorityHead(),
                        attempt.number(),
                        attempt.phase(),
                        attempt.input()),
                new Attempt(
                        attempt.runId(),
                        AuthorityHeadId.create(),
                        attempt.number(),
                        attempt.phase(),
                        attempt.input()),
                new Attempt(
                        attempt.runId(),
                        attempt.authorityHead(),
                        attempt.number() + 1,
                        attempt.phase(),
                        attempt.input()))) {
            AuthorityCopy copy = new AuthorityCopy(resolving.authority());
            copy.attempt = malformed;
            assertMalformedSnapshot(resolving, copy);
        }

        ShipRun failed = ShipLifecycleReducer.failRetryable(resolving, attempt);
        RetryBinding retry = ShipLifecycleReducer.retryBinding(failed);
        Attempt otherRunAttempt = new Attempt(
                otherRun,
                retry.failedAttempt().authorityHead(),
                retry.failedAttempt().number(),
                retry.failedAttempt().phase(),
                retry.failedAttempt().input());
        for (RetryBinding malformed : java.util.List.of(
                new RetryBinding(
                        otherRunAttempt,
                        retry.failureRevision(),
                        retry.failureHead(),
                        retry.resumeState()),
                new RetryBinding(
                        retry.failedAttempt(),
                        retry.failureRevision() + 1,
                        retry.failureHead(),
                        retry.resumeState()),
                new RetryBinding(
                        retry.failedAttempt(),
                        retry.failureRevision(),
                        AuthorityHeadId.create(),
                        retry.resumeState()))) {
            AuthorityCopy copy = new AuthorityCopy(failed.authority());
            copy.retry = malformed;
            assertMalformedSnapshot(failed, copy);
        }

        ShipRun approved = planApproved("rehydrate-approvals");
        AuthorityCopy missingDesignApproval = new AuthorityCopy(approved.authority());
        missingDesignApproval.designApproval = null;
        assertMalformedSnapshot(approved, missingDesignApproval);
        AuthorityCopy missingPlanApproval = new AuthorityCopy(approved.authority());
        missingPlanApproval.planApproval = null;
        assertMalformedSnapshot(approved, missingPlanApproval);

        ShipRun completed = completeNormally("rehydrate-normal");
        AuthorityCopy missingValidation = new AuthorityCopy(completed.authority());
        missingValidation.validation = null;
        assertMalformedSnapshot(completed, missingValidation);
        AuthorityCopy missingNormalStamp = new AuthorityCopy(completed.authority());
        missingNormalStamp.stampCompletion = null;
        assertMalformedSnapshot(completed, missingNormalStamp);
        AuthorityCopy wrongNormalStampKind = new AuthorityCopy(completed.authority());
        wrongNormalStampKind.stampCompletion
                = new StampCompletionBinding(ShipPhase.WAIVER_STAMP, id("wrong-normal-kind"));
        assertMalformedSnapshot(completed, wrongNormalStampKind);

        ShipRun waived = completeWithWaiver("rehydrate-waiver");
        AuthorityCopy mismatchedWaiver = new AuthorityCopy(waived.authority());
        mismatchedWaiver.recordedWaiver
                = new WaiverBinding(id("other-check"), id("other-evidence"), id("policy"));
        assertMalformedSnapshot(waived, mismatchedWaiver);
        AuthorityCopy missingWaiverStamp = new AuthorityCopy(waived.authority());
        missingWaiverStamp.stampCompletion = null;
        assertMalformedSnapshot(waived, missingWaiverStamp);
        AuthorityCopy wrongWaiverStampKind = new AuthorityCopy(waived.authority());
        wrongWaiverStampKind.stampCompletion
                = new StampCompletionBinding(ShipPhase.STAMP, id("wrong-waiver-kind"));
        assertMalformedSnapshot(waived, wrongWaiverStampKind);
    }

    @Test
    void inputChangesInvalidateOnlyAuthorityThatDependsOnThem() {
        ShipRun approved = planApproved("invalidate");
        DesignApproval designApproval = approved.authority().designApproval();

        ShipRun planChanged = ShipLifecycleReducer.planInputsChanged(approved);
        assertEquals(ShipState.PLAN_RUNNING, planChanged.state());
        assertEquals(designApproval, planChanged.authority().designApproval());
        assertNull(planChanged.authority().planApproval());
        assertThrows(IllegalStateException.class,
                () -> ShipLifecycleReducer.startExecution(planChanged));

        ShipRun designChanged = ShipLifecycleReducer.designInputsChanged(approved);
        assertEquals(ShipState.DESIGN_RUNNING, designChanged.state());
        assertNull(designChanged.authority().designApproval());
        assertNull(designChanged.authority().planApproval());

        ShipRun requirementsChanged = ShipLifecycleReducer.requirementsInputsChanged(approved);
        assertEquals(ShipState.DISCOVERY_ANALYZING, requirementsChanged.state());
        assertEquals(approved.authority().context(), requirementsChanged.authority().context());
        assertEquals(
                PhaseInput.initial(approved.authority().context()),
                ShipLifecycleReducer.attempt(requirementsChanged).input());
        assertNull(requirementsChanged.authority().basis());
        assertNull(requirementsChanged.authority().designApproval());
        assertNull(requirementsChanged.authority().planApproval());

        ShipRun executing = ShipLifecycleReducer.startExecution(approved);
        Attempt staleAttempt = ShipLifecycleReducer.attempt(executing);
        ShipRun invalidated = ShipLifecycleReducer.requirementsInputsChanged(executing);
        assertThrows(IllegalStateException.class,
                () -> ShipLifecycleReducer.executionValidated(
                        invalidated, executionResult(staleAttempt, id("stale-result"))));
    }

    @Test
    void retryIsBoundToTheFailedPhaseAndAllocatesAFreshAttempt() {
        ShipRun executing = ShipLifecycleReducer.startExecution(planApproved("retry"));
        Attempt failedAttempt = ShipLifecycleReducer.attempt(executing);
        ShipRun failed = ShipLifecycleReducer.failRetryable(executing, failedAttempt);
        assertEquals(ShipState.EXECUTE_FAILED_RETRYABLE, failed.state());
        RetryBinding exact = ShipLifecycleReducer.retryBinding(failed);
        RetryBinding wrongRevision = new RetryBinding(
                exact.failedAttempt(),
                exact.failureRevision() + 1,
                exact.failureHead(),
                exact.resumeState());
        assertThrows(IllegalStateException.class, () -> ShipLifecycleReducer.retry(failed, wrongRevision));

        ShipRun retried = ShipLifecycleReducer.retry(failed, exact);
        Attempt freshAttempt = ShipLifecycleReducer.attempt(retried);
        assertEquals(ShipState.EXECUTE_RUNNING, retried.state());
        assertEquals(failedAttempt.phase(), freshAttempt.phase());
        assertEquals(failedAttempt.input(), freshAttempt.input());
        assertTrue(freshAttempt.number() > failedAttempt.number());
        assertNotEquals(failedAttempt, freshAttempt);
        assertThrows(IllegalStateException.class,
                () -> ShipLifecycleReducer.executionValidated(
                        retried, executionResult(failedAttempt, id("stale-result"))));
        assertThrows(IllegalStateException.class, () -> ShipLifecycleReducer.retryBinding(retried));

        ShipRun terminal = ShipLifecycleReducer.failTerminal(failed);
        assertEquals(ShipState.FAILED_TERMINAL, terminal.state());
        assertThrows(IllegalStateException.class, () -> ShipLifecycleReducer.retry(terminal, exact));
    }

    @Test
    void waiverRequiresEligibilityExactApprovalAndSeparateStamping() {
        ShipRun validating = validating("waiver");
        WaiverBinding waiver = new WaiverBinding(id("check-citrus"), id("evidence"), id("policy"));
        Attempt validationAttempt = ShipLifecycleReducer.attempt(validating);
        assertThrows(IllegalStateException.class,
                () -> ShipLifecycleReducer.recordWaivableFailure(
                        validating,
                        validationAttempt,
                        eligibility(
                                validating,
                                validationAttempt,
                                new WaiverBinding(
                                        id("check-citrus"), id("evidence"), id("other-policy")))));
        ShipRun eligible = ShipLifecycleReducer.recordWaivableFailure(
                validating, validationAttempt, eligibility(validating, validationAttempt, waiver));
        assertEquals(ShipState.WAIVER_ELIGIBLE, eligible.state());
        assertThrows(IllegalStateException.class,
                () -> ShipLifecycleReducer.completeWaiverStamp(
                        eligible,
                        stampCompletion(validationAttempt, id("premature-stamp-evidence"))));

        ShipRun waiting = ShipLifecycleReducer.requestWaiver(eligible);
        PendingInteraction exact = ShipLifecycleReducer.pending(waiting);
        PendingInteraction wrongEvidence = new PendingInteraction(
                exact.runId(),
                exact.requestRevision(),
                exact.requestHead(),
                exact.kind(),
                new WaiverBinding(id("check-citrus"), id("other-evidence"), id("policy")));
        assertThrows(IllegalStateException.class,
                () -> ShipLifecycleReducer.recordWaiver(waiting, accepted(wrongEvidence)));

        ShipRun recorded = ShipLifecycleReducer.recordWaiver(waiting, accepted(exact));
        assertEquals(ShipState.WAIVER_RECORDED, recorded.state());
        assertThrows(IllegalStateException.class,
                () -> ShipLifecycleReducer.completeWaiverStamp(
                        recorded,
                        stampCompletion(validationAttempt, id("stale-stamp-evidence"))));
        ShipRun stamp = ShipLifecycleReducer.startWaiverStamp(recorded);
        ShipRun completed = ShipLifecycleReducer.completeWaiverStamp(
                stamp, stampCompletion(stamp, id("waiver-stamp-evidence")));
        assertEquals(ShipState.COMPLETED_WITH_WAIVER, completed.state());
        assertTrue(completed.terminal());

        ShipRun mandatoryFailure = ShipLifecycleReducer.failTerminal(validating("mandatory"));
        assertEquals(ShipState.FAILED_TERMINAL, mandatoryFailure.state());
        assertThrows(IllegalStateException.class, () -> ShipLifecycleReducer.requestWaiver(mandatoryFailure));
    }

    @Test
    void terminalRunsAreAbsorbingAndOpaqueValuesRejectMalformedInput() {
        ShipRun normal = completeNormally("terminal-normal");
        ShipRun waived = completeWithWaiver("terminal-waived");
        ShipRun failed = ShipLifecycleReducer.failTerminal(ShipLifecycleReducer.start());
        ShipRun aborted = ShipLifecycleReducer.abort(ShipLifecycleReducer.start());
        for (ShipRun terminal : new ShipRun[]{normal, waived, failed, aborted}) {
            assertTrue(terminal.terminal());
            assertThrows(IllegalStateException.class, () -> ShipLifecycleReducer.abort(terminal));
            assertThrows(IllegalStateException.class, () -> ShipLifecycleReducer.failTerminal(terminal));
        }

        assertThrows(IllegalArgumentException.class, () -> new ContentId(null));
        assertThrows(IllegalArgumentException.class, () -> new ContentId(" "));
        assertThrows(IllegalArgumentException.class, () -> new ContentId("x".repeat(513)));
        assertThrows(NullPointerException.class,
                () -> ShipLifecycleReducer.startContextResolution(null, id("input")));
    }

    private static ShipRun completeNormally(String suffix) {
        ShipRun validating = validating(suffix);
        ShipRun passed = ShipLifecycleReducer.validationPassed(
                validating, validationResult(validating, id("validation-" + suffix)));
        ShipRun stamp = ShipLifecycleReducer.startStamp(passed);
        return ShipLifecycleReducer.completeStamp(
                stamp, stampCompletion(stamp, id("stamp-" + suffix)));
    }

    private static ShipRun completeWithWaiver(String suffix) {
        ShipRun validating = validating(suffix);
        WaiverBinding waiver
                = new WaiverBinding(id("check-" + suffix), id("evidence-" + suffix), id("policy"));
        Attempt attempt = ShipLifecycleReducer.attempt(validating);
        ShipRun eligible = ShipLifecycleReducer.recordWaivableFailure(
                validating, attempt, eligibility(validating, attempt, waiver));
        ShipRun waiting = ShipLifecycleReducer.requestWaiver(eligible);
        ShipRun recorded = ShipLifecycleReducer.recordWaiver(waiting, accepted(waiting));
        ShipRun stamp = ShipLifecycleReducer.startWaiverStamp(recorded);
        return ShipLifecycleReducer.completeWaiverStamp(
                stamp, stampCompletion(stamp, id("waiver-stamp-" + suffix)));
    }

    private static ShipRun validating(String suffix) {
        ShipRun execute = ShipLifecycleReducer.startExecution(planApproved(suffix));
        ShipRun executeValidated = ShipLifecycleReducer.executionValidated(
                execute, executionResult(execute, id("execution-" + suffix)));
        return ShipLifecycleReducer.startValidation(executeValidated);
    }

    private static ShipRun planApproved(String suffix) {
        ShipRun ready = designReady(suffix);
        ShipRun waitingDesign = ShipLifecycleReducer.requestDesignApproval(ready);
        ShipRun designApproved = ShipLifecycleReducer.approveDesign(waitingDesign, accepted(waitingDesign));
        ShipRun plan = ShipLifecycleReducer.startPlan(designApproved);
        ShipRun validated = ShipLifecycleReducer.planValidated(
                plan, planResult(plan, id("plan-" + suffix)));
        ShipRun waitingPlan = ShipLifecycleReducer.requestPlanApproval(validated);
        return ShipLifecycleReducer.approvePlan(waitingPlan, accepted(waitingPlan));
    }

    private static ShipRun designReady(String suffix) {
        ShipRun context
                = ShipLifecycleReducer.startContextResolution(ShipLifecycleReducer.start(),
                        id("context-input-" + suffix));
        ShipRun recorded = ShipLifecycleReducer.recordContext(
                context, contextResult(context, id("context-" + suffix)));
        ShipRun discovery = ShipLifecycleReducer.startDiscovery(recorded);
        ShipRun requirements = ShipLifecycleReducer.requirementsReady(
                discovery, requirementsResult(discovery, basis(suffix)));
        ShipRun design = ShipLifecycleReducer.startDesign(requirements);
        return ShipLifecycleReducer.designReady(
                design, designResult(design, id("design-" + suffix)));
    }

    private static AuthorityBasis basis(String suffix) {
        return new AuthorityBasis(
                id("context-" + suffix),
                id("ledger-" + suffix),
                1,
                id("requirements-" + suffix),
                id("policy"),
                id("baseline"));
    }

    private static ContentId id(String value) {
        return new ContentId(value);
    }

    private static InteractionDecision accepted(ShipRun run) {
        return accepted(ShipLifecycleReducer.pending(run));
    }

    private static InteractionDecision accepted(PendingInteraction request) {
        return construct(
                InteractionDecision.class,
                new Class<?>[]{
                        PendingInteraction.class, InteractionDecisionValue.class, ContentId.class
                },
                request,
                InteractionDecisionValue.ACCEPTED,
                null);
    }

    private static InteractionDecision denied(ShipRun run) {
        PendingInteraction request = ShipLifecycleReducer.pending(run);
        return construct(
                InteractionDecision.class,
                new Class<?>[]{
                        PendingInteraction.class, InteractionDecisionValue.class, ContentId.class
                },
                request,
                InteractionDecisionValue.DENIED,
                denialFeedback(request));
    }

    private static ContentId denialFeedback(PendingInteraction request) {
        return switch (request.kind()) {
            case DESIGN_APPROVAL -> id("feedback-design-approval");
            case PLAN_APPROVAL -> id("feedback-plan-approval");
            default -> null;
        };
    }

    private static DiscoveryAnswer answer(PendingInteraction request, ContentId answer) {
        return construct(
                DiscoveryAnswer.class,
                new Class<?>[]{PendingInteraction.class, ContentId.class},
                request,
                answer);
    }

    private static AcceptedContextResult contextResult(ShipRun run, ContentId context) {
        return acceptedStageResult(
                AcceptedContextResult.class,
                ShipLifecycleReducer.attempt(run),
                ContentId.class,
                context);
    }

    private static AcceptedRequirementsResult requirementsResult(
            ShipRun run, AuthorityBasis basis) {
        return requirementsResult(ShipLifecycleReducer.attempt(run), basis);
    }

    private static AcceptedRequirementsResult requirementsResult(
            Attempt attempt, AuthorityBasis basis) {
        return acceptedStageResult(
                AcceptedRequirementsResult.class, attempt, AuthorityBasis.class, basis);
    }

    private static AcceptedDesignResult designResult(ShipRun run, ContentId design) {
        return designResult(ShipLifecycleReducer.attempt(run), design);
    }

    private static AcceptedDesignResult designResult(Attempt attempt, ContentId design) {
        return acceptedStageResult(
                AcceptedDesignResult.class, attempt, ContentId.class, design);
    }

    private static AcceptedPlanResult planResult(ShipRun run, ContentId plan) {
        return acceptedStageResult(
                AcceptedPlanResult.class,
                ShipLifecycleReducer.attempt(run),
                ContentId.class,
                plan);
    }

    private static AcceptedExecutionResult executionResult(ShipRun run, ContentId execution) {
        return executionResult(ShipLifecycleReducer.attempt(run), execution);
    }

    private static AcceptedExecutionResult executionResult(Attempt attempt, ContentId execution) {
        return acceptedStageResult(
                AcceptedExecutionResult.class, attempt, ContentId.class, execution);
    }

    private static AcceptedValidationResult validationResult(ShipRun run, ContentId validation) {
        return acceptedStageResult(
                AcceptedValidationResult.class,
                ShipLifecycleReducer.attempt(run),
                ContentId.class,
                validation);
    }

    private static AcceptedStampCompletion stampCompletion(ShipRun run, ContentId evidence) {
        return stampCompletion(ShipLifecycleReducer.attempt(run), evidence);
    }

    private static AcceptedStampCompletion stampCompletion(
            Attempt attempt, ContentId evidence) {
        return acceptedStageResult(
                AcceptedStampCompletion.class, attempt, ContentId.class, evidence);
    }

    private static <T> T acceptedStageResult(
            Class<T> type, Attempt attempt, Class<?> valueType, Object value) {
        return construct(
                type,
                new Class<?>[]{Attempt.class, valueType},
                attempt,
                value);
    }

    private static PolicyWaiverEligibility eligibility(
            ShipRun run, Attempt attempt, WaiverBinding waiver) {
        return construct(
                PolicyWaiverEligibility.class,
                new Class<?>[]{ShipRunId.class, Attempt.class, WaiverBinding.class},
                run.id(),
                attempt,
                waiver);
    }

    private static <T> T construct(
            Class<T> type, Class<?>[] parameterTypes, Object... arguments) {
        try {
            Object value = MethodHandles.privateLookupIn(type, MethodHandles.lookup())
                    .findConstructor(type, MethodType.methodType(void.class, parameterTypes))
                    .invokeWithArguments(arguments);
            return type.cast(value);
        } catch (Throwable failure) {
            throw new AssertionError("Cannot issue test-only " + type.getSimpleName(), failure);
        }
    }

    private static ShipRun reconstruct(ShipRun source, AuthorityCopy copy) {
        return new ShipRun(
                source.id(),
                source.head(),
                source.state(),
                source.revision(),
                source.lastEvent(),
                copy.build());
    }

    private static void assertMalformedSnapshot(ShipRun source, AuthorityCopy copy) {
        assertThrows(IllegalArgumentException.class, () -> reconstruct(source, copy));
    }

    private static final class AuthorityCopy {
        private final ContentId context;
        private final AuthorityBasis basis;
        private final ContentId design;
        private DesignApproval designApproval;
        private final ContentId plan;
        private PlanApproval planApproval;
        private final ContentId execution;
        private ContentId validation;
        private StampCompletionBinding stampCompletion;
        private PendingInteraction pending;
        private Attempt attempt;
        private RetryBinding retry;
        private final WaiverBinding waiverCandidate;
        private WaiverBinding recordedWaiver;
        private final long lastAttemptNumber;

        private AuthorityCopy(AuthorityData source) {
            context = source.context();
            basis = source.basis();
            design = source.design();
            designApproval = source.designApproval();
            plan = source.plan();
            planApproval = source.planApproval();
            execution = source.execution();
            validation = source.validation();
            stampCompletion = source.stampCompletion();
            pending = source.pending();
            attempt = source.attempt();
            retry = source.retry();
            waiverCandidate = source.waiverCandidate();
            recordedWaiver = source.recordedWaiver();
            lastAttemptNumber = source.lastAttemptNumber();
        }

        private AuthorityData build() {
            return new AuthorityData(
                    context,
                    basis,
                    design,
                    designApproval,
                    plan,
                    planApproval,
                    execution,
                    validation,
                    stampCompletion,
                    pending,
                    attempt,
                    retry,
                    waiverCandidate,
                    recordedWaiver,
                    lastAttemptNumber);
        }
    }
}
