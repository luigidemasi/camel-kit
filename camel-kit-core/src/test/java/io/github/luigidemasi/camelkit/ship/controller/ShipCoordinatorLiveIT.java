package io.github.luigidemasi.camelkit.ship.controller;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import io.github.luigidemasi.camelkit.config.DistributionConfig;
import io.github.luigidemasi.camelkit.ship.ShipDigest;
import io.github.luigidemasi.camelkit.ship.artifact.ArtifactManifestReader;
import io.github.luigidemasi.camelkit.ship.context.ShipContext;
import io.github.luigidemasi.camelkit.ship.controller.ShipRun.Oversight;
import io.github.luigidemasi.camelkit.ship.controller.ShipRun.RunStatus;
import io.github.luigidemasi.camelkit.ship.controller.ShipRun.Stage;
import io.github.luigidemasi.camelkit.ship.controller.ShipRun.StageStatus;
import io.github.luigidemasi.camelkit.ship.evidence.ShipLocalStamp;
import io.github.luigidemasi.camelkit.ship.evidence.ShipLocalStampStore;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Opt-in authenticated Pi/Linux certification for the complete local Ship workflow. */
@EnabledOnOs(OS.LINUX)
class ShipCoordinatorLiveIT {

    private static final String LIVE_PI = "CAMEL_KIT_SHIP_LIVE_PI";
    private static final String LIVE_NODE = "CAMEL_KIT_SHIP_LIVE_NODE";
    private static final String PIPELINE = "149-live-pi";

    @TempDir(cleanup = CleanupMode.ON_SUCCESS)
    Path directory;

    @Test
    @Timeout(value = 60, unit = TimeUnit.MINUTES)
    void documentDrivenWorkflowCompletesWithRealPiAndLocalEvidence()
            throws Exception {
        String configuredPi = System.getenv(LIVE_PI);
        Assumptions.assumeTrue(
                configuredPi != null && !configuredPi.isBlank(),
                LIVE_PI + " is not configured");
        Path pi = Path.of(configuredPi);
        assertTrue(pi.isAbsolute(), LIVE_PI + " must be an absolute path");
        assertTrue(Files.isExecutable(pi), LIVE_PI + " must identify an executable");
        String configuredNode = System.getenv(LIVE_NODE);
        assertTrue(
                configuredNode != null && !configuredNode.isBlank(),
                LIVE_NODE + " is required when " + LIVE_PI + " is configured");
        Path node = Path.of(configuredNode);
        assertTrue(node.isAbsolute(), LIVE_NODE + " must be an absolute path");
        assertTrue(
                Files.isExecutable(node),
                LIVE_NODE + " must identify an executable");

        DistributionConfig distribution = DistributionConfig.loadBundled();
        Path project = Files.createDirectory(directory.resolve("project"));
        Path state = directory.resolve("state");
        Path repository = Files.createDirectory(directory.resolve("m2"));
        Files.createDirectories(project.resolve(".camel-kit"));
        Files.writeString(
                project.resolve(".camel-kit/pipeline.json"),
                "{\"mode\":\"manual\",\"activePipeline\":\""
                                                             + PIPELINE + "\"}\n");
        Path requirements = Files.writeString(
                project.resolve("requirements.md"),
                requirements(distribution),
                StandardCharsets.UTF_8);

        ShipController controller = new ShipController(state);
        ShipRun started = controller.start(
                project,
                Oversight.NEVER,
                List.of(new ShipContext.DocumentInput(requirements)));
        ShipCoordinator coordinator = new ShipCoordinator(
                state,
                pi,
                node,
                repository,
                distribution,
                Duration.ofMinutes(10),
                false);

        ShipRun completed = coordinator.run(started.id());

        assertEquals(
                RunStatus.COMPLETED,
                completed.status(),
                completed.currentStage() + ": " + completed.message()
                                    + " (retained at " + directory + ')');
        assertEquals(PIPELINE, completed.pipelineId());
        for (Stage stage : Stage.values()) {
            assertEquals(StageStatus.COMPLETED, completed.stage(stage).status());
            assertEquals(1, completed.stage(stage).attempts());
        }

        Path candidate = completed.stage(Stage.EXECUTE).artifacts().stream()
                .map(ShipRun.ArtifactRef::path)
                .map(Path::of)
                .filter(Files::isDirectory)
                .findFirst()
                .orElseThrow();
        Path manifest = candidate.resolve(
                "docs/camel-kit/" + PIPELINE + "/artifact-manifest.json");
        for (String relative : List.of(
                "src/main/resources/routes/orders.camel.yaml",
                "test/orders.camel.it.yaml",
                ".camel-kit/config.properties",
                "pom.xml",
                "docs/camel-kit/" + PIPELINE + "/artifact-manifest.json")) {
            assertTrue(Files.isRegularFile(candidate.resolve(relative)), relative);
        }
        ArtifactManifestReader.read(candidate, manifest);

        Path evidence = state.resolve(completed.id())
                .resolve("evidence/validate-1");
        ShipLocalStamp stamp = ShipLocalStampStore.read(
                evidence, completed.id());
        assertEquals(ShipLocalStamp.Status.PASS, stamp.status());
        assertEquals(
                List.of(
                        "pi",
                        "node",
                        "java",
                        "camel",
                        "os",
                        "architecture"),
                stamp.toolVersions().stream()
                        .map(ShipLocalStamp.ToolVersion::tool)
                        .toList());
        assertEquals(distribution.piVersion(),
                stamp.toolVersions().get(0).version());
        assertEquals(
                pi.toRealPath().toString(),
                stamp.toolVersions().get(0).executable());
        assertEquals(distribution.nodeVersion(),
                stamp.toolVersions().get(1).version());
        assertEquals(
                node.toRealPath().toString(),
                stamp.toolVersions().get(1).executable());
        assertEquals(
                Runtime.version().toString(),
                stamp.toolVersions().get(2).version());
        assertEquals(
                distribution.camelMainVersion(),
                stamp.toolVersions().get(3).version());
        assertEquals(
                System.getProperty("os.name") + " "
                     + System.getProperty("os.version"),
                stamp.toolVersions().get(4).version());
        assertEquals(
                System.getProperty("os.arch"),
                stamp.toolVersions().get(5).version());
        assertTrue(stamp.toolVersions().subList(0, 2).stream()
                .allMatch(tool -> tool.support()
                                  == ShipLocalStamp.Support.SUPPORTED
                        && tool.message() == null));
        assertTrue(stamp.toolVersions().subList(2, 6).stream()
                .allMatch(tool -> tool.support()
                                  == ShipLocalStamp.Support.UNTESTED
                        && tool.executable() == null
                        && tool.message() != null));
        assertEquals(List.of(
                "artifact-policy",
                "catalog-usage",
                "route-schema",
                "main-package-and-inspect",
                "main-runtime-resolve-and-start",
                "citrus-integration-test-001"),
                stamp.checks().stream()
                        .map(ShipLocalStamp.Check::id)
                        .toList());
        assertTrue(stamp.checks().stream()
                .allMatch(check -> check.outcome()
                                   == ShipLocalStamp.Outcome.PASS));
        List<ShipLocalStamp.CommandRun> commands = stamp.checks().stream()
                .map(ShipLocalStamp.Check::command)
                .filter(java.util.Objects::nonNull)
                .toList();
        assertEquals(4, commands.size());
        for (ShipLocalStamp.CommandRun command : commands) {
            assertTrue(command.launched());
            assertFalse(command.timedOut());
            assertEquals(0, command.exitCode());
            assertEquals(
                    command.stdoutDigest(),
                    ShipDigest.sha256(Files.readAllBytes(
                            Path.of(command.stdoutLog()))));
            assertEquals(
                    command.stderrDigest(),
                    ShipDigest.sha256(Files.readAllBytes(
                            Path.of(command.stderrLog()))));
        }
    }

    private static String requirements(DistributionConfig distribution) {
        return String.format(Locale.ROOT, """
                # Complete Camel integration requirements

                Implement pipeline 149-live-pi. Every product and design decision is
                complete: report no material ambiguity and ask no questions.

                Use Camel Main %1$s, YAML DSL, Simple expressions, Citrus %2$s, and
                no Java or Java exceptions.

                Create exactly one route at
                `src/main/resources/routes/orders.camel.yaml`:

                ```yaml
                - route:
                    id: orders
                    from:
                      uri: direct:orders
                      steps:
                        - setBody:
                            simple: "${body}"
                ```

                Create exactly one Citrus test at `test/orders.camel.it.yaml`:

                ```yaml
                name: orders-test
                actions:
                  - send:
                      endpoint: "camel:sync:direct:camel-kit-ship-test-orders"
                      message:
                        body:
                          data: exercise route
                  - receive:
                      endpoint: "camel:sync:direct:camel-kit-ship-test-orders"
                      message:
                        body:
                          data: exercise route
                ```

                Create `.camel-kit/config.properties` with exactly:

                ```properties
                project.runtime=main
                project.camelVersion=%1$s
                project.platformBomVersion=%1$s
                citrus.version=%2$s
                ```

                Create `pom.xml` with exactly this accepted model. Keep the default
                Maven namespace as the only namespace and do not add any other
                namespace declarations, attributes, properties, dependency
                management, plugins, scopes, or classifiers:

                ```xml
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId>
                  <artifactId>orders</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>org.apache.camel</groupId>
                      <artifactId>camel-main</artifactId>
                      <version>%1$s</version>
                    </dependency>
                    <dependency>
                      <groupId>org.apache.camel</groupId>
                      <artifactId>camel-yaml-dsl</artifactId>
                      <version>%1$s</version>
                    </dependency>
                    <dependency>
                      <groupId>org.apache.camel</groupId>
                      <artifactId>camel-core-languages</artifactId>
                      <version>%1$s</version>
                    </dependency>
                    <dependency>
                      <groupId>org.apache.camel</groupId>
                      <artifactId>camel-direct</artifactId>
                      <version>%1$s</version>
                    </dependency>
                  </dependencies>
                </project>
                ```

                During EXECUTE, create
                `docs/camel-kit/149-live-pi/artifact-manifest.json` matching the exact
                controller-supplied schema. Declare the route, Citrus test,
                `.camel-kit/config.properties`, and `pom.xml` with their current
                digests, each encoded exactly as `sha256:<64 lowercase hex>`.
                Do not declare `requirements.md` or `.camel-kit/pipeline.json`,
                because they are pre-existing inputs.
                """,
                distribution.camelMainVersion(),
                distribution.citrusVersion());
    }
}
