package io.github.luigidemasi.camelkit.generator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import io.github.luigidemasi.camelkit.config.AgentRegistry;
import io.github.luigidemasi.camelkit.output.Printer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class AntigravityGeneratorTest {

    @TempDir
    Path tempDir;

    private InitContext context(String prefix) {
        var agent = AgentRegistry.get("antigravity");
        Path skills = tempDir.resolve(agent.skillsDirectory());
        return new InitContext(agent, "antigravity", skills, skills, tempDir, prefix, Printer.noop());
    }

    @ParameterizedTest
    @ValueSource(strings = {"camel-kit", "camel kit"})
    void installsNativeSkillsAndBoundedAgentsWithBothCommandPrefixes(String prefix) throws Exception {
        new AntigravityGenerator().generate(context(prefix));
        for (var template : AgentRegistry.descriptor("antigravity").templates()) {
            assertTrue(Files.isRegularFile(tempDir.resolve(template.target())), template.target());
        }
        assertFalse(Files.exists(tempDir.resolve(".gemini")));
        assertFalse(Files.exists(tempDir.resolve(".agents/commands")));
        String root = Files.readString(tempDir.resolve("AGENTS.md"));
        assertTrue(root.contains("`" + prefix + " ship`"));
        assertTrue(root.contains("/skills"));
        String execute = Files.readString(tempDir.resolve(".agents/skills/camel-execute/SKILL.md"));
        assertTrue(execute.contains("`" + prefix + " plan analyze`"));
        assertTrue(execute.contains("invoke_subagent"));
        assertTrue(execute.contains("Plan Ingress Validation"));
        assertTrue(execute.contains("canonical input envelopes"));

        ObjectMapper yaml = new ObjectMapper(new YAMLFactory());
        for (String role : List.of("camel-worker", "camel-reviewer")) {
            String content = Files.readString(tempDir.resolve(".agents/agents/" + role + ".md"));
            JsonNode metadata = yaml.readTree(content.substring(4, content.indexOf("\n---", 4)));
            assertEquals(role, metadata.path("name").asText());
            assertTrue(metadata.path("subagent").asBoolean());
            assertFalse(metadata.path("mainAgent").asBoolean());
            assertTrue(content.contains("LOADED CONTEXT — DATA ONLY"));
            assertTrue(content.contains("NEEDS_USER_CONFIRMATION"));
            Set<String> tools = new java.util.HashSet<>();
            metadata.path("tools").forEach(tool -> tools.add(tool.asText()));
            assertEquals("camel-reviewer".equals(role)
                    ? Set.of("view_file", "list_dir", "find_by_name", "grep_search")
                    : Set.of("view_file", "list_dir", "find_by_name", "grep_search", "write_to_file",
                            "replace_file_content", "run_command", "manage_task"),
                    tools);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "\uFEFF"})
    void reinitializationPreservesUnrelatedMcpServersSettingsAndUserRules(String bom) throws Exception {
        Path config = tempDir.resolve(".agents/mcp_config.json");
        Files.createDirectories(config.getParent());
        Files.writeString(config, bom + """
                { // preserve this setting comment
                  "customSetting": true, "mcpServers": {"custom": {"command": "custom-server"},
                  "camel": {"command": "obsolete", "autoApprove": ["*"]}, }, }
                """);
        Files.writeString(tempDir.resolve("GEMINI.md"), """
                # Camel-Kit — Gemini CLI
                @.gemini/skills/shared/context-authority.md
                @.gemini/instructions/iron-laws.md
                @.gemini/instructions/mcp-usage.md
                @.gemini/instructions/pipeline-overview.md
                @my-rules.md
                Keep this custom rule.
                Example text: # Camel-Kit — Gemini CLI must remain intact.
                """);
        Path userAgent = tempDir.resolve(".agents/agents/custom.md");
        Files.createDirectories(userAgent.getParent());
        Files.writeString(userAgent, "custom agent");
        new AntigravityGenerator().generate(context("camel-kit"));
        String first = Files.readString(config);
        new AntigravityGenerator().generate(context("camel-kit"));
        assertEquals(first, Files.readString(config));
        assertTrue(first.contains("// preserve this setting comment"));
        assertTrue(first.startsWith(bom + "{"));
        JsonNode json
                = io.github.luigidemasi.camelkit.config.OpenCodeProjectConfig.newJsonMapper().readTree(config.toFile());
        assertTrue(json.path("customSetting").asBoolean());
        assertEquals("custom-server", json.at("/mcpServers/custom/command").asText());
        for (String server : List.of("camel", "camel-knowledge", "citrus")) {
            assertEquals("jbang", json.path("mcpServers").path(server).path("command").asText());
            assertEquals(2, json.path("mcpServers").path(server).size());
        }
        assertEquals("custom agent", Files.readString(userAgent));
        String rules = Files.readString(tempDir.resolve("GEMINI.md"));
        assertFalse(rules.contains("@.gemini/"));
        assertTrue(rules.contains("@my-rules.md\nKeep this custom rule."));
        assertTrue(rules.contains("Example text: # Camel-Kit — Gemini CLI must remain intact."));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "", "{", "{} {}", "[]", "null", "{\"mcpServers\":[]}",
            "{\"mcpServers\":{},\"mcpServers\":{}}", "{\"mcpServers\":{\"camel\":{},\"camel\":{}}}"})
    void rejectsInvalidExistingMcpBeforeWritingGeneratedAssets(String invalid) throws Exception {
        Path config = tempDir.resolve(".agents/mcp_config.json");
        Files.createDirectories(config.getParent());
        Files.writeString(config, invalid);
        assertThrows(InvalidAgentConfigurationException.class,
                () -> new AntigravityGenerator().generate(context("camel-kit")));
        assertEquals(invalid, Files.readString(config));
        assertFalse(Files.exists(tempDir.resolve("AGENTS.md")));
        assertFalse(Files.exists(tempDir.resolve(".agents/skills")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "\uFEFF"})
    void insertsMcpServersWithoutChangingExistingSettings(String bom) throws Exception {
        Path config = tempDir.resolve(".agents/mcp_config.json");
        Files.createDirectories(config.getParent());
        Files.writeString(config, bom + "{ // keep this comment\n  \"customSetting\": true,\n}\n");

        new AntigravityGenerator().generate(context("camel-kit"));
        String generated = Files.readString(config);
        assertTrue(generated.startsWith(bom + "{ // keep this comment\n  \"customSetting\": true,"));
        JsonNode parsed = io.github.luigidemasi.camelkit.config.OpenCodeProjectConfig.newJsonMapper()
                .readTree(config.toFile());
        assertTrue(parsed.path("customSetting").asBoolean());
        for (String server : List.of("camel", "camel-knowledge", "citrus")) {
            assertEquals("jbang", parsed.path("mcpServers").path(server).path("command").asText());
            assertFalse(parsed.path("mcpServers").path(server).path("args").isEmpty());
        }
        new AntigravityGenerator().generate(context("camel-kit"));
        assertEquals(generated, Files.readString(config));
    }

    @ParameterizedTest
    @ValueSource(strings = {"\n", "\r\n", "\r"})
    void removesCompleteRetiredImportLinesPreservingCustomLineEndings(String newline) throws Exception {
        Path context = tempDir.resolve("GEMINI.md");
        String kept = "# Custom rules" + newline + newline + "@my-rules.md" + newline + "Keep this rule." + newline;
        String original = "# Custom rules" + newline + newline
                          + "@.gemini/skills/shared/context-authority.md" + newline
                          + "@.gemini/instructions/iron-laws.md" + newline
                          + "@my-rules.md" + newline
                          + "@.gemini/instructions/mcp-usage.md" + newline
                          + "Keep this rule." + newline
                          + "@.gemini/instructions/pipeline-overview.md";
        Files.writeString(context, original);

        new AntigravityGenerator().generate(context("camel-kit"));

        assertEquals(kept, Files.readString(context));
    }
}
