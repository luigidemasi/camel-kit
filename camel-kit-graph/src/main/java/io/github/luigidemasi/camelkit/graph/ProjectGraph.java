package io.github.luigidemasi.camelkit.graph;

import java.util.*;
import java.util.stream.Collectors;

import io.github.luigidemasi.camelkit.graph.model.*;

public class ProjectGraph {

    private final Map<String, GraphNode> nodes = new LinkedHashMap<>();
    private final List<GraphEdge> edges = new ArrayList<>();

    public synchronized void addNode(GraphNode node) {
        nodes.put(node.id(), node);
    }

    public synchronized boolean hasNode(String id) {
        return nodes.containsKey(id);
    }

    public synchronized GraphNode getNode(String id) {
        return nodes.get(id);
    }

    public synchronized Map<String, GraphNode> getNodes() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(nodes));
    }

    public synchronized List<GraphEdge> getEdges() {
        return List.copyOf(edges);
    }

    public synchronized Set<GraphEdge> getEdgeSet() {
        return Collections.unmodifiableSet(new HashSet<>(edges));
    }

    public synchronized void addEdge(GraphEdge edge) {
        edges.add(edge);
    }

    public synchronized List<GraphNode> findByType(NodeType type) {
        return nodes.values().stream()
                .filter(n -> n.type() == type)
                .collect(Collectors.toList());
    }

    public synchronized List<GraphEdge> getOutgoingEdges(String nodeId) {
        return edges.stream()
                .filter(e -> e.from().equals(nodeId))
                .collect(Collectors.toList());
    }

    public synchronized List<GraphEdge> getIncomingEdges(String nodeId) {
        return edges.stream()
                .filter(e -> e.to().equals(nodeId))
                .collect(Collectors.toList());
    }

    public synchronized int nodeCount() {
        return nodes.size();
    }

    public synchronized int edgeCount() {
        return edges.size();
    }

    public void merge(ProjectGraph other) {
        other.getNodes().values().forEach(this::addNode);
        other.getEdges().forEach(this::addEdge);
    }
}
