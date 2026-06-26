package io.github.luigidemasi.camelkit.graph.parser;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

import io.github.luigidemasi.camelkit.graph.ProjectGraph;
import io.github.luigidemasi.camelkit.graph.model.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

public class YamlRouteParser implements GraphParser {

    private static final Set<String> EIP_ELEMENTS = Set.of(
            "filter", "split", "aggregate", "marshal", "unmarshal",
            "transform", "bean", "process", "enrich", "log", "groovy", "script");

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    @Override
    public void parse(Path projectRoot, ProjectGraph graph) {
        try {
            Files.walkFileTree(projectRoot, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    String fileName = file.getFileName().toString();
                    if ((fileName.endsWith(".yaml") || fileName.endsWith(".yml"))
                            && !fileName.startsWith("application")) {
                        parseYamlFile(file, projectRoot, graph);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new RuntimeException("Failed to walk project for YAML files", e);
        }
    }

    @Override
    public List<String> scannedFiles(Path projectRoot) {
        return GraphParser.findFiles(projectRoot, this::isRouteYaml);
    }

    private boolean isRouteYaml(Path file) {
        String fileName = file.getFileName().toString();
        return (fileName.endsWith(".yaml") || fileName.endsWith(".yml"))
                && !fileName.startsWith("application");
    }

    private void parseYamlFile(Path yamlFile, Path projectRoot, ProjectGraph graph) {
        try {
            JsonNode root = yamlMapper.readTree(yamlFile.toFile());

            // Handle array of routes or single route
            if (root.isArray()) {
                for (JsonNode element : root) {
                    if (element.has("route")) {
                        parseRoute(element.get("route"), projectRoot, yamlFile, graph);
                    }
                }
            } else if (root.has("route")) {
                parseRoute(root.get("route"), projectRoot, yamlFile, graph);
            }
        } catch (Exception e) {
            // Skip unparseable YAML silently
        }
    }

    private void parseRoute(JsonNode routeNode, Path projectRoot, Path yamlFile, ProjectGraph graph) {
        if (!routeNode.has("id")) {
            return; // Skip routes without id
        }

        String routeId = routeNode.get("id").asText();
        String routeNodeId = "route:" + routeId;
        Map<String, String> routeProps = new HashMap<>();
        routeProps.put("file", projectRoot.relativize(yamlFile).toString());

        // Parse from endpoint
        if (routeNode.has("from")) {
            JsonNode fromNode = routeNode.get("from");
            String fromUri = extractUri(fromNode);

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

            // Create route node
            graph.addNode(new GraphNode(routeNodeId, NodeType.CAMEL_ROUTE, routeProps));

            // Parse steps inside from
            if (fromNode.has("steps")) {
                parseSteps(fromNode.get("steps"), routeNodeId, graph, 0);
            }
        } else {
            // Create route node even without from
            graph.addNode(new GraphNode(routeNodeId, NodeType.CAMEL_ROUTE, routeProps));
        }

        // Parse steps at route level (alternative structure)
        if (routeNode.has("steps")) {
            parseSteps(routeNode.get("steps"), routeNodeId, graph, 0);
        }
    }

    private String extractUri(JsonNode node) {
        if (node.isTextual()) {
            return node.asText();
        } else if (node.isObject() && node.has("uri")) {
            return node.get("uri").asText();
        }
        return null;
    }

    private int parseSteps(JsonNode stepsNode, String routeNodeId, ProjectGraph graph, int order) {
        if (!stepsNode.isArray()) {
            return order;
        }

        for (JsonNode step : stepsNode) {
            order = parseStep(step, routeNodeId, graph, order);
        }

        return order;
    }

    private int parseStep(JsonNode stepNode, String routeNodeId, ProjectGraph graph, int order) {
        // Iterate over fields in the step object
        Iterator<Map.Entry<String, JsonNode>> fields = stepNode.fields();

        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String stepType = field.getKey();
            JsonNode stepValue = field.getValue();

            if ("to".equals(stepType)) {
                // Handle to endpoint
                String uri = extractUri(stepValue);
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
            } else if (EIP_ELEMENTS.contains(stepType)) {
                // Handle EIP processor
                String processorId = routeNodeId + ":processor:" + stepType + ":" + order;
                graph.addNode(new GraphNode(
                        processorId, NodeType.CAMEL_PROCESSOR,
                        Map.of("type", stepType)));
                graph.addEdge(new GraphEdge(
                        routeNodeId, processorId,
                        EdgeType.PROCESSES, Map.of("order", String.valueOf(order))));
                order++;

                // Recursively parse nested steps
                if (stepValue.has("steps")) {
                    order = parseSteps(stepValue.get("steps"), routeNodeId, graph, order);
                }
            }
        }

        return order;
    }
}
