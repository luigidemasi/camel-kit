package io.github.luigidemasi.camelkit.dispatch;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class GitChangeDetector {

    private final Path workingDir;

    public GitChangeDetector(Path workingDir) {
        this.workingDir = workingDir;
    }

    public record FileChanges(
            List<String> modified,
            List<String> created) {
    }

    public Set<String> captureState() {
        Set<String> state = new HashSet<>();
        state.addAll(runGitCommand("git", "diff", "--name-only"));
        state.addAll(runGitCommand("git", "diff", "--name-only", "--cached"));
        state.addAll(runGitCommand("git", "ls-files", "--others", "--exclude-standard"));
        return state;
    }

    public FileChanges detectChanges(Set<String> beforeState) {
        List<String> modified = new ArrayList<>();
        List<String> created = new ArrayList<>();

        List<String> currentDirty = runGitCommand("git", "diff", "--name-only");
        List<String> currentStaged = runGitCommand("git", "diff", "--name-only", "--cached");
        List<String> currentUntracked = runGitCommand("git", "ls-files", "--others", "--exclude-standard");

        Set<String> allChanged = new HashSet<>();
        allChanged.addAll(currentDirty);
        allChanged.addAll(currentStaged);

        for (String file : allChanged) {
            if (!beforeState.contains(file)) {
                modified.add(file);
            }
        }

        for (String file : currentUntracked) {
            if (!beforeState.contains(file)) {
                created.add(file);
            }
        }

        return new FileChanges(modified, created);
    }

    private List<String> runGitCommand(String... command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(workingDir.toFile());
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            Process process = pb.start();

            List<String> lines;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                lines = reader.lines()
                        .filter(line -> !line.isBlank())
                        .collect(Collectors.toList());
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                return List.of();
            }
            return lines;
        } catch (Exception e) {
            return List.of();
        }
    }
}
