package io.github.luigidemasi.camelkit;

import io.github.luigidemasi.camelkit.command.InitCommand;
import io.github.luigidemasi.camelkit.output.JLinePrinter;
import io.github.luigidemasi.camelkit.output.Printer;
import io.github.luigidemasi.camelkit.output.SystemPrinter;
import io.github.luigidemasi.camelkit.util.AnsiColors;
import io.github.luigidemasi.camelkit.util.TemplateUtils;

import java.io.IOException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

/**
 * Camel-Kit CLI - Design Apache Camel integrations with AI coding assistants.
 */
@Command(
    name = "camel-kit",
    mixinStandardHelpOptions = true,
    version = "0.2.0-SNAPSHOT",
    description = "Design Apache Camel integrations with AI coding assistants")
public class CamelKitMain implements Callable<Integer> {

    public static final String LATEST_CAMEL_LTS_VERSION = "4.18.0";
    public static final String DEFAULT_CITRUS_VERSION = "4.9.2";

    private Terminal terminal;
    private Printer printer;

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

    public Terminal getTerminal() {
        return terminal;
    }

    public static void main(String[] args) {
        run(args);
    }

    public static void run(String... args) {
        run(new CamelKitMain(), args);
    }

    public static void run(CamelKitMain main, String... args) {
        CommandLine commandLine = new CommandLine(main)
            .addSubcommand("init", new InitCommand(main));

        int exitCode = commandLine.execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() {
        printBanner();
        CommandLine.usage(this, System.out);
        return 0;
    }

    /**
     * Print the Camel-Kit banner with gradient colors.
     */
    public void printBanner() {
        String[] camelLines;
        String[] bannerLines;

        try {
            camelLines = TemplateUtils.readLines("art/camelLines.txt");
            bannerLines = TemplateUtils.readLines("art/bannerLines.txt");
        } catch (IOException e) {
            // Fallback to empty if resources not found
            camelLines = new String[]{""};
            bannerLines = new String[]{""};
        }

        printer.println();
        printCenteredBlock(camelLines, AnsiColors.CAMEL_GRADIENT);
        printer.println();
        printCenteredBlock(bannerLines, AnsiColors.CAMEL_GRADIENT);
        printer.println();

        // Center the tagline
        String tagline = "Camel-Kit - Design Apache Camel Integrations with AI";
        int terminalWidth = terminal != null ? terminal.getWidth() : 80;
        int padding = Math.max(0, (terminalWidth - tagline.length()) / 2);

        AttributedStyle taglineStyle = AttributedStyle.DEFAULT.italic().foregroundRgb(0xF4AF23);
        printer.print(" ".repeat(padding));
        printer.println(new AttributedString(tagline, taglineStyle).toAnsi(terminal));
        printer.println();
    }

    /**
     * Prints a block of text centered on the terminal screen.
     * Calculates padding once based on the longest line to preserve the block's shape.
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
