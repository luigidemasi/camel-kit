package io.github.luigidemasi.camelkit.ship.ledger;

import java.util.List;

/** Raised when a discovery worker proposes an invalid or incomplete ledger. */
public class LedgerValidationException extends IllegalArgumentException {

    private final List<String> violations;

    public LedgerValidationException(List<String> violations) {
        super(String.join("; ", violations));
        this.violations = List.copyOf(violations);
    }

    public List<String> violations() {
        return violations;
    }
}
