# Spec Compliance Reviewer — Orchestrator Guide

> **Context:** Used by `camel-execute` to run the spec-compliance-reviewer role in an isolated subagent where supported or inline otherwise.
> **Persona:** `agents/spec-compliance-reviewer.md` — defines the reviewer's adversarial checks, finding classification, and output format.

---

## Dispatch Protocol

1. Load the persona from `agents/spec-compliance-reviewer.md`
2. Build the subagent prompt with:
   - The persona text (full — do not summarize)
   - The generated files (or paths to them)
   - The design spec section this task implements
   - The task's review specification
   - Any trade-offs documented by the ACR Moderator (from `guides/adversarial-code-review.md`), if ACR ran for this task
3. Use a fresh subagent when supported; otherwise review sequentially inline and record the missing isolation. Produce the same structured review report.

## Handling Review Results

The spec reviewer classifies each finding as **Actionable**, **Trade-off**, or **Noise**.

| Finding classification | Orchestrator action |
|---|---|
| **Actionable** | Extract finding details, send to implementer with fix instructions |
| **Trade-off** | Document for user decision — does NOT block proceeding to quality review |
| **Noise** | Dismiss — verify the reviewer's reasoning is sound, then discard |

### Failure loop

If the spec reviewer reports one or more **Actionable** findings:

1. Extract the specific Actionable findings from the review
2. Send findings to the implementer subagent with instructions to fix
3. After fixes, re-dispatch the spec reviewer
4. Loop until zero Actionable findings remain
5. Only then proceed to code quality review

**Maximum iterations:** 3. If Actionable findings persist after 3 rounds, escalate to the user with the unresolved findings and any documented trade-offs.

### Convergence check

Track Actionable findings by identity across iterations. Convergence means at least one previously-reported Actionable finding was resolved. New findings may appear as fixes reveal deeper issues — that is progress. If zero previously-reported findings were resolved between consecutive iterations, escalate immediately — the implementer and reviewer are stuck.
