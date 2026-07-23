package io.github.luigidemasi.camelkit.ship.context;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.github.luigidemasi.camelkit.ship.ShipDigest;

/** Ordered, content-addressed initial input for one local Ship run. */
public record ShipContext(List<Source> sources, String digest) {

    public static final int MAX_SOURCES = 256;
    public static final int MAX_DOCUMENT_BYTES = 4 * 1024 * 1024;
    public static final int MAX_TOTAL_BYTES = 8 * 1024 * 1024;

    private static final String DIGEST_VERSION = "camel-kit.ship.context.v1";

    public ShipContext {
        sources = List.copyOf(Objects.requireNonNull(sources, "context sources"));
        if (sources.size() > MAX_SOURCES) {
            throw new IllegalArgumentException("Ship context exceeds the source-count limit");
        }
        long total = 0;
        for (Source source : sources) {
            Objects.requireNonNull(source, "context source");
            total += source.byteCount();
            if (total > MAX_TOTAL_BYTES) {
                throw new IllegalArgumentException("Ship context exceeds the aggregate-byte limit");
            }
        }
        if (!ShipDigest.isSha256(digest) || !aggregateDigest(sources).equals(digest)) {
            throw new IllegalArgumentException("Ship context digest does not match its ordered sources");
        }
    }

    /** Returns the stable representation of a bare invocation. */
    public static ShipContext none() {
        return fromSources(List.of());
    }

    /**
     * Resolves ordered text and document input. Relative documents are resolved from {@code projectRoot}; recorded
     * document paths are normalized and absolute.
     */
    public static ShipContext resolve(Path projectRoot, List<? extends Input> inputs)
            throws Failure {
        Objects.requireNonNull(projectRoot, "project root");
        Objects.requireNonNull(inputs, "context inputs");
        if (inputs.size() > MAX_SOURCES) {
            throw failure(
                    Failure.Code.CONTEXT_TOO_LARGE,
                    "sources",
                    "Ship context exceeds the " + MAX_SOURCES + "-source limit");
        }

        Path root;
        try {
            root = projectRoot.toAbsolutePath().normalize();
        } catch (SecurityException e) {
            throw failure(
                    Failure.Code.UNREADABLE,
                    "project-root",
                    "Ship project root cannot be accessed",
                    e);
        }

        List<Source> resolved = new ArrayList<>(inputs.size());
        long total = 0;
        for (int ordinal = 0; ordinal < inputs.size(); ordinal++) {
            Input input = inputs.get(ordinal);
            if (input == null) {
                throw failure(
                        Failure.Code.MALFORMED,
                        "source-" + ordinal,
                        "Ship context contains a null source");
            }
            Source source = input instanceof TextInput text
                    ? resolveText(text, ordinal)
                    : resolveDocument(root, (DocumentInput) input, ordinal);
            total = addToTotal(total, source.byteCount(), sourceLabel(source, ordinal));
            resolved.add(source);
        }
        return fromSources(resolved);
    }

    /** Re-reads every recorded document and returns context with current source and aggregate digests. */
    public ShipContext refresh() throws Failure {
        List<Source> refreshed = new ArrayList<>(sources.size());
        long total = 0;
        for (int ordinal = 0; ordinal < sources.size(); ordinal++) {
            Source existing = sources.get(ordinal);
            Source source;
            if (existing.kind() == Kind.TEXT) {
                source = existing;
            } else {
                Path path;
                try {
                    path = Path.of(existing.value());
                } catch (InvalidPathException e) {
                    throw failure(
                            Failure.Code.MALFORMED,
                            "document-" + ordinal,
                            "Recorded Ship document path is malformed",
                            e);
                }
                source = documentSource(path);
            }
            total = addToTotal(total, source.byteCount(), sourceLabel(source, ordinal));
            refreshed.add(source);
        }
        return fromSources(refreshed);
    }

    public sealed interface Input permits TextInput, DocumentInput {
    }

    /** Exact inline text. Empty text is valid and remains distinct from absent context. */
    public record TextInput(String text) implements Input {
        public TextInput {
            Objects.requireNonNull(text, "context text");
        }

        @Override
        public String toString() {
            return "TextInput[text=<redacted>]";
        }
    }

    /** A document path, absolute or relative to the project root supplied to {@link #resolve}. */
    public record DocumentInput(String path) implements Input {
        public DocumentInput {
            Objects.requireNonNull(path, "document path");
        }

        public DocumentInput(Path path) {
            this(Objects.requireNonNull(path, "document path").toString());
        }
    }

    public enum Kind {
        TEXT,
        DOCUMENT
    }

    /**
     * Durable source identity. {@link #value} is exact inline text for {@link Kind#TEXT} and a normalized absolute path
     * for {@link Kind#DOCUMENT}; document content is not persisted.
     */
    public record Source(Kind kind, String value, long byteCount, String digest) {
        public Source {
            Objects.requireNonNull(kind, "source kind");
            Objects.requireNonNull(value, "source value");
            if (!ShipDigest.isSha256(digest)) {
                throw new IllegalArgumentException("Ship context source digest must be SHA-256");
            }
            if (kind == Kind.TEXT) {
                if (value.indexOf('\0') >= 0 || value.length() > MAX_TOTAL_BYTES) {
                    throw new IllegalArgumentException("Ship text source is malformed or too large");
                }
                byte[] bytes;
                try {
                    bytes = strictUtf8(value);
                } catch (CharacterCodingException e) {
                    throw new IllegalArgumentException("Ship text source is not strict UTF-8", e);
                }
                if (bytes.length > MAX_TOTAL_BYTES
                        || byteCount != bytes.length
                        || !digest.equals(ShipDigest.sha256(bytes))) {
                    throw new IllegalArgumentException("Ship text source identity does not match its content");
                }
            } else {
                Path path;
                try {
                    path = Path.of(value);
                } catch (InvalidPathException e) {
                    throw new IllegalArgumentException("Ship document source path is malformed", e);
                }
                if (!path.isAbsolute()
                        || !path.normalize().equals(path)
                        || !path.toString().equals(value)) {
                    throw new IllegalArgumentException(
                            "Ship document source path must be normalized and absolute");
                }
                if (byteCount < 1 || byteCount > MAX_DOCUMENT_BYTES) {
                    throw new IllegalArgumentException(
                            "Ship document source byte count is outside its bounds");
                }
            }
        }

        @Override
        public String toString() {
            return "Source[kind=" + kind
                   + ", value=" + (kind == Kind.TEXT ? "<redacted>" : value)
                   + ", byteCount=" + byteCount
                   + ", digest=" + digest + ']';
        }
    }

    /** An actionable failure while resolving or refreshing context. */
    public static final class Failure extends Exception {

        private static final long serialVersionUID = 1L;

        private final Code code;
        private final String input;

        private Failure(Code code, String input, String message) {
            super(message);
            this.code = Objects.requireNonNull(code, "failure code");
            this.input = Objects.requireNonNull(input, "failure input");
        }

        private Failure(Code code, String input, String message, Throwable cause) {
            super(message, cause);
            this.code = Objects.requireNonNull(code, "failure code");
            this.input = Objects.requireNonNull(input, "failure input");
        }

        public Code code() {
            return code;
        }

        public String input() {
            return input;
        }

        public enum Code {
            MISSING,
            EMPTY,
            MALFORMED,
            UNREADABLE,
            DOCUMENT_TOO_LARGE,
            CONTEXT_TOO_LARGE
        }
    }

    private static Source resolveText(TextInput input, int ordinal) throws Failure {
        String text = input.text();
        if (text.indexOf('\0') >= 0) {
            throw failure(
                    Failure.Code.MALFORMED,
                    "text-" + ordinal,
                    "Ship text context must not contain NUL");
        }
        if (text.length() > MAX_TOTAL_BYTES) {
            throw contextTooLarge("text-" + ordinal);
        }
        byte[] bytes;
        try {
            bytes = strictUtf8(text);
        } catch (CharacterCodingException e) {
            throw failure(
                    Failure.Code.MALFORMED,
                    "text-" + ordinal,
                    "Ship text context is not strict UTF-8",
                    e);
        }
        if (bytes.length > MAX_TOTAL_BYTES) {
            throw contextTooLarge("text-" + ordinal);
        }
        return new Source(Kind.TEXT, text, bytes.length, ShipDigest.sha256(bytes));
    }

    private static Source resolveDocument(Path root, DocumentInput input, int ordinal)
            throws Failure {
        String value = input.path();
        if (value.isBlank() || value.indexOf('\0') >= 0) {
            throw failure(
                    Failure.Code.MALFORMED,
                    "document-" + ordinal,
                    "Ship document path is malformed");
        }
        Path supplied;
        try {
            supplied = Path.of(value);
        } catch (InvalidPathException e) {
            throw failure(
                    Failure.Code.MALFORMED,
                    "document-" + ordinal,
                    "Ship document path is malformed",
                    e);
        }
        Path path;
        try {
            path = (supplied.isAbsolute() ? supplied : root.resolve(supplied))
                    .toAbsolutePath()
                    .normalize();
        } catch (SecurityException e) {
            throw failure(
                    Failure.Code.UNREADABLE,
                    "document-" + ordinal,
                    "Ship document path cannot be accessed",
                    e);
        }
        return documentSource(path);
    }

    private static Source documentSource(Path path) throws Failure {
        byte[] bytes = readDocument(path);
        return new Source(
                Kind.DOCUMENT, path.toString(), bytes.length, ShipDigest.sha256(bytes));
    }

    private static byte[] readDocument(Path path) throws Failure {
        String input = path.toString();
        try {
            BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
            if (!attributes.isRegularFile()) {
                throw failure(
                        Failure.Code.MALFORMED,
                        input,
                        "Ship document must be a regular file: " + input);
            }
            if (!Files.isReadable(path)) {
                throw failure(
                        Failure.Code.UNREADABLE,
                        input,
                        "Ship document is not readable: " + input);
            }
            if (attributes.size() > MAX_DOCUMENT_BYTES) {
                throw documentTooLarge(input);
            }

            byte[] bytes;
            try (InputStream stream = Files.newInputStream(path)) {
                bytes = stream.readNBytes(MAX_DOCUMENT_BYTES + 1);
            }
            if (bytes.length == 0) {
                throw failure(
                        Failure.Code.EMPTY,
                        input,
                        "Ship document is empty: " + input);
            }
            if (bytes.length > MAX_DOCUMENT_BYTES) {
                throw documentTooLarge(input);
            }
            for (byte value : bytes) {
                if (value == 0) {
                    throw failure(
                            Failure.Code.MALFORMED,
                            input,
                            "Ship document must not contain NUL: " + input);
                }
            }
            try {
                StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes));
            } catch (CharacterCodingException e) {
                throw failure(
                        Failure.Code.MALFORMED,
                        input,
                        "Ship document is not strict UTF-8: " + input,
                        e);
            }
            return bytes;
        } catch (Failure e) {
            throw e;
        } catch (NoSuchFileException | FileNotFoundException e) {
            throw failure(
                    Failure.Code.MISSING,
                    input,
                    "Ship document does not exist: " + input,
                    e);
        } catch (AccessDeniedException e) {
            throw failure(
                    Failure.Code.UNREADABLE,
                    input,
                    "Ship document is not readable: " + input,
                    e);
        } catch (IOException | SecurityException e) {
            throw failure(
                    Failure.Code.UNREADABLE,
                    input,
                    "Ship document could not be read: " + input,
                    e);
        }
    }

    private static long addToTotal(long current, long additional, String input)
            throws Failure {
        long total = current + additional;
        if (total > MAX_TOTAL_BYTES) {
            throw contextTooLarge(input);
        }
        return total;
    }

    private static String sourceLabel(Source source, int ordinal) {
        return source.kind() == Kind.TEXT ? "text-" + ordinal : source.value();
    }

    private static ShipContext fromSources(List<Source> sources) {
        List<Source> copy = List.copyOf(sources);
        return new ShipContext(copy, aggregateDigest(copy));
    }

    private static String aggregateDigest(List<Source> sources) {
        StringBuilder identity = new StringBuilder(DIGEST_VERSION);
        for (Source source : sources) {
            identity.append('\0').append(source.kind());
            identity.append('\0');
            if (source.kind() == Kind.DOCUMENT) {
                identity.append(source.value());
            }
            identity.append('\0').append(source.byteCount());
            identity.append('\0').append(source.digest());
        }
        return ShipDigest.sha256(identity.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] strictUtf8(String value) throws CharacterCodingException {
        ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .encode(CharBuffer.wrap(value));
        byte[] bytes = new byte[encoded.remaining()];
        encoded.get(bytes);
        return bytes;
    }

    private static Failure documentTooLarge(String input) {
        return failure(
                Failure.Code.DOCUMENT_TOO_LARGE,
                input,
                "Ship document exceeds the " + MAX_DOCUMENT_BYTES + "-byte limit: " + input);
    }

    private static Failure contextTooLarge(String input) {
        return failure(
                Failure.Code.CONTEXT_TOO_LARGE,
                input,
                "Ship context exceeds the " + MAX_TOTAL_BYTES + "-byte limit");
    }

    private static Failure failure(
            Failure.Code code, String input, String message) {
        return new Failure(code, input, message);
    }

    private static Failure failure(
            Failure.Code code, String input, String message, Throwable cause) {
        return new Failure(code, input, message, cause);
    }
}
