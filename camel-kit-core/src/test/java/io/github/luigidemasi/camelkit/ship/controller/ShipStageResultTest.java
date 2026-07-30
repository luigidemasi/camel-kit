package io.github.luigidemasi.camelkit.ship.controller;

import java.io.IOException;
import java.util.List;

import io.github.luigidemasi.camelkit.ship.artifact.ArtifactManifest.JavaPolicy;
import io.github.luigidemasi.camelkit.ship.controller.ShipRun.Stage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipStageResultTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String CITRUS_VERSION = "5.0.0-M2";

    @Test
    void parsesEveryPiOwnedStageShape() throws Exception {
        ShipStageResult discovery = parse(Stage.DISCOVERY, result("149-local-controller", null));
        ShipStageResult design = parse(Stage.DESIGN, result(null, null));
        ObjectNode planResult = result(null, policy("4.21.0", CITRUS_VERSION));
        planResult.put("materialAmbiguity", true);
        ShipStageResult plan = parse(Stage.PLAN, planResult);
        ShipStageResult execute = parse(Stage.EXECUTE, result(null, null));

        assertEquals(ShipStageResult.SCHEMA_VERSION, discovery.schemaVersion());
        assertEquals("149-local-controller", discovery.pipelineId());
        assertEquals("Stage report", discovery.report());
        assertNull(discovery.artifactPolicy());
        assertFalse(discovery.materialAmbiguity());
        assertNull(design.pipelineId());
        assertNull(design.artifactPolicy());
        assertEquals("main", plan.artifactPolicy().runtime());
        assertEquals("4.21.0", plan.artifactPolicy().camelVersion());
        assertEquals("yaml", plan.artifactPolicy().routeDsl());
        assertEquals("simple", plan.artifactPolicy().expressionLanguage());
        assertEquals(JavaPolicy.FORBIDDEN, plan.artifactPolicy().javaPolicy());
        assertTrue(plan.artifactPolicy().citrusTestsRequired());
        assertTrue(plan.artifactPolicy().oneCitrusTestPerRoute());
        assertTrue(plan.materialAmbiguity());
        assertEquals("orders", plan.artifactPolicy().routes().get(0).routeId());
        assertNull(execute.artifactPolicy());

        assertRejected(Stage.VALIDATE, result(null, null), "Validate is controller-owned");
    }

    @Test
    void rejectsMalformedOrNonCanonicalEnvelopeJson() throws Exception {
        ObjectNode valid = result("149-local-controller", null);
        for (String field : List.of(
                "schemaVersion", "pipelineId", "report", "artifactPolicy", "materialAmbiguity")) {
            ObjectNode missing = valid.deepCopy();
            missing.remove(field);
            assertRejected(Stage.DISCOVERY, missing, "missing " + field);
        }

        ObjectNode unknown = valid.deepCopy();
        unknown.put("unknown", true);
        assertRejected(Stage.DISCOVERY, unknown, "unknown property");
        assertRejected(
                Stage.DISCOVERY,
                JSON.writeValueAsString(valid) + "{}",
                "trailing document");
        assertRejected(
                Stage.DISCOVERY,
                """
                        {
                          "schemaVersion": 1,
                          "schemaVersion": 1,
                          "pipelineId": "149-local-controller",
                          "report": "Stage report",
                          "artifactPolicy": null,
                          "materialAmbiguity": false
                        }
                        """,
                "duplicate property");
        assertRejected(Stage.DISCOVERY, "{", "malformed document");
        assertRejected(Stage.DISCOVERY, "\uD800", "invalid Unicode");
        assertRejected(Stage.DISCOVERY, " ".repeat(1024 * 1024 + 1), "oversized response");
        ObjectNode multibyte = valid.deepCopy();
        multibyte.put("report", "é".repeat(512 * 1024 + 1));
        assertRejected(Stage.DISCOVERY, multibyte, "oversized UTF-8 response");
    }

    @Test
    void rejectsScalarCoercionAndInvalidPrimitiveValues() throws Exception {
        ObjectNode valid = result("149-local-controller", null);

        for (JsonNode value : List.of(
                JSON.getNodeFactory().textNode("1"),
                JSON.getNodeFactory().numberNode(1.0),
                JSON.getNodeFactory().nullNode())) {
            ObjectNode invalid = valid.deepCopy();
            invalid.set("schemaVersion", value);
            assertRejected(Stage.DISCOVERY, invalid, "invalid schemaVersion " + value);
        }
        for (JsonNode value : List.of(
                JSON.getNodeFactory().textNode("false"),
                JSON.getNodeFactory().numberNode(0),
                JSON.getNodeFactory().nullNode())) {
            ObjectNode invalid = valid.deepCopy();
            invalid.set("materialAmbiguity", value);
            assertRejected(Stage.DISCOVERY, invalid, "invalid materialAmbiguity " + value);
        }
        ObjectNode numericPipeline = valid.deepCopy();
        numericPipeline.put("pipelineId", 149);
        assertRejected(Stage.DISCOVERY, numericPipeline, "numeric pipeline ID");

        ObjectNode numericReport = valid.deepCopy();
        numericReport.put("report", 149);
        assertRejected(Stage.DISCOVERY, numericReport, "numeric report");

        ObjectNode wrongSchema = valid.deepCopy();
        wrongSchema.put("schemaVersion", 2);
        assertRejected(Stage.DISCOVERY, wrongSchema, "unsupported schema");
    }

    @Test
    void rejectsInvalidReportsAndStageSpecificFields() throws Exception {
        ObjectNode discovery = result("149-local-controller", null);
        for (String report : List.of("", " \n\t", "bad\0report")) {
            ObjectNode invalid = discovery.deepCopy();
            invalid.put("report", report);
            assertRejected(Stage.DISCOVERY, invalid, "invalid report");
        }
        ObjectNode nullReport = discovery.deepCopy();
        nullReport.putNull("report");
        assertRejected(Stage.DISCOVERY, nullReport, "null report");

        assertRejected(Stage.DISCOVERY, result(null, null), "missing Discovery pipeline ID");
        assertRejected(Stage.DISCOVERY, result("orders", null), "invalid Discovery pipeline ID");
        assertRejected(
                Stage.DISCOVERY,
                result("149-local-controller", policy("4.21.0", CITRUS_VERSION)),
                "Discovery policy");
        for (Stage stage : List.of(Stage.DESIGN, Stage.EXECUTE)) {
            assertRejected(stage, result("149-local-controller", null), stage + " pipeline ID");
            assertRejected(stage, result(null, policy("4.21.0", CITRUS_VERSION)), stage + " policy");
        }
        assertRejected(Stage.PLAN, result("149-local-controller", policy("4.21.0", CITRUS_VERSION)),
                "Plan pipeline ID");
        assertRejected(Stage.PLAN, result(null, null), "missing Plan policy");
        assertRejected(Stage.PLAN, result(null, JSON.getNodeFactory().textNode("policy")), "scalar Plan policy");
        assertThrows(IOException.class, () -> ShipStageResult.parse(null, "{}"));
    }

    @Test
    void requiresEveryArtifactPolicyFieldAndRejectsUnknownOrNullFields() throws Exception {
        ObjectNode valid = result(null, policy("4.21.0", CITRUS_VERSION));
        for (String field : List.of(
                "runtime",
                "camelVersion",
                "platformVersion",
                "springBootVersion",
                "routeDsl",
                "expressionLanguage",
                "citrusVersion",
                "citrusDependencies",
                "javaPolicy",
                "approvedJavaExceptions",
                "routes",
                "citrusTestsRequired",
                "oneCitrusTestPerRoute")) {
            ObjectNode missing = valid.deepCopy();
            artifactPolicy(missing).remove(field);
            assertRejected(Stage.PLAN, missing, "missing policy " + field);
        }

        ObjectNode unknown = valid.deepCopy();
        artifactPolicy(unknown).put("unknown", true);
        assertRejected(Stage.PLAN, unknown, "unknown policy property");

        for (String field : List.of(
                "runtime",
                "camelVersion",
                "routeDsl",
                "expressionLanguage",
                "citrusVersion",
                "citrusDependencies",
                "javaPolicy",
                "approvedJavaExceptions",
                "routes")) {
            ObjectNode invalid = valid.deepCopy();
            artifactPolicy(invalid).putNull(field);
            assertRejected(Stage.PLAN, invalid, "null policy " + field);
        }
        for (String field : List.of("citrusTestsRequired", "oneCitrusTestPerRoute")) {
            ObjectNode invalid = valid.deepCopy();
            artifactPolicy(invalid).putNull(field);
            assertRejected(Stage.PLAN, invalid, "null policy primitive " + field);

            ObjectNode coerced = valid.deepCopy();
            artifactPolicy(coerced).put(field, "true");
            assertRejected(Stage.PLAN, coerced, "coerced policy primitive " + field);
        }

        ObjectNode numericEnum = valid.deepCopy();
        artifactPolicy(numericEnum).put("javaPolicy", 0);
        assertRejected(Stage.PLAN, numericEnum, "numeric Java policy");

        ObjectNode numericVersion = valid.deepCopy();
        artifactPolicy(numericVersion).put("camelVersion", 421);
        assertRejected(Stage.PLAN, numericVersion, "numeric Camel version");

        for (String field : List.of("citrusDependencies", "approvedJavaExceptions")) {
            for (JsonNode value : List.of(
                    JSON.getNodeFactory().nullNode(),
                    JSON.getNodeFactory().numberNode(149))) {
                ObjectNode invalid = valid.deepCopy();
                ((ArrayNode) artifactPolicy(invalid).get(field)).add(value);
                assertRejected(Stage.PLAN, invalid, "invalid " + field + " item " + value);
            }
        }
    }

    @Test
    void acceptsUnpinnedImmutableVersionsButEnforcesMainValidationPolicy() throws Exception {
        ShipStageResult accepted = parse(
                Stage.PLAN,
                result(null, policy("99.1.0", "6.1.2")));
        assertEquals("99.1.0", accepted.artifactPolicy().camelVersion());
        assertEquals("6.1.2", accepted.artifactPolicy().citrusVersion());

        ObjectNode valid = result(null, policy("4.21.0", CITRUS_VERSION));
        assertPolicyMutationRejected(valid, "runtime", "spring-boot");
        assertPolicyMutationRejected(valid, "camelVersion", "4.21.0-SNAPSHOT");
        assertPolicyMutationRejected(valid, "platformVersion", "4.21.0");
        assertPolicyMutationRejected(valid, "springBootVersion", "3.5.0");
        assertPolicyMutationRejected(valid, "routeDsl", "xml");
        assertPolicyMutationRejected(valid, "expressionLanguage", "groovy");
        assertPolicyMutationRejected(valid, "citrusVersion", "LATEST");
        assertPolicyMutationRejected(valid, "javaPolicy", "ALLOWED");

        ObjectNode dependencies = valid.deepCopy();
        ((ArrayNode) artifactPolicy(dependencies).get("citrusDependencies")).remove(0);
        assertRejected(Stage.PLAN, dependencies, "incomplete Citrus dependencies");

        ObjectNode exception = valid.deepCopy();
        ((ArrayNode) artifactPolicy(exception).get("approvedJavaExceptions")).add("Example.java");
        assertRejected(Stage.PLAN, exception, "Java exception");

        ObjectNode noRoutes = valid.deepCopy();
        artifactPolicy(noRoutes).putArray("routes");
        assertRejected(Stage.PLAN, noRoutes, "empty routes");

        for (String flag : List.of("citrusTestsRequired", "oneCitrusTestPerRoute")) {
            ObjectNode weakened = valid.deepCopy();
            artifactPolicy(weakened).put(flag, false);
            assertRejected(Stage.PLAN, weakened, "false " + flag);
        }
    }

    @Test
    void requiresDistinctSortedOneToOneSafeRouteContracts() throws Exception {
        ObjectNode valid = result(null, policy("4.21.0", CITRUS_VERSION));

        for (String field : List.of("routeId", "routePath", "citrusTestPath")) {
            ObjectNode missing = valid.deepCopy();
            route(missing, 0).remove(field);
            assertRejected(Stage.PLAN, missing, "missing route " + field);

            ObjectNode nullValue = valid.deepCopy();
            route(nullValue, 0).putNull(field);
            assertRejected(Stage.PLAN, nullValue, "null route " + field);
        }
        ObjectNode unknown = valid.deepCopy();
        route(unknown, 0).put("unknown", true);
        assertRejected(Stage.PLAN, unknown, "unknown route property");

        for (String routePath : List.of(
                "/orders.camel.yaml",
                "../orders.camel.yaml",
                "routes\\orders.camel.yaml",
                ".git/orders.camel.yaml",
                "target/orders.camel.yaml",
                "routes/wrong.camel.yaml",
                "routes/orders.yaml")) {
            ObjectNode invalid = valid.deepCopy();
            route(invalid, 0).put("routePath", routePath);
            assertRejected(Stage.PLAN, invalid, "unsafe route path " + routePath);
        }
        for (String testPath : List.of(
                "../orders.camel.it.yaml",
                "test/nested/orders.camel.it.yaml",
                "test/wrong.camel.it.yaml",
                "target/orders.camel.it.yaml")) {
            ObjectNode invalid = valid.deepCopy();
            route(invalid, 0).put("citrusTestPath", testPath);
            assertRejected(Stage.PLAN, invalid, "unsafe test path " + testPath);
        }
        for (String routeId : List.of("", "bad/id", ".orders", "a".repeat(129))) {
            ObjectNode invalid = valid.deepCopy();
            route(invalid, 0).put("routeId", routeId);
            assertRejected(Stage.PLAN, invalid, "invalid route ID " + routeId);
        }

        ObjectNode duplicate = valid.deepCopy();
        ((ArrayNode) artifactPolicy(duplicate).get("routes")).add(route(duplicate, 0).deepCopy());
        assertRejected(Stage.PLAN, duplicate, "duplicate route");

        ObjectNode nullRoute = valid.deepCopy();
        ((ArrayNode) artifactPolicy(nullRoute).get("routes")).addNull();
        assertRejected(Stage.PLAN, nullRoute, "null route");

        ObjectNode sorted = valid.deepCopy();
        ((ArrayNode) artifactPolicy(sorted).get("routes")).add(route(
                "payments",
                "src/main/resources/routes/payments.camel.yaml",
                "test/payments.camel.it.yaml"));
        assertEquals(2, parse(Stage.PLAN, sorted).artifactPolicy().routes().size());

        ObjectNode unsorted = valid.deepCopy();
        ArrayNode routes = (ArrayNode) artifactPolicy(unsorted).get("routes");
        JsonNode orders = routes.remove(0);
        routes.add(route(
                "payments",
                "src/main/resources/routes/payments.camel.yaml",
                "test/payments.camel.it.yaml"));
        routes.add(orders);
        assertRejected(Stage.PLAN, unsorted, "unsorted routes");
    }

    private static void assertPolicyMutationRejected(ObjectNode valid, String field, String value)
            throws Exception {
        ObjectNode invalid = valid.deepCopy();
        artifactPolicy(invalid).put(field, value);
        assertRejected(Stage.PLAN, invalid, "invalid " + field);
    }

    private static ShipStageResult parse(Stage stage, ObjectNode result) throws Exception {
        return ShipStageResult.parse(stage, JSON.writeValueAsString(result));
    }

    private static void assertRejected(Stage stage, ObjectNode result, String label) throws Exception {
        assertRejected(stage, JSON.writeValueAsString(result), label);
    }

    private static void assertRejected(Stage stage, String result, String label) {
        assertThrows(IOException.class, () -> ShipStageResult.parse(stage, result), label);
    }

    private static ObjectNode result(String pipelineId, JsonNode policy) {
        ObjectNode result = JSON.createObjectNode();
        result.put("schemaVersion", ShipStageResult.SCHEMA_VERSION);
        if (pipelineId == null) {
            result.putNull("pipelineId");
        } else {
            result.put("pipelineId", pipelineId);
        }
        result.put("report", "Stage report");
        if (policy == null) {
            result.putNull("artifactPolicy");
        } else {
            result.set("artifactPolicy", policy);
        }
        result.put("materialAmbiguity", false);
        return result;
    }

    private static ObjectNode policy(String camelVersion, String citrusVersion) {
        ObjectNode policy = JSON.createObjectNode();
        policy.put("runtime", "main");
        policy.put("camelVersion", camelVersion);
        policy.putNull("platformVersion");
        policy.putNull("springBootVersion");
        policy.put("routeDsl", "yaml");
        policy.put("expressionLanguage", "simple");
        policy.put("citrusVersion", citrusVersion);
        ArrayNode dependencies = policy.putArray("citrusDependencies");
        dependencies.add("org.citrusframework:citrus-camel:" + citrusVersion);
        dependencies.add("org.citrusframework:citrus-junit-jupiter:" + citrusVersion);
        dependencies.add("org.citrusframework:citrus-yaml:" + citrusVersion);
        policy.put("javaPolicy", "FORBIDDEN");
        policy.putArray("approvedJavaExceptions");
        policy.putArray("routes").add(route(
                "orders",
                "src/main/resources/routes/orders.camel.yaml",
                "test/orders.camel.it.yaml"));
        policy.put("citrusTestsRequired", true);
        policy.put("oneCitrusTestPerRoute", true);
        return policy;
    }

    private static ObjectNode route(String id, String routePath, String testPath) {
        return JSON.createObjectNode()
                .put("routeId", id)
                .put("routePath", routePath)
                .put("citrusTestPath", testPath);
    }

    private static ObjectNode artifactPolicy(ObjectNode result) {
        return (ObjectNode) result.get("artifactPolicy");
    }

    private static ObjectNode route(ObjectNode result, int index) {
        return (ObjectNode) artifactPolicy(result).withArray("routes").get(index);
    }
}
