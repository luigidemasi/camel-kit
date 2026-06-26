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

public class XmlRouteParser implements GraphParser {

    private static final int SNIFF_BYTES = 1024;

    private static final Set<String> EIP_ELEMENTS = Set.of(
            "filter", "split", "aggregate", "marshal", "unmarshal",
            "transform", "bean", "process", "enrich", "log", "groovy", "script");

    @Override
    public void parse(Path projectRoot, ProjectGraph graph) {
        parseFiles(projectRoot, scannedFilePaths(projectRoot, GraphParser.projectFiles(projectRoot)), graph);
    }

    @Override
    public List<String> scannedFiles(Path projectRoot) {
        return GraphParser.findFiles(projectRoot, this::isCamelXmlCandidate);
    }

    @Override
    public List<Path> scannedFilePaths(Path projectRoot, List<Path> projectFiles) {
        return GraphParser.findFilePaths(projectRoot, projectFiles, this::isCamelXmlCandidate);
    }

    @Override
    public void parseFiles(Path projectRoot, List<Path> files, ProjectGraph graph) {
        files.forEach(file -> parseXmlFile(file, projectRoot, graph));
    }

    private boolean isCamelXmlCandidate(Path file) {
        String fileName = file.getFileName().toString();
        if (!fileName.endsWith(".xml") || fileName.equals("pom.xml")) {
            return false;
        }
        try {
            String head = readHead(file);
            return !head.contains("mulesoft.org/schema/mule") && !BizTalkParser.isBizTalkXml(head);
        } catch (IOException e) {
            return false;
        }
    }

    private void parseXmlFile(Path xmlFile, Path projectRoot, ProjectGraph graph) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(xmlFile.toFile());

            // Find all route elements
            NodeList routes = doc.getElementsByTagNameNS("*", "route");
            for (int i = 0; i < routes.getLength(); i++) {
                Element route = (Element) routes.item(i);
                parseRoute(route, projectRoot, xmlFile, graph);
            }
        } catch (Exception e) {
            // Skip unparseable XML silently
        }
    }

    private String readHead(Path file) throws IOException {
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[SNIFF_BYTES];
            int read = input.read(buffer);
            if (read <= 0) {
                return "";
            }
            return new String(buffer, 0, read, StandardCharsets.UTF_8);
        }
    }

    private void parseRoute(Element routeElement, Path projectRoot, Path xmlFile, ProjectGraph graph) {
        String routeId = routeElement.getAttribute("id");
        if (routeId == null || routeId.isEmpty()) {
            return; // Skip routes without id
        }

        String routeNodeId = "route:" + routeId;
        Map<String, String> routeProps = new HashMap<>();
        routeProps.put("file", projectRoot.relativize(xmlFile).toString());

        // Parse from endpoint
        NodeList fromElements = routeElement.getElementsByTagNameNS("*", "from");
        if (fromElements.getLength() > 0) {
            Element from = (Element) fromElements.item(0);
            String fromUri = from.getAttribute("uri");
            if (fromUri != null && !fromUri.isEmpty()) {
                routeProps.put("fromUri", fromUri);

                // Create endpoint node and ROUTES_FROM edge
                String endpointId = "endpoint:" + fromUri;
                graph.addNode(new GraphNode(
                        endpointId, NodeType.CAMEL_ENDPOINT,
                        Map.of("uri", fromUri)));
                graph.addEdge(new GraphEdge(
                        routeNodeId, endpointId,
                        EdgeType.ROUTES_FROM, Map.of()));
            }
        }

        // Create route node
        graph.addNode(new GraphNode(routeNodeId, NodeType.CAMEL_ROUTE, routeProps));

        // Parse to endpoints and EIP processors
        parseChildElements(routeElement, routeNodeId, graph, 0);
    }

    private int parseChildElements(Element parent, String routeNodeId, ProjectGraph graph, int order) {
        NodeList children = parent.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element element = (Element) child;
            String tagName = element.getLocalName();

            if ("to".equals(tagName)) {
                String uri = element.getAttribute("uri");
                if (uri != null && !uri.isEmpty()) {
                    // Create endpoint node and ROUTES_TO edge
                    String endpointId = "endpoint:" + uri;
                    graph.addNode(new GraphNode(
                            endpointId, NodeType.CAMEL_ENDPOINT,
                            Map.of("uri", uri)));
                    graph.addEdge(new GraphEdge(
                            routeNodeId, endpointId,
                            EdgeType.ROUTES_TO, Map.of()));
                }
            } else if (EIP_ELEMENTS.contains(tagName)) {
                // Create processor node
                String processorId = routeNodeId + ":processor:" + tagName + ":" + order;
                graph.addNode(new GraphNode(
                        processorId, NodeType.CAMEL_PROCESSOR,
                        Map.of("type", tagName)));
                graph.addEdge(new GraphEdge(
                        routeNodeId, processorId,
                        EdgeType.PROCESSES, Map.of("order", String.valueOf(order))));
                order++;

                // Recursively parse children of EIP elements
                order = parseChildElements(element, routeNodeId, graph, order);
            } else {
                // Recursively parse other elements
                order = parseChildElements(element, routeNodeId, graph, order);
            }
        }

        return order;
    }
}
