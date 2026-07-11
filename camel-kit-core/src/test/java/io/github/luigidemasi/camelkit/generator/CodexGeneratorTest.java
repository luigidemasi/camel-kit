package io.github.luigidemasi.camelkit.generator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import io.github.luigidemasi.camelkit.config.AgentConfig;
import io.github.luigidemasi.camelkit.config.AgentDescriptor;
import io.github.luigidemasi.camelkit.config.AgentRegistry;
import io.github.luigidemasi.camelkit.output.Printer;
import io.github.luigidemasi.camelkit.workflow.WorkflowManifest;
import io.github.luigidemasi.camelkit.workflow.WorkflowManifestLoader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

import static org.junit.jupiter.api.Assertions.*;

class CodexGeneratorTest {

    @TempDir
    Path tempDir;

    @Test
    void generatesCodexNativeAssetsWithoutCommandScaffolding() throws Exception {
        InitContext ctx = createContext(tempDir);

        new CodexGenerator().generate(ctx);

        assertFalse(Files.exists(tempDir.resolve(".codex/commands")));
        assertFalse(Files.exists(tempDir.resolve(".agents/commands")));
        assertTrue(Files.isRegularFile(tempDir.resolve("AGENTS.md")));
        String agentsMd = Files.readString(tempDir.resolve("AGENTS.md"));
        assertTrue(agentsMd.contains("$camel-start"));
        assertTrue(agentsMd.contains("/skills"));
        assertTrue(agentsMd.contains("/mcp"));
        assertTrue(agentsMd.contains("configuration and any project hooks only after the repository is trusted"));
        assertFalse(agentsMd.contains("/camel-start"));

        String executeSkill = Files.readString(tempDir.resolve(".agents/skills/camel-execute/SKILL.md"));
        assertTrue(executeSkill.contains("Spawn independent tasks from the same implementation wave in parallel"));
        assertTrue(executeSkill.contains("Delegated agents must not spawn more agents"));

        String startSkill = Files.readString(tempDir.resolve(".agents/skills/camel-start/SKILL.md"));
        assertTrue(startSkill.contains("$camel-plan"));
        assertTrue(startSkill.contains("$camel-execute"));
        assertFalse(startSkill.contains("/camel-plan"));
        assertFalse(startSkill.contains("/camel-execute"));

        String planSkill = Files.readString(tempDir.resolve(".agents/skills/camel-plan/SKILL.md"));
        assertTrue(planSkill.contains("$camel-brainstorm"));
        assertTrue(planSkill.contains("$camel-execute"));
        assertTrue(planSkill.contains("docs/camel-kit/<PIPELINE_ID>/implementation-plan.md"));
        assertFalse(planSkill.contains("/camel-brainstorm"));
        assertFalse(planSkill.contains("/camel-execute"));

        WorkflowManifest workflow = WorkflowManifestLoader.loadDefault();
        assertNoUnsupportedSlashSkillInvocations(workflow, tempDir.resolve(".agents/skills"));
        for (WorkflowManifest.WorkflowSkill skill : workflow.skills()) {
            assertTrue(Files.isRegularFile(
                    tempDir.resolve(".agents/skills").resolve(skill.name()).resolve("SKILL.md")), skill.name());
        }
        assertEquals(workflow.skills().size(), countSkillDirectories(tempDir.resolve(".agents/skills")));

        AgentDescriptor descriptor = AgentRegistry.descriptor("codex");
        long customAgentCount = descriptor.templates().stream()
                .filter(template -> template.target().startsWith(".codex/agents/"))
                .count();
        assertEquals(7, customAgentCount);
        for (AgentDescriptor.TemplateInstall template : descriptor.templates()) {
            if (!template.target().startsWith(".codex/agents/")) {
                continue;
            }
            TomlParseResult agent = Toml.parse(tempDir.resolve(template.target()));
            assertFalse(agent.hasErrors(), agent.errors().toString());
            assertNonBlankString(agent, "name");
            assertNonBlankString(agent, "description");
            assertNonBlankString(agent, "developer_instructions");
            if (template.target().endsWith("camel-catalog-researcher.toml")
                    || template.target().endsWith("camel-security-reviewer.toml")) {
                assertEquals("read-only", agent.getString("sandbox_mode"), template.target());
            }
        }
        assertFalse(Files.exists(tempDir.resolve(".codex/hooks")));
    }

    @Test
    void generatesResolvedLeastPrivilegeMcpConfiguration() throws Exception {
        InitContext ctx = createContext(tempDir);

        new CodexGenerator().generate(ctx);

        Path configFile = tempDir.resolve(".codex/config.toml");
        String configText = Files.readString(configFile);
        assertFalse(configText.contains("{CAMEL_"));
        assertFalse(configText.contains("{KNOWLEDGE_"));
        assertFalse(configText.contains("{CITRUS_"));
        assertEquals(1, occurrences(configText, CodexConfigMerger.BEGIN_MARKER));
        assertEquals(1, occurrences(configText, CodexConfigMerger.END_MARKER));

        TomlParseResult config = Toml.parse(configFile);
        assertFalse(config.hasErrors(), config.errors().toString());
        TomlTable servers = (TomlTable) config.get("mcp_servers");
        assertNotNull(servers);

        WorkflowManifest workflow = WorkflowManifestLoader.loadDefault();
        assertServer(servers, "camel", workflow.mcpServer("camel").allowedTools(),
                ctx.distribution().camelMcpVersion());
        assertServer(servers, "camel-knowledge", workflow.mcpServer("camel-knowledge").allowedTools(),
                ctx.distribution().knowledgeMcpVersion());
        assertServer(servers, "citrus", workflow.mcpServer("citrus").allowedTools(),
                ctx.distribution().citrusMcpVersion());
    }

    @Test
    void preservesUnrelatedValidCodexConfiguration() throws Exception {
        Path codexDir = Files.createDirectories(tempDir.resolve(".codex"));
        Files.writeString(codexDir.resolve("config.toml"), """
                model_reasoning_effort = "high"

                [mcp_servers.other]
                command = "other-mcp"
                enabled_tools = ["read"]
                """);

        new CodexGenerator().generate(createContext(tempDir));

        TomlParseResult config = Toml.parse(codexDir.resolve("config.toml"));
        assertFalse(config.hasErrors(), config.errors().toString());
        assertEquals("high", config.getString("model_reasoning_effort"));
        TomlTable servers = config.getTable("mcp_servers");
        assertEquals("other-mcp", servers.getTable("other").getString("command"));
        assertNotNull(servers.getTable("camel"));
        assertNotNull(servers.getTable("camel-knowledge"));
        assertNotNull(servers.getTable("citrus"));
    }

    @Test
    void invalidExistingCodexConfigurationFailsWithoutChangingIt() throws Exception {
        Path configFile = Files.createDirectories(tempDir.resolve(".codex")).resolve("config.toml");
        String invalid = "model = [\n";
        Files.writeString(configFile, invalid);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> new CodexGenerator().generate(createContext(tempDir)));

        assertTrue(error.getMessage().contains("not valid TOML"));
        assertEquals(invalid, Files.readString(configFile));
    }

    @Test
    void conflictingCodexMcpTableFailsWithoutChangingIt() throws Exception {
        Path configFile = Files.createDirectories(tempDir.resolve(".codex")).resolve("config.toml");
        String existing = """
                [mcp_servers.camel]
                command = "custom"
                """;
        Files.writeString(configFile, existing);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> new CodexGenerator().generate(createContext(tempDir)));

        assertTrue(error.getMessage().contains("already defines Camel-Kit MCP server tables"));
        assertEquals(existing, Files.readString(configFile));
    }

    @Test
    void repeatedGenerationReplacesManagedBlockWithoutDuplicates() throws Exception {
        CodexGenerator generator = new CodexGenerator();
        InitContext ctx = createContext(tempDir);
        generator.generate(ctx);
        String first = Files.readString(tempDir.resolve(".codex/config.toml"));

        generator.generate(ctx);

        String second = Files.readString(tempDir.resolve(".codex/config.toml"));
        assertEquals(first, second);
        assertEquals(1, occurrences(second, CodexConfigMerger.BEGIN_MARKER));
    }

    @Test
    void refusesSymlinkedCodexDirectoryWithoutChangingExternalConfig() throws Exception {
        Path project = Files.createDirectories(tempDir.resolve("project"));
        Path externalCodex = Files.createDirectories(tempDir.resolve("external-codex"));
        Path externalConfig = externalCodex.resolve("config.toml");
        String original = "model_reasoning_effort = \"high\"\n";
        Files.writeString(externalConfig, original);
        createSymlinkOrSkip(project.resolve(".codex"), externalCodex);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> new CodexGenerator().generate(createContext(project)));

        assertTrue(error.getMessage().contains("symbolic link: .codex"));
        assertEquals(original, Files.readString(externalConfig));
        assertFalse(Files.exists(externalCodex.resolve("agents")));
    }

    @Test
    void refusesNestedSymlinkedSkillsDirectoryWithoutWritingOutsideProject() throws Exception {
        Path project = Files.createDirectories(tempDir.resolve("project"));
        Path agents = Files.createDirectories(project.resolve(".agents"));
        Path externalSkills = Files.createDirectories(tempDir.resolve("external-skills"));
        Path marker = externalSkills.resolve("keep.txt");
        Files.writeString(marker, "unchanged");
        createSymlinkOrSkip(agents.resolve("skills"), externalSkills);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> new CodexGenerator().generate(createContext(project)));

        assertTrue(error.getMessage().contains("symbolic link: .agents/skills"));
        assertEquals("unchanged", Files.readString(marker));
        try (var files = Files.list(externalSkills)) {
            assertEquals(List.of(marker), files.toList());
        }
    }

    private InitContext createContext(Path projectDir) {
        AgentConfig agent = AgentRegistry.get("codex");
        return new InitContext(
                agent,
                "codex",
                projectDir.resolve(".codex/commands"),
                projectDir.resolve(agent.skillsDirectory()),
                projectDir,
                "camel-kit",
                Printer.noop());
    }

    private void createSymlinkOrSkip(Path link, Path target) throws Exception {
        try {
            Files.createSymbolicLink(link, target.toAbsolutePath());
        } catch (IOException | UnsupportedOperationException | SecurityException e) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false,
                    "Symbolic links are unavailable on this test platform: " + e.getMessage());
        }
    }

    private long countSkillDirectories(Path skillsDir) throws Exception {
        try (var paths = Files.list(skillsDir)) {
            return paths.filter(Files::isDirectory)
                    .filter(path -> !"shared".equals(path.getFileName().toString()))
                    .count();
        }
    }

    private void assertNoUnsupportedSlashSkillInvocations(WorkflowManifest workflow, Path skillsDir)
            throws Exception {
        String skillNames = workflow.skills().stream()
                .map(skill -> Pattern.quote(skill.name()))
                .collect(Collectors.joining("|"));
        Pattern unsupported = Pattern.compile(
                "(?<![A-Za-z0-9._/-])/(?:" + skillNames + ")(?![A-Za-z0-9-])");
        try (var files = Files.walk(skillsDir)) {
            for (Path markdown : files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".md"))
                    .toList()) {
                var match = unsupported.matcher(Files.readString(markdown));
                if (match.find()) {
                    fail(markdown + " contains unsupported Codex invocation " + match.group());
                }
            }
        }
    }

    private void assertServer(TomlTable servers, String name, List<String> expectedTools, String version) {
        TomlTable server = (TomlTable) servers.get(name);
        assertNotNull(server, name);
        assertEquals("jbang", server.getString("command"));
        assertEquals("prompt", server.getString("default_tools_approval_mode"));

        Set<String> actualTools = new LinkedHashSet<>(strings(server.getArray("enabled_tools")));
        assertEquals(new LinkedHashSet<>(expectedTools), actualTools);
        assertTrue(strings(server.getArray("args")).stream().anyMatch(argument -> argument.contains(version)), name);
    }

    private List<String> strings(TomlArray values) {
        return java.util.stream.IntStream.range(0, values.size())
                .mapToObj(values::getString)
                .toList();
    }

    private void assertNonBlankString(TomlParseResult parsed, String key) {
        assertInstanceOf(String.class, parsed.get(key), key);
        assertFalse(((String) parsed.get(key)).isBlank(), key);
    }

    private int occurrences(String content, String token) {
        return content.split(java.util.regex.Pattern.quote(token), -1).length - 1;
    }
}
