# YAML Route Structure and Generation (Step 3)

> **Context variables from orchestrator:**
> - `FLOW_NAME`, `ROUTE_DIR`, `ROUTE_FILE`, `CAMEL_VERSION`, `TARGET_MODULE`

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

### 3.2 Structural Rules

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
                 # Map from TDD "Processing Steps" section table
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
# Include ONLY if TDD "Error Handling" section defines global (cross-route) onException handling.
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

    # Error handling strategy from TDD "Error Handling" section
    errorHandler:
      deadLetterChannel:
        deadLetterUri: "[component]:{{dlq.endpoint}}"
        redeliveryPolicy:
          maximumRedeliveries: {{error.max.retries}}
          redeliveryDelay: {{error.retry.delay}}
          backOffMultiplier: {{error.backoff.multiplier}}

    # Source from TDD "Source System" section
    from:
      uri: "[component]:{{source.endpoint}}"

      steps:
        # Processing steps from TDD "Processing Steps" section
        # (unmarshal only if explicitly required — see Rule in Step 3.2)

        # DataMapper transformation (injected by DataMapper guide if applicable)
        - step:
            id: kaoto-datamapper-{id}
            steps:
              - to:
                  id: kaoto-datamapper-xslt-{4hexchars}
                  uri: xslt-saxon:kaoto-datamapper-{id}.xsl
                  # Pass parameters to XSLT if TDD "Processing Steps" section defines parameters
                  parameters:
                    userId: "${header.userId}"
                    customerProfile: "${variable.customerProfile}"
                    tenantId: "${header.tenantId}"

        - validate:
            simple: "[validation expression from TDD]"

        - filter:
            simple: "[filter condition from TDD]"

        # Additional steps from TDD...

        # Sink from TDD "Sink System" section
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
