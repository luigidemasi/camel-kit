# Camel Migrate Mule - MuleSoft → Apache Camel Migration

> **Context variables provided by master SKILL.md:**
> - `CAMEL_VERSION` — target Camel version from `.camel-kit/config.properties`
> - `RUNTIME` — from `.camel-kit/config.properties` (`project.runtime`, default: `main`)
> - `PLATFORM_BOM` — resolved from `CAMEL_VERSION` + `RUNTIME` via the version mapping table in `skills/shared/mcp-setup.md`
> - Confirmed analysis summary (with ✓/~/? markers) from `camel-migrate`
> - Full list of source artifact paths
> - Detected vendor: MuleSoft Mule (3.x or 4.x)

> **"Load" means READ and FOLLOW.** When this guide says "Load `path/to/file.md`", read that file relative to the skill directory and execute its instructions. The files are always present — do NOT report them as missing.

This guide is loaded by `camel-migrate` after it has already:
1. Detected the vendor (MuleSoft Mule 3.x / 4.x)
2. Scanned all project artifacts
3. Built a pre-populated analysis summary
4. Confirmed the summary with the user

**Do not re-ask questions already answered in the summary. Do not load this guide directly.**

You will work in two phases:
- **Phase 1 (Business Analyst):** Deep-dive into Mule XML flows, resolve proprietary connectors, fill any remaining gaps, produce business requirements.
- **Phase 2 (Integration Architect):** Design catalog-verified Camel route architecture and produce design spec updates.

**Phase 1 of 2.** After completing this phase (business requirements generation), the orchestrator dispatches Phase 2 (design spec generation) automatically.

The outputs use the active Camel Kit pipeline package, making them compatible with `camel-plan` and `camel-execute`.

---

## Phase 1 — Business Analyst

### Context Loading

**ALWAYS load at the start of Phase 1:**
- Load `skills/camel-migrate/guides/mule-component-mapping.md` — needed for the entire migration
- Read ALL Mule XML files (flows, sub-flows, global configuration)
- Read the confirmed analysis summary passed by `camel-migrate` (contains vendor, purpose, SLA, security, failure behaviour, deployment target, API compatibility)

**Conditional:**
- Read `docs/constitution.md` if it exists (for reference — do not generate or modify it)

---

### Step 1.1 — Parse and Inventory the Mule Flows

Analyse all Mule XML files and build a flow inventory. This is Mule-specific extraction that complements the generic analysis summary already provided.

**For each Mule flow and sub-flow, identify:**
- Flow name
- Source (inbound endpoint, HTTP listener, scheduler, etc.)
- Processors in order (transformers, routers, filters, enrichers)
- DataWeave transformation scripts (location and complexity)
- Sink (outbound endpoint, HTTP request, database write, etc.)
- Error handlers (`on-error-continue`, `on-error-propagate`, retry policies, DLQ endpoints)

**Classify each component using `mule-component-mapping.md`:**
- Mark components with known Camel equivalents
- Mark components flagged as **ASK USER** (proprietary connectors with no direct equivalent)
- Use dependencies from `pom.xml` to pre-suggest replacement options for proprietary connectors (e.g. AWS SDK present → suggest `camel-aws2-sqs` for Anypoint MQ)

---

### Step 1.2 — Resolve Proprietary Connectors

For each component marked **ASK USER**, ask the user — one connector at a time:

```
I found the following connector with no direct Apache Camel equivalent:

- **[Connector Name]** (used in: [flow-name])
  Suggested alternatives based on your project dependencies:
  a) [best match from pom.xml analysis] — [brief description]
  b) [alternative]
  c) Provide another MCP-verified replacement
  d) Remove this step

Your choice?
```

Record each decision. Use these in Phase 2.

---

### Step 1.3 — Fill Remaining Gaps

Check the confirmed summary from `camel-migrate`. For every field still marked **? Unknown** or **~ Inferred**, ask the user — one question at a time, only if it was not resolved by the Mule XML analysis in Step 1.1.

**Do not ask about fields already marked ✓ Confirmed in the summary.**

API compatibility is assumed by default — Camel routes will preserve the same HTTP paths, queue/topic names, and data contracts as the original Mule flows. If the user explicitly stated otherwise during the Step 5 confirmation in `camel-migrate`, note the deviation in the business requirements.

If the summary has no remaining gaps, skip this step entirely.

---

### Step 1.4 — Produce Business Requirements Document

Create `docs/camel-kit/<PIPELINE_ID>/business-requirements.md` using the following format:

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

The following rules from `docs/constitution.md` apply to every generated route:
- One Camel route per Mule flow (Single Responsibility)
- Route IDs follow `<domain>-<action>[-<qualifier>]` naming (Naming Conventions)
- Every route declares a `routeId` and a `description` (Observability)
- All connection parameters externalised to `application.properties` — no hardcoded values (External Configuration)
- The future `/camel-validate` report should flag missing error handling where the approved design requires it

## Success Criteria

- [ ] All [N] Mule flows have an equivalent Camel route
- [ ] Proprietary connector replacements are documented and agreed
- [ ] `/camel-validate` findings are reviewed and resolved or explicitly accepted
- [ ] SLA requirements are met under load

## Next Steps

Continue to `camel-plan` and `camel-execute` once the design spec is approved.

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

### Phase 1 Complete

Report:
```
Phase 1 complete.

Created:
- docs/camel-kit/<PIPELINE_ID>/business-requirements.md

Flows to migrate: [list flow names]

Starting Phase 2 — Integration Architect...
```

**Phase 1 complete.** The orchestrator will now dispatch Phase 2 (`mulesoft-phase2.md`) to generate design spec updates.

---
