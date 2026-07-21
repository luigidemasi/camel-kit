package io.github.luigidemasi.camelkit.ship.evidence.launcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import io.github.luigidemasi.camelkit.ship.ShipArtifactLimits;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class ShipCitrusYamlMainTest {

    @TempDir
    Path directory;

    @Test
    void acceptsOnlyOneLiteralSynchronousCamelBehaviorAssertion() throws Exception {
        ShipCitrusYamlMain.TestContract contract = ShipCitrusYamlMain.inspect(write(validTest()), "orders");

        assertEquals("orders-behavior", contract.name());
        assertEquals("camel:sync:direct:camel-kit-ship-test-orders", contract.endpoint());
        assertEquals(1, contract.cases().size());
        assertEquals("new-order", contract.cases().get(0).requestBody());
        assertEquals("accepted-order", contract.cases().get(0).responseBody());
        assertTrue(contract.cases().get(0).requestHeaders().isEmpty());
        assertTrue(contract.cases().get(0).responseHeaders().isEmpty());
    }

    @Test
    void acceptsBoundedLiteralAcceptanceMatrixWithApplicationHeaders() throws Exception {
        ShipCitrusYamlMain.TestContract contract = ShipCitrusYamlMain.inspect(write(matrixTest()), "orders");

        assertEquals(2, contract.cases().size());
        assertEquals(List.of(
                new ShipCitrusYamlMain.Header("requestId", "req-1"),
                new ShipCitrusYamlMain.Header("timestamp", "2026-07-17T10:15:30Z"),
                new ShipCitrusYamlMain.Header("strategy", "priority")),
                contract.cases().get(0).requestHeaders());
        assertEquals(List.of(new ShipCitrusYamlMain.Header("requestId", "req-1")),
                contract.cases().get(0).responseHeaders());
        assertEquals("standard-order", contract.cases().get(1).requestBody());
        assertEquals("accepted-standard", contract.cases().get(1).responseBody());
    }

    @Test
    void enforcesCompleteAlternatingCasesAndActionBounds() throws Exception {
        assertEquals(ShipCitrusYamlMain.MAX_CASES,
                ShipCitrusYamlMain.inspect(write(testWithCases(ShipCitrusYamlMain.MAX_CASES)), "orders")
                        .cases().size());

        for (String invalid : List.of(
                validTest().replace("  - receive:", "  - send:"),
                validTest() + sendAction("extra"),
                testWithCases(ShipCitrusYamlMain.MAX_CASES + 1))) {
            assertThrows(IOException.class, () -> ShipCitrusYamlMain.inspect(write(invalid), "orders"));
        }
    }

    @Test
    void rejectsUnsafeDuplicateNonLiteralAndOversizedHeaders() throws Exception {
        String maxHeaders = java.util.stream.IntStream
                .range(0, ShipCitrusYamlMain.MAX_HEADERS_PER_ACTION)
                .mapToObj(index -> "          - name: header" + index + "\n            value: value\n")
                .collect(java.util.stream.Collectors.joining());
        ShipCitrusYamlMain.inspect(write(withRequestHeaders(maxHeaders)), "orders");

        String tooManyHeaders = maxHeaders
                                + "          - name: overflow\n            value: value\n";
        for (String headers : List.of(
                "          - name: requestId\n            value: one\n"
                                      + "          - name: RequestId\n            value: two\n",
                "          - name: _requestId\n            value: one\n",
                "          - name: " + "h".repeat(129) + "\n            value: one\n",
                "          - name: CamelHttpUri\n            value: hidden\n",
                "          - name: citrus_camel_exchange_pattern\n            value: InOnly\n",
                "          - name: requestId\n            value: 42\n",
                "          - name: requestId\n            value: true\n",
                "          - name: requestId\n            value: null\n",
                "          - name: requestId\n            value: [one]\n",
                "          - name: requestId\n            value: \"line\\nbreak\"\n",
                "          - name: requestId\n            value: \"" + "x".repeat(
                        ShipCitrusYamlMain.MAX_HEADER_VALUE_CHARS + 1) + "\"\n",
                "          - name: requestId\n            value: one\n            type: java.lang.String\n",
                tooManyHeaders)) {
            assertThrows(IOException.class,
                    () -> ShipCitrusYamlMain.inspect(write(withRequestHeaders(headers)), "orders"));
        }
        assertThrows(IOException.class,
                () -> ShipCitrusYamlMain.inspect(write(withRequestHeaders("")), "orders"));
    }

    @Test
    void rejectsOversizedLiteralBodies() throws Exception {
        String invalid = validTest().replace(
                "data: new-order", "data: " + "x".repeat(ShipCitrusYamlMain.MAX_BODY_CHARS + 1));

        assertThrows(IOException.class, () -> ShipCitrusYamlMain.inspect(write(invalid), "orders"));
    }

    @Test
    void acceptsOrdinaryLiteralContentThatOnlyLooksLikeCodeOrAResource() throws Exception {
        String literal = "description script for user@example.test at https://example.test/file:payload.class";
        Path test = write(validTest().replace("accepted-order", '"' + literal + '"'));

        ShipCitrusYamlMain.TestContract contract = ShipCitrusYamlMain.inspect(test, "orders");

        assertEquals(literal, contract.cases().get(0).responseBody());
    }

    @Test
    void rejectsOnlyActualDynamicExpressionsAndAssertionBypasses() throws Exception {
        for (String bypass : List.of(
                "${variable}", "#{bean}", "{{property}}", "citrus:readFileResource(payload.txt)",
                "@ignore@", "@matches(.*)@")) {
            Path test = write(validTest().replace("accepted-order", bypass));
            assertThrows(IOException.class, () -> ShipCitrusYamlMain.inspect(test, "orders"), bypass);
        }
    }

    @Test
    void enforcesTheCanonicalAggregateCitrusYamlByteLimitAtItsBoundary() throws Exception {
        Path accepted = write(padWithComment(validTest(), ShipArtifactLimits.MAX_CITRUS_YAML_BYTES));
        assertEquals(ShipArtifactLimits.MAX_CITRUS_YAML_BYTES, Files.size(accepted));
        ShipCitrusYamlMain.inspect(accepted, "orders");

        Path rejected = write(padWithComment(validTest(), ShipArtifactLimits.MAX_CITRUS_YAML_BYTES + 1));
        assertEquals(ShipArtifactLimits.MAX_CITRUS_YAML_BYTES + 1L, Files.size(rejected));
        assertThrows(IOException.class, () -> ShipCitrusYamlMain.inspect(rejected, "orders"));
    }

    @Test
    void rejectsEndpointAndActionShapeBypasses() throws Exception {
        for (String invalid : List.of(
                validTest().replace("camel:sync:direct:camel-kit-ship-test-orders", "camel:sync:http:example.test"),
                validTest().replace("camel:sync:direct:camel-kit-ship-test-orders", "camel:direct:orders"),
                validTest().replace(
                        "camel:sync:direct:camel-kit-ship-test-orders", "camel:sync:direct:other"),
                validTest().replaceFirst(
                        "camel:sync:direct:camel-kit-ship-test-orders", "camel:sync:direct:other"),
                validTest().replace(
                        "endpoint: \"camel:sync:direct:camel-kit-ship-test-orders\"",
                        "endpoint:\n        uri: \"camel:sync:direct:camel-kit-ship-test-orders\""),
                validTest().replaceFirst("  - send:", "  - receive:"),
                validTest().replace("  - receive:", "  - send:"),
                validTest().replace("  - receive:", "  - echo:"),
                validTest().replace("actions:\n", "actions:\n  - send:\n      endpoint: other\n"))) {
            assertThrows(IOException.class, () -> ShipCitrusYamlMain.inspect(write(invalid), "orders"));
        }
    }

    @Test
    void rejectsExtensionsResourcesPlaceholdersAndMultipleDocuments() throws Exception {
        for (String invalid : List.of(
                validTest().replace("actions:", "beans: []\nactions:"),
                validTest().replace("actions:", "endpoints: []\nactions:"),
                validTest().replace("actions:", "description: hidden\nactions:"),
                validTest().replace("data: accepted-order", "resource:\n            file: classpath:test.txt"),
                validTest().replace("data: accepted-order", "data: &response accepted-order"),
                validTest().replace("data: new-order", "data: *response"),
                validTest().replace("data: accepted-order", "data: !!java.lang.String accepted-order"),
                validTest() + "---\nname: hidden\nactions: []\n",
                validTest().replace("name: orders-behavior", "name: orders behavior"),
                validTest().replace("name: orders-behavior", "name: ${name}"))) {
            assertThrows(IOException.class, () -> ShipCitrusYamlMain.inspect(write(invalid), "orders"));
        }
    }

    @Test
    void rejectsDuplicateFieldsAndNonLiteralBodies() throws Exception {
        for (String invalid : List.of(
                validTest().replace("name: orders-behavior", "name: one\nname: two"),
                validTest().replace("data: accepted-order", "data: accepted-order\n          data: hidden"),
                validTest().replace("data: accepted-order", "data: 42"),
                validTest().replace("data: accepted-order", "data: true"),
                validTest().replace("data: accepted-order", "data: \"\""),
                validTest().replace("message:\n        body:", "message:\n        headers: []\n        body:"))) {
            assertThrows(IOException.class, () -> ShipCitrusYamlMain.inspect(write(invalid), "orders"));
        }
    }

    @Test
    void bindsTheLiteralEndpointToTheDeclaredRoute() throws Exception {
        Path test = write(validTest());

        assertThrows(IOException.class, () -> ShipCitrusYamlMain.inspect(test, "other"));
        assertThrows(IOException.class, () -> ShipCitrusYamlMain.inspect(test, "orders/escape"));
        assertThrows(IOException.class, () -> ShipCitrusYamlMain.inspect(
                write(replaceLast(
                        matrixTest(),
                        "camel:sync:direct:camel-kit-ship-test-orders",
                        "camel:sync:direct:other")),
                "orders"));
    }

    private Path write(String content) throws IOException {
        Path file = Files.createTempFile(directory, "orders-", ".camel.it.yaml");
        return Files.writeString(file, content);
    }

    private static String validTest() {
        return """
                name: orders-behavior
                actions:
                  - send:
                      endpoint: "camel:sync:direct:camel-kit-ship-test-orders"
                      message:
                        body:
                          data: new-order
                  - receive:
                      endpoint: "camel:sync:direct:camel-kit-ship-test-orders"
                      message:
                        body:
                          data: accepted-order
                """;
    }

    private static String matrixTest() {
        return """
                name: orders-matrix
                actions:
                  - send:
                      endpoint: "camel:sync:direct:camel-kit-ship-test-orders"
                      message:
                        headers:
                          - name: requestId
                            value: req-1
                          - name: timestamp
                            value: "2026-07-17T10:15:30Z"
                          - name: strategy
                            value: priority
                        body:
                          data: priority-order
                  - receive:
                      endpoint: "camel:sync:direct:camel-kit-ship-test-orders"
                      message:
                        headers:
                          - name: requestId
                            value: req-1
                        body:
                          data: accepted-priority
                  - send:
                      endpoint: "camel:sync:direct:camel-kit-ship-test-orders"
                      message:
                        body:
                          data: standard-order
                  - receive:
                      endpoint: "camel:sync:direct:camel-kit-ship-test-orders"
                      message:
                        body:
                          data: accepted-standard
                """;
    }

    private static String testWithCases(int count) {
        StringBuilder yaml = new StringBuilder("name: orders-matrix\nactions:\n");
        for (int index = 0; index < count; index++) {
            yaml.append(sendAction("request-")).append(index).append('\n')
                    .append(receiveAction("response-")).append(index).append('\n');
        }
        return yaml.toString();
    }

    private static String sendAction(String body) {
        return String.format(Locale.ROOT, """
                  - send:
                      endpoint: "camel:sync:direct:camel-kit-ship-test-orders"
                      message:
                        body:
                          data: %s
                """, body).stripTrailing();
    }

    private static String receiveAction(String body) {
        return String.format(Locale.ROOT, """
                  - receive:
                      endpoint: "camel:sync:direct:camel-kit-ship-test-orders"
                      message:
                        body:
                          data: %s
                """, body).stripTrailing();
    }

    private static String withRequestHeaders(String entries) {
        return validTest().replace(
                "      message:\n        body:\n          data: new-order",
                "      message:\n        headers:\n" + entries + "        body:\n          data: new-order");
    }

    private static String replaceLast(String source, String value, String replacement) {
        int index = source.lastIndexOf(value);
        return source.substring(0, index) + replacement + source.substring(index + value.length());
    }

    private static String padWithComment(String source, int bytes) {
        int padding = bytes - source.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        if (padding < 2) {
            throw new IllegalArgumentException("Requested Citrus fixture size is too small");
        }
        return source + '#' + "x".repeat(padding - 2) + '\n';
    }
}
