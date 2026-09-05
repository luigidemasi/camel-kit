# MuleSoft Migration — Phase 2: Design Spec Generation

> **Context variables:** `CAMEL_VERSION`, `RUNTIME`, `PLATFORM_BOM` from `.camel-kit/config.properties`
> **Prerequisite:** Phase 1 (`mulesoft-phase1.md`), the shared behavioral analysis, the source-retirement audit, and the
> deferred migration-strategy pass must be complete. `business-requirements.md` and `migration-analysis.md` must exist
> in `docs/camel-kit/<PIPELINE_ID>/`; the business requirements must contain `## Migration Strategy`, and the analysis
> must contain `## Behavioral Assumptions and Risks` and `## Source-Retirement Candidate Audit`.

## Phase 2 — Integration Architect

### Context Loading

**ALWAYS load at the start of Phase 2:**
- Load `skills/camel-migrate/guides/mule-dataweave-conversion.md` — required for DataWeave analysis
- Re-read `docs/camel-kit/<PIPELINE_ID>/business-requirements.md`
- Re-read `docs/camel-kit/<PIPELINE_ID>/migration-analysis.md`
- Read `docs/constitution.md` if it exists (for reference)
- Re-read `.camel-kit/config.properties` — **REQUIRED**: extract `project.camelVersion` as `CAMEL_VERSION` and `project.runtime` as `RUNTIME`. If the file does not exist, ask the user for the Camel version before proceeding.

**Conditionally load:**
- `skills/camel-migrate/guides/datamapper-migrate.md` — load once per flow that contains a DataWeave transformation (see Step 2.2)
- `skills/camel-design/guides/performance.md` — if SLA requirements are strict
- `skills/camel-design/guides/security.md` — if compliance requirements exist
- `skills/camel-design/guides/monitoring.md` — if observability requirements exist

Before designing flows, preserve every migration-strategy scope and every supporting `MIG-###` and `SRC-###` evidence
ID and status. Preserve each scope's exact classification (`Incremental candidate`, `Single cutover required`, or
`Undetermined - evidence needed`). Map each `Undetermined - evidence needed` gap to a blocking unresolved obligation in
the design. Map every other `Inferred` or `Unknown` `MIG-###` row and `Retirement candidate`, `Broken reference`, or
`Unknown` `SRC-###` row to an explicit constraint, validation requirement, or unresolved decision. Concrete incremental or strangler design is
allowed only for an `Incremental candidate` whose eight required operational-seam facts all have `Confirmed` evidence.
Confirmed target-side conditions are design obligations with pre-cutover validation, not claims that the target is
deployed or cutover-ready.
Never reclassify from topology or inferred data. Phase 2 and design approval do not authorize any deployment, cutover,
rollback, or traffic action.

**MCP catalog tools — MANDATORY when MCP is configured (same rules as the design assembly guide):**

Before every MCP catalog call, resolve `CAMEL_VERSION` + `RUNTIME` to the full runtime-specific `PLATFORM_BOM` GAV using
Rule 1 in `skills/shared/mcp-setup.md`, establish the `limit=0` version probe, and pass that same runtime/BOM binding to
the list and detail calls. Never substitute a stripped minor version (for example `4.14`) or treat the separately supplied
`camelVersion` field as the binding when `platformBom` is present. Never use a Camel component name, EIP name, data
format name, or expression language name from training data or the mapping guide without first verifying it in the
catalog.

→ **For MCP setup, version mapping, and fallback policy:** see `skills/shared/mcp-setup.md`

| Decision | Tool to call first | Then call |
|----------|--------------------|-----------|
| Camel component for a Mule connector | `camel_catalog_components` | `camel_catalog_component_doc` |
| Camel EIP for a Mule routing construct | `camel_catalog_eips` | `camel_catalog_eip_doc` |
| Data format for unmarshal/marshal | `camel_catalog_dataformats` | `camel_catalog_dataformat_doc` |
| Expression language for conditions/predicates | `camel_catalog_languages` | `camel_catalog_language_doc` |

The static `mule-component-mapping.md` guide provides a **starting point** (the suggested Camel component name). It does NOT replace catalog verification — always confirm availability and option names in `CAMEL_VERSION` before writing the design spec.

---

### Step 2.1 — Design Camel Route Architecture for Each Flow

For each Mule flow identified in Phase 1:

1. **Map Mule components → Camel components (catalog-verified).**
   Use `mule-component-mapping.md` to find the suggested Camel component name, then — **before writing anything to the design spec** — MUST verify it in the catalog that the component exist for the camel version in use:
   ```
   MCP Tool: camel_catalog_component_doc
   Params: { "component": "[suggested-camel-component]", "camelVersion": "{{CAMEL_VERSION}}", "platformBom": "{{PLATFORM_BOM}}", "runtime": "{{RUNTIME}}" }
   MCP Tool: camel_catalog_component_maven
   Params: { "component": "[suggested-camel-component]", "camelVersion": "{{CAMEL_VERSION}}", "platformBom": "{{PLATFORM_BOM}}", "runtime": "{{RUNTIME}}" }
   ```
   Record URI syntax and options from the documentation result, and Maven coordinates from the Maven result. Establish
   the version binding and prove absence with a complete exact-name list check per `shared/mcp-setup.md`; a detail error
   alone is unverified. If absent in `CAMEL_VERSION`, search `camel_catalog_components` for an alternative and notify the user.

   **CRITICAL — use the exact component scheme from the route URI.** The component name MUST be the exact URI scheme (e.g., `smtp`, not `mail`; `aws2-sqs`, not `aws`). Many Camel components share a parent artifact but are distinct components with distinct schemes, options, and property prefixes.

   **CRITICAL — the design spec "Configuration Properties" section must only list properties that actually exist.** For each `camel.component.<name>.<property>` entry, verify that `<property>` appears in the component options returned by `camel_catalog_component_doc`. Do NOT carry over Mule configuration parameters (host, port, etc.) as Camel component properties if the catalog does not list them.

   **Platform-HTTP special case:** The `platform-http` component has NO `host` or `port` component options. Mule's HTTP Listener host/port do NOT map to `camel.component.platform-http.*` properties. If the Mule flow uses a non-default port, document it in the "Configuration Properties" section using the target runtime's HTTP server property: `camel.server.enabled=true` plus `camel.server.port=XXXX` for `main`, `server.port=XXXX` for `spring-boot`, or `quarkus.http.port=XXXX` for `quarkus`.

2. **Apply proprietary connector decisions from Step 1.2** using the same catalog verification above.

3. **Translate DataWeave transformations** using `mule-dataweave-conversion.md`.
   Canonicalize the mapping before engine-specific work: Groovy when both schemas are absent OR there are fewer than 20
   leaf fields; XSLT only when there are at least 20 leaf fields AND at least one schema. When the translation requires
   `unmarshal`/`marshal` outside that selected engine, verify the data format in the catalog before documenting it:
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

- **DataWeave transformations:** For each flow containing a DataWeave script, load
  `skills/camel-migrate/guides/datamapper-migrate.md` and follow its steps. The guide infers field mappings, collects
  schema paths, confirms them with the user, and uses `skills/shared/datamapper-canonicalize.md` to select and record
  inline Groovy or XSLT. Do not ask ad-hoc mapping questions here — the guide handles it fully.
- **Proprietary connectors:** Confirm the replacement approach decided in Phase 1 and any additional configuration needed (credentials format, endpoint URLs, etc.).
- **Target infrastructure endpoints:** If endpoint URLs, queue names, or topic names are parameterised or missing from the config, ask for the target environment values or confirm they will be externalised to properties.
- **Authentication:** For HTTP endpoints, confirm authentication mechanism (Basic, OAuth2, mTLS, API Key) and where credentials will be stored.

**Do NOT ask about error handling.** Error handlers (`on-error-continue`/`on-error-propagate`), retry policies, DLQ endpoints, and alert mechanisms are extracted from Mule XML in Step 1.1 and recorded in the analysis summary. Use them directly when populating the design spec "Error Handling" section.

---

### Step 2.3 — Update the Pipeline Design Spec

Before the per-flow sections, create or replace this package-level subsection in
`docs/camel-kit/<PIPELINE_ID>/design-spec.md`, with one row per business-requirements strategy scope:

```markdown
### Migration Strategy Constraints

| Scope | Covered Ingress IDs | Classification | Design Obligation | Evidence IDs |
|---|---|---|---|---|
| [scope] | [same non-overlapping ingress IDs from business requirements] | [exact classification from business requirements] | [confirmed seam constraint, bounded single-cutover constraint, or blocking unresolved evidence requirement] | [MIG-###, SRC-###] |
```

For each Mule flow, update the relevant `### Flow: {flow-name}` section in
`docs/camel-kit/<PIPELINE_ID>/design-spec.md`.

Use the same flow-design structure as `camel-brainstorm/guides/design-assembly.md`. The flow section MUST contain all
of the following details:

````markdown
# Flow Design: {flow-name}

## Section 1: Overview

| Field | Value |
|-------|-------|
| Flow Name | {flow-name} |
| Migrated From | MuleSoft Mule [version] — [original-mule-flow-name] |
| Source Module | {relative path from workspace root to the source Mule project} |
| Target Module | {relative path from workspace root to the target Camel project, e.g. `order-service/`} |
| Business Purpose | [from business requirements] |
| Trigger | [how the Camel route is triggered] |
| Camel Version | [from config.properties] |
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

> This table is for migration traceability only. The `### DataMapper:` section below (if present) is what
> `camel-implement` uses to generate the approved inline Groovy or XSLT transformation.

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

> Insert the exact non-empty canonical section emitted by `guides/datamapper-migrate.md` and
> `skills/shared/datamapper-canonicalize.md`; do not synthesize a second schema here. The canonical section records
> `Transformation Engine: Groovy (inline)` plus `Format Pair` for Groovy, or XSLT Pattern/Approach and structural
> mapping columns for XSLT. Generate `.kaoto` metadata only for XSLT.


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

> Only list properties that are valid for the actual Camel component (verified via `camel_catalog_component_doc` in Step 2.1). For `platform-http`, do NOT list host/port as component properties — use the runtime HTTP server property (`camel.server.enabled` plus `camel.server.port` for `main`, `server.port` for `spring-boot`, or `quarkus.http.port` for `quarkus`) instead.

| Property Key | Description | Example Value | Required |
|-------------|-------------|---------------|----------|
| [key] | [description] | [placeholder] | Yes/No |

## Section 8: Dependencies

| Dependency | Maven Coordinates | Notes |
|-----------|-------------------|-------|
| [component name] | `[groupId:artifactId:version]` | [notes] |

## Section 9: Constitution Gate Checks

Constitution v2.0 — eight enforced rules:

- [ ] **MCP Catalog Verification** — every component, EIP, data format, language, and option was verified with runtime/platform BOM
  MCP catalog verification is satisfied by the `Catalog Verification Evidence` block in section 5 of the active design
  spec, as defined in `camel-brainstorm/guides/design-assembly.md`; reference its rows for this flow, do not restate the table.
- [ ] **Route Structure** — route has a `from:` source and a final `to:` sink
- [ ] **Single Responsibility** — route has one clear purpose; ≤ 7 processing steps
- [ ] **Separation of Concerns** — ingestion, processing, and delivery are separate routes where appropriate
- [ ] **Naming Conventions** — route ID follows `<domain>-<action>[-<qualifier>]`
- [ ] **Observability** — `routeId` and `description` declared; correlation ID propagated
- [ ] **External Configuration** — route YAML uses `{{property.key}}` on every runtime and the runtime properties file
  supplies concrete `key=value` entries; no hardcoded credentials or environment-specific route values
- [ ] **Infrastructure via Forage** — infrastructure beans follow the Forage configuration ladder (see `skills/shared/forage.md`)

## Section 10: Testing Strategy

### Happy Path
[Description of the normal-flow test scenario, derived from Mule flow purpose]

### Error Scenarios
[List error scenarios to test, derived from Mule error handlers]

### Migration Validation
- [ ] Output payload matches Mule output (same field names, types, and values)
- [ ] Performance meets SLA requirements from business requirements

## Section 11: Implementation Checklist

- [ ] Ensure `camel-plan` includes an implementation task for this flow
- [ ] Run `camel-execute` to generate Camel YAML and integration tests
- [ ] Verify against original Mule behaviour
````

**Note:** If the business requirements specify performance/throughput requirements, add a **Section 5e: Throttling & Scaling** covering throttle EIP configuration and Kafka `consumersCount`. If security or compliance requirements exist, add a **Section 5f: Security**. If observability requirements exist, add a **Section 5g: Monitoring**. Renumber sections as needed.

---

### Step 2.4 — Complete

After all design spec updates are created, return them to the `camel-migrate`
orchestrator for the design review:

```
Migration analysis complete.

Created files:
- docs/camel-kit/<PIPELINE_ID>/business-requirements.md
- docs/camel-kit/<PIPELINE_ID>/migration-analysis.md
- docs/camel-kit/<PIPELINE_ID>/design-spec.md

Status: Ready for the single design-approval review.
```

In chained mode, `camel-migrate` presents the design and, after approval,
automatically hands off to `camel-plan`, `camel-execute`, and final
`camel-validate`. In standalone mode, write the design package and stop. Do not
instruct the user to invoke downstream commands manually.
