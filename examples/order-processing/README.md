# Order Processing Integration Tutorial

This tutorial walks you through creating a complete order processing integration using **camel-kit** and **Kaoto**. You will build an integration that:

1. Consumes order messages from a Kafka topic
2. Filters out orders with amounts less than 50 EUR
3. Transforms the message using Kaoto DataMapper for database insertion
4. Persists the order to a PostgreSQL database using JDBC

## Prerequisites

- Docker and Docker Compose
- [Camel JBang](https://camel.apache.org/manual/camel-jbang.html) installed
- [Kaoto VS Code Extension](https://marketplace.visualstudio.com/items?itemName=redhat.vscode-kaoto) (for visual editing)
- camel-kit CLI installed (see installation options below)
- An AI coding assistant (IBM Project Bob, Claude Code, etc.)

### Installing camel-kit CLI

**Using uv (Recommended):**

```bash
# Install persistently
uv tool install camel-kit-cli --from git+https://github.com/luigidemasi/camel-kit.git

# Or run without installing (one-time usage)
uvx --from git+https://github.com/luigidemasi/camel-kit.git camel-kit --help
```

**Using pip:**

```bash
pip install git+https://github.com/luigidemasi/camel-kit.git
```

> **Note:** [uv](https://github.com/astral-sh/uv) is a fast Python package manager. Install it with `curl -LsSf https://astral.sh/uv/install.sh | sh`

## Architecture Overview

```
┌─────────────┐     ┌─────────────────┐     ┌──────────────┐     ┌────────────┐
│   Kafka     │────▶│   Filter        │────▶│   Kaoto      │────▶│ PostgreSQL │
│ orders topic│     │ amount >= 50    │     │  DataMapper  │     │    JDBC    │
└─────────────┘     └─────────────────┘     └──────────────┘     └────────────┘
```

---

## Part 1: Infrastructure Setup

### Step 1.1: Create Docker Compose File

Create a `docker-compose.yml` file with Kafka (using the official [apache/kafka](https://hub.docker.com/r/apache/kafka) image) and PostgreSQL:

```yaml
# docker-compose.yml
services:
  kafka:
    image: apache/kafka:latest
    container_name: kafka
    ports:
      - "9092:9092"
    environment:
      # KRaft mode configuration
      KAFKA_NODE_ID: 1
      KAFKA_PROCESS_ROLES: broker,controller
      KAFKA_LISTENERS: PLAINTEXT://:9092,CONTROLLER://:9093
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
      KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT
      KAFKA_CONTROLLER_QUORUM_VOTERS: 1@localhost:9093
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"
    healthcheck:
      test: /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list || exit 1
      interval: 10s
      timeout: 5s
      retries: 5

  postgres:
    image: postgres:16
    container_name: postgres
    ports:
      - "5432:5432"
    environment:
      - POSTGRES_DB=orders
      - POSTGRES_USER=orderuser
      - POSTGRES_PASSWORD=orderpass123
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./init-db.sql:/docker-entrypoint-initdb.d/init-db.sql
    healthcheck:
      test: pg_isready -U orderuser -d orders
      interval: 10s
      timeout: 5s
      retries: 5

volumes:
  postgres_data:
```

> **Note**: This setup uses PLAINTEXT for simplicity. For production, configure TLS/SSL. See [Apache Kafka Security](https://kafka.apache.org/documentation/#security).

### Step 1.2: Create PostgreSQL Schema

Create the database initialization script `init-db.sql`:

```sql
-- init-db.sql
-- Order table for JDBC persistence

CREATE TABLE IF NOT EXISTS orders (
    id SERIAL PRIMARY KEY,
    order_id VARCHAR(50) UNIQUE NOT NULL,
    customer_id VARCHAR(50) NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    currency VARCHAR(3) DEFAULT 'EUR',
    status VARCHAR(20) DEFAULT 'RECEIVED',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Index for common queries
CREATE INDEX idx_orders_customer ON orders(customer_id);
CREATE INDEX idx_orders_status ON orders(status);

-- Order items table
CREATE TABLE IF NOT EXISTS order_items (
    id SERIAL PRIMARY KEY,
    order_id VARCHAR(50) REFERENCES orders(order_id) ON DELETE CASCADE,
    product_id VARCHAR(50) NOT NULL,
    product_name VARCHAR(200),
    quantity INTEGER NOT NULL,
    unit_price DECIMAL(10, 2) NOT NULL
);
```

### Step 1.3: Start the Infrastructure

```bash
# Start Kafka and PostgreSQL
docker compose up -d

# Wait for services to be healthy (may take 30-60 seconds)
docker compose ps

# Verify Kafka is running
docker exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list

# Create the orders topic
docker exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 \
  --create --topic orders --partitions 3 --replication-factor 1
```

---

## Part 2: Define Message Schemas

### Step 2.1: Order Message Schema (Kafka Input)

Create the JSON Schema for incoming order messages in `schemas/order-message.json`:

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "$id": "https://example.com/order-message.json",
  "title": "OrderMessage",
  "description": "Order message from Kafka topic",
  "type": "object",
  "required": ["orderId", "customerId", "amount", "currency", "items"],
  "properties": {
    "orderId": {
      "type": "string",
      "description": "Unique order identifier"
    },
    "customerId": {
      "type": "string",
      "description": "Customer identifier"
    },
    "amount": {
      "type": "number",
      "description": "Total order amount"
    },
    "currency": {
      "type": "string",
      "enum": ["EUR", "USD", "GBP"],
      "default": "EUR"
    },
    "status": {
      "type": "string",
      "enum": ["PENDING", "CONFIRMED", "SHIPPED", "DELIVERED", "CANCELLED"],
      "default": "PENDING"
    },
    "timestamp": {
      "type": "string",
      "format": "date-time"
    },
    "items": {
      "type": "array",
      "items": {
        "type": "object",
        "required": ["productId", "quantity", "unitPrice"],
        "properties": {
          "productId": {
            "type": "string"
          },
          "productName": {
            "type": "string"
          },
          "quantity": {
            "type": "integer",
            "minimum": 1
          },
          "unitPrice": {
            "type": "number"
          }
        }
      }
    }
  }
}
```

### Step 2.2: Database Record Schema (Database Output)

Create the target schema for the database record in `schemas/order-entity.json`:

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "$id": "https://example.com/order-entity.json",
  "title": "OrderRecord",
  "description": "Order record for JDBC database persistence",
  "type": "object",
  "properties": {
    "orderId": {
      "type": "string"
    },
    "customerId": {
      "type": "string"
    },
    "amount": {
      "type": "number"
    },
    "currency": {
      "type": "string"
    },
    "status": {
      "type": "string"
    },
    "createdAt": {
      "type": "string",
      "format": "date-time"
    }
  }
}
```

---

## Part 3: Initialize camel-kit Project

### Step 3.1: Initialize the Project

```bash
# Create and initialize the project
mkdir order-processing
cd order-processing

camel-kit init --here --ai bob
```

**Expected output:**
```
✓ Camel-Kit initialized for order-processing

📦 Cached 386 Camel 4.17.0 components
Kamelet catalog fetched on-demand when needed

Next steps:
  1. Open order-processing in IBM Project Bob
  2. Run /camel.context to define your integration landscape
  3. Run /camel.route <name> to design routes
```

### Step 3.2: Open in Your AI Coding Assistant

Open the project folder in VS Code with your AI coding assistant (IBM Project Bob, Claude Code, etc.).

---

## Part 4: Define Integration Context with /camel.context

Run the `/camel.context` command in your AI assistant. Answer the questions as follows:

### Question: What is the business purpose of this integration?

**Your answer:**
```
Process incoming customer orders from the e-commerce platform. Orders are received via Kafka, validated and filtered (minimum 50 EUR), then persisted to PostgreSQL for fulfillment processing.
```

### Question: What is the name of the first system?

**Your answer:**
```
E-commerce Kafka
```

### Question: Is E-commerce Kafka a source, sink, or both?

**Your answer:**
```
1. Source (data comes FROM this system)
```

### Question: What protocol or technology connects to E-commerce Kafka?

**Your answer:**
```
Kafka (PLAINTEXT for development)
```

### Question: What authentication is required?

**Your answer:**
```
None (development setup)
```

### Question: Add another system?

**Your answer:**
```
yes
```

### Question: What is the name of the second system?

**Your answer:**
```
Orders Database
```

### Question: Is Orders Database a source, sink, or both?

**Your answer:**
```
2. Sink (data goes TO this system)
```

### Question: What protocol or technology?

**Your answer:**
```
PostgreSQL via JDBC
```

### Question: What authentication is required?

**Your answer:**
```
Username/password
```

### Question: Add another system?

**Your answer:**
```
no
```

### Question: What data format flows through this integration?

**Your answer:**
```
1. JSON
```

### Question: Do you have existing schemas?

**Your answer:**
```
1. Yes, I have schema files
```

### Question: What distinct data flows do you need?

**Your answer:**
```
1. Receive orders from Kafka, filter orders >= 50 EUR, transform for JDBC insert, store in database
```

### Question: Does the route summary look correct?

**Your answer:**
```
yes
```

### Question: Save this context?

**Your answer:**
```
yes
```

---

## Part 5: Design the Order Processing Route with /camel.route

Run `/camel.route order-ingestion` and answer the questions:

### Question: What is the purpose of this route?

**Your answer:**
```
Consume orders from Kafka topic, filter out orders below 50 EUR, transform for database insertion, and persist to PostgreSQL database using JDBC.
```

### SOURCE Section

**Question: Where does data come from?**

**Your answer:**
```
Kafka topic called "orders"
```

**Question: Which approach - Kamelet or Component?**

**Your answer:**
```
2. Component (more control)
```

> **Note:** If you chose Kamelet and the catalog wasn't downloaded, the assistant will prompt you to run `camel-kit catalog fetch`.

**Question: Bootstrap Servers?**

**Your answer:**
```
{{KAFKA_BROKERS}}
```

**Question: Topic name?**

**Your answer:**
```
orders
```

**Question: Consumer Group ID?**

**Your answer:**
```
order-processor
```

### DATA FORMAT Section

**Question: What format is the incoming data?**

**Your answer:**
```
1. JSON
```

**Question: Do you have a schema or data class?**

**Your answer:**
```
1. Yes, I have a schema file
```

**Question: Add schema validation at route entry?**

**Your answer:**
```
yes
```

### PROCESSING Section

**Question: What needs to happen to the data?**

**Your answer:**
```
1. Filter out orders with amount less than 50 EUR
2. Transform the message using Kaoto DataMapper to match the database record structure
3. Set the status to "RECEIVED"
```

**Question: Confirm processing steps?**

**Your answer:**
```
yes
```

### SINK Section

**Question: Where does the processed data go?**

**Your answer:**
```
PostgreSQL database using JDBC component
```

**Question: Which approach?**

**Your answer:**
```
2. Component (more control)
```

### ERROR HANDLING Section

**Question: How should this route handle failures?**

**Your answer:**
```
4. Combination (DLC + Retry)
```

**Question: Where should failed messages go?**

**Your answer:**
```
kafka:orders-dlq
```

**Question: Maximum retry attempts?**

**Your answer:**
```
3
```

### Save the Route

**Question: Save this route?**

**Your answer:**
```
yes
```

---

## Part 6: Configure Kaoto DataMapper

The [Kaoto DataMapper](https://kaoto.io/docs/manual/04_datamapper/) provides a visual way to create data transformations. Unlike AtlasMap, it uses XSLT under the hood and doesn't require a custom runtime component.

> **Source:** Kaoto DataMapper documentation at [kaoto.io/docs/manual/04_datamapper](https://kaoto.io/docs/manual/04_datamapper/)

### Option A: Visual Approach (Kaoto VS Code Extension)

1. **Install Kaoto Extension**
   - Open VS Code Extensions
   - Search for "Kaoto"
   - Install "Kaoto - Integration Designer"

2. **Open the Generated Route**
   - Navigate to `.camel-kit/output/routes.camel.yaml`
   - Right-click → "Open with Kaoto"

3. **Add DataMapper Step**
   - Click on the route canvas after the Filter step
   - Click "+" to add a step
   - Search for "Kaoto DataMapper"
   - Click to add

4. **Configure the Mapping**
   - Click on the DataMapper step
   - In the properties panel:
     - **Source Schema**: Select `schemas/order-message.json`
     - **Target Schema**: Select `schemas/order-entity.json`
   - The visual mapper opens showing source fields on the left, target on the right

5. **Create Field Mappings**
   - Drag `orderId` → `orderId`
   - Drag `customerId` → `customerId`
   - Drag `amount` → `amount`
   - Drag `currency` → `currency`
   - For `status`: Click the target field, use "Set constant" → `RECEIVED`
   - For `createdAt`: Click target field, use "Current timestamp" function

6. **Save the Mapping**
   - Click "Save" in the DataMapper
   - The XSLT mapping is generated and embedded in your route

### Option B: YAML DSL Approach

If you prefer to define the transformation in YAML without the visual editor, add this step to your route:

```yaml
- route:
    id: order-ingestion
    from:
      uri: kafka:orders
      parameters:
        brokers: "{{KAFKA_BROKERS}}"
        groupId: order-processor
      steps:
        # Unmarshal JSON
        - unmarshal:
            json:
              library: jackson

        # Filter orders >= 50 EUR
        - filter:
            simple: "${body[amount]} >= 50"
            steps:
              # Set headers for JDBC parameters
              - setHeader:
                  name: orderId
                  simple: "${body[orderId]}"
              - setHeader:
                  name: customerId
                  simple: "${body[customerId]}"
              - setHeader:
                  name: amount
                  simple: "${body[amount]}"
              - setHeader:
                  name: currency
                  simple: "${body[currency]}"

              # Set SQL INSERT statement as body
              - setBody:
                  constant: "INSERT INTO orders (order_id, customer_id, amount, currency, status) VALUES (:?orderId, :?customerId, :?amount, :?currency, 'RECEIVED')"

              # Execute INSERT using JDBC
              - to:
                  uri: jdbc:ordersDataSource
                  parameters:
                    useHeadersAsParameters: true
```

---

## Part 7: Generate and Run the Integration

### Step 7.1: Validate the Design

Run `/camel.validate` in your AI assistant to check for issues:

**Expected output:**
```
✅ Validation passed!

Routes validated:
  ✓ order-ingestion

Warnings:
  ⚠ Consider adding idempotency for exactly-once processing
```

### Step 7.2: Generate YAML

Run `/camel.generate` to produce the final Camel YAML:

**Expected output:**
```
✅ YAML generated successfully!

Output: .camel-kit/output/routes.camel.yaml

Environment variables needed:
  - KAFKA_BROKERS
  - DATABASE_URL
```

### Step 7.3: Create Environment File

Create a `.env` file with your configuration:

```bash
# .env
KAFKA_BROKERS=localhost:9092
DATABASE_URL=jdbc:postgresql://localhost:5432/orders
DATABASE_USER=orderuser
DATABASE_PASSWORD=orderpass123
```

### Step 7.4: Run the Integration

```bash
# Using Camel JBang
camel run .camel-kit/output/routes.camel.yaml \
  --property-file .env \
  --dep org.apache.camel:camel-kafka \
  --dep org.apache.camel:camel-jdbc \
  --dep org.postgresql:postgresql:42.7.1
```

---

## Part 8: Test the Integration

### Step 8.1: Send a Test Order (Should be Processed)

```bash
# Send an order with amount >= 50 EUR
echo '{"orderId":"ORD-001","customerId":"CUST-123","amount":75.50,"currency":"EUR","status":"PENDING","items":[{"productId":"PROD-1","productName":"Widget","quantity":2,"unitPrice":37.75}]}' | \
  docker exec -i kafka /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server localhost:9092 \
  --topic orders
```

### Step 8.2: Send a Test Order (Should be Filtered Out)

```bash
# Send an order with amount < 50 EUR (will be filtered)
echo '{"orderId":"ORD-002","customerId":"CUST-456","amount":25.00,"currency":"EUR","status":"PENDING","items":[{"productId":"PROD-2","productName":"Small Item","quantity":1,"unitPrice":25.00}]}' | \
  docker exec -i kafka /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server localhost:9092 \
  --topic orders
```

### Step 8.3: Verify Database

```bash
# Check orders in database
docker exec postgres psql -U orderuser -d orders -c "SELECT * FROM orders;"
```

**Expected output:**
```
 id | order_id | customer_id | amount | currency |  status  |         created_at
----+----------+-------------+--------+----------+----------+----------------------------
  1 | ORD-001  | CUST-123    |  75.50 | EUR      | RECEIVED | 2024-01-15 10:30:45.123456
(1 row)
```

Note: `ORD-002` should NOT appear because it was filtered out (amount < 50 EUR).

---

## Part 9: Automated Testing with Citrus

For comprehensive automated testing, use the `/camel.test` command to generate Citrus integration tests that can be visually edited in Kaoto.

### Step 9.1: Understanding the /camel.test Command

The `/camel.test` command generates integration tests using the [Citrus Framework](https://citrusframework.org/), which provides:

- **Testcontainers**: Automatic infrastructure provisioning (Kafka, PostgreSQL)
- **Message Testing**: Send/receive verification for Kafka topics
- **Database Assertions**: SQL queries to validate persisted data
- **Kaoto Integration**: Visual test editing and debugging

### Step 9.2: Generate Test Suite

Run `/camel.test order-ingestion` in your AI assistant:

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

> 1
```

### Step 9.3: Select Test Scenarios

The command analyzes your route and suggests relevant test scenarios:

```
== TEST SCENARIOS ==

Based on your route design, I recommend these test scenarios:

ALWAYS INCLUDED:
[x] Happy Path - Valid order processed successfully

BASED ON YOUR ROUTE:
[x] Filter Conditions - Orders below 50 EUR filtered out
[x] Dead Letter Queue - Invalid orders routed to DLQ
[x] Edge Case - Order exactly at 50 EUR threshold
[ ] External Service Failure - N/A (no external calls)

Which scenarios do you want? (enter numbers, 'all', or describe custom)
> all
```

### Step 9.4: Review Test Data

The command generates test data files in `.camel-kit/tests/test-data/`:

```
Generated test data:

order-valid.json     - Valid order (75.50 EUR) - should pass
order-filtered.json  - Small order (25.00 EUR) - should be filtered
order-large.json     - Large order (299.95 EUR) - should pass

Review and modify? (yes/no)
```

**order-valid.json** (passes filter):
```json
{
  "orderId": "ORD-001",
  "customerId": "CUST-123",
  "amount": 75.50,
  "currency": "EUR",
  "status": "PENDING",
  "items": [
    {"productId": "PROD-1", "productName": "Widget", "quantity": 2, "unitPrice": 37.75}
  ]
}
```

**order-filtered.json** (filtered out - amount < 50):
```json
{
  "orderId": "ORD-002",
  "customerId": "CUST-456",
  "amount": 25.00,
  "currency": "EUR",
  "status": "PENDING",
  "items": [
    {"productId": "PROD-2", "productName": "Small Item", "quantity": 1, "unitPrice": 25.00}
  ]
}
```

### Step 9.5: Review Generated Test File

The command generates `.camel-kit/tests/order-ingestion-test.yaml`:

```yaml
name: "order-ingestion-integration-test"
description: "Integration tests for order-ingestion route"

# Testcontainers automatically provision infrastructure
testcontainers:
  kafka:
    image: "apache/kafka:latest"
    exposedPorts: [9092]
  postgres:
    image: "postgres:16"
    initScript: "../../init-db.sql"

tests:
  # Test 1: Happy Path
  - name: "happy-path"
    description: "Valid order flows through successfully"
    actions:
      - send:
          endpoint:
            kafka:
              topic: "orders"
          message:
            body:
              file: "test-data/order-valid.json"
      - sleep:
          milliseconds: 3000
      - query:
          datasource: "ordersdb"
          statement: "SELECT * FROM orders WHERE order_id = 'ORD-001'"
          validate:
            - column: "status"
              value: "RECEIVED"
            - column: "amount"
              value: 75.50

  # Test 2: Filter Rejection
  - name: "filter-rejected"
    description: "Order below threshold is filtered out"
    actions:
      - send:
          endpoint:
            kafka:
              topic: "orders"
          message:
            body:
              file: "test-data/order-filtered.json"
      - sleep:
          milliseconds: 3000
      - query:
          datasource: "ordersdb"
          statement: "SELECT COUNT(*) as cnt FROM orders WHERE order_id = 'ORD-002'"
          validate:
            - column: "cnt"
              value: 0

  # Test 3: Dead Letter Queue
  - name: "dead-letter-queue"
    description: "Invalid message routed to DLQ"
    actions:
      - send:
          endpoint:
            kafka:
              topic: "orders"
          message:
            body: '{"orderId": "", "amount": "invalid"}'
      - receive:
          endpoint:
            kafka:
              topic: "orders-dlq"
          timeout: 10000

finally:
  - query:
      datasource: "ordersdb"
      statement: "DELETE FROM orders WHERE order_id LIKE 'ORD-%'"
```

### Step 9.6: Run Tests

```bash
# Ensure infrastructure is running
docker compose up -d

# Run integration tests with Camel JBang
camel test run .camel-kit/tests/order-ingestion-test.yaml

# Run with specific environment configuration
camel test run --config .camel-kit/tests/order-ingestion-test-config.yaml --env local

# Generate HTML report
camel test run .camel-kit/tests/order-ingestion-test.yaml --report html
```

**Expected output:**
```
━━━ Test Case 1: Happy Path ━━━
✅ Happy path test PASSED

━━━ Test Case 2: Filter Rejection ━━━
✅ Filter rejection test PASSED - order correctly filtered out

━━━ Test Case 3: Dead Letter Queue ━━━
✅ DLQ test PASSED - invalid message routed to dead letter queue

━━━ Test suite completed ━━━
Tests: 3 passed, 0 failed
```

### Step 9.7: Visual Test Editing with Kaoto

Open the test file in Kaoto for visual editing:

1. Open VS Code with Kaoto extension installed
2. Navigate to `.camel-kit/tests/order-ingestion-test.yaml`
3. Right-click -> "Open with Kaoto"

In Kaoto you can:
- **Visualize** the test flow as a diagram
- **Drag and drop** to add new test steps
- **Configure assertions** visually
- **Run tests** directly from the editor
- **Debug** test failures with visual feedback

### Step 9.8: CI/CD Integration

The test configuration supports multiple environments. For CI/CD, use the `ci` environment:

```yaml
# .camel-kit/tests/order-ingestion-test-config.yaml
environments:
  ci:
    kafka.bootstrap.servers: "${KAFKA_BROKERS}"
    database.url: "${DATABASE_URL}"
    database.username: "${DATABASE_USER}"
    database.password: "${DATABASE_PASSWORD}"
```

**GitHub Actions example:**
```yaml
- name: Run Integration Tests
  env:
    KAFKA_BROKERS: ${{ secrets.KAFKA_BROKERS }}
    DATABASE_URL: ${{ secrets.DATABASE_URL }}
  run: |
    camel test run --config .camel-kit/tests/order-ingestion-test-config.yaml --env ci --report junit

- name: Publish Test Results
  uses: dorny/test-reporter@v1
  with:
    name: Integration Tests
    path: .camel-kit/tests/reports/junit.xml
    reporter: java-junit
```

---

## Part 10: Clean Up

```bash
# Stop the integration (Ctrl+C in the terminal running camel)

# Stop and remove containers
docker-compose down -v
```

---

## Summary

In this tutorial, you learned how to:

1. **Set up infrastructure** with Kafka and PostgreSQL using Docker Compose
2. **Define JSON schemas** for message validation
3. **Initialize a camel-kit project** and configure it for IBM Project Bob
4. **Use /camel.context** to define your integration landscape
5. **Use /camel.route** to design a complete data flow
6. **Configure Kaoto DataMapper** for visual data transformation
7. **Generate and run** the Camel integration with `/camel.generate`
8. **Test** the complete flow manually with sample messages
9. **Create automated tests** using `/camel.test` with Citrus framework and Kaoto integration

## Available Commands Reference

| Command | Description |
|---------|-------------|
| `/camel.init` | Initialize a new camel-kit project |
| `/camel.context` | Define your integration landscape (systems, connections) |
| `/camel.route <name>` | Design a Camel route step by step |
| `/camel.validate` | Validate route designs against best practices |
| `/camel.generate` | Generate Camel YAML from route designs |
| `/camel.test <name>` | Generate Citrus integration tests for routes |

## References

- [Apache Kafka Docker Image](https://hub.docker.com/r/apache/kafka) - Official Kafka Docker image
- [Kaoto DataMapper Documentation](https://kaoto.io/docs/manual/04_datamapper/) - Visual data mapping
- [Kaoto 2.3 Release Notes](https://kaoto.io/blog/2024/12/kaoto-release-2.3.0/) - DataMapper technical preview
- [Apache Camel JDBC Component](https://camel.apache.org/components/latest/jdbc-component.html) - Database persistence
- [Apache Camel Kafka Component](https://camel.apache.org/components/latest/kafka-component.html) - Kafka integration
- [Apache Kafka Security](https://kafka.apache.org/documentation/#security) - Production security configuration
- [Citrus Framework](https://citrusframework.org/) - Integration testing framework
- [Apache Camel Testing](https://camel.apache.org/manual/testing.html) - Camel test documentation
- [Kaoto Visual Designer](https://kaoto.io/docs/manual/03_designer/) - Visual integration and test designer
- [Testcontainers](https://testcontainers.com/) - Container-based test infrastructure
