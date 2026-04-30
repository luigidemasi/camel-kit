package io.github.luigidemasi.camelkit.command.graph;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import io.github.luigidemasi.camelkit.graph.GraphSerializer;
import io.github.luigidemasi.camelkit.graph.ProjectGraph;

import picocli.CommandLine;
import picocli.CommandLine.Option;

public abstract class GraphQueryCommand implements Callable<Integer> {

    @Option(names = {"--graph-file"}, description = "Path to project graph JSON",
            defaultValue = ".camel-kit/project-graph.json", hidden = true)
    protected Path graphFile;

    @CommandLine.Spec
    protected CommandLine.Model.CommandSpec spec;

    @Override
    public Integer call() {
        if (!Files.exists(graphFile)) {
            err("Graph not found: " + graphFile + ". Run 'graph generate' first.");
            return 1;
        }
        try {
            ProjectGraph graph = GraphSerializer.read(graphFile);
            out(execute(graph));
            return 0;
        } catch (Exception e) {
            err("Invalid graph file: " + graphFile + ". Regenerate with 'graph generate'.");
            return 1;
        }
    }

    protected abstract String execute(ProjectGraph graph) throws Exception;

    protected void out(String message) {
        PrintWriter w = spec.commandLine().getOut();
        w.println(message);
        w.flush();
    }

    protected void err(String message) {
        PrintWriter w = spec.commandLine().getErr();
        w.println(message);
        w.flush();
    }
}
