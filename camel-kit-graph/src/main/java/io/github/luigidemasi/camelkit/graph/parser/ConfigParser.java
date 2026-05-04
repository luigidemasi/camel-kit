package io.github.luigidemasi.camelkit.graph.parser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

import io.github.luigidemasi.camelkit.graph.ProjectGraph;
import io.github.luigidemasi.camelkit.graph.model.*;

public class ConfigParser implements GraphParser {

    @Override
    public void parse(Path projectRoot, ProjectGraph graph) {
        try {
            Files.walkFileTree(projectRoot, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    String name = file.getFileName().toString();
                    if (name.equals("application.properties") || name.matches("application-.*\\.properties")) {
                        parseProperties(file, projectRoot, graph);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new RuntimeException("Failed to walk project for config files", e);
        }
    }

    private void parseProperties(Path file, Path projectRoot, ProjectGraph graph) {
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            props.load(in);
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse properties: " + file, e);
        }

        String relativePath = projectRoot.relativize(file).toString();
        String profile = extractProfile(file.getFileName().toString());

        props.forEach((key, value) -> {
            String keyStr = key.toString();
            String nodeId = "config:" + keyStr;
            Map<String, String> nodeProps = new HashMap<>();
            nodeProps.put("key", keyStr);
            nodeProps.put("value", value.toString());
            nodeProps.put("file", relativePath);
            if (profile != null) {
                nodeProps.put("profile", profile);
            }
            graph.addNode(new GraphNode(nodeId, NodeType.CONFIG_PROPERTY, nodeProps));
        });
    }

    private String extractProfile(String fileName) {
        if (fileName.startsWith("application-") && fileName.endsWith(".properties")) {
            return fileName.substring("application-".length(),
                    fileName.length() - ".properties".length());
        }
        return null;
    }
}
