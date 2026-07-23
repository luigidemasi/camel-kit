package io.github.luigidemasi.camelkit.ship.artifact;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import io.github.luigidemasi.camelkit.ship.artifact.ArtifactManifest.DeclaredArtifact;
import io.github.luigidemasi.camelkit.ship.artifact.ArtifactManifest.JavaPolicy;
import io.github.luigidemasi.camelkit.ship.artifact.ArtifactManifest.RouteArtifact;
import io.github.luigidemasi.camelkit.ship.artifact.ArtifactManifest.TestArtifact;
import io.github.luigidemasi.camelkit.ship.artifact.ArtifactPolicy.RouteContract;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class ArtifactValidatorTest {

    private static final String CITRUS_VERSION = "5.0.0-M2";
    private static final List<String> CITRUS_DEPENDENCIES = CitrusDependencyPolicy.required(CITRUS_VERSION);

    @TempDir
    Path project;

    @Test
    void acceptsYamlSimpleProjectWithOneCitrusTestPerRoute() throws Exception {
        write("src/main/resources/routes/orders.camel.yaml",
                "- route:\n    id: orders\n    from:\n      uri: direct:start\n");
        write("test/orders.camel.it.yaml", citrusTest());
        write(".camel-kit/config.properties", mainConfiguration());

        ArtifactValidationResult result = ArtifactValidator.validate(project, manifest(
                "main",
                List.of(new RouteArtifact(
                        "orders", "src/main/resources/routes/orders.camel.yaml",
                        digest("src/main/resources/routes/orders.camel.yaml"))),
                List.of(new TestArtifact(
                        "orders", "test/orders.camel.it.yaml",
                        digest("test/orders.camel.it.yaml"))),
                JavaPolicy.FORBIDDEN,
                List.of()));

        assertTrue(result.passed(), () -> result.findings().toString());
    }

    @Test
    void rejectsLegacyCitrusJbangConfiguration() throws Exception {
        write("src/main/resources/routes/orders.camel.yaml",
                "- route:\n    id: orders\n    from:\n      uri: direct:start\n");
        write("test/orders.camel.it.yaml", citrusTest());
        write(".camel-kit/config.properties", mainConfiguration());
        ArtifactManifest approved = manifest(
                "main",
                List.of(new RouteArtifact(
                        "orders", "src/main/resources/routes/orders.camel.yaml",
                        digest("src/main/resources/routes/orders.camel.yaml"))),
                List.of(new TestArtifact(
                        "orders", "test/orders.camel.it.yaml",
                        digest("test/orders.camel.it.yaml"))),
                JavaPolicy.FORBIDDEN,
                List.of());
        write(CitrusDependencyPolicy.FORBIDDEN_JBANG_PROPERTIES_PATH,
                "run.deps=org.citrusframework:citrus-yaml:" + CITRUS_VERSION + '\n');

        ArtifactValidationResult result = ArtifactValidator.validate(project, approved);

        assertTrue(hasError(result, "citrus-jbang-forbidden", CitrusDependencyPolicy.FORBIDDEN_JBANG_PROPERTIES_PATH));
    }

    @Test
    void rejectsDeclaredCitrusJbangConfigurationArtifact() throws Exception {
        write("src/main/resources/routes/orders.camel.yaml",
                "- route:\n    id: orders\n    from:\n      uri: direct:start\n");
        write("test/orders.camel.it.yaml", citrusTest());
        write(".camel-kit/config.properties", mainConfiguration());
        writeCitrusConfiguration();

        ArtifactValidationResult result = ArtifactValidator.validate(project, manifest(
                "main",
                List.of(new RouteArtifact(
                        "orders", "src/main/resources/routes/orders.camel.yaml",
                        digest("src/main/resources/routes/orders.camel.yaml"))),
                List.of(new TestArtifact(
                        "orders", "test/orders.camel.it.yaml",
                        digest("test/orders.camel.it.yaml"))),
                JavaPolicy.FORBIDDEN,
                List.of(),
                List.of()));

        assertTrue(hasError(result, "citrus-jbang-forbidden", CitrusDependencyPolicy.FORBIDDEN_JBANG_PROPERTIES_PATH));
    }

    @Test
    void citrusTestMustUseControllerOwnedTestLocation() throws Exception {
        write("src/main/resources/routes/orders.camel.yaml",
                "- route:\n    id: orders\n    from:\n      uri: direct:start\n");
        write("src/test/resources/orders.camel.it.yaml", citrusTest());

        ArtifactValidationResult result = ArtifactValidator.validate(project, manifest(
                "main",
                List.of(new RouteArtifact("orders", "src/main/resources/routes/orders.camel.yaml", null)),
                List.of(new TestArtifact("orders", "src/test/resources/orders.camel.it.yaml", null)),
                JavaPolicy.FORBIDDEN,
                List.of()));

        assertTrue(hasError(result, "citrus-test-location", "src/test/resources/orders.camel.it.yaml"));
    }

    @Test
    void discoversWrongSuffixInsteadOfIgnoringRoute() throws Exception {
        write("src/main/resources/routes/orders.yaml", "- route:\n    id: orders\n");

        ArtifactValidationResult result = ArtifactValidator.validate(project, manifest(
                "main", List.of(new RouteArtifact("orders", "src/main/resources/routes/orders.yaml", null)),
                List.of(), JavaPolicy.FORBIDDEN, List.of()));

        assertFalse(result.passed());
        assertTrue(hasError(result, "route-suffix", "src/main/resources/routes/orders.yaml"));
    }

    @Test
    void discoversStructuralRouteOutsideTheConventionalDirectoryAndWithoutBlockRegexShape() throws Exception {
        write("config/hidden.yml", "{\"route\": {\"id\": \"hidden\", \"from\": {\"uri\": \"direct:start\"}}}\n");

        ArtifactValidationResult result = ArtifactValidator.validate(project, manifest(
                "main", List.of(), List.of(), JavaPolicy.FORBIDDEN, List.of()));

        assertTrue(hasError(result, "route-suffix", "config/hidden.yml"));
    }

    @Test
    void discoversTopLevelFromAndPluralRestDefinitionsWithWrongSuffixes() throws Exception {
        write("config/from.yml", "- from:\n    uri: direct:start\n    steps: []\n");
        write("config/rests.yaml", "rests:\n  - rest:\n      path: /orders\n");

        ArtifactValidationResult result = ArtifactValidator.validate(project, manifest(
                "main", List.of(), List.of(), JavaPolicy.FORBIDDEN, List.of()));

        assertTrue(hasError(result, "route-suffix", "config/from.yml"));
        assertTrue(hasError(result, "route-suffix", "config/rests.yaml"));
    }

    @Test
    void alternatePipeAndKameletDefinitionsCannotHideBehindTheirOwnSuffixes() throws Exception {
        write("config/orders.pipe.yaml", "apiVersion: camel.apache.org/v1\nkind: Pipe\nspec: {}\n");
        write("config/enrich.kamelet.yaml", "apiVersion: camel.apache.org/v1\nkind: Kamelet\nspec: {}\n");

        ArtifactValidationResult result = ArtifactValidator.validate(project, manifest(
                "main", List.of(), List.of(), JavaPolicy.FORBIDDEN, List.of()));

        assertTrue(hasError(result, "route-suffix", "config/orders.pipe.yaml"));
        assertTrue(hasError(result, "route-suffix", "config/enrich.kamelet.yaml"));
    }

    @Test
    void uninspectableOrMultiDocumentYamlFailsClosedOutsideTheRouteDirectory() throws Exception {
        write("config/broken.yaml", "route: [\n");
        write("config/multiple.yaml", "service:\n  name: orders\n---\nroute:\n  id: hidden\n");

        ArtifactValidationResult result = ArtifactValidator.validate(project, manifest(
                "main", List.of(), List.of(), JavaPolicy.FORBIDDEN, List.of()));

        assertTrue(hasError(result, "route-scan-parse", "config/broken.yaml"));
        assertTrue(hasError(result, "route-scan-parse", "config/multiple.yaml"));
    }

    @Test
    void oversizedYamlCannotBypassWrongSuffixInspection() throws Exception {
        write("config/hidden.yaml", "route:\n  id: hidden\n#" + "x".repeat(2 * 1024 * 1024));

        ArtifactValidationResult result = ArtifactValidator.validate(project, manifest(
                "main", List.of(), List.of(), JavaPolicy.FORBIDDEN, List.of()));

        assertTrue(hasError(result, "route-scan-size", "config/hidden.yaml"));
    }

    @Test
    void genericArtifactDeclarationCannotMasqueradeAsADeclaredRoute() throws Exception {
        write("config/hidden.camel.yaml", "- route:\n    id: hidden\n");

        ArtifactValidationResult result = ArtifactValidator.validate(project, manifest(
                "main", List.of(), List.of(), JavaPolicy.FORBIDDEN, List.of(),
                List.of(new DeclaredArtifact(
                        "config", "config/hidden.camel.yaml", digest("config/hidden.camel.yaml"), true))));

        assertTrue(hasError(result, "route-undeclared", "config/hidden.camel.yaml"));
    }

    @Test
    void unrelatedBoundedYamlIsNotMisclassifiedAsARoute() throws Exception {
        write("config/application.yaml", "service:\n  name: orders\n");

        ArtifactValidationResult result = ArtifactValidator.validate(project, manifest(
                "main", List.of(), List.of(), JavaPolicy.FORBIDDEN, List.of()));

        assertFalse(hasError(result, "route-suffix", "config/application.yaml"));
        assertFalse(hasError(result, "route-scan-size", "config/application.yaml"));
    }

    @Test
    void missingCitrusTestsAreFailureNotSkip() throws Exception {
        write("src/main/resources/routes/orders.camel.yaml", "- route:\n    id: orders\n");

        ArtifactValidationResult result = ArtifactValidator.validate(project, manifest(
                "main",
                List.of(new RouteArtifact("orders", "src/main/resources/routes/orders.camel.yaml", null)),
                List.of(), JavaPolicy.FORBIDDEN, List.of()));

        assertTrue(hasError(result, "citrus-tests-missing", null));
        assertTrue(hasError(result, "citrus-route-uncovered", null));
    }

    @Test
    void rejectsCitrusEndpointBoundToAnotherRoute() throws Exception {
        write("src/main/resources/routes/orders.camel.yaml",
                "- route:\n    id: orders\n    from:\n      uri: direct:start\n");
        write("test/orders.camel.it.yaml", citrusTest().replace(
                "camel-kit-ship-test-orders", "camel-kit-ship-test-other"));

        ArtifactValidationResult result = ArtifactValidator.validate(project, manifest(
                "main",
                List.of(new RouteArtifact("orders", "src/main/resources/routes/orders.camel.yaml", null)),
                List.of(new TestArtifact("orders", "test/orders.camel.it.yaml", null)),
                JavaPolicy.FORBIDDEN, List.of()));

        assertTrue(hasError(result, "citrus-yaml-policy", "test/orders.camel.it.yaml"));
    }

    @Test
    void routePlaceholdersCannotBeSatisfiedByControllerGuesses() throws Exception {
        write("src/main/resources/routes/orders.camel.yaml", """
                - route:
                    id: orders
                    from:
                      uri: kafka:{{orders.topic}}
                      steps:
                        - delay:
                            constant: "{{orders.threshold}}"
                """);
        write("test/orders.camel.it.yaml", citrusTest());

        ArtifactValidationResult result = ArtifactValidator.validate(project, manifest(
                "main",
                List.of(new RouteArtifact("orders", "src/main/resources/routes/orders.camel.yaml", null)),
                List.of(new TestArtifact("orders", "test/orders.camel.it.yaml", null)),
                JavaPolicy.FORBIDDEN, List.of()));

        assertTrue(hasError(result, "route-yaml-parse", "src/main/resources/routes/orders.camel.yaml"));
    }

    @Test
    void emptyCitrusTestCannotSatisfyRequiredIntegrationCoverage() throws Exception {
        write("src/main/resources/routes/orders.camel.yaml",
                "- route:\n    id: orders\n    from:\n      uri: direct:start\n");
        write("test/orders.camel.it.yaml", "name: orders-test\nactions: []\n");
        write(".camel-kit/config.properties", mainConfiguration());

        ArtifactValidationResult result = ArtifactValidator.validate(project, manifest(
                "main",
                List.of(new RouteArtifact(
                        "orders", "src/main/resources/routes/orders.camel.yaml",
                        digest("src/main/resources/routes/orders.camel.yaml"))),
                List.of(new TestArtifact(
                        "orders", "test/orders.camel.it.yaml",
                        digest("test/orders.camel.it.yaml"))),
                JavaPolicy.FORBIDDEN,
                List.of()));

        assertTrue(hasError(result, "citrus-yaml-policy", "test/orders.camel.it.yaml"));
    }

    @Test
    void citrusTestMayUseJbangTextAsLiteralBodyData() throws Exception {
        write("src/main/resources/routes/orders.camel.yaml",
                "- route:\n    id: orders\n    from:\n      uri: direct:start\n");
        write("test/orders.camel.it.yaml", """
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
                          data: camel.jbang
                """);

        ArtifactValidationResult result = ArtifactValidator.validate(project, manifest(
                "main",
                List.of(new RouteArtifact("orders", "src/main/resources/routes/orders.camel.yaml", null)),
                List.of(new TestArtifact("orders", "test/orders.camel.it.yaml", null)),
                JavaPolicy.FORBIDDEN, List.of()));

        assertFalse(hasError(result, "citrus-yaml-policy", "test/orders.camel.it.yaml"));
    }

    @Test
    void citrusTestCannotAddressAnExternalEndpoint() throws Exception {
        write("src/main/resources/routes/orders.camel.yaml",
                "- route:\n    id: orders\n    from:\n      uri: direct:start\n");
        write("test/orders.camel.it.yaml", citrusTest().replace(
                "camel:sync:direct:camel-kit-ship-test-orders", "https://example.test/orders"));

        ArtifactValidationResult result = ArtifactValidator.validate(project, manifest(
                "main",
                List.of(new RouteArtifact("orders", "src/main/resources/routes/orders.camel.yaml", null)),
                List.of(new TestArtifact("orders", "test/orders.camel.it.yaml", null)),
                JavaPolicy.FORBIDDEN, List.of()));

        assertTrue(hasError(result, "citrus-yaml-policy", "test/orders.camel.it.yaml"));
    }

    @Test
    void citrusTestMayUseOnlyTheControllerOwnedCamelDirectEndpointShape() throws Exception {
        write("src/main/resources/routes/orders.camel.yaml",
                "- route:\n    id: orders\n    from:\n      uri: direct:start\n");
        write("test/orders.camel.it.yaml", citrusTest());

        ArtifactValidationResult result = ArtifactValidator.validate(project, manifest(
                "main",
                List.of(new RouteArtifact("orders", "src/main/resources/routes/orders.camel.yaml", null)),
                List.of(new TestArtifact("orders", "test/orders.camel.it.yaml", null)),
                JavaPolicy.FORBIDDEN, List.of()));

        assertFalse(hasError(result, "citrus-yaml-policy", "test/orders.camel.it.yaml"));
    }

    @Test
    void citrusTestMayUseBoundedLiteralCasesAndApplicationHeaders() throws Exception {
        write("src/main/resources/routes/orders.camel.yaml",
                "- route:\n    id: orders\n    from:\n      uri: direct:start\n");
        write("test/orders.camel.it.yaml", citrusMatrixTest());

        ArtifactValidationResult result = ArtifactValidator.validate(project, manifest(
                "main",
                List.of(new RouteArtifact("orders", "src/main/resources/routes/orders.camel.yaml", null)),
                List.of(new TestArtifact("orders", "test/orders.camel.it.yaml", null)),
                JavaPolicy.FORBIDDEN, List.of()));

        assertFalse(hasError(result, "citrus-yaml-policy", "test/orders.camel.it.yaml"));
    }

    @Test
    void undeclaredRouteIsRejected() throws Exception {
        write("src/main/resources/routes/orders.camel.yaml", "- route:\n    id: orders\n");
        write("src/main/resources/routes/hidden.camel.yaml", "- route:\n    id: hidden\n");
        write("test/orders.camel.it.yaml", citrusTest());

        ArtifactValidationResult result = ArtifactValidator.validate(project, manifest(
                "main",
                List.of(new RouteArtifact("orders", "src/main/resources/routes/orders.camel.yaml", null)),
                List.of(new TestArtifact("orders", "test/orders.camel.it.yaml", null)),
                JavaPolicy.FORBIDDEN, List.of()));

        assertTrue(hasError(result, "route-undeclared", "src/main/resources/routes/hidden.camel.yaml"));
    }

    @Test
    void forbiddenJavaCannotHideBesideYamlRoute() throws Exception {
        write("src/main/resources/routes/orders.camel.yaml", "- route:\n    id: orders\n");
        write("test/orders.camel.it.yaml", citrusTest());
        write("src/main/java/example/HiddenProcessor.java", "package example; class HiddenProcessor {}\n");

        ArtifactValidationResult result = ArtifactValidator.validate(project, manifest(
                "main",
                List.of(new RouteArtifact("orders", "src/main/resources/routes/orders.camel.yaml", null)),
                List.of(new TestArtifact("orders", "test/orders.camel.it.yaml", null)),
                JavaPolicy.FORBIDDEN, List.of()));

        assertTrue(hasError(result, "java-forbidden", "src/main/java/example/HiddenProcessor.java"));
    }

    @Test
    void justificationPolicyRequiresExactApprovedPath() throws Exception {
        write("src/main/resources/routes/orders.camel.yaml", "- route:\n    id: orders\n");
        write("test/orders.camel.it.yaml", citrusTest());
        write("src/main/java/example/Approved.java", "package example; class Approved {}\n");
        write("src/main/java/example/Hidden.java", "package example; class Hidden {}\n");

        ArtifactValidationResult result = ArtifactValidator.validate(project, manifest(
                "spring-boot",
                List.of(new RouteArtifact("orders", "src/main/resources/routes/orders.camel.yaml", null)),
                List.of(new TestArtifact("orders", "test/orders.camel.it.yaml", null)),
                JavaPolicy.JUSTIFICATION_REQUIRED,
                List.of("src/main/java/example/Approved.java")));

        assertFalse(hasError(result, "java-unapproved", "src/main/java/example/Approved.java"));
        assertTrue(hasError(result, "java-unapproved", "src/main/java/example/Hidden.java"));
    }

    @Test
    void camelMainRejectsJavaPoliciesWithoutCompilationEvidence() throws Exception {
        write("src/main/resources/routes/orders.camel.yaml", "- route:\n    id: orders\n");
        write("test/orders.camel.it.yaml", citrusTest());
        write(".camel-kit/config.properties", mainConfiguration());

        for (JavaPolicy javaPolicy : List.of(JavaPolicy.ALLOWED, JavaPolicy.JUSTIFICATION_REQUIRED)) {
            ArtifactValidationResult result = ArtifactValidator.validate(project, manifest(
                    "main",
                    List.of(new RouteArtifact("orders", "src/main/resources/routes/orders.camel.yaml", null)),
                    List.of(new TestArtifact("orders", "test/orders.camel.it.yaml", null)),
                    javaPolicy, List.of()));

            assertTrue(hasError(result, "main-java-policy", null),
                    () -> javaPolicy + " findings: " + result.findings());
        }
    }

    @Test
    void runtimeConfigurationMismatchFails() throws Exception {
        write("src/main/resources/routes/orders.camel.yaml", "- route:\n    id: orders\n");
        write("test/orders.camel.it.yaml", citrusTest());
        write(".camel-kit/config.properties", mainConfiguration().replace("main", "spring-boot"));

        ArtifactValidationResult result = ArtifactValidator.validate(project, manifest(
                "main",
                List.of(new RouteArtifact("orders", "src/main/resources/routes/orders.camel.yaml", null)),
                List.of(new TestArtifact("orders", "test/orders.camel.it.yaml", null)),
                JavaPolicy.FORBIDDEN, List.of()));

        assertTrue(hasError(result, "runtime-config-mismatch", ".camel-kit/config.properties"));
    }

    @Test
    void artifactPathCannotEscapeProject() throws Exception {
        ArtifactValidationResult result = ArtifactValidator.validate(project, manifest(
                "main", List.of(new RouteArtifact("orders", "../orders.camel.yaml", null)),
                List.of(), JavaPolicy.FORBIDDEN, List.of()));

        assertTrue(hasError(result, "artifact-path", "../orders.camel.yaml"));
    }

    @Test
    void requiredRouteAndTestDigestsCannotBeOmitted() throws Exception {
        write("src/main/resources/routes/orders.camel.yaml", "- route:\n    id: orders\n");
        write("test/orders.camel.it.yaml", citrusTest());

        ArtifactValidationResult result = ArtifactValidator.validate(project, manifest(
                "main",
                List.of(new RouteArtifact("orders", "src/main/resources/routes/orders.camel.yaml", null)),
                List.of(new TestArtifact("orders", "test/orders.camel.it.yaml", null)),
                JavaPolicy.FORBIDDEN,
                List.of()));

        assertTrue(hasError(result, "artifact-digest-missing", "src/main/resources/routes/orders.camel.yaml"));
        assertTrue(hasError(result, "artifact-digest-missing", "test/orders.camel.it.yaml"));
    }

    @Test
    void staleAndMalformedArtifactDigestsFailClosed() throws Exception {
        write("src/main/resources/routes/orders.camel.yaml", "- route:\n    id: orders\n");
        write("test/orders.camel.it.yaml", citrusTest());
        String approvedRouteDigest = digest("src/main/resources/routes/orders.camel.yaml");
        write("src/main/resources/routes/orders.camel.yaml", "- route:\n    id: orders\n# edited after approval\n");

        ArtifactValidationResult result = ArtifactValidator.validate(project, manifest(
                "main",
                List.of(new RouteArtifact(
                        "orders", "src/main/resources/routes/orders.camel.yaml",
                        approvedRouteDigest)),
                List.of(new TestArtifact(
                        "orders", "test/orders.camel.it.yaml", "sha256:not-a-digest")),
                JavaPolicy.FORBIDDEN,
                List.of()));

        assertTrue(hasError(result, "artifact-digest", "src/main/resources/routes/orders.camel.yaml"));
        assertTrue(hasError(result, "artifact-digest-format", "test/orders.camel.it.yaml"));
    }

    @Test
    void routeAndCitrusBasenamesMustMatchRouteIdExactly() throws Exception {
        write("src/main/resources/routes/order-route.camel.yaml", "- route:\n    id: orders\n");
        write("test/order-test.camel.it.yaml", citrusTest());

        ArtifactValidationResult result = ArtifactValidator.validate(project, manifest(
                "main",
                List.of(new RouteArtifact("orders", "src/main/resources/routes/order-route.camel.yaml", null)),
                List.of(new TestArtifact("orders", "test/order-test.camel.it.yaml", null)),
                JavaPolicy.FORBIDDEN, List.of()));

        assertTrue(hasError(result, "route-name", "src/main/resources/routes/order-route.camel.yaml"));
        assertTrue(hasError(result, "citrus-test-name", "test/order-test.camel.it.yaml"));
    }

    @Test
    void routeYamlIdMustMatchManifestRouteId() throws Exception {
        write("src/main/resources/routes/orders.camel.yaml", "- route:\n    id: hidden\n");
        write("test/orders.camel.it.yaml", citrusTest());

        ArtifactValidationResult result = ArtifactValidator.validate(project, manifest(
                "main",
                List.of(new RouteArtifact("orders", "src/main/resources/routes/orders.camel.yaml", null)),
                List.of(new TestArtifact("orders", "test/orders.camel.it.yaml", null)),
                JavaPolicy.FORBIDDEN, List.of()));

        assertTrue(hasError(result, "route-yaml-id-mismatch", "src/main/resources/routes/orders.camel.yaml"));
    }

    @Test
    void malformedOrDuplicateKeyRouteYamlIsRejected() throws Exception {
        write("src/main/resources/routes/orders.camel.yaml",
                "- route:\n    id: orders\n    id: hidden\n");
        write("test/orders.camel.it.yaml", citrusTest());

        ArtifactValidationResult result = ArtifactValidator.validate(project, manifest(
                "main",
                List.of(new RouteArtifact("orders", "src/main/resources/routes/orders.camel.yaml", null)),
                List.of(new TestArtifact("orders", "test/orders.camel.it.yaml", null)),
                JavaPolicy.FORBIDDEN, List.of()));

        assertTrue(hasError(result, "route-yaml-parse", "src/main/resources/routes/orders.camel.yaml"));
    }

    @Test
    void acceptsApprovedSpringPomWithResolvedVersionProperties() throws Exception {
        writeSpringProject("${spring.boot.version}");

        ArtifactValidationResult result = ArtifactValidator.validate(
                project, springManifest(), springPolicy());

        assertTrue(result.passed(), () -> result.findings().toString());
    }

    @Test
    void rejectsCitrusVersionThatDiffersFromApprovedPolicy() throws Exception {
        writeSpringProject("${spring.boot.version}");

        ArtifactValidationResult result = ArtifactValidator.validate(
                project, springManifest(), springPolicy("5.0.0-M3"));

        assertTrue(hasError(result, "approved-policy-citrus-version", null));
        assertTrue(hasError(result, "approved-policy-citrus-dependencies", null));
    }

    @Test
    void rejectsSpringPluginVersionThatDiffersFromApprovedPolicy() throws Exception {
        writeSpringProject("4.0.0");

        ArtifactValidationResult result = ArtifactValidator.validate(
                project, springManifest(), springPolicy());

        assertTrue(hasError(result, "runtime-pom-version", "pom.xml"));
    }

    @Test
    void rejectsMavenModelsThatCanTurnTestIntoANoOp() throws Exception {
        writeSpringProject("${spring.boot.version}");
        Path pom = project.resolve("pom.xml");
        String original = Files.readString(pom);

        Files.writeString(pom, original.replace(
                "<properties>", "<packaging>pom</packaging><properties><skipTests>true</skipTests>"));
        ArtifactValidationResult disabled = ArtifactValidator.validate(
                project, springManifest(), springPolicy());

        assertTrue(hasError(disabled, "runtime-pom-packaging", "pom.xml"));
        assertTrue(hasError(disabled, "runtime-pom-test-skip", "pom.xml"));
    }

    @Test
    void rejectsMavenControlsThatIgnoreTestFailures() throws Exception {
        for (String control : List.of("maven.test.failure.ignore", "testFailureIgnore")) {
            writeSpringProject("${spring.boot.version}");
            Path pom = project.resolve("pom.xml");
            Files.writeString(pom, Files.readString(pom).replace(
                    "<properties>",
                    "<properties><" + control + ">true</" + control + ">"));

            ArtifactValidationResult result = ArtifactValidator.validate(
                    project, springManifest(), springPolicy());

            assertTrue(hasError(result, "runtime-pom-test-skip", "pom.xml"), control);
        }
    }

    @Test
    void rejectsUninspectedMavenModelSourcesAndStartupHooks() throws Exception {
        writeSpringProject("${spring.boot.version}");
        Path pom = project.resolve("pom.xml");
        Files.writeString(pom, Files.readString(pom).replace(
                "</project>",
                "<profiles><profile><id>hidden</id></profile></profiles>"
                              + "<pluginRepositories><pluginRepository><id>hidden</id>"
                              + "<url>https://example.invalid</url></pluginRepository></pluginRepositories>"
                              + "</project>"));
        write(".mvn/extensions.xml", "<extensions/>\n");

        ArtifactValidationResult result = ArtifactValidator.validate(
                project, springManifest(), springPolicy());

        assertTrue(hasError(result, "runtime-pom-dynamic-model", "pom.xml"));
        assertTrue(hasError(result, "runtime-maven-hook", ".mvn/extensions.xml"));
    }

    @Test
    void rejectsUnapprovedBuildPlugins() throws Exception {
        writeSpringProject("${spring.boot.version}");
        Path pom = project.resolve("pom.xml");
        Files.writeString(pom, Files.readString(pom).replace(
                "<build><plugins>",
                "<build><plugins><plugin><groupId>org.codehaus.mojo</groupId>"
                                    + "<artifactId>exec-maven-plugin</artifactId><version>3.5.0</version></plugin>"));

        ArtifactValidationResult result = ArtifactValidator.validate(
                project, springManifest(), springPolicy());

        assertTrue(hasError(result, "runtime-pom-plugin", "pom.xml"));
    }

    @Test
    void commentsCannotSpoofRequiredSpringCoordinates() throws Exception {
        writeSpringProject("${spring.boot.version}");
        write("pom.xml", """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <!-- camel-spring-boot-bom spring-boot-maven-plugin -->
                  <groupId>example</groupId><artifactId>orders</artifactId><version>1</version>
                </project>
                """);

        ArtifactValidationResult result = ArtifactValidator.validate(
                project, springManifest(), springPolicy());

        assertTrue(hasError(result, "runtime-pom-coordinate", "pom.xml"));
    }

    @Test
    void pomDoctypeAndExternalEntityAreRejected() throws Exception {
        writeSpringProject("${spring.boot.version}");
        write("pom.xml", """
                <?xml version="1.0"?>
                <!DOCTYPE project [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>&xxe;</artifactId><version>1</version>
                </project>
                """);

        ArtifactValidationResult result = ArtifactValidator.validate(
                project, springManifest(), springPolicy());

        assertTrue(hasError(result, "runtime-pom-parse", "pom.xml"));
    }

    @Test
    void deeplyNestedPomFailsClosedBeforeDomTraversal() throws Exception {
        writeSpringProject("${spring.boot.version}");
        write("pom.xml", """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>orders</artifactId><version>1</version>
                """ + "<nested>".repeat(150) + "value" + "</nested>".repeat(150) + "</project>");

        ArtifactValidationResult result = ArtifactValidator.validate(
                project, springManifest(), springPolicy());

        assertTrue(hasError(result, "runtime-pom-parse", "pom.xml"));
    }

    @Test
    void duplicateRuntimeConfigurationKeysAreRejected() throws Exception {
        writeSpringProject("${spring.boot.version}");
        write(".camel-kit/config.properties", """
                project.runtime=spring-boot
                project.runtime=main
                project.camelVersion=4.21.0
                project.platformBomVersion=4.21.0
                project.springBootVersion=4.1.0
                citrus.version=5.0.0-M2
                """);

        ArtifactValidationResult result = ArtifactValidator.validate(
                project, springManifest(), springPolicy());

        assertTrue(hasError(result, "runtime-config-read", ".camel-kit/config.properties"));
    }

    @Test
    void validatesApprovedQuarkusBomsPluginAndManagedExtension() throws Exception {
        writeQuarkusProject(null);

        ArtifactValidationResult accepted = ArtifactValidator.validate(
                project, quarkusManifest(), quarkusPolicy());
        assertTrue(accepted.passed(), () -> accepted.findings().toString());

        writeQuarkusProject("4.18.2");
        ArtifactValidationResult explicitExtensionVersion = ArtifactValidator.validate(
                project, quarkusManifest(), quarkusPolicy());
        assertTrue(hasError(explicitExtensionVersion, "runtime-pom-version", "pom.xml"));
    }

    private void writeSpringProject(String pluginVersion) throws Exception {
        write("src/main/resources/routes/orders.camel.yaml",
                "- route:\n    id: orders\n    from:\n      uri: direct:start\n");
        write("test/orders.camel.it.yaml", citrusTest());
        write(".camel-kit/config.properties", """
                project.runtime=spring-boot
                project.camelVersion=4.21.0
                project.platformBomVersion=4.21.0
                project.springBootVersion=4.1.0
                citrus.version=5.0.0-M2
                """);
        write("pom.xml", String.format(Locale.ROOT, """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>orders</artifactId><version>1</version>
                  <properties>
                    <camel.platform.version>4.21.0</camel.platform.version>
                    <spring.boot.version>4.1.0</spring.boot.version>
                  </properties>
                  <dependencyManagement><dependencies><dependency>
                    <groupId>org.apache.camel.springboot</groupId>
                    <artifactId>camel-spring-boot-bom</artifactId>
                    <version>${camel.platform.version}</version><type>pom</type><scope>import</scope>
                  </dependency></dependencies></dependencyManagement>
                  <dependencies>
                    <dependency><groupId>org.springframework.boot</groupId>
                      <artifactId>spring-boot-starter</artifactId></dependency>
                    <dependency><groupId>org.apache.camel.springboot</groupId>
                      <artifactId>camel-spring-boot-starter</artifactId></dependency>
                  </dependencies>
                  <build><plugins><plugin>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-maven-plugin</artifactId>
                    <version>%s</version>
                  </plugin></plugins></build>
                </project>
                """, pluginVersion));
    }

    private ArtifactManifest springManifest() throws Exception {
        return new ArtifactManifest(
                1, "spring-boot", "4.21.0", "4.21.0", "4.1.0", "yaml", "simple",
                CITRUS_VERSION, CITRUS_DEPENDENCIES,
                JavaPolicy.FORBIDDEN, List.of(),
                List.of(new RouteArtifact(
                        "orders", "src/main/resources/routes/orders.camel.yaml",
                        digest("src/main/resources/routes/orders.camel.yaml"))),
                List.of(new TestArtifact(
                        "orders", "test/orders.camel.it.yaml",
                        digest("test/orders.camel.it.yaml"))),
                List.of(), true, true);
    }

    private static ArtifactPolicy springPolicy() {
        return springPolicy(CITRUS_VERSION);
    }

    private static ArtifactPolicy springPolicy(String citrusVersion) {
        return new ArtifactPolicy(
                "spring-boot", "4.21.0", "4.21.0", "4.1.0", "yaml", "simple",
                citrusVersion, CitrusDependencyPolicy.required(citrusVersion),
                JavaPolicy.FORBIDDEN, List.of(),
                List.of(new RouteContract(
                        "orders", "src/main/resources/routes/orders.camel.yaml",
                        "test/orders.camel.it.yaml")),
                true, true);
    }

    private void writeQuarkusProject(String extensionVersion) throws Exception {
        write("src/main/resources/routes/orders.camel.yaml",
                "- route:\n    id: orders\n    from:\n      uri: direct:start\n");
        write("test/orders.camel.it.yaml", citrusTest());
        write(".camel-kit/config.properties", """
                project.runtime=quarkus
                project.camelVersion=4.18.2
                project.platformBomVersion=3.33.1
                citrus.version=5.0.0-M2
                """);
        String versionElement = extensionVersion == null ? "" : "<version>" + extensionVersion + "</version>";
        write("pom.xml", String.format(Locale.ROOT, """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>orders</artifactId><version>1</version>
                  <properties>
                    <quarkus.platform.group-id>io.quarkus.platform</quarkus.platform.group-id>
                    <quarkus.platform.version>3.33.1</quarkus.platform.version>
                  </properties>
                  <dependencyManagement><dependencies>
                    <dependency><groupId>${quarkus.platform.group-id}</groupId>
                      <artifactId>quarkus-bom</artifactId><version>${quarkus.platform.version}</version>
                      <type>pom</type><scope>import</scope></dependency>
                    <dependency><groupId>${quarkus.platform.group-id}</groupId>
                      <artifactId>quarkus-camel-bom</artifactId><version>${quarkus.platform.version}</version>
                      <type>pom</type><scope>import</scope></dependency>
                  </dependencies></dependencyManagement>
                  <dependencies><dependency>
                    <groupId>org.apache.camel.quarkus</groupId><artifactId>camel-quarkus-direct</artifactId>%s
                  </dependency></dependencies>
                  <build><plugins><plugin>
                    <groupId>io.quarkus</groupId><artifactId>quarkus-maven-plugin</artifactId>
                    <version>${quarkus.platform.version}</version>
                  </plugin></plugins></build>
                </project>
                """, versionElement));
    }

    private ArtifactManifest quarkusManifest() throws Exception {
        return new ArtifactManifest(
                1, "quarkus", "4.18.2", "3.33.1", null, "yaml", "simple",
                CITRUS_VERSION, CITRUS_DEPENDENCIES,
                JavaPolicy.FORBIDDEN, List.of(),
                List.of(new RouteArtifact(
                        "orders", "src/main/resources/routes/orders.camel.yaml",
                        digest("src/main/resources/routes/orders.camel.yaml"))),
                List.of(new TestArtifact(
                        "orders", "test/orders.camel.it.yaml",
                        digest("test/orders.camel.it.yaml"))),
                List.of(), true, true);
    }

    private static ArtifactPolicy quarkusPolicy() {
        return new ArtifactPolicy(
                "quarkus", "4.18.2", "3.33.1", null, "yaml", "simple",
                CITRUS_VERSION, CITRUS_DEPENDENCIES,
                JavaPolicy.FORBIDDEN, List.of(),
                List.of(new RouteContract(
                        "orders", "src/main/resources/routes/orders.camel.yaml",
                        "test/orders.camel.it.yaml")),
                true, true);
    }

    private ArtifactManifest manifest(
            String runtime,
            List<RouteArtifact> routes,
            List<TestArtifact> tests,
            JavaPolicy javaPolicy,
            List<String> exceptions)
            throws Exception {
        return manifest(runtime, routes, tests, javaPolicy, exceptions, List.of());
    }

    private static ArtifactManifest manifest(
            String runtime,
            List<RouteArtifact> routes,
            List<TestArtifact> tests,
            JavaPolicy javaPolicy,
            List<String> exceptions,
            List<DeclaredArtifact> artifacts) {
        return new ArtifactManifest(
                1, runtime, "4.21.0", null, null, "yaml", "simple",
                CITRUS_VERSION, CITRUS_DEPENDENCIES, javaPolicy, exceptions,
                routes, tests, artifacts, true, true);
    }

    private static String citrusTest() {
        return """
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
                """;
    }

    private static String citrusMatrixTest() {
        return """
                name: orders-matrix
                actions:
                  - send:
                      endpoint: "camel:sync:direct:camel-kit-ship-test-orders"
                      message:
                        headers:
                          - name: requestId
                            value: req-1
                          - name: timestamp
                            value: "2026-07-17T10:15:30Z"
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
                          data: accepted-priority
                  - send:
                      endpoint: "camel:sync:direct:camel-kit-ship-test-orders"
                      message:
                        body:
                          data: standard-order
                  - receive:
                      endpoint: "camel:sync:direct:camel-kit-ship-test-orders"
                      message:
                        body:
                          data: accepted-standard
                """;
    }

    private static String mainConfiguration() {
        return """
                project.runtime=main
                project.camelVersion=4.21.0
                project.platformBomVersion=4.21.0
                citrus.version=5.0.0-M2
                """;
    }

    private void writeCitrusConfiguration() throws Exception {
        write(CitrusDependencyPolicy.FORBIDDEN_JBANG_PROPERTIES_PATH,
                "run.deps=" + String.join(",", CITRUS_DEPENDENCIES) + '\n');
    }

    private void write(String relative, String content) throws Exception {
        Path file = project.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private String digest(String relative) throws Exception {
        byte[] content = Files.readAllBytes(project.resolve(relative));
        return "sha256:" + java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(content));
    }

    private static boolean hasError(ArtifactValidationResult result, String code, String path) {
        return result.findings().stream()
                .anyMatch(finding -> finding.severity() == ArtifactFinding.Severity.ERROR
                        && code.equals(finding.code())
                        && (path == null || path.equals(finding.path())));
    }
}
