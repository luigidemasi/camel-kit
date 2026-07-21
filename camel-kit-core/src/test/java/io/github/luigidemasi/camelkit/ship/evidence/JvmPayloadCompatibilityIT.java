package io.github.luigidemasi.camelkit.ship.evidence;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;

import io.github.luigidemasi.camelkit.ship.artifact.CitrusDependencyPolicy;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogUsageRecord.RuntimeDependency;
import io.github.luigidemasi.camelkit.ship.evidence.EvidenceSandboxLauncher.Access;
import io.github.luigidemasi.camelkit.ship.evidence.EvidenceSandboxLauncher.Invocation;
import io.github.luigidemasi.camelkit.ship.evidence.EvidenceSandboxLauncher.Mount;
import io.github.luigidemasi.camelkit.ship.resolver.ShipMavenResolver;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

/** Explicit network and bubblewrap conformance for every packaged Ship JVM compatibility row. */
class JvmPayloadCompatibilityIT {

    private static final String JDK_DIGEST = "sha256:" + "1".repeat(64);
    private static final String SANDBOX_ARCHIVE = JvmPayloadArchive.SANDBOX_ARCHIVE;
    private static final String SANDBOX_JAVA = "/opt/camel-kit/jdk/bin/java";
    private static final Duration PROCESS_TIMEOUT = Duration.ofMinutes(2);
    private static final Set<Row> FUNCTIONALLY_TESTED_ROWS = Set.of(
            new Row("4.21.0", "5.0.0-M2"),
            new Row("4.18.3", "5.0.0-M2"));
    private static final String RELOCATED_RESOLVER_CLASS
            = "io/github/luigidemasi/camelkit/ship/resolver/internal/org/eclipse/aether/RepositorySystem.class";

    @TempDir
    Path directory;

    @BeforeAll
    static void packagedResolverIsTheResolverUnderTest() throws Exception {
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
    void explicitFunctionalRowsCoverTheEntirePackagedPolicy() throws Exception {
        Properties policy = new Properties();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("distribution.properties")) {
            assertNotNull(input, "Missing packaged distribution.properties");
            policy.load(input);
        }
        Set<String> validatorVersions = Set.of(
                policy.getProperty("ship.evidence.camel-yaml-validator.supported").split(","));
        Set<Row> citrusRows = policy.stringPropertyNames().stream()
                .filter(key -> key.startsWith("ship.evidence.citrus."))
                .filter(key -> key.endsWith(".camel.supported"))
                .flatMap(key -> {
                    String citrusVersion = key.substring(
                            "ship.evidence.citrus.".length(), key.length() - ".camel.supported".length());
                    return List.of(policy.getProperty(key).split(",")).stream()
                            .map(camelVersion -> new Row(camelVersion.trim(), citrusVersion));
                })
                .collect(java.util.stream.Collectors.toUnmodifiableSet());

        assertEquals(FUNCTIONALLY_TESTED_ROWS, citrusRows);
        assertEquals(
                FUNCTIONALLY_TESTED_ROWS.stream().map(Row::camelVersion).collect(java.util.stream.Collectors.toSet()),
                validatorVersions);
    }

    @Test
    void camel421PayloadsMaterializeAndRunTheirFunctionalEvidenceInIsolation() throws Exception {
        requirePayloads("4.21.0", "5.0.0-M2");
    }

    @Test
    void camel418PayloadsMaterializeAndRunTheirFunctionalEvidenceInIsolation() throws Exception {
        requirePayloads("4.18.3", "5.0.0-M2");
    }

    private void requirePayloads(String camelVersion, String citrusVersion) throws Exception {
        Path workspace = Files.createDirectory(directory.resolve("workspace"));
        Path route = Files.writeString(workspace.resolve("orders.camel.yaml"), """
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
                JvmPayloadRequest.mainPackage(camelVersion),
                JvmPayloadRequest.yamlValidator(camelVersion),
                JvmPayloadRequest.camelMain(camelVersion, acceptedRuntime),
                JvmPayloadRequest.citrus(
                        camelVersion, citrusVersion, CitrusDependencyPolicy.required(citrusVersion),
                        acceptedRuntime));
        for (JvmPayloadRequest request : requests) {
            Path root = Files.createDirectory(directory.resolve(request.kind().id()));
            JvmPayloadArchive.Identity payload = JvmPayloadArchive.materialize(root, request, JDK_DIGEST);
            requireIsolatedLaunch(payload.archive(), workspace, request, List.of("--payload-version"), true);
            requireIsolatedLaunch(
                    payload.archive(), workspace, request, functionalArguments(request, digest(route)), false);
        }
    }

    private static List<String> functionalArguments(JvmPayloadRequest request, String routeDigest) {
        return switch (request.kind()) {
            case MAIN_PACKAGE_INSPECT -> List.of(
                    "--candidate-digest=" + digest(1),
                    "--manifest-digest=" + digest(2),
                    "--catalog-usage-digest=" + digest(3),
                    "--pom-digest=" + digest(4),
                    "--main-payload-digest=" + JvmPayloadRequest.camelMain(
                            request.camelVersion(), List.of(
                                    new RuntimeDependency(
                                            "org.apache.camel", "camel-kafka",
                                            request.camelVersion(), "compile"),
                                    new RuntimeDependency(
                                            "org.apache.camel", "camel-main",
                                            request.camelVersion(), "compile"),
                                    new RuntimeDependency(
                                            "org.apache.camel", "camel-yaml-dsl",
                                            request.camelVersion(), "compile")))
                            .digest(),
                    "--route=orders.camel.yaml", "--route-digest=" + routeDigest);
            case CAMEL_YAML_VALIDATE -> List.of("/workspace/orders.camel.yaml");
            case CAMEL_MAIN_START -> List.of(
                    "--route=/workspace/orders.camel.yaml", "--expected-route=orders");
            case CITRUS_YAML -> List.of(
                    "--route=/workspace/orders.camel.yaml", "--expected-route=orders",
                    "--test=/workspace/orders.camel.it.yaml");
            default -> throw new IllegalArgumentException("Unsupported compatibility request " + request.kind());
        };
    }

    private void requireIsolatedLaunch(
            Path archive,
            Path workspace,
            JvmPayloadRequest request,
            List<String> launcherArguments,
            boolean assertVersions)
            throws Exception {
        Path javaHome = Path.of(System.getProperty("java.home")).toRealPath();
        Path java = javaHome.resolve("bin/java").toRealPath();
        String launch = assertVersions ? "version" : "functional";
        Path home = Files.createDirectory(directory.resolve(request.kind().id() + "-home-" + launch));
        Path temporary = Files.createDirectory(
                directory.resolve(request.kind().id() + "-tmp-" + launch));

        List<Mount> mounts = List.of(
                new Mount(Path.of("/usr/lib").toRealPath(), "/usr/lib", Access.READ_ONLY),
                new Mount(Path.of("/usr/lib64").toRealPath(), "/usr/lib64", Access.READ_ONLY),
                new Mount(javaHome, "/opt/camel-kit/jdk", Access.READ_ONLY),
                new Mount(workspace.toRealPath(), "/workspace", Access.READ_ONLY),
                new Mount(home.toRealPath(), "/home/camel-kit", Access.READ_WRITE),
                new Mount(temporary.toRealPath(), "/tmp", Access.READ_WRITE),
                new Mount(archive.toRealPath(), SANDBOX_ARCHIVE, Access.READ_ONLY));
        List<String> arguments = new ArrayList<>(
                List.of(
                        SANDBOX_JAVA, "-cp", SANDBOX_ARCHIVE,
                        ShipJvmPayloadBootstrap.class.getName()));
        arguments.addAll(launcherArguments);
        Map<String, String> environment = new LinkedHashMap<>();
        environment.put("HOME", "/home/camel-kit");
        environment.put("TMPDIR", "/tmp");
        environment.put("JAVA_HOME", "/opt/camel-kit/jdk");
        environment.put("JAVA_TOOL_OPTIONS", "-Duser.home=/home/camel-kit -Djava.io.tmpdir=/tmp");
        environment.put("CAMEL_KIT_JDK_DIGEST", JDK_DIGEST);
        environment.put("LANG", "C");
        environment.put("LC_ALL", "C");

        Invocation invocation = new Invocation(
                workspace, workspace, "/workspace", home, temporary, java,
                mounts, arguments, environment);
        Process process = new BubblewrapSandboxLauncher().launch(invocation);
        boolean exited = process.waitFor(PROCESS_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        if (!exited) {
            process.destroyForcibly();
            assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Timed-out payload process did not terminate");
            fail("Timed out launching isolated " + request.kind().id() + " payload");
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                        + new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.exitValue(), output);
        if (assertVersions) {
            if (request.kind() == JvmPayloadRequest.Kind.MAIN_PACKAGE_INSPECT) {
                assertTrue(output.contains(
                        io.github.luigidemasi.camelkit.ship.evidence.launcher.ShipMainPackageMain.PAYLOAD_VERSION),
                        output);
            } else {
                assertTrue(output.contains(request.camelVersion()), output);
            }
            if (request.citrusVersion() != null) {
                assertTrue(output.contains(request.citrusVersion()), output);
            }
        }
    }

    private static String digest(Path file) throws Exception {
        return "sha256:" + java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }

    private static String digest(int seed) {
        return "sha256:" + String.format(Locale.ROOT, "%064x", seed);
    }

    private record Row(String camelVersion, String citrusVersion) {
    }
}
