# Camel Migrate BizTalk - Microsoft BizTalk → Apache Camel Migration (Phase 1)

> **Context variables provided by master SKILL.md:**
> - `CAMEL_VERSION` — target Camel version from `.camel-kit/config.yaml`
> - `RUNTIME` — from `.camel-kit/config.yaml` (`project.runtime`, default: `main`)
> - `PLATFORM_BOM` — resolved from `CAMEL_VERSION` + `RUNTIME` via the version mapping table in `skills/shared/mcp-setup.md`
> - Confirmed analysis summary (with ✓/~/? markers) from `camel-migrate`
> - Full list of source artifact paths
> - Detected vendor: Microsoft BizTalk Server (2004/2006/2006 R2/2009/2010/2013/2013 R2/2016/2020)
>
> **"Load" means READ and FOLLOW.** When this guide says "Load `path/to/file.md`", read that file relative to the skill directory and execute its instructions. The files are always present — do NOT report them as missing.

This guide is loaded by `camel-migrate` after it has already:
1. Detected the vendor (Microsoft BizTalk Server)
2. Scanned all project artifacts (`.odx`, `.btm`, `.btp`, `.btproj` files)
3. Built a pre-populated analysis summary
4. Confirmed the summary with the user

**Do not re-ask questions already answered in the summary. Do not load this guide directly.**

You will work in two phases:
- **Phase 1 (Business Analyst):** Deep-dive into BizTalk orchestrations, maps, pipelines, bindings; resolve proprietary adapters; fill any remaining gaps; produce BRD.
- **Phase 2 (Integration Architect):** Design catalog-verified Camel route architecture and produce TDD files.

**Phase 1 of 2.** After completing this phase (BRD generation), the orchestrator dispatches Phase 2 (TDD generation) automatically.

The outputs are identical in format to `/camel-project` + `/camel-flow`, making them fully compatible with `/camel-implement`.

---

## Phase 1 — Business Analyst

### Context Loading

**ALWAYS load at the start of Phase 1:**
- Load `skills/camel-migrate/guides/biztalk-component-mapping.md` — needed for the entire migration
- Read ALL BizTalk artifacts:
  - Orchestration files (`.odx`)
  - Map files (`.btm`)
  - Pipeline files (`.btp`)
  - Binding files (`.xml`)
  - Project files (`.btproj`)
  - Configuration files (binding configuration)
- Read the confirmed analysis summary passed by `camel-migrate` (contains vendor, purpose, SLA, security, failure behaviour, deployment target, API compatibility)

**Conditional:**
- Read `.camel-kit/constitution.md` if it exists (for reference — do not generate or modify it)

---

### Step 1.1 — Parse and Inventory the BizTalk Artifacts

Analyse all BizTalk artifacts and build an orchestration/map/pipeline inventory. This is BizTalk-specific extraction that complements the generic analysis summary already provided.

**For each BizTalk orchestration (`.odx` file), identify:**
- Orchestration name
- Receive ports (inbound endpoints, adapters, pipelines)
- Send ports (outbound endpoints, adapters, pipelines)
- Orchestration shapes in order:
  - Receive/Send shapes (message endpoints)
  - Decide shapes (routing logic)
  - Transform shapes (map transformations)
  - Construct Message shapes (message construction)
  - Expression shapes (XLANG/s code)
  - Loop/Parallel Actions shapes (iteration/parallelism)
  - Call Orchestration shapes (sub-orchestration calls)
  - Scope shapes (error handling)
- Variables and parameters
- Error handlers (Scope shapes with exception handlers, Suspend shapes)
- Correlation sets

**For each BizTalk map (`.btm` file), identify:**
- Source schema (XSD)
- Target schema (XSD)
- Functoids used (String Concatenate, Looping, Scripting, Database Lookup, etc.)
- Complexity rating (Simple / Medium / Complex):
  - Simple: Direct links only
  - Medium: Basic functoids (String, Math, Logical)
  - Complex: Scripting functoids, Database Lookup, custom XSLT

**For each BizTalk pipeline (`.btp` file), identify:**
- Pipeline type (Receive / Send)
- Stages (Decode, Disassemble, Validate, ResolveParty, Assemble, Encode)
- Components per stage (XML Disassembler, JSON Encoder, MIME Decoder, etc.)
- Custom components (flag for manual review)

**For each BizTalk binding configuration, identify:**
- Receive Port → Receive Pipeline mapping
- Send Port → Send Pipeline mapping
- Adapter types and configuration (FILE, FTP, HTTP, MSMQ, SQL, WCF, etc.)

**Classify each adapter/component using `biztalk-component-mapping.md`:**
- Mark adapters with known Camel equivalents
- Mark adapters flagged as **ASK USER** (MSMQ, WCF-Custom, third-party, SAP, Dynamics, etc.)
- Use dependencies from project files to pre-suggest replacement options (e.g., Azure SDK present → suggest `camel-azure-servicebus` for MSMQ)

---

### Step 1.2 — Resolve Proprietary Adapters

For each adapter/component marked **ASK USER**, ask the user — one adapter at a time:

```
I found the following adapter with no direct Apache Camel equivalent:

- **[Adapter Name]** (used in: [orchestration/port name])
  Suggested alternatives based on your project configuration:
  a) [best match from component-mapping.md] — [brief description]
  b) [alternative]
  c) Keep as a TODO placeholder
  d) Remove this step

Your choice?
```

**Special handling for MSMQ adapter:**

```
I found the MSMQ adapter in your BizTalk application.

MSMQ has no direct Apache Camel equivalent. Please choose a replacement:

a) ActiveMQ Artemis (`camel-jms`) — drop-in JMS replacement, supports durable queues
b) RabbitMQ (`camel-rabbitmq`) — AMQP-based messaging
c) Azure Service Bus (`camel-azure-servicebus`) — cloud-native alternative (if migrating to Azure)
d) Keep as TODO placeholder — decide later

Your choice?
```

Record each decision. Use these in Phase 2.

---

### Step 1.3 — Fill Remaining Gaps

Check the confirmed summary from `camel-migrate`. For every field still marked **? Unknown** or **~ Inferred**, ask the user — one question at a time, only if it was not resolved by the BizTalk artifact analysis in Step 1.1.

**Do not ask about fields already marked ✓ Confirmed in the summary.**

API compatibility is assumed by default — Camel routes will preserve the same HTTP paths, queue/topic names, and data contracts as the original BizTalk orchestrations. If the user explicitly stated otherwise during the Step 5 confirmation in `camel-migrate`, note the deviation in the BRD.

If the summary has no remaining gaps, skip this step entirely.

---

### Step 1.4 — Produce Business Requirements Document

Create `docs/business-requirements.md` using the following format:

```markdown
# Business Requirements Document

## Executive Summary

[2–3 sentence description of the integration purpose, origin platform (Microsoft BizTalk Server [version]), and migration goal]

**Migrated from:** Microsoft BizTalk Server [version]
**Migration date:** [current date]
**Original orchestrations:** [count] orchestration(s) detected
**Original maps:** [count] map(s) detected
**Original pipelines:** [count] pipeline(s) detected

## Systems Landscape

| System | Role | Protocol | Direction | Adapter |
|--------|------|----------|-----------|---------|
| [name] | [Source / Sink / Both] | [HTTP / FILE / JMS / SQL / etc.] | [inbound / outbound] | [BizTalk adapter name] |

## Integration Requirements

### [Orchestration Name 1]
- **Purpose:** [business purpose derived from orchestration analysis + user input]
- **Trigger:** [how the orchestration is triggered — receive port adapter + pipeline]
- **Data:** [what data flows through — schemas, maps]
- **Processing:** [key orchestration shapes: Decide, Transform, Loop, etc.]
- **Outcome:** [expected result — send port adapter + pipeline]

[Repeat for each orchestration]

## Constraints

### Technical Constraints
- [Derived from BizTalk artifact analysis: protocols, formats, schemas, etc.]

### Business Constraints
- [From user interview: compliance, SLA, etc.]

### Migration Constraints
- [Cut-over vs. parallel, API compatibility requirements]

## Best Practices

If `.camel-kit/constitution.md` exists, the following rules from it apply to every generated route:
- One Camel route per BizTalk orchestration (Single Responsibility)
- Route IDs follow `<domain>-<action>[-<qualifier>]` naming (Naming Conventions)
- Every route declares a `routeId` and a `description` (Observability)
- All connection parameters externalised to `application.properties` — no hardcoded values (External Configuration)
- Dead Letter Channel for failed messages (Error Handling — enforced by `/camel-validate`)

## Success Criteria

- [ ] All [N] BizTalk orchestrations have an equivalent Camel route
- [ ] Proprietary adapter replacements are documented and agreed
- [ ] All routes pass `/camel-validate`
- [ ] SLA requirements are met under load

## Next Steps

Run `/camel-implement <orchestration-name>` for each orchestration once TDD files are created.

## Appendices

### A. Original BizTalk Orchestrations Inventory

| Orchestration Name | Receive Ports | Send Ports | Transform Shapes | Decide Shapes | Complexity |
|---|---|---|---|---|---|
[One row per orchestration]

### B. BizTalk Maps Complexity Assessment

| Map Name | Source Schema | Target Schema | Functoid Count | Complexity | Manual Review Required |
|---|---|---|---|---|---|
[One row per map — flag Scripting functoids in "Manual Review Required"]

### C. Adapter Decisions

| Adapter | Original Port | Decision | Camel Replacement |
|---|---|---|---|
[One row per proprietary adapter decision — MSMQ, WCF-Custom, third-party, etc.]
```

---

### Phase 1 Complete

Report:
```
Phase 1 complete.

Created:
- docs/business-requirements.md

Orchestrations to migrate: [list orchestration names]

Starting Phase 2 — Integration Architect...
```

**Phase 1 complete.** The orchestrator will now dispatch Phase 2 (`biztalk-phase2.md`) to generate TDD files.

---
