package io.github.luigidemasi.camelkit.dispatch.model;

import java.time.Duration;
import java.time.Instant;

public record RunningDispatch(
        Process process,
        Instant startTime) {

    public int elapsedSeconds() {
        return (int) Duration.between(startTime, Instant.now()).toSeconds();
    }
}
