package io.github.luigidemasi.camelkit.ship.artifact;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Canonical, version-bound dependency contract for controller-run Citrus YAML tests. */
public final class CitrusDependencyPolicy {

    public static final String FORBIDDEN_JBANG_PROPERTIES_PATH = "test/jbang.properties";
    public static final String FORBIDDEN_JBANG_ARTIFACT_KIND = "citrus-jbang-properties";

    private static final int MAX_DEPENDENCIES = 64;
    private static final Pattern VERSION = Pattern.compile(
            "(?:0|[1-9][0-9]*)(?:\\.[0-9A-Za-z][0-9A-Za-z_-]*)+(?:-[0-9A-Za-z][0-9A-Za-z.-]*)?");
    private static final Pattern ARTIFACT_ID = Pattern.compile("[a-z0-9][a-z0-9.-]*");

    private CitrusDependencyPolicy() {
    }

    public static List<String> required(String citrusVersion) {
        return List.of(
                coordinate("citrus-camel", citrusVersion),
                coordinate("citrus-junit-jupiter", citrusVersion),
                coordinate("citrus-yaml", citrusVersion));
    }

    public static List<String> violations(String citrusVersion, List<String> dependencies) {
        List<String> violations = new ArrayList<>();
        if (!validVersion(citrusVersion)) {
            violations.add("Citrus version must be an explicit immutable release version");
            return List.copyOf(violations);
        }
        List<String> values = dependencies == null ? List.of() : dependencies;
        if (values.size() > MAX_DEPENDENCIES) {
            violations.add("Citrus dependency policy contains more than " + MAX_DEPENDENCIES + " coordinates");
        }
        boolean containsNull = values.stream().anyMatch(java.util.Objects::isNull);
        if (containsNull) {
            violations.add("Citrus dependencies must not contain null coordinates");
        } else if (!values.equals(values.stream().sorted().toList())) {
            violations.add("Citrus dependencies must be sorted canonically");
        }
        if (new HashSet<>(values).size() != values.size()) {
            violations.add("Citrus dependencies must not contain duplicates");
        }
        for (String value : values) {
            if (!validCoordinate(value, citrusVersion)) {
                violations.add("Invalid or unpinned Citrus dependency: " + value);
            }
        }
        List<String> required = required(citrusVersion);
        if (!required.equals(values)) {
            violations.add("Citrus dependencies must equal the controller-approved base coordinates: " + required);
        }
        return List.copyOf(violations);
    }

    public static boolean validVersion(String value) {
        if (value == null || !VERSION.matcher(value).matches()) {
            return false;
        }
        String normalized = value.toUpperCase(Locale.ROOT);
        return !normalized.contains("SNAPSHOT")
                && !normalized.equals("LATEST")
                && !normalized.equals("RELEASE");
    }

    private static boolean validCoordinate(String value, String citrusVersion) {
        if (value == null || value.indexOf(' ') >= 0 || value.indexOf('\t') >= 0
                || value.indexOf('$') >= 0 || value.indexOf('[') >= 0 || value.indexOf('(') >= 0) {
            return false;
        }
        String[] parts = value.split(":", -1);
        return parts.length == 3
                && "org.citrusframework".equals(parts[0])
                && ARTIFACT_ID.matcher(parts[1]).matches()
                && citrusVersion.equals(parts[2]);
    }

    private static String coordinate(String artifactId, String version) {
        return "org.citrusframework:" + artifactId + ':' + version;
    }
}
