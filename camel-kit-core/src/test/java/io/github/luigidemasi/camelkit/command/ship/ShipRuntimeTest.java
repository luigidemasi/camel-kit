package io.github.luigidemasi.camelkit.command.ship;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipRuntimeTest {

    @TempDir
    Path tempDir;

    @Test
    @EnabledOnOs(OS.LINUX)
    void findsTheFirstExecutableRegularFileOnThePath() throws Exception {
        Path first = Files.createDirectory(tempDir.resolve("first"));
        Path second = Files.createDirectory(tempDir.resolve("second"));
        Files.createDirectory(first.resolve("pi"));
        writeExecutable(second.resolve("pi"));
        String searchPath = String.join(File.pathSeparator,
                first.toString(), "", second.toString());

        Optional<Path> found = ShipRuntime.findOnPath(searchPath, "pi");

        assertEquals(Optional.of(second.resolve("pi")), found);
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void skipsNonExecutableCandidates() throws Exception {
        Path directory = Files.createDirectory(tempDir.resolve("bin"));
        Files.writeString(directory.resolve("pi"), "#!/bin/sh\n");
        Files.setPosixFilePermissions(
                directory.resolve("pi"), PosixFilePermissions.fromString("rw-------"));

        assertEquals(Optional.empty(), ShipRuntime.findOnPath(directory.toString(), "pi"));
    }

    @Test
    void returnsEmptyForMissingOrBlankSearchPaths() {
        assertEquals(Optional.empty(), ShipRuntime.findOnPath(null, "pi"));
        assertEquals(Optional.empty(), ShipRuntime.findOnPath("  ", "pi"));
        assertEquals(Optional.empty(),
                ShipRuntime.findOnPath(tempDir.resolve("absent").toString(), "pi"));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void ignoresRelativeSearchPathEntries() throws Exception {
        Path relative = Files.createDirectory(tempDir.resolve("rel-bin"));
        writeExecutable(relative.resolve("pi"));
        String relativeEntry = Path.of("").toAbsolutePath().relativize(relative).toString();

        assertEquals(Optional.empty(), ShipRuntime.findOnPath(relativeEntry, "pi"),
                "relative PATH entries must never resolve against the working directory");
        assertEquals(Optional.of(relative.resolve("pi")),
                ShipRuntime.findOnPath(relative.toString(), "pi"),
                "the same directory is discoverable through its absolute entry");
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void resolvesExplicitOptionsOverDiscoveryAndAppliesDefaults() throws Exception {
        Path binDirectory = Files.createDirectory(tempDir.resolve("bin"));
        writeExecutable(binDirectory.resolve("pi"));
        writeExecutable(binDirectory.resolve("node"));
        Path state = tempDir.resolve("state");
        Path explicitPi = tempDir.resolve("explicit-pi");
        ShipRuntime runtime = new ShipRuntime(state, binDirectory.toString());

        ShipCommand.RuntimeSettings defaults = runtime.resolve(new ShipCommand.RuntimeSettings(
                null, null, null, null, false, null, List.of()));
        assertEquals(binDirectory.resolve("pi"), defaults.piExecutable());
        assertEquals(binDirectory.resolve("node"), defaults.nodeExecutable());
        assertEquals(state.toAbsolutePath().normalize().resolve(ShipRuntime.CATALOG_REPOSITORY),
                defaults.mavenRepository());
        assertEquals(ShipRuntime.DEFAULT_STAGE_TIMEOUT, defaults.stageTimeout());
        assertFalse(defaults.acceptExperimental());

        ShipCommand.RuntimeSettings explicit = runtime.resolve(new ShipCommand.RuntimeSettings(
                explicitPi, explicitPi, tempDir.resolve("repo"), Duration.ofSeconds(90), true,
                tempDir.resolve("config"), List.of("camel.main.version=9.9.9")));
        assertEquals(explicitPi, explicit.piExecutable());
        assertEquals(explicitPi, explicit.nodeExecutable());
        assertEquals(tempDir.resolve("repo"), explicit.mavenRepository());
        assertEquals(Duration.ofSeconds(90), explicit.stageTimeout());
        assertTrue(explicit.acceptExperimental());
        assertEquals(tempDir.resolve("config"), explicit.configFile());
        assertEquals(List.of("camel.main.version=9.9.9"), explicit.configProperties());
    }

    @Test
    void missingPiOnThePathReportsCanonicalInstallationGuidance() {
        ShipRuntime runtime = new ShipRuntime(tempDir.resolve("state"), "");

        IllegalArgumentException missing = assertThrows(
                IllegalArgumentException.class,
                () -> runtime.resolve(new ShipCommand.RuntimeSettings(
                        null, null, null, null, false, null, List.of())));

        assertEquals(
                "Pi is missing or not executable; install Pi and configure its executable path",
                missing.getMessage());
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void missingNodeOnThePathReportsCanonicalInstallationGuidance() throws Exception {
        Path binDirectory = Files.createDirectory(tempDir.resolve("bin"));
        writeExecutable(binDirectory.resolve("pi"));
        ShipRuntime runtime = new ShipRuntime(tempDir.resolve("state"), binDirectory.toString());

        IllegalArgumentException missing = assertThrows(
                IllegalArgumentException.class,
                () -> runtime.resolve(new ShipCommand.RuntimeSettings(
                        null, null, null, null, false, null, List.of())));

        assertEquals(
                "Node is missing or not executable; install Node and configure its executable path",
                missing.getMessage());
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void launchFailsFastOnAMissingConfiguredExecutable() {
        ShipRuntime runtime = new ShipRuntime(tempDir.resolve("state"), "");
        Path absent = tempDir.resolve("absent-pi");

        IllegalArgumentException missing = assertThrows(
                IllegalArgumentException.class,
                () -> runtime.launch(new ShipCommand.RuntimeSettings(
                        absent, absent, null, null, false, null, List.of())));

        assertTrue(missing.getMessage().contains("install Pi"), missing.getMessage());
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void launchBuildsACoordinatorBackedWorkflowFromFakeExecutables() throws Exception {
        Path binDirectory = Files.createDirectory(tempDir.resolve("bin"));
        writeExecutable(binDirectory.resolve("pi"));
        writeExecutable(binDirectory.resolve("node"));
        Path configFile = Files.createFile(tempDir.resolve("config.properties"));
        ShipRuntime runtime = new ShipRuntime(
                tempDir.resolve("state"), binDirectory.toString());

        ShipCommand.Workflow workflow = runtime.launch(new ShipCommand.RuntimeSettings(
                null, null, null, Duration.ofSeconds(5), false, configFile, List.of()));

        assertNotNull(workflow);
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void loadsUserConfigurationOnlyWhenLaunchingTheWorkflow() throws Exception {
        Path binDirectory = Files.createDirectory(tempDir.resolve("config-bin"));
        writeExecutable(binDirectory.resolve("pi"));
        writeExecutable(binDirectory.resolve("node"));
        Path configFile = tempDir.resolve("missing-ship.properties");
        ShipRuntime runtime = new ShipRuntime(
                tempDir.resolve("config-state"), binDirectory.toString());
        ShipCommand.RuntimeSettings settings = new ShipCommand.RuntimeSettings(
                null, null, null, Duration.ofSeconds(5), false, configFile, List.of());

        assertNotNull(runtime.resolve(settings), "resolving options must not load user config");

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class, () -> runtime.launch(settings));
        assertTrue(failure.getMessage().contains(configFile.toString()), failure.getMessage());
    }

    private static void writeExecutable(Path path) throws IOException {
        Files.writeString(path, "#!/bin/sh\nexit 0\n");
        Files.setPosixFilePermissions(
                path, PosixFilePermissions.fromString("rwx------"));
    }
}
