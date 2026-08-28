package io.github.luigidemasi.camelkit.generator;

public interface AgentGenerator {
    default void preflight(InitContext ctx) throws Exception {
        // Most generators do not need agent-specific validation before initialization starts.
    }

    void generate(InitContext ctx) throws Exception;
}
