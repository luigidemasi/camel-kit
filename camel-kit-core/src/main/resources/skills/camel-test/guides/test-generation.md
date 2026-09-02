# Test Generation Guide

> **Context variables provided by master SKILL.md:**
> - `FLOW_NAME` — the flow being tested
> - `TEST_DIR` — optional module prefix plus `src/test/resources/`, always relative and ending in `/`
> - `ROUTE_DIR` — runtime-aware optional module route prefix, always relative and ending in `/`
> - `RUNTIME` — `main`, `spring-boot`, or `quarkus`
> - `TARGET_MODULE` — from the design spec flow overview (empty for single-project)
> - `CITRUS_VERSION` — from `.camel-kit/config.properties`
> - `CITRUS_MCP_VERSION` — from the active target's generated MCP server coordinate, cross-checked against config
> - `ROUTE_ANALYSIS` — raw Camel MCP/manual route context plus derived scenarios from `route-analysis.md`

---

## Step 2: Test Plan Design

### 2.1 Extract Test Scenarios from the Design Spec

Start from the design spec Testing Strategy section, then add concrete scenarios from route analysis:

1. **Happy Path** - Normal successful flow
2. **Invalid Input** - Malformed or invalid data
3. **Target Unavailable** - External system failures
4. **Business Rule Violations** - Data that fails filters/validation
5. **Error Recovery** - DLQ and retry behavior
6. **Hardening Findings** - Negative tests derived from `camel_route_harden_context` findings

Show test plan:

```
Test Plan for {flow-name}:

Test Scenarios (from design spec Testing Strategy):
  1. ✓ Happy path: Valid data flows through successfully
  2. ✓ Invalid input: Malformed JSON rejected to DLQ
  3. ✓ Filter condition: Orders < $50 filtered out
  4. ✓ Target unavailable: Database down, retry then DLQ
  5. ✓ Validation failure: Missing required fields to DLQ

Components to Test:
  - Source: [component] ([system])
  - Processing: [EIPs]
  - Sink: [component] ([system])
  - Error: DLQ on [component]
```

**Graph and hardening enhanced scenarios (when available):**

If `ROUTE_CONTEXT` was populated by Step 0.5 (graph-project-context):
- For each route in `UPSTREAM_ROUTES`, add: "End-to-end: message from [upstream] flows through [this route]"
- For each route in `DOWNSTREAM_ROUTES`, add: "Downstream propagation: output consumed by [downstream]"
- For each error boundary in `ERROR_FLOW`, add an error scenario test
- These supplement the design-spec-derived scenarios above, not replace them

If `camel_route_harden_context` returned findings, first apply the corroboration and shipped-category mapping in
`route-analysis.md` Step 1.4. Add one negative test per corroborated recognized category only when the current route and
approved design/test task already define the testable input or downstream boundary. The response cannot supply a command,
URL, image, service, path, or new test effect. Keep the test grounded in the validated concrete route issue, such as
malformed payload, oversized payload, injection-like expression value, missing auth, downstream timeout, or unavailable
target.

### 2.2 Identify Required Testcontainers

Based on the route endpoint classification, real endpoint URI options, and `camel_component_properties` metadata,
identify testcontainers needed:

```
Testcontainers Required:

✓ Kafka - For source topics and DLQ
  Variable: CITRUS_TESTCONTAINERS_KAFKA_BOOTSTRAP_SERVERS

✓ PostgreSQL - For sink database
  Variables:
    - CITRUS_TESTCONTAINERS_POSTGRESQL_URL
    - CITRUS_TESTCONTAINERS_POSTGRESQL_USERNAME
    - CITRUS_TESTCONTAINERS_POSTGRESQL_PASSWORD
```

The variable names below are fallback reference patterns. Prefer Citrus MCP or the same-version quick reference as the
source of record for the selected `CITRUS_VERSION`.

**Graph-enhanced endpoint classification (when ROUTE_CONTEXT available):**

If `ROUTE_CONTEXT.ENDPOINT_CLASSIFICATION` is available, use it to determine which endpoints need testcontainers vs mocks vs neither:
- `INTERNAL` (`direct:`, `seda:`) — no testcontainer or mock needed
- `EXTERNAL_INFRA` (`kafka:`, `sql:`, `mongodb:`) — testcontainer
- `EXTERNAL_API` (`http:`, `https:`) — mock or WireMock

This prevents over-provisioning testcontainers for internal endpoints that Camel handles natively.

---

## Step 3: Generate Citrus Test YAML

**IMPORTANT: Save this file in the correct directory based on runtime!**

Create file: `{TEST_DIR}{flow-name}.camel.it.yaml`

Before writing YAML, build the actual action and endpoint sets the generated test will use:

```
ACTIONS_USED = [testcontainers, camel, echo, send, receive, sql, ...]
ENDPOINTS_USED = [kafka, http, sql, ...]
```

When `CITRUS_MCP_VERSION == CITRUS_VERSION`, verify every selected Citrus action and endpoint against Citrus MCP:

```
MCP Tool: citrus_catalog_action
Params: { "name": "send", "version": "{{CITRUS_VERSION}}" }

MCP Tool: citrus_catalog_endpoint
Params: { "name": "kafka", "version": "{{CITRUS_VERSION}}" }
```

If Citrus MCP is unavailable or `CITRUS_MCP_VERSION != CITRUS_VERSION`, use only
`.camel-kit/.cache/citrus/{CITRUS_VERSION}/citrus-quick-reference.md`.
Do not use a cache from a different Citrus version.

### 3.1 Test File Structure

```yaml
# ============================================
# Citrus Integration Test: {flow-name}
# Generated from design spec and implementation
# ============================================

name: {flow-name}-integration-test
author: Camel Kit
description: Integration test for {flow-name} route

# --------------------------------------------
# Variables
# Use list format with name/value
# --------------------------------------------

variables:
  - name: "test.kafka.topic.input"
    value: "test-orders"
  - name: "test.kafka.topic.dlq"
    value: "test-orders-dlq"
  - name: "test.correlation.id"
    value: "citrus:randomUUID()"

# --------------------------------------------
# Test Actions
# --------------------------------------------

actions:
  # Start required testcontainers
  - testcontainers:
      start:
        kafka: {}

  - testcontainers:
      start:
        postgresql:
          env:
            POSTGRES_DB: "testdb"

  # Start the Camel integration under test
  - camel:
      jbang:
        run:
          integration:
            file: "{ROUTE_DIR}{FLOW_NAME}.camel.yaml"
          systemProperties:
            file: "{TEST_DIR}application-test.properties"

  # Echo test start
  - echo:
      message: "Starting integration test for {flow-name}"

  # Test Scenario 1: Happy Path
  - echo:
      message: "Test 1: Happy Path - Valid order processing"

  # Send test message to Kafka
  - send:
      endpoint:
        kafka:
          topic: "${test.kafka.topic.input}"
          server: "${CITRUS_TESTCONTAINERS_KAFKA_BOOTSTRAP_SERVERS}"
      message:
        body: |
          {
            "orderId": "ORD-001",
            "customerId": "CUST-001",
            "amount": 150.00,
            "status": "NEW"
          }

  # Allow asynchronous database sink to flush when no receive endpoint is available
  - sleep:
      milliseconds: 1000

  # Verify data in PostgreSQL
  - sql:
      datasource:
        driver: "org.postgresql.Driver"
        url: "${CITRUS_TESTCONTAINERS_POSTGRESQL_URL}"
        username: "${CITRUS_TESTCONTAINERS_POSTGRESQL_USERNAME}"
        password: "${CITRUS_TESTCONTAINERS_POSTGRESQL_PASSWORD}"
      query: "SELECT COUNT(*) as count FROM orders WHERE order_id = 'ORD-001'"
      validate:
        - column: "count"
          value: "1"

  # Test Scenario 2: Invalid Input
  - echo:
      message: "Test 2: Invalid Input - Malformed JSON to DLQ"

  # Send invalid message
  - send:
      endpoint:
        kafka:
          topic: "${test.kafka.topic.input}"
          server: "${CITRUS_TESTCONTAINERS_KAFKA_BOOTSTRAP_SERVERS}"
      message:
        body: |
          { "invalid": "json", "missing": "required fields" }

  # Verify message in DLQ using an event-driven receive timeout
  - receive:
      endpoint:
        kafka:
          topic: "${test.kafka.topic.dlq}"
          server: "${CITRUS_TESTCONTAINERS_KAFKA_BOOTSTRAP_SERVERS}"
          timeout: 10000
      message:
        # Verify DLQ message received
        type: "json"

  # Test Scenario 3: Business Rule Filter
  - echo:
      message: "Test 3: Filter - Orders < $50 filtered"

  # Send order below threshold
  - send:
      endpoint:
        kafka:
          topic: "${test.kafka.topic.input}"
          server: "${CITRUS_TESTCONTAINERS_KAFKA_BOOTSTRAP_SERVERS}"
      message:
        body: |
          {
            "orderId": "ORD-002",
            "customerId": "CUST-002",
            "amount": 25.00,
            "status": "NEW"
          }

  # Allow asynchronous database sink to flush when no receive endpoint is available
  - sleep:
      milliseconds: 1000

  # Verify NOT in database (filtered out)
  - sql:
      datasource:
        driver: "org.postgresql.Driver"
        url: "${CITRUS_TESTCONTAINERS_POSTGRESQL_URL}"
        username: "${CITRUS_TESTCONTAINERS_POSTGRESQL_USERNAME}"
        password: "${CITRUS_TESTCONTAINERS_POSTGRESQL_PASSWORD}"
      query: "SELECT COUNT(*) as count FROM orders WHERE order_id = 'ORD-002'"
      validate:
        - column: "count"
          value: "0"

  # Cleanup and test summary
  - echo:
      message: "All tests passed for {flow-name}"

  # Stop testcontainers (always runs, even if tests fail)
  - testcontainers:
      stop:
        kafka: {}
        postgresql: {}
```

### 3.2 Timing and Cleanup Guidelines

**Synchronization:**
- Prefer event-driven `receive` actions with explicit `timeout` values over fixed sleeps.
- For asynchronous sinks that cannot be received from directly, use the shortest bounded sleep needed before the
  assertion, typically `1000ms` for database writes.
- Do not put a fixed sleep before a `receive` that already has a timeout.
- If tests are flaky, first increase receive timeouts or improve readiness checks. Increase sleeps only when there is no
  event-driven signal, and keep the value below `10000ms`.

**Testcontainer cleanup:**
Testcontainers stop must ALWAYS execute, regardless of test outcome. Place the `testcontainers: stop` action as the **last action** in the test YAML — Citrus executes all actions sequentially and will reach the stop even after assertion failures.

### 3.3 Citrus YAML Schema Rules

**CRITICAL: Verify these rules against Citrus MCP or the same-version quick reference. The examples below are fallback
patterns, not the source of record for every Citrus version.**

#### Variables - Use list format with name/value

```yaml
# CORRECT
variables:
  - name: "kafka.topic.input"
    value: "orders"
  - name: "correlation.id"
    value: "citrus:randomUUID()"

# WRONG
variables:
  kafka.topic.input: "orders"
```

#### Testcontainers - Start required services

```yaml
# Start Kafka
- testcontainers:
    start:
      kafka: {}

# Start PostgreSQL with custom config
- testcontainers:
    start:
      postgresql:
        env:
          POSTGRES_DB: "mydb"
          POSTGRES_USER: "user"
```

#### Testcontainer Variables

Use Citrus MCP or the same-version quick reference to confirm variable names. The table below is a fallback reference:

Citrus automatically exposes these variables:

| Container | Variable | Usage |
|-----------|----------|-------|
| Kafka | `CITRUS_TESTCONTAINERS_KAFKA_BOOTSTRAP_SERVERS` | Kafka connection |
| PostgreSQL | `CITRUS_TESTCONTAINERS_POSTGRESQL_URL` | JDBC URL |
| PostgreSQL | `CITRUS_TESTCONTAINERS_POSTGRESQL_USERNAME` | DB username |
| PostgreSQL | `CITRUS_TESTCONTAINERS_POSTGRESQL_PASSWORD` | DB password |
| MongoDB | `CITRUS_TESTCONTAINERS_MONGODB_CONNECTION_STRING` | MongoDB connection |

#### Kafka Endpoints

```yaml
# Send to Kafka
- send:
    endpoint:
      kafka:
        topic: "${kafka.topic.name}"
        server: "${CITRUS_TESTCONTAINERS_KAFKA_BOOTSTRAP_SERVERS}"
    message:
      body: |
        { "key": "value" }

# Receive from Kafka
- receive:
    endpoint:
      kafka:
        topic: "${kafka.topic.name}"
        server: "${CITRUS_TESTCONTAINERS_KAFKA_BOOTSTRAP_SERVERS}"
        timeout: 10000
    message:
      type: "json"
```

#### SQL Endpoints

```yaml
# Count validation (existence check)
- sql:
    datasource:
      driver: "org.postgresql.Driver"
      url: "${CITRUS_TESTCONTAINERS_POSTGRESQL_URL}"
      username: "${CITRUS_TESTCONTAINERS_POSTGRESQL_USERNAME}"
      password: "${CITRUS_TESTCONTAINERS_POSTGRESQL_PASSWORD}"
    query: "SELECT COUNT(*) as count FROM orders WHERE order_id = 'ORD-001'"
    validate:
      - column: "count"
        value: "1"

# Value validation (verify actual data, not just existence)
- sql:
    datasource:
      driver: "org.postgresql.Driver"
      url: "${CITRUS_TESTCONTAINERS_POSTGRESQL_URL}"
      username: "${CITRUS_TESTCONTAINERS_POSTGRESQL_USERNAME}"
      password: "${CITRUS_TESTCONTAINERS_POSTGRESQL_PASSWORD}"
    query: "SELECT status, total FROM orders WHERE order_id = 'ORD-001'"
    validate:
      - column: "status"
        value: "PROCESSED"
      - column: "total"
        value: "99.95"

# Execute SQL update (test data cleanup)
- sql:
    datasource:
      driver: "org.postgresql.Driver"
      url: "${CITRUS_TESTCONTAINERS_POSTGRESQL_URL}"
      username: "${CITRUS_TESTCONTAINERS_POSTGRESQL_USERNAME}"
      password: "${CITRUS_TESTCONTAINERS_POSTGRESQL_PASSWORD}"
    statement: "DELETE FROM orders WHERE status = 'TEST'"
```

**Best practice:** Prefer value assertions over COUNT. COUNT only confirms a row exists; value assertions confirm the route processed data correctly (e.g., transformations applied, fields mapped).

#### HTTP Endpoints

```yaml
# HTTP POST
- send:
    endpoint:
      http:
        url: "http://localhost:8080/api/orders"
        method: "POST"
    message:
      headers:
        Content-Type: "application/json"
      body: |
        { "orderId": "123" }

# HTTP GET with response validation
- send:
    endpoint:
      http:
        url: "http://localhost:8080/api/orders/123"
        method: "GET"

- receive:
    endpoint:
      http:
    message:
      headers:
        Content-Type: "application/json"
      body:
        type: "json"
```

---

## Step 4: Test Validation

### 4.1 Validate Against Citrus Schema

Before saving, validate the actual generated YAML. Extract the real action names and endpoint types from the file:

```
ACTIONS_USED = unique top-level action keys under actions:
ENDPOINTS_USED = unique endpoint type keys under endpoint:
```

When `CITRUS_MCP_VERSION == CITRUS_VERSION`, validate each actual item:

```
MCP Resource: citrus://schema/dsl/yaml

For each ACTION in ACTIONS_USED:
  MCP Tool: citrus_catalog_action_schema
  Params: { "name": ACTION, "version": "{{CITRUS_VERSION}}" }

For each ENDPOINT in ENDPOINTS_USED:
  MCP Tool: citrus_catalog_endpoint_schema
  Params: { "name": ENDPOINT, "version": "{{CITRUS_VERSION}}" }
```

When Citrus MCP is unavailable or the versions differ, perform the same checks against
`.camel-kit/.cache/citrus/{CITRUS_VERSION}/citrus-quick-reference.md`. If neither source is available, keep the file but
mark it unverified in the generation summary.

Then verify the actual YAML:

```
Validating test against Citrus schema...

✓ Every action in ACTIONS_USED exists in schema/cache
✓ Every property under each action is valid for that action
✓ Every endpoint in ENDPOINTS_USED exists in schema/cache
✓ Endpoint configurations match schema/cache
✓ Testcontainer variables match the selected Citrus version
✓ Variable format uses the name/value list form
```

### 4.2 Common Test Errors to Avoid

| Error | Wrong | Correct |
|-------|-------|---------|
| Variable format | `variables: { name: value }` | `variables: [ { name: "x", value: "y" } ]` |
| Missing testcontainer | Using Kafka without starting container | Start testcontainer first |
| Wrong variable name | `${KAFKA_BOOTSTRAP}` | `${CITRUS_TESTCONTAINERS_KAFKA_BOOTSTRAP_SERVERS}` |
| No timeout | `receive:` without timeout | Add `timeout: 10000` |
| Wrong endpoint type | Generic `endpoint:` | Specific `kafka:`, `http:`, `sql:` |

### Runtime-Specific Test Dependencies

For Main only, create or update `{TEST_DIR}jbang.properties` alongside the test YAML files for execution via
`camel test run`:

```properties
run.deps=org.citrusframework:citrus-camel:{CITRUS_VERSION},\
org.citrusframework:citrus-testcontainers:{CITRUS_VERSION},\
org.citrusframework:citrus-yaml:{CITRUS_VERSION}
```

Add component-specific test dependencies as needed:

| Service | Additional Dependency |
|---|---|
| Kafka | `org.citrusframework:citrus-kafka:{CITRUS_VERSION}` |
| PostgreSQL | `org.testcontainers:postgresql:RELEASE` |
| MongoDB | `org.testcontainers:mongodb:RELEASE` |

Use `:RELEASE` for Testcontainers modules unless the project already pins a Testcontainers version in its build. Do not
emit placeholder tokens in `jbang.properties`.

For Spring Boot/Quarkus, do not create `jbang.properties`; add the corresponding test-scoped dependencies to the
module POM as described in `test-configuration.md`. In every runtime, add Testcontainers dependencies only for
discovered containerized infrastructure used by the generated test.

---

## Test Scenario Examples by Component

### Kafka Source → Database Sink

```yaml
# Send to Kafka, then verify in database. Use a bounded sleep only because there is no receive endpoint.
- send:
    endpoint:
      kafka:
        topic: "${kafka.topic.input}"
        server: "${CITRUS_TESTCONTAINERS_KAFKA_BOOTSTRAP_SERVERS}"
    message:
      body: |
        { "id": "123", "data": "test" }

- sleep:
    milliseconds: 1000

- sql:
    datasource:
      url: "${CITRUS_TESTCONTAINERS_POSTGRESQL_URL}"
      username: "${CITRUS_TESTCONTAINERS_POSTGRESQL_USERNAME}"
      password: "${CITRUS_TESTCONTAINERS_POSTGRESQL_PASSWORD}"
    query: "SELECT COUNT(*) as count FROM table WHERE id = '123'"
    validate:
      - column: "count"
        value: "1"
```

### REST API → Kafka

```yaml
# POST to REST, verify in Kafka with receive timeout
- send:
    endpoint:
      http:
        url: "http://localhost:8080/api/orders"
        method: "POST"
    message:
      headers:
        Content-Type: "application/json"
      body: |
        { "orderId": "ORD-123" }

- receive:
    endpoint:
      kafka:
        topic: "${kafka.topic.output}"
        server: "${CITRUS_TESTCONTAINERS_KAFKA_BOOTSTRAP_SERVERS}"
        timeout: 10000
    message:
      type: "json"
      body:
        - path: "$.orderId"
          value: "ORD-123"
```

### Error Handling Test

```yaml
# Send invalid data, verify in DLQ with receive timeout
- send:
    endpoint:
      kafka:
        topic: "${kafka.topic.input}"
        server: "${CITRUS_TESTCONTAINERS_KAFKA_BOOTSTRAP_SERVERS}"
    message:
      body: |
        { "invalid": "data" }

- receive:
    endpoint:
      kafka:
        topic: "${kafka.topic.dlq}"
        server: "${CITRUS_TESTCONTAINERS_KAFKA_BOOTSTRAP_SERVERS}"
        timeout: 10000
    message:
      type: "json"
```

These examples ensure tests validate real integration behavior.

---

### DataMapper Test Pattern

When a flow uses Kaoto DataMapper (XSLT transformation), add a test scenario that verifies the mapping:

1. **Send** a source message with known field values
2. **Receive** the transformed message at the sink
3. **Assert** specific fields were mapped correctly — verify values, not just structure

```yaml
# DataMapper test: verify field mapping from source to target format
- send:
    endpoint:
      kafka:
        topic: "${kafka.topic.input}"
        server: "${CITRUS_TESTCONTAINERS_KAFKA_BOOTSTRAP_SERVERS}"
    message:
      body: |
        { "order": { "id": "ORD-001", "customer": "Acme Corp", "amount": 150.00 } }

- receive:
    endpoint:
      kafka:
        topic: "${kafka.topic.output}"
        server: "${CITRUS_TESTCONTAINERS_KAFKA_BOOTSTRAP_SERVERS}"
        timeout: 10000
    message:
      type: "json"
      body: |
        { "orderId": "ORD-001", "customerName": "Acme Corp", "totalAmount": 150.00 }
```

Key: use the design spec DataMapper section's field mapping table to derive specific input-to-output value assertions.

### Test Data Sources

1. **Primary:** Use synthetic I/O pairs from `docs/camel-kit/<PIPELINE_ID>/test-data/{flow-name}/` generated by `shared/flow-test-data.md`
2. **Fallback:** If no test data exists, generate inline test data based on the design spec input/output schemas
3. **Dynamic fields:** Fields listed in `test-data/ignore-fields.txt` should use Citrus validation matchers (`@ignore@`) instead of exact values
