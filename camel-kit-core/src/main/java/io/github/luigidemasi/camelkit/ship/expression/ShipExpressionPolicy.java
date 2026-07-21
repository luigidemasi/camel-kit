package io.github.luigidemasi.camelkit.ship.expression;

/**
 * Pure, bounded permission policy for the YAML expression forms and Simple-language subset accepted by Ship.
 *
 * <p>
 * Schema-aware YAML traversal must determine whether a scalar is a template or a predicate before calling the matching
 * method here. This class does not validate YAML shape or scalar typing. It establishes syntax permission only; catalog
 * availability and runtime compatibility remain separate, exact-version evidence checks. Unicode format-control
 * classification is frozen to Unicode 16.0 so results do not vary with the running JDK.
 * </p>
 */
public final class ShipExpressionPolicy {

    private static final int MAX_EXPRESSION_UTF8_BYTES = 16_384;
    private static final String SIMPLE = "simple";
    private static final String GENERIC = "language";
    private static final String[] COMPARISON_OPERATORS = {
            "!startsWith", "!endsWith", "!contains", "startsWith", "endsWith", "contains",
            "!=~", ">=", "<=", "==", "!=", "=~", ">", "<"
    };
    // Inclusive, sorted Unicode 16.0 General_Category=Cf ranges.
    private static final int[] UNICODE_16_FORMAT_RANGES = {
            0x00ad, 0x00ad,
            0x0600, 0x0605,
            0x061c, 0x061c,
            0x06dd, 0x06dd,
            0x070f, 0x070f,
            0x0890, 0x0891,
            0x08e2, 0x08e2,
            0x180e, 0x180e,
            0x200b, 0x200f,
            0x202a, 0x202e,
            0x2060, 0x2064,
            0x2066, 0x206f,
            0xfeff, 0xfeff,
            0xfff9, 0xfffb,
            0x110bd, 0x110bd,
            0x110cd, 0x110cd,
            0x13430, 0x1343f,
            0x1bca0, 0x1bca3,
            0x1d173, 0x1d17a,
            0xe0001, 0xe0001,
            0xe0020, 0xe007f
    };

    private ShipExpressionPolicy() {
    }

    /**
     * Returns whether {@code selector} is exactly {@code simple}. This classifies a selector string only; YAML shape
     * and scalar typing are validated separately.
     */
    public static boolean isDirectSimpleSelector(String selector) {
        return SIMPLE.equals(selector);
    }

    /**
     * Returns whether the selector is exactly {@code language} and its language is exactly {@code simple}. This does
     * not validate the surrounding YAML object.
     */
    public static boolean isGenericSimpleSelector(String selector, String language) {
        return GENERIC.equals(selector) && SIMPLE.equals(language);
    }

    /**
     * Returns whether the value is a bounded Simple template containing only approved literal text and lookups. The
     * lookup set is {@code body}, {@code routeId}, {@code exchangeId}, {@code messageTimestamp}, and single-level ASCII
     * keys under {@code header}, {@code headers}, {@code exchangeProperty}, {@code variable}, or {@code variables}.
     * Plural {@code headers} and {@code variables} reject keys {@code size} and {@code length}, which Camel interprets
     * as collection functions. Backslashes, property/init expansion markers, and active Elvis, chain, or unary syntax
     * are not approved literal text.
     */
    public static boolean isSafeSimpleTemplate(String value) {
        if (!isBoundedText(value) || hasIndirectExpansion(value)
                || value.contains("~>") || value.contains("?:") || hasTemplateUnaryOperator(value)) {
            return false;
        }

        int cursor = 0;
        while (true) {
            int start = value.indexOf("${", cursor);
            if (start < 0) {
                return true;
            }
            int end = value.indexOf('}', start + 2);
            if (end < 0 || !isAllowedLookup(value.substring(start + 2, end))) {
                return false;
            }
            cursor = end + 1;
        }
    }

    /**
     * Returns whether the value conforms to Ship's closed, comparison-only Simple predicate grammar. Comparisons use
     * one ASCII space around operators, single-quoted scalars, and at most one logical operator kind. Permitted
     * comparison operators are {@code ==}, {@code !=}, {@code =~}, {@code !=~}, ordering, {@code contains},
     * {@code startsWith}, and {@code endsWith}, including their explicit negated forms. The right operand may be an
     * approved lookup, an unescaped single-quoted scalar, a canonical finite decimal or signed-long integer, lowercase
     * {@code true}/{@code false}, or {@code null}. The {@code =~} pair means case-insensitive equality, not
     * regular-expression matching.
     */
    public static boolean isSafeSimplePredicate(String value) {
        return isBoundedText(value) && !hasIndirectExpansion(value) && new PredicateParser(value).parse();
    }

    private static boolean isBoundedText(String value) {
        if (value == null) {
            return false;
        }
        int utf8Bytes = 0;
        for (int offset = 0; offset < value.length();) {
            char first = value.charAt(offset);
            int codePoint;
            int width;
            if (first <= 0x7f) {
                codePoint = first;
                width = 1;
            } else if (first <= 0x7ff) {
                codePoint = first;
                width = 2;
            } else if (Character.isHighSurrogate(first)) {
                if (offset + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(offset + 1))) {
                    return false;
                }
                codePoint = Character.toCodePoint(first, value.charAt(offset + 1));
                width = 4;
                offset++;
            } else if (Character.isLowSurrogate(first)) {
                return false;
            } else {
                codePoint = first;
                width = 3;
            }

            if (isForbiddenCodePoint(codePoint) || utf8Bytes > MAX_EXPRESSION_UTF8_BYTES - width) {
                return false;
            }
            utf8Bytes += width;
            offset++;
        }
        return true;
    }

    private static boolean hasIndirectExpansion(String value) {
        return value.contains("$simple{")
                || value.contains("$init{")
                || value.contains("}init$")
                || value.contains("{{")
                || value.contains("}}")
                || value.indexOf('\\') >= 0;
    }

    private static boolean hasTemplateUnaryOperator(String value) {
        for (int closingBrace = value.indexOf('}');
             closingBrace >= 0;
             closingBrace = value.indexOf('}', closingBrace + 1)) {
            int operator = closingBrace + 1;
            if ((value.startsWith("++", operator) || value.startsWith("--", operator))
                    && (operator + 2 == value.length() || value.charAt(operator + 2) == ' ')) {
                return true;
            }
        }
        return false;
    }

    private static boolean isForbiddenCodePoint(int codePoint) {
        if (codePoint <= 0x1f || codePoint >= 0x7f && codePoint <= 0x9f
                || codePoint == 0x2028 || codePoint == 0x2029) {
            return true;
        }
        for (int index = 0; index < UNICODE_16_FORMAT_RANGES.length; index += 2) {
            if (codePoint < UNICODE_16_FORMAT_RANGES[index]) {
                return false;
            }
            if (codePoint <= UNICODE_16_FORMAT_RANGES[index + 1]) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAllowedLookup(String lookup) {
        return "body".equals(lookup)
                || "routeId".equals(lookup)
                || "exchangeId".equals(lookup)
                || "messageTimestamp".equals(lookup)
                || hasAllowedKey(lookup, "header.", false)
                || hasAllowedKey(lookup, "headers.", true)
                || hasAllowedKey(lookup, "exchangeProperty.", false)
                || hasAllowedKey(lookup, "variable.", false)
                || hasAllowedKey(lookup, "variables.", true);
    }

    private static boolean hasAllowedKey(String lookup, String prefix, boolean rejectCollectionFunctions) {
        if (!lookup.startsWith(prefix) || lookup.length() == prefix.length()) {
            return false;
        }
        String key = lookup.substring(prefix.length());
        if (rejectCollectionFunctions && ("size".equals(key) || "length".equals(key))) {
            return false;
        }
        for (int index = 0; index < key.length(); index++) {
            char value = key.charAt(index);
            if (!isAsciiLetterOrDigit(value) && value != '_' && value != '-') {
                return false;
            }
        }
        return true;
    }

    private static boolean isAsciiLetterOrDigit(char value) {
        return value >= 'a' && value <= 'z'
                || value >= 'A' && value <= 'Z'
                || value >= '0' && value <= '9';
    }

    private static final class PredicateParser {

        private final String value;
        private int cursor;
        private String logicalOperator;

        private PredicateParser(String value) {
            this.value = value;
        }

        private boolean parse() {
            if (!readLookup() || !readSpace() || !readComparisonOperator() || !readSpace() || !readOperand()) {
                return false;
            }
            while (cursor < value.length()) {
                if (!readSpace()) {
                    return false;
                }
                String currentLogicalOperator = readLogicalOperator();
                if (currentLogicalOperator == null
                        || (logicalOperator != null && !logicalOperator.equals(currentLogicalOperator))
                        || !readSpace()
                        || !readLookup()
                        || !readSpace()
                        || !readComparisonOperator()
                        || !readSpace()
                        || !readOperand()) {
                    return false;
                }
                logicalOperator = currentLogicalOperator;
            }
            return true;
        }

        private boolean readLookup() {
            if (!value.startsWith("${", cursor)) {
                return false;
            }
            int end = value.indexOf('}', cursor + 2);
            if (end < 0 || !isAllowedLookup(value.substring(cursor + 2, end))) {
                return false;
            }
            cursor = end + 1;
            return true;
        }

        private boolean readComparisonOperator() {
            for (String operator : COMPARISON_OPERATORS) {
                if (value.startsWith(operator, cursor)) {
                    cursor += operator.length();
                    return true;
                }
            }
            return false;
        }

        private String readLogicalOperator() {
            if (value.startsWith("&&", cursor)) {
                cursor += 2;
                return "&&";
            }
            if (value.startsWith("||", cursor)) {
                cursor += 2;
                return "||";
            }
            return null;
        }

        private boolean readOperand() {
            if (readLookup()) {
                return true;
            }
            if (cursor >= value.length()) {
                return false;
            }
            if (value.charAt(cursor) == '\'') {
                return readQuotedScalar();
            }
            if (readKeyword("true") || readKeyword("false") || readKeyword("null")) {
                return true;
            }
            return readNumber();
        }

        private boolean readQuotedScalar() {
            cursor++;
            while (cursor < value.length()) {
                char character = value.charAt(cursor);
                if (character == '\'') {
                    cursor++;
                    return true;
                }
                if (character == '\\'
                        || value.startsWith("${", cursor)
                        || value.startsWith("$simple{", cursor)
                        || value.startsWith("$init{", cursor)
                        || value.startsWith("{{", cursor)
                        || value.startsWith("}}", cursor)) {
                    return false;
                }
                cursor++;
            }
            return false;
        }

        private boolean readKeyword(String keyword) {
            if (!value.startsWith(keyword, cursor)) {
                return false;
            }
            int end = cursor + keyword.length();
            if (end < value.length() && value.charAt(end) != ' ') {
                return false;
            }
            cursor = end;
            return true;
        }

        private boolean readNumber() {
            int start = cursor;
            if (cursor < value.length() && value.charAt(cursor) == '-') {
                cursor++;
            }
            int integerStart = cursor;
            while (cursor < value.length() && isAsciiDigit(value.charAt(cursor))) {
                cursor++;
            }
            if (cursor == integerStart) {
                cursor = start;
                return false;
            }
            if (cursor - integerStart > 1 && value.charAt(integerStart) == '0') {
                return false;
            }

            boolean decimal = cursor < value.length() && value.charAt(cursor) == '.';
            if (decimal) {
                cursor++;
                int fractionStart = cursor;
                while (cursor < value.length() && isAsciiDigit(value.charAt(cursor))) {
                    cursor++;
                }
                if (cursor == fractionStart) {
                    return false;
                }
            }
            if (cursor < value.length() && value.charAt(cursor) != ' ') {
                return false;
            }

            String number = value.substring(start, cursor);
            try {
                if (decimal) {
                    return Double.isFinite(Double.parseDouble(number));
                }
                Long.parseLong(number);
                return true;
            } catch (NumberFormatException ignored) {
                return false;
            }
        }

        private boolean readSpace() {
            if (cursor >= value.length() || value.charAt(cursor) != ' ') {
                return false;
            }
            cursor++;
            return cursor >= value.length() || value.charAt(cursor) != ' ';
        }

        private static boolean isAsciiDigit(char value) {
            return value >= '0' && value <= '9';
        }
    }
}
