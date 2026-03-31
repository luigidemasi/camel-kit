package io.github.luigidemasi.camelkit.graph.query;

import io.github.luigidemasi.camelkit.graph.GraphBuilder;
import io.github.luigidemasi.camelkit.graph.ProjectGraph;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RouteFlowTracerTest {

    private static RouteFlowTracer tracer;

    @BeforeAll
    static void setUp() {
        ProjectGraph graph = new GraphBuilder().build(
            Path.of("src/test/resources/testdata"));
        tracer = new RouteFlowTracer(graph);
    }

    @Test
    void traceFromRoute() {
        List<RouteFlowTracer.FlowStep> flow = tracer.trace("route:processOrders");
        assertFalse(flow.isEmpty());
        assertEquals("kafka:orders", flow.get(0).label());
    }

    @Test
    void traceFollowsDirectLinks() {
        List<RouteFlowTracer.FlowStep> flow = tracer.trace("route:processOrders");
        // processOrders -> to(direct:enrichOrder) -> enrichOrder route should be followed
        boolean reachesEnrichRoute = flow.stream()
            .anyMatch(step -> step.label().contains("enrichOrder"));
        assertTrue(reachesEnrichRoute);
    }

    @Test
    void traceFromEndpoint() {
        List<RouteFlowTracer.FlowStep> flow = tracer.traceFromEndpoint("endpoint:kafka:orders");
        assertFalse(flow.isEmpty());
    }

    @Test
    void traceDetectsCycles() {
        // Should not infinite-loop on circular routes
        List<RouteFlowTracer.FlowStep> flow = tracer.trace("route:processOrders");
        assertTrue(flow.size() < 100); // reasonable bound
    }
}
