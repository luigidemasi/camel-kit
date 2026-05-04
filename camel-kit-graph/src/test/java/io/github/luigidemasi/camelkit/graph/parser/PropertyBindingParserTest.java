package io.github.luigidemasi.camelkit.graph.parser;

import java.util.Map;

import io.github.luigidemasi.camelkit.graph.ProjectGraph;
import io.github.luigidemasi.camelkit.graph.model.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PropertyBindingParserTest {

    static ProjectGraph graph;

    @BeforeAll
    static void setUp() {
        graph = new ProjectGraph();

        graph.addNode(new GraphNode(
                "config:camel.component.jms.connection-factory", NodeType.CONFIG_PROPERTY,
                Map.of("key", "camel.component.jms.connection-factory", "value", "#class:com.example.MyFactory")));
        graph.addNode(new GraphNode(
                "config:camel.component.xslt.saxon-extension-functions", NodeType.CONFIG_PROPERTY,
                Map.of("key", "camel.component.xslt.saxon-extension-functions", "value", "#bean:saxonFactory")));
        graph.addNode(new GraphNode(
                "config:camel.component.kafka.brokers", NodeType.CONFIG_PROPERTY,
                Map.of("key", "camel.component.kafka.brokers", "value", "localhost:9092")));
        graph.addNode(new GraphNode(
                "config:spring.datasource.url", NodeType.CONFIG_PROPERTY,
                Map.of("key", "spring.datasource.url", "value", "jdbc:postgresql://localhost/orders")));

        graph.addNode(new GraphNode(
                "class:com.example.MyFactory", NodeType.CLASS,
                Map.of("name", "MyFactory")));
        graph.addNode(new GraphNode(
                "class:com.example.SaxonFactory", NodeType.CLASS,
                Map.of("name", "SaxonFactory", "bean", "true", "beanName", "saxonFactory")));

        new PropertyBindingParser().parse(graph, "spring-boot");
    }

    @Test
    void createsInstantiatesEdgeForClassPrefix() {
        var edges = graph.getOutgoingEdges("config:camel.component.jms.connection-factory").stream()
                .filter(e -> e.type() == EdgeType.INSTANTIATES)
                .toList();
        assertEquals(1, edges.size());
        assertEquals("class:com.example.MyFactory", edges.get(0).to());
    }

    @Test
    void createsReferencesBeanEdgeForBeanPrefix() {
        var edges = graph.getOutgoingEdges("config:camel.component.xslt.saxon-extension-functions").stream()
                .filter(e -> e.type() == EdgeType.REFERENCES_BEAN)
                .toList();
        assertEquals(1, edges.size());
        assertEquals("class:com.example.SaxonFactory", edges.get(0).to());
    }

    @Test
    void skipsPlainValues() {
        var edges = graph.getOutgoingEdges("config:camel.component.kafka.brokers").stream()
                .filter(e -> e.type() == EdgeType.INSTANTIATES || e.type() == EdgeType.REFERENCES_BEAN)
                .toList();
        assertTrue(edges.isEmpty(), "Plain string values should not create INSTANTIATES or REFERENCES_BEAN edges");
    }

    @Test
    void createsSyntheticDataSourceForSpringBoot() {
        assertNotNull(graph.getNode("synthetic:javax.sql.DataSource"),
                "Should create synthetic DataSource node for spring.datasource.* properties");
        var edges = graph.getOutgoingEdges("config:spring.datasource.url").stream()
                .filter(e -> e.type() == EdgeType.CONFIGURES)
                .toList();
        assertFalse(edges.isEmpty(), "spring.datasource.url should CONFIGURES the synthetic DataSource");
    }
}
