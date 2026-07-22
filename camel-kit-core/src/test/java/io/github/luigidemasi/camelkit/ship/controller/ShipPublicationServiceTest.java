package io.github.luigidemasi.camelkit.ship.controller;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import io.github.luigidemasi.camelkit.ship.ShipDigest;
import io.github.luigidemasi.camelkit.ship.controller.ShipBlobStore.BlobReference;
import io.github.luigidemasi.camelkit.ship.protocol.ProducedArtifact;
import io.github.luigidemasi.camelkit.ship.protocol.ShipStage;
import io.github.luigidemasi.camelkit.ship.protocol.StageResult;
import io.github.luigidemasi.camelkit.ship.security.ProjectEvidenceFiles;
import io.github.luigidemasi.camelkit.ship.security.ProjectSnapshot;

import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledOnOs(OS.LINUX)
class ShipPublicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-22T00:00:00Z");
    private static final String SYNTACTIC_MAC = "hmac-sha256:" + "0".repeat(64);
    private static final String ROUTE = "src/main/resources/routes/orders.camel.yaml";
    private static final byte[] BASELINE_BYTES
            = "untouched baseline\n".getBytes(StandardCharsets.UTF_8);
    private static final byte[] ROUTE_BYTES
            = "- from:\n    uri: timer:orders\n".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path temporaryDirectory;

    @Test
    void closeRollsBackTheLiveProjectAndRemovesTheJournal() throws Exception {
        Fixture fixture = fixture("close");
        ShipPublicationService.LivePublication publication = publish(fixture);

        assertPublished(fixture);
        assertJournalPresent(fixture);

        publication.close();

        assertBaseline(fixture);
        assertJournalRemoved(fixture);
    }

    @Test
    void recoverRollsBackAProtectedPredecessorHeadAndRemovesTheJournal() throws Exception {
        Fixture fixture = fixture("predecessor");
        ShipPublicationService.LivePublication publication = publish(fixture);
        publication.retainForRecovery();
        publication.close();

        assertPublished(fixture);
        assertJournalPresent(fixture);

        ShipPublicationService.recover(
                fixture.predecessorView(),
                ShipBlobStore.open(fixture.stateRoot(), fixture.runId()),
                List.of(fixture.predecessorEvent()));

        assertBaseline(fixture);
        assertJournalRemoved(fixture);
    }

    @Test
    void recoverPreservesAnAuthenticatedCompletionAndRemovesTheJournal() throws Exception {
        Fixture fixture = fixture("completed");
        ShipPublicationService.LivePublication publication = publish(fixture);
        ProjectSnapshot published = publication.publishedSnapshot();
        BlobReference publishedReference = json(
                fixture.blobs(), "project-snapshot", published);
        publication.retainForRecovery();
        publication.close();

        ShipEvent predecessor = fixture.predecessorEvent();
        ShipEvent completion = fixture.completionEvent(predecessor);
        ShipRunView completedView = fixture.completedView(publishedReference);

        assertPublished(fixture);
        assertJournalPresent(fixture);

        ShipPublicationService.recover(
                completedView,
                ShipBlobStore.open(fixture.stateRoot(), fixture.runId()),
                List.of(predecessor, completion));

        assertTrue(ProjectEvidenceFiles.unchangedMaterialTree(
                published, ProjectEvidenceFiles.capture(fixture.project())));
        assertPublished(fixture);
        assertJournalRemoved(fixture);
    }

    @Test
    void recoverRefusesAnUnknownMixtureBeforeOverwritingProjectBytes() throws Exception {
        Fixture fixture = fixture("unknown-mixture");
        ShipPublicationService.LivePublication publication = publish(fixture);
        publication.retainForRecovery();
        publication.close();
        byte[] externalBytes = "external concurrent edit\n".getBytes(StandardCharsets.UTF_8);
        Files.write(fixture.route(), externalBytes);

        assertThrows(
                IOException.class,
                () -> ShipPublicationService.recover(
                        fixture.predecessorView(),
                        ShipBlobStore.open(fixture.stateRoot(), fixture.runId()),
                        List.of(fixture.predecessorEvent())));

        assertArrayEquals(externalBytes, Files.readAllBytes(fixture.route()));
        assertArrayEquals(BASELINE_BYTES, Files.readAllBytes(fixture.readme()));
        assertJournalPresent(fixture);
    }

    @Test
    void recoverRejectsUnsafeJournalMetadataWithoutChangingTheProject() throws Exception {
        Fixture fixture = fixture("journal-metadata");
        ShipPublicationService.LivePublication publication = publish(fixture);
        publication.retainForRecovery();
        publication.close();
        Files.setPosixFilePermissions(
                fixture.journal(), PosixFilePermissions.fromString("rw-------"));

        IOException failure = assertThrows(
                IOException.class,
                () -> ShipPublicationService.recover(
                        fixture.predecessorView(),
                        ShipBlobStore.open(fixture.stateRoot(), fixture.runId()),
                        List.of(fixture.predecessorEvent())));

        assertTrue(failure.getMessage().contains("unsafe metadata"));
        assertPublished(fixture);
        assertJournalPresent(fixture);
    }

    @Test
    void recoverRejectsMismatchedJournalIdentityWithoutChangingTheProject() throws Exception {
        Fixture fixture = fixture("journal-identity");
        ShipPublicationService.LivePublication publication = publish(fixture);
        publication.retainForRecovery();
        publication.close();
        Path journal = fixture.journal();
        Files.setPosixFilePermissions(
                journal, PosixFilePermissions.fromString("rw-------"));
        ObjectNode intent = (ObjectNode) ShipJson.mapper().readTree(Files.readAllBytes(journal));
        intent.put("runId", "ship-" + "f".repeat(32));
        Files.write(
                journal,
                ShipJson.mapper().writeValueAsBytes(intent),
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
        Files.setPosixFilePermissions(
                journal, PosixFilePermissions.fromString("r--------"));

        IOException failure = assertThrows(
                IOException.class,
                () -> ShipPublicationService.recover(
                        fixture.predecessorView(),
                        ShipBlobStore.open(fixture.stateRoot(), fixture.runId()),
                        List.of(fixture.predecessorEvent())));

        assertTrue(failure.getMessage().contains("invalid identity"));
        assertPublished(fixture);
        assertJournalPresent(fixture);
    }

    private Fixture fixture(String name) throws Exception {
        AuthorityPair authority = authorityPair(name);
        String runId = authority.predecessor().id().toString();
        Path stateRoot = Files.createDirectory(
                temporaryDirectory.resolve(name + "-state"),
                PosixFilePermissions.asFileAttribute(
                        PosixFilePermissions.fromString("rwx------")))
                .toAbsolutePath()
                .normalize();
        Path runRoot = Files.createDirectory(
                stateRoot.resolve(runId),
                PosixFilePermissions.asFileAttribute(
                        PosixFilePermissions.fromString("rwx------")));
        ShipBlobStore blobs = ShipBlobStore.create(stateRoot, runId);
        Path project = Files.createDirectory(temporaryDirectory.resolve(name + "-project"))
                .toAbsolutePath()
                .normalize();
        Files.write(project.resolve("README.md"), BASELINE_BYTES);
        ProjectSnapshot baseline = ProjectEvidenceFiles.capture(project);
        BlobReference baselineReference = json(blobs, "project-snapshot", baseline);
        Path source = Files.createDirectory(
                runRoot.resolve("source"),
                PosixFilePermissions.asFileAttribute(
                        PosixFilePermissions.fromString("rwx------")));
        ProjectEvidenceFiles.materializeMaterial(project, source);

        BlobReference artifactBlob = blobs.writeBytes("route", ROUTE_BYTES);
        ProducedArtifact claim = new ProducedArtifact(
                "route", ROUTE, artifactBlob.digest(), artifactBlob.byteSize());
        String inputDigest = digest(name + "-execution-input");
        ShipWorkspaceService.AcceptedArtifact accepted
                = new ShipWorkspaceService.AcceptedArtifact(claim, artifactBlob, 0100644);
        ShipWorkspaceService.AcceptedWorkspace workspace
                = new ShipWorkspaceService.AcceptedWorkspace(
                        runId,
                        ShipStage.EXECUTE,
                        "attempt-1",
                        inputDigest,
                        List.of(accepted));
        String predecessorDigest = digest(name + "-predecessor-event");
        try (ShipProjectPublisher.StagedCandidate stagedCandidate
                = ShipProjectPublisher.stageSealedCandidate(
                        stateRoot,
                        runId,
                        project,
                        predecessorDigest,
                        authority.predecessor().revision(),
                        blobs,
                        workspace)) {
            Path candidateDirectory = stagedCandidate.candidateDirectory();
            ProjectSnapshot candidate = ProjectEvidenceFiles.captureSealed(candidateDirectory);
            BlobReference candidateReference = json(blobs, "project-snapshot", candidate);
            StageResult execution = new StageResult(
                    StageResult.SCHEMA_VERSION,
                    runId,
                    ShipStage.EXECUTE,
                    "attempt-1",
                    "challenge",
                    inputDigest,
                    StageResult.Outcome.COMPLETED,
                    null,
                    null,
                    List.of(),
                    List.of(claim),
                    null,
                    null,
                    null);
            BlobReference executionReference = json(blobs, "stage-result", execution);
            BlobReference stamp = blobs.writeBytes(
                    "ship-stamp", ("stamp " + name).getBytes(StandardCharsets.UTF_8));
            String completionDigest = digest(name + "-completion-event");
            ShipRunView predecessorView = view(
                    authority.predecessor(),
                    predecessorDigest,
                    project,
                    baselineReference,
                    source,
                    executionReference,
                    candidateReference,
                    candidateDirectory,
                    null);
            Fixture fixture = new Fixture(
                    stateRoot,
                    runRoot,
                    project,
                    blobs,
                    authority.predecessor(),
                    authority.completed(),
                    predecessorView,
                    baseline,
                    candidate,
                    stamp,
                    predecessorDigest,
                    completionDigest);
            stagedCandidate.transferToHistory();
            return fixture;
        }
    }

    private static ShipPublicationService.LivePublication publish(Fixture fixture)
            throws IOException {
        return ShipPublicationService.apply(
                fixture.predecessorView(),
                fixture.blobs(),
                fixture.stamp(),
                ShipEventType.RUN_COMPLETED);
    }

    private static ShipRunView view(
            ShipRun authority,
            String eventDigest,
            Path project,
            BlobReference baseline,
            Path source,
            BlobReference execution,
            BlobReference candidate,
            Path candidateDirectory,
            BlobReference stamp) {
        return new ShipRunView(
                authority,
                eventDigest,
                project,
                "test-adapter",
                null,
                baseline,
                baseline,
                null,
                source,
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
                null,
                execution,
                candidate,
                candidateDirectory,
                List.of(),
                Map.of(),
                null,
                stamp,
                null,
                null,
                null,
                null);
    }

    private static AuthorityPair authorityPair(String suffix) {
        ShipRun context = ShipLifecycleReducer.startContextResolution(
                ShipLifecycleReducer.start(), id("context-input-" + suffix));
        ShipRun recorded = ShipLifecycleReducer.recordContext(
                context,
                AcceptedStageResultFactory.context(
                        ShipLifecycleReducer.attempt(context), id("context-" + suffix)));
        ShipRun discovery = ShipLifecycleReducer.startDiscovery(recorded);
        ShipRun review = ShipLifecycleReducer.startGapReview(
                discovery,
                AcceptedStageResultFactory.discoveryCandidate(
                        ShipLifecycleReducer.attempt(discovery), id("candidate-" + suffix)));
        AuthorityBasis basis = new AuthorityBasis(
                id("context-" + suffix),
                id("ledger-" + suffix),
                1,
                id("requirements-" + suffix),
                id("policy"),
                id("baseline"));
        ShipRun requirements = ShipLifecycleReducer.requirementsReady(
                review,
                AcceptedStageResultFactory.requirements(
                        ShipLifecycleReducer.attempt(review), basis));
        ShipRun design = ShipLifecycleReducer.startDesign(requirements);
        ShipRun designReady = ShipLifecycleReducer.designReady(
                design,
                AcceptedStageResultFactory.design(
                        ShipLifecycleReducer.attempt(design), id("design-" + suffix)));
        ShipRun waitingDesign = ShipLifecycleReducer.requestDesignApproval(designReady);
        ShipRun designApproved = ShipLifecycleReducer.approveDesign(
                waitingDesign, approvedDesign(waitingDesign));
        ShipRun plan = ShipLifecycleReducer.startPlan(designApproved);
        ShipRun planValidated = ShipLifecycleReducer.planValidated(
                plan,
                AcceptedStageResultFactory.plan(
                        ShipLifecycleReducer.attempt(plan), id("plan-" + suffix)));
        ShipRun waitingPlan = ShipLifecycleReducer.requestPlanApproval(planValidated);
        ShipRun planApproved = ShipLifecycleReducer.approvePlan(
                waitingPlan, approvedPlan(waitingPlan));
        ShipRun execution = ShipLifecycleReducer.startExecution(planApproved);
        ShipRun executionValidated = ShipLifecycleReducer.executionValidated(
                execution,
                AcceptedStageResultFactory.execution(
                        ShipLifecycleReducer.attempt(execution), id("execution-" + suffix)));
        ShipRun validation = ShipLifecycleReducer.startValidation(executionValidated);
        ShipRun validationPassed = ShipLifecycleReducer.validationPassed(
                validation,
                AcceptedStageResultFactory.validation(
                        ShipLifecycleReducer.attempt(validation), id("validation-" + suffix)));
        ShipRun predecessor = ShipLifecycleReducer.startStamp(validationPassed);
        ShipRun completed = ShipLifecycleReducer.completeStamp(
                predecessor,
                AcceptedStageResultFactory.stamp(
                        ShipLifecycleReducer.attempt(predecessor), id("stamp-" + suffix)));
        return new AuthorityPair(predecessor, completed);
    }

    private static DesignApprovalDecision approvedDesign(ShipRun run) {
        return construct(
                DesignApprovalDecision.class,
                new Class<?>[]{
                        PendingInteraction.class,
                        DesignApprovalDecisionValue.class,
                        ContentId.class
                },
                ShipLifecycleReducer.pending(run),
                DesignApprovalDecisionValue.APPROVE,
                null);
    }

    private static PlanApprovalDecision approvedPlan(ShipRun run) {
        return construct(
                PlanApprovalDecision.class,
                new Class<?>[]{
                        PendingInteraction.class,
                        PlanApprovalDecisionValue.class,
                        ContentId.class
                },
                ShipLifecycleReducer.pending(run),
                PlanApprovalDecisionValue.APPROVE,
                null);
    }

    private static <T> T construct(
            Class<T> type, Class<?>[] parameterTypes, Object... arguments) {
        try {
            Object value = MethodHandles.privateLookupIn(type, MethodHandles.lookup())
                    .findConstructor(type, MethodType.methodType(void.class, parameterTypes))
                    .invokeWithArguments(arguments);
            return type.cast(value);
        } catch (Throwable failure) {
            throw new AssertionError("Cannot issue test-only " + type.getSimpleName(), failure);
        }
    }

    private static BlobReference json(
            ShipBlobStore blobs, String kind, Object value)
            throws IOException {
        return blobs.writeBytes(kind, ShipJson.mapper().writeValueAsBytes(value));
    }

    private static ContentId id(String value) {
        return new ContentId(value);
    }

    private static String digest(String value) {
        return ShipDigest.sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    private static void assertBaseline(Fixture fixture) throws IOException {
        assertTrue(ProjectEvidenceFiles.unchangedMaterialTree(
                fixture.baseline(), ProjectEvidenceFiles.capture(fixture.project())));
        assertArrayEquals(BASELINE_BYTES, Files.readAllBytes(fixture.readme()));
        assertFalse(Files.exists(fixture.route(), LinkOption.NOFOLLOW_LINKS));
    }

    private static void assertPublished(Fixture fixture) throws IOException {
        assertTrue(ProjectEvidenceFiles.unchangedMaterialTree(
                fixture.candidate(), ProjectEvidenceFiles.capture(fixture.project())));
        assertArrayEquals(BASELINE_BYTES, Files.readAllBytes(fixture.readme()));
        assertArrayEquals(ROUTE_BYTES, Files.readAllBytes(fixture.route()));
    }

    private static void assertJournalPresent(Fixture fixture) {
        assertTrue(Files.isRegularFile(fixture.journal(), LinkOption.NOFOLLOW_LINKS));
    }

    private static void assertJournalRemoved(Fixture fixture) {
        assertFalse(Files.exists(fixture.journal(), LinkOption.NOFOLLOW_LINKS));
    }

    private record AuthorityPair(ShipRun predecessor, ShipRun completed) {
    }

    private record Fixture(
            Path stateRoot,
            Path runRoot,
            Path project,
            ShipBlobStore blobs,
            ShipRun predecessor,
            ShipRun completed,
            ShipRunView predecessorView,
            ProjectSnapshot baseline,
            ProjectSnapshot candidate,
            BlobReference stamp,
            String predecessorDigest,
            String completionDigest) {

        String runId() {
            return predecessor.id().toString();
        }

        Path readme() {
            return project.resolve("README.md");
        }

        Path route() {
            return project.resolve(ROUTE);
        }

        Path journal() {
            return runRoot.resolve("publication").resolve("live-publication.json");
        }

        ShipRunView completedView(BlobReference publishedSnapshot) {
            return view(
                    completed,
                    completionDigest,
                    project,
                    predecessorView.baselineSnapshot(),
                    predecessorView.sourceDirectory(),
                    predecessorView.executionResult(),
                    publishedSnapshot,
                    null,
                    stamp);
        }

        ShipEvent predecessorEvent() {
            return new ShipEvent(
                    ShipEvent.SCHEMA_VERSION,
                    predecessor.id(),
                    predecessor.revision() + 1,
                    digest(runId() + "-before-stamp"),
                    ShipEventType.STAMP_STARTED,
                    ShipState.VALIDATE_PASSED,
                    ShipState.STAMP_RUNNING,
                    AuthorityHeadId.create(),
                    predecessor.head(),
                    NOW,
                    NullNode.getInstance(),
                    predecessorDigest,
                    SYNTACTIC_MAC);
        }

        ShipEvent completionEvent(ShipEvent predecessorEvent) {
            return new ShipEvent(
                    ShipEvent.SCHEMA_VERSION,
                    completed.id(),
                    predecessorEvent.sequence() + 1,
                    predecessorEvent.eventDigest(),
                    ShipEventType.RUN_COMPLETED,
                    ShipState.STAMP_RUNNING,
                    ShipState.COMPLETED,
                    predecessor.head(),
                    completed.head(),
                    NOW.plusSeconds(1),
                    NullNode.getInstance(),
                    completionDigest,
                    SYNTACTIC_MAC);
        }
    }
}
