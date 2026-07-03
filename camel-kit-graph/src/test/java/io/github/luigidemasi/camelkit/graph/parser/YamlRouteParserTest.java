package io.github.luigidemasi.camelkit.graph.parser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import io.github.luigidemasi.camelkit.graph.ProjectGraph;
import io.github.luigidemasi.camelkit.graph.model.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class YamlRouteParserTest {

    private static ProjectGraph graph;
    private static final Path TEST_PROJECT = Path.of("src/test/resources/testdata");

    @BeforeAll
    static void setUp() {
        graph = new ProjectGraph();
        new YamlRouteParser().parse(TEST_PROJECT, graph);
    }

    @Test
    void parsesYamlRoute() {
        assertTrue(graph.hasNode("route:yamlHttpRoute"));
    }

    @Test
    void parsesFromEndpoint() {
        assertEquals("platform-http:/api/orders",
                graph.getNode("route:yamlHttpRoute").properties().get("fromUri"));
    }

    @Test
    void parsesToEndpoints() {
        List<GraphEdge> routesTo = graph.getOutgoingEdges("route:yamlHttpRoute").stream()
                .filter(e -> e.type() == EdgeType.ROUTES_TO)
                .toList();
        assertEquals(1, routesTo.size());
        assertEquals("endpoint:direct:enrichOrder", routesTo.get(0).to());
    }

    @Test
    void parsesProcessors() {
        List<GraphEdge> processors = graph.getOutgoingEdges("route:yamlHttpRoute").stream()
                .filter(e -> e.type() == EdgeType.PROCESSES)
                .toList();
        assertTrue(processors.size() >= 1); // log step
    }

    @Test
    void parsesTopLevelFromWithoutRouteId(@TempDir Path tempDir) throws Exception {
        Path route = tempDir.resolve("top.camel.yaml");
        Files.writeString(route, """
                from:
                  uri: "direct:start"
                  steps:
                    - toD:
                        uri: "http://example.test/${body}"
                """);
        YamlRouteParser parser = new YamlRouteParser();
        ProjectGraph graph = new ProjectGraph();

        parser.parseFiles(tempDir, List.of(route), graph);

        assertTrue(graph.hasNode("route:top.camel.yaml#route-1"));
        assertTrue(graph.hasNode("endpoint:http://example.test/${body}"));
        assertEquals("http", graph.getNode("endpoint:http://example.test/${body}").properties().get("scheme"));
    }

    @Test
    void warnsOnRouteLevelSteps(@TempDir Path tempDir) throws Exception {
        Path route = tempDir.resolve("bad.camel.yaml");
        Files.writeString(route, """
                - route:
                    id: bad
                    from:
                      uri: "direct:start"
                    steps:
                      - to:
                          uri: "mock:out"
                """);
        YamlRouteParser parser = new YamlRouteParser();
        ProjectGraph graph = new ProjectGraph();

        parser.parseFiles(tempDir, List.of(route), graph);

        assertTrue(parser.warnings().stream().anyMatch(warning -> warning.contains("route-level steps")));
    }
}
