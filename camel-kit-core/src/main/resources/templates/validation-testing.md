# Camel-Kit Validation — Citrus Testing Integration

Guidelines for generating Citrus test files to verify route behavior.

---

## Overview

Camel-kit generates [Citrus](https://citrusframework.org/) test files to verify route behavior. Tests use YAML format and integrate with `camel test` command.

---

## Test File Structure

```
project-root/
├── routes.camel.yaml
└── test/
    ├── order-ingestion.camel.it.yaml
    ├── inventory-lookup.camel.it.yaml
    ├── jbang.properties
    └── data/
        ├── order-valid.json
        ├── order-invalid.json
        └── expected-output.json
```

---

## Generated Test Format

Example for a Kafka → Database route with DLQ error handling:

```yaml
name: order-ingestion-test
description: Test order ingestion route - Kafka to Database

variables:
  kafka.brokers: localhost:9092
  database.url: jdbc:h2:mem:testdb

actions:
  # Start test infrastructure
  - camel:
      infra:
        run:
          service: kafka
          properties:
            topics: orders,orders-dlq

  - camel:
      infra:
        run:
          service: h2
          properties:
            database: testdb

  # Start the Camel integration
  - camel:
      jbang:
        run:
          integration:
            file: "../output/routes.camel.yaml"
          wait:
            for:
              log:
                message: "started and consuming"
            timeout: 30000

  # Happy Path: Valid Order
  - send:
      endpoint:
        uri: kafka:orders
        parameters:
          brokers: ${kafka.brokers}
      message:
        headers:
          kafka.KEY: "order-001"
        body:
          file: test-data/order-valid.json

  - sleep:
      milliseconds: 2000

  - sql:
      datasource: testdb
      statement: SELECT * FROM orders WHERE order_id = 'order-001'
      validate:
        - column: order_id
          value: order-001
        - column: status
          value: PROCESSED

  # Error Path: Invalid Order → DLQ
  - send:
      endpoint:
        uri: kafka:orders
      message:
        body:
          data: '{"invalid": "data"}'

  - sleep:
      milliseconds: 2000

  - receive:
      endpoint:
        uri: kafka:orders-dlq
      timeout: 10000

finally:
  - camel:
      jbang:
        stop:
          integration: order-ingestion
```

---

## Test Scenarios to Generate

| Scenario | What to Test | How |
|----------|--------------|-----|
| Happy Path | Normal processing | Send valid message, verify sink |
| Validation Error | Invalid input | Send invalid, verify error handling |
| Dead Letter | Unrecoverable errors | Cause failure, verify DLQ |
| Idempotency | Duplicate handling | Send twice, verify once processed |
| Circuit Breaker | External failure | Mock failure, verify fallback |
| Transformation | Format conversion | Send input, verify output format |
| Filtering | Filter conditions | Send filtered/non-filtered, verify |
| Splitting | Batch processing | Send batch, verify individual |

---

## Test Data Files

**test-data/order-valid.json:**
```json
{
  "orderId": "order-001",
  "customerId": "cust-123",
  "orderDate": "2024-01-15T10:30:00Z",
  "items": [
    {"productId": "prod-001", "quantity": 2, "unitPrice": 29.99}
  ],
  "totalAmount": 59.98,
  "currency": "USD"
}
```

**test-data/order-invalid.json:**
```json
{
  "invalid": "missing required fields"
}
```

---

## Running Tests

```bash
# Install test plugin (first time)
camel plugin add test

# Run single test
camel test run test/order-ingestion.camel.it.yaml

# Run all tests
camel test run test/

# Export to Maven project for CI/CD
camel export routes.camel.yaml --runtime quarkus --dir target/project
```

---

## Integration with Validation

After `/camel.validate` passes:
```
✅ VALIDATION PASSED

Next steps:
  1. Generate tests: /camel.test --all
  2. Run tests: camel test run test/
  3. Generate YAML: /camel.generate
```
