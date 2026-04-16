package io.github.luigidemasi.camelkit.catalog;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Consumer;

/**
 * Populates a local Maven repository with artifacts needed for offline operation.
 * <p>
 * Artifacts are first looked up on the classpath (under {@code offline-repo/}) where
 * they are placed at build time by {@code maven-dependency-plugin}. If not found on
 * the classpath, they are downloaded from Maven Central or Red Hat GA.
 * <p>
 * The local repo uses standard Maven layout with minimal POMs (no parent references),
 * {@code _remote.repositories} markers, and SHA-1 checksums so that JBang's Maven
 * Resolver can resolve artifacts from a {@code file://} repository.
 */
public class OfflineRepoPopulator {

    private static final String DISTRIBUTION_REPO = io.github.luigidemasi.camelkit.CamelKitMain.distribution().mavenRepo();
    private static final String MAVEN_CENTRAL = "https://repo1.maven.org/maven2";
    private static final String CLASSPATH_PREFIX = "offline-repo/";
    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    /** The MCP runner version to download (community release). */
    public static final String CAMEL_MCP_VERSION = io.github.luigidemasi.camelkit.CamelKitMain.CAMEL_MCP_VERSION;

    /** Repo ID used in _remote.repositories markers. Must match the --repos ID in MCP configs. */
    public static final String REPO_ID = "camel-kit";

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
     * Extracts from classpath first (build-time bundled), falls back to HTTP download.
     *
     * @param camelVersion the Camel version to resolve catalogs for
     * @param knowledgeMcpVersion the knowledge MCP version (unused, reserved for future)
     * @return number of artifacts installed
     */
    public int populate(String camelVersion, String knowledgeMcpVersion) throws Exception {
        Files.createDirectories(repoDir);
        List<ArtifactSpec> artifacts = new ArrayList<>();

        // 1. Upstream Camel MCP runner JAR (community, from Maven Central)
        artifacts.add(new ArtifactSpec(
                MAVEN_CENTRAL,
                "org.apache.camel", "camel-jbang-mcp", CAMEL_MCP_VERSION, "runner", "jar"));

        // 2. Catalog JARs for the configured version (from distribution repo)
        VersionMapping.CatalogVersions versions = VersionMapping.resolve(camelVersion);
        if (versions != null) {
            artifacts.add(new ArtifactSpec(
                    DISTRIBUTION_REPO,
                    "org.apache.camel", "camel-catalog", versions.camelCatalog(), null, "jar"));
            artifacts.add(new ArtifactSpec(
                    DISTRIBUTION_REPO,
                    "org.apache.camel.springboot", "camel-catalog-provider-springboot",
                    versions.springBootProvider(), null, "jar"));
            artifacts.add(new ArtifactSpec(
                    DISTRIBUTION_REPO,
                    "org.apache.camel.quarkus", "camel-quarkus-catalog",
                    versions.quarkusCatalog(), null, "jar"));
        }

        int installed = 0;
        for (ArtifactSpec spec : artifacts) {
            if (installArtifact(spec)) {
                installed++;
            }
        }

        return installed;
    }

    private boolean installArtifact(ArtifactSpec spec) throws Exception {
        Path jarPath = artifactPath(spec);

        if (Files.exists(jarPath)) {
            log.accept("  Already cached: " + spec.artifactId() + ":" + spec.version()
                    + (spec.classifier() != null ? ":" + spec.classifier() : ""));
            return false;
        }

        Files.createDirectories(jarPath.getParent());

        // Try classpath first (build-time bundled by maven-dependency-plugin)
        String resourceName = CLASSPATH_PREFIX + spec.jarFilename();
        try (InputStream classpathJar = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            if (classpathJar != null) {
                log.accept("  Extracting: " + spec.artifactId() + ":" + spec.version()
                        + (spec.classifier() != null ? ":" + spec.classifier() : ""));
                Files.copy(classpathJar, jarPath, StandardCopyOption.REPLACE_EXISTING);
            } else {
                // Fall back to HTTP download
                log.accept("  Downloading: " + spec.artifactId() + ":" + spec.version()
                        + (spec.classifier() != null ? ":" + spec.classifier() : ""));
                if (!downloadFile(spec.remoteUrl(), jarPath)) {
                    return false;
                }
            }
        }
        writeSha1(jarPath);

        // Write minimal POM without parent references — Maven Resolver
        // would otherwise try to walk the entire parent POM chain
        Path pomPath = pomPath(spec);
        if (!Files.exists(pomPath)) {
            writeMinimalPom(spec, pomPath);
            writeSha1(pomPath);
        }

        // Write _remote.repositories marker so Maven Resolver
        // recognises this artifact as properly installed
        writeRemoteRepositories(spec);

        return true;
    }

    private boolean downloadFile(String url, Path target) throws Exception {
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

        try (InputStream in = response.body()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return true;
    }

    private Path artifactPath(ArtifactSpec spec) {
        return versionDir(spec).resolve(spec.jarFilename());
    }

    private Path pomPath(ArtifactSpec spec) {
        return versionDir(spec).resolve(spec.pomFilename());
    }

    private Path versionDir(ArtifactSpec spec) {
        return repoDir
                .resolve(spec.groupId().replace('.', '/'))
                .resolve(spec.artifactId())
                .resolve(spec.version());
    }

    private void writeRemoteRepositories(ArtifactSpec spec) throws Exception {
        Path marker = versionDir(spec).resolve("_remote.repositories");

        StringBuilder sb = new StringBuilder();
        sb.append("#NOTE: This is a Maven Resolver internal implementation file, its format can be changed without prior notice.\n");
        sb.append("#").append(Instant.now()).append("\n");
        sb.append(spec.jarFilename()).append(">").append(REPO_ID).append("=\n");
        sb.append(spec.pomFilename()).append(">").append(REPO_ID).append("=\n");

        Files.writeString(marker, sb.toString());
    }

    private void writeSha1(Path file) throws Exception {
        byte[] content = Files.readAllBytes(file);
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        byte[] hash = digest.digest(content);
        Files.writeString(Path.of(file + ".sha1"), HexFormat.of().formatHex(hash));
    }

    private void writeMinimalPom(ArtifactSpec spec, Path pomPath) throws Exception {
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
            return repoUrl + "/" + groupId.replace('.', '/') + "/" + artifactId
                    + "/" + version + "/" + jarFilename();
        }

        String jarFilename() {
            return classifier != null
                    ? artifactId + "-" + version + "-" + classifier + "." + type
                    : artifactId + "-" + version + "." + type;
        }

        String pomFilename() {
            return artifactId + "-" + version + ".pom";
        }
    }
}
