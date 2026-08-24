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

        assertEquals(
                "first",
                PiWorkerResultStore.read(first)
                        .orElseThrow()
                        .assistantText());
        assertEquals(
                "second",
                PiWorkerResultStore.read(second)
                        .orElseThrow()
                        .assistantText());
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

        IOException indeterminate = assertThrows(
                IOException.class,
                () -> PiWorkerResultStore.read(request));
        assertFalse(indeterminate instanceof PiWorker.UntrustedResultException);
        assertThrows(
                IOException.class,
                () -> PiWorkerResultStore.delete(request));

        // Companion coverage: dangling final symlinks fail in toRealPath(),
        // while the loop above pins checked indeterminate-path probing.
        Path dangling = directory.resolve("dangling");
        Files.createSymbolicLink(dangling, Path.of("missing"));
        PiWorker.Request danglingRequest = request(RUN_A, dangling);

        IOException danglingFailure = assertThrows(
                IOException.class,
                () -> PiWorkerResultStore.read(danglingRequest));
        assertFalse(danglingFailure instanceof PiWorker.UntrustedResultException);
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
        assertTrue(assertThrows(PiWorker.UntrustedResultException.class,
                () -> PiWorkerResultStore.read(request))
                .getMessage().contains("does not match"));

        Files.writeString(marker, encoded.replace(
                "\"runId\" : \"" + RUN_A + "\"", "\"runId\" : null"));
        assertTrue(assertThrows(IOException.class,
                () -> PiWorkerResultStore.read(request))
                .getMessage().contains("does not match"));

        Files.writeString(marker, encoded.replace(
                "\"inputDigest\" : \"" + DIGEST + "\"", "\"inputDigest\" : null"));
        assertTrue(assertThrows(IOException.class,
                () -> PiWorkerResultStore.read(request))
                .getMessage().contains("does not match"));

        Files.writeString(marker, encoded.replace("\"schemaVersion\" : 3", "\"schemaVersion\" : 4"));
        assertTrue(assertThrows(IOException.class,
                () -> PiWorkerResultStore.read(request))
                .getMessage().contains("schema version"));
    }

    @Test
    void rejectsEmptyMalformedAndOversizedMarkers() throws Exception {
        Path sessions = Files.createDirectory(directory.resolve("sessions"));
        PiWorker.Request request = request(RUN_A, sessions);
        PiWorkerResultStore.write(request, result("done"));
        Path marker = marker(sessions);

        Files.write(marker, new byte[0]);
        assertTrue(assertThrows(IOException.class,
                () -> PiWorkerResultStore.read(request))
                .getMessage().contains("size"));

        Files.writeString(marker, "{");
        assertTrue(assertThrows(IOException.class,
                () -> PiWorkerResultStore.read(request))
                .getMessage().contains("malformed"));

        try (RandomAccessFile file = new RandomAccessFile(marker.toFile(), "rw")) {
            file.setLength(20L * 1024 * 1024 + 1);
        }
        assertTrue(assertThrows(IOException.class,
                () -> PiWorkerResultStore.read(request))
                .getMessage().contains("size"));
    }

    @Test
    void rejectsDeletedAndTruncatedEvidenceLogs() throws Exception {
        Path sessions = Files.createDirectory(directory.resolve("sessions"));
        PiWorker.Request request = request(RUN_A, sessions);

        PiWorker.Result deleted = result("deleted");
        PiWorkerResultStore.write(request, deleted);
        Files.delete(Path.of(deleted.evidence().stdoutLog()));
        assertTrue(assertThrows(
                IOException.class,
                () -> PiWorkerResultStore.read(request))
                .getMessage().contains("missing or invalid"));

        PiWorker.Result truncated = result("truncated");
        PiWorkerResultStore.write(request, truncated);
        Files.writeString(
                Path.of(truncated.evidence().stderrLog()),
                "x");
        assertTrue(assertThrows(
                IOException.class,
                () -> PiWorkerResultStore.read(request))
                .getMessage().contains("recorded size"));
    }

    @Test
    void rejectsEvidenceLogsOutsideTheirDirectory() throws Exception {
        Path sessions = Files.createDirectory(directory.resolve("sessions"));
        PiWorker.Request request = request(RUN_A, sessions);
        PiWorker.Result original = result("escaped");
        Path outside = Files.writeString(
                directory.resolve("outside.stdout.log"),
                "outside");
        PiWorker.Result escaped = withEvidence(
                original,
                original.evidence().executable(),
                original.evidence().workingDirectory(),
                original.evidence().inputDigests(),
                outside,
                ShipDigest.sha256(Files.readAllBytes(outside)),
                Path.of(original.evidence().stderrLog()),
                original.evidence().stderrDigest(),
                original.evidence().version());

        PiWorkerResultStore.write(request, escaped);

        assertTrue(assertThrows(
                IOException.class,
                () -> PiWorkerResultStore.read(request))
                .getMessage().contains("escaped"));
    }

    @Test
    void validatesCommandEvidenceAgainstItsHistoricalRequest() throws Exception {
        Path sessions = Files.createDirectory(directory.resolve("sessions"));
        PiWorker.Request request = request(RUN_A, sessions);
        PiWorker.Result original = result("mismatch");
        Path stdout = Path.of(original.evidence().stdoutLog());
        Path stderr = Path.of(original.evidence().stderrLog());

        Path otherWorking = Files.createDirectory(
                directory.resolve("other-working"));
        PiWorkerResultStore.write(
                request,
                withEvidence(
                        original,
                        original.evidence().executable(),
                        otherWorking.toString(),
                        original.evidence().inputDigests(),
                        stdout,
                        original.evidence().stdoutDigest(),
                        stderr,
                        original.evidence().stderrDigest(),
                        original.evidence().version()));
        assertTrue(assertThrows(
                IOException.class,
                () -> PiWorkerResultStore.read(request))
                .getMessage().contains("does not match its request"));

        String otherDigest = ShipDigest.sha256(
                "other input".getBytes(
                        java.nio.charset.StandardCharsets.UTF_8));
        PiWorkerResultStore.write(
                request,
                withEvidence(
                        original,
                        original.evidence().executable(),
                        original.evidence().workingDirectory(),
                        List.of(otherDigest),
                        stdout,
                        original.evidence().stdoutDigest(),
                        stderr,
                        original.evidence().stderrDigest(),
                        original.evidence().version()));
        assertTrue(assertThrows(
                IOException.class,
                () -> PiWorkerResultStore.read(request))
                .getMessage().contains("does not match its request"));

        String historicalExecutable = directory.resolve("retired-pi")
                .toAbsolutePath()
                .normalize()
                .toString();
        PiWorkerResultStore.write(
                request,
                withEvidence(
                        original,
                        historicalExecutable,
                        original.evidence().workingDirectory(),
                        original.evidence().inputDigests(),
                        stdout,
                        original.evidence().stdoutDigest(),
                        stderr,
                        original.evidence().stderrDigest(),
                        original.evidence().version()));
        assertEquals(
                historicalExecutable,
                PiWorkerResultStore.read(request)
                        .orElseThrow()
                        .evidence()
                        .executable());

        PiWorkerResultStore.write(request, original);
        Path marker = marker(sessions);
        String encoded = Files.readString(marker);
        String version = "\"version\" : \"0.83.0\"";
        int evidenceVersion = encoded.indexOf(
                version, encoded.indexOf(version) + version.length());
        assertTrue(evidenceVersion >= 0);
        Files.writeString(
                marker,
                encoded.substring(0, evidenceVersion)
                        + "\"version\" : \"0.81.1\""
                        + encoded.substring(evidenceVersion + version.length()));
        assertTrue(assertThrows(
                IOException.class,
                () -> PiWorkerResultStore.read(request))
                .getMessage().contains("malformed"));
    }

    private PiWorker.Request request(String runId, Path sessions)
            throws IOException {
        return new PiWorker.Request(
                runId,
                ShipRun.Stage.DISCOVERY,
                1,
                Files.createDirectories(
                        directory.resolve("working")),
                sessions,
                Files.createDirectories(
                        directory.resolve("evidence")),
                DIGEST,
                true,
                "prompt");
    }

    private PiWorker.Result result(String text) throws IOException {
        String timestamp = "2026-07-28T08:00:00Z";
        Path evidence = Files.createDirectories(
                directory.resolve("evidence"));
        Path stdout = Files.createTempFile(
                evidence, "pi-", ".stdout.log");
        Path stderr = Files.createTempFile(
                evidence, "pi-", ".stderr.log");
        byte[] stdoutBytes = "stdout".getBytes(
                java.nio.charset.StandardCharsets.UTF_8);
        byte[] stderrBytes = "stderr".getBytes(
                java.nio.charset.StandardCharsets.UTF_8);
        Files.write(stdout, stdoutBytes);
        Files.write(stderr, stderrBytes);
        CommandRun command = new CommandRun(
                executable().toString(),
                "0.83.0",
                List.of("--mode", "rpc"),
                directory.resolve("working")
                        .toAbsolutePath()
                        .toString(),
                List.of(DIGEST),
                true,
                false,
                false,
                0,
                timestamp,
                timestamp,
                stdout.toAbsolutePath().toString(),
                ShipDigest.sha256(stdoutBytes),
                stderr.toAbsolutePath().toString(),
                ShipDigest.sha256(stderrBytes));
        return new PiWorker.Result(
                PiWorker.Outcome.SUCCEEDED,
                ShipLocalStamp.Support.SUPPORTED,
                "0.83.0",
                null,
                new ShipLocalStamp.ToolVersion(
                        "node",
                        directory.resolve("node")
                                .toAbsolutePath()
                                .normalize()
                                .toString(),
                        "22.22.2",
                        ShipLocalStamp.Support.SUPPORTED,
                        null),
                text,
                null,
                command);
    }

    private static PiWorker.Result withEvidence(
            PiWorker.Result result,
            String executable,
            String workingDirectory,
            List<String> inputDigests,
            Path stdout,
            String stdoutDigest,
            Path stderr,
            String stderrDigest,
            String version) {
        CommandRun previous = result.evidence();
        CommandRun evidence = new CommandRun(
                executable,
                version,
                previous.redactedArguments(),
                workingDirectory,
                inputDigests,
                previous.launched(),
                previous.timedOut(),
                previous.outputLimited(),
                previous.exitCode(),
                previous.startedAt(),
                previous.endedAt(),
                stdout.toAbsolutePath().normalize().toString(),
                stdoutDigest,
                stderr.toAbsolutePath().normalize().toString(),
                stderrDigest);
        return new PiWorker.Result(
                result.outcome(),
                result.support(),
                version,
                result.warning(),
                result.node(),
                result.assistantText(),
                result.failure(),
                evidence);
    }

    private Path executable() {
        return directory.resolve("pi").toAbsolutePath().normalize();
    }

    private static Path marker(Path sessions) throws IOException {
        try (var files = Files.list(sessions.resolve(".camel-kit-results"))) {
            return files.findFirst().orElseThrow();
        }
    }
}
