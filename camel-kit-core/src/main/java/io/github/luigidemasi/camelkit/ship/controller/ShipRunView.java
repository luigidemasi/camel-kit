package io.github.luigidemasi.camelkit.ship.controller;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.github.luigidemasi.camelkit.ship.ShipDigest;
import io.github.luigidemasi.camelkit.ship.controller.ShipBlobStore.BlobReference;
import io.github.luigidemasi.camelkit.ship.protocol.ShipStage;
import io.github.luigidemasi.camelkit.ship.protocol.StageRequest;

/** Immutable read-model shape; PR 5 owns reconstruction from integrity-checked event and blob records. */
public record ShipRunView(
        ShipRun authority,
        String eventDigest,
        Path projectRoot,
        String adapterId,
        BlobReference nativeSessionEvidence,
        BlobReference baselineSnapshot,
        BlobReference sourceSnapshot,
        BlobReference projectSourceManifest,
        Path sourceDirectory,
        BlobReference context,
        BlobReference ledger,
        BlobReference catalogEvidence,
        String requirementsDigest,
        BlobReference design,
        String designDigest,
        BlobReference plan,
        BlobReference latestInteraction,
        BlobReference interactionBundle,
        StageRequest activeRequest,
        BlobReference activeRequestReference,
        ShipInteractionBundle.Exchange pendingInteraction,
        BlobReference artifactManifest,
        BlobReference catalogUsage,
        BlobReference executionResult,
        BlobReference candidateSnapshot,
        Path candidateDirectory,
        List<BlobReference> evidence,
        Map<String, BlobReference> evidenceById,
        BlobReference validationReport,
        BlobReference stamp,
        BlobReference failedRequest,
        ShipStage failedStage,
        String failureCode,
        String failureMessage) {

    public ShipRunView {
        Objects.requireNonNull(authority, "Ship lifecycle authority");
        if (!ShipDigest.isSha256(eventDigest)) {
            throw new IllegalArgumentException("Ship run view requires a content-addressed event digest");
        }
        if (projectRoot == null || !projectRoot.isAbsolute() || !projectRoot.normalize().equals(projectRoot)) {
            throw new IllegalArgumentException("Ship run view project root must be absolute and normalized");
        }
        if (adapterId == null || !adapterId.matches("[a-z][a-z0-9-]{0,63}")) {
            throw new IllegalArgumentException("Ship run view adapter ID is invalid");
        }
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        evidenceById = evidenceById == null ? Map.of() : Map.copyOf(evidenceById);
        if (evidence.stream().anyMatch(Objects::isNull)
                || evidenceById.entrySet().stream().anyMatch(entry -> entry.getKey() == null
                        || entry.getKey().isBlank()
                        || entry.getValue() == null)) {
            throw new IllegalArgumentException("Ship run view evidence references are invalid");
        }
        if (activeRequest != null && !authority.id().toString().equals(activeRequest.runId())) {
            throw new IllegalArgumentException("Active request belongs to a different Ship run");
        }
        if (pendingInteraction != null && !pendingInteraction.pending()) {
            throw new IllegalArgumentException("Projected pending interaction already has a response");
        }
        if (pendingInteraction != null
                && !authority.id().toString().equals(pendingInteraction.runId())) {
            throw new IllegalArgumentException("Projected pending interaction belongs to a different Ship run");
        }
    }

    public String runId() {
        return authority.id().toString();
    }

    public ShipState state() {
        return authority.state();
    }

    public long revision() {
        return authority.revision();
    }

    public boolean terminal() {
        return authority.terminal();
    }
}
