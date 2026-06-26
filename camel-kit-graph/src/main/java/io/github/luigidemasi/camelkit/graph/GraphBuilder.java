package io.github.luigidemasi.camelkit.graph;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.*;

import io.github.luigidemasi.camelkit.graph.model.GraphEdge;
import io.github.luigidemasi.camelkit.graph.model.GraphNode;
import io.github.luigidemasi.camelkit.graph.parser.*;

public class GraphBuilder {

    private static final long DEFAULT_PARSER_TIMEOUT = 60;
    private static final TimeUnit DEFAULT_PARSER_TIMEOUT_UNIT = TimeUnit.SECONDS;

    private final GraphParser pomParser;
    private final List<GraphParser> remainingParsers;
    private final long parserTimeout;
    private final TimeUnit parserTimeoutUnit;

    public GraphBuilder() {
        this(new PomParser(), List.of(
                new JavaGraphParser(),
                new GroovyGraphParser(),
                new XmlRouteParser(),
                new MuleXmlFlowParser(),
                new DataWeaveParser(),
                new BizTalkParser(),
                new YamlRouteParser(),
                new ConfigParser()),
             DEFAULT_PARSER_TIMEOUT,
             DEFAULT_PARSER_TIMEOUT_UNIT);
    }

    GraphBuilder(
                 GraphParser pomParser, List<GraphParser> remainingParsers,
                 long parserTimeout, TimeUnit parserTimeoutUnit) {
        this.pomParser = Objects.requireNonNull(pomParser, "pomParser");
        this.remainingParsers = List.copyOf(Objects.requireNonNull(remainingParsers, "remainingParsers"));
        if (parserTimeout <= 0) {
            throw new IllegalArgumentException("parserTimeout must be positive");
        }
        this.parserTimeout = parserTimeout;
        this.parserTimeoutUnit = Objects.requireNonNull(parserTimeoutUnit, "parserTimeoutUnit");
    }

    public ProjectGraph build(Path projectRoot) {
        return buildWithDiagnostics(projectRoot).graph();
    }

    public GraphBuildResult buildWithDiagnostics(Path projectRoot) {
        ProjectGraph graph = new ProjectGraph();
        List<ParserDiagnostic> diagnostics = new ArrayList<>();
        List<Path> projectFiles = projectFiles(projectRoot);

        // Run PomParser first so MAVEN_ARTIFACT nodes are available for all other parsers
        ParserRun pomRun
                = runParser(pomParser, projectRoot, new ProjectGraph(),
                        parserTask(pomParser, projectRoot, projectFiles));
        diagnostics.add(pomRun.diagnostic());
        if (pomRun.diagnostic().successful()) {
            mergeDeterministically(graph, pomRun.graph());
        }

        ExecutorService executor = Executors.newFixedThreadPool(
                Math.max(1, Math.min(remainingParsers.size(), Runtime.getRuntime().availableProcessors())));
        try {
            ProjectGraph baseGraph = new ProjectGraph();
            baseGraph.merge(graph);

            List<ParserTask> tasks = remainingParsers.stream()
                    .map(parser -> parserTask(parser, projectRoot, projectFiles))
                    .toList();

            List<ParserFuture> futures = tasks.stream()
                    .map(task -> {
                        long submittedAt = System.nanoTime();
                        return new ParserFuture(
                                task,
                                executor.submit(
                                        () -> runParser(task.parser(), projectRoot, baseGraph, task)),
                                submittedAt);
                    })
                    .toList();

            for (int i = 0; i < futures.size(); i++) {
                ParserFuture parserFuture = futures.get(i);
                try {
                    ParserRun parserRun = parserFuture.future().get(parserTimeout, parserTimeoutUnit);
                    diagnostics.add(parserRun.diagnostic());
                    if (parserRun.diagnostic().successful()) {
                        mergeDeterministically(graph, parserRun.graph());
                    }
                } catch (ExecutionException e) {
                    ParserDiagnostic diagnostic = failureDiagnostic(
                            parserFuture.task().parser(),
                            parserFuture.task().scannedFiles(),
                            e.getCause(),
                            elapsedMillis(parserFuture.submittedAt()));
                    diagnostics.add(diagnostic);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    parserFuture.future().cancel(true);
                    diagnostics.add(
                            interruptedDiagnostic(parserFuture.task().parser(), parserFuture.task().scannedFiles()));
                    addSkippedAfterInterruptionDiagnostics(futures, i + 1, diagnostics);
                    break;
                } catch (TimeoutException e) {
                    parserFuture.future().cancel(true);
                    diagnostics
                            .add(timeoutDiagnostic(parserFuture.task().parser(), parserFuture.task().scannedFiles()));
                }
            }
        } finally {
            executor.shutdownNow();
        }

        CrossLinker crossLinker = new CrossLinker();
        crossLinker.link(graph);

        PropertyBindingParser propertyBindingParser = new PropertyBindingParser();
        propertyBindingParser.parse(graph, RuntimeDetector.detect(graph));
        propertyBindingParser.resolvePlaceholders(graph);

        crossLinker.expandInterfaces(graph);

        return new GraphBuildResult(graph, diagnostics);
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

    private ParserRun runParser(
            GraphParser parser, Path projectRoot, ProjectGraph baseGraph,
            ParserTask task) {
        long startedAt = System.nanoTime();
        ProjectGraph delta = new ProjectGraph();
        try {
            ProjectGraph fragment = parser.parseFragment(projectRoot, baseGraph, task.scannedFilePaths());
            delta = deltaFrom(fragment, baseGraph);
            ParserDiagnostic diagnostic = diagnostic(
                    parser,
                    task.scannedFiles(),
                    List.of(),
                    collectWarnings(parser),
                    List.of(),
                    false,
                    delta.nodeCount(),
                    delta.edgeCount(),
                    elapsedMillis(startedAt));
            return new ParserRun(delta, diagnostic);
        } catch (RuntimeException e) {
            ParserDiagnostic diagnostic = failureDiagnostic(parser, task.scannedFiles(), e, elapsedMillis(startedAt));
            return new ParserRun(delta, diagnostic);
        }
    }

    private List<Path> projectFiles(Path projectRoot) {
        try {
            return GraphParser.projectFiles(projectRoot);
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private ParserTask parserTask(GraphParser parser, Path projectRoot, List<Path> projectFiles) {
        try {
            List<Path> scannedFilePaths = parser.scannedFilePaths(projectRoot, projectFiles);
            return new ParserTask(
                    parser,
                    scannedFilePaths,
                    GraphParser.relativeFileNames(projectRoot, scannedFilePaths));
        } catch (RuntimeException e) {
            return new ParserTask(parser, List.of(), List.of());
        }
    }

    private List<String> collectWarnings(GraphParser parser) {
        try {
            return parser.warnings();
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private ProjectGraph deltaFrom(ProjectGraph fragment, ProjectGraph baseGraph) {
        ProjectGraph delta = new ProjectGraph();
        Set<GraphEdge> baseEdges = baseGraph.getEdgeSet();

        fragment.getNodes().values().stream()
                .filter(node -> !Objects.equals(baseGraph.getNode(node.id()), node))
                .forEach(delta::addNode);

        fragment.getEdges().stream()
                .filter(edge -> !baseEdges.contains(edge))
                .forEach(delta::addEdge);

        return delta;
    }

    private void mergeDeterministically(ProjectGraph target, ProjectGraph source) {
        source.getNodes().values().stream()
                .sorted(Comparator.comparing(GraphNode::id))
                .forEach(target::addNode);
        source.getEdges().stream()
                .sorted(GraphBuilder::compareEdges)
                .forEach(target::addEdge);
    }

    private static int compareEdges(GraphEdge left, GraphEdge right) {
        return Comparator.comparing(GraphEdge::from)
                .thenComparing(GraphEdge::to)
                .thenComparing(edge -> edge.type().name())
                .thenComparing(edge -> sortedProperties(edge.properties()))
                .compare(left, right);
    }

    private static String sortedProperties(Map<String, String> properties) {
        return properties.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .toList()
                .toString();
    }

    private ParserDiagnostic failureDiagnostic(
            GraphParser parser, List<String> scannedFiles, Throwable failure,
            long durationMillis) {
        String message = failure == null || failure.getMessage() == null
                ? "Parser failed"
                : failure.getMessage();
        return diagnostic(
                parser,
                scannedFiles,
                List.of(),
                collectWarnings(parser),
                List.of(message),
                false,
                0,
                0,
                durationMillis);
    }

    private ParserDiagnostic interruptedDiagnostic(GraphParser parser, List<String> scannedFiles) {
        return diagnostic(
                parser,
                scannedFiles,
                List.of(),
                List.of(),
                List.of("Parser execution was interrupted"),
                false,
                0,
                0,
                0);
    }

    private void addSkippedAfterInterruptionDiagnostics(
            List<ParserFuture> futures, int startIndex, List<ParserDiagnostic> diagnostics) {
        for (int i = startIndex; i < futures.size(); i++) {
            ParserFuture parserFuture = futures.get(i);
            parserFuture.future().cancel(true);
            diagnostics.add(skippedAfterInterruptionDiagnostic(
                    parserFuture.task().parser(),
                    parserFuture.task().scannedFiles()));
        }
    }

    private ParserDiagnostic skippedAfterInterruptionDiagnostic(GraphParser parser, List<String> scannedFiles) {
        return diagnostic(
                parser,
                scannedFiles,
                scannedFiles,
                List.of(),
                List.of("Parser execution skipped after graph build interruption"),
                false,
                0,
                0,
                0);
    }

    private ParserDiagnostic timeoutDiagnostic(GraphParser parser, List<String> scannedFiles) {
        return diagnostic(
                parser,
                scannedFiles,
                List.of(),
                List.of(),
                List.of(),
                true,
                0,
                0,
                parserTimeoutUnit.toMillis(parserTimeout));
    }

    private ParserDiagnostic diagnostic(
            GraphParser parser,
            List<String> scannedFiles,
            List<String> skippedFiles,
            List<String> warnings,
            List<String> failures,
            boolean timedOut,
            int nodesAdded,
            int edgesAdded,
            long durationMillis) {
        return new ParserDiagnostic(
                parserName(parser),
                scannedFiles,
                skippedFiles,
                warnings,
                failures,
                timedOut,
                nodesAdded,
                edgesAdded,
                durationMillis);
    }

    private String parserName(GraphParser parser) {
        String simpleName = parser.getClass().getSimpleName();
        if (simpleName == null || simpleName.isBlank()) {
            return parser.getClass().getName();
        }
        return simpleName;
    }

    private long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    private record ParserTask(GraphParser parser, List<Path> scannedFilePaths, List<String> scannedFiles) {
    }

    private record ParserFuture(ParserTask task, Future<ParserRun> future, long submittedAt) {
    }

    private record ParserRun(ProjectGraph graph, ParserDiagnostic diagnostic) {
    }
}
