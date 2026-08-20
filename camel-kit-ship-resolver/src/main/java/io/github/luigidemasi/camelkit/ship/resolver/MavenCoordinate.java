package io.github.luigidemasi.camelkit.ship.resolver;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** Immutable Maven artifact coordinate owned by Camel Kit. */
public record MavenCoordinate(
        String groupId,
        String artifactId,
        String extension,
        String classifier,
        String version) {

    private static final Pattern SAFE_PART = Pattern.compile("[A-Za-z0-9_.-]+");
    private static final Pattern TIMESTAMPED_SNAPSHOT = Pattern.compile(".*-\\d{8}\\.\\d{6}-\\d+$");

    public MavenCoordinate {
        groupId = requirePart(groupId, "groupId");
        artifactId = requirePart(artifactId, "artifactId");
        extension = requirePart(extension, "extension");
        classifier = classifier == null ? "" : classifier;
        if (!classifier.isEmpty()) {
            classifier = requirePart(classifier, "classifier");
        }
        version = requirePart(version, "version");
        if (!isImmutableRelease(version)) {
            throw new IllegalArgumentException("Maven coordinate requires an immutable release version");
        }
    }

    public static MavenCoordinate jar(String groupId, String artifactId, String version) {
        return new MavenCoordinate(groupId, artifactId, "jar", "", version);
    }

    public static MavenCoordinate of(
            String groupId, String artifactId, String version, String extension) {
        return new MavenCoordinate(groupId, artifactId, extension, "", version);
    }

    public MavenCoordinate withExtension(String value) {
        return new MavenCoordinate(groupId, artifactId, value, classifier, version);
    }

    public String gav() {
        return groupId + ':' + artifactId + ':' + version;
    }

    public String resolverString() {
        return groupId + ':' + artifactId + ':' + extension
               + (classifier.isEmpty() ? "" : ':' + classifier) + ':' + version;
    }

    public String fileName() {
        return artifactId + '-' + version + (classifier.isEmpty() ? "" : '-' + classifier) + '.' + extension;
    }

    private static String requirePart(String value, String label) {
        Objects.requireNonNull(value, label + " must not be null");
        if (value.length() > 128 || !SAFE_PART.matcher(value).matches()
                || value.startsWith(".") || value.endsWith(".") || value.contains("..")) {
            throw new IllegalArgumentException("Unsafe Maven coordinate " + label);
        }
        return value;
    }

    private static boolean isImmutableRelease(String version) {
        String normalized = version.toUpperCase(Locale.ROOT);
        return !normalized.contains("SNAPSHOT")
                && !"LATEST".equals(normalized)
                && !"RELEASE".equals(normalized)
                && !TIMESTAMPED_SNAPSHOT.matcher(version).matches();
    }
}
