package io.github.luigidemasi.camelkit.ship.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import io.github.luigidemasi.camelkit.ship.security.ShipTreePolicy.Classification;

/** Content-addressed project-tree identity captured under one exact Ship tree policy. */
public record ProjectSnapshot(
        int schemaVersion,
        String root,
        String rootIdentity,
        String policyDigest,
        Map<String, DirectoryEntry> directories,
        Map<String, FileEntry> files,
        String digest) {

    public static final int SCHEMA_VERSION = 5;

    private static final int FILE_TYPE_MASK = 0170000;
    private static final int DIRECTORY_TYPE = 0040000;
    private static final int REGULAR_FILE_TYPE = 0100000;
    private static final int UNIX_MODE_MASK = 0177777;
    private static final long MAX_UNIX_ID = 0xffff_ffffL;

    public ProjectSnapshot {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported project-snapshot schema: " + schemaVersion);
        }
        if (root == null || root.isBlank()) {
            throw new IllegalArgumentException("Project-snapshot root must be absolute");
        }
        Path parsedRoot = Path.of(root);
        if (!parsedRoot.isAbsolute() || !parsedRoot.normalize().equals(parsedRoot)
                || !parsedRoot.toString().equals(root)) {
            throw new IllegalArgumentException("Project-snapshot root must be canonical and absolute");
        }
        requireDigest(rootIdentity, "root identity");
        requireDigest(policyDigest, "policy digest");
        requireDigest(digest, "snapshot digest");
        ShipTreePolicy policy = ShipTreePolicy.current();
        if (!policy.isAllowedAbsolutePath(parsedRoot)) {
            throw new IllegalArgumentException("Project-snapshot root is outside the Ship tree policy");
        }
        if (!policy.digest().equals(policyDigest)) {
            throw new IllegalArgumentException("Project snapshot uses an unknown Ship tree policy");
        }
        EntryMaps entries = boundedEntries(directories, files);
        TreeMap<String, DirectoryEntry> canonicalDirectories = entries.directories();
        for (Map.Entry<String, DirectoryEntry> item : canonicalDirectories.entrySet()) {
            String path = item.getKey();
            DirectoryEntry entry = item.getValue();
            requireDepth(path, true, policy);
            Classification expected = policy.classify(path);
            if (expected == Classification.VOLATILE
                    || expected == Classification.DENIED
                    || entry.classification() != expected) {
                throw new IllegalArgumentException("Project snapshot contains an unsafe directory: " + path);
            }
        }
        directories = Collections.unmodifiableMap(canonicalDirectories);
        TreeMap<String, FileEntry> canonicalFiles = entries.files();
        for (Map.Entry<String, FileEntry> item : canonicalFiles.entrySet()) {
            String path = item.getKey();
            FileEntry entry = item.getValue();
            requireDepth(path, false, policy);
            Classification expected = policy.classify(path);
            if (expected == Classification.VOLATILE
                    || expected == Classification.DENIED
                    || entry.classification() != expected) {
                throw new IllegalArgumentException("Project snapshot contains an unsafe entry: " + path);
            }
        }
        long aggregate = 0;
        try {
            for (FileEntry entry : canonicalFiles.values()) {
                aggregate = Math.addExact(aggregate, entry.size());
            }
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Project snapshot aggregate size overflowed", e);
        }
        if (aggregate > ShipTreePolicy.DEFAULT_MAX_AGGREGATE_BYTES) {
            throw new IllegalArgumentException("Project snapshot exceeds the aggregate-byte quota");
        }
        files = Collections.unmodifiableMap(canonicalFiles);
        for (String directory : canonicalDirectories.keySet()) {
            if (canonicalFiles.containsKey(directory)) {
                throw new IllegalArgumentException(
                        "Project snapshot path is both a file and directory: " + directory);
            }
            requireRecordedParent(directory, canonicalDirectories);
        }
        for (String file : canonicalFiles.keySet()) {
            requireRecordedParent(file, canonicalDirectories);
        }
        String computed = computeCanonicalDigest(
                root, rootIdentity, policyDigest, canonicalDirectories, canonicalFiles);
        if (!computed.equals(digest)) {
            throw new IllegalArgumentException("Project-snapshot digest does not match its canonical tree");
        }
    }

    static ProjectSnapshot create(
            String root,
            String rootIdentity,
            String policyDigest,
            Map<String, DirectoryEntry> directories,
            Map<String, FileEntry> files) {
        EntryMaps entries = boundedEntries(directories, files);
        return new ProjectSnapshot(
                SCHEMA_VERSION, root, rootIdentity, policyDigest, entries.directories(), entries.files(),
                computeCanonicalDigest(
                        root, rootIdentity, policyDigest, entries.directories(), entries.files()));
    }

    static String computeDigest(
            String root,
            String rootIdentity,
            String policyDigest,
            Map<String, DirectoryEntry> directories,
            Map<String, FileEntry> files) {
        EntryMaps entries = boundedEntries(directories, files);
        return computeCanonicalDigest(
                root, rootIdentity, policyDigest, entries.directories(), entries.files());
    }

    private static String computeCanonicalDigest(
            String root,
            String rootIdentity,
            String policyDigest,
            Map<String, DirectoryEntry> directories,
            Map<String, FileEntry> files) {
        MessageDigest digest = messageDigest();
        update(digest, "camel-kit.ship.project-snapshot.v5");
        update(digest, root);
        update(digest, rootIdentity);
        update(digest, policyDigest);
        directories.forEach((path, entry) -> {
            update(digest, path);
            update(digest, "DIRECTORY");
            update(digest, entry.classification().name());
            update(digest, Integer.toString(entry.unixMode()));
            update(digest, Long.toString(entry.userId()));
            update(digest, Long.toString(entry.groupId()));
        });
        files.forEach((path, entry) -> {
            update(digest, path);
            update(digest, "REGULAR");
            update(digest, entry.classification().name());
            update(digest, Long.toString(entry.size()));
            update(digest, entry.digest());
            update(digest, Integer.toString(entry.unixMode()));
            update(digest, Long.toString(entry.userId()));
            update(digest, Long.toString(entry.groupId()));
        });
        return "sha256:" + HexFormat.of().formatHex(digest.digest());
    }

    public record DirectoryEntry(
            Classification classification, int unixMode, long userId, long groupId) {

        public DirectoryEntry {
            if (classification == null || classification == Classification.VOLATILE
                    || classification == Classification.DENIED) {
                throw new IllegalArgumentException("Invalid project-snapshot directory identity");
            }
            requireUnixIdentity(unixMode, userId, groupId, DIRECTORY_TYPE, "directory");
        }
    }

    private static EntryMaps boundedEntries(
            Map<String, DirectoryEntry> directories, Map<String, FileEntry> files) {
        Map<String, DirectoryEntry> suppliedDirectories = Objects.requireNonNull(
                directories, "project snapshot directories");
        Map<String, FileEntry> suppliedFiles = Objects.requireNonNull(
                files, "project snapshot files");
        long preflightCount = (long) suppliedDirectories.size() + suppliedFiles.size();
        if (preflightCount > ShipTreePolicy.DEFAULT_MAX_FILE_COUNT) {
            throw new IllegalArgumentException("Project snapshot exceeds the maximum entry count");
        }
        TreeMap<String, DirectoryEntry> canonicalDirectories = boundedCopy(
                suppliedDirectories, ShipTreePolicy.DEFAULT_MAX_FILE_COUNT, "directory");
        TreeMap<String, FileEntry> canonicalFiles = boundedCopy(
                suppliedFiles,
                ShipTreePolicy.DEFAULT_MAX_FILE_COUNT - canonicalDirectories.size(),
                "file");
        return new EntryMaps(canonicalDirectories, canonicalFiles);
    }

    private static <T> TreeMap<String, T> boundedCopy(
            Map<String, T> source, int maximumEntries, String label) {
        if (source.size() > maximumEntries) {
            throw new IllegalArgumentException("Project snapshot exceeds the maximum entry count");
        }
        TreeMap<String, T> result = new TreeMap<>();
        int seen = 0;
        for (Map.Entry<String, T> item : source.entrySet()) {
            if (++seen > maximumEntries) {
                throw new IllegalArgumentException("Project snapshot exceeds the maximum entry count");
            }
            String path = ShipTreePolicy.requireCanonicalRelativePath(item.getKey());
            T entry = item.getValue();
            if (entry == null || result.putIfAbsent(path, entry) != null) {
                throw new IllegalArgumentException(
                        "Project snapshot contains an invalid " + label + " entry: " + path);
            }
        }
        return result;
    }

    private static void requireDepth(String path, boolean directory, ShipTreePolicy policy) {
        int components = 1;
        for (int index = 0; index < path.length(); index++) {
            if (path.charAt(index) == '/') {
                components++;
            }
        }
        int depth = directory ? components : components - 1;
        if (depth > policy.maxDepth()) {
            throw new IllegalArgumentException("Project snapshot path exceeds the depth quota: " + path);
        }
    }

    private static void requireUnixIdentity(
            int unixMode, long userId, long groupId, int expectedType, String label) {
        if ((unixMode & ~UNIX_MODE_MASK) != 0
                || (unixMode & FILE_TYPE_MASK) != expectedType
                || userId < 0 || userId > MAX_UNIX_ID
                || groupId < 0 || groupId > MAX_UNIX_ID) {
            throw new IllegalArgumentException(
                    "Invalid project-snapshot Unix " + label + " identity");
        }
    }

    private record EntryMaps(
            TreeMap<String, DirectoryEntry> directories, TreeMap<String, FileEntry> files) {
    }

    private static void requireDigest(String value, String label) {
        if (value == null || !value.matches("sha256:[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Project-snapshot " + label + " is invalid");
        }
    }

    private static void requireRecordedParent(
            String path, Map<String, DirectoryEntry> directories) {
        int slash = path.lastIndexOf('/');
        if (slash > 0 && !directories.containsKey(path.substring(0, slash))) {
            throw new IllegalArgumentException(
                    "Project snapshot is missing the parent directory for " + path);
        }
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private static MessageDigest messageDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    public record FileEntry(
            Classification classification,
            long size,
            String digest,
            int unixMode,
            long userId,
            long groupId) {

        public FileEntry {
            if (classification == null || classification == Classification.VOLATILE
                    || classification == Classification.DENIED || size < 0
                    || size > ShipTreePolicy.DEFAULT_MAX_FILE_BYTES) {
                throw new IllegalArgumentException("Invalid project-snapshot file identity");
            }
            requireDigest(digest, "file digest");
            requireUnixIdentity(unixMode, userId, groupId, REGULAR_FILE_TYPE, "file");
        }
    }

    record Difference(String path, Change change) {
    }

    enum Change {
        ADDED,
        MODIFIED,
        DELETED
    }

    record Comparison(List<Difference> differences) {

        Comparison {
            differences = differences == null ? List.of() : List.copyOf(differences);
        }

        boolean unchanged() {
            return differences.isEmpty();
        }
    }
}
