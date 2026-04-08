---
name: camel-validate
description: Internal reference skill — loaded by camel-execute during validation tasks. Contains guides for schema validation, endpoint verification, quality checks, security analysis, and anti-pattern detection. NOT user-invocable.
user_invocable: false
---

# Camel Validate Reference

> This skill is NOT user-invocable. It is loaded by `camel-execute` when dispatching quality review subagents.

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

## Iron Laws

All guides in this skill enforce:
- **Iron Law 1**: MCP Catalog Verification — endpoint validation uses MCP catalog as source of truth
- **Iron Law 3**: Constitution Compliance — validation checks all 7 constitution rules
