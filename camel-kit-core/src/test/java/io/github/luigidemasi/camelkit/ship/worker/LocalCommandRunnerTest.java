package io.github.luigidemasi.camelkit.ship.worker;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.github.luigidemasi.camelkit.ship.ShipDigest;
import io.github.luigidemasi.camelkit.ship.evidence.ShipLocalStamp;
import io.github.luigidemasi.camelkit.ship.worker.LocalCommandRunner.Command;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledOnOs(OS.LINUX)
class LocalCommandRunnerTest {

    @TempDir
    Path temporaryDirectory;

    private Path executable;
    private Path fixture;
    private Path workingDirectory;
    private Path evidenceDirectory;

    @BeforeEach
    void prepareFixture() throws Exception {
        fixture = Files.createDirectory(temporaryDirectory.resolve("fixture"));
        executable = writeExecutable(fixture.resolve("command"),
                """
                        #!/bin/sh
                        fixture=$(dirname "$0")
                        mode="$1"
                        shift
                        if [ "$mode" = "echo" ]; then
                          cat > "$fixture/input"
                          printf '%s\\n' '{"type":"message_end","message":{"role":"user","content":[{"type":"text","text":"fixture-secret"}]}}'
                          printf '%s\\n' '{"token":"fixture-secret"}'
                          printf '%s\\n' 'Authorization=Bearer fixture-secret' >&2
                          printf '%s\\n' 'Authorization: Basic dXNlcjpwYXNz' >&2
                          printf '%s\\n' 'Authorization: "Basic quoted-value"' >&2
                          printf '%s\\n' 'AWS_ACCESS_KEY_ID=log-access-key' >&2
                          printf '%s\\n' "SERVICE_PRIVATE_KEY='log-private-key'" >&2
                          printf '%s\\n' 'SERVICE_JWT=log-jwt' >&2
                          printf '%s\\n' 'SERVICE_PAT=log-pat' >&2
                          exit 0
                        fi
                        if [ "$mode" = "embedded-secrets" ]; then
                          printf '%s\\n' 'supersecret' 'password-fragment' 'db-secret' \
                            'quoted-token' 'json-secret,tail' 'url-token' 'empty-user-secret'
                          exit 0
                        fi
                        if [ "$mode" = "credential-carriers" ]; then
                          printf '%s\\n' 'proxy-secret' 'oauth-secret' \
                            'cookie-secret' 'compact-cookie-secret' 'carrier-auth-secret'
                          exit 0
                        fi
                        if [ "$mode" = "known-secret" ]; then
                          printf '%s\\n' "$@"
                          printf '%s\\n' "$@" >&2
                          exit 0
                        fi
                        if [ "$mode" = "nonzero" ]; then
                          printf '%s\\n' 'failed'
                          exit 7
                        fi
                        if [ "$mode" = "oversized" ]; then
                          dd if=/dev/zero bs=4096 count=2 2>/dev/null
                          exit 0
                        fi
                        if [ "$mode" = "oversized-secret" ]; then
                          printf 'captured-secret-prefix'
                          dd if=/dev/zero bs=4096 count=2 2>/dev/null
                          exit 0
                        fi
                        if [ "$mode" = "delayed-exit" ]; then
                          sleep 0.05
                          exit 0
                        fi
                        if [ "$mode" = "fast-detach" ]; then
                          (
                            trap '' TERM HUP INT
                            sleep 1
                            touch delayed-mutation
                          ) &
                          printf '%s\\n' "$!" > "$fixture/detached-pid"
                          exit 0
                        fi
                        if [ "$mode" = "output-flood" ]; then
                          sh -c 'trap "" TERM HUP INT; while :; do printf "%04096d" 0; done' &
                          child=$!
                          printf '%s\\n' "$$" > "$fixture/parent-pid"
                          printf '%s\\n' "$child" > "$fixture/child-pid"
                          touch "$fixture/ready"
                          trap 'kill "$child" 2>/dev/null; wait "$child" 2>/dev/null; exit 143' TERM HUP INT
                          wait "$child"
                        fi
                        if [ "$mode" != "no-read" ]; then
                          cat > /dev/null
                        fi
                        sh -c 'trap "" TERM HUP INT; while :; do sleep 300; done' &
                        child=$!
                        printf '%s\\n' "$$" > "$fixture/parent-pid"
                        printf '%s\\n' "$child" > "$fixture/child-pid"
                        touch "$fixture/ready"
                        trap 'kill "$child" 2>/dev/null; wait "$child" 2>/dev/null; exit 143' TERM HUP INT
                        wait "$child"
                        """);
        workingDirectory = Files.createDirectory(temporaryDirectory.resolve("workspace"));
        evidenceDirectory = Files.createDirectory(temporaryDirectory.resolve("evidence"));
    }

    @Test
    void closesInputAndRetainsOnlyRedactedBoundedLogs() throws Exception {
        List<String> arguments = new ArrayList<>(
                List.of("echo", "--token", "fixture-secret"));
        Command command = new Command(
                executable,
                arguments,
                workingDirectory,
                evidenceDirectory,
                Duration.ofSeconds(5),
                4096,
                List.of("fixture-secret"));
        arguments.clear();

        LocalCommandRunner.Result result = new LocalCommandRunner().run(command);

        assertEquals(0, result.exitCode());
        assertEquals(0, Files.size(fixture.resolve("input")));
        String retainedStdout = Files.readString(result.stdoutLog());
        assertFalse(retainedStdout.contains("fixture-secret"));
        assertTrue(retainedStdout.contains("\"text\":\"<redacted>\""));
        assertTrue(retainedStdout.contains("{\"token\":\"<redacted>\"}"));
        String retainedStderr = Files.readString(result.stderrLog());
        assertTrue(retainedStderr.contains("Authorization=<redacted>"));
        assertTrue(retainedStderr.contains("Authorization: <redacted>"));
        assertTrue(retainedStderr.contains("Authorization: \"<redacted>\""));
        assertTrue(retainedStderr.contains("AWS_ACCESS_KEY_ID=<redacted>"));
        assertTrue(retainedStderr.contains("SERVICE_PRIVATE_KEY='<redacted>'"));
        assertTrue(retainedStderr.contains("SERVICE_JWT=<redacted>"));
        assertTrue(retainedStderr.contains("SERVICE_PAT=<redacted>"));
        assertFalse(retainedStderr.contains("dXNlcjpwYXNz"));
        assertFalse(retainedStderr.contains("quoted-value"));
        assertFalse(retainedStderr.contains("log-access-key"));
        assertFalse(retainedStderr.contains("log-private-key"));
        assertFalse(retainedStderr.contains("log-jwt"));
        assertFalse(retainedStderr.contains("log-pat"));
        assertEquals(
                List.of("echo", "--token", "<redacted>"),
                result.redactedArguments());
        assertEquals(
                ShipDigest.sha256(Files.readAllBytes(result.stdoutLog())),
                result.stdoutDigest());
        assertEquals(
                ShipDigest.sha256(Files.readAllBytes(result.stderrLog())),
                result.stderrDigest());
        assertFalse(command.toString().contains("fixture-secret"));
        assertFalse(result.toString().contains("fixture-secret"));
    }

    @Test
    void redactsCallerKnownSecretsWithoutGuessingOverloadedFlags() throws Exception {
        String secret = "hunter2";
        Command command = new Command(
                executable,
                List.of("known-secret", "-p", secret),
                workingDirectory,
                evidenceDirectory,
                Duration.ofSeconds(5),
                4096,
                List.of(secret));

        LocalCommandRunner.Result result = new LocalCommandRunner().run(command);
        ShipLocalStamp.CommandRun evidence = stampCommand(result);

        assertEquals(
                List.of("known-secret", "-p", "<redacted>"),
                result.redactedArguments());
        assertFalse(Files.readString(result.stdoutLog()).contains(secret));
        assertFalse(Files.readString(result.stderrLog()).contains(secret));
        assertFalse(result.toString().contains(secret));
        assertFalse(evidence.toString().contains(secret));
    }

    @Test
    void filtersOnlyBooleanFeatureToggleEnvironmentValues() {
        assertFalse(LocalCommandRunner.isSensitiveEnvironmentValue(
                "AUTH_ENABLED", "true"));
        assertFalse(LocalCommandRunner.isSensitiveEnvironmentValue(
                "USE_JWT", "1"));
        assertFalse(LocalCommandRunner.isSensitiveEnvironmentValue(
                "ENABLE_COOKIE", "false"));
        assertTrue(LocalCommandRunner.isSensitiveEnvironmentValue("PASS", "xy"));
        assertTrue(LocalCommandRunner.isSensitiveEnvironmentValue("TOKEN", "1"));
    }

    @Test
    void reapsFastDetachedProcessGroupBeforeReturning() throws Exception {
        LocalCommandRunner.Result result = new LocalCommandRunner().run(command(
                List.of("fast-detach"), Duration.ofSeconds(5), 4096));

        long detached = Long.parseLong(
                Files.readString(fixture.resolve("detached-pid")).trim());
        assertEquals(0, result.exitCode());
        assertFalse(ProcessHandle.of(detached).map(ProcessHandle::isAlive).orElse(false));
    }

    @Test
    void reportsNonzeroAndBoundsExcessiveOutput() throws Exception {
        LocalCommandRunner runner = new LocalCommandRunner();

        LocalCommandRunner.Result nonzero = runner.run(command(
                List.of("nonzero"), Duration.ofSeconds(5), 4096));
        LocalCommandRunner.Result oversized = runner.run(command(
                List.of("oversized"), Duration.ofSeconds(5), 1024));

        assertEquals(7, nonzero.exitCode());
        assertFalse(nonzero.timedOut());
        assertTrue(oversized.outputLimited());
        assertTrue(Files.size(oversized.stdoutLog()) <= 1024);
        assertEquals(
                ShipDigest.sha256(Files.readAllBytes(oversized.stdoutLog())),
                oversized.stdoutDigest());
    }

    @Test
    void outputLimitRetainsOnlyTheLimitMarker() throws Exception {
        LocalCommandRunner.Result result = new LocalCommandRunner().run(command(
                List.of("oversized-secret"), Duration.ofSeconds(5), 128));

        assertTrue(result.outputLimited());
        assertEquals("<output-limit-exceeded>\n", Files.readString(result.stdoutLog()));
        assertEquals("<output-limit-exceeded>\n", Files.readString(result.stderrLog()));
        assertFalse(Files.readString(result.stdoutLog()).contains("captured-secret-prefix"));
    }

    @Test
    void outputFloodForciblyReapsProcessGroup() throws Exception {
        LocalCommandRunner.Result result = new LocalCommandRunner().run(command(
                List.of("output-flood"), Duration.ofSeconds(5), 1024));

        assertTrue(result.outputLimited());
        assertProcessesGone();
    }

    @Test
    void boundsShortSecretRedactionWithoutRescanningTheMarker() throws Exception {
        int maximumBytes = 1024;
        LocalCommandRunner.RetainedLog retained = LocalCommandRunner.retain(
                "a".repeat(16 * 1024 * 1024).getBytes(StandardCharsets.UTF_8),
                evidenceDirectory,
                ".stdout.log",
                maximumBytes,
                List.of("a", "redacted"));

        String content = Files.readString(retained.path());
        assertTrue(Files.size(retained.path()) <= maximumBytes);
        assertEquals(Files.size(retained.path()), retained.size());
        assertEquals(
                ShipLocalStamp.REDACTED.repeat(
                        maximumBytes / ShipLocalStamp.REDACTED.length()),
                content);
    }

    @Test
    void rejectsPreInterruptedInvocationWithoutLaunching() {
        Thread.currentThread().interrupt();
        try {
            assertThrows(
                    InterruptedException.class,
                    () -> new LocalCommandRunner().run(command(
                            List.of("echo"),
                            Duration.ofSeconds(5),
                            4096)));
            assertTrue(Thread.currentThread().isInterrupted());
            assertFalse(Files.exists(fixture.resolve("input")));
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void accountsTinyTimeoutBeforeTheProcessCanExit() throws Exception {
        LocalCommandRunner.Result result = new LocalCommandRunner().run(command(
                List.of("delayed-exit"),
                Duration.ofMillis(1),
                4096));

        assertTrue(result.timedOut());
        assertNull(result.exitCode());
    }

    @Test
    void rejectsEqualOrNestedWorkingAndEvidenceDirectories() throws Exception {
        Path parent = Files.createDirectory(temporaryDirectory.resolve("overlap"));
        Path child = Files.createDirectory(parent.resolve("child"));

        assertOverlapRejected(parent, parent);
        assertOverlapRejected(parent, child);
        assertOverlapRejected(child, parent);
        assertFalse(Files.exists(fixture.resolve("input")));
    }

    @Test
    void timeoutForciblyReapsTermIgnoringChild()
            throws Exception {
        LocalCommandRunner.Result result = new LocalCommandRunner().run(command(
                List.of("no-read"), Duration.ofMillis(300), 4096));

        assertTrue(result.timedOut());
        assertNull(result.exitCode());
        assertProcessesGone();
    }

    @Test
    void interruptionForciblyReapsTreeAndPreservesInterrupt() throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicBoolean interrupted = new AtomicBoolean();
        Thread invocation = new Thread(() -> {
            try {
                new LocalCommandRunner().run(command(
                        List.of("block"),
                        Duration.ofSeconds(30),
                        4096));
                failure.set(new AssertionError("Local command unexpectedly completed"));
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
        assertProcessesGone();
    }

    @Test
    void acceptsAncestorSymlinksButRejectsALeafDirectorySymlink() throws Exception {
        Path realParent = Files.createDirectory(temporaryDirectory.resolve("real-parent"));
        Path realWorking = Files.createDirectory(realParent.resolve("working"));
        Path parentAlias = temporaryDirectory.resolve("alias-parent");
        Files.createSymbolicLink(parentAlias, realParent.getFileName());

        LocalCommandRunner.Result result = new LocalCommandRunner().run(new Command(
                executable,
                List.of("echo"),
                parentAlias.resolve(realWorking.getFileName()),
                evidenceDirectory,
                Duration.ofSeconds(5),
                4096));

        assertEquals(realWorking, result.workingDirectory());
        Path leafAlias = temporaryDirectory.resolve("working-link");
        Files.createSymbolicLink(leafAlias, realWorking);
        IOException failure = assertThrows(
                IOException.class,
                () -> new LocalCommandRunner().run(new Command(
                        executable,
                        List.of("echo"),
                        leafAlias,
                        evidenceDirectory,
                        Duration.ofSeconds(5),
                        4096)));

        assertTrue(failure.getMessage().contains("real directory"));
    }

    private void assertOverlapRejected(Path working, Path evidence) {
        IOException failure = assertThrows(
                IOException.class,
                () -> new LocalCommandRunner().run(new Command(
                        executable,
                        List.of("echo"),
                        working,
                        evidence,
                        Duration.ofSeconds(5),
                        4096)));
        assertTrue(failure.getMessage().contains("disjoint"));
    }

    private Command command(
            List<String> arguments,
            Duration timeout,
            int maximumOutputBytes) {
        return new Command(
                executable,
                arguments,
                workingDirectory,
                evidenceDirectory,
                timeout,
                maximumOutputBytes);
    }

    private static ShipLocalStamp.CommandRun stampCommand(
            LocalCommandRunner.Result result) {
        return new ShipLocalStamp.CommandRun(
                result.executable().toString(),
                "1.0",
                result.redactedArguments(),
                result.workingDirectory().toString(),
                List.of(ShipDigest.sha256("input".getBytes(StandardCharsets.UTF_8))),
                true,
                result.timedOut(),
                result.outputLimited(),
                result.exitCode(),
                result.startedAt().toString(),
                result.endedAt().toString(),
                result.stdoutLog().toString(),
                result.stdoutDigest(),
                result.stderrLog().toString(),
                result.stderrDigest());
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
