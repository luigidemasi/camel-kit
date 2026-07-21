package io.github.luigidemasi.camelkit.ship.controller;

import java.nio.file.Path;
import java.util.Objects;

/** Resolves controller state outside the worker-visible project tree. */
public final class ShipControllerPaths {

    public static final String STATE_HOME_ENV = "CAMEL_KIT_SHIP_STATE_HOME";

    private ShipControllerPaths() {
    }

    public static Path defaultStateRoot() {
        String explicit = System.getenv(STATE_HOME_ENV);
        if (explicit != null && !explicit.isBlank()) {
            return Path.of(explicit).toAbsolutePath().normalize();
        }
        String xdg = System.getenv("XDG_STATE_HOME");
        if (xdg != null && !xdg.isBlank()) {
            return Path.of(xdg).resolve("camel-kit/ship").toAbsolutePath().normalize();
        }
        String userHome = System.getProperty("user.home");
        if (userHome == null || userHome.isBlank()) {
            throw new ShipControllerException(
                    "state-home-unavailable",
                    "Set " + STATE_HOME_ENV
                                              + " because neither XDG_STATE_HOME nor user.home is available");
        }
        return Path.of(userHome, ".local", "state", "camel-kit", "ship")
                .toAbsolutePath()
                .normalize();
    }

    static Path requireRunRoot(Path stateRoot, String storageRunId) {
        return requireRunRoot(stateRoot, ShipRunId.fromStorageId(storageRunId));
    }

    static Path requireRunRoot(Path stateRoot, ShipRunId runId) {
        Path root = Objects.requireNonNull(stateRoot, "stateRoot").toAbsolutePath().normalize();
        Path run = root.resolve(Objects.requireNonNull(runId, "runId").storageId()).normalize();
        if (!root.equals(run.getParent())) {
            throw new IllegalArgumentException("Ship run directory must be a direct state-root child");
        }
        return run;
    }
}
