package io.github.luigidemasi.camelkit.command.graph;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.*;

class GraphParameterizedCommandsTest {
    static final ObjectMapper MAPPER = new ObjectMapper();
    @TempDir
    Path tempDir;
    Path graphFile;

    @BeforeEach
    void setUp() throws Exception {
        graphFile = TestGraphs.writeToTempFile(TestGraphs.sampleProject(), tempDir);
    }

    @Test
    void findByType() throws Exception {
        JsonNode r = MAPPER.readTree(run(new GraphFindCommand(), "--type", "CAMEL_ROUTE"));
        assertTrue(r.get("found").asBoolean());
        assertEquals(2, r.get("total").asInt());
    }

    @Test
    void findWithPattern() throws Exception {
        JsonNode r = MAPPER.readTree(run(new GraphFindCommand(), "--type", "CAMEL_ROUTE", "--name", ".*process.*"));
        assertEquals(1, r.get("total").asInt());
    }

    @Test
    void neighborsReturnsConnected() throws Exception {
        JsonNode r = MAPPER.readTree(run(new GraphNeighborsCommand(), "route:order-process"));
        assertTrue(r.get("found").asBoolean());
        assertTrue(r.has("nodes"));
        assertTrue(r.has("edges"));
    }

    @Test
    void pathBetweenNodes() throws Exception {
        JsonNode r = MAPPER.readTree(run(new GraphPathCommand(), "route:order-process", "endpoint:kafka:orders-out"));
        assertTrue(r.get("found").asBoolean());
        assertTrue(r.get("length").asInt() > 0);
    }

    @Test
    void subgraphReturnsNodesAndEdges() throws Exception {
        JsonNode r = MAPPER.readTree(run(new GraphSubgraphCommand(), "route:order-process"));
        assertTrue(r.has("nodes"));
        assertTrue(r.has("edges"));
        assertTrue(r.get("nodes").size() > 0);
    }

    @Test
    void routeFlowTraces() throws Exception {
        JsonNode r = MAPPER.readTree(run(new GraphRouteFlowCommand(), "route:order-process"));
        assertTrue(r.get("found").asBoolean());
        assertTrue(r.has("steps"));
    }

    @Test
    void impactReturnsGroupedByType() throws Exception {
        JsonNode r = MAPPER.readTree(run(new GraphImpactCommand(), "route:order-process"));
        assertTrue(r.get("found").asBoolean());
        assertTrue(r.has("byType"));
    }

    private String run(GraphQueryCommand cmd, String... args) {
        StringWriter out = new StringWriter(), err = new StringWriter();
        CommandLine cl = new CommandLine(cmd);
        cl.setOut(new PrintWriter(out));
        cl.setErr(new PrintWriter(err));
        String[] full = new String[args.length + 2];
        full[0] = "--graph-file";
        full[1] = graphFile.toString();
        System.arraycopy(args, 0, full, 2, args.length);
        assertEquals(0, cl.execute(full), "Failed: " + err);
        return out.toString().trim();
    }
}
