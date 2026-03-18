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

**CRITICAL — Migration TDDs are fully supported.** TDDs produced by `/camel-migrate` are identical in structure to those from `/camel-flow`. They may contain extra migration context (e.g., "Migrated From", "Source Module", Java source files to migrate, WSDL files to copy, CXF configuration). This does NOT make them incompatible — treat them like any other TDD:
- Generate Camel YAML routes, properties, docker-compose, and maven dependencies as normal
- If the TDD references Java source files (processors, beans), copy or create them in the target module
- If the TDD references non-route files (WSDL, XSD, config), copy them to the target module
- If the TDD specifies Maven plugins or build configuration, add them to pom.xml
- Do NOT refuse to implement, do NOT claim the TDD is "for a different context", do NOT suggest a "follow-up session"
- Do NOT hedge by discussing "scope", "limitations", or "what this would involve" — just implement

## Parameters

This skill can implement a specific flow or all flows:

```
/camel-implement <flow-name>   # Implement specific flow
/camel-implement all           # Implement all flows sequentially
/camel-implement --all         # Same as above
/camel-implement --parallel    # Implement all flows in parallel via subagents
```

Example: `/camel-implement order-to-warehouse`

### Batch Mode (`all`)

When `all` or `--all` is specified:

**HARD RULE — `--all` means START IMPLEMENTING NOW. No preamble. No analysis. No discussion.**

The following behaviors are **skill violations** and must NEVER occur:
- Discussing "scope", "limitations", "practical approach", or "implementation reality"
- Claiming the batch "exceeds conversation capacity" or requires too many operations
- Saying this is "designed for CI/CD pipelines" or any other invented constraint
- Estimating the number of tool operations, files, or steps needed
- Presenting a "recommended solution" or "alternative approach" instead of implementing
- Showing a "Current Status" summary of the TDDs instead of implementing them
- Any text before the flow discovery list that is not the flow discovery list itself

**This skill implements 1, 4, 10, or 100 flows the same way: one at a time, sequentially.** There is no capacity limit. Start with flow 1, finish it (generate all files), move to flow 2, and so on. Each flow is independent.

1. **Discover flows:** List all directories under `docs/flows/` that contain a `{flow-name}.tdd.md` file
2. **Show plan and proceed immediately** (the user already confirmed by passing `--all`):
   ```
   Found [N] flows to implement:
     1. flow-name-1  (docs/flows/flow-name-1/flow-name-1.tdd.md)
     2. flow-name-2  (docs/flows/flow-name-2/flow-name-2.tdd.md)
     ...

   Implementing all [N] flows sequentially...
   ```
3. **Process sequentially:** For each flow, run the full pipeline (Steps 1–2 below) which MUST produce actual files (route YAML, properties, docker-compose, etc.). Reading documents and running checks is NOT "implementing" — you must reach the orchestrator and generate code. Between flows, report progress:
   ```
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
   ✅ [current]/[total] — {flow-name} implemented
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
   ```
   **If a flow fails** (smoke test exhausted all 6 attempts, or unrecoverable error):
   ```
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
   ❌ [current]/[total] — {flow-name} FAILED
      Error: [one-line summary]
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
   ```
   **Continue to the next flow.** Do NOT stop the batch on failure.

4. **Final summary:** After all flows, show combined summary with pass/fail status:
   ```
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
   BATCH IMPLEMENTATION COMPLETE
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

   Results: [passed]/[total] flows implemented
     ✅ flow-name-1
     ❌ flow-name-2 — [error summary]
     ✅ flow-name-3
     ...

   [If any failed:]
   Failed flows need manual investigation:
     - flow-name-2: [error details]

   Next steps:
     /camel-validate --all    # Validate all flows
     /camel-test --all        # Generate tests for all flows
   ```

If no TDD files are found: ERROR "No TDD files found in docs/flows/. Run /camel-flow first."

### Parallel Mode (`--parallel`)

When `--parallel` is specified, implement all flows simultaneously using subagents. This requires the agent to support subagent/parallel task dispatch (e.g., Claude Code Agent tool, Bob parallel tasks). If the agent does not support subagents, fall back to sequential batch mode.

**Prerequisite:** Each flow MUST target a different `Target Module` directory. If two or more flows share the same target module, fall back to sequential mode for those flows.

1. **Discover flows:** Same as batch mode — list all `{flow-name}.tdd.md` files
2. **Load shared context once:** Read `docs/business-requirements.md`, `docs/constitution.md`, `.camel-kit/config.yaml`, and `.camel-kit/templates/yaml-generation-guide.md`. These are read-only and shared across all subagents.
3. **Dispatch one subagent per flow.** Each subagent receives:
   - The full text of this SKILL.md (so it knows the pipeline)
   - The shared context files (BRD, constitution, config, YAML guide)
   - The flow-specific TDD content
   - Instruction: "Implement flow `{flow-name}` by running Steps 1-2 of the camel-implement skill. Generate all files in `{target-module}/`. Skip the smoke test (Step 8) — it will run after all flows complete."
4. **Wait for all subagents to complete.** Collect pass/fail status from each.
5. **Run smoke tests sequentially:** After all subagents finish, run the smoke test (Step 8 from `guides/orchestrator.md`) for each flow one at a time. Smoke tests may need Docker ports and cannot run in parallel.
6. **Show combined summary:** Same format as batch mode final summary.

```
Found [N] flows to implement:
  1. flow-name-1  (docs/flows/flow-name-1/flow-name-1.tdd.md) → target: module-1/
  2. flow-name-2  (docs/flows/flow-name-2/flow-name-2.tdd.md) → target: module-2/
  ...

All flows target different modules — dispatching [N] parallel subagents...
```

**Same anti-hedging rules as batch mode apply.** Do not discuss scope, limitations, or capacity. Dispatch and go.

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

**CRITICAL: Reading documents is NOT the deliverable.** After loading context, you MUST proceed through Steps 1 and 2 to generate actual code files. Do NOT stop after reading documents or after running pre-implementation checks. The goal of this skill is to **generate working Camel route files, properties, docker-compose, and all runtime artifacts.**

---

## Step 1: Pre-Implementation Checks (Gate Only — NOT the Deliverable)

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

## Step 2: Detect Runtime and Dispatch (MANDATORY — DO NOT SKIP)

**This is the step that generates actual code. You MUST reach this step for every flow.**

Read `project.runtime` from `.camel-kit/config.yaml`.
If not set, default to `jbang`.

**Read `Target Module` from the TDD "Overview" section to determine the base directory.**

If the TDD contains a `Target Module` field (e.g., `my-module/` or `services/order-service/`), pass it to the orchestrator. If not set or is `.` (dot), use empty string (single-project layout).

**Read the file `guides/orchestrator.md` (in the same directory as this SKILL.md) and follow its instructions step by step.** The orchestrator adapts to all runtimes and drives actual code generation: route YAML files, application.properties, docker-compose, runtime artifacts, and smoke test. When the orchestrator says "Load `guides/xyz.md`", read that file from the `guides/` subdirectory next to this SKILL.md and execute its instructions — do NOT report guides as missing, they are always present.

**After reading the orchestrator, your next action MUST be executing its first applicable step (writing a file or loading a guide). If your next action after reading the orchestrator is outputting text to the user (discussing complexity, presenting alternatives, summarizing what the orchestrator says), you are violating this skill.**

Pass to the orchestrator:
- **RUNTIME**: `jbang`, `springboot`, or `quarkus` (from step above)
- **FLOW_NAME**: `{flow-name}` from parameters
- **CAMEL_VERSION**: from `.camel-kit/config.yaml` (or default)
- **PLATFORM_BOM**: resolved from `CAMEL_VERSION` + `RUNTIME` via the version mapping table in `skills/shared/mcp-setup.md`
- **TARGET_MODULE**: from the TDD "Overview" section (`Target Module` field, empty for single-project)
- **TDD**: full content of the Technical Design Document
- **BRD**: summary of business requirements
- **SCHEMAS_MISSING**: true/false from Step 1.2
- **HAS_DATAMAPPER**: true/false (TDD contains `### DataMapper:` sections)
- **HAS_ADVANCED_PATTERNS**: true/false (TDD contains "Performance & Reliability" or "Security" sections)

**DO NOT output "Analysis Completed" or any summary at this point.** The orchestrator handles all remaining steps including the implementation summary. Your job is not done until actual files have been written to disk.

---

## Error Handling

### Missing TDD

```
❌ ERROR: Technical Design Document not found

File: docs/flows/{flow-name}/{flow-name}.tdd.md

You need to create the TDD first:

  /camel-flow {flow-name}
```
