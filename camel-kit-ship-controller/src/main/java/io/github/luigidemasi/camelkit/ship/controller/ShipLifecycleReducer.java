package io.github.luigidemasi.camelkit.ship.controller;

import java.util.Objects;

/**
 * Stateless, event-specific lifecycle reducer.
 *
 * <p>
 * The owning service must reconstruct the supplied run from authenticated events, never from a caller-deserialized
 * snapshot, and atomically compare and replace its durable current head. This reducer validates one locally consistent
 * snapshot but cannot prove its predecessor history or determine whether it is still current. A head UUID is branch
 * identity, not a content commitment.
 */
final class ShipLifecycleReducer {

    private static final VerifiedInputKey VERIFIED_INPUT_KEY = new VerifiedInputKey();

    private ShipLifecycleReducer() {
    }

    static ShipRun start() {
        return new ShipRun(
                ShipRunId.create(),
                AuthorityHeadId.create(),
                ShipState.CREATED,
                0,
                ShipEventType.RUN_CREATED,
                AuthorityData.empty());
    }

    /**
     * Rebinds a locally reduced successor to the authority head committed by the authenticated event store.
     *
     * <p>
     * The event projector must still prove predecessor continuity and event semantics. This method only replaces the
     * reducer's provisional UUID and every successor-local copy of it, then lets {@link ShipRun} revalidate the
     * complete authority snapshot.
     */
    static ShipRun rebindAuthorityHead(ShipRun successor, AuthorityHeadId authenticatedHead) {
        ShipRun run = requireRun(successor);
        AuthorityHeadId head = Objects.requireNonNull(authenticatedHead, "authenticatedHead");
        AuthorityData current = run.authority();

        PendingInteraction pending = current.pending();
        if (pending != null) {
            pending = new PendingInteraction(
                    pending.runId(), pending.requestRevision(), head, pending.kind(), pending.binding());
        }

        Attempt attempt = current.attempt();
        if (attempt != null) {
            attempt = new Attempt(
                    attempt.runId(), head, attempt.number(), attempt.phase(), attempt.input());
        }

        RetryBinding retry = current.retry();
        if (retry != null) {
            retry = new RetryBinding(
                    retry.failedAttempt(), retry.failureRevision(), head, retry.resumeState());
        }

        AuthorityData rebound = new AuthorityData(
                current.context(),
                current.basis(),
                current.design(),
                current.designApproval(),
                current.plan(),
                current.planApproval(),
                current.execution(),
                current.validation(),
                current.stampCompletion(),
                pending,
                attempt,
                retry,
                current.waiverCandidate(),
                current.recordedWaiver(),
                current.lastAttemptNumber());
        return new ShipRun(
                run.id(), head, run.state(), run.revision(), run.lastEvent(), rebound);
    }

    static ShipRun startContextResolution(ShipRun run, ContentId input) {
        return next(run, ShipEventType.CONTEXT_RESOLUTION_STARTED)
                .startAttempt(ShipPhase.CONTEXT, input)
                .build();
    }

    static ShipRun requestDocumentConsent(ShipRun run, Attempt attempt, ContentId document) {
        requireAttempt(run, attempt);
        attempt.input().requireCapacity(1);
        return next(run, ShipEventType.DOCUMENT_CONSENT_REQUESTED)
                .clearAttempt()
                .request(
                        ShipInteractionKind.DOCUMENT_READ_CONSENT,
                        new ContentDecision(document, attempt.input()))
                .build();
    }

    static ShipRun acceptDocumentConsent(ShipRun run, InteractionDecision response) {
        requireDecision(
                run,
                response,
                ShipInteractionKind.DOCUMENT_READ_CONSENT,
                InteractionDecisionValue.ACCEPTED);
        ContentDecision document = requireContentDecision(response.request());
        return next(run, ShipEventType.DOCUMENT_CONSENT_ACCEPTED)
                .clearPending()
                .startAttempt(
                        ShipPhase.CONTEXT,
                        document.resumeInput().appendConsent(
                                ShipInteractionKind.DOCUMENT_READ_CONSENT,
                                document.subject(),
                                InteractionDecisionValue.ACCEPTED))
                .build();
    }

    static ShipRun denyDocumentConsent(ShipRun run, InteractionDecision response) {
        requireDecision(
                run,
                response,
                ShipInteractionKind.DOCUMENT_READ_CONSENT,
                InteractionDecisionValue.DENIED);
        return next(run, ShipEventType.DOCUMENT_CONSENT_DENIED).clearLiveAuthority().build();
    }

    static ShipRun recordContext(ShipRun run, AcceptedContextResult result) {
        ContentId context = requireAcceptedResult(run, result);
        Next next = next(run, ShipEventType.CONTEXT_RECORDED).clearAttempt();
        next.context = context;
        return next.build();
    }

    static ShipRun startDiscovery(ShipRun run) {
        ContentId context = requireValue(run.authority().context(), "Context is not recorded");
        return next(run, ShipEventType.DISCOVERY_STARTED)
                .startAttempt(ShipPhase.DISCOVERY, context)
                .build();
    }

    static ShipRun requestRemoteUseConsent(ShipRun run, Attempt attempt, ContentId remoteUse) {
        requireAttempt(run, attempt);
        attempt.input().requireCapacity(1);
        return next(run, ShipEventType.REMOTE_USE_CONSENT_REQUESTED)
                .clearAttempt()
                .request(
                        ShipInteractionKind.REMOTE_USE_CONSENT,
                        new ContentDecision(remoteUse, attempt.input()))
                .build();
    }

    static ShipRun acceptRemoteUseConsent(ShipRun run, InteractionDecision response) {
        requireDecision(
                run,
                response,
                ShipInteractionKind.REMOTE_USE_CONSENT,
                InteractionDecisionValue.ACCEPTED);
        ContentDecision remoteUse = requireContentDecision(response.request());
        return next(run, ShipEventType.REMOTE_USE_CONSENT_ACCEPTED)
                .clearPending()
                .startAttempt(
                        ShipPhase.DISCOVERY,
                        remoteUse.resumeInput().appendConsent(
                                ShipInteractionKind.REMOTE_USE_CONSENT,
                                remoteUse.subject(),
                                InteractionDecisionValue.ACCEPTED))
                .build();
    }

    static ShipRun denyRemoteUseConsent(ShipRun run, InteractionDecision response) {
        requireDecision(
                run,
                response,
                ShipInteractionKind.REMOTE_USE_CONSENT,
                InteractionDecisionValue.DENIED);
        ContentDecision remoteUse = requireContentDecision(response.request());
        return next(run, ShipEventType.REMOTE_USE_CONSENT_DENIED)
                .clearPending()
                .startAttempt(
                        ShipPhase.DISCOVERY,
                        remoteUse.resumeInput().appendConsent(
                                ShipInteractionKind.REMOTE_USE_CONSENT,
                                remoteUse.subject(),
                                InteractionDecisionValue.DENIED))
                .build();
    }

    static ShipRun requestDiscoveryAnswer(ShipRun run, Attempt attempt, ContentId question) {
        requireAttempt(run, attempt);
        attempt.input().requireCapacity(1);
        return next(run, ShipEventType.DISCOVERY_QUESTION_PRESENTED)
                .clearAttempt()
                .request(
                        ShipInteractionKind.DISCOVERY_ANSWER,
                        new ContentDecision(question, attempt.input()))
                .build();
    }

    static ShipRun recordDiscoveryAnswer(ShipRun run, DiscoveryAnswer response) {
        if (response == null) {
            throw new IllegalStateException("Discovery answer is required");
        }
        requirePending(run, response.request(), ShipInteractionKind.DISCOVERY_ANSWER);
        ContentDecision question = requireContentDecision(response.request());
        return next(run, ShipEventType.DISCOVERY_ANSWER_RECORDED)
                .clearPending()
                .startAttempt(
                        ShipPhase.DISCOVERY,
                        question.resumeInput().appendDiscoveryAnswer(
                                question.subject(), response.answer()))
                .build();
    }

    static ShipRun continueDiscovery(ShipRun run, AcceptedDiscoveryContinuation result) {
        ContentId continuation = requireAcceptedResult(run, result);
        return next(run, ShipEventType.DISCOVERY_CONTINUED)
                .clearAttempt()
                .startAttempt(ShipPhase.DISCOVERY, continuation)
                .build();
    }

    static ShipRun startGapReview(ShipRun run, AcceptedDiscoveryCandidate result) {
        ContentId candidate = requireAcceptedResult(run, result);
        return next(run, ShipEventType.GAP_REVIEW_STARTED)
                .clearAttempt()
                .startAttempt(ShipPhase.REVIEW, candidate)
                .build();
    }

    static ShipRun reopenGapReview(ShipRun run, AcceptedReviewResult result) {
        GapReviewResult review = requireAcceptedResult(run, result);
        ContentId candidate = requireReviewCandidate(result.attempt());
        if (!candidate.equals(review.candidate())) {
            throw new IllegalStateException("Review result does not bind the reviewed candidate");
        }
        ContentId context = requireValue(run.authority().context(), "Context is not recorded");
        PhaseInput discoveryInput = new PhaseInput(
                java.util.List.of(
                        new ContentBinding(context),
                        new ReviewFeedbackBinding(candidate, review.feedback())));
        Next next = next(run, ShipEventType.GAP_REVIEW_REOPENED).clearAttempt();
        next.basis = null;
        next.clearDesignAndLater();
        return next.startAttempt(ShipPhase.DISCOVERY, discoveryInput).build();
    }

    static ShipRun requirementsReady(ShipRun run, AcceptedRequirementsResult result) {
        requireReviewCandidate(ShipLifecycleReducer.attempt(run));
        AuthorityBasis basis = requireAcceptedResult(run, result);
        if (!basis.context().equals(run.authority().context())) {
            throw new IllegalStateException("Authority basis does not bind the recorded context");
        }
        Next next = next(run, ShipEventType.REQUIREMENTS_READY).clearAttempt();
        next.basis = basis;
        next.clearDesignAndLater();
        return next.build();
    }

    static ShipRun startDesign(ShipRun run) {
        AuthorityBasis basis = requireBasis(run);
        return next(run, ShipEventType.DESIGN_STARTED)
                .startAttempt(ShipPhase.DESIGN, basis.requirements())
                .build();
    }

    static ShipRun designReady(ShipRun run, AcceptedDesignResult result) {
        ContentId design = requireAcceptedResult(run, result);
        Next next = next(run, ShipEventType.DESIGN_READY).clearAttempt();
        next.design = design;
        next.designApproval = null;
        next.clearPlanAndLater();
        return next.build();
    }

    static ShipRun designGapsFound(ShipRun run, AcceptedDesignGapResult result) {
        DesignGapResult gaps = requireAcceptedResult(run, result);
        ContentId context = requireValue(run.authority().context(), "Context is not recorded");
        PhaseInput discoveryInput = new PhaseInput(
                java.util.List.of(
                        new ContentBinding(context),
                        new ReviewFeedbackBinding(gaps.candidate(), gaps.feedback())));
        Next next = next(run, ShipEventType.DESIGN_GAPS_FOUND).clearAttempt();
        next.basis = null;
        next.clearDesignAndLater();
        return next.startAttempt(ShipPhase.DISCOVERY, discoveryInput).build();
    }

    static ShipRun requestDesignApproval(ShipRun run) {
        requireBasis(run);
        ContentId design = requireValue(run.authority().design(), "Design is not ready");
        return next(run, ShipEventType.DESIGN_APPROVAL_REQUESTED)
                .request(
                        ShipInteractionKind.DESIGN_APPROVAL,
                        new DesignDecisionBinding(requireBasis(run), design))
                .build();
    }

    static ShipRun approveDesign(ShipRun run, DesignApprovalDecision response) {
        PendingInteraction request = requireDesignDecision(
                run,
                response,
                DesignApprovalDecisionValue.APPROVE);
        AuthorityBasis basis = requireBasis(run);
        ContentId design = requireValue(run.authority().design(), "Design is not ready");
        requireBinding(request, new DesignDecisionBinding(basis, design));
        Next next = next(run, ShipEventType.DESIGN_APPROVED).clearPending();
        next.designApproval = new DesignApproval(basis, design);
        return next.build();
    }

    static ShipRun denyDesignApproval(ShipRun run, DesignApprovalDecision response) {
        requireDesignDecision(
                run,
                response,
                DesignApprovalDecisionValue.REQUEST_DESIGN_CHANGES);
        ContentId rejectedDesign = requireValue(run.authority().design(), "Design is not ready");
        PhaseInput retryInput = new PhaseInput(
                java.util.List.of(
                        new ContentBinding(requireBasis(run).requirements()),
                        new ApprovalFeedbackBinding(
                                ShipInteractionKind.DESIGN_APPROVAL,
                                rejectedDesign,
                                response.feedback())));
        Next next = next(run, ShipEventType.DESIGN_APPROVAL_DENIED).clearPending();
        next.design = null;
        next.designApproval = null;
        next.clearPlanAndLater();
        return next.startAttempt(ShipPhase.DESIGN, retryInput).build();
    }

    static ShipRun requestRequirementsChangesFromDesign(
            ShipRun run, DesignApprovalDecision response) {
        requireDesignDecision(
                run, response, DesignApprovalDecisionValue.REQUEST_REQUIREMENTS_CHANGES);
        ContentId rejectedDesign = requireValue(run.authority().design(), "Design is not ready");
        ContentId context = requireValue(run.authority().context(), "Context is not recorded");
        PhaseInput discoveryInput = new PhaseInput(
                java.util.List.of(
                        new ContentBinding(context),
                        new ApprovalFeedbackBinding(
                                ShipInteractionKind.DESIGN_APPROVAL,
                                rejectedDesign,
                                response.feedback())));
        Next next = next(run, ShipEventType.DESIGN_REQUIREMENTS_CHANGES_REQUESTED)
                .clearPending();
        next.basis = null;
        next.clearDesignAndLater();
        return next.startAttempt(ShipPhase.DISCOVERY, discoveryInput).build();
    }

    static ShipRun abortDesignApproval(ShipRun run, DesignApprovalDecision response) {
        requireDesignDecision(run, response, DesignApprovalDecisionValue.ABORT);
        return next(run, ShipEventType.DESIGN_APPROVAL_ABORTED).clearLiveAuthority().build();
    }

    static ShipRun startPlan(ShipRun run) {
        DesignApproval designApproval = requireCurrentDesignApproval(run);
        return next(run, ShipEventType.PLAN_STARTED)
                .startAttempt(ShipPhase.PLAN, designApproval.design())
                .build();
    }

    static ShipRun planValidated(ShipRun run, AcceptedPlanResult result) {
        ContentId plan = requireAcceptedResult(run, result);
        requireCurrentDesignApproval(run);
        Next next = next(run, ShipEventType.PLAN_VALIDATED).clearAttempt();
        next.plan = plan;
        next.planApproval = null;
        next.clearWaiver();
        return next.build();
    }

    static ShipRun requestPlanApproval(ShipRun run) {
        requireCurrentDesignApproval(run);
        ContentId plan = requireValue(run.authority().plan(), "Plan is not validated");
        return next(run, ShipEventType.PLAN_APPROVAL_REQUESTED)
                .request(
                        ShipInteractionKind.PLAN_APPROVAL,
                        new PlanDecisionBinding(requireBasis(run), requireCurrentDesignApproval(run), plan))
                .build();
    }

    static ShipRun approvePlan(ShipRun run, PlanApprovalDecision response) {
        PendingInteraction request = requirePlanDecision(
                run,
                response,
                PlanApprovalDecisionValue.APPROVE);
        AuthorityBasis basis = requireBasis(run);
        DesignApproval designApproval = requireCurrentDesignApproval(run);
        ContentId plan = requireValue(run.authority().plan(), "Plan is not validated");
        requireBinding(request, new PlanDecisionBinding(basis, designApproval, plan));
        Next next = next(run, ShipEventType.PLAN_APPROVED).clearPending();
        next.planApproval = new PlanApproval(basis, designApproval.design(), plan);
        return next.build();
    }

    static ShipRun denyPlanApproval(ShipRun run, PlanApprovalDecision response) {
        requirePlanDecision(
                run,
                response,
                PlanApprovalDecisionValue.REQUEST_PLAN_CHANGES);
        ContentId rejectedPlan = requireValue(run.authority().plan(), "Plan is not validated");
        PhaseInput retryInput = new PhaseInput(
                java.util.List.of(
                        new ContentBinding(requireCurrentDesignApproval(run).design()),
                        new ApprovalFeedbackBinding(
                                ShipInteractionKind.PLAN_APPROVAL,
                                rejectedPlan,
                                response.feedback())));
        Next next = next(run, ShipEventType.PLAN_APPROVAL_DENIED).clearPending();
        next.plan = null;
        next.planApproval = null;
        next.clearWaiver();
        return next.startAttempt(ShipPhase.PLAN, retryInput).build();
    }

    static ShipRun requestDesignChangesFromPlan(
            ShipRun run, PlanApprovalDecision response) {
        requirePlanDecision(run, response, PlanApprovalDecisionValue.REQUEST_DESIGN_CHANGES);
        ContentId rejectedPlan = requireValue(run.authority().plan(), "Plan is not validated");
        AuthorityBasis basis = requireBasis(run);
        PhaseInput designInput = new PhaseInput(
                java.util.List.of(
                        new ContentBinding(basis.requirements()),
                        new ApprovalFeedbackBinding(
                                ShipInteractionKind.PLAN_APPROVAL,
                                rejectedPlan,
                                response.feedback())));
        Next next = next(run, ShipEventType.PLAN_DESIGN_CHANGES_REQUESTED).clearPending();
        next.design = null;
        next.designApproval = null;
        next.clearPlanAndLater();
        return next.startAttempt(ShipPhase.DESIGN, designInput).build();
    }

    static ShipRun requestRequirementsChangesFromPlan(
            ShipRun run, PlanApprovalDecision response) {
        requirePlanDecision(run, response, PlanApprovalDecisionValue.REQUEST_REQUIREMENTS_CHANGES);
        ContentId rejectedPlan = requireValue(run.authority().plan(), "Plan is not validated");
        ContentId context = requireValue(run.authority().context(), "Context is not recorded");
        PhaseInput discoveryInput = new PhaseInput(
                java.util.List.of(
                        new ContentBinding(context),
                        new ApprovalFeedbackBinding(
                                ShipInteractionKind.PLAN_APPROVAL,
                                rejectedPlan,
                                response.feedback())));
        Next next = next(run, ShipEventType.PLAN_REQUIREMENTS_CHANGES_REQUESTED)
                .clearPending();
        next.basis = null;
        next.clearDesignAndLater();
        return next.startAttempt(ShipPhase.DISCOVERY, discoveryInput).build();
    }

    static ShipRun abortPlanApproval(ShipRun run, PlanApprovalDecision response) {
        requirePlanDecision(run, response, PlanApprovalDecisionValue.ABORT);
        return next(run, ShipEventType.PLAN_APPROVAL_ABORTED).clearLiveAuthority().build();
    }

    static ShipRun startExecution(ShipRun run) {
        PlanApproval planApproval = requireCurrentApprovals(run);
        return next(run, ShipEventType.EXECUTION_STARTED)
                .startAttempt(ShipPhase.EXECUTE, planApproval.plan())
                .build();
    }

    static ShipRun executionValidated(ShipRun run, AcceptedExecutionResult result) {
        ContentId execution = requireAcceptedResult(run, result);
        requireCurrentApprovals(run);
        Next next = next(run, ShipEventType.EXECUTION_VALIDATED).clearAttempt();
        next.execution = execution;
        next.validation = null;
        return next.build();
    }

    static ShipRun startValidation(ShipRun run) {
        requireCurrentApprovals(run);
        ContentId execution = requireValue(run.authority().execution(), "Execution is not validated");
        return next(run, ShipEventType.VALIDATION_STARTED)
                .startAttempt(ShipPhase.VALIDATE, execution)
                .build();
    }

    static ShipRun validationPassed(ShipRun run, AcceptedValidationResult result) {
        ContentId validation = requireAcceptedResult(run, result);
        requireCurrentApprovals(run);
        Next next = next(run, ShipEventType.VALIDATION_PASSED).clearAttempt();
        next.validation = validation;
        return next.build();
    }

    static ShipRun recordWaivableFailure(
            ShipRun run, Attempt attempt, PolicyWaiverEligibility eligibility) {
        requireAttempt(run, attempt);
        requireCurrentApprovals(run);
        if (eligibility == null
                || !run.id().equals(eligibility.runId())
                || !attempt.equals(eligibility.validationAttempt())
                || !requireBasis(run).policy().equals(eligibility.waiver().policy())) {
            throw new IllegalStateException("Waiver eligibility does not match the validation attempt and policy");
        }
        Next next = next(run, ShipEventType.WAIVABLE_FAILURE_RECORDED).clearAttempt();
        next.waiverCandidate = eligibility.waiver();
        next.recordedWaiver = null;
        return next.build();
    }

    static ShipRun requestWaiver(ShipRun run) {
        requireCurrentApprovals(run);
        WaiverBinding waiver = requireValue(run.authority().waiverCandidate(), "No eligible waiver");
        return next(run, ShipEventType.WAIVER_REQUESTED)
                .request(ShipInteractionKind.WAIVER, waiver)
                .build();
    }

    static ShipRun recordWaiver(ShipRun run, InteractionDecision response) {
        PendingInteraction request = requireDecision(
                run,
                response,
                ShipInteractionKind.WAIVER,
                InteractionDecisionValue.ACCEPTED);
        requireCurrentApprovals(run);
        WaiverBinding waiver = requireValue(run.authority().waiverCandidate(), "No eligible waiver");
        requireBinding(request, waiver);
        Next next = next(run, ShipEventType.WAIVER_RECORDED).clearPending();
        next.recordedWaiver = waiver;
        return next.build();
    }

    static ShipRun denyWaiver(ShipRun run, InteractionDecision response) {
        requireDecision(
                run, response, ShipInteractionKind.WAIVER, InteractionDecisionValue.DENIED);
        return next(run, ShipEventType.WAIVER_DENIED).clearLiveAuthority().build();
    }

    static ShipRun startStamp(ShipRun run) {
        requireCurrentApprovals(run);
        ContentId validation = requireValue(run.authority().validation(), "Validation has not passed");
        return next(run, ShipEventType.STAMP_STARTED)
                .startAttempt(ShipPhase.STAMP, validation)
                .build();
    }

    static ShipRun startWaiverStamp(ShipRun run) {
        requireCurrentApprovals(run);
        WaiverBinding candidate = requireValue(run.authority().waiverCandidate(), "No eligible waiver");
        if (!candidate.equals(run.authority().recordedWaiver())) {
            throw new IllegalStateException("Waiver was not recorded for the eligible failure");
        }
        return next(run, ShipEventType.WAIVER_STAMP_STARTED)
                .startAttempt(ShipPhase.WAIVER_STAMP, candidate.evidence())
                .build();
    }

    static ShipRun completeStamp(ShipRun run, AcceptedStampCompletion completion) {
        ContentId evidence = requireAcceptedResult(run, completion);
        requireCurrentApprovals(run);
        Next next = next(run, ShipEventType.RUN_COMPLETED).clearLiveAuthority();
        next.stampCompletion = new StampCompletionBinding(completion.attempt().phase(), evidence);
        return next.build();
    }

    static ShipRun completeWaiverStamp(ShipRun run, AcceptedStampCompletion completion) {
        ContentId evidence = requireAcceptedResult(run, completion);
        requireCurrentApprovals(run);
        WaiverBinding candidate = requireValue(run.authority().waiverCandidate(), "No eligible waiver");
        if (!candidate.equals(run.authority().recordedWaiver())) {
            throw new IllegalStateException("Waiver was not recorded for the eligible failure");
        }
        Next next = next(run, ShipEventType.RUN_COMPLETED_WITH_WAIVER).clearLiveAuthority();
        next.stampCompletion = new StampCompletionBinding(completion.attempt().phase(), evidence);
        return next.build();
    }

    static ShipRun failRetryable(ShipRun run, Attempt failedAttempt) {
        requireAttempt(run, failedAttempt);
        ShipPhase phase = ShipPhase.requireRunningState(run.state());
        Next next = next(run, ShipEventType.ATTEMPT_FAILED_RETRYABLE).clearAttempt();
        next.retry = new RetryBinding(failedAttempt, next.revision, next.head, phase.runningState());
        return next.build();
    }

    static ShipRun retry(ShipRun run, RetryBinding retry) {
        if (retry == null || !retry.equals(run.authority().retry())) {
            throw new IllegalStateException("Retry does not match the failed attempt");
        }
        Next next = next(run, ShipEventType.RETRY_STARTED);
        next.retry = null;
        return next.startAttempt(
                retry.failedAttempt().phase(), retry.failedAttempt().input())
                .build();
    }

    static ShipRun requirementsInputsChanged(ShipRun run) {
        ContentId context = requireValue(run.authority().context(), "Context is not recorded");
        Next next = next(run, ShipEventType.REQUIREMENTS_INPUTS_CHANGED).clearLiveAuthority();
        next.basis = null;
        next.clearDesignAndLater();
        return next.startAttempt(ShipPhase.DISCOVERY, context).build();
    }

    static ShipRun designInputsChanged(ShipRun run) {
        AuthorityBasis basis = requireBasis(run);
        Next next = next(run, ShipEventType.DESIGN_INPUTS_CHANGED).clearLiveAuthority();
        next.design = null;
        next.designApproval = null;
        next.clearPlanAndLater();
        return next.startAttempt(ShipPhase.DESIGN, basis.requirements()).build();
    }

    static ShipRun planInputsChanged(ShipRun run) {
        DesignApproval designApproval = requireCurrentDesignApproval(run);
        Next next = next(run, ShipEventType.PLAN_INPUTS_CHANGED).clearLiveAuthority();
        next.clearPlanAndLater();
        return next.startAttempt(ShipPhase.PLAN, designApproval.design()).build();
    }

    static ShipRun failTerminal(ShipRun run) {
        return next(run, ShipEventType.RUN_FAILED_TERMINAL).clearLiveAuthority().build();
    }

    static ShipRun abort(ShipRun run) {
        return next(run, ShipEventType.RUN_ABORTED).clearLiveAuthority().build();
    }

    /** Applies an interaction outcome only after the owning controller has verified its protected record. */
    static ShipRun applyVerifiedDecision(
            ShipRun run, ShipEventType event, ContentId feedback) {
        if (event == ShipEventType.DESIGN_APPROVED
                || event == ShipEventType.DESIGN_APPROVAL_DENIED
                || event == ShipEventType.DESIGN_REQUIREMENTS_CHANGES_REQUESTED
                || event == ShipEventType.DESIGN_APPROVAL_ABORTED) {
            return applyVerifiedDesignDecision(run, event, feedback);
        }
        if (event == ShipEventType.PLAN_APPROVED
                || event == ShipEventType.PLAN_APPROVAL_DENIED
                || event == ShipEventType.PLAN_DESIGN_CHANGES_REQUESTED
                || event == ShipEventType.PLAN_REQUIREMENTS_CHANGES_REQUESTED
                || event == ShipEventType.PLAN_APPROVAL_ABORTED) {
            return applyVerifiedPlanDecision(run, event, feedback);
        }
        PendingInteraction pending = pending(run);
        InteractionDecisionValue value = switch (event) {
            case DOCUMENT_CONSENT_ACCEPTED,
                    REMOTE_USE_CONSENT_ACCEPTED,
                    WAIVER_RECORDED ->
                InteractionDecisionValue.ACCEPTED;
            case DOCUMENT_CONSENT_DENIED,
                    REMOTE_USE_CONSENT_DENIED,
                    WAIVER_DENIED ->
                InteractionDecisionValue.DENIED;
            default -> throw new IllegalArgumentException("Event is not a verified interaction decision: " + event);
        };
        InteractionDecision decision = InteractionDecision.issue(
                VERIFIED_INPUT_KEY, pending, value, feedback);
        return switch (event) {
            case DOCUMENT_CONSENT_ACCEPTED -> acceptDocumentConsent(run, decision);
            case DOCUMENT_CONSENT_DENIED -> denyDocumentConsent(run, decision);
            case REMOTE_USE_CONSENT_ACCEPTED -> acceptRemoteUseConsent(run, decision);
            case REMOTE_USE_CONSENT_DENIED -> denyRemoteUseConsent(run, decision);
            case WAIVER_RECORDED -> recordWaiver(run, decision);
            case WAIVER_DENIED -> denyWaiver(run, decision);
            default -> throw new AssertionError(event);
        };
    }

    /** Applies a typed design outcome only after the owning controller has verified its protected record. */
    static ShipRun applyVerifiedDesignDecision(
            ShipRun run, ShipEventType event, ContentId feedback) {
        DesignApprovalDecisionValue value = switch (event) {
            case DESIGN_APPROVED -> DesignApprovalDecisionValue.APPROVE;
            case DESIGN_APPROVAL_DENIED -> DesignApprovalDecisionValue.REQUEST_DESIGN_CHANGES;
            case DESIGN_REQUIREMENTS_CHANGES_REQUESTED ->
                DesignApprovalDecisionValue.REQUEST_REQUIREMENTS_CHANGES;
            case DESIGN_APPROVAL_ABORTED -> DesignApprovalDecisionValue.ABORT;
            default -> throw new IllegalArgumentException("Event is not a design decision: " + event);
        };
        DesignApprovalDecision decision = DesignApprovalDecision.issue(
                VERIFIED_INPUT_KEY, pending(run), value, feedback);
        return switch (value) {
            case APPROVE -> approveDesign(run, decision);
            case REQUEST_DESIGN_CHANGES -> denyDesignApproval(run, decision);
            case REQUEST_REQUIREMENTS_CHANGES -> requestRequirementsChangesFromDesign(run, decision);
            case ABORT -> abortDesignApproval(run, decision);
        };
    }

    /** Applies a typed plan outcome only after the owning controller has verified its protected record. */
    static ShipRun applyVerifiedPlanDecision(
            ShipRun run, ShipEventType event, ContentId feedback) {
        PlanApprovalDecisionValue value = switch (event) {
            case PLAN_APPROVED -> PlanApprovalDecisionValue.APPROVE;
            case PLAN_APPROVAL_DENIED -> PlanApprovalDecisionValue.REQUEST_PLAN_CHANGES;
            case PLAN_DESIGN_CHANGES_REQUESTED -> PlanApprovalDecisionValue.REQUEST_DESIGN_CHANGES;
            case PLAN_REQUIREMENTS_CHANGES_REQUESTED ->
                PlanApprovalDecisionValue.REQUEST_REQUIREMENTS_CHANGES;
            case PLAN_APPROVAL_ABORTED -> PlanApprovalDecisionValue.ABORT;
            default -> throw new IllegalArgumentException("Event is not a plan decision: " + event);
        };
        PlanApprovalDecision decision = PlanApprovalDecision.issue(
                VERIFIED_INPUT_KEY, pending(run), value, feedback);
        return switch (value) {
            case APPROVE -> approvePlan(run, decision);
            case REQUEST_PLAN_CHANGES -> denyPlanApproval(run, decision);
            case REQUEST_DESIGN_CHANGES -> requestDesignChangesFromPlan(run, decision);
            case REQUEST_REQUIREMENTS_CHANGES -> requestRequirementsChangesFromPlan(run, decision);
            case ABORT -> abortPlanApproval(run, decision);
        };
    }

    /** Applies a discovery answer only after the owning controller has verified its protected record. */
    static ShipRun applyVerifiedDiscoveryAnswer(ShipRun run, ContentId answer) {
        return recordDiscoveryAnswer(
                run,
                DiscoveryAnswer.issue(VERIFIED_INPUT_KEY, pending(run), answer));
    }

    /** Applies waiver eligibility only after deterministic controller policy verification. */
    static ShipRun applyVerifiedWaiverEligibility(
            ShipRun run, ContentId stableCheckId, ContentId evidence, ContentId policy) {
        Attempt attempt = attempt(run);
        PolicyWaiverEligibility eligibility = PolicyWaiverEligibility.issue(
                VERIFIED_INPUT_KEY,
                run.id(),
                attempt,
                new WaiverBinding(stableCheckId, evidence, policy));
        return recordWaivableFailure(run, attempt, eligibility);
    }

    static void requireVerifiedInputKey(VerifiedInputKey key) {
        if (key != VERIFIED_INPUT_KEY) {
            throw new IllegalArgumentException("Lifecycle capabilities require verified controller input");
        }
    }

    static final class VerifiedInputKey {

        private VerifiedInputKey() {
        }
    }

    static PendingInteraction pending(ShipRun run) {
        PendingInteraction pending = requireRun(run).authority().pending();
        if (pending == null) {
            throw new IllegalStateException("Ship run has no pending interaction");
        }
        return pending;
    }

    static Attempt attempt(ShipRun run) {
        Attempt attempt = requireRun(run).authority().attempt();
        if (attempt == null) {
            throw new IllegalStateException("Ship run has no active attempt");
        }
        return attempt;
    }

    static RetryBinding retryBinding(ShipRun run) {
        RetryBinding retry = requireRun(run).authority().retry();
        if (retry == null) {
            throw new IllegalStateException("Ship run has no retryable failure");
        }
        return retry;
    }

    private static Next next(ShipRun run, ShipEventType event) {
        return new Next(requireRun(run), event);
    }

    private static ShipRun requireRun(ShipRun run) {
        return Objects.requireNonNull(run, "run");
    }

    private static AuthorityBasis requireBasis(ShipRun run) {
        return requireValue(run.authority().basis(), "Authority basis is not ready");
    }

    private static DesignApproval requireCurrentDesignApproval(ShipRun run) {
        AuthorityBasis basis = requireBasis(run);
        ContentId design = requireValue(run.authority().design(), "Design is not ready");
        DesignApproval expected = new DesignApproval(basis, design);
        if (!expected.equals(run.authority().designApproval())) {
            throw new IllegalStateException("Current design is not approved");
        }
        return expected;
    }

    private static PlanApproval requireCurrentApprovals(ShipRun run) {
        DesignApproval designApproval = requireCurrentDesignApproval(run);
        ContentId plan = requireValue(run.authority().plan(), "Plan is not validated");
        PlanApproval expected = new PlanApproval(designApproval.basis(), designApproval.design(), plan);
        if (!expected.equals(run.authority().planApproval())) {
            throw new IllegalStateException("Current plan is not approved");
        }
        return expected;
    }

    private static PendingInteraction requireDecision(
            ShipRun run,
            InteractionDecision response,
            ShipInteractionKind expectedKind,
            InteractionDecisionValue expectedValue) {
        if (response == null || response.value() != expectedValue) {
            throw new IllegalStateException("Decision does not match " + expectedValue);
        }
        requirePending(run, response.request(), expectedKind);
        return response.request();
    }

    private static PendingInteraction requireDesignDecision(
            ShipRun run,
            DesignApprovalDecision response,
            DesignApprovalDecisionValue expectedValue) {
        if (response == null || response.value() != expectedValue) {
            throw new IllegalStateException("Design decision does not match " + expectedValue);
        }
        requirePending(run, response.request(), ShipInteractionKind.DESIGN_APPROVAL);
        return response.request();
    }

    private static PendingInteraction requirePlanDecision(
            ShipRun run,
            PlanApprovalDecision response,
            PlanApprovalDecisionValue expectedValue) {
        if (response == null || response.value() != expectedValue) {
            throw new IllegalStateException("Plan decision does not match " + expectedValue);
        }
        requirePending(run, response.request(), ShipInteractionKind.PLAN_APPROVAL);
        return response.request();
    }

    private static void requirePending(
            ShipRun run, PendingInteraction response, ShipInteractionKind expectedKind) {
        PendingInteraction pending = requireValue(run.authority().pending(), "No interaction is pending");
        if (response == null || !pending.equals(response) || response.kind() != expectedKind) {
            throw new IllegalStateException("Response does not match the pending " + expectedKind);
        }
    }

    private static void requireBinding(PendingInteraction response, DecisionBinding expected) {
        if (!expected.equals(response.binding())) {
            throw new IllegalStateException("Response does not match the current authority binding");
        }
    }

    private static ContentDecision requireContentDecision(PendingInteraction request) {
        if (!(request.binding() instanceof ContentDecision content)) {
            throw new IllegalStateException("Pending interaction is not content-bound");
        }
        return content;
    }

    private static ContentId requireReviewCandidate(Attempt attempt) {
        if (attempt == null
                || attempt.phase() != ShipPhase.REVIEW
                || attempt.input().bindings().size() != 1
                || !(attempt.input().bindings().get(0) instanceof ContentBinding candidate)) {
            throw new IllegalStateException("Review attempt does not bind one discovery candidate");
        }
        return candidate.content();
    }

    private static void requireAttempt(ShipRun run, Attempt result) {
        Attempt current = requireValue(run.authority().attempt(), "No attempt is active");
        if (result == null || !current.equals(result)) {
            throw new IllegalStateException("Result does not match the active attempt");
        }
    }

    private static <T> T requireAcceptedResult(ShipRun run, AcceptedStageResult<T> result) {
        if (result == null) {
            throw new IllegalStateException("Accepted stage result is required");
        }
        requireAttempt(run, result.attempt());
        return result.value();
    }

    private static <T> T requireValue(T value, String message) {
        if (value == null) {
            throw new IllegalStateException(message);
        }
        return value;
    }

    private static final class Next {
        private final ShipRun previous;
        private final ShipState state;
        private final ShipEventType event;
        private final long revision;
        private final AuthorityHeadId head;
        private ContentId context;
        private AuthorityBasis basis;
        private ContentId design;
        private DesignApproval designApproval;
        private ContentId plan;
        private PlanApproval planApproval;
        private ContentId execution;
        private ContentId validation;
        private StampCompletionBinding stampCompletion;
        private PendingInteraction pending;
        private Attempt attempt;
        private RetryBinding retry;
        private WaiverBinding waiverCandidate;
        private WaiverBinding recordedWaiver;
        private long lastAttemptNumber;

        private Next(ShipRun previous, ShipEventType event) {
            this.previous = previous;
            this.state = ShipTransitionPolicy.requireNext(previous.state(), event);
            this.event = event;
            this.revision = Math.incrementExact(previous.revision());
            this.head = AuthorityHeadId.create();
            AuthorityData current = previous.authority();
            this.context = current.context();
            this.basis = current.basis();
            this.design = current.design();
            this.designApproval = current.designApproval();
            this.plan = current.plan();
            this.planApproval = current.planApproval();
            this.execution = current.execution();
            this.validation = current.validation();
            this.stampCompletion = current.stampCompletion();
            this.pending = current.pending();
            this.attempt = current.attempt();
            this.retry = current.retry();
            this.waiverCandidate = current.waiverCandidate();
            this.recordedWaiver = current.recordedWaiver();
            this.lastAttemptNumber = current.lastAttemptNumber();
        }

        private Next request(ShipInteractionKind kind, DecisionBinding binding) {
            if (pending != null) {
                throw new IllegalStateException("Ship run already has a pending interaction");
            }
            pending = new PendingInteraction(previous.id(), revision, head, kind, binding);
            return this;
        }

        private Next clearPending() {
            pending = null;
            return this;
        }

        private Next startAttempt(ShipPhase phase, ContentId input) {
            return startAttempt(phase, PhaseInput.initial(input));
        }

        private Next startAttempt(ShipPhase phase, PhaseInput input) {
            Objects.requireNonNull(phase, "phase");
            Objects.requireNonNull(input, "input");
            if (state != phase.runningState()) {
                throw new IllegalStateException("Attempt phase does not match successor state " + state);
            }
            if (pending != null || retry != null || attempt != null) {
                throw new IllegalStateException("Cannot start an attempt while other authority is active");
            }
            lastAttemptNumber = Math.incrementExact(lastAttemptNumber);
            attempt = new Attempt(previous.id(), head, lastAttemptNumber, phase, input);
            return this;
        }

        private Next clearAttempt() {
            attempt = null;
            return this;
        }

        private Next clearLiveAuthority() {
            pending = null;
            attempt = null;
            retry = null;
            return this;
        }

        private void clearDesignAndLater() {
            design = null;
            designApproval = null;
            clearPlanAndLater();
        }

        private void clearPlanAndLater() {
            plan = null;
            planApproval = null;
            execution = null;
            validation = null;
            stampCompletion = null;
            clearWaiver();
        }

        private void clearWaiver() {
            waiverCandidate = null;
            recordedWaiver = null;
        }

        private ShipRun build() {
            AuthorityData authority = new AuthorityData(
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
            return new ShipRun(previous.id(), head, state, revision, event, authority);
        }
    }
}
