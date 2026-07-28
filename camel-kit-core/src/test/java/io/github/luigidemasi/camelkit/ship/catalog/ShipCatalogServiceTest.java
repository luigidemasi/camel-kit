package io.github.luigidemasi.camelkit.ship.catalog;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.AbstractCollection;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import io.github.luigidemasi.camelkit.ship.catalog.CatalogSubject.Kind;
import io.github.luigidemasi.camelkit.ship.resolver.MavenCoordinate;
import io.github.luigidemasi.camelkit.ship.resolver.ResolvedExactMavenArtifact;
import io.github.luigidemasi.camelkit.ship.resolver.ShipMavenResolver;
import io.github.luigidemasi.camelkit.ship.resolver.ShipMavenResolver.ResolutionMode;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledOnOs(OS.LINUX)
class ShipCatalogServiceTest {

    private static final String CAMEL_VERSION = "4.18.0";

    @Test
    void mutableCatalogVersionsAreRejectedBeforeResolution() {
        assertThrows(IllegalArgumentException.class,
                () -> new CatalogTarget("main", "4.18.1-SNAPSHOT", null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new CatalogTarget("quarkus", CAMEL_VERSION, "LATEST", null));
        assertThrows(IllegalArgumentException.class,
                () -> new CatalogTarget("main", "4.18.0-20260716.123456-1", null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new CatalogTarget("main", "..", null, null));
    }

    @Test
    void symbolicLocalRepositoryIsRejected() throws Exception {
        Path realRepository = Files.createDirectory(repository.resolve("real-repository"));
        Path symbolicRepository = repository.resolve("symbolic-repository");
        Files.createSymbolicLink(symbolicRepository, realRepository);
        ShipCatalogService service = offlineService(symbolicRepository);

        IOException error = assertThrows(IOException.class, () -> service.snapshot(
                new CatalogTarget("main", CAMEL_VERSION, null, null)));

        assertTrue(error.getMessage().contains("real directory"));
    }

    @Test
    void localRepositoryBelowSymbolicAncestorIsAccepted() throws Exception {
        Path realParent = Files.createDirectory(repository.resolve("real-parent"));
        Path symbolicParent = repository.resolve("symbolic-parent");
        Files.createSymbolicLink(symbolicParent, realParent.getFileName());
        Path localRepository = symbolicParent.resolve("repository");
        Files.createDirectories(localRepository);
        writeJar(localRepository, "org.apache.camel", "camel-catalog", CAMEL_VERSION,
                mainEntries(CAMEL_VERSION, "timer"));

        CatalogEvidenceSet result = offlineService(localRepository).snapshot(
                new CatalogTarget("main", CAMEL_VERSION, null, null)).evidenceFor(
                        List.of(subject(Kind.COMPONENT, "timer")));

        assertEquals(1, result.artifacts().size());
    }

    @TempDir
    Path repository;

    @Test
    void verifiesAllMainCatalogKindsOffline() throws Exception {
        writeMainCatalog(CAMEL_VERSION, "timer");
        ShipCatalogService service = service();
        CatalogTarget target = new CatalogTarget("main", CAMEL_VERSION, null, null);

        CatalogEvidenceSet result = service.snapshot(target).evidenceFor(List.of(
                subject(Kind.COMPONENT, "timer"),
                subject(Kind.EIP, "split"),
                subject(Kind.DATAFORMAT, "csv"),
                subject(Kind.LANGUAGE, "simple")));

        assertEquals("org.apache.camel:camel-catalog:" + CAMEL_VERSION, result.platformCoordinate().gav());
        assertEquals(1, result.artifacts().size());
        assertEquals(4, result.subjects().size());
        assertTrue(result.artifacts().get(0).sha256().matches("sha256:[0-9a-f]{64}"));
        assertTrue(result.subjects().stream()
                .allMatch(evidence -> evidence.resourceSha256().matches("sha256:[0-9a-f]{64}")));
        assertTrue(result.digest().matches("sha256:[0-9a-f]{64}"));
    }

    @Test
    void componentModelsRetainExactEvidenceAndBoundedOptions() throws Exception {
        Map<String, byte[]> entries = mainEntries(CAMEL_VERSION, "timer");
        put(entries, "org/apache/camel/catalog/components/timer.json", componentIdentity(
                "timer", "org.apache.camel", "camel-timer", CAMEL_VERSION));
        writeJar("org.apache.camel", "camel-catalog", CAMEL_VERSION, entries);
        CatalogTarget target = new CatalogTarget("main", CAMEL_VERSION, null, null);

        ShipCatalogService.Snapshot snapshot = service().snapshot(target);
        CatalogComponentModel model = snapshot.componentModelsFor(
                List.of(subject(Kind.COMPONENT, "timer"))).get(0);
        CatalogEvidenceSet evidence = snapshot.evidenceFor(
                List.of(subject(Kind.COMPONENT, "timer")));

        assertEquals(evidence.subjects().get(0), model.evidence());
        assertEquals("timer:timerName", model.syntax());
        assertEquals(List.of("timerName", "delay", "extra", "exceptionHandler"), model.options().stream()
                .map(CatalogComponentModel.Option::name).toList());
        CatalogComponentModel.Option extra = model.options().stream()
                .filter(option -> "extra".equals(option.name())).findFirst().orElseThrow();
        CatalogComponentModel.Option exceptionHandler = model.options().stream()
                .filter(option -> "exceptionHandler".equals(option.name())).findFirst().orElseThrow();
        assertEquals("extra.", extra.prefix());
        assertNull(extra.optionalPrefix());
        assertNull(exceptionHandler.prefix());
        assertEquals("consumer.", exceptionHandler.optionalPrefix());
    }

    @Test
    void componentEnumParsingAcceptsRealCatalogSizeAndTheFixedLimitButRejectsOneOver() throws Exception {
        CatalogTarget target = new CatalogTarget("main", CAMEL_VERSION, null, null);
        CatalogSubject timer = subject(Kind.COMPONENT, "timer");

        writeMainCatalogWithEnumValues(380);
        assertEquals(380, service().snapshot(target).componentModelsFor(List.of(timer)).get(0)
                .options().get(1).enumValues().size());

        writeMainCatalogWithEnumValues(CatalogComponentModel.MAX_ENUM_VALUES);
        assertEquals(CatalogComponentModel.MAX_ENUM_VALUES,
                service().snapshot(target).componentModelsFor(List.of(timer)).get(0)
                        .options().get(1).enumValues().size());

        writeMainCatalogWithEnumValues(CatalogComponentModel.MAX_ENUM_VALUES + 1);
        IOException excessive = assertThrows(IOException.class,
                () -> service().snapshot(target).componentModelsFor(List.of(timer)));
        assertTrue(excessive.getMessage().contains("unsafe enum"), excessive.getMessage());
    }

    @Test
    void fractionalOptionIndexesAreRejectedInsteadOfTruncated() throws Exception {
        Map<String, byte[]> entries = mainEntries(CAMEL_VERSION, "timer");
        put(entries, "org/apache/camel/catalog/components/timer.json", componentIdentity(
                "timer", "org.apache.camel", "camel-timer", CAMEL_VERSION)
                .replace("\"index\": 0", "\"index\": 0.5"));
        writeJar("org.apache.camel", "camel-catalog", CAMEL_VERSION, entries);

        IOException error = assertThrows(IOException.class, () -> service().snapshot(
                new CatalogTarget("main", CAMEL_VERSION, null, null)).componentModelsFor(
                        List.of(subject(Kind.COMPONENT, "timer"))));

        assertTrue(error.getMessage().contains("metadata is incomplete"));
    }

    @Test
    void springProviderAvailabilityIsBoundWithoutFollowingDescriptorRepositories() throws Exception {
        writeMainCatalog(CAMEL_VERSION, "timer");
        writeSpringProvider("4.18.0", CAMEL_VERSION);
        ShipCatalogService service = service();
        CatalogTarget target = new CatalogTarget("spring-boot", CAMEL_VERSION, "4.18.0", "3.5.0");

        CatalogEvidenceSet result = service.snapshot(target).evidenceFor(List.of(
                subject(Kind.COMPONENT, "kafka"), subject(Kind.EIP, "split")));

        assertEquals("org.apache.camel.springboot:camel-catalog-provider-springboot:4.18.0",
                result.platformCoordinate().gav());
        assertEquals(5, result.artifacts().size());
        assertEquals("org.apache.camel.springboot", result.subjects().get(0).groupId());
        assertEquals(Kind.EIP, result.subjects().get(1).subject().kind());
        assertEquals("org.apache.camel:camel-catalog:" + CAMEL_VERSION,
                result.subjects().get(1).catalogCoordinate().gav());
    }

    @Test
    void springBootVersionIsVerifiedFromTheSnapshottedParentPom() throws Exception {
        writeMainCatalog(CAMEL_VERSION, "timer");
        writeSpringProvider("4.18.0", CAMEL_VERSION, "3.5.1");

        IOException mismatch = assertThrows(IOException.class, () -> service().snapshot(
                new CatalogTarget("spring-boot", CAMEL_VERSION, "4.18.0", "3.5.0")));
        assertTrue(mismatch.getMessage().contains("approved Spring Boot version"));

        writeSpringProvider("4.18.0", CAMEL_VERSION, null);
        IOException missing = assertThrows(IOException.class, () -> service().snapshot(
                new CatalogTarget("spring-boot", CAMEL_VERSION, "4.18.0", "3.5.0")));
        assertTrue(missing.getMessage().contains("lacks an immutable Spring Boot version"));

        writeSpringProvider("4.18.0", CAMEL_VERSION, "LATEST");
        IOException mutable = assertThrows(IOException.class, () -> service().snapshot(
                new CatalogTarget("spring-boot", CAMEL_VERSION, "4.18.0", "3.5.0")));
        assertTrue(mutable.getMessage().contains("lacks an immutable Spring Boot version"));
    }

    @Test
    void pomContentsMustMatchTheExactResolvedCoordinate() throws Exception {
        writeMainCatalog(CAMEL_VERSION, "timer");
        writeQuarkusBom("3.33.2", "3.33.1", CAMEL_VERSION);
        Path bom = artifact("io.quarkus.platform", "quarkus-camel-bom", "3.33.2", "pom");
        Files.writeString(bom, """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>attacker.example</groupId>
                  <artifactId>quarkus-camel-bom</artifactId>
                  <version>3.33.2</version>
                </project>
                """);

        IOException mismatch = assertThrows(IOException.class, () -> service().snapshot(
                new CatalogTarget("quarkus", CAMEL_VERSION, "3.33.2", null)));

        assertTrue(mismatch.getMessage().contains("POM identity"));
    }

    @Test
    void pomDepthLimitAcceptsTheBoundaryAndRejectsOneBeyond() throws Exception {
        writeMainCatalog(CAMEL_VERSION, "timer");
        writeQuarkusProvider("3.33.1", CAMEL_VERSION);
        CatalogTarget target = new CatalogTarget("quarkus", CAMEL_VERSION, "3.33.2", null);
        String body = quarkusBomBody("3.33.1", CAMEL_VERSION);

        writePom("io.quarkus.platform", "quarkus-camel-bom", "3.33.2",
                body + nestedPomElements(63));
        assertEquals(3, service().snapshot(target).evidenceFor(
                List.of(subject(Kind.COMPONENT, "kafka"))).artifacts().size());

        writePom("io.quarkus.platform", "quarkus-camel-bom", "3.33.2",
                body + nestedPomElements(64));
        IOException excessive = assertThrows(IOException.class, () -> service().snapshot(target));
        assertTrue(excessive.getMessage().contains("structural limits"));
    }

    @Test
    void pomElementLimitAcceptsTheBoundaryAndRejectsOneBeyond() throws Exception {
        writeMainCatalog(CAMEL_VERSION, "timer");
        writeQuarkusProvider("3.33.1", CAMEL_VERSION);
        CatalogTarget target = new CatalogTarget("quarkus", CAMEL_VERSION, "3.33.2", null);
        String body = quarkusBomBody("3.33.1", CAMEL_VERSION);
        int fixedElements = 15;

        writePom("io.quarkus.platform", "quarkus-camel-bom", "3.33.2",
                body + "<padding/>".repeat(16_384 - fixedElements));
        assertEquals(3, service().snapshot(target).evidenceFor(
                List.of(subject(Kind.COMPONENT, "kafka"))).artifacts().size());

        writePom("io.quarkus.platform", "quarkus-camel-bom", "3.33.2",
                body + "<padding/>".repeat(16_385 - fixedElements));
        IOException excessive = assertThrows(IOException.class, () -> service().snapshot(target));
        assertTrue(excessive.getMessage().contains("structural limits"));
    }

    @Test
    void pomNodeLimitAcceptsTheBoundaryAndRejectsOneBeyond() throws Exception {
        writeMainCatalog(CAMEL_VERSION, "timer");
        writeQuarkusProvider("3.33.1", CAMEL_VERSION);
        CatalogTarget target = new CatalogTarget("quarkus", CAMEL_VERSION, "3.33.2", null);
        String body = minifiedQuarkusBomBody("3.33.1", CAMEL_VERSION);
        int fixedNodes = 25;

        writeRawPom("io.quarkus.platform", "quarkus-camel-bom", "3.33.2", "",
                body + "<!---->".repeat(65_536 - fixedNodes));
        assertEquals(3, service().snapshot(target).evidenceFor(
                List.of(subject(Kind.COMPONENT, "kafka"))).artifacts().size());

        writeRawPom("io.quarkus.platform", "quarkus-camel-bom", "3.33.2", "",
                body + "<!---->".repeat(65_537 - fixedNodes));
        IOException excessive = assertThrows(IOException.class, () -> service().snapshot(target));
        assertTrue(excessive.getMessage().contains("structural limits"));
    }

    @Test
    void pomNamespaceLimitAcceptsTheBoundaryAndRejectsOneBeyond() throws Exception {
        writeMainCatalog(CAMEL_VERSION, "timer");
        writeQuarkusProvider("3.33.1", CAMEL_VERSION);
        CatalogTarget target = new CatalogTarget("quarkus", CAMEL_VERSION, "3.33.2", null);
        String body = minifiedQuarkusBomBody("3.33.1", CAMEL_VERSION);

        writeRawPom("io.quarkus.platform", "quarkus-camel-bom", "3.33.2",
                namespaceDeclarations(511), body);
        assertEquals(3, service().snapshot(target).evidenceFor(
                List.of(subject(Kind.COMPONENT, "kafka"))).artifacts().size());

        writeRawPom("io.quarkus.platform", "quarkus-camel-bom", "3.33.2",
                namespaceDeclarations(512), body);
        IOException excessive = assertThrows(IOException.class, () -> service().snapshot(target));
        assertTrue(excessive.getMessage().contains("structural limits"));
    }

    @Test
    void pomAttributeLimitAcceptsTheBoundaryAndRejectsOneBeyond() throws Exception {
        writeMainCatalog(CAMEL_VERSION, "timer");
        writeQuarkusProvider("3.33.1", CAMEL_VERSION);
        CatalogTarget target = new CatalogTarget("quarkus", CAMEL_VERSION, "3.33.2", null);
        String body = minifiedQuarkusBomBody("3.33.1", CAMEL_VERSION);

        writeRawPom("io.quarkus.platform", "quarkus-camel-bom", "3.33.2",
                attributeDeclarations(511), body);
        assertEquals(3, service().snapshot(target).evidenceFor(
                List.of(subject(Kind.COMPONENT, "kafka"))).artifacts().size());

        writeRawPom("io.quarkus.platform", "quarkus-camel-bom", "3.33.2",
                attributeDeclarations(512), body);
        IOException excessive = assertThrows(IOException.class, () -> service().snapshot(target));
        assertTrue(excessive.getMessage().contains("structural limits"));
    }

    @Test
    void pomNamespaceModelAndSingletonAmbiguityFailClosed() throws Exception {
        writeMainCatalog(CAMEL_VERSION, "timer");
        CatalogTarget target = new CatalogTarget("quarkus", CAMEL_VERSION, "3.33.2", null);
        String body = minifiedQuarkusBomBody("3.33.1", CAMEL_VERSION);

        writeRawPom("io.quarkus.platform", "quarkus-camel-bom", "3.33.2", "", body,
                "urn:attacker.example:pom");
        IOException namespace = assertThrows(IOException.class, () -> service().snapshot(target));
        assertTrue(namespace.getMessage().contains("root is not project"));

        writeRawPom("io.quarkus.platform", "quarkus-camel-bom", "3.33.2", "", body);
        Path pom = artifact("io.quarkus.platform", "quarkus-camel-bom", "3.33.2", "pom");
        Files.writeString(pom, Files.readString(pom).replace(
                "<modelVersion>4.0.0</modelVersion>", "<modelVersion>3.0.0</modelVersion>"));
        IOException model = assertThrows(IOException.class, () -> service().snapshot(target));
        assertTrue(model.getMessage().contains("unsupported model version"));

        writePom("io.quarkus.platform", "quarkus-camel-bom", "3.33.2",
                body + "<version>attacker</version>");
        IOException coordinate = assertThrows(IOException.class, () -> service().snapshot(target));
        assertTrue(coordinate.getMessage().contains("duplicate version"));

        writePom("io.quarkus.platform", "quarkus-camel-bom", "3.33.2", """
                <dependencyManagement>
                  <dependencies></dependencies>
                  <dependencies></dependencies>
                </dependencyManagement>
                """);
        IOException container = assertThrows(IOException.class, () -> service().snapshot(target));
        assertTrue(container.getMessage().contains("duplicate dependencies"));
    }

    @Test
    void repeatedPomPlaceholdersAreAllowedButCyclesFailClosed() throws Exception {
        writeMainCatalog(CAMEL_VERSION, "timer");
        writeQuarkusProvider("3.3.1", CAMEL_VERSION);
        CatalogTarget target = new CatalogTarget("quarkus", CAMEL_VERSION, "3.33.2", null);
        writePom("io.quarkus.platform", "quarkus-camel-bom", "3.33.2", """
                <properties><part>3</part></properties>
                <dependencyManagement><dependencies>
                  <dependency>
                    <groupId>org.apache.camel.quarkus</groupId>
                    <artifactId>camel-quarkus-catalog</artifactId>
                    <version>${part}.${part}.1</version>
                  </dependency>
                  <dependency>
                    <groupId>org.apache.camel</groupId>
                    <artifactId>camel-catalog</artifactId>
                    <version>4.18.0</version>
                  </dependency>
                </dependencies></dependencyManagement>
                """);
        assertEquals(3, service().snapshot(target).evidenceFor(
                List.of(subject(Kind.COMPONENT, "kafka"))).artifacts().size());

        writePom("io.quarkus.platform", "quarkus-camel-bom", "3.33.2", """
                <properties><catalog.version>${catalog.version}</catalog.version></properties>
                <dependencyManagement><dependencies>
                  <dependency>
                    <groupId>org.apache.camel.quarkus</groupId>
                    <artifactId>camel-quarkus-catalog</artifactId>
                    <version>${catalog.version}</version>
                  </dependency>
                  <dependency>
                    <groupId>org.apache.camel</groupId>
                    <artifactId>camel-catalog</artifactId>
                    <version>4.18.0</version>
                  </dependency>
                </dependencies></dependencyManagement>
                """);
        IOException cycle = assertThrows(IOException.class, () -> service().snapshot(target));
        assertTrue(cycle.getMessage().contains("cyclic Maven property"));
    }

    @Test
    void quarkusProviderIsDerivedFromPlatformBomOffline() throws Exception {
        writeMainCatalog(CAMEL_VERSION, "timer");
        writeQuarkusBom("3.33.2", "3.33.1", CAMEL_VERSION);
        writeQuarkusProvider("3.33.1", CAMEL_VERSION);
        ShipCatalogService service = service();
        CatalogTarget target = new CatalogTarget("quarkus", CAMEL_VERSION, "3.33.2", null);

        CatalogEvidenceSet result = service.snapshot(target).evidenceFor(List.of(
                subject(Kind.COMPONENT, "kafka"), subject(Kind.LANGUAGE, "simple")));

        assertEquals("io.quarkus.platform:quarkus-camel-bom:3.33.2", result.platformCoordinate().gav());
        assertEquals(3, result.artifacts().size());
        assertTrue(result.subjects().stream()
                .allMatch(evidence -> "org.apache.camel.quarkus".equals(evidence.groupId())));
    }

    @Test
    void quarkusBomCamelMismatchFailsClosed() throws Exception {
        writeMainCatalog(CAMEL_VERSION, "timer");
        writeQuarkusBom("3.33.1", "3.33.0", "4.18.1");

        IOException error = assertThrows(IOException.class, () -> service().snapshot(
                new CatalogTarget("quarkus", CAMEL_VERSION, "3.33.1", null)));

        assertTrue(error.getMessage().contains("approved Camel version"));
    }

    @Test
    void mutableVersionDerivedFromBomIsRejected() throws Exception {
        writeMainCatalog(CAMEL_VERSION, "timer");
        writeQuarkusBom("3.33.1", "LATEST", CAMEL_VERSION);

        IOException error = assertThrows(IOException.class, () -> service().snapshot(
                new CatalogTarget("quarkus", CAMEL_VERSION, "3.33.1", null)));

        assertTrue(error.getMessage().contains("unsafe or mutable Maven coordinate"));
    }

    @Test
    void missingOfflineArtifactNeverFallsBackToBundledCatalog() {
        IOException error = assertThrows(IOException.class, () -> service().snapshot(
                new CatalogTarget("main", CAMEL_VERSION, null, null)));

        assertTrue(error.getMessage().toLowerCase(Locale.ROOT).contains("offline"), error.getMessage());
    }

    @Test
    void mismatchedResourceIdentityIsRejected() throws Exception {
        writeMainCatalog(CAMEL_VERSION, "different-name");

        IOException error = assertThrows(IOException.class, () -> service().snapshot(
                new CatalogTarget("main", CAMEL_VERSION, null, null)).evidenceFor(
                        List.of(subject(Kind.COMPONENT, "timer"))));

        assertTrue(error.getMessage().contains("identity mismatch"));
    }

    @Test
    void trailingJsonTokensAreRejectedBehindASafeDiagnostic() throws Exception {
        Map<String, byte[]> entries = mainEntries(CAMEL_VERSION, "timer");
        String resource = "org/apache/camel/catalog/components/timer.json";
        String valid = new String(entries.get(resource), StandardCharsets.UTF_8);
        put(entries, resource, valid + "{\"secret\":\"do-not-reflect\"}");
        writeJar("org.apache.camel", "camel-catalog", CAMEL_VERSION, entries);

        IOException error = assertThrows(IOException.class, () -> service().snapshot(
                new CatalogTarget("main", CAMEL_VERSION, null, null)).evidenceFor(
                        List.of(subject(Kind.COMPONENT, "timer"))));

        assertTrue(error.getMessage().contains("not valid JSON"));
        assertFalse(error.getMessage().contains("do-not-reflect"));
    }

    @Test
    void oversizedCatalogResourceIsRejectedBeforeJsonParsing() throws Exception {
        Map<String, byte[]> entries = mainEntries(CAMEL_VERSION, "timer");
        entries.put("org/apache/camel/catalog/components/timer.json", new byte[2 * 1024 * 1024 + 1]);
        writeJar("org.apache.camel", "camel-catalog", CAMEL_VERSION, entries);

        IOException error = assertThrows(IOException.class, () -> service().snapshot(
                new CatalogTarget("main", CAMEL_VERSION, null, null)).evidenceFor(
                        List.of(subject(Kind.COMPONENT, "timer"))));

        assertTrue(error.getMessage().contains("exceeds its size limit"), error.getMessage());
    }

    @Test
    void catalogResourceAtTheExactLimitIsAccepted() throws Exception {
        Map<String, byte[]> entries = mainEntries(CAMEL_VERSION, "timer");
        byte[] json = componentIdentity("timer", "org.apache.camel", "camel-timer", CAMEL_VERSION)
                .getBytes(StandardCharsets.UTF_8);
        byte[] atLimit = Arrays.copyOf(json, 2 * 1024 * 1024);
        Arrays.fill(atLimit, json.length, atLimit.length, (byte) ' ');
        entries.put("org/apache/camel/catalog/components/timer.json", atLimit);
        writeJar("org.apache.camel", "camel-catalog", CAMEL_VERSION, entries);

        CatalogEvidenceSet result = service().snapshot(
                new CatalogTarget("main", CAMEL_VERSION, null, null)).evidenceFor(
                        List.of(subject(Kind.COMPONENT, "timer")));

        assertEquals(1, result.subjects().size());
    }

    @Test
    void unsafeAndDuplicateZipEntryNamesFailClosed() throws Exception {
        Map<String, byte[]> unsafe = mainEntries(CAMEL_VERSION, "timer");
        unsafe.put("../outside", new byte[]{1});
        writeJar("org.apache.camel", "camel-catalog", CAMEL_VERSION, unsafe);

        IOException traversal = assertThrows(IOException.class, () -> service().snapshot(
                new CatalogTarget("main", CAMEL_VERSION, null, null)).evidenceFor(
                        List.of(subject(Kind.COMPONENT, "timer"))));
        assertTrue(traversal.getMessage().contains("unsafe entry name"), traversal.getMessage());

        Map<String, byte[]> duplicate = mainEntries(CAMEL_VERSION, "timer");
        duplicate.put("dup-one", new byte[]{1});
        duplicate.put("dup-two", new byte[]{2});
        byte[] duplicateArchive = replaceAscii(jarBytes(duplicate), "dup-two", "dup-one");
        Files.write(artifact("org.apache.camel", "camel-catalog", CAMEL_VERSION, "jar"), duplicateArchive);

        IOException repeated = assertThrows(IOException.class, () -> service().snapshot(
                new CatalogTarget("main", CAMEL_VERSION, null, null)).evidenceFor(
                        List.of(subject(Kind.COMPONENT, "timer"))));
        assertTrue(repeated.getMessage().contains("duplicate entries"), repeated.getMessage());
    }

    @Test
    void zipEntryQuotaAcceptsTheLimitAndRejectsOneOver() throws Exception {
        Map<String, byte[]> entries = mainEntries(CAMEL_VERSION, "timer");
        int base = entries.size();
        for (int index = base; index < 8_192; index++) {
            entries.put("filler/entry-" + index, new byte[0]);
        }
        writeJar("org.apache.camel", "camel-catalog", CAMEL_VERSION, entries);
        assertEquals(1, service().snapshot(
                new CatalogTarget("main", CAMEL_VERSION, null, null)).evidenceFor(
                        List.of(subject(Kind.COMPONENT, "timer")))
                .subjects().size());

        entries.put("filler/one-over", new byte[0]);
        writeJar("org.apache.camel", "camel-catalog", CAMEL_VERSION, entries);
        IOException excessive = assertThrows(IOException.class, () -> service().snapshot(
                new CatalogTarget("main", CAMEL_VERSION, null, null)).evidenceFor(
                        List.of(subject(Kind.COMPONENT, "timer"))));
        assertTrue(excessive.getMessage().contains("too many"), excessive.getMessage());
    }

    @Test
    void duplicateCatalogIndexNamesAreRejected() throws Exception {
        Map<String, byte[]> entries = mainEntries(CAMEL_VERSION, "timer");
        put(entries, "org/apache/camel/catalog/components.properties", "timer\ntimer\n");
        writeJar("org.apache.camel", "camel-catalog", CAMEL_VERSION, entries);

        IOException duplicate = assertThrows(IOException.class, () -> service().snapshot(
                new CatalogTarget("main", CAMEL_VERSION, null, null)).evidenceFor(
                        List.of(subject(Kind.COMPONENT, "timer"))));

        assertTrue(duplicate.getMessage().contains("duplicate name"), duplicate.getMessage());
    }

    @Test
    void unsafeCatalogIndexContentIsNotReflectedInTheTopLevelDiagnostic() throws Exception {
        String sensitive = "https://user:secret@example.invalid/catalog";
        Map<String, byte[]> entries = mainEntries(CAMEL_VERSION, "timer");
        put(entries, "org/apache/camel/catalog/components.properties", sensitive + '\n');
        writeJar("org.apache.camel", "camel-catalog", CAMEL_VERSION, entries);

        IOException error = assertThrows(IOException.class, () -> service().snapshot(
                new CatalogTarget("main", CAMEL_VERSION, null, null)).evidenceFor(
                        List.of(subject(Kind.COMPONENT, "timer"))));

        assertFalse(error.getMessage().contains(sensitive));
        assertTrue(error.getMessage().contains("unsafe name"));
    }

    @Test
    void catalogIndexesMustUseStrictUtf8() throws Exception {
        Map<String, byte[]> entries = mainEntries(CAMEL_VERSION, "timer");
        entries.put("org/apache/camel/catalog/components.properties",
                new byte[]{'#', (byte) 0xc3, 0x28, '\n', 't', 'i', 'm', 'e', 'r', '\n'});
        writeJar("org.apache.camel", "camel-catalog", CAMEL_VERSION, entries);

        IOException error = assertThrows(IOException.class, () -> service().snapshot(
                new CatalogTarget("main", CAMEL_VERSION, null, null)).evidenceFor(
                        List.of(subject(Kind.COMPONENT, "timer"))));

        assertTrue(error.getMessage().contains("strict UTF-8"));
    }

    @Test
    void perKindNameQuotaAcceptsTheLimitAndRejectsOneOver() throws Exception {
        Map<String, byte[]> entries = mainEntries(CAMEL_VERSION, "timer");
        String root = "org/apache/camel/catalog/";
        put(entries, root + "components.properties", catalogNames("component", 8_192));
        put(entries, root + "components/component0.json",
                identity("component", "component", "component0",
                        "org.apache.camel", "camel-test", CAMEL_VERSION, null));
        writeJar("org.apache.camel", "camel-catalog", CAMEL_VERSION, entries);
        CatalogTarget target = new CatalogTarget("main", CAMEL_VERSION, null, null);

        assertEquals(1, service().snapshot(target).evidenceFor(
                List.of(subject(Kind.COMPONENT, "component0"))).subjects().size());

        put(entries, root + "components.properties", catalogNames("component", 8_193));
        writeJar("org.apache.camel", "camel-catalog", CAMEL_VERSION, entries);
        IOException excessive = assertThrows(IOException.class, () -> service().snapshot(target).evidenceFor(
                List.of(subject(Kind.COMPONENT, "component0"))));
        assertTrue(excessive.getMessage().contains("too many names"));
    }

    @Test
    void aggregateSubjectQuotaAcceptsTheLimitAndRejectsOneOver() throws Exception {
        Map<String, byte[]> entries = mainEntries(CAMEL_VERSION, "timer");
        String components = "org/apache/camel/catalog/components.properties";
        put(entries, components, catalogNames("component", 8_189));
        put(entries, "org/apache/camel/catalog/models.properties", "split\n");
        writeJar("org.apache.camel", "camel-catalog", CAMEL_VERSION, entries);
        CatalogTarget target = new CatalogTarget("main", CAMEL_VERSION, null, null);

        assertEquals(8_192, service().snapshot(target).availableSubjects().size());

        put(entries, components, catalogNames("component", 8_190));
        writeJar("org.apache.camel", "camel-catalog", CAMEL_VERSION, entries);
        IOException excessive = assertThrows(IOException.class,
                () -> service().snapshot(target).availableSubjects());
        assertTrue(excessive.getMessage().contains("more than 8192 subjects"));
    }

    @Test
    void requestedOrderCannotChangeTheSnapshot() throws Exception {
        writeMainCatalog(CAMEL_VERSION, "timer");
        CatalogTarget target = new CatalogTarget("main", CAMEL_VERSION, null, null);
        CatalogSubject timer = subject(Kind.COMPONENT, "timer");
        CatalogSubject split = subject(Kind.EIP, "split");

        ShipCatalogService.Snapshot snapshot = service().snapshot(target);
        CatalogEvidenceSet first = snapshot.evidenceFor(List.of(timer, split));
        CatalogEvidenceSet second = snapshot.evidenceFor(List.of(split, timer));

        assertEquals(first, second);
    }

    @Test
    void allQueriesStayBoundToTheArtifactBytesCapturedByOneSnapshot() throws Exception {
        Map<String, byte[]> original = mainEntries(CAMEL_VERSION, "timer");
        put(original, "org/apache/camel/catalog/components/timer.json", componentIdentity(
                "timer", "org.apache.camel", "camel-timer", CAMEL_VERSION));
        writeJar("org.apache.camel", "camel-catalog", CAMEL_VERSION, original);
        CatalogTarget target = new CatalogTarget("main", CAMEL_VERSION, null, null);
        ShipCatalogService.Snapshot snapshot = service().snapshot(target);

        writeMainCatalog(CAMEL_VERSION, "different-name");

        assertTrue(snapshot.availableSubjects().contains(subject(Kind.COMPONENT, "timer")));
        assertEquals(1, snapshot.evidenceFor(
                List.of(subject(Kind.COMPONENT, "timer"))).subjects().size());
        assertEquals("timer:timerName", snapshot.componentModelsFor(
                List.of(subject(Kind.COMPONENT, "timer"))).get(0).syntax());
        assertThrows(IOException.class, () -> service().snapshot(target).evidenceFor(
                List.of(subject(Kind.COMPONENT, "timer"))));
    }

    @Test
    void subjectBoundsDoNotTrustCallerCollectionSize() throws Exception {
        writeMainCatalog(CAMEL_VERSION, "timer");
        ShipCatalogService.Snapshot snapshot = service().snapshot(
                new CatalogTarget("main", CAMEL_VERSION, null, null));
        AbstractCollection<CatalogSubject> lying = new AbstractCollection<>() {
            @Override
            public Iterator<CatalogSubject> iterator() {
                return new Iterator<>() {
                    private int index;

                    @Override
                    public boolean hasNext() {
                        return index <= 512;
                    }

                    @Override
                    public CatalogSubject next() {
                        return subject(Kind.COMPONENT, "c" + index++);
                    }
                };
            }

            @Override
            public int size() {
                throw new AssertionError("The catalog boundary must not trust Collection.size()");
            }
        };

        IOException excessive = assertThrows(IOException.class, () -> snapshot.evidenceFor(lying));

        assertTrue(excessive.getMessage().contains("between 1 and 512"));
    }

    @Test
    void onlineAcquisitionDelegatesOnlyExactCoordinatesToTheResolverBoundary() throws Exception {
        writeMainCatalog(CAMEL_VERSION, "timer");
        AtomicReference<List<MavenCoordinate>> requested = new AtomicReference<>();
        AtomicReference<ResolutionMode> mode = new AtomicReference<>();
        ShipCatalogService service = new ShipCatalogService(
                repository, ResolutionMode.ONLINE,
                (local, coordinates, resolutionMode) -> {
                    requested.set(coordinates);
                    mode.set(resolutionMode);
                    MavenCoordinate coordinate = coordinates.get(0);
                    Path path = artifact(coordinate.groupId(), coordinate.artifactId(),
                            coordinate.version(), coordinate.extension());
                    byte[] bytes = Files.readAllBytes(path);
                    return List.of(new ResolvedExactMavenArtifact(
                            coordinate, path, sha256(bytes), bytes.length));
                });

        service.snapshot(new CatalogTarget("main", CAMEL_VERSION, null, null)).evidenceFor(
                List.of(subject(Kind.COMPONENT, "timer")));

        assertEquals(ResolutionMode.ONLINE, mode.get());
        assertEquals(List.of(MavenCoordinate.jar("org.apache.camel", "camel-catalog", CAMEL_VERSION)),
                requested.get());
    }

    @Test
    void securelySnapshottedBytesMustMatchTheAcquiredCentralIdentity() throws Exception {
        writeMainCatalog(CAMEL_VERSION, "timer");
        MavenCoordinate coordinate = MavenCoordinate.jar("org.apache.camel", "camel-catalog", CAMEL_VERSION);
        Path path = artifact(coordinate.groupId(), coordinate.artifactId(),
                coordinate.version(), coordinate.extension());
        byte[] acquired = Files.readAllBytes(path);
        ShipCatalogService service = new ShipCatalogService(
                repository, ResolutionMode.ONLINE,
                (local, coordinates, mode) -> {
                    byte[] changed = acquired.clone();
                    changed[changed.length - 1] ^= 1;
                    Files.write(path, changed);
                    return List.of(new ResolvedExactMavenArtifact(
                            coordinate, path, sha256(acquired), acquired.length));
                });

        IOException mismatch = assertThrows(IOException.class, () -> service.snapshot(
                new CatalogTarget("main", CAMEL_VERSION, null, null)));

        assertTrue(mismatch.getMessage().contains("acquired content identity"));
    }

    @Test
    void availableSubjectsUsesRuntimeProviderForRuntimeSensitiveKinds() throws Exception {
        writeMainCatalog(CAMEL_VERSION, "timer");
        writeSpringProvider("4.18.0", CAMEL_VERSION);

        List<CatalogSubject> subjects = service().snapshot(
                new CatalogTarget("spring-boot", CAMEL_VERSION, "4.18.0", "3.5.0"))
                .availableSubjects();

        assertTrue(subjects.contains(subject(Kind.COMPONENT, "kafka")));
        assertTrue(subjects.contains(subject(Kind.EIP, "split")));
        assertFalse(subjects.contains(subject(Kind.COMPONENT, "timer")));
    }

    @Test
    void symbolicArtifactAncestorCannotEscapeThePrivateRepository() throws Exception {
        Path privateRepository = Files.createDirectory(repository.resolve("private"));
        Path outsideRepository = Files.createDirectory(repository.resolve("outside"));
        writeJar(outsideRepository, "org.apache.camel", "camel-catalog", CAMEL_VERSION,
                mainEntries(CAMEL_VERSION, "timer"));
        Files.createSymbolicLink(privateRepository.resolve("org"), outsideRepository.resolve("org"));

        IOException error = assertThrows(IOException.class, () -> offlineService(privateRepository).snapshot(
                new CatalogTarget("main", CAMEL_VERSION, null, null)));

        assertTrue(error.getMessage().contains("offline mode"));
        assertTrue(causeChainContains(error, "symbolic link"));
    }

    @Test
    void hardLinkedCatalogArtifactIsRejected() throws Exception {
        writeMainCatalog(CAMEL_VERSION, "timer");
        Files.createLink(
                repository.resolve("artifact-hard-link"),
                artifact("org.apache.camel", "camel-catalog", CAMEL_VERSION, "jar"));

        IOException error = assertThrows(IOException.class, () -> service().snapshot(
                new CatalogTarget("main", CAMEL_VERSION, null, null)));

        assertTrue(causeChainContains(error, "hard-linked"));
    }

    private ShipCatalogService service() {
        return offlineService(repository);
    }

    private static ShipCatalogService offlineService(Path path) {
        return new ShipCatalogService(path, ResolutionMode.OFFLINE, ShipMavenResolver::resolveArtifacts);
    }

    private static boolean causeChainContains(Throwable error, String fragment) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current.getMessage() != null && current.getMessage().contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    private static CatalogSubject subject(Kind kind, String name) {
        return new CatalogSubject(kind, name);
    }

    private void writeMainCatalog(String version, String timerIdentity) throws IOException {
        writeJar("org.apache.camel", "camel-catalog", version, mainEntries(version, timerIdentity));
    }

    private static Map<String, byte[]> mainEntries(String version, String timerIdentity) {
        String root = "org/apache/camel/catalog/";
        Map<String, byte[]> entries = new LinkedHashMap<>();
        put(entries, "META-INF/version.properties", "version=" + version + "\n");
        put(entries, root + "components.properties", "timer\n");
        put(entries, root + "models.properties", "route\nfrom\nto\nsetBody\nsplit\nmarshal\nunmarshal\n");
        put(entries, root + "dataformats.properties", "csv\n");
        put(entries, root + "languages.properties", "simple\n");
        put(entries, root + "components/timer.json",
                identity("component", "component", timerIdentity,
                        "org.apache.camel", "camel-timer", version, null));
        put(entries, root + "models/split.json",
                identity("model", "model", "split", null, null, null, null));
        put(entries, root + "dataformats/csv.json",
                identity("dataformat", "dataformat", "csv",
                        "org.apache.camel", "camel-csv", version, null));
        put(entries, root + "languages/simple.json",
                identity("language", "language", "simple",
                        "org.apache.camel", "camel-core-languages", version, null));
        return entries;
    }

    private void writeSpringProvider(String providerVersion, String camelVersion) throws IOException {
        writeSpringProvider(providerVersion, camelVersion, "3.5.0");
    }

    private void writeSpringProvider(
            String providerVersion, String camelVersion, String springBootVersion)
            throws IOException {
        String root = "org/apache/camel/springboot/catalog/";
        Map<String, byte[]> entries = new LinkedHashMap<>();
        put(entries, root + "components.properties", "kafka\n");
        put(entries, root + "dataformats.properties", "csv\n");
        put(entries, root + "languages.properties", "simple\n");
        put(entries, root + "components/kafka.json",
                identity("component", "component", "kafka", "org.apache.camel.springboot",
                        "camel-kafka-starter", providerVersion, null));
        put(entries, root + "dataformats/csv.json",
                identity("dataformat", "dataformat", "csv", "org.apache.camel.springboot",
                        "camel-csv-starter", providerVersion, null));
        put(entries, root + "languages/simple.json",
                identity("language", "language", "simple", "org.apache.camel.springboot",
                        "camel-core-languages-starter", providerVersion, null));
        writeJar("org.apache.camel.springboot", "camel-catalog-provider-springboot", providerVersion, entries);
        writePom("org.apache.camel.springboot", "camel-catalog-provider-springboot", providerVersion, """
                <repositories>
                  <repository><id>poison</id><url>https://repo.example.invalid/maven2</url></repository>
                </repositories>
                <dependencies>
                  <dependency>
                    <groupId>org.apache.camel</groupId>
                    <artifactId>camel-catalog</artifactId>
                    <version>${camel-version}</version>
                  </dependency>
                </dependencies>
                """);
        writePom("org.apache.camel.springboot", "spring-boot", providerVersion,
                String.format(Locale.ROOT, """
                        <parent>
                          <groupId>org.apache.camel</groupId>
                          <artifactId>camel-dependencies</artifactId>
                          <version>%s</version>
                        </parent>
                        <properties><camel-version>%s</camel-version></properties>
                        """, camelVersion, camelVersion));
        String bootProperty = springBootVersion == null
                ? ""
                : "<spring-boot-version>" + springBootVersion + "</spring-boot-version>";
        writePom("org.apache.camel", "camel-dependencies", camelVersion,
                "<properties>" + bootProperty + "</properties>");
    }

    private void writeQuarkusBom(String platformVersion, String catalogVersion, String camelVersion)
            throws IOException {
        writePom("io.quarkus.platform", "quarkus-camel-bom", platformVersion,
                quarkusBomBody(catalogVersion, camelVersion));
    }

    private static String quarkusBomBody(String catalogVersion, String camelVersion) {
        return String.format(Locale.ROOT, """
                <dependencyManagement>
                  <dependencies>
                    <dependency>
                      <groupId>org.apache.camel.quarkus</groupId>
                      <artifactId>camel-quarkus-catalog</artifactId>
                      <version>%s</version>
                    </dependency>
                    <dependency>
                      <groupId>org.apache.camel</groupId>
                      <artifactId>camel-catalog</artifactId>
                      <version>%s</version>
                    </dependency>
                  </dependencies>
                </dependencyManagement>
                """, catalogVersion, camelVersion);
    }

    private static String minifiedQuarkusBomBody(String catalogVersion, String camelVersion) {
        return "<dependencyManagement><dependencies>"
               + "<dependency><groupId>org.apache.camel.quarkus</groupId>"
               + "<artifactId>camel-quarkus-catalog</artifactId><version>" + catalogVersion
               + "</version></dependency>"
               + "<dependency><groupId>org.apache.camel</groupId>"
               + "<artifactId>camel-catalog</artifactId><version>" + camelVersion
               + "</version></dependency>"
               + "</dependencies></dependencyManagement>";
    }

    private static String namespaceDeclarations(int count) {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < count; index++) {
            result.append(" xmlns:n").append(index).append("=\"urn:n").append(index).append("\"");
        }
        return result.toString();
    }

    private static String attributeDeclarations(int count) {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < count; index++) {
            result.append(" a").append(index).append("=\"v\"");
        }
        return result.toString();
    }

    private static String catalogNames(String prefix, int count) {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < count; index++) {
            result.append(prefix).append(index).append('\n');
        }
        return result.toString();
    }

    private static String nestedPomElements(int depth) {
        return "<nested>".repeat(depth) + "</nested>".repeat(depth);
    }

    private void writeQuarkusProvider(String catalogVersion, String camelVersion) throws IOException {
        String root = "org/apache/camel/catalog/quarkus/";
        String metadata = "\"metadata\":{\"camelVersion\":\"" + camelVersion + "\"}";
        Map<String, byte[]> entries = new LinkedHashMap<>();
        put(entries, root + "components.properties", "kafka\n");
        put(entries, root + "dataformats.properties", "csv\n");
        put(entries, root + "languages.properties", "simple\n");
        put(entries, root + "components/kafka.json",
                identity("component", "component", "kafka", "org.apache.camel.quarkus",
                        "camel-quarkus-kafka", catalogVersion, metadata));
        put(entries, root + "dataformats/csv.json",
                identity("dataformat", "dataformat", "csv", "org.apache.camel.quarkus",
                        "camel-quarkus-csv", catalogVersion, metadata));
        put(entries, root + "languages/simple.json",
                identity("language", "language", "simple", "org.apache.camel.quarkus",
                        "camel-quarkus-core", catalogVersion, metadata));
        writeJar("org.apache.camel.quarkus", "camel-quarkus-catalog", catalogVersion, entries);
    }

    private void writeJar(String groupId, String artifactId, String version, Map<String, byte[]> entries)
            throws IOException {
        writeJar(repository, groupId, artifactId, version, entries);
    }

    private static void writeJar(
            Path repository,
            String groupId,
            String artifactId,
            String version,
            Map<String, byte[]> entries)
            throws IOException {
        Path jar = artifact(repository, groupId, artifactId, version, "jar");
        Files.createDirectories(jar.getParent());
        Files.write(jar, jarBytes(entries));
    }

    private static byte[] jarBytes(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }

    private static byte[] replaceAscii(byte[] bytes, String from, String to) {
        byte[] source = from.getBytes(StandardCharsets.US_ASCII);
        byte[] replacement = to.getBytes(StandardCharsets.US_ASCII);
        if (source.length != replacement.length) {
            throw new IllegalArgumentException("ZIP entry replacement must preserve byte length");
        }
        byte[] result = bytes.clone();
        int replacements = 0;
        for (int offset = 0; offset <= result.length - source.length; offset++) {
            boolean match = true;
            for (int index = 0; index < source.length; index++) {
                if (result[offset + index] != source[index]) {
                    match = false;
                    break;
                }
            }
            if (match) {
                System.arraycopy(replacement, 0, result, offset, replacement.length);
                replacements++;
                offset += source.length - 1;
            }
        }
        assertEquals(2, replacements, "Expected one local and one central ZIP entry name");
        return result;
    }

    private void writePom(String groupId, String artifactId, String version, String body) throws IOException {
        Path pom = artifact(groupId, artifactId, version, "pom");
        Files.createDirectories(pom.getParent());
        Files.writeString(pom, String.format(Locale.ROOT, """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>%s</groupId>
                  <artifactId>%s</artifactId>
                  <version>%s</version>
                  %s
                </project>
                """, groupId, artifactId, version, body));
    }

    private void writeRawPom(
            String groupId, String artifactId, String version, String rootAttributes, String body)
            throws IOException {
        writeRawPom(groupId, artifactId, version, rootAttributes, body,
                "http://maven.apache.org/POM/4.0.0");
    }

    private void writeRawPom(
            String groupId,
            String artifactId,
            String version,
            String rootAttributes,
            String body,
            String namespace)
            throws IOException {
        Path pom = artifact(groupId, artifactId, version, "pom");
        Files.createDirectories(pom.getParent());
        Files.writeString(pom,
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                               + "<project xmlns=\"" + namespace + "\"" + rootAttributes + ">"
                               + "<modelVersion>4.0.0</modelVersion>"
                               + "<groupId>" + groupId + "</groupId>"
                               + "<artifactId>" + artifactId + "</artifactId>"
                               + "<version>" + version + "</version>"
                               + body + "</project>");
    }

    private Path artifact(String groupId, String artifactId, String version, String extension) {
        return artifact(repository, groupId, artifactId, version, extension);
    }

    private static Path artifact(
            Path repository, String groupId, String artifactId, String version, String extension) {
        return repository.resolve(groupId.replace('.', '/')).resolve(artifactId).resolve(version)
                .resolve(artifactId + '-' + version + '.' + extension);
    }

    private static void put(Map<String, byte[]> entries, String name, String value) {
        entries.put(name, value.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(byte[] bytes) {
        try {
            return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String identity(
            String root, String kind, String name, String groupId, String artifactId, String version, String extra) {
        StringBuilder json = new StringBuilder("{\"").append(root).append("\":{")
                .append("\"kind\":\"").append(kind).append("\",")
                .append("\"name\":\"").append(name).append("\",")
                .append("\"deprecated\":false");
        if (groupId != null) {
            json.append(",\"groupId\":\"").append(groupId).append("\"")
                    .append(",\"artifactId\":\"").append(artifactId).append("\"")
                    .append(",\"version\":\"").append(version).append("\"");
        }
        if (extra != null) {
            json.append(',').append(extra);
        }
        return json.append("}}\n").toString();
    }

    private static String componentIdentity(
            String name, String groupId, String artifactId, String version) {
        return String.format(Locale.ROOT, """
                {
                  "component": {
                    "kind": "component", "name": "%s", "deprecated": false,
                    "groupId": "%s", "artifactId": "%s", "version": "%s",
                    "syntax": "%s:timerName", "lenientProperties": false
                  },
                  "componentProperties": {},
                  "properties": {
                    "timerName": {
                      "index": 0, "kind": "path", "required": true,
                      "type": "string", "javaType": "java.lang.String"
                    },
                    "delay": {
                      "index": 1, "kind": "parameter", "required": false,
                      "type": "integer", "javaType": "long"
                    },
                    "extra": {
                      "index": 2, "kind": "parameter", "required": false,
                      "type": "object", "javaType": "java.util.Map",
                      "multiValue": true, "prefix": "extra."
                    },
                    "exceptionHandler": {
                      "index": 3, "kind": "parameter", "required": false,
                      "type": "object", "javaType": "java.lang.Object",
                      "optionalPrefix": "consumer."
                    }
                  }
                }
                """, name, groupId, artifactId, version, name);
    }

    private void writeMainCatalogWithEnumValues(int count) throws IOException {
        Map<String, byte[]> entries = mainEntries(CAMEL_VERSION, "timer");
        String enumValues = IntStream.range(0, count)
                .mapToObj(index -> "\"value" + index + "\"")
                .collect(java.util.stream.Collectors.joining(","));
        String component = componentIdentity("timer", "org.apache.camel", "camel-timer", CAMEL_VERSION)
                .replace("\"type\": \"integer\", \"javaType\": \"long\"",
                        "\"type\": \"integer\", \"javaType\": \"long\", \"enum\": ["
                                                                          + enumValues + "]");
        put(entries, "org/apache/camel/catalog/components/timer.json", component);
        writeJar("org.apache.camel", "camel-catalog", CAMEL_VERSION, entries);
    }
}
