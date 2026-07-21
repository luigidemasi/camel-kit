package io.github.luigidemasi.camelkit.ship.controller;

import java.io.IOException;
import java.util.Map;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaException;
import org.junit.jupiter.api.Test;

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
}
