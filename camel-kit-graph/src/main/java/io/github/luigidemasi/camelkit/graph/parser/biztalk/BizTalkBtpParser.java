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
import io.github.luigidemasi.camelkit.graph.model.EdgeType;
import io.github.luigidemasi.camelkit.graph.model.GraphEdge;
import io.github.luigidemasi.camelkit.graph.model.GraphNode;
import io.github.luigidemasi.camelkit.graph.model.NodeType;

/**
 * Parser for BizTalk BTP (pipeline) files.
 * <p>
 * BTP files are XML files that define BizTalk receive and send pipelines, which consist of stages containing pipeline
 * components that process messages.
 * </p>
 */
public class BizTalkBtpParser {

    /**
     * GUID identifying receive pipelines.
     */
    private static final String RECEIVE_PIPELINE_GUID = "f66b9f5e-43ff-4f5f-ba46-885348ae1b4e";

    /**
     * GUID identifying send pipelines.
     */
    private static final String SEND_CATEGORY = "8c6b051c-0ff5-4fc2-9ae5-5016cb726282";

    /**
     * Holds component data until the final pipeline name is determined.
     */
    private record PendingComponent(String componentName, String typeName, String version, String description,
            int order) {
    }

    /**
     * Parse a single BTP file and add nodes/edges to the graph.
     *
     * @param btpFile the BTP file to parse
     * @param graph   the project graph to populate
     */
    public void parse(Path btpFile, ProjectGraph graph) {
        try {
            if (!Files.exists(btpFile)) {
                return;
            }

            parseBtpXml(btpFile, graph);
        } catch (Exception e) {
            System.err.println("Failed to parse BizTalk BTP file: " + btpFile + " - " + e.getMessage());
        }
    }

    /**
     * Parse BTP XML content using StAX.
     */
    private void parseBtpXml(Path btpFile, ProjectGraph graph) throws Exception {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);

        try (InputStream input = Files.newInputStream(btpFile)) {
            XMLStreamReader reader = factory.createXMLStreamReader(input);

            try {
                while (reader.hasNext()) {
                    int event = reader.next();

                    if (event == XMLStreamConstants.START_ELEMENT && "Document".equals(reader.getLocalName())) {
                        parseDocument(reader, btpFile, graph);
                    }
                }
            } finally {
                reader.close();
            }
        }
    }

    /**
     * Parse the Document root element.
     */
    private void parseDocument(XMLStreamReader reader, Path btpFile, ProjectGraph graph) throws Exception {
        // Extract pipeline name from filename (strip .btp extension)
        String fileName = btpFile.getFileName().toString();
        String pipelineName = fileName.endsWith(".btp") ? fileName.substring(0, fileName.length() - 4) : fileName;

        String categoryId = null;
        String friendlyName = null;
        int componentOrder = 0;

        // Buffer components until the final pipeline name is determined
        List<PendingComponent> pendingComponents = new ArrayList<>();

        while (reader.hasNext()) {
            int event = reader.next();

            if (event == XMLStreamConstants.START_ELEMENT) {
                String localName = reader.getLocalName();

                if ("CategoryId".equals(localName)) {
                    categoryId = readElementText(reader);
                } else if ("FriendlyName".equals(localName)) {
                    friendlyName = readElementText(reader);
                } else if ("Component".equals(localName)) {
                    parseComponent(reader, pendingComponents, componentOrder++);
                    // parseComponent consumes the entire Component element including closing tag
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                if ("Document".equals(reader.getLocalName())) {
                    break;
                }
            }
        }

        // Use FriendlyName if present, otherwise use filename
        String finalPipelineName = friendlyName != null ? friendlyName : pipelineName;

        // Determine pipeline direction based on CategoryId
        String direction;
        if (categoryId != null) {
            if (RECEIVE_PIPELINE_GUID.equalsIgnoreCase(categoryId)) {
                direction = "receive";
            } else if (SEND_CATEGORY.equalsIgnoreCase(categoryId)) {
                direction = "send";
            } else {
                direction = "unknown";
            }
        } else {
            direction = "unknown";
        }

        // Create pipeline node
        String pipelineId = "biztalk-pipeline:" + finalPipelineName;
        Map<String, String> pipelineProps = new HashMap<>();
        pipelineProps.put("file", btpFile.toString());
        pipelineProps.put("name", finalPipelineName);
        if (categoryId != null) {
            pipelineProps.put("categoryId", categoryId);
        }
        pipelineProps.put("direction", direction);

        GraphNode pipelineNode = new GraphNode(pipelineId, NodeType.BIZTALK_PIPELINE, pipelineProps);
        graph.addNode(pipelineNode);

        // Now emit all component nodes and edges using the final pipeline name
        for (PendingComponent comp : pendingComponents) {
            String componentNameForId = comp.componentName().replace(" ", "");
            String componentId = pipelineId + ":component:" + componentNameForId + ":" + comp.order();

            Map<String, String> componentProps = new HashMap<>();
            componentProps.put("componentName", comp.componentName());
            if (comp.typeName() != null) {
                componentProps.put("typeName", comp.typeName());
            }
            if (comp.version() != null) {
                componentProps.put("version", comp.version());
            }
            if (comp.description() != null) {
                componentProps.put("description", comp.description());
            }

            GraphNode componentNode = new GraphNode(componentId, NodeType.BIZTALK_PIPELINE_COMPONENT, componentProps);
            graph.addNode(componentNode);

            Map<String, String> edgeProps = new HashMap<>();
            edgeProps.put("order", String.valueOf(comp.order()));
            GraphEdge edge = new GraphEdge(pipelineId, componentId, EdgeType.BIZTALK_PIPELINE_STAGE, edgeProps);
            graph.addEdge(edge);
        }
    }

    /**
     * Parse a Component element and buffer it until the pipeline name is finalized.
     */
    private void parseComponent(XMLStreamReader reader, List<PendingComponent> pendingComponents, int order)
            throws Exception {
        String typeName = reader.getAttributeValue(null, "Name");
        String componentName = reader.getAttributeValue(null, "ComponentName");
        String version = reader.getAttributeValue(null, "Version");
        String description = reader.getAttributeValue(null, "Description");

        if (componentName == null) {
            skipElement(reader);
            return;
        }

        // Buffer component data
        pendingComponents.add(new PendingComponent(componentName, typeName, version, description, order));

        // Consume the rest of the Component element
        skipElement(reader);
    }

    /**
     * Read the text content of the current element.
     */
    private String readElementText(XMLStreamReader reader) throws Exception {
        StringBuilder text = new StringBuilder();
        int depth = 1;

        while (reader.hasNext() && depth > 0) {
            int event = reader.next();

            if (event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA) {
                text.append(reader.getText());
            } else if (event == XMLStreamConstants.START_ELEMENT) {
                depth++;
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                depth--;
            }
        }

        return text.toString().trim();
    }

    /**
     * Skip the current element and all its children.
     */
    private void skipElement(XMLStreamReader reader) throws Exception {
        int depth = 1;
        while (reader.hasNext() && depth > 0) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                depth++;
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                depth--;
            }
        }
    }
}
