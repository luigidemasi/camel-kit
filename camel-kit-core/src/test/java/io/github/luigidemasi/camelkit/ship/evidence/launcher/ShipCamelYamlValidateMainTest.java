package io.github.luigidemasi.camelkit.ship.evidence.launcher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import io.github.luigidemasi.camelkit.ship.ShipArtifactLimits;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ShipCamelYamlValidateMainTest {

    @TempDir
    Path project;

    @Test
    void enforcesTheCanonicalAggregateRouteYamlByteLimitAtItsBoundary() throws Exception {
        Path accepted = write("accepted.camel.yaml", padWithComment(
                validRoute(), ShipArtifactLimits.MAX_ROUTE_YAML_BYTES));
        assertEquals(ShipArtifactLimits.MAX_ROUTE_YAML_BYTES, Files.size(accepted));
        assertDoesNotThrow(() -> ShipCamelYamlValidateMain.validate(
                project, List.of(accepted.toString())));

        Path rejected = write("rejected.camel.yaml", padWithComment(
                validRoute(), ShipArtifactLimits.MAX_ROUTE_YAML_BYTES + 1));
        assertEquals(ShipArtifactLimits.MAX_ROUTE_YAML_BYTES + 1L, Files.size(rejected));
        assertThrows(IOException.class, () -> ShipCamelYamlValidateMain.validate(
                project, List.of(rejected.toString())));
    }

    private Path write(String name, String content) throws IOException {
        return Files.writeString(project.resolve(name), content, StandardCharsets.UTF_8);
    }

    private static String validRoute() {
        return """
                - route:
                    id: orders
                    from:
                      uri: direct:orders
                      steps:
                        - setBody:
                            simple: accepted-order
                """;
    }

    private static String padWithComment(String value, int bytes) {
        int padding = bytes - value.getBytes(StandardCharsets.UTF_8).length;
        if (padding < 2) {
            throw new IllegalArgumentException("Target byte size is too small");
        }
        return value + '#' + "x".repeat(padding - 2) + '\n';
    }
}
