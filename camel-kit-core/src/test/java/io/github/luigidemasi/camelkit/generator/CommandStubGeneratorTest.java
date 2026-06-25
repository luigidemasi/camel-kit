package io.github.luigidemasi.camelkit.generator;

import java.nio.file.Files;
import java.nio.file.Path;

import io.github.luigidemasi.camelkit.config.AgentConfig;
import io.github.luigidemasi.camelkit.config.AgentRegistry;
import io.github.luigidemasi.camelkit.output.Printer;
import io.github.luigidemasi.camelkit.workflow.WorkflowManifestLoader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class CommandStubGeneratorTest {

    @TempDir
    Path tempDir;

    @Test
    void writesGeneratedManifestCommandsOnly() throws Exception {
        InitContext ctx = createContext("gemini");
        Files.createDirectories(ctx.commandsDir());

        new CommandStubGenerator().generate(ctx, WorkflowManifestLoader.loadDefault());

        Path startCommand = ctx.commandsDir().resolve("camel-start.toml");
        assertTrue(Files.isRegularFile(startCommand));
        assertFalse(Files.exists(ctx.commandsDir().resolve("camel-implement.toml")));

        String content = Files.readString(startCommand);
        assertTrue(content.contains("description = \"Camel-Kit start command\""));
        assertTrue(content.contains("Read .gemini/skills/camel-start/SKILL.md and follow those instructions"));
    }

    private InitContext createContext(String agentName) {
        AgentConfig agent = AgentRegistry.get(agentName);
        String agentBaseFolder = agent.folder().substring(0, agent.folder().lastIndexOf("/"));
        Path commandsDir = tempDir.resolve(agent.folder());
        Path skillsDir = tempDir.resolve(agentBaseFolder + "/skills");
        return new InitContext(
                agent, agentName, commandsDir, skillsDir, tempDir,
                "camel-kit", Printer.noop());
    }
}
