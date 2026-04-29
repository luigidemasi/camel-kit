package io.github.luigidemasi.camelkit.graph.parser.biztalk;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

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

class BizTalkBtpParserTest {

    private BizTalkBtpParser parser;
    private ProjectGraph graph;

    @BeforeEach
    void setUp() {
        parser = new BizTalkBtpParser();
        graph = new ProjectGraph();
    }

    @Test
    void testParseXMLReceiveBtp() throws URISyntaxException {
        Path btpFile = Paths.get(getClass().getResource("/testdata/biztalk/XMLReceive.btp").toURI());

        parser.parse(btpFile, graph);

        // Verify pipeline node
        GraphNode pipelineNode = graph.getNode("biztalk-pipeline:XMLReceive");
        assertNotNull(pipelineNode, "Pipeline node should exist");
        assertEquals(NodeType.BIZTALK_PIPELINE, pipelineNode.type());
        assertEquals("XMLReceive", pipelineNode.properties().get("name"));
        assertEquals("f66b9f5e-43ff-4f5f-ba46-885348ae1b4e", pipelineNode.properties().get("categoryId"));
        assertEquals("receive", pipelineNode.properties().get("direction"));
        assertTrue(pipelineNode.properties().get("file").endsWith("XMLReceive.btp"));

        // Verify component nodes
        GraphNode component1 = graph.getNode("biztalk-pipeline:XMLReceive:component:XMLdisassembler:0");
        assertNotNull(component1, "Component 1 should exist");
        assertEquals(NodeType.BIZTALK_PIPELINE_COMPONENT, component1.type());
        assertEquals("XML disassembler", component1.properties().get("componentName"));
        assertEquals("Microsoft.BizTalk.Component.XmlDasmComp", component1.properties().get("typeName"));
        assertEquals("1.0", component1.properties().get("version"));
        assertEquals("Streaming XML disassembler", component1.properties().get("description"));

        GraphNode component2 = graph.getNode("biztalk-pipeline:XMLReceive:component:XMLvalidator:1");
        assertNotNull(component2, "Component 2 should exist");
        assertEquals(NodeType.BIZTALK_PIPELINE_COMPONENT, component2.type());
        assertEquals("XML validator", component2.properties().get("componentName"));
        assertEquals("Microsoft.BizTalk.Component.XmlValidator", component2.properties().get("typeName"));
        assertEquals("1.0", component2.properties().get("version"));
        assertEquals("XML validator component", component2.properties().get("description"));

        // Verify pipeline stage edges
        List<GraphEdge> stageEdges = graph.getEdges().stream()
                .filter(e -> e.type() == EdgeType.BIZTALK_PIPELINE_STAGE)
                .toList();
        assertEquals(2, stageEdges.size(), "Should have 2 pipeline stage edges");

        GraphEdge edge1 = stageEdges.stream()
                .filter(e -> e.to().equals("biztalk-pipeline:XMLReceive:component:XMLdisassembler:0"))
                .findFirst()
                .orElse(null);
        assertNotNull(edge1, "Pipeline stage edge to component 1 should exist");
        assertEquals("biztalk-pipeline:XMLReceive", edge1.from());
        assertEquals("0", edge1.properties().get("order"));

        GraphEdge edge2 = stageEdges.stream()
                .filter(e -> e.to().equals("biztalk-pipeline:XMLReceive:component:XMLvalidator:1"))
                .findFirst()
                .orElse(null);
        assertNotNull(edge2, "Pipeline stage edge to component 2 should exist");
        assertEquals("biztalk-pipeline:XMLReceive", edge2.from());
        assertEquals("1", edge2.properties().get("order"));
    }

    @Test
    void testPipelineDirection() throws URISyntaxException {
        Path btpFile = Paths.get(getClass().getResource("/testdata/biztalk/XMLReceive.btp").toURI());

        parser.parse(btpFile, graph);

        GraphNode pipelineNode = graph.getNode("biztalk-pipeline:XMLReceive");
        assertNotNull(pipelineNode);
        assertEquals("receive", pipelineNode.properties().get("direction"));
    }

    @Test
    void testParseNonexistentFile() {
        Path nonexistent = Paths.get("/nonexistent/file.btp");
        parser.parse(nonexistent, graph);

        // Should not throw exception, graph should be empty
        assertEquals(0, graph.nodeCount());
        assertEquals(0, graph.edgeCount());
    }

    @Test
    void testPipelineNameDerivedFromFriendlyName() throws URISyntaxException {
        Path btpFile = Paths.get(getClass().getResource("/testdata/biztalk/XMLReceive.btp").toURI());

        parser.parse(btpFile, graph);

        GraphNode pipelineNode = graph.getNode("biztalk-pipeline:XMLReceive");
        assertNotNull(pipelineNode);
        assertEquals("XMLReceive", pipelineNode.properties().get("name"));
    }

    @Test
    void testGraphStructure() throws URISyntaxException {
        Path btpFile = Paths.get(getClass().getResource("/testdata/biztalk/XMLReceive.btp").toURI());

        parser.parse(btpFile, graph);

        // Verify total counts
        long pipelineNodes = graph.findByType(NodeType.BIZTALK_PIPELINE).size();
        assertEquals(1, pipelineNodes, "Should have 1 pipeline node");

        long componentNodes = graph.findByType(NodeType.BIZTALK_PIPELINE_COMPONENT).size();
        assertEquals(2, componentNodes, "Should have 2 pipeline component nodes");

        long stageEdges = graph.getEdges().stream()
                .filter(e -> e.type() == EdgeType.BIZTALK_PIPELINE_STAGE)
                .count();
        assertEquals(2, stageEdges, "Should have 2 pipeline stage edges");
    }

    @Test
    void testComponentOrderAttributes() throws URISyntaxException {
        Path btpFile = Paths.get(getClass().getResource("/testdata/biztalk/XMLReceive.btp").toURI());

        parser.parse(btpFile, graph);

        List<GraphEdge> stageEdges = graph.getEdges().stream()
                .filter(e -> e.type() == EdgeType.BIZTALK_PIPELINE_STAGE)
                .sorted((e1, e2) -> {
                    int order1 = Integer.parseInt(e1.properties().get("order"));
                    int order2 = Integer.parseInt(e2.properties().get("order"));
                    return Integer.compare(order1, order2);
                })
                .toList();

        assertEquals(2, stageEdges.size());
        assertEquals("0", stageEdges.get(0).properties().get("order"));
        assertEquals("1", stageEdges.get(1).properties().get("order"));
    }
}
