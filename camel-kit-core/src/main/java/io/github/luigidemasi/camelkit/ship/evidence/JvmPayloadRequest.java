package io.github.luigidemasi.camelkit.ship.evidence;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

import io.github.luigidemasi.camelkit.ship.catalog.CatalogUsageRecord.RuntimeDependency;
import io.github.luigidemasi.camelkit.ship.evidence.launcher.ShipCamelMainBootstrap;
import io.github.luigidemasi.camelkit.ship.evidence.launcher.ShipCamelYamlValidateMain;
import io.github.luigidemasi.camelkit.ship.evidence.launcher.ShipCitrusYamlMain;

/** Exact controller-owned dependency roots and launcher selected for a JVM evidence command. */
public record JvmPayloadRequest(
        Kind kind,
        String camelVersion,
        String citrusVersion,
        String launcherClass,
        List<DependencyRoot> dependencyRoots) {

    private static final Pattern SAFE_VERSION = Pattern.compile("[A-Za-z0-9_.-]+");
    private static final Pattern SAFE_COORDINATE_PART = Pattern.compile("[A-Za-z0-9_.-]+");
    private static final DependencyExclusion ALL_CAMEL = new DependencyExclusion("org.apache.camel", "*");
    private static final Policy POLICY = Policy.load();

    public JvmPayloadRequest {
        dependencyRoots = dependencyRoots == null
                ? List.of()
                : dependencyRoots.stream()
                        .sorted(Comparator.comparing(DependencyRoot::coordinate))
                        .toList();
        if (kind == null || !exactRelease(camelVersion)) {
            throw new IllegalArgumentException("JVM payload requires an exact release Camel version");
        }
        if (citrusVersion != null && !exactRelease(citrusVersion)) {
            throw new IllegalArgumentException("JVM payload requires an exact release Citrus version");
        }
        if (kind.supported()) {
            if (launcherClass == null || !launcherClass.matches("[A-Za-z_$][A-Za-z0-9_$.]*")
                    || dependencyRoots.isEmpty()) {
                throw new IllegalArgumentException("A supported JVM payload requires a launcher and roots");
            }
        } else if (launcherClass != null) {
            throw new IllegalArgumentException("An unsupported JVM payload cannot name a launcher");
        }
        if (dependencyRoots.size() > 64
                || dependencyRoots.stream().map(DependencyRoot::coordinate).distinct().count()
                   != dependencyRoots.size()) {
            throw new IllegalArgumentException("JVM payload roots must be unique exact GAV coordinates");
        }
        requireCanonicalPolicy(kind, camelVersion, citrusVersion, launcherClass, dependencyRoots);
    }

    public static JvmPayloadRequest yamlValidator(String camelVersion) {
        return new JvmPayloadRequest(
                Kind.CAMEL_YAML_VALIDATE,
                camelVersion,
                null,
                ShipCamelYamlValidateMain.class.getName(),
                yamlValidatorRoots(camelVersion));
    }

    public static JvmPayloadRequest camelMain(String camelVersion) {
        return camelMain(camelVersion, List.of());
    }

    public static JvmPayloadRequest camelMain(
            String camelVersion, List<RuntimeDependency> runtimeDependencies) {
        return new JvmPayloadRequest(
                Kind.CAMEL_MAIN_START,
                camelVersion,
                null,
                ShipCamelMainBootstrap.class.getName(),
                mergeRuntimeRoots(camelMainRoots(camelVersion), runtimeDependencies));
    }

    public static JvmPayloadRequest citrus(
            String camelVersion, String citrusVersion, List<String> citrusDependencies) {
        return citrus(camelVersion, citrusVersion, citrusDependencies, List.of());
    }

    public static JvmPayloadRequest citrus(
            String camelVersion,
            String citrusVersion,
            List<String> citrusDependencies,
            List<RuntimeDependency> runtimeDependencies) {
        java.util.TreeSet<String> declared = new java.util.TreeSet<>(citrusDependencies);
        Set<String> required = Set.of(
                "org.citrusframework:citrus-camel:" + citrusVersion,
                "org.citrusframework:citrus-junit-jupiter:" + citrusVersion,
                "org.citrusframework:citrus-yaml:" + citrusVersion);
        if (!declared.equals(required)) {
            throw new IllegalArgumentException("Direct Citrus requires the exact approved dependency declaration");
        }
        return new JvmPayloadRequest(
                Kind.CITRUS_YAML,
                camelVersion,
                citrusVersion,
                ShipCitrusYamlMain.class.getName(),
                mergeRuntimeRoots(citrusRoots(camelVersion, citrusVersion), runtimeDependencies));
    }

    public static JvmPayloadRequest runtimeBuild(String kind, String camelVersion) {
        return new JvmPayloadRequest(
                Kind.valueOf(kind.toUpperCase(Locale.ROOT).replace('-', '_') + "_BUILD"),
                camelVersion,
                null,
                null,
                List.of());
    }

    /** Canonical exact coordinates retained in the payload lock. */
    public List<String> roots() {
        return dependencyRoots.stream().map(DependencyRoot::coordinate).toList();
    }

    /** Stable digest included in the sandbox policy identity. */
    public String digest() {
        MessageDigest digest = sha256();
        update(digest, "camel-kit.ship.jvm-payload-request.v2");
        update(digest, kind.id());
        update(digest, camelVersion);
        update(digest, citrusVersion == null ? "" : citrusVersion);
        update(digest, launcherClass == null ? "" : launcherClass);
        update(digest, Integer.toString(dependencyRoots.size()));
        for (DependencyRoot root : dependencyRoots) {
            update(digest, root.groupId());
            update(digest, root.artifactId());
            update(digest, root.version());
            update(digest, Integer.toString(root.exclusions().size()));
            for (DependencyExclusion exclusion : root.exclusions()) {
                update(digest, exclusion.groupId());
                update(digest, exclusion.artifactId());
            }
        }
        return "sha256:" + HexFormat.of().formatHex(digest.digest());
    }

    private static List<DependencyRoot> yamlValidatorRoots(String camelVersion) {
        List<DependencyRoot> roots = new ArrayList<>(launcherYamlRoots());
        roots.add(root(
                "com.networknt", "json-schema-validator", POLICY.jsonSchemaValidatorVersion(camelVersion)));
        roots.add(root("com.ethlo.time", "itu", POLICY.networkntItuVersion()));
        roots.add(root("org.slf4j", "slf4j-api", POLICY.networkntSlf4jVersion()));
        roots.add(root("org.apache.camel", "camel-yaml-dsl-validator", camelVersion));
        roots.add(root("org.apache.camel", "camel-yaml-dsl", camelVersion));
        return roots.stream().sorted(Comparator.comparing(DependencyRoot::coordinate)).toList();
    }

    private static List<DependencyRoot> camelMainRoots(String camelVersion) {
        List<DependencyRoot> roots = new ArrayList<>(launcherYamlRoots());
        roots.addAll(List.of(
                root("org.apache.camel", "camel-direct", camelVersion),
                root("org.apache.camel", "camel-main", camelVersion),
                root("org.apache.camel", "camel-stub", camelVersion),
                root("org.apache.camel", "camel-yaml-dsl", camelVersion)));
        return roots.stream().sorted(Comparator.comparing(DependencyRoot::coordinate)).toList();
    }

    private static List<DependencyRoot> launcherYamlRoots() {
        return List.of(
                root("com.fasterxml.jackson.core", "jackson-annotations", POLICY.jacksonYamlVersion()),
                root("com.fasterxml.jackson.core", "jackson-core", POLICY.jacksonYamlVersion()),
                root("com.fasterxml.jackson.core", "jackson-databind", POLICY.jacksonYamlVersion()),
                root("com.fasterxml.jackson.dataformat", "jackson-dataformat-yaml", POLICY.jacksonYamlVersion()),
                root("org.yaml", "snakeyaml", POLICY.snakeyamlVersion()));
    }

    private static List<DependencyRoot> citrusRoots(String camelVersion, String citrusVersion) {
        List<DependencyRoot> roots = new ArrayList<>(camelMainRoots(camelVersion));
        for (String artifact : List.of("camel-catalog", "camel-core", "camel-spring", "camel-spring-xml")) {
            roots.add(root("org.apache.camel", artifact, camelVersion));
        }
        for (String artifact : List.of("citrus-camel", "citrus-junit-jupiter", "citrus-yaml")) {
            roots.add(new DependencyRoot(
                    "org.citrusframework", artifact, citrusVersion, List.of(ALL_CAMEL)));
        }
        return roots.stream().sorted(Comparator.comparing(DependencyRoot::coordinate)).toList();
    }

    private static DependencyRoot root(String groupId, String artifactId, String version) {
        return new DependencyRoot(groupId, artifactId, version, List.of());
    }

    private static void requireCanonicalPolicy(
            Kind kind,
            String camelVersion,
            String citrusVersion,
            String launcherClass,
            List<DependencyRoot> roots) {
        if (!kind.supported()) {
            if (citrusVersion != null || !roots.isEmpty()) {
                throw new IllegalArgumentException("An unsupported JVM payload cannot name dependencies");
            }
            return;
        }
        POLICY.requireYamlValidator(camelVersion);
        String expectedLauncher;
        List<DependencyRoot> expectedRoots;
        switch (kind) {
            case CAMEL_YAML_VALIDATE -> {
                if (citrusVersion != null) {
                    throw new IllegalArgumentException("Camel YAML validation cannot name a Citrus version");
                }
                expectedLauncher = ShipCamelYamlValidateMain.class.getName();
                expectedRoots = yamlValidatorRoots(camelVersion);
            }
            case CAMEL_MAIN_START -> {
                if (citrusVersion != null) {
                    throw new IllegalArgumentException("Camel Main startup cannot name a Citrus version");
                }
                expectedLauncher = ShipCamelMainBootstrap.class.getName();
                expectedRoots = camelMainRoots(camelVersion);
            }
            case CITRUS_YAML -> {
                POLICY.requireCitrus(camelVersion, citrusVersion);
                expectedLauncher = ShipCitrusYamlMain.class.getName();
                expectedRoots = citrusRoots(camelVersion, citrusVersion);
            }
            default -> throw new IllegalArgumentException("Unsupported JVM payload kind");
        }
        boolean allowRuntimeRoots = kind == Kind.CAMEL_MAIN_START || kind == Kind.CITRUS_YAML;
        if (!expectedLauncher.equals(launcherClass)
                || !canonicalRoots(expectedRoots, roots, allowRuntimeRoots)) {
            throw new IllegalArgumentException("JVM payload dependencies differ from the packaged controller policy");
        }
    }

    private static boolean canonicalRoots(
            List<DependencyRoot> base, List<DependencyRoot> actual, boolean allowRuntimeRoots) {
        Map<String, DependencyRoot> required = new java.util.HashMap<>();
        base.forEach(root -> required.put(root.groupId() + ':' + root.artifactId(), root));
        Set<String> seen = new HashSet<>();
        for (DependencyRoot root : actual) {
            String ga = root.groupId() + ':' + root.artifactId();
            if (!seen.add(ga)) {
                return false;
            }
            DependencyRoot expected = required.get(ga);
            if (expected != null) {
                if (!expected.equals(root)) {
                    return false;
                }
                continue;
            }
            if (!allowRuntimeRoots || !(root.groupId().equals("org.apache.camel")
                    || root.groupId().equals("org.apache.camel.springboot")
                    || root.groupId().equals("org.apache.camel.quarkus"))
                    || !root.artifactId().startsWith("camel-") || !root.exclusions().isEmpty()) {
                return false;
            }
        }
        Set<String> actualGa = actual.stream()
                .map(root -> root.groupId() + ':' + root.artifactId()).collect(java.util.stream.Collectors.toSet());
        return actualGa.containsAll(required.keySet());
    }

    private static List<DependencyRoot> mergeRuntimeRoots(
            List<DependencyRoot> base, List<RuntimeDependency> runtimeDependencies) {
        Objects.requireNonNull(runtimeDependencies, "runtimeDependencies must not be null");
        Map<String, DependencyRoot> roots = new java.util.HashMap<>();
        for (DependencyRoot root : base) {
            roots.put(root.groupId() + ':' + root.artifactId(), root);
        }
        for (RuntimeDependency dependency : runtimeDependencies) {
            Objects.requireNonNull(dependency, "runtimeDependencies must not contain null");
            DependencyRoot root = root(dependency.groupId(), dependency.artifactId(), dependency.version());
            String ga = root.groupId() + ':' + root.artifactId();
            DependencyRoot previous = roots.putIfAbsent(ga, root);
            if (previous != null && !previous.equals(root)) {
                throw new IllegalArgumentException("Catalog runtime root conflicts with controller payload: " + ga);
            }
        }
        return roots.values().stream().sorted(Comparator.comparing(DependencyRoot::coordinate)).toList();
    }

    private static boolean exactRelease(String value) {
        if (value == null || !SAFE_VERSION.matcher(value).matches()) {
            return false;
        }
        String normalized = value.toUpperCase(Locale.ROOT);
        return !normalized.contains("SNAPSHOT")
                && !"LATEST".equals(normalized)
                && !"RELEASE".equals(normalized);
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

    public record DependencyRoot(
            String groupId,
            String artifactId,
            String version,
            List<DependencyExclusion> exclusions) {

        public DependencyRoot {
            requireCoordinatePart(groupId, "group ID");
            requireCoordinatePart(artifactId, "artifact ID");
            if (!exactRelease(version)) {
                throw new IllegalArgumentException("JVM payload roots require exact release versions");
            }
            exclusions = Objects.requireNonNull(exclusions, "exclusions").stream()
                    .sorted(Comparator.comparing(DependencyExclusion::groupId)
                            .thenComparing(DependencyExclusion::artifactId))
                    .toList();
            if (exclusions.size() > 16 || Set.copyOf(exclusions).size() != exclusions.size()) {
                throw new IllegalArgumentException("JVM payload root exclusions are duplicated or excessive");
            }
        }

        public String coordinate() {
            return groupId + ':' + artifactId + ':' + version;
        }
    }

    public record DependencyExclusion(String groupId, String artifactId) {

        public DependencyExclusion {
            requireCoordinatePattern(groupId, "exclusion group ID");
            requireCoordinatePattern(artifactId, "exclusion artifact ID");
        }
    }

    private static void requireCoordinatePart(String value, String label) {
        if (value == null || !SAFE_COORDINATE_PART.matcher(value).matches()) {
            throw new IllegalArgumentException("JVM payload has an unsafe " + label);
        }
    }

    private static void requireCoordinatePattern(String value, String label) {
        if (!"*".equals(value)) {
            requireCoordinatePart(value, label);
        }
    }

    private record Policy(
            List<String> yamlValidatorVersions,
            Properties properties,
            String jacksonYamlVersion,
            String snakeyamlVersion) {

        private static final String VALIDATOR_VERSIONS = "ship.evidence.camel-yaml-validator.supported";
        private static final String JACKSON_VERSION = "ship.evidence.jackson-yaml.version";
        private static final String SNAKEYAML_VERSION = "ship.evidence.snakeyaml.version";
        private static final String NETWORKNT_ITU_VERSION = "ship.evidence.camel-yaml-validator.networknt-itu.version";
        private static final String NETWORKNT_SLF4J_VERSION
                = "ship.evidence.camel-yaml-validator.networknt-slf4j.version";

        private static Policy load() {
            Properties properties = new Properties();
            try (InputStream input = JvmPayloadRequest.class.getClassLoader()
                    .getResourceAsStream("distribution.properties")) {
                if (input == null) {
                    throw new IllegalStateException("Packaged distribution.properties is unavailable");
                }
                properties.load(input);
            } catch (IOException e) {
                throw new IllegalStateException("Could not read the packaged Ship payload policy", e);
            }
            List<String> versions = versionList(properties, VALIDATOR_VERSIONS, true);
            versions.forEach(version -> requiredRelease(properties, jsonSchemaValidatorKey(version)));
            String defaultCamel = requiredRelease(properties, "camel.main.version");
            if (!versions.contains(defaultCamel)) {
                throw new IllegalStateException("Ship validator policy excludes the distribution's default Camel");
            }
            String defaultCitrus = requiredRelease(properties, "citrus.version");
            List<String> citrusCamel = versionList(properties, citrusKey(defaultCitrus), true);
            if (!citrusCamel.contains(defaultCamel)) {
                throw new IllegalStateException("Ship Citrus policy excludes the distribution's default Camel");
            }
            return new Policy(
                    versions,
                    properties,
                    requiredRelease(properties, JACKSON_VERSION),
                    requiredRelease(properties, SNAKEYAML_VERSION));
        }

        private void requireYamlValidator(String camelVersion) {
            if (!yamlValidatorVersions.contains(camelVersion)) {
                throw new IllegalArgumentException(
                        "Camel " + camelVersion + " has no certified direct YAML validator payload; supported: "
                                                   + yamlValidatorVersions);
            }
        }

        private String jsonSchemaValidatorVersion(String camelVersion) {
            requireYamlValidator(camelVersion);
            return requiredRelease(properties, jsonSchemaValidatorKey(camelVersion));
        }

        private String networkntItuVersion() {
            return requiredRelease(properties, NETWORKNT_ITU_VERSION);
        }

        private String networkntSlf4jVersion() {
            return requiredRelease(properties, NETWORKNT_SLF4J_VERSION);
        }

        private void requireCitrus(String camelVersion, String citrusVersion) {
            if (citrusVersion == null
                    || !versionList(properties, citrusKey(citrusVersion), false).contains(camelVersion)) {
                throw new IllegalArgumentException(
                        "Citrus " + citrusVersion + " with Camel " + camelVersion
                                                   + " has no certified direct evidence payload");
            }
        }

        private static String citrusKey(String citrusVersion) {
            return "ship.evidence.citrus." + citrusVersion + ".camel.supported";
        }

        private static String jsonSchemaValidatorKey(String camelVersion) {
            return "ship.evidence.camel-yaml-validator." + camelVersion
                   + ".json-schema-validator.version";
        }

        private static List<String> versionList(Properties properties, String key, boolean required) {
            String value = properties.getProperty(key);
            if (value == null || value.isBlank()) {
                if (!required) {
                    return List.of();
                }
                throw new IllegalStateException("Packaged Ship payload policy is missing " + key);
            }
            Set<String> unique = new HashSet<>();
            for (String version : value.split(",", -1)) {
                String normalized = version.trim();
                if (!exactRelease(normalized) || !unique.add(normalized)) {
                    throw new IllegalStateException("Packaged Ship payload policy has an invalid " + key);
                }
            }
            return unique.stream().sorted().toList();
        }

        private static String requiredRelease(Properties properties, String key) {
            String value = properties.getProperty(key);
            if (!exactRelease(value)) {
                throw new IllegalStateException("Packaged Ship payload policy has an invalid " + key);
            }
            return value;
        }
    }

    public enum Kind {
        CAMEL_YAML_VALIDATE("camel-yaml-validate", true),
        CAMEL_MAIN_START("camel-main-start", true),
        CITRUS_YAML("citrus-yaml", true),
        SPRING_BOOT_BUILD("spring-boot-build", false),
        QUARKUS_BUILD("quarkus-build", false);

        private final String id;
        private final boolean supported;

        Kind(String id, boolean supported) {
            this.id = id;
            this.supported = supported;
        }

        public String id() {
            return id;
        }

        public boolean supported() {
            return supported;
        }
    }
}
