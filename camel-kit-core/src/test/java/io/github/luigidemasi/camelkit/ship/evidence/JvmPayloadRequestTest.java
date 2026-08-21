package io.github.luigidemasi.camelkit.ship.evidence;

import java.util.List;

import io.github.luigidemasi.camelkit.ship.artifact.CitrusDependencyPolicy;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogUsageRecord.RuntimeDependency;
import io.github.luigidemasi.camelkit.ship.evidence.JvmPayloadRequest.DependencyExclusion;
import io.github.luigidemasi.camelkit.ship.evidence.JvmPayloadRequest.DependencyRoot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JvmPayloadRequestTest {

    @Test
    void selectsOnlyControllerOwnedDirectLaunchersAndExactRoots() {
        JvmPayloadRequest validator = JvmPayloadRequest.yamlValidator("4.21.0");
        JvmPayloadRequest main = JvmPayloadRequest.camelMain("4.21.0");
        JvmPayloadRequest citrus = JvmPayloadRequest.citrus(
                "4.21.0", "5.0.0-M2", CitrusDependencyPolicy.required("5.0.0-M2"));

        assertEquals(JvmPayloadRequest.Kind.CAMEL_YAML_VALIDATE, validator.kind());
        assertEquals(JvmPayloadRequest.Kind.CAMEL_MAIN_START, main.kind());
        assertEquals(JvmPayloadRequest.Kind.CITRUS_YAML, citrus.kind());
        assertTrue(citrus.roots().contains("org.citrusframework:citrus-junit-jupiter:5.0.0-M2"));
        assertTrue(citrus.roots().contains("org.citrusframework:citrus-yaml:5.0.0-M2"));
        assertTrue(citrus.roots().contains("org.citrusframework:citrus-camel:5.0.0-M2"));
        assertTrue(main.roots().contains("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.19.2"));
        assertTrue(main.roots().contains("org.yaml:snakeyaml:2.4"));
        assertTrue(validator.roots().contains("com.networknt:json-schema-validator:2.0.1"));
        assertTrue(validator.roots().contains("com.ethlo.time:itu:1.14.0"));
        assertTrue(validator.roots().contains("org.slf4j:slf4j-api:2.0.17"));
        assertTrue(JvmPayloadRequest.yamlValidator("4.18.3").roots()
                .contains("com.networknt:json-schema-validator:1.5.9"));
        assertTrue(citrus.roots().contains("org.apache.camel:camel-core:4.21.0"));
        assertTrue(citrus.roots().contains("org.apache.camel:camel-spring:4.21.0"));
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
                "4.21.0", "5.0.0-M2", List.of(
                        "org.citrusframework:citrus-camel:5.0.0-M2",
                        "org.citrusframework:citrus-yaml:5.0.0-M2")));
        assertThrows(IllegalArgumentException.class, () -> JvmPayloadRequest.yamlValidator("LATEST"));
        assertThrows(IllegalArgumentException.class, () -> JvmPayloadRequest.yamlValidator("4.21.0-SNAPSHOT"));
        assertThrows(IllegalArgumentException.class, () -> new JvmPayloadRequest(
                JvmPayloadRequest.Kind.CAMEL_MAIN_START, "4.21.0", null, "example.Launcher",
                List.of(
                        new DependencyRoot("g", "a", "1", List.of()),
                        new DependencyRoot("g", "a", "1", List.of()))));
    }

    @Test
    void failsClosedBeforeResolutionOutsideThePackagedCompatibilityMatrix() {
        assertDoesNotThrow(() -> JvmPayloadRequest.yamlValidator("4.21.0"));
        assertDoesNotThrow(() -> JvmPayloadRequest.yamlValidator("4.18.3"));
        assertThrows(IllegalArgumentException.class, () -> JvmPayloadRequest.yamlValidator("4.14.7"));
        assertThrows(IllegalArgumentException.class, () -> JvmPayloadRequest.camelMain("4.14.7"));
        assertThrows(IllegalArgumentException.class, () -> JvmPayloadRequest.citrus(
                "4.21.0", "4.9.2", CitrusDependencyPolicy.required("4.9.2")));
    }

    @Test
    void refusesARequestThatChangesTheControllerOwnedExclusionPolicy() {
        JvmPayloadRequest citrus = JvmPayloadRequest.citrus(
                "4.21.0", "5.0.0-M2", CitrusDependencyPolicy.required("5.0.0-M2"));
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
                new RuntimeDependency("org.apache.camel", "camel-main", "4.21.0", "compile"),
                new RuntimeDependency("org.apache.camel", "camel-yaml-dsl", "4.21.0", "compile"),
                new RuntimeDependency("org.apache.camel", "camel-direct", "4.21.0", "compile"),
                new RuntimeDependency("org.apache.camel", "camel-csv", "4.21.0", "runtime"));

        JvmPayloadRequest fixed = JvmPayloadRequest.camelMain("4.21.0");
        JvmPayloadRequest main = JvmPayloadRequest.camelMain("4.21.0", runtime);
        JvmPayloadRequest citrus = JvmPayloadRequest.citrus(
                "4.21.0", "5.0.0-M2", CitrusDependencyPolicy.required("5.0.0-M2"), runtime);

        assertTrue(main.roots().contains("org.apache.camel:camel-csv:4.21.0"));
        assertTrue(citrus.roots().contains("org.apache.camel:camel-csv:4.21.0"));
        assertEquals(1, main.roots().stream()
                .filter("org.apache.camel:camel-direct:4.21.0"::equals).count());
        assertNotEquals(fixed.digest(), main.digest());
        assertTrue(citrus.dependencyRoots().stream()
                .filter(root -> "org.citrusframework".equals(root.groupId()))
                .allMatch(root -> root.exclusions().equals(List.of(
                        new DependencyExclusion("org.apache.camel", "*")))));
    }

    @Test
    void runtimeRootsCannotConflictWithCoreOrIntroduceUnverifiedGroups() {
        assertThrows(IllegalArgumentException.class, () -> JvmPayloadRequest.camelMain(
                "4.21.0", List.of(new RuntimeDependency(
                        "org.apache.camel", "camel-main", "4.20.0", "compile"))));
        assertThrows(IllegalArgumentException.class, () -> JvmPayloadRequest.camelMain(
                "4.21.0", List.of(new RuntimeDependency(
                        "com.example", "unverified", "1.0.0", "compile"))));
    }

    @Test
    void springBootAndQuarkusBuildsFailClosedInsteadOfFallingBackToMaven() {
        JvmPayloadRequest spring = JvmPayloadRequest.runtimeBuild("spring-boot", "4.21.0");
        JvmPayloadRequest quarkus = JvmPayloadRequest.runtimeBuild("quarkus", "4.18.2");

        assertFalse(spring.kind().supported());
        assertFalse(quarkus.kind().supported());
        assertNull(spring.launcherClass());
        assertNull(quarkus.launcherClass());
        assertTrue(spring.roots().isEmpty());
        assertTrue(quarkus.roots().isEmpty());
    }
}
