package io.github.luigidemasi.camelkit.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

/**
 * Runs short-lived external commands with timeout-bound output capture.
 */
public final class ProcessRunner {

    private ProcessRunner() {
        // Utility class
    }

    public static Result run(Duration timeout, String... command) {
        return run(null, timeout, command);
    }

    public static Result run(Path directory, Duration timeout, String... command) {
        Objects.requireNonNull(timeout, "timeout");
        Objects.requireNonNull(command, "command");

        Process process = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            if (directory != null) {
                pb.directory(directory.toFile());
            }
            pb.redirectErrorStream(true);

            process = pb.start();
            Process runningProcess = process;
            StringBuffer output = new StringBuffer();
            Thread drainer = new Thread(() -> drain(runningProcess, output));
            drainer.setDaemon(true);
            drainer.start();

            if (!process.waitFor(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                return new Result(false, -1, output.toString().trim());
            }

            drainer.join(500);
            return new Result(true, process.exitValue(), output.toString().trim());
        } catch (Exception e) {
            if (process != null) {
                process.destroyForcibly();
            }
            return new Result(false, -1, "");
        }
    }

    private static void drain(Process process, StringBuffer output) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            reader.lines().forEach(line -> output.append(line).append('\n'));
        } catch (java.io.IOException ignored) {
        }
    }

    public record Result(boolean completed, int exitCode, String output) {

        public boolean succeeded() {
            return completed && exitCode == 0;
        }
    }
}
