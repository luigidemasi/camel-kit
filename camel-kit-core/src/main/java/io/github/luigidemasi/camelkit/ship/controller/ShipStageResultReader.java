package io.github.luigidemasi.camelkit.ship.controller;

import java.io.IOException;
import java.nio.file.Path;

import io.github.luigidemasi.camelkit.ship.protocol.StageRequest;
import io.github.luigidemasi.camelkit.ship.protocol.StageResult;
import io.github.luigidemasi.camelkit.ship.protocol.StageResultValidator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Decodes one bounded worker result without opening worker-controlled paths. */
final class ShipStageResultReader {

    static final int MAX_RESULT_BYTES = 4 * 1024 * 1024;

    private ShipStageResultReader() {
    }

    static byte[] boundedCopy(byte[] encoded) throws IOException {
        requireBounded(encoded);
        return encoded.clone();
    }

    static StageResult read(
            StageRequest request, Path attemptOutputDirectory, byte[] encoded)
            throws IOException {
        requireBounded(encoded);

        ObjectMapper mapper = ShipJson.mapper();
        JsonNode document = mapper.readTree(encoded);
        if (document == null) {
            throw new IOException("Stage result must contain one JSON document");
        }
        StageResultWireSchema.validate(document);
        StageResult result = mapper.treeToValue(document, StageResult.class);
        StageResultValidator.validateEnvelope(request, result, attemptOutputDirectory);
        if (!result.artifacts().isEmpty() || result.artifactManifest() != null) {
            throw new IOException(
                    "Artifact-bearing Ship results require the production broker/import boundary");
        }
        return result;
    }

    private static void requireBounded(byte[] encoded) throws IOException {
        if (encoded == null) {
            throw new IOException("Stage result bytes are required");
        }
        if (encoded.length == 0 || encoded.length > MAX_RESULT_BYTES) {
            throw new IOException(
                    "Stage result size must be between 1 and " + MAX_RESULT_BYTES + " bytes");
        }
    }
}
