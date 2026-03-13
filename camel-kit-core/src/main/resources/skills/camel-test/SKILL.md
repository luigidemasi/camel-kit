---
name: camel-test
description: Create integration tests when user wants to test routes, generate test cases, set up Citrus tests, configure Testcontainers, verify behavior, or write test suites
user-invocable: true
metadata:
  version: "1.0.0"
  author: "camel-kit"
  category: "integration"
  license: "Apache-2.0"
---

# Camel Test - Integration Test Generation

You are acting as a **Test Engineer** creating integration tests for Camel routes using the Citrus framework. Generate realistic tests that validate end-to-end flow behavior with Testcontainers for external dependencies.

## Parameters

This skill can test a specific flow or all flows:

```
/camel-test <flow-name>   # Generate tests for specific flow
/camel-test all           # Generate tests for all flows
/camel-test --all         # Same as above
```

Example: `/camel-test order-to-warehouse`

### Batch Mode (`all`)

When `all` or `--all` is specified:

1. **Discover flows:** List all directories under `docs/flows/` that contain both a `{flow-name}.tdd.md` and corresponding route YAML
2. **Show plan and proceed immediately** (the user already confirmed by passing `--all`):
   ```
   Found [N] flows to generate tests for:
     1. flow-name-1  (docs/flows/flow-name-1/flow-name-1.tdd.md)
     2. flow-name-2  (docs/flows/flow-name-2/flow-name-2.tdd.md)
     ...

   Generating tests for all [N] flows sequentially...
   ```
3. **Process sequentially:** For each flow, run the full pipeline (Steps 1–2 below). Between flows, report progress:
   ```
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
   ✅ [current]/[total] — {flow-name} tests generated
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
   ```
   **If a flow fails:**
   ```
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
   ❌ [current]/[total] — {flow-name} FAILED
      Error: [one-line summary]
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
   ```
   **Continue to the next flow.** Do NOT stop the batch on failure.

4. **Final summary:** After all flows, show combined summary:
   ```
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
   BATCH TEST GENERATION COMPLETE
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

   Results: [passed]/[total] flows
     ✅ flow-name-1
     ❌ flow-name-2 — [error summary]
     ✅ flow-name-3
     ...

   [If any failed:]
   Failed flows need manual investigation:
     - flow-name-2: [error details]

   Next steps:
     Run all tests: ./run-tests.sh
   ```

If no TDD files are found: ERROR "No TDD files found in docs/flows/. Run /camel-flow first."

---

## Context Loading

**ALWAYS read at the start:**
1. `docs/business-requirements.md` - Business context (if exists)
2. `docs/flows/{flow-name}/{flow-name}.tdd.md` - Technical specification (REQUIRED)
3. `docs/constitution.md` - Best practices (REQUIRED)
4. `.camel-kit/config.yaml` - Configuration (if exists)
5. `{flow-name}.camel.yaml` - Implementation to test (REQUIRED)

**Citrus Schema Reference (MANDATORY when available):**
- **Read first:** `.camel-kit/.cache/citrus/{version}/citrus-quick-reference.md`
- Contains all valid actions, endpoints, and testcontainer configurations
- If found, never generate Citrus YAML without consulting this reference
- If missing, proceed with WARNING (see error conditions below)

## Error Conditions

### Missing TDD

```
❌ ERROR: Technical Design Document not found

File: docs/flows/{flow-name}/{flow-name}.tdd.md

Tests require the TDD to understand:
- Expected behavior
- Test scenarios
- Data contracts

Run: /camel-flow {flow-name}
```

### Missing Implementation

```
❌ ERROR: Camel route not found

File: {flow-name}.camel.yaml

Tests require the implementation to exist first.

Run: /camel-implement {flow-name}
```

### Missing Citrus Reference

```
⚠️ WARNING: Citrus quick reference not found

File: .camel-kit/.cache/citrus/{version}/citrus-quick-reference.md

Proceeding with standard Citrus patterns.
Generated tests may require manual validation.
```

### Docker Not Running

```
❌ ERROR: Docker not running

Testcontainers requires Docker to be running.

Start Docker and try again:
  - Docker Desktop (Mac/Windows)
  - sudo systemctl start docker (Linux)
```

---

## Step 1: Detect Runtime and Resolve Paths

Read `project.runtime` from `.camel-kit/config.yaml` (default: `jbang`).
Read `Target Module` from the TDD "Overview" section.

If the TDD contains a `Target Module` field (e.g., `my-module/` or `services/order-service/`), use it as the base directory. If not set or is `.` (dot), use empty string (single-project layout).

### Context Variable Resolution Table

| Variable | JBang (default) | Spring Boot / Quarkus |
|----------|----------------|----------------------|
| `TEST_DIR` | `{module}/` | `{module}/src/test/resources/` |
| `RUNNER_DIR` | `{module}/` | `{module}/` |

Where `{module}` is the `Target Module` from the TDD "Overview" section (empty for single-project layouts).

### Additional Context Variables

- **FLOW_NAME**: `{flow-name}` from parameters
- **CAMEL_VERSION**: from `.camel-kit/config.yaml` (or default)
- **RUNTIME**: from `.camel-kit/config.yaml` `project.runtime` (default: `jbang`)
- **TARGET_MODULE**: from the TDD "Overview" section (`Target Module` field, empty for single-project)

**Placeholder convention:** In guide templates, `{flow-name}` (lowercase, kebab-case) and `{FLOW_NAME}` (uppercase) both refer to this variable's value. Guides use `{flow-name}` in user-facing text and file content, `{FLOW_NAME}` in context variable references. Replace both with the actual flow name value.

---

## Step 2: Execute Test Generation Pipeline

Execute these guides in order, passing all resolved context variables.

> **"Load" means READ and FOLLOW.** When this document says "Load `guides/xyz.md`", you MUST read that file from the `guides/` subdirectory next to this SKILL.md and execute its instructions. The guide files are always present — do NOT report them as missing.

1. **→ Load `guides/route-analysis.md`** — MCP route analysis + Citrus reference loading
2. **→ Load `guides/test-generation.md`** — Test plan design + Citrus YAML generation + validation
3. **→ Load `guides/test-configuration.md`** — Test properties + dependencies
4. **→ Load `guides/test-runner.md`** — Test runner script generation

---

## Test Generation Summary

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
TESTS GENERATED: {FLOW_NAME}
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Test File: {TEST_DIR}{flow-name}.camel.it.yaml

Test Scenarios:
  ✓ Happy path
  ✓ Invalid input → DLQ
  ✓ Business rule filters
  ✓ Error handling → DLQ
  ✓ Target unavailable → retry and DLQ

Testcontainers:
  [list containers from route analysis]

Supporting Files:
  ✓ {TEST_DIR}application-test.properties
  ✓ {RUNNER_DIR}run-tests.sh

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

---

## Next Steps

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
NEXT STEPS
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. Run the tests:
   ./{RUNNER_DIR}run-tests.sh

2. Iterate on failures:
   - Review test logs
   - Update tests or implementation as needed
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```
