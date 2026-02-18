package io.github.luigidemasi.camelkit.output;

import org.jline.terminal.Terminal;

/**
 * JLine-based printer with terminal support for colors and formatting.
 */
public class JLinePrinter implements Printer {

    private final Terminal terminal;

    public JLinePrinter(Terminal terminal) {
        this.terminal = terminal;
    }

    @Override
    public void println() {
        terminal.writer().println();
        terminal.writer().flush();
    }

    @Override
    public void println(String line) {
        terminal.writer().println(line);
        terminal.writer().flush();
    }

    @Override
    public void print(String output) {
        terminal.writer().print(output);
        terminal.writer().flush();
    }
}
