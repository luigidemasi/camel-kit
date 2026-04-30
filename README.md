# Camel-Kit

<p align="center">
  <img src="camel-kit.gif" alt="Camel-Kit Logo" width="600"/>
</p>

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Website](https://img.shields.io/badge/Website-luigidemasi.github.io%2Fcamel--kit-orange)](https://luigidemasi.github.io/camel-kit/)

> Design, implement, and verify Apache Camel integrations with AI coding assistants.

Camel-Kit adds structured slash commands to your AI assistant that guide you through the full integration lifecycle — from designing the integration, through implementation and testing, to runtime verification. It works across multiple AI agents and produces production-ready Apache Camel routes.

**Inspired by [GitHub Spec-Kit](https://github.com/github/spec-kit)**, adapted for the Apache Camel ecosystem.

---

## The Workflow

```
Greenfield:   /camel-design  → /camel-plan → /camel-execute
                                                   ├── implements (camel-implement)
                                                   ├── validates (camel-validate)
                                                   ├── tests (camel-test)
                                                   └── verifies (camel-verify)

Migration:    /camel-migrate → /camel-plan → /camel-execute

Manual:       /camel-verify      (standalone runtime verification)
```

| Command | Purpose |
|---------|---------|
| `/camel-design` | Interactive design session — produces a Blueprint Reference Document (BRD) with Technical Design Documents (TDDs) |
| `/camel-plan` | Reviews approved design, creates a detailed implementation plan |
| `/camel-execute` | Orchestrated execution — implements, validates, tests, and verifies all flows |
| `/camel-migrate` | Migration from MuleSoft, legacy Camel, or JBoss Fuse to modern Camel |
| `/camel-verify` | Runtime verification — builds, starts, diagnoses errors, retries until the app runs |

---

## Installation

**Requires:** Java 17+, [JBang](https://www.jbang.dev/), AI model with **64K+ context window** (128K recommended)

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
  --gav io.github.luigidemasi:camel-kit-jbang-plugin:LATEST \
  --description "Design Apache Camel Integrations with AI"

# Then use via the camel CLI
camel kit init my-integration --ai bob
```

---

## Quick Start

```bash
# 1. Create a new project (choose your AI assistant)
camel-kit init my-integration --ai claude   # Anthropic Claude Code
camel-kit init my-integration --ai bob      # IBM Project Bob
camel-kit init my-integration --ai gemini   # Google Gemini CLI
camel-kit init my-integration --ai qwen     # Qwen
camel-kit init my-integration --ai opencode # OpenCode

# 2. Override configuration if needed
camel-kit init my-integration --ai claude -p "camel.version=4.18.0"

# 3. Open in your AI assistant
cd my-integration

# 4. Start designing
/camel-design
```

---

## Supported AI Agents

| Agent | Init Flag | Instruction File | MCP Config |
|-------|-----------|-----------------|------------|
| Anthropic Claude Code | `--ai claude` | `CLAUDE.md` | `.mcp.json` |
| IBM Project Bob | `--ai bob` | `custom_modes.yaml` + rules | `.bob/mcp.json` |
| Google Gemini CLI | `--ai gemini` | `GEMINI.md` | `.gemini/mcp.json` |
| Qwen | `--ai qwen` | `QWEN.md` | `.qwen/mcp.json` |
| OpenCode | `--ai opencode` | `AGENTS.md` | `.opencode/mcp.json` |

All agents use the same skills — camel-kit generates agent-specific instruction files that load the shared skill guides. The skills are the equalization layer.

---

## Key Features

- **3-phase orchestrated pipeline** — brainstorm the design, plan the implementation, execute with automated review
- **Multi-agent parity** — same skills work across 5 AI agents (Claude, Bob, Gemini, Qwen, OpenCode)
- **MCP integration** — real-time catalog queries, route validation, and security analysis via the Apache Camel MCP server
- **DataMapper** — automatic data transformation with two engines: XSLT for complex mappings, Groovy for simple ones
- **Runtime verification** — `/camel-verify` builds, starts, diagnoses errors, and retries until the app runs
- **Migration support** — migrate from MuleSoft, legacy Apache Camel, or JBoss Fuse with graph-accelerated analysis
- **MuleSoft graph analysis** — automatic parsing of Mule XML flows, sub-flows, connectors, and DataWeave scripts into a project graph for migration
- **Knowledge layer** — Apache Camel documentation searchable via MCP

---

## Documentation

- **[User Guide](docs/user-guide.md)** — workflows, migration, verification, DataMapper
- **[Command Reference](docs/commands.md)** — all slash commands and CLI options
- **[Architecture Guide](docs/architecture.md)** — skills internals, MCP, DataMapper pipeline (for contributors)
- **[Contributing](CONTRIBUTING.md)** — development setup, how to add skills

---

## License

Apache License 2.0 — see [LICENSE](LICENSE).

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## Acknowledgments

Camel-Kit is built on ideas pioneered by the **[GitHub Spec-Kit](https://github.com/github/spec-kit)** team. We are grateful for their open approach to spec-driven development with AI assistants.
