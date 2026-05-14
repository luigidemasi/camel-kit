---
name: camel-execute
description: Execute an approved implementation plan with two-stage review.
user_invocable: false
---

# Camel Execute — Phase 3 Orchestrator

Execute the approved implementation plan by dispatching fresh subagents per task, with two-stage review after each.

**Announce at start:** "I'm using the camel-execute skill to implement the plan."

**Core principle:** Fresh subagent per task + two-stage review (spec then quality) = high quality, fast iteration.

## When NOT to use this skill

- No approved implementation plan exists — use `/camel-plan` first to create one
- Questions about Apache Camel — use `/camel-knowledge` instead
- Validation-only tasks (checking existing routes without changing them) — use `/camel-validate` instead
- You need a design spec — use `/camel-brainstorm` first; this skill implements plans, not ideas

<HARD-RULE>
AUTONOMOUS EXECUTION: Execute ALL tasks from the plan using wave analysis. Run `{COMMAND_PREFIX} plan analyze` first to get parallel waves. Tasks within a wave can run in parallel if the agent supports it. Tasks across waves run sequentially (wave N completes before wave N+1 starts). If wave analysis is unavailable, fall back to sequential execution. Do NOT stop, pause, or ask the user between tasks or waves. The user approved the entire plan — that is your authorization.
</HARD-RULE>

**Violating the letter of these rules is violating the spirit of these rules.**

---

## Invocation Modes

This skill supports two invocation modes (see `shared/pipeline-infrastructure.md` for details):

### Chained Mode (default)

Auto-invoked by `camel-plan` after planning completes. The plan content is available in conversation context. After execution, auto-invokes `camel-validate`.

### Standalone Mode

Invoked directly (e.g., `/camel-execute` or `/camel-execute <PIPELINE_ID>`) in a new session. No conversation context — reads the implementation plan from `docs/camel-kit/<PIPELINE_ID>/implementation-plan.md`.

**Detection at start:**

1. If auto-invoked by plan in this conversation → **chained mode**
2. If invoked independently and `implementation-plan.md` exists in the pipeline directory → **standalone mode**
3. If `implementation-plan.md` does not exist → error: "No implementation plan found. Run /camel-plan first."

**Standalone behavior:**

- Read `implementation-plan.md` from disk as the input
- Also read `design-spec.md` (needed for spec compliance reviews)
- Run `{COMMAND_PREFIX} doc check <file>` on both files to detect staleness — if stale, warn but proceed
- Execute the full task loop (Step 0 through Step 4)
- Write `execution-report.md` to the pipeline directory
- Do NOT auto-invoke `camel-validate` (standalone mode suppresses auto-transitions)

---

## Process Flow

```dot
digraph execute {
    rankdir=TB;
    
    start [label="Read approved plan\nExtract all tasks", shape=box];
    dispatch [label="Dispatch implementer\nsubagent for task N", shape=box];
    impl_status [label="Implementer\nstatus?", shape=diamond];
    answer_q [label="Answer questions\nprovide context", shape=box];
    
    spec_review [label="Dispatch spec-compliance\nreviewer", shape=box];
    spec_pass [label="Spec review\npasses?", shape=diamond];
    spec_fix [label="Implementer fixes\nspec gaps", shape=box];
    
    quality_review [label="Dispatch code-quality\nreviewer", shape=box];
    quality_pass [label="Quality review\npasses?", shape=diamond];
    quality_fix [label="Implementer fixes\nquality issues", shape=box];
    
    mark_done [label="Mark task complete", shape=box];
    more [label="More tasks?", shape=diamond];
    final [label="Final cross-cutting\nreview", shape=box];
    verify [label="Internal verification\n(camel-verify subagent)", shape=box];
    done [label="Completion summary", shape=doublecircle];
    next [label="Pipeline continues to\n/camel-validate (Stage 3)", shape=note, style=dashed];
    
    start -> dispatch;
    dispatch -> impl_status;
    impl_status -> answer_q [label="NEEDS_CONTEXT"];
    answer_q -> dispatch [label="re-dispatch"];
    impl_status -> spec_review [label="DONE"];
    impl_status -> spec_review [label="DONE_WITH_CONCERNS\n(note concerns)"];
    impl_status -> dispatch [label="BLOCKED\n(change approach)"];
    
    spec_review -> spec_pass;
    spec_pass -> spec_fix [label="FAIL"];
    spec_fix -> spec_review [label="re-review"];
    spec_pass -> quality_review [label="PASS"];
    
    quality_review -> quality_pass;
    quality_pass -> quality_fix [label="Critical issues"];
    quality_fix -> quality_review [label="re-review"];
    quality_pass -> mark_done [label="PASS\n(or only non-critical)"];
    
    mark_done -> more;
    more -> dispatch [label="yes"];
    more -> final [label="no"];
    final -> verify;
    verify -> done;
    done -> next [style=dashed];
}
```

---

## Step 0: Analyze Plan for Parallel Execution

Before executing tasks, analyze the plan for parallel execution waves:

```bash
{COMMAND_PREFIX} plan analyze docs/camel-kit/<activePipeline>/implementation-plan.md
```

This outputs JSON with parallel execution waves — groups of tasks that can run simultaneously because they touch different files:

```json
{
  "waves": [
    {"wave": 1, "tasks": [1, 2, 4]},
    {"wave": 2, "tasks": [3, 5]},
    {"wave": 3, "tasks": [6]}
  ],
  "dependencies": {"3": [2], "5": [4], "6": [3, 5]}
}
```

**Execution strategy:**
- Execute all tasks within a wave **in parallel** (dispatch separate subagents simultaneously)
- Wait for ALL tasks in a wave to complete and pass review before starting the next wave
- Tasks in the same wave are guaranteed to touch different files — no conflicts
- Each task still gets two-stage review (spec compliance, then code quality)
- If the `plan analyze` command fails or is unavailable, fall back to sequential execution (execute tasks in plan order)

**For agents that cannot parallelize** (single-conversation agents):
- Execute tasks within each wave sequentially, but respect the wave ordering
- This ensures correct dependency order even without parallelism

---

### Step 0.5: Environment Probe

Before dispatching any implementers, validate the target environment.

1. Load `guides/environment-probe.md`
2. Execute the probe (skeleton pom.xml, docker-compose, empty route)
3. If probe passes → proceed to task dispatch
4. If probe finds mechanical failures → auto-fix and re-probe
5. If probe finds architectural failures → load `guides/re-plan-loop.md`
   - Re-plan modifies affected TDD(s), max 3 rounds
   - After successful re-plan → re-probe, then proceed
6. If probe still fails after re-plan → escalate to user

The probe prevents wasting implementation cycles on environments that cannot support the planned architecture.

---

## Iron Laws (enforced in this phase)

Read `shared/iron-laws.md` for the full Iron Laws. This phase enforces ALL four:

- **Iron Law 1: MCP Catalog Verification** — implementer subagents MUST verify every component via MCP before generating YAML.
- **Iron Law 2: Constitution Compliance** — quality reviewer checks all 7 constitution rules.
- **Iron Law 3: No Code Without Design Approval** — this phase runs after the design spec is approved and planning is complete.
- **Iron Law 4: Spec Compliance Before Quality** — ALWAYS spec review FIRST, then quality review. Never in parallel. Never reversed.

### Rationalization Table

| Excuse | Reality |
|--------|---------|
| "I can run both reviews in parallel to save time" | Iron Law 4: spec first, quality second. Sequential. Always. |
| "The implementation clearly matches the spec" | Clearly ≠ verified. Run the spec review. |
| "I'll fix issues after all tasks are done" | Fix per task. Don't accumulate tech debt across tasks. |
| "The implementer's self-review is sufficient" | Self-review catches obvious issues. Reviewer catches what self-review misses. Both needed. |
| "This task is too simple for two-stage review" | Simple tasks get simple reviews. But they still get reviews. |
| "I'll dispatch multiple implementers in parallel without wave analysis" | Only parallelize within waves from `plan analyze`. Tasks in different waves may conflict. |
| "The subagent can read the plan file itself" | Provide full task text. Don't make subagents read plan files. |
| "I should ask before proceeding to the next task" | The user approved the ENTIRE plan. Execute ALL tasks without asking. |
| "Let me check if the user wants to continue" | They already said yes — to the whole plan. Keep going. |
| "The input plan is stale but I'll ignore the warning" | Always warn about staleness. Execution will produce fresh output, but the user should know the plan may be outdated. |
| "Let me summarize what was completed so far" | No mid-plan summaries. Print ONE LINE per task. Summary only at the END (Step 4). |
| "The scaffolding phase is complete, ready for implementation" | Scaffolding is ONE task. The plan has N tasks. Execute all N. Don't stop at 1. |
| "Next Steps: Ready to proceed with Task N" | There are no "Next Steps" — you ARE executing the next step RIGHT NOW. |

### Red Flags — STOP If You Think:

- "Let me skip the spec review for this simple task..."
- "I can run spec and quality reviews at the same time..."
- "The implementer said it's done, I trust them..."
- "I'll batch the reviews at the end..."
- "I don't need to dispatch a subagent for this..."
- "Would you like me to continue with Task N?"
- "Shall I proceed with the next task?"
- "Next Steps: Ready to proceed with..."
- "The [phase/command] has successfully completed..."
- "The project is now ready for implementation of the remaining tasks"
- "Ready to continue..." (any sentence starting with "Ready to continue")

---

## Execution Process

### Step 1: Read Plan and Extract Tasks

Resolve the active pipeline using `shared/pipeline-infrastructure.md`:
1. Read `.camel-kit/pipeline.json` -> get `activePipeline`
2. Read the plan from `docs/camel-kit/<activePipeline>/implementation-plan.md`
3. The execution report will be saved to `docs/camel-kit/<activePipeline>/execution-report.md`

Extract ALL tasks with:
- Full task text (don't summarize)
- Agent persona to dispatch
- Files to create/modify
- Guides to load
- MCP tools to call
- Design spec section reference
- Review specification

### Step 1.5: Pre-Implementation Catalog Research

Before dispatching implementers for a wave, batch-verify all MCP catalog artifacts referenced in the wave's tasks. This keeps MCP response traces (~500 tokens each) out of the orchestrator and implementer contexts.

1. Scan each task's TDD section for components, EIPs, dataformats, and languages
2. Deduplicate across tasks in the wave
3. Dispatch a `catalog-researcher` subagent (from `agents/catalog-researcher.md`) with:
   - The deduplicated artifact list
   - Runtime and platformBom parameters
4. Receive the structured verification summary
5. If any artifact is NOT FOUND: flag it in the task context before dispatch — the implementer must find an alternative

**The verification summary replaces per-implementer MCP lookups.** The implementer receives pre-verified catalog data and MUST use it as the source of truth for this wave. Iron Law 1 remains enforced via delegated MCP verification by `catalog-researcher`.

Pass the verification summary to each implementer subagent as part of the context (see `guides/implementer-context.md`).

---

### Step 2: Per-Task Loop

For each wave (sequential across waves, parallel within a wave for agents that support it). For single-conversation agents, execute tasks within each wave sequentially in plan order:

#### 2a: Dispatch Implementer Subagent

Build the implementer prompt using `guides/implementer-context.md`.

Dispatch a fresh subagent with:
- Agent persona (from `agents/[persona].md`)
- Full task text from the plan
- Relevant design spec section (read and include — don't make the subagent find it)
- Guide file paths to load
- MCP tool parameters (runtime, platformBom)
- Project context (Camel version, runtime, module path)

**Model selection:**
- Mechanical tasks (single-route YAML, properties, Docker Compose): standard model
- Complex tasks (DataMapper XSLT, multi-route coordination, migration): most capable model
- Review tasks: standard model (spec reviewer), most capable (quality reviewer)

#### 2b: Handle Implementer Status

| Status | Action |
|--------|--------|
| **DONE** | Proceed to spec compliance review |
| **DONE_WITH_CONCERNS** | Read concerns. If correctness/scope issues: address before review. If observations: note and proceed. |
| **NEEDS_CONTEXT** | Provide missing context, re-dispatch same subagent |
| **BLOCKED** | Assess blocker: context problem → provide more context. Task too large → break it up. Plan wrong → note for user. |

#### 2b.5: In-Flight Doubt Cycle (Doubt-Driven Review)

After the implementer reports DONE (or DONE_WITH_CONCERNS), run the doubt-driven review cycle before dispatching the full spec review. This adversarial pre-filter assumes the implementer is overconfident and actively looks for what's wrong, what's missing, and what will fail.

**Load `guides/doubt-driven-review.md` for the full protocol.** Summary below.

**Step 1 — Triviality Gate:** Classify the task's implementation decisions using the triviality gate in `guides/doubt-driven-review.md`. If ALL decisions are trivial (properties, Docker Compose, run scripts, timer with fixed period), skip directly to spec compliance review (Step 2c). If ANY decision is non-trivial (introduces branching logic, crosses a system boundary, asserts something the type system can't verify, depends on invisible context, or is irreversible), proceed to Step 2.

**Step 2 — CLAIM → EXTRACT → DOUBT:** Dispatch a fresh-context subagent that receives ONLY the extracted claims, the TDD section, and the generated files — no accumulated session context. The subagent reviews adversarially and reports findings.

**Step 3 — Classify findings:** The orchestrator classifies each finding as actionable, trade-off, contract misread, or noise. Only actionable findings require implementer fixes.

**Step 4 — Decide:**
- Zero actionable findings → proceed to spec compliance review (Step 2c)
- Actionable findings → return to implementer for fixes, then re-run doubt cycle
- Trade-off findings → document for user decision, proceed to spec compliance review

**Hard cap:** 3 doubt cycles per task. If actionable findings persist after 3 rounds, or the actionable count is not strictly decreasing between consecutive cycles, escalate to the user.

**Doubt theater detection:** If the doubt reviewer reports findings but the orchestrator classifies all of them as non-actionable (contract misread or noise), log a warning and proceed — the process is validating rather than doubting.

---

#### 2c: Spec Compliance Review (Stage 1)

Dispatch spec-compliance-reviewer subagent using `guides/spec-reviewer-criteria.md`.

Provide:
- The generated files (or paths to them)
- The design spec section this task implements
- The task's review specification

If review FAILS: return feedback to implementer, re-dispatch implementer to fix, re-review. Loop until pass.

**DO NOT proceed to quality review until spec review passes.**

#### 2d: Code Quality Review (Stage 2)

Dispatch code-quality-reviewer subagent using `guides/quality-reviewer-criteria.md`.

Provide:
- The generated files (or paths to them)
- Constitution rules to check
- Security and anti-pattern checks

If review finds **Critical** issues: return to implementer, fix, re-review.
If review finds only **Important/Suggestion** issues: note them, proceed.

#### 2e: Mark Task Complete and Continue

Record completion with a ONE-LINE status: `✅ Task N complete. Starting Task N+1...`

Then IMMEDIATELY start Step 2a for the next task. No summary, no "Next Steps", no pause.

<HARD-RULE>
The per-task loop is AUTOMATIC and UNINTERRUPTED.

After completing a task (implement → spec review → quality review):
1. Print ONE LINE: `✅ Task N complete. Starting Task N+1...`
2. IMMEDIATELY begin dispatching the next task's implementer subagent

Do NOT:
- Ask "Would you like me to continue?" or "Shall I proceed with Task N?"
- Print "Next Steps" or "Ready to proceed" blocks
- Print a completion summary (that's Step 4, ONLY after ALL tasks)
- Pause for confirmation between tasks
- Say "The camel-execute/camel-migrate command has completed" (it hasn't — there are more tasks)

The user approved the entire plan — that approval covers ALL tasks. Execute them ALL without interruption, following wave policy (parallel within a wave where supported, sequential across waves). The ONLY time you stop is after the LAST task, when you print the Step 4 completion summary.
</HARD-RULE>

### Step 3: Final Cross-Cutting Review (Subagent-Isolated)

After all tasks complete, dispatch the cross-cutting review as a subagent to keep review traces out of the orchestrator context:

1. Dispatch `code-quality-reviewer` subagent (from `agents/code-quality-reviewer.md`) with:
   - ALL generated route file paths
   - Constitution rules (`docs/constitution.md`)
   - Instruction to check cross-route consistency (naming conventions, property patterns, error handling consistency)
2. The subagent checks constitution compliance across all routes (not just individually) and returns the structured review report
3. Run smoke test if plan includes one (this stays in the orchestrator context — it's a build command, not a review)
4. **Generate cross-cutting review report** — include the cross-route consistency findings in the Step 4 completion summary. The full validation report is generated by `/camel-validate` (Stage 3), not by this step.

Only the structured report flows back to the orchestrator. The full review trace (file reads, MCP calls, reasoning) stays in the subagent's context.

### Step 3.5: Verification Phase (Subagent-Isolated)

After the cross-cutting review, dispatch the full verification loop as a subagent to keep build output and fix traces out of the orchestrator context.

1. Dispatch a verification subagent with:
   - The `camel-verify` skill (`skills/camel-verify/SKILL.md`)
   - Both guides: `verify-loop.md` and `error-taxonomy.md`
   - Project configuration (runtime, Camel version, module path)
2. The subagent executes the full verification loop (3 phases: build, Citrus tests, report)
3. Only the structured verification report flows back to the orchestrator

**Key rules:**
- Verification runs **once** after all tasks complete — not per-task. Camel loads all routes at startup, so per-task verification would fail on routes that depend on other not-yet-implemented routes.
- Verification failure does **NOT** block finishing. The user might want to merge/PR even with verification issues (e.g., external services unavailable in dev environment). The report is informational.
- The verification report is included in Step 4's completion summary.

**Re-plan trigger:** If verification failures persist after fix attempts within the verify loop, the verify loop may trigger `guides/re-plan-loop.md` to modify affected TDDs and re-execute. See `camel-verify/guides/verify-loop.md` Phase 2 for trigger conditions.

### Step 4: Completion Summary

```
===============================================================
IMPLEMENTATION COMPLETE
===============================================================

Pipeline: <PIPELINE_ID>
Plan: docs/camel-kit/<PIPELINE_ID>/implementation-plan.md
Design Spec: docs/camel-kit/<PIPELINE_ID>/design-spec.md

Tasks Completed: [N/N]

Generated Files:
  [list all generated files with paths]

Review Results:
  Spec Compliance: [N/N] tasks passed
  Code Quality: [N/N] tasks passed ([M] non-critical issues noted)

Cross-Cutting Review: PASS/FAIL
Smoke Test: PASS/FAIL/NOT_RUN

Verification: PASS/PARTIAL/FAIL/NOT_RUN
  [Include the full verification report from Step 3.5]

===============================================================
```

Save the completion summary as `docs/camel-kit/<PIPELINE_ID>/execution-report.md`.

**Add frontmatter metadata** — run `{COMMAND_PREFIX} doc init --by camel-execute --from implementation-plan.md <execution-report.md>` to add provenance metadata. This is idempotent — if frontmatter already exists, it is preserved.

**Mark downstream artifacts stale** — per `shared/pipeline-infrastructure.md`, run `{COMMAND_PREFIX} doc stale --reason "execution report regenerated" --cascade <first-downstream-artifact>` (e.g., `validation-report.md`) to propagate staleness to all downstream artifacts. The `--cascade` flag walks the `generated.from` chain automatically — do NOT loop over individual artifacts. Do NOT mark the freshly regenerated `execution-report.md` itself stale.

**Post-completion transition (invocation mode dependent):**

- **Chained mode:** auto-invoke `camel-validate` (the pipeline continues to Stage 3)
- **Standalone mode:** print confirmation and STOP. Do NOT auto-invoke validate.

---

## Never

- Start implementation without an approved plan
- Skip reviews (spec compliance OR code quality)
- Run reviews in parallel or reversed order
- Dispatch implementers simultaneously outside of wave analysis — only parallelize within waves from `plan analyze`, and only for agents that support concurrent conversations
- Make subagents read the plan file (provide full text)
- Ignore subagent questions
- Accept "close enough" on spec compliance
- Skip re-review after fixes
- Move to next task with open issues
- Stop or pause between tasks to ask the user
- Print "Next Steps" or completion summaries between tasks (only after the LAST task)
- Say "command has completed" or "phases are complete" while tasks remain
- Skip the catalog research step (Step 1.5) — MCP verification is delegated, not eliminated
- Let implementers re-verify components already verified by the catalog-researcher — trust the pre-verified summary
- Skip the doubt cycle for non-trivial tasks — run the triviality gate from `guides/doubt-driven-review.md` and only skip for genuinely trivial decisions
- Run doubt cycle more than 3 times — escalate to user if actionable findings persist or are not converging
- Share accumulated session context with the doubt reviewer — it must run in fresh context to prevent confirmation bias
- Classify doubt findings yourself without checking them against the TDD — classification requires verifying claims against the contract
