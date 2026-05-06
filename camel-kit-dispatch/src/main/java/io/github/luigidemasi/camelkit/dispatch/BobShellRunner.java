package io.github.luigidemasi.camelkit.dispatch;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.github.luigidemasi.camelkit.dispatch.model.DispatchResult;
import io.github.luigidemasi.camelkit.dispatch.model.ParallelResult;
import io.github.luigidemasi.camelkit.dispatch.model.RunningDispatch;
import io.github.luigidemasi.camelkit.dispatch.model.StatusResult;
import io.github.luigidemasi.camelkit.dispatch.model.TaskSpec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@ApplicationScoped
public class BobShellRunner {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Inject
    DispatchConfig config;

    private final ConcurrentHashMap<String, RunningDispatch> activeDispatches = new ConcurrentHashMap<>();

    public DispatchResult run(
            String task, String mode, String approvalMode,
            int timeout, List<String> filesContext) {
        String dispatchId = UUID.randomUUID().toString().substring(0, 8);
        GitChangeDetector detector = new GitChangeDetector(config.resolveWorkingDir());
        Set<String> beforeState = detector.captureState();
        Instant start = Instant.now();

        List<String> cmd = buildCommand(config.bobPath(), task, mode, approvalMode, filesContext);

        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(config.resolveWorkingDir().toFile());
            pb.redirectErrorStream(true);
            Process process = pb.start();

            activeDispatches.put(dispatchId, new RunningDispatch(process, start));

            CompletableFuture<String> outputFuture = CompletableFuture.supplyAsync(
                    () -> drainStream(process.getInputStream()));

            boolean completed = process.waitFor(timeout, TimeUnit.SECONDS);
            int elapsed = (int) Duration.between(start, Instant.now()).toSeconds();

            if (!completed) {
                process.destroyForcibly();
                activeDispatches.remove(dispatchId);
                return DispatchResult.timeout(dispatchId, elapsed);
            }

            GitChangeDetector.FileChanges changes = detector.detectChanges(beforeState);
            String summary = extractSummary(outputFuture.get());

            activeDispatches.remove(dispatchId);

            if (process.exitValue() != 0) {
                return DispatchResult.failure(dispatchId, summary, elapsed);
            }

            return DispatchResult.success(dispatchId, summary,
                    changes.modified(), changes.created(), elapsed);

        } catch (Exception e) {
            activeDispatches.remove(dispatchId);
            int elapsed = (int) Duration.between(start, Instant.now()).toSeconds();
            return DispatchResult.failure(dispatchId, e.getMessage(), elapsed);
        }
    }

    public ParallelResult runParallel(List<TaskSpec> tasks, int maxConcurrent) {
        if (tasks == null || tasks.isEmpty()) {
            return new ParallelResult(List.of(), 0);
        }

        int poolSize = Math.max(1, Math.min(maxConcurrent, tasks.size()));
        ExecutorService executor = Executors.newFixedThreadPool(poolSize);
        Instant start = Instant.now();

        try {
            List<CompletableFuture<DispatchResult>> futures = tasks.stream()
                    .map(t -> CompletableFuture.supplyAsync(
                            () -> run(t.task(), t.mode(), t.approvalMode(),
                                    t.timeoutSeconds(), t.filesContext()),
                            executor))
                    .toList();

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            List<DispatchResult> results = futures.stream()
                    .map(CompletableFuture::join)
                    .toList();

            int wallClock = (int) Duration.between(start, Instant.now()).toSeconds();
            return new ParallelResult(results, wallClock);
        } finally {
            executor.shutdown();
        }
    }

    public StatusResult status(String dispatchId) {
        RunningDispatch rd = activeDispatches.get(dispatchId);
        if (rd == null) {
            return StatusResult.notFound(dispatchId);
        }
        if (rd.process().isAlive()) {
            return StatusResult.running(dispatchId, rd.elapsedSeconds());
        }
        return StatusResult.notFound(dispatchId);
    }

    static List<String> buildCommand(
            String bobPath, String task, String mode,
            String approvalMode, List<String> filesContext) {
        List<String> cmd = new ArrayList<>();
        cmd.add(bobPath);

        if (filesContext != null && !filesContext.isEmpty()) {
            String fileInstructions = filesContext.stream()
                    .map(f -> "Read and understand the file: " + f)
                    .collect(Collectors.joining(". "));
            cmd.add(fileInstructions + ". Then: " + task);
        } else {
            cmd.add(task);
        }

        cmd.addAll(List.of("--chat-mode", mode));
        cmd.addAll(List.of("--output-format", "json"));
        cmd.add("--hide-intermediary-output");

        switch (approvalMode) {
            case "read_only" -> {
                cmd.add("--allowed-tools");
                cmd.add("read_file");
                cmd.add("list_code_definition_names");
            }
            case "yolo" -> cmd.addAll(List.of("--approval-mode", "yolo"));
            default -> {
                cmd.addAll(List.of("--approval-mode", "auto_edit"));
                cmd.add("--pre-check-auto-approved");
            }
        }

        return cmd;
    }

    static String extractSummary(String output) {
        if (output == null || output.isBlank()) {
            return "No output from subagent";
        }

        try {
            String[] lines = output.strip().split("\n");
            for (int i = lines.length - 1; i >= 0; i--) {
                String line = lines[i].strip();
                if (line.startsWith("{")) {
                    JsonNode node = MAPPER.readTree(line);
                    if (node.has("content")) {
                        return node.get("content").asText();
                    }
                    return node.toString();
                }
            }
        } catch (Exception ignored) {
        }

        String[] lines = output.strip().split("\n");
        return lines[lines.length - 1].strip();
    }

    private static String drainStream(InputStream is) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (Exception e) {
            return "";
        }
    }
}
