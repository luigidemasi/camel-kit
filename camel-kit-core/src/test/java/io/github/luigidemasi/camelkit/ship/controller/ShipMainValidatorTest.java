package io.github.luigidemasi.camelkit.ship.controller;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.github.luigidemasi.camelkit.ship.ShipDigest;
import io.github.luigidemasi.camelkit.ship.artifact.ArtifactManifest;
import io.github.luigidemasi.camelkit.ship.artifact.ArtifactManifest.DeclaredArtifact;
import io.github.luigidemasi.camelkit.ship.artifact.ArtifactManifest.JavaPolicy;
import io.github.luigidemasi.camelkit.ship.artifact.ArtifactManifest.RouteArtifact;
import io.github.luigidemasi.camelkit.ship.artifact.ArtifactManifest.TestArtifact;
import io.github.luigidemasi.camelkit.ship.artifact.ArtifactPolicy;
import io.github.luigidemasi.camelkit.ship.artifact.ArtifactPolicy.RouteContract;
import io.github.luigidemasi.camelkit.ship.artifact.CitrusDependencyPolicy;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogTestVerifier;
import io.github.luigidemasi.camelkit.ship.catalog.ShipCatalogService;
import io.github.luigidemasi.camelkit.ship.evidence.CommandEvidence;
import io.github.luigidemasi.camelkit.ship.evidence.CommandEvidence.SandboxIdentity;
import io.github.luigidemasi.camelkit.ship.evidence.EvidenceCommand;
import io.github.luigidemasi.camelkit.ship.evidence.EvidenceRunner;
import io.github.luigidemasi.camelkit.ship.evidence.JvmPayloadArchive;
import io.github.luigidemasi.camelkit.ship.evidence.JvmPayloadRequest;
import io.github.luigidemasi.camelkit.ship.evidence.JvmPayloadTestFixture;
import io.github.luigidemasi.camelkit.ship.evidence.ShipLocalStamp;
import io.github.luigidemasi.camelkit.ship.evidence.ShipLocalStamp.Outcome;
import io.github.luigidemasi.camelkit.ship.evidence.ShipLocalStamp.Support;
import io.github.luigidemasi.camelkit.ship.evidence.ShipLocalStamp.ToolVersion;
import io.github.luigidemasi.camelkit.ship.evidence.ShipLocalStampStore;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipMainValidatorTest {

    private static final String RUN_ID = "ship-" + "a".repeat(32);
    private static final String CITRUS_VERSION = "5.0.0-M2";
    private static final List<String> CITRUS_DEPENDENCIES
            = CitrusDependencyPolicy.required(CITRUS_VERSION);
    private static final Instant STARTED = Instant.parse("2026-07-29T10:00:00Z");
    private static final Instant ENDED = Instant.parse("2026-07-29T10:00:01Z");
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-29T10:00:02Z"), ZoneOffset.UTC);
    private static final long MAX_TOTAL_LOG_BYTES = 64L * 1024 * 1024;
    private static final ToolVersion PI = new ToolVersion(
            "pi",
            "/usr/bin/pi",
            "0.83.0",
            Support.SUPPORTED,
            null);
    private static final ToolVersion NODE = new ToolVersion(
            "node",
            "/usr/bin/node",
            "22.22.2",
            Support.SUPPORTED,
            null);

    @TempDir
    Path tempDir;

    @Test
    void runsTheDeterministicPlanAndPersistsAPassStamp() throws Exception {
        Project project = project("orders", "payments");
        RecordingExecutor executor = new RecordingExecutor();

        ShipMainValidator.Result result = validator(executor).validate(
                RUN_ID,
                project.root(),
                project.manifest(),
                project.policy(),
                snapshot(),
                tempDir.resolve("evidence"),
                PI,
                NODE,
                CLOCK);

        assertEquals(ShipLocalStamp.Status.PASS, result.stamp().status());
        assertEquals(
                List.of(
                        "pi",
                        "node",
                        "java",
                        "camel",
                        "os",
                        "architecture"),
                result.stamp().toolVersions().stream()
                        .map(ToolVersion::tool)
                        .toList());
        assertTrue(result.stamp().toolVersions().subList(0, 2).stream()
                .allMatch(tool -> tool.support() == Support.SUPPORTED
                        && tool.message() == null));
        assertTrue(result.stamp().toolVersions().subList(2, 6).stream()
                .allMatch(tool -> tool.support() == Support.UNTESTED
                        && tool.message() != null));
        assertEquals(
                "4.21.0",
                result.stamp().toolVersions().get(3).version());
        assertEquals(List.of(
                "artifact-policy",
                "catalog-usage",
                "route-schema",
                "main-runtime-resolve-and-start",
                "citrus-integration-test-001",
                "citrus-integration-test-002"),
                result.stamp().checks().stream().map(ShipLocalStamp.Check::id).toList());
        assertEquals(List.of(
                "route-schema",
                "main-runtime-resolve-and-start",
                "citrus-integration-test-001",
                "citrus-integration-test-002"),
                executor.commands.stream().map(EvidenceCommand::id).toList());
        String manifestDigest = ShipDigest.sha256(
                Files.readAllBytes(project.manifest()));
        assertTrue(executor.commands.stream()
                .allMatch(command -> manifestDigest.equals(
                        command.inputDigests().get(0))));
        assertEquals(result.stamp(),
                ShipLocalStampStore.read(tempDir.resolve("evidence"), RUN_ID));
    }

    @Test
    @ResourceLock("systemProperties")
    void diagnosticOnlyPlatformMetadataCannotFailValidation()
            throws Exception {
        Project project = project("orders");
        RecordingExecutor executor = new RecordingExecutor();
        ShipCatalogService.Snapshot catalog = snapshot();
        String osVersion = System.getProperty("os.version");
        String architecture = System.getProperty("os.arch");
        ShipMainValidator.Result result;
        try {
            System.setProperty("os.version", " ");
            System.setProperty("os.arch", "\u202e");
            result = validator(executor).validate(
                    RUN_ID,
                    project.root(),
                    project.manifest(),
                    project.policy(),
                    catalog,
                    tempDir.resolve("diagnostic-evidence"),
                    PI,
                    NODE,
                    CLOCK);
        } finally {
            restoreProperty("os.version", osVersion);
            restoreProperty("os.arch", architecture);
        }

        assertEquals(ShipLocalStamp.Status.PASS, result.stamp().status());
        assertTrue(result.stamp().toolVersions().stream()
                .filter(tool -> "os".equals(tool.tool()))
                .findFirst()
                .orElseThrow()
                .version()
                .endsWith(" unknown"));
        assertEquals(
                "unknown",
                result.stamp().toolVersions().stream()
                        .filter(tool -> "architecture".equals(tool.tool()))
                        .findFirst()
                        .orElseThrow()
                        .version());
    }

    @Test
    void invalidArtifactsAndEmptyRequiredTestsLaunchNothing() throws Exception {
        Project invalid = project("orders");
        Files.writeString(invalid.root().resolve(".camel-kit/config.properties"), "\nchanged=true\n",
                java.nio.file.StandardOpenOption.APPEND);
        RecordingExecutor invalidExecutor = new RecordingExecutor();

        ShipMainValidator.Result invalidResult = validator(invalidExecutor).validate(
                RUN_ID,
                invalid.root(),
                invalid.manifest(),
                invalid.policy(),
                snapshot(),
                tempDir.resolve("invalid-evidence"),
                PI,
                NODE,
                CLOCK);

        Project empty = project();
        RecordingExecutor emptyExecutor = new RecordingExecutor();
        ShipMainValidator.Result emptyResult = validator(emptyExecutor).validate(
                RUN_ID,
                empty.root(),
                empty.manifest(),
                empty.policy(),
                snapshot(),
                tempDir.resolve("empty-evidence"),
                PI,
                NODE,
                CLOCK);

        assertEquals(ShipLocalStamp.Status.FAIL, invalidResult.stamp().status());
        assertEquals(ShipLocalStamp.Status.FAIL, emptyResult.stamp().status());
        assertTrue(invalidExecutor.commands.isEmpty());
        assertTrue(emptyExecutor.commands.isEmpty());
    }

    @Test
    void invalidCatalogDependenciesLaunchNothingAndPersistFailure() throws Exception {
        Project project = project("orders");
        Path pom = project.root().resolve("pom.xml");
        Files.writeString(pom, Files.readString(pom).replace(
                """
                            <dependency><groupId>org.apache.camel</groupId>
                              <artifactId>camel-direct</artifactId><version>4.21.0</version></dependency>
                        """, ""));
        writeManifest(project.root(), project.routes(), project.tests());
        RecordingExecutor executor = new RecordingExecutor();

        ShipMainValidator.Result result = validator(executor).validate(
                RUN_ID,
                project.root(),
                project.manifest(),
                project.policy(),
                snapshot(),
                tempDir.resolve("evidence"),
                PI,
                NODE,
                CLOCK);

        assertEquals(ShipLocalStamp.Status.FAIL, result.stamp().status());
        assertEquals(Outcome.FAIL, result.stamp().checks().get(1).outcome());
        assertTrue(executor.commands.isEmpty());
        assertEquals(result.stamp(),
                ShipLocalStampStore.read(tempDir.resolve("evidence"), RUN_ID));
    }

    @Test
    void mapsANonzeroCommandExitToItsCheckOutcome() throws Exception {
        Project nonzeroProject = project("orders");
        RecordingExecutor nonzero = new RecordingExecutor();
        nonzero.nonzeroCommand = "citrus-integration-test-001";

        ShipMainValidator.Result nonzeroResult = validator(nonzero).validate(
                RUN_ID,
                nonzeroProject.root(),
                nonzeroProject.manifest(),
                nonzeroProject.policy(),
                snapshot(),
                tempDir.resolve("nonzero-evidence"),
                PI,
                NODE,
                CLOCK);

        assertEquals(ShipLocalStamp.Status.FAIL, nonzeroResult.stamp().status());
        assertEquals(Outcome.NONZERO, nonzeroResult.stamp().checks().stream()
                .filter(check -> "citrus-integration-test-001".equals(check.id()))
                .findFirst().orElseThrow().outcome());
    }

    @Test
    void interruptionStopsValidationWithoutPublishingAStamp() throws Exception {
        Project project = project("orders");
        RecordingExecutor executor = new RecordingExecutor();
        executor.interruptCommand = "route-schema";
        Path evidence = tempDir.resolve("interrupted-evidence");

        try {
            assertThrows(
                    InterruptedException.class,
                    () -> validator(executor).validate(
                            RUN_ID,
                            project.root(),
                            project.manifest(),
                            project.policy(),
                            snapshot(),
                            evidence,
                            PI,
                            NODE,
                            CLOCK));

            assertTrue(Thread.currentThread().isInterrupted());
            assertEquals(List.of("route-schema"),
                    executor.commands.stream().map(EvidenceCommand::id).toList());
            assertFalse(Files.exists(evidence.resolve("stamp.json")));
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void interruptionBeforeAnEarlyFailureStampLeavesTheAttemptResumable() throws Exception {
        Project project = project("orders");
        Files.writeString(project.manifest(), "not a manifest");
        Path evidence = tempDir.resolve("early-interrupted-evidence");
        Clock interruptingClock = new Clock() {
            @Override
            public java.time.ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                Thread.currentThread().interrupt();
                return CLOCK.instant();
            }
        };

        try {
            assertThrows(
                    InterruptedException.class,
                    () -> validator(new RecordingExecutor()).validate(
                            RUN_ID,
                            project.root(),
                            project.manifest(),
                            project.policy(),
                            snapshot(),
                            evidence,
                            PI,
                            NODE,
                            interruptingClock));

            assertTrue(Thread.currentThread().isInterrupted());
            assertFalse(Files.exists(evidence.resolve("stamp.json")));
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void rejectsNewLogsWhenOtherInterruptedAttemptLogsExhaustAggregateBudget() throws Exception {
        Project project = project("orders");
        RecordingExecutor executor = new RecordingExecutor();
        Path evidence = Files.createDirectory(tempDir.resolve("aggregate-evidence"));
        Files.setPosixFilePermissions(
                evidence, PosixFilePermissions.fromString("rwx------"));
        Path routeStdout = evidence.resolve("route-schema.stdout.log");
        Path routeStderr = evidence.resolve("route-schema.stderr.log");
        privateFile(routeStdout, new byte[0]);
        privateFile(routeStderr, new byte[0]);
        privateSparseFile(
                evidence.resolve("main-runtime-resolve-and-start.stdout.log"),
                MAX_TOTAL_LOG_BYTES);
        privateFile(
                evidence.resolve("main-runtime-resolve-and-start.stderr.log"),
                new byte[0]);

        ShipMainValidator.Result result = validator(executor).validate(
                RUN_ID,
                project.root(),
                project.manifest(),
                project.policy(),
                snapshot(),
                evidence,
                PI,
                NODE,
                CLOCK);

        ShipLocalStamp.Check route = result.stamp().checks().stream()
                .filter(check -> "route-schema".equals(check.id()))
                .findFirst()
                .orElseThrow();
        assertEquals(Outcome.FAIL, route.outcome());
        assertTrue(route.summary().contains("retention limit"));
        assertEquals(0, Files.size(routeStdout));
        assertEquals(0, Files.size(routeStderr));
    }

    @Test
    void replacesRetainedLogsLeftByAnInterruptedAttempt() throws Exception {
        Project project = project("orders");
        RecordingExecutor executor = new RecordingExecutor();
        Path evidence = Files.createDirectory(tempDir.resolve("retry-evidence"));
        Files.setPosixFilePermissions(
                evidence, PosixFilePermissions.fromString("rwx------"));
        Path stdout = evidence.resolve("route-schema.stdout.log");
        Path stderr = evidence.resolve("route-schema.stderr.log");
        byte[] partialStderr = "partial stderr".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        privateSparseFile(stdout, MAX_TOTAL_LOG_BYTES - partialStderr.length);
        privateFile(stderr, partialStderr);

        ShipMainValidator.Result result = validator(executor).validate(
                RUN_ID,
                project.root(),
                project.manifest(),
                project.policy(),
                snapshot(),
                evidence,
                PI,
                NODE,
                CLOCK);

        assertEquals(ShipLocalStamp.Status.PASS, result.stamp().status());
        assertEquals("PASS\n", Files.readString(stdout));
        assertEquals(0, Files.size(stderr));
        assertEquals(
                PosixFilePermissions.fromString("rw-------"),
                Files.getPosixFilePermissions(stdout));
        assertEquals(
                PosixFilePermissions.fromString("rw-------"),
                Files.getPosixFilePermissions(stderr));
        try (var files = Files.list(evidence)) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString()
                    .startsWith(".retained-log-")));
        }
    }

    private ShipMainValidator validator(RecordingExecutor executor) {
        return new ShipMainValidator(executor);
    }

    private ShipCatalogService.Snapshot snapshot() throws Exception {
        Path directory = Files.createTempDirectory(tempDir, "catalog-");
        return CatalogTestVerifier.mainSnapshot(directory);
    }

    private Project project(String... routeIds) throws Exception {
        Path root = Files.createTempDirectory(tempDir, "candidate-");
        List<RouteArtifact> routes = new ArrayList<>();
        List<TestArtifact> tests = new ArrayList<>();
        List<RouteContract> contracts = new ArrayList<>();
        for (String routeId : routeIds) {
            String routePath = "src/main/resources/routes/" + routeId + ".camel.yaml";
            String testPath = "test/" + routeId + ".camel.it.yaml";
            write(root, routePath, String.format(Locale.ROOT, """
                    - route:
                        id: %s
                        from:
                          uri: direct:%s
                    """, routeId, routeId));
            write(root, testPath, String.format(Locale.ROOT, """
                    name: %s-test
                    actions:
                      - send:
                          endpoint: "camel:sync:direct:camel-kit-ship-test-%s"
                          message:
                            body:
                              data: exercise route
                      - receive:
                          endpoint: "camel:sync:direct:camel-kit-ship-test-%s"
                          message:
                            body:
                              data: exercise route
                    """, routeId, routeId, routeId));
            routes.add(new RouteArtifact(routeId, routePath, digest(root.resolve(routePath))));
            tests.add(new TestArtifact(routeId, testPath, digest(root.resolve(testPath))));
            contracts.add(new RouteContract(routeId, routePath, testPath));
        }
        write(root, ".camel-kit/config.properties", """
                project.runtime=main
                project.camelVersion=4.21.0
                project.platformBomVersion=4.21.0
                citrus.version=5.0.0-M2
                """);
        write(root, "pom.xml", """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>orders</artifactId><version>1.0.0</version>
                  <dependencies>
                    <dependency><groupId>org.apache.camel</groupId>
                      <artifactId>camel-main</artifactId><version>4.21.0</version></dependency>
                    <dependency><groupId>org.apache.camel</groupId>
                      <artifactId>camel-yaml-dsl</artifactId><version>4.21.0</version></dependency>
                    <dependency><groupId>org.apache.camel</groupId>
                      <artifactId>camel-direct</artifactId><version>4.21.0</version></dependency>
                  </dependencies>
                </project>
                """);
        writeManifest(root, routes, tests);
        ArtifactPolicy policy = new ArtifactPolicy(
                "main",
                "4.21.0",
                null,
                null,
                "yaml",
                "simple",
                CITRUS_VERSION,
                CITRUS_DEPENDENCIES,
                JavaPolicy.FORBIDDEN,
                List.of(),
                contracts,
                true,
                true);
        return new Project(root, root.resolve("artifact-manifest.json"), policy, routes, tests);
    }

    private static void writeManifest(
            Path root, List<RouteArtifact> routes, List<TestArtifact> tests)
            throws Exception {
        ArtifactManifest manifest = new ArtifactManifest(
                ArtifactManifest.SCHEMA_VERSION,
                "main",
                "4.21.0",
                null,
                null,
                "yaml",
                "simple",
                CITRUS_VERSION,
                CITRUS_DEPENDENCIES,
                JavaPolicy.FORBIDDEN,
                List.of(),
                routes,
                tests,
                List.of(
                        new DeclaredArtifact(
                                "config",
                                ".camel-kit/config.properties",
                                digest(root.resolve(".camel-kit/config.properties")),
                                true),
                        new DeclaredArtifact("pom", "pom.xml", digest(root.resolve("pom.xml")), true)),
                true,
                true);
        new ObjectMapper().writerWithDefaultPrettyPrinter()
                .writeValue(root.resolve("artifact-manifest.json").toFile(), manifest);
    }

    private static void write(Path root, String relative, String content) throws Exception {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private static String digest(Path file) throws Exception {
        return ShipDigest.sha256(Files.readAllBytes(file));
    }

    private static String digest(String value) {
        return ShipDigest.sha256(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }

    private static String version(JvmPayloadRequest payload) {
        return switch (payload.kind()) {
            case CAMEL_YAML_VALIDATE -> "Camel direct YAML validator " + payload.camelVersion();
            case CAMEL_MAIN_START -> "Camel direct Main bootstrap " + payload.camelVersion();
            case CITRUS_YAML -> "Citrus direct YAML " + payload.citrusVersion()
                                + " with Camel " + payload.camelVersion();
            default -> throw new IllegalArgumentException();
        };
    }

    private static void privateFile(Path file, byte[] content) throws Exception {
        Files.write(file, content);
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
    }

    private static void privateSparseFile(Path file, long size) throws Exception {
        try (RandomAccessFile output = new RandomAccessFile(file.toFile(), "rw")) {
            output.setLength(size);
        }
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
    }

    private static Path privateExecutable(Path file, byte[] content) throws Exception {
        Files.write(file, content);
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rwx------"));
        return file;
    }

    private final class RecordingExecutor implements ShipMainValidator.EvidenceExecutor {

        private final List<EvidenceCommand> commands = new ArrayList<>();
        private String nonzeroCommand;
        private String interruptCommand;

        @Override
        public CommandEvidence run(Path candidate, Path directory, EvidenceCommand command)
                throws IOException {
            commands.add(command);
            try {
                Files.createDirectory(directory);
                Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"));
                String jdkDigest = digest("jdk");
                JvmPayloadArchive.Identity toolchain = JvmPayloadTestFixture.create(
                        directory.resolve("toolchain"), command.jvmPayload(), jdkDigest);
                Path sandboxExecutable = privateExecutable(
                        directory.resolve("bwrap"), "sandbox".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                Path javaExecutable = privateExecutable(
                        directory.resolve("java"), "java".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                byte[] stdout = "PASS\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                Path stdoutPath = directory.resolve("raw.stdout.log");
                Path stderrPath = directory.resolve("raw.stderr.log");
                privateFile(stdoutPath, stdout);
                privateFile(stderrPath, new byte[0]);
                String sandboxDigest = digest(sandboxExecutable);
                String executableDigest = digest(javaExecutable);
                boolean nonzero = command.id().equals(nonzeroCommand);
                CommandEvidence evidence = new CommandEvidence(
                        CommandEvidence.SCHEMA_VERSION,
                        command.id(),
                        new SandboxIdentity(
                                EvidenceRunner.SANDBOX_PROVIDER,
                                sandboxExecutable.toString(),
                                sandboxDigest,
                                sandboxDigest,
                                null,
                                EvidenceRunner.expectedSandboxProfileDigest(command, jdkDigest)),
                        toolchain.aggregateDigest(),
                        toolchain.aggregateDigest(),
                        toolchain.archive().toString(),
                        toolchain.archiveDigest(),
                        javaExecutable.toString(),
                        executableDigest,
                        executableDigest,
                        null,
                        version(command.jvmPayload()),
                        command.arguments(),
                        candidate.toString(),
                        EvidenceRunner.expectedEnvironment(command, jdkDigest),
                        STARTED,
                        ENDED,
                        true,
                        false,
                        nonzero ? 7 : 0,
                        null,
                        stdoutPath.toString(),
                        ShipDigest.sha256(stdout),
                        stderrPath.toString(),
                        ShipDigest.sha256(new byte[0]),
                        command.inputDigests());
                if (command.id().equals(interruptCommand)) {
                    Thread.currentThread().interrupt();
                }
                return evidence;
            } catch (Exception e) {
                throw new IOException(e);
            }
        }
    }

    private record Project(
            Path root,
            Path manifest,
            ArtifactPolicy policy,
            List<RouteArtifact> routes,
            List<TestArtifact> tests) {
    }
}
