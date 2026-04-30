package io.github.luigidemasi.camelkit.command.graph;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import io.github.luigidemasi.camelkit.graph.ProjectGraph;
import io.github.luigidemasi.camelkit.graph.model.GraphNode;
import io.github.luigidemasi.camelkit.graph.model.NodeType;
import io.github.luigidemasi.camelkit.graph.query.GraphQuery;

import com.fasterxml.jackson.databind.node.ObjectNode;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "find", description = "Find nodes by type and name pattern")
public class GraphFindCommand extends GraphQueryCommand {
    @Option(names = {"--type"}, description = "Node type filter")
    String type;
    @Option(names = {"--name"}, description = "Name pattern (regex)", defaultValue = ".*")
    String namePattern;

    @Override
    protected String execute(ProjectGraph graph) {
        NodeType nodeType = null;
        if (type != null) {
            try {
                nodeType = NodeType.valueOf(type);
            } catch (IllegalArgumentException e) {
                ObjectNode error = GraphJsonWriter.createObject();
                error.put("error", "Invalid node type: " + type);
                error.put("validTypes",
                        Arrays.stream(NodeType.values()).map(Enum::name).collect(Collectors.joining(", ")));
                return GraphJsonWriter.toJson(error);
            }
        }
        GraphQuery query = new GraphQuery(graph);
        List<GraphNode> nodes = query.find(namePattern, nodeType);
        ObjectNode root = GraphJsonWriter.createObject();
        root.put("found", !nodes.isEmpty());
        root.put("total", nodes.size());
        root.set("nodes", GraphJsonWriter.nodesToArray(nodes));
        return GraphJsonWriter.toJson(root);
    }
}
