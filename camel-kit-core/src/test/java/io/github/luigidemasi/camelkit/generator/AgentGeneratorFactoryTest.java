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
    void geminiReturnsDefaultGenerator() {
        AgentGenerator gen = AgentGeneratorFactory.create("gemini");
        assertInstanceOf(DefaultGenerator.class, gen);
        assertFalse(gen instanceof BobGenerator);
        assertFalse(gen instanceof ClaudeGenerator);
    }

    @Test
    void qwenReturnsDefaultGenerator() {
        AgentGenerator gen = AgentGeneratorFactory.create("qwen");
        assertInstanceOf(DefaultGenerator.class, gen);
    }

    @Test
    void opencodeReturnsDefaultGenerator() {
        AgentGenerator gen = AgentGeneratorFactory.create("opencode");
        assertInstanceOf(DefaultGenerator.class, gen);
    }
}
