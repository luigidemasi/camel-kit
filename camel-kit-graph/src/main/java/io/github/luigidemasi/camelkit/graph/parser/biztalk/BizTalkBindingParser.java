package io.github.luigidemasi.camelkit.graph.parser.biztalk;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;

import io.github.luigidemasi.camelkit.graph.ProjectGraph;
import io.github.luigidemasi.camelkit.graph.model.GraphNode;
import io.github.luigidemasi.camelkit.graph.model.NodeType;

/**
 * Parser for BizTalk binding XML files.
 * <p>
 * Binding files contain port and adapter configurations, including receive locations and send ports with their
 * transport types, addresses, pipelines, and polling intervals.
 * </p>
 */
public class BizTalkBindingParser {

    private static final String BIZTALK_NS = "http://schemas.microsoft.com/BizTalk/2003";

    /**
     * Parse a single binding XML file and add adapter nodes to the graph.
     *
     * @param bindingFile the binding XML file to parse
     * @param graph       the project graph to populate
     */
    public void parse(Path bindingFile, ProjectGraph graph) {
        try {
            if (!Files.exists(bindingFile)) {
                return;
            }

            parseBindingXml(bindingFile, graph);
        } catch (Exception e) {
            System.err.println("Failed to parse BizTalk binding file: " + bindingFile + " - " + e.getMessage());
        }
    }

    /**
     * Parse the binding XML file using StAX.
     */
    private void parseBindingXml(Path bindingFile, ProjectGraph graph) throws Exception {
        List<GraphNode> pendingNodes = new ArrayList<>();
        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);

        try (InputStream input = Files.newInputStream(bindingFile)) {
            XMLStreamReader reader = factory.createXMLStreamReader(input);

            try {
                while (reader.hasNext()) {
                    int event = reader.next();

                    if (event == XMLStreamConstants.START_ELEMENT) {
                        String localName = reader.getLocalName();

                        if ("ReceiveLocation".equals(localName)) {
                            parseReceiveLocation(reader, pendingNodes);
                        } else if ("SendPort".equals(localName)) {
                            parseSendPort(reader, pendingNodes);
                        }
                    }
                }

                // Commit all nodes only after successful parse
                pendingNodes.forEach(graph::addNode);
            } finally {
                reader.close();
            }
        }
    }

    /**
     * Parse ReceiveLocation element and create adapter node.
     */
    private void parseReceiveLocation(XMLStreamReader reader, List<GraphNode> pendingNodes) throws Exception {
        String name = reader.getAttributeValue(null, "Name");
        String address = null;
        String transportType = null;
        String pipelineName = null;
        String pollingInterval = null;
        int depth = 1;

        while (reader.hasNext() && depth > 0) {
            int event = reader.next();

            if (event == XMLStreamConstants.START_ELEMENT) {
                String localName = reader.getLocalName();

                if ("Address".equals(localName)) {
                    address = reader.getElementText();
                    // getElementText() consumes the end tag, so don't increment depth
                    continue;
                } else if ("TransportType".equals(localName)) {
                    transportType = reader.getAttributeValue(null, "Name");
                    depth++;
                } else if ("ReceivePipeline".equals(localName)) {
                    pipelineName = reader.getAttributeValue(null, "Name");
                    depth++;
                } else if ("PollingInterval".equals(localName)) {
                    pollingInterval = reader.getElementText();
                    // getElementText() consumes the end tag, so don't increment depth
                    continue;
                } else {
                    depth++;
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                depth--;
            }
        }

        if (name != null) {
            String adapterId = "biztalk-adapter:" + name;

            Map<String, String> nodeProps = new HashMap<>();
            nodeProps.put("name", name);
            nodeProps.put("direction", "receive");
            if (transportType != null) {
                nodeProps.put("transportType", transportType);
            }
            if (address != null) {
                nodeProps.put("address", address);
            }
            if (pipelineName != null) {
                nodeProps.put("pipelineName", pipelineName);
            }
            if (pollingInterval != null) {
                nodeProps.put("pollingInterval", pollingInterval);
            }

            GraphNode adapterNode = new GraphNode(adapterId, NodeType.BIZTALK_ADAPTER, nodeProps);
            pendingNodes.add(adapterNode);
        }
    }

    /**
     * Parse SendPort element and create adapter node.
     */
    private void parseSendPort(XMLStreamReader reader, List<GraphNode> pendingNodes) throws Exception {
        String name = reader.getAttributeValue(null, "Name");
        String address = null;
        String transportType = null;
        String pipelineName = null;
        int depth = 1;

        while (reader.hasNext() && depth > 0) {
            int event = reader.next();

            if (event == XMLStreamConstants.START_ELEMENT) {
                String localName = reader.getLocalName();

                if ("Address".equals(localName)) {
                    address = reader.getElementText();
                    // getElementText() consumes the end tag, so don't increment depth
                    continue;
                } else if ("TransportType".equals(localName)) {
                    transportType = reader.getAttributeValue(null, "Name");
                    depth++;
                } else if ("SendPipeline".equals(localName)) {
                    pipelineName = reader.getAttributeValue(null, "Name");
                    depth++;
                } else {
                    depth++;
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                depth--;
            }
        }

        if (name != null) {
            String adapterId = "biztalk-adapter:" + name;

            Map<String, String> nodeProps = new HashMap<>();
            nodeProps.put("name", name);
            nodeProps.put("direction", "send");
            if (transportType != null) {
                nodeProps.put("transportType", transportType);
            }
            if (address != null) {
                nodeProps.put("address", address);
            }
            if (pipelineName != null) {
                nodeProps.put("pipelineName", pipelineName);
            }

            GraphNode adapterNode = new GraphNode(adapterId, NodeType.BIZTALK_ADAPTER, nodeProps);
            pendingNodes.add(adapterNode);
        }
    }
}
