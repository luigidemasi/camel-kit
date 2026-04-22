package io.github.luigidemasi.camelkit.graph.parser;

import io.github.luigidemasi.camelkit.graph.ProjectGraph;
import io.github.luigidemasi.camelkit.graph.model.NodeType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DataWeaveParserTest {

    private static ProjectGraph graph;
    private static final Path TEST_PROJECT = Path.of("src/test/resources/testdata");

    @BeforeAll
    static void setUp() {
        graph = new ProjectGraph();
        new DataWeaveParser().parse(TEST_PROJECT, graph);
    }

    @Test
    void parsesDataWeaveFile() {
        assertTrue(graph.hasNode("dataweave:transform-order.dwl"));
        assertEquals(NodeType.DATAWEAVE_SCRIPT,
            graph.getNode("dataweave:transform-order.dwl").type());
    }

    @Test
    void extractsDwVersion() {
        assertEquals("2.0",
            graph.getNode("dataweave:transform-order.dwl").properties().get("dwVersion"));
    }

    @Test
    void extractsInputType() {
        assertEquals("application/json",
            graph.getNode("dataweave:transform-order.dwl").properties().get("inputType"));
    }

    @Test
    void extractsOutputType() {
        assertEquals("application/json",
            graph.getNode("dataweave:transform-order.dwl").properties().get("outputType"));
    }

    @Test
    void extractsFunctions() {
        String functions = graph.getNode("dataweave:transform-order.dwl").properties().get("functions");
        assertNotNull(functions);
        assertTrue(functions.contains("formatAmount"));
        assertTrue(functions.contains("isValid"));
    }

    @Test
    void extractsFieldAccess() {
        String fields = graph.getNode("dataweave:transform-order.dwl").properties().get("fields");
        assertNotNull(fields);
        assertTrue(fields.contains("payload.id"));
        assertTrue(fields.contains("payload.amount"));
        assertTrue(fields.contains("payload.customer.name"));
    }
}
