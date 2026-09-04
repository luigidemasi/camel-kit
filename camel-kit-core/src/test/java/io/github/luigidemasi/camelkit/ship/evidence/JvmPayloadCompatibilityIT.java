package io.github.luigidemasi.camelkit.ship.evidence;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;
import java.util.stream.Stream;

import io.github.luigidemasi.camelkit.ship.artifact.CitrusDependencyPolicy;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogUsageRecord.RuntimeDependency;
import io.github.luigidemasi.camelkit.ship.resolver.ShipMavenResolver;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.*;

/** Explicit network and direct-launch conformance for every packaged Ship JVM compatibility row. */
class JvmPayloadCompatibilityIT {

    private static final Duration PROCESS_TIMEOUT = Duration.ofMinutes(2);
    private static final int MAX_RETAINED_OUTPUT_BYTES = 8 * 1024 * 1024;
    private static final String RELOCATED_RESOLVER_CLASS
            = "io/github/luigidemasi/camelkit/ship/resolver/internal/org/eclipse/aether/RepositorySystem.class";

    @TempDir
    Path directory;

    @BeforeAll
    static void packagedResolverIsTheResolverUnderTest() throws Exception {
        assertEquals("Linux", System.getProperty("os.name"),
                "The linux-ship-certification profile requires Linux");
        String configured = System.getProperty("ship.resolver.jar");
        assertNotNull(configured, "Compatibility certification requires the packaged resolver path");
        Path expected = Path.of(configured).toRealPath();
        assertTrue(Files.isRegularFile(expected), "Packaged resolver is absent: " + expected);
        assertNotNull(ShipMavenResolver.class.getProtectionDomain().getCodeSource());
        Path actual = Path.of(ShipMavenResolver.class.getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI())
                .toRealPath();
        assertEquals(expected, actual, "Compatibility certification loaded an unshaded reactor resolver");
        try (JarFile resolver = new JarFile(actual.toFile())) {
            assertNotNull(resolver.getJarEntry(RELOCATED_RESOLVER_CLASS),
                    "Compatibility certification resolver lacks its relocated implementation");
        }
    }

    @Test
    void validatorAndCitrusEvidenceCoverTheSameCamelVersions() throws Exception {
        Properties policy = packagedPolicy();
        Set<String> validatorVersions = csv(
                policy.getProperty("ship.evidence.camel-yaml-validator.supported"));
        Set<Row> citrusRows = packagedFunctionalRows(policy);

        assertEquals(
                citrusRows.stream().map(Row::camelVersion).collect(java.util.stream.Collectors.toSet()),
                validatorVersions);
    }

    @ParameterizedTest(name = "Camel {0}")
    @MethodSource("functionalRows")
    void packagedPayloadsMaterializeAndRunTheirFunctionalEvidenceInIsolation(Row row) throws Exception {
        requirePayloads(row.camelVersion(), row.citrusVersion());
    }

    static Stream<Row> functionalRows() throws Exception {
        return packagedFunctionalRows(packagedPolicy()).stream()
                .sorted(java.util.Comparator.comparing(Row::camelVersion).thenComparing(Row::citrusVersion));
    }

    private static Properties packagedPolicy() throws IOException {
        Properties policy = new Properties();
        try (InputStream input = JvmPayloadCompatibilityIT.class.getClassLoader()
                .getResourceAsStream("distribution.properties")) {
            assertNotNull(input, "Missing packaged distribution.properties");
            policy.load(input);
        }
        return policy;
    }

    private static Set<Row> packagedFunctionalRows(Properties policy) {
        return policy.stringPropertyNames().stream()
                .filter(key -> key.startsWith("ship.evidence.citrus."))
                .filter(key -> key.endsWith(".camel.supported"))
                .flatMap(key -> {
                    String citrusVersion = key.substring(
                            "ship.evidence.citrus.".length(), key.length() - ".camel.supported".length());
                    return csv(policy.getProperty(key)).stream()
                            .map(camelVersion -> new Row(camelVersion, citrusVersion));
                })
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static Set<String> csv(String values) {
        return Stream.of(values.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private void requirePayloads(String camelVersion, String citrusVersion) throws Exception {
        Path workspace = Files.createDirectory(directory.resolve("workspace"));
        Files.writeString(workspace.resolve("orders.camel.yaml"), """
                - route:
                    id: orders
                    from:
                      uri: kafka:orders
                      steps:
                        - setBody:
                            simple: accepted-order
                """);
        Files.writeString(workspace.resolve("orders.camel.it.yaml"), """
                name: orders-behavior
                actions:
                  - send:
                      endpoint: "camel:sync:direct:camel-kit-ship-test-orders"
                      message:
                        headers:
                          - name: requestId
                            value: req-1
                          - name: strategy
                            value: priority
                        body:
                          data: priority-order
                  - receive:
                      endpoint: "camel:sync:direct:camel-kit-ship-test-orders"
                      message:
                        headers:
                          - name: requestId
                            value: req-1
                        body:
                          data: accepted-order
                  - send:
                      endpoint: "camel:sync:direct:camel-kit-ship-test-orders"
                      message:
                        body:
                          data: standard-order
                  - receive:
                      endpoint: "camel:sync:direct:camel-kit-ship-test-orders"
                      message:
                        body:
                          data: accepted-order
                """);

        List<RuntimeDependency> acceptedRuntime = List.of(
                new RuntimeDependency("org.apache.camel", "camel-kafka", camelVersion, "compile"),
                new RuntimeDependency("org.apache.camel", "camel-main", camelVersion, "compile"),
                new RuntimeDependency("org.apache.camel", "camel-yaml-dsl", camelVersion, "compile"));
        List<JvmPayloadRequest> requests = List.of(
                JvmPayloadRequest.yamlValidator(camelVersion),
                JvmPayloadRequest.camelMain(camelVersion, acceptedRuntime),
                JvmPayloadRequest.citrus(
                        camelVersion, citrusVersion, CitrusDependencyPolicy.required(citrusVersion),
                        acceptedRuntime));
        for (JvmPayloadRequest request : requests) {
            Path root = Files.createDirectory(directory.resolve(request.kind().id()));
            Path payload = JvmPayloadArchive.materialize(root, request);
            requireIsolatedLaunch(payload, workspace, request, functionalArguments(request));
        }
    }

    private static List<String> functionalArguments(JvmPayloadRequest request) {
        return switch (request.kind()) {
            case CAMEL_YAML_VALIDATE -> List.of("orders.camel.yaml");
            case CAMEL_MAIN_START -> List.of(
                    "--route=orders.camel.yaml", "--expected-route=orders");
            case CITRUS_YAML -> List.of(
                    "--route=orders.camel.yaml", "--expected-route=orders",
                    "--test=orders.camel.it.yaml");
            default -> throw new IllegalArgumentException("Unsupported compatibility request " + request.kind());
        };
    }

    private void requireIsolatedLaunch(
            Path archive,
            Path workspace,
            JvmPayloadRequest request,
            List<String> launcherArguments)
            throws Exception {
        Path java = Path.of(System.getProperty("java.home"), "bin", "java").toRealPath();
        Path acceptedRoot = workspace.toRealPath();
        // Space-bearing names prove the sandbox JVM options survive as argv entries.
        Path home = Files.createDirectory(directory.resolve(request.kind().id() + " home"));
        Path temporary = Files.createDirectory(directory.resolve(request.kind().id() + " tmp"));

        List<String> arguments = new ArrayList<>(
                List.of(
                        java.toString(),
                        "-Duser.home=" + home, "-Djava.io.tmpdir=" + temporary,
                        "-cp", archive.toRealPath().toString(),
                        ShipJvmPayloadBootstrap.class.getName(),
                        "--launcher=" + request.launcherClass(),
                        "--accepted-root=" + acceptedRoot));
        arguments.addAll(launcherArguments);
        ProcessBuilder builder = new ProcessBuilder(arguments);
        builder.directory(acceptedRoot.toFile());
        Map<String, String> environment = builder.environment();
        environment.clear();
        environment.put("LANG", "C");
        environment.put("LC_ALL", "C");
        environment.put("HOME", home.toString());
        environment.put("TMPDIR", temporary.toString());

        Process process = builder.start();
        ExecutorService readers = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "camel-kit-ship-compatibility-output");
            thread.setDaemon(true);
            return thread;
        });
        Future<CapturedOutput> stdout = readers.submit(() -> drain(process.getInputStream()));
        Future<CapturedOutput> stderr = readers.submit(() -> drain(process.getErrorStream()));
        CapturedOutput capturedStdout;
        CapturedOutput capturedStderr;
        try {
            boolean exited = process.waitFor(PROCESS_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            if (!exited) {
                process.destroyForcibly();
                assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Timed-out payload process did not terminate");
                fail("Timed out launching isolated " + request.kind().id() + " payload");
            }
            capturedStdout = stdout.get(30, TimeUnit.SECONDS);
            capturedStderr = stderr.get(30, TimeUnit.SECONDS);
        } finally {
            readers.shutdownNow();
        }
        String output = capturedStdout.text() + capturedStderr.text();
        assertFalse(capturedStdout.truncated() || capturedStderr.truncated(),
                "Isolated payload output exceeded " + MAX_RETAINED_OUTPUT_BYTES + " bytes per stream\n" + output);
        assertEquals(0, process.exitValue(), output);
    }

    private static CapturedOutput drain(InputStream input) throws IOException {
        ByteArrayOutputStream retained = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        boolean truncated = false;
        int count;
        while ((count = input.read(buffer)) != -1) {
            int remaining = MAX_RETAINED_OUTPUT_BYTES - retained.size();
            if (remaining > 0) {
                retained.write(buffer, 0, Math.min(count, remaining));
            }
            truncated |= count > remaining;
        }
        return new CapturedOutput(retained.toByteArray(), truncated);
    }

    private record Row(String camelVersion, String citrusVersion) {
    }

    private record CapturedOutput(byte[] bytes, boolean truncated) {

        private String text() {
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }
}
