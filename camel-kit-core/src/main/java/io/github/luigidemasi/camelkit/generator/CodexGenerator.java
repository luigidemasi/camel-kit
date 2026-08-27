package io.github.luigidemasi.camelkit.generator;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import io.github.luigidemasi.camelkit.config.AgentDescriptor;
import io.github.luigidemasi.camelkit.config.AgentRegistry;
import io.github.luigidemasi.camelkit.util.AnsiColors;

/**
 * Generates repository-scoped OpenAI Codex assets.
 */
public class CodexGenerator extends DefaultGenerator {

    private static final String AGENTS_TEMPLATE = "templates/codex/agents-md.md";

    private final QuteTemplateEngine templateEngine = new QuteTemplateEngine();

    @Override
    public void generate(InitContext ctx) throws Exception {
        rejectSymlinkedManagedPaths(ctx.projectDir());
        super.generate(ctx);
        installDescriptorTemplates(ctx);
    }

    private void rejectSymlinkedManagedPaths(Path projectDir) throws Exception {
        for (Path managedPath : List.of(
                projectDir.resolve("AGENTS.md"),
                projectDir.resolve(".agents"),
                projectDir.resolve(".codex"))) {
            if (!Files.exists(managedPath, LinkOption.NOFOLLOW_LINKS)) {
                continue;
            }
            try (var paths = Files.walk(managedPath)) {
                Path symlink = paths.filter(Files::isSymbolicLink).findFirst().orElse(null);
                if (symlink != null) {
                    throw new IllegalStateException(
                            "Refusing to generate Codex assets through symbolic link: "
                                                    + projectDir.relativize(symlink));
                }
            }
        }
    }

    private void installDescriptorTemplates(InitContext ctx) throws Exception {
        AgentDescriptor descriptor = AgentRegistry.descriptor(ctx.agentName());
        if (descriptor == null) {
            throw new IllegalArgumentException("Unsupported agent: " + ctx.agentName());
        }

        int customAgents = 0;
        for (AgentDescriptor.TemplateInstall template : descriptor.templates()) {
            if (PersonaResourceInstaller.isPersonaTemplate(template)) {
                continue;
            }
            Path target = ctx.projectDir().resolve(template.target());
            if (AGENTS_TEMPLATE.equals(template.source())) {
                Files.writeString(target, templateEngine.render(
                        template.source(), Map.of("COMMAND_PREFIX", ctx.commandPrefix())));
            } else {
                copyTemplateResource(template.source(), target);
            }
            if (template.target().startsWith(".codex/agents/") && template.target().endsWith(".toml")) {
                customAgents++;
            }
        }

        ctx.printer().println(AnsiColors.green("✓") + " Generated " + customAgents + " Codex custom agents");
    }
}
