# /camel.test

You are helping the user create integration test scenarios for their Camel routes using the Citrus framework with Testcontainers. Follow these steps exactly.

The user runs: `/camel.test <flow-name>` or `/camel.test --all`

---

## Citrus YAML Schema Rules

**CRITICAL: Follow these rules exactly when generating Citrus YAML tests.**

### 1. Variables - Use list format with name/value

```yaml
# WRONG
variables:
  kafka.brokers: "localhost:9092"

# CORRECT
variables:
  - name: "kafka.topic.input"
    value: "orders"
  - name: "kafka.topic.dlq"
    value: "orders-dlq"
```

### 2. Testcontainers - MUST use for external systems

**ALWAYS use Testcontainers for external systems like Kafka, PostgreSQL, MongoDB, etc.**

Citrus automatically exposes connection details as test variables that you can use in your tests.

```yaml
# Start Kafka container
- testcontainers:
    start:
      kafka: {}

# Start PostgreSQL container
- testcontainers:
    start:
      postgresql: {}

# Start MongoDB container
- testcontainers:
    start:
      mongodb: {}
```

**Testcontainers-exposed variables:**

| Container | Variable | Description |
|-----------|----------|-------------|
| Kafka | `CITRUS_TESTCONTAINERS_KAFKA_BOOTSTRAP_SERVERS` | Kafka bootstrap servers |
| PostgreSQL | `CITRUS_TESTCONTAINERS_POSTGRESQL_URL` | JDBC URL |
| PostgreSQL | `CITRUS_TESTCONTAINERS_POSTGRESQL_USERNAME` | Username |
| PostgreSQL | `CITRUS_TESTCONTAINERS_POSTGRESQL_PASSWORD` | Password |
| PostgreSQL | `CITRUS_TESTCONTAINERS_POSTGRESQL_DRIVER_CLASS_NAME` | JDBC driver class |
| MongoDB | `CITRUS_TESTCONTAINERS_MONGODB_URL` | MongoDB connection URL |

### 3. Camel Integration - Use application.test.properties

Create a separate `application.test.properties` that uses testcontainer variables:

```properties
# application.test.properties - Uses Citrus testcontainer variables
camel.component.kafka.brokers=${CITRUS_TESTCONTAINERS_KAFKA_BOOTSTRAP_SERVERS}

# DataSource using testcontainer variables
camel.beans.dataSource=#class:org.apache.commons.dbcp2.BasicDataSource
camel.beans.dataSource.driverClassName=org.postgresql.Driver
camel.beans.dataSource.url=${CITRUS_TESTCONTAINERS_POSTGRESQL_URL}
camel.beans.dataSource.username=${CITRUS_TESTCONTAINERS_POSTGRESQL_USERNAME}
camel.beans.dataSource.password=${CITRUS_TESTCONTAINERS_POSTGRESQL_PASSWORD}

# Topic names (same as production)
kafka.topic.orders=orders
kafka.topic.dlq=orders-dlq
```

### 4. Camel JBang Run - Use integration.file format

```yaml
# Start the Camel route under test
- camel:
    jbang:
      run:
        integration:
          file: "../[flow-name].camel.yaml"
        properties:
          file: "application.test.properties"
```

**Note:** Use `properties.file` instead of `systemProperties` for configuration.

### 5. SQL Actions - Use dataSource (camelCase)

```yaml
# Setup database schema
- sql:
    dataSource: "testcontainers-postgresql"
    statements:
      - statement: "CREATE TABLE IF NOT EXISTS orders (order_id VARCHAR(50) PRIMARY KEY, customer_id VARCHAR(50), amount DECIMAL(10,2))"

# Query and validate
- sql:
    dataSource: "testcontainers-postgresql"
    statements:
      - statement: "SELECT * FROM orders WHERE order_id = 'TEST-001'"
    validate:
      - column: "order_id"
        value: "TEST-001"
      - column: "amount"
        value: "100.00"
```

### 6. Kafka Send/Receive - Use endpoint with variable references

```yaml
# Send message to Kafka
- send:
    endpoint:
      kafka:
        topic: "${kafka.topic.input}"
        bootstrapServers: "${CITRUS_TESTCONTAINERS_KAFKA_BOOTSTRAP_SERVERS}"
    message:
      body:
        data: |
          {"orderId":"TEST-001","customerId":"CUST-123","amount":100.00}
      headers:
        - name: "kafka.KEY"
          value: "TEST-001"

# Receive message from Kafka
- receive:
    endpoint:
      kafka:
        topic: "${kafka.topic.dlq}"
        bootstrapServers: "${CITRUS_TESTCONTAINERS_KAFKA_BOOTSTRAP_SERVERS}"
    timeout: 15000
    message:
      headers:
        - name: "kafka.KEY"
          value: "TEST-001"
```

### 7. Sleep - Allow time for async processing

```yaml
# Wait for async processing
- sleep:
    milliseconds: 3000
```

### 8. Echo - Add test progress messages

```yaml
- echo:
    message: "Test 1: Happy Path - Starting"
```

---

## Step 1: Load Flow

Read these files:
- `.camel-kit/flows/<flow-name>/flow.md` - Flow definition
- `.camel-kit/project.md` - Systems and connections
- `<flow-name>.camel.yaml` - Generated route (if exists)
- `application.properties` - Configuration (to create test version)

If `--all`, load all flow files from `.camel-kit/flows/`.

Show:

```
Loading flow: [flow-name]

Source: [component:endpoint]
Processing: [list of EIPs]
Sink: [component:endpoint]
Error Handling: [strategy]

External services detected (will use Testcontainers):
- Kafka → testcontainers-kafka
- PostgreSQL → testcontainers-postgresql
- MongoDB → testcontainers-mongodb
```

---

## Step 2: Identify Test Scenarios

Based on the flow design, identify applicable test scenarios:

```
== TEST SCENARIOS ==

Based on your flow, I recommend testing:

[x] 1. Happy Path (always included)
      Send valid message → verify it reaches the sink

[ ] 2. Filter Rejection (if flow has filter)
      Send message that fails filter → verify it's dropped

[ ] 3. Error Handling / DLQ (if flow has error handling)
      Send malformed message → verify it goes to DLQ

[ ] 4. Transformation Validation (if flow transforms data)
      Verify output format matches expected schema

[ ] 5. Edge Cases
      Test boundary values, empty messages, etc.

Which scenarios? (enter numbers, 'all', or describe custom)
```

Wait for response before continuing.

---

## Step 3: Create Test Configuration

### 3.1 Generate application.test.properties

Create `test/application.test.properties` that references testcontainer variables:

```properties
# ==============================================
# Test Configuration for [flow-name]
# Uses Citrus Testcontainer variables
# ==============================================

# Kafka Component - uses testcontainer
camel.component.kafka.brokers=${CITRUS_TESTCONTAINERS_KAFKA_BOOTSTRAP_SERVERS}
camel.component.kafka.groupId=[flow-name]-test-consumer

# DataSource - uses testcontainer
camel.beans.dataSource=#class:org.apache.commons.dbcp2.BasicDataSource
camel.beans.dataSource.driverClassName=org.postgresql.Driver
camel.beans.dataSource.url=${CITRUS_TESTCONTAINERS_POSTGRESQL_URL}
camel.beans.dataSource.username=${CITRUS_TESTCONTAINERS_POSTGRESQL_USERNAME}
camel.beans.dataSource.password=${CITRUS_TESTCONTAINERS_POSTGRESQL_PASSWORD}

# Topic names (same as production application.properties)
kafka.topic.orders=orders
kafka.topic.dlq=orders-dlq

# JBang dependencies (same as production)
camel.jbang.dependencies=org.postgresql:postgresql:42.7.3,\
org.apache.commons:commons-dbcp2:2.12.0
```

---

## Step 4: Generate Test Data

Create test data files in `test/data/`:

```
Generated test data:

[flow-name]-valid.json     - Valid message (should pass all validations)
[flow-name]-filtered.json  - Message that fails filter (if applicable)
[flow-name]-invalid.json   - Malformed message (for error handling)

Files saved to: test/data/
```

Example valid message:
```json
{
  "orderId": "TEST-001",
  "customerId": "CUST-123",
  "amount": 100.00,
  "status": "PENDING"
}
```

---

## Step 5: Generate Citrus Test

Generate `test/[flow-name].camel.it.yaml`:

```yaml
# ============================================
# Citrus Integration Test: [flow-name]
# Generated by camel-kit
# ============================================
#
# Run with:
#   cd test && citrus run [flow-name].camel.it.yaml
#
# Prerequisites:
#   - Install Citrus: jbang app install citrus@citrusframework/citrus
#   - Docker running (for Testcontainers)

name: "[flow-name]-integration-test"
description: "Integration tests for [flow-name] flow"

variables:
  - name: "kafka.topic.input"
    value: "orders"
  - name: "kafka.topic.dlq"
    value: "orders-dlq"

actions:

  # ==========================================
  # INFRASTRUCTURE SETUP (Testcontainers)
  # ==========================================

  - echo:
      message: "Starting test infrastructure..."

  # Start Kafka container
  - testcontainers:
      start:
        kafka: {}

  # Start PostgreSQL container
  - testcontainers:
      start:
        postgresql: {}

  # Wait for containers to be ready
  - sleep:
      milliseconds: 5000

  - echo:
      message: "Infrastructure ready. Kafka: ${CITRUS_TESTCONTAINERS_KAFKA_BOOTSTRAP_SERVERS}"

  # ==========================================
  # DATABASE SETUP
  # ==========================================

  - echo:
      message: "Setting up database schema..."

  - sql:
      dataSource: "testcontainers-postgresql"
      statements:
        - statement: |
            CREATE TABLE IF NOT EXISTS orders (
              order_id VARCHAR(50) PRIMARY KEY,
              customer_id VARCHAR(50),
              amount DECIMAL(10,2),
              status VARCHAR(20),
              created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )

  # ==========================================
  # START CAMEL INTEGRATION
  # ==========================================

  - echo:
      message: "Starting Camel integration..."

  - camel:
      jbang:
        run:
          integration:
            file: "../[flow-name].camel.yaml"
          properties:
            file: "application.test.properties"

  # Wait for route to start
  - sleep:
      milliseconds: 5000

  - echo:
      message: "Camel integration started"

  # ==========================================
  # TEST 1: HAPPY PATH
  # ==========================================

  - echo:
      message: "=== TEST 1: Happy Path ==="

  - send:
      endpoint:
        kafka:
          topic: "${kafka.topic.input}"
          bootstrapServers: "${CITRUS_TESTCONTAINERS_KAFKA_BOOTSTRAP_SERVERS}"
      message:
        body:
          data: |
            {"orderId":"TEST-001","customerId":"CUST-123","amount":100.00,"status":"PENDING"}
        headers:
          - name: "kafka.KEY"
            value: "TEST-001"

  # Wait for processing
  - sleep:
      milliseconds: 3000

  # Verify record in database
  - sql:
      dataSource: "testcontainers-postgresql"
      statements:
        - statement: "SELECT * FROM orders WHERE order_id = 'TEST-001'"
      validate:
        - column: "order_id"
          value: "TEST-001"
        - column: "customer_id"
          value: "CUST-123"

  - echo:
      message: "TEST 1: Happy Path - PASSED"

  # ==========================================
  # TEST 2: FILTER REJECTION (if applicable)
  # ==========================================

  - echo:
      message: "=== TEST 2: Filter Rejection ==="

  - send:
      endpoint:
        kafka:
          topic: "${kafka.topic.input}"
          bootstrapServers: "${CITRUS_TESTCONTAINERS_KAFKA_BOOTSTRAP_SERVERS}"
      message:
        body:
          data: |
            {"orderId":"TEST-002","customerId":"CUST-456","amount":25.00,"status":"PENDING"}
        headers:
          - name: "kafka.KEY"
            value: "TEST-002"

  # Wait for processing
  - sleep:
      milliseconds: 3000

  # Verify record NOT in database (filtered out)
  - sql:
      dataSource: "testcontainers-postgresql"
      statements:
        - statement: "SELECT COUNT(*) as cnt FROM orders WHERE order_id = 'TEST-002'"
      validate:
        - column: "cnt"
          value: "0"

  - echo:
      message: "TEST 2: Filter Rejection - PASSED"

  # ==========================================
  # TEST 3: ERROR HANDLING / DLQ
  # ==========================================

  - echo:
      message: "=== TEST 3: Error Handling / DLQ ==="

  - send:
      endpoint:
        kafka:
          topic: "${kafka.topic.input}"
          bootstrapServers: "${CITRUS_TESTCONTAINERS_KAFKA_BOOTSTRAP_SERVERS}"
      message:
        body:
          data: |
            {"orderId":"","amount":"not-a-number","invalid":true}
        headers:
          - name: "kafka.KEY"
            value: "TEST-INVALID"

  # Verify message arrives in DLQ
  - receive:
      endpoint:
        kafka:
          topic: "${kafka.topic.dlq}"
          bootstrapServers: "${CITRUS_TESTCONTAINERS_KAFKA_BOOTSTRAP_SERVERS}"
      timeout: 15000
      message:
        headers:
          - name: "kafka.KEY"
            value: "TEST-INVALID"

  - echo:
      message: "TEST 3: Error Handling / DLQ - PASSED"

  # ==========================================
  # CLEANUP
  # ==========================================

  - echo:
      message: "Cleaning up test data..."

  - sql:
      dataSource: "testcontainers-postgresql"
      statements:
        - statement: "DELETE FROM orders WHERE order_id LIKE 'TEST-%'"

  - echo:
      message: "============================================"
  - echo:
      message: "ALL TESTS PASSED"
  - echo:
      message: "============================================"
```

---

## Step 6: Create jbang.properties

Create `test/jbang.properties` with ALL Citrus dependencies:

```properties
# ============================================
# Citrus Test Dependencies
# Generated by camel-kit
# ============================================
#
# All dependencies in single run.deps property
# Citrus version should match your Camel version compatibility

run.deps=org.citrusframework:citrus-core:4.4.0,\
org.citrusframework:citrus-camel:4.4.0,\
org.citrusframework:citrus-kafka:4.4.0,\
org.citrusframework:citrus-testcontainers:4.4.0,\
org.citrusframework:citrus-sql:4.4.0,\
org.citrusframework:citrus-yaml:4.4.0,\
org.citrusframework:citrus-validation-json:4.4.0,\
org.postgresql:postgresql:42.7.3,\
org.apache.commons:commons-dbcp2:2.12.0,\
org.testcontainers:testcontainers:1.20.4,\
org.testcontainers:postgresql:1.20.4,\
org.testcontainers:kafka:1.20.4
```

---

## Step 7: Summary

Show summary:

```
============================================
TEST SUITE GENERATED: [flow-name]
============================================

INFRASTRUCTURE (via Testcontainers):
  - Kafka (started automatically)
  - PostgreSQL (started automatically)

CREATED FILES:
  test/[flow-name].camel.it.yaml      Citrus test suite
  test/application.test.properties    Test configuration (uses testcontainers)
  test/jbang.properties               Citrus dependencies
  test/data/[flow-name]-valid.json
  test/data/[flow-name]-filtered.json
  test/data/[flow-name]-invalid.json

TEST SCENARIOS:
  [x] Happy Path
  [x] Filter Rejection
  [x] Error Handling / DLQ

HOW TO RUN:

  # 1. Install Citrus JBang app (one-time)
  jbang app install citrus@citrusframework/citrus

  # 2. Make sure Docker is running (for Testcontainers)
  docker info

  # 3. Run the tests
  cd test
  citrus run [flow-name].camel.it.yaml

ALTERNATIVE - Using Camel Test Plugin:

  # Install test plugin (one-time)
  camel plugin add test

  # Run tests
  camel test run test/[flow-name].camel.it.yaml

DOCUMENTATION:
  - Citrus Framework: https://citrusframework.org/
  - Citrus Testcontainers: https://citrusframework.org/citrus/reference/html/#testcontainers
  - Camel Test Plugin: https://camel.apache.org/manual/camel-jbang-test.html
  - Testcontainers: https://testcontainers.com/
```

---

## Testcontainers Reference

### Supported Containers

| Container | Citrus Module | Variables Exposed |
|-----------|---------------|-------------------|
| Kafka | `citrus-testcontainers` | `CITRUS_TESTCONTAINERS_KAFKA_BOOTSTRAP_SERVERS` |
| PostgreSQL | `citrus-testcontainers` | `CITRUS_TESTCONTAINERS_POSTGRESQL_URL`, `_USERNAME`, `_PASSWORD` |
| MongoDB | `citrus-testcontainers` | `CITRUS_TESTCONTAINERS_MONGODB_URL` |
| Redis | `citrus-testcontainers` | `CITRUS_TESTCONTAINERS_REDIS_URL` |
| LocalStack (AWS) | `citrus-testcontainers` | Various AWS endpoint URLs |

### DataSource Reference

When using `sql` actions with testcontainers, use `dataSource: "testcontainers-postgresql"` - Citrus auto-configures this.

### Troubleshooting

**Container startup fails:**
- Ensure Docker is running: `docker info`
- Check Docker resources (memory/CPU)
- Check container logs in Docker Desktop

**Connection refused:**
- Increase sleep time after container start
- Verify testcontainer variables are set correctly

**Test timeout:**
- Increase timeout values in receive actions
- Check if Camel route started successfully
