package io.github.luigidemasi.camelkit.graph.parser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import io.github.luigidemasi.camelkit.graph.ProjectGraph;

public interface GraphParser {
    void parse(Path projectRoot, ProjectGraph graph);

    default ProjectGraph parseFragment(Path projectRoot, ProjectGraph baseGraph) {
        ProjectGraph fragment = new ProjectGraph();
        fragment.merge(baseGraph);
        parse(projectRoot, fragment);
        return fragment;
    }

    default List<String> scannedFiles(Path projectRoot) {
        return List.of();
    }

    default List<String> warnings() {
        return List.of();
    }

    static List<String> findFiles(Path projectRoot, Predicate<Path> matcher) {
        if (!Files.exists(projectRoot)) {
            return List.of();
        }

        try (Stream<Path> paths = Files.walk(projectRoot)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(matcher)
                    .map(projectRoot::relativize)
                    .map(Path::toString)
                    .map(path -> path.replace('\\', '/'))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new RuntimeException("Failed to scan files under " + projectRoot, e);
        }
    }
}
