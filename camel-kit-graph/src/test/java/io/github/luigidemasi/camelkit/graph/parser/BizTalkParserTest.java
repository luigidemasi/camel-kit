package io.github.luigidemasi.camelkit.graph.parser;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

import io.github.luigidemasi.camelkit.graph.ProjectGraph;
import io.github.luigidemasi.camelkit.graph.model.*;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for BizTalkParser that verifies all BizTalk artifact types are parsed correctly.
 */
class BizTalkParserTest {

    @Test
    void testBizTalkParserFindsAllArtifactTypes() throws URISyntaxException {
        // Given: test data directory with BizTalk artifacts
        Path testDataDir = Paths.get(
                getClass().getResource("/testdata/biztalk").toURI());

        // When: BizTalkParser walks the directory
        ProjectGraph graph = new ProjectGraph();
        BizTalkParser parser = new BizTalkParser();
        parser.parse(testDataDir, graph);

        // Then: verify all BizTalk artifact types are detected
        Set<NodeType> nodeTypes = graph.getNodes().values().stream()
                .map(GraphNode::type)
                .collect(java.util.stream.Collectors.toSet());

        // At least 1 BIZTALK_ORCHESTRATION (from SimpleOrchestration.odx)
        assertTrue(nodeTypes.contains(NodeType.BIZTALK_ORCHESTRATION),
                "Should find at least one BIZTALK_ORCHESTRATION node from .odx file");

        // At least 1 BIZTALK_MAP (from OrderToInvoice.btm)
        assertTrue(nodeTypes.contains(NodeType.BIZTALK_MAP),
                "Should find at least one BIZTALK_MAP node from .btm file");

        // At least 1 BIZTALK_PIPELINE (from XMLReceive.btp)
        assertTrue(nodeTypes.contains(NodeType.BIZTALK_PIPELINE),
                "Should find at least one BIZTALK_PIPELINE node from .btp file");

        // At least 1 BIZTALK_ADAPTER (from PortBindings.xml)
        assertTrue(nodeTypes.contains(NodeType.BIZTALK_ADAPTER),
                "Should find at least one BIZTALK_ADAPTER node from binding XML file");

        // Verify specific nodes exist
        long orchestrationCount = graph.getNodes().values().stream()
                .filter(n -> n.type() == NodeType.BIZTALK_ORCHESTRATION)
                .count();
        assertTrue(orchestrationCount > 0,
                "Should have at least one orchestration");

        long mapCount = graph.getNodes().values().stream()
                .filter(n -> n.type() == NodeType.BIZTALK_MAP)
                .count();
        assertTrue(mapCount > 0,
                "Should have at least one map");

        long pipelineCount = graph.getNodes().values().stream()
                .filter(n -> n.type() == NodeType.BIZTALK_PIPELINE)
                .count();
        assertTrue(pipelineCount > 0,
                "Should have at least one pipeline");

        long adapterCount = graph.getNodes().values().stream()
                .filter(n -> n.type() == NodeType.BIZTALK_ADAPTER)
                .count();
        assertTrue(adapterCount > 0,
                "Should have at least one adapter");
    }

    @Test
    void testXmlRouteParserDoesNotParseBizTalkFiles() throws URISyntaxException {
        // Given: test data directory with BizTalk XML files
        Path testDataDir = Paths.get(
                getClass().getResource("/testdata/biztalk").toURI());

        // When: XmlRouteParser walks the directory
        ProjectGraph graph = new ProjectGraph();
        XmlRouteParser xmlParser = new XmlRouteParser();
        xmlParser.parse(testDataDir, graph);

        // Then: verify NO CAMEL_ROUTE nodes are created from BizTalk XML files
        long camelRouteCount = graph.getNodes().values().stream()
                .filter(n -> n.type() == NodeType.CAMEL_ROUTE)
                .count();

        assertEquals(0, camelRouteCount,
                "XmlRouteParser should not create CAMEL_ROUTE nodes from BizTalk XML files");
    }

    @Test
    void testBizTalkParserIgnoresPomXml() throws URISyntaxException {
        // Given: a directory containing ONLY a pom.xml (no BizTalk artifacts)
        Path pomOnlyDir = Paths.get(
                getClass().getResource("/biztalk-pom-only").toURI());

        // When: BizTalkParser walks the directory
        ProjectGraph graph = new ProjectGraph();
        BizTalkParser parser = new BizTalkParser();
        parser.parse(pomOnlyDir, graph);

        // Then: no BizTalk nodes should be created — pom.xml is not a BizTalk artifact
        long bizTalkNodeCount = graph.getNodes().values().stream()
                .filter(n -> n.type().name().startsWith("BIZTALK_"))
                .count();

        assertEquals(0, bizTalkNodeCount,
                "BizTalkParser should not create any nodes from pom.xml");
    }

    @Test
    void testIsBizTalkXmlStaticMethod() {
        // Test BizTalk binding XML marker
        String bindingXml = "<?xml version=\"1.0\"?>\n" +
                            "<BindingInfo xmlns=\"http://schemas.microsoft.com/BizTalk/2003\">";
        assertTrue(BizTalkParser.isBizTalkXml(bindingXml),
                "Should recognize BizTalk binding XML");

        // Test BizTalk map marker
        String mapXml = "<?xml version=\"1.0\"?>\n" +
                        "<mapsource xmlns=\"http://schemas.microsoft.com/BizTalk/2003\">";
        assertTrue(BizTalkParser.isBizTalkXml(mapXml),
                "Should recognize BizTalk map XML");

        // Test BizTalk pipeline marker
        String pipelineXml = "<?xml version=\"1.0\"?>\n" +
                             "<Document xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" " +
                             "PolicyFilePath=\"BTSReceivePolicy.xml\">";
        assertTrue(BizTalkParser.isBizTalkXml(pipelineXml),
                "Should recognize BizTalk pipeline XML");

        // Test non-BizTalk XML
        String camelXml = "<?xml version=\"1.0\"?>\n" +
                          "<routes xmlns=\"http://camel.apache.org/schema/spring\">";
        assertFalse(BizTalkParser.isBizTalkXml(camelXml),
                "Should NOT recognize Camel XML as BizTalk");

        // Test Mule XML
        String muleXml = "<?xml version=\"1.0\"?>\n" +
                         "<mule xmlns=\"http://www.mulesoft.org/schema/mule/core\">";
        assertFalse(BizTalkParser.isBizTalkXml(muleXml),
                "Should NOT recognize Mule XML as BizTalk");
    }
}
