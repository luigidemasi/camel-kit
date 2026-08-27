---
name: camel-debug
description: Ad-hoc troubleshooting for broken Camel routes outside of a pipeline run.
user_invocable: false
---

# Camel Debug — Standalone Troubleshooting

> Diagnose and fix a broken Camel route using a structured STOP → PRESERVE → DIAGNOSE → FIX → GUARD workflow.

**Announce:** "Let me debug this using the camel-debug skill."

## When to Use

- A route was working but is now broken (startup failures, runtime exceptions, unexpected behavior)
- A user arrives with "my route is broken" outside of an active pipeline run
- Ad-hoc troubleshooting — no pipeline context required

## When NOT to Use

- Build/test failures during pipeline execution — `camel-verify` handles this (dispatched by `camel-execute`)
- Quality validation of generated routes — use `/camel-validate`
- Designing new integrations — use `/camel-brainstorm`
- Planning or executing implementation — use `/camel-plan` or `/camel-execute`

**Violating the letter of these rules is violating the spirit of these rules.**

## Prerequisites

- `.camel-kit/config.properties` must exist (for runtime and version detection)
- The project must have existing route files to debug

## Guides

| Guide | When to Load | Purpose |
|-------|-------------|---------|
| `guides/debug-workflow.md` | Always | Full debugging workflow — STOP, PRESERVE, DIAGNOSE, FIX, GUARD |
| `camel-verify/guides/error-taxonomy.md` | Always (reference) | Error classification tables — patterns, categories, fix actions |

## Shared Guides

Load these shared guides at workflow start:
- `shared/iron-laws.md` — Iron Law 1 (MCP verification) applies during diagnosis
- `shared/mcp-setup.md` — MCP tool configuration for catalog queries

## Diagnostic Role Isolation

Run diagnosis roles as isolated subagents when the target supports them. For
single-conversation targets, run the same three roles sequentially inline and
record that isolation is unavailable. Never skip a diagnostic role.

| Subagent | Purpose | Input | Output |
|----------|---------|-------|--------|
| Route analyzer | Inspect route YAML for structural issues | Route file paths, error message | Structural findings list |
| MCP verifier | Verify components/endpoints against catalog | Component names from routes | Verification results (exists/missing/wrong options) |
| Log analyzer | Parse and classify error output | Raw log/stack trace text | Classified error with taxonomy match |
