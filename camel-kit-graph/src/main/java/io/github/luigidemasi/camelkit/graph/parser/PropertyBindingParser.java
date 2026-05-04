package io.github.luigidemasi.camelkit.graph.parser;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.luigidemasi.camelkit.graph.ProjectGraph;
import io.github.luigidemasi.camelkit.graph.model.EdgeType;
import io.github.luigidemasi.camelkit.graph.model.GraphEdge;
import io.github.luigidemasi.camelkit.graph.model.GraphNode;
import io.github.luigidemasi.camelkit.graph.model.NodeType;

public class PropertyBindingParser {

    private static final Pattern CLASS_PATTERN = Pattern.compile("#class:([^(#]+)(?:\\(([^)]+)\\))?(?:#(\\w+))?");
    private static final Pattern BEAN_PATTERN = Pattern.compile("#bean:([\\w.-]+)");
    private static final Pattern TYPE_PATTERN = Pattern.compile("#type:([\\w.]+)");
    private static final Pattern PROPERTY_PATTERN = Pattern.compile("#property:([\\w.-]+)");
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{([^}:]+)(?::([^}]+))?\\}\\}");

    public void parse(ProjectGraph graph, String runtime) {
        parsePropertyBindingSyntax(graph);
        if (runtime != null) {
            applyConventionBasedDetection(graph, runtime);
        }
    }

    private void parsePropertyBindingSyntax(ProjectGraph graph) {
        for (GraphNode node : graph.findByType(NodeType.CONFIG_PROPERTY)) {
            String value = node.properties().get("value");
            if (value == null || value.isBlank()) {
                continue;
            }

            // #class:com.foo.Bar or #class:com.foo.Bar(args) or #class:com.foo.Bar#method
            Matcher classMatcher = CLASS_PATTERN.matcher(value);
            if (classMatcher.matches()) {
                String className = classMatcher.group(1);
                String constructorArgs = classMatcher.group(2);
                String factoryMethod = classMatcher.group(3);

                String classNodeId = "class:" + className;
                ensureClassNode(graph, classNodeId, className);

                Map<String, String> edgeProps = new HashMap<>();
                if (constructorArgs != null) {
                    edgeProps.put("constructorArgs", constructorArgs);
                }
                if (factoryMethod != null) {
                    edgeProps.put("factoryMethod", factoryMethod);
                }

                graph.addEdge(new GraphEdge(node.id(), classNodeId, EdgeType.INSTANTIATES, edgeProps));
                continue;
            }

            // #bean:myBean
            Matcher beanMatcher = BEAN_PATTERN.matcher(value);
            if (beanMatcher.matches()) {
                String beanName = beanMatcher.group(1);
                String beanNodeId = findBeanNode(graph, beanName);
                if (beanNodeId == null) {
                    beanNodeId = "synthetic:bean:" + beanName;
                    graph.addNode(new GraphNode(
                            beanNodeId, NodeType.CLASS,
                            Map.of("synthetic", "true", "beanName", beanName, "bean", "true")));
                }
                graph.addEdge(new GraphEdge(node.id(), beanNodeId, EdgeType.REFERENCES_BEAN, Map.of()));
                continue;
            }

            // #autowired
            if (value.equals("#autowired")) {
                String autowiredNodeId = "synthetic:autowired:" + node.id();
                graph.addNode(new GraphNode(
                        autowiredNodeId, NodeType.CLASS,
                        Map.of("synthetic", "true", "autowired", "true")));
                graph.addEdge(new GraphEdge(node.id(), autowiredNodeId, EdgeType.REFERENCES_BEAN, Map.of()));
                continue;
            }

            // #type:com.foo.Type
            Matcher typeMatcher = TYPE_PATTERN.matcher(value);
            if (typeMatcher.matches()) {
                String typeName = typeMatcher.group(1);
                String typeNodeId = "class:" + typeName;
                ensureClassNode(graph, typeNodeId, typeName);
                graph.addEdge(new GraphEdge(node.id(), typeNodeId, EdgeType.REFERENCES_BEAN, Map.of()));
                continue;
            }

            // #property:otherKey
            Matcher propertyMatcher = PROPERTY_PATTERN.matcher(value);
            if (propertyMatcher.matches()) {
                String propertyKey = propertyMatcher.group(1);
                String propertyNodeId = "config:" + propertyKey;
                if (graph.hasNode(propertyNodeId)) {
                    graph.addEdge(new GraphEdge(node.id(), propertyNodeId, EdgeType.REFERENCES_PROPERTY, Map.of()));
                } else {
                    // Create synthetic property node
                    graph.addNode(new GraphNode(
                            propertyNodeId, NodeType.CONFIG_PROPERTY,
                            Map.of("key", propertyKey, "synthetic", "true")));
                    graph.addEdge(new GraphEdge(node.id(), propertyNodeId, EdgeType.REFERENCES_PROPERTY, Map.of()));
                }
            }
        }
    }

    private void applyConventionBasedDetection(ProjectGraph graph, String runtime) {
        if ("spring-boot".equals(runtime)) {
            handleSpringBootConventions(graph);
        } else if ("quarkus".equals(runtime)) {
            handleQuarkusConventions(graph);
        }
    }

    private void handleSpringBootConventions(ProjectGraph graph) {
        // spring.datasource.* → synthetic javax.sql.DataSource
        boolean hasDataSourceProps = graph.findByType(NodeType.CONFIG_PROPERTY).stream()
                .anyMatch(n -> n.properties().get("key") != null
                        && n.properties().get("key").startsWith("spring.datasource."));

        if (hasDataSourceProps) {
            String dataSourceNodeId = "synthetic:javax.sql.DataSource";
            graph.addNode(new GraphNode(
                    dataSourceNodeId, NodeType.CLASS,
                    Map.of("name", "javax.sql.DataSource", "bean", "true", "synthetic", "true")));

            for (GraphNode node : graph.findByType(NodeType.CONFIG_PROPERTY)) {
                String key = node.properties().get("key");
                if (key != null && key.startsWith("spring.datasource.")) {
                    graph.addEdge(new GraphEdge(node.id(), dataSourceNodeId, EdgeType.CONFIGURES, Map.of()));
                }
            }
        }
    }

    private void handleQuarkusConventions(ProjectGraph graph) {
        // quarkus.datasource.* → synthetic javax.sql.DataSource
        boolean hasDataSourceProps = graph.findByType(NodeType.CONFIG_PROPERTY).stream()
                .anyMatch(n -> n.properties().get("key") != null
                        && n.properties().get("key").startsWith("quarkus.datasource."));

        if (hasDataSourceProps) {
            String dataSourceNodeId = "synthetic:javax.sql.DataSource";
            if (!graph.hasNode(dataSourceNodeId)) {
                graph.addNode(new GraphNode(
                        dataSourceNodeId, NodeType.CLASS,
                        Map.of("name", "javax.sql.DataSource", "bean", "true", "synthetic", "true")));
            }

            for (GraphNode node : graph.findByType(NodeType.CONFIG_PROPERTY)) {
                String key = node.properties().get("key");
                if (key != null && key.startsWith("quarkus.datasource.")) {
                    graph.addEdge(new GraphEdge(node.id(), dataSourceNodeId, EdgeType.CONFIGURES, Map.of()));
                }
            }
        }

        // quarkus.camel.* → mark as build-time
        for (GraphNode node : graph.findByType(NodeType.CONFIG_PROPERTY)) {
            String key = node.properties().get("key");
            if (key != null && key.startsWith("quarkus.camel.")) {
                Map<String, String> updatedProps = new HashMap<>(node.properties());
                updatedProps.put("build-time", "true");
                graph.addNode(new GraphNode(node.id(), node.type(), updatedProps));
            }
        }
    }

    public void resolvePlaceholders(ProjectGraph graph) {
        for (GraphNode node : graph.findByType(NodeType.CAMEL_ENDPOINT)) {
            String uri = node.properties().get("uri");
            if (uri == null || uri.isBlank()) {
                continue;
            }

            Matcher matcher = PLACEHOLDER_PATTERN.matcher(uri);
            while (matcher.find()) {
                String key = matcher.group(1);
                String configNodeId = "config:" + key;
                if (graph.hasNode(configNodeId)) {
                    graph.addEdge(new GraphEdge(configNodeId, node.id(), EdgeType.CONFIGURES, Map.of()));
                }
            }
        }
    }

    private void ensureClassNode(ProjectGraph graph, String classNodeId, String className) {
        if (!graph.hasNode(classNodeId)) {
            String simpleName = className.substring(className.lastIndexOf('.') + 1);
            graph.addNode(new GraphNode(
                    classNodeId, NodeType.CLASS,
                    Map.of("name", simpleName, "synthetic", "true")));
        }
    }

    private String findBeanNode(ProjectGraph graph, String beanName) {
        return graph.findByType(NodeType.CLASS).stream()
                .filter(n -> beanName.equals(n.properties().get("beanName")))
                .map(GraphNode::id)
                .findFirst()
                .orElse(null);
    }
}
