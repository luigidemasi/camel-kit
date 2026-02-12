# Camel-Kit User Guide

This guide walks you through using Camel-Kit to design Apache Camel integrations with AI coding assistants.

## Table of Contents

- [Introduction](#introduction)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Workflow Overview](#workflow-overview)
- [Working with Context](#working-with-context)
- [Designing Routes](#designing-routes)
- [Validation](#validation)
- [Test Generation](#test-generation)
- [YAML Generation](#yaml-generation)
- [Best Practices](#best-practices)
- [Troubleshooting](#troubleshooting)

---

## Introduction

Camel-Kit is a toolkit that guides you through designing Apache Camel integrations using AI coding assistants. Instead of writing code directly, you work with structured specifications that capture your integration requirements, then generate Kaoto-compatible YAML.

**Key concepts:**

- **Context** - The integration landscape: systems, protocols, data formats
- **Routes** - Individual data flows: source → processing → sink
- **Constitution** - Best practices that guide design decisions
- **Catalog** - Live component and Kamelet information from Apache Camel

---

## Installation

### Using uv (Recommended)

```bash
# Install persistently
uv tool install camel-kit-cli --from git+https://github.com/luigidemasi/camel-kit.git

# Or run once without installing
uvx --from git+https://github.com/luigidemasi/camel-kit.git camel-kit --help
```

### Using pip

```bash
pip install git+https://github.com/luigidemasi/camel-kit.git
```

### Verify Installation

```bash
camel-kit version
camel-kit --help
```

---

## Quick Start

```bash
# 1. Create a new project
camel-kit init order-processing --ai bob

# 2. Open in your AI assistant (e.g., IBM Project Bob)
cd order-processing

# 3. Use slash commands in the AI assistant:
#    /camel.context     - Define what systems you're connecting
#    /camel.route       - Design each route
#    /camel.validate    - Check your specifications
#    /camel.generate    - Output Camel YAML

# 4. Run the generated routes
camel run routes.camel.yaml
```

---

## Workflow Overview

```
┌─────────────────────────────────────────────────────────────────┐
│  1. INIT                                                         │
│     camel-kit init my-project --ai bob                          │
│     Creates project structure and fetches catalogs               │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│  2. CONTEXT                                                      │
│     /camel.context                                               │
│     Define systems, protocols, data formats, route overview      │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│  3. ROUTES (repeat for each route)                               │
│     /camel.route order-ingestion                                 │
│     Design source, processing, sink, error handling              │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│  4. VALIDATE                                                     │
│     /camel.validate                                              │
│     Check completeness, correctness, constitution compliance     │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│  5. TEST                                                         │
│     /camel.test order-ingestion                                  │
│     Generate Citrus integration tests                            │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│  6. GENERATE                                                     │
│     /camel.generate                                              │
│     Output Kaoto-compatible Camel YAML DSL                       │
└─────────────────────────────────────────────────────────────────┘
```

---

## Working with Context

The context defines your integration landscape before you design individual routes.

### Creating Context

Run `/camel.context` in your AI assistant. You'll be guided through:

1. **Business Purpose** - What problem does this integration solve?
2. **Systems** - What external systems are involved?
3. **Data Formats** - JSON, XML, Avro, etc.
4. **Routes Overview** - High-level list of needed routes
5. **Non-Functional Requirements** - Volume, latency, availability

### Updating Context

Running `/camel.context` again enters **update mode**:
- Existing values are shown
- Press Enter to keep, or type to update
- Add/remove systems and routes as needed

### Context File

The context is saved to `.camel-kit/context.md` in structured markdown format.

---

## Designing Routes

Each route represents a single data flow: source → processing → sink.

### Creating a Route

```
/camel.route order-ingestion
```

The AI assistant guides you through:

1. **Source** - Where does data come from?
2. **Data Format** - How is the data structured?
3. **Processing** - What transformations are needed?
4. **Sink** - Where does data go?
5. **Error Handling** - How to handle failures?

### Catalog Integration

During route design, the AI assistant searches the cached Camel catalog to:
- Suggest appropriate components or Kamelets
- Show required vs optional parameters
- Validate your configuration

### Updating Routes

Running `/camel.route <name>` on an existing route enters **update mode**:
- Current configuration is shown for each section
- Press Enter to keep, or provide new input
- Processing steps can be added, removed, or reordered

### Route Files

Routes are saved to `.camel-kit/routes/<route-name>.md`.

---

## Validation

Validation checks your specifications before generating YAML.

### Running Validation

```
/camel.validate           # Validate all routes
/camel.validate order-ingestion  # Validate specific route
```

### What's Checked

| Category | Examples |
|----------|----------|
| **Completeness** | Source defined, sink defined, error handling |
| **Correctness** | Valid component names, valid EIP usage |
| **Constitution** | Naming conventions, circuit breakers, schema validation |
| **Dependencies** | All direct: endpoints have routes, no circular deps |

### Validation Report

Results are saved to `.camel-kit/validation-report.md` with:
- Pass/fail status for each check
- Error codes for failures
- Suggested fixes

---

## Test Generation

Generate Citrus integration tests for your routes.

### Running Test Generation

```
/camel.test order-ingestion    # Generate tests for one route
/camel.test --all              # Generate tests for all routes
```

### Test Scenarios

Based on your route design, tests are generated for:
- Happy path
- Error handling / invalid input
- Dead letter queue
- Idempotency (if using idempotent consumer)
- Circuit breaker fallback (if using resilience patterns)
- Filter conditions (if using filter EIP)

### Test Files

Tests are saved to `test/<route-name>.camel.it.yaml` following the Camel JBang naming convention, with test data in `test/data/`.

### Running Tests

```bash
# Install Camel test plugin
camel plugin add test

# Run tests (Testcontainers manages infrastructure automatically)
camel test run test/order-ingestion.camel.it.yaml

# Or start infrastructure separately
camel infra run kafka
camel infra run postgres
camel test run test/order-ingestion.camel.it.yaml
```

---

## YAML Generation

Generate Kaoto-compatible Camel YAML DSL from your specifications.

### Running Generation

```
/camel.generate
```

This:
1. Runs validation first (blocks if errors)
2. Transforms specifications to Camel YAML DSL
3. Outputs to `routes.camel.yaml`

### Kaoto Compatibility

Generated YAML follows Kaoto requirements:
- Nested EIPs under parent's `steps` array
- Proper expression format for Simple, JSONPath, etc.
- Error handlers at route level
- Environment variables as `{{VARIABLE}}`

### Running Generated Routes

```bash
# With Camel JBang
camel run routes.camel.yaml

# With environment variables
KAFKA_BROKERS=localhost:9092 camel run routes.camel.yaml

# Export to Maven project
camel export routes.camel.yaml \
  --runtime quarkus \
  --gav com.example:my-integration:1.0.0
```

---

## Best Practices

Camel-Kit enforces best practices through the **constitution** (`.camel-kit/constitution.md`):

| Principle | Description |
|-----------|-------------|
| Single Responsibility | One route = one clear purpose |
| Error Handling | Every route declares error strategy |
| Circuit Breaker | External service calls use resilience patterns |
| Idempotency | Consumer routes handle duplicates |
| Schema Validation | Validate at integration boundaries |
| Secrets Management | Use environment variables, never hardcode |
| Observability | Include correlation IDs and logging |
| Naming Conventions | Route IDs follow `<domain>-<action>` pattern |

### Customizing the Constitution

Edit `.camel-kit/constitution.md` to:
- Disable specific rules
- Add organization-specific guidelines
- Adjust strictness levels

---

## Troubleshooting

### Catalog Not Found

```
Error: Catalog not cached
```

**Solution:** Fetch the catalog:
```bash
camel-kit catalog fetch
```

### Validation Errors

```
COMP-001: Component 'kafak' not found
```

**Solution:** Check for typos. The validation will suggest corrections.

### Route Dependencies

```
DEP-001: direct:inventory-lookup referenced but route not found
```

**Solution:** Create the missing route:
```
/camel.route inventory-lookup
```

### Test Failures

If generated tests fail, check:
1. Infrastructure is running (Kafka, database, etc.)
2. Test data matches current schema
3. Route behavior matches test expectations

Regenerate tests after route changes:
```
/camel.test <route-name>
```

---

## Next Steps

- See [Command Reference](commands.md) for detailed command documentation
- See [Constitution](constitution.md) for best practices details
- See [CONTRIBUTING.md](../CONTRIBUTING.md) to contribute to Camel-Kit
