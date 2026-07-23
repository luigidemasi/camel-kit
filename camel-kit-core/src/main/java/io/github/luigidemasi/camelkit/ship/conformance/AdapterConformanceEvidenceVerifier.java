package io.github.luigidemasi.camelkit.ship.conformance;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import io.github.luigidemasi.camelkit.ship.ShipDigest;
import io.github.luigidemasi.camelkit.ship.controller.ShipJson;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Strict parser for raw, non-admitting evidence from one exact adapter tuple. */
public final class AdapterConformanceEvidenceVerifier {

    private static final String SUITE_RESOURCE = "ship/adapter-conformance-suite.json";
    private static final int EVIDENCE_SCHEMA_VERSION = 2;
    private static final int SUITE_SCHEMA_VERSION = 1;
    private static final int MAX_EVIDENCE_BYTES = 64 * 1024;
    private static final int MAX_SUITE_BYTES = 16 * 1024;
    private static final int MAX_CHECKS = 64;
    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9-]{0,63}");
    private static final Pattern VERSION = Pattern.compile("[A-Za-z0-9][A-Za-z0-9+._-]{0,127}");
    private static final Pattern PROFILE = Pattern.compile("[a-z0-9][a-z0-9._-]{0,127}");
    private static final Set<String> OPERATING_SYSTEMS = Set.of("linux", "macos", "windows");
    private static final ObjectMapper JSON = ShipJson.mapper();

    private AdapterConformanceEvidenceVerifier() {
    }

    /** Parses and verifies raw conformance evidence without deriving support or runtime admission. */
    public static ParsedEvidence verify(byte[] encoded) {
        if (encoded == null || encoded.length == 0) {
            throw invalid("evidence must not be empty", null);
        }
        if (encoded.length > MAX_EVIDENCE_BYTES) {
            throw invalid("evidence exceeds " + MAX_EVIDENCE_BYTES + " bytes", null);
        }

        RawEvidence raw;
        try {
            raw = JSON.readValue(encoded, RawEvidence.class);
        } catch (IOException | IllegalArgumentException e) {
            throw invalid("could not parse evidence", e);
        }
        Suite suite = SuiteHolder.SUITE;
        validateTuple(raw, suite);
        List<CheckEvidence> checks = raw.checks().stream()
                .map(check -> new CheckEvidence(check.checkId(), check.result(), check.evidenceDigest()))
                .toList();
        return new ParsedEvidence(
                raw.harnessFamily(),
                raw.harnessVersion(),
                raw.adapterId(),
                raw.adapterVersion(),
                raw.protocolVersion(),
                raw.operatingSystem(),
                raw.architecture(),
                raw.launchProfile(),
                raw.runtimeProfile(),
                raw.runtimeArtifactDigest(),
                raw.driverDigest(),
                raw.ingressDigest(),
                raw.testSuiteDigest(),
                checks);
    }

    private static void validateTuple(RawEvidence raw, Suite suite) {
        if (raw == null || raw.schemaVersion() != EVIDENCE_SCHEMA_VERSION) {
            throw invalid("schemaVersion must be " + EVIDENCE_SCHEMA_VERSION, null);
        }
        require(raw.harnessFamily(), ID, "harnessFamily");
        require(raw.harnessVersion(), VERSION, "harnessVersion");
        require(raw.adapterId(), ID, "adapterId");
        require(raw.adapterVersion(), VERSION, "adapterVersion");
        require(raw.protocolVersion(), VERSION, "protocolVersion");
        if (!OPERATING_SYSTEMS.contains(raw.operatingSystem())) {
            throw invalid("operatingSystem is not canonical", null);
        }
        require(raw.architecture(), PROFILE, "architecture");
        require(raw.launchProfile(), PROFILE, "launchProfile");
        require(raw.runtimeProfile(), PROFILE, "runtimeProfile");
        requireDigest(raw.runtimeArtifactDigest(), "runtimeArtifactDigest");
        requireDigest(raw.driverDigest(), "driverDigest");
        requireDigest(raw.ingressDigest(), "ingressDigest");
        if (!suite.digest().equals(raw.testSuiteDigest())) {
            throw invalid("testSuiteDigest does not match the bundled mandatory suite", null);
        }
        validateChecks(raw.checks(), suite.mandatoryChecks());
    }

    private static void validateChecks(List<RawCheck> checks, List<String> mandatoryChecks) {
        if (checks == null || checks.size() > MAX_CHECKS) {
            throw invalid("checks must be a bounded array", null);
        }
        Set<String> seen = new HashSet<>();
        Set<String> duplicates = new LinkedHashSet<>();
        for (RawCheck check : checks) {
            if (check == null) {
                throw invalid("checks must not contain null", null);
            }
            require(check.checkId(), ID, "checkId");
            if (!seen.add(check.checkId())) {
                duplicates.add(check.checkId());
            }
            if (!"pass".equals(check.result()) && !"fail".equals(check.result())) {
                throw invalid("check result must be pass or fail", null);
            }
            requireDigest(check.evidenceDigest(), "evidenceDigest");
        }
        if (!duplicates.isEmpty()) {
            throw invalid("duplicate mandatory checks " + duplicates, null);
        }
        Set<String> missing = new LinkedHashSet<>(mandatoryChecks);
        missing.removeAll(seen);
        Set<String> unknown = new LinkedHashSet<>(seen);
        unknown.removeAll(mandatoryChecks);
        if (!missing.isEmpty() || !unknown.isEmpty()) {
            throw invalid("mandatory check mismatch; missing=" + missing + ", unknown=" + unknown, null);
        }
        if (!checks.stream().map(RawCheck::checkId).toList().equals(mandatoryChecks)) {
            throw invalid("mandatory checks are not in suite order", null);
        }
    }

    private static void require(String value, Pattern pattern, String field) {
        if (value == null || !pattern.matcher(value).matches()) {
            throw invalid(field + " is not canonical or contains path syntax", null);
        }
    }

    private static void requireDigest(String value, String field) {
        if (!ShipDigest.isSha256(value)) {
            throw invalid(field + " must be a canonical SHA-256 digest", null);
        }
    }

    private static Suite loadSuite() {
        InputStream input = AdapterConformanceEvidenceVerifier.class.getClassLoader()
                .getResourceAsStream(SUITE_RESOURCE);
        if (input == null) {
            throw new IllegalStateException("Ship adapter conformance suite not found: " + SUITE_RESOURCE);
        }
        try (input) {
            byte[] encoded = input.readNBytes(MAX_SUITE_BYTES + 1);
            if (encoded.length == 0 || encoded.length > MAX_SUITE_BYTES) {
                throw new IllegalStateException("Ship adapter conformance suite is empty or oversized");
            }
            SuiteManifest manifest = JSON.readValue(encoded, SuiteManifest.class);
            if (manifest.suiteSchemaVersion() != SUITE_SCHEMA_VERSION
                    || manifest.mandatoryChecks() == null
                    || manifest.mandatoryChecks().isEmpty()
                    || manifest.mandatoryChecks().size() > MAX_CHECKS) {
                throw new IllegalStateException("Invalid Ship adapter conformance suite identity");
            }
            List<String> checks = List.copyOf(manifest.mandatoryChecks());
            Set<String> unique = new HashSet<>();
            if (checks.stream().anyMatch(check -> check == null || !ID.matcher(check).matches())
                    || !checks.stream().allMatch(unique::add)) {
                throw new IllegalStateException("Invalid Ship adapter conformance suite checks");
            }
            return new Suite(checks, ShipDigest.sha256(encoded));
        } catch (IOException | IllegalArgumentException e) {
            throw new IllegalStateException("Could not parse Ship adapter conformance suite", e);
        }
    }

    private static IllegalArgumentException invalid(String message, Throwable cause) {
        return new IllegalArgumentException("Invalid Ship adapter conformance evidence: " + message, cause);
    }

    /** Parsed raw evidence. This value is neither a support decision nor an admission capability. */
    public record ParsedEvidence(
            String harnessFamily,
            String harnessVersion,
            String adapterId,
            String adapterVersion,
            String protocolVersion,
            String operatingSystem,
            String architecture,
            String launchProfile,
            String runtimeProfile,
            String runtimeArtifactDigest,
            String driverDigest,
            String ingressDigest,
            String testSuiteDigest,
            List<CheckEvidence> checks) {

        public ParsedEvidence {
            checks = List.copyOf(checks);
        }
    }

    /** One bounded raw check outcome and the digest of its separately retained evidence. */
    public record CheckEvidence(String checkId, String result, String evidenceDigest) {
    }

    private record RawEvidence(
            int schemaVersion,
            String harnessFamily,
            String harnessVersion,
            String adapterId,
            String adapterVersion,
            String protocolVersion,
            String operatingSystem,
            String architecture,
            String launchProfile,
            String runtimeProfile,
            String runtimeArtifactDigest,
            String driverDigest,
            String ingressDigest,
            String testSuiteDigest,
            List<RawCheck> checks) {
    }

    private record RawCheck(String checkId, String result, String evidenceDigest) {
    }

    private record SuiteManifest(int suiteSchemaVersion, List<String> mandatoryChecks) {
    }

    private record Suite(List<String> mandatoryChecks, String digest) {
    }

    private static final class SuiteHolder {

        private static final Suite SUITE = loadSuite();

        private SuiteHolder() {
        }
    }
}
