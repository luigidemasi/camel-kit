package io.github.luigidemasi.camelkit.ship.catalog;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

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
    private static final long MAX_POM_BYTES = 4L * 1024 * 1024;
    private static final int MAX_POM_DEPTH = 64;
    private static final int MAX_POM_ELEMENTS = 16_384;
    private static final int MAX_POM_ATTRIBUTES = 512;
    private static final int MAX_POM_NODES = 65_536;
    private static final int MAX_POM_VALUE_CHARS = 1_024;
    private static final int MAX_ZIP_ENTRIES = 8_192;
    private static final long MAX_ZIP_UNCOMPRESSED_BYTES = 256L * 1024 * 1024;
    private static final int MAX_RESOURCE_BYTES = 2 * 1024 * 1024;
    private static final int MAX_SUBJECTS = 512;
    private static final int MAX_NAMES_PER_KIND = 8_192;
    private static final String MAIN_ROOT = "org/apache/camel/catalog";
    private static final String SPRING_ROOT = "org/apache/camel/springboot/catalog";
    private static final String QUARKUS_ROOT = "org/apache/camel/catalog/quarkus";
    private static final String POM_NAMESPACE = "http://maven.apache.org/POM/4.0.0";
    private static final Pattern SAFE_COORDINATE = Pattern.compile("[A-Za-z0-9_.-]+");
    private static final Pattern TIMESTAMPED_SNAPSHOT = Pattern.compile(".*-\\d{8}\\.\\d{6}-\\d+$");
    private static final Pattern PROPERTY = Pattern.compile("\\$\\{([^}]+)}");
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
        MavenCoordinate mainGav = gav("org.apache.camel", "camel-catalog", target.camelVersion(), "jar");
        ResolvedArtifactSnapshot main = resolveArtifact(mainGav);
        List<ResolvedArtifactSnapshot> artifacts = new ArrayList<>();
        artifacts.add(main);

        return switch (target.runtime()) {
            case "main" -> {
                yield new ResolvedCatalog(
                        target, mainGav, main, null, null, null,
                        List.copyOf(artifacts));
            }
            case "spring-boot" -> resolveSpring(target, main, artifacts);
            case "quarkus" -> resolveQuarkus(target, main, artifacts);
            default -> throw new IOException("Unsupported catalog runtime: " + target.runtime());
        };
    }

    private ResolvedCatalog resolveSpring(
            CatalogTarget target, ResolvedArtifactSnapshot main, List<ResolvedArtifactSnapshot> artifacts)
            throws IOException {
        MavenCoordinate providerGav = gav(
                "org.apache.camel.springboot", "camel-catalog-provider-springboot",
                target.platformVersion(), "jar");

        MavenCoordinate providerPomGav = gav(
                providerGav.groupId(), providerGav.artifactId(), providerGav.version(), "pom");
        ResolvedArtifactSnapshot providerPom = resolveArtifact(providerPomGav);
        MavenCoordinate springRootGav = gav(
                "org.apache.camel.springboot", "spring-boot", providerGav.version(), "pom");
        ResolvedArtifactSnapshot springRoot = resolveArtifact(springRootGav);
        MavenCoordinate dependenciesGav = pomParentCoordinate(springRoot.bytes());
        MavenCoordinate expectedDependencies = gav(
                "org.apache.camel", "camel-dependencies", target.camelVersion(), "pom");
        if (!expectedDependencies.equals(dependenciesGav)) {
            throw new IOException("Spring Boot catalog root has an unexpected parent POM");
        }
        ResolvedArtifactSnapshot dependencies = resolveArtifact(dependenciesGav);
        Map<String, String> inheritedProperties = new HashMap<>(pomProperties(dependencies.bytes()));
        inheritedProperties.putAll(pomProperties(springRoot.bytes()));
        String springBootVersion = resolvePomValue(
                inheritedProperties.get("spring-boot-version"), inheritedProperties);
        if (!safe(springBootVersion) || !immutableRelease(springBootVersion)) {
            throw new IOException("Spring Boot catalog metadata lacks an immutable Spring Boot version");
        }
        if (!target.springBootVersion().equals(springBootVersion)) {
            throw new IOException(
                    "Spring Boot catalog metadata does not match the approved Spring Boot version");
        }
        String providerCamelVersion = pomVersion(
                providerPom.bytes(), false, "org.apache.camel", "camel-catalog", inheritedProperties);
        if (!target.camelVersion().equals(providerCamelVersion)) {
            throw new IOException(
                    "Spring Boot catalog provider does not match the approved Camel version");
        }

        ResolvedArtifactSnapshot provider = resolveArtifact(providerGav);
        artifacts.add(providerPom);
        artifacts.add(springRoot);
        artifacts.add(dependencies);
        artifacts.add(provider);
        return new ResolvedCatalog(
                target, providerGav, main, provider, SPRING_ROOT,
                providerGav.version(), List.copyOf(artifacts));
    }

    private ResolvedCatalog resolveQuarkus(
            CatalogTarget target, ResolvedArtifactSnapshot main, List<ResolvedArtifactSnapshot> artifacts)
            throws IOException {
        if (target.platformVersion() == null) {
            throw new IOException("Quarkus catalog verification requires platformVersion");
        }
        MavenCoordinate bomGav = gav(
                "io.quarkus.platform", "quarkus-camel-bom", target.platformVersion(), "pom");
        ResolvedArtifactSnapshot bom = resolveArtifact(bomGav);

        String managedCamelVersion = pomVersion(
                bom.bytes(), true, "org.apache.camel", "camel-catalog");
        if (!target.camelVersion().equals(managedCamelVersion)) {
            throw new IOException("Quarkus platform does not match the approved Camel version");
        }
        String quarkusCatalogVersion = pomVersion(
                bom.bytes(), true, "org.apache.camel.quarkus", "camel-quarkus-catalog");
        if (quarkusCatalogVersion == null) {
            throw new IOException("Quarkus platform BOM does not manage camel-quarkus-catalog");
        }
        MavenCoordinate providerGav = gav(
                "org.apache.camel.quarkus", "camel-quarkus-catalog", quarkusCatalogVersion, "jar");
        ResolvedArtifactSnapshot provider = resolveArtifact(providerGav);
        artifacts.add(bom);
        artifacts.add(provider);
        return new ResolvedCatalog(
                target, bomGav, main, provider, QUARKUS_ROOT,
                quarkusCatalogVersion, List.copyOf(artifacts));
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
        long limit = "pom".equals(coordinate.extension()) ? MAX_POM_BYTES : MAX_ARTIFACT_BYTES;
        byte[] bytes;
        try {
            bytes = CatalogArtifactReader.read(localRepository, normalized, limit);
        } catch (IOException e) {
            throw new IOException(
                    "Could not securely snapshot the exact catalog artifact "
                                  + resolverString(coordinate),
                    e);
        }
        String digest = sha256(bytes);
        if (bytes.length != artifact.contentLength() || !digest.equals(artifact.contentSha256())) {
            throw new IOException("Catalog artifact does not match its acquired content identity");
        }
        if ("pom".equals(coordinate.extension())) {
            requirePomCoordinate(bytes, coordinate);
        }
        return new ResolvedArtifactSnapshot(coordinate, bytes, digest);
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

    private static String pomVersion(byte[] pom, boolean managed, String groupId, String artifactId)
            throws IOException {
        return pomVersion(pom, managed, groupId, artifactId, Map.of());
    }

    private static String pomVersion(
            byte[] pom,
            boolean managed,
            String groupId,
            String artifactId,
            Map<String, String> inheritedProperties)
            throws IOException {
        Document document = parsePom(pom);
        Element project = document.getDocumentElement();
        Map<String, String> properties = new HashMap<>(inheritedProperties);
        Set<String> localProperties = new HashSet<>();
        Element propertiesElement = child(project, "properties");
        if (propertiesElement != null) {
            for (Node node = propertiesElement.getFirstChild(); node != null; node = node.getNextSibling()) {
                if (node instanceof Element property) {
                    String name = localName(property);
                    if (!localProperties.add(name)) {
                        throw new IOException("Catalog POM contains a duplicate Maven property");
                    }
                    requirePomNamespace(property);
                    properties.put(name, elementText(property));
                }
            }
        }
        String projectVersion = childText(project, "version");
        if (projectVersion == null) {
            projectVersion = childText(child(project, "parent"), "version");
        }
        if (projectVersion != null) {
            properties.putIfAbsent("project.version", projectVersion);
            properties.putIfAbsent("pom.version", projectVersion);
        }

        Element dependencies = managed
                ? child(child(project, "dependencyManagement"), "dependencies")
                : child(project, "dependencies");
        String result = null;
        if (dependencies != null) {
            for (Node node = dependencies.getFirstChild(); node != null; node = node.getNextSibling()) {
                if (!(node instanceof Element dependency) || !"dependency".equals(localName(dependency))) {
                    continue;
                }
                requirePomNamespace(dependency);
                if (groupId.equals(childText(dependency, "groupId"))
                        && artifactId.equals(childText(dependency, "artifactId"))) {
                    if (result != null) {
                        throw new IOException(
                                "Catalog POM declares duplicate dependency " + groupId + ':' + artifactId);
                    }
                    result = resolvePomValue(childText(dependency, "version"), properties);
                }
            }
        }
        return result;
    }

    private static Map<String, String> pomProperties(byte[] pom) throws IOException {
        Element project = parsePom(pom).getDocumentElement();
        Map<String, String> properties = new HashMap<>();
        Element propertiesElement = child(project, "properties");
        if (propertiesElement != null) {
            for (Node node = propertiesElement.getFirstChild(); node != null; node = node.getNextSibling()) {
                if (node instanceof Element property) {
                    String name = localName(property);
                    requirePomNamespace(property);
                    if (properties.putIfAbsent(name, elementText(property)) != null) {
                        throw new IOException("Catalog POM contains a duplicate Maven property");
                    }
                }
            }
        }
        return Map.copyOf(properties);
    }

    private static void requirePomCoordinate(byte[] pom, MavenCoordinate expected) throws IOException {
        Element project = parsePom(pom).getDocumentElement();
        Element parent = child(project, "parent");
        Map<String, String> properties = pomProperties(pom);
        String groupId = resolvePomValue(childText(project, "groupId"), properties);
        if (groupId == null) {
            groupId = resolvePomValue(childText(parent, "groupId"), properties);
        }
        String artifactId = resolvePomValue(childText(project, "artifactId"), properties);
        String version = resolvePomValue(childText(project, "version"), properties);
        if (version == null) {
            version = resolvePomValue(childText(parent, "version"), properties);
        }
        if (!expected.groupId().equals(groupId)
                || !expected.artifactId().equals(artifactId)
                || !expected.version().equals(version)) {
            throw new IOException("Catalog POM identity does not match its exact artifact coordinate");
        }
    }

    private static MavenCoordinate pomParentCoordinate(byte[] pom) throws IOException {
        Element parent = child(parsePom(pom).getDocumentElement(), "parent");
        if (parent == null) {
            throw new IOException("Spring Boot catalog root lacks its required parent POM");
        }
        return gav(
                childText(parent, "groupId"),
                childText(parent, "artifactId"),
                childText(parent, "version"),
                "pom");
    }

    private static Document parsePom(byte[] pom) throws IOException {
        if (pom.length == 0 || pom.length > MAX_POM_BYTES) {
            throw new IOException("Catalog POM has an unsafe size");
        }
        requireBoundedPomStructure(pom);
        DocumentBuilderFactory factory = DocumentBuilderFactory.newDefaultInstance();
        try {
            factory.setNamespaceAware(true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setCoalescing(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setAttribute("jdk.xml.elementAttributeLimit", MAX_POM_ATTRIBUTES);
            var builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new StrictXmlErrorHandler());
            try (InputStream input = new ByteArrayInputStream(pom)) {
                Document document = builder.parse(input);
                Element project = document.getDocumentElement();
                if (!"project".equals(localName(project))
                        || !POM_NAMESPACE.equals(project.getNamespaceURI())) {
                    throw new IOException("Catalog POM root is not project");
                }
                if (!"4.0.0".equals(childText(project, "modelVersion"))) {
                    throw new IOException("Catalog POM has an unsupported model version");
                }
                return document;
            }
        } catch (IllegalArgumentException | ParserConfigurationException | SAXException e) {
            throw new IOException("Could not securely parse catalog POM", e);
        }
    }

    private static void requireBoundedPomStructure(byte[] pom) throws IOException {
        XMLInputFactory factory = XMLInputFactory.newDefaultFactory();
        try {
            factory.setProperty("jdk.xml.elementAttributeLimit", MAX_POM_ATTRIBUTES);
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
            factory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
        } catch (IllegalArgumentException e) {
            throw new IOException("Could not securely configure catalog POM preflight", e);
        }
        factory.setXMLResolver((publicId, systemId, baseUri, namespace) -> {
            throw new XMLStreamException("External XML references are forbidden");
        });

        XMLStreamReader reader = null;
        try (InputStream input = new ByteArrayInputStream(pom)) {
            reader = factory.createXMLStreamReader(input);
            int depth = 0;
            int elements = 0;
            int attributes = 0;
            int nodes = 0;
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.DTD || event == XMLStreamConstants.ENTITY_REFERENCE
                        || event == XMLStreamConstants.PROCESSING_INSTRUCTION) {
                    throw new IOException("Catalog POM contains forbidden XML declarations");
                }
                if (event == XMLStreamConstants.START_ELEMENT) {
                    depth = Math.addExact(depth, 1);
                    elements = Math.addExact(elements, 1);
                    attributes = Math.addExact(attributes,
                            Math.addExact(reader.getAttributeCount(), reader.getNamespaceCount()));
                    nodes = Math.addExact(nodes, 1);
                    if (depth > MAX_POM_DEPTH || elements > MAX_POM_ELEMENTS
                            || attributes > MAX_POM_ATTRIBUTES || nodes > MAX_POM_NODES) {
                        throw new IOException("Catalog POM exceeds its structural limits");
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    depth--;
                } else if (event == XMLStreamConstants.CHARACTERS
                        || event == XMLStreamConstants.CDATA
                        || event == XMLStreamConstants.SPACE
                        || event == XMLStreamConstants.COMMENT) {
                    nodes = Math.addExact(nodes, 1);
                    if (nodes > MAX_POM_NODES) {
                        throw new IOException("Catalog POM exceeds its structural limits");
                    }
                }
            }
            if (depth != 0 || elements == 0) {
                throw new IOException("Catalog POM has an invalid XML structure");
            }
        } catch (ArithmeticException e) {
            throw new IOException("Catalog POM structure accounting overflowed", e);
        } catch (XMLStreamException e) {
            throw new IOException("Could not securely preflight catalog POM", e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (XMLStreamException ignored) {
                    // The in-memory source is already closed; parsing failures remain authoritative.
                }
            }
        }
    }

    private static String resolvePomValue(String value, Map<String, String> properties) throws IOException {
        if (value == null || value.isBlank()) {
            return null;
        }
        return expandPomValue(value.trim(), properties, new HashSet<>(), 0);
    }

    private static String expandPomValue(
            String value, Map<String, String> properties, Set<String> active, int depth)
            throws IOException {
        if (depth > 16) {
            throw new IOException("Maven property expansion is too deep");
        }
        Matcher matcher = PROPERTY.matcher(value);
        StringBuilder result = new StringBuilder(Math.min(value.length(), MAX_POM_VALUE_CHARS));
        int cursor = 0;
        while (matcher.find()) {
            appendPomValue(result, value, cursor, matcher.start());
            String key = matcher.group(1);
            String replacement = properties.get(key);
            if (replacement == null) {
                return null;
            }
            if (!active.add(key)) {
                throw new IOException("Catalog POM contains cyclic Maven property expansion");
            }
            try {
                appendPomValue(result, expandPomValue(replacement, properties, active, depth + 1));
            } finally {
                active.remove(key);
            }
            cursor = matcher.end();
        }
        appendPomValue(result, value, cursor, value.length());
        return result.toString();
    }

    private static void appendPomValue(StringBuilder target, CharSequence value) throws IOException {
        appendPomValue(target, value, 0, value.length());
    }

    private static void appendPomValue(StringBuilder target, CharSequence value, int start, int end)
            throws IOException {
        if (end - start > MAX_POM_VALUE_CHARS - target.length()) {
            throw new IOException("Catalog POM property expansion exceeds its size limit");
        }
        target.append(value, start, end);
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

    private static Element child(Element parent, String name) throws IOException {
        if (parent == null) {
            return null;
        }
        Element result = null;
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && name.equals(localName(element))) {
                requirePomNamespace(element);
                if (result != null) {
                    throw new IOException("Catalog POM contains duplicate " + name + " elements");
                }
                result = element;
            }
        }
        return result;
    }

    private static String childText(Element parent, String name) throws IOException {
        Element child = child(parent, name);
        if (child == null) {
            return null;
        }
        String value = elementText(child);
        return value.isBlank() ? null : value;
    }

    private static String elementText(Element element) throws IOException {
        StringBuilder result = new StringBuilder();
        for (Node node = element.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node.getNodeType() != Node.TEXT_NODE && node.getNodeType() != Node.CDATA_SECTION_NODE) {
                throw new IOException("Catalog POM scalar metadata contains nested structure");
            }
            String text = node.getNodeValue();
            if (text.length() > MAX_POM_BYTES - result.length()) {
                throw new IOException("Catalog POM scalar metadata exceeds its size limit");
            }
            result.append(text);
        }
        return result.toString().trim();
    }

    private static void requirePomNamespace(Element element) throws IOException {
        if (!POM_NAMESPACE.equals(element.getNamespaceURI())) {
            throw new IOException("Catalog POM contains foreign-namespace metadata");
        }
    }

    private static String localName(Node node) {
        return node.getLocalName() == null ? node.getNodeName() : node.getLocalName();
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

    private static final class StrictXmlErrorHandler implements ErrorHandler {
        @Override
        public void warning(SAXParseException exception) throws SAXException {
            throw exception;
        }

        @Override
        public void error(SAXParseException exception) throws SAXException {
            throw exception;
        }

        @Override
        public void fatalError(SAXParseException exception) throws SAXException {
            throw exception;
        }
    }
}
