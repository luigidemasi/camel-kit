package io.github.luigidemasi.camelkit.command.graph;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import io.github.luigidemasi.camelkit.graph.GraphSerializer;
import io.github.luigidemasi.camelkit.graph.ProjectGraph;
import io.github.luigidemasi.camelkit.graph.model.EdgeType;
import io.github.luigidemasi.camelkit.graph.model.GraphEdge;
import io.github.luigidemasi.camelkit.graph.model.GraphNode;
import io.github.luigidemasi.camelkit.graph.model.NodeType;

public final class TestGraphs {

    private TestGraphs() {
    }

    /**
     * Sample project: 2 routes, 4 endpoints, 3 artifacts (1 unused), 2 config props, 1 class, 1 resource.
     */
    public static ProjectGraph sampleProject() {
        ProjectGraph graph = new ProjectGraph();

        graph.addNode(new GraphNode(
                "route:order-process", NodeType.CAMEL_ROUTE,
                Map.of("name", "order-process", "from", "kafka:orders-in")));
        graph.addNode(new GraphNode(
                "route:order-validate", NodeType.CAMEL_ROUTE,
                Map.of("name", "order-validate", "from", "direct:validate")));

        graph.addNode(new GraphNode(
                "endpoint:direct:orders", NodeType.CAMEL_ENDPOINT,
                Map.of("uri", "direct:orders", "scheme", "direct")));
        graph.addNode(new GraphNode(
                "endpoint:kafka:orders-in", NodeType.CAMEL_ENDPOINT,
                Map.of("uri", "kafka:orders-in", "scheme", "kafka")));
        graph.addNode(new GraphNode(
                "endpoint:kafka:orders-out", NodeType.CAMEL_ENDPOINT,
                Map.of("uri", "kafka:orders-out", "scheme", "kafka")));
        graph.addNode(new GraphNode(
                "endpoint:log:processed", NodeType.CAMEL_ENDPOINT,
                Map.of("uri", "log:processed", "scheme", "log")));

        graph.addNode(new GraphNode(
                "maven:camel-kafka", NodeType.MAVEN_ARTIFACT,
                Map.of("artifactId", "camel-kafka", "version", "4.14.4")));
        graph.addNode(new GraphNode(
                "maven:camel-jdbc", NodeType.MAVEN_ARTIFACT,
                Map.of("artifactId", "camel-jdbc", "version", "4.14.4")));
        graph.addNode(new GraphNode(
                "maven:camel-core", NodeType.MAVEN_ARTIFACT,
                Map.of("artifactId", "camel-core", "version", "4.14.4")));

        graph.addNode(new GraphNode(
                "prop:app.kafka.topic", NodeType.CONFIG_PROPERTY,
                Map.of("key", "app.kafka.topic", "value", "orders")));
        graph.addNode(new GraphNode(
                "prop:app.kafka.group-id", NodeType.CONFIG_PROPERTY,
                Map.of("key", "app.kafka.group-id", "value", "order-service")));

        graph.addNode(new GraphNode(
                "class:OrderProcessor", NodeType.CLASS,
                Map.of("fqn", "com.example.OrderProcessor", "name", "OrderProcessor")));
        graph.addNode(new GraphNode(
                "resource:order-routes.camel.yaml", NodeType.RESOURCE_FILE,
                Map.of("path", "src/main/resources/camel/order-routes.camel.yaml")));

        graph.addEdge(new GraphEdge("route:order-process", "endpoint:kafka:orders-in", EdgeType.ROUTES_FROM, Map.of()));
        graph.addEdge(new GraphEdge("route:order-process", "endpoint:kafka:orders-out", EdgeType.ROUTES_TO, Map.of()));
        graph.addEdge(new GraphEdge("route:order-process", "endpoint:direct:orders", EdgeType.ROUTES_TO, Map.of()));
        graph.addEdge(new GraphEdge("route:order-validate", "endpoint:direct:orders", EdgeType.ROUTES_FROM, Map.of()));
        graph.addEdge(new GraphEdge("route:order-validate", "endpoint:log:processed", EdgeType.ROUTES_TO, Map.of()));
        graph.addEdge(
                new GraphEdge("endpoint:kafka:orders-in", "maven:camel-kafka", EdgeType.USES_COMPONENT, Map.of()));
        graph.addEdge(
                new GraphEdge("endpoint:kafka:orders-out", "maven:camel-kafka", EdgeType.USES_COMPONENT, Map.of()));
        graph.addEdge(new GraphEdge("prop:app.kafka.topic", "endpoint:kafka:orders-in", EdgeType.CONFIGURES, Map.of()));
        graph.addEdge(new GraphEdge("class:OrderProcessor", "route:order-process", EdgeType.PROCESSES, Map.of()));

        return graph;
    }

    public static Path writeToTempFile(ProjectGraph graph, Path dir) throws IOException {
        Path graphFile = dir.resolve("project-graph.json");
        GraphSerializer.write(graph, graphFile, dir.toAbsolutePath().toString());
        return graphFile;
    }
}
