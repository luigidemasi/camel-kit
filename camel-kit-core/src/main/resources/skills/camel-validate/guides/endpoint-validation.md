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

**If tool call succeeds:**

```
== ENDPOINT URI VALIDATION (MCP) ==

Validating URIs against Camel {{CAMEL_VERSION}} catalog...

Endpoint 1: kafka:{{kafka.topic.input}}
  MCP Tool: camel_validate_route
  Params: { "uri": "kafka:topic", "camelVersion": "{{CAMEL_VERSION}}", "platformBom": "{{PLATFORM_BOM}}", "runtime": "{{RUNTIME}}" }

  Result: ✅ VALID
  - Component: kafka exists
  - Path parameter: topic (valid)
  - Suggestions: Consider adding groupId for consumer

Endpoint 2: sql:{{sql.insert}}
  MCP Tool: camel_validate_route
  Params: { "uri": "sql:INSERT INTO orders", "camelVersion": "{{CAMEL_VERSION}}", "platformBom": "{{PLATFORM_BOM}}", "runtime": "{{RUNTIME}}" }

  Result: ✅ VALID
  - Component: sql exists
  - Query: valid SQL syntax
  - Warning: Ensure dataSource bean is configured (Configured means: a `forage.<name>.jdbc.*` block, a `camel.beans.dataSource` definition, or rung-2 scalar configuration — see `skills/shared/forage.md`.)

Endpoint 3: http://{{api.endpoint}}
  MCP Tool: camel_validate_route
  Params: { "uri": "http://api.example.com", "camelVersion": "{{CAMEL_VERSION}}", "platformBom": "{{PLATFORM_BOM}}", "runtime": "{{RUNTIME}}" }

  Result: ⚠️ WARNING
  - Component: http exists
  - Security: Using HTTP instead of HTTPS
  - Recommendation: Use https:// for production
```

**If tool call fails (fallback):**

```
== ENDPOINT URI VALIDATION (Manual) ==

Validating component existence...

✅ kafka - component exists
✅ sql - component exists
⚠️ http - consider using https for security
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
