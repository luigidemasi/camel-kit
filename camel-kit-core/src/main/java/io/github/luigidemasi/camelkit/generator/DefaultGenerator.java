package io.github.luigidemasi.camelkit.generator;

import io.github.luigidemasi.camelkit.CamelKitMain;
import io.github.luigidemasi.camelkit.util.AnsiColors;
import io.github.luigidemasi.camelkit.util.TemplateUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class DefaultGenerator implements AgentGenerator {
    @Override
    public void generate(InitContext ctx) throws Exception {
        // Ensure directories exist
        Files.createDirectories(ctx.commandsDir());
        Files.createDirectories(ctx.skillsDir());

        // 1. Create command templates
        createCommandTemplates(ctx);

        // 2. Copy skills with dispatch blocks
        copySkills(ctx);

        // 3. Generate Qwen agents if needed
        if ("qwen".equals(ctx.agentName())) {
            generateQwenAgents(ctx);
        }

        // 4. Create MCP configs
        createMcpConfigs(ctx);
    }

    private void createCommandTemplates(InitContext ctx) throws Exception {
        List<String> commands = List.of("project", "flow", "implement", "validate", "test", "migrate", "knowledge");

        // Extract agent base folder (e.g., ".bob" from ".bob/commands")
        String agentBaseFolder = ctx.agent().folder().substring(0, ctx.agent().folder().lastIndexOf("/"));

        for (String cmd : commands) {
            String skillName = "camel-" + cmd;

            // Create reference to skill file
            String content = "Read " + agentBaseFolder + "/skills/" + skillName + "/SKILL.md and follow those instructions";

            // Wrap in TOML format if needed
            if ("toml".equals(ctx.agent().fileFormat())) {
                content = wrapInToml(cmd, content);
            }

            String filename = skillName + "." + ctx.agent().fileFormat();
            Files.writeString(ctx.commandsDir().resolve(filename), content);
        }

        ctx.printer().println(AnsiColors.green("✓") + " Created " + commands.size() + " skill reference commands");
    }

    private String wrapInToml(String cmd, String content) {
        // Escape triple quotes for TOML
        String escaped = content.replace("\"\"\"", "\\\"\\\"\\\"");
        return """
            description = "Camel-Kit %s command"

            prompt = \"\"\"
            %s
            \"\"\"
            """.formatted(cmd, escaped);
    }

    private void copySkills(InitContext ctx) throws Exception {
        Files.createDirectories(ctx.skillsDir());

        // Get the skills directory from resources
        var skillsResource = getClass().getClassLoader().getResource("skills");
        if (skillsResource == null) {
            ctx.printer().println(AnsiColors.yellow("  Warning: Skills not found in resources"));
            return;
        }

        URI uri = skillsResource.toURI();
        Path skillsSourceDir;
        FileSystem fileSystem = null;

        try {
            // Check if we're reading from a JAR
            if (uri.getScheme().equals("jar")) {
                // Create filesystem for JAR
                try {
                    fileSystem = FileSystems.newFileSystem(uri, Collections.emptyMap());
                } catch (Exception e) {
                    // FileSystem might already exist
                    fileSystem = FileSystems.getFileSystem(uri);
                }
                skillsSourceDir = fileSystem.getPath("/skills");
            } else {
                // Regular file system path
                skillsSourceDir = Path.of(uri);
            }

            // Copy all skills from resources to target directory
            int filesCopied = 0;
            int dirsCopied = 0;
            try (var stream = Files.walk(skillsSourceDir)) {
                var paths = stream.toList();
                for (Path source : paths) {
                    // Convert paths to strings to avoid ProviderMismatchException
                    String relativePathStr = skillsSourceDir.relativize(source).toString();

                    // Skip the root directory itself
                    if (relativePathStr.isEmpty()) {
                        continue;
                    }

                    // Resolve using string to create a proper filesystem path
                    Path destination = ctx.skillsDir().resolve(relativePathStr);

                    try {
                        // Check attributes to determine if directory (works across filesystems)
                        boolean isDir = Files.isDirectory(source);

                        if (isDir) {
                            Files.createDirectories(destination);
                            dirsCopied++;
                        } else {
                            // Ensure parent directory exists
                            Files.createDirectories(destination.getParent());

                            // Use InputStream for cross-filesystem copy (JAR to filesystem)
                            try (InputStream in = Files.newInputStream(source)) {
                                Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
                                filesCopied++;
                            }
                            // Append platform-specific dispatch block to SKILL.md
                            if (destination.getFileName().toString().equals("SKILL.md")) {
                                appendDispatchBlock(destination, ctx.agentName());
                            }
                        }
                    } catch (Exception e) {
                        // Silent skip - this is expected for some paths
                    }
                }
            }

            // Count copied skills (directories only)
            int skillCount = (int) Files.list(ctx.skillsDir())
                .filter(Files::isDirectory)
                .count();

            if (filesCopied > 0) {
                ctx.printer().println(AnsiColors.green("✓") + " Copied " + filesCopied + " files in " + skillCount + " skill folders");
            } else {
                ctx.printer().println(AnsiColors.yellow("  No skills copied (this is normal - skills are embedded in command files)"));
            }

        } finally {
            // Close the JAR filesystem if we created it
            if (fileSystem != null && uri.getScheme().equals("jar")) {
                try {
                    fileSystem.close();
                } catch (Exception e) {
                    // Ignore close errors
                }
            }
        }
    }

    /**
     * Append the platform-specific dispatch block to a SKILL.md file.
     * Reads the dispatch template for the selected agent and appends it.
     */
    private void appendDispatchBlock(Path skillMdFile, String agentName) throws Exception {
        String dispatchTemplatePath = "templates/dispatch/" + agentName + ".md";
        try {
            String dispatchBlock = TemplateUtils.readTemplate(dispatchTemplatePath);
            String existing = Files.readString(skillMdFile);
            Files.writeString(skillMdFile, existing + "\n---\n\n" + dispatchBlock);
        } catch (IOException e) {
            // Dispatch template not found — skill works without it (fallback to monolithic mode)
        }
    }

    /**
     * For Qwen Code: generate pre-registered sub-agent definitions in .qwen/agents/.
     * Each agent inlines a specific guide file as its system prompt.
     */
    private void generateQwenAgents(InitContext ctx) throws Exception {
        Path agentsDir = ctx.projectDir().resolve(".qwen/agents");
        Files.createDirectories(agentsDir);

        int agentCount = 0;
        if (!Files.exists(ctx.skillsDir())) {
            return;
        }

        try (var stream = Files.walk(ctx.skillsDir())) {
            var guidePaths = stream
                .filter(p -> p.toString().contains("/guides/"))
                .filter(p -> p.toString().endsWith(".md"))
                .toList();

            for (Path guidePath : guidePaths) {
                String guideContent = Files.readString(guidePath);
                String guideName = guidePath.getFileName().toString().replace(".md", "");
                String agentName = "camel-" + guideName;
                String description = "Camel Kit sub-agent for " + guideName.replace("-", " ");

                // Use string concatenation to avoid IllegalFormatException
                // if guideContent contains % characters
                String agentDef = "---\n"
                    + "name: " + agentName + "\n"
                    + "description: " + description + "\n"
                    + "tools:\n"
                    + "  - read_file\n"
                    + "  - write_file\n"
                    + "  - edit\n"
                    + "  - read_many_files\n"
                    + "  - run_shell_command\n"
                    + "  - glob\n"
                    + "  - grep\n"
                    + "---\n\n"
                    + guideContent;

                Files.writeString(agentsDir.resolve(agentName + ".md"), agentDef);
                agentCount++;
            }
        }

        if (agentCount > 0) {
            ctx.printer().println(AnsiColors.green("✓") + " Generated " + agentCount + " Qwen sub-agent definitions");
        }
    }

    private void createMcpConfigs(InitContext ctx) throws Exception {
        String agentName = "";

        // Always extract the Camel MCP runner JAR — it's started with java -jar,
        // avoiding JBang's classloader and repo resolution issues entirely.
        Path mcpDir = ctx.projectDir().resolve(".camel-kit/mcp");
        Files.createDirectories(mcpDir);
        extractRunnerJar(ctx, mcpDir);

        // Knowledge server repos (still uses JBang for now)
        String knowledgeMcpRepos = CamelKitMain.KNOWLEDGE_MCP_REPOS;
        String camelMcpRepos = CamelKitMain.CAMEL_MCP_REPOS;

        try {
            String templatePath;
            Path configFile;

            switch (ctx.agentName().toLowerCase()) {
                case "claude" -> {
                    templatePath = ctx.offlineMode()
                            ? "templates/mcp-configs/claude-code-mcp-standalone.json"
                            : "templates/mcp-configs/claude-code-mcp.json";
                    configFile = ctx.projectDir().resolve(".mcp.json");
                    agentName = "Claude Code";
                }
                case "bob" -> {
                    templatePath = ctx.offlineMode()
                            ? "templates/mcp-configs/bob-mcp-standalone.json"
                            : "templates/mcp-configs/bob-mcp.json";
                    Path bobDir = ctx.projectDir().resolve(".bob");
                    Files.createDirectories(bobDir);
                    configFile = bobDir.resolve("mcp.json");
                    agentName = "IBM Bob";
                }
                case "gemini" -> {
                    templatePath = ctx.offlineMode()
                            ? "templates/mcp-configs/gemini-mcp-standalone.json"
                            : "templates/mcp-configs/gemini-mcp.json";
                    Path geminiDir = ctx.projectDir().resolve(".gemini");
                    Files.createDirectories(geminiDir);
                    configFile = geminiDir.resolve("settings.json");
                    agentName = "Gemini CLI";
                }
                case "qwen" -> {
                    templatePath = ctx.offlineMode()
                            ? "templates/mcp-configs/qwen-mcp-standalone.json"
                            : "templates/mcp-configs/qwen-mcp.json";
                    Path qwenDir = ctx.projectDir().resolve(".qwen");
                    Files.createDirectories(qwenDir);
                    configFile = qwenDir.resolve("settings.json");
                    agentName = "Qwen Code";
                }
                case "opencode" -> {
                    templatePath = ctx.offlineMode()
                            ? "templates/mcp-configs/opencode-mcp-standalone.json"
                            : "templates/mcp-configs/opencode-mcp.json";
                    configFile = ctx.projectDir().resolve("opencode.json");
                    agentName = "OpenCode";
                }
                default -> {
                    ctx.printer().println(AnsiColors.yellow("  Warning: Unknown agent '" + ctx.agentName() + "', skipping MCP config"));
                    return;
                }
            }

            QuteTemplateEngine qute = new QuteTemplateEngine();
            String template = TemplateUtils.readTemplate(templatePath);
            String processed = qute.renderString(template, Map.of(
                "CAMEL_MCP_VERSION", CamelKitMain.CAMEL_MCP_VERSION,
                "KNOWLEDGE_VERSION", CamelKitMain.KNOWLEDGE_MCP_VERSION,
                "CAMEL_MCP_REPOS", camelMcpRepos,
                "KNOWLEDGE_MCP_REPOS", knowledgeMcpRepos,
                "CAMEL_CATALOG_REPOS", CamelKitMain.CAMEL_CATALOG_REPOS
            ));
            Files.writeString(configFile, processed);

            ctx.printer().println(AnsiColors.green("✓") + " MCP config created for " + agentName);
        } catch (Exception e) {
            ctx.printer().println(AnsiColors.yellow("  Warning: Could not create MCP config: " + e.getMessage()));
        }
    }

    private void extractRunnerJar(InitContext ctx, Path mcpDir) throws Exception {
        String jarName = "camel-jbang-mcp-runner.jar";
        Path target = mcpDir.resolve(jarName);
        if (Files.exists(target)) return;

        // The runner JAR is bundled on the classpath by maven-dependency-plugin
        String resourceName = "offline-repo/" + "camel-jbang-mcp-"
                + CamelKitMain.CAMEL_MCP_VERSION + "-runner.jar";
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            if (in != null) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                ctx.printer().println(AnsiColors.green("✓") + " Extracted Camel MCP runner JAR");
            } else {
                ctx.printer().println(AnsiColors.yellow("  Warning: MCP runner JAR not found on classpath: " + resourceName));
            }
        }
    }
}
