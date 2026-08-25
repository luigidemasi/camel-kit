package io.github.luigidemasi.camelkit.ship.worker;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import io.github.luigidemasi.camelkit.ship.controller.ShipRun.Stage;
import io.github.luigidemasi.camelkit.ship.evidence.ShipLocalStamp.CommandRun;
import io.github.luigidemasi.camelkit.ship.worker.PiWorker.Request;
import io.github.luigidemasi.camelkit.ship.worker.PiWorker.Result;
import io.github.luigidemasi.camelkit.ship.worker.PiWorker.UntrustedResultException;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Atomic recovery marker for a completed Pi stage attempt. */
final class PiWorkerResultStore {

    private static final int SCHEMA_VERSION = 3;
    private static final int MAX_RESULT_BYTES = 20 * 1024 * 1024;
    private static final String DIRECTORY = ".camel-kit-results";
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

    private PiWorkerResultStore() {
    }

    static Optional<Result> read(Request request)
            throws IOException {
        Path path = resultPath(request, false);
        if (path == null) {
            return Optional.empty();
        }
        BasicFileAttributes attributes = attributesIfPresent(path);
        if (attributes == null) {
            return Optional.empty();
        }
        if (attributes.isSymbolicLink() || !attributes.isRegularFile()) {
            throw new UntrustedResultException(
                    "Pi stage result marker is invalid");
        }
        byte[] encoded;
        try (InputStream input = Files.newInputStream(
                path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            encoded = input.readNBytes(MAX_RESULT_BYTES + 1);
        } catch (NoSuchFileException e) {
            throw new UntrustedResultException(
                    "Pi stage result marker is missing",
                    e);
        }
        if (encoded.length == 0 || encoded.length > MAX_RESULT_BYTES) {
            throw new UntrustedResultException(
                    "Pi stage result marker has an invalid size");
        }
        final Marker marker;
        try {
            marker = JSON.readValue(encoded, Marker.class);
        } catch (JsonProcessingException e) {
            throw new UntrustedResultException(
                    "Pi stage result marker is malformed",
                    e);
        }
        if (marker.schemaVersion() != SCHEMA_VERSION) {
            throw new UntrustedResultException(
                    "Pi stage result marker has an unsupported schema version");
        }
        if (!marker.matches(request)) {
            throw new UntrustedResultException(
                    "Pi stage result marker does not match its attempt");
        }
        verifyEvidence(request, marker);
        return Optional.of(marker.result());
    }

    static void write(
            Request request,
            Result result,
            long stdoutSize,
            long stderrSize)
            throws IOException {
        Path path = resultPath(request, true);
        byte[] encoded;
        try {
            encoded = JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(
                    new Marker(
                            SCHEMA_VERSION,
                            request.runId(),
                            request.stage(),
                            request.attempt(),
                            request.inputDigest(),
                            stdoutSize,
                            stderrSize,
                            result));
        } catch (JsonProcessingException e) {
            throw new IOException("Pi stage result marker cannot be encoded", e);
        }
        if (encoded.length > MAX_RESULT_BYTES) {
            throw new IOException("Pi stage result marker exceeds its size limit");
        }
        Path temporary = Files.createTempFile(
                path.getParent(),
                ".result-",
                ".tmp",
                PosixFilePermissions.asFileAttribute(
                        PosixFilePermissions.fromString("rw-------")));
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
                        path,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                throw new IOException(
                        "Filesystem does not support atomic Pi stage result replacement",
                        e);
            }
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    static void delete(Request request) throws IOException {
        Path path = resultPath(request, false);
        if (path != null) {
            Files.deleteIfExists(path);
        }
    }

    private static Path resultPath(Request request, boolean createDirectory)
            throws IOException {
        if (!createDirectory
                && attributesIfPresent(request.sessionDirectory()) == null) {
            return null;
        }
        Path sessions;
        try {
            sessions = request.sessionDirectory().toRealPath();
        } catch (SecurityException e) {
            throw new IOException(
                    "Pi session directory could not be inspected",
                    e);
        }
        Path directory = sessions.resolve(DIRECTORY);
        if (createDirectory) {
            Files.createDirectories(
                    directory,
                    PosixFilePermissions.asFileAttribute(
                            PosixFilePermissions.fromString("rwx------")));
        }
        BasicFileAttributes attributes = attributesIfPresent(directory);
        if (attributes == null) {
            if (!createDirectory) {
                return null;
            }
            throw new IOException("Pi stage result directory disappeared");
        }
        if (attributes.isSymbolicLink() || !attributes.isDirectory()) {
            throw new UntrustedResultException(
                    "Pi stage result directory is invalid");
        }
        String name = request.runId()
                      + '-' + request.stage().name().toLowerCase(Locale.ROOT)
                      + '-' + request.inputDigest().substring("sha256:".length())
                      + '-' + request.attempt() + ".json";
        return directory.resolve(name);
    }

    private static BasicFileAttributes attributesIfPresent(Path path)
            throws IOException {
        return PiWorker.attributesIfPresent(
                path, "Pi stage result path could not be inspected");
    }

    private static void verifyEvidence(
            Request request, Marker marker)
            throws IOException {
        Result result = marker.result();
        CommandRun evidence = result.evidence();
        Path workingDirectory = realDirectory(
                request.workingDirectory(), "Pi working directory");
        Path evidenceDirectory = realDirectory(
                request.evidenceDirectory(), "Pi evidence directory");
        Path historicalExecutable = normalizedAbsolute(
                evidence.executable(), "Pi evidence executable");
        if (!historicalExecutable.toString().equals(evidence.executable())
                || !Path.of(evidence.workingDirectory()).equals(workingDirectory)
                || !evidence.inputDigests().equals(List.of(request.inputDigest()))
                || !evidence.version().equals(result.version())) {
            throw new UntrustedResultException(
                    "Pi stage result evidence does not match its request");
        }

        resolveLog(
                evidenceDirectory,
                evidence.stdoutLog(),
                marker.stdoutSize());
        resolveLog(
                evidenceDirectory,
                evidence.stderrLog(),
                marker.stderrSize());
    }

    private static Path normalizedAbsolute(String supplied, String label)
            throws IOException {
        try {
            Path path = Path.of(Objects.requireNonNull(supplied, label));
            if (!path.isAbsolute() || !path.normalize().equals(path)) {
                throw new UntrustedResultException(label + " is invalid");
            }
            return path;
        } catch (RuntimeException e) {
            throw new UntrustedResultException(label + " is invalid", e);
        }
    }

    private static Path realDirectory(Path supplied, String label)
            throws IOException {
        Path normalized = Objects.requireNonNull(supplied, label)
                .toAbsolutePath()
                .normalize();
        BasicFileAttributes attributes = attributesIfPresent(normalized);
        if (attributes == null
                || attributes.isSymbolicLink()
                || !attributes.isDirectory()) {
            throw new UntrustedResultException(
                    label + " must be a real directory");
        }
        try {
            return normalized.toRealPath();
        } catch (NoSuchFileException e) {
            throw new UntrustedResultException(
                    label + " must be a real directory",
                    e);
        }
    }

    private static Path resolveLog(
            Path evidenceDirectory, String supplied, long expectedSize)
            throws IOException {
        Path log = normalizedAbsolute(supplied, "Pi stage result log");
        if (!log.toString().equals(supplied)
                || !log.startsWith(evidenceDirectory)) {
            throw new UntrustedResultException(
                    "Pi stage result log escaped its evidence directory: " + log);
        }
        if (expectedSize < 0
                || expectedSize > PiWorker.MAX_LOG_BYTES
                || !Files.isRegularFile(log)) {
            throw new UntrustedResultException(
                    "Pi stage result log is missing or invalid: " + log);
        }
        final long actualSize;
        try {
            actualSize = Files.size(log);
        } catch (NoSuchFileException e) {
            throw new UntrustedResultException(
                    "Pi stage result log is missing or invalid: " + log,
                    e);
        }
        if (actualSize != expectedSize) {
            throw new UntrustedResultException(
                    "Pi stage result log does not match its recorded size: " + log);
        }
        return log;
    }

    private record Marker(
            int schemaVersion,
            String runId,
            Stage stage,
            int attempt,
            String inputDigest,
            long stdoutSize,
            long stderrSize,
            Result result) {

        private boolean matches(Request request) {
            return Objects.equals(runId, request.runId())
                    && stage == request.stage()
                    && attempt == request.attempt()
                    && Objects.equals(inputDigest, request.inputDigest())
                    && result != null;
        }
    }
}
