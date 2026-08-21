package io.github.luigidemasi.camelkit.ship.resolver;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.supplier.RepositorySystemSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class ExactArtifactResolutionTest {

    @TempDir
    Path repository;

    @Test
    void resolvesExactJarAndPomOfflineInRequestOrderWithoutParsingDescriptors() throws IOException {
        MavenCoordinate jar = MavenCoordinate.jar("example.test", "catalog", "1.0.0");
        MavenCoordinate pom = jar.withExtension("pom");
        seed(jar, "not a jar");
        seed(pom, "<not-a-valid-pom>");

        List<ResolvedExactMavenArtifact> resolved = ShipMavenResolver.resolveArtifacts(
                repository, List.of(pom, jar), ShipMavenResolver.ResolutionMode.OFFLINE);

        assertEquals(List.of(pom, jar), resolved.stream().map(ResolvedExactMavenArtifact::coordinate).toList());
        assertEquals(List.of(path(pom), path(jar)), resolved.stream().map(ResolvedExactMavenArtifact::path).toList());
        assertEquals("<not-a-valid-pom>", Files.readString(resolved.get(0).path()));
    }

    @Test
    void rejectsDuplicateAndExcessiveExactRequests() throws IOException {
        MavenCoordinate coordinate = MavenCoordinate.jar("example.test", "catalog", "1.0.0");
        IOException duplicate = assertThrows(IOException.class, () -> ShipMavenResolver.resolveArtifacts(
                repository,
                List.of(coordinate, coordinate),
                ShipMavenResolver.ResolutionMode.OFFLINE));

        List<MavenCoordinate> excessive = IntStream.rangeClosed(0, ShipMavenResolver.MAX_ARTIFACTS)
                .mapToObj(index -> MavenCoordinate.jar("example.test", "catalog-" + index, "1.0.0"))
                .toList();
        IOException aboveLimit = assertThrows(IOException.class, () -> ShipMavenResolver.resolveArtifacts(
                repository, excessive, ShipMavenResolver.ResolutionMode.OFFLINE));

        String expected = "Ship resolver requires between 1 and "
                          + ShipMavenResolver.MAX_ARTIFACTS + " unique exact artifacts";
        assertEquals(expected, duplicate.getMessage());
        assertEquals(expected, aboveLimit.getMessage());
    }

    @Test
    void acceptsTheExactArtifactRequestLimitOffline() throws IOException {
        List<MavenCoordinate> coordinates = IntStream.range(0, ShipMavenResolver.MAX_ARTIFACTS)
                .mapToObj(index -> MavenCoordinate.jar("example.test", "catalog-" + index, "1.0.0"))
                .toList();
        for (MavenCoordinate coordinate : coordinates) {
            seed(coordinate, "artifact");
        }

        List<ResolvedExactMavenArtifact> resolved = ShipMavenResolver.resolveArtifacts(
                repository, coordinates, ShipMavenResolver.ResolutionMode.OFFLINE);

        assertEquals(coordinates, resolved.stream().map(ResolvedExactMavenArtifact::coordinate).toList());
    }

    @Test
    void resolvedArtifactToStringRedactsTheRepositoryPath() throws IOException {
        MavenCoordinate coordinate = MavenCoordinate.jar("example.test", "catalog", "1.0.0");
        seed(coordinate, "artifact");

        ResolvedExactMavenArtifact resolved = ShipMavenResolver.resolveArtifacts(
                repository, List.of(coordinate), ShipMavenResolver.ResolutionMode.OFFLINE).get(0);

        assertFalse(resolved.toString().contains(repository.toString()));
        assertTrue(resolved.toString().contains("path=<redacted>"));
    }

    @Test
    void missingExactArtifactFailsOffline() {
        MavenCoordinate missing = MavenCoordinate.jar("example.test", "missing", "1.0.0");

        IOException failure = assertThrows(IOException.class, () -> ShipMavenResolver.resolveArtifacts(
                repository, List.of(missing), ShipMavenResolver.ResolutionMode.OFFLINE));

        assertTrue(failure.getMessage().startsWith("Could not resolve an exact Ship artifact:"),
                failure.getMessage());
    }

    @Test
    void onlineResolutionBindsIdentityToTheLocallyCachedBytes() throws IOException {
        MavenCoordinate coordinate = MavenCoordinate.jar("example.test", "catalog", "1.0.0");
        byte[] cached = "cached-bytes".getBytes(StandardCharsets.UTF_8);
        seed(coordinate, "cached-bytes");

        ResolvedExactMavenArtifact resolved = ShipMavenResolver.resolveArtifacts(
                repository, List.of(coordinate), ShipMavenResolver.ResolutionMode.ONLINE).get(0);

        // A warm cache is served without touching the network and its bytes are trusted as-is;
        // the recorded identity must match the cached bytes exactly.
        assertEquals(cached.length, resolved.contentLength());
        assertEquals(sha256(cached), resolved.contentSha256());
    }

    @Test
    void aetherCacheFillDisablesRedirectsAndHonorsJvmTransportProperties() {
        var session = ShipMavenResolver.session(
                new RepositorySystemSupplier().get(), repository, ShipMavenResolver.ResolutionMode.ONLINE);
        Map<String, Object> configuration = session.getConfigProperties();
        RemoteRepository central = new RemoteRepository.Builder(
                "camel-kit-central", "default", "https://repo.maven.apache.org/maven2/").build();

        assertEquals(false, configuration.get(ConfigurationProperties.HTTP_FOLLOW_REDIRECTS));
        assertEquals(0, configuration.get(ConfigurationProperties.HTTP_MAX_REDIRECTS));
        assertEquals(ConfigurationProperties.HTTPS_SECURITY_MODE_DEFAULT,
                configuration.get(ConfigurationProperties.HTTPS_SECURITY_MODE));
        assertEquals(true, configuration.get("aether.connector.http.useSystemProperties"));
        assertEquals("SHA-1", configuration.get("aether.checksums.algorithms"));
        // No Aether-level proxy override: transport proxying is decided by the
        // JVM's standard proxy system properties.
        assertNull(session.getProxySelector().getProxy(central));
    }

    @Test
    void resolutionToleratesProxyAndTrustStorePropertiesButStillRefusesRepositoryOverrides() throws IOException {
        MavenCoordinate coordinate = MavenCoordinate.jar("example.test", "catalog", "1.0.0");
        seed(coordinate, "artifact");

        withProperty("https.proxyHost", "proxy.corp.example", () -> {
            List<ResolvedExactMavenArtifact> resolved = ShipMavenResolver.resolveArtifacts(
                    repository, List.of(coordinate), ShipMavenResolver.ResolutionMode.OFFLINE);
            assertEquals(coordinate, resolved.get(0).coordinate());
        });
        withProperty("javax.net.ssl.trustStore", "/etc/pki/corp-truststore.p12", () -> {
            List<ResolvedExactMavenArtifact> resolved = ShipMavenResolver.resolveArtifacts(
                    repository, List.of(coordinate), ShipMavenResolver.ResolutionMode.OFFLINE);
            assertEquals(coordinate, resolved.get(0).coordinate());
        });
        withProperty("camel.extra.repos", "https://repo.example/maven", () -> {
            IOException refused = assertThrows(IOException.class, () -> ShipMavenResolver.resolveArtifacts(
                    repository, List.of(coordinate), ShipMavenResolver.ResolutionMode.OFFLINE));
            assertEquals("Ship resolver refuses repository override property camel.extra.repos",
                    refused.getMessage());
        });
    }

    private interface ResolutionProbe {
        void run() throws IOException;
    }

    private static void withProperty(String key, String value, ResolutionProbe probe) throws IOException {
        String previous = System.getProperty(key);
        System.setProperty(key, value);
        try {
            probe.run();
        } finally {
            if (previous == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, previous);
            }
        }
    }

    @Test
    void streamedIdentityAcceptsTheExactLimitAndRejectsEmptyOrOneOver() throws IOException {
        byte[] exact = new byte[1024];

        ShipMavenResolver.ContentIdentity identity = ShipMavenResolver.contentIdentity(
                new ByteArrayInputStream(exact), exact.length);

        assertEquals(exact.length, identity.length());
        assertEquals(sha256(exact), identity.sha256());
        assertThrows(IOException.class, () -> ShipMavenResolver.contentIdentity(
                new ByteArrayInputStream(new byte[0]), exact.length));
        assertThrows(IOException.class, () -> ShipMavenResolver.contentIdentity(
                new ByteArrayInputStream(new byte[exact.length + 1]), exact.length));
    }

    private void seed(MavenCoordinate coordinate, String content) throws IOException {
        Path artifact = path(coordinate);
        Files.createDirectories(artifact.getParent());
        Files.writeString(artifact, content, StandardCharsets.UTF_8);
        Files.writeString(
                artifact.getParent().resolve("_remote.repositories"),
                coordinate.fileName() + ">=\n",
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);
    }

    private Path path(MavenCoordinate coordinate) {
        return repository.resolve(coordinate.groupId().replace('.', '/'))
                .resolve(coordinate.artifactId())
                .resolve(coordinate.version())
                .resolve(coordinate.fileName());
    }

    private static String sha256(byte[] bytes) {
        try {
            return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
