package io.github.luigidemasi.camelkit.ship.controller;

import java.util.Objects;

/** Immutable read model for one revision of a Ship run. */
public final class ShipRun {

    private final ShipRunId id;
    private final AuthorityHeadId head;
    private final ShipState state;
    private final long revision;
    private final ShipEventType lastEvent;
    private final AuthorityData authority;

    ShipRun(
            ShipRunId id,
            AuthorityHeadId head,
            ShipState state,
            long revision,
            ShipEventType lastEvent,
            AuthorityData authority) {
        this.id = Objects.requireNonNull(id, "id");
        this.head = Objects.requireNonNull(head, "head");
        this.state = Objects.requireNonNull(state, "state");
        if (revision < 0) {
            throw new IllegalArgumentException("Ship run revision must not be negative");
        }
        this.revision = revision;
        this.lastEvent = Objects.requireNonNull(lastEvent, "lastEvent");
        this.authority = Objects.requireNonNull(authority, "authority");
        requireConsistentAuthority();
    }

    public ShipRunId id() {
        return id;
    }

    public ShipState state() {
        return state;
    }

    public long revision() {
        return revision;
    }

    public ShipEventType lastEvent() {
        return lastEvent;
    }

    public boolean terminal() {
        return state.isTerminal();
    }

    AuthorityData authority() {
        return authority;
    }

    AuthorityHeadId head() {
        return head;
    }

    private void requireConsistentAuthority() {
        if (!ShipTransitionPolicy.canProduce(lastEvent, state)) {
            throw new IllegalArgumentException("Last event cannot produce state " + state);
        }
        if (revision == 0) {
            if (state != ShipState.CREATED
                    || lastEvent != ShipEventType.RUN_CREATED
                    || !authority.equals(AuthorityData.empty())) {
                throw new IllegalArgumentException("Revision zero must be an empty CREATED run");
            }
        } else if (state == ShipState.CREATED || lastEvent == ShipEventType.RUN_CREATED) {
            throw new IllegalArgumentException("RUN_CREATED is valid only at revision zero");
        }

        ShipInteractionKind expectedPending = state.pendingInteraction().orElse(null);
        ShipInteractionKind actualPending
                = authority.pending() == null ? null : authority.pending().kind();
        if (expectedPending != actualPending) {
            throw new IllegalArgumentException("Pending interaction does not match state " + state);
        }
        if (state.isTerminal()
                && (authority.pending() != null
                        || authority.attempt() != null
                        || authority.retry() != null)) {
            throw new IllegalArgumentException("Terminal Ship runs cannot retain active authority");
        }

        requireEmbeddedIdentities();

        boolean retryState = false;
        ShipPhase expectedAttemptPhase = null;
        for (ShipPhase phase : ShipPhase.values()) {
            retryState |= phase.retryableFailureState() == state;
            if (phase.runningState() == state) {
                expectedAttemptPhase = phase;
            }
        }
        if (retryState != (authority.retry() != null)) {
            throw new IllegalArgumentException("Retry authority does not match state " + state);
        }
        if (expectedAttemptPhase != null && authority.attempt() == null) {
            throw new IllegalArgumentException("Running state has no bound attempt: " + state);
        }
        if (expectedAttemptPhase == null && authority.attempt() != null) {
            throw new IllegalArgumentException("Non-running state retains an attempt: " + state);
        }
        if (authority.attempt() != null
                && authority.attempt().phase() != expectedAttemptPhase) {
            throw new IllegalArgumentException("Attempt phase does not match state " + state);
        }
        if (authority.pending() != null && authority.attempt() != null) {
            throw new IllegalArgumentException("A pending interaction cannot retain a worker attempt");
        }

        requireRelationalBindings();
        requireCompletionBinding();
        requireStateBindings();
    }

    private void requireCompletionBinding() {
        StampCompletionBinding completion = authority.stampCompletion();
        if (state == ShipState.COMPLETED) {
            if (completion == null || completion.phase() != ShipPhase.STAMP) {
                throw new IllegalArgumentException("Completed run has no normal stamp evidence");
            }
        } else if (state == ShipState.COMPLETED_WITH_WAIVER) {
            if (completion == null || completion.phase() != ShipPhase.WAIVER_STAMP) {
                throw new IllegalArgumentException("Waived run has no waiver-stamp evidence");
            }
        } else if (completion != null) {
            throw new IllegalArgumentException("Non-completed run retains stamp-completion evidence");
        }
    }

    private void requireEmbeddedIdentities() {
        PendingInteraction pending = authority.pending();
        if (pending != null
                && (!id.equals(pending.runId())
                        || revision != pending.requestRevision()
                        || !head.equals(pending.requestHead()))) {
            throw new IllegalArgumentException("Pending interaction is not bound to this run head");
        }
        Attempt attempt = authority.attempt();
        if (attempt != null
                && (!id.equals(attempt.runId())
                        || !head.equals(attempt.authorityHead())
                        || attempt.number() != authority.lastAttemptNumber())) {
            throw new IllegalArgumentException("Attempt is not bound to this run head");
        }
        RetryBinding retry = authority.retry();
        if (retry != null
                && (!id.equals(retry.failedAttempt().runId())
                        || retry.failedAttempt().number() != authority.lastAttemptNumber()
                        || retry.failureRevision() != revision
                        || !head.equals(retry.failureHead())
                        || retry.failedAttempt().phase().retryableFailureState() != state)) {
            throw new IllegalArgumentException("Retry is not bound to this failure head");
        }
        if (authority.lastAttemptNumber() == 0 && (attempt != null || retry != null)) {
            throw new IllegalArgumentException("Active authority requires a positive attempt history");
        }
    }

    private void requireRelationalBindings() {
        AuthorityBasis basis = authority.basis();
        if (basis != null
                && (authority.context() == null || !basis.context().equals(authority.context()))) {
            throw new IllegalArgumentException("Authority basis does not bind the recorded context");
        }
        DesignApproval designApproval = authority.designApproval();
        if (designApproval != null
                && (basis == null
                        || authority.design() == null
                        || !designApproval.equals(new DesignApproval(basis, authority.design())))) {
            throw new IllegalArgumentException("Design approval does not bind current inputs");
        }
        PlanApproval planApproval = authority.planApproval();
        if (planApproval != null
                && (designApproval == null
                        || authority.plan() == null
                        || !planApproval.equals(
                                new PlanApproval(basis, designApproval.design(), authority.plan())))) {
            throw new IllegalArgumentException("Plan approval does not bind current inputs");
        }
        if (authority.execution() != null && planApproval == null) {
            throw new IllegalArgumentException("Execution evidence requires current approvals");
        }
        if (authority.validation() != null && authority.execution() == null) {
            throw new IllegalArgumentException("Validation evidence requires execution evidence");
        }
        if (authority.recordedWaiver() != null
                && !authority.recordedWaiver().equals(authority.waiverCandidate())) {
            throw new IllegalArgumentException("Recorded waiver does not match the eligible failure");
        }

        PendingInteraction pending = authority.pending();
        if (pending == null) {
            return;
        }
        DecisionBinding expected = switch (pending.kind()) {
            case DOCUMENT_READ_CONSENT, REMOTE_USE_CONSENT, DISCOVERY_ANSWER -> {
                if (!(pending.binding() instanceof ContentDecision content)) {
                    throw new IllegalArgumentException("Interaction must be content-bound");
                }
                if (!content.resumeInput().hasCapacity(1)) {
                    throw new IllegalArgumentException("Pending interaction has no response capacity");
                }
                yield pending.binding();
            }
            case DESIGN_APPROVAL -> new DesignDecisionBinding(
                    requireValue(basis, "Design decision has no authority basis"),
                    requireValue(authority.design(), "Design decision has no design"));
            case PLAN_APPROVAL -> new PlanDecisionBinding(
                    requireValue(basis, "Plan decision has no authority basis"),
                    requireValue(designApproval, "Plan decision has no design approval"),
                    requireValue(authority.plan(), "Plan decision has no plan"));
            case WAIVER -> requireValue(
                    authority.waiverCandidate(), "Waiver decision has no eligible failure");
        };
        if (!expected.equals(pending.binding())) {
            throw new IllegalArgumentException("Pending interaction does not bind current authority");
        }
    }

    private void requireStateBindings() {
        switch (state) {
            case CREATED,
                    CONTEXT_RESOLVING,
                    WAITING_FOR_DOCUMENT_CONSENT,
                    CONTEXT_FAILED_RETRYABLE ->
                requireAbsent(
                        authority.context(),
                        authority.basis(),
                        authority.design(),
                        authority.designApproval(),
                        authority.plan(),
                        authority.planApproval(),
                        authority.execution(),
                        authority.validation(),
                        authority.waiverCandidate(),
                        authority.recordedWaiver());
            case CONTEXT_RECORDED,
                    DISCOVERY_ANALYZING,
                    WAITING_FOR_REMOTE_USE_CONSENT,
                    WAITING_FOR_DISCOVERY_ANSWER,
                    DISCOVERY_FAILED_RETRYABLE -> {
                requireValue(authority.context(), "Discovery requires recorded context");
                requireAbsent(
                        authority.basis(),
                        authority.design(),
                        authority.designApproval(),
                        authority.plan(),
                        authority.planApproval(),
                        authority.execution(),
                        authority.validation(),
                        authority.waiverCandidate(),
                        authority.recordedWaiver());
            }
            case REQUIREMENTS_READY, DESIGN_RUNNING, DESIGN_FAILED_RETRYABLE -> {
                requireBasisOnly();
                requireAbsent(
                        authority.design(),
                        authority.designApproval(),
                        authority.plan(),
                        authority.planApproval(),
                        authority.execution(),
                        authority.validation(),
                        authority.waiverCandidate(),
                        authority.recordedWaiver());
            }
            case DESIGN_READY, WAITING_FOR_DESIGN_APPROVAL -> {
                requireBasisOnly();
                requireValue(authority.design(), "Design state requires a design");
                requireAbsent(
                        authority.designApproval(),
                        authority.plan(),
                        authority.planApproval(),
                        authority.execution(),
                        authority.validation(),
                        authority.waiverCandidate(),
                        authority.recordedWaiver());
            }
            case DESIGN_APPROVED -> {
                requireCurrentDesignApproval();
                requireAbsent(
                        authority.plan(),
                        authority.planApproval(),
                        authority.execution(),
                        authority.validation(),
                        authority.waiverCandidate(),
                        authority.recordedWaiver());
            }
            case PLAN_RUNNING, PLAN_FAILED_RETRYABLE -> {
                requireCurrentDesignApproval();
                requireAbsent(
                        authority.plan(),
                        authority.planApproval(),
                        authority.execution(),
                        authority.validation(),
                        authority.waiverCandidate(),
                        authority.recordedWaiver());
            }
            case PLAN_VALIDATED, WAITING_FOR_PLAN_APPROVAL -> {
                requireCurrentDesignApproval();
                requireValue(authority.plan(), "Plan state requires a validated plan");
                requireAbsent(
                        authority.planApproval(),
                        authority.execution(),
                        authority.validation(),
                        authority.waiverCandidate(),
                        authority.recordedWaiver());
            }
            case PLAN_APPROVED -> {
                requireCurrentPlanApproval();
                requireAbsent(
                        authority.execution(),
                        authority.validation(),
                        authority.waiverCandidate(),
                        authority.recordedWaiver());
            }
            case EXECUTE_RUNNING, EXECUTE_FAILED_RETRYABLE -> {
                requireCurrentPlanApproval();
                requireAbsent(
                        authority.execution(),
                        authority.validation(),
                        authority.waiverCandidate(),
                        authority.recordedWaiver());
            }
            case EXECUTE_VALIDATED -> {
                requireCurrentPlanApproval();
                requireValue(authority.execution(), "Execution result is missing");
                requireAbsent(
                        authority.validation(), authority.waiverCandidate(), authority.recordedWaiver());
            }
            case VALIDATE_RUNNING, VALIDATE_FAILED_RETRYABLE -> {
                requireCurrentPlanApproval();
                requireValue(authority.execution(), "Validation requires execution evidence");
                requireAbsent(
                        authority.validation(), authority.waiverCandidate(), authority.recordedWaiver());
            }
            case VALIDATE_PASSED -> {
                requireCurrentPlanApproval();
                requireValue(authority.execution(), "Validation requires execution evidence");
                requireValue(authority.validation(), "Passed validation requires evidence");
                requireAbsent(authority.waiverCandidate(), authority.recordedWaiver());
            }
            case WAIVER_ELIGIBLE, WAITING_FOR_WAIVER -> {
                requireCurrentPlanApproval();
                requireValue(authority.execution(), "Waiver requires execution evidence");
                requireValue(authority.waiverCandidate(), "Waiver eligibility is missing");
                requireAbsent(authority.validation(), authority.recordedWaiver());
            }
            case WAIVER_RECORDED,
                    WAIVER_STAMP_RUNNING,
                    WAIVER_STAMP_FAILED_RETRYABLE,
                    COMPLETED_WITH_WAIVER -> {
                requireCurrentPlanApproval();
                requireValue(authority.execution(), "Waiver requires execution evidence");
                WaiverBinding waiver
                        = requireValue(authority.waiverCandidate(), "Waiver eligibility is missing");
                if (!waiver.equals(authority.recordedWaiver())) {
                    throw new IllegalArgumentException("Waiver was not recorded");
                }
                requireAbsent(authority.validation());
            }
            case STAMP_RUNNING, STAMP_FAILED_RETRYABLE, COMPLETED -> {
                requireCurrentPlanApproval();
                requireValue(authority.execution(), "Stamp requires execution evidence");
                requireValue(authority.validation(), "Stamp requires validation evidence");
                requireAbsent(authority.waiverCandidate(), authority.recordedWaiver());
            }
            case FAILED_TERMINAL, ABORTED -> {
                // Failures may preserve any already validated descriptive binding, but never live authority.
            }
        }
    }

    private void requireBasisOnly() {
        requireValue(authority.context(), "Authority basis requires context");
        requireValue(authority.basis(), "Authority basis is missing");
    }

    private void requireCurrentDesignApproval() {
        requireBasisOnly();
        ContentId design = requireValue(authority.design(), "Design is missing");
        DesignApproval expected = new DesignApproval(authority.basis(), design);
        if (!expected.equals(authority.designApproval())) {
            throw new IllegalArgumentException("Current design approval is missing");
        }
    }

    private void requireCurrentPlanApproval() {
        requireCurrentDesignApproval();
        ContentId plan = requireValue(authority.plan(), "Plan is missing");
        PlanApproval expected
                = new PlanApproval(authority.basis(), authority.designApproval().design(), plan);
        if (!expected.equals(authority.planApproval())) {
            throw new IllegalArgumentException("Current plan approval is missing");
        }
    }

    private static <T> T requireValue(T value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private static void requireAbsent(Object... values) {
        for (Object value : values) {
            if (value != null) {
                throw new IllegalArgumentException("Ship state contains authority from a later phase");
            }
        }
    }
}
