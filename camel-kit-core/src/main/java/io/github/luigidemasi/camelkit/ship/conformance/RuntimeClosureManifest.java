package io.github.luigidemasi.camelkit.ship.conformance;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import io.github.luigidemasi.camelkit.ship.ShipDigest;
import io.github.luigidemasi.camelkit.ship.security.ShipTreePolicy;

/** Portable, path-free identity of one snapshotted runtime package tree and its launch entry point. */
public record RuntimeClosureManifest(
        int schemaVersion,
        EntryPoint entryPoint,
        List<RuntimeFile> files) {

    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_FILES = 50_000;
    public static final long MAX_FILE_BYTES = 256L * 1024 * 1024;
    public static final long MAX_AGGREGATE_BYTES = 1024L * 1024 * 1024;

    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9-]{0,63}");
    private static final Comparator<RuntimeFile> ORDER = Comparator
            .comparing(RuntimeFile::rootId)
            .thenComparing(RuntimeFile::relativePath);

    public RuntimeClosureManifest {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "Runtime closure schemaVersion must be " + SCHEMA_VERSION);
        }
        Objects.requireNonNull(entryPoint, "runtime closure entry point");
        files = List.copyOf(Objects.requireNonNull(files, "runtime closure files"));
        if (files.isEmpty() || files.size() > MAX_FILES
                || files.stream().anyMatch(Objects::isNull)
                || !files.equals(files.stream().sorted(ORDER).toList())) {
            throw new IllegalArgumentException(
                    "Runtime closure files must be nonempty, bounded, and canonically ordered");
        }
        Set<String> identities = new HashSet<>();
        long aggregate = 0;
        boolean foundEntryPoint = false;
        for (RuntimeFile file : files) {
            if (!identities.add(file.rootId() + "\0" + file.relativePath())) {
                throw new IllegalArgumentException("Runtime closure contains a duplicate file identity");
            }
            try {
                aggregate = Math.addExact(aggregate, file.byteSize());
            } catch (ArithmeticException e) {
                throw new IllegalArgumentException("Runtime closure aggregate size overflowed", e);
            }
            if (aggregate > MAX_AGGREGATE_BYTES) {
                throw new IllegalArgumentException("Runtime closure exceeds its aggregate byte limit");
            }
            if (entryPoint.rootId().equals(file.rootId())
                    && entryPoint.relativePath().equals(file.relativePath())
                    && entryPoint.digest().equals(file.digest())) {
                if (!file.executable()) {
                    throw new IllegalArgumentException("Runtime closure entry point is not executable");
                }
                foundEntryPoint = true;
            }
        }
        if (!foundEntryPoint) {
            throw new IllegalArgumentException(
                    "Runtime closure entry point does not identify an executable file");
        }
    }

    public record EntryPoint(
            String rootId,
            String relativePath,
            String digest) {

        public EntryPoint {
            requireId(rootId, "runtime closure entry-point root ID");
            relativePath = requirePath(relativePath, "runtime closure entry-point path");
            requireDigest(digest, "runtime closure entry-point digest");
        }
    }

    public record RuntimeFile(
            String rootId,
            String relativePath,
            String digest,
            long byteSize,
            boolean executable) {

        public RuntimeFile {
            requireId(rootId, "runtime closure root ID");
            relativePath = requirePath(relativePath, "runtime closure file path");
            requireDigest(digest, "runtime closure file digest");
            if (byteSize < 0 || byteSize > MAX_FILE_BYTES) {
                throw new IllegalArgumentException(
                        "Runtime closure file byte size is outside its bounded range");
            }
        }
    }

    private static void requireId(String value, String label) {
        if (value == null || !ID.matcher(value).matches()) {
            throw new IllegalArgumentException(label + " is not canonical");
        }
    }

    private static String requirePath(String value, String label) {
        try {
            return ShipTreePolicy.requireCanonicalRelativePath(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(label + " is not canonical", e);
        }
    }

    private static void requireDigest(String value, String label) {
        if (!ShipDigest.isSha256(value)) {
            throw new IllegalArgumentException(label + " is not a SHA-256 content address");
        }
    }
}
