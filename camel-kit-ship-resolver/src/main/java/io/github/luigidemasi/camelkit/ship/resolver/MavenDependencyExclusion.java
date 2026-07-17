package io.github.luigidemasi.camelkit.ship.resolver;

import java.util.Objects;
import java.util.regex.Pattern;

/** Exact or wildcard group/artifact exclusion on one direct dependency root. */
public record MavenDependencyExclusion(String groupId, String artifactId) {

    private static final Pattern SAFE_PART = Pattern.compile("[A-Za-z0-9_.-]+");

    public MavenDependencyExclusion {
        groupId = requirePattern(groupId, "groupId");
        artifactId = requirePattern(artifactId, "artifactId");
    }

    private static String requirePattern(String value, String label) {
        Objects.requireNonNull(value, label + " must not be null");
        if (!"*".equals(value)
                && (!SAFE_PART.matcher(value).matches()
                        || value.startsWith(".") || value.endsWith(".") || value.contains(".."))) {
            throw new IllegalArgumentException("Unsafe Maven dependency exclusion " + label);
        }
        return value;
    }
}
