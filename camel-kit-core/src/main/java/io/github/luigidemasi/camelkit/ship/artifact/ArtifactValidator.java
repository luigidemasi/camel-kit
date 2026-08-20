package io.github.luigidemasi.camelkit.ship.artifact;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import io.github.luigidemasi.camelkit.ship.ShipArtifactLimits;
import io.github.luigidemasi.camelkit.ship.artifact.ArtifactFinding.Severity;
import io.github.luigidemasi.camelkit.ship.artifact.ArtifactManifest.DeclaredArtifact;
import io.github.luigidemasi.camelkit.ship.artifact.ArtifactManifest.RouteArtifact;
import io.github.luigidemasi.camelkit.ship.artifact.ArtifactManifest.TestArtifact;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogEvidenceSet;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogEvidenceSet.SubjectEvidence;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogSubject.Kind;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogUsageRecord.RuntimeDependency;
import io.github.luigidemasi.camelkit.ship.evidence.launcher.ShipCamelMainBootstrap;
import io.github.luigidemasi.camelkit.ship.evidence.launcher.ShipCitrusYamlMain;
import io.github.luigidemasi.camelkit.ship.security.ProjectEvidenceFiles;

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

/** Deterministic filesystem checks used before execution output can be accepted. */
public final class ArtifactValidator {

    private static final long MAX_POM_INSPECTION_BYTES = 2 * 1024 * 1024;
    private static final long MAX_CONFIG_INSPECTION_BYTES = 64 * 1024;
    private static final String MAVEN_NAMESPACE = "http://maven.apache.org/POM/4.0.0";
    private static final Pattern MAVEN_PROPERTY = Pattern.compile("\\$\\{([^}]+)}");
    private static final Pattern SAFE_MAVEN_TOKEN = Pattern.compile("[A-Za-z0-9_.-]+");
    private static final Pattern ROUTE_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}");
    private static final Set<String> APPROVED_BUILD_PLUGINS = Set.of(
            "org.springframework.boot:spring-boot-maven-plugin",
            "io.quarkus:quarkus-maven-plugin");
    private static final Set<String> TEST_SKIP_CONTROLS = Set.of(
            "skip", "skiptests", "skipits", "skipit", "surefire.skip", "failsafe.skip",
            "maven.test.skip", "maven.test.skip.exec", "maven.test.failure.ignore",
            "testfailureignore", "quarkus.test.skip");
    private static final ObjectReader YAML_READER = new com.fasterxml.jackson.databind.ObjectMapper(
            YAMLFactory.builder()
                    .streamReadConstraints(StreamReadConstraints.builder()
                            .maxNestingDepth(100)
                            .maxStringLength(ShipArtifactLimits.MAX_ROUTE_YAML_BYTES)
                            .build())
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .build())
            .reader()
            .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private ArtifactValidator() {
    }

    public static ArtifactValidationResult validate(Path projectRoot, ArtifactManifest manifest) {
        return validate(projectRoot, manifest, null);
    }

    /** Validates both filesystem artifacts and their exact approved requirements policy. */
    public static ArtifactValidationResult validate(
            Path projectRoot, ArtifactManifest manifest, ArtifactPolicy policy) {
        Path root = projectRoot.toAbsolutePath().normalize();
        List<ArtifactFinding> findings = new ArrayList<>();
        if (manifest == null) {
            return new ArtifactValidationResult(
                    List.of(error("manifest-missing", null,
                            "The execution result did not include an artifact manifest")));
        }
        if (manifest.schemaVersion() != ArtifactManifest.SCHEMA_VERSION) {
            findings.add(error("manifest-schema", null,
                    "Unsupported artifact-manifest schema version: " + manifest.schemaVersion()));
        }
        requireText("runtime", manifest.runtime(), findings);
        requireText("camel-version", manifest.camelVersion(), findings);
        requireText("route-dsl", manifest.routeDsl(), findings);
        requireText("expression-language", manifest.expressionLanguage(), findings);
        for (String violation : CitrusDependencyPolicy.violations(
                manifest.citrusVersion(), manifest.citrusDependencies())) {
            findings.add(error("citrus-dependencies", null, violation));
        }
        if (manifest.javaPolicy() == null) {
            findings.add(error("java-policy-missing", null, "The Java policy is required"));
        }
        if (manifest.routes().isEmpty()) {
            findings.add(error("routes-empty", null, "At least one route must be declared"));
        }

        Set<String> declaredPaths = new LinkedHashSet<>();
        Set<String> routeIds = new LinkedHashSet<>();
        Map<String, String> routePaths = new HashMap<>();
        for (RouteArtifact route : manifest.routes()) {
            validateRoute(root, route, routeIds, declaredPaths, findings);
            if (route != null && !isBlank(route.routeId()) && !isBlank(route.path())) {
                routePaths.putIfAbsent(route.routeId(), route.path());
            }
        }
        validateTests(root, manifest, routeIds, routePaths, declaredPaths, findings);
        validateCitrusConfiguration(root, manifest, findings);
        for (DeclaredArtifact artifact : manifest.artifacts()) {
            validateDeclaredArtifact(root, artifact, declaredPaths, findings);
        }

        findWronglySuffixedRoutes(root, Set.copyOf(routePaths.values()), findings);
        validateRuntimeJavaPolicy(manifest, findings);
        validateJavaPolicy(root, manifest, findings);
        validateRuntimeConsistency(root, manifest, policy, findings);
        if ("main".equals(normalizeRuntime(manifest.runtime()))) {
            try {
                ShipCamelMainBootstrap.validateLaunchSurface(
                        root,
                        manifest.routes().stream()
                                .filter(Objects::nonNull)
                                .map(route -> Path.of(route.path()))
                                .toList());
            } catch (IOException | RuntimeException e) {
                findings.add(error("main-launch-surface", null,
                        "Camel Main launch surface is not controller-bounded: " + safeMessage(e)));
            }
        }
        if (policy != null) {
            validateApprovedPolicy(manifest, policy, findings);
        }
        return new ArtifactValidationResult(findings);
    }

    /** Validate and retain the exact candidate runtime dependency roots used for controller execution. */
    public static CatalogDependencyBinding bindCatalogDependencies(
            Path projectRoot, ArtifactPolicy policy, CatalogEvidenceSet usageEvidence) {
        Path root = projectRoot.toAbsolutePath().normalize();
        List<ArtifactFinding> findings = new ArrayList<>();
        List<SubjectEvidence> runtimeSubjects = usageEvidence.subjects().stream()
                // EIP resources describe YAML model grammar; they are not route-selectable runtime artifacts.
                .filter(subject -> subject.subject().kind() != Kind.EIP)
                .toList();
        Path pom = root.resolve("pom.xml");
        if (Files.isSymbolicLink(pom) || !Files.isRegularFile(pom, LinkOption.NOFOLLOW_LINKS)) {
            findings.add(error("catalog-component-pom-missing", "pom.xml",
                    "Catalog-derived runtime usage requires a regular Maven POM"));
            return new CatalogDependencyBinding(new ArtifactValidationResult(findings), null, List.of());
        }
        try {
            if (Files.size(pom) > MAX_POM_INSPECTION_BYTES) {
                throw new IOException("Maven POM is too large for deterministic inspection");
            }
            MavenModel model = parseMavenModel(pom, true);
            List<RuntimeDependency> dependencies = validateExactMainDependencies(
                    model, policy, runtimeSubjects, findings);
            return new CatalogDependencyBinding(
                    new ArtifactValidationResult(findings), model.sourceDigest(), dependencies);
        } catch (ExactMainPomPolicyException e) {
            findings.add(error("catalog-runtime-pom-policy", "pom.xml", e.getMessage()));
            return new CatalogDependencyBinding(new ArtifactValidationResult(findings), null, List.of());
        } catch (IOException | RuntimeException e) {
            findings.add(error("catalog-component-pom-invalid", "pom.xml",
                    "Could not verify catalog-derived runtime dependencies: " + safeMessage(e)));
            return new CatalogDependencyBinding(new ArtifactValidationResult(findings), null, List.of());
        }
    }

    private static List<RuntimeDependency> validateExactMainDependencies(
            MavenModel model,
            ArtifactPolicy policy,
            List<SubjectEvidence> runtimeSubjects,
            List<ArtifactFinding> findings) {
        Map<String, RuntimeDependency> expected = new HashMap<>();
        putExpected(expected, new RuntimeDependency(
                "org.apache.camel", "camel-main", policy.camelVersion(), "compile"), findings);
        putExpected(expected, new RuntimeDependency(
                "org.apache.camel", "camel-yaml-dsl", policy.camelVersion(), "compile"), findings);
        Map<String, List<SubjectEvidence>> subjectsByGa = new HashMap<>();
        for (SubjectEvidence subject : runtimeSubjects) {
            RuntimeDependency dependency = new RuntimeDependency(
                    subject.groupId(), subject.artifactId(), subject.artifactVersion(), "compile");
            putExpected(expected, dependency, findings);
            subjectsByGa.computeIfAbsent(subject.groupId() + ':' + subject.artifactId(), ignored -> new ArrayList<>())
                    .add(subject);
        }

        Map<String, RuntimeDependency> actual = new HashMap<>();
        for (MavenCoordinate coordinate : model.dependencies()) {
            String ga = coordinate.groupId() + ':' + coordinate.artifactId();
            String scope = coordinate.scope() == null ? "compile" : coordinate.scope();
            RuntimeDependency dependency = new RuntimeDependency(
                    coordinate.groupId(), coordinate.artifactId(), coordinate.version(), scope);
            if (actual.putIfAbsent(ga, dependency) != null) {
                findings.add(error("catalog-runtime-dependency-duplicate", "pom.xml",
                        "Candidate POM repeats runtime dependency " + ga));
                continue;
            }
            RuntimeDependency allowed = expected.get(ga);
            if (allowed == null) {
                findings.add(error("catalog-runtime-dependency-extra", "pom.xml",
                        "Candidate POM contains an unverified runtime dependency: " + dependency.coordinate()));
            } else if (!allowed.version().equals(dependency.version())) {
                findings.add(error("catalog-component-dependency-version", "pom.xml",
                        "Runtime dependency " + ga + " version " + dependency.version()
                                                                                      + " differs from catalog-bound version "
                                                                                      + allowed.version()));
            }
        }
        for (Map.Entry<String, RuntimeDependency> entry : expected.entrySet()) {
            if (actual.containsKey(entry.getKey())) {
                continue;
            }
            List<SubjectEvidence> missingSubjects = subjectsByGa.get(entry.getKey());
            String code = missingSubjects != null && missingSubjects.stream()
                    .anyMatch(subject -> subject.subject().kind() == Kind.COMPONENT)
                            ? "catalog-component-dependency-missing"
                            : "catalog-runtime-dependency-missing";
            String label = missingSubjects == null
                    ? "required controller base" : "catalog-derived route usage";
            findings.add(error(code, "pom.xml",
                    "Candidate POM omits " + label + " dependency " + entry.getValue().coordinate()));
        }
        return actual.values().stream()
                .sorted(java.util.Comparator.comparing(RuntimeDependency::coordinate)
                        .thenComparing(RuntimeDependency::scope))
                .toList();
    }

    private static void putExpected(
            Map<String, RuntimeDependency> expected,
            RuntimeDependency dependency,
            List<ArtifactFinding> findings) {
        String ga = dependency.groupId() + ':' + dependency.artifactId();
        RuntimeDependency previous = expected.putIfAbsent(ga, dependency);
        if (previous != null && !previous.version().equals(dependency.version())) {
            findings.add(error("catalog-runtime-dependency-ambiguous", "pom.xml",
                    "Verified catalog subjects require conflicting versions of " + ga));
        }
    }

    private static boolean safeMavenToken(String value) {
        return value != null && SAFE_MAVEN_TOKEN.matcher(value).matches();
    }

    private static boolean exactRelease(String value) {
        if (!safeMavenToken(value)) {
            return false;
        }
        String normalized = value.toUpperCase(Locale.ROOT);
        return !normalized.contains("SNAPSHOT")
                && !"LATEST".equals(normalized)
                && !"RELEASE".equals(normalized);
    }

    public record CatalogDependencyBinding(
            ArtifactValidationResult validation,
            String pomDigest,
            List<RuntimeDependency> runtimeDependencies) {

        public CatalogDependencyBinding {
            runtimeDependencies = runtimeDependencies == null ? List.of() : List.copyOf(runtimeDependencies);
        }
    }

    private static void validateRoute(
            Path root,
            RouteArtifact route,
            Set<String> routeIds,
            Set<String> declaredPaths,
            List<ArtifactFinding> findings) {
        if (route == null || isBlank(route.routeId()) || isBlank(route.path())) {
            findings.add(error("route-invalid", null, "Every route requires a route ID and path"));
            return;
        }
        if (!routeIds.add(route.routeId())) {
            findings.add(error("route-id-duplicate", route.path(), "Duplicate route ID: " + route.routeId()));
        }
        if (!safeFileSegment(route.routeId())) {
            findings.add(error("route-id-invalid", route.path(),
                    "Route IDs must be single filename segments without separators or control characters"));
        }
        if (!route.path().endsWith(".camel.yaml")) {
            findings.add(error("route-suffix", route.path(), "Camel YAML routes must use the .camel.yaml suffix"));
        }
        if (!hasFileName(route.path(), route.routeId() + ".camel.yaml")) {
            findings.add(error("route-name", route.path(),
                    "Camel YAML route must be named exactly " + route.routeId() + ".camel.yaml"));
        }
        Path routeFile = validateFile(root, route.path(), route.digest(), true, declaredPaths, findings);
        if (routeFile != null) {
            validateRouteYaml(root, routeFile, route.routeId(), findings);
        }
    }

    private static void validateTests(
            Path root,
            ArtifactManifest manifest,
            Set<String> routeIds,
            Map<String, String> routePaths,
            Set<String> declaredPaths,
            List<ArtifactFinding> findings) {
        Map<String, Integer> testsPerRoute = new HashMap<>();
        for (TestArtifact test : manifest.citrusTests()) {
            if (test == null || isBlank(test.routeId()) || isBlank(test.path())) {
                findings.add(error("citrus-test-invalid", null, "Every Citrus test requires a route ID and path"));
                continue;
            }
            if (!test.path().endsWith(".camel.it.yaml")) {
                findings.add(error(
                        "citrus-test-suffix", test.path(), "Citrus YAML tests must use the .camel.it.yaml suffix"));
            }
            if (!hasFileName(test.path(), test.routeId() + ".camel.it.yaml")) {
                findings.add(error("citrus-test-name", test.path(),
                        "Citrus YAML test must be named exactly " + test.routeId() + ".camel.it.yaml"));
            }
            String expectedPath = "test/" + test.routeId() + ".camel.it.yaml";
            if (!expectedPath.equals(test.path())) {
                findings.add(error("citrus-test-location", test.path(),
                        "Citrus YAML test path must be exactly " + expectedPath));
            }
            if (!routeIds.contains(test.routeId())) {
                findings.add(error("citrus-test-route", test.path(),
                        "Citrus test references undeclared route ID: " + test.routeId()));
            }
            testsPerRoute.merge(test.routeId(), 1, Integer::sum);
            Path testFile = validateFile(root, test.path(), test.digest(), true, declaredPaths, findings);
            if (testFile != null) {
                validateCitrusYaml(root, testFile, test.routeId(), findings);
            }
        }
        if (manifest.testsRequired() && manifest.citrusTests().isEmpty()) {
            findings.add(error("citrus-tests-missing", null,
                    "Citrus tests are required but the manifest declares none"));
        }
        if (manifest.testsRequired() && manifest.oneTestPerRoute()) {
            for (String routeId : routeIds) {
                int count = testsPerRoute.getOrDefault(routeId, 0);
                if (count != 1) {
                    findings.add(error("citrus-route-uncovered", null,
                            "Expected exactly one Citrus YAML integration test for route " + routeId
                                                                       + " but found " + count));
                }
            }
        }
    }

    private static void validateCitrusConfiguration(
            Path root, ArtifactManifest manifest, List<ArtifactFinding> findings) {
        List<DeclaredArtifact> matching = manifest.artifacts().stream()
                .filter(java.util.Objects::nonNull)
                .filter(artifact -> CitrusDependencyPolicy.FORBIDDEN_JBANG_ARTIFACT_KIND.equals(artifact.kind())
                        || CitrusDependencyPolicy.FORBIDDEN_JBANG_PROPERTIES_PATH.equals(artifact.path()))
                .toList();
        if (!matching.isEmpty()) {
            findings.add(error("citrus-jbang-forbidden", CitrusDependencyPolicy.FORBIDDEN_JBANG_PROPERTIES_PATH,
                    "Direct controller-owned Citrus execution forbids Citrus JBang configuration"));
        }

        Path propertiesFile = root.resolve(CitrusDependencyPolicy.FORBIDDEN_JBANG_PROPERTIES_PATH);
        if (Files.exists(propertiesFile, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(propertiesFile)) {
            findings.add(error("citrus-jbang-forbidden", CitrusDependencyPolicy.FORBIDDEN_JBANG_PROPERTIES_PATH,
                    "Direct controller-owned Citrus execution must not use test/jbang.properties"));
        }
    }

    private static void validateDeclaredArtifact(
            Path root,
            DeclaredArtifact artifact,
            Set<String> declaredPaths,
            List<ArtifactFinding> findings) {
        if (artifact == null || isBlank(artifact.kind()) || isBlank(artifact.path())) {
            findings.add(error("artifact-invalid", null, "Declared artifacts require a kind and path"));
            return;
        }
        validateFile(root, artifact.path(), artifact.digest(), artifact.required(), declaredPaths, findings);
    }

    private static Path validateFile(
            Path root,
            String relativePath,
            String expectedDigest,
            boolean required,
            Set<String> declaredPaths,
            List<ArtifactFinding> findings) {
        Path path = safeResolve(root, relativePath, findings);
        if (path == null) {
            return null;
        }
        String normalized = root.relativize(path).toString().replace('\\', '/');
        if (!declaredPaths.add(normalized)) {
            findings.add(error("artifact-path-duplicate", normalized, "Artifact path is declared more than once"));
        }
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            if (required) {
                findings.add(error("artifact-missing", normalized, "Required artifact is missing"));
            }
            return null;
        }
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            findings.add(error("artifact-type", normalized, "Artifact must be a regular non-symbolic-link file"));
            return null;
        }
        if (required && isBlank(expectedDigest)) {
            findings.add(error("artifact-digest-missing", normalized,
                    "Required artifacts must declare a SHA-256 digest"));
        } else if (!isBlank(expectedDigest) && !expectedDigest.matches("sha256:[0-9a-f]{64}")) {
            findings.add(error("artifact-digest-format", normalized,
                    "Artifact digest must be a lowercase SHA-256 content address"));
        } else if (!isBlank(expectedDigest)) {
            try {
                String actual = digest(path);
                if (!actual.equals(expectedDigest)) {
                    findings.add(error("artifact-digest", normalized,
                            "Artifact digest mismatch: expected " + expectedDigest + " but found " + actual));
                }
            } catch (IOException e) {
                findings.add(error("artifact-read", normalized, "Could not hash artifact: " + e.getMessage()));
            }
        }
        return path;
    }

    private static Path safeResolve(Path root, String relativePath, List<ArtifactFinding> findings) {
        if (isBlank(relativePath)) {
            findings.add(error("artifact-path", relativePath, "Artifact path must not be blank"));
            return null;
        }
        Path relative;
        try {
            relative = Path.of(relativePath);
        } catch (java.nio.file.InvalidPathException e) {
            findings.add(error("artifact-path", relativePath, "Artifact path is not a valid filesystem path"));
            return null;
        }
        if (relative.isAbsolute()) {
            findings.add(error("artifact-path", relativePath, "Artifact paths must be relative to the project"));
            return null;
        }
        if (relativePath.indexOf('\\') >= 0 || !relative.equals(relative.normalize())) {
            findings.add(error("artifact-path", relativePath,
                    "Artifact paths must use canonical project-relative POSIX form"));
            return null;
        }
        Path path = root.resolve(relative).normalize();
        if (!path.startsWith(root)) {
            findings.add(error("artifact-path", relativePath, "Artifact path escapes the project root"));
            return null;
        }
        if (containsSymbolicLink(root, path)) {
            findings.add(error("artifact-path", relativePath,
                    "Artifact path contains a symbolic-link component"));
            return null;
        }
        return path;
    }

    private static boolean containsSymbolicLink(Path root, Path path) {
        Path current = root;
        for (Path part : root.relativize(path)) {
            current = current.resolve(part);
            if (Files.isSymbolicLink(current)) {
                return true;
            }
        }
        return false;
    }

    private static void validateRouteYaml(
            Path root, Path path, String expectedRouteId, List<ArtifactFinding> findings) {
        String relative = root.relativize(path).toString().replace('\\', '/');
        try {
            if (Files.size(path) > ShipArtifactLimits.MAX_ROUTE_YAML_BYTES) {
                findings.add(error("route-yaml-size", relative,
                        "Camel YAML route is too large for deterministic inspection"));
                return;
            }
            JsonNode document;
            try (var input = Files.newInputStream(path)) {
                document = YAML_READER.readTree(input);
            }
            List<String> declaredRouteIds = new ArrayList<>();
            collectRouteIds(document, declaredRouteIds);
            if (declaredRouteIds.isEmpty() || declaredRouteIds.get(0) == null
                    || declaredRouteIds.get(0).isBlank()) {
                findings.add(error("route-yaml-id-missing", relative,
                        "Camel YAML route must declare its route ID"));
            } else if (declaredRouteIds.size() != 1) {
                findings.add(error("route-yaml-count", relative,
                        "Each declared route artifact must contain exactly one Camel route"));
            } else if (!expectedRouteId.equals(declaredRouteIds.get(0))) {
                findings.add(error("route-yaml-id-mismatch", relative,
                        "Camel YAML declares route ID " + declaredRouteIds.get(0)
                                                                       + " but the manifest declares "
                                                                       + expectedRouteId));
            }
            ShipCamelMainBootstrap.validatePolicy(path);
        } catch (IOException | RuntimeException e) {
            findings.add(error("route-yaml-parse", relative,
                    "Could not parse Camel YAML route: " + safeMessage(e)));
        }
    }

    private static void validateCitrusYaml(
            Path root, Path path, String expectedRouteId, List<ArtifactFinding> findings) {
        String relative = root.relativize(path).toString().replace('\\', '/');
        try {
            if (Files.size(path) > ShipArtifactLimits.MAX_CITRUS_YAML_BYTES) {
                findings.add(error("citrus-yaml-size", relative,
                        "Citrus YAML test is too large for deterministic inspection"));
                return;
            }
            ShipCitrusYamlMain.validatePolicy(path, expectedRouteId);
        } catch (IOException | RuntimeException e) {
            findings.add(error("citrus-yaml-policy", relative,
                    "Citrus YAML is outside the direct execution allowlist: " + safeMessage(e)));
        }
    }

    private static void collectRouteIds(JsonNode document, List<String> routeIds) {
        if (document == null || document.isNull()) {
            return;
        }
        if (document.isArray()) {
            document.forEach(item -> collectRouteIds(item, routeIds));
            return;
        }
        if (!document.isObject()) {
            return;
        }
        JsonNode route = document.get("route");
        if (route != null) {
            JsonNode id = route.isObject() ? route.get("id") : null;
            routeIds.add(id != null && id.isTextual() ? id.textValue().trim() : null);
        }
        JsonNode routes = document.get("routes");
        if (routes != null) {
            collectRouteIds(routes, routeIds);
        }
    }

    private static void findWronglySuffixedRoutes(
            Path root, Set<String> declaredRoutePaths, List<ArtifactFinding> findings) {
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(ArtifactValidator::isYaml)
                    .forEach(path -> inspectYamlCandidate(root, path, declaredRoutePaths, findings));
        } catch (IOException e) {
            findings.add(error("route-scan", null,
                    "Could not enumerate possible route files: " + e.getMessage()));
        }
    }

    private static void inspectYamlCandidate(
            Path root, Path path, Set<String> declaredRoutePaths, List<ArtifactFinding> findings) {
        String relative = root.relativize(path).toString().replace('\\', '/');
        if (relative.endsWith(".camel.yaml")) {
            if (!declaredRoutePaths.contains(relative)) {
                findings.add(error("route-undeclared", relative,
                        "Camel route exists on disk but is absent from the route manifest"));
            }
            return;
        }
        try {
            if (Files.size(path) > ShipArtifactLimits.MAX_ROUTE_YAML_BYTES) {
                findings.add(error("route-scan-size", relative,
                        "YAML file is too large to prove that it does not contain a wrongly suffixed Camel route"));
                return;
            }
            JsonNode document;
            try (var input = Files.newInputStream(path)) {
                document = YAML_READER.readTree(input);
            }
            if (containsCamelDefinition(document)) {
                findings.add(error("route-suffix", relative,
                        "Possible Camel YAML route has the wrong suffix; expected .camel.yaml"));
            }
        } catch (IOException | RuntimeException e) {
            findings.add(error("route-scan-parse", relative,
                    "Could not prove that YAML is not an alternate Camel route definition: " + safeMessage(e)));
        }
    }

    private static boolean containsCamelDefinition(JsonNode document) {
        if (document == null || document.isNull()) {
            return false;
        }
        if (document.isArray()) {
            for (JsonNode item : document) {
                if (containsCamelDefinition(item)) {
                    return true;
                }
            }
            return false;
        }
        if (!document.isObject()) {
            return false;
        }
        JsonNode apiVersion = document.get("apiVersion");
        JsonNode kind = document.get("kind");
        boolean camelCustomResource = apiVersion != null && apiVersion.isTextual()
                && apiVersion.asText().toLowerCase(Locale.ROOT).startsWith("camel.apache.org/")
                && kind != null && kind.isTextual()
                && Set.of("pipe", "kamelet").contains(kind.asText().toLowerCase(Locale.ROOT));
        return document.has("route")
                || document.has("routes")
                || document.has("from")
                || document.has("routeConfiguration")
                || document.has("routeConfigurations")
                || document.has("routeTemplate")
                || document.has("routeTemplates")
                || document.has("templatedRoute")
                || document.has("templatedRoutes")
                || document.has("rest")
                || document.has("rests")
                || document.has("restConfiguration")
                || document.has("restConfigurations")
                || camelCustomResource;
    }

    private static void validateJavaPolicy(
            Path root, ArtifactManifest manifest, List<ArtifactFinding> findings) {
        if (manifest.javaPolicy() == null || manifest.javaPolicy() == ArtifactManifest.JavaPolicy.ALLOWED) {
            return;
        }
        Path source = root.resolve("src");
        if (!Files.isDirectory(source)) {
            return;
        }
        Set<String> exceptions = new HashSet<>(manifest.approvedJavaExceptions());
        try (Stream<Path> files = Files.walk(source)) {
            files.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .forEach(path -> {
                        String relative = root.relativize(path).toString().replace('\\', '/');
                        if (manifest.javaPolicy() == ArtifactManifest.JavaPolicy.FORBIDDEN) {
                            findings.add(error("java-forbidden", relative,
                                    "Java source is forbidden by the approved requirements"));
                        } else if (!exceptions.contains(relative)) {
                            findings.add(error("java-unapproved", relative,
                                    "Java source lacks a controller-approved exception"));
                        }
                    });
        } catch (IOException e) {
            findings.add(error("java-scan", "src", "Could not inspect Java sources: " + e.getMessage()));
        }
    }

    private static void validateRuntimeJavaPolicy(
            ArtifactManifest manifest, List<ArtifactFinding> findings) {
        if ("main".equals(normalizeRuntime(manifest.runtime()))
                && manifest.javaPolicy() != null
                && manifest.javaPolicy() != ArtifactManifest.JavaPolicy.FORBIDDEN) {
            findings.add(error("main-java-policy", null,
                    "Camel Main requires Java policy FORBIDDEN in Ship protocol v1 because its mandatory startup "
                                                         + "evidence does not compile Java sources"));
        }
    }

    private static void validateRuntimeConsistency(
            Path root,
            ArtifactManifest manifest,
            ArtifactPolicy policy,
            List<ArtifactFinding> findings) {
        boolean approvedPolicy = policy != null;
        String runtime = normalizeRuntime(approvedPolicy ? policy.runtime() : manifest.runtime());
        String camelVersion = normalizeNullable(approvedPolicy ? policy.camelVersion() : manifest.camelVersion());
        String platformVersion = normalizeNullable(
                approvedPolicy ? policy.platformVersion() : manifest.platformVersion());
        String springBootVersion = normalizeNullable(
                approvedPolicy ? policy.springBootVersion() : manifest.springBootVersion());
        String citrusVersion = normalizeNullable(
                approvedPolicy ? policy.citrusVersion() : manifest.citrusVersion());
        if (!Set.of("main", "spring-boot", "quarkus").contains(runtime)) {
            findings.add(error("runtime-invalid", null,
                    "Runtime must be one of main, spring-boot, or quarkus"));
            return;
        }
        validateRuntimeConfiguration(
                root, runtime, camelVersion, platformVersion, springBootVersion, citrusVersion, true, findings);
        validatePom(root, runtime, camelVersion, platformVersion, springBootVersion, approvedPolicy, findings);
    }

    private static void validateRuntimeConfiguration(
            Path root,
            String runtime,
            String camelVersion,
            String platformVersion,
            String springBootVersion,
            String citrusVersion,
            boolean required,
            List<ArtifactFinding> findings) {
        String relative = ".camel-kit/config.properties";
        Path config = root.resolve(relative);
        if (containsSymbolicLink(root, config)) {
            findings.add(error("runtime-config-type", relative,
                    "Runtime configuration path must not contain symbolic links"));
            return;
        }
        if (!Files.exists(config, LinkOption.NOFOLLOW_LINKS)) {
            if (required) {
                findings.add(error("runtime-config-missing", relative,
                        "Approved Ship output requires .camel-kit/config.properties"));
            }
            return;
        }
        if (Files.isSymbolicLink(config) || !Files.isRegularFile(config, LinkOption.NOFOLLOW_LINKS)) {
            findings.add(error("runtime-config-type", relative,
                    "Runtime configuration must be a regular non-symbolic-link file"));
            return;
        }
        try {
            if (Files.size(config) > MAX_CONFIG_INSPECTION_BYTES) {
                findings.add(error("runtime-config-size", relative,
                        "Runtime configuration is too large for deterministic inspection"));
                return;
            }
            Properties properties = new UniqueProperties();
            try (var reader = Files.newBufferedReader(config, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
            validateConfigValue(properties, "project.runtime", runtime, required, true, findings);
            validateConfigValue(properties, "project.camelVersion", camelVersion, required, false, findings);
            validateConfigValue(properties, "citrus.version", citrusVersion, required, false, findings);
            String expectedPlatform = "main".equals(runtime) ? camelVersion : platformVersion;
            validateConfigValue(
                    properties, "project.platformBomVersion", expectedPlatform, required, false, findings);
            if ("spring-boot".equals(runtime)) {
                validateConfigValue(
                        properties, "project.springBootVersion", springBootVersion, required, false, findings);
            } else if (normalizeNullable(properties.getProperty("project.springBootVersion")) != null) {
                findings.add(error("runtime-config-mismatch", relative,
                        "project.springBootVersion is valid only for the spring-boot runtime"));
            }
        } catch (IOException | IllegalArgumentException e) {
            findings.add(error("runtime-config-read", relative,
                    "Could not parse runtime configuration: " + safeMessage(e)));
        }
    }

    private static void validateConfigValue(
            Properties properties,
            String key,
            String expected,
            boolean required,
            boolean runtimeValue,
            List<ArtifactFinding> findings) {
        String configured = normalizeNullable(properties.getProperty(key));
        if (configured == null) {
            if (required) {
                findings.add(error("runtime-config-value-missing", ".camel-kit/config.properties",
                        "Runtime configuration is missing " + key));
            }
            return;
        }
        String actual = runtimeValue ? normalizeRuntime(configured) : configured;
        if (expected != null && !expected.equals(actual)) {
            findings.add(error("runtime-config-mismatch", ".camel-kit/config.properties",
                    "Configured " + key + " value " + actual + " differs from approved value " + expected));
        }
    }

    private static void validatePom(
            Path root,
            String runtime,
            String camelVersion,
            String platformVersion,
            String springBootVersion,
            boolean approvedPolicy,
            List<ArtifactFinding> findings) {
        validateMavenProjectHooks(root, findings);
        Path pom = root.resolve("pom.xml");
        if (!Files.exists(pom, LinkOption.NOFOLLOW_LINKS)) {
            if (approvedPolicy && !"main".equals(runtime)) {
                findings.add(error("runtime-pom-missing", "pom.xml",
                        runtime + " Ship output requires a Maven POM"));
            }
            return;
        }
        if (Files.isSymbolicLink(pom) || !Files.isRegularFile(pom, LinkOption.NOFOLLOW_LINKS)) {
            findings.add(error("runtime-pom-type", "pom.xml",
                    "Maven POM must be a regular non-symbolic-link file"));
            return;
        }
        try {
            if (Files.size(pom) > MAX_POM_INSPECTION_BYTES) {
                findings.add(error("runtime-pom-size", "pom.xml",
                        "Maven POM is too large for deterministic inspection"));
                return;
            }
            MavenModel model = parseMavenModel(pom, "main".equals(runtime));
            validateMavenExecutionPolicy(model, findings);
            boolean hasSpring = model.hasFamily("org.springframework.boot")
                    || model.hasFamily("org.apache.camel.springboot");
            boolean hasQuarkus = model.hasFamily("io.quarkus")
                    || model.hasFamily("io.quarkus.platform")
                    || model.hasFamily("org.apache.camel.quarkus");
            switch (runtime) {
                case "main" -> validateMainPom(model, camelVersion, hasSpring, hasQuarkus, findings);
                case "spring-boot" -> validateSpringBootPom(
                        model, camelVersion, platformVersion, springBootVersion, hasQuarkus, findings);
                case "quarkus" -> validateQuarkusPom(model, platformVersion, hasSpring, findings);
                default -> throw new IllegalStateException("Unexpected runtime: " + runtime);
            }
        } catch (ExactMainPomPolicyException e) {
            findings.add(error("runtime-pom-policy", "pom.xml", e.getMessage()));
        } catch (IOException | RuntimeException e) {
            findings.add(error("runtime-pom-parse", "pom.xml",
                    "Could not securely parse Maven POM: " + safeMessage(e)));
        }
    }

    private static void validateMavenProjectHooks(Path root, List<ArtifactFinding> findings) {
        for (String relative : List.of(
                ".mvn/extensions.xml", ".mvn/maven.config", ".mvn/jvm.config")) {
            Path path = root.resolve(relative);
            if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
                findings.add(error("runtime-maven-hook", relative,
                        "Ship protocol v1 forbids project-controlled Maven startup hooks"));
            }
        }
    }

    private static void validateMavenExecutionPolicy(
            MavenModel model, List<ArtifactFinding> findings) {
        if (model.parent() != null) {
            findings.add(error("runtime-pom-parent", "pom.xml",
                    "Ship protocol v1 requires a self-contained POM without inherited build behavior"));
        }
        if (model.packaging() != null && !"jar".equals(model.packaging())) {
            findings.add(error("runtime-pom-packaging", "pom.xml",
                    "Ship protocol v1 requires jar packaging; found " + model.packaging()));
        }
        if (model.hasProfiles() || model.hasModules() || model.hasRepositories()
                || model.hasPluginRepositories() || model.hasBuildExtensions()
                || model.hasPluginManagement()) {
            findings.add(error("runtime-pom-dynamic-model", "pom.xml",
                    "Ship protocol v1 forbids profiles, modules, custom repositories, build extensions, "
                                                                       + "and plugin management because they escape deterministic inspection"));
        }
        for (MavenCoordinate plugin : model.plugins()) {
            String coordinate = plugin.groupId() + ':' + plugin.artifactId();
            if (!APPROVED_BUILD_PLUGINS.contains(coordinate)) {
                findings.add(error("runtime-pom-plugin", "pom.xml",
                        "Ship protocol v1 does not approve build plugin " + coordinate));
            }
        }
        if (model.enabledTestSkipControl() != null) {
            findings.add(error("runtime-pom-test-skip", "pom.xml",
                    "Maven test execution is disabled by " + model.enabledTestSkipControl()));
        }
    }

    private static void validateMainPom(
            MavenModel model,
            String camelVersion,
            boolean hasSpring,
            boolean hasQuarkus,
            List<ArtifactFinding> findings) {
        if (hasSpring || hasQuarkus) {
            findings.add(error("runtime-pom-mismatch", "pom.xml",
                    "Camel Main runtime cannot use Spring Boot or Quarkus runtime coordinates"));
        }
        MavenCoordinate main = requireRuntimeDependency(
                model, "org.apache.camel", "camel-main", "Camel Main", findings);
        MavenCoordinate bom = findImportedBom(model, "org.apache.camel", "camel-bom");
        if (bom != null) {
            validateVersion(bom, camelVersion, "Camel BOM", findings);
        }
        if (main != null && main.version() == null && bom == null) {
            findings.add(error("runtime-pom-version", "pom.xml",
                    "Camel Main version is neither explicit nor managed by org.apache.camel:camel-bom"));
        }
        validateExplicitDependencyVersions(
                model, "org.apache.camel", camelVersion, "Camel dependency", false, findings);
    }

    private static void validateSpringBootPom(
            MavenModel model,
            String camelVersion,
            String platformVersion,
            String springBootVersion,
            boolean hasQuarkus,
            List<ArtifactFinding> findings) {
        if (hasQuarkus) {
            findings.add(error("runtime-pom-mismatch", "pom.xml",
                    "Spring Boot runtime cannot use Quarkus runtime coordinates"));
        }
        if (model.parent() != null
                && model.parent().matches("org.springframework.boot", "spring-boot-starter-parent")) {
            findings.add(error("runtime-pom-coordinate", "pom.xml",
                    "Spring Boot parent POM is forbidden; use the approved Camel Spring Boot BOM"));
        }
        requireImportedBom(model, "org.apache.camel.springboot", "camel-spring-boot-bom",
                platformVersion, "Camel Spring Boot BOM", findings);
        requireRuntimeDependency(model, "org.springframework.boot", "spring-boot-starter",
                "Spring Boot starter", findings);
        requireRuntimeDependency(model, "org.apache.camel.springboot", "camel-spring-boot-starter",
                "Camel Spring Boot starter", findings);
        MavenCoordinate plugin = requirePlugin(
                model, "org.springframework.boot", "spring-boot-maven-plugin",
                "Spring Boot Maven plugin", findings);
        if (plugin != null) {
            validateVersion(plugin, springBootVersion, "Spring Boot Maven plugin", findings);
        }
        validateExplicitDependencyVersions(
                model, "org.apache.camel.springboot", camelVersion, "Camel Spring Boot dependency", false, findings);
        validateExplicitDependencyVersions(
                model, "org.springframework.boot", springBootVersion, "Spring Boot dependency", false, findings);
    }

    private static void validateQuarkusPom(
            MavenModel model,
            String platformVersion,
            boolean hasSpring,
            List<ArtifactFinding> findings) {
        if (hasSpring) {
            findings.add(error("runtime-pom-mismatch", "pom.xml",
                    "Quarkus runtime cannot use Spring Boot runtime coordinates"));
        }
        requireImportedBom(model, "io.quarkus.platform", "quarkus-bom",
                platformVersion, "Quarkus platform BOM", findings);
        requireImportedBom(model, "io.quarkus.platform", "quarkus-camel-bom",
                platformVersion, "Camel Quarkus platform BOM", findings);
        boolean camelExtension = model.dependencies().stream()
                .anyMatch(coordinate -> "org.apache.camel.quarkus".equals(coordinate.groupId())
                        && coordinate.artifactId() != null
                        && coordinate.artifactId().startsWith("camel-quarkus-")
                        && coordinate.runtimeScoped());
        if (!camelExtension) {
            findings.add(error("runtime-pom-coordinate", "pom.xml",
                    "Quarkus runtime requires at least one org.apache.camel.quarkus:camel-quarkus-* dependency"));
        }
        MavenCoordinate plugin = requirePlugin(
                model, "io.quarkus", "quarkus-maven-plugin", "Quarkus Maven plugin", findings);
        if (plugin != null) {
            validateVersion(plugin, platformVersion, "Quarkus Maven plugin", findings);
        }
        validateExplicitDependencyVersions(
                model, "org.apache.camel.quarkus", null, "Camel Quarkus dependency", true, findings);
        validateExplicitDependencyVersions(
                model, "io.quarkus", null, "Quarkus dependency", true, findings);
    }

    private static MavenCoordinate requireRuntimeDependency(
            MavenModel model,
            String groupId,
            String artifactId,
            String label,
            List<ArtifactFinding> findings) {
        MavenCoordinate coordinate = model.dependencies().stream()
                .filter(candidate -> candidate.matches(groupId, artifactId) && candidate.runtimeScoped())
                .findFirst()
                .orElse(null);
        if (coordinate == null) {
            findings.add(error("runtime-pom-coordinate", "pom.xml",
                    label + " dependency " + groupId + ":" + artifactId + " is required"));
        }
        return coordinate;
    }

    private static MavenCoordinate requirePlugin(
            MavenModel model,
            String groupId,
            String artifactId,
            String label,
            List<ArtifactFinding> findings) {
        MavenCoordinate coordinate = model.plugins().stream()
                .filter(candidate -> candidate.matches(groupId, artifactId))
                .findFirst()
                .orElse(null);
        if (coordinate == null) {
            findings.add(error("runtime-pom-coordinate", "pom.xml",
                    label + " coordinate " + groupId + ":" + artifactId + " is required"));
        }
        return coordinate;
    }

    private static void requireImportedBom(
            MavenModel model,
            String groupId,
            String artifactId,
            String expectedVersion,
            String label,
            List<ArtifactFinding> findings) {
        MavenCoordinate coordinate = findImportedBom(model, groupId, artifactId);
        if (coordinate == null) {
            findings.add(error("runtime-pom-coordinate", "pom.xml",
                    label + " import " + groupId + ":" + artifactId + " is required"));
            return;
        }
        validateVersion(coordinate, expectedVersion, label, findings);
    }

    private static MavenCoordinate findImportedBom(MavenModel model, String groupId, String artifactId) {
        return model.managedDependencies().stream()
                .filter(candidate -> candidate.matches(groupId, artifactId)
                        && "pom".equals(candidate.type())
                        && "import".equals(candidate.scope()))
                .findFirst()
                .orElse(null);
    }

    private static void validateVersion(
            MavenCoordinate coordinate,
            String expectedVersion,
            String label,
            List<ArtifactFinding> findings) {
        if (expectedVersion == null || !expectedVersion.equals(coordinate.version())) {
            findings.add(error("runtime-pom-version", "pom.xml",
                    label + " version " + coordinate.version()
                                                                 + " differs from approved version "
                                                                 + expectedVersion));
        }
    }

    private static void validateExplicitDependencyVersions(
            MavenModel model,
            String groupId,
            String expectedVersion,
            String label,
            boolean mustBeManaged,
            List<ArtifactFinding> findings) {
        model.dependencies().stream()
                .filter(coordinate -> groupId.equals(coordinate.groupId()) && coordinate.version() != null)
                .forEach(coordinate -> {
                    if (mustBeManaged) {
                        findings.add(error("runtime-pom-version", "pom.xml",
                                label + " " + coordinate.groupId() + ':' + coordinate.artifactId()
                                                                             + " must omit its version and use the approved platform BOM"));
                    } else if (!java.util.Objects.equals(expectedVersion, coordinate.version())) {
                        findings.add(error("runtime-pom-version", "pom.xml",
                                label + " " + coordinate.groupId() + ':' + coordinate.artifactId()
                                                                             + " version " + coordinate.version()
                                                                             + " differs from approved version "
                                                                             + expectedVersion));
                    }
                });
    }

    private static MavenModel parseMavenModel(Path pom, boolean exactMain) throws IOException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        try {
            factory.setNamespaceAware(true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setAttribute("jdk.xml.maxElementDepth", "100");
            var builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new StrictXmlErrorHandler());
            byte[] source = readPomBytes(pom);
            Document document = builder.parse(new ByteArrayInputStream(source));
            Element project = document.getDocumentElement();
            if (exactMain) {
                try {
                    validateExactMainPomGrammar(document, project);
                } catch (IOException e) {
                    throw new ExactMainPomPolicyException(e.getMessage(), e);
                }
            }
            String namespace = project.getNamespaceURI();
            if (!"project".equals(localName(project))
                    || (namespace != null && !namespace.isBlank() && !MAVEN_NAMESPACE.equals(namespace))) {
                throw new IOException("Document root is not a Maven project");
            }
            for (Node node = project; node != null; node = nextNode(project, node)) {
                if (node instanceof Element element) {
                    String elementNamespace = element.getNamespaceURI();
                    if (elementNamespace != null && !elementNamespace.isBlank()
                            && !MAVEN_NAMESPACE.equals(elementNamespace)) {
                        throw new IOException("Maven POM contains a foreign XML namespace");
                    }
                }
            }

            Map<String, String> properties = readMavenProperties(project);
            Element parentElement = child(project, "parent");
            String parentGroup = childText(parentElement, "groupId");
            String parentArtifact = childText(parentElement, "artifactId");
            String parentVersion = childText(parentElement, "version");
            String projectGroup = childText(project, "groupId");
            if (projectGroup == null) {
                projectGroup = parentGroup;
            }
            String projectVersion = childText(project, "version");
            if (projectVersion == null) {
                projectVersion = parentVersion;
            }
            putMavenBuiltin(properties, "project.groupId", projectGroup);
            putMavenBuiltin(properties, "pom.groupId", projectGroup);
            putMavenBuiltin(properties, "project.artifactId", childText(project, "artifactId"));
            putMavenBuiltin(properties, "pom.artifactId", childText(project, "artifactId"));
            putMavenBuiltin(properties, "project.version", projectVersion);
            putMavenBuiltin(properties, "pom.version", projectVersion);
            putMavenBuiltin(properties, "project.parent.groupId", parentGroup);
            putMavenBuiltin(properties, "project.parent.artifactId", parentArtifact);
            putMavenBuiltin(properties, "project.parent.version", parentVersion);

            MavenCoordinate parent = parentElement == null ? null : coordinate(parentElement, properties, false);
            List<MavenCoordinate> dependencies = coordinates(child(project, "dependencies"), properties, "dependency");
            Element dependencyManagement = child(project, "dependencyManagement");
            List<MavenCoordinate> managed = coordinates(
                    child(dependencyManagement, "dependencies"), properties, "dependency");
            Element build = child(project, "build");
            List<MavenCoordinate> plugins = coordinates(child(build, "plugins"), properties, "plugin");
            rejectDuplicateCoordinates(dependencies, "dependencies");
            rejectDuplicateCoordinates(managed, "managed dependencies");
            rejectDuplicateCoordinates(plugins, "plugins");
            return new MavenModel(
                    dependencies,
                    managed,
                    plugins,
                    parent,
                    resolveMavenValue(childText(project, "packaging"), properties),
                    child(project, "properties") != null,
                    dependencyManagement != null,
                    build != null,
                    child(project, "profiles") != null,
                    child(project, "modules") != null,
                    child(project, "repositories") != null,
                    child(project, "pluginRepositories") != null,
                    child(build, "extensions") != null,
                    child(build, "pluginManagement") != null,
                    enabledTestSkipControl(project, properties),
                    digest(source));
        } catch (ParserConfigurationException | SAXException | IllegalArgumentException e) {
            throw new IOException(safeMessage(e), e);
        }
    }

    private static byte[] readPomBytes(Path pom) throws IOException {
        Path parent = pom.getParent();
        Path name = pom.getFileName();
        if (parent == null || name == null) {
            throw new IOException("Maven POM has no secure parent directory");
        }
        return ProjectEvidenceFiles.readMaterial(
                parent, name.toString(), Math.toIntExact(MAX_POM_INSPECTION_BYTES));
    }

    private static void validateExactMainPomGrammar(Document document, Element project) throws IOException {
        requireDocumentStructure(document, project);
        requireDefaultMavenElement(project, true);

        Map<String, Element> root = exactChildren(project, Set.of(
                "modelVersion", "groupId", "artifactId", "version", "packaging", "dependencies"));
        requireLiteral(root, "modelVersion", "4.0.0");
        requireSafeLiteral(root, "groupId", "project groupId");
        requireSafeLiteral(root, "artifactId", "project artifactId");
        requireSafeLiteral(root, "version", "project version");
        if (root.containsKey("packaging")) {
            requireLiteral(root, "packaging", "jar");
        }

        Element dependencies = requireElement(root, "dependencies");
        requireDefaultMavenElement(dependencies, false);
        int count = 0;
        for (Node node = dependencies.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node.getNodeType() == Node.TEXT_NODE && node.getTextContent().isBlank()) {
                continue;
            }
            if (!(node instanceof Element dependency) || !"dependency".equals(localName(dependency))) {
                throw new IOException("Exact Main POM dependencies may contain only dependency elements");
            }
            validateExactMainDependency(dependency);
            count++;
        }
        if (count == 0) {
            throw new IOException("Exact Main POM dependencies must not be empty");
        }
    }

    private static void requireDocumentStructure(Document document, Element project) throws IOException {
        if (project == null) {
            throw new IOException("Exact Main POM is missing its project root");
        }
        for (Node node = document.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node == project || node.getNodeType() == Node.TEXT_NODE && node.getTextContent().isBlank()) {
                continue;
            }
            throw new IOException("Exact Main POM forbids comments, processing instructions, and extra roots");
        }
    }

    private static void validateExactMainDependency(Element dependency) throws IOException {
        requireDefaultMavenElement(dependency, false);
        Map<String, Element> fields = exactChildren(
                dependency, Set.of("groupId", "artifactId", "version", "type", "scope"));
        requireSafeLiteral(fields, "groupId", "dependency groupId");
        requireSafeLiteral(fields, "artifactId", "dependency artifactId");
        String version = requireSafeLiteral(fields, "version", "dependency version");
        if (!exactRelease(version)) {
            throw new IOException("Exact Main POM dependency versions must be fixed releases");
        }
        if (fields.containsKey("type")) {
            requireLiteral(fields, "type", "jar");
        }
        if (fields.containsKey("scope")) {
            String scope = literalText(fields.get("scope"), "dependency scope");
            if (!Set.of("compile", "runtime").contains(scope)) {
                throw new IOException("Exact Main POM dependency scope must be compile or runtime");
            }
        }
    }

    private static Map<String, Element> exactChildren(Element parent, Set<String> allowed) throws IOException {
        Map<String, Element> children = new HashMap<>();
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node.getNodeType() == Node.TEXT_NODE && node.getTextContent().isBlank()) {
                continue;
            }
            if (!(node instanceof Element element)) {
                throw new IOException("Exact Main POM forbids comments and processing instructions");
            }
            requireDefaultMavenElement(element, false);
            String name = localName(element);
            if (!allowed.contains(name)) {
                throw new IOException("Exact Main POM contains unsupported element " + name);
            }
            if (children.putIfAbsent(name, element) != null) {
                throw new IOException("Exact Main POM repeats element " + name);
            }
        }
        return children;
    }

    private static void requireDefaultMavenElement(Element element, boolean root) throws IOException {
        if (!MAVEN_NAMESPACE.equals(element.getNamespaceURI()) || element.getPrefix() != null) {
            throw new IOException("Exact Main POM requires unprefixed elements in the Maven namespace");
        }
        int expectedAttributes = root ? 1 : 0;
        if (element.getAttributes().getLength() != expectedAttributes
                || root && !MAVEN_NAMESPACE.equals(element.getAttributeNS(
                        XMLConstants.XMLNS_ATTRIBUTE_NS_URI, XMLConstants.XMLNS_ATTRIBUTE))) {
            throw new IOException("Exact Main POM contains unsupported XML attributes or namespace declarations");
        }
    }

    private static Element requireElement(Map<String, Element> elements, String name) throws IOException {
        Element element = elements.get(name);
        if (element == null) {
            throw new IOException("Exact Main POM is missing " + name);
        }
        return element;
    }

    private static String requireSafeLiteral(
            Map<String, Element> elements, String name, String label)
            throws IOException {
        String value = literalText(requireElement(elements, name), label);
        if (!safeMavenToken(value)) {
            throw new IOException("Exact Main POM " + label + " is not a safe literal");
        }
        return value;
    }

    private static void requireLiteral(
            Map<String, Element> elements, String name, String expected)
            throws IOException {
        Element element = requireElement(elements, name);
        String value = literalText(element, name);
        if (!expected.equals(value)) {
            throw new IOException("Exact Main POM " + name + " must be " + expected);
        }
    }

    private static String literalText(Element element, String label) throws IOException {
        requireDefaultMavenElement(element, false);
        StringBuilder value = new StringBuilder();
        for (Node node = element.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node.getNodeType() != Node.TEXT_NODE) {
                throw new IOException("Exact Main POM " + label + " must be literal text");
            }
            value.append(node.getNodeValue());
        }
        String literal = value.toString().trim();
        if (literal.isEmpty() || literal.contains("${")) {
            throw new IOException("Exact Main POM " + label + " must be a non-empty literal");
        }
        return literal;
    }

    private static String enabledTestSkipControl(Element root, Map<String, String> properties) {
        for (Node node = root; node != null; node = nextNode(root, node)) {
            if (!(node instanceof Element element)) {
                continue;
            }
            String name = localName(element).toLowerCase(Locale.ROOT);
            if (!TEST_SKIP_CONTROLS.contains(name)) {
                continue;
            }
            String value = element.getTextContent().trim();
            try {
                value = resolveMavenValue(value, properties);
            } catch (IllegalArgumentException ignored) {
                return localName(element) + " with an unresolved value";
            }
            if (value != null && Set.of("true", "yes", "on", "1")
                    .contains(value.toLowerCase(Locale.ROOT))) {
                return localName(element) + '=' + value;
            }
        }
        return null;
    }

    private static Node nextNode(Node root, Node current) {
        if (current.getFirstChild() != null) {
            return current.getFirstChild();
        }
        Node node = current;
        while (node != null && node != root && node.getNextSibling() == null) {
            node = node.getParentNode();
        }
        return node == null || node == root ? null : node.getNextSibling();
    }

    private static Map<String, String> readMavenProperties(Element project) {
        Map<String, String> properties = new HashMap<>();
        Element propertiesElement = child(project, "properties");
        if (propertiesElement == null) {
            return properties;
        }
        for (Node node = propertiesElement.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element property) {
                String name = localName(property);
                if (properties.putIfAbsent(name, property.getTextContent().trim()) != null) {
                    throw new IllegalArgumentException("Duplicate Maven property: " + name);
                }
            }
        }
        return properties;
    }

    private static void putMavenBuiltin(Map<String, String> properties, String key, String value) {
        if (value != null) {
            properties.put(key, value);
        }
    }

    private static List<MavenCoordinate> coordinates(
            Element container, Map<String, String> properties, String elementName) {
        if (container == null) {
            return List.of();
        }
        List<MavenCoordinate> result = new ArrayList<>();
        for (Node node = container.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && elementName.equals(localName(element))) {
                result.add(coordinate(element, properties, "plugin".equals(elementName)));
            }
        }
        return List.copyOf(result);
    }

    private static MavenCoordinate coordinate(
            Element element, Map<String, String> properties, boolean plugin) {
        boolean expressionDeclared = element.getTextContent().contains("${");
        String groupId = resolveMavenValue(childText(element, "groupId"), properties);
        if (plugin && groupId == null) {
            groupId = "org.apache.maven.plugins";
        }
        String artifactId = resolveMavenValue(childText(element, "artifactId"), properties);
        if (groupId == null || artifactId == null) {
            throw new IllegalArgumentException("Maven coordinate is missing groupId or artifactId");
        }
        return new MavenCoordinate(
                groupId,
                artifactId,
                resolveMavenValue(childText(element, "version"), properties),
                resolveMavenValue(childText(element, "type"), properties),
                resolveMavenValue(childText(element, "scope"), properties),
                resolveMavenValue(childText(element, "classifier"), properties),
                child(element, "optional") != null,
                child(element, "exclusions") != null,
                expressionDeclared);
    }

    private static String resolveMavenValue(String value, Map<String, String> properties) {
        return resolveMavenValue(value, properties, new LinkedHashSet<>(), 0);
    }

    private static String resolveMavenValue(
            String value,
            Map<String, String> properties,
            Set<String> resolving,
            int depth) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (depth > 20) {
            throw new IllegalArgumentException("Maven property expansion is too deep");
        }
        Matcher matcher = MAVEN_PROPERTY.matcher(value);
        StringBuffer resolved = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            String replacement = properties.get(key);
            if (replacement == null) {
                throw new IllegalArgumentException("Unresolved Maven property: " + key);
            }
            if (!resolving.add(key)) {
                throw new IllegalArgumentException("Cyclic Maven property: " + key);
            }
            replacement = resolveMavenValue(replacement, properties, resolving, depth + 1);
            resolving.remove(key);
            if (replacement == null) {
                throw new IllegalArgumentException("Empty Maven property: " + key);
            }
            matcher.appendReplacement(resolved, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(resolved);
        String result = resolved.toString().trim();
        if (MAVEN_PROPERTY.matcher(result).find()) {
            return resolveMavenValue(result, properties, resolving, depth + 1);
        }
        return result.isEmpty() ? null : result;
    }

    private static void rejectDuplicateCoordinates(List<MavenCoordinate> coordinates, String label) {
        Set<String> seen = new HashSet<>();
        for (MavenCoordinate coordinate : coordinates) {
            String key = coordinate.groupId() + ':' + coordinate.artifactId();
            if (!seen.add(key)) {
                throw new IllegalArgumentException("Duplicate Maven " + label + " coordinate: " + key);
            }
        }
    }

    private static Element child(Element parent, String name) {
        if (parent == null) {
            return null;
        }
        Element result = null;
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && name.equals(localName(element))) {
                if (result != null) {
                    throw new IllegalArgumentException("Duplicate Maven element: " + name);
                }
                result = element;
            }
        }
        return result;
    }

    private static String childText(Element parent, String name) {
        Element child = child(parent, name);
        if (child == null) {
            return null;
        }
        String value = child.getTextContent().trim();
        return value.isEmpty() ? null : value;
    }

    private static String localName(Node node) {
        return node.getLocalName() == null ? node.getNodeName() : node.getLocalName();
    }

    private record MavenCoordinate(
            String groupId,
            String artifactId,
            String version,
            String type,
            String scope,
            String classifier,
            boolean optionalDeclared,
            boolean exclusionsDeclared,
            boolean expressionDeclared) {

        private boolean matches(String expectedGroupId, String expectedArtifactId) {
            return expectedGroupId.equals(groupId) && expectedArtifactId.equals(artifactId);
        }

        private boolean runtimeScoped() {
            return (scope == null || "compile".equals(scope) || "runtime".equals(scope))
                    && (type == null || "jar".equals(type))
                    && classifier == null;
        }
    }

    private record MavenModel(
            List<MavenCoordinate> dependencies,
            List<MavenCoordinate> managedDependencies,
            List<MavenCoordinate> plugins,
            MavenCoordinate parent,
            String packaging,
            boolean hasProperties,
            boolean hasDependencyManagement,
            boolean hasBuild,
            boolean hasProfiles,
            boolean hasModules,
            boolean hasRepositories,
            boolean hasPluginRepositories,
            boolean hasBuildExtensions,
            boolean hasPluginManagement,
            String enabledTestSkipControl,
            String sourceDigest) {

        private boolean hasFamily(String groupId) {
            return java.util.stream.Stream.of(
                    dependencies.stream(), managedDependencies.stream(), plugins.stream(),
                    parent == null ? Stream.<MavenCoordinate>empty() : Stream.of(parent))
                    .flatMap(stream -> stream)
                    .anyMatch(coordinate -> groupId.equals(coordinate.groupId()));
        }
    }

    private static final class UniqueProperties extends Properties {

        @Override
        public synchronized Object put(Object key, Object value) {
            if (containsKey(key)) {
                throw new IllegalArgumentException("Duplicate property: " + key);
            }
            return super.put(key, value);
        }
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

    private static final class ExactMainPomPolicyException extends IOException {

        private ExactMainPomPolicyException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static void validateApprovedPolicy(
            ArtifactManifest manifest, ArtifactPolicy policy, List<ArtifactFinding> findings) {
        requirePolicyEqual("runtime", normalizeRuntime(policy.runtime()), normalizeRuntime(manifest.runtime()),
                findings);
        requirePolicyEqual("camel-version", policy.camelVersion(), manifest.camelVersion(), findings);
        requirePolicyEqual("platform-version", normalizeNullable(policy.platformVersion()),
                normalizeNullable(manifest.platformVersion()), findings);
        requirePolicyEqual("spring-boot-version", normalizeNullable(policy.springBootVersion()),
                normalizeNullable(manifest.springBootVersion()), findings);
        requirePolicyEqual("route-dsl", normalizeRuntime(policy.routeDsl()),
                normalizeRuntime(manifest.routeDsl()), findings);
        requirePolicyEqual("expression-language", normalizeRuntime(policy.expressionLanguage()),
                normalizeRuntime(manifest.expressionLanguage()), findings);
        requirePolicyEqual("citrus-version", policy.citrusVersion(), manifest.citrusVersion(), findings);
        if (!policy.citrusDependencies().equals(manifest.citrusDependencies())) {
            findings.add(error("approved-policy-citrus-dependencies", null,
                    "Artifact Citrus dependencies differ from the approved requirements policy"));
        }
        if (policy.javaPolicy() != manifest.javaPolicy()) {
            String message = "Artifact java-policy " + manifest.javaPolicy()
                             + " differs from approved value " + policy.javaPolicy();
            findings.add(error("approved-policy-java-policy", null,
                    message));
        }
        if (!new HashSet<>(policy.approvedJavaExceptions())
                .equals(new HashSet<>(manifest.approvedJavaExceptions()))) {
            findings.add(error("approved-policy-java-exceptions", null,
                    "Artifact Java exceptions differ from the approved requirements policy"));
        }
        if (manifest.testsRequired() != policy.citrusTestsRequired()
                || manifest.oneTestPerRoute() != policy.oneCitrusTestPerRoute()) {
            findings.add(error("approved-policy-test-coverage", null,
                    "Artifact test coverage flags differ from the approved requirements policy"));
        }

        Set<String> expectedRoutes = policy.routes().stream()
                .filter(java.util.Objects::nonNull)
                .map(route -> route.routeId() + "\0" + route.routePath())
                .collect(java.util.stream.Collectors.toSet());
        Set<String> actualRoutes = manifest.routes().stream()
                .filter(java.util.Objects::nonNull)
                .map(route -> route.routeId() + "\0" + route.path())
                .collect(java.util.stream.Collectors.toSet());
        if (!expectedRoutes.equals(actualRoutes)) {
            findings.add(error("approved-policy-routes", null,
                    "Artifact routes differ from the exact approved route contracts"));
        }
        Set<String> expectedTests = policy.routes().stream()
                .filter(java.util.Objects::nonNull)
                .map(route -> route.routeId() + "\0" + route.citrusTestPath())
                .collect(java.util.stream.Collectors.toSet());
        Set<String> actualTests = manifest.citrusTests().stream()
                .filter(java.util.Objects::nonNull)
                .map(test -> test.routeId() + "\0" + test.path())
                .collect(java.util.stream.Collectors.toSet());
        if (!expectedTests.equals(actualTests)) {
            findings.add(error("approved-policy-citrus-tests", null,
                    "Artifact Citrus tests differ from the exact approved route contracts"));
        }
    }

    private static void requirePolicyEqual(
            String field, String expected, String actual, List<ArtifactFinding> findings) {
        if (!java.util.Objects.equals(expected, actual)) {
            findings.add(error("approved-policy-" + field, null,
                    "Artifact " + field + " " + actual + " differs from approved value " + expected));
        }
    }

    private static String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String normalizeRuntime(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean safeFileSegment(String value) {
        return value != null && ROUTE_ID.matcher(value).matches();
    }

    private static boolean hasFileName(String value, String expected) {
        try {
            Path name = Path.of(value).getFileName();
            return name != null && expected.equals(name.toString());
        } catch (java.nio.file.InvalidPathException e) {
            return false;
        }
    }

    private static String safeMessage(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    private static boolean isYaml(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".yaml") || name.endsWith(".yml");
    }

    private static String digest(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return "sha256:" + java.util.HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static String digest(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return "sha256:" + java.util.HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static void requireText(String code, String value, List<ArtifactFinding> findings) {
        if (isBlank(value)) {
            findings.add(error(code + "-missing", null, code + " is required"));
        }
    }

    private static ArtifactFinding error(String code, String path, String message) {
        return new ArtifactFinding(Severity.ERROR, code, path, message);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
