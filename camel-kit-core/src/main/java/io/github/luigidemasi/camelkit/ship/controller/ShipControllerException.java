package io.github.luigidemasi.camelkit.ship.controller;

/** Actionable failure raised at the trusted Ship controller boundary. */
public final class ShipControllerException extends RuntimeException {

    private final String code;

    public ShipControllerException(String code, String message) {
        super(message);
        this.code = requireCode(code);
    }

    public ShipControllerException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = requireCode(code);
    }

    public String code() {
        return code;
    }

    private static String requireCode(String value) {
        if (value == null || !value.matches("[a-z][a-z0-9-]*")) {
            throw new IllegalArgumentException("Invalid Ship controller error code: " + value);
        }
        return value;
    }
}
