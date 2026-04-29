package io.github.luigidemasi.camelkit.graph.parser;

import java.nio.file.Path;
import java.util.List;

import io.github.luigidemasi.camelkit.graph.ProjectGraph;
import io.github.luigidemasi.camelkit.graph.model.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MuleXmlFlowParserTest {

    private static ProjectGraph graph;
    private static final Path TEST_PROJECT = Path.of("src/test/resources/testdata");

    @BeforeAll
    static void setUp() {
        graph = new ProjectGraph();
        new MuleXmlFlowParser().parse(TEST_PROJECT, graph);
    }

    // ── Mule 4.x flow tests ────────────────────────────────────────────

    @Test
    void parsesMule4Flow() {
        assertTrue(graph.hasNode("mule-flow:order-ingestion-flow"));
        assertEquals(NodeType.MULE_FLOW,
                graph.getNode("mule-flow:order-ingestion-flow").type());
    }

    @Test
    void parsesMule4SubFlows() {
        assertTrue(graph.hasNode("mule-subflow:enrich-order-subflow"));
        assertTrue(graph.hasNode("mule-subflow:validate-order-subflow"));
        assertEquals(NodeType.MULE_SUB_FLOW,
                graph.getNode("mule-subflow:enrich-order-subflow").type());
        assertEquals(NodeType.MULE_SUB_FLOW,
                graph.getNode("mule-subflow:validate-order-subflow").type());
    }

    @Test
    void parsesMule4HttpListener() {
        List<GraphEdge> endpoints = graph.getOutgoingEdges("mule-flow:order-ingestion-flow").stream()
                .filter(e -> e.type() == EdgeType.MULE_FLOW_CONTAINS)
                .filter(e -> graph.getNode(e.to()) != null
                        && graph.getNode(e.to()).type() == NodeType.MULE_ENDPOINT)
                .toList();
        assertTrue(endpoints.size() >= 1, "flow should contain at least 1 endpoint");
    }

    @Test
    void parsesMule4Transform() {
        List<GraphEdge> transforms = graph.getOutgoingEdges("mule-flow:order-ingestion-flow").stream()
                .filter(e -> e.type() == EdgeType.MULE_FLOW_CONTAINS)
                .filter(e -> graph.getNode(e.to()) != null
                        && graph.getNode(e.to()).type() == NodeType.MULE_TRANSFORM)
                .toList();
        assertEquals(1, transforms.size(), "flow should contain exactly 1 transform");
    }

    @Test
    void parsesMule4Processors() {
        List<GraphEdge> processors = graph.getOutgoingEdges("mule-flow:order-ingestion-flow").stream()
                .filter(e -> e.type() == EdgeType.MULE_FLOW_CONTAINS)
                .filter(e -> graph.getNode(e.to()) != null
                        && graph.getNode(e.to()).type() == NodeType.MULE_PROCESSOR)
                .toList();
        assertTrue(processors.size() >= 1, "flow should contain at least 1 processor (choice)");
    }

    @Test
    void parsesMule4ErrorHandler() {
        List<GraphEdge> errorHandlers = graph.getOutgoingEdges("mule-flow:order-ingestion-flow").stream()
                .filter(e -> e.type() == EdgeType.MULE_FLOW_CONTAINS)
                .filter(e -> graph.getNode(e.to()) != null
                        && graph.getNode(e.to()).type() == NodeType.MULE_ERROR_HANDLER)
                .toList();
        assertEquals(1, errorHandlers.size(), "flow should contain exactly 1 error handler");
    }

    @Test
    void parsesMule4FlowRef() {
        List<GraphEdge> refs = graph.getOutgoingEdges("mule-subflow:enrich-order-subflow").stream()
                .filter(e -> e.type() == EdgeType.MULE_CALLS_SUBFLOW)
                .toList();
        assertTrue(refs.stream().anyMatch(
                e -> e.to().equals("mule-subflow:validate-order-subflow")),
                "enrich-order-subflow should call validate-order-subflow");
    }

    @Test
    void parsesMule4Connectors() {
        assertTrue(graph.hasNode("mule-connector:http-listener-config"),
                "http connector config should exist");
        assertTrue(graph.hasNode("mule-connector:db-config"),
                "db connector config should exist");
        assertTrue(graph.hasNode("mule-connector:jms-config"),
                "jms connector config should exist");
    }

    @Test
    void createsConnectorEdges() {
        List<GraphEdge> connectorEdges = graph.getEdges().stream()
                .filter(e -> e.type() == EdgeType.MULE_USES_CONNECTOR)
                .toList();
        assertTrue(connectorEdges.size() >= 1,
                "at least 1 MULE_USES_CONNECTOR edge should exist");
    }

    // ── Mule 3.x flow tests ────────────────────────────────────────────

    @Test
    void parsesMule3Flow() {
        assertTrue(graph.hasNode("mule-flow:legacy-order-flow"));
        assertEquals(NodeType.MULE_FLOW,
                graph.getNode("mule-flow:legacy-order-flow").type());
    }

    @Test
    void parsesMule3InboundEndpoint() {
        List<GraphEdge> endpoints = graph.getOutgoingEdges("mule-flow:legacy-order-flow").stream()
                .filter(e -> e.type() == EdgeType.MULE_FLOW_CONTAINS)
                .filter(e -> graph.getNode(e.to()) != null
                        && graph.getNode(e.to()).type() == NodeType.MULE_ENDPOINT)
                .toList();
        assertTrue(endpoints.size() >= 1, "Mule 3 flow should contain at least 1 endpoint");
    }

    @Test
    void parsesMule3Transform() {
        List<GraphEdge> transforms = graph.getOutgoingEdges("mule-flow:legacy-order-flow").stream()
                .filter(e -> e.type() == EdgeType.MULE_FLOW_CONTAINS)
                .filter(e -> graph.getNode(e.to()) != null
                        && graph.getNode(e.to()).type() == NodeType.MULE_TRANSFORM)
                .toList();
        assertEquals(1, transforms.size(), "Mule 3 flow should contain exactly 1 transform");
    }

    @Test
    void parsesMule3ExceptionStrategy() {
        List<GraphEdge> handlers = graph.getOutgoingEdges("mule-flow:legacy-order-flow").stream()
                .filter(e -> e.type() == EdgeType.MULE_FLOW_CONTAINS)
                .filter(e -> graph.getNode(e.to()) != null
                        && graph.getNode(e.to()).type() == NodeType.MULE_ERROR_HANDLER)
                .toList();
        assertEquals(1, handlers.size(), "Mule 3 flow should contain exactly 1 error handler");
    }

    @Test
    void parsesMule3Connector() {
        assertTrue(graph.hasNode("mule-connector:httpConnector"),
                "Mule 3 connector should exist");
        assertEquals(NodeType.MULE_CONNECTOR,
                graph.getNode("mule-connector:httpConnector").type());
    }

    // ── Negative test ───────────────────────────────────────────────────

    @Test
    void skipsCamelXml() {
        List<GraphNode> muleNodes = graph.getNodes().values().stream()
                .filter(n -> n.type().name().startsWith("MULE_"))
                .filter(n -> n.properties().containsKey("file")
                        && n.properties().get("file").contains("xml/camel-context.xml"))
                .toList();
        assertTrue(muleNodes.isEmpty(),
                "no MULE_ nodes should come from camel-context.xml");
    }
}
