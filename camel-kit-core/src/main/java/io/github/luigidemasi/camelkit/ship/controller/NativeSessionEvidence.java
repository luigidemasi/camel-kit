package io.github.luigidemasi.camelkit.ship.controller;

import java.io.IOException;
import java.util.Objects;

import io.github.luigidemasi.camelkit.ship.ShipDigest;
import io.github.luigidemasi.camelkit.ship.controller.ShipBlobStore.BlobReference;

/** Controller-local profile binding for one native ingress session. */
final class NativeSessionEvidence {

    static final String CHECK_ID = "native-adapter-session";
    static final String EVIDENCE_KIND = "native-session-evidence";
    static final String TRACE_KIND = "native-ingress-trace";
    static final int MAX_TRACE_BYTES = 4 * 1024 * 1024;
    static final int MAX_EVIDENCE_BYTES = 64 * 1024;

    private static final String SESSION_DOMAIN = "camel-kit-ship-native-session-v2";

    private NativeSessionEvidence() {
    }

    /**
     * Records and MACs supplied profile values. In this slice that does not authenticate a principal, confer lifecycle
     * authority, or certify an adapter. A future protected ingress may populate the observed-principal field as trusted
     * evidence.
     */
    static Binding create(
            ShipInteractionSigner signer,
            String runId,
            String adapterId,
            String sessionId,
            String ingressMode,
            String interactionProfile,
            String launcherProfile,
            String sandboxProfile,
            String providerProfile,
            String protocolProfile,
            String evidenceProfile,
            String controllerObservedProcessPrincipal,
            String declaredCliUiProfile,
            BlobReference ingressTrace)
            throws IOException {
        Objects.requireNonNull(signer, "interaction signer");
        Objects.requireNonNull(ingressTrace, "ingress trace");
        if (!signer.belongsTo(runId)) {
            throw new IOException("Native session signer belongs to a different Ship run");
        }
        String[] fields = macFields(
                runId,
                adapterId,
                sessionId,
                ingressMode,
                interactionProfile,
                launcherProfile,
                sandboxProfile,
                providerProfile,
                protocolProfile,
                evidenceProfile,
                controllerObservedProcessPrincipal,
                declaredCliUiProfile,
                ingressTrace);
        return new Binding(
                Binding.SCHEMA_VERSION,
                runId,
                adapterId,
                sessionId,
                ingressMode,
                interactionProfile,
                launcherProfile,
                sandboxProfile,
                providerProfile,
                protocolProfile,
                evidenceProfile,
                controllerObservedProcessPrincipal,
                declaredCliUiProfile,
                ingressTrace,
                signer.sign(fields));
    }

    static BlobReference store(
            ShipBlobStore blobs, ShipInteractionSigner signer, Binding binding)
            throws IOException {
        Objects.requireNonNull(blobs, "blobs");
        Objects.requireNonNull(signer, "interaction signer");
        Objects.requireNonNull(binding, "binding");
        if (!signer.belongsTo(blobs, binding.runId())) {
            throw new IOException("Native session signer and blob store belong to a different Ship run");
        }
        blobs.verify(binding.ingressTrace());
        if (!signer.verify(binding.mac(), macFields(binding))) {
            throw new IOException("Native session evidence failed controller integrity verification");
        }
        byte[] content = ShipJson.mapper().writeValueAsBytes(binding);
        if (content.length > MAX_EVIDENCE_BYTES) {
            throw new IOException("Native session evidence exceeds its byte limit");
        }
        return blobs.writeBytes(EVIDENCE_KIND, content);
    }

    static Binding verify(
            ShipBlobStore blobs,
            ShipInteractionSigner signer,
            String runId,
            String adapterId,
            BlobReference reference)
            throws IOException {
        Objects.requireNonNull(blobs, "blobs");
        Objects.requireNonNull(signer, "interaction signer");
        if (adapterId == null || !adapterId.matches("[a-z][a-z0-9-]{0,63}")) {
            throw new IOException("Native session adapter identifier is missing or invalid");
        }
        if (!signer.belongsTo(blobs, runId)) {
            throw new IOException("Native session verifier belongs to a different Ship run");
        }
        if (reference == null
                || !EVIDENCE_KIND.equals(reference.kind())
                || reference.byteSize() > MAX_EVIDENCE_BYTES) {
            throw new IOException("Native run is missing its bounded session evidence");
        }
        Binding binding = ShipJson.mapper().readValue(
                blobs.readBytes(reference, MAX_EVIDENCE_BYTES), Binding.class);
        if (!runId.equals(binding.runId()) || !adapterId.equals(binding.adapterId())) {
            throw new IOException("Native session evidence does not match the Ship run");
        }
        if (!TRACE_KIND.equals(binding.ingressTrace().kind())) {
            throw new IOException("Native session evidence has an invalid ingress trace");
        }
        blobs.verify(binding.ingressTrace());
        if (!signer.verify(binding.mac(), macFields(binding))) {
            throw new IOException("Native session evidence failed controller integrity verification");
        }
        return binding;
    }

    static void requireTrace(byte[] trace) {
        if (trace == null || trace.length < 1 || trace.length > MAX_TRACE_BYTES) {
            throw new ShipControllerException(
                    "native-trace-invalid",
                    "Native ingress trace must contain 1.." + MAX_TRACE_BYTES + " bytes");
        }
    }

    static void requireSessionId(String sessionId) {
        if (sessionId == null || !sessionId.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")) {
            throw new ShipControllerException(
                    "native-session-invalid", "Native session identifier is missing or invalid");
        }
    }

    private static String[] macFields(Binding binding) {
        return macFields(
                binding.runId(),
                binding.adapterId(),
                binding.sessionId(),
                binding.ingressMode(),
                binding.interactionProfile(),
                binding.launcherProfile(),
                binding.sandboxProfile(),
                binding.providerProfile(),
                binding.protocolProfile(),
                binding.evidenceProfile(),
                binding.controllerObservedProcessPrincipal(),
                binding.declaredCliUiProfile(),
                binding.ingressTrace());
    }

    private static String[] macFields(
            String runId,
            String adapterId,
            String sessionId,
            String ingressMode,
            String interactionProfile,
            String launcherProfile,
            String sandboxProfile,
            String providerProfile,
            String protocolProfile,
            String evidenceProfile,
            String controllerObservedProcessPrincipal,
            String declaredCliUiProfile,
            BlobReference trace) {
        return new String[]{
                SESSION_DOMAIN,
                runId,
                adapterId,
                sessionId,
                ingressMode,
                interactionProfile,
                launcherProfile,
                sandboxProfile,
                providerProfile,
                protocolProfile,
                evidenceProfile,
                controllerObservedProcessPrincipal,
                declaredCliUiProfile,
                trace.kind(),
                trace.digest(),
                Long.toString(trace.byteSize())
        };
    }

    record Binding(
            int schemaVersion,
            String runId,
            String adapterId,
            String sessionId,
            String ingressMode,
            String interactionProfile,
            String launcherProfile,
            String sandboxProfile,
            String providerProfile,
            String protocolProfile,
            String evidenceProfile,
            String controllerObservedProcessPrincipal,
            String declaredCliUiProfile,
            BlobReference ingressTrace,
            String mac) {

        static final int SCHEMA_VERSION = 1;

        Binding {
            if (schemaVersion != SCHEMA_VERSION
                    || runId == null
                    || !runId.matches("ship-[0-9a-f]{32}")) {
                throw new IllegalArgumentException("Invalid native session identity");
            }
            requireIdentifier(adapterId, "adapter");
            if (sessionId == null || !sessionId.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")) {
                throw new IllegalArgumentException("Invalid native session identifier");
            }
            requireProfile(ingressMode, "ingress mode");
            requireProfile(interactionProfile, "interaction profile");
            requireProfile(launcherProfile, "launcher profile");
            requireProfile(sandboxProfile, "sandbox profile");
            requireProfile(providerProfile, "provider profile");
            requireProfile(protocolProfile, "protocol profile");
            requireProfile(evidenceProfile, "evidence profile");
            requireBoundedText(
                    controllerObservedProcessPrincipal, "controller-observed process principal");
            requireBoundedText(declaredCliUiProfile, "declared CLI UI profile");
            if (ingressTrace == null
                    || !TRACE_KIND.equals(ingressTrace.kind())
                    || ingressTrace.byteSize() < 1
                    || ingressTrace.byteSize() > MAX_TRACE_BYTES
                    || !ShipDigest.isHmacSha256(mac)) {
                throw new IllegalArgumentException("Invalid native session binding");
            }
        }

        private static void requireIdentifier(String value, String label) {
            if (value == null || !value.matches("[a-z][a-z0-9-]{0,63}")) {
                throw new IllegalArgumentException("Invalid native session " + label);
            }
        }

        private static void requireProfile(String value, String label) {
            if (value == null || !value.matches("[a-z][a-z0-9._:-]{0,127}")) {
                throw new IllegalArgumentException("Invalid native session " + label);
            }
        }

        private static void requireBoundedText(String value, String label) {
            if (value == null
                    || value.isBlank()
                    || value.length() > 4096
                    || value.chars().anyMatch(character -> character == 0)) {
                throw new IllegalArgumentException("Invalid native session " + label);
            }
        }
    }
}
