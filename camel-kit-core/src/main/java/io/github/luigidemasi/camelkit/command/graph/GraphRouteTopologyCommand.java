package io.github.luigidemasi.camelkit.command.graph;

import java.util.List;
import java.util.Map;

import io.github.luigidemasi.camelkit.graph.ProjectGraph;
import io.github.luigidemasi.camelkit.graph.query.RouteTopology;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import picocli.CommandLine.Command;

@Command(name = "route-topology", description = "Show route connectivity map")
public class GraphRouteTopologyCommand extends GraphQueryCommand {

    @Override
    protected String execute(ProjectGraph graph) {
        RouteTopology topology = new RouteTopology(graph);
        Map<String, List<RouteTopology.RouteConnection>> routeMap = topology.build();

        ObjectNode root = GraphJsonWriter.createObject();
        ObjectNode routes = root.putObject("routes");

        routeMap.forEach((routeId, connections) -> {
            ArrayNode conns = routes.putArray(routeId);
            connections.forEach(conn -> {
                ObjectNode c = conns.addObject();
                c.put("target", conn.targetRouteId());
                c.put("scheme", conn.scheme());
                c.put("uri", conn.endpointUri());
            });
        });

        return GraphJsonWriter.toJson(root);
    }
}
