package io.github.luigidemasi.camelkit.ship.protocol;

import java.util.List;

/** Raised when a worker result violates its controller-issued stage contract. */
public class StageResultValidationException extends IllegalArgumentException {

    private final List<String> violations;

    public StageResultValidationException(List<String> violations) {
        super(String.join("; ", violations));
        this.violations = List.copyOf(violations);
    }

    public List<String> violations() {
        return violations;
    }
}
