# Camel-Kit

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

> Design Apache Camel integrations with AI coding assistants using spec-driven development.

**Camel-Kit is heavily inspired by [GitHub Spec-Kit](https://github.com/github/spec-kit)** — a brilliant project by the GitHub team that pioneered the concept of spec-driven development with AI coding assistants. We're grateful to the spec-kit authors for their innovative approach and for making their work available to the community. Their elegant design patterns and philosophy have directly shaped how Camel-Kit guides developers through integration design.

Camel-Kit adapts these ideas for the Apache Camel ecosystem, providing structured slash commands for AI coding assistants (like IBM Project Bob) to help you design, validate, and generate Camel routes following best practices.

## Features

- **Guided Design** - Interactive commands walk you through integration design step-by-step
- **Best Practices** - Constitution enforces Apache Camel best practices automatically
- **Catalog Integration** - Live component and Kamelet catalog lookup with suggestions
- **Validation** - Comprehensive checks before generating code
- **Kaoto-Ready Output** - Generate YAML DSL compatible with [Kaoto](https://kaoto.io/) visual designer
- **Citrus Testing** - Generate integration tests using [Citrus Framework](https://citrusframework.org/)

## Quick Start

### Installation

**Using uv (Recommended):**

[uv](https://github.com/astral-sh/uv) is a fast Python package manager. Install it first:

```bash
curl -LsSf https://astral.sh/uv/install.sh | sh
```

Then install camel-kit:

```bash
# Persistent installation (adds to PATH)
uv tool install camel-kit-cli --from git+https://github.com/luigidemasi/camel-kit.git

# Verify installation
camel-kit version
```

**Using uvx (Run without installing):**

```bash
# Run any camel-kit command directly without installation
uvx --from git+https://github.com/luigidemasi/camel-kit.git camel-kit init my-integration

# Examples
uvx --from git+https://github.com/luigidemasi/camel-kit.git camel-kit version
uvx --from git+https://github.com/luigidemasi/camel-kit.git camel-kit catalog search kafka
```

**Using pip:**

```bash
pip install git+https://github.com/luigidemasi/camel-kit.git
```

### Initialize a Project

```bash
# Create new integration project
camel-kit init my-integration --ai bob

# With specific Camel version
camel-kit init my-integration --ai bob --camel-version 4.10.0

# Initialize in current directory
camel-kit init --here --ai bob
```

### Use with AI Assistant

Open your project in IBM Project Bob (or other supported AI assistant) and use the slash commands:

```
/camel.context     Define your integration landscape
/camel.route       Design individual routes
/camel.validate    Check specifications
/camel.test        Generate Citrus tests
/camel.generate    Output Kaoto-ready YAML
```

## Documentation

- [User Guide](docs/user-guide.md) - Complete guide to using camel-kit
- [Command Reference](docs/commands.md) - Detailed command documentation
- [Constitution](docs/constitution.md) - Best practices enforced by camel-kit
- [Contributing](CONTRIBUTING.md) - How to contribute to camel-kit

## Supported AI Agents

| Agent | Status | Commands Folder |
|-------|--------|-----------------|
| [IBM Project Bob](https://www.ibm.com/products/bob) | ✅ Available | `.bob/commands/` |
| Claude Code | 🔜 Planned | `.claude/commands/` |
| GitHub Copilot | 🔜 Planned | `.github/agents/` |
| Cursor | 🔜 Planned | `.cursor/commands/` |

## Commands Overview

| Command | Purpose |
|---------|---------|
| `/camel.init` | Bootstrap project with constitution and catalog |
| `/camel.context` | Define systems, protocols, and route overview |
| `/camel.route <name>` | Design individual route with EIPs |
| `/camel.validate` | Check completeness and correctness |
| `/camel.test` | Generate Citrus integration tests |
| `/camel.generate` | Output Camel YAML DSL |

## Project Structure

After initialization:

```
my-integration/
├── .bob/commands/              # AI agent slash commands
│   ├── camel.init.md
│   ├── camel.context.md
│   ├── camel.route.md
│   ├── camel.validate.md
│   ├── camel.test.md
│   └── camel.generate.md
└── .camel-kit/                 # Specifications and output
    ├── config.yaml             # Project configuration
    ├── constitution.md         # Best practices
    ├── context.md              # Integration landscape
    ├── templates/              # Reference templates
    │   ├── route.md
    │   ├── yaml-generation-guide.md
    │   └── validation-guide.md
    ├── routes/                 # Route specifications
    ├── tests/                  # Generated Citrus tests
    │   └── test-data/
    └── output/                 # Generated YAML
        └── routes.camel.yaml
```

## Example Workflow

```bash
# 1. Initialize project
camel-kit init order-processing --ai bob

# 2. Open in IBM Project Bob
cd order-processing

# 3. In the AI assistant, run:
#    /camel.context
#    - Define: Kafka source, PostgreSQL sink
#    - Identify routes: order-ingestion, order-validation

# 4. Design routes:
#    /camel.route order-ingestion
#    - Source: Kafka topic "orders"
#    - Processing: Unmarshal JSON, validate, filter
#    - Sink: PostgreSQL database
#    - Error handling: Dead Letter Channel

# 5. Validate:
#    /camel.validate
#    - Check completeness
#    - Verify against catalog
#    - Constitution compliance

# 6. Generate tests:
#    /camel.test order-ingestion
#    - Happy path test
#    - Error handling test
#    - DLQ test

# 7. Generate YAML:
#    /camel.generate
#    - Creates: .camel-kit/output/routes.camel.yaml

# 8. Open in Kaoto or run:
camel run .camel-kit/output/routes.camel.yaml
```

## Output Example

Generated Kaoto-compatible YAML:

```yaml
- route:
    id: order-ingestion
    description: Consume orders from Kafka and persist to database

    errorHandler:
      deadLetterChannel:
        deadLetterUri: kafka:orders-dlq
        redeliveryPolicy:
          maximumRedeliveries: 3

    from:
      uri: kafka:orders
      parameters:
        brokers: "{{KAFKA_BROKERS}}"
        groupId: order-processor
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

## CLI Commands

```bash
# Project initialization
camel-kit init <project-name> [options]
  --ai, -a        AI agent (default: bob)
  --camel-version Version (default: latest)
  --here          Initialize in current directory
  --no-fetch-catalog  Skip catalog download

# List available agents
camel-kit agents

# Manage component/Kamelet catalog
camel-kit catalog info
camel-kit catalog fetch [--force]
camel-kit catalog search <query> [--type source|sink|action]

# Show version
camel-kit version
```

## Requirements

- Python 3.11+
- [uv](https://github.com/astral-sh/uv) (recommended) or pip
- [Camel JBang](https://camel.apache.org/manual/camel-jbang.html) (for running routes)
- Supported AI coding assistant (IBM Project Bob, etc.)

## Related Projects

- [Apache Camel](https://camel.apache.org/) - Integration framework
- [Kaoto](https://kaoto.io/) - Visual integration designer
- [Citrus Framework](https://citrusframework.org/) - Integration testing
- [GitHub Spec-Kit](https://github.com/github/spec-kit) - Inspiration for spec-driven development

## License

Apache License 2.0 - See [LICENSE](LICENSE) for details.

## Contributing

Contributions are welcome! See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

## Acknowledgments

This project owes its existence to the brilliant work of the **[GitHub Spec-Kit](https://github.com/github/spec-kit)** team. Their pioneering vision of spec-driven development with AI assistants has transformed how developers can approach software design. The spec-kit authors demonstrated that AI coding assistants become dramatically more effective when guided by structured specifications and clear workflows. We deeply appreciate their open approach to sharing these ideas.

Camel-Kit is our humble attempt to bring these powerful concepts to the Apache Camel integration community.
