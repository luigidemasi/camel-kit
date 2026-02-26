---
name: camel-migrate-mule
description: Internal sub-skill for migrating MuleSoft Mule integrations to Apache Camel
user-invocable: false
metadata:
  version: "1.0.0"
  author: "camel-kit"
  category: "migration"
  license: "Apache-2.0"
---

# Camel Migrate Mule - MuleSoft → Apache Camel Migration

This is an internal sub-skill invoked by `camel-migrate` when a MuleSoft Mule project is detected. Do not invoke this skill directly.

You will work in two phases:
- **Phase 1 (Business Analyst):** Understand the business context and produce BRD + constitution.
- **Phase 2 (Integration Architect):** Design the Camel route architecture and produce TDD files.

The outputs of both phases are identical in format to those produced by `/camel-project` + `/camel-flow`, making them fully compatible with `/camel-implement`.

---

## Phase 1 — Business Analyst

### Context Loading

**ALWAYS load at the start of Phase 1:**
- Load `skills/camel-migrate-mule/guides/mule-component-mapping.md` — you will need this for the entire migration
- Read ALL Mule XML files provided (flows, sub-flows, global configuration)
- Read `pom.xml` if present (to determine Mule version and dependencies)

**Conditional:**
- Load `.camel-kit/constitution.md` if it already exists (do not overwrite without asking)

---

### Step 1.1 — Parse and Inventory the Mule Project

Before asking any questions, analyse all provided Mule XML files and build an internal inventory:

**For each Mule flow and sub-flow, identify:**
- Flow name
- Source (inbound endpoint, HTTP listener, scheduler, etc.)
- Processors in order (transformers, routers, filters, enrichers)
- DataWeave transformation scripts (note their location and complexity)
- Sink (outbound endpoint, HTTP request, database write, etc.)
- Error handlers (on-error-continue, on-error-propagate)

**Classify each component using the mapping guide** (`mule-component-mapping.md`):
- Mark components that have known Camel equivalents
- Mark components flagged as **ASK USER** (proprietary connectors with no direct equivalent)

---

### Step 1.2 — Resolve Proprietary Connectors

For each component marked **ASK USER** in the mapping guide, ask the user before proceeding:

```
I found the following connector(s) in your Mule project that have no direct Apache Camel equivalent:

[For each proprietary connector:]
- **[Connector Name]** (used in flow: [flow-name])
  Suggested alternatives: [list from mapping guide]

  How would you like to handle this?
  a) Replace with [suggested alternative] — [brief description]
  b) Replace with a different component (please specify)
  c) Remove this step (it will be omitted from the migration)
  d) Keep as a TODO placeholder in the TDD (I'll decide later)
```

Record the user's decision for each proprietary connector. You will use these decisions in Phase 2.

---

### Step 1.3 — Business Interview

Ask ONLY questions that the XML files cannot answer. Ask them **one at a time**, in order. Wait for the user's answer before moving to the next question. Skip any question whose answer is clearly determinable from the XML.

Introduce the interview with:
```
I have a few business questions that I can't answer from the Mule config alone.
I'll ask them one at a time — feel free to answer with as much or as little detail as you have.
```

Then ask each question individually and wait for the response:

**Question 1 — Business Purpose**
```
What is the business purpose of this integration, and what problem does it solve?
(What value does it deliver to the business?)
```

**Question 2 — Owning Team / Stakeholders** *(optional — skip if the user is unsure or it is not relevant)*
```
Do you know which team owns this integration or who the key stakeholders are?
(Feel free to skip this if you don't have that information handy.)
```

**Question 3 — SLA / Performance Requirements**
```
What are the performance expectations?
- Throughput: how many messages or transactions per hour/day?
- Latency: real-time (< 1s), near-real-time (< 10s), or batch?
- Are there peak load periods to plan for?
```

**Question 4 — Compliance and Security**
```
Are there any compliance or security requirements?
(e.g. GDPR, PCI-DSS, HIPAA, data classification, audit logging)
If none apply, just say "no".
```

**Question 5 — Business Failure Behaviour**
```
What should happen from a business perspective when this integration fails?
- Should failed messages be retried, dead-lettered, or discarded?
- Who (if anyone) should be notified on failure?
```

**Question 6 — Migration Constraints**
```
A few final questions about the migration itself:
- Is this a direct cut-over or will both systems run in parallel for a while?
- Must the Camel implementation be API-compatible with the existing Mule version?
```

---

### Step 1.4 — Produce Business Requirements Document

Create `.camel-kit/business-requirements.md` using the following format:

```markdown
# Business Requirements Document

## Executive Summary

[2–3 sentence description of the integration purpose, origin platform (MuleSoft Mule X.x), and migration goal]

**Migrated from:** MuleSoft Mule [version]
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
- [Derived from Mule XML analysis: protocols, formats, etc.]

### Business Constraints
- [From user interview: compliance, SLA, etc.]

### Migration Constraints
- [Cut-over vs. parallel, API compatibility requirements]

## Best Practices

- Follow Apache Camel DSL best practices per `.camel-kit/constitution.md`
- One Camel route per Mule flow
- Use Dead Letter Channel for failed messages
- Externalise all connection parameters to `application.properties`

## Success Criteria

- [ ] All [N] Mule flows have an equivalent Camel route
- [ ] Proprietary connector replacements are documented and agreed
- [ ] All flows pass `/camel-validate`
- [ ] SLA requirements are met under load

## Next Steps

Run `/camel-implement <flow-name>` for each flow once TDD files are created.

## Appendices

### A. Original Mule Flows Inventory

| Flow Name | Source | Processors | Sink | Proprietary Connectors |
|-----------|--------|------------|------|----------------------|
[One row per flow]

### B. Proprietary Connector Decisions

| Connector | Original Flow | Decision | Camel Replacement |
|-----------|--------------|----------|-------------------|
[One row per proprietary connector decision]
```

---

### Step 1.5 — Produce Constitution

Check if `.camel-kit/constitution.md` already exists:

- **If it exists:** Read it and confirm with the user whether to keep it or update it.
- **If it does not exist:** Create it using the same minimal constitution format that `/camel-project` produces. Load `skills/camel-project/guides/constitution-template.md` for the format reference, then create `.camel-kit/constitution.md`.

---

### Phase 1 Complete

Report:
```
Phase 1 complete.

Created:
- .camel-kit/business-requirements.md
- .camel-kit/constitution.md

Flows to migrate: [list flow names]

Starting Phase 2 — Integration Architect...
```

---

## Phase 2 — Integration Architect

### Context Loading

**ALWAYS load at the start of Phase 2:**
- Load `skills/camel-migrate-mule/guides/mule-dataweave-conversion.md` — required for DataWeave analysis
- Re-read `.camel-kit/business-requirements.md` and `.camel-kit/constitution.md`
- Re-read `.camel-kit/config.yaml` if it exists (for Camel version)
- Load `skills/camel-flow/guides/eip-catalog.md` — for EIP mapping decisions

**Conditionally load:**
- `skills/camel-migrate-mule/guides/datamapper-migrate.md` — load once per flow that contains a DataWeave transformation (see Step 2.2)
- `skills/camel-flow/guides/performance.md` — if SLA requirements are strict
- `skills/camel-flow/guides/security.md` — if compliance requirements exist
- `skills/camel-flow/guides/monitoring.md` — if observability requirements exist

---

### Step 2.1 — Design Camel Route Architecture for Each Flow

For each Mule flow identified in Phase 1:

1. Map each Mule component to its Camel equivalent using `mule-component-mapping.md`.
2. Apply the proprietary connector decisions from Step 1.2.
3. Translate DataWeave transformations using `mule-dataweave-conversion.md`.
4. Identify Camel EIPs that correspond to Mule routing constructs (choice → CBR, scatter-gather → multicast, etc.).
5. Map Mule sub-flows to Camel `direct:` routes.
6. Map Mule error handlers to Camel `doTry/doCatch` or Dead Letter Channel.

---

### Step 2.2 — Technical Interview (Per Flow)

Ask ONLY questions that cannot be answered from the Mule XML. Group questions per flow.

**For each flow, ask if not determinable from config:**

- **DataWeave transformations:** For each flow containing a DataWeave script, load `skills/camel-migrate-mule/guides/datamapper-migrate.md` and follow its steps. The guide will infer field mappings from the DataWeave code, collect schema paths, confirm with the user, and append a `### DataMapper: kaoto-datamapper-{id}` section to the TDD. Do not ask ad-hoc mapping questions here — the guide handles it fully.
- **Proprietary connectors:** Confirm the replacement approach decided in Phase 1 and any additional configuration needed (credentials format, endpoint URLs, etc.).
- **Target infrastructure endpoints:** If endpoint URLs, queue names, or topic names are parameterised or missing from the config, ask for the target environment values or confirm they will be externalised to properties.
- **Error handling strategy:** If not fully defined in the Mule config, ask: retry count, retry delay, dead-letter queue/topic name, alert mechanism.
- **Authentication:** For HTTP endpoints, confirm authentication mechanism (Basic, OAuth2, mTLS, API Key) and where credentials will be stored.

---

### Step 2.3 — Produce TDD Files

For each Mule flow, create `.camel-kit/flows/{flow-name}/{flow-name}.tdd.md`.

Use the **exact same TDD format** as `/camel-flow` output. The file MUST contain all of the following sections:

```markdown
# Technical Design Document: {flow-name}

## Section 1: Overview

| Field | Value |
|-------|-------|
| Flow Name | {flow-name} |
| Migrated From | MuleSoft Mule [version] — [original-mule-flow-name] |
| Business Purpose | [from BRD] |
| Trigger | [how the Camel route is triggered] |
| Camel Version | [from config.yaml or constitution] |
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

> Generated by `guides/datamapper-migrate.md`. Include this section only when the Mule flow contains a DataWeave transformation. `camel-implement` reads this section to generate the XSLT and `.kaoto` metadata.

**Mapping ID:** `kaoto-datamapper-{8hexchars}`
**Migrated from DataWeave:** `{path-to-dataweave-file-or-"inline"}`
**Source:** {XML_SCHEMA | JSON_SCHEMA | Primitive} — `{source-schema-path or "none"}`
**Target:** {XML_SCHEMA | JSON_SCHEMA | Primitive} — `{target-schema-path or "none"}`

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

| Source Field | Src Type | Target Field | Tgt Type | Transformation | How |
|---|---|---|---|---|---|

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

| Property Key | Description | Example Value | Required |
|-------------|-------------|---------------|----------|
| [key] | [description] | [placeholder] | Yes/No |

## Section 8: Dependencies

| Dependency | Maven Coordinates | Notes |
|-----------|-------------------|-------|
| [component name] | `[groupId:artifactId:version]` | [notes] |

## Section 9: Constitution Gate Checks

- [ ] One route per flow (no mega-routes)
- [ ] All connection parameters externalised to `application.properties`
- [ ] Dead Letter Channel configured for error handling
- [ ] No hardcoded credentials or secrets
- [ ] Route IDs follow naming convention: `[flow-name]-route`
- [ ] Logging at appropriate levels (INFO for business events, DEBUG for technical details)

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

**Note:** If the BRD specifies performance, security, or monitoring requirements, also include the corresponding conditional sections from the `camel-flow` TDD format (Sections 6/7/8 for Performance/Security/Monitoring, renumbering as needed).

---

### Step 2.4 — Complete

After all TDD files are created, report:

```
Migration analysis complete.

Created files:
- .camel-kit/business-requirements.md
- .camel-kit/constitution.md
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
