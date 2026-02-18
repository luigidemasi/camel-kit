package io.github.luigidemasi.camelkit.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Downloads and caches Citrus JSON schemas from Maven Central.
 */
public class CitrusSchemaDownloader {

    private static final String MAVEN_CENTRAL = "https://repo1.maven.org/maven2";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final Path cacheDir;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    public CitrusSchemaDownloader(Path cacheDir) {
        this.cacheDir = cacheDir;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
        this.mapper = new ObjectMapper();
    }

    /**
     * Fetch Citrus schemas for a specific version.
     * Downloads from Maven Central and extracts JSON schemas.
     *
     * @param version Citrus version (e.g., "4.9.2")
     * @param forceRefresh force re-download even if cached
     * @return the index.json content
     */
    public JsonNode fetchCitrusSchemas(String version, boolean forceRefresh) throws Exception {
        Path citrusDir = cacheDir.resolve("citrus").resolve(version);
        Path indexFile = citrusDir.resolve("index.json");

        if (!forceRefresh && Files.exists(indexFile)) {
            return mapper.readTree(Files.readString(indexFile));
        }

        Files.createDirectories(citrusDir);

        // Download citrus-catalog-schema JAR
        String jarUrl = String.format("%s/org/citrusframework/citrus-catalog-schema/%s/citrus-catalog-schema-%s.jar",
            MAVEN_CENTRAL, version, version);

        System.out.println("  Downloading Citrus schemas for version " + version + "...");

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(jarUrl))
            .timeout(TIMEOUT)
            .GET()
            .build();

        HttpResponse<InputStream> response = httpClient.send(request,
            HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to download Citrus schemas: HTTP " + response.statusCode());
        }

        // Extract JSON schema files from JAR
        String schemaPrefix = "org/citrusframework/schema/citrus/" + version + "/";
        int fileCount = 0;

        try (ZipInputStream zis = new ZipInputStream(response.body())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                // Extract files from org/citrusframework/schema/citrus/{version}/
                if (name.startsWith(schemaPrefix) && !entry.isDirectory()) {
                    String fileName = name.substring(schemaPrefix.length());
                    Path targetFile = citrusDir.resolve(fileName);

                    // Create parent directories if needed
                    Files.createDirectories(targetFile.getParent());

                    byte[] data = zis.readAllBytes();
                    Files.write(targetFile, data);
                    fileCount++;
                }
            }
        }

        System.out.println("    Extracted " + fileCount + " schema files");

        // Generate quick reference from schemas
        System.out.println("    Generating quick reference...");
        CitrusSchemaProcessor processor = new CitrusSchemaProcessor();
        processor.generateQuickReference(citrusDir);

        if (Files.exists(indexFile)) {
            return mapper.readTree(Files.readString(indexFile));
        }

        return mapper.createObjectNode();
    }

    /**
     * Get the path to the cached Citrus schemas directory.
     */
    public Path getCitrusSchemasDir(String version) {
        return cacheDir.resolve("citrus").resolve(version);
    }
}
