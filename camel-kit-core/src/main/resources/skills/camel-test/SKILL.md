---
name: camel-test
description: Create integration tests when user wants to test routes, generate test cases, verify route behavior, or write tests for a Camel integration. Use this when the user says things like "test my route", "write tests for order-to-warehouse", "generate tests", or "how do I test this flow", even if they don't mention Citrus or Testcontainers specifically.
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

```
/camel-test <flow-name>   # Generate tests for specific flow
/camel-test all           # Generate tests for all flows
/camel-test --all         # Same as above
```

### Batch Mode (`all`)

When `all` or `--all` is specified:

1. **Discover flows:** List all directories under `docs/flows/` that contain both a `{flow-name}.tdd.md` and corresponding route YAML
2. **Show plan and proceed immediately** (the user already confirmed by passing `--all`):
   ```
   Found [N] flows to generate tests for:
     1. flow-name-1  (docs/flows/flow-name-1/flow-name-1.tdd.md)
     ...

   Generating tests for all [N] flows sequentially...
   ```
3. **Process sequentially:** For each flow, run the full pipeline (Guide Manifest below). Between flows, report progress:
   ```
   ✅ [current]/[total] — {flow-name} tests generated
   ```
   **If a flow fails:** report `❌ [current]/[total] — {flow-name} FAILED` and **continue to the next flow.**
4. **Final summary:** Show combined results with pass/fail per flow.

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
- If found, never generate Citrus YAML without consulting this reference
- If missing, proceed with WARNING (see error conditions below)

## Pre-flight Checks

Before generating tests, verify a container runtime is available by executing `docker info` or `podman info`. If neither is available, fail early with:
```
❌ ERROR: Docker not running

Testcontainers requires Docker to be running.

Start Docker and try again:
  - Docker Desktop (Mac/Windows)
  - sudo systemctl start docker (Linux)
```

---

## Error Conditions

### Missing TDD
```
❌ ERROR: Technical Design Document not found
File: docs/flows/{flow-name}/{flow-name}.tdd.md
Run: /camel-flow {flow-name}
```

### Missing Implementation
```
❌ ERROR: Camel route not found
File: {flow-name}.camel.yaml
Run: /camel-implement {flow-name}
```

### Missing Citrus Reference
```
⚠️ WARNING: Citrus quick reference not found
File: .camel-kit/.cache/citrus/{version}/citrus-quick-reference.md
Proceeding with standard Citrus patterns. Generated tests may require manual validation.
```

---

## Step 1: Detect Runtime and Resolve Paths

Read `project.runtime` from `.camel-kit/config.yaml` (default: `jbang`).
Read `Target Module` from the TDD "Overview" section.

If the TDD contains a `Target Module` field (e.g., `my-module/`), use it as the base directory. If not set or is `.`, use empty string (single-project layout).

### Context Variable Resolution Table

| Variable | JBang (default) | Spring Boot / Quarkus |
|----------|----------------|----------------------|
| `TEST_DIR` | `{module}/` | `{module}/src/test/resources/` |
| `RUNNER_DIR` | `{module}/` | `{module}/` |

### Additional Context Variables

- **FLOW_NAME**: `{flow-name}` from parameters
- **CAMEL_VERSION**: from `.camel-kit/config.yaml` (or default)
- **RUNTIME**: from `.camel-kit/config.yaml` `project.runtime` (default: `jbang`)
- **PLATFORM_BOM**: resolved from `CAMEL_VERSION` + `RUNTIME` via `skills/shared/mcp-setup.md`
- **TARGET_MODULE**: from the TDD "Overview" section (`Target Module` field, empty for single-project)

**Placeholder convention:** `{flow-name}` (kebab-case) and `{FLOW_NAME}` (uppercase) both refer to this variable. Replace both with the actual flow name.

---

## Guide Manifest

Dispatch sub-agents for each step sequentially. Each sub-agent loads one guide plus the resolved context variables.

| Step | Guide | Shared Guide | ~Tokens | When |
|------|-------|-------------|---------|------|
| A | guides/route-analysis.md | — | 1.1K | Always |
| B | guides/test-generation.md | — | 3.5K | Always |
| C | guides/test-configuration.md | — | 0.5K | Always |
| D | guides/test-runner.md | — | 0.3K | Always |

### Context Passing

Include in each sub-agent prompt:
- All resolved context variables (FLOW_NAME, CAMEL_VERSION, RUNTIME, PLATFORM_BOM, TARGET_MODULE, TEST_DIR, RUNNER_DIR)
- Path to the TDD file and route YAML file
- Citrus reference path (if available)

### Execution

1. Dispatch Step A — MCP route analysis + Citrus reference loading
2. Dispatch Step B — Test plan design + Citrus YAML generation + validation
3. Dispatch Step C — Test properties + dependencies
4. Dispatch Step D — Test runner script generation

---

## Test Generation Summary

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
TESTS GENERATED: {FLOW_NAME}
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Test File: {TEST_DIR}{flow-name}.camel.it.yaml

Testcontainers:
  [list containers from route analysis]

Supporting Files:
  ✓ {TEST_DIR}application-test.properties
  ✓ {RUNNER_DIR}run-tests.sh

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

## Next Steps

```
1. Run the tests:
   ./{RUNNER_DIR}run-tests.sh

2. Iterate on failures:
   - Review test logs
   - Update tests or implementation as needed
```
