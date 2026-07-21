package io.github.luigidemasi.camelkit.ship.controller;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import io.github.luigidemasi.camelkit.ship.ShipDigest;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.DesignChallenge;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.DesignResponse;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.DiscoveryAnswer;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.DiscoveryChallenge;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.DocumentConsentChallenge;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.DocumentConsentResponse;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.PlanChallenge;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.PlanResponse;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.RemoteProviderConsentChallenge;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.RemoteProviderConsentResponse;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.WaiverChallenge;
import io.github.luigidemasi.camelkit.ship.protocol.Interaction.WaiverResponse;

/** Ordered controller-recorded interactions for one exact initial-context revision. */
public record ShipInteractionBundle(
        int schemaVersion,
        String runId,
        long contextRevision,
        String contextDigest,
        List<Exchange> exchanges) {

    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_EXCHANGES = 256;
    public static final int MAX_SERIALIZED_BYTES = 4 * 1024 * 1024;

    public ShipInteractionBundle {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported Ship interaction-bundle schema: " + schemaVersion);
        }
        if (runId == null || !runId.matches("ship-[0-9a-f]{32}")) {
            throw new IllegalArgumentException("Interaction-bundle run ID must be canonical");
        }
        if (contextRevision < 1) {
            throw new IllegalArgumentException("Interaction-bundle context revision must be positive");
        }
        if (!ShipDigest.isSha256(contextDigest)) {
            throw new IllegalArgumentException("Interaction-bundle context digest must be a SHA-256 content address");
        }
        exchanges = exchanges == null ? List.of() : List.copyOf(exchanges);
        if (exchanges.size() > MAX_EXCHANGES) {
            throw new IllegalArgumentException(
                    "Interaction bundle may contain at most " + MAX_EXCHANGES + " exchanges");
        }
        boolean pending = false;
        for (int index = 0; index < exchanges.size(); index++) {
            Exchange exchange = Objects.requireNonNull(exchanges.get(index), "interaction exchange");
            if (exchange.ordinal() != index + 1) {
                throw new IllegalArgumentException("Interaction-bundle ordinals must be contiguous from 1");
            }
            if (pending) {
                throw new IllegalArgumentException("Only the final interaction exchange may be pending");
            }
            exchange.validate(runId);
            pending = exchange.pending();
        }
    }

    /** Exactly one typed challenge/response pair. */
    public record Exchange(
            int ordinal,
            DiscoveryPair discovery,
            DocumentConsentPair documentConsent,
            RemoteProviderConsentPair remoteProviderConsent,
            DesignPair design,
            PlanPair plan,
            WaiverPair waiver) {

        public Exchange {
            if (ordinal < 1) {
                throw new IllegalArgumentException("Interaction exchange ordinal must be positive");
            }
            long variants = Stream.of(
                    discovery, documentConsent, remoteProviderConsent, design, plan, waiver)
                    .filter(Objects::nonNull)
                    .count();
            if (variants != 1) {
                throw new IllegalArgumentException("Interaction exchange must contain exactly one typed pair");
            }
        }

        static Exchange discovery(int ordinal, DiscoveryChallenge challenge) {
            return new Exchange(ordinal, new DiscoveryPair(challenge, null), null, null, null, null, null);
        }

        static Exchange documentConsent(int ordinal, DocumentConsentChallenge challenge) {
            return new Exchange(ordinal, null, new DocumentConsentPair(challenge, null), null, null, null, null);
        }

        static Exchange remoteProviderConsent(int ordinal, RemoteProviderConsentChallenge challenge) {
            return new Exchange(
                    ordinal, null, null, new RemoteProviderConsentPair(challenge, null), null, null, null);
        }

        static Exchange design(int ordinal, DesignChallenge challenge) {
            return new Exchange(ordinal, null, null, null, new DesignPair(challenge, null), null, null);
        }

        static Exchange plan(int ordinal, PlanChallenge challenge) {
            return new Exchange(ordinal, null, null, null, null, new PlanPair(challenge, null), null);
        }

        static Exchange waiver(int ordinal, WaiverChallenge challenge) {
            return new Exchange(ordinal, null, null, null, null, null, new WaiverPair(challenge, null));
        }

        Exchange answer(DiscoveryAnswer answer) {
            if (discovery == null || discovery.answer() != null) {
                throw new IllegalStateException("No discovery challenge is awaiting an answer");
            }
            return new Exchange(
                    ordinal, new DiscoveryPair(discovery.challenge(), answer), null, null, null, null, null);
        }

        Exchange respond(DocumentConsentResponse response) {
            if (documentConsent == null || documentConsent.response() != null) {
                throw new IllegalStateException("No document-consent challenge is awaiting a response");
            }
            return new Exchange(
                    ordinal,
                    null,
                    new DocumentConsentPair(documentConsent.challenge(), response),
                    null,
                    null,
                    null,
                    null);
        }

        Exchange respond(RemoteProviderConsentResponse response) {
            if (remoteProviderConsent == null || remoteProviderConsent.response() != null) {
                throw new IllegalStateException("No remote-provider consent challenge is awaiting a response");
            }
            return new Exchange(
                    ordinal,
                    null,
                    null,
                    new RemoteProviderConsentPair(remoteProviderConsent.challenge(), response),
                    null,
                    null,
                    null);
        }

        Exchange respond(DesignResponse response) {
            if (design == null || design.response() != null) {
                throw new IllegalStateException("No design challenge is awaiting a response");
            }
            return new Exchange(
                    ordinal, null, null, null, new DesignPair(design.challenge(), response), null, null);
        }

        Exchange respond(PlanResponse response) {
            if (plan == null || plan.response() != null) {
                throw new IllegalStateException("No plan challenge is awaiting a response");
            }
            return new Exchange(
                    ordinal, null, null, null, null, new PlanPair(plan.challenge(), response), null);
        }

        Exchange respond(WaiverResponse response) {
            if (waiver == null || waiver.response() != null) {
                throw new IllegalStateException("No waiver challenge is awaiting a response");
            }
            return new Exchange(
                    ordinal, null, null, null, null, null, new WaiverPair(waiver.challenge(), response));
        }

        boolean pending() {
            return discovery != null ? discovery.answer() == null
                    : documentConsent != null ? documentConsent.response() == null
                    : remoteProviderConsent != null ? remoteProviderConsent.response() == null
                    : design != null ? design.response() == null
                    : plan != null ? plan.response() == null
                    : waiver.response() == null;
        }

        String runId() {
            return discovery != null ? discovery.challenge().runId()
                    : documentConsent != null ? documentConsent.challenge().runId()
                    : remoteProviderConsent != null ? remoteProviderConsent.challenge().runId()
                    : design != null ? design.challenge().runId()
                    : plan != null ? plan.challenge().runId()
                    : waiver.challenge().runId();
        }

        private void validate(String runId) {
            if (discovery != null) {
                ShipInteractionBundle.validate(runId, discovery.challenge(), discovery.answer());
            } else if (documentConsent != null) {
                ShipInteractionBundle.validate(
                        runId, documentConsent.challenge(), documentConsent.response());
            } else if (remoteProviderConsent != null) {
                ShipInteractionBundle.validate(
                        runId, remoteProviderConsent.challenge(), remoteProviderConsent.response());
            } else if (design != null) {
                ShipInteractionBundle.validate(runId, design.challenge(), design.response());
            } else if (plan != null) {
                ShipInteractionBundle.validate(runId, plan.challenge(), plan.response());
            } else {
                ShipInteractionBundle.validate(runId, waiver.challenge(), waiver.response());
            }
        }
    }

    public record DiscoveryPair(DiscoveryChallenge challenge, DiscoveryAnswer answer) {
        public DiscoveryPair {
            Objects.requireNonNull(challenge, "discovery challenge");
        }
    }

    public record DocumentConsentPair(
            DocumentConsentChallenge challenge, DocumentConsentResponse response) {
        public DocumentConsentPair {
            Objects.requireNonNull(challenge, "document-consent challenge");
        }
    }

    public record RemoteProviderConsentPair(
            RemoteProviderConsentChallenge challenge, RemoteProviderConsentResponse response) {
        public RemoteProviderConsentPair {
            Objects.requireNonNull(challenge, "remote-provider consent challenge");
        }
    }

    public record DesignPair(DesignChallenge challenge, DesignResponse response) {
        public DesignPair {
            Objects.requireNonNull(challenge, "design challenge");
        }
    }

    public record PlanPair(PlanChallenge challenge, PlanResponse response) {
        public PlanPair {
            Objects.requireNonNull(challenge, "plan challenge");
        }
    }

    public record WaiverPair(WaiverChallenge challenge, WaiverResponse response) {
        public WaiverPair {
            Objects.requireNonNull(challenge, "waiver challenge");
        }
    }

    private static void validate(String runId, DiscoveryChallenge challenge, DiscoveryAnswer answer) {
        requireInteraction(runId, challenge.schemaVersion(), challenge.runId(), "discovery challenge");
        if (answer != null) {
            requireInteraction(runId, answer.schemaVersion(), answer.runId(), "discovery answer");
            requireMatch(
                    challenge.ledgerRevision() == answer.ledgerRevision()
                            && Objects.equals(challenge.questionId(), answer.questionId())
                            && Objects.equals(challenge.openItemId(), answer.openItemId())
                            && Objects.equals(challenge.nonce(), answer.nonce()),
                    "Discovery answer");
        }
    }

    private static void validate(
            String runId, DocumentConsentChallenge challenge, DocumentConsentResponse response) {
        requireInteraction(runId, challenge.schemaVersion(), challenge.runId(), "document-consent challenge");
        if (response != null) {
            requireInteraction(runId, response.schemaVersion(), response.runId(), "document-consent response");
            requireMatch(
                    challenge.sourceOrdinal() == response.sourceOrdinal()
                            && Objects.equals(challenge.requestId(), response.requestId())
                            && Objects.equals(challenge.canonicalPath(), response.canonicalPath())
                            && Objects.equals(challenge.purpose(), response.purpose())
                            && Objects.equals(challenge.physicalIdentityDigest(), response.physicalIdentityDigest())
                            && Objects.equals(challenge.nonce(), response.nonce()),
                    "Document-consent response");
        }
    }

    private static void validate(
            String runId,
            RemoteProviderConsentChallenge challenge,
            RemoteProviderConsentResponse response) {
        requireInteraction(
                runId, challenge.schemaVersion(), challenge.runId(), "remote-provider consent challenge");
        if (response != null) {
            requireInteraction(
                    runId, response.schemaVersion(), response.runId(), "remote-provider consent response");
            requireMatch(
                    challenge.stage() == response.stage()
                            && Objects.equals(challenge.requestId(), response.requestId())
                            && Objects.equals(challenge.purpose(), response.purpose())
                            && Objects.equals(challenge.contentDigest(), response.contentDigest())
                            && Objects.equals(challenge.provenanceDigest(), response.provenanceDigest())
                            && Objects.equals(challenge.provider(), response.provider())
                            && Objects.equals(challenge.model(), response.model())
                            && Objects.equals(challenge.brokerProfile(), response.brokerProfile())
                            && Objects.equals(challenge.scope(), response.scope())
                            && Objects.equals(challenge.nonce(), response.nonce()),
                    "Remote-provider consent response");
        }
    }

    private static void validate(String runId, DesignChallenge challenge, DesignResponse response) {
        requireInteraction(runId, challenge.schemaVersion(), challenge.runId(), "design challenge");
        if (response != null) {
            requireInteraction(runId, response.schemaVersion(), response.runId(), "design response");
            requireMatch(
                    challenge.ledgerRevision() == response.ledgerRevision()
                            && Objects.equals(challenge.contextDigest(), response.contextDigest())
                            && Objects.equals(challenge.ledgerDigest(), response.ledgerDigest())
                            && Objects.equals(challenge.requirementsDigest(), response.requirementsDigest())
                            && Objects.equals(challenge.policyDigest(), response.policyDigest())
                            && Objects.equals(challenge.baselineDigest(), response.baselineDigest())
                            && Objects.equals(challenge.designDigest(), response.designDigest())
                            && Objects.equals(challenge.nonce(), response.nonce()),
                    "Design response");
        }
    }

    private static void validate(String runId, PlanChallenge challenge, PlanResponse response) {
        requireInteraction(runId, challenge.schemaVersion(), challenge.runId(), "plan challenge");
        if (response != null) {
            requireInteraction(runId, response.schemaVersion(), response.runId(), "plan response");
            requireMatch(
                    challenge.ledgerRevision() == response.ledgerRevision()
                            && Objects.equals(challenge.contextDigest(), response.contextDigest())
                            && Objects.equals(challenge.ledgerDigest(), response.ledgerDigest())
                            && Objects.equals(challenge.requirementsDigest(), response.requirementsDigest())
                            && Objects.equals(challenge.policyDigest(), response.policyDigest())
                            && Objects.equals(challenge.baselineDigest(), response.baselineDigest())
                            && Objects.equals(challenge.approvedDesignDigest(), response.approvedDesignDigest())
                            && Objects.equals(challenge.planDigest(), response.planDigest())
                            && Objects.equals(challenge.nonce(), response.nonce()),
                    "Plan response");
        }
    }

    private static void validate(String runId, WaiverChallenge challenge, WaiverResponse response) {
        requireInteraction(runId, challenge.schemaVersion(), challenge.runId(), "waiver challenge");
        if (response != null) {
            requireInteraction(runId, response.schemaVersion(), response.runId(), "waiver response");
            requireMatch(
                    Objects.equals(challenge.checkId(), response.checkId())
                            && Objects.equals(challenge.evidenceDigest(), response.evidenceDigest())
                            && Objects.equals(
                                    challenge.eligibilityPolicyDigest(), response.eligibilityPolicyDigest())
                            && Objects.equals(challenge.subjectDigest(), response.subjectDigest())
                            && Objects.equals(challenge.nonce(), response.nonce()),
                    "Waiver response");
        }
    }

    private static void requireInteraction(
            String runId, int schemaVersion, String interactionRunId, String label) {
        if (schemaVersion != Interaction.SCHEMA_VERSION || !runId.equals(interactionRunId)) {
            throw new IllegalArgumentException("Invalid " + label + " in interaction bundle");
        }
    }

    private static void requireMatch(boolean matches, String label) {
        if (!matches) {
            throw new IllegalArgumentException(label + " does not match its challenge");
        }
    }
}
