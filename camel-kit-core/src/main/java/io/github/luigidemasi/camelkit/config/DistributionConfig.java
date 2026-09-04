package io.github.luigidemasi.camelkit.config;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * Configuration loaded from distribution.properties with cascading overrides.
 *
 * <p>
 * Resolution order (highest priority first):
 * <ol>
 * <li>CLI {@code -p key=value} overrides</li>
 * <li>User config file ({@code -c path} or {@code ~/.camel-kit/config.properties})</li>
 * <li>Built-in distribution.properties (from JAR classpath)</li>
 * </ol>
 */
public class DistributionConfig {

    private static final Path DEFAULT_USER_CONFIG = Path.of(
            System.getProperty("user.home"), ".camel-kit", "config.properties");

    private final Properties rawProps;
    private final String camelMainVersion;
    private final String camelSpringbootVersion;
    private final String camelQuarkusVersion;
    private final String springbootBomVersion;
    private final String springBootVersion;
    private final String quarkusPlatformVersion;
    private final String camelMainSupported;
    private final String camelSpringbootSupported;
    private final String camelQuarkusSupported;
    private final String citrusVersion;
    private final String camelMcpVersion;
    private final String knowledgeMcpVersion;
    private final String citrusMcpVersion;
    private final String camelMcpRepos;
    private final String knowledgeMcpRepos;
    private final String citrusMcpRepos;
    private final String camelCatalogRepos;
    private final String piVersion;
    private final String piSupported;
    private final String nodeVersion;
    private final String piMcpAdapterVersion;
    private final String forageCatalogArtifact;
    private final int overrideCount;

    private DistributionConfig(Properties props, Properties bundled, int overrideCount) {
        this.rawProps = props;
        this.camelMainVersion = configured(props, bundled, "camel.main.version");
        this.camelSpringbootVersion = configured(props, bundled, "camel.springboot.version");
        this.camelQuarkusVersion = configured(props, bundled, "camel.quarkus.version");
        this.springbootBomVersion = configured(props, bundled, "springboot.bom.version");
        this.springBootVersion = configured(props, bundled, "spring.boot.version");
        this.quarkusPlatformVersion = configured(props, bundled, "quarkus.platform.version");
        this.camelMainSupported = configured(props, bundled, "camel.main.supported");
        this.camelSpringbootSupported = configured(props, bundled, "camel.springboot.supported");
        this.camelQuarkusSupported = configured(props, bundled, "camel.quarkus.supported");
        this.citrusVersion = configured(props, bundled, "citrus.version");
        this.camelMcpVersion = configured(props, bundled, "camel.mcp.version");
        this.knowledgeMcpVersion = configured(props, bundled, "knowledge.mcp.version");
        this.citrusMcpVersion = configured(props, bundled, "citrus.mcp.version");
        this.camelMcpRepos = configured(props, bundled, "camel.mcp.repos");
        this.knowledgeMcpRepos = configured(props, bundled, "knowledge.mcp.repos");
        this.citrusMcpRepos = configured(props, bundled, "citrus.mcp.repos");
        this.camelCatalogRepos = configured(props, bundled, "camel.catalog.repos");
        this.piVersion = configured(props, bundled, "pi.version");
        requiredProperty(bundled, "pi.supported");
        this.piSupported = props.getProperty("pi.supported", this.piVersion);
        this.nodeVersion = configured(props, bundled, "node.version");
        this.piMcpAdapterVersion = configured(props, bundled, "pi.mcp.adapter.version");
        this.forageCatalogArtifact = configured(props, bundled, "forage.catalog.artifact");
        this.overrideCount = overrideCount;
    }

    /**
     * Load with cascading overrides: built-in defaults -> user config file -> CLI properties.
     *
     * @param  configFile    path to user config file, or null to use default (~/.camel-kit/config.properties)
     * @param  cliProperties CLI -p overrides as "key=value" strings, or null/empty
     * @return               merged configuration
     */
    public static DistributionConfig loadWithOverrides(Path configFile, List<String> cliProperties) {
        return loadWithOverrides(configFile, cliProperties, DEFAULT_USER_CONFIG, false);
    }

    /** Loads the same cascade but rejects a present configuration that cannot be applied exactly. */
    public static DistributionConfig loadWithOverridesStrict(Path configFile, List<String> cliProperties) {
        return loadWithOverrides(configFile, cliProperties, DEFAULT_USER_CONFIG, true);
    }

    static DistributionConfig loadWithOverridesStrict(
            Path configFile, List<String> cliProperties, Path defaultConfig) {
        return loadWithOverrides(configFile, cliProperties, defaultConfig, true);
    }

    private static DistributionConfig loadWithOverrides(
            Path configFile, List<String> cliProperties, Path defaultConfig, boolean strict) {
        // Layer 1: built-in defaults from classpath
        Properties bundled = loadClasspathProperties();
        Properties props = copyOf(bundled);

        // Layer 2: user config file (-c or default location)
        int overrides = 0;
        Path userConfig = configFile != null ? configFile : defaultConfig;
        boolean present = configFile != null || (strict
                ? !Files.notExists(userConfig, LinkOption.NOFOLLOW_LINKS)
                : Files.exists(userConfig));
        if (strict && present && !Files.isRegularFile(userConfig)) {
            throw new IllegalArgumentException(
                    "Config file is missing, unreadable, or not a regular file: " + userConfig);
        }
        if (present) {
            try (InputStream in = Files.newInputStream(userConfig)) {
                Properties userProps = new Properties();
                userProps.load(in);
                for (String key : userProps.stringPropertyNames()) {
                    props.setProperty(key, userProps.getProperty(key));
                    overrides++;
                }
            } catch (Exception e) {
                if (strict) {
                    throw new IllegalArgumentException(
                            "Failed to load config from " + userConfig, e);
                }
                System.err.printf(Locale.ROOT, "WARN: Failed to load config from %s: %s%n", userConfig,
                        e.getMessage());
            }
        }

        // Layer 3: CLI -p overrides (highest priority)
        if (cliProperties != null) {
            for (String prop : cliProperties) {
                int eq = prop == null ? -1 : prop.indexOf('=');
                String key = eq < 0 ? "" : prop.substring(0, eq).trim();
                if (key.isEmpty()) {
                    if (strict) {
                        throw new IllegalArgumentException(
                                "Invalid config property; expected key=value");
                    }
                    continue;
                }
                String value = prop.substring(eq + 1).trim();
                props.setProperty(key, value);
                overrides++;
            }
        }

        return new DistributionConfig(props, bundled, overrides);
    }

    public static DistributionConfig load(InputStream in) {
        Properties bundled = loadClasspathProperties();
        Properties props = new Properties();
        try {
            if (in != null) {
                props.load(in);
            }
        } catch (Exception e) {
            // Preserve the packaged baseline when an optional input cannot be applied.
        }
        return new DistributionConfig(props, bundled, 0);
    }

    public static DistributionConfig load(Properties properties) {
        Properties bundled = loadClasspathProperties();
        Properties props = new Properties();
        if (properties != null) {
            props.putAll(properties);
        }
        return new DistributionConfig(props, bundled, 0);
    }

    public static DistributionConfig loadFromFile(Path path) {
        try (InputStream in = Files.newInputStream(path)) {
            return load(in);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load distribution.properties from " + path, e);
        }
    }

    public static DistributionConfig loadFromFileWithClasspathBaseline(Path path) {
        Properties bundled = loadClasspathProperties();
        Properties props = copyOf(bundled);
        int overrides = applyFileOverrides(props, path);
        return new DistributionConfig(props, bundled, overrides);
    }

    public static DistributionConfig loadFromClasspathOrDefaults() {
        return loadWithOverrides(null, null);
    }

    /** Loads the packaged distribution baseline without user or CLI overrides. */
    public static DistributionConfig loadBundled() {
        Properties bundled = loadClasspathProperties();
        return new DistributionConfig(bundled, bundled, 0);
    }

    /**
     * Looks up the Quarkus platform BOM version for a given Camel Quarkus version. Falls back to the default
     * {@link #quarkusPlatformVersion()} if no mapping is found.
     */
    public String quarkusPlatformForVersion(String camelVersion) {
        return rawProps.getProperty("quarkus.platform." + camelVersion, quarkusPlatformVersion);
    }

    /**
     * Returns the Forage stream version mapped to the given Camel version via {@code forage.version.<camelVersion>}
     * properties, or {@code null} when no Forage stream is mapped (Forage support is then skipped).
     */
    public String forageVersionForCamel(String camelVersion) {
        return rawProps.getProperty("forage.version." + camelVersion);
    }

    /** Maven {@code groupId:artifactId} of the Forage catalog artifact. */
    public String forageCatalogArtifact() {
        return forageCatalogArtifact;
    }

    /** Returns all exact Camel → Forage stream mappings. */
    public Map<String, String> forageVersionMappings() {
        return mappings("forage.version.", null);
    }

    /**
     * Returns all explicit Camel Quarkus → Quarkus platform version mappings from the {@code quarkus.platform.*}
     * properties (excluding the default {@code quarkus.platform.version}).
     */
    public Map<String, String> quarkusPlatformMappings() {
        return mappings("quarkus.platform.", "quarkus.platform.version");
    }

    /**
     * Returns all explicit Camel Spring Boot → Spring Boot framework version mappings from the {@code spring.boot.*}
     * properties (excluding the default {@code spring.boot.version}).
     */
    public Map<String, String> springBootMappings() {
        return mappings("spring.boot.", "spring.boot.version");
    }

    private Map<String, String> mappings(String prefix, String excludedKey) {
        Map<String, String> mappings = new LinkedHashMap<>();
        rawProps.stringPropertyNames().stream()
                .filter(key -> key.startsWith(prefix) && (excludedKey == null || !key.equals(excludedKey)))
                .sorted(Comparator.comparing(key -> key.substring(prefix.length())))
                .forEach(key -> {
                    String camelVersion = key.substring(prefix.length());
                    mappings.put(camelVersion, rawProps.getProperty(key));
                });
        return mappings;
    }

    private static Properties loadClasspathProperties() {
        Properties props = new Properties();
        try (InputStream in = DistributionConfig.class.getClassLoader()
                .getResourceAsStream("distribution.properties")) {
            if (in == null) {
                throw new IllegalStateException(
                        "Packaged distribution.properties is unavailable");
            }
            props.load(in);
            return props;
        } catch (java.io.IOException | IllegalArgumentException e) {
            throw new IllegalStateException(
                    "Packaged distribution.properties could not be loaded", e);
        }
    }

    private static String requiredProperty(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null) {
            throw new IllegalStateException("Required distribution property is unavailable: " + key);
        }
        return value;
    }

    private static String configured(Properties properties, Properties bundled, String key) {
        return properties.getProperty(key, requiredProperty(bundled, key));
    }

    private static Properties copyOf(Properties source) {
        Properties copy = new Properties();
        copy.putAll(source);
        return copy;
    }

    private static int applyFileOverrides(Properties props, Path path) {
        if (path == null || !Files.exists(path)) {
            return 0;
        }
        try (InputStream in = Files.newInputStream(path)) {
            Properties fileProps = new Properties();
            fileProps.load(in);
            for (String key : fileProps.stringPropertyNames()) {
                props.setProperty(key, fileProps.getProperty(key));
            }
            return fileProps.size();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load distribution.properties from " + path, e);
        }
    }

    public String camelMainVersion() {
        return camelMainVersion;
    }

    public String camelSpringbootVersion() {
        return camelSpringbootVersion;
    }

    public String camelQuarkusVersion() {
        return camelQuarkusVersion;
    }

    public String springbootBomVersion() {
        return springbootBomVersion;
    }

    public String springBootVersion() {
        return springBootVersion;
    }

    public String quarkusPlatformVersion() {
        return quarkusPlatformVersion;
    }

    public String camelMainSupported() {
        return camelMainSupported;
    }

    public String camelSpringbootSupported() {
        return camelSpringbootSupported;
    }

    public String camelQuarkusSupported() {
        return camelQuarkusSupported;
    }

    public String citrusVersion() {
        return citrusVersion;
    }

    public String camelMcpVersion() {
        return camelMcpVersion;
    }

    public String knowledgeMcpVersion() {
        return knowledgeMcpVersion;
    }

    public String citrusMcpVersion() {
        return citrusMcpVersion;
    }

    public String camelMcpRepos() {
        return camelMcpRepos;
    }

    public String knowledgeMcpRepos() {
        return knowledgeMcpRepos;
    }

    public String citrusMcpRepos() {
        return citrusMcpRepos;
    }

    public String camelCatalogRepos() {
        return camelCatalogRepos;
    }

    public String piVersion() {
        return piVersion;
    }

    /**
     * Certified Pi versions; the first entry is the primary install target and must match {@link #piVersion()} — the
     * bundled ordering invariant is pinned by DistributionConfigTest, and overrides of the two properties must stay
     * consistent. Unlike the sibling raw-string supported accessors, this returns a parsed list because the Ship worker
     * needs list membership and positional access rather than template interpolation.
     */
    public List<String> piSupportedVersions() {
        return Arrays.stream(piSupported.split(","))
                .map(String::trim)
                .filter(version -> !version.isEmpty())
                .toList();
    }

    public String nodeVersion() {
        return nodeVersion;
    }

    public String piMcpAdapterVersion() {
        return piMcpAdapterVersion;
    }

    public int overrideCount() {
        return overrideCount;
    }
}
