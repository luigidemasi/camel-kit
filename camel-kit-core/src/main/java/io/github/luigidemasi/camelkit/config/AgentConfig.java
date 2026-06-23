package io.github.luigidemasi.camelkit.config;

/**
 * Configuration for an AI coding agent.
 */
public record AgentConfig(
        String name,
        String folder,
        String fileFormat,
        String argPlaceholder,
        String mcpConfigPath,
        String mcpServerContainerKey,
        String description) {
}
