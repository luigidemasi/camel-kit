package io.github.luigidemasi.camelkit.ship.controller;

import java.time.Instant;
import java.util.List;

import io.github.luigidemasi.camelkit.ship.controller.ShipBlobStore.BlobReference;

/** Issues and replays a Stamp only from one persisted controller validation report. */
final class ShipStampService {

    private ShipStampService() {
    }

    static ShipStamp issue(
            ShipRunView run,
            ShipValidationReport report,
            ShipStamp.Status status,
            BlobReference waiverResponse,
            Instant generatedAt,
            String failureCode,
            String failureMessage) {
        requireBindings(run, report);
        List<ShipStamp.Waiver> waivers = List.of();
        if (status == ShipStamp.Status.COMPLETED_WITH_WAIVER) {
            List<ShipStamp.Check> failed = report.checks().stream()
                    .filter(ShipStamp.Check::mandatory)
                    .filter(check -> !check.passed())
                    .toList();
            if (failed.size() != 1
                    || !failed.get(0).id().matches("citrus-integration-test-[0-9]{3}")
                    || waiverResponse == null) {
                throw new ShipControllerException(
                        "waiver-policy-ineligible",
                        "Only one exact failed Citrus integration check can be waived");
            }
            ShipStamp.Check check = failed.get(0);
            waivers = List.of(new ShipStamp.Waiver(
                    check.id(), check.evidenceDigest(), waiverResponse.digest()));
        }
        return new ShipStamp(
                ShipStamp.SCHEMA_VERSION,
                run.runId(),
                status,
                run.adapterId(),
                run.requirementsDigest(),
                run.designDigest(),
                run.artifactManifest().digest(),
                run.candidateSnapshot().digest(),
                run.catalogUsage().digest(),
                report.checks(),
                waivers,
                generatedAt,
                failureCode,
                failureMessage);
    }

    static void verify(ShipRunView run, ShipValidationReport report, ShipStamp stamp) {
        requireBindings(run, report);
        if (stamp == null
                || !run.runId().equals(stamp.runId())
                || !run.adapterId().equals(stamp.adapterId())
                || !run.requirementsDigest().equals(stamp.requirementsDigest())
                || !run.designDigest().equals(stamp.designDigest())
                || !run.artifactManifest().digest().equals(stamp.artifactManifestDigest())
                || !run.candidateSnapshot().digest().equals(stamp.candidateSnapshotDigest())
                || !run.catalogUsage().digest().equals(stamp.catalogUsageDigest())
                || !report.checks().equals(stamp.checks())) {
            throw new ShipControllerException(
                    "stamp-binding-invalid", "Ship Stamp differs from durable run evidence");
        }
    }

    private static void requireBindings(ShipRunView run, ShipValidationReport report) {
        if (run == null || report == null
                || run.artifactManifest() == null
                || run.candidateSnapshot() == null
                || run.catalogUsage() == null
                || !run.runId().equals(report.runId())
                || !run.artifactManifest().digest().equals(report.artifactManifestDigest())
                || !run.candidateSnapshot().digest().equals(report.candidateSnapshotDigest())
                || !run.catalogUsage().digest().equals(report.catalogUsageDigest())) {
            throw new ShipControllerException(
                    "validation-report-binding-invalid",
                    "Controller validation report differs from the durable run inputs");
        }
    }
}
