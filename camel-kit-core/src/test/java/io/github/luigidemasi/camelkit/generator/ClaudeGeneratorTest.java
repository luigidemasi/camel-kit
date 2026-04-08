package io.github.luigidemasi.camelkit.generator;

import io.github.luigidemasi.camelkit.config.AgentConfig;
import io.github.luigidemasi.camelkit.config.AgentRegistry;
import io.github.luigidemasi.camelkit.output.Printer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ClaudeGeneratorTest {

    @TempDir Path tempDir;

    private InitContext createContext() {
        AgentConfig agent = AgentRegistry.get("claude");
        String agentBaseFolder = agent.folder().substring(0, agent.folder().lastIndexOf("/"));
        Path commandsDir = tempDir.resolve(agent.folder());
        Path skillsDir = tempDir.resolve(agentBaseFolder + "/skills");
        return new InitContext(agent, "claude", commandsDir, skillsDir, tempDir,
            "camel-kit", "4.14.4.redhat-00008", false, Printer.noop());
    }

    @Test
    void generatesClaudeMd() throws Exception {
        InitContext ctx = createContext();
        new ClaudeGenerator().generate(ctx);

        Path claudeMd = tempDir.resolve("CLAUDE.md");
        assertTrue(Files.exists(claudeMd));
        String content = Files.readString(claudeMd);
        assertTrue(content.contains("Iron Laws"));
        assertTrue(content.contains("camel-kit graph stats"));
    }

    @Test
    void appendsParallelDispatch() throws Exception {
        InitContext ctx = createContext();
        new ClaudeGenerator().generate(ctx);

        Path implementSkill = ctx.skillsDir().resolve("camel-implement/SKILL.md");
        assertTrue(Files.exists(implementSkill));
        String content = Files.readString(implementSkill);
        assertTrue(content.contains("Parallel Route Implementation"));
        assertTrue(content.contains("route-topology"));
    }

    @Test
    void preservesBaseSkillBehavior() throws Exception {
        InitContext ctx = createContext();
        new ClaudeGenerator().generate(ctx);

        // Standard skills still have dispatch block
        Path brainstormSkill = ctx.skillsDir().resolve("camel-brainstorm/SKILL.md");
        assertTrue(Files.exists(brainstormSkill));
        String content = Files.readString(brainstormSkill);
        assertTrue(content.contains("Dispatch"));

        // Slash commands still exist
        assertTrue(Files.exists(ctx.commandsDir().resolve("camel-migrate.md")));
    }
}
