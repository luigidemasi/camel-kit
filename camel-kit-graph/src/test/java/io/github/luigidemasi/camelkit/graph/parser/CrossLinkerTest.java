package io.github.luigidemasi.camelkit.graph.parser;

import io.github.luigidemasi.camelkit.graph.ProjectGraph;
import io.github.luigidemasi.camelkit.graph.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CrossLinkerTest {

    private ProjectGraph graph;

    @BeforeEach
    void setUp() {
        graph = new ProjectGraph();
    }

    @Test
    void linksDirectEndpoints() {
        graph.addNode(new GraphNode("route:routeA", NodeType.CAMEL_ROUTE, Map.of("routeId", "routeA", "fromUri", "kafka:input")));
        graph.addNode(new GraphNode("route:routeB", NodeType.CAMEL_ROUTE, Map.of("routeId", "routeB", "fromUri", "direct:foo")));
        graph.addNode(new GraphNode("endpoint:direct:foo", NodeType.CAMEL_ENDPOINT, Map.of("uri", "direct:foo", "scheme", "direct")));
        graph.addNode(new GraphNode("endpoint:kafka:input", NodeType.CAMEL_ENDPOINT, Map.of("uri", "kafka:input", "scheme", "kafka")));
        graph.addEdge(new GraphEdge("route:routeA", "endpoint:direct:foo", EdgeType.ROUTES_TO, Map.of()));
        graph.addEdge(new GraphEdge("route:routeB", "endpoint:direct:foo", EdgeType.ROUTES_FROM, Map.of()));
        new CrossLinker().link(graph);
        List<GraphEdge> links = graph.getEdges().stream().filter(e -> e.type() == EdgeType.LINKS_TO).toList();
        assertEquals(1, links.size());
    }

    @Test
    void linksSedaEndpoints() {
        graph.addNode(new GraphNode("route:routeA", NodeType.CAMEL_ROUTE, Map.of("routeId", "routeA", "fromUri", "timer:tick")));
        graph.addNode(new GraphNode("route:routeB", NodeType.CAMEL_ROUTE, Map.of("routeId", "routeB", "fromUri", "seda:queue")));
        graph.addNode(new GraphNode("endpoint:seda:queue", NodeType.CAMEL_ENDPOINT, Map.of("uri", "seda:queue", "scheme", "seda")));
        graph.addEdge(new GraphEdge("route:routeA", "endpoint:seda:queue", EdgeType.ROUTES_TO, Map.of()));
        graph.addEdge(new GraphEdge("route:routeB", "endpoint:seda:queue", EdgeType.ROUTES_FROM, Map.of()));
        new CrossLinker().link(graph);
        List<GraphEdge> links = graph.getEdges().stream().filter(e -> e.type() == EdgeType.LINKS_TO).toList();
        assertEquals(1, links.size());
    }

    @Test
    void linksComponentToMavenArtifact() {
        graph.addNode(new GraphNode("route:r1", NodeType.CAMEL_ROUTE, Map.of("routeId", "r1", "fromUri", "kafka:topic")));
        graph.addNode(new GraphNode("endpoint:kafka:topic", NodeType.CAMEL_ENDPOINT, Map.of("uri", "kafka:topic", "scheme", "kafka")));
        graph.addNode(new GraphNode("maven:org.apache.camel:camel-kafka", NodeType.MAVEN_ARTIFACT, Map.of("artifactId", "camel-kafka")));
        graph.addEdge(new GraphEdge("route:r1", "endpoint:kafka:topic", EdgeType.ROUTES_FROM, Map.of()));
        new CrossLinker().link(graph);
        List<GraphEdge> usesComponent = graph.getEdges().stream().filter(e -> e.type() == EdgeType.USES_COMPONENT).toList();
        assertEquals(1, usesComponent.size());
        assertEquals("maven:org.apache.camel:camel-kafka", usesComponent.get(0).to());
    }

    @Test
    void linksConfigToEndpoint() {
        graph.addNode(new GraphNode("config:camel.component.kafka.brokers", NodeType.CONFIG_PROPERTY, Map.of("key", "camel.component.kafka.brokers", "value", "localhost:9092")));
        graph.addNode(new GraphNode("endpoint:kafka:topic", NodeType.CAMEL_ENDPOINT, Map.of("uri", "kafka:topic", "scheme", "kafka")));
        new CrossLinker().link(graph);
        List<GraphEdge> configures = graph.getEdges().stream().filter(e -> e.type() == EdgeType.CONFIGURES).toList();
        assertEquals(1, configures.size());
    }
}
