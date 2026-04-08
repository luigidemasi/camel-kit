package io.github.luigidemasi.camelkit.graph.query;

import io.github.luigidemasi.camelkit.graph.ProjectGraph;
import io.github.luigidemasi.camelkit.graph.model.*;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class DeadCodeAnalyzer {

    /**
     * Framework artifacts that are used implicitly (not via endpoint URIs).
     * These should never be flagged as unused.
     */
    private static final Set<String> FRAMEWORK_ARTIFACTS = Set.of(
            "camel-core", "camel-core-engine", "camel-core-model", "camel-core-processor",
            "camel-core-reifier", "camel-core-languages",
            "camel-main", "camel-spring-boot", "camel-spring-boot-starter",
            "camel-quarkus-core", "camel-bean", "camel-direct", "camel-seda",
            "camel-log", "camel-timer", "camel-mock", "camel-stub",
            "camel-xml-jaxb", "camel-xml-io", "camel-xml-io-dsl",
            "camel-yaml-dsl", "camel-kamelet",
            "camel-platform-http", "camel-platform-http-main"
    );

    private final ProjectGraph graph;

    public record DeadCodeResult(
            List<GraphNode> unusedArtifacts,
            List<GraphNode> orphanedRoutes,
            List<GraphNode> unusedProperties
    ) {}

    public DeadCodeAnalyzer(ProjectGraph graph) {
        this.graph = graph;
    }

    public DeadCodeResult analyze() {
        return new DeadCodeResult(
                findUnusedArtifacts(),
                findOrphanedRoutes(),
                findUnusedProperties()
        );
    }

    private List<GraphNode> findUnusedArtifacts() {
        // Collect all artifact IDs that have at least one USES_COMPONENT edge pointing to them
        Set<String> usedArtifactIds = graph.getEdges().stream()
                .filter(e -> e.type() == EdgeType.USES_COMPONENT)
                .map(GraphEdge::to)
                .collect(Collectors.toSet());

        return graph.findByType(NodeType.MAVEN_ARTIFACT).stream()
                .filter(node -> {
                    String artifactId = node.properties().get("artifactId");
                    if (artifactId == null) return false;
                    // Only check camel-* artifacts
                    if (!artifactId.startsWith("camel-")) return false;
                    // Exclude framework artifacts
                    if (FRAMEWORK_ARTIFACTS.contains(artifactId)) return false;
                    // Unused if no USES_COMPONENT edge points to this node
                    return !usedArtifactIds.contains(node.id());
                })
                .collect(Collectors.toList());
    }

    private List<GraphNode> findOrphanedRoutes() {
        return graph.findByType(NodeType.CAMEL_ROUTE).stream()
                .filter(route -> {
                    // Find the from-endpoint for this route
                    List<GraphEdge> fromEdges = graph.getOutgoingEdges(route.id()).stream()
                            .filter(e -> e.type() == EdgeType.ROUTES_FROM)
                            .collect(Collectors.toList());

                    for (GraphEdge fromEdge : fromEdges) {
                        GraphNode endpoint = graph.getNode(fromEdge.to());
                        if (endpoint == null) continue;

                        String scheme = endpoint.properties().get("scheme");
                        if (scheme == null) continue;

                        // Only internal endpoints can be orphaned
                        if (!"direct".equals(scheme) && !"seda".equals(scheme)) {
                            return false; // External consumer = entry point, not orphaned
                        }

                        // Check if any other route ROUTES_TO an endpoint with the same URI
                        String uri = endpoint.properties().get("uri");
                        if (uri == null) continue;

                        boolean hasProducer = graph.getEdges().stream()
                                .filter(e -> e.type() == EdgeType.ROUTES_TO)
                                .map(GraphEdge::to)
                                .map(graph::getNode)
                                .filter(java.util.Objects::nonNull)
                                .anyMatch(ep -> uri.equals(ep.properties().get("uri")));

                        if (!hasProducer) {
                            return true; // Orphaned: internal consumer with no producer
                        }
                    }
                    return false;
                })
                .collect(Collectors.toList());
    }

    private List<GraphNode> findUnusedProperties() {
        // Collect all property node IDs that have a CONFIGURES edge
        Set<String> configuredPropertyIds = graph.getEdges().stream()
                .filter(e -> e.type() == EdgeType.CONFIGURES)
                .map(GraphEdge::from)
                .collect(Collectors.toSet());

        return graph.findByType(NodeType.CONFIG_PROPERTY).stream()
                .filter(node -> {
                    String key = node.properties().get("key");
                    if (key == null) return false;
                    // Only check camel.* properties
                    if (!key.startsWith("camel.")) return false;
                    // Unused if no CONFIGURES edge from this property
                    return !configuredPropertyIds.contains(node.id());
                })
                .collect(Collectors.toList());
    }
}
