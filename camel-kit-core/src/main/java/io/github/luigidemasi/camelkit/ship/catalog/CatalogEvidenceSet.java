package io.github.luigidemasi.camelkit.ship.catalog;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import io.github.luigidemasi.camelkit.ship.resolver.MavenCoordinate;

/** Immutable descriptive snapshot of exact catalog artifacts and resources. */
public record CatalogEvidenceSet(
        int schemaVersion,
        CatalogTarget target,
        MavenCoordinate platformCoordinate,
        List<ArtifactEvidence> artifacts,
        List<SubjectEvidence> subjects,
        String digest) {

    public static final int SCHEMA_VERSION = 1;
    static final int MAX_ARTIFACTS = 5;
    static final int MAX_SUBJECTS = 512;

    private static final long MAX_JAR_BYTES = 128L * 1024 * 1024;
    private static final long MAX_POM_BYTES = 4L * 1024 * 1024;
    private static final Pattern DIGEST = Pattern.compile("sha256:[0-9a-f]{64}");
    private static final Pattern SAFE_IDENTITY = Pattern.compile("[A-Za-z0-9][A-Za-z0-9+._-]{0,127}");
    private static final Comparator<ArtifactEvidence> ARTIFACT_ORDER
            = Comparator.comparing(value -> value.coordinate().resolverString());
    private static final Comparator<SubjectEvidence> SUBJECT_ORDER
            = Comparator.comparing(SubjectEvidence::subject)
                    .thenComparing(SubjectEvidence::resource);

    public CatalogEvidenceSet {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported catalog snapshot schema version");
        }
        Objects.requireNonNull(target, "target must not be null");
        Objects.requireNonNull(platformCoordinate, "platformCoordinate must not be null");
        artifacts = boundedCopy(artifacts, 1, MAX_ARTIFACTS, "artifacts");
        subjects = boundedCopy(subjects, 1, MAX_SUBJECTS, "subjects");
        requireCanonical(artifacts, ARTIFACT_ORDER, "artifacts");
        requireCanonical(subjects, SUBJECT_ORDER, "subjects");
        validateSnapshot(target, platformCoordinate, artifacts, subjects);
        String expected = digestFor(schemaVersion, target, platformCoordinate, artifacts, subjects);
        if (!expected.equals(digest)) {
            throw new IllegalArgumentException("Catalog snapshot digest does not match its contents");
        }
    }

    @Override
    public String toString() {
        return "CatalogEvidenceSet[schemaVersion=" + schemaVersion
               + ", target=" + target
               + ", platformCoordinate=" + platformCoordinate
               + ", artifacts=<" + artifacts.size() + " entries>"
               + ", subjects=<" + subjects.size() + " entries>"
               + ", digest=" + digest + ']';
    }

    /** Creates a canonically ordered snapshot and derives its content digest. */
    static CatalogEvidenceSet create(
            CatalogTarget target,
            MavenCoordinate platformCoordinate,
            List<ArtifactEvidence> artifacts,
            List<SubjectEvidence> subjects) {
        List<ArtifactEvidence> canonicalArtifacts = sortedCopy(
                artifacts, MAX_ARTIFACTS, ARTIFACT_ORDER, "artifacts");
        List<SubjectEvidence> canonicalSubjects = sortedCopy(
                subjects, MAX_SUBJECTS, SUBJECT_ORDER, "subjects");
        String digest = digestFor(
                SCHEMA_VERSION, target, platformCoordinate, canonicalArtifacts, canonicalSubjects);
        return new CatalogEvidenceSet(
                SCHEMA_VERSION, target, platformCoordinate, canonicalArtifacts, canonicalSubjects, digest);
    }

    /** Exact Maven artifact bytes included in this descriptive snapshot. */
    public record ArtifactEvidence(MavenCoordinate coordinate, String sha256, long size) {
        public ArtifactEvidence {
            Objects.requireNonNull(coordinate, "coordinate must not be null");
            if (!coordinate.classifier().isEmpty()
                    || !("jar".equals(coordinate.extension()) || "pom".equals(coordinate.extension()))) {
                throw new IllegalArgumentException("Catalog artifact must be an unclassified JAR or POM");
            }
            requireDigest(sha256, "artifact");
            long limit = "pom".equals(coordinate.extension()) ? MAX_POM_BYTES : MAX_JAR_BYTES;
            if (size <= 0 || size > limit) {
                throw new IllegalArgumentException("Catalog artifact size is outside its fixed bound");
            }
        }
    }

    /** Exact catalog resource and source-artifact binding for one requested subject. */
    public record SubjectEvidence(
            CatalogSubject subject,
            MavenCoordinate catalogCoordinate,
            String catalogSha256,
            String resource,
            String resourceSha256,
            String groupId,
            String artifactId,
            String artifactVersion,
            boolean deprecated) {

        public SubjectEvidence {
            Objects.requireNonNull(subject, "subject must not be null");
            Objects.requireNonNull(catalogCoordinate, "catalogCoordinate must not be null");
            if (!"jar".equals(catalogCoordinate.extension()) || !catalogCoordinate.classifier().isEmpty()) {
                throw new IllegalArgumentException("Catalog resource source must be an unclassified JAR");
            }
            requireDigest(catalogSha256, "catalog");
            requireResource(resource);
            requireDigest(resourceSha256, "resource");
            boolean absentIdentity = groupId == null && artifactId == null && artifactVersion == null;
            boolean completeIdentity = safeIdentity(groupId) && safeIdentity(artifactId)
                    && safeIdentity(artifactVersion);
            if (subject.kind() == CatalogSubject.Kind.EIP ? !absentIdentity : !completeIdentity) {
                throw new IllegalArgumentException("Catalog resource Maven identity is incomplete or unexpected");
            }
        }
    }

    private static void validateSnapshot(
            CatalogTarget target,
            MavenCoordinate platform,
            List<ArtifactEvidence> artifacts,
            List<SubjectEvidence> subjects) {
        Map<MavenCoordinate, ArtifactEvidence> byCoordinate = new HashMap<>();
        for (ArtifactEvidence artifact : artifacts) {
            if (byCoordinate.put(artifact.coordinate(), artifact) != null) {
                throw new IllegalArgumentException("Catalog snapshot contains duplicate artifact coordinates");
            }
        }
        MavenCoordinate main = MavenCoordinate.jar(
                "org.apache.camel", "camel-catalog", target.camelVersion());
        requireArtifact(byCoordinate, main);
        Set<MavenCoordinate> subjectSources = new HashSet<>();
        subjectSources.add(main);
        switch (target.runtime()) {
            case "main" -> {
                requireExactArtifacts(artifacts, 1);
                requireCoordinate(platform, main);
            }
            case "spring-boot" -> {
                MavenCoordinate provider = MavenCoordinate.jar(
                        "org.apache.camel.springboot", "camel-catalog-provider-springboot",
                        target.platformVersion());
                requireCoordinate(platform, provider);
                requireArtifact(byCoordinate, provider);
                requireArtifact(byCoordinate, provider.withExtension("pom"));
                requireArtifact(byCoordinate, MavenCoordinate.of(
                        "org.apache.camel.springboot", "spring-boot",
                        target.platformVersion(), "pom"));
                requireArtifact(byCoordinate, MavenCoordinate.of(
                        "org.apache.camel", "camel-dependencies",
                        target.camelVersion(), "pom"));
                requireExactArtifacts(artifacts, 5);
                subjectSources.add(provider);
            }
            case "quarkus" -> {
                MavenCoordinate bom = MavenCoordinate.of(
                        "io.quarkus.platform", "quarkus-camel-bom", target.platformVersion(), "pom");
                requireCoordinate(platform, bom);
                requireArtifact(byCoordinate, bom);
                ArtifactEvidence provider = artifacts.stream()
                        .filter(value -> "org.apache.camel.quarkus".equals(value.coordinate().groupId()))
                        .filter(value -> "camel-quarkus-catalog".equals(value.coordinate().artifactId()))
                        .filter(value -> "jar".equals(value.coordinate().extension()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Quarkus catalog snapshot lacks its provider artifact"));
                requireExactArtifacts(artifacts, 3);
                subjectSources.add(provider.coordinate());
            }
            default -> throw new IllegalStateException("Unexpected catalog runtime");
        }

        Set<CatalogSubject> uniqueSubjects = new HashSet<>();
        Set<String> uniqueResources = new HashSet<>();
        for (SubjectEvidence subject : subjects) {
            if (!uniqueSubjects.add(subject.subject()) || !uniqueResources.add(subject.resource())) {
                throw new IllegalArgumentException("Catalog snapshot contains duplicate subjects or resources");
            }
            if (!subjectSources.contains(subject.catalogCoordinate())) {
                throw new IllegalArgumentException("Catalog subject references an artifact outside the snapshot");
            }
            ArtifactEvidence source = requireArtifact(byCoordinate, subject.catalogCoordinate());
            if (!source.sha256().equals(subject.catalogSha256())) {
                throw new IllegalArgumentException("Catalog subject source digest does not match its artifact");
            }
            validateSubjectBinding(target, main, subject);
        }
    }

    private static void validateSubjectBinding(
            CatalogTarget target, MavenCoordinate main, SubjectEvidence evidence) {
        CatalogSubject subject = evidence.subject();
        boolean mainSource = evidence.catalogCoordinate().equals(main);
        if (subject.kind() == CatalogSubject.Kind.EIP && !mainSource) {
            throw new IllegalArgumentException("EIP catalog subjects must come from the main catalog artifact");
        }
        if (subject.kind() != CatalogSubject.Kind.EIP && "main".equals(target.runtime()) != mainSource) {
            throw new IllegalArgumentException("Catalog subject comes from the wrong runtime artifact");
        }
        String root = mainSource
                ? "org/apache/camel/catalog"
                : "spring-boot".equals(target.runtime())
                        ? "org/apache/camel/springboot/catalog"
                : "org/apache/camel/catalog/quarkus";
        String expectedResource = root + '/' + directory(subject.kind()) + '/' + subject.name() + ".json";
        if (!expectedResource.equals(evidence.resource())) {
            throw new IllegalArgumentException("Catalog subject resource does not match its canonical identity");
        }
        if (subject.kind() != CatalogSubject.Kind.EIP) {
            String expectedGroup = mainSource
                    ? "org.apache.camel"
                    : "spring-boot".equals(target.runtime())
                            ? "org.apache.camel.springboot"
                    : "org.apache.camel.quarkus";
            if (!expectedGroup.equals(evidence.groupId())
                    || !evidence.catalogCoordinate().version().equals(evidence.artifactVersion())) {
                throw new IllegalArgumentException("Catalog subject Maven identity does not match its source artifact");
            }
        }
    }

    private static ArtifactEvidence requireArtifact(
            Map<MavenCoordinate, ArtifactEvidence> artifacts, MavenCoordinate coordinate) {
        ArtifactEvidence result = artifacts.get(coordinate);
        if (result == null) {
            throw new IllegalArgumentException("Catalog snapshot lacks a required artifact");
        }
        return result;
    }

    private static void requireCoordinate(MavenCoordinate actual, MavenCoordinate expected) {
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException("Catalog snapshot platform coordinate is inconsistent");
        }
    }

    private static void requireExactArtifacts(List<ArtifactEvidence> artifacts, int expected) {
        if (artifacts.size() != expected) {
            throw new IllegalArgumentException("Catalog snapshot artifact set is incomplete or excessive");
        }
    }

    private static <T> void requireCanonical(List<T> values, Comparator<? super T> comparator, String label) {
        for (int index = 1; index < values.size(); index++) {
            if (comparator.compare(values.get(index - 1), values.get(index)) >= 0) {
                throw new IllegalArgumentException("Catalog snapshot " + label + " are not canonical and unique");
            }
        }
    }

    private static <T> List<T> sortedCopy(
            List<T> values, int maximum, Comparator<? super T> order, String label) {
        List<T> copy = new ArrayList<>(boundedCopy(values, 1, maximum, label));
        copy.sort(order);
        return List.copyOf(copy);
    }

    private static <T> List<T> boundedCopy(List<T> values, int minimum, int maximum, String label) {
        Objects.requireNonNull(values, label + " must not be null");
        List<T> copy = new ArrayList<>();
        for (T value : values) {
            if (copy.size() == maximum) {
                throw new IllegalArgumentException("Catalog snapshot " + label + " exceed their fixed bound");
            }
            copy.add(Objects.requireNonNull(value, label + " must not contain null"));
        }
        if (copy.size() < minimum) {
            throw new IllegalArgumentException("Catalog snapshot " + label + " are empty");
        }
        return List.copyOf(copy);
    }

    private static void requireDigest(String value, String label) {
        if (value == null || !DIGEST.matcher(value).matches()) {
            throw new IllegalArgumentException("Catalog " + label + " digest is invalid");
        }
    }

    private static void requireResource(String resource) {
        if (resource == null || resource.length() > 512 || resource.startsWith("/")
                || resource.indexOf('\\') >= 0 || resource.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Catalog resource path is invalid");
        }
        for (String segment : resource.split("/", -1)) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                throw new IllegalArgumentException("Catalog resource path is invalid");
            }
        }
    }

    private static boolean safeIdentity(String value) {
        return value != null && SAFE_IDENTITY.matcher(value).matches();
    }

    private static String directory(CatalogSubject.Kind kind) {
        return switch (kind) {
            case COMPONENT -> "components";
            case EIP -> "models";
            case DATAFORMAT -> "dataformats";
            case LANGUAGE -> "languages";
        };
    }

    private static String digestFor(
            int schemaVersion,
            CatalogTarget target,
            MavenCoordinate platform,
            List<ArtifactEvidence> artifacts,
            List<SubjectEvidence> subjects) {
        MessageDigest digest = sha256();
        put(digest, "camel-kit.ship.catalog-snapshot.v1");
        putInt(digest, schemaVersion);
        put(digest, target.runtime());
        put(digest, target.camelVersion());
        putNullable(digest, target.platformVersion());
        putNullable(digest, target.springBootVersion());
        put(digest, platform.resolverString());
        putInt(digest, artifacts.size());
        for (ArtifactEvidence artifact : artifacts) {
            put(digest, artifact.coordinate().resolverString());
            put(digest, artifact.sha256());
            putLong(digest, artifact.size());
        }
        putInt(digest, subjects.size());
        for (SubjectEvidence subject : subjects) {
            put(digest, subject.subject().kind().name());
            put(digest, subject.subject().name());
            put(digest, subject.catalogCoordinate().resolverString());
            put(digest, subject.catalogSha256());
            put(digest, subject.resource());
            put(digest, subject.resourceSha256());
            putNullable(digest, subject.groupId());
            putNullable(digest, subject.artifactId());
            putNullable(digest, subject.artifactVersion());
            digest.update((byte) (subject.deprecated() ? 1 : 0));
        }
        return "sha256:" + java.util.HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static void putNullable(MessageDigest digest, String value) {
        digest.update((byte) (value == null ? 0 : 1));
        if (value != null) {
            put(digest, value);
        }
    }

    private static void put(MessageDigest digest, String value) {
        byte[] bytes = Objects.requireNonNull(value, "digest value").getBytes(StandardCharsets.UTF_8);
        putInt(digest, bytes.length);
        digest.update(bytes);
    }

    private static void putInt(MessageDigest digest, int value) {
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(value).array());
    }

    private static void putLong(MessageDigest digest, long value) {
        digest.update(ByteBuffer.allocate(Long.BYTES).putLong(value).array());
    }
}
