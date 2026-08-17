package io.github.luigidemasi.camelkit.ship.security;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import io.github.luigidemasi.camelkit.ship.security.ProjectSnapshot.Change;
import io.github.luigidemasi.camelkit.ship.security.ProjectSnapshot.Comparison;
import io.github.luigidemasi.camelkit.ship.security.ProjectSnapshot.Difference;
import io.github.luigidemasi.camelkit.ship.security.ProjectSnapshot.DirectoryEntry;
import io.github.luigidemasi.camelkit.ship.security.ProjectSnapshot.FileEntry;
import io.github.luigidemasi.camelkit.ship.security.ShipSecureFilesystem.SecureRoot;
import io.github.luigidemasi.camelkit.ship.security.ShipTreePolicy.Classification;

/** Captures and compares project trees through the read-only secure Ship filesystem boundary. */
final class ProjectSnapshotService {

    private final ShipTreePolicy policy;

    ProjectSnapshotService() {
        this(ShipTreePolicy.current());
    }

    ProjectSnapshotService(ShipTreePolicy policy) {
        this.policy = java.util.Objects.requireNonNull(policy, "policy");
    }

    ProjectSnapshot capture(Path projectRoot) throws IOException {
        try (SecureRoot root = ShipSecureFilesystem.open(projectRoot, "Ship project", policy)) {
            return root.snapshot();
        }
    }

    String rootIdentity(Path projectRoot) throws IOException {
        try (SecureRoot root = ShipSecureFilesystem.open(projectRoot, "Ship project", policy)) {
            return root.rootIdentity();
        }
    }

    String projectIdentity(Path projectRoot) throws IOException {
        try (SecureRoot root = ShipSecureFilesystem.open(projectRoot, "Ship project", policy)) {
            return root.projectIdentity();
        }
    }

    ProjectSnapshot captureStaged(Path stagedRoot) throws IOException {
        try (SecureRoot root = ShipSecureFilesystem.open(stagedRoot, "Staged Ship tree", policy)) {
            return root.snapshotStaged();
        }
    }

    /** Captures a sealed tree containing material entries only. */
    ProjectSnapshot captureSealed(Path sealedRoot) throws IOException {
        try (SecureRoot root = ShipSecureFilesystem.open(sealedRoot, "Sealed Ship tree", policy)) {
            return root.snapshotMaterialOnly();
        }
    }

    Comparison compare(ProjectSnapshot before, ProjectSnapshot after) {
        requireCompatible(before, after);
        if (!before.root().equals(after.root()) || !before.rootIdentity().equals(after.rootIdentity())) {
            throw new IllegalArgumentException("Project snapshots refer to different secure roots");
        }
        return combine(
                compareMaps(before.files(), after.files()),
                compareMaps(before.directories(), after.directories()));
    }

    /** Compares only material files across distinct roots. */
    Comparison compareContents(ProjectSnapshot before, ProjectSnapshot after) {
        requireCompatible(before, after);
        return compareMaps(material(before.files()), material(after.files()));
    }

    /** Compares the complete material file-and-directory tree across distinct roots. */
    Comparison compareMaterialTree(ProjectSnapshot before, ProjectSnapshot after) {
        requireCompatible(before, after);
        return combine(
                compareMaps(
                        portableMaterial(before.files()),
                        portableMaterial(after.files())),
                compareMaps(
                        portableMaterialDirectories(before.directories()),
                        portableMaterialDirectories(after.directories())));
    }

    boolean unchanged(ProjectSnapshot before, ProjectSnapshot after) {
        return compare(before, after).unchanged();
    }

    boolean unchangedMaterialTree(ProjectSnapshot before, ProjectSnapshot after) {
        return compareMaterialTree(before, after).unchanged();
    }

    /** Copies an authenticated material snapshot into an empty controller-owned directory. */
    ProjectSnapshot materializeMaterial(Path sourceRoot, Path targetRoot) throws IOException {
        ProjectSnapshot expected = capture(sourceRoot);
        Path target = requireEmptyTarget(targetRoot);
        try (SecureRoot source = ShipSecureFilesystem.open(sourceRoot, "Ship baseline source", policy)) {
            ProjectSnapshot before = source.snapshot();
            if (!compare(before, expected).unchanged()) {
                throw new ShipFilesystemException(
                        ShipFilesystemException.CONCURRENT_MUTATION,
                        "Ship project differs from the accepted baseline before materialization");
            }
            for (Map.Entry<String, DirectoryEntry> item : before.directories().entrySet()) {
                if (item.getValue().classification() == Classification.MATERIAL) {
                    Path directory = target.resolve(item.getKey());
                    Files.createDirectory(directory);
                }
            }
            for (Map.Entry<String, FileEntry> item : before.files().entrySet()) {
                if (item.getValue().classification() != Classification.MATERIAL) {
                    continue;
                }
                byte[] content = source.readMaterialBytes(
                        item.getKey(), Math.max(1, Math.toIntExact(item.getValue().size())));
                Path file = target.resolve(item.getKey());
                Files.write(file, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                setMode(file, item.getValue().unixMode());
            }
            for (Map.Entry<String, DirectoryEntry> item : before.directories().entrySet().stream()
                    .sorted(Map.Entry.<String, DirectoryEntry>comparingByKey().reversed())
                    .toList()) {
                if (item.getValue().classification() == Classification.MATERIAL) {
                    setMode(target.resolve(item.getKey()), item.getValue().unixMode());
                }
            }
            ProjectSnapshot after = source.snapshot();
            if (!compare(before, after).unchanged()) {
                throw new ShipFilesystemException(
                        ShipFilesystemException.CONCURRENT_MUTATION,
                        "Ship project changed while its accepted baseline was materialized");
            }
        }
        ProjectSnapshot materialized = captureSealed(target);
        if (!compareMaterialTree(expected, materialized).unchanged()) {
            throw new ShipFilesystemException(
                    ShipFilesystemException.CONCURRENT_MUTATION,
                    "Materialized Ship workspace differs from its accepted baseline");
        }
        return materialized;
    }

    /** Reads one bounded material file through the descriptor-relative project boundary. */
    byte[] readMaterial(Path root, String relativePath, int maximumBytes) throws IOException {
        try (SecureRoot secure = ShipSecureFilesystem.open(root, "Ship material read", policy)) {
            return secure.readMaterialBytes(relativePath, maximumBytes);
        }
    }

    /** Reads one bounded volatile file through the descriptor-relative project boundary. */
    byte[] readVolatile(Path root, String relativePath, int maximumBytes) throws IOException {
        try (SecureRoot secure = ShipSecureFilesystem.open(root, "Ship volatile read", policy)) {
            return secure.readVolatileBytes(relativePath, maximumBytes);
        }
    }

    private static Path requireEmptyTarget(Path targetRoot) throws IOException {
        Path supplied = targetRoot.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(supplied)
                || !Files.isDirectory(supplied, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Ship materialized workspace must be a real directory");
        }
        Path target = supplied.toRealPath();
        try (var entries = Files.newDirectoryStream(target)) {
            if (entries.iterator().hasNext()) {
                throw new IOException("Ship materialized workspace must be empty");
            }
        }
        return target;
    }

    private static void setMode(Path path, int unixMode) throws IOException {
        int permissions = unixMode & 0777;
        String mode = "---------";
        char[] chars = mode.toCharArray();
        int[] bits = {0400, 0200, 0100, 0040, 0020, 0010, 0004, 0002, 0001};
        char[] values = {'r', 'w', 'x', 'r', 'w', 'x', 'r', 'w', 'x'};
        for (int index = 0; index < bits.length; index++) {
            if ((permissions & bits[index]) != 0) {
                chars[index] = values[index];
            }
        }
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(new String(chars)));
    }

    private void requireCompatible(ProjectSnapshot before, ProjectSnapshot after) {
        if (before == null || after == null) {
            throw new IllegalArgumentException("Both project snapshots are required");
        }
        requirePolicy(before);
        requirePolicy(after);
        if (!before.policyDigest().equals(after.policyDigest())) {
            throw new IllegalArgumentException("Project snapshots use different Ship tree policies");
        }
    }

    private void requirePolicy(ProjectSnapshot snapshot) {
        if (snapshot == null || !policy.digest().equals(snapshot.policyDigest())) {
            throw new IllegalArgumentException("Project snapshot was not captured under the current Ship tree policy");
        }
    }

    private static Map<String, FileEntry> material(Map<String, FileEntry> files) {
        TreeMap<String, FileEntry> result = new TreeMap<>();
        files.forEach((path, entry) -> {
            if (entry.classification() == Classification.MATERIAL) {
                result.put(path, entry);
            }
        });
        return result;
    }

    private static Map<String, PortableFile> portableMaterial(
            Map<String, FileEntry> files) {
        TreeMap<String, PortableFile> result = new TreeMap<>();
        files.forEach((path, entry) -> {
            if (entry.classification() == Classification.MATERIAL) {
                result.put(path, new PortableFile(
                        entry.size(), entry.digest(), entry.unixMode() & 0777));
            }
        });
        return result;
    }

    private static Map<String, PortableDirectory> portableMaterialDirectories(
            Map<String, DirectoryEntry> directories) {
        TreeMap<String, PortableDirectory> result = new TreeMap<>();
        directories.forEach((path, entry) -> {
            if (entry.classification() == Classification.MATERIAL) {
                result.put(path, new PortableDirectory(entry.unixMode() & 0777));
            }
        });
        return result;
    }

    private static Comparison combine(Comparison first, Comparison second) {
        List<Difference> differences = new ArrayList<>(first.differences());
        differences.addAll(second.differences());
        differences.sort(java.util.Comparator.comparing(Difference::path));
        return new Comparison(differences);
    }

    private static <T> Comparison compareMaps(Map<String, T> before, Map<String, T> after) {
        Set<String> paths = new LinkedHashSet<>();
        paths.addAll(before.keySet());
        paths.addAll(after.keySet());
        List<Difference> differences = new ArrayList<>();
        for (String path : paths.stream().sorted().toList()) {
            T oldEntry = before.get(path);
            T newEntry = after.get(path);
            if (oldEntry == null) {
                differences.add(new Difference(path, Change.ADDED));
            } else if (newEntry == null) {
                differences.add(new Difference(path, Change.DELETED));
            } else if (!oldEntry.equals(newEntry)) {
                differences.add(new Difference(path, Change.MODIFIED));
            }
        }
        return new Comparison(differences);
    }

    private record PortableFile(long size, String digest, int permissions) {
    }

    private record PortableDirectory(int permissions) {
    }
}
