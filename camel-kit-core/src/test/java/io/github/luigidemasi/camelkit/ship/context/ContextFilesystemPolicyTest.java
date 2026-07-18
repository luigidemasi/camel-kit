package io.github.luigidemasi.camelkit.ship.context;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.StandardProtocolFamily;
import java.net.URI;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.SeekableByteChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.github.luigidemasi.camelkit.ship.context.ContextFilesystemPolicy.ExternalCandidateInspection;
import io.github.luigidemasi.camelkit.ship.context.ContextFilesystemPolicy.ProjectAccess;
import io.github.luigidemasi.camelkit.ship.context.InitialContextException.Reason;
import io.github.luigidemasi.camelkit.ship.context.PendingDocumentConsent.CandidatePhysicalIdentity;
import io.github.luigidemasi.camelkit.ship.security.ProjectContextFiles;
import io.github.luigidemasi.camelkit.ship.security.ShipFilesystemException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

@EnabledOnOs(OS.LINUX)
class ContextFilesystemPolicyTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void requiresExplicitCanonicalPolicyRoots() throws Exception {
        InitialContextException relative = assertThrows(
                InitialContextException.class,
                () -> new ContextFilesystemPolicy(Path.of("state"), List.of()));
        InitialContextException absentCredentials = assertThrows(
                InitialContextException.class,
                () -> new ContextFilesystemPolicy(temporaryDirectory.toRealPath(), null));
        InitialContextException unsafePolicyRoot = assertThrows(
                InitialContextException.class,
                () -> new ContextFilesystemPolicy(
                        temporaryDirectory.resolve("unsafe\nstate").toAbsolutePath(), List.of()));

        assertEquals(Reason.INVALID_POLICY_CONFIGURATION, relative.reason());
        assertEquals(Reason.INVALID_POLICY_CONFIGURATION, absentCredentials.reason());
        assertEquals("<invalid-policy-root>", unsafePolicyRoot.input());
        assertFalse(unsafePolicyRoot.toString().contains("unsafe\nstate"));
    }

    @Test
    void deniesStateCredentialAndBuiltinPseudoFilesystemTrees() throws Exception {
        Path state = Files.createDirectory(temporaryDirectory.resolve("state")).toRealPath();
        Path credentials = Files.createDirectory(temporaryDirectory.resolve("credentials")).toRealPath();
        Path stateFile = Files.writeString(state.resolve("controller.json"), "state");
        Path credentialFile = Files.writeString(credentials.resolve("token"), "token");
        ContextFilesystemPolicy policy = new ContextFilesystemPolicy(state, List.of(credentials));

        assertReason(Reason.DOCUMENT_PATH_DENIED, () -> policy.inspectExternal(stateFile));
        assertReason(Reason.DOCUMENT_PATH_DENIED, () -> policy.inspectExternal(credentialFile));
        assertReason(Reason.DOCUMENT_PATH_DENIED, () -> policy.inspectExternal(Path.of("/proc/version")));
        assertReason(Reason.DOCUMENT_PATH_DENIED, () -> policy.inspectExternal(Path.of("/sys/kernel")));
        assertReason(Reason.DOCUMENT_PATH_DENIED, () -> policy.inspectExternal(Path.of("/dev/null")));
        assertReason(Reason.DOCUMENT_PATH_DENIED, () -> policy.inspectExternal(Path.of("/run")));
    }

    @Test
    void canonicalizesAliasesBeforeApplyingDeniedRoots() throws Exception {
        Path state = Files.createDirectory(temporaryDirectory.resolve("state")).toRealPath();
        Path stateFile = Files.writeString(state.resolve("controller.json"), "state");
        Path alias = temporaryDirectory.resolve("state-alias");
        Files.createSymbolicLink(alias, state);
        ContextFilesystemPolicy policy = new ContextFilesystemPolicy(state, List.of());

        assertReason(
                Reason.DOCUMENT_PATH_DENIED,
                () -> policy.inspectExternal(alias.resolve(stateFile.getFileName()).toAbsolutePath()));
    }

    @Test
    void projectsAbsentDeniedRootsThroughTheirCanonicalExistingAncestor() throws Exception {
        Path realParent = Files.createDirectory(temporaryDirectory.resolve("real-parent"));
        Path futureCredentialRoot = realParent.resolve("future-credentials");
        Path state = Files.createDirectory(temporaryDirectory.resolve("state")).toRealPath();
        ContextFilesystemPolicy policy = new ContextFilesystemPolicy(
                state, List.of(futureCredentialRoot));

        Path secret = Files.createDirectories(realParent.resolve("future-credentials"))
                .resolve("secret.txt");
        Files.writeString(secret, "secret");

        assertReason(Reason.DOCUMENT_PATH_DENIED, () -> policy.inspectExternal(secret.toRealPath()));
    }

    @Test
    void rejectsReplacementOfMaterializedProtectedSuffix() throws Exception {
        Path state = Files.createDirectory(temporaryDirectory.resolve("replacement-state")).toRealPath();
        Path futureCredentials = temporaryDirectory.resolve("replacement-parent/credentials").toAbsolutePath();
        Path document = Files.writeString(temporaryDirectory.resolve("replacement-context.md"), "context");
        ContextFilesystemPolicy policy = new ContextFilesystemPolicy(state, List.of(futureCredentials));

        Files.createDirectories(futureCredentials);
        policy.inspectExternal(document.toRealPath());
        Files.move(futureCredentials, futureCredentials.resolveSibling("original-credentials"));
        Files.createDirectory(futureCredentials);

        assertProtectedRootChanged(() -> policy.inspectExternal(document.toRealPath()));
    }

    @Test
    void rejectsSymlinkMaterializedAsProtectedSuffix() throws Exception {
        Path state = Files.createDirectory(temporaryDirectory.resolve("symlink-state")).toRealPath();
        Path futureCredentials = temporaryDirectory.resolve("symlink-parent/credentials").toAbsolutePath();
        Path target = Files.createDirectory(temporaryDirectory.resolve("symlink-target")).toRealPath();
        Path document = Files.writeString(temporaryDirectory.resolve("symlink-context.md"), "context");
        ContextFilesystemPolicy policy = new ContextFilesystemPolicy(state, List.of(futureCredentials));

        Files.createDirectories(futureCredentials.getParent());
        Files.createSymbolicLink(futureCredentials, target);

        assertProtectedRootChanged(() -> policy.inspectExternal(document.toRealPath()));
    }

    @Test
    void rejectsDisappearanceOfMaterializedProtectedSuffix() throws Exception {
        Path state = Files.createDirectory(temporaryDirectory.resolve("disappearance-state")).toRealPath();
        Path futureCredentials = temporaryDirectory.resolve("disappearance-parent/credentials").toAbsolutePath();
        Path document = Files.writeString(temporaryDirectory.resolve("disappearance-context.md"), "context");
        ContextFilesystemPolicy policy = new ContextFilesystemPolicy(state, List.of(futureCredentials));

        Files.createDirectories(futureCredentials);
        policy.inspectExternal(document.toRealPath());
        Files.delete(futureCredentials);

        assertProtectedRootChanged(() -> policy.inspectExternal(document.toRealPath()));
    }

    @Test
    void rejectsConfiguredSymlinkAliasesBeforeTheyCanBeRetargeted() throws Exception {
        Path state = Files.createDirectory(temporaryDirectory.resolve("state")).toRealPath();
        Path credentials = Files.createDirectory(temporaryDirectory.resolve("credentials")).toRealPath();
        Path alias = temporaryDirectory.resolve("credential-alias");
        Files.createSymbolicLink(alias, credentials);

        assertReason(
                Reason.INVALID_POLICY_CONFIGURATION,
                () -> new ContextFilesystemPolicy(state, List.of(alias)));
    }

    @Test
    void rejectsDeniedAndNonCanonicalProjectRoots() throws Exception {
        Path state = Files.createDirectory(temporaryDirectory.resolve("state")).toRealPath();
        Path credentials = Files.createDirectory(temporaryDirectory.resolve("credentials")).toRealPath();
        Path project = Files.createDirectory(temporaryDirectory.resolve("project")).toRealPath();
        ContextFilesystemPolicy policy = new ContextFilesystemPolicy(state, List.of(credentials));

        try (ProjectAccess access = policy.admitProject(project)) {
            assertEquals(project, access.canonicalRoot());
        }
        assertReason(Reason.INVALID_PROJECT_ROOT, () -> policy.admitProject(state));
        assertReason(Reason.INVALID_PROJECT_ROOT, () -> policy.admitProject(credentials));
        assertReason(Reason.INVALID_PROJECT_ROOT, () -> policy.admitProject(Path.of("/proc")));
        assertReason(Reason.INVALID_PROJECT_ROOT, () -> policy.admitProject(Path.of("relative")));
    }

    @Test
    void admitsOnlyMaterialProjectContextPaths() throws Exception {
        ContextFilesystemPolicy policy = policy();
        Path project = Files.createDirectory(temporaryDirectory.resolve("project")).toRealPath();
        try (ProjectAccess access = policy.admitProject(project)) {
            assertEquals("docs/requirements.md", access.requireContextPath("docs/requirements.md"));
            assertReason(Reason.DOCUMENT_PATH_DENIED,
                    () -> access.requireContextPath(".camel-kit/config.properties"));
            assertReason(Reason.DOCUMENT_PATH_DENIED,
                    () -> access.requireContextPath(".CAMEL-KIT/config.properties"));
            assertReason(Reason.DOCUMENT_PATH_DENIED,
                    () -> access.requireContextPath(".git/HEAD"));
            assertReason(Reason.DOCUMENT_PATH_DENIED,
                    () -> access.requireContextPath(".GIT/HEAD"));
            assertReason(Reason.DOCUMENT_PATH_DENIED,
                    () -> access.requireContextPath(".ssh/id_ed25519"));
            assertReason(Reason.DOCUMENT_PATH_DENIED,
                    () -> access.requireContextPath("module/target/generated.yaml"));
            assertReason(Reason.DOCUMENT_PATH_ESCAPE,
                    () -> access.requireContextPath("../requirements.md"));
            InitialContextException unsafe = assertThrows(
                    InitialContextException.class,
                    () -> access.requireContextPath("unsafe\nrequirements.md"));
            assertEquals(Reason.DOCUMENT_PATH_ESCAPE, unsafe.reason());
            assertEquals("<invalid-project-path>", unsafe.input());
            assertFalse(unsafe.toString().contains("unsafe\nrequirements.md"));
        }
    }

    @Test
    void rejectsConfiguredDeniedSubtreesNestedInsideTheProject() throws Exception {
        Path project = Files.createDirectory(temporaryDirectory.resolve("project")).toRealPath();
        Path state = Files.createDirectory(temporaryDirectory.resolve("state")).toRealPath();
        Path nestedCredentials = Files.createDirectory(project.resolve("private-documents")).toRealPath();
        ContextFilesystemPolicy policy = new ContextFilesystemPolicy(state, List.of(nestedCredentials));

        assertReason(Reason.INVALID_PROJECT_ROOT, () -> policy.admitProject(project));

        Files.move(nestedCredentials, project.resolve("renamed-private-documents"));
        assertReason(Reason.PROTECTED_ROOT_CHANGED, () -> policy.admitProject(project));
    }

    @Test
    void projectAccessIsOpaqueIdentityBasedAndPurposeBound() throws Exception {
        ContextFilesystemPolicy policy = policy();
        Path project = Files.createDirectory(temporaryDirectory.resolve("admitted-project")).toRealPath();
        Files.writeString(project.resolve("requirements.md"), "requirements");
        try (ProjectAccess first = policy.admitProject(project);
             ProjectAccess second = policy.admitProject(project)) {
            assertEquals(0, ProjectAccess.class.getConstructors().length);
            assertTrue(Modifier.isPublic(ProjectAccess.class.getModifiers()));
            assertTrue(Modifier.isFinal(ProjectAccess.class.getModifiers()));
            assertNotEquals(first, second);
            assertFalse(first.toString().contains(project.toString()));

            Method admission = ContextFilesystemPolicy.class.getDeclaredMethod("admitProject", Path.class);
            assertFalse(Modifier.isPublic(admission.getModifiers()));
            assertEquals(ProjectAccess.class, admission.getReturnType());

            try (ProjectContextFiles files = ProjectContextFiles.open(first)) {
                assertArrayEquals(
                        "requirements".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        files.readDocument("requirements.md", 1024));
                ShipFilesystemException denied = assertThrows(
                        ShipFilesystemException.class,
                        () -> files.readDocument(".camel-kit/config.properties", 1024));
                assertEquals(ShipFilesystemException.UNSAFE_ENTRY, denied.code());
            }
            assertThrows(IOException.class, () -> ProjectContextFiles.open(first));
            second.close();
            assertThrows(IOException.class, () -> ProjectContextFiles.open(second));
        }
    }

    @Test
    void admittedDescriptorRejectsProjectReplacementBeforeLazyOpen() throws Exception {
        ContextFilesystemPolicy policy = policy();
        Path project = Files.createDirectory(temporaryDirectory.resolve("replaceable-project")).toRealPath();
        Files.writeString(project.resolve("requirements.md"), "admitted");

        try (ProjectAccess access = policy.admitProject(project)) {
            Path original = project.resolveSibling("original-project");
            Files.move(project, original);
            Files.createDirectory(project);
            Files.writeString(project.resolve("requirements.md"), "replacement");

            ShipFilesystemException changed = assertThrows(
                    ShipFilesystemException.class,
                    () -> ProjectContextFiles.open(access));
            assertEquals(ShipFilesystemException.CONCURRENT_MUTATION, changed.code());
        }
    }

    @Test
    void everyProjectReadFailsClosedAfterProtectedRootDrift() throws Exception {
        Path state = Files.createDirectory(temporaryDirectory.resolve("state")).toRealPath();
        Path credentials = Files.createDirectory(temporaryDirectory.resolve("credentials")).toRealPath();
        Path project = Files.createDirectory(temporaryDirectory.resolve("stable-project")).toRealPath();
        Files.writeString(project.resolve("requirements.md"), "requirements");
        ContextFilesystemPolicy policy = new ContextFilesystemPolicy(state, List.of(credentials));

        try (ProjectAccess access = policy.admitProject(project);
             ProjectContextFiles files = ProjectContextFiles.open(access)) {
            Files.move(credentials, temporaryDirectory.resolve("renamed-credentials"));

            ShipFilesystemException changed = assertThrows(
                    ShipFilesystemException.class,
                    () -> files.readDocument("requirements.md", 1024));
            assertEquals(ShipFilesystemException.UNSAFE_ENTRY, changed.code());
            InitialContextException readFailure = assertInstanceOf(
                    InitialContextException.class, changed.getCause());
            assertEquals("<protected-root>", readFailure.input());
            assertNull(readFailure.getCause());
            assertFalse(readFailure.toString().contains(credentials.toString()));

            InitialContextException inspectionFailure = assertProtectedRootChanged(
                    () -> policy.inspectExternal(project.resolve("requirements.md")));
            assertFalse(inspectionFailure.getMessage().contains(credentials.toString()));
            assertFalse(inspectionFailure.toString().contains(credentials.toString()));
        }
    }

    @Test
    void inspectsStableUnixIdentityWithoutOpeningContent() throws Exception {
        Path document = Files.writeString(temporaryDirectory.resolve("requirements.md"), "requirements");
        ContextFilesystemPolicy policy = policy();

        ExternalCandidateInspection inspection = policy.inspectExternal(document.toRealPath());
        CandidatePhysicalIdentity identity = inspection.candidateIdentity();

        assertEquals(document.toRealPath(), inspection.canonicalPath());
        assertEquals(((Number) Files.getAttribute(document, "unix:dev")).longValue(), identity.device());
        assertEquals(((Number) Files.getAttribute(document, "unix:ino")).longValue(), identity.inode());
        assertEquals(((Number) Files.getAttribute(document, "unix:mode")).intValue(), identity.unixMode());
        assertEquals(1, identity.hardLinkCount());
        assertEquals(Files.size(document), identity.byteSize());
        assertEquals(
                Files.getLastModifiedTime(document).to(TimeUnit.NANOSECONDS),
                identity.lastModifiedNanos());
        assertEquals(
                ((FileTime) Files.getAttribute(document, "unix:ctime")).to(TimeUnit.NANOSECONDS),
                identity.changeTimeNanos());
        assertEquals(
                PosixFilePermissions.toString(Files.getPosixFilePermissions(document)),
                identity.posixPermissions());
        assertEquals(inspection, policy.inspectExternal(document.toRealPath()));
    }

    @Test
    void ctimeDetectsModificationEvenWhenMtimeAndSizeAreRestored() throws Exception {
        Path document = Files.writeString(temporaryDirectory.resolve("requirements.md"), "first-value");
        ContextFilesystemPolicy policy = policy();
        CandidatePhysicalIdentity before = policy.inspectExternal(document.toRealPath()).candidateIdentity();
        FileTime originalModified = Files.getLastModifiedTime(document);

        Files.writeString(document, "other-value");
        Files.setLastModifiedTime(document, originalModified);
        CandidatePhysicalIdentity after = policy.inspectExternal(document.toRealPath()).candidateIdentity();

        assertEquals(before.byteSize(), after.byteSize());
        assertEquals(before.lastModifiedNanos(), after.lastModifiedNanos());
        assertNotEquals(before.changeTimeNanos(), after.changeTimeNanos());
        assertNotEquals(before, after);
    }

    @Test
    void rejectsHardLinksAndSpecialFiles() throws Exception {
        ContextFilesystemPolicy policy = policy();
        Path document = Files.writeString(temporaryDirectory.resolve("hard-linked.md"), "requirements");
        Files.createLink(temporaryDirectory.resolve("second-link.md"), document);

        assertReason(Reason.DOCUMENT_NOT_REGULAR, () -> policy.inspectExternal(document.toRealPath()));
        assertReason(Reason.DOCUMENT_NOT_REGULAR,
                () -> policy.inspectExternal(temporaryDirectory.toRealPath()));

        Path socket = temporaryDirectory.resolve("context.socket");
        try (ServerSocketChannel server = ServerSocketChannel.open(StandardProtocolFamily.UNIX)) {
            server.bind(UnixDomainSocketAddress.of(socket));
            assertReason(Reason.DOCUMENT_NOT_REGULAR, () -> policy.inspectExternal(socket.toRealPath()));
        }
    }

    @Test
    void rejectsMissingEmptyAndOversizeDocuments() throws Exception {
        ContextFilesystemPolicy policy = policy();
        Path missing = temporaryDirectory.resolve("missing.md").toAbsolutePath();
        Path empty = Files.createFile(temporaryDirectory.resolve("empty.md"));
        Path atLimit = temporaryDirectory.resolve("at-limit.md");
        try (SeekableByteChannel channel = Files.newByteChannel(
                atLimit, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            channel.position(InitialContext.MAX_DOCUMENT_BYTES - 1L);
            channel.write(ByteBuffer.wrap(new byte[]{0}));
        }
        Path oversize = temporaryDirectory.resolve("oversize.md");
        try (SeekableByteChannel channel = Files.newByteChannel(
                oversize, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            channel.position(InitialContext.MAX_DOCUMENT_BYTES);
            channel.write(ByteBuffer.wrap(new byte[]{0}));
        }
        assertReason(Reason.DOCUMENT_MISSING, () -> policy.inspectExternal(missing));
        assertReason(Reason.DOCUMENT_EMPTY, () -> policy.inspectExternal(empty.toRealPath()));
        assertEquals(
                InitialContext.MAX_DOCUMENT_BYTES,
                policy.inspectExternal(atLimit.toRealPath()).candidateIdentity().byteSize());
        assertReason(Reason.DOCUMENT_TOO_LARGE, () -> policy.inspectExternal(oversize.toRealPath()));
    }

    @Test
    void metadataInspectionDoesNotRequireContentReadPermission() throws Exception {
        ContextFilesystemPolicy policy = policy();
        Path document = Files.writeString(temporaryDirectory.resolve("metadata-only.md"), "requirements");
        Files.setPosixFilePermissions(document, Set.of());
        try {
            ExternalCandidateInspection inspection = policy.inspectExternal(document.toRealPath());
            assertEquals("---------", inspection.candidateIdentity().posixPermissions());
        } finally {
            Files.setPosixFilePermissions(document, PosixFilePermissions.fromString("rw-------"));
        }
    }

    @Test
    void rejectsRelativeAndIdentityUnsupportedExternalPaths() throws Exception {
        ContextFilesystemPolicy policy = policy();
        assertReason(Reason.DOCUMENT_PATH_ESCAPE, () -> policy.inspectExternal(Path.of("relative.md")));

        Path archive = temporaryDirectory.resolve("foreign.zip");
        URI uri = URI.create("jar:" + archive.toUri());
        try (FileSystem filesystem = FileSystems.newFileSystem(uri, Map.of("create", "true"))) {
            Path document = Files.writeString(filesystem.getPath("/requirements.md"), "requirements");
            assertReason(Reason.DOCUMENT_IDENTITY_UNAVAILABLE, () -> policy.inspectExternal(document));
        }
    }

    @Test
    void nonSecureProjectFilesystemFailsDuringAdmission() throws Exception {
        ContextFilesystemPolicy policy = policy();
        Path archive = temporaryDirectory.resolve("project.zip");
        URI uri = URI.create("jar:" + archive.toUri());
        try (FileSystem filesystem = FileSystems.newFileSystem(uri, Map.of("create", "true"))) {
            Path project = Files.createDirectory(filesystem.getPath("/project"));
            assertReason(Reason.SECURE_FILESYSTEM_UNSUPPORTED, () -> policy.admitProject(project));
        }
    }

    @Test
    void metadataInspectorHasNoContentReadSurface() {
        for (Method method : ContextFilesystemPolicy.class.getDeclaredMethods()) {
            assertFalse(isContentType(method.getReturnType()), method::toString);
            for (Class<?> parameter : method.getParameterTypes()) {
                assertFalse(isContentType(parameter), method::toString);
            }
            assertFalse(
                    method.getName().matches("(?i).*(readallbytes|readstring|newinputstream|bytechannel).*"),
                    method::toString);
        }
        assertEquals(
                List.of(Path.class, CandidatePhysicalIdentity.class),
                List.of(ExternalCandidateInspection.class.getRecordComponents()).stream()
                        .map(component -> component.getType())
                        .toList());
    }

    private ContextFilesystemPolicy policy() throws Exception {
        Path state = Files.createDirectories(temporaryDirectory.resolve("controller-state")).toRealPath();
        Path credentials = Files.createDirectories(temporaryDirectory.resolve("credential-root")).toRealPath();
        return new ContextFilesystemPolicy(state, List.of(credentials));
    }

    private static boolean isContentType(Class<?> type) {
        return type == byte[].class
                || InputStream.class.isAssignableFrom(type)
                || Reader.class.isAssignableFrom(type)
                || Channel.class.isAssignableFrom(type);
    }

    private static void assertReason(Reason expected, ThrowingOperation operation) {
        InitialContextException failure = assertThrows(InitialContextException.class, operation::run);
        assertEquals(expected, failure.reason());
    }

    private static InitialContextException assertProtectedRootChanged(ThrowingOperation operation) {
        InitialContextException failure = assertThrows(InitialContextException.class, operation::run);
        assertEquals(Reason.PROTECTED_ROOT_CHANGED, failure.reason());
        assertEquals("<protected-root>", failure.input());
        assertNull(failure.getCause());
        return failure;
    }

    @FunctionalInterface
    private interface ThrowingOperation {
        void run() throws Exception;
    }
}
