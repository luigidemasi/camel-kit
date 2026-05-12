# Spec Compliance Reviewer — Orchestrator Guide

> **Context:** Used by `camel-execute` to dispatch and handle spec-compliance-reviewer subagents.
> **Persona:** `agents/spec-compliance-reviewer.md` — defines the reviewer's checks and output format.

---

## Dispatch Protocol

1. Load the persona from `agents/spec-compliance-reviewer.md`
2. Build the subagent prompt with:
   - The persona text (full — do not summarize)
   - The generated files (or paths to them)
   - The design spec section this task implements
   - The task's review specification
3. Dispatch as a subagent — the reviewer runs in its own context and returns only the structured review report

## Failure Handling

If the spec reviewer reports FAIL:

1. Extract the specific failures from the review
2. Send failures to the implementer subagent with instructions to fix
3. After fixes, re-dispatch the spec reviewer
4. Loop until PASS
5. Only then proceed to code quality review

**Maximum iterations:** 3. If spec review fails 3 times, escalate to the user with the specific unresolved issues.
