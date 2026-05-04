package io.github.luigidemasi.camelkit.command.graph;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import io.github.luigidemasi.camelkit.graph.ProjectGraph;
import io.github.luigidemasi.camelkit.graph.model.GraphEdge;
import io.github.luigidemasi.camelkit.graph.model.GraphNode;
import io.github.luigidemasi.camelkit.graph.model.NodeType;
import io.github.luigidemasi.camelkit.graph.query.GraphQuery;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "migration-context",
         description = "Structured migration context for a route — components, services, artifacts, properties, warnings")
public class GraphMigrationContextCommand extends GraphQueryCommand {

    @Parameters(index = "0", description = "Route ID (without route: prefix)")
    String routeId;

    @Option(names = {"--depth"}, defaultValue = "3", description = "BFS expansion depth (default: 3)")
    int depth;

    @Override
    protected String execute(ProjectGraph graph) {
        String nodeId = "route:" + routeId;
        GraphNode routeNode = graph.getNode(nodeId);
        if (routeNode == null) {
            ObjectNode errorNode = GraphJsonWriter.createObject();
            errorNode.put("error", "Route not found: " + routeId);
            return GraphJsonWriter.toJson(errorNode);
        }

        GraphQuery query = new GraphQuery(graph);
        Set<GraphNode> expanded = query.expandWithInterfaces(nodeId, "both", depth);
        // Include the route node itself in the working set
        expanded.add(routeNode);

        ObjectNode root = GraphJsonWriter.createObject();
        root.put("route", routeId);
        root.put("runtime", detectRuntime());

        // Components: deduplicated schemes from CAMEL_ENDPOINT nodes
        Set<String> components = new TreeSet<>();
        for (GraphNode node : expanded) {
            if (node.type() == NodeType.CAMEL_ENDPOINT) {
                String scheme = node.properties().get("scheme");
                if (scheme == null) {
                    String uri = node.properties().getOrDefault("uri", "");
                    int colonIdx = uri.indexOf(':');
                    if (colonIdx > 0) {
                        scheme = uri.substring(0, colonIdx);
                    }
                }
                if (scheme != null && !scheme.isEmpty()) {
                    components.add(scheme);
                }
            }
        }
        ArrayNode componentsArray = root.putArray("components");
        components.forEach(componentsArray::add);

        // Routes: all CAMEL_ROUTE nodes
        ArrayNode routesArray = root.putArray("routes");
        for (GraphNode node : expanded) {
            if (node.type() == NodeType.CAMEL_ROUTE) {
                ObjectNode routeJson = GraphJsonWriter.createObject();
                routeJson.put("id", node.id());
                routeJson.put("from", node.properties().getOrDefault("fromUri", ""));
                routeJson.put("file", node.properties().getOrDefault("file", ""));
                routesArray.add(routeJson);
            }
        }

        // Services: CLASS nodes with bean=true
        ArrayNode servicesArray = root.putArray("services");
        for (GraphNode node : expanded) {
            if (node.type() == NodeType.CLASS && "true".equals(node.properties().get("bean"))) {
                ObjectNode serviceJson = GraphJsonWriter.createObject();
                serviceJson.put("class", node.properties().getOrDefault("fqn", node.id()));
                serviceJson.put("bean", true);
                serviceJson.put("beanName", node.properties().getOrDefault("beanName", ""));
                servicesArray.add(serviceJson);
            }
        }

        // Artifacts: MAVEN_ARTIFACT nodes
        ArrayNode artifactsArray = root.putArray("artifacts");
        for (GraphNode node : expanded) {
            if (node.type() == NodeType.MAVEN_ARTIFACT) {
                ObjectNode artifactJson = GraphJsonWriter.createObject();
                artifactJson.put("groupId", node.properties().getOrDefault("groupId", ""));
                artifactJson.put("artifactId", node.properties().getOrDefault("artifactId", ""));
                artifactJson.put("version", node.properties().getOrDefault("version", ""));
                artifactsArray.add(artifactJson);
            }
        }

        // Properties: CONFIG_PROPERTY nodes with their outgoing edge types
        ArrayNode propertiesArray = root.putArray("properties");
        for (GraphNode node : expanded) {
            if (node.type() == NodeType.CONFIG_PROPERTY) {
                List<GraphEdge> outgoing = graph.getOutgoingEdges(node.id());
                if (outgoing.isEmpty()) {
                    ObjectNode propJson = GraphJsonWriter.createObject();
                    propJson.put("key", node.properties().getOrDefault("key", node.id()));
                    propJson.put("value", node.properties().getOrDefault("value", ""));
                    propJson.put("edgeType", "");
                    propJson.put("target", "");
                    propertiesArray.add(propJson);
                } else {
                    for (GraphEdge edge : outgoing) {
                        ObjectNode propJson = GraphJsonWriter.createObject();
                        propJson.put("key", node.properties().getOrDefault("key", node.id()));
                        propJson.put("value", node.properties().getOrDefault("value", ""));
                        propJson.put("edgeType", edge.type().name());
                        propJson.put("target", edge.to());
                        propertiesArray.add(propJson);
                    }
                }
            }
        }

        // Warnings: nodes with synthetic=true
        ArrayNode warningsArray = root.putArray("warnings");
        for (GraphNode node : expanded) {
            if ("true".equals(node.properties().get("synthetic"))) {
                ObjectNode warningJson = GraphJsonWriter.createObject();
                warningJson.put("type", "synthetic-node");
                warningJson.put("name", node.id());
                warningJson.put("reason", "Node was inferred, not parsed from source");
                warningsArray.add(warningJson);
            }
        }

        return GraphJsonWriter.toJson(root);
    }

    private String detectRuntime() {
        Path configFile = Path.of(".camel-kit", "config.properties");
        if (Files.exists(configFile)) {
            Properties props = new Properties();
            try (InputStream in = Files.newInputStream(configFile)) {
                props.load(in);
                String runtime = props.getProperty("project.runtime");
                if (runtime != null && !runtime.isEmpty()) {
                    return runtime;
                }
            } catch (IOException e) {
                // Fall through to unknown
            }
        }
        return "unknown";
    }
}
