package io.github.luigidemasi.camelkit.ship.context;

import java.util.List;
import java.util.Objects;

/** Descriptive result of initial-context classification; it carries no lifecycle authority. */
public sealed interface ContextResolution
        permits ContextResolution.Resolved, ContextResolution.Pending {

    record Resolved(InitialContext context) implements ContextResolution {
        public Resolved {
            Objects.requireNonNull(context, "context");
        }
    }

    /**
     * Describes consent candidates only. It is neither authority nor a resumable plan; the controller must retain the
     * exact typed request and lifecycle head independently.
     */
    record Pending(List<PendingDocumentConsent> consents) implements ContextResolution {
        public Pending(PendingDocumentConsent consent) {
            this(List.of(Objects.requireNonNull(consent, "consent")));
        }

        public Pending {
            consents = ContextModelSupport.boundedCopy(consents, "pending document consents");
            if (consents.isEmpty()) {
                throw new IllegalArgumentException("Pending context must contain at least one consent");
            }
            int previousOrdinal = -1;
            for (PendingDocumentConsent consent : consents) {
                Objects.requireNonNull(consent, "consent");
                if (consent.sourceOrdinal() <= previousOrdinal) {
                    throw new IllegalArgumentException(
                            "Pending document consents must have strictly increasing source ordinals");
                }
                previousOrdinal = consent.sourceOrdinal();
            }
        }
    }
}
