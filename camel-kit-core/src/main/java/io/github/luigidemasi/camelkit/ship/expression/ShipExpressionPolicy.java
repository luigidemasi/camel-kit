package io.github.luigidemasi.camelkit.ship.expression;

/**
 * Bounded syntax gate for the Simple expressions accepted by Ship.
 *
 * <p>
 * The gate accepts any bounded, cleanly encoded Simple text and rejects only what Ship must never forward: oversized
 * values, control and format characters, lone surrogates, and indirect expansion through nested or property
 * placeholders. The gate operates on the raw code points of the source text only: backslash escape sequences that Camel
 * may expand at evaluation time are deliberately not blocked. Simple syntax itself is not re-implemented here; the
 * sandboxed camel-main startup in the VALIDATE stage remains the authority on what Camel accepts. Format characters are
 * classified with the running JDK's Unicode data, matching how the tree policy classifies unsafe display characters.
 * </p>
 */
public final class ShipExpressionPolicy {

    private static final int MAX_EXPRESSION_UTF8_BYTES = 16_384;

    private ShipExpressionPolicy() {
    }

    /** Returns whether the value is bounded, cleanly encoded Simple text. */
    public static boolean isSafeSimple(String value) {
        if (value == null || hasIndirectExpansion(value)) {
            return false;
        }
        int utf8Bytes = 0;
        for (int offset = 0; offset < value.length(); offset++) {
            char first = value.charAt(offset);
            int codePoint;
            int width;
            if (Character.isHighSurrogate(first)) {
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
                width = first <= 0x7f ? 1 : first <= 0x7ff ? 2 : 3;
            }
            if (isForbiddenCodePoint(codePoint) || utf8Bytes > MAX_EXPRESSION_UTF8_BYTES - width) {
                return false;
            }
            utf8Bytes += width;
        }
        return true;
    }

    private static boolean hasIndirectExpansion(String value) {
        return value.contains("$simple{")
                || value.contains("$init{")
                || value.contains("{{")
                || value.contains("}}");
    }

    private static boolean isForbiddenCodePoint(int codePoint) {
        return codePoint <= 0x1f
                || codePoint >= 0x7f && codePoint <= 0x9f
                || codePoint == 0x2028
                || codePoint == 0x2029
                || Character.getType(codePoint) == Character.FORMAT;
    }
}
