package io.github.luigidemasi.camelkit.ship.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.luigidemasi.camelkit.ship.context.InitialContextRequest;
import io.github.luigidemasi.camelkit.ship.protocol.StageRequest;
import io.github.luigidemasi.camelkit.ship.protocol.StageResult;
import io.github.luigidemasi.camelkit.ship.security.StagedArtifactSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@EnabledOnOs(OS.LINUX)
class ShipProtectedWorkerBrokerTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void submitAlwaysClosesExclusiveCustodyWhenControllerRejectsTheResult() throws Exception {
        Path project = Files.createDirectory(temporaryDirectory.resolve("project"))
                .toAbsolutePath().normalize();
        Path state = temporaryDirectory.resolve("state").toAbsolutePath().normalize();
        ShipController controller = new ShipController(state);
        ShipRunView created = controller.start(new ShipController.PreparedRun(project, "test-adapter"));
        ShipRunView resolving = controller.beginContextResolution(
                created.runId(), created.eventDigest(), new InitialContextRequest.Text("requirements"));
        ShipRunView recorded = controller.continueContextResolution(
                created.runId(), resolving.eventDigest(), List.of());
        ShipRunView discovery = controller.startDiscovery(
                recorded.runId(), recorded.eventDigest());
        AtomicInteger closes = new AtomicInteger();
        ShipProtectedWorkerBroker broker = new ShipProtectedWorkerBroker(
                controller,
                request -> rejectedDiscoveryCustody(request, closes));

        assertThrows(
                IOException.class,
                () -> broker.submit(discovery.runId(), discovery.eventDigest(), null));

        assertEquals(1, closes.get());
        assertEquals(discovery, controller.status(discovery.runId()));
    }

    private static ShipProtectedWorkerBroker.Custody rejectedDiscoveryCustody(
            StageRequest request, AtomicInteger closes) {
        return new ShipProtectedWorkerBroker.Custody() {
            @Override
            public void requireExactAttempt(StageRequest expected) throws IOException {
                if (!request.equals(expected)) {
                    throw new IOException("unexpected attempt");
                }
            }

            @Override
            public byte[] readResultBytes() throws IOException {
                StageResult result = new StageResult(
                        StageResult.SCHEMA_VERSION,
                        request.runId(),
                        request.stage(),
                        request.attemptId(),
                        request.challenge(),
                        request.inputDigest(),
                        StageResult.Outcome.COMPLETED,
                        null,
                        null,
                        List.of(),
                        List.of(),
                        null,
                        null,
                        null);
                return ShipJson.mapper().writeValueAsBytes(result);
            }

            @Override
            public StagedArtifactSource.Session openArtifactSource() throws IOException {
                throw new IOException("Rejected analysis result must not open artifact custody");
            }

            @Override
            public void close() {
                closes.incrementAndGet();
            }
        };
    }
}
