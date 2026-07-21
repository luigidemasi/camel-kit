package io.github.luigidemasi.camelkit.ship.protocol;

/** A staged worker output. The controller computes and verifies its content digest. */
public record ProducedArtifact(String kind, String relativePath, String digest, long size) {
}
