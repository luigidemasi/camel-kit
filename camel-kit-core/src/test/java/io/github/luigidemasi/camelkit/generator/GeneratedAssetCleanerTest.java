package io.github.luigidemasi.camelkit.generator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class GeneratedAssetCleanerTest {

    @TempDir
    Path tempDir;

    @Test
    void deletesOnlyRetiredRegularFiles() throws Exception {
        Path project = Files.createDirectory(tempDir.resolve("project"));
        Path retired = Files.writeString(project.resolve("retired.md"), "generated");
        Path foreignDirectory = Files.createDirectory(project.resolve("foreign.md"));

        GeneratedAssetCleaner.deleteRegularFile(project, retired);
        GeneratedAssetCleaner.deleteRegularFile(project, foreignDirectory);

        assertFalse(Files.exists(retired));
        assertTrue(Files.isDirectory(foreignDirectory));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void preservesFinalSymlinksAndRejectsSymlinkedParents() throws Exception {
        Path project = Files.createDirectory(tempDir.resolve("project"));
        Path outside = Files.createDirectory(tempDir.resolve("outside"));
        Path outsideFile = Files.writeString(outside.resolve("outside.md"), "keep");
        Path finalSymlink = Files.createSymbolicLink(project.resolve("retired.md"), outsideFile);

        GeneratedAssetCleaner.deleteRegularFile(project, finalSymlink);

        assertTrue(Files.isSymbolicLink(finalSymlink));
        assertEquals("keep", Files.readString(outsideFile));

        Path externalGuide = outside.resolve("skills/camel-ship/guides/state-management.md");
        Files.createDirectories(externalGuide.getParent());
        Files.writeString(externalGuide, "legacy");
        Files.createSymbolicLink(project.resolve(".claude"), outside);

        IOException failure = assertThrows(IOException.class, () -> GeneratedAssetCleaner.deleteRegularFile(
                project, project.resolve(".claude/skills/camel-ship/guides/state-management.md")));

        assertTrue(failure.getMessage().contains("symbolic link"));
        assertEquals("legacy", Files.readString(externalGuide));
    }
}
