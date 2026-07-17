package io.github.luigidemasi.camelkit.ship.resolver;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * One direct Maven dependency and its transitive exclusions.
 *
 * <p>
 * Each root accepts at most {@value #MAX_EXCLUSIONS} unique exclusions.
 */
public record MavenDependencyRoot(
        MavenCoordinate coordinate,
        List<MavenDependencyExclusion> exclusions) {

    /** Fixed fail-closed ceiling for unique exclusions on one dependency root. */
    public static final int MAX_EXCLUSIONS = 16;

    public MavenDependencyRoot {
        Objects.requireNonNull(coordinate, "coordinate must not be null");
        if (!"jar".equals(coordinate.extension()) || !coordinate.classifier().isEmpty()) {
            throw new IllegalArgumentException("A dependency root must be an unclassified JAR");
        }
        exclusions = List.copyOf(Objects.requireNonNull(exclusions, "exclusions must not be null")).stream()
                .sorted(Comparator.comparing(MavenDependencyExclusion::groupId)
                        .thenComparing(MavenDependencyExclusion::artifactId))
                .toList();
        if (exclusions.size() > MAX_EXCLUSIONS || exclusions.stream().distinct().count() != exclusions.size()) {
            throw new IllegalArgumentException(
                    "A dependency root may contain at most " + MAX_EXCLUSIONS + " unique exclusions");
        }
    }

    public static MavenDependencyRoot jar(String groupId, String artifactId, String version) {
        return new MavenDependencyRoot(MavenCoordinate.jar(groupId, artifactId, version), List.of());
    }
}
