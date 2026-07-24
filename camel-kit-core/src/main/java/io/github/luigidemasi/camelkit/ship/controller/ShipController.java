package io.github.luigidemasi.camelkit.ship.controller;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
import io.github.luigidemasi.camelkit.ship.security.ProjectEvidenceFiles;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Compact local lifecycle service for Ship runs. */
public final class ShipController {

    private static final int MAX_PROJECT_STATE_BYTES = 1024 * 1024;
    private static final int MAX_ARTIFACT_BYTES = 64 * 1024 * 1024;
    private static final String ABORT_MESSAGE = "Run aborted by user";
    private static final ObjectMapper PROJECT_JSON = new ObjectMapper(
            JsonFactory.builder()
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private final ShipRunStore store;
    private final Clock clock;

    public ShipController(Path stateRoot) {
        this(stateRoot, Clock.systemUTC());
    }

    ShipController(Path stateRoot, Clock clock) {
        this.store = new ShipRunStore(Objects.requireNonNull(stateRoot, "state root"));
        this.clock = Objects.requireNonNull(clock, "clock");
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
        List<StageRecord> stages = new ArrayList<>(ShipRun.pendingStages());
        stages.set(
                Stage.DISCOVERY.ordinal(),
                stages.get(Stage.DISCOVERY.ordinal())
                        .start(ShipRun.inputDigest(context, stages, Stage.DISCOVERY)));
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
        String pipelineId = inspectProjectState(project);
        ShipContext context = resolveContext(project, inputs);
        if (startStage == Stage.DESIGN && context.sources().isEmpty()) {
            throw failure(
                    "start-from-context-missing",
                    "Starting from DESIGN requires text or document context");
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
                stages.get(startStage.ordinal())
                        .start(ShipRun.inputDigest(context, stages, startStage)));
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
        try (ShipRunStore.LockedRun locked = store.lock(runId)) {
            ShipRun current = locked.read();
            if (current.status() == RunStatus.ABORTED) {
                throw failure("run-aborted", "Ship run is aborted and cannot resume: " + runId);
            }
            if (current.status() == RunStatus.COMPLETED) {
                throw failure("run-completed", "Ship run is already complete: " + runId);
            }

            Path project = requireProject(Path.of(current.projectDirectory()));
            ShipContext refreshed = refreshContext(current.context());
            List<StageRecord> stages = new ArrayList<>(current.stages());
            int restart = stages.size();

            for (int index = 0; index < stages.size(); index++) {
                StageRecord record = stages.get(index);
                if (record.status() != StageStatus.COMPLETED) {
                    continue;
                }
                List<ArtifactRef> refreshedArtifacts = new ArrayList<>(record.artifacts().size());
                boolean missing = false;
                for (ArtifactRef artifact : record.artifacts()) {
                    try {
                        refreshedArtifacts.add(readArtifact(project, Path.of(artifact.path())));
                    } catch (Failure e) {
                        restart = Math.min(restart, index);
                        missing = true;
                        break;
                    }
                }
                if (!missing && !refreshedArtifacts.equals(record.artifacts())) {
                    stages.set(index, record.withArtifacts(refreshedArtifacts));
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
                resumed = copy(
                        current,
                        RunStatus.COMPLETED,
                        Stage.VALIDATE,
                        refreshed,
                        stages,
                        null);
            } else {
                for (int index = restart; index < stages.size(); index++) {
                    stages.set(index, stages.get(index).reset());
                }
                Stage stage = Stage.values()[restart];
                stages.set(
                        restart,
                        stages.get(restart)
                                .start(ShipRun.inputDigest(refreshed, stages, stage)));
                resumed = copy(
                        current,
                        RunStatus.RUNNING,
                        stage,
                        refreshed,
                        stages,
                        null);
            }
            locked.write(resumed);
            return resumed;
        } catch (ShipRunStore.StoreException e) {
            throw failure(e.code(), e.getMessage(), e);
        } catch (IOException e) {
            throw failure("state-write-failed", "Could not resume Ship run " + runId, e);
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
     * Records a worker result only when it matches the currently running stage attempt.
     */
    public ShipRun completeStage(
            String runId,
            Stage stage,
            int attempt,
            String inputDigest,
            String outputDigest,
            List<Path> artifacts,
            boolean materialAmbiguity) {
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(artifacts, "artifacts");
        if (!ShipDigest.isSha256(outputDigest)) {
            throw failure("stage-result-invalid", "Ship stage output digest is invalid");
        }
        try (ShipRunStore.LockedRun locked = store.lock(runId)) {
            ShipRun current = locked.read();
            StageRecord active = requireActiveAttempt(current, stage, attempt, inputDigest);
            requireCurrentInputs(current, active);
            Path project = Path.of(current.projectDirectory());
            List<ArtifactRef> references = artifacts.stream()
                    .map(path -> readArtifact(project, path))
                    .toList();
            List<StageRecord> stages = new ArrayList<>(current.stages());
            stages.set(stage.ordinal(), active.complete(outputDigest, references));

            Stage next = stage.next();
            RunStatus status;
            Stage currentStage;
            if (next == null) {
                status = current.oversight().pausesAfter(stage, materialAmbiguity)
                        ? RunStatus.PAUSED
                        : RunStatus.COMPLETED;
                currentStage = Stage.VALIDATE;
            } else if (current.oversight().pausesAfter(stage, materialAmbiguity)) {
                status = RunStatus.PAUSED;
                currentStage = next;
            } else {
                stages.set(
                        next.ordinal(),
                        stages.get(next.ordinal())
                                .start(ShipRun.inputDigest(current.context(), stages, next)));
                status = RunStatus.RUNNING;
                currentStage = next;
            }

            ShipRun completed = copy(
                    current,
                    status,
                    currentStage,
                    current.context(),
                    stages,
                    null);
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
        if (message == null || message.isBlank() || message.length() > 1024
                || message.indexOf('\0') >= 0) {
            throw failure("stage-result-invalid", "Ship stage failure message is invalid");
        }
        try (ShipRunStore.LockedRun locked = store.lock(runId)) {
            ShipRun current = locked.read();
            StageRecord active = requireActiveAttempt(current, stage, attempt, inputDigest);
            requireCurrentInputs(current, active);
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
        return new ShipRun(
                run.schemaVersion(),
                run.id(),
                run.projectDirectory(),
                run.pipelineId(),
                run.oversight(),
                status,
                currentStage,
                context,
                stages,
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

    private static void requireCurrentInputs(ShipRun run, StageRecord active) {
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

        for (int index = 0; index < active.stage().ordinal(); index++) {
            StageRecord predecessor = run.stages().get(index);
            for (ArtifactRef artifact : predecessor.artifacts()) {
                try {
                    if (!readArtifact(project, Path.of(artifact.path())).equals(artifact)) {
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
        }
        String expected = ShipRun.inputDigest(refreshed, run.stages(), active.stage());
        if (!expected.equals(active.inputDigest())) {
            throw failure(
                    "stale-stage-input",
                    "Ship stage input digest is stale; resume the run");
        }
    }

    private static Path requireProject(Path supplied) {
        if (supplied == null) {
            throw failure("project-invalid", "Ship project directory is required");
        }
        final Path project;
        try {
            project = supplied.toAbsolutePath().normalize();
        } catch (RuntimeException e) {
            throw failure("project-invalid", "Ship project directory is invalid", e);
        }
        if (!Files.exists(project)) {
            throw failure("project-missing", "Ship project directory does not exist: " + project);
        }
        if (!Files.isDirectory(project)) {
            throw failure("project-invalid", "Ship project path is not a directory: " + project);
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

    private static ShipContext refreshContext(ShipContext context) {
        try {
            return context.refresh();
        } catch (ShipContext.Failure e) {
            throw failure(contextCode(e), e.getMessage(), e);
        }
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
        String filename = switch (stage) {
            case DESIGN -> "design-spec.md";
            case PLAN -> "implementation-plan.md";
            case EXECUTE -> "execution-report.md";
            case DISCOVERY, VALIDATE -> throw new IllegalStateException(
                    "No imported artifact for stage " + stage);
        };
        ArtifactRef report = readArtifact(
                project, project.resolve("docs/camel-kit").resolve(pipelineId).resolve(filename));
        return stage == Stage.EXECUTE
                ? List.of(report, readArtifact(project, project))
                : List.of(report);
    }

    private static ArtifactRef readArtifact(Path project, Path supplied) {
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
                        normalized.toString(), ProjectEvidenceFiles.capture(project).digest());
            }
            BasicFileAttributes attributes = Files.readAttributes(normalized, BasicFileAttributes.class);
            if (!attributes.isRegularFile()) {
                throw failure(
                        "artifact-invalid",
                        "Ship artifact is not a regular file: " + normalized);
            }
            if (!Files.isReadable(normalized)) {
                throw failure(
                        "artifact-unreadable", "Ship artifact is not readable: " + normalized);
            }
            if (attributes.size() > MAX_ARTIFACT_BYTES) {
                throw failure(
                        "artifact-too-large", "Ship artifact exceeds the read limit: " + normalized);
            }
            byte[] bytes;
            try (InputStream input = Files.newInputStream(normalized)) {
                bytes = input.readNBytes(MAX_ARTIFACT_BYTES + 1);
            }
            if (bytes.length == 0) {
                throw failure("artifact-invalid", "Ship artifact is empty: " + normalized);
            }
            if (bytes.length > MAX_ARTIFACT_BYTES) {
                throw failure(
                        "artifact-too-large", "Ship artifact exceeds the read limit: " + normalized);
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
    }
}
