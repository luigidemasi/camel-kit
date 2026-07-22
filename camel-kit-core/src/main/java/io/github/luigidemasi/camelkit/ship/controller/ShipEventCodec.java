package io.github.luigidemasi.camelkit.ship.controller;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

import io.github.luigidemasi.camelkit.ship.ShipDigest;

import com.fasterxml.jackson.core.ErrorReportConfiguration;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.StreamWriteConstraints;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Canonical, bounded, strict-UTF-8 JSON encoding for authenticated Ship storage records. */
final class ShipEventCodec {

    static final int MAX_NESTING_DEPTH = 64;
    static final int MAX_STRING_CHARS = 1024 * 1024;
    static final int MAX_NAME_CHARS = 1024;
    static final long MAX_TOKENS = 100_000;

    private static final Set<String> EVENT_FIELDS = Set.of(
            "schemaVersion",
            "runId",
            "sequence",
            "previousEventDigest",
            "type",
            "previousState",
            "state",
            "previousAuthorityHead",
            "authorityHead",
            "occurredAt",
            "payload",
            "eventDigest",
            "hmac");
    private static final Set<String> HEAD_FIELDS = Set.of(
            "schemaVersion", "runId", "sequence", "eventDigest", "authorityHead", "hmac");
    private static final ObjectMapper MAPPER = new ObjectMapper(
            JsonFactory.builder()
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .streamReadConstraints(StreamReadConstraints.builder()
                            .maxDocumentLength(FileShipEventStore.MAX_EVENT_BYTES)
                            .maxTokenCount(MAX_TOKENS)
                            .maxNestingDepth(MAX_NESTING_DEPTH)
                            .maxStringLength(MAX_STRING_CHARS)
                            .maxNameLength(MAX_NAME_CHARS)
                            .maxNumberLength(128)
                            .build())
                    .streamWriteConstraints(StreamWriteConstraints.builder()
                            .maxNestingDepth(MAX_NESTING_DEPTH)
                            .build())
                    .errorReportConfiguration(ErrorReportConfiguration.builder()
                            .maxErrorTokenLength(256)
                            .maxRawContentLength(1024)
                            .build())
                    .build());

    private ShipEventCodec() {
    }

    static byte[] encodeUnsigned(UnsignedEvent event) throws IOException {
        return encodeCanonical(unsignedNode(event));
    }

    static byte[] encodeUnsigned(ShipEvent event) throws IOException {
        return encodeUnsigned(UnsignedEvent.from(event));
    }

    static byte[] encode(ShipEvent event) throws IOException {
        ObjectNode node = unsignedNode(UnsignedEvent.from(event));
        node.put("eventDigest", event.eventDigest());
        node.put("hmac", event.hmac());
        return encodeCanonical(node);
    }

    static ShipEvent decode(byte[] bytes) throws IOException {
        ObjectNode node = parseObject(bytes, EVENT_FIELDS, "Ship event");
        try {
            ShipEvent event = new ShipEvent(
                    requiredInt(node, "schemaVersion"),
                    ShipRunId.fromStorageId(requiredText(node, "runId")),
                    requiredLong(node, "sequence"),
                    requiredText(node, "previousEventDigest"),
                    ShipEventType.fromStableId(requiredText(node, "type")),
                    nullableState(node, "previousState"),
                    ShipState.fromStableId(requiredText(node, "state")),
                    nullableAuthorityHead(node, "previousAuthorityHead"),
                    AuthorityHeadId.fromStorageId(requiredText(node, "authorityHead")),
                    canonicalInstant(requiredText(node, "occurredAt")),
                    node.get("payload"),
                    requiredText(node, "eventDigest"),
                    requiredText(node, "hmac"));
            if (!Arrays.equals(bytes, encode(event))) {
                throw new ShipEventStoreException("Ship event JSON is not canonical");
            }
            return event;
        } catch (ShipEventStoreException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw new ShipEventStoreException("Invalid Ship event: " + e.getMessage(), e);
        }
    }

    static byte[] encodeUnsignedHead(ShipRunId runId, ShipEventHead head) throws IOException {
        return encodeCanonical(headNode(runId, head));
    }

    static byte[] encodeHead(ShipRunId runId, ShipEventHead head, String hmac) throws IOException {
        if (!ShipDigest.isHmacSha256(hmac)) {
            throw new IllegalArgumentException("Invalid Ship event-head HMAC");
        }
        ObjectNode node = headNode(runId, head);
        node.put("hmac", hmac);
        return encodeCanonical(node);
    }

    static HeadEnvelope decodeHead(byte[] bytes) throws IOException {
        ObjectNode node = parseObject(bytes, HEAD_FIELDS, "Ship event head");
        try {
            ShipRunId runId = ShipRunId.fromStorageId(requiredText(node, "runId"));
            ShipEventHead head = new ShipEventHead(
                    requiredLong(node, "sequence"),
                    requiredText(node, "eventDigest"),
                    nullableAuthorityHead(node, "authorityHead"));
            String hmac = requiredText(node, "hmac");
            if (!ShipDigest.isHmacSha256(hmac)) {
                throw new IllegalArgumentException("Invalid Ship event-head HMAC");
            }
            HeadEnvelope result = new HeadEnvelope(runId, head, hmac);
            if (!Arrays.equals(bytes, encodeHead(runId, head, hmac))) {
                throw new ShipEventStoreException("Ship event-head JSON is not canonical");
            }
            return result;
        } catch (ShipEventStoreException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw new ShipEventStoreException("Invalid Ship event head: " + e.getMessage(), e);
        }
    }

    static JsonNode copyAndValidatePayload(JsonNode value) {
        if (value == null) {
            throw new IllegalArgumentException("Ship event payload must not be null");
        }
        ArrayDeque<NodeDepth> pending = new ArrayDeque<>();
        pending.push(new NodeDepth(value, 1));
        long tokens = 0;
        while (!pending.isEmpty()) {
            NodeDepth current = pending.pop();
            if (current.depth() > MAX_NESTING_DEPTH || ++tokens > MAX_TOKENS) {
                throw new IllegalArgumentException("Ship event payload exceeds its structural budget");
            }
            JsonNode node = current.node();
            if (node.isObject()) {
                Iterator<String> names = node.fieldNames();
                while (names.hasNext()) {
                    String name = names.next();
                    requireString(name, MAX_NAME_CHARS, "payload field name");
                    if (++tokens > MAX_TOKENS) {
                        throw new IllegalArgumentException("Ship event payload exceeds its token budget");
                    }
                    pending.push(new NodeDepth(node.get(name), current.depth() + 1));
                }
            } else if (node.isArray()) {
                for (JsonNode child : node) {
                    pending.push(new NodeDepth(child, current.depth() + 1));
                }
            } else if (node.isTextual()) {
                requireString(node.textValue(), MAX_STRING_CHARS, "payload string");
            } else if (node.isNumber()) {
                if (node.asText().length() > 128
                        || (node.isFloatingPointNumber() && !Double.isFinite(node.doubleValue()))) {
                    throw new IllegalArgumentException("Ship event payload contains an invalid number");
                }
            } else if (!node.isBoolean() && !node.isNull()) {
                throw new IllegalArgumentException("Ship event payload contains a non-JSON value");
            }
        }
        return value.deepCopy();
    }

    private static ObjectNode unsignedNode(UnsignedEvent event) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("schemaVersion", event.schemaVersion());
        node.put("runId", event.runId().storageId());
        node.put("sequence", event.sequence());
        node.put("previousEventDigest", event.previousEventDigest());
        node.put("type", event.type().stableId());
        putNullable(node, "previousState",
                event.previousState() == null ? null : event.previousState().stableId());
        node.put("state", event.state().stableId());
        putNullable(node, "previousAuthorityHead",
                event.previousAuthorityHead() == null
                        ? null : event.previousAuthorityHead().storageId());
        node.put("authorityHead", event.authorityHead().storageId());
        node.put("occurredAt", event.occurredAt().toString());
        node.set("payload", copyAndValidatePayload(event.payload()));
        return node;
    }

    private static ObjectNode headNode(ShipRunId runId, ShipEventHead head) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("schemaVersion", 1);
        node.put("runId", runId.storageId());
        node.put("sequence", head.sequence());
        node.put("eventDigest", head.eventDigest());
        putNullable(node, "authorityHead",
                head.authorityHead() == null ? null : head.authorityHead().storageId());
        return node;
    }

    private static void putNullable(ObjectNode node, String field, String value) {
        if (value == null) {
            node.putNull(field);
        } else {
            node.put(field, value);
        }
    }

    private static ObjectNode parseObject(byte[] bytes, Set<String> fields, String label)
            throws ShipEventStoreException {
        if (bytes == null || bytes.length == 0 || bytes.length > FileShipEventStore.MAX_EVENT_BYTES) {
            throw new ShipEventStoreException(label + " exceeds its storage budget");
        }
        String json = strictUtf8(bytes, label);
        JsonNode parsed;
        try (JsonParser parser = MAPPER.getFactory().createParser(json)) {
            parsed = MAPPER.readTree(parser);
            if (parsed == null || parser.nextToken() != null) {
                throw new ShipEventStoreException(label + " must contain exactly one JSON document");
            }
        } catch (ShipEventStoreException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new ShipEventStoreException("Invalid " + label + " JSON", e);
        }
        if (!parsed.isObject()) {
            throw new ShipEventStoreException(label + " JSON root must be an object");
        }
        ObjectNode object = (ObjectNode) parsed;
        Set<String> actual = new TreeSet<>();
        object.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(fields)) {
            Set<String> missing = new TreeSet<>(fields);
            missing.removeAll(actual);
            Set<String> unknown = new TreeSet<>(actual);
            unknown.removeAll(fields);
            throw new ShipEventStoreException(
                    label + " fields do not match the schema; missing=" + missing + ", unknown=" + unknown);
        }
        return object;
    }

    private static String strictUtf8(byte[] bytes, String label) throws ShipEventStoreException {
        try {
            return ShipUtf8.decode(bytes);
        } catch (CharacterCodingException e) {
            throw new ShipEventStoreException(label + " is not strict UTF-8", e);
        }
    }

    private static byte[] encodeCanonical(JsonNode node) throws IOException {
        return MAPPER.writeValueAsBytes(canonicalize(node));
    }

    private static JsonNode canonicalize(JsonNode value) {
        if (value.isObject()) {
            ObjectNode result = MAPPER.createObjectNode();
            TreeSet<String> names = new TreeSet<>();
            value.fieldNames().forEachRemaining(names::add);
            for (String name : names) {
                result.set(name, canonicalize(value.get(name)));
            }
            return result;
        }
        if (value.isArray()) {
            ArrayNode result = MAPPER.createArrayNode();
            value.forEach(child -> result.add(canonicalize(child)));
            return result;
        }
        return value.deepCopy();
    }

    private static String requiredText(ObjectNode node, String field) throws ShipEventStoreException {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()) {
            throw typeError(field, "a string");
        }
        return value.textValue();
    }

    private static long requiredLong(ObjectNode node, String field) throws ShipEventStoreException {
        JsonNode value = node.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()) {
            throw typeError(field, "a 64-bit integer");
        }
        return value.longValue();
    }

    private static int requiredInt(ObjectNode node, String field) throws ShipEventStoreException {
        long value = requiredLong(node, field);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw typeError(field, "a 32-bit integer");
        }
        return (int) value;
    }

    private static ShipState nullableState(ObjectNode node, String field)
            throws ShipEventStoreException {
        String value = nullableText(node, field);
        return value == null ? null : ShipState.fromStableId(value);
    }

    private static AuthorityHeadId nullableAuthorityHead(ObjectNode node, String field)
            throws ShipEventStoreException {
        String value = nullableText(node, field);
        return value == null ? null : AuthorityHeadId.fromStorageId(value);
    }

    private static String nullableText(ObjectNode node, String field) throws ShipEventStoreException {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            throw typeError(field, "a string or null");
        }
        return value.textValue();
    }

    private static Instant canonicalInstant(String value) throws ShipEventStoreException {
        try {
            Instant result = Instant.parse(value);
            if (!result.toString().equals(value)) {
                throw new ShipEventStoreException("Ship event timestamp is not canonical");
            }
            return result;
        } catch (DateTimeException e) {
            throw new ShipEventStoreException("Ship event timestamp is invalid", e);
        }
    }

    private static ShipEventStoreException typeError(String field, String expected) {
        return new ShipEventStoreException("Ship storage field '" + field + "' must be " + expected);
    }

    private static void requireString(String value, int maximum, String label) {
        if (value.length() > maximum || !hasOnlyUnicodeScalarValues(value)) {
            throw new IllegalArgumentException("Ship event " + label + " is invalid or too long");
        }
    }

    private static boolean hasOnlyUnicodeScalarValues(String value) {
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isHighSurrogate(current)) {
                if (++index >= value.length() || !Character.isLowSurrogate(value.charAt(index))) {
                    return false;
                }
            } else if (Character.isLowSurrogate(current)) {
                return false;
            }
        }
        return true;
    }

    record UnsignedEvent(
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
            JsonNode payload) {

        static UnsignedEvent from(ShipEvent event) {
            return new UnsignedEvent(
                    event.schemaVersion(),
                    event.runId(),
                    event.sequence(),
                    event.previousEventDigest(),
                    event.type(),
                    event.previousState(),
                    event.state(),
                    event.previousAuthorityHead(),
                    event.authorityHead(),
                    event.occurredAt(),
                    event.payload());
        }
    }

    record HeadEnvelope(ShipRunId runId, ShipEventHead head, String hmac) {
    }

    private record NodeDepth(JsonNode node, int depth) {
    }
}
