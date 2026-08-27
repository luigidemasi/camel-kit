package io.github.luigidemasi.camelkit.generator;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import io.github.luigidemasi.camelkit.config.OpenCodeProjectConfig;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

final class OpenCodeConfigMerger {

    private static final ObjectMapper JSON = OpenCodeProjectConfig.newJsonMapper();
    private static final Set<String> PERMISSION_ACTIONS = Set.of("allow", "ask", "deny");

    void validateExisting(Path configFile) throws IOException {
        for (Path candidate : existingConfigurationFiles(configFile)) {
            readExisting(candidate);
        }
    }

    void merge(Path configFile, String renderedConfig) throws IOException {
        ObjectNode generated = readObject(renderedConfig, "generated OpenCode configuration", false);
        requireGeneratedObject(generated, "permission");
        requireGeneratedObject(generated, "mcp");

        List<Path> targets = existingConfigurationFiles(configFile);
        if (targets.isEmpty()) {
            writeAll(List.of(new PendingWrite(configFile, pretty(generated) + System.lineSeparator())));
            return;
        }

        // Validate every candidate before changing any of them.
        List<ExistingConfiguration> configurations = new ArrayList<>(targets.size());
        for (Path target : targets) {
            configurations.add(readExisting(target));
        }

        List<PendingWrite> writes = new ArrayList<>();
        for (int i = 0; i < configurations.size() - 1; i++) {
            ExistingConfiguration configuration = configurations.get(i);
            String updated = removeManagedEntries(configuration.content(), generated);
            if (!updated.equals(configuration.content())) {
                writes.add(new PendingWrite(configuration.path(), updated));
            }
        }

        ExistingConfiguration highest = configurations.get(configurations.size() - 1);
        String merged = mergeGenerated(highest, generated);
        if (!merged.equals(highest.content())) {
            writes.add(new PendingWrite(highest.path(), merged));
        }
        writeAll(writes);
    }

    private String removeManagedEntries(String content, ObjectNode generated) throws IOException {
        String body = contentWithoutBom(content);
        if (body.isEmpty()) {
            return content;
        }
        JsoncObjectEditor editor = new JsoncObjectEditor(body);
        removeObjectEntries(editor, generated, "permission");
        removeObjectEntries(editor, generated, "mcp");
        return bom(content) + editor.content();
    }

    private void removeObjectEntries(JsoncObjectEditor editor, ObjectNode generated, String field) {
        ObjectNode generatedObject = (ObjectNode) generated.path(field);
        editor.removeObjectMembers(field, generatedObject.fieldNames());
    }

    private String mergeGenerated(ExistingConfiguration existing, ObjectNode generated) throws IOException {
        if (isCurrentAndOrdered(existing, generated)) {
            return existing.content();
        }

        String prefix = bom(existing.content());
        String content = contentWithoutBom(existing.content());
        if (content.isEmpty()) {
            return prefix + pretty(generated) + System.lineSeparator();
        }

        JsoncObjectEditor editor = new JsoncObjectEditor(content);
        if (existing.scalarPermission()) {
            editor.replaceRootMemberValue("permission", existing.config().path("permission"));
        }

        generated.fields().forEachRemaining(entry -> {
            if (!"permission".equals(entry.getKey()) && !"mcp".equals(entry.getKey())) {
                editor.upsertRootMember(entry.getKey(), entry.getValue());
            }
        });
        mergeObject(editor, existing.config(), generated, "permission");
        mergeObject(editor, existing.config(), generated, "mcp");
        return prefix + editor.content();
    }

    private boolean isCurrentAndOrdered(ExistingConfiguration existing, ObjectNode generated) {
        if (existing.scalarPermission()) {
            return false;
        }

        ObjectNode config = existing.config();
        var fields = generated.fields();
        while (fields.hasNext()) {
            var field = fields.next();
            if ("permission".equals(field.getKey()) || "mcp".equals(field.getKey())) {
                if (!containsGeneratedEntries(config.path(field.getKey()), (ObjectNode) field.getValue())) {
                    return false;
                }
            } else if (!field.getValue().equals(config.get(field.getKey()))) {
                return false;
            }
        }
        return generatedPermissionsTrail(config.path("permission"), (ObjectNode) generated.path("permission"));
    }

    private boolean containsGeneratedEntries(JsonNode existing, ObjectNode generated) {
        if (!existing.isObject()) {
            return false;
        }
        var fields = generated.fields();
        while (fields.hasNext()) {
            var field = fields.next();
            if (!field.getValue().equals(existing.get(field.getKey()))) {
                return false;
            }
        }
        return true;
    }

    private boolean generatedPermissionsTrail(JsonNode existing, ObjectNode generated) {
        if (!existing.isObject()) {
            return false;
        }
        List<String> existingNames = new ArrayList<>();
        existing.fieldNames().forEachRemaining(existingNames::add);
        List<String> generatedNames = new ArrayList<>();
        generated.fieldNames().forEachRemaining(generatedNames::add);
        int generatedStart = existingNames.size() - generatedNames.size();
        return generatedStart >= 0
                && existingNames.subList(generatedStart, existingNames.size()).equals(generatedNames);
    }

    private void mergeObject(
            JsoncObjectEditor editor,
            ObjectNode existing,
            ObjectNode generated,
            String field) {
        ObjectNode generatedObject = (ObjectNode) generated.path(field);
        if (!existing.has(field)) {
            editor.appendRootMember(field, generatedObject);
            return;
        }
        editor.removeObjectMembers(field, generatedObject.fieldNames());
        editor.appendObjectMembers(field, generatedObject);
    }

    private ExistingConfiguration readExisting(Path configFile) throws IOException {
        String content;
        try {
            content = Files.readString(configFile);
        } catch (IOException e) {
            throw new InvalidAgentConfigurationException(
                    "existing " + displayName(configFile) + " could not be read", e);
        }

        String body = contentWithoutBom(content);
        ObjectNode existing = body.isEmpty()
                ? JSON.createObjectNode()
                : readObject(body, "existing " + displayName(configFile), true);
        JsonNode permission = existing.get("permission");
        boolean scalarPermission = permission != null && permission.isTextual()
                && PERMISSION_ACTIONS.contains(permission.asText());
        if (scalarPermission) {
            OpenCodeProjectConfig.normalizeScalarPermission(existing);
        }
        requireObjectField(configFile, existing, "permission");
        requireObjectField(configFile, existing, "mcp");
        return new ExistingConfiguration(configFile, content, existing, scalarPermission);
    }

    private boolean hasExistingConfiguration(Path configFile) throws InvalidAgentConfigurationException {
        if (!Files.exists(configFile, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        if (!Files.isRegularFile(configFile, LinkOption.NOFOLLOW_LINKS)) {
            throw new InvalidAgentConfigurationException(
                    "existing " + displayName(configFile) + " must be a regular file");
        }
        return true;
    }

    private List<Path> existingConfigurationFiles(Path configFile) throws InvalidAgentConfigurationException {
        Path projectDir = configFile.getParent();
        if (projectDir == null) {
            projectDir = Path.of("");
        }
        List<Path> existing = new ArrayList<>();
        for (Path candidate : OpenCodeProjectConfig.files(projectDir)) {
            if (hasExistingConfiguration(candidate)) {
                existing.add(candidate);
            }
        }
        return existing;
    }

    private void requireObjectField(Path configFile, ObjectNode existing, String field)
            throws InvalidAgentConfigurationException {
        JsonNode value = existing.get(field);
        if (value != null && !value.isObject()) {
            throw new InvalidAgentConfigurationException(
                    "existing " + displayName(configFile) + " field '" + field + "' must be an object");
        }
    }

    private void requireGeneratedObject(ObjectNode generated, String field) throws IOException {
        if (!generated.path(field).isObject()) {
            throw new IOException("generated OpenCode configuration must contain an object named '" + field + "'");
        }
    }

    private String displayName(Path configFile) {
        Path parent = configFile.getParent();
        if (parent != null && ".opencode".equals(String.valueOf(parent.getFileName()))) {
            return ".opencode/" + configFile.getFileName();
        }
        return configFile.getFileName().toString();
    }

    private ObjectNode readObject(String content, String source, boolean existing) throws IOException {
        JsonNode parsed;
        try {
            parsed = JSON.readTree(content);
        } catch (JsonProcessingException e) {
            String message = source + " is not valid JSON or JSONC";
            if (existing) {
                throw new InvalidAgentConfigurationException(message, e);
            }
            throw new IOException(message, e);
        }
        if (parsed == null || !parsed.isObject()) {
            String message = source + " must be a JSON object";
            if (existing) {
                throw new InvalidAgentConfigurationException(message);
            }
            throw new IOException(message);
        }
        return ((ObjectNode) parsed).deepCopy();
    }

    private String pretty(JsonNode value) {
        try {
            return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not render OpenCode configuration", e);
        }
    }

    private String bom(String content) {
        return content.startsWith("\uFEFF") ? "\uFEFF" : "";
    }

    private String contentWithoutBom(String content) {
        return content.startsWith("\uFEFF") ? content.substring(1) : content;
    }

    private void writeAll(List<PendingWrite> writes) throws IOException {
        List<StagedWrite> staged = new ArrayList<>(writes.size());
        try {
            for (PendingWrite write : writes) {
                staged.add(stage(write));
            }
            // Keep generated definitions available if a later, lower-precedence replacement fails.
            for (int i = staged.size() - 1; i >= 0; i--) {
                commit(staged.get(i));
            }
        } catch (IOException failure) {
            cleanup(staged, failure);
            throw failure;
        }
        cleanup(staged, null);
    }

    private StagedWrite stage(PendingWrite write) throws IOException {
        Path temp = Files.createTempFile(
                write.target().getParent(), write.target().getFileName().toString(), ".tmp");
        try {
            Files.writeString(temp, write.content());
            return new StagedWrite(write.target(), temp);
        } catch (IOException failure) {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    private void commit(StagedWrite write) throws IOException {
        try {
            Files.move(write.temp(), write.target(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(write.temp(), write.target(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void cleanup(List<StagedWrite> staged, IOException failure) throws IOException {
        IOException cleanupFailure = null;
        for (StagedWrite write : staged) {
            try {
                Files.deleteIfExists(write.temp());
            } catch (IOException e) {
                if (failure != null) {
                    failure.addSuppressed(e);
                } else if (cleanupFailure == null) {
                    cleanupFailure = e;
                } else {
                    cleanupFailure.addSuppressed(e);
                }
            }
        }
        if (cleanupFailure != null) {
            throw cleanupFailure;
        }
    }

    private record ExistingConfiguration(
            Path path,
            String content,
            ObjectNode config,
            boolean scalarPermission) {
    }

    private record PendingWrite(Path target, String content) {
    }

    private record StagedWrite(Path target, Path temp) {
    }
}
