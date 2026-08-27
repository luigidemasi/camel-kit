package io.github.luigidemasi.camelkit.generator;

import java.util.HashMap;
import java.util.Map;

import io.github.luigidemasi.camelkit.config.AgentDescriptor;
import io.github.luigidemasi.camelkit.config.AgentRegistry;
import io.github.luigidemasi.camelkit.util.TemplateUtils;

public class BobGenerator extends DefaultGenerator {

    @Override
    protected void beforeApplyTraits(InitContext ctx) throws Exception {
        Map<String, Object> templateData = new HashMap<>(
                Map.of(
                        "COMMAND_PREFIX", ctx.commandPrefix()));
        GeneratedAssetCleaner.deleteRegularFile(ctx, ctx.projectDir().resolve(".bob/rules-camel-ship/ship.md"));
        BobGeneratedAssetCleaner.deleteLegacyModeRules(ctx);
        BobGeneratedAssetCleaner.deleteSiblingAssets(ctx, "bob2");
        generateRegisteredResources(ctx);

        // Bob-specific: replace SKILL.md files with monolithic gate versions
        replaceSkillsWithGates(ctx, templateData);
    }

    private void generateRegisteredResources(InitContext ctx) throws Exception {
        for (AgentDescriptor.TemplateInstall template : AgentRegistry.descriptor("bob").templates()) {
            if (!isGateTemplate(template) && !PersonaResourceInstaller.isPersonaTemplate(template)) {
                copyTemplateResource(template.source(), ctx.projectDir().resolve(template.target()));
            }
        }
    }

    private void replaceSkillsWithGates(InitContext ctx, Map<String, Object> data)
            throws Exception {
        QuteTemplateEngine qute = new QuteTemplateEngine();
        for (AgentDescriptor.TemplateInstall template : AgentRegistry.descriptor("bob").templates()) {
            if (!isGateTemplate(template)) {
                continue;
            }
            String gateTemplate = TemplateUtils.readTemplate(template.source());
            String gateContent = qute.renderString(gateTemplate, data);
            java.nio.file.Files.writeString(ctx.projectDir().resolve(template.target()), gateContent);
        }
    }

    private boolean isGateTemplate(AgentDescriptor.TemplateInstall template) {
        return template.source().startsWith("templates/bob/gates/");
    }
}
