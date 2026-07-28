package io.github.luigidemasi.camelkit.ship.worker;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import io.github.luigidemasi.camelkit.ship.security.ProjectEvidenceFiles;
import io.github.luigidemasi.camelkit.ship.security.ProjectSnapshot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChangedWorkspaceSecretScannerTest {

    @TempDir
    Path directory;

    @Test
    void reportsKnownSecretsOnlyInChangedMaterialFiles() throws Exception {
        Path baselineRoot = Files.createDirectory(directory.resolve("baseline"));
        Files.writeString(baselineRoot.resolve("unchanged.txt"), "token-12345678");
        Files.writeString(baselineRoot.resolve("changed.txt"), "safe");
        ProjectSnapshot baseline = ProjectEvidenceFiles.captureStaged(baselineRoot);

        Path candidateRoot = Files.createDirectory(directory.resolve("candidate"));
        Files.writeString(candidateRoot.resolve("unchanged.txt"), "token-12345678");
        Files.writeString(candidateRoot.resolve("changed.txt"), "prefix token-12345678 suffix");
        Files.writeString(candidateRoot.resolve("short.txt"), "0");
        ProjectSnapshot candidate = ProjectEvidenceFiles.captureStaged(candidateRoot);

        assertEquals(
                java.util.List.of("changed.txt"),
                ChangedWorkspaceSecretScanner.scan(
                        baseline,
                        candidate,
                        Map.of("API_TOKEN", "token-12345678", "AUTH", "0")));
    }

    @Test
    void acceptsChangedFilesWhenNoKnownSecretIsPresent() throws Exception {
        Path baselineRoot = Files.createDirectory(directory.resolve("baseline"));
        Files.writeString(baselineRoot.resolve("route.yaml"), "from: direct:old");
        ProjectSnapshot baseline = ProjectEvidenceFiles.captureStaged(baselineRoot);

        Path candidateRoot = Files.createDirectory(directory.resolve("candidate"));
        Files.writeString(candidateRoot.resolve("route.yaml"), "from: direct:new");
        ProjectSnapshot candidate = ProjectEvidenceFiles.captureStaged(candidateRoot);

        assertEquals(
                java.util.List.of(),
                ChangedWorkspaceSecretScanner.scan(
                        baseline, candidate, Map.of("API_TOKEN", "token-12345678")));
    }
}
