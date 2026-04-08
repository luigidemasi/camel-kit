package io.github.luigidemasi.camelkit.command.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class GraphRouteContextCommandTest {
    static final ObjectMapper MAPPER = new ObjectMapper();
    @TempDir Path tempDir;
    Path graphFile;

    @BeforeEach void setUp() throws Exception { graphFile = TestGraphs.writeToTempFile(TestGraphs.sampleProject(), tempDir); }

    @Test void returnsRouteId() throws Exception { assertEquals("route:order-process", MAPPER.readTree(run("route:order-process")).get("route").asText()); }
    @Test void returnsUpstream() throws Exception { assertTrue(MAPPER.readTree(run("route:order-process")).has("upstream")); }
    @Test void returnsDownstream() throws Exception { assertTrue(MAPPER.readTree(run("route:order-process")).has("downstream")); }
    @Test void returnsEndpointClassification() throws Exception {
        JsonNode ep = MAPPER.readTree(run("route:order-process")).get("endpoints");
        assertTrue(ep.has("internal")); assertTrue(ep.has("externalInfra")); assertTrue(ep.has("externalApi"));
    }
    @Test void classifiesKafkaAsInfra() throws Exception {
        JsonNode infra = MAPPER.readTree(run("route:order-process")).get("endpoints").get("externalInfra");
        boolean found = false;
        for (JsonNode n : infra) if (n.asText().contains("kafka")) found = true;
        assertTrue(found);
    }
    @Test void returnsErrorFlow() throws Exception { assertTrue(MAPPER.readTree(run("route:order-process")).has("errorFlow")); }

    private String run(String routeId) {
        StringWriter out = new StringWriter(), err = new StringWriter();
        CommandLine cl = new CommandLine(new GraphRouteContextCommand());
        cl.setOut(new PrintWriter(out)); cl.setErr(new PrintWriter(err));
        assertEquals(0, cl.execute("--graph-file", graphFile.toString(), routeId));
        return out.toString().trim();
    }
}
