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
        assertEquals(Set.of("bob", "bob2", "claude", "codex", "copilot", "gemini", "opencode", "pi", "qwen"),
                AgentRegistry.names());

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
    void codexDescriptorUsesNativeProjectLocationsWithoutCommands() {
        AgentConfig codex = AgentRegistry.get("codex");
        assertNotNull(codex);
        assertEquals("OpenAI Codex CLI", codex.name());
        assertNull(codex.commandDirectory());
        assertEquals(".agents/skills", codex.skillsDirectory());
        assertFalse(codex.generatesCommandStubs());
        assertEquals(".agents/skills", codex.folder());
        assertEquals(".codex/config.toml", codex.mcpConfigPath());
        assertEquals("toml", codex.mcpConfigFormat());
        assertEquals("mcp_servers", codex.mcpServerContainerKey());

        AgentDescriptor descriptor = AgentRegistry.descriptor("codex");
        assertNotNull(descriptor);
        assertEquals(AgentGeneratorStrategy.CODEX, descriptor.generatorStrategyType());
        assertTrue(descriptor.supportsSubagents());
        assertTrue(descriptor.capabilities().contains("project-skills"));
        assertTrue(descriptor.capabilities().contains("custom-agents"));
        assertTrue(descriptor.capabilities().contains("repository-trust"));
    }

    @Test
    void copilotDescriptorUsesGithubNativeProjectLocations() {
        AgentConfig copilot = AgentRegistry.get("copilot");
        assertNotNull(copilot);
        assertEquals("GitHub Copilot CLI", copilot.name());
        assertEquals(".github/commands", copilot.folder());
        assertEquals("md", copilot.fileFormat());
        assertEquals(".github/mcp.json", copilot.mcpConfigPath());
        assertEquals("templates/mcp-configs/copilot-mcp.json", copilot.mcpConfigTemplatePath());
        assertEquals("mcpServers", copilot.mcpServerContainerKey());

        AgentDescriptor descriptor = AgentRegistry.descriptor("copilot");
        assertNotNull(descriptor);
        assertEquals("copilot", descriptor.generatorStrategy());
        assertEquals(AgentGeneratorStrategy.COPILOT, descriptor.generatorStrategyType());
        assertEquals("templates/dispatch/copilot.md", descriptor.dispatchTemplatePath());
        assertTrue(descriptor.supportsSubagents());
        assertTrue(descriptor.supportsTraits());
        assertTrue(descriptor.capabilities().contains("custom-agents"));
        assertTrue(descriptor.capabilities().contains("project-skills"));
        assertTrue(descriptor.capabilities().contains("hooks"));
    }

    @Test
    void piDescriptorUsesNativeProjectLocations() {
        AgentConfig pi = AgentRegistry.get("pi");
        assertNotNull(pi);
        assertEquals("Pi", pi.name());
        assertEquals(".pi/prompts", pi.folder());
        assertEquals("md", pi.fileFormat());
        assertEquals(".mcp.json", pi.mcpConfigPath());
        assertEquals("templates/mcp-configs/pi-mcp.json", pi.mcpConfigTemplatePath());
        assertEquals("mcpServers", pi.mcpServerContainerKey());

        AgentDescriptor descriptor = AgentRegistry.descriptor("pi");
        assertNotNull(descriptor);
        assertEquals("pi", descriptor.generatorStrategy());
        assertEquals(AgentGeneratorStrategy.PI, descriptor.generatorStrategyType());
        assertEquals("templates/dispatch/pi.md", descriptor.dispatchTemplatePath());
        assertFalse(descriptor.supportsSubagents());
        assertTrue(descriptor.supportsTraits());
        assertTrue(descriptor.capabilities().contains("project-skills"));
        assertTrue(descriptor.capabilities().contains("prompt-templates"));
        assertTrue(descriptor.capabilities().contains("hooks"));
        assertFalse(descriptor.capabilities().contains("custom-agents"));
    }

    @Test
    void bob2DescriptorKeepsBobWorkspacePathsAndEnablesNativeSubagents() {
        AgentConfig bob2 = AgentRegistry.get("bob2");
        assertNotNull(bob2);
        assertEquals("IBM Bob 2", bob2.name());
        assertEquals(".bob/commands", bob2.folder());
        assertEquals("md", bob2.fileFormat());
        assertEquals(".bob/mcp.json", bob2.mcpConfigPath());
        assertEquals("templates/mcp-configs/bob-mcp.json", bob2.mcpConfigTemplatePath());

        AgentDescriptor descriptor = AgentRegistry.descriptor("bob2");
        assertNotNull(descriptor);
        assertEquals("bob2", descriptor.generatorStrategy());
        assertEquals(AgentGeneratorStrategy.BOB2, descriptor.generatorStrategyType());
        assertEquals("templates/dispatch/bob2.md", descriptor.dispatchTemplatePath());
        assertTrue(descriptor.supportsSubagents());
        assertTrue(descriptor.supportsTraits());
        assertTrue(descriptor.capabilities().contains("subagent-dispatch"));
        assertTrue(descriptor.capabilities().contains("parallel-subagent-dispatch"));
        assertTrue(descriptor.capabilities().contains("parallel-tool-calls"));
        assertTrue(descriptor.capabilities().contains("skills"));
        assertFalse(descriptor.capabilities().contains("monolithic-gates"));
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
    void legacyJsonDescriptorDefaultsNewPathAndFormatFields() throws IOException {
        Files.writeString(tempDir.resolve("legacy.yaml"), """
                id: legacy
                displayName: Legacy Agent
                commandDirectory: .legacy/commands
                commandFileFormat: md
                argumentPlaceholder: $ARGUMENTS
                mcpConfigPath: .legacy/mcp.json
                mcpConfigTemplatePath: templates/mcp-configs/legacy-mcp.json
                mcpServerContainerKey: mcpServers
                description: Legacy descriptor shape
                generatorStrategy: default
                dispatchTemplatePath: templates/dispatch/legacy.md
                supportsSubagents: false
                supportsTraits: false
                """);

        AgentDescriptor descriptor = AgentDescriptorLoader.load(tempDir).get("legacy");
        AgentConfig config = descriptor.toAgentConfig();

        assertTrue(descriptor.generatesCommandStubs());
        assertEquals(".legacy/skills", descriptor.skillsDirectory());
        assertEquals("json", descriptor.mcpConfigFormat());
        assertTrue(config.generatesCommandStubs());
        assertEquals(".legacy/skills", config.skillsDirectory());
        assertEquals("json", config.mcpConfigFormat());
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
                skillsDirectory: .%1$s/skills
                generatesCommandStubs: true
                commandFileFormat: md
                argumentPlaceholder: $ARGUMENTS
                mcpConfigPath: .%1$s/mcp.json
                mcpConfigTemplatePath: templates/mcp-configs/%1$s-mcp.json
                mcpConfigFormat: json
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
