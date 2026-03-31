package io.github.luigidemasi.camelkit.graph;

import io.github.luigidemasi.camelkit.graph.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class GraphBuilderTest {

    private static final Path TEST_PROJECT = Path.of("src/test/resources/testdata");

    @Test
    void buildsGraphFromTestProject() {
        ProjectGraph graph = new GraphBuilder().build(TEST_PROJECT);
        assertFalse(graph.findByType(NodeType.CLASS).isEmpty());
        assertFalse(graph.findByType(NodeType.CAMEL_ROUTE).isEmpty());
        assertFalse(graph.findByType(NodeType.CAMEL_ENDPOINT).isEmpty());
        assertFalse(graph.findByType(NodeType.MAVEN_ARTIFACT).isEmpty());
        assertFalse(graph.findByType(NodeType.CONFIG_PROPERTY).isEmpty());
    }

    @Test
    void crossLinkerRuns() {
        ProjectGraph graph = new GraphBuilder().build(TEST_PROJECT);
        long linksToCount = graph.getEdges().stream()
            .filter(e -> e.type() == EdgeType.LINKS_TO)
            .count();
        assertTrue(linksToCount >= 1, "Expected at least one LINKS_TO edge");
    }

    @Test
    void buildAndSerialize(@TempDir Path tempDir) {
        Path outputFile = tempDir.resolve(".camel-kit").resolve("project-graph.json");
        new GraphBuilder().buildAndSerialize(TEST_PROJECT, outputFile);
        assertTrue(Files.exists(outputFile));
        assertTrue(outputFile.toFile().length() > 100);
    }

    @Test
    void emptyProjectProducesEmptyGraph(@TempDir Path emptyDir) {
        ProjectGraph graph = new GraphBuilder().build(emptyDir);
        assertEquals(0, graph.nodeCount());
        assertEquals(0, graph.edgeCount());
    }
}
