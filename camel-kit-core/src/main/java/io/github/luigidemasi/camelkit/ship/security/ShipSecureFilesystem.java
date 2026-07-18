package io.github.luigidemasi.camelkit.ship.security;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
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
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import io.github.luigidemasi.camelkit.ship.security.ProjectSnapshot.DirectoryEntry;
import io.github.luigidemasi.camelkit.ship.security.ProjectSnapshot.FileEntry;
import io.github.luigidemasi.camelkit.ship.security.ShipTreePolicy.Classification;

/**
 * Internal read-only, descriptor-relative filesystem policy boundary for Ship trees. This Java 17 implementation is a
 * phase-one, non-certifying boundary: metadata samples cannot exclude same-device bind mounts, inode or file-key reuse
 * between samples, or replacement of the final entry while a read channel is open. Public execution remains gated on
 * the later OS helper that binds mount policy and identity checks to the exact opened descriptor.
 */
final class ShipSecureFilesystem {

    private static final String UNIX_ATTRIBUTES = "unix:dev,ino,nlink,size,lastModifiedTime,ctime,mode,uid,gid";
    private static final int FILE_TYPE_MASK = 0170000;
    private static final int DIRECTORY_TYPE = 0040000;
    private static final int REGULAR_FILE_TYPE = 0100000;

    private ShipSecureFilesystem() {
    }

    static SecureRoot open(Path requestedRoot, String label, ShipTreePolicy policy) throws IOException {
        Path root = requireRootPath(requestedRoot, label, policy);
        DirectoryStream<Path> stream = Files.newDirectoryStream(root);
        if (!(stream instanceof SecureDirectoryStream<?>)) {
            stream.close();
            throw unsupported(label + " filesystem does not provide SecureDirectoryStream: " + root);
        }
        @SuppressWarnings("unchecked")
        SecureDirectoryStream<Path> secure = (SecureDirectoryStream<Path>) stream;
        return bindOpenedRoot(root, label, policy, secure);
    }

    private static Path requireRootPath(Path requestedRoot, String label, ShipTreePolicy policy)
            throws IOException {
        if (requestedRoot == null || policy == null || label == null || label.isBlank()) {
            throw new IllegalArgumentException("Ship secure root, label, and policy are required");
        }
        Path absolute = requestedRoot.toAbsolutePath().normalize();
        if (!policy.isAllowedAbsolutePath(absolute)) {
            throw unsafe(label + " is outside the allowed project-root lineage");
        }
        rejectSymbolicComponents(absolute, label);
        Path root = absolute.toRealPath();
        if (!root.equals(absolute)
                || !policy.isAllowedAbsolutePath(root)
                || Files.isSymbolicLink(root)
                || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw unsafe(label + " must be an allowed, link-free real directory: " + absolute);
        }
        return root;
    }

    private static SecureRoot bindOpenedRoot(
            Path root,
            String label,
            ShipTreePolicy policy,
            SecureDirectoryStream<Path> secure)
            throws IOException {
        try {
            BasicFileAttributes held = attributes(secure, null);
            if (!held.isDirectory() || held.fileKey() == null) {
                throw unsupported(label + " lacks a stable descriptor-relative identity: " + root);
            }
            UnixIdentity sampled = sampleUnixIdentity(root, held.fileKey(), label + " root");
            requireDirectory(held, sampled, sampled.device(), label + " root");
            BasicFileAttributes rebound = attributes(secure, null);
            requireDirectory(rebound, sampled, sampled.device(), label + " root");
            return new SecureRoot(
                    root, label, policy, secure, sampled, rootIdentity(root, sampled));
        } catch (IOException | RuntimeException e) {
            try {
                secure.close();
            } catch (IOException closeFailure) {
                e.addSuppressed(closeFailure);
            }
            throw e;
        }
    }

    private static void rejectSymbolicComponents(Path path, String label) throws IOException {
        Path current = path.getRoot();
        for (Path component : path) {
            current = current == null ? component : current.resolve(component);
            if (Files.isSymbolicLink(current)) {
                throw unsafe(label + " path contains a symbolic link: " + current);
            }
        }
    }

    private static BasicFileAttributes attributes(SecureDirectoryStream<Path> directory, Path name)
            throws IOException {
        BasicFileAttributeView view = name == null
                ? directory.getFileAttributeView(BasicFileAttributeView.class)
                : directory.getFileAttributeView(
                        name, BasicFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (view == null) {
            throw unsupported("Secure filesystem does not expose basic descriptor-relative attributes");
        }
        return view.readAttributes();
    }

    private static UnixIdentity sampleUnixIdentity(
            Path lexicalPath, Object expectedFileKey, String description)
            throws IOException {
        if (expectedFileKey == null) {
            throw unsupported(description + " lacks a stable file key");
        }
        try {
            BasicFileAttributes before = Files.readAttributes(
                    lexicalPath, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!expectedFileKey.equals(before.fileKey())) {
                throw concurrentMutation(description + " changed before Unix metadata sampling");
            }
            Map<String, Object> values = Files.readAttributes(
                    lexicalPath, UNIX_ATTRIBUTES, LinkOption.NOFOLLOW_LINKS);
            BasicFileAttributes after = Files.readAttributes(
                    lexicalPath, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!expectedFileKey.equals(after.fileKey())) {
                throw concurrentMutation(description + " changed during Unix metadata sampling");
            }
            UnixIdentity identity = new UnixIdentity(
                    expectedFileKey,
                    requiredLong(values, "dev", description),
                    requiredLong(values, "ino", description),
                    requiredLong(values, "nlink", description),
                    requiredLong(values, "size", description),
                    requiredTime(values, "lastModifiedTime", description)
                            .to(TimeUnit.NANOSECONDS),
                    requiredTime(values, "ctime", description).to(TimeUnit.NANOSECONDS),
                    requiredInt(values, "mode", description),
                    requiredUnixId(values, "uid", description),
                    requiredUnixId(values, "gid", description));
            if (identity.linkCount() < 1 || identity.size() < 0) {
                throw unsupported(description + " returned invalid Unix metadata");
            }
            requireBasicBinding(before, identity, description);
            requireBasicBinding(after, identity, description);
            return identity;
        } catch (UnsupportedOperationException | IllegalArgumentException e) {
            throw new ShipFilesystemException(
                    ShipFilesystemException.SECURE_FILESYSTEM_UNSUPPORTED,
                    description + " filesystem cannot provide stable Unix identity metadata",
                    e);
        }
    }

    private static long requiredLong(
            Map<String, Object> values, String name, String description)
            throws IOException {
        Object value = values.get(name);
        if (!(value instanceof Number number)) {
            throw unsupported(description + " lacks Unix attribute " + name);
        }
        return number.longValue();
    }

    private static int requiredInt(
            Map<String, Object> values, String name, String description)
            throws IOException {
        long value = requiredLong(values, name, description);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw unsupported(description + " has an invalid Unix attribute " + name);
        }
        return (int) value;
    }

    private static long requiredUnixId(
            Map<String, Object> values, String name, String description)
            throws IOException {
        Object value = values.get(name);
        if (!(value instanceof Number number)) {
            throw unsupported(description + " lacks Unix attribute " + name);
        }
        long result = value instanceof Integer integer
                ? Integer.toUnsignedLong(integer)
                : number.longValue();
        if (result < 0 || result > 0xffff_ffffL) {
            throw unsupported(description + " has an invalid Unix attribute " + name);
        }
        return result;
    }

    private static FileTime requiredTime(
            Map<String, Object> values, String name, String description)
            throws IOException {
        Object value = values.get(name);
        if (!(value instanceof FileTime time)) {
            throw unsupported(description + " lacks Unix attribute " + name);
        }
        return time;
    }

    private static void requireDirectory(
            BasicFileAttributes basic,
            UnixIdentity unix,
            long rootDevice,
            String description)
            throws IOException {
        if (!basic.isDirectory() || basic.isSymbolicLink() || basic.fileKey() == null
                || (unix.mode() & FILE_TYPE_MASK) != DIRECTORY_TYPE) {
            throw unsafe(description + " is not a stable real directory");
        }
        requireBasicBinding(basic, unix, description);
        requireSameDevice(unix, rootDevice, description);
    }

    private static void requireRegularFile(
            BasicFileAttributes basic,
            UnixIdentity unix,
            long rootDevice,
            String description)
            throws IOException {
        if (!basic.isRegularFile() || basic.isSymbolicLink() || basic.fileKey() == null
                || (unix.mode() & FILE_TYPE_MASK) != REGULAR_FILE_TYPE) {
            throw unsafe(description + " is not a stable regular file");
        }
        requireBasicBinding(basic, unix, description);
        requireSameDevice(unix, rootDevice, description);
        if (unix.linkCount() != 1) {
            throw unsafe("Ship tree contains a hard-linked file: " + description);
        }
    }

    private static void requireBasicBinding(
            BasicFileAttributes basic, UnixIdentity unix, String description)
            throws IOException {
        if (basic.fileKey() == null
                || !unix.fileKey().equals(basic.fileKey())
                || basic.size() != unix.size()
                || basic.lastModifiedTime().to(TimeUnit.NANOSECONDS) != unix.modifiedNanos()) {
            throw concurrentMutation(description + " changed across its identity sample");
        }
    }

    private static void requireSameDevice(
            UnixIdentity identity, long rootDevice, String description)
            throws IOException {
        if (identity.device() != rootDevice) {
            throw unsafe(description + " crosses the trusted project filesystem device");
        }
        // st_dev rejects ordinary mount crossings. Pure Java 17 cannot certify that a
        // same-device entry is not a bind mount; the later OS helper must enforce that boundary.
    }

    private static String rootIdentity(Path root, UnixIdentity identity) {
        return digestBytes(("camel-kit.ship.root.v4\0" + root + "\0"
                            + Long.toUnsignedString(identity.device()) + "\0"
                            + Long.toUnsignedString(identity.inode()))
                .getBytes(StandardCharsets.UTF_8));
    }

    private static String digestBytes(byte[] value) {
        return "sha256:" + HexFormat.of().formatHex(messageDigest().digest(value));
    }

    private static MessageDigest messageDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static ShipFilesystemException unsupported(String message) {
        return new ShipFilesystemException(
                ShipFilesystemException.SECURE_FILESYSTEM_UNSUPPORTED, message);
    }

    private static ShipFilesystemException unsafe(String message) {
        return new ShipFilesystemException(ShipFilesystemException.UNSAFE_ENTRY, message);
    }

    private static ShipFilesystemException unsafe(String message, Throwable cause) {
        return new ShipFilesystemException(ShipFilesystemException.UNSAFE_ENTRY, message, cause);
    }

    private static ShipFilesystemException concurrentMutation(String message) {
        return new ShipFilesystemException(ShipFilesystemException.CONCURRENT_MUTATION, message);
    }

    private record UnixIdentity(
            Object fileKey,
            long device,
            long inode,
            long linkCount,
            long size,
            long modifiedNanos,
            long changedNanos,
            int mode,
            long userId,
            long groupId) {

    }

    /** Held descriptor for one stable root. All untrusted path traversal stays relative to this handle. */
    static final class SecureRoot implements AutoCloseable {

        private static final Set<OpenOption> READ_OPTIONS = Set.of(
                StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);

        private final Path path;
        private final String label;
        private final ShipTreePolicy policy;
        private final SecureDirectoryStream<Path> root;
        private final UnixIdentity rootMetadata;
        private final String identity;

        private SecureRoot(
                           Path path,
                           String label,
                           ShipTreePolicy policy,
                           SecureDirectoryStream<Path> root,
                           UnixIdentity rootMetadata,
                           String identity) {
            this.path = path;
            this.label = label;
            this.policy = policy;
            this.root = root;
            this.rootMetadata = rootMetadata;
            this.identity = identity;
        }

        void validateBinding() throws IOException {
            assertRootBinding();
        }

        ProjectSnapshot snapshot() throws IOException {
            return snapshot(false);
        }

        /** Captures a sealed tree containing material entries only. */
        ProjectSnapshot snapshotMaterialOnly() throws IOException {
            return snapshot(true);
        }

        private ProjectSnapshot snapshot(boolean materialOnly) throws IOException {
            assertRootBinding();
            ScanState state = new ScanState(policy, rootMetadata.fileKey());
            try (BoundDirectory scanRoot = openBoundDirectory(
                    root, Path.of("."), "", rootMetadata)) {
                scan(scanRoot.stream(), "", rootMetadata, 0, state, materialOnly);
            }
            assertRootBinding();
            return ProjectSnapshot.create(
                    path.toString(), identity, policy.digest(), state.directories, state.files);
        }

        /** Reads one bounded material file without following links or exposing protected project metadata. */
        byte[] readMaterialBytes(String relativePath, int maximumBytes) throws IOException {
            if (maximumBytes < 1) {
                throw new IllegalArgumentException("Maximum Ship read size must be positive");
            }
            String relative = canonicalPath(relativePath);
            if (classify(relative) != Classification.MATERIAL) {
                throw unsafe("Only material project files may be read as Ship context: " + relative);
            }
            assertRootBinding();
            byte[] result = withParent(relative, (parent, name) -> {
                BasicFileAttributes basicBefore = attributes(parent, name);
                UnixIdentity before = relativeIdentity(relative, basicBefore.fileKey());
                requireRegularFile(basicBefore, before, rootMetadata.device(), relative);
                long limit = Math.min((long) maximumBytes, policy.maxFileBytes());
                if (before.size() > limit) {
                    throw quota("Ship context file exceeds its read limit: " + relative);
                }
                ByteArrayOutputStream output = new ByteArrayOutputStream(Math.toIntExact(before.size()));
                // Java 17 exposes no fstat-style identity from SeekableByteChannel. These
                // samples bracket the read but cannot certify the opened final descriptor;
                // the later OS helper must bind and verify that descriptor directly.
                try (SeekableByteChannel channel = parent.newByteChannel(name, READ_OPTIONS)) {
                    ByteBuffer buffer = ByteBuffer.allocate(8192);
                    long total = 0;
                    int read;
                    while ((read = channel.read(buffer)) != -1) {
                        if (read == 0) {
                            continue;
                        }
                        total = Math.addExact(total, read);
                        if (total > before.size()) {
                            throw concurrentMutation(
                                    "Ship context file grew while it was read: " + relative);
                        }
                        if (total > limit) {
                            throw quota("Ship context file exceeds its read limit: " + relative);
                        }
                        output.write(buffer.array(), 0, read);
                        buffer.clear();
                    }
                }
                BasicFileAttributes basicAfter = attributes(parent, name);
                UnixIdentity after = relativeIdentity(relative, before.fileKey());
                requireRegularFile(basicAfter, after, rootMetadata.device(), relative);
                if (!before.equals(after) || output.size() != before.size()) {
                    throw concurrentMutation("Ship context file changed while it was read: " + relative);
                }
                return output.toByteArray();
            });
            assertRootBinding();
            return result;
        }

        private void scan(
                SecureDirectoryStream<Path> directory,
                String prefix,
                UnixIdentity expectedDirectory,
                int depth,
                ScanState state,
                boolean materialOnly)
                throws IOException {
            validateOpenedDirectory(directory, expectedDirectory, display(prefix));
            UnixIdentity directoryBefore = relativeIdentity(prefix, expectedDirectory.fileKey());
            if (!expectedDirectory.equals(directoryBefore)) {
                throw concurrentMutation(
                        "Ship directory changed before it was scanned: " + display(prefix));
            }
            for (Path entry : directory) {
                Path name = Path.of(singleName(entry.getFileName().toString()));
                String relative = prefix.isEmpty() ? name.toString() : prefix + "/" + name;
                relative = relative.replace('\\', '/');
                Classification classification = classify(relative);
                BasicFileAttributes basic = attributes(directory, name);
                if (basic.isSymbolicLink()) {
                    throw unsafe("Ship tree contains a symbolic link: " + relative);
                }
                if (basic.isDirectory()) {
                    UnixIdentity entryIdentity = relativeIdentity(relative, basic.fileKey());
                    requireDirectory(basic, entryIdentity, rootMetadata.device(), relative);
                    state.visitDirectory(relative, entryIdentity);
                    if (materialOnly && classification != Classification.MATERIAL) {
                        throw unsafe("Sealed Ship tree contains a non-material entry: " + relative);
                    }
                    if (classification == Classification.DENIED
                            || classification == Classification.VOLATILE) {
                        continue;
                    }
                    int childDepth = Math.addExact(depth, 1);
                    if (childDepth > policy.maxDepth()) {
                        throw quota("Ship tree exceeds the depth quota at " + relative);
                    }
                    state.addDirectory(relative, entryIdentity, new DirectoryEntry(
                            classification,
                            entryIdentity.mode(),
                            entryIdentity.userId(),
                            entryIdentity.groupId()));
                    try (BoundDirectory child = openBoundDirectory(
                            directory, name, relative, entryIdentity)) {
                        scan(child.stream(), relative, entryIdentity, childDepth, state, materialOnly);
                    }
                    continue;
                }
                if (!basic.isRegularFile()) {
                    throw unsafe("Ship tree contains a special filesystem entry: " + relative);
                }
                UnixIdentity entryIdentity = relativeIdentity(relative, basic.fileKey());
                requireRegularFile(basic, entryIdentity, rootMetadata.device(), relative);
                state.visitFile(relative, entryIdentity);
                if (materialOnly && classification != Classification.MATERIAL) {
                    throw unsafe("Sealed Ship tree contains a non-material entry: " + relative);
                }
                if (classification == Classification.DENIED
                        || classification == Classification.VOLATILE) {
                    continue;
                }
                state.add(relative, readRegular(
                        directory, name, relative, classification, entryIdentity, state));
            }
            validateOpenedDirectory(directory, expectedDirectory, display(prefix));
            UnixIdentity directoryAfter = relativeIdentity(prefix, expectedDirectory.fileKey());
            if (!directoryBefore.equals(directoryAfter)) {
                throw concurrentMutation(
                        "Ship directory changed while it was scanned: " + display(prefix));
            }
        }

        private FileEntry readRegular(
                SecureDirectoryStream<Path> directory,
                Path name,
                String relative,
                Classification classification,
                UnixIdentity expected,
                ScanState state)
                throws IOException {
            BasicFileAttributes basicBefore = attributes(directory, name);
            UnixIdentity before = relativeIdentity(relative, basicBefore.fileKey());
            requireRegularFile(basicBefore, before, rootMetadata.device(), relative);
            if (!expected.equals(before)) {
                throw concurrentMutation("Ship file changed before it was read: " + relative);
            }
            state.reserve(relative, before);
            MessageDigest digest = messageDigest();
            long count = 0;
            // Java 17 exposes no fstat-style identity from SeekableByteChannel. These
            // samples bracket the read but cannot certify the opened final descriptor;
            // the later OS helper must bind and verify that descriptor directly.
            try (SeekableByteChannel input = directory.newByteChannel(name, READ_OPTIONS)) {
                ByteBuffer buffer = ByteBuffer.allocate(8192);
                int read;
                while ((read = input.read(buffer)) != -1) {
                    if (read == 0) {
                        continue;
                    }
                    count = Math.addExact(count, read);
                    if (count > before.size()) {
                        throw concurrentMutation("Ship file grew while it was read: " + relative);
                    }
                    if (count > policy.maxFileBytes()) {
                        throw quota("Ship file exceeds the per-file quota: " + relative);
                    }
                    buffer.flip();
                    digest.update(buffer);
                    buffer.clear();
                }
            }
            BasicFileAttributes basicAfter = attributes(directory, name);
            UnixIdentity after = relativeIdentity(relative, before.fileKey());
            requireRegularFile(basicAfter, after, rootMetadata.device(), relative);
            if (count != before.size() || !before.equals(after)) {
                throw concurrentMutation("Ship file changed while it was read: " + relative);
            }
            return new FileEntry(
                    classification,
                    count,
                    "sha256:" + HexFormat.of().formatHex(digest.digest()),
                    after.mode(),
                    after.userId(),
                    after.groupId());
        }

        private UnixIdentity relativeIdentity(String relative, Object expectedKey) throws IOException {
            if (relative == null || relative.isEmpty()) {
                return sampleUnixIdentity(path, expectedKey, label + " root");
            }
            assertLexicalRootStillNamesHandle();
            UnixIdentity result = sampleUnixIdentity(path.resolve(relative), expectedKey, relative);
            assertLexicalRootStillNamesHandle();
            return result;
        }

        private void assertRootBinding() throws IOException {
            BasicFileAttributes held = attributes(root, null);
            requireDirectory(held, rootMetadata, rootMetadata.device(), label + " held root");
            assertLexicalRootStillNamesHandle();
        }

        private void assertLexicalRootStillNamesHandle() throws IOException {
            UnixIdentity named = sampleUnixIdentity(path, rootMetadata.fileKey(), label + " root");
            if (!rootMetadata.equals(named)
                    || !identity.equals(rootIdentity(path, named))) {
                throw concurrentMutation(
                        label + " root path no longer names its original directory: " + path);
            }
        }

        private <T> T withParent(String relative, ParentOperation<T> operation) throws IOException {
            int slash = relative.lastIndexOf('/');
            String directory = slash < 0 ? "" : relative.substring(0, slash);
            Path name = Path.of(slash < 0 ? relative : relative.substring(slash + 1));
            return withDirectory(directory, parent -> operation.apply(parent, name));
        }

        private <T> T withDirectory(String relative, DirectoryOperation<T> operation)
                throws IOException {
            assertRootBinding();
            if (relative == null || relative.isBlank()) {
                return operation.apply(root);
            }
            String canonical = canonicalPath(relative);
            String[] components = canonical.split("/");
            if (components.length > policy.maxDepth()) {
                throw quota("Ship path exceeds the depth quota: " + canonical);
            }
            return withDirectory(root, components, 0, "", operation);
        }

        private <T> T withDirectory(
                SecureDirectoryStream<Path> current,
                String[] components,
                int index,
                String prefix,
                DirectoryOperation<T> operation)
                throws IOException {
            if (index == components.length) {
                return operation.apply(current);
            }
            Path name = Path.of(singleName(components[index]));
            String relative = prefix.isEmpty() ? components[index] : prefix + "/" + components[index];
            BasicFileAttributes basic = attributes(current, name);
            UnixIdentity expected = relativeIdentity(relative, basic.fileKey());
            requireDirectory(basic, expected, rootMetadata.device(), relative);
            try (BoundDirectory child = openBoundDirectory(current, name, relative, expected)) {
                return withDirectory(
                        child.stream(), components, index + 1, relative, operation);
            }
        }

        private BoundDirectory openBoundDirectory(
                SecureDirectoryStream<Path> parent,
                Path name,
                String relative,
                UnixIdentity expected)
                throws IOException {
            SecureDirectoryStream<Path> opened = null;
            try {
                opened = parent.newDirectoryStream(name, LinkOption.NOFOLLOW_LINKS);
                validateOpenedDirectory(opened, expected, display(relative));
                UnixIdentity rebound = relativeIdentity(relative, expected.fileKey());
                if (!expected.equals(rebound)) {
                    throw concurrentMutation(
                            "Ship directory changed while its secure handle was opened: "
                                             + display(relative));
                }
                return new BoundDirectory(parent, name, relative, expected, opened);
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

        private void validateOpenedDirectory(
                SecureDirectoryStream<Path> directory,
                UnixIdentity expected,
                String description)
                throws IOException {
            BasicFileAttributes held = attributes(directory, null);
            requireDirectory(held, expected, rootMetadata.device(), description);
        }

        private void validateParentBinding(
                SecureDirectoryStream<Path> parent,
                Path name,
                String relative,
                UnixIdentity expected)
                throws IOException {
            BasicFileAttributes named = attributes(parent, name);
            UnixIdentity rebound = relativeIdentity(relative, expected.fileKey());
            requireDirectory(named, rebound, rootMetadata.device(), display(relative));
            if (!expected.equals(rebound)) {
                throw concurrentMutation(
                        "Ship directory changed while its secure handle was in use: "
                                         + display(relative));
            }
        }

        @Override
        public void close() throws IOException {
            root.close();
        }

        private String canonicalPath(String value) throws ShipFilesystemException {
            try {
                return ShipTreePolicy.requireCanonicalRelativePath(value);
            } catch (IllegalArgumentException e) {
                throw unsafe("Ship tree path is unsafe", e);
            }
        }

        private Classification classify(String relative) throws ShipFilesystemException {
            try {
                return policy.classify(relative);
            } catch (IllegalArgumentException e) {
                throw unsafe("Ship tree path is unsafe", e);
            }
        }

        private static String singleName(String value) throws ShipFilesystemException {
            try {
                if (value == null || value.indexOf('/') >= 0) {
                    throw new IllegalArgumentException("Invalid Ship tree entry name");
                }
                return ShipTreePolicy.requireCanonicalRelativePath(value);
            } catch (IllegalArgumentException e) {
                throw unsafe("Ship tree contains an unsafe directory entry name", e);
            }
        }

        private static String display(String relative) {
            return relative == null || relative.isBlank() ? "." : relative;
        }

        private static ShipFilesystemException quota(String message) {
            return new ShipFilesystemException(
                    ShipFilesystemException.TREE_QUOTA_EXCEEDED, message);
        }

        @FunctionalInterface
        private interface DirectoryOperation<T> {
            T apply(SecureDirectoryStream<Path> directory) throws IOException;
        }

        @FunctionalInterface
        private interface ParentOperation<T> {
            T apply(SecureDirectoryStream<Path> directory, Path name) throws IOException;
        }

        private final class BoundDirectory implements AutoCloseable {

            private final SecureDirectoryStream<Path> parent;
            private final Path name;
            private final String relative;
            private final UnixIdentity expected;
            private final SecureDirectoryStream<Path> stream;

            private BoundDirectory(
                                   SecureDirectoryStream<Path> parent,
                                   Path name,
                                   String relative,
                                   UnixIdentity expected,
                                   SecureDirectoryStream<Path> stream) {
                this.parent = parent;
                this.name = name;
                this.relative = relative;
                this.expected = expected;
                this.stream = stream;
            }

            private SecureDirectoryStream<Path> stream() {
                return stream;
            }

            @Override
            public void close() throws IOException {
                IOException failure = null;
                try {
                    validateOpenedDirectory(stream, expected, display(relative));
                    validateParentBinding(parent, name, relative, expected);
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

        private static final class ScanState {

            private final ShipTreePolicy policy;
            private final Map<String, DirectoryEntry> directories = new TreeMap<>();
            private final Map<String, FileEntry> files = new TreeMap<>();
            private final Map<Object, String> directoryKeys = new HashMap<>();
            private final Map<Object, String> fileKeys = new HashMap<>();
            private int count;
            private long aggregate;

            private ScanState(ShipTreePolicy policy, Object rootFileKey) {
                this.policy = policy;
                directoryKeys.put(rootFileKey, ".");
            }

            private void visitDirectory(String relative, UnixIdentity identity) throws IOException {
                reserveEntry(relative);
                String first = directoryKeys.putIfAbsent(identity.fileKey(), relative);
                if (first != null) {
                    throw unsafe(
                            "Ship tree contains duplicate directory identities: "
                                 + first + " and " + relative);
                }
            }

            private void visitFile(String relative, UnixIdentity identity) throws IOException {
                reserveEntry(relative);
                String first = fileKeys.putIfAbsent(identity.fileKey(), relative);
                if (first != null) {
                    throw unsafe(
                            "Ship tree contains duplicate file identities: "
                                 + first + " and " + relative);
                }
            }

            private void reserve(String relative, UnixIdentity identity) throws IOException {
                aggregate = Math.addExact(aggregate, identity.size());
                if (identity.size() > policy.maxFileBytes()) {
                    throw quota("Ship file exceeds the per-file quota: " + relative);
                }
                if (aggregate > policy.maxAggregateBytes()) {
                    throw quota("Ship tree exceeds the aggregate-byte quota at " + relative);
                }
            }

            private void add(String relative, FileEntry entry) throws IOException {
                if (directories.containsKey(relative) || files.putIfAbsent(relative, entry) != null) {
                    throw unsafe("Ship tree contains duplicate canonical entry names: " + relative);
                }
            }

            private void addDirectory(
                    String relative, UnixIdentity identity, DirectoryEntry entry)
                    throws IOException {
                if (!directoryKeys.containsKey(identity.fileKey())) {
                    throw concurrentMutation(
                            "Ship directory was not identity-validated before capture: " + relative);
                }
                if (files.containsKey(relative) || directories.putIfAbsent(relative, entry) != null) {
                    throw unsafe("Ship tree contains duplicate canonical entry names: " + relative);
                }
            }

            private void reserveEntry(String relative) throws IOException {
                count = Math.addExact(count, 1);
                if (count > policy.maxFileCount()) {
                    throw quota("Ship tree exceeds the entry-count quota at " + relative);
                }
            }
        }
    }
}
