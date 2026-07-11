package io.github.luigidemasi.camelkit.config;

/**
 * Configuration for an AI coding agent.
 */
public record AgentConfig(
        String name,
        String commandDirectory,
        String skillsDirectory,
        boolean generatesCommandStubs,
        String fileFormat,
        String argPlaceholder,
        String mcpConfigPath,
        String mcpConfigTemplatePath,
        String mcpConfigFormat,
        String mcpServerContainerKey,
        String description) {

    /**
     * Primary generated agent directory retained for the persisted workspace contract.
     */
    public String folder() {
        return generatesCommandStubs ? commandDirectory : skillsDirectory;
    }
}
