package io.github.luigidemasi.camelkit.generator;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QuteTemplateEngineTest {

    private QuteTemplateEngine engine;

    @BeforeEach
    void setUp() {
        engine = new QuteTemplateEngine();
    }

    @Test
    void substitutesVariables() {
        String result = engine.render("test-templates/simple.md",
                Map.of("COMMAND_PREFIX", "camel-kit"));
        assertTrue(result.contains("camel-kit graph stats"));
        assertFalse(result.contains("{COMMAND_PREFIX}"));
    }

    @Test
    void evaluatesConditionals() {
        String bobResult = engine.render("test-templates/conditional.md",
                Map.of("agent", "bob"));
        assertTrue(bobResult.contains("Switch to camel-brainstorm mode"));
        assertFalse(bobResult.contains("Invoke the camel-brainstorm skill"));

        String claudeResult = engine.render("test-templates/conditional.md",
                Map.of("agent", "claude"));
        assertTrue(claudeResult.contains("Invoke the camel-brainstorm skill"));
        assertFalse(claudeResult.contains("Switch to camel-brainstorm mode"));
    }

    @Test
    void throwsOnMissingTemplate() {
        assertThrows(RuntimeException.class,
                () -> engine.render("test-templates/nonexistent.md", Map.of()));
    }
}
