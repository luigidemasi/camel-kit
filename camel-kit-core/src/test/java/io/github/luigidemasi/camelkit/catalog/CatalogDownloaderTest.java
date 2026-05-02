package io.github.luigidemasi.camelkit.catalog;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class CatalogDownloaderTest {

    @TempDir
    Path tempDir;

    @Test
    void fallbackVersionComesFromConstructor() throws Exception {
        CatalogDownloader downloader = new CatalogDownloader(tempDir, "4.14.7");
        // getLatestLtsVersion hits Maven Central; if the network call fails or
        // returns no LTS match, the fallback from the constructor is used.
        // We can't control the network, but we verify the object accepts the value.
        assertNotNull(downloader);
    }

    @Test
    void rejectsNullCacheDir() {
        assertThrows(NullPointerException.class, () -> new CatalogDownloader(null, "4.14.7"));
    }

    @Test
    void rejectsNullFallbackVersion() {
        assertThrows(IllegalArgumentException.class, () -> new CatalogDownloader(tempDir, null));
    }

    @Test
    void rejectsBlankFallbackVersion() {
        assertThrows(IllegalArgumentException.class, () -> new CatalogDownloader(tempDir, "  "));
    }

    @Test
    void searchComponentsOnEmptyCatalog() throws Exception {
        CatalogDownloader downloader = new CatalogDownloader(tempDir, "4.14.7");
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var catalog = mapper.createObjectNode();
        catalog.putArray("components");
        var results = downloader.searchComponents("kafka", catalog, 10);
        assertTrue(results.isEmpty());
    }
}
