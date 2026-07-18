package io.github.luigidemasi.camelkit.ship.security;

import java.io.IOException;

import io.github.luigidemasi.camelkit.ship.context.ContextFilesystemPolicy.ProjectAccess;
import io.github.luigidemasi.camelkit.ship.context.ContextFilesystemPolicy.ProjectRootAdmission;
import io.github.luigidemasi.camelkit.ship.context.InitialContextException;

/**
 * Purpose-bound access to user-approved project documents.
 *
 * <p>
 * This facade narrows the public API; it does not grant or represent Ship lifecycle authority. Production certification
 * still requires the later platform helper to bind the exact opened file descriptor and mount.
 */
public final class ProjectContextFiles implements AutoCloseable {

    private final ShipSecureFilesystem.SecureRoot root;
    private final ProjectAccess access;

    private ProjectContextFiles(ShipSecureFilesystem.SecureRoot root, ProjectAccess access) {
        this.root = root;
        this.access = access;
    }

    /** Opens and retains the exact project root during policy admission. */
    public static HeldRoot hold(ProjectRootAdmission admission) throws IOException {
        if (admission == null) {
            throw new NullPointerException("project root admission");
        }
        return new HeldRoot(
                ShipSecureFilesystem.open(
                        admission.canonicalRoot(), "Ship context project", ShipTreePolicy.current()));
    }

    /** Adopts the exact held project root previously admitted by the initial-context policy. */
    public static ProjectContextFiles open(ProjectAccess access) throws IOException {
        if (access == null) {
            throw new NullPointerException("project access");
        }
        HeldRoot held = access.transferHeldRoot();
        ShipSecureFilesystem.SecureRoot root = null;
        try {
            root = held.take();
            root.validateBinding();
            return new ProjectContextFiles(root, access);
        } catch (IOException | RuntimeException e) {
            try {
                if (root == null) {
                    held.close();
                } else {
                    root.close();
                }
            } catch (IOException closeFailure) {
                e.addSuppressed(closeFailure);
            }
            throw e;
        }
    }

    /** Reads one canonical, bounded material document from the held project root. */
    public byte[] readDocument(String canonicalRelativePath, int maximumBytes) throws IOException {
        try {
            String admittedPath = access.requireContextPath(canonicalRelativePath);
            byte[] content = root.readMaterialBytes(admittedPath, maximumBytes);
            access.requireContextPath(admittedPath);
            return content;
        } catch (InitialContextException e) {
            throw new ShipFilesystemException(
                    ShipFilesystemException.UNSAFE_ENTRY,
                    "Project context path is no longer admitted",
                    e);
        }
    }

    @Override
    public void close() throws IOException {
        root.close();
    }

    /** Opaque one-shot lease over the exact secure root opened during policy admission. */
    public static final class HeldRoot implements AutoCloseable {

        private ShipSecureFilesystem.SecureRoot root;

        private HeldRoot(ShipSecureFilesystem.SecureRoot root) {
            this.root = root;
        }

        private synchronized ShipSecureFilesystem.SecureRoot take() throws IOException {
            if (root == null) {
                throw new IOException("The admitted project root lease is no longer available");
            }
            ShipSecureFilesystem.SecureRoot claimed = root;
            root = null;
            return claimed;
        }

        @Override
        public synchronized void close() throws IOException {
            if (root != null) {
                try {
                    root.close();
                } finally {
                    root = null;
                }
            }
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + '@' + Integer.toHexString(System.identityHashCode(this));
        }
    }
}
