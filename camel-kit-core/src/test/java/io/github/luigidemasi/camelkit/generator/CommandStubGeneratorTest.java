package io.github.luigidemasi.camelkit.generator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import io.github.luigidemasi.camelkit.config.AgentConfig;
import io.github.luigidemasi.camelkit.config.AgentRegistry;
import io.github.luigidemasi.camelkit.output.Printer;
import io.github.luigidemasi.camelkit.workflow.WorkflowManifest;
import io.github.luigidemasi.camelkit.workflow.WorkflowManifest.WorkflowCommand;
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

        WorkflowManifest workflow = WorkflowManifestLoader.loadDefault();
        new CommandStubGenerator().generate(ctx, workflow);

        Path startCommand = ctx.commandsDir().resolve("camel-start.toml");
        assertTrue(Files.isRegularFile(startCommand));
        assertFalse(Files.exists(ctx.commandsDir().resolve("camel-implement.toml")));

        String content = Files.readString(startCommand);
        assertTrue(content.contains("description = \"Camel-Kit start command\""));
        assertTrue(content.contains("Read .gemini/skills/camel-start/SKILL.md and follow those instructions"));
        assertTrue(content.contains("Requested input: {{args}}"));
    }

    @Test
    void writesPlainTextCommandsForMarkdownAgents() throws Exception {
        InitContext ctx = createContext("bob");
        Files.createDirectories(ctx.commandsDir());

        WorkflowManifest workflow = WorkflowManifestLoader.loadDefault();
        new CommandStubGenerator().generate(ctx, workflow);

        Path startCommand = ctx.commandsDir().resolve("camel-start.md");
        assertTrue(Files.isRegularFile(startCommand));

        String content = Files.readString(startCommand);
        assertEquals("Read .bob/skills/camel-start/SKILL.md and follow those instructions", content);
        assertFalse(content.startsWith("description = "));
    }

    @Test
    void writesBob2MarkdownCommandsWithFrontmatter() throws Exception {
        InitContext ctx = createContext("bob2");
        Files.createDirectories(ctx.commandsDir());

        WorkflowManifest workflow = WorkflowManifestLoader.loadDefault();
        new CommandStubGenerator().generate(ctx, workflow);

        Path executeCommand = ctx.commandsDir().resolve("camel-execute.md");
        assertTrue(Files.isRegularFile(executeCommand));

        String content = Files.readString(executeCommand);
        assertTrue(content.startsWith("---\n"));
        assertTrue(content.contains(
                "description: \"Execute a ready implementation plan derived from an approved design with an adversarial pre-filter and ordered spec and quality review.\""));
        assertTrue(content.contains("argument-hint: \"<pipeline-id-or-plan>\""));
        assertTrue(content.contains("Read .bob/skills/camel-execute/SKILL.md and follow those instructions"));
    }

    @Test
    void shipStubForwardsOptionsInProseWhenNoPlaceholderIsDocumented() throws Exception {
        InitContext ctx = createContext("bob");
        Files.createDirectories(ctx.commandsDir());

        new CommandStubGenerator().generate(ctx, WorkflowManifestLoader.loadDefault());

        String content = Files.readString(ctx.commandsDir().resolve("camel-ship.md"));
        assertTrue(content.contains(
                "Run `camel-kit ship` once, appending every option supplied to this command invocation verbatim."));
        assertFalse(content.contains("null"), "a missing placeholder must never be concatenated into the stub");
    }

    @Test
    void escapesTripleQuotesInTomlWrappedContent() throws Exception {
        InitContext ctx = createContext("gemini");
        Files.createDirectories(ctx.commandsDir());
        WorkflowManifest workflow = workflowWithCommands(List.of(command("camel-quote", "skill-\"\"\"-name")));

        new CommandStubGenerator().generate(ctx, workflow);

        String content = Files.readString(ctx.commandsDir().resolve("camel-quote.toml"));
        assertTrue(content.contains("skill-\\\"\\\"\\\"-name"));
        assertFalse(content.contains("skill-\"\"\"-name"));
    }

    @Test
    void handlesEmptyGeneratedCommandStubsWithoutCreatingFiles() throws Exception {
        InitContext ctx = createContext("gemini");
        Files.createDirectories(ctx.commandsDir());

        new CommandStubGenerator().generate(ctx, workflowWithCommands(List.of()));

        try (var files = Files.list(ctx.commandsDir())) {
            assertEquals(0, files.count());
        }
    }

    private WorkflowManifest workflowWithCommands(List<WorkflowCommand> commands) {
        return new WorkflowManifest(
                "1.0",
                "Test workflow",
                commands,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null);
    }

    private WorkflowCommand command(String name, String skill) {
        return new WorkflowCommand(
                name,
                List.of(),
                skill,
                true,
                true,
                "entry",
                "Test command",
                true,
                false,
                List.of());
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
