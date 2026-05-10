---
name: camel-verify
description: Build, test, diagnose, and fix a Camel application iteratively.
user_invocable: false
---

# Camel Verify

Runtime verification feedback loop. Builds, tests, diagnoses, fixes, and retries in a structured 3-phase loop:

1. **Build Verification** — compile the project, classify and fix build errors
2. **Test Verification** — run Citrus integration tests via `camel test run`, classify and fix test failures
3. **Report** — structured summary of all phases, fixes applied, and issues found

Each phase retries up to 15 times with error classification and fix routing to existing skills (`camel-validate`, `camel-implement`, `camel-test`, or self-repair). Persistent architectural failures trigger automatic re-planning via `camel-execute/guides/re-plan-loop.md`. Graceful degradation when tools (Maven, Docker, `camel test` CLI) are unavailable.

## Invocation

- **User:** `/camel-verify`
- **Automatic:** loaded by `camel-execute` after all implementation tasks complete

When invoked standalone, runs the full verification loop on the project as-is. When loaded by `camel-execute`, runs as a final phase before the completion summary.

## Prerequisites

- `.camel-kit/config.properties` must exist (for runtime detection)
- Project source files (routes, pom.xml, properties) must be in place

## Guides

| Guide | When Loaded | Purpose |
|-------|-------------|---------|
| `guides/verify-loop.md` | Always | Core verification loop — 3 phases, iteration, fix routing, report |
| `guides/error-taxonomy.md` | Always (reference) | Error classification tables — patterns, categories, fix actions |

## After Verification

When all verification phases pass, inform the user:

> "Verification complete. The next recommended step is a quality review. Run `/camel-validate` to generate a comprehensive report (anti-patterns, security, schema compliance)."

Do NOT invoke `/camel-validate` automatically — the user must run it manually.
