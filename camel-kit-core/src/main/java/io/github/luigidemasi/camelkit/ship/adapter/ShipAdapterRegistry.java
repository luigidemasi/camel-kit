package io.github.luigidemasi.camelkit.ship.adapter;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import io.github.luigidemasi.camelkit.config.AgentRegistry;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

/**
 * Internal deny-only inventory of Camel Ship adapter candidates.
 *
 * <p>
 * This registry describes product scope; it is not conformance evidence and cannot admit or launch an adapter.
 * Evidence-v2 verification will own positive admission.
 */
final class ShipAdapterRegistry {

    static final int REGISTRY_SCHEMA_VERSION = 1;

    private static final String DEFAULT_RESOURCE = "ship/adapters.yaml";
    private static final int MAX_REGISTRY_BYTES = 64 * 1024;
    private static final int MAX_ADAPTERS = 64;
    private static final int MAX_IDENTIFIER_CHARS = 64;
    private static final int MAX_REASON_CHARS = 512;
    private static final Pattern HARNESS_ID = Pattern.compile("[a-z][a-z0-9-]*");
    private static final ObjectMapper YAML = yamlMapper();

    private final List<Adapter> adapters;
    private final Map<String, Adapter> adaptersById;

    private ShipAdapterRegistry(List<Adapter> adapters) {
        this.adapters = adapters.stream().sorted(Comparator.comparing(Adapter::harnessId)).toList();
        Map<String, Adapter> indexed = new LinkedHashMap<>();
        this.adapters.forEach(adapter -> indexed.put(adapter.harnessId(), adapter));
        this.adaptersById = Map.copyOf(indexed);
    }

    static ShipAdapterRegistry loadDefault() {
        InputStream input = ShipAdapterRegistry.class.getClassLoader().getResourceAsStream(DEFAULT_RESOURCE);
        if (input == null) {
            throw new IllegalStateException("Ship adapter registry not found on classpath: " + DEFAULT_RESOURCE);
        }
        try (input) {
            return load(input, AgentRegistry.names());
        } catch (IOException e) {
            throw new IllegalStateException("Could not close Ship adapter registry: " + e.getMessage(), e);
        }
    }

    static ShipAdapterRegistry load(InputStream input, Set<String> expectedHarnesses) {
        if (input == null) {
            throw new IllegalStateException("Ship adapter registry input must not be null");
        }
        if (expectedHarnesses == null) {
            throw new IllegalStateException("Expected Ship harnesses must not be null");
        }

        byte[] content;
        try {
            content = input.readNBytes(MAX_REGISTRY_BYTES + 1);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read Ship adapter registry: " + e.getMessage(), e);
        }
        if (content.length == 0) {
            throw new IllegalStateException("Ship adapter registry must not be empty");
        }
        if (content.length > MAX_REGISTRY_BYTES) {
            throw new IllegalStateException("Ship adapter registry exceeds " + MAX_REGISTRY_BYTES + " bytes");
        }

        Manifest manifest;
        try {
            manifest = YAML.readValue(content, Manifest.class);
        } catch (IOException | IllegalArgumentException e) {
            throw new IllegalStateException("Could not parse Ship adapter registry: " + e.getMessage(), e);
        }
        return new ShipAdapterRegistry(validate(manifest, Set.copyOf(expectedHarnesses)));
    }

    List<Adapter> descriptors() {
        return adapters;
    }

    Adapter descriptor(String harnessId) {
        if (harnessId == null) {
            throw new IllegalArgumentException("Unknown Ship adapter harness: null");
        }
        Adapter adapter = adaptersById.get(harnessId);
        if (adapter == null) {
            throw new IllegalArgumentException("Unknown Ship adapter harness: " + harnessId);
        }
        return adapter;
    }

    private static ObjectMapper yamlMapper() {
        ObjectMapper mapper = new ObjectMapper(
                YAMLFactory.builder()
                        .streamReadConstraints(StreamReadConstraints.builder()
                                .maxNestingDepth(8)
                                .maxStringLength(MAX_REASON_CHARS)
                                .build())
                        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                        .build())
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS);
        mapper.coercionConfigFor(LogicalType.Textual)
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
        return mapper;
    }

    private static List<Adapter> validate(Manifest manifest, Set<String> expectedHarnesses) {
        List<String> errors = new ArrayList<>();
        if (manifest == null) {
            throw invalid(List.of("registry must not be empty"));
        }
        if (manifest.registrySchemaVersion() != REGISTRY_SCHEMA_VERSION) {
            errors.add("registry_schema_version must be " + REGISTRY_SCHEMA_VERSION);
        }
        if (manifest.adapters() == null) {
            errors.add("adapters must not be missing");
            throw invalid(errors);
        }
        if (manifest.adapters().size() > MAX_ADAPTERS) {
            errors.add("adapters must contain at most " + MAX_ADAPTERS + " rows");
        }

        Map<String, Adapter> indexed = new LinkedHashMap<>();
        Set<String> duplicates = new LinkedHashSet<>();
        for (int index = 0; index < manifest.adapters().size(); index++) {
            Adapter adapter = manifest.adapters().get(index);
            if (adapter == null) {
                errors.add("adapters[" + index + "] must not be null");
                continue;
            }
            String harnessId = adapter.harnessId();
            if (!isCanonicalIdentifier(harnessId, HARNESS_ID)) {
                errors.add("adapters[" + index + "].harness_id must be a canonical bounded harness ID");
                continue;
            }
            if (indexed.putIfAbsent(harnessId, adapter) != null) {
                duplicates.add(harnessId);
            }
            validateAdapter(adapter, errors);
        }
        duplicates.forEach(id -> errors.add("duplicate harness_id '" + id + "'"));

        Set<String> missing = new LinkedHashSet<>(expectedHarnesses);
        missing.removeAll(indexed.keySet());
        if (!missing.isEmpty()) {
            errors.add("missing registered harnesses " + sorted(missing));
        }
        Set<String> unexpected = new LinkedHashSet<>(indexed.keySet());
        unexpected.removeAll(expectedHarnesses);
        if (!unexpected.isEmpty()) {
            errors.add("unknown harnesses " + sorted(unexpected));
        }
        if (!errors.isEmpty()) {
            throw invalid(errors);
        }
        return List.copyOf(indexed.values());
    }

    private static void validateAdapter(Adapter adapter, List<String> errors) {
        String prefix = "adapter[" + adapter.harnessId() + "]";
        SupportStatus requiredStatus = "bob".equals(adapter.harnessId())
                ? SupportStatus.INCOMPATIBLE
                : SupportStatus.UNTESTED;
        if (adapter.status() != requiredStatus) {
            errors.add(prefix + ".status must be " + requiredStatus.name().toLowerCase(Locale.ROOT));
        }
        if (adapter.ingressMode() != IngressMode.NONE) {
            errors.add(prefix + ".ingress_mode must be none");
        }
        requireNone(adapter.interactionProfile(), prefix + ".interaction_profile", errors);
        requireNone(adapter.launcherProfile(), prefix + ".launcher_profile", errors);
        requireNone(adapter.sandboxProfile(), prefix + ".sandbox_profile", errors);
        requireNone(adapter.providerProfile(), prefix + ".provider_profile", errors);
        requireNone(adapter.protocolProfile(), prefix + ".protocol_profile", errors);
        requireNone(adapter.evidenceProfile(), prefix + ".evidence_profile", errors);
        requireReason(adapter.reason(), prefix + ".reason", errors);
    }

    private static void requireNone(String value, String field, List<String> errors) {
        if (!"none".equals(value)) {
            errors.add(field + " must be none in registry schema " + REGISTRY_SCHEMA_VERSION);
        }
    }

    private static void requireReason(String value, String field, List<String> errors) {
        if (value == null || value.isBlank() || value.length() > MAX_REASON_CHARS) {
            errors.add(field + " must contain 1.." + MAX_REASON_CHARS + " characters");
            return;
        }
        if (value.chars().anyMatch(character -> character < 0x20 || character > 0x7e)) {
            errors.add(field + " must contain printable ASCII only");
        }
    }

    private static boolean isCanonicalIdentifier(String value, Pattern pattern) {
        return value != null
                && value.length() <= MAX_IDENTIFIER_CHARS
                && pattern.matcher(value).matches();
    }

    private static List<String> sorted(Set<String> values) {
        return values.stream().sorted().toList();
    }

    private static IllegalStateException invalid(List<String> errors) {
        return new IllegalStateException("Invalid Ship adapter registry:\n- " + String.join("\n- ", errors));
    }

    private record Manifest(int registrySchemaVersion, List<Adapter> adapters) {
    }

    record Adapter(
            String harnessId,
            SupportStatus status,
            IngressMode ingressMode,
            String interactionProfile,
            String launcherProfile,
            String sandboxProfile,
            String providerProfile,
            String protocolProfile,
            String evidenceProfile,
            String reason) {
    }

    enum SupportStatus {
        INCOMPATIBLE,
        UNTESTED;

        @JsonCreator
        static SupportStatus fromValue(String value) {
            return parse(SupportStatus.class, value, "support status");
        }

    }

    enum IngressMode {
        NONE;

        @JsonCreator
        static IngressMode fromValue(String value) {
            return parse(IngressMode.class, value, "ingress mode");
        }

    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value, String label) {
        if (value != null) {
            for (E candidate : type.getEnumConstants()) {
                String canonical = candidate.name().toLowerCase(Locale.ROOT).replace('_', '-');
                if (canonical.equals(value)) {
                    return candidate;
                }
            }
        }
        throw new IllegalArgumentException("Unknown Ship adapter " + label + ": " + value);
    }
}
