package io.github.luigidemasi.camelkit.command.graph;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class GraphQueryCommandTest {

    @TempDir Path tempDir;

    @CommandLine.Command(name = "test-query")
    static class TestQueryCommand extends GraphQueryCommand {
        @Override
        protected String execute(io.github.luigidemasi.camelkit.graph.ProjectGraph graph) {
            return "{\"ok\":true,\"nodes\":" + graph.nodeCount() + "}";
        }
    }

    @Test
    void missingGraphFileReturnsExitCode1() {
        StringWriter out = new StringWriter(), err = new StringWriter();
        CommandLine cmd = new CommandLine(new TestQueryCommand());
        cmd.setOut(new PrintWriter(out));
        cmd.setErr(new PrintWriter(err));
        assertEquals(1, cmd.execute("--graph-file", tempDir.resolve("none.json").toString()));
        assertTrue(err.toString().contains("Graph not found"));
    }

    @Test
    void invalidJsonReturnsExitCode1() throws Exception {
        Path bad = tempDir.resolve("bad.json");
        Files.writeString(bad, "not json");
        StringWriter out = new StringWriter(), err = new StringWriter();
        CommandLine cmd = new CommandLine(new TestQueryCommand());
        cmd.setOut(new PrintWriter(out));
        cmd.setErr(new PrintWriter(err));
        assertEquals(1, cmd.execute("--graph-file", bad.toString()));
        assertTrue(err.toString().contains("Invalid graph file"));
    }

    @Test
    void validGraphReturnsExitCode0() throws Exception {
        Path gf = TestGraphs.writeToTempFile(TestGraphs.sampleProject(), tempDir);
        StringWriter out = new StringWriter(), err = new StringWriter();
        CommandLine cmd = new CommandLine(new TestQueryCommand());
        cmd.setOut(new PrintWriter(out));
        cmd.setErr(new PrintWriter(err));
        assertEquals(0, cmd.execute("--graph-file", gf.toString()));
        assertTrue(out.toString().contains("\"ok\":true"));
    }
}
