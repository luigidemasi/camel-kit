# Camel-Kit

<p align="center">
  <img src="camel-kit.gif" alt="Camel-Kit Logo" width="600"/>
</p>

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Website](https://img.shields.io/badge/Website-luigidemasi.github.io%2Fcamel--kit-orange)](https://luigidemasi.github.io/camel-kit/)

> Design and migrate Apache Camel integrations with AI coding assistants.

Camel-Kit adds structured slash commands to your AI assistant (Claude Code, IBM Project Bob, Gemini CLI) that guide you through designing, implementing, and testing Apache Camel routes — whether you are starting from scratch or migrating from another platform.

**Inspired by [GitHub Spec-Kit](https://github.com/github/spec-kit)**, adapted for the Apache Camel ecosystem.

---

## Installation

**Requires:** Java 17+, [JBang](https://www.jbang.dev/)

### Standalone (JBang)

```bash
# Install JBang (if not already installed)
curl -Ls https://sh.jbang.dev | bash -s - app setup        # Linux/macOS
iex "& { $(iwr -useb https://ps.jbang.dev) } app setup"    # Windows PowerShell

# Install camel-kit globally
jbang app install camel-kit@luigidemasi/camel-kit

# Verify
camel-kit --help
```

### Run without installing

```bash
jbang run camel-kit@luigidemasi/camel-kit init my-integration --ai claude
```

### Camel JBang Plugin

If you already use [Camel JBang](https://camel.apache.org/manual/camel-jbang.html), install camel-kit as a plugin:

```bash
camel plugin add kit \
  --gav io.github.luigidemasi:camel-kit-jbang-plugin:0.3.1 \
  -d "Design Apache Camel Integrations with AI"

# Then use via the camel CLI
camel kit init my-integration --ai bob
```

---

## Bootstrap a new project

```bash
camel-kit init my-integration --ai claude     # Claude Code
camel-kit init my-integration --ai bob        # IBM Project Bob
camel-kit init my-integration --ai gemini     # Gemini CLI

# Options
camel-kit init my-integration --ai claude --camel-version 4.14.5
camel-kit init --here --ai claude             # initialize in current directory
camel-kit init my-integration --ai claude --silent  # suppress all output (CI/scripts)
```

This creates the project structure, downloads the Camel catalog, configures MCP, and registers all slash commands in your AI assistant's commands folder.

Then open the project in your AI assistant and use the slash commands — see [Command Reference](docs/commands.md) for the full list.

---

## Migrate an existing project to Apache Camel

```bash
camel-kit init my-migration --ai claude
cd my-migration
```

Then in your AI assistant:

```
/camel-migrate
```

The command auto-detects your source platform (MuleSoft Mule, Apache Camel 2.x/3.x), analyses the flows, and produces the same BRD + TDD files that the greenfield workflow produces — so `/camel-implement`, `/camel-validate`, and `/camel-test` work without any changes.

See [Migration Workflow](docs/user-guide.md#migration-workflow) for details.

---

## Documentation

- **[Project Website](https://luigidemasi.github.io/camel-kit/)** — getting started, user guide, architecture
- [User Guide](docs/user-guide.md) — installation, workflows, commands
- [Architecture Guide](docs/architecture.md) — skills, MCP, extension points
- [Contributing](CONTRIBUTING.md) — dev setup, coding standards

---

## License

Apache License 2.0 — see [LICENSE](LICENSE).

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## Acknowledgments

Camel-Kit is built on ideas pioneered by the **[GitHub Spec-Kit](https://github.com/github/spec-kit)** team. We are grateful for their open approach to spec-driven development with AI assistants.
