package io.github.luigidemasi.camelkit.ship.resolver;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class ResolverIsolationIT {

    private static final String INTERNAL_PREFIX = "io/github/luigidemasi/camelkit/ship/resolver/internal/";
    private static final String MULTI_RELEASE_PREFIX = "META-INF/versions/";
    private static final String RELOCATED_PLEXUS_UTILS = INTERNAL_PREFIX + "org/codehaus/plexus/util/";

    @TempDir
    Path directory;

    @Test
    void shadedJarContainsNoParentVisibleResolverOrMavenClasses() throws Exception {
        Path jar = resolverJar();
        try (JarFile archive = new JarFile(jar.toFile())) {
            Set<String> entries
                    = archive.stream().map(entry -> entry.getName()).collect(java.util.stream.Collectors.toSet());
            assertTrue(entries.stream().anyMatch(name -> name.startsWith(INTERNAL_PREFIX + "org/eclipse/aether/")));
            assertTrue(entries.stream().anyMatch(name -> name.startsWith(INTERNAL_PREFIX + "org/apache/maven/")));
            assertFalse(entries.stream().anyMatch(name -> name.startsWith("org/eclipse/aether/")));
            assertFalse(entries.stream().anyMatch(name -> name.startsWith("org/apache/maven/")));
            assertFalse(entries.stream().anyMatch(name -> name.startsWith("org/sonatype/")));
            assertEquals(
                    Set.of(
                            INTERNAL_PREFIX
                           + "org/eclipse/sisu/Nullable.class",
                            INTERNAL_PREFIX + "org/eclipse/sisu/Typed.class"),
                    entries.stream()
                            .filter(name -> name.startsWith(INTERNAL_PREFIX + "org/eclipse/sisu/"))
                            .filter(name -> name.endsWith(".class"))
                            .collect(java.util.stream.Collectors.toSet()));
            assertFalse(entries.stream().anyMatch(
                    name -> name.startsWith(INTERNAL_PREFIX + "org/eclipse/aether/impl/guice/")));
            assertFalse(entries.stream().anyMatch(
                    name -> name
                            .startsWith(INTERNAL_PREFIX + "org/apache/maven/repository/internal/MavenResolverModule")));
            assertTrue(entries.contains(INTERNAL_PREFIX + "org/slf4j/impl/StaticLoggerBinder.class"));
            assertTrue(entries.contains(INTERNAL_PREFIX + "org/slf4j/impl/StaticMDCBinder.class"));
            assertTrue(entries.contains(INTERNAL_PREFIX + "org/slf4j/impl/StaticMarkerBinder.class"));
            assertEquals(
                    Set.of(),
                    entries.stream()
                            .filter(name -> name.endsWith(".class"))
                            .map(ResolverIsolationIT::logicalEntryName)
                            .filter(name -> !name.startsWith("io/github/luigidemasi/camelkit/ship/resolver/"))
                            .collect(java.util.stream.Collectors.toSet()));
            assertFalse(entries.stream().anyMatch(ResolverIsolationIT::isDoubleShaded));
            assertMultiReleaseClassesAreRelocated(jar, archive, entries);
            assertPublishedPomHasNoRuntimeDependencies(archive);
            assertEmbeddedVersions(archive, "org.apache.maven.resolver", System.getProperty("ship.resolver.version"));
            assertEmbeddedVersions(archive, "org.apache.maven", System.getProperty("ship.maven.provider.version"));
            assertEmbeddedVersions(archive, "org.slf4j", System.getProperty("ship.slf4j.version"));
        }
    }

    @Test
    void incompatibleParentFirstResolverClassesCannotInterfere() throws Exception {
        Path parentClasses = compileIncompatibleParent();
        Path repository = seedLocalRepository();
        try (URLClassLoader parent = new URLClassLoader(
                new URL[]{parentClasses.toUri().toURL()}, ClassLoader.getPlatformClassLoader());
             URLClassLoader isolated = new URLClassLoader(
                     new URL[]{resolverJar().toUri().toURL()}, parent)) {
            assertSame(parent, isolated.loadClass(
                    "org.eclipse.aether.supplier.RepositorySystemSupplier").getClassLoader());
            assertSame(parent, isolated.loadClass(
                    "org.apache.maven.repository.internal.MavenRepositorySystemUtils").getClassLoader());
            Class<?> coordinateType = isolated.loadClass(MavenCoordinate.class.getName());
            Class<?> rootType = isolated.loadClass(MavenDependencyRoot.class.getName());
            Class<?> resolver = isolated.loadClass(ShipMavenResolver.class.getName());
            Object root = rootType.getMethod("jar", String.class, String.class, String.class)
                    .invoke(null, "example.test", "isolated", "1.0.0");
            Object resolved = assertDoesNotThrow(() -> resolver.getMethod("resolve", Path.class, List.class)
                    .invoke(null, repository, List.of(root)));
            assertEquals(3, ((List<?>) resolved).size(), resolved.toString());
            Object artifact = ((List<?>) resolved).get(0);
            assertSame(isolated, artifact.getClass().getClassLoader());
            assertSame(isolated, coordinateType.getClassLoader());

            Class<?> modeType = isolated.loadClass(ShipMavenResolver.ResolutionMode.class.getName());
            Object jar = coordinateType.getMethod("jar", String.class, String.class, String.class)
                    .invoke(null, "example.test", "isolated", "1.0.0");
            Object pom = coordinateType.getMethod("withExtension", String.class).invoke(jar, "pom");
            Object exact = assertDoesNotThrow(() -> resolver
                    .getMethod("resolveArtifacts", Path.class, List.class, modeType)
                    .invoke(null, repository, List.of(pom, jar), modeType.getField("OFFLINE").get(null)));
            assertEquals(2, ((List<?>) exact).size(), exact.toString());
            assertTrue(((List<?>) exact).stream()
                    .allMatch(item -> item.getClass().getClassLoader() == isolated));
        }
    }

    @Test
    void invalidArtifactDescriptorFailsClosed() throws Exception {
        Path repository = seedLocalRepository();
        try (URLClassLoader isolated = new URLClassLoader(
                new URL[]{resolverJar().toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
            Class<?> rootType = isolated.loadClass(MavenDependencyRoot.class.getName());
            Class<?> resolver = isolated.loadClass(ShipMavenResolver.class.getName());
            Object root = rootType.getMethod("jar", String.class, String.class, String.class)
                    .invoke(null, "example.test", "invalid", "1.0.0");
            InvocationTargetException failure = assertThrows(InvocationTargetException.class,
                    () -> resolver.getMethod("resolve", Path.class, List.class)
                            .invoke(null, repository, List.of(root)));
            assertInstanceOf(IOException.class, failure.getCause());
        }
    }

    private Path compileIncompatibleParent() throws IOException {
        Path source = directory.resolve("src/org/eclipse/aether/supplier/RepositorySystemSupplier.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
                package org.eclipse.aether.supplier;
                public final class RepositorySystemSupplier {
                    public RepositorySystemSupplier() {}
                }
                """);
        Path mavenSource = directory.resolve(
                "src/org/apache/maven/repository/internal/MavenRepositorySystemUtils.java");
        Files.createDirectories(mavenSource.getParent());
        Files.writeString(mavenSource, """
                package org.apache.maven.repository.internal;
                public final class MavenRepositorySystemUtils {
                    private MavenRepositorySystemUtils() {}
                }
                """);
        Path classes = Files.createDirectory(directory.resolve("parent"));
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "Isolation test requires a JDK");
        assertEquals(0, compiler.run(
                null, null, null, "-d", classes.toString(), source.toString(), mavenSource.toString()));
        return classes;
    }

    private Path seedLocalRepository() throws IOException {
        seedArtifact("transitive", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example.test</groupId>
                  <artifactId>transitive</artifactId>
                  <version>1.0.0</version>
                </project>
                """);
        seedArtifact("jdk-profiled", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example.test</groupId>
                  <artifactId>jdk-profiled</artifactId>
                  <version>1.0.0</version>
                </project>
                """);
        seedArtifact("isolated", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example.test</groupId>
                  <artifactId>isolated</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>example.test</groupId>
                      <artifactId>transitive</artifactId>
                      <version>1.0.0</version>
                    </dependency>
                  </dependencies>
                  <profiles>
                    <profile>
                      <id>supported-jdk</id>
                      <activation>
                        <jdk>[17,)</jdk>
                      </activation>
                      <dependencies>
                        <dependency>
                          <groupId>example.test</groupId>
                          <artifactId>jdk-profiled</artifactId>
                          <version>1.0.0</version>
                        </dependency>
                      </dependencies>
                    </profile>
                  </profiles>
                </project>
                """);
        seedArtifact("invalid", "<project><not-closed>");
        return directory.resolve("repository");
    }

    private void seedArtifact(String artifactId, String pom) throws IOException {
        Path version = directory.resolve("repository/example/test").resolve(artifactId).resolve("1.0.0");
        Files.createDirectories(version);
        String base = artifactId + "-1.0.0";
        try (JarOutputStream ignored = new JarOutputStream(Files.newOutputStream(version.resolve(base + ".jar")))) {
            // A valid empty JAR is sufficient for dependency-graph resolution.
        }
        Files.writeString(version.resolve(base + ".pom"), pom, StandardCharsets.UTF_8);
        Files.writeString(
                version.resolve("_remote.repositories"),
                base + ".jar>=\n" + base + ".pom>=\n",
                StandardCharsets.UTF_8);
    }

    private static Path resolverJar() {
        Path jar = Path.of(System.getProperty("ship.resolver.jar"));
        assertTrue(Files.isRegularFile(jar), "Missing shaded resolver JAR: " + jar);
        return jar;
    }

    private static boolean isDoubleShaded(String name) {
        int first = name.indexOf(INTERNAL_PREFIX);
        return first >= 0 && name.indexOf(INTERNAL_PREFIX, first + INTERNAL_PREFIX.length()) >= 0;
    }

    private static String logicalEntryName(String name) {
        if (!name.startsWith(MULTI_RELEASE_PREFIX)) {
            return name;
        }
        int versionEnd = name.indexOf('/', MULTI_RELEASE_PREFIX.length());
        return versionEnd < 0 ? name : name.substring(versionEnd + 1);
    }

    private static void assertMultiReleaseClassesAreRelocated(Path jar, JarFile archive, Set<String> entries)
            throws IOException {
        String baseIo = RELOCATED_PLEXUS_UTILS + "BaseIOUtil.class";
        String baseFile = RELOCATED_PLEXUS_UTILS + "BaseFileUtils.class";
        assertTrue(archive.isMultiRelease(), "Shaded resolver must retain multi-release semantics");
        assertTrue(entries.contains(MULTI_RELEASE_PREFIX + "9/" + baseIo));
        assertTrue(entries.contains(MULTI_RELEASE_PREFIX + "10/" + baseIo));
        assertTrue(entries.contains(MULTI_RELEASE_PREFIX + "11/" + baseFile));
        assertFalse(entries.stream()
                .filter(name -> name.startsWith(MULTI_RELEASE_PREFIX))
                .map(ResolverIsolationIT::logicalEntryName)
                .anyMatch(name -> name.startsWith("org/codehaus/")));
        assertFalse(entries.stream().anyMatch(name -> name.endsWith("module-info.class")));

        try (JarFile runtimeArchive = new JarFile(jar.toFile(), true, JarFile.OPEN_READ, Runtime.version())) {
            assertEquals(MULTI_RELEASE_PREFIX + "10/" + baseIo, runtimeArchive.getJarEntry(baseIo).getRealName());
            assertEquals(MULTI_RELEASE_PREFIX + "11/" + baseFile, runtimeArchive.getJarEntry(baseFile).getRealName());
        }
    }

    private static void assertPublishedPomHasNoRuntimeDependencies(JarFile archive) throws IOException {
        String path = "META-INF/maven/io.github.luigidemasi/camel-kit-ship-resolver/pom.xml";
        ZipEntry entry = archive.getEntry(path);
        assertNotNull(entry, "Shaded resolver lacks its published POM");
        String pom = new String(archive.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8);
        int directStart = pom.indexOf("<dependencies>");
        int directEnd = pom.indexOf("</dependencies>", directStart);
        assertTrue(directStart >= 0 && directEnd > directStart, "Published resolver POM lacks dependencies");
        String directDependencies = pom.substring(directStart, directEnd);
        assertFalse(directDependencies.contains("org.apache.maven.resolver"), directDependencies);
        assertFalse(directDependencies.contains("<groupId>org.apache.maven</groupId>"), directDependencies);
        assertFalse(directDependencies.contains("<scope>compile</scope>"), directDependencies);
        assertFalse(directDependencies.contains("<scope>runtime</scope>"), directDependencies);
    }

    private static void assertEmbeddedVersions(JarFile archive, String groupId, String expected) throws IOException {
        assertNotNull(expected, "Expected embedded dependency version was not configured");
        String prefix = "META-INF/maven/" + groupId + '/';
        List<JarEntry> metadata = archive.stream()
                .filter(entry -> entry.getName().startsWith(prefix))
                .filter(entry -> entry.getName().endsWith("/pom.properties"))
                .toList();
        assertFalse(metadata.isEmpty(), "No embedded metadata for " + groupId);
        for (JarEntry entry : metadata) {
            Properties properties = new Properties();
            properties.load(archive.getInputStream(entry));
            assertEquals(expected, properties.getProperty("version"), entry.getName());
        }
    }

}
