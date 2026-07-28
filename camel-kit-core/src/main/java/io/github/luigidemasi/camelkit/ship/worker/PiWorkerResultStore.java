package io.github.luigidemasi.camelkit.ship.worker;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Optional;

import io.github.luigidemasi.camelkit.ship.worker.PiWorker.Request;
import io.github.luigidemasi.camelkit.ship.worker.PiWorker.Result;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Atomic recovery marker for a completed Pi stage attempt. */
final class PiWorkerResultStore {

    private static final int SCHEMA_VERSION = 1;
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

    static Optional<Result> read(Request request) throws IOException {
        Path path = resultPath(request, false);
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return Optional.empty();
        }
        if (Files.isSymbolicLink(path)
                || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Pi stage result marker is invalid");
        }
        byte[] encoded;
        try (InputStream input = Files.newInputStream(
                path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            encoded = input.readNBytes(MAX_RESULT_BYTES + 1);
        }
        if (encoded.length == 0 || encoded.length > MAX_RESULT_BYTES) {
            throw new IOException("Pi stage result marker has an invalid size");
        }
        final Marker marker;
        try {
            marker = JSON.readValue(encoded, Marker.class);
        } catch (JsonProcessingException e) {
            throw new IOException("Pi stage result marker is malformed", e);
        }
        if (!marker.matches(request)) {
            throw new IOException("Pi stage result marker does not match its attempt");
        }
        return Optional.of(marker.result());
    }

    static void write(Request request, Result result) throws IOException {
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

    private static Path resultPath(Request request, boolean createDirectory)
            throws IOException {
        Path sessions = request.sessionDirectory().toRealPath();
        Path directory = sessions.resolve(DIRECTORY);
        if (createDirectory && !Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            Files.createDirectory(
                    directory,
                    PosixFilePermissions.asFileAttribute(
                            PosixFilePermissions.fromString("rwx------")));
        }
        if (Files.isSymbolicLink(directory)
                || (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)
                        && !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS))) {
            throw new IOException("Pi stage result directory is invalid");
        }
        String name = request.stage().name().toLowerCase(java.util.Locale.ROOT)
                      + '-' + request.inputDigest().substring("sha256:".length())
                      + '-' + request.attempt() + ".json";
        return directory.resolve(name);
    }

    private record Marker(
            int schemaVersion,
            String runId,
            io.github.luigidemasi.camelkit.ship.controller.ShipRun.Stage stage,
            int attempt,
            String inputDigest,
            Result result) {

        private boolean matches(Request request) {
            return schemaVersion == SCHEMA_VERSION
                    && runId.equals(request.runId())
                    && stage == request.stage()
                    && attempt == request.attempt()
                    && inputDigest.equals(request.inputDigest())
                    && result != null;
        }
    }
}
