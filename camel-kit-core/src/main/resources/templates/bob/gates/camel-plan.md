---
name: camel-plan
description: Use when the user has an approved design spec and needs a detailed implementation plan — invoked by camel-brainstorm after spec approval, or directly if a design spec already exists
---

# Camel Plan — Planning Pipeline (Bob)

Write comprehensive implementation plans assuming the engineer has zero context and questionable taste. Document everything they need to know: which files to touch for each task, which guides to load, which MCP tools to call, how to verify. Give them the whole plan as bite-sized tasks. DRY. YAGNI.

Follow every step in order. Do NOT skip steps.

**Core principle:** The plan contains detailed instructions on HOW to generate code, NOT the generated code itself.

## Guide Locations

All planning guides are in `.bob/skills/camel-plan/guides/`. When this file says `guides/X.md`, read `.bob/skills/camel-plan/guides/X.md`. Do NOT explore or list directories to find guides.

<Steps>
<Step>
## Switch to Plan Mode

Switch to **camel-plan-mode** using the mode selector or `/camel-plan-mode`.
This limits edits to planning Markdown and Camel-Kit state. Command access is instruction-scoped to document metadata
and staleness operations; implementation artifacts remain prohibited during the planning phase.
</Step>

<Step>
## Detect Invocation Mode

- **Chained mode:** activated by `camel-brainstorm` or `camel-migrate` after
  design approval. Inherit their pipeline ID and continue automatically to
  `camel-execute` after writing the plan.
- **Standalone mode:** invoked directly as `/camel-plan` or with a pipeline ID.
  Write the plan and stop; standalone mode suppresses automatic transitions.

For standalone mode, use the explicit `<PIPELINE_ID>` when supplied; otherwise
read `activePipeline` from `.camel-kit/pipeline.json`. If neither identifies an
existing pipeline with `design-spec.md`, stop and direct the user to
`camel-brainstorm`; do not create an empty pipeline for standalone planning.
</Step>

<Step>
## Verify Approved Design Spec Exists

Read `docs/camel-kit/<PIPELINE_ID>/design-spec.md` (or the specified design spec path).

If the spec hasn't been approved, STOP and return to camel-brainstorm.
The plan is based on an APPROVED design spec only.
</Step>

<Step>
## Scope Check

If the design spec covers multiple independent subsystems or has more than ~10 flows, suggest breaking into separate plans — one per subsystem or logical group.

Each plan should produce working, testable software on its own. Record the
decomposition in the plan and continue without adding another approval gate.
</Step>

<Step>
## Load Task Template

Load the appropriate task template based on project type:
- Greenfield: `guides/task-template-greenfield.md`
- Migration: `guides/task-template-migration.md`
- Testing: `guides/task-template-testing.md`

Load decomposition rules: `guides/task-decomposition.md`
</Step>

<Step>
## Decompose into Bite-Sized Tasks

Break the design spec into tasks. Each task should:
- Have ONE clear outcome
- Touch a specific set of files
- Be completable in one focused work session
- Include verification steps

For each task, specify:
- Which files to create/modify (exact paths)
- Matching structured metadata with file actions, logical provides/consumes resources, and explicit dependsOn IDs
- Which guides to load and in what order
- Which MCP tools to call and with what parameters
- Which constitution rules to check
- How to verify completion (commands to run, expected output)
- Ordered review specification (same-session adversarial pre-filter, then spec compliance, then code quality)
</Step>

<Step>
## Generate Plan Document

Save the plan to `docs/camel-kit/<PIPELINE_ID>/implementation-plan.md`.

Use this format:

````markdown
# [Project Name] Implementation Plan

> **For agentic workers:** Use camel-execute to implement this plan task-by-task.
> Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** [One sentence from design spec]

**Architecture:** [2-3 sentences about approach]

**Tech Stack:** Apache Camel [version], [runtime], [key components]

**Design Spec:** `docs/camel-kit/<PIPELINE_ID>/design-spec.md` (approved [date])

---

```yaml plan-metadata
tasks:
  - id: 1
    title: [Component/Flow Name]
    files:
      creates:
        - exact/path/to/file
      modifies:
        - exact/path/to/existing
    provides:
      routes:
        - [route-id]
      endpoints:
        - direct:[endpoint-name]
    consumes:
      properties:
        - [property.name]
      schemas:
        - [schema-name]
    dependsOn: []
```

---

### Task N: [Component/Flow Name]

**Role:** [agent persona to assume in the current Bob session]

**Files:**
- Create: `exact/path/to/file`
- Modify: `exact/path/to/existing`

**Guides to Load:**
- `camel-implement/guides/orchestrator.md`
- `camel-implement/guides/yaml-structure.md`
- [other guides specific to this task]

**MCP Tools:**
- `camel_catalog_component_doc(component="X", runtime="Y", platformBom="Z")`
- [other MCP calls]

**Design Spec Section:** Section 3, Flow: [flow-name]

- [ ] **Step 1:** [action description with exact instructions]
- [ ] **Step 2:** [action description]
- [ ] **Step N:** Verify: [exact command to run and expected output]

**Review:**
- [ ] Adversarial pre-filter: [applicable critic lenses and evidence to inspect]
- [ ] Spec compliance: [what to check — components match design spec, structure correct, properties complete]
- [ ] Code quality: [what to check — constitution rules, security, anti-patterns]
````
</Step>

<Step>
## Self-Review Plan

Scan the plan for:

1. **Spec coverage:** Can every flow in the design spec be mapped to a task? List any gaps.
2. **Placeholder scan:** Search for "TBD", "TODO", "fill in", "similar to Task N". Fix them.
3. **Guide consistency:** Do all tasks reference the correct guide paths?
4. **Review completeness:** Does every implementation task specify the same-session adversarial pre-filter followed by spec compliance and code quality review?
5. **Verification completeness:** Does every task have a verification step with an exact command and expected output?

Fix any issues inline.

Run `{COMMAND_PREFIX} doc init --by camel-plan --from design-spec.md docs/camel-kit/<PIPELINE_ID>/implementation-plan.md`.
If an execution report already exists, run
`{COMMAND_PREFIX} doc stale --reason "implementation plan regenerated" --cascade docs/camel-kit/<PIPELINE_ID>/execution-report.md`.
</Step>

<Step>
## Complete or Hand Off

- **Standalone mode:** write the plan and stop. Do not transition.
- **Chained mode:** switch to **camel-execute-mode**, read
  `.bob/skills/camel-execute/SKILL.md`, and follow that full gate without
  abbreviation. It owns the environment probe, every task's same-session
  adversarial/spec/quality review stack, cross-cutting review, internal
  verification, reports, checkpoint, and final report-only validation. Do not
  pause or request another approval.
</Step>
</Steps>

## What Goes IN the Plan

- Which files to create/modify (exact paths)
- Structured `yaml plan-metadata` for every task, including `files`, logical `provides`/`consumes`, and `dependsOn`
- Which guides to load and in what order
- Which MCP tools to call and with what parameters
- Which constitution rules to check
- How to verify the task is complete (commands to run, expected output)
- Same-session adversarial pre-filter plus ordered spec and quality review per task

## What Does NOT Go in the Plan

- Generated YAML route content
- Generated application.properties content
- Generated Java code
- Generated POM dependencies
- Any artifact that the execution phase produces

The plan is a RECIPE, not the MEAL.

## Iron Laws

Read `shared/iron-laws.md` for the full Iron Laws. This phase enforces:

- **Iron Law 3: No Code Without Spec Approval** — The plan is based on an APPROVED design spec.
- **Iron Law 4: Spec Compliance Before Quality** — The plan MUST specify spec compliance before quality for every implementation task.
- **Iron Law 5: Adversarial Code Review** — The plan MUST place Bob 1's same-session critic-lens pre-filter before staged review and require the isolation limitation to be recorded.
