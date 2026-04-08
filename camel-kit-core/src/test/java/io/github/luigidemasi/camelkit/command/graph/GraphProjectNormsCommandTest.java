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

class GraphProjectNormsCommandTest {
    static final ObjectMapper MAPPER = new ObjectMapper();
    @TempDir Path tempDir;
    Path graphFile;

    @BeforeEach void setUp() throws Exception {
        graphFile = TestGraphs.writeToTempFile(TestGraphs.sampleProject(), tempDir);
    }

    @Test void returnsNaming() throws Exception {
        JsonNode r = MAPPER.readTree(run());
        assertTrue(r.has("naming"));
        assertTrue(r.get("naming").has("detectedPattern"));
        assertTrue(r.get("naming").get("routeIds").size() > 0);
    }

    @Test void returnsErrorHandling() throws Exception {
        JsonNode r = MAPPER.readTree(run());
        assertTrue(r.has("errorHandling"));
        assertTrue(r.get("errorHandling").has("coverage"));
    }

    @Test void returnsProperties() throws Exception {
        JsonNode r = MAPPER.readTree(run());
        assertTrue(r.has("properties"));
        assertTrue(r.get("properties").has("count"));
    }

    @Test void returnsStepCounts() throws Exception {
        JsonNode r = MAPPER.readTree(run());
        assertTrue(r.has("stepCounts"));
        assertTrue(r.get("stepCounts").has("p75"));
    }

    private String run() {
        StringWriter out = new StringWriter(), err = new StringWriter();
        CommandLine cl = new CommandLine(new GraphProjectNormsCommand());
        cl.setOut(new PrintWriter(out)); cl.setErr(new PrintWriter(err));
        assertEquals(0, cl.execute("--graph-file", graphFile.toString()), "Failed: " + err);
        return out.toString().trim();
    }
}
