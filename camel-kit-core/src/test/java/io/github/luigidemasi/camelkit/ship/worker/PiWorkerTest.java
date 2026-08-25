package io.github.luigidemasi.camelkit.ship.worker;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.github.luigidemasi.camelkit.ship.ShipDigest;
import io.github.luigidemasi.camelkit.ship.controller.ShipController;
import io.github.luigidemasi.camelkit.ship.controller.ShipRun;
import io.github.luigidemasi.camelkit.ship.evidence.ShipLocalStamp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledOnOs(OS.LINUX)
class PiWorkerTest {

    private static final String RUN_ID = "ship-0123456789abcdef0123456789abcdef";

    @TempDir
    Path temporaryDirectory;

    private Path executable;
    private Path nodeExecutable;
    private Path fixture;
    private Path workingDirectory;
    private Path sessions;
    private Path evidence;

    @BeforeEach
    void prepareFixture() throws Exception {
        fixture = Files.createDirectory(temporaryDirectory.resolve("fake-pi"));
        try (var script = Objects.requireNonNull(
                PiWorkerTest.class.getResourceAsStream(
                        "fake-pi-rpc.sh"))) {
            executable = writeExecutable(
                    fixture.resolve("pi-rpc"),
                    new String(script.readAllBytes(), StandardCharsets.UTF_8));
        }
        nodeExecutable = writeExecutable(
                fixture.resolve("node"),
                """
                        #!/bin/sh
                        if [ "${1:-}" = "--version" ]; then
                          cat "$(dirname "$0")/node-version"
                        else
                          printf '%s\\n' "$*" >> "$(dirname "$0")/node-invocations"
                          exec "$@"
                        fi
                        """);
        Files.writeString(fixture.resolve("node-version"), "v22.22.2\n");
        Files.writeString(fixture.resolve("version"), "0.83.0\n");
        Files.writeString(fixture.resolve("mode"), "success\n");
        Files.writeString(fixture.resolve("assistant-text"), "done");
        workingDirectory = Files.createDirectory(temporaryDirectory.resolve("workspace"));
        sessions = Files.createDirectory(temporaryDirectory.resolve("sessions"));
        Files.setPosixFilePermissions(
                sessions, PosixFilePermissions.fromString("rwx------"));
        evidence = Files.createDirectory(temporaryDirectory.resolve("evidence"));
    }

    @Test
    void runsReadOnlyRpcStageWithoutRetainingThePrompt() throws Exception {
        PiWorker.Result result = worker(Duration.ofSeconds(5))
                .run(request(ShipRun.Stage.DISCOVERY, "private prompt"));

        assertEquals(PiWorker.Outcome.SUCCEEDED, result.outcome());
        assertEquals(ShipLocalStamp.Support.SUPPORTED, result.support());
        assertEquals("0.83.0", result.version());
        assertNull(result.warning());
        assertEquals(
                new ShipLocalStamp.ToolVersion(
                        "node",
                        nodeExecutable.toString(),
                        "22.22.2",
                        ShipLocalStamp.Support.SUPPORTED,
                        null),
                result.node());
        assertEquals("done", result.assistantText());
        List<String> nodeInvocations = Files.readAllLines(
                fixture.resolve("node-invocations"));
        assertEquals(2, nodeInvocations.size());
        assertEquals(
                executable + " --version",
                nodeInvocations.get(0));
        assertTrue(nodeInvocations.get(1).startsWith(
                executable + " --mode rpc"));
        assertEquals(workingDirectory.toString(), Files.readString(fixture.resolve("cwd")).trim());
        assertEquals(
                "{\"id\":\"prompt-1\",\"type\":\"prompt\",\"message\":\"private prompt\"}",
                Files.readString(fixture.resolve("prompt")).trim());
        List<String> arguments = Files.readAllLines(fixture.resolve("args"));
        Path scratch = assertScratchArgument(
                sessionId(ShipRun.Stage.DISCOVERY), arguments);
        assertEquals(
                List.of(
                        "--mode",
                        "rpc",
                        "--session-id",
                        sessionId(ShipRun.Stage.DISCOVERY),
                        "--session-dir",
                        scratch.toString(),
                        "--tools",
                        "read,grep,find,ls",
                        "--no-approve",
                        "--no-extensions",
                        "--no-skills",
                        "--no-prompt-templates",
                        "--no-themes",
                        "--no-context-files",
                        "--system-prompt",
                        "",
                        "--append-system-prompt",
                        "",
                        "--offline"),
                arguments);
        assertFalse(Files.readString(fixture.resolve("args")).contains("private prompt"));
        assertTrue(hasSession(sessionId(ShipRun.Stage.DISCOVERY)));
        List<String> session = Files.readAllLines(
                sessionFile(sessionId(ShipRun.Stage.DISCOVERY)));
        assertEquals(5, session.size());
        assertEquals(
                PosixFilePermissions.fromString("rw-------"),
                Files.getPosixFilePermissions(
                        sessionFile(sessionId(ShipRun.Stage.DISCOVERY))));
        assertTrue(session.get(3).contains("\"role\":\"user\""));
        assertTrue(session.get(4).contains("\"stopReason\":\"stop\""));

        String retainedStdout = Files.readString(Path.of(result.evidence().stdoutLog()));
        String retainedStderr = Files.readString(Path.of(result.evidence().stderrLog()));
        assertFalse(retainedStdout.contains("private prompt"));
        assertFalse(retainedStdout.contains("done"));
        assertTrue(retainedStdout.contains("\"content\":\"<redacted>\""));
        assertEquals(
                "{\"bytes\":36,\"limited\":false,\"content\":\"<redacted>\"}",
                retainedStderr.trim());
        assertFalse(retainedStderr.contains("private prompt"));
        assertEquals(workingDirectory.toString(), result.evidence().workingDirectory());
        assertEquals(List.of(inputDigest()), result.evidence().inputDigests());
        assertEquals(
                ShipDigest.sha256(Files.readAllBytes(Path.of(result.evidence().stdoutLog()))),
                result.evidence().stdoutDigest());
    }

    @Test
    void requiresOptInOnlyForUnverifiedPiOrNode() throws Exception {
        PiWorker.Result maintained = worker(Duration.ofSeconds(5))
                .run(request(
                        ShipRun.Stage.DISCOVERY,
                        "maintained tools",
                        false));

        assertEquals(ShipLocalStamp.Support.SUPPORTED, maintained.support());
        assertNull(maintained.warning());
        assertEquals(
                ShipLocalStamp.Support.SUPPORTED,
                maintained.node().support());
        assertNull(maintained.node().message());

        Files.deleteIfExists(fixture.resolve("args"));
        Files.writeString(
                fixture.resolve("node-version"), "v23.0.0\n");
        IOException unaccepted = assertThrows(
                IOException.class,
                () -> worker(Duration.ofSeconds(5))
                        .run(request(
                                ShipRun.Stage.DESIGN,
                                "unverified Node",
                                false)));

        assertTrue(unaccepted.getMessage().contains("Node 23.0.0"));
        assertTrue(unaccepted.getMessage().contains("22.22.2"));
        assertTrue(unaccepted.getMessage().contains("explicitly accept"));
        assertFalse(Files.exists(fixture.resolve("args")));

        PiWorker.Result accepted = worker(Duration.ofSeconds(5))
                .run(request(
                        ShipRun.Stage.DESIGN,
                        "unverified Node",
                        true));
        assertEquals(
                ShipLocalStamp.Support.EXPERIMENTAL,
                accepted.node().support());
        assertTrue(accepted.node().message().contains("22.22.2"));
    }

    @Test
    void interruptionReapsTheNodeVersionProbeBeforePiStarts()
            throws Exception {
        Path blockingNode = writeExecutable(
                fixture.resolve("blocking-node"),
                """
                        #!/bin/sh
                        if [ "${1:-}" = "--version" ]; then
                          printf '%s\\n' "$$" > "$(dirname "$0")/node-parent-pid"
                          sleep 300 &
                          printf '%s\\n' "$!" > "$(dirname "$0")/node-child-pid"
                          touch "$(dirname "$0")/node-ready"
                          wait
                        else
                          exec "$@"
                        fi
                        """);
        PiWorker blocked = new PiWorker(
                executable,
                List.of("0.83.0"),
                blockingNode,
                "22.22.2",
                Duration.ofSeconds(30));
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicBoolean interrupted = new AtomicBoolean();
        Thread invocation = new Thread(() -> {
            try {
                blocked.run(request(
                        ShipRun.Stage.DISCOVERY, "blocked Node probe"));
                failure.set(new AssertionError(
                        "Node version probe unexpectedly completed"));
            } catch (InterruptedException expected) {
                interrupted.set(Thread.currentThread().isInterrupted());
            } catch (Throwable unexpected) {
                failure.set(unexpected);
            }
        });

        invocation.start();
        awaitFile(fixture.resolve("node-ready"));
        invocation.interrupt();
        invocation.join(Duration.ofSeconds(10).toMillis());

        assertFalse(invocation.isAlive());
        assertNull(failure.get());
        assertTrue(interrupted.get());
        assertFalse(Files.exists(fixture.resolve("args")));
        awaitGone(Long.parseLong(
                Files.readString(
                        fixture.resolve("node-parent-pid")).trim()));
        awaitGone(Long.parseLong(
                Files.readString(
                        fixture.resolve("node-child-pid")).trim()));
    }

    @Test
    void executeStageUsesTheExplicitWriteToolAllowlist() throws Exception {
        PiWorker.Result result = worker(Duration.ofSeconds(5))
                .run(request(ShipRun.Stage.EXECUTE, "execute"));

        assertEquals(PiWorker.Outcome.SUCCEEDED, result.outcome());
        List<String> arguments = Files.readAllLines(fixture.resolve("args"));
        Path scratch = assertScratchArgument(
                sessionId(ShipRun.Stage.EXECUTE), arguments);
        assertEquals(
                List.of(
                        "--mode",
                        "rpc",
                        "--session-id",
                        sessionId(ShipRun.Stage.EXECUTE),
                        "--session-dir",
                        scratch.toString(),
                        "--tools",
                        "read,bash,edit,write,grep,find,ls",
                        "--no-approve",
                        "--no-extensions",
                        "--no-skills",
                        "--no-prompt-templates",
                        "--no-themes",
                        "--no-context-files",
                        "--system-prompt",
                        "",
                        "--append-system-prompt",
                        "",
                        "--offline"),
                arguments);
        assertTrue(hasSession(sessionId(ShipRun.Stage.EXECUTE)));
    }

    @Test
    void changedInputDigestUsesANewStageSession() throws Exception {
        PiWorker worker = worker(Duration.ofSeconds(5));
        String changedDigest = ShipDigest.sha256(
                "changed input".getBytes(StandardCharsets.UTF_8));

        worker.run(request(ShipRun.Stage.DESIGN, "first"));
        worker.run(new PiWorker.Request(
                RUN_ID,
                ShipRun.Stage.DESIGN,
                1,
                workingDirectory,
                sessions,
                evidence,
                changedDigest,
                true,
                "second"));

        String first = sessionId(ShipRun.Stage.DESIGN);
        String changed = sessionId(RUN_ID, ShipRun.Stage.DESIGN, changedDigest);
        assertEquals(List.of(first, changed), Files.readAllLines(fixture.resolve("session-ids")));
        assertTrue(hasSession(first));
        assertTrue(hasSession(changed));
        assertFalse(Files.exists(fixture.resolve("resumed-sessions")));
    }

    @Test
    void requiresSettledEventAndAcceptsUnicodeJsonSeparators() throws Exception {
        String assistant = "before\u2028middle\u2029after";
        Files.writeString(fixture.resolve("mode"), "settled\n");
        Files.writeString(fixture.resolve("assistant-text"), assistant);

        PiWorker.Result result = worker(Duration.ofSeconds(5))
                .run(request(ShipRun.Stage.DESIGN, "prompt"));

        assertEquals(PiWorker.Outcome.SUCCEEDED, result.outcome());
        assertEquals(assistant, result.assistantText());
        assertFalse(result.toString().contains(assistant));

        Files.writeString(fixture.resolve("mode"), "terminal-before-response\n");
        assertEquals(
                PiWorker.Outcome.SUCCEEDED,
                worker(Duration.ofSeconds(5))
                        .run(request(ShipRun.Stage.DESIGN, "second"))
                        .outcome());
    }

    @Test
    void requiresExplicitExperimentalAcceptanceBeforeStartingTheStage() throws Exception {
        Files.writeString(fixture.resolve("version"), "0.81.1\n");
        PiWorker.Request rejected = request(ShipRun.Stage.DISCOVERY, "private prompt", false);

        IOException failure = assertThrows(
                IOException.class,
                () -> worker(Duration.ofSeconds(5)).run(rejected));

        assertTrue(failure.getMessage().contains("explicitly accept experimental Pi"));
        assertFalse(Files.exists(fixture.resolve("args")));
        assertFalse(Files.exists(fixture.resolve("session-dirs")));
        assertFalse(Files.exists(fixture.resolve("prompt")));
        assertFalse(hasSession(sessionId(ShipRun.Stage.DISCOVERY)));
        assertFalse(rejected.toString().contains("private prompt"));
    }

    @Test
    void rejectsStrictRpcProtocolViolations() throws Exception {
        PiWorker worker = worker(Duration.ofSeconds(5));

        for (String mode : List.of(
                "malformed",
                "invalid-utf",
                "duplicate",
                "response-wrong-bool",
                "response-wrong-id",
                "response-wrong-command",
                "response-extra-field",
                "missing-content",
                "retry-wrong-bool",
                "missing-settled",
                "settled-extra",
                "trailing")) {
            Files.writeString(fixture.resolve("mode"), mode + "\n");

            PiWorker.Result result = worker.run(request(ShipRun.Stage.PLAN, "prompt"));

            assertEquals(PiWorker.Outcome.FAILED, result.outcome(), mode);
            assertNull(result.assistantText(), mode);
            assertFalse(hasSession(sessionId(ShipRun.Stage.PLAN)), mode);
        }

        Files.writeString(
                fixture.resolve("mode"), "tool-orphan-stop\n");
        IOException orphan = assertThrows(
                IOException.class,
                () -> worker.run(request(
                        ShipRun.Stage.PLAN, "orphan")));
        assertTrue(orphan.getMessage().contains("all tool results"));
        assertFalse(hasSession(sessionId(ShipRun.Stage.PLAN)));
    }

    @Test
    void persistsSemanticErrorAndLengthTurnsForResume() throws Exception {
        PiWorker worker = worker(Duration.ofSeconds(5));
        for (String mode : List.of("stop-error", "stop-length")) {
            ShipRun.Stage stage = "stop-error".equals(mode)
                    ? ShipRun.Stage.DISCOVERY
                    : ShipRun.Stage.DESIGN;
            Files.writeString(fixture.resolve("mode"), mode + "\n");

            PiWorker.Result result = worker.run(request(stage, "prompt"));

            assertEquals(PiWorker.Outcome.FAILED, result.outcome());
            assertTrue(result.failure().contains(mode.substring("stop-".length())));
            assertTrue(hasSession(sessionId(stage)));
        }
    }

    @Test
    void redactsSensitiveFailureComposedAfterSessionValidation()
            throws Exception {
        String secret = "Pi assistant settled with stop reason error";
        Files.writeString(fixture.resolve("mode"), "stop-error\n");
        PiWorker.Request request = request(
                ShipRun.Stage.DISCOVERY, "prompt");
        PiWorker worker = worker(
                Duration.ofSeconds(5), Map.of("API_TOKEN", secret));

        PiWorker.Result result = worker.run(request);

        assertEquals(PiWorker.Outcome.FAILED, result.outcome());
        assertEquals("Failed", result.failure());
        assertEquals(result, worker.recover(request).orElseThrow());
        try (var markers = Files.list(
                sessions.resolve(".camel-kit-results"))) {
            assertFalse(Files.readString(
                    markers.findFirst().orElseThrow()).contains(secret));
        }
    }

    @Test
    void rejectsSensitiveVersionDiagnosticsBeforePublication()
            throws Exception {
        Files.writeString(fixture.resolve("version"), "0.81.1\n");
        String secret = "Pi 0.81.1 is unverified; install maintained Pi 0.84.2";
        PiWorker.Request request = request(
                ShipRun.Stage.DISCOVERY, "prompt");
        PiWorker worker = new PiWorker(
                executable,
                List.of("0.84.2", "0.83.0"),
                nodeExecutable,
                "22.22.2",
                Duration.ofSeconds(5),
                Map.of("API_TOKEN", secret));

        IOException failure = assertThrows(
                IOException.class,
                () -> worker.run(request));

        assertEquals(
                "Pi output contained a sensitive environment value",
                failure.getMessage());
        assertTrue(worker.recover(request).isEmpty());
        assertFalse(Files.exists(fixture.resolve("session-ids")));
    }

    @Test
    void bindsRetryToolAndCompactionRecordsInRpcOrder() throws Exception {
        PiWorker worker = worker(Duration.ofSeconds(5));
        List<String> modes = List.of(
                "retry-then-success",
                "tool-sequence",
                "compaction-success",
                "compaction-failed",
                "compaction-aborted");
        ShipRun.Stage[] stages = ShipRun.Stage.values();
        for (int index = 0; index < modes.size(); index++) {
            Files.writeString(
                    fixture.resolve("mode"), modes.get(index) + "\n");

            PiWorker.Result result = worker.run(
                    request(stages[index], "prompt-" + index));

            assertEquals(
                    PiWorker.Outcome.SUCCEEDED,
                    result.outcome(),
                    modes.get(index));
            String persisted = Files.readString(
                    sessionFile(sessionId(stages[index])));
            assertEquals(
                    modes.get(index).equals("retry-then-success")
                            || modes.get(index).equals("compaction-success"),
                    persisted.contains("\"type\":\"compaction\""),
                    modes.get(index));
        }
    }

    @Test
    void acceptsProviderAutoRetryWithoutCompaction() throws Exception {
        Files.writeString(fixture.resolve("mode"), "auto-retry-success\n");

        PiWorker.Result result = worker(Duration.ofSeconds(5)).run(
                request(ShipRun.Stage.DISCOVERY, "auto-retry"));

        assertEquals(PiWorker.Outcome.SUCCEEDED, result.outcome());
        assertFalse(Files.readString(
                sessionFile(sessionId(ShipRun.Stage.DISCOVERY)))
                .contains("\"type\":\"compaction\""));
    }

    @Test
    void acceptsLengthOverflowCompactionContinuation() throws Exception {
        Files.writeString(
                fixture.resolve("mode"), "retry-length-then-success\n");

        PiWorker.Result result = worker(Duration.ofSeconds(5)).run(
                request(ShipRun.Stage.PLAN, "length-overflow"));

        assertEquals(PiWorker.Outcome.SUCCEEDED, result.outcome());
        String persisted = Files.readString(
                sessionFile(sessionId(ShipRun.Stage.PLAN)));
        assertTrue(persisted.contains("\"stopReason\":\"length\""));
        assertTrue(persisted.contains("\"type\":\"compaction\""));
        assertTrue(persisted.contains("\"stopReason\":\"stop\""));
    }

    @Test
    void allowsCompactionBeforeTheNewUserButRejectsUnrelatedAssistants()
            throws Exception {
        PiWorker worker = worker(Duration.ofSeconds(5));
        worker.run(request(ShipRun.Stage.DESIGN, "baseline"));
        Files.writeString(fixture.resolve("mode"), "pre-user-compaction\n");

        PiWorker.Result resumed = worker.run(new PiWorker.Request(
                RUN_ID,
                ShipRun.Stage.DESIGN,
                2,
                workingDirectory,
                sessions,
                evidence,
                inputDigest(),
                true,
                "current"));

        assertEquals(PiWorker.Outcome.SUCCEEDED, resumed.outcome());
        List<String> persisted = Files.readAllLines(
                sessionFile(sessionId(ShipRun.Stage.DESIGN)));
        assertEquals(8, persisted.size());
        assertTrue(persisted.get(5).contains("\"type\":\"compaction\""));
        assertTrue(persisted.get(6).contains("\"role\":\"user\""));

        Files.writeString(fixture.resolve("mode"), "unrelated-intermediate\n");
        IOException unrelated = assertThrows(
                IOException.class,
                () -> worker.run(request(
                        ShipRun.Stage.PLAN, "unrelated")));
        assertTrue(unrelated.getMessage().contains("intermediate assistant"));
        assertFalse(hasSession(sessionId(ShipRun.Stage.PLAN)));
    }

    @Test
    void discardsValidatedScratchAfterTrailingOutputExitFailureOrRetentionFailure()
            throws Exception {
        PiWorker worker = worker(Duration.ofSeconds(10));
        for (String mode : List.of("trailing", "settled-nonzero", "settled-hang")) {
            ShipRun.Stage stage = switch (mode) {
                case "trailing" -> ShipRun.Stage.DISCOVERY;
                case "settled-nonzero" -> ShipRun.Stage.DESIGN;
                default -> ShipRun.Stage.PLAN;
            };
            Files.writeString(fixture.resolve("mode"), mode + "\n");

            PiWorker.Result result = worker.run(request(stage, "prompt"));

            assertEquals(PiWorker.Outcome.FAILED, result.outcome(), mode);
            assertFalse(hasSession(sessionId(stage)), mode);
            assertNoScratchDirectories();
        }

        Files.writeString(fixture.resolve("mode"), "success\n");
        worker.run(request(ShipRun.Stage.VALIDATE, "baseline"));
        Path canonical = sessionFile(sessionId(ShipRun.Stage.VALIDATE));
        byte[] before = Files.readAllBytes(canonical);
        Files.writeString(fixture.resolve("mode"), "settled-nonzero\n");
        PiWorker.Result nonzero = worker.run(new PiWorker.Request(
                RUN_ID,
                ShipRun.Stage.VALIDATE,
                2,
                workingDirectory,
                sessions,
                evidence,
                inputDigest(),
                true,
                "current"));
        assertEquals(PiWorker.Outcome.FAILED, nonzero.outcome());
        assertArrayEquals(before, Files.readAllBytes(canonical));

        Files.writeString(fixture.resolve("mode"), "retention-failure\n");
        try {
            assertThrows(
                    IOException.class,
                    () -> worker.run(request(
                            ShipRun.Stage.EXECUTE, "prompt")));
        } finally {
            Files.setPosixFilePermissions(
                    evidence, PosixFilePermissions.fromString("rwxr-xr-x"));
        }
        assertFalse(hasSession(sessionId(ShipRun.Stage.EXECUTE)));
        assertNoScratchDirectories();
    }

    @Test
    void redactsUntrustedRpcMetadataFromRetainedOutput() throws Exception {
        Files.writeString(fixture.resolve("mode"), "metadata-secret\n");

        PiWorker.Result result = worker(Duration.ofSeconds(5))
                .run(request(ShipRun.Stage.PLAN, "prompt"));

        assertEquals(PiWorker.Outcome.FAILED, result.outcome());
        String retained = Files.readString(Path.of(result.evidence().stdoutLog()));
        assertFalse(retained.contains("fixture-secret-fragment"));
        assertTrue(retained.contains("\"type\":\"event-redacted\""));
        assertTrue(retained.contains("\"id\":\"<redacted>\""));
        assertTrue(retained.contains("\"command\":\"<redacted>\""));
    }

    @Test
    void rejectsSensitiveEnvironmentValuesInAssistantAndToolResults()
            throws Exception {
        List<String> names = List.of(
                "OPENAI_API_KEY", "AUTH", "JWT", "PASS", "TOKEN");
        List<String> values = List.of(
                "fixture-secret-123", "auth-value", "jwt-value", "password-value", "token-value");
        ShipRun.Stage[] stages = ShipRun.Stage.values();
        for (int index = 0; index < names.size(); index++) {
            String secret = values.get(index);
            ShipRun.Stage stage = stages[index];
            String prompt = "prompt-" + index;
            Files.writeString(fixture.resolve("secret"), secret);
            Files.writeString(
                    fixture.resolve("mode"),
                    (index % 2 == 0 ? "leak-assistant" : "leak-tool")
                                             + "\n");
            PiWorker sensitiveWorker = worker(
                    Duration.ofSeconds(5),
                    Map.of(names.get(index), secret));

            IOException failure = assertThrows(
                    IOException.class,
                    () -> sensitiveWorker.run(request(
                            stage, prompt)));

            assertTrue(failure.getMessage().contains(
                    "sensitive environment value"));
            assertFalse(hasSession(sessionId(stage)));
            assertNoScratchDirectories();
            assertEvidenceDoesNotContain(secret);
        }

        String cwd = workingDirectory.toString();
        Files.writeString(fixture.resolve("secret"), cwd);
        Files.writeString(fixture.resolve("mode"), "leak-assistant\n");
        PiWorker.Result allowed = worker(
                Duration.ofSeconds(5),
                Map.of("PWD", cwd, "OLDPWD", cwd))
                .run(request(ShipRun.Stage.VALIDATE, "pwd"));
        assertEquals(PiWorker.Outcome.SUCCEEDED, allowed.outcome());
        assertEquals(cwd, allowed.assistantText());
    }

    @Test
    void ignoresBooleanValuesOnlyForFeatureToggleEnvironmentNames()
            throws Exception {
        PiWorker.Result result = worker(
                Duration.ofSeconds(5),
                Map.of(
                        "AUTH_ENABLED", "true",
                        "USE_JWT", "1",
                        "ENABLE_COOKIE", "false"))
                .run(request(ShipRun.Stage.DISCOVERY, "prompt"));

        assertEquals(PiWorker.Outcome.SUCCEEDED, result.outcome());
    }

    @Test
    void shortSensitiveValuesDoNotRejectAnOrdinaryPersistedTurn()
            throws Exception {
        ShipRun.Stage stage = ShipRun.Stage.DESIGN;

        PiWorker.Result result = worker(
                Duration.ofSeconds(5),
                Map.of(
                        "AUTH", "0",
                        "DB_PASSWORD", "test",
                        "COOKIE_NAME", "session",
                        "TOKEN", "1"))
                .run(request(stage, "test prompt"));

        assertEquals(PiWorker.Outcome.SUCCEEDED, result.outcome());
        assertEquals("0.83.0", result.version());
        assertTrue(hasSession(sessionId(stage)));
    }

    @Test
    void usesTheInjectedEnvironmentForVersionAndRpcProcesses()
            throws Exception {
        Path wrapper = writeExecutable(
                fixture.resolve("pi-env"),
                """
                        #!/bin/sh
                        fixture=$(dirname "$0")
                        if [ "${1:-}" = "--version" ]; then
                          /usr/bin/env > "$fixture/version-env"
                        else
                          /usr/bin/env > "$fixture/rpc-env"
                        fi
                        exec "$fixture/pi-rpc" "$@"
                        """);
        String path = Objects.requireNonNull(
                System.getenv("PATH"), "test PATH");
        Map<String, String> environment = Map.of(
                "PATH", path,
                "PI_WORKER_SENTINEL", "injected");

        PiWorker.Result result = new PiWorker(
                wrapper,
                List.of("0.83.0"),
                nodeExecutable,
                "22.22.2",
                Duration.ofSeconds(5),
                environment)
                .run(request(ShipRun.Stage.DISCOVERY, "prompt"));

        assertEquals(PiWorker.Outcome.SUCCEEDED, result.outcome());
        for (String phase : List.of("version-env", "rpc-env")) {
            List<String> observed = Files.readAllLines(
                    fixture.resolve(phase));
            assertTrue(observed.contains("PI_WORKER_SENTINEL=injected"));
            assertTrue(observed.contains("PATH=" + path));
            assertTrue(observed.stream()
                    .noneMatch(value -> value.startsWith("HOME=")));
        }
    }

    @Test
    void rejectsSensitiveEnvironmentValuesInObjectKeysAndScalarValues()
            throws Exception {
        List<String> modes = List.of(
                "leak-assistant-key", "leak-tool-number");
        List<String> values = List.of(
                "nested-secret-key", "86753090");
        ShipRun.Stage[] stages = ShipRun.Stage.values();
        for (int index = 0; index < modes.size(); index++) {
            String secret = values.get(index);
            ShipRun.Stage stage = stages[index];
            String prompt = "prompt-" + index;
            Files.writeString(fixture.resolve("secret"), secret);
            Files.writeString(
                    fixture.resolve("mode"), modes.get(index) + "\n");

            IOException failure = assertThrows(
                    IOException.class,
                    () -> worker(
                            Duration.ofSeconds(5),
                            Map.of("API_TOKEN", secret))
                            .run(request(stage, prompt)));

            assertTrue(failure.getMessage().contains(
                    "sensitive environment value"));
            assertFalse(hasSession(sessionId(stage)));
            assertNoScratchDirectories();
            assertEvidenceDoesNotContain(secret);
        }
    }

    @Test
    void redactsSensitiveEnvironmentCollisionsFromLocalEvidence()
            throws Exception {
        List<String> secrets = List.of("rpc", "prompt", "bytes");
        ShipRun.Stage[] stages = {
                ShipRun.Stage.DISCOVERY,
                ShipRun.Stage.DESIGN,
                ShipRun.Stage.PLAN
        };
        for (int index = 0; index < secrets.size(); index++) {
            String secret = secrets.get(index);
            Files.writeString(fixture.resolve("mode"), "success\n");

            PiWorker.Result result = worker(
                    Duration.ofSeconds(5),
                    Map.of("API_TOKEN", secret))
                    .run(request(stages[index], "safe-input-" + index));

            assertEquals(PiWorker.Outcome.SUCCEEDED, result.outcome());
            assertTrue(result.evidence().redactedArguments().stream()
                    .noneMatch(argument -> argument.contains(secret)));
            assertFalse(Files.readString(
                    Path.of(result.evidence().stdoutLog()))
                    .contains(secret));
            assertFalse(Files.readString(
                    Path.of(result.evidence().stderrLog()))
                    .contains(secret));
        }
    }

    @Test
    void acceptsCredentialedUrlInSensitiveEnvironment() throws Exception {
        String secret = "https://user:hunter2@host/path";

        PiWorker.Result result = worker(
                Duration.ofSeconds(5), Map.of("API_TOKEN", secret))
                .run(request(ShipRun.Stage.DISCOVERY, "prompt"));

        assertEquals(PiWorker.Outcome.SUCCEEDED, result.outcome());
        assertFalse(result.evidence().toString().contains("user:hunter2"));
    }

    @Test
    void ignoresCumulativeStreamingSnapshotsWithoutLosingTheTerminal()
            throws Exception {
        String[] modes = {
                "cumulative-update-burst",
                "cumulative-tool-update-burst"
        };
        ShipRun.Stage[] stages = {
                ShipRun.Stage.DISCOVERY,
                ShipRun.Stage.DESIGN
        };
        for (int index = 0; index < modes.length; index++) {
            Files.writeString(
                    fixture.resolve("mode"), modes[index] + "\n");

            PiWorker.Result result = worker(Duration.ofSeconds(30))
                    .run(request(stages[index], "streaming-" + index));

            assertEquals(PiWorker.Outcome.SUCCEEDED, result.outcome());
            assertEquals("done", result.assistantText());
            assertTrue(hasSession(sessionId(stages[index])));
            assertTrue(Files.size(Path.of(result.evidence().stdoutLog()))
                       < 1024 * 1024);
        }
    }

    @Test
    void retainsNonzeroExitAndBoundsExcessiveOutput() throws Exception {
        Files.writeString(fixture.resolve("mode"), "nonzero\n");
        PiWorker.Result nonzero = worker(Duration.ofSeconds(5))
                .run(request(ShipRun.Stage.VALIDATE, "prompt"));

        Files.writeString(fixture.resolve("mode"), "oversized\n");
        PiWorker.Result oversized = worker(Duration.ofSeconds(5))
                .run(request(ShipRun.Stage.VALIDATE, "prompt"));

        assertEquals(PiWorker.Outcome.FAILED, nonzero.outcome());
        assertEquals(7, nonzero.evidence().exitCode());
        assertTrue(nonzero.failure().contains("status 7"));
        assertEquals(PiWorker.Outcome.FAILED, oversized.outcome());
        assertTrue(oversized.failure().contains("retained-output limit"));
        assertTrue(hasSession(sessionId(ShipRun.Stage.VALIDATE)));
        assertTrue(Files.readString(sessionFile(
                sessionId(ShipRun.Stage.VALIDATE)))
                .contains("\"stopReason\":\"aborted\""));
        assertTrue(Files.size(Path.of(oversized.evidence().stderrLog())) <= 16 * 1024 * 1024);
        assertEquals(
                ShipDigest.sha256(Files.readAllBytes(Path.of(oversized.evidence().stderrLog()))),
                oversized.evidence().stderrDigest());
    }

    @Test
    void boundsCumulativeMaterialRpcOutput() throws Exception {
        Files.writeString(
                fixture.resolve("mode"), "material-output-burst\n");

        PiWorker.Result result = worker(Duration.ofSeconds(10))
                .run(request(ShipRun.Stage.VALIDATE, "prompt"));

        assertEquals(PiWorker.Outcome.FAILED, result.outcome());
        assertTrue(result.evidence().outputLimited());
        assertTrue(result.failure().contains("retained-output limit"));
        assertTrue(hasSession(sessionId(ShipRun.Stage.VALIDATE)));
        assertTrue(Files.readString(sessionFile(
                sessionId(ShipRun.Stage.VALIDATE)))
                .contains("\"stopReason\":\"aborted\""));
        assertNoScratchDirectories();
    }

    @Test
    void rejectsUnverifiableOutputAfterAcknowledgedAbort() throws Exception {
        Files.writeString(
                fixture.resolve("mode"),
                "material-output-burst-trailing\n");

        PiWorker.Result result = worker(Duration.ofSeconds(10))
                .run(request(ShipRun.Stage.EXECUTE, "prompt"));

        assertEquals(PiWorker.Outcome.FAILED, result.outcome());
        assertTrue(result.evidence().outputLimited());
        assertFalse(hasSession(sessionId(ShipRun.Stage.EXECUTE)));
        assertNoScratchDirectories();
    }

    @Test
    void supportsEveryCertifiedPiVersion() throws Exception {
        List<String> certified = List.of("0.84.2", "0.83.0");
        List<ShipRun.Stage> stages = List.of(ShipRun.Stage.DISCOVERY, ShipRun.Stage.DESIGN);
        for (int i = 0; i < certified.size(); i++) {
            String version = certified.get(i);
            Files.writeString(fixture.resolve("version"), version + "\n");
            Files.deleteIfExists(fixture.resolve("args"));
            Files.deleteIfExists(fixture.resolve("prompt"));

            PiWorker.Result result = new PiWorker(
                    executable,
                    certified,
                    nodeExecutable,
                    "22.22.2",
                    Duration.ofSeconds(5))
                    .run(request(stages.get(i), "prompt"));

            assertEquals(PiWorker.Outcome.SUCCEEDED, result.outcome(), version);
            assertEquals(ShipLocalStamp.Support.SUPPORTED, result.support(), version);
            assertEquals(version, result.version());
            assertNull(result.warning(), version);
        }
    }

    @Test
    void experimentalWarningNamesThePrimaryCertifiedVersion() throws Exception {
        Files.writeString(fixture.resolve("version"), "0.81.1\n");

        PiWorker.Result experimental = new PiWorker(
                executable,
                List.of("0.84.2", "0.83.0"),
                nodeExecutable,
                "22.22.2",
                Duration.ofSeconds(5))
                .run(request(ShipRun.Stage.DISCOVERY, "prompt"));

        assertEquals(ShipLocalStamp.Support.EXPERIMENTAL, experimental.support());
        assertTrue(experimental.warning().contains("install maintained Pi 0.84.2"));
    }

    @Test
    void reportsVersionCompatibilityAndExecutableGuidance() throws Exception {
        Files.writeString(fixture.resolve("version"), "0.81.1\n");

        PiWorker.Result experimental = worker(Duration.ofSeconds(5))
                .run(request(ShipRun.Stage.DISCOVERY, "prompt"));

        assertEquals(PiWorker.Outcome.SUCCEEDED, experimental.outcome());
        assertEquals(ShipLocalStamp.Support.EXPERIMENTAL, experimental.support());
        assertEquals("0.81.1", experimental.version());
        assertTrue(experimental.warning().contains("0.83.0"));
        assertTrue(experimental.warning().contains("unverified"));

        Files.deleteIfExists(fixture.resolve("args"));
        Files.deleteIfExists(fixture.resolve("prompt"));
        Files.writeString(fixture.resolve("version"), "0.80.3\n");
        IOException incompatible = assertThrows(
                IOException.class,
                () -> worker(Duration.ofSeconds(5))
                        .run(request(ShipRun.Stage.DESIGN, "prompt")));
        assertTrue(incompatible.getMessage().contains("settled RPC event"));
        assertFalse(Files.exists(fixture.resolve("args")));
        assertFalse(Files.exists(fixture.resolve("prompt")));
        assertFalse(hasSession(sessionId(ShipRun.Stage.DESIGN)));

        Files.writeString(fixture.resolve("version"), "0.81.1\n");
        IOException unaccepted = assertThrows(
                IOException.class,
                () -> worker(Duration.ofSeconds(5))
                        .run(request(ShipRun.Stage.DESIGN, "prompt", false)));
        assertTrue(unaccepted.getMessage().contains("explicitly accept"));
        assertFalse(Files.exists(fixture.resolve("args")));

        Files.writeString(fixture.resolve("version"), "not-a-version\n");
        IOException invalid = assertThrows(
                IOException.class,
                () -> worker(Duration.ofSeconds(5))
                        .run(request(ShipRun.Stage.DISCOVERY, "prompt")));
        IllegalArgumentException missing = assertThrows(
                IllegalArgumentException.class,
                () -> new PiWorker(
                        temporaryDirectory.resolve("missing-pi"),
                        List.of("0.83.0"),
                        nodeExecutable,
                        "22.22.2",
                        Duration.ofSeconds(5)));

        assertTrue(invalid.getMessage().contains("invalid version"));
        assertTrue(missing.getMessage().contains("install Pi"));
    }

    @Test
    void followsTheConfiguredExecutableSymlink() throws Exception {
        Path symlink = fixture.resolve("pi-link");
        Files.createSymbolicLink(symlink, executable.getFileName());

        PiWorker.Result result = new PiWorker(
                symlink,
                List.of("0.83.0"),
                nodeExecutable,
                "22.22.2",
                Duration.ofSeconds(5))
                .run(request(ShipRun.Stage.DISCOVERY, "prompt"));

        assertEquals(PiWorker.Outcome.SUCCEEDED, result.outcome());
        assertEquals(executable.toString(), result.evidence().executable());
    }

    @Test
    void acceptsAncestorSymlinksButRejectsLeafAndOverlappingRuntimeDirectories()
            throws Exception {
        Path realParent = Files.createDirectory(temporaryDirectory.resolve("real-parent"));
        Path realWorking = Files.createDirectory(realParent.resolve("working"));
        Path aliasParent = temporaryDirectory.resolve("alias-parent");
        Files.createSymbolicLink(aliasParent, realParent.getFileName());
        PiWorker.Request aliased = new PiWorker.Request(
                RUN_ID,
                ShipRun.Stage.DISCOVERY,
                1,
                aliasParent.resolve(realWorking.getFileName()),
                sessions,
                evidence,
                inputDigest(),
                true,
                "prompt");
        PiWorker.Result aliasedResult = worker(Duration.ofSeconds(5)).run(aliased);
        assertEquals(PiWorker.Outcome.SUCCEEDED, aliasedResult.outcome());
        assertEquals(
                realWorking.toString(),
                aliasedResult.evidence().workingDirectory());

        Path leafAlias = temporaryDirectory.resolve("working-link");
        Files.createSymbolicLink(leafAlias, realWorking);
        PiWorker.Request leaf = new PiWorker.Request(
                RUN_ID,
                ShipRun.Stage.DESIGN,
                1,
                leafAlias,
                sessions,
                evidence,
                inputDigest(),
                true,
                "prompt");
        Path nestedEvidence = Files.createDirectory(workingDirectory.resolve("nested-evidence"));
        PiWorker.Request overlapsWorking = new PiWorker.Request(
                RUN_ID,
                ShipRun.Stage.DISCOVERY,
                1,
                workingDirectory,
                sessions,
                nestedEvidence,
                inputDigest(),
                true,
                "prompt");
        PiWorker.Request sharedStateDirectory = new PiWorker.Request(
                RUN_ID,
                ShipRun.Stage.DISCOVERY,
                1,
                workingDirectory,
                sessions,
                sessions,
                inputDigest(),
                true,
                "prompt");

        IOException leafFailure = assertThrows(
                IOException.class,
                () -> worker(Duration.ofSeconds(5)).run(leaf));
        IOException workingFailure = assertThrows(
                IOException.class,
                () -> worker(Duration.ofSeconds(5)).run(overlapsWorking));
        IOException stateFailure = assertThrows(
                IOException.class,
                () -> worker(Duration.ofSeconds(5)).run(sharedStateDirectory));

        assertTrue(leafFailure.getMessage().contains("real directory"));
        assertTrue(workingFailure.getMessage().contains("disjoint"));
        assertTrue(stateFailure.getMessage().contains("disjoint"));
    }

    @Test
    void rejectsInsecureSessionDirectoryBeforeLaunchingPi() throws Exception {
        Files.setPosixFilePermissions(
                sessions, PosixFilePermissions.fromString("rwxr-x---"));

        IOException permissions = assertThrows(
                IOException.class,
                () -> worker(Duration.ofSeconds(5))
                        .run(request(ShipRun.Stage.DISCOVERY, "prompt")));

        assertTrue(permissions.getMessage().contains("0700"));
        assertFalse(Files.exists(fixture.resolve("args")));
    }

    @Test
    void ignoresUnrelatedSessionEntriesButRejectsUnsafeMatchingLeaves()
            throws Exception {
        Files.createSymbolicLink(
                sessions.resolve("unrelated-entry"), fixture.resolve("version"));

        PiWorker.Result result = worker(Duration.ofSeconds(5))
                .run(request(ShipRun.Stage.DISCOVERY, "prompt"));

        assertEquals(PiWorker.Outcome.SUCCEEDED, result.outcome());
        ShipRun.Stage symlinkStage = ShipRun.Stage.DESIGN;
        Path matchingSymlink = sessions.resolve(
                "evil_" + sessionId(symlinkStage) + ".jsonl");
        Files.createSymbolicLink(matchingSymlink, fixture.resolve("version"));

        IOException symlink = assertThrows(
                IOException.class,
                () -> worker(Duration.ofSeconds(5))
                        .run(request(symlinkStage, "prompt")));

        assertTrue(symlink.getMessage().contains("symbolic link"));
        Files.delete(matchingSymlink);
        ShipRun.Stage permissionsStage = ShipRun.Stage.PLAN;
        Path matchingFile = sessions.resolve(
                "evil_" + sessionId(permissionsStage) + ".jsonl");
        Files.writeString(matchingFile, "{}\n");
        Files.setPosixFilePermissions(
                matchingFile, PosixFilePermissions.fromString("rw-r--r--"));

        IOException permissions = assertThrows(
                IOException.class,
                () -> worker(Duration.ofSeconds(5))
                        .run(request(permissionsStage, "prompt")));

        assertTrue(permissions.getMessage().contains("0600"));
    }

    @Test
    void rejectsAmbiguousPersistedStageSessions() throws Exception {
        Files.writeString(fixture.resolve("mode"), "session-duplicate\n");

        IOException failure = assertThrows(
                IOException.class,
                () -> worker(Duration.ofSeconds(5))
                        .run(request(ShipRun.Stage.PLAN, "prompt")));

        assertTrue(failure.getMessage().contains("session"));
    }

    @Test
    void requiresANonemptySessionAndAppendOnlyResume() throws Exception {
        Files.writeString(fixture.resolve("mode"), "empty-session\n");

        IOException empty = assertThrows(
                IOException.class,
                () -> worker(Duration.ofSeconds(5))
                        .run(request(ShipRun.Stage.DISCOVERY, "prompt")));

        assertTrue(empty.getMessage().contains("invalid size"));

        Files.writeString(fixture.resolve("mode"), "success\n");
        PiWorker worker = worker(Duration.ofSeconds(5));
        worker.run(request(ShipRun.Stage.DESIGN, "first"));
        byte[] before = Files.readAllBytes(sessionFile(sessionId(ShipRun.Stage.DESIGN)));
        Files.writeString(fixture.resolve("mode"), "unchanged-session\n");

        IOException unchanged = assertThrows(
                IOException.class,
                () -> worker.run(request(ShipRun.Stage.DESIGN, "second")));

        assertTrue(unchanged.getMessage().contains("did not append"));
        assertTrue(java.util.Arrays.equals(
                before, Files.readAllBytes(sessionFile(sessionId(ShipRun.Stage.DESIGN)))));

    }

    @Test
    void serializesSessionsAndCleansAbandonedScratch() throws Exception {
        String sessionId = sessionId(ShipRun.Stage.DISCOVERY);
        Path abandoned = Files.createDirectory(
                sessions.resolve(".ship-pi-" + sessionId + "-abandoned"));
        Files.setPosixFilePermissions(
                abandoned, PosixFilePermissions.fromString("rwx------"));
        Files.writeString(abandoned.resolve("partial"), "partial");

        PiWorker.Result result = worker(Duration.ofSeconds(5))
                .run(request(ShipRun.Stage.DISCOVERY, "prompt"));

        assertEquals(PiWorker.Outcome.SUCCEEDED, result.outcome());
        assertFalse(Files.exists(abandoned));
        assertNoScratchDirectories();

        String lockedSession = sessionId(ShipRun.Stage.DESIGN);
        Path lockPath = sessions.resolve("." + lockedSession + ".lock");
        try (FileChannel channel = FileChannel.open(
                lockPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE);
             FileLock ignored = channel.lock()) {
            Files.setPosixFilePermissions(
                    lockPath, PosixFilePermissions.fromString("rw-------"));
            IOException locked = assertThrows(
                    IOException.class,
                    () -> worker(Duration.ofSeconds(5))
                            .run(request(ShipRun.Stage.DESIGN, "prompt")));
            assertTrue(locked.getMessage().contains("already running"));
            assertFalse(hasSession(lockedSession));
        }
    }

    @Test
    void timeoutFailsClosedWhenPiDoesNotAcknowledgeAbort() throws Exception {
        Files.writeString(fixture.resolve("mode"), "unacknowledged-abort\n");

        IOException failure = assertThrows(
                IOException.class,
                () -> worker(Duration.ofMillis(200))
                        .run(request(ShipRun.Stage.DISCOVERY, "prompt")));

        assertTrue(failure.getMessage().contains("not resumable"));
        assertEquals(
                "{\"id\":\"abort-1\",\"type\":\"abort\"}",
                Files.readString(fixture.resolve("abort")).trim());
        assertFalse(hasSession(sessionId(ShipRun.Stage.DISCOVERY)));
    }

    @Test
    void timeoutAcknowledgesAbortBeforePersistingTheStageSession() throws Exception {
        Files.writeString(fixture.resolve("mode"), "block\n");

        PiWorker.Result result = worker(Duration.ofMillis(300))
                .run(request(ShipRun.Stage.DISCOVERY, "prompt"));

        assertEquals(PiWorker.Outcome.TIMED_OUT, result.outcome());
        assertFalse(result.evidence().succeeded());
        assertEquals(
                "{\"id\":\"abort-1\",\"type\":\"abort\"}",
                Files.readString(fixture.resolve("abort")).trim());
        assertTrue(hasSession(sessionId(ShipRun.Stage.DISCOVERY)));
        assertTrue(Files.readString(sessionFile(sessionId(ShipRun.Stage.DISCOVERY)))
                .contains("\"stopReason\":\"aborted\""));
        assertProcessesGone();
    }

    @Test
    void timeoutDuringRetryDelayPublishesOnlyTheBoundErrorTurn()
            throws Exception {
        Files.writeString(fixture.resolve("mode"), "retry-delay-block\n");

        PiWorker.Result result = worker(Duration.ofMillis(300))
                .run(request(ShipRun.Stage.DISCOVERY, "prompt"));

        assertEquals(PiWorker.Outcome.TIMED_OUT, result.outcome());
        assertEquals(
                List.of("{\"id\":\"abort-1\",\"type\":\"abort\"}"),
                Files.readAllLines(fixture.resolve("abort")));
        List<String> persisted = Files.readAllLines(
                sessionFile(sessionId(ShipRun.Stage.DISCOVERY)));
        assertEquals(5, persisted.size());
        assertTrue(persisted.get(4).contains("\"stopReason\":\"error\""));
        assertFalse(persisted.get(4).contains("\"stopReason\":\"aborted\""));
    }

    @Test
    void abortReconciliationUsesOneSharedDeadline() throws Exception {
        Files.writeString(fixture.resolve("mode"), "preflight-hang\n");
        long started = System.nanoTime();

        IOException failure = assertThrows(
                IOException.class,
                () -> worker(Duration.ofMillis(100))
                        .run(request(
                                ShipRun.Stage.DISCOVERY, "prompt")));
        Duration elapsed = Duration.ofNanos(
                System.nanoTime() - started);

        assertTrue(failure.getMessage().contains("did not acknowledge"));
        assertTrue(elapsed.compareTo(Duration.ofSeconds(4)) >= 0);
        assertTrue(elapsed.compareTo(Duration.ofSeconds(8)) < 0);
        assertFalse(Files.exists(fixture.resolve("abort")));
        assertFalse(hasSession(sessionId(ShipRun.Stage.DISCOVERY)));
    }

    @Test
    void timeoutPreservesANaturalStopThatWinsAfterAbort() throws Exception {
        Files.writeString(
                fixture.resolve("mode"), "natural-stop-after-abort\n");

        PiWorker.Result result = worker(Duration.ofMillis(300))
                .run(request(ShipRun.Stage.DISCOVERY, "prompt"));

        assertEquals(PiWorker.Outcome.SUCCEEDED, result.outcome());
        assertEquals("done", result.assistantText());
        assertFalse(result.evidence().timedOut());
        assertTrue(result.evidence().succeeded());
        assertEquals(
                List.of("{\"id\":\"abort-1\",\"type\":\"abort\"}"),
                Files.readAllLines(fixture.resolve("abort")));
        List<String> persisted = Files.readAllLines(
                sessionFile(sessionId(ShipRun.Stage.DISCOVERY)));
        assertEquals(6, persisted.size());
        assertTrue(persisted.get(4).contains("\"stopReason\":\"stop\""));
        assertTrue(persisted.get(5).contains("\"type\":\"compaction\""));
    }

    @Test
    void interruptionPublishesAProtocolVerifiableOutputLimitedAbort() throws Exception {
        Files.writeString(fixture.resolve("mode"), "interrupt-output-limit\n");
        PiWorker worker = worker(Duration.ofSeconds(30));
        PiWorker.Request request = request(ShipRun.Stage.EXECUTE, "prompt");
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<InterruptedException> interruption = new AtomicReference<>();
        AtomicBoolean interrupted = new AtomicBoolean();
        Thread invocation = new Thread(() -> {
            try {
                worker.run(request);
                failure.set(new AssertionError(
                        "Pi invocation unexpectedly completed"));
            } catch (InterruptedException expected) {
                interrupted.set(Thread.currentThread().isInterrupted());
                interruption.set(expected);
            } catch (Throwable unexpected) {
                failure.set(unexpected);
            }
        });

        invocation.start();
        awaitFile(fixture.resolve("ready"));
        invocation.interrupt();
        invocation.join(Duration.ofSeconds(15).toMillis());

        assertFalse(invocation.isAlive());
        assertNull(failure.get());
        assertTrue(interrupted.get());
        assertTrue(java.util.Arrays.stream(
                interruption.get().getSuppressed())
                .anyMatch(cause -> cause.getMessage().contains(
                        "retained-output limit")));
        String sessionId = sessionId(ShipRun.Stage.EXECUTE);
        assertTrue(hasSession(sessionId));
        assertTrue(Files.readString(sessionFile(sessionId))
                .contains("\"stopReason\":\"aborted\""));
        assertTrue(worker.recover(request).isEmpty());
        assertNoScratchDirectories();

        Files.writeString(fixture.resolve("mode"), "success\n");
        PiWorker.Result retried = worker.run(request);
        assertEquals(PiWorker.Outcome.SUCCEEDED, retried.outcome());
        assertEquals(retried, worker.recover(request).orElseThrow());
        assertEquals(
                List.of(sessionId),
                Files.readAllLines(fixture.resolve("resumed-sessions")));
    }

    @Test
    void interruptionDoesNotPublishAnOutputLimitedNaturalStop() throws Exception {
        Files.writeString(
                fixture.resolve("mode"),
                "interrupt-output-limit-natural-stop\n");
        PiWorker worker = worker(Duration.ofSeconds(30));
        PiWorker.Request request = request(ShipRun.Stage.EXECUTE, "prompt");
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<InterruptedException> interruption = new AtomicReference<>();
        AtomicBoolean interrupted = new AtomicBoolean();
        Thread invocation = new Thread(() -> {
            try {
                worker.run(request);
                failure.set(new AssertionError(
                        "Pi invocation unexpectedly completed"));
            } catch (InterruptedException expected) {
                interrupted.set(Thread.currentThread().isInterrupted());
                interruption.set(expected);
            } catch (Throwable unexpected) {
                failure.set(unexpected);
            }
        });

        invocation.start();
        awaitFile(fixture.resolve("ready"));
        invocation.interrupt();
        invocation.join(Duration.ofSeconds(15).toMillis());

        assertFalse(invocation.isAlive());
        assertNull(failure.get());
        assertTrue(interrupted.get());
        assertEquals(1, interruption.get().getSuppressed().length);
        assertEquals(
                "Interrupted Pi RPC exceeded its retained-output limit",
                interruption.get().getSuppressed()[0].getMessage());
        assertEquals(
                List.of("{\"id\":\"abort-1\",\"type\":\"abort\"}"),
                Files.readAllLines(fixture.resolve("abort")));
        assertFalse(hasSession(sessionId(ShipRun.Stage.EXECUTE)));
        assertTrue(worker.recover(request).isEmpty());
        assertNoScratchDirectories();

        Files.writeString(fixture.resolve("mode"), "success\n");
        PiWorker.Result retried = worker.run(request);
        assertEquals(PiWorker.Outcome.SUCCEEDED, retried.outcome());
        assertEquals(retried, worker.recover(request).orElseThrow());
        assertFalse(Files.exists(fixture.resolve("resumed-sessions")));
    }

    @Test
    void interruptionDoesNotPublishAProtocolInvalidAbort() throws Exception {
        Files.writeString(
                fixture.resolve("mode"),
                "duplicate-response-abort-after-blocked-prompt\n");
        PiWorker worker = worker(Duration.ofSeconds(30));
        PiWorker.Request request = request(
                ShipRun.Stage.VALIDATE, "x".repeat(1024 * 1024));
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<InterruptedException> interruption = new AtomicReference<>();
        AtomicBoolean interrupted = new AtomicBoolean();
        Thread invocation = new Thread(() -> {
            try {
                worker.run(request);
                failure.set(new AssertionError(
                        "Pi invocation unexpectedly completed"));
            } catch (InterruptedException expected) {
                interrupted.set(Thread.currentThread().isInterrupted());
                interruption.set(expected);
            } catch (Throwable unexpected) {
                failure.set(unexpected);
            }
        });

        invocation.start();
        awaitFile(fixture.resolve("ready"));
        awaitPiWorkerMethod(invocation, "awaitExit");
        invocation.interrupt();
        Files.writeString(fixture.resolve("release-prompt"), "release\n");
        invocation.join(Duration.ofSeconds(15).toMillis());

        assertFalse(invocation.isAlive());
        assertNull(failure.get());
        assertTrue(interrupted.get());
        assertEquals(0, interruption.get().getSuppressed().length);
        assertEquals(
                List.of("{\"id\":\"abort-1\",\"type\":\"abort\"}"),
                Files.readAllLines(fixture.resolve("abort")));
        assertFalse(hasSession(sessionId(ShipRun.Stage.VALIDATE)));
        assertTrue(worker.recover(request).isEmpty());
        assertNoScratchDirectories();

        Files.writeString(fixture.resolve("mode"), "success\n");
        PiWorker.Result retried = worker.run(request);
        assertEquals(PiWorker.Outcome.SUCCEEDED, retried.outcome());
        assertEquals(retried, worker.recover(request).orElseThrow());
        assertFalse(Files.exists(fixture.resolve("resumed-sessions")));
    }

    @Test
    void interruptionDoesNotPublishAnUnverifiableNaturalStop() throws Exception {
        assertInterruptedInvalidNaturalStopIsRetryable(
                "malformed-natural-stop-after-eof",
                ShipRun.Stage.DESIGN);
    }

    @Test
    void interruptionDoesNotPublishAProtocolInvalidNaturalStop() throws Exception {
        assertInterruptedInvalidNaturalStopIsRetryable(
                "duplicate-response-natural-stop-after-eof",
                ShipRun.Stage.PLAN);
    }

    @Test
    void interruptionWaitsForPreflightCompactionAndPromptResponse()
            throws Exception {
        PiWorker worker = worker(Duration.ofSeconds(30));
        worker.run(request(ShipRun.Stage.PLAN, "baseline"));
        Files.writeString(
                fixture.resolve("mode"), "preflight-compaction-block\n");
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicBoolean interrupted = new AtomicBoolean();
        Thread invocation = new Thread(() -> {
            try {
                worker.run(new PiWorker.Request(
                        RUN_ID,
                        ShipRun.Stage.PLAN,
                        2,
                        workingDirectory,
                        sessions,
                        evidence,
                        inputDigest(),
                        true,
                        "current"));
                failure.set(new AssertionError(
                        "Pi invocation unexpectedly completed"));
            } catch (InterruptedException expected) {
                interrupted.set(Thread.currentThread().isInterrupted());
                if (expected.getSuppressed().length > 0) {
                    failure.set(expected.getSuppressed()[0]);
                }
            } catch (Throwable unexpected) {
                failure.set(unexpected);
            }
        });

        invocation.start();
        awaitFile(fixture.resolve("compaction-ready"));
        invocation.interrupt();
        Thread.sleep(250);
        assertFalse(Files.exists(fixture.resolve("abort")));
        Files.writeString(
                fixture.resolve("release-compaction"), "release\n");
        invocation.join(Duration.ofSeconds(10).toMillis());

        assertFalse(invocation.isAlive());
        assertNull(failure.get());
        assertTrue(interrupted.get());
        assertEquals(
                List.of("{\"id\":\"abort-1\",\"type\":\"abort\"}"),
                Files.readAllLines(fixture.resolve("abort")));
        List<String> persisted = Files.readAllLines(
                sessionFile(sessionId(ShipRun.Stage.PLAN)));
        assertEquals(8, persisted.size());
        assertTrue(persisted.get(5).contains("\"type\":\"compaction\""));
        assertTrue(persisted.get(6).contains("\"content\":\"current\""));
        assertTrue(persisted.get(7).contains("\"stopReason\":\"aborted\""));
    }

    @Test
    void interruptionAbortsOnlyAfterOverflowCompactionCanContinue()
            throws Exception {
        Files.writeString(
                fixture.resolve("mode"), "overflow-compaction-abort\n");
        PiWorker worker = worker(Duration.ofSeconds(30));
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicBoolean interrupted = new AtomicBoolean();
        Thread invocation = new Thread(() -> {
            try {
                worker.run(request(ShipRun.Stage.VALIDATE, "prompt"));
                failure.set(new AssertionError(
                        "Pi invocation unexpectedly completed"));
            } catch (InterruptedException expected) {
                interrupted.set(Thread.currentThread().isInterrupted());
                if (expected.getSuppressed().length > 0) {
                    failure.set(expected.getSuppressed()[0]);
                }
            } catch (Throwable unexpected) {
                failure.set(unexpected);
            }
        });

        invocation.start();
        awaitFile(fixture.resolve("compaction-ready"));
        invocation.interrupt();
        Thread.sleep(250);
        assertFalse(Files.exists(fixture.resolve("abort-received")));
        Files.writeString(
                fixture.resolve("release-compaction"), "release\n");
        awaitFile(fixture.resolve("compaction-ended"));
        invocation.join(Duration.ofSeconds(10).toMillis());

        assertFalse(invocation.isAlive());
        assertNull(failure.get());
        assertTrue(interrupted.get());
        assertFalse(Files.exists(fixture.resolve("premature-abort")));
        assertEquals(
                "{\"id\":\"abort-1\",\"type\":\"abort\"}",
                Files.readString(fixture.resolve("abort")).trim());
        List<String> persisted = Files.readAllLines(
                sessionFile(sessionId(ShipRun.Stage.VALIDATE)));
        assertEquals(7, persisted.size());
        assertTrue(persisted.get(4).contains("\"stopReason\":\"error\""));
        assertTrue(persisted.get(5).contains("\"type\":\"compaction\""));
        assertTrue(persisted.get(6).contains("\"stopReason\":\"aborted\""));
    }

    @Test
    void partialMultiToolAbortPublishesAndResumesWithoutRepeatingWork()
            throws Exception {
        Files.writeString(fixture.resolve("mode"), "partial-tool-abort\n");
        PiWorker worker = worker(Duration.ofSeconds(30));
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicBoolean interrupted = new AtomicBoolean();
        Thread invocation = new Thread(() -> {
            try {
                worker.run(request(ShipRun.Stage.EXECUTE, "prompt"));
                failure.set(new AssertionError(
                        "Pi invocation unexpectedly completed"));
            } catch (InterruptedException expected) {
                interrupted.set(Thread.currentThread().isInterrupted());
                if (expected.getSuppressed().length > 0) {
                    failure.set(expected.getSuppressed()[0]);
                }
            } catch (Throwable unexpected) {
                failure.set(unexpected);
            }
        });

        invocation.start();
        awaitFile(fixture.resolve("ready"));
        invocation.interrupt();
        invocation.join(Duration.ofSeconds(10).toMillis());

        assertFalse(invocation.isAlive());
        assertNull(failure.get());
        assertTrue(interrupted.get());
        assertEquals(
                List.of("call-a"),
                Files.readAllLines(fixture.resolve("tool-executions")));
        List<String> interruptedSession = Files.readAllLines(
                sessionFile(sessionId(ShipRun.Stage.EXECUTE)));
        assertEquals(7, interruptedSession.size());
        assertTrue(interruptedSession.get(4).contains("\"call-b\""));
        assertTrue(interruptedSession.get(5).contains("\"call-a\""));
        assertTrue(interruptedSession.get(6).contains(
                "\"stopReason\":\"aborted\""));

        Files.writeString(fixture.resolve("mode"), "success\n");
        PiWorker.Result resumed = worker.run(new PiWorker.Request(
                RUN_ID,
                ShipRun.Stage.EXECUTE,
                2,
                workingDirectory,
                sessions,
                evidence,
                inputDigest(),
                true,
                "resume"));

        assertEquals(PiWorker.Outcome.SUCCEEDED, resumed.outcome());
        assertEquals(
                List.of("call-a"),
                Files.readAllLines(fixture.resolve("tool-executions")));
        assertEquals(
                9,
                Files.readAllLines(
                        sessionFile(sessionId(ShipRun.Stage.EXECUTE)))
                        .size());
    }

    @Test
    void overflowContinuationToolMessagesRemainAbortableBeforeAgentEnd()
            throws Exception {
        Files.writeString(
                fixture.resolve("mode"),
                "overflow-continuation-tool-abort\n");
        PiWorker worker = worker(Duration.ofSeconds(30));
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicBoolean interrupted = new AtomicBoolean();
        Thread invocation = new Thread(() -> {
            try {
                worker.run(request(ShipRun.Stage.EXECUTE, "prompt"));
                failure.set(new AssertionError(
                        "Pi invocation unexpectedly completed"));
            } catch (InterruptedException expected) {
                interrupted.set(Thread.currentThread().isInterrupted());
                if (expected.getSuppressed().length > 0) {
                    failure.set(expected.getSuppressed()[0]);
                }
            } catch (Throwable unexpected) {
                failure.set(unexpected);
            }
        });

        invocation.start();
        awaitFile(fixture.resolve("ready"));
        invocation.interrupt();
        invocation.join(Duration.ofSeconds(10).toMillis());

        assertFalse(invocation.isAlive());
        assertNull(failure.get());
        assertTrue(interrupted.get());
        assertEquals(
                "{\"id\":\"abort-1\",\"type\":\"abort\"}",
                Files.readString(fixture.resolve("abort")).trim());
        assertEquals(
                List.of("call-a"),
                Files.readAllLines(fixture.resolve("tool-executions")));
        List<String> interruptedSession = Files.readAllLines(
                sessionFile(sessionId(ShipRun.Stage.EXECUTE)));
        assertEquals(9, interruptedSession.size());
        assertTrue(interruptedSession.get(4).contains(
                "\"stopReason\":\"error\""));
        assertTrue(interruptedSession.get(5).contains(
                "\"type\":\"compaction\""));
        assertTrue(interruptedSession.get(6).contains("\"call-b\""));
        assertTrue(interruptedSession.get(7).contains("\"call-a\""));
        assertTrue(interruptedSession.get(8).contains(
                "\"stopReason\":\"aborted\""));

        Files.writeString(fixture.resolve("mode"), "success\n");
        PiWorker.Result resumed = worker.run(new PiWorker.Request(
                RUN_ID,
                ShipRun.Stage.EXECUTE,
                2,
                workingDirectory,
                sessions,
                evidence,
                inputDigest(),
                true,
                "resume"));

        assertEquals(PiWorker.Outcome.SUCCEEDED, resumed.outcome());
        assertEquals(
                List.of("call-a"),
                Files.readAllLines(fixture.resolve("tool-executions")));
        assertEquals(
                11,
                Files.readAllLines(
                        sessionFile(sessionId(ShipRun.Stage.EXECUTE)))
                        .size());
    }

    @Test
    void interruptionPersistsAbortAndResumesTheSameStageSession() throws Exception {
        Files.writeString(fixture.resolve("mode"), "block\n");
        PiWorker worker = worker(Duration.ofSeconds(30));
        ShipController controller = new ShipController(temporaryDirectory.resolve("state"));
        ShipRun run = controller.start(workingDirectory, ShipRun.Oversight.NEVER, List.of());
        String sessionId = sessionId(
                run.id(),
                ShipRun.Stage.DISCOVERY,
                run.stage(ShipRun.Stage.DISCOVERY).inputDigest());
        PiWorker.Request request = new PiWorker.Request(
                run.id(),
                ShipRun.Stage.DISCOVERY,
                1,
                workingDirectory,
                sessions,
                evidence,
                run.stage(ShipRun.Stage.DISCOVERY).inputDigest(),
                true,
                "prompt");
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicBoolean interrupted = new AtomicBoolean();
        Thread invocation = new Thread(() -> {
            try {
                worker.run(request);
                failure.set(new AssertionError("Pi invocation unexpectedly completed"));
            } catch (InterruptedException expected) {
                interrupted.set(Thread.currentThread().isInterrupted());
                if (expected.getSuppressed().length > 0) {
                    failure.set(expected.getSuppressed()[0]);
                }
            } catch (Throwable unexpected) {
                failure.set(unexpected);
            }
        });

        invocation.start();
        awaitFile(fixture.resolve("ready"));
        assertFalse(hasSession(sessionId));
        invocation.interrupt();
        invocation.join(Duration.ofSeconds(10).toMillis());

        assertFalse(invocation.isAlive());
        assertNull(failure.get());
        assertTrue(interrupted.get());
        assertEquals(
                "{\"id\":\"abort-1\",\"type\":\"abort\"}",
                Files.readString(fixture.resolve("abort")).trim());
        assertTrue(hasSession(sessionId));
        assertTrue(Files.readString(sessionFile(sessionId))
                .contains("\"stopReason\":\"aborted\""));
        assertProcessesGone();

        ShipRun resumed = controller.resume(run.id());
        Files.writeString(fixture.resolve("mode"), "success\n");
        PiWorker.Result resumedResult = worker.run(new PiWorker.Request(
                resumed.id(),
                ShipRun.Stage.DISCOVERY,
                2,
                workingDirectory,
                sessions,
                evidence,
                resumed.stage(ShipRun.Stage.DISCOVERY).inputDigest(),
                true,
                "resume"));

        assertEquals(PiWorker.Outcome.SUCCEEDED, resumedResult.outcome());
        assertEquals(2, resumed.stage(ShipRun.Stage.DISCOVERY).attempts());
        assertEquals(
                List.of(sessionId, sessionId),
                Files.readAllLines(fixture.resolve("session-ids")));
        assertEquals(
                List.of(sessionId),
                Files.readAllLines(fixture.resolve("resumed-sessions")));
        List<String> persisted = Files.readAllLines(sessionFile(sessionId));
        assertEquals(7, persisted.size());
        assertTrue(persisted.get(4).contains("\"stopReason\":\"aborted\""));
        assertTrue(persisted.get(6).contains("\"stopReason\":\"stop\""));
    }

    @Test
    void completedAttemptReturnsItsDurableResultWithoutRunningPiAgain() throws Exception {
        PiWorker worker = worker(Duration.ofSeconds(30));
        PiWorker.Request request = request(ShipRun.Stage.DISCOVERY, "prompt");

        PiWorker.Result first = worker.run(request);
        Files.writeString(fixture.resolve("mode"), "fail-before-session\n");
        PiWorker.Result recovered = worker.recover(request).orElseThrow();

        assertEquals(first, recovered);
        assertEquals(
                1,
                Files.readAllLines(fixture.resolve("session-ids")).size());
    }

    @Test
    void recoveryPreservesHistoricalExecutableAndRejectsDeletedOrTruncatedEvidence()
            throws Exception {
        PiWorker worker = worker(Duration.ofSeconds(5));
        PiWorker.Request request = request(
                ShipRun.Stage.DISCOVERY, "prompt");
        PiWorker.Result result = worker.run(request);
        Path stdout = Path.of(result.evidence().stdoutLog());
        Path stderr = Path.of(result.evidence().stderrLog());
        byte[] stdoutBytes = Files.readAllBytes(stdout);

        Path otherExecutable = writeExecutable(
                fixture.resolve("pi-other"),
                Files.readString(executable));
        assertEquals(
                result,
                new PiWorker(
                        otherExecutable,
                        List.of("0.83.0"),
                        nodeExecutable,
                        "22.22.2",
                        Duration.ofSeconds(5))
                        .recover(request)
                        .orElseThrow());
        assertEquals(result, worker.recover(request).orElseThrow());

        Files.writeString(stdout, "x");
        PiWorker.UntrustedResultException truncated = assertThrows(
                PiWorker.UntrustedResultException.class,
                () -> worker.recover(request));
        assertTrue(truncated.getMessage().contains("recorded size"));

        Files.write(stdout, stdoutBytes);
        Files.delete(stderr);
        PiWorker.UntrustedResultException deleted = assertThrows(
                PiWorker.UntrustedResultException.class,
                () -> worker.recover(request));
        assertTrue(deleted.getMessage().contains("missing or invalid"));
    }

    @Test
    void failedSessionPublicationRemovesTheUnpublishedResult() throws Exception {
        Files.writeString(fixture.resolve("mode"), "publication-failure\n");
        PiWorker worker = worker(Duration.ofSeconds(5));
        PiWorker.Request request = request(ShipRun.Stage.DISCOVERY, "prompt");

        assertThrows(IOException.class, () -> worker.run(request));

        assertTrue(worker.recover(request).isEmpty());
        Path publicationTarget = Path.of(
                Files.readString(fixture.resolve("publication-target")).trim());
        Files.delete(publicationTarget.resolve("block"));
        Files.delete(publicationTarget);
        Files.writeString(fixture.resolve("mode"), "success\n");
        PiWorker.Result retried = worker.run(request);
        assertEquals(PiWorker.Outcome.SUCCEEDED, retried.outcome());
        assertEquals(retried, worker.recover(request).orElseThrow());
    }

    @Test
    void repeatedInterruptsReuseOneAbortCommandAndDeadline() throws Exception {
        Files.writeString(fixture.resolve("mode"), "repeated-interrupt-abort\n");
        PiWorker worker = worker(Duration.ofSeconds(30));
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicBoolean interrupted = new AtomicBoolean();
        Thread invocation = new Thread(() -> {
            try {
                worker.run(request(ShipRun.Stage.DESIGN, "prompt"));
                failure.set(new AssertionError("Pi invocation unexpectedly completed"));
            } catch (InterruptedException expected) {
                interrupted.set(Thread.currentThread().isInterrupted());
                if (expected.getSuppressed().length > 0) {
                    failure.set(expected.getSuppressed()[0]);
                }
            } catch (Throwable unexpected) {
                failure.set(unexpected);
            }
        });

        invocation.start();
        awaitFile(fixture.resolve("ready"));
        invocation.interrupt();
        awaitFile(fixture.resolve("abort-received"));
        invocation.interrupt();
        invocation.interrupt();
        Files.writeString(fixture.resolve("release"), "release\n");
        invocation.join(Duration.ofSeconds(10).toMillis());

        assertFalse(invocation.isAlive());
        assertNull(failure.get());
        assertTrue(interrupted.get());
        assertEquals(
                List.of("{\"id\":\"abort-1\",\"type\":\"abort\"}"),
                Files.readAllLines(fixture.resolve("abort")));
        assertEquals(5, Files.readAllLines(
                sessionFile(sessionId(ShipRun.Stage.DESIGN))).size());
    }

    @Test
    void lateInterruptAfterTerminalResultDoesNotAbortOrRetryTheTurn() throws Exception {
        Files.writeString(fixture.resolve("mode"), "late-terminal-exit\n");
        PiWorker worker = worker(Duration.ofSeconds(30));
        AtomicReference<PiWorker.Result> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicBoolean interrupted = new AtomicBoolean();
        Thread invocation = new Thread(() -> {
            try {
                result.set(worker.run(request(ShipRun.Stage.PLAN, "prompt")));
                interrupted.set(Thread.currentThread().isInterrupted());
            } catch (Throwable unexpected) {
                failure.set(unexpected);
            }
        });

        invocation.start();
        awaitFile(fixture.resolve("ready"));
        invocation.interrupt();
        Files.writeString(fixture.resolve("release"), "release\n");
        invocation.join(Duration.ofSeconds(10).toMillis());

        assertFalse(invocation.isAlive());
        assertNull(failure.get());
        assertEquals(PiWorker.Outcome.SUCCEEDED, result.get().outcome());
        assertTrue(interrupted.get());
        assertFalse(Files.exists(fixture.resolve("abort")));
        assertEquals(
                List.of(sessionId(ShipRun.Stage.PLAN)),
                Files.readAllLines(fixture.resolve("session-ids")));
        assertEquals(5, Files.readAllLines(
                sessionFile(sessionId(ShipRun.Stage.PLAN))).size());
    }

    @Test
    void lateInterruptAfterAbortAcknowledgementKeepsThePersistedAbort() throws Exception {
        Files.writeString(fixture.resolve("mode"), "late-abort-exit\n");
        PiWorker worker = worker(Duration.ofMillis(300));
        AtomicReference<PiWorker.Result> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicBoolean interrupted = new AtomicBoolean();
        Thread invocation = new Thread(() -> {
            try {
                result.set(worker.run(request(ShipRun.Stage.VALIDATE, "prompt")));
                interrupted.set(Thread.currentThread().isInterrupted());
            } catch (Throwable unexpected) {
                failure.set(unexpected);
            }
        });

        invocation.start();
        awaitFile(fixture.resolve("ready"));
        invocation.interrupt();
        Files.writeString(fixture.resolve("release"), "release\n");
        invocation.join(Duration.ofSeconds(10).toMillis());

        assertFalse(invocation.isAlive());
        assertNull(failure.get());
        assertEquals(PiWorker.Outcome.TIMED_OUT, result.get().outcome());
        assertTrue(interrupted.get());
        assertEquals(
                List.of(sessionId(ShipRun.Stage.VALIDATE)),
                Files.readAllLines(fixture.resolve("session-ids")));
        List<String> persisted = Files.readAllLines(
                sessionFile(sessionId(ShipRun.Stage.VALIDATE)));
        assertEquals(5, persisted.size());
        assertTrue(persisted.get(4).contains("\"stopReason\":\"aborted\""));
    }

    @Test
    void interruptionReapsPiBeforeClosingABlockedPromptWriter() throws Exception {
        Files.writeString(fixture.resolve("mode"), "never-read\n");
        PiWorker worker = worker(Duration.ofSeconds(30));
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicBoolean interrupted = new AtomicBoolean();
        Thread invocation = new Thread(() -> {
            try {
                worker.run(request(
                        ShipRun.Stage.DISCOVERY, "x".repeat(1024 * 1024)));
                failure.set(new AssertionError("Pi invocation unexpectedly completed"));
            } catch (InterruptedException expected) {
                interrupted.set(Thread.currentThread().isInterrupted());
            } catch (Throwable unexpected) {
                failure.set(unexpected);
            }
        });

        invocation.start();
        awaitFile(fixture.resolve("ready"));
        invocation.interrupt();
        invocation.join(Duration.ofSeconds(10).toMillis());

        assertFalse(invocation.isAlive());
        assertNull(failure.get());
        assertTrue(interrupted.get());
        assertFalse(hasSession(sessionId(ShipRun.Stage.DISCOVERY)));
        assertProcessesGone();
    }

    @Test
    void reapsFastDetachedProcessGroupMembersBeforeReturning()
            throws Exception {
        Files.writeString(fixture.resolve("mode"), "fast-detach\n");

        PiWorker.Result result = worker(Duration.ofSeconds(5))
                .run(request(ShipRun.Stage.EXECUTE, "prompt"));

        assertEquals(PiWorker.Outcome.SUCCEEDED, result.outcome());
        long detached = Long.parseLong(
                Files.readString(fixture.resolve("detached-pid")).trim());
        awaitGone(detached);
    }

    private PiWorker worker(Duration timeout) {
        return new PiWorker(
                executable,
                List.of("0.83.0"),
                nodeExecutable,
                "22.22.2",
                timeout);
    }

    private PiWorker worker(
            Duration timeout, Map<String, String> environment) {
        return new PiWorker(
                executable,
                List.of("0.83.0"),
                nodeExecutable,
                "22.22.2",
                timeout,
                Clock.systemUTC(),
                environment);
    }

    private PiWorker.Request request(ShipRun.Stage stage, String prompt) {
        return request(stage, prompt, true);
    }

    private PiWorker.Request request(
            ShipRun.Stage stage, String prompt, boolean acceptExperimental) {
        return new PiWorker.Request(
                RUN_ID,
                stage,
                1,
                workingDirectory,
                sessions,
                evidence,
                inputDigest(),
                acceptExperimental,
                prompt);
    }

    private void assertInterruptedInvalidNaturalStopIsRetryable(
            String mode, ShipRun.Stage stage)
            throws Exception {
        Files.writeString(fixture.resolve("mode"), mode + "\n");
        PiWorker worker = worker(Duration.ofSeconds(30));
        PiWorker.Request request = request(stage, "prompt");
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicBoolean interrupted = new AtomicBoolean();
        Thread invocation = new Thread(() -> {
            try {
                worker.run(request);
                failure.set(new AssertionError(
                        "Pi invocation unexpectedly completed"));
            } catch (InterruptedException expected) {
                interrupted.set(Thread.currentThread().isInterrupted());
                if (expected.getSuppressed().length > 0) {
                    failure.set(expected.getSuppressed()[0]);
                }
            } catch (Throwable unexpected) {
                failure.set(unexpected);
            }
        });

        invocation.start();
        awaitFile(fixture.resolve("ready"));
        invocation.interrupt();
        Thread.sleep(250);
        Files.writeString(fixture.resolve("release"), "release\n");
        invocation.join(Duration.ofSeconds(10).toMillis());

        assertFalse(invocation.isAlive());
        assertNull(failure.get());
        assertTrue(interrupted.get());
        assertFalse(hasSession(sessionId(stage)));
        assertTrue(worker.recover(request).isEmpty());
        assertEquals(
                "",
                Files.readString(fixture.resolve("post-prompt-input")));

        Files.writeString(fixture.resolve("mode"), "success\n");
        PiWorker.Result retried = worker.run(request);
        assertEquals(PiWorker.Outcome.SUCCEEDED, retried.outcome());
        assertEquals(retried, worker.recover(request).orElseThrow());
    }

    private static String inputDigest() {
        return ShipDigest.sha256("input".getBytes(StandardCharsets.UTF_8));
    }

    private static String sessionId(ShipRun.Stage stage) {
        return sessionId(RUN_ID, stage, inputDigest());
    }

    private static String sessionId(
            String runId, ShipRun.Stage stage, String inputDigest) {
        return runId + "-" + stage.name().toLowerCase(java.util.Locale.ROOT) + "-"
               + inputDigest.substring("sha256:".length());
    }

    private Path assertScratchArgument(
            String sessionId, List<String> arguments) {
        assertEquals("--session-dir", arguments.get(4));
        Path scratch = Path.of(arguments.get(5));
        assertEquals(sessions, scratch.getParent());
        assertTrue(scratch.getFileName().toString()
                .startsWith(".ship-pi-" + sessionId + "-"));
        assertFalse(Files.exists(scratch));
        return scratch;
    }

    private void assertNoScratchDirectories() throws IOException {
        try (var entries = Files.newDirectoryStream(
                sessions, ".ship-pi-*")) {
            assertFalse(entries.iterator().hasNext());
        }
    }

    private void assertEvidenceDoesNotContain(String secret)
            throws IOException {
        try (var paths = Files.walk(evidence)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                assertFalse(
                        Files.readString(path).contains(secret),
                        path.toString());
            }
        }
    }

    private boolean hasSession(String sessionId) throws IOException {
        try (var files = Files.newDirectoryStream(
                sessions, "*_" + sessionId + ".jsonl")) {
            return files.iterator().hasNext();
        }
    }

    private Path sessionFile(String sessionId) throws IOException {
        try (var files = Files.newDirectoryStream(
                sessions, "*_" + sessionId + ".jsonl")) {
            return files.iterator().next();
        }
    }

    private void assertProcessesGone() throws Exception {
        long parent = Long.parseLong(Files.readString(fixture.resolve("parent-pid")).trim());
        long child = Long.parseLong(Files.readString(fixture.resolve("child-pid")).trim());
        awaitGone(parent);
        awaitGone(child);
        assertFalse(ProcessHandle.of(parent).map(ProcessHandle::isAlive).orElse(false));
        assertFalse(ProcessHandle.of(child).map(ProcessHandle::isAlive).orElse(false));
    }

    private static void awaitFile(Path path) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (!Files.exists(path)) {
            if (System.nanoTime() >= deadline) {
                throw new AssertionError("Timed out waiting for " + path);
            }
            Thread.sleep(10);
        }
    }

    private static void awaitPiWorkerMethod(Thread thread, String method)
            throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (java.util.Arrays.stream(thread.getStackTrace())
                .noneMatch(frame -> frame.getClassName().equals(PiWorker.class.getName())
                        && frame.getMethodName().equals(method))) {
            if (!thread.isAlive()) {
                throw new AssertionError("Pi invocation exited before reaching " + method);
            }
            if (System.nanoTime() >= deadline) {
                throw new AssertionError("Timed out waiting for PiWorker." + method);
            }
            Thread.sleep(10);
        }
    }

    private static void awaitGone(long pid) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)) {
            if (System.nanoTime() >= deadline) {
                throw new AssertionError("Process is still alive: " + pid);
            }
            Thread.sleep(10);
        }
    }

    private static Path writeExecutable(Path path, String script) throws IOException {
        Files.writeString(path, script);
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwx------"));
        return path;
    }
}
