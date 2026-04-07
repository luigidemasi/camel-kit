---
name: camel-test
description: Internal reference skill — loaded by camel-execute during test generation tasks. Contains guides for route analysis, test generation, test configuration, and test execution with Citrus + Testcontainers. NOT user-invocable.
user_invocable: false
---

# Camel Test Reference

> This skill is NOT user-invocable. It is loaded by `camel-execute` when dispatching test generation subagents.

## Purpose

Provides the domain knowledge guides needed to generate integration tests for Apache Camel routes using Citrus framework and Testcontainers. These guides are referenced by the `test-engineer` agent persona.

## Guide Manifest

| Guide | When to Load | Purpose |
|-------|-------------|---------|
| `guides/route-analysis.md` | Always | Analyze routes to identify testable behaviors |
| `guides/test-generation.md` | Always | Test generation patterns and templates |
| `guides/test-configuration.md` | Always | Test infrastructure setup (Testcontainers, mock endpoints) |
| `guides/test-runner.md` | Always | Test execution and verification |

## Iron Laws

All guides in this skill enforce:
- **Iron Law 1**: MCP Catalog Verification — understand component behavior via MCP for accurate assertions
