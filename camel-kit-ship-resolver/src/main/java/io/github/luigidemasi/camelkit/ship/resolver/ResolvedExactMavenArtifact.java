package io.github.luigidemasi.camelkit.ship.resolver;

import java.nio.file.Path;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Descriptive exact-artifact identity reported by the resolver. This caller-constructible record is not a provenance or
 * lifecycle capability; consumers must verify the identity against the bytes they actually open.
 */
public record ResolvedExactMavenArtifact(
        MavenCoordinate coordinate,
        Path path,
        String contentSha256,
        long contentLength) {

    private static final Pattern SHA256 = Pattern.compile("sha256:[0-9a-f]{64}");

    public ResolvedExactMavenArtifact {
        Objects.requireNonNull(coordinate, "coordinate must not be null");
        path = Objects.requireNonNull(path, "path must not be null").toAbsolutePath().normalize();
        if (contentSha256 == null || !SHA256.matcher(contentSha256).matches()) {
            throw new IllegalArgumentException("Exact artifact requires a SHA-256 content identity");
        }
        if (contentLength <= 0) {
            throw new IllegalArgumentException("Exact artifact requires a positive content length");
        }
    }

    @Override
    public String toString() {
        return "ResolvedExactMavenArtifact[coordinate=" + coordinate
               + ", path=<redacted>, contentSha256=" + contentSha256
               + ", contentLength=" + contentLength + ']';
    }
}
