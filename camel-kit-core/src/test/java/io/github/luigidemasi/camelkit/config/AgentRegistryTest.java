package io.github.luigidemasi.camelkit.config;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class AgentRegistryTest {

    @TempDir
    Path tempDir;

    @Test
    void builtInAgentsAreLoadedFromResourceDescriptors() {
        assertEquals(Set.of("bob", "claude", "gemini", "opencode", "qwen"), AgentRegistry.names());

        AgentConfig claude = AgentRegistry.get("claude");
        assertNotNull(claude);
        assertEquals("Claude Code", claude.name());
        assertEquals(".claude/commands", claude.folder());
        assertEquals("md", claude.fileFormat());
        assertEquals("$ARGUMENTS", claude.argPlaceholder());
        assertEquals(".mcp.json", claude.mcpConfigPath());
        assertEquals("templates/mcp-configs/claude-code-mcp.json", claude.mcpConfigTemplatePath());
        assertEquals("mcpServers", claude.mcpServerContainerKey());
        assertEquals("Anthropic's Claude Code CLI", claude.description());

        AgentDescriptor descriptor = AgentRegistry.descriptor("claude");
        assertNotNull(descriptor);
        assertEquals("claude", descriptor.id());
        assertEquals("claude", descriptor.generatorStrategy());
        assertEquals(AgentGeneratorStrategy.CLAUDE, descriptor.generatorStrategyType());
        assertEquals("templates/dispatch/claude.md", descriptor.dispatchTemplatePath());
        assertTrue(descriptor.supportsSubagents());
        assertTrue(descriptor.supportsTraits());
        assertTrue(descriptor.capabilities().contains("parallel-subagent-dispatch"));
    }

    @Test
    void descriptorRegistryIsImmutable() {
        assertThrows(UnsupportedOperationException.class, () -> AgentRegistry.descriptors().clear());
        assertThrows(UnsupportedOperationException.class, () -> AgentRegistry.all().clear());
    }

    @Test
    void missingRegistryDirectoryFailsClearly() {
        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> AgentDescriptorLoader.load(tempDir.resolve("missing")));

        assertTrue(thrown.getMessage().contains("Agent descriptor registry not found"));
        assertTrue(thrown.getMessage().contains("missing"));
    }

    @Test
    void missingRequiredDescriptorFieldFailsClearly() throws IOException {
        Files.writeString(tempDir.resolve("broken.yaml"), """
                id: broken
                commandDirectory: .broken/commands
                commandFileFormat: md
                argumentPlaceholder: $ARGUMENTS
                mcpConfigPath: .broken/mcp.json
                mcpConfigTemplatePath: templates/mcp-configs/broken-mcp.json
                mcpServerContainerKey: mcpServers
                description: Broken test agent
                generatorStrategy: default
                dispatchTemplatePath: templates/dispatch/broken.md
                supportsSubagents: false
                supportsTraits: false
                """);

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> AgentDescriptorLoader.load(tempDir));

        assertTrue(thrown.getMessage().contains("broken.yaml"));
        assertTrue(thrown.getMessage().contains("displayName"));
    }

    @Test
    void malformedDescriptorYamlFailsClearly() throws IOException {
        Files.writeString(tempDir.resolve("broken.yaml"), "id: [\n");

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> AgentDescriptorLoader.load(tempDir));

        assertTrue(thrown.getMessage().contains("Could not read agent descriptor"));
        assertTrue(thrown.getMessage().contains("broken.yaml"));
    }

    @Test
    void unsupportedGeneratorStrategyFailsClearly() throws IOException {
        Files.writeString(tempDir.resolve("broken.yaml"), validDescriptor("broken")
                .replace("generatorStrategy: default", "generatorStrategy: custom"));

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> AgentDescriptorLoader.load(tempDir));

        assertTrue(thrown.getMessage().contains("unsupported generatorStrategy 'custom'"));
    }

    @Test
    void jarBackedRegistryLoadsDescriptors() throws Exception {
        Path jarFile = tempDir.resolve("agents.jar");
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(jarFile))) {
            jar.putNextEntry(new JarEntry("agents/registry/"));
            jar.closeEntry();
            jar.putNextEntry(new JarEntry("agents/registry/broken.yaml"));
            jar.write(validDescriptor("broken").getBytes(StandardCharsets.UTF_8));
            jar.closeEntry();
        }

        URI jarResourceUri = URI.create("jar:" + jarFile.toUri() + "!/agents/registry");
        Map<String, AgentDescriptor> descriptors = AgentDescriptorLoader.load(jarResourceUri.toURL());

        assertEquals(Set.of("broken"), descriptors.keySet());
        assertEquals("Test Agent", descriptors.get("broken").displayName());
    }

    @Test
    void duplicateDescriptorIdsFailClearly() throws IOException {
        Files.writeString(tempDir.resolve("broken.yaml"), validDescriptor("broken"));
        Files.writeString(tempDir.resolve("broken.yml"), validDescriptor("broken"));

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> AgentDescriptorLoader.load(tempDir));

        assertTrue(thrown.getMessage().contains("Duplicate agent descriptor id 'broken'"));
    }

    private static String validDescriptor(String id) {
        return String.format(Locale.ROOT, """
                id: %1$s
                displayName: Test Agent
                commandDirectory: .%1$s/commands
                commandFileFormat: md
                argumentPlaceholder: $ARGUMENTS
                mcpConfigPath: .%1$s/mcp.json
                mcpConfigTemplatePath: templates/mcp-configs/%1$s-mcp.json
                mcpServerContainerKey: mcpServers
                description: Test agent
                generatorStrategy: default
                dispatchTemplatePath: templates/dispatch/%1$s.md
                templates:
                  - source: templates/%1$s/settings.json
                    target: .%1$s/settings.json
                supportsSubagents: false
                supportsTraits: false
                capabilities:
                  - test
                """, id);
    }
}
