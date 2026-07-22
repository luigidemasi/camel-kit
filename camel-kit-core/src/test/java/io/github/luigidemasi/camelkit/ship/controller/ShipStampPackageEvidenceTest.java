package io.github.luigidemasi.camelkit.ship.controller;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShipStampPackageEvidenceTest {

    @Test
    void stampRequiresExactlyOneMandatoryPackageInspectionCheck() {
        List<ShipStamp.Check> checks = passingChecks();

        assertDoesNotThrow(() -> stamp(ShipStamp.Status.PASS, checks, List.of()));

        checks.removeIf(check -> "main-package-and-inspect".equals(check.id()));
        assertThrows(IllegalArgumentException.class,
                () -> stamp(ShipStamp.Status.PASS, checks, List.of()));

        checks.add(check("main-package-and-inspect", false, true));
        assertThrows(IllegalArgumentException.class,
                () -> stamp(ShipStamp.Status.PASS, checks, List.of()));
    }

    @Test
    void waiverStatusRequiresExactFailedMandatoryCheckEvidence() {
        List<ShipStamp.Check> checks = passingChecks();
        ShipStamp.Check failed = check("citrus-integration-test-001", true, false);
        checks.set(
                checks.indexOf(checks.stream()
                        .filter(check -> failed.id().equals(check.id()))
                        .findFirst()
                        .orElseThrow()),
                failed);

        ShipStamp.Waiver waiver = new ShipStamp.Waiver(
                failed.id(), failed.evidenceDigest(), digest(71));
        assertDoesNotThrow(
                () -> stamp(
                        ShipStamp.Status.COMPLETED_WITH_WAIVER,
                        checks,
                        List.of(waiver)));

        ShipStamp.Waiver mismatched = new ShipStamp.Waiver(
                failed.id(), digest(70), digest(71));
        assertThrows(
                IllegalArgumentException.class,
                () -> stamp(
                        ShipStamp.Status.COMPLETED_WITH_WAIVER,
                        checks,
                        List.of(mismatched)));
    }

    @Test
    void adapterIdUsesTheCanonicalRegistryGrammar() {
        assertThrows(
                IllegalArgumentException.class,
                () -> stamp(ShipStamp.Status.PASS, "Test Adapter", passingChecks(), List.of()));
    }

    private static List<ShipStamp.Check> passingChecks() {
        return new ArrayList<>(
                List.of(
                        check("artifact-policy", true, true),
                        check("catalog-usage", true, true),
                        check("bounded-validation-report", false, true),
                        check("route-schema", true, true),
                        check("main-package-and-inspect", true, true),
                        check("main-runtime-resolve-and-start", true, true),
                        check("citrus-integration-test-001", true, true),
                        check("candidate-integrity", true, true)));
    }

    private static ShipStamp stamp(
            ShipStamp.Status status, List<ShipStamp.Check> checks, List<ShipStamp.Waiver> waivers) {
        return stamp(status, "test-adapter", checks, waivers);
    }

    private static ShipStamp stamp(
            ShipStamp.Status status,
            String adapterId,
            List<ShipStamp.Check> checks,
            List<ShipStamp.Waiver> waivers) {
        return new ShipStamp(
                ShipStamp.SCHEMA_VERSION,
                "ship-" + "0".repeat(32),
                status,
                adapterId,
                digest(1),
                digest(2),
                digest(3),
                digest(4),
                digest(5),
                checks,
                waivers,
                Instant.EPOCH,
                null,
                null);
    }

    private static ShipStamp.Check check(String id, boolean mandatory, boolean passed) {
        return new ShipStamp.Check(id, mandatory, passed, digest(id.hashCode()));
    }

    private static String digest(int seed) {
        return "sha256:" + String.format(Locale.ROOT, "%064x", Integer.toUnsignedLong(seed));
    }
}
