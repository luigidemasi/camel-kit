package io.github.luigidemasi.camelkit.ship.catalog;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CatalogTargetTest {

    @Test
    void acceptsOnlyCompleteRuntimeSpecificTuples() {
        assertAll(
                () -> assertDoesNotThrow(() -> new CatalogTarget("main", "4.21.0", null, null)),
                () -> assertDoesNotThrow(
                        () -> new CatalogTarget("spring-boot", "4.21.0", "4.21.0", "4.1.0")),
                () -> assertDoesNotThrow(
                        () -> new CatalogTarget("quarkus", "4.18.2", "3.33.2", null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new CatalogTarget("main", "4.21.0", "4.21.0", null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new CatalogTarget("main", "4.21.0", null, "4.1.0")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new CatalogTarget("spring-boot", "4.21.0", null, "4.1.0")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new CatalogTarget("spring-boot", "4.21.0", "4.21.0", null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new CatalogTarget("quarkus", "4.18.2", null, null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new CatalogTarget("quarkus", "4.18.2", "3.33.2", "3.5.0")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new CatalogTarget("MAIN", "4.21.0", null, null)));
    }

    @Test
    void rejectsMutableUnsafeAndOverlongVersions() {
        String atLimit = "1" + "a".repeat(127);
        assertDoesNotThrow(() -> new CatalogTarget("main", atLimit, null, null));

        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new CatalogTarget("main", "4.21.0-SNAPSHOT", null, null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new CatalogTarget("main", "4.21.0-20260720.123456-1", null, null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new CatalogTarget("main", "LATEST", null, null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new CatalogTarget(null, "4.21.0", null, null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new CatalogTarget("main", "4..21", null, null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new CatalogTarget("main", "4.21.", null, null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new CatalogTarget("main", "[4.18,5)", null, null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new CatalogTarget("main", atLimit + 'a', null, null)));
    }
}
