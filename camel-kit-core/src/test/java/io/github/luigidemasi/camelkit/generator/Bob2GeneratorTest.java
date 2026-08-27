package io.github.luigidemasi.camelkit.generator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import io.github.luigidemasi.camelkit.config.AgentConfig;
import io.github.luigidemasi.camelkit.config.AgentRegistry;
import io.github.luigidemasi.camelkit.output.Printer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class Bob2GeneratorTest {

    private static final Set<String> PERSONA_FILES = Set.of(
            "acr-moderator.md",
            "catalog-researcher.md",
            "code-quality-reviewer.md",
            "critic-behavioral-equivalence.md",
            "critic-boundary-compliance.md",
            "critic-performance.md",
            "critic-route-architecture.md",
            "critic-security.md",
            "implementation-engineer.md",
            "integration-architect.md",
            "knowledge-researcher.md",
            "migration-specialist.md",
            "spec-compliance-reviewer.md",
            "test-engineer.md");

    @TempDir
    Path tempDir;

    private InitContext createContext() {
        return createContext("bob2");
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

    @Test
    void generatesBobWorkspaceFilesUnderDotBob() throws Exception {
        InitContext ctx = createContext();
        Map<String, String> legacyRules = Map.of(
                "camel-brainstorm", "brainstorm.md",
                "camel-plan", "plan.md",
                "camel-implement", "implement.md",
                "camel-execute", "execute.md",
                "camel-validate", "validate.md",
                "camel-test", "test.md",
                "camel-debug", "debug.md");
        for (Map.Entry<String, String> rule : legacyRules.entrySet()) {
            Path file = tempDir.resolve(".bob/rules-" + rule.getKey()).resolve(rule.getValue());
            Files.createDirectories(file.getParent());
            Files.writeString(file, "obsolete generated rule");
        }
        Path neighbor = tempDir.resolve(".bob/rules-camel-execute/keep.md");
        Files.writeString(neighbor, "user rule");

        new Bob2Generator().generate(ctx);

        assertTrue(Files.isDirectory(tempDir.resolve(".bob/commands")));
        assertTrue(Files.isDirectory(tempDir.resolve(".bob/skills")));
        assertTrue(Files.isRegularFile(tempDir.resolve(".bob/mcp.json")));
        assertTrue(Files.isRegularFile(tempDir.resolve(".bob/custom_modes.yaml")));
        assertTrue(Files.isRegularFile(tempDir.resolve(".bob/agents/camel-worker.md")));
        assertTrue(Files.isRegularFile(tempDir.resolve(".bob/agents/camel-reviewer.md")));
        assertTrue(Files.isRegularFile(tempDir.resolve(".bob/personas/acr-moderator.md")));
        assertTrue(Files.isRegularFile(tempDir.resolve(".bob/rules/iron-laws.md")));
        legacyRules.forEach((slug, file) -> {
            assertFalse(Files.exists(tempDir.resolve(".bob/rules-" + slug).resolve(file)), slug);
            assertTrue(Files.isRegularFile(tempDir.resolve(".bob/rules-" + slug + "-mode").resolve(file)), slug);
        });
        assertEquals("user rule", Files.readString(neighbor));
        String executeRule = Files.readString(tempDir.resolve(".bob/rules-camel-execute-mode/execute.md"));
        assertTrue(executeRule.contains("separate fresh subagent calls"));
        assertTrue(executeRule.contains("name: \"camel-reviewer\""));
        assertTrue(executeRule.contains("name: \"camel-worker\""));
        assertFalse(executeRule.contains("separate `explore` calls for ACR Moderator Phase 1, selected critics"));
    }

    @Test
    void switchingFromBob1RemovesOnlyBob1ModeRules() throws Exception {
        new BobGenerator().generate(createContext("bob"));
        Map<String, String> bob1Rules = Map.of(
                "camel-brainstorm", "interview-gates.md",
                "camel-plan", "plan-structure.md",
                "camel-implement", "implementation.md",
                "camel-validate", "validation.md",
                "camel-test", "testing.md");
        for (Map.Entry<String, String> rule : bob1Rules.entrySet()) {
            Path file = tempDir.resolve(".bob/rules-" + rule.getKey()).resolve(rule.getValue());
            Files.createDirectories(file.getParent());
            Files.writeString(file, "obsolete Bob 1 rule");
        }
        Path neighbor = tempDir.resolve(".bob/rules-camel-plan-mode/keep.md");
        Files.writeString(neighbor, "user rule");
        Path unsuffixedNeighbor = tempDir.resolve(".bob/rules-camel-plan/keep.md");
        Files.writeString(unsuffixedNeighbor, "user unsuffixed rule");

        new Bob2Generator().generate(createContext());

        bob1Rules.forEach((slug, file) -> {
            assertFalse(Files.exists(tempDir.resolve(".bob/rules-" + slug).resolve(file)), slug);
            assertFalse(Files.exists(tempDir.resolve(".bob/rules-" + slug + "-mode").resolve(file)), slug);
        });
        assertTrue(Files.isRegularFile(tempDir.resolve(".bob/rules-camel-plan-mode/plan.md")));
        assertTrue(Files.isRegularFile(tempDir.resolve(".bob/agents/camel-worker.md")));
        assertTrue(Files.isRegularFile(tempDir.resolve(".bob/agents/camel-reviewer.md")));
        assertTrue(Files.isRegularFile(tempDir.resolve(".bob/personas/catalog-researcher.md")));
        assertEquals("user rule", Files.readString(neighbor));
        assertEquals("user unsuffixed rule", Files.readString(unsuffixedNeighbor));
    }

    @Test
    void generatesCapabilityScopedProjectAgents() throws Exception {
        new Bob2Generator().generate(createContext());

        String worker = Files.readString(tempDir.resolve(".bob/agents/camel-worker.md"));
        String workerFrontmatter = frontmatter(worker);
        assertTrue(workerFrontmatter.contains("name: camel-worker"));
        assertTrue(workerFrontmatter.contains("groups:\n  - read\n  - edit\n  - execute\n  - mcp\n  - skill"));
        assertTrue(workerFrontmatter.contains("allowForkContext: true"));
        assertFalse(workerFrontmatter.contains("groups: ["));
        assertFalse(workerFrontmatter.contains("  - subagent"));
        assertTrue(worker.contains("Do not spawn subagents or switch modes"));

        String reviewer = Files.readString(tempDir.resolve(".bob/agents/camel-reviewer.md"));
        String reviewerFrontmatter = frontmatter(reviewer);
        assertTrue(reviewerFrontmatter.contains("name: camel-reviewer"));
        assertTrue(reviewerFrontmatter.contains("groups:\n  - read\n  - mcp"));
        assertTrue(reviewerFrontmatter.contains("allowForkContext: true"));
        assertFalse(reviewerFrontmatter.contains("groups: ["));
        assertFalse(reviewerFrontmatter.contains("  - edit"));
        assertFalse(reviewerFrontmatter.contains("  - execute"));
        assertTrue(reviewer.contains("never edit files or run commands"));
        assertTrue(reviewer.contains("full role text the parent loads from `.bob/personas/`"));
    }

    @Test
    void generatesPersonaLibraryAndRewritesBob2References() throws Exception {
        InitContext ctx = createContext();
        Path userSkill = ctx.skillsDir().resolve("custom/SKILL.md");
        Files.createDirectories(userSkill.getParent());
        Files.writeString(userSkill, "Keep user reference agents/catalog-researcher.md unchanged");
        new Bob2Generator().generate(ctx);

        Path personas = tempDir.resolve(".bob/personas");
        try (var files = Files.list(personas)) {
            assertEquals(PERSONA_FILES, files.map(path -> path.getFileName().toString()).collect(Collectors.toSet()));
        }
        String execute = Files.readString(ctx.skillsDir().resolve("camel-execute/SKILL.md"));
        assertTrue(execute.contains(".bob/personas/catalog-researcher.md"));
        assertTrue(execute.contains(".bob/personas/acr-moderator.md"));
        assertTrue(execute.contains(".bob/personas/spec-compliance-reviewer.md"));
        assertTrue(execute.contains(".bob/personas/code-quality-reviewer.md"));
        assertTrue(execute.contains(".bob/personas/[persona].md"));
        assertFalse(execute.contains("`agents/catalog-researcher.md`"));
        assertFalse(execute.contains("`agents/acr-moderator.md`"));

        String knowledge = Files.readString(ctx.skillsDir().resolve("camel-knowledge/SKILL.md"));
        assertTrue(knowledge.contains(".bob/personas/knowledge-researcher.md"));
        assertTrue(knowledge.contains("name: \"camel-reviewer\""));
        assertTrue(knowledge.contains("Do not dispatch an unregistered `knowledge-researcher` preset"));

        String implementerContext = Files.readString(
                ctx.skillsDir().resolve("camel-execute/guides/implementer-context.md"));
        assertTrue(implementerContext.contains(".bob/personas/[persona].md"));

        String moderator = Files.readString(personas.resolve("acr-moderator.md"));
        assertTrue(moderator.contains(".bob/personas/critic-route-architecture.md"));
        assertTrue(moderator.contains(".bob/personas/critic-security.md"));
        assertTrue(moderator.contains(".bob/personas/critic-<lane>.md"));
        assertFalse(moderator.contains("`agents/critic-route-architecture.md`"));

        for (Path root : Set.of(ctx.skillsDir(), personas)) {
            try (var markdown = Files.walk(root)) {
                for (Path file : markdown.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".md"))
                        .toList()) {
                    if (!file.equals(userSkill)) {
                        assertFalse(Pattern.compile("(?<!\\.bob/)agents/").matcher(Files.readString(file)).find(),
                                file.toString());
                    }
                }
            }
        }
        assertEquals("Keep user reference agents/catalog-researcher.md unchanged", Files.readString(userSkill));
    }

    @Test
    void customModesUseBob2ToolGroupsAndAllowedSubagents() throws Exception {
        InitContext ctx = createContext();
        new Bob2Generator().generate(ctx);

        String content = Files.readString(tempDir.resolve(".bob/custom_modes.yaml"));
        assertTrue(content.contains("- execute"));
        assertTrue(content.contains("- skill"));
        assertTrue(content.contains("- todo"));
        assertTrue(content.contains("- artifact"));
        assertTrue(content.contains("- subagent"));
        assertTrue(content.contains("- mode"));
        assertTrue(content.contains("allowedSubagents: [explore]"));
        assertTrue(content.contains("allowedSubagents: [explore, camel-reviewer]"));
        assertTrue(content.contains("allowedSubagents: [explore, camel-worker, camel-reviewer]"));
        assertTrue(content.contains("allowedSubagents: [camel-reviewer]"));
        assertTrue(content.contains("allowedSubagents: [camel-worker, camel-reviewer]"));
        assertFalse(content.contains("general"));
        assertTrue(content.contains("pipeline\\\\.json"));
        assertTrue(content.contains("validation-report\\\\.md"));
        assertFalse(content.contains("\n      - command\n"));
        Set<String> slugs = content.lines()
                .filter(line -> line.startsWith("  - slug: "))
                .map(line -> line.substring("  - slug: ".length()))
                .collect(Collectors.toSet());
        assertEquals(7, content.lines().filter(line -> line.startsWith("  - slug: ")).count());
        assertEquals(Set.of("camel-brainstorm-mode", "camel-plan-mode", "camel-implement-mode",
                "camel-execute-mode", "camel-validate-mode", "camel-test-mode", "camel-debug-mode"), slugs);
        for (String slug : slugs) {
            assertFalse(Files.exists(ctx.commandsDir().resolve(slug + ".md")), slug);
        }
        String brainstormMode = content.substring(
                content.indexOf("slug: camel-brainstorm-mode"), content.indexOf("slug: camel-plan-mode"));
        assertTrue(brainstormMode.contains("camel-brainstorm for greenfield work or camel-migrate for migration"));
        String planMode = content.substring(
                content.indexOf("slug: camel-plan-mode"), content.indexOf("slug: camel-implement-mode"));
        String implementMode = content.substring(
                content.indexOf("slug: camel-implement-mode"), content.indexOf("slug: camel-execute-mode"));
        String validateMode = content.substring(
                content.indexOf("slug: camel-validate-mode"), content.indexOf("slug: camel-test-mode"));
        String executeMode = content.substring(
                content.indexOf("slug: camel-execute-mode"), content.indexOf("slug: camel-validate-mode"));
        String testMode = content.substring(
                content.indexOf("slug: camel-test-mode"), content.indexOf("slug: camel-debug-mode"));
        String debugMode = content.substring(content.indexOf("slug: camel-debug-mode"));
        assertAllowedSubagents(brainstormMode, "[explore, camel-reviewer]");
        assertAllowedSubagents(planMode, "[explore]");
        assertAllowedSubagents(implementMode, "[explore]");
        assertAllowedSubagents(executeMode, "[explore, camel-worker, camel-reviewer]");
        assertAllowedSubagents(validateMode, "[camel-reviewer]");
        assertAllowedSubagents(testMode, "[camel-reviewer]");
        assertAllowedSubagents(debugMode, "[camel-worker, camel-reviewer]");
        for (String restrictedMode : Set.of(planMode, implementMode, validateMode, testMode)) {
            assertFalse(restrictedMode.contains("camel-worker"));
        }
        assertTrue(testMode.contains("(.*/)?src/test/resources/"));
        assertTrue(testMode.contains("pom\\\\.xml"));
        assertFalse(testMode.contains("docs/test-report"));
        assertEditAllows(brainstormMode,
                "docs/design.md", "./docs/design.md",
                ".camel-kit/project-snapshot.md", "./.camel-kit/project-snapshot.md");
        assertEditRejects(brainstormMode,
                "src/main/resources/routes/orders.camel.yaml", "README.md", ".camel-kit/secrets.txt");
        assertEditAllows(planMode,
                "docs/camel-kit/001/implementation-plan.md", "./docs/camel-kit/001/implementation-plan.md");
        assertEditRejects(planMode,
                "docs/design.md", "src/main/resources/routes/orders.camel.yaml", ".camel-kit/pipeline.json");
        assertEditAllows(validateMode,
                "docs/camel-kit/001/validation-report.md", "./docs/camel-kit/001/validation-report.md");
        assertEditRejects(validateMode,
                "docs/camel-kit/001/design-spec.md", "docs/camel-kit/001/execution-report.md",
                "src/main/resources/routes/orders.camel.yaml");
        assertEditAllows(testMode,
                "src/test/resources/orders.camel.it.yaml", "./src/test/resources/orders.camel.it.yaml",
                "module/src/test/resources/orders.camel.it.yaml", "./module/src/test/resources/orders.camel.it.yaml",
                "pom.xml", "./pom.xml", "module/pom.xml", "./module/pom.xml");
        assertEditRejects(testMode,
                "src/main/resources/routes/orders.camel.yaml", "README.md", "pom.xml.bak",
                "docs/test-report.md", "test/orders.camel.it.yaml",
                "docs/camel-kit/001/validation-report.md");
    }

    @Test
    void keepsSharedSkillsAndAppendsBob2Traits() throws Exception {
        InitContext ctx = createContext();
        new Bob2Generator().generate(ctx);

        Path executeSkill = ctx.skillsDir().resolve("camel-execute/SKILL.md");
        assertTrue(Files.isRegularFile(executeSkill));
        String content = Files.readString(executeSkill);
        assertTrue(content.contains("# Camel Execute"));
        assertTrue(content.contains("<!-- TRAIT:bob2 -->"));
        assertTrue(content.contains("spawn_subagent"));
        assertTrue(content.contains("name: \"explore\""));
        assertTrue(content.contains("name: \"camel-worker\""));
        assertTrue(content.contains("name: \"camel-reviewer\""));
        assertFalse(content.contains("name: \"general\""));
        assertTrue(content.contains("name: \"explore\"` only for factual source search"));
        String trait = content.substring(content.indexOf("## Agent Optimization: IBM Bob 2"));
        int catalogResearch = trait.indexOf(".bob/personas/catalog-researcher.md");
        int implementation = trait.indexOf("For implementation, test generation");
        int moderatorPhase1 = trait.indexOf("for Phase 1 only");
        int critics = trait.indexOf("The parent loads the selected `.bob/personas/critic-*.md`");
        int moderatorPhase2 = trait.indexOf("for Phase 2 only");
        int spec = trait.indexOf(".bob/personas/spec-compliance-reviewer.md");
        int quality = trait.indexOf(".bob/personas/code-quality-reviewer.md");
        assertTrue(catalogResearch >= 0 && catalogResearch < implementation && implementation < moderatorPhase1
                && moderatorPhase1 < critics
                && critics < moderatorPhase2 && moderatorPhase2 < spec && spec < quality);
        assertTrue(trait.contains("dispatch it as a fresh\n   `camel-reviewer`"));
        assertTrue(trait.contains("spawns every critic lane together as fresh\n    `camel-reviewer`"));
        assertFalse(content.contains("every selected ACR critic lane together as separate fresh `explore`"));
        assertFalse(content.contains("APPROVAL GATE"));
        assertFalse(content.contains("gates/camel-execute.md"));
    }

    @Test
    void capabilityScopedReviewerHandlesAnalysisAndMcp() throws Exception {
        InitContext ctx = createContext();
        new Bob2Generator().generate(ctx);

        String validate = Files.readString(ctx.skillsDir().resolve("camel-validate/SKILL.md"));
        String debug = Files.readString(ctx.skillsDir().resolve("camel-debug/SKILL.md"));
        String implement = Files.readString(ctx.skillsDir().resolve("camel-implement/SKILL.md"));
        String test = Files.readString(ctx.skillsDir().resolve("camel-test/SKILL.md"));
        assertTrue(validate.contains("name: \"camel-reviewer\""));
        assertTrue(validate.contains("read and MCP groups only"));
        assertTrue(debug.contains("name: \"camel-reviewer\""));
        assertTrue(debug.contains("name: \"camel-worker\""));
        assertFalse(validate.contains("name: \"explore\"` for validation lanes"));
        assertFalse(debug.contains("name: \"explore\"` for route analysis"));
        assertFalse(validate.contains("name: \"general\""));
        assertFalse(debug.contains("name: \"general\""));
        for (String restrictedSkill : Set.of(implement, test)) {
            assertTrue(restrictedSkill.contains(
                    "Restricted implement and test\n  modes perform mutations inline"));
        }
        String testRule = Files.readString(tempDir.resolve(".bob/rules-camel-test-mode/test.md"));
        assertTrue(testRule.contains("Perform test generation and test fixes inline"));
        assertTrue(testRule.contains("Reviewer subagents return evidence, findings"));
        assertTrue(testRule.contains("`{module}/src/test/resources/`"));
        assertTrue(testRule.contains("pipeline `execution-report.md`"));
        assertFalse(testRule.contains("Follow TDD"));
        assertFalse(testRule.contains("docs/test-report.md"));
    }

    @Test
    void commandStubsIncludeBob2Frontmatter() throws Exception {
        InitContext ctx = createContext();
        new Bob2Generator().generate(ctx);

        Path command = ctx.commandsDir().resolve("camel-execute.md");
        assertTrue(Files.isRegularFile(command));
        String content = Files.readString(command);
        assertTrue(content.startsWith("---\n"));
        assertTrue(content.contains(
                "description: \"Execute a ready implementation plan derived from an approved design with an adversarial pre-filter and ordered spec and quality review.\""));
        assertTrue(content.contains("argument-hint: \"<pipeline-id-or-plan>\""));
        assertTrue(content.contains("Read .bob/skills/camel-execute/SKILL.md and follow those instructions"));
    }

    @Test
    void skillFrontmatterKeepsSharedMetadataAndAddsBobReadableUserInvocable() throws Exception {
        InitContext ctx = createContext();
        new Bob2Generator().generate(ctx);

        String startSkill = Files.readString(ctx.skillsDir().resolve("camel-start/SKILL.md"));
        assertTrue(startSkill.contains("user_invocable: true"));
        assertTrue(startSkill.contains("user-invocable: true"));
        assertSingleBlankLineBeforeDispatch(startSkill);

        String executeSkill = Files.readString(ctx.skillsDir().resolve("camel-execute/SKILL.md"));
        assertTrue(executeSkill.contains("user_invocable: false"));
        assertTrue(executeSkill.contains("user-invocable: false"));
        assertSingleBlankLineBeforeDispatch(executeSkill);
    }

    @Test
    void doesNotChangeLegacyBobGenerationContract() throws Exception {
        AgentConfig bob = AgentRegistry.get("bob");
        String agentBaseFolder = bob.folder().substring(0, bob.folder().lastIndexOf("/"));
        InitContext bobCtx = new InitContext(
                bob, "bob", tempDir.resolve("legacy").resolve(bob.folder()),
                tempDir.resolve("legacy").resolve(agentBaseFolder + "/skills"),
                tempDir.resolve("legacy"), "camel-kit", Printer.noop());

        new BobGenerator().generate(bobCtx);

        String command = Files.readString(bobCtx.commandsDir().resolve("camel-execute.md"));
        assertEquals("Read .bob/skills/camel-execute/SKILL.md and follow those instructions", command);

        String executeSkill = Files.readString(bobCtx.skillsDir().resolve("camel-execute/SKILL.md"));
        assertTrue(executeSkill.contains("CHECKPOINT"));
        assertTrue(executeSkill.contains("Switch to"));
        assertFalse(executeSkill.contains("user-invocable:"));
        assertFalse(executeSkill.contains("<!-- TRAIT:bob2 -->"));
    }

    private void assertSingleBlankLineBeforeDispatch(String content) {
        int dispatchIndex = content.indexOf("\n---\n\n## Dispatch");
        assertTrue(dispatchIndex > 0);
        assertFalse(content.substring(0, dispatchIndex).endsWith("\n\n"));
    }

    private String frontmatter(String content) {
        int end = content.indexOf("\n---\n", 4);
        assertTrue(content.startsWith("---\n") && end > 0);
        return content.substring(0, end);
    }

    private void assertEditAllows(String mode, String... paths) {
        Pattern pattern = editPattern(mode);
        for (String path : paths) {
            assertTrue(pattern.matcher(path).find(), path + " does not match " + pattern);
        }
    }

    private void assertEditRejects(String mode, String... paths) {
        Pattern pattern = editPattern(mode);
        for (String path : paths) {
            assertFalse(pattern.matcher(path).find(), path + " unexpectedly matches " + pattern);
        }
    }

    private Pattern editPattern(String mode) {
        String prefix = "- fileRegex: ";
        String expression = mode.lines()
                .map(String::trim)
                .filter(line -> line.startsWith(prefix))
                .map(line -> line.substring(prefix.length()))
                .findFirst()
                .orElseThrow();
        if (expression.startsWith("\"") && expression.endsWith("\"")) {
            expression = expression.substring(1, expression.length() - 1).replace("\\\\", "\\");
        }
        return Pattern.compile(expression);
    }

    private void assertAllowedSubagents(String mode, String expected) {
        assertEquals(1, mode.lines()
                .map(String::trim)
                .filter(line -> line.startsWith("allowedSubagents:"))
                .count());
        assertTrue(mode.contains("allowedSubagents: " + expected));
    }
}
