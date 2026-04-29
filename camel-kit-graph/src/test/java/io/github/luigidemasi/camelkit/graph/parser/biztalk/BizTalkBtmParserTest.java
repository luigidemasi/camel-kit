package io.github.luigidemasi.camelkit.graph.parser.biztalk;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import io.github.luigidemasi.camelkit.graph.ProjectGraph;
import io.github.luigidemasi.camelkit.graph.model.EdgeType;
import io.github.luigidemasi.camelkit.graph.model.GraphEdge;
import io.github.luigidemasi.camelkit.graph.model.GraphNode;
import io.github.luigidemasi.camelkit.graph.model.NodeType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BizTalkBtmParserTest {

    private BizTalkBtmParser parser;
    private ProjectGraph graph;

    @BeforeEach
    void setUp() {
        parser = new BizTalkBtmParser();
        graph = new ProjectGraph();
    }

    @Test
    void testParseOrderToInvoiceBtm() throws URISyntaxException {
        Path btmFile = Paths.get(getClass().getResource("/testdata/biztalk/OrderToInvoice.btm").toURI());

        parser.parse(btmFile, graph);

        // Verify map node
        GraphNode mapNode = graph.getNode("biztalk-map:OrderToInvoice");
        assertNotNull(mapNode, "Map node should exist");
        assertEquals(NodeType.BIZTALK_MAP, mapNode.type());
        assertEquals("OrderToInvoice", mapNode.properties().get("name"));
        assertEquals("MyApp.Schemas.OrderSchema", mapNode.properties().get("sourceSchema"));
        assertEquals("MyApp.Schemas.InvoiceSchema", mapNode.properties().get("targetSchema"));
        assertEquals("3", mapNode.properties().get("functoidCount"));
        assertTrue(mapNode.properties().get("file").endsWith("OrderToInvoice.btm"));

        // Verify schema nodes
        GraphNode sourceSchemaNode = graph.getNode("biztalk-schema:MyApp.Schemas.OrderSchema");
        assertNotNull(sourceSchemaNode, "Source schema node should exist");
        assertEquals(NodeType.BIZTALK_SCHEMA, sourceSchemaNode.type());

        GraphNode targetSchemaNode = graph.getNode("biztalk-schema:MyApp.Schemas.InvoiceSchema");
        assertNotNull(targetSchemaNode, "Target schema node should exist");
        assertEquals(NodeType.BIZTALK_SCHEMA, targetSchemaNode.type());

        // Verify functoid nodes
        GraphNode functoid1 = graph.getNode("biztalk-map:OrderToInvoice:functoid:1");
        assertNotNull(functoid1, "Functoid 1 should exist");
        assertEquals(NodeType.BIZTALK_FUNCTOID, functoid1.type());
        assertEquals("String", functoid1.properties().get("functoidType"));
        assertEquals("107", functoid1.properties().get("functoidFid"));

        GraphNode functoid2 = graph.getNode("biztalk-map:OrderToInvoice:functoid:2");
        assertNotNull(functoid2, "Functoid 2 should exist");
        assertEquals(NodeType.BIZTALK_FUNCTOID, functoid2.type());
        assertEquals("Scripting", functoid2.properties().get("functoidType"));
        assertEquals("260", functoid2.properties().get("functoidFid"));
        assertEquals("CSharp", functoid2.properties().get("scriptLanguage"));

        GraphNode functoid3 = graph.getNode("biztalk-map:OrderToInvoice:functoid:3");
        assertNotNull(functoid3, "Functoid 3 should exist");
        assertEquals(NodeType.BIZTALK_FUNCTOID, functoid3.type());
        assertEquals("Math", functoid3.properties().get("functoidType"));
        assertEquals("111", functoid3.properties().get("functoidFid"));

        // Verify schema edges
        List<GraphEdge> schemaEdges = graph.getEdges().stream()
                .filter(e -> e.type() == EdgeType.BIZTALK_USES_SCHEMA)
                .toList();
        assertEquals(2, schemaEdges.size(), "Should have 2 schema edges");

        GraphEdge sourceEdge = schemaEdges.stream()
                .filter(e -> e.to().equals("biztalk-schema:MyApp.Schemas.OrderSchema"))
                .findFirst()
                .orElse(null);
        assertNotNull(sourceEdge, "Source schema edge should exist");
        assertEquals("biztalk-map:OrderToInvoice", sourceEdge.from());
        assertEquals("source", sourceEdge.properties().get("role"));

        GraphEdge targetEdge = schemaEdges.stream()
                .filter(e -> e.to().equals("biztalk-schema:MyApp.Schemas.InvoiceSchema"))
                .findFirst()
                .orElse(null);
        assertNotNull(targetEdge, "Target schema edge should exist");
        assertEquals("biztalk-map:OrderToInvoice", targetEdge.from());
        assertEquals("target", targetEdge.properties().get("role"));

        // Verify functoid chain edges
        List<GraphEdge> functoidEdges = graph.getEdges().stream()
                .filter(e -> e.type() == EdgeType.BIZTALK_FUNCTOID_CHAIN)
                .toList();
        assertEquals(3, functoidEdges.size(), "Should have 3 functoid chain edges");

        assertTrue(functoidEdges.stream()
                .anyMatch(e -> e.from().equals("biztalk-map:OrderToInvoice")
                        && e.to().equals("biztalk-map:OrderToInvoice:functoid:1")),
                "Should have edge from map to functoid 1");

        assertTrue(functoidEdges.stream()
                .anyMatch(e -> e.from().equals("biztalk-map:OrderToInvoice")
                        && e.to().equals("biztalk-map:OrderToInvoice:functoid:2")),
                "Should have edge from map to functoid 2");

        assertTrue(functoidEdges.stream()
                .anyMatch(e -> e.from().equals("biztalk-map:OrderToInvoice")
                        && e.to().equals("biztalk-map:OrderToInvoice:functoid:3")),
                "Should have edge from map to functoid 3");
    }

    @Test
    void testParseNonexistentFile() {
        Path nonexistent = Paths.get("/nonexistent/file.btm");
        parser.parse(nonexistent, graph);

        // Should not throw exception, graph should be empty
        assertEquals(0, graph.nodeCount());
        assertEquals(0, graph.edgeCount());
    }

    @Test
    void testFunctoidTypes() throws URISyntaxException {
        Path btmFile = Paths.get(getClass().getResource("/testdata/biztalk/OrderToInvoice.btm").toURI());

        parser.parse(btmFile, graph);

        // Verify each functoid has the correct type
        Map<String, String> expectedTypes = Map.of(
                "biztalk-map:OrderToInvoice:functoid:1", "String",
                "biztalk-map:OrderToInvoice:functoid:2", "Scripting",
                "biztalk-map:OrderToInvoice:functoid:3", "Math");

        for (Map.Entry<String, String> entry : expectedTypes.entrySet()) {
            GraphNode functoid = graph.getNode(entry.getKey());
            assertNotNull(functoid, entry.getKey() + " should exist");
            assertEquals(entry.getValue(), functoid.properties().get("functoidType"),
                    "Functoid type should match for " + entry.getKey());
        }
    }

    @Test
    void testScriptingFunctoidLanguage() throws URISyntaxException {
        Path btmFile = Paths.get(getClass().getResource("/testdata/biztalk/OrderToInvoice.btm").toURI());

        parser.parse(btmFile, graph);

        GraphNode scriptingFunctoid = graph.getNode("biztalk-map:OrderToInvoice:functoid:2");
        assertNotNull(scriptingFunctoid);
        assertEquals("CSharp", scriptingFunctoid.properties().get("scriptLanguage"));
    }

    @Test
    void testMapNameDerivedFromFilename() throws URISyntaxException {
        Path btmFile = Paths.get(getClass().getResource("/testdata/biztalk/OrderToInvoice.btm").toURI());

        parser.parse(btmFile, graph);

        GraphNode mapNode = graph.getNode("biztalk-map:OrderToInvoice");
        assertNotNull(mapNode);
        assertEquals("OrderToInvoice", mapNode.properties().get("name"));
    }

    @Test
    void testGraphStructure() throws URISyntaxException {
        Path btmFile = Paths.get(getClass().getResource("/testdata/biztalk/OrderToInvoice.btm").toURI());

        parser.parse(btmFile, graph);

        // Verify total counts
        long mapNodes = graph.findByType(NodeType.BIZTALK_MAP).size();
        assertEquals(1, mapNodes, "Should have 1 map node");

        long schemaNodes = graph.findByType(NodeType.BIZTALK_SCHEMA).size();
        assertEquals(2, schemaNodes, "Should have 2 schema nodes");

        long functoidNodes = graph.findByType(NodeType.BIZTALK_FUNCTOID).size();
        assertEquals(3, functoidNodes, "Should have 3 functoid nodes");

        long schemaEdges = graph.getEdges().stream()
                .filter(e -> e.type() == EdgeType.BIZTALK_USES_SCHEMA)
                .count();
        assertEquals(2, schemaEdges, "Should have 2 schema edges");

        long functoidEdges = graph.getEdges().stream()
                .filter(e -> e.type() == EdgeType.BIZTALK_FUNCTOID_CHAIN)
                .count();
        assertEquals(3, functoidEdges, "Should have 3 functoid chain edges");
    }
}
