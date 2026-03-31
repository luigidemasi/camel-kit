package io.github.luigidemasi.camelkit.graph.parser;

import io.github.luigidemasi.camelkit.graph.ProjectGraph;
import io.github.luigidemasi.camelkit.graph.model.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class YamlRouteParserTest {

    private static ProjectGraph graph;
    private static final Path TEST_PROJECT = Path.of("src/test/resources/testdata");

    @BeforeAll
    static void setUp() {
        graph = new ProjectGraph();
        new YamlRouteParser().parse(TEST_PROJECT, graph);
    }

    @Test
    void parsesYamlRoute() {
        assertTrue(graph.hasNode("route:yamlHttpRoute"));
    }

    @Test
    void parsesFromEndpoint() {
        assertEquals("platform-http:/api/orders",
            graph.getNode("route:yamlHttpRoute").properties().get("fromUri"));
    }

    @Test
    void parsesToEndpoints() {
        List<GraphEdge> routesTo = graph.getOutgoingEdges("route:yamlHttpRoute").stream()
            .filter(e -> e.type() == EdgeType.ROUTES_TO)
            .toList();
        assertEquals(1, routesTo.size());
        assertEquals("endpoint:direct:enrichOrder", routesTo.get(0).to());
    }

    @Test
    void parsesProcessors() {
        List<GraphEdge> processors = graph.getOutgoingEdges("route:yamlHttpRoute").stream()
            .filter(e -> e.type() == EdgeType.PROCESSES)
            .toList();
        assertTrue(processors.size() >= 1); // log step
    }
}
