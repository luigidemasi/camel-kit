# Quality Checks Guide

> **Context variables provided by master SKILL.md:**
> - `FLOW_NAME` — the flow being validated
> - `CAMEL_VERSION` — from `.camel-kit/config.yaml`
>
> **Version mapping:** When calling MCP catalog tools, translate `CAMEL_VERSION` to the correct catalog version using the version mapping table in `skills/shared/mcp-setup.md`.

## Stage 4: Completeness Checks

Verify all required elements are present:

| Check | Pass Criteria |
|-------|---------------|
| Route ID | Route has `id:` property |
| Source defined | Route has `from:` section |
| Sink defined | Route has `to:` or ends with producer |
| Error handling | Route declares error handling strategy |
| Description | Route has `description:` with at least 10 characters describing the flow's business purpose |

Show results:

```
== COMPLETENESS CHECKS ==

✅ {flow-name}: Route ID present
✅ {flow-name}: Source defined (kafka:{{kafka.topic.input}})
✅ {flow-name}: Sink defined (sql:INSERT INTO...)
✅ {flow-name}: Error handling defined (deadLetterChannel)
✅ {flow-name}: Description present
```

---

## Stage 5: Correctness Checks

Validate component usage and configuration:

### 5.1 Component Catalog Validation

For each component used, verify:
- Component exists in Camel catalog for version
- Required parameters provided
- Parameter types correct
- Component used correctly (consumer vs producer)

```
== COMPONENT VALIDATION ==

Kafka Component:
  ✅ Valid component (Camel {{CAMEL_VERSION}})
  ✅ Used as consumer: kafka:{{kafka.topic.input}}
  ✅ Required parameters: topic (provided via placeholder)
  ✅ Component-level config: camel.component.kafka.brokers (defined)

SQL Component:
  ✅ Valid component (Camel {{CAMEL_VERSION}})
  ✅ Used as producer: sql:INSERT INTO...
  ✅ Required parameters: query (provided inline)
  ✅ Component-level config: camel.component.sql.dataSource (defined)
```

### 5.2 Red Hat Support Data Collection (optional, if camel-knowledge MCP is available)

For each component, call `camel_rh_build_component_info` to collect Red Hat support status. If the tool call fails (tool not found, network error), skip this section with a note: `"Skipping Red Hat support check — camel-knowledge MCP not available."`

**This step is data collection only.** Store the results (support level per component) for use in Stage 6 Constitution Rule 7, which evaluates and reports warnings.

```
Collecting Red Hat support data...

  kafka: Production Support
  sql: Production Support
  azure-servicebus: Technology Preview
  custom-component: Not Found
```

### 5.3 Expression Validation

Validate all expressions used in the route:

| Language | Validation Rules |
|----------|-----------------|
| Simple | `${header.X}` / `${body}` / `${exchangeProperty.X}` — verify referenced headers/properties exist in the flow. No undefined variable references. |
| JSONPath | Must start with `$` or `$.`. Verify valid JSONPath syntax (matched brackets, valid operators). |
| XPath | Must be well-formed XPath 1.0/2.0. Verify namespace prefixes are declared if used. |
| Constant | Literal values only. No expression syntax inside `constant` blocks. |
| JQ | Must start with `.` — verify valid JQ filter syntax. |

```
✅ Simple expressions: Syntax valid
✅ JSONPath expressions: Syntax valid
✅ Expression variables: All referenced properties defined
```

---

## Stage 6: Constitution Checks

Validate against the 7 rules in `docs/constitution.md`. Each gate maps 1:1 to a constitution rule.

### 6.1 Constitution Gates

| # | Rule | Check | Formal Criteria | Severity |
|---|------|-------|-----------------|----------|
| 1 | Route Structure | Source and sink present | Route has a `from:` (source) and ends with a `to:` or producer (sink). `direct:`/`seda:` sub-routes are exempt from needing an external sink. | FAIL |
| 2 | Single Responsibility | One purpose per route | Route processing steps serve ONE business purpose, explainable in one sentence. Fail if route mixes unrelated business logic (e.g., order processing AND user notification in the same route). | WARNING if > PROJECT_NORMS.STEP_COUNT_P75 processing steps (default: 7 if no graph) |
| 3 | Separation of Concerns | Decomposed architecture | For routes with >5 processing steps: verify decomposition into ingestion → processing → delivery using `direct:`/`seda:` internal routing. Business logic should be in beans, not inline in routes. Single-step routes are exempt. | WARNING |
| 4 | Naming Conventions | Route ID convention | If PROJECT_NORMS.NAMING_PATTERN is available, validate route ID against the project's dominant pattern (examples: PROJECT_NORMS.NAMING_EXAMPLES). Otherwise, route ID matches `{domain}-{action}` lowercase kebab-case. Valid: `order-process`, `user-notify`. Invalid: `route1`, `myRoute`, `OrderProcess`. Regex: `^[a-z][a-z0-9]*(-[a-z][a-z0-9]*)+$` | WARNING |
| 5 | Observability | routeId + description | Every route declares both a `routeId` and a `description` (≥10 chars describing the flow's business purpose). These are essential for monitoring, logging, and tracing. | FAIL |
| 6 | External Configuration | No hardcoded values | No hostnames, ports, IPs, database URLs, queue names, credentials, API keys, tokens, or secrets in route YAML. Detect patterns: `password=`, `apiKey=`, `secret=`, `token=`, Base64 strings >20 chars, `jdbc:` URLs with inline credentials. All must use `{{placeholder}}` syntax. | FAIL |
| 7 | Component Support | Red Hat verified | Every component verified as supported by Red Hat Build of Apache Camel for the target version. **Primary:** Uses Stage 5.2 collected data (from `camel_rh_build_component_info`). **Fallback (Stage 5.2 was skipped):** Consult the Red Hat Build of Apache Camel reference docs — Quarkus Reference or Spring Boot Reference for the target version — which list all supported extensions with their support level. Three warning levels: (1) **Not found** — component not in Red Hat docs at all; (2) **Tech Preview** — marked as Technology Preview (not for production, may not be functionally complete); (3) **Community Support** — tested upstream but not formally supported by Red Hat. Only "Production Support" passes without warning. | WARNING |

Show results:

```
== CONSTITUTION COMPLIANCE ==

Rule 1 — Route Structure:
  ✅ Source (from: kafka:...) and sink (to: sql:...) present

Rule 2 — Single Responsibility:
  ✅ Single business purpose (4 processing steps)

Rule 3 — Separation of Concerns:
  ✅ Route uses direct: for internal decomposition

Rule 4 — Naming Conventions:
  ✅ Route ID 'order-process' matches domain-action pattern

Rule 5 — Observability:
  ✅ routeId and description present

Rule 6 — External Configuration:
  ✅ No hardcoded values — all configuration externalized

Rule 7 — Component Support:
  ✅ kafka: Production Support
  ✅ sql: Production Support
  [or: ⚠️ azure-servicebus: Technology Preview — not for production use, may not be functionally complete]
  [or: ⚠️ custom-component: NOT FOUND in Red Hat Build docs — check supported extensions list]
  [or: ⚠️ Could not verify — camel-knowledge MCP not available]
```

### 6.2 Custom Constitution Rules

If `docs/constitution.md` defines project-specific rules (under "Project Customizations"), validate those:

```
✅ Custom Rule: [rule name] - [result]
```

---

## Stage 7: Configuration Validation

Validate `application.properties`:

### 7.1 Property Format

```
== CONFIGURATION VALIDATION ==

application.properties:

✅ Component config: Uses camel.component.<name>.<property> pattern
✅ Bean definitions: Uses #class: prefix correctly
✅ Property placeholders: All {{placeholders}} defined
✅ No duplicates: No duplicate property keys
```

### 7.2 Property Completeness

Verify all placeholders used in routes are defined:

```
Checking property placeholders...

Route uses:
  - {{kafka.topic.input}}
  - {{kafka.topic.dlq}}
  - {{sql.insert}}

Properties file defines:
  ✅ kafka.topic.input=orders
  ✅ kafka.topic.dlq=orders-dlq
  ✅ sql.insert=INSERT INTO...

All placeholders resolved: ✅
```

### 7.3 Bean Definitions

Validate bean definitions:

```
Bean Definitions:

✅ dataSource: #class:org.apache.commons.dbcp2.BasicDataSource
   Properties:
     ✅ driverClassName=org.postgresql.Driver
     ✅ url=jdbc:postgresql://...
     ✅ username=postgres
     ✅ password=postgres
```
