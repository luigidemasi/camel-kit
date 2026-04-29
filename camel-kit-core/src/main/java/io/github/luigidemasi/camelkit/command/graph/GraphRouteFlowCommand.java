package io.github.luigidemasi.camelkit.command.graph;

import java.util.List;

import io.github.luigidemasi.camelkit.graph.ProjectGraph;
import io.github.luigidemasi.camelkit.graph.query.RouteFlowTracer;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "route-flow", description = "Trace message path through a route")
public class GraphRouteFlowCommand extends GraphQueryCommand {
    @Parameters(index = "0")
    String routeId;
    @Option(names = {"--from-endpoint"}, defaultValue = "false")
    boolean fromEndpoint;

    @Override
    protected String execute(ProjectGraph graph) {
        RouteFlowTracer tracer = new RouteFlowTracer(graph);
        List<RouteFlowTracer.FlowStep> steps = fromEndpoint ? tracer.traceFromEndpoint(routeId) : tracer.trace(routeId);
        ObjectNode root = GraphJsonWriter.createObject();
        root.put("found", !steps.isEmpty());
        ArrayNode arr = root.putArray("steps");
        steps.forEach(s -> {
            ObjectNode o = arr.addObject();
            o.put("nodeId", s.nodeId());
            o.put("type", s.type());
            o.put("label", s.label());
            o.put("depth", s.depth());
        });
        return GraphJsonWriter.toJson(root);
    }
}
