package io.github.luigidemasi.camelkit.graph;

import java.util.List;
import java.util.Objects;

public record ParserDiagnostic(
        String parserName,
        List<String> scannedFiles,
        List<String> skippedFiles,
        List<String> warnings,
        List<String> failures,
        boolean timedOut,
        int nodesProduced,
        int edgesProduced,
        long durationMillis) {

    public ParserDiagnostic {
        Objects.requireNonNull(parserName, "parserName");
        scannedFiles = List.copyOf(Objects.requireNonNull(scannedFiles, "scannedFiles"));
        skippedFiles = List.copyOf(Objects.requireNonNull(skippedFiles, "skippedFiles"));
        warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings"));
        failures = List.copyOf(Objects.requireNonNull(failures, "failures"));
        if (nodesProduced < 0) {
            throw new IllegalArgumentException("nodesProduced must be non-negative");
        }
        if (edgesProduced < 0) {
            throw new IllegalArgumentException("edgesProduced must be non-negative");
        }
        if (durationMillis < 0) {
            throw new IllegalArgumentException("durationMillis must be non-negative");
        }
    }

    public boolean successful() {
        return !timedOut && failures.isEmpty();
    }

    public String summary() {
        if (timedOut) {
            return parserName + " timed out after " + durationMillis + " ms";
        }
        if (!failures.isEmpty()) {
            return parserName + " failed: " + String.join("; ", failures);
        }
        if (!warnings.isEmpty()) {
            return parserName + " warned: " + String.join("; ", warnings);
        }
        return parserName + " scanned " + scannedFiles.size() + " files";
    }
}
