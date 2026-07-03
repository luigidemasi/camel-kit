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
        return parseFragment(projectRoot, baseGraph, scannedFilePaths(projectRoot, projectFiles(projectRoot)));
    }

    default ProjectGraph parseFragment(Path projectRoot, ProjectGraph baseGraph, List<Path> scannedFiles) {
        ProjectGraph fragment = new ProjectGraph();
        fragment.merge(baseGraph);
        parseFiles(projectRoot, scannedFiles, fragment);
        return fragment;
    }

    default void parseFiles(Path projectRoot, List<Path> files, ProjectGraph graph) {
        parse(projectRoot, graph);
    }

    default List<String> scannedFiles(Path projectRoot) {
        return List.of();
    }

    default List<Path> scannedFilePaths(Path projectRoot, List<Path> projectFiles) {
        return scannedFiles(projectRoot).stream()
                .map(projectRoot::resolve)
                .toList();
    }

    default List<String> warnings() {
        return List.of();
    }

    default void resetWarnings() {
    }

    static List<Path> projectFiles(Path projectRoot) {
        if (!Files.exists(projectRoot)) {
            return List.of();
        }

        try (Stream<Path> paths = Files.walk(projectRoot)) {
            return paths
                    .filter(Files::isRegularFile)
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new RuntimeException("Failed to scan files under " + projectRoot, e);
        }
    }

    static List<String> findFiles(Path projectRoot, Predicate<Path> matcher) {
        return relativeFileNames(projectRoot, findFilePaths(projectRoot, projectFiles(projectRoot), matcher));
    }

    static List<Path> findFilePaths(Path projectRoot, List<Path> projectFiles, Predicate<Path> matcher) {
        return projectFiles.stream()
                .filter(matcher)
                .sorted()
                .toList();
    }

    static List<String> relativeFileNames(Path projectRoot, List<Path> files) {
        return files.stream()
                .map(file -> relativeFileName(projectRoot, file))
                .sorted()
                .toList();
    }

    static String relativeFileName(Path projectRoot, Path file) {
        return projectRoot.relativize(file).toString().replace('\\', '/');
    }
}
