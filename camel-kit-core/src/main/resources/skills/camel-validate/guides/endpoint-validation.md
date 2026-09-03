# Endpoint & Runtime Validation Guide

> **Context variables provided by master SKILL.md:**
> - `CAMEL_VERSION` — from `.camel-kit/config.properties`
> - `RUNTIME` — project runtime from `.camel-kit/config.properties` (affects route file location)
> - `PLATFORM_BOM` — resolved from `CAMEL_VERSION` + `RUNTIME` via the version mapping table in `skills/shared/mcp-setup.md`
> - `ROUTE_FILES` — exact runtime/module-aware relative route paths from the validation inventory
> - `PROPS_FILE` — exact properties path matching the current route's module
>
> **Version mapping:** When calling MCP catalog tools, translate `CAMEL_VERSION` + `RUNTIME` to the correct `camelVersion` and `platformBom` parameters using the version mapping table in `skills/shared/mcp-setup.md`.

## Stage 2: Endpoint URI Validation (MCP Enhanced)

For every exact path in `ROUTE_FILES`, extract all endpoint URIs and validate them with its matching `PROPS_FILE`.

### 2.1 Extract Endpoints

Parse route YAML and extract all component URIs:

```
Extracting endpoints from {ROUTE_FILE}...

Found endpoints:
  - kafka:{{kafka.topic.input}}
  - sql:{{sql.insert}}
  - http:{{api.endpoint}}
```

### 2.2 Validate URIs with MCP

The pinned `camel_validate_route` schema requires both `uri` and `route`. For each endpoint, pass that actual URI and
the full current route; never send a null, empty, or dummy field. Require the top-level `uri` to echo the submitted URI
and its top-level `errors` to be absent or empty. Ignore the aggregate `valid` field because route-content processing can
overwrite it. When present, `uriValidations` is only supplementary best-effort route-extraction evidence and may omit
endpoint expressions.

**If tool call succeeds:**

```
== ENDPOINT URI VALIDATION (MCP) ==

Validating URIs against Camel {{CAMEL_VERSION}} catalog...

Endpoint 1: kafka:{{kafka.topic.input}}
  MCP Tool: camel_validate_route
  Params: { "uri": "kafka:topic", "route": "[full current route content]", "camelVersion": "{{CAMEL_VERSION}}", "platformBom": "{{PLATFORM_BOM}}", "runtime": "{{RUNTIME}}" }

  MCP catalog/URI result: ✅ VALID
  - Component: kafka exists
  - Path parameter: topic (valid)
  - Suggestions: Consider adding groupId for consumer

Endpoint 2: sql:{{sql.insert}}
  MCP Tool: camel_validate_route
  Params: { "uri": "sql:INSERT INTO orders", "route": "[full current route content]", "camelVersion": "{{CAMEL_VERSION}}", "platformBom": "{{PLATFORM_BOM}}", "runtime": "{{RUNTIME}}" }

  MCP catalog/URI result: ✅ VALID
  - Component: sql exists
  - Endpoint URI and options are valid in the bound catalog; SQL grammar is not proven by this tool
  - Warning: Ensure dataSource bean is configured (Configured means: a `forage.<name>.jdbc.*` block, a `camel.beans.dataSource` definition, or rung-2 scalar configuration — see `skills/shared/forage.md`.)

Endpoint 3: http://{{api.endpoint}}
  MCP Tool: camel_validate_route
  Params: { "uri": "http://api.example.com", "route": "[full current route content]", "camelVersion": "{{CAMEL_VERSION}}", "platformBom": "{{PLATFORM_BOM}}", "runtime": "{{RUNTIME}}" }

  MCP catalog/URI result: ✅ VALID
  Combined endpoint validation: ❌ FAIL
  - Component: http exists
  - Security: External non-local plain HTTP violates security checklist rule 2
  - Required fix: Use https:// (plain HTTP is allowed only for localhost)
```

**If tool call fails (fallback):**

```
== ENDPOINT URI VALIDATION (Manual) ==

Validating component existence...

✅ kafka - component exists
✅ sql - component exists
❌ http://api.example.com - external non-local plain HTTP is FAIL; localhost HTTP is exempt
```

---

## Stage 3: Camel Runtime Validation

Use Camel CLI to validate the route compiles:

```bash
camel run --check {ROUTE_FILE} {PROPS_FILE}
```

This validates:
- YAML parsing
- Property placeholder resolution
- Component URI syntax
- Missing dependencies
- Bean references

Show results:

```
== CAMEL RUNTIME VALIDATION ==

Running: camel run --check {ROUTE_FILE} {PROPS_FILE}

✅ Route compiles successfully
✅ Components: [kafka, sql] - all valid
✅ Properties: All placeholders resolved
✅ Beans: All references found
```

Or if errors:

```
❌ Validation failed:

Error: Property '{{kafka.topic.input}}' not defined
Fix: Add to application.properties:
     kafka.topic.input=your-topic-name

Error: Bean 'dataSource' not found
Fix: Add to application.properties:
     camel.beans.dataSource=#class:org.apache.commons.dbcp2.BasicDataSource
```
