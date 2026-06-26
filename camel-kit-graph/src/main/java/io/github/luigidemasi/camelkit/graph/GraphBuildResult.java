package io.github.luigidemasi.camelkit.graph;

import java.util.List;
import java.util.Objects;

public record GraphBuildResult(
        ProjectGraph graph,
        List<ParserDiagnostic> diagnostics) {

    public GraphBuildResult {
        Objects.requireNonNull(graph, "graph");
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
    }
}
