package io.github.luigidemasi.camelkit.command.graph;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import io.github.luigidemasi.camelkit.graph.GraphBuildResult;
import io.github.luigidemasi.camelkit.graph.GraphBuilder;
import io.github.luigidemasi.camelkit.graph.GraphSerializer;
import io.github.luigidemasi.camelkit.graph.ParserDiagnostic;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "generate", description = "Generate or rebuild the project graph from source files")
public class GraphGenerateCommand implements Callable<Integer> {
    @Option(names = {"-o", "--output"}, defaultValue = ".camel-kit/project-graph.json")
    Path output;
    @Option(names = {"--project-dir"}, defaultValue = ".", hidden = true)
    Path projectDir;
    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public Integer call() {
        try {
            Path root = projectDir.toAbsolutePath().normalize();
            PrintWriter err = spec.commandLine().getErr();
            GraphBuildResult result = new GraphBuilder().buildWithDiagnostics(root);
            for (ParserDiagnostic diagnostic : result.warningDiagnostics()) {
                err.println("Warning: " + diagnostic.summary());
            }
            if (!result.successful()) {
                for (ParserDiagnostic diagnostic : result.failedDiagnostics()) {
                    err.println("Error: " + diagnostic.summary());
                }
                err.flush();
                return 1;
            }
            if (result.graph().nodeCount() == 0) {
                err.println("Warning: No source files found");
                err.flush();
            }
            Path outPath = output.isAbsolute() ? output : root.resolve(output);
            Files.createDirectories(outPath.getParent());
            GraphSerializer.write(result.graph(), outPath, root.toString());
            err.println("Graph built: " + result.graph().nodeCount() + " nodes, "
                        + result.graph().edgeCount() + " edges");
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
