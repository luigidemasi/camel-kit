package io.github.luigidemasi.camelkit.ship.controller;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;

import io.github.luigidemasi.camelkit.ship.ShipDigest;
import io.github.luigidemasi.camelkit.ship.context.ShipContext;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

        Files.writeString(state, "{\"schemaVersion\":2}");
        assertCode("state-corrupt", () -> store.read(THIRD_RUN_ID));

        Files.writeString(state, "{\"schemaVersion\":1}");
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
    void rejectsProjectOverlapOnWriteAndRead() throws Exception {
        ShipRunStore store = store();
        ShipRun initial = run(RUN_ID);
        store.create(initial);
        Path runRoot = stateRoot().resolve(RUN_ID).toAbsolutePath().normalize();
        ShipRun overlapping = withProject(initial, runRoot);

        try (ShipRunStore.LockedRun locked = store.lock(RUN_ID)) {
            assertCode("state-invalid", () -> locked.write(overlapping));
        }
        assertEquals(initial, store.read(RUN_ID));

        Path state = runRoot.resolve("state.json");
        ObjectMapper json = new ObjectMapper();
        ObjectNode document = (ObjectNode) json.readTree(state.toFile());
        document.put("projectDirectory", runRoot.toString());
        Files.write(state, json.writeValueAsBytes(document));
        assertCode("state-corrupt", () -> store.read(RUN_ID));
    }

    @Test
    void rejectsAttemptedExecuteDowngradedToAnImportWithoutProvenance() throws Exception {
        ShipRunStore store = store();
        ShipRun completed = completedRun(RUN_ID);
        store.create(completed);
        Path state = stateRoot().resolve(RUN_ID).resolve("state.json");
        ObjectMapper json = new ObjectMapper();
        ObjectNode document = (ObjectNode) json.readTree(state.toFile());
        ObjectNode execute = (ObjectNode) document.withArray("stages")
                .get(ShipRun.Stage.EXECUTE.ordinal());
        String reportDigest = ShipDigest.sha256(
                "import-shaped-report".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        document.put("pipelineId", "149-import");
        execute.put("attempts", 0);
        execute.put("outputDigest", reportDigest);
        execute.putArray("artifacts")
                .addObject()
                .put(
                        "path",
                        Path.of(completed.projectDirectory())
                                .resolve("docs/camel-kit/149-import/execution-report.md")
                                .toString())
                .put("digest", reportDigest);
        execute.withArray("artifacts")
                .addObject()
                .put("path", completed.projectDirectory())
                .put(
                        "digest",
                        ShipDigest.sha256(
                                "import-shaped-project"
                                        .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        Files.write(state, json.writeValueAsBytes(document));

        assertCode("state-corrupt", () -> store.read(RUN_ID));
    }

    @Test
    void preservesCanonicalImportedExecuteEvidence() throws Exception {
        Path project = Files.createDirectory(temporaryDirectory.resolve("project"));
        Files.createDirectories(project.resolve(".camel-kit"));
        Files.writeString(
                project.resolve(".camel-kit/pipeline.json"),
                "{\"mode\":\"manual\",\"activePipeline\":\"149-import\"}");
        Path documents = Files.createDirectories(project.resolve("docs/camel-kit/149-import"));
        Files.writeString(documents.resolve("design-spec.md"), "design");
        Files.writeString(documents.resolve("implementation-plan.md"), "plan");
        Files.writeString(documents.resolve("execution-report.md"), "execution");

        ShipRun imported = new ShipController(stateRoot()).startFrom(
                project,
                ShipRun.Stage.VALIDATE,
                ShipRun.Oversight.NEVER,
                List.of());

        assertEquals(0, imported.stage(ShipRun.Stage.EXECUTE).attempts());
        assertEquals(imported, store().read(imported.id()));
    }

    @Test
    void rejectsNonCanonicalPartialImportEvidence() throws Exception {
        Path project = Files.createDirectory(temporaryDirectory.resolve("project"));
        Files.createDirectories(project.resolve(".camel-kit"));
        Files.writeString(
                project.resolve(".camel-kit/pipeline.json"),
                "{\"mode\":\"manual\",\"activePipeline\":\"149-import\"}");
        Path documents = Files.createDirectories(project.resolve("docs/camel-kit/149-import"));
        Files.writeString(documents.resolve("design-spec.md"), "design");
        Files.writeString(documents.resolve("implementation-plan.md"), "plan");
        ShipRun imported = new ShipController(stateRoot()).startFrom(
                project,
                ShipRun.Stage.EXECUTE,
                ShipRun.Oversight.NEVER,
                List.of());
        assertEquals(imported, store().read(imported.id()));

        Path state = stateRoot().resolve(imported.id()).resolve("state.json");
        byte[] valid = Files.readAllBytes(state);
        ObjectMapper json = new ObjectMapper();
        ObjectNode document = (ObjectNode) json.readTree(valid);
        ObjectNode design = stage(document, ShipRun.Stage.DESIGN);
        String forged = ShipDigest.sha256(
                "forged".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        design.put("outputDigest", forged);
        ObjectNode artifact = (ObjectNode) design.withArray("artifacts").get(0);
        artifact.put("path", project.resolve("forged.md").toString());
        artifact.put("digest", forged);
        Files.write(state, json.writeValueAsBytes(document));
        assertCode("state-corrupt", () -> store().read(imported.id()));

        document = (ObjectNode) json.readTree(valid);
        stage(document, ShipRun.Stage.DISCOVERY).put("attempts", 1);
        Files.write(state, json.writeValueAsBytes(document));
        assertCode("state-corrupt", () -> store().read(imported.id()));
    }

    @Test
    void rejectsNullCoercedNumericEnumAndUnboundedAttempts() throws Exception {
        ShipRunStore store = store();
        store.create(run(RUN_ID));
        Path state = stateRoot().resolve(RUN_ID).resolve("state.json");
        byte[] valid = Files.readAllBytes(state);
        ObjectMapper json = new ObjectMapper();

        ObjectNode document = (ObjectNode) json.readTree(valid);
        stage(document, ShipRun.Stage.DISCOVERY).putNull("attempts");
        Files.write(state, json.writeValueAsBytes(document));
        assertCode("state-corrupt", () -> store.read(RUN_ID));

        document = (ObjectNode) json.readTree(valid);
        stage(document, ShipRun.Stage.DISCOVERY).put("attempts", "0");
        Files.write(state, json.writeValueAsBytes(document));
        assertCode("state-corrupt", () -> store.read(RUN_ID));

        document = (ObjectNode) json.readTree(valid);
        stage(document, ShipRun.Stage.DISCOVERY).put("attempts", 0.0);
        Files.write(state, json.writeValueAsBytes(document));
        assertCode("state-corrupt", () -> store.read(RUN_ID));

        document = (ObjectNode) json.readTree(valid);
        stage(document, ShipRun.Stage.DISCOVERY).put("status", 0);
        Files.write(state, json.writeValueAsBytes(document));
        assertCode("state-corrupt", () -> store.read(RUN_ID));

        document = (ObjectNode) json.readTree(valid);
        stage(document, ShipRun.Stage.DISCOVERY).put("attempts", Integer.MAX_VALUE);
        Files.write(state, json.writeValueAsBytes(document));
        assertCode("state-corrupt", () -> store.read(RUN_ID));
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

    private ShipRun completedRun(String runId) {
        List<ShipRun.StageRecord> stages = new ArrayList<>(ShipRun.pendingStages());
        ShipContext context = ShipContext.none();
        for (ShipRun.Stage stage : ShipRun.Stage.values()) {
            String input = ShipRun.inputDigest(context, stages, stage);
            List<ShipRun.ArtifactRef> artifacts = List.of();
            String output = ShipDigest.sha256(stage.name().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            if (stage == ShipRun.Stage.EXECUTE) {
                ShipRun.ArtifactRef root = new ShipRun.ArtifactRef(
                        stateRoot().resolve(runId).resolve("workspace/candidate")
                                .toAbsolutePath().normalize().toString(),
                        ShipDigest.sha256("workspace".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                artifacts = List.of(root);
                output = ShipRun.executeOutputDigest(root);
            }
            stages.set(
                    stage.ordinal(),
                    new ShipRun.StageRecord(
                            stage,
                            ShipRun.StageStatus.COMPLETED,
                            1,
                            input,
                            output,
                            artifacts));
        }
        return new ShipRun(
                ShipRun.SCHEMA_VERSION,
                runId,
                temporaryDirectory.resolve("project").toAbsolutePath().normalize().toString(),
                null,
                ShipRun.Oversight.NEVER,
                ShipRun.RunStatus.COMPLETED,
                ShipRun.Stage.VALIDATE,
                context,
                stages,
                CREATED_AT,
                UPDATED_AT,
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

    private static ShipRun withProject(ShipRun run, Path project) {
        return new ShipRun(
                run.schemaVersion(),
                run.id(),
                project.toString(),
                run.pipelineId(),
                run.oversight(),
                run.status(),
                run.currentStage(),
                run.context(),
                run.stages(),
                run.createdAt(),
                run.updatedAt(),
                run.message());
    }

    private static ObjectNode stage(ObjectNode document, ShipRun.Stage stage) {
        return (ObjectNode) document.withArray("stages").get(stage.ordinal());
    }

    private static void assertCode(String code, Executable action) {
        ShipRunStore.StoreException failure
                = assertThrows(ShipRunStore.StoreException.class, action);
        assertEquals(code, failure.code());
    }
}
