package io.github.luigidemasi.camelkit.ship.conformance;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import io.github.luigidemasi.camelkit.ship.ShipDigest;
import io.github.luigidemasi.camelkit.ship.conformance.AdapterConformanceEvidenceVerifier.ParsedEvidence;
import io.github.luigidemasi.camelkit.ship.controller.ShipJson;

/**
 * Verifies one release-rooted adapter certification pack and returns a path-free launch identity.
 *
 * <p>
 * This verifier neither resolves a mutable installation nor launches a process. A production probe must first snapshot
 * the exact installation into immutable storage and match that snapshot to the returned component digests.
 */
public final class AdapterCertificationVerifier {

    static final int RELEASE_MANIFEST_SCHEMA_VERSION = 1;
    static final int PACK_SCHEMA_VERSION = 1;
    static final int MAX_MANIFEST_BYTES = 64 * 1024;
    static final int MAX_PACK_BYTES = 256 * 1024;
    static final int MAX_COMPONENT_BYTES = 256 * 1024 * 1024;
    static final int MAX_SIGNATURE_BYTES = 128;

    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9-]{0,63}");
    private static final Pattern VERSION = Pattern.compile("[A-Za-z0-9][A-Za-z0-9+._-]{0,127}");
    private static final Set<String> REQUIRED_COMPONENTS = Set.of(
            "runtime",
            "language-runtime",
            "package-closure",
            "abi",
            "adapter",
            "ingress",
            "interaction",
            "launcher",
            "runner",
            "sandbox",
            "provider-broker",
            "controller",
            "protocol",
            "conformance-suite");

    private AdapterCertificationVerifier() {
    }

    public static VerifiedLaunchDescriptor verify(
            byte[] releaseManifest,
            byte[] releaseSignature,
            BlobResolver blobs,
            TrustPolicy trustPolicy) {
        return verify(releaseManifest, releaseSignature, blobs, trustPolicy, Clock.systemUTC());
    }

    static VerifiedLaunchDescriptor verify(
            byte[] releaseManifest,
            byte[] releaseSignature,
            BlobResolver blobs,
            TrustPolicy trustPolicy,
            Clock clock) {
        Objects.requireNonNull(blobs, "certification blob resolver");
        Objects.requireNonNull(trustPolicy, "certification trust policy");
        Objects.requireNonNull(clock, "certification clock");
        requireBytes(releaseManifest, MAX_MANIFEST_BYTES, "release manifest");
        requireBytes(releaseSignature, MAX_SIGNATURE_BYTES, "release signature");

        ReleaseManifest manifest = read(releaseManifest, ReleaseManifest.class, "release manifest");
        validate(manifest, trustPolicy, clock.instant());
        verifySignature(releaseManifest, releaseSignature, trustPolicy.key(manifest.keyId()));

        byte[] packBytes = resolve(blobs, manifest.packDigest(), MAX_PACK_BYTES, "certification pack");
        CertificationPack pack = read(packBytes, CertificationPack.class, "certification pack");
        validate(pack, manifest);

        Map<String, Component> components = verifyComponents(pack.components(), blobs);
        Set<String> closureDigests = verifyPackageClosure(components.get("package-closure"), blobs);
        ParsedEvidence evidence = AdapterConformanceEvidenceVerifier.verify(
                resolve(blobs, pack.conformanceEvidenceDigest(), MAX_MANIFEST_BYTES, "conformance evidence"));
        bind(evidence, components, pack);
        verifyEvidenceBlobs(evidence, blobs);
        verifyInventory(manifest, pack, evidence, components, closureDigests, blobs);

        return new VerifiedLaunchDescriptor(
                manifest.releaseId(),
                manifest.releaseVersion(),
                manifest.releaseSequence(),
                manifest.packDigest(),
                evidence.harnessFamily(),
                evidence.harnessVersion(),
                evidence.adapterId(),
                evidence.adapterVersion(),
                evidence.protocolVersion(),
                evidence.operatingSystem(),
                evidence.architecture(),
                evidence.launchProfile(),
                evidence.runtimeProfile(),
                evidence.languageRuntimeVersion(),
                evidence.languageRuntimeArtifactDigest(),
                evidence.packageClosureDigest(),
                evidence.abiId(),
                evidence.abiVersion(),
                Map.copyOf(components));
    }

    private static void validate(ReleaseManifest manifest, TrustPolicy policy, Instant now) {
        if (manifest == null || manifest.schemaVersion() != RELEASE_MANIFEST_SCHEMA_VERSION) {
            throw invalid("release manifest schemaVersion must be " + RELEASE_MANIFEST_SCHEMA_VERSION, null);
        }
        requireId(manifest.releaseId(), "releaseId");
        requireVersion(manifest.releaseVersion(), "releaseVersion");
        requireId(manifest.keyId(), "keyId");
        requireVersion(manifest.protocolVersion(), "protocolVersion");
        requireDigest(manifest.packDigest(), "packDigest");
        if (manifest.releaseSequence() < policy.minimumReleaseSequence()) {
            throw invalid("release manifest is below the anti-downgrade floor", null);
        }
        if (!policy.minimumProtocolVersion().equals(manifest.protocolVersion())) {
            throw invalid("release manifest protocol version is not trusted", null);
        }
        if (manifest.issuedAt() == null
                || manifest.expiresAt() == null
                || !manifest.issuedAt().isBefore(manifest.expiresAt())
                || now.isBefore(manifest.issuedAt())
                || !now.isBefore(manifest.expiresAt())) {
            throw invalid("release manifest is outside its validity interval", null);
        }
        if (policy.revokedKeyIds().contains(manifest.keyId())
                || policy.revokedPackDigests().contains(manifest.packDigest())) {
            throw invalid("release manifest is revoked", null);
        }
    }

    private static void validate(CertificationPack pack, ReleaseManifest manifest) {
        if (pack == null || pack.schemaVersion() != PACK_SCHEMA_VERSION) {
            throw invalid("certification pack schemaVersion must be " + PACK_SCHEMA_VERSION, null);
        }
        if (!manifest.releaseId().equals(pack.releaseId())
                || !manifest.releaseVersion().equals(pack.releaseVersion())
                || !manifest.protocolVersion().equals(pack.protocolVersion())) {
            throw invalid("certification pack does not match its signed release manifest", null);
        }
        requireDigest(pack.conformanceEvidenceDigest(), "conformanceEvidenceDigest");
        if (pack.components() == null || pack.components().size() != REQUIRED_COMPONENTS.size()) {
            throw invalid("certification pack must bind the complete component closure", null);
        }
    }

    private static Map<String, Component> verifyComponents(
            List<Component> declared, BlobResolver blobs) {
        Map<String, Component> components = new LinkedHashMap<>();
        Set<String> duplicates = new LinkedHashSet<>();
        for (Component component : declared) {
            if (component == null) {
                throw invalid("certification components must not contain null", null);
            }
            requireId(component.kind(), "component kind");
            requireId(component.id(), "component id");
            requireVersion(component.version(), "component version");
            requireDigest(component.digest(), "component digest");
            if (component.byteSize() < 1 || component.byteSize() > MAX_COMPONENT_BYTES) {
                throw invalid("component byteSize is outside its bounded range", null);
            }
            if (components.putIfAbsent(component.kind(), component) != null) {
                duplicates.add(component.kind());
            }
            byte[] content = resolve(
                    blobs, component.digest(), Math.toIntExact(component.byteSize()),
                    "component " + component.kind());
            if (content.length != component.byteSize()) {
                throw invalid("component " + component.kind() + " byteSize does not match its blob", null);
            }
        }
        if (!duplicates.isEmpty()) {
            throw invalid("duplicate certification components " + duplicates, null);
        }
        Set<String> missing = new LinkedHashSet<>(REQUIRED_COMPONENTS);
        missing.removeAll(components.keySet());
        Set<String> unknown = new LinkedHashSet<>(components.keySet());
        unknown.removeAll(REQUIRED_COMPONENTS);
        if (!missing.isEmpty() || !unknown.isEmpty()) {
            throw invalid("component closure mismatch; missing=" + missing + ", unknown=" + unknown, null);
        }
        return components;
    }

    private static Set<String> verifyPackageClosure(Component component, BlobResolver blobs) {
        byte[] encoded = resolve(
                blobs,
                component.digest(),
                Math.toIntExact(component.byteSize()),
                "package closure manifest");
        RuntimeClosureManifest manifest = read(
                encoded, RuntimeClosureManifest.class, "package closure manifest");
        Set<String> digests = new LinkedHashSet<>();
        for (RuntimeClosureManifest.RuntimeFile file : manifest.files()) {
            byte[] content = resolveAllowEmpty(
                    blobs,
                    file.digest(),
                    Math.toIntExact(file.byteSize()),
                    "package closure file " + file.rootId() + "/" + file.relativePath());
            if (content.length != file.byteSize()) {
                throw invalid("package closure file byteSize does not match its blob", null);
            }
            digests.add(file.digest());
        }
        return Set.copyOf(digests);
    }

    private static void bind(
            ParsedEvidence evidence,
            Map<String, Component> components,
            CertificationPack pack) {
        Component runtime = components.get("runtime");
        Component languageRuntime = components.get("language-runtime");
        Component packageClosure = components.get("package-closure");
        Component abi = components.get("abi");
        Component adapter = components.get("adapter");
        Component ingress = components.get("ingress");
        Component protocol = components.get("protocol");
        Component suite = components.get("conformance-suite");
        List<String> failures = new java.util.ArrayList<>();
        requireEqual(evidence.harnessVersion(), runtime.version(), "runtime version", failures);
        requireEqual(evidence.runtimeArtifactDigest(), runtime.digest(), "runtime digest", failures);
        requireEqual(
                evidence.languageRuntimeVersion(),
                languageRuntime.version(),
                "language runtime version",
                failures);
        requireEqual(
                evidence.languageRuntimeArtifactDigest(),
                languageRuntime.digest(),
                "language runtime digest",
                failures);
        requireEqual(
                evidence.packageClosureDigest(),
                packageClosure.digest(),
                "package closure digest",
                failures);
        requireEqual(evidence.abiId(), abi.id(), "ABI ID", failures);
        requireEqual(evidence.abiVersion(), abi.version(), "ABI version", failures);
        requireEqual(evidence.adapterId(), adapter.id(), "adapter ID", failures);
        requireEqual(evidence.adapterVersion(), adapter.version(), "adapter version", failures);
        requireEqual(evidence.driverDigest(), adapter.digest(), "adapter digest", failures);
        requireEqual(evidence.ingressDigest(), ingress.digest(), "ingress digest", failures);
        requireEqual(evidence.protocolVersion(), protocol.version(), "protocol version", failures);
        requireEqual(evidence.testSuiteDigest(), suite.digest(), "conformance suite digest", failures);
        requireEqual(evidence.protocolVersion(), pack.protocolVersion(), "pack protocol version", failures);
        evidence.checks().stream()
                .filter(check -> !"pass".equals(check.result()))
                .forEach(check -> failures.add("mandatory check failed: " + check.checkId()));
        if (!failures.isEmpty()) {
            throw invalid("certification evidence is not admissible:\n- " + String.join("\n- ", failures), null);
        }
    }

    private static void verifyEvidenceBlobs(ParsedEvidence evidence, BlobResolver blobs) {
        evidence.checks().forEach(check -> resolve(
                blobs,
                check.evidenceDigest(),
                MAX_COMPONENT_BYTES,
                "evidence for mandatory check " + check.checkId()));
    }

    private static void verifyInventory(
            ReleaseManifest manifest,
            CertificationPack pack,
            ParsedEvidence evidence,
            Map<String, Component> components,
            Set<String> closureDigests,
            BlobResolver blobs) {
        Set<String> expected = new LinkedHashSet<>();
        expected.add(manifest.packDigest());
        expected.add(pack.conformanceEvidenceDigest());
        components.values().stream().map(Component::digest).forEach(expected::add);
        expected.addAll(closureDigests);
        evidence.checks().stream().map(check -> check.evidenceDigest()).forEach(expected::add);

        Set<String> actual;
        try {
            actual = Set.copyOf(blobs.digests());
        } catch (IOException | RuntimeException e) {
            throw invalid("certification blob inventory could not be resolved", e);
        }
        actual.forEach(digest -> requireDigest(digest, "certification blob inventory digest"));
        if (!actual.equals(expected)) {
            Set<String> missing = new LinkedHashSet<>(expected);
            missing.removeAll(actual);
            Set<String> unreferenced = new LinkedHashSet<>(actual);
            unreferenced.removeAll(expected);
            throw invalid("certification blob inventory mismatch; missing=" + missing
                          + ", unreferenced=" + unreferenced,
                    null);
        }
    }

    private static void requireEqual(
            String actual, String expected, String label, List<String> failures) {
        if (!Objects.equals(actual, expected)) {
            failures.add(label + " does not match the certified closure");
        }
    }

    private static void verifySignature(byte[] content, byte[] signature, byte[] encodedKey) {
        if (encodedKey == null || encodedKey.length == 0) {
            throw invalid("release signing key is not trusted", null);
        }
        try {
            PublicKey key = KeyFactory.getInstance("Ed25519")
                    .generatePublic(new X509EncodedKeySpec(encodedKey));
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(key);
            verifier.update(content);
            if (!verifier.verify(signature)) {
                throw invalid("release manifest signature is invalid", null);
            }
        } catch (GeneralSecurityException e) {
            throw invalid("release manifest signature could not be verified", e);
        }
    }

    private static byte[] resolve(
            BlobResolver blobs, String digest, int maximumBytes, String label) {
        byte[] content = resolveAllowEmpty(blobs, digest, maximumBytes, label);
        requireBytes(content, maximumBytes, label);
        return content;
    }

    private static byte[] resolveAllowEmpty(
            BlobResolver blobs, String digest, int maximumBytes, String label) {
        byte[] content;
        try {
            content = blobs.resolve(digest, maximumBytes);
        } catch (IOException | RuntimeException e) {
            throw invalid(label + " could not be resolved", e);
        }
        if (content == null || content.length > maximumBytes) {
            throw invalid(label + " must contain 0.." + maximumBytes + " bytes", null);
        }
        if (!digest.equals(ShipDigest.sha256(content))) {
            throw invalid(label + " digest does not match its content", null);
        }
        return content;
    }

    private static <T> T read(byte[] content, Class<T> type, String label) {
        try {
            return ShipJson.mapper().readValue(content, type);
        } catch (IOException | IllegalArgumentException e) {
            throw invalid(label + " is not strict canonical JSON", e);
        }
    }

    private static void requireBytes(byte[] value, int maximumBytes, String label) {
        if (value == null || value.length < 1 || value.length > maximumBytes) {
            throw invalid(label + " must contain 1.." + maximumBytes + " bytes", null);
        }
    }

    private static void requireId(String value, String field) {
        if (value == null || !ID.matcher(value).matches()) {
            throw invalid(field + " is not a canonical identifier", null);
        }
    }

    private static void requireVersion(String value, String field) {
        if (value == null || !VERSION.matcher(value).matches()) {
            throw invalid(field + " is not a canonical version", null);
        }
    }

    private static void requireDigest(String value, String field) {
        if (!ShipDigest.isSha256(value)) {
            throw invalid(field + " must be a canonical SHA-256 digest", null);
        }
    }

    private static IllegalArgumentException invalid(String message, Throwable cause) {
        return new IllegalArgumentException("Invalid Ship adapter certification: " + message, cause);
    }

    public interface BlobResolver {

        byte[] resolve(String digest, int maximumBytes) throws IOException;

        Set<String> digests() throws IOException;
    }

    public record TrustPolicy(
            long minimumReleaseSequence,
            String minimumProtocolVersion,
            Map<String, byte[]> trustedKeys,
            Set<String> revokedKeyIds,
            Set<String> revokedPackDigests) {

        public TrustPolicy {
            if (minimumReleaseSequence < 1) {
                throw new IllegalArgumentException("Trust policy release floor must be positive");
            }
            requireVersion(minimumProtocolVersion, "minimumProtocolVersion");
            trustedKeys = copyKeys(trustedKeys);
            revokedKeyIds = Set.copyOf(Objects.requireNonNull(revokedKeyIds, "revoked key IDs"));
            revokedPackDigests = Set.copyOf(Objects.requireNonNull(revokedPackDigests, "revoked pack digests"));
            revokedKeyIds.forEach(value -> requireId(value, "revoked key ID"));
            revokedPackDigests.forEach(value -> requireDigest(value, "revoked pack digest"));
        }

        byte[] key(String keyId) {
            byte[] value = trustedKeys.get(keyId);
            return value == null ? null : value.clone();
        }

        private static Map<String, byte[]> copyKeys(Map<String, byte[]> values) {
            Objects.requireNonNull(values, "trusted keys");
            Map<String, byte[]> copy = new LinkedHashMap<>();
            values.forEach((key, value) -> {
                requireId(key, "trusted key ID");
                if (value == null || value.length == 0) {
                    throw new IllegalArgumentException("Trusted key material must not be empty");
                }
                copy.put(key, value.clone());
            });
            return Map.copyOf(copy);
        }
    }

    public record VerifiedLaunchDescriptor(
            String releaseId,
            String releaseVersion,
            long releaseSequence,
            String certificationPackDigest,
            String harnessFamily,
            String harnessVersion,
            String adapterId,
            String adapterVersion,
            String protocolVersion,
            String operatingSystem,
            String architecture,
            String launchProfile,
            String runtimeProfile,
            String languageRuntimeVersion,
            String languageRuntimeArtifactDigest,
            String packageClosureDigest,
            String abiId,
            String abiVersion,
            Map<String, Component> components) {

        public VerifiedLaunchDescriptor {
            components = Map.copyOf(components);
        }
    }

    public record Component(
            String kind,
            String id,
            String version,
            String digest,
            long byteSize) {
    }

    private record ReleaseManifest(
            int schemaVersion,
            String releaseId,
            String releaseVersion,
            long releaseSequence,
            String protocolVersion,
            String keyId,
            Instant issuedAt,
            Instant expiresAt,
            String packDigest) {
    }

    private record CertificationPack(
            int schemaVersion,
            String releaseId,
            String releaseVersion,
            String protocolVersion,
            String conformanceEvidenceDigest,
            List<Component> components) {
    }

}
