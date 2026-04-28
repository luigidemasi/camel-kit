---
name: camel-design
description: Internal reference skill — loaded by camel-brainstorm during flow design. Contains guides for component selection, EIP catalog, data formats, integration patterns, and TDD assembly. NOT user-invocable.
user_invocable: false
---

# Camel Design Reference

> This skill is NOT user-invocable. It is loaded by `camel-brainstorm` during the design phase.

## Purpose

Provides the domain knowledge guides needed to design Apache Camel integration flows. These guides are loaded selectively based on the flow requirements discovered during brainstorming.

## Guide Manifest

| Guide | When to Load | Purpose |
|-------|-------------|---------|
| `guides/component-selection.md` | Always during flow design | Component selection criteria and MCP verification process |
| `guides/catalog-lookups.md` | Always during flow design | How to query the MCP catalog effectively |
| `guides/eip-catalog.md` | When EIPs needed (routing, splitting, aggregation) | Enterprise Integration Pattern selection and configuration |
| `guides/data-formats.md` | When data transformation needed | Data format selection (JSON, XML, CSV, etc.) |
| `guides/integration-patterns.md` | Always during flow design | Common integration patterns and composition strategies |
| `guides/datamapper-interview.md` | When XSLT/DataMapper transformation needed | Socratic interview for DataMapper field mapping |
| `guides/resilience-interview.md` | When error handling/resilience needed | Interview for retry, circuit breaker, dead letter channel design |
| `guides/performance.md` | When performance requirements specified | Throttling, concurrency, batch processing design |
| `guides/security.md` | When security requirements specified | TLS, auth, credential management design |
| `guides/monitoring.md` | When monitoring/observability required | Metrics, health checks, tracing design |
| `guides/tdd-assembly.md` | Always (final step of design) | Assembles design decisions into TDD format |

## Iron Laws

All guides in this skill enforce:
- **Iron Law 1**: MCP Catalog Verification — every component/EIP/dataformat recommendation is MCP-verified
