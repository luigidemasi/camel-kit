package io.github.luigidemasi.camelkit.command.ship;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.channels.ClosedByInterruptException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.luigidemasi.camelkit.ship.context.ShipContext;
import io.github.luigidemasi.camelkit.ship.controller.ShipController;
import io.github.luigidemasi.camelkit.ship.controller.ShipRun;
import io.github.luigidemasi.camelkit.ship.controller.ShipRun.Oversight;
import io.github.luigidemasi.camelkit.ship.controller.ShipRun.Stage;

import picocli.CommandLine;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;
import picocli.CommandLine.TypeConversionException;

/**
 * Local Ship command. Starting or resuming a run drives the authoritative coordinator workflow; status and abort remain
 * pure controller operations. Kept unregistered until the publication, interaction, and documentation gates pass.
 */
@Command(
         name = "ship",
         mixinStandardHelpOptions = true,
         description = "Start, inspect, resume, or abort a local Camel Ship run")
public final class ShipCommand implements Callable<Integer> {

    private final ShipController controller;
    private final WorkflowLauncher launcher;

    @Spec
    CommandLine.Model.CommandSpec spec;

    @ArgGroup(exclusive = true, multiplicity = "0..1")
    Operation operation;

    @ArgGroup(exclusive = true, multiplicity = "0..*")
    List<ContextArgument> contextArguments = new ArrayList<>();

    @Option(
            names = "--ask",
            paramLabel = "POLICY",
            converter = OversightConverter.class,
            description = "Oversight policy: always, smart, or never")
    Oversight oversight;

    @Option(names = "--project-dir", defaultValue = ".", hidden = true)
    Path projectDirectory;

    @Option(
            names = "--pi",
            paramLabel = "PATH",
            description = "Pi executable (default: discovered on PATH)")
    Path piExecutable;

    @Option(
            names = "--node",
            paramLabel = "PATH",
            description = "Node executable (default: discovered on PATH)")
    Path nodeExecutable;

    @Option(
            names = "--maven-repository",
            paramLabel = "PATH",
            description = "Private Maven repository for validation catalogs"
                          + " (default: under the ship state directory)")
    Path mavenRepository;

    @Option(
            names = "--stage-timeout",
            paramLabel = "DURATION",
            converter = StageTimeoutConverter.class,
            description = "Time limit for one stage attempt, like 90s, 10m, or 1h (default: 10m)")
    Duration stageTimeout;

    @Option(
            names = "--accept-experimental",
            description = "Accept an experimental Pi or Node version after its warning")
    Boolean acceptExperimental;

    public ShipCommand() {
        this(new ShipController(ShipController.defaultStateRoot()),
             new ShipRuntime(ShipController.defaultStateRoot()));
    }

    ShipCommand(ShipController controller, WorkflowLauncher launcher) {
        this.controller = controller;
        this.launcher = launcher;
    }

    @Override
    public Integer call() {
        validateArguments();
        try {
            ShipRun run = execute();
            printSummary(run);
            return workflowOperation() && run.status() == ShipRun.RunStatus.FAILED ? 1 : 0;
        } catch (ShipController.Failure e) {
            return failure(e.code(), e.getMessage());
        } catch (CommandFailure e) {
            return failure(e.code, e.getMessage());
        }
    }

    private ShipRun execute() {
        if (operation != null && operation.status != null) {
            return controller.status(operation.status);
        }
        if (operation != null && operation.abort != null) {
            return controller.abort(operation.abort);
        }
        Workflow workflow = launchWorkflow();
        if (operation != null && operation.resume != null) {
            return awaitWorkflow(workflow::resume, operation.resume);
        }
        ShipRun run = operation == null
                ? controller.start(projectDirectory, selectedOversight(), inputs())
                : controller.startFrom(projectDirectory, operation.startFrom, selectedOversight(), inputs());
        return awaitWorkflow(workflow::run, run.id());
    }

    private boolean workflowOperation() {
        return operation == null || operation.resume != null || operation.startFrom != null;
    }

    private Workflow launchWorkflow() {
        try {
            return launcher.launch(new RuntimeSettings(
                    piExecutable,
                    nodeExecutable,
                    mavenRepository,
                    stageTimeout,
                    Boolean.TRUE.equals(acceptExperimental)));
        } catch (IllegalArgumentException e) {
            throw new CommandFailure("runtime-unavailable", e.getMessage(), e);
        } catch (IllegalStateException e) {
            throw new CommandFailure("runtime-unsupported", e.getMessage(), e);
        }
    }

    private ShipRun awaitWorkflow(WorkflowStep step, String runId) {
        try {
            return step.apply(runId);
        } catch (ShipController.Failure e) {
            // Keep the run reachable: without a list command the error line is the only place
            // the identifier can surface after a post-start failure.
            throw new CommandFailure(e.code(), e.getMessage() + runReference(runId), e);
        } catch (ClosedByInterruptException e) {
            // The abort watcher's interrupt can land inside an interruptible channel operation
            // and surface as this message-less IOException instead of InterruptedException.
            return statusAfterInterrupt(runId);
        } catch (IOException e) {
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            throw new CommandFailure("workflow-failed", message + runReference(runId), e);
        } catch (InterruptedException e) {
            return statusAfterInterrupt(runId);
        }
    }

    /**
     * Reports the latest durable state like --abort does instead of surfacing a stack trace. The interrupt flag may
     * still be set (PiWorker restores it before rethrowing), so it is stashed around the status read — interruptible
     * channels would fail it otherwise — and restored before returning, mirroring the coordinator's own commit pattern.
     */
    private ShipRun statusAfterInterrupt(String runId) {
        Thread.interrupted();
        try {
            return controller.status(runId);
        } finally {
            Thread.currentThread().interrupt();
        }
    }

    private static String runReference(String runId) {
        return " (run " + runId + ")";
    }

    private void validateArguments() {
        if (operation != null && operation.startFrom == null
                && (oversight != null || !contextArguments.isEmpty())) {
            throw new ParameterException(
                    spec.commandLine(),
                    "--ask, --text, and --document are only valid when starting a run");
        }
        if (operation != null && (operation.status != null || operation.abort != null)
                && (piExecutable != null || nodeExecutable != null || mavenRepository != null
                        || stageTimeout != null || acceptExperimental != null)) {
            throw new ParameterException(
                    spec.commandLine(),
                    "--pi, --node, --maven-repository, --stage-timeout, and --accept-experimental"
                                        + " are only valid when starting or resuming a run");
        }
    }

    private Oversight selectedOversight() {
        return oversight == null ? Oversight.SMART : oversight;
    }

    private List<ShipContext.Input> inputs() {
        return contextArguments.stream().map(ContextArgument::input).toList();
    }

    private void printSummary(ShipRun run) {
        PrintWriter writer = spec.commandLine().getOut();
        writer.println("Run: " + run.id());
        writer.println("Status: " + run.status());
        writer.println("Stage: " + run.currentStage());
        writer.println("Oversight: " + run.oversight());
        writer.flush();
    }

    private Integer failure(String code, String message) {
        PrintWriter writer = spec.commandLine().getErr();
        writer.println("Error [" + code + "]: " + message);
        writer.flush();
        return 1;
    }

    /** Runs the authoritative coordinator workflow over an existing run. */
    interface Workflow {

        ShipRun run(String runId) throws IOException, InterruptedException;

        ShipRun resume(String runId) throws IOException, InterruptedException;
    }

    /** Builds the runtime-backed workflow, failing fast before any run state exists. */
    @FunctionalInterface
    interface WorkflowLauncher {

        Workflow launch(RuntimeSettings settings);
    }

    /** Raw runtime option values; the launcher resolves defaults and discovery. */
    record RuntimeSettings(Path piExecutable, Path nodeExecutable, Path mavenRepository,
            Duration stageTimeout, boolean acceptExperimental) {
    }

    @FunctionalInterface
    private interface WorkflowStep {

        ShipRun apply(String runId) throws IOException, InterruptedException;
    }

    private static final class CommandFailure extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final String code;

        CommandFailure(String code, String message, Throwable cause) {
            super(message, cause);
            this.code = code;
        }
    }

    static final class Operation {

        @Option(names = "--resume", paramLabel = "RUN_ID", description = "Resume an existing run")
        String resume;

        @Option(
                names = "--start-from",
                paramLabel = "STAGE",
                converter = StageConverter.class,
                description = "Start at discovery, design, or plan")
        Stage startFrom;

        @Option(names = "--status", paramLabel = "RUN_ID", description = "Show an existing run")
        String status;

        @Option(names = "--abort", paramLabel = "RUN_ID", description = "Abort an existing run")
        String abort;
    }

    static final class ContextArgument {

        @Option(names = "--text", paramLabel = "TEXT", description = "Add text context")
        String text;

        @Option(names = "--document", paramLabel = "PATH", description = "Add document context")
        Path document;

        ShipContext.Input input() {
            return document == null
                    ? new ShipContext.TextInput(text)
                    : new ShipContext.DocumentInput(document);
        }
    }

    static final class OversightConverter implements ITypeConverter<Oversight> {

        @Override
        public Oversight convert(String value) {
            try {
                return Oversight.valueOf(value.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new TypeConversionException("expected always, smart, or never");
            }
        }
    }

    static final class StageConverter implements ITypeConverter<Stage> {

        @Override
        public Stage convert(String value) {
            try {
                return Stage.valueOf(value.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new TypeConversionException(
                        "expected discovery, design, or plan");
            }
        }
    }

    static final class StageTimeoutConverter implements ITypeConverter<Duration> {

        private static final Pattern DURATION = Pattern.compile("([1-9][0-9]*)([smh])");

        @Override
        public Duration convert(String value) {
            Matcher matcher = DURATION.matcher(value);
            if (!matcher.matches()) {
                throw new TypeConversionException("expected a duration like 90s, 10m, or 1h");
            }
            try {
                long amount = Long.parseLong(matcher.group(1));
                return switch (matcher.group(2)) {
                    case "s" -> Duration.ofSeconds(amount);
                    case "m" -> Duration.ofMinutes(amount);
                    default -> Duration.ofHours(amount);
                };
            } catch (NumberFormatException | ArithmeticException e) {
                throw new TypeConversionException("expected a duration like 90s, 10m, or 1h");
            }
        }
    }
}
