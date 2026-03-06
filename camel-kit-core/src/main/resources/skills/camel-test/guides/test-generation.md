# Test Generation Guide

> **Context variables provided by master SKILL.md:**
> - `FLOW_NAME` — the flow being tested
> - `TEST_DIR` — resolved test file directory
> - `TEST_DATA_DIR` — resolved test data directory
> - `TARGET_MODULE` — from TDD Section 1 (empty for single-project)

---

## Step 2: Test Plan Design

### 2.1 Extract Test Scenarios from TDD

From TDD Section 10.1 (Test Scenarios), identify:

1. **Happy Path** - Normal successful flow
2. **Invalid Input** - Malformed or invalid data
3. **Target Unavailable** - External system failures
4. **Business Rule Violations** - Data that fails filters/validation
5. **Error Recovery** - DLQ and retry behavior

Show test plan:

```
Test Plan for {flow-name}:

Test Scenarios (from TDD Section 10):
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

---

## Step 2: Generate Citrus Test YAML

**IMPORTANT: Save this file in the correct directory based on runtime!**

Create file: `{TEST_DIR}{flow-name}.camel.it.yaml`

### 2.1 Test File Structure

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

  # Stop testcontainers
  - testcontainers:
      stop:
        kafka: {}
        postgresql: {}
```

### 2.2 Citrus YAML Schema Rules

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
# Execute SQL query with validation
- sql:
    datasource:
      driver: "org.postgresql.Driver"
      url: "${CITRUS_TESTCONTAINERS_POSTGRESQL_URL}"
      username: "${CITRUS_TESTCONTAINERS_POSTGRESQL_USERNAME}"
      password: "${CITRUS_TESTCONTAINERS_POSTGRESQL_PASSWORD}"
    query: "SELECT COUNT(*) as count FROM orders"
    validate:
      - column: "count"
        value: "5"

# Execute SQL update
- sql:
    datasource:
      driver: "org.postgresql.Driver"
      url: "${CITRUS_TESTCONTAINERS_POSTGRESQL_URL}"
      username: "${CITRUS_TESTCONTAINERS_POSTGRESQL_USERNAME}"
      password: "${CITRUS_TESTCONTAINERS_POSTGRESQL_PASSWORD}"
    statement: "DELETE FROM orders WHERE status = 'TEST'"
```

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

## Step 3: Test Validation

### 3.1 Validate Against Citrus Schema

Before saving, verify:

```
Validating test against Citrus schema...

✓ All actions exist in schema
✓ All properties valid for each action
✓ Endpoint configurations match schema
✓ Testcontainer variables correct
✓ Variable format correct (name/value list)
```

### 3.2 Common Test Errors to Avoid

| Error | Wrong | Correct |
|-------|-------|---------|
| Variable format | `variables: { name: value }` | `variables: [ { name: "x", value: "y" } ]` |
| Missing testcontainer | Using Kafka without starting container | Start testcontainer first |
| Wrong variable name | `${KAFKA_BOOTSTRAP}` | `${CITRUS_TESTCONTAINERS_KAFKA_BOOTSTRAP_SERVERS}` |
| No timeout | `receive:` without timeout | Add `timeout: 10000` |
| Wrong endpoint type | Generic `endpoint:` | Specific `kafka:`, `http:`, `sql:` |

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
