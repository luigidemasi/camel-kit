package io.github.luigidemasi.camelkit.perf;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import static java.util.Map.entry;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

abstract class AbstractPerfScenario {

    protected ClaudePerfRunner runner;

    @BeforeAll
    static void checkClaudeInstalled() {
        assumeTrue(isClaudeAvailable(), "claude CLI not installed — skipping perf tests");
        assumeTrue(honeycombConfigured(), "HONEYCOMB_API_KEY not set — skipping perf tests");
    }

    @BeforeEach
    void setUp() {
        String endpoint = envOrDefault("HONEYCOMB_ENDPOINT", "https://api.honeycomb.io");
        String apiKey = System.getenv("HONEYCOMB_API_KEY");

        Map<String, String> otelEnv = Map.ofEntries(
                entry("CLAUDE_CODE_ENABLE_TELEMETRY", "1"),
                entry("CLAUDE_CODE_ENHANCED_TELEMETRY_BETA", "1"),
                entry("OTEL_TRACES_EXPORTER", "otlp"),
                entry("OTEL_METRICS_EXPORTER", "otlp"),
                entry("OTEL_LOGS_EXPORTER", "otlp"),
                entry("OTEL_EXPORTER_OTLP_PROTOCOL", "http/protobuf"),
                entry("OTEL_EXPORTER_OTLP_ENDPOINT", endpoint),
                entry("OTEL_EXPORTER_OTLP_HEADERS", "x-honeycomb-team=" + apiKey),
                entry("OTEL_SERVICE_NAME", "camel-kit-perf"),
                entry("OTEL_LOG_USER_PROMPTS", "1"),
                entry("OTEL_LOG_TOOL_DETAILS", "1"),
                entry("OTEL_LOG_TOOL_CONTENT", "1"),
                entry("OTEL_METRIC_EXPORT_INTERVAL", "1000"),
                entry("OTEL_LOGS_EXPORT_INTERVAL", "1000"),
                entry("OTEL_TRACES_EXPORT_INTERVAL", "1000"));
        runner = new ClaudePerfRunner(otelEnv);
    }

    protected static String loadPrompt(String name) {
        try (InputStream is = AbstractPerfScenario.class.getResourceAsStream("/prompts/" + name)) {
            if (is == null) {
                throw new IllegalArgumentException("Prompt not found: " + name);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load prompt: " + name, e);
        }
    }

    private static boolean isClaudeAvailable() {
        try {
            Process p = new ProcessBuilder("claude", "--version")
                    .redirectErrorStream(true)
                    .start();
            p.getInputStream().readAllBytes();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean honeycombConfigured() {
        String key = System.getenv("HONEYCOMB_API_KEY");
        return key != null && !key.isBlank();
    }

    private static String envOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value != null && !value.isBlank() ? value : defaultValue;
    }
}
