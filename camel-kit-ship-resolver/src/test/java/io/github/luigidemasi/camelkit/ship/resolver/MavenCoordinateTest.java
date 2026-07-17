package io.github.luigidemasi.camelkit.ship.resolver;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MavenCoordinateTest {

    @Test
    void formatsGavResolverAndFileNamesWithoutCamelTooling() {
        MavenCoordinate plain = MavenCoordinate.parseGav("org.apache.camel:camel-main:4.18.3");
        MavenCoordinate classified = new MavenCoordinate(
                "org.example", "artifact", "jar", "tests", "1.2.3");

        assertEquals("org.apache.camel:camel-main:4.18.3", plain.gav());
        assertEquals("org.apache.camel:camel-main:jar:4.18.3", plain.resolverString());
        assertEquals("camel-main-4.18.3.jar", plain.fileName());
        assertEquals("org.example:artifact:jar:tests:1.2.3", classified.resolverString());
        assertEquals("artifact-1.2.3-tests.jar", classified.fileName());
        assertEquals("org.apache.camel:camel-main:pom:4.18.3", plain.withExtension("pom").resolverString());
    }

    @Test
    void rejectsMutableOrUnsafeCoordinates() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> MavenCoordinate.parseGav("org.apache.camel:camel-main")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> MavenCoordinate.jar("../org", "camel-main", "4.18.3")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> MavenCoordinate.jar("org.apache.camel", "camel-main", "4.18.3-SNAPSHOT")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> MavenCoordinate.jar("org.apache.camel", "camel-main", "4.18.3-20260101.010101-1")));
    }

    @Test
    void dependencyRootsSortAndRetainExactExclusions() {
        MavenDependencyRoot root = new MavenDependencyRoot(
                MavenCoordinate.jar("org.apache.camel", "camel-main", "4.18.3"),
                java.util.List.of(
                        new MavenDependencyExclusion("org.slf4j", "*"),
                        new MavenDependencyExclusion("com.example", "legacy")));

        assertEquals("com.example", root.exclusions().get(0).groupId());
        assertEquals("org.slf4j", root.exclusions().get(1).groupId());
        assertThrows(IllegalArgumentException.class, () -> new MavenDependencyRoot(
                MavenCoordinate.jar("org.apache.camel", "camel-main", "4.18.3"),
                java.util.List.of(
                        new MavenDependencyExclusion("org.slf4j", "*"),
                        new MavenDependencyExclusion("org.slf4j", "*"))));
    }
}
