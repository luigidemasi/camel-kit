package io.github.luigidemasi.camelkit.generator;

import java.net.JarURLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import io.github.luigidemasi.camelkit.config.AgentDescriptor;
import io.github.luigidemasi.camelkit.config.AgentRegistry;

/**
 * Installs Antigravity's native project skills, custom agents, and MCP configuration.
 */
public class AntigravityGenerator extends DefaultGenerator {

    @Override
    public void preflight(InitContext ctx) throws Exception {
        for (Path managed : List.of(ctx.projectDir().resolve("AGENTS.md"), ctx.projectDir().resolve(".agents"),
                ctx.projectDir().resolve("GEMINI.md"))) {
            if (Files.isSymbolicLink(managed)) {
                throw new InvalidAgentConfigurationException("Refusing to generate through symbolic link: " + managed);
            }
            if (Files.exists(managed)) {
                boolean directory = managed.getFileName().toString().equals(".agents");
                if (directory ? !Files.isDirectory(managed) : !Files.isRegularFile(managed)) {
                    throw new InvalidAgentConfigurationException(
                            "Antigravity requires " + managed + " to be a "
                                                                 + (directory ? "directory" : "regular file"));
                }
                try (var paths = Files.walk(managed)) {
                    Path link = paths.filter(Files::isSymbolicLink).findFirst().orElse(null);
                    if (link != null) {
                        throw new InvalidAgentConfigurationException(
                                "Refusing to generate through symbolic link: " + link);
                    }
                }
            }
        }
        requireType(ctx.skillsDir(), true);
        for (AgentDescriptor.TemplateInstall template : AgentRegistry.descriptor(ctx.agentName()).templates()) {
            Path target = ctx.projectDir().resolve(template.target());
            requireType(target, false);
            for (Path parent = target.getParent(); !parent.equals(ctx.projectDir()); parent = parent.getParent()) {
                requireType(parent, true);
            }
        }
        if (Files.isDirectory(ctx.skillsDir())) {
            try (var paths = Files.walk(ctx.skillsDir())) {
                for (Path target : paths.toList()) {
                    String relative = ctx.skillsDir().relativize(target).toString().replace('\\', '/');
                    if (!relative.isEmpty()) {
                        var loader = getClass().getClassLoader();
                        var resource = loader.getResource("skills/" + relative);
                        if (resource == null) {
                            resource = loader.getResource("skills/" + relative + "/");
                        }
                        if (resource != null) {
                            boolean directory = resource.openConnection() instanceof JarURLConnection jar
                                    ? jar.getJarEntry().isDirectory() : Files.isDirectory(Path.of(resource.toURI()));
                            requireType(target, directory);
                        }
                    }
                }
            }
        }
        McpConfigGenerator.readAntigravityConfig(ctx.projectDir().resolve(ctx.agent().mcpConfigPath()));
    }

    private void requireType(Path target, boolean directory) throws InvalidAgentConfigurationException {
        if (Files.exists(target) && (directory ? !Files.isDirectory(target) : !Files.isRegularFile(target))) {
            throw new InvalidAgentConfigurationException(
                    "Antigravity requires " + target + " to be a "
                                                         + (directory ? "directory" : "regular file"));
        }
    }

    @Override
    public void generate(InitContext ctx) throws Exception {
        preflight(ctx);
        super.generate(ctx);
        QuteTemplateEngine templates = new QuteTemplateEngine();
        for (AgentDescriptor.TemplateInstall template : AgentRegistry.descriptor(ctx.agentName()).templates()) {
            if (!PersonaResourceInstaller.isPersonaTemplate(template)) {
                Path target = ctx.projectDir().resolve(template.target());
                Files.createDirectories(target.getParent());
                Files.writeString(target, templates.render(template.source(),
                        Map.of("COMMAND_PREFIX", ctx.commandPrefix())));
            }
        }
        retireGeminiImports(ctx.projectDir());
    }

    private void retireGeminiImports(Path projectDir) throws Exception {
        // Antigravity also reads GEMINI.md. Retire only the old generated imports, preserving user rules.
        Path context = projectDir.resolve("GEMINI.md");
        if (Files.isRegularFile(context)) {
            String original = Files.readString(context);
            String migrated = original;
            for (String imported : List.of("skills/shared/context-authority.md", "instructions/iron-laws.md",
                    "instructions/mcp-usage.md", "instructions/pipeline-overview.md")) {
                migrated = migrated.replaceAll(
                        "(?m)^@\\.gemini/" + java.util.regex.Pattern.quote(imported) + "(?:\\R|$)",
                        "");
            }
            if (!original.equals(migrated)) {
                AtomicFileWriter.write(context, migrated);
            }
        }
    }
}
