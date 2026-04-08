# Graph Project Context — Testing

> **Prerequisites:** See `shared/graph-availability.md` for availability check and fallback rules.
> **Runs as:** Step 0.5 in route-analysis.md, between Step 0 (Citrus Quick Reference) and Step 1 (Analyze Route with MCP).
> **Output:** ROUTE_CONTEXT variables consumed by `test-generation.md`.

---

## Step 0.5: Route Context from Graph

### 0.5.1 — Upstream and Downstream Routes

Call `graph_impact(routeId, "upstream")` to find what feeds into this route. The `routeId` is derived from the route's `id:` field, prefixed with `route:` (e.g., `route:order-process`).

Call `graph_impact(routeId, "downstream")` to find what this route feeds into.

Record:
- `UPSTREAM_ROUTES` = list of route IDs that produce to this route
- `DOWNSTREAM_ROUTES` = list of route IDs that consume from this route

Use in `test-generation.md` Step 2.1 (Extract Test Scenarios):
- If `UPSTREAM_ROUTES` is non-empty, add a test scenario: "End-to-end: message from [upstream route] flows through [this route] to [downstream route]"
- If `DOWNSTREAM_ROUTES` is non-empty, add a test scenario: "Verify downstream propagation: output of [this route] is consumed by [downstream route IDs]"

### 0.5.2 — Endpoint Classification

Call `graph_neighbors(routeId, "out")` to get all endpoints this route connects to (both `ROUTES_FROM` and `ROUTES_TO` edges).

Classify each endpoint by its scheme:
- **INTERNAL:** scheme is `direct:` or `seda:` → no mock or testcontainer needed, Camel handles internally
- **EXTERNAL_INFRA:** scheme is `kafka:`, `sql:`, `mongodb:`, `amqp:`, `jms:` → needs testcontainer
- **EXTERNAL_API:** scheme is `http:`, `https:`, `rest:`, `platform-http:` → needs mock endpoint or WireMock

Record:
- `ENDPOINT_CLASSIFICATION` = map of endpointUri → category

Use in `test-generation.md` Step 2.2 (Identify Testcontainers):
- Only provision testcontainers for `EXTERNAL_INFRA` endpoints
- Only provision mocks for `EXTERNAL_API` endpoints
- `INTERNAL` endpoints need neither — reduces test setup overhead

### 0.5.3 — Error Propagation Paths

Call `graph_route_flow(routeId)` to trace the full message path.

Identify error handling boundaries:
- Which processors in the flow can throw exceptions (external calls, transformations)
- Where error handlers are defined (on this route vs upstream route)
- Where DLQ endpoints point to

Record:
- `ERROR_FLOW` = ordered list of processor → error handler mapping

Use in `test-generation.md` Step 2.1:
- For each error handler boundary, add an error scenario test
- If DLQ is on a downstream route (not this one), note it: "DLQ verification requires downstream route [routeId] to be active in the test"
