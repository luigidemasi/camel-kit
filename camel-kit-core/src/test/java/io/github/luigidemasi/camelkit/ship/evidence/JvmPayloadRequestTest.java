package io.github.luigidemasi.camelkit.ship.evidence;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;

import io.github.luigidemasi.camelkit.ship.artifact.CitrusDependencyPolicy;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogUsageRecord.RuntimeDependency;
import io.github.luigidemasi.camelkit.ship.evidence.JvmPayloadRequest.DependencyExclusion;
import io.github.luigidemasi.camelkit.ship.evidence.JvmPayloadRequest.DependencyRoot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JvmPayloadRequestTest {

    private static final Properties POLICY = packagedPolicy();
    private static final String CAMEL_VERSION = required("camel.main.version");
    private static final String SECONDARY_CAMEL_VERSION = List.of(
            required("ship.evidence.camel-yaml-validator.supported").split(","))
            .stream()
            .map(String::trim)
            .filter(version -> !CAMEL_VERSION.equals(version))
            .findFirst()
            .orElseThrow();
    private static final String CITRUS_VERSION = required("citrus.version");

    @Test
    void selectsOnlyControllerOwnedDirectLaunchersAndExactRoots() {
        JvmPayloadRequest validator = JvmPayloadRequest.yamlValidator(CAMEL_VERSION);
        JvmPayloadRequest main = JvmPayloadRequest.camelMain(CAMEL_VERSION);
        JvmPayloadRequest citrus = JvmPayloadRequest.citrus(
                CAMEL_VERSION, CITRUS_VERSION, CitrusDependencyPolicy.required(CITRUS_VERSION));

        assertEquals(JvmPayloadRequest.Kind.CAMEL_YAML_VALIDATE, validator.kind());
        assertEquals(JvmPayloadRequest.Kind.CAMEL_MAIN_START, main.kind());
        assertEquals(JvmPayloadRequest.Kind.CITRUS_YAML, citrus.kind());
        assertTrue(citrus.roots().contains("org.citrusframework:citrus-junit-jupiter:" + CITRUS_VERSION));
        assertTrue(citrus.roots().contains("org.citrusframework:citrus-yaml:" + CITRUS_VERSION));
        assertTrue(citrus.roots().contains("org.citrusframework:citrus-camel:" + CITRUS_VERSION));
        assertTrue(main.roots().contains("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:"
                                         + required("ship.evidence.jackson-yaml.version")));
        assertTrue(main.roots().contains("org.yaml:snakeyaml:" + required("ship.evidence.snakeyaml.version")));
        assertTrue(validator.roots().contains("com.networknt:json-schema-validator:"
                                              + required("ship.evidence.camel-yaml-validator." + CAMEL_VERSION
                                                         + ".json-schema-validator.version")));
        assertTrue(validator.roots().contains("com.ethlo.time:itu:"
                                              + required("ship.evidence.camel-yaml-validator.networknt-itu.version")));
        assertTrue(validator.roots().contains("org.slf4j:slf4j-api:"
                                              + required(
                                                      "ship.evidence.camel-yaml-validator.networknt-slf4j.version")));
        assertTrue(JvmPayloadRequest.yamlValidator(SECONDARY_CAMEL_VERSION).roots()
                .contains("com.networknt:json-schema-validator:"
                          + required("ship.evidence.camel-yaml-validator." + SECONDARY_CAMEL_VERSION
                                     + ".json-schema-validator.version")));
        assertTrue(citrus.roots().contains("org.apache.camel:camel-core:" + CAMEL_VERSION));
        assertTrue(citrus.roots().contains("org.apache.camel:camel-spring:" + CAMEL_VERSION));
        assertTrue(citrus.dependencyRoots().stream()
                .filter(root -> "org.citrusframework".equals(root.groupId()))
                .allMatch(root -> root.exclusions().equals(List.of(
                        new DependencyExclusion("org.apache.camel", "*")))));
        assertFalse(citrus.roots().stream().anyMatch(root -> root.contains("testcontainers")));
        assertFalse(citrus.roots().stream().anyMatch(root -> root.endsWith(":4.20.0")));
        assertNotEquals(validator.digest(), main.digest());
        assertNotEquals(main.digest(), citrus.digest());
    }

    @Test
    void refusesDependencyDriftAliasesSnapshotsAndDuplicateRoots() {
        assertThrows(IllegalArgumentException.class, () -> JvmPayloadRequest.citrus(
                CAMEL_VERSION, CITRUS_VERSION, List.of(
                        "org.citrusframework:citrus-camel:" + CITRUS_VERSION,
                        "org.citrusframework:citrus-yaml:" + CITRUS_VERSION)));
        assertThrows(IllegalArgumentException.class, () -> JvmPayloadRequest.yamlValidator("LATEST"));
        assertThrows(IllegalArgumentException.class,
                () -> JvmPayloadRequest.yamlValidator(CAMEL_VERSION + "-SNAPSHOT"));
        assertThrows(IllegalArgumentException.class, () -> new JvmPayloadRequest(
                JvmPayloadRequest.Kind.CAMEL_MAIN_START, CAMEL_VERSION, null, "example.Launcher",
                List.of(
                        new DependencyRoot("g", "a", "1", List.of()),
                        new DependencyRoot("g", "a", "1", List.of()))));
    }

    @Test
    void failsClosedBeforeResolutionOutsideThePackagedCompatibilityMatrix() {
        assertDoesNotThrow(() -> JvmPayloadRequest.yamlValidator(CAMEL_VERSION));
        assertDoesNotThrow(() -> JvmPayloadRequest.yamlValidator(SECONDARY_CAMEL_VERSION));
        assertThrows(IllegalArgumentException.class, () -> JvmPayloadRequest.yamlValidator("4.14.7"));
        assertThrows(IllegalArgumentException.class, () -> JvmPayloadRequest.camelMain("4.14.7"));
        assertThrows(IllegalArgumentException.class, () -> JvmPayloadRequest.citrus(
                CAMEL_VERSION, "4.9.2", CitrusDependencyPolicy.required("4.9.2")));
    }

    @Test
    void refusesARequestThatChangesTheControllerOwnedExclusionPolicy() {
        JvmPayloadRequest citrus = JvmPayloadRequest.citrus(
                CAMEL_VERSION, CITRUS_VERSION, CitrusDependencyPolicy.required(CITRUS_VERSION));
        List<DependencyRoot> changed = citrus.dependencyRoots().stream()
                .map(root -> "citrus-camel".equals(root.artifactId())
                        ? new DependencyRoot(root.groupId(), root.artifactId(), root.version(), List.of())
                        : root)
                .toList();

        assertThrows(IllegalArgumentException.class, () -> new JvmPayloadRequest(
                citrus.kind(), citrus.camelVersion(), citrus.citrusVersion(), citrus.launcherClass(), changed));
    }

    @Test
    void authenticatedRuntimeRootsExtendMainAndCitrusPayloadsExactly() {
        List<RuntimeDependency> runtime = List.of(
                new RuntimeDependency("org.apache.camel", "camel-main", CAMEL_VERSION, "compile"),
                new RuntimeDependency("org.apache.camel", "camel-yaml-dsl", CAMEL_VERSION, "compile"),
                new RuntimeDependency("org.apache.camel", "camel-direct", CAMEL_VERSION, "compile"),
                new RuntimeDependency("org.apache.camel", "camel-csv", CAMEL_VERSION, "runtime"));

        JvmPayloadRequest fixed = JvmPayloadRequest.camelMain(CAMEL_VERSION);
        JvmPayloadRequest main = JvmPayloadRequest.camelMain(CAMEL_VERSION, runtime);
        JvmPayloadRequest citrus = JvmPayloadRequest.citrus(
                CAMEL_VERSION, CITRUS_VERSION, CitrusDependencyPolicy.required(CITRUS_VERSION), runtime);

        assertTrue(main.roots().contains("org.apache.camel:camel-csv:" + CAMEL_VERSION));
        assertTrue(citrus.roots().contains("org.apache.camel:camel-csv:" + CAMEL_VERSION));
        assertEquals(1, main.roots().stream()
                .filter(("org.apache.camel:camel-direct:" + CAMEL_VERSION)::equals).count());
        assertNotEquals(fixed.digest(), main.digest());
        assertTrue(citrus.dependencyRoots().stream()
                .filter(root -> "org.citrusframework".equals(root.groupId()))
                .allMatch(root -> root.exclusions().equals(List.of(
                        new DependencyExclusion("org.apache.camel", "*")))));
    }

    @Test
    void runtimeRootsCannotConflictWithCoreOrIntroduceUnverifiedGroups() {
        assertThrows(IllegalArgumentException.class, () -> JvmPayloadRequest.camelMain(
                CAMEL_VERSION, List.of(new RuntimeDependency(
                        "org.apache.camel", "camel-main", "9.9.8", "compile"))));
        assertThrows(IllegalArgumentException.class, () -> JvmPayloadRequest.camelMain(
                CAMEL_VERSION, List.of(new RuntimeDependency(
                        "com.example", "unverified", "1.0.0", "compile"))));
    }

    @Test
    void springBootAndQuarkusBuildsFailClosedInsteadOfFallingBackToMaven() {
        JvmPayloadRequest spring = JvmPayloadRequest.runtimeBuild("spring-boot", CAMEL_VERSION);
        JvmPayloadRequest quarkus = JvmPayloadRequest.runtimeBuild("quarkus", required("camel.quarkus.version"));

        assertFalse(spring.kind().supported());
        assertFalse(quarkus.kind().supported());
        assertNull(spring.launcherClass());
        assertNull(quarkus.launcherClass());
        assertTrue(spring.roots().isEmpty());
        assertTrue(quarkus.roots().isEmpty());
    }

    private static Properties packagedPolicy() {
        Properties properties = new Properties();
        try (InputStream input = JvmPayloadRequestTest.class.getClassLoader()
                .getResourceAsStream("distribution.properties")) {
            if (input == null) {
                throw new IllegalStateException("Missing packaged distribution.properties");
            }
            properties.load(input);
            return properties;
        } catch (IOException e) {
            throw new IllegalStateException("Could not read packaged distribution.properties", e);
        }
    }

    private static String required(String key) {
        String value = POLICY.getProperty(key);
        if (value == null) {
            throw new IllegalStateException("Missing packaged distribution property " + key);
        }
        return value;
    }
}
