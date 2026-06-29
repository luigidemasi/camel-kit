package io.github.luigidemasi.camelkit.config;

import java.io.InputStream;
import java.util.Properties;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DistributionConfigTest {

    @Test
    void loadsPerPlatformVersions() {
        InputStream in = getClass().getClassLoader().getResourceAsStream("distribution.properties");
        DistributionConfig config = DistributionConfig.load(in);

        assertEquals("4.14.4", config.camelMainVersion());
        assertEquals("4.14.4", config.camelSpringbootVersion());
        assertEquals("4.14.4", config.camelQuarkusVersion());
        assertEquals("4.14.4", config.springbootBomVersion());
        assertEquals("3.5.9", config.springBootVersion());
        assertEquals("3.27.2", config.quarkusPlatformVersion());
    }

    @Test
    void loadsSupportedVersions() {
        InputStream in = getClass().getClassLoader().getResourceAsStream("distribution.properties");
        DistributionConfig config = DistributionConfig.load(in);

        assertEquals("4.14.4,4.13.0", config.camelMainSupported());
        assertEquals("4.14.4,4.13.0", config.camelSpringbootSupported());
        assertEquals("4.14.4,4.13.0", config.camelQuarkusSupported());
    }

    @Test
    void quarkusPlatformForVersionLookup() {
        InputStream in = getClass().getClassLoader().getResourceAsStream("distribution.properties");
        DistributionConfig config = DistributionConfig.load(in);

        // Known mapping
        assertEquals("3.27.2", config.quarkusPlatformForVersion("4.14.4"));
        assertEquals("3.20.0", config.quarkusPlatformForVersion("4.13.0"));

        // Unknown version falls back to default quarkus.platform.version
        assertEquals("3.27.2", config.quarkusPlatformForVersion("9.99.99"));
    }

    @Test
    void quarkusPlatformMappingsReturnsExplicitEntries() {
        InputStream in = getClass().getClassLoader().getResourceAsStream("distribution.properties");
        DistributionConfig config = DistributionConfig.load(in);

        var mappings = config.quarkusPlatformMappings();
        assertEquals("3.27.2", mappings.get("4.14.4"));
        assertEquals("3.20.0", mappings.get("4.13.0"));
        assertFalse(mappings.containsKey("version"), "Default quarkus.platform.version must be excluded");
    }

    @Test
    void springBootMappingsReturnsExplicitEntries() {
        InputStream in = getClass().getClassLoader().getResourceAsStream("distribution.properties");
        DistributionConfig config = DistributionConfig.load(in);

        var mappings = config.springBootMappings();
        assertEquals("3.5.9", mappings.get("4.14.4"));
        assertEquals("3.5.3", mappings.get("4.13.0"));
        assertFalse(mappings.containsKey("version"), "Default spring.boot.version must be excluded");
    }

    @Test
    void loadsMcpConfig() {
        InputStream in = getClass().getClassLoader().getResourceAsStream("distribution.properties");
        DistributionConfig config = DistributionConfig.load(in);

        assertEquals("4.21.0-SNAPSHOT", config.camelMcpVersion());
        assertEquals("0.0.1-SNAPSHOT", config.knowledgeMcpVersion());
        assertEquals("5.0.0-M2", config.citrusVersion());
        assertEquals("5.0.0-M2", config.citrusMcpVersion());
        assertEquals("maven", config.camelMcpRepos());
        assertEquals("maven", config.knowledgeMcpRepos());
        assertEquals("maven", config.citrusMcpRepos());
        assertEquals("maven", config.camelCatalogRepos());
    }

    @Test
    void camelMcpVersionCanBeOverriddenViaProperties() {
        InputStream in = getClass().getClassLoader().getResourceAsStream("distribution.properties");
        DistributionConfig baselineConfig = DistributionConfig.load(in);
        assertEquals("4.21.0-SNAPSHOT", baselineConfig.camelMcpVersion());

        Properties properties = new Properties();
        properties.setProperty("camel.mcp.version", "9.9.9-TEST");

        DistributionConfig overriddenConfig = DistributionConfig.load(properties);
        assertEquals("9.9.9-TEST", overriddenConfig.camelMcpVersion());
    }

    @Test
    void citrusMcpVersionDefaultsIndependentlyFromCitrusVersion() {
        Properties properties = new Properties();
        properties.setProperty("citrus.version", "4.9.2");

        DistributionConfig config = DistributionConfig.load(properties);

        assertEquals("4.9.2", config.citrusVersion());
        assertEquals("5.0.0-M2", config.citrusMcpVersion());
    }

    @Test
    void loadFromClasspath() {
        DistributionConfig config = DistributionConfig.loadFromClasspathOrDefaults();
        assertNotNull(config.camelMainVersion());
    }
}
