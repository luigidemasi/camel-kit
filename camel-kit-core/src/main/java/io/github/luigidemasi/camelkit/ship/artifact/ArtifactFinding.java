package io.github.luigidemasi.camelkit.ship.artifact;

/** Deterministic artifact-manifest validation result. */
public record ArtifactFinding(Severity severity, String code, String path, String message) {

    public enum Severity {
        ERROR,
        WARNING
    }
}
