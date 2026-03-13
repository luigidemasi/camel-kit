# Route Analysis Guide

> **Context variables provided by master SKILL.md:**
> - `FLOW_NAME` — the flow being tested
> - `CAMEL_VERSION` — from `.camel-kit/config.yaml`
>
> **Version mapping:** When calling MCP catalog tools (`camel_route_context`, `camel_catalog_component_doc`), translate `CAMEL_VERSION` to the correct catalog version using the version mapping table in `skills/shared/mcp-setup.md`.

---

## MCP Server Configuration (Recommended)

→ **For MCP setup, version mapping, and fallback policy:** see `skills/shared/mcp-setup.md`

The Camel MCP server provides route analysis capabilities for this skill:
- **Route Context** (`camel_route_context`) - Extract components and EIPs from routes automatically
- **Component Documentation** (`camel_catalog_component_doc`) - Get component test patterns

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
Params: { "name": "kafka", "version": "{{CAMEL_VERSION}}" }

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
4. **Test scenarios:** Derive from the TDD "Testing Strategy" section — if TDD has fewer than 5 scenarios, generate additional ones based on the detected components and error handler

Proceed to Step 2 with the manually extracted analysis.
