package io.github.luigidemasi.camelkit.ship.resolver;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
     * one and {@value #MAX_ARTIFACTS} unique immutable coordinates and results retain request order. Results are bound
     * to the locally resolved bytes. Consumers must compare the returned identity with the bytes they actually read.
     */
    public static List<ResolvedExactMavenArtifact> resolveArtifacts(
            Path repository, List<MavenCoordinate> artifacts, ResolutionMode mode)
            throws IOException {
        Path local = requireRepository(repository);
        List<MavenCoordinate> requested = copyExactArtifacts(artifacts);
        Objects.requireNonNull(mode, "mode must not be null");
        rejectRepositoryOverrides();
        try {
            RepositorySystem system = repositorySystem();
            DefaultRepositorySystemSession session = session(system, local, mode);
            List<ResolvedExactMavenArtifact> result = new ArrayList<>(requested.size());
            for (MavenCoordinate coordinate : requested) {
                ArtifactRequest request = new ArtifactRequest(
                        new DefaultArtifact(coordinate.resolverString()), List.of(central()), null);
                ArtifactResult resolved = system.resolveArtifact(session, request);
                ResolvedMavenArtifact artifact = requireExactArtifact(local, coordinate, resolved);
                ContentIdentity identity = contentIdentity(artifact.path(), exactArtifactLimit(coordinate));
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

    private static long exactArtifactLimit(MavenCoordinate coordinate) {
        return "pom".equals(coordinate.extension()) ? MAX_EXACT_POM_BYTES : MAX_EXACT_JAR_BYTES;
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

    record ContentIdentity(String sha256, long length) {
        ContentIdentity {
            if (sha256 == null || !sha256.matches("sha256:[0-9a-f]{64}") || length <= 0) {
                throw new IllegalArgumentException("Invalid exact artifact content identity");
            }
        }
    }
}
