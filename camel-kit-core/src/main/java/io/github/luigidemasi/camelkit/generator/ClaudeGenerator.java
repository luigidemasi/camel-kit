package io.github.luigidemasi.camelkit.generator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import io.github.luigidemasi.camelkit.util.AnsiColors;

public class ClaudeGenerator extends DefaultGenerator {

    private final QuteTemplateEngine templateEngine = new QuteTemplateEngine();

    @Override
    public void generate(InitContext ctx) throws Exception {
        // Run all default generation (commands, skills, MCP config)
        super.generate(ctx);

        // Claude-specific: generate CLAUDE.md at project root
        generateClaudeMd(ctx);

        // Claude-specific: append parallel dispatch to camel-implement skill
        appendParallelDispatch(ctx);

        // Claude-specific: generate .claude/settings.json with permissions
        generateSettings(ctx);
    }

    private void generateClaudeMd(InitContext ctx) throws Exception {
        Map<String, Object> data = new HashMap<>(
                Map.of(
                        "COMMAND_PREFIX", ctx.commandPrefix()));
        String content = templateEngine.render("templates/claude/claude-md.md", data);
        Files.writeString(ctx.projectDir().resolve("CLAUDE.md"), content);
    }

    private void appendParallelDispatch(InitContext ctx) throws Exception {
        Path implementSkill = ctx.skillsDir().resolve("camel-implement/SKILL.md");
        if (Files.exists(implementSkill)) {
            Map<String, Object> data = Map.of("COMMAND_PREFIX", ctx.commandPrefix());
            String parallelBlock = templateEngine.render(
                    "templates/claude/dispatch-parallel.md", data);
            String existing = Files.readString(implementSkill);
            Files.writeString(implementSkill, existing + "\n---\n\n" + parallelBlock);
        }
    }

    private void generateSettings(InitContext ctx) throws Exception {
        Path settingsFile = ctx.projectDir().resolve(".claude/settings.json");
        Files.createDirectories(settingsFile.getParent());
        copyTemplateResource("templates/claude/settings.json", settingsFile);
        ctx.printer()
                .println(AnsiColors.green("✓") + " Generated .claude/settings.json with auto-approved permissions");
    }
}
