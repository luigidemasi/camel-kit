package io.github.luigidemasi.camelkit.graph.parser;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import io.github.luigidemasi.camelkit.graph.ProjectGraph;
import io.github.luigidemasi.camelkit.graph.model.GraphNode;
import io.github.luigidemasi.camelkit.graph.model.NodeType;

public class DataWeaveParser implements GraphParser {

    private static final Pattern DW_VERSION = Pattern.compile("^%dw\\s+(.+)$", Pattern.MULTILINE);
    private static final Pattern INPUT_TYPE = Pattern.compile("^%input\\s+\\S+\\s+(.+)$", Pattern.MULTILINE);
    private static final Pattern OUTPUT_TYPE = Pattern.compile("^%output\\s+(.+)$", Pattern.MULTILINE);
    private static final Pattern FUNCTION = Pattern.compile("^fun\\s+(\\w+)\\s*\\(", Pattern.MULTILINE);
    private static final Pattern FIELD_ACCESS = Pattern.compile("(?:payload|vars)(?:\\.\\w+)+");

    @Override
    public void parse(Path projectRoot, ProjectGraph graph) {
        try {
            Files.walkFileTree(projectRoot, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (file.toString().endsWith(".dwl")) {
                        parseDwlFile(file, projectRoot, graph);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new RuntimeException("Failed to walk project for DataWeave files", e);
        }
    }

    @Override
    public List<String> scannedFiles(Path projectRoot) {
        return GraphParser.findFiles(projectRoot, file -> file.toString().endsWith(".dwl"));
    }

    private void parseDwlFile(Path file, Path projectRoot, ProjectGraph graph) {
        String content;
        try {
            content = Files.readString(file);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read DataWeave file: " + file, e);
        }

        String fileName = file.getFileName().toString();
        String relativePath = projectRoot.relativize(file).toString().replace('\\', '/');
        String nodeId = "dataweave:" + fileName;

        Map<String, String> properties = new HashMap<>();
        properties.put("file", relativePath);

        extractFirst(DW_VERSION, content).ifPresent(v -> properties.put("dwVersion", v.trim()));
        extractFirst(INPUT_TYPE, content).ifPresent(v -> properties.put("inputType", v.trim()));
        extractFirst(OUTPUT_TYPE, content).ifPresent(v -> properties.put("outputType", v.trim()));

        String functions = extractAll(FUNCTION, content).stream()
                .collect(Collectors.joining(","));
        if (!functions.isEmpty()) {
            properties.put("functions", functions);
        }

        String fields = extractAllUnique(FIELD_ACCESS, content).stream()
                .sorted()
                .collect(Collectors.joining(","));
        if (!fields.isEmpty()) {
            properties.put("fields", fields);
        }

        graph.addNode(new GraphNode(nodeId, NodeType.DATAWEAVE_SCRIPT, properties));
    }

    private Optional<String> extractFirst(Pattern pattern, String content) {
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return Optional.of(matcher.group(1));
        }
        return Optional.empty();
    }

    private List<String> extractAll(Pattern pattern, String content) {
        List<String> results = new ArrayList<>();
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            results.add(matcher.group(1));
        }
        return results;
    }

    private Set<String> extractAllUnique(Pattern pattern, String content) {
        Set<String> results = new LinkedHashSet<>();
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            results.add(matcher.group());
        }
        return results;
    }
}
