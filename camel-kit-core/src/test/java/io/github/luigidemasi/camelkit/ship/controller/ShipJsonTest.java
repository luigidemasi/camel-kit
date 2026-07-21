package io.github.luigidemasi.camelkit.ship.controller;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import io.github.luigidemasi.camelkit.ship.context.InitialContext;
import io.github.luigidemasi.camelkit.ship.protocol.ShipStage;
import io.github.luigidemasi.camelkit.ship.protocol.StageResult;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShipJsonTest {

    @Test
    void initialContextRoundTripsThroughOneLogicalProvenanceShape() throws Exception {
        InitialContext context = InitialContext.fromSources(List.of(
                InitialContext.userText("build an orders route"),
                InitialContext.projectDocument("requirements/orders.md", "orders requirements")));

        String encoded = ShipJson.mapper().writeValueAsString(context);
        JsonNode tree = ShipJson.mapper().readTree(encoded);

        assertEquals(Set.of("kind", "sources", "digest"), fields(tree));
        assertEquals("composite", tree.get("kind").textValue());
        assertEquals("project:requirements/orders.md",
                tree.get("sources").get(1).get("provenance").textValue());
        assertFalse(encoded.contains("@class"));
        assertEquals(context, ShipJson.mapper().readValue(encoded, InitialContext.class));
        assertEquals(encoded, ShipJson.mapper().writeValueAsString(context));
    }

    @Test
    void initialContextRejectsUnknownFieldsAndNonLogicalDocumentProvenance() throws Exception {
        InitialContext context = InitialContext.fromSources(List.of(
                InitialContext.projectDocument("requirements.md", "requirements")));
        ObjectNode encoded = ShipJson.mapper().valueToTree(context);

        ObjectNode unknown = encoded.deepCopy();
        unknown.put("physicalRoot", "/tmp/project");
        assertThrows(IOException.class,
                () -> ShipJson.mapper().readValue(unknown.toString(), InitialContext.class));

        ObjectNode physical = encoded.deepCopy();
        ((ObjectNode) physical.withArray("sources").get(0)).put("provenance", "/tmp/requirements.md");
        assertThrows(IOException.class,
                () -> ShipJson.mapper().readValue(physical.toString(), InitialContext.class));
    }

    @Test
    void initialContextRejectsNoncanonicalDuplicateOccurrenceIds() throws Exception {
        InitialContext.Source repeated = InitialContext.userText("same");
        InitialContext context = InitialContext.fromSources(List.of(repeated, repeated));
        ObjectNode encoded = ShipJson.mapper().valueToTree(context);
        ObjectNode first = (ObjectNode) encoded.withArray("sources").get(0);
        ObjectNode second = (ObjectNode) encoded.withArray("sources").get(1);
        String firstId = first.get("id").textValue();
        first.put("id", second.get("id").textValue());
        second.put("id", firstId);

        assertThrows(IOException.class,
                () -> ShipJson.mapper().readValue(encoded.toString(), InitialContext.class));
    }

    @Test
    void rejectsDuplicateFieldsTrailingDocumentsAndExcessiveNesting() {
        assertThrows(IOException.class, () -> ShipJson.mapper().readTree("{\"a\":1,\"a\":2}"));
        assertThrows(IOException.class, () -> ShipJson.mapper().readTree("{} {}"));
        assertThrows(IOException.class, () -> ShipJson.mapper().readTree(
                "[".repeat(ShipJson.MAX_NESTING_DEPTH + 1)
                                                                         + "]".repeat(ShipJson.MAX_NESTING_DEPTH + 1)));
    }

    @Test
    void callerCannotMutateTheCanonicalMapper() throws Exception {
        var callerMapper = ShipJson.mapper();
        callerMapper.disable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

        assertNotSame(callerMapper, ShipJson.mapper());
        assertThrows(IOException.class, () -> ShipJson.mapper().readTree("{} {}"));
    }

    @Test
    void stageResultRejectsEveryMissingRequiredPropertyBeforeNormalization() {
        ObjectNode encoded = ShipJson.mapper().valueToTree(validFailedResult());

        for (String field : List.of(
                "schemaVersion",
                "runId",
                "stage",
                "attemptId",
                "challenge",
                "inputDigest",
                "outcome",
                "ledger",
                "question",
                "catalogRequests",
                "artifacts",
                "artifactManifest",
                "failureCode",
                "failureMessage")) {
            ObjectNode missing = encoded.deepCopy();
            missing.remove(field);
            assertThrows(IOException.class,
                    () -> ShipJson.mapper().readValue(missing.toString(), StageResult.class), field);
        }
    }

    @Test
    void stageResultRejectsExplicitNullCollections() {
        ObjectNode encoded = ShipJson.mapper().valueToTree(validFailedResult());
        for (String field : List.of("catalogRequests", "artifacts")) {
            ObjectNode invalid = encoded.deepCopy();
            invalid.putNull(field);
            assertThrows(IOException.class,
                    () -> ShipJson.mapper().readValue(invalid.toString(), StageResult.class), field);
        }
    }

    private static Set<String> fields(JsonNode node) {
        java.util.HashSet<String> fields = new java.util.HashSet<>();
        node.fieldNames().forEachRemaining(fields::add);
        return fields;
    }

    private static StageResult validFailedResult() {
        return new StageResult(
                StageResult.SCHEMA_VERSION,
                "ship-" + "0".repeat(32),
                ShipStage.DISCOVERY,
                "discovery-1-test",
                "challenge",
                "sha256:" + "0".repeat(64),
                StageResult.Outcome.FAILED,
                null,
                null,
                List.of(),
                List.of(),
                null,
                "worker-failed",
                "Worker failed");
    }
}
