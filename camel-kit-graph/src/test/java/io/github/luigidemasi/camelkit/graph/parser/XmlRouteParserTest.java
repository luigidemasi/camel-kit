package io.github.luigidemasi.camelkit.graph.parser;

import io.github.luigidemasi.camelkit.graph.ProjectGraph;
import io.github.luigidemasi.camelkit.graph.model.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class XmlRouteParserTest {

    private static ProjectGraph graph;
    private static final Path TEST_PROJECT = Path.of("src/test/resources/testdata");

    @BeforeAll
    static void setUp() {
        graph = new ProjectGraph();
        new XmlRouteParser().parse(TEST_PROJECT, graph);
    }

    @Test
    void parsesXmlRoutes() {
        assertTrue(graph.hasNode("route:xmlFileRoute"));
        assertTrue(graph.hasNode("route:xmlSedaRoute"));
    }

    @Test
    void parsesFromEndpoint() {
        assertEquals("file:input",
            graph.getNode("route:xmlFileRoute").properties().get("fromUri"));
    }

    @Test
    void parsesToEndpoints() {
        assertTrue(graph.hasNode("endpoint:direct:enrichOrder"));
        assertTrue(graph.hasNode("endpoint:log:stored"));
    }

    @Test
    void createsRoutesFromEdge() {
        List<GraphEdge> routesFrom = graph.getOutgoingEdges("route:xmlFileRoute").stream()
            .filter(e -> e.type() == EdgeType.ROUTES_FROM)
            .toList();
        assertEquals(1, routesFrom.size());
        assertEquals("endpoint:file:input", routesFrom.get(0).to());
    }

    @Test
    void createsRoutesToEdge() {
        List<GraphEdge> routesTo = graph.getOutgoingEdges("route:xmlFileRoute").stream()
            .filter(e -> e.type() == EdgeType.ROUTES_TO)
            .toList();
        assertEquals(1, routesTo.size());
    }

    @Test
    void parsesEipProcessors() {
        List<GraphEdge> processors = graph.getOutgoingEdges("route:xmlFileRoute").stream()
            .filter(e -> e.type() == EdgeType.PROCESSES)
            .toList();
        assertTrue(processors.size() >= 1); // at least filter
    }
}
