package io.github.luigidemasi.camelkit.graph.parser;

import io.github.luigidemasi.camelkit.graph.ProjectGraph;
import java.nio.file.Path;

public interface GraphParser {
    void parse(Path projectRoot, ProjectGraph graph);
}
