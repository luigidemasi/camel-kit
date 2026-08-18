package io.github.luigidemasi.camelkit.generator;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import io.github.luigidemasi.camelkit.config.AgentConfig;
import io.github.luigidemasi.camelkit.config.AgentDescriptor;
import io.github.luigidemasi.camelkit.config.AgentRegistry;
import io.github.luigidemasi.camelkit.output.Printer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class CopilotGeneratorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> INTERNAL_SKILLS = Set.of(
            "camel-design",
            "camel-implement",
            "camel-test",
            "camel-verify");

    @TempDir
    Path tempDir;

    private InitContext createContext() {
        AgentConfig agent = AgentRegistry.get("copilot");
        Path skillsDir = tempDir.resolve(agent.skillsDirectory());
        return new InitContext(
                agent, "copilot", skillsDir, skillsDir, tempDir,
                "camel-kit", Printer.noop());
    }

    @Test
    void generatesCopilotInstructionsAndAgentsMdBridge() throws Exception {
        InitContext ctx = createContext();
        new CopilotGenerator().generate(ctx);

        Path instructions = tempDir.resolve(".github/copilot-instructions.md");
        assertTrue(Files.exists(instructions));
        String content = Files.readString(instructions);
        assertTrue(content.contains("camel-start"));
        assertTrue(content.contains(".github/skills"));
        assertTrue(content.contains(".github/mcp.json"));
        assertFalse(content.contains("{COMMAND_PREFIX}"));

        String agentsMd = Files.readString(tempDir.resolve("AGENTS.md"));
        assertTrue(agentsMd.contains("GitHub Copilot CLI"));
        assertTrue(agentsMd.contains("camel-start"));
        assertTrue(agentsMd.contains("/camel-start"));
    }

    @Test
    void installsEveryTemplateDeclaredByCopilotDescriptor() throws Exception {
        InitContext ctx = createContext();
        new CopilotGenerator().generate(ctx);

        AgentDescriptor descriptor = AgentRegistry.descriptor("copilot");
        for (AgentDescriptor.TemplateInstall template : descriptor.templates()) {
            assertTrue(Files.isRegularFile(tempDir.resolve(template.target())),
                    "Missing generated Copilot template target " + template.target());
        }
    }

    @Test
    void generatesProjectSkillsInGithubSkillsDirectory() throws Exception {
        InitContext ctx = createContext();
        new CopilotGenerator().generate(ctx);

        assertTrue(Files.exists(tempDir.resolve(".github/skills/camel-start/SKILL.md")));
        assertTrue(Files.exists(tempDir.resolve(".github/skills/camel-execute/SKILL.md")));
        assertTrue(Files.exists(tempDir.resolve(".github/skills/shared/iron-laws.md")));
    }

    @Test
    void marksInternalSkillsAsNotUserOrModelInvocableForCopilot() throws Exception {
        InitContext ctx = createContext();
        new CopilotGenerator().generate(ctx);

        for (String skillName : INTERNAL_SKILLS) {
            String skill = Files.readString(tempDir.resolve(".github/skills/" + skillName + "/SKILL.md"));
            assertTrue(skill.contains("user-invocable: false"), skillName + " must not be user invocable");
            assertTrue(skill.contains("disable-model-invocation: true"),
                    skillName + " must not be model invocable");
        }

        String start = Files.readString(tempDir.resolve(".github/skills/camel-start/SKILL.md"));
        assertFalse(start.contains("disable-model-invocation: true"));
    }

    @Test
    void generatesCustomAgents() throws Exception {
        InitContext ctx = createContext();
        new CopilotGenerator().generate(ctx);

        Path agentsDir = tempDir.resolve(".github/agents");
        assertTrue(Files.isDirectory(agentsDir));
        assertTrue(Files.exists(agentsDir.resolve("camel-planner.agent.md")));
        assertTrue(Files.exists(agentsDir.resolve("camel-implementer.agent.md")));
        assertTrue(Files.exists(agentsDir.resolve("camel-tester.agent.md")));
        assertTrue(Files.exists(agentsDir.resolve("camel-validator.agent.md")));
        assertTrue(Files.exists(agentsDir.resolve("camel-migrator.agent.md")));
        assertTrue(Files.exists(agentsDir.resolve("camel-catalog-researcher.agent.md")));
        assertTrue(Files.exists(agentsDir.resolve("camel-security-reviewer.agent.md")));
    }

    @Test
    void generatedCustomAgentsUseCopilotToolAliasesAndMcpServerPrefixes() throws Exception {
        InitContext ctx = createContext();
        new CopilotGenerator().generate(ctx);

        String implementer = Files.readString(tempDir.resolve(".github/agents/camel-implementer.agent.md"));
        assertTrue(implementer.contains("target: github-copilot"));
        assertTrue(implementer.contains("\"edit\""));
        assertTrue(implementer.contains("\"execute\""));
        assertTrue(implementer.contains("\"camel/*\""));

        String securityReviewer = Files.readString(tempDir.resolve(".github/agents/camel-security-reviewer.agent.md"));
        assertFalse(securityReviewer.contains("\"edit\""));
        assertFalse(securityReviewer.contains("\"execute\""));
    }

    @Test
    void copiesCustomAgentTemplatesVerbatim() throws Exception {
        InitContext ctx = createContext();
        new CopilotGenerator().generate(ctx);

        String generated = Files.readString(tempDir.resolve(".github/agents/camel-implementer.agent.md"));
        assertEquals(resourceText("templates/copilot/agents/camel-implementer.agent.md"), generated);
    }

    @Test
    void generatesCopilotMcpConfigWithToolsSchema() throws Exception {
        InitContext ctx = createContext();
        new CopilotGenerator().generate(ctx);

        Path mcp = tempDir.resolve(".github/mcp.json");
        assertTrue(Files.exists(mcp));
        JsonNode root = MAPPER.readTree(mcp.toFile());
        JsonNode camel = root.path("mcpServers").path("camel");
        assertEquals("stdio", camel.path("type").asText());
        assertEquals("jbang", camel.path("command").asText());
        assertTrue(camel.path("tools").isArray());
        assertFalse(camel.has("autoApprove"));
        assertFalse(camel.has("alwaysAllow"));

        JsonNode citrus = root.path("mcpServers").path("citrus");
        assertTrue(citrus.path("tools").isArray());
    }

    @Test
    void generatesSafetyHook() throws Exception {
        InitContext ctx = createContext();
        new CopilotGenerator().generate(ctx);

        Path hook = tempDir.resolve(".github/hooks/camel-kit-safety.json");
        assertTrue(Files.exists(hook));
        JsonNode root = MAPPER.readTree(hook.toFile());
        assertEquals(1, root.path("version").asInt());
        JsonNode preToolUse = root.path("hooks").path("preToolUse");
        assertTrue(preToolUse.isArray());
        String hookText = Files.readString(hook);
        assertTrue(hookText.contains("git[[:space:]]+push"));
        assertTrue(hookText.contains("permissionDecision"));
    }

    @Test
    void exposesShipThroughTheNativeProjectSkillWithoutUnsupportedCommands() throws Exception {
        InitContext ctx = createContext();
        new CopilotGenerator().generate(ctx);

        assertFalse(Files.exists(tempDir.resolve(".github/commands")));
        String content = Files.readString(tempDir.resolve(".github/skills/camel-ship/SKILL.md"));
        assertTrue(content.contains("camel-kit ship"));
        assertFalse(content.contains("user-invocable: false"));
    }

    private String resourceText(String resourcePath) throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            assertNotNull(in, "Missing test resource " + resourcePath);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
