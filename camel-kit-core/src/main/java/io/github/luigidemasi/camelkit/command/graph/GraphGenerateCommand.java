package io.github.luigidemasi.camelkit.command.graph;

import io.github.luigidemasi.camelkit.graph.GraphBuilder;
import io.github.luigidemasi.camelkit.graph.GraphSerializer;
import io.github.luigidemasi.camelkit.graph.ProjectGraph;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(name = "generate", description = "Generate or rebuild the project graph from source files")
public class GraphGenerateCommand implements Callable<Integer> {
    @Option(names = {"-o", "--output"}, defaultValue = ".camel-kit/project-graph.json") Path output;
    @Option(names = {"--project-dir"}, defaultValue = ".", hidden = true) Path projectDir;
    @CommandLine.Spec CommandLine.Model.CommandSpec spec;

    @Override public Integer call() {
        try {
            Path root = projectDir.toAbsolutePath().normalize();
            PrintWriter err = spec.commandLine().getErr();
            ProjectGraph graph = new GraphBuilder().build(root);
            if (graph.nodeCount() == 0) { err.println("Warning: No source files found"); err.flush(); }
            Path outPath = output.isAbsolute() ? output : root.resolve(output);
            Files.createDirectories(outPath.getParent());
            GraphSerializer.write(graph, outPath, root.toString());
            err.println("Graph built: " + graph.nodeCount() + " nodes, " + graph.edgeCount() + " edges");
            err.println("Written to: " + outPath);
            err.flush();
            return 0;
        } catch (Exception e) {
            spec.commandLine().getErr().println("Error: " + e.getMessage());
            spec.commandLine().getErr().flush();
            return 1;
        }
    }
}
