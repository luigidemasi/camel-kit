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
}
