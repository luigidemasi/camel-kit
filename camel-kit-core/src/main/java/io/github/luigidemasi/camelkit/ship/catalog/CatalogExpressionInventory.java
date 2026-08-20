package io.github.luigidemasi.camelkit.ship.catalog;

import java.util.Locale;
import java.util.Set;

import io.github.luigidemasi.camelkit.ship.expression.ShipExpressionPolicy;

/** Exact Camel YAML selector inventory layered over Ship's bounded Simple expression gate. */
public final class CatalogExpressionInventory {

    public static final String SIMPLE = "simple";
    public static final String GENERIC = "language";
    public static final String METHOD = "method";

    private static final Set<String> YAML_ALIASES = Set.of(
            "constant", "csimple", "datasonnet", "exchangeProperty", "groovy", "header",
            "hl7terser", "java", "joor", "jq", "js", "jsonpath", GENERIC, METHOD, "mvel",
            "ognl", "python", "ref", SIMPLE, "spel", "tokenize", "variable", "wasm", "xpath",
            "xquery", "xtokenize");
    private static final Set<String> CATALOG_LANGUAGES = Set.of(
            "bean", "constant", "csimple", "datasonnet", "exchangeProperty", "file", "groovy",
            "header", "hl7terser", "java", "joor", "jq", "js", "jsonpath", "mvel", "ognl",
            "python", "ref", SIMPLE, "spel", "tokenize", "variable", "wasm", "xpath", "xquery",
            "xtokenize");

    private CatalogExpressionInventory() {
    }

    public static Set<String> yamlAliases() {
        return YAML_ALIASES;
    }

    public static Set<String> catalogLanguages() {
        return CATALOG_LANGUAGES;
    }

    public static boolean isKnownAlias(String value) {
        return YAML_ALIASES.contains(value) || CATALOG_LANGUAGES.contains(value);
    }

    public static boolean isRejectedAlias(String value) {
        return isKnownAlias(value) && !SIMPLE.equals(value) && !GENERIC.equals(value);
    }

    public static boolean isNonExactAlias(String value) {
        if (value == null || isKnownAlias(value)) {
            return false;
        }
        String normalized = normalize(value);
        return YAML_ALIASES.stream().anyMatch(alias -> normalize(alias).equals(normalized))
                || CATALOG_LANGUAGES.stream().anyMatch(alias -> normalize(alias).equals(normalized));
    }

    /** Syntax permission only; the exact runtime launcher remains the compatibility authority. */
    public static boolean isSafeSimple(String value) {
        return ShipExpressionPolicy.isSafeSimple(value);
    }

    private static String normalize(String value) {
        return value.replace("-", "").replace("_", "").toLowerCase(Locale.ROOT);
    }
}
