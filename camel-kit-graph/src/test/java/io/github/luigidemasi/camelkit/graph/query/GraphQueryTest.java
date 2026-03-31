package io.github.luigidemasi.camelkit.graph.query;

import io.github.luigidemasi.camelkit.graph.ProjectGraph;
import io.github.luigidemasi.camelkit.graph.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GraphQueryTest {

    private GraphQuery query;

    @BeforeEach
    void setUp() {
        ProjectGraph graph = new ProjectGraph();
        graph.addNode(new GraphNode("class:com.example.Foo", NodeType.CLASS, Map.of("name", "Foo", "package", "com.example")));
        graph.addNode(new GraphNode("class:com.example.Bar", NodeType.CLASS, Map.of("name", "Bar", "package", "com.example")));
        graph.addNode(new GraphNode("method:com.example.Foo.process", NodeType.METHOD, Map.of("name", "process")));
        graph.addNode(new GraphNode("method:com.example.Bar.handle", NodeType.METHOD, Map.of("name", "handle")));
        graph.addNode(new GraphNode("route:r1", NodeType.CAMEL_ROUTE, Map.of("routeId", "r1")));
        graph.addNode(new GraphNode("endpoint:direct:foo", NodeType.CAMEL_ENDPOINT, Map.of("uri", "direct:foo", "scheme", "direct")));

        graph.addEdge(new GraphEdge("class:com.example.Foo", "class:com.example.Bar", EdgeType.EXTENDS, Map.of()));
        graph.addEdge(new GraphEdge("class:com.example.Foo", "method:com.example.Foo.process", EdgeType.DECLARES, Map.of()));
        graph.addEdge(new GraphEdge("method:com.example.Foo.process", "method:com.example.Bar.handle", EdgeType.CALLS, Map.of()));
        graph.addEdge(new GraphEdge("route:r1", "endpoint:direct:foo", EdgeType.ROUTES_TO, Map.of()));

        query = new GraphQuery(graph);
    }

    @Test
    void findByName() {
        List<GraphNode> results = query.find("Foo", null);
        assertEquals(1, results.size());
        assertEquals("class:com.example.Foo", results.get(0).id());
    }

    @Test
    void findByNameWithTypeFilter() {
        List<GraphNode> results = query.find("process", NodeType.METHOD);
        assertEquals(1, results.size());
    }

    @Test
    void findByPattern() {
        List<GraphNode> results = query.find("com.example.*", null);
        assertTrue(results.size() >= 2);
    }

    @Test
    void neighborsOutgoing() {
        var result = query.neighbors("class:com.example.Foo", "out", null, 1);
        assertTrue(result.nodes().size() >= 2); // Bar (extends) + process (declares)
    }

    @Test
    void neighborsIncoming() {
        var result = query.neighbors("class:com.example.Bar", "in", null, 1);
        assertEquals(1, result.nodes().size()); // Foo extends Bar
    }

    @Test
    void neighborsFilteredByEdgeType() {
        var result = query.neighbors("class:com.example.Foo", "out", EdgeType.EXTENDS, 1);
        assertEquals(1, result.nodes().size());
        assertEquals("class:com.example.Bar", result.nodes().get(0).id());
    }

    @Test
    void shortestPath() {
        var path = query.path("class:com.example.Foo", "method:com.example.Bar.handle", 5);
        assertFalse(path.isEmpty());
        assertEquals("class:com.example.Foo", path.get(0).id());
    }

    @Test
    void subgraph() {
        var result = query.subgraph("class:com.example.Foo", 1);
        assertTrue(result.nodes().size() >= 2);
        assertFalse(result.edges().isEmpty());
    }

    @Test
    void stats() {
        var stats = query.stats();
        assertEquals(2, stats.get("CLASS"));
        assertEquals(2, stats.get("METHOD"));
        assertEquals(1, stats.get("CAMEL_ROUTE"));
    }

    @Test
    void impactDownstream() {
        var impacted = query.impact("class:com.example.Foo", "downstream");
        assertTrue(impacted.size() >= 2);
    }
}
