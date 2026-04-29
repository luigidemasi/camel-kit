package io.github.luigidemasi.camelkit.graph.parser.biztalk;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import io.github.luigidemasi.camelkit.graph.ProjectGraph;
import io.github.luigidemasi.camelkit.graph.model.GraphNode;
import io.github.luigidemasi.camelkit.graph.model.NodeType;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BizTalkBindingParserTest {

    private static final Path TEST_DATA_DIR = Paths.get("src/test/resources/testdata/biztalk");
    private static final Path TEST_BINDING = TEST_DATA_DIR.resolve("PortBindings.xml");

    @Test
    void testParseAdapterNodes() {
        ProjectGraph graph = new ProjectGraph();
        BizTalkBindingParser parser = new BizTalkBindingParser();

        parser.parse(TEST_BINDING, graph);

        List<GraphNode> adapters = graph.findByType(NodeType.BIZTALK_ADAPTER);
        assertEquals(2, adapters.size(), "Should parse exactly 2 adapter nodes (FILE and SQL)");
    }

    @Test
    void testParseReceiveLocation() {
        ProjectGraph graph = new ProjectGraph();
        BizTalkBindingParser parser = new BizTalkBindingParser();

        parser.parse(TEST_BINDING, graph);

        List<GraphNode> adapters = graph.findByType(NodeType.BIZTALK_ADAPTER);
        Map<String, GraphNode> adapterMap = adapters.stream()
                .collect(java.util.stream.Collectors.toMap(
                        n -> n.properties().get("name"),
                        n -> n));

        GraphNode receiveLocation = adapterMap.get("ReceiveLocation_FileOrders");
        assertNotNull(receiveLocation, "ReceiveLocation_FileOrders should exist");
        assertEquals("biztalk-adapter:ReceiveLocation_FileOrders", receiveLocation.id());
        assertEquals("ReceiveLocation_FileOrders", receiveLocation.properties().get("name"));
        assertEquals("receive", receiveLocation.properties().get("direction"));
        assertEquals("FILE", receiveLocation.properties().get("transportType"));
        assertEquals("C:\\Orders\\In\\*.xml", receiveLocation.properties().get("address"));
        assertEquals("MyApp.Pipelines.XMLReceive", receiveLocation.properties().get("pipelineName"));
        assertEquals("60", receiveLocation.properties().get("pollingInterval"));
    }

    @Test
    void testParseSendPort() {
        ProjectGraph graph = new ProjectGraph();
        BizTalkBindingParser parser = new BizTalkBindingParser();

        parser.parse(TEST_BINDING, graph);

        List<GraphNode> adapters = graph.findByType(NodeType.BIZTALK_ADAPTER);
        Map<String, GraphNode> adapterMap = adapters.stream()
                .collect(java.util.stream.Collectors.toMap(
                        n -> n.properties().get("name"),
                        n -> n));

        GraphNode sendPort = adapterMap.get("SendPort_Invoices");
        assertNotNull(sendPort, "SendPort_Invoices should exist");
        assertEquals("biztalk-adapter:SendPort_Invoices", sendPort.id());
        assertEquals("SendPort_Invoices", sendPort.properties().get("name"));
        assertEquals("send", sendPort.properties().get("direction"));
        assertEquals("SQL", sendPort.properties().get("transportType"));
        assertEquals("mssql://localhost/InvoiceDB", sendPort.properties().get("address"));
        assertEquals("Microsoft.BizTalk.DefaultPipelines.PassThruTransmit", sendPort.properties().get("pipelineName"));
    }

    @Test
    void testSilentlyHandlesInvalidFile() {
        ProjectGraph graph = new ProjectGraph();
        BizTalkBindingParser parser = new BizTalkBindingParser();

        Path nonExistent = Paths.get("does-not-exist.xml");
        assertDoesNotThrow(() -> parser.parse(nonExistent, graph));
        assertEquals(0, graph.nodeCount(), "Should not add any nodes for non-existent file");
    }

    @Test
    void testSilentlyHandlesMalformedXml() {
        ProjectGraph graph = new ProjectGraph();
        BizTalkBindingParser parser = new BizTalkBindingParser();

        Path malformed = Paths.get("src/test/resources/testdata/biztalk/malformed.odx");
        assertDoesNotThrow(() -> parser.parse(malformed, graph));
    }
}
