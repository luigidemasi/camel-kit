package io.github.luigidemasi.camelkit.ship.evidence.launcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.apache.camel.component.stub.StubComponent;

import io.github.luigidemasi.camelkit.ship.ShipArtifactLimits;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogExpressionInventory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class ShipCamelMainBootstrapTest {

    @TempDir
    Path project;

    @Test
    void startsTheAcceptedRouteAndExercisesItsDirectBehavior() throws Exception {
        Path route = write("""
                - route:
                    id: orders
                    from:
                      uri: direct:orders
                      steps:
                        - setBody:
                            simple: accepted-order
                """);

        ShipCamelMainBootstrap.withStarted(project, List.of(route), Set.of("orders"), context -> {
            String response = context.createProducerTemplate()
                    .requestBody("direct:orders", "new-order", String.class);
            assertEquals("accepted-order", response);
        });
    }

    @Test
    void startsAnExactSafeGenericSimpleExpression() throws Exception {
        Path route = write(routeWithExpression("""
                language:
                  language: simple
                  expression: "accepted-${body}"
                """));

        ShipCamelMainBootstrap.withStarted(project, List.of(route), Set.of("orders"), context -> {
            String response = context.createProducerTemplate()
                    .requestBody("direct:orders", "new-order", String.class);
            assertEquals("accepted-new-order", response);
        });
    }

    @Test
    void startsEveryAdmittedDotFreeRuntimeLookupWithoutBeanFallback() throws Exception {
        List<String> expressions = List.of(
                "${header.request-id}",
                "${headers.request-id}",
                "${exchangeProperty.correlation_id}",
                "${variable.route-key}",
                "${variables.route-key}");
        for (String expression : expressions) {
            Path route = write(validRoute().replace(
                    "simple: accepted-order", "simple: \"" + expression + "\""));

            ShipCamelMainBootstrap.withStarted(project, List.of(route), Set.of("orders"), context -> {
                var exchange = context.createProducerTemplate().request("direct:orders", candidate -> {
                    candidate.getMessage().setBody("new-order");
                    candidate.getMessage().setHeader("request-id", "accepted-lookup");
                    candidate.setProperty("correlation_id", "accepted-lookup");
                    candidate.setVariable("route-key", "accepted-lookup");
                });
                assertEquals("accepted-lookup", exchange.getMessage().getBody(String.class), expression);
            });
            Files.delete(route);
        }
    }

    @Test
    void replacesEveryNonDirectEndpointWithTheInMemoryStubComponent() throws Exception {
        Path route = write("""
                - route:
                    id: orders
                    from:
                      uri: direct:orders
                      steps:
                        - to:
                            uri: https://outside.example/orders
                """);

        ShipCamelMainBootstrap.withStarted(project, List.of(route), Set.of("orders"), context -> {
            assertInstanceOf(StubComponent.class, context.getComponent("https"));
            context.createProducerTemplate().sendBody("direct:orders", "new-order");
        });
    }

    @Test
    void replacesExternalConsumersWithTheControllerTestEntryBeforeStartup() throws Exception {
        for (String source : List.of("kafka:orders", "file:inbox", "jms:queue:orders")) {
            Path route = write(validRoute().replace("direct:orders", source));

            ShipCamelMainBootstrap.withTestEntryPoint(
                    project, List.of(route), Set.of("orders"), "orders", context -> {
                        String response = context.createProducerTemplate().requestBody(
                                ShipCamelMainBootstrap.testEntryUri("orders"), "new-order", String.class);
                        assertEquals("accepted-order", response, source);
                        assertInstanceOf(
                                StubComponent.class,
                                context.getComponent(source.substring(0, source.indexOf(':'))),
                                source);
                    });
            Files.delete(route);
        }
    }

    @Test
    void reservesTheInjectedEndpointNamespace() throws Exception {
        Path route = write(validRoute().replace(
                "direct:orders", ShipCamelMainBootstrap.testEntryUri("orders")));

        assertThrows(IOException.class,
                () -> ShipCamelMainBootstrap.start(project, List.of(route), Set.of("orders")));
    }

    @Test
    void rejectsUnapprovedTopicThresholdAndBooleanPlaceholders() throws Exception {
        Path route = write("""
                - route:
                    id: orders
                    from:
                      uri: direct:{{orders.input}}
                      steps:
                        - setBody:
                            simple: "${body}"
                        - delay:
                            simple: "{{orders.delay}}"
                        - setBody:
                            simple: "{{orders.enabled}}"
                """);

        assertThrows(IOException.class,
                () -> ShipCamelMainBootstrap.start(project, List.of(route), Set.of("orders")));
    }

    @Test
    void acceptsEndpointSelectorPatternsWithoutTreatingThemAsEndpointUris() throws Exception {
        Path route = write("- interceptFrom:\n    steps: []\n" + validRoute());
        ShipCamelMainBootstrap.validatePolicy(route);

        for (String operation : List.of("interceptFrom", "interceptSendToEndpoint")) {
            for (String definition : List.of(
                    "- " + operation + ": jms*",
                    "- " + operation + ":\n    uri: jms*\n    steps: []")) {
                route = write(definition + '\n' + validRoute());

                ShipCamelMainBootstrap.validatePolicy(route);
            }
        }
    }

    @Test
    void rejectsUnsafeOrMissingEndpointSelectorPatterns() throws Exception {
        for (String operation : List.of("interceptFrom", "interceptSendToEndpoint")) {
            for (String definition : List.of(
                    "- " + operation + ": \"{{selector}}\"",
                    "- " + operation + ":\n    uri: \"{{selector}}\"",
                    "- " + operation + ":\n    uri: \"${body}\"",
                    "- " + operation + ":\n    uri: \"#bean:selector\"")) {
                Path route = write(definition + '\n' + validRoute());

                assertThrows(IOException.class, () -> ShipCamelMainBootstrap.validatePolicy(route), definition);
            }
        }

        Path missingRequiredPattern = write(
                "- interceptSendToEndpoint:\n    steps: []\n" + validRoute());
        assertThrows(IOException.class, () -> ShipCamelMainBootstrap.validatePolicy(missingRequiredPattern));
    }

    @Test
    void startsEndpointSelectorsAndStubsTheConcreteAfterUri() throws Exception {
        Path route = write("""
                - interceptFrom:
                    steps:
                      - setBody:
                          simple: intercepted
                - interceptSendToEndpoint:
                    uri: jms*
                    afterUri: https://outside.example/orders
                    steps:
                      - setBody:
                          simple: intercepted
                """ + validRoute());

        ShipCamelMainBootstrap.withStarted(project, List.of(route), Set.of("orders"), context -> {
            assertInstanceOf(StubComponent.class, context.getComponent("https"));
            assertFalse(context.getComponentNames().contains("jms"));
        });
    }

    @Test
    void rejectsDottedSimpleLookupKeysThatCamelCouldExecuteAsOgnl() throws Exception {
        for (String expression : List.of(
                "${header.control.stop}",
                "${exchangeProperty.control.stop}",
                "${exchangeProperties.clear}",
                "${exchangeProperties.size}",
                "${exchangeProperties.toString}",
                "${variable.control.stop}",
                "${header.foo.trim}")) {
            Path route = write(validRoute().replace(
                    "simple: accepted-order", "simple: \"" + expression + "\""));

            assertThrows(IOException.class,
                    () -> ShipCamelMainBootstrap.start(project, List.of(route), Set.of("orders")), expression);
            Files.delete(route);
        }
    }

    @Test
    void rejectsYamlReferencesMultipleDocumentsAndDynamicJavaAccess() throws Exception {
        for (String invalid : List.of(
                validRoute().replace("direct:orders", "&endpoint direct:orders"),
                validRoute().replace("direct:orders", "*endpoint"),
                validRoute().replace("direct:orders", "!!java.lang.String direct:orders"),
                validRoute() + "---\n" + validRoute().replace("orders", "hidden"),
                validRoute().replace("simple: accepted-order", "simple: \"${body.class}\""),
                validRoute().replace("simple: accepted-order", "simple: \"${type:java.lang.Runtime}\""),
                validRoute().replace("simple: accepted-order", "simple: \"${bean:executor}\""),
                validRoute().replace("simple: accepted-order", "simple:\n              expression: \"${body}\""),
                validRoute().replace("simple: accepted-order", "bean:\n              ref: executor"),
                validRoute().replace("simple: accepted-order", "script:\n              groovy: exit(0)"))) {
            Path route = write(invalid);
            assertThrows(IOException.class,
                    () -> ShipCamelMainBootstrap.start(project, List.of(route), Set.of("orders")));
        }
    }

    @Test
    void rejectsUnexpectedOrMissingRouteIdsAndSnapshotEscapes() throws Exception {
        Path route = write(validRoute());
        Path outside = Files.writeString(project.getParent().resolve("outside.camel.yaml"), validRoute());

        assertThrows(IOException.class,
                () -> ShipCamelMainBootstrap.start(project, List.of(route), Set.of("other")));
        assertThrows(IOException.class,
                () -> ShipCamelMainBootstrap.start(project, List.of(outside), Set.of("orders")));
        assertThrows(IOException.class,
                () -> ShipCamelMainBootstrap.start(project, List.of(route, route), Set.of("orders")));
    }

    @Test
    void rejectsEveryCandidateRuntimeConfigurationSurface() throws Exception {
        Path route = write(validRoute());
        for (String relative : List.of(
                "application.properties",
                "config/application-dev.yml",
                "src/main/resources/application.yaml",
                "config/camel-main.properties")) {
            Path extra = writeRelative(relative, "hidden=true\n");

            IOException failure = assertThrows(IOException.class,
                    () -> ShipCamelMainBootstrap.start(project, List.of(route), Set.of("orders")), relative);
            assertTrue(failure.getMessage().contains("runtime configuration")
                    || failure.getMessage().contains("runtime resource"), failure::getMessage);
            Files.delete(extra);
        }
    }

    @Test
    void rejectsAlternateLaunchInputsAcrossTheAcceptedCandidate() throws Exception {
        Path route = write(validRoute());
        for (String relative : List.of(
                "hidden.camel.yaml",
                "routes/orders.camel.xml",
                "routes/orders.pipe.yaml",
                "routes/orders.kamelet.yml",
                "src/main/resources/META-INF/services/org.apache.camel.spi.ComponentResolver",
                "src/main/resources/schema.xsd",
                "src/main/java/example/Processor.java",
                "lib/hidden.jar",
                "target/classes/Hidden.class",
                ".mvn/extensions.xml",
                "build.gradle",
                "bin/start.sh")) {
            Path extra = writeRelative(relative, "untrusted\n");

            assertThrows(IOException.class,
                    () -> ShipCamelMainBootstrap.start(project, List.of(route), Set.of("orders")), relative);
            Files.delete(extra);
        }
    }

    @Test
    void allowsOnlyTheBoundedNonRuntimeProjectSupportSurface() throws Exception {
        Path route = writeRelative("src/main/resources/routes/orders.camel.yaml", validRoute());
        writeRelative("pom.xml", "<project/>\n");
        writeRelative(".camel-kit/config.properties", "project.runtime=main\n");
        writeRelative("mvnw", "#!/bin/sh\n");
        writeRelative("mvnw.cmd", "@echo off\r\n");
        writeRelative("test/orders.camel.it.yaml", "name: orders\n");
        writeRelative(".pi/extensions/camel-kit-ship.js", "export default {};\n");
        writeRelative(".github/workflows/ci.yml", "name: ci\n");
        writeRelative("README.md", "# Orders\n");

        ShipCamelMainBootstrap.start(project, List.of(route), Set.of("orders"));
    }

    @Test
    void rejectsEveryNonSimpleCatalogExpressionLanguage() throws Exception {
        for (String language : CatalogExpressionInventory.catalogLanguages().stream()
                .filter(value -> !CatalogExpressionInventory.SIMPLE.equals(value))
                .sorted()
                .toList()) {
            Path route = write(String.format(Locale.ROOT, """
                    - route:
                        id: orders
                        from:
                          uri: direct:orders
                          steps:
                            - setBody:
                                %s: accepted-order
                    """, language));

            IOException failure = assertThrows(IOException.class,
                    () -> ShipCamelMainBootstrap.start(project, List.of(route), Set.of("orders")), language);
            assertTrue(failure.getMessage().contains("only the Simple expression language"),
                    failure::getMessage);
            Files.delete(route);
        }
    }

    @Test
    void rejectsGenericConstantAndBeanMethodExpressions() throws Exception {
        for (String expression : List.of(
                """
                        language:
                          language: constant
                          expression: accepted-order
                        """,
                "method: ordersBean")) {
            Path route = write(routeWithExpression(expression));

            IOException failure = assertThrows(IOException.class,
                    () -> ShipCamelMainBootstrap.start(project, List.of(route), Set.of("orders")), expression);
            assertTrue(failure.getMessage().contains("Simple expression language")
                    || failure.getMessage().contains("select exactly Simple"), failure::getMessage);
            Files.delete(route);
        }
    }

    @Test
    void rejectsMalformedAmbiguousAndUnsafeGenericExpressions() throws Exception {
        for (String expression : List.of(
                "language: simple",
                "language:\n  language: simple",
                "language:\n  expression: accepted-order",
                "language:\n  language: [simple]\n  expression: accepted-order",
                "language:\n  language: simple\n  expression: [accepted-order]",
                "language:\n  language: simple\n  expression: accepted-order\n  trim: true",
                "language:\n  language: simple\n  expression: accepted-order\nsimple: hidden",
                "Language:\n  language: simple\n  expression: accepted-order",
                "language:\n  language: simple\n  expression: \"${body.class}\"",
                "language:\n  language: simple\n  language: simple\n  expression: accepted-order",
                "language:\n  language: simple\n  expression: accepted-order\nlanguage:\n  language: simple\n  expression: hidden")) {
            Path route = write(routeWithExpression(expression));

            assertThrows(IOException.class,
                    () -> ShipCamelMainBootstrap.start(project, List.of(route), Set.of("orders")), expression);
            Files.delete(route);
        }
    }

    @Test
    void enforcesTheCanonicalAggregateRouteYamlByteLimitAtItsBoundary() throws Exception {
        Path accepted = write(padWithComment(validRoute(), ShipArtifactLimits.MAX_ROUTE_YAML_BYTES));
        assertEquals(ShipArtifactLimits.MAX_ROUTE_YAML_BYTES, Files.size(accepted));
        ShipCamelMainBootstrap.validatePolicy(accepted);

        Path rejected = write(padWithComment(validRoute(), ShipArtifactLimits.MAX_ROUTE_YAML_BYTES + 1));
        assertEquals(ShipArtifactLimits.MAX_ROUTE_YAML_BYTES + 1L, Files.size(rejected));
        assertThrows(IOException.class, () -> ShipCamelMainBootstrap.validatePolicy(rejected));
    }

    private Path write(String content) throws IOException {
        return Files.writeString(
                Files.createTempFile(project, "orders-", ".camel.yaml"), content);
    }

    private Path writeRelative(String relative, String content) throws IOException {
        Path target = project.resolve(relative);
        Files.createDirectories(target.getParent());
        return Files.writeString(target, content);
    }

    private static String validRoute() {
        return """
                - route:
                    id: orders
                    from:
                      uri: direct:orders
                      steps:
                        - setBody:
                            simple: accepted-order
                """;
    }

    private static String routeWithExpression(String expression) {
        String indented = expression.strip().lines()
                .map(line -> "            " + line)
                .reduce((left, right) -> left + '\n' + right)
                .orElseThrow();
        return """
                - route:
                    id: orders
                    from:
                      uri: direct:orders
                      steps:
                        - setBody:
                """ + indented + '\n';
    }

    private static String padWithComment(String source, int bytes) {
        int padding = bytes - source.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        if (padding < 2) {
            throw new IllegalArgumentException("Requested route fixture size is too small");
        }
        return source + '#' + "x".repeat(padding - 2) + '\n';
    }
}
