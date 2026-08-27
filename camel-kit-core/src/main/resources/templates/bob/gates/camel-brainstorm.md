---
name: camel-brainstorm
description: Use when the user wants to create a new Camel integration, connect systems, build flows, migrate from another platform (MuleSoft, BizTalk, Fuse, Camel 2.x), or start any integration project — whether greenfield or migration
---

# Camel Brainstorm — Design Pipeline (Bob)

Turn integration ideas into fully formed design specs through collaborative dialogue. Follow every step in order. Do NOT skip steps.

**Core principle:** Understand before designing. Design before planning. Plan before coding.

**Context budget:** You have 100K tokens. The interview consumes ~40K. Be concise in later steps — avoid loading full documents when only headers are needed for approval decisions.

## Valid Pipeline Commands

These are the ONLY user-invocable camel-kit commands. Use these exact names if you must reference them.

| Command | Purpose |
|---|---|
| `/camel-start` | Route the request to the correct Camel-Kit workflow |
| `/camel-brainstorm` | Interview, discovery, design spec |
| `/camel-migrate` | Analyze and design a migration from supported source platforms |
| `/camel-plan` | Decompose spec into implementation tasks |
| `/camel-execute` | Implement and runtime-verify all planned routes; chained runs continue to final validation |
| `/camel-validate` | Standalone validation pass |
| `/camel-ship` | Delegate a Ship workflow to the registered Camel-Kit CLI |
| `/camel-knowledge` | Query Camel documentation, catalog, releases, and security advisories |
| `/camel-debug` | Diagnose and repair a broken Camel project outside a pipeline |

Modes (`camel-implement-mode`, `camel-test-mode`) are internal `switch_mode` targets, NOT user-invocable commands.
Never present them as commands the user should run.

## Guide Locations

All guides are in `.bob/skills/`. Always use these full paths from the project root:

| Reference in steps | Actual path |
|---|---|
| `greenfield-interview.md` | `.bob/skills/camel-brainstorm/guides/greenfield-interview.md` |
| `migration-discovery.md` | `.bob/skills/camel-brainstorm/guides/migration-discovery.md` |
| `migration-graph-analysis.md` | `.bob/skills/camel-brainstorm/guides/migration-graph-analysis.md` |
| `version-selection.md` | `.bob/skills/camel-brainstorm/guides/version-selection.md` |
| `design-assembly.md` | `.bob/skills/camel-brainstorm/guides/design-assembly.md` |
| `camel-version-phase1.md` | `.bob/skills/camel-migrate/guides/camel-version-phase1.md` |
| `camel-version-phase2.md` | `.bob/skills/camel-migrate/guides/camel-version-phase2.md` |
| `orchestrator.md` | `.bob/skills/camel-implement/guides/orchestrator.md` |

Do NOT explore or list directories to find guides — use the paths above.

## Autonomous Execution Rules

After the design approval, planning, implementation, internal verification, and final validation execute as an **uninterrupted sequence**:

1. **No pausing between steps** — After implementation, immediately verify. After verification, immediately validate.
2. **No completion summaries until ALL steps complete** — The ONLY summary is printed after final validation finishes.
3. **No "Next Steps" blocks** — You ARE executing the next step RIGHT NOW.
4. **No asking for confirmation** — The design approval authorizes planning and all remaining steps.
5. **No README generation** — Do NOT generate documentation files mid-pipeline.

<Steps>
<Step>
## Switch to Brainstorm Mode

Switch to **camel-brainstorm-mode** using the mode selector or `/camel-brainstorm-mode`.
This limits edits to design Markdown and Camel-Kit state. Command access is instruction-scoped to document metadata
and graph operations; implementation artifacts remain prohibited during the design phase.
</Step>

<Step>
## Detect Invocation Mode and Resolve Pipeline

- With an explicit `<PIPELINE_ID>`, use standalone mode. If that pipeline already
  has `design-spec.md`, enter amend mode: load it, ask what to change, update and
  self-review only the affected design, request the single design approval, run
  `{COMMAND_PREFIX} doc stale --reason "design spec amended" --cascade docs/camel-kit/<PIPELINE_ID>/implementation-plan.md`
  when that downstream artifact exists, then stop.
- Without an explicit ID, use chained mode and read `activePipeline` from
  `.camel-kit/pipeline.json` when present.
- If neither source yields an ID, ask the user for a lowercase slug, run
  `{COMMAND_PREFIX} nextId <slug>`, and use the returned ID.

For a new or selected pipeline, create or update `.camel-kit/pipeline.json` with
`activePipeline`, `mode: "manual"`, and the current ISO-8601 `started` timestamp.
Create `.camel-kit/` first when needed.
</Step>

<Step>
## Detect Project Type

Determine if this is a greenfield integration or a migration:

**Greenfield indicators:**
- "Create", "build", "connect", "integrate", "new project"
- No existing source artifacts mentioned
- Describes desired end state, not existing state

**Migration indicators:**
- "Migrate", "convert", "move from", "replace"
- Mentions source platform: MuleSoft, Mule, BizTalk, Fuse, Camel 2.x, Camel 3.x
- References existing integration files or projects

If ambiguous, ask: "Are you building a new integration from scratch, or migrating an existing one from another platform?"
</Step>

<Step>
## Load Project Context

Read these files if they exist:
1. `docs/constitution.md` — constitution rules. If missing, copy from `templates/constitution.md`.
2. `.camel-kit/config.properties` — project config (Camel version, runtime). May not exist yet.
3. `docs/camel-kit/<PIPELINE_ID>/business-requirements.md` — existing business requirements (if resuming a project).
</Step>

<Step>
## Run Interview or Discovery

**For greenfield projects:**
Read `.bob/skills/camel-brainstorm/guides/greenfield-interview.md` for the Socratic interview process.
Ask ONE question at a time. Do NOT batch questions.
Understand:
- Systems to connect
- Data flow requirements
- Business logic needs
- Non-functional requirements (security, resilience, monitoring)

**For migration projects:**
Read `.bob/skills/camel-brainstorm/guides/migration-discovery.md` for the discovery process.
Scan source artifacts and detect:
- Vendor (MuleSoft, BizTalk, Fuse, Camel 2.x/3.x)
- Platform (Spring Boot, Karaf, Quarkus, Plain Java)
- DSL (Java, XML, Blueprint, YAML)
- Routes and components
- Migration concerns

If project graph is available, read `.bob/skills/camel-brainstorm/guides/migration-graph-analysis.md` for graph-accelerated analysis.

Confirm all findings with the user.

**Persist findings to disk:** After confirming, create the `docs/` directory if it does not exist, then write a structured summary to `docs/interview-notes.md`:
- Systems identified and their roles
- Data flow requirements and key decisions
- Components discussed and their MCP verification status
- Migration concerns (if migration)

This file survives context condensation and is referenced by later steps.
</Step>

<Step>
## Select Camel Version

Read `.bob/skills/camel-brainstorm/guides/version-selection.md` for the version selection process.

Help the user select:
1. Target Camel version
2. Target runtime (Spring Boot / Quarkus / JBang)
3. Platform BOM version

Store selections in `.camel-kit/config.properties`.
</Step>

<Step>
## Design Flows

For each flow, design the integration using relevant guides from `camel-design/`:
- Component selection: `.bob/skills/camel-design/guides/component-selection.md`
- EIP catalog: `.bob/skills/camel-design/guides/eip-catalog.md`
- Integration patterns: `.bob/skills/camel-design/guides/integration-patterns.md`
- Data formats: `.bob/skills/camel-design/guides/data-formats.md`
- Error handling and resilience: `.bob/skills/camel-design/guides/resilience-interview.md`
- Security: `.bob/skills/camel-design/guides/security.md`

**CRITICAL:** Verify EVERY component via MCP:
1. `camel_catalog_component_doc` — verify component exists

Do NOT guess component names. MCP catalog is truth.
</Step>

<Step>
## Assemble Design Spec

Read `.bob/skills/camel-brainstorm/guides/design-assembly.md` for its assembly
format and self-review criteria only. Do not follow that guide's `Save and
Present` section; this gate owns the one save/presentation/approval sequence.

Assemble the complete design spec including:
- Project overview
- Systems and data flow
- Component selections (all MCP-verified)
- Route designs
- Non-functional requirements

Save to `docs/camel-kit/<PIPELINE_ID>/design-spec.md`.
Run `{COMMAND_PREFIX} doc init --by camel-brainstorm docs/camel-kit/<PIPELINE_ID>/design-spec.md`.
</Step>

<Step>
## Self-Review Design Spec

Scan the design spec for:
- Placeholders ("TBD", "TODO", "fill in")
- Unverified components (did you call MCP for every one?)
- Contradictions between requirements and design
- Missing error handling or security considerations

Fix any issues inline.
</Step>

<Step>
## User Approval

Present the complete design spec to the user.

**APPROVAL GATE — Do NOT proceed without explicit approval:**
"Do you approve this design? (yes / changes needed)"

If changes requested, incorporate and re-present. Only proceed after explicit "yes" or "approved".
</Step>

<Step>
## CHECKPOINT

Before proceeding to planning, this is the design approval checkpoint.
All design decisions are locked in `docs/camel-kit/<PIPELINE_ID>/design-spec.md`. Create a checkpoint now.
</Step>

<Step>
## Complete or Hand Off

Do not derive a second greenfield requirements artifact, modify the approved design, or request another approval.

- **Standalone mode:** stop after confirming the approved design-spec path.
- **Chained mode:** switch to **camel-plan-mode**, then read and follow `.bob/skills/camel-plan/SKILL.md` exactly
  once. That gate owns plan generation and its single downstream handoff through
  execute, verification, and validation. Do not reproduce those phases here.
</Step>
</Steps>

## Never

- Stop after implementation to print a summary or "Next Steps"
- Ask "Would you like me to continue?" between implement, verification, and validation
- Print "Implementation complete" before verification and validation are done
- Skip verification or validation
- Generate a README mid-pipeline instead of continuing to the next step
- Say "implementation has been completed" while steps remain uncompleted
- Reference retired implementation or test commands — these commands do not exist
- Generate Citrus tests in Java or XML — YAML DSL only
- Create a plan without Citrus test tasks for every route

## Iron Laws

Read `shared/iron-laws.md` for the full Iron Laws. This skill enforces:

- **Iron Law 1: MCP Catalog Verification** — Every component, EIP, dataformat, and language MUST be MCP-verified before inclusion.
- **Iron Law 3: No Code Without Spec Approval** — NEVER generate any implementation artifacts before the user has explicitly approved the design spec.

## MCP Tools Used

- `camel_catalog_component_doc` — verify component exists, get options
- `camel_catalog_eip_doc` — verify EIP exists, get configuration
- `camel_catalog_dataformat_doc` — verify dataformat exists
- `camel_catalog_language_doc` — verify expression language exists
- `camel_docs_search` — search docs for guidance

For MCP setup, version mapping, and fallback policy: see `shared/mcp-setup.md`
For graph analysis: use `{COMMAND_PREFIX} graph` CLI commands (see `shared/graph-availability.md`)
