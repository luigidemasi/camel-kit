package io.github.luigidemasi.camelkit.ship.worker;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

import io.github.luigidemasi.camelkit.ship.ShipDigest;
import io.github.luigidemasi.camelkit.ship.controller.ShipRun;
import io.github.luigidemasi.camelkit.ship.evidence.ShipLocalStamp;
import io.github.luigidemasi.camelkit.ship.evidence.ShipLocalStamp.CommandRun;
import io.github.luigidemasi.camelkit.ship.worker.LocalCommandRunner.Command;
import io.github.luigidemasi.camelkit.ship.worker.LocalCommandRunner.RetainedLog;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Runs one bounded Pi stage through one RPC process.
 *
 * <p>
 * The request working directory is the child process cwd, not a filesystem sandbox. The controller must select the
 * stage workspace and validate its outputs before accepting them.
 */
public final class PiWorker {

    private static final int MAX_PROMPT_BYTES = 1024 * 1024;
    private static final int MAX_LOG_BYTES = 16 * 1024 * 1024;
    private static final int MAX_SESSION_HEADER_BYTES = 64 * 1024;
    private static final long MAX_SESSION_BYTES = 64L * 1024 * 1024;
    private static final int MAX_VERSION_LENGTH = 1024;
    // Short values cannot be distinguished reliably from ordinary transcript text.
    private static final int MIN_SENSITIVE_TRANSCRIPT_MATCH_LENGTH = 8;
    private static final Duration VERSION_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration ABORT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration ABORT_DRAIN_RESERVE = Duration.ofSeconds(1);
    private static final Duration EXIT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration IO_TIMEOUT = Duration.ofSeconds(5);
    private static final String PROMPT_ID = "prompt-1";
    private static final String ABORT_ID = "abort-1";
    private static final String READ_ONLY_TOOLS = "read,grep,find,ls";
    private static final String EXECUTE_TOOLS = "read,bash,edit,write,grep,find,ls";
    private static final String SCRATCH_PREFIX = ".ship-pi-";
    private static final Path SETSID = Path.of("/usr/bin/setsid");
    private static final Path KILL = Path.of("/bin/kill");
    private static final Set<PosixFilePermission> PRIVATE_DIRECTORY = PosixFilePermissions.fromString("rwx------");
    private static final Set<PosixFilePermission> PRIVATE_FILE = PosixFilePermissions.fromString("rw-------");
    private static final Pattern VERSION = Pattern.compile("[0-9]+\\.[0-9]+\\.[0-9]+(?:[-+][A-Za-z0-9._-]+)?");
    private static final ObjectMapper JSON = new ObjectMapper(
            JsonFactory.builder()
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private final Path executable;
    private final String supportedVersion;
    private final Duration timeout;
    private final Clock clock;
    private final LocalCommandRunner commands;
    private final Map<String, String> environment;

    public PiWorker(Path executable, String supportedVersion, Duration timeout) {
        this(executable, supportedVersion, timeout, Clock.systemUTC());
    }

    PiWorker(Path executable, String supportedVersion, Duration timeout, Clock clock) {
        this(executable, supportedVersion, timeout, clock, System.getenv());
    }

    PiWorker(
             Path executable,
             String supportedVersion,
             Duration timeout,
             Clock clock,
             Map<String, String> environment) {
        this.executable = requireExecutable(executable);
        this.supportedVersion = requireVersion(supportedVersion, "supported Pi version");
        this.timeout = requireDuration(timeout);
        this.clock = Objects.requireNonNull(clock, "clock");
        this.commands = new LocalCommandRunner(clock);
        this.environment = Map.copyOf(Objects.requireNonNull(environment, "environment"));
    }

    /**
     * Executes one Pi stage. The prompt is sent as an RPC command and never appears in argv.
     *
     * <p>
     * A validated session turn is published atomically, but that publication is not atomic with the controller's
     * durable {@link ShipRun}. The controller must recover the result for the same run, stage, input, and attempt
     * before starting a later attempt.
     */
    public Result run(Request request) throws IOException, InterruptedException {
        Objects.requireNonNull(request, "request");
        requireLinux();
        Path workingDirectory = realDirectory(request.workingDirectory(), "Pi working directory");
        Path sessionDirectory = realDirectory(request.sessionDirectory(), "Pi session directory");
        Path evidenceDirectory = realDirectory(request.evidenceDirectory(), "Pi evidence directory");
        requireDisjoint(workingDirectory, sessionDirectory, "working and session directories");
        requireDisjoint(workingDirectory, evidenceDirectory, "working and evidence directories");
        requireDisjoint(sessionDirectory, evidenceDirectory, "session and evidence directories");
        UserPrincipal sessionOwner = requirePrivateSessionDirectory(sessionDirectory);
        String prompt = requirePrompt(request.prompt());
        List<String> sensitiveValues = List.copyOf(
                sensitiveEnvironmentValues(environment));

        LocalCommandRunner.Result versionRun = commands.run(new Command(
                executable,
                List.of("--version"),
                workingDirectory,
                evidenceDirectory,
                VERSION_TIMEOUT.compareTo(timeout) < 0 ? VERSION_TIMEOUT : timeout,
                MAX_LOG_BYTES,
                sensitiveValues));
        Throwable primary = null;
        Path scratchDirectory = null;
        SessionLock sessionLock = null;
        boolean versionLogsDeleted = false;
        boolean published = false;
        boolean restoreInterrupt = false;
        try {
            if (versionRun.timedOut()
                    || versionRun.outputLimited()
                    || versionRun.exitCode() != 0) {
                throw new IOException(
                        "Pi is installed but `pi --version` failed; reinstall Pi and verify it is on PATH");
            }
            String detectedVersion;
            try {
                detectedVersion = requireVersion(
                        firstLine(versionRun.capturedStdout()),
                        "detected Pi version");
            } catch (IllegalArgumentException | IOException e) {
                throw new IOException(
                        "Pi reported an invalid version; reinstall Pi and verify `pi --version`",
                        e);
            }
            if ("0.80.3".equals(detectedVersion)) {
                throw new IOException(
                        "Pi 0.80.3 lacks the required settled RPC event; install the maintained Pi "
                                      + supportedVersion);
            }
            ShipLocalStamp.Support support = ShipLocalStamp.Support.EXPERIMENTAL;
            String warning = detectedVersion.equals(supportedVersion)
                    ? "Pi " + detectedVersion
                      + " is experimental until the live Pi end-to-end gate passes"
                    : "Pi " + detectedVersion + " is unverified; the maintained Pi "
                      + supportedVersion + " is also experimental until the live end-to-end gate passes";
            if (!request.acceptExperimental()) {
                throw new IOException(
                        warning + "; explicitly accept experimental Pi before starting the stage");
            }

            String sessionId = sessionId(request);
            sessionLock = lockSession(sessionDirectory, sessionId, sessionOwner);
            cleanupAbandonedScratch(sessionDirectory, sessionId, sessionOwner);
            SessionState previousSession = snapshotExistingSession(
                    sessionDirectory, sessionId, workingDirectory, sessionOwner);
            refuseCompletedTurnReplay(previousSession.transcript(), prompt);
            ScratchSession scratch = createScratch(
                    sessionDirectory, sessionId, previousSession);
            scratchDirectory = scratch.directory();
            List<String> arguments = arguments(request, scratch.directory(), sessionId);
            RpcRun turn = runRpc(
                    arguments,
                    workingDirectory,
                    scratch.directory(),
                    sessionDirectory,
                    evidenceDirectory,
                    sessionId,
                    sessionOwner,
                    scratch.previous(),
                    previousSession,
                    prompt,
                    sensitiveValues);
            restoreInterrupt = turn.restoreInterrupt();
            CommandRun evidence = new CommandRun(
                    executable.toString(),
                    detectedVersion,
                    LocalCommandRunner.redactArguments(
                            arguments, sensitiveValues),
                    workingDirectory.toString(),
                    List.of(request.inputDigest()),
                    true,
                    turn.timedOut(),
                    turn.outputLimited(),
                    turn.exitCode(),
                    turn.startedAt().toString(),
                    turn.endedAt().toString(),
                    turn.stdoutLog().toString(),
                    turn.stdoutDigest(),
                    turn.stderrLog().toString(),
                    turn.stderrDigest());

            Outcome outcome = Outcome.SUCCEEDED;
            String assistantText = turn.assistantText();
            String failure = null;
            if (turn.timedOut()) {
                outcome = Outcome.TIMED_OUT;
                assistantText = null;
                failure = "Pi stage exceeded its time limit";
            } else if (turn.outputLimited()) {
                outcome = Outcome.FAILED;
                assistantText = null;
                failure = "Pi stage exceeded its retained-output limit";
            } else if (turn.exitCode() != 0) {
                outcome = Outcome.FAILED;
                assistantText = null;
                failure = "Pi exited with status " + turn.exitCode();
            } else if (turn.protocolFailure() != null) {
                outcome = Outcome.FAILED;
                assistantText = null;
                failure = turn.protocolFailure();
            } else if (!"stop".equals(turn.stopReason())) {
                outcome = Outcome.FAILED;
                assistantText = null;
                failure = "Pi assistant settled with stop reason " + turn.stopReason();
            }
            Result result = new Result(
                    outcome,
                    support,
                    detectedVersion,
                    warning,
                    assistantText,
                    failure,
                    evidence);
            versionRun.deleteLogs();
            versionLogsDeleted = true;
            if (turn.validatedSession() != null) {
                PiWorkerResultStore.write(request, result);
                try {
                    publishSession(
                            turn.validatedSession(),
                            sessionDirectory,
                            previousSession);
                } catch (IOException publicationFailure) {
                    // Only a remaining scratch file proves that the atomic rename
                    // did not publish the result. Indeterminate attribute reads
                    // retain the marker and are reported with the original failure.
                    try {
                        if (attributesIfPresent(
                                turn.validatedSession(),
                                "Could not determine whether the Pi scratch session remains")
                            != null) {
                            PiWorkerResultStore.delete(request);
                        }
                    } catch (IOException cleanupFailure) {
                        publicationFailure.addSuppressed(cleanupFailure);
                    }
                    throw publicationFailure;
                }
                published = true;
            }
            return result;
        } catch (IOException | InterruptedException | RuntimeException | Error e) {
            restoreInterrupt |= Thread.interrupted();
            primary = e;
            throw e;
        } finally {
            IOException cleanupFailure = null;
            try {
                deleteTree(scratchDirectory);
            } catch (IOException cleanup) {
                cleanupFailure = cleanup;
                if (primary != null) {
                    primary.addSuppressed(cleanup);
                }
            }
            try {
                if (sessionLock != null) {
                    sessionLock.close();
                }
            } catch (IOException cleanup) {
                if (cleanupFailure == null) {
                    cleanupFailure = cleanup;
                } else {
                    cleanupFailure.addSuppressed(cleanup);
                }
                if (primary != null) {
                    primary.addSuppressed(cleanup);
                }
            }
            if (!versionLogsDeleted) {
                try {
                    versionRun.deleteLogs();
                } catch (IOException cleanup) {
                    if (cleanupFailure == null) {
                        cleanupFailure = cleanup;
                    } else {
                        cleanupFailure.addSuppressed(cleanup);
                    }
                    if (primary != null) {
                        primary.addSuppressed(cleanup);
                    }
                }
            }
            if (primary == null && !published && cleanupFailure != null) {
                if (restoreInterrupt) {
                    Thread.currentThread().interrupt();
                }
                throw cleanupFailure;
            }
            // Once the atomic move commits a validated session, cleanup cannot change the returned outcome.
            if (restoreInterrupt) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /** Returns the durable result for this exact attempt without launching Pi. */
    public Optional<Result> recover(Request request) throws IOException {
        return PiWorkerResultStore.read(Objects.requireNonNull(request, "request"));
    }

    private RpcRun runRpc(
            List<String> arguments,
            Path workingDirectory,
            Path sessionDirectory,
            Path canonicalSessionDirectory,
            Path evidenceDirectory,
            String sessionId,
            UserPrincipal sessionOwner,
            SessionState previousSession,
            SessionState canonicalPreviousSession,
            String prompt,
            List<String> sensitiveValues)
            throws IOException, InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("Interrupted before launching Pi");
        }
        Path setsid = LocalCommandRunner.trustedHelper(SETSID, "setsid");
        Path kill = LocalCommandRunner.trustedHelper(KILL, "kill");
        List<String> vector = new ArrayList<>(arguments.size() + 4);
        vector.add(setsid.toString());
        vector.add("--wait");
        vector.add("--");
        vector.add(executable.toString());
        vector.addAll(arguments);
        Instant startedAt = clock.instant();
        Process process = new ProcessBuilder(vector)
                .directory(workingDirectory.toFile())
                .start();
        Thread shutdownHook = new Thread(
                () -> terminateRpc(process, kill),
                "camel-kit-pi-rpc-shutdown");
        RpcOutput stdout = new RpcOutput(MAX_LOG_BYTES);
        BoundedCapture stderr = new BoundedCapture(MAX_LOG_BYTES);
        ExecutorService io = null;
        Future<?> stdoutReader = null;
        Future<?> stderrReader = null;
        Future<?> promptWriter = null;
        boolean acquired = false;
        try {
            io = Executors.newFixedThreadPool(3, task -> {
                Thread thread = new Thread(task, "camel-kit-pi-rpc-io");
                thread.setDaemon(true);
                return thread;
            });
            stdoutReader = io.submit(() -> {
                stdout.read(process.getInputStream());
                return null;
            });
            stderrReader = io.submit(() -> {
                stderr.read(process.getErrorStream());
                return null;
            });
            promptWriter = io.submit(() -> {
                writeCommand(process.getOutputStream(), promptCommand(prompt));
                return null;
            });
            Runtime.getRuntime().addShutdownHook(shutdownHook);
            acquired = true;
        } finally {
            if (!acquired) {
                terminateRpc(process, kill);
                close(process.getOutputStream());
                if (promptWriter != null) {
                    promptWriter.cancel(true);
                }
                if (stdoutReader != null) {
                    stdoutReader.cancel(true);
                }
                if (stderrReader != null) {
                    stderrReader.cancel(true);
                }
                if (io != null) {
                    io.shutdownNow();
                    awaitExecutorUninterruptibly(io);
                }
            }
        }

        boolean timedOut = false;
        boolean outputLimited = false;
        boolean interrupted = false;
        boolean lateInterrupted = false;
        boolean completedNormally = false;
        String protocolFailure = null;
        Lifecycle lifecycle = new Lifecycle(prompt);
        ReconciledTurn reconciliation = null;
        CompletedTurn completedTurn = null;
        Path validatedSession = null;
        Path publishableSession = null;
        try {
            long deadline = deadline(timeout);
            while (!lifecycle.complete()) {
                IOException writeFailure = completedFailure(promptWriter, "Could not send the Pi prompt");
                if (writeFailure != null) {
                    protocolFailure = writeFailure.getMessage();
                    break;
                }
                if (stdout.limited() || stderr.limited()) {
                    outputLimited = true;
                    break;
                }
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    timedOut = true;
                    break;
                }
                Frame frame;
                try {
                    frame = stdout.poll(Math.min(
                            remaining, TimeUnit.MILLISECONDS.toNanos(100)));
                } catch (InterruptedException interruptedPoll) {
                    lateInterrupted = true;
                    try {
                        drainQueued(stdout, lifecycle);
                    } catch (IOException e) {
                        protocolFailure = e.getMessage();
                        break;
                    }
                    if (lifecycle.complete()) {
                        break;
                    }
                    throw interruptedPoll;
                }
                if (frame == null) {
                    if (!process.isAlive() && stdoutReader.isDone()) {
                        protocolFailure = "Pi exited before emitting a terminal RPC result";
                        break;
                    }
                    continue;
                }
                try {
                    acceptFrame(frame, lifecycle);
                } catch (IOException e) {
                    protocolFailure = e.getMessage();
                    break;
                }
                if (stdout.limited() || stderr.limited()) {
                    outputLimited = true;
                    break;
                }
            }

            if (lifecycle.complete() && protocolFailure == null && !outputLimited) {
                await(promptWriter, "Could not send the Pi prompt");
                close(process.getOutputStream());
            } else {
                boolean attemptedAbort = protocolFailure == null;
                if (attemptedAbort) {
                    reconciliation = abort(
                            process,
                            promptWriter,
                            stdout,
                            lifecycle);
                }
                if (reconciliation == null) {
                    if (timedOut || outputLimited) {
                        throw new IOException(
                                "Pi did not acknowledge the RPC abort; the stage session is not resumable");
                    }
                    if (protocolFailure == null) {
                        protocolFailure = "Pi did not acknowledge the RPC abort";
                    }
                }
                if (!attemptedAbort && promptWriter.isDone()) {
                    close(process.getOutputStream());
                }
            }

            if (lifecycle.complete() || reconciliation != null) {
                lateInterrupted |= Thread.interrupted();
                if (!awaitExitUninterruptibly(process, EXIT_TIMEOUT)
                        && !terminateRpc(process, kill)) {
                    throw new IOException("Pi RPC process group could not be reaped");
                }
                lateInterrupted |= Thread.interrupted();
                if (!terminateRpc(process, kill)) {
                    throw new IOException("Pi RPC process group could not be reaped");
                }
                awaitUninterruptibly(stdoutReader, "Pi RPC output did not close");
                lateInterrupted |= Thread.interrupted();
                awaitUninterruptibly(stderrReader, "Pi RPC error output did not close");
                lateInterrupted |= Thread.interrupted();
                completedTurn = lifecycle.complete()
                        ? lifecycle.turn()
                        : reconciliation.turn();
                validatedSession = validatePersistedSession(
                        sessionDirectory,
                        sessionId,
                        workingDirectory,
                        sessionOwner,
                        previousSession,
                        prompt,
                        completedTurn);
            } else {
                if (!awaitExit(process, EXIT_TIMEOUT)
                        && !terminateRpc(process, kill)) {
                    throw new IOException("Pi RPC process group could not be reaped");
                }
                if (!terminateRpc(process, kill)) {
                    throw new IOException("Pi RPC process group could not be reaped");
                }
                await(stdoutReader, "Pi RPC output did not close");
                await(stderrReader, "Pi RPC error output did not close");
            }

            if (lifecycle.complete() && protocolFailure == null && !timedOut && !outputLimited) {
                try {
                    drainAfterTerminal(stdout, lifecycle);
                } catch (IOException e) {
                    protocolFailure = e.getMessage();
                }
                lateInterrupted |= Thread.interrupted();
            }
            outputLimited |= stdout.limited() || stderr.limited();
            int actualExitCode = process.exitValue();
            boolean naturalCompletion = lifecycle.complete()
                    || (reconciliation != null
                            && reconciliation.naturalCompletion());
            boolean acknowledgedAbort = reconciliation != null
                    && !reconciliation.naturalCompletion();
            boolean effectiveTimedOut = timedOut && !naturalCompletion;
            if (actualExitCode == 0
                    && validatedSession != null
                    && !stdout.protocolUnverifiable()
                    && protocolFailure == null
                    && ((naturalCompletion && !outputLimited)
                            || (acknowledgedAbort
                                    && (timedOut || outputLimited)))) {
                publishableSession = validatedSession;
            }
            Integer exitCode = effectiveTimedOut ? null : actualExitCode;
            Instant endedAt = clock.instant();
            if (endedAt.isBefore(startedAt)) {
                endedAt = startedAt;
            }
            List<String> stdoutSecrets = new ArrayList<>(
                    sensitiveValues.size() + 1);
            stdoutSecrets.add(prompt);
            stdoutSecrets.addAll(sensitiveValues);
            RetainedLog retainedStdout = LocalCommandRunner.retain(
                    stdout.safeBytes(),
                    evidenceDirectory,
                    ".stdout.log",
                    MAX_LOG_BYTES,
                    stdoutSecrets);
            RetainedLog retainedStderr;
            try {
                retainedStderr = LocalCommandRunner.retain(
                        stderr.metadata(),
                        evidenceDirectory,
                        ".stderr.log",
                        MAX_LOG_BYTES,
                        sensitiveValues);
            } catch (IOException | RuntimeException e) {
                Files.deleteIfExists(retainedStdout.path());
                throw e;
            }
            RpcRun result = new RpcRun(
                    startedAt,
                    endedAt,
                    effectiveTimedOut,
                    outputLimited,
                    exitCode,
                    retainedStdout.path(),
                    retainedStdout.digest(),
                    retainedStderr.path(),
                    retainedStderr.digest(),
                    naturalCompletion
                            ? completedTurn.terminal().text()
                            : null,
                    naturalCompletion
                            ? completedTurn.terminal().stopReason()
                            : null,
                    protocolFailure,
                    publishableSession,
                    lateInterrupted);
            completedNormally = true;
            return result;
        } catch (InterruptedException e) {
            interrupted = true;
            reconciliation = abort(
                    process,
                    promptWriter,
                    stdout,
                    lifecycle);
            Thread.interrupted();
            if (reconciliation != null) {
                close(process.getOutputStream());
            } else {
                terminateRpc(process, kill);
            }
            if (!awaitExitUninterruptibly(process, EXIT_TIMEOUT)
                    && !terminateRpc(process, kill)) {
                e.addSuppressed(new IOException(
                        "Interrupted Pi RPC process group could not be reaped"));
            }
            Thread.interrupted();
            if (reconciliation == null) {
                e.addSuppressed(new IOException(
                        "Pi did not persist a reconciled turn before interruption cleanup"));
            } else {
                try {
                    if (process.isAlive()) {
                        throw new IOException(
                                "Interrupted Pi RPC process did not exit after its acknowledged abort");
                    }
                    if (!terminateRpc(process, kill)) {
                        throw new IOException(
                                "Interrupted Pi RPC process group could not be reaped");
                    }
                    awaitUninterruptibly(
                            stdoutReader,
                            "Interrupted Pi RPC output did not close");
                    awaitUninterruptibly(
                            stderrReader,
                            "Interrupted Pi RPC error output did not close");
                    Thread.interrupted();
                    outputLimited = stdout.limited() || stderr.limited();
                    if (outputLimited) {
                        e.addSuppressed(new IOException(
                                "Interrupted Pi RPC exceeded its retained-output limit"));
                    }
                    validatedSession = validatePersistedSession(
                            sessionDirectory,
                            sessionId,
                            workingDirectory,
                            sessionOwner,
                            previousSession,
                            prompt,
                            reconciliation.turn());
                    if (process.exitValue() == 0
                            && reconciliation.naturalCompletion()
                            && !outputLimited
                            && !stdout.protocolUnverifiable()
                            && protocolFailure == null) {
                        List<String> stdoutSecrets = new ArrayList<>(
                                sensitiveValues.size() + 1);
                        stdoutSecrets.add(prompt);
                        stdoutSecrets.addAll(sensitiveValues);
                        RetainedLog retainedStdout = LocalCommandRunner.retain(
                                stdout.safeBytes(),
                                evidenceDirectory,
                                ".stdout.log",
                                MAX_LOG_BYTES,
                                stdoutSecrets);
                        RetainedLog retainedStderr;
                        try {
                            retainedStderr = LocalCommandRunner.retain(
                                    stderr.metadata(),
                                    evidenceDirectory,
                                    ".stderr.log",
                                    MAX_LOG_BYTES,
                                    sensitiveValues);
                        } catch (IOException | RuntimeException retentionFailure) {
                            Files.deleteIfExists(retainedStdout.path());
                            throw retentionFailure;
                        }
                        Instant endedAt = clock.instant();
                        if (endedAt.isBefore(startedAt)) {
                            endedAt = startedAt;
                        }
                        interrupted = false;
                        completedNormally = true;
                        return new RpcRun(
                                startedAt,
                                endedAt,
                                false,
                                false,
                                0,
                                retainedStdout.path(),
                                retainedStdout.digest(),
                                retainedStderr.path(),
                                retainedStderr.digest(),
                                reconciliation.turn().terminal().text(),
                                reconciliation.turn().terminal().stopReason(),
                                null,
                                validatedSession,
                                true);
                    }
                    if (process.exitValue() == 0
                            && !reconciliation.naturalCompletion()
                            && !stdout.protocolUnverifiable()
                            && protocolFailure == null) {
                        publishSession(
                                validatedSession,
                                canonicalSessionDirectory,
                                canonicalPreviousSession);
                    } else if (process.exitValue() != 0) {
                        e.addSuppressed(new IOException(
                                "Pi abort session was not published after a nonzero process exit"));
                    }
                } catch (IOException invalidSession) {
                    e.addSuppressed(invalidSession);
                }
            }
            close(process.getOutputStream());
            throw e;
        } catch (IOException | RuntimeException e) {
            if (!terminateRpc(process, kill)) {
                e.addSuppressed(new IOException(
                        "Failed Pi RPC process group could not be reaped"));
            }
            close(process.getOutputStream());
            throw e;
        } finally {
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            } catch (IllegalStateException ignored) {
                // JVM shutdown already owns the hook.
            }
            close(process.getOutputStream());
            close(process.getInputStream());
            close(process.getErrorStream());
            promptWriter.cancel(true);
            stdoutReader.cancel(true);
            stderrReader.cancel(true);
            io.shutdownNow();
            awaitExecutorUninterruptibly(io);
            if (interrupted || (lateInterrupted && !completedNormally)) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static ReconciledTurn abort(
            Process process,
            Future<?> promptWriter,
            RpcOutput stdout,
            Lifecycle priorLifecycle) {
        long deadline = deadline(ABORT_TIMEOUT);
        ExecutorService writer = null;
        Future<?> abortWriter = null;
        AbortLifecycle lifecycle = new AbortLifecycle(priorLifecycle);
        boolean interrupted = Thread.interrupted();
        boolean inputClosed = false;
        try {
            while (true) {
                long abortRemaining = deadline - System.nanoTime();
                if (abortRemaining <= 0) {
                    return null;
                }
                Frame queued = stdout.pollNow();
                if (queued != null) {
                    if (queued.failure() != null) {
                        return null;
                    }
                    if (queued.endOfStream()) {
                        return reconciledAtEnd(
                                lifecycle,
                                promptWriter,
                                abortWriter,
                                deadline);
                    }
                    if (!lifecycle.accept(queued.event())) {
                        return null;
                    }
                    continue;
                }
                if (lifecycle.complete()
                        && !inputClosed
                        && promptWriter.isDone()
                        && (abortWriter == null
                                || abortWriter.isDone())) {
                    if (abortWriter != null
                            && completedFailureUninterruptibly(
                                    abortWriter,
                                    "Could not send the Pi abort")
                               != null) {
                        return null;
                    }
                    close(process.getOutputStream());
                    inputClosed = true;
                }
                if (!inputClosed
                        && lifecycle.shouldDisposeTerminal()
                        && abortRemaining
                           <= ABORT_DRAIN_RESERVE.toNanos()) {
                    if (promptWriter.isDone()
                            && (abortWriter == null
                                    || abortWriter.isDone())) {
                        if (abortWriter != null
                                && completedFailureUninterruptibly(
                                        abortWriter,
                                        "Could not send the Pi abort")
                                   != null) {
                            return null;
                        }
                        close(process.getOutputStream());
                        inputClosed = true;
                    }
                }
                if (abortWriter == null
                        && abortRemaining
                           > ABORT_DRAIN_RESERVE.toNanos()
                        && lifecycle.shouldIssueAbort()
                        && promptWriter.isDone()
                        && process.isAlive()) {
                    IOException promptFailure = completedFailureUninterruptibly(
                            promptWriter,
                            "Could not send the Pi prompt");
                    interrupted |= Thread.interrupted();
                    if (promptFailure != null) {
                        return null;
                    }
                    writer = Executors.newSingleThreadExecutor(task -> {
                        Thread thread = new Thread(
                                task, "camel-kit-pi-rpc-abort");
                        thread.setDaemon(true);
                        return thread;
                    });
                    lifecycle.markAbortIssued();
                    abortWriter = writer.submit(() -> {
                        writeCommand(
                                process.getOutputStream(), abortCommand());
                        return null;
                    });
                }
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    return null;
                }
                Frame frame;
                try {
                    frame = stdout.poll(Math.min(
                            remaining, TimeUnit.MILLISECONDS.toNanos(100)));
                } catch (InterruptedException e) {
                    interrupted = true;
                    continue;
                }
                if (frame == null) {
                    continue;
                }
                if (frame.failure() != null) {
                    return null;
                }
                if (frame.endOfStream()) {
                    return reconciledAtEnd(
                            lifecycle,
                            promptWriter,
                            abortWriter,
                            deadline);
                }
                if (!lifecycle.accept(frame.event())) {
                    return null;
                }
            }
        } finally {
            if (promptWriter.isDone()
                    && (abortWriter == null || abortWriter.isDone())) {
                close(process.getOutputStream());
            }
            if (abortWriter != null) {
                abortWriter.cancel(true);
            }
            if (writer != null) {
                writer.shutdownNow();
                awaitExecutorUntil(writer, deadline);
            }
            interrupted |= Thread.interrupted();
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static ReconciledTurn reconciledAtEnd(
            AbortLifecycle lifecycle,
            Future<?> promptWriter,
            Future<?> abortWriter,
            long deadline) {
        if (!awaitDoneUntil(promptWriter, deadline)) {
            return null;
        }
        if (completedFailureUninterruptibly(
                promptWriter, "Could not send the Pi prompt")
            != null) {
            return null;
        }
        if (abortWriter != null
                && (!awaitDoneUntil(abortWriter, deadline)
                        || completedFailureUninterruptibly(
                                abortWriter,
                                "Could not send the Pi abort")
                           != null)) {
            return null;
        }
        try {
            return lifecycle.reconciledTurn();
        } catch (IOException e) {
            return null;
        }
    }

    private static boolean awaitDoneUntil(
            Future<?> operation, long deadline) {
        boolean interrupted = Thread.interrupted();
        try {
            while (!operation.isDone()) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    return false;
                }
                try {
                    TimeUnit.NANOSECONDS.sleep(Math.min(
                            remaining,
                            TimeUnit.MILLISECONDS.toNanos(10)));
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
            return true;
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void awaitExecutorUntil(
            ExecutorService executor, long deadline) {
        boolean interrupted = Thread.interrupted();
        try {
            while (!executor.isTerminated()) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    return;
                }
                try {
                    if (!executor.awaitTermination(
                            remaining, TimeUnit.NANOSECONDS)) {
                        return;
                    }
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static boolean terminateRpc(Process process, Path kill) {
        return LocalCommandRunner.terminateProcessGroup(process, kill);
    }

    private static IOException completedFailureUninterruptibly(
            Future<?> operation, String message) {
        boolean interrupted = false;
        try {
            while (true) {
                try {
                    return completedFailure(operation, message);
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void drainQueued(RpcOutput stdout, Lifecycle lifecycle)
            throws IOException {
        Frame frame;
        while (!lifecycle.complete() && (frame = stdout.pollNow()) != null) {
            acceptFrame(frame, lifecycle);
        }
    }

    private static void acceptFrame(Frame frame, Lifecycle lifecycle)
            throws IOException {
        if (frame.endOfStream()) {
            throw new IOException("Pi closed RPC output before emitting a settled result");
        }
        if (frame.failure() != null) {
            throw new IOException(frame.failure());
        }
        lifecycle.accept(frame.event());
    }

    private static void drainAfterTerminal(RpcOutput stdout, Lifecycle lifecycle)
            throws IOException {
        boolean interrupted = false;
        try {
            long deadline = deadline(IO_TIMEOUT);
            while (true) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    throw new IOException("Pi RPC output did not close after stdin was closed");
                }
                Frame frame;
                try {
                    frame = stdout.poll(remaining);
                } catch (InterruptedException e) {
                    interrupted = true;
                    continue;
                }
                if (frame == null) {
                    throw new IOException("Pi RPC output did not close after stdin was closed");
                }
                if (frame.endOfStream()) {
                    return;
                }
                if (frame.failure() != null) {
                    throw new IOException(frame.failure());
                }
                lifecycle.accept(frame.event());
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static String sessionId(Request request) {
        return request.runId() + "-"
               + request.stage().name().toLowerCase(Locale.ROOT) + "-"
               + request.inputDigest().substring("sha256:".length());
    }

    private static List<String> arguments(
            Request request, Path sessionDirectory, String sessionId) {
        String tools = request.stage() == ShipRun.Stage.EXECUTE
                ? EXECUTE_TOOLS
                : READ_ONLY_TOOLS;
        return List.of(
                "--mode",
                "rpc",
                "--session-id",
                sessionId,
                "--session-dir",
                sessionDirectory.toString(),
                "--tools",
                tools,
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
                "--offline");
    }

    private static ObjectNode promptCommand(String prompt) {
        ObjectNode command = JSON.createObjectNode();
        command.put("id", PROMPT_ID);
        command.put("type", "prompt");
        command.put("message", prompt);
        return command;
    }

    private static ObjectNode abortCommand() {
        ObjectNode command = JSON.createObjectNode();
        command.put("id", ABORT_ID);
        command.put("type", "abort");
        return command;
    }

    private static void writeCommand(OutputStream output, JsonNode command) throws IOException {
        output.write(JSON.writeValueAsBytes(command));
        output.write('\n');
        output.flush();
    }

    private static IOException completedFailure(Future<?> operation, String message)
            throws InterruptedException {
        if (!operation.isDone()) {
            return null;
        }
        try {
            operation.get();
            return null;
        } catch (ExecutionException e) {
            return new IOException(message, e.getCause());
        } catch (java.util.concurrent.CancellationException e) {
            return new IOException(message, e);
        }
    }

    private static void await(Future<?> operation, String message)
            throws IOException, InterruptedException {
        try {
            operation.get(IO_TIMEOUT.toNanos(), TimeUnit.NANOSECONDS);
        } catch (ExecutionException e) {
            throw new IOException(message, e.getCause());
        } catch (TimeoutException e) {
            throw new IOException(message, e);
        } catch (java.util.concurrent.CancellationException e) {
            throw new IOException(message, e);
        }
    }

    private static void awaitUninterruptibly(Future<?> operation, String message)
            throws IOException {
        boolean interrupted = false;
        try {
            long deadline = deadline(IO_TIMEOUT);
            while (true) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    throw new IOException(message);
                }
                try {
                    operation.get(remaining, TimeUnit.NANOSECONDS);
                    return;
                } catch (InterruptedException e) {
                    interrupted = true;
                } catch (ExecutionException e) {
                    throw new IOException(message, e.getCause());
                } catch (TimeoutException | java.util.concurrent.CancellationException e) {
                    throw new IOException(message, e);
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static boolean awaitExit(Process process, Duration duration)
            throws InterruptedException {
        return process.waitFor(duration.toNanos(), TimeUnit.NANOSECONDS);
    }

    private static boolean awaitExitUninterruptibly(Process process, Duration duration) {
        long deadline = deadline(duration);
        boolean interrupted = false;
        try {
            while (process.isAlive()) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    return false;
                }
                try {
                    if (!process.waitFor(remaining, TimeUnit.NANOSECONDS)) {
                        return false;
                    }
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
            return true;
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void awaitExecutorUninterruptibly(ExecutorService executor) {
        boolean interrupted = false;
        try {
            long deadline = deadline(IO_TIMEOUT);
            while (!executor.isTerminated()) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    return;
                }
                try {
                    if (!executor.awaitTermination(remaining, TimeUnit.NANOSECONDS)) {
                        return;
                    }
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static long deadline(Duration duration) {
        try {
            return Math.addExact(System.nanoTime(), duration.toNanos());
        } catch (ArithmeticException e) {
            return Long.MAX_VALUE;
        }
    }

    private static String firstLine(byte[] value) throws IOException {
        String text = decodeStrict(value).trim();
        int newline = text.indexOf('\n');
        String first = (newline < 0 ? text : text.substring(0, newline)).trim();
        if (first.isEmpty()) {
            throw new IOException("Pi did not report its version");
        }
        return first;
    }

    private static String decodeStrict(byte[] value) throws IOException {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(value))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new IOException("Pi emitted invalid UTF-8", e);
        }
    }

    private static String requirePrompt(String prompt) {
        if (prompt == null || prompt.isBlank() || prompt.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Pi prompt is required");
        }
        byte[] encoded = prompt.getBytes(StandardCharsets.UTF_8);
        if (encoded.length > MAX_PROMPT_BYTES) {
            throw new IllegalArgumentException("Pi prompt exceeds its size limit");
        }
        return prompt;
    }

    private static Path requireExecutable(Path supplied) {
        Objects.requireNonNull(supplied, "Pi executable");
        try {
            Path executable = supplied.toRealPath();
            if (!Files.isRegularFile(executable, LinkOption.NOFOLLOW_LINKS)
                    || !Files.isExecutable(executable)) {
                throw new IllegalArgumentException(
                        "Pi is missing or not executable; install Pi and configure its executable path");
            }
            return executable;
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    "Pi is missing or not executable; install Pi and configure its executable path",
                    e);
        }
    }

    private static String requireVersion(String value, String label) {
        if (value == null
                || value.trim().length() > MAX_VERSION_LENGTH
                || !VERSION.matcher(value.trim()).matches()) {
            throw new IllegalArgumentException(label + " is invalid");
        }
        return value.trim();
    }

    private static Duration requireDuration(Duration value) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException("Pi timeout must be positive");
        }
        return value;
    }

    private static Path realDirectory(Path supplied, String label) throws IOException {
        if (supplied == null) {
            throw new IOException(label + " is required");
        }
        Path normalized = supplied.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(normalized)
                || !Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException(label + " must be a real directory");
        }
        return normalized.toRealPath();
    }

    private static void requireDisjoint(Path first, Path second, String label) throws IOException {
        if (first.startsWith(second) || second.startsWith(first)) {
            throw new IOException("Pi " + label + " must be disjoint");
        }
    }

    private static UserPrincipal requirePrivateSessionDirectory(Path directory)
            throws IOException {
        String userName = System.getProperty("user.name");
        if (userName == null || userName.isBlank()) {
            throw new IOException("Could not determine the current user for the Pi session directory");
        }
        UserPrincipal currentUser = FileSystems.getDefault()
                .getUserPrincipalLookupService()
                .lookupPrincipalByName(userName);
        if (!currentUser.equals(Files.getOwner(directory, LinkOption.NOFOLLOW_LINKS))) {
            throw new IOException("Pi session directory must be owned by the current user");
        }
        if (!PRIVATE_DIRECTORY.equals(
                Files.getPosixFilePermissions(directory, LinkOption.NOFOLLOW_LINKS))) {
            throw new IOException("Pi session directory permissions must be 0700");
        }
        return currentUser;
    }

    private static SessionLock lockSession(
            Path directory, String sessionId, UserPrincipal owner)
            throws IOException {
        Path path = directory.resolve("." + sessionId + ".lock");
        if (Files.isSymbolicLink(path)) {
            throw new IOException("Pi stage session lock must not be a symbolic link");
        }
        FileChannel channel = FileChannel.open(
                path,
                Set.of(
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE,
                        LinkOption.NOFOLLOW_LINKS),
                PosixFilePermissions.asFileAttribute(PRIVATE_FILE));
        try {
            if (!owner.equals(Files.getOwner(path, LinkOption.NOFOLLOW_LINKS))
                    || !PRIVATE_FILE.equals(
                            Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS))) {
                throw new IOException("Pi stage session lock must be owned by the current user and 0600");
            }
            FileLock lock;
            try {
                lock = channel.tryLock();
            } catch (OverlappingFileLockException e) {
                throw new IOException("Pi stage session is already running", e);
            }
            if (lock == null) {
                throw new IOException("Pi stage session is already running");
            }
            return new SessionLock(channel, lock);
        } catch (IOException | RuntimeException e) {
            channel.close();
            throw e;
        }
    }

    private static void cleanupAbandonedScratch(
            Path directory, String sessionId, UserPrincipal owner)
            throws IOException {
        String prefix = SCRATCH_PREFIX + sessionId + "-";
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory)) {
            for (Path entry : entries) {
                if (!entry.getFileName().toString().startsWith(prefix)) {
                    continue;
                }
                if (Files.isSymbolicLink(entry)
                        || !Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)
                        || !owner.equals(Files.getOwner(entry, LinkOption.NOFOLLOW_LINKS))
                        || !PRIVATE_DIRECTORY.equals(
                                Files.getPosixFilePermissions(
                                        entry, LinkOption.NOFOLLOW_LINKS))) {
                    throw new IOException("Pi stage scratch directory is unsafe");
                }
                deleteTree(entry);
            }
        }
    }

    private static ScratchSession createScratch(
            Path directory,
            String sessionId,
            SessionState previous)
            throws IOException {
        Path scratch = Files.createTempDirectory(
                directory,
                SCRATCH_PREFIX + sessionId + "-",
                PosixFilePermissions.asFileAttribute(PRIVATE_DIRECTORY));
        try {
            if (previous.file() == null) {
                return new ScratchSession(
                        scratch,
                        new SessionState(
                                null, 0, null, previous.transcript()));
            }
            Path copy = scratch.resolve(previous.file().getFileName());
            Files.copy(
                    previous.file(),
                    copy,
                    StandardCopyOption.COPY_ATTRIBUTES,
                    LinkOption.NOFOLLOW_LINKS);
            return new ScratchSession(
                    scratch,
                    new SessionState(
                            copy,
                            previous.size(),
                            previous.digest(),
                            previous.transcript()));
        } catch (IOException | RuntimeException e) {
            try {
                deleteTree(scratch);
            } catch (IOException cleanup) {
                e.addSuppressed(cleanup);
            }
            throw e;
        }
    }

    private static void publishSession(
            Path scratchFile,
            Path directory,
            SessionState previous)
            throws IOException {
        Files.setPosixFilePermissions(scratchFile, PRIVATE_FILE);
        try (FileChannel channel = FileChannel.open(
                scratchFile,
                StandardOpenOption.WRITE,
                LinkOption.NOFOLLOW_LINKS)) {
            channel.force(true);
        }
        Path target = previous.file() == null
                ? directory.resolve(scratchFile.getFileName())
                : previous.file();
        if (!target.toAbsolutePath().normalize().getParent().equals(directory)
                || Files.isSymbolicLink(target)) {
            throw new IOException("Pi stage session publication target is unsafe");
        }
        // This atomic rename is the commit point and intentionally the method's last
        // potentially failing operation.
        Files.move(
                scratchFile,
                target,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
    }

    static BasicFileAttributes attributesIfPresent(
            Path path, String inspectionFailure)
            throws IOException {
        try {
            return Files.readAttributes(
                    path,
                    BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
        } catch (NoSuchFileException e) {
            return null;
        } catch (SecurityException e) {
            throw new IOException(inspectionFailure, e);
        }
    }

    private static void deleteTree(Path path) throws IOException {
        if (path == null) {
            return;
        }
        BasicFileAttributes attributes = attributesIfPresent(
                path, "Pi scratch cleanup path could not be inspected");
        if (attributes == null) {
            return;
        }
        if (attributes.isSymbolicLink()) {
            throw new IOException("Pi scratch cleanup refused a symbolic link");
        }
        if (attributes.isDirectory()) {
            try (DirectoryStream<Path> entries = Files.newDirectoryStream(path)) {
                for (Path entry : entries) {
                    deleteTree(entry);
                }
            }
        }
        Files.delete(path);
    }

    private static SessionState snapshotExistingSession(
            Path directory,
            String sessionId,
            Path workingDirectory,
            UserPrincipal owner)
            throws IOException {
        List<Path> matches = sessionFiles(directory, sessionId, owner, true);
        if (matches.size() > 1) {
            throw new IOException("Pi stage session ID is ambiguous");
        }
        if (matches.isEmpty()) {
            return new SessionState(
                    null,
                    0,
                    null,
                    new SessionTranscript(null, List.of()));
        }
        Path file = matches.get(0);
        long size = boundedSessionSize(file);
        SessionTranscript transcript = parseSession(
                file, size, sessionId, workingDirectory);
        return new SessionState(
                file, size, fileDigest(file, size), transcript);
    }

    private Path validatePersistedSession(
            Path directory,
            String sessionId,
            Path workingDirectory,
            UserPrincipal owner,
            SessionState previous,
            String prompt,
            CompletedTurn turn)
            throws IOException {
        Objects.requireNonNull(turn, "turn");
        List<Path> matches = sessionFiles(directory, sessionId, owner, false);
        if (matches.size() != 1) {
            throw new IOException("Pi did not persist exactly one stage session");
        }
        Path file = matches.get(0);
        if (previous.file() != null && !previous.file().equals(file)) {
            throw new IOException("Pi replaced the existing stage session");
        }
        long size = boundedSessionSize(file);
        if (previous.file() != null) {
            if (size <= previous.size()) {
                throw new IOException("Pi did not append the current stage turn");
            }
            if (!previous.digest().equals(fileDigest(file, previous.size()))) {
                throw new IOException("Pi rewrote the existing stage session");
            }
        }
        SessionTranscript transcript = parseSession(
                file, size, sessionId, workingDirectory);
        validatePersistedTurn(previous, transcript, prompt, turn);
        rejectSensitiveEnvironmentValues(transcript, turn);
        return file;
    }

    private static void validatePersistedTurn(
            SessionState previous,
            SessionTranscript transcript,
            String prompt,
            CompletedTurn turn)
            throws IOException {
        List<JsonNode> entries = transcript.entries();
        int prefix = previous.transcript().entries().size();
        if (previous.file() == null) {
            if (transcript.header() == null
                    || entries.size() < 2
                    || !"model_change".equals(entries.get(0).path("type").textValue())
                    || !"thinking_level_change".equals(
                            entries.get(1).path("type").textValue())) {
                throw new IOException(
                        "Pi new stage session has an invalid model/thinking bootstrap");
            }
            prefix = 2;
        } else if (!previous.transcript().header().equals(transcript.header())
                || entries.size() < prefix
                || !previous.transcript().entries().equals(
                        entries.subList(0, prefix))) {
            throw new IOException("Pi rewrote the existing stage session records");
        }

        List<ExpectedRecord> expected = turn.records();
        if (previous.file() == null
                && (expected.isEmpty()
                        || !"message".equals(expected.get(0).type())
                        || !"user".equals(
                                expected.get(0).value().path("role").textValue()))) {
            throw new IOException(
                    "Pi new stage session persisted a record before its prompt");
        }
        if (entries.size() - prefix != expected.size()) {
            throw new IOException(
                    "Pi persisted records that do not match the ordered RPC results");
        }
        for (int index = 0; index < expected.size(); index++) {
            JsonNode entry = entries.get(prefix + index);
            ExpectedRecord record = expected.get(index);
            if (!record.type().equals(entry.path("type").textValue())
                    || !matchesExpectedRecord(entry, record)) {
                throw new IOException(
                        "Pi persisted records that do not match the ordered RPC results");
            }
        }

        boolean userSeen = false;
        for (ExpectedRecord record : expected) {
            if (!"message".equals(record.type())) {
                continue;
            }
            String role = record.value().path("role").textValue();
            if ("user".equals(role)) {
                if (userSeen
                        || !matchesPrompt(
                                record.value().path("content"), prompt)) {
                    throw new IOException(
                            "Pi persisted an unrelated current stage prompt");
                }
                userSeen = true;
            } else if (!userSeen) {
                throw new IOException(
                        "Pi persisted a message before the current stage prompt");
            }
        }
        if (!userSeen) {
            throw new IOException("Pi did not persist the current stage prompt");
        }
        Terminal terminal = turn.terminal();
        JsonNode lastMessage = null;
        for (ExpectedRecord record : expected) {
            if ("message".equals(record.type())) {
                lastMessage = record.value();
            }
        }
        if (lastMessage == null || !terminal.assistant().equals(lastMessage)) {
            throw new IOException(
                    "Pi persisted terminal content that does not match the RPC result");
        }
    }

    private static boolean matchesExpectedRecord(
            JsonNode entry, ExpectedRecord expected) {
        if ("message".equals(expected.type())) {
            return expected.value().equals(entry.path("message"));
        }
        if (!"compaction".equals(expected.type())) {
            return false;
        }
        JsonNode value = expected.value();
        return value.path("summary").equals(entry.path("summary"))
                && value.path("firstKeptEntryId").equals(
                        entry.path("firstKeptEntryId"))
                && value.path("tokensBefore").equals(entry.path("tokensBefore"))
                && value.path("details").equals(entry.path("details"))
                && entry.path("fromHook").isBoolean()
                && !entry.path("fromHook").booleanValue();
    }

    private static boolean matchesPrompt(JsonNode content, String prompt) {
        if (content.isTextual()) {
            return prompt.equals(content.textValue());
        }
        if (!content.isArray()) {
            return false;
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode item : content) {
            if (!item.isObject()
                    || !"text".equals(item.path("type").textValue())
                    || !item.path("text").isTextual()) {
                return false;
            }
            text.append(item.path("text").textValue());
        }
        return prompt.contentEquals(text);
    }

    private static List<Path> sessionFiles(
            Path directory,
            String sessionId,
            UserPrincipal owner,
            boolean canonical)
            throws IOException {
        List<Path> matches = new ArrayList<>(2);
        String suffix = "_" + sessionId + ".jsonl";
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory)) {
            for (Path file : files) {
                String name = file.getFileName().toString();
                if (!name.endsWith(suffix)) {
                    continue;
                }
                if (Files.isSymbolicLink(file)) {
                    throw new IOException("Pi stage session must not be a symbolic link");
                }
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                        || !owner.equals(
                                Files.getOwner(file, LinkOption.NOFOLLOW_LINKS))) {
                    throw new IOException(
                            "Pi stage session must be a regular file owned by the current user");
                }
                if (canonical
                        && !PRIVATE_FILE.equals(
                                Files.getPosixFilePermissions(
                                        file,
                                        LinkOption.NOFOLLOW_LINKS))) {
                    throw new IOException(
                            "Pi stage session permissions must be 0600");
                }
                matches.add(file.toAbsolutePath().normalize());
                if (matches.size() > 1) {
                    break;
                }
            }
        }
        return matches;
    }

    private static SessionTranscript parseSession(
            Path file,
            long expectedSize,
            String sessionId,
            Path workingDirectory)
            throws IOException {
        if (Files.isSymbolicLink(file)
                || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Pi stage session must be a real JSONL file");
        }
        byte[] bytes = new byte[Math.toIntExact(expectedSize)];
        try (InputStream input = Files.newInputStream(
                file, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            if (input.readNBytes(bytes, 0, bytes.length) != bytes.length
                    || input.read() != -1) {
                throw new IOException(
                        "Pi stage session changed while it was inspected");
            }
        }
        int headerEnd = 0;
        while (headerEnd < bytes.length && bytes[headerEnd] != '\n') {
            headerEnd++;
        }
        int headerBytes = headerEnd;
        if (headerBytes > 0 && bytes[headerBytes - 1] == '\r') {
            headerBytes--;
        }
        if (headerBytes > MAX_SESSION_HEADER_BYTES) {
            throw new IOException(
                    "Pi stage session header exceeds its size limit");
        }
        String jsonl = decodeStrict(bytes);
        String[] lines = jsonl.split("\n", -1);
        List<JsonNode> records = new ArrayList<>(lines.length);
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];
            if (line.endsWith("\r")) {
                line = line.substring(0, line.length() - 1);
            }
            if (line.isEmpty() && index == lines.length - 1) {
                continue;
            }
            if (line.isBlank()) {
                throw new IOException("Pi stage session contains a blank JSONL record");
            }
            JsonNode record;
            try {
                record = JSON.readTree(line);
            } catch (IOException e) {
                throw new IOException(
                        "Pi stage session contains a malformed JSONL record");
            }
            if (record == null || !record.isObject()) {
                throw new IOException(
                        "Pi stage session contains a malformed JSONL record");
            }
            records.add(record);
        }
        if (records.isEmpty()) {
            throw new IOException("Pi stage session has no JSONL header");
        }
        JsonNode header = records.remove(0);
        if (header == null
                || !header.isObject()
                || header.size() != 5
                || !"session".equals(header.path("type").textValue())
                || !header.path("version").isIntegralNumber()
                || header.path("version").longValue() != 3
                || !sessionId.equals(header.path("id").textValue())
                || !isNonBlankText(header.path("timestamp"))
                || !workingDirectory.toString().equals(header.path("cwd").textValue())) {
            throw new IOException("Pi stage session header does not match this stage");
        }
        if (records.isEmpty()) {
            throw new IOException("Pi stage session has no persisted turn records");
        }
        validateLinearEntries(records);
        List<JsonNode> entries = new ArrayList<>(records.size());
        for (JsonNode record : records) {
            entries.add(record.deepCopy());
        }
        return new SessionTranscript(
                header.deepCopy(),
                List.copyOf(entries));
    }

    private static void validateLinearEntries(List<JsonNode> entries)
            throws IOException {
        Set<String> ids = new HashSet<>();
        String parent = null;
        for (int index = 0; index < entries.size(); index++) {
            JsonNode entry = entries.get(index);
            if (entry == null
                    || !entry.isObject()
                    || !isNonBlankText(entry.path("id"))
                    || !isNonBlankText(entry.path("timestamp"))) {
                throw new IOException("Pi stage session has an invalid entry");
            }
            String id = entry.path("id").textValue();
            if (ids.contains(id)) {
                throw new IOException("Pi stage session has a duplicate entry ID");
            }
            JsonNode parentId = entry.get("parentId");
            if ((index == 0 && (parentId == null || !parentId.isNull()))
                    || (index > 0
                            && (parentId == null
                                    || !parentId.isTextual()
                                    || !parent.equals(parentId.textValue())))) {
                throw new IOException(
                        "Pi stage session entries do not form one linear chain");
            }

            String type = entry.path("type").textValue();
            switch (type == null ? "" : type) {
                case "model_change" -> {
                    if (index != 0
                            || entry.size() != 6
                            || !isNonBlankText(entry.path("provider"))
                            || !isNonBlankText(entry.path("modelId"))) {
                        throw new IOException(
                                "Pi stage session has an invalid model bootstrap");
                    }
                }
                case "thinking_level_change" -> {
                    if (index != 1
                            || entry.size() != 5
                            || !"model_change".equals(
                                    entries.get(0).path("type").textValue())
                            || !isNonBlankText(entry.path("thinkingLevel"))) {
                        throw new IOException(
                                "Pi stage session has an invalid thinking bootstrap");
                    }
                }
                case "message" -> validateMessageEntry(entry, index);
                case "compaction" -> validateCompactionEntry(entry, ids);
                default -> throw new IOException(
                        "Pi stage session contains an unsupported entry type");
            }
            ids.add(id);
            parent = id;
        }
        if (entries.size() < 2
                || !"model_change".equals(entries.get(0).path("type").textValue())
                || !"thinking_level_change".equals(
                        entries.get(1).path("type").textValue())) {
            throw new IOException(
                    "Pi stage session has an invalid model/thinking bootstrap");
        }
    }

    private static void validateMessageEntry(JsonNode entry, int index)
            throws IOException {
        JsonNode message = entry.get("message");
        String role = message == null ? null : message.path("role").textValue();
        if (index < 2
                || entry.size() != 5
                || message == null
                || !message.isObject()
                || (!"user".equals(role)
                        && !"assistant".equals(role)
                        && !"toolResult".equals(role))
                || message.get("content") == null) {
            throw new IOException("Pi stage session has an invalid message entry");
        }
        if ("assistant".equals(role)
                && !isNonBlankText(message.path("stopReason"))) {
            throw new IOException(
                    "Pi stage session has an assistant without a stop reason");
        }
    }

    private static void validateCompactionEntry(
            JsonNode entry, Set<String> priorIds)
            throws IOException {
        String firstKept = entry.path("firstKeptEntryId").textValue();
        if (entry.size() != 9
                || !isNonBlankText(entry.path("summary"))
                || firstKept == null
                || !priorIds.contains(firstKept)
                || !entry.path("tokensBefore").isIntegralNumber()
                || entry.path("tokensBefore").longValue() < 0
                || entry.get("details") == null
                || !entry.path("fromHook").isBoolean()
                || entry.path("fromHook").booleanValue()) {
            throw new IOException("Pi stage session has an invalid compaction entry");
        }
    }

    private static boolean isNonBlankText(JsonNode value) {
        return value != null
                && value.isTextual()
                && !value.textValue().isBlank();
    }

    private static void refuseCompletedTurnReplay(
            SessionTranscript transcript, String prompt)
            throws IOException {
        if (transcript == null || transcript.entries().isEmpty()) {
            return;
        }
        JsonNode lastUser = null;
        JsonNode lastAssistant = null;
        for (JsonNode entry : transcript.entries()) {
            if (!"message".equals(entry.path("type").textValue())) {
                continue;
            }
            JsonNode message = entry.path("message");
            String role = message.path("role").textValue();
            if ("user".equals(role)) {
                lastUser = message;
                lastAssistant = null;
            } else if (lastUser != null && "assistant".equals(role)) {
                lastAssistant = message;
            }
        }
        if (lastUser != null
                && lastAssistant != null
                && matchesPrompt(lastUser.path("content"), prompt)
                && "stop".equals(
                        lastAssistant.path("stopReason").textValue())) {
            throw new IOException(
                    "Pi completed turn requires controller recovery before another attempt");
        }
    }

    private void rejectSensitiveEnvironmentValues(
            SessionTranscript transcript, CompletedTurn turn)
            throws IOException {
        Set<String> sensitive = sensitiveEnvironmentValues(environment);
        if (sensitive.isEmpty()) {
            return;
        }
        if (containsSensitiveValue(transcript.header(), sensitive)) {
            throw sensitiveOutput();
        }
        for (JsonNode entry : transcript.entries()) {
            if (containsSensitiveValue(entry, sensitive)) {
                throw sensitiveOutput();
            }
        }
        if (containsSensitiveValue(turn.terminal().assistant(), sensitive)
                || containsSensitiveValue(turn.terminal().text(), sensitive)) {
            throw sensitiveOutput();
        }
    }

    private static Set<String> sensitiveEnvironmentValues(
            Map<String, String> environment) {
        Set<String> values = new HashSet<>();
        environment.forEach((name, value) -> {
            if (LocalCommandRunner.isSensitiveEnvironmentValue(name, value)) {
                values.add(value);
            }
        });
        return values;
    }

    private static boolean containsSensitiveValue(
            JsonNode node, Set<String> sensitive) {
        if (node == null) {
            return false;
        }
        if (node.isObject()) {
            var fields = node.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                if (containsSensitiveValue(field.getKey(), sensitive)
                        || containsSensitiveValue(
                                field.getValue(), sensitive)) {
                    return true;
                }
            }
            return false;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                if (containsSensitiveValue(child, sensitive)) {
                    return true;
                }
            }
            return false;
        }
        return node.isValueNode()
                && containsSensitiveValue(node.asText(), sensitive);
    }

    private static boolean containsSensitiveValue(
            String text, Set<String> sensitive) {
        if (text == null) {
            return false;
        }
        for (String value : sensitive) {
            if (value.length() >= MIN_SENSITIVE_TRANSCRIPT_MATCH_LENGTH
                    && text.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private static IOException sensitiveOutput() {
        return new IOException(
                "Pi output contained a sensitive environment value");
    }

    private static long boundedSessionSize(Path file) throws IOException {
        long size = Files.size(file);
        if (size > MAX_SESSION_BYTES) {
            throw new IOException("Pi stage session exceeds its size limit");
        }
        return size;
    }

    private static String fileDigest(Path file, long length) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
        try (InputStream input = new DigestInputStream(
                Files.newInputStream(
                        file, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS),
                digest)) {
            byte[] buffer = new byte[8192];
            long remaining = length;
            while (remaining > 0) {
                int read = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (read < 0) {
                    throw new IOException("Pi stage session changed while it was inspected");
                }
                remaining -= read;
            }
        }
        return "sha256:" + HexFormat.of().formatHex(digest.digest());
    }

    private static void requireLinux() {
        String os = System.getProperty("os.name", "");
        if (!os.toLowerCase(Locale.ROOT).contains("linux")) {
            throw new IllegalStateException("The first Pi Ship worker supports Linux only");
        }
    }

    private static void close(OutputStream output) {
        try {
            output.close();
        } catch (IOException ignored) {
            // Process cleanup remains authoritative.
        }
    }

    private static void close(InputStream input) {
        try {
            input.close();
        } catch (IOException ignored) {
            // Process cleanup remains authoritative.
        }
    }

    public enum Outcome {
        SUCCEEDED,
        FAILED,
        TIMED_OUT
    }

    public record Request(
            String runId,
            ShipRun.Stage stage,
            int attempt,
            Path workingDirectory,
            Path sessionDirectory,
            Path evidenceDirectory,
            String inputDigest,
            boolean acceptExperimental,
            String prompt) {

        public Request {
            if (!ShipRun.isRunId(runId)) {
                throw new IllegalArgumentException("Pi request run ID is invalid");
            }
            Objects.requireNonNull(stage, "stage");
            if (attempt <= 0) {
                throw new IllegalArgumentException("Pi request attempt must be positive");
            }
            Objects.requireNonNull(workingDirectory, "workingDirectory");
            Objects.requireNonNull(sessionDirectory, "sessionDirectory");
            Objects.requireNonNull(evidenceDirectory, "evidenceDirectory");
            if (!ShipDigest.isSha256(inputDigest)) {
                throw new IllegalArgumentException("Pi request input digest is invalid");
            }
            Objects.requireNonNull(prompt, "prompt");
        }

        @Override
        public String toString() {
            return "Request[runId=" + runId
                   + ", stage=" + stage
                   + ", attempt=" + attempt
                   + ", workingDirectory=" + workingDirectory
                   + ", sessionDirectory=" + sessionDirectory
                   + ", evidenceDirectory=" + evidenceDirectory
                   + ", inputDigest=" + inputDigest
                   + ", acceptExperimental=" + acceptExperimental
                   + ", prompt=<redacted>]";
        }
    }

    public record Result(
            Outcome outcome,
            ShipLocalStamp.Support support,
            String version,
            String warning,
            String assistantText,
            String failure,
            CommandRun evidence) {

        public Result {
            Objects.requireNonNull(outcome, "outcome");
            Objects.requireNonNull(support, "support");
            version = requireVersion(version, "Pi result version");
            Objects.requireNonNull(evidence, "evidence");
            if (support != ShipLocalStamp.Support.EXPERIMENTAL) {
                throw new IllegalArgumentException(
                        "Pi worker results remain experimental until the live end-to-end gate passes");
            }
            if (!version.equals(evidence.version())) {
                throw new IllegalArgumentException("Pi result version does not match its evidence");
            }
            if (warning == null || warning.isBlank()) {
                throw new IllegalArgumentException("Experimental Pi results require a warning");
            }
            if (outcome == Outcome.SUCCEEDED) {
                if (assistantText == null || failure != null || !evidence.succeeded()) {
                    throw new IllegalArgumentException("Successful Pi result is inconsistent");
                }
            } else if (outcome == Outcome.TIMED_OUT && !evidence.timedOut()) {
                throw new IllegalArgumentException("Timed-out Pi result requires timed-out evidence");
            } else if (assistantText != null || failure == null || failure.isBlank()) {
                throw new IllegalArgumentException("Failed Pi result is inconsistent");
            }
            if (outcome != Outcome.TIMED_OUT && evidence.timedOut()) {
                throw new IllegalArgumentException("Only a timed-out Pi result can carry timed-out evidence");
            }
        }

        @Override
        public String toString() {
            return "Result[outcome=" + outcome
                   + ", support=" + support
                   + ", version=" + version
                   + ", warning=<redacted>"
                   + ", assistantText=<redacted>"
                   + ", failure=<redacted>"
                   + ", evidence=" + evidence + "]";
        }
    }

    private static final class Lifecycle {

        private final String prompt;
        private final List<JsonNode> messages = new ArrayList<>();
        private final List<ExpectedRecord> records = new ArrayList<>();
        private final List<Terminal> agentResults = new ArrayList<>();
        private boolean promptResponse;
        private boolean settled;
        private Terminal terminal;

        private Lifecycle(String prompt) {
            this.prompt = prompt;
        }

        void accept(JsonNode event) throws IOException {
            String type = eventType(event);
            if ("response".equals(type)) {
                requireResponse(event, PROMPT_ID, "prompt");
                if (promptResponse) {
                    throw new IOException("Pi emitted a duplicate prompt response");
                }
                promptResponse = true;
                return;
            }
            if (settled) {
                throw new IOException("Pi emitted an event after it settled");
            }
            if ("agent_settled".equals(type)) {
                requireSettled(event);
                if (terminal == null) {
                    throw new IOException("Pi settled before emitting an agent result");
                }
                settled = true;
                return;
            }
            if ("session".equals(type)) {
                throw new IOException("Pi emitted an unexpected RPC session event");
            }
            if ("message_end".equals(type)) {
                acceptMessageEnd(event, prompt, messages, records);
            } else if ("compaction_end".equals(type)) {
                acceptCompactionEnd(event, records);
            } else if ("agent_end".equals(type)) {
                terminal = terminalCandidate(event);
                agentResults.add(terminal);
            }
        }

        boolean complete() {
            return promptResponse && terminal != null && settled;
        }

        Terminal terminal() {
            return terminal;
        }

        List<JsonNode> messages() {
            return List.copyOf(messages);
        }

        List<ExpectedRecord> records() {
            return List.copyOf(records);
        }

        List<Terminal> agentResults() {
            return List.copyOf(agentResults);
        }

        CompletedTurn turn() throws IOException {
            requireTerminalMessage(
                    terminal, messages, records, agentResults);
            return new CompletedTurn(
                    terminal,
                    List.copyOf(messages),
                    List.copyOf(records));
        }
    }

    private static final class AbortLifecycle {

        private final String prompt;
        private final List<JsonNode> messages;
        private final List<ExpectedRecord> records;
        private final List<Terminal> agentResults;
        private boolean promptResponse;
        private boolean abortResponse;
        private boolean abortIssued;
        private boolean continuationReady;
        private boolean settled;
        private Terminal terminal;

        private AbortLifecycle(Lifecycle prior) {
            this.prompt = prior.prompt;
            this.messages = new ArrayList<>(prior.messages);
            this.records = new ArrayList<>(prior.records);
            this.agentResults = new ArrayList<>(prior.agentResults);
            this.promptResponse = prior.promptResponse;
            this.settled = prior.settled;
            this.terminal = prior.terminal;
            this.continuationReady = !records.isEmpty()
                    && records.get(records.size() - 1).continuesTurn();
        }

        boolean accept(JsonNode event) {
            try {
                String type = eventType(event);
                if ("response".equals(type)) {
                    if (PROMPT_ID.equals(event.path("id").textValue())) {
                        requireResponse(event, PROMPT_ID, "prompt");
                        if (promptResponse) {
                            return false;
                        }
                        promptResponse = true;
                    } else {
                        requireResponse(event, ABORT_ID, "abort");
                        if (!abortIssued || abortResponse) {
                            return false;
                        }
                        abortResponse = true;
                    }
                } else if (settled) {
                    return false;
                } else if ("agent_settled".equals(type)) {
                    requireSettled(event);
                    if (terminal == null
                            || !(isAbortedTerminal(terminal)
                                    || isCancelledRetryTerminal(terminal)
                                    || isNaturalTerminal(terminal))) {
                        return false;
                    }
                    settled = true;
                } else if ("session".equals(type)) {
                    return false;
                } else if ("message_end".equals(type)) {
                    acceptMessageEnd(event, prompt, messages, records);
                    continuationReady = false;
                } else if ("compaction_end".equals(type)) {
                    acceptCompactionEnd(event, records);
                    continuationReady = !records.isEmpty()
                            && records.get(records.size() - 1)
                                    .continuesTurn();
                } else if ("agent_end".equals(type)) {
                    terminal = terminalCandidate(event);
                    agentResults.add(terminal);
                    continuationReady = false;
                }
                return true;
            } catch (IOException e) {
                return false;
            }
        }

        boolean shouldIssueAbort() {
            return promptResponse
                    && !settled
                    && !abortIssued
                    && (terminal == null
                            || terminal.willRetry()
                            || continuationReady
                            || !terminalMatchesLastMessage());
        }

        boolean shouldDisposeTerminal() {
            return !settled && authoritativeTerminal();
        }

        void markAbortIssued() {
            abortIssued = true;
        }

        boolean complete() {
            return naturalCompletion()
                    || (promptResponse
                            && abortIssued
                            && abortResponse
                            && settled
                            && terminal != null
                            && (isAbortedTerminal(terminal)
                                    || isCancelledRetryTerminal(terminal)));
        }

        boolean naturalCompletion() {
            return settled && authoritativeTerminal();
        }

        ReconciledTurn reconciledTurn() throws IOException {
            boolean natural = naturalCompletion()
                    || authoritativeTerminal();
            if (!complete() && !natural) {
                throw new IOException(
                        "Pi did not emit a complete reconciled turn");
            }
            requireTerminalMessage(
                    terminal, messages, records, agentResults);
            return new ReconciledTurn(
                    new CompletedTurn(
                            terminal,
                            List.copyOf(messages),
                            List.copyOf(records)),
                    natural);
        }

        private static boolean isAbortedTerminal(Terminal value) {
            return !value.willRetry()
                    && "aborted".equals(value.stopReason());
        }

        private static boolean isCancelledRetryTerminal(Terminal value) {
            return value.willRetry()
                    && "error".equals(value.stopReason());
        }

        private static boolean isNaturalTerminal(Terminal value) {
            return !value.willRetry()
                    && !"aborted".equals(value.stopReason());
        }

        private boolean authoritativeTerminal() {
            return promptResponse
                    && terminal != null
                    && isNaturalTerminal(terminal)
                    && !continuationReady
                    && terminalMatchesLastMessage();
        }

        private boolean terminalMatchesLastMessage() {
            return !messages.isEmpty()
                    && terminal != null
                    && terminal.assistant().equals(
                            messages.get(messages.size() - 1));
        }
    }

    private static String eventType(JsonNode event) throws IOException {
        if (event == null || !event.isObject() || !event.path("type").isTextual()) {
            throw new IOException("Pi emitted an invalid JSON event");
        }
        return event.path("type").textValue();
    }

    private static void requireResponse(JsonNode event, String id, String command)
            throws IOException {
        if (event.size() != 4
                || !id.equals(event.path("id").textValue())
                || !command.equals(event.path("command").textValue())
                || !event.path("success").isBoolean()
                || !event.path("success").booleanValue()) {
            throw new IOException("Pi emitted an invalid " + command + " response");
        }
    }

    private static void requireSettled(JsonNode event) throws IOException {
        if (event.size() != 1) {
            throw new IOException("Pi emitted an invalid settled event");
        }
    }

    private static void acceptMessageEnd(
            JsonNode event,
            String prompt,
            List<JsonNode> messages,
            List<ExpectedRecord> records)
            throws IOException {
        JsonNode message = event.get("message");
        if (event.size() != 2 || message == null || !message.isObject()) {
            throw new IOException("Pi emitted an invalid message result");
        }
        String role = message.path("role").textValue();
        if ("user".equals(role)) {
            if (!messages.isEmpty() || !matchesPrompt(message.path("content"), prompt)) {
                throw new IOException("Pi emitted an unrelated user message");
            }
        } else if (!"assistant".equals(role) && !"toolResult".equals(role)) {
            throw new IOException("Pi emitted an unsupported message result");
        } else if (messages.isEmpty()) {
            throw new IOException("Pi emitted a message before the stage prompt");
        }
        JsonNode copy = message.deepCopy();
        messages.add(copy);
        records.add(new ExpectedRecord("message", copy, false));
    }

    private static void acceptCompactionEnd(
            JsonNode event, List<ExpectedRecord> records)
            throws IOException {
        if (!isNonBlankText(event.path("reason"))
                || !event.path("aborted").isBoolean()
                || !event.path("willRetry").isBoolean()) {
            throw new IOException("Pi emitted an invalid compaction result");
        }
        JsonNode result = event.get("result");
        if (result == null) {
            if (event.path("willRetry").booleanValue()
                    || (event.path("aborted").booleanValue()
                            ? event.size() != 4
                            : event.size() != 5
                                    || !isNonBlankText(event.path("errorMessage")))) {
                throw new IOException("Pi emitted an invalid compaction result");
            }
            return;
        }
        if (event.size() != 5
                || event.path("aborted").booleanValue()
                || !result.isObject()
                || result.size() != 5
                || !isNonBlankText(result.path("summary"))
                || !isNonBlankText(result.path("firstKeptEntryId"))
                || !result.path("tokensBefore").isIntegralNumber()
                || result.path("tokensBefore").longValue() < 0
                || !result.path("estimatedTokensAfter").isIntegralNumber()
                || result.path("estimatedTokensAfter").longValue() < 0
                || result.get("details") == null) {
            throw new IOException("Pi emitted an invalid compaction result");
        }
        ObjectNode persisted = JSON.createObjectNode();
        persisted.set("summary", result.path("summary").deepCopy());
        persisted.set(
                "firstKeptEntryId",
                result.path("firstKeptEntryId").deepCopy());
        persisted.set(
                "tokensBefore", result.path("tokensBefore").deepCopy());
        persisted.set("details", result.path("details").deepCopy());
        records.add(new ExpectedRecord(
                "compaction",
                persisted,
                event.path("willRetry").booleanValue()));
    }

    private static void requireTerminalMessage(
            Terminal terminal,
            List<JsonNode> messages,
            List<ExpectedRecord> records,
            List<Terminal> agentResults)
            throws IOException {
        if (terminal == null
                || messages.size() < 2
                || !"user".equals(messages.get(0).path("role").textValue())
                || !terminal.assistant().equals(messages.get(messages.size() - 1))) {
            throw new IOException(
                    "Pi terminal assistant does not match its ordered message results");
        }
        Set<String> outstandingTools = new HashSet<>();
        for (int index = 1; index < messages.size(); index++) {
            JsonNode message = messages.get(index);
            String role = message.path("role").textValue();
            if ("assistant".equals(role)) {
                boolean last = index == messages.size() - 1;
                String stopReason = message.path("stopReason").textValue();
                if (!outstandingTools.isEmpty()) {
                    if (!last || !"aborted".equals(stopReason)) {
                        throw new IOException(
                                "Pi emitted an assistant before all tool results");
                    }
                    outstandingTools.clear();
                }
                if ("toolUse".equals(stopReason)) {
                    collectToolCalls(message.path("content"), outstandingTools);
                    if (last || outstandingTools.isEmpty()) {
                        throw new IOException(
                                "Pi emitted an invalid tool-use assistant");
                    }
                } else {
                    Terminal result = matchingAgentResult(
                            message, agentResults);
                    if (result == null
                            || (!last
                                    && !result.willRetry()
                                    && !(("error".equals(stopReason)
                                            || "length".equals(stopReason))
                                            && hasCompactionContinuation(
                                                    index, records)))) {
                        throw new IOException(
                                "Pi emitted an unrelated intermediate assistant");
                    }
                }
            } else if ("toolResult".equals(role)) {
                String callId = message.path("toolCallId").textValue();
                if (callId == null || !outstandingTools.remove(callId)) {
                    throw new IOException(
                            "Pi emitted an unrelated tool result");
                }
            }
        }
        if (!outstandingTools.isEmpty()) {
            throw new IOException("Pi omitted a tool result");
        }
        for (Terminal result : agentResults) {
            if (messages.stream().noneMatch(result.assistant()::equals)) {
                throw new IOException(
                        "Pi agent result does not match an ordered message result");
            }
        }
    }

    private static boolean hasCompactionContinuation(
            int messageIndex, List<ExpectedRecord> records) {
        int currentMessage = -1;
        boolean afterMessage = false;
        for (ExpectedRecord record : records) {
            if ("message".equals(record.type())) {
                if (afterMessage) {
                    return false;
                }
                currentMessage++;
                afterMessage = currentMessage == messageIndex;
            } else if (afterMessage
                    && "compaction".equals(record.type())
                    && record.continuesTurn()) {
                return true;
            }
        }
        return false;
    }

    private static Terminal matchingAgentResult(
            JsonNode assistant, List<Terminal> agentResults) {
        for (Terminal result : agentResults) {
            if (result.assistant().equals(assistant)) {
                return result;
            }
        }
        return null;
    }

    private static void collectToolCalls(
            JsonNode content, Set<String> callIds)
            throws IOException {
        if (!content.isArray()) {
            throw new IOException("Pi tool-use assistant has invalid content");
        }
        for (JsonNode item : content) {
            if ("toolCall".equals(item.path("type").textValue())
                    && isNonBlankText(item.path("id"))
                    && !callIds.add(item.path("id").textValue())) {
                throw new IOException("Pi emitted a duplicate tool call");
            }
        }
    }

    private static Terminal terminalCandidate(JsonNode event)
            throws IOException {
        JsonNode willRetry = event.get("willRetry");
        if (willRetry == null || !willRetry.isBoolean()) {
            throw new IOException("Pi agent result has an invalid retry state");
        }
        JsonNode messages = event.get("messages");
        if (messages == null || !messages.isArray() || messages.isEmpty()) {
            throw new IOException("Pi agent result has no assistant message");
        }
        JsonNode assistant = messages.get(messages.size() - 1);
        JsonNode content = assistant.get("content");
        String stopReason = assistant.path("stopReason").textValue();
        if (!"assistant".equals(assistant.path("role").textValue())
                || stopReason == null
                || stopReason.isBlank()
                || content == null
                || !content.isArray()
                || (willRetry.booleanValue()
                        && !"error".equals(stopReason))) {
            throw new IOException("Pi agent result has an invalid assistant message");
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode item : content) {
            if ("text".equals(item.path("type").textValue()) && item.path("text").isTextual()) {
                text.append(item.path("text").textValue());
            }
        }
        return new Terminal(
                assistant.deepCopy(),
                stopReason,
                text.toString(),
                willRetry.booleanValue());
    }

    private static final class RpcOutput {

        private static final int QUEUE_CAPACITY = 256;
        private static final byte[] INVALID = "{\"type\":\"invalid-json-redacted\"}\n".getBytes(StandardCharsets.UTF_8);
        private static final Set<String> TRANSIENT_EVENTS = Set.of(
                "message_update", "tool_execution_update");

        private final int limit;
        private final BlockingQueue<Frame> frames = new LinkedBlockingQueue<>();
        private final ByteArrayOutputStream safe = new ByteArrayOutputStream(8192);
        private final AtomicBoolean limited = new AtomicBoolean();
        private final AtomicBoolean protocolUnverifiable = new AtomicBoolean();
        private long primaryMaterialBytes;
        private long recoveryMaterialBytes;
        private long queuedBytes;
        private int queuedFrames;
        private boolean settled;

        private RpcOutput(int limit) {
            this.limit = limit;
        }

        void read(InputStream input) throws IOException, InterruptedException {
            ByteArrayOutputStream line = new ByteArrayOutputStream(8192);
            boolean lineLimited = false;
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                for (int index = 0; index < read; index++) {
                    int value = buffer[index] & 0xff;
                    if (value == '\n') {
                        emit(line.toByteArray(), lineLimited, true);
                        line.reset();
                        lineLimited = false;
                    } else if (line.size() < limit) {
                        line.write(value);
                    } else {
                        lineLimited = true;
                        limited.set(true);
                    }
                }
            }
            if (line.size() > 0 || lineLimited) {
                emit(line.toByteArray(), lineLimited, false);
            }
            frames.offer(Frame.end());
        }

        private void emit(byte[] bytes, boolean truncated, boolean terminated)
                throws InterruptedException {
            long rawBytes = bytes.length + (terminated ? 1L : 0L);
            if (bytes.length > 0 && bytes[bytes.length - 1] == '\r') {
                bytes = java.util.Arrays.copyOf(bytes, bytes.length - 1);
            }
            if (truncated) {
                markUnverifiableLimit();
                return;
            }
            JsonNode event;
            try {
                event = JSON.readTree(decodeStrict(bytes));
            } catch (IOException e) {
                append(INVALID);
                protocolUnverifiable.set(true);
                offer(Frame.failed("Pi emitted malformed JSON output"));
                return;
            }
            String type = event == null ? null : event.path("type").textValue();
            if (!settled && TRANSIENT_EVENTS.contains(type)) {
                return;
            }
            if (!admitMaterial(rawBytes)) {
                return;
            }
            byte[] retained;
            try {
                retained = JSON.writeValueAsBytes(sanitize(event));
            } catch (IOException e) {
                append(INVALID);
                protocolUnverifiable.set(true);
                offer(Frame.failed("Pi output could not be retained"));
                return;
            }
            appendRetained(retained);
            appendRetained(new byte[]{'\n'});
            offer(Frame.event(event, rawBytes));
            settled |= "agent_settled".equals(type);
        }

        private boolean admitMaterial(long rawBytes) {
            if (!limited.get()
                    && primaryMaterialBytes <= limit - rawBytes) {
                primaryMaterialBytes += rawBytes;
                return true;
            }
            limited.set(true);
            if (recoveryMaterialBytes <= limit - rawBytes) {
                recoveryMaterialBytes += rawBytes;
                return true;
            }
            markUnverifiableLimit();
            return false;
        }

        private void markUnverifiableLimit() {
            limited.set(true);
            if (protocolUnverifiable.compareAndSet(false, true)) {
                append(INVALID);
            }
        }

        private synchronized void offer(Frame frame) {
            long rawBytes = frame.rawBytes();
            if (queuedFrames >= QUEUE_CAPACITY
                    || queuedBytes > limit - rawBytes) {
                markUnverifiableLimit();
                return;
            }
            queuedFrames++;
            queuedBytes += rawBytes;
            frames.offer(frame);
        }

        private synchronized void release(Frame frame) {
            if (!frame.endOfStream()) {
                queuedFrames--;
                queuedBytes -= frame.rawBytes();
            }
        }

        private synchronized void append(byte[] bytes) {
            int remaining = limit - safe.size();
            if (remaining > 0) {
                safe.write(bytes, 0, Math.min(remaining, bytes.length));
            }
        }

        private synchronized void appendRetained(byte[] bytes) {
            if (bytes.length > limit - safe.size()) {
                limited.set(true);
                append(INVALID);
                return;
            }
            safe.write(bytes, 0, bytes.length);
        }

        Frame poll(long nanos) throws InterruptedException {
            Frame frame = frames.poll(nanos, TimeUnit.NANOSECONDS);
            if (frame != null) {
                release(frame);
            }
            return frame;
        }

        Frame pollNow() {
            Frame frame = frames.poll();
            if (frame != null) {
                release(frame);
            }
            return frame;
        }

        boolean limited() {
            return limited.get();
        }

        boolean protocolUnverifiable() {
            return protocolUnverifiable.get();
        }

        synchronized byte[] safeBytes() {
            return safe.toByteArray();
        }

        private static JsonNode sanitize(JsonNode event) {
            ObjectNode safe = JSON.createObjectNode();
            String type = event == null ? null : event.path("type").textValue();
            if ("response".equals(type)) {
                safe.put("type", "response");
                String id = event.path("id").textValue();
                safe.put(
                        "id",
                        PROMPT_ID.equals(id) || ABORT_ID.equals(id)
                                ? id
                                : ShipLocalStamp.REDACTED);
                String command = event.path("command").textValue();
                safe.put(
                        "command",
                        "prompt".equals(command) || "abort".equals(command)
                                ? command
                                : ShipLocalStamp.REDACTED);
                if (event.path("success").isBoolean()) {
                    safe.put("success", event.path("success").booleanValue());
                }
            } else if ("agent_end".equals(type)) {
                safe.put("type", "agent_end");
                if (event.path("willRetry").isBoolean()) {
                    safe.put("willRetry", event.path("willRetry").booleanValue());
                }
                safe.put("content", ShipLocalStamp.REDACTED);
            } else if ("agent_settled".equals(type)) {
                safe.put("type", "agent_settled");
            } else {
                safe.put("type", "event-redacted");
            }
            return safe;
        }
    }

    private static final class BoundedCapture {

        private final int limit;
        private final AtomicBoolean limited = new AtomicBoolean();
        private final AtomicLong observed = new AtomicLong();

        private BoundedCapture(int limit) {
            this.limit = limit;
        }

        void read(InputStream input) throws IOException {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                long current = observed.get();
                observed.set(current > Long.MAX_VALUE - read
                        ? Long.MAX_VALUE
                        : current + read);
                if (observed.get() > limit) {
                    limited.set(true);
                }
            }
        }

        boolean limited() {
            return limited.get();
        }

        byte[] metadata() {
            return ("{\"bytes\":" + observed.get()
                    + ",\"limited\":" + limited()
                    + ",\"content\":\"" + ShipLocalStamp.REDACTED + "\"}\n")
                    .getBytes(StandardCharsets.UTF_8);
        }
    }

    private record Frame(
            JsonNode event, String failure, boolean endOfStream, long rawBytes) {

        private static Frame event(JsonNode event, long rawBytes) {
            return new Frame(event, null, false, rawBytes);
        }

        private static Frame failed(String failure) {
            return new Frame(null, failure, false, 0);
        }

        private static Frame end() {
            return new Frame(null, null, true, 0);
        }
    }

    private record SessionState(
            Path file, long size, String digest, SessionTranscript transcript) {
    }

    private record ScratchSession(Path directory, SessionState previous) {
    }

    private record SessionTranscript(JsonNode header, List<JsonNode> entries) {
    }

    private record ExpectedRecord(
            String type, JsonNode value, boolean continuesTurn) {
    }

    private record SessionLock(FileChannel channel, FileLock lock)
            implements
                AutoCloseable {

        @Override
        public void close() throws IOException {
            IOException failure = null;
            try {
                lock.release();
            } catch (IOException e) {
                failure = e;
            }
            try {
                channel.close();
            } catch (IOException e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
            if (failure != null) {
                throw failure;
            }
        }
    }

    private record Terminal(
            JsonNode assistant, String stopReason, String text, boolean willRetry) {
    }

    private record CompletedTurn(
            Terminal terminal,
            List<JsonNode> messages,
            List<ExpectedRecord> records) {
    }

    private record ReconciledTurn(
            CompletedTurn turn, boolean naturalCompletion) {
    }

    private record RpcRun(
            Instant startedAt,
            Instant endedAt,
            boolean timedOut,
            boolean outputLimited,
            Integer exitCode,
            Path stdoutLog,
            String stdoutDigest,
            Path stderrLog,
            String stderrDigest,
            String assistantText,
            String stopReason,
            String protocolFailure,
            Path validatedSession,
            boolean restoreInterrupt) {
    }
}
