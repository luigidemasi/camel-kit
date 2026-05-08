package io.github.luigidemasi.camelkit.generator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import io.github.luigidemasi.camelkit.util.TemplateUtils;

public class BobGenerator extends DefaultGenerator {

    private static final String[] SKILLS_WITH_GATES = {
            "camel-migrate", "camel-brainstorm", "camel-implement",
            "camel-validate", "camel-test", "camel-plan", "camel-execute"
    };

    private static final String[] RULE_MODES = {
            "camel-brainstorm", "camel-plan", "camel-implement",
            "camel-validate", "camel-test"
    };

    private static final Map<String, String> RULE_MODE_FILES = Map.of(
            "camel-brainstorm", "interview-gates.md",
            "camel-plan", "plan-structure.md",
            "camel-implement", "implementation.md",
            "camel-validate", "validation.md",
            "camel-test", "testing.md");

    @Override
    public void generate(InitContext ctx) throws Exception {
        // Run default generation (commands, skills, MCP config)
        super.generate(ctx);

        Map<String, Object> templateData = new HashMap<>(
                Map.of(
                        "COMMAND_PREFIX", ctx.commandPrefix()));
        // Bob-specific: generate custom modes
        generateCustomModes(ctx);

        // Bob-specific: generate custom rules
        generateRules(ctx);

        // Bob-specific: replace SKILL.md files with monolithic gate versions
        replaceSkillsWithGates(ctx, templateData);
    }

    private void generateCustomModes(InitContext ctx) throws Exception {
        Path modesFile = ctx.projectDir().resolve(".bob/custom_modes.yaml");
        Files.createDirectories(modesFile.getParent());
        copyTemplateResource("templates/bob/custom_modes.yaml", modesFile);
    }

    private void generateRules(InitContext ctx) throws Exception {
        Path sharedRulesDir = ctx.projectDir().resolve(".bob/rules");
        Files.createDirectories(sharedRulesDir);

        copyTemplateResource("templates/bob/rules/iron-laws.md",
                sharedRulesDir.resolve("iron-laws.md"));

        // Mode-specific rules
        for (String mode : RULE_MODES) {
            Path modeRulesDir = ctx.projectDir().resolve(".bob/rules-" + mode);
            Files.createDirectories(modeRulesDir);
            String ruleFile = RULE_MODE_FILES.get(mode);
            copyTemplateResource(
                    "templates/bob/rules-" + mode + "/" + ruleFile,
                    modeRulesDir.resolve(ruleFile));
        }
    }

    private void replaceSkillsWithGates(InitContext ctx, Map<String, Object> data)
            throws Exception {
        QuteTemplateEngine qute = new QuteTemplateEngine();
        for (String skillName : SKILLS_WITH_GATES) {
            String gatePath = "templates/bob/gates/" + skillName + ".md";
            Path skillMd = ctx.skillsDir().resolve(skillName + "/SKILL.md");
            if (!Files.exists(skillMd)) {
                continue;
            }

            String gateTemplate = TemplateUtils.readTemplateOrNull(gatePath);
            if (gateTemplate == null) {
                continue;
            }

            String gateContent = qute.renderString(gateTemplate, data);
            Files.writeString(skillMd, gateContent);
        }
    }
}
