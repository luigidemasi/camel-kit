package io.github.luigidemasi.camelkit.ship;

/** Canonical byte limits shared by Ship artifact admission and isolated evidence launchers. */
public final class ShipArtifactLimits {

    public static final int MAX_ROUTE_YAML_BYTES = 2 * 1024 * 1024;
    public static final int MAX_CITRUS_YAML_BYTES = 2 * 1024 * 1024;

    private ShipArtifactLimits() {
    }
}
