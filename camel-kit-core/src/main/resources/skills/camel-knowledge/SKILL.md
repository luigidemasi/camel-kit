---
name: camel-knowledge
description: Internal reference skill — loaded by pipeline skills when Red Hat documentation lookup is needed. Routes questions to appropriate MCP tools (component support, CVEs, errata, migration guides, release info). NOT user-invocable.
user_invocable: false
---

# Camel Knowledge — Red Hat Documentation Reference

> This skill is NOT user-invocable. It is loaded by pipeline skills (`camel-brainstorm`, `camel-execute`) when documentation lookup is needed.

## Purpose

Provides access to Red Hat Build of Apache Camel documentation via MCP knowledge tools. Used during:
- **Brainstorm:** checking component support status, finding migration guides
- **Execute:** verifying Red Hat support during quality review, checking for CVEs

## MCP Tools

| Tool | Purpose | When to Use |
|------|---------|-------------|
| `camel_rh_build_search` | General documentation search | Migration guides, getting started, general questions |
| `camel_rh_build_component_info` | Component support status lookup | Iron Law 2 — verify Red Hat support |
| `camel_rh_build_cve_search` | CVE lookup by ID | Security review, known vulnerability check |
| `camel_rh_build_bugfix_search` | Advisory search (security + bugfix) | Security review, version comparison |
| `camel_rh_build_release_info` | Release notes for a version | Version selection, understanding changes |
| `camel_rh_build_supported_configs` | Supported platforms, JDKs, databases | Environment compatibility check |

## Usage Pattern

### Component Support Check (Iron Law 2)

```
camel_rh_build_component_info(component="kafka", version="4.14")
```

Returns support level: Production Support, Technology Preview, Community Support, Dev Support.

### CVE Check (quality review)

```
camel_rh_build_cve_search(cve_id="CVE-2024-XXXXX")
```

### Migration Guide Search

```
camel_rh_build_search(query="migrating from Fuse 7 to RHBAC 4.14", version="4.14", max_results=5)
```

## Important Notes

- Strip `.redhat-XXXXX` suffix from versions before MCP calls (e.g., `4.14.4.redhat-00008` → `4.14`)
- Pass `max_results=5` unless more results are needed
- If a tool call fails, warn and continue — don't block the pipeline
- Never fabricate documentation content — if no results, say so
