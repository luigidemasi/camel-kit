package io.github.luigidemasi.camelkit.command;

import io.github.luigidemasi.camelkit.CamelKitMain;
import io.github.luigidemasi.camelkit.catalog.CitrusSchemaDownloader;
import io.github.luigidemasi.camelkit.config.AgentConfig;
import io.github.luigidemasi.camelkit.config.AgentRegistry;
import io.github.luigidemasi.camelkit.output.Printer;
import io.github.luigidemasi.camelkit.tui.InitTuiView;
import io.github.luigidemasi.camelkit.tui.TaskTracker;
import io.github.luigidemasi.camelkit.util.TemplateUtils;
import dev.tamboui.image.capability.TerminalImageCapabilities;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * Initialize a new Camel-Kit project.
 */
@Command(name = "init", description = "Initialize a new Camel-Kit project")
public class InitCommand extends CamelKitCommand {

    @Parameters(index = "0", description = "Project name", arity = "0..1")
    public String projectName;

    @Option(names = {"-a", "--ai"}, description = "AI agent: bob, gemini, claude",
            defaultValue = "bob")
    public String ai;

    @Option(names = {"--here"}, description = "Initialize in current directory")
    public boolean here;

    @Option(names = {"-v", "--camel-version"},
            description = "Camel version (use 'default' for bundled catalog)",
            defaultValue = "default")
    public String camelVersion;

    @Option(names = {"--citrus-version"},
            description = "Citrus version for test schemas",
            defaultValue = "default")
    public String citrusVersion;

    @Option(names = {"--no-fetch"}, description = "Skip external catalog fetching")
    public boolean noFetch;

    @Option(names = {"--silent"}, description = "Suppress all output (no banner, no progress, no summary)")
    public boolean silent;

    public InitCommand(CamelKitMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        if (silent) {
            main.setOut(Printer.noop());
        }

        // Validate agent and resolve arguments upfront (fast, non-blocking)
        if (!AgentRegistry.contains(ai)) {
            if (!silent) main.printBanner();
            printer().println(red("Error: Unknown agent '" + ai + "'"));
            printer().println("Available agents: " + String.join(", ", AgentRegistry.names()));
            return 1;
        }

        if (projectName == null && !here) {
            if (!silent) main.printBanner();
            printer().println(red("Error: Please provide a project name or use --here"));
            return 1;
        }

        if (!silent) {
            // If native image protocol is available and TUI is enabled, run the full
            // split-screen experience. Falls back to normal mode on any failure
            // (missing backend JAR, dumb terminal, JBang plugin context, etc.).
            if (main.isTuiEnabled() && TerminalImageCapabilities.detect().supportsNativeImages()) {
                InitTuiView tui = new InitTuiView();
                Printer original = main.getOut();
                main.setOut(tui.createPrinter());
                main.setTaskTracker(tui.createTaskTracker());
                // Release our JLine terminal so TamboUI can acquire the device cleanly.
                main.closeTerminal();
                try {
                    return tui.run(this::doInitWork);
                } catch (Throwable e) {
                    // TUI not available — restore and continue in normal mode
                    main.setOut(original);
                    main.setTaskTracker(TaskTracker.noop());
                }
            }

            // Normal mode: print banner then run init inline
            main.printBanner();
        }

        return doInitWork();
    }

    private Integer doInitWork() throws Exception {
        AgentConfig agent = AgentRegistry.get(ai);
        // Resolve target directory
        Path targetDir;
        if (here) {
            targetDir = Path.of("").toAbsolutePath();
            projectName = targetDir.getFileName().toString();
        } else {
            targetDir = Path.of(projectName).toAbsolutePath();
        }

        // Get catalog versions
        String version = "default".equals(camelVersion)
            ? CamelKitMain.LATEST_CAMEL_LTS_VERSION
            : camelVersion;

        String citrusVer = "default".equals(citrusVersion)
            ? CamelKitMain.DEFAULT_CITRUS_VERSION
            : citrusVersion;

        TaskTracker tracker = main.getTaskTracker();

        // 1 — project structure
        tracker.startTask("📁", "Creating project structure");
        Files.createDirectories(targetDir);
        Path commandsDir = targetDir.resolve(agent.folder());
        Files.createDirectories(commandsDir);
        Path camelKitDir = targetDir.resolve(".camel-kit");
        Files.createDirectories(camelKitDir);
        Path docsDir = targetDir.resolve("docs");
        Files.createDirectories(docsDir.resolve("flows"));
        Files.createDirectories(camelKitDir.resolve("templates"));
        Files.createDirectories(camelKitDir.resolve(".cache"));
        Files.createDirectories(targetDir.resolve("test/data"));
        Files.createDirectories(targetDir.resolve("schemas"));
        tracker.finishTask();

        // 2 — configuration files
        tracker.startTask("\uD83D\uDCDD", "Writing configuration");
        createConfigFile(camelKitDir, projectName, version, citrusVer, ai, agent);
        createConstitution(docsDir, version);
        createYamlGuide(camelKitDir.resolve("templates"));
        copyAdditionalTemplates(camelKitDir.resolve("templates"));
        tracker.finishTask();

        // 3 — AI agent commands
        tracker.startTask("🤖", "Registering " + agent.name() + " commands");
        createCommandTemplates(commandsDir, agent);
        tracker.finishTask();

        // 4 — skills
        tracker.startTask("📚", "Copying skills");
        Path agentBaseDir = targetDir.resolve(agent.folder()).getParent();
        copySkills(agentBaseDir.resolve("skills"));
        tracker.finishTask();

        // 5 — MCP + Maven Wrapper
        tracker.startTask("🔌", "Configuring MCP & Maven wrapper");
        createMavenWrapper(targetDir);
        createMcpConfigs(targetDir, version, ai);
        tracker.finishTask();

        // 6 — Citrus schemas
        int citrusSchemaCount = 0;
        if (!noFetch) {
            tracker.startTask("⬇️", "Downloading Citrus schemas");
            try {
                CitrusSchemaDownloader citrusDownloader = new CitrusSchemaDownloader(camelKitDir.resolve(".cache"));
                citrusDownloader.fetchCitrusSchemas(citrusVer, false, printer()::println);
                Path citrusSchemasDir = citrusDownloader.getCitrusSchemasDir(citrusVer);
                if (Files.exists(citrusSchemasDir)) {
                    citrusSchemaCount = (int) Files.walk(citrusSchemasDir)
                        .filter(p -> p.toString().endsWith(".json"))
                        .count();
                }
            } catch (Exception e) {
                printer().println(yellow("  Warning: Could not fetch Citrus schemas: " + e.getMessage()));
            }
            tracker.finishTask();
        }

        // Summary line
        printer().println();
        printer().print("  " + green("✓") + "  ");
        printer().println(bold(projectName));
        String meta = "     " + version + "  \u00b7  " + agent.name()
                + (citrusSchemaCount > 0 ? "  \u00b7  " + citrusSchemaCount + " schemas" : "");
        printer().println(meta);
        printer().println();

        // Next steps
        String divider = "  " + "\u2500".repeat(34);
        printer().println("  " + bold("Next steps"));
        printer().println(divider);
        printer().println("  1  Open " + cyan(projectName) + " in " + agent.name());
        printer().println("  2  " + cyan("/camel-project") + "   \u2014 define integration landscape");
        printer().println("     " + cyan("/camel-migrate") + "   \u2014 migrate from another platform");
        printer().println("  3  " + cyan("/camel-flow") + " <name>");
        printer().println("  4  " + cyan("/camel-implement") + " <name>");
        printer().println();

        return 0;
    }

    private void createConfigFile(Path dir, String name, String version, String citrusVer,
                                   String ai, AgentConfig agent) throws Exception {
        String yaml = """
            # Camel-Kit Configuration
            project:
              name: %s
              camelVersion: "%s"
              citrusVersion: "%s"

            agent:
              name: %s
              folder: %s

            catalog:
              source: bundled
              lastUpdated: %s
            """.formatted(name, version, citrusVer, ai, agent.folder(), Instant.now().toString());
        Files.writeString(dir.resolve("config.yaml"), yaml);
    }

    private void createConstitution(Path dir, String camelVersion) throws Exception {
        String content = TemplateUtils.readTemplate("templates/constitution.md")
                .replace("{{DATE}}", java.time.LocalDate.now().toString())
                .replace("{{CAMEL_VERSION}}", camelVersion);
        Files.writeString(dir.resolve("constitution.md"), content);
    }

    private void createCommandTemplates(Path dir, AgentConfig agent) throws Exception {
        List<String> commands = List.of("project", "flow", "implement", "validate", "test", "migrate");

        // Extract agent base folder (e.g., ".bob" from ".bob/commands")
        String agentBaseFolder = agent.folder().substring(0, agent.folder().lastIndexOf("/"));

        for (String cmd : commands) {
            String skillName = "camel-" + cmd;

            // Create reference to skill file
            String content = "Read " + agentBaseFolder + "/skills/" + skillName + "/SKILL.md and follow those instructions";

            // Wrap in TOML format if needed
            if ("toml".equals(agent.fileFormat())) {
                content = wrapInToml(cmd, content);
            }

            String filename = skillName + "." + agent.fileFormat();
            Files.writeString(dir.resolve(filename), content);
        }

        printer().println(green("✓") + " Created " + commands.size() + " skill reference commands");
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

    private void createYamlGuide(Path dir) throws Exception {
        String content = TemplateUtils.readTemplate("templates/yaml-generation-guide.md");
        Files.writeString(dir.resolve("yaml-generation-guide.md"), content);
    }

    private void copyAdditionalTemplates(Path templatesDir) throws Exception {
        String[] additionalTemplates = {
            "design-patterns.md",
            "validation-guide.md",
            "flow.md"
        };
        for (String template : additionalTemplates) {
            String content = TemplateUtils.readTemplate("templates/" + template);
            Files.writeString(templatesDir.resolve(template), content);
        }
    }

    private void copySkills(Path skillsTargetDir) throws Exception {
        Files.createDirectories(skillsTargetDir);

        // Get the skills directory from resources
        var skillsResource = getClass().getClassLoader().getResource("skills");
        if (skillsResource == null) {
            printer().println(yellow("  Warning: Skills not found in resources"));
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
                    Path destination = skillsTargetDir.resolve(relativePathStr);

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
                        }
                    } catch (Exception e) {
                        // Silent skip - this is expected for some paths
                    }
                }
            }

            // Count copied skills (directories only)
            int skillCount = (int) Files.list(skillsTargetDir)
                .filter(Files::isDirectory)
                .count();

            if (filesCopied > 0) {
                printer().println(green("✓") + " Copied " + filesCopied + " files in " + skillCount + " skill folders");
            } else {
                printer().println(yellow("  No skills copied (this is normal - skills are embedded in command files)"));
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

    private void createMavenWrapper(Path projectDir) throws Exception {
        // Create .mvn/wrapper directory
        Path wrapperDir = projectDir.resolve(".mvn/wrapper");
        Files.createDirectories(wrapperDir);

        // Create maven-wrapper.properties
        String mavenVersion = "3.9.9";
        String wrapperProps = """
            distributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/%s/apache-maven-%s-bin.zip
            wrapperUrl=https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar
            """.formatted(mavenVersion, mavenVersion);
        Files.writeString(wrapperDir.resolve("maven-wrapper.properties"), wrapperProps);

        // Create mvnw (Unix script)
        String mvnwScript = TemplateUtils.readTemplate("maven/mvnw");
        Path mvnwPath = projectDir.resolve("mvnw");
        Files.writeString(mvnwPath, mvnwScript);
        mvnwPath.toFile().setExecutable(true);

        // Create mvnw.cmd (Windows script)
        String mvnwCmdScript = TemplateUtils.readTemplate("maven/mvnw.cmd");
        Files.writeString(projectDir.resolve("mvnw.cmd"), mvnwCmdScript);

        printer().println(green("✓") + " Maven Wrapper created (portable Maven " + mavenVersion + ")");
    }

    private void createMcpConfigs(Path projectDir, String camelVersion, String selectedAgent) throws Exception {
        String agentName = "";

        // Create MCP config only for the selected agent
        try {
            switch (selectedAgent.toLowerCase()) {
                case "claude" -> {
                    // Claude Code - .mcp.json in project root (standard MCP location)
                    String claudeTemplate = TemplateUtils.readTemplate("templates/mcp-configs/claude-code-mcp.json");
                    String processedTemplate = claudeTemplate.replace("{{CAMEL_VERSION}}", camelVersion)
                            .replace("{{KNOWLEDGE_VERSION}}", CamelKitMain.DEFAULT_KNOWLEDGE_MCP_VERSION);
                    Files.writeString(projectDir.resolve(".mcp.json"), processedTemplate);
                    agentName = "Claude Code";
                }
                case "bob" -> {
                    // IBM Bob - .bob/mcp.json
                    String bobTemplate = TemplateUtils.readTemplate("templates/mcp-configs/bob-mcp.json");
                    String processedTemplate = bobTemplate.replace("{{CAMEL_VERSION}}", camelVersion)
                            .replace("{{KNOWLEDGE_VERSION}}", CamelKitMain.DEFAULT_KNOWLEDGE_MCP_VERSION);
                    Path bobDir = projectDir.resolve(".bob");
                    Files.createDirectories(bobDir);
                    Files.writeString(bobDir.resolve("mcp.json"), processedTemplate);
                    agentName = "IBM Bob";
                }
                case "gemini" -> {
                    // Gemini CLI - .gemini/mcp.json
                    String geminiTemplate = TemplateUtils.readTemplate("templates/mcp-configs/gemini-mcp.json");
                    String processedTemplate = geminiTemplate.replace("{{CAMEL_VERSION}}", camelVersion)
                            .replace("{{KNOWLEDGE_VERSION}}", CamelKitMain.DEFAULT_KNOWLEDGE_MCP_VERSION);
                    Path geminiDir = projectDir.resolve(".gemini");
                    Files.createDirectories(geminiDir);
                    Files.writeString(geminiDir.resolve("settings.json"), processedTemplate);
                    agentName = "Gemini CLI";
                }
                default -> {
                    printer().println(yellow("  Warning: Unknown agent '" + selectedAgent + "', skipping MCP config"));
                    return;
                }
            }

            printer().println(green("✓") + " MCP config created for " + agentName);
        } catch (Exception e) {
            printer().println(yellow("  Warning: Could not create MCP config: " + e.getMessage()));
        }
    }
}
