---
name: camel-migrate-spring
description: Internal sub-skill for migrating Spring Integration projects to Apache Camel
user-invocable: false
metadata:
  version: "2.0.0"
  author: "camel-kit"
  category: "migration"
  license: "Apache-2.0"
---

# Camel Migrate Spring — Spring Integration → Apache Camel Migration

This is an internal sub-skill invoked by `camel-migrate` after it has already:
1. Detected the vendor (Spring Integration)
2. Scanned all project artifacts
3. Built a pre-populated analysis summary
4. Confirmed the summary with the user

**Do not re-ask questions already answered in the summary. Do not invoke this skill directly.**

You will work in two phases:
- **Phase 1 (Business Analyst):** Deep-dive into Spring Integration XML config and Java DSL flows, resolve custom service-activators, fill any remaining gaps, produce BRD.
- **Phase 2 (Integration Architect):** Design catalog-verified Camel route architecture and produce TDD files.

The outputs are identical in format to `/camel-project` + `/camel-flow`, making them fully compatible with `/camel-implement`.

---

## Phase 1 — Business Analyst

### Context Loading

**ALWAYS load at the start of Phase 1:**
- Load `skills/camel-migrate-spring/guides/spring-component-mapping.md` — needed for the entire migration
- Read ALL Spring Integration XML configuration files (`<int:*>` namespaces, `*-context.xml`, `*-integration.xml`)
- Read ALL Java DSL configuration classes (files containing `IntegrationFlow`, `IntegrationFlows`, `@MessagingGateway`, `@ServiceActivator`, `@Transformer`, `@Filter`, `@Router`, `@Splitter`, `@Aggregator`)
- Read the confirmed analysis summary passed by `camel-migrate` (contains vendor, purpose, SLA, security, failure behaviour, deployment target, API compatibility)

**Conditional:**
- Read `.camel-kit/constitution.md` if it exists (for reference — do not generate or modify it)

---

### Step 1.1 — Parse and Inventory the Spring Integration Flows

Analyse all Spring Integration XML config files and Java DSL classes. Build a flow inventory. This is Spring Integration-specific extraction that complements the generic analysis summary already provided.

**For each message flow (channel chain from inbound to outbound), identify:**
- Flow name (derived from channel names, gateway interfaces, or IntegrationFlow bean names)
- Source (inbound channel adapter, inbound gateway, message-driven channel adapter, or `IntegrationFlow.from(...)`)
- Channels (`DirectChannel`, `QueueChannel`, `PublishSubscribeChannel`, `ExecutorChannel`)
- Processors in order (transformers, filters, routers, splitters, aggregators, service-activators, bridges, header-enrichers)
- SpEL expressions (location and complexity)
- Sink (outbound channel adapter, outbound gateway, or terminal `.handle(...)` / `.channel(...)`)
- Error handlers (`errorChannel` attribute, global `errorChannel`, `ExpressionEvaluatingRequestHandlerAdvice`, `RequestHandlerRetryAdvice`)
- Pollers (`<int:poller>` elements, `Pollers` Java DSL, fixed-delay / fixed-rate / cron)

**Classify each component using `spring-component-mapping.md`:**
- Mark components with known Camel equivalents
- Mark components flagged as **ASK USER** (custom service-activators calling project-specific Spring beans with complex business logic)
- Use dependencies from `pom.xml` / `build.gradle` to pre-suggest replacement options

---

### Step 1.2 — Resolve Custom Service-Activators and Beans

For each component marked **ASK USER**, ask the user — one component at a time:

```
I found the following component with no direct Apache Camel equivalent:

- **[Component / Bean Name]** (used in: [flow-name])
  Type: [service-activator / custom transformer / custom router / etc.]
  Spring Bean: [bean class name if identifiable]
  Suggested alternatives:
  a) Migrate as Camel `bean:` processor — keep the same Spring bean class
  b) Re-implement using Camel EIP patterns
  c) Remove this step
  d) Keep as a TODO placeholder

Your choice?
```

Record each decision. Use these in Phase 2.

---

### Step 1.3 — Fill Remaining Gaps

Check the confirmed summary from `camel-migrate`. For every field still marked **? Unknown** or **~ Inferred**, ask the user — one question at a time, only if it was not resolved by the Spring Integration analysis in Step 1.1.

**Do not ask about fields already marked ✓ Confirmed in the summary.**

API compatibility is assumed by default — Camel routes will preserve the same HTTP paths, queue/topic names, and data contracts as the original Spring Integration flows. If the user explicitly stated otherwise during the Step 5 confirmation in `camel-migrate`, note the deviation in the BRD.

If the summary has no remaining gaps, skip this step entirely.

---

### Step 1.4 — Produce Business Requirements Document

Create `.camel-kit/business-requirements.md` using the following format:

```markdown
# Business Requirements Document

## Executive Summary

[2–3 sentence description of the integration purpose, origin platform (Spring Integration), and migration goal]

**Migrated from:** Spring Integration [version if known]
**Migration date:** [current date]
**Original flows:** [count] flow(s) detected

## Systems Landscape

| System | Role | Protocol | Direction |
|--------|------|----------|-----------|
| [name] | [Source / Sink / Both] | [HTTP / JMS / File / etc.] | [inbound / outbound] |

## Integration Requirements

### [Flow Name 1]
- **Purpose:** [business purpose derived from flow analysis + user input]
- **Trigger:** [how the flow is triggered]
- **Data:** [what data flows through]
- **Outcome:** [expected result]

[Repeat for each flow]

## Constraints

### Technical Constraints
- [Derived from Spring Integration analysis: protocols, formats, etc.]

### Business Constraints
- [From user interview: compliance, SLA, etc.]

### Migration Constraints
- [Cut-over vs. parallel, API compatibility requirements]

## Best Practices

The following rules from `.camel-kit/constitution.md` apply to every generated route:
- One Camel route per Spring Integration flow (Single Responsibility)
- Route IDs follow `<domain>-<action>[-<qualifier>]` naming (Naming Conventions)
- Every route declares a `routeId` and a `description` (Observability)
- All connection parameters externalised to `application.properties` — no hardcoded values (External Configuration)
- Dead Letter Channel for failed messages (Error Handling — enforced by `/camel-validate`)

## Success Criteria

- [ ] All [N] Spring Integration flows have an equivalent Camel route
- [ ] Custom bean replacements are documented and agreed
- [ ] All flows pass `/camel-validate`
- [ ] SLA requirements are met under load

## Next Steps

Run `/camel-implement <flow-name>` for each flow once TDD files are created.

## Appendices

### A. Original Spring Integration Flows Inventory

| Flow Name | Source | Channels | Processors | Sink | Custom Beans |
|-----------|--------|----------|------------|------|-------------|
[One row per flow]

### B. Custom Bean Decisions

| Bean / Component | Original Flow | Decision | Camel Replacement |
|-----------------|--------------|----------|-------------------|
[One row per custom bean decision]
```

---

### Phase 1 Complete

Report:
```
Phase 1 complete.

Created:
- .camel-kit/business-requirements.md

Flows to migrate: [list flow names]

Starting Phase 2 — Integration Architect...
```

---

## Phase 2 — Integration Architect

### Context Loading

**ALWAYS load at the start of Phase 2:**
- Load `skills/camel-migrate-spring/guides/spring-spel-conversion.md` — required for SpEL expression analysis
- Re-read `.camel-kit/business-requirements.md`
- Read `.camel-kit/constitution.md` if it exists (for reference)
- Re-read `.camel-kit/config.yaml` — **REQUIRED**: extract `project.camelVersion` and store it as `CAMEL_VERSION`. Every MCP catalog call in Phase 2 MUST use this exact version. If the file does not exist, ask the user for the Camel version before proceeding.

**Conditionally load:**
- `skills/camel-migrate-spring/guides/datamapper-migrate.md` — load once per flow that contains a transformer with SpEL-based or complex field mappings (see Step 2.2)
- `skills/camel-flow/guides/performance.md` — if SLA requirements are strict
- `skills/camel-flow/guides/security.md` — if compliance requirements exist
- `skills/camel-flow/guides/monitoring.md` — if observability requirements exist

**MCP catalog tools — MANDATORY when MCP is configured (same rules as `/camel-flow`):**

All catalog calls MUST pass `CAMEL_VERSION` as the `version` parameter. Never use a Camel component name, EIP name, data format name, or expression language name from training data or the mapping guide without first verifying it in the catalog.

| Decision | Tool to call first | Then call |
|----------|--------------------|-----------|
| Camel component for a SI adapter/gateway | `camel_catalog_components` | `camel_catalog_component_doc` |
| Camel EIP for a SI routing construct | `camel_catalog_eips` | `camel_catalog_eip_doc` |
| Data format for unmarshal/marshal | `camel_catalog_dataformats` | `camel_catalog_dataformat_doc` |
| Expression language for conditions/predicates | `camel_catalog_languages` | `camel_catalog_language_doc` |

The static `spring-component-mapping.md` guide provides a **starting point** (the suggested Camel component name). It does NOT replace catalog verification — always confirm availability and option names in `CAMEL_VERSION` before writing the TDD.

---

### Step 2.1 — Design Camel Route Architecture for Each Flow

For each Spring Integration flow identified in Phase 1:

1. **Map SI components → Camel components (catalog-verified).**
   Use `spring-component-mapping.md` to find the suggested Camel component name, then — **before writing anything to the TDD** — MUST verify it in the catalog that the component exist for the camel version in use:
   ```
   MCP Tool: camel_catalog_component_doc
   Params: { "name": "[suggested-camel-component]", "version": "{{CAMEL_VERSION}}" }
   ```
   Record the URI syntax, endpoint options, component-level options, and Maven coordinates from the catalog response. If the component is not found in `CAMEL_VERSION`, call `camel_catalog_components` to search for an alternative and notify the user.

   **CRITICAL — use the exact component scheme from the route URI.** The component name MUST be the exact URI scheme (e.g., `smtp`, not `mail`; `sftp`, not `ftp`). Many Camel components share a parent artifact but are distinct components with distinct schemes, options, and property prefixes.

   **CRITICAL — TDD Section 7 (Configuration Properties) must only list properties that actually exist.** For each `camel.component.<name>.<property>` entry, verify that `<property>` appears in the component options returned by `camel_catalog_component_doc`. Do NOT carry over Spring Integration configuration parameters as Camel component properties if the catalog does not list them.

   **Platform-HTTP special case:** The `platform-http` component has NO `host` or `port` component options. Spring Integration's HTTP inbound gateway host/port do NOT map to `camel.component.platform-http.*` properties. If the SI flow uses a non-default port, document it in Section 7 as `camel.server.enabled=true` and `camel.server.port=XXXX`.

2. **Apply custom bean decisions from Step 1.2** using the same catalog verification above.

3. **Translate SpEL expressions** using `spring-spel-conversion.md`.
   When the translation requires `unmarshal`/`marshal` (e.g. no DataMapper XSLT coverage), verify the data format in the catalog before documenting it in the TDD:
   ```
   MCP Tool: camel_catalog_dataformat_doc
   Params: { "name": "[format-name]", "version": "{{CAMEL_VERSION}}" }
   ```

4. **Map SI routing constructs → Camel EIPs (catalog-verified).**
   Use `spring-component-mapping.md` for the initial EIP suggestion (router → `choice`, splitter → `split`, aggregator → `aggregate`, etc.), then verify each EIP in the catalog:
   ```
   MCP Tool: camel_catalog_eip_doc
   Params: { "name": "[eip-name]", "version": "{{CAMEL_VERSION}}" }
   ```
   If any condition or predicate expression is required inside the EIP, also verify the expression language:
   ```
   MCP Tool: camel_catalog_language_doc
   Params: { "name": "[language-name]", "version": "{{CAMEL_VERSION}}" }
   ```

5. **Map SI channels → Camel `direct:` or `seda:` routes.**
   - `DirectChannel` → `direct:channelName`
   - `QueueChannel` → `seda:channelName`
   - `PublishSubscribeChannel` → `seda:channelName?multipleConsumers=true` or `direct:` with `multicast` EIP
   - `ExecutorChannel` → `seda:channelName` with thread pool configuration

6. **Map SI error handling → Camel error handling.**
   - Global `errorChannel` → `onException` or `errorHandler` at route-builder level
   - Per-endpoint `error-channel` → `doTry/doCatch` or `onException` scoped to the route
   - `RequestHandlerRetryAdvice` → `redeliveryPolicy` on error handler
   - `ExpressionEvaluatingRequestHandlerAdvice` with `failureChannel` → `doTry/doCatch`

7. **Map SI pollers → Camel consumer scheduling.**
   - `<int:poller fixed-delay="...">` → `timer:` component or consumer `delay` option
   - `<int:poller cron="...">` → `quartz:` component
   - `<int:poller fixed-rate="...">` → `timer:` with `fixedRate=true`

---

### Step 2.2 — Technical Interview (Per Flow)

Ask ONLY questions that cannot be answered from the Spring Integration config. Group questions per flow.

**For each flow, ask if not determinable from config:**

- **Transformer mappings:** For each flow containing a transformer with SpEL expressions or complex logic, load `skills/camel-migrate-spring/guides/datamapper-migrate.md` and follow its steps. The guide will infer field mappings from SpEL expressions and transformer code, collect schema paths, confirm with the user, canonicalize with XSLT-ready XPaths and Target Elements (via `skills/shared/datamapper-canonicalize.md`), and append a canonical `### DataMapper: kaoto-datamapper-{id}` section to the TDD. Do not ask ad-hoc mapping questions here — the guide handles it fully.
- **Custom beans:** Confirm the replacement approach decided in Phase 1 and any additional configuration needed (method signatures, dependencies, etc.).
- **Target infrastructure endpoints:** If endpoint URLs, queue names, or topic names are parameterised or missing from the config, ask for the target environment values or confirm they will be externalised to properties.
- **Authentication:** For HTTP endpoints, confirm authentication mechanism (Basic, OAuth2, mTLS, API Key) and where credentials will be stored.

**Do NOT ask about error handling.** Error handlers (`errorChannel`, retry advice, failure channels) are extracted from Spring Integration config in Step 1.1 and recorded in the analysis summary. Use them directly when populating TDD Section 5.

---

### Step 2.3 — Produce TDD Files

For each Spring Integration flow, create `.camel-kit/flows/{flow-name}/{flow-name}.tdd.md`.

Use the **exact same TDD format** as `/camel-flow` output. The file MUST contain all of the following sections:

```markdown
# Technical Design Document: {flow-name}

## Section 1: Overview

| Field | Value |
|-------|-------|
| Flow Name | {flow-name} |
| Migrated From | Spring Integration [version if known] — [original-flow-name] |
| Business Purpose | [from BRD] |
| Trigger | [how the Camel route is triggered] |
| Camel Version | [from config.yaml] |
| Created | [current date] |

## Section 2: Source System

| Field | Value |
|-------|-------|
| Component | [Camel component URI] |
| Protocol | [protocol] |
| Format | [data format: JSON / XML / CSV / Binary] |
| Authentication | [mechanism] |
| Configuration Property | [property key name] |

**Spring Integration Original:** `[original SI inbound adapter/gateway element]`

## Section 3: Processing Steps

### 3.1 Processing Overview

[Numbered list of processing steps in the Camel route, with the SI component that each step replaces]

### 3.2 Field Mapping Table (Migration Audit Trail)

> This table is for migration traceability only. The `### DataMapper:` section below (if present) is what `camel-implement` uses to generate the XSLT.

| Source Field | Target Field | Transformation | Type | SI Origin |
|-------------|-------------|----------------|------|-----------|
| [source] | [target] | Direct Copy / [expression] | String/Integer/etc. | [SpEL expression or SI element] |

### 3.3 Routing Logic (if applicable)

| Condition | Route | Camel EIP | SI Original |
|-----------|-------|-----------|-------------|
| [condition] | [destination] | choice/when | `<int:router>` / `@Router` |

### 3.4 String/Date Functions (if applicable)

| Operation | Source | Function | Result | SI Origin |
|-----------|--------|----------|--------|-----------|

### 3.5 Sub-flow / Route References (if applicable)

| SI Channel/Gateway | Camel Route | Direct URI |
|-------------------|-------------|------------|
| [channel-name] | [camel-route-name] | `direct:[camel-route-name]` |

### DataMapper: kaoto-datamapper-{8hexchars} (if flow contains transformer with field mappings)

> Generated by `guides/datamapper-migrate.md` + `skills/shared/datamapper-canonicalize.md`. Include this section only when the SI flow contains a transformer with SpEL-based field mappings. `camel-implement` reads this section to generate the XSLT and `.kaoto` metadata.

**Mapping ID:** `kaoto-datamapper-{8hexchars}`
**Migrated from SI Transformer:** `{transformer bean name or "inline SpEL"}`
**Source:** {XML_SCHEMA | JSON_SCHEMA | Primitive} — `{source-schema-path or "none"}`
**Target:** {XML_SCHEMA | JSON_SCHEMA | Primitive} — `{target-schema-path or "none"}`
**XSLT Pattern:** {A | B | C | D} — {source-format} → {target-format}
**XSLT Approach:** {A (useJsonBody) | B (header param) | N/A}

> **Type selection rules:** Use `JSON_SCHEMA` for JSON data and `XML_SCHEMA` for XML data, even when no schema file exists (schema path = `"none"`). `Primitive` is only for truly scalar values (a single string, number, or boolean — not a JSON object or XML document). `N/A` approach is only valid when source is `XML_SCHEMA`.

#### Source Parameters

| Parameter | Type | Schema Path |
|-----------|------|-------------|

#### Namespace Map

| Prefix | URI |
|--------|-----|
| xs  | http://www.w3.org/2001/XMLSchema |
| fn  | http://www.w3.org/2005/xpath-functions |
| xsl | http://www.w3.org/1999/XSL/Transform |

#### Field Mappings

| Source Field | Src Type | Source XPath | Target Field | Tgt Type | Target Element | Transformation | How |
|---|---|---|---|---|---|---|---|

#### Conditional Mappings

| Target Field | Condition | True Value | False Value | Notes |
|---|---|---|---|---|

#### Collection Mappings

| Source Collection | Target Collection | Iteration |
|---|---|---|


## Section 4: Sink System

| Field | Value |
|-------|-------|
| Component | [Camel component URI] |
| Protocol | [protocol] |
| Format | [data format] |
| Authentication | [mechanism] |
| Configuration Property | [property key name] |

**Spring Integration Original:** `[original SI outbound adapter/gateway element]`

## Section 5: Error Handling

| Error Type | SI Handler | Camel Equivalent | Action |
|-----------|-----------|-----------------|--------|
| [error type] | errorChannel / retry advice / failure channel | doCatch / Dead Letter / onException | [action] |

**Dead Letter Queue/Topic:** `[name or N/A]`
**Retry Policy:** [count] retries, [delay] delay, [backoff strategy]
**Alert Mechanism:** [email / Slack / none]

## Section 6: Sequence Diagram

```mermaid
sequenceDiagram
    participant Source as [Source System]
    participant Camel as Apache Camel
    participant Sink as [Sink System]

    Source->>Camel: [trigger description]
    [Add processing steps]
    Camel->>Sink: [output description]
```

## Section 7: Configuration Properties

> Only list properties that are valid for the actual Camel component (verified via `camel_catalog_component_doc` in Step 2.1). For `platform-http`, do NOT list host/port as component properties — use `camel.server.enabled` and `camel.server.port` instead.

| Property Key | Description | Example Value | Required |
|-------------|-------------|---------------|----------|
| [key] | [description] | [placeholder] | Yes/No |

## Section 8: Dependencies

| Dependency | Maven Coordinates | Notes |
|-----------|-------------------|-------|
| [component name] | `[groupId:artifactId:version]` | [notes] |

## Section 9: Constitution Gate Checks

Constitution v2.0 — six enforced rules:

- [ ] **Route Structure** — route has a `from:` source and a final `to:` sink
- [ ] **Single Responsibility** — route has one clear purpose; ≤ 7 processing steps
- [ ] **Separation of Concerns** — ingestion, processing, and delivery are separate routes where appropriate
- [ ] **Naming Conventions** — route ID follows `<domain>-<action>[-<qualifier>]`
- [ ] **Observability** — `routeId` and `description` declared; correlation ID propagated
- [ ] **External Configuration** — no hardcoded credentials, connection strings, or env-specific values; all use `{{PLACEHOLDER}}`

## Section 10: Testing Strategy

### Happy Path
[Description of the normal-flow test scenario, derived from SI flow purpose]

### Error Scenarios
[List error scenarios to test, derived from SI error handlers]

### Migration Validation
- [ ] Output payload matches Spring Integration output (same field names, types, and values)
- [ ] Performance meets SLA requirements from BRD

## Section 11: Implementation Checklist

- [ ] Run `/camel-implement {flow-name}` to generate Camel YAML
- [ ] Run `/camel-validate {flow-name}` to validate the route
- [ ] Run `/camel-test {flow-name}` to generate integration tests
- [ ] Verify against original Spring Integration behaviour
```

**Note:** If the BRD specifies performance/throughput requirements, add a **Section 5e: Throttling & Scaling** covering throttle EIP configuration and consumer thread pools. If security or compliance requirements exist, add a **Section 5f: Security**. If observability requirements exist, add a **Section 5g: Monitoring**. Renumber sections as needed.

---

### Step 2.4 — Complete

After all TDD files are created, report:

```
Migration analysis complete.

Created files:
- .camel-kit/business-requirements.md
- .camel-kit/flows/{flow-name-1}/{flow-name-1}.tdd.md
[... one line per flow ...]

Next steps — run for each flow:
  /camel-implement {flow-name-1}
  /camel-implement {flow-name-2}
  [...]

Then validate and test:
  /camel-validate {flow-name}
  /camel-test {flow-name}
```
