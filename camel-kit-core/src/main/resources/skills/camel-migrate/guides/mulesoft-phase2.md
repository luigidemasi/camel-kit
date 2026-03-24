# MuleSoft Migration — Phase 2: TDD Generation

> **Context variables:** `CAMEL_VERSION`, `RUNTIME`, `PLATFORM_BOM` from `.camel-kit/config.yaml`
> **Prerequisite:** Phase 1 (`mulesoft-phase1.md`) must be complete — BRD written to `docs/business-requirements.md`

## Phase 2 — Integration Architect

### Context Loading

**ALWAYS load at the start of Phase 2:**
- Load `skills/camel-migrate/guides/mule-dataweave-conversion.md` — required for DataWeave analysis
- Re-read `docs/business-requirements.md`
- Read `docs/constitution.md` if it exists (for reference)
- Re-read `.camel-kit/config.yaml` — **REQUIRED**: extract `project.camelVersion` as `CAMEL_VERSION` and `project.runtime` as `RUNTIME`. If the file does not exist, ask the user for the Camel version before proceeding.

**Conditionally load:**
- `skills/camel-migrate/guides/datamapper-migrate.md` — load once per flow that contains a DataWeave transformation (see Step 2.2)
- `skills/camel-flow/guides/performance.md` — if SLA requirements are strict
- `skills/camel-flow/guides/security.md` — if compliance requirements exist
- `skills/camel-flow/guides/monitoring.md` — if observability requirements exist

**MCP catalog tools — MANDATORY when MCP is configured (same rules as `/camel-flow`):**

Before every MCP catalog call, translate `CAMEL_VERSION` + `RUNTIME` to the correct `camelVersion` parameter using the version mapping table in `skills/shared/mcp-setup.md`. Never pass the raw `CAMEL_VERSION` or a stripped minor version (e.g., `4.14`) directly — always use the translated Red Hat artifact version from the table. Never use a Camel component name, EIP name, data format name, or expression language name from training data or the mapping guide without first verifying it in the catalog.

→ **For MCP setup, version mapping, and fallback policy:** see `skills/shared/mcp-setup.md`

| Decision | Tool to call first | Then call |
|----------|--------------------|-----------|
| Camel component for a Mule connector | `camel_catalog_components` | `camel_catalog_component_doc` |
| Camel EIP for a Mule routing construct | `camel_catalog_eips` | `camel_catalog_eip_doc` |
| Data format for unmarshal/marshal | `camel_catalog_dataformats` | `camel_catalog_dataformat_doc` |
| Expression language for conditions/predicates | `camel_catalog_languages` | `camel_catalog_language_doc` |
| Migration context for mapped Camel component | `camel_rh_build_search` | — |

The static `mule-component-mapping.md` guide provides a **starting point** (the suggested Camel component name). It does NOT replace catalog verification — always confirm availability and option names in `CAMEL_VERSION` before writing the TDD.

---

### Step 2.1 — Design Camel Route Architecture for Each Flow

For each Mule flow identified in Phase 1:

1. **Map Mule components → Camel components (catalog-verified).**
   Use `mule-component-mapping.md` to find the suggested Camel component name, then — **before writing anything to the TDD** — MUST verify it in the catalog that the component exist for the camel version in use:
   ```
   MCP Tool: camel_catalog_component_doc
   Params: { "component": "[suggested-camel-component]", "camelVersion": "{{CAMEL_VERSION}}", "platformBom": "{{PLATFORM_BOM}}", "runtime": "{{RUNTIME}}" }
   ```
   Record the URI syntax, endpoint options, component-level options, and Maven coordinates from the catalog response. If the component is not found in `CAMEL_VERSION`, call `camel_catalog_components` to search for an alternative and notify the user.

   **Red Hat support check (MANDATORY when camel-knowledge MCP is available):**
   After verifying a component in the catalog, call `camel_rh_build_component_info` to check Red Hat support:
   ```
   MCP Tool: camel_rh_build_component_info
   Params: { "component": "[camel-component-name]", "runtime": "{{RUNTIME}}" }
   ```
   If the component is NOT supported by Red Hat, raise a WARNING to the user, search for a Red Hat-supported alternative that provides equivalent functionality, and present both options. Let the user decide. If the MCP server is not available, skip this step.

   After mapping a Mule connector to a Camel component, ALWAYS call:
   ```
   camel_rh_build_search(query: "{mapped_camel_component} migration", max_results: 5)
   ```
   This provides migration context that may be relevant even for MuleSoft migrations —
   the Camel component may have changed between versions.
   If the camel-knowledge MCP server is not available, skip this step.

   **CRITICAL — use the exact component scheme from the route URI.** The component name MUST be the exact URI scheme (e.g., `smtp`, not `mail`; `aws2-sqs`, not `aws`). Many Camel components share a parent artifact but are distinct components with distinct schemes, options, and property prefixes.

   **CRITICAL — the TDD "Configuration Properties" section must only list properties that actually exist.** For each `camel.component.<name>.<property>` entry, verify that `<property>` appears in the component options returned by `camel_catalog_component_doc`. Do NOT carry over Mule configuration parameters (host, port, etc.) as Camel component properties if the catalog does not list them.

   **Platform-HTTP special case:** The `platform-http` component has NO `host` or `port` component options. Mule's HTTP Listener host/port do NOT map to `camel.component.platform-http.*` properties. If the Mule flow uses a non-default port, document it in the "Configuration Properties" section as `camel.server.enabled=true` and `camel.server.port=XXXX`.

2. **Apply proprietary connector decisions from Step 1.2** using the same catalog verification above.

3. **Translate DataWeave transformations** using `mule-dataweave-conversion.md`.
   When the translation requires `unmarshal`/`marshal` (e.g. no DataMapper XSLT coverage), verify the data format in the catalog before documenting it in the TDD:
   ```
   MCP Tool: camel_catalog_dataformat_doc
   Params: { "dataformat": "[format-name]", "camelVersion": "{{CAMEL_VERSION}}", "platformBom": "{{PLATFORM_BOM}}", "runtime": "{{RUNTIME}}" }
   ```

4. **Map Mule routing constructs → Camel EIPs (catalog-verified).**
   Use `mule-component-mapping.md` for the initial EIP suggestion (choice → `choice`, scatter-gather → `multicast`, forEach → `split`, etc.), then verify each EIP in the catalog:
   ```
   MCP Tool: camel_catalog_eip_doc
   Params: { "eip": "[eip-name]", "camelVersion": "{{CAMEL_VERSION}}", "platformBom": "{{PLATFORM_BOM}}", "runtime": "{{RUNTIME}}" }
   ```
   If any condition or predicate expression is required inside the EIP, also verify the expression language:
   ```
   MCP Tool: camel_catalog_language_doc
   Params: { "language": "[language-name]", "camelVersion": "{{CAMEL_VERSION}}", "platformBom": "{{PLATFORM_BOM}}", "runtime": "{{RUNTIME}}" }
   ```

5. **Map Mule sub-flows → Camel `direct:` routes.**

6. **Map Mule error handlers → Camel error handling.**
   Use `doTry/doCatch` for inline handlers, `onException` or `deadLetterChannel` for global handlers.

---

### Step 2.2 — Technical Interview (Per Flow)

Ask ONLY questions that cannot be answered from the Mule XML. Group questions per flow.

**For each flow, ask if not determinable from config:**

- **DataWeave transformations:** For each flow containing a DataWeave script, load `skills/camel-migrate/guides/datamapper-migrate.md` and follow its steps. The guide will infer field mappings from the DataWeave code, collect schema paths, confirm with the user, canonicalize with XSLT-ready XPaths and Target Elements (via `skills/shared/datamapper-canonicalize.md`), and append a canonical `### DataMapper: kaoto-datamapper-{id}` section to the TDD. Do not ask ad-hoc mapping questions here — the guide handles it fully.
- **Proprietary connectors:** Confirm the replacement approach decided in Phase 1 and any additional configuration needed (credentials format, endpoint URLs, etc.).
- **Target infrastructure endpoints:** If endpoint URLs, queue names, or topic names are parameterised or missing from the config, ask for the target environment values or confirm they will be externalised to properties.
- **Authentication:** For HTTP endpoints, confirm authentication mechanism (Basic, OAuth2, mTLS, API Key) and where credentials will be stored.

**Do NOT ask about error handling.** Error handlers (`on-error-continue`/`on-error-propagate`), retry policies, DLQ endpoints, and alert mechanisms are extracted from Mule XML in Step 1.1 and recorded in the analysis summary. Use them directly when populating the TDD "Error Handling" section.

---

### Step 2.3 — Produce TDD Files

For each Mule flow, create `docs/flows/{flow-name}/{flow-name}.tdd.md`.

Use the **exact same TDD format** as `/camel-flow` output. The file MUST contain all of the following sections:

```markdown
# Technical Design Document: {flow-name}

## Section 1: Overview

| Field | Value |
|-------|-------|
| Flow Name | {flow-name} |
| Migrated From | MuleSoft Mule [version] — [original-mule-flow-name] |
| Source Module | {relative path from workspace root to the source Mule project} |
| Target Module | {relative path from workspace root to the target Camel project, e.g. `order-service/`} |
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

**Mule Original:** `[original Mule endpoint/listener element]`

## Section 3: Processing Steps

### 3.1 Processing Overview

[Numbered list of processing steps in the Camel route, with the Mule component that each step replaces]

### 3.2 Field Mapping Table (Migration Audit Trail)

> This table is for migration traceability only. The `### DataMapper:` section below (if present) is what `camel-implement` uses to generate the XSLT.

| Source Field | Target Field | Transformation | Type | Mule Origin |
|-------------|-------------|----------------|------|-------------|
| [source] | [target] | Direct Copy / [expression] | String/Integer/etc. | [DataWeave expression or Mule element] |

### 3.3 Routing Logic (if applicable)

| Condition | Route | Camel EIP | Mule Original |
|-----------|-------|-----------|---------------|
| [condition] | [destination] | choice/when | `<choice><when>` |

### 3.4 String/Date Functions (if applicable)

| Operation | Source | Function | Result | Mule Origin |
|-----------|--------|----------|--------|-------------|

### 3.5 Sub-flow / Route References (if applicable)

| Mule Sub-flow | Camel Route | Direct URI |
|--------------|-------------|------------|
| [sub-flow-name] | [camel-route-name] | `direct:[camel-route-name]` |

### DataMapper: kaoto-datamapper-{8hexchars} (if flow contains DataWeave)

> Generated by `guides/datamapper-migrate.md` + `skills/shared/datamapper-canonicalize.md`. Include this section only when the Mule flow contains a DataWeave transformation. `camel-implement` reads this section to generate the XSLT and `.kaoto` metadata.

**Mapping ID:** `kaoto-datamapper-{8hexchars}`
**Migrated from DataWeave:** `{path-to-dataweave-file-or-"inline"}`
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

**Mule Original:** `[original Mule outbound endpoint element]`

## Section 5: Error Handling

| Error Type | Mule Handler | Camel Equivalent | Action |
|-----------|-------------|-----------------|--------|
| [error type] | on-error-continue / on-error-propagate | doCatch / Dead Letter | [action] |

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
[Description of the normal-flow test scenario, derived from Mule flow purpose]

### Error Scenarios
[List error scenarios to test, derived from Mule error handlers]

### Migration Validation
- [ ] Output payload matches Mule output (same field names, types, and values)
- [ ] Performance meets SLA requirements from BRD

## Section 11: Implementation Checklist

- [ ] Run `/camel-implement {flow-name}` to generate Camel YAML
- [ ] Run `/camel-validate {flow-name}` to validate the route
- [ ] Run `/camel-test {flow-name}` to generate integration tests
- [ ] Verify against original Mule behaviour
```

**Note:** If the BRD specifies performance/throughput requirements, add a **Section 5e: Throttling & Scaling** covering throttle EIP configuration and Kafka `consumersCount`. If security or compliance requirements exist, add a **Section 5f: Security**. If observability requirements exist, add a **Section 5g: Monitoring**. Renumber sections as needed.

---

### Step 2.4 — Complete

After all TDD files are created, report:

```
Migration analysis complete.

Created files:
- docs/business-requirements.md
- docs/flows/{flow-name-1}/{flow-name-1}.tdd.md
[... one line per flow ...]

Next steps:
  /camel-implement --all    # Implement all flows
  /camel-validate --all     # Validate all flows
  /camel-test --all         # Generate tests for all flows
```
