package io.github.luigidemasi.camelkit.dispatch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class GitChangeDetectorTest {

    @TempDir
    Path tempDir;

    private GitChangeDetector detector;

    @BeforeEach
    void setUp() throws Exception {
        assertEquals(0, run("git", "init"), "git init failed");
        assertEquals(0, run("git", "config", "user.email", "test@test.com"), "git config email failed");
        assertEquals(0, run("git", "config", "user.name", "Test"), "git config name failed");

        Files.writeString(tempDir.resolve("existing.txt"), "original");
        assertEquals(0, run("git", "add", "."), "git add failed");
        assertEquals(0, run("git", "commit", "-m", "init"), "git commit failed");

        detector = new GitChangeDetector(tempDir);
    }

    private int run(String... cmd) throws Exception {
        return new ProcessBuilder(cmd).directory(tempDir.toFile()).start().waitFor();
    }

    @Test
    void detectsNoChangesWhenNothingChanged() {
        Set<String> before = detector.captureState();
        GitChangeDetector.FileChanges changes = detector.detectChanges(before);

        assertTrue(changes.modified().isEmpty());
        assertTrue(changes.created().isEmpty());
    }

    @Test
    void detectsModifiedFile() throws IOException {
        Set<String> before = detector.captureState();
        Files.writeString(tempDir.resolve("existing.txt"), "modified content");
        GitChangeDetector.FileChanges changes = detector.detectChanges(before);

        assertTrue(changes.modified().contains("existing.txt"));
        assertTrue(changes.created().isEmpty());
    }

    @Test
    void detectsCreatedFile() throws IOException {
        Set<String> before = detector.captureState();
        Files.writeString(tempDir.resolve("newfile.txt"), "new content");
        GitChangeDetector.FileChanges changes = detector.detectChanges(before);

        assertTrue(changes.created().contains("newfile.txt"));
    }

    @Test
    void detectsBothModifiedAndCreated() throws IOException {
        Set<String> before = detector.captureState();
        Files.writeString(tempDir.resolve("existing.txt"), "changed");
        Files.writeString(tempDir.resolve("brand-new.yaml"), "routes:");
        GitChangeDetector.FileChanges changes = detector.detectChanges(before);

        assertTrue(changes.modified().contains("existing.txt"));
        assertTrue(changes.created().contains("brand-new.yaml"));
    }
}
