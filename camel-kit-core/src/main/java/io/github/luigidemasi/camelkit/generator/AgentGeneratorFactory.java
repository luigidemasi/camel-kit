package io.github.luigidemasi.camelkit.generator;

import io.github.luigidemasi.camelkit.config.AgentDescriptor;
import io.github.luigidemasi.camelkit.config.AgentGeneratorStrategy;
import io.github.luigidemasi.camelkit.config.AgentRegistry;

public final class AgentGeneratorFactory {

    private AgentGeneratorFactory() {
    }

    public static AgentGenerator create(String agentName) {
        return switch (generatorStrategy(agentName)) {
            case BOB -> new BobGenerator();
            case BOB2 -> new Bob2Generator();
            case CLAUDE -> new ClaudeGenerator();
            case CODEX -> new CodexGenerator();
            case COPILOT -> new CopilotGenerator();
            case GEMINI -> new GeminiGenerator();
            case OPENCODE -> new OpenCodeGenerator();
            case PI -> new PiGenerator();
            case QWEN -> new QwenGenerator();
            case DEFAULT -> new DefaultGenerator();
        };
    }

    private static AgentGeneratorStrategy generatorStrategy(String agentName) {
        AgentDescriptor descriptor = AgentRegistry.descriptor(agentName);
        if (descriptor == null) {
            throw new IllegalArgumentException("Unsupported agent: " + agentName);
        }
        return descriptor.generatorStrategyType();
    }
}
