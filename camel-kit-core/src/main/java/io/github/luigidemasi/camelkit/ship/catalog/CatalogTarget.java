package io.github.luigidemasi.camelkit.ship.catalog;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Exact immutable runtime tuple whose catalog bytes are being snapshotted. */
public record CatalogTarget(
        String runtime,
        String camelVersion,
        String platformVersion,
        String springBootVersion) {

    private static final Pattern SAFE_VERSION = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}");
    private static final Pattern TIMESTAMPED_SNAPSHOT = Pattern.compile(".*-\\d{8}\\.\\d{6}-\\d+$");

    public CatalogTarget {
        if (runtime == null || !Set.of("main", "spring-boot", "quarkus").contains(runtime)) {
            throw new IllegalArgumentException("Unsupported catalog runtime");
        }
        requireRelease(camelVersion, "Camel");
        if (platformVersion != null) {
            requireRelease(platformVersion, "platform");
        }
        if (springBootVersion != null) {
            requireRelease(springBootVersion, "Spring Boot");
        }
        switch (runtime) {
            case "main" -> {
                if (platformVersion != null || springBootVersion != null) {
                    throw new IllegalArgumentException("Camel Main catalog target accepts only camelVersion");
                }
            }
            case "spring-boot" -> {
                if (platformVersion == null || springBootVersion == null) {
                    throw new IllegalArgumentException(
                            "Spring Boot catalog target requires platformVersion and springBootVersion");
                }
            }
            case "quarkus" -> {
                if (platformVersion == null || springBootVersion != null) {
                    throw new IllegalArgumentException(
                            "Quarkus catalog target requires platformVersion and forbids springBootVersion");
                }
            }
            default -> throw new IllegalStateException("Unexpected catalog runtime");
        }
    }

    private static void requireRelease(String version, String label) {
        if (version == null || !SAFE_VERSION.matcher(version).matches()
                || version.startsWith(".") || version.endsWith(".") || version.contains("..")) {
            throw new IllegalArgumentException(label + " catalog version is invalid");
        }
        String normalized = version.toUpperCase(Locale.ROOT);
        if (normalized.contains("SNAPSHOT") || "LATEST".equals(normalized) || "RELEASE".equals(normalized)
                || TIMESTAMPED_SNAPSHOT.matcher(version).matches()) {
            throw new IllegalArgumentException(label + " catalog version must be an immutable release");
        }
    }
}
