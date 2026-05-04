package io.github.luigidemasi.camelkit.graph.parser;

import java.nio.file.Path;

import io.github.luigidemasi.camelkit.graph.ProjectGraph;
import io.github.luigidemasi.camelkit.graph.model.NodeType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
    void capturesNonCamelProperties() {
        assertTrue(graph.hasNode("config:spring.datasource.url"));
        assertTrue(graph.hasNode("config:order.max-retries"));
    }

    @Test
    void allNodesAreConfigPropertyType() {
        graph.findByType(NodeType.CONFIG_PROPERTY).forEach(node -> assertEquals(NodeType.CONFIG_PROPERTY, node.type()));
    }

    @Test
    void propertiesIncludeFileInfo() {
        String file = graph.getNode("config:camel.component.kafka.brokers")
                .properties().get("file");
        assertTrue(file.endsWith("application.properties"));
    }

    @Test
    void capturesAllProperties() throws Exception {
        ProjectGraph graph = new ProjectGraph();
        new ConfigParser().parse(Path.of("src/test/resources/testdata/di"), graph);

        assertNotNull(graph.getNode("config:camel.component.kafka.brokers"),
                "Should capture camel.* properties");
        assertNotNull(graph.getNode("config:spring.datasource.url"),
                "Should capture spring.* properties");
        assertNotNull(graph.getNode("config:order.max-retries"),
                "Should capture application-specific properties");
        assertNotNull(graph.getNode("config:payment.gateway.url"),
                "Should capture all custom properties");
    }
}
