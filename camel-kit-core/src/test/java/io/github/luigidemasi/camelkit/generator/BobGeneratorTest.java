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

class BobGeneratorTest {

    @TempDir Path tempDir;

    private InitContext createContext() {
        AgentConfig agent = AgentRegistry.get("bob");
        String agentBaseFolder = agent.folder().substring(0, agent.folder().lastIndexOf("/"));
        Path commandsDir = tempDir.resolve(agent.folder());
        Path skillsDir = tempDir.resolve(agentBaseFolder + "/skills");
        return new InitContext(agent, "bob", commandsDir, skillsDir, tempDir,
            "camel-kit", CamelKitMain.LATEST_CAMEL_LTS_VERSION, Printer.noop());
    }

    @Test
    void generatesCustomModes() throws Exception {
        InitContext ctx = createContext();
        new BobGenerator().generate(ctx);

        Path modesFile = tempDir.resolve(".bob/custom_modes.yaml");
        assertTrue(Files.exists(modesFile));
        String content = Files.readString(modesFile);
        assertTrue(content.contains("camel-brainstorm"));
        assertTrue(content.contains("camel-plan"));
        assertTrue(content.contains("camel-implement"));
        assertTrue(content.contains("camel-validate"));
        assertTrue(content.contains("camel-test"));
    }

    @Test
    void generatesSharedRules() throws Exception {
        InitContext ctx = createContext();
        new BobGenerator().generate(ctx);

        Path rulesDir = tempDir.resolve(".bob/rules");
        assertTrue(Files.isDirectory(rulesDir));
        Path ironLaws = rulesDir.resolve("iron-laws.md");
        assertTrue(Files.exists(ironLaws));
        String content = Files.readString(ironLaws);
        assertTrue(content.contains("MCP Catalog Verification"));
    }

    @Test
    void generatesModeSpecificRules() throws Exception {
        InitContext ctx = createContext();
        new BobGenerator().generate(ctx);

        assertTrue(Files.isDirectory(tempDir.resolve(".bob/rules-camel-brainstorm")));
        assertTrue(Files.isDirectory(tempDir.resolve(".bob/rules-camel-implement")));
        assertTrue(Files.isDirectory(tempDir.resolve(".bob/rules-camel-validate")));
        assertTrue(Files.isDirectory(tempDir.resolve(".bob/rules-camel-test")));
        assertTrue(Files.isDirectory(tempDir.resolve(".bob/rules-camel-plan")));

        String brainstormRules = Files.readString(
            tempDir.resolve(".bob/rules-camel-brainstorm/interview-gates.md"));
        assertTrue(brainstormRules.contains("ONE question at a time"));
    }

    @Test
    void generatesMonolithicSkills() throws Exception {
        InitContext ctx = createContext();
        new BobGenerator().generate(ctx);

        Path migrateSkill = ctx.skillsDir().resolve("camel-migrate/SKILL.md");
        assertTrue(Files.exists(migrateSkill));
        String content = Files.readString(migrateSkill);
        // Should contain gate content, not the base delegation
        assertTrue(content.contains("<Steps>"));
        assertTrue(content.contains("Switch to"));
        assertTrue(content.contains("CHECKPOINT"));
        assertTrue(content.contains("APPROVAL GATE"));
        // Should NOT contain the base "Invoke camel-brainstorm" delegation
        assertFalse(content.contains("Invoke `camel-brainstorm/SKILL.md`"));
    }

    @Test
    void copiesGuideFiles() throws Exception {
        InitContext ctx = createContext();
        new BobGenerator().generate(ctx);

        // Guide files should still be copied for reference
        assertTrue(Files.isDirectory(ctx.skillsDir().resolve("camel-migrate/guides")));
        assertTrue(Files.isDirectory(ctx.skillsDir().resolve("camel-brainstorm/guides")));
    }

    @Test
    void preservesSlashCommands() throws Exception {
        InitContext ctx = createContext();
        new BobGenerator().generate(ctx);

        assertTrue(Files.exists(ctx.commandsDir().resolve("camel-migrate.md")));
        assertTrue(Files.exists(ctx.commandsDir().resolve("camel-implement.md")));
    }

    @Test
    void generatesMcpConfig() throws Exception {
        InitContext ctx = createContext();
        new BobGenerator().generate(ctx);

        assertTrue(Files.exists(tempDir.resolve(".bob/mcp.json")));
    }

    @Test
    void generatesIronLawsRule() throws Exception {
        InitContext ctx = createContext();
        new BobGenerator().generate(ctx);

        Path ironLaws = tempDir.resolve(".bob/rules/iron-laws.md");
        assertTrue(Files.exists(ironLaws));
        String content = Files.readString(ironLaws);
        assertTrue(content.contains("MCP Catalog Verification"));
    }

    @Test
    void substitutesQuteVariables() throws Exception {
        InitContext ctx = createContext();
        new BobGenerator().generate(ctx);

        Path migrateSkill = ctx.skillsDir().resolve("camel-migrate/SKILL.md");
        String content = Files.readString(migrateSkill);
        assertTrue(content.contains("camel-kit graph"));
        assertFalse(content.contains("{commandPrefix}"));
    }
}
