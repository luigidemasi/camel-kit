package io.github.luigidemasi.camelkit.command.graph;

import io.github.luigidemasi.camelkit.graph.ProjectGraph;
import io.github.luigidemasi.camelkit.graph.query.GraphQuery;

import com.fasterxml.jackson.databind.node.ObjectNode;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "subgraph", description = "All nodes within N hops of a node")
public class GraphSubgraphCommand extends GraphQueryCommand {
    @Parameters(index = "0")
    String nodeId;
    @Option(names = {"--hops"}, defaultValue = "2")
    int hops;

    @Override
    protected String execute(ProjectGraph graph) {
        GraphQuery.SubgraphResult result = new GraphQuery(graph).subgraph(nodeId, hops);
        ObjectNode root = GraphJsonWriter.createObject();
        root.set("nodes", GraphJsonWriter.nodesToArray(result.nodes()));
        root.set("edges", GraphJsonWriter.edgesToArray(result.edges()));
        return GraphJsonWriter.toJson(root);
    }
}
