package io.github.luigidemasi.camelkit.knowledge.indexer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DocumentFetcherTest {

    @TempDir
    Path tempDir;

    @Test
    void fetchTextFromGitHub() throws Exception {
        DocumentFetcher fetcher = new DocumentFetcher(tempDir);

        // Fetch a known small file from GitHub
        String url = "https://raw.githubusercontent.com/apache/camel/main/docs/user-manual/modules/ROOT/pages/camel-4-migration-guide.adoc";
        String content = fetcher.fetchText(url);

        assertNotNull(content);
        assertFalse(content.isEmpty());
        assertTrue(content.contains("Camel")); // Basic sanity check
    }

    @Test
    void fetchToFile() throws Exception {
        DocumentFetcher fetcher = new DocumentFetcher(tempDir);

        String url = "https://raw.githubusercontent.com/apache/camel/main/docs/user-manual/modules/ROOT/pages/camel-4-migration-guide.adoc";
        Path file = fetcher.fetch(url, "camel-4-migration.adoc");

        assertTrue(Files.exists(file));
        assertTrue(Files.size(file) > 0);
    }

    @Test
    void cacheHitSkipsFetch() throws Exception {
        DocumentFetcher fetcher = new DocumentFetcher(tempDir);

        // Pre-create the cache file
        Path cached = tempDir.resolve("cached.txt");
        Files.writeString(cached, "cached content");

        Path result = fetcher.fetch("https://example.com/does-not-exist", "cached.txt");
        assertEquals(cached, result);
        assertEquals("cached content", Files.readString(result));
    }
}
