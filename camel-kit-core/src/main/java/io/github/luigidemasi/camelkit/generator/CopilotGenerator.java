package io.github.luigidemasi.camelkit.generator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import io.github.luigidemasi.camelkit.util.AnsiColors;

public class CopilotGenerator extends DefaultGenerator {

    private static final String[] CUSTOM_AGENTS = {
            "camel-planner",
            "camel-implementer",
            "camel-tester",
            "camel-validator",
            "camel-migrator",
            "camel-catalog-researcher",
            "camel-security-reviewer"
    };

    private final QuteTemplateEngine templateEngine = new QuteTemplateEngine();

    @Override
    public void generate(InitContext ctx) throws Exception {
        super.generate(ctx);

        Map<String, Object> data = new HashMap<>(
                Map.of(
                        "COMMAND_PREFIX", ctx.commandPrefix()));
        generateCopilotInstructions(ctx, data);
        generateAgentsMd(ctx, data);
        generateCustomAgents(ctx);
        generateHooks(ctx);
    }

    private void generateCopilotInstructions(InitContext ctx, Map<String, Object> data) throws Exception {
        Path instructions = ctx.projectDir().resolve(".github/copilot-instructions.md");
        Files.createDirectories(instructions.getParent());
        String content = templateEngine.render("templates/copilot/copilot-instructions.md", data);
        Files.writeString(instructions, content);
    }

    private void generateAgentsMd(InitContext ctx, Map<String, Object> data) throws Exception {
        String content = templateEngine.render("templates/copilot/agents-md.md", data);
        Files.writeString(ctx.projectDir().resolve("AGENTS.md"), content);
    }

    private void generateCustomAgents(InitContext ctx) throws Exception {
        Path agentsDir = ctx.projectDir().resolve(".github/agents");
        Files.createDirectories(agentsDir);

        for (String agentName : CUSTOM_AGENTS) {
            copyTemplateResource(
                    "templates/copilot/agents/" + agentName + ".agent.md",
                    agentsDir.resolve(agentName + ".agent.md"));
        }

        ctx.printer()
                .println(AnsiColors.green("✓") + " Generated " + CUSTOM_AGENTS.length
                         + " Copilot custom agents");
    }

    private void generateHooks(InitContext ctx) throws Exception {
        Path hooksDir = ctx.projectDir().resolve(".github/hooks");
        Files.createDirectories(hooksDir);
        copyTemplateResource(
                "templates/copilot/hooks/camel-kit-safety.json",
                hooksDir.resolve("camel-kit-safety.json"));
        ctx.printer().println(AnsiColors.green("✓") + " Generated Copilot safety hooks");
    }
}
