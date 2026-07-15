package io.github.luigidemasi.camelkit.config;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
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

    private static final String DEFAULT_CITRUS_VERSION = "5.0.0-M2";
    private static final String DEFAULT_CITRUS_MCP_VERSION = "5.0.0-M1";
    private static final String DEFAULT_PI_VERSION = "0.80.3";
    private static final String DEFAULT_PI_MCP_ADAPTER_VERSION = "2.11.0";

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
    private final String piMcpAdapterVersion;
    private final int overrideCount;

    private DistributionConfig(Properties props, int overrideCount) {
        this.rawProps = props;
        this.camelMainVersion = props.getProperty("camel.main.version", "4.21.0");
        this.camelSpringbootVersion = props.getProperty("camel.springboot.version", "4.21.0");
        this.camelQuarkusVersion = props.getProperty("camel.quarkus.version", "4.18.2");
        this.springbootBomVersion = props.getProperty("springboot.bom.version", "4.21.0");
        this.springBootVersion = props.getProperty("spring.boot.version", "4.1.0");
        this.quarkusPlatformVersion = props.getProperty("quarkus.platform.version", "3.33.1");
        this.camelMainSupported = props.getProperty("camel.main.supported", "4.21.0,4.18.3,4.14.7");
        this.camelSpringbootSupported = props.getProperty("camel.springboot.supported", "4.21.0,4.18.3,4.14.7");
        this.camelQuarkusSupported = props.getProperty("camel.quarkus.supported", "4.18.2,4.14.7");
        this.citrusVersion = props.getProperty("citrus.version", DEFAULT_CITRUS_VERSION);
        this.camelMcpVersion = props.getProperty("camel.mcp.version", "4.21.0");
        this.knowledgeMcpVersion = props.getProperty("knowledge.mcp.version", "0.0.1-SNAPSHOT");
        this.citrusMcpVersion = props.getProperty("citrus.mcp.version", DEFAULT_CITRUS_MCP_VERSION);
        this.camelMcpRepos = props.getProperty("camel.mcp.repos",
                "central=https://repo1.maven.org/maven2/,apache_snap=https://repository.apache.org/snapshots");
        this.knowledgeMcpRepos = props.getProperty("knowledge.mcp.repos", "central=https://repo1.maven.org/maven2/");
        this.citrusMcpRepos = props.getProperty("citrus.mcp.repos", "central=https://repo1.maven.org/maven2/");
        this.camelCatalogRepos = props.getProperty("camel.catalog.repos",
                "https://repo1.maven.org/maven2/,https://repository.apache.org/snapshots");
        this.piVersion = props.getProperty("pi.version", DEFAULT_PI_VERSION);
        this.piMcpAdapterVersion = props.getProperty("pi.mcp.adapter.version", DEFAULT_PI_MCP_ADAPTER_VERSION);
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
        // Layer 1: built-in defaults from classpath
        Properties props = loadClasspathProperties();

        // Layer 2: user config file (-c or default location)
        int overrides = 0;
        Path userConfig = configFile != null ? configFile : DEFAULT_USER_CONFIG;
        if (Files.exists(userConfig)) {
            try (InputStream in = Files.newInputStream(userConfig)) {
                Properties userProps = new Properties();
                userProps.load(in);
                for (String key : userProps.stringPropertyNames()) {
                    props.setProperty(key, userProps.getProperty(key));
                    overrides++;
                }
                System.out.printf(Locale.ROOT, "  Config: %s (%d overrides)%n", userConfig, userProps.size());
            } catch (Exception e) {
                System.out.printf(Locale.ROOT, "  WARN: Failed to load config from %s: %s%n", userConfig,
                        e.getMessage());
            }
        }

        // Layer 3: CLI -p overrides (highest priority)
        if (cliProperties != null) {
            for (String prop : cliProperties) {
                int eq = prop.indexOf('=');
                if (eq > 0) {
                    String key = prop.substring(0, eq).trim();
                    String value = prop.substring(eq + 1).trim();
                    props.setProperty(key, value);
                    overrides++;
                }
            }
        }

        return new DistributionConfig(props, overrides);
    }

    public static DistributionConfig load(InputStream in) {
        Properties props = new Properties();
        try {
            if (in != null) {
                props.load(in);
            }
        } catch (Exception e) {
            // Fall through with empty properties — defaults will apply
        }
        return new DistributionConfig(props, 0);
    }

    public static DistributionConfig load(Properties properties) {
        Properties props = new Properties();
        if (properties != null) {
            props.putAll(properties);
        }
        return new DistributionConfig(props, 0);
    }

    public static DistributionConfig loadFromFile(Path path) {
        try (InputStream in = Files.newInputStream(path)) {
            return load(in);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load distribution.properties from " + path, e);
        }
    }

    public static DistributionConfig loadFromFileWithClasspathBaseline(Path path) {
        Properties props = loadClasspathProperties();
        int overrides = applyFileOverrides(props, path);
        return new DistributionConfig(props, overrides);
    }

    public static DistributionConfig loadFromClasspathOrDefaults() {
        return loadWithOverrides(null, null);
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
        return rawProps.getProperty("forage.catalog.artifact", "io.kaoto.forage:forage-catalog");
    }

    /**
     * Returns all explicit Camel Quarkus → Quarkus platform version mappings from the {@code quarkus.platform.*}
     * properties (excluding the default {@code quarkus.platform.version}).
     */
    public Map<String, String> quarkusPlatformMappings() {
        Map<String, String> mappings = new LinkedHashMap<>();
        rawProps.stringPropertyNames().stream()
                .filter(key -> key.startsWith("quarkus.platform.") && !key.equals("quarkus.platform.version"))
                .sorted(Comparator.comparing(key -> key.substring("quarkus.platform.".length())))
                .forEach(key -> {
                    String camelVersion = key.substring("quarkus.platform.".length());
                    mappings.put(camelVersion, rawProps.getProperty(key));
                });
        return mappings;
    }

    /**
     * Returns all explicit Camel Spring Boot → Spring Boot framework version mappings from the {@code spring.boot.*}
     * properties (excluding the default {@code spring.boot.version}).
     */
    public Map<String, String> springBootMappings() {
        Map<String, String> mappings = new LinkedHashMap<>();
        rawProps.stringPropertyNames().stream()
                .filter(key -> key.startsWith("spring.boot.") && !key.equals("spring.boot.version"))
                .sorted(Comparator.comparing(key -> key.substring("spring.boot.".length())))
                .forEach(key -> {
                    String camelVersion = key.substring("spring.boot.".length());
                    mappings.put(camelVersion, rawProps.getProperty(key));
                });
        return mappings;
    }

    private static Properties loadClasspathProperties() {
        Properties props = new Properties();
        try (InputStream in = DistributionConfig.class.getClassLoader()
                .getResourceAsStream("distribution.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (Exception ignored) {
        }
        return props;
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

    public String piMcpAdapterVersion() {
        return piMcpAdapterVersion;
    }

    public int overrideCount() {
        return overrideCount;
    }
}
