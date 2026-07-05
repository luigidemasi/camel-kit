package io.github.luigidemasi.camelkit.generator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import io.github.luigidemasi.camelkit.config.AgentDescriptor;
import io.github.luigidemasi.camelkit.config.AgentRegistry;
import io.github.luigidemasi.camelkit.util.AnsiColors;

public class CopilotGenerator extends DefaultGenerator {

    private final QuteTemplateEngine templateEngine = new QuteTemplateEngine();

    @Override
    public void generate(InitContext ctx) throws Exception {
        super.generate(ctx);

        Map<String, Object> data = new HashMap<>(
                Map.of(
                        "COMMAND_PREFIX", ctx.commandPrefix()));
        installDescriptorTemplates(ctx, data);
    }

    private void installDescriptorTemplates(InitContext ctx, Map<String, Object> data) throws Exception {
        AgentDescriptor descriptor = AgentRegistry.descriptor(ctx.agentName());
        if (descriptor == null) {
            throw new IllegalArgumentException("Unsupported agent: " + ctx.agentName());
        }

        long customAgents = 0;
        boolean generatedHooks = false;
        for (AgentDescriptor.TemplateInstall template : descriptor.templates()) {
            Path target = ctx.projectDir().resolve(template.target());
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            if (template.source().endsWith(".md")) {
                Files.writeString(target, templateEngine.render(template.source(), data));
            } else {
                copyTemplateResource(template.source(), target);
            }
            if (template.target().startsWith(".github/agents/") && template.target().endsWith(".agent.md")) {
                customAgents++;
            } else if (template.target().startsWith(".github/hooks/")) {
                generatedHooks = true;
            }
        }

        if (customAgents > 0) {
            ctx.printer()
                    .println(AnsiColors.green("✓") + " Generated " + customAgents + " Copilot custom agents");
        }
        if (generatedHooks) {
            ctx.printer().println(AnsiColors.green("✓") + " Generated Copilot safety hooks");
        }
    }
}
