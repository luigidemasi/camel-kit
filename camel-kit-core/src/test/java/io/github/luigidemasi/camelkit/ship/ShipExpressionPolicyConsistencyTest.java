package io.github.luigidemasi.camelkit.ship;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import io.github.luigidemasi.camelkit.ship.catalog.CatalogExpressionInventory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipExpressionPolicyConsistencyTest {

    /**
     * Non-classpath version rows are externally reviewed exact-artifact pins. Schema digests cover the raw JAR entry
     * bytes. Catalog-language digests cover the UTF-8 {@code languages.properties} names normalized to lexical order,
     * one name per line, with one final LF. The default classpath schema receives the additional live content check
     * below; historical rows intentionally do not add dependencies or network access to unit tests.
     */
    private static final String INVENTORY = "ship/expression-policy/catalog-yaml-inventory.json";
    private static final String EXPRESSION_DEFINITION = "org.apache.camel.model.language.ExpressionDefinition";
    private static final String GENERIC_DEFINITION = "org.apache.camel.model.language.LanguageExpression";
    private static final String SIMPLE_DEFINITION = "org.apache.camel.model.language.SimpleExpression";
    private static final String METHOD_DEFINITION = "org.apache.camel.model.language.MethodCallExpression";
    private static final String REF_PREFIX = "#/items/definitions/";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void everyCertifiedCamelLineHasAnExactClosedCatalogAndYamlInventory() throws Exception {
        Properties distribution = distribution();
        Set<String> certified = csv(distribution.getProperty(
                "ship.evidence.camel-yaml-validator.supported"));
        JsonNode versions = resourceJson(INVENTORY).path("versions");

        assertEquals(certified, fieldNames(versions),
                "A certified Camel version needs a reviewed expression inventory before it can ship");
        for (String version : certified) {
            JsonNode inventory = versions.path(version);
            Map<String, String> aliases = textMap(inventory.path("yamlAliases"));
            assertEquals(CatalogExpressionInventory.yamlAliases(), aliases.keySet(), version);
            assertEquals(CatalogExpressionInventory.catalogLanguages(), textSet(inventory.path("catalogLanguages")),
                    version);
            assertEquals(GENERIC_DEFINITION, aliases.get(CatalogExpressionInventory.GENERIC), version);
            assertEquals(SIMPLE_DEFINITION, aliases.get(CatalogExpressionInventory.SIMPLE), version);
            assertEquals(METHOD_DEFINITION, aliases.get(CatalogExpressionInventory.METHOD), version);
            assertEquals(Set.of("expression", "id", "language", "trim"),
                    textSet(inventory.path("generic").path("properties")), version);
            assertEquals(Set.of("expression", "language"),
                    textSet(inventory.path("generic").path("required")), version);
            assertEquals(Set.of("expression", "id", "nested", "pretty", "resultType", "trim", "trimResult"),
                    textSet(inventory.path("simple").path("properties")), version);
            assertEquals(Set.of("expression"), textSet(inventory.path("simple").path("required")), version);
            assertEquals(Set.of("beanType", "id", "method", "ref", "resultType", "scope", "trim", "validate"),
                    textSet(inventory.path("method").path("properties")), version);
            assertEquals("org.apache.camel:camel-yaml-dsl:" + version + "!/schema/camelYamlDsl.json",
                    inventory.path("yamlSchemaArtifact").asText(), version);
            assertTrue(ShipDigest.isSha256(inventory.path("yamlSchemaSha256").asText()), version);
            assertEquals("org.apache.camel:camel-catalog:" + version
                         + "!/org/apache/camel/catalog/languages.properties",
                    inventory.path("catalogLanguagesArtifact").asText(), version);
            assertEquals(inventory.path("catalogLanguagesSha256").asText(),
                    digestLines(textSet(inventory.path("catalogLanguages"))), version);
        }
    }

    @Test
    void defaultCamelSchemasMatchTheReviewedInventoryWithoutAnUnclassifiedAlias() throws Exception {
        Properties distribution = distribution();
        String version = distribution.getProperty("camel.main.version");
        JsonNode inventory = resourceJson(INVENTORY).path("versions").path(version);
        assertFalse(inventory.isMissingNode(), "Default Camel lacks an expression inventory: " + version);

        byte[] schemaBytes = resourceBytes("schema/camelYamlDsl.json");
        assertEquals(inventory.path("yamlSchemaSha256").asText(), ShipDigest.sha256(schemaBytes));
        assertSchemaInventory(JSON.readTree(schemaBytes), inventory);
        assertSchemaInventory(resourceJson("schema/camelYamlDsl-canonical.json"), inventory);
        assertEquals(version, resourceJson("yaml-dsl.json").path("other").path("version").asText());
    }

    private static void assertSchemaInventory(JsonNode schema, JsonNode inventory) {
        JsonNode definitions = schema.path("items").path("definitions");
        JsonNode expression = definitions.path(EXPRESSION_DEFINITION);
        Map<String, String> aliases = new TreeMap<>();
        if (expression.has("anyOf")) {
            for (JsonNode alternative : expression.path("anyOf").path(0).path("oneOf")) {
                assertEquals(1, alternative.path("required").size());
                String alias = alternative.path("required").path(0).asText();
                String ref = alternative.path("properties").path(alias).path("$ref").asText();
                addAlias(aliases, alias, ref);
                assertEquals(1, alternative.path("properties").size(), alias);
            }
        } else {
            assertEquals("object", expression.path("type").asText());
            assertFalse(expression.path("additionalProperties").asBoolean(true));
            expression.path("properties").properties()
                    .forEach(field -> addAlias(aliases, field.getKey(), field.getValue().path("$ref").asText()));
        }
        assertEquals(textMap(inventory.path("yamlAliases")), aliases);
        assertEquals(aliases.keySet(), fieldNames(expression.path("properties")));

        JsonNode generic = definitions.path(GENERIC_DEFINITION);
        assertEquals("object", generic.path("type").asText());
        assertFalse(generic.path("additionalProperties").asBoolean(true));
        assertEquals(textSet(inventory.path("generic").path("properties")),
                fieldNames(generic.path("properties")));
        assertEquals(textSet(inventory.path("generic").path("required")), textSet(generic.path("required")));

        assertExpressionObject(definitions.path(SIMPLE_DEFINITION), inventory.path("simple"), true);
        assertExpressionObject(definitions.path(METHOD_DEFINITION), inventory.path("method"), false);
    }

    private static void addAlias(Map<String, String> aliases, String alias, String ref) {
        assertTrue(ref.startsWith(REF_PREFIX), alias);
        assertFalse(aliases.containsKey(alias), alias);
        aliases.put(alias, ref.substring(REF_PREFIX.length()));
    }

    private static void assertExpressionObject(JsonNode definition, JsonNode inventory, boolean required) {
        JsonNode object;
        if (definition.has("oneOf")) {
            Set<String> types = new TreeSet<>();
            object = null;
            for (JsonNode alternative : definition.path("oneOf")) {
                types.add(alternative.path("type").asText());
                if (alternative.has("properties")) {
                    object = alternative;
                }
            }
            assertEquals(Set.of("object", "string"), types);
            assertNotNull(object);
        } else {
            assertEquals("object", definition.path("type").asText());
            object = definition;
        }
        assertFalse(object.path("additionalProperties").asBoolean(true));
        assertEquals(textSet(inventory.path("properties")), fieldNames(object.path("properties")));
        if (required) {
            assertEquals(textSet(inventory.path("required")), textSet(definition.path("required")));
        }
    }

    private static Properties distribution() throws IOException {
        Properties properties = new Properties();
        try (InputStream input = resource("distribution.properties")) {
            properties.load(input);
        }
        return properties;
    }

    private static JsonNode resourceJson(String name) throws IOException {
        try (InputStream input = resource(name)) {
            return JSON.readTree(input);
        }
    }

    private static byte[] resourceBytes(String name) throws IOException {
        try (InputStream input = resource(name)) {
            return input.readAllBytes();
        }
    }

    private static InputStream resource(String name) throws IOException {
        InputStream input = ShipExpressionPolicyConsistencyTest.class.getClassLoader().getResourceAsStream(name);
        if (input == null) {
            throw new IOException("Missing test resource: " + name);
        }
        return input;
    }

    private static Set<String> csv(String value) {
        TreeSet<String> result = new TreeSet<>();
        if (value != null) {
            for (String item : value.split(",")) {
                if (!item.isBlank()) {
                    result.add(item.trim());
                }
            }
        }
        return result;
    }

    private static Set<String> fieldNames(JsonNode object) {
        TreeSet<String> result = new TreeSet<>();
        object.properties().forEach(field -> result.add(field.getKey()));
        return result;
    }

    private static Set<String> textSet(JsonNode array) {
        TreeSet<String> result = new TreeSet<>();
        array.forEach(value -> result.add(value.asText()));
        return result;
    }

    private static String digestLines(Set<String> values) {
        return ShipDigest.sha256((String.join("\n", new TreeSet<>(values)) + '\n')
                .getBytes(StandardCharsets.UTF_8));
    }

    private static Map<String, String> textMap(JsonNode object) {
        TreeMap<String, String> result = new TreeMap<>();
        object.properties().forEach(field -> result.put(field.getKey(), field.getValue().asText()));
        return result;
    }
}
