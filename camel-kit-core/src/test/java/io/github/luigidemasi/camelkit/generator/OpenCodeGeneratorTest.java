package io.github.luigidemasi.camelkit.generator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import io.github.luigidemasi.camelkit.config.AgentConfig;
import io.github.luigidemasi.camelkit.config.AgentDescriptor;
import io.github.luigidemasi.camelkit.config.AgentRegistry;
import io.github.luigidemasi.camelkit.config.OpenCodeProjectConfig;
import io.github.luigidemasi.camelkit.output.Printer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class OpenCodeGeneratorTest {

    @TempDir
    Path tempDir;

    private static void assertManagedEntriesWritten(JsonNode config) {
        assertEquals("ask", config.path("permission").path("camel_*").asText());
        assertEquals("ask", config.path("permission").path("citrus_*").asText());
        assertEquals("local", config.path("mcp").path("camel").path("type").asText());
        assertFalse(config.path("mcp").path("camel").has("autoApprove"));
    }

    private InitContext createContext() {
        return createContext("camel-kit");
    }

    private InitContext createContext(String commandPrefix) {
        AgentConfig agent = AgentRegistry.get("opencode");
        String agentBaseFolder = agent.folder().substring(0, agent.folder().lastIndexOf("/"));
        Path commandsDir = tempDir.resolve(agent.folder());
        Path skillsDir = tempDir.resolve(agentBaseFolder + "/skills");
        return new InitContext(
                agent, "opencode", commandsDir, skillsDir, tempDir,
                commandPrefix, Printer.noop());
    }

    @Test
    void generatesAgentsMd() throws Exception {
        InitContext ctx = createContext();
        new OpenCodeGenerator().generate(ctx);

        Path agentsMd = tempDir.resolve("AGENTS.md");
        assertTrue(Files.exists(agentsMd));
        String content = Files.readString(agentsMd);
        assertTrue(content.contains("/camel-start"));
        assertTrue(content.contains("Laws"));
        assertTrue(content.contains("config.properties"));
        assertTrue(content.contains("camel-kit graph stats"));
    }

    @Test
    void generatesAgentDefinitions() throws Exception {
        InitContext ctx = createContext();
        new OpenCodeGenerator().generate(ctx);

        Path agentsDir = tempDir.resolve(".opencode/agents");
        assertTrue(Files.isDirectory(agentsDir));
        assertTrue(Files.exists(agentsDir.resolve("brainstormer.md")));
        assertTrue(Files.exists(agentsDir.resolve("planner.md")));
        assertTrue(Files.exists(agentsDir.resolve("implementer.md")));
        assertTrue(Files.exists(agentsDir.resolve("validator.md")));
        assertTrue(Files.exists(agentsDir.resolve("tester.md")));
        assertTrue(Files.exists(agentsDir.resolve("migrator.md")));
        assertTrue(Files.exists(agentsDir.resolve("executor.md")));
        assertTrue(Files.exists(agentsDir.resolve("researcher.md")));
        assertTrue(Files.exists(agentsDir.resolve("reviewer.md")));

        AgentDescriptor descriptor = AgentRegistry.descriptor("opencode");
        Set<String> registeredAgents = descriptor.templates().stream()
                .map(AgentDescriptor.TemplateInstall::target)
                .filter(target -> target.startsWith(".opencode/agents/"))
                .collect(Collectors.toSet());
        assertEquals(Set.of(
                ".opencode/agents/brainstormer.md",
                ".opencode/agents/executor.md",
                ".opencode/agents/implementer.md",
                ".opencode/agents/migrator.md",
                ".opencode/agents/planner.md",
                ".opencode/agents/researcher.md",
                ".opencode/agents/reviewer.md",
                ".opencode/agents/tester.md",
                ".opencode/agents/validator.md"), registeredAgents);
    }

    @Test
    void generatedAgentDefinitionsHaveDescriptions() throws Exception {
        InitContext ctx = createContext();
        new OpenCodeGenerator().generate(ctx);

        for (String name : new String[]{
                "brainstormer", "executor", "implementer", "migrator", "planner",
                "researcher", "reviewer", "tester", "validator"
        }) {
            String content = Files.readString(tempDir.resolve(".opencode/agents/" + name + ".md"));
            assertTrue(Pattern.compile("(?m)^description: \\S.+$").matcher(content).find(), name);
        }
    }

    @Test
    void phaseAgentsHaveScopedArtifactWrites() throws Exception {
        InitContext ctx = createContext();
        new OpenCodeGenerator().generate(ctx);

        String brainstormer = Files.readString(
                tempDir.resolve(".opencode/agents/brainstormer.md"));
        String planner = Files.readString(tempDir.resolve(".opencode/agents/planner.md"));
        String validator = Files.readString(tempDir.resolve(".opencode/agents/validator.md"));

        assertTrue(brainstormer.contains("permission:"));
        assertTrue(brainstormer.contains("\"docs/**\": allow"));
        assertTrue(brainstormer.contains("\".camel-kit/pipeline.json\": allow"));
        assertTrue(brainstormer.contains("\".camel-kit/project-snapshot.md\": allow"));
        assertTrue(planner.contains("\"docs/camel-kit/**\": allow"));
        assertTrue(validator.contains("\"docs/camel-kit/*/validation-report.md\": allow"));
        assertTrue(validator.contains("\"docs/validation-report-*.md\": allow"));
        assertTrue(validator.contains("task: deny"));
        assertTrue(validator.contains("bounded validation analysis"));
        assertFalse(validator.contains("follow those instructions exactly"));
        assertFalse(brainstormer.contains("\nedit:"));
        assertFalse(planner.contains("\nedit:"));
        assertFalse(validator.contains("\nedit:"));
        assertTrue(brainstormer.contains("mode: subagent"));
        assertTrue(brainstormer.contains("steps: 200"));
    }

    @Test
    void implementerHasFullAccess() throws Exception {
        InitContext ctx = createContext();
        new OpenCodeGenerator().generate(ctx);

        String content = Files.readString(
                tempDir.resolve(".opencode/agents/implementer.md"));
        assertTrue(content.contains("edit: allow"));
        assertTrue(content.contains("\"rm -rf *\": deny"));
        assertTrue(content.contains("steps: 50"));
    }

    @Test
    void testerHasPathScopedEdits() throws Exception {
        InitContext ctx = createContext();
        new OpenCodeGenerator().generate(ctx);

        String content = Files.readString(
                tempDir.resolve(".opencode/agents/tester.md"));
        assertTrue(content.contains("\"src/test/**\": allow"));
        assertTrue(content.contains("\"test/**\": allow"));
        assertTrue(content.contains("\"*\": ask"));
    }

    @Test
    void executorHasScopedLeafDelegation() throws Exception {
        InitContext ctx = createContext();
        new OpenCodeGenerator().generate(ctx);

        String content = Files.readString(
                tempDir.resolve(".opencode/agents/executor.md"));
        assertTrue(content.contains("mode: primary"));
        String taskPermissions = content.substring(
                content.indexOf("  task:"), content.indexOf("steps:"));
        assertTrue(taskPermissions.contains("\"*\": deny"));
        assertTrue(taskPermissions.contains("implementer: allow"));
        assertTrue(taskPermissions.contains("migrator: allow"));
        assertTrue(taskPermissions.contains("planner: allow"));
        assertTrue(taskPermissions.contains("researcher: allow"));
        assertTrue(taskPermissions.contains("reviewer: allow"));
        assertTrue(taskPermissions.contains("tester: allow"));
        assertFalse(taskPermissions.contains("validator: allow"));
        assertTrue(content.contains("steps: 100"));
    }

    @Test
    void reviewLeavesAreReadOnlyAndCannotDelegate() throws Exception {
        InitContext ctx = createContext();
        new OpenCodeGenerator().generate(ctx);

        for (String name : new String[]{"researcher", "reviewer"}) {
            String content = Files.readString(
                    tempDir.resolve(".opencode/agents/" + name + ".md"));
            assertTrue(content.contains("edit: deny"));
            assertTrue(content.contains("bash: deny"));
            assertTrue(content.contains("task: deny"));
        }
    }

    @Test
    void keepsMcpConfigCompatibleWithReleasedOpenCodeSchemas() throws Exception {
        InitContext ctx = createContext();
        new OpenCodeGenerator().generate(ctx);

        JsonNode config = new ObjectMapper().readTree(
                Files.readString(tempDir.resolve("opencode.json")));
        assertFalse(config.has("subagent_depth"));
        for (String server : new String[]{"camel", "camel-knowledge", "citrus"}) {
            assertFalse(config.path("mcp").path(server).has("autoApprove"));
            assertFalse(config.path("mcp").path(server).has("alwaysAllow"));
        }
        assertEquals("ask", config.path("permission").path("camel_*").asText());
        assertEquals("ask", config.path("permission").path("camel-knowledge_*").asText());
        assertEquals("ask", config.path("permission").path("citrus_*").asText());
    }

    @Test
    void mergesManagedOpenCodeConfigWithoutDiscardingUserSettings() throws Exception {
        Files.writeString(tempDir.resolve("opencode.json"), """
                {
                  "theme": "custom",
                  "permission": {"bash": "deny", "camel_*": "allow"},
                  "mcp": {
                    "custom": {"type": "remote", "url": "https://example.test/mcp"},
                    "camel": {"type": "local", "autoApprove": ["legacy"]}
                  }
                }
                """);

        new OpenCodeGenerator().generate(createContext());

        JsonNode config = new ObjectMapper().readTree(tempDir.resolve("opencode.json").toFile());
        assertEquals("custom", config.path("theme").asText());
        assertEquals("deny", config.path("permission").path("bash").asText());
        assertEquals("ask", config.path("permission").path("camel_*").asText());
        assertEquals("https://example.test/mcp", config.path("mcp").path("custom").path("url").asText());
        assertFalse(config.path("mcp").path("camel").has("autoApprove"));
    }

    @Test
    void mergesJsoncAndScalarPermissionWithoutDiscardingUserSettings() throws Exception {
        Path configFile = tempDir.resolve("opencode.jsonc");
        Files.writeString(configFile, "\uFEFF" + """
                {
                  // OpenCode accepts JSONC in project configuration.
                  "theme": "custom",
                  "permission": /* Keep this permission comment. */ "deny",
                  "mcp": {
                    /* This server belongs to the user. */
                    "custom": {"type": "remote", "url": "https://example.test/mcp"},
                  },
                }
                """);

        OpenCodeGenerator generator = new OpenCodeGenerator();
        generator.preflight(createContext());
        generator.generate(createContext());

        JsonNode config = readOpenCodeConfig(configFile);
        assertFalse(Files.exists(tempDir.resolve("opencode.json")));
        assertEquals("custom", config.path("theme").asText());
        assertEquals("deny", config.path("permission").path("*").asText());
        assertEquals("ask", config.path("permission").path("camel_*").asText());
        assertEquals("ask", config.path("permission").path("camel-knowledge_*").asText());
        assertEquals("ask", config.path("permission").path("citrus_*").asText());
        assertEquals("https://example.test/mcp", config.path("mcp").path("custom").path("url").asText());
        String preserved = Files.readString(configFile);
        assertTrue(preserved.startsWith("\uFEFF"));
        assertTrue(preserved.contains("// OpenCode accepts JSONC in project configuration."));
        assertTrue(preserved.contains("/* Keep this permission comment. */"));
        assertTrue(preserved.contains("/* This server belongs to the user. */"));
    }

    @Test
    void treatsAnEmptyExistingConfigAsOpenCodeDoes() throws Exception {
        Path configFile = Files.createFile(tempDir.resolve("opencode.json"));

        OpenCodeGenerator generator = new OpenCodeGenerator();
        generator.preflight(createContext());
        generator.generate(createContext());

        JsonNode config = new ObjectMapper().readTree(configFile.toFile());
        assertEquals("ask", config.path("permission").path("camel_*").asText());
        assertTrue(config.path("mcp").has("camel"));
    }

    @Test
    void treatsAWhitespaceOnlyExistingConfigAsEmpty() throws Exception {
        Path configFile = tempDir.resolve("opencode.jsonc");
        Files.writeString(configFile, "\uFEFF \r\n\t\n");

        OpenCodeGenerator generator = new OpenCodeGenerator();
        generator.preflight(createContext());
        generator.generate(createContext());

        assertManagedEntriesWritten(readOpenCodeConfig(configFile));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void writesThroughASymlinkedConfig() throws Exception {
        Path real = Files.createDirectory(tempDir.resolve("dotfiles")).resolve("opencode.json");
        Files.writeString(real, "{\"permission\": {\"bash\": \"deny\"}, \"mcp\": {}}\n");
        Path link = Files.createSymbolicLink(tempDir.resolve("opencode.json"), real);

        OpenCodeGenerator generator = new OpenCodeGenerator();
        generator.preflight(createContext());
        generator.generate(createContext());

        assertTrue(Files.isSymbolicLink(link));
        JsonNode config = readOpenCodeConfig(real);
        assertEquals("deny", config.path("permission").path("bash").asText());
        assertManagedEntriesWritten(config);
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void treatsAliasedLayersAsOneFile() throws Exception {
        Path real = tempDir.resolve("opencode.json");
        Files.writeString(real, "{\"permission\": {\"bash\": \"deny\"}, \"mcp\": {}}\n");
        Files.createSymbolicLink(tempDir.resolve("opencode.jsonc"), real);

        OpenCodeGenerator generator = new OpenCodeGenerator();
        generator.preflight(createContext());
        generator.generate(createContext());
        String afterFirstGeneration = Files.readString(real);
        generator.generate(createContext());

        assertEquals(afterFirstGeneration, Files.readString(real));
        JsonNode config = readOpenCodeConfig(real);
        assertEquals("deny", config.path("permission").path("bash").asText());
        assertManagedEntriesWritten(config);
    }

    @Test
    void leavesFreshDefaultConfigByteIdenticalOnSecondGeneration() throws Exception {
        Path configFile = tempDir.resolve("opencode.json");
        assertFalse(Files.exists(configFile));

        assertFirstRegenerationIsByteStable(configFile);

        JsonNode config = readOpenCodeConfig(configFile);
        assertEquals("ask", config.path("permission").path("camel_*").asText());
        assertTrue(config.path("mcp").has("camel"));
    }

    @Test
    void consolidatesManagedEntriesInTheHighestPrecedenceProjectConfigFile() throws Exception {
        List<String> configFiles = List.of(
                "opencode.json",
                "opencode.jsonc",
                ".opencode/opencode.json",
                ".opencode/opencode.jsonc");
        for (int i = 0; i < configFiles.size(); i++) {
            Path configFile = tempDir.resolve(configFiles.get(i));
            Files.createDirectories(configFile.getParent());
            Files.writeString(configFile, String.format(Locale.ROOT,
                    """
                            {
                              // Keep root comment %d.
                              "theme": "custom-%d",
                              "permission": {
                                // Keep permission comment %d.
                                "custom_%d": "deny",
                                "camel_*": /* Keep managed permission comment %d. */ "allow" /* Keep trailing permission comment %d. */
                              },
                              "mcp": {
                                // Keep custom MCP comment %d.
                                "custom-%d": {"type": "remote", "url": "https://example.test/%d"},
                                "camel": {
                                  // Keep managed MCP comment %d.
                                  "type": "local",
                                  "autoApprove": ["legacy"]
                                } /* Keep trailing MCP comment %d. */
                              }
                            }
                            """,
                    i, i, i, i, i, i, i, i, i, i, i));
        }

        OpenCodeGenerator generator = new OpenCodeGenerator();
        generator.generate(createContext());
        List<String> afterFirstGeneration = configFiles.stream()
                .map(tempDir::resolve)
                .map(path -> assertDoesNotThrow(() -> Files.readString(path)))
                .toList();
        generator.generate(createContext());

        for (int i = 0; i < configFiles.size(); i++) {
            JsonNode config = readOpenCodeConfig(tempDir.resolve(configFiles.get(i)));
            String preserved = Files.readString(tempDir.resolve(configFiles.get(i)));
            assertEquals(afterFirstGeneration.get(i), preserved, configFiles.get(i));
            assertTrue(preserved.contains("// Keep root comment " + i + "."));
            assertTrue(preserved.contains("// Keep permission comment " + i + "."));
            assertTrue(preserved.contains("/* Keep managed permission comment " + i + ". */"));
            assertTrue(preserved.contains("/* Keep trailing permission comment " + i + ". */"));
            assertTrue(preserved.contains("// Keep custom MCP comment " + i + "."));
            assertTrue(preserved.contains("// Keep managed MCP comment " + i + "."));
            assertTrue(preserved.contains("/* Keep trailing MCP comment " + i + ". */"));
            assertEquals("custom-" + i, config.path("theme").asText());
            assertEquals("deny", config.path("permission").path("custom_" + i).asText());
            assertEquals("https://example.test/" + i,
                    config.path("mcp").path("custom-" + i).path("url").asText());
            if (i == configFiles.size() - 1) {
                assertEquals("ask", config.path("permission").path("camel_*").asText());
                assertTrue(config.path("mcp").has("camel"));
                assertFalse(config.path("mcp").path("camel").has("autoApprove"));
            } else {
                assertFalse(config.path("permission").has("camel_*"));
                assertFalse(config.path("mcp").has("camel"));
            }
        }
    }

    @Test
    void appendsManagedPermissionsAfterHigherPrecedenceUserWildcards() throws Exception {
        Files.writeString(tempDir.resolve("opencode.json"), """
                {
                  "permission": {"camel_*": "allow"},
                  "mcp": {"camel": {"type": "local", "autoApprove": ["legacy"]}}
                }
                """);
        Files.writeString(tempDir.resolve("opencode.jsonc"), """
                {
                  "permission": {"*": "deny"},
                  "mcp": {"custom": {"type": "remote", "url": "https://example.test/mcp"}}
                }
                """);

        new OpenCodeGenerator().generate(createContext());

        JsonNode lower = readOpenCodeConfig(tempDir.resolve("opencode.json"));
        assertFalse(lower.path("permission").has("camel_*"));
        assertFalse(lower.path("mcp").has("camel"));

        Path higherFile = tempDir.resolve("opencode.jsonc");
        JsonNode higher = readOpenCodeConfig(higherFile);
        assertEquals("deny", higher.path("permission").path("*").asText());
        assertEquals("ask", higher.path("permission").path("camel_*").asText());
        assertTrue(Files.readString(higherFile).indexOf("\"*\"")
                   < Files.readString(higherFile).indexOf("\"camel_*\""));
        assertEquals("https://example.test/mcp", higher.path("mcp").path("custom").path("url").asText());
        assertTrue(higher.path("mcp").has("camel"));
    }

    @Test
    void reordersCurrentManagedPermissionsAfterAUserWildcard() throws Exception {
        Path configFile = tempDir.resolve("opencode.json");
        OpenCodeGenerator generator = new OpenCodeGenerator();
        generator.generate(createContext());
        String current = Files.readString(configFile);
        Files.writeString(configFile, current.replace(
                "\"citrus_*\": \"ask\"",
                "\"citrus_*\": \"ask\",\n    \"*\": \"deny\""));

        generator.generate(createContext());
        String reordered = Files.readString(configFile);
        generator.generate(createContext());

        assertTrue(reordered.indexOf("\"*\"") < reordered.indexOf("\"camel_*\""), reordered);
        assertEquals(reordered, Files.readString(configFile));
        assertEquals("ask", readOpenCodeConfig(configFile).path("permission").path("camel_*").asText());
    }

    @Test
    void preservesLineCommentsInsideCompactManagedMembers() throws Exception {
        Path configFile = tempDir.resolve("opencode.jsonc");
        Files.writeString(configFile, """
                {"permission":{"camel_*":"allow"},"mcp":{"camel":{// Keep compact managed comment.
                "type":"local","autoApprove":["legacy"]},
                    "custom":{"type":"remote","url":"https://example.test/mcp"}}}
                """);

        OpenCodeGenerator generator = new OpenCodeGenerator();
        generator.generate(createContext());
        String afterFirstGeneration = Files.readString(configFile);
        generator.generate(createContext());

        assertEquals(afterFirstGeneration, Files.readString(configFile));
        assertTrue(afterFirstGeneration.contains("// Keep compact managed comment."));
        JsonNode config = assertDoesNotThrow(() -> readOpenCodeConfig(configFile), afterFirstGeneration);
        assertEquals("https://example.test/mcp", config.path("mcp").path("custom").path("url").asText());
        assertManagedEntriesWritten(config);
    }

    @Test
    void preservesCrOnlyJsoncLineCommentsAndNewlines() throws Exception {
        Path configFile = tempDir.resolve("opencode.jsonc");
        Files.writeString(configFile,
                "{\r"
                                      + "  // Keep CR-only root comment.\r"
                                      + "  \"permission\": {\"camel_*\": \"allow\"},\r"
                                      + "  \"mcp\": {\"camel\": {// Keep CR-only managed comment.\r"
                                      + "    \"type\": \"local\", \"autoApprove\": [\"legacy\"]}}\r"
                                      + "}\r");

        OpenCodeGenerator generator = new OpenCodeGenerator();
        generator.generate(createContext());
        String afterFirstGeneration = Files.readString(configFile);
        generator.generate(createContext());

        assertEquals(afterFirstGeneration, Files.readString(configFile));
        assertTrue(afterFirstGeneration.contains("// Keep CR-only root comment."));
        assertTrue(afterFirstGeneration.contains("// Keep CR-only managed comment."));
        assertTrue(afterFirstGeneration.contains("\r"));
        assertFalse(afterFirstGeneration.contains("\n"));
        assertManagedEntriesWritten(assertDoesNotThrow(() -> readOpenCodeConfig(configFile), afterFirstGeneration));
    }

    @Test
    void preservesCrLfJsoncLineCommentsAndNewlines() throws Exception {
        Path configFile = tempDir.resolve("opencode.jsonc");
        Files.writeString(configFile,
                "{\r\n"
                                      + "  // Keep CRLF root comment.\r\n"
                                      + "  \"permission\": {\"camel_*\": \"allow\"},\r\n"
                                      + "  \"mcp\": {\"camel\": {// Keep CRLF managed comment.\r\n"
                                      + "    \"type\": \"local\", \"autoApprove\": [\"legacy\"]}}\r\n"
                                      + "}\r\n");

        OpenCodeGenerator generator = new OpenCodeGenerator();
        generator.generate(createContext());
        String afterFirstGeneration = Files.readString(configFile);
        generator.generate(createContext());

        assertEquals(afterFirstGeneration, Files.readString(configFile));
        assertTrue(afterFirstGeneration.contains("// Keep CRLF root comment."));
        assertTrue(afterFirstGeneration.contains("// Keep CRLF managed comment."));
        assertTrue(afterFirstGeneration.contains("\r\n"));
        assertFalse(afterFirstGeneration.replace("\r\n", "").contains("\r"));
        assertFalse(afterFirstGeneration.replace("\r\n", "").contains("\n"));
        assertManagedEntriesWritten(assertDoesNotThrow(() -> readOpenCodeConfig(configFile), afterFirstGeneration));
    }

    @Test
    void preservesCrOnlyNewlinesWhenExpandingScalarPermission() throws Exception {
        Path configFile = tempDir.resolve("opencode.jsonc");
        Files.writeString(configFile,
                "{\r"
                                      + "  \"permission\": \"deny\",\r"
                                      + "  \"mcp\": {}\r"
                                      + "}\r");

        assertFirstRegenerationIsByteStable(configFile);

        String content = Files.readString(configFile);
        assertTrue(content.contains("\r"));
        assertFalse(content.contains("\n"));
        assertEquals("deny", readOpenCodeConfig(configFile).path("permission").path("*").asText());
        assertManagedEntriesWritten(readOpenCodeConfig(configFile));
    }

    @Test
    void preservesCrLfNewlinesWhenExpandingScalarPermission() throws Exception {
        Path configFile = tempDir.resolve("opencode.jsonc");
        Files.writeString(configFile,
                "{\r\n"
                                      + "  \"permission\": \"deny\",\r\n"
                                      + "  \"mcp\": {}\r\n"
                                      + "}\r\n");

        assertFirstRegenerationIsByteStable(configFile);

        String content = Files.readString(configFile);
        assertTrue(content.contains("\r\n"));
        assertFalse(content.replace("\r\n", "").contains("\r"));
        assertFalse(content.replace("\r\n", "").contains("\n"));
        assertEquals("deny", readOpenCodeConfig(configFile).path("permission").path("*").asText());
        assertManagedEntriesWritten(readOpenCodeConfig(configFile));
    }

    @Test
    void preservesCommentsAroundManagedPermissionsAcrossRepeatedInit() throws Exception {
        Path configFile = tempDir.resolve("opencode.jsonc");
        Files.writeString(configFile, """
                {
                "permission":{"custom":"deny" // c1
                , // c2
                "camel_*":/* c3 */"allow" // c4
                },
                "mcp":{"custom":{"type":"remote"},"camel":{"type":"old"}}
                }
                """);

        assertFirstRegenerationIsByteStable(configFile);

        String content = Files.readString(configFile);
        for (String comment : List.of("// c1", "// c2", "/* c3 */", "// c4")) {
            assertTrue(content.contains(comment), comment);
        }
        JsonNode config = readOpenCodeConfig(configFile);
        assertEquals("deny", config.path("permission").path("custom").asText());
        assertEquals("remote", config.path("mcp").path("custom").path("type").asText());
        assertManagedEntriesWritten(config);
    }

    @Test
    void preservesIrregularObjectTriviaAcrossRepeatedInit() throws Exception {
        Path configFile = tempDir.resolve("opencode.jsonc");
        Files.writeString(configFile, """
                {
                "permission":{"custom":"deny","camel_*":"allow"},
                "mcp":
                  { "custom":{"type":"remote"}
                \t,\t"camel"
                  :{\t"type":"old"
                \t}
                  }
                }
                """);

        assertFirstRegenerationIsByteStable(configFile);

        JsonNode config = readOpenCodeConfig(configFile);
        assertEquals("deny", config.path("permission").path("custom").asText());
        assertEquals("remote", config.path("mcp").path("custom").path("type").asText());
        assertManagedEntriesWritten(config);
    }

    @Test
    void stagesEveryConfigBeforeReplacingAnyOriginal() throws Exception {
        Path lower = tempDir.resolve("opencode.json");
        Path higherDirectory = Files.createDirectories(tempDir.resolve(".opencode"));
        Path higher = higherDirectory.resolve("opencode.jsonc");
        String lowerOriginal = """
                {"permission":{"camel_*":"allow"},"mcp":{"camel":{"type":"old"}}}
                """;
        String higherOriginal = """
                {"permission":{"camel_*":"allow"},"mcp":{"camel":{"type":"old"}}}
                """;
        Files.writeString(lower, lowerOriginal);
        Files.writeString(higher, higherOriginal);

        PosixFileAttributeView attributes = Files.getFileAttributeView(
                higherDirectory, PosixFileAttributeView.class);
        assumeTrue(attributes != null, "POSIX permissions are required");
        Set<PosixFilePermission> originalPermissions = attributes.readAttributes().permissions();
        try {
            Files.setPosixFilePermissions(higherDirectory, Set.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_EXECUTE));
            assumeTrue(!Files.isWritable(higherDirectory), "test user can bypass directory permissions");

            assertThrows(IOException.class, () -> new OpenCodeConfigMerger().merge(lower, """
                    {
                      "$schema": "https://opencode.ai/config.json",
                      "permission": {"camel_*": "ask"},
                      "mcp": {"camel": {"type": "local"}}
                    }
                    """));
        } finally {
            Files.setPosixFilePermissions(higherDirectory, originalPermissions);
        }

        assertEquals(lowerOriginal, Files.readString(lower));
        assertEquals(higherOriginal, Files.readString(higher));
        try (var rootFiles = Files.list(tempDir);
             var higherFiles = Files.list(higherDirectory)) {
            assertFalse(rootFiles.anyMatch(path -> path.getFileName().toString().endsWith(".tmp")));
            assertFalse(higherFiles.anyMatch(path -> path.getFileName().toString().endsWith(".tmp")));
        }
    }

    @Test
    void rendersLeafCommandAllowlistForPluginPrefix() throws Exception {
        new OpenCodeGenerator().generate(createContext("camel kit"));

        for (String name : new String[]{"brainstormer", "planner", "validator"}) {
            String content = Files.readString(tempDir.resolve(".opencode/agents/" + name + ".md"));
            assertTrue(content.contains("\"camel kit *\": allow"), name);
            assertFalse(content.contains("camel-kit *"), name);
        }
        String validator = Files.readString(tempDir.resolve(".opencode/agents/validator.md"));
        assertTrue(validator.contains("\"./mvnw *\": allow"));
        assertFalse(validator.contains("\"mvn *\": allow"));
    }

    @Test
    void generatedTraitsUseAvailableAgentsAndOwnedStepLimits() throws Exception {
        InitContext ctx = createContext();
        new OpenCodeGenerator().generate(ctx);

        String brainstorm = Files.readString(
                tempDir.resolve(".opencode/skills/camel-brainstorm/SKILL.md"));
        assertTrue(brainstorm.contains("loads this skill in the primary session"));
        assertTrue(brainstorm.contains("cannot own approval or phase handoff"));
        assertFalse(brainstorm.contains("Use the `Plan` agent type"));
        assertFalse(brainstorm.contains("`Explore` subagents"));

        String execute = Files.readString(
                tempDir.resolve(".opencode/skills/camel-execute/SKILL.md"));
        assertTrue(execute.contains("selects the generated primary `executor`"));
        assertTrue(execute.contains("subagent_type"));
        assertTrue(execute.contains("dispatch `implementer` (`steps: 50`)"));
        assertTrue(execute.contains("dispatch `migrator` (`steps: 50`)"));
        assertTrue(execute.contains("dispatch `tester` (`steps: 40`)"));
        assertTrue(execute.contains("dispatch `researcher` (`steps: 30`)"));
        assertTrue(execute.contains("dispatch `reviewer` (`steps: 50`)"));
        assertTrue(execute.contains("omitting `background`"));
        assertFalse(execute.contains("Implementation subagents: `steps: 100`"));
        assertFalse(execute.contains("use `General`"));
        assertFalse(execute.contains("use `Explore`"));
        assertFalse(execute.contains("use the `Build`"));
        assertTrue(execute.contains("`test-engineer`"));
        assertTrue(execute.contains("`migration-specialist`"));
        assertTrue(execute.contains("`subagent_type`"));
    }

    @Test
    void installsAndReferencesCompletePersonaLibrary() throws Exception {
        InitContext ctx = createContext();
        new OpenCodeGenerator().generate(ctx);

        Path personas = tempDir.resolve(".opencode/camel-kit-personas");
        assertDescriptorRegistersPersonas("opencode", ".opencode/camel-kit-personas");
        try (var files = Files.list(personas)) {
            assertEquals(Set.copyOf(PersonaResourceInstaller.PERSONAS),
                    files.map(path -> path.getFileName().toString().replace(".md", ""))
                            .collect(Collectors.toSet()));
        }
        String execute = Files.readString(ctx.skillsDir().resolve("camel-execute/SKILL.md"));
        assertTrue(execute.contains(".opencode/camel-kit-personas/catalog-researcher.md"));
        assertTrue(execute.contains(".opencode/camel-kit-personas/acr-moderator.md"));
        assertTrue(execute.contains(".opencode/camel-kit-personas/[persona].md"));
        assertFalse(execute.contains("`agents/"));
        assertNoBarePersonaReferences(ctx.skillsDir(), personas);
        String knowledge = Files.readString(ctx.skillsDir().resolve("camel-knowledge/SKILL.md"));
        assertTrue(knowledge.contains("subagent_type: researcher"));
        assertTrue(knowledge.contains(".opencode/camel-kit-personas/knowledge-researcher.md"));
        assertFalse(knowledge.contains("as a `knowledge-researcher` subagent"));
    }

    private void assertDescriptorRegistersPersonas(String agentName, String targetDirectory) {
        AgentDescriptor descriptor = AgentRegistry.descriptor(agentName);
        Set<String> targets = descriptor.templates().stream()
                .map(AgentDescriptor.TemplateInstall::target)
                .filter(target -> target.startsWith(targetDirectory + "/"))
                .map(target -> Path.of(target).getFileName().toString().replaceFirst("\\.md$", ""))
                .collect(Collectors.toSet());
        assertEquals(Set.copyOf(PersonaResourceInstaller.PERSONAS), targets);
    }

    private JsonNode readOpenCodeConfig(Path file) throws Exception {
        String content = Files.readString(file);
        if (content.startsWith("\uFEFF")) {
            content = content.substring(1);
        }
        String source = content;
        return assertDoesNotThrow(() -> OpenCodeProjectConfig.newJsonMapper().readTree(source), source);
    }

    private void assertFirstRegenerationIsByteStable(Path configFile) throws Exception {
        OpenCodeGenerator generator = new OpenCodeGenerator();
        generator.generate(createContext());
        String afterFirstGeneration = Files.readString(configFile);
        generator.generate(createContext());

        assertEquals(afterFirstGeneration, Files.readString(configFile));
        assertDoesNotThrow(() -> readOpenCodeConfig(configFile), afterFirstGeneration);
    }

    private void assertNoBarePersonaReferences(Path... roots) throws Exception {
        Pattern bareReference = Pattern.compile("(?<![A-Za-z0-9._/-])agents/");
        for (Path root : roots) {
            try (var files = Files.walk(root)) {
                for (Path file : files.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".md")).toList()) {
                    assertFalse(bareReference.matcher(Files.readString(file)).find(), file.toString());
                }
            }
        }
    }

    @Test
    void keepsInteractiveCommandsInPrimarySessionAndSelectsPrimaryExecutor() throws Exception {
        InitContext ctx = createContext();
        new OpenCodeGenerator().generate(ctx);

        for (String command : new String[]{"camel-brainstorm", "camel-plan", "camel-migrate", "camel-validate"}) {
            String content = Files.readString(ctx.commandsDir().resolve(command + ".md"));
            assertTrue(content.contains("Read .opencode/skills"), command);
            assertTrue(content.contains("Requested input: $ARGUMENTS"), command);
            assertFalse(content.contains("@"), command);
        }

        String execute = Files.readString(
                ctx.commandsDir().resolve("camel-execute.md"));
        assertTrue(execute.contains("agent: executor"));
        assertTrue(execute.contains("subtask: false"));
        assertTrue(execute.contains(
                "description: \"Execute a ready implementation plan derived from an approved design with an adversarial pre-filter and ordered spec and quality review.\""));
        assertTrue(execute.contains("$ARGUMENTS"));
        assertFalse(execute.contains("@executor"));
    }

    @Test
    void preservesBaseSkillBehavior() throws Exception {
        InitContext ctx = createContext();
        new OpenCodeGenerator().generate(ctx);

        // Standard commands without agents keep default content
        String content = Files.readString(
                ctx.commandsDir().resolve("camel-knowledge.md"));
        assertTrue(content.contains("Read .opencode/skills"));

        // MCP config exists
        assertTrue(Files.exists(tempDir.resolve("opencode.json")));
    }
}
