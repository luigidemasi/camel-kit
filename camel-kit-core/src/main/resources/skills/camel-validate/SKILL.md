---
name: camel-validate
description: Static quality analysis of Camel routes — correctness, security, anti-patterns.
user_invocable: false
---

# Camel Validate — Pipeline Quality Gate

> **Tier 1 pipeline step.** Final stage after execute — produces a comprehensive quality report.

## When NOT to use this skill

- Runtime debugging (build failures, startup errors, exceptions) — `camel-verify` handles that within `/camel-execute`
- Generating or modifying routes — use `/camel-execute` for implementation
- Design work or architecture decisions — use `/camel-brainstorm` instead
- Fixing the issues found — this skill reports problems, it does not fix them

**Strictness invariant:** for every rule class (property keys, bean definitions, placeholders, component usage), this skill must be AT LEAST as strict as the generation rules in `camel-implement`. If generation forbids emitting X, validation must detect X — hand-edited projects must not pass checks that generated projects are held to.

## Invocation Modes

This skill supports two invocation modes (see `shared/pipeline-infrastructure.md` for details):

### Chained Mode

Invoked as Stage 3 by `camel-ship` after execute completes, or auto-invoked by `camel-execute` at the end of its task loop. Pipeline context is available in the conversation.

### Standalone Mode

Invoked directly by the user: `/camel-validate` or `/camel-validate <PIPELINE_ID>`.

**Detection at start:**

1. If auto-invoked by execute or ship in this conversation → **chained mode** (pipeline)
2. If invoked with `<PIPELINE_ID>` and pipeline artifacts exist → **standalone mode** (pipeline-scoped)
3. If invoked without `<PIPELINE_ID>` and `.camel-kit/pipeline.json` exists → **standalone mode** (pipeline-scoped, use `activePipeline`)
4. If invoked without `<PIPELINE_ID>` and no `.camel-kit/pipeline.json` → **standalone mode** (project-scoped, validates routes in current project)

**Standalone behavior (pipeline-scoped):**

- Read prior artifacts from `docs/camel-kit/<PIPELINE_ID>/` for cross-reference
- Run `{COMMAND_PREFIX} doc check <file>` on input artifacts to detect staleness — if stale, warn but proceed
- Execute the full validation workflow
- Write `validation-report.md` to the pipeline directory
- STOP (no further stage transitions)

**Standalone behavior (project-scoped — no pipeline):**

- Validate routes found in the current project
- Write a timestamped report to `docs/validation-report-YYYY-MM-DD_HH-mm.md`

**Announce at start:** "I'm using the camel-validate skill to analyze route quality."

## Purpose

Provides the domain knowledge guides needed to validate generated Apache Camel routes across multiple quality dimensions. These guides are referenced by the `code-quality-reviewer` and `test-engineer` agent personas.

## Pipeline Resolution

Before running validation, resolve the active pipeline using `shared/pipeline-infrastructure.md`:
1. Read `.camel-kit/pipeline.json` -> get `activePipeline`
2. Load prior artifacts from `docs/camel-kit/<activePipeline>/` for cross-reference:
   - `design-spec.md` — for spec compliance checking
   - `implementation-plan.md` — for task coverage verification
   - `execution-report.md` — for verifying generated file list, review results, and verification status
3. Save the validation report to `docs/camel-kit/<activePipeline>/validation-report.md`

When invoked standalone without pipeline context (project-scoped), fall back to scanning routes in the current project and saving the report to `docs/validation-report-YYYY-MM-DD_HH-mm.md`.

## Guide Manifest

| Guide | When to Load | Purpose |
|-------|-------------|---------|
| `guides/schema-validation.md` | Always | YAML DSL schema validation rules |
| `guides/endpoint-validation.md` | Always | Endpoint URI validation via MCP catalog |
| `guides/quality-checks.md` | Always | Quality metrics and thresholds |
| `guides/security-analysis.md` | Always | Security checks catalog (credentials, TLS, headers) |
| `guides/anti-patterns.md` | Always | Anti-pattern detection catalog |
| `guides/graph-project-context.md` | When `.camel-kit/project-graph.json` exists | Project norms for validation thresholds |
| `guides/graph-dead-code-report.md` | When `.camel-kit/project-graph.json` exists | Dead code analysis and report |

## Validation Report

After completing all validation checks, generate a markdown report saved to:

- **Chained mode and standalone pipeline-scoped:** `docs/camel-kit/<PIPELINE_ID>/validation-report.md`
- **Standalone project-scoped (no pipeline):** `docs/validation-report-YYYY-MM-DD_HH-mm.md`

Use the current date and time for the timestamped filename (e.g., `validation-report-2026-04-22_14-30.md`).

### Report Format

```markdown
---
staleness:
  stale: false
  since: null
  reason: null
generated:
  at: "<current ISO-8601 timestamp>"
  by: camel-validate
  from: execution-report.md
---
# Validation Report

**Date:** YYYY-MM-DD HH:mm
**Project:** {project name from .camel-kit/config.properties}
**Runtime:** {project.runtime from config.properties, or "not yet selected"}
**Camel Version:** {project.camelVersion from config.properties, or "not yet selected"}

## Summary

| Category | Pass | Fail | Warn | Total |
|----------|------|------|------|-------|
| Schema Validation | N | N | N | N |
| Endpoint Verification | N | N | N | N |
| Quality Checks | N | N | N | N |
| Security Analysis | N | N | N | N |
| Anti-Patterns | N | N | N | N |
| Constitution Compliance | N | N | N | N |
| **Total** | **N** | **N** | **N** | **N** |

## Findings

### Schema Validation
- [PASS/FAIL/WARN] Finding description
- ...

### Endpoint Verification
- [PASS/FAIL/WARN] Finding description
- ...

### Quality Checks
- ...

### Security Analysis
- ...

### Anti-Patterns
- ...

### Constitution Compliance
- Rule 1 (Route Structure): PASS/FAIL
- Rule 2 (Single Responsibility): PASS/FAIL
- ...
- Rule 7 (Component Verification): PASS/FAIL

## Recommendations
- Priority fixes (FAIL items)
- Suggested improvements (WARN items)
```

<HARD-RULE>
ALWAYS generate the validation report.
- In chained mode and standalone pipeline-scoped mode, save to `docs/camel-kit/<PIPELINE_ID>/validation-report.md`.
- In standalone project-scoped mode (no pipeline), save to `docs/validation-report-YYYY-MM-DD_HH-mm.md`.
This creates an audit trail of validation results over time.
</HARD-RULE>

**Add frontmatter metadata** — run `{COMMAND_PREFIX} doc init --by camel-validate --from execution-report.md <validation-report.md>` to add provenance metadata. This is idempotent — if frontmatter already exists, it is preserved.

**Mark downstream artifacts stale** — per `shared/pipeline-infrastructure.md`, after saving the validation report, if `stamp-report.md` exists, run `{COMMAND_PREFIX} doc stale --reason "validation report regenerated" --cascade <stamp-report.md>`. Do NOT mark the freshly regenerated `validation-report.md` itself stale.

## Iron Laws

All guides in this skill enforce:
- **Iron Law 1**: MCP Catalog Verification — endpoint validation uses MCP catalog as source of truth
- **Iron Law 2**: Constitution Compliance — validation checks all 7 constitution rules
