package io.github.luigidemasi.camelkit.config;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Generator strategies supported by agent descriptors.
 */
public enum AgentGeneratorStrategy {
    DEFAULT("default"),
    BOB("bob"),
    BOB2("bob2"),
    CLAUDE("claude"),
    CODEX("codex"),
    COPILOT("copilot"),
    GEMINI("gemini"),
    OPENCODE("opencode"),
    PI("pi"),
    QWEN("qwen");

    private final String descriptorValue;

    AgentGeneratorStrategy(String descriptorValue) {
        this.descriptorValue = descriptorValue;
    }

    public String descriptorValue() {
        return descriptorValue;
    }

    static AgentGeneratorStrategy fromDescriptorValue(String value, String source) {
        for (AgentGeneratorStrategy strategy : values()) {
            if (strategy.descriptorValue.equals(value)) {
                return strategy;
            }
        }
        throw new IllegalStateException(
                source + " has unsupported generatorStrategy '" + value
                                        + "'. Supported values: " + supportedValues());
    }

    private static String supportedValues() {
        return Arrays.stream(values())
                .map(AgentGeneratorStrategy::descriptorValue)
                .collect(Collectors.joining(", "));
    }
}
