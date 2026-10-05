package io.github.luigidemasi.camelkit.perf;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class CamelKnowledgeScenarioTest extends AbstractPerfScenario {

    @Test
    void componentLookup(@TempDir Path workDir) throws Exception {
        PerfResult result = runner.run(
                loadPrompt("knowledge-component-lookup.txt"),
                workDir,
                List.of("mcp__camel-knowledge__*"));

        assertEquals(0, result.exitCode(), "claude -p failed: " + result.result());
        assertFalse(result.isError());
        assertNotNull(result.sessionId(), "No session ID in response");
    }
}
