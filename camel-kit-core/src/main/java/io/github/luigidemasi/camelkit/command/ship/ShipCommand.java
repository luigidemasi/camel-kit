package io.github.luigidemasi.camelkit.command.ship;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;

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

/** Local Ship controller command. Kept unregistered until the worker path passes its live gates. */
@Command(
         name = "ship",
         mixinStandardHelpOptions = true,
         description = "Start, inspect, resume, or abort a local Camel Ship run")
public final class ShipCommand implements Callable<Integer> {

    private final ShipController controller;

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

    public ShipCommand() {
        this(new ShipController(ShipController.defaultStateRoot()));
    }

    ShipCommand(ShipController controller) {
        this.controller = controller;
    }

    @Override
    public Integer call() {
        validateArguments();
        try {
            ShipRun run = execute();
            printSummary(run);
            return 0;
        } catch (ShipController.Failure e) {
            PrintWriter writer = spec.commandLine().getErr();
            writer.println("Error [" + e.code() + "]: " + e.getMessage());
            writer.flush();
            return 1;
        }
    }

    private ShipRun execute() {
        if (operation == null) {
            return controller.start(projectDirectory, selectedOversight(), inputs());
        }
        if (operation.resume != null) {
            return controller.resume(operation.resume);
        }
        if (operation.status != null) {
            return controller.status(operation.status);
        }
        if (operation.abort != null) {
            return controller.abort(operation.abort);
        }
        return controller.startFrom(projectDirectory, operation.startFrom, selectedOversight(), inputs());
    }

    private void validateArguments() {
        if (operation != null && operation.startFrom == null
                && (oversight != null || !contextArguments.isEmpty())) {
            throw new ParameterException(
                    spec.commandLine(),
                    "--ask, --text, and --document are only valid when starting a run");
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

    static final class Operation {

        @Option(names = "--resume", paramLabel = "RUN_ID", description = "Resume an existing run")
        String resume;

        @Option(
                names = "--start-from",
                paramLabel = "STAGE",
                converter = StageConverter.class,
                description = "Start at discovery, design, plan, execute, or validate")
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
                        "expected discovery, design, plan, execute, or validate");
            }
        }
    }
}
