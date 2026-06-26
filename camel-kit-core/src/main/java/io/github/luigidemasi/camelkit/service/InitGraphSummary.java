package io.github.luigidemasi.camelkit.service;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Project graph generated during initialization.
 */
public record InitGraphSummary(
        Path graphFile,
        int nodeCount,
        int edgeCount,
        String detectedProjectType,
        String detectedRuntime) {

    public InitGraphSummary {
        Objects.requireNonNull(graphFile, "graphFile");
    }
}
