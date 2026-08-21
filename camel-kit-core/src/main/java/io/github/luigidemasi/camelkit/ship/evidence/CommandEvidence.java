package io.github.luigidemasi.camelkit.ship.evidence;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/**
 * Result of a controller-owned direct process execution.
 *
 * <p>
 * {@code executable}, {@code arguments}, and {@code workingDirectory} record the planned controller form of the
 * command, not the runtime invocation: the launch resolves the executable, substitutes the materialized archive for the
 * {@code payload.jar} token at index 2, injects the sandbox JVM options and {@code --accepted-root}, and runs inside
 * the frozen accepted snapshot. Run-local paths travel in {@code sandboxRoot}. {@code workingDirectory} anchors the
 * command to the candidate root; the process working directory is the byte-identical accepted snapshot bound by
 * {@code inputDigests}.
 * </p>
 */
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
