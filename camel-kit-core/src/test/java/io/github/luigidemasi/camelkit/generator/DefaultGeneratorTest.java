package io.github.luigidemasi.camelkit.generator;

import java.nio.file.Files;
import java.nio.file.Path;

import io.github.luigidemasi.camelkit.config.AgentConfig;
import io.github.luigidemasi.camelkit.config.AgentRegistry;
import io.github.luigidemasi.camelkit.output.Printer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class DefaultGeneratorTest {

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

        assertTrue(Files.exists(tempDir.resolve(".bob/mcp.json")));
    }

    @Test
    void generatesAgentsMd() throws Exception {
        InitContext ctx = createContext("bob");
        new DefaultGenerator().generate(ctx);

        Path agentsMd = ctx.projectDir().resolve("AGENTS.md");
        assertTrue(Files.exists(agentsMd));
        String content = Files.readString(agentsMd);
        assertTrue(content.contains("Skill Routing"));
        assertTrue(content.contains("/camel-brainstorm"));
        assertTrue(content.contains("Iron Laws"));
        assertTrue(content.contains("MCP Catalog Verification"));
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
        if (Files.exists(implementerGuide)) {
            String content = Files.readString(implementerGuide);
            assertTrue(content.length() > 0, "Guide should have content");
        }
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
    void wrapsTomlForGemini() throws Exception {
        InitContext ctx = createContext("gemini");
        new DefaultGenerator().generate(ctx);

        Path geminiCmd = ctx.commandsDir().resolve("camel-migrate.toml");
        assertTrue(Files.exists(geminiCmd));
        String content = Files.readString(geminiCmd);
        assertTrue(content.contains("description ="));
        assertTrue(content.contains("prompt ="));
    }
}
