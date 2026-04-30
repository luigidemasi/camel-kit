package io.github.luigidemasi.camelkit.graph.query;

import java.nio.file.Path;
import java.util.List;

import io.github.luigidemasi.camelkit.graph.GraphBuilder;
import io.github.luigidemasi.camelkit.graph.ProjectGraph;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RouteTopologyTest {

    private static RouteTopology topology;

    @BeforeAll
    static void setUp() {
        ProjectGraph graph = new GraphBuilder().build(
                Path.of("src/test/resources/testdata"));
        topology = new RouteTopology(graph);
    }

    @Test
    void buildTopology() {
        var topo = topology.build();
        assertFalse(topo.isEmpty());
    }

    @Test
    void topologyContainsRoutes() {
        var topo = topology.build();
        assertTrue(topo.containsKey("processOrders") || topo.containsKey("enrichOrder")
                || topo.containsKey("xmlFileRoute"));
    }

    @Test
    void routeConnectionsIncludeScheme() {
        var topo = topology.build();
        // At least one connection should exist via direct or seda
        boolean hasConnection = topo.values().stream()
                .flatMap(List::stream)
                .anyMatch(conn -> "direct".equals(conn.scheme()) || "seda".equals(conn.scheme()));
        assertTrue(hasConnection);
    }
}
