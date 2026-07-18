package io.github.luigidemasi.camelkit.ship.context;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import io.github.luigidemasi.camelkit.ship.security.ShipTreePolicy;

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
        return safePathDiagnostic(reference, "<invalid-document-reference>");
    }

    static String safePathDiagnostic(String value, String invalidToken) {
        Objects.requireNonNull(invalidToken, "invalid diagnostic token");
        if (value == null) {
            return "<null>";
        }
        if (value.length() > ShipTreePolicy.MAX_PATH_CHARACTERS) {
            return "<oversized>";
        }
        if (value.isBlank()) {
            return invalidToken;
        }
        for (int offset = 0; offset < value.length();) {
            char current = value.charAt(offset);
            if (Character.isHighSurrogate(current)) {
                if (offset + 1 >= value.length()
                        || !Character.isLowSurrogate(value.charAt(offset + 1))) {
                    return invalidToken;
                }
            } else if (Character.isLowSurrogate(current)) {
                return invalidToken;
            }
            int codePoint = value.codePointAt(offset);
            int type = Character.getType(codePoint);
            if (Character.isISOControl(codePoint)
                    || type == Character.FORMAT
                    || type == Character.LINE_SEPARATOR
                    || type == Character.PARAGRAPH_SEPARATOR) {
                return invalidToken;
            }
            offset += Character.charCount(codePoint);
        }
        return value;
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
