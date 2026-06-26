package io.github.luigidemasi.camelkit.generator;

import io.github.luigidemasi.camelkit.config.AgentDescriptor;
import io.github.luigidemasi.camelkit.config.AgentRegistry;

public final class AgentGeneratorFactory {

    private AgentGeneratorFactory() {
    }

    public static AgentGenerator create(String agentName) {
        return switch (generatorStrategy(agentName)) {
            case "bob" -> new BobGenerator();
            case "claude" -> new ClaudeGenerator();
            case "qwen" -> new QwenGenerator();
            case "opencode" -> new OpenCodeGenerator();
            case "gemini" -> new GeminiGenerator();
            default -> new DefaultGenerator();
        };
    }

    private static String generatorStrategy(String agentName) {
        AgentDescriptor descriptor = AgentRegistry.descriptor(agentName);
        return descriptor == null ? "default" : descriptor.generatorStrategy();
    }
}
