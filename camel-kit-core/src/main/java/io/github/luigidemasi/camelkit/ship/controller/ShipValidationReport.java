package io.github.luigidemasi.camelkit.ship.controller;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import io.github.luigidemasi.camelkit.ship.ShipDigest;
import io.github.luigidemasi.camelkit.ship.controller.ShipBlobStore.BlobReference;
import io.github.luigidemasi.camelkit.ship.controller.ShipEvidenceVerifier.RetainedEvidence;
import io.github.luigidemasi.camelkit.ship.evidence.CommandEvidence;
import io.github.luigidemasi.camelkit.ship.evidence.EvidenceCommand;

/** Controller-authored replay bundle for the exact checks summarized by a Ship Stamp. */
record ShipValidationReport(
        int schemaVersion,
        String runId,
        String artifactManifestDigest,
        String candidateSnapshotDigest,
        String catalogUsageDigest,
        BlobReference workerValidation,
        List<ShipStamp.Check> checks,
        List<BlobReference> evidence,
        Instant generatedAt) {

    static final int SCHEMA_VERSION = 1;

    ShipValidationReport {
        checks = checks == null ? List.of() : new ArrayList<>(checks);
        evidence = evidence == null ? List.of() : new ArrayList<>(evidence);
        if (schemaVersion != SCHEMA_VERSION
                || runId == null || !runId.matches("ship-[0-9a-f]{32}")
                || !ShipDigest.isSha256(artifactManifestDigest)
                || !ShipDigest.isSha256(candidateSnapshotDigest)
                || !ShipDigest.isSha256(catalogUsageDigest)
                || workerValidation == null
                || generatedAt == null
                || checks.isEmpty()
                || checks.size() > 64
                || checks.size() != evidence.size()) {
            throw new IllegalArgumentException("Invalid controller validation report");
        }

        HashSet<String> checkIds = new HashSet<>();
        HashSet<String> evidenceDigests = new HashSet<>();
        for (int index = 0; index < checks.size(); index++) {
            ShipStamp.Check check = checks.get(index);
            BlobReference reference = evidence.get(index);
            if (check == null
                    || reference == null
                    || !"ship-check-evidence".equals(reference.kind())
                    || !checkIds.add(check.id())
                    || !evidenceDigests.add(reference.digest())
                    || !check.evidenceDigest().equals(reference.digest())) {
                throw new IllegalArgumentException("Validation check differs from its retained evidence");
            }
        }
        checks = List.copyOf(checks);
        evidence = List.copyOf(evidence);
    }
}

record ShipCheckEvidence(
        int schemaVersion,
        String runId,
        String checkId,
        boolean mandatory,
        boolean passed,
        List<BlobReference> subjects,
        EvidenceCommand command,
        CommandEvidence commandEvidence,
        RetainedEvidence retainedEvidence) {

    static final int SCHEMA_VERSION = 1;

    ShipCheckEvidence {
        subjects = subjects == null ? List.of() : new ArrayList<>(subjects);
        if (schemaVersion != SCHEMA_VERSION
                || runId == null || !runId.matches("ship-[0-9a-f]{32}")
                || checkId == null || !checkId.matches("[a-z0-9][a-z0-9-]{0,127}")
                || subjects.isEmpty()
                || subjects.size() > 64
                || (command == null) != (commandEvidence == null)
                || (command == null) != (retainedEvidence == null)) {
            throw new IllegalArgumentException("Invalid Ship check evidence");
        }

        HashSet<String> subjectDigests = new HashSet<>();
        for (BlobReference subject : subjects) {
            if (subject == null || !subjectDigests.add(subject.digest())) {
                throw new IllegalArgumentException(
                        "Ship check evidence subjects must be nonnull and unique");
            }
        }
        subjects = List.copyOf(subjects);
    }
}
