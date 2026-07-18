package io.github.luigidemasi.camelkit.ship.controller;

import java.util.Optional;

/** Immutable lifecycle states visible to Ship observers. */
public enum ShipState {
    CREATED("created"),
    CONTEXT_RESOLVING("context-resolving"),
    WAITING_FOR_DOCUMENT_CONSENT(
                                 "waiting-for-document-consent", ShipInteractionKind.DOCUMENT_READ_CONSENT),
    CONTEXT_RECORDED("context-recorded"),
    DISCOVERY_ANALYZING("discovery-analyzing"),
    WAITING_FOR_REMOTE_USE_CONSENT(
                                   "waiting-for-remote-use-consent", ShipInteractionKind.REMOTE_USE_CONSENT),
    WAITING_FOR_DISCOVERY_ANSWER(
                                 "waiting-for-discovery-answer", ShipInteractionKind.DISCOVERY_ANSWER),
    REQUIREMENTS_READY("requirements-ready"),
    DESIGN_RUNNING("design-running"),
    DESIGN_READY("design-ready"),
    WAITING_FOR_DESIGN_APPROVAL(
                                "waiting-for-design-approval", ShipInteractionKind.DESIGN_APPROVAL),
    DESIGN_APPROVED("design-approved"),
    PLAN_RUNNING("plan-running"),
    PLAN_VALIDATED("plan-validated"),
    WAITING_FOR_PLAN_APPROVAL("waiting-for-plan-approval", ShipInteractionKind.PLAN_APPROVAL),
    PLAN_APPROVED("plan-approved"),
    EXECUTE_RUNNING("execute-running"),
    EXECUTE_VALIDATED("execute-validated"),
    VALIDATE_RUNNING("validate-running"),
    VALIDATE_PASSED("validate-passed"),
    WAIVER_ELIGIBLE("waiver-eligible"),
    WAITING_FOR_WAIVER("waiting-for-waiver", ShipInteractionKind.WAIVER),
    WAIVER_RECORDED("waiver-recorded"),
    STAMP_RUNNING("stamp-running"),
    WAIVER_STAMP_RUNNING("waiver-stamp-running"),
    CONTEXT_FAILED_RETRYABLE("context-failed-retryable"),
    DISCOVERY_FAILED_RETRYABLE("discovery-failed-retryable"),
    DESIGN_FAILED_RETRYABLE("design-failed-retryable"),
    PLAN_FAILED_RETRYABLE("plan-failed-retryable"),
    EXECUTE_FAILED_RETRYABLE("execute-failed-retryable"),
    VALIDATE_FAILED_RETRYABLE("validate-failed-retryable"),
    STAMP_FAILED_RETRYABLE("stamp-failed-retryable"),
    WAIVER_STAMP_FAILED_RETRYABLE("waiver-stamp-failed-retryable"),
    COMPLETED("completed", true),
    COMPLETED_WITH_WAIVER("completed-with-waiver", true),
    FAILED_TERMINAL("failed-terminal", true),
    ABORTED("aborted", true);

    private final String stableId;
    private final boolean terminal;
    private final ShipInteractionKind pendingInteraction;

    ShipState(String stableId) {
        this(stableId, false, null);
    }

    ShipState(String stableId, boolean terminal) {
        this(stableId, terminal, null);
    }

    ShipState(String stableId, ShipInteractionKind pendingInteraction) {
        this(stableId, false, pendingInteraction);
    }

    ShipState(String stableId, boolean terminal, ShipInteractionKind pendingInteraction) {
        this.stableId = stableId;
        this.terminal = terminal;
        this.pendingInteraction = pendingInteraction;
    }

    public String stableId() {
        return stableId;
    }

    public boolean isTerminal() {
        return terminal;
    }

    public Optional<ShipInteractionKind> pendingInteraction() {
        return Optional.ofNullable(pendingInteraction);
    }

    public static ShipState fromStableId(String stableId) {
        for (ShipState state : values()) {
            if (state.stableId.equals(stableId)) {
                return state;
            }
        }
        throw new IllegalArgumentException("Unknown Ship state: " + stableId);
    }
}
