package io.github.luigidemasi.camelkit.ship.controller;

/** The single controller-owned interaction that can block a Ship run. */
public enum ShipInteractionKind {
    DOCUMENT_READ_CONSENT("document-read-consent"),
    REMOTE_USE_CONSENT("remote-use-consent"),
    DISCOVERY_ANSWER("discovery-answer"),
    DESIGN_APPROVAL("design-approval"),
    PLAN_APPROVAL("plan-approval"),
    WAIVER("waiver");

    private final String stableId;

    ShipInteractionKind(String stableId) {
        this.stableId = stableId;
    }

    public String stableId() {
        return stableId;
    }

    public static ShipInteractionKind fromStableId(String stableId) {
        for (ShipInteractionKind kind : values()) {
            if (kind.stableId.equals(stableId)) {
                return kind;
            }
        }
        throw new IllegalArgumentException("Unknown Ship interaction kind: " + stableId);
    }
}
