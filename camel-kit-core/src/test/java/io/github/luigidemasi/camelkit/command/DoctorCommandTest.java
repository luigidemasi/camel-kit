package io.github.luigidemasi.camelkit.command;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import io.github.luigidemasi.camelkit.CamelKitMain;
import io.github.luigidemasi.camelkit.config.AgentConfig;
import io.github.luigidemasi.camelkit.config.AgentRegistry;
import io.github.luigidemasi.camelkit.generator.AgentGeneratorFactory;
import io.github.luigidemasi.camelkit.generator.InitContext;
import io.github.luigidemasi.camelkit.output.Printer;
import io.github.luigidemasi.camelkit.service.DoctorExpectations;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.*;

class DoctorCommandTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DoctorExpectations EXPECTATIONS = DoctorExpectations.loadDefault();

    @TempDir
    Path tempDir;

    @Test
    void healthyWorkspaceReturnsSuccessAndJsonFindings() throws Exception {
        createHealthyWorkspace(tempDir);

        RunResult result = runDoctor("--project-dir", tempDir.toString(), "--json");

        assertEquals(0, result.exitCode());
        JsonNode json = MAPPER.readTree(result.output());
        assertEquals("PASS", json.get("status").asText());
        assertEquals(0, json.get("summary").get("fail").asInt());
        assertTrue(json.get("summary").get("pass").asInt() > 0);
        assertTrue(hasFinding(json, "PASS", "config"));
        assertTrue(hasFinding(json, "PASS", "mcp"));
        assertTrue(hasFinding(json, "PASS", "skills"));
        assertTrue(hasFinding(json, "PASS", "workspace"));
        assertTrue(hasFinding(json, "PASS", "graph"));
    }

    @Test
    void brokenWorkspaceReportsActionableFailures() throws Exception {
        createHealthyWorkspace(tempDir);
        Files.writeString(tempDir.resolve("AGENTS.md"), "Use /camel-design for old generated workflows.\n");
        Files.writeString(tempDir.resolve(".bob/commands/camel-implement.md"), "internal command should not exist");
        Files.writeString(tempDir.resolve(".bob/commands/camel-verify.md"), "internal command should not exist");
        Files.delete(tempDir.resolve(".bob/skills/camel-start/SKILL.md"));
        Files.writeString(tempDir.resolve(".bob/mcp.json"), mcpJson(List.of("camel_catalog_components", "extra_tool"),
                EXPECTATIONS.knowledgeMcpTools()));

        RunResult result = runDoctor("--project-dir", tempDir.toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.output().contains("FAIL"));
        assertTrue(result.output().contains("unexpected"));
        assertTrue(result.output().contains("camel-implement"));
        assertTrue(result.output().contains("camel-verify"));
        assertTrue(result.output().contains("Missing SKILL.md for: camel-start"));
        assertTrue(result.output().contains("extra extra_tool"));
        assertTrue(result.output().contains("stale references"));
        assertTrue(result.output().contains("Fix:"));
    }

    @Test
    void missingConfigFileProducesConfigFailFinding() throws Exception {
        RunResult result = runDoctor("--project-dir", tempDir.toString(), "--json");

        assertNotEquals(0, result.exitCode());
        JsonNode json = MAPPER.readTree(result.output());
        assertEquals("FAIL", json.get("status").asText());
        assertTrue(json.get("summary").get("fail").asInt() > 0);
        assertTrue(hasFinding(json, "FAIL", "config",
                ".camel-kit/config.properties is missing",
                "Run camel-kit init --here --ai <agent>"), result.output());

        RunResult textResult = runDoctor("--project-dir", tempDir.toString());
        assertNotEquals(0, textResult.exitCode());
        assertTrue(textResult.output().contains(".camel-kit/config.properties is missing"));
        assertTrue(textResult.output().contains("Run camel-kit init --here --ai <agent>"));
    }

    @Test
    void missingAgentNameInConfigProducesSpecificFailure() throws Exception {
        writeConfig("""
                project.name=orders
                project.command-prefix=camel-kit
                agent.folder=.bob/commands
                """);

        RunResult result = runDoctor("--project-dir", tempDir.toString(), "--json");

        assertNotEquals(0, result.exitCode());
        JsonNode json = MAPPER.readTree(result.output());
        assertEquals("FAIL", json.get("status").asText());
        assertTrue(hasFinding(json, "FAIL", "config", "agent.name is not configured"), result.output());
    }

    @Test
    void missingAgentFolderInConfigProducesSpecificFailure() throws Exception {
        writeConfig("""
                project.name=orders
                project.command-prefix=camel-kit
                agent.name=bob
                """);

        RunResult result = runDoctor("--project-dir", tempDir.toString(), "--json");

        assertNotEquals(0, result.exitCode());
        JsonNode json = MAPPER.readTree(result.output());
        assertEquals("FAIL", json.get("status").asText());
        assertTrue(hasFinding(json, "FAIL", "config",
                ".camel-kit/config.properties is missing keys: agent.folder"), result.output());
        assertTrue(hasFinding(json, "FAIL", "config",
                "agent.folder is '' but bob expects '.bob/commands'"), result.output());
    }

    @Test
    void agentFolderMismatchProducesFailure() throws Exception {
        writeConfig("""
                project.name=orders
                project.command-prefix=camel-kit
                agent.name=bob
                agent.folder=.claude/commands
                """);

        RunResult result = runDoctor("--project-dir", tempDir.toString(), "--json");

        assertNotEquals(0, result.exitCode());
        JsonNode json = MAPPER.readTree(result.output());
        assertEquals("FAIL", json.get("status").asText());
        assertTrue(hasFinding(json, "FAIL", "config",
                "agent.folder is '.claude/commands' but bob expects '.bob/commands'"), result.output());
    }

    @Test
    void unknownAgentNameProducesFailureWithValidOptions() throws Exception {
        writeConfig("""
                project.name=orders
                project.command-prefix=camel-kit
                agent.name=unknown-agent
                agent.folder=.unknown/commands
                """);

        RunResult result = runDoctor("--project-dir", tempDir.toString(), "--json");

        assertNotEquals(0, result.exitCode());
        JsonNode json = MAPPER.readTree(result.output());
        assertEquals("FAIL", json.get("status").asText());
        assertTrue(hasFinding(json, "FAIL", "config",
                "Unknown agent.name 'unknown-agent'",
                "Set agent.name to one of:"), result.output());
    }

    @Test
    void generatedWorkspacesMatchDoctorExpectations() throws Exception {
        for (String agentName : List.of("bob", "bob2", "claude", "copilot", "gemini", "qwen", "opencode", "pi")) {
            Path root = tempDir.resolve(agentName);
            createGeneratedWorkspace(root, agentName);

            RunResult result = runDoctor("--project-dir", root.toString(), "--json");

            assertEquals(0, result.exitCode(), agentName + System.lineSeparator() + result.output());
            AgentConfig agent = AgentRegistry.get(agentName);
            String extension = "." + agent.fileFormat();
            Path commandsDir = root.resolve(agent.folder());
            for (String internalCommand : List.of("camel-design", "camel-implement", "camel-test", "camel-verify")) {
                assertFalse(Files.exists(commandsDir.resolve(internalCommand + extension)),
                        agentName + " should not expose " + internalCommand + " as a command");
            }

            Path skillsDir = commandsDir.getParent().resolve("skills");
            for (String internalSkill : List.of("camel-design", "camel-implement", "camel-test", "camel-verify")) {
                assertTrue(Files.isRegularFile(skillsDir.resolve(internalSkill).resolve("SKILL.md")),
                        agentName + " should keep " + internalSkill + " as a skill");
            }
        }
    }

    private RunResult runDoctor(String... args) {
        CapturingPrinter printer = new CapturingPrinter();
        CamelKitMain main = new CamelKitMain();
        main.setOut(printer);
        DoctorCommand command = new DoctorCommand(main);
        int exitCode = new CommandLine(command).execute(args);
        return new RunResult(exitCode, printer.output());
    }

    private void createHealthyWorkspace(Path root) throws Exception {
        Files.createDirectories(root.resolve(".camel-kit"));
        Files.writeString(root.resolve(".camel-kit/config.properties"), """
                project.name=orders
                project.command-prefix=camel-kit
                project.runtime=main
                project.camelVersion=4.20.0
                project.platformBomVersion=4.20.0
                agent.name=bob
                agent.folder=.bob/commands
                """);
        Files.writeString(root.resolve(".camel-kit/project-graph.json"), "{}");
        Files.writeString(root.resolve("AGENTS.md"), "Integration work -> /camel-start\n");
        Files.writeString(root.resolve("mvnw"), "#!/bin/sh\n");

        Path commands = root.resolve(".bob/commands");
        Files.createDirectories(commands);
        for (String command : EXPECTATIONS.userCommands()) {
            Files.writeString(commands.resolve(command + ".md"),
                    "Read .bob/skills/" + command + "/SKILL.md and follow those instructions\n");
        }

        Path skills = root.resolve(".bob/skills");
        for (String skill : EXPECTATIONS.requiredSkills()) {
            Path skillDir = skills.resolve(skill);
            Files.createDirectories(skillDir);
            Files.writeString(skillDir.resolve("SKILL.md"), "# " + skill + "\n");
        }

        Files.createDirectories(root.resolve(".bob"));
        Files.writeString(root.resolve(".bob/mcp.json"),
                mcpJson(EXPECTATIONS.camelMcpTools(), EXPECTATIONS.knowledgeMcpTools()));
    }

    private void writeConfig(String config) throws Exception {
        Path camelKitDir = Files.createDirectories(tempDir.resolve(".camel-kit"));
        Files.writeString(camelKitDir.resolve("config.properties"), withVersionDefaults(config));
    }

    private void createGeneratedWorkspace(Path root, String agentName) throws Exception {
        AgentConfig agent = AgentRegistry.get(agentName);
        Files.createDirectories(root.resolve(".camel-kit"));
        Files.writeString(root.resolve(".camel-kit/config.properties"), String.format(Locale.ROOT, """
                project.name=orders
                project.command-prefix=camel-kit
                project.runtime=main
                project.camelVersion=4.20.0
                project.platformBomVersion=4.20.0
                agent.name=%s
                agent.folder=%s
                """, agentName, agent.folder()));
        Files.writeString(root.resolve(".camel-kit/project-graph.json"), "{}");
        Files.writeString(root.resolve("mvnw"), "#!/bin/sh\n");

        Path commandsDir = root.resolve(agent.folder());
        Path skillsDir = commandsDir.getParent().resolve("skills");
        InitContext ctx = new InitContext(
                agent, agentName, commandsDir, skillsDir, root, "camel-kit", Printer.noop());
        AgentGeneratorFactory.create(agentName).generate(ctx);
    }

    private String withVersionDefaults(String config) {
        StringBuilder merged = new StringBuilder(config);
        if (!config.contains("project.runtime=")) {
            merged.append("project.runtime=main\n");
        }
        if (!config.contains("project.camelVersion=")) {
            merged.append("project.camelVersion=4.20.0\n");
        }
        if (!config.contains("project.platformBomVersion=")) {
            merged.append("project.platformBomVersion=4.20.0\n");
        }
        return merged.toString();
    }

    private String mcpJson(Collection<String> camelTools, Collection<String> knowledgeTools) {
        return String.format(Locale.ROOT, """
                {
                  "mcpServers": {
                    "camel": {
                      "command": "jbang",
                      "autoApprove": [%s],
                      "alwaysAllow": [%s]
                    },
                    "camel-knowledge": {
                      "command": "jbang",
                      "autoApprove": [%s],
                      "alwaysAllow": [%s]
                    }
                  }
                }
                """, jsonArray(camelTools), jsonArray(camelTools),
                jsonArray(knowledgeTools), jsonArray(knowledgeTools));
    }

    private String jsonArray(Collection<String> values) {
        return values.stream()
                .map(value -> "\"" + value + "\"")
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
    }

    private boolean hasFinding(JsonNode root, String status, String category) {
        for (JsonNode finding : root.get("findings")) {
            if (status.equals(finding.get("status").asText())
                    && category.equals(finding.get("category").asText())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasFinding(JsonNode root, String status, String category, String messageContains) {
        return hasFinding(root, status, category, messageContains, null);
    }

    private boolean hasFinding(
            JsonNode root, String status, String category, String messageContains, String remediationContains) {
        for (JsonNode finding : root.get("findings")) {
            boolean matches = status.equals(finding.get("status").asText())
                    && category.equals(finding.get("category").asText())
                    && finding.path("message").asText().contains(messageContains);
            if (matches && (remediationContains == null
                    || finding.path("remediation").asText().contains(remediationContains))) {
                return true;
            }
        }
        return false;
    }

    private static final class CapturingPrinter implements Printer {
        private final StringBuilder output = new StringBuilder();

        @Override
        public void println() {
            output.append(System.lineSeparator());
        }

        @Override
        public void println(String line) {
            output.append(line).append(System.lineSeparator());
        }

        @Override
        public void print(String value) {
            output.append(value);
        }

        String output() {
            return output.toString();
        }
    }

    record RunResult(int exitCode, String output) {
    }
}
