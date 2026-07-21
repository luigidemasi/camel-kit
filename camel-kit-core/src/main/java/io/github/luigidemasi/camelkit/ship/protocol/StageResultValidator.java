package io.github.luigidemasi.camelkit.ship.protocol;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.luigidemasi.camelkit.ship.ShipDigest;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogSubject;
import io.github.luigidemasi.camelkit.ship.ledger.LedgerValidationException;
import io.github.luigidemasi.camelkit.ship.ledger.LedgerValidator;
import io.github.luigidemasi.camelkit.ship.security.ShipTreePolicy;

/** Validates worker identity, outcome shape, and staged output before the controller can commit a result. */
public final class StageResultValidator {

    private StageResultValidator() {
    }

    public static void validate(StageRequest request, StageResult result, Path attemptOutputDirectory) {
        validate(request, result, attemptOutputDirectory, true);
    }

    /** Revalidates an authenticated historical result without consulting mutable worker output. */
    public static void validateRecorded(StageRequest request, StageResult result) {
        validate(request, result, null, false);
    }

    private static void validate(
            StageRequest request, StageResult result, Path attemptOutputDirectory, boolean verifyFiles) {
        List<String> violations = new ArrayList<>();
        if (request == null || result == null) {
            throw new StageResultValidationException(List.of("Stage request and result are required"));
        }
        requireEqual("schema version", StageResult.SCHEMA_VERSION, result.schemaVersion(), violations);
        requireEqual("run ID", request.runId(), result.runId(), violations);
        requireEqual("stage", request.stage(), result.stage(), violations);
        requireEqual("attempt ID", request.attemptId(), result.attemptId(), violations);
        requireEqual("challenge", request.challenge(), result.challenge(), violations);
        requireEqual("input digest", request.inputDigest(), result.inputDigest(), violations);
        if (result.outcome() == null) {
            violations.add("Stage outcome is required");
        } else {
            validateOutcome(request.stage(), result, violations);
        }
        validateCatalogRequests(request.stage(), result, violations);
        validateArtifacts(result.artifacts(), attemptOutputDirectory, verifyFiles, violations);
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
                        || !result.question().id().equals(result.ledger().openQuestions().get(0).id()))) {
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

    private static void validateArtifacts(
            List<ProducedArtifact> artifacts,
            Path outputDirectory,
            boolean verifyFiles,
            List<String> violations) {
        if (artifacts.isEmpty()) {
            return;
        }
        ShipTreePolicy policy = ShipTreePolicy.current();
        if (artifacts.size() > policy.maxFileCount()) {
            violations.add("Stage result exceeds the " + policy.maxFileCount() + " artifact limit");
            return;
        }
        if (verifyFiles && outputDirectory == null) {
            violations.add("Attempt output directory is required when artifacts are returned");
            return;
        }
        Path root = verifyFiles ? outputDirectory.toAbsolutePath().normalize() : null;
        Set<String> paths = new HashSet<>();
        long aggregateSize = 0;
        for (ProducedArtifact artifact : artifacts) {
            if (artifact == null
                    || artifact.kind() == null
                    || !artifact.kind().matches("[a-z][a-z0-9-]{0,63}")
                    || !safeRelativePath(artifact.relativePath())
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
            if (!verifyFiles) {
                continue;
            }
            Path relative = Path.of(artifact.relativePath());
            Path file = root.resolve(relative).normalize();
            if (relative.isAbsolute() || !file.startsWith(root)) {
                violations.add("Produced artifact escapes the attempt output directory: " + artifact.relativePath());
                continue;
            }
            if (Files.isSymbolicLink(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                violations.add("Produced artifact is missing, symbolic, or not regular: " + artifact.relativePath());
                continue;
            }
            try {
                ContentIdentity identity = identity(file, policy.maxFileBytes());
                if (identity.size() != artifact.size()) {
                    violations.add("Produced artifact size mismatch: " + artifact.relativePath());
                }
                if (!identity.digest().equals(artifact.digest())) {
                    violations.add("Produced artifact digest mismatch: " + artifact.relativePath());
                }
            } catch (IOException e) {
                violations.add("Could not verify produced artifact " + artifact.relativePath() + ": " + e.getMessage());
            }
        }
    }

    private static ContentIdentity identity(Path file, long maximumBytes) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long size = 0;
            try (var input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    size = Math.addExact(size, read);
                    if (size > maximumBytes) {
                        throw new IOException("Produced artifact exceeds the per-file limit");
                    }
                    digest.update(buffer, 0, read);
                }
            }
            return new ContentIdentity(
                    "sha256:" + java.util.HexFormat.of().formatHex(digest.digest()), size);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
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

    private static boolean safeRelativePath(String value) {
        if (value == null || value.isBlank() || value.length() > Interaction.MAX_REFERENCE_CHARS
                || value.startsWith("/") || value.startsWith("\\") || value.contains("\\")) {
            return false;
        }
        String[] segments = value.split("/", -1);
        for (String segment : segments) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                return false;
            }
        }
        try {
            Path relative = Path.of(value);
            return !relative.isAbsolute() && relative.normalize().equals(relative);
        } catch (java.nio.file.InvalidPathException e) {
            return false;
        }
    }

    private record ContentIdentity(String digest, long size) {
    }
}
