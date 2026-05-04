package io.github.luigidemasi.camelkit.graph.query;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.luigidemasi.camelkit.graph.ProjectGraph;
import io.github.luigidemasi.camelkit.graph.model.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExpandWithInterfacesTest {

    static ProjectGraph graph;

    @BeforeAll
    static void setUp() {
        graph = new ProjectGraph();

        graph.addNode(new GraphNode(
                "route:processOrders", NodeType.CAMEL_ROUTE,
                Map.of("routeId", "processOrders")));
        graph.addNode(new GraphNode(
                "class:OrderRoute", NodeType.CLASS,
                Map.of("name", "OrderRoute")));
        graph.addNode(new GraphNode(
                "class:OrderService", NodeType.CLASS,
                Map.of("name", "OrderService", "interface", "true")));
        graph.addNode(new GraphNode(
                "class:OrderServiceImpl", NodeType.CLASS,
                Map.of("name", "OrderServiceImpl")));
        graph.addNode(new GraphNode(
                "class:AuditLogger", NodeType.CLASS,
                Map.of("name", "AuditLogger")));
        graph.addNode(new GraphNode(
                "endpoint:kafka:orders", NodeType.CAMEL_ENDPOINT,
                Map.of("scheme", "kafka")));

        graph.addEdge(new GraphEdge("class:OrderRoute", "route:processOrders", EdgeType.DECLARES, Map.of()));
        graph.addEdge(new GraphEdge("route:processOrders", "endpoint:kafka:orders", EdgeType.ROUTES_FROM, Map.of()));
        graph.addEdge(new GraphEdge(
                "class:OrderRoute", "class:OrderService", EdgeType.USES_TYPE,
                Map.of("injection", "true")));
        graph.addEdge(new GraphEdge("class:OrderServiceImpl", "class:OrderService", EdgeType.IMPLEMENTS, Map.of()));
        graph.addEdge(new GraphEdge("class:OrderServiceImpl", "class:AuditLogger", EdgeType.USES_TYPE, Map.of()));
    }

    @Test
    void expandsCrossInterfaceBoundary() {
        GraphQuery query = new GraphQuery(graph);
        Set<GraphNode> expanded = query.expandWithInterfaces("route:processOrders", "both", 5);
        Set<String> ids = expanded.stream().map(GraphNode::id).collect(Collectors.toSet());

        assertTrue(ids.contains("class:OrderRoute"), "Should include the declaring class");
        assertTrue(ids.contains("class:OrderService"), "Should include the interface");
        assertTrue(ids.contains("class:OrderServiceImpl"), "Should cross interface boundary to find implementor");
        assertTrue(ids.contains("class:AuditLogger"), "Should continue BFS from implementor");
        assertTrue(ids.contains("endpoint:kafka:orders"), "Should include route endpoints");
    }

    @Test
    void respectsDepthLimit() {
        GraphQuery query = new GraphQuery(graph);
        Set<GraphNode> expanded = query.expandWithInterfaces("route:processOrders", "both", 1);
        Set<String> ids = expanded.stream().map(GraphNode::id).collect(Collectors.toSet());

        assertTrue(ids.contains("class:OrderRoute"), "Depth 1 should reach OrderRoute");
        assertTrue(ids.contains("endpoint:kafka:orders"), "Depth 1 should reach endpoint");
    }
}
