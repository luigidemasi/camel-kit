package io.github.luigidemasi.camelkit.generator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import io.github.luigidemasi.camelkit.config.AgentConfig;
import io.github.luigidemasi.camelkit.config.AgentRegistry;
import io.github.luigidemasi.camelkit.config.DistributionConfig;
import io.github.luigidemasi.camelkit.output.Printer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class DefaultGeneratorTest {

    @TempDir
    Path tempDir;

    private InitContext createContext(String agentName) {
        AgentConfig agent = AgentRegistry.get(agentName);
        String agentBaseFolder = agent.folder().substring(0, agent.folder().lastIndexOf("/"));
        Path commandsDir = tempDir.resolve(agent.folder());
        Path skillsDir = tempDir.resolve(agentBaseFolder + "/skills");
        return new InitContext(
                agent, agentName, commandsDir, skillsDir, tempDir,
                "camel-kit", Printer.noop());
    }

    @Test
    void generatesSlashCommands() throws Exception {
        InitContext ctx = createContext("bob");
        new DefaultGenerator().generate(ctx);

        assertTrue(Files.exists(ctx.commandsDir().resolve("camel-migrate.md")));
        assertTrue(Files.exists(ctx.commandsDir().resolve("camel-brainstorm.md")));
        assertTrue(Files.exists(ctx.commandsDir().resolve("camel-execute.md")));
        assertTrue(Files.exists(ctx.commandsDir().resolve("camel-start.md")));
        assertFalse(Files.exists(ctx.commandsDir().resolve("camel-flow.md")));
        assertFalse(Files.exists(ctx.commandsDir().resolve("camel-implement.md")));
        assertFalse(Files.exists(ctx.commandsDir().resolve("camel-test.md")));
        String content = Files.readString(ctx.commandsDir().resolve("camel-migrate.md"));
        assertTrue(content.contains("SKILL.md"));
    }

    @Test
    void copiesSkillsWithDispatch() throws Exception {
        InitContext ctx = createContext("bob");
        new DefaultGenerator().generate(ctx);

        assertTrue(Files.exists(ctx.skillsDir().resolve("camel-migrate/SKILL.md")));
        assertTrue(Files.exists(ctx.skillsDir().resolve("camel-brainstorm/SKILL.md")));
        String skillContent = Files.readString(ctx.skillsDir().resolve("camel-brainstorm/SKILL.md"));
        assertTrue(skillContent.contains("Dispatch"), "Dispatch block should be appended");
    }

    @Test
    void copiesSkillGuides() throws Exception {
        InitContext ctx = createContext("bob");
        new DefaultGenerator().generate(ctx);

        assertTrue(Files.isDirectory(ctx.skillsDir().resolve("camel-brainstorm/guides")));
        assertTrue(Files.isDirectory(ctx.skillsDir().resolve("camel-migrate/guides")));
    }

    @Test
    void generatesMcpConfig() throws Exception {
        InitContext ctx = createContext("bob");
        new DefaultGenerator().generate(ctx);

        assertTrue(Files.exists(tempDir.resolve(".bob/mcp.json")));
    }

    @Test
    void generatesAgentsMd() throws Exception {
        InitContext ctx = createContext("bob");
        new DefaultGenerator().generate(ctx);

        Path agentsMd = ctx.projectDir().resolve("AGENTS.md");
        assertTrue(Files.exists(agentsMd));
        String content = Files.readString(agentsMd);
        assertTrue(content.contains("Skill Routing"));
        assertTrue(content.contains("/camel-brainstorm"));
        assertTrue(content.contains("Iron Laws"));
        assertTrue(content.contains("MCP Catalog Verification"));
    }

    @Test
    void copiesIronLawsFile() throws Exception {
        InitContext ctx = createContext("bob");
        new DefaultGenerator().generate(ctx);

        Path ironLaws = ctx.skillsDir().resolve("shared/iron-laws.md");
        assertTrue(Files.exists(ironLaws));
    }

    @Test
    void appliesSkillLevelTraits() throws Exception {
        InitContext ctx = createContext("claude");
        new ClaudeGenerator().generate(ctx);

        Path brainstormSkill = ctx.skillsDir().resolve("camel-brainstorm/SKILL.md");
        String content = Files.readString(brainstormSkill);
        assertTrue(content.contains("<!-- TRAIT:claude -->"), "Sentinel should be present");
        assertTrue(content.contains("AskUserQuestion"), "Claude trait content should be appended");
    }

    @Test
    void traitsAreIdempotent() throws Exception {
        InitContext ctx = createContext("claude");
        ClaudeGenerator generator = new ClaudeGenerator();
        generator.generate(ctx);
        generator.generate(ctx);

        Path brainstormSkill = ctx.skillsDir().resolve("camel-brainstorm/SKILL.md");
        String content = Files.readString(brainstormSkill);
        int count = content.split("<!-- TRAIT:claude -->").length - 1;
        assertEquals(1, count, "Trait sentinel should appear exactly once after double init");
    }

    @Test
    void appliesGuideLevelTraits() throws Exception {
        InitContext ctx = createContext("claude");
        new ClaudeGenerator().generate(ctx);

        Path implementerGuide = ctx.skillsDir().resolve("camel-execute/guides/implementer-context.md");
        assertTrue(Files.exists(implementerGuide), "Guide-level trait should create implementer-context.md");
        String content = Files.readString(implementerGuide);
        assertTrue(content.length() > 0, "Guide should have content");
    }

    @Test
    void generatesShipCommand() throws Exception {
        InitContext ctx = createContext("bob");
        new DefaultGenerator().generate(ctx);

        assertTrue(Files.exists(ctx.commandsDir().resolve("camel-ship.md")));
        String content = Files.readString(ctx.commandsDir().resolve("camel-ship.md"));
        assertTrue(content.contains("SKILL.md"));
    }

    @Test
    void copiesShipSkill() throws Exception {
        InitContext ctx = createContext("bob");
        new DefaultGenerator().generate(ctx);

        assertTrue(Files.exists(ctx.skillsDir().resolve("camel-ship/SKILL.md")));
        assertTrue(Files.isDirectory(ctx.skillsDir().resolve("camel-ship/guides")));
        assertTrue(Files.exists(ctx.skillsDir().resolve("camel-ship/guides/oversight-matrix.md")));
        assertTrue(Files.exists(ctx.skillsDir().resolve("camel-ship/guides/state-management.md")));
        assertTrue(Files.exists(ctx.skillsDir().resolve("camel-ship/guides/auto-fix-loop.md")));
    }

    @Test
    void substitutesVersionPlaceholdersInSkillFiles() throws Exception {
        InitContext ctx = createContext("bob");
        new DefaultGenerator().generate(ctx);

        Path versionSelection = ctx.skillsDir().resolve("camel-brainstorm/guides/version-selection.md");
        assertTrue(Files.exists(versionSelection));
        String content = Files.readString(versionSelection);

        DistributionConfig dist = DistributionConfig.loadFromClasspathOrDefaults();
        var mappings = dist.quarkusPlatformMappings();
        assertFalse(mappings.isEmpty(), "Expected explicit Quarkus platform mappings");
        assertFalse(content.contains("{QUARKUS_PLATFORM_VERSION}"),
                "Placeholder should be substituted");
        assertTrue(content.contains(dist.quarkusPlatformVersion()),
                "Resolved value should appear");
        assertFalse(content.contains("{QUARKUS_PLATFORM_TABLE}"),
                "Table placeholder should be substituted");
        for (var entry : mappings.entrySet()) {
            assertTrue(content.contains(entry.getValue()),
                    "Mapping for " + entry.getKey() + " should appear in table");
        }
    }

    @Test
    void substitutionPreservesNonPlaceholderBraces() throws IOException {
        Path mdFile = tempDir.resolve("test.md");
        Files.writeString(mdFile, """
                jackson: {}
                ${quarkus.platform.version}
                {COMMAND_PREFIX} graph stats
                Version: {QUARKUS_PLATFORM_VERSION}
                """);
        DefaultGenerator.substituteVersionPlaceholders(mdFile);
        String result = Files.readString(mdFile);

        DistributionConfig dist = DistributionConfig.loadFromClasspathOrDefaults();
        assertTrue(result.contains("jackson: {}"), "YAML empty map must be preserved");
        assertTrue(result.contains("${quarkus.platform.version}"), "Maven property must be preserved");
        assertTrue(result.contains("{COMMAND_PREFIX} graph stats"), "Non-version placeholder must be preserved");
        assertTrue(result.contains(dist.quarkusPlatformVersion()), "Version placeholder must be resolved");
        assertFalse(result.contains("{QUARKUS_PLATFORM_VERSION}"), "Version placeholder must not remain");
    }

    @Test
    void substitutionSkipsFilesWithoutPlaceholders() throws IOException {
        Path mdFile = tempDir.resolve("plain.md");
        String original = "No placeholders here. Just some text with {braces} and ${maven}.";
        Files.writeString(mdFile, original);
        DefaultGenerator.substituteVersionPlaceholders(mdFile);
        assertEquals(original, Files.readString(mdFile), "File without version placeholders must not change");
    }

    @Test
    void wrapsTomlForGemini() throws Exception {
        InitContext ctx = createContext("gemini");
        new DefaultGenerator().generate(ctx);

        Path geminiCmd = ctx.commandsDir().resolve("camel-migrate.toml");
        assertTrue(Files.exists(geminiCmd));
        String content = Files.readString(geminiCmd);
        assertTrue(content.contains("description ="));
        assertTrue(content.contains("prompt ="));
    }
}
