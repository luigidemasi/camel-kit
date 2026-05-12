---
name: camel-validate
description: Static quality analysis of Camel routes — correctness, security, anti-patterns.
user_invocable: true
---

# Camel Validate — Pipeline Quality Gate

> **Tier 1 pipeline step.** Final stage after execute — produces a comprehensive quality report.

## Invocation

- **User:** `/camel-validate` — run standalone on any project with generated routes
- **Pipeline:** invoked as Stage 3 by `camel-ship` after execute completes (including verification)

When invoked standalone, validates routes in the current project. When invoked as a pipeline stage, reads the generated routes from the execute phase and produces the validation report.

## Purpose

Provides the domain knowledge guides needed to validate generated Apache Camel routes across multiple quality dimensions. These guides are referenced by the `quality-engineer` and `code-quality-reviewer` agent personas.

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

```
docs/validation-report-YYYY-MM-DD_HH-mm.md
```

Use the current date and time for the filename (e.g., `validation-report-2026-04-22_14-30.md`).

### Report Format

```markdown
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
ALWAYS generate the validation report. Every validation run MUST produce a timestamped report in `docs/`. This creates an audit trail of validation results over time.
</HARD-RULE>

## Iron Laws

All guides in this skill enforce:
- **Iron Law 1**: MCP Catalog Verification — endpoint validation uses MCP catalog as source of truth
- **Iron Law 3**: Constitution Compliance — validation checks all 7 constitution rules
