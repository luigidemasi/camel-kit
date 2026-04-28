package io.github.luigidemasi.camelkit.graph;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.luigidemasi.camelkit.graph.model.GraphEdge;
import io.github.luigidemasi.camelkit.graph.model.GraphNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class GraphVisualizer {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String BASE_PATH = "graph-visualizer/";

    private static final Map<String, String> LIBRARY_FILES = Map.of(
            "cytoscape", "cytoscape.min.js",
            "d3", "d3.v7.min.js",
            "vis-network", "vis-network.min.js",
            "antv-g6", "g6.min.js");

    private static final Map<String, String> INIT_FILES = Map.of(
            "cytoscape", "init-cytoscape.js",
            "d3", "init-d3.js",
            "vis-network", "init-vis-network.js",
            "antv-g6", "init-antv-g6.js");

    private GraphVisualizer() {}

    public static String generate(ProjectGraph graph) {
        return generate(graph, "cytoscape");
    }

    public static String generate(ProjectGraph graph, String library) {
        String lib = LIBRARY_FILES.containsKey(library) ? library : "cytoscape";
        String css = readResource(BASE_PATH + "base.css");
        String libraryJs = readResource(BASE_PATH + LIBRARY_FILES.get(lib));
        String initJs = readResource(BASE_PATH + INIT_FILES.get(lib));
        String data = serializeForVisualization(graph);

        return "<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n<meta charset=\"UTF-8\">\n"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n"
                + "<title>Camel-Kit Project Graph</title>\n<style>\n" + css + "\n</style>\n</head>\n<body>\n"
                + "<div id=\"controls\">\n<h3>Camel-Kit Project Graph</h3>\n"
                + "<input type=\"text\" id=\"search\" placeholder=\"Search nodes...\">\n"
                + "<div id=\"filters\"></div>\n<div id=\"stats-line\"></div>\n</div>\n"
                + "<div id=\"info\"></div>\n"
                + "<div id=\"graph-container\"></div>\n"
                + "<script>\n" + libraryJs + "\n</script>\n"
                + "<script>\nvar graphData = " + data + ";\n" + initJs + "\n</script>\n"
                + "</body>\n</html>";
    }

    private static String serializeForVisualization(ProjectGraph graph) {
        ObjectNode root = MAPPER.createObjectNode();
        ArrayNode nodes = root.putArray("nodes");
        for (GraphNode node : graph.getNodes().values()) {
            ObjectNode n = nodes.addObject();
            n.put("id", node.id());
            n.put("type", node.type().name());
            ObjectNode props = n.putObject("properties");
            node.properties().forEach(props::put);
        }
        ArrayNode edges = root.putArray("edges");
        var nodeIds = graph.getNodes().keySet();
        for (GraphEdge edge : graph.getEdges()) {
            if (!nodeIds.contains(edge.from()) || !nodeIds.contains(edge.to())) continue;
            ObjectNode e = edges.addObject();
            e.put("source", edge.from());
            e.put("target", edge.to());
            e.put("type", edge.type().name());
        }
        try { return MAPPER.writeValueAsString(root); }
        catch (Exception e) { return "{\"nodes\":[],\"edges\":[]}"; }
    }

    private static String readResource(String path) {
        try (InputStream is = GraphVisualizer.class.getClassLoader().getResourceAsStream(path)) {
            if (is == null) throw new IOException("Resource not found: " + path);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) { throw new RuntimeException("Failed to read resource: " + path, e); }
    }
}
