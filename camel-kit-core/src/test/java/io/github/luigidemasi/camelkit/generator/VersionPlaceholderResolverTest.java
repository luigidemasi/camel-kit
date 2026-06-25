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
                Version: {QUARKUS_PLATFORM_VERSION}
                """);
        new VersionPlaceholderResolver().substitute(mdFile);

        String result = Files.readString(mdFile);
        DistributionConfig dist = DistributionConfig.loadFromClasspathOrDefaults();
        assertTrue(result.contains("jackson: {}"), "YAML empty map must be preserved");
        assertTrue(result.contains("${quarkus.platform.version}"), "Maven property must be preserved");
        assertTrue(result.contains("{COMMAND_PREFIX} graph stats"), "Non-version placeholder must be preserved");
        assertTrue(result.contains(dist.quarkusPlatformVersion()), "Version placeholder must be resolved");
        assertFalse(result.contains("{QUARKUS_PLATFORM_VERSION}"), "Version placeholder must not remain");
    }

    @Test
    void skipsFilesWithoutVersionPlaceholders() throws Exception {
        Path mdFile = tempDir.resolve("plain.md");
        String original = "No placeholders here. Just some text with {braces} and ${maven}.";
        Files.writeString(mdFile, original);

        new VersionPlaceholderResolver().substitute(mdFile);

        assertEquals(original, Files.readString(mdFile), "File without version placeholders must not change");
    }
}
