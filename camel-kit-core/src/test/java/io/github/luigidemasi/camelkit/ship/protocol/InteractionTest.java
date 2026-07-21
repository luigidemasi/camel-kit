package io.github.luigidemasi.camelkit.ship.protocol;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import io.github.luigidemasi.camelkit.ship.protocol.Interaction.DiscoveryAnswer;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.DiscoveryChallenge;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.DocumentConsentChallenge;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.DocumentConsentResponse;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.RemoteProviderConsentChallenge;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.RemoteProviderConsentResponse;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InteractionTest {

    private static final String RUN_ID = "ship-00000000000000000000000000000001";
    private static final String NONCE = "n".repeat(43);
    private static final String MAC = "hmac-sha256:" + "a".repeat(64);
    private static final String DIGEST = "sha256:" + "b".repeat(64);
    private static final String PRINCIPAL = "unix:uid=1000";
    private static final String UI_PROFILE = "camel-kit-cli:1";

    @Test
    void discoveryMacBindsOrderedOptionsAndRecommendation() {
        String[] original = Interaction.discoveryChallengeMacFields(
                RUN_ID, 1, "question-1", "open-1", "Runtime?",
                List.of("main", "quarkus"), "main", NONCE);
        String[] reordered = Interaction.discoveryChallengeMacFields(
                RUN_ID, 1, "question-1", "open-1", "Runtime?",
                List.of("quarkus", "main"), "main", NONCE);
        String[] changedRecommendation = Interaction.discoveryChallengeMacFields(
                RUN_ID, 1, "question-1", "open-1", "Runtime?",
                List.of("main", "quarkus"), "quarkus", NONCE);

        assertFalse(Arrays.equals(original, reordered));
        assertFalse(Arrays.equals(original, changedRecommendation));
        assertEquals("discovery-challenge-v1", original[0]);
    }

    @Test
    void interactionRecordsRejectOversizedOrMalformedUntrustedFields() {
        assertThrows(IllegalArgumentException.class, () -> new DiscoveryChallenge(
                1, RUN_ID, 1, "question-1", "open-1", "x".repeat(Interaction.MAX_PROMPT_CHARS + 1),
                List.of(), null, NONCE, MAC));
        assertThrows(IllegalArgumentException.class, () -> new DiscoveryChallenge(
                1, RUN_ID, 1, "question-1", "open-1", "Runtime?",
                List.of("main", "main"), null, NONCE, MAC));
        assertThrows(IllegalArgumentException.class, () -> new DiscoveryAnswer(
                1, RUN_ID, 1, "question-1", "open-1", NONCE,
                "x".repeat(Interaction.MAX_RESPONSE_CHARS + 1), "external", Instant.EPOCH, MAC));
        assertThrows(IllegalArgumentException.class, () -> new DiscoveryAnswer(
                1, RUN_ID, 1, "question-1", "open-1", "short", "main", "external",
                Instant.EPOCH, MAC));
        assertThrows(IllegalArgumentException.class, () -> new DiscoveryAnswer(
                1, RUN_ID, 1, "question-1", "open-1", NONCE, "main", "external",
                Instant.EPOCH, "not-a-mac"));
    }

    @Test
    void localDocumentAndRemoteProviderConsentRemainSeparate() {
        DocumentConsentChallenge local = new DocumentConsentChallenge(
                1, RUN_ID, "document-1", 0, "/outside/requirements.md", "initial-context", DIGEST,
                NONCE, MAC);
        DocumentConsentResponse localResponse = new DocumentConsentResponse(
                1, RUN_ID, "document-1", 0, "/outside/requirements.md", "initial-context", DIGEST,
                NONCE, Interaction.ConsentDecision.AUTHORIZE, PRINCIPAL, UI_PROFILE, "protected-cli",
                Instant.EPOCH, MAC);
        RemoteProviderConsentChallenge remote = new RemoteProviderConsentChallenge(
                1, RUN_ID, "provider-1", ShipStage.DISCOVERY, "semantic-discovery", DIGEST, DIGEST,
                "provider", "model", "broker:1", "source:document-1", NONCE, MAC);
        RemoteProviderConsentResponse remoteResponse = new RemoteProviderConsentResponse(
                1, RUN_ID, "provider-1", ShipStage.DISCOVERY, "semantic-discovery", DIGEST, DIGEST,
                "provider", "model", "broker:1", "source:document-1", NONCE,
                Interaction.ConsentDecision.DENY, PRINCIPAL, UI_PROFILE, "protected-cli", Instant.EPOCH, MAC);

        assertEquals("document-consent-challenge-v1", Interaction.documentConsentChallengeMacFields(local)[0]);
        assertEquals("document-consent-response-v1", Interaction.documentConsentResponseMacFields(localResponse)[0]);
        assertEquals("remote-provider-consent-challenge-v1",
                Interaction.remoteProviderConsentChallengeMacFields(remote)[0]);
        assertEquals("remote-provider-consent-response-v1",
                Interaction.remoteProviderConsentResponseMacFields(remoteResponse)[0]);
        assertFalse(Arrays.equals(
                Interaction.documentConsentChallengeMacFields(local),
                Interaction.remoteProviderConsentChallengeMacFields(remote)));
    }

    @Test
    void designAndPlanHaveIndependentExactApprovalBindings() {
        Interaction.DesignChallenge design = new Interaction.DesignChallenge(
                1, RUN_ID, DIGEST, 2, DIGEST, DIGEST, DIGEST, DIGEST, DIGEST, "design.md", NONCE, MAC);
        Interaction.DesignResponse designResponse = new Interaction.DesignResponse(
                1, RUN_ID, DIGEST, 2, DIGEST, DIGEST, DIGEST, DIGEST, DIGEST, NONCE,
                Interaction.DesignDecision.APPROVE, "", PRINCIPAL, UI_PROFILE, "protected-cli",
                Instant.EPOCH, MAC);
        Interaction.PlanChallenge plan = new Interaction.PlanChallenge(
                1, RUN_ID, DIGEST, 2, DIGEST, DIGEST, DIGEST, DIGEST, DIGEST, DIGEST, "plan.md",
                NONCE, MAC);
        Interaction.PlanResponse planResponse = new Interaction.PlanResponse(
                1, RUN_ID, DIGEST, 2, DIGEST, DIGEST, DIGEST, DIGEST, DIGEST, DIGEST, NONCE,
                Interaction.PlanDecision.APPROVE, "", PRINCIPAL, UI_PROFILE, "protected-cli",
                Instant.EPOCH, MAC);

        assertEquals("design-challenge-v1", Interaction.designChallengeMacFields(design)[0]);
        assertEquals("design-response-v1", Interaction.designResponseMacFields(designResponse)[0]);
        assertEquals("plan-challenge-v1", Interaction.planChallengeMacFields(plan)[0]);
        assertEquals("plan-response-v1", Interaction.planResponseMacFields(planResponse)[0]);
        assertFalse(Arrays.equals(
                Interaction.designResponseMacFields(designResponse),
                Interaction.planResponseMacFields(planResponse)));
        assertFalse(Arrays.equals(
                Interaction.designResponseMacFields(designResponse),
                Interaction.designResponseMacFields(
                        RUN_ID, DIGEST, 2, DIGEST, DIGEST, DIGEST, DIGEST, DIGEST, NONCE,
                        Interaction.DesignDecision.APPROVE, "", "unix:uid=1001", UI_PROFILE,
                        "protected-cli", Instant.EPOCH)));
    }

    @Test
    void waiverBindsEligibilitySubjectEvidenceAndResponder() {
        Interaction.WaiverChallenge challenge = new Interaction.WaiverChallenge(
                1, RUN_ID, "runner-isolation", DIGEST, DIGEST, DIGEST, "waiver.txt",
                "Runner isolation is incomplete", "Normal PASS remains unavailable", NONCE, MAC);
        Interaction.WaiverResponse response = new Interaction.WaiverResponse(
                1, RUN_ID, "runner-isolation", DIGEST, DIGEST, DIGEST, NONCE,
                Interaction.WaiverDecision.WAIVE, "Accept platform-specific evidence", PRINCIPAL, UI_PROFILE,
                "protected-cli", Instant.EPOCH, MAC);

        assertEquals("waiver-challenge-v1", Interaction.waiverChallengeMacFields(challenge)[0]);
        assertEquals("waiver-response-v1", Interaction.waiverResponseMacFields(response)[0]);
        assertThrows(IllegalArgumentException.class, () -> new Interaction.WaiverResponse(
                1, RUN_ID, "runner-isolation", DIGEST, DIGEST, DIGEST, NONCE,
                Interaction.WaiverDecision.WAIVE, "", PRINCIPAL, UI_PROFILE, "protected-cli",
                Instant.EPOCH, MAC));
        assertThrows(IllegalArgumentException.class, () -> new Interaction.DesignResponse(
                1, RUN_ID, DIGEST, 2, DIGEST, DIGEST, DIGEST, DIGEST, DIGEST, NONCE,
                Interaction.DesignDecision.APPROVE, "", "", UI_PROFILE, "protected-cli",
                Instant.EPOCH, MAC));
    }
}
