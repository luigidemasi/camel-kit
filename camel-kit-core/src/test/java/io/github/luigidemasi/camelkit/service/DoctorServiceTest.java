package io.github.luigidemasi.camelkit.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class DoctorServiceTest {

    private static final DoctorExpectations EXPECTATIONS = DoctorExpectations.loadDefault();

    @TempDir
    Path tempDir;

    @Test
    void healthyWorkspaceReturnsStructuredFindings() throws Exception {
        createHealthyWorkspace(tempDir);

        DoctorResult result = new DoctorService().inspect(new DoctorRequest(tempDir));

        assertFalse(result.hasFailures());
        assertEquals(tempDir.toAbsolutePath().normalize(), result.projectDir());
        assertTrue(result.count(DoctorFinding.Status.PASS) > 0);
        assertTrue(hasFinding(result, DoctorFinding.Status.PASS, "config"));
        assertTrue(hasFinding(result, DoctorFinding.Status.PASS, "mcp"));
        assertTrue(hasFinding(result, DoctorFinding.Status.PASS, "workspace"));
    }

    @Test
    void missingConfigReturnsActionableFailure() {
        DoctorResult result = new DoctorService().inspect(new DoctorRequest(tempDir));

        assertTrue(result.hasFailures());
        assertTrue(hasFinding(result, DoctorFinding.Status.FAIL, "config",
                ".camel-kit/config.properties is missing",
                "Run camel-kit init --here --ai <agent>"));
    }

    private void createHealthyWorkspace(Path root) throws Exception {
        Files.createDirectories(root.resolve(".camel-kit"));
        Files.writeString(root.resolve(".camel-kit/config.properties"), """
                project.name=orders
                project.command-prefix=camel-kit
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
