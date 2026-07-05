package io.github.luigidemasi.camelkit.command;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import io.github.luigidemasi.camelkit.CamelKitMain;
import io.github.luigidemasi.camelkit.config.AgentGeneratorStrategy;
import io.github.luigidemasi.camelkit.config.AgentRegistry;
import io.github.luigidemasi.camelkit.output.Printer;
import io.github.luigidemasi.camelkit.service.InitGraphSummary;
import io.github.luigidemasi.camelkit.service.InitProgress;
import io.github.luigidemasi.camelkit.service.InitReporter;
import io.github.luigidemasi.camelkit.service.InitRequest;
import io.github.luigidemasi.camelkit.service.InitService;
import io.github.luigidemasi.camelkit.service.InitWarning;
import io.github.luigidemasi.camelkit.tui.InitTuiView;
import io.github.luigidemasi.camelkit.tui.TaskTracker;
import io.github.luigidemasi.camelkit.util.PrerequisiteChecker;

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

    @Option(names = {"-a", "--ai"},
            description = "AI agent: bob2 (IBM Bob 2, default), bob (IBM Bob 1 legacy), "
                          + "gemini, claude, copilot, qwen, opencode",
            defaultValue = "bob2")
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

    private final InitService initService;
    private Path resolvedTargetDir;

    public InitCommand(CamelKitMain main) {
        this(main, new InitService());
    }

    InitCommand(CamelKitMain main, InitService initService) {
        super(main);
        this.initService = initService;
    }

    @Override
    public Integer doCall() throws Exception {
        if (silent) {
            main.setOut(Printer.noop());
        }

        if (configFile != null || properties != null) {
            CamelKitMain.reloadDistribution(configFile, properties);
        }

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

        if (here) {
            resolvedTargetDir = Path.of("").toAbsolutePath();
            projectName = resolvedTargetDir.getFileName().toString();
        } else {
            resolvedTargetDir = Path.of(projectName).toAbsolutePath();
        }

        Path agentsMd = resolvedTargetDir.resolve("AGENTS.md");
        Path camelKitDir = resolvedTargetDir.resolve(".camel-kit");
        if (!force && (Files.exists(agentsMd) || Files.isDirectory(camelKitDir))) {
            if (!silent)
                main.printBanner();
            printer().println();
            printer().println(yellow("\u26A0") + bold(" Project already initialized"));
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
            if (main.isTuiEnabled() && TerminalImageCapabilities.detect().supportsNativeImages()) {
                InitTuiView tui = new InitTuiView();
                Printer original = main.getOut();
                main.setOut(tui.createPrinter());
                main.setTaskTracker(tui.createTaskTracker());
                main.closeTerminal();
                try {
                    return tui.run(this::doInitWork);
                } catch (Throwable e) {
                    // InitTuiView.run only throws during setup, before any init work starts,
                    // so falling back to plain output cannot double-run the init.
                    main.setOut(original);
                    main.setTaskTracker(TaskTracker.noop());
                    printer().println(yellow("Warning: TUI unavailable (" + e.getMessage()
                                             + ") — continuing without it"));
                }
            }

            main.printBanner();
        }

        return doInitWork();
    }

    private Integer doInitWork() throws Exception {
        if (!silent) {
            PrerequisiteChecker.check(printer());
        }

        InitRequest request = new InitRequest(
                projectName,
                ai,
                resolvedTargetDir,
                citrusVersion,
                noFetch,
                sourcePlatform,
                detectCommandPrefix(),
                CamelKitMain.DEFAULT_CITRUS_VERSION,
                CamelKitMain.distribution(),
                printer(),
                initProgress(),
                initReporter());

        var result = initService.initialize(request);
        printSummary(result.projectName(), result.agent().name(), result.citrusSchemaCount());
        printNextSteps(result.projectName(), result.agent().name(), result.agentName());
        return 0;
    }

    void printNextSteps(String projectName, String agentName, String agentId) {
        String divider = "  " + "\u2500".repeat(34);
        printer().println("  " + bold("Next steps"));
        printer().println(divider);
        printer().println("  1  Open " + cyan(projectName) + " in " + agentName);
        if (AgentGeneratorStrategy.COPILOT.descriptorValue().equalsIgnoreCase(agentId)) {
            printer().println("  2  Ask Copilot: " + cyan("\"Use the /camel-start skill.\""));
            printer().println("     Run " + cyan("/skills list") + " to inspect available project skills");
            printer().println("  3  Use " + cyan("/mcp show") + " to verify Camel Kit MCP servers");
            printer().println();
            return;
        }
        printer().println("  2  " + cyan("/camel-start") + "  \u2014 route integration work");
        printer().println("     " + cyan("/camel-migrate") + " \u2014 migration shortcut");
        printer().println("  3  " + cyan("/camel-debug") + "  \u2014 troubleshoot broken routes");
        printer().println("     " + cyan("/camel-knowledge") + " \u2014 ask Camel documentation questions");
        printer().println();
    }

    private InitProgress initProgress() {
        return new InitProgress() {
            @Override
            public void startTask(String icon, String label) {
                main.getTaskTracker().startTask(icon, label);
            }

            @Override
            public void finishTask() {
                main.getTaskTracker().finishTask();
            }
        };
    }

    private InitReporter initReporter() {
        return new InitReporter() {
            @Override
            public void mavenWrapperCreated(String mavenVersion) {
                printer().println(green("\u2713") + " Maven Wrapper created (portable Maven " + mavenVersion + ")");
            }

            @Override
            public void graphBuilt(InitGraphSummary graph) {
                printer().println(green("\u2713") + " Project graph: " + graph.nodeCount()
                                  + " nodes, " + graph.edgeCount() + " edges");
                if (graph.detectedProjectType() != null && !graph.detectedProjectType().isEmpty()) {
                    printer().println("     Detected: " + cyan(graph.detectedProjectType()));
                }
            }

            @Override
            public void graphSkipped() {
                printer().println(yellow("  No source files found \u2014 graph skipped"));
            }

            @Override
            public void warning(InitWarning warning) {
                printer().println(yellow("  Warning: " + warning.message()));
            }
        };
    }

    private void printSummary(String projectName, String agentName, int citrusSchemaCount) {
        printer().println();
        printer().print("  " + green("\u2713") + "  ");
        printer().println(bold(projectName));
        String meta = "     " + agentName
                      + (citrusSchemaCount > 0 ? "  \u00b7  " + citrusSchemaCount + " schemas" : "");
        printer().println(meta);
        printer().println();
    }

    private String detectCommandPrefix() {
        String cmdLine = ProcessHandle.current().info().commandLine().orElse("");
        if (cmdLine.contains("camel-kit"))
            return "camel-kit";
        return "camel kit";
    }
}
