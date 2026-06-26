package io.github.luigidemasi.camelkit.graph.parser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.*;

import io.github.luigidemasi.camelkit.graph.ProjectGraph;
import io.github.luigidemasi.camelkit.graph.model.*;

public class ConfigParser implements GraphParser {

    @Override
    public void parse(Path projectRoot, ProjectGraph graph) {
        parseFiles(projectRoot, scannedFilePaths(projectRoot, GraphParser.projectFiles(projectRoot)), graph);
    }

    @Override
    public List<String> scannedFiles(Path projectRoot) {
        return GraphParser.findFiles(projectRoot, this::isConfigFile);
    }

    @Override
    public List<Path> scannedFilePaths(Path projectRoot, List<Path> projectFiles) {
        return GraphParser.findFilePaths(projectRoot, projectFiles, this::isConfigFile);
    }

    @Override
    public void parseFiles(Path projectRoot, List<Path> files, ProjectGraph graph) {
        files.forEach(file -> parseProperties(file, projectRoot, graph));
    }

    private boolean isConfigFile(Path file) {
        String name = file.getFileName().toString();
        return name.equals("application.properties") || name.matches("application-.*\\.properties");
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
