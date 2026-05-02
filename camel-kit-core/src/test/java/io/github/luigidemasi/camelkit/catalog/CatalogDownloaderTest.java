package io.github.luigidemasi.camelkit.catalog;

import java.nio.file.Path;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class CatalogDownloaderTest {

    private static final Set<String> LTS_VERSIONS = Set.of("4.14", "4.18", "4.20");

    @TempDir
    Path tempDir;

    @Test
    void fallbackVersionComesFromConstructor() {
        CatalogDownloader downloader = new CatalogDownloader(tempDir, "4.14.7", LTS_VERSIONS);
        assertEquals("4.14.7", downloader.fallbackVersion());
    }

    @Test
    void rejectsNullCacheDir() {
        assertThrows(NullPointerException.class, () -> new CatalogDownloader(null, "4.14.7", LTS_VERSIONS));
    }

    @Test
    void rejectsNullFallbackVersion() {
        assertThrows(IllegalArgumentException.class, () -> new CatalogDownloader(tempDir, null, LTS_VERSIONS));
    }

    @Test
    void rejectsBlankFallbackVersion() {
        assertThrows(IllegalArgumentException.class, () -> new CatalogDownloader(tempDir, "  ", LTS_VERSIONS));
    }

    @Test
    void rejectsNullLtsVersions() {
        assertThrows(NullPointerException.class, () -> new CatalogDownloader(tempDir, "4.20.0", null));
    }

    @Test
    void searchComponentsOnEmptyCatalog() {
        CatalogDownloader downloader = new CatalogDownloader(tempDir, "4.14.7", LTS_VERSIONS);
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var catalog = mapper.createObjectNode();
        catalog.putArray("components");
        var results = downloader.searchComponents("kafka", catalog, 10);
        assertTrue(results.isEmpty());
    }
}
