---
name: camel-ship
description: Run the full pipeline autonomously with configurable oversight.
user_invocable: false
---

# Camel Ship — Autonomous Pipeline

Chain the full camel-kit pipeline (brainstorm → plan → execute → validate) with configurable human oversight.

**Announce at start:** "I'm using the camel-ship skill to run the full pipeline."

**Violating the letter of these rules is violating the spirit of these rules.**

---

## Arguments

Parse the skill arguments for these flags:

| Argument | Default | Description |
|---|---|---|
| `[input-file]` | none | Requirements document, design spec, or brainstorm notes |
| `--ask` | `smart` | Oversight level: `always`, `smart`, or `never` |
| `--resume` | false | Continue from `.camel-kit/pipeline.json` (mode=ship) |
| `--start-from <stage>` | none | Skip to stage: `brainstorm`, `plan`, `execute`, `validate` |

---

## Pipeline

```text
Stage 0: BRAINSTORM  →  Stage 1: PLAN  →  Stage 2: EXECUTE  →  Stage 3: VALIDATE  →  STAMP
```

### Before Starting

1. Check for `--resume` flag. If set:
   a. Read `.camel-kit/pipeline.json`, verify `mode = "ship"`
   b. **Run staleness detection** (see Staleness Detection on Resume below)
   c. If stale artifacts found → re-run from the earliest stale stage (overrides `currentStage`)
   d. If no staleness → jump to `currentStage`
   e. Skip steps 2-6
2. Generate pipeline ID: run `{COMMAND_PREFIX} nextId <slug>` (derive slug from input file name or user request).
3. Check for `--start-from` flag. If set, verify prerequisite artifacts exist in `docs/camel-kit/<activePipeline>/`:
   - `plan` requires `design-spec.md`
   - `execute` requires `design-spec.md` AND `implementation-plan.md`
   - `validate` requires generated route files to exist
4. If neither `--resume` nor `--start-from` is set, start from Stage 0.
5. Parse `--ask` level (default: `smart`).
6. Initialize state file: write `.camel-kit/pipeline.json` with `mode: "ship"` and initial state.

### Stage Execution

For each stage:

1. Load the oversight matrix: read `guides/oversight-matrix.md`
2. Update state: set `currentStage` in `.camel-kit/pipeline.json`
3. Invoke the corresponding skill:
   - Stage 0: invoke `/camel-brainstorm` with `[input-file]` as context
   - Stage 1: invoke `/camel-plan` (reads design spec from Stage 0)
   - Stage 2: invoke `/camel-execute` (reads plan from Stage 1; includes runtime verification via `camel-verify` subagent)
   - Stage 3: invoke `/camel-validate` (static quality analysis of generated routes)
4. After the skill completes, apply oversight decision from the matrix
5. If oversight says "pause": present results and wait for user input
6. If oversight says "auto-proceed": save state and continue to next stage
7. If a failure occurs during any stage:
   - If matrix action is `AUTO-FIX`: load `guides/auto-fix-loop.md` and attempt repair
   - Otherwise: PAUSE and wait for user decision

### Stamp Gate (After Stage 3) — Parallel Fan-Out

After validation completes, run the final quality gate using parallel reviewer subagents. This keeps review traces out of the main ship context — only structured reports flow back.

**Step 1: Build verification** (inline — not subagent)
- Run `{COMMAND_PREFIX} verify` (or `mvn verify` directly)
- If build fails → PAUSE regardless of --ask level

**Step 2: Parallel reviewer fan-out** (3 subagents dispatched simultaneously)

Dispatch these three reviewer subagents in parallel:

| Reviewer | Persona | Focus |
|----------|---------|-------|
| Spec consistency | `agents/spec-compliance-reviewer.md` | Cross-route spec consistency — naming patterns, property conventions, shared endpoints |
| Code quality | `agents/code-quality-reviewer.md` | Constitution compliance across ALL routes, anti-patterns, YAML quality |
| Security scan | `agents/code-quality-reviewer.md` (security-only mode) | CVE checks via `camel_docs_cve_search`, credential scan, TLS verification |

Each reviewer receives ALL generated route files and returns a structured report. The orchestrator does NOT read the full review traces — only the reports.

**Why parallel is safe here:** Iron Law 4 (spec before quality) applies to per-task reviews in `camel-execute`. By this point, every task has already passed both spec and quality reviews individually. The Stamp Gate checks are cross-cutting and independent — they CAN run in parallel.

**Step 3: Merge reports**
- Combine the three reviewer reports into a single Stamp Gate report saved to `docs/camel-kit/<activePipeline>/stamp-report.md`
- Categorize issues: Critical / Important / Suggestion
- Cross-reference acceptance criteria from the design spec

**Step 4: Decision**

If ALL checks pass:
- Report: "Pipeline complete. All checks passed."

If ANY check fails:
- Report failures clearly with the merged report
- If `--ask never`: attempt auto-fix (load `guides/auto-fix-loop.md`)
- Otherwise: present failures and ask user for next steps

---

## Staleness Detection on Resume

When `--resume` is used, camel-ship checks all pipeline artifacts for staleness markers before continuing.

### Detection Algorithm

1. Read all artifacts in `docs/camel-kit/<activePipeline>/`:
   - `design-spec.md` (Stage 0 output)
   - `implementation-plan.md` (Stage 1 output)
   - `execution-report.md` (Stage 2 output)
   - `validation-report.md` (Stage 3 output)
   - `stamp-report.md` (Stamp gate output)
2. For each artifact, check the first 10 lines for a blockquote containing `⚠️ **STALE**` (see `shared/pipeline-infrastructure.md` for the full marker format)
3. Find the **earliest stale stage** — the lowest stage number whose artifact is marked stale

### Re-run Decision

| Scenario | Action |
|---|---|
| No stale artifacts | Resume from `currentStage` (normal behavior) |
| Stale artifacts found | Report stale stages to user, then re-run from the earliest stale stage |
| `design-spec.md` itself is stale | Edge case (e.g., manual marker addition). Warn that the design spec is the root artifact and should not be marked stale, then re-run from Stage 0. |

### Re-run Behavior

When re-running stale stages:

1. Update `currentStage` in `.camel-kit/pipeline.json` to the earliest stale stage
2. Report to the user:
   ```text
   ⚠️ Staleness detected in pipeline <PIPELINE_ID>:
     - implementation-plan.md: STALE (design-spec.md modified on YYYY-MM-DD)
     - execution-report.md: STALE
     - validation-report.md: STALE
   
   Re-running from Stage 1 (plan) to regenerate stale artifacts.
   ```
3. Execute stages sequentially from the earliest stale stage
4. Each regenerated artifact is written fresh (no staleness marker)
5. Downstream artifacts are regenerated in order — their staleness markers are naturally cleared

---

## Guide Manifest

| Guide | When to Load | Purpose |
|---|---|---|
| `guides/oversight-matrix.md` | At each stage transition | Determines pause/proceed behavior |
| `guides/state-management.md` | At pipeline start and after each stage | State persistence format |
| `guides/auto-fix-loop.md` | When any stage fails or review finds issues | Fix-retry logic |

---

## Shared Guides

Load these shared guides at pipeline start:
- `shared/iron-laws.md` — all 4 laws apply across all stages
- `shared/mcp-setup.md` — MCP tool configuration for verification
