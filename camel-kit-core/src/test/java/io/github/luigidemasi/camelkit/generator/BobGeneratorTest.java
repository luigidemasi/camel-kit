package io.github.luigidemasi.camelkit.generator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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

class BobGeneratorTest {

    @TempDir
    Path tempDir;

    private InitContext createContext() {
        return createContext("bob");
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
    void generatesCustomModes() throws Exception {
        InitContext ctx = createContext();
        new BobGenerator().generate(ctx);

        Path modesFile = tempDir.resolve(".bob/custom_modes.yaml");
        assertTrue(Files.exists(modesFile));
        String content = Files.readString(modesFile);
        assertTrue(content.contains("slug: camel-brainstorm-mode"));
        assertTrue(content.contains("slug: camel-plan-mode"));
        assertTrue(content.contains("slug: camel-implement-mode"));
        assertTrue(content.contains("slug: camel-validate-mode"));
        assertTrue(content.contains("slug: camel-test-mode"));
    }

    @Test
    void generatesSharedRules() throws Exception {
        InitContext ctx = createContext();
        new BobGenerator().generate(ctx);

        Path rulesDir = tempDir.resolve(".bob/rules");
        assertTrue(Files.isDirectory(rulesDir));
        Path ironLaws = rulesDir.resolve("iron-laws.md");
        assertTrue(Files.exists(ironLaws));
        String content = Files.readString(ironLaws);
        assertTrue(content.contains("MCP Catalog Verification"));
    }

    @Test
    void generatesModeSpecificRules() throws Exception {
        InitContext ctx = createContext();
        Map<String, String> legacyRules = Map.of(
                "camel-brainstorm", "interview-gates.md",
                "camel-plan", "plan-structure.md",
                "camel-implement", "implementation.md",
                "camel-validate", "validation.md",
                "camel-test", "testing.md");
        for (Map.Entry<String, String> rule : legacyRules.entrySet()) {
            Path file = tempDir.resolve(".bob/rules-" + rule.getKey()).resolve(rule.getValue());
            Files.createDirectories(file.getParent());
            Files.writeString(file, "obsolete generated rule");
        }
        Path neighbor = tempDir.resolve(".bob/rules-camel-plan/keep.md");
        Files.writeString(neighbor, "user rule");

        new BobGenerator().generate(ctx);

        legacyRules.forEach((slug, file) -> {
            assertFalse(Files.exists(tempDir.resolve(".bob/rules-" + slug).resolve(file)), slug);
            assertTrue(Files.isRegularFile(tempDir.resolve(".bob/rules-" + slug + "-mode").resolve(file)), slug);
        });
        assertEquals("user rule", Files.readString(neighbor));

        String brainstormRules = Files.readString(
                tempDir.resolve(".bob/rules-camel-brainstorm-mode/interview-gates.md"));
        assertTrue(brainstormRules.contains("ONE question at a time"));
    }

    @Test
    void switchingFromBob2RemovesOnlyBob2ModeRules() throws Exception {
        new Bob2Generator().generate(createContext("bob2"));
        List<String> bob2Rules = registeredModeRules("bob2");
        for (String rule : bob2Rules) {
            Path file = tempDir.resolve(legacyModeRule(rule));
            Files.createDirectories(file.getParent());
            Files.writeString(file, "obsolete Bob 2 rule");
        }
        Path neighbor = tempDir.resolve(".bob/rules-camel-execute-mode/keep.md");
        Files.writeString(neighbor, "user rule");
        Path unsuffixedNeighbor = tempDir.resolve(".bob/rules-camel-execute/keep.md");
        Files.writeString(unsuffixedNeighbor, "user unsuffixed rule");
        Path agentNeighbor = tempDir.resolve(".bob/agents/keep.md");
        Files.writeString(agentNeighbor, "user agent");
        Path personaNeighbor = tempDir.resolve(".bob/personas/keep.md");
        Files.writeString(personaNeighbor, "user persona");

        new BobGenerator().generate(createContext());

        bob2Rules.forEach(rule -> {
            assertFalse(Files.exists(tempDir.resolve(legacyModeRule(rule))), rule);
            assertFalse(Files.exists(tempDir.resolve(rule)), rule);
        });
        assertTrue(Files.isRegularFile(
                tempDir.resolve(".bob/rules-camel-plan-mode/plan-structure.md")));
        AgentRegistry.descriptor("bob2").templates().stream()
                .map(template -> template.target())
                .filter(target -> target.startsWith(".bob/agents/") || target.startsWith(".bob/personas/"))
                .forEach(target -> assertFalse(Files.exists(tempDir.resolve(target)), target));
        assertEquals("user agent", Files.readString(agentNeighbor));
        try (var personas = Files.list(tempDir.resolve(".bob/personas"))) {
            assertEquals(Set.of("keep.md"),
                    personas.map(path -> path.getFileName().toString()).collect(Collectors.toSet()));
        }
        assertEquals("user persona", Files.readString(personaNeighbor));
        assertEquals("user rule", Files.readString(neighbor));
        assertEquals("user unsuffixed rule", Files.readString(unsuffixedNeighbor));
    }

    private List<String> registeredModeRules(String agentName) {
        return AgentRegistry.descriptor(agentName).templates().stream()
                .map(template -> template.target())
                .filter(target -> target.startsWith(".bob/rules-") && target.contains("-mode/"))
                .toList();
    }

    private String legacyModeRule(String target) {
        return target.replace("-mode/", "/");
    }

    @Test
    void generatesMonolithicSkills() throws Exception {
        InitContext ctx = createContext();
        new BobGenerator().generate(ctx);

        Path migrateSkill = ctx.skillsDir().resolve("camel-migrate/SKILL.md");
        assertTrue(Files.exists(migrateSkill));
        String content = Files.readString(migrateSkill);
        // Should contain gate content, not the base delegation
        assertTrue(content.contains("<Steps>"));
        assertTrue(content.contains("Switch to"));
        assertTrue(content.contains("CHECKPOINT"));
        assertTrue(content.contains("APPROVAL GATE"));
        // Should NOT contain the base "Invoke camel-brainstorm" delegation
        assertFalse(content.contains("Invoke `camel-brainstorm/SKILL.md`"));
    }

    @Test
    void copiesGuideFiles() throws Exception {
        InitContext ctx = createContext();
        new BobGenerator().generate(ctx);

        // Guide files should still be copied for reference
        assertTrue(Files.isDirectory(ctx.skillsDir().resolve("camel-migrate/guides")));
        assertTrue(Files.isDirectory(ctx.skillsDir().resolve("camel-brainstorm/guides")));
    }

    @Test
    void preservesSlashCommands() throws Exception {
        InitContext ctx = createContext();
        new BobGenerator().generate(ctx);

        assertTrue(Files.exists(ctx.commandsDir().resolve("camel-migrate.md")));
        assertTrue(Files.exists(ctx.commandsDir().resolve("camel-brainstorm.md")));
        assertTrue(Files.exists(ctx.commandsDir().resolve("camel-execute.md")));
        assertFalse(Files.exists(ctx.commandsDir().resolve("camel-implement.md")));
    }

    @Test
    void generatesMcpConfig() throws Exception {
        InitContext ctx = createContext();
        new BobGenerator().generate(ctx);

        assertTrue(Files.exists(tempDir.resolve(".bob/mcp.json")));
    }

    @Test
    void generatesIronLawsRule() throws Exception {
        InitContext ctx = createContext();
        new BobGenerator().generate(ctx);

        Path ironLaws = tempDir.resolve(".bob/rules/iron-laws.md");
        assertTrue(Files.exists(ironLaws));
        String content = Files.readString(ironLaws);
        assertTrue(content.contains("MCP Catalog Verification"));
    }

    @Test
    void substitutesQuteVariables() throws Exception {
        InitContext ctx = createContext();
        new BobGenerator().generate(ctx);

        Path brainstormSkill = ctx.skillsDir().resolve("camel-brainstorm/SKILL.md");
        String content = Files.readString(brainstormSkill);
        assertTrue(content.contains("camel-kit nextId"));
        assertFalse(content.contains("{COMMAND_PREFIX}"));
    }

    @Test
    void generatedWorkflowUsesOneApprovalAndValidatesAfterVerification() throws Exception {
        InitContext ctx = createContext();
        new BobGenerator().generate(ctx);

        String brainstorm = Files.readString(ctx.skillsDir().resolve("camel-brainstorm/SKILL.md"));
        String migrate = Files.readString(ctx.skillsDir().resolve("camel-migrate/SKILL.md"));
        String plan = Files.readString(ctx.skillsDir().resolve("camel-plan/SKILL.md"));
        String execute = Files.readString(ctx.skillsDir().resolve("camel-execute/SKILL.md"));
        String modes = Files.readString(tempDir.resolve(".bob/custom_modes.yaml"));

        assertEquals(1, brainstorm.split("APPROVAL GATE", -1).length - 1);
        assertEquals(1, migrate.split("APPROVAL GATE", -1).length - 1);
        assertTrue(brainstorm.contains("Modes (`camel-implement-mode`, `camel-test-mode`)"));
        assertFalse(brainstorm.contains("Modes (`camel-implement`, `camel-test`)"));
        assertFalse(brainstorm.contains("Do you approve this plan?"));
        assertFalse(migrate.contains("migration plan is ready"));
        assertFalse(plan.contains("Do you approve this plan?"));
        assertFalse(plan.contains("complete code"));
        assertTrue(plan.contains("switch to **camel-execute-mode**"));
        assertFalse(plan.contains("switch to **camel-execute**"));
        assertTrue(plan.contains(".bob/skills/camel-execute/SKILL.md"));
        int brainstormStandalone = brainstorm.indexOf("- **Standalone mode:** stop");
        int brainstormPlanSwitch = brainstorm.indexOf("switch to **camel-plan-mode**");
        assertTrue(brainstormStandalone >= 0 && brainstormPlanSwitch > brainstormStandalone);
        assertEquals(brainstormPlanSwitch, brainstorm.lastIndexOf("switch to **camel-plan-mode**"));
        assertEquals(1, brainstorm.split(".bob/skills/camel-plan/SKILL.md", -1).length - 1);
        assertEquals(1, migrate.split(".bob/skills/camel-plan/SKILL.md", -1).length - 1);
        assertFalse(brainstorm.contains("## Run Internal Verification"));
        assertFalse(migrate.contains("## Run Internal Verification"));
        assertFalse(brainstorm.contains("## Validate"));
        assertFalse(migrate.contains("## Validate"));
        int migrateApproval = migrate.indexOf("APPROVAL GATE");
        for (String phase : Set.of(
                "mulesoft-phase1.md", "mulesoft-phase2.md",
                "camel-version-phase1.md", "camel-version-phase2.md",
                "biztalk-phase1.md", "biztalk-phase2.md")) {
            assertTrue(migrate.indexOf(phase) >= 0 && migrate.indexOf(phase) < migrateApproval, phase);
        }
        assertTrue(migrate.indexOf("switch to **camel-plan-mode**") > migrateApproval);
        assertTrue(execute.contains("guides/environment-probe.md"));
        int executeVerification = execute.indexOf("## Run Verification");
        assertTrue(executeVerification >= 0
                && executeVerification < execute.indexOf("## Continue or Stop"));
        assertTrue(execute.contains("same-session adversarial pre-filter"));
        int adversarial = execute.indexOf("Adversarial Review (Bob 1 Same-Session Fallback)");
        int spec = execute.indexOf("Spec Compliance Review (Stage 1)");
        int quality = execute.indexOf("Code Quality Review (Stage 2)");
        assertTrue(adversarial >= 0 && adversarial < spec && spec < quality);
        assertTrue(execute.contains("docs/camel-kit/<PIPELINE_ID>/execution-report.md"));
        assertTrue(execute.contains("Verification Report:"));
        assertFalse(execute.contains("transition to \"camel-implement\""));
        assertTrue(execute.contains(".bob/skills/camel-implement/guides/orchestrator.md"));
        assertEquals(1, execute.split(".bob/skills/camel-validate/SKILL.md", -1).length - 1);
        assertFalse(execute.contains(".bob/skills/camel-implement/SKILL.md"));
        assertTrue(execute.contains("Bob 1 has no subagent"));
        assertFalse(execute.contains("Dispatch a subagent"));

        Set<String> slugs = modes.lines()
                .filter(line -> line.startsWith("  - slug: "))
                .map(line -> line.substring("  - slug: ".length()))
                .collect(Collectors.toSet());
        assertEquals(7, modes.lines().filter(line -> line.startsWith("  - slug: ")).count());
        assertEquals(Set.of("camel-brainstorm-mode", "camel-plan-mode", "camel-implement-mode",
                "camel-execute-mode", "camel-validate-mode", "camel-debug-mode", "camel-test-mode"), slugs);
        for (String slug : slugs) {
            assertFalse(Files.exists(ctx.commandsDir().resolve(slug + ".md")), slug);
        }
        String brainstormMode = modes.substring(
                modes.indexOf("slug: camel-brainstorm-mode"), modes.indexOf("slug: camel-plan-mode"));
        String planMode = modes.substring(
                modes.indexOf("slug: camel-plan-mode"), modes.indexOf("slug: camel-implement-mode"));
        String validateMode = modes.substring(
                modes.indexOf("slug: camel-validate-mode"), modes.indexOf("slug: camel-debug-mode"));
        String implementMode = modes.substring(
                modes.indexOf("slug: camel-implement-mode"), modes.indexOf("slug: camel-execute-mode"));
        String executeMode = modes.substring(
                modes.indexOf("slug: camel-execute-mode"), modes.indexOf("slug: camel-validate-mode"));
        String debugMode = modes.substring(
                modes.indexOf("slug: camel-debug-mode"), modes.indexOf("slug: camel-test-mode"));
        String testMode = modes.substring(modes.indexOf("slug: camel-test-mode"));
        assertTrue(brainstormMode.contains("\n      - command"));
        assertTrue(brainstormMode.contains("config\\.properties"));
        assertTrue(brainstormMode.contains("project-snapshot"));
        assertTrue(planMode.contains("\n      - command"));
        assertTrue(validateMode.contains("\n      - command"));
        assertEditAllows(brainstormMode,
                "docs/design.md", "./docs/design.md",
                ".camel-kit/project-snapshot.md", "./.camel-kit/project-snapshot.md");
        assertEditRejects(brainstormMode,
                "src/main/resources/routes/orders.camel.yaml", "README.md", ".camel-kit/secrets.txt");
        assertEditAllows(planMode, "docs/plan.md", "./docs/plan.md");
        assertEditRejects(planMode, "src/main/resources/routes/orders.camel.yaml", "pom.xml");
        assertEditAllows(validateMode,
                "docs/camel-kit/001/validation-report.md", "./docs/camel-kit/001/validation-report.md");
        assertEditRejects(validateMode,
                "docs/camel-kit/001/design-spec.md", "docs/camel-kit/001/execution-report.md",
                "src/main/resources/routes/orders.camel.yaml");
        for (String mode : Set.of(implementMode, executeMode, debugMode)) {
            assertTrue(mode.contains("\n      - read"));
            assertTrue(mode.contains("\n      - edit"));
            assertTrue(mode.contains("\n      - command"));
            assertTrue(mode.contains("\n      - mcp"));
        }
        assertTrue(testMode.contains("src/test/resources"));
        assertTrue(testMode.contains("(.*/)?"));
        assertTrue(testMode.contains("pom\\.xml"));
        assertEditAllows(testMode,
                "src/test/resources/orders.camel.it.yaml", "./src/test/resources/orders.camel.it.yaml",
                "module/src/test/resources/orders.camel.it.yaml", "./module/src/test/resources/orders.camel.it.yaml",
                "test/orders.camel.it.yaml", "./module/test/orders.camel.it.yaml",
                "pom.xml", "./pom.xml", "module/pom.xml", "./module/pom.xml",
                "docs/test-report.md", "./docs/test-report.md");
        assertEditRejects(testMode,
                "src/main/resources/routes/orders.camel.yaml", "README.md", "pom.xml.bak",
                "docs/camel-kit/001/validation-report.md");

        String validate = Files.readString(ctx.skillsDir().resolve("camel-validate/SKILL.md"));
        assertTrue(validate.contains("docs/camel-kit/<PIPELINE_ID>/design-spec.md"));
        assertTrue(validate.contains("execution-report.md"));
        assertFalse(validate.contains("verification-report.md"));
        assertTrue(validate.contains("continue to Generate Validation Report"));
        assertTrue(validate.contains("doc init --by camel-validate"));
        assertFalse(validate.contains("auto-invoke"));
    }

    private void assertEditAllows(String mode, String... paths) {
        Pattern pattern = editPattern(mode);
        for (String path : paths) {
            assertTrue(pattern.matcher(path).find(), path + " does not match " + pattern);
        }
    }

    @Test
    void installsCanonicalTestDesignPrinciplesForBobTestTasks() throws Exception {
        InitContext ctx = createContext();
        new BobGenerator().generate(ctx);

        Path guide = tempDir.resolve(".bob/skills/camel-test/guides/test-generation.md");
        assertTrue(Files.isRegularFile(guide));
        String guideContent = Files.readString(guide);
        assertTrue(guideContent.contains("## Test Design Principles"));
        for (String principle : List.of("One test = one behavior", "Realistic test data", "Infrastructure isolation",
                "Assertion completeness", "Negative testing", "Idempotent")) {
            assertTrue(guideContent.contains(principle), principle);
        }

        // Bob installs no persona resources, so the test task template must not point at agents/
        Path template = tempDir.resolve(".bob/skills/camel-plan/guides/task-template-testing.md");
        assertTrue(Files.isRegularFile(template));
        String templateContent = Files.readString(template);
        assertTrue(templateContent.contains("camel-test/guides/test-generation.md"));
        assertFalse(templateContent.contains("agents/"));
    }

    @Test
    void installsSharedSecurityChecklistForBobValidation() throws Exception {
        InitContext ctx = createContext();
        new BobGenerator().generate(ctx);

        Path checklist = tempDir.resolve(".bob/skills/shared/camel-security-checklist.md");
        assertTrue(Files.isRegularFile(checklist));
        String content = Files.readString(checklist);
        assertTrue(content.contains("| 1 | **No hardcoded credentials** |"));
        assertTrue(content.contains("camel.main.logMask=true"));

        // The Bob validate gate loads these guides; they must point at the installed shared checklist
        for (String guide : List.of("security-analysis.md", "anti-patterns.md", "quality-checks.md")) {
            Path installed = tempDir.resolve(".bob/skills/camel-validate/guides/" + guide);
            assertTrue(Files.isRegularFile(installed), guide);
            assertTrue(Files.readString(installed).contains("shared/camel-security-checklist.md"), guide);
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
        return Pattern.compile(expression);
    }
}
