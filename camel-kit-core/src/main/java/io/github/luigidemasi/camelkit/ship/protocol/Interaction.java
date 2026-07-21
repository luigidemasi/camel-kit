package io.github.luigidemasi.camelkit.ship.protocol;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import io.github.luigidemasi.camelkit.ship.ShipDigest;

/**
 * Typed controller-owned user interaction messages.
 *
 * <p>
 * Principal and UI-profile values are authenticated only after the later protected controller service observes and
 * signs them. Merely constructing one of these wire records grants no controller authority.
 */
public final class Interaction {

    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_ID_CHARS = 256;
    public static final int MAX_PROMPT_CHARS = 16 * 1024;
    public static final int MAX_OPTIONS = 32;
    public static final int MAX_OPTION_CHARS = 4 * 1024;
    public static final int MAX_RESPONSE_CHARS = 64 * 1024;
    public static final int MAX_CHANNEL_CHARS = 128;
    public static final int MAX_REFERENCE_CHARS = 4 * 1024;
    private static final int NONCE_CHARS = 43;

    private Interaction() {
    }

    public record DiscoveryChallenge(
            int schemaVersion,
            String runId,
            long ledgerRevision,
            String questionId,
            String openItemId,
            String prompt,
            List<String> options,
            String recommendation,
            String nonce,
            String mac) {

        public DiscoveryChallenge {
            options = options == null ? List.of() : List.copyOf(options);
            requireCommon(schemaVersion, runId, ledgerRevision, questionId, nonce, mac, "discovery challenge");
            requireText(openItemId, MAX_ID_CHARS, "Discovery challenge open-item ID");
            requireText(prompt, MAX_PROMPT_CHARS, "Discovery challenge prompt");
            if (options.size() > MAX_OPTIONS) {
                throw new IllegalArgumentException("Discovery challenge has too many options");
            }
            HashSet<String> unique = new HashSet<>();
            for (String option : options) {
                requireText(option, MAX_OPTION_CHARS, "Discovery challenge option");
                if (!unique.add(option)) {
                    throw new IllegalArgumentException("Discovery challenge options must be unique");
                }
            }
            requireOptionalText(recommendation, MAX_OPTION_CHARS, "Discovery challenge recommendation");
            recommendation = recommendation == null || recommendation.isBlank() ? null : recommendation;
        }
    }

    public record DiscoveryAnswer(
            int schemaVersion,
            String runId,
            long ledgerRevision,
            String questionId,
            String openItemId,
            String nonce,
            String response,
            String channel,
            Instant answeredAt,
            String mac) {

        public DiscoveryAnswer {
            requireCommon(schemaVersion, runId, ledgerRevision, questionId, nonce, mac, "discovery answer");
            requireText(openItemId, MAX_ID_CHARS, "Discovery answer open-item ID");
            requireText(response, MAX_RESPONSE_CHARS, "Discovery answer response");
            requireText(channel, MAX_CHANNEL_CHARS, "Discovery answer channel");
            if (answeredAt == null) {
                throw new IllegalArgumentException("Discovery answer timestamp is required");
            }
        }
    }

    /** Consent to read one exact external document locally. */
    public record DocumentConsentChallenge(
            int schemaVersion,
            String runId,
            String requestId,
            int sourceOrdinal,
            String canonicalPath,
            String purpose,
            String physicalIdentityDigest,
            String nonce,
            String mac) {

        public DocumentConsentChallenge {
            requireEnvelope(schemaVersion, runId, requestId, nonce, mac, "document-consent challenge");
            if (sourceOrdinal < 0) {
                throw new IllegalArgumentException("Document-consent source ordinal must not be negative");
            }
            requireText(canonicalPath, MAX_REFERENCE_CHARS, "Document-consent canonical path");
            requireText(purpose, MAX_REFERENCE_CHARS, "Document-consent purpose");
            requireDigest(physicalIdentityDigest, "Document-consent physical-identity digest");
        }
    }

    public record DocumentConsentResponse(
            int schemaVersion,
            String runId,
            String requestId,
            int sourceOrdinal,
            String canonicalPath,
            String purpose,
            String physicalIdentityDigest,
            String nonce,
            ConsentDecision decision,
            String controllerObservedProcessPrincipal,
            String declaredCliUiProfile,
            String channel,
            Instant answeredAt,
            String mac) {

        public DocumentConsentResponse {
            requireEnvelope(schemaVersion, runId, requestId, nonce, mac, "document-consent response");
            if (sourceOrdinal < 0) {
                throw new IllegalArgumentException("Document-consent source ordinal must not be negative");
            }
            requireText(canonicalPath, MAX_REFERENCE_CHARS, "Document-consent canonical path");
            requireText(purpose, MAX_REFERENCE_CHARS, "Document-consent purpose");
            requireDigest(physicalIdentityDigest, "Document-consent physical-identity digest");
            requireDecision(decision, "Document-consent response");
            requireResponder(controllerObservedProcessPrincipal, declaredCliUiProfile, channel, answeredAt,
                    "Document-consent response");
        }
    }

    /** Separate consent to send exact content to one remote provider/model scope. */
    public record RemoteProviderConsentChallenge(
            int schemaVersion,
            String runId,
            String requestId,
            ShipStage stage,
            String purpose,
            String contentDigest,
            String provenanceDigest,
            String provider,
            String model,
            String brokerProfile,
            String scope,
            String nonce,
            String mac) {

        public RemoteProviderConsentChallenge {
            requireEnvelope(schemaVersion, runId, requestId, nonce, mac, "remote-provider consent challenge");
            requireDecision(stage, "Remote-provider consent stage");
            requireText(purpose, MAX_REFERENCE_CHARS, "Remote-provider consent purpose");
            requireDigest(contentDigest, "Remote-provider consent content digest");
            requireDigest(provenanceDigest, "Remote-provider consent provenance digest");
            requireText(provider, MAX_ID_CHARS, "Remote-provider consent provider");
            requireText(model, MAX_ID_CHARS, "Remote-provider consent model");
            requireText(brokerProfile, MAX_REFERENCE_CHARS, "Remote-provider consent broker profile");
            requireText(scope, MAX_REFERENCE_CHARS, "Remote-provider consent scope");
        }
    }

    public record RemoteProviderConsentResponse(
            int schemaVersion,
            String runId,
            String requestId,
            ShipStage stage,
            String purpose,
            String contentDigest,
            String provenanceDigest,
            String provider,
            String model,
            String brokerProfile,
            String scope,
            String nonce,
            ConsentDecision decision,
            String controllerObservedProcessPrincipal,
            String declaredCliUiProfile,
            String channel,
            Instant answeredAt,
            String mac) {

        public RemoteProviderConsentResponse {
            requireEnvelope(schemaVersion, runId, requestId, nonce, mac, "remote-provider consent response");
            requireDecision(stage, "Remote-provider consent stage");
            requireText(purpose, MAX_REFERENCE_CHARS, "Remote-provider consent purpose");
            requireDigest(contentDigest, "Remote-provider consent content digest");
            requireDigest(provenanceDigest, "Remote-provider consent provenance digest");
            requireText(provider, MAX_ID_CHARS, "Remote-provider consent provider");
            requireText(model, MAX_ID_CHARS, "Remote-provider consent model");
            requireText(brokerProfile, MAX_REFERENCE_CHARS, "Remote-provider consent broker profile");
            requireText(scope, MAX_REFERENCE_CHARS, "Remote-provider consent scope");
            requireDecision(decision, "Remote-provider consent response");
            requireResponder(controllerObservedProcessPrincipal, declaredCliUiProfile, channel, answeredAt,
                    "Remote-provider consent response");
        }
    }

    /** Approval challenge for the exact safely displayed design and all of its authoritative inputs. */
    public record DesignChallenge(
            int schemaVersion,
            String runId,
            String contextDigest,
            long ledgerRevision,
            String ledgerDigest,
            String requirementsDigest,
            String policyDigest,
            String baselineDigest,
            String designDigest,
            String designReference,
            String nonce,
            String mac) {

        public DesignChallenge {
            requireApprovalBindings(schemaVersion, runId, contextDigest, ledgerRevision, ledgerDigest,
                    requirementsDigest, policyDigest, baselineDigest, nonce, mac, "design challenge");
            requireDigest(designDigest, "Design challenge design digest");
            requireText(designReference, MAX_REFERENCE_CHARS, "Design challenge reference");
        }
    }

    public record DesignResponse(
            int schemaVersion,
            String runId,
            String contextDigest,
            long ledgerRevision,
            String ledgerDigest,
            String requirementsDigest,
            String policyDigest,
            String baselineDigest,
            String designDigest,
            String nonce,
            DesignDecision decision,
            String requestedChanges,
            String controllerObservedProcessPrincipal,
            String declaredCliUiProfile,
            String channel,
            Instant answeredAt,
            String mac) {

        public DesignResponse {
            requireApprovalBindings(schemaVersion, runId, contextDigest, ledgerRevision, ledgerDigest,
                    requirementsDigest, policyDigest, baselineDigest, nonce, mac, "design response");
            requireDigest(designDigest, "Design response design digest");
            requireDecision(decision, "Design response");
            requestedChanges = requireRequestedChanges(
                    requestedChanges,
                    decision == DesignDecision.REQUEST_DESIGN_CHANGES
                            || decision == DesignDecision.REQUEST_REQUIREMENTS_CHANGES,
                    "Design response");
            requireResponder(controllerObservedProcessPrincipal, declaredCliUiProfile, channel, answeredAt,
                    "Design response");
        }
    }

    /** Separate approval challenge for the exact plan produced from one approved design. */
    public record PlanChallenge(
            int schemaVersion,
            String runId,
            String contextDigest,
            long ledgerRevision,
            String ledgerDigest,
            String requirementsDigest,
            String policyDigest,
            String baselineDigest,
            String approvedDesignDigest,
            String planDigest,
            String planReference,
            String nonce,
            String mac) {

        public PlanChallenge {
            requireApprovalBindings(schemaVersion, runId, contextDigest, ledgerRevision, ledgerDigest,
                    requirementsDigest, policyDigest, baselineDigest, nonce, mac, "plan challenge");
            requireDigest(approvedDesignDigest, "Plan challenge approved-design digest");
            requireDigest(planDigest, "Plan challenge plan digest");
            requireText(planReference, MAX_REFERENCE_CHARS, "Plan challenge reference");
        }
    }

    public record PlanResponse(
            int schemaVersion,
            String runId,
            String contextDigest,
            long ledgerRevision,
            String ledgerDigest,
            String requirementsDigest,
            String policyDigest,
            String baselineDigest,
            String approvedDesignDigest,
            String planDigest,
            String nonce,
            PlanDecision decision,
            String requestedChanges,
            String controllerObservedProcessPrincipal,
            String declaredCliUiProfile,
            String channel,
            Instant answeredAt,
            String mac) {

        public PlanResponse {
            requireApprovalBindings(schemaVersion, runId, contextDigest, ledgerRevision, ledgerDigest,
                    requirementsDigest, policyDigest, baselineDigest, nonce, mac, "plan response");
            requireDigest(approvedDesignDigest, "Plan response approved-design digest");
            requireDigest(planDigest, "Plan response plan digest");
            requireDecision(decision, "Plan response");
            requestedChanges = requireRequestedChanges(
                    requestedChanges, decision != PlanDecision.APPROVE && decision != PlanDecision.ABORT,
                    "Plan response");
            requireResponder(controllerObservedProcessPrincipal, declaredCliUiProfile, channel, answeredAt,
                    "Plan response");
        }
    }

    /** Controller-generated, safely displayable subject for one policy-eligible failure waiver. */
    public record WaiverChallenge(
            int schemaVersion,
            String runId,
            String checkId,
            String evidenceDigest,
            String eligibilityPolicyDigest,
            String subjectDigest,
            String subjectReference,
            String risk,
            String consequence,
            String nonce,
            String mac) {

        public WaiverChallenge {
            requireEnvelope(schemaVersion, runId, checkId, nonce, mac, "waiver challenge");
            requireDigest(evidenceDigest, "Waiver challenge evidence digest");
            requireDigest(eligibilityPolicyDigest, "Waiver challenge eligibility-policy digest");
            requireDigest(subjectDigest, "Waiver challenge subject digest");
            requireText(subjectReference, MAX_REFERENCE_CHARS, "Waiver challenge subject reference");
            requireText(risk, MAX_PROMPT_CHARS, "Waiver challenge risk");
            requireText(consequence, MAX_PROMPT_CHARS, "Waiver challenge consequence");
        }
    }

    public record WaiverResponse(
            int schemaVersion,
            String runId,
            String checkId,
            String evidenceDigest,
            String eligibilityPolicyDigest,
            String subjectDigest,
            String nonce,
            WaiverDecision decision,
            String reason,
            String controllerObservedProcessPrincipal,
            String declaredCliUiProfile,
            String channel,
            Instant answeredAt,
            String mac) {

        public WaiverResponse {
            requireEnvelope(schemaVersion, runId, checkId, nonce, mac, "waiver response");
            requireDigest(evidenceDigest, "Waiver response evidence digest");
            requireDigest(eligibilityPolicyDigest, "Waiver response eligibility-policy digest");
            requireDigest(subjectDigest, "Waiver response subject digest");
            requireDecision(decision, "Waiver response");
            requireOptionalText(reason, MAX_RESPONSE_CHARS, "Waiver response reason");
            if (decision == WaiverDecision.WAIVE && (reason == null || reason.isBlank())) {
                throw new IllegalArgumentException("An approved waiver requires a reason");
            }
            if (decision == WaiverDecision.DENY && reason != null && !reason.isBlank()) {
                throw new IllegalArgumentException("A denied waiver cannot carry an approval reason");
            }
            reason = decision == WaiverDecision.WAIVE ? reason : "";
            requireResponder(controllerObservedProcessPrincipal, declaredCliUiProfile, channel, answeredAt,
                    "Waiver response");
        }
    }

    public enum ConsentDecision {
        AUTHORIZE,
        DENY
    }

    public enum DesignDecision {
        APPROVE,
        REQUEST_DESIGN_CHANGES,
        REQUEST_REQUIREMENTS_CHANGES,
        ABORT
    }

    public enum PlanDecision {
        APPROVE,
        REQUEST_PLAN_CHANGES,
        REQUEST_DESIGN_CHANGES,
        REQUEST_REQUIREMENTS_CHANGES,
        ABORT
    }

    public enum WaiverDecision {
        WAIVE,
        DENY
    }

    /** Canonical, type-separated fields covered by a discovery-challenge MAC. */
    public static String[] discoveryChallengeMacFields(
            String runId,
            long ledgerRevision,
            String questionId,
            String openItemId,
            String prompt,
            List<String> options,
            String recommendation,
            String nonce) {
        List<String> fields = new ArrayList<>(9 + (options == null ? 0 : options.size()));
        fields.add("discovery-challenge-v1");
        fields.add(runId);
        fields.add(Long.toString(ledgerRevision));
        fields.add(questionId);
        fields.add(openItemId);
        fields.add(prompt);
        List<String> values = options == null ? List.of() : options;
        fields.add(Integer.toString(values.size()));
        fields.addAll(values);
        fields.add(recommendation);
        fields.add(nonce);
        return fields.toArray(String[]::new);
    }

    /** Canonical, type-separated fields covered by a discovery-answer MAC. */
    public static String[] discoveryAnswerMacFields(DiscoveryAnswer answer) {
        return discoveryAnswerMacFields(
                answer.runId(), answer.ledgerRevision(), answer.questionId(), answer.openItemId(), answer.nonce(),
                answer.response(), answer.channel(), answer.answeredAt());
    }

    public static String[] discoveryAnswerMacFields(
            String runId,
            long ledgerRevision,
            String questionId,
            String openItemId,
            String nonce,
            String response,
            String channel,
            Instant answeredAt) {
        return new String[]{
                "discovery-answer-v1", runId, Long.toString(ledgerRevision), questionId, openItemId, nonce,
                response, channel, answeredAt.toString()
        };
    }

    public static String[] documentConsentChallengeMacFields(DocumentConsentChallenge challenge) {
        return documentConsentChallengeMacFields(
                challenge.runId(), challenge.requestId(), challenge.sourceOrdinal(), challenge.canonicalPath(),
                challenge.purpose(), challenge.physicalIdentityDigest(), challenge.nonce());
    }

    public static String[] documentConsentChallengeMacFields(
            String runId,
            String requestId,
            int sourceOrdinal,
            String canonicalPath,
            String purpose,
            String physicalIdentityDigest,
            String nonce) {
        return macFields("document-consent-challenge-v1", runId, requestId, sourceOrdinal, canonicalPath, purpose,
                physicalIdentityDigest, nonce);
    }

    public static String[] documentConsentResponseMacFields(DocumentConsentResponse response) {
        return documentConsentResponseMacFields(
                response.runId(), response.requestId(), response.sourceOrdinal(), response.canonicalPath(),
                response.purpose(), response.physicalIdentityDigest(), response.nonce(), response.decision(),
                response.controllerObservedProcessPrincipal(), response.declaredCliUiProfile(), response.channel(),
                response.answeredAt());
    }

    public static String[] documentConsentResponseMacFields(
            String runId,
            String requestId,
            int sourceOrdinal,
            String canonicalPath,
            String purpose,
            String physicalIdentityDigest,
            String nonce,
            ConsentDecision decision,
            String controllerObservedProcessPrincipal,
            String declaredCliUiProfile,
            String channel,
            Instant answeredAt) {
        return macFields("document-consent-response-v1", runId, requestId, sourceOrdinal, canonicalPath, purpose,
                physicalIdentityDigest, nonce, decision, controllerObservedProcessPrincipal, declaredCliUiProfile,
                channel, answeredAt);
    }

    public static String[] remoteProviderConsentChallengeMacFields(RemoteProviderConsentChallenge challenge) {
        return remoteProviderConsentChallengeMacFields(
                challenge.runId(), challenge.requestId(), challenge.stage(), challenge.purpose(),
                challenge.contentDigest(), challenge.provenanceDigest(), challenge.provider(), challenge.model(),
                challenge.brokerProfile(), challenge.scope(), challenge.nonce());
    }

    public static String[] remoteProviderConsentChallengeMacFields(
            String runId,
            String requestId,
            ShipStage stage,
            String purpose,
            String contentDigest,
            String provenanceDigest,
            String provider,
            String model,
            String brokerProfile,
            String scope,
            String nonce) {
        return macFields("remote-provider-consent-challenge-v1", runId, requestId, stage, purpose, contentDigest,
                provenanceDigest, provider, model, brokerProfile, scope, nonce);
    }

    public static String[] remoteProviderConsentResponseMacFields(RemoteProviderConsentResponse response) {
        return remoteProviderConsentResponseMacFields(
                response.runId(), response.requestId(), response.stage(), response.purpose(), response.contentDigest(),
                response.provenanceDigest(), response.provider(), response.model(), response.brokerProfile(),
                response.scope(), response.nonce(), response.decision(),
                response.controllerObservedProcessPrincipal(), response.declaredCliUiProfile(), response.channel(),
                response.answeredAt());
    }

    public static String[] remoteProviderConsentResponseMacFields(
            String runId,
            String requestId,
            ShipStage stage,
            String purpose,
            String contentDigest,
            String provenanceDigest,
            String provider,
            String model,
            String brokerProfile,
            String scope,
            String nonce,
            ConsentDecision decision,
            String controllerObservedProcessPrincipal,
            String declaredCliUiProfile,
            String channel,
            Instant answeredAt) {
        return macFields("remote-provider-consent-response-v1", runId, requestId, stage, purpose, contentDigest,
                provenanceDigest, provider, model, brokerProfile, scope, nonce, decision,
                controllerObservedProcessPrincipal, declaredCliUiProfile, channel, answeredAt);
    }

    public static String[] designChallengeMacFields(DesignChallenge challenge) {
        return designChallengeMacFields(
                challenge.runId(), challenge.contextDigest(), challenge.ledgerRevision(), challenge.ledgerDigest(),
                challenge.requirementsDigest(), challenge.policyDigest(), challenge.baselineDigest(),
                challenge.designDigest(), challenge.designReference(), challenge.nonce());
    }

    public static String[] designChallengeMacFields(
            String runId,
            String contextDigest,
            long ledgerRevision,
            String ledgerDigest,
            String requirementsDigest,
            String policyDigest,
            String baselineDigest,
            String designDigest,
            String designReference,
            String nonce) {
        return macFields("design-challenge-v1", runId, contextDigest, ledgerRevision, ledgerDigest,
                requirementsDigest, policyDigest, baselineDigest, designDigest, designReference, nonce);
    }

    public static String[] designResponseMacFields(DesignResponse response) {
        return designResponseMacFields(
                response.runId(), response.contextDigest(), response.ledgerRevision(), response.ledgerDigest(),
                response.requirementsDigest(), response.policyDigest(), response.baselineDigest(),
                response.designDigest(), response.nonce(), response.decision(), response.requestedChanges(),
                response.controllerObservedProcessPrincipal(), response.declaredCliUiProfile(), response.channel(),
                response.answeredAt());
    }

    public static String[] designResponseMacFields(
            String runId,
            String contextDigest,
            long ledgerRevision,
            String ledgerDigest,
            String requirementsDigest,
            String policyDigest,
            String baselineDigest,
            String designDigest,
            String nonce,
            DesignDecision decision,
            String requestedChanges,
            String controllerObservedProcessPrincipal,
            String declaredCliUiProfile,
            String channel,
            Instant answeredAt) {
        return macFields("design-response-v1", runId, contextDigest, ledgerRevision, ledgerDigest,
                requirementsDigest, policyDigest, baselineDigest, designDigest, nonce, decision, requestedChanges,
                controllerObservedProcessPrincipal, declaredCliUiProfile, channel, answeredAt);
    }

    public static String[] planChallengeMacFields(PlanChallenge challenge) {
        return planChallengeMacFields(
                challenge.runId(), challenge.contextDigest(), challenge.ledgerRevision(), challenge.ledgerDigest(),
                challenge.requirementsDigest(), challenge.policyDigest(), challenge.baselineDigest(),
                challenge.approvedDesignDigest(), challenge.planDigest(), challenge.planReference(), challenge.nonce());
    }

    public static String[] planChallengeMacFields(
            String runId,
            String contextDigest,
            long ledgerRevision,
            String ledgerDigest,
            String requirementsDigest,
            String policyDigest,
            String baselineDigest,
            String approvedDesignDigest,
            String planDigest,
            String planReference,
            String nonce) {
        return macFields("plan-challenge-v1", runId, contextDigest, ledgerRevision, ledgerDigest, requirementsDigest,
                policyDigest, baselineDigest, approvedDesignDigest, planDigest, planReference, nonce);
    }

    public static String[] planResponseMacFields(PlanResponse response) {
        return planResponseMacFields(
                response.runId(), response.contextDigest(), response.ledgerRevision(), response.ledgerDigest(),
                response.requirementsDigest(), response.policyDigest(), response.baselineDigest(),
                response.approvedDesignDigest(), response.planDigest(), response.nonce(), response.decision(),
                response.requestedChanges(), response.controllerObservedProcessPrincipal(),
                response.declaredCliUiProfile(), response.channel(), response.answeredAt());
    }

    public static String[] planResponseMacFields(
            String runId,
            String contextDigest,
            long ledgerRevision,
            String ledgerDigest,
            String requirementsDigest,
            String policyDigest,
            String baselineDigest,
            String approvedDesignDigest,
            String planDigest,
            String nonce,
            PlanDecision decision,
            String requestedChanges,
            String controllerObservedProcessPrincipal,
            String declaredCliUiProfile,
            String channel,
            Instant answeredAt) {
        return macFields("plan-response-v1", runId, contextDigest, ledgerRevision, ledgerDigest, requirementsDigest,
                policyDigest, baselineDigest, approvedDesignDigest, planDigest, nonce, decision, requestedChanges,
                controllerObservedProcessPrincipal, declaredCliUiProfile, channel, answeredAt);
    }

    public static String[] waiverChallengeMacFields(WaiverChallenge challenge) {
        return waiverChallengeMacFields(
                challenge.runId(), challenge.checkId(), challenge.evidenceDigest(),
                challenge.eligibilityPolicyDigest(), challenge.subjectDigest(), challenge.subjectReference(),
                challenge.risk(), challenge.consequence(), challenge.nonce());
    }

    public static String[] waiverChallengeMacFields(
            String runId,
            String checkId,
            String evidenceDigest,
            String eligibilityPolicyDigest,
            String subjectDigest,
            String subjectReference,
            String risk,
            String consequence,
            String nonce) {
        return macFields("waiver-challenge-v1", runId, checkId, evidenceDigest, eligibilityPolicyDigest,
                subjectDigest, subjectReference, risk, consequence, nonce);
    }

    public static String[] waiverResponseMacFields(WaiverResponse response) {
        return waiverResponseMacFields(
                response.runId(), response.checkId(), response.evidenceDigest(),
                response.eligibilityPolicyDigest(), response.subjectDigest(), response.nonce(), response.decision(),
                response.reason(), response.controllerObservedProcessPrincipal(), response.declaredCliUiProfile(),
                response.channel(), response.answeredAt());
    }

    public static String[] waiverResponseMacFields(
            String runId,
            String checkId,
            String evidenceDigest,
            String eligibilityPolicyDigest,
            String subjectDigest,
            String nonce,
            WaiverDecision decision,
            String reason,
            String controllerObservedProcessPrincipal,
            String declaredCliUiProfile,
            String channel,
            Instant answeredAt) {
        return macFields("waiver-response-v1", runId, checkId, evidenceDigest, eligibilityPolicyDigest,
                subjectDigest, nonce, decision, reason, controllerObservedProcessPrincipal, declaredCliUiProfile,
                channel, answeredAt);
    }

    private static String[] macFields(String domain, Object... values) {
        String[] fields = new String[values.length + 1];
        fields[0] = domain;
        for (int index = 0; index < values.length; index++) {
            Object value = values[index];
            fields[index + 1] = value == null ? null : value instanceof Enum<?> item ? item.name() : value.toString();
        }
        return fields;
    }

    private static void requireCommon(
            int schemaVersion,
            String runId,
            long ledgerRevision,
            String identity,
            String nonce,
            String mac,
            String label) {
        requireEnvelope(schemaVersion, runId, identity, nonce, mac, label);
        if (ledgerRevision < 1) {
            throw new IllegalArgumentException("Interaction ledger revision must be positive");
        }
    }

    private static void requireEnvelope(
            int schemaVersion,
            String runId,
            String identity,
            String nonce,
            String mac,
            String label) {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported " + label + " schema: " + schemaVersion);
        }
        requireText(runId, MAX_ID_CHARS, "Interaction run ID");
        requireText(identity, MAX_ID_CHARS, "Interaction identity");
        if (nonce == null || nonce.length() != NONCE_CHARS || !nonce.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("Interaction nonce must be a 32-byte base64url value");
        }
        if (!ShipDigest.isHmacSha256(mac)) {
            throw new IllegalArgumentException("Interaction MAC must be an HMAC-SHA-256 value");
        }
    }

    private static void requireApprovalBindings(
            int schemaVersion,
            String runId,
            String contextDigest,
            long ledgerRevision,
            String ledgerDigest,
            String requirementsDigest,
            String policyDigest,
            String baselineDigest,
            String nonce,
            String mac,
            String label) {
        requireEnvelope(schemaVersion, runId, label, nonce, mac, label);
        if (ledgerRevision < 1) {
            throw new IllegalArgumentException("Interaction ledger revision must be positive");
        }
        requireDigest(contextDigest, "Interaction context digest");
        requireDigest(ledgerDigest, "Interaction ledger digest");
        requireDigest(requirementsDigest, "Interaction requirements digest");
        requireDigest(policyDigest, "Interaction policy digest");
        requireDigest(baselineDigest, "Interaction baseline digest");
    }

    private static void requireResponder(
            String controllerObservedProcessPrincipal,
            String declaredCliUiProfile,
            String channel,
            Instant answeredAt,
            String label) {
        requireText(controllerObservedProcessPrincipal, MAX_REFERENCE_CHARS,
                label + " controller-observed process principal");
        requireText(declaredCliUiProfile, MAX_REFERENCE_CHARS, label + " declared CLI UI profile");
        requireText(channel, MAX_CHANNEL_CHARS, label + " channel");
        if (answeredAt == null) {
            throw new IllegalArgumentException(label + " timestamp is required");
        }
    }

    private static void requireDecision(Object decision, String label) {
        if (decision == null) {
            throw new IllegalArgumentException(label + " decision is required");
        }
    }

    private static String requireRequestedChanges(String value, boolean required, String label) {
        requireOptionalText(value, MAX_RESPONSE_CHARS, label + " requested changes");
        if (required && (value == null || value.isBlank())) {
            throw new IllegalArgumentException(label + " change request requires requested changes");
        }
        if (!required && value != null && !value.isBlank()) {
            throw new IllegalArgumentException(label + " requested changes require a change-request decision");
        }
        return required ? value : "";
    }

    private static void requireDigest(String value, String label) {
        if (!ShipDigest.isSha256(value)) {
            throw new IllegalArgumentException(label + " must be a SHA-256 content address");
        }
    }

    private static void requireText(String value, int maximum, String label) {
        if (value == null || value.isBlank() || value.length() > maximum) {
            throw new IllegalArgumentException(label + " must contain between 1 and " + maximum + " characters");
        }
    }

    private static void requireOptionalText(String value, int maximum, String label) {
        if (value != null && value.length() > maximum) {
            throw new IllegalArgumentException(label + " must contain at most " + maximum + " characters");
        }
    }
}
