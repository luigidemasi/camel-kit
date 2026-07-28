package io.github.luigidemasi.camelkit.ship.worker;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import io.github.luigidemasi.camelkit.ship.ShipDigest;
import io.github.luigidemasi.camelkit.ship.security.ProjectEvidenceFiles;
import io.github.luigidemasi.camelkit.ship.security.ProjectSnapshot;
import io.github.luigidemasi.camelkit.ship.security.ShipFilesystemException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledOnOs(OS.LINUX)
class ShipWorkspaceTest {

    private static final String RUN_ID = "ship-0123456789abcdef0123456789abcdef";
    private static final String INPUT_DIGEST = digest("input");

    @TempDir
    Path temporaryDirectory;

    @Test
    void materializesTheExactMaterialTreeWithoutChangingTheLiveProject() throws Exception {
        Path project = directory("project");
        Path route = write(project, "routes/orders.camel.yaml", "- route:\n");
        Files.setPosixFilePermissions(route, PosixFilePermissions.fromString("rw-------"));
        Files.createDirectory(project.resolve("empty"));
        ProjectSnapshot before = ProjectEvidenceFiles.capture(project);
        Path run = directory("run");

        Path candidate = ShipWorkspace.prepare(project, run, RUN_ID, 1, INPUT_DIGEST);

        assertEquals(run.resolve("workspace/candidate"), candidate);
        assertTrue(Files.isRegularFile(run.resolve("workspace/workspace.json")));
        assertEquals("- route:\n", Files.readString(candidate
                .resolve("routes/orders.camel.yaml")));
        assertTrue(Files.isDirectory(candidate.resolve("empty")));
        assertTrue(ProjectEvidenceFiles.unchanged(before, ProjectEvidenceFiles.capture(project)));
        assertTrue(ProjectEvidenceFiles.unchangedMaterialTree(
                before, ProjectEvidenceFiles.captureSealed(candidate)));
        assertEquals(
                PosixFilePermissions.fromString("rwx------"),
                Files.getPosixFilePermissions(candidate));
        assertEquals(
                PosixFilePermissions.fromString("rwx------"),
                Files.getPosixFilePermissions(run.resolve("workspace")));
        assertEquals(
                PosixFilePermissions.fromString("rw-------"),
                Files.getPosixFilePermissions(run.resolve("workspace/workspace.json")));
    }

    @Test
    void excludesCredentialAndNonMaterialPaths() throws Exception {
        Path project = directory("project");
        write(project, "README.md", "material");
        write(project, ".env", "TOKEN=secret");
        write(project, ".ssh/config", "credential");
        write(project, "target/generated.txt", "generated");
        write(project, ".idea/workspace.xml", "private");
        write(project, ".env.example", "TOKEN=");

        Path candidate = ShipWorkspace.prepare(
                project, directory("run"), RUN_ID, 1, INPUT_DIGEST);

        assertTrue(Files.exists(candidate.resolve("README.md")));
        assertTrue(Files.exists(candidate.resolve(".env.example")));
        assertFalse(Files.exists(candidate.resolve(".env")));
        assertFalse(Files.exists(candidate.resolve(".ssh")));
        assertFalse(Files.exists(candidate.resolve("target")));
        assertFalse(Files.exists(candidate.resolve(".idea")));
    }

    @Test
    void rejectsUnsafeRealFilenamesWithATypedFailure() throws Exception {
        Path project = directory("project");
        Files.writeString(project.resolve("line\nbreak"), "content");

        ShipFilesystemException failure = assertThrows(
                ShipFilesystemException.class,
                () -> ShipWorkspace.prepare(
                        project, directory("run"), RUN_ID, 1, INPUT_DIGEST));

        assertEquals(ShipFilesystemException.UNSAFE_ENTRY, failure.code());
    }

    @Test
    void reusesTheBoundWorkspaceWithoutOverwritingPartialEdits() throws Exception {
        Path project = directory("project");
        write(project, "README.md", "baseline");
        Path run = directory("run");
        Path first = ShipWorkspace.prepare(project, run, RUN_ID, 1, INPUT_DIGEST);
        assertEquals(first.toString(),
                ShipWorkspace.verify(project, first, RUN_ID, 1, INPUT_DIGEST).root());
        assertThrows(IOException.class,
                () -> ShipWorkspace.verify(project, first, RUN_ID, 2, INPUT_DIGEST));
        Files.writeString(first.resolve("README.md"), "partial edit");
        write(first, "src/new.txt", "partial file");
        write(first, "target/generated.txt", "partial build output");

        Path resumed = ShipWorkspace.prepare(project, run, RUN_ID, 2, INPUT_DIGEST);

        assertEquals(first, resumed);
        assertThrows(IOException.class,
                () -> ShipWorkspace.verify(project, resumed, RUN_ID, 1, INPUT_DIGEST));
        assertEquals(resumed.toString(),
                ShipWorkspace.verify(project, resumed, RUN_ID, 2, INPUT_DIGEST).root());
        assertEquals("partial edit", Files.readString(resumed.resolve("README.md")));
        assertEquals("partial file", Files.readString(resumed.resolve("src/new.txt")));
        assertEquals("partial build output",
                Files.readString(resumed.resolve("target/generated.txt")));
    }

    @Test
    void upgradesASealedWorkspaceBeforeAdvancingItsAttempt() throws Exception {
        Path project = directory("project");
        write(project, "README.md", "baseline");
        Path run = directory("run");
        Path candidate = ShipWorkspace.prepare(project, run, RUN_ID, 1, INPUT_DIGEST);
        Path workspace = candidate.getParent();
        Path binding = workspace.resolve("workspace.json");
        Files.setPosixFilePermissions(binding, PosixFilePermissions.fromString("r--------"));
        Files.setPosixFilePermissions(workspace, PosixFilePermissions.fromString("r-x------"));

        assertEquals(candidate, ShipWorkspace.prepare(project, run, RUN_ID, 2, INPUT_DIGEST));
        assertEquals(
                PosixFilePermissions.fromString("rwx------"),
                Files.getPosixFilePermissions(workspace));
        assertEquals(
                PosixFilePermissions.fromString("rw-------"),
                Files.getPosixFilePermissions(binding));
    }

    @Test
    void recoversTheDisplacedWorkspaceAfterInterruptedPublication() throws Exception {
        Path project = directory("project");
        write(project, "README.md", "baseline");
        Path run = directory("run");
        Path candidate = ShipWorkspace.prepare(project, run, RUN_ID, 1, INPUT_DIGEST);
        Files.writeString(candidate.resolve("README.md"), "partial edit");
        Files.move(
                run.resolve("workspace"),
                run.resolve(".workspace-stale"),
                java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        Path abandoned = Files.createDirectory(run.resolve(".workspace-interrupted"));
        Files.createDirectory(abandoned.resolve("candidate"));

        Path recovered = ShipWorkspace.prepare(project, run, RUN_ID, 2, INPUT_DIGEST);

        assertEquals(candidate, recovered);
        assertEquals("partial edit", Files.readString(recovered.resolve("README.md")));
        assertNoWorkspaceDebris(run);
    }

    @Test
    void keepsAValidPublishedWorkspaceWhenCrashDebrisCoexists() throws Exception {
        Path project = directory("project");
        write(project, "README.md", "baseline");
        Path run = directory("run");
        Path staleCandidate = ShipWorkspace.prepare(project, run, RUN_ID, 1, INPUT_DIGEST);
        Files.writeString(staleCandidate.resolve("README.md"), "stale edit");
        Files.move(
                run.resolve("workspace"),
                run.resolve(".workspace-stale"),
                java.nio.file.StandardCopyOption.ATOMIC_MOVE);

        Path sourceRun = directory("source-run");
        Path currentCandidate = ShipWorkspace.prepare(project, sourceRun, RUN_ID, 2, INPUT_DIGEST);
        Files.writeString(currentCandidate.resolve("README.md"), "current edit");
        Files.move(
                sourceRun.resolve("workspace"),
                run.resolve("workspace"),
                java.nio.file.StandardCopyOption.ATOMIC_MOVE);

        Path recovered = ShipWorkspace.prepare(project, run, RUN_ID, 2, INPUT_DIGEST);

        assertEquals(run.resolve("workspace/candidate"), recovered);
        assertEquals("current edit", Files.readString(recovered.resolve("README.md")));
        assertNoWorkspaceDebris(run);
    }

    @Test
    void rollsBackAnInvalidPublishedWorkspaceWhenCrashDebrisCoexists() throws Exception {
        Path project = directory("project");
        write(project, "README.md", "baseline");
        Path run = directory("run");
        Path staleCandidate = ShipWorkspace.prepare(project, run, RUN_ID, 1, INPUT_DIGEST);
        Files.writeString(staleCandidate.resolve("README.md"), "partial edit");
        Files.move(
                run.resolve("workspace"),
                run.resolve(".workspace-stale"),
                java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        Files.createDirectories(run.resolve("workspace/candidate"));
        Files.writeString(run.resolve("workspace/candidate/README.md"), "invalid current");

        Path recovered = ShipWorkspace.prepare(project, run, RUN_ID, 2, INPUT_DIGEST);

        assertEquals(run.resolve("workspace/candidate"), recovered);
        assertEquals("partial edit", Files.readString(recovered.resolve("README.md")));
        assertNoWorkspaceDebris(run);
    }

    @Test
    void reusedAttemptCannotAcceptLaterBaselineDrift() throws Exception {
        Path project = directory("project");
        write(project, "README.md", "baseline");
        Path run = directory("run");
        Path candidate = ShipWorkspace.prepare(project, run, RUN_ID, 1, INPUT_DIGEST);
        Files.writeString(candidate.resolve("README.md"), "partial edit");
        assertEquals(candidate,
                ShipWorkspace.prepare(project, run, RUN_ID, 2, INPUT_DIGEST));

        Files.writeString(project.resolve("README.md"), "changed baseline");

        assertThrows(IOException.class,
                () -> ShipWorkspace.prepare(project, run, RUN_ID, 2, INPUT_DIGEST));
        assertEquals("partial edit", Files.readString(candidate.resolve("README.md")));
        assertEquals(candidate,
                ShipWorkspace.prepare(project, run, RUN_ID, 3, INPUT_DIGEST));
        assertEquals("changed baseline", Files.readString(candidate.resolve("README.md")));
    }

    @Test
    void failedVerificationStillConsumesTheLaterAttempt() throws Exception {
        Path project = directory("project");
        write(project, "README.md", "baseline");
        Path run = directory("run");
        Path candidate = ShipWorkspace.prepare(project, run, RUN_ID, 1, INPUT_DIGEST);
        Files.writeString(candidate.resolve("README.md"), "partial edit");
        Path unsafe = write(candidate, "target/.env", "TOKEN=secret");

        assertThrows(IOException.class,
                () -> ShipWorkspace.prepare(project, run, RUN_ID, 2, INPUT_DIGEST));
        Files.delete(unsafe);
        Files.writeString(project.resolve("README.md"), "changed baseline");

        assertThrows(IOException.class,
                () -> ShipWorkspace.prepare(project, run, RUN_ID, 2, INPUT_DIGEST));
        assertEquals("partial edit", Files.readString(candidate.resolve("README.md")));
        assertEquals(candidate,
                ShipWorkspace.prepare(project, run, RUN_ID, 3, INPUT_DIGEST));
        assertEquals("changed baseline", Files.readString(candidate.resolve("README.md")));
    }

    @Test
    void rotatesChangedInputOrBaselineOnlyForALaterAttempt() throws Exception {
        Path project = directory("project");
        write(project, "README.md", "baseline");
        Path run = directory("run");
        Path candidate = ShipWorkspace.prepare(project, run, RUN_ID, 1, INPUT_DIGEST);
        Files.writeString(candidate.resolve("README.md"), "partial edit");
        String changedInput = digest("changed-input");

        assertThrows(IOException.class,
                () -> ShipWorkspace.prepare(project, run, RUN_ID, 1, changedInput));

        Path rebound = ShipWorkspace.prepare(project, run, RUN_ID, 2, changedInput);
        assertEquals(candidate, rebound);
        assertEquals("baseline", Files.readString(rebound.resolve("README.md")));

        Files.writeString(project.resolve("README.md"), "new baseline");
        assertThrows(IOException.class,
                () -> ShipWorkspace.prepare(project, run, RUN_ID, 2, changedInput));

        Path refreshed = ShipWorkspace.prepare(project, run, RUN_ID, 3, changedInput);
        assertEquals(candidate, refreshed);
        assertEquals("new baseline", Files.readString(refreshed.resolve("README.md")));
        assertNoWorkspaceDebris(run);

        for (int attempt = 4; attempt <= 6; attempt++) {
            assertEquals(candidate, ShipWorkspace.prepare(
                    project, run, RUN_ID, attempt, digest("input-" + attempt)));
            assertNoWorkspaceDebris(run);
        }
    }

    @Test
    void rejectsUnsafeStaleWorkspaceBeforeRotationAndLeavesNoDebris() throws Exception {
        Path project = directory("project");
        write(project, "README.md", "baseline");
        Path run = directory("run");
        Path candidate = ShipWorkspace.prepare(project, run, RUN_ID, 1, INPUT_DIGEST);
        Files.writeString(project.resolve("README.md"), "changed baseline");

        Path credential = write(candidate, "target/.env", "TOKEN=secret");
        assertThrows(IOException.class,
                () -> ShipWorkspace.prepare(project, run, RUN_ID, 2, INPUT_DIGEST));
        assertFalse(Files.exists(run.resolve(".workspace-stale")));
        Files.delete(credential);

        Path link = candidate.resolve("target/link");
        Files.createSymbolicLink(link, project.resolve("README.md"));
        assertThrows(IOException.class,
                () -> ShipWorkspace.prepare(project, run, RUN_ID, 2, INPUT_DIGEST));
        assertFalse(Files.exists(run.resolve(".workspace-stale")));
        Files.delete(link);

        assertEquals(candidate,
                ShipWorkspace.prepare(project, run, RUN_ID, 3, INPUT_DIGEST));
        assertEquals("changed baseline", Files.readString(candidate.resolve("README.md")));
        assertNoWorkspaceDebris(run);
    }

    @Test
    void portableBaselineIgnoresOwnershipAndSpecialModeBits() throws Exception {
        Path project = directory("project");
        Path shared = Files.createDirectory(project.resolve("shared"));
        Files.setAttribute(shared, "unix:mode", 02750);
        write(shared, "README.md", "shared material");
        Path run = directory("run");

        Path candidate = ShipWorkspace.prepare(project, run, RUN_ID, 1, INPUT_DIGEST);

        assertEquals(
                PosixFilePermissions.fromString("rwxr-x---"),
                Files.getPosixFilePermissions(candidate.resolve("shared")));
        assertEquals(candidate,
                ShipWorkspace.prepare(project, run, RUN_ID, 2, INPUT_DIGEST));
    }

    @Test
    void rejectsAWorkspaceBoundToAnotherProjectInputOrBaseline() throws Exception {
        Path firstProject = directory("first-project");
        write(firstProject, "README.md", "same");
        Path secondProject = directory("second-project");
        write(secondProject, "README.md", "same");
        Path run = directory("run");
        ShipWorkspace.prepare(firstProject, run, RUN_ID, 1, INPUT_DIGEST);

        assertThrows(IOException.class,
                () -> ShipWorkspace.prepare(secondProject, run, RUN_ID, 1, INPUT_DIGEST));
        assertThrows(IOException.class,
                () -> ShipWorkspace.prepare(
                        firstProject,
                        run,
                        "ship-fedcba9876543210fedcba9876543210",
                        1,
                        INPUT_DIGEST));
        assertThrows(IOException.class,
                () -> ShipWorkspace.prepare(
                        firstProject, run, RUN_ID, 1, digest("changed-input")));

        Files.writeString(firstProject.resolve("README.md"), "changed");
        assertThrows(IOException.class,
                () -> ShipWorkspace.prepare(firstProject, run, RUN_ID, 1, INPUT_DIGEST));
    }

    @Test
    void cleansAnUnpublishedTemporaryWorkspace() throws Exception {
        Path project = directory("project");
        write(project, "README.md", "material");
        Path run = directory("run");
        Path interrupted = Files.createDirectory(run.resolve(".workspace-interrupted"));
        Files.createDirectory(interrupted.resolve("candidate"));
        Files.writeString(interrupted.resolve("workspace.json"), "{");

        Path candidate = ShipWorkspace.prepare(project, run, RUN_ID, 1, INPUT_DIGEST);

        assertEquals(run.resolve("workspace/candidate"), candidate);
        assertFalse(Files.exists(run.resolve(".workspace-interrupted")));
    }

    @Test
    void cleansTemporaryWorkspaceWhenMaterializationFails() throws Exception {
        Path project = directory("project");
        write(project, "README.md", "material");
        Files.createSymbolicLink(
                project.resolve("unsafe-link"), project.resolve("README.md"));
        Path run = directory("run");

        assertThrows(IOException.class,
                () -> ShipWorkspace.prepare(project, run, RUN_ID, 1, INPUT_DIGEST));

        try (var entries = Files.list(run)) {
            assertTrue(entries.findAny().isEmpty());
        }
    }

    @Test
    void rejectsUnboundAndUnsafeCandidates() throws Exception {
        Path project = directory("project");
        write(project, "README.md", "material");

        Path unboundRun = directory("unbound-run");
        Files.createDirectory(unboundRun.resolve("workspace"));
        assertThrows(IOException.class,
                () -> ShipWorkspace.prepare(project, unboundRun, RUN_ID, 1, INPUT_DIGEST));

        Path linkedRun = directory("linked-run");
        Files.createSymbolicLink(linkedRun.resolve("workspace"), project);
        assertThrows(IOException.class,
                () -> ShipWorkspace.prepare(project, linkedRun, RUN_ID, 1, INPUT_DIGEST));

        Path nestedRun = Files.createDirectory(project.resolve("run"));
        assertThrows(IOException.class,
                () -> ShipWorkspace.prepare(project, nestedRun, RUN_ID, 1, INPUT_DIGEST));

        Path boundRun = directory("bound-run");
        Path candidate = ShipWorkspace.prepare(project, boundRun, RUN_ID, 1, INPUT_DIGEST);
        Files.createSymbolicLink(candidate.resolve("linked.txt"), project.resolve("README.md"));
        assertThrows(IOException.class,
                () -> ShipWorkspace.prepare(project, boundRun, RUN_ID, 1, INPUT_DIGEST));
    }

    @Test
    void permitsVolatileBuildOutputButRejectsNonPublishableWorkerAdditions() throws Exception {
        Path project = directory("project");
        write(project, "README.md", "material");
        Path run = directory("run");
        Path candidate = ShipWorkspace.prepare(project, run, RUN_ID, 1, INPUT_DIGEST);
        write(candidate, "target/generated.txt", "build output");

        assertEquals(candidate, ShipWorkspace.prepare(project, run, RUN_ID, 2, INPUT_DIGEST));

        Path nestedCredential = write(candidate, "target/.env", "TOKEN=secret");
        assertThrows(IOException.class,
                () -> ShipWorkspace.prepare(project, run, RUN_ID, 2, INPUT_DIGEST));
        Files.delete(nestedCredential);

        Path nestedPrivate = write(candidate, "target/.idea/workspace.xml", "private");
        assertThrows(IOException.class,
                () -> ShipWorkspace.prepare(project, run, RUN_ID, 2, INPUT_DIGEST));
        Files.delete(nestedPrivate);
        Files.delete(nestedPrivate.getParent());

        Path nestedLink = candidate.resolve("target/link");
        Files.createSymbolicLink(nestedLink, project.resolve("README.md"));
        assertThrows(IOException.class,
                () -> ShipWorkspace.prepare(project, run, RUN_ID, 2, INPUT_DIGEST));
        Files.delete(nestedLink);

        Path credential = write(candidate, ".env", "TOKEN=secret");
        assertThrows(IOException.class,
                () -> ShipWorkspace.prepare(project, run, RUN_ID, 2, INPUT_DIGEST));
        Files.delete(credential);

        write(candidate, ".idea/workspace.xml", "private");
        assertThrows(IOException.class,
                () -> ShipWorkspace.prepare(project, run, RUN_ID, 2, INPUT_DIGEST));
    }

    @Test
    void rejectsSymbolicRootLeavesButAcceptsBenignAncestorAliases() throws Exception {
        Path realParent = directory("real-parent");
        Path project = Files.createDirectory(realParent.resolve("project"));
        Path rootLink = temporaryDirectory.resolve("project-link");
        Files.createSymbolicLink(rootLink, project);
        Path parentLink = temporaryDirectory.resolve("parent-link");
        Files.createSymbolicLink(parentLink, realParent);
        Path realRunParent = directory("real-run-parent");
        Path run = Files.createDirectory(realRunParent.resolve("run"));
        Path runParentLink = temporaryDirectory.resolve("run-parent-link");
        Files.createSymbolicLink(runParentLink, realRunParent);

        assertThrows(IOException.class,
                () -> ShipWorkspace.prepare(
                        rootLink, directory("root-link-run"), RUN_ID, 1, INPUT_DIGEST));

        Path candidate = ShipWorkspace.prepare(
                parentLink.resolve("project"),
                runParentLink.resolve("run"),
                RUN_ID,
                1,
                INPUT_DIGEST);

        assertEquals(run.resolve("workspace/candidate").toRealPath(), candidate);
        assertEquals(candidate.toString(), ShipWorkspace.verify(
                parentLink.resolve("project"),
                runParentLink.resolve("run/workspace/candidate"),
                RUN_ID,
                1,
                INPUT_DIGEST).root());
    }

    private Path directory(String name) throws IOException {
        return Files.createDirectory(temporaryDirectory.resolve(name));
    }

    private static Path write(Path root, String relative, String content) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        return Files.writeString(file, content);
    }

    private static void assertNoWorkspaceDebris(Path run) throws IOException {
        try (var entries = Files.list(run)) {
            assertEquals(
                    java.util.List.of("workspace"),
                    entries.map(path -> String.valueOf(path.getFileName())).sorted().toList());
        }
    }

    private static String digest(String value) {
        return ShipDigest.sha256(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
