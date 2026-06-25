package io.github.luigidemasi.camelkit.generator;

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
import java.util.Locale;
import java.util.Map;

import io.github.luigidemasi.camelkit.CamelKitMain;
import io.github.luigidemasi.camelkit.config.DistributionConfig;
import io.github.luigidemasi.camelkit.util.AnsiColors;
import io.github.luigidemasi.camelkit.util.TemplateUtils;
import io.github.luigidemasi.camelkit.workflow.WorkflowManifest;
import io.github.luigidemasi.camelkit.workflow.WorkflowManifest.WorkflowCommand;
import io.github.luigidemasi.camelkit.workflow.WorkflowManifestLoader;

import com.fasterxml.jackson.databind.ObjectMapper;

public class DefaultGenerator implements AgentGenerator {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    @Override
    public void generate(InitContext ctx) throws Exception {
        WorkflowManifest workflow = WorkflowManifestLoader.loadDefault();

        Files.createDirectories(ctx.commandsDir());
        Files.createDirectories(ctx.skillsDir());
        generateAgentsMd(ctx);
        createCommandTemplates(ctx, workflow);
        copySkills(ctx);
        applyTraits(ctx, workflow);
        createMcpConfigs(ctx, workflow);
    }

    private void generateAgentsMd(InitContext ctx) throws Exception {
        QuteTemplateEngine qute = new QuteTemplateEngine();
        Map<String, Object> data = new java.util.HashMap<>(
                Map.of(
                        "COMMAND_PREFIX", ctx.commandPrefix()));
        String content = qute.render("templates/shared/agents-md.md", data);
        Files.writeString(ctx.projectDir().resolve("AGENTS.md"), content);
        ctx.printer().println(AnsiColors.green("✓") + " Generated AGENTS.md with skill routing and iron laws");
    }

    private void createCommandTemplates(InitContext ctx, WorkflowManifest workflow) throws Exception {
        List<WorkflowCommand> commands = workflow.generatedCommandStubs();

        // Extract agent base folder (e.g., ".bob" from ".bob/commands")
        String agentBaseFolder = ctx.agent().folder().substring(0, ctx.agent().folder().lastIndexOf("/"));

        for (WorkflowCommand command : commands) {
            String cmd = command.shortName();
            String skillName = command.skill();

            // Create reference to skill file
            String content
                    = "Read " + agentBaseFolder + "/skills/" + skillName + "/SKILL.md and follow those instructions";

            // Wrap in TOML format if needed
            if ("toml".equals(ctx.agent().fileFormat())) {
                content = wrapInToml(cmd, content);
            }

            String filename = command.name() + "." + ctx.agent().fileFormat();
            Files.writeString(ctx.commandsDir().resolve(filename), content);
        }

        ctx.printer().println(AnsiColors.green("✓") + " Created " + commands.size() + " skill reference commands");
    }

    private String wrapInToml(String cmd, String content) {
        // Escape triple quotes for TOML
        String escaped = content.replace("\"\"\"", "\\\"\\\"\\\"");
        return String.format(Locale.ROOT, """
                description = "Camel-Kit %s command"

                prompt = \"\"\"
                %s
                \"\"\"
                """, cmd, escaped);
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
                            // Substitute version placeholders in all .md files
                            if (destination.getFileName().toString().endsWith(".md")) {
                                try {
                                    substituteVersionPlaceholders(destination);
                                } catch (Exception e) {
                                    ctx.printer().println(AnsiColors.yellow(
                                            "  Warning: Failed to substitute version placeholders in "
                                                                            + destination + ": " + e.getMessage()));
                                }
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
                ctx.printer().println(AnsiColors.green("✓") + " Copied " + filesCopied + " files in " + skillCount
                                      + " skill folders");
            } else {
                ctx.printer().println(AnsiColors
                        .yellow("  No skills copied (this is normal - skills are embedded in command files)"));
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
     * Append the platform-specific dispatch block to a SKILL.md file. Reads the dispatch template for the selected
     * agent and appends it.
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
     * Substitute version placeholders in a copied skill .md file using Qute. Escapes all {@code {}} to protect
     * non-template braces (JSON, Maven {@code ${}}, YAML), then restores only known version placeholders before
     * rendering.
     */
    static void substituteVersionPlaceholders(Path mdFile) throws IOException {
        String content = Files.readString(mdFile);

        DistributionConfig dist = CamelKitMain.distribution();
        Map<String, String> versionData = buildVersionTemplateData(dist);

        boolean hasPlaceholder = false;
        for (String key : versionData.keySet()) {
            if (content.contains("{" + key + "}")) {
                hasPlaceholder = true;
                break;
            }
        }
        if (!hasPlaceholder) {
            return;
        }

        // Escape ALL { to \{ so Qute treats them as literals
        String escaped = content.replace("{", "\\{");

        // Restore only known version placeholders so Qute will resolve them
        for (String key : versionData.keySet()) {
            escaped = escaped.replace("\\{" + key + "}", "{" + key + "}");
        }

        QuteTemplateEngine qute = new QuteTemplateEngine();
        Map<String, Object> data = new java.util.HashMap<>(versionData);
        String rendered = qute.renderString(escaped, data);
        Files.writeString(mdFile, rendered);
    }

    private static Map<String, String> buildVersionTemplateData(DistributionConfig dist) {
        Map<String, String> data = new java.util.LinkedHashMap<>();
        data.put("CAMEL_VERSION", dist.camelMainVersion());
        data.put("CAMEL_MAIN_VERSION", dist.camelMainVersion());
        data.put("CAMEL_SPRINGBOOT_VERSION", dist.camelSpringbootVersion());
        data.put("CAMEL_QUARKUS_VERSION", dist.camelQuarkusVersion());
        data.put("SPRINGBOOT_BOM_VERSION", dist.springbootBomVersion());
        data.put("QUARKUS_PLATFORM_VERSION", dist.quarkusPlatformVersion());
        data.put("CAMEL_MAIN_SUPPORTED", dist.camelMainSupported());
        data.put("CAMEL_SPRINGBOOT_SUPPORTED", dist.camelSpringbootSupported());
        data.put("CAMEL_QUARKUS_SUPPORTED", dist.camelQuarkusSupported());

        StringBuilder table = new StringBuilder();
        for (var entry : dist.quarkusPlatformMappings().entrySet()) {
            table.append("| ").append(entry.getKey())
                    .append(" | ").append(entry.getValue())
                    .append(" |\n");
        }
        data.put("QUARKUS_PLATFORM_TABLE", table.toString().stripTrailing());
        return data;
    }

    private void createMcpConfigs(InitContext ctx, WorkflowManifest workflow) throws Exception {
        // Knowledge server repos (still uses JBang for now)
        String knowledgeMcpRepos = CamelKitMain.KNOWLEDGE_MCP_REPOS;
        String camelMcpRepos = CamelKitMain.CAMEL_MCP_REPOS;

        try {
            Path configFile = ctx.projectDir().resolve(ctx.agent().mcpConfigPath());
            if (configFile.getParent() != null) {
                Files.createDirectories(configFile.getParent());
            }

            QuteTemplateEngine qute = new QuteTemplateEngine();
            String template = TemplateUtils.readTemplate(ctx.agent().mcpConfigTemplatePath());
            DistributionConfig dist = CamelKitMain.distribution();
            Map<String, Object> templateData = new java.util.HashMap<>(
                    Map.of(
                            "CAMEL_MCP_VERSION", CamelKitMain.CAMEL_MCP_VERSION,
                            "KNOWLEDGE_VERSION", CamelKitMain.KNOWLEDGE_MCP_VERSION,
                            "CAMEL_MCP_REPOS", camelMcpRepos,
                            "KNOWLEDGE_MCP_REPOS", knowledgeMcpRepos,
                            "CAMEL_CATALOG_REPOS", CamelKitMain.CAMEL_CATALOG_REPOS));
            WorkflowManifest.WorkflowMcpServer camelServer = workflow.mcpServer("camel");
            templateData.put("CAMEL_TOOLS_JSON", toJsonArray(camelServer.allowedTools()));
            templateData.put("CAMEL_VERSION", dist.camelMainVersion());
            templateData.put("CAMEL_MAIN_VERSION", dist.camelMainVersion());
            templateData.put("CAMEL_SPRINGBOOT_VERSION", dist.camelSpringbootVersion());
            templateData.put("CAMEL_QUARKUS_VERSION", dist.camelQuarkusVersion());
            templateData.put("SPRINGBOOT_BOM_VERSION", dist.springbootBomVersion());
            templateData.put("QUARKUS_PLATFORM_VERSION", dist.quarkusPlatformVersion());
            templateData.put("CAMEL_MAIN_SUPPORTED", dist.camelMainSupported());
            templateData.put("CAMEL_SPRINGBOOT_SUPPORTED", dist.camelSpringbootSupported());
            templateData.put("CAMEL_QUARKUS_SUPPORTED", dist.camelQuarkusSupported());

            WorkflowManifest.WorkflowMcpServer knowledgeServer = workflow.mcpServer("camel-knowledge");
            templateData.put("KNOWLEDGE_TOOLS_JSON", toJsonArray(knowledgeServer.allowedTools()));
            templateData.put("KNOWLEDGE_DESCRIPTION", knowledgeServer.description());

            String processed = qute.renderString(template, templateData);
            Files.writeString(configFile, processed);

            ctx.printer().println(AnsiColors.green("✓") + " MCP config created for " + ctx.agent().name());
        } catch (Exception e) {
            ctx.printer().println(AnsiColors.yellow("  Warning: Could not create MCP config: " + e.getMessage()));
        }
    }

    private void applyTraits(InitContext ctx, WorkflowManifest workflow) throws Exception {
        String traitsBasePath = "templates/traits/" + ctx.agentName() + "/";
        int traitCount = 0;

        List<String> skillNames = workflow.skills().stream()
                .map(WorkflowManifest.WorkflowSkill::name)
                .toList();

        for (String skillName : skillNames) {
            String traitResourcePath = traitsBasePath + skillName + ".append.md";
            Path targetSkillMd = ctx.skillsDir().resolve(skillName + "/SKILL.md");
            if (appendTraitIfExists(traitResourcePath, targetSkillMd, ctx.agentName())) {
                traitCount++;
            }

            traitCount += applyGuideTraits(
                    traitsBasePath + skillName + "/",
                    ctx.skillsDir().resolve(skillName + "/guides/"),
                    ctx.agentName());
        }

        if (traitCount > 0) {
            ctx.printer().println(AnsiColors.green("✓") + " Applied " + traitCount
                                  + " agent traits for " + ctx.agentName());
        }
    }

    private static String toJsonArray(List<String> values) throws IOException {
        return JSON_MAPPER.writeValueAsString(values);
    }

    private int applyGuideTraits(String traitDirPath, Path guidesDir, String agentName) throws Exception {
        int count = 0;
        List<String> guideNames = List.of(
                "implementer-context", "spec-reviewer-criteria",
                "quality-reviewer-criteria", "verify-loop",
                "test-generation", "test-runner");

        for (String guideName : guideNames) {
            String traitResourcePath = traitDirPath + guideName + ".append.md";
            Path targetGuide = guidesDir.resolve(guideName + ".md");
            if (appendTraitIfExists(traitResourcePath, targetGuide, agentName)) {
                count++;
            }
        }
        return count;
    }

    private boolean appendTraitIfExists(String traitResourcePath, Path targetFile, String agentName) throws Exception {
        if (!Files.exists(targetFile)) {
            return false;
        }
        String traitContent;
        try {
            traitContent = TemplateUtils.readTemplate(traitResourcePath);
        } catch (IOException e) {
            return false;
        }

        String sentinel = "<!-- TRAIT:" + agentName + " -->";
        String existing = Files.readString(targetFile);
        if (existing.contains(sentinel)) {
            return false;
        }

        String closeSentinel = "<!-- /TRAIT:" + agentName + " -->";
        Files.writeString(targetFile,
                existing + "\n---\n\n" + sentinel + "\n" + traitContent + "\n" + closeSentinel + "\n");
        return true;
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
