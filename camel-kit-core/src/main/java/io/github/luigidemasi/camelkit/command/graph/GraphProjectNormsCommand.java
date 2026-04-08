package io.github.luigidemasi.camelkit.command.graph;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.luigidemasi.camelkit.graph.ProjectGraph;
import io.github.luigidemasi.camelkit.graph.model.GraphNode;
import io.github.luigidemasi.camelkit.graph.model.NodeType;
import io.github.luigidemasi.camelkit.graph.query.GraphQuery;
import io.github.luigidemasi.camelkit.graph.query.RouteFlowTracer;
import picocli.CommandLine.Command;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Command(name = "project-norms", description = "Compute project norms for validation (composite)")
public class GraphProjectNormsCommand extends GraphQueryCommand {

    private static final Pattern KEBAB = Pattern.compile("^[a-z][a-z0-9]*(-[a-z][a-z0-9]*)+$");
    private static final Pattern CAMEL = Pattern.compile("^[a-z][a-zA-Z0-9]*$");
    private static final Pattern DOT = Pattern.compile("^[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)+$");

    @Override protected String execute(ProjectGraph graph) {
        GraphQuery query = new GraphQuery(graph);
        ObjectNode root = GraphJsonWriter.createObject();

        // Naming
        List<GraphNode> routes = query.find(".*", NodeType.CAMEL_ROUTE);
        List<String> ids = routes.stream().map(r -> r.properties().getOrDefault("name", r.id())).collect(Collectors.toList());
        long kebab = ids.stream().filter(n -> KEBAB.matcher(n).matches()).count();
        long camel = ids.stream().filter(n -> CAMEL.matcher(n).matches()).count();
        String pattern = kebab >= camel ? "kebab-case" : "camelCase";
        long matchCount = ids.stream().filter(n -> (pattern.equals("kebab-case") ? KEBAB : CAMEL).matcher(n).matches()).count();
        ObjectNode naming = root.putObject("naming");
        ArrayNode idsArr = naming.putArray("routeIds");
        ids.forEach(idsArr::add);
        naming.put("detectedPattern", pattern);
        naming.put("majorityPercentage", ids.isEmpty() ? 0 : (int)(matchCount * 100.0 / ids.size()));

        // Error handling
        int withEH = 0;
        for (GraphNode route : routes) {
            GraphQuery.NeighborResult nb = query.neighbors(route.id(), "out", null, 1);
            if (nb.nodes().stream().anyMatch(n -> { String id = n.id().toLowerCase(); return id.contains("error") || id.contains("exception") || id.contains("dlq"); }))
                withEH++;
        }
        ObjectNode eh = root.putObject("errorHandling");
        eh.put("totalRoutes", routes.size());
        eh.put("routesWithErrorHandling", withEH);
        eh.put("coverage", routes.isEmpty() ? 0.0 : Math.round(withEH * 1000.0 / routes.size()) / 10.0);

        // Properties
        List<GraphNode> props = query.find(".*", NodeType.CONFIG_PROPERTY);
        Set<String> prefixes = new TreeSet<>();
        props.forEach(p -> { String key = p.properties().getOrDefault("key", p.id()); int dot = key.lastIndexOf('.'); if (dot > 0) prefixes.add(key.substring(0, dot) + ".*"); });
        ObjectNode propsNode = root.putObject("properties");
        ArrayNode pats = propsNode.putArray("patterns");
        prefixes.forEach(pats::add);
        propsNode.put("count", props.size());

        // Step counts
        RouteFlowTracer tracer = new RouteFlowTracer(graph);
        List<Integer> counts = new ArrayList<>();
        routes.forEach(r -> counts.add(tracer.trace(r.id()).size()));
        Collections.sort(counts);
        ObjectNode sc = root.putObject("stepCounts");
        ArrayNode vals = sc.putArray("values");
        counts.forEach(vals::add);
        if (counts.isEmpty()) { sc.put("p75", 7); sc.put("median", 5); sc.put("max", 0); }
        else {
            sc.put("p75", counts.get(Math.max(0, (int)Math.ceil(0.75 * counts.size()) - 1)));
            sc.put("median", counts.get(counts.size() / 2));
            sc.put("max", counts.get(counts.size() - 1));
        }

        return GraphJsonWriter.toJson(root);
    }
}
