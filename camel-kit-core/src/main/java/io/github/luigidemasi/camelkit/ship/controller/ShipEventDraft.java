package io.github.luigidemasi.camelkit.ship.controller;

import java.time.Instant;
import java.util.Objects;

import io.github.luigidemasi.camelkit.ship.ShipDigest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;

/** Controller-supplied event data; storage assigns sequence and integrity fields. */
record ShipEventDraft(
        ShipEventType type,
        ShipState state,
        AuthorityHeadId previousAuthorityHead,
        AuthorityHeadId authorityHead,
        Instant occurredAt,
        JsonNode payload) {

    ShipEventDraft {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(authorityHead, "authorityHead");
        Objects.requireNonNull(occurredAt, "occurredAt");
        payload = ShipEventCodec.copyAndValidatePayload(
                payload == null ? NullNode.getInstance() : payload);
    }

    @Override
    public JsonNode payload() {
        return payload.deepCopy();
    }
}

/** Authenticated compare-and-append position, including lifecycle-authority branch identity. */
record ShipEventHead(long sequence, String eventDigest, AuthorityHeadId authorityHead) {

    static final String GENESIS_DIGEST = "sha256:" + "0".repeat(64);

    ShipEventHead {
        if (sequence < 0) {
            throw new IllegalArgumentException("Ship event-head sequence must not be negative");
        }
        if (!ShipDigest.isSha256(eventDigest)) {
            throw new IllegalArgumentException("Ship event head must contain a SHA-256 digest");
        }
        if (sequence == 0) {
            if (!GENESIS_DIGEST.equals(eventDigest) || authorityHead != null) {
                throw new IllegalArgumentException("The genesis Ship event head is not canonical");
            }
        } else if (authorityHead == null) {
            throw new IllegalArgumentException("A non-empty Ship event head requires authority identity");
        }
    }

    static ShipEventHead genesis() {
        return new ShipEventHead(0, GENESIS_DIGEST, null);
    }

    static ShipEventHead from(ShipEvent event) {
        Objects.requireNonNull(event, "event");
        return new ShipEventHead(event.sequence(), event.eventDigest(), event.authorityHead());
    }
}

/** One immutable, authenticated event in the controller-owned ledger. */
record ShipEvent(
        int schemaVersion,
        ShipRunId runId,
        long sequence,
        String previousEventDigest,
        ShipEventType type,
        ShipState previousState,
        ShipState state,
        AuthorityHeadId previousAuthorityHead,
        AuthorityHeadId authorityHead,
        Instant occurredAt,
        JsonNode payload,
        String eventDigest,
        String hmac) {

    static final int SCHEMA_VERSION = 1;

    ShipEvent {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported Ship event schema version: " + schemaVersion);
        }
        Objects.requireNonNull(runId, "runId");
        if (sequence < 1) {
            throw new IllegalArgumentException("Ship event sequence must be positive");
        }
        if (!ShipDigest.isSha256(previousEventDigest)) {
            throw new IllegalArgumentException("Previous event digest must be SHA-256");
        }
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(authorityHead, "authorityHead");
        Objects.requireNonNull(occurredAt, "occurredAt");
        if ((previousState == null) != (previousAuthorityHead == null)
                || (sequence == 1) != (previousAuthorityHead == null)) {
            throw new IllegalArgumentException("Ship event predecessor fields do not match its sequence");
        }
        payload = ShipEventCodec.copyAndValidatePayload(
                payload == null ? NullNode.getInstance() : payload);
        if (!ShipDigest.isSha256(eventDigest) || !ShipDigest.isHmacSha256(hmac)) {
            throw new IllegalArgumentException("Ship event integrity fields are invalid");
        }
    }

    @Override
    public JsonNode payload() {
        return payload.deepCopy();
    }
}
