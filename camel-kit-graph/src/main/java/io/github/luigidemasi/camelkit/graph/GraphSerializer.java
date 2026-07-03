package io.github.luigidemasi.camelkit.graph;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import io.github.luigidemasi.camelkit.graph.model.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class GraphSerializer {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    private static final String FORMAT_VERSION = "1.0";

    private GraphSerializer() {
    }

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
        String version = requiredText(root, "version", "root");
        if (!FORMAT_VERSION.equals(version)) {
            throw new IOException("Unsupported graph format version '" + version + "'; expected " + FORMAT_VERSION);
        }

        JsonNode nodesObj = requiredObject(root, "nodes", "root");
        nodesObj.fieldNames().forEachRemaining(id -> {
            JsonNode nodeObj = nodesObj.get(id);
            NodeType type = nodeType(requiredTextUnchecked(nodeObj, "type", "node " + id));
            Map<String, String> props = Map.of();
            if (nodeObj.has("properties")) {
                props = MAPPER.convertValue(nodeObj.get("properties"), new TypeReference<>() {
                });
            }
            graph.addNode(new GraphNode(id, type, props));
        });

        JsonNode edgesArr = requiredArray(root, "edges", "root");
        for (JsonNode edgeObj : edgesArr) {
            String from = requiredText(edgeObj, "from", "edge");
            String to = requiredText(edgeObj, "to", "edge");
            EdgeType type = edgeType(requiredText(edgeObj, "type", "edge"));
            Map<String, String> props = Map.of();
            if (edgeObj.has("properties")) {
                props = MAPPER.convertValue(edgeObj.get("properties"), new TypeReference<>() {
                });
            }
            graph.addEdge(new GraphEdge(from, to, type, props));
        }

        return graph;
    }

    private static JsonNode requiredObject(JsonNode parent, String field, String location) throws IOException {
        JsonNode node = parent.get(field);
        if (node == null || !node.isObject()) {
            throw new IOException("Malformed graph: " + location + " is missing object field '" + field + "'");
        }
        return node;
    }

    private static JsonNode requiredArray(JsonNode parent, String field, String location) throws IOException {
        JsonNode node = parent.get(field);
        if (node == null || !node.isArray()) {
            throw new IOException("Malformed graph: " + location + " is missing array field '" + field + "'");
        }
        return node;
    }

    private static String requiredText(JsonNode parent, String field, String location) throws IOException {
        JsonNode node = parent.get(field);
        if (node == null || !node.isTextual()) {
            throw new IOException("Malformed graph: " + location + " is missing text field '" + field + "'");
        }
        return node.asText();
    }

    private static String requiredTextUnchecked(JsonNode parent, String field, String location) {
        JsonNode node = parent.get(field);
        if (node == null || !node.isTextual()) {
            throw new IllegalArgumentException(
                    "Malformed graph: " + location + " is missing text field '" + field + "'");
        }
        return node.asText();
    }

    private static NodeType nodeType(String value) {
        try {
            return NodeType.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Malformed graph: unknown node type '" + value + "'", e);
        }
    }

    private static EdgeType edgeType(String value) throws IOException {
        try {
            return EdgeType.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new IOException("Malformed graph: unknown edge type '" + value + "'", e);
        }
    }
}
