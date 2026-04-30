package io.github.luigidemasi.camelkit.graph.parser;

import java.util.*;
import java.util.stream.Collectors;

import io.github.luigidemasi.camelkit.graph.ProjectGraph;
import io.github.luigidemasi.camelkit.graph.model.*;

/**
 * Post-processing pass that creates cross-reference edges in the project graph.
 *
 * Creates three types of cross-reference edges: 1. LINKS_TO - Connects producer routes to consumer routes via
 * direct/seda endpoints 2. USES_COMPONENT - Links endpoints to their Maven artifact dependencies 3. CONFIGURES - Links
 * config properties to the endpoints they configure
 */
public class CrossLinker {

    private static final Set<String> LINKABLE_SCHEMES = Set.of("direct", "seda");

    /**
     * Creates cross-reference edges in the graph.
     *
     * @param graph the project graph to process
     */
    public void link(ProjectGraph graph) {
        linkDirectAndSedaEndpoints(graph);
        linkComponentsToMavenArtifacts(graph);
        linkConfigToEndpoints(graph);
    }

    /**
     * Creates LINKS_TO edges connecting producer routes to consumer routes via direct: and seda: endpoints.
     */
    private void linkDirectAndSedaEndpoints(ProjectGraph graph) {
        // Find all direct/seda endpoints
        List<GraphNode> linkableEndpoints = graph.findByType(NodeType.CAMEL_ENDPOINT).stream()
                .filter(node -> {
                    String scheme = extractScheme(node);
                    return scheme != null && LINKABLE_SCHEMES.contains(scheme);
                })
                .toList();

        // For each linkable endpoint, connect producer routes to consumer routes
        for (GraphNode endpoint : linkableEndpoints) {
            String endpointId = endpoint.id();

            // Find all routes that produce to this endpoint (ROUTES_TO)
            List<String> producers = graph.getIncomingEdges(endpointId).stream()
                    .filter(edge -> edge.type() == EdgeType.ROUTES_TO)
                    .map(GraphEdge::from)
                    .toList();

            // Find all routes that consume from this endpoint (ROUTES_FROM)
            List<String> consumers = graph.getIncomingEdges(endpointId).stream()
                    .filter(edge -> edge.type() == EdgeType.ROUTES_FROM)
                    .map(GraphEdge::from)
                    .toList();

            // Create LINKS_TO edges from each producer to each consumer
            for (String producer : producers) {
                for (String consumer : consumers) {
                    graph.addEdge(new GraphEdge(producer, consumer, EdgeType.LINKS_TO, Map.of()));
                }
            }
        }
    }

    /**
     * Creates USES_COMPONENT edges linking endpoints to their Maven artifact dependencies. Convention: scheme "kafka"
     * maps to artifact "camel-kafka".
     */
    private void linkComponentsToMavenArtifacts(ProjectGraph graph) {
        List<GraphNode> endpoints = graph.findByType(NodeType.CAMEL_ENDPOINT);
        List<GraphNode> mavenArtifacts = graph.findByType(NodeType.MAVEN_ARTIFACT);

        // Build a map of artifactId to Maven node ID for quick lookup
        Map<String, String> artifactIdToNodeId = mavenArtifacts.stream()
                .collect(Collectors.toMap(
                        node -> node.properties().get("artifactId"),
                        GraphNode::id,
                        (existing, replacement) -> existing));

        // For each endpoint, find the corresponding Maven artifact
        for (GraphNode endpoint : endpoints) {
            String scheme = extractScheme(endpoint);
            if (scheme == null)
                continue;

            // Convention: scheme "kafka" → artifact "camel-kafka"
            String expectedArtifactId = "camel-" + scheme;
            String mavenNodeId = artifactIdToNodeId.get(expectedArtifactId);

            if (mavenNodeId != null) {
                graph.addEdge(new GraphEdge(endpoint.id(), mavenNodeId, EdgeType.USES_COMPONENT, Map.of()));
            }
        }
    }

    /**
     * Creates CONFIGURES edges linking config properties to endpoints. Matches "camel.component.{scheme}.*" properties
     * to endpoints with that scheme.
     */
    private void linkConfigToEndpoints(ProjectGraph graph) {
        List<GraphNode> configProperties = graph.findByType(NodeType.CONFIG_PROPERTY);
        List<GraphNode> endpoints = graph.findByType(NodeType.CAMEL_ENDPOINT);

        for (GraphNode config : configProperties) {
            String key = config.properties().get("key");
            if (key == null || !key.startsWith("camel.component."))
                continue;

            // Extract scheme from config key: "camel.component.kafka.brokers" → "kafka"
            String[] parts = key.split("\\.", 4);
            if (parts.length < 3)
                continue;
            String scheme = parts[2];

            // Link to all endpoints with matching scheme
            for (GraphNode endpoint : endpoints) {
                String endpointScheme = extractScheme(endpoint);
                if (scheme.equals(endpointScheme)) {
                    graph.addEdge(new GraphEdge(config.id(), endpoint.id(), EdgeType.CONFIGURES, Map.of()));
                }
            }
        }
    }

    /**
     * Extracts the scheme from an endpoint node. Supports both "scheme" property (explicit) and "uri" property
     * (extracts scheme from URI).
     *
     * @param  endpoint the endpoint node
     * @return          the scheme, or null if not found
     */
    private String extractScheme(GraphNode endpoint) {
        // Try explicit scheme property first
        String scheme = endpoint.properties().get("scheme");
        if (scheme != null) {
            return scheme;
        }

        // Fall back to extracting from URI
        String uri = endpoint.properties().get("uri");
        if (uri != null && uri.contains(":")) {
            return uri.substring(0, uri.indexOf(":"));
        }

        return null;
    }
}
