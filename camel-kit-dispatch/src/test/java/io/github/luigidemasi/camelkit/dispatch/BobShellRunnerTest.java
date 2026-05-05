package io.github.luigidemasi.camelkit.dispatch;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BobShellRunnerTest {

    @Test
    void buildCommandWithAutoEdit() {
        List<String> cmd = BobShellRunner.buildCommand(
                "bob", "implement route X", "code", "auto_edit", List.of());

        assertTrue(cmd.contains("bob"));
        assertTrue(cmd.contains("implement route X"));
        assertTrue(cmd.contains("--chat-mode"));
        assertTrue(cmd.contains("code"));
        assertTrue(cmd.contains("--approval-mode"));
        assertTrue(cmd.contains("auto_edit"));
        assertTrue(cmd.contains("--pre-check-auto-approved"));
        assertTrue(cmd.contains("--output-format"));
        assertTrue(cmd.contains("json"));
        assertTrue(cmd.contains("--hide-intermediary-output"));
    }

    @Test
    void buildCommandWithYolo() {
        List<String> cmd = BobShellRunner.buildCommand(
                "bob", "run tests", "advanced", "yolo", List.of());

        assertTrue(cmd.contains("--approval-mode"));
        assertTrue(cmd.contains("yolo"));
        assertFalse(cmd.contains("--pre-check-auto-approved"));
    }

    @Test
    void buildCommandWithReadOnly() {
        List<String> cmd = BobShellRunner.buildCommand(
                "bob", "review code", "ask", "read_only", List.of());

        assertTrue(cmd.contains("--allowed-tools"));
        assertTrue(cmd.contains("read_file"));
        assertTrue(cmd.contains("list_code_definition_names"));
        assertFalse(cmd.contains("--approval-mode"));
    }

    @Test
    void buildCommandPrependsFileContext() {
        List<String> cmd = BobShellRunner.buildCommand(
                "bob", "implement route X", "code", "auto_edit",
                List.of("docs/TDD.md", "docs/plan.md"));

        String taskArg = cmd.get(1);
        assertTrue(taskArg.startsWith("Read and understand the file: docs/TDD.md"));
        assertTrue(taskArg.contains("Read and understand the file: docs/plan.md"));
        assertTrue(taskArg.contains("Then: implement route X"));
    }

    @Test
    void buildCommandWithEmptyFileContext() {
        List<String> cmd = BobShellRunner.buildCommand(
                "bob", "implement route X", "code", "auto_edit", List.of());

        assertEquals("implement route X", cmd.get(1));
    }

    @Test
    void extractSummaryFromJsonOutput() {
        String jsonOutput = "{\"role\":\"assistant\",\"content\":\"Route order-processing created successfully.\"}";
        String summary = BobShellRunner.extractSummary(jsonOutput);

        assertEquals("Route order-processing created successfully.", summary);
    }

    @Test
    void extractSummaryFromPlainText() {
        String plainOutput = "Route implemented successfully.";
        String summary = BobShellRunner.extractSummary(plainOutput);

        assertEquals("Route implemented successfully.", summary);
    }

    @Test
    void extractSummaryFromEmptyOutput() {
        String summary = BobShellRunner.extractSummary("");

        assertEquals("No output from subagent", summary);
    }

    @Test
    void extractSummaryFromNullOutput() {
        String summary = BobShellRunner.extractSummary(null);

        assertEquals("No output from subagent", summary);
    }
}
