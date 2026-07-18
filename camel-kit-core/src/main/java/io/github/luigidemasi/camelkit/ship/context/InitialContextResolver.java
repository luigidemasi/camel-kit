package io.github.luigidemasi.camelkit.ship.context;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import io.github.luigidemasi.camelkit.ship.context.ContextFilesystemPolicy.ExternalCandidateInspection;
import io.github.luigidemasi.camelkit.ship.context.ContextFilesystemPolicy.ProjectAccess;
import io.github.luigidemasi.camelkit.ship.context.InitialContextException.Reason;
import io.github.luigidemasi.camelkit.ship.context.InitialContextRequest.DocumentReference;
import io.github.luigidemasi.camelkit.ship.context.InitialContextRequest.UserText;
import io.github.luigidemasi.camelkit.ship.security.ProjectContextFiles;
import io.github.luigidemasi.camelkit.ship.security.ShipFilesystemException;
import io.github.luigidemasi.camelkit.ship.security.ShipTreePolicy;

/** Resolves typed initial-context requests without granting workers filesystem authority. */
public final class InitialContextResolver {

    private static final Pattern URI_SCHEME = Pattern.compile("[A-Za-z][A-Za-z0-9+.-]*:.*");

    private final ContextFilesystemPolicy filesystemPolicy;
    private final int maxDocumentBytes;
    private final int maxTotalBytes;
    private final ProjectReaderOpener projectReaderOpener;

    public InitialContextResolver(ContextFilesystemPolicy filesystemPolicy) {
        this(
             filesystemPolicy,
             InitialContext.MAX_DOCUMENT_BYTES,
             InitialContext.MAX_TOTAL_BYTES,
             InitialContextResolver::openSecureProjectReader);
    }

    InitialContextResolver(
                           ContextFilesystemPolicy filesystemPolicy,
                           int maxDocumentBytes,
                           int maxTotalBytes,
                           ProjectReaderOpener projectReaderOpener) {
        this.filesystemPolicy = Objects.requireNonNull(filesystemPolicy, "filesystem policy");
        if (maxDocumentBytes < 1
                || maxDocumentBytes > InitialContext.MAX_DOCUMENT_BYTES
                || maxTotalBytes < 1
                || maxTotalBytes > InitialContext.MAX_TOTAL_BYTES) {
            throw new IllegalArgumentException("Resolver bounds must not exceed the fixed context limits");
        }
        this.maxDocumentBytes = maxDocumentBytes;
        this.maxTotalBytes = maxTotalBytes;
        this.projectReaderOpener = Objects.requireNonNull(projectReaderOpener, "project reader opener");
    }

    /**
     * Resolves an initial-context request through an admitted project root. A {@link ContextResolution.Pending Pending}
     * result is metadata-only: no document content, including project-document content, has been opened. This slice
     * cannot consume consent; the controller must retain the exact typed request independently for the later protected
     * consent-consumption and document-open boundary.
     *
     * @param  projectRoot             canonical project root
     * @param  request                 typed initial-context request
     * @return                         either worker-safe resolved context or descriptive pending consents
     * @throws InitialContextException if classification or secure resolution fails
     */
    public ContextResolution resolve(Path projectRoot, InitialContextRequest request)
            throws InitialContextException {
        Objects.requireNonNull(request, "initial context request");
        try (ProjectAccess access = filesystemPolicy.admitProject(projectRoot)) {
            return resolve(access, request);
        }
    }

    private ContextResolution resolve(ProjectAccess access, InitialContextRequest request)
            throws InitialContextException {
        Map<Integer, PlannedDocument> documents = classifyDocuments(access, request.sources());
        List<PlannedSource> plan = new ArrayList<>(request.sources().size());
        long reservedBytes = 0;
        for (int ordinal = 0; ordinal < request.sources().size(); ordinal++) {
            InitialContextRequest.Source source = request.sources().get(ordinal);
            if (source instanceof UserText text) {
                requireNoNul(text.value(), ordinal);
                int remaining = Math.toIntExact(maxTotalBytes - reservedBytes);
                int byteLength = textUtf8Length(text.value(), ordinal, remaining);
                reservedBytes = reserve(reservedBytes, byteLength);
                plan.add(new PlannedText(text.value()));
            } else {
                PlannedDocument document = documents.get(ordinal);
                if (document instanceof PlannedExternal external) {
                    long size = external.consent().candidateIdentity().byteSize();
                    if (size > maxDocumentBytes) {
                        throw failure(
                                Reason.DOCUMENT_TOO_LARGE,
                                external.consent().canonicalPath().toString(),
                                "External document exceeds the resolver document limit");
                    }
                    reservedBytes = reserve(reservedBytes, size);
                }
                plan.add(document);
            }
        }
        if (reservedBytes > maxTotalBytes) {
            throw failure(
                    Reason.CONTEXT_TOO_LARGE,
                    Long.toString(reservedBytes),
                    "Initial context exceeds the aggregate byte limit");
        }

        List<PendingDocumentConsent> pending = plan.stream()
                .filter(PlannedExternal.class::isInstance)
                .map(PlannedExternal.class::cast)
                .map(PlannedExternal::consent)
                .toList();
        if (!pending.isEmpty()) {
            return new ContextResolution.Pending(pending);
        }

        List<InitialContext.Source> resolvedSources = new ArrayList<>(plan.size());
        long projectBytes = 0;
        try (LazyProjectReader projectFiles = new LazyProjectReader(access, projectReaderOpener)) {
            for (PlannedSource source : plan) {
                if (source instanceof PlannedText text) {
                    resolvedSources.add(InitialContext.userText(text.value()));
                } else if (source instanceof PlannedProject project) {
                    long remaining = maxTotalBytes - reservedBytes - projectBytes;
                    if (remaining < 1) {
                        throw failure(
                                Reason.CONTEXT_TOO_LARGE,
                                project.relativePath(),
                                "No aggregate context capacity remains for the project document");
                    }
                    int readLimit = (int) Math.min(maxDocumentBytes, remaining);
                    byte[] bytes;
                    try {
                        bytes = projectFiles.readDocument(project.relativePath(), readLimit);
                    } catch (IOException e) {
                        throw mapReadFailure(project.relativePath(), e, remaining < maxDocumentBytes);
                    }
                    if (bytes.length == 0) {
                        throw failure(
                                Reason.DOCUMENT_EMPTY,
                                project.relativePath(),
                                "Project context document is empty");
                    }
                    if (bytes.length > maxDocumentBytes) {
                        throw failure(
                                Reason.DOCUMENT_TOO_LARGE,
                                project.relativePath(),
                                "Project context document exceeds the document byte limit");
                    }
                    if (bytes.length > remaining) {
                        throw failure(
                                Reason.CONTEXT_TOO_LARGE,
                                project.relativePath(),
                                "Project context document exceeds remaining aggregate capacity");
                    }
                    String content = decodeStrict(bytes, project.relativePath());
                    requireNoNul(content, project.relativePath());
                    projectBytes += bytes.length;
                    resolvedSources.add(InitialContext.projectDocument(project.relativePath(), content));
                }
            }
        } catch (IOException e) {
            throw mapReadFailure(access.canonicalRoot().toString(), e, false);
        }

        return new ContextResolution.Resolved(InitialContext.fromSources(resolvedSources));
    }

    private Map<Integer, PlannedDocument> classifyDocuments(
            ProjectAccess access, List<? extends InitialContextRequest.Source> sources)
            throws InitialContextException {
        Map<Integer, PlannedDocument> documents = new HashMap<>();
        for (int ordinal = 0; ordinal < sources.size(); ordinal++) {
            InitialContextRequest.Source source = sources.get(ordinal);
            if (source instanceof DocumentReference document) {
                documents.put(ordinal, classifyDocument(access, ordinal, document.value()));
            }
        }
        return documents;
    }

    private PlannedDocument classifyDocument(ProjectAccess access, int ordinal, String reference)
            throws InitialContextException {
        Path root = access.canonicalRoot();
        Path path = parseDocumentReference(reference);
        if (!path.isAbsolute()) {
            return new PlannedProject(access.requireContextPath(reference));
        }
        if (!path.normalize().equals(path)) {
            throw failure(
                    Reason.DOCUMENT_PATH_ESCAPE,
                    reference,
                    "Absolute document reference must be lexically normalized");
        }
        requireSafeAbsoluteComponents(path, reference);
        if (path.startsWith(root)) {
            String relative = root.relativize(path).toString().replace(path.getFileSystem().getSeparator(), "/");
            return new PlannedProject(access.requireContextPath(relative));
        }

        ExternalCandidateInspection candidate = filesystemPolicy.inspectExternal(path);
        PendingDocumentConsent consent = PendingDocumentConsent.create(
                ordinal, candidate.canonicalPath(), candidate.candidateIdentity());
        return new PlannedExternal(consent);
    }

    private static Path parseDocumentReference(String reference) throws InitialContextException {
        boolean rejectedUri = reference != null
                && reference.length() <= ShipTreePolicy.MAX_PATH_CHARACTERS
                && URI_SCHEME.matcher(reference).matches();
        String diagnosticInput = rejectedUri
                ? "<invalid-document-reference>"
                : ContextModelSupport.safeDocumentReferenceDiagnostic(reference);
        if (reference == null
                || reference.length() > ShipTreePolicy.MAX_PATH_CHARACTERS
                || reference.isBlank()
                || reference.indexOf('\\') >= 0
                || rejectedUri
                || !diagnosticInput.equals(reference)) {
            throw failure(
                    Reason.MALFORMED_DOCUMENT_REFERENCE,
                    diagnosticInput,
                    "Document reference must be a plain safe filesystem path");
        }
        try {
            return Path.of(reference);
        } catch (InvalidPathException e) {
            throw failure(
                    Reason.MALFORMED_DOCUMENT_REFERENCE,
                    diagnosticInput,
                    "Document reference is not a valid filesystem path",
                    e);
        }
    }

    private static void requireSafeAbsoluteComponents(Path path, String reference)
            throws InitialContextException {
        StringBuilder components = new StringBuilder();
        for (Path component : path) {
            if (!components.isEmpty()) {
                components.append('/');
            }
            components.append(component);
        }
        try {
            ShipTreePolicy.requireCanonicalRelativePath(components.toString());
        } catch (IllegalArgumentException e) {
            throw failure(
                    Reason.DOCUMENT_PATH_ESCAPE,
                    reference,
                    "Absolute document reference contains an unsafe path component",
                    e);
        }
    }

    private long reserve(long current, long additional) throws InitialContextException {
        try {
            long total = Math.addExact(current, additional);
            if (total > maxTotalBytes) {
                throw failure(
                        Reason.CONTEXT_TOO_LARGE,
                        Long.toString(total),
                        "Initial context exceeds the aggregate byte limit");
            }
            return total;
        } catch (ArithmeticException e) {
            throw failure(
                    Reason.CONTEXT_TOO_LARGE,
                    Long.toString(current),
                    "Initial context byte count overflowed",
                    e);
        }
    }

    private static int textUtf8Length(String value, int ordinal, int maximumBytes)
            throws InitialContextException {
        try {
            return ContextModelSupport.strictUtf8Length(
                    value, maximumBytes, "text-source-" + ordinal);
        } catch (ContextModelSupport.StrictUtf8Exception e) {
            if (e.failure() == ContextModelSupport.Utf8Failure.MALFORMED_SCALAR) {
                throw failure(
                        Reason.CONTENT_INVALID_UTF8,
                        "text-source-" + ordinal,
                        "Text context is not an exact Unicode scalar sequence",
                        e);
            }
            throw failure(
                    Reason.CONTEXT_TOO_LARGE,
                    "text-source-" + ordinal,
                    "Text context exceeds the remaining aggregate byte limit",
                    e);
        }
    }

    private static String decodeStrict(byte[] bytes, String input) throws InitialContextException {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            throw failure(
                    Reason.DOCUMENT_INVALID_UTF8,
                    input,
                    "Project context document is not strict UTF-8",
                    e);
        }
    }

    private static void requireNoNul(String value, int ordinal) throws InitialContextException {
        requireNoNul(value, "text-source-" + ordinal);
    }

    private static void requireNoNul(String value, String input) throws InitialContextException {
        if (value.indexOf('\0') >= 0) {
            throw failure(
                    Reason.CONTENT_CONTAINS_NUL,
                    input,
                    "Initial context content must not contain NUL");
        }
    }

    private static InitialContextException mapReadFailure(
            String input, IOException failure, boolean aggregateBound) {
        if (failure instanceof NoSuchFileException || failure instanceof FileNotFoundException) {
            return failure(Reason.DOCUMENT_MISSING, input, "Project context document does not exist", failure);
        }
        if (failure instanceof AccessDeniedException) {
            return failure(Reason.DOCUMENT_UNREADABLE, input, "Project context document is unreadable", failure);
        }
        if (failure instanceof ShipFilesystemException secureFailure) {
            if (secureFailure.getCause() instanceof InitialContextException policyFailure) {
                return policyFailure;
            }
            return switch (secureFailure.code()) {
                case ShipFilesystemException.UNSAFE_ENTRY -> failure(
                        Reason.DOCUMENT_NOT_REGULAR,
                        input,
                        "Project context document is not a safe regular file",
                        failure);
                case ShipFilesystemException.TREE_QUOTA_EXCEEDED -> failure(
                        aggregateBound ? Reason.CONTEXT_TOO_LARGE : Reason.DOCUMENT_TOO_LARGE,
                        input,
                        "Project context document exceeds its active byte limit",
                        failure);
                case ShipFilesystemException.CONCURRENT_MUTATION -> failure(
                        Reason.DOCUMENT_CHANGED,
                        input,
                        "Project context document changed during secure access",
                        failure);
                case ShipFilesystemException.SECURE_FILESYSTEM_UNSUPPORTED -> failure(
                        Reason.SECURE_FILESYSTEM_UNSUPPORTED,
                        input,
                        "Project filesystem cannot provide secure descriptor-relative access",
                        failure);
                default -> failure(
                        Reason.DOCUMENT_UNREADABLE,
                        input,
                        "Project context document could not be read securely",
                        failure);
            };
        }
        return failure(
                Reason.DOCUMENT_UNREADABLE,
                input,
                "Project context document could not be read",
                failure);
    }

    private static ProjectReader openSecureProjectReader(ProjectAccess access) throws IOException {
        return new SecureProjectReader(ProjectContextFiles.open(access));
    }

    private static InitialContextException failure(Reason reason, String input, String message) {
        return new InitialContextException(reason, input, message);
    }

    private static InitialContextException failure(
            Reason reason, String input, String message, Throwable cause) {
        return new InitialContextException(reason, input, message, cause);
    }

    @FunctionalInterface
    interface ProjectReaderOpener {
        ProjectReader open(ProjectAccess access) throws IOException;
    }

    interface ProjectReader extends AutoCloseable {
        byte[] readDocument(String canonicalRelativePath, int maximumBytes) throws IOException;

        @Override
        void close() throws IOException;
    }

    private static final class SecureProjectReader implements ProjectReader {
        private final ProjectContextFiles files;

        private SecureProjectReader(ProjectContextFiles files) {
            this.files = files;
        }

        @Override
        public byte[] readDocument(String canonicalRelativePath, int maximumBytes) throws IOException {
            return files.readDocument(canonicalRelativePath, maximumBytes);
        }

        @Override
        public void close() throws IOException {
            files.close();
        }
    }

    private static final class LazyProjectReader implements AutoCloseable {
        private final ProjectAccess access;
        private final ProjectReaderOpener opener;
        private ProjectReader reader;

        private LazyProjectReader(ProjectAccess access, ProjectReaderOpener opener) {
            this.access = access;
            this.opener = opener;
        }

        private byte[] readDocument(String relativePath, int maximumBytes) throws IOException {
            if (reader == null) {
                reader = opener.open(access);
            }
            return reader.readDocument(relativePath, maximumBytes);
        }

        @Override
        public void close() throws IOException {
            if (reader != null) {
                reader.close();
            }
        }
    }

    private sealed interface PlannedSource permits PlannedText, PlannedDocument {
    }

    private sealed interface PlannedDocument extends PlannedSource
            permits PlannedProject, PlannedExternal {
    }

    private record PlannedText(String value) implements PlannedSource {
        @Override
        public String toString() {
            return "PlannedText[redacted]";
        }
    }

    private record PlannedProject(String relativePath) implements PlannedDocument {
    }

    private record PlannedExternal(PendingDocumentConsent consent) implements PlannedDocument {
    }
}
