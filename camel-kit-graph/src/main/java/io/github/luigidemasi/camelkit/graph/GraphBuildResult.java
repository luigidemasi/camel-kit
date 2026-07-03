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

    public boolean successful() {
        return diagnostics.stream().allMatch(ParserDiagnostic::successful);
    }

    public List<ParserDiagnostic> failedDiagnostics() {
        return diagnostics.stream()
                .filter(diagnostic -> !diagnostic.successful())
                .toList();
    }

    public List<ParserDiagnostic> warningDiagnostics() {
        return diagnostics.stream()
                .filter(diagnostic -> !diagnostic.warnings().isEmpty())
                .toList();
    }

    public String failureSummary() {
        return failedDiagnostics().stream()
                .map(ParserDiagnostic::summary)
                .toList()
                .toString();
    }
}
