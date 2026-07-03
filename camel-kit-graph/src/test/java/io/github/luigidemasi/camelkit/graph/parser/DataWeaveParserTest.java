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
    void idsUseClasspathRelativePath(@TempDir Path tempDir) throws Exception {
        Path script = Files.createDirectories(tempDir.resolve("src/main/resources/dwl")).resolve("transform.dwl");
        Files.writeString(script, """
                %dw 2.0
                %output application/json
                ---
                payload
                """);
        ProjectGraph graph = new ProjectGraph();

        new DataWeaveParser().parse(tempDir, graph);

        assertTrue(graph.hasNode("dataweave:dwl/transform.dwl"),
                "node ID must match the classpath path Mule flows reference");
        assertEquals("src/main/resources/dwl/transform.dwl",
                graph.getNode("dataweave:dwl/transform.dwl").properties().get("file"));
    }

    @Test
    void idsMatchMuleFlowReferences(@TempDir Path tempDir) throws Exception {
        Path script = Files.createDirectories(tempDir.resolve("src/main/resources/dwl")).resolve("map-order.dwl");
        Files.writeString(script, """
                %dw 2.0
                %output application/json
                ---
                payload
                """);
        Path mule = tempDir.resolve("src/main/mule/orders.xml");
        Files.createDirectories(mule.getParent());
        Files.writeString(mule, """
                <mule xmlns="http://www.mulesoft.org/schema/mule/core"
                      xmlns:ee="http://www.mulesoft.org/schema/mule/ee/core">
                  <flow name="order-flow">
                    <ee:transform>
                      <ee:message>
                        <ee:set-payload resource="classpath:dwl/map-order.dwl"/>
                      </ee:message>
                    </ee:transform>
                  </flow>
                </mule>
                """);
        ProjectGraph graph = new ProjectGraph();

        new MuleXmlFlowParser().parseFiles(tempDir, java.util.List.of(mule), graph);
        new DataWeaveParser().parseFiles(tempDir, java.util.List.of(script), graph);

        assertTrue(graph.hasNode("dataweave:dwl/map-order.dwl"),
                "Mule flow reference and .dwl file scan must converge on one node");
        assertEquals("2.0", graph.getNode("dataweave:dwl/map-order.dwl").properties().get("dwVersion"),
                "the parsed script metadata must land on the node the Mule flow links to");
    }
}
