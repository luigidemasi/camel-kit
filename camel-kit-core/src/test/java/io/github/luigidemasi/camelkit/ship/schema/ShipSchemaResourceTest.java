package io.github.luigidemasi.camelkit.ship.schema;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import io.github.luigidemasi.camelkit.ship.artifact.ArtifactManifest;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogComponentModel;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogEvidenceSet;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogSubject;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogTarget;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogUsageRecord;
import io.github.luigidemasi.camelkit.ship.evidence.CommandEvidence;
import io.github.luigidemasi.camelkit.ship.resolver.MavenCoordinate;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.dialect.Draft202012;
import com.networknt.schema.resource.SchemaLoader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShipSchemaResourceTest {

    private static final String DRAFT_2020_12 = "https://json-schema.org/draft/2020-12/schema";
    private static final String ID_BASE = "https://github.com/luigidemasi/camel-kit/schemas/ship/v1/";
    private static final String RESOURCE_BASE = "ship/schema/";
    private static final List<String> FILES = List.of(
            "artifact-manifest.schema.json",
            "catalog-evidence.schema.json",
            "catalog-usage.schema.json",
            "command-evidence.schema.json");
    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build();

    @Test
    void schemasAreClosedVersionedDraft202012Documents() throws Exception {
        for (String file : FILES) {
            JsonNode schema = readSchema(file);
            assertEquals(DRAFT_2020_12, schema.path("$schema").asText(), file);
            assertEquals(ID_BASE + file, schema.path("$id").asText(), file);
            assertStrictObjectSchemas(schema, file);
        }
    }

    @Test
    void schemaShapesMatchRetainedValidationRecords() throws Exception {
        JsonNode manifest = readSchema("artifact-manifest.schema.json");
        assertRecordShape(manifest, "", ArtifactManifest.class);
        assertRecordShape(manifest, "/$defs/routeArtifact", ArtifactManifest.RouteArtifact.class);
        assertRecordShape(manifest, "/$defs/testArtifact", ArtifactManifest.TestArtifact.class);
        assertRecordShape(manifest, "/$defs/declaredArtifact", ArtifactManifest.DeclaredArtifact.class);

        JsonNode catalogEvidence = readSchema("catalog-evidence.schema.json");
        assertRecordShape(catalogEvidence, "/$defs/catalogSubject", CatalogSubject.class);
        assertRecordShape(catalogEvidence, "/$defs/target", CatalogTarget.class);
        assertRecordShape(catalogEvidence, "/$defs/coordinate", MavenCoordinate.class);
        assertRecordShape(catalogEvidence, "/$defs/evidenceSet", CatalogEvidenceSet.class);
        assertRecordShape(catalogEvidence, "/$defs/artifactEvidence", CatalogEvidenceSet.ArtifactEvidence.class);
        assertRecordShape(catalogEvidence, "/$defs/subjectEvidence", CatalogEvidenceSet.SubjectEvidence.class);

        JsonNode usage = readSchema("catalog-usage.schema.json");
        assertRecordShape(usage, "", CatalogUsageRecord.class);
        assertRecordShape(usage, "/$defs/routeUsage", CatalogUsageRecord.RouteUsage.class);
        assertRecordShape(usage, "/$defs/endpointUsage", CatalogUsageRecord.EndpointUsage.class);
        assertRecordShape(usage, "/$defs/componentModel", CatalogComponentModel.class);
        assertRecordShape(usage, "/$defs/componentOption", CatalogComponentModel.Option.class);
        assertRecordShape(usage, "/$defs/runtimeDependency", CatalogUsageRecord.RuntimeDependency.class);

        JsonNode command = readSchema("command-evidence.schema.json");
        assertRecordShape(command, "", CommandEvidence.class);
        assertRecordShape(command, "/$defs/sandboxIdentity", CommandEvidence.SandboxIdentity.class);
    }

    @Test
    void everySchemaCompilesWithoutNetworkResolution() throws Exception {
        Map<String, String> resources = resources();
        Set<String> allowed = Set.copyOf(resources.keySet());
        SchemaLoader loader = SchemaLoader.builder()
                .resourceLoaders(loaders -> loaders.resources(resources))
                .allow(iri -> allowed.contains(iri.toString()))
                .build();
        SchemaRegistry registry = SchemaRegistry.withDialect(
                Draft202012.getInstance(), builder -> builder.schemaLoader(loader));

        for (String id : allowed) {
            Schema schema = registry.getSchema(SchemaLocation.of(id));
            assertNotNull(schema, id);
            assertFalse(schema.validate(MAPPER.createObjectNode()).isEmpty(), id);
        }
    }

    @Test
    void retainedSchemasRejectUnsafePathsAndMutableCoordinates() throws Exception {
        Schema routePath = compiledSchema("artifact-manifest.schema.json#/$defs/routeArtifact/properties/path");
        assertTrue(routePath.validate(MAPPER.getNodeFactory().textNode("routes/orders.camel.yaml")).isEmpty());
        for (String invalid : List.of(
                "../../outside.camel.yaml",
                "/absolute.camel.yaml",
                "routes\\orders.camel.yaml",
                "routes//orders.camel.yaml",
                "routes/orders.yaml")) {
            assertFalse(routePath.validate(MAPPER.getNodeFactory().textNode(invalid)).isEmpty(), invalid);
        }

        Schema coordinate = compiledSchema("catalog-evidence.schema.json#/$defs/coordinate");
        JsonNode valid = MAPPER.readTree("""
                {
                  "groupId": "org.apache.camel",
                  "artifactId": "camel-catalog",
                  "extension": "jar",
                  "classifier": "",
                  "version": "4.21.0"
                }
                """);
        assertTrue(coordinate.validate(valid).isEmpty());
        for (String version : List.of("4.21.0-SNAPSHOT", "LATEST", "RELEASE")) {
            com.fasterxml.jackson.databind.node.ObjectNode invalid = valid.deepCopy();
            invalid.put("version", version);
            assertFalse(coordinate.validate(invalid).isEmpty(), version);
        }
    }

    private static Schema compiledSchema(String location) throws IOException {
        Map<String, String> resources = resources();
        Set<String> allowed = Set.copyOf(resources.keySet());
        SchemaLoader loader = SchemaLoader.builder()
                .resourceLoaders(loaders -> loaders.resources(resources))
                .allow(iri -> allowed.contains(iri.toString()))
                .build();
        SchemaRegistry registry = SchemaRegistry.withDialect(
                Draft202012.getInstance(), builder -> builder.schemaLoader(loader));
        return registry.getSchema(SchemaLocation.of(ID_BASE + location));
    }

    private static Map<String, String> resources() throws IOException {
        Map<String, String> resources = new LinkedHashMap<>();
        for (String file : FILES) {
            resources.put(ID_BASE + file, readText(RESOURCE_BASE + file));
        }
        return resources;
    }

    private static JsonNode readSchema(String file) throws IOException {
        return MAPPER.readTree(readText(RESOURCE_BASE + file));
    }

    private static String readText(String resource) throws IOException {
        try (InputStream input = ShipSchemaResourceTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input, resource);
            byte[] bytes = input.readNBytes(16 * 1024 * 1024 + 1);
            assertTrue(bytes.length <= 16 * 1024 * 1024, resource);
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private static void assertStrictObjectSchemas(JsonNode node, String path) {
        if (node.isObject()) {
            if ("object".equals(node.path("type").asText())) {
                assertFalse(node.path("additionalProperties").asBoolean(true),
                        path + " must reject unknown fields");
                assertEquals(fieldNames(node.path("properties")), textValues(node.path("required")),
                        path + " must require every declared wire field");
            }
            node.fields().forEachRemaining(
                    entry -> assertStrictObjectSchemas(entry.getValue(), path + "/" + entry.getKey()));
        } else if (node.isArray()) {
            node.forEach(child -> assertStrictObjectSchemas(child, path));
        }
    }

    private static void assertRecordShape(JsonNode schema, String pointer, Class<?> recordType) {
        JsonNode object = pointer.isEmpty() ? schema : schema.at(pointer);
        assertFalse(object.isMissingNode(), pointer);
        assertTrue(recordType.isRecord(), recordType.getName());
        Set<String> expected = Stream.of(recordType.getRecordComponents())
                .map(component -> component.getName())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        assertEquals(expected, fieldNames(object.path("properties")), recordType.getSimpleName());
        assertEquals(expected, textValues(object.path("required")), recordType.getSimpleName());
    }

    private static Set<String> fieldNames(JsonNode object) {
        Set<String> fields = new LinkedHashSet<>();
        object.fieldNames().forEachRemaining(fields::add);
        return fields;
    }

    private static Set<String> textValues(JsonNode array) {
        Set<String> values = new LinkedHashSet<>();
        if (array.isArray()) {
            array.forEach(value -> values.add(value.asText()));
        }
        return values;
    }
}
