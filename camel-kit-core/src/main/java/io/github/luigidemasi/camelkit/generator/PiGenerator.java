package io.github.luigidemasi.camelkit.generator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import io.github.luigidemasi.camelkit.config.AgentDescriptor;
import io.github.luigidemasi.camelkit.config.AgentRegistry;
import io.github.luigidemasi.camelkit.util.AnsiColors;

public class PiGenerator extends DefaultGenerator {

    private static final Set<String> RENDERED_TEMPLATES = Set.of(
            "templates/pi/agents-md.md");

    private final QuteTemplateEngine templateEngine = new QuteTemplateEngine();

    @Override
    public void generate(InitContext ctx) throws Exception {
        super.generate(ctx);

        Map<String, Object> data = new HashMap<>(
                Map.of(
                        "COMMAND_PREFIX", ctx.commandPrefix(),
                        "PI_VERSION", ctx.distribution().piVersion(),
                        "PI_MCP_ADAPTER_VERSION", ctx.distribution().piMcpAdapterVersion()));
        installDescriptorTemplates(ctx, data);
    }

    private void installDescriptorTemplates(InitContext ctx, Map<String, Object> data) throws Exception {
        AgentDescriptor descriptor = AgentRegistry.descriptor(ctx.agentName());
        if (descriptor == null) {
            throw new IllegalArgumentException("Unsupported agent: " + ctx.agentName());
        }

        boolean generatedGuard = false;
        for (AgentDescriptor.TemplateInstall template : descriptor.templates()) {
            if (PersonaResourceInstaller.isPersonaTemplate(template)) {
                continue;
            }
            Path target = ctx.projectDir().resolve(template.target());
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            if (RENDERED_TEMPLATES.contains(template.source())) {
                Files.writeString(target, templateEngine.render(template.source(), data));
            } else {
                copyTemplateResource(template.source(), target);
            }
            if (template.target().startsWith(".pi/extensions/")) {
                generatedGuard = true;
            }
        }

        if (generatedGuard) {
            ctx.printer().println(AnsiColors.green("✓") + " Generated Pi safety guard extension");
        }
    }
}
