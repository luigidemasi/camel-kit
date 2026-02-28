# Camel-Kit

<p align="center">
  <img src="camel-kit.gif" alt="Camel-Kit Logo" width="600"/>
</p>

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

> Design and migrate Apache Camel integrations with AI coding assistants.

Camel-Kit adds structured slash commands to your AI assistant (Claude Code, IBM Project Bob, Gemini CLI) that guide you through designing, implementing, and testing Apache Camel routes — whether you are starting from scratch or migrating from another platform.

**Inspired by [GitHub Spec-Kit](https://github.com/github/spec-kit)**, adapted for the Apache Camel ecosystem.

---

## Installation

**Requires:** Java 17+, [JBang](https://www.jbang.dev/)

```bash
# Install JBang (if not already installed)
curl -Ls https://sh.jbang.dev | bash -s - app setup        # Linux/macOS
iex "& { $(iwr -useb https://ps.jbang.dev) } app setup"    # Windows PowerShell

# Install camel-kit globally
jbang app install camel-kit@luigidemasi/camel-kit

# Verify
camel-kit --help
```

**Local build:**

```bash
mvn install
jbang app install --force camel-kit@./
```

---

## Run without installing

No global install required — JBang can run camel-kit directly, caching dependencies automatically:

```bash
# From the published catalog
jbang run camel-kit@luigidemasi/camel-kit init my-project --ai claude

# From a local clone
jbang run camel-kit-main/src/main/jbang/main/CamelKit.java init my-project --ai claude

# From a local SNAPSHOT build (after mvn install)
jbang -Dcamel.kit.version=0.3.1-SNAPSHOT \
  run camel-kit-main/src/main/jbang/main/CamelKit.java init my-project --ai claude
```

JBang caches the resolved JARs in `~/.jbang/cache/` so subsequent runs are fast.

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

## Migrate an existing project from MuleSoft Mule to Apache Camel

```bash
camel-kit init my-migration --ai claude
cd my-migration
```

Then in your AI assistant:

```
/camel-migrate path/to/mule-project/
```

The command detects the Mule version, analyses the flows, and produces the same BRD + TDD files that the greenfield workflow produces — so `/camel-implement`, `/camel-validate`, and `/camel-test` work without any changes.

See [Migration Workflow](docs/user-guide.md#migration-workflow) for details.

---

## Supported AI assistants

| Assistant | Status | Commands folder |
|-----------|--------|----------------|
| [Claude Code](https://docs.anthropic.com/en/docs/claude-code) | Available | `.claude/commands/` |
| [IBM Project Bob](https://www.ibm.com/products/bob) | Available | `.bob/commands/` |
| [Gemini CLI](https://github.com/google-gemini/gemini-cli) | Available | `.gemini/commands/` |
| GitHub Copilot | Planned | — |
| Cursor | Planned | — |

---

## Requirements

- Java 17+
- [JBang](https://www.jbang.dev/)
- [Camel JBang](https://camel.apache.org/manual/camel-jbang.html) (to run generated routes)
- Docker / Podman (for Citrus tests with Testcontainers)

---

## Documentation

- [User Guide](docs/user-guide.md)
- [Command Reference](docs/commands.md)
- [MCP Tools Reference](docs/mcp-tools-reference.md)
- [Skills Architecture](docs/skills-architecture.md)

---

## License

Apache License 2.0 — see [LICENSE](LICENSE).

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## Acknowledgments

Camel-Kit is built on ideas pioneered by the **[GitHub Spec-Kit](https://github.com/github/spec-kit)** team. We are grateful for their open approach to spec-driven development with AI assistants.
