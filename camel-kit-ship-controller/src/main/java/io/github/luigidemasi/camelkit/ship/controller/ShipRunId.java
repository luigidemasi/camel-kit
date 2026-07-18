package io.github.luigidemasi.camelkit.ship.controller;

import java.util.Objects;
import java.util.UUID;

/** Opaque identity allocated by the Ship lifecycle authority. */
public final class ShipRunId {

    private final UUID value;

    private ShipRunId(UUID value) {
        this.value = Objects.requireNonNull(value, "value");
    }

    static ShipRunId create() {
        return new ShipRunId(UUID.randomUUID());
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof ShipRunId that && value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return "ship-" + value.toString().replace("-", "");
    }
}
