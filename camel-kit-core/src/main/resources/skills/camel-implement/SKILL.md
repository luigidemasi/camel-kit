---
name: camel-implement
description: Generate Camel YAML routes when user wants to implement flows, create route definitions, write integration code, convert TDD to YAML, or build Camel applications
user-invocable: true
metadata:
  version: "1.0.0"
  author: "camel-kit"
  category: "integration"
  license: "Apache-2.0"
---

# Camel Implement - Code Generation from TDD

You are acting as a **Developer/Implementer** generating production-ready Apache Camel integration code from technical specifications.

## Role and Approach

- Read and follow the Technical Design Document precisely
- Detect the target runtime from project configuration
- Dispatch to the runtime-specific orchestrator guide for code generation

## Parameters

This skill can implement a specific flow or all flows:

```
/camel-implement <flow-name>   # Implement specific flow
/camel-implement all           # Implement all flows with TDDs
/camel-implement --all         # Same as above
```

Example: `/camel-implement order-to-warehouse`

### Batch Mode (`all`)

When `all` or `--all` is specified:

1. **Discover flows:** List all directories under `docs/flows/` that contain a `{flow-name}.tdd.md` file
2. **Show plan:**
   ```
   Found [N] flows to implement:
     1. flow-name-1  (docs/flows/flow-name-1/flow-name-1.tdd.md)
     2. flow-name-2  (docs/flows/flow-name-2/flow-name-2.tdd.md)
     ...

   Proceed with implementing all [N] flows? (yes/no)
   ```
3. **Process sequentially:** For each flow, run the full pipeline (Steps 1–2 below). Between flows, report progress:
   ```
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
   ✅ [current]/[total] — {flow-name} implemented
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
   ```
4. **Final summary:** After all flows, show combined summary:
   ```
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
   BATCH IMPLEMENTATION COMPLETE
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

   Implemented [N] flows:
     ✅ flow-name-1
     ✅ flow-name-2
     ...

   Next steps:
     /camel-validate --all    # Validate all flows
     /camel-test --all        # Generate tests for all flows
   ```

If no TDD files are found: ERROR "No TDD files found in docs/flows/. Run /camel-flow first."

---

## Context Loading

**ALWAYS read at the start:**
1. `docs/business-requirements.md` - Business context (REQUIRED)
2. `docs/flows/{flow-name}/{flow-name}.tdd.md` - Technical Design Document (REQUIRED)
3. `docs/constitution.md` - Best practices and quality gates. If missing, copy from `templates/constitution.md` and continue.
4. `.camel-kit/config.yaml` - Camel version and configuration (if exists)
5. `.camel-kit/templates/yaml-generation-guide.md` - YAML DSL rules (if exists)

**Error conditions:**
- If BRD does not exist: ERROR "Business Requirements Document not found. Run /camel-project first."
- If TDD does not exist: see [Missing TDD](#missing-tdd) error below.

---

## Step 1: Pre-Implementation Checks

### 1.1 Verify TDD Completeness

Check that the TDD contains all required sections:

```
Checking TDD completeness...

✓ Source System defined
✓ Processing Steps defined
✓ Sink System defined
✓ Error Handling Strategy defined
✓ Configuration Properties defined
✓ Dependencies listed
```

If any sections are missing or incomplete:

```
⚠️ WARNING: TDD incomplete

Missing sections:
- [section name]

This may result in incomplete implementation.

Continue anyway? (yes/no)
```

### 1.2 Verify Schemas

Check that all JSON schemas referenced in the TDD exist:

```
Checking schemas...

✓ schemas/{flow-name}-input.json
✓ schemas/{flow-name}-output.json
```

If schemas are missing, offer to generate them:

```
❌ Missing schemas:
- schemas/{flow-name}-input.json

Would you like me to:
1. Generate schemas from TDD data contracts
2. Skip schemas (you'll create them later)
3. Cancel implementation
```

### 1.3 Constitution Gate Check

Verify the TDD passes all constitution gates:

```
Constitution Gate Check:

✓ Route Structure: Single responsibility
✓ External Configuration: No hardcoded connections
✓ Error Handling: Dead Letter Channel configured
✓ Security: No hardcoded credentials
✓ Naming Convention: Route ID follows pattern
```

If gates fail, warn before proceeding.

---

## Step 2: Detect Runtime and Dispatch

Read `project.runtime` from `.camel-kit/config.yaml`.
If not set, default to `jbang`.

**CRITICAL: Read `Target Module` from TDD Section 1 to determine the base directory.**

If the TDD contains a `Target Module` field (e.g., `my-module/` or `services/order-service/`), pass it to the orchestrator. If not set or is `.` (dot), use empty string (single-project layout).

Based on runtime, load the corresponding orchestrator guide:

| Runtime | Guide |
|---------|-------|
| `jbang` (default) | → Load `guides/orchestrator-jbang.md` |
| `springboot` or `spring-boot` | → Load `guides/orchestrator-springboot.md` |
| `quarkus` | → Load `guides/orchestrator-quarkus.md` |

Pass to the orchestrator:
- **FLOW_NAME**: `{flow-name}` from parameters
- **CAMEL_VERSION**: from `.camel-kit/config.yaml` (or default)
- **TARGET_MODULE**: from TDD Section 1 (`Target Module` field, empty for single-project)
- **TDD**: full content of the Technical Design Document
- **BRD**: summary of business requirements
- **SCHEMAS_MISSING**: true/false from Step 1.2
- **HAS_DATAMAPPER**: true/false (TDD contains `### DataMapper:` sections)
- **HAS_ADVANCED_PATTERNS**: true/false (TDD contains Section 6 or Section 7)

The orchestrator guide handles all remaining steps: component documentation, YAML generation, validation, properties, docker-compose, and runtime-specific artifacts.

---

## Error Handling

### Missing TDD

```
❌ ERROR: Technical Design Document not found

File: docs/flows/{flow-name}/{flow-name}.tdd.md

You need to create the TDD first:

  /camel-flow {flow-name}
```
