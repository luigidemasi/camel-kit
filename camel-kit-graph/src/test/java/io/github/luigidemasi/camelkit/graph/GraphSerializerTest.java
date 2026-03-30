package io.github.luigidemasi.camelkit.graph;

import io.github.luigidemasi.camelkit.graph.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GraphSerializerTest {

    @TempDir
    Path tempDir;

    @Test
    void roundTrip() throws IOException {
        ProjectGraph original = new ProjectGraph();
        original.addNode(new GraphNode("class:com.example.Foo", NodeType.CLASS,
            Map.of("name", "Foo", "package", "com.example")));
        original.addNode(new GraphNode("route:processOrders", NodeType.CAMEL_ROUTE,
            Map.of("routeId", "processOrders", "fromUri", "kafka:orders")));
        original.addEdge(new GraphEdge("class:com.example.Foo", "route:processOrders",
            EdgeType.DECLARES, Map.of()));

        Path file = tempDir.resolve("project-graph.json");
        GraphSerializer.write(original, file, "/test/project");

        ProjectGraph loaded = GraphSerializer.read(file);

        assertEquals(original.nodeCount(), loaded.nodeCount());
        assertEquals(original.edgeCount(), loaded.edgeCount());
        assertEquals("Foo", loaded.getNode("class:com.example.Foo").properties().get("name"));
        assertEquals(NodeType.CAMEL_ROUTE, loaded.getNode("route:processOrders").type());
    }

    @Test
    void jsonContainsVersionAndStats() throws IOException {
        ProjectGraph graph = new ProjectGraph();
        graph.addNode(new GraphNode("class:A", NodeType.CLASS, Map.of()));
        graph.addNode(new GraphNode("class:B", NodeType.CLASS, Map.of()));
        graph.addEdge(new GraphEdge("class:A", "class:B", EdgeType.EXTENDS, Map.of()));

        Path file = tempDir.resolve("project-graph.json");
        GraphSerializer.write(graph, file, "/my/project");

        String json = Files.readString(file);
        assertTrue(json.contains("\"version\" : \"1.0\"") || json.contains("\"version\":\"1.0\""));
        assertTrue(json.contains("/my/project"));
    }

    @Test
    void edgePropertiesPreserved() throws IOException {
        ProjectGraph graph = new ProjectGraph();
        graph.addNode(new GraphNode("route:r1", NodeType.CAMEL_ROUTE, Map.of()));
        graph.addNode(new GraphNode("processor:r1:marshal:0", NodeType.CAMEL_PROCESSOR, Map.of()));
        graph.addEdge(new GraphEdge("route:r1", "processor:r1:marshal:0",
            EdgeType.PROCESSES, Map.of("order", "0")));

        Path file = tempDir.resolve("project-graph.json");
        GraphSerializer.write(graph, file, "/test");

        ProjectGraph loaded = GraphSerializer.read(file);
        GraphEdge edge = loaded.getEdges().get(0);
        assertEquals("0", edge.properties().get("order"));
    }
}
