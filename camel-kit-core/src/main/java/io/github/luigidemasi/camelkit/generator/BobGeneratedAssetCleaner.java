package io.github.luigidemasi.camelkit.generator;

import java.io.IOException;
import java.util.List;

import io.github.luigidemasi.camelkit.config.AgentDescriptor;
import io.github.luigidemasi.camelkit.config.AgentRegistry;

final class BobGeneratedAssetCleaner {

    private BobGeneratedAssetCleaner() {
    }

    static void deleteBob1Assets(InitContext ctx) throws IOException {
        // Retain upgrade cleanup after retiring the Bob 1 generator and descriptor.
        // Shared skills and custom_modes.yaml are replaced by Bob 2 generation.
        for (String target : List.of(
                ".bob/rules-camel-brainstorm-mode/interview-gates.md",
                ".bob/rules-camel-plan-mode/plan-structure.md",
                ".bob/rules-camel-implement-mode/implementation.md",
                ".bob/rules-camel-validate-mode/validation.md",
                ".bob/rules-camel-test-mode/testing.md")) {
            delete(ctx, target);
            delete(ctx, legacyModeRule(target));
        }
    }

    static void deleteLegacyModeRules(InitContext ctx) throws IOException {
        for (AgentDescriptor.TemplateInstall template : AgentRegistry.descriptor(ctx.agentName()).templates()) {
            if (isModeRule(template.target())) {
                delete(ctx, legacyModeRule(template.target()));
            }
        }
    }

    private static boolean isModeRule(String target) {
        return target.startsWith(".bob/rules-") && target.contains("-mode/");
    }

    private static String legacyModeRule(String target) {
        return target.replace("-mode/", "/");
    }

    private static void delete(InitContext ctx, String target) throws IOException {
        GeneratedAssetCleaner.deleteRegularFile(ctx, ctx.projectDir().resolve(target));
    }
}
