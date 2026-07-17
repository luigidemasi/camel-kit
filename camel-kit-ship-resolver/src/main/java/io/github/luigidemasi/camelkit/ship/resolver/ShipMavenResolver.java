package io.github.luigidemasi.camelkit.ship.resolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
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
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.supplier.RepositorySystemSupplier;
import org.eclipse.aether.util.repository.SimpleArtifactDescriptorPolicy;

/** Maven Resolver boundary whose third-party implementation is relocated in the published JAR. */
public final class ShipMavenResolver {

    private static final String CENTRAL = "https://repo.maven.apache.org/maven2/";
    private static final int MAX_ROOTS = 64;
    private static final int MAX_ARTIFACTS = 512;

    private ShipMavenResolver() {
    }

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
            DefaultRepositorySystemSession session = session(system, local);
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
                    throw new IOException("Ship resolver returned too many artifacts");
                }
            }
            return List.copyOf(result);
        } catch (IOException e) {
            throw e;
        } catch (Exception | LinkageError e) {
            throw new IOException("Could not resolve the isolated Ship payload: " + safeMessage(e), e);
        }
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

    private static DefaultRepositorySystemSession session(RepositorySystem system, Path repository) {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
        // Maven's utility session ignores missing and invalid descriptors by default, which can silently
        // turn a transitive graph into direct roots only. An evidence payload must fail closed instead.
        session.setArtifactDescriptorPolicy(new SimpleArtifactDescriptorPolicy(false, false));
        session.setOffline(false);
        session.setIgnoreArtifactDescriptorRepositories(true);
        session.setChecksumPolicy(RepositoryPolicy.CHECKSUM_POLICY_FAIL);
        session.setUpdatePolicy(RepositoryPolicy.UPDATE_POLICY_NEVER);
        // Maven's JDK profile activator requires java.version. Derive it from the immutable VM runtime
        // view instead of accepting mutable/user-supplied model properties.
        session.setSystemProperties(Map.of("java.version", Runtime.version().toString()));
        session.setUserProperties(Map.of());
        session.setConfigProperties(Map.of(
                ConfigurationProperties.CONNECT_TIMEOUT, 10_000,
                ConfigurationProperties.REQUEST_TIMEOUT, 60_000));
        session.setLocalRepositoryManager(
                system.newLocalRepositoryManager(session, new LocalRepository(repository.toFile())));
        return session;
    }

    private static RemoteRepository central() {
        RepositoryPolicy releases = new RepositoryPolicy(
                true, RepositoryPolicy.UPDATE_POLICY_NEVER, RepositoryPolicy.CHECKSUM_POLICY_FAIL);
        RepositoryPolicy snapshots = new RepositoryPolicy(
                false, RepositoryPolicy.UPDATE_POLICY_NEVER, RepositoryPolicy.CHECKSUM_POLICY_FAIL);
        return new RemoteRepository.Builder("camel-kit-central", "default", CENTRAL)
                .setReleasePolicy(releases)
                .setSnapshotPolicy(snapshots)
                .build();
    }

    private static void rejectRepositoryOverrides() throws IOException {
        for (String key : List.of(
                "camel.extra.repos",
                "camel.default.extra.repos.default.value",
                "https.proxyHost",
                "https.proxyPort",
                "http.proxyHost",
                "http.proxyPort",
                "java.net.useSystemProxies",
                "javax.net.ssl.trustStore",
                "javax.net.ssl.trustStoreType",
                "javax.net.ssl.trustStoreProvider")) {
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
}
