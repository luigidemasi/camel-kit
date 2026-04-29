package io.github.luigidemasi.camelkit.graph.model;

import java.util.Map;

public record GraphNode(
        String id,
        NodeType type,
        Map<String, String> properties) {
}
