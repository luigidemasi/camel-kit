# Code Quality Reviewer — Orchestrator Guide

> **Context:** Used by `camel-execute` to run the code-quality-reviewer role in an isolated subagent where supported or inline otherwise.
> **Persona:** `agents/code-quality-reviewer.md` — defines the reviewer's checks, issue categories, and output format.

---

## Dispatch Protocol

1. Load the persona from `agents/code-quality-reviewer.md`
2. Build the subagent prompt with:
   - The persona text (full — do not summarize)
   - The generated files (or paths to them)
   - Constitution rules to check (read `docs/constitution.md`)
   - Security and anti-pattern checks
3. Use a fresh subagent when supported; otherwise review sequentially inline and record the missing isolation. Produce the same structured review report.

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
- `camel_catalog_component_doc(component, runtime, platformBom)` — verify endpoint option names
- `camel_catalog_eip_doc(eip, runtime, platformBom)` — verify EIP configuration
