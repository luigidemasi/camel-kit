package io.github.luigidemasi.camelkit.generator;

import io.github.luigidemasi.camelkit.config.AgentDescriptor;
import io.github.luigidemasi.camelkit.config.AgentRegistry;

public class Bob2Generator extends DefaultGenerator {

    @Override
    public void generate(InitContext ctx) throws Exception {
        GeneratedAssetCleaner.deleteRegularFile(ctx, ctx.projectDir().resolve(".bob/rules-camel-ship/ship.md"));
        BobGeneratedAssetCleaner.deleteLegacyModeRules(ctx);
        BobGeneratedAssetCleaner.deleteSiblingAssets(ctx, "bob");
        super.generate(ctx);
        generateRegisteredResources(ctx);
    }

    private void generateRegisteredResources(InitContext ctx) throws Exception {
        for (AgentDescriptor.TemplateInstall template : AgentRegistry.descriptor("bob2").templates()) {
            if (!PersonaResourceInstaller.isPersonaTemplate(template)) {
                copyTemplateResource(template.source(), ctx.projectDir().resolve(template.target()));
            }
        }
    }

}
