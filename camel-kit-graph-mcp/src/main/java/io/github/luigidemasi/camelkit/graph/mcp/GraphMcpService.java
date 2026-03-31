package io.github.luigidemasi.camelkit.graph.mcp;

import io.github.luigidemasi.camelkit.graph.GraphSerializer;
import io.github.luigidemasi.camelkit.graph.ProjectGraph;
import io.github.luigidemasi.camelkit.graph.query.GraphQuery;
import io.github.luigidemasi.camelkit.graph.query.RouteFlowTracer;
import io.github.luigidemasi.camelkit.graph.query.RouteTopology;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Singleton
public class GraphMcpService {

    private static final String GRAPH_FILE = ".camel-kit/project-graph.json";

    private volatile ProjectGraph graph;
    private volatile GraphQuery query;
    private volatile RouteFlowTracer flowTracer;
    private volatile RouteTopology topology;

    public boolean isAvailable() {
        ensureLoaded();
        return graph != null;
    }

    public ProjectGraph getGraph() {
        ensureLoaded();
        return graph;
    }

    public GraphQuery getQuery() {
        ensureLoaded();
        return query;
    }

    public RouteFlowTracer getFlowTracer() {
        ensureLoaded();
        return flowTracer;
    }

    public RouteTopology getTopology() {
        ensureLoaded();
        return topology;
    }

    public void reload() {
        synchronized (this) {
            graph = null;
            query = null;
            flowTracer = null;
            topology = null;
            Path graphPath = resolveGraphPath();
            if (graphPath != null && Files.exists(graphPath)) {
                try {
                    ProjectGraph g = GraphSerializer.read(graphPath);
                    query = new GraphQuery(g);
                    flowTracer = new RouteFlowTracer(g);
                    topology = new RouteTopology(g);
                    graph = g; // assign sentinel LAST
                } catch (IOException e) {
                    System.err.println("Failed to load project graph: " + e.getMessage());
                }
            }
        }
    }

    // Package-private, for testing only
    void setGraphForTesting(ProjectGraph testGraph) {
        this.graph = testGraph;
        this.query = new GraphQuery(testGraph);
        this.flowTracer = new RouteFlowTracer(testGraph);
        this.topology = new RouteTopology(testGraph);
    }

    private void ensureLoaded() {
        if (graph != null) return;
        synchronized (this) {
            if (graph != null) return;
            Path graphPath = resolveGraphPath();
            if (graphPath != null && Files.exists(graphPath)) {
                try {
                    ProjectGraph g = GraphSerializer.read(graphPath);
                    query = new GraphQuery(g);
                    flowTracer = new RouteFlowTracer(g);
                    topology = new RouteTopology(g);
                    graph = g; // assign sentinel LAST so concurrent readers see all fields
                } catch (IOException e) {
                    System.err.println("Failed to load project graph: " + e.getMessage());
                }
            }
        }
    }

    private Path resolveGraphPath() {
        String projectPath = System.getProperty("camel.graph.project-path");
        if (projectPath != null) {
            return Path.of(projectPath, GRAPH_FILE);
        }
        return Path.of(GRAPH_FILE);
    }
}
