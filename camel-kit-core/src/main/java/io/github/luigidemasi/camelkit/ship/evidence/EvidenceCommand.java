package io.github.luigidemasi.camelkit.ship.evidence;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Controller-selected command vector. Worker-provided shell strings are deliberately unsupported. The argument vector
 * is the planned template: index 0 names the controller java path and index 2 the {@code payload.jar} token; the runner
 * substitutes run-local sandbox paths at launch.
 */
public record EvidenceCommand(
        String id,
        List<String> arguments,
        String relativeWorkingDirectory,
        Duration timeout,
        List<String> inputDigests,
        Map<String, String> environment,
        JvmPayloadRequest jvmPayload) {

    public EvidenceCommand {
        arguments = arguments == null ? List.of() : List.copyOf(arguments);
        timeout = timeout == null ? Duration.ofMinutes(10) : timeout;
        inputDigests = inputDigests == null ? List.of() : List.copyOf(inputDigests);
        environment = environment == null ? Map.of() : Map.copyOf(environment);
        if (id == null || !id.matches("[a-z0-9][a-z0-9-]*")) {
            throw new IllegalArgumentException("Evidence command ID must be lowercase alphanumeric with hyphens");
        }
        if (arguments.isEmpty() || arguments.stream().anyMatch(value -> value == null || value.isEmpty())) {
            throw new IllegalArgumentException("Evidence command requires a nonempty argument vector");
        }
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("Evidence command timeout must be positive");
        }
        if (!environment.keySet().stream().allMatch(key -> "LANG".equals(key) || "LC_ALL".equals(key))) {
            throw new IllegalArgumentException("Evidence commands may only select deterministic locale values");
        }
        if (jvmPayload == null || !EvidenceRunner.JAVA_EXECUTABLE.equals(arguments.get(0))) {
            throw new IllegalArgumentException(
                    "Evidence commands require a controller-owned direct JVM payload");
        }
    }
}
