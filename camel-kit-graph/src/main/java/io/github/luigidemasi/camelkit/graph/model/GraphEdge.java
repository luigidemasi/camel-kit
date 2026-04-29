package io.github.luigidemasi.camelkit.graph.model;

import java.util.Map;

public record GraphEdge(
        String from,
        String to,
        EdgeType type,
        Map<String, String> properties) {
}
