# Adversarial Code Review

Adversarial review for implementation output. A Moderator dynamically selects and runs specialized Critic Lanes in parallel fresh contexts when supported, or sequentially inline otherwise.

**Load this guide during Step 2b.5 of `camel-execute`.** Replaces the former doubt-driven review.

---

## Purpose

The default review posture ("does this match the spec?") catches compliance errors but misses overconfident assumptions. Adversarial Code Review (ACR) uses **identity-separated Critic Lanes** — each with a specialized adversarial constitution — to catch what a single generic reviewer would miss. It runs post-implementation, before the two-stage review, as a pre-filter.

**Key principle from ACR:** The implementation must be challenged through independent critic lenses. Each Critic uses only the relevant design spec section and artifact. Prefer fresh contexts; when the target cannot create them, run the lenses sequentially and record that limitation.

---

## Constraints

| Constraint | Value |
|---|---|
| Maximum ACR cycles | 3 per task |
| Critic context | Fresh when supported; same-session fallback recorded otherwise |
| Critic execution | Parallel subagents when supported; sequential inline fallback |
| Moderator execution | Subagent when supported; inline fallback |
| Modifiable artifacts | None — critics report findings, implementer fixes |
| Escalation target | User (after 3 cycles without convergence) |

---

## ACR Workflow

After the implementer reports DONE (or DONE_WITH_CONCERNS), run this workflow.

### Step 1: Run Moderator

Run the `acr-moderator` role (from `agents/acr-moderator.md`) in a fresh subagent when supported, or inline otherwise, with:

1. The implementer's generated files (read contents, not just paths)
2. The design spec section for this task (the contract)
3. Source contracts if available for migration pipelines (WSDL, OpenAPI, XSD)
4. The implementer's status and any concerns

The Moderator performs lane selection, runs critics in parallel where supported or sequentially otherwise, and returns a unified synthesis report with a verdict.

**Model selection:** Standard model for Moderator (triage + synthesis). Most capable model for each Critic (deep adversarial reasoning).

### Step 2: Handle Verdict

| Verdict | Action |
|---|---|
| **PASS** | Accept — proceed to spec compliance review (Step 2c) |
| **FAIL** | Correct — send actionable findings to implementer, re-dispatch ACR after fixes |
| **PASS_WITH_TRADEOFFS** | Document trade-offs, proceed to spec compliance review (Step 2c) |

### Step 3: Pass Trade-offs Forward

Trade-offs documented by the ACR Moderator carry forward into the spec compliance review as context. The spec reviewer should be aware of documented trade-offs but evaluates independently.

---

## Bounded Loop Discipline

### Hard cap: 3 cycles

If the ACR cycle has run 3 times for the same task without all actionable findings being resolved, **escalate to the user**:

```
ACR ESCALATION — Task: <task-name>

3 ACR cycles completed without convergence. Unresolved actionable findings:

1. [finding description] — [file:location] — [critic lane]
2. [finding description] — [file:location] — [critic lane]

Trade-offs awaiting decision:
1. [trade-off description]

Please advise: fix these findings, accept as trade-offs, or dismiss.
```

### Convergence detection

Track findings across cycles by identity, not just count. Convergence means at least one previously-reported finding was resolved between cycles. New findings may appear (fixes can reveal deeper issues) — that is progress, not stagnation.

Non-convergence is when ZERO previously-reported findings were resolved between consecutive cycles. If this happens, escalate immediately rather than burning the remaining cycles.

### ACR theater detection

If the Moderator reports findings but ALL of them are classified as Noise across ALL lanes, the ACR process is validating rather than doubting. This is detectable in a single cycle and is a red flag:

```
ACR THEATER WARNING — Task: <task-name>

Critics reported [N] finding(s) across [M] lanes, but all were classified as
non-actionable. The adversarial process may be confirming rather than challenging.

Proceeding to spec review, but flagging for awareness.
```

Log the warning and proceed to spec compliance review. Do not loop further — re-running with the same dynamics will produce the same result.

---

## Integration with Two-Stage Review

ACR is a **pre-filter** that sits between implementation and the two-stage review (Iron Law 4). It does NOT replace spec compliance or code quality review.

```
Implementer DONE → ACR (Moderator + Critic Lanes) → Spec Review → Quality Review
```

Findings from the ACR that are classified as trade-offs carry forward into the spec compliance review as context — the spec reviewer should be aware of documented trade-offs but evaluates independently.

---

## Attribution

Adapts the [Adversarial Code Review pattern](https://asdlc.io/patterns/adversarial-code-review/) from the Agentic Software Development Lifecycle (ASDLC) framework.
