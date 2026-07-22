package io.github.luigidemasi.camelkit.ship.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import io.github.luigidemasi.camelkit.ship.protocol.ProducedArtifact;
import io.github.luigidemasi.camelkit.ship.protocol.ShipStage;
import io.github.luigidemasi.camelkit.ship.protocol.StageRequest;
import io.github.luigidemasi.camelkit.ship.protocol.StageResult;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipStageResultReaderTest {

    private static final String RUN_ID = "ship-" + "1".repeat(32);
    private static final String DIGEST = "sha256:" + "2".repeat(64);

    @TempDir
    Path temporaryDirectory;

    @Test
    void acceptsAValidArtifactFreeResult() throws Exception {
        StageRequest request = request(ShipStage.DISCOVERY);
        StageResult expected = failed(request);

        StageResult actual = ShipStageResultReader.read(
                request,
                Path.of(request.outputDirectory()),
                ShipJson.mapper().writeValueAsBytes(expected));

        assertEquals(expected, actual);
    }

    @Test
    void validatesTheWireSchemaBeforeTypedSemanticValidation() throws Exception {
        StageRequest request = request(ShipStage.DISCOVERY);
        StageResult invalidVersion = new StageResult(
                2,
                request.runId(),
                request.stage(),
                request.attemptId(),
                request.challenge(),
                request.inputDigest(),
                StageResult.Outcome.FAILED,
                null,
                null,
                List.of(),
                List.of(),
                null,
                "worker-failed",
                "Worker failed");

        IOException failure = assertThrows(
                IOException.class,
                () -> ShipStageResultReader.read(
                        request,
                        Path.of(request.outputDirectory()),
                        ShipJson.mapper().writeValueAsBytes(invalidVersion)));

        assertTrue(failure.getMessage().startsWith("Stage result failed JSON Schema validation:"));
    }

    @Test
    void rejectsArtifactBearingResultsUntilTheBrokerImportBoundaryExists() throws Exception {
        StageRequest request = request(ShipStage.DESIGN);
        StageResult result = new StageResult(
                StageResult.SCHEMA_VERSION,
                request.runId(),
                request.stage(),
                request.attemptId(),
                request.challenge(),
                request.inputDigest(),
                StageResult.Outcome.COMPLETED,
                null,
                null,
                List.of(),
                List.of(new ProducedArtifact("design", "design.md", DIGEST, 1)),
                null,
                null,
                null);

        IOException failure = assertThrows(
                IOException.class,
                () -> ShipStageResultReader.read(
                        request,
                        Path.of(request.outputDirectory()),
                        ShipJson.mapper().writeValueAsBytes(result)));

        assertTrue(failure.getMessage().contains("production broker/import boundary"));
        assertEquals(
                result,
                ShipStageResultReader.readBrokered(
                        request, ShipJson.mapper().writeValueAsBytes(result)));
    }

    @Test
    void rejectsNullEmptyMalformedDuplicateAndOversizedInput() {
        StageRequest request = request(ShipStage.DISCOVERY);
        Path output = Path.of(request.outputDirectory());

        assertThrows(IOException.class, () -> ShipStageResultReader.read(request, output, null));
        assertThrows(IOException.class, () -> ShipStageResultReader.read(request, output, new byte[0]));
        assertThrows(
                IOException.class,
                () -> ShipStageResultReader.read(
                        request, output, "{not-json".getBytes(StandardCharsets.UTF_8)));
        assertThrows(
                IOException.class,
                () -> ShipStageResultReader.read(
                        request,
                        output,
                        "{\"schemaVersion\":1,\"schemaVersion\":1}"
                                .getBytes(StandardCharsets.UTF_8)));
        assertThrows(
                IOException.class,
                () -> ShipStageResultReader.read(
                        request, output, new byte[ShipStageResultReader.MAX_RESULT_BYTES + 1]));
    }

    private StageRequest request(ShipStage stage) {
        Path output = temporaryDirectory.resolve(stage.name().toLowerCase(java.util.Locale.ROOT));
        return new ShipAttemptFactory().create(
                RUN_ID,
                stage,
                1,
                DIGEST,
                new ShipAttemptFactory.Inputs(
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of()),
                "workers/" + stage.name().toLowerCase(java.util.Locale.ROOT) + ".md",
                output.toAbsolutePath().normalize().toString(),
                null,
                null);
    }

    private static StageResult failed(StageRequest request) {
        return new StageResult(
                StageResult.SCHEMA_VERSION,
                request.runId(),
                request.stage(),
                request.attemptId(),
                request.challenge(),
                request.inputDigest(),
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
