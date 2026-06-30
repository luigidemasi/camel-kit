# Route Analysis Guide

> **Context variables provided by master SKILL.md:**
> - `FLOW_NAME` — the flow being tested
> - `CAMEL_VERSION` — from `.camel-kit/config.properties`
> - `RUNTIME` — from `.camel-kit/config.properties` (`project.runtime`, default: `main`)
> - `PLATFORM_BOM` — resolved from `CAMEL_VERSION` + `RUNTIME` via the version mapping table in `skills/shared/mcp-setup.md`
> - `CITRUS_VERSION` — from `.camel-kit/config.properties` (`citrus.version`)
> - `CITRUS_MCP_VERSION` — from `.camel-kit/config.properties` (`citrus.mcp.version`) or the generated MCP server coordinate
>
> **Version mapping:** When calling Camel MCP catalog tools, translate `CAMEL_VERSION` to the correct catalog version
> using the version mapping table in `skills/shared/mcp-setup.md`.

---

## MCP Server Configuration (Recommended)

→ **For MCP setup, version mapping, and fallback policy:** see `skills/shared/mcp-setup.md`

The Camel MCP server provides route analysis capabilities for this skill:
- **Route validation** (`camel_validate_yaml_dsl`, `camel_validate_route`) - Validate syntax, endpoint URIs, and route definitions before generating tests
- **Route context** (`camel_route_context`) - Extract components, endpoint URIs, route ids, and EIPs from routes
- **Route hardening** (`camel_route_harden_context`) - Identify security and robustness concerns that should become negative tests
- **Component properties** (`camel_component_properties`) - Resolve endpoint option names, defaults, and supported configuration
- **Component documentation** (`camel_catalog_component_doc`) - Get component behavior and testing notes

The Citrus MCP server provides Citrus test-generation capabilities when `CITRUS_MCP_VERSION == CITRUS_VERSION`:
- **Action catalog** (`citrus_catalog_actions`, `citrus_catalog_action`) - Verify action names and properties
- **Endpoint catalog** (`citrus_catalog_endpoints`, `citrus_catalog_endpoint`) - Verify endpoint syntax and properties
- **Schemas** (`citrus_catalog_action_schema`, `citrus_catalog_endpoint_schema`, `citrus://schema/dsl/yaml`) - Validate YAML DSL shape
- **Docs and practices** (`citrus_docs_index`, `citrus_docs_page`, `citrus://docs/best-practices`) - Discover and read current Citrus guidance

---

## Step 0: Load Citrus Catalog Context

### 0.1 Resolve Citrus Versions

Read `.camel-kit/config.properties` and set:

```
CITRUS_VERSION = citrus.version
CITRUS_MCP_VERSION = citrus.mcp.version
```

If `citrus.mcp.version` is missing, read the generated MCP configuration and extract it from the
`org.citrusframework:citrus-mcp-server:{version}:runner` coordinate.

If `citrus.version` is missing, use `CITRUS_MCP_VERSION`. Do not assume an older fallback version.

### 0.2 Use Citrus MCP Only for Matching Versions

Use Citrus MCP for versioned catalog, schema, and documentation data only when:

```
CITRUS_MCP_VERSION == CITRUS_VERSION
```

When the versions match, load the Citrus context before generating any test:

```
MCP Tool: citrus_catalog_actions
Params: { "version": "{{CITRUS_VERSION}}" }

MCP Tool: citrus_catalog_endpoints
Params: { "version": "{{CITRUS_VERSION}}" }

MCP Tool: citrus_docs_index
Params: { "version": "{{CITRUS_VERSION}}" }

MCP Resource: citrus://schema/dsl/yaml
```

Use `citrus_docs_index` to find the relevant best-practice or component-specific documentation page, then call:

```
MCP Tool: citrus_docs_page
Params: { "page": "[page-from-index]", "version": "{{CITRUS_VERSION}}" }
```

If `CITRUS_MCP_VERSION != CITRUS_VERSION`, skip versioned Citrus MCP catalog/schema/docs calls and use the same-version
cache fallback below. Do not trust a returned `version` field to prove compatibility because list/docs responses may
echo the requested version while serving the server artifact's bundled catalog.

### 0.3 Same-Version Cache Fallback

If Citrus MCP is unavailable or `CITRUS_MCP_VERSION != CITRUS_VERSION`, read:

```
.camel-kit/.cache/citrus/{CITRUS_VERSION}/citrus-quick-reference.md
```

This file contains:
- Available test actions with properties and types
- Endpoint types (kafka, http, sql, etc.) with configuration
- Testcontainer definitions with exposed variables
- Valid YAML structure and syntax

**If same-version cache exists — validate all generated YAML against it:**
- All action names exist in quick reference
- All properties are valid for each action
- All endpoint configurations match schema
- All testcontainer variable names are correct

**If same-version cache is missing:**
```
WARNING: Citrus MCP and same-version quick reference are unavailable for {CITRUS_VERSION}.
Proceeding with static Citrus patterns.
Generated tests are unverified and require manual validation.
```
Proceed to Step 1 — do not hard-stop.

Do not use a quick reference from a different Citrus version.

---

## Step 0.5: Route Context from Graph (CONDITIONAL)

**IF** `.camel-kit/project-graph.json` exists:
- Load `guides/graph-project-context.md`
- Pass: `FLOW_NAME`, `CAMEL_VERSION`

**SKIP** if no project graph exists. Proceed directly to Step 1.

---

## Step 1: Analyze Route with MCP

### 1.1 Validate Route Before Test Generation

Validate the actual route file before deriving tests. Do not generate integration tests for a route that the Camel
catalog says is invalid.

For Camel YAML DSL routes:

```
MCP Tool: camel_validate_yaml_dsl
Params: {
  "route": "[route-yaml-content]",
  "camelVersion": "{{CAMEL_VERSION}}",
  "platformBom": "{{PLATFORM_BOM}}",
  "runtime": "{{RUNTIME}}"
}
```

For all route formats:

```
MCP Tool: camel_validate_route
Params: {
  "route": "[route-content]",
  "camelVersion": "{{CAMEL_VERSION}}",
  "platformBom": "{{PLATFORM_BOM}}",
  "runtime": "{{RUNTIME}}"
}
```

If validation reports syntax, endpoint URI, or catalog errors, fix or report those route errors before generating tests.

### 1.2 Extract Route Context

```
MCP Tool: camel_route_context
Params: {
  "route": "[route-content]",
  "camelVersion": "{{CAMEL_VERSION}}",
  "platformBom": "{{PLATFORM_BOM}}",
  "runtime": "{{RUNTIME}}"
}
```

`camel_route_context` provides structural route data such as components, endpoint URIs, route ids, and EIPs. It does not
provide ready-made Citrus scenarios or Testcontainer mappings.

Record the raw MCP output separately from derived analysis:

```
MCP Route Context:
  Components:
    - kafka
    - sql
  Endpoint URIs:
    - kafka:orders
    - sql:insertOrder
    - kafka:orders.dlq
  EIPs:
    - unmarshal
    - validate
    - filter
  Error handling:
    - dead letter channel to kafka:orders.dlq

Derived Test Analysis:
  Testcontainers:
    - kafka, because kafka endpoints are external infrastructure
    - postgresql, because sql endpoint uses a datasource backed by JDBC
  Scenarios:
    - Happy path from input topic to database row
    - Invalid JSON to dead letter topic
    - Validation failure to dead letter topic
    - Filter rejection leaves no database row
```

### 1.3 Inspect Component Properties

For each component scheme found in route endpoints, query component metadata before writing test endpoints or
`application-test.properties`:

```
MCP Tool: camel_component_properties
Params: {
  "component": "[component-name]",
  "camelVersion": "{{CAMEL_VERSION}}",
  "platformBom": "{{PLATFORM_BOM}}",
  "runtime": "{{RUNTIME}}"
}
```

Use this metadata plus the actual route URI options to preserve real topic names, consumer groups, serializers,
datasource names, base URLs, timeouts, query parameters, and other endpoint-specific options. Override only the values
needed for test isolation, such as broker URLs, datasource URLs, credentials, or HTTP mock hosts.

### 1.4 Derive Negative Tests from Hardening Analysis

Run route hardening and convert concrete findings into negative or resilience test scenarios:

```
MCP Tool: camel_route_harden_context
Params: {
  "route": "[route-content]",
  "camelVersion": "{{CAMEL_VERSION}}",
  "platformBom": "{{PLATFORM_BOM}}",
  "runtime": "{{RUNTIME}}"
}
```

Examples:
- Missing input validation → malformed payload and oversized payload tests
- Dynamic endpoint or expression risk → injection-style input tests
- Missing timeout or retry policy → unavailable downstream test
- Authentication or TLS warning → invalid credential or connection failure test

These hardening-derived scenarios supplement the design-spec scenarios and graph-derived scenarios.

### 1.5 Get Component Test Patterns

For each external component, query documentation for component behavior and test patterns:

```
MCP Tool: camel_catalog_component_doc
Params: {
  "component": "[component-name]",
  "camelVersion": "{{CAMEL_VERSION}}",
  "platformBom": "{{PLATFORM_BOM}}",
  "runtime": "{{RUNTIME}}"
}
```

Use documentation for component-specific behavior only. Keep endpoint option values grounded in the actual route URI and
`camel_component_properties` metadata.

### 1.6 Fallback When Camel MCP Is Unavailable

If a Camel MCP tool call fails, continue with manual route analysis and mark the relevant analysis source as fallback:

```
INFO: Camel MCP tool call failed. Using the design spec and manual route analysis.
```

Manual extraction:
1. **Components:** Read the route file, identify all `from:` and `to:` URIs, extract component names before the first `:`
2. **EIPs:** Scan for `unmarshal`, `marshal`, `filter`, `choice`, `split`, `aggregate`, `validate`, and transformations
3. **Error handler:** Look for `onException`, `errorHandler`, or `deadLetterChannel`
4. **Endpoint options:** Preserve real URI query parameters and property placeholders from the route
5. **Test scenarios:** Derive from the design spec Testing Strategy section, graph context, detected EIPs, and error handler

Proceed to Step 2 with both the raw MCP/manual context and the derived test analysis.
