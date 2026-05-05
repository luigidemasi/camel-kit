package io.github.luigidemasi.camelkit.dispatch.model;

import java.util.List;

public record DispatchResult(
        String status,
        String summary,
        List<String> filesModified,
        List<String> filesCreated,
        int durationSeconds,
        String dispatchId) {

    public static DispatchResult success(
            String dispatchId, String summary,
            List<String> modified, List<String> created, int duration) {
        return new DispatchResult("success", summary, modified, created, duration, dispatchId);
    }

    public static DispatchResult failure(String dispatchId, String summary, int duration) {
        return new DispatchResult("failure", summary, List.of(), List.of(), duration, dispatchId);
    }

    public static DispatchResult timeout(String dispatchId, int duration) {
        return new DispatchResult(
                "timeout",
                "Subagent exceeded timeout of " + duration + " seconds",
                List.of(), List.of(), duration, dispatchId);
    }
}
