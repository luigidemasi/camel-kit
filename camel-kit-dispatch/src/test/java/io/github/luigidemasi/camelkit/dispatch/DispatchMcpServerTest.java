package io.github.luigidemasi.camelkit.dispatch;

import jakarta.inject.Inject;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class DispatchMcpServerTest {

    @Inject
    DispatchMcpServer server;

    @Inject
    DispatchConfig config;

    @Test
    void serverIsInjectable() {
        assertNotNull(server);
    }

    @Test
    void configIsInjectable() {
        assertNotNull(config);
        assertEquals("bob", config.bobPath());
        assertEquals(300, config.defaultTimeoutSeconds());
        assertEquals(4, config.maxConcurrent());
    }

    @Test
    void dispatchSubagentReturnsNonSuccessWhenBobNotInstalled() {
        String result = server.dispatchSubagent(
                "echo hello", "code", "auto_edit", 5, null);

        assertNotNull(result);
        boolean isFailureOrTimeout = result.contains("\"failure\"") || result.contains("\"timeout\"");
        assertTrue(isFailureOrTimeout,
                "Expected failure or timeout when bob is not installed: " + result);
        assertFalse(result.contains("\"success\""));
    }

    @Test
    void dispatchStatusReturnsNotFoundForUnknownId() {
        String result = server.dispatchStatus("nonexistent-id");

        assertNotNull(result);
        assertTrue(result.contains("not_found"),
                "Expected not_found status: " + result);
    }
}
