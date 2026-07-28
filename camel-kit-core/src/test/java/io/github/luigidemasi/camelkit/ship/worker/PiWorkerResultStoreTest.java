package io.github.luigidemasi.camelkit.ship.worker;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import io.github.luigidemasi.camelkit.ship.ShipDigest;
import io.github.luigidemasi.camelkit.ship.controller.ShipRun;
import io.github.luigidemasi.camelkit.ship.evidence.ShipLocalStamp;
import io.github.luigidemasi.camelkit.ship.evidence.ShipLocalStamp.CommandRun;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class PiWorkerResultStoreTest {

    private static final String RUN_A = "ship-0123456789abcdef0123456789abcdef";
    private static final String RUN_B = "ship-fedcba9876543210fedcba9876543210";
    private static final String DIGEST = ShipDigest.sha256("input".getBytes(java.nio.charset.StandardCharsets.UTF_8));

    @TempDir
    Path directory;

    @Test
    void keepsResultsForDifferentRunsInOneSessionDirectory() throws Exception {
        Path sessions = Files.createDirectory(directory.resolve("sessions"));
        PiWorker.Request first = request(RUN_A, sessions);
        PiWorker.Request second = request(RUN_B, sessions);

        PiWorkerResultStore.write(first, result("first"));
        PiWorkerResultStore.write(second, result("second"));

        assertEquals("first", PiWorkerResultStore.read(first).orElseThrow().assistantText());
        assertEquals("second", PiWorkerResultStore.read(second).orElseThrow().assistantText());
        try (var files = Files.list(sessions.resolve(".camel-kit-results"))) {
            assertEquals(2, files.count());
        }
    }

    @Test
    void returnsEmptyWhenTheSessionDirectoryDoesNotExist() throws Exception {
        assertTrue(PiWorkerResultStore.read(
                request(RUN_A, directory.resolve("missing"))).isEmpty());
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void doesNotTreatIndeterminatePathsAsMissing() throws Exception {
        Path loop = directory.resolve("loop");
        Files.createSymbolicLink(loop, Path.of("loop"));
        PiWorker.Request request = request(
                RUN_A,
                loop.resolve("sessions"));

        assertThrows(
                IOException.class,
                () -> PiWorkerResultStore.read(request));
        assertThrows(
                IOException.class,
                () -> PiWorkerResultStore.delete(request));

        Path dangling = directory.resolve("dangling");
        Files.createSymbolicLink(dangling, Path.of("missing"));
        PiWorker.Request danglingRequest = request(RUN_A, dangling);

        assertThrows(
                IOException.class,
                () -> PiWorkerResultStore.read(danglingRequest));
        assertThrows(
                IOException.class,
                () -> PiWorkerResultStore.delete(danglingRequest));
    }

    @Test
    void rejectsMismatchedAndUnsupportedMarkers() throws Exception {
        Path sessions = Files.createDirectory(directory.resolve("sessions"));
        PiWorker.Request request = request(RUN_A, sessions);
        PiWorkerResultStore.write(request, result("done"));
        Path marker = marker(sessions);
        String encoded = Files.readString(marker);

        Files.writeString(marker, encoded.replace(RUN_A, RUN_B));
        assertTrue(assertThrows(IOException.class,
                () -> PiWorkerResultStore.read(request)).getMessage().contains("does not match"));

        Files.writeString(marker, encoded.replace(
                "\"runId\" : \"" + RUN_A + "\"", "\"runId\" : null"));
        assertTrue(assertThrows(IOException.class,
                () -> PiWorkerResultStore.read(request)).getMessage().contains("does not match"));

        Files.writeString(marker, encoded.replace(
                "\"inputDigest\" : \"" + DIGEST + "\"", "\"inputDigest\" : null"));
        assertTrue(assertThrows(IOException.class,
                () -> PiWorkerResultStore.read(request)).getMessage().contains("does not match"));

        Files.writeString(marker, encoded.replace("\"schemaVersion\" : 1", "\"schemaVersion\" : 2"));
        assertTrue(assertThrows(IOException.class,
                () -> PiWorkerResultStore.read(request)).getMessage().contains("schema version"));
    }

    @Test
    void rejectsEmptyMalformedAndOversizedMarkers() throws Exception {
        Path sessions = Files.createDirectory(directory.resolve("sessions"));
        PiWorker.Request request = request(RUN_A, sessions);
        PiWorkerResultStore.write(request, result("done"));
        Path marker = marker(sessions);

        Files.write(marker, new byte[0]);
        assertTrue(assertThrows(IOException.class,
                () -> PiWorkerResultStore.read(request)).getMessage().contains("size"));

        Files.writeString(marker, "{");
        assertTrue(assertThrows(IOException.class,
                () -> PiWorkerResultStore.read(request)).getMessage().contains("malformed"));

        try (RandomAccessFile file = new RandomAccessFile(marker.toFile(), "rw")) {
            file.setLength(20L * 1024 * 1024 + 1);
        }
        assertTrue(assertThrows(IOException.class,
                () -> PiWorkerResultStore.read(request)).getMessage().contains("size"));
    }

    private PiWorker.Request request(String runId, Path sessions) {
        return new PiWorker.Request(
                runId,
                ShipRun.Stage.DISCOVERY,
                1,
                directory.toAbsolutePath(),
                sessions,
                directory.resolve("evidence"),
                DIGEST,
                true,
                "prompt");
    }

    private PiWorker.Result result(String text) {
        String timestamp = "2026-07-28T08:00:00Z";
        CommandRun command = new CommandRun(
                directory.resolve("pi").toAbsolutePath().toString(),
                "0.80.6",
                List.of("--mode", "rpc"),
                directory.toAbsolutePath().toString(),
                List.of(DIGEST),
                true,
                false,
                false,
                0,
                timestamp,
                timestamp,
                directory.resolve("stdout.log").toAbsolutePath().toString(),
                ShipDigest.sha256(new byte[0]),
                directory.resolve("stderr.log").toAbsolutePath().toString(),
                ShipDigest.sha256(new byte[0]));
        return new PiWorker.Result(
                PiWorker.Outcome.SUCCEEDED,
                ShipLocalStamp.Support.EXPERIMENTAL,
                "0.80.6",
                "experimental",
                text,
                null,
                command);
    }

    private static Path marker(Path sessions) throws IOException {
        try (var files = Files.list(sessions.resolve(".camel-kit-results"))) {
            return files.findFirst().orElseThrow();
        }
    }
}
