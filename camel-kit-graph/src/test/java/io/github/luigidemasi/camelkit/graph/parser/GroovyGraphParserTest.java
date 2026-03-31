package io.github.luigidemasi.camelkit.graph.parser;

import io.github.luigidemasi.camelkit.graph.ProjectGraph;
import io.github.luigidemasi.camelkit.graph.model.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GroovyGraphParserTest {

    private static ProjectGraph graph;
    private static final Path TEST_PROJECT = Path.of("src/test/resources/testdata");

    @BeforeAll
    static void setUp() {
        graph = new ProjectGraph();
        new GroovyGraphParser().parse(TEST_PROJECT, graph);
    }

    @Test
    void parsesGroovyClass() {
        assertTrue(graph.hasNode("class:GroovyRoute"));
    }

    @Test
    void parsesGroovyDslRoute() {
        assertTrue(graph.hasNode("route:groovyTimer"));
    }

    @Test
    void parsesFromEndpoint() {
        assertEquals("timer:groovyTick?period=5000",
            graph.getNode("route:groovyTimer").properties().get("fromUri"));
    }

    @Test
    void parsesToEndpoints() {
        List<GraphEdge> routesTo = graph.getOutgoingEdges("route:groovyTimer").stream()
            .filter(e -> e.type() == EdgeType.ROUTES_TO)
            .toList();
        assertEquals(1, routesTo.size());
        assertEquals("endpoint:direct:enrichOrder", routesTo.get(0).to());
    }

    @Test
    void detectsInlineGroovyScript() {
        List<GraphEdge> processors = graph.getOutgoingEdges("route:groovyTimer").stream()
            .filter(e -> e.type() == EdgeType.PROCESSES)
            .toList();
        boolean hasGroovyScript = processors.stream()
            .map(e -> graph.getNode(e.to()))
            .anyMatch(n -> "script-groovy".equals(n.properties().get("type")));
        assertTrue(hasGroovyScript);
    }

    @Test
    void parsesStandaloneGroovyScript() {
        assertTrue(graph.hasNode("resource:groovy/transform.groovy"));
        assertEquals(NodeType.RESOURCE_FILE,
            graph.getNode("resource:groovy/transform.groovy").type());
    }
}
