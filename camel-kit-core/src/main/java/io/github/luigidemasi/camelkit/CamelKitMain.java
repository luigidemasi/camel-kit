package io.github.luigidemasi.camelkit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import io.github.luigidemasi.camelkit.command.DoctorCommand;
import io.github.luigidemasi.camelkit.command.InitCommand;
import io.github.luigidemasi.camelkit.command.doc.DocCommand;
import io.github.luigidemasi.camelkit.command.graph.GraphCommand;
import io.github.luigidemasi.camelkit.command.pipeline.NextIdCommand;
import io.github.luigidemasi.camelkit.command.plan.PlanCommand;
import io.github.luigidemasi.camelkit.command.ship.ShipCommand;
import io.github.luigidemasi.camelkit.config.DistributionConfig;
import io.github.luigidemasi.camelkit.output.JLinePrinter;
import io.github.luigidemasi.camelkit.output.Printer;
import io.github.luigidemasi.camelkit.output.SystemPrinter;
import io.github.luigidemasi.camelkit.util.AnsiColors;
import io.github.luigidemasi.camelkit.util.LogoRenderer;
import io.github.luigidemasi.camelkit.util.TemplateUtils;

import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * Camel-Kit CLI - Design Apache Camel integrations with AI coding assistants.
 */
@Command(
         name = "camel-kit",
         mixinStandardHelpOptions = true,
         versionProvider = VersionProvider.class,
         description = "Design Apache Camel integrations with AI coding assistants")
public class CamelKitMain implements Callable<Integer> {

    private static DistributionConfig DISTRIBUTION = loadDistribution();

    public static String LATEST_CAMEL_LTS_VERSION = DISTRIBUTION.camelMainVersion();
    public static String CAMEL_MCP_VERSION = DISTRIBUTION.camelMcpVersion();
    public static String DEFAULT_CITRUS_VERSION = DISTRIBUTION.citrusVersion();
    public static String KNOWLEDGE_MCP_VERSION = DISTRIBUTION.knowledgeMcpVersion();
    public static String CITRUS_MCP_VERSION = DISTRIBUTION.citrusMcpVersion();
    public static String CAMEL_MCP_REPOS = DISTRIBUTION.camelMcpRepos();
    public static String KNOWLEDGE_MCP_REPOS = DISTRIBUTION.knowledgeMcpRepos();
    public static String CITRUS_MCP_REPOS = DISTRIBUTION.citrusMcpRepos();
    public static String CAMEL_CATALOG_REPOS = DISTRIBUTION.camelCatalogRepos();
    private Terminal terminal;
    private Printer printer;
    private boolean tuiEnabled = true;
    private io.github.luigidemasi.camelkit.tui.TaskTracker taskTracker
            = io.github.luigidemasi.camelkit.tui.TaskTracker.noop();

    public CamelKitMain() {
        try {
            this.terminal = TerminalBuilder.builder()
                    .system(true)
                    .build();
            this.printer = new JLinePrinter(terminal);
        } catch (Exception e) {
            // Fallback to system printer
            this.printer = new SystemPrinter();
        }
    }

    public Printer getOut() {
        return printer;
    }

    public void setOut(Printer printer) {
        this.printer = printer;
    }

    public io.github.luigidemasi.camelkit.tui.TaskTracker getTaskTracker() {
        return taskTracker;
    }

    public void setTaskTracker(io.github.luigidemasi.camelkit.tui.TaskTracker tracker) {
        this.taskTracker = tracker;
    }

    /** Disable the TUI split-screen experience (e.g. when running as a JBang plugin). */
    public void disableTui() {
        this.tuiEnabled = false;
    }

    public boolean isTuiEnabled() {
        return tuiEnabled;
    }

    /**
     * Release the JLine terminal so that another framework (e.g. TamboUI) can acquire the terminal device. Safe to call
     * multiple times.
     */
    public void closeTerminal() {
        if (terminal != null) {
            try {
                terminal.close();
            } catch (Exception ignored) {
            }
            terminal = null;
        }
    }

    public Terminal getTerminal() {
        return terminal;
    }

    private static DistributionConfig loadDistribution() {
        Path distFile = Path.of("distribution.properties");
        if (Files.exists(distFile)) {
            return DistributionConfig.loadFromFileWithClasspathBaseline(distFile);
        }
        return DistributionConfig.loadFromClasspathOrDefaults();
    }

    public static DistributionConfig distribution() {
        return DISTRIBUTION;
    }

    /**
     * Reload the distribution config with cascading overrides. Called by InitCommand when -c or -p options are
     * provided.
     */
    public static void reloadDistribution(java.nio.file.Path configFile, java.util.List<String> cliProperties) {
        DISTRIBUTION = DistributionConfig.loadWithOverrides(configFile, cliProperties);
        LATEST_CAMEL_LTS_VERSION = DISTRIBUTION.camelMainVersion();
        CAMEL_MCP_VERSION = DISTRIBUTION.camelMcpVersion();
        DEFAULT_CITRUS_VERSION = DISTRIBUTION.citrusVersion();
        KNOWLEDGE_MCP_VERSION = DISTRIBUTION.knowledgeMcpVersion();
        CITRUS_MCP_VERSION = DISTRIBUTION.citrusMcpVersion();
        CAMEL_MCP_REPOS = DISTRIBUTION.camelMcpRepos();
        KNOWLEDGE_MCP_REPOS = DISTRIBUTION.knowledgeMcpRepos();
        CITRUS_MCP_REPOS = DISTRIBUTION.citrusMcpRepos();
        CAMEL_CATALOG_REPOS = DISTRIBUTION.camelCatalogRepos();
    }

    public static void main(String[] args) {
        run(args);
    }

    public static void run(String... args) {
        run(new CamelKitMain(), args);
    }

    public static void run(CamelKitMain main, String... args) {
        int exitCode = commandLine(main, args).execute(args);
        System.exit(exitCode);
    }

    static CommandLine commandLine(CamelKitMain main, String... args) {
        CommandLine commandLine = new CommandLine(main)
                .addSubcommand("init", new InitCommand(main))
                .addSubcommand("doctor", new DoctorCommand(main))
                .addSubcommand("doc", new CommandLine(new DocCommand()))
                .addSubcommand("graph", new CommandLine(new GraphCommand()))
                .addSubcommand("plan", new CommandLine(new PlanCommand()))
                .addSubcommand("nextId", new NextIdCommand())
                .addSubcommand("ship", new CommandLine(new ShipCommand()));

        // Picocli expands @files at the root before dispatching; only direct Ship invocations need literals.
        if (args.length > 0 && "ship".equals(args[0])) {
            commandLine.setExpandAtFiles(false);
        }
        return commandLine;
    }

    @Override
    public Integer call() {
        printBanner();
        CommandLine.usage(this, System.out);
        return 0;
    }

    /**
     * Print the Camel-Kit banner.
     *
     * <p>
     * Tries to render the logo image using a native terminal image protocol (Kitty, iTerm2, Sixel). Falls back to ASCII
     * art + gradient text when the current terminal does not support any native image protocol.
     */
    public void printBanner() {
        int terminalWidth = terminal != null ? terminal.getWidth() : 80;
        int terminalHeight = terminal != null ? terminal.getHeight() : 24;

        printer.println();

        boolean imageRendered = LogoRenderer.tryRender(System.out, terminalWidth, terminalHeight);

        if (!imageRendered) {
            // ASCII art fallback
            String[] camelLines;
            String[] bannerLines;
            try {
                camelLines = TemplateUtils.readLines("art/camelLines.txt");
                bannerLines = TemplateUtils.readLines("art/bannerLines.txt");
            } catch (IOException e) {
                camelLines = new String[]{""};
                bannerLines = new String[]{""};
            }
            printCenteredBlock(camelLines, AnsiColors.CAMEL_GRADIENT);
            printer.println();
            printCenteredBlock(bannerLines, AnsiColors.CAMEL_GRADIENT);
        }

        printer.println();

        // Tagline — always shown
        String tagline = "Camel-Kit \u2014 Design Apache Camel Integrations with AI";
        int padding = Math.max(0, (terminalWidth - tagline.length()) / 2);
        String pad = " ".repeat(padding);

        AttributedStyle dimStyle = AttributedStyle.DEFAULT.foregroundRgb(0x888888);
        AttributedStyle mainStyle = AttributedStyle.DEFAULT.bold().foregroundRgb(0xF4AF23);

        // "Camel-Kit — " dimmed, rest in amber
        int splitAt = "Camel-Kit \u2014 ".length();
        printer.print(pad);
        printer.print(new AttributedString(tagline.substring(0, splitAt), dimStyle).toAnsi(terminal));
        printer.println(new AttributedString(tagline.substring(splitAt), mainStyle).toAnsi(terminal));
        printer.println();
    }

    /**
     * Prints a block of text centered on the terminal screen. Calculates padding once based on the longest line to
     * preserve the block's shape.
     */
    private void printCenteredBlock(String[] lines, int[] colors) {
        int terminalWidth = (terminal != null && terminal.getWidth() > 0) ? terminal.getWidth() : 100;

        // Find max width of the block
        int maxBlockWidth = 0;
        for (String line : lines) {
            maxBlockWidth = Math.max(maxBlockWidth, line.length());
        }

        int leftPadding = Math.max(0, (terminalWidth - maxBlockWidth) / 2);
        String paddingStr = " ".repeat(leftPadding);

        for (int i = 0; i < lines.length; i++) {
            int color = colors[i % colors.length];
            AttributedStyle style = AttributedStyle.DEFAULT.foregroundRgb(color);
            printer.print(paddingStr);
            printer.println(new AttributedString(lines[i], style).toAnsi(terminal));
        }
    }
}
