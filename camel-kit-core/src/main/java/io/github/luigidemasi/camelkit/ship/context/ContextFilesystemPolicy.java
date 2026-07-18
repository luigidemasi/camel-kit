package io.github.luigidemasi.camelkit.ship.context;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.ProviderMismatchException;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.github.luigidemasi.camelkit.ship.context.InitialContextException.Reason;
import io.github.luigidemasi.camelkit.ship.context.PendingDocumentConsent.CandidatePhysicalIdentity;
import io.github.luigidemasi.camelkit.ship.security.ProjectContextFiles;
import io.github.luigidemasi.camelkit.ship.security.ProjectContextFiles.HeldRoot;
import io.github.luigidemasi.camelkit.ship.security.ShipFilesystemException;
import io.github.luigidemasi.camelkit.ship.security.ShipTreePolicy;

/** Metadata-only admission policy for documents requested as initial Ship context. */
public final class ContextFilesystemPolicy {

    private static final List<Path> BUILTIN_DENIED_ROOTS = List.of(
            Path.of("/proc"), Path.of("/sys"), Path.of("/dev"), Path.of("/run"));

    private final List<Path> deniedRoots;
    private final List<ProtectedRootBinding> protectedRootBindings;

    public ContextFilesystemPolicy(Path controllerStateRoot, List<Path> credentialRoots)
                                                                                         throws InitialContextException {
        Set<Path> configuredRoots = new LinkedHashSet<>();
        configuredRoots.add(requireConfiguredRoot(controllerStateRoot, "controller state root"));
        if (credentialRoots == null) {
            throw failure(
                    Reason.INVALID_POLICY_CONFIGURATION,
                    "<null>",
                    "credential roots must be supplied explicitly");
        }
        for (Path root : credentialRoots) {
            configuredRoots.add(requireConfiguredRoot(root, "credential root"));
        }
        configuredRoots.addAll(BUILTIN_DENIED_ROOTS);

        List<ProtectedRootBinding> bindings = new ArrayList<>(configuredRoots.size());
        Set<Path> roots = new LinkedHashSet<>();
        for (Path root : configuredRoots) {
            ProtectedRootBinding binding = bindProtectedRoot(root);
            bindings.add(binding);
            roots.add(binding.configuredRoot());
            roots.add(binding.projectedRoot());
        }
        deniedRoots = List.copyOf(roots);
        protectedRootBindings = List.copyOf(bindings);
    }

    ExternalCandidateInspection inspectExternal(Path requested) throws InitialContextException {
        requireStableProtectedRoots();
        String input = requested == null ? "<null>" : requested.toString();
        if (requested == null) {
            throw failure(Reason.DOCUMENT_MISSING, input, "External document path is missing");
        }
        if (!requested.isAbsolute()) {
            throw failure(
                    Reason.DOCUMENT_PATH_ESCAPE,
                    input,
                    "External document path must already be absolute");
        }

        Path lexical = requested.normalize();
        if (isDenied(lexical)) {
            throw failure(Reason.DOCUMENT_PATH_DENIED, input, "External document path is protected");
        }

        try {
            Path canonical = lexical.toRealPath();
            if (isDenied(canonical)) {
                throw failure(Reason.DOCUMENT_PATH_DENIED, input, "External document resolves into a protected root");
            }
            if (!lexical.equals(canonical)) {
                throw failure(Reason.DOCUMENT_NOT_REGULAR, input, "External document path contains a symbolic link");
            }

            CandidatePhysicalIdentity before = inspectIdentity(canonical, input);
            CandidatePhysicalIdentity after;
            Path confirmed;
            try {
                after = inspectIdentity(canonical, input);
                confirmed = lexical.toRealPath();
            } catch (NoSuchFileException e) {
                throw failure(
                        Reason.DOCUMENT_CHANGED,
                        input,
                        "External document disappeared during inspection",
                        e);
            }
            if (!canonical.equals(confirmed) || !before.equals(after)) {
                throw failure(Reason.DOCUMENT_CHANGED, input, "External document changed during inspection");
            }
            requireStableProtectedRoots();
            return new ExternalCandidateInspection(canonical, after);
        } catch (InitialContextException e) {
            throw e;
        } catch (NoSuchFileException e) {
            throw failure(Reason.DOCUMENT_MISSING, input, "External document does not exist", e);
        } catch (AccessDeniedException e) {
            throw failure(Reason.DOCUMENT_UNREADABLE, input, "External document is not readable", e);
        } catch (UnsupportedOperationException | IllegalArgumentException | ClassCastException e) {
            throw failure(
                    Reason.DOCUMENT_IDENTITY_UNAVAILABLE,
                    input,
                    "External document filesystem does not expose the required identity",
                    e);
        } catch (IOException e) {
            throw failure(Reason.DOCUMENT_UNREADABLE, input, "External document could not be inspected", e);
        }
    }

    ProjectAccess admitProject(Path projectRoot) throws InitialContextException {
        requireStableProtectedRoots();
        Path canonicalRoot = requireProjectContextRoot(projectRoot);
        requireNoProtectedRootOverlap(canonicalRoot);
        HeldRoot heldRoot;
        try {
            heldRoot = ProjectContextFiles.hold(new ProjectRootAdmission(canonicalRoot));
        } catch (ShipFilesystemException e) {
            Reason reason = ShipFilesystemException.SECURE_FILESYSTEM_UNSUPPORTED.equals(e.code())
                    ? Reason.SECURE_FILESYSTEM_UNSUPPORTED
                    : Reason.INVALID_PROJECT_ROOT;
            throw failure(
                    reason,
                    canonicalRoot.toString(),
                    "Project root could not be bound to a secure admission handle",
                    e);
        } catch (IOException e) {
            throw failure(
                    Reason.INVALID_PROJECT_ROOT,
                    canonicalRoot.toString(),
                    "Project root could not be bound to a secure admission handle",
                    e);
        }
        try {
            requireStableProtectedRoots();
            requireNoProtectedRootOverlap(canonicalRoot);
            return new ProjectAccess(this, canonicalRoot, heldRoot);
        } catch (InitialContextException e) {
            try {
                heldRoot.close();
            } catch (IOException closeFailure) {
                e.addSuppressed(closeFailure);
            }
            throw e;
        }
    }

    private String requireProjectContextPath(Path projectRoot, String relativePath)
            throws InitialContextException {
        requireStableProtectedRoots();
        Path canonicalRoot = Objects.requireNonNull(projectRoot, "project root");
        requireNoProtectedRootOverlap(canonicalRoot);
        String canonical;
        try {
            canonical = ShipTreePolicy.requireCanonicalRelativePath(relativePath);
        } catch (IllegalArgumentException e) {
            throw failure(
                    Reason.DOCUMENT_PATH_ESCAPE,
                    ContextModelSupport.safePathDiagnostic(relativePath, "<invalid-project-path>"),
                    "Project context path is not canonical",
                    e);
        }
        String folded = canonical.toLowerCase(Locale.ROOT);
        if (subtree(folded, ".camel-kit")
                || subtree(folded, ".git")
                || isDenied(canonicalRoot.resolve(canonical).normalize())
                || ShipTreePolicy.current().classify(canonical) != ShipTreePolicy.Classification.MATERIAL) {
            throw failure(
                    Reason.DOCUMENT_PATH_DENIED,
                    canonical,
                    "Project context path is protected or volatile");
        }
        return canonical;
    }

    private Path requireProjectContextRoot(Path projectRoot) throws InitialContextException {
        String input = ContextModelSupport.safePathDiagnostic(
                projectRoot == null ? null : projectRoot.toString(), "<invalid-project-root>");
        if (projectRoot == null
                || !projectRoot.isAbsolute()
                || !projectRoot.normalize().equals(projectRoot)) {
            throw failure(
                    Reason.INVALID_PROJECT_ROOT,
                    input,
                    "Project root must be an absolute normalized canonical directory");
        }
        try {
            Path canonical = projectRoot.toRealPath();
            if (!canonical.equals(projectRoot)
                    || !Files.isDirectory(canonical, LinkOption.NOFOLLOW_LINKS)
                    || isDenied(canonical)) {
                throw failure(
                        Reason.INVALID_PROJECT_ROOT,
                        input,
                        "Project root is not a canonical allowed directory");
            }
            return canonical;
        } catch (InitialContextException e) {
            throw e;
        } catch (IOException e) {
            throw failure(
                    Reason.INVALID_PROJECT_ROOT,
                    input,
                    "Project root could not be canonicalized",
                    e);
        }
    }

    private CandidatePhysicalIdentity inspectIdentity(Path canonical, String input)
            throws IOException, InitialContextException {
        BasicFileAttributes basic = Files.readAttributes(
                canonical, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (basic.isSymbolicLink() || !basic.isRegularFile()) {
            throw failure(Reason.DOCUMENT_NOT_REGULAR, input, "External document is not a regular file");
        }
        if (basic.fileKey() == null) {
            throw failure(
                    Reason.DOCUMENT_IDENTITY_UNAVAILABLE,
                    input,
                    "External document filesystem does not expose a file identity");
        }

        PosixFileAttributes posix = Files.readAttributes(
                canonical, PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        Map<String, Object> unix = Files.readAttributes(
                canonical,
                "unix:dev,ino,nlink,mode,ctime,size,lastModifiedTime",
                LinkOption.NOFOLLOW_LINKS);
        long size = number(unix, "size").longValue();
        long links = number(unix, "nlink").longValue();
        int mode = number(unix, "mode").intValue();
        FileTime modified = fileTime(unix, "lastModifiedTime");
        FileTime changed = fileTime(unix, "ctime");

        if (size == 0) {
            throw failure(Reason.DOCUMENT_EMPTY, input, "External document is empty");
        }
        if (size < 0 || size > InitialContext.MAX_DOCUMENT_BYTES) {
            throw failure(Reason.DOCUMENT_TOO_LARGE, input, "External document exceeds the byte limit");
        }
        if (links != 1 || (mode & 0170000) != 0100000) {
            throw failure(
                    Reason.DOCUMENT_NOT_REGULAR,
                    input,
                    "External document must be a regular file with one hard link");
        }
        if (basic.size() != size
                || posix.size() != size
                || !basic.lastModifiedTime().equals(modified)
                || !posix.lastModifiedTime().equals(modified)) {
            throw failure(Reason.DOCUMENT_CHANGED, input, "External document changed during inspection");
        }

        return new CandidatePhysicalIdentity(
                number(unix, "dev").longValue(),
                number(unix, "ino").longValue(),
                size,
                modified.to(TimeUnit.NANOSECONDS),
                changed.to(TimeUnit.NANOSECONDS),
                mode,
                PosixFilePermissions.toString(posix.permissions()),
                links);
    }

    private boolean isDenied(Path candidate) {
        if (!ShipTreePolicy.current().isAllowedAbsolutePath(candidate)) {
            return true;
        }
        for (Path root : deniedRoots) {
            try {
                if (candidate.equals(root) || candidate.startsWith(root)) {
                    return true;
                }
            } catch (ProviderMismatchException e) {
                // A foreign filesystem cannot alias a root on the controller's default filesystem.
            }
        }
        return false;
    }

    private static Path requireConfiguredRoot(Path root, String label) throws InitialContextException {
        if (root == null
                || !root.isAbsolute()
                || root.getNameCount() == 0
                || !root.normalize().equals(root)) {
            throw failure(
                    Reason.INVALID_POLICY_CONFIGURATION,
                    "<invalid-policy-root>",
                    label + " must be an absolute normalized path");
        }
        StringBuilder components = new StringBuilder();
        for (Path component : root) {
            if (!components.isEmpty()) {
                components.append('/');
            }
            components.append(component);
        }
        try {
            ShipTreePolicy.requireCanonicalRelativePath(components.toString());
        } catch (IllegalArgumentException e) {
            throw failure(
                    Reason.INVALID_POLICY_CONFIGURATION,
                    "<invalid-policy-root>",
                    label + " contains an unsafe or oversized path component",
                    e);
        }
        return root;
    }

    private static ProtectedRootBinding bindProtectedRoot(Path root)
            throws InitialContextException {
        Path existing = root;
        List<Path> missingSuffix = new ArrayList<>();
        while (existing != null && !Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) {
            missingSuffix.add(existing.getFileName());
            existing = existing.getParent();
        }
        if (existing == null) {
            throw failure(
                    Reason.INVALID_POLICY_CONFIGURATION,
                    "<invalid-policy-root>",
                    "Protected root has no canonicalizable ancestor");
        }
        try {
            rejectSymbolicComponents(existing);
            Path projected = existing.toRealPath();
            if (!existing.equals(projected)
                    || !Files.isDirectory(projected, LinkOption.NOFOLLOW_LINKS)) {
                throw failure(
                        Reason.INVALID_POLICY_CONFIGURATION,
                        "<invalid-policy-root>",
                        "Protected roots must have a link-free directory ancestor");
            }
            DirectoryIdentity identity = captureDirectoryIdentity(projected);
            List<Path> unresolvedSuffix = new ArrayList<>(missingSuffix.size());
            for (int index = missingSuffix.size() - 1; index >= 0; index--) {
                Path component = missingSuffix.get(index);
                unresolvedSuffix.add(component);
                projected = projected.resolve(component);
            }
            return new ProtectedRootBinding(
                    root, projected.normalize(), existing, identity, unresolvedSuffix);
        } catch (InitialContextException e) {
            throw e;
        } catch (IOException e) {
            throw failure(
                    Reason.INVALID_POLICY_CONFIGURATION,
                    "<invalid-policy-root>",
                    "Protected root could not be canonicalized",
                    e);
        }
    }

    private void requireStableProtectedRoots() throws InitialContextException {
        for (ProtectedRootBinding binding : protectedRootBindings) {
            try {
                binding.validate();
            } catch (IOException ignored) {
                throw failure(
                        Reason.PROTECTED_ROOT_CHANGED,
                        "<protected-root>",
                        "A protected root is no longer bound to its original directory");
            }
        }
    }

    private void requireNoProtectedRootOverlap(Path projectRoot) throws InitialContextException {
        for (Path protectedRoot : deniedRoots) {
            try {
                if (projectRoot.startsWith(protectedRoot) || protectedRoot.startsWith(projectRoot)) {
                    throw failure(
                            Reason.INVALID_PROJECT_ROOT,
                            projectRoot.toString(),
                            "Project root overlaps controller state, credentials, or a denied system root");
                }
            } catch (ProviderMismatchException e) {
                // A foreign filesystem cannot overlap a protected root on the controller filesystem.
            }
        }
    }

    private static DirectoryIdentity captureDirectoryIdentity(Path directory) throws IOException {
        DirectoryStream<Path> opened = Files.newDirectoryStream(directory);
        if (!(opened instanceof SecureDirectoryStream<?>)) {
            opened.close();
            throw new IOException("Protected root filesystem lacks descriptor-relative identity");
        }
        @SuppressWarnings("unchecked")
        SecureDirectoryStream<Path> secure = (SecureDirectoryStream<Path>) opened;
        try {
            return requireBoundDirectoryIdentity(directory, secure);
        } finally {
            secure.close();
        }
    }

    private static DirectoryIdentity requireBoundDirectoryIdentity(
            Path directory, SecureDirectoryStream<Path> secure)
            throws IOException {
        BasicFileAttributeView view = secure.getFileAttributeView(BasicFileAttributeView.class);
        if (view == null) {
            throw new IOException("Directory lacks descriptor-relative attributes");
        }
        BasicFileAttributes heldBefore = view.readAttributes();
        BasicFileAttributes namedBefore = Files.readAttributes(
                directory, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        Path confirmed = directory.toRealPath();
        BasicFileAttributes namedAfter = Files.readAttributes(
                directory, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        BasicFileAttributes heldAfter = view.readAttributes();
        Object fileKey = heldBefore.fileKey();
        if (!directory.equals(confirmed)
                || fileKey == null
                || !heldBefore.isDirectory()
                || heldBefore.isSymbolicLink()
                || !heldAfter.isDirectory()
                || heldAfter.isSymbolicLink()
                || !fileKey.equals(heldAfter.fileKey())
                || !fileKey.equals(namedBefore.fileKey())
                || !fileKey.equals(namedAfter.fileKey())) {
            throw new IOException("Directory changed across its descriptor identity sample");
        }
        return new DirectoryIdentity(fileKey);
    }

    private static void rejectSymbolicComponents(Path path) throws IOException {
        Path current = path.getRoot();
        for (Path component : path) {
            current = current == null ? component : current.resolve(component);
            if (Files.isSymbolicLink(current)) {
                throw new IOException("Protected root contains a symbolic link: " + current);
            }
        }
    }

    private static Number number(Map<String, Object> attributes, String name) {
        Object value = attributes.get(name);
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("Missing Unix attribute: " + name);
        }
        return number;
    }

    private static boolean subtree(String path, String root) {
        return path.equals(root) || path.startsWith(root + "/");
    }

    private static FileTime fileTime(Map<String, Object> attributes, String name) {
        Object value = attributes.get(name);
        if (!(value instanceof FileTime fileTime)) {
            throw new IllegalArgumentException("Missing Unix attribute: " + name);
        }
        return fileTime;
    }

    private static InitialContextException failure(Reason reason, String input, String message) {
        return new InitialContextException(reason, input, message);
    }

    private static InitialContextException failure(
            Reason reason, String input, String message, Throwable cause) {
        return new InitialContextException(reason, input, message, cause);
    }

    private record DirectoryIdentity(Object fileKey) {
        private DirectoryIdentity {
            Objects.requireNonNull(fileKey, "directory file key");
        }
    }

    private static final class ProtectedRootBinding {
        private final Path configuredRoot;
        private final Path projectedRoot;
        private final Path anchor;
        private final List<Path> unresolvedSuffix;
        private final Map<Path, DirectoryIdentity> materializedIdentities = new LinkedHashMap<>();

        private ProtectedRootBinding(
                                     Path configuredRoot,
                                     Path projectedRoot,
                                     Path anchor,
                                     DirectoryIdentity identity,
                                     List<Path> unresolvedSuffix) {
            this.configuredRoot = Objects.requireNonNull(configuredRoot, "configured root");
            this.projectedRoot = Objects.requireNonNull(projectedRoot, "projected root");
            this.anchor = Objects.requireNonNull(anchor, "protected root anchor");
            this.unresolvedSuffix = List.copyOf(unresolvedSuffix);
            materializedIdentities.put(anchor, Objects.requireNonNull(identity, "protected root identity"));
        }

        private Path configuredRoot() {
            return configuredRoot;
        }

        private Path projectedRoot() {
            return projectedRoot;
        }

        private synchronized void validate() throws IOException {
            rejectSymbolicComponents(anchor);
            Path canonicalAnchor = anchor.toRealPath();
            DirectoryIdentity expectedAnchor = materializedIdentities.get(anchor);
            if (!canonicalAnchor.equals(anchor)
                    || !expectedAnchor.equals(captureDirectoryIdentity(canonicalAnchor))) {
                throw new IOException("Protected root anchor changed after policy construction");
            }

            Path lexical = anchor;
            Path projected = anchor;
            for (Path component : unresolvedSuffix) {
                lexical = lexical.resolve(component);
                projected = projected.resolve(component);
                if (!Files.exists(lexical, LinkOption.NOFOLLOW_LINKS)) {
                    Path missing = lexical;
                    if (materializedIdentities.keySet().stream().anyMatch(path -> path.startsWith(missing))) {
                        throw new IOException("A materialized protected root component disappeared");
                    }
                    return;
                }
                if (Files.isSymbolicLink(lexical)
                        || !Files.isDirectory(lexical, LinkOption.NOFOLLOW_LINKS)
                        || !projected.equals(lexical.toRealPath())) {
                    throw new IOException("Protected root suffix is no longer a real directory path");
                }
                DirectoryIdentity current = captureDirectoryIdentity(lexical);
                DirectoryIdentity previous = materializedIdentities.putIfAbsent(lexical, current);
                if (previous != null && !previous.equals(current)) {
                    throw new IOException("A protected root component changed physical identity");
                }
            }
        }
    }

    /** Opaque same-process bridge carrying one project root admitted by policy. */
    public static final class ProjectRootAdmission {
        private final Path canonicalRoot;

        private ProjectRootAdmission(Path canonicalRoot) {
            this.canonicalRoot = Objects.requireNonNull(canonicalRoot, "canonical root");
        }

        /**
         * Returns the canonical root bound to this opaque admission. Non-public construction is API hygiene, not an
         * authentication boundary.
         */
        public Path canonicalRoot() {
            return canonicalRoot;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + '@' + Integer.toHexString(System.identityHashCode(this));
        }
    }

    record ExternalCandidateInspection(
            Path canonicalPath, CandidatePhysicalIdentity candidateIdentity) {
        ExternalCandidateInspection {
            Objects.requireNonNull(canonicalPath, "canonical path");
            Objects.requireNonNull(candidateIdentity, "candidate identity");
        }
    }

    /**
     * Opaque same-process handle carrying access admitted by this policy. Non-public construction is API hygiene, not
     * an authentication boundary.
     */
    public static final class ProjectAccess implements AutoCloseable {
        private final ContextFilesystemPolicy policy;
        private final Path canonicalRoot;
        private HeldRoot heldRoot;

        private ProjectAccess(
                              ContextFilesystemPolicy policy,
                              Path canonicalRoot,
                              HeldRoot heldRoot) {
            this.policy = policy;
            this.canonicalRoot = canonicalRoot;
            this.heldRoot = heldRoot;
        }

        /** Returns the canonical root bound to this admitted access. */
        public Path canonicalRoot() {
            return canonicalRoot;
        }

        /** Revalidates one context path against the policy that admitted this access. */
        public String requireContextPath(String relativePath) throws InitialContextException {
            return policy.requireProjectContextPath(canonicalRoot, relativePath);
        }

        /** Transfers the opaque admitted-root lease to the purpose-bound project facade exactly once. */
        public synchronized HeldRoot transferHeldRoot() throws IOException {
            if (heldRoot == null) {
                throw new IOException("The admitted project root lease is no longer available");
            }
            HeldRoot claimed = heldRoot;
            heldRoot = null;
            return claimed;
        }

        /**
         * Releases the admitted-root lease. Release failure is fail-closed and aborts an encompassing resolution;
         * {@code secure-filesystem-unsupported} covers failure to establish or release this secure filesystem
         * lifecycle.
         */
        @Override
        public synchronized void close() throws InitialContextException {
            if (heldRoot == null) {
                return;
            }
            try {
                heldRoot.close();
            } catch (IOException e) {
                throw failure(
                        Reason.SECURE_FILESYSTEM_UNSUPPORTED,
                        canonicalRoot.toString(),
                        "Admitted project root lease could not be closed",
                        e);
            } finally {
                heldRoot = null;
            }
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + '@' + Integer.toHexString(System.identityHashCode(this));
        }
    }
}
