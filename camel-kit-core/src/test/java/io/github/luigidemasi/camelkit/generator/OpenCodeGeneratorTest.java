package io.github.luigidemasi.camelkit.generator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import io.github.luigidemasi.camelkit.config.AgentConfig;
import io.github.luigidemasi.camelkit.config.AgentDescriptor;
import io.github.luigidemasi.camelkit.config.AgentRegistry;
import io.github.luigidemasi.camelkit.output.Printer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class OpenCodeGeneratorTest {

    @TempDir
    Path tempDir;

    private InitContext createContext() {
        return createContext("camel-kit");
    }

    private InitContext createContext(String commandPrefix) {
        AgentConfig agent = AgentRegistry.get("opencode");
        String agentBaseFolder = agent.folder().substring(0, agent.folder().lastIndexOf("/"));
        Path commandsDir = tempDir.resolve(agent.folder());
        Path skillsDir = tempDir.resolve(agentBaseFolder + "/skills");
        return new InitContext(
                agent, "opencode", commandsDir, skillsDir, tempDir,
                commandPrefix, Printer.noop());
    }

    @Test
    void generatesAgentsMd() throws Exception {
        InitContext ctx = createContext();
        new OpenCodeGenerator().generate(ctx);

        Path agentsMd = tempDir.resolve("AGENTS.md");
        assertTrue(Files.exists(agentsMd));
        String content = Files.readString(agentsMd);
        assertTrue(content.contains("/camel-start"));
        assertTrue(content.contains("Laws"));
        assertTrue(content.contains("config.properties"));
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
        assertTrue(Files.exists(agentsDir.resolve("researcher.md")));
        assertTrue(Files.exists(agentsDir.resolve("reviewer.md")));

        AgentDescriptor descriptor = AgentRegistry.descriptor("opencode");
        Set<String> registeredAgents = descriptor.templates().stream()
                .map(AgentDescriptor.TemplateInstall::target)
                .filter(target -> target.startsWith(".opencode/agents/"))
                .collect(Collectors.toSet());
        assertEquals(Set.of(
                ".opencode/agents/brainstormer.md",
                ".opencode/agents/executor.md",
                ".opencode/agents/implementer.md",
                ".opencode/agents/migrator.md",
                ".opencode/agents/planner.md",
                ".opencode/agents/researcher.md",
                ".opencode/agents/reviewer.md",
                ".opencode/agents/tester.md",
                ".opencode/agents/validator.md"), registeredAgents);
    }

    @Test
    void generatedAgentDefinitionsHaveDescriptions() throws Exception {
        InitContext ctx = createContext();
        new OpenCodeGenerator().generate(ctx);

        for (String name : new String[]{
                "brainstormer", "executor", "implementer", "migrator", "planner",
                "researcher", "reviewer", "tester", "validator"
        }) {
            String content = Files.readString(tempDir.resolve(".opencode/agents/" + name + ".md"));
            assertTrue(Pattern.compile("(?m)^description: \\S.+$").matcher(content).find(), name);
        }
    }

    @Test
    void phaseAgentsHaveScopedArtifactWrites() throws Exception {
        InitContext ctx = createContext();
        new OpenCodeGenerator().generate(ctx);

        String brainstormer = Files.readString(
                tempDir.resolve(".opencode/agents/brainstormer.md"));
        String planner = Files.readString(tempDir.resolve(".opencode/agents/planner.md"));
        String validator = Files.readString(tempDir.resolve(".opencode/agents/validator.md"));

        assertTrue(brainstormer.contains("permission:"));
        assertTrue(brainstormer.contains("\"docs/**\": allow"));
        assertTrue(brainstormer.contains("\".camel-kit/pipeline.json\": allow"));
        assertTrue(brainstormer.contains("\".camel-kit/project-snapshot.md\": allow"));
        assertTrue(planner.contains("\"docs/camel-kit/**\": allow"));
        assertTrue(validator.contains("\"docs/camel-kit/*/validation-report.md\": allow"));
        assertTrue(validator.contains("\"docs/validation-report-*.md\": allow"));
        assertTrue(validator.contains("task: deny"));
        assertTrue(validator.contains("bounded validation analysis"));
        assertFalse(validator.contains("follow those instructions exactly"));
        assertFalse(brainstormer.contains("\nedit:"));
        assertFalse(planner.contains("\nedit:"));
        assertFalse(validator.contains("\nedit:"));
        assertTrue(brainstormer.contains("mode: subagent"));
        assertTrue(brainstormer.contains("steps: 200"));
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
    void executorHasScopedLeafDelegation() throws Exception {
        InitContext ctx = createContext();
        new OpenCodeGenerator().generate(ctx);

        String content = Files.readString(
                tempDir.resolve(".opencode/agents/executor.md"));
        assertTrue(content.contains("mode: primary"));
        String taskPermissions = content.substring(
                content.indexOf("  task:"), content.indexOf("steps:"));
        assertTrue(taskPermissions.contains("\"*\": deny"));
        assertTrue(taskPermissions.contains("implementer: allow"));
        assertTrue(taskPermissions.contains("migrator: allow"));
        assertTrue(taskPermissions.contains("planner: allow"));
        assertTrue(taskPermissions.contains("researcher: allow"));
        assertTrue(taskPermissions.contains("reviewer: allow"));
        assertTrue(taskPermissions.contains("tester: allow"));
        assertFalse(taskPermissions.contains("validator: allow"));
        assertTrue(content.contains("steps: 100"));
    }

    @Test
    void reviewLeavesAreReadOnlyAndCannotDelegate() throws Exception {
        InitContext ctx = createContext();
        new OpenCodeGenerator().generate(ctx);

        for (String name : new String[]{"researcher", "reviewer"}) {
            String content = Files.readString(
                    tempDir.resolve(".opencode/agents/" + name + ".md"));
            assertTrue(content.contains("edit: deny"));
            assertTrue(content.contains("bash: deny"));
            assertTrue(content.contains("task: deny"));
        }
    }

    @Test
    void keepsMcpConfigCompatibleWithReleasedOpenCodeSchemas() throws Exception {
        InitContext ctx = createContext();
        new OpenCodeGenerator().generate(ctx);

        JsonNode config = new ObjectMapper().readTree(
                Files.readString(tempDir.resolve("opencode.json")));
        assertFalse(config.has("subagent_depth"));
        for (String server : new String[]{"camel", "camel-knowledge", "citrus"}) {
            assertFalse(config.path("mcp").path(server).has("autoApprove"));
            assertFalse(config.path("mcp").path(server).has("alwaysAllow"));
        }
        assertEquals("ask", config.path("permission").path("camel_*").asText());
        assertEquals("ask", config.path("permission").path("camel-knowledge_*").asText());
        assertEquals("ask", config.path("permission").path("citrus_*").asText());
    }

    @Test
    void mergesManagedOpenCodeConfigWithoutDiscardingUserSettings() throws Exception {
        Files.writeString(tempDir.resolve("opencode.json"), """
                {
                  "theme": "custom",
                  "permission": {"bash": "deny", "camel_*": "allow"},
                  "mcp": {
                    "custom": {"type": "remote", "url": "https://example.test/mcp"},
                    "camel": {"type": "local", "autoApprove": ["legacy"]}
                  }
                }
                """);

        new OpenCodeGenerator().generate(createContext());

        JsonNode config = new ObjectMapper().readTree(tempDir.resolve("opencode.json").toFile());
        assertEquals("custom", config.path("theme").asText());
        assertEquals("deny", config.path("permission").path("bash").asText());
        assertEquals("ask", config.path("permission").path("camel_*").asText());
        assertEquals("https://example.test/mcp", config.path("mcp").path("custom").path("url").asText());
        assertFalse(config.path("mcp").path("camel").has("autoApprove"));
    }

    @Test
    void rendersLeafCommandAllowlistForPluginPrefix() throws Exception {
        new OpenCodeGenerator().generate(createContext("camel kit"));

        for (String name : new String[]{"brainstormer", "planner", "validator"}) {
            String content = Files.readString(tempDir.resolve(".opencode/agents/" + name + ".md"));
            assertTrue(content.contains("\"camel kit *\": allow"), name);
            assertFalse(content.contains("camel-kit *"), name);
        }
        String validator = Files.readString(tempDir.resolve(".opencode/agents/validator.md"));
        assertTrue(validator.contains("\"./mvnw *\": allow"));
        assertFalse(validator.contains("\"mvn *\": allow"));
    }

    @Test
    void generatedTraitsUseAvailableAgentsAndOwnedStepLimits() throws Exception {
        InitContext ctx = createContext();
        new OpenCodeGenerator().generate(ctx);

        String brainstorm = Files.readString(
                tempDir.resolve(".opencode/skills/camel-brainstorm/SKILL.md"));
        assertTrue(brainstorm.contains("loads this skill in the primary session"));
        assertTrue(brainstorm.contains("cannot own approval or phase handoff"));
        assertFalse(brainstorm.contains("Use the `Plan` agent type"));
        assertFalse(brainstorm.contains("`Explore` subagents"));

        String execute = Files.readString(
                tempDir.resolve(".opencode/skills/camel-execute/SKILL.md"));
        assertTrue(execute.contains("selects the generated primary `executor`"));
        assertTrue(execute.contains("subagent_type"));
        assertTrue(execute.contains("dispatch `implementer` (`steps: 50`)"));
        assertTrue(execute.contains("dispatch `migrator` (`steps: 50`)"));
        assertTrue(execute.contains("dispatch `tester` (`steps: 40`)"));
        assertTrue(execute.contains("dispatch `researcher` (`steps: 30`)"));
        assertTrue(execute.contains("dispatch `reviewer` (`steps: 50`)"));
        assertTrue(execute.contains("omitting `background`"));
        assertFalse(execute.contains("Implementation subagents: `steps: 100`"));
        assertFalse(execute.contains("use `General`"));
        assertFalse(execute.contains("use `Explore`"));
        assertFalse(execute.contains("use the `Build`"));
        assertTrue(execute.contains("`test-engineer`"));
        assertTrue(execute.contains("`migration-specialist`"));
        assertTrue(execute.contains("`subagent_type`"));
    }

    @Test
    void installsAndReferencesCompletePersonaLibrary() throws Exception {
        InitContext ctx = createContext();
        new OpenCodeGenerator().generate(ctx);

        Path personas = tempDir.resolve(".opencode/camel-kit-personas");
        assertDescriptorRegistersPersonas("opencode", ".opencode/camel-kit-personas");
        try (var files = Files.list(personas)) {
            assertEquals(Set.copyOf(PersonaResourceInstaller.PERSONAS),
                    files.map(path -> path.getFileName().toString().replace(".md", ""))
                            .collect(Collectors.toSet()));
        }
        String execute = Files.readString(ctx.skillsDir().resolve("camel-execute/SKILL.md"));
        assertTrue(execute.contains(".opencode/camel-kit-personas/catalog-researcher.md"));
        assertTrue(execute.contains(".opencode/camel-kit-personas/acr-moderator.md"));
        assertTrue(execute.contains(".opencode/camel-kit-personas/[persona].md"));
        assertFalse(execute.contains("`agents/"));
        assertNoBarePersonaReferences(ctx.skillsDir(), personas);
        String knowledge = Files.readString(ctx.skillsDir().resolve("camel-knowledge/SKILL.md"));
        assertTrue(knowledge.contains("subagent_type: researcher"));
        assertTrue(knowledge.contains(".opencode/camel-kit-personas/knowledge-researcher.md"));
        assertFalse(knowledge.contains("as a `knowledge-researcher` subagent"));
    }

    private void assertDescriptorRegistersPersonas(String agentName, String targetDirectory) {
        AgentDescriptor descriptor = AgentRegistry.descriptor(agentName);
        Set<String> targets = descriptor.templates().stream()
                .map(AgentDescriptor.TemplateInstall::target)
                .filter(target -> target.startsWith(targetDirectory + "/"))
                .map(target -> Path.of(target).getFileName().toString().replaceFirst("\\.md$", ""))
                .collect(Collectors.toSet());
        assertEquals(Set.copyOf(PersonaResourceInstaller.PERSONAS), targets);
    }

    private void assertNoBarePersonaReferences(Path... roots) throws Exception {
        Pattern bareReference = Pattern.compile("(?<![A-Za-z0-9._/-])agents/");
        for (Path root : roots) {
            try (var files = Files.walk(root)) {
                for (Path file : files.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".md")).toList()) {
                    assertFalse(bareReference.matcher(Files.readString(file)).find(), file.toString());
                }
            }
        }
    }

    @Test
    void keepsInteractiveCommandsInPrimarySessionAndSelectsPrimaryExecutor() throws Exception {
        InitContext ctx = createContext();
        new OpenCodeGenerator().generate(ctx);

        for (String command : new String[]{"camel-brainstorm", "camel-plan", "camel-migrate", "camel-validate"}) {
            String content = Files.readString(ctx.commandsDir().resolve(command + ".md"));
            assertTrue(content.contains("Read .opencode/skills"), command);
            assertTrue(content.contains("Requested input: $ARGUMENTS"), command);
            assertFalse(content.contains("@"), command);
        }

        String execute = Files.readString(
                ctx.commandsDir().resolve("camel-execute.md"));
        assertTrue(execute.contains("agent: executor"));
        assertTrue(execute.contains("subtask: false"));
        assertTrue(execute.contains(
                "description: \"Execute a ready implementation plan derived from an approved design with an adversarial pre-filter and ordered spec and quality review.\""));
        assertTrue(execute.contains("$ARGUMENTS"));
        assertFalse(execute.contains("@executor"));
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
