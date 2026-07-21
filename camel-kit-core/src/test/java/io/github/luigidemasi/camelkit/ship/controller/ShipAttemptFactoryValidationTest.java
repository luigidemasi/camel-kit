package io.github.luigidemasi.camelkit.ship.controller;

import java.nio.file.Path;
import java.util.List;

import io.github.luigidemasi.camelkit.ship.protocol.ShipStage;
import io.github.luigidemasi.camelkit.ship.protocol.StageCapability.Operation;
import io.github.luigidemasi.camelkit.ship.protocol.StageCapability.RepositoryAccess;
import io.github.luigidemasi.camelkit.ship.protocol.StageRequest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class ShipAttemptFactoryValidationTest {

    private static final String RUN_ID = "ship-" + "1".repeat(32);
    private static final String DIGEST = "sha256:" + "2".repeat(64);

    @TempDir
    Path temporaryDirectory;

    @Test
    void rejectsReferencesOutsideThePackagedSchemaGrammar() {
        ShipAttemptFactory factory = new ShipAttemptFactory();

        assertThrows(IllegalArgumentException.class, () -> factory.create(
                RUN_ID,
                ShipStage.DISCOVERY,
                1,
                DIGEST,
                inputs(List.of()),
                "workers/discovery.md\nignored",
                temporaryDirectory.resolve("output").toString(),
                null,
                null));
        assertThrows(IllegalArgumentException.class,
                () -> inputs(List.of("evidence" + (char) 0x7f + ".json")));
    }

    @Test
    void rejectsNoncanonicalFailureCodesAndDuplicateEvidenceReferences() {
        ShipAttemptFactory factory = new ShipAttemptFactory();

        assertThrows(IllegalArgumentException.class, () -> factory.create(
                RUN_ID,
                ShipStage.VALIDATE,
                2,
                DIGEST,
                inputs(List.of()),
                "workers/validate.md",
                temporaryDirectory.resolve("output").toString(),
                "Build Failed",
                "The build failed"));
        assertThrows(IllegalArgumentException.class, () -> inputs(List.of("evidence.json", "evidence.json")));
    }

    @Test
    void createsTheCapabilityCategoryForEveryStage() {
        for (ShipStage stage : ShipStage.values()) {
            StageRequest request = create(stage, 1, null, null);
            boolean writes = stage != ShipStage.DISCOVERY && stage != ShipStage.REVIEW;
            RepositoryAccess expected = switch (stage) {
                case DISCOVERY, REVIEW -> RepositoryAccess.READ_ONLY;
                case EXECUTE -> RepositoryAccess.DECLARED_EXECUTION_PATHS;
                default -> RepositoryAccess.READ_WITH_STAGED_OUTPUT;
            };

            assertEquals(expected, request.capability().repositoryAccess());
            assertTrue(request.capability().readRoots().isEmpty());
            assertEquals(writes ? List.of(request.outputDirectory()) : List.of(), request.capability().writeRoots());
            assertEquals(writes, request.capability().allowedOperations().contains(Operation.WRITE_STAGED_ARTIFACT));
            assertTrue(request.capability().allowedOperations().containsAll(
                    List.of(Operation.READ, Operation.SEARCH, Operation.RETURN_STRUCTURED_RESULT)));
            assertFalse(request.capability().mayLaunchChildren());
            assertFalse(request.capability().mayInvokeLaterStages());
        }
    }

    @Test
    void stableInputsProduceStableDigestsButFreshAttemptSecrets() {
        StageRequest first = create(ShipStage.PLAN, 2, "validation-failed", "Try again");
        StageRequest second = create(ShipStage.PLAN, 2, "validation-failed", "Try again");

        assertEquals(first.inputDigest(), second.inputDigest());
        assertEquals(first.idempotencyKey(), second.idempotencyKey());
        assertEquals(first.capability(), second.capability());
        assertNotEquals(first.attemptId(), second.attemptId());
        assertNotEquals(first.challenge(), second.challenge());
    }

    private StageRequest create(
            ShipStage stage, int attempt, String failureCode, String failureMessage) {
        return new ShipAttemptFactory().create(
                RUN_ID,
                stage,
                attempt,
                DIGEST,
                inputs(List.of("evidence.json")),
                "workers/" + stage.name().toLowerCase(java.util.Locale.ROOT) + ".md",
                temporaryDirectory.resolve(stage.name().toLowerCase(java.util.Locale.ROOT) + "-output")
                        .toAbsolutePath()
                        .normalize()
                        .toString(),
                failureCode,
                failureMessage);
    }

    private static ShipAttemptFactory.Inputs inputs(List<String> evidenceReferences) {
        return new ShipAttemptFactory.Inputs(
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
                evidenceReferences);
    }
}
