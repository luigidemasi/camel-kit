package io.github.luigidemasi.camelkit.ship.controller;

import java.io.IOException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Closed mapping between lifecycle events, authority commands, and controller data payloads. */
final class ShipStoredEventCodec {

    private static final Set<String> ENVELOPE_FIELDS = Set.of("authority", "data");

    private ShipStoredEventCodec() {
    }

    static JsonNode encode(ShipAuthorityCommand authority, ShipEventPayloads.Payload data) {
        Objects.requireNonNull(authority, "authority");
        Objects.requireNonNull(data, "data");
        Class<? extends ShipEventPayloads.Payload> expected = payloadType(authority.type());
        if (!expected.isInstance(data)) {
            throw new IllegalArgumentException(
                    "Event " + authority.type().stableId() + " requires " + expected.getSimpleName());
        }
        ObjectMapper mapper = ShipJson.mapper();
        ObjectNode envelope = mapper.createObjectNode();
        envelope.set("authority", authority.toJson());
        envelope.set("data", mapper.valueToTree(data));
        return envelope;
    }

    static StoredEvent decode(ShipEvent event) throws IOException {
        Objects.requireNonNull(event, "event");
        return decode(event.type(), event.payload());
    }

    static StoredEvent decode(ShipEventType type, JsonNode encodedPayload) throws IOException {
        Objects.requireNonNull(type, "type");
        JsonNode envelope = Objects.requireNonNull(encodedPayload, "encoded payload");
        if (!envelope.isObject()) {
            throw new IOException("Ship event payload envelope must be an object");
        }
        Set<String> fields = new HashSet<>();
        envelope.fieldNames().forEachRemaining(fields::add);
        if (!fields.equals(ENVELOPE_FIELDS)) {
            throw new IOException("Ship event payload envelope must contain exactly " + ENVELOPE_FIELDS);
        }
        ShipAuthorityCommand authority = ShipAuthorityCommand.fromJson(
                type, envelope.get("authority"));
        JsonNode dataNode = envelope.get("data");
        if (dataNode == null || !dataNode.isObject()) {
            throw new IOException("Ship event data must be an object");
        }
        try {
            ShipEventPayloads.Payload data = ShipJson.mapper().treeToValue(
                    dataNode, payloadType(type));
            return new StoredEvent(authority, data);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new IOException(
                    "Invalid typed data for Ship event " + type.stableId() + ": " + e.getMessage(), e);
        }
    }

    private static Class<? extends ShipEventPayloads.Payload> payloadType(ShipEventType type) {
        return switch (type) {
            case RUN_CREATED -> ShipEventPayloads.RunCreated.class;
            case CONTEXT_RESOLUTION_STARTED -> ShipEventPayloads.ContextResolutionStarted.class;
            case DOCUMENT_CONSENT_REQUESTED -> ShipEventPayloads.DocumentConsentRequested.class;
            case DOCUMENT_CONSENT_ACCEPTED, DOCUMENT_CONSENT_DENIED ->
                ShipEventPayloads.DocumentConsentRecorded.class;
            case CONTEXT_RECORDED -> ShipEventPayloads.ContextRecorded.class;
            case DISCOVERY_STARTED,
                    DESIGN_STARTED,
                    PLAN_STARTED,
                    EXECUTION_STARTED,
                    VALIDATION_STARTED,
                    RETRY_STARTED,
                    REQUIREMENTS_INPUTS_CHANGED,
                    DESIGN_INPUTS_CHANGED,
                    PLAN_INPUTS_CHANGED ->
                ShipEventPayloads.StageStarted.class;
            case STAMP_STARTED, WAIVER_STAMP_STARTED -> ShipEventPayloads.NoData.class;
            case REMOTE_USE_CONSENT_REQUESTED ->
                ShipEventPayloads.RemoteProviderConsentRequested.class;
            case REMOTE_USE_CONSENT_ACCEPTED, REMOTE_USE_CONSENT_DENIED ->
                ShipEventPayloads.RemoteProviderConsentRecorded.class;
            case DISCOVERY_QUESTION_PRESENTED ->
                ShipEventPayloads.DiscoveryQuestionPresented.class;
            case DISCOVERY_ANSWER_RECORDED -> ShipEventPayloads.DiscoveryAnswerRecorded.class;
            case DISCOVERY_CONTINUED,
                    GAP_REVIEW_STARTED,
                    GAP_REVIEW_REOPENED,
                    DESIGN_GAPS_FOUND,
                    REQUIREMENTS_READY,
                    DESIGN_READY,
                    PLAN_VALIDATED,
                    EXECUTION_VALIDATED,
                    VALIDATION_PASSED ->
                ShipEventPayloads.StageAccepted.class;
            case DESIGN_APPROVAL_REQUESTED -> ShipEventPayloads.DesignApprovalRequested.class;
            case DESIGN_APPROVED,
                    DESIGN_APPROVAL_DENIED,
                    DESIGN_REQUIREMENTS_CHANGES_REQUESTED,
                    DESIGN_APPROVAL_ABORTED ->
                ShipEventPayloads.DesignApprovalRecorded.class;
            case PLAN_APPROVAL_REQUESTED -> ShipEventPayloads.PlanApprovalRequested.class;
            case PLAN_APPROVED,
                    PLAN_APPROVAL_DENIED,
                    PLAN_DESIGN_CHANGES_REQUESTED,
                    PLAN_REQUIREMENTS_CHANGES_REQUESTED,
                    PLAN_APPROVAL_ABORTED ->
                ShipEventPayloads.PlanApprovalRecorded.class;
            case WAIVABLE_FAILURE_RECORDED,
                    ATTEMPT_FAILED_RETRYABLE,
                    RUN_FAILED_TERMINAL,
                    RUN_ABORTED ->
                ShipEventPayloads.Failure.class;
            case WAIVER_REQUESTED -> ShipEventPayloads.WaiverRequested.class;
            case WAIVER_RECORDED, WAIVER_DENIED -> ShipEventPayloads.WaiverRecorded.class;
            case RUN_COMPLETED, RUN_COMPLETED_WITH_WAIVER -> ShipEventPayloads.StampRecorded.class;
        };
    }

    record StoredEvent(ShipAuthorityCommand authority, ShipEventPayloads.Payload data) {

        StoredEvent {
            Objects.requireNonNull(authority, "authority");
            Objects.requireNonNull(data, "data");
        }
    }
}
