---
name: camel-knowledge
description: Look up Camel documentation, components, CVEs, errata, and versions.
user_invocable: false
---

# Camel Knowledge — Apache Camel Documentation Reference

> Invocable standalone via `/camel-knowledge` or loaded by pipeline skills for documentation lookup.

## When NOT to use this skill

- Implementation tasks (generating routes, writing code) — use `/camel-execute` instead
- Route validation or quality analysis — use `/camel-validate` instead
- Designing integrations — use `/camel-brainstorm` for design work; this skill answers factual questions

## Purpose

Provides access to Apache Camel documentation via MCP knowledge tools. Used during:
- **Brainstorm:** checking component availability, finding migration guides
- **Execute:** verifying component availability during quality review, checking for CVEs

## MCP Tools

The generated MCP allowlist is defined in `workflow/camel-kit-workflow.yaml` under the `camel-knowledge` MCP server. Keep this table aligned with that manifest.

| Tool | Purpose | When to Use |
|------|---------|-------------|
| `camel_docs_search` | General documentation search | Migration guides, getting started, EIP patterns, general questions |
| `camel_docs_component_info` | Component documentation and CVE lookup | Verify component exists, get configuration reference, check security |
| `camel_docs_cve_search` | CVE security advisory search | Security review, vulnerability check by CVE ID, component, or severity |
| `camel_docs_release_info` | Release notes for a version | Version selection, understanding what changed |
| `camel_docs_jira_lookup` | JIRA issue lookup by ID | Find which release fixed a specific issue |

## Usage Pattern

### Component Availability Check

```
camel_docs_component_info(component="kafka", version="4.14")
```

Returns availability status.

### CVE Check (quality review)

```
camel_docs_cve_search(cve_id="CVE-2024-22369")
camel_docs_cve_search(component="sql", severity="HIGH")
```

### Migration Guide Search

```
camel_docs_search(query="migrating from Camel 3 to Camel 4.14", version="4.14", max_results=5)
```

### JIRA Issue Lookup

```
camel_docs_jira_lookup(jira_id="CAMEL-22784")
```

## Subagent Dispatch Pattern

When invoked from within another skill (not standalone), the orchestrator should dispatch knowledge queries as a `knowledge-researcher` subagent (from `agents/knowledge-researcher.md`). This keeps full MCP search results out of the orchestrator context — only the synthesized answer flows back.

**Standalone invocation** (`/camel-knowledge`): runs inline in the current context.

**Pipeline invocation** (from `camel-brainstorm`, `camel-execute`, etc.): dispatch as subagent:
1. Build the subagent prompt with:
   - The `knowledge-researcher` persona (full text from `agents/knowledge-researcher.md`)
   - The specific question or lookup request
   - Relevant context (Camel version, component name, CVE ID, etc.)
2. The subagent runs the appropriate MCP tools
3. Only the structured answer (see `agents/knowledge-researcher.md` output format) flows back

This pattern prevents ~2000-5000 tokens of raw search results per query from accumulating in the orchestrator context. For a migration brainstorm with 5-10 knowledge lookups, this saves 10,000-50,000 tokens.

## Important Notes

- Pass `max_results=5` unless more results are needed
- If a tool call fails, warn and continue — don't block the pipeline
- Never fabricate documentation content — if no results, say so
