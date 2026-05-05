package io.github.luigidemasi.camelkit.dispatch.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record StatusResult(
        String status,
        String dispatchId,
        DispatchResult result,
        int elapsedSeconds,
        String lastOutput) {

    public static StatusResult running(String dispatchId, int elapsed) {
        return new StatusResult("running", dispatchId, null, elapsed, null);
    }

    public static StatusResult completed(String dispatchId, DispatchResult result) {
        return new StatusResult(
                "completed", dispatchId, result,
                result.durationSeconds(), null);
    }

    public static StatusResult notFound(String dispatchId) {
        return new StatusResult("not_found", dispatchId, null, 0, null);
    }
}
