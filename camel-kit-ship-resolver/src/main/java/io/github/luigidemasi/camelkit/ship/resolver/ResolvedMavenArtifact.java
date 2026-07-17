package io.github.luigidemasi.camelkit.ship.resolver;

import java.nio.file.Path;
import java.util.Objects;

/** Resolved artifact returned across the isolated resolver boundary. */
public record ResolvedMavenArtifact(MavenCoordinate coordinate, Path path) {

    public ResolvedMavenArtifact {
        Objects.requireNonNull(coordinate, "coordinate must not be null");
        path = Objects.requireNonNull(path, "path must not be null").toAbsolutePath().normalize();
    }
}
