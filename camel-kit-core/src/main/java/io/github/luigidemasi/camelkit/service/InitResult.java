package io.github.luigidemasi.camelkit.service;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import io.github.luigidemasi.camelkit.config.AgentConfig;

/**
 * Result of initializing a Camel-Kit workspace.
 */
public record InitResult(
        String projectName,
        String agentName,
        AgentConfig agent,
        Path targetDir,
        Path camelKitDir,
        Path commandsDir,
        Path skillsDir,
        String citrusVersion,
        int citrusSchemaCount,
        String mavenWrapperVersion,
        InitGraphSummary graph,
        List<Path> createdPaths,
        List<InitWarning> warnings) {

    public InitResult {
        Objects.requireNonNull(projectName, "projectName");
        Objects.requireNonNull(agentName, "agentName");
        Objects.requireNonNull(agent, "agent");
        Objects.requireNonNull(targetDir, "targetDir");
        Objects.requireNonNull(camelKitDir, "camelKitDir");
        Objects.requireNonNull(commandsDir, "commandsDir");
        Objects.requireNonNull(skillsDir, "skillsDir");
        Objects.requireNonNull(citrusVersion, "citrusVersion");
        Objects.requireNonNull(mavenWrapperVersion, "mavenWrapperVersion");
        createdPaths = List.copyOf(Objects.requireNonNull(createdPaths, "createdPaths"));
        warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings"));
    }
}
