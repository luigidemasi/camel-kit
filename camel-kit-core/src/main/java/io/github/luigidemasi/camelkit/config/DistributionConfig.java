package io.github.luigidemasi.camelkit.config;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
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
    private final String quarkusPlatformVersion;
    private final String camelMainSupported;
    private final String camelSpringbootSupported;
    private final String camelQuarkusSupported;
    private final String camelMcpVersion;
    private final String knowledgeMcpVersion;
    private final String camelMcpRepos;
    private final String knowledgeMcpRepos;
    private final String camelCatalogRepos;
    private final int overrideCount;

    private DistributionConfig(Properties props, int overrideCount) {
        this.rawProps = props;
        this.camelMainVersion = props.getProperty("camel.main.version", "4.20.0");
        this.camelSpringbootVersion = props.getProperty("camel.springboot.version", "4.20.0");
        this.camelQuarkusVersion = props.getProperty("camel.quarkus.version", "4.18.0");
        this.springbootBomVersion = props.getProperty("springboot.bom.version", "4.20.0");
        this.quarkusPlatformVersion = props.getProperty("quarkus.platform.version", "3.33.1");
        this.camelMainSupported = props.getProperty("camel.main.supported", "4.20.0");
        this.camelSpringbootSupported = props.getProperty("camel.springboot.supported", "4.20.0");
        this.camelQuarkusSupported = props.getProperty("camel.quarkus.supported", "4.18.0");
        this.camelMcpVersion = props.getProperty("camel.mcp.version", "4.20.0");
        this.knowledgeMcpVersion = props.getProperty("knowledge.mcp.version", "0.0.1-SNAPSHOT");
        this.camelMcpRepos = props.getProperty("camel.mcp.repos", "maven");
        this.knowledgeMcpRepos = props.getProperty("knowledge.mcp.repos", "maven");
        this.camelCatalogRepos = props.getProperty("camel.catalog.repos", "maven");
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
        Properties props = new Properties();
        try (InputStream in = DistributionConfig.class.getClassLoader()
                .getResourceAsStream("distribution.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (Exception ignored) {
        }

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
            props.load(in);
        } catch (Exception e) {
            // Fall through with empty properties — defaults will apply
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

    public String camelMcpVersion() {
        return camelMcpVersion;
    }

    public String knowledgeMcpVersion() {
        return knowledgeMcpVersion;
    }

    public String camelMcpRepos() {
        return camelMcpRepos;
    }

    public String knowledgeMcpRepos() {
        return knowledgeMcpRepos;
    }

    public String camelCatalogRepos() {
        return camelCatalogRepos;
    }

    public int overrideCount() {
        return overrideCount;
    }
}
