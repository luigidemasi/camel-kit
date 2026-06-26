package io.github.luigidemasi.camelkit.config;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

final class AgentDescriptorLoader {

    private static final String DEFAULT_REGISTRY_PATH = "agents/registry";
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    private AgentDescriptorLoader() {
    }

    static Map<String, AgentDescriptor> loadDefault() {
        var registryUrl = AgentDescriptorLoader.class.getClassLoader().getResource(DEFAULT_REGISTRY_PATH);
        if (registryUrl == null) {
            throw new IllegalStateException(
                    "Agent descriptor registry not found on classpath: "
                                            + DEFAULT_REGISTRY_PATH);
        }

        return load(registryUrl);
    }

    static Map<String, AgentDescriptor> load(URL registryUrl) {
        FileSystem fileSystem = null;
        boolean closeFileSystem = false;
        try {
            URI uri = registryUrl.toURI();
            Path registryPath;
            if ("jar".equals(uri.getScheme())) {
                URI jarUri = jarFileSystemUri(uri);
                try {
                    fileSystem = FileSystems.newFileSystem(jarUri, Collections.emptyMap());
                    closeFileSystem = true;
                } catch (FileSystemAlreadyExistsException e) {
                    fileSystem = FileSystems.getFileSystem(jarUri);
                }
                registryPath = fileSystem.getPath(jarEntryPath(uri));
            } else {
                registryPath = Path.of(uri);
            }
            return load(registryPath);
        } catch (IOException | URISyntaxException e) {
            throw new IllegalStateException(
                    "Could not load agent descriptor registry from " + registryUrl
                                            + ": " + e.getMessage(),
                    e);
        } finally {
            if (closeFileSystem && fileSystem != null) {
                try {
                    fileSystem.close();
                } catch (IOException e) {
                    // Ignore close errors.
                }
            }
        }
    }

    private static URI jarFileSystemUri(URI resourceUri) {
        String value = resourceUri.toString();
        int separator = value.indexOf("!/");
        if (separator < 0) {
            throw new IllegalStateException("Invalid jar resource URI: " + resourceUri);
        }
        return URI.create(value.substring(0, separator));
    }

    private static String jarEntryPath(URI resourceUri) {
        String value = resourceUri.toString();
        int separator = value.indexOf("!/");
        if (separator < 0) {
            throw new IllegalStateException("Invalid jar resource URI: " + resourceUri);
        }
        return "/" + value.substring(separator + 2);
    }

    static Map<String, AgentDescriptor> load(Path registryDir) {
        if (!Files.isDirectory(registryDir)) {
            throw new IllegalStateException("Agent descriptor registry not found: " + registryDir);
        }

        List<Path> descriptorFiles;
        try (Stream<Path> paths = Files.list(registryDir)) {
            descriptorFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(AgentDescriptorLoader::isDescriptorFile)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not list agent descriptor registry " + registryDir
                                            + ": " + e.getMessage(),
                    e);
        }

        if (descriptorFiles.isEmpty()) {
            throw new IllegalStateException("No agent descriptors found in " + registryDir);
        }

        Map<String, AgentDescriptor> descriptors = new LinkedHashMap<>();
        for (Path descriptorFile : descriptorFiles) {
            AgentDescriptor descriptor = readDescriptor(descriptorFile);
            validateFileName(descriptorFile, descriptor);
            AgentDescriptor previous = descriptors.putIfAbsent(descriptor.id(), descriptor);
            if (previous != null) {
                throw new IllegalStateException(
                        "Duplicate agent descriptor id '" + descriptor.id()
                                                + "' in " + descriptorFile);
            }
        }
        return Collections.unmodifiableMap(descriptors);
    }

    private static AgentDescriptor readDescriptor(Path descriptorFile) {
        try (InputStream input = Files.newInputStream(descriptorFile)) {
            AgentDescriptor descriptor = YAML_MAPPER.readValue(input, AgentDescriptor.class);
            if (descriptor == null) {
                throw new IllegalStateException(descriptorFile + " is empty");
            }
            return descriptor.validate(descriptorFile.toString());
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not read agent descriptor " + descriptorFile
                                            + ": " + e.getMessage(),
                    e);
        }
    }

    private static void validateFileName(Path descriptorFile, AgentDescriptor descriptor) {
        String fileName = descriptorFile.getFileName().toString();
        String expectedYaml = descriptor.id() + ".yaml";
        String expectedYml = descriptor.id() + ".yml";
        if (!fileName.equals(expectedYaml) && !fileName.equals(expectedYml)) {
            throw new IllegalStateException(
                    descriptorFile + " id '" + descriptor.id()
                                            + "' must match descriptor filename");
        }
    }

    private static boolean isDescriptorFile(Path path) {
        String fileName = path.getFileName().toString();
        return fileName.endsWith(".yaml") || fileName.endsWith(".yml");
    }
}
