package io.github.luigidemasi.camelkit.ship.controller;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Executable;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.spi.ToolProvider;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ControllerIsolationIT {

    private static final String PACKAGE = "io.github.luigidemasi.camelkit.ship.controller.";
    private static final String PACKAGE_PATH = PACKAGE.replace('.', '/');
    private static final Set<String> PUBLIC_CLASSES = Set.of(
            PACKAGE + "ShipEventType",
            PACKAGE + "ShipInteractionKind",
            PACKAGE + "ShipRun",
            PACKAGE + "ShipRunId",
            PACKAGE + "ShipState");
    private static final Map<String, Set<String>> PUBLIC_METHODS = Map.of(
            PACKAGE + "ShipEventType",
            Set.of(
                    signature("values", PACKAGE + "ShipEventType[]"),
                    signature("valueOf", PACKAGE + "ShipEventType", "java.lang.String"),
                    signature("stableId", "java.lang.String"),
                    signature("fromStableId", PACKAGE + "ShipEventType", "java.lang.String")),
            PACKAGE + "ShipInteractionKind",
            Set.of(
                    signature("values", PACKAGE + "ShipInteractionKind[]"),
                    signature("valueOf", PACKAGE + "ShipInteractionKind", "java.lang.String"),
                    signature("stableId", "java.lang.String"),
                    signature(
                            "fromStableId", PACKAGE + "ShipInteractionKind", "java.lang.String")),
            PACKAGE + "ShipRun",
            Set.of(
                    signature("id", PACKAGE + "ShipRunId"),
                    signature("state", PACKAGE + "ShipState"),
                    signature("revision", "long"),
                    signature("lastEvent", PACKAGE + "ShipEventType"),
                    signature("terminal", "boolean")),
            PACKAGE + "ShipRunId",
            Set.of(
                    signature("equals", "boolean", "java.lang.Object"),
                    signature("hashCode", "int"),
                    signature("toString", "java.lang.String")),
            PACKAGE + "ShipState",
            Set.of(
                    signature("values", PACKAGE + "ShipState[]"),
                    signature("valueOf", PACKAGE + "ShipState", "java.lang.String"),
                    signature("stableId", "java.lang.String"),
                    signature("fromStableId", PACKAGE + "ShipState", "java.lang.String"),
                    signature("isTerminal", "boolean"),
                    signature(
                            "pendingInteraction",
                            "java.util.Optional<" + PACKAGE + "ShipInteractionKind>")));
    private static final List<String> FORBIDDEN_CLASS_REFERENCES = List.of(
            "java.io.",
            "java.net.",
            "java.nio.file.",
            "java.lang.Process",
            "java.security.",
            "java.util.concurrent.",
            "javax.crypto.",
            "com.fasterxml.",
            "info.picocli.",
            "org.apache.camel.",
            "io.github.luigidemasi.camelkit.ship.resolver.");
    private static final Set<String> AUTHORITY_CAPABILITIES = Set.of(
            PACKAGE + "InteractionDecision",
            PACKAGE + "DiscoveryAnswer",
            PACKAGE + "PolicyWaiverEligibility",
            PACKAGE + "AcceptedContextResult",
            PACKAGE + "AcceptedRequirementsResult",
            PACKAGE + "AcceptedDesignResult",
            PACKAGE + "AcceptedPlanResult",
            PACKAGE + "AcceptedExecutionResult",
            PACKAGE + "AcceptedValidationResult",
            PACKAGE + "AcceptedStampCompletion");

    @Test
    void packagedControllerUsesOnlyJavaBaseAndOwnedClasses() throws Exception {
        Path jar = controllerJar();
        ToolProvider jdeps = ToolProvider.findFirst("jdeps").orElseThrow(
                () -> new AssertionError("Controller isolation test requires a JDK with jdeps"));
        StringWriter output = new StringWriter();
        StringWriter errors = new StringWriter();
        int result = jdeps.run(
                new PrintWriter(output),
                new PrintWriter(errors),
                "--print-module-deps",
                jar.toString());
        assertEquals(0, result, errors.toString());
        assertEquals("java.base", output.toString().trim());

        StringWriter classOutput = new StringWriter();
        StringWriter classErrors = new StringWriter();
        int classResult = jdeps.run(
                new PrintWriter(classOutput),
                new PrintWriter(classErrors),
                "-verbose:class",
                jar.toString());
        assertEquals(0, classResult, classErrors.toString());
        for (String line : classOutput.toString().lines().toList()) {
            int arrow = line.indexOf(" -> ");
            if (arrow < 0) {
                continue;
            }
            String dependency = line.substring(arrow + 4).trim().split("\\s+", 2)[0];
            for (String forbidden : FORBIDDEN_CLASS_REFERENCES) {
                assertFalse(
                        dependency.startsWith(forbidden),
                        line.trim() + " crosses forbidden controller boundary " + forbidden);
            }
        }

        List<String> classNames = new ArrayList<>();
        try (JarFile archive = new JarFile(jar.toFile())) {
            for (JarEntry entry : java.util.Collections.list(archive.entries())) {
                if (!entry.getName().endsWith(".class")) {
                    continue;
                }
                assertTrue(
                        entry.getName().startsWith(PACKAGE_PATH),
                        "Controller JAR contains a foreign class: " + entry.getName());
                classNames.add(entry.getName()
                        .substring(0, entry.getName().length() - ".class".length())
                        .replace('/', '.'));
            }
        }
        assertFalse(classNames.isEmpty(), "Controller JAR contains no classes");

        try (URLClassLoader isolated = new URLClassLoader(
                new URL[]{jar.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
            for (String className : classNames) {
                Class<?> type = Class.forName(className, true, isolated);
                type.getDeclaredConstructors();
                type.getDeclaredMethods();
                type.getDeclaredFields();
                if (type.isRecord()) {
                    type.getRecordComponents();
                }
            }
        }
    }

    @Test
    void publicSurfaceIsReadOnlyAndExplicitlyWhitelisted() throws Exception {
        Set<String> actualPublicClasses = new HashSet<>();
        try (JarFile archive = new JarFile(controllerJar().toFile());
             URLClassLoader isolated = new URLClassLoader(
                     new URL[]{controllerJar().toUri().toURL()},
                     ClassLoader.getPlatformClassLoader())) {
            for (JarEntry entry : java.util.Collections.list(archive.entries())) {
                if (!entry.getName().startsWith(PACKAGE_PATH)
                        || !entry.getName().endsWith(".class")) {
                    continue;
                }
                String className = entry.getName()
                        .substring(0, entry.getName().length() - ".class".length())
                        .replace('/', '.');
                Class<?> type = isolated.loadClass(className);
                if (!Modifier.isPublic(type.getModifiers())) {
                    continue;
                }
                actualPublicClasses.add(className);
                assertTrue(
                        Modifier.isFinal(type.getModifiers()) || type.isEnum(),
                        className + " must be immutable");
                assertEquals(
                        type.isEnum() ? Enum.class : Object.class,
                        type.getSuperclass(),
                        className + " has an unexpected superclass");
                assertEquals(0, type.getTypeParameters().length, className + " has type parameters");
                assertEquals(0, type.getGenericInterfaces().length, className + " implements an interface");
                for (var method : type.getMethods()) {
                    assertTrue(
                            method.getDeclaringClass() == type
                                    || method.getDeclaringClass() == Object.class
                                    || type.isEnum() && method.getDeclaringClass() == Enum.class,
                            className + " inherits unexpected public method " + method.toGenericString());
                }
                for (var constructor : type.getDeclaredConstructors()) {
                    assertFalse(
                            Modifier.isPublic(constructor.getModifiers()),
                            className + " exposes a public authority-bearing constructor");
                }
                Set<String> methodSignatures = new HashSet<>();
                for (var method : type.getDeclaredMethods()) {
                    if (Modifier.isPublic(method.getModifiers())) {
                        methodSignatures.add(signature(method));
                        assertConsumerType(method.getGenericReturnType(), method.toGenericString());
                        assertExecutableTypes(method);
                    }
                }
                assertEquals(PUBLIC_METHODS.get(className), methodSignatures, className);
                for (var field : type.getDeclaredFields()) {
                    assertTrue(Modifier.isFinal(field.getModifiers()), field.toGenericString());
                    if (Modifier.isPublic(field.getModifiers())) {
                        assertTrue(type.isEnum() && field.isEnumConstant(), field.toGenericString());
                        assertConsumerType(field.getGenericType(), field.toGenericString());
                    } else if (!field.isSynthetic()) {
                        assertTrue(Modifier.isPrivate(field.getModifiers()), field.toGenericString());
                    }
                }
            }
        }
        assertEquals(PUBLIC_CLASSES, actualPublicClasses);
    }

    @Test
    void moduleDeclaresNoProductionDependencyAndIsOneWayInTheReactor() throws Exception {
        Element moduleProject = parseProject(Path.of(System.getProperty("ship.controller.pom")));
        List<Element> dependencies = declaredDependencies(moduleProject);
        assertFalse(dependencies.isEmpty(), "Expected the test dependency to be declared explicitly");
        for (Element dependency : dependencies) {
            assertEquals("test", directText(dependency, "scope"), describeDependency(dependency));
        }

        Element rootProject = parseProject(Path.of(System.getProperty("ship.controller.root.pom")));
        for (Element dependency : declaredDependencies(rootProject)) {
            assertEquals("test", directText(dependency, "scope"), describeDependency(dependency));
        }
        List<String> modules = directChildren(firstDirectChild(rootProject, "modules"), "module").stream()
                .map(Element::getTextContent)
                .map(String::trim)
                .toList();
        assertEquals(1, modules.stream().filter("camel-kit-ship-controller"::equals).count());
        assertEquals(
                modules.indexOf("camel-kit-ship-resolver") + 1,
                modules.indexOf("camel-kit-ship-controller"),
                "Controller module must follow the isolated resolver without coupling to it");
    }

    @Test
    void authorityCapabilitiesHavePrivateConstructorsAndNoPackagedIssuer() throws Exception {
        List<Class<?>> packagedClasses = new ArrayList<>();
        try (JarFile archive = new JarFile(controllerJar().toFile());
             URLClassLoader isolated = new URLClassLoader(
                     new URL[]{controllerJar().toUri().toURL()},
                     ClassLoader.getPlatformClassLoader())) {
            for (JarEntry entry : java.util.Collections.list(archive.entries())) {
                if (!entry.getName().startsWith(PACKAGE_PATH)
                        || !entry.getName().endsWith(".class")) {
                    continue;
                }
                String className = entry.getName()
                        .substring(0, entry.getName().length() - ".class".length())
                        .replace('/', '.');
                packagedClasses.add(isolated.loadClass(className));
            }

            for (String capabilityName : AUTHORITY_CAPABILITIES) {
                Class<?> capability = isolated.loadClass(capabilityName);
                assertFalse(Modifier.isPublic(capability.getModifiers()), capabilityName);
                assertEquals(1, capability.getDeclaredConstructors().length, capabilityName);
                assertTrue(
                        Modifier.isPrivate(capability.getDeclaredConstructors()[0].getModifiers()),
                        capabilityName + " constructor must remain private");
            }

            for (Class<?> owner : packagedClasses) {
                for (var method : owner.getDeclaredMethods()) {
                    assertFalse(
                            containsAuthorityCapability(method.getGenericReturnType()),
                            method.toGenericString() + " is a packaged capability issuer");
                }
                for (var field : owner.getDeclaredFields()) {
                    assertFalse(
                            containsAuthorityCapability(field.getGenericType()),
                            field.toGenericString() + " stores an issuable capability");
                }
            }
        }
    }

    private static boolean containsAuthorityCapability(Type type) {
        if (type instanceof Class<?> concrete) {
            return (PACKAGE + "AcceptedStageResult").equals(concrete.getName())
                    || AUTHORITY_CAPABILITIES.contains(concrete.getName())
                    || concrete.isArray()
                            && containsAuthorityCapability(concrete.getComponentType());
        }
        if (type instanceof ParameterizedType parameterized) {
            if (containsAuthorityCapability(parameterized.getRawType())) {
                return true;
            }
            return java.util.Arrays.stream(parameterized.getActualTypeArguments())
                    .anyMatch(ControllerIsolationIT::containsAuthorityCapability);
        }
        if (type instanceof GenericArrayType array) {
            return containsAuthorityCapability(array.getGenericComponentType());
        }
        if (type instanceof WildcardType wildcard) {
            return java.util.stream.Stream.concat(
                    java.util.Arrays.stream(wildcard.getUpperBounds()),
                    java.util.Arrays.stream(wildcard.getLowerBounds()))
                    .anyMatch(ControllerIsolationIT::containsAuthorityCapability);
        }
        if (type instanceof TypeVariable<?> variable) {
            return java.util.Arrays.stream(variable.getBounds())
                    .anyMatch(ControllerIsolationIT::containsAuthorityCapability);
        }
        return false;
    }

    private static Path controllerJar() {
        String configured = System.getProperty("ship.controller.jar");
        assertNotNull(configured, "ship.controller.jar is not configured");
        Path jar = Path.of(configured).toAbsolutePath().normalize();
        assertTrue(Files.isRegularFile(jar), "Controller JAR does not exist: " + jar);
        return jar;
    }

    private static void assertExecutableTypes(Executable executable) {
        for (Type parameter : executable.getGenericParameterTypes()) {
            assertConsumerType(parameter, executable.toGenericString());
        }
        for (Type exception : executable.getGenericExceptionTypes()) {
            assertConsumerType(exception, executable.toGenericString());
        }
        for (var variable : executable.getTypeParameters()) {
            for (Type bound : variable.getBounds()) {
                assertConsumerType(bound, executable.toGenericString());
            }
        }
    }

    private static String signature(java.lang.reflect.Method method) {
        String[] parameters = java.util.Arrays.stream(method.getGenericParameterTypes())
                .map(Type::getTypeName)
                .toArray(String[]::new);
        return signature(method.getName(), method.getGenericReturnType().getTypeName(), parameters);
    }

    private static String signature(String name, String returnType, String... parameterTypes) {
        return name + '(' + String.join(",", parameterTypes) + ")->" + returnType;
    }

    private static void assertConsumerType(Type type, String owner) {
        if (type instanceof Class<?> concrete) {
            if (concrete.isArray()) {
                assertConsumerType(concrete.getComponentType(), owner);
                return;
            }
            assertTrue(
                    concrete.isPrimitive()
                            || concrete.getName().startsWith("java.")
                            || PUBLIC_CLASSES.contains(concrete.getName()),
                    owner + " exposes " + concrete.getName());
            return;
        }
        if (type instanceof ParameterizedType parameterized) {
            assertConsumerType(parameterized.getRawType(), owner);
            for (Type argument : parameterized.getActualTypeArguments()) {
                assertConsumerType(argument, owner);
            }
            return;
        }
        if (type instanceof WildcardType wildcard) {
            for (Type bound : wildcard.getUpperBounds()) {
                assertConsumerType(bound, owner);
            }
            for (Type bound : wildcard.getLowerBounds()) {
                assertConsumerType(bound, owner);
            }
            return;
        }
        fail(owner + " exposes unsupported generic type " + type);
    }

    private static Element parseProject(Path pom) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder().parse(pom.toFile()).getDocumentElement();
    }

    private static Element firstDirectChild(Element parent, String name) {
        Element child = optionalDirectChild(parent, name);
        assertNotNull(child, "Missing <" + name + "> in " + parent.getTagName());
        return child;
    }

    private static Element optionalDirectChild(Element parent, String name) {
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && name.equals(element.getLocalName())) {
                return element;
            }
        }
        return null;
    }

    private static List<Element> directChildren(Element parent, String name) {
        List<Element> children = new ArrayList<>();
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && name.equals(element.getLocalName())) {
                children.add(element);
            }
        }
        return children;
    }

    private static List<Element> declaredDependencies(Element project) {
        List<Element> dependencies = new ArrayList<>();
        Element direct = optionalDirectChild(project, "dependencies");
        if (direct != null) {
            dependencies.addAll(directChildren(direct, "dependency"));
        }
        Element profiles = optionalDirectChild(project, "profiles");
        if (profiles != null) {
            for (Element profile : directChildren(profiles, "profile")) {
                Element profileDependencies = optionalDirectChild(profile, "dependencies");
                if (profileDependencies != null) {
                    dependencies.addAll(directChildren(profileDependencies, "dependency"));
                }
            }
        }
        return dependencies;
    }

    private static String directText(Element parent, String name) {
        return firstDirectChild(parent, name).getTextContent().trim();
    }

    private static String describeDependency(Element dependency) {
        return directText(dependency, "groupId") + ':' + directText(dependency, "artifactId");
    }
}
