package io.github.luigidemasi.camelkit.ship.controller;

import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShipTransitionPolicyTest {

    private static final int EXPECTED_STATE_COUNT = 39;
    private static final int EXPECTED_EVENT_COUNT = 51;

    @Test
    void transitionPolicyMatchesIndependentClosedMatrix() {
        assertEquals(EXPECTED_STATE_COUNT, ShipState.values().length, "Classify every new Ship state here");
        assertEquals(EXPECTED_EVENT_COUNT, ShipEventType.values().length, "Classify every new Ship event here");
        Map<ShipState, Map<ShipEventType, ShipState>> expected = expectedTransitions();

        for (ShipState state : ShipState.values()) {
            for (ShipEventType event : ShipEventType.values()) {
                ShipState target = expected.get(state).get(event);
                if (target == null) {
                    assertThrows(
                            IllegalStateException.class,
                            () -> ShipTransitionPolicy.requireNext(state, event),
                            state + " must reject " + event);
                } else {
                    assertEquals(
                            target,
                            ShipTransitionPolicy.requireNext(state, event),
                            state + " must handle " + event + " exactly");
                }
            }
        }
        assertThrows(IllegalArgumentException.class,
                () -> ShipTransitionPolicy.requireNext(null, ShipEventType.RUN_ABORTED));
        assertThrows(IllegalArgumentException.class,
                () -> ShipTransitionPolicy.requireNext(ShipState.CREATED, null));
    }

    @Test
    void bothApprovalsAndWaiverDominateTheirProtectedPaths() {
        Map<ShipState, Map<ShipEventType, ShipState>> graph = expectedTransitions();

        assertTrue(reachable(graph, Set.of()).contains(ShipState.COMPLETED));
        assertTrue(reachable(graph, Set.of()).contains(ShipState.COMPLETED_WITH_WAIVER));
        assertFalse(reachable(graph, Set.of(ShipEventType.DESIGN_APPROVED))
                .contains(ShipState.EXECUTE_RUNNING));
        assertFalse(reachable(graph, Set.of(ShipEventType.PLAN_APPROVED))
                .contains(ShipState.EXECUTE_RUNNING));
        assertFalse(reachable(graph, Set.of(ShipEventType.DESIGN_APPROVED))
                .contains(ShipState.COMPLETED));
        assertFalse(reachable(graph, Set.of(ShipEventType.PLAN_APPROVED))
                .contains(ShipState.COMPLETED));
        assertFalse(reachable(graph, Set.of(ShipEventType.WAIVER_RECORDED))
                .contains(ShipState.COMPLETED_WITH_WAIVER));

        assertThrows(IllegalStateException.class,
                () -> ShipTransitionPolicy.requireNext(
                        ShipState.PLAN_VALIDATED, ShipEventType.EXECUTION_STARTED));
        assertThrows(IllegalStateException.class,
                () -> ShipTransitionPolicy.requireNext(
                        ShipState.WAIVER_ELIGIBLE, ShipEventType.RUN_COMPLETED_WITH_WAIVER));
        assertThrows(IllegalStateException.class,
                () -> ShipTransitionPolicy.requireNext(
                        ShipState.WAIVER_RECORDED, ShipEventType.RUN_COMPLETED_WITH_WAIVER));
    }

    @Test
    void pendingAndTerminalStatesAreStructurallyClosed() {
        assertEquals(
                Map.of(
                        ShipState.WAITING_FOR_DOCUMENT_CONSENT,
                        ShipInteractionKind.DOCUMENT_READ_CONSENT,
                        ShipState.WAITING_FOR_REMOTE_USE_CONSENT,
                        ShipInteractionKind.REMOTE_USE_CONSENT,
                        ShipState.WAITING_FOR_DISCOVERY_ANSWER,
                        ShipInteractionKind.DISCOVERY_ANSWER,
                        ShipState.WAITING_FOR_DESIGN_APPROVAL,
                        ShipInteractionKind.DESIGN_APPROVAL,
                        ShipState.WAITING_FOR_PLAN_APPROVAL,
                        ShipInteractionKind.PLAN_APPROVAL,
                        ShipState.WAITING_FOR_WAIVER,
                        ShipInteractionKind.WAIVER),
                java.util.Arrays.stream(ShipState.values())
                        .filter(state -> state.pendingInteraction().isPresent())
                        .collect(java.util.stream.Collectors.toMap(
                                state -> state, state -> state.pendingInteraction().orElseThrow())));

        for (ShipState terminal : EnumSet.of(
                ShipState.COMPLETED,
                ShipState.COMPLETED_WITH_WAIVER,
                ShipState.FAILED_TERMINAL,
                ShipState.ABORTED)) {
            assertTrue(terminal.isTerminal());
            for (ShipEventType event : ShipEventType.values()) {
                assertThrows(
                        IllegalStateException.class,
                        () -> ShipTransitionPolicy.requireNext(terminal, event));
            }
        }
        assertEquals(
                Set.of(
                        ShipState.COMPLETED,
                        ShipState.COMPLETED_WITH_WAIVER,
                        ShipState.FAILED_TERMINAL,
                        ShipState.ABORTED),
                java.util.Arrays.stream(ShipState.values())
                        .filter(ShipState::isTerminal)
                        .collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void inputChangesCannotAbandonAPendingInteraction() {
        for (ShipState pending : EnumSet.of(
                ShipState.WAITING_FOR_DESIGN_APPROVAL,
                ShipState.WAITING_FOR_PLAN_APPROVAL,
                ShipState.WAITING_FOR_WAIVER)) {
            for (ShipEventType changed : EnumSet.of(
                    ShipEventType.REQUIREMENTS_INPUTS_CHANGED,
                    ShipEventType.DESIGN_INPUTS_CHANGED,
                    ShipEventType.PLAN_INPUTS_CHANGED)) {
                assertThrows(
                        IllegalStateException.class,
                        () -> ShipTransitionPolicy.requireNext(pending, changed),
                        pending + " must not abandon its interaction via " + changed);
            }
        }
    }

    @Test
    void stableIdsRoundTripAndUnknownIdsFailClosed() {
        Set<String> stateIds = new HashSet<>();
        for (ShipState state : ShipState.values()) {
            assertTrue(stateIds.add(state.stableId()), "Duplicate state ID " + state.stableId());
            assertSame(state, ShipState.fromStableId(state.stableId()));
        }
        Set<String> eventIds = new HashSet<>();
        for (ShipEventType event : ShipEventType.values()) {
            assertTrue(eventIds.add(event.stableId()), "Duplicate event ID " + event.stableId());
            assertSame(event, ShipEventType.fromStableId(event.stableId()));
        }
        Set<String> interactionIds = new HashSet<>();
        for (ShipInteractionKind interaction : ShipInteractionKind.values()) {
            assertTrue(
                    interactionIds.add(interaction.stableId()),
                    "Duplicate interaction ID " + interaction.stableId());
            assertSame(interaction, ShipInteractionKind.fromStableId(interaction.stableId()));
        }

        for (String unknown : new String[]{null, "", "future-state", "CREATED", "created "}) {
            assertThrows(IllegalArgumentException.class, () -> ShipState.fromStableId(unknown));
            assertThrows(IllegalArgumentException.class, () -> ShipEventType.fromStableId(unknown));
            assertThrows(IllegalArgumentException.class, () -> ShipInteractionKind.fromStableId(unknown));
        }
    }

    private static Set<ShipState> reachable(
            Map<ShipState, Map<ShipEventType, ShipState>> graph, Set<ShipEventType> excluded) {
        Set<ShipState> reached = EnumSet.of(ShipState.CREATED);
        ArrayDeque<ShipState> pending = new ArrayDeque<>();
        pending.add(ShipState.CREATED);
        while (!pending.isEmpty()) {
            ShipState state = pending.removeFirst();
            graph.get(state).forEach((event, target) -> {
                if (!excluded.contains(event) && reached.add(target)) {
                    pending.addLast(target);
                }
            });
        }
        return reached;
    }

    private static Map<ShipState, Map<ShipEventType, ShipState>> expectedTransitions() {
        EnumMap<ShipState, Map<ShipEventType, ShipState>> expected = new EnumMap<>(ShipState.class);
        for (ShipState state : ShipState.values()) {
            expected.put(state, new EnumMap<>(ShipEventType.class));
        }

        legal(expected, ShipState.CREATED,
                ShipEventType.CONTEXT_RESOLUTION_STARTED, ShipState.CONTEXT_RESOLVING);
        legal(expected, ShipState.CONTEXT_RESOLVING,
                ShipEventType.DOCUMENT_CONSENT_REQUESTED, ShipState.WAITING_FOR_DOCUMENT_CONSENT,
                ShipEventType.CONTEXT_RECORDED, ShipState.CONTEXT_RECORDED);
        legal(expected, ShipState.WAITING_FOR_DOCUMENT_CONSENT,
                ShipEventType.DOCUMENT_CONSENT_ACCEPTED, ShipState.CONTEXT_RESOLVING,
                ShipEventType.DOCUMENT_CONSENT_DENIED, ShipState.ABORTED);
        legal(expected, ShipState.CONTEXT_RECORDED,
                ShipEventType.DISCOVERY_STARTED, ShipState.DISCOVERY_ANALYZING);
        legal(expected, ShipState.DISCOVERY_ANALYZING,
                ShipEventType.REMOTE_USE_CONSENT_REQUESTED,
                ShipState.WAITING_FOR_REMOTE_USE_CONSENT,
                ShipEventType.DISCOVERY_QUESTION_PRESENTED,
                ShipState.WAITING_FOR_DISCOVERY_ANSWER,
                ShipEventType.DISCOVERY_CONTINUED, ShipState.DISCOVERY_ANALYZING,
                ShipEventType.GAP_REVIEW_STARTED, ShipState.REVIEW_RUNNING);
        legal(expected, ShipState.WAITING_FOR_REMOTE_USE_CONSENT,
                ShipEventType.REMOTE_USE_CONSENT_ACCEPTED, ShipState.DISCOVERY_ANALYZING,
                ShipEventType.REMOTE_USE_CONSENT_DENIED, ShipState.DISCOVERY_ANALYZING);
        legal(expected, ShipState.WAITING_FOR_DISCOVERY_ANSWER,
                ShipEventType.DISCOVERY_ANSWER_RECORDED, ShipState.DISCOVERY_ANALYZING);
        legal(expected, ShipState.REVIEW_RUNNING,
                ShipEventType.GAP_REVIEW_REOPENED, ShipState.DISCOVERY_ANALYZING,
                ShipEventType.REQUIREMENTS_READY, ShipState.REQUIREMENTS_READY);
        legal(expected, ShipState.REQUIREMENTS_READY,
                ShipEventType.DESIGN_STARTED, ShipState.DESIGN_RUNNING);
        legal(expected, ShipState.DESIGN_RUNNING,
                ShipEventType.DESIGN_READY, ShipState.DESIGN_READY,
                ShipEventType.DESIGN_GAPS_FOUND, ShipState.DISCOVERY_ANALYZING);
        legal(expected, ShipState.DESIGN_READY,
                ShipEventType.DESIGN_APPROVAL_REQUESTED,
                ShipState.WAITING_FOR_DESIGN_APPROVAL);
        legal(expected, ShipState.WAITING_FOR_DESIGN_APPROVAL,
                ShipEventType.DESIGN_APPROVED, ShipState.DESIGN_APPROVED,
                ShipEventType.DESIGN_APPROVAL_DENIED, ShipState.DESIGN_RUNNING,
                ShipEventType.DESIGN_REQUIREMENTS_CHANGES_REQUESTED,
                ShipState.DISCOVERY_ANALYZING,
                ShipEventType.DESIGN_APPROVAL_ABORTED, ShipState.ABORTED);
        legal(expected, ShipState.DESIGN_APPROVED,
                ShipEventType.PLAN_STARTED, ShipState.PLAN_RUNNING);
        legal(expected, ShipState.PLAN_RUNNING,
                ShipEventType.PLAN_VALIDATED, ShipState.PLAN_VALIDATED);
        legal(expected, ShipState.PLAN_VALIDATED,
                ShipEventType.PLAN_APPROVAL_REQUESTED,
                ShipState.WAITING_FOR_PLAN_APPROVAL);
        legal(expected, ShipState.WAITING_FOR_PLAN_APPROVAL,
                ShipEventType.PLAN_APPROVED, ShipState.PLAN_APPROVED,
                ShipEventType.PLAN_APPROVAL_DENIED, ShipState.PLAN_RUNNING,
                ShipEventType.PLAN_DESIGN_CHANGES_REQUESTED, ShipState.DESIGN_RUNNING,
                ShipEventType.PLAN_REQUIREMENTS_CHANGES_REQUESTED,
                ShipState.DISCOVERY_ANALYZING,
                ShipEventType.PLAN_APPROVAL_ABORTED, ShipState.ABORTED);
        legal(expected, ShipState.PLAN_APPROVED,
                ShipEventType.EXECUTION_STARTED, ShipState.EXECUTE_RUNNING);
        legal(expected, ShipState.EXECUTE_RUNNING,
                ShipEventType.EXECUTION_VALIDATED, ShipState.EXECUTE_VALIDATED);
        legal(expected, ShipState.EXECUTE_VALIDATED,
                ShipEventType.VALIDATION_STARTED, ShipState.VALIDATE_RUNNING);
        legal(expected, ShipState.VALIDATE_RUNNING,
                ShipEventType.VALIDATION_PASSED, ShipState.VALIDATE_PASSED,
                ShipEventType.WAIVABLE_FAILURE_RECORDED, ShipState.WAIVER_ELIGIBLE);
        legal(expected, ShipState.VALIDATE_PASSED,
                ShipEventType.STAMP_STARTED, ShipState.STAMP_RUNNING);
        legal(expected, ShipState.STAMP_RUNNING,
                ShipEventType.RUN_COMPLETED, ShipState.COMPLETED);
        legal(expected, ShipState.WAIVER_ELIGIBLE,
                ShipEventType.WAIVER_REQUESTED, ShipState.WAITING_FOR_WAIVER);
        legal(expected, ShipState.WAITING_FOR_WAIVER,
                ShipEventType.WAIVER_RECORDED, ShipState.WAIVER_RECORDED,
                ShipEventType.WAIVER_DENIED, ShipState.FAILED_TERMINAL);
        legal(expected, ShipState.WAIVER_RECORDED,
                ShipEventType.WAIVER_STAMP_STARTED, ShipState.WAIVER_STAMP_RUNNING);
        legal(expected, ShipState.WAIVER_STAMP_RUNNING,
                ShipEventType.RUN_COMPLETED_WITH_WAIVER, ShipState.COMPLETED_WITH_WAIVER);

        for (RetryCase retry : Set.of(
                new RetryCase(ShipState.CONTEXT_RESOLVING, ShipState.CONTEXT_FAILED_RETRYABLE),
                new RetryCase(ShipState.DISCOVERY_ANALYZING, ShipState.DISCOVERY_FAILED_RETRYABLE),
                new RetryCase(ShipState.REVIEW_RUNNING, ShipState.REVIEW_FAILED_RETRYABLE),
                new RetryCase(ShipState.DESIGN_RUNNING, ShipState.DESIGN_FAILED_RETRYABLE),
                new RetryCase(ShipState.PLAN_RUNNING, ShipState.PLAN_FAILED_RETRYABLE),
                new RetryCase(ShipState.EXECUTE_RUNNING, ShipState.EXECUTE_FAILED_RETRYABLE),
                new RetryCase(ShipState.VALIDATE_RUNNING, ShipState.VALIDATE_FAILED_RETRYABLE),
                new RetryCase(ShipState.STAMP_RUNNING, ShipState.STAMP_FAILED_RETRYABLE),
                new RetryCase(
                        ShipState.WAIVER_STAMP_RUNNING,
                        ShipState.WAIVER_STAMP_FAILED_RETRYABLE))) {
            legal(expected, retry.running(),
                    ShipEventType.ATTEMPT_FAILED_RETRYABLE, retry.failed());
            legal(expected, retry.failed(), ShipEventType.RETRY_STARTED, retry.running());
        }

        invalidate(
                expected,
                ShipEventType.REQUIREMENTS_INPUTS_CHANGED,
                ShipState.DISCOVERY_ANALYZING,
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
                ShipState.WAIVER_STAMP_FAILED_RETRYABLE);
        invalidate(
                expected,
                ShipEventType.DESIGN_INPUTS_CHANGED,
                ShipState.DESIGN_RUNNING,
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
                ShipState.WAIVER_STAMP_FAILED_RETRYABLE);
        invalidate(
                expected,
                ShipEventType.PLAN_INPUTS_CHANGED,
                ShipState.PLAN_RUNNING,
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
                ShipState.WAIVER_STAMP_FAILED_RETRYABLE);

        for (ShipState state : ShipState.values()) {
            if (!state.isTerminal()) {
                legal(expected, state,
                        ShipEventType.RUN_FAILED_TERMINAL, ShipState.FAILED_TERMINAL,
                        ShipEventType.RUN_ABORTED, ShipState.ABORTED);
            }
        }
        return expected;
    }

    private static void invalidate(
            EnumMap<ShipState, Map<ShipEventType, ShipState>> expected,
            ShipEventType event,
            ShipState target,
            ShipState... sources) {
        for (ShipState source : sources) {
            legal(expected, source, event, target);
        }
    }

    private static void legal(
            EnumMap<ShipState, Map<ShipEventType, ShipState>> expected,
            ShipState from,
            Object... eventTargets) {
        assertEquals(0, eventTargets.length % 2);
        for (int index = 0; index < eventTargets.length; index += 2) {
            ShipEventType event = (ShipEventType) eventTargets[index];
            ShipState target = (ShipState) eventTargets[index + 1];
            assertNull(expected.get(from).put(event, target), "Duplicate test transition " + from + '/' + event);
        }
    }

    private record RetryCase(ShipState running, ShipState failed) {
    }
}
