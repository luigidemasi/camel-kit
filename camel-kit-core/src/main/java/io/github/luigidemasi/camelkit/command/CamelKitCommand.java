package io.github.luigidemasi.camelkit.command;

import io.github.luigidemasi.camelkit.CamelKitMain;
import io.github.luigidemasi.camelkit.output.Printer;
import io.github.luigidemasi.camelkit.util.AnsiColors;
import picocli.CommandLine;

import java.util.concurrent.Callable;

/**
 * Base class for all Camel-Kit commands (following Camel JBang CamelCommand pattern).
 */
public abstract class CamelKitCommand implements Callable<Integer> {

    @CommandLine.Option(names = {"-h", "--help"}, usageHelp = true,
                        description = "Display help")
    boolean helpRequested;

    protected final CamelKitMain main;

    protected CamelKitCommand(CamelKitMain main) {
        this.main = main;
    }

    protected Printer printer() {
        return main.getOut();
    }

    @Override
    public Integer call() throws Exception {
        return doCall();
    }

    /**
     * Implement command logic in subclasses.
     */
    public abstract Integer doCall() throws Exception;

    // Convenience methods for colored output
    protected String green(String s) {
        return AnsiColors.green(s);
    }

    protected String red(String s) {
        return AnsiColors.red(s);
    }

    protected String yellow(String s) {
        return AnsiColors.yellow(s);
    }

    protected String cyan(String s) {
        return AnsiColors.cyan(s);
    }

    protected String bold(String s) {
        return AnsiColors.bold(s);
    }

    protected String dim(String s) {
        return AnsiColors.dim(s);
    }
}
