package io.github.luigidemasi.camelkit.catalog;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Loads Camel version mappings from catalog/versions.properties.
 * Single source of truth for Red Hat artifact version resolution.
 */
public final class VersionMapping {

    private static final String PROPERTIES_PATH = "catalog/versions.properties";
    private static final Map<String, CatalogVersions> VERSIONS;
    private static final String DEFAULT_VERSION;

    static {
        Properties props = new Properties();
        try (InputStream in = VersionMapping.class.getClassLoader().getResourceAsStream(PROPERTIES_PATH)) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException e) {
            throw new ExceptionInInitializerError("Cannot load " + PROPERTIES_PATH + ": " + e.getMessage());
        }

        DEFAULT_VERSION = props.getProperty("default", "4.14");
        String supported = props.getProperty("supported", "");
        Map<String, CatalogVersions> map = new LinkedHashMap<>();

        for (String minor : supported.split(",")) {
            minor = minor.trim();
            if (minor.isEmpty()) continue;
            map.put(minor, new CatalogVersions(
                    props.getProperty(minor + ".camelCatalog"),
                    props.getProperty(minor + ".springBootProvider"),
                    props.getProperty(minor + ".quarkusCatalog"),
                    props.getProperty(minor + ".mainPlatformBom"),
                    props.getProperty(minor + ".springBootPlatformBom"),
                    props.getProperty(minor + ".quarkusPlatformBom")));
        }
        VERSIONS = map;
    }

    private VersionMapping() {}

    public record CatalogVersions(
            String camelCatalog,
            String springBootProvider,
            String quarkusCatalog,
            String mainPlatformBom,
            String springBootPlatformBom,
            String quarkusPlatformBom
    ) {

        /**
         * Returns the platformBom GAV for the given runtime.
         * @param runtime "main", "spring-boot", or "quarkus"
         */
        public String platformBom(String runtime) {
            if (runtime == null) return mainPlatformBom;
            return switch (runtime.toLowerCase()) {
                case "spring-boot" -> springBootPlatformBom;
                case "quarkus" -> quarkusPlatformBom;
                default -> mainPlatformBom;
            };
        }
    }

    /**
     * Resolve a Camel version string to catalog artifact versions.
     * Accepts: "4.14", "4.14.4", "4.14.4.redhat-00008" — all resolve to the 4.14 entry.
     */
    public static CatalogVersions resolve(String camelVersion) {
        if (camelVersion == null || camelVersion.isBlank()) return null;

        CatalogVersions exact = VERSIONS.get(camelVersion);
        if (exact != null) return exact;

        String[] parts = camelVersion.split("\\.");
        if (parts.length >= 2) {
            return VERSIONS.get(parts[0] + "." + parts[1]);
        }
        return null;
    }

    public static String defaultMinorVersion() {
        return DEFAULT_VERSION;
    }

    public static Map<String, CatalogVersions> allVersions() {
        return Map.copyOf(VERSIONS);
    }
}
