package io.github.luigidemasi.camelkit.graph;

import io.github.luigidemasi.camelkit.graph.model.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ProjectGraph {

    private final Map<String, GraphNode> nodes = new ConcurrentHashMap<>();
    private final List<GraphEdge> edges = Collections.synchronizedList(new ArrayList<>());

    public void addNode(GraphNode node) {
        nodes.put(node.id(), node);
    }

    public boolean hasNode(String id) {
        return nodes.containsKey(id);
    }

    public GraphNode getNode(String id) {
        return nodes.get(id);
    }

    public Map<String, GraphNode> getNodes() {
        return Collections.unmodifiableMap(nodes);
    }

    public List<GraphEdge> getEdges() {
        return Collections.unmodifiableList(edges);
    }

    public void addEdge(GraphEdge edge) {
        edges.add(edge);
    }

    public List<GraphNode> findByType(NodeType type) {
        return nodes.values().stream()
            .filter(n -> n.type() == type)
            .collect(Collectors.toList());
    }

    public List<GraphEdge> getOutgoingEdges(String nodeId) {
        return edges.stream()
            .filter(e -> e.from().equals(nodeId))
            .collect(Collectors.toList());
    }

    public List<GraphEdge> getIncomingEdges(String nodeId) {
        return edges.stream()
            .filter(e -> e.to().equals(nodeId))
            .collect(Collectors.toList());
    }

    public int nodeCount() {
        return nodes.size();
    }

    public int edgeCount() {
        return edges.size();
    }

    public void merge(ProjectGraph other) {
        other.nodes.values().forEach(this::addNode);
        other.edges.forEach(this::addEdge);
    }
}
