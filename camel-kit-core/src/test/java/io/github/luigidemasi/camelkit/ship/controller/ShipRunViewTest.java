package io.github.luigidemasi.camelkit.ship.controller;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import io.github.luigidemasi.camelkit.ship.protocol.Interaction;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.DiscoveryChallenge;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipRunViewTest {

    private static final String DIGEST = "sha256:" + "1".repeat(64);
    private static final String MAC = "hmac-sha256:" + "2".repeat(64);

    @TempDir
    Path temporaryDirectory;

    @Test
    void rejectsPendingInteractionFromAnotherRun() {
        ShipRun authority = new ShipRun(
                ShipRunId.create(),
                AuthorityHeadId.create(),
                ShipState.CREATED,
                0,
                ShipEventType.RUN_CREATED,
                AuthorityData.empty());
        DiscoveryChallenge foreign = new DiscoveryChallenge(
                Interaction.SCHEMA_VERSION,
                ShipRunId.create().toString(),
                1,
                "question-1",
                "open-item-1",
                "Choose one",
                List.of("one", "two"),
                "one",
                "a".repeat(43),
                MAC);

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> view(authority, ShipInteractionBundle.Exchange.discovery(1, foreign)));

        assertTrue(failure.getMessage().contains("different Ship run"));
    }

    private ShipRunView view(ShipRun authority, ShipInteractionBundle.Exchange pending) {
        return new ShipRunView(
                authority,
                DIGEST,
                temporaryDirectory.toAbsolutePath().normalize(),
                "native",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                pending,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                Map.of(),
                null,
                null,
                null,
                null,
                null,
                null);
    }
}
