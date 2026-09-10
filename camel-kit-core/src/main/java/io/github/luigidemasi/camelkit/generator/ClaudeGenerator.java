package io.github.luigidemasi.camelkit.generator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import io.github.luigidemasi.camelkit.config.AgentDescriptor;
import io.github.luigidemasi.camelkit.config.AgentRegistry;
import io.github.luigidemasi.camelkit.util.AnsiColors;

public class ClaudeGenerator extends DefaultGenerator {

    private final QuteTemplateEngine templateEngine = new QuteTemplateEngine();

    @Override
    public void generate(InitContext ctx) throws Exception {
        super.generate(ctx);
        generateClaudeMd(ctx);
        generateSettings(ctx);
        generateSubagents(ctx);
    }

    /** Installs the registry-declared project subagents, currently the read-only Ship worker. */
    private void generateSubagents(InitContext ctx) throws Exception {
        int subagents = 0;
        for (AgentDescriptor.TemplateInstall template : AgentRegistry.descriptor(ctx.agentName()).templates()) {
            if (template.target().startsWith(".claude/agents/")) {
                copyTemplateResource(template.source(), ctx.projectDir().resolve(template.target()));
                subagents++;
            }
        }
        if (subagents > 0) {
            ctx.printer().println(AnsiColors.green("✓") + " Generated " + subagents + " Claude Code subagents");
        }
    }

    private void generateClaudeMd(InitContext ctx) throws Exception {
        Map<String, Object> data = new HashMap<>(
                Map.of(
                        "COMMAND_PREFIX", ctx.commandPrefix()));
        String content = templateEngine.render("templates/claude/claude-md.md", data);
        Files.writeString(ctx.projectDir().resolve("CLAUDE.md"), content);
    }

    private void generateSettings(InitContext ctx) throws Exception {
        Path settingsFile = ctx.projectDir().resolve(".claude/settings.json");
        Files.createDirectories(settingsFile.getParent());
        copyTemplateResource("templates/claude/settings.json", settingsFile);
        ctx.printer()
                .println(AnsiColors.green("✓") + " Generated .claude/settings.json with auto-approved permissions");
    }
}
