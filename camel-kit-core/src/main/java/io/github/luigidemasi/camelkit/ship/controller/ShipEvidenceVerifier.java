package io.github.luigidemasi.camelkit.ship.controller;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

import io.github.luigidemasi.camelkit.ship.ShipDigest;
import io.github.luigidemasi.camelkit.ship.artifact.ArtifactManifest;
import io.github.luigidemasi.camelkit.ship.controller.ShipBlobStore.BlobReference;
import io.github.luigidemasi.camelkit.ship.evidence.CommandEvidence;
import io.github.luigidemasi.camelkit.ship.evidence.EvidenceCommand;
import io.github.luigidemasi.camelkit.ship.evidence.EvidenceRunner;
import io.github.luigidemasi.camelkit.ship.evidence.JvmPayloadRequest;
import io.github.luigidemasi.camelkit.ship.evidence.launcher.ShipMainPackageMain;

/** Verifies one command record against controller-selected inputs and retained CAS bytes. */
final class ShipEvidenceVerifier {

    private ShipEvidenceVerifier() {
    }

    static boolean verify(
            ShipBlobStore blobs,
            Path candidate,
            ArtifactManifest manifest,
            EvidenceCommand expected,
            CommandEvidence evidence,
            RetainedEvidence retained)
            throws IOException {
        Objects.requireNonNull(blobs, "blobs");
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(manifest, "manifest");
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(retained, "retained evidence");
        if (evidence == null
                || evidence.schemaVersion() != CommandEvidence.SCHEMA_VERSION
                || !expected.id().equals(evidence.commandId())) {
            throw new IOException("Command evidence identity does not match the controller check");
        }
        Path recordedWorkingDirectory;
        try {
            recordedWorkingDirectory = Path.of(evidence.workingDirectory()).toAbsolutePath().normalize();
        } catch (RuntimeException e) {
            throw new IOException("Command evidence working directory is invalid", e);
        }
        if (!candidate.toAbsolutePath().normalize().equals(recordedWorkingDirectory)) {
            throw new IOException("Command evidence working directory is not the accepted candidate");
        }
        if (!expected.inputDigests().equals(evidence.inputDigests())
                || !expected.arguments().equals(evidence.arguments())) {
            throw new IOException("Command evidence inputs differ from the controller-selected command");
        }
        String jdkDigest = evidence.controlledEnvironment().get("CAMEL_KIT_JDK_DIGEST");
        if ((evidence.launched() || evidence.sandbox() != null || !evidence.controlledEnvironment().isEmpty())
                && (!ShipDigest.isSha256(jdkDigest)
                        || !EvidenceRunner.expectedEnvironment(expected, jdkDigest)
                                .equals(evidence.controlledEnvironment()))) {
            throw new IOException("Command evidence environment differs from controller policy");
        }
        requireTimes(evidence.startedAt(), evidence.endedAt());
        requireRetained(blobs, retained.stdoutLog(), evidence.stdoutDigest(), "command stdout log");
        requireRetained(blobs, retained.stderrLog(), evidence.stderrDigest(), "command stderr log");

        if (!evidence.launched()) {
            if (evidence.launchError() == null
                    || evidence.launchError().isBlank()
                    || evidence.exitCode() != null
                    || evidence.timedOut()) {
                throw new IOException("Unlaunched command evidence has an invalid failure outcome");
            }
            verifyOptionalAuthority(blobs, expected, evidence, retained, jdkDigest);
            return false;
        }

        verifySandbox(blobs, expected, evidence.sandbox(), retained.sandboxExecutable(), jdkDigest);
        verifyToolchain(blobs, expected, evidence, retained.toolchainArchive());
        verifyExecutable(blobs, evidence, retained.executableSnapshot());
        boolean passed = evidence.passed() && versionMatches(expected, manifest, evidence.executableVersion());
        if (passed && expected.jvmPayload().kind() == JvmPayloadRequest.Kind.MAIN_PACKAGE_INSPECT) {
            byte[] summary = blobs.readBytes(retained.stdoutLog(), ShipMainPackageMain.MAX_SUMMARY_BYTES);
            if (expected.arguments().size() <= 4) {
                throw new IOException("Deterministic Main package command lacks its accepted inputs");
            }
            ShipMainPackageMain.verifySummary(
                    summary, candidate, expected.arguments().subList(4, expected.arguments().size()));
        }
        return passed;
    }

    private static void verifyOptionalAuthority(
            ShipBlobStore blobs,
            EvidenceCommand expected,
            CommandEvidence evidence,
            RetainedEvidence retained,
            String jdkDigest)
            throws IOException {
        if (evidence.sandbox() != null || retained.sandboxExecutable() != null) {
            verifySandbox(blobs, expected, evidence.sandbox(), retained.sandboxExecutable(), jdkDigest);
        }
        if (evidence.toolchainDigest() != null
                || evidence.postToolchainDigest() != null
                || retained.toolchainArchive() != null) {
            verifyToolchain(blobs, expected, evidence, retained.toolchainArchive());
        }
        if (evidence.executableDigest() != null
                || evidence.postExecutableDigest() != null
                || retained.executableSnapshot() != null) {
            verifyExecutable(blobs, evidence, retained.executableSnapshot());
        }
    }

    private static void verifySandbox(
            ShipBlobStore blobs,
            EvidenceCommand expected,
            CommandEvidence.SandboxIdentity sandbox,
            BlobReference retained,
            String jdkDigest)
            throws IOException {
        if (sandbox == null || !EvidenceRunner.SANDBOX_PROVIDER.equals(sandbox.provider())) {
            throw new IOException("Command evidence did not use the required OS sandbox");
        }
        requireDigest(sandbox.executableDigest(), "pre-execution sandbox executable");
        requireDigest(sandbox.postExecutableDigest(), "post-execution sandbox executable");
        if (!sandbox.executableDigest().equals(sandbox.postExecutableDigest())) {
            throw new IOException("Evidence sandbox executable changed during collection");
        }
        if (!EvidenceRunner.expectedSandboxProfileDigest(expected, jdkDigest)
                .equals(sandbox.profileDigest())) {
            throw new IOException("Evidence sandbox profile differs from controller policy");
        }
        requireRetained(blobs, retained, sandbox.executableDigest(), "sandbox executable");
    }

    private static void verifyToolchain(
            ShipBlobStore blobs,
            EvidenceCommand expected,
            CommandEvidence evidence,
            BlobReference retained)
            throws IOException {
        requireDigest(evidence.toolchainDigest(), "pre-execution toolchain");
        requireDigest(evidence.postToolchainDigest(), "post-execution toolchain");
        if (!evidence.toolchainDigest().equals(evidence.postToolchainDigest())) {
            throw new IOException("Evidence toolchain changed during collection");
        }
        if (expected.requiredToolchainDigest() != null
                && !expected.requiredToolchainDigest().equals(evidence.toolchainDigest())) {
            throw new IOException("Evidence toolchain differs from the controller-required toolchain");
        }
        requireRetained(blobs, retained, evidence.toolchainSnapshotDigest(), "JVM payload archive");
    }

    private static void verifyExecutable(
            ShipBlobStore blobs, CommandEvidence evidence, BlobReference retained)
            throws IOException {
        requireDigest(evidence.executableDigest(), "pre-execution executable");
        requireDigest(evidence.postExecutableDigest(), "post-execution executable");
        if (!evidence.executableDigest().equals(evidence.postExecutableDigest())) {
            throw new IOException("Command executable changed during evidence collection");
        }
        requireRetained(blobs, retained, evidence.executableDigest(), "command executable");
    }

    private static void requireTimes(Instant startedAt, Instant endedAt) throws IOException {
        if (startedAt == null || endedAt == null || endedAt.isBefore(startedAt)) {
            throw new IOException("Command evidence timestamps are invalid");
        }
    }

    private static void requireRetained(
            ShipBlobStore blobs, BlobReference reference, String digest, String label)
            throws IOException {
        requireDigest(digest, label);
        if (reference == null || !digest.equals(reference.digest())) {
            throw new IOException("Command evidence is missing its exact retained " + label);
        }
        blobs.verify(reference);
    }

    private static boolean versionMatches(
            EvidenceCommand expected, ArtifactManifest manifest, String executableVersion) {
        if (executableVersion == null || expected.arguments().isEmpty()) {
            return false;
        }
        if (!EvidenceRunner.JAVA_EXECUTABLE.equals(expected.arguments().get(0))) {
            return false;
        }
        if (expected.jvmPayload().kind() == JvmPayloadRequest.Kind.MAIN_PACKAGE_INSPECT) {
            return ShipMainPackageMain.PAYLOAD_VERSION.equals(executableVersion);
        }
        return executableVersion.contains(manifest.camelVersion())
                && (expected.jvmPayload().citrusVersion() == null
                        || executableVersion.contains(expected.jvmPayload().citrusVersion()));
    }

    private static void requireDigest(String digest, String label) throws IOException {
        if (!ShipDigest.isSha256(digest)) {
            throw new IOException(label + " digest is invalid");
        }
    }

    record RetainedEvidence(
            BlobReference stdoutLog,
            BlobReference stderrLog,
            BlobReference sandboxExecutable,
            BlobReference toolchainArchive,
            BlobReference executableSnapshot) {
    }
}
