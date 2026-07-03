# Order Processing Example

This example demonstrates how to use Camel-Kit's workflow to design and implement an order ingestion pipeline.

## Getting Started

Initialize a new Camel-Kit project using the CLI:
```bash
# Choose your AI assistant
camel-kit init my-integration --ai bob        # Bob
camel-kit init my-integration --ai gemini     # Gemini CLI
camel-kit init my-integration --ai claude     # Claude Code
camel-kit init my-integration --ai qwen       # Qwen Code
camel-kit init my-integration --ai opencode   # OpenCode
```

You can also override configuration at init time:
```bash
# With custom Camel version
camel-kit init my-integration --ai claude -p "camel.main.version=4.18.3"

# With custom config file
camel-kit init my-integration --ai claude -c team-config.properties
```

This creates the project structure with AI agent configuration and skills.

## Scenario

Customer orders are received on a Kafka topic as JSON messages. Valid orders (amount >= $50) must be persisted to a PostgreSQL database. Invalid or low-value orders should be filtered or sent to a Dead Letter Queue (DLQ).

## Workflow Steps

### 1. Design the Integration

Start the Camel-Kit workflow:
```
/camel-start
```

For a new integration, `/camel-start` routes to the interactive design session. The assistant asks questions one at a time about:
- Business purpose (automate order fulfillment)
- Systems involved (Kafka, PostgreSQL)
- Data flows (orders from Kafka topic to database)
- Business rules (only orders >= $50)
- Error handling (DLQ with retries)

**Result:** Design artifacts saved under `docs/camel-kit/<PIPELINE_ID>/`

### 2. Plan the Implementation

After approving the design, the plan is created automatically:
```
/camel-plan
```

**Result:** Implementation plan with task decomposition

### 3. Execute

After approving the plan, execution runs automatically:
```
/camel-execute
```

The assistant implements, validates, tests, and verifies all flows.

**Generated files:**
- `order-ingestion.camel.yaml` - Camel route (Kaoto compatible)
- `application.properties` - Component & bean configuration
- `docker-compose.yaml` - External services (Kafka, PostgreSQL)
- Citrus test files in `test/`
- Verification report

## Infrastructure

Use the provided `docker-compose.yaml` to start Kafka and PostgreSQL:
```bash
docker compose up -d
```

## Project Structure

```
order-processing/
├── order-ingestion.camel.yaml     # Generated Camel route
├── application.properties         # Component config & dependencies
├── docker-compose.yaml            # External services
├── run.sh                         # Run script
├── schemas/                       # Data contracts (JSON schemas)
│   └── order.json
├── test/                          # Citrus integration tests
│   ├── order-ingestion.camel.it.yaml
│   ├── application.test.properties
│   ├── jbang.properties
│   └── data/
└── .camel-kit/
    └── config.properties
└── docs/
    └── camel-kit/
        └── 001-order-processing/
            ├── design-spec.md
            ├── implementation-plan.md
            ├── execution-report.md
            └── validation-report.md
```

## Workflow Summary

```
/camel-start               # Route to the right Camel-Kit workflow
/camel-plan                # Create implementation plan (auto after design)
/camel-execute             # Implement, test, and run internal verification
/camel-validate            # Final quality gate (auto after execution)
/camel-debug               # Standalone troubleshooting if something breaks later
```

## Documentation

- [User Guide](../../docs/user-guide.md)
- [Command Reference](../../docs/commands.md)
- [Constitution](../../docs/constitution.md)
