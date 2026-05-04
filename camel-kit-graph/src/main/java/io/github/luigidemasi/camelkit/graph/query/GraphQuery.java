package io.github.luigidemasi.camelkit.graph.query;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import io.github.luigidemasi.camelkit.graph.ProjectGraph;
import io.github.luigidemasi.camelkit.graph.model.*;

public class GraphQuery {

    private static final int MAX_RESULTS = 50;

    private final ProjectGraph graph;

    public record NeighborResult(List<GraphNode> nodes, List<GraphEdge> edges) {
    }

    public record SubgraphResult(List<GraphNode> nodes, List<GraphEdge> edges) {
    }

    public GraphQuery(ProjectGraph graph) {
        this.graph = graph;
    }

    /**
     * Regex match on node id, name, or fqn properties. Returns matching nodes, capped at 50.
     */
    public List<GraphNode> find(String query, NodeType typeFilter) {
        Pattern pattern = Pattern.compile(query);
        // First pass: exact match on name or fqn properties
        List<GraphNode> byProperty = graph.getNodes().values().stream()
                .filter(node -> typeFilter == null || node.type() == typeFilter)
                .filter(node -> {
                    String name = node.properties().get("name");
                    if (name != null && pattern.matcher(name).matches())
                        return true;
                    String fqn = node.properties().get("fqn");
                    return fqn != null && pattern.matcher(fqn).matches();
                })
                .limit(MAX_RESULTS)
                .collect(Collectors.toList());
        if (!byProperty.isEmpty()) {
            return byProperty;
        }
        // Second pass: substring search on node id
        return graph.getNodes().values().stream()
                .filter(node -> typeFilter == null || node.type() == typeFilter)
                .filter(node -> pattern.matcher(node.id()).find())
                .limit(MAX_RESULTS)
                .collect(Collectors.toList());
    }

    /**
     * BFS from nodeId following edges in given direction. direction: "out", "in", or "both".
     */
    public NeighborResult neighbors(String nodeId, String direction, EdgeType edgeType, int depth) {
        Set<String> visited = new LinkedHashSet<>();
        List<GraphEdge> collectedEdges = new ArrayList<>();
        Queue<String> frontier = new ArrayDeque<>();
        Map<String, Integer> depthMap = new HashMap<>();

        frontier.add(nodeId);
        depthMap.put(nodeId, 0);

        while (!frontier.isEmpty()) {
            String current = frontier.poll();
            int currentDepth = depthMap.get(current);
            if (currentDepth >= depth)
                continue;

            List<GraphEdge> adjacent = getEdges(current, direction);
            for (GraphEdge edge : adjacent) {
                if (edgeType != null && edge.type() != edgeType)
                    continue;

                String neighbor = edge.from().equals(current) ? edge.to() : edge.from();
                collectedEdges.add(edge);

                if (!visited.contains(neighbor) && !neighbor.equals(nodeId)) {
                    visited.add(neighbor);
                    if (!depthMap.containsKey(neighbor)) {
                        depthMap.put(neighbor, currentDepth + 1);
                        frontier.add(neighbor);
                    }
                }
            }
        }

        List<GraphNode> nodes = visited.stream()
                .map(graph::getNode)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        return new NeighborResult(nodes, collectedEdges);
    }

    /**
     * BFS shortest path from fromId to toId. Returns list of nodes in order, or empty list if no path found.
     */
    public List<GraphNode> path(String fromId, String toId, int maxDepth) {
        if (fromId.equals(toId)) {
            GraphNode node = graph.getNode(fromId);
            return node != null ? List.of(node) : List.of();
        }

        Map<String, String> parentMap = new LinkedHashMap<>();
        Queue<String> frontier = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();

        frontier.add(fromId);
        visited.add(fromId);
        parentMap.put(fromId, null);

        int currentDepth = 0;
        int nodesAtCurrentDepth = 1;
        int nodesAtNextDepth = 0;

        while (!frontier.isEmpty() && currentDepth < maxDepth) {
            String current = frontier.poll();
            nodesAtCurrentDepth--;

            // Follow outgoing edges (directed BFS)
            List<GraphEdge> outgoing = graph.getOutgoingEdges(current);
            for (GraphEdge edge : outgoing) {
                String neighbor = edge.to();
                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    parentMap.put(neighbor, current);
                    if (neighbor.equals(toId)) {
                        return reconstructPath(parentMap, fromId, toId);
                    }
                    frontier.add(neighbor);
                    nodesAtNextDepth++;
                }
            }

            if (nodesAtCurrentDepth == 0) {
                currentDepth++;
                nodesAtCurrentDepth = nodesAtNextDepth;
                nodesAtNextDepth = 0;
            }
        }

        return List.of();
    }

    /**
     * Collect all nodes and edges within radius hops (both directions).
     */
    public SubgraphResult subgraph(String nodeId, int radius) {
        NeighborResult result = neighbors(nodeId, "both", null, radius);
        List<GraphNode> nodes = new ArrayList<>(result.nodes());
        GraphNode center = graph.getNode(nodeId);
        if (center != null && nodes.stream().noneMatch(n -> n.id().equals(nodeId))) {
            nodes.add(0, center);
        }
        return new SubgraphResult(nodes, result.edges());
    }

    /**
     * Count nodes by type. Returns Map of type name to count.
     */
    public Map<String, Integer> stats() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (GraphNode node : graph.getNodes().values()) {
            String typeName = node.type().name();
            counts.merge(typeName, 1, Integer::sum);
        }
        return counts;
    }

    /**
     * Transitive closure in given direction. Returns all affected nodes, capped at 50.
     */
    public List<GraphNode> impact(String nodeId, String direction) {
        String bfsDirection = "downstream".equals(direction) ? "out" : "in";
        Set<String> visited = new LinkedHashSet<>();
        Queue<String> frontier = new ArrayDeque<>();

        frontier.add(nodeId);

        while (!frontier.isEmpty() && visited.size() < MAX_RESULTS) {
            String current = frontier.poll();
            List<GraphEdge> adjacent = getEdges(current, bfsDirection);
            for (GraphEdge edge : adjacent) {
                String neighbor = edge.from().equals(current) ? edge.to() : edge.from();
                if (!visited.contains(neighbor) && !neighbor.equals(nodeId)) {
                    visited.add(neighbor);
                    frontier.add(neighbor);
                }
            }
        }

        return visited.stream()
                .map(graph::getNode)
                .filter(Objects::nonNull)
                .limit(MAX_RESULTS)
                .collect(Collectors.toList());
    }

    /**
     * BFS expansion that crosses interface boundaries (DKB Algorithm 1). When encountering an IMPLEMENTS edge, also
     * traverses USES_TYPE predecessors of the interface. Returns all expanded nodes (excluding the start node), capped
     * at MAX_RESULTS.
     */
    public Set<GraphNode> expandWithInterfaces(String nodeId, String direction, int maxDepth) {
        record QueueEntry(String nodeId, int depth) {
        }

        Set<String> visited = new LinkedHashSet<>();
        Queue<QueueEntry> frontier = new ArrayDeque<>();
        Set<String> inQueue = new HashSet<>();

        frontier.add(new QueueEntry(nodeId, 0));
        inQueue.add(nodeId);

        while (!frontier.isEmpty() && visited.size() < MAX_RESULTS) {
            QueueEntry entry = frontier.poll();
            String current = entry.nodeId();
            int currentDepth = entry.depth();

            if (currentDepth >= maxDepth) {
                continue;
            }

            List<GraphEdge> adjacent = getEdges(current, direction);

            for (GraphEdge edge : adjacent) {
                String neighbor = edge.from().equals(current) ? edge.to() : edge.from();

                if (!visited.contains(neighbor) && !neighbor.equals(nodeId)) {
                    visited.add(neighbor);

                    if (!inQueue.contains(neighbor)) {
                        frontier.add(new QueueEntry(neighbor, currentDepth + 1));
                        inQueue.add(neighbor);
                    }
                }

                // Interface boundary crossing: if this is an IMPLEMENTS edge, also find USES_TYPE predecessors
                if (edge.type() == EdgeType.IMPLEMENTS) {
                    String interfaceNode = edge.to(); // The interface being implemented
                    List<GraphEdge> usesTypeEdges = graph.getIncomingEdges(interfaceNode);

                    for (GraphEdge usesEdge : usesTypeEdges) {
                        if (usesEdge.type() == EdgeType.USES_TYPE) {
                            String user = usesEdge.from();

                            if (!visited.contains(user) && !user.equals(nodeId)) {
                                visited.add(user);

                                if (!inQueue.contains(user)) {
                                    frontier.add(new QueueEntry(user, currentDepth + 1));
                                    inQueue.add(user);
                                }
                            }
                        }
                    }
                }
            }
        }

        return visited.stream()
                .map(graph::getNode)
                .filter(Objects::nonNull)
                .limit(MAX_RESULTS)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private List<GraphEdge> getEdges(String nodeId, String direction) {
        return switch (direction) {
            case "out" -> graph.getOutgoingEdges(nodeId);
            case "in" -> graph.getIncomingEdges(nodeId);
            case "both" -> {
                List<GraphEdge> all = new ArrayList<>(graph.getOutgoingEdges(nodeId));
                all.addAll(graph.getIncomingEdges(nodeId));
                yield all;
            }
            default ->
                throw new IllegalArgumentException("Invalid direction: " + direction + ". Use 'out', 'in', or 'both'.");
        };
    }

    private List<GraphNode> reconstructPath(Map<String, String> parentMap, String from, String to) {
        LinkedList<GraphNode> path = new LinkedList<>();
        String current = to;
        while (current != null) {
            GraphNode node = graph.getNode(current);
            if (node != null) {
                path.addFirst(node);
            }
            current = parentMap.get(current);
            if (current != null && current.equals(from)) {
                GraphNode fromNode = graph.getNode(from);
                if (fromNode != null) {
                    path.addFirst(fromNode);
                }
                break;
            }
        }
        return path;
    }
}
