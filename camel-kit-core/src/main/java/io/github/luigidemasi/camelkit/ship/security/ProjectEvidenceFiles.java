package io.github.luigidemasi.camelkit.ship.security;

import java.io.IOException;
import java.nio.file.Path;

/** Purpose-bound secure filesystem operations used to collect controller-owned evidence. */
public final class ProjectEvidenceFiles {

    private ProjectEvidenceFiles() {
    }

    public static ProjectSnapshot capture(Path projectRoot) throws IOException {
        return new ProjectSnapshotService().capture(projectRoot);
    }

    public static ProjectSnapshot captureSealed(Path sealedRoot) throws IOException {
        return new ProjectSnapshotService().captureSealed(sealedRoot);
    }

    public static boolean unchanged(ProjectSnapshot before, ProjectSnapshot after) {
        return new ProjectSnapshotService().unchanged(before, after);
    }

    public static boolean unchangedMaterialTree(ProjectSnapshot before, ProjectSnapshot after) {
        return new ProjectSnapshotService().unchangedMaterialTree(before, after);
    }

    public static ProjectSnapshot materializeMaterial(Path sourceRoot, Path targetRoot) throws IOException {
        return new ProjectSnapshotService().materializeMaterial(sourceRoot, targetRoot);
    }

    public static byte[] readMaterial(Path root, String relativePath, int maximumBytes) throws IOException {
        return new ProjectSnapshotService().readMaterial(root, relativePath, maximumBytes);
    }
}
