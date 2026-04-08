package io.github.luigidemasi.camelkit.command.graph;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.luigidemasi.camelkit.graph.model.GraphEdge;
import io.github.luigidemasi.camelkit.graph.model.GraphNode;

import java.util.List;

public final class GraphJsonWriter {

    static final ObjectMapper MAPPER = new ObjectMapper();

    private GraphJsonWriter() {}

    public static ObjectNode createObject() { return MAPPER.createObjectNode(); }

    public static ArrayNode createArray() { return MAPPER.createArrayNode(); }

    public static ObjectNode nodeToJson(GraphNode node) {
        ObjectNode json = MAPPER.createObjectNode();
        json.put("id", node.id());
        json.put("type", node.type().name());
        ObjectNode props = json.putObject("properties");
        node.properties().forEach(props::put);
        return json;
    }

    public static ObjectNode edgeToJson(GraphEdge edge) {
        ObjectNode json = MAPPER.createObjectNode();
        json.put("from", edge.from());
        json.put("to", edge.to());
        json.put("type", edge.type().name());
        if (edge.properties() != null && !edge.properties().isEmpty()) {
            ObjectNode props = json.putObject("properties");
            edge.properties().forEach(props::put);
        }
        return json;
    }

    public static ArrayNode nodesToArray(List<GraphNode> nodes) {
        ArrayNode array = MAPPER.createArrayNode();
        nodes.forEach(n -> array.add(nodeToJson(n)));
        return array;
    }

    public static ArrayNode edgesToArray(List<GraphEdge> edges) {
        ArrayNode array = MAPPER.createArrayNode();
        edges.forEach(e -> array.add(edgeToJson(e)));
        return array;
    }

    public static String toJson(ObjectNode root) {
        try {
            return MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            return "{\"error\":\"JSON serialization failed\"}";
        }
    }
}
