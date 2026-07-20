package io.github.luigidemasi.camelkit.ship.catalog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

import io.github.luigidemasi.camelkit.ship.catalog.CatalogEvidenceSet.ArtifactEvidence;
import io.github.luigidemasi.camelkit.ship.catalog.CatalogEvidenceSet.SubjectEvidence;
import io.github.luigidemasi.camelkit.ship.resolver.MavenCoordinate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CatalogEvidenceSetTest {

    private static final String ARTIFACT_DIGEST = "sha256:" + "a".repeat(64);
    private static final String RESOURCE_DIGEST = "sha256:" + "b".repeat(64);
    private static final MavenCoordinate MAIN
            = MavenCoordinate.jar("org.apache.camel", "camel-catalog", "4.21.0");
    private static final CatalogTarget TARGET = new CatalogTarget("main", "4.21.0", null, null);

    @Test
    void factoryCanonicalizesInputAndProducesAStableDigest() {
        SubjectEvidence timer = component("timer", RESOURCE_DIGEST);
        SubjectEvidence direct = component("direct", "sha256:" + "c".repeat(64));

        CatalogEvidenceSet first = CatalogEvidenceSet.create(
                TARGET, MAIN, List.of(artifact()), List.of(timer, direct));
        CatalogEvidenceSet second = CatalogEvidenceSet.create(
                TARGET, MAIN, List.of(artifact()), List.of(direct, timer));

        assertEquals(List.of("direct", "timer"), first.subjects().stream()
                .map(value -> value.subject().name()).toList());
        assertEquals(first, second);
        assertEquals("sha256:2e7a339aab7d855b21783f6a1c008d8c705f749475860136158502f70299fdaf",
                first.digest());
    }

    @Test
    void constructorRejectsDigestAndCanonicalOrderForgery() {
        CatalogEvidenceSet snapshot = snapshot();
        SubjectEvidence direct = component("direct", "sha256:" + "c".repeat(64));
        List<SubjectEvidence> reversed = List.of(snapshot.subjects().get(0), direct).stream()
                .sorted(Collections.reverseOrder(java.util.Comparator.comparing(SubjectEvidence::subject)))
                .toList();

        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> new CatalogEvidenceSet(
                        snapshot.schemaVersion(), snapshot.target(), snapshot.platformCoordinate(),
                        snapshot.artifacts(), snapshot.subjects(), "sha256:" + "0".repeat(64))),
                () -> assertThrows(IllegalArgumentException.class, () -> new CatalogEvidenceSet(
                        snapshot.schemaVersion(), snapshot.target(), snapshot.platformCoordinate(),
                        snapshot.artifacts(), reversed, snapshot.digest())));
    }

    @Test
    void snapshotIsDeeplyImmutableAndDigestChangesWithExactResourceBytes() {
        List<ArtifactEvidence> artifacts = new ArrayList<>(List.of(artifact()));
        List<SubjectEvidence> subjects = new ArrayList<>(List.of(component("timer", RESOURCE_DIGEST)));
        CatalogEvidenceSet snapshot = CatalogEvidenceSet.create(TARGET, MAIN, artifacts, subjects);
        artifacts.clear();
        subjects.clear();

        CatalogEvidenceSet changed = CatalogEvidenceSet.create(
                TARGET, MAIN, List.of(artifact()),
                List.of(component("timer", "sha256:" + "c".repeat(64))));

        assertEquals(1, snapshot.artifacts().size());
        assertEquals(1, snapshot.subjects().size());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.subjects().clear());
        assertNotEquals(snapshot.digest(), changed.digest());
    }

    @Test
    void subjectEvidenceMustBindToAnIncludedArtifactAndCanonicalResource() {
        MavenCoordinate other = MavenCoordinate.jar("org.apache.camel", "camel-catalog", "4.20.0");
        SubjectEvidence wrongAuthority = new SubjectEvidence(
                new CatalogSubject(CatalogSubject.Kind.COMPONENT, "timer"), other, ARTIFACT_DIGEST,
                "org/apache/camel/catalog/components/timer.json", RESOURCE_DIGEST,
                "org.apache.camel", "camel-timer", "4.20.0", false);
        SubjectEvidence wrongResource = new SubjectEvidence(
                new CatalogSubject(CatalogSubject.Kind.COMPONENT, "timer"), MAIN, ARTIFACT_DIGEST,
                "org/apache/camel/catalog/components/direct.json", RESOURCE_DIGEST,
                "org.apache.camel", "camel-timer", "4.21.0", false);

        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> CatalogEvidenceSet.create(TARGET, MAIN, List.of(artifact()),
                                List.of(wrongAuthority))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> CatalogEvidenceSet.create(TARGET, MAIN, List.of(artifact()),
                                List.of(wrongResource))));
    }

    @Test
    void eipIdentityIsAbsentWhileOtherKindsRequireTheCompleteIdentity() {
        SubjectEvidence eip = new SubjectEvidence(
                new CatalogSubject(CatalogSubject.Kind.EIP, "split"), MAIN, ARTIFACT_DIGEST,
                "org/apache/camel/catalog/models/split.json", RESOURCE_DIGEST,
                null, null, null, false);

        assertDoesNotThrow(() -> CatalogEvidenceSet.create(
                TARGET, MAIN, List.of(artifact()), List.of(eip)));
        assertThrows(IllegalArgumentException.class, () -> new SubjectEvidence(
                new CatalogSubject(CatalogSubject.Kind.EIP, "split"), MAIN, ARTIFACT_DIGEST,
                "org/apache/camel/catalog/models/split.json", RESOURCE_DIGEST,
                "org.apache.camel", null, null, false));
        assertThrows(IllegalArgumentException.class, () -> new SubjectEvidence(
                new CatalogSubject(CatalogSubject.Kind.COMPONENT, "timer"), MAIN, ARTIFACT_DIGEST,
                "org/apache/camel/catalog/components/timer.json", RESOURCE_DIGEST,
                null, null, null, false));
    }

    @Test
    void subjectQuotaAcceptsTheLimitAndRejectsOneOver() {
        List<SubjectEvidence> atLimit = IntStream.range(0, CatalogEvidenceSet.MAX_SUBJECTS)
                .mapToObj(index -> component("c" + index, digest(index)))
                .toList();

        assertEquals(CatalogEvidenceSet.MAX_SUBJECTS,
                CatalogEvidenceSet.create(TARGET, MAIN, List.of(artifact()), atLimit).subjects().size());
        List<SubjectEvidence> excessive = new ArrayList<>(atLimit);
        excessive.add(component("extra", "sha256:" + "f".repeat(64)));
        assertThrows(IllegalArgumentException.class,
                () -> CatalogEvidenceSet.create(TARGET, MAIN, List.of(artifact()), excessive));
    }

    @Test
    void artifactSizeAcceptsTheLimitAndRejectsOneOver() {
        assertDoesNotThrow(() -> new ArtifactEvidence(MAIN, ARTIFACT_DIGEST, 128L * 1024 * 1024));
        assertThrows(IllegalArgumentException.class,
                () -> new ArtifactEvidence(MAIN, ARTIFACT_DIGEST, 128L * 1024 * 1024 + 1));
    }

    private static CatalogEvidenceSet snapshot() {
        return CatalogEvidenceSet.create(
                TARGET, MAIN, List.of(artifact()), List.of(component("timer", RESOURCE_DIGEST)));
    }

    private static ArtifactEvidence artifact() {
        return new ArtifactEvidence(MAIN, ARTIFACT_DIGEST, 42);
    }

    private static SubjectEvidence component(String name, String digest) {
        return new SubjectEvidence(
                new CatalogSubject(CatalogSubject.Kind.COMPONENT, name), MAIN, ARTIFACT_DIGEST,
                "org/apache/camel/catalog/components/" + name + ".json", digest,
                "org.apache.camel", "camel-" + name, "4.21.0", false);
    }

    private static String digest(int value) {
        return "sha256:" + String.format(Locale.ROOT, "%064x", value + 1L);
    }
}
