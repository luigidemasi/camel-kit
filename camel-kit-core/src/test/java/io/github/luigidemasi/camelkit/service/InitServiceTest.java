package io.github.luigidemasi.camelkit.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import io.github.luigidemasi.camelkit.CamelKitMain;
import io.github.luigidemasi.camelkit.config.DistributionConfig;
import io.github.luigidemasi.camelkit.output.Printer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class InitServiceTest {

    private static final String EXPECTED_CITRUS_MCP_VERSION = "5.0.0-M1";

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
        assertEquals("5.0.0-M2", result.citrusVersion());
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
        assertTrue(config.contains("citrus.version=5.0.0-M2"));
        assertTrue(config.contains("citrus.mcp.version=" + EXPECTED_CITRUS_MCP_VERSION));

        assertTrue(progress.events().contains("start:Creating project structure"));
        assertTrue(progress.events().contains("start:Generating IBM Project Bob workspace"));
        assertEquals("3.9.9", reporter.mavenVersion());
        assertTrue(result.warnings().stream()
                .anyMatch(warning -> warning.message().contains("IBM Bob 1 legacy selected")));
        assertTrue(reporter.hasWarningContaining("use --ai bob2 for new IBM Bob projects"));
        assertTrue(reporter.wasGraphSkipped() || reporter.graph() != null || !reporter.warnings().isEmpty());
    }

    @Test
    void bob2InitializationDoesNotReportBob1LegacyWarning() throws Exception {
        RecordingReporter reporter = new RecordingReporter();
        Path targetDir = tempDir.resolve("orders");

        InitResult result = new InitService().initialize(
                request(targetDir, "bob2", InitProgress.noop(), reporter));

        assertEquals("bob2", result.agentName());
        assertFalse(result.warnings().stream()
                .anyMatch(warning -> warning.message().contains("IBM Bob 1 legacy selected")));
        assertFalse(reporter.hasWarningContaining("IBM Bob 1 legacy selected"));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void rejectsSymlinkedAgentRootsBeforeWritingOutsideTheProject() throws Exception {
        for (String agentName : List.of("claude", "bob2")) {
            Path targetDir = Files.createDirectory(tempDir.resolve(agentName));
            Path outside = Files.createDirectory(tempDir.resolve(agentName + "-outside"));
            Files.writeString(outside.resolve("keep.md"), "outside");
            Files.createSymbolicLink(targetDir.resolve("claude".equals(agentName) ? ".claude" : ".bob"), outside);
            List<String> before = snapshot(outside);

            IOException failure = assertThrows(
                    IOException.class,
                    () -> new InitService().initialize(
                            request(targetDir, agentName, InitProgress.noop(), InitReporter.noop())));

            assertTrue(failure.getMessage().contains("symbolic link"), agentName);
            assertEquals(before, snapshot(outside), agentName + " must not write through the agent-root link");
        }
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void normalizesTheTargetBeforeCleanupAndGeneration() throws Exception {
        Path targetDir = Files.createDirectory(tempDir.resolve("orders"));
        Path outside = Files.createDirectory(tempDir.resolve("outside"));
        Path outsideChild = Files.createDirectory(outside.resolve("child"));
        Files.writeString(outside.resolve("keep.md"), "outside");
        Files.createSymbolicLink(targetDir.resolve("link"), outsideChild);
        List<String> before = snapshot(outside);

        InitResult result = new InitService().initialize(
                request(targetDir.resolve("link/.."), "claude", InitProgress.noop(), InitReporter.noop()));

        assertEquals(targetDir.toAbsolutePath().normalize(), result.targetDir());
        assertTrue(Files.isRegularFile(targetDir.resolve(".claude/commands/camel-ship.md")));
        assertEquals(before, snapshot(outside));
    }

    @Test
    void initializesCopilotWorkspaceWithGithubNativeAssets() throws Exception {
        RecordingProgress progress = new RecordingProgress();
        Path targetDir = tempDir.resolve("orders");

        InitResult result = new InitService().initialize(
                request(targetDir, "copilot", progress, InitReporter.noop()));

        assertEquals("copilot", result.agentName());
        assertTrue(Files.isRegularFile(targetDir.resolve(".github/copilot-instructions.md")));
        assertTrue(Files.isRegularFile(targetDir.resolve(".github/mcp.json")));
        assertTrue(Files.isRegularFile(targetDir.resolve(".github/skills/camel-start/SKILL.md")));
        assertTrue(Files.isRegularFile(targetDir.resolve(".github/agents/camel-implementer.agent.md")));
        assertTrue(Files.isRegularFile(targetDir.resolve(".github/hooks/camel-kit-safety.json")));
        assertFalse(Files.exists(targetDir.resolve(".github/commands")));
        assertTrue(progress.events().contains("start:Generating GitHub Copilot CLI workspace"));

        String config = Files.readString(targetDir.resolve(".camel-kit/config.properties"));
        assertTrue(config.contains("agent.name=copilot"));
        assertTrue(config.contains("agent.folder=.github/skills"));
    }

    @Test
    void initializesPiWorkspaceWithNativeAssets() throws Exception {
        RecordingProgress progress = new RecordingProgress();
        Path targetDir = tempDir.resolve("orders");

        InitResult result = new InitService().initialize(
                request(targetDir, "pi", progress, InitReporter.noop()));

        assertEquals("pi", result.agentName());
        assertTrue(Files.isRegularFile(targetDir.resolve("AGENTS.md")));
        assertTrue(Files.isRegularFile(targetDir.resolve(".mcp.json")));
        assertTrue(Files.isRegularFile(targetDir.resolve(".pi/skills/camel-start/SKILL.md")));
        assertTrue(Files.isRegularFile(targetDir.resolve(".pi/prompts/camel-start.md")));
        assertTrue(Files.isRegularFile(targetDir.resolve(".pi/extensions/camel-kit-guard.ts")));
        assertTrue(Files.isRegularFile(targetDir.resolve(".pi/camel-kit-guard-policy.json")));
        assertTrue(progress.events().contains("start:Generating Pi workspace"));

        String config = Files.readString(targetDir.resolve(".camel-kit/config.properties"));
        assertTrue(config.contains("agent.name=pi"));
        assertTrue(config.contains("agent.folder=.pi/prompts"));
    }

    @Test
    void initializesCodexWorkspaceWithNativeAssetsWithoutCommandStubs() throws Exception {
        RecordingProgress progress = new RecordingProgress();
        Path targetDir = tempDir.resolve("orders");

        InitResult result = new InitService().initialize(
                request(targetDir, "codex", progress, InitReporter.noop()));

        assertEquals("codex", result.agentName());
        assertTrue(Files.isRegularFile(targetDir.resolve("AGENTS.md")));
        assertTrue(Files.isRegularFile(targetDir.resolve(".codex/config.toml")));
        assertTrue(Files.isRegularFile(targetDir.resolve(".agents/skills/camel-start/SKILL.md")));
        assertTrue(Files.isRegularFile(targetDir.resolve(".codex/agents/camel-implementer.toml")));
        assertFalse(Files.exists(targetDir.resolve(".codex/commands")));
        assertFalse(Files.exists(targetDir.resolve(".agents/commands")));
        assertTrue(progress.events().contains("start:Generating OpenAI Codex CLI workspace"));

        String config = Files.readString(targetDir.resolve(".camel-kit/config.properties"));
        assertTrue(config.contains("agent.name=codex"));
        assertTrue(config.contains("agent.folder=.agents/skills"));
    }

    @Test
    void initPersistsRuntimeAndVersionConfig() throws Exception {
        Path targetDir = tempDir.resolve("orders");

        new InitService().initialize(request(targetDir, "bob2", InitProgress.noop(), InitReporter.noop()));

        Properties config = new Properties();
        try (var in = Files.newInputStream(targetDir.resolve(".camel-kit/config.properties"))) {
            config.load(in);
        }
        DistributionConfig distribution = CamelKitMain.distribution();
        assertEquals("main", config.getProperty("project.runtime"));
        assertEquals(distribution.camelMainVersion(), config.getProperty("project.camelVersion"));
        assertEquals(distribution.camelMainVersion(), config.getProperty("project.platformBomVersion"));
        assertNull(config.getProperty("project.springBootVersion"),
                "spring-boot framework version must only be written for the spring-boot runtime");
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

    @Test
    void customCitrusVersionDoesNotChangeMcpServerArtifactVersion() throws Exception {
        Path targetDir = tempDir.resolve("orders");

        InitResult result = new InitService().initialize(
                request(targetDir, "bob2", "4.9.2", InitProgress.noop(), InitReporter.noop()));

        assertEquals("4.9.2", result.citrusVersion());
        String config = Files.readString(targetDir.resolve(".camel-kit/config.properties"));
        assertTrue(config.contains("citrus.version=4.9.2"));
        assertTrue(config.contains("citrus.mcp.version=" + EXPECTED_CITRUS_MCP_VERSION));
        String mcp = Files.readString(targetDir.resolve(".bob/mcp.json"));
        assertTrue(mcp.contains(
                "org.citrusframework:citrus-mcp-server:" + EXPECTED_CITRUS_MCP_VERSION + ":runner"));
        assertFalse(mcp.contains("org.citrusframework:citrus-mcp-server:4.9.2:runner"));
    }

    @Test
    void explicitCitrusMcpVersionOverrideIsUsedWithCustomCitrusVersion() throws Exception {
        Path targetDir = tempDir.resolve("orders");
        Properties properties = new Properties();
        properties.setProperty("citrus.mcp.version", "4.10.1");
        DistributionConfig distribution = DistributionConfig.load(properties);

        InitResult result = new InitService().initialize(
                request(targetDir, "bob2", "4.9.2", distribution, InitProgress.noop(), InitReporter.noop()));

        assertEquals("4.9.2", result.citrusVersion());
        String config = Files.readString(targetDir.resolve(".camel-kit/config.properties"));
        assertTrue(config.contains("citrus.version=4.9.2"));
        assertTrue(config.contains("citrus.mcp.version=4.10.1"));
        String mcp = Files.readString(targetDir.resolve(".bob/mcp.json"));
        assertTrue(mcp.contains("org.citrusframework:citrus-mcp-server:4.10.1:runner"));
        assertFalse(mcp.contains("org.citrusframework:citrus-mcp-server:4.9.2:runner"));
    }

    @Test
    void forageVersionMappedToDefaultCamelVersionIsWrittenToConfig() throws Exception {
        Path targetDir = tempDir.resolve("orders");
        Properties properties = new Properties();
        properties.setProperty("forage.version.4.21.0", "1.5.0");
        DistributionConfig distribution = DistributionConfig.load(properties);

        // pre-seeded cache keeps the unit test offline (isCached short-circuits the download);
        // noFetch=false so the caching path actually runs and the offline guard below stays meaningful
        Path forageCacheDir = targetDir.resolve(".camel-kit/.cache/forage/1.5.0");
        Files.createDirectories(forageCacheDir);
        Files.writeString(forageCacheDir.resolve("forage-catalog.json"), "{}");
        Files.writeString(forageCacheDir.resolve("forage-configuration-catalog.json"), "{}");

        new InitService().initialize(
                request(targetDir, "bob2", "default", distribution, false, InitProgress.noop(), InitReporter.noop()));

        String config = Files.readString(targetDir.resolve(".camel-kit/config.properties"));
        assertTrue(config.contains("forage.version=1.5.0"));

        // guard: if the cache short-circuit stopped working, the real download would overwrite
        // these dummy files with actual catalog JSON — asserting content stays "{}" proves no
        // network call happened.
        assertEquals("{}", Files.readString(forageCacheDir.resolve("forage-catalog.json")));
        assertEquals("{}", Files.readString(forageCacheDir.resolve("forage-configuration-catalog.json")));
    }

    @Test
    void noFetchSkipsForageCatalogCaching() throws Exception {
        Path targetDir = tempDir.resolve("orders");
        Properties properties = new Properties();
        properties.setProperty("forage.version.4.21.0", "1.5.0");
        DistributionConfig distribution = DistributionConfig.load(properties);

        new InitService().initialize(
                request(targetDir, "bob2", "default", distribution, InitProgress.noop(), InitReporter.noop()));

        String config = Files.readString(targetDir.resolve(".camel-kit/config.properties"));
        assertTrue(config.contains("forage.version=1.5.0"), "version mapping is still written with --no-fetch");
        assertFalse(Files.exists(targetDir.resolve(".camel-kit/.cache/forage")),
                "--no-fetch must not attempt any catalog download");
    }

    @Test
    void runtimeDetectionRecomputesForageVersionForDetectedRuntime() throws Exception {
        Path targetDir = tempDir.resolve("orders");
        Files.createDirectories(targetDir);
        Files.writeString(targetDir.resolve("pom.xml"), """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>demo</groupId>
                  <artifactId>orders</artifactId>
                  <version>1.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>org.apache.camel.quarkus</groupId>
                      <artifactId>camel-quarkus-core</artifactId>
                      <version>3.15.0</version>
                    </dependency>
                  </dependencies>
                </project>
                """);
        Properties properties = new Properties();
        properties.setProperty("forage.version.4.21.0", "1.5.0");
        properties.setProperty("forage.version.4.18.2", "1.3");
        DistributionConfig distribution = DistributionConfig.load(properties);

        new InitService().initialize(
                request(targetDir, "bob2", "default", distribution, InitProgress.noop(), InitReporter.noop()));

        Properties config = new Properties();
        try (var in = Files.newInputStream(targetDir.resolve(".camel-kit/config.properties"))) {
            config.load(in);
        }
        assertEquals("quarkus", config.getProperty("project.runtime"));
        assertEquals("1.3", config.getProperty("forage.version"),
                "forage.version must track the runtime-detected Camel version, not the initial main default");
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
                "5.0.0-M2",
                CamelKitMain.distribution(),
                Printer.noop(),
                progress,
                reporter);
    }

    private static List<String> snapshot(Path root) throws Exception {
        List<String> entries = new ArrayList<>();
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted().toList()) {
                if (path.equals(root)) {
                    continue;
                }
                String relative = root.relativize(path).toString();
                entries.add(Files.isDirectory(path)
                        ? "directory:" + relative
                        : "file:" + relative + "=" + Files.readString(path));
            }
        }
        return entries;
    }

    private InitRequest request(
            Path targetDir,
            String agentName,
            String citrusVersion,
            InitProgress progress,
            InitReporter reporter) {
        return request(targetDir, agentName, citrusVersion, CamelKitMain.distribution(), progress, reporter);
    }

    private InitRequest request(
            Path targetDir,
            String agentName,
            String citrusVersion,
            DistributionConfig distribution,
            InitProgress progress,
            InitReporter reporter) {
        return request(targetDir, agentName, citrusVersion, distribution, true, progress, reporter);
    }

    private InitRequest request(
            Path targetDir,
            String agentName,
            String citrusVersion,
            DistributionConfig distribution,
            boolean noFetch,
            InitProgress progress,
            InitReporter reporter) {
        return new InitRequest(
                "orders",
                agentName,
                targetDir,
                citrusVersion,
                noFetch,
                "mulesoft",
                "camel-kit",
                "5.0.0-M2",
                distribution,
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

        boolean hasWarningContaining(String text) {
            return warnings.stream().anyMatch(warning -> warning.message().contains(text));
        }
    }
}
