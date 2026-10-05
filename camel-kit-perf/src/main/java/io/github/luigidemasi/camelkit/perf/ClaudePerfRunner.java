package io.github.luigidemasi.camelkit.perf;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

public final class ClaudePerfRunner {

    private final Map<String, String> otelEnv;

    public ClaudePerfRunner(Map<String, String> otelEnv) {
        this.otelEnv = Map.copyOf(otelEnv);
    }

    public PerfResult run(String prompt, Path workingDir, List<String> allowedTools)
            throws IOException, InterruptedException {
        List<String> command = buildCommand(prompt, allowedTools);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workingDir.toFile());
        pb.environment().putAll(otelEnv);
        pb.redirectErrorStream(true);

        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();

        return PerfResult.parse(output, exitCode);
    }

    List<String> buildCommand(String prompt, List<String> allowedTools) {
        List<String> command = new ArrayList<>(
                List.of(
                        "claude", "-p", prompt,
                        "--bare",
                        "--output-format", "json"));

        if (!allowedTools.isEmpty()) {
            command.add("--allowedTools");
            command.add(String.join(",", allowedTools));
        }

        return command;
    }

    Map<String, String> otelEnv() {
        return otelEnv;
    }
}
