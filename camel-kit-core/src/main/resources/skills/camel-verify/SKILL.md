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

- **Internal only** — dispatched as a subagent by `camel-execute` after all implementation tasks complete
- This skill is NOT user-invocable. It runs as part of the execute phase, not as a standalone pipeline stage.

## Prerequisites

- `.camel-kit/config.properties` must exist (for runtime detection)
- Project source files (routes, pom.xml, properties) must be in place

## Guides

| Guide | When Loaded | Purpose |
|-------|-------------|---------|
| `guides/verify-loop.md` | Always | Core verification loop — 3 phases, iteration, fix routing, report |
| `guides/error-taxonomy.md` | Always (reference) | Error classification tables — patterns, categories, fix actions |

## Verify Iteration Log

After each verification iteration (whether PASS or FAIL), append an entry to `.camel-kit/verify-log.md` following the format in `shared/pipeline-infrastructure.md`:

1. Read the active pipeline ID from `.camel-kit/pipeline.json` (if it exists)
2. Append a new `## Iteration N` section with:
   - Pipeline ID, trigger context, and result (PASS/FAIL)
   - Findings with severity (see table below)
   - Actions taken (fixes applied during this iteration)
3. Create the file if it doesn't exist

The verify-log is an operational audit trail — it persists across multiple verify cycles and sessions.

### Severity Classification

| Severity | When to Use | Examples |
|----------|-------------|---------|
| `[CRITICAL]` | Build fails, route doesn't start, data loss risk | Compilation error, endpoint not found, missing required dependency |
| `[WARNING]` | Route starts but behavior is incorrect or degraded | Wrong data format, missing error handler, performance regression |
| `[INFO]` | Observation with no immediate impact | All routes started, test passed after retry, non-blocking suggestion |

## After Verification

When all verification phases pass, return a structured verification report to the orchestrator (`camel-execute`). The orchestrator includes this report in the Step 4 completion summary.

The pipeline proceeds to `/camel-validate` (Tier 1) as the next stage — this is handled by the orchestrating skill (`camel-ship` or the user), not by this skill.
