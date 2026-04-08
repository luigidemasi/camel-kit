# Graph Project Context — Validation

> **Prerequisites:** See `shared/graph-availability.md` for availability check and fallback rules.
> **Runs before:** Stages 4–7 (completeness, correctness, constitution, configuration).
> **Output:** PROJECT_NORMS context block consumed by `quality-checks.md` and `anti-patterns.md`.

---

## Step 0: Project Norms Collection

### 0.1 — Route Naming Convention

Call `graph_find(type="CAMEL_ROUTE")` to get all existing route IDs.

Extract the dominant naming pattern:
- Count routes matching `{domain}-{action}` (kebab-case, 2+ segments). Regex: `^[a-z][a-z0-9]*(-[a-z][a-z0-9]*)+$`
- Count routes matching `{service}-{verb}-{qualifier}` (3+ segments). Regex: `^[a-z][a-z0-9]*(-[a-z][a-z0-9]*){2,}$`
- Count routes matching other patterns

Record:
- `NAMING_PATTERN` = the pattern used by ≥60% of routes
- `NAMING_EXAMPLES` = 3 representative route IDs from the dominant pattern

If no dominant pattern (no pattern reaches 60%): fall back to the default `{domain}-{action}` convention from `quality-checks.md`.

### 0.2 — Error Handling Baseline

Call `graph_find(type="CAMEL_ROUTE")` and for each route, call `graph_neighbors(routeId, "out")` to detect outgoing edges. Look for edges pointing to endpoints with `dead` or `dlq` in the URI, or to processors of type `onException` or `errorHandler`.

Compute:
- % of routes with DLC (deadLetterChannel)
- % of routes with onException
- % of routes with no error handling

Record:
- `ERROR_HANDLING_NORM` = the strategy used by the majority (DLC, onException, or none)
- `ERROR_HANDLING_COVERAGE` = percentage of routes with any error handling

### 0.3 — Property Naming Patterns

Call `graph_find(type="CONFIG_PROPERTY")` to get all existing properties.

Extract naming patterns:
- Camel component properties: `camel.component.{scheme}.{prop}`
- Custom properties: extract common prefixes and conventions (e.g., `kafka.topic.*` vs `kafka.topics.*`, singular vs plural)

Record:
- `PROPERTY_PATTERNS` = map of prefix → count

### 0.4 — Structural Baseline

Call `graph_stats` for node/edge counts.
Call `graph_route_topology` for route connectivity.

For each route, count the number of `PROCESSES` edges (outgoing from the route). This is the processing step count.

Compute:
- Average processing steps per route
- Median processing steps per route
- Max processing steps across all routes
- % of routes using `direct:`/`seda:` internal routing

Record:
- `STEP_COUNT_P75` = 75th percentile of processing step counts

This replaces the hardcoded "7 steps" threshold in constitution Rule 2 and the "10 steps" threshold in anti-pattern God Route detection.

### 0.5 — Structural Warnings

From the topology data, detect:
- **Orphaned routes:** Routes that `ROUTES_TO` a `direct:`/`seda:` endpoint that no other route consumes from
- **Broken references:** Routes that `ROUTES_FROM` a `direct:`/`seda:` endpoint that no other route produces to

Record:
- `STRUCTURAL_WARNINGS` = list of issues found

Display structural warnings inline:

```
== STRUCTURAL WARNINGS (from project graph) ==

⚠️ Orphaned endpoint: direct:legacyProcess — produced by route "order-legacy" but no route consumes it
⚠️ Broken reference: direct:missingHandler — route "error-dispatch" consumes it but no route produces to it
```
