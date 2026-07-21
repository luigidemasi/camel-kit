package io.github.luigidemasi.camelkit.ship.controller;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

final class AuthorityHeadId {
    private static final String STORAGE_PREFIX = "head-";

    private final UUID value;

    private AuthorityHeadId(UUID value) {
        this.value = Objects.requireNonNull(value, "value");
    }

    static AuthorityHeadId create() {
        return new AuthorityHeadId(UUID.randomUUID());
    }

    static AuthorityHeadId fromStorageId(String storageId) {
        if (storageId == null || !storageId.matches("head-[0-9a-f]{32}")) {
            throw new IllegalArgumentException("Invalid Ship authority head ID");
        }
        String compact = storageId.substring(STORAGE_PREFIX.length());
        String canonical = compact.substring(0, 8)
                           + "-" + compact.substring(8, 12)
                           + "-" + compact.substring(12, 16)
                           + "-" + compact.substring(16, 20)
                           + "-" + compact.substring(20);
        return new AuthorityHeadId(UUID.fromString(canonical));
    }

    String storageId() {
        return STORAGE_PREFIX + value.toString().replace("-", "");
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof AuthorityHeadId that && value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }
}

record ContentId(String value) {
    ContentId {
        if (value == null || value.isBlank() || value.length() > 512) {
            throw new IllegalArgumentException("Authority content ID must contain 1 to 512 characters");
        }
    }
}

record AuthorityBasis(
        ContentId context,
        ContentId ledger,
        long ledgerRevision,
        ContentId requirements,
        ContentId policy,
        ContentId baseline) {
    AuthorityBasis {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(ledger, "ledger");
        if (ledgerRevision < 0) {
            throw new IllegalArgumentException("Ledger revision must not be negative");
        }
        Objects.requireNonNull(requirements, "requirements");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(baseline, "baseline");
    }
}

sealed interface DecisionBinding
        permits ContentDecision, DesignDecisionBinding, PlanDecisionBinding, WaiverBinding {
}

sealed interface PhaseBinding
        permits ContentBinding,
        ConsentOutcomeBinding,
        DiscoveryAnswerBinding,
        ApprovalFeedbackBinding {
}

record ContentBinding(ContentId content) implements PhaseBinding {
    ContentBinding {
        Objects.requireNonNull(content, "content");
    }
}

record ConsentOutcomeBinding(
        ShipInteractionKind kind,
        ContentId subject,
        InteractionDecisionValue decision) implements PhaseBinding {
    ConsentOutcomeBinding {
        if (kind != ShipInteractionKind.DOCUMENT_READ_CONSENT
                && kind != ShipInteractionKind.REMOTE_USE_CONSENT) {
            throw new IllegalArgumentException("Consent outcome requires a consent interaction kind");
        }
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(decision, "decision");
    }
}

record DiscoveryAnswerBinding(ContentId question, ContentId answer) implements PhaseBinding {
    DiscoveryAnswerBinding {
        Objects.requireNonNull(question, "question");
        Objects.requireNonNull(answer, "answer");
    }
}

record ApprovalFeedbackBinding(
        ShipInteractionKind kind,
        ContentId rejectedArtifact,
        ContentId feedback) implements PhaseBinding {
    ApprovalFeedbackBinding {
        if (kind != ShipInteractionKind.DESIGN_APPROVAL
                && kind != ShipInteractionKind.PLAN_APPROVAL) {
            throw new IllegalArgumentException("Approval feedback requires a design or plan interaction");
        }
        Objects.requireNonNull(rejectedArtifact, "rejectedArtifact");
        Objects.requireNonNull(feedback, "feedback");
    }
}

record PhaseInput(List<PhaseBinding> bindings) {
    PhaseInput {
        bindings = List.copyOf(bindings);
        if (bindings.isEmpty() || bindings.size() > 256) {
            throw new IllegalArgumentException("Phase input must contain 1 to 256 bindings");
        }
        if (bindings.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Phase input cannot contain null bindings");
        }
    }

    static PhaseInput initial(ContentId binding) {
        return new PhaseInput(List.of(new ContentBinding(binding)));
    }

    PhaseInput appendConsent(
            ShipInteractionKind kind,
            ContentId subject,
            InteractionDecisionValue decision) {
        return append(new ConsentOutcomeBinding(kind, subject, decision));
    }

    PhaseInput appendDiscoveryAnswer(ContentId question, ContentId answer) {
        return append(new DiscoveryAnswerBinding(question, answer));
    }

    void requireCapacity(int additionalBindings) {
        if (!hasCapacity(additionalBindings)) {
            throw new IllegalStateException("Phase input has no capacity for the interaction response");
        }
    }

    boolean hasCapacity(int additionalBindings) {
        return additionalBindings >= 0 && additionalBindings <= 256 - bindings.size();
    }

    private PhaseInput append(PhaseBinding binding) {
        Objects.requireNonNull(binding, "binding");
        if (bindings.size() == 256) {
            throw new IllegalStateException("Phase input binding limit exceeded");
        }
        java.util.ArrayList<PhaseBinding> appended = new java.util.ArrayList<>(bindings);
        appended.add(binding);
        return new PhaseInput(appended);
    }
}

record ContentDecision(ContentId subject, PhaseInput resumeInput) implements DecisionBinding {
    ContentDecision {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(resumeInput, "resumeInput");
    }
}

record DesignDecisionBinding(AuthorityBasis basis, ContentId design) implements DecisionBinding {
    DesignDecisionBinding {
        Objects.requireNonNull(basis, "basis");
        Objects.requireNonNull(design, "design");
    }
}

record PlanDecisionBinding(AuthorityBasis basis, DesignApproval designApproval, ContentId plan)
        implements
            DecisionBinding {
    PlanDecisionBinding {
        Objects.requireNonNull(basis, "basis");
        Objects.requireNonNull(designApproval, "designApproval");
        Objects.requireNonNull(plan, "plan");
        if (!basis.equals(designApproval.basis())) {
            throw new IllegalArgumentException("Plan decision must bind the current design approval");
        }
    }
}

record WaiverBinding(ContentId stableCheckId, ContentId evidence, ContentId policy)
        implements
            DecisionBinding {
    WaiverBinding {
        Objects.requireNonNull(stableCheckId, "stableCheckId");
        Objects.requireNonNull(evidence, "evidence");
        Objects.requireNonNull(policy, "policy");
    }
}

/** Production issuance is deliberately absent until protected policy enforcement is added. */
final class PolicyWaiverEligibility {
    private final ShipRunId runId;
    private final Attempt validationAttempt;
    private final WaiverBinding waiver;

    private PolicyWaiverEligibility(
                                    ShipRunId runId, Attempt validationAttempt, WaiverBinding waiver) {
        this.runId = Objects.requireNonNull(runId, "runId");
        this.validationAttempt = Objects.requireNonNull(validationAttempt, "validationAttempt");
        this.waiver = Objects.requireNonNull(waiver, "waiver");
    }

    ShipRunId runId() {
        return runId;
    }

    Attempt validationAttempt() {
        return validationAttempt;
    }

    WaiverBinding waiver() {
        return waiver;
    }
}

record PendingInteraction(
        ShipRunId runId,
        long requestRevision,
        AuthorityHeadId requestHead,
        ShipInteractionKind kind,
        DecisionBinding binding) {
    PendingInteraction {
        Objects.requireNonNull(runId, "runId");
        if (requestRevision < 0) {
            throw new IllegalArgumentException("Request revision must not be negative");
        }
        Objects.requireNonNull(requestHead, "requestHead");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(binding, "binding");
    }
}

enum InteractionDecisionValue {
    ACCEPTED,
    DENIED
}

/** Production issuance is deliberately absent until protected response verification is added. */
final class InteractionDecision {
    private final PendingInteraction request;
    private final InteractionDecisionValue value;
    private final ContentId feedback;

    private InteractionDecision(
                                PendingInteraction request,
                                InteractionDecisionValue value,
                                ContentId feedback) {
        this.request = Objects.requireNonNull(request, "request");
        this.value = Objects.requireNonNull(value, "value");
        boolean feedbackRequired = value == InteractionDecisionValue.DENIED
                && (request.kind() == ShipInteractionKind.DESIGN_APPROVAL
                        || request.kind() == ShipInteractionKind.PLAN_APPROVAL);
        if (feedbackRequired != (feedback != null)) {
            throw new IllegalArgumentException(
                    "Feedback is required only for denied design or plan approval");
        }
        this.feedback = feedback;
    }

    PendingInteraction request() {
        return request;
    }

    InteractionDecisionValue value() {
        return value;
    }

    ContentId feedback() {
        return feedback;
    }
}

/** Production issuance is deliberately absent until protected response verification is added. */
final class DiscoveryAnswer {
    private final PendingInteraction request;
    private final ContentId answer;

    private DiscoveryAnswer(PendingInteraction request, ContentId answer) {
        this.request = Objects.requireNonNull(request, "request");
        this.answer = Objects.requireNonNull(answer, "answer");
    }

    PendingInteraction request() {
        return request;
    }

    ContentId answer() {
        return answer;
    }
}

enum ShipPhase {
    CONTEXT(ShipState.CONTEXT_RESOLVING, ShipState.CONTEXT_FAILED_RETRYABLE),
    DISCOVERY(ShipState.DISCOVERY_ANALYZING, ShipState.DISCOVERY_FAILED_RETRYABLE),
    DESIGN(ShipState.DESIGN_RUNNING, ShipState.DESIGN_FAILED_RETRYABLE),
    PLAN(ShipState.PLAN_RUNNING, ShipState.PLAN_FAILED_RETRYABLE),
    EXECUTE(ShipState.EXECUTE_RUNNING, ShipState.EXECUTE_FAILED_RETRYABLE),
    VALIDATE(ShipState.VALIDATE_RUNNING, ShipState.VALIDATE_FAILED_RETRYABLE),
    STAMP(ShipState.STAMP_RUNNING, ShipState.STAMP_FAILED_RETRYABLE),
    WAIVER_STAMP(ShipState.WAIVER_STAMP_RUNNING, ShipState.WAIVER_STAMP_FAILED_RETRYABLE);

    private final ShipState runningState;
    private final ShipState retryableFailureState;

    ShipPhase(ShipState runningState, ShipState retryableFailureState) {
        this.runningState = runningState;
        this.retryableFailureState = retryableFailureState;
    }

    ShipState runningState() {
        return runningState;
    }

    ShipState retryableFailureState() {
        return retryableFailureState;
    }

    static ShipPhase requireRunningState(ShipState state) {
        for (ShipPhase phase : values()) {
            if (phase.runningState == state) {
                return phase;
            }
        }
        throw new IllegalStateException("State does not own a retryable attempt: " + state);
    }
}

record Attempt(
        ShipRunId runId,
        AuthorityHeadId authorityHead,
        long number,
        ShipPhase phase,
        PhaseInput input) {
    Attempt {
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(authorityHead, "authorityHead");
        if (number <= 0) {
            throw new IllegalArgumentException("Attempt number must be positive");
        }
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(input, "input");
    }
}

record RetryBinding(
        Attempt failedAttempt,
        long failureRevision,
        AuthorityHeadId failureHead,
        ShipState resumeState) {
    RetryBinding {
        Objects.requireNonNull(failedAttempt, "failedAttempt");
        if (failureRevision < 0) {
            throw new IllegalArgumentException("Failure revision must not be negative");
        }
        Objects.requireNonNull(failureHead, "failureHead");
        Objects.requireNonNull(resumeState, "resumeState");
        if (failedAttempt.phase().runningState() != resumeState) {
            throw new IllegalArgumentException("Retry must resume the failed phase");
        }
    }
}

record DesignApproval(AuthorityBasis basis, ContentId design) {
    DesignApproval {
        Objects.requireNonNull(basis, "basis");
        Objects.requireNonNull(design, "design");
    }
}

record PlanApproval(AuthorityBasis basis, ContentId design, ContentId plan) {
    PlanApproval {
        Objects.requireNonNull(basis, "basis");
        Objects.requireNonNull(design, "design");
        Objects.requireNonNull(plan, "plan");
    }
}

record StampCompletionBinding(ShipPhase phase, ContentId evidence) {
    StampCompletionBinding {
        if (phase != ShipPhase.STAMP && phase != ShipPhase.WAIVER_STAMP) {
            throw new IllegalArgumentException("Completion evidence must come from a stamp phase");
        }
        Objects.requireNonNull(evidence, "evidence");
    }
}

record AuthorityData(
        ContentId context,
        AuthorityBasis basis,
        ContentId design,
        DesignApproval designApproval,
        ContentId plan,
        PlanApproval planApproval,
        ContentId execution,
        ContentId validation,
        StampCompletionBinding stampCompletion,
        PendingInteraction pending,
        Attempt attempt,
        RetryBinding retry,
        WaiverBinding waiverCandidate,
        WaiverBinding recordedWaiver,
        long lastAttemptNumber) {
    AuthorityData {
        if (lastAttemptNumber < 0) {
            throw new IllegalArgumentException("Last attempt number must not be negative");
        }
    }

    static AuthorityData empty() {
        return new AuthorityData(
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
                null,
                null,
                null,
                0);
    }
}
