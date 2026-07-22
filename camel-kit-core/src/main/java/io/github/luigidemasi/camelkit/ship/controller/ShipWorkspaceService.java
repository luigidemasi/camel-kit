package io.github.luigidemasi.camelkit.ship.controller;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.github.luigidemasi.camelkit.ship.protocol.ProducedArtifact;
import io.github.luigidemasi.camelkit.ship.protocol.ShipStage;
import io.github.luigidemasi.camelkit.ship.protocol.StageRequest;
import io.github.luigidemasi.camelkit.ship.protocol.StageResult;
import io.github.luigidemasi.camelkit.ship.protocol.StageResultValidator;

/** Imports one structurally valid stopped-worker result into controller-owned content storage. */
final class ShipWorkspaceService {

    private ShipWorkspaceService() {
    }

    static AcceptedWorkspace accept(
            StageRequest request,
            StageResult result,
            ShipProtectedWorkerBroker.CompletedAttempt completed,
            ShipBlobStore blobs,
            ShipBlobStore.Transaction transaction)
            throws IOException {
        if (blobs == null || transaction == null || completed == null) {
            throw new IllegalArgumentException("Ship blob store is required");
        }

        // This deliberately performs no worker-controlled file I/O. A malformed envelope
        // is rejected before StagedArtifactSource opens even one artifact.
        StageResultValidator.validateEnvelope(
                request, result, java.nio.file.Path.of(request.outputDirectory()));
        if (!blobs.belongsToRun(request.runId())) {
            throw new ShipControllerException(
                    "blob-run-mismatch", "Ship attempt output belongs to a different controller blob store");
        }
        List<ShipBlobStore.ImportedBlob> imported = completed.importArtifacts(
                request, result.artifacts(), transaction);
        if (imported.size() != result.artifacts().size()) {
            throw new IOException("Ship artifact import returned an incomplete receipt");
        }

        List<AcceptedArtifact> artifacts = new ArrayList<>(imported.size());
        for (int index = 0; index < imported.size(); index++) {
            ProducedArtifact claim = result.artifacts().get(index);
            ShipBlobStore.ImportedBlob item = imported.get(index);
            artifacts.add(new AcceptedArtifact(claim, item.reference(), item.unixMode()));
        }
        return new AcceptedWorkspace(
                request.runId(), request.stage(), request.attemptId(), result.inputDigest(), artifacts);
    }

    record AcceptedWorkspace(
            String runId,
            ShipStage stage,
            String attemptId,
            String inputDigest,
            List<AcceptedArtifact> artifacts) {

        AcceptedWorkspace {
            if (runId == null || runId.isBlank() || stage == null || attemptId == null || attemptId.isBlank()
                    || inputDigest == null) {
                throw new IllegalArgumentException("Invalid accepted Ship workspace identity");
            }
            artifacts = List.copyOf(artifacts);
        }
    }

    record AcceptedArtifact(ProducedArtifact claim, ShipBlobStore.BlobReference blob, int unixMode) {

        AcceptedArtifact {
            if (claim == null || blob == null
                    || !claim.kind().equals(blob.kind())
                    || !claim.digest().equals(blob.digest())
                    || claim.size() != blob.byteSize()
                    || unixMode != 0100644) {
                throw new IllegalArgumentException("Accepted Ship artifact does not match its controller blob");
            }
        }
    }
}
