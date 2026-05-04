package io.github.luigidemasi.camelkit.graph;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.*;

import io.github.luigidemasi.camelkit.graph.parser.*;

public class GraphBuilder {

    private final List<GraphParser> parsers = List.of(
            new JavaGraphParser(),
            new GroovyGraphParser(),
            new XmlRouteParser(),
            new MuleXmlFlowParser(),
            new DataWeaveParser(),
            new BizTalkParser(),
            new YamlRouteParser(),
            new PomParser(),
            new ConfigParser());

    public ProjectGraph build(Path projectRoot) {
        ProjectGraph graph = new ProjectGraph();

        ExecutorService executor = Executors.newFixedThreadPool(
                Math.min(parsers.size(), Runtime.getRuntime().availableProcessors()));
        try {
            List<Future<?>> futures = parsers.stream()
                    .<Future<?>>map(parser -> executor.submit(() -> parser.parse(projectRoot, graph)))
                    .toList();

            for (Future<?> future : futures) {
                try {
                    future.get(60, TimeUnit.SECONDS);
                } catch (ExecutionException e) {
                    System.err.println("Parser failed: " + e.getCause().getMessage());
                } catch (InterruptedException | TimeoutException e) {
                    Thread.currentThread().interrupt();
                }
            }
        } finally {
            executor.shutdown();
        }

        CrossLinker crossLinker = new CrossLinker();
        crossLinker.link(graph);

        PropertyBindingParser propertyBindingParser = new PropertyBindingParser();
        propertyBindingParser.parse(graph, null);
        propertyBindingParser.resolvePlaceholders(graph);

        crossLinker.expandInterfaces(graph);

        return graph;
    }

    public void buildAndSerialize(Path projectRoot, Path outputFile) {
        ProjectGraph graph = build(projectRoot);
        try {
            Files.createDirectories(outputFile.getParent());
            GraphSerializer.write(graph, outputFile, projectRoot.toAbsolutePath().toString());
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize graph to " + outputFile, e);
        }
    }
}
