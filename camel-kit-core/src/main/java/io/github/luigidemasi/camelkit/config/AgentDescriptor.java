package io.github.luigidemasi.camelkit.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Resource-backed description of a supported AI coding agent.
 */
public record AgentDescriptor(
        String id,
        String displayName,
        String commandDirectory,
        String skillsDirectory,
        Boolean generatesCommandStubs,
        String commandFileFormat,
        String argumentPlaceholder,
        String mcpConfigPath,
        String mcpConfigTemplatePath,
        String mcpConfigFormat,
        String mcpServerContainerKey,
        String description,
        String generatorStrategy,
        String dispatchTemplatePath,
        List<TemplateInstall> templates,
        Boolean supportsSubagents,
        Boolean supportsTraits,
        List<String> capabilities) {

    public AgentDescriptor {
        generatesCommandStubs = generatesCommandStubs == null || generatesCommandStubs;
        if ((skillsDirectory == null || skillsDirectory.isBlank())
                && commandDirectory != null && commandDirectory.contains("/")) {
            skillsDirectory = commandDirectory.substring(0, commandDirectory.lastIndexOf('/')) + "/skills";
        }
        if (mcpConfigFormat == null || mcpConfigFormat.isBlank()) {
            mcpConfigFormat = "json";
        }
        templates = immutableList(templates);
        capabilities = immutableList(capabilities);
    }

    AgentDescriptor validate(String source) {
        requireText(id, "id", source);
        requireText(displayName, "displayName", source);
        requireText(skillsDirectory, "skillsDirectory", source);
        requireBoolean(generatesCommandStubs, "generatesCommandStubs", source);
        if (generatesCommandStubs) {
            requireText(commandDirectory, "commandDirectory", source);
            requireText(commandFileFormat, "commandFileFormat", source);
            requireText(argumentPlaceholder, "argumentPlaceholder", source);
        }
        requireText(mcpConfigPath, "mcpConfigPath", source);
        requireText(mcpConfigTemplatePath, "mcpConfigTemplatePath", source);
        requireText(mcpConfigFormat, "mcpConfigFormat", source);
        if (!mcpConfigFormat.equals("json") && !mcpConfigFormat.equals("toml")) {
            throw new IllegalStateException(source + " has unsupported mcpConfigFormat '" + mcpConfigFormat + "'");
        }
        requireText(mcpServerContainerKey, "mcpServerContainerKey", source);
        requireText(description, "description", source);
        requireText(generatorStrategy, "generatorStrategy", source);
        requireText(dispatchTemplatePath, "dispatchTemplatePath", source);
        requireBoolean(supportsSubagents, "supportsSubagents", source);
        requireBoolean(supportsTraits, "supportsTraits", source);

        if (!id.matches("[a-z][a-z0-9-]*")) {
            throw new IllegalStateException(source + " has invalid agent id '" + id + "'");
        }
        generatorStrategyType(source);
        for (int i = 0; i < templates.size(); i++) {
            TemplateInstall template = templates.get(i);
            if (template == null) {
                throw new IllegalStateException(source + " has null templates[" + i + "]");
            }
            template.validate(source + " templates[" + i + "]");
        }
        for (int i = 0; i < capabilities.size(); i++) {
            requireText(capabilities.get(i), "capabilities[" + i + "]", source);
        }
        return this;
    }

    public AgentGeneratorStrategy generatorStrategyType() {
        return generatorStrategyType("agent descriptor '" + id + "'");
    }

    private AgentGeneratorStrategy generatorStrategyType(String source) {
        return AgentGeneratorStrategy.fromDescriptorValue(generatorStrategy, source);
    }

    AgentConfig toAgentConfig() {
        return new AgentConfig(
                displayName,
                commandDirectory,
                skillsDirectory,
                generatesCommandStubs,
                commandFileFormat,
                argumentPlaceholder,
                mcpConfigPath,
                mcpConfigTemplatePath,
                mcpConfigFormat,
                mcpServerContainerKey,
                description);
    }

    private static <T> List<T> immutableList(List<T> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return Collections.unmodifiableList(new ArrayList<>(values));
    }

    private static void requireText(String value, String field, String source) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(source + " is missing required field '" + field + "'");
        }
    }

    private static void requireBoolean(Boolean value, String field, String source) {
        if (value == null) {
            throw new IllegalStateException(source + " is missing required field '" + field + "'");
        }
    }

    public record TemplateInstall(String source, String target) {

        private void validate(String location) {
            requireText(source, "source", location);
            requireText(target, "target", location);
        }
    }
}
