package io.github.luigidemasi.camelkit.generator;

import java.io.IOException;

/**
 * Signals invalid existing agent configuration that must be reported without starting generation.
 */
public final class InvalidAgentConfigurationException extends IOException {

    public InvalidAgentConfigurationException(String message) {
        super(message);
    }

    public InvalidAgentConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
