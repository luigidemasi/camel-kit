package io.github.luigidemasi.camelkit.graph.parser;

import java.nio.file.Path;

import io.github.luigidemasi.camelkit.graph.ProjectGraph;
import io.github.luigidemasi.camelkit.graph.model.EdgeType;
import io.github.luigidemasi.camelkit.graph.model.NodeType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PomParserTest {

    private ProjectGraph graph;
    private static final Path TEST_PROJECT = Path.of("src/test/resources/testdata");

    @BeforeEach
    void setUp() {
        graph = new ProjectGraph();
        new PomParser().parse(TEST_PROJECT, graph);
    }

    @Test
    void parsesProjectArtifact() {
        assertTrue(graph.hasNode("maven:com.example:order-service"));
        assertEquals("1.0.0",
                graph.getNode("maven:com.example:order-service").properties().get("version"));
    }

    @Test
    void parsesDependencies() {
        assertTrue(graph.hasNode("maven:org.apache.camel:camel-core"));
        assertTrue(graph.hasNode("maven:org.apache.camel:camel-kafka"));
        assertTrue(graph.hasNode("maven:org.apache.camel:camel-jdbc"));
    }

    @Test
    void createsDependsOnEdges() {
        long depEdges = graph.getEdges().stream()
                .filter(e -> e.type() == EdgeType.DEPENDS_ON)
                .filter(e -> e.from().equals("maven:com.example:order-service"))
                .count();
        assertEquals(3, depEdges);
    }

    @Test
    void allNodesAreMavenArtifactType() {
        graph.findByType(NodeType.MAVEN_ARTIFACT).forEach(node -> assertEquals(NodeType.MAVEN_ARTIFACT, node.type()));
        assertTrue(graph.findByType(NodeType.MAVEN_ARTIFACT).size() >= 4,
                "Should have at least 4 MAVEN_ARTIFACT nodes (project + 3 deps from root pom)");
    }
}
