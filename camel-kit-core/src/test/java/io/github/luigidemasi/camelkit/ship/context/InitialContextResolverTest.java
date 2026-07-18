package io.github.luigidemasi.camelkit.ship.context;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.URI;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.luigidemasi.camelkit.ship.context.ContextFilesystemPolicy.ProjectAccess;
import io.github.luigidemasi.camelkit.ship.context.InitialContextRequest.DocumentReference;
import io.github.luigidemasi.camelkit.ship.context.InitialContextRequest.UserText;
import io.github.luigidemasi.camelkit.ship.security.ShipFilesystemException;
import io.github.luigidemasi.camelkit.ship.security.ShipFilesystemTestFixtures;
import io.github.luigidemasi.camelkit.ship.security.ShipTreePolicy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

@EnabledOnOs(OS.LINUX)
class InitialContextResolverTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void resolvesAllFourRequestFormsAndPreservesLiteralAtText() throws Exception {
        Path project = project("forms");
        RecordingOpener opener = new RecordingOpener(
                Map.of(
                        "requirements.md", bytes("requirements")));
        InitialContextResolver resolver = resolver(opener, 128, 256);

        InitialContext none = resolved(resolver.resolve(project, new InitialContextRequest.None()));
        InitialContext explicit = resolved(resolver.resolve(project, new InitialContextRequest.Text("@literal")));
        InitialContext documents = resolved(resolver.resolve(
                project, new InitialContextRequest.Documents("requirements.md")));
        InitialContext composite = resolved(resolver.resolve(
                project,
                new InitialContextRequest.Composite(
                        List.of(
                                new DocumentReference("requirements.md"), new UserText("qualification")))));
        InitialContext escaped = resolved(resolver.resolve(
                project, InitialContextRequest.fromConvenienceTokens(List.of("@@literal"))));

        assertInstanceOf(InitialContext.None.class, none);
        assertInstanceOf(InitialContext.Text.class, explicit);
        assertInstanceOf(InitialContext.Documents.class, documents);
        assertInstanceOf(InitialContext.Composite.class, composite);
        assertEquals("@literal", explicit.sources().get(0).content());
        assertEquals("@literal", escaped.sources().get(0).content());
        assertEquals(2, opener.openCount);
        assertEquals(2, opener.readCount);
        assertEquals(1, InitialContextResolver.class.getConstructors().length);
        assertArrayEquals(
                new Class<?>[]{ContextFilesystemPolicy.class},
                InitialContextResolver.class.getConstructors()[0].getParameterTypes());
    }

    @Test
    void opensOneHeldProjectReaderForMultipleDocuments() throws Exception {
        Path project = project("multiple-project-documents");
        RecordingOpener opener = new RecordingOpener(
                Map.of(
                        "one.md", bytes("one"),
                        "two.md", bytes("two")));
        InitialContextResolver resolver = resolver(opener, 128, 256);

        InitialContext context = resolved(resolver.resolve(
                project,
                new InitialContextRequest.Documents(
                        List.of(
                                new DocumentReference("one.md"), new DocumentReference("two.md")))));

        assertEquals(1, opener.openCount);
        assertEquals(2, opener.readCount);
        assertEquals(1, opener.closeCount);
        assertEquals(List.of("one.md", "two.md"), opener.paths);
        assertEquals(List.of("one", "two"), context.sources().stream()
                .map(InitialContext.Source::content)
                .toList());
    }

    @Test
    void externalOnlyRequestsNeverOpenOrReadProjectFiles() throws Exception {
        Path project = project("external-only");
        Path external = Files.writeString(temporaryDirectory.resolve("external.md"), "external").toRealPath();
        RecordingOpener opener = new RecordingOpener(Map.of());
        InitialContextResolver resolver = resolver(opener, 128, 256);

        ContextResolution.Pending pending = assertInstanceOf(
                ContextResolution.Pending.class,
                resolver.resolve(project, new InitialContextRequest.Documents(external.toString())));

        assertEquals(0, opener.openCount);
        assertEquals(0, opener.readCount);
        assertEquals(0, opener.closeCount);
        assertEquals(1, pending.consents().size());
        assertEquals(external, pending.consents().get(0).canonicalPath());
    }

    @Test
    void returnsEveryExternalConsentInOriginalMixedSourceOrder() throws Exception {
        Path project = project("mixed-pending");
        Path first = Files.writeString(temporaryDirectory.resolve("first.md"), "first").toRealPath();
        Path second = Files.writeString(temporaryDirectory.resolve("second.md"), "second").toRealPath();
        RecordingOpener opener = new RecordingOpener(Map.of("project.md", bytes("project")));
        InitialContextResolver resolver = resolver(opener, 128, 256);

        ContextResolution.Pending pending = assertInstanceOf(
                ContextResolution.Pending.class,
                resolver.resolve(
                        project,
                        new InitialContextRequest.Composite(
                                List.of(
                                        new DocumentReference(first.toString()),
                                        new UserText("text"),
                                        new DocumentReference("project.md"),
                                        new DocumentReference(second.toString())))));

        assertEquals(List.of(0, 3), pending.consents().stream()
                .map(PendingDocumentConsent::sourceOrdinal)
                .toList());
        assertEquals(List.of(first, second), pending.consents().stream()
                .map(PendingDocumentConsent::canonicalPath)
                .toList());
        assertEquals(0, opener.openCount);
        assertEquals(0, opener.readCount);
        assertEquals(0, opener.closeCount);
        assertTrue(opener.paths.isEmpty());
    }

    @Test
    void externalInvalidUtf8AndNulRemainPendingWithoutContentReads() throws Exception {
        Path project = project("external-opaque");
        Path invalidUtf8 = Files.write(
                temporaryDirectory.resolve("invalid-utf8.md"), new byte[]{(byte) 0xc3, 0x28}).toRealPath();
        Path nul = Files.write(
                temporaryDirectory.resolve("nul.md"), new byte[]{'a', 0, 'b'}).toRealPath();
        RecordingOpener opener = new RecordingOpener(Map.of());
        InitialContextResolver resolver = resolver(opener, 128, 256);

        ContextResolution.Pending pending = assertInstanceOf(
                ContextResolution.Pending.class,
                resolver.resolve(
                        project,
                        new InitialContextRequest.Documents(
                                List.of(
                                        new DocumentReference(invalidUtf8.toString()),
                                        new DocumentReference(nul.toString())))));

        assertEquals(2, pending.consents().size());
        assertEquals(0, opener.openCount);
        assertEquals(0, opener.readCount);
    }

    @Test
    void treatsLexicalAbsoluteProjectPathsAsDescriptorRelativeProjectContent() throws Exception {
        Path project = project("absolute-project");
        Path absolute = project.resolve("docs/requirements.md");
        RecordingOpener opener = new RecordingOpener(
                Map.of(
                        "docs/requirements.md", bytes("requirements")));
        InitialContextResolver resolver = resolver(opener, 128, 256);

        InitialContext context = resolved(resolver.resolve(
                project, new InitialContextRequest.Documents(absolute.toString())));

        assertEquals(List.of("docs/requirements.md"), opener.paths);
        assertEquals("project:docs/requirements.md", context.sources().get(0).provenance());
    }

    @Test
    void rejectsTraversalUrisInvalidReferencesAndProtectedPathsBeforeOpeningProject() throws Exception {
        Path project = project("invalid-references");
        RecordingOpener opener = new RecordingOpener(Map.of());
        InitialContextResolver resolver = resolver(opener, 128, 256);

        assertReason(InitialContextException.Reason.DOCUMENT_PATH_ESCAPE,
                () -> resolver.resolve(project, new InitialContextRequest.Documents("../secret.md")));
        assertReason(InitialContextException.Reason.MALFORMED_DOCUMENT_REFERENCE,
                () -> resolver.resolve(project, new InitialContextRequest.Documents("file:///tmp/context.md")));
        assertReason(InitialContextException.Reason.MALFORMED_DOCUMENT_REFERENCE,
                () -> resolver.resolve(project, new InitialContextRequest.Documents("bad\0name.md")));
        assertReason(InitialContextException.Reason.DOCUMENT_PATH_DENIED,
                () -> resolver.resolve(
                        project, new InitialContextRequest.Documents(".camel-kit/config.properties")));
        assertReason(InitialContextException.Reason.DOCUMENT_PATH_DENIED,
                () -> resolver.resolve(project, new InitialContextRequest.Documents(".git/HEAD")));
        assertReason(InitialContextException.Reason.DOCUMENT_PATH_DENIED,
                () -> resolver.resolve(project, new InitialContextRequest.Documents(".ssh/id_ed25519")));
        assertReason(InitialContextException.Reason.DOCUMENT_PATH_DENIED,
                () -> resolver.resolve(project, new InitialContextRequest.Documents("/proc/version")));
        assertReason(
                InitialContextException.Reason.MALFORMED_DOCUMENT_REFERENCE,
                () -> resolver.resolve(
                        project,
                        new InitialContextRequest.Composite(
                                List.of(
                                        new DocumentReference("would-read.md"),
                                        new UserText("text"),
                                        new DocumentReference("https://example.test/context.md")))));
        assertEquals(0, opener.openCount);
        assertEquals(0, opener.readCount);
    }

    @Test
    void redactsOversizedAndDisplayUnsafeTypedDocumentReferences() throws Exception {
        Path project = project("redacted-invalid-references");
        RecordingOpener opener = new RecordingOpener(Map.of());
        InitialContextResolver resolver = resolver(opener, 128, 256);
        String oversizedSecret = "secret-" + "x".repeat(ShipTreePolicy.MAX_PATH_CHARACTERS);
        String unsafeSecret = "secret\nforged-log-entry.md";
        String uriSecret = "https://user:sentinel-password@example.test/context.md";

        InitialContextException oversized = assertThrows(
                InitialContextException.class,
                () -> resolver.resolve(
                        project, new InitialContextRequest.Documents(oversizedSecret)));
        InitialContextException unsafe = assertThrows(
                InitialContextException.class,
                () -> resolver.resolve(
                        project, new InitialContextRequest.Documents(unsafeSecret)));
        InitialContextException uri = assertThrows(
                InitialContextException.class,
                () -> resolver.resolve(
                        project, new InitialContextRequest.Documents(uriSecret)));
        Path unsafeProjectRoot = Files.createDirectory(
                temporaryDirectory.resolve("unsafe\nproject-root")).toAbsolutePath().normalize();
        InitialContextException unsafeRoot = assertThrows(
                InitialContextException.class,
                () -> resolver.resolve(unsafeProjectRoot, new InitialContextRequest.None()));

        assertEquals(InitialContextException.Reason.MALFORMED_DOCUMENT_REFERENCE, oversized.reason());
        assertEquals("<oversized>", oversized.input());
        assertNull(oversized.getCause());
        assertFalse(oversized.getMessage().contains("secret"));
        assertEquals(InitialContextException.Reason.MALFORMED_DOCUMENT_REFERENCE, unsafe.reason());
        assertEquals("<invalid-document-reference>", unsafe.input());
        assertNull(unsafe.getCause());
        assertFalse(unsafe.getMessage().contains("secret"));
        assertEquals(InitialContextException.Reason.MALFORMED_DOCUMENT_REFERENCE, uri.reason());
        assertEquals("<invalid-document-reference>", uri.input());
        assertNull(uri.getCause());
        assertFalse(uri.getMessage().contains("sentinel-password"));
        assertFalse(uri.toString().contains("sentinel-password"));
        assertEquals(InitialContextException.Reason.INVALID_PROJECT_ROOT, unsafeRoot.reason());
        assertEquals("<invalid-project-root>", unsafeRoot.input());
        assertFalse(unsafeRoot.toString().contains("unsafe\nproject-root"));
        assertEquals(0, opener.openCount);
        assertEquals(0, opener.readCount);
    }

    @Test
    void absoluteProjectSymlinkEscapeIsRejectedByHeldDescriptorRead() throws Exception {
        Path project = project("symlink-escape");
        Path outside = Files.createDirectory(temporaryDirectory.resolve("outside-symlink"));
        Files.writeString(outside.resolve("secret.md"), "secret");
        Files.createSymbolicLink(project.resolve("linked"), outside);
        InitialContextResolver resolver = new InitialContextResolver(policy());

        assertReason(
                InitialContextException.Reason.DOCUMENT_NOT_REGULAR,
                () -> resolver.resolve(
                        project,
                        new InitialContextRequest.Documents(
                                project.resolve("linked/secret.md").toString())));
    }

    @Test
    void rejectsMissingEmptyInvalidUtf8NulAndPerDocumentOverflow() throws Exception {
        Path project = project("invalid-content");
        RecordingOpener opener = new RecordingOpener(
                Map.of(
                        "empty.md", new byte[0],
                        "invalid.md", new byte[]{(byte) 0xc3, 0x28},
                        "nul.md", new byte[]{'a', 0, 'b'},
                        "large.md", bytes("12345")));
        opener.failures.put("missing.md", new NoSuchFileException("missing.md"));
        InitialContextResolver resolver = resolver(opener, 4, 32);

        assertReason(InitialContextException.Reason.DOCUMENT_MISSING,
                () -> resolver.resolve(project, new InitialContextRequest.Documents("missing.md")));
        assertReason(InitialContextException.Reason.DOCUMENT_EMPTY,
                () -> resolver.resolve(project, new InitialContextRequest.Documents("empty.md")));
        assertReason(InitialContextException.Reason.DOCUMENT_INVALID_UTF8,
                () -> resolver.resolve(project, new InitialContextRequest.Documents("invalid.md")));
        assertReason(InitialContextException.Reason.CONTENT_CONTAINS_NUL,
                () -> resolver.resolve(project, new InitialContextRequest.Documents("nul.md")));
        assertReason(InitialContextException.Reason.DOCUMENT_TOO_LARGE,
                () -> resolver.resolve(project, new InitialContextRequest.Documents("large.md")));
    }

    @Test
    void enforcesAggregateLimitIncludingTextAndReservedExternalBytes() throws Exception {
        Path project = project("aggregate");
        Path external = Files.writeString(temporaryDirectory.resolve("reserved.md"), "1234").toRealPath();
        RecordingOpener opener = new RecordingOpener(Map.of("project.md", bytes("3456")));
        InitialContextResolver resolver = resolver(opener, 8, 5);

        assertReason(
                InitialContextException.Reason.CONTEXT_TOO_LARGE,
                () -> resolver.resolve(
                        project,
                        new InitialContextRequest.Composite(
                                List.of(
                                        new UserText("12"), new DocumentReference("project.md")))));
        int readsAfterProjectAggregate = opener.readCount;
        assertReason(
                InitialContextException.Reason.CONTEXT_TOO_LARGE,
                () -> resolver.resolve(
                        project,
                        new InitialContextRequest.Composite(
                                List.of(
                                        new UserText("12"), new DocumentReference(external.toString())))));

        assertEquals(1, readsAfterProjectAggregate);
        assertEquals(readsAfterProjectAggregate, opener.readCount);
    }

    @Test
    void acceptsDocumentAndAggregateLimitsExactly() throws Exception {
        Path project = project("exact-limits");
        RecordingOpener opener = new RecordingOpener(Map.of("project.md", bytes("5678")));
        InitialContextResolver resolver = resolver(opener, 4, 8);

        InitialContext context = resolved(resolver.resolve(
                project,
                new InitialContextRequest.Composite(
                        List.of(new UserText("1234"), new DocumentReference("project.md")))));

        assertEquals(2, context.sources().size());
        assertEquals(8, context.sources().stream().mapToLong(InitialContext.Source::byteSize).sum());
        assertEquals(1, opener.readCount);
    }

    @Test
    void rejectsProjectHardLinksSymlinksAndSpecialFiles() throws Exception {
        InitialContextResolver resolver = new InitialContextResolver(policy());

        Path hardLinkProject = project("hard-link");
        Path original = Files.writeString(hardLinkProject.resolve("original.md"), "requirements");
        Path hardLink = Files.createLink(hardLinkProject.resolve("hard.md"), original);
        assertReason(InitialContextException.Reason.DOCUMENT_NOT_REGULAR,
                () -> resolver.resolve(
                        hardLinkProject, new InitialContextRequest.Documents(hardLink.getFileName().toString())));

        Path symlinkProject = project("symlink");
        Path target = Files.writeString(temporaryDirectory.resolve("symlink-target.md"), "requirements");
        Path symlink = Files.createSymbolicLink(symlinkProject.resolve("link.md"), target);
        assertReason(InitialContextException.Reason.DOCUMENT_NOT_REGULAR,
                () -> resolver.resolve(
                        symlinkProject, new InitialContextRequest.Documents(symlink.getFileName().toString())));

        Path socketProject = project("socket");
        Path socket = socketProject.resolve("context.socket");
        try (ServerSocketChannel server = ServerSocketChannel.open(StandardProtocolFamily.UNIX)) {
            server.bind(UnixDomainSocketAddress.of(socket));
            assertReason(InitialContextException.Reason.DOCUMENT_NOT_REGULAR,
                    () -> resolver.resolve(
                            socketProject, new InitialContextRequest.Documents(socket.getFileName().toString())));
        }
    }

    @Test
    void mapsSecureFailuresToDistinctStableReasons() throws Exception {
        Path project = project("failure-mapping");
        RecordingOpener opener = new RecordingOpener(Map.of());
        InitialContextResolver resolver = resolver(opener, 64, 128);

        opener.failures.put("unsafe.md", secure(ShipFilesystemException.UNSAFE_ENTRY));
        opener.failures.put("changed.md", secure(ShipFilesystemException.CONCURRENT_MUTATION));
        opener.failures.put("unsupported.md", secure(ShipFilesystemException.SECURE_FILESYSTEM_UNSUPPORTED));
        opener.failures.put("unreadable.md", new AccessDeniedException("unreadable.md"));
        opener.failures.put(
                "policy-drift.md",
                secure(
                        ShipFilesystemException.UNSAFE_ENTRY,
                        new InitialContextException(
                                InitialContextException.Reason.PROTECTED_ROOT_CHANGED,
                                "<protected-root>",
                                "Protected root changed")));

        assertReason(InitialContextException.Reason.DOCUMENT_NOT_REGULAR,
                () -> resolver.resolve(project, new InitialContextRequest.Documents("unsafe.md")));
        assertReason(InitialContextException.Reason.DOCUMENT_CHANGED,
                () -> resolver.resolve(project, new InitialContextRequest.Documents("changed.md")));
        assertReason(InitialContextException.Reason.SECURE_FILESYSTEM_UNSUPPORTED,
                () -> resolver.resolve(project, new InitialContextRequest.Documents("unsupported.md")));
        assertReason(InitialContextException.Reason.DOCUMENT_UNREADABLE,
                () -> resolver.resolve(project, new InitialContextRequest.Documents("unreadable.md")));
        assertReason(InitialContextException.Reason.PROTECTED_ROOT_CHANGED,
                () -> resolver.resolve(project, new InitialContextRequest.Documents("policy-drift.md")));
        assertEquals("document-changed", InitialContextException.Reason.DOCUMENT_CHANGED.id());
        assertSame(
                InitialContextException.Reason.DOCUMENT_CHANGED,
                InitialContextException.Reason.fromId("document-changed"));
    }

    @Test
    void resolvedContextHasStableOccurrenceIdentityAndNoHostPathTypes() throws Exception {
        Path project = project("worker-context");
        RecordingOpener opener = new RecordingOpener(Map.of("requirements.md", bytes("same")));
        InitialContextResolver resolver = resolver(opener, 128, 256);
        InitialContextRequest request = new InitialContextRequest.Documents(
                List.of(
                        new DocumentReference("requirements.md"),
                        new DocumentReference("requirements.md")));

        InitialContext first = resolved(resolver.resolve(project, request));
        InitialContext second = resolved(resolver.resolve(project, request));

        assertEquals(first.digest(), second.digest());
        assertEquals(first.sources().get(0).id() + "-2", first.sources().get(1).id());
        assertFalse(first.toString().contains(project.toString()));
        assertFalse(List.of(InitialContext.Source.class.getRecordComponents()).stream()
                .anyMatch(component -> component.getType() == Path.class || component.getType() == URI.class));
    }

    @Test
    void textIsStrictUtf8NulFreeAndEmptyTextRemainsDistinctFromNone() throws Exception {
        Path project = project("text");
        RecordingOpener opener = new RecordingOpener(Map.of());
        InitialContextResolver resolver = resolver(opener, 64, 128);

        InitialContext empty = resolved(resolver.resolve(project, new InitialContextRequest.Text("")));
        InitialContext none = resolved(resolver.resolve(project, new InitialContextRequest.None()));

        assertInstanceOf(InitialContext.Text.class, empty);
        assertNotEquals(none.digest(), empty.digest());
        assertReason(InitialContextException.Reason.CONTENT_CONTAINS_NUL,
                () -> resolver.resolve(project, new InitialContextRequest.Text("a\0b")));
        assertReason(InitialContextException.Reason.CONTENT_INVALID_UTF8,
                () -> resolver.resolve(project, new InitialContextRequest.Text("\ud800")));
        InitialContextResolver tiny = resolver(opener, 64, 5);
        assertReason(InitialContextException.Reason.CONTEXT_TOO_LARGE,
                () -> tiny.resolve(project, new InitialContextRequest.Text("123456\ud800")));
        assertEquals(0, opener.openCount);
        assertEquals(0, opener.readCount);
    }

    private InitialContextResolver resolver(
            RecordingOpener opener, int maxDocumentBytes, int maxTotalBytes)
            throws Exception {
        return new InitialContextResolver(policy(), maxDocumentBytes, maxTotalBytes, opener);
    }

    private ContextFilesystemPolicy policy() throws Exception {
        Path state = Files.createDirectories(temporaryDirectory.resolve("controller-state")).toRealPath();
        Path credentials = Files.createDirectories(temporaryDirectory.resolve("credentials")).toRealPath();
        return new ContextFilesystemPolicy(state, List.of(credentials));
    }

    private Path project(String name) throws IOException {
        return Files.createDirectory(temporaryDirectory.resolve("project-" + name)).toRealPath();
    }

    private static InitialContext resolved(ContextResolution resolution) {
        return assertInstanceOf(ContextResolution.Resolved.class, resolution).context();
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static ShipFilesystemException secure(String code) {
        return ShipFilesystemTestFixtures.failure(code);
    }

    private static ShipFilesystemException secure(String code, Throwable cause) {
        return ShipFilesystemTestFixtures.failure(code, cause);
    }

    private static void assertReason(
            InitialContextException.Reason reason, ThrowingOperation operation) {
        InitialContextException failure = assertThrows(InitialContextException.class, operation::run);
        assertEquals(reason, failure.reason());
    }

    @FunctionalInterface
    private interface ThrowingOperation {
        void run() throws Exception;
    }

    private static final class RecordingOpener implements InitialContextResolver.ProjectReaderOpener {
        private final Map<String, byte[]> documents;
        private final Map<String, IOException> failures = new HashMap<>();
        private final List<String> paths = new ArrayList<>();
        private int openCount;
        private int readCount;
        private int closeCount;

        private RecordingOpener(Map<String, byte[]> documents) {
            this.documents = documents;
        }

        @Override
        public InitialContextResolver.ProjectReader open(ProjectAccess access) {
            openCount++;
            return new InitialContextResolver.ProjectReader() {
                @Override
                public byte[] readDocument(String relativePath, int maximumBytes) throws IOException {
                    readCount++;
                    paths.add(relativePath);
                    IOException failure = failures.get(relativePath);
                    if (failure != null) {
                        throw failure;
                    }
                    byte[] bytes = documents.get(relativePath);
                    if (bytes == null) {
                        throw new NoSuchFileException(relativePath);
                    }
                    if (bytes.length > maximumBytes) {
                        throw secure(ShipFilesystemException.TREE_QUOTA_EXCEEDED);
                    }
                    return bytes.clone();
                }

                @Override
                public void close() {
                    closeCount++;
                }
            };
        }
    }
}
