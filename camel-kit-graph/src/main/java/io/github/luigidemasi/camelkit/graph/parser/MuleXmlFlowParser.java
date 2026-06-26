package io.github.luigidemasi.camelkit.graph.parser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.*;

import io.github.luigidemasi.camelkit.graph.ProjectGraph;
import io.github.luigidemasi.camelkit.graph.model.*;

public class MuleXmlFlowParser implements GraphParser {

    private static final String MULE_NS_MARKER = "mulesoft.org/schema/mule";
    private static final int SNIFF_BYTES = 1024;

    /** Mule 4.x element local names that are source/sink endpoints. */
    private static final Set<String> MULE4_ENDPOINT_NAMES = Set.of(
            "listener", "consume", "request", "publish",
            "insert", "update", "delete", "select");

    /** Mule elements that represent error handling. */
    private static final Set<String> ERROR_HANDLER_NAMES = Set.of(
            "error-handler", "on-error-continue", "on-error-propagate", "exception-strategy");

    /** Mule elements that are generic processors. */
    private static final Set<String> PROCESSOR_NAMES = Set.of(
            "choice", "foreach", "scatter-gather",
            "set-payload", "set-variable", "logger",
            "remove-variable", "raise-error", "try",
            "until-successful", "round-robin", "first-successful");

    /** Wrapper elements whose children should be recursed into. */
    private static final Set<String> RECURSE_ELEMENTS = Set.of(
            "when", "otherwise");

    @Override
    public void parse(Path projectRoot, ProjectGraph graph) {
        parseFiles(projectRoot, scannedFilePaths(projectRoot, GraphParser.projectFiles(projectRoot)), graph);
    }

    @Override
    public List<String> scannedFiles(Path projectRoot) {
        return GraphParser.findFiles(projectRoot, this::isMuleXmlCandidate);
    }

    @Override
    public List<Path> scannedFilePaths(Path projectRoot, List<Path> projectFiles) {
        return GraphParser.findFilePaths(projectRoot, projectFiles, this::isMuleXmlCandidate);
    }

    @Override
    public void parseFiles(Path projectRoot, List<Path> files, ProjectGraph graph) {
        files.forEach(file -> parseMuleFile(file, projectRoot, graph));
    }

    private boolean isMuleXmlCandidate(Path file) {
        String fileName = file.getFileName().toString();
        return fileName.endsWith(".xml") && !fileName.equals("pom.xml") && isMuleXml(file);
    }

    /**
     * Namespace sniff: read first 1KB and check for the MuleSoft namespace marker.
     */
    private boolean isMuleXml(Path file) {
        try (InputStream is = Files.newInputStream(file)) {
            byte[] buf = new byte[SNIFF_BYTES];
            int read = is.read(buf);
            if (read <= 0)
                return false;
            String head = new String(buf, 0, read, StandardCharsets.UTF_8);
            return head.contains(MULE_NS_MARKER);
        } catch (IOException e) {
            return false;
        }
    }

    private void parseMuleFile(Path xmlFile, Path projectRoot, ProjectGraph graph) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(xmlFile.toFile());

            String relPath = projectRoot.relativize(xmlFile).toString();

            Element root = doc.getDocumentElement();
            NodeList children = root.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child.getNodeType() != Node.ELEMENT_NODE)
                    continue;
                Element el = (Element) child;
                String localName = el.getLocalName();

                if ("flow".equals(localName)) {
                    parseFlow(el, relPath, graph);
                } else if ("sub-flow".equals(localName)) {
                    parseSubFlow(el, relPath, graph);
                } else if (isConnectorElement(el)) {
                    parseConnector(el, relPath, graph);
                }
            }
        } catch (Exception e) {
            // Skip unparseable XML silently
        }
    }

    /**
     * A connector is a top-level element whose local name ends with "-config", is exactly "config" (namespaced
     * connector, e.g. {@code db:config}), or is exactly "connector".
     */
    private boolean isConnectorElement(Element el) {
        String localName = el.getLocalName();
        return localName != null
                && (localName.endsWith("-config")
                        || "config".equals(localName)
                        || "connector".equals(localName));
    }

    private void parseConnector(Element el, String relPath, ProjectGraph graph) {
        String name = el.getAttribute("name");
        if (name == null || name.isEmpty())
            return;

        String nodeId = "mule-connector:" + name;
        Map<String, String> props = new HashMap<>();
        props.put("file", relPath);
        props.put("element", el.getLocalName());
        graph.addNode(new GraphNode(nodeId, NodeType.MULE_CONNECTOR, props));
    }

    private void parseFlow(Element flowEl, String relPath, ProjectGraph graph) {
        String name = flowEl.getAttribute("name");
        if (name == null || name.isEmpty())
            return;

        String flowId = "mule-flow:" + name;
        Map<String, String> props = new HashMap<>();
        props.put("file", relPath);
        graph.addNode(new GraphNode(flowId, NodeType.MULE_FLOW, props));

        parseFlowChildren(flowEl, flowId, graph, relPath, 0);
    }

    private void parseSubFlow(Element subFlowEl, String relPath, ProjectGraph graph) {
        String name = subFlowEl.getAttribute("name");
        if (name == null || name.isEmpty())
            return;

        String flowId = "mule-subflow:" + name;
        Map<String, String> props = new HashMap<>();
        props.put("file", relPath);
        graph.addNode(new GraphNode(flowId, NodeType.MULE_SUB_FLOW, props));

        parseFlowChildren(subFlowEl, flowId, graph, relPath, 0);
    }

    /**
     * Recursively walk flow children to extract endpoints, transforms, processors, error handlers, flow-refs, etc.
     */
    private int parseFlowChildren(
            Element parent, String flowId,
            ProjectGraph graph, String relPath, int order) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE)
                continue;
            Element el = (Element) child;
            String localName = el.getLocalName();

            if ("flow-ref".equals(localName)) {
                handleFlowRef(el, flowId, graph);
            } else if (isTransform(el)) {
                order = handleTransform(el, flowId, graph, relPath, order);
            } else if (ERROR_HANDLER_NAMES.contains(localName)) {
                order = handleErrorHandler(el, flowId, graph, relPath, order);
            } else if (isEndpoint(el)) {
                order = handleEndpoint(el, flowId, graph, relPath, order);
            } else if (PROCESSOR_NAMES.contains(localName)) {
                order = handleProcessor(el, flowId, graph, relPath, localName, order);
            } else if (RECURSE_ELEMENTS.contains(localName)) {
                // Recurse into structural wrappers
                order = parseFlowChildren(el, flowId, graph, relPath, order);
            } else {
                // Unknown element -- recurse to pick up nested content
                order = parseFlowChildren(el, flowId, graph, relPath, order);
            }
        }
        return order;
    }

    // ── Element classification ─────────────────────────────────────────

    private boolean isTransform(Element el) {
        String localName = el.getLocalName();
        return "transform".equals(localName) || "transform-message".equals(localName);
    }

    private boolean isEndpoint(Element el) {
        String localName = el.getLocalName();
        // Mule 4.x named endpoints
        if (MULE4_ENDPOINT_NAMES.contains(localName))
            return true;
        // Mule 3.x: any element ending with -endpoint
        return localName != null && localName.endsWith("-endpoint");
    }

    // ── Handlers ───────────────────────────────────────────────────────

    private void handleFlowRef(Element el, String flowId, ProjectGraph graph) {
        String refName = el.getAttribute("name");
        if (refName == null || refName.isEmpty())
            return;

        // Prefer sub-flow target, fall back to flow
        String targetId = graph.hasNode("mule-subflow:" + refName)
                ? "mule-subflow:" + refName
                : graph.hasNode("mule-flow:" + refName)
                        ? "mule-flow:" + refName
                : "mule-subflow:" + refName; // default to sub-flow convention

        graph.addEdge(new GraphEdge(
                flowId, targetId,
                EdgeType.MULE_CALLS_SUBFLOW, Map.of()));
    }

    private int handleEndpoint(
            Element el, String flowId,
            ProjectGraph graph, String relPath, int order) {
        String localName = el.getLocalName();
        String nodeId = flowId + ":endpoint:" + localName + ":" + order;

        Map<String, String> props = new HashMap<>();
        props.put("file", relPath);
        props.put("element", localName);

        graph.addNode(new GraphNode(nodeId, NodeType.MULE_ENDPOINT, props));
        graph.addEdge(new GraphEdge(
                flowId, nodeId,
                EdgeType.MULE_FLOW_CONTAINS, Map.of("order", String.valueOf(order))));

        // Connector reference: config-ref (Mule 4) or connector-ref (Mule 3)
        String configRef = el.getAttribute("config-ref");
        if (configRef == null || configRef.isEmpty()) {
            configRef = el.getAttribute("connector-ref");
        }
        if (configRef != null && !configRef.isEmpty()) {
            String connectorId = "mule-connector:" + configRef;
            graph.addEdge(new GraphEdge(
                    nodeId, connectorId,
                    EdgeType.MULE_USES_CONNECTOR, Map.of()));
        }

        return order + 1;
    }

    private int handleTransform(
            Element el, String flowId,
            ProjectGraph graph, String relPath, int order) {
        String nodeId = flowId + ":transform:" + order;

        Map<String, String> props = new HashMap<>();
        props.put("file", relPath);
        props.put("element", el.getLocalName());

        graph.addNode(new GraphNode(nodeId, NodeType.MULE_TRANSFORM, props));
        graph.addEdge(new GraphEdge(
                flowId, nodeId,
                EdgeType.MULE_FLOW_CONTAINS, Map.of("order", String.valueOf(order))));

        // Look for DWL resource reference in set-payload child
        findDwlResource(el, nodeId, graph);

        return order + 1;
    }

    /**
     * Scan child elements (e.g. ee:set-payload, dw:set-payload) for a {@code resource} attribute pointing to a .dwl
     * file.
     */
    private void findDwlResource(Element parent, String transformNodeId, ProjectGraph graph) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE)
                continue;
            Element childEl = (Element) child;
            String resource = childEl.getAttribute("resource");
            if (resource != null && !resource.isEmpty()) {
                // Strip classpath: prefix if present
                String dwlPath = resource.startsWith("classpath:")
                        ? resource.substring("classpath:".length())
                        : resource;
                String dwlNodeId = "dwl:" + dwlPath;
                graph.addNode(new GraphNode(
                        dwlNodeId, NodeType.DATAWEAVE_SCRIPT,
                        Map.of("path", dwlPath)));
                graph.addEdge(new GraphEdge(
                        transformNodeId, dwlNodeId,
                        EdgeType.MULE_REFERENCES_DWL, Map.of()));
            }
        }
    }

    private int handleErrorHandler(
            Element el, String flowId,
            ProjectGraph graph, String relPath, int order) {
        String nodeId = flowId + ":error-handler:" + order;

        Map<String, String> props = new HashMap<>();
        props.put("file", relPath);
        props.put("element", el.getLocalName());

        graph.addNode(new GraphNode(nodeId, NodeType.MULE_ERROR_HANDLER, props));
        graph.addEdge(new GraphEdge(
                flowId, nodeId,
                EdgeType.MULE_FLOW_CONTAINS, Map.of("order", String.valueOf(order))));

        return order + 1;
    }

    private int handleProcessor(
            Element el, String flowId,
            ProjectGraph graph, String relPath,
            String localName, int order) {
        String nodeId = flowId + ":processor:" + localName + ":" + order;

        Map<String, String> props = new HashMap<>();
        props.put("file", relPath);
        props.put("element", localName);

        graph.addNode(new GraphNode(nodeId, NodeType.MULE_PROCESSOR, props));
        graph.addEdge(new GraphEdge(
                flowId, nodeId,
                EdgeType.MULE_FLOW_CONTAINS, Map.of("order", String.valueOf(order))));

        return order + 1;
    }
}
