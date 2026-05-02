package io.github.luigidemasi.camelkit.command.graph;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.Map;

import io.github.luigidemasi.camelkit.graph.ProjectGraph;
import io.github.luigidemasi.camelkit.graph.model.EdgeType;
import io.github.luigidemasi.camelkit.graph.model.GraphEdge;
import io.github.luigidemasi.camelkit.graph.model.GraphNode;
import io.github.luigidemasi.camelkit.graph.model.NodeType;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.*;

class GraphRouteContextCommandTest {
    static final ObjectMapper MAPPER = new ObjectMapper();
    @TempDir
    Path tempDir;
    Path graphFile;

    @BeforeEach
    void setUp() throws Exception {
        graphFile = TestGraphs.writeToTempFile(TestGraphs.sampleProject(), tempDir);
    }

    @Test
    void returnsRouteId() throws Exception {
        assertEquals("route:order-process", MAPPER.readTree(run("route:order-process")).get("route").asText());
    }

    @Test
    void returnsUpstream() throws Exception {
        assertTrue(MAPPER.readTree(run("route:order-process")).has("upstream"));
    }

    @Test
    void returnsDownstream() throws Exception {
        assertTrue(MAPPER.readTree(run("route:order-process")).has("downstream"));
    }

    @Test
    void returnsEndpointClassification() throws Exception {
        JsonNode ep = MAPPER.readTree(run("route:order-process")).get("endpoints");
        assertTrue(ep.has("internal"));
        assertTrue(ep.has("externalInfra"));
        assertTrue(ep.has("externalApi"));
    }

    @Test
    void classifiesKafkaAsInfra() throws Exception {
        JsonNode infra = MAPPER.readTree(run("route:order-process")).get("endpoints").get("externalInfra");
        boolean found = false;
        for (JsonNode n : infra)
            if (n.asText().contains("kafka"))
                found = true;
        assertTrue(found);
    }

    @Test
    void returnsErrorFlow() throws Exception {
        assertTrue(MAPPER.readTree(run("route:order-process")).has("errorFlow"));
    }

    @Test
    void doesNotThrowWhenProcessorLabelIsNull() throws Exception {
        ProjectGraph graph = TestGraphs.sampleProject();
        graph.addNode(new GraphNode(
                "proc:no-type", NodeType.CAMEL_PROCESSOR, Map.of("name", "anonymous")));
        graph.addEdge(new GraphEdge(
                "route:order-process", "proc:no-type", EdgeType.PROCESSES, Map.of("order", "0")));
        Path subDir = tempDir.resolve("null-label");
        java.nio.file.Files.createDirectories(subDir);
        Path gf = TestGraphs.writeToTempFile(graph, subDir);
        String json = runWithGraph(gf, "route:order-process");
        assertTrue(MAPPER.readTree(json).has("errorFlow"));
    }

    private String run(String routeId) {
        return runWithGraph(graphFile, routeId);
    }

    private String runWithGraph(Path gf, String routeId) {
        StringWriter out = new StringWriter(), err = new StringWriter();
        CommandLine cl = new CommandLine(new GraphRouteContextCommand());
        cl.setOut(new PrintWriter(out));
        cl.setErr(new PrintWriter(err));
        assertEquals(0, cl.execute("--graph-file", gf.toString(), routeId));
        return out.toString().trim();
    }
}
