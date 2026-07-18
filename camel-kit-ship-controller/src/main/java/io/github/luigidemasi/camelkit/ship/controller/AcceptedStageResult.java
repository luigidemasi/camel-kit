package io.github.luigidemasi.camelkit.ship.controller;

import java.util.Objects;

/**
 * Accepted stage-result capabilities consumed by the lifecycle reducer.
 *
 * <p>
 * Production issuance is deliberately absent until the controller-owned evidence boundary is added. Worker-stage
 * results require accepted evidence; stamp completion requires controller-owned deterministic Stamp verification.
 * Package isolation is API hygiene only; it is not an authentication boundary.
 */
sealed abstract class AcceptedStageResult<T>
        permits AcceptedContextResult,
        AcceptedRequirementsResult,
        AcceptedDesignResult,
        AcceptedPlanResult,
        AcceptedExecutionResult,
        AcceptedValidationResult,
        AcceptedStampCompletion {

    private final Attempt attempt;
    private final T value;

    AcceptedStageResult(Attempt attempt, T value) {
        this.attempt = Objects.requireNonNull(attempt, "attempt");
        this.value = Objects.requireNonNull(value, "value");
    }

    final Attempt attempt() {
        return attempt;
    }

    final T value() {
        return value;
    }
}

final class AcceptedContextResult extends AcceptedStageResult<ContentId> {
    private AcceptedContextResult(Attempt attempt, ContentId context) {
        super(attempt, context);
    }
}

final class AcceptedRequirementsResult extends AcceptedStageResult<AuthorityBasis> {
    private AcceptedRequirementsResult(Attempt attempt, AuthorityBasis basis) {
        super(attempt, basis);
    }
}

final class AcceptedDesignResult extends AcceptedStageResult<ContentId> {
    private AcceptedDesignResult(Attempt attempt, ContentId design) {
        super(attempt, design);
    }
}

final class AcceptedPlanResult extends AcceptedStageResult<ContentId> {
    private AcceptedPlanResult(Attempt attempt, ContentId plan) {
        super(attempt, plan);
    }
}

final class AcceptedExecutionResult extends AcceptedStageResult<ContentId> {
    private AcceptedExecutionResult(Attempt attempt, ContentId execution) {
        super(attempt, execution);
    }
}

final class AcceptedValidationResult extends AcceptedStageResult<ContentId> {
    private AcceptedValidationResult(Attempt attempt, ContentId validation) {
        super(attempt, validation);
    }
}

final class AcceptedStampCompletion extends AcceptedStageResult<ContentId> {
    private AcceptedStampCompletion(Attempt attempt, ContentId evidence) {
        super(attempt, evidence);
    }
}
