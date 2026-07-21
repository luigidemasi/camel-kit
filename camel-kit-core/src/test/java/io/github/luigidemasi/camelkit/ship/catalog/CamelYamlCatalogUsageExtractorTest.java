package io.github.luigidemasi.camelkit.ship.catalog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.github.luigidemasi.camelkit.ship.catalog.CatalogEvidenceSet.SubjectEvidence;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogSubject.Kind;
import io.github.luigidemasi.camelkit.ship.resolver.MavenCoordinate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CamelYamlCatalogUsageExtractorTest {

    @TempDir
    Path project;

    @Test
    void extractsComponentsEipsDataformatsAndSimple() throws Exception {
        Path route = write("""
                - route:
                    id: orders
                    from:
                      uri: direct:start
                      steps:
                        - set-body:
                            simple: ${body}
                        - split:
                            simple: ${body}
                            steps:
                              - marshal:
                                  csv: {}
                              - to:
                                  uri: kafka:orders
                """);

        List<CatalogSubject> result = extractor().extract(route, available());

        assertEquals(List.of(
                subject(Kind.COMPONENT, "direct"),
                subject(Kind.COMPONENT, "kafka"),
                subject(Kind.EIP, "from"),
                subject(Kind.EIP, "marshal"),
                subject(Kind.EIP, "route"),
                subject(Kind.EIP, "setBody"),
                subject(Kind.EIP, "split"),
                subject(Kind.EIP, "to"),
                subject(Kind.DATAFORMAT, "csv"),
                subject(Kind.LANGUAGE, "simple")), result);
    }

    @Test
    void genericSimpleIsRecordedAsTheExactSimpleCatalogSubject() throws Exception {
        Path route = write("""
                - route:
                    from:
                      uri: direct:start
                      steps:
                        - setBody:
                            language:
                              language: simple
                              expression: ${body}
                """);

        List<CatalogSubject> result = extractor().extract(route, available());

        assertTrue(result.contains(subject(Kind.LANGUAGE, "simple")));
    }

    @Test
    void rejectsDottedSimpleLookupKeysThatCamelCouldExecuteAsOgnl() throws Exception {
        for (String expression : List.of(
                "${header.control.stop}",
                "${exchangeProperty.control.stop}",
                "${exchangeProperties.clear}",
                "${exchangeProperties.size}",
                "${exchangeProperties.toString}",
                "${variable.control.stop}")) {
            Path route = write(String.format(Locale.ROOT, """
                    - route:
                        id: orders
                        from:
                          uri: direct:orders
                          steps:
                            - setBody:
                                simple: "%s"
                    """, expression));

            assertThrows(IOException.class, () -> extractor().extract(route, available()), expression);
            Files.delete(route);
        }
    }

    @Test
    void genericConstantMethodAndEveryDirectAlternativeFailClosed() throws Exception {
        List<String> alternatives = new ArrayList<>(
                List.of(
                        "language:\n  language: constant\n  expression: accepted-order",
                        "method: ordersBean"));
        CatalogExpressionInventory.catalogLanguages().stream()
                .filter(language -> !CatalogExpressionInventory.SIMPLE.equals(language))
                .map(language -> language + ": accepted-order")
                .forEach(alternatives::add);
        for (String expression : alternatives) {
            Path route = expressionRoute(expression);

            IOException failure = assertThrows(IOException.class,
                    () -> extractor().extract(route, available()), expression);
            assertTrue(failure.getMessage().contains("Simple expression language")
                    || failure.getMessage().contains("select exactly Simple"), failure::getMessage);
        }
    }

    @Test
    void malformedAmbiguousAndUnsafeGenericExpressionsFailClosed() throws Exception {
        for (String expression : List.of(
                "language: simple",
                "language:\n  language: simple",
                "language:\n  expression: accepted-order",
                "language:\n  language: [simple]\n  expression: accepted-order",
                "language:\n  language: simple\n  expression: [accepted-order]",
                "language:\n  language: simple\n  expression: accepted-order\n  trim: true",
                "language:\n  language: simple\n  expression: accepted-order\nsimple: hidden",
                "Language:\n  language: simple\n  expression: accepted-order",
                "language:\n  language: simple\n  expression: \"${body.class}\"")) {
            Path route = expressionRoute(expression);

            assertThrows(IOException.class, () -> extractor().extract(route, available()), expression);
        }
    }

    @Test
    void nonExactExpressionAliasesFailClosedWithoutDependingOnCatalogAvailability() throws Exception {
        for (String alias : List.of("exchange-property", "exchange_property", "Json-Path")) {
            Path route = expressionRoute(alias + ": order-status");

            IOException error = assertThrows(IOException.class,
                    () -> extractor().extract(route, available()), alias);

            assertTrue(error.getMessage().contains("non-exact language alias"), error::getMessage);
        }
    }

    @Test
    void endpointParameterValuesCannotManufactureFalseCatalogUsage() throws Exception {
        Path route = write("""
                - route:
                    from:
                      uri: direct:start
                      steps:
                        - to:
                            uri: kafka:orders
                            parameters:
                              split: enabled
                              simple: not-an-expression
                """);

        List<CatalogSubject> result = extractor().extract(route, available());

        assertFalse(result.contains(subject(Kind.EIP, "split")));
        assertFalse(result.contains(subject(Kind.LANGUAGE, "simple")));
    }

    @Test
    void unknownComponentFailsClosed() throws Exception {
        Path route = write("- route:\n    from:\n      uri: unknown:thing\n");

        IOException error = assertThrows(IOException.class, () -> extractor().extract(route, available()));

        assertTrue(error.getMessage().contains("unavailable in the resolved catalog"));
    }

    @Test
    void dynamicComponentSchemeFailsClosed() throws Exception {
        Path route = write("- route:\n    from:\n      uri: '{{source.scheme}}:thing'\n");

        IOException error = assertThrows(IOException.class, () -> extractor().extract(route, available()));

        assertTrue(error.getMessage().contains("dynamic or unsafe"));
    }

    @Test
    void duplicateYamlKeysAreRejected() throws Exception {
        Path route = write("""
                - route:
                    from:
                      uri: direct:start
                      uri: kafka:orders
                """);

        assertThrows(IOException.class, () -> extractor().extract(route, available()));
    }

    @Test
    void unknownStepGrammarFailsClosed() throws Exception {
        Path route = write("""
                - route:
                    from:
                      uri: direct:start
                      steps:
                        - invented-step:
                            value: ignored-before-catalog-binding
                """);

        IOException error = assertThrows(IOException.class, () -> extractor().extract(route, available()));

        assertTrue(error.getMessage().contains("Unknown Camel YAML step"));
    }

    @Test
    void pluralRoutesContainerPreservesStepClassification() throws Exception {
        Path route = write("""
                routes:
                  - route:
                      from:
                        uri: direct:start
                """);

        List<CatalogSubject> result = extractor().extract(route, available());

        assertTrue(result.contains(subject(Kind.EIP, "route")));
        assertTrue(result.contains(subject(Kind.EIP, "from")));
        assertTrue(result.contains(subject(Kind.COMPONENT, "direct")));
    }

    @Test
    void oversizedYamlIsRejectedBeforeParsing() throws Exception {
        Path route = project.resolve("route.camel.yaml");
        Files.writeString(route, " ".repeat(2 * 1024 * 1024 + 1));

        IOException error = assertThrows(IOException.class, () -> extractor().extract(route, available()));

        assertTrue(error.getMessage().contains("unsafe size"));
    }

    @Test
    void exactComponentMetadataProvesStaticUriAndYamlOptions() throws Exception {
        Path route = write("""
                - route:
                    from:
                      uri: direct:start?block=true
                      parameters:
                        timeout: 50
                """);

        CamelYamlCatalogUsageExtractor.Extraction result = extractor().extractBound(
                route, available(), List.of(directModel()));

        assertEquals(1, result.endpoints().size());
        CatalogUsageRecord.EndpointUsage endpoint = result.endpoints().get(0);
        assertEquals(1, endpoint.pathParameterCount());
        assertEquals(List.of("block", "timeout"), endpoint.endpointOptions());
    }

    @Test
    void endpointMetadataScalarsAreNotMisclassifiedAsUris() throws Exception {
        Path route = write("""
                - route:
                    from:
                      uri: direct:start
                      steps:
                        - to:
                            uri: direct:orders
                            id: deliver
                            description: "kafka: orders"
                            pattern: InOnly
                """);

        CamelYamlCatalogUsageExtractor.Extraction result = extractor().extractBound(
                route, available(), List.of(directModel()));

        assertFalse(result.subjects().contains(subject(Kind.COMPONENT, "kafka")));
        assertEquals(2, result.endpoints().size());
        assertTrue(result.endpoints().stream()
                .allMatch(endpoint -> endpoint.component().equals(subject(Kind.COMPONENT, "direct"))));
    }

    @Test
    void everyStaticEndpointFieldProducesExactlyValidatedUsage() throws Exception {
        Path route = write("""
                - route:
                    errorHandler:
                      deadLetterChannel:
                        deadLetterUri: direct:dead
                    from:
                      uri: direct:start
                      steps:
                        - saga:
                            compensation: direct:compensate
                            completion: direct:complete
                            steps:
                              - to: direct:done
                - interceptFrom:
                    uri: direct:incoming
                - interceptSendToEndpoint:
                    uri: direct:target
                    afterUri: direct:after
                    steps: []
                - endpointTransformer:
                    uri: direct:transform
                - endpointValidator:
                    uri: direct:validate
                """);

        CamelYamlCatalogUsageExtractor.Extraction result = extractor().extractBound(
                route, available(), List.of(directModel()));

        assertEquals(10, result.endpoints().size());
        assertEquals(List.of(subject(Kind.COMPONENT, "direct")), result.subjects().stream()
                .filter(subject -> subject.kind() == Kind.COMPONENT)
                .toList());
    }

    @Test
    void expressionDrivenAndServiceDiscoveredEndpointEipsFailClosed() throws Exception {
        for (String endpointStep : List.of(
                "- dynamicRouter:\n    simple: kafka:orders",
                "- enrich:\n    simple: kafka:orders",
                "- pollEnrich:\n    simple: kafka:orders",
                "- recipientList:\n    simple: kafka:orders",
                "- routingSlip:\n    simple: kafka:orders",
                // serviceCall is an endpoint EIP in the certified Camel 4.18.3 schema only.
                "- serviceCall: orders",
                "- serviceCall:\n    name: orders\n    component: kafka")) {
            Path route = endpointStepRoute(endpointStep);

            IOException error = assertThrows(IOException.class, () -> extractor().extractBound(
                    route, available(), List.of(directModel())), endpointStep);

            assertTrue(error.getMessage().contains("cannot catalog-prove"), error::getMessage);
        }
    }

    @Test
    void endpointFieldsOutsidePrimaryEndpointEipsValidateFrozenOptions() throws Exception {
        Path route = write("""
                - route:
                    errorHandler:
                      deadLetterChannel:
                        deadLetterUri: direct:dead?invented=true
                    from:
                      uri: direct:start
                """);

        IOException error = assertThrows(IOException.class, () -> extractor().extractBound(
                route, available(), List.of(directModel())));

        assertTrue(error.getMessage().contains("absent from frozen component metadata"));
    }

    @Test
    void endpointOptionAbsentFromFrozenMetadataFailsClosed() throws Exception {
        Path route = write("""
                - route:
                    from:
                      uri: direct:start?invented=true
                """);

        IOException error = assertThrows(IOException.class, () -> extractor().extractBound(
                route, available(), List.of(directModel())));

        assertTrue(error.getMessage().contains("absent from frozen component metadata"));
    }

    @Test
    void endpointOptionTypeAndDynamicValuesFailClosed() throws Exception {
        Path invalidType = write("""
                - route:
                    from:
                      uri: direct:start?block=perhaps
                """);
        assertThrows(IOException.class, () -> extractor().extractBound(
                invalidType, available(), List.of(directModel())));

        Path dynamic = write("""
                - route:
                    from:
                      uri: direct:start
                      parameters:
                        timeout: "${header.timeout}"
                """);
        IOException error = assertThrows(IOException.class, () -> extractor().extractBound(
                dynamic, available(), List.of(directModel())));
        assertTrue(error.getMessage().contains("Dynamic or unsafe"));
    }

    @Test
    void marshalMustSelectExactlyOneStaticDataformat() throws Exception {
        Path route = write("""
                - route:
                    from:
                      uri: direct:start
                      steps:
                        - marshal: {}
                """);

        IOException error = assertThrows(IOException.class, () -> extractor().extract(route, available()));

        assertTrue(error.getMessage().contains("exactly one static data format"));
    }

    private CamelYamlCatalogUsageExtractor extractor() {
        return new CamelYamlCatalogUsageExtractor();
    }

    private Path write(String content) throws IOException {
        Path route = project.resolve("route.camel.yaml");
        Files.writeString(route, content);
        return route;
    }

    private Path expressionRoute(String expression) throws IOException {
        String indented = expression.lines()
                .map(line -> "            " + line)
                .reduce((left, right) -> left + '\n' + right)
                .orElseThrow();
        return write("""
                - route:
                    from:
                      uri: direct:start
                      steps:
                        - setBody:
                """ + indented + '\n');
    }

    private Path endpointStepRoute(String endpointStep) throws IOException {
        String indented = endpointStep.lines()
                .map(line -> "        " + line)
                .reduce((left, right) -> left + '\n' + right)
                .orElseThrow();
        return write("""
                - route:
                    from:
                      uri: direct:start
                      steps:
                """ + indented + '\n');
    }

    private static List<CatalogSubject> available() {
        return List.of(
                subject(Kind.COMPONENT, "direct"),
                subject(Kind.COMPONENT, "kafka"),
                subject(Kind.EIP, "route"),
                subject(Kind.EIP, "from"),
                subject(Kind.EIP, "dynamicRouter"),
                subject(Kind.EIP, "enrich"),
                subject(Kind.EIP, "interceptFrom"),
                subject(Kind.EIP, "interceptSendToEndpoint"),
                subject(Kind.EIP, "endpointTransformer"),
                subject(Kind.EIP, "endpointValidator"),
                subject(Kind.EIP, "pollEnrich"),
                subject(Kind.EIP, "recipientList"),
                subject(Kind.EIP, "routingSlip"),
                subject(Kind.EIP, "serviceCall"),
                subject(Kind.EIP, "to"),
                subject(Kind.EIP, "setBody"),
                subject(Kind.EIP, "split"),
                subject(Kind.EIP, "saga"),
                subject(Kind.EIP, "marshal"),
                subject(Kind.DATAFORMAT, "csv"),
                subject(Kind.LANGUAGE, "simple"),
                subject(Kind.LANGUAGE, "jsonpath"));
    }

    private static CatalogSubject subject(Kind kind, String name) {
        return new CatalogSubject(kind, name);
    }

    private static CatalogComponentModel directModel() {
        CatalogSubject direct = subject(Kind.COMPONENT, "direct");
        SubjectEvidence evidence = new SubjectEvidence(
                direct, MavenCoordinate.jar("org.apache.camel", "camel-catalog", "4.21.0"),
                "sha256:" + "a".repeat(64),
                "org/apache/camel/catalog/components/direct.json", "sha256:" + "b".repeat(64),
                "org.apache.camel", "camel-direct", "4.21.0", false);
        return new CatalogComponentModel(
                evidence, "direct:name", false, List.of(
                        new CatalogComponentModel.Option(
                                "name", CatalogComponentModel.Scope.ENDPOINT, CatalogComponentModel.Kind.PATH,
                                0, "string", "java.lang.String", true, false, null, null, List.of()),
                        new CatalogComponentModel.Option(
                                "block", CatalogComponentModel.Scope.ENDPOINT, CatalogComponentModel.Kind.PARAMETER,
                                1, "boolean", "boolean", false, false, null, null, List.of()),
                        new CatalogComponentModel.Option(
                                "timeout", CatalogComponentModel.Scope.ENDPOINT, CatalogComponentModel.Kind.PARAMETER,
                                2, "integer", "long", false, false, null, null, List.of())));
    }
}
