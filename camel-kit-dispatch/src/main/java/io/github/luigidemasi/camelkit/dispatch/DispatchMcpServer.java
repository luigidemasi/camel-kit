package io.github.luigidemasi.camelkit.dispatch;

import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;

import io.github.luigidemasi.camelkit.dispatch.model.DispatchResult;
import io.github.luigidemasi.camelkit.dispatch.model.ParallelResult;
import io.github.luigidemasi.camelkit.dispatch.model.StatusResult;
import io.github.luigidemasi.camelkit.dispatch.model.TaskSpec;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;

public class DispatchMcpServer {

    @Inject
    ObjectMapper mapper;

    @Inject
    BobShellRunner runner;

    @Inject
    DispatchConfig config;

    @Tool(description = "Dispatch a task to a fresh Bob Shell subagent with isolated context. "
                        + "The subagent runs in a new process with its own context window. "
                        + "Returns a compact summary - all intermediate work stays in the subagent's context.")
    public String dispatchSubagent(
            @ToolArg(description = "Full prompt/instruction for the subagent") String task,
            @ToolArg(description = "Bob Shell chat mode: code, plan, ask, advanced") String mode,
            @ToolArg(description = "Permission scope: auto_edit (file edits), yolo (all), read_only (none)") String approvalMode,
            @ToolArg(description = "Maximum execution time in seconds") Integer timeoutSeconds,
            @ToolArg(description = "Files the subagent should read as initial context") List<String> filesContext) {

        int timeout = timeoutSeconds != null ? timeoutSeconds : config.defaultTimeoutSeconds();
        String effectiveMode = mode != null ? mode : "code";
        String approval = approvalMode != null ? approvalMode : "auto_edit";
        List<String> files = filesContext != null ? filesContext : List.of();

        DispatchResult result = runner.run(task, effectiveMode, approval, timeout, files);
        return toJson(result);
    }

    @Tool(description = "Dispatch multiple tasks to parallel Bob Shell subagents. "
                        + "Each task gets its own fresh context. Independent tasks run concurrently.")
    public String dispatchParallel(
            @ToolArg(description = "Array of tasks: [{task, mode, approvalMode, timeoutSeconds, filesContext}]") List<TaskSpec> tasks,
            @ToolArg(description = "Maximum concurrent subagents") Integer maxConcurrent) {

        int concurrent = maxConcurrent != null ? maxConcurrent : config.maxConcurrent();

        ParallelResult result = runner.runParallel(tasks, concurrent);
        return toJson(result);
    }

    @Tool(description = "Check the status of a running or completed dispatch.")
    public String dispatchStatus(
            @ToolArg(description = "Dispatch ID returned by dispatch_subagent or dispatch_parallel") String dispatchId) {

        StatusResult result = runner.status(dispatchId);
        return toJson(result);
    }

    private String toJson(Object obj) {
        try {
            return mapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            String msg = e.getMessage() != null ? e.getMessage() : "JSON serialization failed";
            try {
                return mapper.writeValueAsString(Map.of("error", msg));
            } catch (JsonProcessingException ignored) {
                return "{\"error\":\"serialization_failed\"}";
            }
        }
    }
}
