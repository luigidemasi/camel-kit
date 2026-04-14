# Order Processing Example

This example demonstrates how to use Camel-Kit's workflow to design and implement an order ingestion pipeline.

## Getting Started

Initialize a new Camel-Kit project using the CLI:
```bash
# Choose your AI assistant
camel-kit init my-integration --ai bob        # IBM Project Bob
camel-kit init my-integration --ai gemini     # Google Gemini CLI
camel-kit init my-integration --ai claude     # Anthropic Claude Code
camel-kit init my-integration --ai qwen       # Qwen
camel-kit init my-integration --ai opencode   # OpenCode
```

This creates the project structure with AI agent configuration and skills.

## Scenario

Customer orders are received on a Kafka topic as JSON messages. Valid orders (amount >= $50) must be persisted to a PostgreSQL database. Invalid or low-value orders should be filtered or sent to a Dead Letter Queue (DLQ).

## Workflow Steps

### 1. Brainstorm the Design

Start an interactive design session:
```
/camel-brainstorm
```

The assistant asks questions one at a time about:
- Business purpose (automate order fulfillment)
- Systems involved (Kafka, PostgreSQL)
- Data flows (orders from Kafka topic to database)
- Business rules (only orders >= $50)
- Error handling (DLQ with retries)

**Result:** BRD and TDD saved to `.camel-kit/`

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

### Alternative: Single-Flow Shortcut

For a quicker workflow on a single flow:
```
/camel-flow
```

This combines brainstorm + plan + execute in one command.

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
    ├── config.yaml
    ├── business-requirements.md
    └── flows/
        └── order-ingestion/
            └── order-ingestion.tdd.md
```

## Workflow Summary

```
/camel-brainstorm              # Design the integration (interview + spec)
/camel-plan                    # Create implementation plan (auto after brainstorm)
/camel-execute                 # Implement, validate, test, verify (auto after plan)

# Or use the single-flow shortcut:
/camel-flow                    # All-in-one for a single flow

# Standalone verification:
/camel-verify                  # Build, start, diagnose, fix
```

## Documentation

- [User Guide](../../docs/user-guide.md)
- [Command Reference](../../docs/commands.md)
- [Constitution](../../docs/constitution.md)
