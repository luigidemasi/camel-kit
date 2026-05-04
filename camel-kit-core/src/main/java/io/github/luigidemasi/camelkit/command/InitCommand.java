package io.github.luigidemasi.camelkit.command;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.github.luigidemasi.camelkit.CamelKitMain;
import io.github.luigidemasi.camelkit.catalog.CitrusSchemaDownloader;
import io.github.luigidemasi.camelkit.config.AgentConfig;
import io.github.luigidemasi.camelkit.config.AgentRegistry;
import io.github.luigidemasi.camelkit.config.DistributionConfig;
import io.github.luigidemasi.camelkit.generator.AgentGeneratorFactory;
import io.github.luigidemasi.camelkit.generator.InitContext;
import io.github.luigidemasi.camelkit.generator.QuteTemplateEngine;
import io.github.luigidemasi.camelkit.graph.GraphBuilder;
import io.github.luigidemasi.camelkit.graph.ProjectGraph;
import io.github.luigidemasi.camelkit.graph.RuntimeDetector;
import io.github.luigidemasi.camelkit.output.Printer;
import io.github.luigidemasi.camelkit.tui.InitTuiView;
import io.github.luigidemasi.camelkit.tui.TaskTracker;
import io.github.luigidemasi.camelkit.util.PrerequisiteChecker;
import io.github.luigidemasi.camelkit.util.TemplateUtils;

import dev.tamboui.image.capability.TerminalImageCapabilities;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Initialize a new Camel-Kit project.
 */
@Command(name = "init", description = "Initialize a new Camel-Kit project")
public class InitCommand extends CamelKitCommand {

    @Parameters(index = "0", description = "Project name", arity = "0..1")
    public String projectName;

    @Option(names = {"-a", "--ai"}, description = "AI agent: bob, gemini, claude, qwen, opencode",
            defaultValue = "bob")
    public String ai;

    @Option(names = {"--here"}, description = "Initialize in current directory")
    public boolean here;

    @Option(names = {"--citrus-version"},
            description = "Citrus version for test schemas",
            defaultValue = "default")
    public String citrusVersion;

    @Option(names = {"--no-fetch"}, description = "Skip external catalog fetching")
    public boolean noFetch;

    @Option(names = {"--force"}, description = "Overwrite existing project without prompting")
    public boolean force;

    @Option(names = {"--silent"}, description = "Suppress all output (no banner, no progress, no summary)")
    public boolean silent;

    @Option(names = {"-c", "--config"},
            description = "Path to config properties file (default: ~/.camel-kit/config.properties)")
    public Path configFile;

    @Option(names = {"-p", "--property"}, arity = "1..*",
            description = "Override config property: -p key=value (repeatable)")
    public List<String> properties;

    @Option(names = {"--source-platform"},
            description = "Source platform for migration graph analysis: mulesoft, camel, biztalk, auto (default: auto)")
    public String sourcePlatform;

    private Path resolvedTargetDir;

    public InitCommand(CamelKitMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        if (silent) {
            main.setOut(Printer.noop());
        }

        // Reload config with cascading overrides: JAR defaults → -c file → -p properties
        if (configFile != null || properties != null) {
            CamelKitMain.reloadDistribution(configFile, properties);
        }

        // Validate agent and resolve arguments upfront (fast, non-blocking)
        if (!AgentRegistry.contains(ai)) {
            if (!silent)
                main.printBanner();
            printer().println(red("Error: Unknown agent '" + ai + "'"));
            printer().println("Available agents: " + String.join(", ", AgentRegistry.names()));
            return 1;
        }

        if (projectName == null && !here) {
            if (!silent)
                main.printBanner();
            printer().println(red("Error: Please provide a project name or use --here"));
            return 1;
        }

        // Resolve target directory once — reused in doInitWork()
        if (here) {
            resolvedTargetDir = Path.of("").toAbsolutePath();
            projectName = resolvedTargetDir.getFileName().toString();
        } else {
            resolvedTargetDir = Path.of(projectName).toAbsolutePath();
        }

        // Overwrite detection (runs before TUI so colors work on error)
        Path agentsMd = resolvedTargetDir.resolve("AGENTS.md");
        Path camelKitDir = resolvedTargetDir.resolve(".camel-kit");
        if (!force && (Files.exists(agentsMd) || Files.isDirectory(camelKitDir))) {
            if (!silent)
                main.printBanner();
            printer().println();
            printer().println(yellow("⚠") + bold(" Project already initialized"));
            printer().println(dim("  Directory: ") + cyan(resolvedTargetDir.toString()));
            if (Files.exists(agentsMd)) {
                printer().println(dim("  Found:     ") + "AGENTS.md");
            }
            if (Files.isDirectory(camelKitDir)) {
                printer().println(dim("  Found:     ") + ".camel-kit/");
            }
            printer().println();
            printer().println(dim("  To overwrite: ") + bold("--force"));
            printer().println(dim("  Example:      ") + "camel-kit init " + projectName + " --ai " + ai + " --force");
            printer().println();
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
        // Prerequisite check (non-blocking, informational)
        if (!silent) {
            PrerequisiteChecker.check(printer());
        }

        AgentConfig agent = AgentRegistry.get(ai);
        Path targetDir = resolvedTargetDir;
        Path camelKitDir = targetDir.resolve(".camel-kit");

        // Get Citrus version
        String citrusVer = "default".equals(citrusVersion)
                ? CamelKitMain.DEFAULT_CITRUS_VERSION
                : citrusVersion;

        TaskTracker tracker = main.getTaskTracker();

        // 1 — project structure
        tracker.startTask("📁", "Creating project structure");
        Files.createDirectories(targetDir);
        Path commandsDir = targetDir.resolve(agent.folder());
        Files.createDirectories(commandsDir);
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
        createConfigFile(camelKitDir, projectName, ai, agent);
        createConstitution(docsDir);
        copyAdditionalTemplates(camelKitDir.resolve("templates"));
        tracker.finishTask();

        // 3+4+4b+5 — AI agent generation (commands, skills, MCP)
        tracker.startTask("🤖", "Generating " + agent.name() + " workspace");
        String agentBaseFolder = agent.folder().substring(0, agent.folder().lastIndexOf("/"));
        Path agentBaseDir = targetDir.resolve(agent.folder()).getParent();
        InitContext genCtx = new InitContext(
                agent, ai, commandsDir,
                agentBaseDir.resolve("skills"), targetDir,
                detectCommandPrefix(), printer());
        AgentGeneratorFactory.create(ai).generate(genCtx);
        tracker.finishTask();

        // Maven Wrapper
        tracker.startTask("🔌", "Configuring Maven wrapper");
        createMavenWrapper(targetDir);
        tracker.finishTask();

        // 6 — Project graph
        tracker.startTask("\uD83D\uDD0D", "Building project graph");
        try {
            GraphBuilder graphBuilder = new GraphBuilder();
            ProjectGraph projectGraph = graphBuilder.build(targetDir);
            if (projectGraph.nodeCount() > 0) {
                Path graphFile = camelKitDir.resolve("project-graph.json");
                io.github.luigidemasi.camelkit.graph.GraphSerializer.write(
                        projectGraph, graphFile, targetDir.toAbsolutePath().toString());
                printer().println(green("✓") + " Project graph: " + projectGraph.nodeCount()
                                  + " nodes, " + projectGraph.edgeCount() + " edges");

                String detected = detectProjectType(projectGraph);
                if (!detected.isEmpty()) {
                    printer().println("     Detected: " + cyan(detected));
                }

                // Update config file with detected runtime
                updateConfigWithRuntime(camelKitDir, projectGraph);
            } else {
                printer().println(yellow("  No source files found — graph skipped"));
            }
        } catch (Exception e) {
            printer().println(yellow("  Warning: Could not build project graph: " + e.getMessage()));
        }
        tracker.finishTask();

        // 7 — Citrus schemas
        int citrusSchemaCount = 0;
        if (!noFetch) {
            tracker.startTask("⬇️", "Downloading Citrus schemas");
            try {
                CitrusSchemaDownloader citrusDownloader = new CitrusSchemaDownloader(camelKitDir.resolve(".cache"));
                citrusDownloader.fetchCitrusSchemas(citrusVer, false, printer()::println);
                Path citrusSchemasDir = citrusDownloader.getCitrusSchemasDir(citrusVer);
                if (Files.exists(citrusSchemasDir)) {
                    try (var paths = Files.walk(citrusSchemasDir)) {
                        citrusSchemaCount = (int) paths
                                .filter(p -> p.toString().endsWith(".json"))
                                .count();
                    }
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
        String meta = "     " + agent.name()
                      + (citrusSchemaCount > 0 ? "  \u00b7  " + citrusSchemaCount + " schemas" : "");
        printer().println(meta);
        printer().println();

        // Next steps
        String divider = "  " + "\u2500".repeat(34);
        printer().println("  " + bold("Next steps"));
        printer().println(divider);
        printer().println("  1  Open " + cyan(projectName) + " in " + agent.name());
        printer()
                .println("  2  " + cyan("/camel-design") + "   \u2014 design an integration (greenfield or migration)");
        printer().println("     " + cyan("/camel-migrate") + "  \u2014 migration shortcut");
        printer().println("  3  " + cyan("/camel-verify") + "   \u2014 build, run, and diagnose");
        printer().println();

        return 0;
    }

    private String detectProjectType(ProjectGraph graph) {
        var types = new java.util.ArrayList<String>();

        if (!graph.findByType(io.github.luigidemasi.camelkit.graph.model.NodeType.MULE_FLOW).isEmpty()) {
            int flows = graph.findByType(io.github.luigidemasi.camelkit.graph.model.NodeType.MULE_FLOW).size();
            int subFlows = graph.findByType(io.github.luigidemasi.camelkit.graph.model.NodeType.MULE_SUB_FLOW).size();
            int dwl = graph.findByType(io.github.luigidemasi.camelkit.graph.model.NodeType.DATAWEAVE_SCRIPT).size();
            String muleVersion = findMavenProperty(graph, "mule.version");
            if (muleVersion == null)
                muleVersion = findMavenProperty(graph, "app.runtime");
            if (muleVersion == null)
                muleVersion = findArtifactVersion(graph, "org.mule.runtime", "mule-core");
            if (muleVersion == null)
                muleVersion = findArtifactVersion(graph, "org.mule", "mule-core");
            types.add("MuleSoft Mule" + (muleVersion != null ? " " + muleVersion : "")
                      + " (" + flows + " flows, " + subFlows + " sub-flows"
                      + (dwl > 0 ? ", " + dwl + " DataWeave scripts" : "") + ")");
        }

        int orchs = graph.findByType(io.github.luigidemasi.camelkit.graph.model.NodeType.BIZTALK_ORCHESTRATION).size();
        int maps = graph.findByType(io.github.luigidemasi.camelkit.graph.model.NodeType.BIZTALK_MAP).size();
        int pipelines = graph.findByType(io.github.luigidemasi.camelkit.graph.model.NodeType.BIZTALK_PIPELINE).size();
        if (orchs > 0 || maps > 0 || pipelines > 0) {
            types.add("Microsoft BizTalk (" + orchs + " orchestrations"
                      + (maps > 0 ? ", " + maps + " maps" : "")
                      + (pipelines > 0 ? ", " + pipelines + " pipelines" : "") + ")");
        }

        if (!graph.findByType(io.github.luigidemasi.camelkit.graph.model.NodeType.CAMEL_ROUTE).isEmpty()) {
            int routes = graph.findByType(io.github.luigidemasi.camelkit.graph.model.NodeType.CAMEL_ROUTE).size();
            String camelVersion = detectCamelVersion(graph);
            String platform = detectCamelPlatform(graph);
            types.add(platform + (camelVersion != null ? " " + camelVersion : "")
                      + " (" + routes + " routes)");
        }

        if (!graph.findByType(io.github.luigidemasi.camelkit.graph.model.NodeType.MAVEN_ARTIFACT).isEmpty()
                && types.isEmpty()) {
            types.add("Maven project");
        }

        return String.join(", ", types);
    }

    private String detectCamelVersion(ProjectGraph graph) {
        // Try Maven property first (most reliable — declared in POM <properties>)
        String version = findMavenProperty(graph, "camel.version");
        if (version != null)
            return version;

        // Try explicit dependency version
        version = findArtifactVersion(graph, "org.apache.camel", "camel-core");
        if (version != null)
            return version;

        version = findArtifactVersion(graph, "org.apache.camel", "camel-bom");
        if (version != null)
            return version;

        // Camel dependencies are "managed" — scan all camel artifacts for any with a real version
        for (var node : graph.findByType(io.github.luigidemasi.camelkit.graph.model.NodeType.MAVEN_ARTIFACT)) {
            if ("org.apache.camel".equals(node.properties().get("groupId"))) {
                String v = node.properties().get("version");
                if (v != null && !"managed".equals(v) && !"unknown".equals(v) && !v.contains("${")) {
                    return v;
                }
            }
        }

        return null;
    }

    private String detectCamelPlatform(ProjectGraph graph) {
        // Check project groupId for Fuse indicators
        for (var node : graph.findByType(io.github.luigidemasi.camelkit.graph.model.NodeType.MAVEN_ARTIFACT)) {
            String file = node.properties().get("file");
            if (file != null && file.contains("pom.xml")) {
                String groupId = node.properties().getOrDefault("groupId", "");
                if (groupId.contains("jboss.fuse") || groupId.contains("fusesource")) {
                    return "JBoss Fuse";
                }
            }
        }

        // Check dependency versions for .redhat- or .fuse- qualifiers
        for (var node : graph.findByType(io.github.luigidemasi.camelkit.graph.model.NodeType.MAVEN_ARTIFACT)) {
            String v = node.properties().getOrDefault("version", "");
            if (v.contains(".fuse-") || v.contains(".redhat-")) {
                return "JBoss Fuse";
            }
        }

        return "Apache Camel";
    }

    private String findMavenProperty(ProjectGraph graph, String propertyName) {
        var node = graph.getNode("maven-property:" + propertyName);
        if (node != null) {
            String value = node.properties().get("value");
            if (value != null && !value.isEmpty() && !value.contains("${")) {
                return value;
            }
        }
        return null;
    }

    private String findArtifactVersion(ProjectGraph graph, String groupId, String artifactId) {
        String nodeId = "maven:" + groupId + ":" + artifactId;
        var node = graph.getNode(nodeId);
        if (node != null) {
            String version = node.properties().get("version");
            if (version != null && !"managed".equals(version) && !"unknown".equals(version)
                    && !version.contains("${")) {
                return version;
            }
        }
        return null;
    }

    private void createConfigFile(
            Path dir, String name,
            String ai, AgentConfig agent)
            throws Exception {
        String cmdPrefix = detectCommandPrefix();
        java.util.Properties config = new java.util.Properties();
        config.setProperty("project.name", name);
        config.setProperty("project.command-prefix", cmdPrefix);
        config.setProperty("agent.name", ai);
        config.setProperty("agent.folder", agent.folder());
        if (sourcePlatform != null && !"auto".equals(sourcePlatform)) {
            config.setProperty("project.sourcePlatform", sourcePlatform);
        }

        Path configFile = dir.resolve("config.properties");
        try (var out = Files.newOutputStream(configFile)) {
            config.store(out, "Camel-Kit Project Configuration");
        }
    }

    private void updateConfigWithRuntime(Path dir, ProjectGraph graph) throws Exception {
        Path configFile = dir.resolve("config.properties");
        java.util.Properties config = new java.util.Properties();

        // Load existing properties
        if (Files.exists(configFile)) {
            try (var in = Files.newInputStream(configFile)) {
                config.load(in);
            }
        }

        // Detect and set runtime
        String runtime = RuntimeDetector.detect(graph);
        config.setProperty("project.runtime", runtime);

        // Write back
        try (var out = Files.newOutputStream(configFile)) {
            config.store(out, "Camel-Kit Project Configuration");
        }
    }

    private String detectCommandPrefix() {
        String cmdLine = ProcessHandle.current().info().commandLine().orElse("");
        if (cmdLine.contains("camel-kit"))
            return "camel-kit";
        return "camel kit";
    }

    private void createConstitution(Path dir) throws Exception {
        QuteTemplateEngine qute = new QuteTemplateEngine();

        String template = TemplateUtils.readTemplate("templates/constitution.md");

        DistributionConfig dist = CamelKitMain.distribution();
        String content = qute.renderString(template, Map.of(
                "DATE", java.time.LocalDate.now(ZoneId.systemDefault()).toString(),
                "CAMEL_VERSION", dist.camelMainVersion()));
        Files.writeString(dir.resolve("constitution.md"), content);
    }

    private void copyAdditionalTemplates(Path templatesDir) throws Exception {
        String[] additionalTemplates = {
                "patterns-foundational.md",
                "patterns-error-handling.md",
                "patterns-deployment.md",
                "yaml-structure.md",
                "yaml-components.md",
                "yaml-examples.md",
                "validation-completeness.md",
                "validation-constitution.md",
                "validation-testing.md",
                "flow.md"
        };
        for (String template : additionalTemplates) {
            String content = TemplateUtils.readTemplate("templates/" + template);
            Files.writeString(templatesDir.resolve(template), content);
        }
    }

    private void createMavenWrapper(Path projectDir) throws Exception {
        // Create .mvn/wrapper directory
        Path wrapperDir = projectDir.resolve(".mvn/wrapper");
        Files.createDirectories(wrapperDir);

        // Create maven-wrapper.properties
        String mavenVersion = "3.9.9";
        String wrapperProps
                = String.format(Locale.ROOT,
                        """
                                distributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/%s/apache-maven-%s-bin.zip
                                wrapperUrl=https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar
                                """,
                        mavenVersion, mavenVersion);
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

}
