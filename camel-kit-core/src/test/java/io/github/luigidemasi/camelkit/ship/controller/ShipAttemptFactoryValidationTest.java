package io.github.luigidemasi.camelkit.ship.controller;

import java.nio.file.Path;
import java.util.List;

import io.github.luigidemasi.camelkit.ship.protocol.ShipStage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertThrows;

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
