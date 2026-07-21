package io.github.luigidemasi.camelkit.ship.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@EnabledOnOs(OS.LINUX)
class ShipOperationLockTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void competingThreadCannotEnterTheSameRunLease() throws Exception {
        ShipRunId runId = ShipRunId.create();
        Path stateRoot = stateWithRuns(runId);

        try (ShipOperationLock.Lease outer
                = ShipOperationLock.acquireRun(stateRoot, runId.storageId())) {
            assertDoesNotThrow(() -> {
                try (ShipOperationLock.Lease nested
                        = ShipOperationLock.acquireRun(stateRoot, runId.storageId())) {
                    // Same-thread leases are deliberately reentrant.
                }
            });
            ShipControllerException failure = onAnotherThread(() -> assertThrows(
                    ShipControllerException.class,
                    () -> {
                        try (ShipOperationLock.Lease ignored
                                = ShipOperationLock.acquireRun(stateRoot, runId.storageId())) {
                            // A competing controller operation must not enter this scope.
                        }
                    }));
            assertEquals("operation-in-progress", failure.code());
        }
    }

    @Test
    void projectLeaseExcludesAnotherRunAndAcquisitionOrderIsEnforced() throws Exception {
        ShipRunId firstRun = ShipRunId.create();
        ShipRunId secondRun = ShipRunId.create();
        Path stateRoot = stateWithRuns(firstRun, secondRun);
        Path projectRoot = Files.createDirectory(temporaryDirectory.resolve("project"));

        try (ShipOperationLock.Lease first
                = ShipOperationLock.acquireRun(stateRoot, firstRun.storageId());
             ShipOperationLock.Lease project
                     = ShipOperationLock.acquireProject(stateRoot, projectRoot)) {
            ShipControllerException failure = onAnotherThread(() -> {
                try (ShipOperationLock.Lease second
                        = ShipOperationLock.acquireRun(stateRoot, secondRun.storageId())) {
                    return assertThrows(
                            ShipControllerException.class,
                            () -> {
                                try (ShipOperationLock.Lease ignored
                                        = ShipOperationLock.acquireProject(stateRoot, projectRoot)) {
                                    // A second run cannot publish to the same project concurrently.
                                }
                            });
                }
            });
            assertEquals("operation-in-progress", failure.code());
        }

        assertThrows(
                IllegalStateException.class,
                () -> ShipOperationLock.acquireProject(stateRoot, projectRoot));
    }

    @Test
    void runMustPrecedeProjectAndProjectMustCloseFirst() throws Exception {
        ShipRunId firstRun = ShipRunId.create();
        ShipRunId secondRun = ShipRunId.create();
        Path stateRoot = stateWithRuns(firstRun, secondRun);
        Path projectRoot = Files.createDirectory(temporaryDirectory.resolve("ordered-project"));
        ShipOperationLock.Lease run = ShipOperationLock.acquireRun(stateRoot, firstRun.storageId());
        ShipOperationLock.Lease project = ShipOperationLock.acquireProject(stateRoot, projectRoot);
        try {
            assertThrows(
                    IllegalStateException.class,
                    () -> ShipOperationLock.acquireRun(stateRoot, secondRun.storageId()));
            assertThrows(IOException.class, run::close);
        } finally {
            project.close();
            run.close();
        }
    }

    @Test
    void hardLinkedLockAndSymbolicStateRootFailClosed() throws Exception {
        ShipRunId runId = ShipRunId.create();
        Path stateRoot = stateWithRuns(runId);
        try (ShipOperationLock.Lease ignored
                = ShipOperationLock.acquireRun(stateRoot, runId.storageId())) {
            // Materialize the lock file before creating an alias.
        }
        Path lock = stateRoot.resolve(runId.storageId()).resolve("operation.lock");
        try {
            Files.createLink(temporaryDirectory.resolve("operation-lock-alias"), lock);
        } catch (UnsupportedOperationException | IOException | SecurityException e) {
            Assumptions.abort("Hard links are not supported: " + e.getMessage());
        }
        IOException hardLink = assertThrows(
                IOException.class,
                () -> ShipOperationLock.acquireRun(stateRoot, runId.storageId()));
        assertEquals("Ship operation lock must have exactly one hard link", hardLink.getMessage());

        Path target = privateDirectory(temporaryDirectory.resolve("linked-target"));
        privateDirectory(target.resolve(runId.storageId()));
        Path linkedState = temporaryDirectory.resolve("linked-state");
        try {
            Files.createSymbolicLink(linkedState, target);
        } catch (UnsupportedOperationException | IOException | SecurityException e) {
            Assumptions.abort("Symbolic links are not supported: " + e.getMessage());
        }
        assertThrows(
                IOException.class,
                () -> ShipOperationLock.acquireRun(linkedState, runId.storageId()));
    }

    @Test
    void invalidRunIdFailsBeforeFilesystemMutation() throws Exception {
        Path stateRoot = privateDirectory(temporaryDirectory.resolve("invalid-state"));

        ShipControllerException failure = assertThrows(
                ShipControllerException.class,
                () -> ShipOperationLock.acquireRun(stateRoot, "../../outside"));

        assertEquals("run-id-invalid", failure.code());
        try (var entries = Files.list(stateRoot)) {
            assertEquals(0, entries.count());
        }
    }

    private Path stateWithRuns(ShipRunId... runIds) throws IOException {
        Path stateRoot = privateDirectory(temporaryDirectory.resolve("state"));
        for (ShipRunId runId : runIds) {
            privateDirectory(stateRoot.resolve(runId.storageId()));
        }
        return stateRoot;
    }

    private static Path privateDirectory(Path path) throws IOException {
        return Files.createDirectory(
                path,
                PosixFilePermissions.asFileAttribute(
                        PosixFilePermissions.fromString("rwx------")));
    }

    private static <T> T onAnotherThread(Callable<T> action) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            return executor.submit(action).get(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }
}
