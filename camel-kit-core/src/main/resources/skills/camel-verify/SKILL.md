---
name: camel-verify
description: Runtime verification feedback loop — builds, starts, diagnoses errors, applies fixes, and retries until the application runs correctly or the iteration limit is reached
user_invocable: true
---

# Camel Verify

Runtime verification feedback loop. Replaces the one-shot "try running" instruction with a structured 5-phase loop:

1. **Environment Preparation** — start external services via docker-compose
2. **Build Verification** — compile the project, classify and fix build errors
3. **Startup Verification** — start the application, classify and fix startup errors
4. **Behavioral Verification** — send test data, compare output, fix mismatches
5. **Report** — structured summary of all phases, fixes applied, and issues found

Each phase retries up to 15 times with error classification and fix routing to existing skills (`camel-validate`, `camel-implement`, or self-repair). Graceful degradation when tools (Maven, Docker, JBang) are unavailable.

## Invocation

- **User:** `/camel-verify`
- **Automatic:** loaded by `camel-execute` after all implementation tasks complete

When invoked standalone, runs the full verification loop on the project as-is. When loaded by `camel-execute`, runs as a final phase before the completion summary.

## Prerequisites

- `.camel-kit/config.yaml` must exist (for runtime detection)
- Project source files (routes, pom.xml, properties) must be in place

## Guides

| Guide | When Loaded | Purpose |
|-------|-------------|---------|
| `guides/verify-loop.md` | Always | Core verification loop — 5 phases, iteration, fix routing, report |
| `guides/error-taxonomy.md` | Always (reference) | Error classification tables — patterns, categories, fix actions |
