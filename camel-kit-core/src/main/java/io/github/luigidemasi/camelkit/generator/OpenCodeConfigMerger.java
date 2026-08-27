package io.github.luigidemasi.camelkit.generator;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

final class OpenCodeConfigMerger {

    private static final ObjectMapper JSON = new ObjectMapper();

    void merge(Path configFile, String renderedConfig) throws IOException {
        ObjectNode generated = readObject(renderedConfig, "generated OpenCode configuration");
        ObjectNode merged = Files.isRegularFile(configFile)
                ? readObject(Files.readString(configFile), "existing opencode.json")
                : JSON.createObjectNode();

        generated.fields().forEachRemaining(entry -> {
            if (!"permission".equals(entry.getKey()) && !"mcp".equals(entry.getKey())) {
                merged.set(entry.getKey(), entry.getValue());
            }
        });
        mergeObject(merged, generated, "permission");
        mergeObject(merged, generated, "mcp");

        atomicWrite(configFile,
                JSON.writerWithDefaultPrettyPrinter().writeValueAsString(merged) + System.lineSeparator());
    }

    private void mergeObject(ObjectNode target, ObjectNode generated, String field) throws IOException {
        JsonNode generatedValue = generated.path(field);
        if (!generatedValue.isObject()) {
            throw new IOException("generated OpenCode configuration must contain an object named '" + field + "'");
        }

        JsonNode existingValue = target.get(field);
        if (existingValue != null && !existingValue.isObject()) {
            throw new IOException("existing opencode.json field '" + field + "' must be an object");
        }
        ObjectNode mergedValue = existingValue == null
                ? JSON.createObjectNode()
                : ((ObjectNode) existingValue).deepCopy();
        generatedValue.fields().forEachRemaining(entry -> mergedValue.set(entry.getKey(), entry.getValue()));
        target.set(field, mergedValue);
    }

    private ObjectNode readObject(String content, String source) throws IOException {
        JsonNode parsed = JSON.readTree(content);
        if (parsed == null || !parsed.isObject()) {
            throw new IOException(source + " must be a JSON object");
        }
        return ((ObjectNode) parsed).deepCopy();
    }

    private void atomicWrite(Path target, String content) throws IOException {
        Path temp = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
        try {
            Files.writeString(temp, content);
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
