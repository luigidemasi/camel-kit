package io.github.luigidemasi.camelkit.graph.parser;

import java.nio.file.Path;

import io.github.luigidemasi.camelkit.graph.ProjectGraph;

public interface GraphParser {
    void parse(Path projectRoot, ProjectGraph graph);
}
