# Route Analysis Guide

> **Context variables provided by master SKILL.md:**
> - `FLOW_NAME` — the flow being tested
> - `CAMEL_VERSION` — from `.camel-kit/config.properties`
> - `RUNTIME` — from `.camel-kit/config.properties` (`project.runtime`, default: `main`)
> - `PLATFORM_BOM` — resolved from `CAMEL_VERSION` + `RUNTIME` via the version mapping table in `skills/shared/mcp-setup.md`
> - `CITRUS_VERSION` — from `.camel-kit/config.properties` (`citrus.version`)
>
> **Version mapping:** When calling MCP catalog tools (`camel_route_context`, `camel_catalog_component_doc`), translate `CAMEL_VERSION` to the correct catalog version using the version mapping table in `skills/shared/mcp-setup.md`.

---

## MCP Server Configuration (Recommended)

→ **For MCP setup, version mapping, and fallback policy:** see `skills/shared/mcp-setup.md`

The Camel MCP server provides route analysis capabilities for this skill:
- **Route Context** (`camel_route_context`) - Extract components and EIPs from routes automatically
- **Component Documentation** (`camel_catalog_component_doc`) - Get component test patterns

The Citrus MCP server provides Citrus test-generation capabilities:
- **Action catalog** (`citrus_catalog_actions`, `citrus_catalog_action`) - Verify action names and properties
- **Endpoint catalog** (`citrus_catalog_endpoints`, `citrus_catalog_endpoint`) - Verify endpoint syntax and properties
- **Schemas** (`citrus_catalog_action_schema`, `citrus_catalog_endpoint_schema`, `citrus://schema/dsl/yaml`) - Validate YAML DSL shape
- **Docs and practices** (`citrus_docs_page`, `citrus://docs/best-practices`) - Get current Citrus guidance

---

## Step 0: Load Citrus Catalog Context

### 0.1 Resolve Citrus Version

Read `.camel-kit/config.properties` and set `CITRUS_VERSION` from `citrus.version`.

If `citrus.version` is missing, use the version embedded in the generated MCP configuration. Do not assume an older
fallback version.

### 0.2 Prefer Citrus MCP

Before generating any test, use Citrus MCP when available:

```
MCP Tool: citrus_catalog_actions
Params: { "version": "{{CITRUS_VERSION}}" }

MCP Tool: citrus_catalog_endpoints
Params: { "version": "{{CITRUS_VERSION}}" }

MCP Resource: citrus://schema/dsl/yaml
MCP Resource: citrus://docs/best-practices
```

Use these results to validate:
- All action names exist in the Citrus catalog
- All action properties are valid for the selected Citrus version
- All endpoint names and endpoint properties are valid
- Testcontainer actions and variables match the selected Citrus version
- YAML structure conforms to the Citrus YAML DSL schema

### 0.3 Same-Version Cache Fallback

If Citrus MCP is unavailable, read:

```
.camel-kit/.cache/citrus/{CITRUS_VERSION}/citrus-quick-reference.md
```

This file contains:
- All available test actions with properties and types
- All endpoint types (kafka, http, sql, etc.) with configuration
- Testcontainer definitions with exposed variables
- Valid YAML structure and syntax

**If same-version cache exists — validate all generated YAML against it:**
- All action names exist in quick reference
- All properties are valid for each action
- All endpoint configurations match schema
- All testcontainer variable names are correct

**If same-version cache is missing:**
```
⚠️ WARNING: Citrus MCP and same-version quick reference are unavailable for {CITRUS_VERSION}.
Proceeding with static Citrus patterns.
Generated tests are unverified and require manual validation.
```
Proceed to Step 1 — do NOT hard-stop.

Do not use a quick reference from a different Citrus version.

---

## Step 0.5: Route Context from Graph (CONDITIONAL)

**IF** `.camel-kit/project-graph.json` exists:
- Load `guides/graph-project-context.md`
- Pass: `FLOW_NAME`, `CAMEL_VERSION`

**SKIP** if no project graph exists. Proceed directly to Step 1.

---

## Step 1: Analyze Route with MCP

### 1.1 Extract Route Context

```
Analyzing route structure with MCP...

MCP Tool: camel_route_context
Params: {
  "route": "[route-yaml-content]",
  "camelVersion": "{{CAMEL_VERSION}}",
  "platformBom": "{{PLATFORM_BOM}}",
  "runtime": "{{RUNTIME}}"
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
Params: { "component": "kafka", "camelVersion": "{{CAMEL_VERSION}}", "platformBom": "{{PLATFORM_BOM}}", "runtime": "{{RUNTIME}}" }

Get testing recommendations for kafka component.
```

**If tool call fails (fallback):**

```
ℹ️ MCP tool call failed. Using the design spec and manual route analysis.
```

Without MCP, extract test information manually:
1. **Components:** Read the route YAML file, identify all `from:` and `to:` URIs, extract component names (the part before the first `:`)
2. **EIPs:** Scan the route for `unmarshal`, `marshal`, `filter`, `choice`, `split`, `aggregate`, etc.
3. **Error handler:** Look for `onException`, `errorHandler`, or `deadLetterChannel` in the route YAML
4. **Test scenarios:** Derive from the design spec Testing Strategy section — if it has fewer than 5 scenarios, generate additional ones based on the detected components and error handler

Proceed to Step 2 with the manually extracted analysis.
