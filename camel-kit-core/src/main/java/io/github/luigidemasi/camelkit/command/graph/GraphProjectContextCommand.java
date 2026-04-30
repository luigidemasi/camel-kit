package io.github.luigidemasi.camelkit.command.graph;

import java.util.List;
import java.util.stream.Collectors;

import io.github.luigidemasi.camelkit.graph.ProjectGraph;
import io.github.luigidemasi.camelkit.graph.model.GraphNode;
import io.github.luigidemasi.camelkit.graph.model.NodeType;
import io.github.luigidemasi.camelkit.graph.query.GraphQuery;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import picocli.CommandLine.Command;

@Command(name = "project-context", description = "Extract project conventions for implementation (composite)")
public class GraphProjectContextCommand extends GraphQueryCommand {

    @Override
    protected String execute(ProjectGraph graph) {
        GraphQuery query = new GraphQuery(graph);
        ObjectNode root = GraphJsonWriter.createObject();

        // Property conventions
        List<GraphNode> properties = query.find(".*", NodeType.CONFIG_PROPERTY);
        List<String> keys = properties.stream()
                .map(p -> p.properties().getOrDefault("key", p.id()))
                .collect(Collectors.toList());
        long dotCount = keys.stream().filter(k -> k.contains(".")).count();
        long underCount = keys.stream().filter(k -> k.contains("_")).count();

        ObjectNode pc = root.putObject("propertyConventions");
        ArrayNode propsArr = pc.putArray("properties");
        properties.forEach(p -> {
            ObjectNode o = propsArr.addObject();
            o.put("key", p.properties().getOrDefault("key", p.id()));
            String v = p.properties().getOrDefault("value", "");
            if (!v.isEmpty())
                o.put("value", v);
        });
        pc.put("namingStyle", underCount > dotCount ? "underscore-separated" : "dot-separated-lowercase");

        // Existing beans
        List<GraphNode> classes = query.find(".*", NodeType.CLASS);
        ArrayNode beans = root.putArray("existingBeans");
        classes.forEach(c -> {
            ObjectNode b = beans.addObject();
            b.put("name", c.properties().getOrDefault("name", c.id()));
            b.put("fqn", c.properties().getOrDefault("fqn", ""));
            b.put("id", c.id());
        });

        // Dependency versions
        List<GraphNode> artifacts = query.find(".*", NodeType.MAVEN_ARTIFACT);
        ObjectNode versions = root.putObject("dependencyVersions");
        artifacts.forEach(a -> versions.put(
                a.properties().getOrDefault("artifactId", a.id()),
                a.properties().getOrDefault("version", "unknown")));

        // Route directory
        List<GraphNode> resources = query.find(".*\\.camel\\.yaml", NodeType.RESOURCE_FILE);
        if (resources.isEmpty())
            resources = query.find(".*\\.xml", NodeType.RESOURCE_FILE);
        String dir = "src/main/resources/camel";
        if (!resources.isEmpty()) {
            String path = resources.get(0).properties().getOrDefault("path", "");
            int ls = path.lastIndexOf('/');
            if (ls > 0)
                dir = path.substring(0, ls);
        }
        root.put("routeDirectory", dir);

        return GraphJsonWriter.toJson(root);
    }
}
