package io.github.luigidemasi.camelkit.ship.controller;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
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

    static void deleteFreshRunRoot(Path stateRoot, ShipRunId runId) throws IOException {
        Path runRoot = requireRunRoot(stateRoot, runId);
        if (!Files.exists(runRoot, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        BasicFileAttributes rootAttributes = Files.readAttributes(
                runRoot, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!rootAttributes.isDirectory() || rootAttributes.isSymbolicLink()) {
            throw new IOException("Fresh Ship run root changed before cleanup");
        }
        Files.walkFileTree(runRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(
                    Path directory, BasicFileAttributes attributes)
                    throws IOException {
                if (attributes.isSymbolicLink()) {
                    throw new IOException("Fresh Ship run contains a symbolic directory");
                }
                Files.setPosixFilePermissions(
                        directory, PosixFilePermissions.fromString("rwx------"));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes)
                    throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException failure)
                    throws IOException {
                if (failure != null) {
                    throw failure;
                }
                Files.delete(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
