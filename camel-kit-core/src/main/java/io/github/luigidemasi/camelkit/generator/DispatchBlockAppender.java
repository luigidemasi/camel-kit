package io.github.luigidemasi.camelkit.generator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import io.github.luigidemasi.camelkit.config.AgentDescriptor;
import io.github.luigidemasi.camelkit.config.AgentRegistry;
import io.github.luigidemasi.camelkit.util.TemplateUtils;

class DispatchBlockAppender {

    void append(Path skillMdFile, String agentName) throws IOException {
        AgentDescriptor descriptor = AgentRegistry.descriptor(agentName);
        if (descriptor == null) {
            return;
        }
        String dispatchTemplatePath = descriptor.dispatchTemplatePath();
        String dispatchBlock = TemplateUtils.readTemplate(dispatchTemplatePath);
        String existing = Files.readString(skillMdFile);
        Files.writeString(skillMdFile, existing + "\n---\n\n" + dispatchBlock);
    }
}
