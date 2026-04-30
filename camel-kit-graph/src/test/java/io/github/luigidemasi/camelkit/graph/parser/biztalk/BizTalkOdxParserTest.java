package io.github.luigidemasi.camelkit.graph.parser.biztalk;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import io.github.luigidemasi.camelkit.graph.ProjectGraph;
import io.github.luigidemasi.camelkit.graph.model.EdgeType;
import io.github.luigidemasi.camelkit.graph.model.GraphEdge;
import io.github.luigidemasi.camelkit.graph.model.GraphNode;
import io.github.luigidemasi.camelkit.graph.model.NodeType;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BizTalkOdxParserTest {

    private static final Path TEST_DATA_DIR = Paths.get("src/test/resources/testdata/biztalk");
    private static final Path TEST_ODX = TEST_DATA_DIR.resolve("SimpleOrchestration.odx");

    @Test
    void testParseOrchestrationNode() {
        ProjectGraph graph = new ProjectGraph();
        BizTalkOdxParser parser = new BizTalkOdxParser();

        parser.parse(TEST_ODX, graph);

        List<GraphNode> orchestrations = graph.findByType(NodeType.BIZTALK_ORCHESTRATION);
        assertEquals(1, orchestrations.size(), "Should parse exactly one orchestration");

        GraphNode orch = orchestrations.get(0);
        assertEquals("biztalk-orch:MyApp.Orchestrations.OrderProcessOrchestration", orch.id());
        assertEquals("OrderProcessOrchestration", orch.properties().get("name"));
        assertEquals("MyApp.Orchestrations", orch.properties().get("namespace"));
    }

    @Test
    void testParseMessageNodes() {
        ProjectGraph graph = new ProjectGraph();
        BizTalkOdxParser parser = new BizTalkOdxParser();

        parser.parse(TEST_ODX, graph);

        List<GraphNode> messages = graph.findByType(NodeType.BIZTALK_MESSAGE);
        assertEquals(2, messages.size(), "Should parse 2 messages");

        Map<String, GraphNode> messageMap = Map.of(
                messages.get(0).properties().get("name"), messages.get(0),
                messages.get(1).properties().get("name"), messages.get(1));

        GraphNode orderMsg = messageMap.get("OrderMsg");
        assertNotNull(orderMsg, "OrderMsg should exist");
        assertEquals("biztalk-orch:MyApp.Orchestrations.OrderProcessOrchestration:message:OrderMsg", orderMsg.id());
        assertEquals("MyApp.Schemas.OrderSchema", orderMsg.properties().get("type"));
        assertEquals("In", orderMsg.properties().get("direction"));

        GraphNode invoiceMsg = messageMap.get("InvoiceMsg");
        assertNotNull(invoiceMsg, "InvoiceMsg should exist");
        assertEquals("biztalk-orch:MyApp.Orchestrations.OrderProcessOrchestration:message:InvoiceMsg", invoiceMsg.id());
        assertEquals("MyApp.Schemas.InvoiceSchema", invoiceMsg.properties().get("type"));
        assertEquals("Out", invoiceMsg.properties().get("direction"));
    }

    @Test
    void testParsePortNodes() {
        ProjectGraph graph = new ProjectGraph();
        BizTalkOdxParser parser = new BizTalkOdxParser();

        parser.parse(TEST_ODX, graph);

        List<GraphNode> ports = graph.findByType(NodeType.BIZTALK_PORT);
        assertEquals(2, ports.size(), "Should parse 2 ports");

        Map<String, GraphNode> portMap = Map.of(
                ports.get(0).properties().get("name"), ports.get(0),
                ports.get(1).properties().get("name"), ports.get(1));

        GraphNode receivePort = portMap.get("ReceivePort");
        assertNotNull(receivePort, "ReceivePort should exist");
        assertEquals("biztalk-orch:MyApp.Orchestrations.OrderProcessOrchestration:port:ReceivePort", receivePort.id());
        assertEquals("Receive", receivePort.properties().get("direction"));
        assertEquals("FILE", receivePort.properties().get("adapter"));
        assertEquals("C:\\Orders\\In", receivePort.properties().get("address"));

        GraphNode sendPort = portMap.get("SendPort");
        assertNotNull(sendPort, "SendPort should exist");
        assertEquals("biztalk-orch:MyApp.Orchestrations.OrderProcessOrchestration:port:SendPort", sendPort.id());
        assertEquals("Send", sendPort.properties().get("direction"));
        assertEquals("SQL", sendPort.properties().get("adapter"));
        assertEquals("mssql://localhost/InvoiceDB", sendPort.properties().get("address"));
    }

    @Test
    void testParseShapeNodes() {
        ProjectGraph graph = new ProjectGraph();
        BizTalkOdxParser parser = new BizTalkOdxParser();

        parser.parse(TEST_ODX, graph);

        List<GraphNode> shapes = graph.findByType(NodeType.BIZTALK_SHAPE);
        assertTrue(shapes.size() >= 5, "Should parse at least 5 shapes (Receive, Decide, Construct, Transform, Send)");

        Map<String, GraphNode> shapeMap = shapes.stream()
                .collect(java.util.stream.Collectors.toMap(
                        n -> n.properties().get("name"),
                        n -> n,
                        (a, b) -> a));

        assertNotNull(shapeMap.get("ReceiveOrder"), "ReceiveOrder shape should exist");
        assertEquals("Receive", shapeMap.get("ReceiveOrder").properties().get("shapeType"));

        assertNotNull(shapeMap.get("CheckOrderType"), "CheckOrderType shape should exist");
        assertEquals("Decide", shapeMap.get("CheckOrderType").properties().get("shapeType"));

        assertNotNull(shapeMap.get("ConstructInvoice"), "ConstructInvoice shape should exist");
        assertEquals("Construct", shapeMap.get("ConstructInvoice").properties().get("shapeType"));

        assertNotNull(shapeMap.get("MapOrderToInvoice"), "MapOrderToInvoice shape should exist");
        assertEquals("Transform", shapeMap.get("MapOrderToInvoice").properties().get("shapeType"));

        assertNotNull(shapeMap.get("SendInvoice"), "SendInvoice shape should exist");
        assertEquals("Send", shapeMap.get("SendInvoice").properties().get("shapeType"));
    }

    @Test
    void testOrchestrationContainsEdges() {
        ProjectGraph graph = new ProjectGraph();
        BizTalkOdxParser parser = new BizTalkOdxParser();

        parser.parse(TEST_ODX, graph);

        List<GraphEdge> edges = graph.getEdges();
        long containsEdges = edges.stream()
                .filter(e -> e.type() == EdgeType.BIZTALK_ORCHESTRATION_CONTAINS)
                .count();

        assertTrue(containsEdges >= 9,
                "Should have at least 9 BIZTALK_ORCHESTRATION_CONTAINS edges (2 messages + 2 ports + 5 shapes)");

        GraphNode orch = graph.findByType(NodeType.BIZTALK_ORCHESTRATION).get(0);
        List<GraphEdge> orchEdges = graph.getOutgoingEdges(orch.id());

        assertTrue(orchEdges.stream()
                .filter(e -> e.type() == EdgeType.BIZTALK_ORCHESTRATION_CONTAINS)
                .anyMatch(e -> e.properties().containsKey("order")),
                "BIZTALK_ORCHESTRATION_CONTAINS edges should have order attribute");
    }

    @Test
    void testUsesMapEdge() {
        ProjectGraph graph = new ProjectGraph();
        BizTalkOdxParser parser = new BizTalkOdxParser();

        parser.parse(TEST_ODX, graph);

        List<GraphEdge> edges = graph.getEdges();
        long usesMapEdges = edges.stream()
                .filter(e -> e.type() == EdgeType.BIZTALK_USES_MAP)
                .count();

        assertEquals(1, usesMapEdges, "Should have 1 BIZTALK_USES_MAP edge from Transform shape");

        GraphEdge mapEdge = edges.stream()
                .filter(e -> e.type() == EdgeType.BIZTALK_USES_MAP)
                .findFirst()
                .orElse(null);

        assertNotNull(mapEdge, "BIZTALK_USES_MAP edge should exist");
        assertTrue(mapEdge.from().contains("MapOrderToInvoice"), "Edge should be from Transform shape");
        assertEquals("biztalk-map:OrderToInvoice", mapEdge.to(),
                "Map ID should use short name to match BTM parser output");
    }

    @Test
    void testUsesSchemaEdges() {
        ProjectGraph graph = new ProjectGraph();
        BizTalkOdxParser parser = new BizTalkOdxParser();

        parser.parse(TEST_ODX, graph);

        List<GraphEdge> edges = graph.getEdges();
        long usesSchemaEdges = edges.stream()
                .filter(e -> e.type() == EdgeType.BIZTALK_USES_SCHEMA)
                .count();

        assertEquals(2, usesSchemaEdges, "Should have 2 BIZTALK_USES_SCHEMA edges from messages");

        List<String> schemaTargets = edges.stream()
                .filter(e -> e.type() == EdgeType.BIZTALK_USES_SCHEMA)
                .map(GraphEdge::to)
                .toList();

        assertTrue(schemaTargets.contains("biztalk-schema:MyApp.Schemas.OrderSchema"), "Should reference OrderSchema");
        assertTrue(schemaTargets.contains("biztalk-schema:MyApp.Schemas.InvoiceSchema"),
                "Should reference InvoiceSchema");
    }

    @Test
    void testSilentlyHandlesInvalidFile() {
        ProjectGraph graph = new ProjectGraph();
        BizTalkOdxParser parser = new BizTalkOdxParser();

        Path nonExistent = Paths.get("does-not-exist.odx");
        assertDoesNotThrow(() -> parser.parse(nonExistent, graph));
        assertEquals(0, graph.nodeCount(), "Should not add any nodes for non-existent file");
    }

    @Test
    void testSilentlyHandlesMalformedXml() {
        ProjectGraph graph = new ProjectGraph();
        BizTalkOdxParser parser = new BizTalkOdxParser();

        Path malformed = Paths.get("src/test/resources/testdata/biztalk/malformed.odx");
        assertDoesNotThrow(() -> parser.parse(malformed, graph));
    }
}
