package io.github.luigidemasi.camelkit.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
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
import java.util.regex.Pattern;
import java.util.stream.Stream;

import io.github.luigidemasi.camelkit.config.AgentConfig;
import io.github.luigidemasi.camelkit.config.AgentDescriptor;
import io.github.luigidemasi.camelkit.config.AgentGeneratorStrategy;
import io.github.luigidemasi.camelkit.config.AgentRegistry;
import io.github.luigidemasi.camelkit.config.OpenCodeProjectConfig;
import io.github.luigidemasi.camelkit.graph.GraphBuildResult;
import io.github.luigidemasi.camelkit.graph.GraphBuilder;
import io.github.luigidemasi.camelkit.graph.ParserDiagnostic;
import io.github.luigidemasi.camelkit.util.PrerequisiteChecker;
import io.github.luigidemasi.camelkit.util.ProcessRunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/**
 * Validates generated Camel-Kit workspaces.
 */
public class DoctorService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ObjectMapper OPENCODE_MAPPER = OpenCodeProjectConfig.newJsonMapper();
    private static final Duration PREREQUISITE_TIMEOUT = Duration.ofSeconds(3);
    private static final Set<String> OPENCODE_PERMISSION_ACTIONS = Set.of("allow", "ask", "deny");

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

        if (agent.generatesCommandStubs()) {
            Path commandsDir = root.resolve(agent.commandDirectory());
            if (!Files.isDirectory(commandsDir)) {
                findings.add(DoctorFinding.fail("workspace", relativize(root, commandsDir),
                        "Generated command directory is missing",
                        "Run camel-kit init --here --ai " + agentKey(agent) + " --force to regenerate commands."));
            } else {
                checkCommandFiles(root, commandsDir, agent, findings);
            }
        }

        Path skillsDir = root.resolve(agent.skillsDirectory());
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
        checkAgentSpecificWorkspace(root, agent, findings);
    }

    private void checkAgentSpecificWorkspace(Path root, AgentConfig agent, List<DoctorFinding> findings) {
        checkRegisteredTemplates(root, agent, findings);
        if (AgentGeneratorStrategy.PI.descriptorValue().equals(agentKey(agent))) {
            checkPiGuard(root, findings);
        }
        if (AgentGeneratorStrategy.CODEX.descriptorValue().equals(agentKey(agent))) {
            checkCodexAgents(root, findings);
        }
    }

    private void checkRegisteredTemplates(Path root, AgentConfig agent, List<DoctorFinding> findings) {
        String agentName = agentKey(agent);
        List<String> missing = AgentRegistry.descriptor(agentName).templates().stream()
                .map(AgentDescriptor.TemplateInstall::target)
                .filter(target -> !target.startsWith(agent.skillsDirectory() + "/"))
                .filter(target -> !isSeparatelyValidatedTemplate(agentName, target))
                .filter(target -> !Files.isRegularFile(root.resolve(target)))
                .toList();
        if (missing.isEmpty()) {
            findings.add(DoctorFinding.pass("workspace", null,
                    "All registered " + agentName + " template assets are present",
                    "No action required."));
        } else {
            findings.add(DoctorFinding.warn("workspace", null,
                    "Registered " + agentName + " template assets are missing: " + String.join(", ", missing),
                    "Regenerate the workspace with camel-kit init --here --ai " + agentName + " --force."));
        }
    }

    private boolean isSeparatelyValidatedTemplate(String agentName, String target) {
        return (AgentGeneratorStrategy.CODEX.descriptorValue().equals(agentName)
                && target.startsWith(".codex/agents/"))
                || (AgentGeneratorStrategy.PI.descriptorValue().equals(agentName)
                        && (target.equals(".pi/extensions/camel-kit-guard.ts")
                                || target.equals(".pi/camel-kit-guard-policy.json")));
    }

    private void checkCodexAgents(Path root, List<DoctorFinding> findings) {
        AgentDescriptor descriptor = AgentRegistry.descriptor(AgentGeneratorStrategy.CODEX.descriptorValue());
        boolean valid = true;
        for (AgentDescriptor.TemplateInstall template : descriptor.templates()) {
            if (!template.target().startsWith(".codex/agents/") || !template.target().endsWith(".toml")) {
                continue;
            }

            Path agentFile = root.resolve(template.target());
            if (!Files.isRegularFile(agentFile)) {
                findings.add(DoctorFinding.fail("workspace", relativize(root, agentFile),
                        "Codex custom agent is missing",
                        "Run camel-kit init --here --ai codex --force to regenerate Codex agents."));
                valid = false;
                continue;
            }

            try {
                TomlParseResult parsed = Toml.parse(agentFile);
                if (parsed.hasErrors()) {
                    findings.add(DoctorFinding.fail("workspace", relativize(root, agentFile),
                            "Codex custom agent is not valid TOML: " + parsed.errors().get(0),
                            "Fix the TOML syntax or regenerate Codex agents with camel-kit init --here --ai codex --force."));
                    valid = false;
                    continue;
                }
                List<String> missing = List.of("name", "description", "developer_instructions").stream()
                        .filter(key -> !(parsed.get(key) instanceof String value) || value.isBlank())
                        .toList();
                if (!missing.isEmpty()) {
                    findings.add(DoctorFinding.fail("workspace", relativize(root, agentFile),
                            "Codex custom agent is missing required fields: " + String.join(", ", missing),
                            "Restore the required fields or regenerate Codex agents with camel-kit init --here --ai codex --force."));
                    valid = false;
                }
                if (isReadOnlyCodexAgent(template.target())
                        && !"read-only".equals(parsed.get("sandbox_mode"))) {
                    findings.add(DoctorFinding.fail("workspace", relativize(root, agentFile),
                            "Codex research or security agent must declare sandbox_mode = 'read-only'",
                            "Restore sandbox_mode = \"read-only\" or regenerate Codex agents with camel-kit init --here --ai codex --force."));
                    valid = false;
                }
            } catch (IOException e) {
                findings.add(DoctorFinding.fail("workspace", relativize(root, agentFile),
                        "Could not read Codex custom agent: " + e.getMessage(),
                        "Check filesystem permissions and re-run camel-kit doctor."));
                valid = false;
            }
        }

        if (valid) {
            findings.add(DoctorFinding.pass("workspace", ".codex/agents",
                    "Codex custom agents are valid TOML and declare the required fields",
                    "No action required."));
        }
    }

    private boolean isReadOnlyCodexAgent(String target) {
        return target.endsWith("camel-catalog-researcher.toml")
                || target.endsWith("camel-security-reviewer.toml");
    }

    private void checkPiGuard(Path root, List<DoctorFinding> findings) {
        Path extension = root.resolve(".pi/extensions/camel-kit-guard.ts");
        Path policy = root.resolve(".pi/camel-kit-guard-policy.json");
        boolean extensionOk = Files.isRegularFile(extension);
        boolean policyOk = false;

        if (!extensionOk) {
            findings.add(DoctorFinding.fail("workspace", relativize(root, extension),
                    "Pi safety guard extension is missing",
                    "Run camel-kit init --here --ai pi --force to regenerate Pi guard resources."));
        }

        if (!Files.isRegularFile(policy)) {
            findings.add(DoctorFinding.fail("workspace", relativize(root, policy),
                    "Pi safety guard policy is missing",
                    "Run camel-kit init --here --ai pi --force to regenerate Pi guard resources."));
        } else {
            policyOk = checkPiGuardPolicy(root, policy, findings);
        }

        if (extensionOk && policyOk) {
            findings.add(DoctorFinding.pass("workspace", relativize(root, extension),
                    "Pi safety guard extension and policy are present",
                    "No action required."));
        }
    }

    private boolean checkPiGuardPolicy(Path root, Path policy, List<DoctorFinding> findings) {
        JsonNode rootNode;
        try {
            rootNode = MAPPER.readTree(policy.toFile());
        } catch (IOException e) {
            findings.add(DoctorFinding.fail("workspace", relativize(root, policy),
                    "Pi safety guard policy is not valid JSON: " + e.getMessage(),
                    "Fix the JSON syntax or regenerate Pi guard resources with camel-kit init --here --ai pi --force."));
            return false;
        }

        boolean valid = true;
        JsonNode version = rootNode.path("version");
        if (!version.isInt() || version.asInt() != 1 || !rootNode.path("rules").isArray()) {
            findings.add(DoctorFinding.fail("workspace", relativize(root, policy),
                    "Pi safety guard policy must declare version 1 and a rules array",
                    "Regenerate Pi guard resources with camel-kit init --here --ai pi --force."));
            valid = false;
        }

        JsonNode rules = rootNode.path("rules");
        if (rules.isArray()) {
            for (int i = 0; i < rules.size(); i++) {
                valid &= checkPiGuardRule(root, policy, rules.get(i), i, findings);
            }
        }
        return valid;
    }

    private boolean checkPiGuardRule(
            Path root, Path policy, JsonNode rule, int index, List<DoctorFinding> findings) {
        boolean valid = true;
        if (!rule.isObject()) {
            findings.add(DoctorFinding.fail("workspace", relativize(root, policy),
                    "Pi safety guard policy rule at index " + index + " must be an object",
                    "Ensure each rule declares inputPattern, reason, and optional toolNames."));
            return false;
        }

        if (!isNonBlankText(rule.path("inputPattern"))) {
            findings.add(DoctorFinding.fail("workspace", relativize(root, policy),
                    "Pi safety guard policy rule at index " + index + " must declare a non-empty inputPattern",
                    "Regenerate Pi guard resources with camel-kit init --here --ai pi --force."));
            valid = false;
        }
        if (!isNonBlankText(rule.path("reason"))) {
            findings.add(DoctorFinding.fail("workspace", relativize(root, policy),
                    "Pi safety guard policy rule at index " + index + " must declare a non-empty reason",
                    "Regenerate Pi guard resources with camel-kit init --here --ai pi --force."));
            valid = false;
        }

        JsonNode toolNames = rule.path("toolNames");
        if (!toolNames.isMissingNode()) {
            if (!toolNames.isArray()) {
                addPiGuardPolicyFailure(root, policy, findings,
                        "Pi safety guard policy rule at index " + index
                                                                + " has invalid toolNames; expected an array of strings");
                valid = false;
            } else {
                for (int i = 0; i < toolNames.size(); i++) {
                    if (!isNonBlankText(toolNames.get(i))) {
                        addPiGuardPolicyFailure(root, policy, findings,
                                "Pi safety guard policy rule at index " + index
                                                                        + " has an invalid toolNames entry at index "
                                                                        + i);
                        valid = false;
                    }
                }
            }
        }
        return valid;
    }

    private void addPiGuardPolicyFailure(
            Path root, Path policy, List<DoctorFinding> findings, String message) {
        findings.add(DoctorFinding.fail("workspace", relativize(root, policy),
                message,
                "Regenerate Pi guard resources with camel-kit init --here --ai pi --force."));
    }

    private boolean isNonBlankText(JsonNode node) {
        return node.isTextual() && !node.asText().isBlank();
    }

    private void checkCommandFiles(
            Path root, Path commandsDir, AgentConfig agent, List<DoctorFinding> findings) {
        String extension = "." + agent.fileFormat();
        List<String> userCommands = expectations.userCommands(agentKey(agent));
        List<String> missing = userCommands.stream()
                .filter(command -> !Files.isRegularFile(commandsDir.resolve(command + extension)))
                .toList();

        Set<String> unexpected = new LinkedHashSet<>();
        try (Stream<Path> stream = Files.list(commandsDir)) {
            stream.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith("camel-"))
                    .map(this::commandName)
                    .filter(command -> !userCommands.contains(command))
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
        String normalizedAgentName = agentName.toLowerCase(Locale.ROOT);
        boolean copilotToolsSchema = AgentGeneratorStrategy.COPILOT.descriptorValue()
                .equals(normalizedAgentName);
        boolean piDirectToolsSchema = AgentGeneratorStrategy.PI.descriptorValue()
                .equals(normalizedAgentName);
        boolean qwenIncludeToolsSchema = AgentGeneratorStrategy.QWEN.descriptorValue()
                .equals(normalizedAgentName);
        boolean openCodeSchema = AgentGeneratorStrategy.OPENCODE.descriptorValue()
                .equals(normalizedAgentName);
        Path mcpFile = mcpConfigPath(root, agentName);
        if (mcpFile == null) {
            findings.add(DoctorFinding.fail("mcp", null,
                    "Cannot determine MCP config path for agent '" + agentName + "'",
                    "Fix agent.name in .camel-kit/config.properties."));
            return;
        }

        JsonNode rootNode;
        if (openCodeSchema) {
            OpenCodeConfiguration openCode = readOpenCodeConfiguration(root, mcpFile, findings);
            if (openCode == null) {
                return;
            }
            mcpFile = openCode.file();
            rootNode = openCode.root();
        } else {
            if (!Files.isRegularFile(mcpFile)) {
                findings.add(DoctorFinding.fail("mcp", relativize(root, mcpFile),
                        "MCP config is missing for agent '" + agentName + "'",
                        "Run camel-kit init --here --ai " + agentName
                                                                               + " --force to regenerate MCP configuration."));
                return;
            }

            AgentConfig configuredAgent = AgentRegistry.get(normalizedAgentName);
            if (configuredAgent != null && "toml".equals(configuredAgent.mcpConfigFormat())) {
                checkCodexMcp(root, mcpFile, findings);
                return;
            }

            try {
                rootNode = MAPPER.readTree(mcpFile.toFile());
            } catch (IOException e) {
                findings.add(DoctorFinding.fail("mcp", relativize(root, mcpFile),
                        "MCP config is not valid JSON: " + e.getMessage(),
                        "Fix the JSON syntax or regenerate the MCP config with camel-kit init --here --force."));
                return;
            }
        }

        JsonNode servers = rootNode.has("mcp") ? rootNode.path("mcp") : rootNode.path("mcpServers");
        if (!servers.isObject()) {
            findings.add(DoctorFinding.fail("mcp", relativize(root, mcpFile),
                    "MCP config does not contain an mcp or mcpServers object",
                    "Regenerate the MCP config with camel-kit init --here --force."));
            return;
        }

        int findingsBeforeServerChecks = findings.size();
        boolean camelOk = checkMcpServer(
                root, mcpFile, servers, "camel", expectations.camelMcpTools(), copilotToolsSchema,
                piDirectToolsSchema, qwenIncludeToolsSchema, openCodeSchema, findings);
        boolean knowledgeOk = checkMcpServer(
                root, mcpFile, servers, "camel-knowledge", expectations.knowledgeMcpTools(), copilotToolsSchema,
                piDirectToolsSchema, qwenIncludeToolsSchema, openCodeSchema, findings);
        boolean citrusOk = checkMcpServer(
                root, mcpFile, servers, "citrus", expectations.citrusMcpTools(), copilotToolsSchema,
                piDirectToolsSchema, qwenIncludeToolsSchema, openCodeSchema, findings);
        boolean openCodePermissionsOk = !openCodeSchema
                || checkOpenCodePermissions(root, mcpFile, rootNode, servers, findings);
        boolean warned = findings.subList(findingsBeforeServerChecks, findings.size()).stream()
                .anyMatch(finding -> finding.status() == DoctorFinding.Status.WARN);
        if (camelOk && knowledgeOk && citrusOk && openCodePermissionsOk && !warned) {
            String message = openCodeSchema
                    ? "OpenCode MCP config uses supported server fields; tool calls retain OpenCode permission prompts"
                    : "MCP config exists and tool allowlists match Camel-Kit expectations";
            findings.add(DoctorFinding.pass("mcp", relativize(root, mcpFile),
                    message,
                    "No action required."));
        }
    }

    private OpenCodeConfiguration readOpenCodeConfiguration(
            Path root, Path defaultFile, List<DoctorFinding> findings) {
        ObjectNode effective = OPENCODE_MAPPER.createObjectNode();
        Path effectiveFile = null;
        for (Path candidate : OpenCodeProjectConfig.files(root)) {
            if (!Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) {
                continue;
            }
            if (!Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)) {
                findings.add(DoctorFinding.fail("mcp", relativize(root, candidate),
                        "OpenCode configuration must be a regular file",
                        "Replace it with a regular JSON or JSONC file, then re-run camel-kit doctor."));
                return null;
            }

            JsonNode parsed;
            try {
                byte[] content = Files.readAllBytes(candidate);
                parsed = content.length == 0
                        ? OPENCODE_MAPPER.createObjectNode()
                        : OPENCODE_MAPPER.readTree(content);
            } catch (IOException e) {
                findings.add(DoctorFinding.fail("mcp", relativize(root, candidate),
                        "OpenCode configuration is not valid JSON or JSONC: " + e.getMessage(),
                        "Fix the syntax or regenerate the OpenCode config with camel-kit init --here --force."));
                return null;
            }
            if (parsed == null || !parsed.isObject()) {
                findings.add(DoctorFinding.fail("mcp", relativize(root, candidate),
                        "OpenCode configuration must be a JSON object",
                        "Fix the configuration or regenerate it with camel-kit init --here --force."));
                return null;
            }
            if (!hasValidOpenCodeFields(root, candidate, parsed, findings)) {
                return null;
            }
            ObjectNode layer = ((ObjectNode) parsed).deepCopy();
            OpenCodeProjectConfig.normalizeScalarPermission(layer);
            mergeJsonObjects(effective, layer);
            effectiveFile = candidate;
        }

        if (effectiveFile == null) {
            findings.add(DoctorFinding.fail("mcp", relativize(root, defaultFile),
                    "MCP config is missing for agent 'opencode'",
                    "Run camel-kit init --here --ai opencode --force to regenerate MCP configuration."));
            return null;
        }
        return new OpenCodeConfiguration(effectiveFile, effective);
    }

    private boolean hasValidOpenCodeFields(
            Path root, Path file, JsonNode config, List<DoctorFinding> findings) {
        JsonNode permission = config.get("permission");
        if (permission != null && !permission.isObject()
                && !(permission.isTextual() && OPENCODE_PERMISSION_ACTIONS.contains(permission.asText()))) {
            findings.add(DoctorFinding.fail("mcp", relativize(root, file),
                    "OpenCode permission must be an object or one of allow, ask, deny",
                    "Fix the permission field or regenerate the OpenCode config."));
            return false;
        }
        JsonNode mcp = config.get("mcp");
        if (mcp != null && !mcp.isObject()) {
            findings.add(DoctorFinding.fail("mcp", relativize(root, file),
                    "OpenCode mcp must be an object",
                    "Fix the mcp field or regenerate the OpenCode config."));
            return false;
        }
        return true;
    }

    private void mergeJsonObjects(ObjectNode target, ObjectNode source) {
        source.fields().forEachRemaining(entry -> {
            JsonNode existing = target.get(entry.getKey());
            if (existing != null && existing.isObject() && entry.getValue().isObject()) {
                mergeJsonObjects((ObjectNode) existing, (ObjectNode) entry.getValue());
            } else {
                target.set(entry.getKey(), entry.getValue().deepCopy());
            }
        });
    }

    private void checkCodexMcp(Path root, Path mcpFile, List<DoctorFinding> findings) {
        TomlParseResult parsed;
        try {
            parsed = Toml.parse(mcpFile);
        } catch (IOException e) {
            findings.add(DoctorFinding.fail("mcp", relativize(root, mcpFile),
                    "Could not read Codex MCP config: " + e.getMessage(),
                    "Check filesystem permissions and re-run camel-kit doctor."));
            return;
        }
        if (parsed.hasErrors()) {
            findings.add(DoctorFinding.fail("mcp", relativize(root, mcpFile),
                    "Codex MCP config is not valid TOML: " + parsed.errors().get(0),
                    "Fix the TOML syntax or regenerate it with camel-kit init --here --ai codex --force."));
            return;
        }

        if (!(parsed.get("mcp_servers") instanceof TomlTable servers)) {
            findings.add(DoctorFinding.fail("mcp", relativize(root, mcpFile),
                    "Codex MCP config does not contain an mcp_servers table",
                    "Regenerate it with camel-kit init --here --ai codex --force."));
            return;
        }

        boolean camelOk = checkCodexMcpServer(
                root, mcpFile, servers, "camel", expectations.camelMcpTools(), findings);
        boolean knowledgeOk = checkCodexMcpServer(
                root, mcpFile, servers, "camel-knowledge", expectations.knowledgeMcpTools(), findings);
        boolean citrusOk = checkCodexMcpServer(
                root, mcpFile, servers, "citrus", expectations.citrusMcpTools(), findings);
        if (camelOk && knowledgeOk && citrusOk) {
            findings.add(DoctorFinding.pass("mcp", relativize(root, mcpFile),
                    "Codex MCP config is valid TOML and all tool allowlists match Camel-Kit expectations",
                    "No action required."));
        }
    }

    private boolean checkCodexMcpServer(
            Path root, Path mcpFile, TomlTable servers, String serverName, Set<String> expected,
            List<DoctorFinding> findings) {
        if (!(servers.get(serverName) instanceof TomlTable server)) {
            findings.add(DoctorFinding.fail("mcp", relativize(root, mcpFile),
                    "Codex MCP server '" + serverName + "' is missing",
                    "Regenerate the Codex MCP config with camel-kit init --here --ai codex --force."));
            return false;
        }

        boolean valid = true;
        if (!(server.get("command") instanceof String command) || command.isBlank()) {
            findings.add(DoctorFinding.fail("mcp", relativize(root, mcpFile),
                    "Codex MCP server '" + serverName + "' must declare a non-empty command",
                    "Regenerate the Codex MCP config with camel-kit init --here --ai codex --force."));
            valid = false;
        }

        TomlArray args = server.get("args") instanceof TomlArray value ? value : null;
        if (!isStringArray(args)) {
            findings.add(DoctorFinding.fail("mcp", relativize(root, mcpFile),
                    "Codex MCP server '" + serverName + "' args must be an array of strings",
                    "Regenerate the Codex MCP config with camel-kit init --here --ai codex --force."));
            valid = false;
        }

        TomlArray enabledTools = server.get("enabled_tools") instanceof TomlArray value ? value : null;
        if (!isStringArray(enabledTools)) {
            findings.add(DoctorFinding.fail("mcp", relativize(root, mcpFile),
                    "Codex MCP server '" + serverName + "' enabled_tools must be an array of strings",
                    "Regenerate the Codex MCP config with camel-kit init --here --ai codex --force."));
            valid = false;
        } else {
            Set<String> actual = new LinkedHashSet<>();
            for (int i = 0; i < enabledTools.size(); i++) {
                actual.add((String) enabledTools.get(i));
            }
            valid &= checkToolSet(
                    root, mcpFile, serverName, "enabled_tools", actual, expected, false, findings);
        }

        Object approvalMode = server.get("default_tools_approval_mode");
        if (!"prompt".equals(approvalMode)) {
            findings.add(DoctorFinding.fail("mcp", relativize(root, mcpFile),
                    "Codex MCP server '" + serverName
                                                                              + "' default_tools_approval_mode must be 'prompt' for least privilege",
                    "Set default_tools_approval_mode = \"prompt\" or regenerate the Codex MCP config."));
            valid = false;
        }
        return valid;
    }

    private boolean isStringArray(TomlArray array) {
        if (array == null) {
            return false;
        }
        for (int i = 0; i < array.size(); i++) {
            if (!(array.get(i) instanceof String)) {
                return false;
            }
        }
        return true;
    }

    private boolean checkMcpServer(
            Path root, Path mcpFile, JsonNode servers, String serverName, Set<String> expected,
            boolean copilotToolsSchema, boolean piDirectToolsSchema, boolean qwenIncludeToolsSchema,
            boolean openCodeSchema, List<DoctorFinding> findings) {
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
        if (piDirectToolsSchema) {
            return checkPiDirectTools(root, mcpFile, serverName, server, expected, findings);
        }
        if (qwenIncludeToolsSchema) {
            return checkQwenTools(root, mcpFile, serverName, server, expected, findings);
        }
        if (openCodeSchema) {
            return checkOpenCodeServer(root, mcpFile, serverName, server, findings);
        }

        boolean autoApproveOk = checkAllowlist(root, mcpFile, serverName, "autoApprove", server, expected, findings);
        boolean alwaysAllowOk = checkAllowlist(root, mcpFile, serverName, "alwaysAllow", server, expected, findings);
        return autoApproveOk && alwaysAllowOk;
    }

    private boolean checkQwenTools(
            Path root, Path mcpFile, String serverName, JsonNode server, Set<String> expected,
            List<DoctorFinding> findings) {
        JsonNode includeTools = server.get("includeTools");
        boolean valid;
        if (includeTools != null && includeTools.isArray()) {
            Set<String> actual = new LinkedHashSet<>();
            includeTools.forEach(value -> actual.add(value.asText()));
            valid = checkToolSet(root, mcpFile, serverName, "includeTools", actual, expected, false, findings);
        } else if (includeTools == null
                && (server.path("autoApprove").isArray() || server.path("alwaysAllow").isArray())) {
            findings.add(DoctorFinding.warn("mcp", relativize(root, mcpFile),
                    "Qwen MCP server '" + serverName + "' uses the legacy approval-field schema",
                    "Regenerate the Qwen config to add the supported includeTools filter."));
            valid = true;
        } else {
            findings.add(DoctorFinding.fail("mcp", relativize(root, mcpFile),
                    "MCP server '" + serverName + "' "
                                                                              + (includeTools == null
                                                                                      ? "is missing" : "has invalid")
                                                                              + " includeTools array",
                    "Regenerate the MCP config with camel-kit init --here --force."));
            valid = false;
        }
        JsonNode trust = server.get("trust");
        if (server.hasNonNull("trust") && (!trust.isBoolean() || trust.asBoolean())) {
            findings.add(DoctorFinding.fail("mcp", relativize(root, mcpFile),
                    "Qwen MCP server '" + serverName + "' trust must be absent or false to preserve approval prompts",
                    "Remove trust or set it to false, then re-run camel-kit doctor."));
            valid = false;
        }
        return valid;
    }

    private boolean checkOpenCodeServer(
            Path root, Path mcpFile, String serverName, JsonNode server, List<DoctorFinding> findings) {
        for (String ignoredField : List.of("autoApprove", "alwaysAllow")) {
            if (server.has(ignoredField)) {
                findings.add(DoctorFinding.warn("mcp", relativize(root, mcpFile),
                        "OpenCode MCP server '" + serverName + "' contains unsupported field " + ignoredField,
                        "Remove the ignored field or regenerate the OpenCode MCP config."));
            }
        }
        return true;
    }

    private boolean checkOpenCodePermissions(
            Path root, Path mcpFile, JsonNode config, JsonNode servers, List<DoctorFinding> findings) {
        JsonNode permission = config.get("permission");
        boolean valid = true;
        for (String pattern : List.of("camel_*", "camel-knowledge_*", "citrus_*")) {
            List<OpenCodePermissionResolution> resolutions = openCodeTools(pattern).stream()
                    .map(tool -> openCodePermissionAction(permission, tool))
                    .toList();
            boolean knownWrong = resolutions.stream()
                    .anyMatch(resolution -> resolution.matched() && !"ask".equals(resolution.action()));
            boolean missing = resolutions.stream().anyMatch(resolution -> !resolution.matched());
            boolean wrong = knownWrong || (!missing && hasUnsafeOpenCodeNamespaceOverride(permission, pattern));
            if (wrong) {
                findings.add(DoctorFinding.fail("mcp", relativize(root, mcpFile),
                        "OpenCode permission namespace '" + pattern
                                                                                  + "' must end with a namespace-wide 'ask' rule",
                        "Add a final namespace-wide ask rule to retain MCP permission prompts, or regenerate the OpenCode config."));
                valid = false;
            } else if (missing) {
                String serverName = pattern.substring(0, pattern.length() - 2);
                JsonNode server = servers.path(serverName);
                if (server.path("autoApprove").isArray() || server.path("alwaysAllow").isArray()) {
                    findings.add(DoctorFinding.warn("mcp", relativize(root, mcpFile),
                            "Legacy OpenCode config has no supported permission for '" + pattern + "'",
                            "Regenerate the OpenCode config to retain explicit MCP approval prompts."));
                } else {
                    findings.add(DoctorFinding.fail("mcp", relativize(root, mcpFile),
                            "OpenCode permission '" + pattern + "' must be 'ask'",
                            "Restore the generated MCP permission prompts or regenerate the OpenCode config."));
                }
                valid = false;
            }
        }
        return valid;
    }

    private Set<String> openCodeTools(String pattern) {
        String serverName = switch (pattern) {
            case "camel_*" -> "camel";
            case "camel-knowledge_*" -> "camel-knowledge";
            case "citrus_*" -> "citrus";
            default -> pattern.substring(0, pattern.indexOf('_'));
        };
        Set<String> tools = switch (pattern) {
            case "camel_*" -> expectations.camelMcpTools();
            case "camel-knowledge_*" -> expectations.knowledgeMcpTools();
            case "citrus_*" -> expectations.citrusMcpTools();
            default -> Set.of("tool");
        };
        if (tools.isEmpty()) {
            return Set.of(serverName + "_tool");
        }
        return tools.stream()
                .map(tool -> serverName + "_" + tool.replaceAll("[^a-zA-Z0-9_-]", "_"))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private boolean hasUnsafeOpenCodeNamespaceOverride(JsonNode permission, String managedPattern) {
        if (permission == null || !permission.isObject()) {
            return false;
        }

        boolean coveredByAsk = false;
        boolean overridden = false;
        String managedPrefix = managedPattern.substring(0, managedPattern.length() - 1);
        for (var rule = permission.fields(); rule.hasNext();) {
            Map.Entry<String, JsonNode> entry = rule.next();
            OpenCodePermissionResolution resolution = openCodeRuleAction(entry.getValue());
            if (!resolution.matched()) {
                continue;
            }

            if (openCodeGlobCoversPrefix(entry.getKey(), managedPrefix)) {
                if ("ask".equals(resolution.action())) {
                    coveredByAsk = true;
                    overridden = false;
                } else {
                    coveredByAsk = false;
                    overridden = true;
                }
            } else if (coveredByAsk && !"ask".equals(resolution.action())
                    && openCodeGlobCanMatchPrefix(entry.getKey(), managedPrefix)) {
                overridden = true;
            }
        }
        return !coveredByAsk || overridden;
    }

    private boolean openCodeGlobCoversPrefix(String wildcard, String prefix) {
        String pattern = normalizeOpenCodeToolGlob(wildcard);
        String normalizedPrefix = prefix.replace('\\', '/');
        if (isWindows()) {
            pattern = pattern.toLowerCase(Locale.ROOT);
            normalizedPrefix = normalizedPrefix.toLowerCase(Locale.ROOT);
        }
        if (!pattern.endsWith("*")) {
            return false;
        }
        boolean[] states = openCodeGlobStatesAfterPrefix(pattern, normalizedPrefix);
        return states[pattern.length()];
    }

    private boolean openCodeGlobCanMatchPrefix(String wildcard, String prefix) {
        String pattern = normalizeOpenCodeToolGlob(wildcard);
        String normalizedPrefix = prefix.replace('\\', '/');
        if (isWindows()) {
            pattern = pattern.toLowerCase(Locale.ROOT);
            normalizedPrefix = normalizedPrefix.toLowerCase(Locale.ROOT);
        }

        boolean[] states = openCodeGlobStatesAfterPrefix(pattern, normalizedPrefix);
        for (int state = 0; state < states.length; state++) {
            if (states[state] && openCodeGlobCanFinishWithToolCharacters(pattern, state)) {
                return true;
            }
        }
        return false;
    }

    private String normalizeOpenCodeToolGlob(String wildcard) {
        String normalized = wildcard.replace('\\', '/');
        return normalized.endsWith(" *")
                ? normalized.substring(0, normalized.length() - 2)
                : normalized;
    }

    private boolean[] openCodeGlobStatesAfterPrefix(String pattern, String prefix) {
        boolean[] states = new boolean[pattern.length() + 1];
        states[0] = true;
        closeOpenCodeGlobStars(states, pattern);
        for (char character : prefix.toCharArray()) {
            boolean[] next = new boolean[pattern.length() + 1];
            for (int state = 0; state < pattern.length(); state++) {
                if (!states[state]) {
                    continue;
                }
                char token = pattern.charAt(state);
                if (token == '*') {
                    next[state] = true;
                } else if (token == '?' || token == character) {
                    next[state + 1] = true;
                }
            }
            closeOpenCodeGlobStars(next, pattern);
            states = next;
        }
        return states;
    }

    private boolean openCodeGlobCanFinishWithToolCharacters(String pattern, int state) {
        for (int index = state; index < pattern.length(); index++) {
            char token = pattern.charAt(index);
            if (token != '*' && token != '?' && !isOpenCodeToolCharacter(token)) {
                return false;
            }
        }
        return true;
    }

    private boolean isOpenCodeToolCharacter(char character) {
        return character >= 'a' && character <= 'z'
                || character >= 'A' && character <= 'Z'
                || character >= '0' && character <= '9'
                || character == '_'
                || character == '-';
    }

    private void closeOpenCodeGlobStars(boolean[] states, String pattern) {
        for (int state = 0; state < pattern.length(); state++) {
            if (states[state] && pattern.charAt(state) == '*') {
                states[state + 1] = true;
            }
        }
    }

    private OpenCodePermissionResolution openCodePermissionAction(JsonNode permission, String tool) {
        if (permission == null || permission.isNull()) {
            return new OpenCodePermissionResolution(false, null);
        }
        if (!permission.isObject()) {
            return new OpenCodePermissionResolution(false, null);
        }

        boolean matched = false;
        String action = null;
        for (var rule = permission.fields(); rule.hasNext();) {
            Map.Entry<String, JsonNode> entry = rule.next();
            if (openCodeWildcardMatches(tool, entry.getKey())) {
                OpenCodePermissionResolution resolution = openCodeRuleAction(entry.getValue());
                if (resolution.matched()) {
                    matched = true;
                    action = resolution.action();
                }
            }
        }
        return new OpenCodePermissionResolution(matched, action);
    }

    private OpenCodePermissionResolution openCodeRuleAction(JsonNode value) {
        if (value.isTextual()) {
            return new OpenCodePermissionResolution(true, value.asText());
        }
        if (!value.isObject()) {
            return new OpenCodePermissionResolution(true, null);
        }

        boolean matched = false;
        String action = null;
        for (var nestedRule = value.fields(); nestedRule.hasNext();) {
            Map.Entry<String, JsonNode> nested = nestedRule.next();
            if (openCodeWildcardMatches("*", nested.getKey())) {
                matched = true;
                action = nested.getValue().isTextual() ? nested.getValue().asText() : null;
            }
        }
        return new OpenCodePermissionResolution(matched, action);
    }

    private boolean openCodeWildcardMatches(String value, String wildcard) {
        StringBuilder regex = new StringBuilder("^");
        for (char character : wildcard.replace('\\', '/').toCharArray()) {
            if (character == '*') {
                regex.append(".*");
            } else if (character == '?') {
                regex.append('.');
            } else {
                if (".+^${}()|[]\\".indexOf(character) >= 0) {
                    regex.append('\\');
                }
                regex.append(character);
            }
        }
        if (regex.toString().endsWith(" .*")) {
            regex.setLength(regex.length() - 3);
            regex.append("( .*)?");
        }
        regex.append('$');
        int flags = Pattern.DOTALL | (isWindows() ? Pattern.CASE_INSENSITIVE : 0);
        return Pattern.compile(regex.toString(), flags)
                .matcher(value.replace('\\', '/'))
                .matches();
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

    private boolean checkPiDirectTools(
            Path root, Path mcpFile, String serverName, JsonNode server, Set<String> expected,
            List<DoctorFinding> findings) {
        JsonNode directTools = server.path("directTools");
        if (directTools.isMissingNode() || directTools.isNull()) {
            findings.add(DoctorFinding.fail("mcp", relativize(root, mcpFile),
                    "MCP server '" + serverName + "' is missing directTools",
                    "Regenerate the Pi MCP config with camel-kit init --here --ai pi --force."));
            return false;
        }

        if (directTools.isBoolean()) {
            if (directTools.asBoolean()) {
                findings.add(DoctorFinding.warn("mcp", relativize(root, mcpFile),
                        "MCP server '" + serverName + "' directTools promotes all tools",
                        "Prefer the generated Camel-Kit tool allowlist for least privilege unless broader access is intentional."));
                return true;
            }
            findings.add(DoctorFinding.fail("mcp", relativize(root, mcpFile),
                    "MCP server '" + serverName + "' directTools disables first-class tools",
                    "Set directTools to the generated Camel-Kit tool allowlist and re-run camel-kit doctor."));
            return false;
        }

        if (!directTools.isArray()) {
            findings.add(DoctorFinding.fail("mcp", relativize(root, mcpFile),
                    "MCP server '" + serverName + "' directTools must be an array or true",
                    "Set directTools to true or the generated Camel-Kit tool list, then re-run camel-kit doctor."));
            return false;
        }

        Set<String> actual = new LinkedHashSet<>();
        directTools.forEach(value -> actual.add(value.asText()));
        return checkToolSet(root, mcpFile, serverName, "directTools", actual, expected, false, findings);
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
        OpenCodeProjectConfig.files(root).forEach(path -> addIfExists(activeFiles, path));
        if (agent != null) {
            Path commandsDir = agent.generatesCommandStubs() ? root.resolve(agent.commandDirectory()) : null;
            if (commandsDir != null && Files.isDirectory(commandsDir)) {
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

    private record OpenCodeConfiguration(Path file, JsonNode root) {
    }

    private record OpenCodePermissionResolution(boolean matched, String action) {
    }
}
