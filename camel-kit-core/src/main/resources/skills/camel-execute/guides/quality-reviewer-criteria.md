# Code Quality Reviewer — Orchestrator Guide

> **Context:** Used by `camel-execute` to dispatch and handle code-quality-reviewer subagents.
> **Persona:** `agents/code-quality-reviewer.md` — defines the reviewer's checks, issue categories, and output format.

---

## Dispatch Protocol

1. Load the persona from `agents/code-quality-reviewer.md`
2. Build the subagent prompt with:
   - The persona text (full — do not summarize)
   - The generated files (or paths to them)
   - Constitution rules to check (read `docs/constitution.md`)
   - Security and anti-pattern checks
3. Dispatch as a subagent — the reviewer runs in its own context and returns only the structured review report

## Failure Handling

If the quality reviewer reports Critical issues:

1. Extract specific Critical issues from the review
2. Send to implementer subagent with fix instructions
3. After fixes, re-dispatch the quality reviewer
4. Loop until no Critical issues remain
5. Important and Suggestion issues are reported but don't block

**Maximum iterations:** 3. If Critical issues persist after 3 rounds, escalate to the user.

## MCP Tools for Quality Review

The quality reviewer should use:
- `camel_catalog_component_doc(name, runtime, platformBom)` — verify endpoint option names
- `camel_catalog_eip(name, runtime, platformBom)` — verify EIP configuration
