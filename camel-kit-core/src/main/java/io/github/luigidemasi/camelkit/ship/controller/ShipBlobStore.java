package io.github.luigidemasi.camelkit.ship.controller;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

import io.github.luigidemasi.camelkit.ship.ShipDigest;
import io.github.luigidemasi.camelkit.ship.protocol.ProducedArtifact;
import io.github.luigidemasi.camelkit.ship.security.ShipTreePolicy;
import io.github.luigidemasi.camelkit.ship.security.StagedArtifactSource;

/** Controller-owned, content-addressed bytes with no path-bearing public surface. */
public final class ShipBlobStore {

    private static final String BLOB_SUFFIX = ".blob";
    private static final String TEMP_PREFIX = ".import-";
    private static final String TEMP_SUFFIX = ".tmp";
    private static final int REGULAR_FILE_TYPE = 0100000;
    private static final Set<PosixFilePermission> DIRECTORY_PERMISSIONS
            = PosixFilePermissions.fromString("rwx------");
    private static final Set<PosixFilePermission> TEMP_PERMISSIONS
            = PosixFilePermissions.fromString("rw-------");
    private static final Set<PosixFilePermission> BLOB_PERMISSIONS
            = PosixFilePermissions.fromString("r--------");

    private final Path stateRoot;
    private final String runId;
    private final Path runRoot;
    private final Path blobs;
    private final Path quarantine;

    private ShipBlobStore(Path stateRoot, String runId) {
        this.stateRoot = stateRoot.toAbsolutePath().normalize();
        this.runId = runId;
        this.runRoot = ShipControllerPaths.requireRunRoot(this.stateRoot, runId);
        this.blobs = runRoot.resolve("blobs");
        this.quarantine = runRoot.resolve("quarantine");
    }

    public static ShipBlobStore create(Path stateRoot, String runId) throws IOException {
        ShipBlobStore store = new ShipBlobStore(stateRoot, runId);
        try (ShipOperationLock.Lease ignored = ShipOperationLock.acquireRun(store.stateRoot, store.runId)) {
            store.createPrivateDirectory(store.blobs, "Ship blob directory");
            store.createPrivateDirectory(store.quarantine, "Ship blob quarantine");
            store.cleanQuarantine();
        }
        return store;
    }

    public static ShipBlobStore open(Path stateRoot, String runId) throws IOException {
        ShipBlobStore store = new ShipBlobStore(stateRoot, runId);
        try (ShipOperationLock.Lease ignored = ShipOperationLock.acquireRun(store.stateRoot, store.runId)) {
            store.verifyPrivateDirectory(store.blobs, "Ship blob directory");
            store.verifyPrivateDirectory(store.quarantine, "Ship blob quarantine");
            store.cleanQuarantine();
        }
        return store;
    }

    public BlobReference writeBytes(String kind, byte[] value) throws IOException {
        requireKind(kind);
        if (value == null || value.length > ShipTreePolicy.current().maxFileBytes()) {
            throw new IllegalArgumentException("Ship blob bytes exceed the per-file limit");
        }
        BlobReference reference = new BlobReference(kind, ShipDigest.sha256(value), value.length);
        try (ShipOperationLock.Lease ignored = ShipOperationLock.acquireRun(stateRoot, runId)) {
            verifyStore();
            cleanQuarantine();
            Path target = blobPath(reference.digest());
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                verifyBlob(reference);
                return reference;
            }
            requireCapacity(reference.byteSize(), 1);
            Path temporary = newTemporary();
            boolean promoted = false;
            try {
                try (FileChannel output = openTemporary(temporary)) {
                    writeFully(output, ByteBuffer.wrap(value));
                    output.force(true);
                }
                verifyTemporary(temporary, value.length);
                promoted = promote(temporary, target, reference);
                return reference;
            } finally {
                Files.deleteIfExists(temporary);
                if (!promoted && Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                    verifyBlob(reference);
                }
                forceDirectory(quarantine);
            }
        }
    }

    public byte[] readBytes(BlobReference reference, int maximumBytes) throws IOException {
        requireReference(reference);
        if (maximumBytes < 0 || reference.byteSize() > maximumBytes || reference.byteSize() > Integer.MAX_VALUE) {
            throw new IOException("Ship blob exceeds the requested read limit");
        }
        try (ShipOperationLock.Lease ignored = ShipOperationLock.acquireRun(stateRoot, runId)) {
            verifyStore();
            return readVerified(reference);
        }
    }

    public void verify(BlobReference reference) throws IOException {
        requireReference(reference);
        try (ShipOperationLock.Lease ignored = ShipOperationLock.acquireRun(stateRoot, runId)) {
            verifyStore();
            verifyBlob(reference);
        }
    }

    boolean belongsTo(Path candidateStateRoot, String candidateRunId) {
        return candidateStateRoot != null
                && stateRoot.equals(candidateStateRoot.toAbsolutePath().normalize())
                && runId.equals(candidateRunId);
    }

    boolean belongsToRun(String candidateRunId) {
        return runId.equals(candidateRunId);
    }

    /** Imports a whole accepted result before any artifact becomes addressable. */
    List<ImportedBlob> importArtifacts(Path attemptOutput, List<ProducedArtifact> artifacts) throws IOException {
        if (artifacts == null) {
            throw new IllegalArgumentException("Produced artifacts are required");
        }
        if (artifacts.isEmpty()) {
            return List.of();
        }
        try (ShipOperationLock.Lease ignored = ShipOperationLock.acquireRun(stateRoot, runId)) {
            verifyStore();
            cleanQuarantine();
            List<PendingBlob> pending = new ArrayList<>();
            Set<Path> promoted = new HashSet<>();
            try {
                try (StagedArtifactSource.Session source = StagedArtifactSource.open(attemptOutput)) {
                    for (ProducedArtifact artifact : artifacts) {
                        Path temporary = newTemporary();
                        try {
                            pending.add(copyToQuarantine(source, artifact, temporary));
                        } catch (IOException | RuntimeException e) {
                            try {
                                Files.deleteIfExists(temporary);
                            } catch (IOException cleanup) {
                                e.addSuppressed(cleanup);
                            }
                            throw e;
                        }
                    }
                }
                validateBatch(pending);
                List<ImportedBlob> result = promoteBatch(pending, promoted);
                cleanPending(pending);
                return result;
            } catch (IOException | RuntimeException e) {
                rollbackPromoted(promoted, e);
                try {
                    cleanPending(pending);
                } catch (IOException cleanup) {
                    e.addSuppressed(cleanup);
                }
                throw e;
            }
        }
    }

    private PendingBlob copyToQuarantine(
            StagedArtifactSource.Session source, ProducedArtifact artifact, Path temporary)
            throws IOException {
        StagedArtifactSource.CopyResult copy;
        try (FileChannel output = openTemporary(temporary)) {
            copy = source.copyTo(artifact.relativePath(), artifact.size(), output);
            output.force(true);
        }
        if (copy.size() != artifact.size()) {
            throw new ShipControllerException(
                    "artifact-size-mismatch", "Produced artifact size changed: " + artifact.relativePath());
        }
        if (!copy.digest().equals(artifact.digest())) {
            throw new ShipControllerException(
                    "artifact-digest-mismatch", "Produced artifact digest changed: " + artifact.relativePath());
        }
        verifyTemporary(temporary, copy.size());
        int mode = REGULAR_FILE_TYPE | 0644;
        return new PendingBlob(
                new BlobReference(artifact.kind(), copy.digest(), copy.size()), temporary, mode);
    }

    private void validateBatch(List<PendingBlob> pending) throws IOException {
        Set<String> unique = new HashSet<>();
        long additionalBytes = 0;
        int additionalCount = 0;
        for (PendingBlob item : pending) {
            BlobReference reference = item.reference();
            if (!unique.add(reference.digest())) {
                continue;
            }
            Path target = blobPath(reference.digest());
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                verifyBlob(reference);
            } else {
                additionalBytes = Math.addExact(additionalBytes, reference.byteSize());
                additionalCount++;
            }
        }
        requireCapacity(additionalBytes, additionalCount);
    }

    private List<ImportedBlob> promoteBatch(List<PendingBlob> pending, Set<Path> promoted)
            throws IOException {
        for (PendingBlob item : pending) {
            Path target = blobPath(item.reference().digest());
            if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                setPermissions(item.temporary(), BLOB_PERMISSIONS, "Ship quarantined blob");
                if (promote(item.temporary(), target, item.reference())) {
                    promoted.add(target);
                }
            } else {
                verifyBlob(item.reference());
            }
        }
        return pending.stream()
                .map(item -> new ImportedBlob(item.reference(), item.unixMode()))
                .toList();
    }

    private void cleanPending(List<PendingBlob> pending) throws IOException {
        IOException failure = null;
        for (PendingBlob item : pending) {
            try {
                Files.deleteIfExists(item.temporary());
            } catch (IOException e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        try {
            forceDirectory(quarantine);
        } catch (IOException e) {
            if (failure == null) {
                failure = e;
            } else {
                failure.addSuppressed(e);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private void rollbackPromoted(Set<Path> promoted, Throwable failure) {
        for (Path target : promoted) {
            try {
                Files.deleteIfExists(target);
            } catch (IOException cleanup) {
                failure.addSuppressed(cleanup);
            }
        }
        try {
            forceDirectory(blobs);
        } catch (IOException cleanup) {
            failure.addSuppressed(cleanup);
        }
    }

    /** Returns true only when this call installed a new content address. */
    private boolean promote(Path temporary, Path target, BlobReference reference) throws IOException {
        setPermissions(temporary, BLOB_PERMISSIONS, "Ship quarantined blob");
        try {
            Files.createLink(target, temporary);
        } catch (FileAlreadyExistsException e) {
            verifyBlob(reference);
            return false;
        }
        boolean installed = false;
        try {
            Files.delete(temporary);
            verifyBlob(reference);
            try (FileChannel channel = FileChannel.open(
                    target, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
                channel.force(true);
            }
            forceDirectory(blobs);
            installed = true;
            return true;
        } finally {
            if (!installed) {
                Files.deleteIfExists(target);
                forceDirectory(blobs);
            }
        }
    }

    private byte[] readVerified(BlobReference reference) throws IOException {
        Path path = blobPath(reference.digest());
        FileIdentity before = verifyBlobMetadata(path, reference);
        byte[] value = new byte[Math.toIntExact(reference.byteSize())];
        MessageDigest digest = messageDigest();
        try (FileChannel channel = FileChannel.open(
                path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            ByteBuffer buffer = ByteBuffer.wrap(value);
            while (buffer.hasRemaining()) {
                int start = buffer.position();
                if (channel.read(buffer) < 0) {
                    throw new IOException("Ship blob is truncated");
                }
                digest.update(ByteBuffer.wrap(value, start, buffer.position() - start));
            }
            if (channel.read(ByteBuffer.allocate(1)) != -1) {
                throw new IOException("Ship blob has trailing bytes");
            }
        }
        FileIdentity after = verifyBlobMetadata(path, reference);
        if (!before.equals(after)
                || !reference.digest().equals("sha256:" + HexFormat.of().formatHex(digest.digest()))) {
            throw new IOException("Ship blob content identity does not match its reference");
        }
        return value;
    }

    private void verifyBlob(BlobReference reference) throws IOException {
        readVerified(reference);
    }

    private FileIdentity verifyBlobMetadata(Path path, BlobReference reference) throws IOException {
        BasicFileAttributes basic = Files.readAttributes(
                path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!basic.isRegularFile() || basic.isSymbolicLink() || basic.fileKey() == null
                || basic.size() != reference.byteSize()) {
            throw new IOException("Ship blob is missing or has invalid metadata");
        }
        setExpectedOwnerAndLinks(path, blobs, "Ship blob");
        requirePermissions(path, BLOB_PERMISSIONS, "Ship blob");
        PosixFileAttributes posix = Files.readAttributes(
                path, PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        return new FileIdentity(basic.fileKey(), basic.size(), basic.lastModifiedTime(), posix.creationTime());
    }

    private void verifyTemporary(Path path, long expectedSize) throws IOException {
        BasicFileAttributes basic = Files.readAttributes(
                path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!basic.isRegularFile() || basic.isSymbolicLink() || basic.fileKey() == null
                || basic.size() != expectedSize) {
            throw new IOException("Ship quarantined blob changed while it was written");
        }
        setExpectedOwnerAndLinks(path, quarantine, "Ship quarantined blob");
        requirePermissions(path, TEMP_PERMISSIONS, "Ship quarantined blob");
    }

    private FileChannel openTemporary(Path path) throws IOException {
        verifyTemporary(path, 0);
        return FileChannel.open(
                path, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING, LinkOption.NOFOLLOW_LINKS);
    }

    private Path newTemporary() throws IOException {
        Path path = Files.createTempFile(
                quarantine, TEMP_PREFIX, TEMP_SUFFIX,
                PosixFilePermissions.asFileAttribute(TEMP_PERMISSIONS));
        verifyTemporary(path, 0);
        return path;
    }

    private void requireCapacity(long additionalBytes, int additionalCount) throws IOException {
        long total = additionalBytes;
        int count = 0;
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(blobs)) {
            for (Path path : entries) {
                String name = path.getFileName().toString();
                if (!name.matches("[0-9a-f]{64}\\.blob")) {
                    throw new IOException("Ship blob directory contains an unrecognized entry");
                }
                String digest = "sha256:" + name.substring(0, 64);
                BasicFileAttributes basic = Files.readAttributes(
                        path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                verifyBlobMetadata(path, new BlobReference("stored", digest, basic.size()));
                total = Math.addExact(total, basic.size());
                count++;
            }
        } catch (ArithmeticException e) {
            throw new IOException("Ship blob quota overflowed", e);
        }
        ShipTreePolicy policy = ShipTreePolicy.current();
        if ((long) count + additionalCount > policy.maxFileCount()
                || total > policy.maxAggregateBytes()) {
            throw new IOException("Ship blob store exceeds its run quota");
        }
    }

    private void cleanQuarantine() throws IOException {
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(quarantine)) {
            for (Path path : entries) {
                String name = path.getFileName().toString();
                if (!name.startsWith(TEMP_PREFIX) || !name.endsWith(TEMP_SUFFIX)) {
                    throw new IOException("Ship blob quarantine contains an unrecognized entry");
                }
                BasicFileAttributes basic = Files.readAttributes(
                        path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (!basic.isRegularFile() || basic.isSymbolicLink()) {
                    throw new IOException("Ship blob quarantine contains an unsafe entry");
                }
                requireSameOwner(path, quarantine, "Ship blob quarantine entry");
                long links = linkCount(path, "Ship blob quarantine entry");
                if (links < 1 || links > 2) {
                    throw new IOException("Ship blob quarantine entry has an unsafe hard-link count");
                }
                Files.delete(path);
            }
        }
        forceDirectory(quarantine);
    }

    private void verifyStore() throws IOException {
        verifyPrivateDirectory(blobs, "Ship blob directory");
        verifyPrivateDirectory(quarantine, "Ship blob quarantine");
    }

    private void createPrivateDirectory(Path path, String description) throws IOException {
        try {
            Files.createDirectory(path, PosixFilePermissions.asFileAttribute(DIRECTORY_PERMISSIONS));
            forceDirectory(runRoot);
        } catch (FileAlreadyExistsException ignored) {
            // An interrupted or re-opened run may already own it; validate below.
        } catch (UnsupportedOperationException e) {
            throw new IOException(description + " requires POSIX permissions", e);
        }
        verifyPrivateDirectory(path, description);
    }

    private void verifyPrivateDirectory(Path path, String description) throws IOException {
        BasicFileAttributes basic = Files.readAttributes(
                path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!basic.isDirectory() || basic.isSymbolicLink() || basic.fileKey() == null
                || !path.toRealPath(LinkOption.NOFOLLOW_LINKS).equals(path)) {
            throw new IOException(description + " must be a real directory");
        }
        requirePermissions(path, DIRECTORY_PERMISSIONS, description);
        requireSameOwner(path, runRoot, description);
    }

    private static void setExpectedOwnerAndLinks(Path path, Path parent, String description)
            throws IOException {
        requireSameOwner(path, parent, description);
        if (linkCount(path, description) != 1) {
            throw new IOException(description + " must have exactly one hard link");
        }
    }

    private static long linkCount(Path path, String description) throws IOException {
        Object links;
        try {
            links = Files.getAttribute(path, "unix:nlink", LinkOption.NOFOLLOW_LINKS);
        } catch (UnsupportedOperationException e) {
            throw new IOException(description + " filesystem lacks hard-link identity", e);
        }
        if (!(links instanceof Number count)) {
            throw new IOException(description + " lacks a hard-link count");
        }
        return count.longValue();
    }

    private static void requireSameOwner(Path path, Path parent, String description) throws IOException {
        PosixFileAttributes attributes = Files.readAttributes(
                path, PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        PosixFileAttributes parentAttributes = Files.readAttributes(
                parent, PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.owner().equals(parentAttributes.owner())) {
            throw new IOException(description + " owner differs from its protected parent");
        }
    }

    private static void requirePermissions(
            Path path, Set<PosixFilePermission> expected, String description)
            throws IOException {
        if (Files.getFileAttributeView(
                path, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS)
            == null) {
            throw new IOException(description + " requires POSIX permission support");
        }
        Set<PosixFilePermission> actual = Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS);
        if (!actual.equals(expected)) {
            throw new IOException(
                    description + " has unsafe permissions "
                                  + PosixFilePermissions.toString(actual));
        }
    }

    private static void setPermissions(
            Path path, Set<PosixFilePermission> permissions, String description)
            throws IOException {
        if (Files.getFileAttributeView(
                path, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS)
            == null) {
            throw new IOException(description + " requires POSIX permission support");
        }
        Files.setPosixFilePermissions(path, permissions);
    }

    private Path blobPath(String digest) {
        return blobs.resolve(digest.substring("sha256:".length()) + BLOB_SUFFIX);
    }

    private static void writeFully(FileChannel output, ByteBuffer value) throws IOException {
        while (value.hasRemaining()) {
            output.write(value);
        }
    }

    private static void forceDirectory(Path path) throws IOException {
        try (FileChannel directory = FileChannel.open(path, StandardOpenOption.READ)) {
            directory.force(true);
        } catch (UnsupportedOperationException ignored) {
            // Some POSIX providers do not expose directory fsync.
        }
    }

    private static MessageDigest messageDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static void requireReference(BlobReference reference) {
        if (reference == null) {
            throw new IllegalArgumentException("Ship blob reference is required");
        }
    }

    private static String requireKind(String value) {
        if (value == null || !value.matches("[a-z][a-z0-9-]{0,63}")) {
            throw new IllegalArgumentException("Invalid Ship blob kind");
        }
        return value;
    }

    public record BlobReference(String kind, String digest, long byteSize) {

        public BlobReference {
            kind = requireKind(kind);
            if (!ShipDigest.isSha256(digest) || byteSize < 0
                    || byteSize > ShipTreePolicy.current().maxFileBytes()) {
                throw new IllegalArgumentException("Invalid Ship blob reference");
            }
        }
    }

    record ImportedBlob(BlobReference reference, int unixMode) {

        ImportedBlob {
            requireReference(reference);
            if (unixMode != (REGULAR_FILE_TYPE | 0644)) {
                throw new IllegalArgumentException("Invalid imported Ship artifact mode");
            }
        }
    }

    private record PendingBlob(BlobReference reference, Path temporary, int unixMode) {
    }

    private record FileIdentity(Object fileKey, long size, java.nio.file.attribute.FileTime modified,
            java.nio.file.attribute.FileTime created) {
    }
}
