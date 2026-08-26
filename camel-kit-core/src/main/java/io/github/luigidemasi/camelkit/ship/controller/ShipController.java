package io.github.luigidemasi.camelkit.ship.controller;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import io.github.luigidemasi.camelkit.ship.ShipDigest;
import io.github.luigidemasi.camelkit.ship.context.ShipContext;
import io.github.luigidemasi.camelkit.ship.context.ShipContext.Input;
import io.github.luigidemasi.camelkit.ship.controller.ShipRun.ArtifactRef;
import io.github.luigidemasi.camelkit.ship.controller.ShipRun.Oversight;
import io.github.luigidemasi.camelkit.ship.controller.ShipRun.RunStatus;
import io.github.luigidemasi.camelkit.ship.controller.ShipRun.Stage;
import io.github.luigidemasi.camelkit.ship.controller.ShipRun.StageRecord;
import io.github.luigidemasi.camelkit.ship.controller.ShipRun.StageStatus;
import io.github.luigidemasi.camelkit.ship.evidence.ShipLocalStamp;
import io.github.luigidemasi.camelkit.ship.evidence.ShipLocalStampStore;
import io.github.luigidemasi.camelkit.ship.evidence.ShipLocalStampStore.VerifiedStamp;
import io.github.luigidemasi.camelkit.ship.security.ProjectEvidenceFiles;
import io.github.luigidemasi.camelkit.ship.security.ProjectSnapshot;
import io.github.luigidemasi.camelkit.ship.security.ShipTreePolicy;
import io.github.luigidemasi.camelkit.ship.security.ShipTreePolicy.Classification;
import io.github.luigidemasi.camelkit.ship.worker.ChangedWorkspaceSecretScanner;
import io.github.luigidemasi.camelkit.ship.worker.ShipWorkspace;
import io.github.luigidemasi.camelkit.ship.worker.ShipWorkspace.StaleBaselineException;
import io.github.luigidemasi.camelkit.ship.worker.ShipWorkspace.Verification;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Compact local lifecycle service for Ship runs. */
public final class ShipController {

    private static final int MAX_PROJECT_STATE_BYTES = 1024 * 1024;
    private static final int MAX_ARTIFACT_BYTES = 64 * 1024 * 1024;
    private static final long MAX_STAGE_ARTIFACT_BYTES
            = ShipTreePolicy.DEFAULT_MAX_AGGREGATE_BYTES;
    private static final String ABORT_MESSAGE = "Run aborted by user";
    private static final ObjectMapper PROJECT_JSON = new ObjectMapper(
            JsonFactory.builder()
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private final ShipRunStore store;
    private final Clock clock;
    private final Map<String, String> environment;

    public ShipController(Path stateRoot) {
        this(stateRoot, Clock.systemUTC(), System.getenv());
    }

    public ShipController(Path stateRoot, Map<String, String> environment) {
        this(stateRoot, Clock.systemUTC(), environment);
    }

    ShipController(Path stateRoot, Clock clock) {
        this(stateRoot, clock, System.getenv());
    }

    ShipController(
                   Path stateRoot, Clock clock, Map<String, String> environment) {
        this.store = new ShipRunStore(Objects.requireNonNull(stateRoot, "state root"));
        this.clock = Objects.requireNonNull(clock, "clock");
        this.environment = Map.copyOf(
                Objects.requireNonNull(environment, "environment"));
    }

    /** Resolves the conventional user-owned Ship state directory. */
    public static Path defaultStateRoot() {
        String configured = System.getenv("CAMEL_KIT_SHIP_STATE_HOME");
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured).toAbsolutePath().normalize();
        }
        String xdgState = System.getenv("XDG_STATE_HOME");
        if (xdgState != null && !xdgState.isBlank()) {
            return Path.of(xdgState).resolve("camel-kit/ship").toAbsolutePath().normalize();
        }
        String userHome = System.getProperty("user.home");
        if (userHome == null || userHome.isBlank()) {
            throw new IllegalStateException("Cannot resolve the user state directory");
        }
        return Path.of(userHome)
                .resolve(".local/state/camel-kit/ship")
                .toAbsolutePath()
                .normalize();
    }

    public ShipRun start(
            Path projectDirectory, Oversight oversight, List<? extends Input> inputs) {
        String runId = newRunId();
        Path project = requireProject(projectDirectory);
        String pipelineId = inspectProjectState(project);
        ShipContext context = resolveContext(project, inputs);
        rejectContextSecrets(context);
        List<StageRecord> stages = new ArrayList<>(ShipRun.pendingStages());
        stages.set(
                Stage.DISCOVERY.ordinal(),
                startStage(
                        stages.get(Stage.DISCOVERY.ordinal()),
                        ShipRun.inputDigest(context, stages, Stage.DISCOVERY)));
        ShipRun run = newRun(
                runId,
                project,
                pipelineId,
                oversight,
                Stage.DISCOVERY,
                context,
                stages);
        create(run);
        return run;
    }

    public ShipRun startFrom(
            Path projectDirectory,
            Stage target,
            Oversight oversight,
            List<? extends Input> inputs) {
        String runId = newRunId();
        Path project = requireProject(projectDirectory);
        Stage startStage = Objects.requireNonNull(target, "start stage");
        if (startStage == Stage.EXECUTE || startStage == Stage.VALIDATE) {
            throw failure(
                    "start-from-stage-unsupported",
                    "Starting from EXECUTE or VALIDATE requires controller-owned "
                                                    + "generated PLAN/Pi evidence; start from PLAN instead");
        }
        String pipelineId = inspectProjectState(project);
        ShipContext context = resolveContext(project, inputs);
        rejectContextSecrets(context);
        if (startStage == Stage.DESIGN && context.sources().isEmpty()) {
            throw failure(
                    "start-from-context-missing",
                    "Starting from DESIGN requires text or document context");
        }
        if (startStage == Stage.DESIGN && pipelineId == null) {
            throw failure(
                    "start-from-pipeline-missing",
                    "Starting from DESIGN requires a controller-bound active pipeline");
        }
        List<StageRecord> stages = new ArrayList<>(ShipRun.pendingStages());

        for (Stage stage : Stage.values()) {
            if (stage.ordinal() >= startStage.ordinal()) {
                break;
            }
            String inputDigest = ShipRun.inputDigest(context, stages, stage);
            List<ArtifactRef> artifacts = importedArtifacts(project, pipelineId, stage);
            String outputDigest = artifacts.isEmpty()
                    ? context.digest()
                    : artifacts.get(0).digest();
            stages.set(
                    stage.ordinal(),
                    stages.get(stage.ordinal()).imported(inputDigest, outputDigest, artifacts));
        }

        stages.set(
                startStage.ordinal(),
                startStage(
                        stages.get(startStage.ordinal()),
                        ShipRun.inputDigest(context, stages, startStage)));
        ShipRun run = newRun(
                runId,
                project,
                pipelineId,
                oversight,
                startStage,
                context,
                stages);
        create(run);
        return run;
    }

    /** Reads one atomic state snapshot without taking the mutation lock. */
    public ShipRun status(String runId) {
        try {
            return store.read(runId);
        } catch (ShipRunStore.StoreException e) {
            throw failure(e.code(), e.getMessage(), e);
        } catch (IOException e) {
            throw failure("state-read-failed", "Could not read Ship run " + runId, e);
        }
    }

    /**
     * Re-reads mutable inputs and restarts the earliest stale or incomplete stage with a fresh attempt.
     */
    public ShipRun resume(String runId) {
        return resume(runId, List.of());
    }

    /**
     * Appends context to a paused run, then applies the same stale-input restart used by ordinary resume.
     */
    public ShipRun resume(String runId, List<? extends Input> additions) {
        List<Input> added = new ArrayList<>(Objects.requireNonNull(additions, "context additions"));
        try (ShipRunStore.LockedRun locked = store.lock(runId)) {
            ShipRun current = locked.read();
            if (current.status() == RunStatus.ABORTED) {
                throw failure("run-aborted", "Ship run is aborted and cannot resume: " + runId);
            }
            if (!added.isEmpty() && current.status() != RunStatus.PAUSED) {
                throw failure(
                        "run-not-paused",
                        "Ship context can only be added while a run is paused: " + runId);
            }
            boolean completedWithLocalStamp = current.status() == RunStatus.COMPLETED;
            if (completedWithLocalStamp) {
                StageRecord validation = current.stage(Stage.VALIDATE);
                if (validation.attempts() <= 0
                        || !isLocalStampEvidence(locked.directory(), validation)) {
                    throw failure(
                            "run-completed", "Ship run is already complete: " + runId);
                }
            }

            Path project = requireProject(Path.of(current.projectDirectory()));
            try (ShipRunStore.LockedProject projectLock = store.lockProject(project)) {
                projectLock.requireNoForeignTornPublication(current.id());
                recoverTornPublication(current, project, locked.directory());
            }
            String publishedIdentity = publishedCandidateIdentity(
                    current, locked.directory());
            ShipContext refreshed = resumeContext(project, current.context(), added);
            rejectContextSecrets(refreshed);
            List<StageRecord> stages = new ArrayList<>(current.stages());
            int restart = stages.size();

            for (int index = 0; index < stages.size(); index++) {
                StageRecord record = stages.get(index);
                if (record.status() != StageStatus.COMPLETED) {
                    continue;
                }
                boolean stagedExecute = record.stage() == Stage.EXECUTE
                        && record.attempts() > 0;
                List<ArtifactRef> refreshedArtifacts;
                try {
                    if (stagedExecute) {
                        requireExecuteRoot(
                                record.artifacts(), expectedWorkspace(locked.directory()));
                    }
                    if (record.stage() == Stage.VALIDATE
                            && record.attempts() > 0
                            && isLocalStampEvidence(
                                    locked.directory(), record)) {
                        Path evidence = validationEvidenceDirectory(
                                locked.directory(), record.attempts());
                        VerifiedStamp stamp = ShipLocalStampStore.readVerified(
                                evidence, current.id());
                        refreshedArtifacts = List.of(new ArtifactRef(
                                stamp.path().toString(), stamp.digest()));
                    } else {
                        refreshedArtifacts = readRecordedArtifacts(
                                project,
                                current.id(),
                                record.stage(),
                                record.attempts(),
                                record.inputDigest(),
                                locked.directory(),
                                record.artifacts(),
                                stagedExecute,
                                publishedIdentity);
                    }
                } catch (Failure | IOException e) {
                    restart = Math.min(restart, index);
                    break;
                }
                if (!refreshedArtifacts.equals(record.artifacts())) {
                    String refreshedOutput = record.outputDigest();
                    if (record.attempts() == 0 && record.stage() != Stage.DISCOVERY) {
                        refreshedOutput = refreshedArtifacts.get(0).digest();
                    } else if (stagedExecute) {
                        refreshedOutput = ShipRun.executeOutputDigest(requireExecuteRoot(
                                refreshedArtifacts, expectedWorkspace(locked.directory())));
                    }
                    stages.set(
                            index,
                            record.withArtifacts(refreshedOutput, refreshedArtifacts));
                    if (record.stage().next() == null) {
                        restart = Math.min(restart, index);
                    }
                }
            }

            for (int index = 0; index < stages.size(); index++) {
                StageRecord record = stages.get(index);
                if (record.status() != StageStatus.COMPLETED) {
                    restart = Math.min(restart, index);
                    break;
                }
                if (index < restart) {
                    String expected = ShipRun.inputDigest(refreshed, stages, record.stage());
                    if (!expected.equals(record.inputDigest())) {
                        restart = index;
                        break;
                    }
                }
            }

            ShipRun resumed;
            if (restart == stages.size()) {
                if (completedWithLocalStamp) {
                    throw failure(
                            "run-completed", "Ship run is already complete: " + runId);
                }
                // Every stage is verified complete; guarded publication is the remaining step.
                resumed = copy(
                        current,
                        current.pipelineId(),
                        RunStatus.RUNNING,
                        Stage.VALIDATE,
                        refreshed,
                        stages,
                        null,
                        current.publication());
            } else {
                for (int index = restart; index < stages.size(); index++) {
                    stages.set(index, stages.get(index).reset());
                }
                Stage stage = Stage.values()[restart];
                stages.set(
                        restart,
                        startStage(
                                stages.get(restart),
                                ShipRun.inputDigest(refreshed, stages, stage)));
                // A restarted EXECUTE produces a new candidate, so the published claim is void.
                ArtifactRef publication = restart <= Stage.EXECUTE.ordinal()
                        ? null
                        : current.publication();
                if (publication == null && current.publication() != null) {
                    discardPublicationScratch(current.id(), locked.directory());
                }
                resumed = copy(
                        current,
                        current.pipelineId(),
                        RunStatus.RUNNING,
                        stage,
                        refreshed,
                        stages,
                        null,
                        publication);
            }
            locked.write(resumed);
            return resumed;
        } catch (ShipRunStore.StoreException e) {
            throw failure(e.code(), e.getMessage(), e);
        } catch (IOException e) {
            throw failure("state-write-failed", "Could not resume Ship run " + runId, e);
        }
    }

    /**
     * Restarts a completed generated predecessor whose controller-consumed Pi result is no longer recoverable.
     */
    ShipRun restartGeneratedStage(
            StageAttempt currentAttempt, StageRecord unavailable) {
        Objects.requireNonNull(currentAttempt, "current attempt");
        Objects.requireNonNull(unavailable, "unavailable stage");
        if (unavailable.stage() == Stage.VALIDATE
                || unavailable.attempts() <= 0) {
            throw failure(
                    "stage-result-invalid",
                    "Only a generated Pi predecessor can be restarted");
        }
        String runId = currentAttempt.run().id();
        try (ShipRunStore.LockedRun locked = store.lock(runId)) {
            ShipRun current = locked.read();
            StageRecord active = requireActiveAttempt(
                    current,
                    currentAttempt.stage().stage(),
                    currentAttempt.stage().attempts(),
                    currentAttempt.stage().inputDigest());
            requireCurrentInputs(current, active, locked.directory());
            StageRecord recorded = current.stage(unavailable.stage());
            if (recorded.stage().ordinal() >= active.stage().ordinal()
                    || recorded.status() != StageStatus.COMPLETED
                    || recorded.attempts() != unavailable.attempts()
                    || !recorded.inputDigest().equals(
                            unavailable.inputDigest())) {
                throw failure(
                        "stale-stage-attempt",
                        "Unavailable Pi result no longer matches the completed predecessor");
            }

            List<StageRecord> stages = new ArrayList<>(current.stages());
            int restart = recorded.stage().ordinal();
            for (int index = restart; index < stages.size(); index++) {
                stages.set(index, stages.get(index).reset());
            }
            stages.set(
                    restart,
                    startStage(
                            stages.get(restart),
                            ShipRun.inputDigest(
                                    current.context(),
                                    stages,
                                    recorded.stage())));
            // Restarting a generated predecessor always resets EXECUTE, so the published claim is void.
            if (current.publication() != null) {
                discardPublicationScratch(current.id(), locked.directory());
            }
            ShipRun resumed = copy(
                    current,
                    current.pipelineId(),
                    RunStatus.RUNNING,
                    recorded.stage(),
                    current.context(),
                    stages,
                    null,
                    null);
            locked.write(resumed);
            return resumed;
        } catch (ShipRunStore.StoreException e) {
            throw failure(e.code(), e.getMessage(), e);
        } catch (IOException e) {
            throw failure(
                    "state-write-failed",
                    "Could not restart Ship stage " + unavailable.stage()
                                          + " for " + runId,
                    e);
        }
    }

    public ShipRun abort(String runId) {
        try (ShipRunStore.LockedRun locked = store.lock(runId)) {
            ShipRun current = locked.read();
            if (current.status() == RunStatus.ABORTED) {
                throw failure("run-aborted", "Ship run is already aborted: " + runId);
            }
            if (current.status() == RunStatus.COMPLETED) {
                throw failure("run-completed", "Completed Ship run cannot be aborted: " + runId);
            }
            // Aborting must stay possible when the project is gone; only a torn uncommitted
            // publication needs the live project resolved for its rollback.
            if (current.publication() == null
                    && ShipPublicationService.journalExists(locked.directory())) {
                Path project = requireProject(Path.of(current.projectDirectory()));
                try (ShipRunStore.LockedProject projectLock = store.lockProject(project)) {
                    projectLock.requireNoForeignTornPublication(current.id());
                    recoverTornPublication(current, project, locked.directory());
                }
            }

            List<StageRecord> stages = new ArrayList<>(current.stages());
            boolean allCompleted = stages.stream()
                    .allMatch(stage -> stage.status() == StageStatus.COMPLETED);
            if (!allCompleted) {
                int active = current.currentStage().ordinal();
                stages.set(active, stages.get(active).abort());
            }
            ShipRun aborted = copy(
                    current,
                    RunStatus.ABORTED,
                    current.currentStage(),
                    current.context(),
                    stages,
                    ABORT_MESSAGE);
            locked.write(aborted);
            return aborted;
        } catch (ShipRunStore.StoreException e) {
            throw failure(e.code(), e.getMessage(), e);
        } catch (IOException e) {
            throw failure("state-write-failed", "Could not abort Ship run " + runId, e);
        }
    }

    /**
     * Publishes the validated workspace candidate into the live project under the oversight and validation gates, and
     * commits the run COMPLETED in the same locked operation.
     *
     * <p>
     * The per-run and project mutation locks are held for the whole guarded apply, so other runs, aborts, and resumes
     * observe {@code operation-in-progress} instead of racing the file writes; a torn publication left by process death
     * is rolled back from its write-ahead journal by the next locked operation.
     */
    ShipRun publish(String runId) {
        try (ShipRunStore.LockedRun locked = store.lock(runId)) {
            ShipRun current = locked.read();
            if (!current.publicationPending()) {
                throw failure(
                        "stale-stage-attempt",
                        "Ship publication requires a validated run awaiting publication");
            }
            Path project = requireProject(Path.of(current.projectDirectory()));
            try (ShipRunStore.LockedProject projectLock = store.lockProject(project)) {
                projectLock.requireNoForeignTornPublication(current.id());
                Path runDirectory = locked.directory();
                recoverTornPublication(current, project, runDirectory);
                ShipContext refreshed = refreshContext(current.context());
                if (!refreshed.equals(current.context())) {
                    throw failure(
                            "stale-stage-input", "Ship stage context changed; resume the run");
                }
                WorkspaceEvidence evidence = verifyCompletedStages(current, project, runDirectory);

                StageRecord execute = current.stage(Stage.EXECUTE);
                final ShipPublicationService.Journal journal;
                try {
                    journal = ShipPublicationService.plan(
                            current.id(),
                            execute.attempts(),
                            now(),
                            new Verification(evidence.baseline(), evidence.snapshot()));
                    ShipPublicationService.begin(runDirectory, journal);
                    ShipPublicationService.apply(
                            project, evidence.candidate(), runDirectory, journal);
                } catch (ShipPublicationService.StaleLiveTreeException e) {
                    throw failure(
                            "stale-stage-input",
                            "Live project changed before Ship publication; resume the run",
                            e);
                } catch (IOException e) {
                    // A candidate this protocol cannot publish must fail the run once; retrying it
                    // would loop forever because nothing about the run or the project is stale.
                    return commitPublicationFailure(locked, current, e);
                }
                final ArtifactRef record;
                try {
                    record = ShipPublicationService.commitRecord(runDirectory, journal, now());
                } catch (IOException e) {
                    try {
                        ShipPublicationService.rollbackApplied(project, runDirectory, journal);
                    } catch (IOException rollbackFailure) {
                        e.addSuppressed(rollbackFailure);
                    }
                    return commitPublicationFailure(locked, current, e);
                }
                ShipRun published = copy(
                        current,
                        current.pipelineId(),
                        RunStatus.COMPLETED,
                        Stage.VALIDATE,
                        current.context(),
                        current.stages(),
                        null,
                        record);
                locked.write(published);
                return published;
            }
        } catch (ShipRunStore.StoreException e) {
            throw failure(e.code(), e.getMessage(), e);
        } catch (IOException e) {
            throw failure(
                    "publication-failed", "Could not publish Ship run " + runId, e);
        }
    }

    /** Verifies every completed stage exactly as recorded before the guarded apply. */
    private WorkspaceEvidence verifyCompletedStages(
            ShipRun run, Path project, Path runDirectory) {
        WorkspaceEvidence evidence = null;
        for (StageRecord record : run.stages()) {
            try {
                if (record.stage() == Stage.VALIDATE) {
                    if (!isLocalStampEvidence(runDirectory, record)) {
                        throw failure(
                                "stale-stage-input",
                                "Ship validation evidence is not the controller's local Stamp");
                    }
                    Path evidenceDirectory = validationEvidenceDirectory(
                            runDirectory, record.attempts());
                    VerifiedStamp stamp = ShipLocalStampStore.readVerified(
                            evidenceDirectory, run.id());
                    if (!stamp.digest().equals(record.outputDigest())) {
                        throw failure(
                                "stale-stage-input",
                                "Ship local Stamp changed; resume the run");
                    }
                } else if (record.stage() == Stage.EXECUTE && record.attempts() > 0) {
                    ArtifactRef root = requireExecuteRoot(
                            record.artifacts(), expectedWorkspace(runDirectory));
                    evidence = verifyWorkspace(
                            project,
                            Path.of(root.path()),
                            run.id(),
                            record.attempts(),
                            record.inputDigest(),
                            runDirectory,
                            null);
                    if (!workspaceArtifacts(
                            evidence,
                            record.artifacts().stream()
                                    .map(artifact -> Path.of(artifact.path()))
                                    .toList())
                            .equals(record.artifacts())) {
                        throw failure(
                                "stale-stage-input",
                                "A Ship stage artifact changed; resume the run");
                    }
                } else if (!readRecordedArtifacts(
                        project,
                        run.id(),
                        record.stage(),
                        record.attempts(),
                        record.inputDigest(),
                        runDirectory,
                        record.artifacts(),
                        false,
                        null)
                        .equals(record.artifacts())) {
                    throw failure(
                            "stale-stage-input",
                            "A Ship stage artifact changed; resume the run");
                }
            } catch (Failure e) {
                if ("stale-stage-input".equals(e.code())) {
                    throw e;
                }
                throw failure(
                        "stale-stage-input",
                        "A Ship stage artifact changed or became unavailable; resume the run",
                        e);
            } catch (IOException | SecurityException e) {
                throw failure(
                        "stale-stage-input",
                        "A Ship stage artifact changed or became unavailable; resume the run",
                        e);
            }
        }
        if (evidence == null) {
            throw failure(
                    "state-invalid", "Ship publication requires a staged EXECUTE candidate");
        }
        return evidence;
    }

    /** Rolls back an uncommitted torn publication before any other locked mutation proceeds. */
    private static void recoverTornPublication(
            ShipRun current, Path project, Path runDirectory) {
        if (current.publication() != null
                || !ShipPublicationService.journalExists(runDirectory)) {
            return;
        }
        try {
            ShipPublicationService.recover(
                    project,
                    runDirectory,
                    current.id(),
                    current.stage(Stage.EXECUTE).attempts());
        } catch (ShipPublicationService.RecoveryBlockedException e) {
            throw failure(
                    "publication-recovery-blocked",
                    "Ship publication recovery stopped: the live project no longer matches the "
                                                    + "journalled baseline or candidate; resolve it manually and retry",
                    e);
        } catch (IOException e) {
            throw failure(
                    "publication-failed",
                    "Could not recover the torn Ship publication for " + current.id(),
                    e);
        }
    }

    /**
     * Drops the retained publication scratch of a run that is voiding its published claim.
     *
     * <p>
     * Once the claim is gone, a retained journal would otherwise read as a torn write-ahead log to the next locked
     * operation, which would roll a committed publication out of the live project.
     */
    private static void discardPublicationScratch(String runId, Path runDirectory) {
        try {
            ShipPublicationService.discard(runDirectory);
        } catch (IOException e) {
            throw failure(
                    "publication-failed",
                    "Could not discard the superseded Ship publication record for " + runId,
                    e);
        }
    }

    /** Reads the published candidate identity accepted as a legitimate live baseline. */
    private static String publishedCandidateIdentity(
            ShipRun current, Path runDirectory) {
        if (current.publication() == null) {
            return null;
        }
        try {
            return ShipPublicationService.readVerifiedRecord(
                    runDirectory, current.publication(), current.id())
                    .candidateIdentity();
        } catch (IOException e) {
            throw failure(
                    "state-corrupt",
                    "Ship publication record is invalid for " + current.id(),
                    e);
        }
    }

    /**
     * Records why publication failed. A rollback that failed too leaves a partly published project, so the message must
     * say so rather than claim the live project was restored.
     */
    private ShipRun commitPublicationFailure(
            ShipRunStore.LockedRun locked, ShipRun current, IOException cause)
            throws IOException {
        // A retained journal is exactly the evidence that rollback did not finish.
        String outcome = ShipPublicationService.journalExists(locked.directory())
                ? "Ship publication failed and its rollback did not finish, so the project may be "
                  + "partly published; resume to retry recovery"
                : "Ship publication failed and the live project is unchanged; resume to retry";
        ShipRun failed = copy(
                current,
                RunStatus.FAILED,
                Stage.VALIDATE,
                current.context(),
                current.stages(),
                publicationMessage(outcome, cause));
        locked.write(failed);
        return failed;
    }

    /** Appends the concrete cause, stripped of control characters and bounded to the run-message limit. */
    private static String publicationMessage(String outcome, IOException cause) {
        String detail = cause.getMessage();
        if (detail == null || detail.isBlank()) {
            return outcome;
        }
        String combined = outcome + ": " + detail.replaceAll("\\p{Cntrl}", " ").trim();
        return combined.length() > ShipRun.MAX_MESSAGE_LENGTH
                ? combined.substring(0, ShipRun.MAX_MESSAGE_LENGTH)
                : combined;
    }

    private static String pauseMessage(Stage stage, String report) {
        String message = report == null
                ? ""
                : report.replaceAll("[\\p{Cntrl}&&[^\\n]]", " ").strip();
        if (message.isBlank()) {
            message = "Approval required after " + stage;
        }
        if (message.length() <= ShipRun.MAX_MESSAGE_LENGTH) {
            return message;
        }
        int start = message.length() - (ShipRun.MAX_MESSAGE_LENGTH - 4);
        if (Character.isLowSurrogate(message.charAt(start))
                && Character.isHighSurrogate(message.charAt(start - 1))) {
            start++;
        }
        return "...\n" + message.substring(start);
    }

    /**
     * Records a worker result only when it matches the currently running stage attempt.
     */
    ShipRun completeStage(
            String runId,
            Stage stage,
            int attempt,
            String inputDigest,
            String outputDigest,
            List<Path> artifacts,
            boolean materialAmbiguity,
            String report) {
        if (stage == Stage.EXECUTE) {
            throw failure(
                    "workspace-required",
                    "Ship EXECUTE results require a workspace bound to the active stage");
        }
        if (stage == Stage.VALIDATE) {
            throw failure(
                    "validation-controller-owned",
                    "Ship VALIDATE results require a controller-derived local Stamp");
        }
        return completeStage(
                runId,
                stage,
                attempt,
                inputDigest,
                outputDigest,
                artifacts,
                false,
                materialAmbiguity,
                report,
                null);
    }

    /** Records a typed DISCOVERY result and binds its controller-approved pipeline ID. */
    ShipRun completeDiscoveryStage(
            String runId,
            int attempt,
            String inputDigest,
            String outputDigest,
            String pipelineId,
            boolean materialAmbiguity,
            String report) {
        if (!ShipRun.isPipelineId(pipelineId)) {
            throw failure(
                    "stage-result-invalid",
                    "Ship discovery result has an invalid pipeline ID");
        }
        return completeStage(
                runId,
                Stage.DISCOVERY,
                attempt,
                inputDigest,
                outputDigest,
                List.of(),
                false,
                materialAmbiguity,
                report,
                pipelineId);
    }

    /**
     * Records an EXECUTE result from the controller-owned workspace. The workspace root is always retained;
     * {@code artifacts} contains only additional material files.
     */
    ShipRun completeExecuteStage(
            String runId,
            int attempt,
            String inputDigest,
            List<Path> artifacts,
            boolean materialAmbiguity,
            String report) {
        return completeStage(
                runId,
                Stage.EXECUTE,
                attempt,
                inputDigest,
                null,
                artifacts,
                true,
                materialAmbiguity,
                report,
                null);
    }

    /**
     * Resolves one authoritative stage attempt and prepares its private worker directories.
     *
     * <p>
     * The run lock is released before worker execution.
     */
    StageAttempt prepareAttempt(String runId) {
        try (ShipRunStore.LockedRun locked = store.lock(runId)) {
            ShipRun current = locked.read();
            StageRecord active = requireActiveAttempt(
                    current,
                    current.currentStage(),
                    current.stage(current.currentStage()).attempts(),
                    current.stage(current.currentStage()).inputDigest());
            requireCurrentInputs(current, active, locked.directory());
            Path runDirectory = locked.directory().toRealPath();
            Path sessions = privateDirectory(runDirectory, "sessions");
            Path inputs = privateDirectory(runDirectory, "inputs");
            Path evidenceRoot = privateDirectory(runDirectory, "evidence");
            Path evidence = privateDirectory(
                    evidenceRoot,
                    active.stage().name().toLowerCase(Locale.ROOT)
                                  + "-" + active.attempts());
            Path workingDirectory = active.stage() == Stage.EXECUTE
                    ? ShipWorkspace.prepare(
                            Path.of(current.projectDirectory()),
                            runDirectory,
                            current.id(),
                            active.attempts(),
                            active.inputDigest())
                    : Path.of(current.projectDirectory());
            return new StageAttempt(
                    current,
                    workingDirectory,
                    sessions,
                    evidence,
                    inputs,
                    runDirectory);
        } catch (ShipRunStore.StoreException e) {
            throw failure(e.code(), e.getMessage(), e);
        } catch (StaleBaselineException e) {
            throw failure(
                    "stale-stage-input",
                    "Ship workspace baseline changed; resume the run",
                    e);
        } catch (IOException | SecurityException | UnsupportedOperationException e) {
            throw failure(
                    "worker-preparation-failed",
                    "Could not prepare the Ship worker attempt for " + runId,
                    e);
        }
    }

    private ShipRun completeStage(
            String runId,
            Stage stage,
            int attempt,
            String inputDigest,
            String outputDigest,
            List<Path> artifacts,
            boolean execute,
            boolean materialAmbiguity,
            String report,
            String discoveredPipelineId) {
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(artifacts, "artifacts");
        if (execute != (stage == Stage.EXECUTE)) {
            throw new IllegalStateException("Ship EXECUTE completion mode does not match its stage");
        }
        if (!execute && !ShipDigest.isSha256(outputDigest)) {
            throw failure("stage-result-invalid", "Ship stage output digest is invalid");
        }
        try (ShipRunStore.LockedRun locked = store.lock(runId)) {
            ShipRun current = locked.read();
            StageRecord active = requireActiveAttempt(current, stage, attempt, inputDigest);
            if (discoveredPipelineId != null
                    && (stage != Stage.DISCOVERY
                            || current.pipelineId() != null
                                    && !current.pipelineId().equals(discoveredPipelineId))) {
                throw failure(
                        "stage-result-invalid",
                        "Ship discovery result conflicts with the active pipeline");
            }
            requireCurrentInputs(current, active, locked.directory());
            Path project = Path.of(current.projectDirectory());
            Path artifactRoot;
            List<Path> normalizedArtifacts;
            WorkspaceEvidence workspaceEvidence = null;
            if (execute) {
                Path expected = expectedWorkspace(locked.directory());
                normalizedArtifacts = normalizeArtifactPaths(expected, artifacts);
                requireExecuteArtifactPaths(normalizedArtifacts, expected);
                workspaceEvidence = verifyWorkspace(
                        project,
                        expected,
                        current.id(),
                        active.attempts(),
                        active.inputDigest(),
                        locked.directory(),
                        null);
                rejectChangedWorkspaceSecrets(workspaceEvidence);
                artifactRoot = workspaceEvidence.candidate();
            } else {
                normalizedArtifacts = normalizeArtifactPaths(project, artifacts);
                artifactRoot = project;
            }
            String acceptedOutputDigest = outputDigest;
            List<ArtifactRef> references;
            if (execute) {
                ArtifactRef root = workspaceArtifact(workspaceEvidence, artifactRoot);
                references = java.util.stream.Stream
                        .concat(
                                java.util.stream.Stream.of(root),
                                workspaceArtifacts(workspaceEvidence, normalizedArtifacts).stream())
                        .sorted(Comparator.comparing(ArtifactRef::path))
                        .toList();
                acceptedOutputDigest = ShipRun.executeOutputDigest(root);
            } else {
                requireAggregateArtifactSize(normalizedArtifacts);
                ArtifactReadBudget budget = new ArtifactReadBudget();
                references = new ArrayList<>(normalizedArtifacts.size());
                for (Path path : normalizedArtifacts) {
                    references.add(readArtifact(artifactRoot, path, budget));
                }
            }
            List<StageRecord> stages = new ArrayList<>(current.stages());
            stages.set(stage.ordinal(), active.complete(acceptedOutputDigest, references));

            Stage next = stage.next();
            RunStatus status;
            Stage currentStage;
            String message = null;
            if (next == null) {
                // VALIDATE completes through completeValidationStage, which owns the publication gate.
                throw new IllegalStateException(
                        "Ship final-stage completion is owned by the validation path");
            } else if (current.oversight().pausesAfter(stage, materialAmbiguity)) {
                status = RunStatus.PAUSED;
                currentStage = next;
                message = pauseMessage(stage, report);
            } else {
                stages.set(
                        next.ordinal(),
                        startStage(
                                stages.get(next.ordinal()),
                                ShipRun.inputDigest(current.context(), stages, next)));
                status = RunStatus.RUNNING;
                currentStage = next;
            }

            ShipRun completed = copy(
                    current,
                    discoveredPipelineId == null
                            ? current.pipelineId()
                            : discoveredPipelineId,
                    status,
                    currentStage,
                    current.context(),
                    stages,
                    message);
            locked.write(completed);
            return completed;
        } catch (ShipRunStore.StoreException e) {
            throw failure(e.code(), e.getMessage(), e);
        } catch (IOException e) {
            throw failure("state-write-failed", "Could not complete Ship stage for " + runId, e);
        }
    }

    /** Records a retryable worker failure for the current stage attempt. */
    public ShipRun failStage(
            String runId, Stage stage, int attempt, String inputDigest, String message) {
        Objects.requireNonNull(stage, "stage");
        if (message == null || message.isBlank()
                || message.length() > ShipRun.MAX_MESSAGE_LENGTH
                || message.indexOf('\0') >= 0) {
            throw failure("stage-result-invalid", "Ship stage failure message is invalid");
        }
        try (ShipRunStore.LockedRun locked = store.lock(runId)) {
            ShipRun current = locked.read();
            StageRecord active = requireActiveAttempt(current, stage, attempt, inputDigest);
            requireCurrentInputs(current, active, locked.directory());
            List<StageRecord> stages = new ArrayList<>(current.stages());
            stages.set(stage.ordinal(), active.fail());
            ShipRun failed = copy(
                    current,
                    RunStatus.FAILED,
                    stage,
                    current.context(),
                    stages,
                    message);
            locked.write(failed);
            return failed;
        } catch (ShipRunStore.StoreException e) {
            throw failure(e.code(), e.getMessage(), e);
        } catch (IOException e) {
            throw failure("state-write-failed", "Could not fail Ship stage for " + runId, e);
        }
    }

    /** Commits a controller-derived local Stamp and lets its status decide validation. */
    ShipRun completeValidationStage(
            String runId,
            int attempt,
            String inputDigest,
            ShipLocalStamp suppliedStamp) {
        try (ShipRunStore.LockedRun locked = store.lock(runId)) {
            ShipRun current = locked.read();
            StageRecord active = requireActiveAttempt(
                    current, Stage.VALIDATE, attempt, inputDigest);
            requireCurrentInputs(current, active, locked.directory());
            Path evidenceDirectory = validationEvidenceDirectory(
                    locked.directory(), attempt);
            VerifiedStamp verified = ShipLocalStampStore.readVerified(
                    evidenceDirectory, runId);
            ShipLocalStamp stamp = verified.stamp();
            if (!stamp.equals(Objects.requireNonNull(suppliedStamp, "local Stamp"))) {
                throw failure(
                        "validation-stamp-invalid",
                        "Persisted local Stamp differs from the validation result");
            }
            ArtifactRef reference = new ArtifactRef(
                    verified.path().toString(), verified.digest());
            List<StageRecord> stages = new ArrayList<>(current.stages());
            RunStatus status;
            String message = null;
            if (stamp.status() == ShipLocalStamp.Status.FAIL) {
                stages.set(
                        Stage.VALIDATE.ordinal(),
                        active.fail(reference.digest(), List.of(reference)));
                status = RunStatus.FAILED;
                message = "Mandatory validation checks failed; inspect the retained local Stamp";
            } else {
                stages.set(
                        Stage.VALIDATE.ordinal(),
                        active.complete(reference.digest(), List.of(reference)));
                if (current.publication() != null) {
                    // A re-validated run keeps its published candidate; nothing is left to gate.
                    status = RunStatus.COMPLETED;
                } else {
                    // A waiver is decision-relevant information the oversight gate has not seen.
                    boolean waived = stamp.status() == ShipLocalStamp.Status.COMPLETED_WITH_WAIVER;
                    status = current.oversight().pausesAfter(Stage.VALIDATE, waived)
                            ? RunStatus.PAUSED
                            : RunStatus.RUNNING;
                    if (status == RunStatus.PAUSED) {
                        message = waived
                                ? "Validation completed with a waiver; approval is required before publication"
                                : "Validation passed; approval is required before publication";
                    }
                }
            }
            ShipRun completed = copy(
                    current,
                    status,
                    Stage.VALIDATE,
                    current.context(),
                    stages,
                    message);
            locked.write(completed);
            return completed;
        } catch (ShipRunStore.StoreException e) {
            throw failure(e.code(), e.getMessage(), e);
        } catch (IOException | SecurityException e) {
            throw failure(
                    "validation-stamp-invalid",
                    "Could not verify the local Stamp for " + runId,
                    e);
        }
    }

    private ShipRun newRun(
            String runId,
            Path project,
            String pipelineId,
            Oversight oversight,
            Stage stage,
            ShipContext context,
            List<StageRecord> stages) {
        String timestamp = now();
        return new ShipRun(
                ShipRun.SCHEMA_VERSION,
                runId,
                project.toString(),
                pipelineId,
                Objects.requireNonNull(oversight, "oversight"),
                RunStatus.RUNNING,
                stage,
                context,
                stages,
                null,
                timestamp,
                timestamp,
                null);
    }

    private void create(ShipRun run) {
        try {
            store.create(run);
        } catch (ShipRunStore.StoreException e) {
            throw failure(e.code(), e.getMessage(), e);
        } catch (IOException e) {
            throw failure("state-write-failed", "Could not create Ship run " + run.id(), e);
        }
    }

    private ShipRun copy(
            ShipRun run,
            RunStatus status,
            Stage currentStage,
            ShipContext context,
            List<StageRecord> stages,
            String message) {
        return copy(
                run,
                run.pipelineId(),
                status,
                currentStage,
                context,
                stages,
                message);
    }

    private ShipRun copy(
            ShipRun run,
            String pipelineId,
            RunStatus status,
            Stage currentStage,
            ShipContext context,
            List<StageRecord> stages,
            String message) {
        return copy(run, pipelineId, status, currentStage, context, stages, message, run.publication());
    }

    private ShipRun copy(
            ShipRun run,
            String pipelineId,
            RunStatus status,
            Stage currentStage,
            ShipContext context,
            List<StageRecord> stages,
            String message,
            ArtifactRef publication) {
        return new ShipRun(
                run.schemaVersion(),
                run.id(),
                run.projectDirectory(),
                pipelineId,
                run.oversight(),
                status,
                currentStage,
                context,
                stages,
                publication,
                run.createdAt(),
                mutationTime(run),
                message);
    }

    private static StageRecord requireActiveAttempt(
            ShipRun run, Stage stage, int attempt, String inputDigest) {
        StageRecord record = run.stage(stage);
        if (run.status() != RunStatus.RUNNING
                || run.currentStage() != stage
                || record.status() != StageStatus.RUNNING
                || record.attempts() != attempt
                || !record.inputDigest().equals(inputDigest)) {
            throw failure(
                    "stale-stage-attempt",
                    "Ship stage result does not match the active attempt");
        }
        return record;
    }

    private static StageRecord startStage(StageRecord stage, String inputDigest) {
        if (stage.attempts() == Integer.MAX_VALUE - 1) {
            throw failure(
                    "state-corrupt",
                    "Ship stage attempt count cannot be advanced");
        }
        return stage.start(inputDigest);
    }

    private static void requireCurrentInputs(
            ShipRun run, StageRecord active, Path runDirectory) {
        Path project = requireProject(Path.of(run.projectDirectory()));
        ShipContext refreshed;
        try {
            refreshed = run.context().refresh();
        } catch (ShipContext.Failure e) {
            throw failure(
                    "stale-stage-input",
                    "Ship stage input changed or became unavailable; resume the run",
                    e);
        }
        if (!refreshed.equals(run.context())) {
            throw failure(
                    "stale-stage-input",
                    "Ship stage context changed; resume the run");
        }

        String publishedIdentity = publishedCandidateIdentity(run, runDirectory);
        for (int index = 0; index < active.stage().ordinal(); index++) {
            StageRecord predecessor = run.stages().get(index);
            try {
                boolean stagedExecute = predecessor.stage() == Stage.EXECUTE
                        && predecessor.attempts() > 0;
                if (stagedExecute) {
                    requireExecuteRoot(
                            predecessor.artifacts(), expectedWorkspace(runDirectory));
                }
                if (!readRecordedArtifacts(
                        project,
                        run.id(),
                        predecessor.stage(),
                        predecessor.attempts(),
                        predecessor.inputDigest(),
                        runDirectory,
                        predecessor.artifacts(),
                        stagedExecute,
                        publishedIdentity)
                        .equals(predecessor.artifacts())) {
                    throw failure(
                            "stale-stage-input",
                            "A Ship stage artifact changed; resume the run");
                }
            } catch (Failure e) {
                if ("stale-stage-input".equals(e.code())) {
                    throw e;
                }
                throw failure(
                        "stale-stage-input",
                        "A Ship stage artifact changed or became unavailable; resume the run",
                        e);
            }
        }
        String expected = ShipRun.inputDigest(refreshed, run.stages(), active.stage());
        if (!expected.equals(active.inputDigest())) {
            throw failure(
                    "stale-stage-input",
                    "Ship stage input digest is stale; resume the run");
        }
    }

    private static List<ArtifactRef> readRecordedArtifacts(
            Path project,
            String runId,
            Stage stage,
            int attempt,
            String inputDigest,
            Path runDirectory,
            List<ArtifactRef> artifacts,
            boolean stagedExecute,
            String publishedIdentity) {
        Path expected = expectedWorkspace(runDirectory);
        List<Path> paths = new ArrayList<>(artifacts.size());
        boolean workspaceRequired = stagedExecute;
        for (ArtifactRef artifact : artifacts) {
            Path normalized;
            try {
                normalized = Path.of(artifact.path()).toAbsolutePath().normalize();
            } catch (RuntimeException e) {
                throw failure("artifact-invalid", "Ship artifact path is invalid", e);
            }
            if (stagedExecute && !normalized.startsWith(expected)) {
                throw failure(
                        "artifact-invalid",
                        "Ship EXECUTE artifact is outside its workspace: " + normalized);
            }
            if (!stagedExecute && !normalized.startsWith(project)) {
                if (!normalized.startsWith(expected)) {
                    throw failure(
                            "artifact-invalid",
                            "Ship artifact is outside its project and workspace: " + normalized);
                }
                workspaceRequired = true;
            }
            paths.add(normalized);
        }
        if (stage == Stage.EXECUTE && attempt == 0) {
            throw failure(
                    "artifact-invalid",
                    "Imported Ship EXECUTE evidence is unsupported; start from PLAN instead");
        }
        WorkspaceEvidence workspace = workspaceRequired
                ? verifyWorkspace(
                        project, expected, runId, attempt, inputDigest, runDirectory,
                        publishedIdentity)
                : null;
        if (workspace != null) {
            return workspaceArtifacts(workspace, paths);
        }
        requireAggregateArtifactSize(paths);
        ArtifactReadBudget budget = new ArtifactReadBudget();
        List<ArtifactRef> refreshed = new ArrayList<>(paths.size());
        for (Path path : paths) {
            refreshed.add(readArtifact(project, path, budget));
        }
        return List.copyOf(refreshed);
    }

    private static WorkspaceEvidence verifyWorkspace(
            Path project,
            Path candidate,
            String runId,
            int attempt,
            String inputDigest,
            Path runDirectory,
            String publishedIdentity) {
        try {
            Path expected = expectedWorkspace(runDirectory);
            Verification verification = ShipWorkspace.verify(
                    project, candidate, runId, attempt, inputDigest, publishedIdentity);
            Path verified = Path.of(verification.candidate().root());
            if (!verified.equals(expected)) {
                throw new IOException(
                        "Ship workspace is outside the controller-owned run directory");
            }
            return new WorkspaceEvidence(
                    verified, verification.baseline(), verification.candidate());
        } catch (StaleBaselineException e) {
            throw failure(
                    "stale-stage-input",
                    "Ship workspace baseline changed; resume the run",
                    e);
        } catch (IOException | SecurityException e) {
            throw failure(
                    "artifact-invalid",
                    "Ship workspace does not match the active stage",
                    e);
        }
    }

    private void rejectChangedWorkspaceSecrets(WorkspaceEvidence workspace) {
        try {
            List<String> findings = ChangedWorkspaceSecretScanner.scan(
                    workspace.baseline(),
                    workspace.snapshot(),
                    environment);
            if (!findings.isEmpty()) {
                throw failure(
                        "workspace-secret-detected",
                        "Ship workspace contains a known secret in changed files: "
                                                     + String.join(", ", findings));
            }
        } catch (Failure e) {
            throw e;
        } catch (IOException | SecurityException e) {
            throw failure(
                    "workspace-secret-scan-failed",
                    "Could not scan changed Ship workspace files for known secrets",
                    e);
        }
    }

    private static List<ArtifactRef> workspaceArtifacts(
            WorkspaceEvidence evidence, List<Path> paths) {
        return paths.stream()
                .map(path -> workspaceArtifact(evidence, path))
                .toList();
    }

    private static ArtifactRef workspaceArtifact(
            WorkspaceEvidence evidence, Path path) {
        Path candidate = evidence.candidate();
        if (path.equals(candidate)) {
            return new ArtifactRef(path.toString(), evidence.snapshot().digest());
        }
        String relative = candidate.relativize(path)
                .toString()
                .replace(java.io.File.separatorChar, '/');
        ProjectSnapshot.FileEntry entry = evidence.snapshot().files().get(relative);
        if (entry == null || entry.size() == 0
                || entry.classification() != Classification.MATERIAL) {
            throw failure(
                    "artifact-invalid",
                    "Ship workspace artifact is not in its accepted staged snapshot: " + path);
        }
        return new ArtifactRef(path.toString(), entry.digest());
    }

    private static Path expectedWorkspace(Path runDirectory) {
        return runDirectory.resolve("workspace/candidate").toAbsolutePath().normalize();
    }

    private static Path validationEvidenceDirectory(
            Path runDirectory, int attempt) {
        if (attempt <= 0) {
            throw failure(
                    "validation-stamp-invalid",
                    "Local Stamp attempt is invalid");
        }
        Path root = runDirectory.toAbsolutePath().normalize();
        Path evidence = ShipRunStore.validationStampPath(root, attempt).getParent();
        try {
            if (!evidence.startsWith(root)
                    || Files.isSymbolicLink(evidence)
                    || !Files.isDirectory(evidence, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException(
                        "Local Stamp evidence directory is missing or unsafe");
            }
            Path realRoot = root.toRealPath();
            Path evidenceRoot = root.resolve("evidence");
            Path realEvidenceRoot = evidenceRoot.toRealPath();
            Path realEvidence = evidence.toRealPath();
            Path expectedEvidence = ShipRunStore.validationStampPath(
                    realRoot, attempt).getParent();
            if (!realEvidenceRoot.equals(expectedEvidence.getParent())
                    || !realEvidence.equals(expectedEvidence)) {
                throw new IOException(
                        "Local Stamp evidence directory escaped its run");
            }
            return realEvidence;
        } catch (IOException | SecurityException e) {
            throw failure(
                    "validation-stamp-invalid",
                    "Local Stamp evidence directory is invalid",
                    e);
        }
    }

    private static boolean isLocalStampEvidence(
            Path runDirectory, StageRecord record) {
        if (record.artifacts().size() != 1) {
            return false;
        }
        Path expected = ShipRunStore.validationStampPath(
                runDirectory, record.attempts());
        return expected.toString().equals(record.artifacts().get(0).path())
                && record.artifacts().get(0).digest().equals(record.outputDigest());
    }

    private static Path privateDirectory(Path parent, String name)
            throws IOException {
        Path directory = parent.resolve(name);
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            Files.createDirectory(
                    directory,
                    PosixFilePermissions.asFileAttribute(
                            PosixFilePermissions.fromString("rwx------")));
        }
        if (Files.isSymbolicLink(directory)
                || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
                || !PosixFilePermissions.fromString("rwx------").equals(
                        Files.getPosixFilePermissions(
                                directory, LinkOption.NOFOLLOW_LINKS))) {
            throw new IOException(
                    "Ship worker directory must be a private real directory: "
                                  + directory);
        }
        return directory.toRealPath(LinkOption.NOFOLLOW_LINKS);
    }

    private static ArtifactRef requireExecuteRoot(
            List<ArtifactRef> artifacts, Path expectedRoot) {
        List<ArtifactRef> roots = artifacts.stream()
                .filter(artifact -> artifact.path().equals(expectedRoot.toString()))
                .toList();
        if (roots.size() != 1) {
            throw failure(
                    "state-invalid",
                    "Completed Ship EXECUTE evidence has no unique workspace root");
        }
        return roots.get(0);
    }

    private static List<Path> normalizeArtifactPaths(
            Path root, List<Path> artifacts) {
        if (artifacts.size() > ShipTreePolicy.DEFAULT_MAX_FILE_COUNT) {
            throw failure(
                    "artifact-invalid",
                    "Ship stage has too many artifacts");
        }
        List<Path> normalized = new ArrayList<>(artifacts.size());
        for (Path artifact : artifacts) {
            if (artifact == null) {
                throw failure("artifact-invalid", "Ship artifact path is required");
            }
            try {
                normalized.add((artifact.isAbsolute() ? artifact : root.resolve(artifact))
                        .toAbsolutePath()
                        .normalize());
            } catch (RuntimeException e) {
                throw failure("artifact-invalid", "Ship artifact path is invalid", e);
            }
        }
        if (normalized.stream().anyMatch(path -> !path.startsWith(root))) {
            throw failure(
                    "artifact-invalid",
                    "Ship artifact is outside its project or workspace");
        }
        if (normalized.stream().distinct().count() != normalized.size()) {
            throw failure(
                    "artifact-invalid",
                    "Ship stage artifacts must have distinct paths");
        }
        return List.copyOf(normalized);
    }

    private static void requireAggregateArtifactSize(List<Path> artifacts) {
        long total = 0;
        for (Path artifact : artifacts) {
            try {
                BasicFileAttributes attributes = Files.readAttributes(
                        artifact, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (attributes.isRegularFile()) {
                    total = Math.addExact(total, attributes.size());
                    if (total > MAX_STAGE_ARTIFACT_BYTES) {
                        throw failure(
                                "artifact-too-large",
                                "Ship stage artifacts exceed the aggregate read limit");
                    }
                }
            } catch (NoSuchFileException e) {
                throw failure(
                        "artifact-missing", "Ship artifact does not exist: " + artifact, e);
            } catch (AccessDeniedException e) {
                throw failure(
                        "artifact-unreadable", "Ship artifact is not readable: " + artifact, e);
            } catch (ArithmeticException e) {
                throw failure(
                        "artifact-too-large",
                        "Ship stage artifacts exceed the aggregate read limit",
                        e);
            } catch (IOException | SecurityException e) {
                throw failure(
                        "artifact-unreadable", "Ship artifact could not be read: " + artifact, e);
            }
        }
    }

    private static void requireExecuteArtifactPaths(
            List<Path> artifacts, Path expectedRoot) {
        if (artifacts.size() >= ShipTreePolicy.DEFAULT_MAX_FILE_COUNT) {
            throw failure(
                    "artifact-invalid",
                    "Ship EXECUTE stage has too many additional artifacts");
        }
        if (artifacts.stream().distinct().count() != artifacts.size()) {
            throw failure(
                    "artifact-invalid",
                    "Ship EXECUTE artifacts must have distinct paths");
        }
        if (artifacts.stream().anyMatch(
                artifact -> artifact.equals(expectedRoot)
                        || !artifact.startsWith(expectedRoot))) {
            throw failure(
                    "artifact-invalid",
                    "Ship EXECUTE artifacts must be additional files in its workspace");
        }
    }

    private static Path requireProject(Path supplied) {
        if (supplied == null) {
            throw failure("project-invalid", "Ship project directory is required");
        }
        final Path normalized;
        try {
            normalized = supplied.toAbsolutePath().normalize();
        } catch (RuntimeException e) {
            throw failure("project-invalid", "Ship project directory is invalid", e);
        }
        if (!Files.exists(normalized, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            throw failure("project-missing", "Ship project directory does not exist: " + normalized);
        }
        if (Files.isSymbolicLink(normalized)
                || !Files.isDirectory(normalized, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            throw failure("project-invalid", "Ship project path is not a directory: " + normalized);
        }
        final Path project;
        try {
            project = normalized.toRealPath();
        } catch (IOException | SecurityException e) {
            throw failure("project-invalid", "Ship project path cannot be resolved: " + normalized, e);
        }
        if (!Files.isReadable(project)) {
            throw failure("project-unreadable", "Ship project directory is not readable: " + project);
        }
        return project;
    }

    private static ShipContext resolveContext(Path project, List<? extends Input> inputs) {
        try {
            return ShipContext.resolve(project, Objects.requireNonNull(inputs, "context inputs"));
        } catch (ShipContext.Failure e) {
            throw failure(contextCode(e), e.getMessage(), e);
        }
    }

    private void rejectContextSecrets(ShipContext context) {
        for (ShipContext.Source source : context.sources()) {
            byte[] content = source.kind() == ShipContext.Kind.TEXT
                    ? source.value().getBytes(StandardCharsets.UTF_8)
                    : readResolvedDocument(source);
            boolean sensitivePath = ChangedWorkspaceSecretScanner
                    .containsSensitiveValue(
                            source.value().getBytes(StandardCharsets.UTF_8),
                            environment);
            if (sensitivePath
                    || ChangedWorkspaceSecretScanner.containsSensitiveValue(
                            content, environment)) {
                throw failure(
                        "context-secret-detected",
                        "Ship context contains a sensitive environment value");
            }
        }
    }

    private static byte[] readResolvedDocument(ShipContext.Source source) {
        int expected = Math.toIntExact(source.byteCount());
        try (InputStream input = Files.newInputStream(Path.of(source.value()))) {
            byte[] content = input.readNBytes(expected + 1);
            if (content.length != expected
                    || !source.digest().equals(ShipDigest.sha256(content))) {
                throw failure(
                        "context-changed",
                        "Ship context changed while it was being verified");
            }
            return content;
        } catch (IOException | SecurityException e) {
            throw failure(
                    "context-unreadable",
                    "Ship context could not be verified",
                    e);
        }
    }

    private static ShipContext refreshContext(ShipContext context) {
        try {
            return context.refresh();
        } catch (ShipContext.Failure e) {
            throw failure(contextCode(e), e.getMessage(), e);
        }
    }

    private static ShipContext resumeContext(
            Path project, ShipContext existing, List<? extends Input> additions) {
        if (additions.isEmpty()) {
            return refreshContext(existing);
        }
        List<Input> combined = new ArrayList<>(existing.sources().size() + additions.size());
        for (ShipContext.Source source : existing.sources()) {
            combined.add(source.kind() == ShipContext.Kind.TEXT
                    ? new ShipContext.TextInput(source.value())
                    : new ShipContext.DocumentInput(source.value()));
        }
        combined.addAll(additions);
        return resolveContext(project, combined);
    }

    private static String contextCode(ShipContext.Failure failure) {
        return "context-" + failure.code().name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static String inspectProjectState(Path project) {
        Path metadata = project.resolve(".camel-kit");
        Path oldState = metadata.resolve("ship-state.json");
        if (Files.exists(oldState)) {
            throw incompatibleState(oldState);
        }

        Path pipelineFile = metadata.resolve("pipeline.json");
        if (!Files.exists(pipelineFile)) {
            return null;
        }
        if (!Files.isRegularFile(pipelineFile) || !Files.isReadable(pipelineFile)) {
            throw incompatibleState(pipelineFile);
        }

        byte[] encoded;
        try {
            if (Files.size(pipelineFile) > MAX_PROJECT_STATE_BYTES) {
                throw incompatibleState(pipelineFile);
            }
            try (InputStream input = Files.newInputStream(pipelineFile)) {
                encoded = input.readNBytes(MAX_PROJECT_STATE_BYTES + 1);
            }
        } catch (IOException | SecurityException e) {
            throw failure(
                    "pre-release-ship-state",
                    "Existing Ship project state cannot be read and was left unchanged: "
                                              + pipelineFile,
                    e);
        }
        if (encoded.length == 0 || encoded.length > MAX_PROJECT_STATE_BYTES) {
            throw incompatibleState(pipelineFile);
        }

        final JsonNode root;
        try {
            root = PROJECT_JSON.readTree(encoded);
        } catch (IOException e) {
            throw failure(
                    "pre-release-ship-state",
                    "Existing Ship project state is incompatible and was left unchanged: "
                                              + pipelineFile,
                    e);
        }
        if (root == null || !root.isObject()) {
            throw incompatibleState(pipelineFile);
        }
        JsonNode mode = root.get("mode");
        if (mode == null || !mode.isTextual() || !"manual".equals(mode.textValue())) {
            throw incompatibleState(pipelineFile);
        }
        JsonNode active = root.get("activePipeline");
        if (active == null || active.isNull()) {
            return null;
        }
        if (!active.isTextual() || !ShipRun.isPipelineId(active.textValue())) {
            throw incompatibleState(pipelineFile);
        }
        return active.textValue();
    }

    private static Failure incompatibleState(Path path) {
        return failure(
                "pre-release-ship-state",
                "Existing pre-release Ship state is incompatible and was left unchanged: " + path);
    }

    private static List<ArtifactRef> importedArtifacts(
            Path project, String pipelineId, Stage stage) {
        if (stage == Stage.DISCOVERY) {
            return List.of();
        }
        if (pipelineId == null) {
            throw failure(
                    "start-from-artifact-missing",
                    "Starting from " + stage.next()
                                                   + " requires a manual activePipeline in .camel-kit/pipeline.json");
        }
        if (stage != Stage.DESIGN) {
            throw new IllegalStateException(
                    "No supported imported artifact for stage " + stage);
        }
        Path reportPath = project.resolve("docs/camel-kit")
                .resolve(pipelineId)
                .resolve("design-spec.md");
        return List.of(readArtifact(project, reportPath));
    }

    private static ArtifactRef readArtifact(Path project, Path supplied) {
        return readArtifact(project, supplied, null);
    }

    private static ArtifactRef readArtifact(
            Path project,
            Path supplied,
            ArtifactReadBudget budget) {
        Objects.requireNonNull(supplied, "artifact path");
        Path normalized;
        try {
            normalized = (supplied.isAbsolute() ? supplied : project.resolve(supplied))
                    .toAbsolutePath()
                    .normalize();
        } catch (RuntimeException e) {
            throw failure("artifact-invalid", "Ship artifact path is invalid", e);
        }
        if (!normalized.startsWith(project)) {
            throw failure(
                    "artifact-invalid", "Ship artifact is outside the project: " + normalized);
        }

        try {
            Path realProject = project.toRealPath();
            Path realArtifact = normalized.toRealPath();
            if (!realArtifact.startsWith(realProject)) {
                throw failure(
                        "artifact-invalid",
                        "Ship artifact resolves outside the project: " + normalized);
            }
            if (normalized.equals(project)) {
                return new ArtifactRef(
                        normalized.toString(),
                        ProjectEvidenceFiles.capture(project).digest());
            }
            String relative = project.relativize(normalized)
                    .toString()
                    .replace(java.io.File.separatorChar, '/');
            Classification classification;
            try {
                classification = ShipTreePolicy.current().classify(relative);
            } catch (IllegalArgumentException e) {
                throw failure("artifact-invalid", "Ship artifact path is unsafe", e);
            }
            if (classification == Classification.DENIED
                    || classification == Classification.PROTECTED) {
                throw failure(
                        "artifact-invalid",
                        "Ship artifact is not publishable under the project-tree policy: " + normalized);
            }
            requireNoSymbolicComponents(project, normalized);
            BasicFileAttributes attributes = Files.readAttributes(
                    normalized, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isRegularFile() || attributes.isSymbolicLink()) {
                throw failure(
                        "artifact-invalid",
                        "Ship artifact is not a stable regular file: " + normalized);
            }
            if (!Files.isReadable(normalized)) {
                throw failure(
                        "artifact-unreadable", "Ship artifact is not readable: " + normalized);
            }
            if (attributes.size() == 0) {
                throw failure("artifact-invalid", "Ship artifact is empty: " + normalized);
            }
            if (attributes.size() > MAX_ARTIFACT_BYTES) {
                throw failure(
                        "artifact-too-large", "Ship artifact exceeds the read limit: " + normalized);
            }
            int readLimit = MAX_ARTIFACT_BYTES;
            if (budget != null) {
                if (attributes.size() > budget.remaining()) {
                    throw failure(
                            "artifact-too-large",
                            "Ship stage artifacts exceed the aggregate read limit");
                }
                readLimit = Math.toIntExact(
                        Math.min((long) MAX_ARTIFACT_BYTES, budget.remaining()));
            }
            byte[] bytes;
            if (classification == Classification.MATERIAL) {
                bytes = ProjectEvidenceFiles.readMaterial(project, relative, readLimit);
            } else {
                bytes = ProjectEvidenceFiles.readVolatile(project, relative, readLimit);
            }
            if (bytes.length == 0) {
                throw failure("artifact-invalid", "Ship artifact is empty: " + normalized);
            }
            if (bytes.length > readLimit) {
                throw failure(
                        "artifact-too-large",
                        readLimit < MAX_ARTIFACT_BYTES
                                ? "Ship stage artifacts exceed the aggregate read limit"
                                : "Ship artifact exceeds the read limit: " + normalized);
            }
            if (budget != null) {
                budget.consume(bytes.length);
            }
            return new ArtifactRef(normalized.toString(), ShipDigest.sha256(bytes));
        } catch (NoSuchFileException e) {
            throw failure("artifact-missing", "Ship artifact does not exist: " + normalized, e);
        } catch (AccessDeniedException e) {
            throw failure(
                    "artifact-unreadable", "Ship artifact is not readable: " + normalized, e);
        } catch (IOException | SecurityException e) {
            throw failure(
                    "artifact-unreadable", "Ship artifact could not be read: " + normalized, e);
        }
    }

    private static void requireNoSymbolicComponents(Path root, Path path)
            throws IOException {
        Path relative = root.relativize(path);
        Path current = root;
        int index = 0;
        for (Path component : relative) {
            current = current.resolve(component);
            BasicFileAttributes attributes = Files.readAttributes(
                    current, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            index++;
            if (attributes.isSymbolicLink()
                    || (index < relative.getNameCount() && !attributes.isDirectory())) {
                throw failure(
                        "artifact-invalid",
                        "Ship artifact path crosses a symbolic link or non-directory");
            }
        }
    }

    private String now() {
        return clock.instant().toString();
    }

    private String mutationTime(ShipRun run) {
        Instant previous = Instant.parse(run.updatedAt());
        Instant current = clock.instant();
        return current.isBefore(previous) ? run.updatedAt() : current.toString();
    }

    private static String newRunId() {
        return "ship-" + UUID.randomUUID().toString().replace("-", "");
    }

    private static Failure failure(String code, String message) {
        return new Failure(code, message);
    }

    private static Failure failure(String code, String message, Throwable cause) {
        return new Failure(code, message, cause);
    }

    private static final class ArtifactReadBudget {

        private long remaining = MAX_STAGE_ARTIFACT_BYTES;

        private long remaining() {
            return remaining;
        }

        private void consume(long bytes) {
            remaining = Math.subtractExact(remaining, bytes);
        }
    }

    private record WorkspaceEvidence(
            Path candidate, ProjectSnapshot baseline, ProjectSnapshot snapshot) {
    }

    record StageAttempt(
            ShipRun run,
            Path workingDirectory,
            Path sessionDirectory,
            Path evidenceDirectory,
            Path inputDirectory,
            Path runDirectory) {

        StageAttempt {
            Objects.requireNonNull(run, "run");
            Objects.requireNonNull(workingDirectory, "working directory");
            Objects.requireNonNull(sessionDirectory, "session directory");
            Objects.requireNonNull(evidenceDirectory, "evidence directory");
            Objects.requireNonNull(inputDirectory, "input directory");
            Objects.requireNonNull(runDirectory, "run directory");
        }

        StageRecord stage() {
            return run.stage(run.currentStage());
        }
    }

    public static final class Failure extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final String code;

        private Failure(String code, String message) {
            super(message);
            this.code = Objects.requireNonNull(code, "failure code");
        }

        private Failure(String code, String message, Throwable cause) {
            super(message, cause);
            this.code = Objects.requireNonNull(code, "failure code");
        }

        public String code() {
            return code;
        }

        Failure withMessage(String message) {
            return new Failure(code, message, this);
        }
    }
}
