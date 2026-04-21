package io.github.luigidemasi.camelkit.generator;

import io.github.luigidemasi.camelkit.CamelKitMain;
import io.github.luigidemasi.camelkit.config.DistributionConfig;
import io.github.luigidemasi.camelkit.util.AnsiColors;
import io.github.luigidemasi.camelkit.util.TemplateUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
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
        Files.createDirectories(ctx.commandsDir());
        Files.createDirectories(ctx.skillsDir());
        createCommandTemplates(ctx);
        copySkills(ctx);
        createMcpConfigs(ctx);
    }

    private void createCommandTemplates(InitContext ctx) throws Exception {
        List<String> commands = List.of("brainstorm", "flow", "plan", "implement", "validate", "test", "execute", "migrate", "knowledge", "verify");

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

                    String fileName = source.getFileName().toString();
                    String distribution = CamelKitMain.distribution().distribution();

                    // Skip variant files that don't match current distribution
                    // e.g., skip iron-laws-redhat.md when distribution is "community"
                    if (fileName.endsWith("-community.md") || fileName.endsWith("-redhat.md")) {
                        // Only process the variant that matches our distribution
                        if (fileName.endsWith("-" + distribution + ".md")) {
                            // This is our variant — copy it under the base name
                            String baseName = fileName.replace("-" + distribution + ".md", ".md");
                            Path baseDestination = destination.getParent().resolve(baseName);
                            Files.createDirectories(baseDestination.getParent());
                            try (InputStream in = Files.newInputStream(source)) {
                                Files.copy(in, baseDestination, StandardCopyOption.REPLACE_EXISTING);
                                filesCopied++;
                            }
                            // Also append dispatch block if this is a SKILL variant
                            if (baseName.equals("SKILL.md")) {
                                appendDispatchBlock(baseDestination, ctx.agentName());
                            }
                        }
                        // Skip this file from normal processing (don't copy the suffixed version)
                        continue;
                    }

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

    private void createMcpConfigs(InitContext ctx) throws Exception {
        String agentName = "";

        // Knowledge server repos (still uses JBang for now)
        String knowledgeMcpRepos = CamelKitMain.KNOWLEDGE_MCP_REPOS;
        String camelMcpRepos = CamelKitMain.CAMEL_MCP_REPOS;

        try {
            String templatePath;
            Path configFile;

            switch (ctx.agentName().toLowerCase()) {
                case "claude" -> {
                    templatePath = "templates/mcp-configs/claude-code-mcp.json";
                    configFile = ctx.projectDir().resolve(".mcp.json");
                    agentName = "Claude Code";
                }
                case "bob" -> {
                    templatePath = "templates/mcp-configs/bob-mcp.json";
                    Path bobDir = ctx.projectDir().resolve(".bob");
                    Files.createDirectories(bobDir);
                    configFile = bobDir.resolve("mcp.json");
                    agentName = "IBM Bob";
                }
                case "gemini" -> {
                    templatePath = "templates/mcp-configs/gemini-mcp.json";
                    Path geminiDir = ctx.projectDir().resolve(".gemini");
                    Files.createDirectories(geminiDir);
                    configFile = geminiDir.resolve("settings.json");
                    agentName = "Gemini CLI";
                }
                case "qwen" -> {
                    templatePath = "templates/mcp-configs/qwen-mcp.json";
                    Path qwenDir = ctx.projectDir().resolve(".qwen");
                    Files.createDirectories(qwenDir);
                    configFile = qwenDir.resolve("settings.json");
                    agentName = "Qwen Code";
                }
                case "opencode" -> {
                    templatePath = "templates/mcp-configs/opencode-mcp.json";
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
            DistributionConfig dist = CamelKitMain.distribution();
            Map<String, Object> templateData = new java.util.HashMap<>(Map.of(
                "CAMEL_MCP_VERSION", CamelKitMain.CAMEL_MCP_VERSION,
                "KNOWLEDGE_VERSION", CamelKitMain.KNOWLEDGE_MCP_VERSION,
                "CAMEL_MCP_REPOS", camelMcpRepos,
                "KNOWLEDGE_MCP_REPOS", knowledgeMcpRepos,
                "CAMEL_CATALOG_REPOS", CamelKitMain.CAMEL_CATALOG_REPOS
            ));
            templateData.put("DISTRIBUTION", dist.distribution());
            templateData.put("CAMEL_VERSION", dist.camelVersion());
            templateData.put("MAVEN_REPO", dist.mavenRepo());
            templateData.put("PRODUCT_NAME", dist.productName());

            String knowledgeToolPrefix = "camel_docs_";
            String knowledgeToolsJson = String.join(", ",
                "\"" + knowledgeToolPrefix + "component_info\"",
                "\"" + knowledgeToolPrefix + "search\"",
                "\"" + knowledgeToolPrefix + "jira_lookup\"",
                "\"" + knowledgeToolPrefix + "cve_search\"",
                "\"" + knowledgeToolPrefix + "bugfix_search\"",
                "\"" + knowledgeToolPrefix + "release_info\"",
                "\"" + knowledgeToolPrefix + "supported_configs\""
            );
            templateData.put("KNOWLEDGE_TOOLS_JSON", knowledgeToolsJson);

            String knowledgeDescription = "camel-kit Knowledge Server - documentation search for Apache Camel";
            templateData.put("KNOWLEDGE_DESCRIPTION", knowledgeDescription);

            String processed = qute.renderString(template, templateData);
            Files.writeString(configFile, processed);

            ctx.printer().println(AnsiColors.green("✓") + " MCP config created for " + agentName);
        } catch (Exception e) {
            ctx.printer().println(AnsiColors.yellow("  Warning: Could not create MCP config: " + e.getMessage()));
        }
    }

    protected void copyTemplateResource(String resourcePath, Path target) throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is != null) {
                Files.createDirectories(target.getParent());
                Files.writeString(target, new String(is.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
    }
}
