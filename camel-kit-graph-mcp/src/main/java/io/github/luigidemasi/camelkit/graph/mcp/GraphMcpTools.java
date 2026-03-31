package io.github.luigidemasi.camelkit.graph.mcp;

import io.github.luigidemasi.camelkit.graph.model.EdgeType;
import io.github.luigidemasi.camelkit.graph.model.GraphEdge;
import io.github.luigidemasi.camelkit.graph.model.GraphNode;
import io.github.luigidemasi.camelkit.graph.model.NodeType;
import io.github.luigidemasi.camelkit.graph.query.GraphQuery;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GraphMcpTools {

    @Inject
    GraphMcpService service;

    @Tool(description = "Find nodes in the project code graph by name pattern and/or type. " +
            "Returns matching nodes with their properties. " +
            "Use to find classes, routes, endpoints, or dependencies.")
    public String graph_find(
            @ToolArg(description = "Name or pattern to search for (regex pattern)") String query,
            @ToolArg(description = "Filter by node type: CLASS, METHOD, FIELD, CAMEL_ROUTE, CAMEL_ENDPOINT, " +
                    "CAMEL_PROCESSOR, MAVEN_ARTIFACT, CONFIG_PROPERTY, RESOURCE_FILE") String type) {
        if (!service.isAvailable()) {
            return "{\"available\":false,\"message\":\"No project graph found. Run /camel-init first.\"}";
        }
        NodeType nodeType = type != null ? NodeType.valueOf(type) : null;
        String pattern = query != null ? query : ".*";
        List<GraphNode> results = service.getQuery().find(pattern, nodeType);
        return formatNodes(results);
    }

    @Tool(description = "Get neighboring nodes connected to a given node in the project graph. " +
            "Use to explore what a class calls, what a route connects to, or what depends on a component.")
    public String graph_neighbors(
            @ToolArg(description = "Node ID, e.g., 'route:processOrders', 'class:com.example.Foo'") String nodeId,
            @ToolArg(description = "Direction: 'in' (incoming edges), 'out' (outgoing edges), or 'both'") String direction,
            @ToolArg(description = "Filter by edge type: EXTENDS, IMPLEMENTS, DECLARES, CALLS, USES_TYPE, " +
                    "ROUTES_FROM, ROUTES_TO, PROCESSES, LINKS_TO, DEPENDS_ON, USES_COMPONENT, CONFIGURES") String edgeType,
            @ToolArg(description = "Traversal depth (1-3, default 1)") Integer depth) {
        if (!service.isAvailable()) {
            return "{\"available\":false,\"message\":\"No project graph found. Run /camel-init first.\"}";
        }
        int d = depth != null ? Math.min(depth, 3) : 1;
        EdgeType et = edgeType != null ? EdgeType.valueOf(edgeType) : null;
        GraphQuery.NeighborResult result = service.getQuery().neighbors(nodeId, direction, et, d);
        return formatNeighborResult(result);
    }

    @Tool(description = "Find the shortest path between two nodes in the project graph. " +
            "Use to understand how two components are connected.")
    public String graph_path(
            @ToolArg(description = "Starting node ID") String fromId,
            @ToolArg(description = "Target node ID") String toId,
            @ToolArg(description = "Maximum search depth (default 5)") Integer maxDepth) {
        if (!service.isAvailable()) {
            return "{\"available\":false,\"message\":\"No project graph found. Run /camel-init first.\"}";
        }
        int max = maxDepth != null ? maxDepth : 5;
        List<GraphNode> path = service.getQuery().path(fromId, toId, max);
        if (path.isEmpty()) {
            return "{\"found\":false,\"message\":\"No path found between " + escape(fromId) + " and " + escape(toId) + "\"}";
        }
        return "{\"found\":true,\"length\":" + path.size() + ",\"path\":[" +
                path.stream().map(this::nodeToJson).collect(Collectors.joining(",")) + "]}";
    }

    @Tool(description = "Get the subgraph around a node within a given radius. " +
            "Returns all nodes and edges within the radius.")
    public String graph_subgraph(
            @ToolArg(description = "Center node ID") String nodeId,
            @ToolArg(description = "Radius (default 2, max 3)") Integer radius) {
        if (!service.isAvailable()) {
            return "{\"available\":false,\"message\":\"No project graph found. Run /camel-init first.\"}";
        }
        int r = radius != null ? Math.min(radius, 3) : 2;
        GraphQuery.SubgraphResult result = service.getQuery().subgraph(nodeId, r);
        return "{\"nodes\":[" +
                result.nodes().stream().map(this::nodeToJson).collect(Collectors.joining(",")) +
                "],\"edges\":[" +
                result.edges().stream().map(this::edgeToJson).collect(Collectors.joining(",")) +
                "]}";
    }

    @Tool(description = "Get project graph statistics: node and edge counts by type, route count, component summary. " +
            "Use for a quick project overview at the start of analysis.")
    public String graph_stats() {
        if (!service.isAvailable()) {
            return "{\"available\":false,\"message\":\"No project graph found. Run /camel-init first.\"}";
        }
        Map<String, Integer> stats = service.getQuery().stats();
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"available\":true,");
        sb.append("\"nodes\":").append(service.getGraph().nodeCount()).append(",");
        sb.append("\"edges\":").append(service.getGraph().edgeCount()).append(",");
        sb.append("\"nodesByType\":{");
        boolean first = true;
        for (NodeType nt : NodeType.values()) {
            int count = service.getGraph().findByType(nt).size();
            if (count > 0) {
                if (!first) sb.append(",");
                sb.append("\"").append(nt.name()).append("\":").append(count);
                first = false;
            }
        }
        sb.append("}}");
        return sb.toString();
    }

    // --- Formatting helpers (package-private for reuse by domain tools) ---

    String formatNodes(List<GraphNode> nodes) {
        if (nodes.isEmpty()) {
            return "{\"found\":true,\"total\":0,\"nodes\":[]}";
        }
        return "{\"found\":true,\"total\":" + nodes.size() + ",\"nodes\":[" +
                nodes.stream().map(this::nodeToJson).collect(Collectors.joining(",")) + "]}";
    }

    String formatNeighborResult(GraphQuery.NeighborResult result) {
        if (result.nodes().isEmpty()) {
            return "{\"found\":false,\"total\":0,\"nodes\":[],\"edges\":[]}";
        }
        return "{\"found\":true,\"total\":" + result.nodes().size() + ",\"nodes\":[" +
                result.nodes().stream().map(this::nodeToJson).collect(Collectors.joining(",")) +
                "],\"edges\":[" +
                result.edges().stream().map(this::edgeToJson).collect(Collectors.joining(",")) +
                "]}";
    }

    String nodeToJson(GraphNode node) {
        String props = node.properties().entrySet().stream()
                .map(e -> "\"" + escape(e.getKey()) + "\":\"" + escape(e.getValue()) + "\"")
                .collect(Collectors.joining(","));
        return "{\"id\":\"" + escape(node.id()) + "\",\"type\":\"" + node.type().name() +
                "\",\"properties\":{" + props + "}}";
    }

    String edgeToJson(GraphEdge edge) {
        String props = edge.properties().isEmpty() ? "" :
                ",\"properties\":{" + edge.properties().entrySet().stream()
                        .map(e -> "\"" + escape(e.getKey()) + "\":\"" + escape(e.getValue()) + "\"")
                        .collect(Collectors.joining(",")) + "}";
        return "{\"from\":\"" + escape(edge.from()) + "\",\"to\":\"" + escape(edge.to()) +
                "\",\"type\":\"" + edge.type().name() + "\"" + props + "}";
    }

    String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
    }
}
