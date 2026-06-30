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
                                    + "four\\s+iron\\s+laws|full\\s+set\\s+of\\s+four\\s+iron\\s+laws)\\b")),
            new StalePattern(
                    "legacy per-flow TDD path",
                    Pattern.compile("\\bdocs/flows\\b|\\.camel-kit/flows\\b|\\.tdd\\.md\\b")),
            new StalePattern(
                    "legacy root pipeline artifact path",
                    Pattern.compile("\\bdocs/(business-requirements|implementation-plan)\\.md\\b")),
            new StalePattern(
                    "legacy TDD artifact terminology",
                    Pattern.compile("(?i)(\\bTDD\\b\\s*(Section|file|files|path|content|field|mapping|format|"
                                    + "output|artifact|specification|decomposition|creation|assembly)"
                                    + "|\\b(TDDs|per-flow\\s+TDD|BRD\\+TDD|TDD-level)\\b"
                                    + "|Technical\\s+Design\\s+Document)")),
            new StalePattern(
                    "legacy BRD artifact terminology",
                    Pattern.compile("\\bBRD\\b|Blueprint\\s+Reference\\s+Document")),
            new StalePattern(
                    "uppercase flow-name token in pipeline test-data path",
                    Pattern.compile("docs/camel-kit/<PIPELINE_ID>/test-data/\\{FLOW_NAME\\}")),
            new StalePattern(
                    "retired internal command exposed as slash command",
                    Pattern.compile("(?<![\\w-])/camel-(implement|test|flow)\\b")),
            new StalePattern("legacy camel.version property", Pattern.compile("\\bcamel\\.version\\b")),
            new StalePattern("legacy versions.properties source", Pattern.compile("\\bversions\\.properties\\b")),
            new StalePattern(
                    "legacy runtime enum in config or RUNTIME context",
                    Pattern.compile("(?i)(`?project\\.runtime`?|`RUNTIME`)[^\\n]*\\([^\\n)]*\\b(jbang|springboot)\\b"
                                    + "[^\\n)]*\\)|\\bRUNTIME\\s*==\\s*(jbang|springboot)\\b")),
            new StalePattern(
                    "Iron Law numbering mismatch",
                    Pattern.compile("(?i)(Iron\\s+Law\\s+3[^\\n]*(constitution|all\\s+7)"
                                    + "|constitution[^\\n]*Iron\\s+Law\\s+3"
                                    + "|Iron\\s+Law\\s+4[^\\n]*(No\\s+Code|approval|approved|"
                                    + "generate\\s+ONLY|Produce\\s+the\\s+design\\s+spec))")));

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
    void activeResourceScanIncludesTopLevelDocsOnly() throws IOException {
        Path root = repositoryRoot();

        Set<String> activeResources = activeResourceFiles(root).stream()
                .map(path -> root.relativize(path).toString())
                .collect(Collectors.toSet());
        Set<String> topLevelDocs;
        try (Stream<Path> paths = Files.list(root.resolve("docs"))) {
            topLevelDocs = paths.filter(Files::isRegularFile)
                    .filter(ResourceConsistencyTest::isScannedTextFile)
                    .map(path -> root.relativize(path).toString())
                    .collect(Collectors.toSet());
        }

        assertTrue(activeResources.containsAll(topLevelDocs));
        assertFalse(activeResources.stream().anyMatch(path -> path.startsWith("docs/plans/")));
        assertFalse(activeResources.stream().anyMatch(path -> path.startsWith("docs/superpowers/")));
        assertFalse(activeResources.stream().anyMatch(path -> path.startsWith("docs/flows/")));
        assertFalse(activeResources.stream().anyMatch(path -> path.startsWith("docs/img/")));
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

            JsonNode knowledgeServer = serverConfig(agentName, projectDir, "camel-knowledge");
            assertKnowledgeTools(agentName, "autoApprove", knowledgeServer.get("autoApprove"));
            assertKnowledgeTools(agentName, "alwaysAllow", knowledgeServer.get("alwaysAllow"));

            JsonNode citrusServer = serverConfig(agentName, projectDir, "citrus");
            assertCitrusTools(agentName, "autoApprove", citrusServer.get("autoApprove"));
            assertCitrusTools(agentName, "alwaysAllow", citrusServer.get("alwaysAllow"));
        }
    }

    @Test
    void architectureDocumentsActualCamelRouteTestScaffoldOutput() throws IOException {
        String architecture = Files.readString(repositoryRoot().resolve("docs/architecture.md"));

        assertTrue(architecture.contains("`camel_route_test_scaffold` | Generate a JUnit 5 Camel route test scaffold"),
                "camel_route_test_scaffold currently emits JUnit 5 scaffolds in Camel MCP");
        assertFalse(
                architecture.contains("camel_route_test_scaffold` | Generate a route test scaffold for Citrus YAML"),
                "Architecture docs must not claim camel_route_test_scaffold emits Citrus YAML");
    }

    @Test
    void camelTestGuidesUseConcreteMcpValidationContract() throws IOException {
        Path root = repositoryRoot();
        String mcpSetup = Files.readString(root.resolve(
                "camel-kit-core/src/main/resources/skills/shared/mcp-setup.md"));
        String routeAnalysis = Files.readString(root.resolve(
                "camel-kit-core/src/main/resources/skills/camel-test/guides/route-analysis.md"));
        String testGeneration = Files.readString(root.resolve(
                "camel-kit-core/src/main/resources/skills/camel-test/guides/test-generation.md"));

        assertTrue(mcpSetup.contains("CITRUS_MCP_VERSION == CITRUS_VERSION"),
                "Citrus MCP catalog usage must be gated on matching server and framework versions");
        assertTrue(routeAnalysis.contains("camel_validate_route"),
                "camel-test route analysis must validate routes before generating tests");
        assertTrue(routeAnalysis.contains("camel_validate_yaml_dsl"),
                "camel-test route analysis must validate YAML DSL syntax when applicable");
        assertTrue(routeAnalysis.contains("camel_route_harden_context"),
                "camel-test route analysis must use hardening findings for negative scenarios");
        assertTrue(routeAnalysis.contains("camel_component_properties"),
                "camel-test route analysis must inspect component metadata before writing endpoint config");
        assertTrue(routeAnalysis.contains("citrus_docs_index"),
                "camel-test should discover Citrus docs pages before reading them");
        assertFalse(routeAnalysis.contains("Suggested Test Scenarios (from MCP analysis)"),
                "camel_route_context output must not be presented as ready-made test scenarios");

        assertFalse(testGeneration.contains("\"<action-name>\""),
                "Citrus action schema validation must use actual generated action names");
        assertFalse(testGeneration.contains("\"<endpoint-name>\""),
                "Citrus endpoint schema validation must use actual generated endpoint names");
        assertTrue(testGeneration.contains("For each ACTION in ACTIONS_USED"),
                "test-generation must iterate over actual generated actions");
        assertTrue(testGeneration.contains("For each ENDPOINT in ENDPOINTS_USED"),
                "test-generation must iterate over actual generated endpoints");
        assertFalse(testGeneration.contains("<pinned-compatible-version>"),
                "test-generation must not emit unresolved dependency version placeholders");
        assertTrue(testGeneration.contains("org.testcontainers:postgresql:RELEASE"),
                "test-generation must provide a resolvable PostgreSQL Testcontainers dependency");
        assertTrue(testGeneration.contains("org.testcontainers:mongodb:RELEASE"),
                "test-generation must provide a resolvable MongoDB Testcontainers dependency");
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

    private static void assertCitrusTools(String agentName, String field, JsonNode allowlist) throws IOException {
        assertNotNull(allowlist, "Missing " + field + " allowlist for " + agentName);
        assertTrue(allowlist.isArray(), field + " allowlist for " + agentName + " must be an array");

        Set<String> actual = new LinkedHashSet<>();
        allowlist.forEach(node -> actual.add(node.asText()));
        assertEquals(new LinkedHashSet<>(
                WorkflowManifestLoader.loadDefault()
                        .mcpServer("citrus")
                        .allowedTools()),
                actual,
                field + " allowlist for " + agentName + " must match Citrus MCP tools");
    }

    private JsonNode serverConfig(String agentName, Path projectDir, String serverId) throws IOException {
        AgentConfig agent = AgentRegistry.get(agentName);
        assertNotNull(agent, "Unexpected agent: " + agentName);
        Path configFile = projectDir.resolve(agent.mcpConfigPath());
        JsonNode root = MAPPER.readTree(configFile.toFile());
        JsonNode servers = root.get(agent.mcpServerContainerKey());
        assertNotNull(servers, "Missing MCP server config for " + agentName);
        JsonNode server = servers.get(serverId);
        assertNotNull(server, "Missing " + serverId + " MCP server config for " + agentName);
        return server;
    }

    private static List<Path> activeResourceFiles(Path root) throws IOException {
        List<Path> scanRoots = List.of(
                root.resolve("camel-kit-core/src/main/resources/skills"),
                root.resolve("camel-kit-core/src/main/resources/agents"),
                root.resolve("camel-kit-core/src/main/resources/templates"),
                root.resolve("README.md"),
                root.resolve("CONTRIBUTING.md"));

        List<Path> files = new ArrayList<>();
        addTopLevelDocs(root, files);
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

    private static void addTopLevelDocs(Path root, List<Path> files) throws IOException {
        Path docsDir = root.resolve("docs");
        if (!Files.isDirectory(docsDir)) {
            return;
        }
        try (Stream<Path> paths = Files.list(docsDir)) {
            paths.filter(Files::isRegularFile)
                    .filter(ResourceConsistencyTest::isScannedTextFile)
                    .forEach(files::add);
        }
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
