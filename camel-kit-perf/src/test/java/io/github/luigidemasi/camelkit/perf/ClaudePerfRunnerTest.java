package io.github.luigidemasi.camelkit.perf;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClaudePerfRunnerTest {

    @Test
    void buildsCommandWithAllFlags() {
        ClaudePerfRunner runner = new ClaudePerfRunner(Map.of("KEY", "val"));

        List<String> command = runner.buildCommand(
                "test prompt",
                List.of("Read", "Bash"));

        assertTrue(command.contains("claude"));
        assertTrue(command.contains("-p"));
        assertTrue(command.contains("test prompt"));
        assertTrue(command.contains("--bare"));
        assertTrue(command.contains("--output-format"));
        assertTrue(command.contains("json"));
        assertTrue(command.contains("--allowedTools"));
        assertTrue(command.contains("Read,Bash"));
    }

    @Test
    void buildsCommandWithoutAllowedToolsWhenEmpty() {
        ClaudePerfRunner runner = new ClaudePerfRunner(Map.of());

        List<String> command = runner.buildCommand("prompt", List.of());

        assertFalse(command.contains("--allowedTools"));
    }

    @Test
    void otelEnvIsIncluded() {
        Map<String, String> otelEnv = Map.of(
                "CLAUDE_CODE_ENABLE_TELEMETRY", "1",
                "OTEL_TRACES_EXPORTER", "otlp");
        ClaudePerfRunner runner = new ClaudePerfRunner(otelEnv);

        assertEquals("1", runner.otelEnv().get("CLAUDE_CODE_ENABLE_TELEMETRY"));
        assertEquals("otlp", runner.otelEnv().get("OTEL_TRACES_EXPORTER"));
    }
}
