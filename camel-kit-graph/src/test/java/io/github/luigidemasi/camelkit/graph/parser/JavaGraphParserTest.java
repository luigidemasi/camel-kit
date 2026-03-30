package io.github.luigidemasi.camelkit.graph.parser;

import io.github.luigidemasi.camelkit.graph.ProjectGraph;
import io.github.luigidemasi.camelkit.graph.model.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

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
        assertTrue(routesTo.size() >= 2, "enrichOrder route should have at least 2 endpoints (log:enriched and seda:storeOrder)");
    }
}
