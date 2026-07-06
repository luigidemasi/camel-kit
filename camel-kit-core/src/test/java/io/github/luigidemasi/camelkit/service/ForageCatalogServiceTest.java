package io.github.luigidemasi.camelkit.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForageCatalogServiceTest {

    private byte[] jarWith(String... entries) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (String entry : entries) {
                zip.putNextEntry(new ZipEntry(entry));
                zip.write(("{\"from\":\"" + entry + "\"}").getBytes());
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    @Test
    void extractsBothCatalogFilesToVersionedCacheDir(@TempDir Path camelKitDir) throws Exception {
        byte[] jar = jarWith("catalog/forage-catalog.json", "catalog/forage-configuration-catalog.json",
                "META-INF/MANIFEST.MF");
        ForageCatalogService service = new ForageCatalogService();

        Path cacheDir = service.cacheFromJar(new ByteArrayInputStream(jar), camelKitDir, "1.5.0");

        assertEquals(camelKitDir.resolve(".cache/forage/1.5.0"), cacheDir);
        assertTrue(Files.exists(cacheDir.resolve("forage-catalog.json")));
        assertTrue(Files.exists(cacheDir.resolve("forage-configuration-catalog.json")));
    }

    @Test
    void missingCatalogEntryLeavesNoPartialCache(@TempDir Path camelKitDir) throws Exception {
        byte[] jar = jarWith("catalog/forage-catalog.json"); // configuration catalog missing
        ForageCatalogService service = new ForageCatalogService();

        assertThrows(IOException.class,
                () -> service.cacheFromJar(new ByteArrayInputStream(jar), camelKitDir, "1.5.0"));
        assertFalse(Files.exists(camelKitDir.resolve(".cache/forage/1.5.0")));
    }

    @Test
    void existingCacheIsNotRedownloaded(@TempDir Path camelKitDir) throws Exception {
        Path existing = camelKitDir.resolve(".cache/forage/1.5.0");
        Files.createDirectories(existing);
        Files.writeString(existing.resolve("forage-catalog.json"), "{}");
        Files.writeString(existing.resolve("forage-configuration-catalog.json"), "{}");

        assertTrue(new ForageCatalogService().isCached(camelKitDir, "1.5.0"));
    }
}
