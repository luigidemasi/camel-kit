package io.github.luigidemasi.camelkit.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import io.github.luigidemasi.camelkit.config.DistributionConfig;
import io.github.luigidemasi.camelkit.output.Printer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class InitServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void initializesWorkspaceAndReturnsStructuredResult() throws Exception {
        RecordingProgress progress = new RecordingProgress();
        RecordingReporter reporter = new RecordingReporter();
        Path targetDir = tempDir.resolve("orders");

        InitResult result = new InitService().initialize(request(targetDir, "bob", progress, reporter));

        assertEquals("orders", result.projectName());
        assertEquals("bob", result.agentName());
        assertEquals(targetDir, result.targetDir());
        assertEquals("4.9.2", result.citrusVersion());
        assertEquals(0, result.citrusSchemaCount());
        assertEquals("3.9.9", result.mavenWrapperVersion());
        assertFalse(result.createdPaths().isEmpty());

        assertTrue(Files.isRegularFile(targetDir.resolve(".camel-kit/config.properties")));
        assertTrue(Files.isRegularFile(targetDir.resolve("AGENTS.md")));
        assertTrue(Files.isRegularFile(targetDir.resolve(".bob/mcp.json")));
        assertTrue(Files.isRegularFile(targetDir.resolve("mvnw")));

        String config = Files.readString(targetDir.resolve(".camel-kit/config.properties"));
        assertTrue(config.contains("project.name=orders"));
        assertTrue(config.contains("agent.name=bob"));
        assertTrue(config.contains("project.sourcePlatform=mulesoft"));

        assertTrue(progress.events().contains("start:Creating project structure"));
        assertTrue(progress.events().contains("start:Generating IBM Project Bob workspace"));
        assertEquals("3.9.9", reporter.mavenVersion());
        assertTrue(reporter.wasGraphSkipped() || reporter.graph() != null || !reporter.warnings().isEmpty());
    }

    @Test
    void unknownAgentFailsBeforeWritingWorkspace() {
        Path targetDir = tempDir.resolve("orders");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new InitService().initialize(
                        request(targetDir, "unknown-agent", InitProgress.noop(), InitReporter.noop())));

        assertEquals("Unknown agent: unknown-agent", error.getMessage());
        assertFalse(Files.exists(targetDir));
    }

    private InitRequest request(
            Path targetDir,
            String agentName,
            InitProgress progress,
            InitReporter reporter) {
        return new InitRequest(
                "orders",
                agentName,
                targetDir,
                "default",
                true,
                "mulesoft",
                "camel-kit",
                "4.9.2",
                DistributionConfig.load(new Properties()),
                Printer.noop(),
                progress,
                reporter);
    }

    private static final class RecordingProgress implements InitProgress {
        private final List<String> events = new ArrayList<>();

        @Override
        public void startTask(String icon, String label) {
            events.add("start:" + label);
        }

        @Override
        public void finishTask() {
            events.add("finish");
        }

        List<String> events() {
            return events;
        }
    }

    private static final class RecordingReporter implements InitReporter {
        private final List<InitWarning> warnings = new ArrayList<>();
        private String mavenVersion;
        private InitGraphSummary graph;
        private boolean graphSkipped;

        @Override
        public void mavenWrapperCreated(String mavenVersion) {
            this.mavenVersion = mavenVersion;
        }

        @Override
        public void graphBuilt(InitGraphSummary graph) {
            this.graph = graph;
        }

        @Override
        public void graphSkipped() {
            graphSkipped = true;
        }

        @Override
        public void warning(InitWarning warning) {
            warnings.add(warning);
        }

        String mavenVersion() {
            return mavenVersion;
        }

        InitGraphSummary graph() {
            return graph;
        }

        boolean wasGraphSkipped() {
            return graphSkipped;
        }

        List<InitWarning> warnings() {
            return warnings;
        }
    }
}
