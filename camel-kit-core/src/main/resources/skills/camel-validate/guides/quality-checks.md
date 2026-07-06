# Quality Checks Guide

> **Context variables provided by master SKILL.md:**
> - `FLOW_NAME` — the flow being validated
> - `CAMEL_VERSION` — from `.camel-kit/config.properties`
>
> **Version mapping:** When calling MCP catalog tools, translate `CAMEL_VERSION` to the correct catalog version using the version mapping table in `skills/shared/mcp-setup.md`.

> **The ✅ blocks in this guide are OUTPUT FORMATS, not results.** Every ✅ you print must be derived from an actual check or tool call performed in this run. Printing the example blocks without performing the checks is a validation failure.

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

### 5.2 Component Catalog Verification

For each component, call `camel_catalog_component_doc` with the component name, `runtime`, and `platformBom` from `.camel-kit/config.properties` (see `shared/mcp-setup.md`). Check the `camelVersion` echoed in each response matches the project version — a mismatch means the answer came from the wrong catalog and must be re-queried with an explicit `platformBom`. If the tool call fails (tool not found, network error), skip this section with a note: `"Skipping catalog verification — MCP not available."`

**This step is data collection only.** Store the results (availability per component) for use in Stage 6 Constitution Rule 7, which evaluates and reports warnings.

```
Collecting catalog data...

  kafka: Available
  sql: Available
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
| 7 | Component Support | Catalog verified | Every component verified to exist in the Apache Camel catalog for the target version. **Primary:** Uses Stage 5.2 collected data (from `camel_catalog_component_doc`). **Fallback (Stage 5.2 was skipped):** Consult the Apache Camel component catalog for the target version. Two warning levels: (1) **Not found** — component not in catalog; (2) **Deprecated** — component is deprecated in the target version. "Available" passes without warning. | WARNING |

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
  ✅ kafka: Available in catalog
  ✅ sql: Available in catalog
  [or: ⚠️ custom-component: NOT FOUND in Apache Camel catalog for version X.Y]
  [or: ⚠️ Could not verify — catalog MCP not available]
```

### 6.2 Custom Constitution Rules

If `docs/constitution.md` defines project-specific rules (under "Project Customizations"), validate those:

```
✅ Custom Rule: [rule name] - [result]
```

---

## Stage 7: Configuration Validation

Validate `application.properties`:

### 7.1 Property Key Validation (MCP)

Run the properties file through the catalog validator — do NOT pattern-match keys by shape.

1. Read `application.properties` (and each `application-<env>.properties`).
2. Remove `camel.beans.*` lines from the text to submit (covered by 7.3, not a catalog concept).
3. Call `camel_configuration_validate` with `properties` = the remaining file content, `runtime` and `platformBom` from `.camel-kit/config.properties`. Check the echoed `camelVersion` matches the project version.
4. Report the tool's per-line results. Any `unknown` key or invalid value = ❌ FAIL, including the tool's suggestion in the report.
5. If the tool is unavailable, fall back to per-component `camel_catalog_component_doc` (with `runtime`+`platformBom`) and diff every `camel.component.<c>.<prop>` key against the returned component-options list; note the fallback in the report.
6. Additionally check (manually): no duplicate property keys in the file.

```
== CONFIGURATION VALIDATION ==

application.properties (via camel_configuration_validate, Camel {{CAMEL_VERSION}}):
  [real per-line results — e.g.:]
  ✅ camel.component.kafka.brokers — valid
  ❌ camel.component.amqp.brokerUr — unknown option; suggestion: brokerUrl
  ✅ No duplicate property keys
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
