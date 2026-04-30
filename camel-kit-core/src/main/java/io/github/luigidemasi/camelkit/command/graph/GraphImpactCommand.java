package io.github.luigidemasi.camelkit.command.graph;

import java.util.*;

import io.github.luigidemasi.camelkit.graph.ProjectGraph;
import io.github.luigidemasi.camelkit.graph.model.GraphNode;
import io.github.luigidemasi.camelkit.graph.query.GraphQuery;

import com.fasterxml.jackson.databind.node.ObjectNode;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "impact", description = "Transitive closure — upstream/downstream of a node")
public class GraphImpactCommand extends GraphQueryCommand {
    @Parameters(index = "0")
    String nodeId;
    @Option(names = {"--direction"}, defaultValue = "both")
    String direction;

    @Override
    protected String execute(ProjectGraph graph) {
        GraphQuery query = new GraphQuery(graph);
        List<GraphNode> impacted;
        if ("both".equals(direction)) {
            List<GraphNode> up = query.impact(nodeId, "upstream");
            List<GraphNode> down = query.impact(nodeId, "downstream");
            impacted = new ArrayList<>(up);
            down.stream().filter(n -> up.stream().noneMatch(u -> u.id().equals(n.id()))).forEach(impacted::add);
        } else {
            impacted = query.impact(nodeId, direction);
        }
        Map<String, List<GraphNode>> byType = new LinkedHashMap<>();
        impacted.forEach(n -> byType.computeIfAbsent(n.type().name(), k -> new ArrayList<>()).add(n));
        ObjectNode root = GraphJsonWriter.createObject();
        root.put("found", !impacted.isEmpty());
        root.put("total", impacted.size());
        ObjectNode bt = root.putObject("byType");
        byType.forEach((type, nodes) -> bt.set(type, GraphJsonWriter.nodesToArray(nodes)));
        return GraphJsonWriter.toJson(root);
    }
}
