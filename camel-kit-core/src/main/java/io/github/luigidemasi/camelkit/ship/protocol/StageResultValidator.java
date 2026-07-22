package io.github.luigidemasi.camelkit.ship.protocol;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.luigidemasi.camelkit.ship.ShipDigest;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogSubject;
import io.github.luigidemasi.camelkit.ship.ledger.LedgerValidationException;
import io.github.luigidemasi.camelkit.ship.ledger.LedgerValidator;
import io.github.luigidemasi.camelkit.ship.protocol.StageCapability.Operation;
import io.github.luigidemasi.camelkit.ship.protocol.StageCapability.RepositoryAccess;
import io.github.luigidemasi.camelkit.ship.security.ShipTreePolicy;
import io.github.luigidemasi.camelkit.ship.security.StagedArtifactSource;

/** Validates worker identity, outcome shape, and the live staged bytes observed during one non-certifying preflight. */
public final class StageResultValidator {

    private StageResultValidator() {
    }

    /**
     * Performs a live preflight over one stopped worker's output.
     *
     * <p>
     * The controller must own and protect the attempt-output ancestry from worker replacement while this method runs.
     * Passing preflight does not authorize a later reopen or commit. Production callers must use the protected
     * controller importer, which hashes while copying the same opened stream into controller-owned quarantine/CAS.
     */
    public static void validatePreflight(StageRequest request, StageResult result, Path attemptOutputDirectory) {
        validateEnvelope(request, result, attemptOutputDirectory);
        if (result.artifacts().isEmpty()) {
            return;
        }
        List<String> violations = new ArrayList<>();
        try (StagedArtifactSource.Session source = StagedArtifactSource.open(attemptOutputDirectory);
             var discard = Channels.newChannel(OutputStream.nullOutputStream())) {
            for (ProducedArtifact artifact : result.artifacts()) {
                try {
                    StagedArtifactSource.CopyResult copy
                            = source.copyTo(artifact.relativePath(), artifact.size(), discard);
                    if (copy.size() != artifact.size()) {
                        violations.add("Produced artifact size mismatch: " + artifact.relativePath());
                    }
                    if (!copy.digest().equals(artifact.digest())) {
                        violations.add("Produced artifact digest mismatch: " + artifact.relativePath());
                    }
                } catch (IOException e) {
                    violations.add(
                            "Could not verify produced artifact " + artifact.relativePath() + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            violations.add("Could not securely bind the attempt output directory: " + e.getMessage());
        }
        if (!violations.isEmpty()) {
            throw new StageResultValidationException(violations);
        }
    }

    /**
     * Validates the result shape and controller-issued authority without reading worker-controlled files.
     *
     * <p>
     * This is the production import gate. A controller can reject malformed claims before it opens any staged artifact,
     * then copy and hash each accepted claim exactly once into controller-owned quarantine.
     */
    public static void validateEnvelope(StageRequest request, StageResult result, Path attemptOutputDirectory) {
        List<String> violations = new ArrayList<>();
        if (request == null || result == null) {
            throw new StageResultValidationException(List.of("Stage request and result are required"));
        }
        requireEqual("request schema version", StageRequest.SCHEMA_VERSION, request.schemaVersion(), violations);
        requireEqual("result schema version", StageResult.SCHEMA_VERSION, result.schemaVersion(), violations);
        requireEqual("schema version", request.schemaVersion(), result.schemaVersion(), violations);
        requireEqual("run ID", request.runId(), result.runId(), violations);
        requireEqual("stage", request.stage(), result.stage(), violations);
        requireEqual("attempt ID", request.attemptId(), result.attemptId(), violations);
        requireEqual("challenge", request.challenge(), result.challenge(), violations);
        requireEqual("input digest", request.inputDigest(), result.inputDigest(), violations);
        if (request.stage() == null) {
            violations.add("Stage request stage is required");
        }
        if (result.outcome() == null) {
            violations.add("Stage outcome is required");
        } else if (request.stage() != null) {
            validateOutcome(request.stage(), result, violations);
        }
        validateCatalogRequests(request.stage(), result, violations);
        validateResultAuthority(request, result.artifacts(), attemptOutputDirectory, violations);
        validateArtifactClaims(result.artifacts(), violations);
        if (!violations.isEmpty()) {
            throw new StageResultValidationException(violations);
        }
    }

    private static void validateCatalogRequests(
            ShipStage stage, StageResult result, List<String> violations) {
        List<CatalogSubject> requests = result.catalogRequests();
        if (requests.size() > 512) {
            violations.add("A discovery continuation may request at most 512 catalog subjects");
        }
        if (new HashSet<>(requests).size() != requests.size()) {
            violations.add("Catalog requests must be unique");
        }

        boolean catalogContinuation = stage == ShipStage.DISCOVERY
                && result.outcome() == StageResult.Outcome.NEEDS_DISCOVERY;
        if (catalogContinuation && requests.isEmpty()) {
            violations.add("A discovery continuation requires at least one catalog request");
        } else if (!catalogContinuation && !requests.isEmpty()) {
            violations.add("Only discovery may return catalog requests for a discovery continuation");
        }
    }

    private static void validateOutcome(ShipStage stage, StageResult result, List<String> violations) {
        switch (result.outcome()) {
            case FAILED -> {
                if (isBlank(result.failureCode()) || isBlank(result.failureMessage())
                        || !result.failureCode().matches("[a-z][a-z0-9-]{0,127}")
                        || result.failureMessage().length() > Interaction.MAX_RESPONSE_CHARS) {
                    violations.add("A failed result requires a failure code and message");
                }
                if (result.ledger() != null || result.question() != null) {
                    violations.add("A failed result cannot contain discovery analysis");
                }
                requireNoArtifacts(result, violations);
            }
            case NEEDS_USER_INPUT -> validateNeedsInput(stage, result, violations);
            case NEEDS_DISCOVERY -> validateNeedsDiscovery(stage, result, violations);
            case COMPLETED -> validateCompleted(stage, result, violations);
            default -> violations.add("Unsupported stage outcome: " + result.outcome());
        }
    }

    private static void validateNeedsDiscovery(
            ShipStage stage, StageResult result, List<String> violations) {
        if (stage != ShipStage.DISCOVERY && stage != ShipStage.DESIGN && stage != ShipStage.REVIEW) {
            violations.add("Only discovery, design, or gap review may request another discovery attempt");
        }
        validateLedgerAnalysis(result, violations);
        if (result.question() != null) {
            violations.add("A discovery continuation cannot contain a user question");
        }
        if (result.ledger() != null && !result.ledger().openQuestions().isEmpty()) {
            violations.add("A discovery continuation cannot carry an unpresented ledger question");
        }
        if (!isBlank(result.failureCode()) || !isBlank(result.failureMessage())) {
            violations.add("A discovery continuation cannot contain failure details");
        }
        requireNoArtifacts(result, violations);
    }

    private static void validateNeedsInput(ShipStage stage, StageResult result, List<String> violations) {
        if (stage != ShipStage.DISCOVERY) {
            violations.add("Only discovery may request user input");
        }
        validateLedgerAnalysis(result, violations);
        if (result.question() == null) {
            violations.add("A needs-user-input result requires exactly one question");
        } else if (result.ledger() != null
                && (result.ledger().openQuestions().size() != 1
                        || !result.question().equals(result.ledger().openQuestions().get(0)))) {
            violations.add("The returned question must be the ledger's single pending question");
        }
        if (!isBlank(result.failureCode()) || !isBlank(result.failureMessage())) {
            violations.add("A needs-user-input result cannot contain failure details");
        }
        requireNoArtifacts(result, violations);
    }

    private static void validateLedgerAnalysis(StageResult result, List<String> violations) {
        if (result.ledger() == null) {
            violations.add("Discovery analysis requires a decision ledger");
            return;
        }
        try {
            LedgerValidator.validateAnalysis(result.ledger());
        } catch (LedgerValidationException e) {
            violations.addAll(e.violations());
        }
    }

    private static void validateCompleted(ShipStage stage, StageResult result, List<String> violations) {
        if (!isBlank(result.failureCode()) || !isBlank(result.failureMessage())) {
            violations.add("A completed result cannot contain failure details");
        }
        if (result.question() != null) {
            violations.add("A completed result cannot contain a pending question");
        }
        if (stage != ShipStage.DISCOVERY && stage != ShipStage.REVIEW && result.ledger() != null) {
            violations.add("Only completed discovery or gap review may return a decision ledger");
        }
        switch (stage) {
            case DISCOVERY -> {
                if (result.ledger() == null) {
                    violations.add("Completed discovery requires a decision ledger");
                } else {
                    try {
                        LedgerValidator.validateCandidateReady(result.ledger());
                    } catch (LedgerValidationException e) {
                        violations.addAll(e.violations());
                    }
                }
                requireNoArtifacts(result, violations);
            }
            case DESIGN -> requireArtifactKind(result, "design", violations);
            case PLAN -> requireArtifactKind(result, "plan", violations);
            case EXECUTE -> {
                if (result.artifactManifest() == null) {
                    violations.add("Completed execution requires an artifact manifest");
                }
                if (result.artifacts().isEmpty()) {
                    violations.add("Completed execution requires staged artifacts");
                }
            }
            case VALIDATE -> requireArtifactKind(result, "validation", violations);
            case REVIEW -> {
                if (result.ledger() == null) {
                    violations.add("Completed gap review requires a decision ledger");
                } else {
                    try {
                        LedgerValidator.validateReady(result.ledger());
                    } catch (LedgerValidationException e) {
                        violations.addAll(e.violations());
                    }
                }
                requireNoArtifacts(result, violations);
            }
            default -> violations.add("Unsupported completed stage: " + stage);
        }
    }

    private static void requireNoArtifacts(StageResult result, List<String> violations) {
        if (!result.artifacts().isEmpty() || result.artifactManifest() != null) {
            violations.add("This stage may not return implementation artifacts");
        }
    }

    private static void requireArtifactKind(StageResult result, String kind, List<String> violations) {
        if (result.artifacts().size() != 1
                || result.artifacts().get(0) == null
                || !kind.equals(result.artifacts().get(0).kind())) {
            violations.add("Completed stage requires exactly one " + kind + " artifact");
        }
        if (result.artifactManifest() != null) {
            violations.add("Only execution may return the implementation artifact manifest");
        }
    }

    private static void validateArtifactClaims(List<ProducedArtifact> artifacts, List<String> violations) {
        if (artifacts.isEmpty()) {
            return;
        }
        ShipTreePolicy policy = ShipTreePolicy.current();
        if (artifacts.size() > policy.maxFileCount()) {
            violations.add("Stage result exceeds the " + policy.maxFileCount() + " artifact limit");
            return;
        }
        Set<String> paths = new HashSet<>();
        long aggregateSize = 0;
        for (ProducedArtifact artifact : artifacts) {
            if (artifact == null
                    || artifact.kind() == null
                    || !artifact.kind().matches("[a-z][a-z0-9-]{0,63}")
                    || !safeRelativePath(artifact.relativePath(), policy)
                    || !ShipDigest.isSha256(artifact.digest())
                    || artifact.size() < 0) {
                violations.add("Produced artifacts require a canonical kind, portable relative path, "
                               + "SHA-256 digest, and nonnegative size");
                continue;
            }
            if (artifact.size() > policy.maxFileBytes()) {
                violations.add("Produced artifact exceeds the per-file limit: " + artifact.relativePath());
                continue;
            }
            try {
                aggregateSize = Math.addExact(aggregateSize, artifact.size());
            } catch (ArithmeticException e) {
                aggregateSize = Long.MAX_VALUE;
            }
            if (aggregateSize > policy.maxAggregateBytes()) {
                violations.add("Stage result exceeds the aggregate artifact-byte limit");
                return;
            }
            if (!paths.add(artifact.relativePath())) {
                violations.add("Duplicate produced artifact path: " + artifact.relativePath());
            }
        }
    }

    private static void validateResultAuthority(
            StageRequest request,
            List<ProducedArtifact> artifacts,
            Path activeOutputDirectory,
            List<String> violations) {
        StageCapability capability = request.capability();
        if (capability == null || capability.repositoryAccess() == null) {
            violations.add("Stage results require a controller-issued capability envelope");
            return;
        }
        if (!capability.allowedOperations().contains(Operation.RETURN_STRUCTURED_RESULT)) {
            violations.add("The controller-issued capability must authorize returning a structured result");
        }
        if (artifacts.isEmpty()) {
            return;
        }
        if (!repositoryAccessAllowsArtifacts(request.stage(), capability.repositoryAccess())
                || !capability.allowedOperations().contains(Operation.WRITE_STAGED_ARTIFACT)) {
            violations.add(
                    "Produced artifacts require stage-compatible controller-issued staged-artifact write authority");
            return;
        }
        Path declaredOutput = absoluteCanonicalPath(request.outputDirectory());
        if (declaredOutput == null) {
            violations.add("Produced artifacts require a canonical absolute request output directory");
            return;
        }
        boolean invalidWriteRoot = false;
        boolean outputCovered = false;
        for (String value : capability.writeRoots()) {
            Path root = absoluteCanonicalPath(value);
            if (root == null) {
                invalidWriteRoot = true;
            } else if (declaredOutput.startsWith(root)) {
                outputCovered = true;
            }
        }
        if (invalidWriteRoot || !outputCovered) {
            violations.add("The request output directory must be covered by a canonical capability write root");
        }
        if (activeOutputDirectory == null
                || !declaredOutput.equals(activeOutputDirectory.toAbsolutePath().normalize())) {
            violations.add("The active attempt output directory does not match the request capability envelope");
        }
    }

    private static boolean repositoryAccessAllowsArtifacts(ShipStage stage, RepositoryAccess access) {
        if (stage == null) {
            return false;
        }
        return switch (stage) {
            case DESIGN, PLAN, VALIDATE -> access == RepositoryAccess.READ_WITH_STAGED_OUTPUT;
            case EXECUTE -> access == RepositoryAccess.DECLARED_EXECUTION_PATHS;
            default -> false;
        };
    }

    private static Path absoluteCanonicalPath(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            Path path = Path.of(value);
            return path.isAbsolute() && path.getNameCount() > 0 && path.normalize().equals(path) ? path : null;
        } catch (InvalidPathException e) {
            return null;
        }
    }

    private static void requireEqual(String field, Object expected, Object actual, List<String> violations) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            violations.add("Stage result " + field + " does not match the active attempt");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean safeRelativePath(String value, ShipTreePolicy policy) {
        try {
            ShipTreePolicy.requireCanonicalRelativePath(value);
            Path relative = Path.of(value);
            return !relative.isAbsolute()
                    && relative.normalize().equals(relative)
                    && relative.getNameCount() - 1 <= policy.maxDepth();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

}
