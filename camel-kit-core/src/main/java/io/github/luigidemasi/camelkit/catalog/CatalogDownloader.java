package io.github.luigidemasi.camelkit.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Downloads and caches Camel catalog JSON files.
 * Fetches JSON from Maven/GitHub.
 */
public class CatalogDownloader {

    private static final String MAVEN_CENTRAL = "https://repo1.maven.org/maven2";
    private static final String REDHAT_GA = "https://maven.repository.redhat.com/ga";
    private static final String GITHUB_RAW = "https://raw.githubusercontent.com/apache/camel";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final Path cacheDir;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    public CatalogDownloader(Path cacheDir) {
        this.cacheDir = cacheDir;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
        this.mapper = new ObjectMapper();
    }

    /**
     * Fetch component catalog for a Camel version.
     * Downloads from Maven Central and extracts component JSONs.
     */
    public JsonNode fetchComponentCatalog(String version, boolean forceRefresh) throws Exception {
        Path cacheFile = cacheDir.resolve("components-" + version + ".json");

        if (!forceRefresh && Files.exists(cacheFile)) {
            return mapper.readTree(Files.readString(cacheFile));
        }

        Files.createDirectories(cacheDir);

        // Download camel-catalog JAR and extract component list
        String repo = version.contains(".redhat-") ? REDHAT_GA : MAVEN_CENTRAL;
        String jarUrl = String.format("%s/org/apache/camel/camel-catalog/%s/camel-catalog-%s.jar",
            repo, version, version);

        System.out.println("  Downloading component catalog for Camel " + version + "...");

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(jarUrl))
            .timeout(TIMEOUT)
            .GET()
            .build();

        HttpResponse<InputStream> response = httpClient.send(request,
            HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to download catalog: HTTP " + response.statusCode());
        }

        // Extract component JSON files from JAR
        ObjectNode catalog = mapper.createObjectNode();
        catalog.put("version", version);
        catalog.put("fetchedAt", Instant.now().toString());
        ArrayNode components = catalog.putArray("components");

        try (ZipInputStream zis = new ZipInputStream(response.body())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                // Component JSONs are in org/apache/camel/catalog/components/*.json
                if (name.startsWith("org/apache/camel/catalog/components/") && name.endsWith(".json")) {
                    String componentName = name.substring(name.lastIndexOf('/') + 1, name.length() - 5);
                    byte[] data = zis.readAllBytes();
                    JsonNode componentJson = mapper.readTree(data);

                    ObjectNode comp = mapper.createObjectNode();
                    comp.put("name", componentName);
                    comp.set("component", componentJson.get("component"));
                    components.add(comp);
                }
            }
        }

        catalog.put("componentCount", components.size());

        // Save to cache
        Files.writeString(cacheFile, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(catalog));

        return catalog;
    }

    /**
     * Fetch Kamelet catalog from GitHub.
     */
    public JsonNode fetchKameletCatalog(String version, boolean forceRefresh) throws Exception {
        Path cacheFile = cacheDir.resolve("kamelets-" + version.replace(".", "_") + ".json");

        if (!forceRefresh && Files.exists(cacheFile)) {
            return mapper.readTree(Files.readString(cacheFile));
        }

        Files.createDirectories(cacheDir);

        // Try versioned tag first, then main branch
        String apiUrl = "https://api.github.com/repos/apache/camel-kamelets/contents/kamelets?ref=v" + version;

        System.out.println("  Downloading Kamelet catalog...");

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(apiUrl))
            .header("Accept", "application/vnd.github.v3+json")
            .timeout(TIMEOUT)
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        ObjectNode catalog = mapper.createObjectNode();
        catalog.put("version", version);
        catalog.put("fetchedAt", Instant.now().toString());
        ArrayNode kamelets = catalog.putArray("kamelets");

        if (response.statusCode() == 200) {
            JsonNode files = mapper.readTree(response.body());
            for (JsonNode file : files) {
                String name = file.get("name").asText();
                if (name.endsWith(".kamelet.yaml")) {
                    String kameletName = name.replace(".kamelet.yaml", "");
                    String type = "action";
                    if (kameletName.endsWith("-source")) type = "source";
                    else if (kameletName.endsWith("-sink")) type = "sink";

                    ObjectNode kam = mapper.createObjectNode();
                    kam.put("name", kameletName);
                    kam.put("type", type);
                    kamelets.add(kam);
                }
            }
        }

        catalog.put("kameletCount", kamelets.size());

        Files.writeString(cacheFile, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(catalog));

        return catalog;
    }

    /**
     * Fetch YAML DSL schema from GitHub.
     */
    public void fetchYamlSchema(String version, boolean forceRefresh) throws Exception {
        Path cacheFile = cacheDir.resolve("camelYamlDsl-" + version + ".json");

        if (!forceRefresh && Files.exists(cacheFile)) {
            return;
        }

        Files.createDirectories(cacheDir);

        String schemaUrl = String.format("%s/camel-%s/dsl/camel-yaml-dsl/camel-yaml-dsl/src/generated/resources/schema/camelYamlDsl.json",
            GITHUB_RAW, version);

        System.out.println("  Downloading YAML DSL schema...");

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(schemaUrl))
            .timeout(TIMEOUT)
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            Files.writeString(cacheFile, response.body());
        } else {
            System.out.println("    (schema not found for version " + version + ")");
        }
    }

    /**
     * Get latest LTS version from Maven Central.
     */
    public String getLatestLtsVersion() throws Exception {
        String searchUrl = "https://search.maven.org/solrsearch/select?q=g:org.apache.camel+AND+a:camel-catalog&rows=20&wt=json&core=gav";

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(searchUrl))
            .timeout(TIMEOUT)
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            JsonNode root = mapper.readTree(response.body());
            JsonNode docs = root.path("response").path("docs");

            for (JsonNode doc : docs) {
                String version = doc.path("v").asText();
                // LTS versions are x.4.y and x.8.y
                String[] parts = version.split("\\.");
                if (parts.length >= 2) {
                    try {
                        int minor = Integer.parseInt(parts[1]);
                        if (minor == 4 || minor == 8) {
                            return version;
                        }
                    } catch (NumberFormatException e) {
                        // ignore
                    }
                }
            }

            // Return first available if no LTS found
            if (docs.size() > 0) {
                return docs.get(0).path("v").asText();
            }
        }

        return "4.8.0"; // fallback
    }

    /**
     * Search components in cached catalog.
     */
    public List<JsonNode> searchComponents(String query, JsonNode catalog, int limit) {
        List<JsonNode> results = new ArrayList<>();
        String q = query.toLowerCase();

        JsonNode components = catalog.get("components");
        if (components != null) {
            for (JsonNode comp : components) {
                String name = comp.path("name").asText().toLowerCase();
                String title = comp.path("component").path("title").asText().toLowerCase();
                String desc = comp.path("component").path("description").asText().toLowerCase();

                if (name.contains(q) || title.contains(q) || desc.contains(q)) {
                    results.add(comp);
                    if (results.size() >= limit) break;
                }
            }
        }

        return results;
    }
}
