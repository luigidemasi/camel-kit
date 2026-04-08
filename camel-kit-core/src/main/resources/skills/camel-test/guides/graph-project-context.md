# Graph Project Context — Testing

> **Prerequisites:** See `shared/graph-availability.md` for availability check and fallback rules.
> **Runs as:** Step 0.5 in route-analysis.md, between Step 0 (Citrus Quick Reference) and Step 1 (Analyze Route with MCP).
> **Output:** ROUTE_CONTEXT variables consumed by `test-generation.md`.

---

## Step 0.5: Route Context from Graph

### 0.5.0 — Run Composite Command

Read `.camel-kit/config.yaml` to get the `command-prefix` field (default: `camel-kit`).

The `routeId` is derived from the route's `id:` field (e.g., `order-process`).

Run the composite command:
```bash
{COMMAND_PREFIX} graph route-context <routeId>
```

This returns a JSON object with all route context in one call:
```json
{
  "upstreamRoutes": [...],
  "downstreamRoutes": [...],
  "endpointClassification": {...},
  "errorFlow": [...]
}
```

If the command exits with code != 0, skip all graph-enhanced test generation steps and proceed without route context (manual analysis only).

### 0.5.1 — Upstream and Downstream Routes

Extract from JSON response:
- `upstreamRoutes` = list of route IDs that produce to this route
- `downstreamRoutes` = list of route IDs that consume from this route

Record:
- `UPSTREAM_ROUTES` = response.upstreamRoutes
- `DOWNSTREAM_ROUTES` = response.downstreamRoutes

Use in `test-generation.md` Step 2.1 (Extract Test Scenarios):
- If `UPSTREAM_ROUTES` is non-empty, add a test scenario: "End-to-end: message from [upstream route] flows through [this route] to [downstream route]"
- If `DOWNSTREAM_ROUTES` is non-empty, add a test scenario: "Verify downstream propagation: output of [this route] is consumed by [downstream route IDs]"

### 0.5.2 — Endpoint Classification

Extract from JSON response:
- `endpointClassification` = map of endpointUri → category

Categories:
- **INTERNAL:** scheme is `direct:` or `seda:` → no mock or testcontainer needed, Camel handles internally
- **EXTERNAL_INFRA:** scheme is `kafka:`, `sql:`, `mongodb:`, `amqp:`, `jms:` → needs testcontainer
- **EXTERNAL_API:** scheme is `http:`, `https:`, `rest:`, `platform-http:` → needs mock endpoint or WireMock

Record:
- `ENDPOINT_CLASSIFICATION` = response.endpointClassification

Use in `test-generation.md` Step 2.2 (Identify Testcontainers):
- Only provision testcontainers for `EXTERNAL_INFRA` endpoints
- Only provision mocks for `EXTERNAL_API` endpoints
- `INTERNAL` endpoints need neither — reduces test setup overhead

### 0.5.3 — Error Propagation Paths

Extract from JSON response:
- `errorFlow` = ordered list of processor → error handler mapping

Record:
- `ERROR_FLOW` = response.errorFlow

Use in `test-generation.md` Step 2.1:
- For each error handler boundary, add an error scenario test
- If DLQ is on a downstream route (not this one), note it: "DLQ verification requires downstream route [routeId] to be active in the test"
