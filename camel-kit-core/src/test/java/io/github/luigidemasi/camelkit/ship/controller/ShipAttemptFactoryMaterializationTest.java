package io.github.luigidemasi.camelkit.ship.controller;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Map;

import io.github.luigidemasi.camelkit.ship.ShipDigest;
import io.github.luigidemasi.camelkit.ship.controller.ShipBlobStore.BlobReference;
import io.github.luigidemasi.camelkit.ship.protocol.ShipStage;
import io.github.luigidemasi.camelkit.ship.protocol.StageCapability;
import io.github.luigidemasi.camelkit.ship.protocol.StageCapability.Operation;
import io.github.luigidemasi.camelkit.ship.protocol.StageCapability.RepositoryAccess;
import io.github.luigidemasi.camelkit.ship.protocol.StageRequest;
import io.github.luigidemasi.camelkit.ship.security.ProjectEvidenceFiles;
import io.github.luigidemasi.camelkit.ship.security.ProjectSnapshot;

import com.networknt.schema.Schema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledOnOs(OS.LINUX)
class ShipAttemptFactoryMaterializationTest {

    private static final String RUN_ID = "ship-" + "1".repeat(32);
    private static final String EVENT_DIGEST = "sha256:" + "2".repeat(64);

    @TempDir
    Path temporaryDirectory;

    private Path project;
    private Path runRoot;
    private Path sourceDirectory;
    private ShipBlobStore blobs;
    private BlobReference baseline;
    private BlobReference sourceSnapshot;
    private BlobReference sourceManifest;
    private BlobReference context;
    private BlobReference ledger;
    private BlobReference catalog;
    private BlobReference design;
    private BlobReference plan;
    private BlobReference interaction;
    private BlobReference artifactManifest;
    private List<BlobReference> evidence;

    @BeforeEach
    void createProtectedRun() throws Exception {
        Path stateRoot = Files.createDirectory(
                temporaryDirectory.resolve("state"),
                PosixFilePermissions.asFileAttribute(
                        PosixFilePermissions.fromString("rwx------")));
        runRoot = Files.createDirectory(
                stateRoot.resolve(RUN_ID),
                PosixFilePermissions.asFileAttribute(
                        PosixFilePermissions.fromString("rwx------")));
        blobs = ShipBlobStore.create(stateRoot, RUN_ID);
        project = Files.createDirectory(temporaryDirectory.resolve("project"))
                .toAbsolutePath()
                .normalize();
        Files.writeString(project.resolve("route.camel.yaml"), "- from: {uri: timer:tick}\n");
        sourceDirectory = Files.createDirectory(
                runRoot.resolve("source"),
                PosixFilePermissions.asFileAttribute(
                        PosixFilePermissions.fromString("rwx------")));
        ProjectSnapshot baselineValue = ProjectEvidenceFiles.capture(project);
        ProjectSnapshot sourceValue = ProjectEvidenceFiles.materializeMaterial(
                project, sourceDirectory);
        baseline = json("project-snapshot", baselineValue);
        sourceSnapshot = json("project-snapshot", sourceValue);
        sourceManifest = json("project-source-manifest", ProjectSourceManifest.from(sourceValue));
        context = bytes("initial-context", "context");
        ledger = bytes("decision-ledger", "ledger");
        catalog = bytes("catalog-evidence", "catalog");
        design = bytes("design", "design");
        plan = bytes("plan", "plan");
        interaction = bytes("interaction-bundle", "interaction");
        artifactManifest = bytes("artifact-manifest", "manifest");
        evidence = List.of(bytes("command-evidence", "evidence-1"));
    }

    @Test
    void materializesSchemaConformantProtectedRequestsForEveryStage() throws Exception {
        ShipAttemptFactory factory = new ShipAttemptFactory();
        ShipRunView view = view(null, null, ledger);
        StageRequest firstExecution = factory.createRunnable(view, blobs, ShipStage.EXECUTE, 1);
        Path candidate = Path.of(firstExecution.candidateDirectory());
        BlobReference candidateSnapshot = json(
                "project-snapshot", ProjectEvidenceFiles.captureSealed(candidate));
        ShipRunView complete = view(candidate, candidateSnapshot, ledger);
        Schema schema = requestSchema();

        for (ShipStage stage : ShipStage.values()) {
            StageRequest request = factory.createRunnable(
                    complete, blobs, stage, stage.ordinal() + 2);

            factory.validateIssued(
                    complete,
                    blobs,
                    request,
                    stage,
                    stage.ordinal() + 2,
                    request.outputDirectory());
            assertTrue(
                    schema.validate(ShipJson.mapper().valueToTree(request)).isEmpty(),
                    () -> stage + " request does not match stage-request.schema.json");
            assertEquals(
                    ShipDigest.sha256(ShipJson.mapper().writeValueAsBytes(request.capability())),
                    request.policyDigest());
            assertEquals(
                    stage == ShipStage.EXECUTE,
                    request.outputDirectory().equals(request.candidateDirectory()));
            assertTrue(Path.of(request.outputDirectory()).startsWith(runRoot.resolve("attempts")));
            assertEquals(
                    "rwx------",
                    PosixFilePermissions.toString(
                            Files.getPosixFilePermissions(Path.of(request.outputDirectory()))));
            assertCapability(stage, request);
            assertProtectedReferences(request);
        }

        assertEquals(
                "- from: {uri: timer:tick}\n",
                Files.readString(Path.of(firstExecution.outputDirectory()).resolve("route.camel.yaml")));
        StageRequest designRequest = factory.createRunnable(
                complete, blobs, ShipStage.DESIGN, 20);
        assertArrayEquals(
                resource("ship/workers/design.md"),
                Files.readAllBytes(Path.of(designRequest.workerContractReference())));
        StageRequest discoveryRequest = factory.createRunnable(
                complete, blobs, ShipStage.DISCOVERY, 21);
        String discoveryContract = Files.readString(
                Path.of(discoveryRequest.workerContractReference()));
        assertTrue(discoveryContract.contains("--- BEGIN EXACT RESOURCE ---"));
        assertTrue(discoveryContract.contains("resource: ship/workers/discovery.md"));
        StageRequest reviewRequest = factory.createRunnable(
                complete, blobs, ShipStage.REVIEW, 22);
        String reviewContract = Files.readString(Path.of(reviewRequest.workerContractReference()));
        assertTrue(reviewContract.contains("# Bounded Ship review contract"));
        assertTrue(reviewContract.contains("# Shared discovery and completeness semantics"));

        StageRequest validationBefore = factory.createRunnable(
                complete, blobs, ShipStage.VALIDATE, 23);
        Files.writeString(candidate.resolve("route.camel.yaml"), "- from: {uri: direct:changed}\n");
        BlobReference changedSnapshot = json(
                "project-snapshot", ProjectEvidenceFiles.captureSealed(candidate));
        ShipRunView changed = view(candidate, changedSnapshot, ledger);
        StageRequest validationAfter = factory.createRunnable(
                changed, blobs, ShipStage.VALIDATE, 23);
        assertNotEquals(validationBefore.inputDigest(), validationAfter.inputDigest());
    }

    @Test
    void deterministicValidationRejectsForgedPathsCapabilitiesAndStaleInputs() throws Exception {
        ShipAttemptFactory factory = new ShipAttemptFactory();
        ShipRunView current = view(null, null, ledger);
        StageRequest request = factory.createRunnable(current, blobs, ShipStage.PLAN, 3);

        assertThrows(
                java.io.IOException.class,
                () -> factory.validateIssued(
                        current,
                        blobs,
                        copy(request, "/tmp/forged-contract", request.capability()),
                        ShipStage.PLAN,
                        3,
                        request.outputDirectory()));

        StageCapability forged = new StageCapability(
                RepositoryAccess.READ_ONLY,
                request.capability().readRoots(),
                List.of(),
                List.of(Operation.READ, Operation.SEARCH, Operation.RETURN_STRUCTURED_RESULT),
                false,
                false);
        assertThrows(
                java.io.IOException.class,
                () -> factory.validateIssued(
                        current,
                        blobs,
                        copy(request, request.workerContractReference(), forged),
                        ShipStage.PLAN,
                        3,
                        request.outputDirectory()));

        BlobReference changedLedger = bytes("decision-ledger", "changed-ledger");
        ShipRunView stale = view(null, null, changedLedger);
        assertThrows(
                java.io.IOException.class,
                () -> factory.validateIssued(
                        stale,
                        blobs,
                        request,
                        ShipStage.PLAN,
                        3,
                        request.outputDirectory()));
    }

    @Test
    void sameEventInputsBindTheRequestToTheSuccessorProjection() throws Exception {
        ShipAttemptFactory factory = new ShipAttemptFactory();
        ShipRunView before = view(null, null, ledger);
        BlobReference successorInteraction = bytes("interaction-bundle", "successor-interaction");
        ShipAttemptFactory.AttemptInputs successorInputs = new ShipAttemptFactory.AttemptInputs(
                context,
                ledger,
                catalog,
                design,
                plan,
                successorInteraction,
                artifactManifest,
                null,
                null,
                evidence,
                null,
                null);

        StageRequest request = factory.createRunnable(
                before, blobs, ShipStage.PLAN, 4, successorInputs);
        assertEquals(blobs.verifiedPath(successorInteraction).toString(), request.interactionReference());
        assertThrows(
                java.io.IOException.class,
                () -> factory.validateIssued(
                        before,
                        blobs,
                        request,
                        ShipStage.PLAN,
                        4,
                        request.outputDirectory()));

        ShipRunView after = view(null, null, ledger, successorInteraction);
        factory.validateIssued(
                after,
                blobs,
                request,
                ShipStage.PLAN,
                4,
                request.outputDirectory());
    }

    @Test
    void rejectsWrongKindSuccessorInputsAndIncompleteDiscoveryEvidence() throws Exception {
        ShipAttemptFactory factory = new ShipAttemptFactory();
        ShipRunView current = view(null, null, ledger);
        BlobReference wrongKindLedger = new BlobReference(
                "plan", ledger.digest(), ledger.byteSize());
        ShipAttemptFactory.AttemptInputs wrongKind = new ShipAttemptFactory.AttemptInputs(
                context,
                wrongKindLedger,
                catalog,
                design,
                plan,
                interaction,
                artifactManifest,
                null,
                null,
                evidence,
                null,
                null);
        assertThrows(
                java.io.IOException.class,
                () -> factory.createRunnable(current, blobs, ShipStage.PLAN, 5, wrongKind));

        ShipAttemptFactory.AttemptInputs incompleteDiscovery = new ShipAttemptFactory.AttemptInputs(
                context,
                null,
                catalog,
                null,
                null,
                interaction,
                null,
                null,
                null,
                List.of(),
                null,
                null);
        assertThrows(
                java.io.IOException.class,
                () -> factory.createRunnable(
                        current, blobs, ShipStage.DISCOVERY, 5, incompleteDiscovery));
    }

    @Test
    void rejectsSymlinkedProtectedSourceAndCandidateRoots() throws Exception {
        Path externalSource = Files.createDirectory(temporaryDirectory.resolve("external-source"));
        Files.writeString(externalSource.resolve("route.camel.yaml"), "external\n");
        Files.delete(sourceDirectory.resolve("route.camel.yaml"));
        Files.delete(sourceDirectory);
        Files.createSymbolicLink(sourceDirectory, externalSource);
        assertThrows(
                java.io.IOException.class,
                () -> new ShipAttemptFactory().createRunnable(
                        view(null, null, ledger), blobs, ShipStage.DISCOVERY, 6));

        Files.delete(sourceDirectory);
        Files.createDirectory(
                sourceDirectory,
                PosixFilePermissions.asFileAttribute(
                        PosixFilePermissions.fromString("rwx------")));
        ProjectSnapshot restored = ProjectEvidenceFiles.materializeMaterial(project, sourceDirectory);
        sourceSnapshot = json("project-snapshot", restored);
        sourceManifest = json("project-source-manifest", ProjectSourceManifest.from(restored));
        ShipAttemptFactory factory = new ShipAttemptFactory();
        StageRequest execution = factory.createRunnable(
                view(null, null, ledger), blobs, ShipStage.EXECUTE, 6);
        Path candidate = Path.of(execution.candidateDirectory());
        BlobReference snapshot = json(
                "project-snapshot", ProjectEvidenceFiles.captureSealed(candidate));
        Path externalCandidate = Files.createDirectory(temporaryDirectory.resolve("external-candidate"));
        Files.writeString(externalCandidate.resolve("route.camel.yaml"), "external\n");
        Files.delete(candidate.resolve("route.camel.yaml"));
        Files.delete(candidate);
        Files.createSymbolicLink(candidate, externalCandidate);
        assertThrows(
                java.io.IOException.class,
                () -> factory.createRunnable(
                        view(candidate, snapshot, ledger), blobs, ShipStage.VALIDATE, 6));
    }

    private void assertCapability(ShipStage stage, StageRequest request) {
        boolean writes = stage != ShipStage.DISCOVERY && stage != ShipStage.REVIEW;
        RepositoryAccess access = switch (stage) {
            case DISCOVERY, REVIEW -> RepositoryAccess.READ_ONLY;
            case EXECUTE -> RepositoryAccess.DECLARED_EXECUTION_PATHS;
            default -> RepositoryAccess.READ_WITH_STAGED_OUTPUT;
        };
        int reads = switch (stage) {
            case DISCOVERY -> 8;
            case REVIEW, DESIGN -> 8;
            case PLAN -> 5;
            case EXECUTE -> 6;
            case VALIDATE -> 8;
        };
        assertEquals(access, request.capability().repositoryAccess());
        assertEquals(reads, request.capability().readRoots().size());
        assertEquals(writes ? List.of(request.outputDirectory()) : List.of(), request.capability().writeRoots());
        assertEquals(writes, request.capability().allowedOperations().contains(Operation.WRITE_STAGED_ARTIFACT));
        assertFalse(request.capability().mayLaunchChildren());
        assertFalse(request.capability().mayInvokeLaterStages());
    }

    private void assertProtectedReferences(StageRequest request) throws Exception {
        assertTrue(Path.of(request.workerContractReference()).startsWith(runRoot));
        for (String reference : request.capability().readRoots()) {
            Path path = Path.of(reference);
            assertTrue(path.startsWith(runRoot));
            assertEquals(path, path.toRealPath(LinkOption.NOFOLLOW_LINKS));
            assertEquals(device(runRoot), device(path));
        }
    }

    private static long device(Path path) throws Exception {
        return ((Number) Files.getAttribute(
                path, "unix:dev", LinkOption.NOFOLLOW_LINKS)).longValue();
    }

    private ShipRunView view(
            Path candidateDirectory,
            BlobReference candidateSnapshot,
            BlobReference currentLedger) {
        return view(candidateDirectory, candidateSnapshot, currentLedger, interaction);
    }

    private ShipRunView view(
            Path candidateDirectory,
            BlobReference candidateSnapshot,
            BlobReference currentLedger,
            BlobReference currentInteraction) {
        ShipRun authority = new ShipRun(
                ShipRunId.fromStorageId(RUN_ID),
                AuthorityHeadId.create(),
                ShipState.CREATED,
                0,
                ShipEventType.RUN_CREATED,
                AuthorityData.empty());
        return new ShipRunView(
                authority,
                EVENT_DIGEST,
                project,
                "test-adapter",
                null,
                baseline,
                sourceSnapshot,
                sourceManifest,
                sourceDirectory,
                null,
                context,
                currentLedger,
                catalog,
                ShipDigest.sha256("requirements".getBytes(StandardCharsets.UTF_8)),
                design,
                ShipDigest.sha256("design".getBytes(StandardCharsets.UTF_8)),
                plan,
                null,
                currentInteraction,
                null,
                null,
                null,
                artifactManifest,
                null,
                null,
                candidateSnapshot,
                candidateDirectory,
                evidence,
                Map.of("evidence-1", evidence.get(0)),
                null,
                null,
                null,
                null,
                null,
                null);
    }

    private BlobReference bytes(String kind, String value) throws Exception {
        return blobs.writeBytes(kind, value.getBytes(StandardCharsets.UTF_8));
    }

    private BlobReference json(String kind, Object value) throws Exception {
        return blobs.writeBytes(kind, ShipJson.mapper().writeValueAsBytes(value));
    }

    private static StageRequest copy(
            StageRequest request, String workerContract, StageCapability capability) {
        return new StageRequest(
                request.schemaVersion(),
                request.runId(),
                request.stage(),
                request.attemptId(),
                request.attempt(),
                request.idempotencyKey(),
                request.challenge(),
                request.inputDigest(),
                request.policyDigest(),
                request.sourceDirectory(),
                request.sourceSnapshotReference(),
                request.projectSourceManifestReference(),
                request.contextReference(),
                request.ledgerReference(),
                request.catalogEvidenceReference(),
                request.approvedDesignReference(),
                request.planReference(),
                request.interactionReference(),
                request.artifactManifestReference(),
                request.candidateDirectory(),
                request.evidenceReferences(),
                request.failureCode(),
                request.failureMessage(),
                workerContract,
                request.outputDirectory(),
                capability);
    }

    private static Schema requestSchema() throws Exception {
        String root = StageResultWireSchema.SCHEMA_BASE + "stage-request.schema.json";
        return StageResultWireSchema.compile(
                Map.of(root, new String(resource("ship/schema/stage-request.schema.json"), StandardCharsets.UTF_8)),
                root);
    }

    private static byte[] resource(String name) throws Exception {
        try (InputStream input = ShipAttemptFactoryMaterializationTest.class
                .getClassLoader()
                .getResourceAsStream(name)) {
            if (input == null) {
                throw new IllegalStateException("Missing test resource " + name);
            }
            return input.readAllBytes();
        }
    }
}
