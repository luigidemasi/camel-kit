package io.github.luigidemasi.camelkit.config;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests run against the real repo-root {@code distribution.properties} (copied to the classpath by the build), so the
 * assertions below stay in lockstep with the shipped defaults and cannot silently diverge.
 */
class DistributionConfigTest {

    @Test
    void loadsPerPlatformVersions() {
        InputStream in = getClass().getClassLoader().getResourceAsStream("distribution.properties");
        DistributionConfig config = DistributionConfig.load(in);

        assertEquals("4.21.0", config.camelMainVersion());
        assertEquals("4.21.0", config.camelSpringbootVersion());
        assertEquals("4.18.2", config.camelQuarkusVersion());
        assertEquals("4.21.0", config.springbootBomVersion());
        assertEquals("4.1.0", config.springBootVersion());
        assertEquals("3.33.1", config.quarkusPlatformVersion());
    }

    @Test
    void loadsSupportedVersions() {
        InputStream in = getClass().getClassLoader().getResourceAsStream("distribution.properties");
        DistributionConfig config = DistributionConfig.load(in);

        assertEquals("4.21.0,4.18.3,4.14.7", config.camelMainSupported());
        assertEquals("4.21.0,4.18.3,4.14.7", config.camelSpringbootSupported());
        assertEquals("4.18.2,4.14.7", config.camelQuarkusSupported());
    }

    @Test
    void quarkusPlatformForVersionLookup() {
        InputStream in = getClass().getClassLoader().getResourceAsStream("distribution.properties");
        DistributionConfig config = DistributionConfig.load(in);

        // Known mapping
        assertEquals("3.33.1", config.quarkusPlatformForVersion("4.18.2"));
        assertEquals("3.27.3", config.quarkusPlatformForVersion("4.14.7"));

        // Unknown version falls back to default quarkus.platform.version
        assertEquals("3.33.1", config.quarkusPlatformForVersion("9.99.99"));
    }

    @Test
    void quarkusPlatformMappingsReturnsExplicitEntries() {
        InputStream in = getClass().getClassLoader().getResourceAsStream("distribution.properties");
        DistributionConfig config = DistributionConfig.load(in);

        var mappings = config.quarkusPlatformMappings();
        assertEquals("3.33.1", mappings.get("4.18.2"));
        assertEquals("3.27.3", mappings.get("4.14.7"));
        assertFalse(mappings.containsKey("version"), "Default quarkus.platform.version must be excluded");
    }

    @Test
    void springBootMappingsReturnsExplicitEntries() {
        InputStream in = getClass().getClassLoader().getResourceAsStream("distribution.properties");
        DistributionConfig config = DistributionConfig.load(in);

        var mappings = config.springBootMappings();
        assertEquals("4.1.0", mappings.get("4.21.0"));
        assertEquals("3.5.16", mappings.get("4.18.3"));
        assertEquals("3.5.12", mappings.get("4.14.7"));
        assertFalse(mappings.containsKey("version"), "Default spring.boot.version must be excluded");
    }

    @Test
    void loadsMcpConfig() {
        InputStream in = getClass().getClassLoader().getResourceAsStream("distribution.properties");
        DistributionConfig config = DistributionConfig.load(in);

        assertEquals("4.21.0", config.camelMcpVersion());
        assertEquals("0.0.1-SNAPSHOT", config.knowledgeMcpVersion());
        assertEquals("5.0.0-M2", config.citrusVersion());
        assertEquals("5.0.0-M1", config.citrusMcpVersion());
        assertEquals("central=https://repo1.maven.org/maven2/,apache_snap=https://repository.apache.org/snapshots",
                config.camelMcpRepos());
        assertEquals("central=https://repo1.maven.org/maven2/", config.knowledgeMcpRepos());
        assertEquals("central=https://repo1.maven.org/maven2/", config.citrusMcpRepos());
        assertEquals("https://repo1.maven.org/maven2/,https://repository.apache.org/snapshots",
                config.camelCatalogRepos());
        assertEquals("0.83.0", config.piVersion());
        assertEquals("22.22.2", config.nodeVersion());
        assertEquals("2.11.0", config.piMcpAdapterVersion());
    }

    @Test
    void camelMcpVersionCanBeOverriddenViaProperties() {
        InputStream in = getClass().getClassLoader().getResourceAsStream("distribution.properties");
        DistributionConfig baselineConfig = DistributionConfig.load(in);
        assertEquals("4.21.0", baselineConfig.camelMcpVersion());

        Properties properties = new Properties();
        properties.setProperty("camel.mcp.version", "9.9.9-TEST");

        DistributionConfig overriddenConfig = DistributionConfig.load(properties);
        assertEquals("9.9.9-TEST", overriddenConfig.camelMcpVersion());
    }

    @Test
    void piVersionsCanBeOverriddenViaProperties() {
        Properties properties = new Properties();
        properties.setProperty("pi.version", "9.9.9-test");
        properties.setProperty("node.version", "7.7.7-test");
        properties.setProperty("pi.mcp.adapter.version", "8.8.8-test");

        DistributionConfig config = DistributionConfig.load(properties);

        assertEquals("9.9.9-test", config.piVersion());
        assertEquals("7.7.7-test", config.nodeVersion());
        assertEquals("8.8.8-test", config.piMcpAdapterVersion());
    }

    @Test
    void fileOverridesUseClasspathBaseline(@TempDir Path tempDir) throws Exception {
        Path overrideFile = tempDir.resolve("distribution.properties");
        Files.writeString(overrideFile, "camel.main.version=9.9.9\n");

        DistributionConfig config = DistributionConfig.loadFromFileWithClasspathBaseline(overrideFile);

        assertEquals("9.9.9", config.camelMainVersion());
        assertEquals("4.21.0", config.camelSpringbootVersion());
        assertEquals("4.21.0", config.camelMcpVersion());
    }

    @Test
    void citrusMcpVersionDefaultsIndependentlyFromCitrusVersion() {
        Properties properties = new Properties();
        properties.setProperty("citrus.version", "4.9.2");

        DistributionConfig config = DistributionConfig.load(properties);

        assertEquals("4.9.2", config.citrusVersion());
        assertEquals("5.0.0-M1", config.citrusMcpVersion());
    }

    @Test
    void loadFromClasspath() {
        DistributionConfig config = DistributionConfig.loadFromClasspathOrDefaults();
        assertNotNull(config.camelMainVersion());
    }

    @Test
    void bundledBaselineLoadsMaintainedWorkerVersionsWithoutOverrides() {
        DistributionConfig config = DistributionConfig.loadBundled();

        assertEquals("0.83.0", config.piVersion());
        assertEquals("22.22.2", config.nodeVersion());
        assertEquals(0, config.overrideCount());
    }

    @Test
    @ResourceLock(Resources.SYSTEM_OUT)
    void cascadingOverridesAreQuietAndCliPropertiesWin(@TempDir Path tempDir) throws Exception {
        Path configFile = tempDir.resolve("config.properties");
        Files.writeString(
                configFile,
                "camel.main.version=8.8.8\n"
                            + "citrus.version=4.9.0\n");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream original = System.out;
        DistributionConfig config;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            config = DistributionConfig.loadWithOverrides(
                    configFile,
                    List.of(
                            "camel.main.version=9.9.8",
                            "camel.main.version=9.9.9",
                            "knowledge.mcp.version="));
        } finally {
            System.setOut(original);
        }

        assertEquals("", output.toString(StandardCharsets.UTF_8));
        assertEquals("9.9.9", config.camelMainVersion());
        assertEquals("4.9.0", config.citrusVersion());
        assertEquals("4.21.0", config.camelSpringbootVersion());
        assertEquals("", config.knowledgeMcpVersion());
        assertEquals(5, config.overrideCount());
    }

    @Test
    void explicitConfigMustBeAReadablePropertiesFile(@TempDir Path tempDir) throws Exception {
        Path missing = tempDir.resolve("missing.properties");
        Path directory = Files.createDirectory(tempDir.resolve("directory.properties"));
        Path malformed = Files.write(
                tempDir.resolve("malformed.properties"),
                "camel.main.version=\\u00ZZ\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertEquals(
                "Config file is missing, unreadable, or not a regular file: " + missing,
                assertThrows(
                        IllegalArgumentException.class,
                        () -> DistributionConfig.loadWithOverridesStrict(missing, List.of()))
                        .getMessage());
        assertTrue(assertThrows(
                IllegalArgumentException.class,
                () -> DistributionConfig.loadWithOverridesStrict(directory, List.of()))
                .getMessage().contains(directory.toString()));
        assertTrue(assertThrows(
                IllegalArgumentException.class,
                () -> DistributionConfig.loadWithOverridesStrict(malformed, List.of()))
                .getMessage().contains(malformed.toString()));
    }

    @Test
    void strictDefaultConfigMayBeAbsentButNotMalformed(@TempDir Path tempDir) throws Exception {
        Path configFile = tempDir.resolve("config.properties");

        assertEquals("4.21.0", DistributionConfig.loadWithOverridesStrict(
                null, List.of(), configFile).camelMainVersion());

        Files.writeString(configFile, "camel.main.version=9.9.9\n");
        assertEquals("9.9.9", DistributionConfig.loadWithOverridesStrict(
                null, List.of(), configFile).camelMainVersion());

        Files.writeString(configFile, "camel.main.version=\\u00ZZ\n");
        assertTrue(assertThrows(
                IllegalArgumentException.class,
                () -> DistributionConfig.loadWithOverridesStrict(null, List.of(), configFile))
                .getMessage().contains(configFile.toString()));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void strictDefaultConfigRejectsADanglingLink(@TempDir Path tempDir) throws Exception {
        Path configFile = tempDir.resolve("config.properties");
        Files.createSymbolicLink(configFile, tempDir.resolve("missing.properties"));

        assertTrue(assertThrows(
                IllegalArgumentException.class,
                () -> DistributionConfig.loadWithOverridesStrict(null, List.of(), configFile))
                .getMessage().contains(configFile.toString()));
    }

    @Test
    @ResourceLock(Resources.SYSTEM_ERR)
    void legacyCascadeIgnoresInvalidOverrides(@TempDir Path tempDir) throws Exception {
        Path malformed = Files.writeString(
                tempDir.resolve("malformed.properties"), "camel.main.version=\\u00ZZ\n");
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        PrintStream original = System.err;
        DistributionConfig config;
        try {
            System.setErr(new PrintStream(error, true, StandardCharsets.UTF_8));
            config = DistributionConfig.loadWithOverrides(
                    malformed, List.of("missing-equals"));
        } finally {
            System.setErr(original);
        }

        assertEquals("4.21.0", config.camelMainVersion());
        assertEquals(0, config.overrideCount());
        assertTrue(error.toString(StandardCharsets.UTF_8)
                .contains("WARN: Failed to load config from " + malformed + ":"));
    }

    @Test
    void malformedCliPropertiesFailWithoutEchoingTheirValues(@TempDir Path tempDir) {
        Path defaultConfig = tempDir.resolve("config.properties");
        for (String property : List.of("missing-equals", " =secret-value")) {
            IllegalArgumentException failure = assertThrows(
                    IllegalArgumentException.class,
                    () -> DistributionConfig.loadWithOverridesStrict(
                            null, List.of(property), defaultConfig));

            assertEquals("Invalid config property; expected key=value", failure.getMessage());
            assertFalse(failure.getMessage().contains(property));
        }
    }

    @Test
    void forageVersionForCamelReturnsMappedStream() {
        Properties props = new Properties();
        props.setProperty("forage.version.4.21.0", "1.5.0");
        props.setProperty("forage.version.4.18.3", "1.3");
        DistributionConfig config = DistributionConfig.load(props);
        assertEquals("1.5.0", config.forageVersionForCamel("4.21.0"));
        assertEquals("1.3", config.forageVersionForCamel("4.18.3"));
    }

    @Test
    void forageVersionForCamelReturnsNullWhenUnmapped() {
        DistributionConfig config = DistributionConfig.load(new Properties());
        assertNull(config.forageVersionForCamel("4.14.7"));
    }

    @Test
    void forageCatalogArtifactHasDefault() {
        DistributionConfig config = DistributionConfig.load(new Properties());
        assertEquals("io.kaoto.forage:forage-catalog", config.forageCatalogArtifact());
    }
}
