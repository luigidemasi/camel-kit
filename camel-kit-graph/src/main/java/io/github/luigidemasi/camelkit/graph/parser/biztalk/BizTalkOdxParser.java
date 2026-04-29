package io.github.luigidemasi.camelkit.graph.parser.biztalk;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
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
 * Parser for BizTalk ODX (orchestration) files.
 * <p>
 * ODX files are hybrid text-XML files containing XML metadata between {@code <?xml} and {@code #endif} markers,
 * followed by binary designer data. This parser extracts and parses the XML portion using StAX.
 * </p>
 */
public class BizTalkOdxParser {

    private static final String BIZTALK_NS = "http://schemas.microsoft.com/BizTalk/2003/DesignerData";

    /**
     * Parse a single ODX file and add nodes/edges to the graph.
     *
     * @param odxFile the ODX file to parse
     * @param graph   the project graph to populate
     */
    public void parse(Path odxFile, ProjectGraph graph) {
        try {
            if (!Files.exists(odxFile)) {
                return;
            }

            String content = Files.readString(odxFile, StandardCharsets.UTF_8);
            String xmlContent = extractXmlContent(content);
            if (xmlContent == null || xmlContent.isEmpty()) {
                return;
            }

            parseOdxXml(xmlContent, graph);
        } catch (Exception e) {
            System.err.println("Failed to parse BizTalk ODX file: " + odxFile + " - " + e.getMessage());
        }
    }

    /**
     * Extract XML content from ODX file. ODX files have XML between {@code <?xml} and {@code #endif}.
     */
    private String extractXmlContent(String content) {
        int xmlStart = content.indexOf("<?xml");
        if (xmlStart == -1) {
            return null;
        }

        int endifPos = content.indexOf("#endif", xmlStart);
        if (endifPos == -1) {
            return null;
        }

        return content.substring(xmlStart, endifPos).trim();
    }

    /**
     * Parse the extracted XML content using StAX.
     */
    private void parseOdxXml(String xmlContent, ProjectGraph graph) throws Exception {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);

        XMLStreamReader reader = factory.createXMLStreamReader(new StringReader(xmlContent));

        try {
            while (reader.hasNext()) {
                int event = reader.next();

                if (event == XMLStreamConstants.START_ELEMENT && "Element".equals(reader.getLocalName())) {
                    String elementType = reader.getAttributeValue(null, "Type");

                    if ("Module".equals(elementType)) {
                        parseModule(reader, graph);
                    }
                }
            }
        } finally {
            reader.close();
        }
    }

    /**
     * Parse Module element and its descendants.
     */
    private void parseModule(XMLStreamReader reader, ProjectGraph graph) throws Exception {
        String namespace = null;
        int depth = 1;

        while (reader.hasNext() && depth > 0) {
            int event = reader.next();

            if (event == XMLStreamConstants.START_ELEMENT) {
                String localName = reader.getLocalName();

                if ("Property".equals(localName) && depth == 1) {
                    // Only read properties at depth 1 (direct children of Module)
                    String propName = reader.getAttributeValue(null, "Name");
                    String propValue = reader.getAttributeValue(null, "Value");
                    if ("Name".equals(propName) && namespace == null) {
                        namespace = propValue;
                    }
                } else if ("Element".equals(localName)) {
                    String elementType = reader.getAttributeValue(null, "Type");
                    if ("ServiceDeclaration".equals(elementType) && depth == 1) {
                        // ServiceDeclaration is a direct child of Module
                        parseServiceDeclaration(reader, namespace, graph);
                        // Don't increment depth here because parseServiceDeclaration consumes the entire element
                    } else {
                        depth++;
                    }
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                if ("Element".equals(reader.getLocalName())) {
                    depth--;
                }
            }
        }
    }

    /**
     * Parse ServiceDeclaration element and its children.
     */
    private void parseServiceDeclaration(XMLStreamReader reader, String namespace, ProjectGraph graph)
            throws Exception {
        String orchestrationName = null;
        String orchId = null;
        int order = 0;
        int depth = 1;
        int elementDepth = 0; // Track nested Element depth

        while (reader.hasNext() && depth > 0) {
            int event = reader.next();

            if (event == XMLStreamConstants.START_ELEMENT) {
                String localName = reader.getLocalName();

                if ("Property".equals(localName) && elementDepth == 0) {
                    // Only extract orchestration name from direct Property children (depth 0)
                    String propName = reader.getAttributeValue(null, "Name");
                    String propValue = reader.getAttributeValue(null, "Value");
                    if ("Name".equals(propName) && orchestrationName == null) {
                        orchestrationName = propValue;
                        orchId = buildOrchestrationId(namespace, orchestrationName);

                        Map<String, String> nodeProps = new HashMap<>();
                        nodeProps.put("name", orchestrationName);
                        nodeProps.put("namespace", namespace);

                        GraphNode orchNode = new GraphNode(orchId, NodeType.BIZTALK_ORCHESTRATION, nodeProps);
                        graph.addNode(orchNode);
                    }
                } else if ("Element".equals(localName)) {
                    String elementType = reader.getAttributeValue(null, "Type");

                    if (orchId != null && elementDepth == 0) {
                        // Only process direct Element children
                        if ("MessageDeclaration".equals(elementType)) {
                            order = parseMessageDeclaration(reader, orchId, graph, order);
                            // parseMessageDeclaration consumes the entire element, don't increment elementDepth
                        } else if ("PortDeclaration".equals(elementType)) {
                            order = parsePortDeclaration(reader, orchId, graph, order);
                            // parsePortDeclaration consumes the entire element, don't increment elementDepth
                        } else if ("ServiceBody".equals(elementType)) {
                            order = parseServiceBody(reader, orchId, graph, order);
                            // parseServiceBody consumes the entire element, don't increment elementDepth
                        } else {
                            elementDepth++;
                            depth++;
                        }
                    } else {
                        elementDepth++;
                        depth++;
                    }
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                if ("Element".equals(reader.getLocalName())) {
                    depth--;
                    if (elementDepth > 0) {
                        elementDepth--;
                    }
                }
            }
        }
    }

    /**
     * Parse ServiceBody element containing shape elements.
     */
    private int parseServiceBody(XMLStreamReader reader, String orchId, ProjectGraph graph, int order)
            throws Exception {
        int depth = 1;

        while (reader.hasNext() && depth > 0) {
            int event = reader.next();

            if (event == XMLStreamConstants.START_ELEMENT) {
                String localName = reader.getLocalName();

                if ("Element".equals(localName)) {
                    String elementType = reader.getAttributeValue(null, "Type");

                    if (isShapeElement(elementType)) {
                        order = parseShape(reader, elementType, orchId, graph, order);
                    } else {
                        depth++;
                    }
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                if ("Element".equals(reader.getLocalName())) {
                    depth--;
                }
            }
        }

        return order;
    }

    /**
     * Parse MessageDeclaration to create message node and edges.
     */
    private int parseMessageDeclaration(XMLStreamReader reader, String orchId, ProjectGraph graph, int order)
            throws Exception {
        String name = null;
        String type = null;
        String direction = null;
        int depth = 1;

        while (reader.hasNext() && depth > 0) {
            int event = reader.next();

            if (event == XMLStreamConstants.START_ELEMENT) {
                String localName = reader.getLocalName();

                if ("Property".equals(localName)) {
                    String propName = reader.getAttributeValue(null, "Name");
                    String propValue = reader.getAttributeValue(null, "Value");
                    if ("Name".equals(propName)) {
                        name = propValue;
                    } else if ("Type".equals(propName)) {
                        type = propValue;
                    } else if ("Direction".equals(propName)) {
                        direction = propValue;
                    }
                } else if ("Element".equals(localName)) {
                    depth++;
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                if ("Element".equals(reader.getLocalName())) {
                    depth--;
                }
            }
        }

        if (name != null) {
            String messageId = orchId + ":message:" + name;

            Map<String, String> nodeProps = new HashMap<>();
            nodeProps.put("name", name);
            if (type != null) {
                nodeProps.put("type", type);
            }
            if (direction != null) {
                nodeProps.put("direction", direction);
            }

            GraphNode messageNode = new GraphNode(messageId, NodeType.BIZTALK_MESSAGE, nodeProps);
            graph.addNode(messageNode);

            // Create BIZTALK_ORCHESTRATION_CONTAINS edge
            Map<String, String> edgeProps = new HashMap<>();
            edgeProps.put("order", String.valueOf(order));
            GraphEdge containsEdge = new GraphEdge(
                    orchId, messageId, EdgeType.BIZTALK_ORCHESTRATION_CONTAINS,
                    edgeProps);
            graph.addEdge(containsEdge);

            // Create BIZTALK_USES_SCHEMA edge
            if (type != null) {
                String schemaId = "biztalk-schema:" + type;

                // Create schema node before wiring edge
                Map<String, String> schemaProps = new HashMap<>();
                schemaProps.put("name", type);
                GraphNode schemaNode = new GraphNode(schemaId, NodeType.BIZTALK_SCHEMA, schemaProps);
                graph.addNode(schemaNode);

                GraphEdge schemaEdge = new GraphEdge(messageId, schemaId, EdgeType.BIZTALK_USES_SCHEMA, Map.of());
                graph.addEdge(schemaEdge);
            }

            order++;
        }

        return order;
    }

    /**
     * Parse PortDeclaration to create port node and edges.
     */
    private int parsePortDeclaration(XMLStreamReader reader, String orchId, ProjectGraph graph, int order)
            throws Exception {
        String name = null;
        String direction = null;
        String adapter = null;
        String address = null;
        int depth = 1;

        while (reader.hasNext() && depth > 0) {
            int event = reader.next();

            if (event == XMLStreamConstants.START_ELEMENT) {
                String localName = reader.getLocalName();

                if ("Property".equals(localName)) {
                    String propName = reader.getAttributeValue(null, "Name");
                    String propValue = reader.getAttributeValue(null, "Value");
                    if ("Name".equals(propName)) {
                        name = propValue;
                    } else if ("Direction".equals(propName)) {
                        direction = propValue;
                    } else if ("Adapter".equals(propName)) {
                        adapter = propValue;
                    } else if ("Address".equals(propName)) {
                        address = propValue;
                    }
                } else if ("Element".equals(localName)) {
                    depth++;
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                if ("Element".equals(reader.getLocalName())) {
                    depth--;
                }
            }
        }

        if (name != null) {
            String portId = orchId + ":port:" + name;

            Map<String, String> nodeProps = new HashMap<>();
            nodeProps.put("name", name);
            if (direction != null) {
                nodeProps.put("direction", direction);
            }
            if (adapter != null) {
                nodeProps.put("adapter", adapter);
            }
            if (address != null) {
                nodeProps.put("address", address);
            }

            GraphNode portNode = new GraphNode(portId, NodeType.BIZTALK_PORT, nodeProps);
            graph.addNode(portNode);

            // Create BIZTALK_ORCHESTRATION_CONTAINS edge
            Map<String, String> edgeProps = new HashMap<>();
            edgeProps.put("order", String.valueOf(order));
            GraphEdge containsEdge = new GraphEdge(orchId, portId, EdgeType.BIZTALK_ORCHESTRATION_CONTAINS, edgeProps);
            graph.addEdge(containsEdge);

            order++;
        }

        return order;
    }

    /**
     * Parse shape elements (Receive, Send, Decide, Construct, Transform, etc.).
     */
    private int parseShape(XMLStreamReader reader, String shapeType, String orchId, ProjectGraph graph, int order)
            throws Exception {
        String name = null;
        Map<String, String> properties = new HashMap<>();
        int depth = 1;

        while (reader.hasNext() && depth > 0) {
            int event = reader.next();

            if (event == XMLStreamConstants.START_ELEMENT) {
                String localName = reader.getLocalName();

                if ("Property".equals(localName)) {
                    String propName = reader.getAttributeValue(null, "Name");
                    String propValue = reader.getAttributeValue(null, "Value");
                    if ("Name".equals(propName)) {
                        name = propValue;
                    } else if (propName != null && propValue != null) {
                        properties.put(propName, propValue);
                    }
                } else if ("Element".equals(localName)) {
                    String elementType = reader.getAttributeValue(null, "Type");
                    // Handle nested shapes like Transform inside Construct
                    if (isShapeElement(elementType)) {
                        order = parseShape(reader, elementType, orchId, graph, order);
                    } else {
                        depth++;
                    }
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                if ("Element".equals(reader.getLocalName())) {
                    depth--;
                }
            }
        }

        if (name != null) {
            String shapeId = orchId + ":shape:" + name + ":" + order;

            Map<String, String> nodeProps = new HashMap<>();
            nodeProps.put("name", name);
            nodeProps.put("shapeType", shapeType);

            // Copy relevant properties
            for (String key : properties.keySet()) {
                nodeProps.put(key.toLowerCase(java.util.Locale.ROOT), properties.get(key));
            }

            GraphNode shapeNode = new GraphNode(shapeId, NodeType.BIZTALK_SHAPE, nodeProps);
            graph.addNode(shapeNode);

            // Create BIZTALK_ORCHESTRATION_CONTAINS edge
            Map<String, String> edgeProps = new HashMap<>();
            edgeProps.put("order", String.valueOf(order));
            GraphEdge containsEdge = new GraphEdge(orchId, shapeId, EdgeType.BIZTALK_ORCHESTRATION_CONTAINS, edgeProps);
            graph.addEdge(containsEdge);

            // For Transform shapes, create BIZTALK_USES_MAP edge
            if ("Transform".equals(shapeType)) {
                String mapClassName = properties.get("ClassName");
                if (mapClassName != null) {
                    // Normalize to short class name to match BTM parser output
                    String shortMapName = mapClassName;
                    int lastDot = mapClassName.lastIndexOf('.');
                    if (lastDot >= 0) {
                        shortMapName = mapClassName.substring(lastDot + 1);
                    }
                    String mapId = "biztalk-map:" + shortMapName;
                    GraphEdge mapEdge = new GraphEdge(shapeId, mapId, EdgeType.BIZTALK_USES_MAP, Map.of());
                    graph.addEdge(mapEdge);
                }
            }

            order++;
        }

        return order;
    }

    /**
     * Check if element type represents a shape.
     */
    private boolean isShapeElement(String elementType) {
        if (elementType == null) {
            return false;
        }

        return "Receive".equals(elementType)
                || "Send".equals(elementType)
                || "Decide".equals(elementType)
                || "Construct".equals(elementType)
                || "Transform".equals(elementType)
                || "Delay".equals(elementType)
                || "Suspend".equals(elementType)
                || "Terminate".equals(elementType)
                || "Parallel".equals(elementType)
                || "Listen".equals(elementType)
                || "Scope".equals(elementType)
                || "Loop".equals(elementType)
                || "ThrowException".equals(elementType)
                || "Expression".equals(elementType)
                || "MessageAssignment".equals(elementType)
                || "VariableAssignment".equals(elementType);
    }

    /**
     * Build orchestration ID from namespace and name.
     */
    private String buildOrchestrationId(String namespace, String name) {
        if (namespace == null || namespace.isEmpty()) {
            return "biztalk-orch:" + name;
        }
        return "biztalk-orch:" + namespace + "." + name;
    }
}
