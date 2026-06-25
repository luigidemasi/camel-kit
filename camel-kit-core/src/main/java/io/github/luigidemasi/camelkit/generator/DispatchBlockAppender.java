package io.github.luigidemasi.camelkit.generator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import io.github.luigidemasi.camelkit.util.TemplateUtils;

class DispatchBlockAppender {

    void append(Path skillMdFile, String agentName) throws IOException {
        String dispatchTemplatePath = "templates/dispatch/" + agentName + ".md";
        try {
            String dispatchBlock = TemplateUtils.readTemplate(dispatchTemplatePath);
            String existing = Files.readString(skillMdFile);
            Files.writeString(skillMdFile, existing + "\n---\n\n" + dispatchBlock);
        } catch (IOException e) {
            // Dispatch template not found: skill works without it.
        }
    }
}
