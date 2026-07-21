package io.github.luigidemasi.camelkit.ship.expression;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.spi.ToolProvider;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipExpressionPolicyBoundaryTest {

    private static final String PACKAGE = "io.github.luigidemasi.camelkit.ship.expression.";
    private static final String INTERNAL_NAME_PREFIX = PACKAGE.replace('.', '/');
    private static final Pattern DEPENDENCY = Pattern.compile("^\\s+(\\S+)\\s+->\\s+(\\S+)\\s+(.+)$");
    private static final Pattern METHOD_REFERENCE = Pattern.compile(
            "^\\s+#[0-9]+ = (?:Interface)?Methodref\\s+#[^\\r\\n]*//\\s+(\\S+)$", Pattern.MULTILINE);
    private static final Pattern FIELD_REFERENCE = Pattern.compile(
            "^\\s+#[0-9]+ = Fieldref\\s+#[^\\r\\n]*//\\s+(\\S+)$", Pattern.MULTILINE);
    private static final Set<String> ALLOWED_EXTERNAL_TYPES = Set.of(
            "java.lang.Character",
            "java.lang.CharSequence",
            "java.lang.Double",
            "java.lang.Long",
            "java.lang.NumberFormatException",
            "java.lang.Object",
            "java.lang.String");
    private static final Set<String> ALLOWED_EXTERNAL_METHODS = Set.of(
            "java/lang/Character.isHighSurrogate:(C)Z",
            "java/lang/Character.isLowSurrogate:(C)Z",
            "java/lang/Character.toCodePoint:(CC)I",
            "java/lang/Double.isFinite:(D)Z",
            "java/lang/Double.parseDouble:(Ljava/lang/String;)D",
            "java/lang/Long.parseLong:(Ljava/lang/String;)J",
            "java/lang/Object.\"<init>\":()V",
            "java/lang/String.charAt:(I)C",
            "java/lang/String.contains:(Ljava/lang/CharSequence;)Z",
            "java/lang/String.equals:(Ljava/lang/Object;)Z",
            "java/lang/String.indexOf:(I)I",
            "java/lang/String.indexOf:(II)I",
            "java/lang/String.indexOf:(Ljava/lang/String;I)I",
            "java/lang/String.length:()I",
            "java/lang/String.startsWith:(Ljava/lang/String;)Z",
            "java/lang/String.startsWith:(Ljava/lang/String;I)Z",
            "java/lang/String.substring:(I)Ljava/lang/String;",
            "java/lang/String.substring:(II)Ljava/lang/String;");
    private static final Set<String> PUBLIC_METHODS = Set.of(
            "isDirectSimpleSelector(java.lang.String)->boolean",
            "isGenericSimpleSelector(java.lang.String,java.lang.String)->boolean",
            "isSafeSimplePredicate(java.lang.String)->boolean",
            "isSafeSimpleTemplate(java.lang.String)->boolean");

    @Test
    void compiledPolicyUsesOnlyExactJavaBaseCapabilities() throws Exception {
        Path classes = Path.of(ShipExpressionPolicy.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
        Path packageDirectory = classes.resolve(PACKAGE.replace('.', '/'));
        assertTrue(Files.isDirectory(packageDirectory), packageDirectory.toString());

        ToolProvider jdeps = ToolProvider.findFirst("jdeps").orElseThrow(
                () -> new AssertionError("Ship expression boundary test requires a JDK with jdeps"));
        StringWriter output = new StringWriter();
        StringWriter errors = new StringWriter();
        int result = jdeps.run(
                new PrintWriter(output),
                new PrintWriter(errors),
                "--multi-release", "17",
                "-verbose:class",
                "-filter:none",
                "--class-path", classes.toString(),
                packageDirectory.toString());
        assertEquals(0, result, () -> errors + System.lineSeparator() + output);

        Set<String> externalTypes = new HashSet<>();
        int sourceDependencies = 0;
        for (String line : output.toString().lines().toList()) {
            if (!line.stripLeading().startsWith(PACKAGE)) {
                continue;
            }
            Matcher dependency = DEPENDENCY.matcher(line);
            assertTrue(dependency.matches(), () -> "Unparsed jdeps dependency line: " + line);
            sourceDependencies++;
            String target = dependency.group(2);
            String module = dependency.group(3).trim();
            if (!target.startsWith(PACKAGE)) {
                externalTypes.add(target);
                assertEquals("java.base", module, line);
            }
        }

        assertTrue(sourceDependencies > 0, () -> "jdeps reported no policy dependencies:\n" + output);
        assertEquals(ALLOWED_EXTERNAL_TYPES, externalTypes);

        ToolProvider javap = ToolProvider.findFirst("javap").orElseThrow(
                () -> new AssertionError("Ship expression boundary test requires a JDK with javap"));
        Set<String> externalMethods = new HashSet<>();
        Set<String> externalFields = new HashSet<>();
        try (var paths = Files.walk(packageDirectory)) {
            for (Path classFile : paths.filter(path -> path.toString().endsWith(".class")).toList()) {
                String bytecode = javap(javap, classes, className(classes, classFile));
                collectExternalReferences(METHOD_REFERENCE, bytecode, externalMethods);
                collectExternalReferences(FIELD_REFERENCE, bytecode, externalFields);
                assertFalse(bytecode.contains("= MethodHandle"), "Expression policy gained a method handle");
                assertFalse(bytecode.contains("= InvokeDynamic"), "Expression policy gained an invokedynamic call");
                assertFalse(bytecode.contains("= Dynamic"), "Expression policy gained a dynamic constant");
                assertFalse(bytecode.contains("ACC_NATIVE"), "Expression policy gained a native method");
            }
        }
        assertEquals(ALLOWED_EXTERNAL_METHODS, externalMethods);
        assertTrue(externalFields.isEmpty(), () -> "Expression policy gained external fields: " + externalFields);
    }

    @Test
    void publicSurfaceIsClosed() throws Exception {
        Class<?> policy = ShipExpressionPolicy.class;
        assertTrue(Modifier.isPublic(policy.getModifiers()));
        assertTrue(Modifier.isFinal(policy.getModifiers()));
        assertEquals(Object.class, policy.getSuperclass());
        assertEquals(0, policy.getInterfaces().length);
        assertEquals(0, policy.getTypeParameters().length);
        assertEquals(0, policy.getFields().length);
        assertTrue(Arrays.stream(policy.getDeclaredClasses())
                .noneMatch(type -> Modifier.isPublic(type.getModifiers())));

        Constructor<?>[] constructors = policy.getDeclaredConstructors();
        assertEquals(1, constructors.length);
        assertEquals(0, constructors[0].getParameterCount());
        assertTrue(Modifier.isPrivate(constructors[0].getModifiers()));

        Set<String> methods = new HashSet<>();
        for (Method method : policy.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers())) {
                continue;
            }
            assertTrue(Modifier.isStatic(method.getModifiers()), method.toString());
            assertEquals(boolean.class, method.getReturnType(), method.toString());
            assertEquals(0, method.getExceptionTypes().length, method.toString());
            assertEquals(0, method.getTypeParameters().length, method.toString());
            methods.add(signature(method));
        }
        assertEquals(PUBLIC_METHODS, methods);

        Path classes = Path.of(policy.getProtectionDomain().getCodeSource().getLocation().toURI());
        Path packageDirectory = classes.resolve(PACKAGE.replace('.', '/'));
        try (var paths = Files.walk(packageDirectory)) {
            for (Path classFile : paths.filter(path -> path.toString().endsWith(".class")).toList()) {
                Class<?> type = Class.forName(className(classes, classFile), false, policy.getClassLoader());
                if (Modifier.isPublic(type.getModifiers())) {
                    assertEquals(policy, type, "Unexpected public expression-policy type");
                }
            }
        }
    }

    private static String signature(Method method) {
        String parameters = Arrays.stream(method.getParameterTypes())
                .map(Class::getTypeName)
                .collect(java.util.stream.Collectors.joining(","));
        return method.getName() + '(' + parameters + ")->" + method.getReturnType().getTypeName();
    }

    private static String javap(ToolProvider tool, Path classes, String className) {
        StringWriter output = new StringWriter();
        StringWriter errors = new StringWriter();
        int result = tool.run(
                new PrintWriter(output),
                new PrintWriter(errors),
                "-classpath", classes.toString(),
                "-v",
                "-p",
                className);
        assertEquals(0, result, () -> errors + System.lineSeparator() + output);
        return output.toString();
    }

    private static void collectExternalReferences(Pattern pattern, String bytecode, Set<String> references) {
        Matcher matcher = pattern.matcher(bytecode);
        while (matcher.find()) {
            String reference = matcher.group(1);
            if (!reference.startsWith(INTERNAL_NAME_PREFIX)) {
                references.add(reference);
            }
        }
    }

    private static String className(Path classes, Path classFile) {
        String relative = classes.relativize(classFile).toString();
        return relative.substring(0, relative.length() - ".class".length())
                .replace(classFile.getFileSystem().getSeparator(), ".");
    }
}
