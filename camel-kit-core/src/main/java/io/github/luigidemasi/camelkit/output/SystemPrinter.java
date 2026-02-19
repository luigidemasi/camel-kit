package io.github.luigidemasi.camelkit.output;

/**
 * Standard System.out printer implementation.
 */
public class SystemPrinter implements Printer {

    @Override
    public void println() {
        System.out.println();
    }

    @Override
    public void println(String line) {
        System.out.println(line);
    }

    @Override
    public void print(String output) {
        System.out.print(output);
    }
}
