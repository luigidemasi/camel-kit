package io.github.luigidemasi.camelkit.ship.security;

import java.lang.reflect.Modifier;
import java.net.StandardProtocolFamily;
import java.net.URI;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import io.github.luigidemasi.camelkit.ship.context.ContextFilesystemPolicy.ProjectAccess;
import io.github.luigidemasi.camelkit.ship.context.ContextFilesystemPolicy.ProjectRootAdmission;
import io.github.luigidemasi.camelkit.ship.security.ProjectContextFiles.HeldRoot;
import io.github.luigidemasi.camelkit.ship.security.ShipTreePolicy.Classification;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectSnapshotServiceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void capturesMaterialProtectedAndVolatileEntriesUnderOneDigestedPolicy() throws Exception {
        Path project = project();
        write(project, "src/main/resources/routes/orders.camel.yaml", "- route:\n");
        write(project, "target/generated.txt", "volatile");
        write(project, ".git/objects/01/object", "volatile");
        write(project, ".git/HEAD", "protected");
        write(project, ".camel-kit/pipeline.json", "protected");
        write(project, ".camel-kit/config.properties", "project.runtime=main");
        write(project, ".camel-kit/.cache/generated.json", "volatile");
        write(project, ".idea/workspace.xml", "protected metadata");
        write(project, ".ssh/config", "protected");
        write(project, ".env", "protected");
        write(project, ".env.example", "material example");

        ProjectSnapshot snapshot = new ProjectSnapshotService().capture(project);

        assertEquals(ShipTreePolicy.current().digest(), snapshot.policyDigest());
        assertEquals(Classification.MATERIAL,
                snapshot.files().get("src/main/resources/routes/orders.camel.yaml").classification());
        assertEquals(Classification.PROTECTED,
                snapshot.files().get(".idea/workspace.xml").classification());
        assertEquals(Classification.MATERIAL,
                snapshot.files().get(".camel-kit/config.properties").classification());
        assertEquals(Classification.MATERIAL, snapshot.files().get(".env.example").classification());
        assertFalse(snapshot.files().containsKey("target/generated.txt"));
        assertFalse(snapshot.files().containsKey(".git/objects/01/object"));
        assertFalse(snapshot.files().containsKey(".git/HEAD"));
        assertFalse(snapshot.files().containsKey(".camel-kit/pipeline.json"));
        assertFalse(snapshot.files().containsKey(".ssh/config"));
        assertFalse(snapshot.files().containsKey(".env"));
        assertFalse(snapshot.files().containsKey(".camel-kit/.cache/generated.json"));
        assertTrue(snapshot.digest().matches("sha256:[0-9a-f]{64}"));
        assertTrue(snapshot.rootIdentity().matches("sha256:[0-9a-f]{64}"));
    }

    @Test
    void sameRootComparisonDetectsMaterialProtectedAndModeChanges() throws Exception {
        Path project = project();
        Path route = write(project, "orders.camel.yaml", "before");
        Path protectedMetadata = write(project, ".idea/workspace.xml", "before");
        ProjectSnapshotService service = new ProjectSnapshotService();
        ProjectSnapshot before = service.capture(project);

        Files.writeString(route, "after-content");
        Files.writeString(protectedMetadata, "after-content");
        Files.setPosixFilePermissions(route, PosixFilePermissions.fromString("rwx------"));

        ProjectSnapshot.Comparison changes = service.compare(before, service.capture(project));

        assertFalse(changes.unchanged());
        assertTrue(has(changes, "orders.camel.yaml", ProjectSnapshot.Change.MODIFIED));
        assertTrue(has(changes, ".idea/workspace.xml", ProjectSnapshot.Change.MODIFIED));
    }

    @Test
    void sameRootIdentitySurvivesOrdinaryEntryAdditionsAndDeletions() throws Exception {
        Path project = project();
        ProjectSnapshotService service = new ProjectSnapshotService();
        ProjectSnapshot empty = service.capture(project);

        Path added = write(project, "requirements.md", "requirements");
        ProjectSnapshot populated = service.capture(project);

        assertEquals(empty.rootIdentity(), populated.rootIdentity());
        assertTrue(has(
                service.compare(empty, populated),
                "requirements.md",
                ProjectSnapshot.Change.ADDED));

        Files.delete(added);
        ProjectSnapshot emptiedAgain = service.capture(project);

        assertEquals(empty.rootIdentity(), emptiedAgain.rootIdentity());
        assertTrue(has(
                service.compare(populated, emptiedAgain),
                "requirements.md",
                ProjectSnapshot.Change.DELETED));
    }

    @Test
    void snapshotTracksEmptyDirectoriesAndDirectoryModes() throws Exception {
        Path project = project();
        Path empty = Files.createDirectories(project.resolve("routes/empty"));
        ProjectSnapshotService service = new ProjectSnapshotService();
        ProjectSnapshot before = service.capture(project);

        Files.setPosixFilePermissions(empty, PosixFilePermissions.fromString("rwx------"));
        ProjectSnapshot.Comparison changes = service.compare(before, service.capture(project));

        assertTrue(before.directories().containsKey("routes/empty"));
        assertTrue(has(changes, "routes/empty", ProjectSnapshot.Change.MODIFIED));
    }

    @Test
    void materialComparisonExcludesProtectedMetadataButNotMaterialFiles() throws Exception {
        Path source = project();
        write(source, "README.md", "one");
        write(source, ".idea/workspace.xml", "protected-one");
        Path copy = project();
        write(copy, "README.md", "two");
        write(copy, ".idea/workspace.xml", "protected-two");
        ProjectSnapshotService service = new ProjectSnapshotService();

        ProjectSnapshot.Comparison material = service.compareContents(
                service.capture(source), service.capture(copy));
        ProjectSnapshot.Comparison exact = service.compareExactContents(
                service.capture(source), service.capture(copy));

        assertTrue(has(material, "README.md", ProjectSnapshot.Change.MODIFIED));
        assertFalse(material.differences().stream().anyMatch(change -> change.path().startsWith(".idea/")));
        assertTrue(has(exact, ".idea/workspace.xml", ProjectSnapshot.Change.MODIFIED));
    }

    @Test
    void readsOnlyBoundedMaterialFilesThroughTheHeldProjectDescriptor() throws Exception {
        Path project = project();
        Path material = write(project, "requirements.md", "requirements");
        write(project, ".camel-kit/pipeline.json", "private");
        write(project, "target/generated.txt", "generated");

        try (ShipSecureFilesystem.SecureRoot root = ShipSecureFilesystem.open(
                project, "Ship context project", ShipTreePolicy.current())) {
            assertArrayEquals(Files.readAllBytes(material), root.readMaterialBytes("requirements.md", 1024));
            assertCode(ShipFilesystemException.UNSAFE_ENTRY,
                    () -> root.readMaterialBytes(".camel-kit/pipeline.json", 1024));
            assertCode(ShipFilesystemException.UNSAFE_ENTRY,
                    () -> root.readMaterialBytes("target/generated.txt", 1024));
            assertCode(ShipFilesystemException.TREE_QUOTA_EXCEEDED,
                    () -> root.readMaterialBytes("requirements.md", 4));
        }
    }

    @Test
    void materialReadRejectsSymlinkedParentsAndHardLinks() throws Exception {
        Path project = project();
        Path outside = Files.createDirectory(temporaryDirectory.resolve("outside"));
        Path secret = write(outside, "secret.md", "secret");
        Files.createSymbolicLink(project.resolve("linked-parent"), outside);
        Files.createLink(project.resolve("hard-linked.md"), secret);

        try (ShipSecureFilesystem.SecureRoot root = ShipSecureFilesystem.open(
                project, "Ship context project", ShipTreePolicy.current())) {
            assertCode(ShipFilesystemException.UNSAFE_ENTRY,
                    () -> root.readMaterialBytes("linked-parent/secret.md", 1024));
            assertCode(ShipFilesystemException.UNSAFE_ENTRY,
                    () -> root.readMaterialBytes("hard-linked.md", 1024));
        }
    }

    @Test
    void rejectsSymbolicLinksAndHardLinksDuringSnapshot() throws Exception {
        Path symbolicProject = project();
        Path original = write(symbolicProject, "original.txt", "value");
        Files.createSymbolicLink(symbolicProject.resolve("link.txt"), original);

        assertCode(ShipFilesystemException.UNSAFE_ENTRY,
                () -> new ProjectSnapshotService().capture(symbolicProject));

        Path hardLinkProject = project();
        Path hardOriginal = write(hardLinkProject, "original.txt", "value");
        Files.createLink(hardLinkProject.resolve("hard.txt"), hardOriginal);
        assertCode(ShipFilesystemException.UNSAFE_ENTRY,
                () -> new ProjectSnapshotService().capture(hardLinkProject));
    }

    @Test
    void rejectsSymbolicRootComponents() throws Exception {
        Path realParent = Files.createDirectory(temporaryDirectory.resolve("real-parent"));
        Path project = Files.createDirectory(realParent.resolve("project"));
        Path alias = temporaryDirectory.resolve("parent-alias");
        Files.createSymbolicLink(alias, realParent);

        assertCode(ShipFilesystemException.UNSAFE_ENTRY,
                () -> new ProjectSnapshotService().capture(alias.resolve("project")));
    }

    @Test
    void rejectsSocketsFifosAndDevicesAsSpecialEntries() throws Exception {
        Path socketProject = project();
        Path socket = socketProject.resolve("worker.sock");
        try (ServerSocketChannel server = ServerSocketChannel.open(StandardProtocolFamily.UNIX)) {
            server.bind(UnixDomainSocketAddress.of(socket));
            assertCode(ShipFilesystemException.UNSAFE_ENTRY,
                    () -> new ProjectSnapshotService().capture(socketProject));
        }

        Path fifoProject = project();
        Path fifo = fifoProject.resolve("input.pipe");
        Process fifoCreation = new ProcessBuilder("mkfifo", fifo.toString()).start();
        if (!fifoCreation.waitFor(5, TimeUnit.SECONDS)) {
            fifoCreation.destroyForcibly();
            fifoCreation.waitFor(5, TimeUnit.SECONDS);
            throw new AssertionError("mkfifo did not terminate");
        }
        int fifoExit = fifoCreation.exitValue();
        String fifoError = new String(
                fifoCreation.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, fifoExit, fifoError);
        assertCode(ShipFilesystemException.UNSAFE_ENTRY,
                () -> new ProjectSnapshotService().capture(fifoProject));

        assertCode(ShipFilesystemException.UNSAFE_ENTRY,
                () -> ShipSecureFilesystem.open(
                        Path.of("/dev"), "Linux device filesystem", ShipTreePolicy.current()));
    }

    @Test
    void enforcesFileCountPerFileAndAggregateQuotas() throws Exception {
        Path project = project();
        write(project, "one.txt", "1234");
        write(project, "two.txt", "5678");

        assertCode(ShipFilesystemException.TREE_QUOTA_EXCEEDED,
                () -> new ProjectSnapshotService(ShipTreePolicy.testing(1, 8, 8)).capture(project));
        assertCode(ShipFilesystemException.TREE_QUOTA_EXCEEDED,
                () -> new ProjectSnapshotService(ShipTreePolicy.testing(2, 3, 8)).capture(project));
        assertCode(ShipFilesystemException.TREE_QUOTA_EXCEEDED,
                () -> new ProjectSnapshotService(ShipTreePolicy.testing(2, 4, 7)).capture(project));
    }

    @Test
    void deniedEntriesStillConsumeTheTraversalQuotaWithoutBeingRead() throws Exception {
        Path project = project();
        write(project, ".env", "first secret");
        write(project, ".npmrc", "second secret");

        assertCode(ShipFilesystemException.TREE_QUOTA_EXCEEDED,
                () -> new ProjectSnapshotService(ShipTreePolicy.testing(1, 1024, 4096))
                        .capture(project));
    }

    @Test
    void rejectsNoncanonicalAndDisplayUnsafePaths() {
        assertEquals("a".repeat(255),
                ShipTreePolicy.requireCanonicalRelativePath("a".repeat(255)));
        assertThrows(IllegalArgumentException.class,
                () -> ShipTreePolicy.requireCanonicalRelativePath("a".repeat(256)));
        assertThrows(IllegalArgumentException.class,
                () -> ShipTreePolicy.requireCanonicalRelativePath("é".repeat(128)));
        assertThrows(IllegalArgumentException.class,
                () -> ShipTreePolicy.requireCanonicalRelativePath("a/".repeat(2048) + "a"));
        assertThrows(IllegalArgumentException.class,
                () -> ShipTreePolicy.requireCanonicalRelativePath("../escape"));
        assertThrows(IllegalArgumentException.class,
                () -> ShipTreePolicy.requireCanonicalRelativePath("C:/escape"));
        assertThrows(IllegalArgumentException.class,
                () -> ShipTreePolicy.requireCanonicalRelativePath("line\nbreak"));
        assertThrows(IllegalArgumentException.class,
                () -> ShipTreePolicy.requireCanonicalRelativePath("spoof\u202ereversed"));
        assertThrows(IllegalArgumentException.class,
                () -> ShipTreePolicy.requireCanonicalRelativePath("zero\u200bwidth"));
        assertThrows(IllegalArgumentException.class,
                () -> ShipTreePolicy.requireCanonicalRelativePath("line\u2028separator"));
        assertThrows(IllegalArgumentException.class,
                () -> ShipTreePolicy.requireCanonicalRelativePath("paragraph\u2029separator"));
        assertThrows(IllegalArgumentException.class,
                () -> ShipTreePolicy.requireCanonicalRelativePath("unpaired-high-\ud800"));
        assertThrows(IllegalArgumentException.class,
                () -> ShipTreePolicy.requireCanonicalRelativePath("unpaired-low-\udc00"));
        assertEquals("supplementary-\ud83d\ude80",
                ShipTreePolicy.requireCanonicalRelativePath("supplementary-\ud83d\ude80"));
    }

    @Test
    void heldSnapshotRejectsLexicalRootReplacement() throws Exception {
        Path project = project();
        write(project, "orders.camel.yaml", "before");
        try (ShipSecureFilesystem.SecureRoot root = ShipSecureFilesystem.open(
                project, "Ship project", ShipTreePolicy.current())) {
            Path relocated = project.resolveSibling(project.getFileName() + "-relocated");
            Files.move(project, relocated);
            Files.createDirectory(project);
            write(project, "orders.camel.yaml", "before");

            assertCode(ShipFilesystemException.CONCURRENT_MUTATION, root::snapshot);
        }
    }

    @Test
    void sealedCaptureRejectsVolatileEntries() throws Exception {
        Path sealed = project();
        write(sealed, "target/injected.txt", "injected");

        assertCode(ShipFilesystemException.UNSAFE_ENTRY,
                () -> new ProjectSnapshotService().captureSealed(sealed));
    }

    @Test
    void sealedCaptureRejectsProtectedEntries() throws Exception {
        Path sealed = project();
        write(sealed, ".ssh/config", "credential");

        assertCode(ShipFilesystemException.UNSAFE_ENTRY,
                () -> new ProjectSnapshotService().captureSealed(sealed));
    }

    @Test
    void policySeparatesProjectConfigurationFromStateAndCaseFoldedProtectedNames() {
        ShipTreePolicy policy = ShipTreePolicy.current();

        assertEquals(
                "sha256:227072c330ffc7cae2e53a3f68b697dfa4614d8963b9461ebf91db2146c63204",
                policy.digest());
        assertEquals(Classification.MATERIAL, policy.classify(".camel-kit/config.properties"));
        assertEquals(Classification.DENIED, policy.classify(".camel-kit/pipeline.json"));
        assertEquals(Classification.DENIED, policy.classify(".CAMEL-KIT/PIPELINE.JSON"));
        assertEquals(Classification.DENIED, policy.classify(".SSH/id_rsa"));
        assertEquals(Classification.DENIED, policy.classify(".ENV"));
        assertEquals(Classification.DENIED, policy.classify("module/.m2/settings.xml"));
        assertEquals(Classification.DENIED, policy.classify("module/.gradle/gradle.properties"));
        assertEquals(Classification.DENIED, policy.classify("module/.config/gcloud/token"));
        assertEquals(Classification.DENIED, policy.classify("module/.config/gh/hosts.yml"));
        assertEquals(Classification.DENIED, policy.classify("module/.config/gh/hosts.yml/secret"));
        assertEquals(Classification.DENIED, policy.classify("module/.m2/settings.xml/secret"));
        assertEquals(Classification.DENIED, policy.classify("module/.camel-kit/pipeline.json/secret"));
        assertEquals(Classification.DENIED, policy.classify("module/.GIT/HEAD"));
        assertEquals(Classification.DENIED, policy.classify("module/.env.example/secret"));
        assertEquals(Classification.MATERIAL, policy.classify("module/.env.example"));
        assertEquals(Classification.DENIED, policy.classify("module/.GIT/objects/01/object"));
        assertEquals(Classification.DENIED, policy.classify("module/.GIT/logs/HEAD"));
        assertEquals(Classification.DENIED, policy.classify("module/.GIT/refs/heads/main"));
        assertEquals(Classification.DENIED, policy.classify("module/.GIT/FETCH_HEAD"));
        assertEquals(Classification.DENIED, policy.classify("module/.GIT/ORIG_HEAD"));
        assertEquals(Classification.DENIED, policy.classify("module/.GIT/COMMIT_EDITMSG"));
        assertEquals(Classification.DENIED, policy.classify("module/.GIT/MERGE_HEAD"));
        assertEquals(Classification.DENIED, policy.classify("module/.GIT/REBASE_HEAD"));
        assertEquals(Classification.VOLATILE, policy.classify("BUILD/TARGET/output.txt"));
    }

    @Test
    void snapshotDigestBindsTheCanonicalRoot() throws Exception {
        Path project = project();
        write(project, "requirements.md", "requirements");
        ProjectSnapshot snapshot = new ProjectSnapshotService().capture(project);
        Path otherRoot = project();

        assertThrows(IllegalArgumentException.class, () -> new ProjectSnapshot(
                snapshot.schemaVersion(),
                otherRoot.toString(),
                snapshot.rootIdentity(),
                snapshot.policyDigest(),
                snapshot.directories(),
                snapshot.files(),
                snapshot.digest()));
    }

    @Test
    void snapshotRejectsRootsThatCaptureCannotAdmitAndMissingEntryMaps() {
        String identity = "sha256:" + "0".repeat(64);
        String policyDigest = ShipTreePolicy.current().digest();
        String emptyDigest = "sha256:" + "1".repeat(64);

        for (String deniedRoot : List.of(
                "/proc",
                "/tmp/.git/project",
                "/tmp/target/project",
                "/tmp/" + "x".repeat(256))) {
            String digest = ProjectSnapshot.computeDigest(
                    deniedRoot, identity, policyDigest, Map.of(), Map.of());
            assertThrows(IllegalArgumentException.class, () -> new ProjectSnapshot(
                    ProjectSnapshot.SCHEMA_VERSION,
                    deniedRoot,
                    identity,
                    policyDigest,
                    Map.of(),
                    Map.of(),
                    digest));
        }
        String allowedRoot = temporaryDirectory.resolve("snapshot-root")
                .toAbsolutePath().normalize().toString();
        assertThrows(NullPointerException.class, () -> new ProjectSnapshot(
                ProjectSnapshot.SCHEMA_VERSION,
                allowedRoot,
                identity,
                policyDigest,
                null,
                Map.of(),
                emptyDigest));
        assertThrows(NullPointerException.class, () -> new ProjectSnapshot(
                ProjectSnapshot.SCHEMA_VERSION,
                allowedRoot,
                identity,
                policyDigest,
                Map.of(),
                null,
                emptyDigest));
    }

    @Test
    void snapshotRejectsForgedClassificationsEvenWithARecomputedDigest() throws Exception {
        Path project = project();
        write(project, "README.md", "readme");
        write(project, ".idea/workspace.xml", "protected");
        ProjectSnapshot snapshot = new ProjectSnapshotService().capture(project);

        TreeMap<String, ProjectSnapshot.FileEntry> forgedProtected = new TreeMap<>(snapshot.files());
        ProjectSnapshot.FileEntry metadata = forgedProtected.get(".idea/workspace.xml");
        forgedProtected.put(".idea/workspace.xml", new ProjectSnapshot.FileEntry(
                Classification.MATERIAL, metadata.size(), metadata.digest(),
                metadata.unixMode(), metadata.userId(), metadata.groupId()));
        String protectedDigest = ProjectSnapshot.computeDigest(
                snapshot.root(), snapshot.rootIdentity(), snapshot.policyDigest(),
                snapshot.directories(), forgedProtected);
        assertThrows(IllegalArgumentException.class, () -> new ProjectSnapshot(
                snapshot.schemaVersion(), snapshot.root(), snapshot.rootIdentity(), snapshot.policyDigest(),
                snapshot.directories(), forgedProtected, protectedDigest));

        TreeMap<String, ProjectSnapshot.FileEntry> forgedDenied = new TreeMap<>(snapshot.files());
        ProjectSnapshot.FileEntry readmeAsDenied = forgedDenied.get("README.md");
        forgedDenied.put(".git/HEAD", new ProjectSnapshot.FileEntry(
                Classification.MATERIAL, readmeAsDenied.size(), readmeAsDenied.digest(),
                readmeAsDenied.unixMode(), readmeAsDenied.userId(), readmeAsDenied.groupId()));
        String deniedDigest = ProjectSnapshot.computeDigest(
                snapshot.root(), snapshot.rootIdentity(), snapshot.policyDigest(),
                snapshot.directories(), forgedDenied);
        assertThrows(IllegalArgumentException.class, () -> new ProjectSnapshot(
                snapshot.schemaVersion(), snapshot.root(), snapshot.rootIdentity(), snapshot.policyDigest(),
                snapshot.directories(), forgedDenied, deniedDigest));

        TreeMap<String, ProjectSnapshot.FileEntry> forgedMaterial = new TreeMap<>(snapshot.files());
        ProjectSnapshot.FileEntry readme = forgedMaterial.get("README.md");
        forgedMaterial.put("README.md", new ProjectSnapshot.FileEntry(
                Classification.PROTECTED, readme.size(), readme.digest(),
                readme.unixMode(), readme.userId(), readme.groupId()));
        String materialDigest = ProjectSnapshot.computeDigest(
                snapshot.root(), snapshot.rootIdentity(), snapshot.policyDigest(),
                snapshot.directories(), forgedMaterial);
        assertThrows(IllegalArgumentException.class, () -> new ProjectSnapshot(
                snapshot.schemaVersion(), snapshot.root(), snapshot.rootIdentity(), snapshot.policyDigest(),
                snapshot.directories(), forgedMaterial, materialDigest));
    }

    @Test
    void snapshotStoresAndDigestsFullUnixModeAndOwnership() throws Exception {
        Path project = project();
        Path routes = Files.createDirectory(project.resolve("routes"));
        Path route = write(project, "routes/orders.camel.yaml", "- route:\n");
        ProjectSnapshot snapshot = new ProjectSnapshotService().capture(project);
        ProjectSnapshot.DirectoryEntry directory = snapshot.directories().get("routes");
        ProjectSnapshot.FileEntry file = snapshot.files().get("routes/orders.camel.yaml");

        assertEquals(unixInt(routes, "mode"), directory.unixMode());
        assertEquals(unixId(routes, "uid"), directory.userId());
        assertEquals(unixId(routes, "gid"), directory.groupId());
        assertEquals(unixInt(route, "mode"), file.unixMode());
        assertEquals(unixId(route, "uid"), file.userId());
        assertEquals(unixId(route, "gid"), file.groupId());

        TreeMap<String, ProjectSnapshot.FileEntry> specialModeFiles = new TreeMap<>(snapshot.files());
        specialModeFiles.put("routes/orders.camel.yaml", new ProjectSnapshot.FileEntry(
                file.classification(), file.size(), file.digest(), file.unixMode() ^ 04000,
                file.userId(), file.groupId()));
        TreeMap<String, ProjectSnapshot.FileEntry> differentOwnerFiles = new TreeMap<>(snapshot.files());
        differentOwnerFiles.put("routes/orders.camel.yaml", new ProjectSnapshot.FileEntry(
                file.classification(), file.size(), file.digest(), file.unixMode(),
                differentUnixId(file.userId()), file.groupId()));
        TreeMap<String, ProjectSnapshot.DirectoryEntry> specialModeDirectories
                = new TreeMap<>(snapshot.directories());
        specialModeDirectories.put("routes", new ProjectSnapshot.DirectoryEntry(
                directory.classification(), directory.unixMode() ^ 01000,
                directory.userId(), directory.groupId()));

        assertNotEquals(snapshot.digest(), ProjectSnapshot.computeDigest(
                snapshot.root(), snapshot.rootIdentity(), snapshot.policyDigest(),
                snapshot.directories(), specialModeFiles));
        assertNotEquals(snapshot.digest(), ProjectSnapshot.computeDigest(
                snapshot.root(), snapshot.rootIdentity(), snapshot.policyDigest(),
                snapshot.directories(), differentOwnerFiles));
        assertNotEquals(snapshot.digest(), ProjectSnapshot.computeDigest(
                snapshot.root(), snapshot.rootIdentity(), snapshot.policyDigest(),
                specialModeDirectories, snapshot.files()));
        assertThrows(IllegalArgumentException.class,
                () -> new ProjectSnapshot.DirectoryEntry(
                        Classification.MATERIAL, 0100755, directory.userId(), directory.groupId()));
        assertThrows(IllegalArgumentException.class,
                () -> new ProjectSnapshot.FileEntry(
                        Classification.MATERIAL, 0, file.digest(), 0040644,
                        file.userId(), file.groupId()));
        assertThrows(IllegalArgumentException.class,
                () -> new ProjectSnapshot.FileEntry(
                        Classification.MATERIAL, 0, file.digest(), 0100644, -1, file.groupId()));
    }

    @Test
    void snapshotConstructorRejectsAnImpossibleDeepTree() {
        String root = temporaryDirectory.resolve("snapshot-root").toAbsolutePath().normalize().toString();
        String rootIdentity = "sha256:" + "0".repeat(64);
        String policyDigest = ShipTreePolicy.current().digest();
        String deepPath = "d/".repeat(ShipTreePolicy.DEFAULT_MAX_DEPTH + 1) + "file.txt";
        ProjectSnapshot.FileEntry entry = new ProjectSnapshot.FileEntry(
                Classification.MATERIAL, 0, "sha256:" + "1".repeat(64),
                0100644, 1000, 1000);
        Map<String, ProjectSnapshot.FileEntry> files = Map.of(deepPath, entry);
        String digest = ProjectSnapshot.computeDigest(
                root, rootIdentity, policyDigest, Map.of(), files);

        assertThrows(IllegalArgumentException.class, () -> new ProjectSnapshot(
                ProjectSnapshot.SCHEMA_VERSION, root, rootIdentity, policyDigest,
                Map.of(), files, digest));
    }

    @Test
    void snapshotBoundsPreflightAndDishonestMapIteration() {
        String root = temporaryDirectory.resolve("snapshot-root").toAbsolutePath().normalize().toString();
        String identity = "sha256:" + "0".repeat(64);
        String policyDigest = ShipTreePolicy.current().digest();
        String placeholderDigest = "sha256:" + "1".repeat(64);
        ProjectSnapshot.FileEntry entry = new ProjectSnapshot.FileEntry(
                Classification.MATERIAL, 0, placeholderDigest, 0100644, 1000, 1000);
        Map<String, ProjectSnapshot.FileEntry> preflightOversized = new AbstractMap<>() {
            @Override
            public int size() {
                return ShipTreePolicy.DEFAULT_MAX_FILE_COUNT + 1;
            }

            @Override
            public Set<Entry<String, ProjectSnapshot.FileEntry>> entrySet() {
                throw new AssertionError("Oversized map must be rejected before iteration");
            }
        };
        IllegalArgumentException preflight = assertThrows(IllegalArgumentException.class,
                () -> new ProjectSnapshot(
                        ProjectSnapshot.SCHEMA_VERSION, root, identity,
                        policyDigest, Map.of(), preflightOversized, placeholderDigest));
        assertEquals("Project snapshot exceeds the maximum entry count", preflight.getMessage());

        IllegalArgumentException dishonest = assertThrows(IllegalArgumentException.class,
                () -> new ProjectSnapshot(
                        ProjectSnapshot.SCHEMA_VERSION, root, identity,
                        policyDigest, Map.of(), dishonestMap(entry), placeholderDigest));
        assertEquals("Project snapshot exceeds the maximum entry count", dishonest.getMessage());
    }

    @Test
    void enforcesTraversalDepthBeforeOpeningTheNextDirectory() throws Exception {
        Path project = project();
        write(project, "one/two/three/requirements.md", "requirements");

        assertCode(ShipFilesystemException.TREE_QUOTA_EXCEEDED,
                () -> new ProjectSnapshotService(
                        ShipTreePolicy.testing(20, 1024, 4096, 2))
                        .capture(project));
    }

    @Test
    void repeatedCapturesAndPureComparisonAreDeterministic() throws Exception {
        Path firstRoot = project();
        write(firstRoot, "a/requirements.md", "requirements");
        ProjectSnapshotService service = new ProjectSnapshotService();
        ProjectSnapshot first = service.capture(firstRoot);
        ProjectSnapshot repeated = service.capture(firstRoot);

        Path secondRoot = project();
        write(secondRoot, "a/requirements.md", "requirements");
        ProjectSnapshot second = service.capture(secondRoot);

        assertEquals(first, repeated);
        assertTrue(service.compare(first, repeated).unchanged());
        assertTrue(service.compareMaterialTree(first, second).unchanged());
    }

    @Test
    void publicProjectFacadeRequiresAdmittedAccessAndRawBrokerTypesRemainInternal() throws Exception {
        Set<String> publicMethods = Arrays.stream(ProjectContextFiles.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(method -> method.getName())
                .collect(Collectors.toSet());
        assertEquals(Set.of("hold", "open", "readDocument", "close"), publicMethods);
        assertEquals(
                ProjectAccess.class,
                ProjectContextFiles.class.getMethod("open", ProjectAccess.class).getParameterTypes()[0]);
        assertThrows(NoSuchMethodException.class, () -> ProjectContextFiles.class.getMethod("open", Path.class));
        assertThrows(NoSuchMethodException.class, () -> ProjectContextFiles.class.getMethod("snapshot"));
        assertEquals(
                ProjectRootAdmission.class,
                ProjectContextFiles.class.getMethod("hold", ProjectRootAdmission.class)
                        .getParameterTypes()[0]);
        assertEquals(0, ProjectRootAdmission.class.getConstructors().length);
        assertEquals(0, HeldRoot.class.getConstructors().length);
        assertFalse(Modifier.isPublic(ShipSecureFilesystem.class.getModifiers()));
        assertFalse(Modifier.isPublic(ShipSecureFilesystem.SecureRoot.class.getModifiers()));
        assertFalse(Modifier.isPublic(ProjectSnapshotService.class.getModifiers()));
        assertTrue(Arrays.stream(ShipFilesystemException.class.getDeclaredConstructors())
                .noneMatch(constructor -> Modifier.isPublic(constructor.getModifiers())));
    }

    @Test
    void nonSecureDirectoryStreamFailsClosed() throws Exception {
        Path archive = temporaryDirectory.resolve("tree.zip");
        URI uri = URI.create("jar:" + archive.toUri());
        try (FileSystem zip = FileSystems.newFileSystem(uri, Map.of("create", "true"))) {
            Path project = Files.createDirectory(zip.getPath("/project"));
            Files.writeString(project.resolve("sentinel.txt"), "unchanged");

            assertCode(ShipFilesystemException.SECURE_FILESYSTEM_UNSUPPORTED,
                    () -> new ProjectSnapshotService().capture(project));
            assertEquals("unchanged", Files.readString(project.resolve("sentinel.txt")));
        }
    }

    private static Map<String, ProjectSnapshot.FileEntry> dishonestMap(
            ProjectSnapshot.FileEntry entry) {
        return new AbstractMap<>() {
            @Override
            public Set<Entry<String, ProjectSnapshot.FileEntry>> entrySet() {
                return new AbstractSet<>() {
                    @Override
                    public Iterator<Entry<String, ProjectSnapshot.FileEntry>> iterator() {
                        return new Iterator<>() {
                            private int index;

                            @Override
                            public boolean hasNext() {
                                return index <= ShipTreePolicy.DEFAULT_MAX_FILE_COUNT;
                            }

                            @Override
                            public Entry<String, ProjectSnapshot.FileEntry> next() {
                                if (!hasNext()) {
                                    throw new java.util.NoSuchElementException();
                                }
                                return Map.entry("entry-" + index++, entry);
                            }
                        };
                    }

                    @Override
                    public int size() {
                        return 0;
                    }
                };
            }
        };
    }

    private static int unixInt(Path path, String attribute) throws Exception {
        return ((Number) Files.getAttribute(path, "unix:" + attribute)).intValue();
    }

    private static long unixId(Path path, String attribute) throws Exception {
        Number value = (Number) Files.getAttribute(path, "unix:" + attribute);
        return value instanceof Integer integer
                ? Integer.toUnsignedLong(integer)
                : value.longValue();
    }

    private static long differentUnixId(long value) {
        return value == 0xffff_ffffL ? value - 1 : value + 1;
    }

    private Path project() throws Exception {
        return Files.createDirectory(temporaryDirectory.resolve("project-" + System.nanoTime()));
    }

    private static Path write(Path root, String relative, String content) throws Exception {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        return Files.writeString(file, content);
    }

    private static boolean has(
            ProjectSnapshot.Comparison comparison, String path, ProjectSnapshot.Change change) {
        return comparison.differences().stream()
                .anyMatch(difference -> path.equals(difference.path()) && change == difference.change());
    }

    private static void assertCode(String expected, ThrowingOperation operation) {
        ShipFilesystemException error = assertThrows(ShipFilesystemException.class, operation::run);
        assertEquals(expected, error.code());
    }

    @FunctionalInterface
    private interface ThrowingOperation {
        void run() throws Exception;
    }
}
