package io.github.luigidemasi.camelkit.generator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import io.github.luigidemasi.camelkit.util.AnsiColors;

public class GeminiGenerator extends DefaultGenerator {

    private static final String[] SUB_AGENTS = {
            "camel-brainstormer", "camel-planner", "camel-implementer",
            "camel-validator", "camel-tester", "camel-migrator"
    };

    private static final String[] INSTRUCTIONS = {
            "iron-laws", "mcp-usage", "pipeline-overview"
    };

    private static final Map<String, String[]> COMMAND_DISPATCH = Map.of(
            "camel-brainstorm",
            new String[]{
                    "Discover integration requirements",
                    "Delegate this task to the camel-brainstormer subagent.\nRead .gemini/skills/camel-brainstorm/SKILL.md and follow those instructions."},
            "camel-plan",
            new String[]{
                    "Create implementation plan",
                    "Delegate this task to the camel-planner subagent.\nRead .gemini/skills/camel-plan/SKILL.md and follow those instructions."},
            "camel-validate",
            new String[]{
                    "Validate Camel routes against quality rules",
                    "Read .gemini/skills/camel-validate/SKILL.md. Delegate its analysis to the read-only camel-validator "
                                                                   + "subagent, then write the returned report content to the path required by the skill."},
            "camel-migrate",
            new String[]{
                    "Migrate integrations to Apache Camel",
                    "Delegate this task to the camel-migrator subagent.\nRead .gemini/skills/camel-migrate/SKILL.md and follow those instructions."},
            "camel-start",
            new String[]{
                    "Route to the right camel-kit skill for integration work",
                    "Read .gemini/skills/camel-start/SKILL.md and follow those instructions."},
            "camel-execute",
            new String[]{
                    "Execute implementation plan by dispatching to specialized subagents",
                    "You are the orchestrator. Execute the implementation plan by dispatching tasks to the appropriate subagents.\nRead .gemini/skills/camel-execute/SKILL.md and follow those instructions."});

    private final QuteTemplateEngine templateEngine = new QuteTemplateEngine();

    @Override
    public void generate(InitContext ctx) throws Exception {
        // Run default generation (commands, skills, MCP config)
        super.generate(ctx);

        Map<String, Object> data = new HashMap<>(
                Map.of(
                        "COMMAND_PREFIX", ctx.commandPrefix()));
        // Gemini-specific: generate GEMINI.md at project root
        generateGeminiMd(ctx);

        // Gemini-specific: generate instruction files
        generateInstructions(ctx, data);

        // Gemini-specific: generate policy file
        generatePolicy(ctx);

        // Gemini-specific: generate subagent definitions (6, not 7 — no executor)
        generateSubAgents(ctx);

        // Gemini-specific: generate .geminiignore
        generateGeminiIgnore(ctx);

        // Gemini-specific: override commands with TOML subagent dispatch
        overrideCommandsForSubAgents(ctx);
    }

    private void generateGeminiMd(InitContext ctx) throws Exception {
        copyTemplateResource("templates/gemini/gemini-md.md",
                ctx.projectDir().resolve("GEMINI.md"));
    }

    private void generateInstructions(InitContext ctx, Map<String, Object> data) throws Exception {
        Path instructionsDir = ctx.projectDir().resolve(".gemini/instructions");
        Files.createDirectories(instructionsDir);

        for (String name : INSTRUCTIONS) {
            String content = templateEngine.render(
                    "templates/gemini/instructions/" + name + ".md", data);
            Files.writeString(instructionsDir.resolve(name + ".md"), content);
        }
    }

    private void generatePolicy(InitContext ctx) throws Exception {
        Path policiesDir = ctx.projectDir().resolve(".gemini/policies");
        Files.createDirectories(policiesDir);
        copyTemplateResource("templates/gemini/policies/camel-kit.toml",
                policiesDir.resolve("camel-kit.toml"));
    }

    private void generateSubAgents(InitContext ctx) throws Exception {
        Path agentsDir = ctx.projectDir().resolve(".gemini/agents");
        Files.createDirectories(agentsDir);

        for (String agentName : SUB_AGENTS) {
            copyTemplateResource(
                    "templates/gemini/agents/" + agentName + ".md",
                    agentsDir.resolve(agentName + ".md"));
        }

        ctx.printer()
                .println(AnsiColors.green("✓") + " Generated " + SUB_AGENTS.length + " Gemini subagent definitions");
    }

    private void generateGeminiIgnore(InitContext ctx) throws Exception {
        copyTemplateResource("templates/gemini/geminiignore",
                ctx.projectDir().resolve(".geminiignore"));
    }

    private void overrideCommandsForSubAgents(InitContext ctx) throws Exception {
        String argPlaceholder = ctx.agent().argPlaceholder();
        for (Map.Entry<String, String[]> entry : COMMAND_DISPATCH.entrySet()) {
            String filename = entry.getKey() + "." + ctx.agent().fileFormat();
            Path cmdFile = ctx.commandsDir().resolve(filename);
            String description = entry.getValue()[0];
            String prompt = entry.getValue()[1];
            String toml = "description = \"" + description + "\"\n\n"
                          + "prompt = \"\"\"\n"
                          + prompt + "\n"
                          + argPlaceholder + "\n"
                          + "\"\"\"";
            Files.writeString(cmdFile, toml);
        }
    }
}
