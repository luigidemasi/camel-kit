package io.github.luigidemasi.camelkit.ship.protocol;

import java.util.List;

/** Controller-issued filesystem and operation envelope for one worker attempt. */
public record StageCapability(
        RepositoryAccess repositoryAccess,
        List<String> readRoots,
        List<String> writeRoots,
        List<Operation> allowedOperations,
        boolean mayLaunchChildren,
        boolean mayInvokeLaterStages) {

    public StageCapability {
        readRoots = readRoots == null ? List.of() : List.copyOf(readRoots);
        writeRoots = writeRoots == null ? List.of() : List.copyOf(writeRoots);
        allowedOperations = allowedOperations == null ? List.of() : List.copyOf(allowedOperations);
        if (mayLaunchChildren) {
            throw new IllegalArgumentException("A bounded Ship worker may never launch child processes");
        }
        if (mayInvokeLaterStages) {
            throw new IllegalArgumentException("A bounded Ship worker may never invoke a later stage");
        }
        if (repositoryAccess == RepositoryAccess.READ_ONLY && !writeRoots.isEmpty()) {
            throw new IllegalArgumentException("A read-only capability cannot contain writable roots");
        }
    }

    public enum RepositoryAccess {
        READ_ONLY,
        READ_WITH_STAGED_OUTPUT,
        DECLARED_EXECUTION_PATHS
    }

    public enum Operation {
        READ,
        SEARCH,
        WRITE_STAGED_ARTIFACT,
        RETURN_STRUCTURED_RESULT
    }
}
