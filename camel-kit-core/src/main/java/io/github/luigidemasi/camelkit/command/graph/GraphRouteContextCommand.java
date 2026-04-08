package io.github.luigidemasi.camelkit.command.graph;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.luigidemasi.camelkit.graph.ProjectGraph;
import io.github.luigidemasi.camelkit.graph.model.GraphNode;
import io.github.luigidemasi.camelkit.graph.model.NodeType;
import io.github.luigidemasi.camelkit.graph.query.GraphQuery;
import io.github.luigidemasi.camelkit.graph.query.RouteFlowTracer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import java.util.*;

@Command(name = "route-context", description = "Map route topology for test planning (composite)")
public class GraphRouteContextCommand extends GraphQueryCommand {

    private static final Set<String> INTERNAL = Set.of("direct", "seda", "direct-vm");
    private static final Set<String> INFRA = Set.of("kafka", "jms", "amqp", "activemq", "sql", "jdbc", "mongodb", "ftp", "sftp", "aws-s3", "aws-sqs", "aws-sns", "minio");

    @Parameters(index = "0", description = "Route ID") String routeId;

    @Override protected String execute(ProjectGraph graph) {
        GraphQuery query = new GraphQuery(graph);
        ObjectNode root = GraphJsonWriter.createObject();
        root.put("route", routeId);
        root.set("upstream", GraphJsonWriter.nodesToArray(query.impact(routeId, "upstream")));
        root.set("downstream", GraphJsonWriter.nodesToArray(query.impact(routeId, "downstream")));

        // Endpoint classification
        GraphQuery.NeighborResult nb = query.neighbors(routeId, "out", null, 1);
        List<String> internal = new ArrayList<>(), infra = new ArrayList<>(), api = new ArrayList<>();
        for (GraphNode n : nb.nodes()) {
            if (n.type() != NodeType.CAMEL_ENDPOINT) continue;
            String scheme = n.properties().getOrDefault("scheme", "");
            String uri = n.properties().getOrDefault("uri", n.id());
            if (INTERNAL.contains(scheme)) internal.add(uri);
            else if (INFRA.contains(scheme)) infra.add(uri);
            else api.add(uri);
        }
        ObjectNode ep = root.putObject("endpoints");
        ArrayNode ia = ep.putArray("internal"); internal.forEach(ia::add);
        ArrayNode fa = ep.putArray("externalInfra"); infra.forEach(fa::add);
        ArrayNode aa = ep.putArray("externalApi"); api.forEach(aa::add);

        // Error flow
        RouteFlowTracer tracer = new RouteFlowTracer(graph);
        ArrayNode ef = root.putArray("errorFlow");
        tracer.trace(routeId).stream()
                .filter(s -> { String t = s.type().toLowerCase(); String l = s.label().toLowerCase(); return t.contains("error") || t.contains("exception") || l.contains("dlq") || l.contains("deadletter"); })
                .forEach(s -> { ObjectNode o = ef.addObject(); o.put("nodeId", s.nodeId()); o.put("type", s.type()); o.put("label", s.label()); o.put("depth", s.depth()); });

        return GraphJsonWriter.toJson(root);
    }
}
