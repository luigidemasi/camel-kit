package io.github.luigidemasi.camelkit.util;

/**
 * ANSI color code utilities for terminal output.
 */
public final class AnsiColors {

    // Camel brand colors (gradient from yellow to brown)
    public static final int[] CAMEL_GRADIENT = {
            0xF4AF23, 0xEC7826, 0xD4691A, 0xB86B1B, 0x995B35, 0x7A4A2A
    };

    private static final String RESET = "\u001B[0m";
    private static final String GREEN = "\u001B[32m";
    private static final String RED = "\u001B[31m";
    private static final String YELLOW = "\u001B[33m";
    private static final String CYAN = "\u001B[36m";
    private static final String BOLD = "\u001B[1m";
    private static final String DIM = "\u001B[2m";

    private AnsiColors() {
        // Utility class
    }

    public static String green(String s) {
        return GREEN + s + RESET;
    }

    public static String red(String s) {
        return RED + s + RESET;
    }

    public static String yellow(String s) {
        return YELLOW + s + RESET;
    }

    public static String cyan(String s) {
        return CYAN + s + RESET;
    }

    public static String bold(String s) {
        return BOLD + s + RESET;
    }

    public static String dim(String s) {
        return DIM + s + RESET;
    }
}
