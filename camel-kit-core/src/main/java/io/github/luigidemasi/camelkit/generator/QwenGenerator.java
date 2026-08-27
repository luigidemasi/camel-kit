package io.github.luigidemasi.camelkit.generator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import io.github.luigidemasi.camelkit.util.AnsiColors;

public class QwenGenerator extends DefaultGenerator {

    private static final String[] SUB_AGENTS = {
            "camel-implementer", "camel-reviewer", "camel-validator", "camel-tester"
    };
    private static final String[] RETIRED_SUB_AGENTS = {
            "camel-brainstormer", "camel-planner", "camel-migrator", "camel-executor"
    };

    private final QuteTemplateEngine templateEngine = new QuteTemplateEngine();
    private final PersonaResourceInstaller personaInstaller = new PersonaResourceInstaller();

    @Override
    public void generate(InitContext ctx) throws Exception {
        // Run default generation (commands, skills, MCP config)
        super.generate(ctx);

        Map<String, Object> data = new HashMap<>(
                Map.of(
                        "COMMAND_PREFIX", ctx.commandPrefix()));
        // Qwen-specific: generate QWEN.md at project root
        generateQwenMd(ctx, data);

        removeRetiredSubAgents(ctx);

        // Qwen-specific: generate bounded leaf definitions
        generateSubAgents(ctx);
        personaInstaller.install(ctx, ".qwen/camel-kit-personas");

        // Qwen-specific: generate .qwenignore
        generateQwenIgnore(ctx);

    }

    private void generateQwenMd(InitContext ctx, Map<String, Object> data) throws Exception {
        String content = templateEngine.render("templates/qwen/qwen-md.md", data);
        Files.writeString(ctx.projectDir().resolve("QWEN.md"), content);
    }

    private void generateSubAgents(InitContext ctx) throws Exception {
        Path agentsDir = ctx.projectDir().resolve(".qwen/agents");
        Files.createDirectories(agentsDir);

        for (String agentName : SUB_AGENTS) {
            copyTemplateResource(
                    "templates/qwen/agents/" + agentName + ".md",
                    agentsDir.resolve(agentName + ".md"));
        }

        ctx.printer()
                .println(AnsiColors.green("✓") + " Generated " + SUB_AGENTS.length + " Qwen sub-agent definitions");
    }

    private void removeRetiredSubAgents(InitContext ctx) throws Exception {
        Path agentsDir = ctx.projectDir().resolve(".qwen/agents");
        for (String agentName : RETIRED_SUB_AGENTS) {
            GeneratedAssetCleaner.deleteRegularFile(
                    ctx.projectDir(), agentsDir.resolve(agentName + ".md"));
        }
    }

    private void generateQwenIgnore(InitContext ctx) throws Exception {
        copyTemplateResource("templates/qwen/qwenignore",
                ctx.projectDir().resolve(".qwenignore"));
    }

}
