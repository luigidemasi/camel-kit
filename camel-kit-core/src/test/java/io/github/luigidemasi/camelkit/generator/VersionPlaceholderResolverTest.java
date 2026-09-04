package io.github.luigidemasi.camelkit.generator;

import java.nio.file.Files;
import java.nio.file.Path;

import io.github.luigidemasi.camelkit.config.DistributionConfig;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class VersionPlaceholderResolverTest {

    @TempDir
    Path tempDir;

    @Test
    void substitutesVersionPlaceholdersAndPreservesOtherBraces() throws Exception {
        Path mdFile = tempDir.resolve("test.md");
        Files.writeString(mdFile, """
                jackson: {}
                ${quarkus.platform.version}
                {COMMAND_PREFIX} graph stats
                Runtime property placeholder: {{CAMEL_VERSION}}
                Version: {QUARKUS_PLATFORM_VERSION}
                MCP: {CAMEL_MCP_VERSION}
                Repositories: {CAMEL_MCP_REPOS}
                Forage:
                {FORAGE_VERSION_TABLE}
                Selected Citrus: {CITRUS_VERSION}
                """);
        new VersionPlaceholderResolver().substitute(mdFile);

        String result = Files.readString(mdFile);
        DistributionConfig dist = DistributionConfig.loadFromClasspathOrDefaults();
        assertTrue(result.contains("jackson: {}"), "YAML empty map must be preserved");
        assertTrue(result.contains("${quarkus.platform.version}"), "Maven property must be preserved");
        assertTrue(result.contains("{COMMAND_PREFIX} graph stats"), "Non-version placeholder must be preserved");
        assertTrue(result.contains("{{CAMEL_VERSION}}"), "Camel runtime property placeholder must be preserved");
        assertTrue(result.contains("{CITRUS_VERSION}"), "Project-selected Citrus version must be preserved");
        assertTrue(result.contains(dist.quarkusPlatformVersion()), "Version placeholder must be resolved");
        assertTrue(result.contains(dist.camelMcpVersion()), "MCP version placeholder must be resolved");
        assertTrue(result.contains(dist.camelMcpRepos()), "MCP repository placeholder must be resolved");
        for (var entry : dist.forageVersionMappings().entrySet()) {
            assertTrue(result.contains("| " + entry.getKey() + " | " + entry.getValue() + " |"));
        }
        assertFalse(result.contains("{QUARKUS_PLATFORM_VERSION}"), "Version placeholder must not remain");
        assertFalse(result.contains("{FORAGE_VERSION_TABLE}"), "Forage table placeholder must not remain");
    }

    @Test
    void skipsFilesWithoutVersionPlaceholders() throws Exception {
        Path mdFile = tempDir.resolve("plain.md");
        String original = "No placeholders here. Just some text with {braces} and ${maven}.";
        Files.writeString(mdFile, original);

        new VersionPlaceholderResolver().substitute(mdFile);

        assertEquals(original, Files.readString(mdFile), "File without version placeholders must not change");
    }

    @Test
    void substitutesCommandPrefixWhenProvided() throws Exception {
        Path mdFile = tempDir.resolve("command.md");
        Files.writeString(mdFile, "Run {COMMAND_PREFIX} graph stats");

        new VersionPlaceholderResolver().substitute(
                mdFile, DistributionConfig.loadFromClasspathOrDefaults(), "camel kit");

        assertEquals("Run camel kit graph stats", Files.readString(mdFile));
    }
}
