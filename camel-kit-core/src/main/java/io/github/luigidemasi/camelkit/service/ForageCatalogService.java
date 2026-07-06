package io.github.luigidemasi.camelkit.service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Downloads the published Forage catalog artifact and caches its two JSON files under
 * {@code .camel-kit/.cache/forage/<version>/}. Fail-soft by design: callers treat any IOException as "no Forage support
 * for this workspace" (skills then skip rung 1).
 */
public class ForageCatalogService {

    private static final String MAVEN_CENTRAL = "https://repo1.maven.org/maven2/";
    private static final Set<String> CATALOG_ENTRIES = Set.of(
            "catalog/forage-catalog.json",
            "catalog/forage-configuration-catalog.json");

    public boolean isCached(Path camelKitDir, String forageVersion) {
        Path dir = cacheDir(camelKitDir, forageVersion);
        return CATALOG_ENTRIES.stream()
                .allMatch(entry -> Files.exists(dir.resolve(Path.of(entry).getFileName().toString())));
    }

    /** Resolves the artifact from Maven Central and caches the catalog files. Skips when already cached. */
    public Path cache(Path camelKitDir, String catalogArtifact, String forageVersion)
            throws IOException, InterruptedException {
        if (isCached(camelKitDir, forageVersion)) {
            return cacheDir(camelKitDir, forageVersion);
        }
        String[] gav = catalogArtifact.split(":");
        String url = MAVEN_CENTRAL + gav[0].replace('.', '/') + "/" + gav[1] + "/" + forageVersion
                     + "/" + gav[1] + "-" + forageVersion + ".jar";
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60)).build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            throw new IOException("Forage catalog download failed: HTTP " + response.statusCode() + " for " + url);
        }
        try (InputStream body = response.body()) {
            return cacheFromJar(body, camelKitDir, forageVersion);
        }
    }

    /** Extracts the catalog JSON entries from the jar stream into the versioned cache dir (atomic: temp dir + move). */
    public Path cacheFromJar(InputStream jarStream, Path camelKitDir, String forageVersion) throws IOException {
        Path finalDir = cacheDir(camelKitDir, forageVersion);
        Files.createDirectories(finalDir.getParent());
        Path tempDir = Files.createTempDirectory(finalDir.getParent(), "forage-dl-");
        try {
            Set<String> remaining = new HashSet<>(CATALOG_ENTRIES);
            try (ZipInputStream zip = new ZipInputStream(jarStream)) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null && !remaining.isEmpty()) {
                    if (remaining.remove(entry.getName())) {
                        Path target = tempDir.resolve(Path.of(entry.getName()).getFileName().toString());
                        Files.copy(zip, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
            if (!remaining.isEmpty()) {
                throw new IOException("Forage catalog jar is missing entries: " + remaining);
            }
            deleteRecursively(finalDir);
            Files.move(tempDir, finalDir, StandardCopyOption.REPLACE_EXISTING);
            return finalDir;
        } finally {
            deleteRecursively(tempDir);
        }
    }

    private Path cacheDir(Path camelKitDir, String forageVersion) {
        return camelKitDir.resolve(".cache").resolve("forage").resolve(forageVersion);
    }

    private void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (var walk = Files.walk(dir)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    // best effort cleanup
                }
            });
        }
    }
}
