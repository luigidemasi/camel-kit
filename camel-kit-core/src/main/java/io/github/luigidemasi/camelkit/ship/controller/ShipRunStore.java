package io.github.luigidemasi.camelkit.ship.controller;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Objects;
import java.util.Set;

import io.github.luigidemasi.camelkit.ship.controller.ShipRun.ArtifactRef;
import io.github.luigidemasi.camelkit.ship.controller.ShipRun.Stage;
import io.github.luigidemasi.camelkit.ship.controller.ShipRun.StageRecord;
import io.github.luigidemasi.camelkit.ship.controller.ShipRun.StageStatus;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Atomic local persistence and exclusive mutation leases for Ship runs. */
final class ShipRunStore {

    private static final int MAX_STATE_BYTES = 64 * 1024 * 1024;
    private static final String STATE_FILE = "state.json";
    private static final String LOCK_FILE = "run.lock";
    private static final ObjectMapper JSON = new ObjectMapper(
            JsonFactory.builder()
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS);

    private final Path stateRoot;
    private final boolean posix;

    ShipRunStore(Path stateRoot) {
        this.stateRoot = Objects.requireNonNull(stateRoot, "stateRoot").toAbsolutePath().normalize();
        this.posix = this.stateRoot.getFileSystem().supportedFileAttributeViews().contains("posix");
    }

    void create(ShipRun initial) throws IOException {
        requireCurrent(initial);
        Path root = resolvedStateRoot("state-root-invalid");
        Path runRoot = runRoot(root, initial.id());
        requireDisjoint(runRoot, initial, "state-project-overlap");
        Files.createDirectories(root, directoryAttributes());
        root = resolvedStateRoot("state-root-invalid");
        runRoot = runRoot(root, initial.id());
        requireDisjoint(runRoot, initial, "state-project-overlap");
        try {
            Files.createDirectory(runRoot, directoryAttributes());
        } catch (FileAlreadyExistsException e) {
            throw new StoreException(
                    "run-already-exists", "Ship run already exists: " + initial.id(), e);
        }
        try {
            write(runRoot, initial);
        } catch (IOException e) {
            try {
                Files.delete(runRoot);
            } catch (IOException cleanupFailure) {
                e.addSuppressed(cleanupFailure);
            }
            throw e;
        }
    }

    ShipRun read(String runId) throws IOException {
        Path runRoot = existingRunRoot(runId);
        Path stateFile = runRoot.resolve(STATE_FILE);
        if (!Files.exists(stateFile)) {
            throw new StoreException(
                    "state-missing", "Ship run state is missing: " + stateFile);
        }
        if (!Files.isRegularFile(stateFile)) {
            throw new StoreException(
                    "state-corrupt", "Ship run state is not a regular file: " + stateFile);
        }

        if (Files.size(stateFile) > MAX_STATE_BYTES) {
            throw new StoreException(
                    "state-corrupt", "Ship run state has an invalid size: " + stateFile);
        }
        byte[] encoded;
        try (InputStream input = Files.newInputStream(stateFile)) {
            encoded = input.readNBytes(MAX_STATE_BYTES + 1);
        }
        if (encoded.length == 0 || encoded.length > MAX_STATE_BYTES) {
            throw new StoreException(
                    "state-corrupt", "Ship run state exceeds the read limit: " + stateFile);
        }

        JsonNode document;
        try {
            document = JSON.readValue(encoded, JsonNode.class);
        } catch (JsonProcessingException e) {
            throw corrupt(stateFile, e);
        }
        if (document == null || !document.isObject()) {
            throw corrupt(stateFile, null);
        }
        JsonNode schema = document.get("schemaVersion");
        if (schema == null || !schema.isIntegralNumber() || !schema.canConvertToInt()) {
            throw corrupt(stateFile, null);
        }
        int schemaVersion = schema.intValue();
        if (schemaVersion != ShipRun.SCHEMA_VERSION) {
            throw new StoreException(
                    "state-version-unsupported",
                    "Unsupported Ship run state schema " + schemaVersion
                                                 + "; expected " + ShipRun.SCHEMA_VERSION);
        }

        ShipRun run;
        try {
            run = JSON.treeToValue(document, ShipRun.class);
        } catch (JsonProcessingException e) {
            throw corrupt(stateFile, e);
        }
        if (!runId.equals(run.id())) {
            throw new StoreException(
                    "state-corrupt",
                    "Ship run state ID " + run.id() + " does not match its directory " + runId);
        }
        requireDisjoint(runRoot, run, "state-corrupt");
        requireWorkspaceEvidence(runRoot, run, "state-corrupt");
        requireLocalStampEvidence(runRoot, run, "state-corrupt");
        return run;
    }

    LockedRun lock(String runId) throws IOException {
        Path runRoot = existingRunRoot(runId);
        Path lockFile = runRoot.resolve(LOCK_FILE);
        FileChannel channel = FileChannel.open(
                lockFile,
                Set.of(StandardOpenOption.CREATE, StandardOpenOption.WRITE),
                fileAttributes());
        FileLock fileLock;
        try {
            fileLock = channel.tryLock();
        } catch (OverlappingFileLockException e) {
            closeAfterFailure(channel, e);
            throw busy(runId, e);
        } catch (IOException e) {
            closeAfterFailure(channel, e);
            throw e;
        }
        if (fileLock == null) {
            StoreException busy = busy(runId, null);
            closeAfterFailure(channel, busy);
            throw busy;
        }
        return new LockedRun(runId, runRoot, channel, fileLock);
    }

    private void write(Path runRoot, ShipRun run) throws IOException {
        requireCurrent(run);
        if (Files.isSymbolicLink(runRoot)
                || !Files.isDirectory(runRoot, LinkOption.NOFOLLOW_LINKS)) {
            throw new StoreException(
                    "state-invalid", "Ship run path is not a real directory: " + runRoot);
        }
        requireDisjoint(runRoot, run, "state-invalid");
        requireWorkspaceEvidence(runRoot, run, "state-invalid");
        requireLocalStampEvidence(runRoot, run, "state-invalid");
        byte[] encoded;
        try {
            encoded = JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(run);
        } catch (JsonProcessingException e) {
            throw new StoreException(
                    "state-invalid", "Ship run state cannot be encoded: " + run.id(), e);
        }
        if (encoded.length > MAX_STATE_BYTES) {
            throw new StoreException(
                    "state-too-large", "Ship run state exceeds " + MAX_STATE_BYTES + " bytes");
        }

        Path temporary = Files.createTempFile(
                runRoot, ".state-", ".tmp", fileAttributes());
        boolean moved = false;
        try {
            try (FileChannel channel = FileChannel.open(
                    temporary,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer content = ByteBuffer.wrap(encoded);
                while (content.hasRemaining()) {
                    channel.write(content);
                }
                channel.force(true);
            }
            try {
                Files.move(
                        temporary,
                        runRoot.resolve(STATE_FILE),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                throw new StoreException(
                        "atomic-write-unsupported",
                        "Filesystem does not support atomic Ship run state replacement",
                        e);
            }
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private void requireCurrent(ShipRun run) throws StoreException {
        if (run == null) {
            throw new StoreException("state-invalid", "Ship run state is required");
        }
        runRoot(stateRoot, run.id());
    }

    private static void requireWorkspaceEvidence(
            Path runRoot, ShipRun run, String code)
            throws StoreException {
        requireImportedEvidence(run, code);
        StageRecord execute = run.stage(Stage.EXECUTE);
        if (execute.status() != StageStatus.COMPLETED) {
            return;
        }
        if (execute.attempts() == 0) {
            return;
        }
        Path expected = runRoot.resolve("workspace/candidate").toAbsolutePath().normalize();
        ArtifactRef root = null;
        for (ArtifactRef artifact : execute.artifacts()) {
            Path path = Path.of(artifact.path());
            if (!path.startsWith(expected)) {
                throw invalidWorkspaceEvidence(run, code);
            }
            if (path.equals(expected)) {
                if (root != null) {
                    throw invalidWorkspaceEvidence(run, code);
                }
                root = artifact;
            }
        }
        if (root == null
                || !ShipRun.executeOutputDigest(root).equals(execute.outputDigest())) {
            throw invalidWorkspaceEvidence(run, code);
        }
    }

    private static void requireLocalStampEvidence(
            Path runRoot, ShipRun run, String code)
            throws StoreException {
        StageRecord validation = run.stage(Stage.VALIDATE);
        if (validation.attempts() <= 0) {
            return;
        }
        if (validation.artifacts().isEmpty()
                && validation.status() != StageStatus.COMPLETED) {
            return;
        }
        Path expected = runRoot.resolve("evidence")
                .resolve("validate-" + validation.attempts())
                .resolve("stamp.json")
                .toAbsolutePath()
                .normalize();
        boolean localStamp = validation.artifacts().size() == 1
                && expected.toString().equals(
                        validation.artifacts().get(0).path())
                && validation.outputDigest().equals(
                        validation.artifacts().get(0).digest());
        if (localStamp) {
            return;
        }
        throw new StoreException(
                code,
                "Completed Ship validation evidence is invalid for run " + run.id());
    }

    private static void requireImportedEvidence(ShipRun run, String code)
            throws StoreException {
        boolean attemptedStageCompleted = false;
        Path project = Path.of(run.projectDirectory());
        Path documents = run.pipelineId() == null
                ? null
                : project.resolve("docs/camel-kit").resolve(run.pipelineId());
        for (StageRecord record : run.stages()) {
            if (record.status() != StageStatus.COMPLETED) {
                continue;
            }
            if (record.attempts() > 0) {
                attemptedStageCompleted = true;
                continue;
            }
            if (attemptedStageCompleted
                    || !record.inputDigest().equals(
                            ShipRun.inputDigest(run.context(), run.stages(), record.stage()))) {
                throw invalidImportedEvidence(run, code);
            }
            boolean canonical = switch (record.stage()) {
                case DISCOVERY -> record.artifacts().isEmpty()
                        && record.outputDigest().equals(run.context().digest());
                case DESIGN -> documents != null
                        && isImportedFile(record, documents.resolve("design-spec.md"));
                case PLAN -> documents != null
                        && isImportedFile(record, documents.resolve("implementation-plan.md"));
                case EXECUTE -> documents != null
                        && isImportedExecute(run, record, documents);
                case VALIDATE -> false;
            };
            if (!canonical) {
                throw invalidImportedEvidence(run, code);
            }
        }
    }

    private static boolean isImportedExecute(
            ShipRun run, StageRecord execute, Path documents) {
        if (execute.artifacts().size() != 2) {
            return false;
        }
        ArtifactRef report = execute.artifacts().get(0);
        ArtifactRef snapshot = execute.artifacts().get(1);
        Path expectedReport = documents.resolve("execution-report.md");
        return report.path().equals(expectedReport.toString())
                && snapshot.path().equals(run.projectDirectory())
                && execute.outputDigest().equals(report.digest());
    }

    private static boolean isImportedFile(StageRecord stage, Path expected) {
        return stage.artifacts().size() == 1
                && stage.artifacts().get(0).path().equals(expected.toString())
                && stage.outputDigest().equals(stage.artifacts().get(0).digest());
    }

    private static StoreException invalidWorkspaceEvidence(ShipRun run, String code) {
        return new StoreException(
                code,
                "Completed Ship EXECUTE evidence is invalid for run " + run.id());
    }

    private static StoreException invalidImportedEvidence(ShipRun run, String code) {
        return new StoreException(
                code,
                "Imported Ship stage evidence is invalid for run " + run.id());
    }

    private Path existingRunRoot(String runId) throws StoreException {
        Path runRoot = runRoot(resolvedStateRoot("state-corrupt"), runId);
        if (!Files.exists(runRoot, LinkOption.NOFOLLOW_LINKS)) {
            throw new StoreException("run-not-found", "Ship run was not found: " + runId);
        }
        if (Files.isSymbolicLink(runRoot)
                || !Files.isDirectory(runRoot, LinkOption.NOFOLLOW_LINKS)) {
            throw new StoreException(
                    "state-corrupt", "Ship run path is not a real directory: " + runRoot);
        }
        return runRoot;
    }

    private static void requireDisjoint(
            Path runRoot, ShipRun run, String code)
            throws StoreException {
        final Path canonicalRunRoot;
        final Path project;
        try {
            canonicalRunRoot = canonicalize(runRoot);
            project = canonicalize(Path.of(run.projectDirectory()));
        } catch (IOException | RuntimeException e) {
            throw new StoreException(
                    code,
                    "Ship run state and project directories could not be resolved",
                    e);
        }
        if (canonicalRunRoot.startsWith(project) || project.startsWith(canonicalRunRoot)) {
            throw new StoreException(
                    code,
                    "Ship run state and project directories must be disjoint");
        }
    }

    private Path resolvedStateRoot(String code) throws StoreException {
        final Path resolved;
        try {
            resolved = canonicalize(stateRoot);
        } catch (IOException | SecurityException e) {
            throw new StoreException(code, "Ship state root could not be resolved", e);
        }
        if (Files.exists(resolved, LinkOption.NOFOLLOW_LINKS)
                && !Files.isDirectory(resolved, LinkOption.NOFOLLOW_LINKS)) {
            throw new StoreException(
                    code,
                    "Ship state root must be a real directory");
        }
        return resolved;
    }

    private static Path canonicalize(Path path) throws IOException {
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return path.toRealPath();
        }
        Path parent = path.getParent();
        return parent == null
                ? path.toRealPath()
                : canonicalize(parent).resolve(path.getFileName()).normalize();
    }

    private static Path runRoot(Path root, String runId) throws StoreException {
        if (runId == null || !ShipRun.isRunId(runId)) {
            throw new StoreException("run-id-invalid", "Invalid Ship run ID: " + runId);
        }
        Path runRoot = root.resolve(runId).normalize();
        if (!root.equals(runRoot.getParent())) {
            throw new StoreException("run-id-invalid", "Invalid Ship run ID: " + runId);
        }
        return runRoot;
    }

    private FileAttribute<?>[] directoryAttributes() {
        return attributes("rwx------");
    }

    private FileAttribute<?>[] fileAttributes() {
        return attributes("rw-------");
    }

    private FileAttribute<?>[] attributes(String permissions) {
        return posix
                ? new FileAttribute<?>[]{
                        PosixFilePermissions.asFileAttribute(
                                PosixFilePermissions.fromString(permissions))}
                : new FileAttribute<?>[0];
    }

    private static StoreException corrupt(Path stateFile, Throwable cause) {
        String message = "Ship run state is corrupt: " + stateFile;
        return cause == null
                ? new StoreException("state-corrupt", message)
                : new StoreException("state-corrupt", message, cause);
    }

    private static StoreException busy(String runId, Throwable cause) {
        String message = "Ship run already has an in-progress operation: " + runId;
        return cause == null
                ? new StoreException("operation-in-progress", message)
                : new StoreException("operation-in-progress", message, cause);
    }

    private static void closeAfterFailure(FileChannel channel, Throwable failure) {
        try {
            channel.close();
        } catch (IOException closeFailure) {
            failure.addSuppressed(closeFailure);
        }
    }

    final class LockedRun implements AutoCloseable {

        private final String runId;
        private final Path runRoot;
        private FileChannel channel;
        private FileLock fileLock;

        private LockedRun(
                          String runId, Path runRoot, FileChannel channel, FileLock fileLock) {
            this.runId = runId;
            this.runRoot = runRoot;
            this.channel = channel;
            this.fileLock = fileLock;
        }

        ShipRun read() throws IOException {
            requireOpen();
            return ShipRunStore.this.read(runId);
        }

        void write(ShipRun next) throws IOException {
            requireOpen();
            requireCurrent(next);
            if (!runId.equals(next.id())) {
                throw new StoreException(
                        "state-id-mismatch",
                        "Cannot write Ship run " + next.id() + " while " + runId + " is locked");
            }
            ShipRunStore.this.write(runRoot, next);
        }

        Path directory() throws StoreException {
            requireOpen();
            return runRoot;
        }

        @Override
        public void close() throws IOException {
            if (fileLock == null) {
                return;
            }
            IOException failure = null;
            try {
                fileLock.release();
            } catch (IOException e) {
                failure = e;
            }
            try {
                channel.close();
            } catch (IOException e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            } finally {
                fileLock = null;
                channel = null;
            }
            if (failure != null) {
                throw failure;
            }
        }

        private void requireOpen() throws StoreException {
            if (fileLock == null) {
                throw new StoreException("lock-closed", "Ship run lock is already closed: " + runId);
            }
        }
    }

    static final class StoreException extends IOException {

        private final String code;

        StoreException(String code, String message) {
            super(message);
            this.code = code;
        }

        StoreException(String code, String message, Throwable cause) {
            super(message, cause);
            this.code = code;
        }

        String code() {
            return code;
        }
    }
}
