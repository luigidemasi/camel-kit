package io.github.luigidemasi.camelkit.graph.mcp;

import io.github.luigidemasi.camelkit.graph.GraphBuilder;
import io.github.luigidemasi.camelkit.graph.ProjectGraph;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class GraphMcpToolsTest {

    private static GraphMcpTools tools;

    @BeforeAll
    static void setUp() {
        ProjectGraph graph = new GraphBuilder().build(
            Path.of("../camel-kit-graph/src/test/resources/testdata"));
        tools = new GraphMcpTools();
        tools.service = new GraphMcpService();
        tools.service.setGraphForTesting(graph);
    }

    @Test
    void graphFindByName() {
        String result = tools.graph_find("Order", null);
        assertTrue(result.contains("\"found\":true"));
        assertTrue(result.contains("OrderRoute") || result.contains("OrderProcessor"));
    }

    @Test
    void graphFindByType() {
        String result = tools.graph_find(".*", "CAMEL_ROUTE");
        assertTrue(result.contains("\"found\":true"));
        assertTrue(result.contains("CAMEL_ROUTE"));
    }

    @Test
    void graphFindNoMatch() {
        String result = tools.graph_find("NonExistent12345", null);
        assertTrue(result.contains("\"total\":0"));
    }

    @Test
    void graphFindNullQueryReturnsAll() {
        String result = tools.graph_find(null, null);
        assertTrue(result.contains("\"found\":true"));
        assertTrue(result.contains("\"total\":"));
        assertFalse(result.contains("\"total\":0"));
    }

    @Test
    void graphNeighborsOutgoing() {
        String result = tools.graph_neighbors("route:processOrders", "out", null, 1);
        assertTrue(result.contains("\"found\":true"));
    }

    @Test
    void graphNeighborsUnknownNode() {
        String result = tools.graph_neighbors("route:nonexistent", "out", null, 1);
        assertTrue(result.contains("\"found\":false") || result.contains("\"total\":0"));
    }

    @Test
    void graphStats() {
        String result = tools.graph_stats();
        assertTrue(result.contains("\"nodes\""));
        assertTrue(result.contains("\"edges\""));
        assertTrue(result.contains("CAMEL_ROUTE"));
    }

    @Test
    void graphStatsNoGraph() {
        GraphMcpTools emptyTools = new GraphMcpTools();
        emptyTools.service = new GraphMcpService();
        String result = emptyTools.graph_stats();
        assertTrue(result.contains("\"available\":false"));
    }

    @Test
    void graphPath() {
        String result = tools.graph_path("route:processOrders", "endpoint:kafka:orders", 5);
        assertTrue(result.contains("\"found\":true") || result.contains("\"found\":false"));
    }

    @Test
    void graphSubgraph() {
        String result = tools.graph_subgraph("route:processOrders", 1);
        assertTrue(result.contains("\"nodes\""));
        assertTrue(result.contains("\"edges\""));
    }

    @Test
    void graphImpactDownstream() {
        String result = tools.graph_impact("route:processOrders", "downstream");
        assertTrue(result.contains("\"found\":true") || result.contains("\"total\""));
    }

    @Test
    void graphImpactUpstream() {
        String result = tools.graph_impact("route:enrichOrder", "upstream");
        assertNotNull(result);
    }

    @Test
    void graphRouteFlow() {
        String result = tools.graph_route_flow("route:processOrders", null);
        assertTrue(result.contains("\"steps\""));
        assertTrue(result.contains("kafka:orders"));
    }

    @Test
    void graphRouteFlowFromEndpoint() {
        String result = tools.graph_route_flow(null, "endpoint:kafka:orders");
        assertTrue(result.contains("\"steps\""));
    }

    @Test
    void graphRouteFlowNoGraph() {
        GraphMcpTools emptyTools = new GraphMcpTools();
        emptyTools.service = new GraphMcpService();
        String result = emptyTools.graph_route_flow("route:processOrders", null);
        assertTrue(result.contains("\"available\":false"));
    }

    @Test
    void graphRouteTopology() {
        String result = tools.graph_route_topology();
        assertTrue(result.contains("\"routes\""));
    }
}
