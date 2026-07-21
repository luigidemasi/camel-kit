package io.github.luigidemasi.camelkit.ship.controller;

/** Signals that a conditional append was based on a stale authenticated event head. */
final class ShipEventHeadMismatchException extends ShipEventStoreException {

    private final ShipEventHead expected;
    private final ShipEventHead actual;

    ShipEventHeadMismatchException(ShipEventHead expected, ShipEventHead actual) {
        super("Ship event head changed: expected sequence "
              + expected.sequence()
              + " with digest "
              + expected.eventDigest()
              + ", but found sequence "
              + actual.sequence()
              + " with digest "
              + actual.eventDigest());
        this.expected = expected;
        this.actual = actual;
    }

    ShipEventHead expected() {
        return expected;
    }

    ShipEventHead actual() {
        return actual;
    }
}
