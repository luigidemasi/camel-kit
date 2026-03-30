package io.github.luigidemasi.camelkit.graph.parser;

import io.github.luigidemasi.camelkit.graph.ProjectGraph;
import io.github.luigidemasi.camelkit.graph.model.NodeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ConfigParserTest {

    private ProjectGraph graph;
    private static final Path TEST_PROJECT = Path.of("src/test/resources/testdata");

    @BeforeEach
    void setUp() {
        graph = new ProjectGraph();
        new ConfigParser().parse(TEST_PROJECT, graph);
    }

    @Test
    void parsesCamelProperties() {
        assertTrue(graph.hasNode("config:camel.component.kafka.brokers"));
        assertEquals("localhost:9092",
            graph.getNode("config:camel.component.kafka.brokers").properties().get("value"));
    }

    @Test
    void parsesMultipleCamelProperties() {
        assertTrue(graph.hasNode("config:camel.component.kafka.group-id"));
        assertTrue(graph.hasNode("config:camel.component.jdbc.dataSource"));
    }

    @Test
    void ignoresNonCamelProperties() {
        assertFalse(graph.hasNode("config:server.port"));
    }

    @Test
    void allNodesAreConfigPropertyType() {
        graph.findByType(NodeType.CONFIG_PROPERTY).forEach(node ->
            assertEquals(NodeType.CONFIG_PROPERTY, node.type()));
    }

    @Test
    void propertiesIncludeFileInfo() {
        String file = graph.getNode("config:camel.component.kafka.brokers")
            .properties().get("file");
        assertTrue(file.endsWith("application.properties"));
    }
}
