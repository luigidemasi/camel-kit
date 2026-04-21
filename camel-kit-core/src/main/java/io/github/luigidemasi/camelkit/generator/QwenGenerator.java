package io.github.luigidemasi.camelkit.generator;

import io.github.luigidemasi.camelkit.CamelKitMain;
import io.github.luigidemasi.camelkit.util.AnsiColors;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class QwenGenerator extends DefaultGenerator {

    private static final String[] SUB_AGENTS = {
        "camel-brainstormer", "camel-planner", "camel-implementer",
        "camel-validator", "camel-tester", "camel-migrator", "camel-executor"
    };

    private static final Map<String, String> COMMAND_DISPATCH = Map.of(
        "camel-brainstorm", "Delegate to the camel-brainstormer sub-agent: discover integration requirements and design Camel routes for this project",
        "camel-plan", "Delegate to the camel-planner sub-agent: create an implementation plan with TDD task decomposition for this project",
        "camel-implement", "Delegate to the camel-implementer sub-agent: implement the Camel routes in this project",
        "camel-validate", "Delegate to the camel-validator sub-agent: validate the Camel routes in this project",
        "camel-test", "Delegate to the camel-tester sub-agent: write and run tests for the Camel routes in this project",
        "camel-migrate", "Delegate to the camel-migrator sub-agent: migrate the integrations to Apache Camel",
        "camel-execute", "Delegate to the camel-executor sub-agent: execute the implementation plan for this project"
    );

    private final QuteTemplateEngine templateEngine = new QuteTemplateEngine();

    @Override
    public void generate(InitContext ctx) throws Exception {
        // Run default generation (commands, skills, MCP config)
        super.generate(ctx);

        Map<String, Object> data = new HashMap<>(Map.of(
            "commandPrefix", ctx.commandPrefix(),
            "camelVersion", ctx.camelVersion()
        ));
        // Qwen-specific: generate QWEN.md at project root
        generateQwenMd(ctx, data);

        // Qwen-specific: generate sub-agent definitions
        generateSubAgents(ctx);

        // Qwen-specific: generate .qwenignore
        generateQwenIgnore(ctx);

        // Qwen-specific: override commands with sub-agent dispatch
        overrideCommandsForSubAgents(ctx);
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

        ctx.printer().println(AnsiColors.green("✓") + " Generated " + SUB_AGENTS.length + " Qwen sub-agent definitions");
    }

    private void generateQwenIgnore(InitContext ctx) throws Exception {
        copyTemplateResource("templates/qwen/qwenignore",
            ctx.projectDir().resolve(".qwenignore"));
    }

    private void overrideCommandsForSubAgents(InitContext ctx) throws Exception {
        for (Map.Entry<String, String> entry : COMMAND_DISPATCH.entrySet()) {
            String filename = entry.getKey() + "." + ctx.agent().fileFormat();
            Path cmdFile = ctx.commandsDir().resolve(filename);
            Files.writeString(cmdFile, entry.getValue());
        }
    }
}
