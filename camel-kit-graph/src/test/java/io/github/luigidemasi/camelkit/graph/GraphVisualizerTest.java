package io.github.luigidemasi.camelkit.graph;

import java.util.Map;

import io.github.luigidemasi.camelkit.graph.model.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class GraphVisualizerTest {

    @ParameterizedTest
    @ValueSource(strings = {"cytoscape", "d3", "vis-network", "antv-g6"})
    void generatesValidHtml(String library) {
        ProjectGraph graph = new ProjectGraph();
        graph.addNode(new GraphNode("route:test", NodeType.CAMEL_ROUTE, Map.of("name", "test")));
        graph.addNode(new GraphNode("endpoint:direct:in", NodeType.CAMEL_ENDPOINT, Map.of("uri", "direct:in")));
        graph.addEdge(new GraphEdge("route:test", "endpoint:direct:in", EdgeType.ROUTES_FROM, Map.of()));
        String html = GraphVisualizer.generate(graph, library);
        assertTrue(html.contains("<!DOCTYPE html>"), "Should be valid HTML for " + library);
        assertTrue(html.contains("route:test"), "Should contain node data for " + library);
        assertTrue(html.contains("Camel-Kit Project Graph"), "Should have title for " + library);
    }

    @Test
    void defaultLibraryIsCytoscape() {
        ProjectGraph graph = new ProjectGraph();
        String html = GraphVisualizer.generate(graph);
        assertTrue(html.contains("cytoscape"));
    }

    @Test
    void emptyGraphProducesValidHtml() {
        String html = GraphVisualizer.generate(new ProjectGraph(), "cytoscape");
        assertTrue(html.contains("<!DOCTYPE html>"));
    }

    @Test
    void escapesScriptEndTagsInEmbeddedJson() {
        ProjectGraph graph = new ProjectGraph();
        graph.addNode(new GraphNode(
                "route:unsafe", NodeType.CAMEL_ROUTE, Map.of("name", "</script><script>alert(1)</script>")));

        String html = GraphVisualizer.generate(graph);

        assertFalse(html.contains("</script><script>alert(1)</script>"));
        assertTrue(html.contains("\\u003C/script>"));
    }
}
