package io.github.luigidemasi.camelkit.ship;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.net.URI;
import java.nio.channels.Channel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.spi.ToolProvider;

import io.github.luigidemasi.camelkit.ship.context.ContextFilesystemPolicy;
import io.github.luigidemasi.camelkit.ship.context.ContextResolution;
import io.github.luigidemasi.camelkit.ship.context.InitialContext;
import io.github.luigidemasi.camelkit.ship.security.ProjectContextFiles;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipFoundationBoundaryTest {

    private static final String CONTEXT_PACKAGE = "io.github.luigidemasi.camelkit.ship.context.";
    private static final String SECURITY_PACKAGE = "io.github.luigidemasi.camelkit.ship.security.";
    private static final List<String> FORBIDDEN_REFERENCES = List.of(
            "io.github.luigidemasi.camelkit.ship.controller.",
            "io.github.luigidemasi.camelkit.ship.protocol.",
            "io.github.luigidemasi.camelkit.ship.persistence.",
            "io.github.luigidemasi.camelkit.ship.state.",
            "io.github.luigidemasi.camelkit.ship.store.",
            "io.github.luigidemasi.camelkit.ship.cli.",
            "io.github.luigidemasi.camelkit.ship.command.",
            "io.github.luigidemasi.camelkit.ship.worker.",
            "io.github.luigidemasi.camelkit.ship.adapter.",
            "io.github.luigidemasi.camelkit.ship.process.",
            "io.github.luigidemasi.camelkit.ship.network.",
            "com.fasterxml.",
            "org.springframework.",
            "io.quarkus.");
    private static final List<String> FORBIDDEN_NETWORK_CLASSES = List.of(
            "java.nio.channels.AsynchronousServerSocketChannel",
            "java.nio.channels.AsynchronousSocketChannel",
            "java.nio.channels.DatagramChannel",
            "java.nio.channels.MulticastChannel",
            "java.nio.channels.NetworkChannel",
            "java.nio.channels.SelectableChannel",
            "java.nio.channels.SelectionKey",
            "java.nio.channels.Selector",
            "java.nio.channels.ServerSocketChannel",
            "java.nio.channels.SocketChannel",
            "java.nio.channels.spi.AbstractSelectableChannel",
            "java.nio.channels.spi.SelectorProvider");
    private static final Set<String> PROJECT_CONTEXT_METHODS = Set.of(
            "static hold(" + CONTEXT_PACKAGE + "ContextFilesystemPolicy$ProjectRootAdmission)->"
                                                                      + SECURITY_PACKAGE
                                                                      + "ProjectContextFiles$HeldRoot throws java.io.IOException",
            "static open(" + CONTEXT_PACKAGE + "ContextFilesystemPolicy$ProjectAccess)->"
                                                                                                                                   + SECURITY_PACKAGE
                                                                                                                                   + "ProjectContextFiles throws java.io.IOException",
            "readDocument(java.lang.String,int)->byte[] throws java.io.IOException",
            "close()->void throws java.io.IOException");
    private static final Set<String> PROJECT_ACCESS_METHODS = Set.of(
            "canonicalRoot()->java.nio.file.Path",
            "requireContextPath(java.lang.String)->java.lang.String throws "
                                                   + CONTEXT_PACKAGE + "InitialContextException",
            "transferHeldRoot()->" + SECURITY_PACKAGE
                                                                                                  + "ProjectContextFiles$HeldRoot throws java.io.IOException",
            "close()->void throws " + CONTEXT_PACKAGE + "InitialContextException",
            "toString()->java.lang.String");
    private static final Set<String> PROJECT_ROOT_ADMISSION_METHODS = Set.of(
            "canonicalRoot()->java.nio.file.Path",
            "toString()->java.lang.String");
    private static final Set<String> HELD_ROOT_METHODS = Set.of(
            "close()->void throws java.io.IOException",
            "toString()->java.lang.String");
    private static final List<String> FORBIDDEN_CONTENT_OPEN_REFERENCES = List.of(
            "FileInputStream",
            "RandomAccessFile",
            "newInputStream",
            "newByteChannel",
            "newBufferedReader",
            "readAllBytes",
            "readString",
            "readAllLines",
            "Files.lines",
            "Files.copy",
            "FileChannel.open",
            "AsynchronousFileChannel.open");
    private static final Set<String> ALLOWED_CONTEXT_FILES_METHODS = Set.of(
            "exists",
            "isDirectory",
            "isSymbolicLink",
            "newDirectoryStream",
            "readAttributes");
    private static final Set<String> ALLOWED_CONTEXT_JAVA_IO_TYPES = Set.of(
            "FileNotFoundException", "IOException");
    private static final Pattern FILES_METHOD_REFERENCE = Pattern.compile(
            "java/nio/file/Files\\.([A-Za-z0-9_$]+)");
    private static final Pattern JAVA_IO_TYPE_REFERENCE = Pattern.compile(
            "java/io/([A-Za-z0-9_$]+)");

    @Test
    void foundationRemainsJavaBaseOnlyAndPurposeBound() throws Exception {
        assertCompiledBoundary();
        assertPublicFilesystemSurface();
        assertResolvedContextHasNoHostLocationSurface();
    }

    private static void assertCompiledBoundary() throws Exception {
        Path classes = Path.of(InitialContext.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI())
                .toAbsolutePath().normalize();
        Path context = classes.resolve(CONTEXT_PACKAGE.replace('.', '/'));
        Path security = classes.resolve(SECURITY_PACKAGE.replace('.', '/'));
        assertTrue(Files.isDirectory(context), "Compiled Ship context classes are missing: " + context);
        assertTrue(Files.isDirectory(security), "Compiled Ship security classes are missing: " + security);

        ToolProvider jdeps = ToolProvider.findFirst("jdeps").orElseThrow(
                () -> new AssertionError("Ship foundation boundary test requires a JDK with jdeps"));
        String modules = jdeps(
                jdeps,
                "--print-module-deps",
                "--class-path", classes.toString(),
                context.toString(), security.toString());
        assertEquals("java.base", modules.trim(), "Ship foundation gained a non-java.base dependency");

        String dependencies = jdeps(
                jdeps,
                "-verbose:class",
                "-filter:none",
                "--class-path", classes.toString(),
                context.toString(), security.toString());
        for (String line : dependencies.lines().toList()) {
            int arrow = line.indexOf(" -> ");
            if (arrow < 0) {
                continue;
            }
            String source = line.substring(0, arrow).trim();
            if (!source.startsWith(CONTEXT_PACKAGE) && !source.startsWith(SECURITY_PACKAGE)) {
                continue;
            }
            String dependency = line.substring(arrow + 4).trim().split("\\s+", 2)[0];
            assertFalse(isForbidden(dependency), line.trim() + " crosses a forbidden Ship boundary");
            assertTrue(
                    dependency.startsWith("java.")
                            || dependency.startsWith("javax.")
                            || dependency.startsWith(CONTEXT_PACKAGE)
                            || dependency.startsWith(SECURITY_PACKAGE),
                    line.trim() + " is outside the java.base-only Ship foundation");
        }

        ToolProvider javap = ToolProvider.findFirst("javap").orElseThrow(
                () -> new AssertionError("Ship foundation boundary test requires a JDK with javap"));
        List<Path> contextClasses;
        try (var files = Files.walk(context)) {
            contextClasses = files.filter(path -> path.toString().endsWith(".class")).toList();
        }
        for (Path classFile : contextClasses) {
            String relative = classes.relativize(classFile).toString();
            String className = relative.substring(0, relative.length() - ".class".length())
                    .replace('/', '.')
                    .replace('\\', '.');
            String bytecode = jdeps(
                    javap,
                    "-classpath", classes.toString(),
                    "-c",
                    "-p",
                    className);
            for (String forbidden : FORBIDDEN_CONTENT_OPEN_REFERENCES) {
                assertFalse(
                        bytecode.contains(forbidden),
                        () -> className + " gained a content-opening reference: " + forbidden);
            }
            assertMetadataOnlyContextIo(className, bytecode);
        }
    }

    private static void assertMetadataOnlyContextIo(String className, String bytecode) {
        Matcher filesMethods = FILES_METHOD_REFERENCE.matcher(bytecode);
        while (filesMethods.find()) {
            String method = filesMethods.group(1);
            assertTrue(
                    ALLOWED_CONTEXT_FILES_METHODS.contains(method),
                    () -> className + " gained an unreviewed Files method: " + method);
        }
        Matcher javaIoTypes = JAVA_IO_TYPE_REFERENCE.matcher(bytecode);
        while (javaIoTypes.find()) {
            String type = javaIoTypes.group(1);
            assertTrue(
                    ALLOWED_CONTEXT_JAVA_IO_TYPES.contains(type),
                    () -> className + " gained an unreviewed java.io type: " + type);
        }
        assertFalse(
                bytecode.contains("java/nio/channels/"),
                () -> className + " gained a java.nio.channels capability");
        assertFalse(
                bytecode.contains("java/util/Scanner"),
                () -> className + " gained a path-capable Scanner");
    }

    private static boolean isForbidden(String dependency) {
        if (dependency.startsWith("java.net.")
                || dependency.startsWith("javax.net.")
                || dependency.startsWith("java.lang.Process")
                || dependency.equals("java.lang.Runtime")) {
            return true;
        }
        return FORBIDDEN_NETWORK_CLASSES.stream().anyMatch(dependency::startsWith)
                || FORBIDDEN_REFERENCES.stream().anyMatch(dependency::startsWith);
    }

    private static void assertPublicFilesystemSurface() throws Exception {
        ClassLoader loader = InitialContext.class.getClassLoader();
        for (String rawType : List.of(
                SECURITY_PACKAGE + "ShipSecureFilesystem",
                SECURITY_PACKAGE + "ProjectSnapshotService")) {
            assertFalse(
                    Modifier.isPublic(Class.forName(rawType, false, loader).getModifiers()),
                    rawType + " must remain package-private");
        }

        assertTrue(Modifier.isPublic(ProjectContextFiles.class.getModifiers()));
        assertTrue(Modifier.isFinal(ProjectContextFiles.class.getModifiers()));
        assertEquals(0, ProjectContextFiles.class.getConstructors().length);
        assertEquals(0, ProjectContextFiles.class.getFields().length);
        Set<String> methods = Arrays.stream(ProjectContextFiles.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(ShipFoundationBoundaryTest::signature)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        assertEquals(PROJECT_CONTEXT_METHODS, methods);
        assertTrue(Arrays.stream(ProjectContextFiles.class.getDeclaredMethods())
                .flatMap(method -> Arrays.stream(method.getParameterTypes()))
                .noneMatch(Path.class::equals));
        assertTrue(Arrays.stream(ProjectContextFiles.class.getDeclaredMethods())
                .flatMap(method -> java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(method.getReturnType()),
                        Arrays.stream(method.getParameterTypes())))
                .noneMatch(Channel.class::isAssignableFrom));

        Class<?> access = ContextFilesystemPolicy.ProjectAccess.class;
        assertTrue(Modifier.isPublic(access.getModifiers()));
        assertTrue(Modifier.isFinal(access.getModifiers()));
        assertEquals(0, access.getConstructors().length);
        Set<String> accessMethods = Arrays.stream(access.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(ShipFoundationBoundaryTest::signature)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        assertEquals(PROJECT_ACCESS_METHODS, accessMethods);

        Class<?> admission = ContextFilesystemPolicy.ProjectRootAdmission.class;
        assertTrue(Modifier.isPublic(admission.getModifiers()));
        assertTrue(Modifier.isFinal(admission.getModifiers()));
        assertEquals(0, admission.getConstructors().length);
        Set<String> admissionMethods = Arrays.stream(admission.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(ShipFoundationBoundaryTest::signature)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        assertEquals(PROJECT_ROOT_ADMISSION_METHODS, admissionMethods);

        Class<?> heldRoot = ProjectContextFiles.HeldRoot.class;
        assertTrue(Modifier.isPublic(heldRoot.getModifiers()));
        assertTrue(Modifier.isFinal(heldRoot.getModifiers()));
        assertEquals(0, heldRoot.getConstructors().length);
        Set<String> heldRootMethods = Arrays.stream(heldRoot.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(ShipFoundationBoundaryTest::signature)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        assertEquals(HELD_ROOT_METHODS, heldRootMethods);
    }

    private static String signature(java.lang.reflect.Method method) {
        String parameters = Arrays.stream(method.getGenericParameterTypes())
                .map(Type::getTypeName)
                .collect(java.util.stream.Collectors.joining(","));
        String exceptions = Arrays.stream(method.getGenericExceptionTypes())
                .map(Type::getTypeName)
                .sorted()
                .collect(java.util.stream.Collectors.joining(","));
        return (Modifier.isStatic(method.getModifiers()) ? "static " : "")
               + method.getName() + '(' + parameters + ")->" + method.getGenericReturnType().getTypeName()
               + (exceptions.isEmpty() ? "" : " throws " + exceptions);
    }

    private static void assertResolvedContextHasNoHostLocationSurface() {
        List<Class<?>> surfaces = new java.util.ArrayList<>();
        surfaces.add(InitialContext.class);
        surfaces.add(ContextResolution.Resolved.class);
        Arrays.stream(InitialContext.class.getDeclaredClasses())
                .filter(Class::isRecord)
                .forEach(surfaces::add);

        for (Class<?> surface : surfaces) {
            if (surface.isRecord()) {
                Arrays.stream(surface.getRecordComponents())
                        .forEach(component -> assertNoHostLocation(component.getGenericType(), component.toString()));
            }
            Arrays.stream(surface.getDeclaredMethods())
                    .filter(method -> Modifier.isPublic(method.getModifiers()))
                    .forEach(method -> {
                        assertNoHostLocation(method.getGenericReturnType(), method.toGenericString());
                        Arrays.stream(method.getGenericParameterTypes())
                                .forEach(type -> assertNoHostLocation(type, method.toGenericString()));
                    });
            Arrays.stream(surface.getDeclaredConstructors())
                    .filter(constructor -> Modifier.isPublic(constructor.getModifiers()))
                    .flatMap(constructor -> Arrays.stream(constructor.getGenericParameterTypes()))
                    .forEach(type -> assertNoHostLocation(type, surface.getName()));
        }
    }

    private static void assertNoHostLocation(Type type, String owner) {
        String name = type.getTypeName();
        assertFalse(
                name.contains(Path.class.getName()) || name.contains(URI.class.getName()),
                owner + " exposes a host location through " + name);
    }

    private static String jdeps(ToolProvider tool, String... arguments) {
        StringWriter output = new StringWriter();
        StringWriter errors = new StringWriter();
        int result = tool.run(new PrintWriter(output), new PrintWriter(errors), arguments);
        assertEquals(0, result, () -> errors + System.lineSeparator() + output);
        return output.toString();
    }
}
