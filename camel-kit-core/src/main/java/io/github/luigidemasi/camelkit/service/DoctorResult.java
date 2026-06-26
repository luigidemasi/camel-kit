package io.github.luigidemasi.camelkit.service;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Result of validating a generated Camel-Kit workspace.
 */
public record DoctorResult(Path projectDir, List<DoctorFinding> findings) {

    public DoctorResult {
        Objects.requireNonNull(projectDir, "projectDir");
        findings = List.copyOf(Objects.requireNonNull(findings, "findings"));
    }

    public boolean hasFailures() {
        return findings.stream().anyMatch(finding -> finding.status() == DoctorFinding.Status.FAIL);
    }

    public long count(DoctorFinding.Status status) {
        return findings.stream().filter(finding -> finding.status() == status).count();
    }
}
