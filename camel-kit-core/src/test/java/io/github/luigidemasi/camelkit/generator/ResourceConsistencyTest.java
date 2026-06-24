package io.github.luigidemasi.camelkit.generator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.github.luigidemasi.camelkit.config.AgentConfig;
import io.github.luigidemasi.camelkit.config.AgentRegistry;
import io.github.luigidemasi.camelkit.output.Printer;
import io.github.luigidemasi.camelkit.workflow.WorkflowManifestLoader;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class ResourceConsistencyTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final List<StalePattern> STALE_PATTERNS = List.of(
            new StalePattern(".camel-kit/config.yaml", Pattern.compile(Pattern.quote(".camel-kit/config.yaml"))),
            new StalePattern("config.yaml", Pattern.compile("\\bconfig\\.yaml\\b")),
            new StalePattern("graph_stats", Pattern.compile("\\bgraph_stats\\b")),
            new StalePattern("/camel-design", Pattern.compile("(?<![\\w-])/camel-design\\b")),
            new StalePattern("camel_knowledge_search", Pattern.compile("\\bcamel_knowledge_search\\b")),
            new StalePattern("camel_docs_component", Pattern.compile("\\bcamel_docs_component\\b")),
            new StalePattern(
                    "camel_rh_build_component_info",
                    Pattern.compile("\\bcamel_rh_build_component_info\\b")),
            new StalePattern(
                    "outdated Iron Law count",
                    Pattern.compile("(?i)\\b(all\\s+4\\s+laws|all\\s+four\\s+iron\\s+laws|"
                                    + "all\\s+four\\s+laws|the\\s+four\\s+iron\\s+laws|"
                                    + "four\\s+iron\\s+laws|full\\s+set\\s+of\\s+four\\s+iron\\s+laws)\\b")));

    @TempDir
    Path tempDir;

    @Test
    void activeShippedResourcesDoNotContainKnownStaleContractTokens() throws IOException {
        Path root = repositoryRoot();

        List<String> violations = new ArrayList<>();
        for (Path file : activeResourceFiles(root)) {
            String content = Files.readString(file);
            for (StalePattern stale : STALE_PATTERNS) {
                if (stale.pattern().matcher(content).find()) {
                    violations.add(root.relativize(file) + ": " + stale.description());
                }
            }
        }

        assertTrue(violations.isEmpty(),
                "Active shipped resources contain stale contract references:\n"
                                         + String.join("\n", violations));
    }

    @Test
    void generatedCommandStubsMatchIntendedExposedSkills() throws Exception {
        for (String agentName : sortedAgentNames()) {
            Path projectDir = tempDir.resolve(agentName);
            InitContext ctx = createContext(agentName, projectDir);

            new DefaultGenerator().generate(ctx);

            Set<String> generatedCommands;
            try (Stream<Path> files = Files.list(ctx.commandsDir())) {
                generatedCommands = files
                        .filter(Files::isRegularFile)
                        .map(path -> stripExtension(path.getFileName().toString()))
                        .collect(Collectors.toCollection(LinkedHashSet::new));
            }

            Set<String> expectedCommands = WorkflowManifestLoader.loadDefault().generatedCommandStubs().stream()
                    .map(command -> command.name())
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            assertEquals(expectedCommands, generatedCommands,
                    "Generated command stubs for " + agentName + " must match the public slash-command surface");
            assertFalse(generatedCommands.contains("camel-verify"), "camel-verify is internal to camel-execute");
            assertFalse(generatedCommands.contains("camel-implement"), "camel-implement is an internal guide skill");
            assertFalse(generatedCommands.contains("camel-test"), "camel-test is an internal guide skill");
            assertFalse(generatedCommands.contains("camel-design"), "camel-design is an internal guide skill");
        }
    }

    @Test
    void generatedKnowledgeMcpAllowlistsOnlyExposeImplementedTools() throws Exception {
        for (String agentName : sortedAgentNames()) {
            Path projectDir = tempDir.resolve(agentName);
            InitContext ctx = createContext(agentName, projectDir);

            new DefaultGenerator().generate(ctx);

            JsonNode knowledgeServer = knowledgeServerConfig(agentName, projectDir);
            assertKnowledgeTools(agentName, "autoApprove", knowledgeServer.get("autoApprove"));
            assertKnowledgeTools(agentName, "alwaysAllow", knowledgeServer.get("alwaysAllow"));
        }
    }

    private static void assertKnowledgeTools(String agentName, String field, JsonNode allowlist) throws IOException {
        assertNotNull(allowlist, "Missing " + field + " allowlist for " + agentName);
        assertTrue(allowlist.isArray(), field + " allowlist for " + agentName + " must be an array");

        Set<String> actual = new LinkedHashSet<>();
        allowlist.forEach(node -> actual.add(node.asText()));
        assertEquals(new LinkedHashSet<>(
                WorkflowManifestLoader.loadDefault()
                        .mcpServer("camel-knowledge")
                        .allowedTools()),
                actual,
                field + " allowlist for " + agentName + " must match KnowledgeMcpServer tools");
    }

    private JsonNode knowledgeServerConfig(String agentName, Path projectDir) throws IOException {
        AgentConfig agent = AgentRegistry.get(agentName);
        assertNotNull(agent, "Unexpected agent: " + agentName);
        Path configFile = projectDir.resolve(agent.mcpConfigPath());
        JsonNode root = MAPPER.readTree(configFile.toFile());
        JsonNode servers = root.get(agent.mcpServerContainerKey());
        assertNotNull(servers, "Missing MCP server config for " + agentName);
        JsonNode knowledge = servers.get("camel-knowledge");
        assertNotNull(knowledge, "Missing camel-knowledge MCP server config for " + agentName);
        return knowledge;
    }

    private static List<Path> activeResourceFiles(Path root) throws IOException {
        List<Path> scanRoots = List.of(
                root.resolve("camel-kit-core/src/main/resources/skills"),
                root.resolve("camel-kit-core/src/main/resources/templates"),
                root.resolve("docs"),
                root.resolve("README.md"),
                root.resolve("CONTRIBUTING.md"));

        List<Path> files = new ArrayList<>();
        for (Path scanRoot : scanRoots) {
            if (Files.isRegularFile(scanRoot)) {
                if (isScannedTextFile(scanRoot)) {
                    files.add(scanRoot);
                }
                continue;
            }
            try (Stream<Path> paths = Files.walk(scanRoot)) {
                paths.filter(Files::isRegularFile)
                        .filter(ResourceConsistencyTest::isScannedTextFile)
                        .forEach(files::add);
            }
        }
        files.sort(Comparator.comparing(path -> root.relativize(path).toString()));
        return files;
    }

    private static boolean isScannedTextFile(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".md")
                || name.endsWith(".json")
                || name.endsWith(".toml")
                || name.endsWith(".yaml")
                || name.endsWith(".yml")
                || name.equals("geminiignore")
                || name.equals("qwenignore");
    }

    private static InitContext createContext(String agentName, Path projectDir) {
        AgentConfig agent = AgentRegistry.get(agentName);
        String agentBaseFolder = agent.folder().substring(0, agent.folder().lastIndexOf("/"));
        Path commandsDir = projectDir.resolve(agent.folder());
        Path skillsDir = projectDir.resolve(agentBaseFolder + "/skills");
        return new InitContext(
                agent, agentName, commandsDir, skillsDir, projectDir,
                "camel-kit", Printer.noop());
    }

    private static List<String> sortedAgentNames() {
        return AgentRegistry.names().stream()
                .sorted()
                .toList();
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0) {
            return name;
        }
        return name.substring(0, dot);
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve("pom.xml"))
                    && Files.exists(current.resolve("camel-kit-core/src/main/resources"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Could not locate repository root from " + Path.of("").toAbsolutePath());
    }

    private record StalePattern(String description, Pattern pattern) {
    }
}
