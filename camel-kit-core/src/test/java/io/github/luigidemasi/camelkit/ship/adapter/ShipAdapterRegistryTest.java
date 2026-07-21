package io.github.luigidemasi.camelkit.ship.adapter;

import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.spi.ToolProvider;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import io.github.luigidemasi.camelkit.config.AgentRegistry;
import io.github.luigidemasi.camelkit.ship.adapter.ShipAdapterRegistry.Adapter;
import io.github.luigidemasi.camelkit.ship.adapter.ShipAdapterRegistry.IngressMode;
import io.github.luigidemasi.camelkit.ship.adapter.ShipAdapterRegistry.SupportStatus;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShipAdapterRegistryTest {

    private static final String NONE = "none";
    private static final String PACKAGE = "io.github.luigidemasi.camelkit.ship.adapter.";
    private static final String REGISTRY = PACKAGE + "ShipAdapterRegistry";
    private static final String ADAPTER = REGISTRY + "$Adapter";
    private static final String MANIFEST = REGISTRY + "$Manifest";
    private static final String STATUS = REGISTRY + "$SupportStatus";
    private static final String INGRESS = REGISTRY + "$IngressMode";
    private static final Pattern DEPENDENCY = Pattern.compile("^\\s+(\\S+)\\s+->\\s+(\\S+)\\s+(.+)$");
    private static final Set<String> EXPECTED_CLASSES = Set.of(REGISTRY, ADAPTER, MANIFEST, STATUS, INGRESS);
    private static final Set<String> ALLOWED_EXTERNAL_TYPES = Set.of(
            "com.fasterxml.jackson.annotation.JsonCreator",
            "com.fasterxml.jackson.core.JsonFactory",
            "com.fasterxml.jackson.core.StreamReadConstraints",
            "com.fasterxml.jackson.core.StreamReadConstraints$Builder",
            "com.fasterxml.jackson.core.StreamReadFeature",
            "com.fasterxml.jackson.core.TSFBuilder",
            "com.fasterxml.jackson.databind.DeserializationFeature",
            "com.fasterxml.jackson.databind.MapperFeature",
            "com.fasterxml.jackson.databind.ObjectMapper",
            "com.fasterxml.jackson.databind.PropertyNamingStrategies",
            "com.fasterxml.jackson.databind.PropertyNamingStrategy",
            "com.fasterxml.jackson.databind.cfg.CoercionAction",
            "com.fasterxml.jackson.databind.cfg.CoercionInputShape",
            "com.fasterxml.jackson.databind.cfg.MutableCoercionConfig",
            "com.fasterxml.jackson.databind.type.LogicalType",
            "com.fasterxml.jackson.dataformat.yaml.YAMLFactory",
            "com.fasterxml.jackson.dataformat.yaml.YAMLFactoryBuilder",
            "io.github.luigidemasi.camelkit.config.AgentRegistry",
            "java.io.IOException",
            "java.io.InputStream",
            "java.lang.CharSequence",
            "java.lang.Class",
            "java.lang.ClassLoader",
            "java.lang.Enum",
            "java.lang.Exception",
            "java.lang.IllegalArgumentException",
            "java.lang.IllegalStateException",
            "java.lang.Iterable",
            "java.lang.Object",
            "java.lang.Record",
            "java.lang.String",
            "java.lang.Throwable",
            "java.lang.invoke.CallSite",
            "java.lang.invoke.LambdaMetafactory",
            "java.lang.invoke.MethodHandle",
            "java.lang.invoke.MethodHandles",
            "java.lang.invoke.MethodHandles$Lookup",
            "java.lang.invoke.MethodType",
            "java.lang.invoke.StringConcatFactory",
            "java.lang.invoke.TypeDescriptor",
            "java.lang.runtime.ObjectMethods",
            "java.util.ArrayList",
            "java.util.Collection",
            "java.util.Comparator",
            "java.util.LinkedHashMap",
            "java.util.LinkedHashSet",
            "java.util.List",
            "java.util.Locale",
            "java.util.Map",
            "java.util.Set",
            "java.util.function.Consumer",
            "java.util.function.Function",
            "java.util.function.IntPredicate",
            "java.util.regex.Matcher",
            "java.util.regex.Pattern",
            "java.util.stream.IntStream",
            "java.util.stream.Stream");
    private static final Map<String, Set<String>> NON_PRIVATE_METHODS = Map.of(
            REGISTRY, Set.of(
                    "static loadDefault()->" + REGISTRY,
                    "static load(java.io.InputStream,java.util.Set<java.lang.String>)->" + REGISTRY,
                    "descriptors()->java.util.List<" + ADAPTER + ">",
                    "descriptor(java.lang.String)->" + ADAPTER),
            ADAPTER, Set.of(
                    "toString()->java.lang.String",
                    "hashCode()->int",
                    "equals(java.lang.Object)->boolean",
                    "harnessId()->java.lang.String",
                    "status()->" + STATUS,
                    "ingressMode()->" + INGRESS,
                    "interactionProfile()->java.lang.String",
                    "launcherProfile()->java.lang.String",
                    "sandboxProfile()->java.lang.String",
                    "providerProfile()->java.lang.String",
                    "protocolProfile()->java.lang.String",
                    "evidenceProfile()->java.lang.String",
                    "reason()->java.lang.String"),
            MANIFEST, Set.of(
                    "toString()->java.lang.String",
                    "hashCode()->int",
                    "equals(java.lang.Object)->boolean",
                    "registrySchemaVersion()->int",
                    "adapters()->java.util.List<" + ADAPTER + ">"),
            STATUS, Set.of(
                    "static values()->" + STATUS + "[]",
                    "static valueOf(java.lang.String)->" + STATUS,
                    "static fromValue(java.lang.String)->" + STATUS),
            INGRESS, Set.of(
                    "static values()->" + INGRESS + "[]",
                    "static valueOf(java.lang.String)->" + INGRESS,
                    "static fromValue(java.lang.String)->" + INGRESS));
    private static final Map<String, Set<String>> NON_PRIVATE_FIELDS = Map.of(
            REGISTRY, Set.of("static final REGISTRY_SCHEMA_VERSION:int"),
            ADAPTER, Set.of(),
            MANIFEST, Set.of(),
            STATUS, Set.of("static final INCOMPATIBLE:" + STATUS, "static final UNTESTED:" + STATUS),
            INGRESS, Set.of("static final NONE:" + INGRESS));
    private static final Map<String, Set<String>> NON_PRIVATE_CONSTRUCTORS = Map.of(
            REGISTRY, Set.of(),
            ADAPTER, Set.of("(java.lang.String," + STATUS + ',' + INGRESS
                            + ",java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,"
                            + "java.lang.String,java.lang.String)"),
            MANIFEST, Set.of(),
            STATUS, Set.of(),
            INGRESS, Set.of());

    @Test
    void bundledRegistryCoversEveryHarnessWithoutClaimingSupport() {
        ShipAdapterRegistry registry = ShipAdapterRegistry.loadDefault();
        List<String> expected = AgentRegistry.names().stream().sorted().toList();

        assertEquals(expected, registry.descriptors().stream().map(Adapter::harnessId).toList());
        assertTrue(registry.descriptors().stream().allMatch(adapter -> adapter.status() == SupportStatus.INCOMPATIBLE
                || adapter.status() == SupportStatus.UNTESTED));

        Adapter bob = registry.descriptor("bob");
        assertEquals(SupportStatus.INCOMPATIBLE, bob.status());
        assertProfilesNone(bob);
        assertAll(
                () -> assertTrue(bob.reason().contains("single-session")),
                () -> assertTrue(bob.reason().contains("spawned subagents")),
                () -> assertTrue(bob.reason().contains("no Bob 1 Ship wrapper")),
                () -> assertNotNull(AgentRegistry.descriptor("bob")),
                () -> assertFalse(AgentRegistry.descriptor("bob").supportsSubagents()));

        registry.descriptors().stream()
                .filter(adapter -> !"bob".equals(adapter.harnessId()))
                .forEach(adapter -> {
                    assertEquals(SupportStatus.UNTESTED, adapter.status(), adapter.harnessId());
                    assertProfilesNone(adapter);
                    assertEquals("No certified Ship adapter tuple is registered for this harness.", adapter.reason());
                });
    }

    @Test
    void inventoryIsImmutableSortedAndExact() {
        ShipAdapterRegistry registry = load(validRegistry("qwen", "untested", "pi", "untested"),
                Set.of("pi", "qwen"));

        assertEquals(List.of("pi", "qwen"), registry.descriptors().stream().map(Adapter::harnessId).toList());
        assertThrows(UnsupportedOperationException.class, () -> registry.descriptors().clear());
        assertEquals("pi", registry.descriptor("pi").harnessId());
        assertThrows(IllegalArgumentException.class, () -> registry.descriptor("PI"));
        assertThrows(IllegalArgumentException.class, () -> registry.descriptor(null));
        assertThrows(IllegalArgumentException.class, () -> registry.descriptor("missing"));
    }

    @Test
    void rejectsMissingDuplicateAndUnknownHarnesses() {
        IllegalStateException duplicate = assertInvalid(
                registry(adapter("pi", "untested") + adapter("pi", "untested")), Set.of("pi"));
        assertTrue(duplicate.getMessage().contains("duplicate harness_id 'pi'"), duplicate.getMessage());

        IllegalStateException missing = assertInvalid(validRegistry("pi", "untested"), Set.of("pi", "qwen"));
        assertTrue(missing.getMessage().contains("missing registered harnesses [qwen]"), missing.getMessage());

        IllegalStateException unknown = assertInvalid(validRegistry("rogue", "untested"), Set.of("pi"));
        assertAll(
                () -> assertTrue(unknown.getMessage().contains("missing registered harnesses [pi]"),
                        unknown.getMessage()),
                () -> assertTrue(unknown.getMessage().contains("unknown harnesses [rogue]"), unknown.getMessage()));
    }

    @Test
    void rejectsPrematureStatusAndIngressClaims() {
        for (String status : List.of("supported", "experimental", "incompatible")) {
            assertInvalid(validRegistry("pi", status), Set.of("pi"));
        }

        for (String status : List.of("supported", "experimental", "untested")) {
            assertInvalid(validRegistry("bob", status), Set.of("bob"));
        }

        IllegalStateException ingress = assertInvalid(
                validRegistry("pi", "untested").replace("ingress_mode: none", "ingress_mode: extension"),
                Set.of("pi"));
        assertTrue(ingress.getMessage().contains("Unknown Ship adapter ingress mode"), ingress.getMessage());

        for (String invalid : List.of("UNTESTED", "unknown")) {
            IllegalStateException error = assertInvalid(validRegistry("pi", invalid), Set.of("pi"));
            assertTrue(error.getMessage().contains("Unknown Ship adapter support status"), error.getMessage());
        }
        IllegalStateException unknownIngress = assertInvalid(
                validRegistry("pi", "untested").replace("ingress_mode: none", "ingress_mode: unknown"),
                Set.of("pi"));
        assertTrue(unknownIngress.getMessage().contains("Unknown Ship adapter ingress mode"),
                unknownIngress.getMessage());
    }

    @Test
    void requiresEveryProfileToBeExplicitlyNone() {
        for (String field : List.of(
                "interaction_profile", "launcher_profile", "sandbox_profile", "provider_profile",
                "protocol_profile", "evidence_profile")) {
            String valid = validRegistry("pi", "untested");
            IllegalStateException missing = assertInvalid(valid.replace("    " + field + ": none\n", ""),
                    Set.of("pi"));
            assertTrue(missing.getMessage().contains(field), missing.getMessage());

            IllegalStateException nullValue = assertInvalid(
                    valid.replace(field + ": none", field + ":"), Set.of("pi"));
            assertTrue(nullValue.getMessage().contains(field), nullValue.getMessage());

            IllegalStateException claimed = assertInvalid(
                    valid.replace(field + ": none", field + ": candidate-v1"), Set.of("pi"));
            assertTrue(claimed.getMessage().contains(field + " must be none"), claimed.getMessage());
        }
    }

    @Test
    void rejectsLegacyEvidenceAndCapabilityFields() {
        Map<String, String> legacyFields = Map.of(
                "protocol_version", "protocol_version: \"1.0\"\n",
                "tested_version_range", "    tested_version_range: \"!=0\"\n",
                "capabilities", "    capabilities: {}\n",
                "evidence_ids", "    evidence_ids: []\n",
                "native_ingress_kind", "    native_ingress_kind: prompt_command\n");

        legacyFields.forEach((field, line) -> {
            String valid = validRegistry("pi", "untested");
            String invalid = field.equals("protocol_version")
                    ? line + valid
                    : valid.replace("    reason:", line + "    reason:");
            IllegalStateException error = assertInvalid(invalid, Set.of("pi"));
            assertTrue(error.getMessage().contains(field), error.getMessage());
        });
    }

    @Test
    void parsesOnlyOneStrictBoundedRegistryDocument() {
        String valid = validRegistry("pi", "untested");

        assertInvalid("", Set.of("pi"));
        assertInvalid("registry_schema_version: [", Set.of("pi"));
        assertInvalid(valid.replace("registry_schema_version: 1", "registry_schema_version: 2"), Set.of("pi"));
        assertInvalid(valid.replace("registry_schema_version: 1", "registry_schema_version: \"1\""), Set.of("pi"));
        assertInvalid(valid.replace("registry_schema_version: 1\n", ""), Set.of("pi"));
        assertInvalid(valid.replace("    status: untested", "    status: untested\n    status: untested"),
                Set.of("pi"));
        assertInvalid(valid + "\n---\nregistry_schema_version: 1\nadapters: []\n", Set.of("pi"));
        assertInvalid(valid.replace("    reason:", "    unknown_field: value\n    reason:"), Set.of("pi"));
        for (String field : List.of("status", "ingress_mode")) {
            assertInvalid(valid.replace("    " + field + ": " + (field.equals("status") ? "untested" : "none")
                                        + "\n",
                    ""), Set.of("pi"));
            assertInvalid(valid.replace(field + ": " + (field.equals("status") ? "untested" : "none"),
                    field + ':'), Set.of("pi"));
        }
        assertInvalid(valid.replace(
                "reason: No certified Ship adapter tuple is registered for this harness.", "reason: 1"),
                Set.of("pi"));

        byte[] oversized = new byte[64 * 1024 + 1];
        Arrays.fill(oversized, (byte) 'x');
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> ShipAdapterRegistry.load(new ByteArrayInputStream(oversized), Set.of("pi")));
        assertTrue(error.getMessage().contains("exceeds 65536 bytes"), error.getMessage());
        assertThrows(IllegalStateException.class, () -> ShipAdapterRegistry.load(null, Set.of("pi")));
        assertThrows(IllegalStateException.class, () -> ShipAdapterRegistry.load(stream(valid), null));
    }

    @Test
    void acceptsExactLimitsAndRejectsFirstValuesOver() {
        String idAtLimit = "a".repeat(64);
        assertEquals(idAtLimit, load(validRegistry(idAtLimit, "untested"), Set.of(idAtLimit))
                .descriptor(idAtLimit).harnessId());
        assertInvalid(validRegistry("a".repeat(65), "untested"), Set.of("a".repeat(65)));

        String valid = validRegistry("pi", "untested");
        String reasonAtLimit = "x".repeat(512);
        assertEquals(reasonAtLimit, load(valid.replace(
                "No certified Ship adapter tuple is registered for this harness.", reasonAtLimit), Set.of("pi"))
                .descriptor("pi").reason());

        int padding = 64 * 1024 - valid.getBytes(StandardCharsets.UTF_8).length;
        assertEquals("pi", load(valid + " ".repeat(padding), Set.of("pi")).descriptor("pi").harnessId());

        List<String> idsAtLimit = IntStream.range(0, 64).mapToObj(index -> "h" + index).toList();
        assertEquals(64, load(registryFor(idsAtLimit), Set.copyOf(idsAtLimit)).descriptors().size());
        List<String> idsOverLimit = IntStream.range(0, 65).mapToObj(index -> "h" + index).toList();
        IllegalStateException overLimit = assertInvalid(registryFor(idsOverLimit), Set.copyOf(idsOverLimit));
        assertTrue(overLimit.getMessage().contains("at most 64 rows"), overLimit.getMessage());
    }

    @Test
    void validatesCanonicalIdentifiersAndDisplayOnlyReason() {
        String valid = validRegistry("pi", "untested");

        assertInvalid(valid.replace("harness_id: pi", "harness_id: PI"), Set.of("pi"));
        assertInvalid(valid.replace("interaction_profile: none", "interaction_profile: NONE"), Set.of("pi"));
        assertInvalid(valid.replace("reason: No", "reason: \"line\\nnext\" # No"), Set.of("pi"));
        assertInvalid(valid.replace("reason: No", "reason: Caf\u00e9 # No"), Set.of("pi"));
        assertInvalid(valid.replace(
                "reason: No certified Ship adapter tuple is registered for this harness.",
                "reason: \"" + "x".repeat(513) + "\""), Set.of("pi"));

        ShipAdapterRegistry registry = load(valid.replace(
                "No certified Ship adapter tuple is registered for this harness.", "supported"), Set.of("pi"));
        assertEquals(SupportStatus.UNTESTED, registry.descriptor("pi").status());
    }

    @Test
    void compiledPackageSurfaceIsClosed() throws Exception {
        Path classes = Path.of(ShipAdapterRegistry.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
        Path packageDirectory = classes.resolve(PACKAGE.replace('.', '/'));
        Set<String> actualClasses = new HashSet<>();
        try (var paths = Files.walk(packageDirectory)) {
            for (Path classFile : paths.filter(path -> path.toString().endsWith(".class")).toList()) {
                String className = className(classes, classFile);
                actualClasses.add(className);
                Class<?> type = Class.forName(className, false, ShipAdapterRegistry.class.getClassLoader());
                assertFalse(Modifier.isPublic(type.getModifiers()), className);
                assertEquals(NON_PRIVATE_METHODS.get(className), Arrays.stream(type.getDeclaredMethods())
                        .filter(method -> !Modifier.isPrivate(method.getModifiers()))
                        .map(ShipAdapterRegistryTest::signature)
                        .collect(Collectors.toSet()), className + " methods changed");
                assertEquals(NON_PRIVATE_FIELDS.get(className), Arrays.stream(type.getDeclaredFields())
                        .filter(field -> !Modifier.isPrivate(field.getModifiers()))
                        .map(ShipAdapterRegistryTest::signature)
                        .collect(Collectors.toSet()), className + " fields changed");
                assertEquals(NON_PRIVATE_CONSTRUCTORS.get(className), Arrays.stream(type.getDeclaredConstructors())
                        .filter(constructor -> !Modifier.isPrivate(constructor.getModifiers()))
                        .map(ShipAdapterRegistryTest::signature)
                        .collect(Collectors.toSet()), className + " constructors changed");
            }
        }
        assertEquals(EXPECTED_CLASSES, actualClasses, "Ship adapter package types changed");
    }

    @Test
    void compiledPackageUsesOnlyApprovedDependencies() throws Exception {
        Path classes = Path.of(ShipAdapterRegistry.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
        Path packageDirectory = classes.resolve(PACKAGE.replace('.', '/'));
        ToolProvider jdeps = ToolProvider.findFirst("jdeps").orElseThrow(
                () -> new AssertionError("Ship adapter boundary test requires a JDK with jdeps"));
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
                if (target.startsWith("java.")) {
                    assertEquals("java.base", module, line);
                }
            }
        }
        assertTrue(sourceDependencies > 0, () -> "jdeps reported no adapter dependencies:\n" + output);
        assertEquals(ALLOWED_EXTERNAL_TYPES, externalTypes);
    }

    private static void assertProfilesNone(Adapter adapter) {
        assertAll(
                () -> assertEquals(IngressMode.NONE, adapter.ingressMode(), adapter.harnessId()),
                () -> assertEquals(NONE, adapter.interactionProfile(), adapter.harnessId()),
                () -> assertEquals(NONE, adapter.launcherProfile(), adapter.harnessId()),
                () -> assertEquals(NONE, adapter.sandboxProfile(), adapter.harnessId()),
                () -> assertEquals(NONE, adapter.providerProfile(), adapter.harnessId()),
                () -> assertEquals(NONE, adapter.protocolProfile(), adapter.harnessId()),
                () -> assertEquals(NONE, adapter.evidenceProfile(), adapter.harnessId()));
    }

    private static String signature(Method method) {
        String parameters = Arrays.stream(method.getGenericParameterTypes())
                .map(type -> type.getTypeName())
                .collect(Collectors.joining(","));
        String prefix = Modifier.isStatic(method.getModifiers()) ? "static " : "";
        return prefix + method.getName() + '(' + parameters + ")->" + method.getGenericReturnType().getTypeName();
    }

    private static String signature(Field field) {
        String prefix = Modifier.isStatic(field.getModifiers()) ? "static " : "";
        String suffix = Modifier.isFinal(field.getModifiers()) ? "final " : "";
        return prefix + suffix + field.getName() + ':' + field.getGenericType().getTypeName();
    }

    private static String signature(Constructor<?> constructor) {
        return Arrays.stream(constructor.getGenericParameterTypes())
                .map(type -> type.getTypeName())
                .collect(Collectors.joining(",", "(", ")"));
    }

    private static String className(Path classes, Path classFile) {
        String relative = classes.relativize(classFile).toString();
        return relative.substring(0, relative.length() - ".class".length())
                .replace(classFile.getFileSystem().getSeparator(), ".");
    }

    private static IllegalStateException assertInvalid(String yaml, Set<String> expectedHarnesses) {
        return assertThrows(IllegalStateException.class, () -> load(yaml, expectedHarnesses));
    }

    private static ShipAdapterRegistry load(String yaml, Set<String> expectedHarnesses) {
        return ShipAdapterRegistry.load(stream(yaml), expectedHarnesses);
    }

    private static ByteArrayInputStream stream(String yaml) {
        return new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8));
    }

    private static String validRegistry(String firstId, String firstStatus, String... remainingIdStatus) {
        if (remainingIdStatus.length % 2 != 0) {
            throw new IllegalArgumentException("Harness/status arguments must be paired");
        }
        StringBuilder adapters = new StringBuilder(adapter(firstId, firstStatus));
        for (int index = 0; index < remainingIdStatus.length; index += 2) {
            adapters.append(adapter(remainingIdStatus[index], remainingIdStatus[index + 1]));
        }
        return registry(adapters.toString());
    }

    private static String registryFor(List<String> harnessIds) {
        return registry(harnessIds.stream().map(id -> adapter(id, "untested")).collect(Collectors.joining()));
    }

    private static String registry(String adapters) {
        return "registry_schema_version: 1\nadapters:\n" + adapters;
    }

    private static String adapter(String harnessId, String status) {
        return String.format(Locale.ROOT, """
                  - harness_id: %s
                    status: %s
                    ingress_mode: none
                    interaction_profile: none
                    launcher_profile: none
                    sandbox_profile: none
                    provider_profile: none
                    protocol_profile: none
                    evidence_profile: none
                    reason: No certified Ship adapter tuple is registered for this harness.
                """, harnessId, status);
    }
}
