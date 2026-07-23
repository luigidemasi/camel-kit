package io.github.luigidemasi.camelkit.ship.artifact;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import io.github.luigidemasi.camelkit.ship.ShipDigest;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogEvidenceSet;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogEvidenceSet.ArtifactEvidence;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogEvidenceSet.SubjectEvidence;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogSubject;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogTarget;
import io.github.luigidemasi.camelkit.ship.resolver.MavenCoordinate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArtifactValidatorCatalogDependencyTest {

    @TempDir
    Path project;

    @Test
    void exactMainPomAcceptsOnlyLiteralControllerAndCatalogRoots() throws Exception {
        writePom("", "", "4.21.0");

        ArtifactValidator.CatalogDependencyBinding binding = binding();

        assertTrue(binding.validation().passed(), () -> binding.validation().findings().toString());
        assertTrue(binding.runtimeDependencies().stream()
                .anyMatch(dependency -> "camel-direct".equals(dependency.artifactId())));
        assertEquals(ShipDigest.sha256(Files.readAllBytes(project.resolve("pom.xml"))), binding.pomDigest());
    }

    @Test
    void exactMainPomRejectsEveryMavenExecutionOrPublicationSection() throws Exception {
        for (String section : List.of(
                "<build><resources/></build>",
                "<reporting><plugins><plugin><artifactId>maven-project-info-reports-plugin</artifactId>"
                                               + "</plugin></plugins></reporting>",
                "<distributionManagement><repository><id>releases</id>"
                                                                                    + "<url>https://example.invalid</url></repository></distributionManagement>")) {
            writePom("", section, "4.21.0");
            assertFinding("catalog-runtime-pom-policy");
        }
    }

    @Test
    void exactMainPomRejectsResolvedPropertyExpressions() throws Exception {
        writePom("", "", "${project.version}");

        assertFalse(binding().validation().passed());
        assertFinding("catalog-runtime-pom-policy");
    }

    @Test
    void exactMainPomRejectsForeignXmlNamespacesAndDuplicateRoots() throws Exception {
        writePom("", "<evil:dependencies xmlns:evil=\"urn:evil\"/>", "4.21.0");
        assertFinding("catalog-runtime-pom-policy");

        writePom("""
                <dependency><groupId>org.apache.camel</groupId>
                  <artifactId>camel-direct</artifactId><version>4.21.0</version></dependency>
                """, "", "4.21.0");
        assertFalse(binding().validation().passed());
    }

    @Test
    void exactMainPomRejectsNonJarPackaging() throws Exception {
        writePom("", "<packaging>pom</packaging>", "4.21.0");

        assertFalse(binding().validation().passed());
        assertFinding("catalog-runtime-pom-policy");
    }

    @Test
    void exactMainPomRequiresOneLiteralModelVersionAndDependenciesContainer() throws Exception {
        String valid = validPom();
        String model = "<modelVersion>4.0.0</modelVersion>";
        String dependencies = validDependencies("4.21.0", "");
        for (String invalid : List.of(
                valid.replace(model, ""),
                valid.replace(model, "<modelVersion>3.0.0</modelVersion>"),
                valid.replace(model, model + model),
                valid.replace(dependencies, ""),
                valid.replace(dependencies, dependencies + dependencies))) {
            writeRawPom(invalid);
            assertFinding("catalog-runtime-pom-policy");
        }
    }

    @Test
    void exactMainPomRejectsUnknownRootAndDependencyFields() throws Exception {
        writePom("", "<name>orders</name>", "4.21.0");
        assertFinding("catalog-runtime-pom-policy");

        writeRawPom(validPom().replace(
                "<artifactId>camel-direct</artifactId>",
                "<artifactId>camel-direct</artifactId><systemPath>/tmp/escape.jar</systemPath>"));
        assertFinding("catalog-runtime-pom-policy");

        writeRawPom(validPom().replace(
                "<groupId>example</groupId>",
                "<groupId>example<name>hidden</name></groupId>"));
        assertFinding("catalog-runtime-pom-policy");
    }

    @Test
    void exactMainPomRejectsProjectCoordinateExpressions() throws Exception {
        writeRawPom(validPom().replace("<version>4.21.0</version>\n  <dependencies>",
                "<version>${revision}</version>\n  <dependencies>"));
        assertFinding("catalog-runtime-pom-policy");

        writeRawPom(validPom().replace(
                "<groupId>example</groupId>", "<groupId>${env.HOME}</groupId>"));
        assertFinding("catalog-runtime-pom-policy");
    }

    @Test
    void exactMainPomRequiresOnlyTheDefaultMavenNamespaceAndNoAttributes() throws Exception {
        String valid = validPom();
        for (String invalid : List.of(
                valid.replace("<project xmlns=\"http://maven.apache.org/POM/4.0.0\">", "<project>"),
                valid.replace("<project xmlns=\"http://maven.apache.org/POM/4.0.0\">",
                        "<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:evil=\"urn:evil\">"),
                valid.replace("<project xmlns=\"http://maven.apache.org/POM/4.0.0\">",
                        "<project xmlns=\"http://maven.apache.org/POM/4.0.0\" id=\"hidden\">"),
                valid.replace("<modelVersion>", "<modelVersion id=\"hidden\">"),
                valid.replace("<dependency>", "<dependency id=\"hidden\">"))) {
            writeRawPom(invalid);
            assertFinding("catalog-runtime-pom-policy");
        }
    }

    @Test
    void exactMainPomRejectsCommentsAndProcessingInstructions() throws Exception {
        for (String markup : List.of("<!-- hidden -->", "<?ship hidden?>")) {
            writeRawPom(validPom().replace("<dependencies>", markup + "<dependencies>"));
            assertFinding("catalog-runtime-pom-policy");
        }
    }

    private ArtifactValidator.CatalogDependencyBinding binding() {
        return ArtifactValidator.bindCatalogDependencies(project, policy(), evidence());
    }

    private void assertFinding(String code) {
        assertTrue(binding().validation().findings().stream().anyMatch(finding -> code.equals(finding.code())),
                () -> binding().validation().findings().toString());
    }

    private void writePom(String dependencyExtra, String projectExtra, String directVersion) throws Exception {
        writeRawPom(String.format(Locale.ROOT, """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>orders</artifactId><version>4.21.0</version>
                  %s
                %s
                </project>
                """, validDependencies(directVersion, dependencyExtra), projectExtra));
    }

    private String validPom() {
        return String.format(Locale.ROOT, """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>example</groupId><artifactId>orders</artifactId><version>4.21.0</version>
                  %s
                </project>
                """, validDependencies("4.21.0", ""));
    }

    private static String validDependencies(String directVersion, String dependencyExtra) {
        return String.format(Locale.ROOT, """
                <dependencies>
                  <dependency><groupId>org.apache.camel</groupId>
                    <artifactId>camel-main</artifactId><version>4.21.0</version></dependency>
                  <dependency><groupId>org.apache.camel</groupId>
                    <artifactId>camel-yaml-dsl</artifactId><version>4.21.0</version></dependency>
                  <dependency><groupId>org.apache.camel</groupId>
                    <artifactId>camel-direct</artifactId><version>%s</version></dependency>
                  %s
                </dependencies>
                """, directVersion, dependencyExtra).stripTrailing();
    }

    private void writeRawPom(String pom) throws Exception {
        Files.writeString(project.resolve("pom.xml"), pom);
    }

    private static ArtifactPolicy policy() {
        return new ArtifactPolicy(
                "main", "4.21.0", null, null, "yaml", "simple", "5.0.0-M2",
                CitrusDependencyPolicy.required("5.0.0-M2"),
                ArtifactPolicy.JavaPolicy.FORBIDDEN, List.of(), List.of(), true, true);
    }

    private static CatalogEvidenceSet evidence() {
        CatalogSubject direct = new CatalogSubject(CatalogSubject.Kind.COMPONENT, "direct");
        MavenCoordinate catalog = MavenCoordinate.jar("org.apache.camel", "camel-catalog", "4.21.0");
        String catalogDigest = "sha256:" + "a".repeat(64);
        return CatalogEvidenceSet.create(
                new CatalogTarget("main", "4.21.0", null, null), catalog,
                List.of(new ArtifactEvidence(catalog, catalogDigest, 1)),
                List.of(new SubjectEvidence(
                        direct, catalog, catalogDigest,
                        "org/apache/camel/catalog/components/direct.json", "sha256:" + "b".repeat(64),
                        "org.apache.camel", "camel-direct", "4.21.0", false)));
    }
}
