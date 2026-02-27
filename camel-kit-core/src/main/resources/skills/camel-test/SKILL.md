---
name: camel-test
description: Create integration tests when user wants to test routes, generate test cases, set up Citrus tests, configure Testcontainers, verify behavior, or write test suites
user-invocable: true
metadata:
  version: "1.0.0"
  author: "camel-kit"
  category: "integration"
  license: "Apache-2.0"
---

# Camel Test - Integration Test Generation

You are acting as a **Test Engineer** creating comprehensive integration tests for Camel routes using the Citrus framework.

## Role and Approach

- Generate realistic integration tests that validate end-to-end flow behavior
- Use Testcontainers for external system dependencies
- Test both happy path and error scenarios
- Follow Citrus YAML schema precisely
- Ensure tests are repeatable and isolated

## Output File Locations

**CRITICAL: All test files go in PROJECT ROOT test/ directory, NOT in .camel-kit folder!**

Generated test files and their locations:
- `test/{flow-name}.citrus.yaml` → **Project root test/ directory**
- `test/application-test.properties` → **Project root test/ directory**
- `test/data/` → **Project root test/data/ directory** (test data files)
- `run-tests.sh` → **Project root** (make executable with chmod +x)

The `.camel-kit/` folder is ONLY for internal metadata, NOT for test files!

## Parameters

This skill can test a specific flow or all flows:

```
/camel-test <flow-name>   # Generate tests for specific flow
/camel-test --all        # Generate tests for all flows
```

Example: `/camel-test order-to-warehouse`

## Context Loading

**ALWAYS read at the start:**
1. `.camel-kit/business-requirements.md` - Business context (if exists)
2. `.camel-kit/flows/{flow-name}/{flow-name}.tdd.md` - Technical specification (REQUIRED)
3. `.camel-kit/constitution.md` - Best practices (REQUIRED)
4. `.camel-kit/config.yaml` - Configuration (if exists)
5. `{flow-name}.camel.yaml` - Implementation to test (REQUIRED)

**CRITICAL - Citrus Schema Reference:**
- **MUST READ FIRST:** `.camel-kit/.cache/citrus/{version}/citrus-quick-reference.md`
- This contains all valid actions, endpoints, and testcontainer configurations
- Never generate Citrus YAML without consulting this reference

**Error conditions:**
- If TDD not found: ERROR "Run /camel-flow {flow-name} first"
- If implementation not found: ERROR "Run /camel-implement {flow-name} first"
- If Citrus quick reference not found: WARN "Citrus schema not cached, using standard patterns"

---

## MCP Server Configuration (Recommended)

The Camel MCP server provides route analysis capabilities:
- **Route Context** - Extract components and EIPs from routes automatically
- **Component Documentation** - Get component test patterns
- **Route Understanding** - Analyze route structure for test generation

Always attempt MCP tool calls directly. If a call fails (tool not found, network error), fall back to manual analysis from TDD and route files.

**To enable MCP server**, add to `.mcp.json`:
```json
{
  "mcpServers": {
    "camel": {
      "command": "jbang",
      "args": [
        "-Dquarkus.log.level=WARN",
        "org.apache.camel:camel-jbang-mcp:{{CAMEL_VERSION}}:runner"
      ]
    }
  }
}
```

---

## Step 0: Load Citrus Quick Reference (MANDATORY)

**Before generating any test, read:**

```
.camel-kit/.cache/citrus/{version}/citrus-quick-reference.md
```

This file contains:
- All available test actions with properties and types
- All endpoint types (kafka, http, sql, etc.) with configuration
- Testcontainer definitions with exposed variables
- Valid YAML structure and syntax

**Validate against reference:**
- All action names exist in quick reference
- All properties are valid for each action
- All endpoint configurations match schema
- All testcontainer variable names are correct

---

## Step 1: Analyze Route with MCP

### 1.1 Extract Route Context

```
Analyzing route structure with MCP...

MCP Tool: camel_route_context
Params: {
  "route": "[route-yaml-content]",
  "version": "{{CAMEL_VERSION}}"
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
Params: { "name": "kafka", "version": "{{VERSION}}" }

Get testing recommendations for kafka component.
```

**If tool call fails (fallback):**

```
MCP tool call failed. Using TDD and manual route analysis.
Proceeding to Step 2...
```

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

### 1.2 Identify Required Testcontainers

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

**IMPORTANT: Save this file in the test/ directory in PROJECT ROOT, NOT in .camel-kit/**

Create file: `test/{flow-name}.citrus.yaml` (in project root test/ directory)

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

## Step 4: Generate Test Configuration

### 4.1 Test Application Properties

**IMPORTANT: Save this file in the test/ directory in PROJECT ROOT, NOT in .camel-kit/**

Create file: `test/application-test.properties` (in project root test/ directory)

```properties
# ============================================
# Test Configuration for {flow-name}
# ============================================

# Camel configuration for testing
camel.springboot.name={flow-name}-test

# Override with test-specific values
kafka.topic.input=test-orders
kafka.topic.dlq=test-orders-dlq

# Use testcontainer-provided values
camel.component.kafka.brokers=${CITRUS_TESTCONTAINERS_KAFKA_BOOTSTRAP_SERVERS}
camel.beans.dataSource.url=${CITRUS_TESTCONTAINERS_POSTGRESQL_URL}
camel.beans.dataSource.username=${CITRUS_TESTCONTAINERS_POSTGRESQL_USERNAME}
camel.beans.dataSource.password=${CITRUS_TESTCONTAINERS_POSTGRESQL_PASSWORD}

# Test-specific settings
camel.component.kafka.autoOffsetReset=earliest
```

### 4.2 Test Dependencies

Document additional test dependencies:

```xml
<!-- Test Dependencies -->
<dependency>
  <groupId>org.citrusframework</groupId>
  <artifactId>citrus-testcontainers</artifactId>
  <scope>test</scope>
</dependency>

<dependency>
  <groupId>org.testcontainers</groupId>
  <artifactId>kafka</artifactId>
  <scope>test</scope>
</dependency>

<dependency>
  <groupId>org.testcontainers</groupId>
  <artifactId>postgresql</artifactId>
  <scope>test</scope>
</dependency>
```

---

## Step 5: Generate Test Runner Script

**IMPORTANT: Save this file in the PROJECT ROOT directory, NOT in .camel-kit/**

Create file: `run-tests.sh` (in project root, make it executable with chmod +x)

```bash
#!/bin/bash
# ============================================
# Test Runner for {flow-name}
# ============================================

set -e

echo "Running integration tests for {flow-name}..."

# Ensure Docker is running (required for Testcontainers)
if ! docker info > /dev/null 2>&1; then
  echo "❌ Docker is not running. Testcontainers requires Docker."
  exit 1
fi

echo "✓ Docker is running"

# Run Citrus tests
echo "Starting Citrus tests..."

citrus run tests/{flow-name}.citrus.yaml

# Or using Maven:
# ./mvnw test -Dtest={flow-name}IntegrationTest

echo "✅ All tests passed"
```

Make executable:
```bash
chmod +x run-tests.sh
```

---

## Test Generation Summary

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
TESTS GENERATED: {flow-name}
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Test File: tests/{flow-name}.citrus.yaml

Test Scenarios:
  ✓ Happy path: Valid order processing
  ✓ Invalid input: Malformed data to DLQ
  ✓ Business rule: Filter orders < $50
  ✓ Error handling: DLQ on validation failure
  ✓ Target unavailable: Retry and DLQ

Testcontainers:
  - Kafka (for source and DLQ)
  - PostgreSQL (for sink database)

Supporting Files:
  ✓ tests/application-test.properties
  ✓ run-tests.sh

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

---

## Next Steps

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
RUNNING THE TESTS
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. Ensure Docker is running:

   docker --version
   docker info

2. Run the tests:

   ./run-tests.sh

   Or manually:
   citrus run tests/{flow-name}.citrus.yaml

3. Review test results:

   Tests will output success/failure for each scenario
   Testcontainers will automatically clean up after tests

4. Iterate on failures:

   - Review test logs
   - Verify Camel route implementation
   - Check TDD for expected behavior
   - Update tests or implementation as needed

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Integration test workflow complete!

Next: Run tests and verify all scenarios pass before production deployment.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

---

## Error Handling

### Missing TDD

```
❌ ERROR: Technical Design Document not found

File: .camel-kit/flows/{flow-name}/{flow-name}.tdd.md

Tests require the TDD to understand:
- Expected behavior
- Test scenarios
- Data contracts

Run: /camel-flow {flow-name}
```

### Missing Implementation

```
❌ ERROR: Camel route not found

File: {flow-name}.camel.yaml

Tests require the implementation to exist first.

Run: /camel-implement {flow-name}
```

### Missing Citrus Reference

```
⚠️ WARNING: Citrus quick reference not found

File: .camel-kit/.cache/citrus/{version}/citrus-quick-reference.md

Proceeding with standard Citrus patterns.
Generated tests may require manual validation.

To cache Citrus schemas:
  [Provide download/cache instructions]
```

### Docker Not Running

```
❌ ERROR: Docker not running

Testcontainers requires Docker to be running.

Start Docker and try again:
  - Docker Desktop (Mac/Windows)
  - sudo systemctl start docker (Linux)
```

---

## Tips for Effective Integration Tests

1. **Test realistic scenarios** - Use actual data formats from TDD
2. **Test error paths** - Don't just test happy path
3. **Use testcontainers** - Never mock external systems
4. **Keep tests isolated** - Each test should be independent
5. **Verify side effects** - Check database, DLQ, logs
6. **Use proper timeouts** - Give async operations time to complete
7. **Clean up data** - Reset state between tests
8. **Document assumptions** - What data, what state, what behavior
9. **Run tests frequently** - Catch regressions early
10. **Test constitution compliance** - Verify error handling, logging, etc.

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
