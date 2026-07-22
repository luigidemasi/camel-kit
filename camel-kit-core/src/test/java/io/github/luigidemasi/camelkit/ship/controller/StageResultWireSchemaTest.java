package io.github.luigidemasi.camelkit.ship.controller;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StageResultWireSchemaTest {

    @Test
    void packagedValidatorRejectsAnInvalidWorkerResult() {
        IOException failure = assertThrows(
                IOException.class,
                () -> StageResultWireSchema.validate(JsonMapper.builder().build().createObjectNode()));

        assertTrue(failure.getMessage().startsWith("Stage result failed JSON Schema validation:"));
    }

    @Test
    void closedLoaderRejectsAnUnknownSchemaIri() {
        String root = StageResultWireSchema.ROOT_SCHEMA_ID;
        String schema = """
                {
                  "$schema": "https://json-schema.org/draft/2020-12/schema",
                  "$id": "$ROOT",
                  "$ref": "https://untrusted.invalid/schema.json"
                }
                """.replace("$ROOT", root);

        assertThrows(SchemaException.class, () -> {
            Schema compiled = StageResultWireSchema.compile(Map.of(root, schema), root);
            compiled.validate(JsonMapper.builder().build().createObjectNode());
        });
    }

    @Test
    void needsDiscoverySchemaRejectsAnUnpresentedLedgerQuestionForEveryAllowedStage() throws Exception {
        for (String stage : List.of("DISCOVERY", "DESIGN", "REVIEW")) {
            ObjectNode poisoned = needsDiscovery(stage);
            IOException failure = assertThrows(
                    IOException.class,
                    () -> StageResultWireSchema.validate(poisoned),
                    stage);
            assertTrue(failure.getMessage().contains("openQuestions"), stage);

            ObjectNode valid = poisoned.deepCopy();
            ((ObjectNode) valid.path("ledger")).putArray("openQuestions");
            assertDoesNotThrow(() -> StageResultWireSchema.validate(valid), stage);
        }
    }

    @Test
    void producedArtifactPathRejectsControlCharacters() throws Exception {
        ObjectNode valid = completedDesign("design.md");
        assertDoesNotThrow(() -> StageResultWireSchema.validate(valid));

        for (char control : new char[]{'\n', '\u001f', '\u007f'}) {
            ObjectNode poisoned = valid.deepCopy();
            ((ObjectNode) poisoned.path("artifacts").path(0))
                    .put("relativePath", "design" + control + ".md");

            assertThrows(
                    IOException.class,
                    () -> StageResultWireSchema.validate(poisoned),
                    () -> "accepted control character U+"
                          + String.format(Locale.ROOT, "%04X", (int) control));
        }
    }

    private static ObjectNode needsDiscovery(String stage) throws Exception {
        String catalogRequests = "DISCOVERY".equals(stage)
                ? "[{\"kind\":\"COMPONENT\",\"name\":\"kafka\"}]"
                : "[]";
        JsonNode document = JsonMapper.builder().build().readTree("""
                {
                  "schemaVersion": 1,
                  "runId": "ship-11111111111111111111111111111111",
                  "stage": "$STAGE",
                  "attemptId": "$ATTEMPT",
                  "challenge": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                  "inputDigest": "sha256:2222222222222222222222222222222222222222222222222222222222222222",
                  "outcome": "NEEDS_DISCOVERY",
                  "ledger": {
                    "schemaVersion": 1,
                    "revision": 1,
                    "facts": [],
                    "decisions": [{
                      "id": "decision-runtime",
                      "category": "runtime",
                      "value": null,
                      "status": "NEEDS_USER_DECISION",
                      "sourceRefs": [],
                      "rationale": "Runtime is unresolved"
                    }],
                    "conflicts": [],
                    "assumptions": [],
                    "catalogEvidence": [],
                    "openQuestions": [{
                      "id": "question-runtime",
                      "openItemId": "decision-runtime",
                      "prompt": "Which runtime?",
                      "options": ["main"],
                      "recommendation": "main",
                      "status": "OPEN"
                    }],
                    "completeness": [],
                    "blockingOpenItemIds": ["decision-runtime"],
                    "gapReviewStatus": "NOT_RUN",
                    "requirementsPolicy": null
                  },
                  "question": null,
                  "catalogRequests": $CATALOG_REQUESTS,
                  "artifacts": [],
                  "artifactManifest": null,
                  "failureCode": null,
                  "failureMessage": null
                }
                """
                .replace("$STAGE", stage)
                .replace("$ATTEMPT", stage.toLowerCase(java.util.Locale.ROOT) + "-1-AAAAAAAAAAAAAAAA")
                .replace("$CATALOG_REQUESTS", catalogRequests));
        return (ObjectNode) document;
    }

    private static ObjectNode completedDesign(String relativePath) throws Exception {
        ObjectNode document = (ObjectNode) JsonMapper.builder().build().readTree("""
                {
                  "schemaVersion": 1,
                  "runId": "ship-11111111111111111111111111111111",
                  "stage": "DESIGN",
                  "attemptId": "design-1-AAAAAAAAAAAAAAAA",
                  "challenge": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                  "inputDigest": "sha256:2222222222222222222222222222222222222222222222222222222222222222",
                  "outcome": "COMPLETED",
                  "ledger": null,
                  "question": null,
                  "catalogRequests": [],
                  "artifacts": [{
                    "kind": "design",
                    "relativePath": "design.md",
                    "digest": "sha256:3333333333333333333333333333333333333333333333333333333333333333",
                    "size": 1
                  }],
                  "artifactManifest": null,
                  "failureCode": null,
                  "failureMessage": null
                }
                """);
        ((ObjectNode) document.path("artifacts").path(0)).put("relativePath", relativePath);
        return document;
    }
}
