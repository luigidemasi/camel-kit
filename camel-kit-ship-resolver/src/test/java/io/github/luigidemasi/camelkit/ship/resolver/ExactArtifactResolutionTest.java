package io.github.luigidemasi.camelkit.ship.resolver;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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
    void onlineResolutionRejectsAPoisonedCacheAndBindsMatchingCentralBytes() throws IOException {
        MavenCoordinate coordinate = MavenCoordinate.jar("example.test", "catalog", "1.0.0");
        seed(coordinate, "poisoned-cache");

        IOException poisoned = assertThrows(IOException.class, () -> ShipMavenResolver.resolveArtifacts(
                repository,
                List.of(coordinate),
                ShipMavenResolver.ResolutionMode.ONLINE,
                (uri, maxBytes) -> identity("central-bytes")));
        assertTrue(poisoned.getMessage().contains("differs from authoritative Maven Central bytes"));

        seed(coordinate, "central-bytes");
        AtomicReference<URI> requested = new AtomicReference<>();
        ResolvedExactMavenArtifact resolved = ShipMavenResolver.resolveArtifacts(
                repository,
                List.of(coordinate),
                ShipMavenResolver.ResolutionMode.ONLINE,
                (uri, maxBytes) -> {
                    requested.set(uri);
                    assertTrue("central-bytes".getBytes(StandardCharsets.UTF_8).length <= maxBytes);
                    return identity("central-bytes");
                }).get(0);

        assertEquals(URI.create(
                "https://repo.maven.apache.org/maven2/example/test/catalog/1.0.0/catalog-1.0.0.jar"),
                requested.get());
        assertEquals("central-bytes".length(), resolved.contentLength());
        assertEquals(sha256("central-bytes".getBytes(StandardCharsets.UTF_8)), resolved.contentSha256());
    }

    @Test
    void directCentralTransportDisablesRedirectsAndContentEncodingAndFollowsJvmProxyDefaults() {
        URI uri = URI.create("https://repo.maven.apache.org/maven2/example.test");
        HttpClient client = ShipMavenResolver.directClient();
        HttpRequest request = ShipMavenResolver.directRequest(uri);

        assertSame(client, ShipMavenResolver.directClient());
        assertEquals(HttpClient.Redirect.NEVER, client.followRedirects());
        // No explicit proxy selector: the client follows the JVM default, which
        // honors the standard proxy system properties.
        assertTrue(client.proxy().isEmpty());
        assertEquals("identity", request.headers().firstValue("Accept-Encoding").orElseThrow());
        assertEquals(uri, request.uri());
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

    @Test
    void directFetchTimesOutAndClosesAnExchangeStalledBeforeHeaders() throws Exception {
        assertStalledExchangeIsBounded(false);
    }

    @Test
    void directFetchTimesOutAndClosesAnExchangeStalledMidBody() throws Exception {
        assertStalledExchangeIsBounded(true);
    }

    @Test
    void directFetchNormalizesTheJdkRequestTimeout() {
        HttpTimeoutException jdkFailure = new HttpTimeoutException("request timed out");

        IOException normalized = ShipMavenResolver.directFetchFailure(jdkFailure);

        assertEquals("Timed out fetching exact artifact bytes from Maven Central", normalized.getMessage());
        assertSame(jdkFailure, normalized.getCause());
    }

    @Test
    void directBodySubscriberCancelsExactlyOnceAndRejectsLateSignals() {
        ShipMavenResolver.DirectBodySubscriber overflow = new ShipMavenResolver.DirectBodySubscriber(2);
        RecordingSubscription overflowSubscription = new RecordingSubscription();
        overflow.onSubscribe(overflowSubscription);
        overflow.onNext(List.of(ByteBuffer.wrap(new byte[3])));

        CompletionException overflowFailure = assertThrows(
                CompletionException.class, () -> overflow.getBody().toCompletableFuture().join());
        assertInstanceOf(IOException.class, overflowFailure.getCause());
        assertEquals(1, overflowSubscription.cancellations.get());
        overflow.onComplete();
        overflow.onNext(List.of(ByteBuffer.wrap(new byte[]{1})));
        overflow.abort(new IOException("late abort"));
        assertEquals(1, overflowSubscription.cancellations.get());

        ShipMavenResolver.DirectBodySubscriber aborted = new ShipMavenResolver.DirectBodySubscriber(2);
        RecordingSubscription abortedSubscription = new RecordingSubscription();
        aborted.onSubscribe(abortedSubscription);
        aborted.abort(new IOException("deadline"));
        aborted.abort(new IOException("duplicate deadline"));
        aborted.onComplete();
        assertEquals(1, abortedSubscription.cancellations.get());
    }

    private static void assertStalledExchangeIsBounded(boolean partialBody) throws Exception {
        ExecutorService serverExecutor = Executors.newSingleThreadExecutor();
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            Future<Boolean> clientClosed = serverExecutor.submit(() -> {
                try (Socket socket = server.accept()) {
                    socket.setSoTimeout(3_000);
                    readRequest(socket.getInputStream());
                    if (partialBody) {
                        socket.getOutputStream().write(("HTTP/1.1 200 OK\r\n"
                                                        + "Content-Length: 2\r\n"
                                                        + "Content-Encoding: identity\r\n"
                                                        + "Connection: close\r\n\r\n"
                                                        + "x")
                                .getBytes(StandardCharsets.US_ASCII));
                        socket.getOutputStream().flush();
                    }
                    return socket.getInputStream().read() == -1;
                }
            });

            URI endpoint = URI.create("http://127.0.0.1:" + server.getLocalPort() + "/artifact.jar");
            IOException failure = assertThrows(IOException.class,
                    () -> ShipMavenResolver.fetchDirect(endpoint, 2, Duration.ofMillis(300)));

            assertTrue(failure.getMessage().contains("Timed out"), failure.getMessage());
            assertTrue(clientClosed.get(2, TimeUnit.SECONDS), "cancelled exchange did not close its connection");
        } finally {
            serverExecutor.shutdownNow();
            assertTrue(serverExecutor.awaitTermination(3, TimeUnit.SECONDS));
        }
    }

    private static void readRequest(InputStream input) throws IOException {
        int matched = 0;
        byte[] terminator = "\r\n\r\n".getBytes(StandardCharsets.US_ASCII);
        while (matched < terminator.length) {
            int next = input.read();
            if (next < 0) {
                throw new IOException("client closed before sending an HTTP request");
            }
            matched = next == terminator[matched] ? matched + 1 : next == terminator[0] ? 1 : 0;
        }
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

    private static ShipMavenResolver.ContentIdentity identity(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        return new ShipMavenResolver.ContentIdentity(sha256(bytes), bytes.length);
    }

    private static final class RecordingSubscription implements Flow.Subscription {
        private final AtomicInteger cancellations = new AtomicInteger();

        @Override
        public void request(long count) {
            // The fixture emits data explicitly.
        }

        @Override
        public void cancel() {
            cancellations.incrementAndGet();
        }
    }
}
