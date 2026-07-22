package io.github.luigidemasi.camelkit.ship.controller;

import java.io.IOException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Closed controller-authored lifecycle command persisted inside one authenticated event. */
final class ShipAuthorityCommand {

    private static final Set<ShipEventType> VALUE_EVENTS = Set.of(
            ShipEventType.CONTEXT_RESOLUTION_STARTED,
            ShipEventType.DOCUMENT_CONSENT_REQUESTED,
            ShipEventType.CONTEXT_RECORDED,
            ShipEventType.REMOTE_USE_CONSENT_REQUESTED,
            ShipEventType.DISCOVERY_QUESTION_PRESENTED,
            ShipEventType.DISCOVERY_ANSWER_RECORDED,
            ShipEventType.DISCOVERY_CONTINUED,
            ShipEventType.GAP_REVIEW_STARTED,
            ShipEventType.DESIGN_READY,
            ShipEventType.PLAN_VALIDATED,
            ShipEventType.EXECUTION_VALIDATED,
            ShipEventType.VALIDATION_PASSED,
            ShipEventType.RUN_COMPLETED,
            ShipEventType.RUN_COMPLETED_WITH_WAIVER);
    private static final Set<ShipEventType> DECISION_EVENTS = Set.of(
            ShipEventType.DOCUMENT_CONSENT_ACCEPTED,
            ShipEventType.DOCUMENT_CONSENT_DENIED,
            ShipEventType.REMOTE_USE_CONSENT_ACCEPTED,
            ShipEventType.REMOTE_USE_CONSENT_DENIED,
            ShipEventType.DESIGN_APPROVED,
            ShipEventType.DESIGN_APPROVAL_DENIED,
            ShipEventType.DESIGN_REQUIREMENTS_CHANGES_REQUESTED,
            ShipEventType.DESIGN_APPROVAL_ABORTED,
            ShipEventType.PLAN_APPROVED,
            ShipEventType.PLAN_APPROVAL_DENIED,
            ShipEventType.PLAN_DESIGN_CHANGES_REQUESTED,
            ShipEventType.PLAN_REQUIREMENTS_CHANGES_REQUESTED,
            ShipEventType.PLAN_APPROVAL_ABORTED,
            ShipEventType.WAIVER_RECORDED,
            ShipEventType.WAIVER_DENIED);
    private static final Set<ShipEventType> FEEDBACK_EVENTS = Set.of(
            ShipEventType.DESIGN_APPROVAL_DENIED,
            ShipEventType.DESIGN_REQUIREMENTS_CHANGES_REQUESTED,
            ShipEventType.PLAN_APPROVAL_DENIED,
            ShipEventType.PLAN_DESIGN_CHANGES_REQUESTED,
            ShipEventType.PLAN_REQUIREMENTS_CHANGES_REQUESTED);
    private static final Set<ShipEventType> ACCEPT_EVENTS = Set.of(
            ShipEventType.DOCUMENT_CONSENT_ACCEPTED,
            ShipEventType.REMOTE_USE_CONSENT_ACCEPTED,
            ShipEventType.DESIGN_APPROVED,
            ShipEventType.PLAN_APPROVED,
            ShipEventType.WAIVER_RECORDED);

    private final ShipEventType type;
    private final ContentId value;
    private final AuthorityBasis basis;
    private final ContentId feedback;
    private final WaiverCommand waiver;

    private ShipAuthorityCommand(
                                 ShipEventType type,
                                 ContentId value,
                                 AuthorityBasis basis,
                                 ContentId feedback,
                                 WaiverCommand waiver) {
        this.type = Objects.requireNonNull(type, "type");
        this.value = value;
        this.basis = basis;
        this.feedback = feedback;
        this.waiver = waiver;
        requireShape();
    }

    static ShipAuthorityCommand empty(ShipEventType type) {
        return new ShipAuthorityCommand(type, null, null, null, null);
    }

    static ShipAuthorityCommand value(ShipEventType type, String value) {
        return new ShipAuthorityCommand(type, new ContentId(value), null, null, null);
    }

    static ShipAuthorityCommand requirements(AuthorityBasis basis) {
        return new ShipAuthorityCommand(
                ShipEventType.REQUIREMENTS_READY, null, Objects.requireNonNull(basis, "basis"), null, null);
    }

    static ShipAuthorityCommand review(String candidate, String feedback) {
        return stageFeedback(ShipEventType.GAP_REVIEW_REOPENED, candidate, feedback);
    }

    static ShipAuthorityCommand designGaps(String candidate, String feedback) {
        return stageFeedback(ShipEventType.DESIGN_GAPS_FOUND, candidate, feedback);
    }

    private static ShipAuthorityCommand stageFeedback(
            ShipEventType type, String candidate, String feedback) {
        return new ShipAuthorityCommand(
                type,
                new ContentId(candidate),
                null,
                new ContentId(feedback),
                null);
    }

    static ShipAuthorityCommand decision(ShipEventType type, String feedback) {
        return new ShipAuthorityCommand(
                type, null, null, feedback == null ? null : new ContentId(feedback), null);
    }

    static ShipAuthorityCommand waiver(
            String stableCheckId, String evidence, String policy) {
        return new ShipAuthorityCommand(
                ShipEventType.WAIVABLE_FAILURE_RECORDED,
                null,
                null,
                null,
                new WaiverCommand(
                        new ContentId(stableCheckId), new ContentId(evidence), new ContentId(policy)));
    }

    ShipEventType type() {
        return type;
    }

    ContentId content() {
        return value;
    }

    AuthorityBasis basis() {
        return basis;
    }

    ContentId feedback() {
        return feedback;
    }

    ShipRun apply(ShipRun previous) {
        Objects.requireNonNull(previous, "previous");
        return switch (type) {
            case RUN_CREATED -> throw new IllegalStateException("RUN_CREATED has no predecessor");
            case CONTEXT_RESOLUTION_STARTED -> ShipLifecycleReducer.startContextResolution(previous, value);
            case DOCUMENT_CONSENT_REQUESTED -> ShipLifecycleReducer.requestDocumentConsent(
                    previous, ShipLifecycleReducer.attempt(previous), value);
            case DOCUMENT_CONSENT_ACCEPTED,
                    DOCUMENT_CONSENT_DENIED,
                    REMOTE_USE_CONSENT_ACCEPTED,
                    REMOTE_USE_CONSENT_DENIED,
                    WAIVER_RECORDED,
                    WAIVER_DENIED ->
                ShipLifecycleReducer.applyVerifiedDecision(previous, type, feedback);
            case CONTEXT_RECORDED -> ShipLifecycleReducer.recordContext(
                    previous,
                    AcceptedStageResultFactory.context(ShipLifecycleReducer.attempt(previous), value));
            case DISCOVERY_STARTED -> ShipLifecycleReducer.startDiscovery(previous);
            case REMOTE_USE_CONSENT_REQUESTED -> ShipLifecycleReducer.requestRemoteUseConsent(
                    previous, ShipLifecycleReducer.attempt(previous), value);
            case DISCOVERY_QUESTION_PRESENTED -> ShipLifecycleReducer.requestDiscoveryAnswer(
                    previous, ShipLifecycleReducer.attempt(previous), value);
            case DISCOVERY_ANSWER_RECORDED ->
                ShipLifecycleReducer.applyVerifiedDiscoveryAnswer(previous, value);
            case DISCOVERY_CONTINUED -> ShipLifecycleReducer.continueDiscovery(
                    previous,
                    AcceptedStageResultFactory.discoveryContinuation(
                            ShipLifecycleReducer.attempt(previous), value));
            case GAP_REVIEW_STARTED -> ShipLifecycleReducer.startGapReview(
                    previous,
                    AcceptedStageResultFactory.discoveryCandidate(
                            ShipLifecycleReducer.attempt(previous), value));
            case GAP_REVIEW_REOPENED -> ShipLifecycleReducer.reopenGapReview(
                    previous,
                    AcceptedStageResultFactory.review(
                            ShipLifecycleReducer.attempt(previous), value, feedback));
            case REQUIREMENTS_READY -> ShipLifecycleReducer.requirementsReady(
                    previous,
                    AcceptedStageResultFactory.requirements(
                            ShipLifecycleReducer.attempt(previous), basis));
            case DESIGN_STARTED -> ShipLifecycleReducer.startDesign(previous);
            case DESIGN_READY -> ShipLifecycleReducer.designReady(
                    previous,
                    AcceptedStageResultFactory.design(ShipLifecycleReducer.attempt(previous), value));
            case DESIGN_GAPS_FOUND -> ShipLifecycleReducer.designGapsFound(
                    previous,
                    AcceptedStageResultFactory.designGaps(
                            ShipLifecycleReducer.attempt(previous), value, feedback));
            case DESIGN_APPROVAL_REQUESTED -> ShipLifecycleReducer.requestDesignApproval(previous);
            case DESIGN_APPROVED,
                    DESIGN_APPROVAL_DENIED,
                    DESIGN_REQUIREMENTS_CHANGES_REQUESTED,
                    DESIGN_APPROVAL_ABORTED ->
                ShipLifecycleReducer.applyVerifiedDesignDecision(previous, type, feedback);
            case PLAN_STARTED -> ShipLifecycleReducer.startPlan(previous);
            case PLAN_VALIDATED -> ShipLifecycleReducer.planValidated(
                    previous,
                    AcceptedStageResultFactory.plan(ShipLifecycleReducer.attempt(previous), value));
            case PLAN_APPROVAL_REQUESTED -> ShipLifecycleReducer.requestPlanApproval(previous);
            case PLAN_APPROVED,
                    PLAN_APPROVAL_DENIED,
                    PLAN_DESIGN_CHANGES_REQUESTED,
                    PLAN_REQUIREMENTS_CHANGES_REQUESTED,
                    PLAN_APPROVAL_ABORTED ->
                ShipLifecycleReducer.applyVerifiedPlanDecision(previous, type, feedback);
            case EXECUTION_STARTED -> ShipLifecycleReducer.startExecution(previous);
            case EXECUTION_VALIDATED -> ShipLifecycleReducer.executionValidated(
                    previous,
                    AcceptedStageResultFactory.execution(ShipLifecycleReducer.attempt(previous), value));
            case VALIDATION_STARTED -> ShipLifecycleReducer.startValidation(previous);
            case VALIDATION_PASSED -> ShipLifecycleReducer.validationPassed(
                    previous,
                    AcceptedStageResultFactory.validation(ShipLifecycleReducer.attempt(previous), value));
            case WAIVABLE_FAILURE_RECORDED -> ShipLifecycleReducer.applyVerifiedWaiverEligibility(
                    previous, waiver.stableCheckId(), waiver.evidence(), waiver.policy());
            case WAIVER_REQUESTED -> ShipLifecycleReducer.requestWaiver(previous);
            case STAMP_STARTED -> ShipLifecycleReducer.startStamp(previous);
            case WAIVER_STAMP_STARTED -> ShipLifecycleReducer.startWaiverStamp(previous);
            case RUN_COMPLETED -> ShipLifecycleReducer.completeStamp(
                    previous,
                    AcceptedStageResultFactory.stamp(ShipLifecycleReducer.attempt(previous), value));
            case RUN_COMPLETED_WITH_WAIVER -> ShipLifecycleReducer.completeWaiverStamp(
                    previous,
                    AcceptedStageResultFactory.stamp(ShipLifecycleReducer.attempt(previous), value));
            case ATTEMPT_FAILED_RETRYABLE -> ShipLifecycleReducer.failRetryable(
                    previous, ShipLifecycleReducer.attempt(previous));
            case RETRY_STARTED -> ShipLifecycleReducer.retry(
                    previous, ShipLifecycleReducer.retryBinding(previous));
            case REQUIREMENTS_INPUTS_CHANGED -> ShipLifecycleReducer.requirementsInputsChanged(previous);
            case DESIGN_INPUTS_CHANGED -> ShipLifecycleReducer.designInputsChanged(previous);
            case PLAN_INPUTS_CHANGED -> ShipLifecycleReducer.planInputsChanged(previous);
            case RUN_FAILED_TERMINAL -> ShipLifecycleReducer.failTerminal(previous);
            case RUN_ABORTED -> ShipLifecycleReducer.abort(previous);
        };
    }

    ShipRun apply(ShipRun previous, AuthorityHeadId authenticatedHead) {
        return ShipLifecycleReducer.rebindAuthorityHead(apply(previous), authenticatedHead);
    }

    JsonNode toJson() {
        ObjectNode node = ShipJson.mapper().createObjectNode();
        if (VALUE_EVENTS.contains(type)) {
            node.put("value", value.value());
        } else if (type == ShipEventType.GAP_REVIEW_REOPENED
                || type == ShipEventType.DESIGN_GAPS_FOUND) {
            node.put("candidate", value.value());
            node.put("feedback", feedback.value());
        } else if (type == ShipEventType.REQUIREMENTS_READY) {
            ObjectNode valueNode = node.putObject("basis");
            valueNode.put("context", basis.context().value());
            valueNode.put("ledger", basis.ledger().value());
            valueNode.put("ledgerRevision", basis.ledgerRevision());
            valueNode.put("requirements", basis.requirements().value());
            valueNode.put("policy", basis.policy().value());
            valueNode.put("baseline", basis.baseline().value());
        } else if (DECISION_EVENTS.contains(type)) {
            node.put("decision", ACCEPT_EVENTS.contains(type) ? "accepted" : "denied");
            if (feedback == null) {
                node.putNull("feedback");
            } else {
                node.put("feedback", feedback.value());
            }
        } else if (type == ShipEventType.WAIVABLE_FAILURE_RECORDED) {
            ObjectNode waiverNode = node.putObject("waiver");
            waiverNode.put("stableCheckId", waiver.stableCheckId().value());
            waiverNode.put("evidence", waiver.evidence().value());
            waiverNode.put("policy", waiver.policy().value());
        }
        return node;
    }

    static ShipAuthorityCommand fromJson(ShipEventType type, JsonNode node) throws IOException {
        requireObject(node, "authority command");
        try {
            if (VALUE_EVENTS.contains(type)) {
                requireFields(node, Set.of("value"), "authority command");
                return value(type, text(node, "value"));
            }
            if (type == ShipEventType.GAP_REVIEW_REOPENED
                    || type == ShipEventType.DESIGN_GAPS_FOUND) {
                requireFields(node, Set.of("candidate", "feedback"), "authority command");
                return type == ShipEventType.GAP_REVIEW_REOPENED
                        ? review(text(node, "candidate"), text(node, "feedback"))
                        : designGaps(text(node, "candidate"), text(node, "feedback"));
            }
            if (type == ShipEventType.REQUIREMENTS_READY) {
                requireFields(node, Set.of("basis"), "authority command");
                JsonNode basis = node.get("basis");
                requireObject(basis, "authority basis");
                requireFields(
                        basis,
                        Set.of("context", "ledger", "ledgerRevision", "requirements", "policy", "baseline"),
                        "authority basis");
                JsonNode revision = basis.get("ledgerRevision");
                if (!revision.isIntegralNumber() || !revision.canConvertToLong()) {
                    throw new IOException("Authority basis ledgerRevision must be an integer");
                }
                return requirements(new AuthorityBasis(
                        new ContentId(text(basis, "context")),
                        new ContentId(text(basis, "ledger")),
                        revision.longValue(),
                        new ContentId(text(basis, "requirements")),
                        new ContentId(text(basis, "policy")),
                        new ContentId(text(basis, "baseline"))));
            }
            if (DECISION_EVENTS.contains(type)) {
                requireFields(node, Set.of("decision", "feedback"), "authority command");
                String expected = ACCEPT_EVENTS.contains(type) ? "accepted" : "denied";
                if (!expected.equals(text(node, "decision"))) {
                    throw new IOException("Authority decision does not match event " + type.stableId());
                }
                JsonNode feedback = node.get("feedback");
                if (feedback == null || !(feedback.isNull() || feedback.isTextual())) {
                    throw new IOException("Authority decision feedback must be a string or null");
                }
                return decision(type, feedback.isNull() ? null : feedback.textValue());
            }
            if (type == ShipEventType.WAIVABLE_FAILURE_RECORDED) {
                requireFields(node, Set.of("waiver"), "authority command");
                JsonNode waiver = node.get("waiver");
                requireObject(waiver, "waiver command");
                requireFields(
                        waiver, Set.of("stableCheckId", "evidence", "policy"), "waiver command");
                return waiver(
                        text(waiver, "stableCheckId"),
                        text(waiver, "evidence"),
                        text(waiver, "policy"));
            }
            requireFields(node, Set.of(), "authority command");
            return empty(type);
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid authority command for " + type.stableId() + ": " + e.getMessage(), e);
        }
    }

    private void requireShape() {
        boolean valid = VALUE_EVENTS.contains(type)
                ? value != null && basis == null && feedback == null && waiver == null
                : type == ShipEventType.GAP_REVIEW_REOPENED
                        || type == ShipEventType.DESIGN_GAPS_FOUND
                        ? value != null && basis == null && feedback != null && waiver == null
                : type == ShipEventType.REQUIREMENTS_READY
                        ? value == null && basis != null && feedback == null && waiver == null
                : DECISION_EVENTS.contains(type)
                        ? value == null
                                && basis == null
                                && waiver == null
                                && (feedback != null) == FEEDBACK_EVENTS.contains(type)
                : type == ShipEventType.WAIVABLE_FAILURE_RECORDED
                        ? value == null && basis == null && feedback == null && waiver != null
                : value == null && basis == null && feedback == null && waiver == null;
        if (!valid) {
            throw new IllegalArgumentException("Authority command fields do not match event " + type.stableId());
        }
    }

    private static void requireObject(JsonNode value, String label) throws IOException {
        if (value == null || !value.isObject()) {
            throw new IOException(label + " must be an object");
        }
    }

    private static void requireFields(JsonNode value, Set<String> expected, String label) throws IOException {
        Set<String> actual = new HashSet<>();
        value.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(expected)) {
            throw new IOException(label + " must contain exactly " + expected);
        }
    }

    private static String text(JsonNode value, String field) throws IOException {
        JsonNode member = value.get(field);
        if (member == null || !member.isTextual()) {
            throw new IOException(field + " must be a string");
        }
        return member.textValue();
    }

    private record WaiverCommand(
            ContentId stableCheckId, ContentId evidence, ContentId policy) {

        private WaiverCommand {
            Objects.requireNonNull(stableCheckId, "stableCheckId");
            Objects.requireNonNull(evidence, "evidence");
            Objects.requireNonNull(policy, "policy");
        }
    }
}
