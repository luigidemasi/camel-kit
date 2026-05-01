---
name: camel-ship
description: Use this skill when the user wants to run the entire integration workflow autonomously — from brainstorm through verification — in a single command. Trigger for phrases like 'ship it', 'build the whole thing', 'run the full pipeline', 'autonomous mode', 'end to end', or when the user provides requirements and wants everything generated without manual phase transitions. Also use when the user references '--ask', 'oversight level', 'resume pipeline', or 'auto-fix'.
user_invocable: true
---

# Camel Ship — Autonomous Pipeline

Chain the full camel-kit pipeline (brainstorm → plan → execute → verify) with configurable human oversight.

**Announce at start:** "I'm using the camel-ship skill to run the full pipeline."

**Violating the letter of these rules is violating the spirit of these rules.**

---

## Arguments

Parse the skill arguments for these flags:

| Argument | Default | Description |
|---|---|---|
| `[input-file]` | none | Requirements document, design spec, or brainstorm notes |
| `--ask` | `smart` | Oversight level: `always`, `smart`, or `never` |
| `--resume` | false | Continue from `.camel-kit/ship-state.json` |
| `--start-from <stage>` | none | Skip to stage: `brainstorm`, `plan`, `execute`, `verify` |

---

## Pipeline

```text
Stage 0: BRAINSTORM  →  Stage 1: PLAN  →  Stage 2: EXECUTE  →  Stage 3: VERIFY  →  STAMP
```

### Before Starting

1. Check for `--resume` flag. If set, read `.camel-kit/ship-state.json` and jump to `currentStage`.
2. Check for `--start-from` flag. If set, verify prerequisite artifacts exist:
   - `plan` requires `docs/design-spec.md`
   - `execute` requires `docs/design-spec.md` AND `docs/implementation-plan.md`
   - `verify` requires generated route files to exist
3. If neither flag is set, start from Stage 0.
4. Parse `--ask` level (default: `smart`).
5. Initialize state file: write `.camel-kit/ship-state.json` with initial state.

### Stage Execution

For each stage:

1. Load the oversight matrix: read `guides/oversight-matrix.md`
2. Update state: set `currentStage` in `.camel-kit/ship-state.json`
3. Invoke the corresponding skill:
   - Stage 0: invoke `/camel-brainstorm` with `[input-file]` as context
   - Stage 1: invoke `/camel-plan` (reads design spec from Stage 0)
   - Stage 2: invoke `/camel-execute` (reads plan from Stage 1)
   - Stage 3: invoke `/camel-verify` (verifies generated routes)
4. After the skill completes, apply oversight decision from the matrix
5. If oversight says "pause": present results and wait for user input
6. If oversight says "auto-proceed": save state and continue to next stage
7. If a failure occurs during any stage:
   - If matrix action is `AUTO-FIX`: load `guides/auto-fix-loop.md` and attempt repair
   - Otherwise: PAUSE and wait for user decision

### Stamp Gate (After Stage 3)

After verification completes, run the final quality gate:

1. Verify build passes: run `{COMMAND_PREFIX} verify` (or `mvn verify` directly)
2. Check Iron Law compliance: scan generated YAML for Iron Law violations
3. Constitution compliance: compare generated routes against `docs/constitution.md`
4. Acceptance criteria: cross-reference design spec acceptance criteria with generated output

If ALL checks pass:
- Report: "Pipeline complete. All checks passed."

If ANY check fails:
- Report failures clearly
- If `--ask never`: attempt auto-fix (load `guides/auto-fix-loop.md`)
- Otherwise: present failures and ask user for next steps

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
