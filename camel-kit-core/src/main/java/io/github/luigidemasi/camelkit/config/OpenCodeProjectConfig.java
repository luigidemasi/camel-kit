package io.github.luigidemasi.camelkit.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * OpenCode's supported project configuration files and JSONC parser.
 */
public final class OpenCodeProjectConfig {

    private static final List<String> FILES = List.of(
            "opencode.json",
            "opencode.jsonc",
            ".opencode/opencode.json",
            ".opencode/opencode.jsonc");

    private OpenCodeProjectConfig() {
    }

    public static List<Path> files(Path projectDir) {
        return FILES.stream().map(projectDir::resolve).toList();
    }

    /**
     * Existing configuration files (including dangling symbolic links) in precedence order. Files aliased through
     * symbolic links are one layer and keep only their highest-precedence name.
     */
    public static List<Path> existingFiles(Path projectDir) throws IOException {
        Map<Path, Path> byRealPath = new LinkedHashMap<>();
        for (Path candidate : files(projectDir)) {
            if (!Files.exists(candidate) && !Files.isSymbolicLink(candidate)) {
                continue;
            }
            Path key = Files.exists(candidate) ? candidate.toRealPath() : candidate;
            byRealPath.remove(key);
            byRealPath.put(key, candidate);
        }
        return List.copyOf(byRealPath.values());
    }

    public static ObjectMapper newJsonMapper() {
        return new ObjectMapper(
                JsonFactory.builder()
                        .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
                        .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
                        .build())
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    public static void normalizeScalarPermission(ObjectNode config) {
        JsonNode permission = config.get("permission");
        if (permission != null && permission.isTextual()) {
            ObjectNode expanded = JsonNodeFactory.instance.objectNode();
            expanded.set("*", permission);
            config.set("permission", expanded);
        }
    }
}
