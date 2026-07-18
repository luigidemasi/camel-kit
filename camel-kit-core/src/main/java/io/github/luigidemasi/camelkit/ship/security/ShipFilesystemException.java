package io.github.luigidemasi.camelkit.ship.security;

import java.io.IOException;

/** Stable fail-closed errors raised at the Ship filesystem trust boundary. */
public final class ShipFilesystemException extends IOException {

    public static final String SECURE_FILESYSTEM_UNSUPPORTED = "secure-filesystem-unsupported";
    public static final String UNSAFE_ENTRY = "unsafe-filesystem-entry";
    public static final String TREE_QUOTA_EXCEEDED = "tree-quota-exceeded";
    public static final String CONCURRENT_MUTATION = "concurrent-filesystem-mutation";

    private final String code;

    ShipFilesystemException(String code, String message) {
        super(message);
        this.code = requireCode(code);
    }

    ShipFilesystemException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = requireCode(code);
    }

    public String code() {
        return code;
    }

    private static String requireCode(String value) {
        if (value == null || !value.matches("[a-z][a-z0-9-]*")) {
            throw new IllegalArgumentException("Invalid Ship filesystem error code: " + value);
        }
        return value;
    }
}
