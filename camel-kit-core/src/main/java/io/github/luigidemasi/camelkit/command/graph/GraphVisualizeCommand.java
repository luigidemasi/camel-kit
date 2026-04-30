package io.github.luigidemasi.camelkit.command.graph;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import io.github.luigidemasi.camelkit.graph.GraphSerializer;
import io.github.luigidemasi.camelkit.graph.GraphVisualizer;
import io.github.luigidemasi.camelkit.graph.ProjectGraph;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "visualize", description = "Generate interactive HTML graph visualization")
public class GraphVisualizeCommand implements Callable<Integer> {
    @Option(names = {"-i", "--input"}, defaultValue = ".camel-kit/project-graph.json")
    Path input;
    @Option(names = {"-o", "--output"}, defaultValue = ".camel-kit/project-graph.html")
    Path output;
    @Option(names = {"-l", "--library"}, description = "Visualization library: ${COMPLETION-CANDIDATES}",
            defaultValue = "cytoscape", completionCandidates = LibCandidates.class)
    String library;
    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    static class LibCandidates extends ArrayList<String> {
        LibCandidates() {
            super(List.of("cytoscape", "d3", "vis-network", "antv-g6"));
        }
    }

    @Override
    public Integer call() {
        PrintWriter err = spec.commandLine().getErr();
        if (!Files.exists(input)) {
            err.println("Graph not found: " + input + ". Run 'graph generate' first.");
            err.flush();
            return 1;
        }
        try {
            ProjectGraph graph = GraphSerializer.read(input);
            err.println("Reading: " + input + " (" + graph.nodeCount() + " nodes, " + graph.edgeCount() + " edges)");
            String html = GraphVisualizer.generate(graph, library);
            Files.createDirectories(output.getParent());
            Files.writeString(output, html);
            err.println("Written to: " + output + " (library: " + library + ")");
            err.flush();
            return 0;
        } catch (Exception e) {
            err.println("Error: " + e.getMessage());
            err.flush();
            return 1;
        }
    }
}
