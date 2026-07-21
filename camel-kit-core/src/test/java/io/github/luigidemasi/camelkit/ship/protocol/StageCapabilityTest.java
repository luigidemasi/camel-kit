package io.github.luigidemasi.camelkit.ship.protocol;

import java.util.List;

import io.github.luigidemasi.camelkit.ship.protocol.StageCapability.Operation;
import io.github.luigidemasi.camelkit.ship.protocol.StageCapability.RepositoryAccess;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StageCapabilityTest {

    @Test
    void excludesChildProcessesAndBuildCommandsFromWorkerAuthority() {
        assertThrows(IllegalArgumentException.class, () -> new StageCapability(
                RepositoryAccess.READ_ONLY, List.of(), List.of(), List.of(Operation.READ), true, false));
        assertEquals(
                List.of(
                        Operation.READ,
                        Operation.SEARCH,
                        Operation.WRITE_STAGED_ARTIFACT,
                        Operation.RETURN_STRUCTURED_RESULT),
                List.of(Operation.values()));
    }
}
