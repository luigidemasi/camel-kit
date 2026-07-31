package io.github.luigidemasi.camelkit.command.ship;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import io.github.luigidemasi.camelkit.config.DistributionConfig;
import io.github.luigidemasi.camelkit.ship.controller.ShipCoordinator;
import io.github.luigidemasi.camelkit.ship.controller.ShipRun;

/**
 * Resolves the local Pi runtime and builds the coordinator-backed workflow.
 *
 * <p>
 * Executable paths come from explicit options or PATH discovery; the maintained Pi/Node support pins always come from
 * the bundled distribution inside {@link ShipCoordinator}, so no option here can promote an unmaintained version beyond
 * an experimental run. The bundled distribution also drives artifact policy for now: the user config cascade prints
 * progress to stdout while loading, which would corrupt the command's summary contract, so wiring it up belongs to the
 * registration slice.
 * </p>
 */
final class ShipRuntime implements ShipCommand.WorkflowLauncher {

    // Mirrored by the --stage-timeout help text in ShipCommand; ShipCommandTest pins the two together.
    static final Duration DEFAULT_STAGE_TIMEOUT = Duration.ofMinutes(10);
    static final String CATALOG_REPOSITORY = "catalog-repository";

    private final Path stateRoot;
    private final String searchPath;

    ShipRuntime(Path stateRoot) {
        this(stateRoot, System.getenv("PATH"));
    }

    ShipRuntime(Path stateRoot, String searchPath) {
        this.stateRoot = Objects.requireNonNull(stateRoot, "state root")
                .toAbsolutePath()
                .normalize();
        this.searchPath = searchPath;
    }

    @Override
    public ShipCommand.Workflow launch(ShipCommand.RuntimeSettings settings) {
        requireLinux();
        ShipCommand.RuntimeSettings resolved = resolve(settings);
        ShipCoordinator coordinator = new ShipCoordinator(
                stateRoot,
                resolved.piExecutable(),
                resolved.nodeExecutable(),
                resolved.mavenRepository(),
                DistributionConfig.loadBundled(),
                resolved.stageTimeout(),
                resolved.acceptExperimental());
        return new ShipCommand.Workflow() {

            @Override
            public ShipRun run(String runId) throws IOException, InterruptedException {
                return coordinator.run(runId);
            }

            @Override
            public ShipRun resume(String runId) throws IOException, InterruptedException {
                return coordinator.resume(runId);
            }
        };
    }

    /** Returns the settings with discovery applied and every default filled in. */
    ShipCommand.RuntimeSettings resolve(ShipCommand.RuntimeSettings settings) {
        return new ShipCommand.RuntimeSettings(
                resolveExecutable(settings.piExecutable(), "pi", "Pi"),
                resolveExecutable(settings.nodeExecutable(), "node", "Node"),
                settings.mavenRepository() == null
                        ? stateRoot.resolve(CATALOG_REPOSITORY)
                        : settings.mavenRepository(),
                settings.stageTimeout() == null ? DEFAULT_STAGE_TIMEOUT : settings.stageTimeout(),
                settings.acceptExperimental());
    }

    private Path resolveExecutable(Path configured, String name, String label) {
        if (configured != null) {
            return configured;
        }
        return findOnPath(searchPath, name).orElseThrow(() -> new IllegalArgumentException(
                label + " is missing or not executable; install " + label
                                                                                           + " and configure its executable path"));
    }

    static Optional<Path> findOnPath(String searchPath, String name) {
        if (searchPath == null || searchPath.isBlank()) {
            return Optional.empty();
        }
        for (String entry : searchPath.split(File.pathSeparator, -1)) {
            if (entry.isBlank()) {
                continue;
            }
            Path directory = Path.of(entry);
            // Relative PATH entries would resolve against the current (possibly untrusted)
            // project directory; never discover a stage executable there.
            if (!directory.isAbsolute()) {
                continue;
            }
            Path candidate = directory.resolve(name);
            if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    // Mirrors PiWorker.requireLinux (private there, and only checked inside run); this copy is
    // what makes the OS gate fire at launch, before any run state exists. Keep messages identical.
    private static void requireLinux() {
        String os = System.getProperty("os.name", "");
        if (!os.toLowerCase(Locale.ROOT).contains("linux")) {
            throw new IllegalStateException("The first Pi Ship worker supports Linux only");
        }
    }
}
