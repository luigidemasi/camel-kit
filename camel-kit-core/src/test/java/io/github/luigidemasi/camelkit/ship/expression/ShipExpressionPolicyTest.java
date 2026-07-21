package io.github.luigidemasi.camelkit.ship.expression;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipExpressionPolicyTest {

    @Test
    void acceptsOnlyExactSimpleYamlSelectors() {
        assertTrue(ShipExpressionPolicy.isDirectSimpleSelector("simple"));
        assertTrue(ShipExpressionPolicy.isGenericSimpleSelector("language", "simple"));

        for (String selector : List.of(
                "Simple", "SIMPLE", "simple-", "simple_", "constant", "csimple", "method", "java",
                "jsonpath", "xpath", "\"simple\"", "!!str simple")) {
            assertFalse(ShipExpressionPolicy.isDirectSimpleSelector(selector), selector);
        }
        for (String selector : List.of(
                "", " simple", "simple ", "simple\n",
                "s" + scalar(0x0456) + "mple",
                scalar(0xff53) + "imple",
                "sim" + scalar(0x200b) + "ple")) {
            assertFalse(ShipExpressionPolicy.isDirectSimpleSelector(selector), selector);
        }
        assertFalse(ShipExpressionPolicy.isDirectSimpleSelector(null));
        assertFalse(ShipExpressionPolicy.isGenericSimpleSelector(null, "simple"));
        assertFalse(ShipExpressionPolicy.isGenericSimpleSelector("language", null));
        assertFalse(ShipExpressionPolicy.isGenericSimpleSelector("Language", "simple"));
        assertFalse(ShipExpressionPolicy.isGenericSimpleSelector("language", "Simple"));
        assertFalse(ShipExpressionPolicy.isGenericSimpleSelector("simple", "simple"));
    }

    @Test
    void acceptsClosedTemplateSurface() {
        for (String expression : List.of(
                "",
                "literal text",
                "ordinary Unicode: café 漢字 😀",
                "C++ and regex are literal template text",
                "${body}",
                "${routeId}",
                "${exchangeId}",
                "${messageTimestamp}",
                "${header.request-id}",
                "${header.size}",
                "${header.-}",
                "${header._}",
                "${header.0}",
                "${header.A0_-}",
                "${headers.request_id}",
                "${exchangeProperty.correlationId}",
                "${variable.route-key}",
                "${variable.length}",
                "${variables.route_key}",
                "${header.first}${header.second}",
                "Order is ${header.order-id}; route is ${routeId}",
                "Status : ${header.status}",
                "Question ? ${body}",
                "${body} ? 'yes' : 'no'",
                "C}++suffix")) {
            assertTrue(ShipExpressionPolicy.isSafeSimpleTemplate(expression), expression);
        }
    }

    @Test
    void rejectsDynamicTemplateSurfacesOutsideTheAllowlist() {
        for (String expression : List.of(
                "${}",
                "${header.}",
                "${header.foo.bar}",
                "${header.foo[0]}",
                "${header(foo)}",
                "${header.na" + scalar(0x043c) + "e}",
                "${headers}",
                "${headers.size}",
                "${headers.length}",
                "${exchangeProperties.foo}",
                "${body.foo}",
                "${variables.size}",
                "${variables.length}",
                "${bean:service}",
                "${ref:service}",
                "${type:java.lang.Runtime}",
                "${sys.user.home}",
                "${file:name}",
                "${date:now}",
                "${header.foo",
                "${${body}}",
                "$simple{body}",
                "$init{ int x = 1 }init$",
                "{{secret}}",
                "literal }} text",
                "escaped\\ntext",
                "escaped\\rtext",
                "escaped\\ttext",
                "escaped\\}text",
                "${body}++",
                "${body}--",
                "${body}++ suffix",
                "${body}-- suffix",
                "${body}}++",
                "${body}text}--",
                "${body} ?: fallback",
                "${body} ~> ${header.next}",
                "${body}~> ${header.next}",
                "${body} ?~> ${header.next}",
                "${body}?~> ${header.next}",
                "before ${body} after ${bean:service}")) {
            assertFalse(ShipExpressionPolicy.isSafeSimpleTemplate(expression), expression);
        }
        assertFalse(ShipExpressionPolicy.isSafeSimpleTemplate(null));
    }

    @Test
    void acceptsOnlyTheClosedPredicateGrammar() {
        for (String operator : List.of(
                "==", "!=", "=~", "!=~", "<", "<=", ">", ">=",
                "contains", "!contains", "startsWith", "!startsWith", "endsWith", "!endsWith")) {
            assertTrue(
                    ShipExpressionPolicy.isSafeSimplePredicate("${header.value} " + operator + " 'target'"),
                    operator);
        }

        for (String expression : List.of(
                "${header.score} >= -1.5",
                "${header.enabled} == true",
                "${header.disabled} == false",
                "${header.optional} == null",
                "${header.left} == ${exchangeProperty.right}",
                "${body} == ''",
                "${body} contains 'order' && ${header.confidence} >= 0.8",
                "${header.first} == 'a' && ${header.second} != 'b' && ${routeId} == 'route-a'",
                "${header.first} == 'a' || ${header.second} != 'b' || ${routeId} == 'route-a'",
                "${body} == 'the words is, regex, and contains are data here'")) {
            assertTrue(ShipExpressionPolicy.isSafeSimplePredicate(expression), expression);
        }
    }

    @Test
    void rejectsEveryUnapprovedPredicateSurface() {
        for (String operator : List.of(
                "~~", "!~~", "regex", "!regex", "not regex", "is", "!is", "not is",
                "in", "!in", "not in", "range", "!range", "not range", "not contains",
                "starts with", "ends with", "=", "===", "!==", "<>", "and", "or", "not",
                "matches", "containsx", "StartsWith")) {
            assertFalse(
                    ShipExpressionPolicy.isSafeSimplePredicate("${body} " + operator + " 'value'"),
                    operator);
        }

        for (String expression : List.of(
                "",
                "true",
                "${header.enabled}",
                "${body} == value",
                "${body} == \"a value\"",
                "${body}=='value'",
                " ${body} == 'value'",
                "${body}  == 'value'",
                "${body} ==  'value'",
                "${body} == 'value' ",
                "${body} == 'unterminated",
                "${body} == 'escaped\\'value'",
                "${body} == '${bean:service}'",
                "${body} == '$simple{body}'",
                "${body} == '$init{x}init$'",
                "${body} == '{{secret}}'",
                "${body} == 'escaped\\nvalue'",
                "${body} == 'value' && true",
                "${body} == 'value' && ${header.other}",
                "${body} == 'value' &&",
                "${header.first} == 'a' || ${header.second} != 'b' && ${routeId} == 'route-a'",
                "(${body} == 'value')",
                "${body} ?: 'fallback'",
                "${body} ? 'yes' : 'no'",
                "${body} ~> ${header.next}",
                "${body} ?~> ${header.next}",
                "${header.count} ++",
                "${header.count} --",
                "${body} Contains 'value'",
                "${type:java.lang.String} == 'value'",
                "${header.foo.bar} == 'value'")) {
            assertFalse(ShipExpressionPolicy.isSafeSimplePredicate(expression), expression);
        }
        assertFalse(ShipExpressionPolicy.isSafeSimplePredicate(null));
    }

    @Test
    void enforcesStrictUnicodeAndUtf8ByteLimits() {
        String asciiAtLimit = "a".repeat(16_384);
        String twoByteAtLimit = "é".repeat(8_192);
        String threeByteAtLimit = "€".repeat(5_461) + "a";
        String fourByteAtLimit = "😀".repeat(4_096);

        for (String expression : List.of(asciiAtLimit, twoByteAtLimit, threeByteAtLimit, fourByteAtLimit)) {
            assertTrue(ShipExpressionPolicy.isSafeSimpleTemplate(expression));
            assertFalse(ShipExpressionPolicy.isSafeSimpleTemplate(expression + 'a'));
        }

        String predicatePrefix = "${header.value} == '";
        String predicateSuffix = "'";
        String predicateAtLimit = predicatePrefix
                                  + "a".repeat(16_384 - predicatePrefix.length() - predicateSuffix.length())
                                  + predicateSuffix;
        String predicateOverLimit = predicatePrefix
                                    + "a".repeat(16_385 - predicatePrefix.length() - predicateSuffix.length())
                                    + predicateSuffix;
        assertTrue(ShipExpressionPolicy.isSafeSimplePredicate(predicateAtLimit));
        assertFalse(ShipExpressionPolicy.isSafeSimplePredicate(predicateOverLimit));

        for (String number : List.of(
                "-9223372036854775808", "-2147483649", "-2147483648", "2147483647",
                "9223372036854775807", "0", "-0", "0.0", "-1.5")) {
            assertTrue(ShipExpressionPolicy.isSafeSimplePredicate("${header.value} == " + number), number);
        }
        for (String number : List.of(
                "-9223372036854775809", "9223372036854775808", "00", "01", "00.1", "1e3", ".5", "1.", "+1",
                "1,5", "1..2", "--1", "NaN", "Infinity", "١",
                "9".repeat(400) + ".0")) {
            assertFalse(ShipExpressionPolicy.isSafeSimplePredicate("${header.value} == " + number), number);
        }

        for (String expression : List.of(
                "line\nfeed",
                "tab\tvalue",
                "nul\0value",
                "line" + scalar(0x2028) + "separator",
                "paragraph" + scalar(0x2029) + "separator",
                "bidi" + scalar(0x202e) + "override",
                "isolate" + scalar(0x2066) + "text",
                "zero" + scalar(0x200b) + "width",
                "bom" + scalar(0xfeff) + "value",
                scalar(0x0890),
                scalar(0x110bd),
                scalar(0x13430),
                scalar(0xe0001),
                scalar(0xe007f),
                "\ud800",
                "\udc00",
                "\ud800x")) {
            assertFalse(ShipExpressionPolicy.isSafeSimpleTemplate(expression));
        }
        assertFalse(ShipExpressionPolicy.isSafeSimplePredicate("${body} == 'line" + scalar(0x2028) + "separator'"));
        assertTrue(ShipExpressionPolicy.isSafeSimpleTemplate(scalar(0x088f)));
        assertTrue(ShipExpressionPolicy.isSafeSimpleTemplate(scalar(0x0892)));
    }

    private static String scalar(int codePoint) {
        return new String(Character.toChars(codePoint));
    }
}
