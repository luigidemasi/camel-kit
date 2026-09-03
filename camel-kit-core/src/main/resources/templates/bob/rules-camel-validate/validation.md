# Validate Mode Rules

## Report-Only

- Do NOT modify route, configuration, application, or test files in this mode.
- Write findings only to the report path selected by the validation gate.
- Report findings as a structured checklist.
- Flag issues with severity levels: CRITICAL, HIGH, MEDIUM, LOW.

## Validation Checklist

1. Constitution compliance — check every rule in `docs/constitution.md`
2. Naming conventions — route IDs follow project patterns
3. Error handling — all routes have error handlers or deadLetterChannel
4. Component verification — all components are MCP-verified
5. Step count — flag routes exceeding project P75 step count (if graph available)

## Security Classification

- Load `.bob/skills/shared/camel-security-checklist.md` and apply all five rules.
- The authentication rule requires caller authentication on externally exposed inbound HTTP/REST endpoints.
- Every confirmed violation of `.bob/skills/shared/camel-security-checklist.md` is `CRITICAL`, makes the Security Analysis
  category `FAIL`, and makes Overall Status `FAIL`.
