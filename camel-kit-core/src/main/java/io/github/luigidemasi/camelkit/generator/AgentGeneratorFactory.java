package io.github.luigidemasi.camelkit.generator;

public final class AgentGeneratorFactory {

    private AgentGeneratorFactory() {}

    public static AgentGenerator create(String agentName) {
        return new DefaultGenerator();
    }
}
