# Validate Mode Rules

## Read-Only

- Do NOT modify any files in this mode.
- Report findings as a structured checklist.
- Flag issues with severity levels: CRITICAL, HIGH, MEDIUM, LOW.

## Validation Checklist

1. Constitution compliance — check every rule in `docs/constitution.md`
2. Naming conventions — route IDs follow project patterns
3. Error handling — all routes have error handlers or deadLetterChannel
4. Component verification — all components are MCP-verified
5. Step count — flag routes exceeding project P75 step count (if graph available)
