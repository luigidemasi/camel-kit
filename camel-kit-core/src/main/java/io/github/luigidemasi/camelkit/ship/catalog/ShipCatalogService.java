package io.github.luigidemasi.camelkit.ship.catalog;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import io.github.luigidemasi.camelkit.ship.catalog.CatalogEvidenceSet.ArtifactEvidence;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogEvidenceSet.SubjectEvidence;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogSubject.Kind;
import io.github.luigidemasi.camelkit.ship.resolver.MavenCoordinate;
import io.github.luigidemasi.camelkit.ship.resolver.ResolvedExactMavenArtifact;
import io.github.luigidemasi.camelkit.ship.resolver.ShipMavenResolver;
import io.github.luigidemasi.camelkit.ship.resolver.ShipMavenResolver.ResolutionMode;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Resolves and snapshots version-specific Camel catalog artifacts without loading their classes. */
public final class ShipCatalogService {

    private static final long MAX_ARTIFACT_BYTES = 128L * 1024 * 1024;
    private static final int MAX_ZIP_ENTRIES = 8_192;
    private static final long MAX_ZIP_UNCOMPRESSED_BYTES = 256L * 1024 * 1024;
    private static final int MAX_RESOURCE_BYTES = 2 * 1024 * 1024;
    private static final int MAX_SUBJECTS = 512;
    private static final int MAX_NAMES_PER_KIND = 8_192;
    private static final String MAIN_ROOT = "org/apache/camel/catalog";
    private static final Pattern SAFE_COORDINATE = Pattern.compile("[A-Za-z0-9_.-]+");
    private static final Pattern TIMESTAMPED_SNAPSHOT = Pattern.compile(".*-\\d{8}\\.\\d{6}-\\d+$");
    private static final ObjectMapper JSON = new ObjectMapper(
            JsonFactory.builder()
                    .streamReadConstraints(StreamReadConstraints.builder()
                            .maxNestingDepth(100)
                            .maxStringLength(MAX_RESOURCE_BYTES)
                            .build())
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private Path localRepository;
    private final ResolutionMode resolutionMode;
    private final ArtifactResolver artifactResolver;

    /**
     * Uses an explicit caller-supplied local repository and the fixed Maven Central release source. The controlling
     * process remains responsible for creating and protecting that repository from other writers.
     */
    public ShipCatalogService(Path localRepository) {
        this(localRepository, ResolutionMode.ONLINE, ShipMavenResolver::resolveArtifacts);
    }

    ShipCatalogService(
                       Path localRepository, ResolutionMode resolutionMode, ArtifactResolver artifactResolver) {
        this.localRepository = Objects.requireNonNull(localRepository, "localRepository must not be null")
                .toAbsolutePath().normalize();
        this.resolutionMode = Objects.requireNonNull(resolutionMode, "resolutionMode must not be null");
        this.artifactResolver = Objects.requireNonNull(artifactResolver, "artifactResolver must not be null");
    }

    /** Acquires one exact artifact generation and freezes its bytes for every subsequent catalog query. */
    public synchronized Snapshot snapshot(CatalogTarget target) throws IOException {
        Objects.requireNonNull(target, "target must not be null");
        prepareLocalRepository();
        localRepository = localRepository.toRealPath();
        return new Snapshot(resolve(target));
    }

    private static CatalogEvidenceSet evidenceFor(
            ResolvedCatalog resolved, Collection<CatalogSubject> requested)
            throws IOException {
        List<CatalogSubject> subjects = boundedSubjects(requested, null, "Catalog evidence");
        Set<String> mainResources = new HashSet<>();
        Set<String> providerResources = new HashSet<>();
        for (CatalogSubject subject : subjects) {
            String resource = resourceName(subject.kind(), subject.name(),
                    subject.kind() == Kind.EIP || resolved.provider() == null ? MAIN_ROOT : resolved.providerRoot());
            (subject.kind() == Kind.EIP || resolved.provider() == null
                    ? mainResources : providerResources).add(resource);
        }
        CatalogArchive main = new CatalogArchive(
                resolved.main(), MAIN_ROOT, resolved.target().camelVersion(), mainResources);
        CatalogArchive provider = resolved.provider() == null
                ? null
                : new CatalogArchive(
                        resolved.provider(), resolved.providerRoot(), resolved.providerVersion(), providerResources);
        List<SubjectEvidence> evidence = new ArrayList<>();
        for (CatalogSubject subject : subjects) {
            CatalogArchive archive = subject.kind() == Kind.EIP || provider == null ? main : provider;
            if (!archive.names(subject.kind()).contains(subject.name())) {
                throw new IOException(
                        "Catalog subject is unavailable for " + resolved.target().runtime() + ": " + subject);
            }
            evidence.add(archive.verify(subject, resolved.target(), archive == provider));
        }
        try {
            return CatalogEvidenceSet.create(
                    resolved.target(),
                    resolved.platformCoordinate(),
                    resolved.artifacts().stream()
                            .map(ResolvedArtifactSnapshot::evidence)
                            .sorted(Comparator.comparing(value -> value.coordinate().resolverString()))
                            .toList(),
                    List.copyOf(evidence));
        } catch (IllegalArgumentException e) {
            throw new IOException("Resolved catalog data violates the bounded snapshot model", e);
        }
    }

    private static List<CatalogSubject> availableSubjects(ResolvedCatalog resolved) throws IOException {
        CatalogArchive main = new CatalogArchive(
                resolved.main(), MAIN_ROOT, resolved.target().camelVersion(), Set.of());
        CatalogArchive provider = resolved.provider() == null
                ? null
                : new CatalogArchive(
                        resolved.provider(), resolved.providerRoot(), resolved.providerVersion(), Set.of());
        TreeSet<CatalogSubject> result = new TreeSet<>();
        for (Kind kind : Kind.values()) {
            CatalogArchive archive = kind == Kind.EIP || provider == null ? main : provider;
            for (String name : archive.names(kind)) {
                if (result.size() == MAX_ZIP_ENTRIES) {
                    throw new IOException("Catalog exposes more than " + MAX_ZIP_ENTRIES + " subjects");
                }
                result.add(new CatalogSubject(kind, name));
            }
        }
        return List.copyOf(result);
    }

    private static List<CatalogComponentModel> componentModelsFor(
            ResolvedCatalog resolved, Collection<CatalogSubject> components)
            throws IOException {
        List<CatalogSubject> subjects = boundedSubjects(components, Kind.COMPONENT, "Component metadata");
        String root = resolved.provider() == null ? MAIN_ROOT : resolved.providerRoot();
        Set<String> resources = new HashSet<>();
        for (CatalogSubject subject : subjects) {
            resources.add(resourceName(Kind.COMPONENT, subject.name(), root));
        }
        CatalogArchive archive = new CatalogArchive(
                resolved.provider() == null ? resolved.main() : resolved.provider(),
                root,
                resolved.provider() == null ? resolved.target().camelVersion() : resolved.providerVersion(),
                resources);
        List<CatalogComponentModel> models = new ArrayList<>();
        for (CatalogSubject subject : subjects) {
            if (!archive.names(Kind.COMPONENT).contains(subject.name())) {
                throw new IOException(
                        "Catalog component is unavailable for " + resolved.target().runtime() + ": " + subject);
            }
            models.add(archive.componentModel(subject, resolved.target(), resolved.provider() != null));
        }
        return List.copyOf(models);
    }

    private static List<CatalogSubject> boundedSubjects(
            Collection<CatalogSubject> values, Kind requiredKind, String label)
            throws IOException {
        Objects.requireNonNull(values, label + " subjects must not be null");
        TreeSet<CatalogSubject> result = new TreeSet<>();
        for (CatalogSubject subject : values) {
            if (result.size() == MAX_SUBJECTS || subject == null
                    || requiredKind != null && subject.kind() != requiredKind) {
                throw new IOException(
                        label + " requires between 1 and " + MAX_SUBJECTS + " valid subjects");
            }
            if (!result.add(subject)) {
                throw new IOException(label + " request contains duplicate subjects");
            }
        }
        if (result.isEmpty()) {
            throw new IOException(
                    label + " requires between 1 and " + MAX_SUBJECTS + " valid subjects");
        }
        return List.copyOf(result);
    }

    /** Opaque view over one frozen set of exact catalog artifact bytes. */
    public static final class Snapshot {

        private final ResolvedCatalog resolved;

        private Snapshot(ResolvedCatalog resolved) {
            this.resolved = Objects.requireNonNull(resolved, "resolved catalog must not be null");
        }

        public CatalogTarget target() {
            return resolved.target();
        }

        /** Produces descriptive evidence for subjects from this snapshot only. */
        public CatalogEvidenceSet evidenceFor(Collection<CatalogSubject> subjects) throws IOException {
            return ShipCatalogService.evidenceFor(resolved, subjects);
        }

        /** Returns the complete runtime-filtered inventory from this snapshot only. */
        public List<CatalogSubject> availableSubjects() throws IOException {
            return ShipCatalogService.availableSubjects(resolved);
        }

        /** Parses exact component option metadata from this snapshot only. */
        public List<CatalogComponentModel> componentModelsFor(Collection<CatalogSubject> components)
                throws IOException {
            return ShipCatalogService.componentModelsFor(resolved, components);
        }

        @Override
        public String toString() {
            return "CatalogSnapshot[target=" + resolved.target() + ']';
        }
    }

    private ResolvedCatalog resolve(CatalogTarget target) throws IOException {
        if (!"main".equals(target.runtime())) {
            throw new IOException("Ship catalog supports only the main runtime");
        }
        MavenCoordinate mainGav = gav("org.apache.camel", "camel-catalog", target.camelVersion(), "jar");
        ResolvedArtifactSnapshot main = resolveArtifact(mainGav);
        return new ResolvedCatalog(
                target, mainGav, main, null, null, null, List.of(main));
    }

    private ResolvedArtifactSnapshot resolveArtifact(MavenCoordinate coordinate) throws IOException {
        List<ResolvedExactMavenArtifact> resolved;
        try {
            resolved = artifactResolver.resolve(localRepository, List.of(coordinate), resolutionMode);
        } catch (IOException e) {
            throw new IOException(
                    "Could not acquire the exact catalog artifact in "
                                  + resolutionMode.name().toLowerCase(Locale.ROOT) + " mode",
                    e);
        }
        if (resolved == null) {
            throw new IOException("Ship resolver returned an unexpected catalog artifact");
        }
        ResolvedExactMavenArtifact artifact = null;
        for (ResolvedExactMavenArtifact value : resolved) {
            if (artifact != null || value == null) {
                throw new IOException("Ship resolver returned an unexpected catalog artifact");
            }
            artifact = value;
        }
        if (artifact == null || !coordinate.equals(artifact.coordinate())) {
            throw new IOException("Ship resolver returned an unexpected catalog artifact");
        }
        return snapshotArtifact(coordinate, artifact);
    }

    private ResolvedArtifactSnapshot snapshotArtifact(
            MavenCoordinate coordinate, ResolvedExactMavenArtifact artifact)
            throws IOException {
        Path file = artifact.path();
        if (file == null) {
            throw new IOException("Ship resolver returned an artifact without a path");
        }
        Path normalized = file.toAbsolutePath().normalize();
        requireInsideRepository(normalized);
        if (!normalized.equals(exactArtifactPath(coordinate))) {
            throw new IOException("Resolved catalog artifact is not at its exact repository path");
        }
        byte[] bytes;
        try {
            bytes = readBoundedArtifact(normalized);
        } catch (IOException e) {
            throw new IOException(
                    "Could not read the exact catalog artifact "
                                  + resolverString(coordinate),
                    e);
        }
        String digest = sha256(bytes);
        if (bytes.length != artifact.contentLength() || !digest.equals(artifact.contentSha256())) {
            throw new IOException("Catalog artifact does not match its acquired content identity");
        }
        return new ResolvedArtifactSnapshot(coordinate, bytes, digest);
    }

    /** Reads one catalog artifact within its fixed byte bound; the caller verifies the acquired content digest. */
    private static byte[] readBoundedArtifact(Path file) throws IOException {
        try (SeekableByteChannel channel = Files.newByteChannel(
                file, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            long size = channel.size();
            if (size <= 0 || size > MAX_ARTIFACT_BYTES) {
                throw new IOException("Catalog artifact has an unsafe size");
            }
            return Channels.newInputStream(channel).readNBytes(Math.toIntExact(size));
        }
    }

    private Path exactArtifactPath(MavenCoordinate coordinate) {
        return localRepository.resolve(coordinate.groupId().replace('.', '/'))
                .resolve(coordinate.artifactId())
                .resolve(coordinate.version())
                .resolve(coordinate.fileName())
                .normalize();
    }

    private void prepareLocalRepository() throws IOException {
        Path parent = localRepository.getParent();
        if (parent == null
                || !Files.isDirectory(parent.toRealPath(), LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Catalog repository parent must be a real directory");
        }
        if (!Files.exists(localRepository, LinkOption.NOFOLLOW_LINKS)) {
            Files.createDirectory(localRepository);
        }
        if (Files.isSymbolicLink(localRepository)
                || !Files.isDirectory(localRepository, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Catalog repository must be a real directory without symbolic links");
        }
    }

    private void requireInsideRepository(Path path) throws IOException {
        if (!path.startsWith(localRepository)) {
            throw new IOException("Resolved catalog artifact escaped its private repository");
        }
    }

    private static MavenCoordinate gav(
            String groupId, String artifactId, String version, String packaging)
            throws IOException {
        if (!Set.of("jar", "pom").contains(packaging)) {
            throw new IOException("Catalog metadata contains an unsupported Maven artifact type");
        }
        try {
            return MavenCoordinate.of(groupId, artifactId, version, packaging);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IOException("Catalog metadata contains an unsafe or mutable Maven coordinate", e);
        }
    }

    private static boolean safe(String value) {
        return value != null && value.length() <= 128 && SAFE_COORDINATE.matcher(value).matches()
                && !value.startsWith(".") && !value.endsWith(".") && !value.contains("..");
    }

    private static boolean immutableRelease(String version) {
        String normalized = version.toUpperCase(Locale.ROOT);
        return !normalized.contains("SNAPSHOT")
                && !"LATEST".equals(normalized)
                && !"RELEASE".equals(normalized)
                && !TIMESTAMPED_SNAPSHOT.matcher(version).matches();
    }

    private static String resolverString(MavenCoordinate gav) {
        return gav.resolverString();
    }

    private static String sha256(byte[] bytes) {
        return "sha256:" + hex(sha256Digest().digest(bytes));
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static String hex(byte[] bytes) {
        return java.util.HexFormat.of().formatHex(bytes);
    }

    private static String resourceName(Kind kind, String name, String root) {
        return root + '/' + CatalogArchive.directory(kind) + '/' + name + ".json";
    }

    private record ResolvedCatalog(
            CatalogTarget target,
            MavenCoordinate platformCoordinate,
            ResolvedArtifactSnapshot main,
            ResolvedArtifactSnapshot provider,
            String providerRoot,
            String providerVersion,
            List<ResolvedArtifactSnapshot> artifacts) {
    }

    private static final class ResolvedArtifactSnapshot {
        private final MavenCoordinate coordinate;
        private final byte[] bytes;
        private final String sha256;

        private ResolvedArtifactSnapshot(MavenCoordinate coordinate, byte[] bytes, String sha256) {
            this.coordinate = Objects.requireNonNull(coordinate, "coordinate");
            this.bytes = Objects.requireNonNull(bytes, "bytes");
            this.sha256 = Objects.requireNonNull(sha256, "sha256");
        }

        MavenCoordinate coordinate() {
            return coordinate;
        }

        byte[] bytes() {
            return bytes;
        }

        String sha256() {
            return sha256;
        }

        ArtifactEvidence evidence() {
            return new ArtifactEvidence(coordinate, sha256, bytes.length);
        }
    }

    private static final class CatalogArchive {
        private final ResolvedArtifactSnapshot artifact;
        private final String root;
        private final String resourceVersion;
        private final Map<String, byte[]> entries;
        private final Map<Kind, Set<String>> names = new HashMap<>();

        private CatalogArchive(
                               ResolvedArtifactSnapshot artifact,
                               String root,
                               String resourceVersion,
                               Set<String> requestedResources)
                                                               throws IOException {
            this.artifact = artifact;
            this.root = root;
            this.resourceVersion = resourceVersion;
            Set<String> required = new HashSet<>(requestedResources);
            for (Kind kind : Kind.values()) {
                if (kind != Kind.EIP || MAIN_ROOT.equals(root)) {
                    required.add(root + '/' + directory(kind) + ".properties");
                }
            }
            if (MAIN_ROOT.equals(root)) {
                required.add("META-INF/version.properties");
            }
            this.entries = snapshotEntries(artifact.bytes(), required);
            if (MAIN_ROOT.equals(root)) {
                if (!resourceVersion.equals(embeddedVersion(read("META-INF/version.properties")))) {
                    throw new IOException(
                            "Camel catalog embedded version differs from the requested version");
                }
            }
        }

        private Set<String> names(Kind kind) throws IOException {
            if (kind == Kind.EIP && !MAIN_ROOT.equals(root)) {
                throw new IOException("Runtime provider catalog cannot supply EIP models");
            }
            Set<String> cached = names.get(kind);
            if (cached != null) {
                return cached;
            }
            LinkedHashMap<String, Boolean> values = new LinkedHashMap<>();
            String content = strictUtf8(read(root + '/' + directory(kind) + ".properties"));
            var lines = content.lines().iterator();
            while (lines.hasNext()) {
                String line = lines.next();
                String name = line.trim();
                if (name.isEmpty() || name.startsWith("#")) {
                    continue;
                }
                try {
                    new CatalogSubject(kind, name);
                } catch (IllegalArgumentException e) {
                    throw new IOException("Catalog index contains an unsafe name", e);
                }
                if (values.putIfAbsent(name, Boolean.TRUE) != null) {
                    throw new IOException("Catalog index contains duplicate name: " + name);
                }
                if (values.size() > MAX_NAMES_PER_KIND) {
                    throw new IOException("Catalog index contains too many names");
                }
            }
            Set<String> immutable = Set.copyOf(values.keySet());
            names.put(kind, immutable);
            return immutable;
        }

        private SubjectEvidence verify(CatalogSubject subject, CatalogTarget target, boolean provider)
                throws IOException {
            String resource = root + '/' + directory(subject.kind()) + '/' + subject.name() + ".json";
            byte[] bytes = read(resource);
            JsonNode document;
            try {
                document = JSON.readTree(bytes);
            } catch (IOException | RuntimeException e) {
                throw new IOException("Catalog resource is not valid JSON: " + resource, e);
            }
            String identityName = identityNode(subject.kind());
            JsonNode identity = document == null ? null : document.get(identityName);
            if (identity == null || !identity.isObject()) {
                throw new IOException("Catalog resource lacks " + identityName + " identity: " + resource);
            }
            String expectedKind = subject.kind() == Kind.EIP ? "model" : kindName(subject.kind());
            if (!expectedKind.equals(text(identity, "kind")) || !subject.name().equals(text(identity, "name"))) {
                throw new IOException("Catalog resource identity mismatch: " + resource);
            }

            String groupId = text(identity, "groupId");
            String artifactId = text(identity, "artifactId");
            String version = text(identity, "version");
            if (subject.kind() != Kind.EIP) {
                String expectedGroup = provider
                        ? ("spring-boot".equals(target.runtime())
                                ? "org.apache.camel.springboot" : "org.apache.camel.quarkus")
                        : "org.apache.camel";
                if (!expectedGroup.equals(groupId) || artifactId == null || artifactId.isBlank()
                        || !resourceVersion.equals(version)) {
                    throw new IOException("Catalog resource Maven identity mismatch: " + resource);
                }
                if (provider && "quarkus".equals(target.runtime())) {
                    JsonNode metadata = identity.get("metadata");
                    if (metadata == null || !target.camelVersion().equals(text(metadata, "camelVersion"))) {
                        throw new IOException(
                                "Quarkus catalog resource does not bind Camel "
                                              + target.camelVersion() + ": " + resource);
                    }
                }
            }
            JsonNode deprecated = identity.get("deprecated");
            if (deprecated == null || !deprecated.isBoolean()) {
                throw new IOException("Catalog resource lacks a boolean deprecated flag: " + resource);
            }
            try {
                return new SubjectEvidence(
                        subject,
                        artifact.coordinate(),
                        artifact.sha256(),
                        resource,
                        sha256(bytes),
                        groupId,
                        artifactId,
                        version,
                        deprecated.booleanValue());
            } catch (IllegalArgumentException e) {
                throw new IOException("Catalog resource contains invalid bounded identity: " + resource, e);
            }
        }

        private CatalogComponentModel componentModel(
                CatalogSubject subject, CatalogTarget target, boolean provider)
                throws IOException {
            if (subject.kind() != Kind.COMPONENT) {
                throw new IOException("Only component catalog resources contain endpoint option metadata");
            }
            SubjectEvidence evidence = verify(subject, target, provider);
            byte[] bytes = read(evidence.resource());
            JsonNode document;
            try {
                document = JSON.readTree(bytes);
            } catch (IOException | RuntimeException e) {
                throw new IOException("Catalog component resource is not valid JSON: " + evidence.resource(), e);
            }
            JsonNode identity = document == null ? null : document.get("component");
            String syntax = text(identity, "syntax");
            JsonNode lenient = identity == null ? null : identity.get("lenientProperties");
            if (syntax == null || !syntax.startsWith(subject.name() + ':')
                    || lenient == null || !lenient.isBoolean()) {
                throw new IOException("Catalog component lacks a bounded syntax policy: " + evidence.resource());
            }

            List<CatalogComponentModel.Option> options = new ArrayList<>();
            parseOptions(document.get("componentProperties"), CatalogComponentModel.Scope.COMPONENT,
                    CatalogComponentModel.Kind.PROPERTY, options, evidence.resource());
            parseOptions(document.get("properties"), CatalogComponentModel.Scope.ENDPOINT,
                    null, options, evidence.resource());
            options.sort(Comparator.comparing(CatalogComponentModel.Option::scope)
                    .thenComparing(CatalogComponentModel.Option::kind)
                    .thenComparingInt(CatalogComponentModel.Option::index)
                    .thenComparing(CatalogComponentModel.Option::name));

            Set<String> unique = new HashSet<>();
            for (CatalogComponentModel.Option option : options) {
                String key = option.scope() + ":" + normalizedOptionName(option.name());
                if (!unique.add(key)) {
                    throw new IOException("Catalog component has ambiguous option names: " + evidence.resource());
                }
            }
            try {
                return new CatalogComponentModel(evidence, syntax, lenient.booleanValue(), List.copyOf(options));
            } catch (IllegalArgumentException e) {
                throw new IOException(
                        "Catalog component violates the bounded option model: "
                                      + evidence.resource(),
                        e);
            }
        }

        private static void parseOptions(
                JsonNode node,
                CatalogComponentModel.Scope scope,
                CatalogComponentModel.Kind requiredKind,
                List<CatalogComponentModel.Option> result,
                String resource)
                throws IOException {
            if (node == null || !node.isObject()) {
                throw new IOException("Catalog component lacks option metadata: " + resource);
            }
            for (Map.Entry<String, JsonNode> entry : node.properties()) {
                String name = entry.getKey();
                JsonNode value = entry.getValue();
                try {
                    new CatalogSubject(Kind.COMPONENT, name);
                } catch (IllegalArgumentException e) {
                    throw new IOException("Catalog component has an unsafe option name: " + resource, e);
                }
                if (value == null || !value.isObject()) {
                    throw new IOException("Catalog component option is not an object: " + resource + '#' + name);
                }
                String rawKind = text(value, "kind");
                CatalogComponentModel.Kind kind = switch (rawKind == null ? "" : rawKind) {
                    case "property" -> CatalogComponentModel.Kind.PROPERTY;
                    case "path" -> CatalogComponentModel.Kind.PATH;
                    case "parameter" -> CatalogComponentModel.Kind.PARAMETER;
                    default -> throw new IOException(
                            "Catalog component option has an unsupported kind: " + resource + '#' + name);
                };
                if (requiredKind != null ? kind != requiredKind : kind == CatalogComponentModel.Kind.PROPERTY) {
                    throw new IOException("Catalog component option has the wrong scope: " + resource + '#' + name);
                }
                JsonNode index = value.get("index");
                JsonNode required = value.get("required");
                JsonNode multiValue = value.get("multiValue");
                String type = text(value, "type");
                String javaType = text(value, "javaType");
                if (index == null || !index.isIntegralNumber() || !index.canConvertToInt()
                        || index.intValue() < 0
                        || required == null || !required.isBoolean()
                        || multiValue != null && !multiValue.isBoolean()
                        || type == null || javaType == null) {
                    throw new IOException(
                            "Catalog component option metadata is incomplete: "
                                          + resource + '#' + name);
                }
                String prefix = text(value, "prefix");
                if (prefix != null && (!prefix.matches("[A-Za-z0-9][A-Za-z0-9+._-]{0,127}")
                        || prefix.chars().anyMatch(Character::isISOControl))) {
                    throw new IOException(
                            "Catalog component option has an unsafe prefix: "
                                          + resource + '#' + name);
                }
                String optionalPrefix = text(value, "optionalPrefix");
                if (optionalPrefix != null
                        && (!optionalPrefix.matches("[A-Za-z0-9][A-Za-z0-9+._-]{0,127}")
                                || optionalPrefix.chars().anyMatch(Character::isISOControl))) {
                    throw new IOException(
                            "Catalog component option has an unsafe optional prefix: "
                                          + resource + '#' + name);
                }
                List<String> enumValues = enumValues(value.get("enum"), resource, name);
                if (result.size() == CatalogComponentModel.MAX_OPTIONS) {
                    throw new IOException("Catalog component has too many options: " + resource);
                }
                try {
                    result.add(new CatalogComponentModel.Option(
                            name, scope, kind, index.intValue(), type, javaType, required.booleanValue(),
                            multiValue != null && multiValue.booleanValue(), prefix, optionalPrefix, enumValues));
                } catch (IllegalArgumentException e) {
                    throw new IOException(
                            "Catalog component option violates the bounded model: "
                                          + resource + '#' + name,
                            e);
                }
            }
        }

        private static List<String> enumValues(JsonNode node, String resource, String option) throws IOException {
            if (node == null) {
                return List.of();
            }
            if (!node.isArray() || node.size() > CatalogComponentModel.MAX_ENUM_VALUES) {
                throw new IOException("Catalog component option has an unsafe enum: " + resource + '#' + option);
            }
            List<String> result = new ArrayList<>();
            Set<String> unique = new HashSet<>();
            for (JsonNode value : node) {
                if (!value.isTextual() || value.asText().isBlank()
                        || value.asText().length() > 512 || !unique.add(value.asText())) {
                    throw new IOException(
                            "Catalog component option has an invalid enum: "
                                          + resource + '#' + option);
                }
                result.add(value.asText());
            }
            return List.copyOf(result);
        }

        private static String normalizedOptionName(String value) {
            return value.replace("-", "").replace("_", "").toLowerCase(Locale.ROOT);
        }

        private static String embeddedVersion(byte[] bytes) throws IOException {
            String result = null;
            var lines = strictUtf8(bytes).lines().iterator();
            while (lines.hasNext()) {
                String line = lines.next();
                String value = line.trim();
                if (value.isEmpty() || value.startsWith("#") || value.startsWith("!")) {
                    continue;
                }
                if (!value.startsWith("version=") || result != null) {
                    throw new IOException("Camel catalog version metadata is not canonical");
                }
                result = value.substring("version=".length()).trim();
            }
            if (result == null || !safe(result) || !immutableRelease(result)) {
                throw new IOException("Camel catalog version metadata lacks an immutable version");
            }
            return result;
        }

        private static String strictUtf8(byte[] bytes) throws IOException {
            try {
                return StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes))
                        .toString();
            } catch (CharacterCodingException e) {
                throw new IOException("Catalog text resource is not strict UTF-8", e);
            }
        }

        private byte[] read(String name) throws IOException {
            byte[] bytes = entries.get(name);
            if (bytes == null) {
                throw new IOException("Catalog artifact is missing resource: " + name);
            }
            return bytes;
        }

        private static Map<String, byte[]> snapshotEntries(byte[] archive, Set<String> required)
                throws IOException {
            Set<String> seen = new HashSet<>();
            Map<String, byte[]> selected = new HashMap<>();
            long total = 0;
            int count = 0;
            byte[] buffer = new byte[16_384];
            try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    String name = entry.getName();
                    if (++count > MAX_ZIP_ENTRIES || !seen.add(name)) {
                        throw new IOException("Catalog ZIP has too many or duplicate entries");
                    }
                    requireSafeZipName(name, entry.isDirectory());
                    ByteArrayOutputStream retained = required.contains(name)
                            ? new ByteArrayOutputStream() : null;
                    long entryBytes = 0;
                    int read;
                    while ((read = zip.read(buffer)) >= 0) {
                        if (read == 0) {
                            continue;
                        }
                        entryBytes = Math.addExact(entryBytes, read);
                        total = Math.addExact(total, read);
                        if (total > MAX_ZIP_UNCOMPRESSED_BYTES) {
                            throw new IOException("Catalog ZIP exceeds the uncompressed size limit");
                        }
                        if (retained != null) {
                            if (entryBytes > MAX_RESOURCE_BYTES) {
                                throw new IOException("Catalog resource exceeds its size limit: " + name);
                            }
                            retained.write(buffer, 0, read);
                        }
                    }
                    if (retained != null) {
                        selected.put(name, retained.toByteArray());
                    }
                    zip.closeEntry();
                }
            } catch (ArithmeticException e) {
                throw new IOException("Catalog ZIP size accounting overflowed", e);
            }
            if (count == 0) {
                throw new IOException("Catalog artifact is not a non-empty ZIP archive");
            }
            return Map.copyOf(selected);
        }

        private static void requireSafeZipName(String name, boolean directory) throws IOException {
            if (name == null || name.isEmpty() || name.length() > 512 || name.startsWith("/")
                    || name.indexOf('\\') >= 0
                    || name.indexOf('\0') >= 0) {
                throw new IOException("Catalog ZIP contains an unsafe entry name");
            }
            String[] segments = name.split("/", -1);
            for (int i = 0; i < segments.length; i++) {
                String segment = segments[i];
                if (segment.isEmpty() && directory && i == segments.length - 1) {
                    continue;
                }
                if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                    throw new IOException("Catalog ZIP contains an unsafe entry name");
                }
            }
        }

        private static String identityNode(Kind kind) {
            return switch (kind) {
                case COMPONENT -> "component";
                case EIP -> "model";
                case DATAFORMAT -> "dataformat";
                case LANGUAGE -> "language";
            };
        }

        private static String kindName(Kind kind) {
            return switch (kind) {
                case COMPONENT -> "component";
                case DATAFORMAT -> "dataformat";
                case LANGUAGE -> "language";
                case EIP -> "model";
            };
        }

        private static String directory(Kind kind) {
            return switch (kind) {
                case COMPONENT -> "components";
                case EIP -> "models";
                case DATAFORMAT -> "dataformats";
                case LANGUAGE -> "languages";
            };
        }

        private static String text(JsonNode node, String name) {
            JsonNode value = node == null ? null : node.get(name);
            return value != null && value.isTextual() && !value.asText().isBlank() ? value.asText() : null;
        }
    }

    @FunctionalInterface
    interface ArtifactResolver {
        List<ResolvedExactMavenArtifact> resolve(
                Path repository, List<MavenCoordinate> coordinates, ResolutionMode mode)
                throws IOException;
    }
}
