package io.github.luigidemasi.camelkit.ship.controller;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

/** Closed, side-effect-free lifecycle policy used only by controller authority code. */
final class ShipTransitionPolicy {

    private static final Map<ShipState, Map<ShipEventType, ShipState>> TRANSITIONS = transitions();

    private ShipTransitionPolicy() {
    }

    static ShipState requireNext(ShipState from, ShipEventType event) {
        if (from == null || event == null) {
            throw new IllegalArgumentException("Ship state and event are required");
        }
        ShipState next = TRANSITIONS.get(from).get(event);
        if (next == null) {
            throw new IllegalStateException("Event " + event + " is not legal from " + from);
        }
        return next;
    }

    static boolean canProduce(ShipEventType event, ShipState state) {
        if (event == null || state == null) {
            return false;
        }
        if (event == ShipEventType.RUN_CREATED) {
            return state == ShipState.CREATED;
        }
        return TRANSITIONS.values().stream()
                .anyMatch(events -> state == events.get(event));
    }

    private static Map<ShipState, Map<ShipEventType, ShipState>> transitions() {
        EnumMap<ShipState, Map<ShipEventType, ShipState>> transitions
                = new EnumMap<>(ShipState.class);
        for (ShipState state : ShipState.values()) {
            transitions.put(state, new EnumMap<>(ShipEventType.class));
        }

        put(transitions, ShipState.CREATED,
                ShipEventType.CONTEXT_RESOLUTION_STARTED, ShipState.CONTEXT_RESOLVING);
        put(transitions, ShipState.CONTEXT_RESOLVING,
                ShipEventType.DOCUMENT_CONSENT_REQUESTED, ShipState.WAITING_FOR_DOCUMENT_CONSENT);
        put(transitions, ShipState.CONTEXT_RESOLVING,
                ShipEventType.CONTEXT_RECORDED, ShipState.CONTEXT_RECORDED);
        put(transitions, ShipState.WAITING_FOR_DOCUMENT_CONSENT,
                ShipEventType.DOCUMENT_CONSENT_ACCEPTED, ShipState.CONTEXT_RESOLVING);
        put(transitions, ShipState.WAITING_FOR_DOCUMENT_CONSENT,
                ShipEventType.DOCUMENT_CONSENT_DENIED, ShipState.ABORTED);
        put(transitions, ShipState.CONTEXT_RECORDED,
                ShipEventType.DISCOVERY_STARTED, ShipState.DISCOVERY_ANALYZING);
        put(transitions, ShipState.DISCOVERY_ANALYZING,
                ShipEventType.REMOTE_USE_CONSENT_REQUESTED,
                ShipState.WAITING_FOR_REMOTE_USE_CONSENT);
        put(transitions, ShipState.WAITING_FOR_REMOTE_USE_CONSENT,
                ShipEventType.REMOTE_USE_CONSENT_ACCEPTED, ShipState.DISCOVERY_ANALYZING);
        put(transitions, ShipState.WAITING_FOR_REMOTE_USE_CONSENT,
                ShipEventType.REMOTE_USE_CONSENT_DENIED, ShipState.DISCOVERY_ANALYZING);
        put(transitions, ShipState.DISCOVERY_ANALYZING,
                ShipEventType.DISCOVERY_QUESTION_PRESENTED,
                ShipState.WAITING_FOR_DISCOVERY_ANSWER);
        put(transitions, ShipState.DISCOVERY_ANALYZING,
                ShipEventType.DISCOVERY_CONTINUED, ShipState.DISCOVERY_ANALYZING);
        put(transitions, ShipState.WAITING_FOR_DISCOVERY_ANSWER,
                ShipEventType.DISCOVERY_ANSWER_RECORDED, ShipState.DISCOVERY_ANALYZING);
        put(transitions, ShipState.DISCOVERY_ANALYZING,
                ShipEventType.GAP_REVIEW_STARTED, ShipState.REVIEW_RUNNING);
        put(transitions, ShipState.REVIEW_RUNNING,
                ShipEventType.GAP_REVIEW_REOPENED, ShipState.DISCOVERY_ANALYZING);
        put(transitions, ShipState.REVIEW_RUNNING,
                ShipEventType.REQUIREMENTS_READY, ShipState.REQUIREMENTS_READY);
        put(transitions, ShipState.REQUIREMENTS_READY,
                ShipEventType.DESIGN_STARTED, ShipState.DESIGN_RUNNING);
        put(transitions, ShipState.DESIGN_RUNNING,
                ShipEventType.DESIGN_READY, ShipState.DESIGN_READY);
        put(transitions, ShipState.DESIGN_RUNNING,
                ShipEventType.DESIGN_GAPS_FOUND, ShipState.DISCOVERY_ANALYZING);
        put(transitions, ShipState.DESIGN_READY,
                ShipEventType.DESIGN_APPROVAL_REQUESTED,
                ShipState.WAITING_FOR_DESIGN_APPROVAL);
        put(transitions, ShipState.WAITING_FOR_DESIGN_APPROVAL,
                ShipEventType.DESIGN_APPROVED, ShipState.DESIGN_APPROVED);
        put(transitions, ShipState.WAITING_FOR_DESIGN_APPROVAL,
                ShipEventType.DESIGN_APPROVAL_DENIED, ShipState.DESIGN_RUNNING);
        put(transitions, ShipState.WAITING_FOR_DESIGN_APPROVAL,
                ShipEventType.DESIGN_REQUIREMENTS_CHANGES_REQUESTED,
                ShipState.DISCOVERY_ANALYZING);
        put(transitions, ShipState.WAITING_FOR_DESIGN_APPROVAL,
                ShipEventType.DESIGN_APPROVAL_ABORTED, ShipState.ABORTED);
        put(transitions, ShipState.DESIGN_APPROVED,
                ShipEventType.PLAN_STARTED, ShipState.PLAN_RUNNING);
        put(transitions, ShipState.PLAN_RUNNING,
                ShipEventType.PLAN_VALIDATED, ShipState.PLAN_VALIDATED);
        put(transitions, ShipState.PLAN_VALIDATED,
                ShipEventType.PLAN_APPROVAL_REQUESTED,
                ShipState.WAITING_FOR_PLAN_APPROVAL);
        put(transitions, ShipState.WAITING_FOR_PLAN_APPROVAL,
                ShipEventType.PLAN_APPROVED, ShipState.PLAN_APPROVED);
        put(transitions, ShipState.WAITING_FOR_PLAN_APPROVAL,
                ShipEventType.PLAN_APPROVAL_DENIED, ShipState.PLAN_RUNNING);
        put(transitions, ShipState.WAITING_FOR_PLAN_APPROVAL,
                ShipEventType.PLAN_DESIGN_CHANGES_REQUESTED, ShipState.DESIGN_RUNNING);
        put(transitions, ShipState.WAITING_FOR_PLAN_APPROVAL,
                ShipEventType.PLAN_REQUIREMENTS_CHANGES_REQUESTED,
                ShipState.DISCOVERY_ANALYZING);
        put(transitions, ShipState.WAITING_FOR_PLAN_APPROVAL,
                ShipEventType.PLAN_APPROVAL_ABORTED, ShipState.ABORTED);
        put(transitions, ShipState.PLAN_APPROVED,
                ShipEventType.EXECUTION_STARTED, ShipState.EXECUTE_RUNNING);
        put(transitions, ShipState.EXECUTE_RUNNING,
                ShipEventType.EXECUTION_VALIDATED, ShipState.EXECUTE_VALIDATED);
        put(transitions, ShipState.EXECUTE_VALIDATED,
                ShipEventType.VALIDATION_STARTED, ShipState.VALIDATE_RUNNING);
        put(transitions, ShipState.VALIDATE_RUNNING,
                ShipEventType.VALIDATION_PASSED, ShipState.VALIDATE_PASSED);
        put(transitions, ShipState.VALIDATE_RUNNING,
                ShipEventType.WAIVABLE_FAILURE_RECORDED, ShipState.WAIVER_ELIGIBLE);
        put(transitions, ShipState.VALIDATE_PASSED,
                ShipEventType.STAMP_STARTED, ShipState.STAMP_RUNNING);
        put(transitions, ShipState.STAMP_RUNNING,
                ShipEventType.RUN_COMPLETED, ShipState.COMPLETED);
        put(transitions, ShipState.WAIVER_ELIGIBLE,
                ShipEventType.WAIVER_REQUESTED, ShipState.WAITING_FOR_WAIVER);
        put(transitions, ShipState.WAITING_FOR_WAIVER,
                ShipEventType.WAIVER_RECORDED, ShipState.WAIVER_RECORDED);
        put(transitions, ShipState.WAITING_FOR_WAIVER,
                ShipEventType.WAIVER_DENIED, ShipState.FAILED_TERMINAL);
        put(transitions, ShipState.WAIVER_RECORDED,
                ShipEventType.WAIVER_STAMP_STARTED, ShipState.WAIVER_STAMP_RUNNING);
        put(transitions, ShipState.WAIVER_STAMP_RUNNING,
                ShipEventType.RUN_COMPLETED_WITH_WAIVER, ShipState.COMPLETED_WITH_WAIVER);

        for (ShipPhase phase : ShipPhase.values()) {
            put(transitions, phase.runningState(),
                    ShipEventType.ATTEMPT_FAILED_RETRYABLE, phase.retryableFailureState());
            put(transitions, phase.retryableFailureState(),
                    ShipEventType.RETRY_STARTED, phase.runningState());
        }

        addInputInvalidation(
                transitions,
                ShipEventType.REQUIREMENTS_INPUTS_CHANGED,
                ShipState.DISCOVERY_ANALYZING,
                EnumSet.of(
                        ShipState.REQUIREMENTS_READY,
                        ShipState.DESIGN_RUNNING,
                        ShipState.DESIGN_READY,
                        ShipState.DESIGN_APPROVED,
                        ShipState.DESIGN_FAILED_RETRYABLE,
                        ShipState.PLAN_RUNNING,
                        ShipState.PLAN_VALIDATED,
                        ShipState.PLAN_APPROVED,
                        ShipState.PLAN_FAILED_RETRYABLE,
                        ShipState.EXECUTE_RUNNING,
                        ShipState.EXECUTE_VALIDATED,
                        ShipState.EXECUTE_FAILED_RETRYABLE,
                        ShipState.VALIDATE_RUNNING,
                        ShipState.VALIDATE_PASSED,
                        ShipState.VALIDATE_FAILED_RETRYABLE,
                        ShipState.WAIVER_ELIGIBLE,
                        ShipState.WAIVER_RECORDED,
                        ShipState.STAMP_RUNNING,
                        ShipState.STAMP_FAILED_RETRYABLE,
                        ShipState.WAIVER_STAMP_RUNNING,
                        ShipState.WAIVER_STAMP_FAILED_RETRYABLE));
        addInputInvalidation(
                transitions,
                ShipEventType.DESIGN_INPUTS_CHANGED,
                ShipState.DESIGN_RUNNING,
                EnumSet.of(
                        ShipState.DESIGN_RUNNING,
                        ShipState.DESIGN_READY,
                        ShipState.DESIGN_APPROVED,
                        ShipState.DESIGN_FAILED_RETRYABLE,
                        ShipState.PLAN_RUNNING,
                        ShipState.PLAN_VALIDATED,
                        ShipState.PLAN_APPROVED,
                        ShipState.PLAN_FAILED_RETRYABLE,
                        ShipState.EXECUTE_RUNNING,
                        ShipState.EXECUTE_VALIDATED,
                        ShipState.EXECUTE_FAILED_RETRYABLE,
                        ShipState.VALIDATE_RUNNING,
                        ShipState.VALIDATE_PASSED,
                        ShipState.VALIDATE_FAILED_RETRYABLE,
                        ShipState.WAIVER_ELIGIBLE,
                        ShipState.WAIVER_RECORDED,
                        ShipState.STAMP_RUNNING,
                        ShipState.STAMP_FAILED_RETRYABLE,
                        ShipState.WAIVER_STAMP_RUNNING,
                        ShipState.WAIVER_STAMP_FAILED_RETRYABLE));
        addInputInvalidation(
                transitions,
                ShipEventType.PLAN_INPUTS_CHANGED,
                ShipState.PLAN_RUNNING,
                EnumSet.of(
                        ShipState.PLAN_RUNNING,
                        ShipState.PLAN_VALIDATED,
                        ShipState.PLAN_APPROVED,
                        ShipState.PLAN_FAILED_RETRYABLE,
                        ShipState.EXECUTE_RUNNING,
                        ShipState.EXECUTE_VALIDATED,
                        ShipState.EXECUTE_FAILED_RETRYABLE,
                        ShipState.VALIDATE_RUNNING,
                        ShipState.VALIDATE_PASSED,
                        ShipState.VALIDATE_FAILED_RETRYABLE,
                        ShipState.WAIVER_ELIGIBLE,
                        ShipState.WAIVER_RECORDED,
                        ShipState.STAMP_RUNNING,
                        ShipState.STAMP_FAILED_RETRYABLE,
                        ShipState.WAIVER_STAMP_RUNNING,
                        ShipState.WAIVER_STAMP_FAILED_RETRYABLE));

        for (ShipState state : ShipState.values()) {
            if (!state.isTerminal()) {
                put(transitions, state, ShipEventType.RUN_FAILED_TERMINAL, ShipState.FAILED_TERMINAL);
                put(transitions, state, ShipEventType.RUN_ABORTED, ShipState.ABORTED);
            }
        }

        EnumMap<ShipState, Map<ShipEventType, ShipState>> immutable
                = new EnumMap<>(ShipState.class);
        transitions.forEach((state, events) -> immutable.put(state, Map.copyOf(events)));
        return Map.copyOf(immutable);
    }

    private static void addInputInvalidation(
            EnumMap<ShipState, Map<ShipEventType, ShipState>> transitions,
            ShipEventType event,
            ShipState target,
            EnumSet<ShipState> sources) {
        for (ShipState source : sources) {
            put(transitions, source, event, target);
        }
    }

    private static void put(
            EnumMap<ShipState, Map<ShipEventType, ShipState>> transitions,
            ShipState from,
            ShipEventType event,
            ShipState to) {
        ShipState previous = transitions.get(from).put(event, to);
        if (previous != null) {
            throw new IllegalStateException("Duplicate Ship transition for " + from + " and " + event);
        }
    }
}
