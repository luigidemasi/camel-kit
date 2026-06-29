---
name: camel-test
description: Internal reference skill — loaded by camel-execute during test generation tasks. Contains guides for route analysis, test generation, test configuration, and test execution with Citrus + Testcontainers. NOT user-invocable.
user_invocable: false
---

# Camel Test Reference

> This skill is NOT user-invocable. It is loaded by `camel-execute` when dispatching test generation subagents.

## Purpose

Provides the domain knowledge guides needed to generate integration tests for Apache Camel routes using Citrus framework and Testcontainers. These guides are referenced by the `test-engineer` agent persona.

Use the configured Citrus MCP server as the primary source for Citrus test actions, endpoints, schemas, documentation,
and best practices only when the Citrus MCP server artifact version matches the project `citrus.version`. If Citrus MCP
is unavailable or its artifact version differs, fall back only to the cached quick reference for the same `citrus.version`
configured in `.camel-kit/config.properties`.

## Guide Manifest

| Guide | When to Load | Purpose |
|-------|-------------|---------|
| `guides/route-analysis.md` | Always | Analyze routes to identify testable behaviors |
| `guides/test-generation.md` | Always | Test generation patterns and templates |
| `guides/test-configuration.md` | Always | Test infrastructure setup (Testcontainers, mock endpoints) |
| `guides/test-runner.md` | Always | Test execution and verification |
| `guides/graph-project-context.md` | When `.camel-kit/project-graph.json` exists | Cross-route test awareness |

## Iron Laws

All guides in this skill enforce:
- **Iron Law 1**: MCP Catalog Verification — understand component behavior via MCP for accurate assertions
