package io.github.luigidemasi.camelkit.knowledge.mcp;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.metadata.DefaultMetadata;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.MetadataRequest;
import org.eclipse.aether.resolution.MetadataResult;
import org.eclipse.aether.supplier.RepositorySystemSupplier;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Resolves the knowledge index artifact from Maven repositories at startup.
 *
 * <p>Flow:
 * <ol>
 *   <li>Build list of remote repos from config properties</li>
 *   <li>If version specified in config, use it; otherwise resolve latest from maven-metadata.xml</li>
 *   <li>Resolve artifact (downloads to local repo if not cached)</li>
 *   <li>Extract knowledge-index/* from the JAR to a temp directory</li>
 * </ol>
 */
@ApplicationScoped
public class IndexResolver {

    private static final String INDEX_PREFIX = "knowledge-index/";

    @Inject
    IndexResolverConfig config;

    /**
     * Resolve and extract the knowledge index, returning the path to the extracted directory.
     *
     * @return path to directory containing extracted Lucene index files
     * @throws IndexResolverException if resolution fails and no cached version is available
     */
    public Path resolve() throws IndexResolverException {
        RepositorySystem repoSystem = new RepositorySystemSupplier().get();
        Path localRepoPath = resolveLocalRepoPath();
        LocalRepository localRepo = new LocalRepository(localRepoPath.toFile());

        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
        session.setLocalRepositoryManager(
                repoSystem.newLocalRepositoryManager(session, localRepo));

        try {
            List<RemoteRepository> remoteRepos = buildRemoteRepositories();
            String version = resolveVersion(repoSystem, session, remoteRepos);

            Artifact artifact = new DefaultArtifact(
                    config.groupId(), config.artifactId(), "jar", version);

            ArtifactRequest request = new ArtifactRequest(artifact, remoteRepos, null);
            ArtifactResult result = repoSystem.resolveArtifact(session, request);

            if (!result.isResolved()) {
                throw new IndexResolverException(
                        "Failed to resolve index artifact: " + artifact);
            }

            File jarFile = result.getArtifact().getFile();
            Path jarPath = jarFile.toPath();
            System.out.printf("Knowledge index resolved: %s (version %s)%n",
                    jarPath, version);

            return extractIndexFromJar(jarPath);

        } catch (IndexResolverException e) {
            Path fallback = findLatestLocalVersion(localRepoPath);
            if (fallback != null) {
                System.out.printf("WARNING: Remote resolution failed, using cached index: %s%n",
                        fallback);
                return extractIndexFromJar(fallback);
            }
            throw e;
        } catch (Exception e) {
            Path fallback = findLatestLocalVersion(localRepoPath);
            if (fallback != null) {
                System.out.printf("WARNING: Remote resolution failed (%s), using cached index: %s%n",
                        e.getMessage(), fallback);
                return extractIndexFromJar(fallback);
            }
            throw new IndexResolverException(
                    "Failed to resolve index artifact and no cached version found. " +
                    "Install the index locally with: mvn install -pl index -Prebuild-index -Drevision=$(date +%%Y%%m%%d%%H%%M)", e);
        }
    }

    private String resolveVersion(RepositorySystem repoSystem,
                                  DefaultRepositorySystemSession session,
                                  List<RemoteRepository> remoteRepos) throws IndexResolverException {

        if (config.version().isPresent() && !config.version().get().isBlank()) {
            return config.version().get();
        }

        Metadata metadata = new DefaultMetadata(
                config.groupId(), config.artifactId(), "maven-metadata.xml",
                Metadata.Nature.RELEASE);

        List<MetadataRequest> requests = remoteRepos.stream()
                .map(repo -> new MetadataRequest(metadata, repo, null))
                .toList();

        List<MetadataResult> results = repoSystem.resolveMetadata(session, requests);

        String latestVersion = null;
        for (MetadataResult result : results) {
            if (result.getMetadata() != null && result.getMetadata().getFile() != null) {
                String version = parseLatestVersion(result.getMetadata().getFile().toPath());
                if (version != null) {
                    if (latestVersion == null || version.compareTo(latestVersion) > 0) {
                        latestVersion = version;
                    }
                }
            }
        }

        if (latestVersion == null) {
            Path localLatest = findLatestLocalVersion(resolveLocalRepoPath());
            if (localLatest != null) {
                String fileName = localLatest.getFileName().toString();
                String prefix = config.artifactId() + "-";
                if (fileName.startsWith(prefix) && fileName.endsWith(".jar")) {
                    return fileName.substring(prefix.length(), fileName.length() - 4);
                }
            }
            throw new IndexResolverException(
                    "Cannot determine index version: no maven-metadata.xml found and no local cache");
        }

        return latestVersion;
    }

    private String parseLatestVersion(Path metadataPath) {
        try {
            var factory = DocumentBuilderFactory.newInstance();
            var doc = factory.newDocumentBuilder().parse(metadataPath.toFile());

            var releaseNodes = doc.getElementsByTagName("release");
            if (releaseNodes.getLength() > 0) {
                String release = releaseNodes.item(0).getTextContent().trim();
                if (!release.isEmpty()) return release;
            }

            var versionNodes = doc.getElementsByTagName("version");
            String latest = null;
            for (int i = 0; i < versionNodes.getLength(); i++) {
                String v = versionNodes.item(i).getTextContent().trim();
                if (latest == null || v.compareTo(latest) > 0) {
                    latest = v;
                }
            }
            return latest;
        } catch (Exception e) {
            System.out.printf("WARNING: Failed to parse maven-metadata.xml at %s: %s%n",
                    metadataPath, e.getMessage());
            return null;
        }
    }

    private List<RemoteRepository> buildRemoteRepositories() {
        List<RemoteRepository> repos = new ArrayList<>();

        repos.add(new RemoteRepository.Builder(
                "central", "default", "https://repo1.maven.org/maven2/").build());

        config.repositories().ifPresent(repoList -> {
            String[] urls = repoList.split(",");
            for (int i = 0; i < urls.length; i++) {
                String url = urls[i].trim();
                if (!url.isEmpty()) {
                    repos.add(new RemoteRepository.Builder(
                            "config-repo-" + i, "default", url).build());
                }
            }
        });

        return repos;
    }

    private Path extractIndexFromJar(Path jarPath) throws IndexResolverException {
        try {
            Path tempDir = Files.createTempDirectory("knowledge-index");
            tempDir.toFile().deleteOnExit();

            try (JarFile jarFile = new JarFile(jarPath.toFile())) {
                Enumeration<JarEntry> entries = jarFile.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (name.startsWith(INDEX_PREFIX) && !entry.isDirectory()) {
                        String fileName = name.substring(INDEX_PREFIX.length());
                        if (fileName.equals("INDEX_FILES") || fileName.equals(".gitkeep")) {
                            continue;
                        }
                        try (InputStream is = jarFile.getInputStream(entry)) {
                            Files.copy(is, tempDir.resolve(fileName),
                                    StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                }
            }

            return tempDir;
        } catch (IOException e) {
            throw new IndexResolverException("Failed to extract index from JAR: " + jarPath, e);
        }
    }

    private Path resolveLocalRepoPath() {
        Path settingsFile = Path.of(System.getProperty("user.home"), ".m2", "settings.xml");
        if (Files.exists(settingsFile)) {
            try {
                var factory = DocumentBuilderFactory.newInstance();
                var doc = factory.newDocumentBuilder().parse(settingsFile.toFile());
                var nodes = doc.getElementsByTagName("localRepository");
                if (nodes.getLength() > 0) {
                    String path = nodes.item(0).getTextContent().trim();
                    if (!path.isEmpty()) return Path.of(path);
                }
            } catch (Exception e) {
                // Fall through to default
            }
        }
        return Path.of(System.getProperty("user.home"), ".m2", "repository");
    }

    private Path findLatestLocalVersion(Path localRepoPath) {
        Path artifactDir = localRepoPath
                .resolve(config.groupId().replace('.', '/'))
                .resolve(config.artifactId());

        if (!Files.isDirectory(artifactDir)) return null;

        try (var versions = Files.list(artifactDir)) {
            return versions
                    .filter(Files::isDirectory)
                    .sorted(Comparator.comparing(p -> p.getFileName().toString(), Comparator.reverseOrder()))
                    .map(versionDir -> {
                        String version = versionDir.getFileName().toString();
                        Path jar = versionDir.resolve(config.artifactId() + "-" + version + ".jar");
                        return Files.exists(jar) ? jar : null;
                    })
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    public static class IndexResolverException extends Exception {
        public IndexResolverException(String message) { super(message); }
        public IndexResolverException(String message, Throwable cause) { super(message, cause); }
    }
}
