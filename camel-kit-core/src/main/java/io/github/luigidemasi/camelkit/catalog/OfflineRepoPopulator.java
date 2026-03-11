package io.github.luigidemasi.camelkit.catalog;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Downloads Maven artifacts into a local file-based repository for offline use.
 * The local repo uses standard Maven layout so JBang and Maven Resolver can resolve from it.
 */
public class OfflineRepoPopulator {

    private static final String RED_HAT_GA = "https://maven.repository.redhat.com/ga";
    private static final String MAVEN_CENTRAL = "https://repo1.maven.org/maven2";
    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    /** The MCP runner version to download (community release). */
    public static final String CAMEL_MCP_VERSION = "4.18.0";

    private final Path repoDir;
    private final HttpClient httpClient;
    private final Consumer<String> log;

    public OfflineRepoPopulator(Path repoDir, Consumer<String> log) {
        this.repoDir = repoDir;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.log = log;
    }

    /**
     * Populate the local repo with all artifacts needed for offline operation.
     *
     * @param camelVersion the Camel version to resolve catalogs for (e.g., "4.14.4.redhat-00008" or "4.14")
     * @param knowledgeMcpVersion the knowledge MCP version (null to skip)
     * @return number of artifacts downloaded
     */
    public int populate(String camelVersion, String knowledgeMcpVersion) throws Exception {
        Files.createDirectories(repoDir);
        List<ArtifactSpec> artifacts = new ArrayList<>();

        // 1. Upstream Camel MCP runner JAR (community, from Maven Central)
        artifacts.add(new ArtifactSpec(
                MAVEN_CENTRAL,
                "org.apache.camel", "camel-jbang-mcp", CAMEL_MCP_VERSION, "runner", "jar"));

        // 2. Catalog JARs for the configured version (from Red Hat GA)
        VersionMapping.CatalogVersions versions = VersionMapping.resolve(camelVersion);
        if (versions != null) {
            artifacts.add(new ArtifactSpec(
                    RED_HAT_GA,
                    "org.apache.camel", "camel-catalog", versions.camelCatalog(), null, "jar"));
            artifacts.add(new ArtifactSpec(
                    RED_HAT_GA,
                    "org.apache.camel.springboot", "camel-catalog-provider-springboot",
                    versions.springBootProvider(), null, "jar"));
            artifacts.add(new ArtifactSpec(
                    RED_HAT_GA,
                    "org.apache.camel.quarkus", "camel-quarkus-catalog",
                    versions.quarkusCatalog(), null, "jar"));
        }

        int downloaded = 0;
        for (ArtifactSpec spec : artifacts) {
            if (downloadArtifact(spec)) {
                downloaded++;
            }
        }

        return downloaded;
    }

    private boolean downloadArtifact(ArtifactSpec spec) throws Exception {
        Path localPath = artifactPath(spec);

        if (Files.exists(localPath)) {
            log.accept("  Already cached: " + spec.artifactId() + ":" + spec.version()
                    + (spec.classifier() != null ? ":" + spec.classifier() : ""));
            return false;
        }

        String url = spec.remoteUrl();
        log.accept("  Downloading: " + spec.artifactId() + ":" + spec.version()
                + (spec.classifier() != null ? ":" + spec.classifier() : ""));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(TIMEOUT)
                .GET()
                .build();

        HttpResponse<InputStream> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
            log.accept("    Warning: HTTP " + response.statusCode() + " for " + url);
            return false;
        }

        Files.createDirectories(localPath.getParent());
        try (InputStream in = response.body()) {
            Files.copy(in, localPath, StandardCopyOption.REPLACE_EXISTING);
        }

        // Write minimal POM so Maven resolver is happy
        writePom(spec);

        return true;
    }

    private Path artifactPath(ArtifactSpec spec) {
        String filename = spec.classifier() != null
                ? spec.artifactId() + "-" + spec.version() + "-" + spec.classifier() + "." + spec.type()
                : spec.artifactId() + "-" + spec.version() + "." + spec.type();
        return repoDir
                .resolve(spec.groupId().replace('.', '/'))
                .resolve(spec.artifactId())
                .resolve(spec.version())
                .resolve(filename);
    }

    private void writePom(ArtifactSpec spec) throws Exception {
        String pomFilename = spec.artifactId() + "-" + spec.version() + ".pom";
        Path pomPath = repoDir
                .resolve(spec.groupId().replace('.', '/'))
                .resolve(spec.artifactId())
                .resolve(spec.version())
                .resolve(pomFilename);

        if (Files.exists(pomPath)) return;

        String pom = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>%s</groupId>
                  <artifactId>%s</artifactId>
                  <version>%s</version>
                </project>
                """.formatted(spec.groupId(), spec.artifactId(), spec.version());
        Files.writeString(pomPath, pom);
    }

    private record ArtifactSpec(
            String repoUrl, String groupId, String artifactId,
            String version, String classifier, String type) {

        String remoteUrl() {
            String filename = classifier != null
                    ? artifactId + "-" + version + "-" + classifier + "." + type
                    : artifactId + "-" + version + "." + type;
            return repoUrl + "/" + groupId.replace('.', '/') + "/" + artifactId + "/" + version + "/" + filename;
        }
    }
}
