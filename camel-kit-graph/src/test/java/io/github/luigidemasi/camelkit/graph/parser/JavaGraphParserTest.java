package io.github.luigidemasi.camelkit.graph.parser;

import java.nio.file.Path;
import java.util.List;

import io.github.luigidemasi.camelkit.graph.ProjectGraph;
import io.github.luigidemasi.camelkit.graph.model.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JavaGraphParserTest {

    private static ProjectGraph graph;
    private static final Path TEST_PROJECT = Path.of("src/test/resources/testdata");

    @BeforeAll
    static void setUp() {
        graph = new ProjectGraph();
        new JavaGraphParser().parse(TEST_PROJECT, graph);
    }

    @Test
    void parsesClasses() {
        assertTrue(graph.hasNode("class:com.example.OrderRoute"));
        assertTrue(graph.hasNode("class:com.example.OrderProcessor"));
        assertTrue(graph.hasNode("class:com.example.BaseRoute"));
    }

    @Test
    void parsesInheritance() {
        List<GraphEdge> extendsEdges = graph.getOutgoingEdges("class:com.example.OrderRoute").stream()
                .filter(e -> e.type() == EdgeType.EXTENDS)
                .toList();
        assertEquals(1, extendsEdges.size());
        assertEquals("class:com.example.BaseRoute", extendsEdges.get(0).to());
    }

    @Test
    void parsesMethods() {
        assertTrue(graph.hasNode("method:com.example.OrderProcessor.validate"));
        assertTrue(graph.hasNode("method:com.example.OrderProcessor.transform"));
    }

    @Test
    void createsDeclareEdges() {
        List<GraphEdge> declareEdges = graph.getOutgoingEdges("class:com.example.OrderProcessor").stream()
                .filter(e -> e.type() == EdgeType.DECLARES)
                .toList();
        assertTrue(declareEdges.size() >= 2);
    }

    @Test
    void parsesJavaDslRoutes() {
        assertTrue(graph.hasNode("route:processOrders"));
        assertEquals("kafka:orders",
                graph.getNode("route:processOrders").properties().get("fromUri"));
    }

    @Test
    void parsesRouteEndpoints() {
        assertTrue(graph.hasNode("endpoint:kafka:orders"));
        assertTrue(graph.hasNode("endpoint:direct:enrichOrder"));
        assertTrue(graph.hasNode("endpoint:seda:storeOrder"));
    }

    @Test
    void createsRoutesFromEdges() {
        List<GraphEdge> routesFrom = graph.getOutgoingEdges("route:processOrders").stream()
                .filter(e -> e.type() == EdgeType.ROUTES_FROM)
                .toList();
        assertEquals(1, routesFrom.size());
        assertEquals("endpoint:kafka:orders", routesFrom.get(0).to());
    }

    @Test
    void createsRoutesToEdges() {
        List<GraphEdge> routesTo = graph.getOutgoingEdges("route:processOrders").stream()
                .filter(e -> e.type() == EdgeType.ROUTES_TO)
                .toList();
        assertEquals(1, routesTo.size());
        assertEquals("endpoint:direct:enrichOrder", routesTo.get(0).to());
    }

    @Test
    void parsesMultipleRoutesInSameClass() {
        assertTrue(graph.hasNode("route:processOrders"));
        assertTrue(graph.hasNode("route:enrichOrder"));
    }

    @Test
    void enrichRouteHasCorrectEndpoints() {
        List<GraphEdge> routesTo = graph.getOutgoingEdges("route:enrichOrder").stream()
                .filter(e -> e.type() == EdgeType.ROUTES_TO)
                .toList();
        assertTrue(routesTo.size() >= 2,
                "enrichOrder route should have at least 2 endpoints (log:enriched and seda:storeOrder)");
    }

    @Nested
    class DiAnnotations {

        static ProjectGraph graph;

        @BeforeAll
        static void setUp() {
            graph = new ProjectGraph();
            new PomParser().parse(Path.of("src/test/resources/testdata/di"), graph);
            new JavaGraphParser().parse(Path.of("src/test/resources/testdata/di"), graph);
        }

        @Test
        void createsUsesTypeEdgeForInjectedField() {
            var edges = graph.getOutgoingEdges("class:com.example.di.OrderRoute").stream()
                    .filter(e -> e.type() == EdgeType.USES_TYPE)
                    .toList();
            assertTrue(edges.stream().anyMatch(e -> e.to().equals("class:com.example.di.OrderService")),
                    "OrderRoute should have USES_TYPE edge to OrderService");
        }

        @Test
        void marksInjectedEdge() {
            var edge = graph.getOutgoingEdges("class:com.example.di.OrderRoute").stream()
                    .filter(e -> e.type() == EdgeType.USES_TYPE && e.to().equals("class:com.example.di.OrderService"))
                    .findFirst()
                    .orElseThrow();
            assertEquals("true", edge.properties().get("injection"));
        }

        @Test
        void detectsBeanAnnotation() {
            var node = graph.getNode("class:com.example.di.OrderServiceImpl");
            assertNotNull(node);
            assertEquals("true", node.properties().get("bean"));
            assertEquals("orderService", node.properties().get("beanName"));
        }

        @Test
        void detectsSpringServiceAnnotation() {
            var node = graph.getNode("class:com.example.di.PaymentProcessor");
            assertNotNull(node);
            assertEquals("true", node.properties().get("bean"));
            assertEquals("paymentProcessor", node.properties().get("beanName"));
        }

        @Test
        void createsUsesTypeForAutowiredField() {
            var edges = graph.getOutgoingEdges("class:com.example.di.PaymentProcessor").stream()
                    .filter(e -> e.type() == EdgeType.USES_TYPE && e.to().equals("class:com.example.di.OrderService"))
                    .toList();
            assertFalse(edges.isEmpty(), "PaymentProcessor should have USES_TYPE to OrderService via @Autowired");
            assertEquals("true", edges.get(0).properties().get("injection"));
        }

        @Test
        void skipsJdkTypes() {
            var edges = graph.getOutgoingEdges("class:com.example.di.PaymentProcessor").stream()
                    .filter(e -> e.type() == EdgeType.USES_TYPE)
                    .toList();
            assertTrue(edges.stream().noneMatch(e -> e.to().contains("java.lang.String")),
                    "Should NOT create USES_TYPE edge to String");
        }
    }
}
