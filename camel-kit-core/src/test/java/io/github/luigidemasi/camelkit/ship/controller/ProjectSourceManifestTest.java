package io.github.luigidemasi.camelkit.ship.controller;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import io.github.luigidemasi.camelkit.ship.security.ProjectEvidenceFiles;
import io.github.luigidemasi.camelkit.ship.security.ProjectSnapshot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledOnOs(OS.LINUX)
class ProjectSourceManifestTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void bindsCanonicalControllerOwnedCitationIdentitiesAndRoundTripsJson() throws Exception {
        Path project = Files.createDirectory(temporaryDirectory.resolve("project"));
        write(project, "z-last.txt", "last");
        write(project, "a-first.txt", "first");
        write(project, ".git/HEAD", "protected");
        Path target = Files.createDirectory(temporaryDirectory.resolve("manifest-target"));
        ProjectSnapshot snapshot = ProjectEvidenceFiles.materializeMaterial(project, target);

        ProjectSourceManifest manifest = ProjectSourceManifest.from(snapshot);

        assertEquals(snapshot.digest(), manifest.sourceSnapshotDigest());
        assertEquals(
                List.of("a-first.txt", "z-last.txt"),
                manifest.sources().stream()
                        .map(ProjectSourceManifest.FileSource::relativePath)
                        .toList());
        assertTrue(manifest.sources().stream()
                .allMatch(source -> source.sourceId().matches("project-file-[0-9a-f]{64}")));
        assertTrue(manifest.sources().stream()
                .allMatch(source -> source.locator()
                        .equals("controller:project-source#" + source.relativePath())));
        assertEquals(manifest, ProjectSourceManifest.from(ProjectEvidenceFiles.captureSealed(target)));
        byte[] encoded = ShipJson.mapper().writeValueAsBytes(manifest);
        assertEquals(manifest, ShipJson.mapper().readValue(encoded, ProjectSourceManifest.class));

        ArrayList<ProjectSourceManifest.FileSource> escaped = new ArrayList<>(manifest.sources());
        ProjectSourceManifest.FileSource first = escaped.get(0);
        escaped.set(0, new ProjectSourceManifest.FileSource(
                first.relativePath(),
                first.sourceId(),
                "controller:project-source#../escape",
                first.digest(),
                first.byteSize()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ProjectSourceManifest(
                        manifest.schemaVersion(),
                        manifest.sourceSnapshotDigest(),
                        escaped,
                        manifest.digest()));
    }

    private static Path write(Path root, String relative, String content) throws Exception {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        return Files.writeString(file, content);
    }
}
