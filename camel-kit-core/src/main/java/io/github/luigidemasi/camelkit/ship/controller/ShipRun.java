package io.github.luigidemasi.camelkit.ship.controller;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import io.github.luigidemasi.camelkit.ship.ShipDigest;
import io.github.luigidemasi.camelkit.ship.context.ShipContext;
import io.github.luigidemasi.camelkit.ship.security.ShipTreePolicy;

/** Compact authoritative state for one local Ship run. */
public record ShipRun(
        int schemaVersion,
        String id,
        String projectDirectory,
        String pipelineId,
        Oversight oversight,
        RunStatus status,
        Stage currentStage,
        ShipContext context,
        List<StageRecord> stages,
        ArtifactRef publication,
        String createdAt,
        String updatedAt,
        String message) {

    public static final int SCHEMA_VERSION = 4;
    static final int MAX_MESSAGE_LENGTH = 1024;

    private static final Pattern RUN_ID = Pattern.compile("ship-[0-9a-f]{32}");
    private static final Pattern PIPELINE_ID = Pattern.compile("[0-9]{3,}-[a-z0-9]+(?:-[a-z0-9]+)*");

    public ShipRun {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported Ship run schema version: " + schemaVersion);
        }
        if (!isRunId(id)) {
            throw new IllegalArgumentException("Ship run ID is invalid");
        }
        projectDirectory = normalizedAbsolutePath(projectDirectory, "project directory");
        if (pipelineId != null && !isPipelineId(pipelineId)) {
            throw new IllegalArgumentException("Ship pipeline ID is invalid");
        }
        Objects.requireNonNull(oversight, "oversight");
        Objects.requireNonNull(status, "run status");
        Objects.requireNonNull(currentStage, "current stage");
        Objects.requireNonNull(context, "context");
        stages = List.copyOf(Objects.requireNonNull(stages, "stages"));
        requireCanonicalStages(stages);
        createdAt = canonicalInstant(createdAt, "created timestamp");
        updatedAt = canonicalInstant(updatedAt, "updated timestamp");
        if (Instant.parse(updatedAt).isBefore(Instant.parse(createdAt))) {
            throw new IllegalArgumentException("Ship run update precedes its creation");
        }
        if (message != null
                && (message.isBlank()
                        || message.length() > MAX_MESSAGE_LENGTH
                        || message.indexOf('\0') >= 0)) {
            throw new IllegalArgumentException("Ship run message is invalid");
        }
        if (status != RunStatus.PAUSED
                && ((status == RunStatus.FAILED || status == RunStatus.ABORTED)
                    != (message != null))) {
            throw new IllegalArgumentException("Only paused, failed, or aborted runs carry a message");
        }
        requireConsistentProgress(status, currentStage, stages, publication);
    }

    /** True when every stage is complete and only guarded publication remains. */
    public boolean publicationPending() {
        return status == RunStatus.RUNNING
                && stages.stream().allMatch(stage -> stage.status() == StageStatus.COMPLETED);
    }

    public static boolean isRunId(String value) {
        return value != null && RUN_ID.matcher(value).matches();
    }

    static boolean isPipelineId(String value) {
        return value != null && PIPELINE_ID.matcher(value).matches();
    }

    public StageRecord stage(Stage stage) {
        return stages.get(stage.ordinal());
    }

    static List<StageRecord> pendingStages() {
        return java.util.Arrays.stream(Stage.values()).map(StageRecord::pending).toList();
    }

    static String inputDigest(ShipContext context, List<StageRecord> stages, Stage stage) {
        List<String> fields = new ArrayList<>();
        fields.add("camel-kit.ship.stage-input.v1");
        fields.add(stage.name());
        fields.add(context.digest());
        for (int index = 0; index < stage.ordinal(); index++) {
            StageRecord predecessor = stages.get(index);
            if (predecessor.status() != StageStatus.COMPLETED) {
                throw new IllegalStateException("Ship stage " + stage + " has an incomplete predecessor");
            }
            fields.add(predecessor.stage().name());
            fields.add(predecessor.outputDigest());
            for (ArtifactRef artifact : predecessor.artifacts()) {
                fields.add(artifact.path());
                fields.add(artifact.digest());
            }
        }
        return digestFields(fields);
    }

    static String digestFields(List<String> fields) {
        ByteArrayOutputStream framed = new ByteArrayOutputStream();
        for (String field : fields) {
            byte[] bytes = Objects.requireNonNull(field, "digest field").getBytes(StandardCharsets.UTF_8);
            framed.writeBytes(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
            framed.writeBytes(bytes);
        }
        return ShipDigest.sha256(framed.toByteArray());
    }

    static String executeOutputDigest(ArtifactRef root) {
        return digestFields(List.of(
                "camel-kit.ship.execute-output.v1",
                root.path(),
                root.digest()));
    }

    private static void requireCanonicalStages(List<StageRecord> stages) {
        if (stages.size() != Stage.values().length) {
            throw new IllegalArgumentException("Ship run must contain one record for every stage");
        }
        for (Stage stage : Stage.values()) {
            if (stages.get(stage.ordinal()).stage() != stage) {
                throw new IllegalArgumentException("Ship stage records are not in canonical order");
            }
        }
    }

    private static void requireConsistentProgress(
            RunStatus status, Stage currentStage, List<StageRecord> stages, ArtifactRef publication) {
        int current = currentStage.ordinal();
        boolean allCompleted = stages.stream().allMatch(stage -> stage.status() == StageStatus.COMPLETED);
        if (publication != null
                && (currentStage != Stage.VALIDATE
                        || stages.get(Stage.EXECUTE.ordinal()).status() != StageStatus.COMPLETED)) {
            throw new IllegalArgumentException(
                    "A Ship publication record requires its published EXECUTE candidate");
        }
        if (status == RunStatus.COMPLETED) {
            if (!allCompleted || currentStage != Stage.VALIDATE) {
                throw new IllegalArgumentException("Completed Ship run has incomplete stages");
            }
            if (publication == null) {
                throw new IllegalArgumentException("Completed Ship run has no publication record");
            }
            return;
        }
        if (allCompleted && currentStage == Stage.VALIDATE) {
            if (publication != null) {
                throw new IllegalArgumentException(
                        "Only a completed Ship run retains a publication record with all stages complete");
            }
            // RUNNING means guarded publication is pending; PAUSED is the pre-publication
            // oversight gate; FAILED is a rolled-back publication; ABORTED is a user stop.
            return;
        }
        for (int index = 0; index < stages.size(); index++) {
            StageStatus stageStatus = stages.get(index).status();
            if (index < current && stageStatus != StageStatus.COMPLETED) {
                throw new IllegalArgumentException("Ship run has an incomplete predecessor stage");
            }
            if (index > current && stageStatus != StageStatus.PENDING) {
                throw new IllegalArgumentException("Ship run has an active downstream stage");
            }
        }
        StageStatus active = stages.get(current).status();
        StageStatus expected = switch (status) {
            case RUNNING -> StageStatus.RUNNING;
            case PAUSED -> StageStatus.PENDING;
            case FAILED -> StageStatus.FAILED;
            case ABORTED -> StageStatus.ABORTED;
            case COMPLETED -> throw new IllegalStateException("Completed run handled above");
        };
        if (active != expected) {
            throw new IllegalArgumentException("Ship run status does not match its current stage");
        }
    }

    private static String normalizedAbsolutePath(String value, String label) {
        if (value == null || value.isBlank() || value.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Ship " + label + " is invalid");
        }
        Path path;
        try {
            path = Path.of(value);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Ship " + label + " is invalid", e);
        }
        Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.toString().equals(value)) {
            throw new IllegalArgumentException("Ship " + label + " must be a normalized absolute path");
        }
        return value;
    }

    private static String canonicalInstant(String value, String label) {
        try {
            Instant parsed = Instant.parse(Objects.requireNonNull(value, label));
            if (!parsed.toString().equals(value)) {
                throw new IllegalArgumentException("Ship " + label + " must be canonical");
            }
            return value;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Ship " + label + " is invalid", e);
        }
    }

    public enum Oversight {
        ALWAYS,
        SMART,
        NEVER;

        public boolean pausesAfter(Stage completedStage, boolean materialAmbiguity) {
            if (materialAmbiguity) {
                return this != NEVER;
            }
            return switch (this) {
                case ALWAYS -> completedStage == Stage.DESIGN
                        || completedStage == Stage.PLAN
                        || completedStage == Stage.EXECUTE
                        || completedStage == Stage.VALIDATE;
                case SMART -> completedStage == Stage.PLAN || completedStage == Stage.EXECUTE;
                case NEVER -> false;
            };
        }
    }

    public enum RunStatus {
        RUNNING,
        PAUSED,
        FAILED,
        ABORTED,
        COMPLETED
    }

    public enum Stage {
        DISCOVERY,
        DESIGN,
        PLAN,
        EXECUTE,
        VALIDATE;

        public Stage next() {
            int next = ordinal() + 1;
            return next == values().length ? null : values()[next];
        }
    }

    public enum StageStatus {
        PENDING,
        RUNNING,
        COMPLETED,
        FAILED,
        ABORTED
    }

    public record ArtifactRef(String path, String digest) {
        public ArtifactRef {
            path = normalizedAbsolutePath(path, "artifact path");
            if (!ShipDigest.isSha256(digest)) {
                throw new IllegalArgumentException("Ship artifact digest is invalid");
            }
        }
    }

    public record StageRecord(
            Stage stage,
            StageStatus status,
            int attempts,
            String inputDigest,
            String outputDigest,
            List<ArtifactRef> artifacts) {

        public StageRecord {
            Objects.requireNonNull(stage, "stage");
            Objects.requireNonNull(status, "stage status");
            if (attempts < 0 || attempts == Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Ship stage attempts are out of range");
            }
            artifacts = List.copyOf(Objects.requireNonNull(artifacts, "artifacts"));
            if (artifacts.size() > ShipTreePolicy.DEFAULT_MAX_FILE_COUNT
                    || artifacts.stream().map(ArtifactRef::path).distinct().count()
                       != artifacts.size()) {
                throw new IllegalArgumentException(
                        "Ship stage artifacts must be bounded and have distinct paths");
            }
            if (inputDigest != null && !ShipDigest.isSha256(inputDigest)) {
                throw new IllegalArgumentException("Ship stage input digest is invalid");
            }
            if (outputDigest != null && !ShipDigest.isSha256(outputDigest)) {
                throw new IllegalArgumentException("Ship stage output digest is invalid");
            }
            switch (status) {
                case PENDING -> require(attempts >= 0 && inputDigest == null && outputDigest == null
                        && artifacts.isEmpty());
                case RUNNING -> require(attempts > 0 && inputDigest != null && outputDigest == null
                        && artifacts.isEmpty());
                case COMPLETED -> require(inputDigest != null && outputDigest != null);
                case FAILED -> require(
                        attempts > 0
                                && inputDigest != null
                                && (outputDigest == null && artifacts.isEmpty()
                                        || stage == Stage.VALIDATE
                                                && outputDigest != null
                                                && !artifacts.isEmpty()));
                case ABORTED -> require(outputDigest == null && artifacts.isEmpty());
            }
        }

        static StageRecord pending(Stage stage) {
            return new StageRecord(stage, StageStatus.PENDING, 0, null, null, List.of());
        }

        StageRecord start(String digest) {
            if (status != StageStatus.PENDING) {
                throw new IllegalStateException("Only a pending Ship stage can start");
            }
            return new StageRecord(
                    stage, StageStatus.RUNNING, Math.incrementExact(attempts),
                    digest, null, List.of());
        }

        StageRecord complete(String digest, List<ArtifactRef> references) {
            if (status != StageStatus.RUNNING) {
                throw new IllegalStateException("Only a running Ship stage can complete");
            }
            return new StageRecord(
                    stage, StageStatus.COMPLETED, attempts,
                    inputDigest, digest, references);
        }

        StageRecord imported(String input, String output, List<ArtifactRef> references) {
            if (status != StageStatus.PENDING) {
                throw new IllegalStateException("Only a pending Ship stage can be imported");
            }
            return new StageRecord(stage, StageStatus.COMPLETED, attempts, input, output, references);
        }

        StageRecord fail() {
            if (status != StageStatus.RUNNING) {
                throw new IllegalStateException("Only a running Ship stage can fail");
            }
            return new StageRecord(
                    stage, StageStatus.FAILED, attempts,
                    inputDigest, null, List.of());
        }

        StageRecord fail(String digest, List<ArtifactRef> references) {
            if (status != StageStatus.RUNNING || references == null
                    || references.isEmpty()) {
                throw new IllegalStateException(
                        "Only a running Ship stage can fail with evidence");
            }
            return new StageRecord(
                    stage,
                    StageStatus.FAILED,
                    attempts,
                    inputDigest,
                    digest,
                    references);
        }

        StageRecord abort() {
            if (status == StageStatus.COMPLETED) {
                throw new IllegalStateException("A completed Ship stage cannot be aborted");
            }
            return new StageRecord(
                    stage, StageStatus.ABORTED, attempts,
                    inputDigest, null, List.of());
        }

        StageRecord reset() {
            return new StageRecord(stage, StageStatus.PENDING, attempts, null, null, List.of());
        }

        StageRecord withArtifacts(String digest, List<ArtifactRef> references) {
            if (status != StageStatus.COMPLETED) {
                throw new IllegalStateException("Only a completed Ship stage has artifacts");
            }
            return new StageRecord(stage, status, attempts, inputDigest, digest, references);
        }

        private static void require(boolean condition) {
            if (!condition) {
                throw new IllegalArgumentException("Ship stage record does not match its status");
            }
        }
    }
}
