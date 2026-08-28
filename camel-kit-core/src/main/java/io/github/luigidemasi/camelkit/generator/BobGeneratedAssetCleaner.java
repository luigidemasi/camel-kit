package io.github.luigidemasi.camelkit.generator;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.luigidemasi.camelkit.config.AgentDescriptor;
import io.github.luigidemasi.camelkit.config.AgentRegistry;

final class BobGeneratedAssetCleaner {

    private BobGeneratedAssetCleaner() {
    }

    static void deleteSiblingAssets(InitContext ctx, String siblingAgent) throws IOException {
        AgentDescriptor current = AgentRegistry.descriptor(ctx.agentName());
        Set<String> currentTargets = current.templates().stream()
                .map(AgentDescriptor.TemplateInstall::target)
                .collect(Collectors.toSet());
        // Skill files live in the shared skills directory and are reinstalled by the skill installer,
        // so a sibling's gate SKILL.md targets are never retired assets of the current generation.
        String skillsPrefix = current.skillsDirectory() + "/";
        for (AgentDescriptor.TemplateInstall template : AgentRegistry.descriptor(siblingAgent).templates()) {
            String target = template.target();
            if (currentTargets.contains(target) || target.startsWith(skillsPrefix)) {
                continue;
            }
            delete(ctx, target);
            if (isModeRule(target)) {
                delete(ctx, legacyModeRule(target));
            }
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
