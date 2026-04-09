package io.github.luigidemasi.camelkit.generator;

import io.github.luigidemasi.camelkit.config.AgentConfig;
import io.github.luigidemasi.camelkit.config.AgentRegistry;
import io.github.luigidemasi.camelkit.output.Printer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class QwenGeneratorTest {

    @TempDir Path tempDir;

    private InitContext createContext() {
        AgentConfig agent = AgentRegistry.get("qwen");
        String agentBaseFolder = agent.folder().substring(0, agent.folder().lastIndexOf("/"));
        Path commandsDir = tempDir.resolve(agent.folder());
        Path skillsDir = tempDir.resolve(agentBaseFolder + "/skills");
        return new InitContext(agent, "qwen", commandsDir, skillsDir, tempDir,
            "camel-kit", "4.14.4.redhat-00008", false, Printer.noop());
    }

    @Test
    void generatesQwenMd() throws Exception {
        InitContext ctx = createContext();
        new QwenGenerator().generate(ctx);

        Path qwenMd = tempDir.resolve("QWEN.md");
        assertTrue(Files.exists(qwenMd));
        String content = Files.readString(qwenMd);
        assertTrue(content.contains("Iron Laws"));
        assertTrue(content.contains("4.14.4.redhat-00008"));
        assertTrue(content.contains("camel-kit graph stats"));
    }

    @Test
    void generatesSubAgents() throws Exception {
        InitContext ctx = createContext();
        new QwenGenerator().generate(ctx);

        Path agentsDir = tempDir.resolve(".qwen/agents");
        assertTrue(Files.isDirectory(agentsDir));
        assertTrue(Files.exists(agentsDir.resolve("camel-brainstormer.md")));
        assertTrue(Files.exists(agentsDir.resolve("camel-planner.md")));
        assertTrue(Files.exists(agentsDir.resolve("camel-implementer.md")));
        assertTrue(Files.exists(agentsDir.resolve("camel-validator.md")));
        assertTrue(Files.exists(agentsDir.resolve("camel-tester.md")));
        assertTrue(Files.exists(agentsDir.resolve("camel-migrator.md")));
        assertTrue(Files.exists(agentsDir.resolve("camel-executor.md")));
    }

    @Test
    void generatesQwenIgnore() throws Exception {
        InitContext ctx = createContext();
        new QwenGenerator().generate(ctx);

        Path qwenIgnore = tempDir.resolve(".qwenignore");
        assertTrue(Files.exists(qwenIgnore));
        String content = Files.readString(qwenIgnore);
        assertTrue(content.contains("target/"));
    }

    @Test
    void subAgentHasMustBeUsedDescription() throws Exception {
        InitContext ctx = createContext();
        new QwenGenerator().generate(ctx);

        String content = Files.readString(
            tempDir.resolve(".qwen/agents/camel-brainstormer.md"));
        assertTrue(content.contains("MUST BE USED"));
        assertTrue(content.contains("discovering integration requirements"));
    }

    @Test
    void readOnlySubAgentHasToolWhitelist() throws Exception {
        InitContext ctx = createContext();
        new QwenGenerator().generate(ctx);

        String content = Files.readString(
            tempDir.resolve(".qwen/agents/camel-validator.md"));
        assertTrue(content.contains("read_file"));
        assertTrue(content.contains("grep_search"));
        assertFalse(content.contains("write_file"));
        assertFalse(content.contains("edit"));
    }

    @Test
    void fullAccessSubAgentOmitsToolList() throws Exception {
        InitContext ctx = createContext();
        new QwenGenerator().generate(ctx);

        String content = Files.readString(
            tempDir.resolve(".qwen/agents/camel-implementer.md"));
        assertFalse(content.contains("tools:"));
    }

    @Test
    void overridesCommandsWithSubAgentDispatch() throws Exception {
        InitContext ctx = createContext();
        new QwenGenerator().generate(ctx);

        String content = Files.readString(
            ctx.commandsDir().resolve("camel-validate.md"));
        assertTrue(content.contains("Delegate to the camel-validator sub-agent"));
        assertFalse(content.contains("Read .qwen/skills"));
    }

    @Test
    void preservesBaseSkillBehavior() throws Exception {
        InitContext ctx = createContext();
        new QwenGenerator().generate(ctx);

        // Standard commands without sub-agents keep default content
        String content = Files.readString(
            ctx.commandsDir().resolve("camel-knowledge.md"));
        assertTrue(content.contains("Read .qwen/skills"));

        // MCP config exists
        assertTrue(Files.exists(tempDir.resolve(".qwen/settings.json")));
    }
}
