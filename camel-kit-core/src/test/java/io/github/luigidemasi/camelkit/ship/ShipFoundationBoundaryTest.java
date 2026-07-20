package io.github.luigidemasi.camelkit.ship;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.net.URI;
import java.nio.channels.Channel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipFoundationBoundaryTest {

    private static final String CONTEXT_PACKAGE = "io.github.luigidemasi.camelkit.ship.context.";
    private static final String SECURITY_PACKAGE = "io.github.luigidemasi.camelkit.ship.security.";
    private static final String CONTEXT_FILESYSTEM_POLICY = CONTEXT_PACKAGE + "ContextFilesystemPolicy";
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
    private static final Set<String> ALLOWED_CONTEXT_FILE_DEPENDENCIES = Set.of(
            "java.io.FileNotFoundException",
            "java.io.IOException",
            "java.nio.file.AccessDeniedException",
            "java.nio.file.DirectoryStream",
            "java.nio.file.FileSystem",
            "java.nio.file.Files",
            "java.nio.file.InvalidPathException",
            "java.nio.file.LinkOption",
            "java.nio.file.NoSuchFileException",
            "java.nio.file.Path",
            "java.nio.file.ProviderMismatchException",
            "java.nio.file.SecureDirectoryStream",
            "java.nio.file.attribute.BasicFileAttributeView",
            "java.nio.file.attribute.BasicFileAttributes",
            "java.nio.file.attribute.FileAttributeView",
            "java.nio.file.attribute.FileTime",
            "java.nio.file.attribute.PosixFileAttributes",
            "java.nio.file.attribute.PosixFilePermissions");
    private static final Set<String> ALLOWED_CONTEXT_SECURITY_DEPENDENCIES = Set.of(
            SECURITY_PACKAGE + "ProjectContextFiles",
            SECURITY_PACKAGE + "ProjectContextFiles$HeldRoot",
            SECURITY_PACKAGE + "ShipFilesystemException",
            SECURITY_PACKAGE + "ShipTreePolicy",
            SECURITY_PACKAGE + "ShipTreePolicy$Classification");
    private static final Set<String> CONTEXT_WIDE_FILE_METHODS = Set.of(
            "java/nio/file/FileSystem.getSeparator",
            "java/nio/file/Path.equals",
            "java/nio/file/Path.getFileName",
            "java/nio/file/Path.getFileSystem",
            "java/nio/file/Path.getNameCount",
            "java/nio/file/Path.getParent",
            "java/nio/file/Path.getRoot",
            "java/nio/file/Path.isAbsolute",
            "java/nio/file/Path.iterator",
            "java/nio/file/Path.normalize",
            "java/nio/file/Path.of",
            "java/nio/file/Path.relativize",
            "java/nio/file/Path.resolve",
            "java/nio/file/Path.startsWith",
            "java/nio/file/Path.toAbsolutePath",
            "java/nio/file/Path.toString");
    private static final Set<String> POLICY_ONLY_FILE_METHODS = Set.of(
            "java/nio/file/DirectoryStream.close",
            "java/nio/file/Files.exists",
            "java/nio/file/Files.isDirectory",
            "java/nio/file/Files.isSymbolicLink",
            "java/nio/file/Files.newDirectoryStream",
            "java/nio/file/Files.readAttributes",
            "java/nio/file/Path.toRealPath",
            "java/nio/file/SecureDirectoryStream.close",
            "java/nio/file/SecureDirectoryStream.getFileAttributeView",
            "java/nio/file/attribute/BasicFileAttributeView.readAttributes",
            "java/nio/file/attribute/BasicFileAttributes.fileKey",
            "java/nio/file/attribute/BasicFileAttributes.isDirectory",
            "java/nio/file/attribute/BasicFileAttributes.isRegularFile",
            "java/nio/file/attribute/BasicFileAttributes.isSymbolicLink",
            "java/nio/file/attribute/BasicFileAttributes.lastModifiedTime",
            "java/nio/file/attribute/BasicFileAttributes.size",
            "java/nio/file/attribute/FileTime.equals",
            "java/nio/file/attribute/FileTime.to",
            "java/nio/file/attribute/PosixFileAttributes.lastModifiedTime",
            "java/nio/file/attribute/PosixFileAttributes.permissions",
            "java/nio/file/attribute/PosixFileAttributes.size",
            "java/nio/file/attribute/PosixFilePermissions.toString");
    private static final List<String> FORBIDDEN_CONTEXT_DEPENDENCY_PREFIXES = List.of(
            "java.lang.foreign.",
            "java.lang.module.",
            "java.lang.reflect.",
            "java.nio.channels.",
            "java.nio.file.spi.",
            "java.util.jar.",
            "java.util.zip.");
    private static final Set<String> FORBIDDEN_CONTEXT_DEPENDENCIES = Set.of(
            "java.lang.ClassLoader",
            "java.util.Formatter",
            "java.util.ResourceBundle",
            "java.util.Scanner",
            "java.util.ServiceLoader");
    private static final List<String> FORBIDDEN_CONTEXT_BYTECODE_REFERENCES = List.of(
            "java/lang/ClassLoader",
            "java/lang/foreign/",
            "java/lang/module/",
            "java/lang/reflect/",
            "java/nio/channels/",
            "java/nio/file/spi/",
            "java/util/Formatter",
            "java/util/ResourceBundle",
            "java/util/Scanner",
            "java/util/ServiceLoader",
            "java/util/jar/",
            "java/util/zip/");
    private static final String METHOD_REFERENCE_PREFIX
            = "(?:REF_(?:invoke[A-Za-z]+|newInvokeSpecial)\\s+"
              + "|(?:Interface)?Methodref\\s+#[^\\r\\n]*//\\s+"
              + "|// (?:InterfaceMethod|Method)\\s+)";
    private static final Pattern FILE_METHOD_REFERENCE = Pattern.compile(
            METHOD_REFERENCE_PREFIX
                                                                         + "(java/nio/file(?:/attribute)?/[A-Za-z0-9_$]+)\\.([A-Za-z0-9_$]+):");
    private static final Set<String> ALLOWED_CONTEXT_SECURITY_METHODS = Set.of(
            "io/github/luigidemasi/camelkit/ship/security/ProjectContextFiles$HeldRoot.close:()V",
            "io/github/luigidemasi/camelkit/ship/security/ProjectContextFiles.close:()V",
            "io/github/luigidemasi/camelkit/ship/security/ProjectContextFiles.hold:"
                                                                                          + "(Lio/github/luigidemasi/camelkit/ship/context/ContextFilesystemPolicy$ProjectRootAdmission;)"
                                                                                          + "Lio/github/luigidemasi/camelkit/ship/security/ProjectContextFiles$HeldRoot;",
            "io/github/luigidemasi/camelkit/ship/security/ProjectContextFiles.open:"
                                                                                                                                                                           + "(Lio/github/luigidemasi/camelkit/ship/context/ContextFilesystemPolicy$ProjectAccess;)"
                                                                                                                                                                           + "Lio/github/luigidemasi/camelkit/ship/security/ProjectContextFiles;",
            "io/github/luigidemasi/camelkit/ship/security/ProjectContextFiles.readDocument:(Ljava/lang/String;I)[B",
            "io/github/luigidemasi/camelkit/ship/security/ShipFilesystemException.code:()Ljava/lang/String;",
            "io/github/luigidemasi/camelkit/ship/security/ShipFilesystemException.getCause:()Ljava/lang/Throwable;",
            "io/github/luigidemasi/camelkit/ship/security/ShipTreePolicy.classify:"
                                                                                                                     + "(Ljava/lang/String;)"
                                                                                                                     + "Lio/github/luigidemasi/camelkit/ship/security/ShipTreePolicy$Classification;",
            "io/github/luigidemasi/camelkit/ship/security/ShipTreePolicy.current:"
                                                                                                                                                                                                       + "()Lio/github/luigidemasi/camelkit/ship/security/ShipTreePolicy;",
            "io/github/luigidemasi/camelkit/ship/security/ShipTreePolicy.isAllowedAbsolutePath:(Ljava/nio/file/Path;)Z",
            "io/github/luigidemasi/camelkit/ship/security/ShipTreePolicy.requireCanonicalRelativePath:"
                                                                                                                         + "(Ljava/lang/String;)Ljava/lang/String;");
    private static final Set<String> ALLOWED_CONTEXT_SECURITY_FIELDS = Set.of(
            "io/github/luigidemasi/camelkit/ship/security/ShipTreePolicy$Classification.MATERIAL:"
                                                                              + "Lio/github/luigidemasi/camelkit/ship/security/ShipTreePolicy$Classification;");
    private static final Pattern SECURITY_METHOD_REFERENCE = Pattern.compile(
            METHOD_REFERENCE_PREFIX
                                                                             + "(io/github/luigidemasi/camelkit/ship/security/[A-Za-z0-9_$]+\\."
                                                                             + "(?:[A-Za-z0-9_$]+|\"<init>\"):[^\\s]+)");
    private static final String FIELD_REFERENCE_PREFIX
            = "(?:REF_(?:get|put)(?:Field|Static)\\s+|Fieldref\\s+#[^\\r\\n]*//\\s+|// Field\\s+)";
    private static final Pattern SECURITY_FIELD_REFERENCE = Pattern.compile(
            FIELD_REFERENCE_PREFIX
                                                                            + "(io/github/luigidemasi/camelkit/ship/security/[A-Za-z0-9_$]+\\.[A-Za-z0-9_$]+:[^\\s]+)");
    private static final Pattern DIRECT_METHOD_HANDLE_REFERENCE = Pattern.compile(
            METHOD_REFERENCE_PREFIX + "java/lang/invoke/"
                                                                                  + "(?:MethodHandle|MethodHandles(?:\\$Lookup)?)\\.[A-Za-z0-9_$]+:");
    private static final Pattern DIRECT_CLASS_REFLECTION_REFERENCE = Pattern.compile(
            METHOD_REFERENCE_PREFIX + "java/lang/(?:Class|ClassLoader)\\."
                                                                                     + "(?:forName|loadClass|getMethods?|getDeclaredMethods?|getConstructors?|"
                                                                                     + "getDeclaredConstructors?|getFields?|getDeclaredFields?|newInstance):");
    private static final Pattern NATIVE_LOAD_REFERENCE = Pattern.compile(
            METHOD_REFERENCE_PREFIX + "java/lang/(?:Runtime|System)\\.load(?:Library)?:");
    private static final Pattern NATIVE_METHOD = Pattern.compile(
            "(?m)^\\s*flags:.*\\bACC_NATIVE\\b");
    private static final String STRING_ATTRIBUTE_READ_TARGET
            = "java/nio/file/Files\\.readAttributes:"
              + "\\(Ljava/nio/file/Path;Ljava/lang/String;\\[Ljava/nio/file/LinkOption;\\)Ljava/util/Map;";
    private static final Pattern STRING_ATTRIBUTE_READ_INSTRUCTION = Pattern.compile(
            "// Method " + STRING_ATTRIBUTE_READ_TARGET);
    private static final Pattern STRING_ATTRIBUTE_READ_METHOD_HANDLE = Pattern.compile(
            "REF_invoke[A-Za-z]+\\s+" + STRING_ATTRIBUTE_READ_TARGET);
    private static final Pattern BYTECODE_INSTRUCTION = Pattern.compile(
            "^\\s*[0-9]+:\\s+(.*)$");
    private static final List<Pattern> FIXED_UNIX_ATTRIBUTE_OPERANDS = List.of(
            Pattern.compile("ldc(?:_w)?\\s+#[0-9]+\\s+// String "
                            + Pattern.quote("unix:dev,ino,nlink,mode,ctime,size,lastModifiedTime")),
            Pattern.compile("iconst_1"),
            Pattern.compile("anewarray\\s+#[0-9]+\\s+// class java/nio/file/LinkOption"),
            Pattern.compile("dup"),
            Pattern.compile("iconst_0"),
            Pattern.compile("getstatic\\s+#[0-9]+\\s+// Field java/nio/file/LinkOption\\.NOFOLLOW_LINKS:"
                            + "Ljava/nio/file/LinkOption;"),
            Pattern.compile("aastore"));

    @Test
    void foundationRemainsJavaBaseOnlyAndPurposeBound() throws Exception {
        assertCompiledBoundary();
        assertPublicFilesystemSurface();
        assertResolvedContextHasNoHostLocationSurface();
    }

    @Test
    void metadataOnlyGuardRejectsUnapprovedFilesystemCapabilities() throws Exception {
        assertDoesNotThrow(() -> assertMetadataOnlyContextIo(
                CONTEXT_FILESYSTEM_POLICY,
                "// Method java/nio/file/Files.readAttributes:(Ljava/nio/file/Path;)Ljava/lang/Object;"));
        assertDoesNotThrow(() -> assertMetadataOnlyContextIo(
                CONTEXT_PACKAGE + "InitialContextResolver",
                "// InterfaceMethod java/nio/file/Path.normalize:()Ljava/nio/file/Path;"));
        assertDoesNotThrow(() -> assertMetadataOnlyContextDependency(
                CONTEXT_FILESYSTEM_POLICY, SECURITY_PACKAGE + "ShipTreePolicy"));
        assertDoesNotThrow(() -> assertMetadataOnlyContextIo(
                CONTEXT_FILESYSTEM_POLICY,
                "// Method io/github/luigidemasi/camelkit/ship/security/ShipTreePolicy.current:"
                                           + "()Lio/github/luigidemasi/camelkit/ship/security/ShipTreePolicy;\n"
                                           + "// Field io/github/luigidemasi/camelkit/ship/security/ShipTreePolicy$Classification.MATERIAL:"
                                           + "Lio/github/luigidemasi/camelkit/ship/security/ShipTreePolicy$Classification;"));
        String fixedAttributes = "90: ldc_w #1 // String unix:dev,ino,nlink,mode,ctime,size,lastModifiedTime\n"
                                 + "93: iconst_1\n"
                                 + "94: anewarray #2 // class java/nio/file/LinkOption\n"
                                 + "97: dup\n"
                                 + "98: iconst_0\n"
                                 + "99: getstatic #3 // Field java/nio/file/LinkOption.NOFOLLOW_LINKS:"
                                 + "Ljava/nio/file/LinkOption;\n"
                                 + "102: aastore\n"
                                 + "103: invokestatic #4 // Method java/nio/file/Files.readAttributes:"
                                 + "(Ljava/nio/file/Path;Ljava/lang/String;"
                                 + "[Ljava/nio/file/LinkOption;)Ljava/util/Map;";
        assertDoesNotThrow(() -> assertFixedUnixAttributeRead(CONTEXT_FILESYSTEM_POLICY, fixedAttributes));
        assertThrows(
                AssertionError.class,
                () -> assertFixedUnixAttributeRead(CONTEXT_FILESYSTEM_POLICY, fixedAttributes.replace(
                        "unix:dev,ino,nlink,mode,ctime,size,lastModifiedTime", "user:*")));
        assertThrows(
                AssertionError.class,
                () -> assertFixedUnixAttributeRead(
                        CONTEXT_FILESYSTEM_POLICY,
                        fixedAttributes.replace("93: iconst_1", "91: pop\n92: aload_2\n93: iconst_1")));
        assertThrows(
                AssertionError.class,
                () -> assertFixedUnixAttributeRead(CONTEXT_FILESYSTEM_POLICY + "$Nested", fixedAttributes));

        for (String bytecode : List.of(
                "// InterfaceMethod java/nio/file/SecureDirectoryStream.deleteFile:(Ljava/nio/file/Path;)V",
                "// InterfaceMethod java/nio/file/attribute/BasicFileAttributeView.setTimes:()V",
                "// Method java/nio/file/spi/FileSystemProvider.newByteChannel:()V",
                "// Method java/util/zip/ZipFile.\"<init>\":(Ljava/lang/String;)V",
                "// Method java/util/jar/JarFile.\"<init>\":(Ljava/lang/String;)V",
                "// Method java/util/Formatter.\"<init>\":(Ljava/lang/String;)V",
                "// Method java/util/ResourceBundle.getBundle:(Ljava/lang/String;)Ljava/util/ResourceBundle;",
                "// Method java/util/ServiceLoader.load:(Ljava/lang/Class;)Ljava/util/ServiceLoader;",
                "// Method java/lang/reflect/Method.invoke:()Ljava/lang/Object;",
                "// Method java/lang/Class.forName:(Ljava/lang/String;)Ljava/lang/Class;",
                "// Method java/lang/invoke/MethodHandles$Lookup.findStatic:()Ljava/lang/invoke/MethodHandle;",
                "// Method java/lang/ClassLoader.defineClass:()[B",
                "// Method java/lang/Runtime.load:(Ljava/lang/String;)V",
                "// Method java/lang/System.load:(Ljava/lang/String;)V",
                "flags: (0x0100) ACC_NATIVE")) {
            assertThrows(
                    AssertionError.class,
                    () -> assertMetadataOnlyContextIo(CONTEXT_FILESYSTEM_POLICY, bytecode));
        }

        assertThrows(
                AssertionError.class,
                () -> assertMetadataOnlyContextIo(
                        CONTEXT_PACKAGE + "InitialContextResolver",
                        "// Method java/nio/file/Files.readAttributes:()Ljava/lang/Object;"));
        assertThrows(
                AssertionError.class,
                () -> assertMetadataOnlyContextIo(
                        CONTEXT_PACKAGE + "InitialContextResolver",
                        "// InterfaceMethod java/nio/file/Path.toRealPath:()Ljava/nio/file/Path;"));
        assertThrows(
                AssertionError.class,
                () -> assertMetadataOnlyContextDependency(
                        CONTEXT_FILESYSTEM_POLICY, "java.io.FileReader"));
        assertThrows(
                AssertionError.class,
                () -> assertMetadataOnlyContextDependency(
                        CONTEXT_FILESYSTEM_POLICY, "java.nio.file.spi.FileSystemProvider"));
        assertThrows(
                AssertionError.class,
                () -> assertMetadataOnlyContextDependency(
                        CONTEXT_FILESYSTEM_POLICY, "java.lang.module.ModuleFinder"));
        assertThrows(
                AssertionError.class,
                () -> assertMetadataOnlyContextDependency(
                        CONTEXT_FILESYSTEM_POLICY, "java.lang.ClassLoader"));
        assertThrows(
                AssertionError.class,
                () -> assertMetadataOnlyContextDependency(
                        CONTEXT_FILESYSTEM_POLICY, "java.util.ResourceBundle"));
        assertThrows(
                AssertionError.class,
                () -> assertMetadataOnlyContextDependency(
                        CONTEXT_FILESYSTEM_POLICY, SECURITY_PACKAGE + "ExternalDocumentReader"));
        assertThrows(
                AssertionError.class,
                () -> assertMetadataOnlyContextIo(
                        CONTEXT_FILESYSTEM_POLICY,
                        "// Method io/github/luigidemasi/camelkit/ship/security/ProjectContextFiles.readExternal:"
                                                   + "(Ljava/nio/file/Path;)[B"));
        assertThrows(
                AssertionError.class,
                () -> assertMetadataOnlyContextIo(
                        CONTEXT_FILESYSTEM_POLICY,
                        "REF_newInvokeSpecial "
                                                   + "io/github/luigidemasi/camelkit/ship/security/ProjectContextFiles.\"<init>\":()V"));
        assertThrows(
                AssertionError.class,
                () -> assertMetadataOnlyContextIo(
                        CONTEXT_FILESYSTEM_POLICY,
                        "// Field io/github/luigidemasi/camelkit/ship/security/ProjectContextFiles.READER:"
                                                   + "Ljava/util/function/Function;"));

        Path testClasses = Path.of(ShipFoundationBoundaryTest.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
        ToolProvider javap = ToolProvider.findFirst("javap").orElseThrow();
        String methodReference = jdeps(
                javap,
                "-classpath", testClasses.toString(),
                "-v",
                "-p",
                ForbiddenMethodReference.class.getName());
        assertThrows(
                AssertionError.class,
                () -> assertMetadataOnlyContextIo(CONTEXT_FILESYSTEM_POLICY, methodReference));
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
            if (source.startsWith(CONTEXT_PACKAGE)) {
                assertMetadataOnlyContextDependency(source, dependency);
            }
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
                    "-v",
                    "-p",
                    className);
            assertMetadataOnlyContextIo(className, bytecode);
            if (className.equals(CONTEXT_FILESYSTEM_POLICY)
                    || className.startsWith(CONTEXT_FILESYSTEM_POLICY + '$')) {
                assertFixedUnixAttributeRead(className, bytecode);
            }
        }
    }

    private static void assertMetadataOnlyContextDependency(String className, String dependency) {
        if (dependency.startsWith("java.io.") || dependency.startsWith("java.nio.file.")) {
            assertTrue(
                    ALLOWED_CONTEXT_FILE_DEPENDENCIES.contains(dependency),
                    () -> className + " gained an unreviewed filesystem dependency: " + dependency);
        }
        if (dependency.startsWith(SECURITY_PACKAGE)) {
            assertTrue(
                    ALLOWED_CONTEXT_SECURITY_DEPENDENCIES.contains(dependency),
                    () -> className + " gained an unreviewed security dependency: " + dependency);
        }
        assertFalse(
                FORBIDDEN_CONTEXT_DEPENDENCIES.contains(dependency)
                        || FORBIDDEN_CONTEXT_DEPENDENCY_PREFIXES.stream().anyMatch(dependency::startsWith),
                () -> className + " gained an indirect filesystem capability: " + dependency);
    }

    private static void assertMetadataOnlyContextIo(String className, String bytecode) {
        Matcher fileMethods = FILE_METHOD_REFERENCE.matcher(bytecode);
        while (fileMethods.find()) {
            String method = fileMethods.group(1) + '.' + fileMethods.group(2);
            assertTrue(
                    CONTEXT_WIDE_FILE_METHODS.contains(method) || POLICY_ONLY_FILE_METHODS.contains(method),
                    () -> className + " gained an unreviewed filesystem method: " + method);
            if (POLICY_ONLY_FILE_METHODS.contains(method)) {
                assertTrue(
                        className.equals(CONTEXT_FILESYSTEM_POLICY)
                                || className.startsWith(CONTEXT_FILESYSTEM_POLICY + '$'),
                        () -> className + " used a policy-only filesystem method outside ContextFilesystemPolicy: "
                              + method);
            }
        }
        Matcher securityMethods = SECURITY_METHOD_REFERENCE.matcher(bytecode);
        while (securityMethods.find()) {
            String method = securityMethods.group(1);
            assertTrue(
                    ALLOWED_CONTEXT_SECURITY_METHODS.contains(method),
                    () -> className + " gained an unreviewed security method: " + method);
        }
        Matcher securityFields = SECURITY_FIELD_REFERENCE.matcher(bytecode);
        while (securityFields.find()) {
            String field = securityFields.group(1);
            assertTrue(
                    ALLOWED_CONTEXT_SECURITY_FIELDS.contains(field),
                    () -> className + " gained an unreviewed security field: " + field);
        }
        for (String forbidden : FORBIDDEN_CONTEXT_BYTECODE_REFERENCES) {
            assertFalse(
                    bytecode.contains(forbidden),
                    () -> className + " gained an indirect filesystem capability: " + forbidden);
        }
        assertFalse(
                DIRECT_METHOD_HANDLE_REFERENCE.matcher(bytecode).find(),
                () -> className + " gained an explicit MethodHandle capability");
        assertFalse(
                DIRECT_CLASS_REFLECTION_REFERENCE.matcher(bytecode).find(),
                () -> className + " gained an explicit reflective lookup capability");
        assertFalse(
                NATIVE_LOAD_REFERENCE.matcher(bytecode).find(),
                () -> className + " gained a native-library loading capability");
        assertFalse(
                NATIVE_METHOD.matcher(bytecode).find(),
                () -> className + " gained a native method");
    }

    private static void assertFixedUnixAttributeRead(String className, String bytecode) {
        List<String> instructions = bytecodeInstructions(bytecode);
        List<Integer> stringAttributeReads = new ArrayList<>();
        for (int index = 0; index < instructions.size(); index++) {
            if (STRING_ATTRIBUTE_READ_INSTRUCTION.matcher(instructions.get(index)).find()) {
                stringAttributeReads.add(index);
            }
        }
        int expectedReads = className.equals(CONTEXT_FILESYSTEM_POLICY) ? 1 : 0;
        assertEquals(
                expectedReads,
                stringAttributeReads.size(),
                () -> className + " must have " + expectedReads + " fixed string-based attribute reads");
        assertFalse(
                STRING_ATTRIBUTE_READ_METHOD_HANDLE.matcher(bytecode).find(),
                () -> className + " must not delegate its attribute selector through a method handle");
        if (expectedReads == 0) {
            return;
        }

        int readIndex = stringAttributeReads.get(0);
        assertTrue(
                readIndex >= FIXED_UNIX_ATTRIBUTE_OPERANDS.size(),
                "ContextFilesystemPolicy string-based attribute read is missing fixed operands");
        for (int offset = 0; offset < FIXED_UNIX_ATTRIBUTE_OPERANDS.size(); offset++) {
            String instruction = instructions.get(readIndex - FIXED_UNIX_ATTRIBUTE_OPERANDS.size() + offset);
            Pattern expected = FIXED_UNIX_ATTRIBUTE_OPERANDS.get(offset);
            assertTrue(
                    expected.matcher(instruction).matches(),
                    () -> "ContextFilesystemPolicy string-based attribute read gained a dynamic or unapproved operand: "
                          + instruction);
        }
    }

    private static List<String> bytecodeInstructions(String bytecode) {
        List<String> instructions = new ArrayList<>();
        for (String line : bytecode.lines().toList()) {
            Matcher instruction = BYTECODE_INSTRUCTION.matcher(line);
            if (instruction.matches()) {
                instructions.add(instruction.group(1));
            }
        }
        return List.copyOf(instructions);
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

    @FunctionalInterface
    private interface PathReader {
        String read(Path path) throws IOException;
    }

    private static final class ForbiddenMethodReference {
        private static final PathReader READ = Files::readString;

        private ForbiddenMethodReference() {
        }
    }
}
