package io.github.luigidemasi.camelkit.command.graph;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.luigidemasi.camelkit.graph.ProjectGraph;
import io.github.luigidemasi.camelkit.graph.model.EdgeType;
import io.github.luigidemasi.camelkit.graph.query.GraphQuery;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import java.util.Arrays;
import java.util.stream.Collectors;

@Command(name = "neighbors", description = "BFS traversal from a node")
public class GraphNeighborsCommand extends GraphQueryCommand {
    @Parameters(index = "0", description = "Node ID") String nodeId;
    @Option(names = {"--direction"}, defaultValue = "both") String direction;
    @Option(names = {"--edge-type"}) String edgeType;
    @Option(names = {"--depth"}, defaultValue = "1") int depth;

    @Override protected String execute(ProjectGraph graph) {
        GraphQuery query = new GraphQuery(graph);
        EdgeType et = null;
        if (edgeType != null) {
            try { et = EdgeType.valueOf(edgeType); }
            catch (IllegalArgumentException e) {
                ObjectNode error = GraphJsonWriter.createObject();
                error.put("error", "Invalid edge type: " + edgeType);
                error.put("validTypes", Arrays.stream(EdgeType.values()).map(Enum::name).collect(Collectors.joining(", ")));
                return GraphJsonWriter.toJson(error);
            }
        }
        GraphQuery.NeighborResult result = query.neighbors(nodeId, direction, et, depth);
        ObjectNode root = GraphJsonWriter.createObject();
        root.put("found", !result.nodes().isEmpty());
        root.put("total", result.nodes().size());
        root.set("nodes", GraphJsonWriter.nodesToArray(result.nodes()));
        root.set("edges", GraphJsonWriter.edgesToArray(result.edges()));
        return GraphJsonWriter.toJson(root);
    }
}
