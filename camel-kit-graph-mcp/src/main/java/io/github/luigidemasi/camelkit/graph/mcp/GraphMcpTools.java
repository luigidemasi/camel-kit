package io.github.luigidemasi.camelkit.graph.mcp;

import io.github.luigidemasi.camelkit.graph.model.EdgeType;
import io.github.luigidemasi.camelkit.graph.model.GraphEdge;
import io.github.luigidemasi.camelkit.graph.model.GraphNode;
import io.github.luigidemasi.camelkit.graph.model.NodeType;
import io.github.luigidemasi.camelkit.graph.query.DeadCodeAnalyzer;
import io.github.luigidemasi.camelkit.graph.query.GraphQuery;
import io.github.luigidemasi.camelkit.graph.query.RouteFlowTracer;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.inject.Inject;

import java.util.*;
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
        NodeType nodeType = null;
        if (type != null) {
            try { nodeType = NodeType.valueOf(type); }
            catch (IllegalArgumentException e) {
                return "{\"error\":\"Invalid node type: " + escape(type) +
                    "\",\"validTypes\":[\"" + Arrays.stream(NodeType.values())
                    .map(Enum::name).collect(Collectors.joining("\",\"")) + "\"]}";
            }
        }
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
        if (nodeId == null || nodeId.isBlank()) {
            return "{\"error\":\"nodeId is required\"}";
        }
        int d = depth != null ? Math.min(depth, 3) : 1;
        String dir = direction != null ? direction : "both";
        EdgeType et = null;
        if (edgeType != null) {
            try { et = EdgeType.valueOf(edgeType); }
            catch (IllegalArgumentException e) {
                return "{\"error\":\"Invalid edge type: " + escape(edgeType) +
                    "\",\"validTypes\":[\"" + Arrays.stream(EdgeType.values())
                    .map(Enum::name).collect(Collectors.joining("\",\"")) + "\"]}";
            }
        }
        GraphQuery.NeighborResult result = service.getQuery().neighbors(nodeId, dir, et, d);
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
        if (fromId == null || fromId.isBlank() || toId == null || toId.isBlank()) {
            return "{\"error\":\"Both fromId and toId are required\"}";
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
        if (nodeId == null || nodeId.isBlank()) {
            return "{\"error\":\"nodeId is required\"}";
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
        for (var entry : stats.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":").append(entry.getValue());
            first = false;
        }
        sb.append("}}");
        return sb.toString();
    }

    @Tool(description = "Trace the complete message flow through a Camel route, following cross-route " +
            "direct:/seda: links. Returns the ordered path: from-endpoint -> processors -> to-endpoints. " +
            "The single most important tool for understanding Camel message flows. " +
            "Provide either routeId OR startEndpoint, not both.")
    public String graph_route_flow(
            @ToolArg(description = "Route node ID, e.g., 'route:processOrders'") String routeId,
            @ToolArg(description = "Starting endpoint ID, e.g., 'endpoint:kafka:orders'") String startEndpoint) {
        if (!service.isAvailable()) {
            return "{\"available\":false,\"message\":\"No project graph found. Run /camel-init first.\"}";
        }
        List<RouteFlowTracer.FlowStep> steps;
        if (routeId != null) {
            steps = service.getFlowTracer().trace(routeId);
        } else if (startEndpoint != null) {
            steps = service.getFlowTracer().traceFromEndpoint(startEndpoint);
        } else {
            return "{\"error\":\"Provide either routeId or startEndpoint\"}";
        }
        String stepsJson = steps.stream()
                .map(s -> String.format("{\"nodeId\":\"%s\",\"type\":\"%s\",\"label\":\"%s\",\"depth\":%d}",
                        escape(s.nodeId()), escape(s.type()), escape(s.label()), s.depth()))
                .collect(Collectors.joining(","));
        return "{\"found\":true,\"steps\":[" + stepsJson + "]}";
    }

    @Tool(description = "Find all nodes transitively affected by a change to the given node. " +
            "Critical for migration — tells you 'if I change this, what else must change?' " +
            "Results grouped by node type.")
    public String graph_impact(
            @ToolArg(description = "Node ID to analyze impact for") String nodeId,
            @ToolArg(description = "Direction: 'upstream' (what feeds in), 'downstream' (what's affected), or 'both'") String direction) {
        if (!service.isAvailable()) {
            return "{\"available\":false,\"message\":\"No project graph found. Run /camel-init first.\"}";
        }
        if (nodeId == null || nodeId.isBlank()) {
            return "{\"error\":\"nodeId is required\"}";
        }
        String dir = direction != null ? direction : "both";
        List<GraphNode> impacted;
        if ("both".equals(dir)) {
            // GraphQuery.impact only supports "downstream" or "upstream", not "both"
            // Call twice and merge results
            List<GraphNode> downstream = service.getQuery().impact(nodeId, "downstream");
            List<GraphNode> upstream = service.getQuery().impact(nodeId, "upstream");
            Set<String> seen = new LinkedHashSet<>();
            impacted = new ArrayList<>();
            for (GraphNode n : downstream) {
                if (seen.add(n.id())) impacted.add(n);
            }
            for (GraphNode n : upstream) {
                if (seen.add(n.id())) impacted.add(n);
            }
        } else {
            impacted = service.getQuery().impact(nodeId, dir);
        }
        // Group by type
        Map<String, List<GraphNode>> grouped = impacted.stream()
                .collect(Collectors.groupingBy(n -> n.type().name()));
        StringBuilder sb = new StringBuilder("{\"found\":true,\"total\":" + impacted.size() + ",\"byType\":{");
        boolean first = true;
        for (var entry : grouped.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":[");
            sb.append(entry.getValue().stream().map(this::nodeToJson).collect(Collectors.joining(",")));
            sb.append("]");
            first = false;
        }
        sb.append("}}");
        return sb.toString();
    }

    @Tool(description = "Get the route-to-route connection map showing which Camel routes link to which " +
            "via direct:/seda: endpoints. Bird's-eye view of the messaging architecture. " +
            "First thing to call when understanding a Camel project.")
    public String graph_route_topology() {
        if (!service.isAvailable()) {
            return "{\"available\":false,\"message\":\"No project graph found. Run /camel-init first.\"}";
        }
        var topo = service.getTopology().build();
        StringBuilder sb = new StringBuilder("{\"routes\":{");
        boolean first = true;
        for (var entry : topo.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(escape(entry.getKey())).append("\":[");
            sb.append(entry.getValue().stream()
                    .map(c -> String.format("{\"target\":\"%s\",\"scheme\":\"%s\",\"uri\":\"%s\"}",
                            escape(c.targetRouteId()), escape(c.scheme()), escape(c.endpointUri())))
                    .collect(Collectors.joining(",")));
            sb.append("]");
            first = false;
        }
        sb.append("}}");
        return sb.toString();
    }

    @Tool(description = "Detect dead code in the project: unused Camel Maven dependencies, " +
            "orphaned routes (internal routes nobody calls), and configuration properties " +
            "that don't configure any endpoint. Use for project hygiene and migration cleanup.")
    public String graph_dead_code() {
        if (!service.isAvailable()) {
            return "{\"available\":false,\"message\":\"No project graph found. Run /camel-init first.\"}";
        }
        DeadCodeAnalyzer analyzer = new DeadCodeAnalyzer(service.getGraph());
        DeadCodeAnalyzer.DeadCodeResult result = analyzer.analyze();

        String artifacts = result.unusedArtifacts().stream()
                .map(this::nodeToJson).collect(Collectors.joining(","));
        String routes = result.orphanedRoutes().stream()
                .map(this::nodeToJson).collect(Collectors.joining(","));
        String properties = result.unusedProperties().stream()
                .map(this::nodeToJson).collect(Collectors.joining(","));

        return "{\"available\":true," +
                "\"unusedArtifacts\":[" + artifacts + "]," +
                "\"orphanedRoutes\":[" + routes + "]," +
                "\"unusedProperties\":[" + properties + "]," +
                "\"summary\":{" +
                "\"unusedArtifactCount\":" + result.unusedArtifacts().size() + "," +
                "\"orphanedRouteCount\":" + result.orphanedRoutes().size() + "," +
                "\"unusedPropertyCount\":" + result.unusedProperties().size() + "}}";
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
