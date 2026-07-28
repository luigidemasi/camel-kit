package io.github.luigidemasi.camelkit.ship.catalog;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
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
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Reads one bounded catalog artifact through descriptor-relative, no-follow handles. This is a descriptive snapshot,
 * not a certifying filesystem capability: Java 17 cannot query the identity of the final opened byte channel.
 */
final class CatalogArtifactReader {

    private static final String UNIX_ATTRIBUTES = "unix:dev,ino,nlink,size,lastModifiedTime,ctime,mode,uid,gid";
    private static final int FILE_TYPE_MASK = 0170000;
    private static final int DIRECTORY_TYPE = 0040000;
    private static final int REGULAR_FILE_TYPE = 0100000;
    private static final Set<OpenOption> READ_OPTIONS = Set.of(
            StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);

    private CatalogArtifactReader() {
    }

    static byte[] read(Path requestedRoot, Path requestedFile, long maximumBytes) throws IOException {
        if (maximumBytes < 1 || maximumBytes > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Catalog artifact byte limit is invalid");
        }
        Path requested = Objects.requireNonNull(requestedRoot, "requestedRoot must not be null")
                .toAbsolutePath().normalize();
        Path file = Objects.requireNonNull(requestedFile, "requestedFile must not be null")
                .toAbsolutePath().normalize();
        if (!file.startsWith(requested) || file.equals(requested)) {
            throw new IOException("Catalog artifact is outside its repository root");
        }
        Path relative = requested.relativize(file);
        int count = relative.getNameCount();
        if (count < 2) {
            throw new IOException("Catalog artifact path lacks a parent directory");
        }
        if (Files.isSymbolicLink(requested)
                || !Files.isDirectory(requested, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Catalog repository root is not a real directory");
        }
        Path root = requested.toRealPath();
        try (BoundRoot boundRoot = openRoot(root)) {
            byte[] result = readFrom(
                    boundRoot.stream(), root, relative, 0, boundRoot.identity().device(), maximumBytes);
            boundRoot.validate();
            return result;
        }
    }

    private static byte[] readFrom(
            SecureDirectoryStream<Path> directory,
            Path lexicalDirectory,
            Path relative,
            int index,
            long rootDevice,
            long maximumBytes)
            throws IOException {
        Path name = singleName(relative.getName(index));
        if (index == relative.getNameCount() - 1) {
            return readFile(directory, name, lexicalDirectory.resolve(name), rootDevice, maximumBytes);
        }
        Path lexicalChild = lexicalDirectory.resolve(name);
        try (BoundDirectory child = openDirectory(directory, name, lexicalChild, rootDevice)) {
            return readFrom(child.stream(), lexicalChild, relative, index + 1, rootDevice, maximumBytes);
        }
    }

    private static BoundRoot openRoot(Path root) throws IOException {
        DirectoryStream<Path> opened = Files.newDirectoryStream(root);
        if (!(opened instanceof SecureDirectoryStream<?>)) {
            opened.close();
            throw new IOException("Catalog repository filesystem lacks descriptor-relative directory access");
        }
        @SuppressWarnings("unchecked")
        SecureDirectoryStream<Path> secure = (SecureDirectoryStream<Path>) opened;
        try {
            BasicFileAttributes held = attributes(secure, null);
            UnixIdentity identity = sampleUnix(root, held.fileKey());
            requireDirectory(held, identity, identity.device());
            return new BoundRoot(root, secure, identity);
        } catch (IOException | RuntimeException e) {
            try {
                secure.close();
            } catch (IOException closeFailure) {
                e.addSuppressed(closeFailure);
            }
            throw e;
        }
    }

    private static BoundDirectory openDirectory(
            SecureDirectoryStream<Path> parent,
            Path name,
            Path lexicalPath,
            long rootDevice)
            throws IOException {
        BasicFileAttributes named = attributes(parent, name);
        UnixIdentity expected = sampleUnix(lexicalPath, named.fileKey());
        requireDirectory(named, expected, rootDevice);
        SecureDirectoryStream<Path> opened = null;
        try {
            opened = parent.newDirectoryStream(name, LinkOption.NOFOLLOW_LINKS);
            requireDirectory(attributes(opened, null), expected, rootDevice);
            UnixIdentity rebound = sampleUnix(lexicalPath, expected.fileKey());
            if (!expected.equals(rebound)) {
                throw new IOException("Catalog repository directory changed while it was opened");
            }
            return new BoundDirectory(parent, name, lexicalPath, opened, expected, rootDevice);
        } catch (IOException | RuntimeException e) {
            if (opened != null) {
                try {
                    opened.close();
                } catch (IOException closeFailure) {
                    e.addSuppressed(closeFailure);
                }
            }
            throw e;
        }
    }

    private static byte[] readFile(
            SecureDirectoryStream<Path> parent,
            Path name,
            Path lexicalPath,
            long rootDevice,
            long maximumBytes)
            throws IOException {
        BasicFileAttributes basicBefore = attributes(parent, name);
        UnixIdentity before = sampleUnix(lexicalPath, basicBefore.fileKey());
        requireRegularFile(basicBefore, before, rootDevice);
        if (before.size() <= 0 || before.size() > maximumBytes) {
            throw new IOException("Catalog artifact has an unsafe size");
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.toIntExact(before.size()));
        try (SeekableByteChannel channel = parent.newByteChannel(name, READ_OPTIONS)) {
            ByteBuffer buffer = ByteBuffer.allocate(16_384);
            long total = 0;
            int read;
            while ((read = channel.read(buffer)) != -1) {
                if (read == 0) {
                    continue;
                }
                total = Math.addExact(total, read);
                if (total > before.size() || total > maximumBytes) {
                    throw new IOException("Catalog artifact changed size while it was read");
                }
                output.write(buffer.array(), 0, read);
                buffer.clear();
            }
        } catch (ArithmeticException e) {
            throw new IOException("Catalog artifact size accounting overflowed", e);
        }

        BasicFileAttributes basicAfter = attributes(parent, name);
        UnixIdentity after = sampleUnix(lexicalPath, before.fileKey());
        requireRegularFile(basicAfter, after, rootDevice);
        if (!before.equals(after) || output.size() != before.size()) {
            throw new IOException("Catalog artifact changed while it was read");
        }
        return output.toByteArray();
    }

    private static BasicFileAttributes attributes(SecureDirectoryStream<Path> directory, Path name)
            throws IOException {
        BasicFileAttributeView view = name == null
                ? directory.getFileAttributeView(BasicFileAttributeView.class)
                : directory.getFileAttributeView(
                        name, BasicFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (view == null) {
            throw new IOException("Catalog repository lacks descriptor-relative basic attributes");
        }
        return view.readAttributes();
    }

    private static UnixIdentity sampleUnix(Path path, Object expectedKey) throws IOException {
        if (expectedKey == null) {
            throw new IOException("Catalog repository entry lacks a stable file key");
        }
        try {
            BasicFileAttributes before = Files.readAttributes(
                    path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!expectedKey.equals(before.fileKey())) {
                throw new IOException("Catalog repository entry changed before Unix metadata sampling");
            }
            Map<String, Object> values = Files.readAttributes(
                    path, UNIX_ATTRIBUTES, LinkOption.NOFOLLOW_LINKS);
            BasicFileAttributes after = Files.readAttributes(
                    path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!expectedKey.equals(after.fileKey())) {
                throw new IOException("Catalog repository entry changed during Unix metadata sampling");
            }
            UnixIdentity identity = new UnixIdentity(
                    expectedKey,
                    requiredLong(values, "dev"),
                    requiredLong(values, "ino"),
                    requiredLong(values, "nlink"),
                    requiredLong(values, "size"),
                    requiredTime(values, "lastModifiedTime").to(TimeUnit.NANOSECONDS),
                    requiredTime(values, "ctime").to(TimeUnit.NANOSECONDS),
                    before.creationTime().to(TimeUnit.NANOSECONDS),
                    requiredInt(values, "mode"),
                    requiredUnixId(values, "uid"),
                    requiredUnixId(values, "gid"));
            requireBasicBinding(before, identity);
            requireBasicBinding(after, identity);
            return identity;
        } catch (UnsupportedOperationException | IllegalArgumentException e) {
            throw new IOException("Catalog repository lacks stable Unix identity metadata", e);
        }
    }

    private static void requireDirectory(
            BasicFileAttributes basic, UnixIdentity unix, long rootDevice)
            throws IOException {
        if (!basic.isDirectory() || basic.isSymbolicLink()
                || (unix.mode() & FILE_TYPE_MASK) != DIRECTORY_TYPE) {
            throw new IOException("Catalog repository path contains a non-directory entry");
        }
        requireBasicBinding(basic, unix);
        requireSameDevice(unix, rootDevice);
    }

    private static void requireRegularFile(
            BasicFileAttributes basic, UnixIdentity unix, long rootDevice)
            throws IOException {
        if (!basic.isRegularFile() || basic.isSymbolicLink()
                || (unix.mode() & FILE_TYPE_MASK) != REGULAR_FILE_TYPE) {
            throw new IOException("Catalog artifact is not a stable regular file");
        }
        requireBasicBinding(basic, unix);
        requireSameDevice(unix, rootDevice);
        if (unix.linkCount() != 1) {
            throw new IOException("Catalog artifact is hard-linked");
        }
    }

    private static void requireBasicBinding(BasicFileAttributes basic, UnixIdentity unix)
            throws IOException {
        if (basic.fileKey() == null
                || !unix.fileKey().equals(basic.fileKey())
                || basic.size() != unix.size()
                || basic.lastModifiedTime().to(TimeUnit.NANOSECONDS) != unix.modifiedNanos()
                || basic.creationTime().to(TimeUnit.NANOSECONDS) != unix.createdNanos()) {
            throw new IOException("Catalog repository entry changed across its identity sample");
        }
    }

    private static void requireSameDevice(UnixIdentity unix, long rootDevice) throws IOException {
        if (unix.device() != rootDevice) {
            throw new IOException("Catalog artifact path crosses its repository filesystem device");
        }
    }

    private static long requiredLong(Map<String, Object> values, String name) throws IOException {
        Object value = values.get(name);
        if (!(value instanceof Number number)) {
            throw new IOException("Catalog repository lacks a required Unix attribute");
        }
        return number.longValue();
    }

    private static int requiredInt(Map<String, Object> values, String name) throws IOException {
        long value = requiredLong(values, name);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IOException("Catalog repository returned an invalid Unix attribute");
        }
        return (int) value;
    }

    private static long requiredUnixId(Map<String, Object> values, String name) throws IOException {
        Object value = values.get(name);
        if (!(value instanceof Number number)) {
            throw new IOException("Catalog repository lacks a required Unix identity attribute");
        }
        long result = value instanceof Integer integer
                ? Integer.toUnsignedLong(integer)
                : number.longValue();
        if (result < 0 || result > 0xffff_ffffL) {
            throw new IOException("Catalog repository returned an invalid Unix identity attribute");
        }
        return result;
    }

    private static FileTime requiredTime(Map<String, Object> values, String name) throws IOException {
        Object value = values.get(name);
        if (!(value instanceof FileTime time)) {
            throw new IOException("Catalog repository lacks a required Unix time attribute");
        }
        return time;
    }

    private static Path singleName(Path name) throws IOException {
        if (name == null || name.getNameCount() != 1 || name.toString().isBlank()
                || ".".equals(name.toString()) || "..".equals(name.toString())) {
            throw new IOException("Catalog artifact path contains an unsafe segment");
        }
        return name;
    }

    private record UnixIdentity(
            Object fileKey,
            long device,
            long inode,
            long linkCount,
            long size,
            long modifiedNanos,
            long changedNanos,
            long createdNanos,
            int mode,
            long userId,
            long groupId) {
    }

    private static final class BoundRoot implements AutoCloseable {
        private final Path path;
        private final SecureDirectoryStream<Path> stream;
        private final UnixIdentity identity;

        private BoundRoot(Path path, SecureDirectoryStream<Path> stream, UnixIdentity identity) {
            this.path = path;
            this.stream = stream;
            this.identity = identity;
        }

        private SecureDirectoryStream<Path> stream() {
            return stream;
        }

        private UnixIdentity identity() {
            return identity;
        }

        private void validate() throws IOException {
            requireDirectory(attributes(stream, null), identity, identity.device());
            if (!identity.equals(sampleUnix(path, identity.fileKey()))) {
                throw new IOException("Catalog repository root changed while an artifact was read");
            }
        }

        @Override
        public void close() throws IOException {
            IOException failure = null;
            try {
                validate();
            } catch (IOException e) {
                failure = e;
            }
            try {
                stream.close();
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
    }

    private static final class BoundDirectory implements AutoCloseable {
        private final SecureDirectoryStream<Path> parent;
        private final Path name;
        private final Path lexicalPath;
        private final SecureDirectoryStream<Path> stream;
        private final UnixIdentity identity;
        private final long rootDevice;

        private BoundDirectory(
                               SecureDirectoryStream<Path> parent,
                               Path name,
                               Path lexicalPath,
                               SecureDirectoryStream<Path> stream,
                               UnixIdentity identity,
                               long rootDevice) {
            this.parent = parent;
            this.name = name;
            this.lexicalPath = lexicalPath;
            this.stream = stream;
            this.identity = identity;
            this.rootDevice = rootDevice;
        }

        private SecureDirectoryStream<Path> stream() {
            return stream;
        }

        private void validate() throws IOException {
            requireDirectory(attributes(stream, null), identity, rootDevice);
            requireDirectory(attributes(parent, name), identity, rootDevice);
            if (!identity.equals(sampleUnix(lexicalPath, identity.fileKey()))) {
                throw new IOException("Catalog repository directory changed while it was used");
            }
        }

        @Override
        public void close() throws IOException {
            IOException failure = null;
            try {
                validate();
            } catch (IOException e) {
                failure = e;
            }
            try {
                stream.close();
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
    }
}
