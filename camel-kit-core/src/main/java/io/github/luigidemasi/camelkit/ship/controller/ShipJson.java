package io.github.luigidemasi.camelkit.ship.controller;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import io.github.luigidemasi.camelkit.ship.context.InitialContext;

import com.fasterxml.jackson.core.ErrorReportConfiguration;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.StreamWriteConstraints;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

/** Strict, deterministic JSON codec for controller-owned Ship records. */
public final class ShipJson {

    static final int MAX_DOCUMENT_BYTES = 64 * 1024 * 1024;
    static final long MAX_TOKENS = 1_000_000;
    static final int MAX_NESTING_DEPTH = 64;
    static final int MAX_STRING_CHARS = 8 * 1024 * 1024;
    static final int MAX_NAME_CHARS = 4096;
    static final int MAX_NUMBER_CHARS = 128;

    private static final ObjectMapper MAPPER = createMapper();

    private ShipJson() {
    }

    public static ObjectMapper mapper() {
        // ObjectMapper is mutable; callers never receive the canonical instance.
        return MAPPER.copy();
    }

    private static ObjectMapper createMapper() {
        SimpleModule values = new SimpleModule();
        values.addSerializer(Instant.class, new JsonSerializer<>() {
            @Override
            public void serialize(Instant value, JsonGenerator generator, SerializerProvider serializers)
                    throws IOException {
                generator.writeString(value.toString());
            }
        });
        values.addDeserializer(Instant.class, new JsonDeserializer<>() {
            @Override
            public Instant deserialize(JsonParser parser, DeserializationContext context) throws IOException {
                String value = parser.getValueAsString(null);
                if (value == null) {
                    throw context.weirdStringException("non-string", Instant.class,
                            "Ship timestamps must be canonical strings");
                }
                try {
                    Instant parsed = Instant.parse(value);
                    if (!parsed.toString().equals(value)) {
                        throw new IllegalArgumentException("timestamp is not canonical");
                    }
                    return parsed;
                } catch (RuntimeException e) {
                    throw context.weirdStringException("invalid-timestamp", Instant.class,
                            "Ship timestamp is invalid or noncanonical");
                }
            }
        });
        values.addSerializer(Duration.class, new JsonSerializer<>() {
            @Override
            public void serialize(Duration value, JsonGenerator generator, SerializerProvider serializers)
                    throws IOException {
                generator.writeString(value.toString());
            }
        });
        values.addDeserializer(Duration.class, new JsonDeserializer<>() {
            @Override
            public Duration deserialize(JsonParser parser, DeserializationContext context) throws IOException {
                String value = parser.getValueAsString(null);
                if (value == null) {
                    throw context.weirdStringException("non-string", Duration.class,
                            "Ship durations must be canonical strings");
                }
                try {
                    Duration parsed = Duration.parse(value);
                    if (!parsed.toString().equals(value)) {
                        throw new IllegalArgumentException("duration is not canonical");
                    }
                    return parsed;
                } catch (RuntimeException e) {
                    throw context.weirdStringException("invalid-duration", Duration.class,
                            "Ship duration is invalid or noncanonical");
                }
            }
        });
        values.addSerializer(InitialContext.class, new InitialContextSerializer());
        values.addDeserializer(InitialContext.class, new InitialContextDeserializer());

        StreamReadConstraints readConstraints = StreamReadConstraints.builder()
                .maxDocumentLength(MAX_DOCUMENT_BYTES)
                .maxTokenCount(MAX_TOKENS)
                .maxNestingDepth(MAX_NESTING_DEPTH)
                .maxStringLength(MAX_STRING_CHARS)
                .maxNameLength(MAX_NAME_CHARS)
                .maxNumberLength(MAX_NUMBER_CHARS)
                .build();
        return JsonMapper.builder(JsonFactory.builder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .streamReadConstraints(readConstraints)
                .streamWriteConstraints(StreamWriteConstraints.builder()
                        .maxNestingDepth(MAX_NESTING_DEPTH)
                        .build())
                .errorReportConfiguration(ErrorReportConfiguration.builder()
                        .maxErrorTokenLength(256)
                        .maxRawContentLength(1024)
                        .build())
                .build())
                .addModule(values)
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
    }

    private static final class InitialContextSerializer extends JsonSerializer<InitialContext> {

        @Override
        public void serialize(
                InitialContext value, JsonGenerator generator, SerializerProvider serializers)
                throws IOException {
            generator.writeStartObject();
            generator.writeStringField("kind", value.kind().id());
            generator.writeArrayFieldStart("sources");
            for (InitialContext.Source source : value.sources()) {
                generator.writeStartObject();
                generator.writeStringField("id", source.id());
                generator.writeStringField("kind", source.kind().id());
                generator.writeStringField("provenance", source.provenance());
                generator.writeStringField("content", source.content());
                generator.writeNumberField("byteSize", source.byteSize());
                generator.writeStringField("digest", source.digest());
                generator.writeEndObject();
            }
            generator.writeEndArray();
            generator.writeStringField("digest", value.digest());
            generator.writeEndObject();
        }
    }

    private static final class InitialContextDeserializer extends JsonDeserializer<InitialContext> {

        private static final Set<String> CONTEXT_FIELDS = Set.of("kind", "sources", "digest");
        private static final Set<String> SOURCE_FIELDS
                = Set.of("id", "kind", "provenance", "content", "byteSize", "digest");

        @Override
        public InitialContext deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            JsonNode node = parser.getCodec().readTree(parser);
            requireExactObject(node, CONTEXT_FIELDS, "initial context");
            InitialContext.Kind declaredKind = kind(text(node, "kind"));
            JsonNode sourceNodes = node.get("sources");
            if (!sourceNodes.isArray() || sourceNodes.size() > InitialContext.MAX_SOURCES) {
                throw context.weirdStringException(sourceNodes.toString(), InitialContext.class,
                        "Initial context sources must be a bounded array");
            }
            List<InitialContext.Source> sources = new ArrayList<>(sourceNodes.size());
            for (JsonNode sourceNode : sourceNodes) {
                requireExactObject(sourceNode, SOURCE_FIELDS, "initial context source");
                JsonNode byteSize = sourceNode.get("byteSize");
                if (!byteSize.isIntegralNumber() || !byteSize.canConvertToLong()) {
                    throw context.weirdStringException(byteSize.toString(), InitialContext.Source.class,
                            "Initial context source byteSize must be an integer");
                }
                try {
                    sources.add(new InitialContext.Source(
                            text(sourceNode, "id"),
                            sourceKind(text(sourceNode, "kind")),
                            text(sourceNode, "provenance"),
                            text(sourceNode, "content"),
                            byteSize.longValue(),
                            text(sourceNode, "digest")));
                } catch (IllegalArgumentException e) {
                    throw context.weirdStringException(
                            "invalid-source", InitialContext.Source.class, e.getMessage());
                }
            }
            InitialContext restored;
            try {
                restored = InitialContext.fromSources(sources);
            } catch (IllegalArgumentException e) {
                throw context.weirdStringException(
                        "invalid-context", InitialContext.class, e.getMessage());
            }
            if (restored.kind() != declaredKind
                    || !restored.sources().equals(sources)
                    || !restored.digest().equals(text(node, "digest"))) {
                throw context.weirdStringException("invalid-context", InitialContext.class,
                        "Initial context identity does not match its sources");
            }
            return restored;
        }

        private static void requireExactObject(JsonNode node, Set<String> expected, String label)
                throws IOException {
            if (!node.isObject()) {
                throw new IOException(label + " must be an object");
            }
            Set<String> actual = new java.util.HashSet<>();
            node.fieldNames().forEachRemaining(actual::add);
            if (!actual.equals(expected)) {
                throw new IOException(label + " must contain exactly " + expected);
            }
        }

        private static String text(JsonNode node, String field) throws IOException {
            JsonNode value = node.get(field);
            if (value == null || !value.isTextual()) {
                throw new IOException("Initial context " + field + " must be a string");
            }
            return value.textValue();
        }

        private static InitialContext.Kind kind(String value) throws IOException {
            for (InitialContext.Kind candidate : InitialContext.Kind.values()) {
                if (candidate.id().equals(value)) {
                    return candidate;
                }
            }
            throw new IOException("Unknown initial-context kind: " + value);
        }

        private static InitialContext.SourceKind sourceKind(String value) throws IOException {
            for (InitialContext.SourceKind candidate : InitialContext.SourceKind.values()) {
                if (candidate.id().equals(value)) {
                    return candidate;
                }
            }
            throw new IOException("Unknown initial-context source kind: " + value);
        }
    }
}
