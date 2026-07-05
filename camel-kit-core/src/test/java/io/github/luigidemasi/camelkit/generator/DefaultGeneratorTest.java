package io.github.luigidemasi.camelkit.generator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import io.github.luigidemasi.camelkit.config.AgentConfig;
import io.github.luigidemasi.camelkit.config.AgentGeneratorStrategy;
import io.github.luigidemasi.camelkit.config.AgentRegistry;
import io.github.luigidemasi.camelkit.config.DistributionConfig;
import io.github.luigidemasi.camelkit.output.Printer;
import io.github.luigidemasi.camelkit.workflow.WorkflowManifestLoader;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class DefaultGeneratorTest {

    private static final String COPILOT = AgentGeneratorStrategy.COPILOT.descriptorValue();
    private static final String PI = AgentGeneratorStrategy.PI.descriptorValue();

    @TempDir
    Path tempDir;

    private InitContext createContext(String agentName) {
        AgentConfig agent = AgentRegistry.get(agentName);
        String agentBaseFolder = agent.folder().substring(0, agent.folder().lastIndexOf("/"));
        Path commandsDir = tempDir.resolve(agent.folder());
        Path skillsDir = tempDir.resolve(agentBaseFolder + "/skills");
        return new InitContext(
                agent, agentName, commandsDir, skillsDir, tempDir,
                "camel-kit", Printer.noop());
    }

    @Test
    void generatesSlashCommands() throws Exception {
        InitContext ctx = createContext("bob");
        new DefaultGenerator().generate(ctx);

        assertTrue(Files.exists(ctx.commandsDir().resolve("camel-migrate.md")));
        assertTrue(Files.exists(ctx.commandsDir().resolve("camel-brainstorm.md")));
        assertTrue(Files.exists(ctx.commandsDir().resolve("camel-execute.md")));
        assertTrue(Files.exists(ctx.commandsDir().resolve("camel-start.md")));
        assertFalse(Files.exists(ctx.commandsDir().resolve("camel-flow.md")));
        assertFalse(Files.exists(ctx.commandsDir().resolve("camel-implement.md")));
        assertFalse(Files.exists(ctx.commandsDir().resolve("camel-test.md")));
        String content = Files.readString(ctx.commandsDir().resolve("camel-migrate.md"));
        assertTrue(content.contains("SKILL.md"));
    }

    @Test
    void copiesSkillsWithDispatch() throws Exception {
        InitContext ctx = createContext("bob");
        new DefaultGenerator().generate(ctx);

        assertTrue(Files.exists(ctx.skillsDir().resolve("camel-migrate/SKILL.md")));
        assertTrue(Files.exists(ctx.skillsDir().resolve("camel-brainstorm/SKILL.md")));
        String skillContent = Files.readString(ctx.skillsDir().resolve("camel-brainstorm/SKILL.md"));
        assertTrue(skillContent.contains("Dispatch"), "Dispatch block should be appended");
    }

    @Test
    void copiesSkillGuides() throws Exception {
        InitContext ctx = createContext("bob");
        new DefaultGenerator().generate(ctx);

        assertTrue(Files.isDirectory(ctx.skillsDir().resolve("camel-brainstorm/guides")));
        assertTrue(Files.isDirectory(ctx.skillsDir().resolve("camel-migrate/guides")));
    }

    @Test
    void generatesMcpConfig() throws Exception {
        InitContext ctx = createContext("bob");
        new DefaultGenerator().generate(ctx);

        assertTrue(Files.exists(tempDir.resolve(ctx.agent().mcpConfigPath())));
    }

    @Test
    void knowledgeMcpAllowlistMatchesImplementedServerToolsContract() throws Exception {
        // camel-kit-knowledge is a sibling repository, so keep its tool contract local and explicit here.
        List<String> implementedKnowledgeTools = List.of(
                "camel_docs_component_info",
                "camel_docs_search",
                "camel_docs_cve_search",
                "camel_docs_release_info",
                "camel_docs_jira_lookup");
        assertEquals(implementedKnowledgeTools, WorkflowManifestLoader.loadDefault()
                .mcpServer("camel-knowledge")
                .allowedTools());

        ObjectMapper mapper = new ObjectMapper();
        for (String agentName : List.of("bob", "claude", COPILOT, "gemini", "qwen", "opencode", PI)) {
            InitContext ctx = createContext(agentName);
            new DefaultGenerator().generate(ctx);

            JsonNode knowledgeServer = readKnowledgeServerConfig(mapper, agentName);
            if (COPILOT.equals(agentName)) {
                assertEquals(implementedKnowledgeTools, jsonArrayToList(knowledgeServer.path("tools")),
                        agentName + " tools must only include implemented Knowledge MCP tools");
            } else if (PI.equals(agentName)) {
                assertEquals(implementedKnowledgeTools, jsonArrayToList(knowledgeServer.path("directTools")),
                        agentName + " directTools must only include implemented Knowledge MCP tools");
            } else {
                assertEquals(implementedKnowledgeTools, jsonArrayToList(knowledgeServer.path("autoApprove")),
                        agentName + " autoApprove must only include implemented Knowledge MCP tools");
                assertEquals(implementedKnowledgeTools, jsonArrayToList(knowledgeServer.path("alwaysAllow")),
                        agentName + " alwaysAllow must only include implemented Knowledge MCP tools");
            }
        }
    }

    @Test
    void generatesAgentsMd() throws Exception {
        InitContext ctx = createContext("bob");
        new DefaultGenerator().generate(ctx);

        Path agentsMd = ctx.projectDir().resolve("AGENTS.md");
        assertTrue(Files.exists(agentsMd));
        String content = Files.readString(agentsMd);
        assertTrue(content.contains("/camel-start"));
        assertTrue(content.contains("/camel-brainstorm"));
        assertTrue(content.contains("Laws"));
        assertTrue(content.contains("config.properties"));
    }

    @Test
    void copiesIronLawsFile() throws Exception {
        InitContext ctx = createContext("bob");
        new DefaultGenerator().generate(ctx);

        Path ironLaws = ctx.skillsDir().resolve("shared/iron-laws.md");
        assertTrue(Files.exists(ironLaws));
    }

    @Test
    void appliesSkillLevelTraits() throws Exception {
        InitContext ctx = createContext("claude");
        new ClaudeGenerator().generate(ctx);

        Path brainstormSkill = ctx.skillsDir().resolve("camel-brainstorm/SKILL.md");
        String content = Files.readString(brainstormSkill);
        assertTrue(content.contains("<!-- TRAIT:claude -->"), "Sentinel should be present");
        assertTrue(content.contains("AskUserQuestion"), "Claude trait content should be appended");
    }

    @Test
    void traitsAreIdempotent() throws Exception {
        InitContext ctx = createContext("claude");
        ClaudeGenerator generator = new ClaudeGenerator();
        generator.generate(ctx);
        generator.generate(ctx);

        Path brainstormSkill = ctx.skillsDir().resolve("camel-brainstorm/SKILL.md");
        String content = Files.readString(brainstormSkill);
        int count = content.split("<!-- TRAIT:claude -->").length - 1;
        assertEquals(1, count, "Trait sentinel should appear exactly once after double init");
    }

    @Test
    void appliesGuideLevelTraits() throws Exception {
        InitContext ctx = createContext("claude");
        new ClaudeGenerator().generate(ctx);

        Path implementerGuide = ctx.skillsDir().resolve("camel-execute/guides/implementer-context.md");
        assertTrue(Files.exists(implementerGuide), "Guide-level trait should create implementer-context.md");
        String content = Files.readString(implementerGuide);
        assertTrue(content.length() > 0, "Guide should have content");
    }

    @Test
    void generatesShipCommand() throws Exception {
        InitContext ctx = createContext("bob");
        new DefaultGenerator().generate(ctx);

        assertTrue(Files.exists(ctx.commandsDir().resolve("camel-ship.md")));
        String content = Files.readString(ctx.commandsDir().resolve("camel-ship.md"));
        assertTrue(content.contains("SKILL.md"));
    }

    @Test
    void copiesShipSkill() throws Exception {
        InitContext ctx = createContext("bob");
        new DefaultGenerator().generate(ctx);

        assertTrue(Files.exists(ctx.skillsDir().resolve("camel-ship/SKILL.md")));
        assertTrue(Files.isDirectory(ctx.skillsDir().resolve("camel-ship/guides")));
        assertTrue(Files.exists(ctx.skillsDir().resolve("camel-ship/guides/oversight-matrix.md")));
        assertTrue(Files.exists(ctx.skillsDir().resolve("camel-ship/guides/state-management.md")));
        assertTrue(Files.exists(ctx.skillsDir().resolve("camel-ship/guides/auto-fix-loop.md")));
    }

    @Test
    void generatesDebugCommand() throws Exception {
        InitContext ctx = createContext("bob");
        new DefaultGenerator().generate(ctx);

        assertTrue(Files.exists(ctx.commandsDir().resolve("camel-debug.md")));
        String content = Files.readString(ctx.commandsDir().resolve("camel-debug.md"));
        assertTrue(content.contains("SKILL.md"));
    }

    @Test
    void copiesDebugSkill() throws Exception {
        InitContext ctx = createContext("bob");
        new DefaultGenerator().generate(ctx);

        assertTrue(Files.exists(ctx.skillsDir().resolve("camel-debug/SKILL.md")));
        assertTrue(Files.isDirectory(ctx.skillsDir().resolve("camel-debug/guides")));
        assertTrue(Files.exists(ctx.skillsDir().resolve("camel-debug/guides/debug-workflow.md")));
    }

    @Test
    void substitutesVersionPlaceholdersInSkillFiles() throws Exception {
        InitContext ctx = createContext("bob");
        new DefaultGenerator().generate(ctx);

        Path versionSelection = ctx.skillsDir().resolve("camel-brainstorm/guides/version-selection.md");
        assertTrue(Files.exists(versionSelection));
        String content = Files.readString(versionSelection);

        DistributionConfig dist = DistributionConfig.loadFromClasspathOrDefaults();
        var quarkusMappings = dist.quarkusPlatformMappings();
        var springBootMappings = dist.springBootMappings();
        assertFalse(quarkusMappings.isEmpty(), "Expected explicit Quarkus platform mappings");
        assertFalse(springBootMappings.isEmpty(), "Expected explicit Spring Boot mappings");
        assertFalse(content.contains("{QUARKUS_PLATFORM_VERSION}"),
                "Placeholder should be substituted");
        assertTrue(content.contains(dist.quarkusPlatformVersion()),
                "Resolved value should appear");
        assertFalse(content.contains("{QUARKUS_PLATFORM_TABLE}"),
                "Table placeholder should be substituted");
        for (var entry : quarkusMappings.entrySet()) {
            assertTrue(content.contains(entry.getValue()),
                    "Mapping for " + entry.getKey() + " should appear in table");
        }
        assertFalse(content.contains("{SPRING_BOOT_VERSION}"),
                "Spring Boot version placeholder should be substituted");
        assertTrue(content.contains(dist.springBootVersion()),
                "Resolved Spring Boot value should appear");
        assertFalse(content.contains("{SPRING_BOOT_VERSION_TABLE}"),
                "Spring Boot table placeholder should be substituted");
        for (var entry : springBootMappings.entrySet()) {
            assertTrue(content.contains(entry.getValue()),
                    "Spring Boot mapping for " + entry.getKey() + " should appear in table");
        }
    }

    @Test
    void wrapsTomlForGemini() throws Exception {
        InitContext ctx = createContext("gemini");
        new DefaultGenerator().generate(ctx);

        Path geminiCmd = ctx.commandsDir().resolve("camel-migrate.toml");
        assertTrue(Files.exists(geminiCmd));
        String content = Files.readString(geminiCmd);
        assertTrue(content.contains("description ="));
        assertTrue(content.contains("prompt ="));
    }

    private JsonNode readKnowledgeServerConfig(ObjectMapper mapper, String agentName) throws IOException {
        AgentConfig agent = AgentRegistry.get(agentName);
        assertNotNull(agent, "Unknown agent: " + agentName);
        Path configPath = tempDir.resolve(agent.mcpConfigPath());
        JsonNode config = mapper.readTree(configPath.toFile());
        return config.path(agent.mcpServerContainerKey()).path("camel-knowledge");
    }

    private List<String> jsonArrayToList(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(value -> values.add(value.asText()));
        return values;
    }
}
