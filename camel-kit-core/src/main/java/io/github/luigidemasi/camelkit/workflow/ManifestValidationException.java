package io.github.luigidemasi.camelkit.workflow;

import java.io.IOException;
import java.util.List;

/**
 * Thrown when the workflow manifest parses successfully but violates semantic constraints.
 */
public final class ManifestValidationException extends IOException {

    private final List<String> errors;

    ManifestValidationException(List<String> errors) {
        super("Invalid workflow manifest:\n" + String.join("\n", errors));
        this.errors = List.copyOf(errors);
    }

    public List<String> errors() {
        return errors;
    }
}
