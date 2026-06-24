package io.github.luigidemasi.camelkit.workflow;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.luigidemasi.camelkit.config.AgentConfig;
import io.github.luigidemasi.camelkit.config.AgentRegistry;
import io.github.luigidemasi.camelkit.generator.DefaultGenerator;
import io.github.luigidemasi.camelkit.generator.InitContext;
import io.github.luigidemasi.camelkit.output.Printer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class WorkflowManifestTest {

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
    void generatedCommandStubsMatchManifest() throws Exception {
        WorkflowManifest manifest = WorkflowManifestLoader.loadDefault();
        InitContext ctx = createContext("bob");

        new DefaultGenerator().generate(ctx);

        Set<String> expected = manifest.generatedCommandStubs().stream()
                .map(command -> command.name() + "." + ctx.agent().fileFormat())
                .collect(Collectors.toCollection(java.util.TreeSet::new));
        Set<String> actual;
        try (var stream = Files.list(ctx.commandsDir())) {
            actual = stream
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .collect(Collectors.toCollection(java.util.TreeSet::new));
        }

        assertEquals(expected, actual);
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
    void generatedKnowledgeMcpAllowlistMatchesManifest() throws Exception {
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

        assertEquals(manifest.mcpServer("camel-knowledge").allowedTools(), knowledge.get("autoApprove"));
        assertEquals(manifest.mcpServer("camel-knowledge").allowedTools(), knowledge.get("alwaysAllow"));
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

    private InitContext createContext(String agentName) {
        AgentConfig agent = AgentRegistry.get(agentName);
        String agentBaseFolder = agent.folder().substring(0, agent.folder().lastIndexOf("/"));
        Path commandsDir = tempDir.resolve(agent.folder());
        Path skillsDir = tempDir.resolve(agentBaseFolder + "/skills");
        return new InitContext(
                agent, agentName, commandsDir, skillsDir, tempDir,
                "camel-kit", Printer.noop());
    }

    private static Path resourcePath(String resource) throws Exception {
        URI uri = WorkflowManifestTest.class.getClassLoader().getResource(resource).toURI();
        return Path.of(uri);
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
