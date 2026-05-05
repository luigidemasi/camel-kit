---
name: camel-brainstorm
description: Use when the user wants to create a new Camel integration, connect systems, build flows, migrate from another platform (MuleSoft, Fuse, Camel 2.x), or start any integration project — whether greenfield or migration
---

# Camel Brainstorm — Design Pipeline (Bob)

Turn integration ideas into design specs through collaborative dialogue. Follow every step in order.

**Core principle:** Understand before designing. Design before planning. Plan before coding.

**Context budget:** You have 100K tokens. The interview (steps 1-7) uses ~40K. Steps 8-14 MUST use `dispatchSubagent` for all document generation to stay within budget. Your role after step 7 is ORCHESTRATOR: dispatch, review summaries, handle approvals.

## Guide Locations

| Reference | Path |
|---|---|
| `greenfield-interview.md` | `.bob/skills/camel-brainstorm/guides/greenfield-interview.md` |
| `migration-discovery.md` | `.bob/skills/camel-brainstorm/guides/migration-discovery.md` |
| `migration-graph-analysis.md` | `.bob/skills/camel-brainstorm/guides/migration-graph-analysis.md` |
| `version-selection.md` | `.bob/skills/camel-brainstorm/guides/version-selection.md` |
| `design-assembly.md` | `.bob/skills/camel-brainstorm/guides/design-assembly.md` |
| `camel-version-phase1.md` | `.bob/skills/camel-migrate/guides/camel-version-phase1.md` |
| `camel-version-phase2.md` | `.bob/skills/camel-migrate/guides/camel-version-phase2.md` |
| `orchestrator.md` | `.bob/skills/camel-implement/guides/orchestrator.md` |

Do NOT explore directories to find guides — use the paths above.

## Valid Pipeline Commands

| Command | Purpose |
|---|---|
| `/camel-brainstorm` | Interview, discovery, design spec |
| `/camel-plan` | Decompose spec into implementation tasks |
| `/camel-execute` | Implement all routes (includes validation and testing) |
| `/camel-validate` | Standalone validation pass |
| `/camel-ship` | Autonomous end-to-end pipeline |

Modes (`camel-implement`, `camel-test`) are internal `switch_mode` targets, NOT user-invocable commands. Never present them as commands.

<Steps>
<Step>
## Step 1: Switch to Brainstorm Mode

Switch to **camel-brainstorm** mode. This restricts tools to read, markdown editing, MCP, and browser.
</Step>

<Step>
## Step 2: Detect Project Type

Determine greenfield or migration:
- **Greenfield:** "create", "build", "connect", "new project", no existing artifacts
- **Migration:** "migrate", "convert", "move from", mentions MuleSoft/Fuse/Camel 2.x/3.x

If ambiguous, ask: "Are you building a new integration or migrating an existing one?"
</Step>

<Step>
## Step 3: Load Project Context

Read if they exist: `docs/constitution.md`, `.camel-kit/config.properties`, `docs/business-requirements.md`.
If constitution is missing, copy from `templates/constitution.md`.
</Step>

<Step>
## Step 4: Run Interview or Discovery

**Greenfield:** Read `greenfield-interview.md`. Ask ONE question at a time. Understand systems, data flow, business logic, NFRs.

**Migration:** Read `migration-discovery.md`. Scan source artifacts: vendor, platform, DSL, routes, components. If graph available, read `migration-graph-analysis.md`.

Confirm findings with the user.

**IMPORTANT — Persist findings to disk:** After confirming with the user, write a structured summary of all interview findings to `docs/interview-notes.md`:
- Systems identified and their roles
- Data flow requirements
- Key decisions made during the interview
- Components discussed and their MCP verification status
- Migration concerns (if migration)

This file survives context condensation and is read by subagents in later steps.
</Step>

<Step>
## Step 5: Select Camel Version

Read `version-selection.md`. Help select target Camel version, runtime (Spring Boot / Quarkus / JBang), and platform BOM. Store in `.camel-kit/config.properties`.
</Step>

<Step>
## Step 6: Design Flows and Assemble Spec

Read `design-assembly.md`. For each flow, design the integration. Verify EVERY component via `camel_catalog_component` MCP tool — do NOT guess names.

Assemble and save to `docs/design-spec.md`. Self-review for placeholders, unverified components, contradictions. Fix inline.
</Step>

<Step>
## Step 7: User Approval of Design

Present the design spec. **APPROVAL GATE:** "Do you approve this design? (yes / changes needed)"

Only proceed after explicit approval. Create a **CHECKPOINT** after approval.
</Step>

<Step>
## Step 8: Dispatch BRD Generation

Switch to **camel-plan** mode. Use `dispatchSubagent` to generate the BRD in a fresh context:

```
dispatchSubagent(
  task: "Read docs/design-spec.md and docs/interview-notes.md.
         Read .bob/skills/camel-migrate/guides/camel-version-phase1.md for BRD format.
         Generate a Business Requirements Document.
         Save to docs/business-requirements.md.",
  mode: "plan",
  approvalMode: "auto_edit",
  filesContext: ["docs/design-spec.md", "docs/interview-notes.md"]
)
```

Verify `docs/business-requirements.md` was created by reading its first 10 lines.
</Step>

<Step>
## Step 9: Dispatch TDD Generation

Use `dispatchSubagent` to generate TDDs for each flow:

```
dispatchSubagent(
  task: "Read docs/design-spec.md and docs/business-requirements.md.
         Read .bob/skills/camel-migrate/guides/camel-version-phase2.md for TDD format.
         Generate Technical Design Documents for each flow.
         Save to docs/flows/{flow-name}/{flow-name}.tdd.md.
         Each TDD specifies: endpoints, transformations, error handling,
         component configurations, test criteria.",
  mode: "plan",
  approvalMode: "auto_edit",
  filesContext: ["docs/design-spec.md", "docs/business-requirements.md"]
)
```

Verify TDD files were created.
</Step>

<Step>
## Step 10: User Approval of BRD and TDDs

Present summaries of the BRD and TDDs to the user. Read only the section headings and key decisions — do NOT load full documents into your context.

**APPROVAL GATE:** "The design documents are ready. Do you approve? (yes / changes needed)"

Wait for explicit approval.
</Step>

<Step>
## Step 11: Dispatch Plan Generation

Use `dispatchSubagent` to generate the implementation plan:

```
dispatchSubagent(
  task: "Read docs/design-spec.md, docs/business-requirements.md, and all TDD files
         in docs/flows/. Read .bob/skills/camel-plan/SKILL.md for planning rules.
         Read .bob/skills/camel-plan/guides/task-decomposition.md for decomposition rules.
         Load the appropriate template: task-template-greenfield.md or task-template-migration.md.
         Generate a step-by-step implementation plan at docs/implementation-plan.md.
         The plan is a RECIPE — instructions on HOW to generate code, NOT the code itself.
         Self-review for spec coverage, placeholders, type consistency.",
  mode: "plan",
  approvalMode: "auto_edit",
  filesContext: ["docs/design-spec.md", "docs/business-requirements.md"]
)
```

Verify `docs/implementation-plan.md` was created. Read first 20 lines to confirm header and task count.

**APPROVAL GATE:** "The implementation plan is ready. Do you approve? (yes / changes needed)"

Wait for explicit approval.
</Step>

<Step>
## Step 12: Dispatch Execute → Validate → Test Pipeline

Create a **CHECKPOINT** before execution.

Use `dispatchSubagent` to run the full implementation pipeline in a fresh context:

```
dispatchSubagent(
  task: "You are an implementation agent. Read docs/implementation-plan.md for the
         full task list. Read .bob/skills/camel-execute/SKILL.md for execution rules.
         Read .bob/skills/camel-implement/guides/orchestrator.md for implementation rules.

         Execute ALL tasks in the plan:
         1. For each task: load guides, implement, verify, two-stage review, commit
         2. After all tasks: switch to validation mode, run constitution checks,
            generate validation report at docs/validation-report.md
         3. After validation: write and run integration tests, verify all pass

         Use graph topology ({COMMAND_PREFIX} graph route-topology) to identify
         independent routes and implement them in the correct dependency order.

         Report final status: routes implemented, validation result, test results.",
  mode: "advanced",
  approvalMode: "yolo",
  timeoutSeconds: 600,
  filesContext: ["docs/implementation-plan.md", "docs/design-spec.md"]
)
```

Review the dispatch summary. If successful, print completion summary:

```
===============================================================
IMPLEMENTATION COMPLETE
===============================================================

Routes Implemented: [N]
Validation: PASS/FAIL
Tests: PASS/FAIL ([N] passing, [M] failing)

Generated Files:
  [list from dispatch summary]

Constitution Compliance: PASS/FAIL
===============================================================
```

If the dispatch failed or timed out, report the failure and suggest next steps using valid commands only.
</Step>
</Steps>

## Never

- Reference `/camel-implement` or `/camel-test` — these commands do not exist
- Generate BRD, TDD, plan, or implementation code inline — always dispatch
- Load full documents into your context when only headers are needed for approval
- Print "Next Steps" with manual commands — you are the orchestrator, dispatch the work
- Skip validation or testing

## Iron Laws

Read `shared/iron-laws.md`. This skill enforces:
- **Iron Law 1:** Every component MUST be MCP-verified before inclusion
- **Iron Law 3:** NEVER generate implementation artifacts before user approval

## MCP Tools

- `camel_catalog_component` — verify component exists
- `camel_catalog_eip` — verify EIP exists
- `camel_catalog_dataformat` / `camel_catalog_language` — verify formats/languages
- `camel_knowledge_search` — search docs
- `dispatchSubagent` — dispatch work to fresh subagent (steps 8-12)
- `dispatchParallel` — dispatch independent routes concurrently
- `dispatchStatus` — check dispatch progress

For MCP setup: see `shared/mcp-setup.md`. For graph analysis: `{COMMAND_PREFIX} graph` (see `shared/graph-availability.md`)
