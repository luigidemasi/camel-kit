---
title: Testing
weight: 5
---

Generate Citrus integration tests for your routes.

## Running Test Generation

```
/camel-test <flow-name>    # Generate tests for one flow
/camel-test --all          # Generate tests for all flows
```

## Validation Loop

The `/camel-test` command includes an automated validation loop using the json-yaml-validator-maven-plugin:

```bash
./mvnw com.dataliquid.maven:json-yaml-validator-maven-plugin:2.0.0:validate \
  -Dschema.validator.schemaFile=.camel-kit/.cache/citrus/{version}/citrus-testcase.json \
  -Dschema.validator.sourceDirectory=test \
  -Dschema.validator.includes=**/*.camel.it.yaml
```

## Test Scenarios

Based on your flow design, tests are generated for:

| Scenario | When generated |
|----------|----------------|
| Happy Path | Always |
| Error Handling | Always |
| Dead Letter Queue | If DLQ configured |
| Idempotency | If idempotent consumer used |
| Circuit Breaker | If resilience pattern used |
| Filter Conditions | If filter EIP used |
| Split Processing | If split EIP used |

## Test Files

Tests are saved to `test/<flow-name>.camel.it.yaml` following the Camel JBang naming convention, with test data in `test/data/`.

## Running Tests

```bash
# Install Citrus JBang app
jbang app install citrus@citrusframework/citrus

# Run tests (Docker required for Testcontainers)
cd test
citrus run order-ingestion.camel.it.yaml

# Or using Camel test plugin
camel plugin add test
camel test run test/order-ingestion.camel.it.yaml
```
