package io.github.luigidemasi.camelkit.ship.controller;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import io.github.luigidemasi.camelkit.ship.ShipDigest;

/** Cross-process, same-thread-reentrant leases for long controller operations. */
final class ShipOperationLock {

    private static final Set<PosixFilePermission> DIRECTORY_PERMISSIONS
            = PosixFilePermissions.fromString("rwx------");
    private static final Set<PosixFilePermission> FILE_PERMISSIONS
            = PosixFilePermissions.fromString("rw-------");
    private static final ConcurrentHashMap<Path, JvmLock> JVM_LOCKS = new ConcurrentHashMap<>();
    private static final ThreadLocal<Map<Path, HeldLock>> HELD = ThreadLocal.withInitial(HashMap::new);

    private ShipOperationLock() {
    }

    static Lease acquireRun(Path stateRoot, String runId) throws IOException {
        ShipRunId parsedRunId;
        try {
            parsedRunId = ShipRunId.fromStorageId(runId);
        } catch (IllegalArgumentException e) {
            throw new ShipControllerException(
                    "run-id-invalid", "Invalid Ship run ID: " + runId, e);
        }
        Path runRoot = ShipControllerPaths.requireRunRoot(stateRoot, parsedRunId);
        Path root = runRoot.getParent();
        verifyPrivateDirectory(root, null, "Ship state root");
        verifyPrivateDirectory(runRoot, root, "Ship run directory");
        return acquire(runRoot.resolve("operation.lock"), LockKind.RUN, "Ship run");
    }

    static Lease acquireProject(Path stateRoot, Path projectRoot) throws IOException {
        Path root = Objects.requireNonNull(stateRoot, "stateRoot").toAbsolutePath().normalize();
        boolean runHeldForRoot = HELD.get().entrySet().stream().anyMatch(entry -> entry.getValue().kind == LockKind.RUN
                && root.equals(entry.getKey().getParent().getParent()));
        if (!runHeldForRoot) {
            if (HELD.get().isEmpty()) {
                HELD.remove();
            }
            throw new IllegalStateException(
                    "A Ship project lease requires a same-thread run lease for its state root");
        }
        verifyPrivateDirectory(root, null, "Ship state root");
        String digest = ShipDigest.sha256(projectIdentity(projectRoot));
        Path locks = root.resolve("project-locks");
        createPrivateDirectory(locks, root, "Ship project-lock directory");
        return acquire(
                locks.resolve(digest.substring("sha256:".length()) + ".lock"),
                LockKind.PROJECT,
                "Ship project");
    }

    private static Lease acquire(Path requestedPath, LockKind kind, String label) throws IOException {
        Path path = requestedPath.toAbsolutePath().normalize();
        HeldLock existing = HELD.get().get(path);
        if (existing != null) {
            existing.depth++;
            return new Lease(path, existing);
        }
        if (kind == LockKind.RUN
                && HELD.get().values().stream().anyMatch(lock -> lock.kind == LockKind.PROJECT)) {
            throw new IllegalStateException("Ship run leases must be acquired before project leases");
        }

        JvmLock jvmLock = JVM_LOCKS.compute(path, (ignored, current) -> {
            JvmLock selected = current == null ? new JvmLock() : current;
            selected.references++;
            return selected;
        });
        if (!jvmLock.lock.tryLock()) {
            releaseJvmLock(path, jvmLock);
            removeHeldIfEmpty();
            throw busy(label);
        }

        FileChannel channel = null;
        FileLock fileLock = null;
        try {
            Object expectedFileKey = createAndVerifyPrivateFile(path);
            channel = FileChannel.open(
                    path,
                    StandardOpenOption.READ,
                    StandardOpenOption.WRITE,
                    LinkOption.NOFOLLOW_LINKS);
            requireSameEntry(path, expectedFileKey, label + " operation lock");
            try {
                fileLock = channel.tryLock();
            } catch (OverlappingFileLockException e) {
                throw busy(label);
            }
            if (fileLock == null) {
                throw busy(label);
            }
            requireSameEntry(path, expectedFileKey, label + " operation lock");
            HeldLock held = new HeldLock(kind, jvmLock, channel, fileLock);
            HELD.get().put(path, held);
            return new Lease(path, held);
        } catch (IOException | RuntimeException e) {
            IOException cleanupFailure = null;
            try {
                if (fileLock != null && fileLock.isValid()) {
                    fileLock.release();
                }
            } catch (IOException failure) {
                cleanupFailure = failure;
            }
            try {
                if (channel != null && channel.isOpen()) {
                    channel.close();
                }
            } catch (IOException failure) {
                if (cleanupFailure == null) {
                    cleanupFailure = failure;
                } else {
                    cleanupFailure.addSuppressed(failure);
                }
            } finally {
                jvmLock.lock.unlock();
                releaseJvmLock(path, jvmLock);
                removeHeldIfEmpty();
            }
            if (cleanupFailure != null) {
                e.addSuppressed(cleanupFailure);
            }
            throw e;
        }
    }

    private static Object createAndVerifyPrivateFile(Path file) throws IOException {
        verifyPrivateDirectory(file.getParent(), file.getParent().getParent(),
                "Ship operation-lock directory");
        boolean created = false;
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            try {
                Files.createFile(file, PosixFilePermissions.asFileAttribute(FILE_PERMISSIONS));
                created = true;
            } catch (UnsupportedOperationException e) {
                throw new IOException("Ship operation locks require POSIX permissions", e);
            } catch (FileAlreadyExistsException ignored) {
                // A competing controller created it; the checks below remain authoritative.
            }
        }
        Object fileKey = verifyPrivateFile(file, file.getParent(), "Ship operation lock");
        if (created) {
            forceDirectory(file.getParent());
        }
        return fileKey;
    }

    private static byte[] projectIdentity(Path requestedRoot) throws IOException {
        if (requestedRoot == null) {
            throw new IllegalArgumentException("Ship project root must not be null");
        }
        Path absolute = requestedRoot.toAbsolutePath().normalize();
        rejectSymbolicComponents(absolute, "Ship project root");
        Path real = absolute.toRealPath();
        BasicFileAttributes before = Files.readAttributes(
                real, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!real.equals(absolute) || !before.isDirectory() || before.isSymbolicLink()
                || before.fileKey() == null) {
            throw new IOException("Ship project root must be a link-free real directory");
        }
        Map<String, Object> unix;
        try {
            unix = Files.readAttributes(real, "unix:dev,ino", LinkOption.NOFOLLOW_LINKS);
        } catch (UnsupportedOperationException e) {
            throw new IOException("Ship project locking requires Unix filesystem identity", e);
        }
        BasicFileAttributes after = Files.readAttributes(
                real, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!before.fileKey().equals(after.fileKey())) {
            throw new IOException("Ship project root changed while its identity was captured");
        }
        Object device = unix.get("dev");
        Object inode = unix.get("ino");
        if (!(device instanceof Number) || !(inode instanceof Number)) {
            throw new IOException("Ship project root lacks Unix filesystem identity");
        }
        return ("camel-kit.ship.project-lock.v1\0"
                + Long.toUnsignedString(((Number) device).longValue()) + "\0"
                + Long.toUnsignedString(((Number) inode).longValue()))
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static void createPrivateDirectory(
            Path directory, Path parent, String description)
            throws IOException {
        verifyPrivateDirectory(parent, null, "Ship state root");
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            try {
                Files.createDirectory(
                        directory, PosixFilePermissions.asFileAttribute(DIRECTORY_PERMISSIONS));
                forceDirectory(parent);
            } catch (UnsupportedOperationException e) {
                throw new IOException("Ship operation locks require POSIX permissions", e);
            } catch (FileAlreadyExistsException ignored) {
                // A competing controller created it; validate it below.
            }
        }
        verifyPrivateDirectory(directory, parent, description);
    }

    private static void verifyPrivateDirectory(Path directory, Path parent, String description)
            throws IOException {
        if (directory == null) {
            throw new IOException(description + " is unavailable");
        }
        Path absolute = directory.toAbsolutePath().normalize();
        rejectSymbolicComponents(absolute, description);
        BasicFileAttributes attributes = Files.readAttributes(
                absolute, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isDirectory() || attributes.isSymbolicLink()) {
            throw new IOException(description + " must be a real directory");
        }
        requirePermissions(absolute, DIRECTORY_PERMISSIONS, description);
        if (parent != null) {
            requireSameOwner(absolute, parent, description);
        }
    }

    private static Object verifyPrivateFile(Path file, Path parent, String description)
            throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(
                file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isRegularFile() || attributes.isSymbolicLink() || attributes.fileKey() == null) {
            throw new IOException(description + " must be a real regular file");
        }
        requirePermissions(file, FILE_PERMISSIONS, description);
        requireSameOwner(file, parent, description);
        Object links;
        try {
            links = Files.getAttribute(file, "unix:nlink", LinkOption.NOFOLLOW_LINKS);
        } catch (UnsupportedOperationException e) {
            throw new IOException(description + " filesystem lacks hard-link identity", e);
        }
        if (!(links instanceof Number count) || count.longValue() != 1) {
            throw new IOException(description + " must have exactly one hard link");
        }
        return attributes.fileKey();
    }

    private static void requireSameEntry(Path file, Object expectedFileKey, String description)
            throws IOException {
        Object actual = verifyPrivateFile(file, file.getParent(), description);
        if (!expectedFileKey.equals(actual)) {
            throw new IOException(description + " changed while it was opened");
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
                                  + PosixFilePermissions.toString(actual) + "; expected "
                                  + PosixFilePermissions.toString(expected));
        }
    }

    private static void requireSameOwner(Path path, Path parent, String description)
            throws IOException {
        PosixFileAttributes attributes = Files.readAttributes(
                path, PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        PosixFileAttributes parentAttributes = Files.readAttributes(
                parent, PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.owner().equals(parentAttributes.owner())) {
            throw new IOException(description + " owner differs from its protected parent");
        }
    }

    private static void rejectSymbolicComponents(Path path, String description) throws IOException {
        Path current = path.getRoot();
        for (Path component : path) {
            current = current == null ? component : current.resolve(component);
            if (Files.isSymbolicLink(current)) {
                throw new IOException(description + " path contains a symbolic link");
            }
        }
    }

    private static void forceDirectory(Path directory) throws IOException {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (UnsupportedOperationException ignored) {
            // Directory fsync is not exposed by every POSIX provider.
        }
    }

    private static ShipControllerException busy(String label) {
        return new ShipControllerException(
                "operation-in-progress", label + " already has an in-progress controller operation");
    }

    private static void releaseJvmLock(Path path, JvmLock lock) {
        JVM_LOCKS.compute(path, (ignored, current) -> {
            if (current != lock || --current.references < 0) {
                throw new IllegalStateException("Ship JVM operation-lock ownership was lost");
            }
            return current.references == 0 ? null : current;
        });
    }

    private static void removeHeldIfEmpty() {
        if (HELD.get().isEmpty()) {
            HELD.remove();
        }
    }

    static final class Lease implements AutoCloseable {

        private final Path path;
        private HeldLock held;

        private Lease(Path path, HeldLock held) {
            this.path = path;
            this.held = held;
        }

        @Override
        public void close() throws IOException {
            if (held == null) {
                return;
            }
            HeldLock current = HELD.get().get(path);
            if (current != held) {
                throw new IOException("Ship operation lease ownership was lost");
            }
            if (held.kind == LockKind.RUN
                    && held.depth == 1
                    && HELD.get().values().stream()
                            .anyMatch(lock -> lock.kind == LockKind.PROJECT)) {
                throw new IOException("Ship project leases must be released before their run lease");
            }
            if (--held.depth == 0) {
                HELD.get().remove(path);
                IOException failure = null;
                try {
                    if (held.fileLock.isValid()) {
                        held.fileLock.release();
                    }
                } catch (IOException e) {
                    failure = e;
                }
                try {
                    held.channel.close();
                } catch (IOException e) {
                    if (failure == null) {
                        failure = e;
                    } else {
                        failure.addSuppressed(e);
                    }
                } finally {
                    held.jvmLock.lock.unlock();
                    releaseJvmLock(path, held.jvmLock);
                }
                if (HELD.get().isEmpty()) {
                    HELD.remove();
                }
                if (failure != null) {
                    held = null;
                    throw failure;
                }
            }
            held = null;
        }
    }

    private enum LockKind {
        RUN,
        PROJECT
    }

    private static final class HeldLock {

        private final LockKind kind;
        private final JvmLock jvmLock;
        private final FileChannel channel;
        private final FileLock fileLock;
        private int depth = 1;

        private HeldLock(
                         LockKind kind, JvmLock jvmLock, FileChannel channel, FileLock fileLock) {
            this.kind = kind;
            this.jvmLock = jvmLock;
            this.channel = channel;
            this.fileLock = fileLock;
        }
    }

    private static final class JvmLock {

        private final ReentrantLock lock = new ReentrantLock();
        private int references;
    }
}
