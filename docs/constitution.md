# Camel-Kit Constitution

> Seven non-negotiable rules enforced on every generated route. All other design guidance (error handling strategy, retry policy, throttling, resilience patterns, idempotency, transactions, data format choices, deployment) lives in `/camel-flow` and `/camel-migrate`, where it is applied context-specifically during flow design.

---

## Document Control

| Property | Value |
|----------|-------|
| Version | 2.0 |
| Last Updated | 2026-03-31 |
| Camel Version |` 4.14.4.redhat-00008` |

---

## Rules

### 1. Route Structure

Every route MUST have a **source** (`from:`) and a **sink** (final `to:`).

- `direct:` and `seda:` sub-routes are exempt — they may omit an external sink.
- **Violation:** routes without a source or sink fail validation.
- Pass-through routes with no processing steps trigger a WARNING.

---

### 2. Single Responsibility

One route = one clear purpose, explainable in one sentence.

- If a description requires "and … and …", split into multiple routes.
- **Violation:** routes with more than 7 processing steps trigger a review WARNING.

---

### 3. Separation of Concerns

Decompose complex integrations into discrete, composable routes.

- Pattern: **Ingestion → Processing → Delivery**
- Use `direct:` for synchronous internal routing, `seda:` for asynchronous.
- Keep business logic in beans; keep integration logic in routes.

---

### 4. Naming Conventions

Use consistent, meaningful names throughout.

**Route IDs:** `<domain>-<action>[-<qualifier>]`
```
order-ingestion
customer-enrichment
inventory-lookup
```

**Internal endpoints:** `direct:<route-id>` · `seda:<domain>-<purpose>`

**Headers/Properties:** `CamelCase` for Camel built-ins · `kebab-case` for custom (`order-id`, `correlation-id`)

- **Violation:** route IDs not matching the convention trigger a WARNING.

---

### 5. Observability

Every route MUST declare a `routeId` and a `description`.

- Use correlation IDs (e.g. `X-Correlation-ID`) to trace requests across routes.
- Log at decision points, not at every step.
- **Violation:** routes without a `routeId` fail validation.

---

### 6. External Configuration

Never hardcode connection strings, credentials, or environment-specific values.

- Use `{{PLACEHOLDER}}` syntax for all configurable values.
- Configuration hierarchy (highest priority first): environment variables → system properties → `application.properties` → bundled defaults.
- **Violation:** hardcoded credentials or connection strings fail validation.

---

### 7. Component Support Verification

Every component used in a route MUST be verified as **supported by Red Hat** in the target Camel version.

- **Primary check:** Call `camel_rh_build_component_info` (via camel-knowledge MCP) to check whether the component is included in the Red Hat Build of Apache Camel for the target version. A component may exist in the community Apache Camel catalog but **not** be supported by Red Hat — this distinction is critical.
- **Support levels:** Red Hat docs classify components as Production Support, Technology Preview, Community Support, or Dev Support. Only "Production Support" means the component is fully supported for production use. Technology Preview components may not be functionally complete and are not recommended for production.
- **If a component is NOT supported or is Technology Preview:**
  1. Raise a WARNING to the user explaining the support status.
  2. Search for a Red Hat-supported alternative that provides equivalent functionality (query `camel_rh_build_component_info` for related components).
  3. Present the warning, the component's support status, and the suggested alternative to the user before proceeding. Let the user decide whether to accept the component or switch to the alternative.
- **Violation:** WARNING — this is not a validation blocker, but users must be clearly informed of the support implications.

---

## Project Customizations

> Extend or override the rules above for project-specific needs.

### Allowed Components
```yaml
# allowedComponents:
#   - kafka
#   - http
#   - jpa
```

### Naming Overrides
```yaml
# routeIdPattern: "myteam-{domain}-{action}"
```

### Error Handling Overrides
```yaml
# deadLetterPatterns:
#   order: kafka:order-dlq
#   default: kafka:integration-dlq
```

### Security Requirements
```yaml
# security:
#   requireTLS: true
#   sensitiveHeaders: [Authorization, X-API-Key]
```

---

## References

- [Apache Camel Error Handler](https://camel.apache.org/manual/error-handler.html)
- [Enterprise Integration Patterns](https://www.enterpriseintegrationpatterns.com/)
