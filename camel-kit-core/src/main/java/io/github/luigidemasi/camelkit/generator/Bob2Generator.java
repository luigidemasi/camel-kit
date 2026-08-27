package io.github.luigidemasi.camelkit.generator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class Bob2Generator extends DefaultGenerator {

    private static final String[] PROJECT_AGENT_FILES = {
            "camel-worker.md", "camel-reviewer.md"
    };

    private static final List<String> PERSONA_FILES = List.of(
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

    private static final List<String> PERSONA_REFERENCE_SKILL_FILES = List.of(
            "camel-execute/SKILL.md",
            "camel-execute/guides/adversarial-code-review.md",
            "camel-execute/guides/implementer-context.md",
            "camel-execute/guides/quality-reviewer-criteria.md",
            "camel-execute/guides/spec-reviewer-criteria.md",
            "camel-knowledge/SKILL.md",
            "camel-plan/guides/task-decomposition.md");

    private static final Map<String, String> RULE_MODE_FILES = Map.of(
            "camel-brainstorm", "brainstorm.md",
            "camel-plan", "plan.md",
            "camel-implement", "implement.md",
            "camel-execute", "execute.md",
            "camel-validate", "validate.md",
            "camel-test", "test.md",
            "camel-debug", "debug.md");

    private static final Map<String, String> BOB1_RULE_MODE_FILES = Map.of(
            "camel-brainstorm", "interview-gates.md",
            "camel-plan", "plan-structure.md",
            "camel-implement", "implementation.md",
            "camel-validate", "validation.md",
            "camel-test", "testing.md");

    @Override
    public void generate(InitContext ctx) throws Exception {
        GeneratedAssetCleaner.deleteRegularFile(
                ctx.projectDir(), ctx.projectDir().resolve(".bob/rules-camel-ship/ship.md"));
        for (Map.Entry<String, String> rule : RULE_MODE_FILES.entrySet()) {
            GeneratedAssetCleaner.deleteRegularFile(
                    ctx.projectDir(), ctx.projectDir()
                            .resolve(".bob/rules-" + rule.getKey())
                            .resolve(rule.getValue()));
        }
        for (Map.Entry<String, String> rule : BOB1_RULE_MODE_FILES.entrySet()) {
            GeneratedAssetCleaner.deleteRegularFile(
                    ctx.projectDir(), ctx.projectDir()
                            .resolve(".bob/rules-" + rule.getKey())
                            .resolve(rule.getValue()));
            GeneratedAssetCleaner.deleteRegularFile(
                    ctx.projectDir(), ctx.projectDir()
                            .resolve(".bob/rules-" + rule.getKey() + "-mode")
                            .resolve(rule.getValue()));
        }
        super.generate(ctx);
        generateCustomModes(ctx);
        generateRules(ctx);
        generateProjectAgents(ctx);
        generatePersonas(ctx);
        useGeneratedPersonaPaths(ctx);
    }

    private void generateCustomModes(InitContext ctx) throws Exception {
        Path modesFile = ctx.projectDir().resolve(".bob/custom_modes.yaml");
        Files.createDirectories(modesFile.getParent());
        copyTemplateResource("templates/bob2/custom_modes.yaml", modesFile);
    }

    private void generateRules(InitContext ctx) throws Exception {
        Path sharedRulesDir = ctx.projectDir().resolve(".bob/rules");
        Files.createDirectories(sharedRulesDir);
        copyTemplateResource("templates/bob2/rules/iron-laws.md", sharedRulesDir.resolve("iron-laws.md"));

        for (Map.Entry<String, String> rule : RULE_MODE_FILES.entrySet()) {
            Path modeRulesDir = ctx.projectDir().resolve(".bob/rules-" + rule.getKey() + "-mode");
            Files.createDirectories(modeRulesDir);
            copyTemplateResource(
                    "templates/bob2/rules-" + rule.getKey() + "/" + rule.getValue(),
                    modeRulesDir.resolve(rule.getValue()));
        }
    }

    private void generateProjectAgents(InitContext ctx) throws Exception {
        Path agentsDir = ctx.projectDir().resolve(".bob/agents");
        Files.createDirectories(agentsDir);
        for (String agentFile : PROJECT_AGENT_FILES) {
            copyTemplateResource("templates/bob2/agents/" + agentFile, agentsDir.resolve(agentFile));
        }
    }

    private void generatePersonas(InitContext ctx) throws Exception {
        Path personasDir = ctx.projectDir().resolve(".bob/personas");
        Files.createDirectories(personasDir);
        for (String personaFile : PERSONA_FILES) {
            copyTemplateResource("agents/" + personaFile, personasDir.resolve(personaFile));
        }
    }

    private void useGeneratedPersonaPaths(InitContext ctx) throws Exception {
        for (String relativePath : PERSONA_REFERENCE_SKILL_FILES) {
            rewritePersonaReferences(ctx.skillsDir().resolve(relativePath));
        }
        Path personasDir = ctx.projectDir().resolve(".bob/personas");
        for (String personaFile : PERSONA_FILES) {
            rewritePersonaReferences(personasDir.resolve(personaFile));
        }
    }

    private void rewritePersonaReferences(Path markdown) throws Exception {
        String content = Files.readString(markdown);
        String updated = content;
        for (String personaFile : PERSONA_FILES) {
            updated = updated.replace(
                    "agents/" + personaFile, ".bob/personas/" + personaFile);
        }
        updated = updated
                .replace("agents/[persona].md", ".bob/personas/[persona].md")
                .replace("agents/critic-<lane>.md", ".bob/personas/critic-<lane>.md")
                .replace("`agents/`", "`.bob/personas/`");
        if (!updated.equals(content)) {
            Files.writeString(markdown, updated);
        }
    }

}
