package io.github.luigidemasi.camelkit.ship.protocol;

/** A bounded worker stage selected exclusively by the Ship controller. */
public enum ShipStage {
    DISCOVERY,
    DESIGN,
    PLAN,
    EXECUTE,
    VALIDATE,
    REVIEW
}
