# Camel-Kit Command Reference

This document provides detailed reference for all Camel-Kit commands.

## Table of Contents

- [CLI Commands](#cli-commands)
  - [camel-kit init](#camel-kit-init)
  - [camel-kit agents](#camel-kit-agents)
  - [camel-kit catalog](#camel-kit-catalog)
  - [camel-kit version](#camel-kit-version)
- [Slash Commands](#slash-commands)
  - [/camel.context](#camelcontext)
  - [/camel.flow](#camelflow)
  - [/camel.flow](#camelroute)
  - [/camel.implement](#camelimplement)
  - [/camel.validate](#camelvalidate)
  - [/camel.test](#cameltest)

---

## CLI Commands

These commands are run in your terminal.

### camel-kit init

Initialize a new Camel-Kit project.

**Usage:**

```bash
camel-kit init <project-name> [options]
camel-kit init --here [options]
```

**Arguments:**

| Argument | Description |
|----------|-------------|
| `project-name` | Name of the project directory to create |

**Options:**

| Option | Default | Description |
|--------|---------|-------------|
| `--ai`, `-a` | `bob` | AI coding assistant to configure |
| `--camel-version`, `-v` | `latest` | Apache Camel version to target |
| `--here` | `false` | Initialize in current directory |
| `--force`, `-f` | `false` | Overwrite existing files without confirmation |
| `--fetch-catalog/--no-fetch-catalog` | `true` | Fetch component and Kamelet catalogs |

**Examples:**

```bash
# Create new project for IBM Project Bob
camel-kit init my-integration --ai bob

# Use specific Camel version
camel-kit init my-integration --camel-version 4.10.0

# Initialize in current directory
camel-kit init --here --ai bob

# Skip catalog fetch (faster, offline)
camel-kit init my-integration --ai bob --no-fetch-catalog
```

**Output:**

Creates the following structure:

```
my-integration/
├── <flow-name>.camel.yaml   # Generated routes (after /camel.implement)
├── test/                    # Generated Citrus tests
│   ├── data/                # Test data files
│   ├── *.camel.it.yaml      # Test files
│   └── jbang.properties     # Test dependencies
├── .bob/commands/           # AI agent slash commands
│   ├── camel.context.md
│   ├── camel.flow.md
│   ├── camel.flow.md
│   ├── camel.implement.md
│   ├── camel.validate.md
│   └── camel.test.md
└── .camel-kit/
    ├── config.yaml          # Project configuration
    ├── constitution.md      # Best practices
    ├── context.md           # Integration landscape (optional)
    ├── flows/               # Flow definitions (1 flow = 1 route)
    │   └── <flow-name>/
    │       ├── flow.md      # Business-level flow definition
    │       └── flow.md      # Technical route design
    └── templates/           # Reference templates
```

---

### camel-kit agents

List available AI coding agents.

**Usage:**

```bash
camel-kit agents
```

**Output:**

```
┌────────┬──────────────────┬──────────────────┬───────────┐
│ Agent  │ Name             │ Commands Folder  │ Status    │
├────────┼──────────────────┼──────────────────┼───────────┤
│ bob    │ IBM Project Bob  │ .bob/commands/   │ Available │
└────────┴──────────────────┴──────────────────┴───────────┘
```

---

### camel-kit catalog

Manage Camel component and Kamelet catalogs.

**Usage:**

```bash
camel-kit catalog <action> [options]
```

**Actions:**

| Action | Description |
|--------|-------------|
| `info` | Show catalog status and statistics |
| `fetch` | Download/refresh catalogs |
| `search <query>` | Search components and Kamelets |

**Options:**

| Option | Default | Description |
|--------|---------|-------------|
| `--camel-version`, `-v` | `latest` | Apache Camel version |
| `--force`, `-f` | `false` | Force refresh even if cached |
| `--type`, `-t` | (all) | Filter Kamelets: `source`, `sink`, `action` |

**Examples:**

```bash
# Show catalog status
camel-kit catalog info

# Fetch catalogs for specific version
camel-kit catalog fetch --camel-version 4.10.0

# Force refresh
camel-kit catalog fetch --force

# Search for Kafka components/Kamelets
camel-kit catalog search kafka

# Search for sink Kamelets only
camel-kit catalog search postgres --type sink
```

---

### camel-kit version

Show Camel-Kit version.

**Usage:**

```bash
camel-kit version
```

---

## Slash Commands

These commands are used within your AI coding assistant after project initialization.

### /camel.context

**(Optional)** Define your integration landscape and identify all flows.

**Usage:**

```
/camel.context
```

**Interactive flow:**

1. **Business Purpose** - What problem does this integration solve? (1-2 sentences)
2. **Systems** - List system names and their role (source/sink)
3. **Flows** - Describe data flows as "Move [data] from [source] to [sink]"

**Output:**

- Saves to `.camel-kit/context.md`

---

### /camel.flow

Define a flow's business requirements and data contracts.

**Usage:**

```
/camel.flow <flow-name>
```

**Interactive flow:**

1. **Flow Identification** - Extract core intent
2. **Source & Sink** - Where data comes from and goes to (business terms)
3. **Processing Steps** - High-level steps required
4. **Data Contracts** - Input/output formats and schemas
5. **Error Scenarios** - What can go wrong
6. **Flow Diagram** - Mermaid visualization

**Output:**

- Creates `.camel-kit/flows/<flow-name>/flow.md`

---

### /camel.flow

Design the technical route for a flow (source, sink, EIPs, error handling).

**Usage:**

```
/camel.flow <flow-name>
```

**Prerequisites:**

- Flow definition must exist (`/camel.flow` first)

**Interactive flow:**

1. **Source (Consumer)** - Select Camel component/Kamelet
2. **Processing Steps (EIPs)** - Filter, Split, Aggregate, Transform, etc.
3. **Sink (Producer)** - Select Camel component/Kamelet
4. **Error Handling** - Dead Letter Channel, Retry, Circuit Breaker
5. **Constitution Gate Check** - Verify against best practices
6. **Route Diagram** - Mermaid visualization with EIP icons

**Output:**

- Creates `.camel-kit/flows/<flow-name>/flow.md`
- Identifies necessary schemas in `schemas/`

---

### /camel.implement

Generate Camel YAML DSL from the route design.

**Usage:**

```
/camel.implement <flow-name>
```

**Prerequisites:**

- Route design must exist (`/camel.flow` first)

**Process:**

1. **Validation** - Ensures plan and schemas exist
2. **Transformation** - Converts plan to YAML DSL
3. **Output** - Writes to `<flow-name>.camel.yaml`

**Kaoto compatibility:**

Generated YAML follows Kaoto requirements:
- Nested EIPs under `steps` arrays
- Proper expression syntax
- Route-level error handlers
- Environment variable placeholders

**Output:**

```yaml
- route:
    id: order-ingestion
    description: Consume orders from Kafka and persist to database

    errorHandler:
      deadLetterChannel:
        deadLetterUri: kafka:orders-dlq

    from:
      uri: kafka:orders
      parameters:
        brokers: "{{KAFKA_BROKERS}}"
      steps:
        - unmarshal:
            json:
              unmarshalType: com.example.Order
        - filter:
            simple: "${body.totalAmount} >= 50"
            steps:
              - to:
                  uri: jpa:com.example.Order
```

**Running generated routes:**

```bash
# Direct execution
camel run order-ingestion.camel.yaml

# With environment variables
KAFKA_BROKERS=localhost:9092 camel run order-ingestion.camel.yaml

# Export to Maven project
camel export order-ingestion.camel.yaml \
  --runtime quarkus \
  --gav com.example:my-integration:1.0.0
```

---

### /camel.validate

Validate route specifications before generating YAML.

**Usage:**

```
/camel.validate              # Validate all flows
/camel.validate <flow-name>  # Validate specific flow
/camel.validate --strict     # Treat warnings as errors
```

**Validation categories:**

| Category | Code Prefix | Examples |
|----------|-------------|----------|
| Completeness | `COMP-*` | Source/sink defined, error handling |
| Correctness | `CORR-*` | Valid components, EIP usage |
| Constitution | `CONST-*` | Naming, circuit breakers |
| Dependencies | `DEP-*` | direct: endpoints, circular deps |

**Output format:**

```
== COMPLETENESS ==
✅ order-ingestion: source defined
✅ order-ingestion: sink defined
❌ order-ingestion: error handling NOT defined

== CORRECTNESS ==
✅ kafka component valid (Camel 4.10.x)
⚠️  kafak component - did you mean 'kafka'?

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
❌ VALIDATION FAILED - 2 errors, 1 warning
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

**Validation report:**

Saves detailed report to `.camel-kit/validation-report.md`.

---

### /camel.test

Generate Citrus integration tests for routes.

**Usage:**

```
/camel.test <flow-name>      # Generate tests for one flow
/camel.test --all            # Generate tests for all flows
/camel.test --scenarios      # List available test scenarios
```

**Test scenarios:**

| Scenario | When generated |
|----------|----------------|
| Happy Path | Always |
| Error Handling | Always |
| Dead Letter Queue | If DLQ configured |
| Idempotency | If idempotent consumer used |
| Circuit Breaker | If resilience pattern used |
| Filter Conditions | If filter EIP used |
| Split Processing | If split EIP used |

**Output:**

- Test file: `test/<flow-name>.camel.it.yaml`
- Test data: `test/data/`
- Dependencies: `test/jbang.properties`

**Running tests:**

```bash
camel plugin add test
camel test run test/<flow-name>.camel.it.yaml
```

---

## Command Cheat Sheet

```bash
# CLI
camel-kit init my-project --ai bob     # Create project
camel-kit catalog search kafka         # Search catalog
camel-kit agents                       # List AI agents

# Slash commands (in AI assistant)
/camel.context                         # Define landscape (optional)
/camel.flow order-ingestion            # Define flow (business level)
/camel.flow order-ingestion           # Design route (technical level)
/camel.implement order-ingestion       # Generate YAML
/camel.validate                        # Check specs
/camel.test order-ingestion            # Generate tests
```
