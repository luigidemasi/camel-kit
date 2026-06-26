package io.github.luigidemasi.camelkit.generator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import io.github.luigidemasi.camelkit.util.AnsiColors;
import io.github.luigidemasi.camelkit.util.TemplateUtils;
import io.github.luigidemasi.camelkit.workflow.WorkflowManifest;

class TraitApplicator {

    private static final String APPEND_TRAIT_SUFFIX = ".append.md";

    void apply(InitContext ctx, WorkflowManifest workflow) throws Exception {
        String traitsBasePath = "templates/traits/" + ctx.agentName() + "/";
        int traitCount = 0;

        List<String> skillNames = workflow.skills().stream()
                .map(WorkflowManifest.WorkflowSkill::name)
                .toList();

        for (String skillName : skillNames) {
            String traitResourcePath = traitsBasePath + skillName + ".append.md";
            Path targetSkillMd = ctx.skillsDir().resolve(skillName + "/SKILL.md");
            if (appendTraitIfExists(traitResourcePath, targetSkillMd, ctx.agentName())) {
                traitCount++;
            }

            traitCount += applyGuideTraits(
                    traitsBasePath + skillName + "/",
                    ctx.skillsDir().resolve(skillName + "/guides/"),
                    ctx.agentName());
        }

        if (traitCount > 0) {
            ctx.printer().println(AnsiColors.green("✓") + " Applied " + traitCount
                                  + " agent traits for " + ctx.agentName());
        }
    }

    private int applyGuideTraits(String traitDirPath, Path guidesDir, String agentName) throws Exception {
        int count = 0;
        for (String traitFileName : TemplateUtils.listTemplateFiles(traitDirPath, APPEND_TRAIT_SUFFIX)) {
            String guideName = stripAppendSuffix(traitFileName);
            String traitResourcePath = traitDirPath + guideName + APPEND_TRAIT_SUFFIX;
            Path targetGuide = guidesDir.resolve(guideName + ".md");
            if (appendTraitIfExists(traitResourcePath, targetGuide, agentName)) {
                count++;
            }
        }
        return count;
    }

    private boolean appendTraitIfExists(String traitResourcePath, Path targetFile, String agentName) throws Exception {
        if (!Files.exists(targetFile)) {
            return false;
        }
        String traitContent;
        try {
            traitContent = TemplateUtils.readTemplate(traitResourcePath);
        } catch (IOException e) {
            return false;
        }

        String sentinel = "<!-- TRAIT:" + agentName + " -->";
        String existing = Files.readString(targetFile);
        if (existing.contains(sentinel)) {
            return false;
        }

        String closeSentinel = "<!-- /TRAIT:" + agentName + " -->";
        Files.writeString(targetFile,
                existing + "\n---\n\n" + sentinel + "\n" + traitContent + "\n" + closeSentinel + "\n");
        return true;
    }

    private static String stripAppendSuffix(String fileName) {
        return fileName.substring(0, fileName.length() - APPEND_TRAIT_SUFFIX.length());
    }
}
