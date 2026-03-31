package io.github.luigidemasi.camelkit.graph;

import io.github.luigidemasi.camelkit.graph.model.*;
import io.github.luigidemasi.camelkit.graph.query.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FullGraphIntegrationTest {

    private static ProjectGraph graph;
    private static final Path TEST_PROJECT = Path.of("src/test/resources/testdata");

    @BeforeAll
    static void setUp() {
        graph = new GraphBuilder().build(TEST_PROJECT);
    }

    @Test
    void graphContainsNodesFromAllParsers() {
        assertTrue(graph.findByType(NodeType.CLASS).size() >= 3, "Java + Groovy classes");
        assertTrue(graph.findByType(NodeType.CAMEL_ROUTE).size() >= 4, "Java + XML + YAML + Groovy routes");
        assertTrue(graph.findByType(NodeType.CAMEL_ENDPOINT).size() >= 4, "Multiple endpoints");
        assertTrue(graph.findByType(NodeType.MAVEN_ARTIFACT).size() >= 3, "POM deps");
        assertTrue(graph.findByType(NodeType.CONFIG_PROPERTY).size() >= 2, "Camel properties");
    }

    @Test
    void crossLinkerCreatedLinks() {
        long linksTo = graph.getEdges().stream().filter(e -> e.type() == EdgeType.LINKS_TO).count();
        long usesComponent = graph.getEdges().stream().filter(e -> e.type() == EdgeType.USES_COMPONENT).count();
        long configures = graph.getEdges().stream().filter(e -> e.type() == EdgeType.CONFIGURES).count();

        assertTrue(linksTo >= 1, "At least one direct/seda link");
        assertTrue(usesComponent >= 1, "At least one component-to-artifact link");
        assertTrue(configures >= 1, "At least one config-to-endpoint link");
    }

    @Test
    void queryFindWorks() {
        GraphQuery query = new GraphQuery(graph);
        List<GraphNode> results = query.find("Order", null);
        assertFalse(results.isEmpty());
    }

    @Test
    void routeTopologyIsConnected() {
        RouteTopology topology = new RouteTopology(graph);
        Map<String, List<RouteTopology.RouteConnection>> topo = topology.build();
        assertFalse(topo.isEmpty(), "Routes should be interconnected via direct/seda");
    }

    @Test
    void routeFlowTracerTraversesAcrossRoutes() {
        RouteFlowTracer tracer = new RouteFlowTracer(graph);
        List<RouteFlowTracer.FlowStep> flow = tracer.trace("route:processOrders");
        assertTrue(flow.size() >= 3, "Should traverse from, processors, and to");
    }

    @Test
    void serializationRoundTrip(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("test-graph.json");
        GraphSerializer.write(graph, file, TEST_PROJECT.toAbsolutePath().toString());

        ProjectGraph loaded = GraphSerializer.read(file);
        assertEquals(graph.nodeCount(), loaded.nodeCount());
        assertEquals(graph.edgeCount(), loaded.edgeCount());
    }
}
