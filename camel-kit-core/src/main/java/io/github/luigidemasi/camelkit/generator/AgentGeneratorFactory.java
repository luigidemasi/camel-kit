package io.github.luigidemasi.camelkit.generator;

public final class AgentGeneratorFactory {

    private AgentGeneratorFactory() {}

    public static AgentGenerator create(String agentName) {
        return switch (agentName) {
            case "claude" -> new ClaudeGenerator();
            default -> new DefaultGenerator();
        };
    }
}
