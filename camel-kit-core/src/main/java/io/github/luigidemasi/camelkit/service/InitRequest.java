package io.github.luigidemasi.camelkit.service;

import java.nio.file.Path;
import java.util.Objects;

import io.github.luigidemasi.camelkit.config.DistributionConfig;
import io.github.luigidemasi.camelkit.output.Printer;

/**
 * Request for initializing a Camel-Kit workspace.
 */
public record InitRequest(
        String projectName,
        String agentName,
        Path targetDir,
        String citrusVersion,
        boolean noFetch,
        String sourcePlatform,
        String commandPrefix,
        String defaultCitrusVersion,
        DistributionConfig distribution,
        Printer printer,
        InitProgress progress,
        InitReporter reporter) {

    public InitRequest {
        Objects.requireNonNull(projectName, "projectName");
        Objects.requireNonNull(agentName, "agentName");
        Objects.requireNonNull(targetDir, "targetDir");
        targetDir = targetDir.toAbsolutePath().normalize();
        Objects.requireNonNull(citrusVersion, "citrusVersion");
        Objects.requireNonNull(commandPrefix, "commandPrefix");
        Objects.requireNonNull(defaultCitrusVersion, "defaultCitrusVersion");
        Objects.requireNonNull(distribution, "distribution");
        Objects.requireNonNull(printer, "printer");
        progress = progress == null ? InitProgress.noop() : progress;
        reporter = reporter == null ? InitReporter.noop() : reporter;
    }

    String resolvedCitrusVersion() {
        return "default".equals(citrusVersion) ? defaultCitrusVersion : citrusVersion;
    }
}
