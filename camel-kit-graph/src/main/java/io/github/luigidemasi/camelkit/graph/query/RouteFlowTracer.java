package io.github.luigidemasi.camelkit.graph.query;

import io.github.luigidemasi.camelkit.graph.ProjectGraph;
import io.github.luigidemasi.camelkit.graph.model.*;

import java.util.*;

/**
 * Traces Camel message flows end-to-end through the project graph.
 *
 * Starting from a route or endpoint, walks the FROM endpoint, ordered PROCESSES
 * processors, and TO endpoints, following direct:/seda: cross-route links
 * recursively with cycle detection.
 */
public class RouteFlowTracer {

    private static final Set<String> LINKABLE_SCHEMES = Set.of("direct", "seda");

    private final ProjectGraph graph;

    public record FlowStep(String nodeId, String type, String label, int depth) {}

    public RouteFlowTracer(ProjectGraph graph) {
        this.graph = graph;
    }

    /**
     * Traces the message flow starting from the given route node ID.
     *
     * @param routeId the route node ID (e.g. "route:processOrders")
     * @return ordered list of flow steps
     */
    public List<FlowStep> trace(String routeId) {
        List<FlowStep> flow = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        traceRoute(routeId, 0, flow, visited);
        return flow;
    }

    /**
     * Traces the message flow starting from the given endpoint node ID.
     * Finds all routes that consume from this endpoint and traces them.
     *
     * @param endpointId the endpoint node ID (e.g. "endpoint:kafka:orders")
     * @return ordered list of flow steps
     */
    public List<FlowStep> traceFromEndpoint(String endpointId) {
        List<FlowStep> flow = new ArrayList<>();
        Set<String> visited = new HashSet<>();

        GraphNode endpoint = graph.getNode(endpointId);
        if (endpoint != null) {
            flow.add(new FlowStep(endpointId, "ENDPOINT", endpoint.properties().get("uri"), 0));
        }

        // Find routes that consume from this endpoint
        for (GraphEdge edge : graph.getIncomingEdges(endpointId)) {
            if (edge.type() == EdgeType.ROUTES_FROM) {
                traceRoute(edge.from(), 1, flow, visited);
            }
        }

        return flow;
    }

    private void traceRoute(String routeId, int depth, List<FlowStep> flow, Set<String> visited) {
        if (!visited.add(routeId)) return;

        GraphNode route = graph.getNode(routeId);
        if (route == null || route.type() != NodeType.CAMEL_ROUTE) return;

        // 1. Add from endpoint
        for (GraphEdge edge : graph.getOutgoingEdges(routeId)) {
            if (edge.type() == EdgeType.ROUTES_FROM) {
                GraphNode fromEndpoint = graph.getNode(edge.to());
                if (fromEndpoint != null) {
                    flow.add(new FlowStep(edge.to(), "FROM",
                        fromEndpoint.properties().get("uri"), depth));
                }
            }
        }

        // 2. Add processors in order
        List<GraphEdge> processorEdges = graph.getOutgoingEdges(routeId).stream()
            .filter(e -> e.type() == EdgeType.PROCESSES)
            .sorted(Comparator.comparing(e -> {
                String order = e.properties().get("order");
                return order != null ? Integer.parseInt(order) : 0;
            }))
            .toList();

        for (GraphEdge edge : processorEdges) {
            GraphNode processor = graph.getNode(edge.to());
            if (processor != null) {
                flow.add(new FlowStep(edge.to(), "PROCESSOR",
                    processor.properties().get("type"), depth));
            }
        }

        // 3. Add to endpoints, and follow direct/seda links
        for (GraphEdge edge : graph.getOutgoingEdges(routeId)) {
            if (edge.type() == EdgeType.ROUTES_TO) {
                GraphNode toEndpoint = graph.getNode(edge.to());
                if (toEndpoint != null) {
                    String uri = toEndpoint.properties().get("uri");
                    flow.add(new FlowStep(edge.to(), "TO", uri, depth));

                    // Follow direct/seda links to consuming routes
                    String scheme = extractScheme(uri);
                    if (scheme != null && LINKABLE_SCHEMES.contains(scheme)) {
                        for (GraphEdge consumerEdge : graph.getIncomingEdges(edge.to())) {
                            if (consumerEdge.type() == EdgeType.ROUTES_FROM) {
                                traceRoute(consumerEdge.from(), depth + 1, flow, visited);
                            }
                        }
                    }
                }
            }
        }
    }

    private String extractScheme(String uri) {
        if (uri != null && uri.contains(":")) {
            return uri.substring(0, uri.indexOf(":"));
        }
        return null;
    }
}
