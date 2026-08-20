package io.github.luigidemasi.camelkit.ship.resolver;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.Exclusion;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.supplier.RepositorySystemSupplier;
import org.eclipse.aether.util.repository.SimpleArtifactDescriptorPolicy;

/** Maven Resolver boundary whose third-party implementation is relocated in the published JAR. */
public final class ShipMavenResolver {

    private static final URI CENTRAL = URI.create("https://repo.maven.apache.org/maven2/");
    private static final long MAX_EXACT_JAR_BYTES = 128L * 1024 * 1024;
    private static final long MAX_EXACT_POM_BYTES = 4L * 1024 * 1024;
    private static final Duration DIRECT_FETCH_TIMEOUT = Duration.ofSeconds(30);
    private static final String DIRECT_FETCH_TIMEOUT_MESSAGE
            = "Timed out fetching exact artifact bytes from Maven Central";
    private static final ScheduledThreadPoolExecutor DIRECT_FETCH_DEADLINES = directFetchDeadlines();

    /** Fixed fail-closed ceiling for unique direct roots in one resolution request. */
    public static final int MAX_ROOTS = 64;

    /** Fixed fail-closed ceiling for artifacts returned by one resolution request. */
    public static final int MAX_ARTIFACTS = 512;

    /** Whether exact artifacts may be downloaded from the fixed Maven Central repository. */
    public enum ResolutionMode {
        OFFLINE,
        ONLINE
    }

    private ShipMavenResolver() {
    }

    /**
     * Resolves between one and {@value #MAX_ROOTS} unique roots and returns no more than {@value #MAX_ARTIFACTS}
     * artifacts. The bounds are part of the resolver contract and are not runtime configurable.
     */
    public static List<ResolvedMavenArtifact> resolve(
            Path repository, List<MavenDependencyRoot> roots)
            throws IOException {
        Path local = requireRepository(repository);
        List<MavenDependencyRoot> requested = List.copyOf(Objects.requireNonNull(roots, "roots must not be null"));
        if (requested.isEmpty() || requested.size() > MAX_ROOTS || Set.copyOf(requested).size() != requested.size()) {
            throw new IOException("Ship resolver requires between 1 and " + MAX_ROOTS + " unique roots");
        }
        rejectRepositoryOverrides();
        try {
            RepositorySystem system = repositorySystem();
            DefaultRepositorySystemSession session = session(system, local, ResolutionMode.ONLINE);
            CollectRequest collect = new CollectRequest();
            for (MavenDependencyRoot root : requested) {
                List<Exclusion> exclusions = root.exclusions().stream()
                        .map(exclusion -> new Exclusion(
                                exclusion.groupId(), exclusion.artifactId(), "*", "*"))
                        .toList();
                collect.addDependency(new Dependency(
                        new DefaultArtifact(root.coordinate().resolverString()), "runtime", false, exclusions));
            }
            collect.setRepositories(List.of(central()));
            DependencyResult resolved = system.resolveDependencies(session, new DependencyRequest(collect, null));
            if (!resolved.getCollectExceptions().isEmpty()) {
                throw new IOException(
                        "Ship resolver rejected an incomplete dependency graph: "
                                      + safeMessage(resolved.getCollectExceptions().get(0)));
            }
            List<ResolvedMavenArtifact> result = new ArrayList<>();
            for (var artifactResult : resolved.getArtifactResults()) {
                Artifact artifact = artifactResult.getArtifact();
                if (artifact == null || artifact.getFile() == null) {
                    throw new IOException("Ship resolver returned an incomplete artifact");
                }
                result.add(new ResolvedMavenArtifact(
                        new MavenCoordinate(
                                artifact.getGroupId(),
                                artifact.getArtifactId(),
                                artifact.getExtension(),
                                artifact.getClassifier(),
                                artifact.getVersion()),
                        artifact.getFile().toPath()));
                if (result.size() > MAX_ARTIFACTS) {
                    throw new IOException("Ship resolver returned more than " + MAX_ARTIFACTS + " artifacts");
                }
            }
            return List.copyOf(result);
        } catch (IOException e) {
            throw e;
        } catch (Exception | LinkageError e) {
            throw new IOException("Could not resolve the isolated Ship payload: " + safeMessage(e), e);
        }
    }

    /**
     * Resolves exact artifacts without reading descriptors or traversing dependencies. Requests must contain between
     * one and {@value #MAX_ARTIFACTS} unique immutable coordinates and results retain request order. Online results are
     * byte-matched against a direct, non-redirecting request to the fixed Maven Central origin; offline results are
     * bound to the local bytes. The online request relies on the controlling process's TLS, DNS, and security-provider
     * trust domain. Consumers must compare the returned identity with the bytes they actually read.
     */
    public static List<ResolvedExactMavenArtifact> resolveArtifacts(
            Path repository, List<MavenCoordinate> artifacts, ResolutionMode mode)
            throws IOException {
        return resolveArtifacts(repository, artifacts, mode, ShipMavenResolver::fetchDirect);
    }

    static List<ResolvedExactMavenArtifact> resolveArtifacts(
            Path repository,
            List<MavenCoordinate> artifacts,
            ResolutionMode mode,
            CentralDigestSource centralDigestSource)
            throws IOException {
        Path local = requireRepository(repository);
        List<MavenCoordinate> requested = copyExactArtifacts(artifacts);
        Objects.requireNonNull(mode, "mode must not be null");
        Objects.requireNonNull(centralDigestSource, "centralDigestSource must not be null");
        rejectRepositoryOverrides();
        try {
            RepositorySystem system = repositorySystem();
            DefaultRepositorySystemSession session = session(system, local, mode);
            List<ResolvedExactMavenArtifact> result = new ArrayList<>(requested.size());
            for (MavenCoordinate coordinate : requested) {
                rejectExistingSymlinks(local, coordinate);
                long limit = exactArtifactLimit(coordinate);
                ContentIdentity authoritative = null;
                if (mode == ResolutionMode.ONLINE) {
                    authoritative = centralDigestSource.identity(centralUri(coordinate), limit);
                    if (authoritative == null || authoritative.length() > limit) {
                        throw new IOException("Maven Central returned an invalid exact artifact identity");
                    }
                }
                ArtifactRequest request = new ArtifactRequest(
                        new DefaultArtifact(coordinate.resolverString()), List.of(central()), null);
                ArtifactResult resolved = system.resolveArtifact(session, request);
                ResolvedMavenArtifact artifact = requireExactArtifact(local, coordinate, resolved);
                ContentIdentity localContent = contentIdentity(artifact.path(), limit);
                if (authoritative != null && !authoritative.equals(localContent)) {
                    throw new IOException("Cached exact artifact differs from authoritative Maven Central bytes");
                }
                ContentIdentity identity = authoritative == null ? localContent : authoritative;
                result.add(new ResolvedExactMavenArtifact(
                        coordinate, artifact.path(), identity.sha256(), identity.length()));
            }
            return List.copyOf(result);
        } catch (IOException e) {
            throw e;
        } catch (Exception | LinkageError e) {
            throw new IOException("Could not resolve an exact Ship artifact: " + safeMessage(e), e);
        }
    }

    private static List<MavenCoordinate> copyExactArtifacts(List<MavenCoordinate> artifacts) throws IOException {
        Objects.requireNonNull(artifacts, "artifacts must not be null");
        List<MavenCoordinate> requested = new ArrayList<>();
        Set<MavenCoordinate> unique = new java.util.HashSet<>();
        for (MavenCoordinate coordinate : artifacts) {
            if (requested.size() == MAX_ARTIFACTS || coordinate == null || !unique.add(coordinate)) {
                throw new IOException(
                        "Ship resolver requires between 1 and " + MAX_ARTIFACTS + " unique exact artifacts");
            }
            requested.add(coordinate);
        }
        if (requested.isEmpty()) {
            throw new IOException(
                    "Ship resolver requires between 1 and " + MAX_ARTIFACTS + " unique exact artifacts");
        }
        return List.copyOf(requested);
    }

    private static Path requireRepository(Path repository) throws IOException {
        Path path = Objects.requireNonNull(repository, "repository must not be null").toAbsolutePath().normalize();
        if (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Ship resolver repository must be a non-symbolic-link directory");
        }
        return path.toRealPath(LinkOption.NOFOLLOW_LINKS);
    }

    private static RepositorySystem repositorySystem() {
        return new RepositorySystemSupplier().get();
    }

    static DefaultRepositorySystemSession session(
            RepositorySystem system, Path repository, ResolutionMode mode) {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
        // Maven's utility session ignores missing and invalid descriptors by default, which can silently
        // turn a transitive graph into direct roots only. An evidence payload must fail closed instead.
        session.setArtifactDescriptorPolicy(new SimpleArtifactDescriptorPolicy(false, false));
        session.setOffline(mode == ResolutionMode.OFFLINE);
        session.setIgnoreArtifactDescriptorRepositories(true);
        session.setChecksumPolicy(RepositoryPolicy.CHECKSUM_POLICY_FAIL);
        session.setUpdatePolicy(RepositoryPolicy.UPDATE_POLICY_NEVER);
        // Maven's JDK profile activator requires java.version. Derive it from the immutable VM runtime
        // view instead of accepting mutable/user-supplied model properties.
        session.setSystemProperties(Map.of("java.version", Runtime.version().toString()));
        session.setUserProperties(Map.of());
        session.setConfigProperties(Map.of(
                ConfigurationProperties.CONNECT_TIMEOUT, 10_000,
                ConfigurationProperties.REQUEST_TIMEOUT, 60_000,
                ConfigurationProperties.HTTP_FOLLOW_REDIRECTS, false,
                ConfigurationProperties.HTTP_MAX_REDIRECTS, 0,
                ConfigurationProperties.HTTPS_SECURITY_MODE,
                ConfigurationProperties.HTTPS_SECURITY_MODE_DEFAULT,
                // Honor the JVM's standard proxy and TLS system properties so Ship
                // resolves through the developer's configured transport (corporate
                // proxies, custom trust stores) instead of requiring a direct route.
                "aether.connector.http.useSystemProperties", true,
                "aether.checksums.algorithms", "SHA-1"));
        session.setLocalRepositoryManager(
                system.newLocalRepositoryManager(session, new LocalRepository(repository.toFile())));
        return session;
    }

    private static ResolvedMavenArtifact requireExactArtifact(
            Path repository, MavenCoordinate requested, ArtifactResult result)
            throws IOException {
        if (!result.isResolved() || !result.getExceptions().isEmpty()) {
            throw new IOException("Ship resolver returned an incomplete exact artifact result");
        }
        Artifact artifact = result.getArtifact();
        if (artifact == null || artifact.getFile() == null) {
            throw new IOException("Ship resolver returned an incomplete exact artifact");
        }
        MavenCoordinate actual = new MavenCoordinate(
                artifact.getGroupId(),
                artifact.getArtifactId(),
                artifact.getExtension(),
                artifact.getClassifier(),
                artifact.getVersion());
        if (!requested.equals(actual)) {
            throw new IOException("Ship resolver returned a different exact artifact");
        }

        Path expected = repository.resolve(requested.groupId().replace('.', '/'))
                .resolve(requested.artifactId())
                .resolve(requested.version())
                .resolve(requested.fileName())
                .normalize();
        Path returned = artifact.getFile().toPath().toAbsolutePath().normalize();
        if (!returned.equals(expected)) {
            throw new IOException("Ship resolver returned an artifact outside its exact repository path");
        }
        Path current = repository;
        for (Path part : repository.relativize(expected)) {
            current = current.resolve(part);
            if (Files.isSymbolicLink(current)) {
                throw new IOException("Ship resolver returned an artifact through a symbolic link");
            }
        }
        if (!Files.isRegularFile(expected, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Ship resolver returned a missing or non-regular exact artifact");
        }
        return new ResolvedMavenArtifact(actual, expected);
    }

    private static void rejectExistingSymlinks(Path repository, MavenCoordinate coordinate) throws IOException {
        Path expected = repository.resolve(coordinate.groupId().replace('.', '/'))
                .resolve(coordinate.artifactId())
                .resolve(coordinate.version())
                .resolve(coordinate.fileName())
                .normalize();
        Path current = repository;
        for (Path part : repository.relativize(expected)) {
            current = current.resolve(part);
            if (Files.isSymbolicLink(current)) {
                throw new IOException("Ship resolver repository path contains a symbolic link");
            }
            if (!Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                break;
            }
        }
    }

    private static long exactArtifactLimit(MavenCoordinate coordinate) {
        return "pom".equals(coordinate.extension()) ? MAX_EXACT_POM_BYTES : MAX_EXACT_JAR_BYTES;
    }

    private static URI centralUri(MavenCoordinate coordinate) throws IOException {
        String relative = coordinate.groupId().replace('.', '/') + '/' + coordinate.artifactId() + '/'
                          + coordinate.version() + '/' + coordinate.fileName();
        URI uri = CENTRAL.resolve(relative);
        if (!"https".equals(uri.getScheme()) || !CENTRAL.getHost().equals(uri.getHost())
                || uri.getPort() != -1 || uri.getUserInfo() != null
                || !uri.getPath().startsWith(CENTRAL.getPath())) {
            throw new IOException("Exact artifact coordinate escaped the fixed Maven Central origin");
        }
        return uri;
    }

    static ContentIdentity fetchDirect(URI uri, long maxBytes) throws IOException {
        return fetchDirect(uri, maxBytes, DIRECT_FETCH_TIMEOUT);
    }

    static ContentIdentity fetchDirect(URI uri, long maxBytes, Duration timeout) throws IOException {
        Objects.requireNonNull(uri, "uri must not be null");
        Objects.requireNonNull(timeout, "timeout must not be null");
        if (maxBytes <= 0 || timeout.isZero() || timeout.isNegative()) {
            throw new IOException("Direct Maven Central fetch bounds must be positive");
        }
        long timeoutNanos;
        try {
            timeoutNanos = timeout.toNanos();
        } catch (ArithmeticException e) {
            throw new IOException("Direct Maven Central fetch timeout is too large", e);
        }

        DirectBodyHandler handler = new DirectBodyHandler(maxBytes);
        CompletableFuture<HttpResponse<ContentIdentity>> exchange
                = directClient().sendAsync(directRequest(uri, timeout), handler);
        AtomicBoolean deadlineExpired = new AtomicBoolean();
        IOException timeoutFailure = new IOException(DIRECT_FETCH_TIMEOUT_MESSAGE);
        ScheduledFuture<?> watchdog = DIRECT_FETCH_DEADLINES.schedule(() -> {
            deadlineExpired.set(true);
            handler.abort(timeoutFailure);
            exchange.cancel(true);
        }, timeoutNanos, TimeUnit.NANOSECONDS);
        try {
            HttpResponse<ContentIdentity> response = exchange.get(timeoutNanos, TimeUnit.NANOSECONDS);
            if (!uri.equals(response.uri())) {
                throw new IOException("Maven Central returned an unexpected HTTP response");
            }
            return response.body();
        } catch (TimeoutException e) {
            deadlineExpired.set(true);
            handler.abort(timeoutFailure);
            exchange.cancel(true);
            throw timeoutFailure;
        } catch (InterruptedException e) {
            handler.abort(new IOException("Interrupted while fetching an exact artifact from Maven Central", e));
            exchange.cancel(true);
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while fetching an exact artifact from Maven Central", e);
        } catch (ExecutionException e) {
            IOException failure = directFetchFailure(e.getCause());
            if (e.getCause() instanceof HttpTimeoutException) {
                deadlineExpired.set(true);
                handler.abort(failure);
                exchange.cancel(true);
            }
            throw failure;
        } catch (CancellationException e) {
            if (deadlineExpired.get()) {
                throw timeoutFailure;
            }
            throw new IOException("Maven Central exact artifact fetch was cancelled", e);
        } finally {
            watchdog.cancel(false);
        }
    }

    static HttpClient directClient() {
        return DirectClientHolder.INSTANCE;
    }

    private static HttpClient newDirectClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    static HttpRequest directRequest(URI uri) {
        return directRequest(uri, DIRECT_FETCH_TIMEOUT);
    }

    private static HttpRequest directRequest(URI uri, Duration timeout) {
        return HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("Accept", "application/octet-stream")
                .header("Accept-Encoding", "identity")
                .GET()
                .build();
    }

    static IOException directFetchFailure(Throwable failure) {
        if (failure instanceof HttpTimeoutException) {
            return new IOException(DIRECT_FETCH_TIMEOUT_MESSAGE, failure);
        }
        if (failure instanceof IOException ioFailure) {
            return ioFailure;
        }
        return new IOException("Could not fetch exact artifact bytes from Maven Central", failure);
    }

    private static ScheduledThreadPoolExecutor directFetchDeadlines() {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, task -> {
            Thread thread = new Thread(task, "camel-kit-ship-central-fetch-deadline");
            thread.setDaemon(true);
            return thread;
        });
        executor.setRemoveOnCancelPolicy(true);
        executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        return executor;
    }

    private static ContentIdentity contentIdentity(Path path, long maxBytes) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            return contentIdentity(input, maxBytes);
        }
    }

    static ContentIdentity contentIdentity(InputStream input, long maxBytes) throws IOException {
        Objects.requireNonNull(input, "input must not be null");
        if (maxBytes <= 0) {
            throw new IOException("Exact artifact size limit must be positive");
        }
        MessageDigest digest = sha256();
        byte[] buffer = new byte[8192];
        long length = 0;
        int read;
        try {
            while ((read = input.read(buffer)) >= 0) {
                if (read == 0) {
                    continue;
                }
                length = Math.addExact(length, read);
                if (length > maxBytes) {
                    throw new IOException("Exact artifact exceeds its size limit");
                }
                digest.update(buffer, 0, read);
            }
        } catch (ArithmeticException e) {
            throw new IOException("Exact artifact size accounting overflowed", e);
        }
        if (length == 0) {
            throw new IOException("Exact artifact is empty");
        }
        return new ContentIdentity("sha256:" + HexFormat.of().formatHex(digest.digest()), length);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static RemoteRepository central() {
        RepositoryPolicy releases = new RepositoryPolicy(
                true, RepositoryPolicy.UPDATE_POLICY_NEVER, RepositoryPolicy.CHECKSUM_POLICY_FAIL);
        RepositoryPolicy snapshots = new RepositoryPolicy(
                false, RepositoryPolicy.UPDATE_POLICY_NEVER, RepositoryPolicy.CHECKSUM_POLICY_FAIL);
        return new RemoteRepository.Builder("camel-kit-central", "default", CENTRAL.toString())
                .setReleasePolicy(releases)
                .setSnapshotPolicy(snapshots)
                .build();
    }

    private static void rejectRepositoryOverrides() throws IOException {
        for (String key : List.of(
                "camel.extra.repos",
                "camel.default.extra.repos.default.value")) {
            if (System.getProperty(key) != null && !System.getProperty(key).isBlank()) {
                throw new IOException("Ship resolver refuses repository override property " + key);
            }
        }
    }

    private static String safeMessage(Throwable error) {
        return error.getMessage() == null || error.getMessage().isBlank()
                ? error.getClass().getSimpleName()
                : error.getMessage();
    }

    @FunctionalInterface
    interface CentralDigestSource {
        ContentIdentity identity(URI uri, long maxBytes) throws IOException;
    }

    record ContentIdentity(String sha256, long length) {
        ContentIdentity {
            if (sha256 == null || !sha256.matches("sha256:[0-9a-f]{64}") || length <= 0) {
                throw new IllegalArgumentException("Invalid exact artifact content identity");
            }
        }
    }

    static final class DirectBodySubscriber implements HttpResponse.BodySubscriber<ContentIdentity> {
        private final CompletableFuture<ContentIdentity> body = new CompletableFuture<>();
        private final MessageDigest digest = sha256();
        private final long maxBytes;
        private Flow.Subscription subscription;
        private long declaredLength = -1;
        private long length;
        private boolean terminal;

        DirectBodySubscriber(long maxBytes) {
            if (maxBytes <= 0) {
                throw new IllegalArgumentException("Direct body limit must be positive");
            }
            this.maxBytes = maxBytes;
        }

        synchronized void expectLength(long declaredLength) {
            if (!terminal) {
                this.declaredLength = declaredLength;
            }
        }

        @Override
        public CompletionStage<ContentIdentity> getBody() {
            return body;
        }

        @Override
        public void onSubscribe(Flow.Subscription nextSubscription) {
            Objects.requireNonNull(nextSubscription, "subscription must not be null");
            boolean cancel;
            synchronized (this) {
                cancel = terminal || subscription != null;
                if (!cancel) {
                    subscription = nextSubscription;
                }
            }
            if (cancel) {
                nextSubscription.cancel();
            } else {
                nextSubscription.request(1);
            }
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            IOException failure = null;
            Flow.Subscription next;
            synchronized (this) {
                if (terminal) {
                    return;
                }
                next = subscription;
                if (next == null) {
                    failure = new IOException("Maven Central body arrived before subscription");
                } else if (buffers == null) {
                    failure = new IOException("Maven Central returned an invalid response body");
                } else {
                    for (ByteBuffer buffer : buffers) {
                        if (buffer == null || buffer.remaining() > maxBytes - length) {
                            failure = new IOException("Exact artifact exceeds its size limit");
                            break;
                        }
                        int bytes = buffer.remaining();
                        digest.update(buffer);
                        length += bytes;
                    }
                }
            }
            if (failure != null) {
                abort(failure);
            } else {
                next.request(1);
            }
        }

        @Override
        public void onError(Throwable error) {
            abort(error instanceof IOException ioFailure
                    ? ioFailure
                    : new IOException("Could not read exact artifact bytes from Maven Central", error));
        }

        @Override
        public void onComplete() {
            ContentIdentity identity = null;
            IOException failure = null;
            synchronized (this) {
                if (terminal) {
                    return;
                }
                if (length == 0) {
                    failure = new IOException("Exact artifact is empty");
                } else if (declaredLength >= 0 && declaredLength != length) {
                    failure = new IOException("Maven Central response length does not match Content-Length");
                } else {
                    terminal = true;
                    identity = new ContentIdentity(
                            "sha256:" + HexFormat.of().formatHex(digest.digest()), length);
                }
            }
            if (failure != null) {
                abort(failure);
            } else {
                body.complete(identity);
            }
        }

        void abort(IOException failure) {
            Objects.requireNonNull(failure, "failure must not be null");
            Flow.Subscription toCancel;
            synchronized (this) {
                if (terminal) {
                    return;
                }
                terminal = true;
                toCancel = subscription;
            }
            if (toCancel != null) {
                toCancel.cancel();
            }
            body.completeExceptionally(failure);
        }
    }

    private static final class DirectBodyHandler implements HttpResponse.BodyHandler<ContentIdentity> {
        private final long maxBytes;
        private final DirectBodySubscriber subscriber;

        private DirectBodyHandler(long maxBytes) {
            this.maxBytes = maxBytes;
            this.subscriber = new DirectBodySubscriber(maxBytes);
        }

        @Override
        public HttpResponse.BodySubscriber<ContentIdentity> apply(HttpResponse.ResponseInfo response) {
            IOException failure = validate(response);
            if (failure != null) {
                subscriber.abort(failure);
            }
            return subscriber;
        }

        private IOException validate(HttpResponse.ResponseInfo response) {
            if (response.statusCode() != 200) {
                return new IOException("Maven Central returned an unexpected HTTP response");
            }
            List<String> encodings = response.headers().allValues("Content-Encoding");
            if (encodings.stream().anyMatch(value -> !"identity".equalsIgnoreCase(value.trim()))) {
                return new IOException("Maven Central returned encoded artifact bytes");
            }
            List<String> lengths = response.headers().allValues("Content-Length");
            if (lengths.size() > 1) {
                return new IOException("Maven Central returned an invalid Content-Length");
            }
            if (!lengths.isEmpty()) {
                long declared;
                try {
                    declared = Long.parseLong(lengths.get(0));
                } catch (NumberFormatException e) {
                    return new IOException("Maven Central returned an invalid Content-Length", e);
                }
                if (declared <= 0 || declared > maxBytes) {
                    return new IOException("Maven Central returned an unsafe artifact size");
                }
                subscriber.expectLength(declared);
            }
            return null;
        }

        private void abort(IOException failure) {
            subscriber.abort(failure);
        }
    }

    private static final class DirectClientHolder {
        private static final HttpClient INSTANCE = newDirectClient();

        private DirectClientHolder() {
        }
    }
}
