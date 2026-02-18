# Camel-Kit Command Reference

This document provides detailed reference for all Camel-Kit commands.

## Table of Contents

- [CLI Commands](#cli-commands)
  - [camel-kit init](#camel-kit-init)
- [Slash Commands](#slash-commands)
  - [/camel.project](#camelproject)
  - [/camel.flow](#camelflow)
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
| `--ai`, `-a` | `bob` | AI coding assistant to configure (bob, gemini, claude) |
| `--camel-version`, `-v` | `4.14.5` | Apache Camel version to target |
| `--citrus-version` | `4.9.2` | Citrus Framework version for test schemas |
| `--here` | `false` | Initialize in current directory |
| `--no-fetch` | `false` | Skip external catalog fetching |

**Examples:**

```bash
# Create new project for IBM Project Bob
camel-kit init my-integration --ai bob

# Create new project for Gemini CLI
camel-kit init my-integration --ai gemini

# Create new project for Claude Code
camel-kit init my-integration --ai claude

# Use specific Camel version
camel-kit init my-integration --camel-version 4.14.5

# Use specific Citrus version
camel-kit init my-integration --citrus-version 4.9.2

# Initialize in current directory
camel-kit init --here --ai bob

# Skip catalog fetch (faster, offline)
camel-kit init my-integration --ai bob --no-fetch
```

**Output:**

Creates the following structure:

```
my-integration/
├── mvnw                         # Maven Wrapper (Unix)
├── mvnw.cmd                     # Maven Wrapper (Windows)
├── .mvn/wrapper/                # Maven Wrapper config
├── test/                        # Generated Citrus tests
│   └── data/                    # Test data files
├── schemas/                     # JSON/XML schemas
├── .bob/commands/               # AI agent slash commands
│   ├── camel.project.md
│   ├── camel.flow.md
│   ├── camel.implement.md
│   ├── camel.validate.md
│   └── camel.test.md
└── .camel-kit/
    ├── config.yaml              # Project configuration
    ├── constitution.md          # Best practices
    ├── .cache/                  # Downloaded catalogs and schemas
    │   ├── components-{version}.json
    │   ├── kamelets-{version}.json
    │   ├── camelYamlDsl-{version}.json
    │   └── citrus/{version}/    # Citrus JSON schemas
    ├── flows/                   # Flow definitions
    └── templates/               # Reference templates
```

---

## Slash Commands

These commands are used within your AI coding assistant after project initialization.

### /camel.project

**(Optional)** Define your integration landscape and identify all flows.

**Usage:**

```
/camel.project
```

**Interactive flow:**

1. **Business Purpose** - What problem does this integration solve? (1-2 sentences)
2. **Systems** - List system names and their role (source/sink)
3. **Flows** - Describe data flows as "Move [data] from [source] to [sink]"

**Output:**

- Saves to `.camel-kit/project.md`

---

### /camel.flow

Define a flow's business requirements, technical design, and data contracts.

**Usage:**

```
/camel.flow <flow-name>
```

**Interactive flow:**

1. **Flow Identification** - Extract core intent
2. **Source & Sink** - Where data comes from and goes to
3. **Processing Steps** - High-level steps and EIPs required
4. **Data Contracts** - Input/output formats and schemas
5. **Error Scenarios** - What can go wrong
6. **Error Handling** - Dead Letter Channel, Retry, Circuit Breaker
7. **Flow Diagram** - Mermaid visualization

**Output:**

- Creates `.camel-kit/flows/<flow-name>/flow.md`

---

### /camel.implement

Generate Camel YAML DSL from the flow definition with automated validation.

**Usage:**

```
/camel.implement <flow-name>
```

**Prerequisites:**

- Flow definition must exist (`/camel.flow` first)

**Process:**

1. **Load Flow** - Read flow definition and component catalog
2. **Component Lookup** - Verify components exist in catalog
3. **Generate YAML** - Transform design to Camel YAML DSL
4. **Validate** - Run validation loop until YAML is valid
5. **Output** - Write to `<flow-name>.camel.yaml`

**Validation Loop:**

The command uses the official Camel YAML DSL Validator Maven plugin:

```bash
./mvnw org.apache.camel:camel-yaml-dsl-validator:{version}:validate \
  -Dcamel.validator.files=<flow-name>.camel.yaml
```

The AI agent loops until validation passes:
1. Generate YAML
2. Run validation
3. If errors, fix and repeat
4. If success, proceed

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
# Run with application.properties (includes camel.jbang.dependencies)
camel run order-ingestion.camel.yaml application.properties

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
[OK] order-ingestion: source defined
[OK] order-ingestion: sink defined
[ERROR] order-ingestion: error handling NOT defined

== CORRECTNESS ==
[OK] kafka component valid (Camel 4.14.x)
[WARN] kafak component - did you mean 'kafka'?

VALIDATION FAILED - 2 errors, 1 warning
```

**Validation report:**

Saves detailed report to `.camel-kit/validation-report.md`.

---

### /camel.test

Generate Citrus integration tests for routes with automated validation.

**Usage:**

```
/camel.test <flow-name>      # Generate tests for one flow
/camel.test --all            # Generate tests for all flows
```

**Prerequisites:**

- Citrus schemas must be cached (downloaded during `camel-kit init`)

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

**Validation Loop:**

The command uses the json-yaml-validator-maven-plugin:

```bash
./mvnw com.dataliquid.maven:json-yaml-validator-maven-plugin:2.0.0:validate \
  -Dschema.validator.schemaFile=.camel-kit/.cache/citrus/{version}/citrus-testcase.json \
  -Dschema.validator.sourceDirectory=test \
  -Dschema.validator.includes=**/*.camel.it.yaml
```

The AI agent loops until validation passes.

**Output:**

- Test file: `test/<flow-name>.camel.it.yaml`
- Test data: `test/data/`
- Dependencies: `test/jbang.properties`
- Test config: `test/application.test.properties`

**Running tests:**

```bash
# Install Citrus JBang app
jbang app install citrus@citrusframework/citrus

# Run tests (Docker required for Testcontainers)
cd test
citrus run <flow-name>.camel.it.yaml

# Or using Camel test plugin
camel plugin add test
camel test run test/<flow-name>.camel.it.yaml
```

---

## Command Cheat Sheet

```bash
# CLI
camel-kit init my-project --ai bob     # Create project

# Slash commands (in AI assistant)
/camel.project                         # Define landscape (optional)
/camel.flow order-ingestion            # Define and design flow
/camel.implement order-ingestion       # Generate YAML with validation
/camel.validate                        # Check specs
/camel.test order-ingestion            # Generate tests with validation
```
