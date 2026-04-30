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

class GraphParameterlessCommandsTest {
    static final ObjectMapper MAPPER = new ObjectMapper();
    @TempDir
    Path tempDir;
    Path graphFile;

    @BeforeEach
    void setUp() throws Exception {
        graphFile = TestGraphs.writeToTempFile(TestGraphs.sampleProject(), tempDir);
    }

    @Test
    void statsReturnsNodeAndEdgeCounts() throws Exception {
        JsonNode r = MAPPER.readTree(run(new GraphStatsCommand()));
        assertTrue(r.get("available").asBoolean());
        assertTrue(r.get("nodes").asInt() > 0);
        assertTrue(r.get("nodesByType").has("CAMEL_ROUTE"));
    }

    @Test
    void routeTopologyReturnsRoutes() throws Exception {
        JsonNode r = MAPPER.readTree(run(new GraphRouteTopologyCommand()));
        assertTrue(r.has("routes"));
        assertTrue(r.get("routes").size() > 0);
    }

    @Test
    void deadCodeDetectsUnusedArtifact() throws Exception {
        JsonNode r = MAPPER.readTree(run(new GraphDeadCodeCommand()));
        assertTrue(r.get("available").asBoolean());
        boolean found = false;
        for (JsonNode n : r.get("unusedArtifacts")) {
            if (n.get("id").asText().contains("camel-jdbc"))
                found = true;
        }
        assertTrue(found, "camel-jdbc should be unused");
    }

    private String run(GraphQueryCommand cmd) {
        StringWriter out = new StringWriter(), err = new StringWriter();
        CommandLine cl = new CommandLine(cmd);
        cl.setOut(new PrintWriter(out));
        cl.setErr(new PrintWriter(err));
        assertEquals(0, cl.execute("--graph-file", graphFile.toString()), "Failed: " + err);
        return out.toString().trim();
    }
}
