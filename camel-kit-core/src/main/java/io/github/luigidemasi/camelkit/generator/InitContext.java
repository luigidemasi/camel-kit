package io.github.luigidemasi.camelkit.generator;

import java.nio.file.Path;

import io.github.luigidemasi.camelkit.CamelKitMain;
import io.github.luigidemasi.camelkit.config.AgentConfig;
import io.github.luigidemasi.camelkit.output.Printer;

public record InitContext(
        AgentConfig agent,
        String agentName,
        Path commandsDir,
        Path skillsDir,
        Path projectDir,
        String commandPrefix,
        String citrusVersion,
        Printer printer) {

    public InitContext(
                       AgentConfig agent,
                       String agentName,
                       Path commandsDir,
                       Path skillsDir,
                       Path projectDir,
                       String commandPrefix,
                       Printer printer) {
        this(agent, agentName, commandsDir, skillsDir, projectDir, commandPrefix,
             CamelKitMain.DEFAULT_CITRUS_VERSION, printer);
    }
}
