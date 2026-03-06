# Endpoint & Runtime Validation Guide

> **Context variables provided by master SKILL.md:**
> - `FLOW_NAME` — the flow being validated
> - `CAMEL_VERSION` — from `.camel-kit/config.yaml`

## Stage 2: Endpoint URI Validation (MCP Enhanced)

Extract all endpoint URIs from the route and validate them.

### 2.1 Extract Endpoints

Parse route YAML and extract all component URIs:

```
Extracting endpoints from {flow-name}.camel.yaml...

Found endpoints:
  - kafka:{{kafka.topic.input}}
  - sql:{{sql.insert}}
  - http:{{api.endpoint}}
```

### 2.2 Validate URIs with MCP

**If tool call succeeds:**

```
== ENDPOINT URI VALIDATION (MCP) ==

Validating URIs against Camel {{VERSION}} catalog...

Endpoint 1: kafka:{{kafka.topic.input}}
  MCP Tool: camel_validate_route
  Params: { "uri": "kafka:topic", "version": "{{VERSION}}" }

  Result: ✅ VALID
  - Component: kafka exists
  - Path parameter: topic (valid)
  - Suggestions: Consider adding groupId for consumer

Endpoint 2: sql:{{sql.insert}}
  MCP Tool: camel_validate_route
  Params: { "uri": "sql:INSERT INTO orders", "version": "{{VERSION}}" }

  Result: ✅ VALID
  - Component: sql exists
  - Query: valid SQL syntax
  - Warning: Ensure dataSource bean is configured

Endpoint 3: http://{{api.endpoint}}
  MCP Tool: camel_validate_route
  Params: { "uri": "http://api.example.com", "version": "{{VERSION}}" }

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
camel run --check {flow-name}.camel.yaml application.properties
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

Running: camel run --check {flow-name}.camel.yaml application.properties

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
