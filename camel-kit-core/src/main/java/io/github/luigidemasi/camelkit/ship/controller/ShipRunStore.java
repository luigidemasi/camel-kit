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
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
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
            .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES);

    private final Path stateRoot;
    private final boolean posix;

    ShipRunStore(Path stateRoot) {
        this.stateRoot = Objects.requireNonNull(stateRoot, "stateRoot").toAbsolutePath().normalize();
        this.posix = this.stateRoot.getFileSystem().supportedFileAttributeViews().contains("posix");
    }

    void create(ShipRun initial) throws IOException {
        requireCurrent(initial);
        Path runRoot = runRoot(initial.id());
        Files.createDirectories(stateRoot, directoryAttributes());
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
        runRoot(run.id());
    }

    private Path existingRunRoot(String runId) throws StoreException {
        Path runRoot = runRoot(runId);
        if (!Files.exists(runRoot)) {
            throw new StoreException("run-not-found", "Ship run was not found: " + runId);
        }
        if (!Files.isDirectory(runRoot)) {
            throw new StoreException(
                    "state-corrupt", "Ship run path is not a directory: " + runRoot);
        }
        return runRoot;
    }

    private Path runRoot(String runId) throws StoreException {
        if (runId == null || !ShipRun.isRunId(runId)) {
            throw new StoreException("run-id-invalid", "Invalid Ship run ID: " + runId);
        }
        Path runRoot = stateRoot.resolve(runId).normalize();
        if (!stateRoot.equals(runRoot.getParent())) {
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
