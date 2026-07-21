package io.github.luigidemasi.camelkit.ship.controller;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.dialect.Draft202012;
import com.networknt.schema.resource.SchemaLoader;

/** Validates untrusted worker results against the closed packaged Ship wire contract. */
final class StageResultWireSchema {

    static final String SCHEMA_BASE = "https://github.com/luigidemasi/camel-kit/schemas/ship/v1/";
    static final String ROOT_SCHEMA_ID = SCHEMA_BASE + "stage-result.schema.json";

    private static final int MAX_REPORTED_ERRORS = 20;
    private static final Map<String, String> SCHEMA_RESOURCES = loadResources();
    private static final Schema SCHEMA = compile(SCHEMA_RESOURCES, ROOT_SCHEMA_ID);

    private StageResultWireSchema() {
    }

    static void validate(JsonNode result) throws IOException {
        List<Error> errors = SCHEMA.validate(result);
        if (errors.isEmpty()) {
            return;
        }
        List<String> summaries = errors.stream()
                .map(StageResultWireSchema::summarize)
                .sorted()
                .limit(MAX_REPORTED_ERRORS)
                .toList();
        String omitted = errors.size() > summaries.size()
                ? "; " + (errors.size() - summaries.size()) + " more"
                : "";
        throw new IOException(
                "Stage result failed JSON Schema validation: "
                              + String.join("; ", summaries)
                              + omitted);
    }

    /** Package-private so tests can prove that unknown IRIs can never be dereferenced. */
    static Schema compile(Map<String, String> resources, String rootSchemaId) {
        Map<String, String> trustedResources = Map.copyOf(resources);
        Set<String> trustedIds = Set.copyOf(trustedResources.keySet());
        if (!trustedIds.contains(rootSchemaId)) {
            throw new IllegalArgumentException("Root schema is not in the trusted resource set");
        }
        SchemaLoader loader = SchemaLoader.builder()
                .resourceLoaders(loaders -> loaders.resources(trustedResources))
                .allow(iri -> trustedIds.contains(iri.toString()))
                .build();
        SchemaRegistry registry = SchemaRegistry.withDialect(
                Draft202012.getInstance(), builder -> builder.schemaLoader(loader));
        return registry.getSchema(SchemaLocation.of(rootSchemaId));
    }

    private static Map<String, String> loadResources() {
        Map<String, String> resources = new LinkedHashMap<>();
        resources.put(ROOT_SCHEMA_ID, readResource("stage-result.schema.json"));
        resources.put(
                SCHEMA_BASE + "decision-ledger.schema.json",
                readResource("decision-ledger.schema.json"));
        resources.put(
                SCHEMA_BASE + "artifact-manifest.schema.json",
                readResource("artifact-manifest.schema.json"));
        return Map.copyOf(resources);
    }

    private static String readResource(String name) {
        String path = "/ship/schema/" + name;
        try (InputStream input = StageResultWireSchema.class.getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalStateException("Missing packaged Ship schema " + path);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read packaged Ship schema " + path, e);
        }
    }

    private static String summarize(Error error) {
        String location = error.getInstanceLocation() == null
                || error.getInstanceLocation().toString().isEmpty()
                        ? "/"
                        : error.getInstanceLocation().toString();
        return java.util.stream.Stream.of(location, error.getKeyword(), error.getProperty())
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.joining(" "));
    }
}
