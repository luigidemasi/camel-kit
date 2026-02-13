# Order Processing Example

This example demonstrates how to use Camel-Kit's Flow-Driven Development workflow to design and implement an order ingestion pipeline.

## Getting Started

Initialize a new Camel-Kit project using the CLI:
```bash
# Choose your AI assistant
camel-kit init my-integration --ai bob      # IBM Project Bob
camel-kit init my-integration --ai gemini   # Google Gemini CLI
camel-kit init my-integration --ai claude   # Anthropic Claude Code
```

This creates the project structure with AI agent commands and fetches the Camel component catalog.

## Scenario

Customer orders are received on a Kafka topic as JSON messages. Valid orders (amount >= $50) must be persisted to a PostgreSQL database. Invalid or low-value orders should be filtered or sent to a Dead Letter Queue (DLQ).

## Workflow Steps

### 0. Integration Context (Optional)

Define your integration landscape - systems and high-level integration goals:
```
/camel.project
```

**Sample conversation:**

```
AI: What is the business purpose of this integration project?
    (1-2 sentences describing the problem you're solving)

You: Automate the order fulfillment pipeline by reliably ingesting
     customer orders and persisting them to the database.

AI: What systems or applications are involved in this integration?
    Just list the system names - we'll define how they connect later.

You: Order Management System, Fulfillment Database

AI: What data flows do you need? Describe as "Move [data] from [source] to [sink]"

You: Move customer orders from Order Management System to Fulfillment Database
```

### 1. Define and Design the Flow

Define and design the integration flow (business + technical details):
```
/camel.flow order-ingestion
```

**Sample conversation:**

```
AI: What is the business intent of this flow?

You: Ensure all customer orders >= $50 are reliably captured and stored
     for fulfillment processing.

AI: Where does the data come from? (Source system and format)

You: JSON messages on a Kafka topic called "orders"

AI: Where does the data go? (Sink system)

You: PostgreSQL database, orders table

AI: What business rules or validations apply?

You: Only orders with amount >= $50 should be processed.
     Orders under $50 are filtered out.

AI: What can go wrong? (Error scenarios)

You: Invalid JSON format, validation failures, database unavailable

AI: Now let's design the technical implementation.
    What Camel component should consume from the source?

You: kafka

AI: What processing steps are needed?

You: 1. Validate JSON against schema
     2. Unmarshal to Java object
     3. Filter orders where amount >= 50

AI: What Camel component should write to the sink?

You: sql with INSERT statement

AI: What error handling strategy?

You: Dead Letter Channel to kafka:orders-dlq with 3 retries
```

**Result:** Flow definition saved to `.camel-kit/flows/order-ingestion/flow.md`

### 2. Implementation

Generate the Camel YAML DSL and supporting files:
```
/camel.implement order-ingestion
```

**Generated files:**
- `order-ingestion.camel.yaml` - Camel route (Kaoto compatible)
- `application.properties` - Component & bean configuration
- `docker-compose.yaml` - External services (Kafka, PostgreSQL)
- `run.sh` - Run script

### 3. Run the Integration

```bash
# Start infrastructure
docker compose up -d

# Run the Camel route
camel run order-ingestion.camel.yaml application.properties
```

### 4. Validation

Validate the route against the constitution and YAML schema:
```
/camel.validate
```

Or using Camel CLI:
```bash
camel run --check order-ingestion.camel.yaml application.properties
```

### 5. Testing

Generate integration tests with Testcontainers:
```
/camel.test order-ingestion
```

**Sample conversation:**

```
AI: Based on your flow, I recommend testing:
    [x] 1. Happy Path
    [x] 2. Filter Rejection (amount < $50)
    [x] 3. Error Handling / DLQ

    Which scenarios? (enter numbers, 'all', or describe custom)

You: all
```

**Run tests:**
```bash
# Install Citrus (one-time)
jbang app install citrus@citrusframework/citrus

# Make sure Docker is running (for Testcontainers)
docker info

# Run tests
cd test
citrus run order-ingestion.camel.it.yaml
```

Tests automatically start Kafka and PostgreSQL using Testcontainers.

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
├── .bob/commands/                 # AI agent slash commands
│   ├── camel.project.md
│   ├── camel.flow.md
│   ├── camel.implement.md
│   ├── camel.validate.md
│   └── camel.test.md
└── .camel-kit/
    ├── config.yaml
    ├── constitution.md
    ├── project.md
    └── flows/
        └── order-ingestion/
            └── flow.md
```

## Workflow Summary (1 Flow = 1 Route)

```
/camel.project                # (Optional) Define integration landscape
/camel.flow <flow-name>       # Define and design the flow
/camel.implement <flow-name>  # Generate <flow-name>.camel.yaml
/camel.validate               # Check against constitution & schema
/camel.test <flow-name>       # Generate integration tests
```

To add another flow, simply run:
```
/camel.flow order-notification
/camel.implement order-notification
```

## Documentation

- [User Guide](../../docs/user-guide.md)
- [Command Reference](../../docs/commands.md)
- [Constitution](../../docs/constitution.md)
