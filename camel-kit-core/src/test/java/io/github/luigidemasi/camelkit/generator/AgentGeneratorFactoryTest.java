package io.github.luigidemasi.camelkit.generator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AgentGeneratorFactoryTest {

    @Test
    void bobReturnsBobGenerator() {
        assertInstanceOf(BobGenerator.class, AgentGeneratorFactory.create("bob"));
    }

    @Test
    void bob2ReturnsBob2Generator() {
        assertInstanceOf(Bob2Generator.class, AgentGeneratorFactory.create("bob2"));
    }

    @Test
    void claudeReturnsClaudeGenerator() {
        assertInstanceOf(ClaudeGenerator.class, AgentGeneratorFactory.create("claude"));
    }

    @Test
    void copilotReturnsCopilotGenerator() {
        assertInstanceOf(CopilotGenerator.class, AgentGeneratorFactory.create("copilot"));
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
    void piReturnsPiGenerator() {
        assertInstanceOf(PiGenerator.class, AgentGeneratorFactory.create("pi"));
    }

    @Test
    void unknownAgentFailsFast() {
        IllegalArgumentException thrown = assertThrows(
                IllegalArgumentException.class,
                () -> AgentGeneratorFactory.create("unknown-agent"));

        assertEquals("Unsupported agent: unknown-agent", thrown.getMessage());
    }
}
