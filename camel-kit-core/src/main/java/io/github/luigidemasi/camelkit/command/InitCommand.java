package io.github.luigidemasi.camelkit.command;

import io.github.luigidemasi.camelkit.CamelKitMain;
import io.github.luigidemasi.camelkit.config.DistributionConfig;
import io.github.luigidemasi.camelkit.catalog.CitrusSchemaDownloader;
import io.github.luigidemasi.camelkit.config.AgentConfig;
import io.github.luigidemasi.camelkit.config.AgentRegistry;
import io.github.luigidemasi.camelkit.generator.AgentGeneratorFactory;
import io.github.luigidemasi.camelkit.generator.InitContext;
import io.github.luigidemasi.camelkit.generator.QuteTemplateEngine;
import io.github.luigidemasi.camelkit.graph.GraphBuilder;
import io.github.luigidemasi.camelkit.graph.ProjectGraph;
import io.github.luigidemasi.camelkit.output.Printer;
import io.github.luigidemasi.camelkit.tui.InitTuiView;
import io.github.luigidemasi.camelkit.tui.TaskTracker;
import io.github.luigidemasi.camelkit.util.TemplateUtils;
import dev.tamboui.image.capability.TerminalImageCapabilities;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

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
        copyAdditionalTemplates(camelKitDir.resolve("templates"));
        tracker.finishTask();

        // 3+4+4b+5 — AI agent generation (commands, skills, MCP)
        tracker.startTask("🤖", "Generating " + agent.name() + " workspace");
        String agentBaseFolder = agent.folder().substring(0, agent.folder().lastIndexOf("/"));
        Path agentBaseDir = targetDir.resolve(agent.folder()).getParent();
        InitContext genCtx = new InitContext(agent, ai, commandsDir,
            agentBaseDir.resolve("skills"), targetDir,
            detectCommandPrefix(), version, printer());
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
        printer().println("     Targeting " + cyan("Apache Camel"));
        printer().println();

        // Next steps
        String divider = "  " + "\u2500".repeat(34);
        printer().println("  " + bold("Next steps"));
        printer().println(divider);
        printer().println("  1  Open " + cyan(projectName) + " in " + agent.name());
        printer().println("  2  " + cyan("/camel-brainstorm") + " \u2014 design an integration (greenfield or migration)");
        printer().println("     " + cyan("/camel-flow") + "       \u2014 greenfield shortcut");
        printer().println("     " + cyan("/camel-migrate") + "    \u2014 migration shortcut");
        printer().println("  3  " + cyan("/camel-verify") + "     \u2014 build, run, and diagnose");
        printer().println();

        return 0;
    }

    private void createConfigFile(Path dir, String name, String version, String citrusVer,
                                   String ai, AgentConfig agent) throws Exception {
        String cmdPrefix = detectCommandPrefix();
        DistributionConfig dist = CamelKitMain.distribution();
        String quarkusBom = dist.quarkusBomVersion();
        String springBootBom = dist.springbootBomVersion();
        String yaml = """
            # Camel-Kit Configuration
            project:
              name: %s
              camelVersion: "%s"
              citrusVersion: "%s"
              command-prefix: "%s"
              platformBomVersion:
                quarkus: "%s"
                spring-boot: "%s"

            agent:
              name: %s
              folder: %s

            catalog:
              source: bundled
              lastUpdated: %s
            """.formatted(name, version, citrusVer, cmdPrefix,
                quarkusBom, springBootBom,
                ai, agent.folder(), Instant.now().toString());
        Files.writeString(dir.resolve("config.yaml"), yaml);
    }

    private String detectCommandPrefix() {
        String cmdLine = ProcessHandle.current().info().commandLine().orElse("");
        if (cmdLine.contains("camel-kit")) return "camel-kit";
        return "camel kit";
    }

    private void createConstitution(Path dir, String camelVersion) throws Exception {
        QuteTemplateEngine qute = new QuteTemplateEngine();

        String template = TemplateUtils.readTemplate("templates/constitution.md");

        String content = qute.renderString(template, Map.of(
            "DATE", java.time.LocalDate.now().toString(),
            "CAMEL_VERSION", camelVersion
        ));
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

}
