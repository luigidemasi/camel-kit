package io.github.luigidemasi.camelkit.ship.security;

/** Test-only factory for package-private filesystem failures. */
public final class ShipFilesystemTestFixtures {

    private ShipFilesystemTestFixtures() {
    }

    public static ShipFilesystemException failure(String code) {
        return new ShipFilesystemException(code, code);
    }

    public static ShipFilesystemException failure(String code, Throwable cause) {
        return new ShipFilesystemException(code, code, cause);
    }
}
