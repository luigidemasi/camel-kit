package io.github.luigidemasi.camelkit.command.pipeline;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class NextIdCommandTest {

    @TempDir
    Path tempDir;

    private String runNextId(Path baseDir, String slug) {
        NextIdCommand cmd = new NextIdCommand();
        cmd.slug = slug;
        cmd.baseDir = baseDir;

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(baos, true, StandardCharsets.UTF_8);
        PrintStream oldOut = System.out;
        System.setOut(out);
        try {
            cmd.run();
        } finally {
            System.setOut(oldOut);
        }
        return baos.toString(StandardCharsets.UTF_8).trim();
    }

    @Test
    void emptyDirectoryStartsAt001() throws Exception {
        String id = runNextId(tempDir, "order-processing");
        assertEquals("001-order-processing", id);
        assertTrue(Files.isDirectory(tempDir.resolve("docs/camel-kit/001-order-processing")));
    }

    @Test
    void incrementsFromExistingDirectories() throws Exception {
        Path docsDir = tempDir.resolve("docs/camel-kit");
        Files.createDirectories(docsDir.resolve("001-foo"));
        Files.createDirectories(docsDir.resolve("002-bar"));

        String id = runNextId(tempDir, "baz");
        assertEquals("003-baz", id);
        assertTrue(Files.isDirectory(docsDir.resolve("003-baz")));
    }

    @Test
    void handlesNonSequentialIds() throws Exception {
        Path docsDir = tempDir.resolve("docs/camel-kit");
        Files.createDirectories(docsDir.resolve("001-first"));
        Files.createDirectories(docsDir.resolve("005-fifth"));

        String id = runNextId(tempDir, "next");
        assertEquals("006-next", id);
        assertTrue(Files.isDirectory(docsDir.resolve("006-next")));
    }

    @Test
    void createsDocsCamelKitIfMissing() throws Exception {
        assertFalse(Files.exists(tempDir.resolve("docs/camel-kit")));
        String id = runNextId(tempDir, "first");
        assertEquals("001-first", id);
        assertTrue(Files.isDirectory(tempDir.resolve("docs/camel-kit/001-first")));
    }

    @Test
    void ignoresNonMatchingDirectories() throws Exception {
        Path docsDir = tempDir.resolve("docs/camel-kit");
        Files.createDirectories(docsDir.resolve("readme-notes"));
        Files.createDirectories(docsDir.resolve("001-valid"));
        Files.createFile(docsDir.resolve("some-file.md"));

        String id = runNextId(tempDir, "second");
        assertEquals("002-second", id);
    }

    @Test
    void handlesIdsBeyond999() throws Exception {
        Path docsDir = tempDir.resolve("docs/camel-kit");
        Files.createDirectories(docsDir.resolve("999-last"));
        Files.createDirectories(docsDir.resolve("1000-current"));

        String id = runNextId(tempDir, "next");
        assertEquals("1001-next", id);
        assertTrue(Files.isDirectory(docsDir.resolve("1001-next")));
    }

    @Test
    void invalidSlugThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> runNextId(tempDir, "UPPER-case"));
    }
}
