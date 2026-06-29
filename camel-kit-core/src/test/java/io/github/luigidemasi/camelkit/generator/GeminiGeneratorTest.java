package io.github.luigidemasi.camelkit.generator;

import java.nio.file.Files;
import java.nio.file.Path;

import io.github.luigidemasi.camelkit.config.AgentConfig;
import io.github.luigidemasi.camelkit.config.AgentRegistry;
import io.github.luigidemasi.camelkit.output.Printer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class GeminiGeneratorTest {

    @TempDir
    Path tempDir;

    private InitContext createContext() {
        AgentConfig agent = AgentRegistry.get("gemini");
        String agentBaseFolder = agent.folder().substring(0, agent.folder().lastIndexOf("/"));
        Path commandsDir = tempDir.resolve(agent.folder());
        Path skillsDir = tempDir.resolve(agentBaseFolder + "/skills");
        return new InitContext(
                agent, "gemini", commandsDir, skillsDir, tempDir,
                "camel-kit", "5.0.0-M2", Printer.noop());
    }

    @Test
    void generatesGeminiMdWithImports() throws Exception {
        InitContext ctx = createContext();
        new GeminiGenerator().generate(ctx);

        Path geminiMd = tempDir.resolve("GEMINI.md");
        assertTrue(Files.exists(geminiMd));
        String content = Files.readString(geminiMd);
        assertTrue(content.contains("@.gemini/instructions/iron-laws.md"));
        assertTrue(content.contains("@.gemini/instructions/mcp-usage.md"));
        assertTrue(content.contains("@.gemini/instructions/pipeline-overview.md"));
    }

    @Test
    void generatesInstructionFiles() throws Exception {
        InitContext ctx = createContext();
        new GeminiGenerator().generate(ctx);

        Path instructionsDir = tempDir.resolve(".gemini/instructions");
        assertTrue(Files.isDirectory(instructionsDir));
        assertTrue(Files.exists(instructionsDir.resolve("iron-laws.md")));
        assertTrue(Files.exists(instructionsDir.resolve("mcp-usage.md")));
        assertTrue(Files.exists(instructionsDir.resolve("pipeline-overview.md")));

        // Verify Qute variable substitution in instruction files
        String pipelineContent = Files.readString(instructionsDir.resolve("pipeline-overview.md"));
        assertTrue(pipelineContent.contains("camel-kit"));
        assertFalse(pipelineContent.contains("{COMMAND_PREFIX}"));
    }

    @Test
    void generatesPolicy() throws Exception {
        InitContext ctx = createContext();
        new GeminiGenerator().generate(ctx);

        Path policyFile = tempDir.resolve(".gemini/policies/camel-kit.toml");
        assertTrue(Files.exists(policyFile));
        String content = Files.readString(policyFile);
        assertTrue(content.contains("mcp_camel_*"));
        assertTrue(content.contains("mcp_camel-knowledge_*"));
        assertTrue(content.contains("commandRegex"));
    }

    @Test
    void generatesSubAgents() throws Exception {
        InitContext ctx = createContext();
        new GeminiGenerator().generate(ctx);

        Path agentsDir = tempDir.resolve(".gemini/agents");
        assertTrue(Files.isDirectory(agentsDir));
        assertTrue(Files.exists(agentsDir.resolve("camel-brainstormer.md")));
        assertTrue(Files.exists(agentsDir.resolve("camel-planner.md")));
        assertTrue(Files.exists(agentsDir.resolve("camel-implementer.md")));
        assertTrue(Files.exists(agentsDir.resolve("camel-validator.md")));
        assertTrue(Files.exists(agentsDir.resolve("camel-tester.md")));
        assertTrue(Files.exists(agentsDir.resolve("camel-migrator.md")));
        // No executor subagent — execute runs in main agent context
        assertFalse(Files.exists(agentsDir.resolve("camel-executor.md")));
    }

    @Test
    void subAgentHasMcpWildcard() throws Exception {
        InitContext ctx = createContext();
        new GeminiGenerator().generate(ctx);

        String content = Files.readString(
                tempDir.resolve(".gemini/agents/camel-validator.md"));
        assertTrue(content.contains("mcp_camel_*"));
        assertTrue(content.contains("max_turns: 20"));
    }

    @Test
    void generatesGeminiIgnore() throws Exception {
        InitContext ctx = createContext();
        new GeminiGenerator().generate(ctx);

        Path geminiIgnore = tempDir.resolve(".geminiignore");
        assertTrue(Files.exists(geminiIgnore));
        String content = Files.readString(geminiIgnore);
        assertTrue(content.contains("target/"));
    }

    @Test
    void overridesCommandsWithTomlSubAgentDispatch() throws Exception {
        InitContext ctx = createContext();
        new GeminiGenerator().generate(ctx);

        String content = Files.readString(
                ctx.commandsDir().resolve("camel-validate.toml"));
        assertTrue(content.contains("camel-validator subagent"));
        assertTrue(content.contains("{{args}}"));
    }

    @Test
    void executeCommandRunsInMainAgentContext() throws Exception {
        InitContext ctx = createContext();
        new GeminiGenerator().generate(ctx);

        String content = Files.readString(
                ctx.commandsDir().resolve("camel-execute.toml"));
        // Execute runs in main agent, NOT a subagent
        assertTrue(content.contains("orchestrator"));
        assertFalse(content.contains("camel-executor subagent"));
    }
}
