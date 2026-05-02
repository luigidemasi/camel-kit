# Test Generation Guide

> **Context variables provided by master SKILL.md:**
> - `FLOW_NAME` — the flow being tested
> - `TEST_DIR` — resolved test file directory
> - `TARGET_MODULE` — from the TDD "Overview" section (empty for single-project)

---

## Step 2: Test Plan Design

### 2.1 Extract Test Scenarios from TDD

From the TDD "Testing Strategy" section, identify:

1. **Happy Path** - Normal successful flow
2. **Invalid Input** - Malformed or invalid data
3. **Target Unavailable** - External system failures
4. **Business Rule Violations** - Data that fails filters/validation
5. **Error Recovery** - DLQ and retry behavior

Show test plan:

```
Test Plan for {flow-name}:

Test Scenarios (from TDD "Testing Strategy"):
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

**Graph-enhanced scenarios (when ROUTE_CONTEXT available):**

If `ROUTE_CONTEXT` was populated by Step 0.5 (graph-project-context):
- For each route in `UPSTREAM_ROUTES`, add: "End-to-end: message from [upstream] flows through [this route]"
- For each route in `DOWNSTREAM_ROUTES`, add: "Downstream propagation: output consumed by [downstream]"
- For each error boundary in `ERROR_FLOW`, add an error scenario test
- These supplement the TDD-derived scenarios above, not replace them

### 2.2 Identify Required Testcontainers

Based on components in the flow, identify testcontainers needed:

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

### 3.1 Test File Structure

```yaml
# ============================================
# Citrus Integration Test: {flow-name}
# Generated from TDD and implementation
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

  # Wait for containers to be ready
  - sleep:
      milliseconds: 5000

  # Start the Camel integration under test
  - camel:
      jbang:
        run:
          integration:
            file: "{ROUTE_DIR}/{FLOW_NAME}.camel.yaml"
          systemProperties:
            file: "{TEST_DIR}/application-test.properties"

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

  # Wait for processing
  - sleep:
      milliseconds: 2000

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

  # Wait for error handling
  - sleep:
      milliseconds: 2000

  # Verify message in DLQ
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

  # Wait for processing
  - sleep:
      milliseconds: 2000

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

**Sleep calibration:**
- Use `2000ms` as default wait between send and verify
- For Kafka consumer startup, use `3000ms` on first message
- For database writes, `1000ms` is usually sufficient
- If tests are flaky, increase sleep incrementally (not beyond `10000ms`)
- Prefer `receive` with `timeout` over `sleep` when the framework supports it

**Testcontainer cleanup:**
Testcontainers stop must ALWAYS execute, regardless of test outcome. Place the `testcontainers: stop` action as the **last action** in the test YAML — Citrus executes all actions sequentially and will reach the stop even after assertion failures.

### 3.3 Citrus YAML Schema Rules

**CRITICAL: Follow these rules exactly:**

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

Before saving, verify:

```
Validating test against Citrus schema...

✓ All actions exist in schema
✓ All properties valid for each action
✓ Endpoint configurations match schema
✓ Testcontainer variables correct
✓ Variable format correct (name/value list)
```

### 4.2 Common Test Errors to Avoid

| Error | Wrong | Correct |
|-------|-------|---------|
| Variable format | `variables: { name: value }` | `variables: [ { name: "x", value: "y" } ]` |
| Missing testcontainer | Using Kafka without starting container | Start testcontainer first |
| Wrong variable name | `${KAFKA_BOOTSTRAP}` | `${CITRUS_TESTCONTAINERS_KAFKA_BOOTSTRAP_SERVERS}` |
| No timeout | `receive:` without timeout | Add `timeout: 10000` |
| Wrong endpoint type | Generic `endpoint:` | Specific `kafka:`, `http:`, `sql:` |

### JBang Test Dependencies

For test execution via `camel test run`, create or update `test/jbang.properties` alongside the test YAML files:

```properties
run.deps=org.citrusframework:citrus-camel:RELEASE,\
org.citrusframework:citrus-testcontainers:RELEASE,\
org.citrusframework:citrus-yaml:RELEASE
```

Add component-specific test dependencies as needed:

| Service | Additional Dependency |
|---|---|
| Kafka | `org.citrusframework:citrus-kafka:RELEASE` |
| PostgreSQL | `org.testcontainers:postgresql:RELEASE` |
| MongoDB | `org.testcontainers:mongodb:RELEASE` |

---

## Test Scenario Examples by Component

### Kafka Source → Database Sink

```yaml
# Send to Kafka, verify in database
- send:
    endpoint:
      kafka:
        topic: "${kafka.topic.input}"
        server: "${CITRUS_TESTCONTAINERS_KAFKA_BOOTSTRAP_SERVERS}"
    message:
      body: |
        { "id": "123", "data": "test" }

- sleep:
    milliseconds: 2000

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
# POST to REST, verify in Kafka
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

- sleep:
    milliseconds: 2000

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
# Send invalid data, verify in DLQ
- send:
    endpoint:
      kafka:
        topic: "${kafka.topic.input}"
        server: "${CITRUS_TESTCONTAINERS_KAFKA_BOOTSTRAP_SERVERS}"
    message:
      body: |
        { "invalid": "data" }

- sleep:
    milliseconds: 2000

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

- sleep:
    milliseconds: 3000

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

Key: use the TDD "DataMapper" section's field mapping table to derive specific input→output value assertions.

### Test Data Sources

1. **Primary:** Use synthetic I/O pairs from `docs/flows/{FLOW_NAME}/test-data/` generated by `shared/flow-test-data.md`
2. **Fallback:** If no test data exists, generate inline test data based on the TDD input/output schemas
3. **Dynamic fields:** Fields listed in `test-data/ignore-fields.txt` should use Citrus validation matchers (`@ignore@`) instead of exact values
