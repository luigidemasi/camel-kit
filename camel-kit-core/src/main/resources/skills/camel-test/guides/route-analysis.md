# Route Analysis Guide

> **Context variables provided by master SKILL.md:**
> - `FLOW_NAME` — the flow being tested
> - `CAMEL_VERSION` — from `.camel-kit/config.yaml`

---

## MCP Server Configuration (Recommended)

The Camel MCP server provides route analysis capabilities:
- **Route Context** - Extract components and EIPs from routes automatically
- **Component Documentation** - Get component test patterns
- **Route Understanding** - Analyze route structure for test generation

Always attempt MCP tool calls directly. If a call fails (tool not found, network error), fall back to manual analysis from TDD and route files.

**To enable MCP server**, add to `.mcp.json`:
```json
{
  "mcpServers": {
    "camel": {
      "command": "jbang",
      "args": [
        "--repos", "redhat=https://maven.repository.redhat.com/ga/",
        "-Dquarkus.log.level=WARN",
        "org.apache.camel:camel-jbang-mcp:{{CAMEL_VERSION}}:runner"
      ]
    }
  }
}
```

**CRITICAL — MCP version stripping:** If `CAMEL_VERSION` contains a `.redhat-XXXXX` suffix (e.g., `4.14.4.redhat-00008`), strip it before passing to MCP catalog tools (`camel_catalog_*`, `camel_route_context`). The Camel Catalog MCP server uses community versions only.
Example: `4.14.4.redhat-00008` → pass `4.14.4` to MCP calls. Keep the full `.redhat` version for Maven dependencies and `pom.xml`.

---

## Step 0: Load Citrus Quick Reference (MANDATORY when available)

**Before generating any test, read:**

```
.camel-kit/.cache/citrus/{version}/citrus-quick-reference.md
```

This file contains:
- All available test actions with properties and types
- All endpoint types (kafka, http, sql, etc.) with configuration
- Testcontainer definitions with exposed variables
- Valid YAML structure and syntax

**If file exists — validate all generated YAML against it:**
- All action names exist in quick reference
- All properties are valid for each action
- All endpoint configurations match schema
- All testcontainer variable names are correct

**If file is missing:**
```
⚠️ WARNING: Citrus quick reference not found.
Proceeding with standard Citrus patterns.
Generated tests may require manual validation.
```
Proceed to Step 1 — do NOT hard-stop.

---

## Step 1: Analyze Route with MCP

### 1.1 Extract Route Context

```
Analyzing route structure with MCP...

MCP Tool: camel_route_context
Params: {
  "route": "[route-yaml-content]",
  "version": "{{CAMEL_VERSION}}"
}

Extracting components and EIPs from route...
```

**MCP provides:**

```
Route Analysis Results:

Components Used:
  1. kafka (consumer)
     - MCP provides: kafka testing patterns
     - Testcontainer: confluentinc/cp-kafka
     - Test actions: send message, verify consumption

  2. sql (producer)
     - MCP provides: sql testing patterns
     - Testcontainer: postgres
     - Test actions: verify INSERT, query results

  3. kafka (DLQ producer)
     - MCP provides: DLQ testing patterns
     - Test actions: verify error messages in DLQ

EIPs Detected:
  1. unmarshal (JSON)
     - Test: Valid JSON, Invalid JSON
  2. validate (Simple expression)
     - Test: Valid data, Invalid data
  3. filter
     - Test: Messages that pass, messages that don't

Error Handler:
  - Type: Dead Letter Channel
  - DLQ: kafka:{{dlq.endpoint}}
  - Test: Verify errors go to DLQ

Suggested Test Scenarios (from MCP analysis):
  ✓ Happy Path: Valid message → SQL INSERT → Success
  ✓ Invalid JSON: Malformed message → DLQ
  ✓ Validation Failure: Missing field → DLQ
  ✓ Filter Rejection: Filtered message → No processing
  ✓ SQL Error: Database unavailable → DLQ
```

### 1.2 Get Component Test Patterns

**For each component, query MCP:**

```
MCP Tool: camel_catalog_component_doc
Params: { "name": "kafka", "version": "{{VERSION}}" }

Get testing recommendations for kafka component.
```

**If tool call fails (fallback):**

```
ℹ️ MCP tool call failed. Using TDD and manual route analysis.
```

Without MCP, extract test information manually:
1. **Components:** Read the route YAML file, identify all `from:` and `to:` URIs, extract component names (the part before the first `:`)
2. **EIPs:** Scan the route for `unmarshal`, `marshal`, `filter`, `choice`, `split`, `aggregate`, etc.
3. **Error handler:** Look for `onException`, `errorHandler`, or `deadLetterChannel` in the route YAML
4. **Test scenarios:** Derive from TDD Section 10.1 (Test Scenarios) — if TDD has fewer than 5 scenarios, generate additional ones based on the detected components and error handler

Proceed to Step 2 with the manually extracted analysis.
