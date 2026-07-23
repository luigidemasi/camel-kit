package io.github.luigidemasi.camelkit.ship.controller;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import io.github.luigidemasi.camelkit.ship.context.ShipContext;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipRunStoreTest {

    private static final String RUN_ID = "ship-00000000000000000000000000000001";
    private static final String OTHER_RUN_ID = "ship-00000000000000000000000000000002";
    private static final String THIRD_RUN_ID = "ship-00000000000000000000000000000003";
    private static final String CREATED_AT = "2026-07-23T12:00:00Z";
    private static final String UPDATED_AT = "2026-07-23T12:01:00Z";

    @TempDir
    Path temporaryDirectory;

    @Test
    void createsReadsAndAtomicallyReplacesRunState() throws Exception {
        ShipRunStore store = store();
        ShipRun initial = run(RUN_ID);
        store.create(initial);

        assertEquals(initial, store.read(RUN_ID));
        ShipRun updated = updated(initial);
        try (ShipRunStore.LockedRun locked = store.lock(RUN_ID)) {
            assertEquals(initial, locked.read());
            assertCode("state-id-mismatch", () -> locked.write(run(OTHER_RUN_ID)));
            locked.write(updated);
        }

        assertEquals(updated, store.read(RUN_ID));
        Path runRoot = stateRoot().resolve(RUN_ID);
        assertTrue(Files.isRegularFile(runRoot.resolve("state.json")));
        assertTrue(Files.isRegularFile(runRoot.resolve("run.lock")));
        try (var files = Files.list(runRoot)) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().endsWith(".tmp")));
        }
    }

    @Test
    void keepsCommittedStateWhenAnInterruptedTemporaryFileRemains() throws Exception {
        ShipRunStore store = store();
        ShipRun initial = run(RUN_ID);
        store.create(initial);
        Path abandoned = Files.writeString(
                stateRoot().resolve(RUN_ID).resolve(".state-interrupted.tmp"), "{\"schemaVersion\":");

        assertEquals(initial, store.read(RUN_ID));
        ShipRun updated = updated(initial);
        try (ShipRunStore.LockedRun locked = store.lock(RUN_ID)) {
            locked.read();
            locked.write(updated);
        }

        assertEquals(updated, store.read(RUN_ID));
        assertTrue(Files.exists(abandoned));
    }

    @Test
    void excludesConcurrentMutationAndReleasesTheLock() throws Exception {
        ShipRunStore store = store();
        store.create(run(RUN_ID));

        try (ShipRunStore.LockedRun ignored = store.lock(RUN_ID)) {
            assertCode("operation-in-progress", () -> {
                try (ShipRunStore.LockedRun competing = store.lock(RUN_ID)) {
                    // A competing operation must not enter.
                }
            });
        }

        try (ShipRunStore.LockedRun reacquired = store.lock(RUN_ID)) {
            assertEquals(RUN_ID, reacquired.read().id());
        }
        assertTrue(Files.exists(stateRoot().resolve(RUN_ID).resolve("run.lock")));
    }

    @Test
    void distinguishesMissingInvalidAndCorruptState() throws Exception {
        ShipRunStore store = store();
        assertCode("run-not-found", () -> store.read(RUN_ID));
        assertCode("run-id-invalid", () -> store.read("../../outside"));

        Files.createDirectories(stateRoot().resolve(OTHER_RUN_ID));
        assertCode("state-missing", () -> store.read(OTHER_RUN_ID));

        ShipRun initial = run(THIRD_RUN_ID);
        store.create(initial);
        Path state = stateRoot().resolve(THIRD_RUN_ID).resolve("state.json");
        String valid = Files.readString(state);

        Files.writeString(state, "{");
        assertCode("state-corrupt", () -> store.read(THIRD_RUN_ID));

        Files.writeString(state, "{\"schemaVersion\":1,\"schemaVersion\":1}");
        assertCode("state-corrupt", () -> store.read(THIRD_RUN_ID));

        Files.writeString(state, valid + "\ntrue");
        assertCode("state-corrupt", () -> store.read(THIRD_RUN_ID));

        int closingBrace = valid.lastIndexOf('}');
        Files.writeString(
                state, valid.substring(0, closingBrace) + ",\"unexpected\":true}");
        assertCode("state-corrupt", () -> store.read(THIRD_RUN_ID));

        Files.writeString(state, "{\"schemaVersion\":1}");
        assertCode("state-corrupt", () -> store.read(THIRD_RUN_ID));

        Files.writeString(state, "{\"schemaVersion\":2}");
        assertCode("state-version-unsupported", () -> store.read(THIRD_RUN_ID));

        Files.writeString(state, valid.replace(THIRD_RUN_ID, OTHER_RUN_ID));
        assertCode("state-corrupt", () -> store.read(THIRD_RUN_ID));
    }

    @Test
    void rejectsDuplicateCreationWithoutChangingCommittedState() throws Exception {
        ShipRunStore store = store();
        ShipRun initial = run(RUN_ID);
        store.create(initial);

        assertCode("run-already-exists", () -> store.create(updated(initial)));

        assertEquals(initial, store.read(RUN_ID));
    }

    @Test
    void createsPrivateStateFilesOnPosixFilesystems() throws Exception {
        Assumptions.assumeTrue(
                FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
        ShipRunStore store = store();
        store.create(run(RUN_ID));
        try (ShipRunStore.LockedRun ignored = store.lock(RUN_ID)) {
            // Create and retain the stable lock file.
        }

        Path runRoot = stateRoot().resolve(RUN_ID);
        assertEquals(
                PosixFilePermissions.fromString("rwx------"),
                Files.getPosixFilePermissions(stateRoot()));
        assertEquals(
                PosixFilePermissions.fromString("rwx------"),
                Files.getPosixFilePermissions(runRoot));
        assertEquals(
                PosixFilePermissions.fromString("rw-------"),
                Files.getPosixFilePermissions(runRoot.resolve("state.json")));
        assertEquals(
                PosixFilePermissions.fromString("rw-------"),
                Files.getPosixFilePermissions(runRoot.resolve("run.lock")));
    }

    private ShipRunStore store() {
        return new ShipRunStore(stateRoot());
    }

    private Path stateRoot() {
        return temporaryDirectory.resolve("state");
    }

    private ShipRun run(String runId) {
        return new ShipRun(
                ShipRun.SCHEMA_VERSION,
                runId,
                temporaryDirectory.resolve("project").toAbsolutePath().normalize().toString(),
                null,
                ShipRun.Oversight.SMART,
                ShipRun.RunStatus.PAUSED,
                ShipRun.Stage.DISCOVERY,
                ShipContext.none(),
                ShipRun.pendingStages(),
                CREATED_AT,
                CREATED_AT,
                null);
    }

    private static ShipRun updated(ShipRun run) {
        return new ShipRun(
                run.schemaVersion(),
                run.id(),
                run.projectDirectory(),
                run.pipelineId(),
                ShipRun.Oversight.NEVER,
                run.status(),
                run.currentStage(),
                run.context(),
                run.stages(),
                run.createdAt(),
                UPDATED_AT,
                run.message());
    }

    private static void assertCode(String code, Executable action) {
        ShipRunStore.StoreException failure
                = assertThrows(ShipRunStore.StoreException.class, action);
        assertEquals(code, failure.code());
    }
}
