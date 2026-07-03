package io.github.luigidemasi.camelkit.graph.parser;

import java.nio.file.Files;
import java.nio.file.Path;

import io.github.luigidemasi.camelkit.graph.ProjectGraph;
import io.github.luigidemasi.camelkit.graph.model.NodeType;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class DataWeaveParserTest {

    private static ProjectGraph graph;
    private static final Path TEST_PROJECT = Path.of("src/test/resources/testdata");
    private static final String TRANSFORM_ID = "dataweave:dwl/transform-order.dwl";

    @BeforeAll
    static void setUp() {
        graph = new ProjectGraph();
        new DataWeaveParser().parse(TEST_PROJECT, graph);
    }

    @Test
    void parsesDataWeaveFile() {
        assertTrue(graph.hasNode(TRANSFORM_ID));
        assertEquals(NodeType.DATAWEAVE_SCRIPT,
                graph.getNode(TRANSFORM_ID).type());
    }

    @Test
    void extractsDwVersion() {
        assertEquals("2.0",
                graph.getNode(TRANSFORM_ID).properties().get("dwVersion"));
    }

    @Test
    void extractsInputType() {
        assertEquals("application/json",
                graph.getNode(TRANSFORM_ID).properties().get("inputType"));
    }

    @Test
    void extractsOutputType() {
        assertEquals("application/json",
                graph.getNode(TRANSFORM_ID).properties().get("outputType"));
    }

    @Test
    void extractsFunctions() {
        String functions = graph.getNode(TRANSFORM_ID).properties().get("functions");
        assertNotNull(functions);
        assertTrue(functions.contains("formatAmount"));
        assertTrue(functions.contains("isValid"));
    }

    @Test
    void extractsFieldAccess() {
        String fields = graph.getNode(TRANSFORM_ID).properties().get("fields");
        assertNotNull(fields);
        assertTrue(fields.contains("payload.id"));
        assertTrue(fields.contains("payload.amount"));
        assertTrue(fields.contains("payload.customer.name"));
    }

    @Test
    void idsUseRelativePath(@TempDir Path tempDir) throws Exception {
        Path script = Files.createDirectories(tempDir.resolve("src/main/resources/dwl")).resolve("transform.dwl");
        Files.writeString(script, """
                %dw 2.0
                %output application/json
                ---
                payload
                """);
        ProjectGraph graph = new ProjectGraph();

        new DataWeaveParser().parse(tempDir, graph);

        assertTrue(graph.hasNode("dataweave:src/main/resources/dwl/transform.dwl"));
    }
}
