package io.github.luigidemasi.camelkit.dispatch.model;

import java.util.List;

public record ParallelResult(
        List<DispatchResult> results,
        int totalDurationSeconds) {
}
