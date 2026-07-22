package io.github.luigidemasi.camelkit.ship.controller;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import io.github.luigidemasi.camelkit.ship.ShipDigest;
import io.github.luigidemasi.camelkit.ship.artifact.ArtifactManifest;
import io.github.luigidemasi.camelkit.ship.artifact.ArtifactManifest.DeclaredArtifact;
import io.github.luigidemasi.camelkit.ship.artifact.ArtifactManifest.RouteArtifact;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogComponentModel;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogEvidenceSet;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogSubject;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogTarget;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogTestVerifier;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogUsageRecord;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogUsageRecord.EndpointUsage;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogUsageRecord.RouteUsage;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogUsageRecord.RuntimeDependency;
import io.github.luigidemasi.camelkit.ship.catalog.ShipCatalogService.Snapshot;
import io.github.luigidemasi.camelkit.ship.controller.CatalogEvidenceValidator.UsageBinding;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CatalogEvidenceValidatorTest {

    private static final String RUN_ID = "ship-" + "1".repeat(32);
    private static final String CATALOG_DIGEST = digest('a');
    private static final String MANIFEST_DIGEST = digest('b');
    private static final String CANDIDATE_DIGEST = digest('c');
    private static final String CONTENT_DIGEST = digest('d');
    private static final String POM_DIGEST = digest('e');
    private static final UsageBinding BINDING = new UsageBinding(
            RUN_ID, CATALOG_DIGEST, MANIFEST_DIGEST, CANDIDATE_DIGEST, CONTENT_DIGEST, POM_DIGEST);

    @TempDir
    Path temporaryDirectory;

    @Test
    void resolvesOnlyTheExactCanonicalControllerRequest() throws Exception {
        Snapshot snapshot = CatalogTestVerifier.mainSnapshot(temporaryDirectory);
        List<CatalogSubject> requested = usedSubjects();

        CatalogEvidenceSet evidence = CatalogEvidenceValidator.resolveEvidence(
                snapshot, CatalogTestVerifier.TARGET, requested);

        assertEquals(requested, evidence.subjects().stream().map(value -> value.subject()).toList());
        ShipControllerException duplicate = assertThrows(ShipControllerException.class,
                () -> CatalogEvidenceValidator.resolveEvidence(
                        snapshot, CatalogTestVerifier.TARGET,
                        List.of(requested.get(0), requested.get(0))));
        assertEquals("catalog-evidence-subject-invalid", duplicate.code());
        ShipControllerException target = assertThrows(ShipControllerException.class,
                () -> CatalogEvidenceValidator.resolveEvidence(
                        snapshot, new CatalogTarget("main", "4.20.0", null, null), requested));
        assertEquals("catalog-evidence-target-mismatch", target.code());
    }

    @Test
    void acceptsUsageReplayedEntirelyFromOneFrozenSnapshot() throws Exception {
        Snapshot snapshot = CatalogTestVerifier.mainSnapshot(temporaryDirectory);
        CatalogEvidenceSet approved = CatalogEvidenceValidator.resolveEvidence(
                snapshot, CatalogTestVerifier.TARGET, usedSubjects());
        CatalogUsageRecord usage = usage(snapshot, approved);

        assertDoesNotThrow(() -> CatalogEvidenceValidator.validateUsage(
                snapshot, BINDING, approved, manifest("routes/a.camel.yaml"), usage));
    }

    @Test
    void rejectsUsageOutsideApprovedEvidenceAndCallerForgedComponentModels() throws Exception {
        Snapshot snapshot = CatalogTestVerifier.mainSnapshot(temporaryDirectory);
        CatalogEvidenceSet approved = CatalogEvidenceValidator.resolveEvidence(
                snapshot, CatalogTestVerifier.TARGET, List.of(component("direct"), language("simple")));
        CatalogEvidenceSet usedEvidence = CatalogEvidenceValidator.resolveEvidence(
                snapshot, CatalogTestVerifier.TARGET, usedSubjects());
        CatalogUsageRecord validShape = usage(snapshot, usedEvidence);

        ShipControllerException outsideApproval = assertThrows(ShipControllerException.class,
                () -> CatalogEvidenceValidator.validateUsage(
                        snapshot, BINDING, approved, manifest("routes/a.camel.yaml"), validShape));
        assertEquals("catalog-usage-evidence-mismatch", outsideApproval.code());

        CatalogComponentModel exact = validShape.componentModels().get(0);
        CatalogComponentModel forged = new CatalogComponentModel(
                exact.evidence(), exact.syntax(), !exact.lenientProperties(), exact.options());
        CatalogUsageRecord forgedUsage = copy(validShape, validShape.evidence(), List.of(forged),
                validShape.runtimeDependencies(), validShape.inventoryDigest());
        ShipControllerException forgedModel = assertThrows(ShipControllerException.class,
                () -> CatalogEvidenceValidator.validateUsage(
                        snapshot, BINDING, usedEvidence, manifest("routes/a.camel.yaml"), forgedUsage));
        assertEquals("catalog-component-metadata-mismatch", forgedModel.code());
    }

    @Test
    void rejectsInventoryAndRuntimeDependencyClosureDrift() throws Exception {
        Snapshot snapshot = CatalogTestVerifier.mainSnapshot(temporaryDirectory);
        CatalogEvidenceSet approved = CatalogEvidenceValidator.resolveEvidence(
                snapshot, CatalogTestVerifier.TARGET, usedSubjects());
        CatalogUsageRecord valid = usage(snapshot, approved);
        CatalogUsageRecord wrongInventory = copy(
                valid, valid.evidence(), valid.componentModels(), valid.runtimeDependencies(), digest('f'));

        ShipControllerException inventory = assertThrows(ShipControllerException.class,
                () -> CatalogEvidenceValidator.validateUsage(
                        snapshot, BINDING, approved, manifest("routes/a.camel.yaml"), wrongInventory));
        assertEquals("catalog-inventory-mismatch", inventory.code());

        List<RuntimeDependency> incomplete = valid.runtimeDependencies().stream()
                .filter(value -> !"camel-core-languages".equals(value.artifactId()))
                .toList();
        CatalogUsageRecord wrongDependencies = copy(
                valid, valid.evidence(), valid.componentModels(), incomplete, valid.inventoryDigest());
        ShipControllerException dependencies = assertThrows(ShipControllerException.class,
                () -> CatalogEvidenceValidator.validateUsage(
                        snapshot, BINDING, approved, manifest("routes/a.camel.yaml"), wrongDependencies));
        assertEquals("catalog-runtime-dependencies-invalid", dependencies.code());
    }

    @Test
    void rejectsCatalogRoutesThatDifferFromTheExactArtifactManifest() throws Exception {
        Snapshot snapshot = CatalogTestVerifier.mainSnapshot(temporaryDirectory);
        CatalogEvidenceSet approved = CatalogEvidenceValidator.resolveEvidence(
                snapshot, CatalogTestVerifier.TARGET, usedSubjects());
        CatalogUsageRecord usage = usage(snapshot, approved);

        ShipControllerException mismatch = assertThrows(ShipControllerException.class,
                () -> CatalogEvidenceValidator.validateUsage(
                        snapshot, BINDING, approved, manifest("routes/other.camel.yaml"), usage));

        assertEquals("catalog-usage-manifest-mismatch", mismatch.code());
    }

    private static CatalogUsageRecord usage(Snapshot snapshot, CatalogEvidenceSet evidence) throws IOException {
        List<CatalogSubject> inventory = snapshot.availableSubjects().stream().sorted().toList();
        String inventoryDigest = ShipDigest.sha256(ShipJson.mapper().writeValueAsBytes(inventory));
        List<CatalogComponentModel> models = snapshot.componentModelsFor(List.of(component("direct")));
        RouteUsage route = new RouteUsage(
                "route-a",
                "routes/a.camel.yaml",
                digest('1'),
                usedSubjects(),
                List.of(new EndpointUsage(component("direct"), 1, List.of(), List.of("timeout"))));
        List<RuntimeDependency> dependencies = new ArrayList<>(
                List.of(
                        dependency("camel-core-languages"),
                        dependency("camel-direct"),
                        dependency("camel-main"),
                        dependency("camel-yaml-dsl")));
        dependencies.sort(java.util.Comparator.comparing(RuntimeDependency::coordinate)
                .thenComparing(RuntimeDependency::scope));
        return new CatalogUsageRecord(
                CatalogUsageRecord.SCHEMA_VERSION,
                RUN_ID,
                CATALOG_DIGEST,
                MANIFEST_DIGEST,
                CANDIDATE_DIGEST,
                CONTENT_DIGEST,
                POM_DIGEST,
                inventoryDigest,
                List.of(route),
                models,
                dependencies,
                evidence);
    }

    private static CatalogUsageRecord copy(
            CatalogUsageRecord source,
            CatalogEvidenceSet evidence,
            List<CatalogComponentModel> models,
            List<RuntimeDependency> dependencies,
            String inventoryDigest) {
        return new CatalogUsageRecord(
                source.schemaVersion(), source.runId(), source.catalogEvidenceDigest(),
                source.artifactManifestDigest(), source.candidateSnapshotDigest(),
                source.candidateContentDigest(), source.pomDigest(), inventoryDigest,
                source.routes(), models, dependencies, evidence);
    }

    private static List<CatalogSubject> usedSubjects() {
        return List.of(component("direct"), eip("to"), language("simple"));
    }

    private static RuntimeDependency dependency(String artifactId) {
        return new RuntimeDependency("org.apache.camel", artifactId, CatalogTestVerifier.CAMEL_VERSION, "compile");
    }

    private static ArtifactManifest manifest(String routePath) {
        return new ArtifactManifest(
                ArtifactManifest.SCHEMA_VERSION,
                "main",
                CatalogTestVerifier.CAMEL_VERSION,
                null,
                null,
                "camel-yaml",
                "simple",
                "4.8.1",
                List.of("org.citrusframework:citrus-core:4.8.1"),
                ArtifactManifest.JavaPolicy.FORBIDDEN,
                List.of(),
                List.of(new RouteArtifact("route-a", routePath, digest('1'))),
                List.of(),
                List.of(new DeclaredArtifact("pom", "pom.xml", POM_DIGEST, true)),
                true,
                true);
    }

    private static CatalogSubject component(String name) {
        return new CatalogSubject(CatalogSubject.Kind.COMPONENT, name);
    }

    private static CatalogSubject eip(String name) {
        return new CatalogSubject(CatalogSubject.Kind.EIP, name);
    }

    private static CatalogSubject language(String name) {
        return new CatalogSubject(CatalogSubject.Kind.LANGUAGE, name);
    }

    private static String digest(char value) {
        return "sha256:" + Character.toString(value).repeat(64);
    }
}
