package io.github.luigidemasi.camelkit.ship.worker;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.github.luigidemasi.camelkit.ship.security.ProjectEvidenceFiles;
import io.github.luigidemasi.camelkit.ship.security.ProjectSnapshot;
import io.github.luigidemasi.camelkit.ship.security.ProjectSnapshot.FileEntry;
import io.github.luigidemasi.camelkit.ship.security.ShipTreePolicy.Classification;

/** Rejects known environment secrets introduced into material workspace files. */
public final class ChangedWorkspaceSecretScanner {

    private static final int MIN_SECRET_LENGTH = 8;

    private ChangedWorkspaceSecretScanner() {
    }

    public static List<String> scan(
            ProjectSnapshot baseline,
            ProjectSnapshot candidate,
            Map<String, String> environment)
            throws IOException {
        // Exact UTF-8 matches are the intentional local leak floor; encoded transformations are not detected.
        Objects.requireNonNull(baseline, "baseline");
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(environment, "environment");
        List<byte[]> secrets = environment.entrySet().stream()
                .filter(entry -> LocalCommandRunner.isSensitiveEnvironmentValue(
                        entry.getKey(), entry.getValue()))
                .map(Map.Entry::getValue)
                .filter(value -> value.length() >= MIN_SECRET_LENGTH)
                .distinct()
                .map(value -> value.getBytes(StandardCharsets.UTF_8))
                .toList();
        if (secrets.isEmpty()) {
            return List.of();
        }

        Path root = Path.of(candidate.root());
        List<String> findings = new ArrayList<>();
        for (Map.Entry<String, FileEntry> entry : candidate.files().entrySet()) {
            String path = entry.getKey();
            FileEntry file = entry.getValue();
            FileEntry previous = baseline.files().get(path);
            if (file.classification() != Classification.MATERIAL
                    || file.size() == 0
                    || (previous != null && previous.digest().equals(file.digest()))) {
                continue;
            }
            byte[] content = ProjectEvidenceFiles.readMaterial(
                    root, path, Math.toIntExact(file.size()));
            if (secrets.stream().anyMatch(secret -> contains(content, secret))) {
                findings.add(path);
            }
        }
        return List.copyOf(findings);
    }

    private static boolean contains(byte[] content, byte[] secret) {
        outer : for (int offset = 0; offset <= content.length - secret.length; offset++) {
            for (int index = 0; index < secret.length; index++) {
                if (content[offset + index] != secret[index]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }
}
