package io.github.luigidemasi.camelkit.ship.evidence;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import io.github.luigidemasi.camelkit.ship.ShipArtifactLimits;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogExpressionInventory;
import io.github.luigidemasi.camelkit.ship.evidence.launcher.ShipCamelMainBootstrap;
import io.github.luigidemasi.camelkit.ship.evidence.launcher.ShipCamelYamlValidateMain;
import io.github.luigidemasi.camelkit.ship.evidence.launcher.ShipCitrusYamlMain;
import io.github.luigidemasi.camelkit.ship.evidence.launcher.ShipMainPackageMain;
import io.github.luigidemasi.camelkit.ship.expression.ShipExpressionPolicy;
import io.github.luigidemasi.camelkit.ship.resolver.MavenCoordinate;
import io.github.luigidemasi.camelkit.ship.resolver.MavenDependencyExclusion;
import io.github.luigidemasi.camelkit.ship.resolver.MavenDependencyRoot;
import io.github.luigidemasi.camelkit.ship.resolver.ResolvedMavenArtifact;
import io.github.luigidemasi.camelkit.ship.resolver.ShipMavenResolver;

/** Resolves, freezes, and verifies a single controller-owned nested-JAR JVM payload. */
public final class JvmPayloadArchive {

    public static final String SANDBOX_ARCHIVE = "/opt/camel-kit/payload/payload.jar";
    public static final String BOOTSTRAP_CLASS = ShipJvmPayloadBootstrap.class.getName();
    private static final String CENTRAL = "https://repo.maven.apache.org/maven2/";
    private static final HttpClient CENTRAL_CLIENT = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .proxy(new NoProxySelector())
            .build();
    private static final int MAX_ARTIFACTS = 512;
    private static final long MAX_ARTIFACT_BYTES = 128L * 1024 * 1024;
    private static final long MAX_TOTAL_BYTES = 768L * 1024 * 1024;

    private JvmPayloadArchive() {
    }

    public static Identity materialize(Path root, JvmPayloadRequest request, String jdkDigest) throws IOException {
        if (request == null || !request.kind().supported()) {
            String kind = request == null ? "missing" : request.kind().id();
            throw new IOException(switch (kind) {
                case "citrus-yaml" ->
                    "Direct Citrus YAML evidence fails closed until Citrus and the approved Camel version "
                                      + "can be resolved into one dependency-aligned immutable payload";
                case "spring-boot-build", "quarkus-build" ->
                    "Runtime build evidence fails closed until a direct controller JVM build payload is retained";
                default -> "Unsupported direct JVM evidence payload: " + kind;
            });
        }
        Path payloadRoot = privateDirectory(root.resolve("jvm-payload"));
        List<ArtifactFile> artifacts = List.of();
        if (!request.dependencyRoots().isEmpty()) {
            Path repository = privateDirectory(root.resolve("resolver-repository"));
            try (var files = Files.list(repository)) {
                if (files.findAny().isPresent()) {
                    throw new IOException("Controller JVM payload resolver repository must start empty");
                }
            }
            artifacts = resolve(repository, request);
        }
        Path archive = payloadRoot.resolve("payload.jar");
        return write(archive, request, jdkDigest, artifacts);
    }

    static Identity write(
            Path archive, JvmPayloadRequest request, String jdkDigest, List<ArtifactFile> artifacts)
            throws IOException {
        List<SourceEntry> sources = new ArrayList<>();
        for (Class<?> type : classClosure(ShipJvmPayloadBootstrap.class, JvmPayloadLock.class)) {
            byte[] bytes = classBytes(type);
            sources.add(SourceEntry.bytes(
                    "bootstrap", "class:" + type.getName(), classPath(type), bytes));
        }
        Class<?> launcher = launcher(request);
        byte[] launcherJar = launcherJar(launcher);
        sources.add(SourceEntry.bytes(
                "launcher", "class:" + launcher.getName(), "lib/000-controller-launcher.jar", launcherJar));

        List<ArtifactFile> ordered = artifacts.stream()
                .sorted(Comparator.comparing(ArtifactFile::identity)).toList();
        for (int index = 0; index < ordered.size(); index++) {
            ArtifactFile artifact = ordered.get(index);
            String name = safeName(artifact.artifactId()) + '-' + safeName(artifact.version()) + ".jar";
            sources.add(SourceEntry.file(
                    "maven", artifact.identity(),
                    String.format(Locale.ROOT, "lib/%03d-%s", index + 100, name), artifact.path()));
        }
        sources.sort(Comparator.comparing(SourceEntry::path));
        List<JvmPayloadLock.Entry> entries = sources.stream()
                .map(SourceEntry::lockEntry).toList();
        JvmPayloadLock lock = JvmPayloadLock.create(request, jdkDigest, entries);
        SourceEntry lockEntry = SourceEntry.bytes(
                "lock", "lock", JvmPayloadLock.LOCK_PATH, lock.encode());

        List<SourceEntry> archiveEntries = new ArrayList<>(sources);
        archiveEntries.add(lockEntry);
        archiveEntries.sort(Comparator.comparing(SourceEntry::path));
        long total = archiveEntries.stream().mapToLong(SourceEntry::size).reduce(0, Math::addExact);
        if (total > MAX_TOTAL_BYTES) {
            throw new IOException("Controller JVM payload exceeds " + MAX_TOTAL_BYTES + " bytes");
        }
        Files.createDirectories(archive.toAbsolutePath().normalize().getParent());
        try (ZipOutputStream zip = new ZipOutputStream(
                Files.newOutputStream(
                        archive, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE))) {
            zip.setLevel(0);
            for (SourceEntry source : archiveEntries) {
                ZipEntry entry = new ZipEntry(source.path());
                entry.setMethod(ZipEntry.STORED);
                entry.setSize(source.size());
                entry.setCompressedSize(source.size());
                entry.setCrc(source.crc());
                entry.setTime(0);
                zip.putNextEntry(entry);
                source.writeTo(zip);
                zip.closeEntry();
            }
        }
        if (Files.getFileAttributeView(archive, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS) != null) {
            Files.setPosixFilePermissions(archive, PosixFilePermissions.fromString("r--------"));
        }
        return verify(archive, request, jdkDigest);
    }

    public static Identity verify(Path archive, JvmPayloadRequest request, String jdkDigest) throws IOException {
        Path file = regular(archive, "JVM payload archive");
        if (Files.size(file) <= 0 || Files.size(file) > MAX_TOTAL_BYTES) {
            throw new IOException("JVM payload archive has an unsafe size");
        }
        String archiveDigest = digest(file);
        JvmPayloadLock lock;
        try (ZipFile zip = new ZipFile(file.toFile())) {
            lock = JvmPayloadLock.parse(read(zip, JvmPayloadLock.LOCK_PATH, 1024 * 1024));
            lock.requireRequest(request);
            if (!lock.jdkDigest().equals(jdkDigest)) {
                throw new IOException("JVM payload JDK digest differs from the accepted controller JDK");
            }
            Set<String> expected = new HashSet<>();
            expected.add(JvmPayloadLock.LOCK_PATH);
            lock.entries().forEach(entry -> expected.add(entry.path()));
            Set<String> observed = new HashSet<>();
            long total = 0;
            var enumeration = zip.entries();
            while (enumeration.hasMoreElements()) {
                ZipEntry entry = enumeration.nextElement();
                if (entry.isDirectory() || !observed.add(entry.getName()) || !expected.contains(entry.getName())
                        || entry.getSize() <= 0 || entry.getSize() > MAX_ARTIFACT_BYTES) {
                    throw new IOException("JVM payload archive contains an unsafe entry: " + entry.getName());
                }
                total = Math.addExact(total, entry.getSize());
                if (total > MAX_TOTAL_BYTES) {
                    throw new IOException("JVM payload archive exceeds the controller size bound");
                }
            }
            if (!observed.equals(expected)) {
                throw new IOException("JVM payload archive entries differ from its content lock");
            }
            for (JvmPayloadLock.Entry entry : lock.entries()) {
                byte[] bytes = read(zip, entry.path(), Math.toIntExact(entry.size()));
                if (bytes.length != entry.size() || !entry.digest().equals(digest(bytes))) {
                    throw new IOException("JVM payload entry differs from its content lock: " + entry.path());
                }
            }
        }
        String aggregate = aggregate(lock.contentDigest(), archiveDigest, jdkDigest);
        return new Identity(file, aggregate, archiveDigest, lock);
    }

    private static List<ArtifactFile> resolve(Path repository, JvmPayloadRequest request) throws IOException {
        rejectRepositoryOverrides();
        try {
            List<MavenDependencyRoot> roots = request.dependencyRoots().stream()
                    .map(root -> new MavenDependencyRoot(
                            MavenCoordinate.jar(root.groupId(), root.artifactId(), root.version()),
                            root.exclusions().stream()
                                    .map(exclusion -> new MavenDependencyExclusion(
                                            exclusion.groupId(), exclusion.artifactId()))
                                    .toList()))
                    .toList();
            List<ResolvedMavenArtifact> artifacts = ShipMavenResolver.resolve(repository, roots);
            return validateResolved(repository, request, artifacts);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Could not materialize the direct JVM payload: " + safeMessage(e), e);
        }
    }

    private static List<ArtifactFile> validateResolved(
            Path repository, JvmPayloadRequest request, List<ResolvedMavenArtifact> resolved)
            throws IOException {
        if (resolved == null || resolved.isEmpty() || resolved.size() > MAX_ARTIFACTS) {
            throw new IOException("Direct JVM payload resolution returned an unsafe artifact count");
        }
        Path root = repository.toRealPath(LinkOption.NOFOLLOW_LINKS);
        Map<String, String> versions = new HashMap<>();
        Set<String> coordinates = new HashSet<>();
        Set<String> roots = new HashSet<>();
        List<ArtifactFile> result = new ArrayList<>();
        long total = 0;
        for (ResolvedMavenArtifact artifact : resolved) {
            if (artifact == null || artifact.coordinate() == null || artifact.path() == null) {
                throw new IOException("Direct JVM payload resolver returned an incomplete artifact");
            }
            MavenCoordinate coordinate = artifact.coordinate();
            if (!"jar".equals(coordinate.extension())) {
                throw new IOException("Direct JVM payload contains a non-JAR or non-release artifact: " + artifact);
            }
            String identity = coordinate.resolverString();
            if (!coordinates.add(identity)) {
                throw new IOException("Direct JVM payload contains duplicate coordinates: " + identity);
            }
            String ga = coordinate.groupId() + ':' + coordinate.artifactId();
            String previous = versions.putIfAbsent(ga, coordinate.version());
            if (previous != null && !previous.equals(coordinate.version())) {
                throw new IOException("Direct JVM payload contains multiple versions of " + ga);
            }
            if ("org.apache.camel".equals(coordinate.groupId())
                    && !request.camelVersion().equals(coordinate.version())) {
                throw new IOException(
                        "Direct JVM payload does not align all Camel artifacts to "
                                      + request.camelVersion());
            }
            if ("org.citrusframework".equals(coordinate.groupId()) && request.citrusVersion() != null
                    && !request.citrusVersion().equals(coordinate.version())) {
                throw new IOException(
                        "Direct JVM payload does not align all Citrus artifacts to "
                                      + request.citrusVersion());
            }
            Path file = regular(artifact.path(), "resolved JVM payload artifact");
            if (!file.startsWith(root)) {
                throw new IOException("Direct JVM payload artifact escaped its empty private repository");
            }
            long size = Files.size(file);
            if (size <= 0 || size > MAX_ARTIFACT_BYTES) {
                throw new IOException("Direct JVM payload artifact has an unsafe size: " + identity);
            }
            verifyCentralBytes(coordinate, file, size);
            total = Math.addExact(total, size);
            if (total > MAX_TOTAL_BYTES) {
                throw new IOException("Direct JVM payload dependencies exceed the controller size limit");
            }
            roots.add(coordinate.gav());
            result.add(new ArtifactFile(identity, coordinate.artifactId(), coordinate.version(), file));
        }
        if (!roots.containsAll(request.roots())) {
            throw new IOException("Direct JVM payload resolution omitted a controller root");
        }
        return List.copyOf(result);
    }

    private static void rejectRepositoryOverrides() throws IOException {
        for (String key : List.of(
                "camel.extra.repos",
                "camel.default.extra.repos.default.value")) {
            if (System.getProperty(key) != null && !System.getProperty(key).isBlank()) {
                throw new IOException("Controller JVM payload refuses repository override property " + key);
            }
        }
    }

    private static void verifyCentralBytes(
            MavenCoordinate artifact, Path local, long expectedSize)
            throws IOException {
        requireCoordinatePart(artifact.groupId(), "groupId");
        requireCoordinatePart(artifact.artifactId(), "artifactId");
        requireCoordinatePart(artifact.version(), "version");
        if (!artifact.classifier().isBlank()) {
            requireCoordinatePart(artifact.classifier(), "classifier");
        }
        URI uri = URI.create(CENTRAL
                             + artifact.groupId().replace('.', '/') + '/'
                             + artifact.artifactId() + '/' + artifact.version() + '/' + artifact.fileName());
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(java.time.Duration.ofSeconds(60))
                .header("Accept", "application/java-archive")
                .GET()
                .build();
        try {
            HttpResponse<InputStream> response = centralClient().send(
                    request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200 || !uri.equals(response.uri())) {
                response.body().close();
                throw new IOException("Authenticated Maven Central verification failed for " + artifact);
            }
            MessageDigest remoteDigest = sha512();
            long remoteSize = 0;
            try (InputStream input = response.body()) {
                byte[] buffer = new byte[16_384];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    remoteSize = Math.addExact(remoteSize, count);
                    if (remoteSize > MAX_ARTIFACT_BYTES) {
                        throw new IOException("Authenticated Maven Central artifact exceeds the size limit");
                    }
                    remoteDigest.update(buffer, 0, count);
                }
            }
            if (remoteSize != expectedSize
                    || !MessageDigest.isEqual(remoteDigest.digest(), digest(local, "SHA-512"))) {
                throw new IOException("Resolved JVM payload artifact differs from Maven Central: " + artifact);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while verifying authenticated Maven Central bytes", e);
        } catch (IOException e) {
            throw new IOException(
                    "Could not verify authenticated Maven Central bytes for " + artifact + ": " + safeMessage(e),
                    e);
        }
    }

    private static void requireCoordinatePart(String value, String label) throws IOException {
        if (value == null || !value.matches("[A-Za-z0-9_.-]+")) {
            throw new IOException("Direct JVM payload has an unsafe Maven " + label);
        }
    }

    private static Class<?> launcher(JvmPayloadRequest request) throws IOException {
        if (ShipMainPackageMain.class.getName().equals(request.launcherClass())) {
            return ShipMainPackageMain.class;
        }
        if (ShipCamelYamlValidateMain.class.getName().equals(request.launcherClass())) {
            return ShipCamelYamlValidateMain.class;
        }
        if (ShipCamelMainBootstrap.class.getName().equals(request.launcherClass())) {
            return ShipCamelMainBootstrap.class;
        }
        if (ShipCitrusYamlMain.class.getName().equals(request.launcherClass())) {
            return ShipCitrusYamlMain.class;
        }
        throw new IOException("JVM payload request names an unknown controller launcher");
    }

    private static List<Class<?>> classClosure(Class<?>... roots) {
        Set<Class<?>> result = new LinkedHashSet<>();
        for (Class<?> root : roots) {
            collect(root, result);
        }
        return result.stream().sorted(Comparator.comparing(Class::getName)).toList();
    }

    private static void collect(Class<?> type, Set<Class<?>> result) {
        if (result.add(type)) {
            for (Class<?> nested : type.getDeclaredClasses()) {
                collect(nested, result);
            }
        }
    }

    private static byte[] launcherJar(Class<?> launcher) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.setLevel(0);
            List<Class<?>> types;
            if (launcher == ShipCitrusYamlMain.class) {
                types = classClosure(
                        launcher, ShipCamelMainBootstrap.class,
                        ShipArtifactLimits.class, ShipExpressionPolicy.class, CatalogExpressionInventory.class);
            } else if (launcher == ShipCamelMainBootstrap.class) {
                types = classClosure(
                        launcher, ShipArtifactLimits.class, ShipExpressionPolicy.class,
                        CatalogExpressionInventory.class);
            } else if (launcher == ShipMainPackageMain.class) {
                types = classClosure(launcher, ShipArtifactLimits.class);
            } else if (launcher == ShipCamelYamlValidateMain.class) {
                types = classClosure(launcher, ShipArtifactLimits.class);
            } else {
                types = classClosure(launcher);
            }
            for (Class<?> type : types) {
                byte[] content = classBytes(type);
                ZipEntry entry = storedEntry(classPath(type), content);
                zip.putNextEntry(entry);
                zip.write(content);
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private static ZipEntry storedEntry(String name, byte[] content) {
        CRC32 crc = new CRC32();
        crc.update(content);
        ZipEntry entry = new ZipEntry(name);
        entry.setMethod(ZipEntry.STORED);
        entry.setSize(content.length);
        entry.setCompressedSize(content.length);
        entry.setCrc(crc.getValue());
        entry.setTime(0);
        return entry;
    }

    private static byte[] classBytes(Class<?> type) throws IOException {
        String path = classPath(type);
        ClassLoader loader = type.getClassLoader();
        try (InputStream input = loader == null
                ? ClassLoader.getSystemResourceAsStream(path)
                : loader.getResourceAsStream(path)) {
            if (input == null) {
                throw new IOException("Controller launcher class bytes are unavailable: " + type.getName());
            }
            return input.readAllBytes();
        }
    }

    private static String classPath(Class<?> type) {
        return type.getName().replace('.', '/') + ".class";
    }

    private static byte[] read(ZipFile zip, String name, int maximum) throws IOException {
        ZipEntry entry = zip.getEntry(name);
        if (entry == null || entry.isDirectory() || entry.getSize() <= 0 || entry.getSize() > maximum) {
            throw new IOException("JVM payload archive lacks a bounded entry: " + name);
        }
        try (InputStream input = zip.getInputStream(entry)) {
            byte[] bytes = input.readNBytes(maximum + 1);
            if (bytes.length > maximum || input.read() != -1) {
                throw new IOException("JVM payload archive entry exceeds its bound: " + name);
            }
            return bytes;
        }
    }

    private static Path privateDirectory(Path directory) throws IOException {
        Files.createDirectory(directory);
        if (Files.isSymbolicLink(directory) || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("JVM payload directory is unsafe: " + directory);
        }
        if (Files.getFileAttributeView(directory, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS) != null) {
            Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"));
        }
        return directory.toRealPath(LinkOption.NOFOLLOW_LINKS);
    }

    private static Path regular(Path path, String label) throws IOException {
        Path normalized = path.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(normalized)
                || !Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException(label + " is missing, symbolic, or non-regular: " + path);
        }
        return normalized.toRealPath(LinkOption.NOFOLLOW_LINKS);
    }

    private static String safeName(String value) throws IOException {
        if (value == null || !value.matches("[A-Za-z0-9_.-]+")) {
            throw new IOException("Unsafe JVM payload artifact name");
        }
        return value;
    }

    private static String aggregate(String contentDigest, String archiveDigest, String jdkDigest) {
        MessageDigest digest = sha256();
        update(digest, "camel-kit.ship.jvm-payload-identity.v1");
        update(digest, contentDigest);
        update(digest, archiveDigest);
        update(digest, jdkDigest);
        return "sha256:" + HexFormat.of().formatHex(digest.digest());
    }

    private static String digest(Path file) throws IOException {
        MessageDigest digest = sha256();
        try (InputStream input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            byte[] buffer = new byte[16_384];
            int count;
            while ((count = input.read(buffer)) != -1) {
                digest.update(buffer, 0, count);
            }
        }
        return "sha256:" + HexFormat.of().formatHex(digest.digest());
    }

    private static byte[] digest(Path file, String algorithm) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(algorithm + " is unavailable", e);
        }
        try (InputStream input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            byte[] buffer = new byte[16_384];
            int count;
            while ((count = input.read(buffer)) != -1) {
                digest.update(buffer, 0, count);
            }
        }
        return digest.digest();
    }

    private static String digest(byte[] bytes) {
        return "sha256:" + HexFormat.of().formatHex(sha256().digest(bytes));
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static MessageDigest sha512() {
        try {
            return MessageDigest.getInstance("SHA-512");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-512 is unavailable", e);
        }
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private static String safeMessage(Throwable error) {
        return error.getMessage() == null || error.getMessage().isBlank()
                ? error.getClass().getSimpleName()
                : error.getMessage();
    }

    private static HttpClient centralClient() throws IOException {
        rejectRepositoryOverrides();
        return CENTRAL_CLIENT;
    }

    private static final class NoProxySelector extends ProxySelector {

        @Override
        public List<java.net.Proxy> select(URI uri) {
            return List.of(java.net.Proxy.NO_PROXY);
        }

        @Override
        public void connectFailed(URI uri, SocketAddress address, IOException failure) {
            // There is no proxy endpoint to report.
        }
    }

    public record Identity(Path archive, String aggregateDigest, String archiveDigest, JvmPayloadLock lock) {
    }

    record ArtifactFile(String identity, String artifactId, String version, Path path) {

        ArtifactFile {
            path = path.toAbsolutePath().normalize();
        }
    }

    private record SourceEntry(
            String kind,
            String identity,
            String path,
            long size,
            String digest,
            long crc,
            byte[] bytes,
            Path file) {

        private static SourceEntry bytes(String kind, String identity, String path, byte[] bytes) {
            CRC32 crc = new CRC32();
            crc.update(bytes);
            return new SourceEntry(
                    kind, identity, path, bytes.length, JvmPayloadArchive.digest(bytes), crc.getValue(), bytes.clone(),
                    null);
        }

        private static SourceEntry file(String kind, String identity, String path, Path file) throws IOException {
            Path source = regular(file, "JVM payload source");
            CRC32 crc = new CRC32();
            MessageDigest digest = sha256();
            long size = 0;
            try (InputStream input = Files.newInputStream(source, LinkOption.NOFOLLOW_LINKS)) {
                byte[] buffer = new byte[16_384];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    size = Math.addExact(size, count);
                    if (size > MAX_ARTIFACT_BYTES) {
                        throw new IOException("JVM payload source exceeds the artifact size limit");
                    }
                    crc.update(buffer, 0, count);
                    digest.update(buffer, 0, count);
                }
            }
            return new SourceEntry(
                    kind, identity, path, size,
                    "sha256:" + HexFormat.of().formatHex(digest.digest()), crc.getValue(), null, source);
        }

        private JvmPayloadLock.Entry lockEntry() {
            return new JvmPayloadLock.Entry(kind, identity, path, size, digest);
        }

        private void writeTo(ZipOutputStream target) throws IOException {
            if (bytes != null) {
                target.write(bytes);
                return;
            }
            try (InputStream input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
                input.transferTo(target);
            }
        }
    }
}
