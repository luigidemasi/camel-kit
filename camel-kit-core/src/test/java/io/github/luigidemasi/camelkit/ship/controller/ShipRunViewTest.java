package io.github.luigidemasi.camelkit.ship.controller;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import io.github.luigidemasi.camelkit.ship.protocol.Interaction;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.DiscoveryAnswer;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.DiscoveryChallenge;
import io.github.luigidemasi.camelkit.ship.protocol.ShipStage;
import io.github.luigidemasi.camelkit.ship.protocol.StageRequest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class ShipRunViewTest {

    private static final String DIGEST = "sha256:" + "1".repeat(64);
    private static final String MAC = "hmac-sha256:" + "2".repeat(64);

    @TempDir
    Path temporaryDirectory;

    @Test
    void rejectsPendingInteractionFromAnotherRun() {
        ShipRun authority = authority();
        DiscoveryChallenge foreign = challenge(ShipRunId.create().toString());

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> view(authority, null, ShipInteractionBundle.Exchange.discovery(1, foreign)));

        assertTrue(failure.getMessage().contains("different Ship run"));
    }

    @Test
    void exposesAValidImmutableRunProjection() {
        ShipRun authority = authority();

        ShipRunView view = view(authority, null, null);

        assertEquals(authority.id().toString(), view.runId());
        assertEquals(ShipState.CREATED, view.state());
        assertEquals(0, view.revision());
        assertFalse(view.terminal());
    }

    @Test
    void rejectsActiveRequestFromAnotherRun() {
        ShipRun authority = authority();
        StageRequest foreign = request(ShipRunId.create().toString());

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class, () -> view(authority, foreign, null));

        assertTrue(failure.getMessage().contains("different Ship run"));
    }

    @Test
    void rejectsAnAlreadyAnsweredPendingInteraction() {
        ShipRun authority = authority();
        DiscoveryChallenge challenge = challenge(authority.id().toString());
        DiscoveryAnswer answer = new DiscoveryAnswer(
                Interaction.SCHEMA_VERSION,
                challenge.runId(),
                challenge.ledgerRevision(),
                challenge.questionId(),
                challenge.openItemId(),
                challenge.nonce(),
                "one",
                "cli",
                Instant.EPOCH,
                MAC);
        ShipInteractionBundle.Exchange answered
                = ShipInteractionBundle.Exchange.discovery(1, challenge).answer(answer);

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class, () -> view(authority, null, answered));

        assertTrue(failure.getMessage().contains("already has a response"));
    }

    private ShipRunView view(
            ShipRun authority, StageRequest activeRequest, ShipInteractionBundle.Exchange pending) {
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
                activeRequest,
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

    private ShipRun authority() {
        return new ShipRun(
                ShipRunId.create(),
                AuthorityHeadId.create(),
                ShipState.CREATED,
                0,
                ShipEventType.RUN_CREATED,
                AuthorityData.empty());
    }

    private DiscoveryChallenge challenge(String runId) {
        return new DiscoveryChallenge(
                Interaction.SCHEMA_VERSION,
                runId,
                1,
                "question-1",
                "open-item-1",
                "Choose one",
                List.of("one", "two"),
                "one",
                "a".repeat(43),
                MAC);
    }

    private StageRequest request(String runId) {
        return new ShipAttemptFactory().create(
                runId,
                ShipStage.DISCOVERY,
                1,
                DIGEST,
                new ShipAttemptFactory.Inputs(
                        null, null, null, null, null, null, null, null, null, null, null, List.of()),
                "workers/discovery.md",
                temporaryDirectory.resolve("output").toAbsolutePath().normalize().toString(),
                null,
                null);
    }
}
