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
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import io.github.luigidemasi.camelkit.ship.security.ProjectSnapshot.DirectoryEntry;
import io.github.luigidemasi.camelkit.ship.security.ProjectSnapshot.FileEntry;
import io.github.luigidemasi.camelkit.ship.security.ShipTreePolicy.Classification;

/** Internal bounded read policy for Ship trees. */
final class ShipSecureFilesystem {

    private static final String UNIX_ATTRIBUTES = "unix:mode,nlink";
    private static final int FILE_TYPE_MASK = 0170000;
    private static final int DIRECTORY_TYPE = 0040000;
    private static final int REGULAR_FILE_TYPE = 0100000;

    private ShipSecureFilesystem() {
    }

    static SecureRoot open(Path requestedRoot, String label, ShipTreePolicy policy) throws IOException {
        Path root = requireRootPath(requestedRoot, label, policy);
        BasicFileAttributes attributes = attributes(root);
        UnixMetadata metadata = requireDirectory(root, attributes, label + " root");
        if (attributes.fileKey() == null) {
            throw unsupported(label + " root lacks a stable file key: " + root);
        }
        return new SecureRoot(
                root,
                label,
                policy,
                attributes.fileKey(),
                rootIdentity(root, attributes.fileKey()),
                metadata.mode());
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
        if (Files.isSymbolicLink(absolute)
                || !Files.isDirectory(absolute, LinkOption.NOFOLLOW_LINKS)) {
            throw unsafe(label + " must be a real directory: " + absolute);
        }
        Path root = absolute.toRealPath();
        if (!policy.isAllowedAbsolutePath(root)) {
            throw unsafe(label + " is outside the allowed project-root lineage");
        }
        return root;
    }

    private static BasicFileAttributes attributes(Path path) throws IOException {
        return Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    }

    private static UnixMetadata unixMetadata(Path path, String description) throws IOException {
        try {
            Map<String, Object> values = Files.readAttributes(
                    path, UNIX_ATTRIBUTES, LinkOption.NOFOLLOW_LINKS);
            int mode = requiredInt(values, "mode", description);
            long linkCount = requiredLong(values, "nlink", description);
            if (linkCount < 1) {
                throw unsupported(description + " returned an invalid Unix link count");
            }
            return new UnixMetadata(mode, linkCount);
        } catch (UnsupportedOperationException | IllegalArgumentException e) {
            throw new ShipFilesystemException(
                    ShipFilesystemException.SECURE_FILESYSTEM_UNSUPPORTED,
                    description + " filesystem cannot provide Unix mode and link metadata",
                    e);
        }
    }

    private static long requiredLong(Map<String, Object> values, String name, String description)
            throws IOException {
        Object value = values.get(name);
        if (!(value instanceof Number number)) {
            throw unsupported(description + " lacks Unix attribute " + name);
        }
        return number.longValue();
    }

    private static int requiredInt(Map<String, Object> values, String name, String description)
            throws IOException {
        long value = requiredLong(values, name, description);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw unsupported(description + " has an invalid Unix attribute " + name);
        }
        return (int) value;
    }

    private static UnixMetadata requireDirectory(
            Path path, BasicFileAttributes basic, String description)
            throws IOException {
        UnixMetadata unix = unixMetadata(path, description);
        if (!basic.isDirectory() || basic.isSymbolicLink()
                || (unix.mode() & FILE_TYPE_MASK) != DIRECTORY_TYPE) {
            throw unsafe(description + " is not a real directory");
        }
        return unix;
    }

    private static UnixMetadata requireRegularFile(
            Path path, BasicFileAttributes basic, String description)
            throws IOException {
        UnixMetadata unix = unixMetadata(path, description);
        if (!basic.isRegularFile() || basic.isSymbolicLink()
                || (unix.mode() & FILE_TYPE_MASK) != REGULAR_FILE_TYPE) {
            throw unsafe(description + " is not a regular file");
        }
        if (unix.linkCount() != 1) {
            throw unsafe("Ship tree contains a hard-linked file: " + description);
        }
        return unix;
    }

    private static String rootIdentity(Path root, Object fileKey) {
        return digestBytes(("camel-kit.ship.root.v5\0" + root + "\0" + fileKey)
                .getBytes(StandardCharsets.UTF_8));
    }

    private static String projectIdentity(Object fileKey) {
        return digestBytes(("camel-kit.ship.project.v2\0" + fileKey)
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

    private record UnixMetadata(int mode, long linkCount) {
    }

    static final class SecureRoot implements AutoCloseable {

        private static final Set<OpenOption> READ_OPTIONS = Set.of(
                StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);

        private final Path path;
        private final String label;
        private final ShipTreePolicy policy;
        private final Object rootFileKey;
        private final String identity;
        private final int rootMode;

        private SecureRoot(
                           Path path,
                           String label,
                           ShipTreePolicy policy,
                           Object rootFileKey,
                           String identity,
                           int rootMode) {
            this.path = path;
            this.label = label;
            this.policy = policy;
            this.rootFileKey = rootFileKey;
            this.identity = identity;
            this.rootMode = rootMode;
        }

        void validateBinding() throws IOException {
            BasicFileAttributes current = attributes(path);
            UnixMetadata metadata = requireDirectory(path, current, label + " root");
            if (!rootFileKey.equals(current.fileKey()) || rootMode != metadata.mode()) {
                throw concurrentMutation(
                        label + " root path no longer names its original directory: " + path);
            }
        }

        String rootIdentity() throws IOException {
            validateBinding();
            return identity;
        }

        String projectIdentity() throws IOException {
            validateBinding();
            return ShipSecureFilesystem.projectIdentity(rootFileKey);
        }

        ProjectSnapshot snapshot() throws IOException {
            return snapshot(false, false);
        }

        /** Captures a sealed tree containing material entries only. */
        ProjectSnapshot snapshotMaterialOnly() throws IOException {
            return snapshot(true, false);
        }

        /** Captures material staging content while allowing volatile build output. */
        ProjectSnapshot snapshotStaged() throws IOException {
            return snapshot(false, true);
        }

        private ProjectSnapshot snapshot(boolean materialOnly, boolean staged) throws IOException {
            validateBinding();
            ScanState state = new ScanState(policy);
            try {
                scan(path, "", 0, state, materialOnly, staged);
            } catch (IllegalArgumentException ignored) {
                throw unsafe("Ship tree contains an unsafe entry");
            }
            return ProjectSnapshot.create(
                    path.toString(), identity, policy.digest(), state.directories, state.files);
        }

        /** Reads one bounded material file without following links or exposing protected project metadata. */
        byte[] readMaterialBytes(String relativePath, int maximumBytes) throws IOException {
            return readBytes(relativePath, maximumBytes, Classification.MATERIAL);
        }

        /** Reads one bounded volatile file without following links or exposing other project content. */
        byte[] readVolatileBytes(String relativePath, int maximumBytes) throws IOException {
            return readBytes(relativePath, maximumBytes, Classification.VOLATILE);
        }

        private byte[] readBytes(
                String relativePath, int maximumBytes, Classification requiredClassification)
                throws IOException {
            if (maximumBytes < 1) {
                throw new IllegalArgumentException("Maximum Ship read size must be positive");
            }
            String relative = canonicalPath(relativePath);
            if (classify(relative) != requiredClassification) {
                throw unsafe("Ship file does not have the required classification: " + relative);
            }
            validateBinding();
            Path file = resolve(relative);
            BasicFileAttributes basic = attributes(file);
            requireRegularFile(file, basic, relative);
            long limit = Math.min((long) maximumBytes, policy.maxFileBytes());
            if (basic.size() > limit) {
                throw quota("Ship file exceeds its read limit: " + relative);
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream(Math.toIntExact(basic.size()));
            try (SeekableByteChannel channel = Files.newByteChannel(file, READ_OPTIONS)) {
                ByteBuffer buffer = ByteBuffer.allocate(8192);
                long total = 0;
                int read;
                while ((read = channel.read(buffer)) != -1) {
                    if (read == 0) {
                        continue;
                    }
                    total = Math.addExact(total, read);
                    if (total > limit) {
                        throw quota("Ship file exceeds its read limit: " + relative);
                    }
                    output.write(buffer.array(), 0, read);
                    buffer.clear();
                }
            }
            if (output.size() != basic.size()) {
                throw concurrentMutation("Ship file changed while it was read: " + relative);
            }
            return output.toByteArray();
        }

        private void scan(
                Path directory,
                String prefix,
                int depth,
                ScanState state,
                boolean materialOnly,
                boolean staged)
                throws IOException {
            try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory)) {
                for (Path entry : entries) {
                    String name = singleName(entry.getFileName().toString());
                    String relative = prefix.isEmpty() ? name : prefix + "/" + name;
                    Classification classification = classify(relative);
                    BasicFileAttributes basic = attributes(entry);
                    if (basic.isSymbolicLink()) {
                        throw unsafe("Ship tree contains a symbolic link: " + relative);
                    }
                    if (basic.isDirectory()) {
                        UnixMetadata metadata = requireDirectory(entry, basic, relative);
                        state.visitEntry(relative);
                        if (staged && (classification == Classification.DENIED
                                || classification == Classification.PROTECTED)) {
                            throw unsafe("Staged Ship tree contains a non-publishable entry: " + relative);
                        }
                        if (materialOnly && classification != Classification.MATERIAL) {
                            throw unsafe("Sealed Ship tree contains a non-material entry: " + relative);
                        }
                        if (classification == Classification.DENIED
                                || (classification == Classification.VOLATILE && !staged)) {
                            continue;
                        }
                        int childDepth = Math.addExact(depth, 1);
                        if (childDepth > policy.maxDepth()) {
                            throw quota("Ship tree exceeds the depth quota at " + relative);
                        }
                        if (classification != Classification.VOLATILE) {
                            state.addDirectory(
                                    relative, new DirectoryEntry(classification, metadata.mode()));
                        }
                        scan(entry, relative, childDepth, state, materialOnly, staged);
                        continue;
                    }
                    if (!basic.isRegularFile()) {
                        throw unsafe("Ship tree contains a special filesystem entry: " + relative);
                    }
                    UnixMetadata metadata = requireRegularFile(entry, basic, relative);
                    state.visitEntry(relative);
                    if (staged && (classification == Classification.DENIED
                            || classification == Classification.PROTECTED)) {
                        throw unsafe("Staged Ship tree contains a non-publishable entry: " + relative);
                    }
                    if (materialOnly && classification != Classification.MATERIAL) {
                        throw unsafe("Sealed Ship tree contains a non-material entry: " + relative);
                    }
                    if (classification == Classification.DENIED) {
                        continue;
                    }
                    if (classification == Classification.VOLATILE) {
                        if (staged) {
                            state.reserve(relative, basic.size());
                        }
                        continue;
                    }
                    state.add(relative, readRegular(
                            entry, relative, classification, basic, metadata, state));
                }
            }
        }

        private FileEntry readRegular(
                Path file,
                String relative,
                Classification classification,
                BasicFileAttributes basic,
                UnixMetadata metadata,
                ScanState state)
                throws IOException {
            state.reserve(relative, basic.size());
            MessageDigest digest = messageDigest();
            long count = 0;
            try (SeekableByteChannel input = Files.newByteChannel(file, READ_OPTIONS)) {
                ByteBuffer buffer = ByteBuffer.allocate(8192);
                int read;
                while ((read = input.read(buffer)) != -1) {
                    if (read == 0) {
                        continue;
                    }
                    count = Math.addExact(count, read);
                    if (count > policy.maxFileBytes()) {
                        throw quota("Ship file exceeds the per-file quota: " + relative);
                    }
                    buffer.flip();
                    digest.update(buffer);
                    buffer.clear();
                }
            }
            if (count != basic.size()) {
                throw concurrentMutation("Ship file changed while it was read: " + relative);
            }
            return new FileEntry(
                    classification,
                    count,
                    "sha256:" + HexFormat.of().formatHex(digest.digest()),
                    metadata.mode());
        }

        private Path resolve(String relative) throws IOException {
            Path current = path;
            String[] components = relative.split("/");
            if (components.length - 1 > policy.maxDepth()) {
                throw quota("Ship path exceeds the depth quota: " + relative);
            }
            for (int index = 0; index < components.length - 1; index++) {
                current = current.resolve(singleName(components[index]));
                requireDirectory(current, attributes(current), relative);
            }
            return current.resolve(singleName(components[components.length - 1]));
        }

        @Override
        public void close() {
            // No persistent handle is required by the bounded read policy.
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

        private static ShipFilesystemException quota(String message) {
            return new ShipFilesystemException(
                    ShipFilesystemException.TREE_QUOTA_EXCEEDED, message);
        }

        private static final class ScanState {

            private final ShipTreePolicy policy;
            private final Map<String, DirectoryEntry> directories = new TreeMap<>();
            private final Map<String, FileEntry> files = new TreeMap<>();
            private int count;
            private long aggregate;

            private ScanState(ShipTreePolicy policy) {
                this.policy = policy;
            }

            private void visitEntry(String relative) throws IOException {
                count = Math.addExact(count, 1);
                if (count > policy.maxFileCount()) {
                    throw quota("Ship tree exceeds the entry-count quota at " + relative);
                }
            }

            private void reserve(String relative, long size) throws IOException {
                aggregate = Math.addExact(aggregate, size);
                if (size > policy.maxFileBytes()) {
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

            private void addDirectory(String relative, DirectoryEntry entry) throws IOException {
                if (files.containsKey(relative) || directories.putIfAbsent(relative, entry) != null) {
                    throw unsafe("Ship tree contains duplicate canonical entry names: " + relative);
                }
            }
        }
    }
}
