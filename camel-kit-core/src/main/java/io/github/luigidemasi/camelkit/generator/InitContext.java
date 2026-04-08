package io.github.luigidemasi.camelkit.generator;

import io.github.luigidemasi.camelkit.config.AgentConfig;
import io.github.luigidemasi.camelkit.output.Printer;
import java.nio.file.Path;

public record InitContext(
    AgentConfig agent,
    String agentName,
    Path commandsDir,
    Path skillsDir,
    Path projectDir,
    String commandPrefix,
    String camelVersion,
    boolean offlineMode,
    Printer printer
) {}
