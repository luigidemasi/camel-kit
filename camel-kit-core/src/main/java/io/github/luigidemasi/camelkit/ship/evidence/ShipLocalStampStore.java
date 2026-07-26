package io.github.luigidemasi.camelkit.ship.evidence;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import io.github.luigidemasi.camelkit.ship.ShipDigest;
import io.github.luigidemasi.camelkit.ship.controller.ShipRun;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Atomic persistence for one controller-derived local Stamp.
 *
 * <p>
 * Callers serialize access and keep referenced logs quiescent during reads and writes.
 */
public final class ShipLocalStampStore {

    private static final String STAMP_FILE = "stamp.json";
    private static final int MAX_STAMP_BYTES = 64 * 1024 * 1024;
    private static final int MAX_LOG_BYTES = 64 * 1024 * 1024;
    private static final long MAX_TOTAL_LOG_BYTES = 64L * 1024 * 1024;
    private static final Set<PosixFilePermission> PRIVATE_DIRECTORY
            = PosixFilePermissions.fromString("rwx------");
    private static final Set<PosixFilePermission> PRIVATE_FILE
            = PosixFilePermissions.fromString("rw-------");
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

    private ShipLocalStampStore() {
    }

    public static Path write(
            Path evidenceDirectory, String expectedRunId, ShipLocalStamp stamp)
            throws IOException {
        Path directory = realDirectory(evidenceDirectory);
        UserPrincipal owner = requirePrivateDirectory(directory);
        ShipLocalStamp report = Objects.requireNonNull(stamp, "stamp");
        requireRun(expectedRunId, report);
        verifyCommandLogs(directory, owner, report);

        Path temporary = Files.createTempFile(
                directory, ".stamp-", ".tmp", fileAttributes(directory));
        boolean moved = false;
        Throwable primary = null;
        try {
            requirePrivateFile(temporary, owner, "Temporary local Stamp");
            try (FileChannel channel = FileChannel.open(
                    temporary,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    LinkOption.NOFOLLOW_LINKS)) {
                try {
                    JSON.writerWithDefaultPrettyPrinter().writeValue(
                            new CappedOutputStream(
                                    Channels.newOutputStream(channel),
                                    MAX_STAMP_BYTES),
                            report);
                } catch (JsonProcessingException e) {
                    throw new IOException("Local Stamp cannot be encoded", e);
                }
                channel.force(true);
            }
            try {
                Files.move(
                        temporary,
                        directory.resolve(STAMP_FILE),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                throw new IOException("Filesystem does not support atomic local Stamp replacement", e);
            }
            moved = true;
            Path result = directory.resolve(STAMP_FILE);
            requirePrivateFile(result, owner, "Local Stamp");
            return result;
        } catch (IOException | RuntimeException | Error e) {
            primary = e;
            throw e;
        } finally {
            if (!moved) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException cleanup) {
                    if (primary == null) {
                        throw cleanup;
                    }
                    primary.addSuppressed(cleanup);
                }
            }
        }
    }

    public static ShipLocalStamp read(Path evidenceDirectory, String expectedRunId)
            throws IOException {
        if (!ShipRun.isRunId(expectedRunId)) {
            throw new IOException("Expected local Stamp run ID is invalid");
        }
        Path directory = realDirectory(evidenceDirectory);
        UserPrincipal owner = requirePrivateDirectory(directory);
        Path file = directory.resolve(STAMP_FILE);
        if (Files.isSymbolicLink(file)
                || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Local Stamp is missing or invalid: " + file);
        }
        requirePrivateFile(file, owner, "Local Stamp");
        byte[] encoded;
        try (InputStream input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            encoded = input.readNBytes(MAX_STAMP_BYTES + 1);
        }
        if (encoded.length == 0 || encoded.length > MAX_STAMP_BYTES) {
            throw new IOException("Local Stamp has an invalid size: " + file);
        }
        try {
            ShipLocalStamp stamp = JSON.readValue(encoded, ShipLocalStamp.class);
            if (stamp == null) {
                throw new IllegalArgumentException("Local Stamp root cannot be null");
            }
            requireRun(expectedRunId, stamp);
            verifyCommandLogs(directory, owner, stamp);
            return stamp;
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new IOException("Local Stamp is invalid: " + file, e);
        }
    }

    private static void requireRun(String expectedRunId, ShipLocalStamp stamp)
            throws IOException {
        if (!ShipRun.isRunId(expectedRunId)
                || !expectedRunId.equals(stamp.runId())) {
            throw new IOException("Local Stamp does not match its expected Ship run");
        }
    }

    private static Path realDirectory(Path supplied) throws IOException {
        if (supplied == null) {
            throw new IOException("Local Stamp directory is required");
        }
        Path normalized = supplied.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(normalized)
                || !Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Local Stamp directory must be a real directory");
        }
        return normalized.toRealPath();
    }

    private static UserPrincipal requirePrivateDirectory(Path directory)
            throws IOException {
        if (!directory.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            throw new IOException("Local Stamp directory must support POSIX permissions");
        }
        String userName = System.getProperty("user.name");
        if (userName == null || userName.isBlank()) {
            throw new IOException("Could not determine the current user for the local Stamp directory");
        }
        UserPrincipal currentUser = directory.getFileSystem()
                .getUserPrincipalLookupService()
                .lookupPrincipalByName(userName);
        PosixFileAttributes attributes = Files.readAttributes(
                directory, PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!currentUser.equals(attributes.owner())) {
            throw new IOException("Local Stamp directory must be owned by the current user");
        }
        if (!PRIVATE_DIRECTORY.equals(attributes.permissions())) {
            throw new IOException("Local Stamp directory permissions must be 0700");
        }
        return currentUser;
    }

    private static PosixFileAttributes requirePrivateFile(
            Path file, UserPrincipal owner, String label)
            throws IOException {
        PosixFileAttributes attributes = Files.readAttributes(
                file, PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isRegularFile() || attributes.isSymbolicLink()) {
            throw new IOException(label + " must be a real regular file: " + file);
        }
        if (!owner.equals(attributes.owner())) {
            throw new IOException(label + " must be owned by the current user: " + file);
        }
        if (!PRIVATE_FILE.equals(attributes.permissions())) {
            throw new IOException(label + " permissions must be 0600: " + file);
        }
        return attributes;
    }

    private static void verifyCommandLogs(
            Path evidenceDirectory, UserPrincipal owner, ShipLocalStamp stamp)
            throws IOException {
        Map<Object, ExpectedLog> logs = new LinkedHashMap<>();
        long totalBytes = 0;
        for (ShipLocalStamp.Check check : stamp.checks()) {
            ShipLocalStamp.CommandRun command = check.command();
            if (command == null) {
                continue;
            }
            ExpectedLog stdout = resolveLog(
                    evidenceDirectory, owner, command.stdoutLog(), command.stdoutDigest());
            ExpectedLog stderr = resolveLog(
                    evidenceDirectory, owner, command.stderrLog(), command.stderrDigest());
            if (stdout.identity().equals(stderr.identity())
                    || Files.isSameFile(stdout.path(), stderr.path())) {
                throw new IOException(
                        "Local Stamp command stdout and stderr must be distinct files");
            }
            totalBytes = addLog(logs, stdout, totalBytes);
            totalBytes = addLog(logs, stderr, totalBytes);
        }
        if (totalBytes > MAX_TOTAL_LOG_BYTES) {
            throw new IOException("Local Stamp retained logs exceed their total size limit");
        }
        for (ExpectedLog log : logs.values()) {
            verifyLog(log);
        }
    }

    private static ExpectedLog resolveLog(
            Path evidenceDirectory,
            UserPrincipal owner,
            String supplied,
            String expectedDigest)
            throws IOException {
        Path log = Path.of(supplied).toAbsolutePath().normalize();
        String name = String.valueOf(log.getFileName());
        if (STAMP_FILE.equals(name)
                || name.startsWith(".stamp-")
                || Files.isSymbolicLink(log)
                || !Files.isRegularFile(log, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Local Stamp command log is outside its evidence directory: " + log);
        }
        Path real = log.toRealPath();
        if (!real.startsWith(evidenceDirectory)) {
            throw new IOException("Local Stamp command log escaped its evidence directory: " + log);
        }
        BasicFileAttributes attributes = requirePrivateFile(
                real, owner, "Local Stamp command log");
        if (attributes.size() > MAX_LOG_BYTES) {
            throw new IOException("Local Stamp command log has an invalid size: " + real);
        }
        Object identity = attributes.fileKey() == null ? real : attributes.fileKey();
        return new ExpectedLog(identity, real, attributes.size(), expectedDigest);
    }

    private static long addLog(
            Map<Object, ExpectedLog> logs, ExpectedLog candidate, long totalBytes)
            throws IOException {
        ExpectedLog existing = logs.putIfAbsent(candidate.identity(), candidate);
        if (existing != null) {
            if (!existing.digest().equals(candidate.digest())) {
                throw new IOException(
                        "Local Stamp records conflicting digests for one command log");
            }
            return totalBytes;
        }
        try {
            return Math.addExact(totalBytes, candidate.size());
        } catch (ArithmeticException e) {
            throw new IOException("Local Stamp retained logs exceed their total size limit", e);
        }
    }

    private static void verifyLog(ExpectedLog log) throws IOException {
        byte[] content;
        try (InputStream input = Files.newInputStream(log.path(), LinkOption.NOFOLLOW_LINKS)) {
            content = input.readNBytes(MAX_LOG_BYTES + 1);
        }
        if (content.length > MAX_LOG_BYTES
                || content.length != log.size()
                || !ShipDigest.sha256(content).equals(log.digest())) {
            throw new IOException(
                    "Local Stamp command log does not match its recorded digest: " + log.path());
        }
    }

    private record ExpectedLog(
            Object identity, Path path, long size, String digest) {
    }

    private static final class CappedOutputStream extends OutputStream {

        private final OutputStream delegate;
        private final long maximum;
        private long written;

        private CappedOutputStream(OutputStream delegate, long maximum) {
            this.delegate = delegate;
            this.maximum = maximum;
        }

        @Override
        public void write(int value) throws IOException {
            requireCapacity(1);
            delegate.write(value);
            written++;
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            requireCapacity(length);
            delegate.write(bytes, offset, length);
            written += length;
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        @Override
        public void close() throws IOException {
            flush();
        }

        private void requireCapacity(int additional) throws IOException {
            if (additional < 0 || written > maximum - additional) {
                throw new IOException("Local Stamp exceeds its size limit");
            }
        }
    }

    private static FileAttribute<?>[] fileAttributes(Path path) {
        return path.getFileSystem().supportedFileAttributeViews().contains("posix")
                ? new FileAttribute<?>[]{
                        PosixFilePermissions.asFileAttribute(
                                PosixFilePermissions.fromString("rw-------"))}
                : new FileAttribute<?>[0];
    }
}
