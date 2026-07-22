package io.github.luigidemasi.camelkit.ship.controller;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;

import io.github.luigidemasi.camelkit.ship.ShipDigest;
import io.github.luigidemasi.camelkit.ship.protocol.ProducedArtifact;
import io.github.luigidemasi.camelkit.ship.protocol.ShipStage;
import io.github.luigidemasi.camelkit.ship.protocol.StageCapability;
import io.github.luigidemasi.camelkit.ship.protocol.StageCapability.Operation;
import io.github.luigidemasi.camelkit.ship.protocol.StageCapability.RepositoryAccess;
import io.github.luigidemasi.camelkit.ship.protocol.StageRequest;
import io.github.luigidemasi.camelkit.ship.protocol.StageResult;
import io.github.luigidemasi.camelkit.ship.protocol.StageResultValidationException;
import io.github.luigidemasi.camelkit.ship.security.StagedArtifactSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

@EnabledOnOs(OS.LINUX)
class ShipWorkspaceServiceTest {

    private static final String RUN_ID = "ship-" + "2".repeat(32);

    @TempDir
    Path temporaryDirectory;

    Path runRoot;
    Path output;
    ShipBlobStore blobs;

    @BeforeEach
    void createWorkspace() throws Exception {
        Path stateRoot = Files.createDirectory(
                temporaryDirectory.resolve("state"),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        runRoot = Files.createDirectory(
                stateRoot.resolve(RUN_ID),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        output = Files.createDirectory(temporaryDirectory.resolve("output"));
        blobs = ShipBlobStore.create(stateRoot, RUN_ID);
    }

    @Test
    void invalidEnvelopeIsRejectedBeforeAnyClaimedFileIsOpened() throws Exception {
        ProducedArtifact missing = new ProducedArtifact(
                "design", "missing.md", "sha256:" + "0".repeat(64), 8);
        StageRequest request = request();
        StageResult forged = new StageResult(
                StageResult.SCHEMA_VERSION,
                request.runId(),
                request.stage(),
                request.attemptId(),
                "wrong-challenge",
                request.inputDigest(),
                StageResult.Outcome.COMPLETED,
                null,
                null,
                List.of(),
                List.of(missing),
                null,
                null,
                null);

        StageResultValidationException error = assertThrows(
                StageResultValidationException.class,
                () -> accept(request, forged));

        org.junit.jupiter.api.Assertions.assertTrue(error.getMessage().contains("challenge"));
        assertEquals(0, entryCount(runRoot.resolve("blobs")));
        assertEquals(0, entryCount(runRoot.resolve("quarantine")));
    }

    @Test
    void acceptedReceiptIsImmutableAndNoLongerDependsOnWorkerPath() throws Exception {
        byte[] accepted = "accepted design".getBytes(StandardCharsets.UTF_8);
        Path staged = Files.write(output.resolve("design.md"), accepted);
        Files.setPosixFilePermissions(staged, PosixFilePermissions.fromString("rwxrwxrwx"));
        ProducedArtifact artifact = new ProducedArtifact(
                "design", "design.md", ShipDigest.sha256(accepted), accepted.length);
        StageRequest request = request();
        StageResult result = result(request, artifact);

        ShipWorkspaceService.AcceptedWorkspace workspace = accept(request, result);
        Files.writeString(output.resolve("design.md"), "mutated after acceptance");

        assertEquals(RUN_ID, workspace.runId());
        assertEquals(artifact, workspace.artifacts().get(0).claim());
        assertEquals(0100644, workspace.artifacts().get(0).unixMode());
        assertArrayEquals(
                accepted,
                blobs.readBytes(workspace.artifacts().get(0).blob(), accepted.length));
        assertThrows(UnsupportedOperationException.class, () -> workspace.artifacts().clear());
        assertFalse(java.util.Arrays.stream(ShipWorkspaceService.AcceptedArtifact.class.getRecordComponents())
                .anyMatch(component -> Path.class.equals(component.getType())));
    }

    private StageRequest request() {
        StageCapability capability = new StageCapability(
                RepositoryAccess.READ_WITH_STAGED_OUTPUT,
                List.of(output.toString()),
                List.of(output.toString()),
                List.of(Operation.READ, Operation.WRITE_STAGED_ARTIFACT, Operation.RETURN_STRUCTURED_RESULT),
                false,
                false);
        return new StageRequest(
                StageRequest.SCHEMA_VERSION,
                RUN_ID,
                ShipStage.DESIGN,
                "attempt-1",
                1,
                "idempotency",
                "challenge",
                "sha256:" + "1".repeat(64),
                "sha256:" + "2".repeat(64),
                null,
                null,
                null,
                "context.json",
                "ledger.json",
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                null,
                null,
                "contract.md",
                output.toString(),
                capability);
    }

    private static StageResult result(StageRequest request, ProducedArtifact artifact) {
        return new StageResult(
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
                List.of(artifact),
                null,
                null,
                null);
    }

    private ShipWorkspaceService.AcceptedWorkspace accept(
            StageRequest request, StageResult result)
            throws Exception {
        byte[] encoded = ShipJson.mapper().writeValueAsBytes(result);
        ShipProtectedWorkerBroker broker = new ShipProtectedWorkerBroker(
                new ShipController(temporaryDirectory.resolve("unused-controller")),
                ignored -> new ShipProtectedWorkerBroker.Custody() {
                    @Override
                    public void requireExactAttempt(StageRequest expected) {
                    }

                    @Override
                    public byte[] readResultBytes() {
                        return encoded;
                    }

                    @Override
                    public StagedArtifactSource.Session openArtifactSource() throws java.io.IOException {
                        return StagedArtifactSource.open(output);
                    }

                    @Override
                    public void close() {
                    }
                });
        try (ShipProtectedWorkerBroker.CompletedAttempt completed = broker.acquire(request);
             ShipBlobStore.Transaction transaction = blobs.beginTransaction()) {
            ShipWorkspaceService.AcceptedWorkspace workspace = ShipWorkspaceService.accept(
                    request, result, completed, blobs, transaction);
            transaction.commit();
            return workspace;
        }
    }

    private static long entryCount(Path directory) throws Exception {
        try (var entries = Files.list(directory)) {
            return entries.count();
        }
    }
}
