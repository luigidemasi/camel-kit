package io.github.luigidemasi.camelkit.ship.resolver;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
                        () -> MavenCoordinate.jar("org.apache.camel", "camel-main", "4.18.3-20260101.010101-1")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> MavenCoordinate.jar("org.apache.camel", "camel-main", "LATEST")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> MavenCoordinate.jar("org.apache.camel", "camel-main", "RELEASE")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> MavenCoordinate.jar("org.apache.camel", "camel..main", "4.18.3")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> MavenCoordinate.of("org.apache.camel", "camel-main", "4.18.3", ".jar")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new MavenCoordinate("org.apache.camel", "camel-main", "jar", "tests.", "4.18.3")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new MavenCoordinate("org.apache.camel", "camel-main", "jar", "tests/evil", "4.18.3")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> MavenCoordinate.jar("org.apache.camel", "camel-main", "4..18.3")));
    }

    @Test
    void rejectsNullRequiredPartsAndNormalizesNullClassifier() {
        assertAll(
                () -> assertThrows(NullPointerException.class,
                        () -> new MavenCoordinate(null, "camel-main", "jar", "", "4.18.3")),
                () -> assertThrows(NullPointerException.class,
                        () -> new MavenCoordinate("org.apache.camel", null, "jar", "", "4.18.3")),
                () -> assertThrows(NullPointerException.class,
                        () -> new MavenCoordinate("org.apache.camel", "camel-main", null, "", "4.18.3")),
                () -> assertThrows(NullPointerException.class,
                        () -> new MavenCoordinate("org.apache.camel", "camel-main", "jar", "", null)),
                () -> assertThrows(IllegalArgumentException.class, () -> MavenCoordinate.parseGav(null)),
                () -> assertEquals(
                        "",
                        new MavenCoordinate("org.apache.camel", "camel-main", "jar", null, "4.18.3")
                                .classifier()));
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

        List<MavenDependencyExclusion> tooMany = IntStream.rangeClosed(0, MavenDependencyRoot.MAX_EXCLUSIONS)
                .mapToObj(index -> new MavenDependencyExclusion("org.example", "artifact-" + index))
                .toList();
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> new MavenDependencyRoot(
                MavenCoordinate.jar("org.apache.camel", "camel-main", "4.18.3"), tooMany));
        assertEquals(
                "A dependency root may contain at most " + MavenDependencyRoot.MAX_EXCLUSIONS + " unique exclusions",
                failure.getMessage());
    }

    @Test
    void resolverRejectsRootCountAboveDocumentedLimit(@TempDir Path repository) {
        List<MavenDependencyRoot> roots = IntStream.rangeClosed(0, ShipMavenResolver.MAX_ROOTS)
                .mapToObj(index -> MavenDependencyRoot.jar("org.example", "artifact-" + index, "1.0.0"))
                .toList();

        IOException failure = assertThrows(IOException.class, () -> ShipMavenResolver.resolve(repository, roots));
        assertEquals(
                "Ship resolver requires between 1 and " + ShipMavenResolver.MAX_ROOTS + " unique roots",
                failure.getMessage());
    }
}
