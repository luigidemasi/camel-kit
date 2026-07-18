package io.github.luigidemasi.camelkit.ship.context;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import io.github.luigidemasi.camelkit.ship.security.ShipTreePolicy;

/** Immutable, content-addressed context safe to expose to a Ship worker. */
public sealed interface InitialContext
        permits InitialContext.None,
        InitialContext.Text,
        InitialContext.Documents,
        InitialContext.Composite {

    int MAX_SOURCES = 256;
    int MAX_DOCUMENT_BYTES = 4 * 1024 * 1024;
    int MAX_TOTAL_BYTES = 8 * 1024 * 1024;
    int MAX_PROVENANCE_CHARACTERS = ShipTreePolicy.MAX_PATH_CHARACTERS + "project:".length();

    Kind kind();

    List<Source> sources();

    String digest();

    static InitialContext none() {
        return new None(digestFor(Kind.NONE, List.of()));
    }

    static InitialContext fromSources(List<Source> supplied) {
        List<Source> sources = canonicalOccurrences(supplied);
        boolean hasText = sources.stream().anyMatch(source -> source.kind() == SourceKind.USER_TEXT);
        boolean hasDocuments = sources.stream().anyMatch(source -> source.kind() == SourceKind.DOCUMENT);
        Kind kind = hasText && hasDocuments
                ? Kind.COMPOSITE
                : hasText ? Kind.TEXT : hasDocuments ? Kind.DOCUMENTS : Kind.NONE;
        String digest = digestFor(kind, sources);
        return switch (kind) {
            case NONE -> new None(digest);
            case TEXT -> new Text(sources, digest);
            case DOCUMENTS -> new Documents(sources, digest);
            case COMPOSITE -> new Composite(sources, digest);
        };
    }

    static Source userText(String content) {
        byte[] bytes = ContextModelSupport.encodeStrictUtf8(
                content, MAX_TOTAL_BYTES, "text content");
        String digest = sha256(bytes);
        String provenance = "context:user-text:" + digest.substring("sha256:".length());
        return source(SourceKind.USER_TEXT, provenance, content, bytes, digest);
    }

    static Source projectDocument(String relativePath, String content) {
        byte[] bytes = ContextModelSupport.encodeStrictUtf8(
                content, MAX_DOCUMENT_BYTES, "document content");
        String digest = sha256(bytes);
        return source(
                SourceKind.DOCUMENT,
                "project:" + ShipTreePolicy.requireCanonicalRelativePath(relativePath),
                content,
                bytes,
                digest);
    }

    private static Source source(
            SourceKind kind, String provenance, String content, byte[] bytes, String digest) {
        String id = sourceId(kind, provenance, digest);
        return new Source(id, kind, provenance, content, bytes.length, digest);
    }

    private static List<Source> canonicalOccurrences(List<Source> supplied) {
        List<Source> input = ContextModelSupport.boundedCopy(supplied, "context sources");
        List<Source> result = new ArrayList<>(input.size());
        Map<String, Integer> occurrences = new HashMap<>();
        for (Source source : input) {
            Objects.requireNonNull(source, "context source");
            String baseId = sourceId(source.kind(), source.provenance(), source.digest());
            int occurrence = occurrences.merge(baseId, 1, Integer::sum);
            String id = occurrence == 1 ? baseId : baseId + "-" + occurrence;
            result.add(new Source(
                    id,
                    source.kind(),
                    source.provenance(),
                    source.content(),
                    source.byteSize(),
                    source.digest()));
        }
        return List.copyOf(result);
    }

    private static void validate(Kind kind, List<Source> supplied, String digest) {
        Objects.requireNonNull(kind, "kind");
        List<Source> sources = Objects.requireNonNull(supplied, "sources");
        requireDigest(digest, "context digest");

        long total = 0;
        Map<String, Integer> occurrences = new HashMap<>();
        for (Source source : sources) {
            Objects.requireNonNull(source, "context source");
            String baseId = sourceId(source.kind(), source.provenance(), source.digest());
            int occurrence = occurrences.merge(baseId, 1, Integer::sum);
            String expectedId = occurrence == 1 ? baseId : baseId + "-" + occurrence;
            if (!expectedId.equals(source.id())) {
                throw new IllegalArgumentException("Initial context source IDs are not canonically ordered");
            }
            total = Math.addExact(total, source.byteSize());
            if (total > MAX_TOTAL_BYTES) {
                throw new IllegalArgumentException("Initial context exceeds the aggregate-byte limit");
            }
        }

        boolean hasText = sources.stream().anyMatch(source -> source.kind() == SourceKind.USER_TEXT);
        boolean hasDocuments = sources.stream().anyMatch(source -> source.kind() == SourceKind.DOCUMENT);
        Kind inferred = hasText && hasDocuments
                ? Kind.COMPOSITE
                : hasText ? Kind.TEXT : hasDocuments ? Kind.DOCUMENTS : Kind.NONE;
        if (kind != inferred) {
            throw new IllegalArgumentException("Context kind does not match its sources");
        }
        if (!digest.equals(digestFor(kind, sources))) {
            throw new IllegalArgumentException("Context digest does not match its sources");
        }
    }

    private static String sourceId(SourceKind kind, String provenance, String digest) {
        return "source-" + digestFields(
                "camel-kit.ship.initial-context.source.v2",
                kind.id(),
                provenance,
                digest).substring("sha256:".length());
    }

    private static String digestFor(Kind kind, List<Source> sources) {
        List<String> fields = new ArrayList<>();
        fields.add("camel-kit.ship.initial-context.v2");
        fields.add(kind.id());
        for (Source source : sources) {
            fields.add(source.id());
            fields.add(source.kind().id());
            fields.add(source.provenance());
            fields.add(Long.toString(source.byteSize()));
            fields.add(source.digest());
        }
        return digestFields(fields.toArray(String[]::new));
    }

    private static String sha256(byte[] bytes) {
        return "sha256:" + HexFormat.of().formatHex(messageDigest().digest(bytes));
    }

    private static String digestFields(String... fields) {
        MessageDigest digest = messageDigest();
        for (String field : fields) {
            byte[] bytes = ContextModelSupport.encodeStrictUtf8(
                    field, MAX_TOTAL_BYTES, "digest field");
            digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
            digest.update(bytes);
        }
        return "sha256:" + HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest messageDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("The Java runtime does not provide SHA-256", e);
        }
    }

    private static void requireDigest(String value, String label) {
        if (value == null || !value.matches("sha256:[0-9a-f]{64}")) {
            throw new IllegalArgumentException(label + " must be a lowercase SHA-256 digest");
        }
    }

    enum Kind {
        NONE("none"),
        TEXT("text"),
        DOCUMENTS("documents"),
        COMPOSITE("composite");

        private final String id;

        Kind(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static Kind fromId(String id) {
            for (Kind kind : values()) {
                if (kind.id.equals(id)) {
                    return kind;
                }
            }
            throw new IllegalArgumentException("Unknown initial-context kind: " + id);
        }
    }

    enum SourceKind {
        USER_TEXT("user-text"),
        DOCUMENT("document");

        private final String id;

        SourceKind(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static SourceKind fromId(String id) {
            for (SourceKind kind : values()) {
                if (kind.id.equals(id)) {
                    return kind;
                }
            }
            throw new IllegalArgumentException("Unknown initial-context source kind: " + id);
        }
    }

    record Source(
            String id,
            SourceKind kind,
            String provenance,
            String content,
            long byteSize,
            String digest) {

        private static final Pattern ID = Pattern.compile(
                "source-[0-9a-f]{64}(?:-[1-9][0-9]{0,2})?");
        private static final int MAX_ID_CHARACTERS
                = "source-".length() + 64 + 1 + Integer.toString(MAX_SOURCES).length();

        public Source {
            if (id == null || id.length() > MAX_ID_CHARACTERS || !ID.matcher(id).matches()) {
                throw new IllegalArgumentException("Source ID must be canonical");
            }
            Objects.requireNonNull(kind, "source kind");
            Objects.requireNonNull(provenance, "source provenance");
            Objects.requireNonNull(content, "source content");
            requireDigest(digest, "source digest");
            if (provenance.length() > MAX_PROVENANCE_CHARACTERS
                    || provenance.indexOf('\0') >= 0) {
                throw new IllegalArgumentException("Source provenance is unsafe or too long");
            }
            ContextModelSupport.strictUtf8Length(
                    provenance, MAX_PROVENANCE_CHARACTERS * 4, "source provenance");

            byte[] bytes = ContextModelSupport.encodeStrictUtf8(
                    content,
                    kind == SourceKind.DOCUMENT ? MAX_DOCUMENT_BYTES : MAX_TOTAL_BYTES,
                    "source content");
            if (content.indexOf('\0') >= 0
                    || (kind == SourceKind.DOCUMENT && bytes.length == 0)) {
                throw new IllegalArgumentException("Source content is unsafe or exceeds its byte limit");
            }
            if (byteSize != bytes.length || !digest.equals(sha256(bytes))) {
                throw new IllegalArgumentException("Source content identity does not match its bytes");
            }
            if (kind == SourceKind.USER_TEXT) {
                String expected = "context:user-text:" + digest.substring("sha256:".length());
                if (!expected.equals(provenance)) {
                    throw new IllegalArgumentException("User-text provenance is not logical and content-bound");
                }
            } else {
                if (provenance.startsWith("project:")) {
                    ShipTreePolicy.requireCanonicalRelativePath(provenance.substring("project:".length()));
                } else {
                    throw new IllegalArgumentException("Resolved document provenance must be project-relative");
                }
            }

            String baseId = sourceId(kind, provenance, digest);
            if (!id.equals(baseId)) {
                String prefix = baseId + '-';
                String suffix = id.startsWith(prefix) ? id.substring(prefix.length()) : "";
                int occurrence;
                try {
                    occurrence = Integer.parseInt(suffix);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(
                            "Source ID does not match its logical identity", e);
                }
                if (occurrence < 2
                        || occurrence > MAX_SOURCES
                        || !Integer.toString(occurrence).equals(suffix)) {
                    throw new IllegalArgumentException(
                            "Source ID does not match its logical identity");
                }
            }
        }

        @Override
        public String toString() {
            return "Source[id=" + id
                   + ", kind=" + kind
                   + ", provenance=" + provenance
                   + ", byteSize=" + byteSize
                   + ", digest=" + digest + ']';
        }
    }

    record None(String digest) implements InitialContext {
        public None {
            validate(Kind.NONE, List.of(), digest);
        }

        @Override
        public Kind kind() {
            return Kind.NONE;
        }

        @Override
        public List<Source> sources() {
            return List.of();
        }
    }

    record Text(List<Source> sources, String digest) implements InitialContext {
        public Text {
            sources = ContextModelSupport.boundedCopy(sources, "sources");
            validate(Kind.TEXT, sources, digest);
        }

        @Override
        public Kind kind() {
            return Kind.TEXT;
        }
    }

    record Documents(List<Source> sources, String digest) implements InitialContext {
        public Documents {
            sources = ContextModelSupport.boundedCopy(sources, "sources");
            validate(Kind.DOCUMENTS, sources, digest);
        }

        @Override
        public Kind kind() {
            return Kind.DOCUMENTS;
        }
    }

    record Composite(List<Source> sources, String digest) implements InitialContext {
        public Composite {
            sources = ContextModelSupport.boundedCopy(sources, "sources");
            validate(Kind.COMPOSITE, sources, digest);
        }

        @Override
        public Kind kind() {
            return Kind.COMPOSITE;
        }
    }
}

/** Package-private validation shared by context inputs and the resolver boundary. */
final class ContextModelSupport {

    static final class SourceLimitException extends IllegalArgumentException {

        SourceLimitException(String label) {
            super(label + " exceed the " + InitialContext.MAX_SOURCES + " source limit");
        }
    }

    enum Utf8Failure {
        MALFORMED_SCALAR,
        BYTE_LIMIT_EXCEEDED
    }

    static final class StrictUtf8Exception extends IllegalArgumentException {

        private final Utf8Failure failure;

        StrictUtf8Exception(Utf8Failure failure, String message) {
            super(message);
            this.failure = failure;
        }

        Utf8Failure failure() {
            return failure;
        }
    }

    private ContextModelSupport() {
    }

    static <T> List<T> boundedCopy(List<? extends T> supplied, String label) {
        Objects.requireNonNull(supplied, label);
        List<T> copy = new ArrayList<>();
        Iterator<? extends T> iterator = supplied.iterator();
        while (iterator.hasNext()) {
            if (copy.size() == InitialContext.MAX_SOURCES) {
                throw new SourceLimitException(label);
            }
            copy.add(Objects.requireNonNull(iterator.next(), label + " entry"));
        }
        return List.copyOf(copy);
    }

    static String safeDocumentReferenceDiagnostic(String reference) {
        if (reference == null) {
            return "<null>";
        }
        if (reference.length() > ShipTreePolicy.MAX_PATH_CHARACTERS) {
            return "<oversized>";
        }
        if (reference.isBlank()) {
            return "<invalid-document-reference>";
        }
        for (int offset = 0; offset < reference.length();) {
            char current = reference.charAt(offset);
            if (Character.isHighSurrogate(current)) {
                if (offset + 1 >= reference.length()
                        || !Character.isLowSurrogate(reference.charAt(offset + 1))) {
                    return "<invalid-document-reference>";
                }
            } else if (Character.isLowSurrogate(current)) {
                return "<invalid-document-reference>";
            }
            int codePoint = reference.codePointAt(offset);
            int type = Character.getType(codePoint);
            if (Character.isISOControl(codePoint)
                    || type == Character.FORMAT
                    || type == Character.LINE_SEPARATOR
                    || type == Character.PARAGRAPH_SEPARATOR) {
                return "<invalid-document-reference>";
            }
            offset += Character.charCount(codePoint);
        }
        return reference;
    }

    static byte[] encodeStrictUtf8(String value, int maxBytes, String label) {
        strictUtf8Length(value, maxBytes, label);
        return value.getBytes(StandardCharsets.UTF_8);
    }

    static int strictUtf8Length(String value, int maxBytes, String label) {
        Objects.requireNonNull(value, label);
        if (maxBytes < 0) {
            throw new IllegalArgumentException("UTF-8 byte limit must not be negative");
        }
        int byteLength = 0;
        for (int offset = 0; offset < value.length();) {
            char first = value.charAt(offset);
            int width;
            if (first <= 0x7f) {
                width = 1;
            } else if (first <= 0x7ff) {
                width = 2;
            } else if (Character.isHighSurrogate(first)) {
                if (offset + 1 >= value.length()
                        || !Character.isLowSurrogate(value.charAt(offset + 1))) {
                    throw new StrictUtf8Exception(
                            Utf8Failure.MALFORMED_SCALAR,
                            label + " is not an exact Unicode scalar sequence");
                }
                width = 4;
                offset++;
            } else if (Character.isLowSurrogate(first)) {
                throw new StrictUtf8Exception(
                        Utf8Failure.MALFORMED_SCALAR,
                        label + " is not an exact Unicode scalar sequence");
            } else {
                width = 3;
            }
            if (byteLength > maxBytes - width) {
                throw new StrictUtf8Exception(
                        Utf8Failure.BYTE_LIMIT_EXCEEDED,
                        label + " exceeds its UTF-8 byte limit");
            }
            byteLength += width;
            offset++;
        }
        return byteLength;
    }
}
