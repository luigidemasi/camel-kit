package io.github.luigidemasi.camelkit.util;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.luigidemasi.camelkit.output.Printer;

/**
 * Checks whether prerequisite tools (Java, JBang, Camel JBang, Camel test plugin) are available on the system. The
 * check is non-blocking: it prints warnings but never fails the init.
 */
public final class PrerequisiteChecker {

    private static final Duration PREREQUISITE_TIMEOUT = Duration.ofSeconds(5);
    private static final int MIN_JAVA_VERSION = 17;

    private PrerequisiteChecker() {
        // Utility class
    }

    /**
     * Run all prerequisite checks and print a formatted summary.
     */
    public static void check(Printer printer) {
        List<CheckResult> results = new ArrayList<>();

        results.add(checkJava());
        results.add(checkJBang());
        results.add(checkCamelJBang());
        results.add(checkCamelTestPlugin());

        printer.println(AnsiColors.bold("Prerequisites:"));
        for (CheckResult r : results) {
            String icon = r.found ? AnsiColors.green("✓") : AnsiColors.red("✗");
            String detail;
            if (r.found) {
                detail = AnsiColors.dim("(" + r.version + ")");
            } else {
                String msg = "not found";
                if (r.hint != null) {
                    msg += " — " + AnsiColors.yellow(r.hint);
                }
                detail = AnsiColors.red("(" + msg + ")");
            }
            printer.println(String.format(Locale.ROOT, "  %-19s %s %s", r.name, icon, detail));
        }
        printer.println();
    }

    // ------------------------------------------------------------------
    // Individual checks
    // ------------------------------------------------------------------

    private static CheckResult checkJava() {
        ProcessRunner.Result result = ProcessRunner.run(PREREQUISITE_TIMEOUT, "java", "-version");
        if (!result.completed()) {
            return new CheckResult("Java 17+", false, null, null);
        }

        String version = parseJavaVersion(result.output());
        if (version != null) {
            int major = parseMajorVersion(version);
            if (major >= MIN_JAVA_VERSION) {
                return new CheckResult("Java 17+", true, version, null);
            }
            return new CheckResult("Java 17+", false, null, "found " + version + ", need 17+");
        }
        return new CheckResult("Java 17+", false, null, null);
    }

    private static CheckResult checkJBang() {
        return runSimpleCheck("JBang", new String[]{"jbang", "version"}, false);
    }

    private static CheckResult checkCamelJBang() {
        return runSimpleCheck("Camel JBang", new String[]{"camel", "--version"}, false);
    }

    private static CheckResult checkCamelTestPlugin() {
        CheckResult result = runSimpleCheck("Camel test plugin", new String[]{"camel", "test", "--help"}, true);
        if (!result.found) {
            return new CheckResult(result.name, false, null, missingCamelTestPluginHint());
        }
        return result;
    }

    static String missingCamelTestPluginHint() {
        return "runtime verification will skip Citrus test phase";
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static CheckResult runSimpleCheck(String name, String[] command, boolean ignoreOutput) {
        ProcessRunner.Result result = ProcessRunner.run(PREREQUISITE_TIMEOUT, command);
        if (!result.completed()) {
            return new CheckResult(name, false, null, null);
        }

        if (result.succeeded()) {
            String version = ignoreOutput ? "installed" : extractFirstLine(result.output());
            return new CheckResult(name, true, version, null);
        }
        return new CheckResult(name, false, null, null);
    }

    /**
     * Parse the Java version from {@code java -version} output. Handles both the old format
     * ({@code java version "1.8.0_xxx"}) and the modern format ({@code openjdk version "17.0.1"}).
     */
    public static String parseJavaVersion(String output) {
        Pattern pattern = Pattern.compile("version\\s+\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(output);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * Extract the major version number. Handles {@code 1.8.0_xxx} (returns 8) and {@code 17.0.1} (returns 17).
     */
    public static int parseMajorVersion(String version) {
        if (version.startsWith("1.")) {
            // Old-style: 1.8.0_xxx → 8
            String[] parts = version.split("\\.");
            if (parts.length >= 2) {
                try {
                    return Integer.parseInt(parts[1]);
                } catch (NumberFormatException e) {
                    return 0;
                }
            }
        }
        // Modern: 17.0.1 → 17
        String[] parts = version.split("[.+\\-]");
        try {
            return Integer.parseInt(parts[0]);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String extractFirstLine(String output) {
        if (output == null || output.isEmpty()) {
            return "installed";
        }
        String firstLine = output.lines().findFirst().orElse("").trim();
        return firstLine.isEmpty() ? "installed" : firstLine;
    }

    // ------------------------------------------------------------------
    // Result record
    // ------------------------------------------------------------------

    private static final class CheckResult {
        final String name;
        final boolean found;
        final String version;
        final String hint;

        CheckResult(String name, boolean found, String version, String hint) {
            this.name = name;
            this.found = found;
            this.version = version;
            this.hint = hint;
        }
    }
}
