package io.github.luigidemasi.camelkit.generator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AgentGeneratorFactoryTest {

    @Test
    void bobReturnsBobGenerator() {
        assertInstanceOf(BobGenerator.class, AgentGeneratorFactory.create("bob"));
    }

    @Test
    void claudeReturnsClaudeGenerator() {
        assertInstanceOf(ClaudeGenerator.class, AgentGeneratorFactory.create("claude"));
    }

    @Test
    void geminiReturnsGeminiGenerator() {
        assertInstanceOf(GeminiGenerator.class, AgentGeneratorFactory.create("gemini"));
    }

    @Test
    void qwenReturnsQwenGenerator() {
        assertInstanceOf(QwenGenerator.class, AgentGeneratorFactory.create("qwen"));
    }

    @Test
    void opencodeReturnsOpenCodeGenerator() {
        assertInstanceOf(OpenCodeGenerator.class, AgentGeneratorFactory.create("opencode"));
    }

    @Test
    void unknownAgentReturnsDefaultGenerator() {
        AgentGenerator gen = AgentGeneratorFactory.create("unknown-agent");
        assertInstanceOf(DefaultGenerator.class, gen);
        assertFalse(gen instanceof BobGenerator);
        assertFalse(gen instanceof ClaudeGenerator);
        assertFalse(gen instanceof QwenGenerator);
        assertFalse(gen instanceof OpenCodeGenerator);
        assertFalse(gen instanceof GeminiGenerator);
    }
}
