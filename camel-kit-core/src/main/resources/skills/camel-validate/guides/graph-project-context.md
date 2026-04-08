# Graph Project Context — Validation

> **Prerequisites:** See `shared/graph-availability.md` for availability check and fallback rules.
> **Runs before:** Stages 4–7 (completeness, correctness, constitution, configuration).
> **Output:** PROJECT_NORMS context block consumed by `quality-checks.md` and `anti-patterns.md`.

---

## Step 0: Project Norms Collection

### 0.0 — Run Composite Command

Read `.camel-kit/config.yaml` to get the `command-prefix` field (default: `camel-kit`).

Run the composite command:
```bash
{COMMAND_PREFIX} graph project-norms
```

This returns a JSON object with all project norms in one call:
```json
{
  "namingPattern": "...",
  "namingExamples": ["route1", "route2", "route3"],
  "errorHandlingNorm": "...",
  "errorHandlingCoverage": 0.75,
  "propertyPatterns": {...},
  "stepCountP75": 5,
  "structuralWarnings": [...]
}
```

If the command exits with code != 0, skip all graph-enhanced validation steps and proceed without project norms (use defaults from `quality-checks.md`).

### 0.1 — Route Naming Convention

Extract from JSON response:
- `namingPattern` = the pattern used by ≥60% of routes
- `namingExamples` = 3 representative route IDs from the dominant pattern

Record:
- `NAMING_PATTERN` = response.namingPattern
- `NAMING_EXAMPLES` = response.namingExamples

If no dominant pattern (field is null): fall back to the default `{domain}-{action}` convention from `quality-checks.md`.

### 0.2 — Error Handling Baseline

Extract from JSON response:
- `errorHandlingNorm` = the strategy used by the majority (DLC, onException, or none)
- `errorHandlingCoverage` = percentage of routes with any error handling

Record:
- `ERROR_HANDLING_NORM` = response.errorHandlingNorm
- `ERROR_HANDLING_COVERAGE` = response.errorHandlingCoverage

### 0.3 — Property Naming Patterns

Extract from JSON response:
- `propertyPatterns` = map of prefix → count

Record:
- `PROPERTY_PATTERNS` = response.propertyPatterns

### 0.4 — Structural Baseline

Extract from JSON response:
- `stepCountP75` = 75th percentile of processing step counts

Record:
- `STEP_COUNT_P75` = response.stepCountP75

This replaces the hardcoded "7 steps" threshold in constitution Rule 2 and the "10 steps" threshold in anti-pattern God Route detection.

### 0.5 — Structural Warnings

Extract from JSON response:
- `structuralWarnings` = list of issues found

Record:
- `STRUCTURAL_WARNINGS` = response.structuralWarnings

Display structural warnings inline:

```
== STRUCTURAL WARNINGS (from project graph) ==

⚠️ Orphaned endpoint: direct:legacyProcess — produced by route "order-legacy" but no route consumes it
⚠️ Broken reference: direct:missingHandler — route "error-dispatch" consumes it but no route produces to it
```
