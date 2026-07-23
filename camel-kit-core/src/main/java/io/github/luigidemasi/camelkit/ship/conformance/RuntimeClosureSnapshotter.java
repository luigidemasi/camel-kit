package io.github.luigidemasi.camelkit.ship.conformance;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import io.github.luigidemasi.camelkit.ship.ShipDigest;
import io.github.luigidemasi.camelkit.ship.conformance.RuntimeClosureManifest.EntryPoint;
import io.github.luigidemasi.camelkit.ship.conformance.RuntimeClosureManifest.RuntimeFile;
import io.github.luigidemasi.camelkit.ship.controller.ShipJson;
import io.github.luigidemasi.camelkit.ship.security.ShipTreePolicy;

/**
 * Copies a probed runtime tree into a caller-owned content-addressed sink and emits no host installation paths.
 *
 * <p>
 * Launchers must use the copied blobs described by the returned manifest. They must never re-resolve the supplied
 * mutable paths.
 */
public final class RuntimeClosureSnapshotter {

    private static final int MAX_ROOTS = 16;
    private static final int MAX_SYMLINKS = 32;
    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9-]{0,63}");
    private static final Set<OpenOption> READ_OPTIONS = Set.of(
            StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
    private static final String UNIX_ATTRIBUTES
            = "unix:dev,ino,nlink,size,lastModifiedTime,ctime,mode";

    private RuntimeClosureSnapshotter() {
    }

    public static Snapshot capture(
            Path entryPoint,
            Map<String, Path> roots,
            BlobSink sink)
            throws IOException {
        Objects.requireNonNull(sink, "runtime closure blob sink");
        Map<String, Path> canonicalRoots = canonicalRoots(roots);
        Path resolvedEntryPoint = resolveEntryPoint(entryPoint, canonicalRoots.values());

        List<RuntimeFile> files = new ArrayList<>();
        Map<String, CapturedSource> captured = new LinkedHashMap<>();
        String entryRoot = null;
        String entryRelative = null;
        for (Map.Entry<String, Path> root : canonicalRoots.entrySet()) {
            if (resolvedEntryPoint.startsWith(root.getValue())) {
                entryRoot = root.getKey();
                entryRelative = root.getValue().relativize(resolvedEntryPoint)
                        .toString()
                        .replace('\\', '/');
                break;
            }
        }
        String entryDigest = null;
        for (Map.Entry<String, Path> root : canonicalRoots.entrySet()) {
            FileIdentity rootBefore = identity(root.getValue(), true);
            List<String> beforePaths = listPaths(root.getValue());
            for (String relative : beforePaths) {
                Path lexical = root.getValue().resolve(relative);
                BasicFileAttributes lexicalAttributes = Files.readAttributes(
                        lexical, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (lexicalAttributes.isDirectory()) {
                    continue;
                }
                Path resolved = resolveWithin(root.getValue(), lexical);
                StableBytes stable = readStable(resolved);
                sink.store(stable.digest(), stable.content().clone());
                boolean executable = (stable.identity().mode() & 0111) != 0;
                files.add(new RuntimeFile(
                        root.getKey(), relative, stable.digest(), stable.content().length, executable));
                captured.put(root.getKey() + "\0" + relative,
                        new CapturedSource(root.getValue(), lexical, resolved, stable.identity()));
                if (root.getKey().equals(entryRoot) && relative.equals(entryRelative)) {
                    entryDigest = stable.digest();
                }
            }
            verifyUnchanged(root.getValue(), rootBefore, beforePaths, captured);
        }
        if (entryRoot == null || entryDigest == null) {
            throw new IOException("Runtime entry point is absent from the captured closure");
        }
        RuntimeClosureManifest manifest = new RuntimeClosureManifest(
                RuntimeClosureManifest.SCHEMA_VERSION,
                new EntryPoint(entryRoot, entryRelative, entryDigest),
                files);
        byte[] encoded = ShipJson.mapper().writeValueAsBytes(manifest);
        String digest = ShipDigest.sha256(encoded);
        sink.store(digest, encoded.clone());
        return new Snapshot(encoded, digest);
    }

    private static Map<String, Path> canonicalRoots(Map<String, Path> supplied) throws IOException {
        Objects.requireNonNull(supplied, "runtime closure roots");
        if (supplied.isEmpty() || supplied.size() > MAX_ROOTS) {
            throw new IllegalArgumentException("Runtime closure must have 1.." + MAX_ROOTS + " roots");
        }
        TreeMap<String, Path> roots = new TreeMap<>();
        for (Map.Entry<String, Path> entry : supplied.entrySet()) {
            if (entry.getKey() == null || !ID.matcher(entry.getKey()).matches()) {
                throw new IllegalArgumentException("Runtime closure root ID is not canonical");
            }
            Path root = requireRealDirectory(entry.getValue());
            if (roots.putIfAbsent(entry.getKey(), root) != null) {
                throw new IllegalArgumentException("Runtime closure root IDs must be unique");
            }
        }
        List<Path> paths = List.copyOf(roots.values());
        for (int left = 0; left < paths.size(); left++) {
            for (int right = left + 1; right < paths.size(); right++) {
                if (paths.get(left).startsWith(paths.get(right))
                        || paths.get(right).startsWith(paths.get(left))) {
                    throw new IOException("Runtime closure roots must not overlap");
                }
            }
        }
        return Collections.unmodifiableMap(roots);
    }

    private static Path requireRealDirectory(Path supplied) throws IOException {
        if (supplied == null) {
            throw new IllegalArgumentException("Runtime closure root is required");
        }
        Path root = supplied.toAbsolutePath().normalize();
        rejectSymbolicParents(root);
        if (Files.isSymbolicLink(root)
                || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)
                || !root.toRealPath(LinkOption.NOFOLLOW_LINKS).equals(root)) {
            throw new IOException("Runtime closure root must be a link-free real directory");
        }
        identity(root, true);
        return root;
    }

    private static void rejectSymbolicParents(Path path) throws IOException {
        Path current = path.getRoot();
        for (Path component : path) {
            current = current == null ? component : current.resolve(component);
            if (Files.isSymbolicLink(current)) {
                throw new IOException("Runtime closure path contains a symbolic directory");
            }
        }
    }

    private static Path resolveEntryPoint(Path supplied, Iterable<Path> roots) throws IOException {
        if (supplied == null) {
            throw new IllegalArgumentException("Runtime entry point is required");
        }
        Path resolved = resolveSymlinks(supplied.toAbsolutePath().normalize(), null);
        for (Path root : roots) {
            if (resolved.startsWith(root)) {
                return resolved;
            }
        }
        throw new IOException("Runtime entry point resolves outside the declared closure roots");
    }

    private static Path resolveWithin(Path root, Path lexical) throws IOException {
        Path resolved = resolveSymlinks(lexical, root);
        if (!resolved.startsWith(root)) {
            throw new IOException("Runtime closure symlink escapes its declared root");
        }
        return resolved;
    }

    private static Path resolveSymlinks(Path supplied, Path requiredRoot) throws IOException {
        Path current = supplied;
        Set<Path> seen = new HashSet<>();
        for (int links = 0; links <= MAX_SYMLINKS; links++) {
            if (!seen.add(current)) {
                throw new IOException("Runtime closure contains a symbolic-link cycle");
            }
            BasicFileAttributes attributes = Files.readAttributes(
                    current, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isSymbolicLink()) {
                if (!attributes.isRegularFile()) {
                    throw new IOException("Runtime closure entry is not a regular file");
                }
                Path real = current.toRealPath(LinkOption.NOFOLLOW_LINKS);
                if (!real.equals(current)
                        || requiredRoot != null && !real.startsWith(requiredRoot)) {
                    throw new IOException("Runtime closure entry changed while resolving links");
                }
                return real;
            }
            if (links == MAX_SYMLINKS) {
                throw new IOException("Runtime closure symbolic-link chain is too deep");
            }
            Path target = Files.readSymbolicLink(current);
            if (target.isAbsolute()) {
                throw new IOException("Runtime closure symbolic links must be relative");
            }
            current = current.getParent().resolve(target).normalize();
            if (requiredRoot != null && !current.startsWith(requiredRoot)) {
                throw new IOException("Runtime closure symlink escapes its declared root");
            }
        }
        throw new IOException("Runtime closure symbolic-link resolution failed");
    }

    private static List<String> listPaths(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            List<String> result = paths
                    .filter(path -> !path.equals(root))
                    .map(root::relativize)
                    .map(Path::toString)
                    .map(value -> value.replace('\\', '/'))
                    .sorted()
                    .toList();
            if (result.size() > RuntimeClosureManifest.MAX_FILES) {
                throw new IOException("Runtime closure exceeds its entry-count limit");
            }
            result.forEach(ShipTreePolicy::requireCanonicalRelativePath);
            return result;
        }
    }

    private static StableBytes readStable(Path path) throws IOException {
        FileIdentity before = identity(path, false);
        if (before.size() > RuntimeClosureManifest.MAX_FILE_BYTES
                || before.size() > Integer.MAX_VALUE) {
            throw new IOException("Runtime closure file exceeds its per-file byte limit");
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.toIntExact(before.size()));
        try (SeekableByteChannel input = Files.newByteChannel(path, READ_OPTIONS)) {
            ByteBuffer buffer = ByteBuffer.allocate(8192);
            long total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (read == 0) {
                    continue;
                }
                total = Math.addExact(total, read);
                if (total > before.size()) {
                    throw new IOException("Runtime closure file grew while it was read");
                }
                output.write(buffer.array(), 0, read);
                buffer.clear();
            }
        }
        FileIdentity after = identity(path, false);
        if (!before.equals(after) || output.size() != before.size()) {
            throw new IOException("Runtime closure file changed while it was read");
        }
        byte[] content = output.toByteArray();
        return new StableBytes(content, ShipDigest.sha256(content), after);
    }

    private static void verifyUnchanged(
            Path root,
            FileIdentity rootBefore,
            List<String> beforePaths,
            Map<String, CapturedSource> captured)
            throws IOException {
        if (!rootBefore.equals(identity(root, true)) || !beforePaths.equals(listPaths(root))) {
            throw new IOException("Runtime closure root changed while it was captured");
        }
        for (CapturedSource source : captured.values()) {
            if (!source.root().equals(root)) {
                continue;
            }
            Path rebound = resolveWithin(root, source.lexical());
            if (!source.resolved().equals(rebound)
                    || !source.identity().equals(identity(rebound, false))) {
                throw new IOException("Runtime closure file changed while it was captured");
            }
        }
    }

    private static FileIdentity identity(Path path, boolean directory) throws IOException {
        BasicFileAttributes before = Files.readAttributes(
                path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (before.fileKey() == null
                || directory && !before.isDirectory()
                || !directory && !before.isRegularFile()) {
            throw new IOException("Runtime closure lacks a stable regular filesystem identity");
        }
        Map<String, Object> unix = Files.readAttributes(
                path, UNIX_ATTRIBUTES, LinkOption.NOFOLLOW_LINKS);
        BasicFileAttributes after = Files.readAttributes(
                path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        Map<String, Object> unixAfter = Files.readAttributes(
                path, UNIX_ATTRIBUTES, LinkOption.NOFOLLOW_LINKS);
        BasicFileAttributes rebound = Files.readAttributes(
                path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        FileIdentity identity = fileIdentity(before.fileKey(), unix);
        FileIdentity reboundIdentity = fileIdentity(rebound.fileKey(), unixAfter);
        if (!before.fileKey().equals(after.fileKey())
                || !before.fileKey().equals(rebound.fileKey())
                || !identity.equals(reboundIdentity)
                || identity.size() != before.size()
                || identity.size() != after.size()
                || identity.size() != rebound.size()
                || identity.modifiedNanos()
                   != before.lastModifiedTime().to(TimeUnit.NANOSECONDS)
                || identity.modifiedNanos()
                   != after.lastModifiedTime().to(TimeUnit.NANOSECONDS)
                || identity.modifiedNanos()
                   != rebound.lastModifiedTime().to(TimeUnit.NANOSECONDS)
                || !directory && identity.linkCount() != 1) {
            throw new IOException("Runtime closure filesystem identity is mutable or aliased");
        }
        return identity;
    }

    private static FileIdentity fileIdentity(Object fileKey, Map<String, Object> unix)
            throws IOException {
        return new FileIdentity(
                fileKey,
                number(unix, "dev"),
                number(unix, "ino"),
                number(unix, "nlink"),
                number(unix, "size"),
                time(unix, "lastModifiedTime"),
                time(unix, "ctime"),
                Math.toIntExact(number(unix, "mode")));
    }

    private static long number(Map<String, Object> values, String name) throws IOException {
        Object value = values.get(name);
        if (!(value instanceof Number number)) {
            throw new IOException("Runtime closure filesystem lacks Unix attribute " + name);
        }
        return number.longValue();
    }

    private static long time(Map<String, Object> values, String name) throws IOException {
        Object value = values.get(name);
        if (!(value instanceof FileTime time)) {
            throw new IOException("Runtime closure filesystem lacks Unix attribute " + name);
        }
        return time.to(TimeUnit.NANOSECONDS);
    }

    @FunctionalInterface
    public interface BlobSink {

        void store(String digest, byte[] content) throws IOException;
    }

    public record Snapshot(byte[] manifest, String digest) {

        public Snapshot {
            manifest = manifest.clone();
            if (!digest.equals(ShipDigest.sha256(manifest))) {
                throw new IllegalArgumentException("Runtime closure snapshot digest does not match its manifest");
            }
        }

        @Override
        public byte[] manifest() {
            return manifest.clone();
        }
    }

    private record StableBytes(byte[] content, String digest, FileIdentity identity) {
    }

    private record CapturedSource(
            Path root,
            Path lexical,
            Path resolved,
            FileIdentity identity) {
    }

    private record FileIdentity(
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
