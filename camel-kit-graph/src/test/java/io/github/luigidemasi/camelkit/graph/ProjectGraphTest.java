package io.github.luigidemasi.camelkit.graph;

import java.util.List;
import java.util.Map;

import io.github.luigidemasi.camelkit.graph.model.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProjectGraphTest {

    private ProjectGraph graph;

    @BeforeEach
    void setUp() {
        graph = new ProjectGraph();
    }

    @Test
    void addAndRetrieveNode() {
        GraphNode node = new GraphNode(
                "class:com.example.Foo", NodeType.CLASS,
                Map.of("name", "Foo"));
        graph.addNode(node);
        assertTrue(graph.hasNode("class:com.example.Foo"));
        assertEquals(node, graph.getNode("class:com.example.Foo"));
    }

    @Test
    void addAndRetrieveEdge() {
        GraphNode from = new GraphNode("class:Foo", NodeType.CLASS, Map.of());
        GraphNode to = new GraphNode("class:Bar", NodeType.CLASS, Map.of());
        graph.addNode(from);
        graph.addNode(to);
        GraphEdge edge = new GraphEdge("class:Foo", "class:Bar", EdgeType.EXTENDS, Map.of());
        graph.addEdge(edge);
        assertEquals(1, graph.getEdges().size());
        assertEquals(edge, graph.getEdges().get(0));
    }

    @Test
    void findNodesByType() {
        graph.addNode(new GraphNode("class:Foo", NodeType.CLASS, Map.of()));
        graph.addNode(new GraphNode("class:Bar", NodeType.CLASS, Map.of()));
        graph.addNode(new GraphNode("route:r1", NodeType.CAMEL_ROUTE, Map.of()));
        List<GraphNode> classes = graph.findByType(NodeType.CLASS);
        assertEquals(2, classes.size());
        List<GraphNode> routes = graph.findByType(NodeType.CAMEL_ROUTE);
        assertEquals(1, routes.size());
    }

    @Test
    void getOutgoingEdges() {
        graph.addNode(new GraphNode("class:Foo", NodeType.CLASS, Map.of()));
        graph.addNode(new GraphNode("method:Foo.bar", NodeType.METHOD, Map.of()));
        graph.addNode(new GraphNode("method:Foo.baz", NodeType.METHOD, Map.of()));
        graph.addEdge(new GraphEdge("class:Foo", "method:Foo.bar", EdgeType.DECLARES, Map.of()));
        graph.addEdge(new GraphEdge("class:Foo", "method:Foo.baz", EdgeType.DECLARES, Map.of()));
        List<GraphEdge> outgoing = graph.getOutgoingEdges("class:Foo");
        assertEquals(2, outgoing.size());
    }

    @Test
    void getIncomingEdges() {
        graph.addNode(new GraphNode("class:Foo", NodeType.CLASS, Map.of()));
        graph.addNode(new GraphNode("class:Bar", NodeType.CLASS, Map.of()));
        graph.addEdge(new GraphEdge("class:Bar", "class:Foo", EdgeType.EXTENDS, Map.of()));
        List<GraphEdge> incoming = graph.getIncomingEdges("class:Foo");
        assertEquals(1, incoming.size());
        assertEquals("class:Bar", incoming.get(0).from());
    }

    @Test
    void nodeAndEdgeCounts() {
        graph.addNode(new GraphNode("class:A", NodeType.CLASS, Map.of()));
        graph.addNode(new GraphNode("class:B", NodeType.CLASS, Map.of()));
        graph.addEdge(new GraphEdge("class:A", "class:B", EdgeType.EXTENDS, Map.of()));
        assertEquals(2, graph.nodeCount());
        assertEquals(1, graph.edgeCount());
    }

    @Test
    void duplicateNodeIsOverwritten() {
        graph.addNode(new GraphNode("class:Foo", NodeType.CLASS, Map.of("name", "old")));
        graph.addNode(new GraphNode("class:Foo", NodeType.CLASS, Map.of("name", "new")));
        assertEquals(1, graph.nodeCount());
        assertEquals("new", graph.getNode("class:Foo").properties().get("name"));
    }

    @Test
    void mergeGraphs() {
        ProjectGraph other = new ProjectGraph();
        other.addNode(new GraphNode("class:Other", NodeType.CLASS, Map.of()));
        other.addEdge(new GraphEdge("class:Other", "class:Other", EdgeType.EXTENDS, Map.of()));
        graph.addNode(new GraphNode("class:Mine", NodeType.CLASS, Map.of()));
        graph.merge(other);
        assertEquals(2, graph.nodeCount());
        assertTrue(graph.hasNode("class:Other"));
    }

    @Test
    void accessorsReturnSnapshots() {
        graph.addNode(new GraphNode("class:Original", NodeType.CLASS, Map.of()));
        graph.addEdge(new GraphEdge("class:Original", "class:Original", EdgeType.CALLS, Map.of()));

        Map<String, GraphNode> nodes = graph.getNodes();
        List<GraphEdge> edges = graph.getEdges();

        graph.addNode(new GraphNode("class:Later", NodeType.CLASS, Map.of()));
        graph.addEdge(new GraphEdge("class:Later", "class:Original", EdgeType.CALLS, Map.of()));

        assertEquals(1, nodes.size());
        assertEquals(1, edges.size());
        assertThrows(UnsupportedOperationException.class,
                () -> nodes.put("class:Rejected", new GraphNode("class:Rejected", NodeType.CLASS, Map.of())));
        assertThrows(UnsupportedOperationException.class,
                () -> edges.add(new GraphEdge("class:Rejected", "class:Original", EdgeType.CALLS, Map.of())));
    }
}
