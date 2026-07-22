package io.github.luigidemasi.camelkit.ship.controller;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import io.github.luigidemasi.camelkit.ship.ShipDigest;

/** Controller-derived final assessment value bound to retained check evidence. */
public record ShipStamp(
        int schemaVersion,
        String runId,
        Status status,
        String adapterId,
        String requirementsDigest,
        String designDigest,
        String artifactManifestDigest,
        String candidateSnapshotDigest,
        String catalogUsageDigest,
        List<Check> checks,
        List<Waiver> waivers,
        Instant generatedAt,
        String failureCode,
        String failureMessage) {

    public static final int SCHEMA_VERSION = 2;

    public ShipStamp {
        checks = checks == null ? List.of() : List.copyOf(checks);
        waivers = waivers == null ? List.of() : List.copyOf(waivers);
        if (schemaVersion != SCHEMA_VERSION
                || runId == null
                || !runId.matches("ship-[0-9a-f]{32}")) {
            throw new IllegalArgumentException("Invalid Ship Stamp identity");
        }
        Objects.requireNonNull(status, "Stamp status");
        if (adapterId == null || !adapterId.matches("[a-z][a-z0-9-]{0,63}")) {
            throw new IllegalArgumentException("Ship Stamp adapter ID is invalid");
        }
        requireDigest(requirementsDigest, "requirements digest");
        requireDigest(designDigest, "design digest");
        requireDigest(artifactManifestDigest, "artifact manifest digest");
        requireDigest(candidateSnapshotDigest, "candidate snapshot digest");
        requireDigest(catalogUsageDigest, "catalog usage digest");
        Objects.requireNonNull(generatedAt, "Stamp generatedAt");
        if (checks.isEmpty() || checks.size() > 64 || waivers.size() > 64) {
            throw new IllegalArgumentException("Ship Stamp requires 1..64 checks and at most 64 waivers");
        }

        Set<String> checkIds = new HashSet<>();
        for (Check check : checks) {
            if (check == null || !checkIds.add(check.id()) || check.evidenceDigest() == null) {
                throw new IllegalArgumentException("Ship Stamp checks require unique IDs");
            }
        }
        for (String required : Set.of(
                "artifact-policy",
                "catalog-usage",
                "route-schema",
                "main-package-and-inspect",
                "candidate-integrity")) {
            Check check = checks.stream()
                    .filter(candidate -> required.equals(candidate.id()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Ship Stamp is missing mandatory controller check " + required));
            if (!check.mandatory()) {
                throw new IllegalArgumentException("Controller check must be mandatory: " + required);
            }
        }
        requireCitrusChecks(checks);
        long runtimeChecks = checks.stream()
                .filter(check -> "main-runtime-resolve-and-start".equals(check.id()))
                .filter(Check::mandatory)
                .count();
        if (runtimeChecks != 1) {
            throw new IllegalArgumentException(
                    "Ship Stamp requires exactly one mandatory Main runtime resolution/start check");
        }
        Check report = checks.stream()
                .filter(check -> "bounded-validation-report".equals(check.id()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Ship Stamp is missing its bounded validation report"));
        if (report.mandatory() || !report.passed()) {
            throw new IllegalArgumentException("Bounded validation report must be a passing optional check");
        }

        boolean mandatoryFailed = checks.stream().anyMatch(check -> check.mandatory() && !check.passed());
        boolean anyFailed = checks.stream().anyMatch(check -> !check.passed());
        switch (status) {
            case PASS -> {
                if (anyFailed || !waivers.isEmpty() || failureCode != null || failureMessage != null) {
                    throw new IllegalArgumentException(
                            "PASS Stamp cannot contain failed checks, waivers, or failure details");
                }
            }
            case COMPLETED_WITH_WAIVER -> {
                if (failureCode != null || failureMessage != null || waivers.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Waiver Stamp requires exact waiver receipts and no failure details");
                }
                Set<String> failed = checks.stream()
                        .filter(Check::mandatory)
                        .filter(check -> !check.passed())
                        .map(Check::id)
                        .collect(java.util.stream.Collectors.toSet());
                Set<String> waived = new HashSet<>();
                for (Waiver waiver : waivers) {
                    Check check = checks.stream()
                            .filter(candidate -> candidate.id().equals(waiver.checkId()))
                            .findFirst()
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "Waiver references an unknown check"));
                    if (check.passed()
                            || !check.evidenceDigest().equals(waiver.evidenceDigest())
                            || !waived.add(waiver.checkId())) {
                        throw new IllegalArgumentException(
                                "Waiver does not exactly bind one failed check evidence record");
                    }
                }
                if (!failed.equals(waived)) {
                    throw new IllegalArgumentException(
                            "Waiver Stamp must cover every and only failed mandatory checks");
                }
            }
            case FAIL -> {
                if (!mandatoryFailed
                        || !waivers.isEmpty()
                        || failureCode == null
                        || failureMessage == null) {
                    throw new IllegalArgumentException(
                            "FAIL Stamp requires a failed mandatory check and failure details");
                }
                requireText(failureCode, "failure code");
                requireText(failureMessage, "failure message");
            }
        }
    }

    public enum Status {
        PASS,
        COMPLETED_WITH_WAIVER,
        FAIL
    }

    public record Check(String id, boolean mandatory, boolean passed, String evidenceDigest) {
        public Check {
            requireText(id, "check ID");
            requireDigest(evidenceDigest, "check evidence digest");
        }
    }

    /** Exact evidence and signed response which authorize one failed mandatory check. */
    public record Waiver(String checkId, String evidenceDigest, String responseDigest) {
        public Waiver {
            requireText(checkId, "waiver check ID");
            requireDigest(evidenceDigest, "waiver evidence digest");
            requireDigest(responseDigest, "waiver response digest");
        }
    }

    private static void requireCitrusChecks(List<Check> checks) {
        List<Check> citrusChecks = checks.stream()
                .filter(check -> check.id().matches("citrus-integration-test-[0-9]{3}"))
                .sorted(java.util.Comparator.comparing(Check::id))
                .toList();
        if (citrusChecks.isEmpty()) {
            throw new IllegalArgumentException("Ship Stamp requires at least one Citrus integration test check");
        }
        for (int index = 0; index < citrusChecks.size(); index++) {
            String expected = String.format(
                    Locale.ROOT, "citrus-integration-test-%03d", index + 1);
            Check check = citrusChecks.get(index);
            if (!expected.equals(check.id()) || !check.mandatory()) {
                throw new IllegalArgumentException(
                        "Ship Stamp Citrus checks must be consecutive mandatory checks starting at 001");
            }
        }
    }

    private static void requireText(String value, String label) {
        if (value == null || value.isBlank() || value.length() > 64 * 1024) {
            throw new IllegalArgumentException(label + " must contain 1..65536 characters");
        }
    }

    private static void requireDigest(String value, String label) {
        if (!ShipDigest.isSha256(value)) {
            throw new IllegalArgumentException(label + " must be a lowercase SHA-256 digest");
        }
    }
}
