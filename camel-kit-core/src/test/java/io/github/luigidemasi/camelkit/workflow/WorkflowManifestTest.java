package io.github.luigidemasi.camelkit.workflow;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.luigidemasi.camelkit.config.AgentConfig;
import io.github.luigidemasi.camelkit.config.AgentRegistry;
import io.github.luigidemasi.camelkit.generator.AgentGeneratorFactory;
import io.github.luigidemasi.camelkit.generator.DefaultGenerator;
import io.github.luigidemasi.camelkit.generator.InitContext;
import io.github.luigidemasi.camelkit.output.Printer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class WorkflowManifestTest {

    private static final String MINIMAL_VALID_MANIFEST = """
            version: "1.0"
            description: "Test manifest"
            commands:
              - name: camel-start
                aliases: []
                skill: camel-start
                generated_stub: true
                user_facing: true
                tier: entry
                description: "Start"
                standalone: true
                chained: false
            skills:
              - name: camel-start
                user_invocable: true
                status: router
                generated_command: camel-start
            stages:
              - id: route
                skill: camel-start
                kind: entry
                standalone: true
                chained: false
                inputs: []
                outputs: []
                transitions: []
            artifacts:
              - id: design-spec
                path: docs/camel-kit/<pipeline-id>/design-spec.md
                produced_by: [camel-start]
                consumed_by: [camel-start]
            mcp_servers:
              - id: camel
                display_name: "Camel Catalog MCP"
                description: "Camel"
                allowed_tools: []
              - id: camel-knowledge
                display_name: "Camel-Kit Knowledge MCP"
                description: "Knowledge"
                allowed_tools: []
              - id: citrus
                display_name: "Citrus MCP"
                description: "Citrus"
                allowed_tools: []
            """;

    @TempDir
    Path tempDir;

    @Test
    void loadsBundledWorkflowManifest() throws Exception {
        WorkflowManifest manifest = WorkflowManifestLoader.loadDefault();

        assertEquals("1.0", manifest.version());
        assertFalse(manifest.commands().isEmpty());
        assertFalse(manifest.skills().isEmpty());
        assertFalse(manifest.stages().isEmpty());
        assertFalse(manifest.mcpServer("camel-knowledge").allowedTools().isEmpty());
    }

    @Test
    void loadsMinimalValidManifest() {
        WorkflowManifest manifest = assertDoesNotThrow(() -> loadManifest(MINIMAL_VALID_MANIFEST));

        assertNotNull(manifest);
        assertEquals(List.of("camel-start"), manifest.commands().stream()
                .map(WorkflowManifest.WorkflowCommand::name)
                .toList());
    }

    @Test
    void loadsManifestWithOmittedOptionalCollections() {
        String yaml = MINIMAL_VALID_MANIFEST
                .replace("    aliases: []\n", "")
                .replace("""
                            inputs: []
                            outputs: []
                            transitions: []
                        """, "")
                .replace("""
                            produced_by: [camel-start]
                            consumed_by: [camel-start]
                        """, "")
                .replace("    allowed_tools: []\n", "");

        WorkflowManifest manifest = assertDoesNotThrow(() -> loadManifest(yaml));

        assertTrue(manifest.commands().get(0).aliases().isEmpty());
        assertTrue(manifest.stages().get(0).transitions().isEmpty());
        assertTrue(manifest.artifacts().get(0).producedBy().isEmpty());
        assertTrue(manifest.mcpServer("camel").allowedTools().isEmpty());
    }

    @Test
    void generatedCommandStubsMatchManifest() throws Exception {
        WorkflowManifest manifest = WorkflowManifestLoader.loadDefault();

        for (String agentName : AgentRegistry.names()) {
            InitContext ctx = createContext(agentName);
            AgentGeneratorFactory.create(agentName).generate(ctx);

            if (!ctx.agent().generatesCommandStubs()) {
                assertFalse(Files.exists(tempDir.resolve(".codex/commands")),
                        agentName + " must not generate a command directory");
                assertFalse(Files.exists(tempDir.resolve(".github/commands")),
                        agentName + " must not generate a command directory");
                continue;
            }

            Set<String> expected = manifest.generatedCommandStubs().stream()
                    .filter(command -> !command.isSkillOnly(agentName))
                    .map(command -> command.name() + "." + ctx.agent().fileFormat())
                    .collect(Collectors.toCollection(java.util.TreeSet::new));
            Set<String> actual;
            try (var stream = Files.list(ctx.commandsDir())) {
                actual = stream
                        .filter(Files::isRegularFile)
                        .map(path -> path.getFileName().toString())
                        .collect(Collectors.toCollection(java.util.TreeSet::new));
            }

            assertEquals(expected, actual, agentName + " generated command stubs must match manifest");
        }
    }

    @Test
    void shipStubIsSkillOnlyForPi() throws Exception {
        WorkflowManifest manifest = WorkflowManifestLoader.loadDefault();

        WorkflowManifest.WorkflowCommand ship = manifest.generatedCommandStubs().stream()
                .filter(command -> "camel-ship".equals(command.name()))
                .findFirst()
                .orElseThrow();
        assertEquals(List.of("pi"), ship.skillOnlyAgents(),
                "Pi prompt-file expansion flattens quoted values; Ship must stay skill-only for Pi");
        assertTrue(manifest.generatedCommandStubs().stream()
                .filter(command -> !"camel-ship".equals(command.name()))
                .allMatch(command -> command.skillOnlyAgents().isEmpty()),
                "Only camel-ship declares a skill-only carve-out");
    }

    @Test
    void rejectsUnknownManifestFields() {
        JsonMappingException ex = assertThrows(JsonMappingException.class,
                () -> loadManifest(MINIMAL_VALID_MANIFEST + "unexpected_root: true\n"));
        assertTrue(ex.getMessage().contains("unexpected_root"), ex.getMessage());
    }

    @Test
    void rejectsMissingCollectionSectionsAsValidationErrors() {
        ManifestValidationException ex = assertThrows(ManifestValidationException.class, () -> loadManifest("""
                version: "1.0"
                description: "Missing sections"
                """));

        assertTrue(ex.errors().contains("commands must not be empty"), ex.getMessage());
        assertTrue(ex.errors().contains("skills must not be empty"), ex.getMessage());
        assertTrue(ex.errors().contains("stages must not be empty"), ex.getMessage());
        assertTrue(ex.errors().contains("artifacts must not be empty"), ex.getMessage());
        assertTrue(ex.errors().contains("mcp_servers must not be empty"), ex.getMessage());
    }

    @Test
    void rejectsCommandsReferencingUnknownSkills() {
        assertInvalidManifest(
                MINIMAL_VALID_MANIFEST.replace("skill: camel-start", "skill: missing-skill"),
                "commands[camel-start].skill references unknown skill 'missing-skill'");
    }

    @Test
    void rejectsGeneratedCommandDrift() {
        assertInvalidManifest(
                MINIMAL_VALID_MANIFEST.replace("generated_command: camel-start", "generated_command: camel-plan"),
                "skills[camel-start].generated_command references unknown command 'camel-plan'");
    }

    @Test
    void rejectsStagesReferencingUnknownSkills() {
        assertInvalidManifest(
                MINIMAL_VALID_MANIFEST.replace("""
                          - id: route
                            skill: camel-start
                        """, """
                          - id: route
                            skill: missing-skill
                        """),
                "stages[route].skill references unknown skill 'missing-skill'");
    }

    @Test
    void rejectsStagesReferencingUnknownTransitions() {
        assertInvalidManifest(
                MINIMAL_VALID_MANIFEST.replace("transitions: []", "transitions: [missing-stage]"),
                "stages[route].transitions references unknown stage 'missing-stage'");
    }

    @Test
    void rejectsArtifactsReferencingUnknownSkills() {
        assertInvalidManifest(
                MINIMAL_VALID_MANIFEST.replace("produced_by: [camel-start]", "produced_by: [missing-skill]"),
                "artifacts[design-spec].produced_by references unknown skill 'missing-skill'");
    }

    @Test
    void rejectsMissingRequiredMcpServers() {
        assertInvalidManifest(
                MINIMAL_VALID_MANIFEST.replace("""
                          - id: camel-knowledge
                            display_name: "Camel-Kit Knowledge MCP"
                            description: "Knowledge"
                            allowed_tools: []
                        """, ""),
                "mcp_servers missing required server 'camel-knowledge'");
        assertInvalidManifest(
                MINIMAL_VALID_MANIFEST.replace("""
                          - id: citrus
                            display_name: "Citrus MCP"
                            description: "Citrus"
                            allowed_tools: []
                        """, ""),
                "mcp_servers missing required server 'citrus'");
    }

    @Test
    void internalVerifySkillIsNotGeneratedAsCommandStub() throws Exception {
        WorkflowManifest manifest = WorkflowManifestLoader.loadDefault();
        WorkflowManifest.WorkflowCommand verifyCommand = manifest.commands().stream()
                .filter(command -> "camel-verify".equals(command.name()))
                .findFirst()
                .orElseThrow();
        WorkflowManifest.WorkflowSkill verifySkill = manifest.skills().stream()
                .filter(skill -> "camel-verify".equals(skill.name()))
                .findFirst()
                .orElseThrow();

        assertFalse(verifyCommand.generatedStub());
        assertFalse(verifyCommand.userFacing());
        assertNull(verifySkill.generatedCommand());

        InitContext ctx = createContext("bob");
        new DefaultGenerator().generate(ctx);

        assertFalse(Files.exists(ctx.commandsDir().resolve("camel-verify." + ctx.agent().fileFormat())));
        assertTrue(Files.isRegularFile(ctx.skillsDir().resolve("camel-verify/SKILL.md")));
    }

    @Test
    void skillFrontmatterMatchesManifest() throws Exception {
        WorkflowManifest manifest = WorkflowManifestLoader.loadDefault();
        Map<String, WorkflowManifest.WorkflowSkill> expectedSkills = manifest.skills().stream()
                .collect(Collectors.toMap(
                        WorkflowManifest.WorkflowSkill::name,
                        skill -> skill,
                        (left, right) -> left,
                        LinkedHashMap::new));
        Path skillsDir = resourcePath("skills");

        Set<String> actualSkillNames;
        try (var stream = Files.list(skillsDir)) {
            actualSkillNames = stream
                    .filter(Files::isDirectory)
                    .filter(path -> Files.exists(path.resolve("SKILL.md")))
                    .map(path -> path.getFileName().toString())
                    .collect(Collectors.toCollection(java.util.TreeSet::new));
        }

        assertEquals(expectedSkills.keySet(), actualSkillNames);

        for (String skillName : actualSkillNames) {
            Map<String, Object> frontmatter = readFrontmatter(skillsDir.resolve(skillName).resolve("SKILL.md"));
            WorkflowManifest.WorkflowSkill expected = expectedSkills.get(skillName);
            assertEquals(expected.name(), frontmatter.get("name"), skillName + " name");
            assertEquals(expected.userInvocable(), frontmatter.get("user_invocable"),
                    skillName + " user_invocable");
        }
    }

    @Test
    void generatedMcpAllowlistsMatchManifest() throws Exception {
        WorkflowManifest manifest = WorkflowManifestLoader.loadDefault();
        InitContext ctx = createContext("bob");

        new DefaultGenerator().generate(ctx);

        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> config = mapper.readValue(
                Files.readString(tempDir.resolve(".bob/mcp.json")),
                new TypeReference<>() {
                });
        Map<String, Object> servers = map(config.get("mcpServers"));
        Map<String, Object> knowledge = map(servers.get("camel-knowledge"));
        Map<String, Object> citrus = map(servers.get("citrus"));

        assertEquals(manifest.mcpServer("camel-knowledge").allowedTools(), knowledge.get("autoApprove"));
        assertEquals(manifest.mcpServer("camel-knowledge").allowedTools(), knowledge.get("alwaysAllow"));
        assertEquals(manifest.mcpServer("citrus").allowedTools(), citrus.get("autoApprove"));
        assertEquals(manifest.mcpServer("citrus").allowedTools(), citrus.get("alwaysAllow"));
    }

    @Test
    void knowledgeMcpAllowlistOnlyContainsImplementedTools() throws Exception {
        WorkflowManifest manifest = WorkflowManifestLoader.loadDefault();

        assertEquals(List.of(
                "camel_docs_component_info",
                "camel_docs_search",
                "camel_docs_cve_search",
                "camel_docs_release_info",
                "camel_docs_jira_lookup"),
                manifest.mcpServer("camel-knowledge").allowedTools());
    }

    @Test
    void citrusMcpAllowlistOnlyContainsImplementedTools() throws Exception {
        WorkflowManifest manifest = WorkflowManifestLoader.loadDefault();

        assertEquals(List.of(
                "citrus_catalog_actions",
                "citrus_catalog_action",
                "citrus_catalog_action_schema",
                "citrus_catalog_endpoints",
                "citrus_catalog_endpoint",
                "citrus_catalog_endpoint_schema",
                "citrus_docs_index",
                "citrus_docs_page"),
                manifest.mcpServer("citrus").allowedTools());
    }

    private InitContext createContext(String agentName) {
        AgentConfig agent = AgentRegistry.get(agentName);
        Path skillsDir = tempDir.resolve(agent.skillsDirectory());
        // Mirrors InitService: non-stub agents resolve commandsDir to their skills directory.
        Path commandsDir = agent.generatesCommandStubs()
                ? tempDir.resolve(agent.commandDirectory())
                : skillsDir;
        return new InitContext(
                agent, agentName, commandsDir, skillsDir, tempDir,
                "camel-kit", Printer.noop());
    }

    private static Path resourcePath(String resource) throws Exception {
        URI uri = WorkflowManifestTest.class.getClassLoader().getResource(resource).toURI();
        return Path.of(uri);
    }

    private static void assertInvalidManifest(String yaml, String expectedMessage) {
        ManifestValidationException ex = assertThrows(ManifestValidationException.class, () -> loadManifest(yaml));
        assertTrue(ex.getMessage().contains(expectedMessage), ex.getMessage());
    }

    private static WorkflowManifest loadManifest(String yaml) throws IOException {
        return WorkflowManifestLoader.load(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
    }

    private static Map<String, Object> readFrontmatter(Path skillFile) throws Exception {
        String content = Files.readString(skillFile);
        int start = content.indexOf("---");
        int end = content.indexOf("---", start + 3);
        assertEquals(0, start, "Expected frontmatter at start of " + skillFile);
        assertTrue(end > start, "Expected closing frontmatter marker in " + skillFile);
        String yaml = content.substring(start + 3, end);
        return new ObjectMapper(new YAMLFactory())
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .readValue(yaml, new TypeReference<>() {
                });
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }
}
