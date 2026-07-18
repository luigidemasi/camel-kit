package io.github.luigidemasi.camelkit.ship.context;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.github.luigidemasi.camelkit.ship.security.ShipTreePolicy;

/** Closed, typed initial-context input accepted before filesystem resolution. */
public sealed interface InitialContextRequest
        permits InitialContextRequest.None,
        InitialContextRequest.Text,
        InitialContextRequest.Documents,
        InitialContextRequest.Composite {

    List<? extends Source> sources();

    /**
     * Parses the optional compatibility spelling used by command adapters. Typed adapters should construct the variants
     * directly. {@code @path} is a document and {@code @@text} is literal text beginning with {@code @}.
     */
    static InitialContextRequest fromConvenienceTokens(List<String> tokens)
            throws InitialContextException {
        List<String> input;
        try {
            input = ContextModelSupport.boundedCopy(tokens, "context tokens");
        } catch (ContextModelSupport.SourceLimitException e) {
            throw new InitialContextException(
                    InitialContextException.Reason.CONTEXT_TOO_LARGE,
                    ">" + InitialContext.MAX_SOURCES,
                    "Ship context exceeds the " + InitialContext.MAX_SOURCES + " source limit",
                    e);
        }
        if (input.isEmpty()) {
            return new None();
        }

        List<Source> sources = new ArrayList<>(input.size());
        for (String token : input) {
            if (token.startsWith("@@")) {
                requireBoundedTextToken(token, InitialContext.MAX_TOTAL_BYTES + 1);
                sources.add(new UserText(token.substring(1)));
            } else if (token.startsWith("@")) {
                if (token.length() > ShipTreePolicy.MAX_PATH_CHARACTERS + 1) {
                    throw new InitialContextException(
                            InitialContextException.Reason.MALFORMED_DOCUMENT_REFERENCE,
                            "<oversized>",
                            "Document reference exceeds the path-character limit");
                }
                String reference = token.substring(1);
                if (reference.isBlank()) {
                    throw new InitialContextException(
                            InitialContextException.Reason.MALFORMED_DOCUMENT_REFERENCE,
                            ContextModelSupport.safeDocumentReferenceDiagnostic(reference),
                            "Document reference must contain a path after '@'");
                }
                sources.add(new DocumentReference(reference));
            } else {
                requireBoundedTextToken(token, InitialContext.MAX_TOTAL_BYTES);
                sources.add(new UserText(token));
            }
        }
        return typed(sources);
    }

    private static InitialContextRequest typed(List<Source> sources) {
        boolean hasText = sources.stream().anyMatch(UserText.class::isInstance);
        boolean hasDocuments = sources.stream().anyMatch(DocumentReference.class::isInstance);
        if (hasText && hasDocuments) {
            return new Composite(sources);
        }
        if (hasDocuments) {
            return new Documents(sources.stream().map(DocumentReference.class::cast).toList());
        }
        return new Text(sources.stream().map(UserText.class::cast).toList());
    }

    private static void requireBoundedTextToken(String token, int maximumBytes)
            throws InitialContextException {
        try {
            ContextModelSupport.strictUtf8Length(token, maximumBytes, "context token");
        } catch (ContextModelSupport.StrictUtf8Exception e) {
            InitialContextException.Reason reason
                    = e.failure() == ContextModelSupport.Utf8Failure.MALFORMED_SCALAR
                            ? InitialContextException.Reason.CONTENT_INVALID_UTF8
                            : InitialContextException.Reason.CONTEXT_TOO_LARGE;
            throw new InitialContextException(
                    reason,
                    "<redacted>",
                    reason == InitialContextException.Reason.CONTENT_INVALID_UTF8
                            ? "Text context is not an exact Unicode scalar sequence"
                            : "Text context exceeds the aggregate byte limit",
                    e);
        }
    }

    sealed interface Source permits UserText, DocumentReference {
    }

    record UserText(String value) implements Source {
        public UserText {
            Objects.requireNonNull(value, "text value");
        }

        @Override
        public String toString() {
            return "UserText[value=<redacted>]";
        }
    }

    record DocumentReference(String value) implements Source {
        public DocumentReference {
            Objects.requireNonNull(value, "document reference");
        }

        @Override
        public String toString() {
            return "DocumentReference[value=<redacted>]";
        }
    }

    record None() implements InitialContextRequest {
        @Override
        public List<Source> sources() {
            return List.of();
        }
    }

    record Text(List<UserText> sources) implements InitialContextRequest {
        public Text(String exactText) {
            this(List.of(new UserText(exactText)));
        }

        public Text {
            sources = immutableNonEmpty(sources, UserText.class, "text");
        }
    }

    record Documents(List<DocumentReference> sources) implements InitialContextRequest {
        public Documents(String reference) {
            this(List.of(new DocumentReference(reference)));
        }

        public Documents {
            sources = immutableNonEmpty(sources, DocumentReference.class, "document");
        }
    }

    record Composite(List<Source> sources) implements InitialContextRequest {
        public Composite {
            sources = ContextModelSupport.boundedCopy(sources, "composite sources");
            requireSourceCount(sources.size());
            if (sources.stream().noneMatch(UserText.class::isInstance)
                    || sources.stream().noneMatch(DocumentReference.class::isInstance)) {
                throw new IllegalArgumentException(
                        "Composite context requires at least one text and one document source");
            }
        }
    }

    private static <T extends Source> List<T> immutableNonEmpty(
            List<T> sources, Class<T> type, String label) {
        List<T> copy = ContextModelSupport.boundedCopy(sources, label + " sources");
        requireSourceCount(copy.size());
        if (copy.stream().anyMatch(source -> !type.isInstance(source))) {
            throw new IllegalArgumentException("Initial-context " + label + " source has the wrong type");
        }
        return copy;
    }

    private static void requireSourceCount(int count) {
        if (count < 1 || count > InitialContext.MAX_SOURCES) {
            throw new IllegalArgumentException(
                    "Initial-context source count must be between 1 and " + InitialContext.MAX_SOURCES);
        }
    }
}
