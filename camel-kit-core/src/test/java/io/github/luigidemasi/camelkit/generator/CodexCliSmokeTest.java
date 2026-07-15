package io.github.luigidemasi.camelkit.generator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import io.github.luigidemasi.camelkit.config.AgentConfig;
import io.github.luigidemasi.camelkit.config.AgentRegistry;
import io.github.luigidemasi.camelkit.output.Printer;
import io.github.luigidemasi.camelkit.workflow.WorkflowManifestLoader;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class CodexCliSmokeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @TempDir
    Path tempDir;

    @Test
    void installedCodexDiscoversGeneratedInstructionsSkillsAgentsAndMcpServers() throws Exception {
        Path workspace = Files.createDirectories(tempDir.resolve("workspace"));
        Path codexHome = configureIsolatedCodexHome(workspace);
        Map<String, String> environment = isolatedEnvironment(codexHome);
        CommandResult version = tryRunCodex(List.of("codex", "--version"), workspace, environment);
        Assumptions.assumeTrue(version != null && version.exitCode() == 0,
                "Codex CLI is not installed; skipping local discovery smoke test");
        generateWorkspace(workspace, environment);

        CommandResult prompt = runCodex(
                List.of("codex", "-C", workspace.toString(), "debug", "prompt-input",
                        "-c", "mcp_servers.citrus.enabled=false", "smoke"),
                workspace,
                environment);
        assertEquals(0, prompt.exitCode(), "Codex did not render the isolated prompt input");
        assertTrue(prompt.stdout().contains("# Camel-Kit Project"), "Codex did not load the generated AGENTS.md");
        assertTrue(prompt.stdout().contains("camel-start"), "Codex did not expose the generated skills");
        assertFalse(prompt.combined().contains("Ignoring malformed agent role definition"),
                "Codex rejected a generated custom agent definition");

        CommandResult mcp = runCodex(
                List.of("codex", "-C", workspace.toString(), "mcp", "list", "--json"),
                workspace,
                environment);
        assertEquals(0, mcp.exitCode(), "Codex did not load the generated MCP configuration");
        JsonNode mcpServers = MAPPER.readTree(mcp.stdout());
        assertEquals(List.of("camel", "camel-knowledge", "citrus"),
                mcpServers.findValuesAsText("name"), "Codex discovered an unexpected MCP server set");

        JsonNode skills = discoverSkills(workspace, environment, false);
        Set<String> discoveredSkills = new LinkedHashSet<>(skills.findValuesAsText("name"));
        Set<String> expectedSkills = new LinkedHashSet<>(
                WorkflowManifestLoader.loadDefault().skills().stream()
                        .map(skill -> skill.name())
                        .toList());
        assertTrue(discoveredSkills.containsAll(expectedSkills),
                "Missing skills: " + difference(expectedSkills, discoveredSkills) + "\n" + skills.toPrettyString());
        assertTrue(skills.findValuesAsText("scope").contains("repo"), skills.toPrettyString());

        Files.copy(
                workspace.resolve(".codex/agents/camel-implementer.toml"),
                workspace.resolve(".codex/agents/duplicate-implementer.toml"));
        // The app-server has no role-list endpoint, so a duplicate-valid control proves that thread startup
        // parsed the generated role name while every other generated role remained valid.
        discoverSkills(workspace, environment, true);
    }

    private void generateWorkspace(Path workspace, Map<String, String> environment) throws Exception {
        AgentConfig agent = AgentRegistry.get("codex");
        InitContext context = new InitContext(
                agent,
                "codex",
                workspace.resolve(".codex/commands"),
                workspace.resolve(agent.skillsDirectory()),
                workspace,
                "camel-kit",
                Printer.noop());
        AgentGeneratorFactory.create("codex").generate(context);

        CommandResult gitInit = runCodex(List.of("git", "init", "--quiet"), workspace, environment);
        assertEquals(0, gitInit.exitCode(), "Unable to initialize isolated smoke-test repository");
    }

    private Path configureIsolatedCodexHome(Path workspace) throws IOException {
        Path home = Files.createDirectories(tempDir.resolve("home"));
        Path codexHome = Files.createDirectories(home.resolve(".codex"));
        String trustedPath = workspace.toAbsolutePath().normalize().toString().replace("\\", "\\\\");
        Files.writeString(codexHome.resolve("config.toml"), String.format(Locale.ROOT, """
                [projects."%s"]
                trust_level = "trusted"
                """, trustedPath));
        return codexHome;
    }

    private Map<String, String> isolatedEnvironment(Path codexHome) throws IOException {
        return Map.of(
                "HOME", tempDir.resolve("home").toString(),
                "CODEX_HOME", codexHome.toString(),
                "CODEX_SQLITE_HOME", Files.createDirectories(tempDir.resolve("sqlite")).toString(),
                "XDG_CACHE_HOME", Files.createDirectories(tempDir.resolve("cache")).toString(),
                "XDG_CONFIG_HOME", Files.createDirectories(tempDir.resolve("config")).toString(),
                "TMPDIR", Files.createDirectories(tempDir.resolve("tmp")).toString(),
                "NO_COLOR", "1",
                "TERM", "dumb");
    }

    private JsonNode discoverSkills(
            Path workspace, Map<String, String> environment, boolean expectMalformedAgentWarning)
            throws Exception {
        ProcessBuilder builder = isolatedProcessBuilder(
                List.of("codex", "app-server", "--strict-config", "--listen", "stdio://"),
                workspace,
                environment);
        Process process = builder.start();
        StringBuffer stdout = new StringBuffer();
        LinkedBlockingQueue<String> lines = new LinkedBlockingQueue<>();
        CompletableFuture<Void> stdoutReader = CompletableFuture.runAsync(() -> readLines(process, stdout, lines));
        CompletableFuture<String> stderrReader = readAsync(process.getErrorStream());

        try (OutputStreamWriter writer = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8)) {
            writeRequest(writer,
                    "{\"id\":1,\"method\":\"initialize\",\"params\":{\"clientInfo\":{\"name\":\"camel-kit-smoke\",\"version\":\"1\"}}}");
            awaitResponse(lines, 1, stdout, stderrReader, process);

            writeRequest(writer, "{\"method\":\"initialized\"}");
            writeRequest(writer,
                    "{\"id\":2,\"method\":\"thread/start\",\"params\":{\"cwd\":\""
                                 + jsonEscape(workspace.toString()) + "\",\"ephemeral\":true}}");
            JsonNode thread = awaitResponse(lines, 2, stdout, stderrReader, process);
            assertFalse(thread.has("error"), "Codex app-server could not start an isolated thread");

            writeRequest(writer,
                    "{\"id\":3,\"method\":\"skills/list\",\"params\":{\"cwds\":[\""
                                 + jsonEscape(workspace.toString()) + "\"],\"forceReload\":true}}");
            JsonNode response = awaitResponse(lines, 3, stdout, stderrReader, process);
            assertFalse(response.has("error"), "Codex app-server could not list repository skills");

            writer.close();
            assertTrue(process.waitFor(TIMEOUT.toSeconds(), TimeUnit.SECONDS),
                    "Codex app-server did not exit after stdin closed");
            stdoutReader.join();
            String stderr = stderrReader.join();
            assertEquals(0, process.exitValue(), "Codex app-server exited unsuccessfully");
            assertEquals(expectMalformedAgentWarning,
                    stderr.contains("Ignoring malformed agent role definition"),
                    expectMalformedAgentWarning
                            ? "Codex did not inspect project custom-agent definitions"
                            : "Codex rejected a generated custom agent definition");
            if (expectMalformedAgentWarning) {
                assertTrue(stderr.contains("duplicate agent role name `camel_implementer`"),
                        "Codex did not parse the generated custom-agent role name");
                assertEquals(1,
                        stderr.lines()
                                .filter(line -> line.contains("Ignoring malformed agent role definition"))
                                .count(),
                        "Codex rejected another generated custom-agent definition");
            }
            return response.path("result");
        }
    }

    private JsonNode awaitResponse(
            LinkedBlockingQueue<String> lines,
            int expectedId,
            StringBuffer stdout,
            CompletableFuture<String> stderr,
            Process process)
            throws Exception {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            long remaining = deadline - System.nanoTime();
            String line = lines.poll(remaining, TimeUnit.NANOSECONDS);
            if (line == null) {
                break;
            }
            JsonNode response = MAPPER.readTree(line);
            if (response.path("id").asInt(-1) == expectedId) {
                return response;
            }
        }
        process.destroyForcibly();
        fail("Codex app-server returned no response for id " + expectedId + ":\n" + stdout + stderr.join());
        return MAPPER.nullNode();
    }

    private void writeRequest(OutputStreamWriter writer, String request) throws IOException {
        writer.write(request);
        writer.write(System.lineSeparator());
        writer.flush();
    }

    private void readLines(Process process, StringBuffer output, LinkedBlockingQueue<String> lines) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append(System.lineSeparator());
                lines.add(line);
            }
        } catch (IOException e) {
            if (process.isAlive()) {
                throw new IllegalStateException(e);
            }
        }
    }

    private CommandResult tryRunCodex(List<String> command, Path directory, Map<String, String> environment)
            throws InterruptedException {
        try {
            return runCodex(command, directory, environment);
        } catch (IOException e) {
            return null;
        }
    }

    private CommandResult runCodex(List<String> command, Path directory, Map<String, String> environment)
            throws IOException, InterruptedException {
        ProcessBuilder builder = isolatedProcessBuilder(command, directory, environment);
        Process process = builder.start();
        CompletableFuture<String> stdout = readAsync(process.getInputStream());
        CompletableFuture<String> stderr = readAsync(process.getErrorStream());
        process.getOutputStream().close();

        if (!process.waitFor(TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
            process.destroyForcibly();
            fail("Command timed out after " + TIMEOUT + ": " + String.join(" ", command));
        }
        return new CommandResult(process.exitValue(), stdout.join(), stderr.join());
    }

    private ProcessBuilder isolatedProcessBuilder(
            List<String> command, Path directory, Map<String, String> environment) {
        ProcessBuilder builder = new ProcessBuilder(new ArrayList<>(command));
        builder.directory(directory.toFile());
        Map<String, String> childEnvironment = builder.environment();
        childEnvironment.clear();
        copyHostEnvironment(childEnvironment, "PATH");
        copyHostEnvironment(childEnvironment, "SHELL");
        copyHostEnvironment(childEnvironment, "CODEX_MANAGED_BY_NPM");
        copyHostEnvironment(childEnvironment, "CODEX_MANAGED_PACKAGE_ROOT");
        childEnvironment.put("LANG", "C.UTF-8");
        childEnvironment.put("CODEX_SANDBOX_NETWORK_DISABLED", "1");
        childEnvironment.putAll(environment);
        return builder;
    }

    private void copyHostEnvironment(Map<String, String> target, String name) {
        String value = System.getenv(name);
        if (value != null && !value.isBlank()) {
            target.put(name, value);
        }
    }

    private CompletableFuture<String> readAsync(InputStream input) {
        return CompletableFuture.supplyAsync(() -> {
            try (input) {
                return new String(input.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    private String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private Set<String> difference(Set<String> expected, Set<String> actual) {
        Set<String> missing = new LinkedHashSet<>(expected);
        missing.removeAll(actual);
        return missing;
    }

    private record CommandResult(int exitCode, String stdout, String stderr) {

        private String combined() {
            return stdout + stderr;
        }
    }
}
