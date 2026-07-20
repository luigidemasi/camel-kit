package io.github.luigidemasi.camelkit.ship.resolver;

import java.io.IOException;
import java.lang.reflect.Executable;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

    private static final String API_PACKAGE = "io.github.luigidemasi.camelkit.ship.resolver.";
    private static final String INTERNAL_PACKAGE = API_PACKAGE + "internal.";
    private static final String API_PREFIX = API_PACKAGE.replace('.', '/');
    private static final String INTERNAL_PREFIX = "io/github/luigidemasi/camelkit/ship/resolver/internal/";
    private static final String MULTI_RELEASE_PREFIX = "META-INF/versions/";
    private static final String RELOCATED_PLEXUS_UTILS = INTERNAL_PREFIX + "org/codehaus/plexus/util/";
    private static final Set<String> PUBLIC_CLASSES = Set.of(
            API_PACKAGE + "MavenCoordinate",
            API_PACKAGE + "MavenDependencyExclusion",
            API_PACKAGE + "MavenDependencyRoot",
            API_PACKAGE + "ResolvedExactMavenArtifact",
            API_PACKAGE + "ResolvedMavenArtifact",
            API_PACKAGE + "ShipMavenResolver",
            API_PACKAGE + "ShipMavenResolver$ResolutionMode");
    private static final Set<String> PUBLIC_RECORDS = Set.of(
            API_PACKAGE + "MavenCoordinate",
            API_PACKAGE + "MavenDependencyExclusion",
            API_PACKAGE + "MavenDependencyRoot",
            API_PACKAGE + "ResolvedExactMavenArtifact",
            API_PACKAGE + "ResolvedMavenArtifact");
    private static final Set<String> PUBLIC_ENUMS = Set.of(API_PACKAGE + "ShipMavenResolver$ResolutionMode");
    private static final Map<String, List<String>> PUBLIC_ENUM_CONSTANTS = Map.of(
            API_PACKAGE + "ShipMavenResolver$ResolutionMode", List.of("OFFLINE", "ONLINE"));
    private static final Map<String, Set<String>> PUBLIC_CONSTRUCTORS = Map.ofEntries(
            Map.entry(API_PACKAGE + "MavenCoordinate", Set.of(constructor(
                    "java.lang.String", "java.lang.String", "java.lang.String", "java.lang.String",
                    "java.lang.String"))),
            Map.entry(API_PACKAGE + "MavenDependencyExclusion", Set.of(constructor(
                    "java.lang.String", "java.lang.String"))),
            Map.entry(API_PACKAGE + "MavenDependencyRoot", Set.of(constructor(
                    API_PACKAGE + "MavenCoordinate",
                    "java.util.List<" + API_PACKAGE + "MavenDependencyExclusion>"))),
            Map.entry(API_PACKAGE + "ResolvedExactMavenArtifact", Set.of(constructor(
                    API_PACKAGE + "MavenCoordinate", "java.nio.file.Path", "java.lang.String", "long"))),
            Map.entry(API_PACKAGE + "ResolvedMavenArtifact", Set.of(constructor(
                    API_PACKAGE + "MavenCoordinate", "java.nio.file.Path"))),
            Map.entry(API_PACKAGE + "ShipMavenResolver", Set.of()),
            Map.entry(API_PACKAGE + "ShipMavenResolver$ResolutionMode", Set.of()));
    private static final Map<String, Set<String>> PUBLIC_METHODS = Map.ofEntries(
            Map.entry(API_PACKAGE + "MavenCoordinate", Set.of(
                    staticMethod("jar", API_PACKAGE + "MavenCoordinate",
                            "java.lang.String", "java.lang.String", "java.lang.String"),
                    staticMethod("of", API_PACKAGE + "MavenCoordinate",
                            "java.lang.String", "java.lang.String", "java.lang.String", "java.lang.String"),
                    staticMethod("parseGav", API_PACKAGE + "MavenCoordinate", "java.lang.String"),
                    method("withExtension", API_PACKAGE + "MavenCoordinate", "java.lang.String"),
                    method("gav", "java.lang.String"), method("resolverString", "java.lang.String"),
                    method("fileName", "java.lang.String"), method("toString", "java.lang.String"),
                    method("hashCode", "int"), method("equals", "boolean", "java.lang.Object"),
                    method("groupId", "java.lang.String"), method("artifactId", "java.lang.String"),
                    method("extension", "java.lang.String"), method("classifier", "java.lang.String"),
                    method("version", "java.lang.String"))),
            Map.entry(API_PACKAGE + "MavenDependencyExclusion", Set.of(
                    method("toString", "java.lang.String"), method("hashCode", "int"),
                    method("equals", "boolean", "java.lang.Object"), method("groupId", "java.lang.String"),
                    method("artifactId", "java.lang.String"))),
            Map.entry(API_PACKAGE + "MavenDependencyRoot", Set.of(
                    staticMethod("jar", API_PACKAGE + "MavenDependencyRoot",
                            "java.lang.String", "java.lang.String", "java.lang.String"),
                    method("toString", "java.lang.String"), method("hashCode", "int"),
                    method("equals", "boolean", "java.lang.Object"),
                    method("coordinate", API_PACKAGE + "MavenCoordinate"),
                    method("exclusions", "java.util.List<" + API_PACKAGE + "MavenDependencyExclusion>"))),
            Map.entry(API_PACKAGE + "ResolvedExactMavenArtifact", Set.of(
                    method("toString", "java.lang.String"), method("hashCode", "int"),
                    method("equals", "boolean", "java.lang.Object"),
                    method("coordinate", API_PACKAGE + "MavenCoordinate"),
                    method("path", "java.nio.file.Path"), method("contentSha256", "java.lang.String"),
                    method("contentLength", "long"))),
            Map.entry(API_PACKAGE + "ResolvedMavenArtifact", Set.of(
                    method("toString", "java.lang.String"), method("hashCode", "int"),
                    method("equals", "boolean", "java.lang.Object"),
                    method("coordinate", API_PACKAGE + "MavenCoordinate"), method("path", "java.nio.file.Path"))),
            Map.entry(API_PACKAGE + "ShipMavenResolver", Set.of(
                    staticThrowingMethod("resolve",
                            "java.util.List<" + API_PACKAGE + "ResolvedMavenArtifact>", "java.io.IOException",
                            "java.nio.file.Path", "java.util.List<" + API_PACKAGE + "MavenDependencyRoot>"),
                    staticThrowingMethod("resolveArtifacts",
                            "java.util.List<" + API_PACKAGE + "ResolvedExactMavenArtifact>", "java.io.IOException",
                            "java.nio.file.Path", "java.util.List<" + API_PACKAGE + "MavenCoordinate>",
                            API_PACKAGE + "ShipMavenResolver$ResolutionMode"))),
            Map.entry(API_PACKAGE + "ShipMavenResolver$ResolutionMode", Set.of(
                    staticMethod("values", API_PACKAGE + "ShipMavenResolver$ResolutionMode[]"),
                    staticMethod("valueOf", API_PACKAGE + "ShipMavenResolver$ResolutionMode",
                            "java.lang.String"))));
    private static final Map<String, Set<String>> PUBLIC_FIELDS = Map.ofEntries(
            Map.entry(API_PACKAGE + "MavenCoordinate", Set.of()),
            Map.entry(API_PACKAGE + "MavenDependencyExclusion", Set.of()),
            Map.entry(API_PACKAGE + "MavenDependencyRoot", Set.of(field("MAX_EXCLUSIONS", "int"))),
            Map.entry(API_PACKAGE + "ResolvedExactMavenArtifact", Set.of()),
            Map.entry(API_PACKAGE + "ResolvedMavenArtifact", Set.of()),
            Map.entry(API_PACKAGE + "ShipMavenResolver", Set.of(
                    field("MAX_ROOTS", "int"), field("MAX_ARTIFACTS", "int"))),
            Map.entry(API_PACKAGE + "ShipMavenResolver$ResolutionMode", Set.of(
                    field("OFFLINE", API_PACKAGE + "ShipMavenResolver$ResolutionMode"),
                    field("ONLINE", API_PACKAGE + "ShipMavenResolver$ResolutionMode"))));
    private static final Map<String, Map<String, Object>> PUBLIC_CONSTANT_VALUES = Map.of(
            API_PACKAGE + "MavenDependencyRoot", Map.of("MAX_EXCLUSIONS", 16),
            API_PACKAGE + "ShipMavenResolver", Map.of("MAX_ROOTS", 64, "MAX_ARTIFACTS", 512));

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
    void publicApiExposesOnlyJdkAndCamelKitTypes() throws Exception {
        List<String> apiClasses;
        try (JarFile archive = new JarFile(resolverJar().toFile())) {
            apiClasses = archive.stream()
                    .map(JarEntry::getName)
                    .filter(name -> name.startsWith(API_PREFIX))
                    .filter(name -> !name.startsWith(INTERNAL_PREFIX))
                    .filter(name -> name.endsWith(".class"))
                    .map(name -> name.substring(0, name.length() - ".class".length()).replace('/', '.'))
                    .sorted()
                    .toList();
        }

        Set<String> publicClasses = new HashSet<>();
        try (URLClassLoader isolated = new URLClassLoader(
                new URL[]{resolverJar().toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
            for (String className : apiClasses) {
                Class<?> apiClass = isolated.loadClass(className);
                if (!Modifier.isPublic(apiClass.getModifiers())) {
                    continue;
                }
                publicClasses.add(className);
                assertTrue(Modifier.isFinal(apiClass.getModifiers()), className + " must remain final");
                assertEquals(PUBLIC_RECORDS.contains(className), apiClass.isRecord(), className);
                assertEquals(PUBLIC_ENUMS.contains(className), apiClass.isEnum(), className);
                if (apiClass.isEnum()) {
                    assertEquals(PUBLIC_ENUM_CONSTANTS.get(className), Arrays.stream(apiClass.getEnumConstants())
                            .map(value -> ((Enum<?>) value).name()).toList(), className + " enum order changed");
                }
                assertEquals(apiClass.isRecord() ? Record.class : apiClass.isEnum() ? Enum.class : Object.class,
                        apiClass.getSuperclass(), className + " superclass changed");
                assertEquals(0, apiClass.getTypeParameters().length, className + " added type parameters");
                assertEquals(Set.of(), Arrays.stream(apiClass.getGenericInterfaces())
                        .map(Type::getTypeName).collect(java.util.stream.Collectors.toSet()),
                        className + " interfaces changed");
                if (apiClass.getEnclosingClass() != null) {
                    assertTrue(Modifier.isStatic(apiClass.getModifiers()), className + " must remain static");
                }
                assertConsumerType(apiClass.getGenericSuperclass(), apiClass.getName(), new HashSet<>());
                for (Type interfaceType : apiClass.getGenericInterfaces()) {
                    assertConsumerType(interfaceType, apiClass.getName(), new HashSet<>());
                }
                for (TypeVariable<?> variable : apiClass.getTypeParameters()) {
                    assertConsumerType(variable, apiClass.getName(), new HashSet<>());
                }
                Set<String> constructors = new HashSet<>();
                for (var constructor : apiClass.getDeclaredConstructors()) {
                    if (Modifier.isPublic(constructor.getModifiers())) {
                        constructors.add(constructor(constructor.getGenericParameterTypes()));
                    }
                    assertExecutableTypes(constructor);
                }
                assertEquals(PUBLIC_CONSTRUCTORS.get(className), constructors, className + " constructors changed");
                Set<String> methods = new HashSet<>();
                for (var method : apiClass.getDeclaredMethods()) {
                    if (Modifier.isPublic(method.getModifiers())) {
                        methods.add(method(method));
                        assertConsumerType(method.getGenericReturnType(), method.toGenericString(), new HashSet<>());
                    }
                    assertExecutableTypes(method);
                }
                assertEquals(PUBLIC_METHODS.get(className), methods, className + " methods changed");
                Set<String> fields = new HashSet<>();
                for (var field : apiClass.getDeclaredFields()) {
                    if (Modifier.isPublic(field.getModifiers())) {
                        assertTrue(Modifier.isStatic(field.getModifiers()), field.toGenericString());
                        assertTrue(Modifier.isFinal(field.getModifiers()), field.toGenericString());
                        fields.add(field(field.getName(), field.getGenericType().getTypeName()));
                        assertConsumerType(field.getGenericType(), field.toGenericString(), new HashSet<>());
                    }
                }
                assertEquals(PUBLIC_FIELDS.get(className), fields, className + " fields changed");
                for (var constant : PUBLIC_CONSTANT_VALUES.getOrDefault(className, Map.of()).entrySet()) {
                    assertEquals(constant.getValue(), apiClass.getField(constant.getKey()).get(null),
                            className + '.' + constant.getKey() + " value changed");
                }
                if (apiClass.isRecord()) {
                    for (var component : apiClass.getRecordComponents()) {
                        assertConsumerType(
                                component.getGenericType(),
                                apiClass.getName() + '.' + component.getName(),
                                new HashSet<>());
                    }
                }
            }
        }
        assertEquals(PUBLIC_CLASSES, publicClasses,
                "Resolver public API changed without an explicit isolation-boundary update");
    }

    private static String constructor(String... parameterTypes) {
        return "<init>(" + String.join(",", parameterTypes) + ')';
    }

    private static String constructor(Type... parameterTypes) {
        return constructor(Arrays.stream(parameterTypes).map(Type::getTypeName).toArray(String[]::new));
    }

    private static String method(String name, String returnType, String... parameterTypes) {
        return name + '(' + String.join(",", parameterTypes) + ")->" + returnType;
    }

    private static String staticMethod(String name, String returnType, String... parameterTypes) {
        return "static " + method(name, returnType, parameterTypes);
    }

    private static String staticThrowingMethod(
            String name, String returnType, String exceptionType, String... parameterTypes) {
        return staticMethod(name, returnType, parameterTypes) + " throws " + exceptionType;
    }

    private static String method(java.lang.reflect.Method method) {
        String signature = (Modifier.isStatic(method.getModifiers()) ? "static " : "")
                           + method(method.getName(), method.getGenericReturnType().getTypeName(),
                                   Arrays.stream(method.getGenericParameterTypes())
                                           .map(Type::getTypeName).toArray(String[]::new));
        String[] exceptions = Arrays.stream(method.getGenericExceptionTypes())
                .map(Type::getTypeName).toArray(String[]::new);
        return exceptions.length == 0 ? signature : signature + " throws " + String.join(",", exceptions);
    }

    private static String field(String name, String type) {
        return name + ':' + type;
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

    private static void assertExecutableTypes(Executable executable) {
        if (!Modifier.isPublic(executable.getModifiers())) {
            return;
        }
        for (Type parameter : executable.getGenericParameterTypes()) {
            assertConsumerType(parameter, executable.toGenericString(), new HashSet<>());
        }
        for (Type exception : executable.getGenericExceptionTypes()) {
            assertConsumerType(exception, executable.toGenericString(), new HashSet<>());
        }
        for (TypeVariable<?> variable : executable.getTypeParameters()) {
            assertConsumerType(variable, executable.toGenericString(), new HashSet<>());
        }
    }

    private static void assertConsumerType(Type type, String owner, Set<Type> visited) {
        if (type == null || !visited.add(type)) {
            return;
        }
        if (type instanceof Class<?> exposed) {
            if (exposed.isArray()) {
                assertConsumerType(exposed.getComponentType(), owner, visited);
                return;
            }
            if (exposed.isPrimitive()) {
                return;
            }
            String module = exposed.getModule().getName();
            boolean jdkType = module != null && (module.startsWith("java.") || module.startsWith("jdk."));
            boolean camelKitType = exposed.getName().startsWith(API_PACKAGE)
                    && !exposed.getName().startsWith(INTERNAL_PACKAGE);
            assertTrue(jdkType || camelKitType, () -> owner + " exposes non-consumer type " + exposed.getName());
            return;
        }
        if (type instanceof ParameterizedType parameterized) {
            assertConsumerType(parameterized.getRawType(), owner, visited);
            assertConsumerType(parameterized.getOwnerType(), owner, visited);
            for (Type argument : parameterized.getActualTypeArguments()) {
                assertConsumerType(argument, owner, visited);
            }
            return;
        }
        if (type instanceof GenericArrayType array) {
            assertConsumerType(array.getGenericComponentType(), owner, visited);
            return;
        }
        if (type instanceof TypeVariable<?> variable) {
            for (Type bound : variable.getBounds()) {
                assertConsumerType(bound, owner, visited);
            }
            return;
        }
        if (type instanceof WildcardType wildcard) {
            for (Type bound : wildcard.getLowerBounds()) {
                assertConsumerType(bound, owner, visited);
            }
            for (Type bound : wildcard.getUpperBounds()) {
                assertConsumerType(bound, owner, visited);
            }
            return;
        }
        fail(owner + " exposes unsupported type " + type.getTypeName());
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
