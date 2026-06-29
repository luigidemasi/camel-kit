package io.github.luigidemasi.camelkit.generator;

import java.nio.file.Files;
import java.nio.file.Path;

import io.github.luigidemasi.camelkit.config.AgentConfig;
import io.github.luigidemasi.camelkit.config.AgentRegistry;
import io.github.luigidemasi.camelkit.output.Printer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class Bob2GeneratorTest {

    @TempDir
    Path tempDir;

    private InitContext createContext() {
        AgentConfig agent = AgentRegistry.get("bob2");
        String agentBaseFolder = agent.folder().substring(0, agent.folder().lastIndexOf("/"));
        Path commandsDir = tempDir.resolve(agent.folder());
        Path skillsDir = tempDir.resolve(agentBaseFolder + "/skills");
        return new InitContext(
                agent, "bob2", commandsDir, skillsDir, tempDir,
                "camel-kit", "5.0.0-M2", Printer.noop());
    }

    @Test
    void generatesBobWorkspaceFilesUnderDotBob() throws Exception {
        InitContext ctx = createContext();
        new Bob2Generator().generate(ctx);

        assertTrue(Files.isDirectory(tempDir.resolve(".bob/commands")));
        assertTrue(Files.isDirectory(tempDir.resolve(".bob/skills")));
        assertTrue(Files.isRegularFile(tempDir.resolve(".bob/mcp.json")));
        assertTrue(Files.isRegularFile(tempDir.resolve(".bob/custom_modes.yaml")));
        assertTrue(Files.isRegularFile(tempDir.resolve(".bob/rules/iron-laws.md")));
        assertTrue(Files.isRegularFile(tempDir.resolve(".bob/rules-camel-execute/execute.md")));
    }

    @Test
    void customModesUseBob2ToolGroupsAndAllowedSubagents() throws Exception {
        InitContext ctx = createContext();
        new Bob2Generator().generate(ctx);

        String content = Files.readString(tempDir.resolve(".bob/custom_modes.yaml"));
        assertTrue(content.contains("- execute"));
        assertTrue(content.contains("- skill"));
        assertTrue(content.contains("- todo"));
        assertTrue(content.contains("- artifact"));
        assertTrue(content.contains("- subagent"));
        assertTrue(content.contains("- mode"));
        assertTrue(content.contains("allowedSubagents: [explore]"));
        assertTrue(content.contains("allowedSubagents: [explore, general]"));
        assertFalse(content.contains("\n      - command\n"));
    }

    @Test
    void keepsSharedSkillsAndAppendsBob2Traits() throws Exception {
        InitContext ctx = createContext();
        new Bob2Generator().generate(ctx);

        Path executeSkill = ctx.skillsDir().resolve("camel-execute/SKILL.md");
        assertTrue(Files.isRegularFile(executeSkill));
        String content = Files.readString(executeSkill);
        assertTrue(content.contains("# Camel Execute"));
        assertTrue(content.contains("<!-- TRAIT:bob2 -->"));
        assertTrue(content.contains("spawn_subagent"));
        assertTrue(content.contains("name: \"explore\""));
        assertTrue(content.contains("name: \"general\""));
        assertFalse(content.contains("APPROVAL GATE"));
        assertFalse(content.contains("gates/camel-execute.md"));
    }

    @Test
    void commandStubsIncludeBob2Frontmatter() throws Exception {
        InitContext ctx = createContext();
        new Bob2Generator().generate(ctx);

        Path command = ctx.commandsDir().resolve("camel-execute.md");
        assertTrue(Files.isRegularFile(command));
        String content = Files.readString(command);
        assertTrue(content.startsWith("---\n"));
        assertTrue(content.contains("description: \"Execute an approved implementation plan with two-stage review.\""));
        assertTrue(content.contains("argument-hint: \"<pipeline-id-or-plan>\""));
        assertTrue(content.contains("Read .bob/skills/camel-execute/SKILL.md and follow those instructions"));
    }

    @Test
    void skillFrontmatterKeepsSharedMetadataAndAddsBobReadableUserInvocable() throws Exception {
        InitContext ctx = createContext();
        new Bob2Generator().generate(ctx);

        String startSkill = Files.readString(ctx.skillsDir().resolve("camel-start/SKILL.md"));
        assertTrue(startSkill.contains("user_invocable: true"));
        assertTrue(startSkill.contains("user-invocable: true"));
        assertSingleBlankLineBeforeDispatch(startSkill);

        String executeSkill = Files.readString(ctx.skillsDir().resolve("camel-execute/SKILL.md"));
        assertTrue(executeSkill.contains("user_invocable: false"));
        assertTrue(executeSkill.contains("user-invocable: false"));
        assertSingleBlankLineBeforeDispatch(executeSkill);
    }

    @Test
    void doesNotChangeLegacyBobGenerationContract() throws Exception {
        AgentConfig bob = AgentRegistry.get("bob");
        String agentBaseFolder = bob.folder().substring(0, bob.folder().lastIndexOf("/"));
        InitContext bobCtx = new InitContext(
                bob, "bob", tempDir.resolve("legacy").resolve(bob.folder()),
                tempDir.resolve("legacy").resolve(agentBaseFolder + "/skills"),
                tempDir.resolve("legacy"), "camel-kit", "5.0.0-M2", Printer.noop());

        new BobGenerator().generate(bobCtx);

        String command = Files.readString(bobCtx.commandsDir().resolve("camel-execute.md"));
        assertEquals("Read .bob/skills/camel-execute/SKILL.md and follow those instructions", command);

        String executeSkill = Files.readString(bobCtx.skillsDir().resolve("camel-execute/SKILL.md"));
        assertTrue(executeSkill.contains("CHECKPOINT"));
        assertTrue(executeSkill.contains("Switch to"));
        assertFalse(executeSkill.contains("user-invocable:"));
        assertFalse(executeSkill.contains("<!-- TRAIT:bob2 -->"));
    }

    private void assertSingleBlankLineBeforeDispatch(String content) {
        int dispatchIndex = content.indexOf("\n---\n\n## Dispatch");
        assertTrue(dispatchIndex > 0);
        assertFalse(content.substring(0, dispatchIndex).endsWith("\n\n"));
    }
}
