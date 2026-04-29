package io.github.luigidemasi.camelkit.graph.query;

import java.util.Map;

import io.github.luigidemasi.camelkit.graph.ProjectGraph;
import io.github.luigidemasi.camelkit.graph.model.*;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DeadCodeAnalyzerTest {

    private ProjectGraph buildGraph() {
        ProjectGraph graph = new ProjectGraph();

        // Routes
        graph.addNode(new GraphNode(
                "route:processOrders", NodeType.CAMEL_ROUTE,
                Map.of("routeId", "processOrders")));
        graph.addNode(new GraphNode(
                "route:enrichOrder", NodeType.CAMEL_ROUTE,
                Map.of("routeId", "enrichOrder")));

        // Endpoints
        graph.addNode(new GraphNode(
                "endpoint:kafka:orders", NodeType.CAMEL_ENDPOINT,
                Map.of("uri", "kafka:orders", "scheme", "kafka")));
        graph.addNode(new GraphNode(
                "endpoint:direct:enrichOrder", NodeType.CAMEL_ENDPOINT,
                Map.of("uri", "direct:enrichOrder", "scheme", "direct")));

        // Artifacts
        graph.addNode(new GraphNode(
                "artifact:camel-core", NodeType.MAVEN_ARTIFACT,
                Map.of("artifactId", "camel-core", "groupId", "org.apache.camel", "version", "4.14.0")));
        graph.addNode(new GraphNode(
                "artifact:camel-kafka", NodeType.MAVEN_ARTIFACT,
                Map.of("artifactId", "camel-kafka", "groupId", "org.apache.camel", "version", "4.14.0")));
        graph.addNode(new GraphNode(
                "artifact:camel-jdbc", NodeType.MAVEN_ARTIFACT,
                Map.of("artifactId", "camel-jdbc", "groupId", "org.apache.camel", "version", "4.14.0")));

        // Config properties
        graph.addNode(new GraphNode(
                "config:camel.component.kafka.brokers", NodeType.CONFIG_PROPERTY,
                Map.of("key", "camel.component.kafka.brokers", "value", "localhost:9092")));
        graph.addNode(new GraphNode(
                "config:camel.component.jms.connectionFactory", NodeType.CONFIG_PROPERTY,
                Map.of("key", "camel.component.jms.connectionFactory", "value", "#jmsFactory")));

        // Edges: route -> endpoint
        graph.addEdge(new GraphEdge("route:processOrders", "endpoint:kafka:orders", EdgeType.ROUTES_FROM, Map.of()));
        graph.addEdge(
                new GraphEdge("route:processOrders", "endpoint:direct:enrichOrder", EdgeType.ROUTES_TO, Map.of()));
        graph.addEdge(
                new GraphEdge("route:enrichOrder", "endpoint:direct:enrichOrder", EdgeType.ROUTES_FROM, Map.of()));

        // Edges: endpoint -> artifact (USES_COMPONENT)
        graph.addEdge(
                new GraphEdge("endpoint:kafka:orders", "artifact:camel-kafka", EdgeType.USES_COMPONENT, Map.of()));

        // Edges: config -> endpoint (CONFIGURES)
        graph.addEdge(new GraphEdge(
                "config:camel.component.kafka.brokers", "endpoint:kafka:orders", EdgeType.CONFIGURES, Map.of()));

        // Link routes
        graph.addEdge(new GraphEdge("route:processOrders", "route:enrichOrder", EdgeType.LINKS_TO, Map.of()));

        return graph;
    }

    @Test
    void findsUnusedArtifact() {
        ProjectGraph graph = buildGraph();
        DeadCodeAnalyzer analyzer = new DeadCodeAnalyzer(graph);
        DeadCodeAnalyzer.DeadCodeResult result = analyzer.analyze();

        // camel-jdbc has no USES_COMPONENT edge from any endpoint
        assertEquals(1, result.unusedArtifacts().size());
        assertTrue(result.unusedArtifacts().get(0).id().contains("camel-jdbc"));
    }

    @Test
    void excludesFrameworkArtifacts() {
        ProjectGraph graph = buildGraph();
        DeadCodeAnalyzer analyzer = new DeadCodeAnalyzer(graph);
        DeadCodeAnalyzer.DeadCodeResult result = analyzer.analyze();

        // camel-core has no USES_COMPONENT edge but is a framework artifact — must NOT be flagged
        assertTrue(result.unusedArtifacts().stream()
                .noneMatch(n -> n.properties().get("artifactId").equals("camel-core")));
    }

    @Test
    void findsOrphanedRoute() {
        ProjectGraph graph = buildGraph();

        // Add orphaned route: consumes from direct:orphan, nobody produces to it
        graph.addNode(new GraphNode(
                "route:orphaned", NodeType.CAMEL_ROUTE,
                Map.of("routeId", "orphaned")));
        graph.addNode(new GraphNode(
                "endpoint:direct:orphan", NodeType.CAMEL_ENDPOINT,
                Map.of("uri", "direct:orphan", "scheme", "direct")));
        graph.addEdge(new GraphEdge("route:orphaned", "endpoint:direct:orphan", EdgeType.ROUTES_FROM, Map.of()));

        DeadCodeAnalyzer analyzer = new DeadCodeAnalyzer(graph);
        DeadCodeAnalyzer.DeadCodeResult result = analyzer.analyze();

        assertEquals(1, result.orphanedRoutes().size());
        assertEquals("route:orphaned", result.orphanedRoutes().get(0).id());
    }

    @Test
    void doesNotFlagExternalConsumerAsOrphaned() {
        ProjectGraph graph = buildGraph();
        DeadCodeAnalyzer analyzer = new DeadCodeAnalyzer(graph);
        DeadCodeAnalyzer.DeadCodeResult result = analyzer.analyze();

        // processOrders consumes from kafka: (external) — not orphaned
        // enrichOrder consumes from direct:enrichOrder — but processOrders ROUTES_TO it, so not orphaned
        assertTrue(result.orphanedRoutes().isEmpty());
    }

    @Test
    void findsUnusedProperty() {
        ProjectGraph graph = buildGraph();
        DeadCodeAnalyzer analyzer = new DeadCodeAnalyzer(graph);
        DeadCodeAnalyzer.DeadCodeResult result = analyzer.analyze();

        // camel.component.jms.connectionFactory has no CONFIGURES edge
        assertEquals(1, result.unusedProperties().size());
        assertTrue(result.unusedProperties().get(0).properties().get("key")
                .contains("jms"));
    }

    @Test
    void cleanProjectReturnsEmpty() {
        ProjectGraph graph = new ProjectGraph();

        // Route with kafka endpoint
        graph.addNode(new GraphNode("route:r1", NodeType.CAMEL_ROUTE, Map.of("routeId", "r1")));
        graph.addNode(new GraphNode(
                "endpoint:kafka:topic1", NodeType.CAMEL_ENDPOINT,
                Map.of("uri", "kafka:topic1", "scheme", "kafka")));
        graph.addNode(new GraphNode(
                "artifact:camel-kafka", NodeType.MAVEN_ARTIFACT,
                Map.of("artifactId", "camel-kafka", "groupId", "org.apache.camel", "version", "4.14.0")));
        graph.addNode(new GraphNode(
                "config:camel.component.kafka.brokers", NodeType.CONFIG_PROPERTY,
                Map.of("key", "camel.component.kafka.brokers", "value", "localhost:9092")));

        graph.addEdge(new GraphEdge("route:r1", "endpoint:kafka:topic1", EdgeType.ROUTES_FROM, Map.of()));
        graph.addEdge(
                new GraphEdge("endpoint:kafka:topic1", "artifact:camel-kafka", EdgeType.USES_COMPONENT, Map.of()));
        graph.addEdge(new GraphEdge(
                "config:camel.component.kafka.brokers", "endpoint:kafka:topic1", EdgeType.CONFIGURES, Map.of()));

        DeadCodeAnalyzer analyzer = new DeadCodeAnalyzer(graph);
        DeadCodeAnalyzer.DeadCodeResult result = analyzer.analyze();

        assertTrue(result.unusedArtifacts().isEmpty());
        assertTrue(result.orphanedRoutes().isEmpty());
        assertTrue(result.unusedProperties().isEmpty());
    }
}
