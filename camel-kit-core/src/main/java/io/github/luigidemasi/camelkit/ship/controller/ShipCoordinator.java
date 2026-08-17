package io.github.luigidemasi.camelkit.ship.controller;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import io.github.luigidemasi.camelkit.config.DistributionConfig;
import io.github.luigidemasi.camelkit.ship.ShipDigest;
import io.github.luigidemasi.camelkit.ship.artifact.ArtifactManifest.JavaPolicy;
import io.github.luigidemasi.camelkit.ship.artifact.ArtifactPolicy;
import io.github.luigidemasi.camelkit.ship.artifact.CitrusDependencyPolicy;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogTarget;
import io.github.luigidemasi.camelkit.ship.catalog.ShipCatalogService;
import io.github.luigidemasi.camelkit.ship.catalog.ShipCatalogService.Snapshot;
import io.github.luigidemasi.camelkit.ship.context.ShipContext;
import io.github.luigidemasi.camelkit.ship.controller.ShipController.StageAttempt;
import io.github.luigidemasi.camelkit.ship.controller.ShipRun.RunStatus;
import io.github.luigidemasi.camelkit.ship.controller.ShipRun.Stage;
import io.github.luigidemasi.camelkit.ship.controller.ShipRun.StageRecord;
import io.github.luigidemasi.camelkit.ship.controller.ShipRun.StageStatus;
import io.github.luigidemasi.camelkit.ship.evidence.ShipLocalStamp;
import io.github.luigidemasi.camelkit.ship.worker.ChangedWorkspaceSecretScanner;
import io.github.luigidemasi.camelkit.ship.worker.PiWorker;
import io.github.luigidemasi.camelkit.ship.worker.PiWorker.Outcome;
import io.github.luigidemasi.camelkit.ship.worker.PiWorker.Recovery;
import io.github.luigidemasi.camelkit.ship.worker.PiWorker.Request;
import io.github.luigidemasi.camelkit.ship.worker.PiWorker.Result;
import io.github.luigidemasi.camelkit.ship.worker.PiWorker.SessionBusyException;
import io.github.luigidemasi.camelkit.ship.worker.PiWorker.UntrustedResultException;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Concrete local composition of the compact Ship controller, Pi worker, and deterministic Main validation.
 *
 * <p>
 * The command remains unregistered until the live Pi/Linux and publication gates pass.
 */
public final class ShipCoordinator {

    private static final long ABORT_POLL_MILLIS = 50;
    private static final int MAX_BRIEFING_BYTES = 16 * 1024 * 1024;
    private static final int PLAN_CONTRACT_VERSION = 1;
    private static final String MANIFEST_SCHEMA_RESOURCE
            = "/ship/schema/artifact-manifest.schema.json";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final Path stateRoot;
    private final ShipController controller;
    private final PiWorker worker;
    private final CatalogProvider catalogs;
    private final ShipMainValidator validator;
    private final DistributionConfig distribution;
    private final Map<String, String> environment;
    private final boolean acceptExperimental;
    private final Clock clock;

    public ShipCoordinator(
                           Path stateRoot,
                           Path piExecutable,
                           Path nodeExecutable,
                           Path localMavenRepository,
                           DistributionConfig distribution,
                           Duration timeout,
                           boolean acceptExperimental) {
        Map<String, String> environment = Map.copyOf(System.getenv());
        this.stateRoot = Objects.requireNonNull(stateRoot, "state root")
                .toAbsolutePath()
                .normalize();
        this.controller = new ShipController(this.stateRoot, environment);
        this.distribution = Objects.requireNonNull(distribution, "distribution");
        // Only bundled pins may mark Pi/Node supported; injected config still drives artifact policy.
        DistributionConfig maintained = DistributionConfig.loadBundled();
        this.worker = new PiWorker(
                piExecutable,
                maintained.piVersion(),
                nodeExecutable,
                maintained.nodeVersion(),
                timeout,
                environment);
        this.catalogs = new ShipCatalogService(localMavenRepository)::snapshot;
        this.validator = new ShipMainValidator();
        this.environment = environment;
        this.acceptExperimental = acceptExperimental;
        this.clock = Clock.systemUTC();
    }

    ShipCoordinator(
                    Path stateRoot,
                    ShipController controller,
                    PiWorker worker,
                    ShipCatalogService catalogs,
                    ShipMainValidator validator,
                    DistributionConfig distribution,
                    boolean acceptExperimental,
                    Clock clock) {
        this(
             stateRoot,
             controller,
             worker,
             catalogs::snapshot,
             validator,
             distribution,
             Map.of(),
             acceptExperimental,
             clock);
    }

    ShipCoordinator(
                    Path stateRoot,
                    ShipController controller,
                    PiWorker worker,
                    CatalogProvider catalogs,
                    ShipMainValidator validator,
                    DistributionConfig distribution,
                    Map<String, String> environment,
                    boolean acceptExperimental,
                    Clock clock) {
        this.stateRoot = Objects.requireNonNull(stateRoot, "state root")
                .toAbsolutePath()
                .normalize();
        this.controller = Objects.requireNonNull(controller, "controller");
        this.worker = Objects.requireNonNull(worker, "worker");
        this.catalogs = Objects.requireNonNull(catalogs, "catalogs");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.distribution = Objects.requireNonNull(distribution, "distribution");
        this.environment = Map.copyOf(
                Objects.requireNonNull(environment, "environment"));
        this.acceptExperimental = acceptExperimental;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Runs authoritative stages until an oversight gate or terminal outcome is reached. */
    public ShipRun run(String runId) throws IOException, InterruptedException {
        try {
            ShipRun initial = controller.status(runId);
            try (AttemptLease lease = tryCoordinatorLease(runId)) {
                if (lease == null) {
                    return controller.status(runId);
                }
                if (initial.status() == RunStatus.ABORTED) {
                    return initial;
                }
                try (AbortWatcher ignored = new AbortWatcher(
                        controller, runId)) {
                    return runAvailable(runId);
                }
            }
        } catch (SessionBusyException e) {
            return controller.status(runId);
        } catch (ShipController.Failure e) {
            if (concurrentTransition(e)) {
                return controller.status(runId);
            }
            throw e;
        }
    }

    private ShipRun runAvailable(String runId)
            throws IOException, InterruptedException {
        ShipRun current = controller.status(runId);
        while (current.status() == RunStatus.RUNNING
                && !Thread.currentThread().isInterrupted()) {
            if (current.publicationPending()) {
                current = publishPending(runId);
                continue;
            }
            StageAttempt attempt = controller.prepareAttempt(current.id());
            Optional<ShipRun> restarted = restartStalePredecessor(
                    attempt);
            if (restarted.isPresent()) {
                current = restarted.orElseThrow();
                continue;
            }
            Step step = attempt.stage().stage() == Stage.VALIDATE
                    ? validate(attempt)
                    : advancePi(attempt);
            current = step.run();
            if (Thread.currentThread().isInterrupted()) {
                return controller.status(runId);
            }
            if (!step.progressed()) {
                return current;
            }
        }
        return Thread.currentThread().isInterrupted()
                ? controller.status(runId)
                : current;
    }

    /**
     * Recovers an exact durable Pi result before retrying; deterministic validation restarts in a fresh attempt.
     */
    public ShipRun resume(String runId) throws IOException, InterruptedException {
        try {
            ShipRun initial = controller.status(runId);
            try (AttemptLease lease = tryCoordinatorLease(runId)) {
                if (lease == null) {
                    return controller.status(runId);
                }
                if (initial.status() == RunStatus.ABORTED) {
                    return resumeAvailable(runId);
                }
                try (AbortWatcher ignored = new AbortWatcher(
                        controller, runId)) {
                    return resumeAvailable(runId);
                }
            }
        } catch (SessionBusyException e) {
            return controller.status(runId);
        } catch (ShipController.Failure e) {
            if (concurrentTransition(e)) {
                return controller.status(runId);
            }
            throw e;
        }
    }

    private ShipRun resumeAvailable(String runId)
            throws IOException, InterruptedException {
        requireNotInterrupted();
        ShipRun current = controller.status(runId);
        if (current.status() == RunStatus.RUNNING) {
            if (current.publicationPending()) {
                return continueAvailable(runId, publishPending(runId));
            }
            final StageAttempt attempt;
            try {
                attempt = controller.prepareAttempt(runId);
            } catch (ShipController.Failure e) {
                if (!"stale-stage-input".equals(e.code())) {
                    throw e;
                }
                requireNotInterrupted();
                current = controller.resume(runId);
                return continueAvailable(runId, current);
            }
            Optional<ShipRun> restarted = restartStalePredecessor(
                    attempt);
            if (restarted.isPresent()) {
                return continueAvailable(
                        runId, restarted.orElseThrow());
            }
            if (attempt.stage().stage() == Stage.VALIDATE) {
                requireNotInterrupted();
                current = controller.resume(runId);
            } else {
                Request request = request(attempt);
                try (Recovery recovery = worker.lockRecovery(request)) {
                    if (recovery.result().isPresent()) {
                        current = commitPi(
                                attempt,
                                recovery.result().orElseThrow())
                                .run();
                    } else {
                        requireNotInterrupted();
                        current = controller.resume(runId);
                    }
                } catch (SessionBusyException e) {
                    throw e;
                } catch (IOException e) {
                    requireNotInterrupted();
                    current = optimisticFailure(
                            attempt,
                            "Durable Pi stage result could not be recovered");
                }
            }
        } else {
            requireNotInterrupted();
            current = controller.resume(runId);
        }
        return continueAvailable(runId, current);
    }

    private ShipRun continueAvailable(String runId, ShipRun current)
            throws IOException, InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            return controller.status(runId);
        }
        return current.status() == RunStatus.RUNNING
                ? runAvailable(runId)
                : current;
    }

    /**
     * Drives the guarded publication of a fully validated run; a live project that drifted since EXECUTE routes into
     * the controller's stale-stage resume path instead of escaping.
     */
    private ShipRun publishPending(String runId) throws IOException {
        try {
            return controller.publish(runId);
        } catch (ShipController.Failure e) {
            if (!"stale-stage-input".equals(e.code())) {
                throw e;
            }
            return controller.resume(runId);
        }
    }

    private Step advancePi(StageAttempt attempt)
            throws IOException, InterruptedException {
        Request request = request(attempt);
        final Optional<Result> recovered;
        try {
            recovered = worker.recover(request);
        } catch (SessionBusyException e) {
            return new Step(
                    controller.status(attempt.run().id()), false);
        } catch (IOException e) {
            requireNotInterrupted();
            return new Step(
                    optimisticFailure(
                            attempt,
                            "Durable Pi stage result could not be recovered"),
                    true);
        }
        Result result;
        if (recovered.isPresent()) {
            result = recovered.orElseThrow();
        } else {
            try {
                result = worker.run(request);
            } catch (SessionBusyException e) {
                return new Step(
                        controller.status(attempt.run().id()), false);
            } catch (IOException e) {
                Optional<Result> afterFailure = worker.recover(request);
                if (afterFailure.isEmpty()) {
                    return new Step(
                            optimisticFailure(
                                    attempt,
                                    "Pi stage could not run: " + safeMessage(e)),
                            true);
                }
                result = afterFailure.orElseThrow();
            }
        }
        return commitPi(attempt, result);
    }

    private Optional<ShipRun> restartStalePredecessor(
            StageAttempt attempt)
            throws IOException, InterruptedException {
        for (Stage stage : Stage.values()) {
            if (stage.ordinal() >= attempt.stage().stage().ordinal()) {
                break;
            }
            StageRecord predecessor = attempt.run().stage(stage);
            if (predecessor.attempts() <= 0) {
                continue;
            }
            final Result result;
            try {
                result = completedPiResult(attempt, predecessor);
            } catch (UntrustedResultException
                     | StalePredecessorException e) {
                requireNotInterrupted();
                return Optional.of(controller.restartGeneratedStage(
                        attempt, predecessor));
            }
            try {
                ShipStageResult typed = ShipStageResult.parse(
                        stage, result.assistantText());
                if (stage == Stage.PLAN) {
                    requireDistributionPolicy(typed.artifactPolicy());
                }
            } catch (IOException | IllegalArgumentException e) {
                requireNotInterrupted();
                return Optional.of(controller.restartGeneratedStage(
                        attempt, predecessor));
            }
        }
        return Optional.empty();
    }

    private Step commitPi(StageAttempt attempt, Result result) {
        StageRecord stage = attempt.stage();
        if (result.outcome() != Outcome.SUCCEEDED) {
            return new Step(
                    optimisticFailure(
                            attempt,
                            result.failure() == null
                                    ? "Pi stage failed"
                                    : result.failure()),
                    true);
        }

        final ShipStageResult typed;
        try {
            typed = ShipStageResult.parse(stage.stage(), result.assistantText());
            if (stage.stage() == Stage.PLAN) {
                requireDistributionPolicy(typed.artifactPolicy());
            }
        } catch (IOException | IllegalArgumentException e) {
            return new Step(
                    optimisticFailure(
                            attempt,
                            "Pi returned an invalid "
                                     + stage.stage().name().toLowerCase(
                                             java.util.Locale.ROOT)
                                     + " result"),
                    true);
        }

        String outputDigest = ShipDigest.sha256(
                result.assistantText().getBytes(StandardCharsets.UTF_8));
        boolean restoreInterrupt = Thread.interrupted();
        try {
            ShipRun committed;
            if (stage.stage() == Stage.DISCOVERY) {
                committed = controller.completeDiscoveryStage(
                        attempt.run().id(),
                        stage.attempts(),
                        stage.inputDigest(),
                        outputDigest,
                        typed.pipelineId(),
                        typed.materialAmbiguity());
            } else if (stage.stage() == Stage.EXECUTE) {
                Path manifest;
                try {
                    manifest = manifestPath(attempt.run(), attempt.workingDirectory());
                    readBounded(manifest, MAX_BRIEFING_BYTES);
                } catch (IOException e) {
                    return new Step(
                            optimisticFailure(
                                    attempt,
                                    "Pi did not produce the required artifact manifest"),
                            true);
                }
                committed = controller.completeExecuteStage(
                        attempt.run().id(),
                        stage.attempts(),
                        stage.inputDigest(),
                        List.of(manifest),
                        typed.materialAmbiguity());
            } else {
                committed = controller.completeStage(
                        attempt.run().id(),
                        stage.stage(),
                        stage.attempts(),
                        stage.inputDigest(),
                        outputDigest,
                        List.of(),
                        typed.materialAmbiguity());
            }
            return new Step(committed, true);
        } catch (ShipController.Failure e) {
            if ("stage-result-invalid".equals(e.code())) {
                return new Step(
                        optimisticFailure(
                                attempt,
                                "Pi result conflicts with controller-owned stage state"),
                        true);
            }
            if (!"stale-stage-attempt".equals(e.code())) {
                throw e;
            }
            ShipRun current = controller.status(attempt.run().id());
            StageRecord recorded = current.stage(stage.stage());
            if (recorded.attempts() == stage.attempts()
                    && stage.inputDigest().equals(recorded.inputDigest())
                    && (recorded.status() == StageStatus.COMPLETED
                            || recorded.status() == StageStatus.FAILED)) {
                return new Step(current, true);
            }
            throw e;
        } finally {
            if (restoreInterrupt) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private Step validate(StageAttempt attempt)
            throws IOException, InterruptedException {
        try {
            ArtifactPolicy policy = acceptedPolicy(attempt);
            requireDistributionPolicy(policy);
            Path workspace = executeWorkspace(attempt.run());
            Path manifest = manifestPath(attempt.run(), workspace);
            CatalogTarget target = new CatalogTarget(
                    policy.runtime(),
                    policy.camelVersion(),
                    policy.platformVersion(),
                    policy.springBootVersion());
            Result executeResult = completedPiResult(
                    attempt, attempt.run().stage(Stage.EXECUTE));
            ShipLocalStamp.ToolVersion pi = new ShipLocalStamp.ToolVersion(
                    "pi",
                    executeResult.evidence().executable(),
                    executeResult.version(),
                    executeResult.support(),
                    executeResult.warning());
            ShipMainValidator.Result validated = validator.validate(
                    attempt.run().id(),
                    workspace,
                    manifest,
                    policy,
                    catalogs.snapshot(target),
                    attempt.evidenceDirectory(),
                    pi,
                    executeResult.node(),
                    clock);
            return new Step(
                    commitValidation(attempt, validated.stamp()),
                    true);
        } catch (SessionBusyException e) {
            throw e;
        } catch (IOException e) {
            requireNotInterrupted();
            return new Step(
                    optimisticFailure(
                            attempt,
                            "Ship validation could not run"),
                    true);
        }
    }

    private Request request(StageAttempt attempt) throws IOException {
        Path briefing = briefing(attempt);
        Path policyContract = attempt.stage().stage() == Stage.PLAN
                ? contract(
                        attempt.inputDirectory(),
                        "artifact-policy",
                        policyContract())
                : null;
        Path manifestSchema = attempt.stage().stage() == Stage.EXECUTE
                ? contract(
                        attempt.inputDirectory(),
                        "artifact-manifest-schema",
                        manifestSchema())
                : null;
        String workerInputDigest = workerInputDigest(attempt.stage());
        return new Request(
                attempt.run().id(),
                attempt.stage().stage(),
                attempt.stage().attempts(),
                attempt.workingDirectory(),
                attempt.sessionDirectory(),
                attempt.evidenceDirectory(),
                workerInputDigest,
                acceptExperimental,
                prompt(
                        attempt,
                        briefing,
                        policyContract,
                        manifestSchema,
                        workerInputDigest));
    }

    private byte[] policyContract() throws IOException {
        ArtifactPolicy fixed = new ArtifactPolicy(
                "main",
                distribution.camelMainVersion(),
                null,
                null,
                "yaml",
                "simple",
                distribution.citrusVersion(),
                CitrusDependencyPolicy.required(
                        distribution.citrusVersion()),
                JavaPolicy.FORBIDDEN,
                List.of(),
                List.of(),
                true,
                true);
        return JSON.writerWithDefaultPrettyPrinter()
                .writeValueAsBytes(fixed);
    }

    private static byte[] manifestSchema() throws IOException {
        try (InputStream input = ShipCoordinator.class.getResourceAsStream(
                MANIFEST_SCHEMA_RESOURCE)) {
            if (input == null) {
                throw new IOException(
                        "Bundled Ship artifact manifest schema is missing");
            }
            byte[] schema = input.readNBytes(MAX_BRIEFING_BYTES + 1);
            if (schema.length == 0
                    || schema.length > MAX_BRIEFING_BYTES) {
                throw new IOException(
                        "Bundled Ship artifact manifest schema is invalid");
            }
            return schema;
        }
    }

    private static Path contract(
            Path directory, String name, byte[] content)
            throws IOException {
        String digest = ShipDigest.sha256(content)
                .substring("sha256:".length());
        return writeOnce(
                directory.resolve(name + '-' + digest + ".json"),
                content);
    }

    private String workerInputDigest(StageRecord stage)
            throws IOException {
        String contract = switch (stage.stage()) {
            case PLAN -> "plan:"
                         + PLAN_CONTRACT_VERSION + ':'
                         + ShipDigest.sha256(policyContract());
            case EXECUTE -> "execute:"
                            + ShipMainValidator.CONTRACT_VERSION + ':'
                            + ShipDigest.sha256(manifestSchema());
            default -> null;
        };
        if (contract == null) {
            return stage.inputDigest();
        }
        return ShipDigest.sha256(
                (stage.inputDigest() + '\n' + contract)
                        .getBytes(StandardCharsets.UTF_8));
    }

    private Path briefing(StageAttempt attempt) throws IOException {
        StringBuilder text = new StringBuilder();
        text.append("# Controller input\n\n");
        text.append("Run: ").append(attempt.run().id()).append('\n');
        text.append("Stage: ").append(attempt.stage().stage()).append('\n');
        text.append("Pipeline: ")
                .append(attempt.run().pipelineId() == null
                        ? "not assigned"
                        : attempt.run().pipelineId())
                .append('\n');
        text.append("Input digest: ").append(attempt.stage().inputDigest())
                .append("\n\n## Initial context\n");
        if (attempt.run().context().sources().isEmpty()) {
            text.append("\nNo initial context was supplied.\n");
        }
        for (int index = 0;
             index < attempt.run().context().sources().size();
             index++) {
            ShipContext.Source source = attempt.run().context().sources().get(index);
            text.append("\n### Source ").append(index + 1)
                    .append(" (").append(source.kind()).append(")\n\n");
            if (source.kind() == ShipContext.Kind.TEXT) {
                text.append(source.value()).append('\n');
            } else {
                text.append("Path: ").append(source.value()).append("\n\n");
                byte[] content = readBounded(
                        Path.of(source.value()),
                        ShipContext.MAX_DOCUMENT_BYTES);
                if (!source.digest().equals(ShipDigest.sha256(content))) {
                    throw new IOException(
                            "Ship context document changed before Pi composition");
                }
                text.append(new String(content, StandardCharsets.UTF_8))
                        .append('\n');
            }
        }

        for (Stage stage : Stage.values()) {
            if (stage.ordinal() >= attempt.stage().stage().ordinal()) {
                break;
            }
            StageRecord predecessor = attempt.run().stage(stage);
            text.append("\n## ").append(stage).append(" result\n\n");
            if (predecessor.attempts() > 0) {
                ShipStageResult result = ShipStageResult.parse(
                        stage,
                        completedPiResult(attempt, predecessor).assistantText());
                text.append(result.report()).append('\n');
                if (result.artifactPolicy() != null) {
                    text.append("\nApproved artifact policy:\n\n```json\n")
                            .append(JSON.writeValueAsString(
                                    result.artifactPolicy()))
                            .append("\n```\n");
                }
            } else {
                appendImportedArtifacts(
                        text,
                        predecessor,
                        Path.of(attempt.run().projectDirectory()));
            }
            requireBriefingSize(text);
        }
        byte[] encoded = text.toString().getBytes(StandardCharsets.UTF_8);
        if (encoded.length == 0 || encoded.length > MAX_BRIEFING_BYTES) {
            throw new IOException("Ship Pi briefing exceeds its size limit");
        }
        if (ChangedWorkspaceSecretScanner.containsSensitiveValue(
                encoded, environment)) {
            throw new IOException(
                    "Ship coordinator input contains a sensitive environment value");
        }
        String suffix = attempt.stage().inputDigest()
                .substring("sha256:".length());
        Path target = attempt.inputDirectory()
                .resolve(attempt.stage().stage().name().toLowerCase(
                        java.util.Locale.ROOT) + "-" + suffix + ".md");
        return writeOnce(target, encoded);
    }

    private Result completedPiResult(
            StageAttempt current, StageRecord completed)
            throws IOException {
        if (completed.attempts() <= 0) {
            throw new IOException(
                    "Imported Ship stage has no Pi result: " + completed.stage());
        }
        Path working = completed.stage() == Stage.EXECUTE
                ? executeWorkspace(current.run())
                : Path.of(current.run().projectDirectory());
        Path evidence = current.runDirectory()
                .resolve("evidence")
                .resolve(completed.stage().name().toLowerCase(
                        java.util.Locale.ROOT) + "-" + completed.attempts());
        Request request = new Request(
                current.run().id(),
                completed.stage(),
                completed.attempts(),
                working,
                current.sessionDirectory(),
                evidence,
                workerInputDigest(completed),
                true,
                "Recover the controller-owned completed Ship stage result.");
        Result result = worker.recover(request).orElseThrow(
                () -> new StalePredecessorException(
                        "Durable Pi result is missing for completed stage "
                                                    + completed.stage()));
        if (result.outcome() != Outcome.SUCCEEDED) {
            throw new StalePredecessorException(
                    "Completed Ship stage has a non-successful Pi result");
        }
        if (completed.stage() != Stage.EXECUTE
                && !completed.outputDigest().equals(ShipDigest.sha256(
                        result.assistantText().getBytes(StandardCharsets.UTF_8)))) {
            throw new StalePredecessorException(
                    "Durable Pi result differs from the committed "
                                                + completed.stage() + " output");
        }
        return result;
    }

    private ArtifactPolicy acceptedPolicy(StageAttempt attempt)
            throws IOException {
        StageRecord plan = attempt.run().stage(Stage.PLAN);
        if (plan.attempts() <= 0) {
            throw new IOException(
                    "Validation requires a controller-approved generated artifact policy");
        }
        ShipStageResult result = ShipStageResult.parse(
                Stage.PLAN,
                completedPiResult(attempt, plan).assistantText());
        return result.artifactPolicy();
    }

    private void requireDistributionPolicy(ArtifactPolicy policy) {
        if (policy == null
                || !"main".equals(policy.runtime())
                || !distribution.camelMainVersion().equals(
                        policy.camelVersion())
                || policy.platformVersion() != null
                || policy.springBootVersion() != null
                || !"yaml".equals(policy.routeDsl())
                || !"simple".equals(policy.expressionLanguage())
                || !distribution.citrusVersion().equals(
                        policy.citrusVersion())
                || !CitrusDependencyPolicy.required(
                        distribution.citrusVersion())
                        .equals(policy.citrusDependencies())
                || policy.javaPolicy() != JavaPolicy.FORBIDDEN
                || !policy.approvedJavaExceptions().isEmpty()
                || !policy.citrusTestsRequired()
                || !policy.oneCitrusTestPerRoute()) {
            throw new IllegalArgumentException(
                    "Ship plan differs from the controller's Main policy");
        }
    }

    private static String prompt(
            StageAttempt attempt,
            Path briefing,
            Path policyContract,
            Path manifestSchema,
            String workerInputDigest)
            throws IOException {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are the bounded Pi worker for Camel Kit Ship stage ")
                .append(attempt.stage().stage()).append(".\n")
                .append("Read the complete controller input from this exact file: ")
                .append(briefing).append("\n")
                .append("This is controller attempt ")
                .append(attempt.stage().attempts()).append(".\n")
                .append("The authoritative input digest is ")
                .append(attempt.stage().inputDigest()).append(".\n")
                .append("The bounded worker input identity is ")
                .append(workerInputDigest).append(".\n");
        switch (attempt.stage().stage()) {
            case DISCOVERY -> {
                prompt.append(
                        "Analyze supplied material before unresolved requirements. ");
                if (attempt.run().pipelineId() == null) {
                    prompt.append(
                            "Choose one unused pipeline ID matching "
                                  + "<three-or-more-digits>-<lowercase-slug>.\n");
                } else {
                    prompt.append("Reuse the controller-bound pipeline ID ")
                            .append(attempt.run().pipelineId())
                            .append(".\n");
                }
            }
            case DESIGN -> prompt.append(
                    "Produce the complete Camel integration design. "
                                         + "Do not change application source.\n");
            case PLAN -> prompt.append(
                    "Produce an implementation plan and its independent artifact policy. "
                                       + "Read the fixed controller policy from ")
                    .append(Objects.requireNonNull(policyContract))
                    .append(
                            ". Copy every fixed field exactly and replace the empty routes array "
                            + "with one object per planned route using fields routeId, routePath, "
                            + "and citrusTestPath. For each route, routePath's file name must "
                            + "equal <routeId>.camel.yaml and citrusTestPath must equal "
                            + "test/<routeId>.camel.it.yaml. Sort routes canonically by routeId, "
                            + "then routePath, then citrusTestPath.\n");
            case EXECUTE -> prompt.append(
                    "Implement only inside the supplied working directory. "
                                          + "Read and satisfy the exact bundled manifest JSON Schema at ")
                    .append(Objects.requireNonNull(manifestSchema))
                    .append(
                            ". Match the approved PLAN policy in the controller input. "
                            + "Write the strict artifact manifest to ")
                    .append(manifestPath(
                            attempt.run(), attempt.workingDirectory()))
                    .append(".\n");
            case VALIDATE -> throw new IOException(
                    "Pi does not decide deterministic validation");
        }
        prompt.append(
                "Return only one JSON object with exactly these fields: "
                      + "schemaVersion, pipelineId, report, artifactPolicy, "
                      + "materialAmbiguity. Use schemaVersion 1. "
                      + "Wire types are strict: schemaVersion is integer 1; "
                      + "pipelineId is a JSON string only for DISCOVERY and JSON null otherwise; "
                      + "report is one non-empty JSON string, never an object or array; "
                      + "artifactPolicy is the policy object only for PLAN and JSON null otherwise; "
                      + "materialAmbiguity is one JSON boolean, never an object or array. "
                      + "Use JSON null for fields not allowed by this stage; "
                      + "do not use Markdown fences or surrounding prose.");
        return prompt.toString();
    }

    private static Path manifestPath(ShipRun run, Path candidate)
            throws IOException {
        if (run.pipelineId() == null) {
            throw new IOException(
                    "Ship execution requires a controller-approved pipeline ID");
        }
        return candidate.resolve("docs/camel-kit")
                .resolve(run.pipelineId())
                .resolve("artifact-manifest.json")
                .toAbsolutePath()
                .normalize();
    }

    private static Path executeWorkspace(ShipRun run) throws IOException {
        for (ShipRun.ArtifactRef artifact : run.stage(Stage.EXECUTE).artifacts()) {
            Path candidate = Path.of(artifact.path());
            final BasicFileAttributes attributes;
            try {
                attributes = Files.readAttributes(
                        candidate,
                        BasicFileAttributes.class,
                        LinkOption.NOFOLLOW_LINKS);
            } catch (NoSuchFileException e) {
                continue;
            }
            if (attributes.isDirectory()) {
                return candidate;
            }
        }
        throw new StalePredecessorException(
                "Ship EXECUTE workspace evidence is missing");
    }

    private static void appendImportedArtifacts(
            StringBuilder text, StageRecord predecessor, Path project)
            throws IOException {
        if (predecessor.artifacts().isEmpty()) {
            text.append("No imported artifact content.\n");
            return;
        }
        for (ShipRun.ArtifactRef artifact : predecessor.artifacts()) {
            Path path = Path.of(artifact.path());
            if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                continue;
            }
            byte[] encoded = readBounded(path, MAX_BRIEFING_BYTES);
            if (!artifact.digest().equals(ShipDigest.sha256(encoded))) {
                throw new IOException(
                        "Imported Ship artifact changed before Pi composition");
            }
            text.append("Artifact: ")
                    .append(project.relativize(path))
                    .append("\n\n")
                    .append(new String(encoded, StandardCharsets.UTF_8))
                    .append('\n');
            requireBriefingSize(text);
        }
    }

    private ShipRun optimisticFailure(StageAttempt attempt, String message) {
        boolean restoreInterrupt = Thread.interrupted();
        try {
            return controller.failStage(
                    attempt.run().id(),
                    attempt.stage().stage(),
                    attempt.stage().attempts(),
                    attempt.stage().inputDigest(),
                    truncate(message));
        } catch (ShipController.Failure e) {
            if (!"stale-stage-attempt".equals(e.code())) {
                throw e;
            }
            return controller.status(attempt.run().id());
        } finally {
            if (restoreInterrupt) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private ShipRun commitValidation(
            StageAttempt attempt, ShipLocalStamp stamp) {
        boolean restoreInterrupt = Thread.interrupted();
        try {
            return controller.completeValidationStage(
                    attempt.run().id(),
                    attempt.stage().attempts(),
                    attempt.stage().inputDigest(),
                    stamp);
        } finally {
            if (restoreInterrupt) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private AttemptLease tryCoordinatorLease(String runId)
            throws IOException {
        Path root = stateRoot.toRealPath();
        Path run = stateRoot.resolve(runId).toAbsolutePath().normalize();
        if (Files.isSymbolicLink(run)
                || !Files.isDirectory(run, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException(
                    "Ship coordinator run directory is missing or unsafe");
        }
        Path realRun = run.toRealPath(LinkOption.NOFOLLOW_LINKS);
        if (!realRun.equals(root.resolve(runId))) {
            throw new IOException(
                    "Ship coordinator run directory escaped its state root");
        }
        return tryLease(
                realRun.resolve(".coordinator.lock"),
                "Ship coordinator lease");
    }

    private static AttemptLease tryLease(Path lockPath, String label)
            throws IOException {
        String userName = System.getProperty("user.name");
        if (userName == null || userName.isBlank()) {
            throw new IOException(
                    "Could not determine the current user for " + label);
        }
        UserPrincipal owner = lockPath.getFileSystem()
                .getUserPrincipalLookupService()
                .lookupPrincipalByName(userName);
        if (!owner.equals(Files.getOwner(
                lockPath.getParent(), LinkOption.NOFOLLOW_LINKS))) {
            throw new IOException(
                    label + " directory must be owned by the current user");
        }
        FileChannel channel = FileChannel.open(
                lockPath,
                Set.of(
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE,
                        LinkOption.NOFOLLOW_LINKS),
                PosixFilePermissions.asFileAttribute(
                        PosixFilePermissions.fromString("rw-------")));
        try {
            PosixFileAttributes attributes = Files.readAttributes(
                    lockPath,
                    PosixFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
            if (attributes.isSymbolicLink()
                    || !attributes.isRegularFile()
                    || !owner.equals(attributes.owner())
                    || !PosixFilePermissions.fromString("rw-------").equals(
                            attributes.permissions())) {
                throw new IOException(
                        label
                                      + " must be a current-user-owned 0600 regular file");
            }
            FileLock lock;
            try {
                lock = channel.tryLock();
            } catch (OverlappingFileLockException e) {
                channel.close();
                return null;
            }
            if (lock == null) {
                channel.close();
                return null;
            }
            return new AttemptLease(channel, lock);
        } catch (IOException | RuntimeException e) {
            if (channel.isOpen()) {
                channel.close();
            }
            throw e;
        }
    }

    private static boolean concurrentTransition(
            ShipController.Failure failure) {
        return "operation-in-progress".equals(failure.code())
                || "stale-stage-attempt".equals(failure.code());
    }

    private static Path writeOnce(Path target, byte[] encoded)
            throws IOException {
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            byte[] current = readBounded(target, MAX_BRIEFING_BYTES);
            if (!java.security.MessageDigest.isEqual(current, encoded)) {
                throw new IOException(
                        "Controller-owned Pi briefing differs from its durable input");
            }
            return target.toRealPath(LinkOption.NOFOLLOW_LINKS);
        }
        Path temporary = Files.createTempFile(
                target.getParent(),
                ".briefing-",
                ".tmp",
                PosixFilePermissions.asFileAttribute(
                        PosixFilePermissions.fromString("rw-------")));
        try {
            try (FileChannel channel = FileChannel.open(
                    temporary,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    LinkOption.NOFOLLOW_LINKS)) {
                ByteBuffer content = ByteBuffer.wrap(encoded);
                while (content.hasRemaining()) {
                    channel.write(content);
                }
                channel.force(true);
            }
            try {
                Files.createLink(target, temporary);
            } catch (FileAlreadyExistsException e) {
                byte[] current = readBounded(target, MAX_BRIEFING_BYTES);
                if (!java.security.MessageDigest.isEqual(current, encoded)) {
                    throw new IOException(
                            "Concurrent Ship briefing differs from its input",
                            e);
                }
            }
            return target.toRealPath(LinkOption.NOFOLLOW_LINKS);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static byte[] readBounded(Path path, int maximum)
            throws IOException {
        if (Files.isSymbolicLink(path)
                || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Ship coordinator input is missing or unsafe");
        }
        try (InputStream input = Files.newInputStream(
                path, LinkOption.NOFOLLOW_LINKS)) {
            byte[] encoded = input.readNBytes(maximum + 1);
            if (encoded.length > maximum) {
                throw new IOException(
                        "Ship coordinator input exceeds its size limit");
            }
            return encoded;
        }
    }

    private static void requireBriefingSize(StringBuilder text)
            throws IOException {
        if (text.length() > MAX_BRIEFING_BYTES
                || text.toString().getBytes(StandardCharsets.UTF_8).length
                   > MAX_BRIEFING_BYTES) {
            throw new IOException("Ship Pi briefing exceeds its size limit");
        }
    }

    private String truncate(String value) {
        String message = value == null || value.isBlank()
                ? "Ship stage failed"
                : value.replace('\0', ' ').strip();
        if (ChangedWorkspaceSecretScanner.containsSensitiveValue(
                message.getBytes(StandardCharsets.UTF_8), environment)) {
            return "Failed";
        }
        return message.length() <= 1024
                ? message
                : message.substring(0, 1024);
    }

    private static String safeMessage(IOException failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank()
                ? failure.getClass().getSimpleName()
                : message;
    }

    private static void requireNotInterrupted()
            throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException(
                    "Interrupted before advancing the Ship run");
        }
    }

    private static final class StalePredecessorException extends IOException {

        private static final long serialVersionUID = 1L;

        private StalePredecessorException(String message) {
            super(message);
        }
    }

    private record Step(ShipRun run, boolean progressed) {
    }

    @FunctionalInterface
    interface CatalogProvider {

        Snapshot snapshot(CatalogTarget target) throws IOException;
    }

    private static final class AbortWatcher implements AutoCloseable {

        private final Thread owner;
        private final Thread watcher;
        private volatile boolean closed;

        private AbortWatcher(ShipController controller, String runId) {
            owner = Thread.currentThread();
            watcher = new Thread(
                    () -> watch(controller, runId),
                    "camel-kit-ship-abort-" + runId);
            watcher.setDaemon(true);
            watcher.start();
        }

        private void watch(ShipController controller, String runId) {
            try {
                while (!closed) {
                    try {
                        if (controller.status(runId).status()
                            == RunStatus.ABORTED) {
                            if (!closed) {
                                owner.interrupt();
                            }
                            return;
                        }
                    } catch (ShipController.Failure ignored) {
                        // Retry transient reads; the lease owner remains authoritative.
                    }
                    Thread.sleep(ABORT_POLL_MILLIS);
                }
            } catch (InterruptedException ignored) {
                // Closing the watcher.
            }
        }

        @Override
        public void close() {
            closed = true;
            watcher.interrupt();
            boolean restoreInterrupt = Thread.interrupted();
            while (watcher.isAlive()) {
                try {
                    watcher.join();
                } catch (InterruptedException e) {
                    restoreInterrupt = true;
                }
            }
            if (restoreInterrupt) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private record AttemptLease(FileChannel channel, FileLock lock)
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
}
