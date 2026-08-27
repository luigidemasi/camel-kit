package io.github.luigidemasi.camelkit.generator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class Bob2Generator extends DefaultGenerator {

    private static final String[] PROJECT_AGENT_FILES = {
            "camel-worker.md", "camel-reviewer.md"
    };

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
        GeneratedAssetCleaner.deleteRegularFile(ctx, ctx.projectDir().resolve(".bob/rules-camel-ship/ship.md"));
        for (Map.Entry<String, String> rule : RULE_MODE_FILES.entrySet()) {
            GeneratedAssetCleaner.deleteRegularFile(
                    ctx, ctx.projectDir()
                            .resolve(".bob/rules-" + rule.getKey())
                            .resolve(rule.getValue()));
        }
        for (Map.Entry<String, String> rule : BOB1_RULE_MODE_FILES.entrySet()) {
            GeneratedAssetCleaner.deleteRegularFile(
                    ctx, ctx.projectDir()
                            .resolve(".bob/rules-" + rule.getKey())
                            .resolve(rule.getValue()));
            GeneratedAssetCleaner.deleteRegularFile(
                    ctx, ctx.projectDir()
                            .resolve(".bob/rules-" + rule.getKey() + "-mode")
                            .resolve(rule.getValue()));
        }
        super.generate(ctx);
        generateCustomModes(ctx);
        generateRules(ctx);
        generateProjectAgents(ctx);
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

}
