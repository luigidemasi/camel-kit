package io.github.luigidemasi.camelkit.generator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class Bob2Generator extends DefaultGenerator {

    private static final Map<String, String> RULE_MODE_FILES = Map.of(
            "camel-brainstorm", "brainstorm.md",
            "camel-plan", "plan.md",
            "camel-implement", "implement.md",
            "camel-execute", "execute.md",
            "camel-validate", "validate.md",
            "camel-test", "test.md",
            "camel-debug", "debug.md");

    @Override
    public void generate(InitContext ctx) throws Exception {
        GeneratedAssetCleaner.deleteRegularFile(
                ctx.projectDir(), ctx.projectDir().resolve(".bob/rules-camel-ship/ship.md"));
        super.generate(ctx);
        generateCustomModes(ctx);
        generateRules(ctx);
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
            Path modeRulesDir = ctx.projectDir().resolve(".bob/rules-" + rule.getKey());
            Files.createDirectories(modeRulesDir);
            copyTemplateResource(
                    "templates/bob2/rules-" + rule.getKey() + "/" + rule.getValue(),
                    modeRulesDir.resolve(rule.getValue()));
        }
    }

}
