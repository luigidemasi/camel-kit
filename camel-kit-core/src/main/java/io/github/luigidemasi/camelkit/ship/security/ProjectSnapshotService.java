package io.github.luigidemasi.camelkit.ship.security;

import java.io.IOException;
import java.nio.file.Path;
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

    /** Compares every tracked material and protected entry while allowing distinct physical roots. */
    Comparison compareExactContents(ProjectSnapshot before, ProjectSnapshot after) {
        requireCompatible(before, after);
        return combine(
                compareMaps(before.files(), after.files()),
                compareMaps(before.directories(), after.directories()));
    }

    /** Compares the complete material file-and-directory tree across distinct roots. */
    Comparison compareMaterialTree(ProjectSnapshot before, ProjectSnapshot after) {
        requireCompatible(before, after);
        return combine(
                compareMaps(material(before.files()), material(after.files())),
                compareMaps(
                        materialDirectories(before.directories()),
                        materialDirectories(after.directories())));
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

    private static Map<String, DirectoryEntry> materialDirectories(
            Map<String, DirectoryEntry> directories) {
        TreeMap<String, DirectoryEntry> result = new TreeMap<>();
        directories.forEach((path, entry) -> {
            if (entry.classification() == Classification.MATERIAL) {
                result.put(path, entry);
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
}
