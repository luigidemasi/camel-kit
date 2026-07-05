package io.github.luigidemasi.camelkit.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import io.github.luigidemasi.camelkit.config.AgentConfig;
import io.github.luigidemasi.camelkit.config.AgentGeneratorStrategy;
import io.github.luigidemasi.camelkit.config.AgentRegistry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class DoctorServiceTest {

    private static final DoctorExpectations EXPECTATIONS = DoctorExpectations.loadDefault();
    private static final String COPILOT = AgentGeneratorStrategy.COPILOT.descriptorValue();
    private static final String PI = AgentGeneratorStrategy.PI.descriptorValue();

    @TempDir
    Path tempDir;

    @Test
    void healthyWorkspaceReturnsStructuredFindings() throws Exception {
        createHealthyWorkspace(tempDir, "bob");

        DoctorResult result = new DoctorService().inspect(new DoctorRequest(tempDir));

        assertFalse(result.hasFailures());
        assertEquals(tempDir.toAbsolutePath().normalize(), result.projectDir());
        assertTrue(result.count(DoctorFinding.Status.PASS) > 0);
        assertTrue(hasFinding(result, DoctorFinding.Status.PASS, "config"));
        assertTrue(hasFinding(result, DoctorFinding.Status.PASS, "mcp"));
        assertTrue(hasFinding(result, DoctorFinding.Status.PASS, "workspace"));
    }

    @Test
    void healthyBob2WorkspaceUsesRegisteredMcpPath() throws Exception {
        createHealthyWorkspace(tempDir, "bob2");

        DoctorResult result = new DoctorService().inspect(new DoctorRequest(tempDir));

        assertFalse(result.hasFailures(), result.findings().toString());
        assertTrue(hasFinding(result, DoctorFinding.Status.PASS, "mcp",
                "MCP config exists and tool allowlists match Camel-Kit expectations",
                "No action required."));
    }

    @Test
    void healthyCopilotWorkspaceUsesGithubMcpToolsSchema() throws Exception {
        createHealthyWorkspace(tempDir, "copilot");

        DoctorResult result = new DoctorService().inspect(new DoctorRequest(tempDir));

        assertFalse(result.hasFailures(), result.findings().toString());
        assertTrue(hasFinding(result, DoctorFinding.Status.PASS, "mcp",
                "MCP config exists and tool allowlists match Camel-Kit expectations",
                "No action required."));
    }

    @Test
    void healthyPiWorkspaceUsesDirectToolsSchemaAndGuardResources() throws Exception {
        createHealthyWorkspace(tempDir, "pi");

        DoctorResult result = new DoctorService().inspect(new DoctorRequest(tempDir));

        assertFalse(result.hasFailures(), result.findings().toString());
        assertTrue(hasFinding(result, DoctorFinding.Status.PASS, "mcp",
                "MCP config exists and tool allowlists match Camel-Kit expectations",
                "No action required."));
        assertTrue(hasFinding(result, DoctorFinding.Status.PASS, "workspace",
                "Pi safety guard extension and policy are present",
                "No action required."));
    }

    @Test
    void copilotWildcardToolsSchemaWarnsWithoutFailing() throws Exception {
        createHealthyWorkspace(tempDir, "copilot");
        writeMcpConfig(tempDir, "copilot", """
                {
                  "mcpServers": {
                    "camel": {
                      "type": "stdio",
                      "command": "jbang",
                      "tools": ["*"]
                    },
                    "camel-knowledge": {
                      "type": "stdio",
                      "command": "jbang",
                      "tools": ["*"]
                    }
                  }
                }
                """);

        DoctorResult result = new DoctorService().inspect(new DoctorRequest(tempDir));

        assertFalse(result.hasFailures(), result.findings().toString());
        assertTrue(hasFinding(result, DoctorFinding.Status.WARN, "mcp",
                "MCP server 'camel' tools field allows all tools with '*'",
                "Prefer the generated Camel-Kit tool allowlist"));
    }

    @Test
    void copilotCommaSeparatedToolsStringIsAccepted() throws Exception {
        createHealthyWorkspace(tempDir, "copilot");
        writeMcpConfig(tempDir, "copilot", String.format(Locale.ROOT, """
                {
                  "mcpServers": {
                    "camel": {
                      "type": "stdio",
                      "command": "jbang",
                      "tools": "%s"
                    },
                    "camel-knowledge": {
                      "type": "stdio",
                      "command": "jbang",
                      "tools": "%s"
                    }
                  }
                }
                """, String.join(",", EXPECTATIONS.camelMcpTools()),
                String.join(",", EXPECTATIONS.knowledgeMcpTools())));

        DoctorResult result = new DoctorService().inspect(new DoctorRequest(tempDir));

        assertFalse(result.hasFailures(), result.findings().toString());
        assertTrue(hasFinding(result, DoctorFinding.Status.PASS, "mcp",
                "MCP config exists and tool allowlists match Camel-Kit expectations",
                "No action required."));
    }

    @Test
    void invalidCopilotToolsScalarProducesClearFailure() throws Exception {
        createHealthyWorkspace(tempDir, "copilot");
        writeMcpConfig(tempDir, "copilot", """
                {
                  "mcpServers": {
                    "camel": {
                      "type": "stdio",
                      "command": "jbang",
                      "tools": 42
                    },
                    "camel-knowledge": {
                      "type": "stdio",
                      "command": "jbang",
                      "tools": []
                    }
                  }
                }
                """);

        DoctorResult result = new DoctorService().inspect(new DoctorRequest(tempDir));

        assertTrue(result.hasFailures(), result.findings().toString());
        assertTrue(hasFinding(result, DoctorFinding.Status.FAIL, "mcp",
                "MCP server 'camel' tools must be an array or comma-separated string",
                "Set tools to"));
    }

    @Test
    void piDirectToolsTrueWarnsWithoutFailing() throws Exception {
        createHealthyWorkspace(tempDir, "pi");
        writeMcpConfig(tempDir, "pi", """
                {
                  "mcpServers": {
                    "camel": {
                      "command": "jbang",
                      "directTools": true
                    },
                    "camel-knowledge": {
                      "command": "jbang",
                      "directTools": true
                    }
                  }
                }
                """);

        DoctorResult result = new DoctorService().inspect(new DoctorRequest(tempDir));

        assertFalse(result.hasFailures(), result.findings().toString());
        assertTrue(hasFinding(result, DoctorFinding.Status.WARN, "mcp",
                "MCP server 'camel' directTools promotes all tools",
                "Prefer the generated Camel-Kit tool allowlist"));
    }

    @Test
    void invalidPiDirectToolsScalarProducesClearFailure() throws Exception {
        createHealthyWorkspace(tempDir, "pi");
        writeMcpConfig(tempDir, "pi", """
                {
                  "mcpServers": {
                    "camel": {
                      "command": "jbang",
                      "directTools": "camel_catalog_components"
                    },
                    "camel-knowledge": {
                      "command": "jbang",
                      "directTools": []
                    }
                  }
                }
                """);

        DoctorResult result = new DoctorService().inspect(new DoctorRequest(tempDir));

        assertTrue(result.hasFailures(), result.findings().toString());
        assertTrue(hasFinding(result, DoctorFinding.Status.FAIL, "mcp",
                "MCP server 'camel' directTools must be an array or true",
                "Set directTools"));
    }

    @Test
    void missingPiGuardPolicyProducesClearFailure() throws Exception {
        createHealthyWorkspace(tempDir, "pi");
        Files.delete(tempDir.resolve(".pi/camel-kit-guard-policy.json"));

        DoctorResult result = new DoctorService().inspect(new DoctorRequest(tempDir));

        assertTrue(result.hasFailures(), result.findings().toString());
        assertTrue(hasFinding(result, DoctorFinding.Status.FAIL, "workspace",
                "Pi safety guard policy is missing",
                "Run camel-kit init --here --ai pi --force"));
    }

    @Test
    void invalidPiGuardPolicyJsonUsesPiRegenerationCommand() throws Exception {
        createHealthyWorkspace(tempDir, "pi");
        writePiGuardPolicy("""
                {
                  "version": 1,
                  "rules": [
                }
                """);

        DoctorResult result = new DoctorService().inspect(new DoctorRequest(tempDir));

        assertTrue(result.hasFailures(), result.findings().toString());
        assertTrue(hasFinding(result, DoctorFinding.Status.FAIL, "workspace",
                "Pi safety guard policy is not valid JSON",
                "camel-kit init --here --ai pi --force"));
    }

    @Test
    void malformedPiGuardPolicyRuleProducesClearFailure() throws Exception {
        createHealthyWorkspace(tempDir, "pi");
        writePiGuardPolicy("""
                {
                  "version": 1,
                  "rules": [
                    {
                      "toolNames": "bash",
                      "reason": ""
                    }
                  ]
                }
                """);

        DoctorResult result = new DoctorService().inspect(new DoctorRequest(tempDir));

        assertTrue(result.hasFailures(), result.findings().toString());
        assertTrue(hasFinding(result, DoctorFinding.Status.FAIL, "workspace",
                "must declare a non-empty inputPattern",
                "camel-kit init --here --ai pi --force"));
        assertTrue(hasFinding(result, DoctorFinding.Status.FAIL, "workspace",
                "must declare a non-empty reason",
                "camel-kit init --here --ai pi --force"));
        assertTrue(hasFinding(result, DoctorFinding.Status.FAIL, "workspace",
                "has invalid toolNames; expected an array of strings",
                "camel-kit init --here --ai pi --force"));
    }

    @Test
    void nonCopilotToolsKeyDoesNotBypassLegacyAllowlistValidation() throws Exception {
        createHealthyWorkspace(tempDir, "bob");
        writeMcpConfig(tempDir, "bob", """
                {
                  "mcpServers": {
                    "camel": {
                      "command": "jbang",
                      "tools": ["*"]
                    },
                    "camel-knowledge": {
                      "command": "jbang",
                      "tools": ["*"]
                    }
                  }
                }
                """);

        DoctorResult result = new DoctorService().inspect(new DoctorRequest(tempDir));

        assertTrue(result.hasFailures(), result.findings().toString());
        assertTrue(hasFinding(result, DoctorFinding.Status.FAIL, "mcp",
                "MCP server 'camel' is missing autoApprove array",
                "Regenerate the MCP config"));
    }

    @Test
    void mixedCaseAgentNameStillResolvesRegisteredMcpPathForMcpChecks() throws Exception {
        createHealthyWorkspace(tempDir, "bob");
        Path configFile = tempDir.resolve(".camel-kit/config.properties");
        Files.writeString(configFile, Files.readString(configFile).replace("agent.name=bob", "agent.name=Bob"));

        DoctorResult result = new DoctorService().inspect(new DoctorRequest(tempDir));

        assertTrue(hasFinding(result, DoctorFinding.Status.FAIL, "config", "Unknown agent.name 'Bob'", null));
        assertTrue(hasFinding(result, DoctorFinding.Status.PASS, "mcp",
                "MCP config exists and tool allowlists match Camel-Kit expectations",
                "No action required."));
        assertFalse(hasFinding(result, DoctorFinding.Status.FAIL, "mcp",
                "Cannot determine MCP config path for agent 'Bob'", null));
    }

    @Test
    void missingConfigReturnsActionableFailure() {
        DoctorResult result = new DoctorService().inspect(new DoctorRequest(tempDir));

        assertTrue(result.hasFailures());
        assertTrue(hasFinding(result, DoctorFinding.Status.FAIL, "config",
                ".camel-kit/config.properties is missing",
                "Run camel-kit init --here --ai <agent>"));
    }

    private void createHealthyWorkspace(Path root, String agentName) throws Exception {
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
        Files.writeString(root.resolve("AGENTS.md"), "Integration work -> /camel-start\n");
        Files.writeString(root.resolve("mvnw"), "#!/bin/sh\n");

        Path commands = root.resolve(agent.folder());
        String agentBaseFolder = agent.folder().substring(0, agent.folder().lastIndexOf('/'));
        Files.createDirectories(commands);
        for (String command : EXPECTATIONS.userCommands()) {
            Files.writeString(commands.resolve(command + ".md"),
                    "Read " + agentBaseFolder + "/skills/" + command + "/SKILL.md and follow those instructions\n");
        }

        Path skills = commands.getParent().resolve("skills");
        for (String skill : EXPECTATIONS.requiredSkills()) {
            Path skillDir = skills.resolve(skill);
            Files.createDirectories(skillDir);
            Files.writeString(skillDir.resolve("SKILL.md"), "# " + skill + "\n");
        }

        Path mcpFile = root.resolve(agent.mcpConfigPath());
        Files.createDirectories(mcpFile.getParent());
        Files.writeString(mcpFile,
                mcpJson(agentName, EXPECTATIONS.camelMcpTools(), EXPECTATIONS.knowledgeMcpTools()));

        if (PI.equals(agentName)) {
            Files.createDirectories(root.resolve(".pi/extensions"));
            Files.writeString(root.resolve(".pi/extensions/camel-kit-guard.ts"),
                    "export default function camelKitGuard(pi: any) {}\n");
            Files.writeString(root.resolve(".pi/camel-kit-guard-policy.json"), """
                    {
                      "version": 1,
                      "rules": []
                    }
                    """);
        }
    }

    private void writeMcpConfig(Path root, String agentName, String content) throws Exception {
        AgentConfig agent = AgentRegistry.get(agentName);
        Path mcpFile = root.resolve(agent.mcpConfigPath());
        Files.createDirectories(mcpFile.getParent());
        Files.writeString(mcpFile, content);
    }

    private void writePiGuardPolicy(String content) throws Exception {
        Files.writeString(tempDir.resolve(".pi/camel-kit-guard-policy.json"), content);
    }

    private String mcpJson(String agentName, Collection<String> camelTools, Collection<String> knowledgeTools) {
        if (COPILOT.equals(agentName)) {
            return String.format(Locale.ROOT, """
                    {
                      "mcpServers": {
                        "camel": {
                          "type": "stdio",
                          "command": "jbang",
                          "tools": [%s]
                        },
                        "camel-knowledge": {
                          "type": "stdio",
                          "command": "jbang",
                          "tools": [%s]
                        }
                      }
                    }
                    """, jsonArray(camelTools), jsonArray(knowledgeTools));
        }
        if (PI.equals(agentName)) {
            return String.format(Locale.ROOT, """
                    {
                      "mcpServers": {
                        "camel": {
                          "command": "jbang",
                          "directTools": [%s]
                        },
                        "camel-knowledge": {
                          "command": "jbang",
                          "directTools": [%s]
                        }
                      }
                    }
                    """, jsonArray(camelTools), jsonArray(knowledgeTools));
        }
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

    private boolean hasFinding(DoctorResult result, DoctorFinding.Status status, String category) {
        return hasFinding(result, status, category, null, null);
    }

    private boolean hasFinding(
            DoctorResult result,
            DoctorFinding.Status status,
            String category,
            String messageContains,
            String remediationContains) {
        List<DoctorFinding> findings = result.findings();
        for (DoctorFinding finding : findings) {
            boolean matches = status == finding.status()
                    && category.equals(finding.category())
                    && (messageContains == null || finding.message().contains(messageContains));
            if (matches && (remediationContains == null || finding.remediation().contains(remediationContains))) {
                return true;
            }
        }
        return false;
    }
}
