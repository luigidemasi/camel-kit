package io.github.luigidemasi.camelkit.ship.conformance;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.luigidemasi.camelkit.ship.ShipDigest;
import io.github.luigidemasi.camelkit.ship.conformance.AdapterCertificationVerifier.Component;
import io.github.luigidemasi.camelkit.ship.conformance.AdapterCertificationVerifier.TrustPolicy;
import io.github.luigidemasi.camelkit.ship.conformance.AdapterCertificationVerifier.VerifiedLaunchDescriptor;
import io.github.luigidemasi.camelkit.ship.controller.ShipJson;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AdapterCertificationVerifierTest {

    private static final Instant NOW = Instant.parse("2026-07-23T12:00:00Z");
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

    private KeyPair signingKey;
    private Map<String, byte[]> blobs;
    private List<Component> components;

    @BeforeEach
    void setUp() throws Exception {
        signingKey = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        blobs = new LinkedHashMap<>();
        components = new ArrayList<>();
        component("runtime", "pi", "0.80.6", "pi-runtime");
        component("language-runtime", "node", "22.22.2", "node-runtime");
        component("package-closure", "pi-package-tree", "1", packageClosure());
        component("abi", "glibc", "2.42", "glibc-abi");
        component("adapter", "pi-native", "1.0.0", "pi-driver");
        component("ingress", "pi-ingress", "1.0.0", "pi-ingress");
        component("interaction", "gnome-polkit-fingerprint", "1.0.0", "interaction");
        component("launcher", "systemd-exec", "258.9", "launcher");
        component("runner", "pi-stage-runner", "1.0.0", "runner");
        component("sandbox", "bubblewrap", "0.11.0", "sandbox");
        component("provider-broker", "pi-provider-broker", "1.0.0", "provider");
        component("controller", "camel-kit-ship-controller", "0.3.2", "controller");
        component("protocol", "camel-kit-ship", "2", "protocol");
        component("conformance-suite", "camel-kit-ship-suite", "1", suite());
    }

    @Test
    void admitsOnlyTheCompleteSignedReleaseClosure() throws Exception {
        Bundle bundle = bundle(7, "2", components, validEvidence(false));

        VerifiedLaunchDescriptor descriptor = verify(bundle, policy(7, "2"));

        assertEquals("pi", descriptor.harnessFamily());
        assertEquals("0.80.6", descriptor.harnessVersion());
        assertEquals("pi-native", descriptor.adapterId());
        assertEquals("linux", descriptor.operatingSystem());
        assertEquals("x86_64", descriptor.architecture());
        assertEquals("22.22.2", descriptor.languageRuntimeVersion());
        assertEquals("glibc", descriptor.abiId());
        assertEquals("2.42", descriptor.abiVersion());
        assertEquals(14, descriptor.components().size());
        assertFalse(descriptor.components().values().stream()
                .map(Component::digest)
                .anyMatch(value -> !ShipDigest.isSha256(value)));
    }

    @Test
    void rejectsTamperedSignaturesAndUnsignedReplacementPacks() throws Exception {
        Bundle bundle = bundle(7, "2", components, validEvidence(false));
        byte[] tamperedSignature = bundle.signature().clone();
        tamperedSignature[0] ^= 1;
        assertInvalid(new Bundle(bundle.manifest(), tamperedSignature), policy(7, "2"));

        byte[] replacement = "{}".getBytes(StandardCharsets.UTF_8);
        blobs.put(ShipDigest.sha256(replacement), replacement);
        String manifest = new String(bundle.manifest(), StandardCharsets.UTF_8)
                .replace(bundle.packDigest(), ShipDigest.sha256(replacement));
        assertInvalid(new Bundle(manifest.getBytes(StandardCharsets.UTF_8), bundle.signature()), policy(7, "2"));
    }

    @Test
    void enforcesAntiDowngradeExpiryAndRevocation() throws Exception {
        Bundle bundle = bundle(7, "2", components, validEvidence(false));
        assertInvalid(bundle, policy(8, "2"));
        assertInvalid(bundle, policy(7, "3"));
        assertInvalid(bundle, new TrustPolicy(
                7, "2", Map.of("release-2026", signingKey.getPublic().getEncoded()),
                Set.of("release-2026"), Set.of()));
        assertInvalid(bundle, new TrustPolicy(
                7, "2", Map.of("release-2026", signingKey.getPublic().getEncoded()),
                Set.of(), Set.of(bundle.packDigest())));

        byte[] expiredManifest = manifest(bundle.packDigest(), 7, "2",
                "2026-07-20T00:00:00Z", "2026-07-21T00:00:00Z");
        assertInvalid(new Bundle(expiredManifest, sign(expiredManifest)), policy(7, "2"));
    }

    @Test
    void rejectsMissingDuplicateUnknownAndUnresolvedComponents() throws Exception {
        List<Component> missing = new ArrayList<>(components);
        missing.remove(0);
        assertInvalid(bundle(7, "2", missing, validEvidence(false)), policy(7, "2"));

        List<Component> duplicate = new ArrayList<>(components);
        duplicate.set(0, duplicate.get(1));
        assertInvalid(bundle(7, "2", duplicate, validEvidence(false)), policy(7, "2"));

        List<Component> unknown = new ArrayList<>(components);
        unknown.set(0, new Component("unknown", "unknown", "1", digest("unknown"), 7));
        blobs.put(digest("unknown"), "unknown".getBytes(StandardCharsets.UTF_8));
        assertInvalid(bundle(7, "2", unknown, validEvidence(false)), policy(7, "2"));

        Component runtime = components.get(0);
        blobs.remove(runtime.digest());
        assertInvalid(bundle(7, "2", components, validEvidence(false)), policy(7, "2"));
    }

    @Test
    void rejectsTupleSubstitutionAndAnyFailedMandatoryCheck() throws Exception {
        assertInvalid(bundle(7, "2", components,
                validEvidence(false).replace("\"harnessVersion\": \"0.80.6\"",
                        "\"harnessVersion\": \"0.80.5\"")),
                policy(7, "2"));
        assertInvalid(bundle(7, "2", components, validEvidence(true)), policy(7, "2"));
    }

    @Test
    void resolvesEveryCheckEvidenceBlobAndRejectsUnreferencedPackContent() throws Exception {
        Bundle bundle = bundle(7, "2", components, validEvidence(false));
        blobs.remove(digest("live-e2e"));
        assertInvalid(bundle, policy(7, "2"));

        bundle = bundle(7, "2", components, validEvidence(false));
        blobs.put(digest("unreferenced"), "unreferenced".getBytes(StandardCharsets.UTF_8));
        assertInvalid(bundle, policy(7, "2"));
    }

    @Test
    void resolvesEveryPortablePackageFileAndRejectsClosureSubstitution() throws Exception {
        Bundle bundle = bundle(7, "2", components, validEvidence(false));
        blobs.remove(digest("package-json"));
        assertInvalid(bundle, policy(7, "2"));

        setUp();
        List<Component> substituted = new ArrayList<>(components);
        Component closure = substituted.get(2);
        substituted.set(2, new Component(
                closure.kind(), closure.id(), closure.version(), digest("different"), 9));
        blobs.put(digest("different"), "different".getBytes(StandardCharsets.UTF_8));
        assertInvalid(bundle(7, "2", substituted, validEvidence(false)), policy(7, "2"));
    }

    private VerifiedLaunchDescriptor verify(Bundle bundle, TrustPolicy policy) {
        return AdapterCertificationVerifier.verify(
                bundle.manifest(),
                bundle.signature(),
                new AdapterCertificationVerifier.BlobResolver() {
                    @Override
                    public byte[] resolve(String digest, int maximumBytes) throws java.io.IOException {
                        byte[] value = blobs.get(digest);
                        if (value == null) {
                            throw new java.io.IOException("missing");
                        }
                        if (value.length > maximumBytes) {
                            throw new java.io.IOException("oversized");
                        }
                        return value.clone();
                    }

                    @Override
                    public Set<String> digests() {
                        return Set.copyOf(blobs.keySet());
                    }
                },
                policy,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private void assertInvalid(Bundle bundle, TrustPolicy policy) {
        assertThrows(IllegalArgumentException.class, () -> verify(bundle, policy));
    }

    private TrustPolicy policy(long floor, String protocol) {
        return new TrustPolicy(
                floor,
                protocol,
                Map.of("release-2026", signingKey.getPublic().getEncoded()),
                Set.of(),
                Set.of());
    }

    private Bundle bundle(
            long sequence, String protocol, List<Component> declared, String evidence)
            throws Exception {
        byte[] evidenceBytes = evidence.getBytes(StandardCharsets.UTF_8);
        String evidenceDigest = ShipDigest.sha256(evidenceBytes);
        blobs.put(evidenceDigest, evidenceBytes);
        byte[] pack = ShipJson.mapper().writeValueAsBytes(Map.of(
                "schemaVersion", 1,
                "releaseId", "camel-kit-0-3-2",
                "releaseVersion", "0.3.2",
                "protocolVersion", protocol,
                "conformanceEvidenceDigest", evidenceDigest,
                "components", declared));
        String packDigest = ShipDigest.sha256(pack);
        blobs.put(packDigest, pack);
        byte[] manifest = manifest(
                packDigest, sequence, protocol,
                "2026-07-23T00:00:00Z", "2026-08-23T00:00:00Z");
        return new Bundle(manifest, sign(manifest));
    }

    private byte[] manifest(
            String packDigest,
            long sequence,
            String protocol,
            String issuedAt,
            String expiresAt)
            throws Exception {
        return ShipJson.mapper().writeValueAsBytes(Map.of(
                "schemaVersion", 1,
                "releaseId", "camel-kit-0-3-2",
                "releaseVersion", "0.3.2",
                "releaseSequence", sequence,
                "protocolVersion", protocol,
                "keyId", "release-2026",
                "issuedAt", issuedAt,
                "expiresAt", expiresAt,
                "packDigest", packDigest));
    }

    private byte[] sign(byte[] manifest) throws Exception {
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(signingKey.getPrivate());
        signer.update(manifest);
        return signer.sign();
    }

    private void component(String kind, String id, String version, String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        String digest = ShipDigest.sha256(bytes);
        blobs.put(digest, bytes);
        components.add(new Component(kind, id, version, digest, bytes.length));
    }

    private String validEvidence(boolean failed) {
        Map<String, Component> byKind = components.stream()
                .collect(Collectors.toMap(Component::kind, value -> value));
        String checks = CHECKS.stream()
                .map(check -> {
                    blobs.put(digest(check), check.getBytes(StandardCharsets.UTF_8));
                    return String.format(Locale.ROOT, """
                            {
                              "checkId": "%s",
                              "result": "%s",
                              "evidenceDigest": "%s"
                            }
                            """, check, failed && check.equals("live-e2e") ? "fail" : "pass", digest(check)).strip();
                })
                .collect(Collectors.joining(",\n"));
        return String.format(Locale.ROOT, """
                {
                  "schemaVersion": 2,
                  "harnessFamily": "pi",
                  "harnessVersion": "%s",
                  "adapterId": "%s",
                  "adapterVersion": "%s",
                  "protocolVersion": "%s",
                  "operatingSystem": "linux",
                  "architecture": "x86_64",
                  "launchProfile": "systemd-bwrap-v1",
                  "runtimeProfile": "fedora-43-node-24",
                  "runtimeArtifactDigest": "%s",
                  "languageRuntimeVersion": "%s",
                  "languageRuntimeArtifactDigest": "%s",
                  "packageClosureDigest": "%s",
                  "abiId": "%s",
                  "abiVersion": "%s",
                  "driverDigest": "%s",
                  "ingressDigest": "%s",
                  "testSuiteDigest": "%s",
                  "checks": [
                %s
                  ]
                }
                """,
                byKind.get("runtime").version(),
                byKind.get("adapter").id(),
                byKind.get("adapter").version(),
                byKind.get("protocol").version(),
                byKind.get("runtime").digest(),
                byKind.get("language-runtime").version(),
                byKind.get("language-runtime").digest(),
                byKind.get("package-closure").digest(),
                byKind.get("abi").id(),
                byKind.get("abi").version(),
                byKind.get("adapter").digest(),
                byKind.get("ingress").digest(),
                byKind.get("conformance-suite").digest(),
                checks);
    }

    private String packageClosure() throws Exception {
        byte[] packageJson = "package-json".getBytes(StandardCharsets.UTF_8);
        byte[] empty = new byte[0];
        blobs.put(ShipDigest.sha256(packageJson), packageJson);
        blobs.put(ShipDigest.sha256(empty), empty);
        return new String(
                ShipJson.mapper().writeValueAsBytes(Map.of(
                        "schemaVersion", 1,
                        "entryPoint", Map.of(
                                "rootId", "pi",
                                "relativePath", "package.json",
                                "digest", ShipDigest.sha256(packageJson)),
                        "files", List.of(
                                Map.of(
                                        "rootId", "pi",
                                        "relativePath", "package.json",
                                        "digest", ShipDigest.sha256(packageJson),
                                        "byteSize", packageJson.length,
                                        "executable", true),
                                Map.of(
                                        "rootId", "pi",
                                        "relativePath", "resources/empty",
                                        "digest", ShipDigest.sha256(empty),
                                        "byteSize", 0,
                                        "executable", false)))),
                StandardCharsets.UTF_8);
    }

    private static String suite() throws Exception {
        try (InputStream input = AdapterCertificationVerifierTest.class.getClassLoader()
                .getResourceAsStream("ship/adapter-conformance-suite.json")) {
            if (input == null) {
                throw new IllegalStateException("missing suite");
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String digest(String value) {
        return ShipDigest.sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    private record Bundle(byte[] manifest, byte[] signature) {

        private String packDigest() throws Exception {
            @SuppressWarnings("unchecked")
            Map<String, Object> value = ShipJson.mapper().readValue(manifest, Map.class);
            return value.get("packDigest").toString();
        }
    }
}
