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

    /** Returns the identity of one project root without scanning its entries. */
    public static String rootIdentity(Path projectRoot) throws IOException {
        return new ProjectSnapshotService().rootIdentity(projectRoot);
    }

    /** Returns the path-independent filesystem identity used to coordinate one live project. */
    public static String projectIdentity(Path projectRoot) throws IOException {
        return new ProjectSnapshotService().projectIdentity(projectRoot);
    }

    public static ProjectSnapshot captureSealed(Path sealedRoot) throws IOException {
        return new ProjectSnapshotService().captureSealed(sealedRoot);
    }

    /** Captures material workspace content while rejecting denied and protected additions. */
    public static ProjectSnapshot captureStaged(Path stagedRoot) throws IOException {
        return new ProjectSnapshotService().captureStaged(stagedRoot);
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

    /** Reads one bounded volatile file within the project boundary. */
    public static byte[] readVolatile(Path root, String relativePath, int maximumBytes) throws IOException {
        return new ProjectSnapshotService().readVolatile(root, relativePath, maximumBytes);
    }
}
