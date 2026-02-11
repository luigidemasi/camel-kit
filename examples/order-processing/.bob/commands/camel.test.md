# /camel.test

You are helping the user design integration tests for Camel routes using the **Citrus Framework** integrated with **Kaoto**. Follow these steps exactly.

The user runs: `/camel.test <route-name>` or `/camel.test --all`

## References

- [Citrus Framework](https://citrusframework.org/) - Integration testing framework
- [Apache Camel Testing](https://camel.apache.org/manual/testing.html) - Camel test documentation
- [Kaoto Visual Designer](https://kaoto.io/docs/manual/03_designer/) - Visual integration designer

---

## Step 1: Load Route and Context

Read these files:
- `.camel-kit/routes/<route-name>.md` - Route specification
- `.camel-kit/context.md` - Systems and connections
- `.camel-kit/output/routes.camel.yaml` - Generated route (if exists)

If `--all`, load all route files from `.camel-kit/routes/`.

Show:

```
Loading route: [route-name]

Source: [component:endpoint]
Processing: [list of EIPs]
Sink: [component:endpoint]
Error Handling: [strategy]

External dependencies detected:
- [list any external services, databases, message brokers]
```

---

## Step 2: Choose Test Approach

Ask:

```
== TEST APPROACH ==

How would you like to design your tests?

1. Visual with Kaoto (Recommended)
   Design tests visually in Kaoto, generates Citrus YAML
   Best for: Complex scenarios, visual debugging

2. YAML DSL
   Write Citrus tests directly in YAML
   Best for: CI/CD pipelines, scripted tests

3. Guided Wizard
   I'll guide you through test scenarios step by step
   Best for: Learning, comprehensive coverage
```

---

## Step 3: Identify Test Scenarios

Based on the route design, identify applicable test scenarios:

```
== TEST SCENARIOS ==

Based on your route design, I recommend these test scenarios:

ALWAYS INCLUDED:
[x] Happy Path
    Send valid message through the complete flow
    Verify: Message arrives at sink with expected transformations

BASED ON YOUR ROUTE:
```

Present checkboxes based on route features:

| Route Feature | Test Scenario |
|--------------|---------------|
| Filter EIP | Filter Pass/Reject - Test messages that pass and fail the filter |
| Choice EIP | Branch Coverage - Test each decision branch |
| Dead Letter Channel | DLQ Routing - Verify failed messages go to DLQ |
| Circuit Breaker | Fallback Behavior - Test circuit open/closed states |
| External HTTP call | Service Unavailable - Test timeout and error responses |
| Database sink | Data Integrity - Verify correct data persisted |
| Transformation | Schema Validation - Verify output matches expected schema |
| Idempotency | Duplicate Handling - Send same message twice |

```
[ ] Error Handling
    Test invalid input and verify error responses

[ ] Dead Letter Queue
    Verify failed messages are routed to DLQ

[ ] Filter Conditions
    Test messages that pass/fail your filter: ${body[amount]} >= 50

[ ] External Service Failure
    Test behavior when [service-name] is unavailable

[ ] Data Transformation
    Verify Kaoto DataMapper output matches schema

Which scenarios do you want? (enter numbers, 'all', or describe custom)
```

---

## Step 4: Configure Test Infrastructure

Based on route components, identify required test infrastructure:

```
== TEST INFRASTRUCTURE ==

Your route uses these components:
- [component] ([source/sink/processor])

I'll configure test infrastructure using Testcontainers:

MESSAGING:
[ ] Kafka (Testcontainers)        - For kafka: endpoints
[ ] ActiveMQ (Testcontainers)     - For activemq: endpoints
[ ] RabbitMQ (Testcontainers)     - For rabbitmq: endpoints

DATABASES:
[ ] PostgreSQL (Testcontainers)   - For jdbc/jpa endpoints
[ ] H2 (In-memory)                - Lightweight alternative
[ ] MongoDB (Testcontainers)      - For mongodb: endpoints

MOCKING:
[ ] WireMock                      - Mock HTTP/REST services
[ ] Citrus HTTP Server            - Built-in HTTP mocking
[ ] Citrus Simulator              - Complex service simulation

Confirm infrastructure? (yes/modify)
```

---

## Step 5: Generate Test Data

Ask about test data:

```
== TEST DATA ==

I need sample data for testing.

Your input format: [format from route]
Your input schema: [schema path or class]

Options:
1. Generate sample data from schema
2. I'll provide sample data
3. Use existing files from .camel-kit/tests/test-data/

Choose option:
```

If generating, create test data files:

```
Generated test data in .camel-kit/tests/test-data/:

[route-name]-valid.json
{
  "orderId": "TEST-001",
  "customerId": "CUST-123",
  "amount": 75.50,
  "currency": "EUR",
  "items": [...]
}

[route-name]-invalid.json
{
  "orderId": "",
  "customerId": null,
  "amount": -10
}

[route-name]-filtered.json
{
  "orderId": "TEST-002",
  "customerId": "CUST-456",
  "amount": 25.00  // Below filter threshold
}

[route-name]-edge-case.json
{
  "orderId": "TEST-003",
  "customerId": "CUST-789",
  "amount": 50.00  // Exactly at threshold
}

Review and modify? (yes/no)
```

---

## Step 6: Define Mock Services

If the route calls external services:

```
== MOCK SERVICES ==

Your route calls these external services:
- http://customer-service/api/customers/{id}

Define mock responses:

SERVICE: customer-service
  Endpoint: GET /api/customers/{id}

  Response for Happy Path:
  Status: 200
  Body: {"id": "{id}", "name": "Test Customer", "tier": "GOLD"}

  Response for Not Found:
  Status: 404
  Body: {"error": "Customer not found"}

  Response for Service Error:
  Status: 500
  Body: {"error": "Internal server error"}

Add more mock scenarios? (yes/no)
```

---

## Step 7: Generate Citrus Test File

Generate the Citrus test at `.camel-kit/tests/[route-name]-test.yaml`:

```yaml
# Citrus Integration Test for: [route-name]
# Generated by camel-kit
#
# Run with: camel test run .camel-kit/tests/[route-name]-test.yaml
# Open in Kaoto for visual editing

name: "[route-name]-integration-test"
description: "Integration tests for [route-name] route"

# Test variables - override via environment or command line
variables:
  kafka.bootstrap.servers: "localhost:9092"
  kafka.topic.input: "orders"
  kafka.topic.dlq: "orders-dlq"
  database.url: "jdbc:postgresql://localhost:5432/testdb"
  database.username: "testuser"
  database.password: "testpass"
  http.mock.port: 8089

# Testcontainers configuration
testcontainers:
  kafka:
    image: "apache/kafka:latest"
    exposedPorts:
      - 9092
    env:
      KAFKA_NODE_ID: 1
      KAFKA_PROCESS_ROLES: broker,controller
      KAFKA_LISTENERS: PLAINTEXT://:9092,CONTROLLER://:9093
      KAFKA_CONTROLLER_QUORUM_VOTERS: 1@localhost:9093

  postgres:
    image: "postgres:16"
    exposedPorts:
      - 5432
    env:
      POSTGRES_DB: testdb
      POSTGRES_USER: testuser
      POSTGRES_PASSWORD: testpass
    initScript: "init-db.sql"

# Mock services configuration
mocks:
  customer-service:
    type: http
    port: "${http.mock.port}"
    endpoints:
      - path: "/api/customers/{id}"
        method: GET
        responses:
          - condition: "${pathParams.id} == 'CUST-123'"
            status: 200
            body: |
              {"id": "CUST-123", "name": "Test Customer", "tier": "GOLD"}
          - condition: "${pathParams.id} == 'CUST-404'"
            status: 404
            body: |
              {"error": "Customer not found"}
          - default: true
            status: 200
            body: |
              {"id": "${pathParams.id}", "name": "Default Customer", "tier": "BRONZE"}

# Test suite
tests:

  # ============================================
  # TEST CASE 1: Happy Path
  # ============================================
  - name: "happy-path"
    description: "Valid message flows through entire route successfully"
    actions:
      - echo:
          message: "Test Case 1: Happy Path"

      # Send message to source
      - send:
          endpoint:
            kafka:
              topic: "${kafka.topic.input}"
              bootstrapServers: "${kafka.bootstrap.servers}"
          message:
            body:
              file: "test-data/[route-name]-valid.json"
            headers:
              correlationId: "test-happy-001"

      # Wait for processing
      - sleep:
          milliseconds: 3000

      # Verify message in database
      - query:
          datasource: "testdb"
          statement: "SELECT * FROM orders WHERE order_id = 'TEST-001'"
          validate:
            - column: "status"
              value: "RECEIVED"
            - column: "customer_id"
              value: "CUST-123"
            - column: "amount"
              value: 75.50

      - echo:
          message: "Happy path test PASSED"

  # ============================================
  # TEST CASE 2: Filter - Message Rejected
  # ============================================
  - name: "filter-rejected"
    description: "Message below threshold is filtered out"
    actions:
      - echo:
          message: "Test Case 2: Filter Rejection"

      # Send message that should be filtered
      - send:
          endpoint:
            kafka:
              topic: "${kafka.topic.input}"
              bootstrapServers: "${kafka.bootstrap.servers}"
          message:
            body:
              file: "test-data/[route-name]-filtered.json"
            headers:
              correlationId: "test-filter-001"

      - sleep:
          milliseconds: 3000

      # Verify message NOT in database
      - query:
          datasource: "testdb"
          statement: "SELECT COUNT(*) as cnt FROM orders WHERE order_id = 'TEST-002'"
          validate:
            - column: "cnt"
              value: 0

      - echo:
          message: "Filter rejection test PASSED"

  # ============================================
  # TEST CASE 3: Dead Letter Queue
  # ============================================
  - name: "dead-letter-queue"
    description: "Invalid message is routed to DLQ"
    actions:
      - echo:
          message: "Test Case 3: Dead Letter Queue"

      # Send invalid message
      - send:
          endpoint:
            kafka:
              topic: "${kafka.topic.input}"
              bootstrapServers: "${kafka.bootstrap.servers}"
          message:
            body:
              file: "test-data/[route-name]-invalid.json"
            headers:
              correlationId: "test-dlq-001"

      # Receive from DLQ
      - receive:
          endpoint:
            kafka:
              topic: "${kafka.topic.dlq}"
              bootstrapServers: "${kafka.bootstrap.servers}"
          timeout: 10000
          selector:
            correlationId: "test-dlq-001"
          validate:
            body:
              jsonPath:
                - expression: "$.orderId"
                  value: ""

      - echo:
          message: "DLQ routing test PASSED"

  # ============================================
  # TEST CASE 4: External Service Failure
  # ============================================
  - name: "service-unavailable"
    description: "Route handles external service failure gracefully"
    actions:
      - echo:
          message: "Test Case 4: Service Unavailable"

      # Configure mock to return error
      - http:
          server: "customer-service"
          receive:
            path: "/api/customers/CUST-ERROR"
            method: GET
          send:
            status: 503
            body: |
              {"error": "Service temporarily unavailable"}

      # Send message that triggers the failing service call
      - send:
          endpoint:
            kafka:
              topic: "${kafka.topic.input}"
              bootstrapServers: "${kafka.bootstrap.servers}"
          message:
            body: |
              {
                "orderId": "TEST-ERROR",
                "customerId": "CUST-ERROR",
                "amount": 100.00,
                "currency": "EUR"
              }

      - sleep:
          milliseconds: 5000

      # Verify circuit breaker fallback or DLQ
      - receive:
          endpoint:
            kafka:
              topic: "${kafka.topic.dlq}"
              bootstrapServers: "${kafka.bootstrap.servers}"
          timeout: 10000

      - echo:
          message: "Service failure handling test PASSED"

# Cleanup actions (always run)
finally:
  - echo:
      message: "Cleaning up test resources..."

  - query:
      datasource: "testdb"
      statement: "DELETE FROM orders WHERE order_id LIKE 'TEST-%'"

  - echo:
      message: "Test suite completed"
```

---

## Step 8: Generate Run Configuration

Create run configuration at `.camel-kit/tests/[route-name]-test-config.yaml`:

```yaml
# Test run configuration
# Run with: camel test run --config [route-name]-test-config.yaml

test:
  file: "[route-name]-test.yaml"

# Override variables for different environments
environments:
  local:
    kafka.bootstrap.servers: "localhost:9092"
    database.url: "jdbc:postgresql://localhost:5432/testdb"

  ci:
    kafka.bootstrap.servers: "${KAFKA_BROKERS}"
    database.url: "${DATABASE_URL}"
    database.username: "${DATABASE_USER}"
    database.password: "${DATABASE_PASSWORD}"

# Reporting
reports:
  - format: junit
    output: ".camel-kit/tests/reports/junit.xml"
  - format: html
    output: ".camel-kit/tests/reports/report.html"
```

---

## Step 9: Kaoto Integration

If the user chose visual approach:

```
== KAOTO VISUAL TESTING ==

Your test file is ready for visual editing in Kaoto.

To open in Kaoto:
1. Open VS Code with Kaoto extension
2. Navigate to .camel-kit/tests/[route-name]-test.yaml
3. Right-click -> "Open with Kaoto"

In Kaoto you can:
- Visually see the test flow
- Add/modify test steps by dragging components
- Configure assertions visually
- Run tests directly from the editor

The test file uses Citrus YAML DSL which is compatible with Kaoto's
visual editor.
```

---

## Step 10: Summary and Next Steps

Show summary:

```
Test suite generated for '[route-name]'

CREATED FILES:
  .camel-kit/tests/[route-name]-test.yaml
  .camel-kit/tests/[route-name]-test-config.yaml
  .camel-kit/tests/test-data/[route-name]-valid.json
  .camel-kit/tests/test-data/[route-name]-invalid.json
  .camel-kit/tests/test-data/[route-name]-filtered.json

TEST SCENARIOS:
  [x] Happy Path
  [x] Filter Conditions
  [x] Dead Letter Queue
  [x] External Service Failure

INFRASTRUCTURE:
  - Kafka (Testcontainers)
  - PostgreSQL (Testcontainers)
  - WireMock (HTTP mocking)

RUN TESTS:

  # Using Camel JBang
  camel test run .camel-kit/tests/[route-name]-test.yaml

  # With specific environment
  camel test run --config .camel-kit/tests/[route-name]-test-config.yaml --env ci

  # Generate HTML report
  camel test run .camel-kit/tests/[route-name]-test.yaml --report html

VISUAL EDITING:
  Open .camel-kit/tests/[route-name]-test.yaml in Kaoto

NEXT STEPS:
  - Review and customize test data
  - Add more test scenarios: /camel.test [route-name] --add-scenario
  - Run the tests locally before pushing
  - Add to CI/CD pipeline
```
