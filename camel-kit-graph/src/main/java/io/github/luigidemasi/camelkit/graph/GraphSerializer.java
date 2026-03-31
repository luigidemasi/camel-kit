package io.github.luigidemasi.camelkit.graph;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.luigidemasi.camelkit.graph.model.*;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

public final class GraphSerializer {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);
    private static final String FORMAT_VERSION = "1.0";

    private GraphSerializer() {}

    public static void write(ProjectGraph graph, Path file, String projectRoot) throws IOException {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("version", FORMAT_VERSION);
        root.put("generatedAt", Instant.now().toString());
        root.put("projectRoot", projectRoot);

        ObjectNode stats = root.putObject("stats");
        stats.put("nodes", graph.nodeCount());
        stats.put("edges", graph.edgeCount());

        ObjectNode nodesObj = root.putObject("nodes");
        for (GraphNode node : graph.getNodes().values()) {
            ObjectNode nodeObj = nodesObj.putObject(node.id());
            nodeObj.put("type", node.type().name());
            if (!node.properties().isEmpty()) {
                ObjectNode props = nodeObj.putObject("properties");
                node.properties().forEach(props::put);
            }
        }

        ArrayNode edgesArr = root.putArray("edges");
        for (GraphEdge edge : graph.getEdges()) {
            ObjectNode edgeObj = edgesArr.addObject();
            edgeObj.put("from", edge.from());
            edgeObj.put("to", edge.to());
            edgeObj.put("type", edge.type().name());
            if (!edge.properties().isEmpty()) {
                ObjectNode props = edgeObj.putObject("properties");
                edge.properties().forEach(props::put);
            }
        }

        MAPPER.writeValue(file.toFile(), root);
    }

    public static ProjectGraph read(Path file) throws IOException {
        ProjectGraph graph = new ProjectGraph();
        JsonNode root = MAPPER.readTree(file.toFile());

        JsonNode nodesObj = root.get("nodes");
        nodesObj.fieldNames().forEachRemaining(id -> {
            JsonNode nodeObj = nodesObj.get(id);
            NodeType type = NodeType.valueOf(nodeObj.get("type").asText());
            Map<String, String> props = Map.of();
            if (nodeObj.has("properties")) {
                props = MAPPER.convertValue(nodeObj.get("properties"), new TypeReference<>() {});
            }
            graph.addNode(new GraphNode(id, type, props));
        });

        JsonNode edgesArr = root.get("edges");
        for (JsonNode edgeObj : edgesArr) {
            String from = edgeObj.get("from").asText();
            String to = edgeObj.get("to").asText();
            EdgeType type = EdgeType.valueOf(edgeObj.get("type").asText());
            Map<String, String> props = Map.of();
            if (edgeObj.has("properties")) {
                props = MAPPER.convertValue(edgeObj.get("properties"), new TypeReference<>() {});
            }
            graph.addEdge(new GraphEdge(from, to, type, props));
        }

        return graph;
    }
}
