package io.github.luigidemasi.camelkit.graph;

import io.github.luigidemasi.camelkit.graph.model.GraphNode;
import io.github.luigidemasi.camelkit.graph.model.NodeType;

public final class RuntimeDetector {

    private RuntimeDetector() {
    }

    public static String detect(ProjectGraph graph) {
        for (GraphNode artifact : graph.findByType(NodeType.MAVEN_ARTIFACT)) {
            String artifactId = artifact.properties().getOrDefault("artifactId", "");
            if (artifactId.startsWith("camel-spring-boot")) {
                return "spring-boot";
            }
            if (artifactId.startsWith("camel-quarkus")) {
                return "quarkus";
            }
            if ("camel-blueprint".equals(artifactId)) {
                return "karaf";
            }
        }
        return "main";
    }
}
