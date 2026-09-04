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
    void loadsPerPlatformVersions() throws Exception {
        Properties properties = bundledProperties();
        DistributionConfig config = DistributionConfig.loadBundled();

        assertEquals(properties.getProperty("camel.main.version"), config.camelMainVersion());
        assertEquals(properties.getProperty("camel.springboot.version"), config.camelSpringbootVersion());
        assertEquals(properties.getProperty("camel.quarkus.version"), config.camelQuarkusVersion());
        assertEquals(properties.getProperty("springboot.bom.version"), config.springbootBomVersion());
        assertEquals(properties.getProperty("spring.boot.version"), config.springBootVersion());
        assertEquals(properties.getProperty("quarkus.platform.version"), config.quarkusPlatformVersion());
    }

    @Test
    void loadsSupportedVersions() throws Exception {
        Properties properties = bundledProperties();
        DistributionConfig config = DistributionConfig.loadBundled();

        assertEquals(properties.getProperty("camel.main.supported"), config.camelMainSupported());
        assertEquals(properties.getProperty("camel.springboot.supported"), config.camelSpringbootSupported());
        assertEquals(properties.getProperty("camel.quarkus.supported"), config.camelQuarkusSupported());
    }

    @Test
    void quarkusPlatformForVersionLookup() {
        DistributionConfig config = DistributionConfig.loadBundled();

        config.quarkusPlatformMappings()
                .forEach((camel, quarkus) -> assertEquals(quarkus, config.quarkusPlatformForVersion(camel)));
        assertEquals(config.quarkusPlatformVersion(), config.quarkusPlatformForVersion("9.99.99"));
    }

    @Test
    void quarkusPlatformMappingsReturnsExplicitEntries() throws Exception {
        Properties properties = bundledProperties();
        DistributionConfig config = DistributionConfig.loadBundled();

        var mappings = config.quarkusPlatformMappings();
        long expectedSize = properties.stringPropertyNames().stream()
                .filter(key -> key.startsWith("quarkus.platform.") && !key.equals("quarkus.platform.version"))
                .count();
        assertEquals(expectedSize, mappings.size());
        mappings.forEach(
                (camel, quarkus) -> assertEquals(properties.getProperty("quarkus.platform." + camel), quarkus));
        assertFalse(mappings.containsKey("version"), "Default quarkus.platform.version must be excluded");
    }

    @Test
    void springBootMappingsReturnsExplicitEntries() throws Exception {
        Properties properties = bundledProperties();
        DistributionConfig config = DistributionConfig.loadBundled();

        var mappings = config.springBootMappings();
        long expectedSize = properties.stringPropertyNames().stream()
                .filter(key -> key.startsWith("spring.boot.") && !key.equals("spring.boot.version"))
                .count();
        assertEquals(expectedSize, mappings.size());
        mappings.forEach((camel, spring) -> assertEquals(properties.getProperty("spring.boot." + camel), spring));
        assertFalse(mappings.containsKey("version"), "Default spring.boot.version must be excluded");
    }

    @Test
    void loadsMcpAndWorkerConfig() throws Exception {
        Properties properties = bundledProperties();
        DistributionConfig config = DistributionConfig.loadBundled();

        assertEquals(properties.getProperty("camel.mcp.version"), config.camelMcpVersion());
        assertEquals(properties.getProperty("knowledge.mcp.version"), config.knowledgeMcpVersion());
        assertEquals(properties.getProperty("citrus.version"), config.citrusVersion());
        assertEquals(properties.getProperty("citrus.mcp.version"), config.citrusMcpVersion());
        assertEquals(properties.getProperty("camel.mcp.repos"), config.camelMcpRepos());
        assertEquals(properties.getProperty("knowledge.mcp.repos"), config.knowledgeMcpRepos());
        assertEquals(properties.getProperty("citrus.mcp.repos"), config.citrusMcpRepos());
        assertEquals(properties.getProperty("camel.catalog.repos"), config.camelCatalogRepos());
        assertEquals(properties.getProperty("pi.version"), config.piVersion());
        assertEquals(List.of(properties.getProperty("pi.supported").split(",")), config.piSupportedVersions());
        assertEquals(properties.getProperty("node.version"), config.nodeVersion());
        assertEquals(properties.getProperty("pi.mcp.adapter.version"), config.piMcpAdapterVersion());
    }

    @Test
    void camelMcpVersionCanBeOverriddenViaProperties() {
        InputStream in = getClass().getClassLoader().getResourceAsStream("distribution.properties");
        DistributionConfig baselineConfig = DistributionConfig.load(in);
        assertEquals(DistributionConfig.loadBundled().camelMcpVersion(), baselineConfig.camelMcpVersion());

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
        assertEquals(List.of("9.9.9-test"), config.piSupportedVersions());
        assertEquals("7.7.7-test", config.nodeVersion());
        assertEquals("8.8.8-test", config.piMcpAdapterVersion());
    }

    @Test
    void fileOverridesUseClasspathBaseline(@TempDir Path tempDir) throws Exception {
        Path overrideFile = tempDir.resolve("distribution.properties");
        Files.writeString(overrideFile, "camel.main.version=9.9.9\n");

        DistributionConfig config = DistributionConfig.loadFromFileWithClasspathBaseline(overrideFile);
        DistributionConfig bundled = DistributionConfig.loadBundled();

        assertEquals("9.9.9", config.camelMainVersion());
        assertEquals(bundled.camelSpringbootVersion(), config.camelSpringbootVersion());
        assertEquals(bundled.camelMcpVersion(), config.camelMcpVersion());
    }

    @Test
    void citrusMcpVersionDefaultsIndependentlyFromCitrusVersion() {
        Properties properties = new Properties();
        properties.setProperty("citrus.version", "4.9.2");

        DistributionConfig config = DistributionConfig.load(properties);

        assertEquals("4.9.2", config.citrusVersion());
        assertEquals(DistributionConfig.loadBundled().citrusMcpVersion(), config.citrusMcpVersion());
    }

    @Test
    void loadFromClasspath() {
        DistributionConfig config = DistributionConfig.loadFromClasspathOrDefaults();
        assertNotNull(config.camelMainVersion());
    }

    @Test
    void bundledBaselineLoadsMaintainedWorkerVersionsWithoutOverrides() throws Exception {
        Properties properties = bundledProperties();
        DistributionConfig config = DistributionConfig.loadBundled();

        assertEquals(properties.getProperty("pi.version"), config.piVersion());
        assertEquals(List.of(properties.getProperty("pi.supported").split(",")), config.piSupportedVersions());
        assertEquals(config.piVersion(), config.piSupportedVersions().get(0),
                "the bundled primary pi.version must be the first pi.supported entry");
        assertEquals(properties.getProperty("node.version"), config.nodeVersion());
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
        assertEquals(DistributionConfig.loadBundled().camelSpringbootVersion(), config.camelSpringbootVersion());
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

        assertEquals(DistributionConfig.loadBundled().camelMainVersion(), DistributionConfig.loadWithOverridesStrict(
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

        assertEquals(DistributionConfig.loadBundled().camelMainVersion(), config.camelMainVersion());
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
        props.setProperty("forage.version.9.9.9", "9.1");
        props.setProperty("forage.version.8.8.8", "8.1");
        DistributionConfig config = DistributionConfig.load(props);
        assertEquals("9.1", config.forageVersionForCamel("9.9.9"));
        assertEquals("8.1", config.forageVersionForCamel("8.8.8"));
        assertEquals(List.of("8.8.8", "9.9.9"), List.copyOf(config.forageVersionMappings().keySet()));
    }

    @Test
    void forageVersionForCamelReturnsNullWhenUnmapped() {
        DistributionConfig config = DistributionConfig.load(new Properties());
        assertNull(config.forageVersionForCamel("9.9.9"));
    }

    @Test
    void forageCatalogArtifactComesFromBundledBaseline() {
        DistributionConfig config = DistributionConfig.load(new Properties());
        assertEquals(DistributionConfig.loadBundled().forageCatalogArtifact(), config.forageCatalogArtifact());
    }

    @Test
    void partialLoadsUseBundledScalarDefaults() {
        DistributionConfig bundled = DistributionConfig.loadBundled();

        for (DistributionConfig partial : List.of(
                DistributionConfig.load(new Properties()),
                DistributionConfig.load((InputStream) null))) {
            assertEquals(bundled.camelMainVersion(), partial.camelMainVersion());
            assertEquals(bundled.camelSpringbootVersion(), partial.camelSpringbootVersion());
            assertEquals(bundled.springbootBomVersion(), partial.springbootBomVersion());
            assertEquals(bundled.camelMcpVersion(), partial.camelMcpVersion());
            assertEquals(bundled.citrusVersion(), partial.citrusVersion());
            assertEquals(bundled.nodeVersion(), partial.nodeVersion());
            assertEquals(bundled.forageCatalogArtifact(), partial.forageCatalogArtifact());
        }
    }

    @Test
    void bundledRuntimeDefaultsAreInternallyConsistent() {
        DistributionConfig bundled = DistributionConfig.loadBundled();

        assertEquals(bundled.camelMainVersion(), bundled.camelMainSupported().split(",")[0]);
        assertEquals(bundled.camelSpringbootVersion(), bundled.camelSpringbootSupported().split(",")[0]);
        assertEquals(bundled.camelQuarkusVersion(), bundled.camelQuarkusSupported().split(",")[0]);
        assertEquals(bundled.camelSpringbootVersion(), bundled.springbootBomVersion());
        assertEquals(bundled.springBootVersion(),
                bundled.springBootMappings().get(bundled.camelSpringbootVersion()));
    }

    @Test
    void snapshotKnowledgeMcpUsesSnapshotRepository() {
        DistributionConfig config = DistributionConfig.loadBundled();
        assertTrue(
                !config.knowledgeMcpVersion().endsWith("-SNAPSHOT")
                        || config.knowledgeMcpRepos().contains(
                                "central_snap=https://central.sonatype.com/repository/maven-snapshots/"),
                "SNAPSHOT Knowledge MCP versions require the Central Portal snapshots repository");
    }

    private static Properties bundledProperties() throws java.io.IOException {
        Properties properties = new Properties();
        try (InputStream input = DistributionConfigTest.class.getClassLoader()
                .getResourceAsStream("distribution.properties")) {
            assertNotNull(input, "Missing packaged distribution.properties");
            properties.load(input);
        }
        return properties;
    }
}
