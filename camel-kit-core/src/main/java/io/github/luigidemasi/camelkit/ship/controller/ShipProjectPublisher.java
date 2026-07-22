package io.github.luigidemasi.camelkit.ship.controller;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import io.github.luigidemasi.camelkit.ship.ShipDigest;
import io.github.luigidemasi.camelkit.ship.protocol.ShipStage;
import io.github.luigidemasi.camelkit.ship.security.ProjectEvidenceFiles;
import io.github.luigidemasi.camelkit.ship.security.ProjectSnapshot;
import io.github.luigidemasi.camelkit.ship.security.ProjectSnapshot.FileEntry;
import io.github.luigidemasi.camelkit.ship.security.ShipTreePolicy;
import io.github.luigidemasi.camelkit.ship.security.ShipTreePolicy.Classification;

/**
 * Builds a controller-owned, integrity-bound publication candidate.
 *
 * <p>
 * This class is deliberately not a live-project commit boundary. It never writes under {@code projectRoot}; a later
 * slice must revalidate the expected event head and perform recovery-aware publication. The candidate is protected by
 * controller-state ownership; it is not claimed to be immutable against another process running as the controller's OS
 * principal.
 */
final class ShipProjectPublisher {

    static final int MAX_RETAINED_CANDIDATES = 4;

    private static final String BINDING_DOMAIN = "camel-kit.ship.publication-candidate.v1";
    private static final Set<PosixFilePermission> PRIVATE_DIRECTORY
            = PosixFilePermissions.fromString("rwx------");
    private static final Set<PosixFilePermission> PRIVATE_FILE
            = PosixFilePermissions.fromString("rw-------");
    private static final Set<PosixFilePermission> SEALED_BINDING
            = PosixFilePermissions.fromString("r--------");

    private ShipProjectPublisher() {
    }

    /** Stages and seals a candidate while leaving the live project byte-for-byte untouched. */
    static StagedCandidate stageSealedCandidate(
            Path stateRoot,
            String runId,
            Path projectRoot,
            String expectedEventHeadDigest,
            long expectedEventHeadRevision,
            ShipBlobStore blobs,
            ShipWorkspaceService.AcceptedWorkspace workspace)
            throws IOException {
        return stageSealedCandidate(
                stateRoot,
                runId,
                projectRoot,
                expectedEventHeadDigest,
                expectedEventHeadRevision,
                blobs,
                workspace,
                ignored -> {
                });
    }

    static StagedCandidate stageSealedCandidate(
            Path stateRoot,
            String runId,
            Path projectRoot,
            String expectedEventHeadDigest,
            long expectedEventHeadRevision,
            ShipBlobStore blobs,
            ShipWorkspaceService.AcceptedWorkspace workspace,
            PublicationHook hook)
            throws IOException {
        requireInputs(
                stateRoot,
                runId,
                projectRoot,
                expectedEventHeadDigest,
                expectedEventHeadRevision,
                blobs,
                workspace);
        java.util.Objects.requireNonNull(hook, "publication hook");
        Path normalizedState = stateRoot.toAbsolutePath().normalize();
        if (!blobs.belongsTo(normalizedState, runId)) {
            throw new IllegalArgumentException("Sealed publication blob store belongs to a different Ship run");
        }
        Path normalizedProject = projectRoot.toAbsolutePath().normalize();
        if (normalizedProject.startsWith(normalizedState) || normalizedState.startsWith(normalizedProject)) {
            throw new IllegalArgumentException("Ship project and controller state roots must be disjoint");
        }
        Path runRoot = ShipControllerPaths.requireRunRoot(normalizedState, runId);
        Path publicationRoot = runRoot.resolve("publication");

        try (ShipOperationLock.Lease ignoredRun = ShipOperationLock.acquireRun(normalizedState, runId);
             ShipOperationLock.Lease ignoredProject = ShipOperationLock.acquireProject(normalizedState, projectRoot)) {
            createPrivateDirectory(publicationRoot, runRoot, "Ship publication directory");
            requireCandidateCapacity(publicationRoot);
            String transactionId = UUID.randomUUID().toString();
            Path transactionRoot = publicationRoot.resolve(transactionId);
            Files.createDirectory(
                    transactionRoot, PosixFilePermissions.asFileAttribute(PRIVATE_DIRECTORY));
            boolean complete = false;
            Throwable stagingFailure = null;
            try {
                forceDirectory(publicationRoot);
                Path candidate = transactionRoot.resolve("candidate");
                Files.createDirectory(candidate, PosixFilePermissions.asFileAttribute(PRIVATE_DIRECTORY));
                forceDirectory(transactionRoot);

                ProjectSnapshot baseline = ProjectEvidenceFiles.capture(projectRoot);
                hook.at("after-baseline-capture");
                ProjectSnapshot materialized = ProjectEvidenceFiles.materializeMaterial(projectRoot, candidate);
                hook.at("after-baseline-materialization");
                if (!ProjectEvidenceFiles.unchangedMaterialTree(baseline, materialized)) {
                    throw new IOException(
                            "Materialized publication candidate differs from its captured baseline");
                }
                overlay(candidate, materialized, blobs, workspace.artifacts());
                ProjectSnapshot postimage = ProjectEvidenceFiles.captureSealed(candidate);
                verifyPostimage(postimage, workspace.artifacts());

                ProjectSnapshot liveAfter = ProjectEvidenceFiles.capture(projectRoot);
                if (!ProjectEvidenceFiles.unchanged(baseline, liveAfter)) {
                    throw new IOException("Live Ship project changed while the sealed candidate was staged");
                }

                List<PublicationArtifact> artifacts = workspace.artifacts().stream()
                        .map(artifact -> new PublicationArtifact(
                                artifact.claim().relativePath(), artifact.blob(), artifact.unixMode()))
                        .toList();
                byte[] binding = encodeBinding(
                        transactionId,
                        runId,
                        expectedEventHeadDigest,
                        expectedEventHeadRevision,
                        baseline.digest(),
                        postimage.digest(),
                        artifacts);
                String bindingDigest = ShipDigest.sha256(binding);
                writeBinding(transactionRoot.resolve("binding.bin"), binding);
                forceDirectory(transactionRoot);
                forceDirectory(publicationRoot);
                PublicationTransaction transaction = new PublicationTransaction(
                        transactionId,
                        runId,
                        expectedEventHeadDigest,
                        expectedEventHeadRevision,
                        baseline.digest(),
                        postimage.digest(),
                        bindingDigest,
                        artifacts);
                StagedCandidate result = new StagedCandidate(
                        publicationRoot, transactionRoot, transaction);
                complete = true;
                return result;
            } catch (IOException | RuntimeException | Error failure) {
                stagingFailure = failure;
                throw failure;
            } finally {
                if (!complete) {
                    try {
                        deleteOwnedTree(transactionRoot);
                        forceDirectory(publicationRoot);
                    } catch (IOException cleanup) {
                        if (stagingFailure != null) {
                            stagingFailure.addSuppressed(cleanup);
                        } else {
                            throw cleanup;
                        }
                    }
                }
            }
        }
    }

    /**
     * Removes candidates that are not owned by any authenticated execution-acceptance event. The caller must hold the
     * run and project operation locks and must pass the exact immutable history returned by the projector replay that
     * produced the current view.
     */
    static void reconcileCandidates(
            ShipBlobStore blobs, List<ShipEvent> authenticatedHistory)
            throws IOException {
        java.util.Objects.requireNonNull(blobs, "Ship blob store");
        java.util.Objects.requireNonNull(authenticatedHistory, "authenticated Ship history");
        Path publicationRoot = blobs.runRoot().resolve("publication");
        Set<Path> retained = new HashSet<>();
        for (ShipEvent event : authenticatedHistory) {
            if (event.type() != ShipEventType.EXECUTION_VALIDATED) {
                continue;
            }
            ShipStoredEventCodec.StoredEvent stored = ShipStoredEventCodec.decode(event);
            if (!(stored.data() instanceof ShipEventPayloads.StageAccepted accepted)) {
                throw new IOException("Execution acceptance has an invalid publication payload");
            }
            retained.add(requireCandidateTransaction(
                    publicationRoot, accepted.candidateDirectory()));
        }

        if (!Files.exists(publicationRoot, LinkOption.NOFOLLOW_LINKS)) {
            if (!retained.isEmpty()) {
                throw new IOException("Authenticated publication candidate directory is missing");
            }
            return;
        }
        BasicFileAttributes publicationAttributes = Files.readAttributes(
                publicationRoot, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!publicationAttributes.isDirectory() || publicationAttributes.isSymbolicLink()) {
            throw new IOException("Ship publication directory is not a real directory");
        }

        List<Path> transactions = new ArrayList<>();
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(publicationRoot)) {
            for (Path entry : entries) {
                requireTransactionDirectory(publicationRoot, entry);
                transactions.add(entry);
            }
        }
        if (!transactions.containsAll(retained)) {
            throw new IOException("Authenticated publication candidate directory is missing");
        }
        boolean removed = false;
        for (Path transaction : transactions) {
            if (!retained.contains(transaction)) {
                deleteOwnedTree(transaction);
                removed = true;
            }
        }
        if (removed) {
            forceDirectory(publicationRoot);
        }
    }

    private static Path requireCandidateTransaction(
            Path publicationRoot, String candidateDirectory)
            throws IOException {
        if (candidateDirectory == null) {
            throw new IOException("Execution acceptance has no publication candidate directory");
        }
        Path candidate;
        try {
            Path supplied = Path.of(candidateDirectory);
            candidate = supplied.toAbsolutePath().normalize();
            if (!supplied.equals(candidate)) {
                throw new IOException("Execution acceptance has a non-canonical candidate directory");
            }
        } catch (InvalidPathException e) {
            throw new IOException("Execution acceptance has an invalid candidate directory", e);
        }
        Path transaction = candidate.getParent();
        if (transaction == null
                || candidate.getFileName() == null
                || !candidate.getFileName().toString().equals("candidate")
                || transaction.getParent() == null
                || !transaction.getParent().equals(publicationRoot)) {
            throw new IOException("Execution acceptance candidate is outside its Ship run");
        }
        requireTransactionDirectory(publicationRoot, transaction);
        BasicFileAttributes candidateAttributes = Files.readAttributes(
                candidate, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!candidateAttributes.isDirectory() || candidateAttributes.isSymbolicLink()) {
            throw new IOException("Authenticated publication candidate is not a real directory");
        }
        return transaction;
    }

    private static void requireTransactionDirectory(Path publicationRoot, Path transaction)
            throws IOException {
        BasicFileAttributes basic = Files.readAttributes(
                transaction, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        String name = transaction.getFileName().toString();
        if (!transaction.getParent().equals(publicationRoot)
                || !name.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
                || !basic.isDirectory() || basic.isSymbolicLink()) {
            throw new IOException("Ship publication directory contains an unsafe transaction entry");
        }
    }

    private static void overlay(
            Path candidate,
            ProjectSnapshot baseline,
            ShipBlobStore blobs,
            List<ShipWorkspaceService.AcceptedArtifact> artifacts)
            throws IOException {
        Set<String> paths = new HashSet<>();
        for (ShipWorkspaceService.AcceptedArtifact artifact : artifacts) {
            String relative = ShipTreePolicy.requireCanonicalRelativePath(artifact.claim().relativePath());
            if (!paths.add(relative) || ShipTreePolicy.current().classify(relative) != Classification.MATERIAL) {
                throw new IOException("Publication artifact path is duplicate or non-material: " + relative);
            }
            Path target = prepareParents(candidate, relative, baseline);
            if (Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(target)) {
                throw new IOException("Publication artifact collides with an unsafe candidate entry: " + relative);
            }
            byte[] value = blobs.readBytes(
                    artifact.blob(), Math.toIntExact(artifact.blob().byteSize()));
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                setMode(target, 0600);
            }
            try (FileChannel output = FileChannel.open(
                    target,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE,
                    LinkOption.NOFOLLOW_LINKS)) {
                ByteBuffer buffer = ByteBuffer.wrap(value);
                while (buffer.hasRemaining()) {
                    output.write(buffer);
                }
                output.force(true);
            }
            setMode(target, artifact.unixMode());
        }
        restoreBaselineDirectoryModes(candidate, baseline);
        forceDirectory(candidate);
    }

    private static Path prepareParents(Path candidate, String relative, ProjectSnapshot baseline)
            throws IOException {
        String[] components = relative.split("/");
        Path current = candidate;
        StringBuilder relativeDirectory = new StringBuilder();
        for (int index = 0; index < components.length - 1; index++) {
            if (!relativeDirectory.isEmpty()) {
                relativeDirectory.append('/');
            }
            relativeDirectory.append(components[index]);
            current = current.resolve(components[index]);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                BasicFileAttributes basic = Files.readAttributes(
                        current, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (!basic.isDirectory() || basic.isSymbolicLink()) {
                    throw new IOException("Publication artifact parent is not a real directory");
                }
                FileEntry collision = baseline.files().get(relativeDirectory.toString());
                if (collision != null) {
                    throw new IOException("Publication artifact parent collides with a baseline file");
                }
                ProjectSnapshot.DirectoryEntry recorded = baseline.directories().get(relativeDirectory.toString());
                setMode(current, recorded == null ? 0755 : recorded.unixMode() | 0700);
            } else {
                Files.createDirectory(
                        current,
                        PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwxr-xr-x")));
            }
        }
        return current.resolve(components[components.length - 1]);
    }

    private static void restoreBaselineDirectoryModes(Path candidate, ProjectSnapshot baseline)
            throws IOException {
        for (var item : baseline.directories().entrySet().stream()
                .sorted(java.util.Map.Entry.<String, ProjectSnapshot.DirectoryEntry>comparingByKey().reversed())
                .toList()) {
            setMode(candidate.resolve(item.getKey()), item.getValue().unixMode());
        }
    }

    private static void verifyPostimage(
            ProjectSnapshot postimage, List<ShipWorkspaceService.AcceptedArtifact> artifacts)
            throws IOException {
        for (ShipWorkspaceService.AcceptedArtifact artifact : artifacts) {
            FileEntry entry = postimage.files().get(artifact.claim().relativePath());
            if (entry == null
                    || entry.classification() != Classification.MATERIAL
                    || entry.size() != artifact.blob().byteSize()
                    || !entry.digest().equals(artifact.blob().digest())
                    || entry.unixMode() != artifact.unixMode()) {
                throw new IOException(
                        "Sealed publication candidate differs from accepted artifact: "
                                      + artifact.claim().relativePath());
            }
        }
    }

    private static byte[] encodeBinding(
            String transactionId,
            String runId,
            String eventDigest,
            long eventRevision,
            String baselineDigest,
            String postimageDigest,
            List<PublicationArtifact> artifacts)
            throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            writeString(output, BINDING_DOMAIN);
            writeString(output, transactionId);
            writeString(output, runId);
            writeString(output, eventDigest);
            output.writeLong(eventRevision);
            writeString(output, baselineDigest);
            writeString(output, postimageDigest);
            output.writeInt(artifacts.size());
            for (PublicationArtifact artifact : artifacts) {
                writeString(output, artifact.relativePath());
                writeString(output, artifact.blob().kind());
                writeString(output, artifact.blob().digest());
                output.writeLong(artifact.blob().byteSize());
                output.writeInt(artifact.unixMode());
            }
        }
        return bytes.toByteArray();
    }

    private static void writeString(DataOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static void writeBinding(Path path, byte[] value) throws IOException {
        Files.createFile(path, PosixFilePermissions.asFileAttribute(PRIVATE_FILE));
        try (FileChannel output = FileChannel.open(
                path, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
            ByteBuffer buffer = ByteBuffer.wrap(value);
            while (buffer.hasRemaining()) {
                output.write(buffer);
            }
            output.force(true);
        }
        Files.setPosixFilePermissions(path, SEALED_BINDING);
    }

    private static void createPrivateDirectory(Path path, Path parent, String description)
            throws IOException {
        try {
            Files.createDirectory(path, PosixFilePermissions.asFileAttribute(PRIVATE_DIRECTORY));
            forceDirectory(parent);
        } catch (FileAlreadyExistsException ignored) {
            // Reused across sealed candidates; authoritative checks follow.
        }
        BasicFileAttributes basic = Files.readAttributes(
                path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!basic.isDirectory() || basic.isSymbolicLink()
                || !Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS)
                        .equals(PRIVATE_DIRECTORY)) {
            throw new IOException(description + " must be a private real directory");
        }
    }

    private static void requireCandidateCapacity(Path publicationRoot) throws IOException {
        int retained = 0;
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(publicationRoot)) {
            for (Path entry : entries) {
                BasicFileAttributes basic = Files.readAttributes(
                        entry, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                String name = entry.getFileName().toString();
                if (!name.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
                        || !basic.isDirectory() || basic.isSymbolicLink()) {
                    throw new IOException("Ship publication directory contains an unsafe transaction entry");
                }
                retained++;
            }
        }
        if (retained >= MAX_RETAINED_CANDIDATES) {
            throw new IOException(
                    "Ship run already retains the maximum " + MAX_RETAINED_CANDIDATES
                                  + " publication candidates");
        }
    }

    private static void setMode(Path path, int unixMode) throws IOException {
        if (Files.getFileAttributeView(
                path, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS)
            == null) {
            throw new IOException("Sealed publication candidates require POSIX permissions");
        }
        char[] mode = "---------".toCharArray();
        int[] bits = {0400, 0200, 0100, 0040, 0020, 0010, 0004, 0002, 0001};
        char[] values = {'r', 'w', 'x', 'r', 'w', 'x', 'r', 'w', 'x'};
        for (int index = 0; index < bits.length; index++) {
            if ((unixMode & bits[index]) != 0) {
                mode[index] = values[index];
            }
        }
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(new String(mode)));
    }

    private static void deleteOwnedTree(Path root) throws IOException {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        List<IOException> failures = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes)
                    throws IOException {
                Set<PosixFilePermission> permissions
                        = new HashSet<>(Files.getPosixFilePermissions(directory, LinkOption.NOFOLLOW_LINKS));
                permissions.add(PosixFilePermission.OWNER_READ);
                permissions.add(PosixFilePermission.OWNER_WRITE);
                permissions.add(PosixFilePermission.OWNER_EXECUTE);
                Files.setPosixFilePermissions(directory, permissions);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                try {
                    Files.delete(file);
                } catch (IOException e) {
                    failures.add(e);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException failure) {
                if (failure != null) {
                    failures.add(failure);
                }
                try {
                    Files.delete(directory);
                } catch (IOException e) {
                    failures.add(e);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        if (!failures.isEmpty()) {
            IOException failure = new IOException("Could not remove incomplete publication candidate");
            failures.forEach(failure::addSuppressed);
            throw failure;
        }
    }

    private static void forceDirectory(Path path) throws IOException {
        try (FileChannel directory = FileChannel.open(path, StandardOpenOption.READ)) {
            directory.force(true);
        } catch (UnsupportedOperationException ignored) {
            // Some POSIX providers do not expose directory fsync.
        }
    }

    private static void requireInputs(
            Path stateRoot,
            String runId,
            Path projectRoot,
            String eventDigest,
            long eventRevision,
            ShipBlobStore blobs,
            ShipWorkspaceService.AcceptedWorkspace workspace) {
        if (stateRoot == null || projectRoot == null || runId == null
                || eventRevision < 0 || !ShipDigest.isSha256(eventDigest)
                || blobs == null || workspace == null || !runId.equals(workspace.runId())
                || workspace.stage() != ShipStage.EXECUTE
                || workspace.artifacts().isEmpty()) {
            throw new IllegalArgumentException("Invalid sealed Ship publication inputs");
        }
    }

    record PublicationTransaction(
            String transactionId,
            String runId,
            String expectedEventHeadDigest,
            long expectedEventHeadRevision,
            String baselineDigest,
            String postimageDigest,
            String bindingDigest,
            List<PublicationArtifact> artifacts) {

        PublicationTransaction {
            if (transactionId == null || !transactionId.matches("[0-9a-f-]{36}")
                    || runId == null || runId.isBlank()
                    || !ShipDigest.isSha256(expectedEventHeadDigest)
                    || expectedEventHeadRevision < 0
                    || !ShipDigest.isSha256(baselineDigest)
                    || !ShipDigest.isSha256(postimageDigest)
                    || !ShipDigest.isSha256(bindingDigest)) {
                throw new IllegalArgumentException("Invalid Ship publication transaction");
            }
            artifacts = List.copyOf(artifacts);
        }
    }

    /** Owns an uncommitted candidate until its accepting event is confirmed or recovery takes custody. */
    static final class StagedCandidate implements AutoCloseable {

        private final Path publicationRoot;
        private final Path transactionRoot;
        private final PublicationTransaction transaction;
        private boolean retained;
        private boolean closed;

        private StagedCandidate(
                                Path publicationRoot, Path transactionRoot, PublicationTransaction transaction) {
            this.publicationRoot = java.util.Objects.requireNonNull(publicationRoot, "publication root");
            this.transactionRoot = java.util.Objects.requireNonNull(transactionRoot, "transaction root");
            this.transaction = java.util.Objects.requireNonNull(transaction, "publication transaction");
        }

        String transactionId() {
            return transaction.transactionId();
        }

        String runId() {
            return transaction.runId();
        }

        String expectedEventHeadDigest() {
            return transaction.expectedEventHeadDigest();
        }

        long expectedEventHeadRevision() {
            return transaction.expectedEventHeadRevision();
        }

        String baselineDigest() {
            return transaction.baselineDigest();
        }

        String postimageDigest() {
            return transaction.postimageDigest();
        }

        String bindingDigest() {
            return transaction.bindingDigest();
        }

        List<PublicationArtifact> artifacts() {
            return transaction.artifacts();
        }

        Path candidateDirectory() {
            return transactionRoot.resolve("candidate");
        }

        void transferToHistory() {
            retained = true;
        }

        void retainForRecovery() {
            retained = true;
        }

        @Override
        public void close() throws IOException {
            if (closed) {
                return;
            }
            closed = true;
            if (!retained) {
                deleteOwnedTree(transactionRoot);
                forceDirectory(publicationRoot);
            }
        }
    }

    record PublicationArtifact(
            String relativePath, ShipBlobStore.BlobReference blob, int unixMode) {

        PublicationArtifact {
            ShipTreePolicy.requireCanonicalRelativePath(relativePath);
            if (blob == null || unixMode != 0100644) {
                throw new IllegalArgumentException("Invalid Ship publication artifact");
            }
        }
    }

    @FunctionalInterface
    interface PublicationHook {

        void at(String point) throws IOException;
    }
}
