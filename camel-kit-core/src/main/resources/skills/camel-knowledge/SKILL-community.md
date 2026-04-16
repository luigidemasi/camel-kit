---
name: camel-knowledge
description: Internal reference skill — loaded by pipeline skills when Apache Camel documentation lookup is needed. Routes questions to appropriate MCP tools (component availability, CVEs, errata, migration guides, release info). NOT user-invocable.
user_invocable: false
---

# Camel Knowledge — Apache Camel Documentation Reference

> This skill is NOT user-invocable. It is loaded by pipeline skills (`camel-brainstorm`, `camel-execute`) when documentation lookup is needed.

## Purpose

Provides access to Apache Camel documentation via MCP knowledge tools. Used during:
- **Brainstorm:** checking component availability, finding migration guides
- **Execute:** verifying component availability during quality review, checking for CVEs

## MCP Tools

| Tool | Purpose | When to Use |
|------|---------|-------------|
| `camel_docs_search` | General documentation search | Migration guides, getting started, general questions |
| `camel_docs_component_info` | Component availability lookup | Verify component exists in catalog |
| `camel_docs_cve_search` | CVE lookup by ID | Security review, known vulnerability check |
| `camel_docs_advisory_search` | Advisory search (security + bugfix) | Security review, version comparison |
| `camel_docs_release_info` | Release notes for a version | Version selection, understanding changes |
| `camel_docs_supported_configs` | Supported platforms, JDKs, databases | Environment compatibility check |

## Usage Pattern

### Component Availability Check

```
camel_docs_component_info(component="kafka", version="4.14")
```

Returns availability status.

### CVE Check (quality review)

```
camel_docs_cve_search(cve_id="CVE-2024-XXXXX")
```

### Migration Guide Search

```
camel_docs_search(query="migrating from Camel 3 to Camel 4.14", version="4.14", max_results=5)
```

## Important Notes

- Pass `max_results=5` unless more results are needed
- If a tool call fails, warn and continue — don't block the pipeline
- Never fabricate documentation content — if no results, say so
