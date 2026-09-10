package io.github.luigidemasi.camelkit.ship.controller;

import io.github.luigidemasi.camelkit.ship.controller.ShipRun.ExecutionMode;

/** Runs the same handoff, recovery, boundary and publication acceptance contract as Bob and Copilot. */
class ClaudeNativeWorkerTest extends ShipNativeWorkerTest {

    @Override
    ExecutionMode executionMode() {
        return ExecutionMode.CLAUDE_NATIVE;
    }
}
