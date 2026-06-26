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
    void unknownAgentFailsFast() {
        IllegalArgumentException thrown = assertThrows(
                IllegalArgumentException.class,
                () -> AgentGeneratorFactory.create("unknown-agent"));

        assertEquals("Unsupported agent: unknown-agent", thrown.getMessage());
    }
}
