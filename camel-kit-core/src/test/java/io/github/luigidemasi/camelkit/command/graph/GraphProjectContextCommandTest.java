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

class GraphProjectContextCommandTest {
    static final ObjectMapper MAPPER = new ObjectMapper();
    @TempDir
    Path tempDir;
    Path graphFile;

    @BeforeEach
    void setUp() throws Exception {
        graphFile = TestGraphs.writeToTempFile(TestGraphs.sampleProject(), tempDir);
    }

    @Test
    void returnsPropertyConventions() throws Exception {
        JsonNode r = MAPPER.readTree(run());
        assertTrue(r.has("propertyConventions"));
        assertTrue(r.get("propertyConventions").has("namingStyle"));
    }

    @Test
    void returnsExistingBeans() throws Exception {
        JsonNode r = MAPPER.readTree(run());
        assertTrue(r.has("existingBeans"));
        assertTrue(r.get("existingBeans").size() > 0);
    }

    @Test
    void returnsDependencyVersions() throws Exception {
        JsonNode r = MAPPER.readTree(run());
        assertTrue(r.has("dependencyVersions"));
        assertTrue(r.get("dependencyVersions").size() > 0);
    }

    @Test
    void returnsRouteDirectory() throws Exception {
        JsonNode r = MAPPER.readTree(run());
        assertTrue(r.has("routeDirectory"));
    }

    private String run() {
        StringWriter out = new StringWriter(), err = new StringWriter();
        CommandLine cl = new CommandLine(new GraphProjectContextCommand());
        cl.setOut(new PrintWriter(out));
        cl.setErr(new PrintWriter(err));
        assertEquals(0, cl.execute("--graph-file", graphFile.toString()));
        return out.toString().trim();
    }
}
