package io.github.luigidemasi.camelkit.ship.artifact;

import java.util.List;

/** Immutable result of deterministic artifact validation. */
public record ArtifactValidationResult(List<ArtifactFinding> findings) {

    public ArtifactValidationResult {
        findings = findings == null ? List.of() : List.copyOf(findings);
    }

    public boolean passed() {
        return findings.stream().noneMatch(finding -> finding.severity() == ArtifactFinding.Severity.ERROR);
    }
}
