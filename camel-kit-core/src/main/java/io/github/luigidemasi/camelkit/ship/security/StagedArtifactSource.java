package io.github.luigidemasi.camelkit.ship.security;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Purpose-bound, descriptor-relative access to one stopped worker's staged artifacts.
 *
 * <p>
 * The source channel for an artifact is opened exactly once. Its bytes are hashed while they are copied to the caller's
 * controller-owned quarantine channel; no verified path or source channel escapes this boundary.
 *
 * <p>
 * The caller must prove the worker process tree is stopped and protect the output ancestry from peer replacement. This
 * class does not make a concurrently writable same-identity tree safe.
 */
public final class StagedArtifactSource {

    private StagedArtifactSource() {
    }

    public static Session open(Path requestedRoot) throws IOException {
        return Session.open(requestedRoot);
    }

    /** Digest and size observed while copying the exact opened source stream. */
    public record CopyResult(String digest, long size) {
    }

    /** Held attempt-output root for one bounded import batch. */
    public static final class Session implements AutoCloseable {

        private static final String UNIX_ATTRIBUTES = "unix:dev,ino,nlink,size,lastModifiedTime,ctime,mode";
        private static final int FILE_TYPE_MASK = 0170000;
        private static final int DIRECTORY_TYPE = 0040000;
        private static final int REGULAR_FILE_TYPE = 0100000;
        private static final Set<OpenOption> READ_OPTIONS = Set.of(
                StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);

        private final Path path;
        private final SecureDirectoryStream<Path> root;
        private final UnixIdentity identity;

        private Session(Path path, SecureDirectoryStream<Path> root, UnixIdentity identity) {
            this.path = path;
            this.root = root;
            this.identity = identity;
        }

        private static Session open(Path requested) throws IOException {
            if (requested == null) {
                throw new IllegalArgumentException("Staged artifact root is required");
            }
            Path path = requested.toAbsolutePath().normalize();
            if (path.getRoot() == null || path.getNameCount() == 0) {
                throw new IOException("Staged artifact root must be a non-root absolute directory");
            }
            DirectoryStream<Path> opened = Files.newDirectoryStream(path.getRoot());
            if (!(opened instanceof SecureDirectoryStream<?> stream)) {
                opened.close();
                throw new IOException("Staged artifact filesystem does not provide SecureDirectoryStream");
            }
            @SuppressWarnings("unchecked")
            SecureDirectoryStream<Path> current = (SecureDirectoryStream<Path>) stream;
            Path lexical = path.getRoot();
            try {
                requireBasicDirectory(attributes(current, null),
                        "Filesystem root is not a stable real directory");
                for (Path component : path) {
                    Path name = Path.of(component.toString());
                    lexical = lexical.resolve(name);
                    String description = "Staged artifact path " + lexical;
                    BasicFileAttributes basic = attributes(current, name);
                    boolean stagedRoot = lexical.equals(path);
                    UnixIdentity expected = null;
                    if (stagedRoot) {
                        expected = sampleUnix(lexical, basic.fileKey(), description);
                        requireDirectory(basic, expected, null,
                                "Staged artifact path contains a non-directory or symbolic entry");
                    } else {
                        requireBasicDirectory(
                                basic, "Staged artifact path contains a non-directory or symbolic entry");
                    }

                    SecureDirectoryStream<Path> child = null;
                    try {
                        child = current.newDirectoryStream(name, LinkOption.NOFOLLOW_LINKS);
                        BasicFileAttributes childAttributes = attributes(child, null);
                        if (stagedRoot) {
                            requireDirectory(childAttributes, expected, null,
                                    "Staged artifact root changed while it was opened");
                            UnixIdentity rebound = sampleUnix(lexical, expected.fileKey(), description);
                            if (!expected.equals(rebound)) {
                                throw new IOException("Staged artifact root changed while it was opened");
                            }
                        } else {
                            requireBasicDirectory(
                                    childAttributes, "Staged artifact directory changed while it was opened");
                            if (!basic.fileKey().equals(childAttributes.fileKey())) {
                                throw new IOException("Staged artifact directory changed while it was opened");
                            }
                        }
                    } catch (IOException | RuntimeException e) {
                        if (child != null) {
                            try {
                                child.close();
                            } catch (IOException closeFailure) {
                                e.addSuppressed(closeFailure);
                            }
                        }
                        throw e;
                    }
                    try {
                        current.close();
                    } catch (IOException e) {
                        try {
                            child.close();
                        } catch (IOException closeFailure) {
                            e.addSuppressed(closeFailure);
                        }
                        throw e;
                    }
                    current = child;
                }
                BasicFileAttributes held = attributes(current, null);
                UnixIdentity identity = sampleUnix(path, held.fileKey(), "Staged artifact root");
                requireDirectory(held, identity, null, "Staged artifact root is not a stable real directory");
                return new Session(path, current, identity);
            } catch (IOException | RuntimeException e) {
                try {
                    current.close();
                } catch (IOException closeFailure) {
                    e.addSuppressed(closeFailure);
                }
                throw e;
            }
        }

        /** Copies and hashes one artifact, rejecting the first byte beyond its declared size. */
        public CopyResult copyTo(String relativePath, long declaredSize, WritableByteChannel target)
                throws IOException {
            if (target == null || declaredSize < 0
                    || declaredSize > ShipTreePolicy.current().maxFileBytes()) {
                throw new IllegalArgumentException("Staged artifact target and policy-bounded size are required");
            }
            String canonical = ShipTreePolicy.requireCanonicalRelativePath(relativePath);
            String[] components = canonical.split("/");
            if (components.length - 1 > ShipTreePolicy.current().maxDepth()) {
                throw new IOException("Staged artifact path exceeds the depth limit");
            }
            return copyTo(root, path, components, 0, declaredSize, target);
        }

        private CopyResult copyTo(
                SecureDirectoryStream<Path> directory,
                Path lexicalDirectory,
                String[] components,
                int index,
                long declaredSize,
                WritableByteChannel target)
                throws IOException {
            Path name = Path.of(components[index]);
            Path lexical = lexicalDirectory.resolve(name);
            String description = "Staged artifact " + lexical;
            BasicFileAttributes basicBefore = attributes(directory, name);
            UnixIdentity before = sampleUnix(lexical, basicBefore.fileKey(), description);
            if (index < components.length - 1) {
                requireDirectory(basicBefore, before, identity.device(),
                        "Staged artifact path contains a non-directory or symbolic entry");
                try (SecureDirectoryStream<Path> child
                        = directory.newDirectoryStream(name, LinkOption.NOFOLLOW_LINKS)) {
                    requireDirectory(attributes(child, null), before, identity.device(),
                            "Staged artifact directory changed while it was opened");
                    UnixIdentity rebound = sampleUnix(lexical, before.fileKey(), description);
                    if (!before.equals(rebound)) {
                        throw new IOException("Staged artifact directory changed while it was opened");
                    }
                    CopyResult result = copyTo(
                            child, lexical, components, index + 1, declaredSize, target);
                    BasicFileAttributes basicAfter = attributes(directory, name);
                    UnixIdentity after = sampleUnix(lexical, before.fileKey(), description);
                    requireDirectory(basicAfter, after, identity.device(),
                            "Staged artifact directory changed while it was read");
                    if (!before.equals(after)) {
                        throw new IOException("Staged artifact directory changed while it was read");
                    }
                    return result;
                }
            }

            requireRegular(basicBefore, before, identity.device(),
                    "Staged artifact is missing, symbolic, hard-linked, or not regular");
            MessageDigest digest = messageDigest();
            long size = 0;
            try (SeekableByteChannel input = directory.newByteChannel(name, READ_OPTIONS)) {
                ByteBuffer buffer = ByteBuffer.allocateDirect(64 * 1024);
                int read;
                do {
                    buffer.clear();
                    long remainingThroughFirstExcessByte = declaredSize - size + 1;
                    buffer.limit((int) Math.min(buffer.capacity(), remainingThroughFirstExcessByte));
                    read = input.read(buffer);
                    if (read == -1) {
                        break;
                    }
                    if (read == 0) {
                        continue;
                    }
                    size = Math.addExact(size, read);
                    if (size > declaredSize) {
                        throw new IOException("Staged artifact exceeds its declared size");
                    }
                    buffer.flip();
                    digest.update(buffer.asReadOnlyBuffer());
                    while (buffer.hasRemaining()) {
                        target.write(buffer);
                    }
                } while (true);
            } catch (ArithmeticException e) {
                throw new IOException("Staged artifact size overflowed", e);
            }
            BasicFileAttributes basicAfter = attributes(directory, name);
            UnixIdentity after = sampleUnix(lexical, before.fileKey(), description);
            requireRegular(basicAfter, after, identity.device(), "Staged artifact changed while it was read");
            if (!before.equals(after) || size != before.size()) {
                throw new IOException("Staged artifact changed while it was read");
            }
            return new CopyResult("sha256:" + HexFormat.of().formatHex(digest.digest()), size);
        }

        @Override
        public void close() throws IOException {
            IOException failure = null;
            try {
                BasicFileAttributes held = attributes(root, null);
                UnixIdentity rebound = sampleUnix(path, identity.fileKey(), "Staged artifact root");
                requireDirectory(held, rebound, null,
                        "Staged artifact root changed while artifacts were imported");
                if (!identity.equals(rebound)) {
                    throw new IOException("Staged artifact root changed while artifacts were imported");
                }
            } catch (IOException e) {
                failure = e;
            }
            try {
                root.close();
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

        private static BasicFileAttributes attributes(SecureDirectoryStream<Path> directory, Path name)
                throws IOException {
            BasicFileAttributeView view = name == null
                    ? directory.getFileAttributeView(BasicFileAttributeView.class)
                    : directory.getFileAttributeView(
                            name, BasicFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
            if (view == null) {
                throw new IOException("Staged artifact filesystem lacks descriptor-relative attributes");
            }
            return view.readAttributes();
        }

        private static UnixIdentity sampleUnix(Path path, Object expectedFileKey, String description)
                throws IOException {
            if (expectedFileKey == null) {
                throw new IOException(description + " lacks a stable file key");
            }
            try {
                BasicFileAttributes basicBefore = Files.readAttributes(
                        path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (!expectedFileKey.equals(basicBefore.fileKey())) {
                    throw new IOException(description + " changed before Unix identity sampling");
                }
                Map<String, Object> values = Files.readAttributes(
                        path, UNIX_ATTRIBUTES, LinkOption.NOFOLLOW_LINKS);
                BasicFileAttributes basicAfter = Files.readAttributes(
                        path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (!expectedFileKey.equals(basicAfter.fileKey())) {
                    throw new IOException(description + " changed during Unix identity sampling");
                }
                UnixIdentity identity = new UnixIdentity(
                        expectedFileKey,
                        requiredLong(values, "dev", description),
                        requiredLong(values, "ino", description),
                        requiredLong(values, "nlink", description),
                        requiredLong(values, "size", description),
                        requiredTime(values, "lastModifiedTime", description).to(TimeUnit.NANOSECONDS),
                        requiredTime(values, "ctime", description).to(TimeUnit.NANOSECONDS),
                        requiredInt(values, "mode", description));
                requireBasicBinding(basicBefore, identity, description);
                requireBasicBinding(basicAfter, identity, description);
                return identity;
            } catch (UnsupportedOperationException | IllegalArgumentException e) {
                throw new IOException(description + " filesystem lacks stable Unix identity metadata", e);
            }
        }

        private static long requiredLong(Map<String, Object> values, String name, String description)
                throws IOException {
            Object value = values.get(name);
            if (!(value instanceof Number number)) {
                throw new IOException(description + " lacks Unix attribute " + name);
            }
            return number.longValue();
        }

        private static int requiredInt(Map<String, Object> values, String name, String description)
                throws IOException {
            long value = requiredLong(values, name, description);
            if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
                throw new IOException(description + " has an invalid Unix attribute " + name);
            }
            return (int) value;
        }

        private static FileTime requiredTime(Map<String, Object> values, String name, String description)
                throws IOException {
            Object value = values.get(name);
            if (!(value instanceof FileTime time)) {
                throw new IOException(description + " lacks Unix attribute " + name);
            }
            return time;
        }

        private static void requireBasicDirectory(BasicFileAttributes attributes, String message)
                throws IOException {
            if (!attributes.isDirectory() || attributes.isSymbolicLink() || attributes.fileKey() == null) {
                throw new IOException(message);
            }
        }

        private static void requireDirectory(
                BasicFileAttributes basic, UnixIdentity unix, Long requiredDevice, String message)
                throws IOException {
            if (!basic.isDirectory() || basic.isSymbolicLink() || basic.fileKey() == null
                    || (unix.mode() & FILE_TYPE_MASK) != DIRECTORY_TYPE) {
                throw new IOException(message);
            }
            requireBasicBinding(basic, unix, message);
            requireDevice(unix, requiredDevice, message);
        }

        private static void requireRegular(
                BasicFileAttributes basic, UnixIdentity unix, long requiredDevice, String message)
                throws IOException {
            if (!basic.isRegularFile() || basic.isSymbolicLink() || basic.fileKey() == null
                    || (unix.mode() & FILE_TYPE_MASK) != REGULAR_FILE_TYPE
                    || unix.linkCount() != 1) {
                throw new IOException(message);
            }
            requireBasicBinding(basic, unix, message);
            requireDevice(unix, requiredDevice, message);
        }

        private static void requireBasicBinding(
                BasicFileAttributes basic, UnixIdentity unix, String description)
                throws IOException {
            if (!unix.fileKey().equals(basic.fileKey())
                    || unix.size() != basic.size()
                    || unix.modifiedNanos() != basic.lastModifiedTime().to(TimeUnit.NANOSECONDS)) {
                throw new IOException(description);
            }
        }

        private static void requireDevice(UnixIdentity unix, Long requiredDevice, String message)
                throws IOException {
            if (requiredDevice != null && unix.device() != requiredDevice) {
                throw new IOException(message + ": filesystem device crossing");
            }
        }

        private static MessageDigest messageDigest() {
            try {
                return MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("SHA-256 is not available", e);
            }
        }

        private record UnixIdentity(
                Object fileKey,
                long device,
                long inode,
                long linkCount,
                long size,
                long modifiedNanos,
                long changedNanos,
                int mode) {
        }
    }
}
