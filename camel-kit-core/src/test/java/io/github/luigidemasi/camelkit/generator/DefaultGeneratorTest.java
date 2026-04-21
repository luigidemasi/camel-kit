package io.github.luigidemasi.camelkit.generator;

import io.github.luigidemasi.camelkit.CamelKitMain;
import io.github.luigidemasi.camelkit.config.AgentConfig;
import io.github.luigidemasi.camelkit.config.AgentRegistry;
import io.github.luigidemasi.camelkit.output.Printer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class DefaultGeneratorTest {

    @TempDir Path tempDir;

    private InitContext createContext(String agentName) {
        AgentConfig agent = AgentRegistry.get(agentName);
        String agentBaseFolder = agent.folder().substring(0, agent.folder().lastIndexOf("/"));
        Path commandsDir = tempDir.resolve(agent.folder());
        Path skillsDir = tempDir.resolve(agentBaseFolder + "/skills");
        return new InitContext(agent, agentName, commandsDir, skillsDir, tempDir,
            "camel-kit", CamelKitMain.LATEST_CAMEL_LTS_VERSION, false, Printer.noop());
    }

    @Test
    void generatesSlashCommands() throws Exception {
        InitContext ctx = createContext("bob");
        new DefaultGenerator().generate(ctx);

        assertTrue(Files.exists(ctx.commandsDir().resolve("camel-migrate.md")));
        assertTrue(Files.exists(ctx.commandsDir().resolve("camel-implement.md")));
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
    void selectsCommunityVariantFiles() throws Exception {
        InitContext ctx = createContext("bob");
        new DefaultGenerator().generate(ctx);

        // Verify community iron-laws was selected
        Path ironLaws = ctx.skillsDir().resolve("shared/iron-laws.md");
        if (Files.exists(ironLaws)) {
            String content = Files.readString(ironLaws);
            assertFalse(content.contains("Build Only"),
                "Community variant should contain only community iron laws");
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
}
