package io.github.luigidemasi.camelkit.ship.ledger;

/** Status of a requirements-ledger item. */
public enum LedgerStatus {
    OPEN,
    NEEDS_USER_DECISION,
    RESOLVED,
    NOT_APPLICABLE,
    SUPERSEDED
}
