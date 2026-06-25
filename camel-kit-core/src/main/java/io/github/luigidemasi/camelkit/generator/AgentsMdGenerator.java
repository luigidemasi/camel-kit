package io.github.luigidemasi.camelkit.generator;

import java.nio.file.Files;
import java.util.Map;

import io.github.luigidemasi.camelkit.util.AnsiColors;

class AgentsMdGenerator {

    private final QuteTemplateEngine qute = new QuteTemplateEngine();

    void generate(InitContext ctx) throws Exception {
        Map<String, Object> data = new java.util.HashMap<>(
                Map.of(
                        "COMMAND_PREFIX", ctx.commandPrefix()));
        String content = qute.render("templates/shared/agents-md.md", data);
        Files.writeString(ctx.projectDir().resolve("AGENTS.md"), content);
        ctx.printer().println(AnsiColors.green("✓") + " Generated AGENTS.md with skill routing and iron laws");
    }
}
