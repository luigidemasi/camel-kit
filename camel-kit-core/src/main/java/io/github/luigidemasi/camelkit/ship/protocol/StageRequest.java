package io.github.luigidemasi.camelkit.ship.protocol;

import java.util.List;

/** Immutable request for exactly one controller-managed worker attempt. */
public record StageRequest(
        int schemaVersion,
        String runId,
        ShipStage stage,
        String attemptId,
        int attempt,
        String idempotencyKey,
        String challenge,
        String inputDigest,
        String policyDigest,
        String sourceDirectory,
        String sourceSnapshotReference,
        String projectSourceManifestReference,
        String contextReference,
        String ledgerReference,
        String catalogEvidenceReference,
        String approvedDesignReference,
        String planReference,
        String interactionReference,
        String artifactManifestReference,
        String candidateDirectory,
        List<String> evidenceReferences,
        String failureCode,
        String failureMessage,
        String workerContractReference,
        String outputDirectory,
        StageCapability capability) {

    public static final int SCHEMA_VERSION = 1;

    public StageRequest {
        evidenceReferences = evidenceReferences == null ? List.of() : List.copyOf(evidenceReferences);
    }
}
