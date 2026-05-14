# Doubt-Driven Review

Adversarial in-flight review for non-trivial implementation decisions. Applies the CLAIM → EXTRACT → DOUBT cycle with a fresh-context subagent that receives only the artifact and contract — no accumulated session context.

**Load this guide during Step 2b.5 of `camel-execute`.** The triviality gate determines whether to run the full doubt cycle or skip directly to spec review.

---

## Purpose

The default review posture ("does this match the spec?") catches compliance errors but misses overconfident assumptions. Doubt-driven review assumes the implementer is overconfident and actively looks for what's wrong, what's missing, and what will fail at runtime. It runs post-implementation, before the two-stage review, as a cheap adversarial pre-filter.

---

## Constraints

| Constraint | Value |
|---|---|
| Maximum doubt cycles | 3 per task |
| Reviewer context | Fresh — no accumulated session context |
| Reviewer dispatch | Subagent (not inline in orchestrator) |
| Modifiable artifacts | None — doubt reviewers report findings, implementer fixes |
| Escalation target | User (after 3 cycles without convergence) |

---

## Decision Triviality Gate

Before running the doubt cycle, classify the task's implementation decisions. If ALL decisions are trivial, skip the doubt cycle and proceed directly to spec compliance review (Step 2c).

### Non-trivial decisions (MUST trigger doubt cycle)

A decision is non-trivial if it:

| Criterion | Example |
|---|---|
| **Introduces branching logic** | `choice`, `filter`, `circuitBreaker`, content-based routing conditions, `recipientList`, `routingSlip`, `dynamicRouter` (runtime-resolved destinations are branching — the target depends on message content or state) |
| **Crosses a system boundary** | External API call, database query, message broker producer/consumer, `enrich`/`pollEnrich` (content enrichment calls an external system mid-route) |
| **Asserts something the type system can't verify** | DataMapper field mappings, expression language predicates, header-based routing, aggregation correlation keys and completion predicates |
| **Depends on invisible context** | Message ordering assumptions, timing-dependent logic, shared state between routes, `aggregate` completion conditions (timeout, size, predicate), idempotent consumer key selection |
| **Is irreversible** | Data transformation that discards fields, deletion operations, one-way format conversions, `saga` compensation logic (the compensating action is itself a non-trivial design decision) |

### Trivial decisions (skip doubt cycle)

| Decision | Why trivial |
|---|---|
| Adding a timer with a fixed period | No branching, no boundary crossing, fully type-safe |
| Setting a property placeholder name | Configuration, not logic |
| Generating Docker Compose for a known service | Mechanical, no design decisions |
| Creating a run script | Boilerplate, no integration logic |
| Writing application.properties with placeholder values | No logic, no assertions |

### Edge cases

If uncertain whether a decision is trivial: **run the doubt cycle**. The cost of a false positive (unnecessary doubt cycle on a trivial task) is one subagent dispatch. The cost of a false negative (skipping doubt on a non-trivial decision) is a latent defect that survives both review stages.

---

## CLAIM → EXTRACT → DOUBT Cycle

After the implementer reports DONE (or DONE_WITH_CONCERNS) and the triviality gate passes, run this cycle.

### Step 1: CLAIM

The implementer claims the task is complete. Record the claim and all generated artifacts.

### Step 2: EXTRACT

Extract testable claims from the implementer's output:

- **Component claims** — which components are used, with which options
- **Structure claims** — number of routes, flow direction, sub-route topology
- **Transformation claims** — field mappings, expression predicates, routing conditions
- **Boundary claims** — external systems contacted, protocols used, authentication configured
- **File claims** — paths of generated files, expected content patterns

**Extraction discipline:** Extract a claim for EVERY decision that matched a non-trivial criterion from the triviality gate — not just the ones that feel uncertain. Overconfident assumptions (decisions the orchestrator considers "obvious") are the primary target of doubt-driven review. If a non-trivial decision has no corresponding extracted claim, the doubt reviewer cannot challenge it.

### Step 3: DOUBT

Dispatch a **fresh-context subagent** with ONLY:

1. The extracted claims (from Step 2)
2. The relevant TDD section (the contract)
3. The generated files (the artifact)
4. The adversarial doubt prompt (below)

The subagent receives NO accumulated session context — no prior conversation, no orchestrator reasoning, no implementer explanations. This isolation prevents confirmation bias.

**Adversarial doubt prompt for the subagent:**

```
You are reviewing an implementation claim. Assume the author is overconfident.
Your job is NOT to confirm the claim — it is to find what's wrong, what's missing,
and what will fail at runtime.

For each extracted claim, answer:
1. Is this claim actually supported by the generated code?
2. What would cause this to fail in production?
3. What's missing that the claim doesn't mention?

Before flagging a MISSING feature (no circuit breaker, no async processing, no saga,
no idempotent consumer), check whether the TDD's conditional sections deliberately
omit it. The TDD includes conditional sections only when the design requires them —
absence of a section (e.g., no "Resilience / Circuit Breaker" section) is a deliberate
design decision, not an oversight. Do not flag missing features that the TDD
intentionally excludes.

Similarly, if the implementation uses a component or pattern that seems suboptimal
in isolation (e.g., `direct:` instead of `seda:`), check the TDD's Rationale and
Constraints fields before flagging. The choice may be constrained by cross-flow
dependencies or transaction boundaries documented in the design spec.

Classify each finding using the taxonomy below.
```

### Step 4: CLASSIFY

Each finding from the doubt reviewer is classified:

| Classification | Meaning | Action |
|---|---|---|
| **Contract misread** | The reviewer misunderstood the TDD contract | Dismiss — attach the specific TDD section that contradicts the finding |
| **Actionable** | Real defect the implementer must fix before proceeding | Fix required — return to implementer with finding details |
| **Trade-off** | Valid concern but resolution depends on business context | Document — present to user for decision |
| **Noise** | Stylistic preference or hypothetical concern with no concrete failure mode | Dismiss — attach the specific TDD section or code evidence that proves no concrete failure mode exists |

The orchestrator (not the doubt reviewer) performs classification. The reviewer's job is to find problems; the orchestrator's job is to triage them.

**Evidence-based dismissal:** Every dismissal (contract misread or noise) requires concrete evidence — a TDD section, a code reference, or a verifiable fact. "Stylistic concern" or "hypothetical" without a citation is not a valid dismissal. If the orchestrator cannot produce evidence to dismiss a finding, it stays actionable by default. The burden of proof is on the dismisser, not the finder.

### Step 5: DECIDE

| Outcome | Action |
|---|---|
| Zero actionable findings | Accept — proceed to spec compliance review (Step 2c) |
| One or more actionable findings | Correct — send findings to implementer, re-dispatch after fixes |
| Only trade-off findings | Document trade-offs, proceed to spec compliance review |
| Only contract-misread or noise findings | Accept — proceed to spec compliance review |

---

## Bounded Loop Discipline

### Hard cap: 3 cycles

If the doubt cycle has run 3 times for the same task without all actionable findings being resolved, **escalate to the user**:

```
DOUBT CYCLE ESCALATION — Task: <task-name>

3 doubt cycles completed without convergence. Unresolved actionable findings:

1. [finding description] — [file:location]
2. [finding description] — [file:location]

Trade-offs awaiting decision:
1. [trade-off description]

Please advise: fix these findings, accept as trade-offs, or dismiss.
```

### Convergence detection

Track findings across cycles by identity, not just count. Convergence means at least one previously-reported finding was resolved between cycles. New findings may appear (fixes can reveal deeper issues) — that is progress, not stagnation:

| Cycle | Prior findings resolved | New findings discovered | Total | Converging? |
|---|---|---|---|---|
| 1 | — | 4 | 4 | — |
| 2 | 3 resolved | 1 new | 2 | Yes — prior findings are being fixed |
| 3 | 1 resolved | 1 new | 2 | Yes — prior finding resolved, new one is legitimate |

Non-convergence is when ZERO previously-reported findings were resolved:

| Cycle | Prior findings resolved | New findings discovered | Total | Converging? |
|---|---|---|---|---|
| 1 | — | 3 | 3 | — |
| 2 | 0 resolved | 0 new | 3 | No — same 3 findings, nothing fixed. Escalate |

If no prior findings were resolved between consecutive cycles, the implementer is stuck. Escalate immediately rather than burning the remaining cycles.

### Doubt theater detection

If the doubt reviewer reports findings but the orchestrator classifies **all** of them as non-actionable (contract misread or noise), the doubt process is validating rather than doubting. This is detectable in a single cycle and is a red flag:

```
DOUBT THEATER WARNING — Task: <task-name>

Doubt reviewer reported [N] finding(s), but all were classified as
non-actionable (contract misread: [count], noise: [count]).
The doubt process may be confirming rather than challenging.

Proceeding to spec review, but flagging for awareness.
```

Log the warning and proceed to spec compliance review. Do not loop further — re-running with the same classification bias will produce the same result.

---

## Integration with Two-Stage Review

The doubt cycle is a **pre-filter** that sits between implementation and the two-stage review (Iron Law 4). It does NOT replace spec compliance or code quality review.

```
Implementer DONE → Triviality Gate → [non-trivial] → Doubt Cycle → Spec Review → Quality Review
                                    → [trivial]     → Spec Review → Quality Review
```

Findings from the doubt cycle that are classified as trade-offs carry forward into the spec compliance review as context — the spec reviewer should be aware of documented trade-offs but evaluates independently.
