package io.github.luigidemasi.camelkit.graph.parser;

import io.github.luigidemasi.camelkit.graph.ProjectGraph;
import io.github.luigidemasi.camelkit.graph.model.*;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

public class XmlRouteParser implements GraphParser {

    private static final Set<String> EIP_ELEMENTS = Set.of(
        "filter", "split", "aggregate", "marshal", "unmarshal",
        "transform", "bean", "process", "enrich", "log", "groovy", "script"
    );

    @Override
    public void parse(Path projectRoot, ProjectGraph graph) {
        try {
            Files.walkFileTree(projectRoot, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (file.getFileName().toString().endsWith(".xml")
                        && !file.getFileName().toString().equals("pom.xml")) {
                        parseXmlFile(file, projectRoot, graph);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new RuntimeException("Failed to walk project for XML files", e);
        }
    }

    private void parseXmlFile(Path xmlFile, Path projectRoot, ProjectGraph graph) {
        // Skip MuleSoft XML files (handled by MuleXmlFlowParser)
        try {
            String content = Files.readString(xmlFile);
            String head = content.substring(0, Math.min(1024, content.length()));
            if (head.contains("mulesoft.org/schema/mule")) {
                return;
            }
        } catch (IOException e) {
            return;
        }

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
                graph.addNode(new GraphNode(endpointId, NodeType.CAMEL_ENDPOINT,
                    Map.of("uri", fromUri)));
                graph.addEdge(new GraphEdge(routeNodeId, endpointId,
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
                    graph.addNode(new GraphNode(endpointId, NodeType.CAMEL_ENDPOINT,
                        Map.of("uri", uri)));
                    graph.addEdge(new GraphEdge(routeNodeId, endpointId,
                        EdgeType.ROUTES_TO, Map.of()));
                }
            } else if (EIP_ELEMENTS.contains(tagName)) {
                // Create processor node
                String processorId = routeNodeId + ":processor:" + tagName + ":" + order;
                graph.addNode(new GraphNode(processorId, NodeType.CAMEL_PROCESSOR,
                    Map.of("type", tagName)));
                graph.addEdge(new GraphEdge(routeNodeId, processorId,
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
