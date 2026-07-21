package io.github.luigidemasi.camelkit.ship.protocol;

import java.util.List;
import java.util.Objects;

import io.github.luigidemasi.camelkit.ship.artifact.ArtifactManifest;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogSubject;
import io.github.luigidemasi.camelkit.ship.ledger.DecisionLedger;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Structured result returned by a bounded worker. It contains no requested next state. */
public record StageResult(
        @JsonProperty(value = "schemaVersion", required = true) int schemaVersion,
        @JsonProperty(value = "runId", required = true) String runId,
        @JsonProperty(value = "stage", required = true) ShipStage stage,
        @JsonProperty(value = "attemptId", required = true) String attemptId,
        @JsonProperty(value = "challenge", required = true) String challenge,
        @JsonProperty(value = "inputDigest", required = true) String inputDigest,
        @JsonProperty(value = "outcome", required = true) Outcome outcome,
        @JsonProperty(value = "ledger", required = true) DecisionLedger ledger,
        @JsonProperty(value = "question", required = true) DecisionLedger.Question question,
        @JsonProperty(value = "catalogRequests", required = true) List<CatalogSubject> catalogRequests,
        @JsonProperty(value = "artifacts", required = true) List<ProducedArtifact> artifacts,
        @JsonProperty(value = "artifactManifest", required = true) ArtifactManifest artifactManifest,
        @JsonProperty(value = "failureCode", required = true) String failureCode,
        @JsonProperty(value = "failureMessage", required = true) String failureMessage) {

    public static final int SCHEMA_VERSION = 1;

    public StageResult {
        catalogRequests = List.copyOf(Objects.requireNonNull(catalogRequests, "catalogRequests"));
        artifacts = List.copyOf(Objects.requireNonNull(artifacts, "artifacts"));
    }

    public enum Outcome {
        COMPLETED,
        NEEDS_USER_INPUT,
        NEEDS_DISCOVERY,
        FAILED
    }
}
