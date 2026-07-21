package io.github.luigidemasi.camelkit.ship.controller;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;

import io.github.luigidemasi.camelkit.ship.ShipDigest;
import io.github.luigidemasi.camelkit.ship.protocol.ProducedArtifact;
import io.github.luigidemasi.camelkit.ship.protocol.ShipStage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledOnOs(OS.LINUX)
class ShipProjectPublisherTest {

    private static final String RUN_ID = "ship-" + "3".repeat(32);
    private static final String EVENT_DIGEST = "sha256:" + "a".repeat(64);

    @TempDir
    Path temporaryDirectory;

    Path stateRoot;
    Path runRoot;
    Path project;
    ShipBlobStore blobs;

    @BeforeEach
    void createControllerAndProject() throws Exception {
        stateRoot = Files.createDirectory(
                temporaryDirectory.resolve("state"),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        runRoot = Files.createDirectory(
                stateRoot.resolve(RUN_ID),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        blobs = ShipBlobStore.create(stateRoot, RUN_ID);
        project = Files.createDirectory(temporaryDirectory.resolve("project"));
        Files.writeString(project.resolve("README.md"), "untouched baseline");
    }

    @Test
    void stagesExactCasPostimageBoundToEventHeadWithoutMutatingLiveProject() throws Exception {
        byte[] route = "- from:\n    uri: timer:orders\n".getBytes(StandardCharsets.UTF_8);
        ShipWorkspaceService.AcceptedWorkspace workspace = workspace("routes/orders.camel.yaml", route);
        byte[] liveReadme = Files.readAllBytes(project.resolve("README.md"));

        ShipProjectPublisher.PublicationTransaction transaction = ShipProjectPublisher.stageSealedCandidate(
                stateRoot, RUN_ID, project, EVENT_DIGEST, 7, blobs, workspace);

        assertEquals(RUN_ID, transaction.runId());
        assertEquals(EVENT_DIGEST, transaction.expectedEventHeadDigest());
        assertEquals(7, transaction.expectedEventHeadRevision());
        assertEquals(workspace.artifacts().get(0).blob(), transaction.artifacts().get(0).blob());
        assertTrue(ShipDigest.isSha256(transaction.baselineDigest()));
        assertTrue(ShipDigest.isSha256(transaction.postimageDigest()));
        assertTrue(ShipDigest.isSha256(transaction.bindingDigest()));

        assertArrayEquals(liveReadme, Files.readAllBytes(project.resolve("README.md")));
        assertFalse(Files.exists(project.resolve("routes")));

        Path transactionRoot = runRoot.resolve("publication").resolve(transaction.transactionId());
        assertArrayEquals(route, Files.readAllBytes(
                transactionRoot.resolve("candidate/routes/orders.camel.yaml")));
        assertEquals(
                PosixFilePermissions.fromString("rw-r--r--"),
                Files.getPosixFilePermissions(
                        transactionRoot.resolve("candidate/routes/orders.camel.yaml")));
        byte[] binding = Files.readAllBytes(transactionRoot.resolve("binding.bin"));
        assertEquals(transaction.bindingDigest(), ShipDigest.sha256(binding));
        assertEquals(
                PosixFilePermissions.fromString("r--------"),
                Files.getPosixFilePermissions(transactionRoot.resolve("binding.bin")));
    }

    @Test
    void nonMaterialArtifactFailsAndLeavesNoCandidateOrProjectMutation() throws Exception {
        byte[] settings = "secret-setting".getBytes(StandardCharsets.UTF_8);
        ShipWorkspaceService.AcceptedWorkspace workspace = workspace(".idea/settings.xml", settings);

        assertThrows(
                java.io.IOException.class,
                () -> ShipProjectPublisher.stageSealedCandidate(
                        stateRoot, RUN_ID, project, EVENT_DIGEST, 8, blobs, workspace));

        assertEquals("untouched baseline", Files.readString(project.resolve("README.md")));
        assertFalse(Files.exists(project.resolve(".idea")));
        Path publications = runRoot.resolve("publication");
        try (var entries = Files.list(publications)) {
            assertEquals(0, entries.count());
        }
    }

    @Test
    void materializedBaselineCannotRaceAwayAndBackToTheCapturedPreimage() throws Exception {
        byte[] route = "route".getBytes(StandardCharsets.UTF_8);
        ShipWorkspaceService.AcceptedWorkspace workspace = workspace("route.yaml", route);

        java.io.IOException error = assertThrows(
                java.io.IOException.class,
                () -> ShipProjectPublisher.stageSealedCandidate(
                        stateRoot,
                        RUN_ID,
                        project,
                        EVENT_DIGEST,
                        9,
                        blobs,
                        workspace,
                        point -> {
                            if (point.equals("after-baseline-capture")) {
                                Files.writeString(project.resolve("README.md"), "raced baseline");
                            } else if (point.equals("after-baseline-materialization")) {
                                Files.writeString(project.resolve("README.md"), "untouched baseline");
                            }
                        }));

        assertTrue(error.getMessage().contains("captured baseline"));
        assertEquals("untouched baseline", Files.readString(project.resolve("README.md")));
        try (var entries = Files.list(runRoot.resolve("publication"))) {
            assertEquals(0, entries.count());
        }
    }

    @Test
    void retainedPublicationCandidatesAreExplicitlyBoundedPerRun() throws Exception {
        ShipWorkspaceService.AcceptedWorkspace workspace
                = workspace("route.yaml", "route".getBytes(StandardCharsets.UTF_8));
        for (int revision = 0; revision < ShipProjectPublisher.MAX_RETAINED_CANDIDATES; revision++) {
            ShipProjectPublisher.stageSealedCandidate(
                    stateRoot, RUN_ID, project, EVENT_DIGEST, revision, blobs, workspace);
        }

        java.io.IOException error = assertThrows(
                java.io.IOException.class,
                () -> ShipProjectPublisher.stageSealedCandidate(
                        stateRoot,
                        RUN_ID,
                        project,
                        EVENT_DIGEST,
                        ShipProjectPublisher.MAX_RETAINED_CANDIDATES,
                        blobs,
                        workspace));

        assertTrue(error.getMessage().contains("maximum"));
        try (var entries = Files.list(runRoot.resolve("publication"))) {
            assertEquals(ShipProjectPublisher.MAX_RETAINED_CANDIDATES, entries.count());
        }
    }

    @Test
    void failedCandidateCleanupMakesReadOnlyDirectoriesOwnerWritableBeforeDeletion() throws Exception {
        Path readOnly = Files.createDirectory(project.resolve("readonly"));
        Files.writeString(readOnly.resolve("existing.txt"), "baseline");
        Files.setPosixFilePermissions(readOnly, PosixFilePermissions.fromString("r-xr-xr-x"));
        ShipWorkspaceService.AcceptedWorkspace workspace
                = workspace(".idea/settings.xml", "rejected".getBytes(StandardCharsets.UTF_8));

        try {
            assertThrows(
                    java.io.IOException.class,
                    () -> ShipProjectPublisher.stageSealedCandidate(
                            stateRoot, RUN_ID, project, EVENT_DIGEST, 10, blobs, workspace));
            try (var entries = Files.list(runRoot.resolve("publication"))) {
                assertEquals(0, entries.count());
            }
        } finally {
            Files.setPosixFilePermissions(readOnly, PosixFilePermissions.fromString("rwxr-xr-x"));
        }
    }

    @Test
    void cleanupFailureIsSuppressedOntoTheOriginalStagingFailure() throws Exception {
        Path publication = runRoot.resolve("publication");
        ShipWorkspaceService.AcceptedWorkspace workspace
                = workspace(".idea/settings.xml", "rejected".getBytes(StandardCharsets.UTF_8));

        java.io.IOException error;
        try {
            error = assertThrows(
                    java.io.IOException.class,
                    () -> ShipProjectPublisher.stageSealedCandidate(
                            stateRoot,
                            RUN_ID,
                            project,
                            EVENT_DIGEST,
                            11,
                            blobs,
                            workspace,
                            point -> {
                                if (point.equals("after-baseline-materialization")) {
                                    Files.setPosixFilePermissions(
                                            publication, PosixFilePermissions.fromString("r-x------"));
                                }
                            }));
        } finally {
            if (Files.exists(publication)) {
                Files.setPosixFilePermissions(
                        publication, PosixFilePermissions.fromString("rwx------"));
            }
        }

        assertTrue(error.getMessage().contains("non-material"));
        assertTrue(error.getSuppressed().length > 0);
        assertTrue(error.getSuppressed()[0].getMessage().contains("incomplete publication candidate"));
    }

    @Test
    void publisherExposesNoLiveCommitOperation() {
        List<String> methods = java.util.Arrays.stream(ShipProjectPublisher.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName)
                .toList();

        assertTrue(methods.contains("stageSealedCandidate"));
        assertFalse(methods.stream().anyMatch(name -> name.equals("commit") || name.equals("publish")));
    }

    private ShipWorkspaceService.AcceptedWorkspace workspace(String relativePath, byte[] content)
            throws Exception {
        ShipBlobStore.BlobReference blob = blobs.writeBytes("route", content);
        ProducedArtifact claim = new ProducedArtifact(
                "route", relativePath, blob.digest(), blob.byteSize());
        ShipWorkspaceService.AcceptedArtifact artifact
                = new ShipWorkspaceService.AcceptedArtifact(claim, blob, 0100644);
        return new ShipWorkspaceService.AcceptedWorkspace(
                RUN_ID,
                ShipStage.EXECUTE,
                "attempt-1",
                "sha256:" + "b".repeat(64),
                List.of(artifact));
    }
}
