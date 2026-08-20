package io.github.luigidemasi.camelkit.ship.expression;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipExpressionPolicyTest {

    @Test
    void acceptsRealSimpleTemplatesAndPredicates() {
        for (String value : new String[]{
                "",
                "plain literal text",
                "città ${body}",
                "${body}",
                "${bodyAs(String)}",
                "${date:now:yyyyMMdd}",
                "${header.CamelFileName}",
                "${exchangeProperty.orderId}-${routeId}",
                "${body} == 'expected'",
                "${bodyAs(String)} =~ 'a.*b'",
                "${header.count}>5",
                "${body} != null && ${header.retries} <= 3 || ${variable.done} == true"}) {
            assertTrue(ShipExpressionPolicy.isSafeSimpleTemplate(value), value);
            assertTrue(ShipExpressionPolicy.isSafeSimplePredicate(value), value);
        }
    }

    @Test
    void acceptsTheExactByteBoundAndRejectsOneOver() {
        String bounded = "x".repeat(16_384);

        assertTrue(ShipExpressionPolicy.isSafeSimpleTemplate(bounded));
        assertFalse(ShipExpressionPolicy.isSafeSimpleTemplate(bounded + "x"));
        // Multi-byte text is bounded by encoded size, not char count: 8192 two-byte
        // characters fill the budget exactly.
        String twoByte = "è".repeat(8_192);
        assertTrue(ShipExpressionPolicy.isSafeSimplePredicate(twoByte));
        assertFalse(ShipExpressionPolicy.isSafeSimplePredicate(twoByte + "è"));
    }

    @Test
    void rejectsControlFormatAndMalformedText() {
        for (String value : new String[]{
                null,
                "line\nbreak",
                "nul\u0000byte",
                "del\u007fchar",
                "c1\u0085control",
                "line\u2028separator",
                "para\u2029separator",
                "zero\u200bwidth",
                "bidi\u202eoverride",
                "soft\u00adhyphen",
                "tag\udb40\udc41char",
                "lone\ud800surrogate",
                "lone\udc00low"}) {
            assertFalse(ShipExpressionPolicy.isSafeSimpleTemplate(value), String.valueOf(value));
            assertFalse(ShipExpressionPolicy.isSafeSimplePredicate(value), String.valueOf(value));
        }
    }

    @Test
    void rejectsIndirectExpansion() {
        for (String value : new String[]{
                "$simple{body}",
                "prefix $init{x} suffix",
                "{{app.secret}}",
                "closing }} marker"}) {
            assertFalse(ShipExpressionPolicy.isSafeSimpleTemplate(value), value);
            assertFalse(ShipExpressionPolicy.isSafeSimplePredicate(value), value);
        }
    }
}
