package io.github.luigidemasi.camelkit.ship.controller;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import io.github.luigidemasi.camelkit.ship.ShipDigest;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction;
import io.github.luigidemasi.camelkit.ship.security.ProjectSnapshot;
import io.github.luigidemasi.camelkit.ship.security.ShipTreePolicy;
import io.github.luigidemasi.camelkit.ship.security.ShipTreePolicy.Classification;

/** Deterministic provenance identities for files in one controller-owned immutable project source. */
public record ProjectSourceManifest(
        int schemaVersion,
        String sourceSnapshotDigest,
        List<FileSource> sources,
        String digest) {

    public static final int SCHEMA_VERSION = 1;
    private static final String MANIFEST_DOMAIN = "camel-kit.ship.project-source-manifest.v1";
    private static final String SOURCE_DOMAIN = "camel-kit.ship.project-file-source.v1";
    private static final String LOCATOR_PREFIX = "controller:project-source#";

    public ProjectSourceManifest {
        if (schemaVersion != SCHEMA_VERSION || !ShipDigest.isSha256(sourceSnapshotDigest)) {
            throw new IllegalArgumentException("Invalid project-source manifest identity");
        }
        sources = List.copyOf(Objects.requireNonNull(sources, "manifest sources"));
        if (sources.size() > ShipTreePolicy.DEFAULT_MAX_FILE_COUNT
                || sources.stream().anyMatch(Objects::isNull)
                || !sources.equals(sources.stream()
                        .sorted(Comparator.comparing(FileSource::relativePath))
                        .toList())) {
            throw new IllegalArgumentException("Project-source manifest files are not canonical");
        }
        Set<String> paths = new HashSet<>();
        Set<String> ids = new HashSet<>();
        for (FileSource source : sources) {
            if (!paths.add(source.relativePath())
                    || !ids.add(source.sourceId())
                    || !sourceId(sourceSnapshotDigest, source.relativePath(), source.digest())
                            .equals(source.sourceId())
                    || !locator(source.relativePath()).equals(source.locator())) {
                throw new IllegalArgumentException(
                        "Project-source manifest contains a noncanonical file identity");
            }
        }
        if (!computeDigest(sourceSnapshotDigest, sources).equals(digest)) {
            throw new IllegalArgumentException("Project-source manifest digest does not match its files");
        }
    }

    public static ProjectSourceManifest from(ProjectSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "project snapshot");
        snapshot.directories().forEach((path, entry) -> requireMaterial(path, entry.classification()));
        List<FileSource> sources = snapshot.files().entrySet().stream()
                .map(entry -> {
                    requireMaterial(entry.getKey(), entry.getValue().classification());
                    return new FileSource(
                            entry.getKey(),
                            sourceId(snapshot.digest(), entry.getKey(), entry.getValue().digest()),
                            locator(entry.getKey()),
                            entry.getValue().digest(),
                            entry.getValue().size());
                })
                .sorted(Comparator.comparing(FileSource::relativePath))
                .toList();
        return new ProjectSourceManifest(
                SCHEMA_VERSION,
                snapshot.digest(),
                sources,
                computeDigest(snapshot.digest(), sources));
    }

    private static void requireMaterial(String path, Classification classification) {
        if (classification != Classification.MATERIAL) {
            throw new IllegalArgumentException("Project source contains a protected entry: " + path);
        }
    }

    private static String sourceId(String snapshotDigest, String relativePath, String fileDigest) {
        String digest = ShipDigest.sha256(Interaction.canonicalMacBytes(new String[]{
                SOURCE_DOMAIN, snapshotDigest, relativePath, fileDigest
        }));
        return "project-file-" + digest.substring("sha256:".length());
    }

    private static String locator(String relativePath) {
        return LOCATOR_PREFIX + ShipTreePolicy.requireCanonicalRelativePath(relativePath);
    }

    private static String computeDigest(String snapshotDigest, List<FileSource> sources) {
        List<String> fields = new ArrayList<>(2 + sources.size() * 5);
        fields.add(MANIFEST_DOMAIN);
        fields.add(snapshotDigest);
        for (FileSource source : sources) {
            fields.add(source.relativePath());
            fields.add(source.sourceId());
            fields.add(source.locator());
            fields.add(source.digest());
            fields.add(Long.toString(source.byteSize()));
        }
        return ShipDigest.sha256(Interaction.canonicalMacBytes(fields.toArray(String[]::new)));
    }

    public record FileSource(
            String relativePath,
            String sourceId,
            String locator,
            String digest,
            long byteSize) {

        public FileSource {
            relativePath = ShipTreePolicy.requireCanonicalRelativePath(relativePath);
            if (sourceId == null
                    || !sourceId.matches("project-file-[0-9a-f]{64}")
                    || locator == null
                    || locator.isBlank()
                    || !ShipDigest.isSha256(digest)
                    || byteSize < 0
                    || byteSize > ShipTreePolicy.current().maxFileBytes()) {
                throw new IllegalArgumentException("Invalid project-file source identity");
            }
        }
    }
}
