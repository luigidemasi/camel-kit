package io.github.luigidemasi.camelkit.perf;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PerfResultTest {

    @Test
    void parsesClaudeJsonResponse() {
        String json = """
                {
                  "result": "The camel-kafka component supports...",
                  "session_id": "abc-123",
                  "total_cost_usd": 0.042,
                  "is_error": false
                }
                """;

        PerfResult result = PerfResult.parse(json, 0);

        assertEquals("abc-123", result.sessionId());
        assertEquals("The camel-kafka component supports...", result.result());
        assertEquals(0, result.exitCode());
        assertEquals(0.042, result.costUsd(), 0.001);
        assertFalse(result.isError());
    }

    @Test
    void handlesNonZeroExitCode() {
        String output = "Error: something went wrong";

        PerfResult result = PerfResult.parse(output, 1);

        assertEquals(1, result.exitCode());
        assertTrue(result.isError());
        assertNull(result.sessionId());
    }

    @Test
    void handlesResponseWithoutOptionalFields() {
        String json = """
                {
                  "result": "done",
                  "is_error": false
                }
                """;

        PerfResult result = PerfResult.parse(json, 0);

        assertEquals("done", result.result());
        assertNull(result.sessionId());
        assertEquals(0.0, result.costUsd(), 0.001);
    }
}
