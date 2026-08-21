package io.github.luigidemasi.camelkit.ship.evidence;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Canonical manifest embedded in every controller-owned JVM payload archive. */
public final class JvmPayloadLock {

    public static final int SCHEMA_VERSION = 2;
    public static final String LOCK_PATH = "META-INF/camel-kit/payload.lock";
    private static final int MAX_ROOTS = 64;
    private static final int MAX_EXCLUSIONS = 16;
    private static final int MAX_ENTRIES = 512;
    private static final int MAX_LOCK_BYTES = 1024 * 1024;
    private static final Pattern SAFE_VALUE = Pattern.compile("[A-Za-z0-9_.$:/-]+");
    private static final Pattern SAFE_COORDINATE_PART = Pattern.compile("[A-Za-z0-9_.-]+");
    private static final Pattern DIGEST = Pattern.compile("sha256:[0-9a-f]{64}");

    private final String kind;
    private final String camelVersion;
    private final String citrusVersion;
    private final String launcherClass;
    private final String jdkDigest;
    private final List<Root> dependencyRoots;
    private final List<Entry> entries;
    private final String contentDigest;

    private JvmPayloadLock(
                           String kind,
                           String camelVersion,
                           String citrusVersion,
                           String launcherClass,
                           String jdkDigest,
                           List<Root> dependencyRoots,
                           List<Entry> entries,
                           String contentDigest)
                                                 throws IOException {
        this.kind = requireSafe(kind, "payload kind");
        this.camelVersion = requireRelease(camelVersion, "Camel version");
        this.citrusVersion = citrusVersion == null ? null : requireRelease(citrusVersion, "Citrus version");
        this.launcherClass = requireSafe(launcherClass, "launcher class");
        this.jdkDigest = requireDigest(jdkDigest, "JDK digest");
        this.dependencyRoots = dependencyRoots == null
                ? List.of()
                : dependencyRoots.stream().sorted(Comparator.comparing(Root::coordinate)).toList();
        this.entries = entries == null
                ? List.of()
                : entries.stream().sorted(Comparator.comparing(Entry::path)).toList();
        validate();
        String expected = computeContentDigest();
        if (contentDigest != null && !expected.equals(contentDigest)) {
            throw new IOException("JVM payload lock content digest mismatch");
        }
        this.contentDigest = expected;
    }

    public static JvmPayloadLock create(
            JvmPayloadRequest request, String jdkDigest, List<Entry> entries)
            throws IOException {
        if (request == null || !request.kind().supported()) {
            throw new IOException("Only supported controller JVM payloads can be locked");
        }
        return new JvmPayloadLock(
                request.kind().id(), request.camelVersion(), request.citrusVersion(),
                request.launcherClass(), jdkDigest, roots(request), entries, null);
    }

    public static JvmPayloadLock parse(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_LOCK_BYTES) {
            throw new IOException("JVM payload lock has an unsafe size");
        }
        Map<String, String> values = new LinkedHashMap<>();
        String text = new String(bytes, StandardCharsets.UTF_8);
        for (String line : text.split("\\n", -1)) {
            if (line.isEmpty()) {
                continue;
            }
            int separator = line.indexOf('=');
            if (separator <= 0 || separator == line.length() - 1
                    || values.putIfAbsent(line.substring(0, separator), line.substring(separator + 1)) != null) {
                throw new IOException("JVM payload lock contains malformed or duplicate fields");
            }
        }
        int schema = integer(values.remove("schemaVersion"), "schemaVersion");
        if (schema != SCHEMA_VERSION) {
            throw new IOException("Unsupported JVM payload lock schema: " + schema);
        }
        String kind = required(values, "kind");
        String camelVersion = required(values, "camelVersion");
        String citrusVersion = nullable(required(values, "citrusVersion"));
        String launcherClass = required(values, "launcherClass");
        String jdkDigest = required(values, "jdkDigest");
        int rootCount = boundedCount(values.remove("root.count"), "root.count", MAX_ROOTS);
        List<Root> roots = new ArrayList<>(rootCount);
        for (int index = 0; index < rootCount; index++) {
            String prefix = "root." + formatted(index) + '.';
            String groupId = required(values, prefix + "groupId");
            String artifactId = required(values, prefix + "artifactId");
            String version = required(values, prefix + "version");
            int exclusionCount = boundedCount(
                    values.remove(prefix + "exclusion.count"), prefix + "exclusion.count", MAX_EXCLUSIONS);
            List<Exclusion> exclusions = new ArrayList<>(exclusionCount);
            for (int exclusion = 0; exclusion < exclusionCount; exclusion++) {
                String exclusionPrefix = prefix + "exclusion." + formatted(exclusion) + '.';
                exclusions.add(exclusion(
                        required(values, exclusionPrefix + "groupId"),
                        required(values, exclusionPrefix + "artifactId")));
            }
            roots.add(root(groupId, artifactId, version, exclusions));
        }
        int entryCount = boundedCount(values.remove("entry.count"), "entry.count", MAX_ENTRIES);
        List<Entry> entries = new ArrayList<>(entryCount);
        for (int index = 0; index < entryCount; index++) {
            String prefix = "entry." + formatted(index) + '.';
            entries.add(entry(
                    required(values, prefix + "kind"),
                    required(values, prefix + "identity"),
                    required(values, prefix + "path"),
                    longValue(values.remove(prefix + "size"), prefix + "size"),
                    required(values, prefix + "digest")));
        }
        String contentDigest = required(values, "contentDigest");
        if (!values.isEmpty()) {
            throw new IOException("JVM payload lock contains unknown fields: " + values.keySet());
        }
        return new JvmPayloadLock(
                kind, camelVersion, citrusVersion, launcherClass, jdkDigest, roots, entries, contentDigest);
    }

    public byte[] encode() {
        StringBuilder value = new StringBuilder(4096);
        field(value, "schemaVersion", Integer.toString(SCHEMA_VERSION));
        field(value, "kind", kind);
        field(value, "camelVersion", camelVersion);
        field(value, "citrusVersion", citrusVersion == null ? "-" : citrusVersion);
        field(value, "launcherClass", launcherClass);
        field(value, "jdkDigest", jdkDigest);
        field(value, "root.count", Integer.toString(dependencyRoots.size()));
        for (int index = 0; index < dependencyRoots.size(); index++) {
            Root root = dependencyRoots.get(index);
            String prefix = "root." + formatted(index) + '.';
            field(value, prefix + "groupId", root.groupId());
            field(value, prefix + "artifactId", root.artifactId());
            field(value, prefix + "version", root.version());
            field(value, prefix + "exclusion.count", Integer.toString(root.exclusions().size()));
            for (int exclusion = 0; exclusion < root.exclusions().size(); exclusion++) {
                Exclusion excluded = root.exclusions().get(exclusion);
                String exclusionPrefix = prefix + "exclusion." + formatted(exclusion) + '.';
                field(value, exclusionPrefix + "groupId", excluded.groupId());
                field(value, exclusionPrefix + "artifactId", excluded.artifactId());
            }
        }
        field(value, "entry.count", Integer.toString(entries.size()));
        for (int index = 0; index < entries.size(); index++) {
            Entry entry = entries.get(index);
            String prefix = "entry." + formatted(index) + '.';
            field(value, prefix + "kind", entry.kind());
            field(value, prefix + "identity", entry.identity());
            field(value, prefix + "path", entry.path());
            field(value, prefix + "size", Long.toString(entry.size()));
            field(value, prefix + "digest", entry.digest());
        }
        field(value, "contentDigest", contentDigest);
        return value.toString().getBytes(StandardCharsets.UTF_8);
    }

    public void requireRequest(JvmPayloadRequest request) throws IOException {
        if (request == null
                || !kind.equals(request.kind().id())
                || !camelVersion.equals(request.camelVersion())
                || !Objects.equals(citrusVersion, request.citrusVersion())
                || !launcherClass.equals(request.launcherClass())
                || !dependencyRoots.equals(roots(request))) {
            throw new IOException("Retained JVM payload does not match the controller request");
        }
    }

    public String kind() {
        return kind;
    }

    public String camelVersion() {
        return camelVersion;
    }

    public String citrusVersion() {
        return citrusVersion;
    }

    public String launcherClass() {
        return launcherClass;
    }

    public String jdkDigest() {
        return jdkDigest;
    }

    public List<String> roots() {
        return dependencyRoots.stream().map(Root::coordinate).toList();
    }

    public List<Root> dependencyRoots() {
        return dependencyRoots;
    }

    public List<Entry> entries() {
        return entries;
    }

    public String contentDigest() {
        return contentDigest;
    }

    private void validate() throws IOException {
        if (dependencyRoots.size() > MAX_ROOTS
                || dependencyRoots.isEmpty()
                || dependencyRoots.stream().map(Root::coordinate).distinct().count() != dependencyRoots.size()) {
            throw new IOException("JVM payload lock roots are missing, duplicated, or excessive");
        }
        if (entries.size() < 3 || entries.size() > MAX_ENTRIES) {
            throw new IOException("JVM payload lock entry count is outside controller bounds");
        }
        Set<String> paths = new HashSet<>();
        Map<String, String> gaVersions = new HashMap<>();
        int launcherCount = 0;
        boolean bootstrapFound = false;
        Set<String> resolvedRoots = new HashSet<>();
        for (Entry entry : entries) {
            if (!paths.add(entry.path())) {
                throw new IOException("JVM payload lock contains duplicate paths");
            }
            if ("launcher".equals(entry.kind())) {
                launcherCount++;
            }
            bootstrapFound |= "bootstrap".equals(entry.kind());
            if ("maven".equals(entry.kind())) {
                MavenIdentity identity = MavenIdentity.parse(entry.identity());
                String previous = gaVersions.putIfAbsent(identity.ga(), identity.version());
                if (previous != null && !previous.equals(identity.version())) {
                    throw new IOException("JVM payload contains multiple versions of " + identity.ga());
                }
                if ("org.apache.camel".equals(identity.groupId())
                        && !camelVersion.equals(identity.version())) {
                    throw new IOException("JVM payload Camel artifacts are not aligned to " + camelVersion);
                }
                if ("org.citrusframework".equals(identity.groupId()) && citrusVersion != null
                        && !citrusVersion.equals(identity.version())) {
                    throw new IOException("JVM payload Citrus artifacts are not aligned to " + citrusVersion);
                }
                resolvedRoots.add(identity.groupId() + ':' + identity.artifactId() + ':' + identity.version());
            }
        }
        if (launcherCount != 1 || !bootstrapFound) {
            throw new IOException("JVM payload lock lacks its controller bootstrap or launcher");
        }
        if (!resolvedRoots.containsAll(roots())) {
            throw new IOException("JVM payload lock omits a controller dependency root");
        }
    }

    private String computeContentDigest() {
        MessageDigest digest = sha256();
        update(digest, "camel-kit.ship.jvm-payload-lock.v2");
        update(digest, kind);
        update(digest, camelVersion);
        update(digest, citrusVersion == null ? "" : citrusVersion);
        update(digest, launcherClass);
        update(digest, jdkDigest);
        update(digest, Integer.toString(dependencyRoots.size()));
        for (Root root : dependencyRoots) {
            update(digest, root.groupId());
            update(digest, root.artifactId());
            update(digest, root.version());
            update(digest, Integer.toString(root.exclusions().size()));
            for (Exclusion exclusion : root.exclusions()) {
                update(digest, exclusion.groupId());
                update(digest, exclusion.artifactId());
            }
        }
        for (Entry entry : entries) {
            update(digest, entry.kind());
            update(digest, entry.identity());
            update(digest, entry.path());
            update(digest, Long.toString(entry.size()));
            update(digest, entry.digest());
        }
        return "sha256:" + HexFormat.of().formatHex(digest.digest());
    }

    private static List<Root> roots(JvmPayloadRequest request) {
        return request.dependencyRoots().stream()
                .map(root -> new Root(
                        root.groupId(), root.artifactId(), root.version(),
                        root.exclusions().stream()
                                .map(exclusion -> new Exclusion(exclusion.groupId(), exclusion.artifactId()))
                                .toList()))
                .sorted(Comparator.comparing(Root::coordinate))
                .toList();
    }

    private static Root root(String groupId, String artifactId, String version, List<Exclusion> exclusions)
            throws IOException {
        try {
            return new Root(groupId, artifactId, version, exclusions);
        } catch (IllegalArgumentException e) {
            throw new IOException("JVM payload lock contains an unsafe dependency root", e);
        }
    }

    private static Exclusion exclusion(String groupId, String artifactId) throws IOException {
        try {
            return new Exclusion(groupId, artifactId);
        } catch (IllegalArgumentException e) {
            throw new IOException("JVM payload lock contains an unsafe dependency exclusion", e);
        }
    }

    private static Entry entry(String kind, String identity, String path, long size, String digest)
            throws IOException {
        try {
            return new Entry(kind, identity, path, size, digest);
        } catch (IllegalArgumentException e) {
            throw new IOException("JVM payload lock contains an unsafe entry", e);
        }
    }

    private static void field(StringBuilder target, String key, String value) {
        target.append(key).append('=').append(value).append('\n');
    }

    private static String required(Map<String, String> values, String key) throws IOException {
        String value = values.remove(key);
        if (value == null || value.isBlank()) {
            throw new IOException("JVM payload lock is missing " + key);
        }
        return value;
    }

    private static String nullable(String value) {
        return "-".equals(value) ? null : value;
    }

    private static int boundedCount(String value, String key, int maximum) throws IOException {
        int count = integer(value, key);
        if (count < 0 || count > maximum) {
            throw new IOException("JVM payload lock " + key + " is outside controller bounds");
        }
        return count;
    }

    private static int integer(String value, String key) throws IOException {
        try {
            return Integer.parseInt(value);
        } catch (RuntimeException e) {
            throw new IOException("JVM payload lock has an invalid " + key, e);
        }
    }

    private static long longValue(String value, String key) throws IOException {
        try {
            long result = Long.parseLong(value);
            if (result <= 0) {
                throw new NumberFormatException("non-positive");
            }
            return result;
        } catch (RuntimeException e) {
            throw new IOException("JVM payload lock has an invalid " + key, e);
        }
    }

    private static String formatted(int index) {
        return String.format(Locale.ROOT, "%03d", index);
    }

    private static String requireSafe(String value, String label) throws IOException {
        if (value == null || !SAFE_VALUE.matcher(value).matches()) {
            throw new IOException("JVM payload lock has an unsafe " + label);
        }
        return value;
    }

    private static String requireRelease(String value, String label) throws IOException {
        String release = requireSafe(value, label);
        String normalized = release.toUpperCase(Locale.ROOT);
        if (normalized.contains("SNAPSHOT") || "LATEST".equals(normalized) || "RELEASE".equals(normalized)) {
            throw new IOException("JVM payload lock has a mutable " + label);
        }
        return release;
    }

    private static String requireDigest(String value, String label) throws IOException {
        if (value == null || !DIGEST.matcher(value).matches()) {
            throw new IOException("JVM payload lock has an invalid " + label);
        }
        return value;
    }

    private static String coordinatePart(String value, String label) {
        if (value == null || !SAFE_COORDINATE_PART.matcher(value).matches()) {
            throw new IllegalArgumentException("JVM payload lock has an unsafe " + label);
        }
        return value;
    }

    private static String coordinatePattern(String value, String label) {
        return "*".equals(value) ? value : coordinatePart(value, label);
    }

    private static String release(String value, String label) {
        try {
            return requireRelease(value, label);
        } catch (IOException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    public record Root(String groupId, String artifactId, String version, List<Exclusion> exclusions) {

        public Root {
            groupId = coordinatePart(groupId, "root group ID");
            artifactId = coordinatePart(artifactId, "root artifact ID");
            version = release(version, "root version");
            exclusions = Objects.requireNonNull(exclusions, "exclusions").stream()
                    .sorted(Comparator.comparing(Exclusion::groupId).thenComparing(Exclusion::artifactId))
                    .toList();
            if (exclusions.size() > MAX_EXCLUSIONS || Set.copyOf(exclusions).size() != exclusions.size()) {
                throw new IllegalArgumentException("JVM payload lock exclusions are duplicated or excessive");
            }
        }

        public String coordinate() {
            return groupId + ':' + artifactId + ':' + version;
        }
    }

    public record Exclusion(String groupId, String artifactId) {

        public Exclusion {
            groupId = coordinatePattern(groupId, "exclusion group ID");
            artifactId = coordinatePattern(artifactId, "exclusion artifact ID");
        }
    }

    public record Entry(String kind, String identity, String path, long size, String digest) {

        public Entry {
            try {
                kind = requireSafe(kind, "entry kind");
                identity = requireSafe(identity, "entry identity");
                path = requireSafe(path, "entry path");
                digest = requireDigest(digest, "entry digest");
            } catch (IOException e) {
                throw new IllegalArgumentException(e.getMessage(), e);
            }
            boolean expectedPath = "bootstrap".equals(kind) && path.endsWith(".class")
                    || Set.of("launcher", "maven").contains(kind)
                            && path.startsWith("lib/") && path.endsWith(".jar");
            if (!Set.of("bootstrap", "launcher", "maven").contains(kind)
                    || !expectedPath
                    || path.startsWith("/") || path.contains("..") || size <= 0) {
                throw new IllegalArgumentException("JVM payload lock entry is unsafe");
            }
        }
    }

    private record MavenIdentity(String groupId, String artifactId, String version) {

        private static MavenIdentity parse(String value) throws IOException {
            String[] parts = value.split(":", -1);
            if (parts.length < 3 || parts.length > 5) {
                throw new IOException("JVM payload contains an invalid Maven identity: " + value);
            }
            String groupId = parts[0];
            String artifactId = parts[1];
            String version = parts[parts.length - 1];
            String normalized = version.toUpperCase(Locale.ROOT);
            if (!SAFE_VALUE.matcher(groupId).matches() || !SAFE_VALUE.matcher(artifactId).matches()
                    || !SAFE_VALUE.matcher(version).matches() || normalized.contains("SNAPSHOT")
                    || "LATEST".equals(normalized) || "RELEASE".equals(normalized)) {
                throw new IOException("JVM payload Maven identity is not an exact release: " + value);
            }
            return new MavenIdentity(groupId, artifactId, version);
        }

        private String ga() {
            return groupId + ':' + artifactId;
        }
    }
}
