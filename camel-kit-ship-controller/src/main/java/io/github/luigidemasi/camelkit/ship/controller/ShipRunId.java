package io.github.luigidemasi.camelkit.ship.controller;

import java.util.Objects;
import java.util.UUID;

/** Opaque identity allocated by the Ship lifecycle authority. */
public final class ShipRunId {

    private static final String STORAGE_PREFIX = "ship-";

    private final UUID value;

    private ShipRunId(UUID value) {
        this.value = Objects.requireNonNull(value, "value");
    }

    static ShipRunId create() {
        return new ShipRunId(UUID.randomUUID());
    }

    static ShipRunId fromStorageId(String storageId) {
        if (storageId == null || !storageId.matches("ship-[0-9a-f]{32}")) {
            throw new IllegalArgumentException("Invalid Ship run ID: " + storageId);
        }
        String compact = storageId.substring(STORAGE_PREFIX.length());
        String canonical = compact.substring(0, 8)
                           + "-" + compact.substring(8, 12)
                           + "-" + compact.substring(12, 16)
                           + "-" + compact.substring(16, 20)
                           + "-" + compact.substring(20);
        return new ShipRunId(UUID.fromString(canonical));
    }

    String storageId() {
        return STORAGE_PREFIX + value.toString().replace("-", "");
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
        return storageId();
    }
}
