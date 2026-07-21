package io.github.luigidemasi.camelkit.ship.protocol;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.github.luigidemasi.camelkit.ship.ShipDigest;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogSubject;
import io.github.luigidemasi.camelkit.ship.ledger.LedgerValidationException;
import io.github.luigidemasi.camelkit.ship.ledger.LedgerValidator;
import io.github.luigidemasi.camelkit.ship.protocol.StageCapability.Operation;
import io.github.luigidemasi.camelkit.ship.protocol.StageCapability.RepositoryAccess;
import io.github.luigidemasi.camelkit.ship.security.ShipTreePolicy;

/** Validates worker identity, outcome shape, and the live staged bytes observed during one non-certifying preflight. */
public final class StageResultValidator {

    private StageResultValidator() {
    }

    /**
     * Performs a live preflight over one stopped worker's output.
     *
     * <p>
     * The controller must own and protect the attempt-output ancestry from worker replacement while this method runs.
     * Passing preflight does not authorize a later reopen or commit: the protected controller added in the next slice
     * must hash while importing the same opened stream into controller-owned quarantine/CAS and commit only that copy.
     */
    public static void validatePreflight(StageRequest request, StageResult result, Path attemptOutputDirectory) {
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
        validateArtifacts(result.artifacts(), attemptOutputDirectory, violations);
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

    private static void validateArtifacts(
            List<ProducedArtifact> artifacts,
            Path outputDirectory,
            List<String> violations) {
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
        if (!violations.isEmpty()) {
            return;
        }
        if (outputDirectory == null) {
            violations.add("Attempt output directory is required when artifacts are returned");
            return;
        }
        try (SecureArtifactRoot root = SecureArtifactRoot.open(outputDirectory)) {
            for (ProducedArtifact artifact : artifacts) {
                try {
                    ContentIdentity identity = root.identity(artifact.relativePath(), policy.maxFileBytes());
                    if (identity.size() != artifact.size()) {
                        violations.add("Produced artifact size mismatch: " + artifact.relativePath());
                    }
                    if (!identity.digest().equals(artifact.digest())) {
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

    private record ContentIdentity(String digest, long size) {
    }

    private record UnixIdentity(
            Object fileKey,
            long device,
            long inode,
            long linkCount,
            long size,
            long modifiedNanos,
            long changedNanos,
            int mode) {
    }

    /** Held, descriptor-relative attempt output root used only during live preflight. */
    private static final class SecureArtifactRoot implements AutoCloseable {

        private static final String UNIX_ATTRIBUTES = "unix:dev,ino,nlink,size,lastModifiedTime,ctime,mode";
        private static final int FILE_TYPE_MASK = 0170000;
        private static final int DIRECTORY_TYPE = 0040000;
        private static final int REGULAR_FILE_TYPE = 0100000;
        private static final Set<OpenOption> READ_OPTIONS = Set.of(
                StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);

        private final Path path;
        private final SecureDirectoryStream<Path> root;
        private final UnixIdentity identity;

        private SecureArtifactRoot(
                                   Path path, SecureDirectoryStream<Path> root, UnixIdentity identity) {
            this.path = path;
            this.root = root;
            this.identity = identity;
        }

        private static SecureArtifactRoot open(Path requested) throws IOException {
            Path path = requested.toAbsolutePath().normalize();
            if (path.getRoot() == null || path.getNameCount() == 0) {
                throw new IOException("Attempt output root must be a non-root absolute directory");
            }
            DirectoryStream<Path> opened = Files.newDirectoryStream(path.getRoot());
            if (!(opened instanceof SecureDirectoryStream<?> stream)) {
                opened.close();
                throw new IOException("Attempt output filesystem does not provide SecureDirectoryStream");
            }
            @SuppressWarnings("unchecked")
            SecureDirectoryStream<Path> current = (SecureDirectoryStream<Path>) stream;
            Path lexical = path.getRoot();
            try {
                requireBasicDirectory(
                        attributes(current, null), "Filesystem root is not a stable real directory");
                for (Path component : path) {
                    Path name = Path.of(component.toString());
                    lexical = lexical.resolve(name);
                    String description = "Attempt output path " + lexical;
                    BasicFileAttributes basic = attributes(current, name);
                    boolean attemptRoot = lexical.equals(path);
                    UnixIdentity expected = null;
                    if (attemptRoot) {
                        expected = sampleUnix(lexical, basic.fileKey(), description);
                        requireDirectory(basic, expected, null,
                                "Attempt output path contains a non-directory or symbolic entry");
                    } else {
                        // Shared ancestors may change contents; only their opened identity must remain stable.
                        requireBasicDirectory(
                                basic, "Attempt output path contains a non-directory or symbolic entry");
                    }

                    SecureDirectoryStream<Path> child = null;
                    try {
                        child = current.newDirectoryStream(name, LinkOption.NOFOLLOW_LINKS);
                        BasicFileAttributes childAttributes = attributes(child, null);
                        if (attemptRoot) {
                            requireDirectory(childAttributes, expected, null,
                                    "Attempt output directory changed while it was opened");
                            UnixIdentity rebound = sampleUnix(lexical, expected.fileKey(), description);
                            if (!expected.equals(rebound)) {
                                throw new IOException("Attempt output directory changed while it was opened");
                            }
                        } else {
                            requireBasicDirectory(
                                    childAttributes, "Attempt output directory changed while it was opened");
                            if (!basic.fileKey().equals(childAttributes.fileKey())) {
                                throw new IOException("Attempt output directory changed while it was opened");
                            }
                        }
                    } catch (IOException | RuntimeException e) {
                        if (child != null) {
                            try {
                                child.close();
                            } catch (IOException closeFailure) {
                                e.addSuppressed(closeFailure);
                            }
                        }
                        throw e;
                    }
                    try {
                        current.close();
                    } catch (IOException e) {
                        try {
                            child.close();
                        } catch (IOException closeFailure) {
                            e.addSuppressed(closeFailure);
                        }
                        throw e;
                    }
                    current = child;
                }
                BasicFileAttributes held = attributes(current, null);
                UnixIdentity identity = sampleUnix(path, held.fileKey(), "Attempt output root");
                requireDirectory(held, identity, null, "Attempt output root is not a stable real directory");
                return new SecureArtifactRoot(path, current, identity);
            } catch (IOException | RuntimeException e) {
                try {
                    current.close();
                } catch (IOException closeFailure) {
                    e.addSuppressed(closeFailure);
                }
                throw e;
            }
        }

        private ContentIdentity identity(String relativePath, long maximumBytes) throws IOException {
            String[] components = relativePath.split("/");
            return identity(root, path, components, 0, maximumBytes);
        }

        private ContentIdentity identity(
                SecureDirectoryStream<Path> directory,
                Path lexicalDirectory,
                String[] components,
                int index,
                long maximumBytes)
                throws IOException {
            Path name = Path.of(components[index]);
            Path lexical = lexicalDirectory.resolve(name);
            String description = "Produced artifact " + lexical;
            BasicFileAttributes basicBefore = attributes(directory, name);
            UnixIdentity before = sampleUnix(lexical, basicBefore.fileKey(), description);
            if (index < components.length - 1) {
                requireDirectory(basicBefore, before, identity.device(),
                        "Produced artifact path contains a non-directory or symbolic entry");
                try (SecureDirectoryStream<Path> child
                        = directory.newDirectoryStream(name, LinkOption.NOFOLLOW_LINKS)) {
                    requireDirectory(attributes(child, null), before, identity.device(),
                            "Produced artifact directory changed while it was opened");
                    UnixIdentity rebound = sampleUnix(lexical, before.fileKey(), description);
                    if (!before.equals(rebound)) {
                        throw new IOException("Produced artifact directory changed while it was opened");
                    }
                    ContentIdentity result = identity(child, lexical, components, index + 1, maximumBytes);
                    BasicFileAttributes basicAfter = attributes(directory, name);
                    UnixIdentity after = sampleUnix(lexical, before.fileKey(), description);
                    requireDirectory(basicAfter, after, identity.device(),
                            "Produced artifact directory changed while it was read");
                    if (!before.equals(after)) {
                        throw new IOException("Produced artifact directory changed while it was read");
                    }
                    return result;
                }
            }
            requireRegular(basicBefore, before, identity.device(),
                    "Produced artifact is missing, symbolic, hard-linked, or not regular");
            MessageDigest digest = messageDigest();
            long size = 0;
            // Java 17 exposes no fstat-style identity from this channel. This preflight
            // therefore cannot authorize reopening or committing the path later.
            try (SeekableByteChannel input = directory.newByteChannel(name, READ_OPTIONS)) {
                ByteBuffer buffer = ByteBuffer.allocate(8192);
                int read;
                while ((read = input.read(buffer)) != -1) {
                    if (read == 0) {
                        continue;
                    }
                    size = Math.addExact(size, read);
                    if (size > maximumBytes) {
                        throw new IOException("Produced artifact exceeds the per-file limit");
                    }
                    buffer.flip();
                    digest.update(buffer);
                    buffer.clear();
                }
            }
            BasicFileAttributes basicAfter = attributes(directory, name);
            UnixIdentity after = sampleUnix(lexical, before.fileKey(), description);
            requireRegular(basicAfter, after, identity.device(), "Produced artifact changed while it was read");
            if (!before.equals(after) || size != before.size()) {
                throw new IOException("Produced artifact changed while it was read");
            }
            return new ContentIdentity("sha256:" + HexFormat.of().formatHex(digest.digest()), size);
        }

        private static BasicFileAttributes attributes(SecureDirectoryStream<Path> directory, Path name)
                throws IOException {
            BasicFileAttributeView view = name == null
                    ? directory.getFileAttributeView(BasicFileAttributeView.class)
                    : directory.getFileAttributeView(
                            name, BasicFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
            if (view == null) {
                throw new IOException("Attempt output filesystem does not expose descriptor-relative attributes");
            }
            return view.readAttributes();
        }

        private static UnixIdentity sampleUnix(Path path, Object expectedFileKey, String description)
                throws IOException {
            if (expectedFileKey == null) {
                throw new IOException(description + " lacks a stable file key");
            }
            try {
                BasicFileAttributes basicBefore = Files.readAttributes(
                        path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (!expectedFileKey.equals(basicBefore.fileKey())) {
                    throw new IOException(description + " changed before Unix identity sampling");
                }
                Map<String, Object> values = Files.readAttributes(
                        path, UNIX_ATTRIBUTES, LinkOption.NOFOLLOW_LINKS);
                BasicFileAttributes basicAfter = Files.readAttributes(
                        path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (!expectedFileKey.equals(basicAfter.fileKey())) {
                    throw new IOException(description + " changed during Unix identity sampling");
                }
                UnixIdentity identity = new UnixIdentity(
                        expectedFileKey,
                        requiredLong(values, "dev", description),
                        requiredLong(values, "ino", description),
                        requiredLong(values, "nlink", description),
                        requiredLong(values, "size", description),
                        requiredTime(values, "lastModifiedTime", description).to(TimeUnit.NANOSECONDS),
                        requiredTime(values, "ctime", description).to(TimeUnit.NANOSECONDS),
                        requiredInt(values, "mode", description));
                requireBasicBinding(basicBefore, identity, description);
                requireBasicBinding(basicAfter, identity, description);
                return identity;
            } catch (UnsupportedOperationException | IllegalArgumentException e) {
                throw new IOException(description + " filesystem lacks stable Unix identity metadata", e);
            }
        }

        private static long requiredLong(Map<String, Object> values, String name, String description)
                throws IOException {
            Object value = values.get(name);
            if (!(value instanceof Number number)) {
                throw new IOException(description + " lacks Unix attribute " + name);
            }
            return number.longValue();
        }

        private static int requiredInt(Map<String, Object> values, String name, String description)
                throws IOException {
            long value = requiredLong(values, name, description);
            if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
                throw new IOException(description + " has an invalid Unix attribute " + name);
            }
            return (int) value;
        }

        private static FileTime requiredTime(Map<String, Object> values, String name, String description)
                throws IOException {
            Object value = values.get(name);
            if (!(value instanceof FileTime time)) {
                throw new IOException(description + " lacks Unix attribute " + name);
            }
            return time;
        }

        private static void requireBasicDirectory(BasicFileAttributes attributes, String message)
                throws IOException {
            if (!attributes.isDirectory() || attributes.isSymbolicLink() || attributes.fileKey() == null) {
                throw new IOException(message);
            }
        }

        private static void requireDirectory(
                BasicFileAttributes basic,
                UnixIdentity unix,
                Long requiredDevice,
                String message)
                throws IOException {
            if (!basic.isDirectory() || basic.isSymbolicLink() || basic.fileKey() == null
                    || (unix.mode() & FILE_TYPE_MASK) != DIRECTORY_TYPE) {
                throw new IOException(message);
            }
            requireBasicBinding(basic, unix, message);
            requireDevice(unix, requiredDevice, message);
        }

        private static void requireRegular(
                BasicFileAttributes basic,
                UnixIdentity unix,
                long requiredDevice,
                String message)
                throws IOException {
            if (!basic.isRegularFile() || basic.isSymbolicLink() || basic.fileKey() == null
                    || (unix.mode() & FILE_TYPE_MASK) != REGULAR_FILE_TYPE
                    || unix.linkCount() != 1) {
                throw new IOException(message);
            }
            requireBasicBinding(basic, unix, message);
            requireDevice(unix, requiredDevice, message);
        }

        private static void requireBasicBinding(
                BasicFileAttributes basic, UnixIdentity unix, String description)
                throws IOException {
            if (!unix.fileKey().equals(basic.fileKey())
                    || unix.size() != basic.size()
                    || unix.modifiedNanos() != basic.lastModifiedTime().to(TimeUnit.NANOSECONDS)) {
                throw new IOException(description);
            }
        }

        private static void requireDevice(UnixIdentity unix, Long requiredDevice, String message)
                throws IOException {
            if (requiredDevice != null && unix.device() != requiredDevice) {
                throw new IOException(message + ": filesystem device crossing");
            }
        }

        private static MessageDigest messageDigest() {
            try {
                return MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("SHA-256 is not available", e);
            }
        }

        @Override
        public void close() throws IOException {
            IOException failure = null;
            try {
                BasicFileAttributes held = attributes(root, null);
                UnixIdentity rebound = sampleUnix(path, identity.fileKey(), "Attempt output root");
                requireDirectory(held, rebound, null,
                        "Attempt output root changed while artifacts were verified");
                if (!identity.equals(rebound)) {
                    throw new IOException("Attempt output root changed while artifacts were verified");
                }
            } catch (IOException e) {
                failure = e;
            }
            try {
                root.close();
            } catch (IOException e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
            if (failure != null) {
                throw failure;
            }
        }
    }
}
