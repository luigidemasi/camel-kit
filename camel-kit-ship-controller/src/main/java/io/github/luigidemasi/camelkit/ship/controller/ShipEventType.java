package io.github.luigidemasi.camelkit.ship.controller;

/** Closed event vocabulary accepted by the in-memory lifecycle authority. */
public enum ShipEventType {
    RUN_CREATED("run-created"),
    CONTEXT_RESOLUTION_STARTED("context-resolution-started"),
    DOCUMENT_CONSENT_REQUESTED("document-consent-requested"),
    DOCUMENT_CONSENT_ACCEPTED("document-consent-accepted"),
    DOCUMENT_CONSENT_DENIED("document-consent-denied"),
    CONTEXT_RECORDED("context-recorded"),
    DISCOVERY_STARTED("discovery-started"),
    REMOTE_USE_CONSENT_REQUESTED("remote-use-consent-requested"),
    REMOTE_USE_CONSENT_ACCEPTED("remote-use-consent-accepted"),
    REMOTE_USE_CONSENT_DENIED("remote-use-consent-denied"),
    DISCOVERY_QUESTION_PRESENTED("discovery-question-presented"),
    DISCOVERY_ANSWER_RECORDED("discovery-answer-recorded"),
    REQUIREMENTS_READY("requirements-ready"),
    DESIGN_STARTED("design-started"),
    DESIGN_READY("design-ready"),
    DESIGN_APPROVAL_REQUESTED("design-approval-requested"),
    DESIGN_APPROVED("design-approved"),
    DESIGN_APPROVAL_DENIED("design-approval-denied"),
    PLAN_STARTED("plan-started"),
    PLAN_VALIDATED("plan-validated"),
    PLAN_APPROVAL_REQUESTED("plan-approval-requested"),
    PLAN_APPROVED("plan-approved"),
    PLAN_APPROVAL_DENIED("plan-approval-denied"),
    EXECUTION_STARTED("execution-started"),
    EXECUTION_VALIDATED("execution-validated"),
    VALIDATION_STARTED("validation-started"),
    VALIDATION_PASSED("validation-passed"),
    WAIVABLE_FAILURE_RECORDED("waivable-failure-recorded"),
    WAIVER_REQUESTED("waiver-requested"),
    WAIVER_RECORDED("waiver-recorded"),
    WAIVER_DENIED("waiver-denied"),
    STAMP_STARTED("stamp-started"),
    WAIVER_STAMP_STARTED("waiver-stamp-started"),
    RUN_COMPLETED("run-completed"),
    RUN_COMPLETED_WITH_WAIVER("run-completed-with-waiver"),
    ATTEMPT_FAILED_RETRYABLE("attempt-failed-retryable"),
    RETRY_STARTED("retry-started"),
    REQUIREMENTS_INPUTS_CHANGED("requirements-inputs-changed"),
    DESIGN_INPUTS_CHANGED("design-inputs-changed"),
    PLAN_INPUTS_CHANGED("plan-inputs-changed"),
    RUN_FAILED_TERMINAL("run-failed-terminal"),
    RUN_ABORTED("run-aborted");

    private final String stableId;

    ShipEventType(String stableId) {
        this.stableId = stableId;
    }

    public String stableId() {
        return stableId;
    }

    public static ShipEventType fromStableId(String stableId) {
        for (ShipEventType event : values()) {
            if (event.stableId.equals(stableId)) {
                return event;
            }
        }
        throw new IllegalArgumentException("Unknown Ship event: " + stableId);
    }
}
