package io.github.luigidemasi.camelkit.ship.controller;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import io.github.luigidemasi.camelkit.ship.ShipDigest;
import io.github.luigidemasi.camelkit.ship.controller.ShipBlobStore.BlobReference;
import io.github.luigidemasi.camelkit.ship.protocol.ProducedArtifact;
import io.github.luigidemasi.camelkit.ship.protocol.StageResult;
import io.github.luigidemasi.camelkit.ship.security.ProjectEvidenceFiles;
import io.github.luigidemasi.camelkit.ship.security.ProjectSnapshot;
import io.github.luigidemasi.camelkit.ship.security.ProjectSnapshot.FileEntry;
import io.github.luigidemasi.camelkit.ship.security.ShipTreePolicy;
import io.github.luigidemasi.camelkit.ship.security.ShipTreePolicy.Classification;

/** Write-ahead, replay-recoverable material publication for one validated candidate. */
final class ShipPublicationService {

    private static final int SCHEMA_VERSION = 1;
    private static final String JOURNAL = "live-publication.json";

    private ShipPublicationService() {
    }

    static LivePublication apply(
            ShipRunView run,
            ShipBlobStore blobs,
            BlobReference stamp,
            ShipEventType completionType)
            throws IOException {
        if (run.baselineSnapshot() == null
                || run.candidateSnapshot() == null
                || run.executionResult() == null
                || run.candidateDirectory() == null
                || run.sourceDirectory() == null
                || stamp == null
                || !"ship-stamp".equals(stamp.kind())
                || completionType != ShipEventType.RUN_COMPLETED
                        && completionType != ShipEventType.RUN_COMPLETED_WITH_WAIVER) {
            throw new IOException("Ship publication lacks its exact durable inputs");
        }
        blobs.verify(stamp);
        Path publicationRoot = blobs.privateWorkDirectory("publication");
        Path journal = publicationRoot.resolve(JOURNAL);
        if (Files.exists(journal, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("A Ship live publication already requires recovery");
        }
        ProjectSnapshot baseline = read(
                blobs, run.baselineSnapshot(), ProjectSnapshot.class);
        ProjectSnapshot candidate = read(
                blobs, run.candidateSnapshot(), ProjectSnapshot.class);
        Path candidateDirectory = blobs.verifyCandidateDirectory(run.candidateDirectory());
        ProjectSnapshot sealed = ProjectEvidenceFiles.captureSealed(candidateDirectory);
        if (!candidate.equals(sealed)) {
            throw new IOException("Sealed publication candidate changed before live apply");
        }
        ProjectSnapshot live = ProjectEvidenceFiles.capture(run.projectRoot());
        if (!ProjectEvidenceFiles.unchangedMaterialTree(baseline, live)) {
            throw new IOException("Live project differs from the run-start publication baseline");
        }
        StageResult execution = read(blobs, run.executionResult(), StageResult.class);
        List<Artifact> artifacts = execution.artifacts().stream()
                .map(artifact -> new Artifact(
                        artifact,
                        new BlobReference(artifact.kind(), artifact.digest(), artifact.size())))
                .toList();
        Intent intent = new Intent(
                SCHEMA_VERSION,
                run.runId(),
                run.eventDigest(),
                run.authority().revision(),
                run.projectRoot().toString(),
                run.sourceDirectory().toString(),
                candidateDirectory.toString(),
                run.baselineSnapshot(),
                run.candidateSnapshot(),
                run.executionResult(),
                stamp,
                completionType,
                artifacts);
        writeJournal(journal, intent);
        try {
            applyArtifacts(run.projectRoot(), candidate, blobs, artifacts);
            ProjectSnapshot published = ProjectEvidenceFiles.capture(run.projectRoot());
            if (!ProjectEvidenceFiles.unchangedMaterialTree(candidate, published)) {
                throw new IOException("Published project differs from the validated candidate");
            }
            return new LivePublication(journal, intent, baseline, candidate, published);
        } catch (IOException | RuntimeException failure) {
            try {
                rollback(intent, baseline, candidate);
                Files.deleteIfExists(journal);
                forceDirectory(publicationRoot);
            } catch (IOException cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    static void recover(
            ShipRunView run, ShipBlobStore blobs, List<ShipEvent> history)
            throws IOException {
        Path journal = blobs.runRoot().resolve("publication").resolve(JOURNAL);
        if (!Files.exists(journal, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        if (history == null || history.isEmpty()) {
            throw new IOException("Ship publication recovery lacks authenticated event history");
        }
        ShipEvent authenticatedHead = history.get(history.size() - 1);
        if (!authenticatedHead.eventDigest().equals(run.eventDigest())
                || !authenticatedHead.authorityHead().equals(run.authority().head())) {
            throw new IOException("Ship publication recovery history differs from its projection");
        }
        Intent intent = readJournal(journal);
        requireIntent(run, blobs, intent);
        ProjectSnapshot baseline = read(blobs, intent.baselineSnapshot(), ProjectSnapshot.class);
        ProjectSnapshot candidate = read(blobs, intent.candidateSnapshot(), ProjectSnapshot.class);
        Path candidateDirectory = blobs.verifyCandidateDirectory(Path.of(intent.candidateDirectory()));
        ProjectSnapshot sealed = ProjectEvidenceFiles.captureSealed(candidateDirectory);
        if (!candidate.equals(sealed)) {
            throw new IOException("Recovery candidate differs from its protected snapshot");
        }
        boolean completed = run.state() == ShipState.COMPLETED
                || run.state() == ShipState.COMPLETED_WITH_WAIVER;
        if (completed) {
            ShipState expectedState = intent.completionType() == ShipEventType.RUN_COMPLETED
                    ? ShipState.COMPLETED : ShipState.COMPLETED_WITH_WAIVER;
            if (run.state() != expectedState
                    || run.authority().revision() != intent.expectedRevision() + 1
                    || authenticatedHead.type() != intent.completionType()
                    || !authenticatedHead.previousEventDigest()
                            .equals(intent.expectedEventDigest())
                    || !intent.stamp().equals(run.stamp())) {
                throw new IOException("Committed Ship publication differs from its recovery intent");
            }
            if (run.candidateSnapshot() == null) {
                throw new IOException("Completed Ship run lacks its published snapshot");
            }
            ProjectSnapshot recordedPublished = read(blobs, run.candidateSnapshot(), ProjectSnapshot.class);
            ProjectSnapshot live = ProjectEvidenceFiles.capture(run.projectRoot());
            if (!ProjectEvidenceFiles.unchangedMaterialTree(candidate, recordedPublished)
                    || !ProjectEvidenceFiles.unchangedMaterialTree(recordedPublished, live)) {
                throw new IOException("Committed Ship publication differs from its recorded snapshot");
            }
        } else {
            if (!intent.expectedEventDigest().equals(run.eventDigest())
                    || run.authority().revision() != intent.expectedRevision()) {
                throw new IOException("Ship publication head is neither its predecessor nor completion");
            }
            rollback(intent, baseline, candidate);
        }
        Files.delete(journal);
        forceDirectory(journal.getParent());
    }

    private static void requireIntent(ShipRunView run, ShipBlobStore blobs, Intent intent)
            throws IOException {
        if (intent == null
                || intent.schemaVersion() != SCHEMA_VERSION
                || !run.runId().equals(intent.runId())
                || intent.expectedEventDigest() == null
                || !intent.expectedEventDigest().matches("sha256:[0-9a-f]{64}")
                || intent.expectedRevision() < 0
                || !run.projectRoot().toString().equals(intent.projectRoot())
                || !run.sourceDirectory().toString().equals(intent.sourceDirectory())
                || !Objects.equals(run.baselineSnapshot(), intent.baselineSnapshot())
                || !Objects.equals(run.executionResult(), intent.executionResult())
                || (run.state() == ShipState.COMPLETED
                        || run.state() == ShipState.COMPLETED_WITH_WAIVER
                                ? intent.candidateDirectory() == null || run.candidateDirectory() != null
                                : !Objects.equals(
                                        run.candidateDirectory() == null
                                                ? null : run.candidateDirectory().toString(),
                                        intent.candidateDirectory()))
                || run.candidateSnapshot() != null
                        && run.state() != ShipState.COMPLETED
                        && run.state() != ShipState.COMPLETED_WITH_WAIVER
                        && !run.candidateSnapshot().equals(intent.candidateSnapshot())
                || intent.completionType() != ShipEventType.RUN_COMPLETED
                        && intent.completionType() != ShipEventType.RUN_COMPLETED_WITH_WAIVER
                || !blobs.belongsToRun(intent.runId())) {
            throw new IOException("Ship publication recovery intent has an invalid identity");
        }
        blobs.verify(intent.baselineSnapshot());
        blobs.verify(intent.candidateSnapshot());
        blobs.verify(intent.executionResult());
        blobs.verify(intent.stamp());
    }

    private static void applyArtifacts(
            Path projectRoot,
            ProjectSnapshot candidate,
            ShipBlobStore blobs,
            List<Artifact> artifacts)
            throws IOException {
        for (Artifact artifact : artifacts) {
            String relative = artifact.claim().relativePath();
            FileEntry expected = candidate.files().get(relative);
            if (expected == null
                    || expected.classification() != Classification.MATERIAL
                    || !expected.digest().equals(artifact.blob().digest())
                    || expected.size() != artifact.blob().byteSize()) {
                throw new IOException("Publication artifact differs from the sealed candidate: " + relative);
            }
            Path target = safeTarget(projectRoot, relative);
            createParents(projectRoot, target.getParent(), candidate);
            writeAtomically(
                    target,
                    blobs.readBytes(artifact.blob(), Math.toIntExact(artifact.blob().byteSize())),
                    expected.unixMode());
        }
    }

    private static void rollback(
            Intent intent,
            ProjectSnapshot baseline,
            ProjectSnapshot candidate)
            throws IOException {
        Path projectRoot = Path.of(intent.projectRoot());
        ProjectSnapshot current = ProjectEvidenceFiles.capture(projectRoot);
        requireKnownMixture(current, baseline, candidate);
        Path source = Path.of(intent.sourceDirectory());
        List<String> paths = intent.artifacts().stream()
                .map(artifact -> artifact.claim().relativePath())
                .distinct()
                .sorted()
                .toList();
        for (String relative : paths) {
            Path target = safeTarget(projectRoot, relative);
            FileEntry original = baseline.files().get(relative);
            if (original == null) {
                Files.deleteIfExists(target);
                if (target.getParent() != null) {
                    forceDirectory(target.getParent());
                }
            } else {
                // Validate the immutable-source target before the bounded no-follow read.
                safeTarget(source, relative);
                byte[] content = ProjectEvidenceFiles.readMaterial(
                        source, relative, Math.toIntExact(original.size()));
                if (content.length != original.size()
                        || !ShipDigest.sha256(content).equals(original.digest())) {
                    throw new IOException("Immutable Ship source differs from publication baseline");
                }
                createParents(projectRoot, target.getParent(), baseline);
                writeAtomically(target, content, original.unixMode());
            }
        }
        removeCandidateOnlyDirectories(projectRoot, baseline, candidate);
        ProjectSnapshot restored = ProjectEvidenceFiles.capture(projectRoot);
        if (!ProjectEvidenceFiles.unchangedMaterialTree(baseline, restored)) {
            throw new IOException("Ship publication rollback did not restore the exact baseline");
        }
    }

    private static void requireKnownMixture(
            ProjectSnapshot current, ProjectSnapshot baseline, ProjectSnapshot candidate)
            throws IOException {
        Set<String> paths = new HashSet<>();
        baseline.files().forEach((path, entry) -> {
            if (entry.classification() == Classification.MATERIAL) {
                paths.add(path);
            }
        });
        candidate.files().forEach((path, entry) -> {
            if (entry.classification() == Classification.MATERIAL) {
                paths.add(path);
            }
        });
        current.files().forEach((path, entry) -> {
            if (entry.classification() == Classification.MATERIAL) {
                paths.add(path);
            }
        });
        for (MapEntry item : paths.stream().map(path -> new MapEntry(
                path, current.files().get(path), baseline.files().get(path), candidate.files().get(path))).toList()) {
            if (!Objects.equals(item.current(), item.baseline())
                    && !Objects.equals(item.current(), item.candidate())) {
                throw new IOException(
                        "Live project changed outside the recoverable publication mixture: "
                                      + item.path());
            }
        }
        Set<String> directories = new HashSet<>();
        baseline.directories().forEach((path, entry) -> {
            if (entry.classification() == Classification.MATERIAL) {
                directories.add(path);
            }
        });
        candidate.directories().forEach((path, entry) -> {
            if (entry.classification() == Classification.MATERIAL) {
                directories.add(path);
            }
        });
        current.directories().forEach((path, entry) -> {
            if (entry.classification() == Classification.MATERIAL) {
                directories.add(path);
            }
        });
        for (String path : directories) {
            var actual = current.directories().get(path);
            var before = baseline.directories().get(path);
            var after = candidate.directories().get(path);
            if (!Objects.equals(actual, before) && !Objects.equals(actual, after)) {
                throw new IOException(
                        "Live project directory changed outside the recoverable publication mixture: "
                                      + path);
            }
        }
    }

    private static void removeCandidateOnlyDirectories(
            Path projectRoot, ProjectSnapshot baseline, ProjectSnapshot candidate)
            throws IOException {
        List<String> directories = candidate.directories().keySet().stream()
                .filter(path -> !baseline.directories().containsKey(path))
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();
        for (String relative : directories) {
            try {
                Files.deleteIfExists(safeTarget(projectRoot, relative));
            } catch (DirectoryNotEmptyException ignored) {
                // A protected or baseline entry still owns the directory.
            }
        }
    }

    private static void createParents(
            Path root, Path parent, ProjectSnapshot desired)
            throws IOException {
        if (parent == null || parent.equals(root)) {
            return;
        }
        Path relative = root.relativize(parent);
        Path current = root;
        for (Path component : relative) {
            current = current.resolve(component);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                if (Files.isSymbolicLink(current)
                        || !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("Publication parent is not a real directory");
                }
                continue;
            }
            Files.createDirectory(current, PosixFilePermissions.asFileAttribute(
                    PosixFilePermissions.fromString("rwx------")));
            var entry = desired.directories().get(
                    root.relativize(current).toString().replace(root.getFileSystem().getSeparator(), "/"));
            if (entry != null) {
                setMode(current, entry.unixMode());
            }
            forceDirectory(current.getParent());
        }
    }

    private static Path safeTarget(Path root, String relative) throws IOException {
        String canonical = ShipTreePolicy.requireCanonicalRelativePath(relative);
        Path target = root.resolve(canonical.replace('/', root.getFileSystem().getSeparator().charAt(0)))
                .normalize();
        if (!target.startsWith(root) || target.equals(root)) {
            throw new IOException("Publication path escapes its protected root");
        }
        return target;
    }

    private static void writeAtomically(Path target, byte[] content, int unixMode)
            throws IOException {
        if (Files.isSymbolicLink(target)) {
            throw new IOException("Refusing to publish through a symbolic link");
        }
        Path temporary = target.resolveSibling(
                ".camel-kit-publish-" + UUID.randomUUID() + ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(
                    temporary,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE,
                    LinkOption.NOFOLLOW_LINKS)) {
                ByteBuffer bytes = ByteBuffer.wrap(content);
                while (bytes.hasRemaining()) {
                    channel.write(bytes);
                }
                channel.force(true);
            }
            setMode(temporary, unixMode);
            try {
                Files.move(
                        temporary, target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                throw new IOException("Ship publication requires atomic same-directory replacement", e);
            }
            forceDirectory(target.getParent());
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void writeJournal(Path journal, Intent intent) throws IOException {
        byte[] encoded = ShipJson.mapper().writeValueAsBytes(intent);
        Path temporary = journal.resolveSibling("." + JOURNAL + '-' + UUID.randomUUID());
        try {
            try (FileChannel channel = FileChannel.open(
                    temporary,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE,
                    LinkOption.NOFOLLOW_LINKS)) {
                ByteBuffer bytes = ByteBuffer.wrap(encoded);
                while (bytes.hasRemaining()) {
                    channel.write(bytes);
                }
                channel.force(true);
            }
            Files.setPosixFilePermissions(temporary, PosixFilePermissions.fromString("r--------"));
            Files.move(temporary, journal, StandardCopyOption.ATOMIC_MOVE);
            forceDirectory(journal.getParent());
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static Intent readJournal(Path journal) throws IOException {
        var attributes = Files.readAttributes(
                journal,
                BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS);
        Set<PosixFilePermission> expectedPermissions = Set.of(PosixFilePermission.OWNER_READ);
        Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(journal, LinkOption.NOFOLLOW_LINKS);
        var owner = Files.getOwner(journal, LinkOption.NOFOLLOW_LINKS);
        var parentOwner = Files.getOwner(journal.getParent(), LinkOption.NOFOLLOW_LINKS);
        Object links = Files.getAttribute(journal, "unix:nlink", LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isRegularFile()
                || attributes.isSymbolicLink()
                || attributes.fileKey() == null
                || attributes.size() <= 0
                || attributes.size() > ShipJson.MAX_DOCUMENT_BYTES
                || !permissions.equals(expectedPermissions)
                || !owner.equals(parentOwner)
                || !(links instanceof Number count)
                || count.longValue() != 1) {
            throw new IOException("Ship publication recovery journal has unsafe metadata");
        }
        byte[] encoded;
        try (FileChannel channel = FileChannel.open(
                journal, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            encoded = new byte[Math.toIntExact(attributes.size())];
            ByteBuffer target = ByteBuffer.wrap(encoded);
            while (target.hasRemaining()) {
                if (channel.read(target) < 0) {
                    throw new IOException("Ship publication recovery journal is truncated");
                }
            }
            if (channel.read(ByteBuffer.allocate(1)) != -1) {
                throw new IOException("Ship publication recovery journal has trailing bytes");
            }
        }
        var after = Files.readAttributes(
                journal,
                BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS);
        if (!attributes.fileKey().equals(after.fileKey())
                || attributes.size() != after.size()
                || !attributes.lastModifiedTime().equals(after.lastModifiedTime())
                || !permissions.equals(Files.getPosixFilePermissions(
                        journal, LinkOption.NOFOLLOW_LINKS))
                || !owner.equals(Files.getOwner(journal, LinkOption.NOFOLLOW_LINKS))) {
            throw new IOException("Ship publication recovery journal changed while read");
        }
        return ShipJson.mapper().readValue(encoded, Intent.class);
    }

    private static void setMode(Path path, int unixMode) throws IOException {
        Set<PosixFilePermission> permissions = new HashSet<>();
        PosixFilePermission[] values = {
                PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.GROUP_READ,
                PosixFilePermission.GROUP_WRITE, PosixFilePermission.GROUP_EXECUTE,
                PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_WRITE,
                PosixFilePermission.OTHERS_EXECUTE
        };
        int[] bits = {0400, 0200, 0100, 0040, 0020, 0010, 0004, 0002, 0001};
        for (int index = 0; index < bits.length; index++) {
            if ((unixMode & bits[index]) != 0) {
                permissions.add(values[index]);
            }
        }
        Files.setPosixFilePermissions(path, permissions);
    }

    private static void forceDirectory(Path path) throws IOException {
        try (FileChannel directory = FileChannel.open(path, StandardOpenOption.READ)) {
            directory.force(true);
        } catch (UnsupportedOperationException ignored) {
            // Some POSIX providers do not expose directory fsync.
        }
    }

    private static <T> T read(
            ShipBlobStore blobs, BlobReference reference, Class<T> type)
            throws IOException {
        return ShipJson.mapper().readValue(
                blobs.readBytes(reference, ShipJson.MAX_DOCUMENT_BYTES), type);
    }

    static final class LivePublication implements AutoCloseable {

        private final Path journal;
        private final Intent intent;
        private final ProjectSnapshot baseline;
        private final ProjectSnapshot candidate;
        private final ProjectSnapshot published;
        private boolean finished;

        private LivePublication(
                                Path journal,
                                Intent intent,
                                ProjectSnapshot baseline,
                                ProjectSnapshot candidate,
                                ProjectSnapshot published) {
            this.journal = journal;
            this.intent = intent;
            this.baseline = baseline;
            this.candidate = candidate;
            this.published = published;
        }

        ProjectSnapshot publishedSnapshot() {
            return published;
        }

        void finish() throws IOException {
            if (finished) {
                return;
            }
            finished = true;
            Files.deleteIfExists(journal);
            forceDirectory(journal.getParent());
        }

        void retainForRecovery() {
            finished = true;
        }

        @Override
        public void close() throws IOException {
            if (!finished) {
                rollback(intent, baseline, candidate);
                Files.deleteIfExists(journal);
                forceDirectory(journal.getParent());
                finished = true;
            }
        }
    }

    private record Artifact(ProducedArtifact claim, BlobReference blob) {
    }

    private record MapEntry(
            String path, FileEntry current, FileEntry baseline, FileEntry candidate) {
    }

    private record Intent(
            int schemaVersion,
            String runId,
            String expectedEventDigest,
            long expectedRevision,
            String projectRoot,
            String sourceDirectory,
            String candidateDirectory,
            BlobReference baselineSnapshot,
            BlobReference candidateSnapshot,
            BlobReference executionResult,
            BlobReference stamp,
            ShipEventType completionType,
            List<Artifact> artifacts) {

        private Intent {
            artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
        }
    }
}
