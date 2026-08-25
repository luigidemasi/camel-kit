package io.github.luigidemasi.camelkit.ship.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

import io.github.luigidemasi.camelkit.ship.security.ProjectEvidenceFiles;
import io.github.luigidemasi.camelkit.ship.security.ProjectSnapshot;
import io.github.luigidemasi.camelkit.ship.worker.ShipWorkspace;

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
class ShipPublicationServiceTest {

    private static final String RUN_ID = "ship-00000000000000000000000000000001";
    private static final int ATTEMPT = 1;
    private static final String CREATED_AT = "2026-08-17T12:00:00Z";

    @TempDir
    Path temporaryDirectory;

    @Test
    void preservesLegacyMaterialAndDeniedTemporaryNames() throws Exception {
        String materialTemporary = ".foo.camel-kit-publish.tmp";
        String deniedTemporary = ".env.camel-kit-publish.tmp";
        Fixture fixture = fixture("legacy-temporary", project -> {
            write(project.resolve("foo"), "old");
            write(project.resolve("env"), "old environment");
            write(project.resolve(materialTemporary), "material sentinel");
            setMode(project.resolve(materialTemporary), "rw-r-----");
            write(project.resolve(deniedTemporary), "denied sentinel");
            setMode(project.resolve(deniedTemporary), "rw-------");
        }, candidate -> {
            write(candidate.resolve("foo"), "new");
            write(candidate.resolve("env"), "new environment");
        });
        byte[] material = Files.readAllBytes(fixture.live(materialTemporary));
        byte[] denied = Files.readAllBytes(fixture.live(deniedTemporary));
        Set<PosixFilePermission> materialMode = mode(fixture.live(materialTemporary));
        Set<PosixFilePermission> deniedMode = mode(fixture.live(deniedTemporary));
        fixture.begin();

        ShipPublicationService.apply(
                fixture.project(), fixture.candidate(), fixture.run(), fixture.journal());

        assertEquals("new", Files.readString(fixture.live("foo")));
        assertEquals("new environment", Files.readString(fixture.live("env")));
        assertArrayEquals(material, Files.readAllBytes(fixture.live(materialTemporary)));
        assertArrayEquals(denied, Files.readAllBytes(fixture.live(deniedTemporary)));
        assertEquals(materialMode, mode(fixture.live(materialTemporary)));
        assertEquals(deniedMode, mode(fixture.live(deniedTemporary)));
        assertMaterialTree(fixture.candidateSnapshot(), fixture.project());
    }

    @Test
    void publishesAndRecoversAValidTwoHundredFortyByteLeaf() throws Exception {
        String leaf = "x".repeat(240);
        Fixture fixture = fixture("long-leaf", project -> {
            write(project.resolve(leaf), "old");
        }, candidate -> {
            write(candidate.resolve(leaf), "new");
        });
        fixture.begin();

        ShipPublicationService.apply(
                fixture.project(), fixture.candidate(), fixture.run(), fixture.journal());

        assertEquals("new", Files.readString(fixture.live(leaf)));
        assertMaterialTree(fixture.candidateSnapshot(), fixture.project());

        ShipPublicationService.recover(
                fixture.project(), fixture.run(), RUN_ID, ATTEMPT);

        assertEquals("old", Files.readString(fixture.live(leaf)));
        assertMaterialTree(fixture.baseline(), fixture.project());
        assertFalse(ShipPublicationService.journalExists(fixture.run()));
    }

    @Test
    void recoversABaselineFileModeFromAnyIntermediateMode() throws Exception {
        Fixture fixture = fixture("third-file-mode", project -> {
            write(project.resolve("mode.txt"), "same bytes");
            setMode(project.resolve("mode.txt"), "rw-r--r--");
        }, candidate -> {
            setMode(candidate.resolve("mode.txt"), "rw-------");
        });
        fixture.begin();
        backup(fixture, "mode.txt");
        setMode(fixture.live("mode.txt"), "rw-rw----");

        ShipPublicationService.recover(fixture.project(), fixture.run(), RUN_ID, ATTEMPT);

        assertEquals("same bytes", Files.readString(fixture.live("mode.txt")));
        assertMode(fixture.live("mode.txt"), "rw-r--r--");
        assertFalse(ShipPublicationService.journalExists(fixture.run()));
    }

    @Test
    void recoversABaselineDirectoryModeFromAnyIntermediateMode() throws Exception {
        Fixture fixture = fixture("third-directory-mode", project -> {
            Path config = Files.createDirectory(project.resolve("config"));
            setMode(config, "rwxr-xr-x");
        }, candidate -> {
            setMode(candidate.resolve("config"), "rwx------");
        });
        fixture.begin();
        setMode(fixture.live("config"), "rwxr-x---");

        ShipPublicationService.recover(fixture.project(), fixture.run(), RUN_ID, ATTEMPT);

        assertMode(fixture.live("config"), "rwxr-xr-x");
        assertFalse(ShipPublicationService.journalExists(fixture.run()));
    }

    @Test
    void recoversABaselineDirectoryModeAfterRecoveryStarted() throws Exception {
        Fixture fixture = fixture("third-recovery-directory-mode", project -> {
            Path config = Files.createDirectory(project.resolve("config"));
            setMode(config, "rwxr-xr-x");
        }, candidate -> {
            setMode(candidate.resolve("config"), "rwx------");
        });
        fixture.begin();
        ShipPublicationService.startRecovery(fixture.run(), fixture.journal(), false);
        setMode(fixture.live("config"), "rwxr-x---");

        ShipPublicationService.recover(fixture.project(), fixture.run(), RUN_ID, ATTEMPT);

        assertMode(fixture.live("config"), "rwxr-xr-x");
        assertFalse(ShipPublicationService.journalExists(fixture.run()));
    }

    @Test
    void blocksOnAStableDescendantSymlinkWithoutTouchingItOrItsTarget()
            throws Exception {
        Fixture fixture = fixture("descendant-symlink", project -> {
            write(project.resolve("replace.txt"), "old");
        }, candidate -> {
            write(candidate.resolve("replace.txt"), "new");
        });
        Path outside = temporaryDirectory.resolve("outside.txt");
        write(outside, "outside content");
        setMode(outside, "rw-r-----");
        byte[] outsideContent = Files.readAllBytes(outside);
        Set<PosixFilePermission> outsideMode = mode(outside);
        fixture.begin();
        Files.delete(fixture.live("replace.txt"));
        Files.createSymbolicLink(fixture.live("replace.txt"), outside);

        assertThrows(
                ShipPublicationService.RecoveryBlockedException.class,
                () -> ShipPublicationService.recover(
                        fixture.project(), fixture.run(), RUN_ID, ATTEMPT));

        assertTrue(Files.isSymbolicLink(fixture.live("replace.txt")));
        assertEquals(outside, Files.readSymbolicLink(fixture.live("replace.txt")));
        assertArrayEquals(outsideContent, Files.readAllBytes(outside));
        assertEquals(outsideMode, mode(outside));
        assertTrue(ShipPublicationService.journalExists(fixture.run()));
    }

    @Test
    void blocksOnAStableSymlinkedParentWithoutDeletingOutsideTheProject()
            throws Exception {
        Fixture fixture = fixture("symlinked-parent", project -> {
        }, candidate -> {
            Path generated = Files.createDirectory(candidate.resolve("generated"));
            write(generated.resolve("result.txt"), "candidate content");
        });
        Path outside = Files.createDirectory(temporaryDirectory.resolve("outside-directory"));
        write(outside.resolve("result.txt"), "candidate content");
        setMode(
                outside.resolve("result.txt"),
                PosixFilePermissions.toString(mode(fixture.candidate().resolve(
                        "generated/result.txt"))));
        fixture.begin();
        Files.createSymbolicLink(fixture.live("generated"), outside);

        assertThrows(
                ShipPublicationService.RecoveryBlockedException.class,
                () -> ShipPublicationService.recover(
                        fixture.project(), fixture.run(), RUN_ID, ATTEMPT));

        assertTrue(Files.isSymbolicLink(fixture.live("generated")));
        assertEquals("candidate content", Files.readString(outside.resolve("result.txt")));
        assertTrue(ShipPublicationService.journalExists(fixture.run()));
    }

    @Test
    void publishesAndRecoversAReadOnlyAddedDirectoryWithAChild() throws Exception {
        Fixture fixture = fixture("read-only-directory", project -> {
        }, candidate -> {
            Path generated = Files.createDirectory(candidate.resolve("generated"));
            write(generated.resolve("result.txt"), "candidate content");
            setMode(generated, "r-xr-xr-x");
        });
        fixture.begin();

        ShipPublicationService.apply(
                fixture.project(), fixture.candidate(), fixture.run(), fixture.journal());

        assertEquals("candidate content", Files.readString(fixture.live("generated/result.txt")));
        assertMode(fixture.live("generated"), "r-xr-xr-x");

        ShipPublicationService.recover(
                fixture.project(), fixture.run(), RUN_ID, ATTEMPT);

        assertFalse(Files.exists(fixture.live("generated"), LinkOption.NOFOLLOW_LINKS));
        assertMaterialTree(fixture.baseline(), fixture.project());
    }

    @Test
    void recoversAReadOnlyReplacementDirectoryWithAChangedChild() throws Exception {
        Fixture fixture = fixture("read-only-replacement-directory", project -> {
            Path config = Files.createDirectory(project.resolve("config"));
            write(config.resolve("a.txt"), "old");
            setMode(config, "rwxr-xr-x");
        }, candidate -> {
            write(candidate.resolve("config/a.txt"), "new");
            setMode(candidate.resolve("config"), "r-xr-xr-x");
        });
        fixture.begin();
        ShipPublicationService.apply(
                fixture.project(), fixture.candidate(), fixture.run(), fixture.journal());

        assertEquals("new", Files.readString(fixture.live("config/a.txt")));
        assertMode(fixture.live("config"), "r-xr-xr-x");

        ShipPublicationService.recover(
                fixture.project(), fixture.run(), RUN_ID, ATTEMPT);

        assertEquals("old", Files.readString(fixture.live("config/a.txt")));
        assertMode(fixture.live("config"), "rwxr-xr-x");
        assertMaterialTree(fixture.baseline(), fixture.project());
        assertFalse(ShipPublicationService.journalExists(fixture.run()));
    }

    @Test
    void blocksRecoveryWhenTheProjectRootWasReplaced() throws Exception {
        Fixture fixture = fixture("replaced-root", project -> {
            write(project.resolve("replace.txt"), "old");
            setMode(project.resolve("replace.txt"), "rw-r-----");
        }, candidate -> {
            write(candidate.resolve("replace.txt"), "new");
        });
        fixture.begin();
        Path original = temporaryDirectory.resolve("replaced-root-original");
        Files.move(fixture.project(), original, StandardCopyOption.ATOMIC_MOVE);
        Files.createDirectory(fixture.project());
        write(fixture.live("replace.txt"), "old");
        setMode(fixture.live("replace.txt"), "rw-r-----");
        assertMaterialTree(fixture.baseline(), fixture.project());

        assertThrows(
                ShipPublicationService.RecoveryBlockedException.class,
                () -> ShipPublicationService.recover(
                        fixture.project(), fixture.run(), RUN_ID, ATTEMPT));

        assertEquals("old", Files.readString(fixture.live("replace.txt")));
        assertEquals("old", Files.readString(original.resolve("replace.txt")));
        assertMaterialTree(fixture.baseline(), fixture.project());
        assertTrue(ShipPublicationService.journalExists(fixture.run()));
    }

    @Test
    void blocksRecoveryOfACandidateOnlyDirectoryContainingDeniedContent()
            throws Exception {
        Fixture fixture = fixture("foreign-denied-child", project -> {
        }, candidate -> {
            Path generated = Files.createDirectory(candidate.resolve("generated"));
            setMode(generated, "rwxr-x---");
        });
        fixture.begin();
        Path generated = Files.createDirectory(fixture.live("generated"));
        setMode(generated, "rwxr-x---");
        Path denied = generated.resolve(".env");
        write(denied, "foreign secret");
        setMode(denied, "rw-------");
        byte[] deniedContent = Files.readAllBytes(denied);

        assertThrows(
                ShipPublicationService.RecoveryBlockedException.class,
                () -> ShipPublicationService.recover(
                        fixture.project(), fixture.run(), RUN_ID, ATTEMPT));

        assertTrue(Files.isDirectory(generated, LinkOption.NOFOLLOW_LINKS));
        assertMode(generated, "rwxr-x---");
        assertArrayEquals(deniedContent, Files.readAllBytes(denied));
        assertMode(denied, "rw-------");
        assertTrue(ShipPublicationService.journalExists(fixture.run()));
    }

    @Test
    void rejectsAndPreservesAForeignScratchCollisionCreatedAfterBegin() throws Exception {
        Fixture fixture = fixture("scratch-collision", project -> {
            write(project.resolve("replace.txt"), "old");
        }, candidate -> {
            write(candidate.resolve("replace.txt"), "new");
        });
        fixture.begin();
        Path scratch = fixture.staging("replace.txt");
        Files.write(
                scratch,
                new byte[0],
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE);
        setMode(scratch, "rw-------");
        byte[] scratchContent = Files.readAllBytes(scratch);
        Set<PosixFilePermission> scratchMode = mode(scratch);

        assertThrows(
                ShipPublicationService.StaleLiveTreeException.class,
                () -> ShipPublicationService.apply(
                        fixture.project(), fixture.candidate(), fixture.run(), fixture.journal()));

        assertEquals("old", Files.readString(fixture.live("replace.txt")));
        assertArrayEquals(scratchContent, Files.readAllBytes(scratch));
        assertEquals(scratchMode, mode(scratch));
        assertFalse(ShipPublicationService.journalExists(fixture.run()));
    }

    @Test
    void recoveryBeforeApplicationDeletesOwnedScratch() throws Exception {
        Fixture fixture = fixture("pre-application-scratch", project -> {
            write(project.resolve("replace.txt"), "old");
        }, candidate -> {
            write(candidate.resolve("replace.txt"), "new");
        });
        fixture.begin();
        Path scratch = fixture.staging("replace.txt");
        Files.write(
                scratch,
                new byte[0],
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE);
        setMode(scratch, "rw-------");

        ShipPublicationService.recover(fixture.project(), fixture.run(), RUN_ID, ATTEMPT);

        assertEquals("old", Files.readString(fixture.live("replace.txt")));
        assertFalse(Files.exists(scratch, LinkOption.NOFOLLOW_LINKS));
        assertFalse(ShipPublicationService.journalExists(fixture.run()));
    }

    @Test
    void recoveryRecognizesAnInterruptedDeleteProbeAfterApplicationStarted() throws Exception {
        Fixture fixture = fixture("delete-probe", project -> {
            write(project.resolve("deleted.txt"), "baseline");
        }, candidate -> {
            Files.delete(candidate.resolve("deleted.txt"));
        });
        fixture.begin();
        backup(fixture, "deleted.txt");
        ShipPublicationService.startApplication(fixture.run(), fixture.journal());
        Path scratch = fixture.staging("deleted.txt");
        Files.write(
                scratch,
                new byte[0],
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE);
        setMode(scratch, "rw-------");

        ShipPublicationService.recover(
                fixture.project(), fixture.run(), RUN_ID, ATTEMPT);

        assertEquals("baseline", Files.readString(fixture.live("deleted.txt")));
        assertFalse(Files.exists(scratch, LinkOption.NOFOLLOW_LINKS));
        assertMaterialTree(fixture.baseline(), fixture.project());
        assertFalse(ShipPublicationService.journalExists(fixture.run()));
    }

    @Test
    void recoveryRecognizesAnInterruptedBaselineRestore() throws Exception {
        Fixture fixture = fixture("interrupted-baseline-restore", project -> {
            write(project.resolve("first.txt"), "old first");
            write(project.resolve("second.txt"), "old second");
        }, candidate -> {
            write(candidate.resolve("first.txt"), "new first");
            write(candidate.resolve("second.txt"), "new second");
        });
        fixture.begin();
        backup(fixture, "first.txt");
        backup(fixture, "second.txt");
        ShipPublicationService.startApplication(fixture.run(), fixture.journal());
        Files.write(
                fixture.live("first.txt"),
                Files.readAllBytes(fixture.candidate().resolve("first.txt")),
                StandardOpenOption.TRUNCATE_EXISTING);
        Files.setPosixFilePermissions(
                fixture.live("first.txt"), mode(fixture.candidate().resolve("first.txt")));
        ShipPublicationService.startRecovery(fixture.run(), fixture.journal(), false);
        Path scratch = fixture.staging("first.txt");
        Files.writeString(scratch, "old", StandardOpenOption.CREATE_NEW);
        setMode(scratch, "rw-------");

        ShipPublicationService.recover(
                fixture.project(), fixture.run(), RUN_ID, ATTEMPT);

        assertMaterialTree(fixture.baseline(), fixture.project());
        assertEquals("old first", Files.readString(fixture.live("first.txt")));
        assertEquals("old second", Files.readString(fixture.live("second.txt")));
        assertFalse(Files.exists(scratch, LinkOption.NOFOLLOW_LINKS));
        assertFalse(ShipPublicationService.journalExists(fixture.run()));
    }

    @Test
    void publishesAndRecoversReplacementsToAndFromEmptyFiles() throws Exception {
        Fixture fixture = fixture("empty-files", project -> {
            write(project.resolve("from-empty.txt"), "");
            write(project.resolve("to-empty.txt"), "non-empty");
        }, candidate -> {
            write(candidate.resolve("from-empty.txt"), "now populated");
            write(candidate.resolve("to-empty.txt"), "");
        });
        fixture.begin();

        ShipPublicationService.apply(
                fixture.project(), fixture.candidate(), fixture.run(), fixture.journal());

        assertEquals("now populated", Files.readString(fixture.live("from-empty.txt")));
        assertEquals(0, Files.size(fixture.live("to-empty.txt")));
        assertMaterialTree(fixture.candidateSnapshot(), fixture.project());

        ShipPublicationService.recover(
                fixture.project(), fixture.run(), RUN_ID, ATTEMPT);

        assertEquals(0, Files.size(fixture.live("from-empty.txt")));
        assertEquals("non-empty", Files.readString(fixture.live("to-empty.txt")));
        assertMaterialTree(fixture.baseline(), fixture.project());
        assertFalse(ShipPublicationService.journalExists(fixture.run()));
    }

    @Test
    void recoveryPreflightsAnEditedUnchangedPathBeforeRestoringAnything() throws Exception {
        Fixture fixture = fixture("edited-unchanged-path", project -> {
            write(project.resolve("keep.txt"), "keep");
            write(project.resolve("replace.txt"), "old");
        }, candidate -> {
            write(candidate.resolve("replace.txt"), "new");
        });
        fixture.begin();
        ShipPublicationService.apply(
                fixture.project(), fixture.candidate(), fixture.run(), fixture.journal());
        write(fixture.live("keep.txt"), "user edit");
        ProjectSnapshot beforeRecovery = ProjectEvidenceFiles.capture(fixture.project());

        assertThrows(
                ShipPublicationService.RecoveryBlockedException.class,
                () -> ShipPublicationService.recover(
                        fixture.project(), fixture.run(), RUN_ID, ATTEMPT));

        assertMaterialTree(beforeRecovery, fixture.project());
        assertEquals("new", Files.readString(fixture.live("replace.txt")));
        assertEquals("user edit", Files.readString(fixture.live("keep.txt")));
        assertTrue(ShipPublicationService.journalExists(fixture.run()));
    }

    @Test
    void treatsAnIndeterminateJournalLookupAsPresent() throws Exception {
        Path run = Files.createDirectory(temporaryDirectory.resolve("indeterminate-journal-run"));
        Path publication = Files.createDirectory(
                ShipPublicationService.publicationDirectory(run));
        Files.writeString(publication.resolve(ShipPublicationService.JOURNAL), "{}");
        setMode(publication, "---------");
        try {
            assertTrue(ShipPublicationService.journalExists(run));
        } finally {
            setMode(publication, "rwx------");
        }
    }

    private Fixture fixture(
            String name, Edit baselineEdit, Edit candidateEdit)
            throws Exception {
        Path project = Files.createDirectory(temporaryDirectory.resolve(name + "-project"));
        baselineEdit.apply(project);
        ProjectSnapshot baseline = ProjectEvidenceFiles.capture(project);
        Path run = Files.createDirectory(temporaryDirectory.resolve(name + "-run"));
        Path candidate = Files.createDirectories(run.resolve("workspace/candidate"));
        ProjectEvidenceFiles.materializeMaterial(project, candidate);
        candidateEdit.apply(candidate);
        ProjectSnapshot candidateSnapshot = ProjectEvidenceFiles.captureStaged(candidate);
        ShipPublicationService.Journal journal = ShipPublicationService.plan(
                RUN_ID,
                ATTEMPT,
                CREATED_AT,
                new ShipWorkspace.Verification(baseline, candidateSnapshot));
        return new Fixture(project, candidate, run, baseline, candidateSnapshot, journal);
    }

    private static Path backup(Fixture fixture, String relative) throws IOException {
        Path destination = ShipPublicationService.publicationDirectory(fixture.run())
                .resolve(ShipPublicationService.BACKUP)
                .resolve(relative);
        Files.createDirectories(destination.getParent());
        return Files.copy(fixture.live(relative), destination);
    }

    private static Path write(Path path, String content) throws IOException {
        return Files.writeString(path, content);
    }

    private static void setMode(Path path, String permissions) throws IOException {
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(permissions));
    }

    private static Set<PosixFilePermission> mode(Path path) throws IOException {
        return Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS);
    }

    private static void assertMode(Path path, String expected) throws IOException {
        assertEquals(PosixFilePermissions.fromString(expected), mode(path));
    }

    private static void assertMaterialTree(ProjectSnapshot expected, Path actual)
            throws IOException {
        assertTrue(
                ProjectEvidenceFiles.unchangedMaterialTree(
                        expected, ProjectEvidenceFiles.capture(actual)),
                "material tree differs from the expected snapshot");
    }

    @FunctionalInterface
    private interface Edit {
        void apply(Path root) throws Exception;
    }

    private record Fixture(
            Path project,
            Path candidate,
            Path run,
            ProjectSnapshot baseline,
            ProjectSnapshot candidateSnapshot,
            ShipPublicationService.Journal journal) {

        void begin() throws IOException {
            ShipPublicationService.begin(run, journal);
        }

        Path live(String relative) {
            return project.resolve(relative);
        }

        int index(String relative) {
            for (int index = 0; index < journal.entries().size(); index++) {
                if (relative.equals(journal.entries().get(index).path())) {
                    return index;
                }
            }
            throw new AssertionError("Missing publication entry: " + relative);
        }

        Path staging(String relative) {
            return ShipPublicationService.stagingPath(
                    journal, index(relative), live(relative));
        }
    }
}
