# Order Processing Example

This example demonstrates how to use Camel-Kit's Flow-Driven Development workflow to design and implement an order ingestion pipeline.

## Getting Started

Initialize a new Camel-Kit project using the CLI:
```bash
camel-kit init my-integration --ai bob
```

This creates the project structure with AI agent commands and fetches the Camel component catalog.

## Scenario
Customer orders are received on a Kafka topic as JSON messages. Valid orders (amount >= $50) must be persisted to a PostgreSQL database. Invalid or low-value orders should be filtered or sent to a Dead Letter Queue (DLQ).

## Workflow Steps

### 0. Integration Context (Optional)
Define your integration landscape - systems, data formats, and identify all flows:
```
/camel.context
```

**Sample answers for this example:**

| Question | Answer |
|----------|--------|
| Business purpose? | Automate the order fulfillment pipeline by reliably ingesting customer orders and persisting them to the database. |
| Systems? | Order Management System (source), Fulfillment Database (sink) |
| Data flows needed? | Move customer orders from Kafka to PostgreSQL |

### 1. Define and Design the Flow
Define and design the integration flow in the [Flow Definition](.camel-kit/flows/order-ingestion/flow.md).
```
/camel.flow order-ingestion
```

**Sample answers for this example:**

| Section | Question | Answer |
|---------|----------|--------|
| **Business Context** | Intent? | Ensure all customer orders are reliably captured and stored for fulfillment processing. |
| | Source? | Order Management System, JSON messages on Kafka topic |
| | Sink? | Fulfillment Database (PostgreSQL) |
| | Business Rules? | Only orders >= $50 should be processed |
| | Error Scenarios? | Invalid JSON, validation failures, database unavailable |
| **Technical Design** | Source Component? | kafka |
| | Source URI? | kafka:orders |
| | Processing Steps? | 1. Unmarshal JSON, 2. Validate schema, 3. Filter amount >= 50 |
| | Sink Component? | sql |
| | Sink URI? | sql:INSERT INTO orders |
| | Error Handling? | Dead Letter Channel to kafka:orders-dlq |
| **Data Contracts** | Input Schema? | JSON, see schemas/order.json |
| | Output Schema? | Same as input (no transformation) |

### 2. Implementation
Generate the Camel YAML DSL:
```
/camel.implement order-ingestion
```
This creates `order-ingestion.camel.yaml`.

### 3. Validation
Validate the route against the constitution:
```
/camel.validate
```

### 4. Testing
Run integration tests:
```
/camel.test order-ingestion
camel test run test/order-ingestion.camel.it.yaml
```

## Infrastructure
Use the provided `docker-compose.yml` to start Kafka and PostgreSQL:
```bash
docker compose up -d
```

## Files
- `.camel-kit/flows/` - Flow definitions
- `.camel-kit/constitution.md` - Quality principles
- `schemas/` - Data contracts (JSON schemas)
- `test/` - Citrus integration tests
- `order-ingestion.camel.yaml` - Generated Camel route

## Workflow Summary (1 Flow = 1 Route)

```
/camel.context                # (Optional) Define integration landscape
/camel.flow <flow-name>       # Define and design the flow
/camel.implement <flow-name>  # Generate <flow-name>.camel.yaml
```

To add another flow, simply run:
```
/camel.flow order-notification
/camel.implement order-notification
```
