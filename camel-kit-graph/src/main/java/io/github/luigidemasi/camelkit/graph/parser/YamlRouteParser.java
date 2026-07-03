package io.github.luigidemasi.camelkit.graph.parser;

import java.nio.file.*;
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
    private final List<String> warnings = new ArrayList<>();

    @Override
    public void parse(Path projectRoot, ProjectGraph graph) {
        parseFiles(projectRoot, scannedFilePaths(projectRoot, GraphParser.projectFiles(projectRoot)), graph);
    }

    @Override
    public List<String> scannedFiles(Path projectRoot) {
        return GraphParser.findFiles(projectRoot, this::isRouteYaml);
    }

    @Override
    public List<Path> scannedFilePaths(Path projectRoot, List<Path> projectFiles) {
        return GraphParser.findFilePaths(projectRoot, projectFiles, this::isRouteYaml);
    }

    @Override
    public void parseFiles(Path projectRoot, List<Path> files, ProjectGraph graph) {
        files.forEach(file -> parseYamlFile(file, projectRoot, graph));
    }

    @Override
    public List<String> warnings() {
        return List.copyOf(warnings);
    }

    @Override
    public void resetWarnings() {
        warnings.clear();
    }

    private boolean isRouteYaml(Path file) {
        String fileName = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return (fileName.endsWith(".yaml") || fileName.endsWith(".yml"))
                && !fileName.startsWith("application");
    }

    private void parseYamlFile(Path yamlFile, Path projectRoot, ProjectGraph graph) {
        try {
            JsonNode root = yamlMapper.readTree(yamlFile.toFile());

            // Handle array of routes or single route
            if (root.isArray()) {
                int routeIndex = 0;
                for (JsonNode element : root) {
                    if (element.has("route")) {
                        parseRoute(element.get("route"), projectRoot, yamlFile, graph, routeIndex++);
                    } else if (element.has("from")) {
                        parseRoute(element, projectRoot, yamlFile, graph, routeIndex++);
                    }
                }
            } else if (root.has("route")) {
                parseRoute(root.get("route"), projectRoot, yamlFile, graph, 0);
            } else if (root.has("from")) {
                parseRoute(root, projectRoot, yamlFile, graph, 0);
            }
        } catch (Exception e) {
            warnings.add("Could not parse YAML route file " + GraphParser.relativeFileName(projectRoot, yamlFile)
                         + ": " + e.getMessage());
        }
    }

    private void parseRoute(JsonNode routeNode, Path projectRoot, Path yamlFile, ProjectGraph graph, int routeIndex) {
        String routeId = routeNode.has("id")
                ? routeNode.get("id").asText()
                : syntheticRouteId(projectRoot, yamlFile, routeIndex);
        String routeNodeId = "route:" + routeId;
        Map<String, String> routeProps = new HashMap<>();
        routeProps.put("file", projectRoot.relativize(yamlFile).toString());
        routeProps.put("routeId", routeId);

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
                        endpointProperties(fromUri)));
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
            warnings.add("Route " + routeId + " in " + GraphParser.relativeFileName(projectRoot, yamlFile)
                         + " uses route-level steps; Camel YAML expects steps under from.");
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

            if ("to".equals(stepType) || "toD".equals(stepType)) {
                // Handle to endpoint
                String uri = extractUri(stepValue);
                if (uri != null && !uri.isEmpty()) {
                    // Create endpoint node and ROUTES_TO edge
                    String endpointId = "endpoint:" + uri;
                    graph.addNode(new GraphNode(
                            endpointId, NodeType.CAMEL_ENDPOINT,
                            endpointProperties(uri)));
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

    private String syntheticRouteId(Path projectRoot, Path yamlFile, int routeIndex) {
        String relative = GraphParser.relativeFileName(projectRoot, yamlFile);
        return relative.replaceAll("[^A-Za-z0-9_.-]", "_") + "#route-" + (routeIndex + 1);
    }

    private Map<String, String> endpointProperties(String uri) {
        int colon = uri.indexOf(':');
        if (colon > 0) {
            return Map.of("uri", uri, "scheme", uri.substring(0, colon));
        }
        return Map.of("uri", uri);
    }
}
