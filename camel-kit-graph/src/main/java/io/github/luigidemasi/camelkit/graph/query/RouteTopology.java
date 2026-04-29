package io.github.luigidemasi.camelkit.graph.query;

import java.util.*;

import io.github.luigidemasi.camelkit.graph.ProjectGraph;
import io.github.luigidemasi.camelkit.graph.model.*;

/**
 * Builds a bird's-eye route-to-route connection map showing which routes connect to which via what endpoint scheme
 * (e.g. direct, seda).
 */
public class RouteTopology {

    private static final String ROUTE_ID_PREFIX = "route:";

    private final ProjectGraph graph;

    public record RouteConnection(String targetRouteId, String scheme, String endpointUri) {
    }

    public RouteTopology(ProjectGraph graph) {
        this.graph = graph;
    }

    /**
     * Builds a topology map from route IDs to their outgoing connections.
     *
     * @return map of routeId to list of connections to other routes
     */
    public Map<String, List<RouteConnection>> build() {
        Map<String, List<RouteConnection>> topology = new LinkedHashMap<>();

        // Build a map of endpoint node ID -> consuming route node IDs
        Map<String, List<String>> endpointConsumers = new HashMap<>();
        for (GraphNode route : graph.findByType(NodeType.CAMEL_ROUTE)) {
            for (GraphEdge edge : graph.getOutgoingEdges(route.id())) {
                if (edge.type() == EdgeType.ROUTES_FROM) {
                    GraphNode endpoint = graph.getNode(edge.to());
                    if (endpoint != null) {
                        endpointConsumers
                                .computeIfAbsent(edge.to(), k -> new ArrayList<>())
                                .add(route.id());
                    }
                }
            }
        }

        // For each route, find where it sends messages and map to consuming routes
        for (GraphNode route : graph.findByType(NodeType.CAMEL_ROUTE)) {
            String routeId = extractRouteId(route);
            List<RouteConnection> connections = new ArrayList<>();

            for (GraphEdge edge : graph.getOutgoingEdges(route.id())) {
                if (edge.type() == EdgeType.ROUTES_TO) {
                    GraphNode endpoint = graph.getNode(edge.to());
                    if (endpoint != null) {
                        String uri = endpoint.properties().get("uri");
                        String scheme = extractScheme(uri);

                        // Find consuming routes for this endpoint
                        List<String> consumers = endpointConsumers.getOrDefault(edge.to(), List.of());
                        for (String consumerRouteNodeId : consumers) {
                            GraphNode consumerRoute = graph.getNode(consumerRouteNodeId);
                            if (consumerRoute != null) {
                                String targetRouteId = extractRouteId(consumerRoute);
                                connections.add(new RouteConnection(targetRouteId, scheme, uri));
                            }
                        }
                    }
                }
            }

            if (!connections.isEmpty()) {
                topology.put(routeId, connections);
            }
        }

        return topology;
    }

    /**
     * Extracts the route ID from a route node. Prefers the "routeId" property, falls back to extracting from the node
     * ID (stripping the "route:" prefix).
     */
    private String extractRouteId(GraphNode route) {
        String routeId = route.properties().get("routeId");
        if (routeId != null) {
            return routeId;
        }
        // Fallback: extract from node ID
        String id = route.id();
        if (id.startsWith(ROUTE_ID_PREFIX)) {
            return id.substring(ROUTE_ID_PREFIX.length());
        }
        return id;
    }

    private String extractScheme(String uri) {
        if (uri != null && uri.contains(":")) {
            return uri.substring(0, uri.indexOf(":"));
        }
        return null;
    }
}
