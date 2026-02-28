package io.github.luigidemasi.camelkit.output;

/**
 * Abstraction for console output (following Camel JBang pattern).
 */
public interface Printer {
    void println();
    void println(String line);
    void print(String output);

    default void printErr(String message) {
        System.err.println(message);
    }

    /** No-op implementation that discards all output (used in silent mode). */
    static Printer noop() {
        return new Printer() {
            @Override public void println() {}
            @Override public void println(String line) {}
            @Override public void print(String output) {}
            @Override public void printErr(String message) {}
        };
    }
}
