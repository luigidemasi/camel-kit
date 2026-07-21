package io.github.luigidemasi.camelkit.ship.evidence;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Content-addressed result of a controller-owned process execution. */
public record CommandEvidence(
        int schemaVersion,
        String commandId,
        SandboxIdentity sandbox,
        String toolchainDigest,
        String postToolchainDigest,
        String toolchainSnapshot,
        String toolchainSnapshotDigest,
        String executable,
        String executableDigest,
        String postExecutableDigest,
        String executableSnapshot,
        String executableVersion,
        List<String> arguments,
        String workingDirectory,
        Map<String, String> controlledEnvironment,
        Instant startedAt,
        Instant endedAt,
        boolean launched,
        boolean timedOut,
        Integer exitCode,
        String launchError,
        String stdoutLog,
        String stdoutDigest,
        String stderrLog,
        String stderrDigest,
        List<String> inputDigests) {

    public static final int SCHEMA_VERSION = 1;

    public CommandEvidence {
        arguments = arguments == null ? List.of() : List.copyOf(arguments);
        controlledEnvironment = controlledEnvironment == null
                ? Map.of()
                : Map.copyOf(controlledEnvironment);
        inputDigests = inputDigests == null ? List.of() : List.copyOf(inputDigests);
    }

    public boolean passed() {
        return launched && !timedOut && exitCode != null && exitCode == 0 && launchError == null
                && sandbox != null && sandbox.intact()
                && toolchainDigest != null && toolchainDigest.equals(postToolchainDigest)
                && toolchainSnapshot != null && toolchainSnapshotDigest != null
                && executableDigest != null && executableDigest.equals(postExecutableDigest)
                && executableVersion != null && !executableVersion.isBlank()
                && !executableVersion.startsWith("version-query-")
                && !executableVersion.startsWith("exit-")
                && !"unreported".equals(executableVersion);
    }

    /** Identity of the OS process/filesystem sandbox which launched the command. */
    public record SandboxIdentity(
            String provider,
            String executable,
            String executableDigest,
            String postExecutableDigest,
            String executableSnapshot,
            String profileDigest) {

        public boolean intact() {
            return executableDigest != null && executableDigest.equals(postExecutableDigest)
                    && profileDigest != null;
        }
    }
}
