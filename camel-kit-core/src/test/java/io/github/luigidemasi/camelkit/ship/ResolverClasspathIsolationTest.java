package io.github.luigidemasi.camelkit.ship;

import java.nio.file.Files;
import java.nio.file.Path;

import io.github.luigidemasi.camelkit.ship.resolver.ShipMavenResolver;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ResolverClasspathIsolationTest {

    @Test
    void productionDependencyExposesOnlyTheCamelKitResolverBoundary() throws Exception {
        ClassLoader loader = ShipMavenResolver.class.getClassLoader();
        String pom = Files.readString(Path.of(System.getProperty("basedir"), "pom.xml"));
        int artifact = pom.indexOf("<artifactId>camel-kit-ship-resolver</artifactId>");
        int start = pom.lastIndexOf("<dependency>", artifact);
        int end = pom.indexOf("</dependency>", artifact);
        String resolverDependency = pom.substring(start, end);

        assertSame(loader, Class.forName(ShipMavenResolver.class.getName(), false, loader).getClassLoader());
        assertTrue(resolverDependency.contains("<groupId>*</groupId>"));
        assertTrue(resolverDependency.contains("<artifactId>*</artifactId>"));
        assertFalse(resolverDependency.contains("<scope>test</scope>"));
    }
}
