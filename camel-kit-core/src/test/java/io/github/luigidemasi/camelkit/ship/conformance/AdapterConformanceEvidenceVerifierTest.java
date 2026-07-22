package io.github.luigidemasi.camelkit.ship.conformance;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import io.github.luigidemasi.camelkit.ship.ShipDigest;
import io.github.luigidemasi.camelkit.ship.conformance.AdapterConformanceEvidenceVerifier.ParsedEvidence;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AdapterConformanceEvidenceVerifierTest {

    private static final String SUITE_RESOURCE = "ship/adapter-conformance-suite.json";
    private static final String DIGEST = "sha256:" + "a".repeat(64);
    private static final List<String> CHECKS = List.of(
            "native-ingress",
            "optional-context-transport",
            "protected-interaction-binding",
            "immutable-launch",
            "capability-isolation",
            "worker-cancellation",
            "stopped-process-tree",
            "exclusive-output-custody",
            "artifact-import",
            "crash-resume-replay",
            "adversarial-containment",
            "live-e2e");

    @Test
    void parsesTheCompleteExactTupleWithoutDerivingStatus() throws Exception {
        ParsedEvidence evidence = verify(validEvidence(CHECKS));

        assertEquals("codex", evidence.harnessFamily());
        assertEquals("1.2.3", evidence.harnessVersion());
        assertEquals("codex-native", evidence.adapterId());
        assertEquals("2.0.0", evidence.adapterVersion());
        assertEquals("2", evidence.protocolVersion());
        assertEquals("linux", evidence.operatingSystem());
        assertEquals("amd64", evidence.architecture());
        assertEquals("native-v1", evidence.launchProfile());
        assertEquals("camel-main-v1", evidence.runtimeProfile());
        assertEquals(CHECKS, evidence.checks().stream().map(check -> check.checkId()).toList());
        assertEquals(12, evidence.checks().size());
    }

    @Test
    void acceptsFailedRawChecksWithoutTurningThemIntoAnAdmissionDecision() throws Exception {
        String raw = validEvidence(CHECKS).replaceFirst("\"result\": \"pass\"", "\"result\": \"fail\"");

        assertEquals("fail", verify(raw).checks().get(0).result());
    }

    @Test
    void rejectsMissingUnknownDuplicateAndReorderedMandatoryChecks() throws Exception {
        assertInvalid(validEvidence(CHECKS.subList(0, CHECKS.size() - 1)));

        List<String> unknown = new ArrayList<>(CHECKS);
        unknown.set(unknown.size() - 1, "unknown-check");
        assertInvalid(validEvidence(unknown));

        List<String> duplicate = new ArrayList<>(CHECKS);
        duplicate.set(duplicate.size() - 1, CHECKS.get(0));
        assertInvalid(validEvidence(duplicate));

        List<String> reordered = new ArrayList<>(CHECKS);
        String first = reordered.set(1, reordered.get(0));
        reordered.set(0, first);
        assertInvalid(validEvidence(reordered));
    }

    @Test
    void rejectsMissingDimensionsPathsRawStatusAndDigestMismatch() throws Exception {
        String valid = validEvidence(CHECKS);
        assertInvalid(valid.replace("  \"architecture\": \"amd64\",\n", ""));
        assertInvalid(valid.replace("\"harnessVersion\": \"1.2.3\"",
                "\"harnessVersion\": \"../1.2.3\""));
        assertInvalid(valid.replace("  \"schemaVersion\": 2,",
                "  \"schemaVersion\": 2,\n  \"runtimeArtifactPath\": \"/tmp/runtime.jar\","));
        assertInvalid(valid.replace("  \"schemaVersion\": 2,",
                "  \"schemaVersion\": 2,\n  \"status\": \"supported\","));
        assertInvalid(valid.replace(suiteDigest(), "sha256:" + "0".repeat(64)));
        assertInvalid(valid.replaceFirst(DIGEST, "sha256:invalid"));
    }

    @Test
    void rejectsDuplicateFieldsTrailingDocumentsAndOversizedInput() throws Exception {
        String valid = validEvidence(CHECKS);
        assertInvalid(valid.replace("  \"schemaVersion\": 2,",
                "  \"schemaVersion\": 2,\n  \"schemaVersion\": 2,"));
        assertInvalid(valid + "{}");
        assertThrows(IllegalArgumentException.class,
                () -> AdapterConformanceEvidenceVerifier.verify(new byte[64 * 1024 + 1]));
    }

    private static ParsedEvidence verify(String raw) {
        return AdapterConformanceEvidenceVerifier.verify(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static void assertInvalid(String raw) {
        assertThrows(IllegalArgumentException.class, () -> verify(raw));
    }

    private static String validEvidence(List<String> checks) throws IOException {
        String checkRows = checks.stream()
                .map(check -> String.format(Locale.ROOT, """
                          {
                            "checkId": "%s",
                            "result": "pass",
                            "evidenceDigest": "%s"
                          }
                        """, check, DIGEST).strip())
                .collect(Collectors.joining(",\n"));
        return String.format(Locale.ROOT, """
                {
                  "schemaVersion": 2,
                  "harnessFamily": "codex",
                  "harnessVersion": "1.2.3",
                  "adapterId": "codex-native",
                  "adapterVersion": "2.0.0",
                  "protocolVersion": "2",
                  "operatingSystem": "linux",
                  "architecture": "amd64",
                  "launchProfile": "native-v1",
                  "runtimeProfile": "camel-main-v1",
                  "runtimeArtifactDigest": "%s",
                  "driverDigest": "%s",
                  "ingressDigest": "%s",
                  "testSuiteDigest": "%s",
                  "checks": [
                %s
                  ]
                }
                """, DIGEST, DIGEST, DIGEST, suiteDigest(), checkRows);
    }

    private static String suiteDigest() throws IOException {
        try (InputStream input = AdapterConformanceEvidenceVerifierTest.class.getClassLoader()
                .getResourceAsStream(SUITE_RESOURCE)) {
            if (input == null) {
                throw new IOException("Missing test suite resource: " + SUITE_RESOURCE);
            }
            return ShipDigest.sha256(input.readAllBytes());
        }
    }
}
