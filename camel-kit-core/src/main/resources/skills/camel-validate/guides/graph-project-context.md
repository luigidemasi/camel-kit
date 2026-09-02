# Graph Project Context — Validation

> **Prerequisites:** See `shared/graph-availability.md` for availability check and fallback rules.
> **Runs before:** Stages 4–7 (completeness, correctness, constitution, configuration).
> **Output:** PROJECT_NORMS context block consumed by `quality-checks.md` and `anti-patterns.md`.

---

## Step 0: Project Norms Collection

### 0.0 — Run Composite Command

Apply `shared/graph-availability.md`; use only its install-time fixed command prefix and discrete argv.

Run the composite command:
```bash
{COMMAND_PREFIX} graph project-norms
```

This returns a JSON object with all project norms in one call:
```json
{
  "naming": {
    "routeIds": ["order-create", "order-validate", "legacyRoute"],
    "detectedPattern": "kebab-case",
    "majorityPercentage": 67
  },
  "errorHandling": {
    "totalRoutes": 3,
    "routesWithErrorHandling": 2,
    "coverage": 66.7
  },
  "properties": {
    "patterns": ["camel.component.kafka.*", "orders.*"],
    "count": 8
  },
  "stepCounts": {
    "values": [3, 5, 8],
    "p75": 8,
    "median": 5,
    "max": 8
  }
}
```

If the command exits with code != 0, skip all graph-enhanced validation steps and proceed without project norms (use defaults from `quality-checks.md`).

### 0.1 — Route Naming Convention

Extract from JSON response:
- `naming.detectedPattern` = the detected `kebab-case` or `camelCase` pattern
- `naming.majorityPercentage` = percentage of route IDs matching that pattern
- `naming.routeIds` = route IDs from which to select up to 3 matching examples

Record:
- `NAMING_PATTERN` = response.naming.detectedPattern only when `response.naming.majorityPercentage >= 60`
- `NAMING_EXAMPLES` = up to 3 values from response.naming.routeIds that match `NAMING_PATTERN`

If fewer than 60% of route IDs match the detected pattern, fall back to the default `{domain}-{action}` convention from `quality-checks.md`.

### 0.2 — Error Handling Baseline

Extract from JSON response:
- `errorHandling.totalRoutes` = number of routes analyzed
- `errorHandling.routesWithErrorHandling` = routes with a detected error-handling neighbor
- `errorHandling.coverage` = percentage of routes with detected error handling

Record:
- `ERROR_HANDLING_COVERAGE` = response.errorHandling.coverage

### 0.3 — Property Naming Patterns

Extract from JSON response:
- `properties.patterns` = sorted list of detected property-prefix patterns
- `properties.count` = number of configuration-property nodes

Record:
- `PROPERTY_PATTERNS` = response.properties.patterns
- `PROPERTY_COUNT` = response.properties.count

### 0.4 — Structural Baseline

Extract from JSON response:
- `stepCounts.p75` = 75th percentile of traced route-step counts
- `stepCounts.median` and `stepCounts.max` = additional project baselines

Record:
- `STEP_COUNT_P75` = response.stepCounts.p75
- `STEP_COUNT_MEDIAN` = response.stepCounts.median
- `STEP_COUNT_MAX` = response.stepCounts.max

This replaces the hardcoded "7 steps" threshold in constitution Rule 2 and the "10 steps" threshold in anti-pattern God Route detection.

`project-norms` does not emit structural warnings. Use the dedicated graph dead-code analysis where that stage calls for it.
