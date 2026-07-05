package io.github.luigidemasi.camelkit.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Stream;

import io.github.luigidemasi.camelkit.config.AgentConfig;
import io.github.luigidemasi.camelkit.config.AgentGeneratorStrategy;
import io.github.luigidemasi.camelkit.config.AgentRegistry;
import io.github.luigidemasi.camelkit.graph.GraphBuildResult;
import io.github.luigidemasi.camelkit.graph.GraphBuilder;
import io.github.luigidemasi.camelkit.graph.ParserDiagnostic;
import io.github.luigidemasi.camelkit.util.PrerequisiteChecker;
import io.github.luigidemasi.camelkit.util.ProcessRunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Validates generated Camel-Kit workspaces.
 */
public class DoctorService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Duration PREREQUISITE_TIMEOUT = Duration.ofSeconds(3);

    private static final List<String> REQUIRED_CONFIG_KEYS = List.of(
            "project.name", "project.command-prefix", "project.runtime", "project.camelVersion",
            "project.platformBomVersion", "agent.name", "agent.folder");

    private static final List<String> STALE_REFERENCES = List.of(
            "/camel-design", ".camel-kit/business-requirements.md", ".camel-kit/flows/");

    private final DoctorExpectations expectations;

    public DoctorService() {
        this(DoctorExpectations.loadDefault());
    }

    DoctorService(DoctorExpectations expectations) {
        this.expectations = expectations;
    }

    public DoctorResult inspect(DoctorRequest request) {
        Path root = request.projectDir().toAbsolutePath().normalize();
        List<DoctorFinding> findings = new ArrayList<>();

        Properties config = checkConfig(root, findings);
        AgentConfig agent = checkAgentConfig(config, findings);
        checkGeneratedWorkspace(root, agent, findings);
        checkMcp(root, config, findings);
        checkGraph(root, findings);
        checkCommandPrefix(config, findings);
        checkPrerequisites(root, findings);
        checkStaleReferences(root, agent, findings);

        return new DoctorResult(root, findings);
    }

    private Properties checkConfig(Path root, List<DoctorFinding> findings) {
        Path configFile = root.resolve(".camel-kit/config.properties");
        if (!Files.isRegularFile(configFile)) {
            findings.add(DoctorFinding.fail("config", relativize(root, configFile),
                    ".camel-kit/config.properties is missing",
                    "Run camel-kit init --here --ai <agent> from the workspace root."));
            return new Properties();
        }

        Properties config = new Properties();
        try (InputStream in = Files.newInputStream(configFile)) {
            config.load(in);
        } catch (IOException e) {
            findings.add(DoctorFinding.fail("config", relativize(root, configFile),
                    "Could not read .camel-kit/config.properties: " + e.getMessage(),
                    "Recreate the file with camel-kit init --here --force or restore it from source control."));
            return config;
        }

        List<String> missing = REQUIRED_CONFIG_KEYS.stream()
                .filter(key -> config.getProperty(key, "").isBlank())
                .toList();
        if (missing.isEmpty()) {
            findings.add(DoctorFinding.pass("config", relativize(root, configFile),
                    ".camel-kit/config.properties contains the required keys",
                    "No action required."));
        } else {
            findings.add(DoctorFinding.fail("config", relativize(root, configFile),
                    ".camel-kit/config.properties is missing keys: " + String.join(", ", missing),
                    "Restore the missing keys or re-run camel-kit init --here --ai <agent> --force."));
        }
        return config;
    }

    private AgentConfig checkAgentConfig(Properties config, List<DoctorFinding> findings) {
        String agentName = config.getProperty("agent.name", "").trim();
        if (agentName.isEmpty()) {
            findings.add(DoctorFinding.fail("config", ".camel-kit/config.properties",
                    "agent.name is not configured",
                    "Set agent.name to one of: " + String.join(", ", AgentRegistry.names()) + "."));
            return null;
        }
        if (!AgentRegistry.contains(agentName)) {
            findings.add(DoctorFinding.fail("config", ".camel-kit/config.properties",
                    "Unknown agent.name '" + agentName + "'",
                    "Set agent.name to one of: " + String.join(", ", AgentRegistry.names()) + "."));
            return null;
        }

        AgentConfig agent = AgentRegistry.get(agentName);
        String folder = config.getProperty("agent.folder", "").trim();
        if (agent.folder().equals(folder)) {
            findings.add(DoctorFinding.pass("config", ".camel-kit/config.properties",
                    "Agent configuration selects " + agent.name(),
                    "No action required."));
        } else {
            findings.add(DoctorFinding.fail("config", ".camel-kit/config.properties",
                    "agent.folder is '" + folder + "' but " + agentName + " expects '" + agent.folder() + "'",
                    "Set agent.folder=" + agent.folder() + " or re-run camel-kit init for " + agentName + "."));
        }
        return agent;
    }

    private void checkGeneratedWorkspace(Path root, AgentConfig agent, List<DoctorFinding> findings) {
        if (agent == null) {
            findings.add(DoctorFinding.fail("workspace", null,
                    "Cannot validate generated commands and skills without a valid agent",
                    "Fix .camel-kit/config.properties and re-run camel-kit doctor."));
            return;
        }

        Path commandsDir = root.resolve(agent.folder());
        if (!Files.isDirectory(commandsDir)) {
            findings.add(DoctorFinding.fail("workspace", relativize(root, commandsDir),
                    "Generated command directory is missing",
                    "Run camel-kit init --here --ai " + agentKey(agent) + " --force to regenerate commands."));
        } else {
            checkCommandFiles(root, commandsDir, agent, findings);
        }

        Path skillsDir = root.resolve(agent.folder()).getParent().resolve("skills");
        if (!Files.isDirectory(skillsDir)) {
            findings.add(DoctorFinding.fail("skills", relativize(root, skillsDir),
                    "Generated skills directory is missing",
                    "Run camel-kit init --here --ai " + agentKey(agent) + " --force to regenerate skills."));
            return;
        }

        List<String> missingSkills = expectations.requiredSkills().stream()
                .filter(skill -> !Files.isRegularFile(skillsDir.resolve(skill).resolve("SKILL.md")))
                .toList();
        if (missingSkills.isEmpty()) {
            findings.add(DoctorFinding.pass("skills", relativize(root, skillsDir),
                    "All expected skill directories contain SKILL.md",
                    "No action required."));
        } else {
            findings.add(DoctorFinding.fail("skills", relativize(root, skillsDir),
                    "Missing SKILL.md for: " + String.join(", ", missingSkills),
                    "Restore the missing skill folders or re-run camel-kit init --here --force."));
        }
    }

    private void checkCommandFiles(
            Path root, Path commandsDir, AgentConfig agent, List<DoctorFinding> findings) {
        String extension = "." + agent.fileFormat();
        List<String> missing = expectations.userCommands().stream()
                .filter(command -> !Files.isRegularFile(commandsDir.resolve(command + extension)))
                .toList();

        Set<String> unexpected = new LinkedHashSet<>();
        try (Stream<Path> stream = Files.list(commandsDir)) {
            stream.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith("camel-"))
                    .map(this::commandName)
                    .filter(command -> !expectations.userCommands().contains(command))
                    .forEach(unexpected::add);
        } catch (IOException e) {
            findings.add(DoctorFinding.fail("workspace", relativize(root, commandsDir),
                    "Could not list generated command directory: " + e.getMessage(),
                    "Check filesystem permissions and re-run camel-kit doctor."));
            return;
        }

        if (missing.isEmpty() && unexpected.isEmpty()) {
            findings.add(DoctorFinding.pass("workspace", relativize(root, commandsDir),
                    "Generated user-invocable commands match the expected skill router set",
                    "No action required."));
            return;
        }

        List<String> problems = new ArrayList<>();
        if (!missing.isEmpty()) {
            problems.add("missing " + String.join(", ", missing));
        }
        if (!unexpected.isEmpty()) {
            problems.add("unexpected " + String.join(", ", unexpected));
        }
        findings.add(DoctorFinding.fail("workspace", relativize(root, commandsDir),
                "Generated command directory has " + String.join("; ", problems),
                "Remove unexpected command stubs and re-run camel-kit init --here --force if required files are missing."));
    }

    private void checkMcp(Path root, Properties config, List<DoctorFinding> findings) {
        String agentName = config.getProperty("agent.name", "").trim();
        boolean copilotToolsSchema = AgentGeneratorStrategy.COPILOT.descriptorValue()
                .equals(agentName.toLowerCase(Locale.ROOT));
        Path mcpFile = mcpConfigPath(root, agentName);
        if (mcpFile == null) {
            findings.add(DoctorFinding.fail("mcp", null,
                    "Cannot determine MCP config path for agent '" + agentName + "'",
                    "Fix agent.name in .camel-kit/config.properties."));
            return;
        }
        if (!Files.isRegularFile(mcpFile)) {
            findings.add(DoctorFinding.fail("mcp", relativize(root, mcpFile),
                    "MCP config is missing for agent '" + agentName + "'",
                    "Run camel-kit init --here --ai " + agentName + " --force to regenerate MCP configuration."));
            return;
        }

        JsonNode rootNode;
        try {
            rootNode = MAPPER.readTree(mcpFile.toFile());
        } catch (IOException e) {
            findings.add(DoctorFinding.fail("mcp", relativize(root, mcpFile),
                    "MCP config is not valid JSON: " + e.getMessage(),
                    "Fix the JSON syntax or regenerate the MCP config with camel-kit init --here --force."));
            return;
        }

        JsonNode servers = rootNode.has("mcp") ? rootNode.path("mcp") : rootNode.path("mcpServers");
        if (!servers.isObject()) {
            findings.add(DoctorFinding.fail("mcp", relativize(root, mcpFile),
                    "MCP config does not contain an mcp or mcpServers object",
                    "Regenerate the MCP config with camel-kit init --here --force."));
            return;
        }

        boolean camelOk = checkMcpServer(
                root, mcpFile, servers, "camel", expectations.camelMcpTools(), copilotToolsSchema, findings);
        boolean knowledgeOk = checkMcpServer(
                root, mcpFile, servers, "camel-knowledge", expectations.knowledgeMcpTools(), copilotToolsSchema,
                findings);
        if (camelOk && knowledgeOk) {
            findings.add(DoctorFinding.pass("mcp", relativize(root, mcpFile),
                    "MCP config exists and tool allowlists match Camel-Kit expectations",
                    "No action required."));
        }
    }

    private boolean checkMcpServer(
            Path root, Path mcpFile, JsonNode servers, String serverName, Set<String> expected,
            boolean copilotToolsSchema, List<DoctorFinding> findings) {
        JsonNode server = servers.path(serverName);
        if (!server.isObject()) {
            findings.add(DoctorFinding.fail("mcp", relativize(root, mcpFile),
                    "MCP server '" + serverName + "' is missing",
                    "Regenerate the MCP config with camel-kit init --here --force."));
            return false;
        }

        if (copilotToolsSchema) {
            return checkCopilotTools(root, mcpFile, serverName, server, expected, findings);
        }

        boolean autoApproveOk = checkAllowlist(root, mcpFile, serverName, "autoApprove", server, expected, findings);
        boolean alwaysAllowOk = checkAllowlist(root, mcpFile, serverName, "alwaysAllow", server, expected, findings);
        return autoApproveOk && alwaysAllowOk;
    }

    private boolean checkCopilotTools(
            Path root, Path mcpFile, String serverName, JsonNode server, Set<String> expected,
            List<DoctorFinding> findings) {
        JsonNode tools = server.path("tools");
        if (tools.isMissingNode() || tools.isNull()) {
            findings.add(DoctorFinding.fail("mcp", relativize(root, mcpFile),
                    "MCP server '" + serverName + "' is missing tools",
                    "Regenerate the MCP config with camel-kit init --here --force."));
            return false;
        }

        Set<String> actual = new LinkedHashSet<>();
        if (tools.isArray()) {
            tools.forEach(value -> actual.add(value.asText()));
            return checkToolSet(root, mcpFile, serverName, "tools", actual, expected, true, findings);
        }
        if (tools.isTextual()) {
            for (String value : tools.asText().split(",")) {
                String trimmed = value.trim();
                if (!trimmed.isEmpty()) {
                    actual.add(trimmed);
                }
            }
            return checkToolSet(root, mcpFile, serverName, "tools", actual, expected, true, findings);
        }

        findings.add(DoctorFinding.fail("mcp", relativize(root, mcpFile),
                "MCP server '" + serverName + "' tools must be an array or comma-separated string",
                "Set tools to [\"*\"] or the generated Camel-Kit tool list, then re-run camel-kit doctor."));
        return false;
    }

    private boolean checkAllowlist(
            Path root, Path mcpFile, String serverName, String field, JsonNode server, Set<String> expected,
            List<DoctorFinding> findings) {
        JsonNode node = server.path(field);
        if (!node.isArray()) {
            findings.add(DoctorFinding.fail("mcp", relativize(root, mcpFile),
                    "MCP server '" + serverName + "' is missing " + field + " array",
                    "Regenerate the MCP config with camel-kit init --here --force."));
            return false;
        }

        Set<String> actual = new LinkedHashSet<>();
        node.forEach(value -> actual.add(value.asText()));
        return checkToolSet(root, mcpFile, serverName, field, actual, expected, false, findings);
    }

    private boolean checkToolSet(
            Path root, Path mcpFile, String serverName, String field, Set<String> actual, Set<String> expected,
            boolean allowWildcard, List<DoctorFinding> findings) {
        if (allowWildcard && actual.contains("*")) {
            findings.add(DoctorFinding.warn("mcp", relativize(root, mcpFile),
                    "MCP server '" + serverName + "' " + field + " field allows all tools with '*'",
                    "Prefer the generated Camel-Kit tool allowlist for least privilege unless broader access is intentional."));
            return true;
        }

        Set<String> missing = new LinkedHashSet<>(expected);
        missing.removeAll(actual);
        Set<String> extra = new LinkedHashSet<>(actual);
        extra.removeAll(expected);
        if (missing.isEmpty() && extra.isEmpty()) {
            return true;
        }

        List<String> problems = new ArrayList<>();
        if (!missing.isEmpty()) {
            problems.add("missing " + String.join(", ", missing));
        }
        if (!extra.isEmpty()) {
            problems.add("extra " + String.join(", ", extra));
        }
        findings.add(DoctorFinding.fail("mcp", relativize(root, mcpFile),
                "MCP server '" + serverName + "' " + field + " has " + String.join("; ", problems),
                "Regenerate the MCP config or update the allowlist to match the generated Camel-Kit tools."));
        return false;
    }

    private void checkGraph(Path root, List<DoctorFinding> findings) {
        Path graphFile = root.resolve(".camel-kit/project-graph.json");
        if (Files.isRegularFile(graphFile)) {
            try {
                MAPPER.readTree(graphFile.toFile());
                findings.add(DoctorFinding.pass("graph", relativize(root, graphFile),
                        ".camel-kit/project-graph.json exists and is valid JSON",
                        "No action required."));
            } catch (IOException e) {
                findings.add(DoctorFinding.fail("graph", relativize(root, graphFile),
                        ".camel-kit/project-graph.json is not valid JSON: " + e.getMessage(),
                        "Run camel-kit graph generate --project-dir " + root + " to regenerate it."));
            }
            return;
        }

        try {
            GraphBuildResult result = new GraphBuilder().buildWithDiagnostics(root);
            for (ParserDiagnostic diagnostic : result.warningDiagnostics()) {
                findings.add(DoctorFinding.warn("graph", relativize(root, graphFile),
                        "Graph generation warning: " + diagnostic.summary(),
                        "Inspect the listed source file and regenerate the graph after fixing it."));
            }
            if (!result.successful()) {
                findings.add(DoctorFinding.fail("graph", relativize(root, graphFile),
                        ".camel-kit/project-graph.json is missing and graph generation failed: "
                                                                                      + result.failureSummary(),
                        "Fix the project files reported by graph generation, then run camel-kit graph generate."));
                return;
            }
            findings.add(DoctorFinding.warn("graph", relativize(root, graphFile),
                    ".camel-kit/project-graph.json is missing, but graph generation can run",
                    "Run camel-kit graph generate --project-dir " + root + " to persist the graph."));
        } catch (Exception e) {
            findings.add(DoctorFinding.fail("graph", relativize(root, graphFile),
                    ".camel-kit/project-graph.json is missing and graph generation failed: " + e.getMessage(),
                    "Fix the project files reported by graph generation, then run camel-kit graph generate."));
        }
    }

    private void checkCommandPrefix(Properties config, List<DoctorFinding> findings) {
        String prefix = config.getProperty("project.command-prefix", "").trim();
        if ("camel-kit".equals(prefix) || "camel kit".equals(prefix)) {
            findings.add(DoctorFinding.pass("config", ".camel-kit/config.properties",
                    "project.command-prefix uses a supported invocation style: " + prefix,
                    "No action required."));
        } else {
            findings.add(DoctorFinding.warn("config", ".camel-kit/config.properties",
                    "project.command-prefix is not a known invocation style: " + prefix,
                    "Set project.command-prefix to camel-kit or camel kit, matching how this CLI is installed."));
        }
    }

    private void checkPrerequisites(Path root, List<DoctorFinding> findings) {
        ToolResult java = runTool(null, "java", "-version");
        if (java.available() && javaMajorVersion(java.output()) >= 17) {
            findings.add(DoctorFinding.pass("prerequisites", null,
                    "Java 17+ is available",
                    "No action required."));
        } else if (java.available()) {
            findings.add(DoctorFinding.warn("prerequisites", null,
                    "Java is available but the version could not be confirmed as 17+",
                    "Install or select JDK 17+ before running generated Camel projects."));
        } else {
            findings.add(DoctorFinding.warn("prerequisites", null,
                    "Java 17+ was not found on PATH",
                    "Install JDK 17+ before running generated Camel projects."));
        }

        Path mvnw = root.resolve(isWindows() ? "mvnw.cmd" : "mvnw");
        if (Files.isRegularFile(mvnw)) {
            findings.add(DoctorFinding.pass("prerequisites", relativize(root, mvnw),
                    "Maven Wrapper is present",
                    "No action required."));
        } else if (runTool(root, "mvn", "-version").available()) {
            findings.add(DoctorFinding.warn("prerequisites", null,
                    "Maven is available, but the generated Maven Wrapper is missing",
                    "Run camel-kit init --here --force to restore mvnw and .mvn/wrapper."));
        } else {
            findings.add(DoctorFinding.warn("prerequisites", null,
                    "Neither Maven Wrapper nor mvn was found",
                    "Install Maven or restore the generated Maven Wrapper with camel-kit init --here --force."));
        }

        if (runTool(null, "jbang", "version").available()) {
            findings.add(DoctorFinding.pass("prerequisites", null,
                    "JBang is available for MCP server launch commands",
                    "No action required."));
        } else {
            findings.add(DoctorFinding.warn("prerequisites", null,
                    "JBang was not found on PATH",
                    "Install JBang so generated MCP server commands can run."));
        }

        if (runTool(null, "camel", "--version").available()) {
            findings.add(DoctorFinding.pass("prerequisites", null,
                    "Camel JBang is available",
                    "No action required."));
        } else {
            findings.add(DoctorFinding.warn("prerequisites", null,
                    "Camel JBang was not found on PATH",
                    "Install Camel JBang for local graph, validation, and verification workflows."));
        }
    }

    private void checkStaleReferences(Path root, AgentConfig agent, List<DoctorFinding> findings) {
        List<Path> activeFiles = new ArrayList<>();
        addIfExists(activeFiles, root.resolve("AGENTS.md"));
        addIfExists(activeFiles, root.resolve("CLAUDE.md"));
        addIfExists(activeFiles, root.resolve("GEMINI.md"));
        addIfExists(activeFiles, root.resolve("QWEN.md"));
        addIfExists(activeFiles, root.resolve("opencode.json"));
        if (agent != null) {
            Path commandsDir = root.resolve(agent.folder());
            if (Files.isDirectory(commandsDir)) {
                try (Stream<Path> stream = Files.list(commandsDir)) {
                    stream.filter(Files::isRegularFile).forEach(activeFiles::add);
                } catch (IOException e) {
                    findings.add(DoctorFinding.warn("workspace", relativize(root, commandsDir),
                            "Could not scan generated command files for stale references: " + e.getMessage(),
                            "Check filesystem permissions and re-run camel-kit doctor."));
                }
            }
            addIfExists(activeFiles, mcpConfigPath(root, agentKey(agent)));
        }

        Map<Path, List<String>> matches = new LinkedHashMap<>();
        for (Path file : activeFiles) {
            try {
                String content = Files.readString(file, StandardCharsets.UTF_8);
                List<String> found = STALE_REFERENCES.stream()
                        .filter(content::contains)
                        .toList();
                if (!found.isEmpty()) {
                    matches.put(file, found);
                }
            } catch (IOException e) {
                findings.add(DoctorFinding.warn("workspace", relativize(root, file),
                        "Could not scan active generated file for stale references: " + e.getMessage(),
                        "Check filesystem permissions and re-run camel-kit doctor."));
            }
        }

        if (matches.isEmpty()) {
            findings.add(DoctorFinding.pass("workspace", null,
                    "Active generated files do not contain common stale resource references",
                    "No action required."));
            return;
        }

        List<String> details = matches.entrySet().stream()
                .map(entry -> relativize(root, entry.getKey()) + " -> " + String.join(", ", entry.getValue()))
                .toList();
        findings.add(DoctorFinding.fail("workspace", null,
                "Active generated files contain stale references: " + String.join("; ", details),
                "Regenerate the workspace or update the listed files to current Camel-Kit paths and commands."));
    }

    private ToolResult runTool(Path directory, String... command) {
        ProcessRunner.Result result = ProcessRunner.run(directory, PREREQUISITE_TIMEOUT, command);
        return new ToolResult(result.succeeded(), result.output());
    }

    private int javaMajorVersion(String output) {
        String version = PrerequisiteChecker.parseJavaVersion(output);
        return version == null ? 0 : PrerequisiteChecker.parseMajorVersion(version);
    }

    private Path mcpConfigPath(Path root, String agentName) {
        String key = agentName == null ? "" : agentName.toLowerCase(Locale.ROOT);
        AgentConfig agent = AgentRegistry.get(key);
        if (agent == null) {
            return null;
        }
        return root.resolve(agent.mcpConfigPath());
    }

    private String commandName(String filename) {
        int dot = filename.indexOf('.');
        return dot == -1 ? filename : filename.substring(0, dot);
    }

    private String agentKey(AgentConfig agent) {
        return AgentRegistry.all().entrySet().stream()
                .filter(entry -> entry.getValue().equals(agent))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(agent.name());
    }

    private String relativize(Path root, Path path) {
        if (path == null) {
            return null;
        }
        try {
            return root.relativize(path.toAbsolutePath().normalize()).toString();
        } catch (IllegalArgumentException e) {
            return path.toString();
        }
    }

    private void addIfExists(List<Path> paths, Path path) {
        if (path != null && Files.isRegularFile(path)) {
            paths.add(path);
        }
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    record ToolResult(boolean available, String output) {
    }
}
