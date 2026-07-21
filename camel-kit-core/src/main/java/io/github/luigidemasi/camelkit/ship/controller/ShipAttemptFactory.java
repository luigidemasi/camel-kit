package io.github.luigidemasi.camelkit.ship.controller;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import io.github.luigidemasi.camelkit.ship.ShipDigest;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction;
import io.github.luigidemasi.camelkit.ship.protocol.ShipStage;
import io.github.luigidemasi.camelkit.ship.protocol.StageCapability;
import io.github.luigidemasi.camelkit.ship.protocol.StageCapability.Operation;
import io.github.luigidemasi.camelkit.ship.protocol.StageCapability.RepositoryAccess;
import io.github.luigidemasi.camelkit.ship.protocol.StageRequest;

/** Builds one non-chaining, controller-authored worker request. */
final class ShipAttemptFactory {

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Creates a logical request only. PR 5 must materialize protected input references and issue a runnable capability
     * before dispatch. The returned value is not schema-conformant wire data and must not be serialized or dispatched
     * until that materialization replaces logical references with protected roots. This substrate never exposes CAS
     * paths or opens a project path.
     */
    StageRequest create(
            String runId,
            ShipStage stage,
            int attempt,
            String policyDigest,
            Inputs inputs,
            String workerContractReference,
            String outputDirectory,
            String failureCode,
            String failureMessage) {
        requireRunId(runId);
        Objects.requireNonNull(stage, "stage");
        if (attempt < 1) {
            throw new IllegalArgumentException("Ship worker attempt must be positive");
        }
        if (!ShipDigest.isSha256(policyDigest)) {
            throw new IllegalArgumentException("Ship worker policy digest is invalid");
        }
        Objects.requireNonNull(inputs, "inputs");
        requireReference(workerContractReference, "worker contract reference");
        String output = requireAbsoluteNormalized(outputDirectory, "worker output directory");
        if ((failureCode == null) != (failureMessage == null)) {
            throw new IllegalArgumentException(
                    "Ship worker retry context requires both failure code and message");
        }

        String attemptId = stage.name().toLowerCase(Locale.ROOT)
                           + '-' + attempt + '-' + randomToken(12);
        String challenge = randomToken(32);
        String inputDigest = inputDigest(stage, inputs, workerContractReference, failureCode, failureMessage);
        String idempotencyKey = ShipDigest.sha256(Interaction.canonicalMacBytes(new String[]{
                "camel-kit-ship-attempt-v2",
                runId,
                stage.name(),
                Integer.toString(attempt),
                inputDigest,
                policyDigest
        }));
        return new StageRequest(
                StageRequest.SCHEMA_VERSION,
                runId,
                stage,
                attemptId,
                attempt,
                idempotencyKey,
                challenge,
                inputDigest,
                policyDigest,
                inputs.sourceDirectory(),
                inputs.sourceSnapshotReference(),
                inputs.projectSourceManifestReference(),
                inputs.contextReference(),
                inputs.ledgerReference(),
                inputs.catalogEvidenceReference(),
                inputs.approvedDesignReference(),
                inputs.planReference(),
                inputs.interactionReference(),
                inputs.artifactManifestReference(),
                inputs.candidateDirectory(),
                inputs.evidenceReferences(),
                normalizeFailureCode(failureCode),
                normalizeFailureMessage(failureMessage),
                workerContractReference,
                output,
                logicalCapability(stage, output));
    }

    private static StageCapability logicalCapability(ShipStage stage, String outputDirectory) {
        RepositoryAccess access;
        List<String> writes;
        List<Operation> operations = new ArrayList<>(
                List.of(Operation.READ, Operation.SEARCH, Operation.RETURN_STRUCTURED_RESULT));
        if (stage == ShipStage.DISCOVERY || stage == ShipStage.REVIEW) {
            access = RepositoryAccess.READ_ONLY;
            writes = List.of();
        } else {
            access = stage == ShipStage.EXECUTE
                    ? RepositoryAccess.DECLARED_EXECUTION_PATHS
                    : RepositoryAccess.READ_WITH_STAGED_OUTPUT;
            writes = List.of(outputDirectory);
            operations.add(Operation.WRITE_STAGED_ARTIFACT);
        }
        // Logical blob references are deliberately not represented as filesystem read roots.
        return new StageCapability(access, List.of(), writes, operations, false, false);
    }

    private static String inputDigest(
            ShipStage stage,
            Inputs inputs,
            String workerContractReference,
            String failureCode,
            String failureMessage) {
        List<String> fields = new ArrayList<>();
        fields.add("camel-kit-ship-input-v4");
        fields.add(stage.name());
        fields.add(inputs.sourceDirectory());
        fields.add(inputs.sourceSnapshotReference());
        fields.add(inputs.projectSourceManifestReference());
        fields.add(inputs.contextReference());
        fields.add(inputs.ledgerReference());
        fields.add(inputs.catalogEvidenceReference());
        fields.add(inputs.approvedDesignReference());
        fields.add(inputs.planReference());
        fields.add(inputs.interactionReference());
        fields.add(inputs.artifactManifestReference());
        fields.add(inputs.candidateDirectory());
        fields.add(Integer.toString(inputs.evidenceReferences().size()));
        fields.addAll(inputs.evidenceReferences());
        fields.add(failureCode);
        fields.add(failureMessage);
        fields.add(workerContractReference);
        return ShipDigest.sha256(Interaction.canonicalMacBytes(fields.toArray(String[]::new)));
    }

    private static String randomToken(int bytes) {
        byte[] value = new byte[bytes];
        RANDOM.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static void requireRunId(String runId) {
        if (runId == null || !runId.matches("ship-[0-9a-f]{32}")) {
            throw new IllegalArgumentException("Ship worker request run ID must be canonical");
        }
    }

    private static void requireReference(String value, String label) {
        if (value == null || value.isBlank() || value.length() > 4096 || containsAsciiControl(value)) {
            throw new IllegalArgumentException("Ship worker " + label + " is invalid");
        }
    }

    private static String requireAbsoluteNormalized(String value, String label) {
        requireReference(value, label);
        try {
            Path path = Path.of(value);
            if (!path.isAbsolute() || !path.normalize().equals(path)) {
                throw new IllegalArgumentException("Ship " + label + " must be absolute and normalized");
            }
            return path.toString();
        } catch (InvalidPathException e) {
            throw new IllegalArgumentException("Ship " + label + " is invalid", e);
        }
    }

    private static String normalizeFailureCode(String value) {
        if (value == null) {
            return null;
        }
        if (!value.matches("[a-z][a-z0-9-]{0,127}")) {
            throw new IllegalArgumentException("Ship worker failure code is invalid");
        }
        return value;
    }

    private static String normalizeFailureMessage(String value) {
        if (value == null) {
            return null;
        }
        if (value.isBlank() || value.length() > 64 * 1024 || value.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Ship worker failure message is invalid");
        }
        return value;
    }

    private static boolean containsAsciiControl(String value) {
        return value.chars().anyMatch(character -> character <= 0x1f || character == 0x7f);
    }

    record Inputs(
            String sourceDirectory,
            String sourceSnapshotReference,
            String projectSourceManifestReference,
            String contextReference,
            String ledgerReference,
            String catalogEvidenceReference,
            String approvedDesignReference,
            String planReference,
            String interactionReference,
            String artifactManifestReference,
            String candidateDirectory,
            List<String> evidenceReferences) {

        Inputs {
            evidenceReferences = evidenceReferences == null ? List.of() : List.copyOf(evidenceReferences);
            if (evidenceReferences.size() > 1024
                    || new HashSet<>(evidenceReferences).size() != evidenceReferences.size()) {
                throw new IllegalArgumentException("Ship worker evidence references are invalid");
            }
            evidenceReferences.forEach(reference -> requireReference(reference, "evidence reference"));
            for (String reference : new String[]{
                    sourceDirectory,
                    sourceSnapshotReference,
                    projectSourceManifestReference,
                    contextReference,
                    ledgerReference,
                    catalogEvidenceReference,
                    approvedDesignReference,
                    planReference,
                    interactionReference,
                    artifactManifestReference,
                    candidateDirectory}) {
                if (reference != null) {
                    requireReference(reference, "input reference");
                }
            }
        }
    }
}
