package io.github.luigidemasi.camelkit.ship.controller;

import java.io.IOException;

/** Signals an invalid or unsafe authoritative Ship event store. */
class ShipEventStoreException extends IOException {

    ShipEventStoreException(String message) {
        super(message);
    }

    ShipEventStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
