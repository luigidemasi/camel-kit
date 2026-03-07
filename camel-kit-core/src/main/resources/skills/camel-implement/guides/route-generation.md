# Route Generation Guide (Steps 2 + 3 + 4)

> **This guide is loaded by the runtime orchestrator.**
> Context variables provided by the orchestrator:
> - `FLOW_NAME` — the flow being implemented
> - `ROUTE_DIR` — directory where `{FLOW_NAME}.camel.yaml` and XSLT files are written
> - `ROUTE_FILE` — full path to the route file (`{ROUTE_DIR}/{FLOW_NAME}.camel.yaml`)
> - `CAMEL_VERSION` — Camel version from `.camel-kit/config.yaml`
> - `TARGET_MODULE` — module prefix from TDD "Overview" section (empty for single-project)

---

## MCP Server Configuration (Recommended)

The Camel MCP server provides powerful code generation and validation tools:
- **Component Documentation** (`camel_catalog_component_doc`) - Full options and Maven coords for a component at the project Camel version
- **Data Format Documentation** (`camel_catalog_dataformat_doc`) - Full options and Maven coords for a data format at the project Camel version
- **Language Documentation** (`camel_catalog_language_doc`) - Full syntax, options, and Maven coords for an expression language at the project Camel version
- **EIP List** (`camel_catalog_eips`) - All EIPs available in the project Camel version, filterable by category
- **EIP Documentation** (`camel_catalog_eip_doc`) - Full options and YAML DSL usage for a specific EIP at the project Camel version
- **URI Validation** (`camel_validate_route`) - Validate endpoint URIs and catch typos before runtime

The camel-knowledge MCP server provides Red Hat Build of Apache Camel documentation:
- **Red Hat Component Info** (`camel_rh_build_component_info`) - Check if a component is supported by Red Hat, get configuration reference and known issues
- **Red Hat Docs Search** (`camel_rh_build_search`) - Search Red Hat Build of Apache Camel docs for supported configurations, release notes, migration info

All catalog calls MUST pass the Camel version from `.camel-kit/config.yaml` as the `version` parameter.

**CRITICAL — MCP version stripping:** If `CAMEL_VERSION` contains a `.redhat-XXXXX` suffix (e.g., `4.14.4.redhat-00008`), strip it before passing to MCP catalog tools (`camel_catalog_*`, `camel_validate_route`, `camel_route_context`). The Camel Catalog MCP server uses community versions only.
Example: `4.14.4.redhat-00008` → pass `4.14.4` to MCP calls. Keep the full `.redhat` version for Maven dependencies and `pom.xml`.

Always attempt MCP tool calls directly — do not check for `.mcp.json` or try to detect MCP availability upfront. If a tool call fails (tool not found, network error, timeout), fall back to the bundled component skill files or proceed without validation with a warning.

**To enable MCP server**, add to `.mcp.json`:
```json
{
  "mcpServers": {
    "camel": {
      "command": "jbang",
      "args": [
        "--repos", "redhat=https://maven.repository.redhat.com/ga/",
        "-Dquarkus.log.level=WARN",
        "org.apache.camel:camel-jbang-mcp:LATEST:runner"
      ]
    }
  }
}
```

---

## Step 2: Load Component Documentation

**MANDATORY — do not skip, do not proceed to Step 3 without completing this step for every component.**

Extract every component used in the TDD (source, sink, DLQ, any `to()` targets) and retrieve its full documentation. This is the single source of truth for URI syntax, endpoint options, component-level options, and Maven coordinates. **Never use training-data knowledge as a substitute** — component option names, default values, and URI syntax change between Camel versions and must be verified against the catalog for the project's exact version.

### 2.1 With MCP (Required)

**Call `camel_catalog_component_doc` directly for EVERY component — no exceptions. Do not check for MCP availability upfront.**

**CRITICAL — use the exact component scheme from the route URI.** The component name passed to `camel_catalog_component_doc` MUST be the exact URI scheme used in the route's `from:` or `to:` (e.g., `smtp`, not `mail`; `aws2-sqs`, not `aws`; `kafka`, not `messaging`). Many Camel components share a parent artifact but are distinct components with distinct schemes, options, and property prefixes. Always use the specific scheme — never a parent, alias, or abstract name.

For each component, call `camel_catalog_component_doc` and extract:

| Field | Where to use it |
|-------|----------------|
| `syntax` | URI pattern in `from:` / `to:` |
| `path parameters` (kind=path) | URI path segment, in order |
| `endpoint options` (kind=parameter) | `parameters:` block in YAML |
| `component options` | `camel.component.<name>.<option>` in `application.properties` |
| `groupId` + `artifactId` | Maven dependency in `pom.xml` / `camel.jbang.dependencies` |

```
Loading component documentation via MCP...

Component: [component-name]
  MCP Tool: camel_catalog_component_doc
  Params: { "name": "[component-name]", "version": "{{CAMEL_VERSION}}" }

  ✓ Syntax:            [exact URI syntax from catalog]
  ✓ Path parameters:   [list with order]
  ✓ Endpoint options:  [all valid parameter names and types]
  ✓ Component options: [all valid component-level config keys]
  ✓ Maven:             org.apache.camel:camel-[name]:{{CAMEL_VERSION}}
```

Repeat for every component before writing any YAML.

**Red Hat support check (MANDATORY when camel-knowledge MCP is available):**

After loading component documentation, call `camel_rh_build_component_info` to check whether the component is supported by Red Hat Build of Apache Camel. If the tool call fails (tool not found, network error), skip this step silently.

```
Red Hat support check:
  MCP Tool: camel_rh_build_component_info
  Params: { "component": "[component-name]" }

  Result: [supported / not found in Red Hat docs]
```

- **If supported:** proceed with implementation.
- **If NOT supported by Red Hat:** raise a WARNING to the user. Search for a Red Hat-supported alternative that provides equivalent functionality and present both options. Let the user decide whether to proceed with the unsupported component or switch to the alternative before continuing implementation.

**If `camel_catalog_component_doc` returns an error (component not found):**

```
❌ Component '[name]' not found in Camel {{CAMEL_VERSION}} catalog.

Options:
1. Search for the correct component name with camel_catalog_components
2. Confirm the component exists in this Camel version
3. Update the TDD with the correct component before proceeding
```

Do NOT guess a component name or proceed with an unverified component.

### 2.2 Fallback (tool call failed)

**Only use this path when the `camel_catalog_component_doc` call fails (tool not found, network error, timeout).**

```
Loading component documentation from bundled skills...

Component: [component-name]
  ✓ {skills.folder}/camel-component-[name]/SKILL.md
  ✓ {skills.folder}/camel-component-[name]/schema.json
  - Syntax:   [from skill file]
  - Maven:    [from skill file]
```

If neither MCP nor a bundled skill exists for a component, **stop and ask the user** to provide the component documentation before continuing. Do not invent option names.

---

## Step 3: Generate Camel YAML Route

**File location:** Write to `ROUTE_DIR` (provided by the orchestrator).

Create file: `{FLOW_NAME}.camel.yaml`

### 3.1 Follow TDD Specification

Generate the route by translating the TDD to Camel YAML DSL:

1. **Route Structure** (from TDD "Overview"):
   - Route ID: `{FLOW_NAME}`
   - Description: from TDD overview

2. **Source Configuration** (from TDD "Source System"):
   - Component: from TDD
   - URI: Use property placeholders for endpoints
   - Parameters: Only endpoint-specific (NOT connection details)

3. **Processing Steps** (from TDD "Processing Steps"):
   - For each EIP in the TDD, call `camel_catalog_eip_doc` (with `CAMEL_VERSION`) to get the authoritative option names and YAML DSL structure before writing the step — see Rule 0d
   - Translate each step from TDD to Camel EIP using only catalog-verified option names
   - **If DataMapper artifacts were generated (by a prior guide)**, the DataMapper step block is already injected into the YAML — do not duplicate it
   - Preserve order from TDD
   - Use `steps:` array format for Kaoto compatibility

4. **Sink Configuration** (from TDD "Sink System"):
   - Component: from TDD
   - URI: Use property placeholders
   - Parameters: Only endpoint-specific

5. **Error Handling** (from TDD "Error Handling"):
   - Strategy: from TDD (Dead Letter Channel, onException, etc.)
   - DLQ: Use property placeholder
   - Retry policy: from TDD configuration

### 3.2 YAML Generation Rules

**CRITICAL RULES:**

0. **Use only catalog-verified names** — Every component scheme, endpoint option name, component-level option name, and Maven coordinate used in the generated YAML and `application.properties` MUST come from the documentation loaded in Step 2. Do not use option names, parameter names, or URI syntax from training data or memory. If you are unsure whether an option exists or is spelled correctly, call `camel_catalog_component_doc` again before writing it.

0b. **Data format names and options must also be catalog-verified** — If the TDD requires `unmarshal` or `marshal`, call `camel_catalog_dataformat_doc` for the data format (e.g. `jackson`, `jaxb`, `csv`, `avro`) with the project Camel version before generating the YAML block. Never assume the data format name, its configuration options, or its Maven coordinates from training data. Example:
   ```
   MCP Tool: camel_catalog_dataformat_doc
   Params: { "name": "jackson", "version": "{{CAMEL_VERSION}}" }
   → Use the returned options and Maven coordinates in the generated YAML and application.properties
   ```

0c. **Expression language names and options must also be catalog-verified** — Before writing any expression language value in the YAML (`simple`, `jsonpath`, `xpath`, `jq`, `groovy`, etc.), call `camel_catalog_language_doc` for that language with the project Camel version. This ensures the language is available in the project's Camel version, its syntax is correct, and any required Maven dependency (e.g. `camel-jsonpath`, `camel-jq`) is included. Never assume a language name or its syntax from training data. Example:
   ```
   MCP Tool: camel_catalog_language_doc
   Params: { "name": "jsonpath", "version": "{{CAMEL_VERSION}}" }
   → Use the returned syntax rules and Maven coordinates in the generated YAML
   ```
   If the language requires a separate Maven artifact, add it to `application.properties` (`camel.jbang.dependencies`) and `pom.xml`.

0d. **EIP names and options must also be catalog-verified** — Before writing any EIP step in the YAML (`filter`, `split`, `aggregate`, `choice`, `multicast`, `enrich`, `wireTap`, `throttle`, `idempotentConsumer`, etc.), call `camel_catalog_eip_doc` for that EIP with the project Camel version. This ensures the EIP exists in the project's version and that all option names and their types are correct. Never assume EIP option names from training data. Example:
   ```
   MCP Tool: camel_catalog_eip_doc
   Params: { "name": "filter", "version": "{{CAMEL_VERSION}}" }
   → Use the returned options and YAML DSL structure in the generated YAML
   ```

0e. **HTTP header cleanup between HTTP endpoints** — If the route has both an inbound HTTP consumer (`platform-http`, `servlet`, `jetty`, `netty-http`) **and** one or more outbound HTTP producer calls (`http`, `https`), insert a `removeHeaders` step immediately before **each** outbound HTTP call to remove all `CamelHttp*` headers set by the inbound request. Failing to do this causes inbound headers (`CamelHttpMethod`, `CamelHttpPath`, `CamelHttpQuery`, `CamelHttpUri`, `CamelHttpResponseCode`, etc.) to leak into the outbound call and can produce incorrect behaviour.

   ```yaml
   steps:
     # ... processing steps ...

     # REQUIRED before every outbound HTTP call when the route also has an HTTP consumer
     - removeHeaders:
         pattern: "CamelHttp*"

     - to:
         uri: "http:{{backend.host}}/api/endpoint"
   ```

   This rule applies once per outbound HTTP call — if the route calls two different HTTP backends, add `removeHeaders` before each one.

   → For detailed implementation guidance and examples, load `guides/sequential-http-calls.md`.

0f. **Use `toD` for dynamic URIs and dynamic parameters** — `to` resolves its URI **once at startup** as a static string. Any `${...}` Simple expression in a `to` URI **or** in its `parameters:` block is treated as a literal string and is never evaluated at runtime. This applies equally to the URI path and to every value in the `parameters:` map.

   **Case 1 — dynamic expression in the URI path:**
   ```yaml
   # WRONG — ${header.routeName} is sent as the literal string "${header.routeName}"
   - to:
       uri: "direct:${header.routeName}"

   # CORRECT
   - toD:
       uri: "direct:${header.routeName}"
   ```

   **Case 2 — dynamic expression in a `parameters:` value:**
   ```yaml
   # WRONG — q: "${header.city}" passes the literal string "${header.city}",
   # not the value of the header. parameters: values are always static.
   - to:
       uri: "https://{{api.host}}/data/2.5/weather"
       parameters:
         q: "${header.city}"        # ❌ never evaluated
         appid: "{{api.key}}"

   # CORRECT — move dynamic values into the URI string and use toD
   - toD:
       uri: "https://{{api.host}}/data/2.5/weather?q=${header.city}&appid={{api.key}}&units=metric"
   ```

   For HTTP calls with multiple dynamic query parameters, inline all dynamic values directly in the `toD` URI string. Static `{{placeholder}}` values may stay in the URI string or in `parameters:` — only `${expression}` values must be inlined.

   **Enforcement:** scan every `to:` step in the generated YAML. If the `uri` value **or** any `parameters:` value contains `${...}`, rewrite the step as `toD` with all dynamic values interpolated into the URI string. Property placeholders `{{...}}` are safe in both `to` and `parameters:` — they resolve at startup.

0g. **Never `unmarshal: json:` before a JSON DataMapper step** — With `useJsonBody: true`, the `xslt-saxon` component reads the Exchange body as a JSON **string** and passes it to the XSLT `xsl:param` via `json-to-xml()`. The body must be a JSON string or InputStream. If `unmarshal: json:` appears before the DataMapper step, the body is converted to a `java.util.LinkedHashMap`; the component then receives a `Map` instead of a JSON string and cannot pass it to the XSLT param, causing the route to fail.

   - Body = JSON String or InputStream → `useJsonBody: true` works correctly
   - Body = `LinkedHashMap` (after `unmarshal: json:`) → XSLT param receives nothing usable → failure

   `unmarshal: json:` may be placed **after** the DataMapper step if subsequent steps need a typed object.

0h. **Marshal body before HTTP response** — When a route starts with an HTTP consumer (`platform-http`, `servlet`, `jetty`, `netty-http`) and any step in the route unmarshals the body to a Java object (`unmarshal: json:` produces a `LinkedHashMap`; `unmarshal: jaxb:` produces a JAXB object), the HTTP response writer cannot serialize the Java object back to the wire. Add a `marshal` step at the **end** of the route to convert the body back to the response format.

   ```yaml
   # Route with platform-http source and unmarshal mid-route
   steps:
     # ... processing steps that need the body as a Map ...

     - log:
         message: "Done processing"

     # REQUIRED — serialize body back to JSON for the HTTP response
     - marshal:
         json:
           library: Jackson
   ```

   **When to apply:** scan the generated route — if the source is an HTTP consumer **and** there is an `unmarshal` step anywhere in the route, add a matching `marshal` step as the last step. Match the data format: `unmarshal: json:` → `marshal: json:`, `unmarshal: jaxb:` → `marshal: jaxb:`.

1. **Clean Routes** - NO connection details in YAML:
   ```yaml
   # CORRECT
   from:
     uri: "kafka:{{kafka.topic.input}}"

   # WRONG - no brokers, credentials, or connection details!
   from:
     uri: "kafka:topic?brokers=localhost:9092"
   ```

2. **Component-Level Config** - Put in application.properties:
   ```properties
   # In application.properties, NOT in route YAML
   camel.component.kafka.brokers=localhost:9092
   camel.component.sql.dataSource=#dataSource
   ```

3. **Use Steps Array** - For Kaoto compatibility:
   ```yaml
   from:
     uri: "kafka:{{kafka.topic.input}}"
     steps:
       - unmarshal:
           json:
             library: Jackson
       - to:
           uri: "sql:{{sql.insert}}"
   ```

3a. **DataMapper Step** - For field-level transformations (if XSLT generated):
   ```yaml
   steps:
     # Place DataMapper after unmarshal, before validation
     - step:
         id: order-datamapper-step
         steps:
           - to:
               id: order-datamapper-xslt
               uri: "xslt-saxon:order-datamapper-a1b2c3d4.xsl"
   ```

   **When to include:**
   - ONLY if DataMapper artifacts were generated (TDD had a `### DataMapper:` section)
   - The step block is already injected by the DataMapper guide — do not duplicate it
   - Logical placement: AFTER unmarshal (when data is in structured format), BEFORE validation

   **Component required:** `camel-xslt-saxon` (verified by the DataMapper guide)

3b. **DataMapper Parameters** - Pass Camel Variables/Headers to XSLT (if TDD "Processing Steps" section defines parameters):
   ```yaml
   steps:
     - step:
         id: order-datamapper-step
         steps:
           - to:
               id: order-datamapper-xslt
               uri: "xslt-saxon:order-datamapper-a1b2c3d4.xsl"
               parameters:
                 # Map from TDD Section 3.3 table
                 userId: "${header.userId}"           # From Header
                 customerProfile: "${variable.customerProfile}"  # From Variable
                 tenantId: "${header.tenantId}"       # From Header
   ```

   **Parameter mapping rules:**
   - Headers: `${header.paramName}`
   - Variables: `${variable.paramName}`
   - Exchange properties: `${exchangeProperty.paramName}`
   - Parameter names must match `<xsl:param name="...">` in XSLT

4. **Expression Objects** - Not booleans:
   ```yaml
   # CORRECT
   handled:
     constant:
       expression: "true"

   # WRONG
   handled: true
   ```

5. **Route-Level Error Handler** - For visibility:
   ```yaml
   - route:
       id: flow-name
       errorHandler:
         deadLetterChannel:
           deadLetterUri: "kafka:{{kafka.topic.dlq}}"
       from:
         # ...
   ```

6. **Jakarta EE namespaces when Camel >= 4.0** — Apache Camel 4.x requires Jakarta EE 9+ APIs. If the project's Camel version (from `.camel-kit/config.yaml`) is **4.0 or later**, always use `jakarta.*` package names. If the version is older than 4.0, keep `javax.*`.

   **Java SE packages are exempt** — `javax.sql.*`, `javax.xml.*`, `javax.swing.*`, and other packages that belong to the Java Standard Edition are NOT affected by this rule. Only Jakarta EE APIs change.

   | Functional Area | Camel < 4.0 (javax) | Camel >= 4.0 (jakarta) |
   |---|---|---|
   | Servlet | `javax.servlet.*` | `jakarta.servlet.*` |
   | Persistence (JPA) | `javax.persistence.*` | `jakarta.persistence.*` |
   | CDI | `javax.enterprise.*` | `jakarta.enterprise.*` |
   | Bean Validation | `javax.validation.*` | `jakarta.validation.*` |
   | JAX-RS | `javax.ws.rs.*` | `jakarta.ws.rs.*` |
   | JSON Binding | `javax.json.bind.*` | `jakarta.json.bind.*` |
   | JSON Processing | `javax.json.*` | `jakarta.json.*` |
   | JMS | `javax.jms.*` | `jakarta.jms.*` |
   | Annotation | `javax.annotation.*` | `jakarta.annotation.*` |
   | Mail | `javax.mail.*` | `jakarta.mail.*` |
   | Transaction (JTA) | `javax.transaction.*` | `jakarta.transaction.*` |
   | Faces (JSF) | `javax.faces.*` | `jakarta.faces.*` |
   | WebSocket | `javax.websocket.*` | `jakarta.websocket.*` |

   ```yaml
   # Camel >= 4.0 — CORRECT
   - unmarshal:
       jaxb:
         contextPath: com.example.model    # class uses jakarta.xml.bind annotations

   # Camel >= 4.0 — WRONG
   - unmarshal:
       jaxb:
         contextPath: com.example.model    # class uses javax.xml.bind annotations
   ```

   **Validation gate:** After generating all YAML and property files, scan for any `javax.` reference that belongs to the Jakarta EE list above. If the Camel version is >= 4.0, replace it with the corresponding `jakarta.` equivalent before saving.

7. **Choosing between global and route-scoped `onException`** — Use global scope by default; use route scope only when handling differs per route.

   | Use case | Correct scope |
   |----------|--------------|
   | Same exception handled identically across all routes in the file | **Global** (`- onException:` at top level) |
   | Exception handling differs route by route | **Route** (`onException:` inside the route) |
   | Only one route in the file | Either — prefer **global** for consistency |

   **Do not default to route-scoped just to avoid placement complexity.** Global `onException` is the standard, idiomatic choice for cross-cutting error handling in Camel YAML DSL. The ordering rule below is a mechanical constraint to follow, not a reason to prefer route scope.

   **Ordering constraint — enforced by the Camel YAML DSL schema:** A top-level `- onException:` element MUST appear before the first `- route:` element. Placing it after a route is a **schema validation error**, not a runtime warning.

   ```yaml
   # CORRECT — global onException declared before routes
   - onException:
       exception:
         - com.example.ValidationException
       handled:
         constant:
           expression: "true"
       steps:
         - to:
             uri: "kafka:{{kafka.topic.invalid}}"

   - route:
       id: route-one
       from:
         uri: "kafka:{{kafka.topic.input}}"
         steps:
           - to:
               uri: "direct:process"

   - route:
       id: route-two
       from:
         uri: "direct:process"
         steps:
           - to:
               uri: "http:{{api.host}}"

   # WRONG — global onException after a route (schema validation error)
   - route:
       id: route-one
       from:
         uri: "kafka:{{kafka.topic.input}}"

   - onException:   # schema error — must appear before all routes
       exception:
         - com.example.ValidationException
   ```

   **Validation gate:** Scan the generated YAML top-to-bottom. If any `- onException:` top-level element appears after a `- route:` element, move it above all routes before saving the file.

### 3.3 Generate File

Create `{FLOW_NAME}.camel.yaml` in `ROUTE_DIR`:

```yaml
# ============================================
# Camel Route: {FLOW_NAME}
# Generated from TDD: docs/flows/{FLOW_NAME}/{FLOW_NAME}.tdd.md
# ============================================

# Global onException MUST be declared before any route (Rule 6).
# Include ONLY if TDD Section 5 defines global (cross-route) onException handling.
# Route-scoped error handling (errorHandler:, doTry/doCatch) stays inside the route.
- onException:
    exception:
      - [exception class from TDD]
    handled:
      constant:
        expression: "true"
    steps:
      - to:
          uri: "[component]:{{dlq.endpoint}}"

- route:
    id: {FLOW_NAME}
    description: [from TDD overview]

    # Error handling strategy from TDD Section 5
    errorHandler:
      deadLetterChannel:
        deadLetterUri: "[component]:{{dlq.endpoint}}"
        redeliveryPolicy:
          maximumRedeliveries: {{error.max.retries}}
          redeliveryDelay: {{error.retry.delay}}
          backOffMultiplier: {{error.backoff.multiplier}}

    # Source from TDD Section 2
    from:
      uri: "[component]:{{source.endpoint}}"

      steps:
        # Processing steps from TDD Section 3
        # (unmarshal only if explicitly required — see Rule in Step 3.2)

        # DataMapper transformation (injected by DataMapper guide if applicable)
        - step:
            id: kaoto-datamapper-{id}
            steps:
              - to:
                  id: kaoto-datamapper-xslt-{4hexchars}
                  uri: xslt-saxon:kaoto-datamapper-{id}.xsl
                  # Pass parameters to XSLT if TDD Section 3.3 defines parameters
                  parameters:
                    userId: "${header.userId}"
                    customerProfile: "${variable.customerProfile}"
                    tenantId: "${header.tenantId}"

        - validate:
            simple: "[validation expression from TDD]"

        - filter:
            simple: "[filter condition from TDD]"

        # Additional steps from TDD...

        # Sink from TDD Section 4
        - to:
            uri: "[component]:{{sink.endpoint}}"
```

Show generation summary:

```
Generated: {FLOW_NAME}.camel.yaml

Route Structure:
  ID: {FLOW_NAME}
  Source: [component]:{{source.endpoint}}
  Steps: [list of EIPs used]
  Sink: [component]:{{sink.endpoint}}
  Error Handler: [strategy]

Proceeding to validation...
```

---

## Step 4: Route Validation Loop (MANDATORY)

**CRITICAL — You MUST complete this step before generating any supporting files. Do NOT skip it, do NOT proceed on failure without attempting fixes.**

Always attempt `camel_validate_route` directly. If the call fails (tool not found, network error), skip to Step 4.4. The validate-fix-retry loop is non-negotiable when the tool is available.

### 4.1 Validate the Full Route

Pass the **entire content** of `{FLOW_NAME}.camel.yaml` to `camel_validate_route`:

```
MCP Tool: camel_validate_route
Params:
{
  "route": "<full YAML file content>",
  "version": "{{CAMEL_VERSION}}"
}
```

**Before calling `camel_validate_route`, perform this static check (Rule 0f):**

Scan every `to:` step in the generated YAML. If the `uri` value **or** any `parameters:` value contains `${...}` (a Simple language expression), the step must be rewritten as `toD` with all dynamic values inlined into the URI string — `to` never evaluates `${...}` at runtime. Fix these before validation:

```yaml
# WRONG — expression in URI or in parameters:
- to:
    uri: "direct:${header.routeName}"
- to:
    uri: "https://{{host}}/api"
    parameters:
      q: "${header.city}"

# CORRECT
- toD:
    uri: "direct:${header.routeName}"
- toD:
    uri: "https://{{host}}/api?q=${header.city}"
```

Note: `{{...}}` property placeholders are resolved at startup and are safe in both `to` and `parameters:`.

The tool validates:
- All component schemes exist in the Camel {{CAMEL_VERSION}} catalog
- URI path parameters are in the correct order and format
- All endpoint option names are valid (catches misspellings like `datasource` vs `dataSource`)
- Required parameters are present
- No unknown options are used

### 4.2 Fix -> Re-query -> Retry Loop

**If validation returns errors, follow this loop — up to 3 attempts:**

```
Attempt N/3: camel_validate_route returned errors:

  ❌ [component]: [error description]
     [suggestion from tool]
```

**For each error, before editing the YAML:**

1. **Re-query the failing component** with `camel_catalog_component_doc` to get the authoritative option list:
   ```
   MCP Tool: camel_catalog_component_doc
   Params: { "name": "[component-name]", "version": "{{CAMEL_VERSION}}" }
   ```
2. **Identify the correct option name/value** from the catalog response — do not guess.
3. **Apply the fix** to `{FLOW_NAME}.camel.yaml`.
4. **Run `camel_validate_route` again** with the updated file content.
5. If validation passes → proceed to Step 4.3
6. If errors remain → repeat from step 1 (up to 3 total attempts).

**After 3 failed attempts:**

```
Route validation still failing after 3 fix attempts.

Remaining errors:
[list errors]

These errors require manual intervention. Possible causes:
- Component option not available in Camel {{CAMEL_VERSION}}
- TDD specifies a component configuration that is incompatible
- YAML DSL syntax issue not covered by catalog validation

Action required:
1. Review the errors above
2. Check component docs: camel_catalog_component_doc { "name": "...", "version": "{{CAMEL_VERSION}}" }
3. Update the TDD if the component choice needs to change
4. Re-run /camel-implement once the TDD is corrected
```

Stop and report the errors — do not generate supporting files for a route that fails validation.

### 4.3 Validation Success

```
=== ROUTE VALIDATION PASSED (attempt N/3) ===

File: {FLOW_NAME}.camel.yaml
  ✓ All component schemes valid
  ✓ All endpoint URIs valid
  ✓ All option names verified against catalog
  ✓ No unknown or misspelled options
  ✓ Route ID present
  ✓ Steps array format (Kaoto compatible)

Proceeding to generate supporting files...
```

### 4.4 Tool Call Failed

```
camel_validate_route call failed — skipping catalog validation.
   Endpoint URIs and option names have NOT been verified against the Camel catalog.
   Run /camel-validate after implementation to catch any errors.
```

Proceed with this warning recorded.

### 4.5 Validation Fails (Final Error)

```
ERROR: Route validation failed

The generated route still has validation errors.

Last errors from MCP camel_validate_route:
[show errors]

This may require manual intervention. Possible causes:
- Component typos not auto-fixed
- Invalid endpoint options or parameters
- TDD contains ambiguous or conflicting requirements
- Component-specific configuration issues

Recommended actions:
1. Review the MCP validation errors carefully
2. Check component documentation via camel_catalog_component_doc
3. Verify endpoint URIs match catalog requirements
4. Review the TDD for clarity and completeness
5. Manually review and fix the generated YAML
```
