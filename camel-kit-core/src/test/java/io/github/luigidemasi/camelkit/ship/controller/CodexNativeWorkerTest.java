package io.github.luigidemasi.camelkit.ship.controller;

import io.github.luigidemasi.camelkit.ship.controller.ShipRun.ExecutionMode;

/** Runs the same handoff, recovery, boundary and publication acceptance contract as the other native hosts. */
class CodexNativeWorkerTest extends ShipNativeWorkerTest {

    @Override
    ExecutionMode executionMode() {
        return ExecutionMode.CODEX_NATIVE;
    }
}
