package io.github.luigidemasi.camelkit.ship.context;

import java.util.Objects;

/** A deterministic failure while classifying or resolving initial Ship context. */
public final class InitialContextException extends Exception {

    private final Reason reason;
    private final String input;

    InitialContextException(Reason reason, String input, String message) {
        super(message);
        this.reason = Objects.requireNonNull(reason, "reason");
        this.input = Objects.requireNonNull(input, "input");
    }

    InitialContextException(Reason reason, String input, String message, Throwable cause) {
        super(message, cause);
        this.reason = Objects.requireNonNull(reason, "reason");
        this.input = Objects.requireNonNull(input, "input");
    }

    public Reason reason() {
        return reason;
    }

    public String input() {
        return input;
    }

    public enum Reason {
        INVALID_PROJECT_ROOT("invalid-project-root"),
        INVALID_POLICY_CONFIGURATION("invalid-policy-configuration"),
        PROTECTED_ROOT_CHANGED("protected-root-changed"),
        MALFORMED_DOCUMENT_REFERENCE("malformed-document-reference"),
        DOCUMENT_PATH_ESCAPE("document-path-escape"),
        DOCUMENT_PATH_DENIED("document-path-denied"),
        DOCUMENT_MISSING("document-missing"),
        DOCUMENT_NOT_REGULAR("document-not-regular"),
        DOCUMENT_EMPTY("document-empty"),
        DOCUMENT_UNREADABLE("document-unreadable"),
        DOCUMENT_INVALID_UTF8("document-invalid-utf8"),
        CONTENT_INVALID_UTF8("content-invalid-utf8"),
        DOCUMENT_IDENTITY_UNAVAILABLE("document-identity-unavailable"),
        DOCUMENT_CHANGED("document-changed"),
        CONTENT_CONTAINS_NUL("content-contains-nul"),
        DOCUMENT_TOO_LARGE("document-too-large"),
        CONTEXT_TOO_LARGE("context-too-large"),
        SECURE_FILESYSTEM_UNSUPPORTED("secure-filesystem-unsupported");

        private final String id;

        Reason(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static Reason fromId(String id) {
            for (Reason reason : values()) {
                if (reason.id.equals(id)) {
                    return reason;
                }
            }
            throw new IllegalArgumentException("Unknown initial-context failure reason: " + id);
        }
    }
}
