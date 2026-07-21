package io.github.luigidemasi.camelkit.ship.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.List;

import io.github.luigidemasi.camelkit.ship.controller.ShipBlobStore.BlobReference;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.ConsentDecision;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.DesignChallenge;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.DesignDecision;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.DesignResponse;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.DiscoveryAnswer;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.DiscoveryChallenge;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.DocumentConsentChallenge;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.DocumentConsentResponse;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.PlanChallenge;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.PlanDecision;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.PlanResponse;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.RemoteProviderConsentChallenge;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.RemoteProviderConsentResponse;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.WaiverChallenge;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.WaiverDecision;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.WaiverResponse;
import io.github.luigidemasi.camelkit.ship.protocol.ShipStage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

@EnabledOnOs(OS.LINUX)
class ShipInteractionBundleServiceTest {

    private static final String RUN_ID = "ship-" + "1".repeat(32);
    private static final String OTHER_RUN_ID = "ship-" + "a".repeat(32);
    private static final String DIGEST = "sha256:" + "2".repeat(64);
    private static final String OTHER_DIGEST = "sha256:" + "3".repeat(64);
    private static final String NONCE = "a".repeat(43);
    private static final String SYNTACTIC_MAC = "hmac-sha256:" + "4".repeat(64);

    @TempDir
    Path temporaryDirectory;

    @Test
    void persistsAllSixClosedInteractionFamiliesInOrder() throws Exception {
        Fixture fixture = fixture("all-families");
        BlobReference current = fixture.service().create(fixture.blobs(), RUN_ID, DIGEST);

        current = fixture.service().record(fixture.blobs(), current, discoveryChallenge(fixture.signer()));
        BlobReference pendingDiscovery = current;
        assertThrows(IllegalStateException.class, () -> fixture.service().record(
                fixture.blobs(), pendingDiscovery, designChallenge(fixture.signer())));
        current = fixture.service().record(fixture.blobs(), current, discoveryAnswer(fixture.signer()));
        current = fixture.service().record(fixture.blobs(), current, documentChallenge(fixture.signer()));
        current = fixture.service().record(fixture.blobs(), current, documentResponse(fixture.signer()));
        current = fixture.service().record(fixture.blobs(), current, remoteChallenge(fixture.signer()));
        current = fixture.service().record(fixture.blobs(), current, remoteResponse(fixture.signer()));
        current = fixture.service().record(fixture.blobs(), current, designChallenge(fixture.signer()));
        current = fixture.service().record(fixture.blobs(), current, designResponse(fixture.signer(), DIGEST));
        current = fixture.service().record(fixture.blobs(), current, planChallenge(fixture.signer()));
        current = fixture.service().record(fixture.blobs(), current, planResponse(fixture.signer()));
        current = fixture.service().record(fixture.blobs(), current, waiverChallenge(fixture.signer()));
        current = fixture.service().record(fixture.blobs(), current, waiverResponse(fixture.signer()));

        ShipInteractionBundle restored = fixture.service().read(fixture.blobs(), current);
        assertEquals(6, restored.exchanges().size());
        assertNotNull(restored.exchanges().get(0).discovery());
        assertNotNull(restored.exchanges().get(1).documentConsent());
        assertNotNull(restored.exchanges().get(2).remoteProviderConsent());
        assertNotNull(restored.exchanges().get(3).design());
        assertNotNull(restored.exchanges().get(4).plan());
        assertNotNull(restored.exchanges().get(5).waiver());
        assertTrue(restored.exchanges().stream().noneMatch(ShipInteractionBundle.Exchange::pending));
    }

    @Test
    void rejectsChangedChallengeFieldsWithTheOldMacForAllSixFamilies() throws Exception {
        Fixture fixture = fixture("challenge-macs");
        BlobReference empty = fixture.service().create(fixture.blobs(), RUN_ID, DIGEST);

        assertThrows(IOException.class, () -> fixture.service().record(
                fixture.blobs(), empty, tamper(discoveryChallenge(fixture.signer()))));
        assertThrows(IOException.class, () -> fixture.service().record(
                fixture.blobs(), empty, tamper(documentChallenge(fixture.signer()))));
        assertThrows(IOException.class, () -> fixture.service().record(
                fixture.blobs(), empty, tamper(remoteChallenge(fixture.signer()))));
        assertThrows(IOException.class, () -> fixture.service().record(
                fixture.blobs(), empty, tamper(designChallenge(fixture.signer()))));
        assertThrows(IOException.class, () -> fixture.service().record(
                fixture.blobs(), empty, tamper(planChallenge(fixture.signer()))));
        assertThrows(IOException.class, () -> fixture.service().record(
                fixture.blobs(), empty, tamper(waiverChallenge(fixture.signer()))));
    }

    @Test
    void rejectsChangedResponseFieldsWithTheOldMacForAllSixFamilies() throws Exception {
        Fixture fixture = fixture("response-macs");

        BlobReference discovery = recordChallenge(
                fixture, discoveryChallenge(fixture.signer()));
        assertThrows(IOException.class, () -> fixture.service().record(
                fixture.blobs(), discovery, tamper(discoveryAnswer(fixture.signer()))));

        BlobReference document = recordChallenge(
                fixture, documentChallenge(fixture.signer()));
        assertThrows(IOException.class, () -> fixture.service().record(
                fixture.blobs(), document, tamper(documentResponse(fixture.signer()))));

        BlobReference remote = recordChallenge(fixture, remoteChallenge(fixture.signer()));
        assertThrows(IOException.class, () -> fixture.service().record(
                fixture.blobs(), remote, tamper(remoteResponse(fixture.signer()))));

        BlobReference design = recordChallenge(fixture, designChallenge(fixture.signer()));
        assertThrows(IOException.class, () -> fixture.service().record(
                fixture.blobs(), design, tamper(designResponse(fixture.signer(), DIGEST))));

        BlobReference plan = recordChallenge(fixture, planChallenge(fixture.signer()));
        assertThrows(IOException.class, () -> fixture.service().record(
                fixture.blobs(), plan, tamper(planResponse(fixture.signer()))));

        BlobReference waiver = recordChallenge(fixture, waiverChallenge(fixture.signer()));
        assertThrows(IOException.class, () -> fixture.service().record(
                fixture.blobs(), waiver, tamper(waiverResponse(fixture.signer()))));
    }

    @Test
    void rejectsMismatchedResponseAndMultiplePairTags() throws Exception {
        Fixture fixture = fixture("mismatch");
        BlobReference current = recordChallenge(fixture, designChallenge(fixture.signer()));

        assertThrows(IllegalArgumentException.class, () -> fixture.service().record(
                fixture.blobs(), current, designResponse(fixture.signer(), OTHER_DIGEST)));
        assertThrows(IllegalArgumentException.class, () -> new ShipInteractionBundle.Exchange(
                1,
                new ShipInteractionBundle.DiscoveryPair(discoveryChallenge(fixture.signer()), null),
                new ShipInteractionBundle.DocumentConsentPair(documentChallenge(fixture.signer()), null),
                null,
                null,
                null,
                null));
    }

    @Test
    void rejectsApprovalRecordsForAnotherBundleContext() throws Exception {
        Fixture fixture = fixture("context-mismatch");
        BlobReference current = fixture.service().create(fixture.blobs(), RUN_ID, OTHER_DIGEST);

        assertThrows(IllegalArgumentException.class, () -> fixture.service().record(
                fixture.blobs(), current, designChallenge(fixture.signer())));
        assertThrows(IllegalArgumentException.class, () -> fixture.service().record(
                fixture.blobs(), current, planChallenge(fixture.signer())));
    }

    @Test
    void rejectsValidlySignedResponseForAnotherChallengeNonce() throws Exception {
        Fixture fixture = fixture("nonce-mismatch");
        BlobReference current = recordChallenge(fixture, designChallenge(fixture.signer()));

        assertThrows(IllegalArgumentException.class, () -> fixture.service().record(
                fixture.blobs(), current, designResponse(fixture.signer(), DIGEST, "b".repeat(43))));
    }

    @Test
    void amendedContextStartsANewEmptyBoundedRevision() throws Exception {
        Fixture fixture = fixture("amend");
        BlobReference first = recordChallenge(fixture, discoveryChallenge(fixture.signer()));

        ShipInteractionBundle amended = fixture.service().read(
                fixture.blobs(), fixture.service().amend(fixture.blobs(), first, OTHER_DIGEST));

        assertEquals(2, amended.contextRevision());
        assertEquals(OTHER_DIGEST, amended.contextDigest());
        assertTrue(amended.exchanges().isEmpty());
    }

    @Test
    void rejectsCrossRunSignerBlobStoreAndDeclaredRunMiswiring() throws Exception {
        Fixture first = fixture("run-binding-first", RUN_ID);
        Fixture second = fixture("run-binding-second", OTHER_RUN_ID);
        Fixture sameIdDifferentState = fixture("run-binding-shadow", RUN_ID);

        assertThrows(
                IOException.class,
                () -> first.service().create(second.blobs(), RUN_ID, DIGEST));
        assertThrows(
                IOException.class,
                () -> first.service().create(first.blobs(), OTHER_RUN_ID, DIGEST));
        assertThrows(
                IOException.class,
                () -> first.service().create(sameIdDifferentState.blobs(), RUN_ID, DIGEST));

        BlobReference secondBundle = second.service().create(second.blobs(), OTHER_RUN_ID, DIGEST);
        assertThrows(
                IOException.class,
                () -> first.service().read(second.blobs(), secondBundle));
    }

    private BlobReference recordChallenge(Fixture fixture, Object challenge) throws Exception {
        BlobReference current = fixture.service().create(fixture.blobs(), RUN_ID, DIGEST);
        if (challenge instanceof DiscoveryChallenge value) {
            return fixture.service().record(fixture.blobs(), current, value);
        }
        if (challenge instanceof DocumentConsentChallenge value) {
            return fixture.service().record(fixture.blobs(), current, value);
        }
        if (challenge instanceof RemoteProviderConsentChallenge value) {
            return fixture.service().record(fixture.blobs(), current, value);
        }
        if (challenge instanceof DesignChallenge value) {
            return fixture.service().record(fixture.blobs(), current, value);
        }
        if (challenge instanceof PlanChallenge value) {
            return fixture.service().record(fixture.blobs(), current, value);
        }
        return fixture.service().record(fixture.blobs(), current, (WaiverChallenge) challenge);
    }

    private Fixture fixture(String name) throws Exception {
        return fixture(name, RUN_ID);
    }

    private Fixture fixture(String name, String runId) throws Exception {
        Path state = Files.createDirectory(
                temporaryDirectory.resolve(name),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        Path runRoot = Files.createDirectory(
                state.resolve(runId),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        ShipBlobStore blobs = ShipBlobStore.create(state, runId);
        ShipInteractionSigner signer = ShipInteractionSigner.create(runRoot);
        return new Fixture(blobs, signer, new ShipInteractionBundleService(signer));
    }

    private static DiscoveryChallenge discoveryChallenge(ShipInteractionSigner signer) throws Exception {
        String mac = signer.sign(Interaction.discoveryChallengeMacFields(
                RUN_ID, 1, "question-1", "open-item-1", "Choose one", List.of("a", "b"), "a", NONCE));
        return new DiscoveryChallenge(
                Interaction.SCHEMA_VERSION,
                RUN_ID,
                1,
                "question-1",
                "open-item-1",
                "Choose one",
                List.of("a", "b"),
                "a",
                NONCE,
                mac);
    }

    private static DiscoveryAnswer discoveryAnswer(ShipInteractionSigner signer) throws Exception {
        DiscoveryAnswer value = new DiscoveryAnswer(
                Interaction.SCHEMA_VERSION, RUN_ID, 1, "question-1", "open-item-1", NONCE,
                "a", "cli", Instant.EPOCH, SYNTACTIC_MAC);
        return new DiscoveryAnswer(
                value.schemaVersion(), value.runId(), value.ledgerRevision(), value.questionId(),
                value.openItemId(), value.nonce(), value.response(), value.channel(), value.answeredAt(),
                signer.sign(Interaction.discoveryAnswerMacFields(value)));
    }

    private static DocumentConsentChallenge documentChallenge(ShipInteractionSigner signer) throws Exception {
        DocumentConsentChallenge value = new DocumentConsentChallenge(
                Interaction.SCHEMA_VERSION, RUN_ID, "document-1", 0, "/controlled/requirements.md",
                "Read requirements", DIGEST, NONCE, SYNTACTIC_MAC);
        return new DocumentConsentChallenge(
                value.schemaVersion(), value.runId(), value.requestId(), value.sourceOrdinal(), value.canonicalPath(),
                value.purpose(), value.physicalIdentityDigest(), value.nonce(),
                signer.sign(Interaction.documentConsentChallengeMacFields(value)));
    }

    private static DocumentConsentResponse documentResponse(ShipInteractionSigner signer) throws Exception {
        DocumentConsentResponse value = new DocumentConsentResponse(
                Interaction.SCHEMA_VERSION, RUN_ID, "document-1", 0, "/controlled/requirements.md",
                "Read requirements", DIGEST, NONCE, ConsentDecision.AUTHORIZE, "uid:1000", "terminal-v1",
                "cli", Instant.EPOCH, SYNTACTIC_MAC);
        return new DocumentConsentResponse(
                value.schemaVersion(), value.runId(), value.requestId(), value.sourceOrdinal(), value.canonicalPath(),
                value.purpose(), value.physicalIdentityDigest(), value.nonce(), value.decision(),
                value.controllerObservedProcessPrincipal(), value.declaredCliUiProfile(), value.channel(),
                value.answeredAt(), signer.sign(Interaction.documentConsentResponseMacFields(value)));
    }

    private static RemoteProviderConsentChallenge remoteChallenge(ShipInteractionSigner signer) throws Exception {
        RemoteProviderConsentChallenge value = new RemoteProviderConsentChallenge(
                Interaction.SCHEMA_VERSION, RUN_ID, "remote-1", ShipStage.DESIGN, "Generate design", DIGEST,
                DIGEST, "provider", "model", "broker-v1", "exact-content", NONCE, SYNTACTIC_MAC);
        return new RemoteProviderConsentChallenge(
                value.schemaVersion(), value.runId(), value.requestId(), value.stage(), value.purpose(),
                value.contentDigest(), value.provenanceDigest(), value.provider(), value.model(),
                value.brokerProfile(), value.scope(), value.nonce(),
                signer.sign(Interaction.remoteProviderConsentChallengeMacFields(value)));
    }

    private static RemoteProviderConsentResponse remoteResponse(ShipInteractionSigner signer) throws Exception {
        RemoteProviderConsentResponse value = new RemoteProviderConsentResponse(
                Interaction.SCHEMA_VERSION, RUN_ID, "remote-1", ShipStage.DESIGN, "Generate design", DIGEST,
                DIGEST, "provider", "model", "broker-v1", "exact-content", NONCE, ConsentDecision.AUTHORIZE,
                "uid:1000", "terminal-v1", "cli", Instant.EPOCH, SYNTACTIC_MAC);
        return new RemoteProviderConsentResponse(
                value.schemaVersion(), value.runId(), value.requestId(), value.stage(), value.purpose(),
                value.contentDigest(), value.provenanceDigest(), value.provider(), value.model(),
                value.brokerProfile(), value.scope(), value.nonce(), value.decision(),
                value.controllerObservedProcessPrincipal(), value.declaredCliUiProfile(), value.channel(),
                value.answeredAt(), signer.sign(Interaction.remoteProviderConsentResponseMacFields(value)));
    }

    private static DesignChallenge designChallenge(ShipInteractionSigner signer) throws Exception {
        DesignChallenge value = new DesignChallenge(
                Interaction.SCHEMA_VERSION, RUN_ID, DIGEST, 1, DIGEST, DIGEST, DIGEST, DIGEST, DIGEST,
                "design.md", NONCE, SYNTACTIC_MAC);
        return new DesignChallenge(
                value.schemaVersion(), value.runId(), value.contextDigest(), value.ledgerRevision(),
                value.ledgerDigest(), value.requirementsDigest(), value.policyDigest(), value.baselineDigest(),
                value.designDigest(), value.designReference(), value.nonce(),
                signer.sign(Interaction.designChallengeMacFields(value)));
    }

    private static DesignResponse designResponse(ShipInteractionSigner signer, String designDigest)
            throws Exception {
        return designResponse(signer, designDigest, NONCE);
    }

    private static DesignResponse designResponse(
            ShipInteractionSigner signer, String designDigest, String nonce)
            throws Exception {
        DesignResponse value = new DesignResponse(
                Interaction.SCHEMA_VERSION, RUN_ID, DIGEST, 1, DIGEST, DIGEST, DIGEST, DIGEST, designDigest,
                nonce, DesignDecision.APPROVE, "", "uid:1000", "terminal-v1", "cli", Instant.EPOCH,
                SYNTACTIC_MAC);
        return new DesignResponse(
                value.schemaVersion(), value.runId(), value.contextDigest(), value.ledgerRevision(),
                value.ledgerDigest(), value.requirementsDigest(), value.policyDigest(), value.baselineDigest(),
                value.designDigest(), value.nonce(), value.decision(), value.requestedChanges(),
                value.controllerObservedProcessPrincipal(), value.declaredCliUiProfile(), value.channel(),
                value.answeredAt(), signer.sign(Interaction.designResponseMacFields(value)));
    }

    private static PlanChallenge planChallenge(ShipInteractionSigner signer) throws Exception {
        PlanChallenge value = new PlanChallenge(
                Interaction.SCHEMA_VERSION, RUN_ID, DIGEST, 1, DIGEST, DIGEST, DIGEST, DIGEST, DIGEST, DIGEST,
                "plan.md", NONCE, SYNTACTIC_MAC);
        return new PlanChallenge(
                value.schemaVersion(), value.runId(), value.contextDigest(), value.ledgerRevision(),
                value.ledgerDigest(), value.requirementsDigest(), value.policyDigest(), value.baselineDigest(),
                value.approvedDesignDigest(), value.planDigest(), value.planReference(), value.nonce(),
                signer.sign(Interaction.planChallengeMacFields(value)));
    }

    private static PlanResponse planResponse(ShipInteractionSigner signer) throws Exception {
        PlanResponse value = new PlanResponse(
                Interaction.SCHEMA_VERSION, RUN_ID, DIGEST, 1, DIGEST, DIGEST, DIGEST, DIGEST, DIGEST, DIGEST,
                NONCE, PlanDecision.APPROVE, "", "uid:1000", "terminal-v1", "cli", Instant.EPOCH,
                SYNTACTIC_MAC);
        return new PlanResponse(
                value.schemaVersion(), value.runId(), value.contextDigest(), value.ledgerRevision(),
                value.ledgerDigest(), value.requirementsDigest(), value.policyDigest(), value.baselineDigest(),
                value.approvedDesignDigest(), value.planDigest(), value.nonce(), value.decision(),
                value.requestedChanges(), value.controllerObservedProcessPrincipal(), value.declaredCliUiProfile(),
                value.channel(), value.answeredAt(), signer.sign(Interaction.planResponseMacFields(value)));
    }

    private static WaiverChallenge waiverChallenge(ShipInteractionSigner signer) throws Exception {
        WaiverChallenge value = new WaiverChallenge(
                Interaction.SCHEMA_VERSION, RUN_ID, "runner-isolation", DIGEST, DIGEST, DIGEST, "waiver.txt",
                "Experimental runner", "Evidence is limited", NONCE, SYNTACTIC_MAC);
        return new WaiverChallenge(
                value.schemaVersion(), value.runId(), value.checkId(), value.evidenceDigest(),
                value.eligibilityPolicyDigest(), value.subjectDigest(), value.subjectReference(), value.risk(),
                value.consequence(), value.nonce(), signer.sign(Interaction.waiverChallengeMacFields(value)));
    }

    private static WaiverResponse waiverResponse(ShipInteractionSigner signer) throws Exception {
        WaiverResponse value = new WaiverResponse(
                Interaction.SCHEMA_VERSION, RUN_ID, "runner-isolation", DIGEST, DIGEST, DIGEST, NONCE,
                WaiverDecision.DENY, "", "uid:1000", "terminal-v1", "cli", Instant.EPOCH, SYNTACTIC_MAC);
        return new WaiverResponse(
                value.schemaVersion(), value.runId(), value.checkId(), value.evidenceDigest(),
                value.eligibilityPolicyDigest(), value.subjectDigest(), value.nonce(), value.decision(), value.reason(),
                value.controllerObservedProcessPrincipal(), value.declaredCliUiProfile(), value.channel(),
                value.answeredAt(), signer.sign(Interaction.waiverResponseMacFields(value)));
    }

    private static DiscoveryChallenge tamper(DiscoveryChallenge value) {
        return new DiscoveryChallenge(
                value.schemaVersion(), value.runId(), value.ledgerRevision(), value.questionId(), value.openItemId(),
                value.prompt() + "!", value.options(), value.recommendation(), value.nonce(), value.mac());
    }

    private static DocumentConsentChallenge tamper(DocumentConsentChallenge value) {
        return new DocumentConsentChallenge(
                value.schemaVersion(), value.runId(), value.requestId(), value.sourceOrdinal(), value.canonicalPath(),
                value.purpose() + "!", value.physicalIdentityDigest(), value.nonce(), value.mac());
    }

    private static RemoteProviderConsentChallenge tamper(RemoteProviderConsentChallenge value) {
        return new RemoteProviderConsentChallenge(
                value.schemaVersion(), value.runId(), value.requestId(), value.stage(), value.purpose(),
                value.contentDigest(), value.provenanceDigest(), value.provider(), value.model(),
                value.brokerProfile(), value.scope() + "!", value.nonce(), value.mac());
    }

    private static DesignChallenge tamper(DesignChallenge value) {
        return new DesignChallenge(
                value.schemaVersion(), value.runId(), value.contextDigest(), value.ledgerRevision(),
                value.ledgerDigest(), value.requirementsDigest(), value.policyDigest(), value.baselineDigest(),
                value.designDigest(), value.designReference() + "!", value.nonce(), value.mac());
    }

    private static PlanChallenge tamper(PlanChallenge value) {
        return new PlanChallenge(
                value.schemaVersion(), value.runId(), value.contextDigest(), value.ledgerRevision(),
                value.ledgerDigest(), value.requirementsDigest(), value.policyDigest(), value.baselineDigest(),
                value.approvedDesignDigest(), value.planDigest(), value.planReference() + "!", value.nonce(),
                value.mac());
    }

    private static WaiverChallenge tamper(WaiverChallenge value) {
        return new WaiverChallenge(
                value.schemaVersion(), value.runId(), value.checkId(), value.evidenceDigest(),
                value.eligibilityPolicyDigest(), value.subjectDigest(), value.subjectReference(), value.risk() + "!",
                value.consequence(), value.nonce(), value.mac());
    }

    private static DiscoveryAnswer tamper(DiscoveryAnswer value) {
        return new DiscoveryAnswer(
                value.schemaVersion(), value.runId(), value.ledgerRevision(), value.questionId(), value.openItemId(),
                value.nonce(), value.response(), "other", value.answeredAt(), value.mac());
    }

    private static DocumentConsentResponse tamper(DocumentConsentResponse value) {
        return new DocumentConsentResponse(
                value.schemaVersion(), value.runId(), value.requestId(), value.sourceOrdinal(), value.canonicalPath(),
                value.purpose(), value.physicalIdentityDigest(), value.nonce(), value.decision(),
                value.controllerObservedProcessPrincipal(), value.declaredCliUiProfile(), "other", value.answeredAt(),
                value.mac());
    }

    private static RemoteProviderConsentResponse tamper(RemoteProviderConsentResponse value) {
        return new RemoteProviderConsentResponse(
                value.schemaVersion(), value.runId(), value.requestId(), value.stage(), value.purpose(),
                value.contentDigest(), value.provenanceDigest(), value.provider(), value.model(),
                value.brokerProfile(), value.scope(), value.nonce(), value.decision(),
                value.controllerObservedProcessPrincipal(), value.declaredCliUiProfile(), "other", value.answeredAt(),
                value.mac());
    }

    private static DesignResponse tamper(DesignResponse value) {
        return new DesignResponse(
                value.schemaVersion(), value.runId(), value.contextDigest(), value.ledgerRevision(),
                value.ledgerDigest(), value.requirementsDigest(), value.policyDigest(), value.baselineDigest(),
                value.designDigest(), value.nonce(), value.decision(), value.requestedChanges(),
                value.controllerObservedProcessPrincipal(), value.declaredCliUiProfile(), "other", value.answeredAt(),
                value.mac());
    }

    private static PlanResponse tamper(PlanResponse value) {
        return new PlanResponse(
                value.schemaVersion(), value.runId(), value.contextDigest(), value.ledgerRevision(),
                value.ledgerDigest(), value.requirementsDigest(), value.policyDigest(), value.baselineDigest(),
                value.approvedDesignDigest(), value.planDigest(), value.nonce(), value.decision(),
                value.requestedChanges(), value.controllerObservedProcessPrincipal(), value.declaredCliUiProfile(),
                "other", value.answeredAt(), value.mac());
    }

    private static WaiverResponse tamper(WaiverResponse value) {
        return new WaiverResponse(
                value.schemaVersion(), value.runId(), value.checkId(), value.evidenceDigest(),
                value.eligibilityPolicyDigest(), value.subjectDigest(), value.nonce(), value.decision(), value.reason(),
                value.controllerObservedProcessPrincipal(), value.declaredCliUiProfile(), "other", value.answeredAt(),
                value.mac());
    }

    private record Fixture(
            ShipBlobStore blobs,
            ShipInteractionSigner signer,
            ShipInteractionBundleService service) {
    }
}
