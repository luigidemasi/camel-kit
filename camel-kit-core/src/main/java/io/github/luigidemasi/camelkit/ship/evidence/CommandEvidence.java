package io.github.luigidemasi.camelkit.ship.evidence;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/** Result of a controller-owned direct process execution. */
public record CommandEvidence(
        String commandId,
        String executable,
        List<String> arguments,
        String workingDirectory,
        List<String> inputDigests,
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
        boolean quarantined,
        Path sandboxRoot) {

    public CommandEvidence {
        arguments = arguments == null ? List.of() : List.copyOf(arguments);
        inputDigests = inputDigests == null ? List.of() : List.copyOf(inputDigests);
    }

    public boolean passed() {
        return launched && !timedOut && exitCode != null && exitCode == 0 && launchError == null;
    }
}
