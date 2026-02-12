# Camel-Kit Command Reference

This document provides detailed reference for all Camel-Kit commands.

## Table of Contents

- [CLI Commands](#cli-commands)
  - [camel-kit init](#camel-kit-init)
  - [camel-kit agents](#camel-kit-agents)
  - [camel-kit catalog](#camel-kit-catalog)
  - [camel-kit version](#camel-kit-version)
- [Slash Commands](#slash-commands)
  - [/camel.init](#camelinit)
  - [/camel.context](#camelcontext)
  - [/camel.route](#camelroute)
  - [/camel.validate](#camelvalidate)
  - [/camel.test](#cameltest)
  - [/camel.generate](#camelgenerate)

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
├── routes.camel.yaml        # Generated routes (after /camel.generate)
├── test/                    # Generated Citrus tests (Camel JBang convention)
│   ├── data/                # Test data files
│   ├── *.camel.it.yaml      # Test files
│   └── jbang.properties     # Test dependencies
├── .bob/commands/           # AI agent slash commands
│   ├── camel.init.md
│   ├── camel.context.md
│   ├── camel.route.md
│   ├── camel.validate.md
│   ├── camel.test.md
│   └── camel.generate.md
└── .camel-kit/
    ├── config.yaml          # Project configuration
    ├── constitution.md      # Best practices
    ├── context.md           # Integration landscape
    ├── templates/           # Reference templates
    └── routes/              # Route specifications
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

### /camel.init

Bootstrap the project with constitution and catalog.

**Usage:**

```
/camel.init
```

**When to use:**

Typically run automatically during `camel-kit init`. Use manually to:
- Re-initialize constitution
- Refresh catalog
- Reset project state

---

### /camel.context

Define the integration landscape through guided conversation.

**Usage:**

```
/camel.context
```

**Interactive flow:**

1. **Business Purpose** - What problem does this integration solve?
2. **Systems Discovery** - External systems, roles, protocols
3. **Data Formats** - JSON, XML, schemas
4. **Route Identification** - High-level route overview
5. **Non-Functional Requirements** - Volume, latency, availability
6. **Review & Confirm** - Summary and save

**Update mode:**

Running on existing context enters update mode:
- Shows current values
- Press Enter to keep, type to update
- Add/remove systems and routes

**Output:**

- Updates `.camel-kit/context.md`
- Creates route stubs in `.camel-kit/routes/`

---

### /camel.route

Design a single Camel route with catalog integration and constitution guidance.

**Usage:**

```
/camel.route <route-name>
```

**Arguments:**

| Argument | Description |
|----------|-------------|
| `route-name` | Identifier for the route (e.g., `order-ingestion`) |

**Interactive flow:**

1. **Source Design** - Where does data come from?
   - Catalog lookup for components/Kamelets
   - Gather required configuration
2. **Data Format** - JSON, XML, schema validation
3. **Processing Steps** - Describe logic, AI suggests EIPs
4. **Sink Design** - Where does data go?
5. **Error Handling** - DLQ, retry, circuit breaker
6. **Summary & Confirm** - Visual review and save

**EIP suggestions:**

| You describe... | EIP suggested |
|-----------------|---------------|
| "filter", "only process if" | Filter |
| "split", "process each" | Split |
| "combine", "batch together" | Aggregate |
| "enrich", "lookup" | Enrich / PollEnrich |
| "transform", "convert" | Transform / SetBody |
| "route based on", "if...then" | Choice |
| "send to multiple" | Multicast |

**Update mode:**

Running on existing route enters update mode:
- Shows current configuration per section
- Press Enter to keep, provide input to update
- Add/remove/reorder processing steps

**Output:**

- Saves route spec to `.camel-kit/routes/<route-name>.md`
- Updates context.md routes table if needed

---

### /camel.validate

Validate all route specifications before generating YAML.

**Usage:**

```
/camel.validate              # Validate all routes
/camel.validate <route-name> # Validate specific route
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
/camel.test <route-name>     # Generate tests for one route
/camel.test --all            # Generate tests for all routes
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

**Interactive flow:**

1. **Select Route** - Which route to test?
2. **Select Scenarios** - Which test cases?
3. **Generate Test Data** - Sample input/output files
4. **Configure Infrastructure** - Testcontainers, mocks
5. **Generate Test File** - Citrus YAML test

**Output:**

- Test file: `test/<route-name>.camel.it.yaml`
- Test data: `test/data/`
- Dependencies: `test/jbang.properties`

**Running tests:**

```bash
camel plugin add test
camel test run test/<route-name>.camel.it.yaml
```

---

### /camel.generate

Generate Kaoto-compatible Camel YAML DSL from specifications.

**Usage:**

```
/camel.generate
```

**Process:**

1. **Validation** - Runs `/camel.validate` first
   - Blocks if errors exist
   - Warns but continues if only warnings
2. **Transformation** - Converts specs to YAML DSL
3. **Output** - Writes to `routes.camel.yaml`

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
camel run routes.camel.yaml

# With environment variables
KAFKA_BROKERS=localhost:9092 camel run routes.camel.yaml

# Export to Maven project
camel export routes.camel.yaml \
  --runtime quarkus \
  --gav com.example:my-integration:1.0.0
```

---

## Command Cheat Sheet

```bash
# CLI
camel-kit init my-project --ai bob    # Create project
camel-kit catalog search kafka         # Search catalog
camel-kit agents                        # List AI agents

# Slash commands (in AI assistant)
/camel.context                          # Define landscape
/camel.route order-ingestion            # Design route
/camel.validate                         # Check specs
/camel.test order-ingestion             # Generate tests
/camel.generate                         # Output YAML
```
