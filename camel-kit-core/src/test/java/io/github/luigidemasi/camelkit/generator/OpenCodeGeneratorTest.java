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

class OpenCodeGeneratorTest {

    @TempDir Path tempDir;

    private InitContext createContext() {
        AgentConfig agent = AgentRegistry.get("opencode");
        String agentBaseFolder = agent.folder().substring(0, agent.folder().lastIndexOf("/"));
        Path commandsDir = tempDir.resolve(agent.folder());
        Path skillsDir = tempDir.resolve(agentBaseFolder + "/skills");
        return new InitContext(agent, "opencode", commandsDir, skillsDir, tempDir,
            "camel-kit", CamelKitMain.LATEST_CAMEL_LTS_VERSION, false, Printer.noop());
    }

    @Test
    void generatesAgentsMd() throws Exception {
        InitContext ctx = createContext();
        new OpenCodeGenerator().generate(ctx);

        Path agentsMd = tempDir.resolve("AGENTS.md");
        assertTrue(Files.exists(agentsMd));
        String content = Files.readString(agentsMd);
        assertTrue(content.contains("Iron Laws"));
        assertTrue(content.contains(CamelKitMain.LATEST_CAMEL_LTS_VERSION));
        assertTrue(content.contains("camel-kit graph stats"));
    }

    @Test
    void generatesAgentDefinitions() throws Exception {
        InitContext ctx = createContext();
        new OpenCodeGenerator().generate(ctx);

        Path agentsDir = tempDir.resolve(".opencode/agents");
        assertTrue(Files.isDirectory(agentsDir));
        assertTrue(Files.exists(agentsDir.resolve("brainstormer.md")));
        assertTrue(Files.exists(agentsDir.resolve("planner.md")));
        assertTrue(Files.exists(agentsDir.resolve("implementer.md")));
        assertTrue(Files.exists(agentsDir.resolve("validator.md")));
        assertTrue(Files.exists(agentsDir.resolve("tester.md")));
        assertTrue(Files.exists(agentsDir.resolve("migrator.md")));
        assertTrue(Files.exists(agentsDir.resolve("executor.md")));
    }

    @Test
    void brainstormerHasEditDeny() throws Exception {
        InitContext ctx = createContext();
        new OpenCodeGenerator().generate(ctx);

        String content = Files.readString(
            tempDir.resolve(".opencode/agents/brainstormer.md"));
        assertTrue(content.contains("edit: deny"));
        assertTrue(content.contains("mode: subagent"));
        assertTrue(content.contains("steps: 20"));
    }

    @Test
    void implementerHasFullAccess() throws Exception {
        InitContext ctx = createContext();
        new OpenCodeGenerator().generate(ctx);

        String content = Files.readString(
            tempDir.resolve(".opencode/agents/implementer.md"));
        assertTrue(content.contains("edit: allow"));
        assertTrue(content.contains("\"rm -rf *\": deny"));
        assertTrue(content.contains("steps: 50"));
    }

    @Test
    void testerHasPathScopedEdits() throws Exception {
        InitContext ctx = createContext();
        new OpenCodeGenerator().generate(ctx);

        String content = Files.readString(
            tempDir.resolve(".opencode/agents/tester.md"));
        assertTrue(content.contains("\"src/test/**\": allow"));
        assertTrue(content.contains("\"test/**\": allow"));
        assertTrue(content.contains("\"*\": ask"));
    }

    @Test
    void executorHasTaskAllow() throws Exception {
        InitContext ctx = createContext();
        new OpenCodeGenerator().generate(ctx);

        String content = Files.readString(
            tempDir.resolve(".opencode/agents/executor.md"));
        assertTrue(content.contains("task:"));
        assertTrue(content.contains("\"*\": allow"));
        assertTrue(content.contains("steps: 100"));
    }

    @Test
    void overridesCommandsWithAgentDispatch() throws Exception {
        InitContext ctx = createContext();
        new OpenCodeGenerator().generate(ctx);

        String content = Files.readString(
            ctx.commandsDir().resolve("camel-validate.md"));
        assertTrue(content.contains("@validator"));
        assertFalse(content.contains("Read .opencode/skills"));
    }

    @Test
    void preservesBaseSkillBehavior() throws Exception {
        InitContext ctx = createContext();
        new OpenCodeGenerator().generate(ctx);

        // Standard commands without agents keep default content
        String content = Files.readString(
            ctx.commandsDir().resolve("camel-knowledge.md"));
        assertTrue(content.contains("Read .opencode/skills"));

        // MCP config exists
        assertTrue(Files.exists(tempDir.resolve("opencode.json")));
    }
}
