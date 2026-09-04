# Camel-Kit

<p align="center">
  <img src="camel-kit.gif" alt="Camel-Kit Logo" width="600"/>
</p>

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Website](https://img.shields.io/badge/Website-luigidemasi.github.io%2Fcamel--kit--web-orange)](https://luigidemasi.github.io/camel-kit-web/)

> Design, implement, and verify Apache Camel integrations with AI coding assistants.

Camel-Kit adds structured AI-agent workflows to your assistant that guide you through the full integration lifecycle — from designing the integration, through implementation and testing, to runtime verification. It works across multiple AI agents and produces production-ready Apache Camel routes.

**Inspired by [GitHub Spec-Kit](https://github.com/github/spec-kit)**, adapted for the Apache Camel ecosystem.

---

## The Workflow

```
Entry:        /camel-start         (routes migration, planning, execution, validation,
                                    debugging, or greenfield work based on context)

Greenfield:   /camel-brainstorm → /camel-plan → /camel-execute → /camel-validate
                                                      ├── implements (camel-implement)
                                                      ├── verifies (camel-verify, internal)
                                                      └── tests (camel-test)

Migration:    /camel-migrate    → /camel-plan → /camel-execute → /camel-validate

Utilities:    /camel-ship          (delegate to the local Ship controller)
              /camel-knowledge     (documentation Q&A)
              /camel-debug         (standalone troubleshooting)
```

| Command | Purpose |
|---------|---------|
| `/camel-start` | Entry point — routes migration sources to migrate, approved designs to plan, ready plans to execute, generated routes to validate, broken projects outside a pipeline to debug, and other work to brainstorm |
| `/camel-brainstorm` | Interactive design session — produces business requirements and a design spec |
| `/camel-migrate` | Migration from MuleSoft, Microsoft BizTalk, legacy Camel, or JBoss Fuse to modern Camel |
| `/camel-plan` | Reviews approved design, creates a detailed implementation plan with wave analysis for parallel execution |
| `/camel-execute` | Orchestrated execution — probes the environment, implements tasks in dependency waves, runs adversarial/spec/quality reviews, and performs applicable smoke and runtime verification |
| `/camel-validate` | Final static quality gate or standalone project check — covers schema, endpoints, quality, security, anti-patterns, and constitution compliance without modifying routes |
| `/camel-debug` | Standalone troubleshooting outside a pipeline for broken routes, build failures, startup errors, and runtime exceptions |
| `/camel-ship` | Thin harness entry point for the local Ship controller, which owns the end-to-end run and configurable oversight (`always`, `smart`, `never`) |
| `/camel-knowledge` | Documentation Q&A — semantic search over Apache Camel docs, CVE advisories, release notes, and component catalog |

[Command Reference →](docs/commands.md)

---

## Installation

### Prerequisites

| Requirement | Purpose | Install |
|-------------|---------|---------|
| Java 17+ | Runtime | [SDKMAN](https://sdkman.io/), [Adoptium](https://adoptium.net/), or your package manager |
| [JBang](https://www.jbang.dev/) | CLI launcher for camel-kit | See below |
| [Camel JBang](https://camel.apache.org/manual/camel-jbang.html) | `camel run`, `camel run --check` for route execution and validation | `jbang app install camel@apache/camel` |
| [Camel JBang test plugin](https://camel.apache.org/manual/camel-jbang.html) | `camel test run` for Citrus integration tests in the verify loop | `camel plugin add test` |
| [Docker](https://docs.docker.com/get-docker/) | Conditional: external-service probes and full Citrus/Testcontainers verification | Install Docker Engine or Docker Desktop |

Camel JBang and its test plugin are needed for the complete execution and verification loops. Docker-dependent checks are reported as skipped when Docker is unavailable; design, planning, and checks that do not need containers still work.

The optional Ship controller currently requires Linux, Pi, and Node, and its deterministic validation supports Camel Main projects with YAML DSL routes. The maintained versions are Pi `0.84.2` or `0.83.0` with Node `22.22.2`; unverified versions run only with `--accept-experimental`, while explicitly incompatible versions remain rejected. See [`camel-ship`](docs/commands.md#camel-ship) for the complete contract.

`camel-kit init` checks Java, JBang, Camel JBang, and the Camel test plugin. Missing tools produce warnings but don't block initialization; Docker and Ship prerequisites are checked when those workflows run.

### Release channels

This README describes the current `main` line, version `0.3.2-SNAPSHOT`. Choose the channel whose surface you need:

| Channel | Install source | Supported surface |
|---------|----------------|-------------------|
| Stable `0.3.1` | Maven Central | The Camel plugin exposes `camel kit init`; init supports `bob` (default), `gemini`, and `claude`. It predates the current workflow and agent surface. |
| Current `0.3.2-SNAPSHOT` | GitHub JBang catalog or a source build | Standalone CLI commands `init`, `doctor`, `doc`, `graph`, `plan`, `nextId`, and `ship`, plus all nine AI targets documented below. A source-built Camel plugin exposes the same commands under `camel kit`. |

Do not use `LATEST` when following current-main instructions: Maven Central currently resolves it to stable `0.3.1`.
Hosted snapshots are mutable and may lag `main` until the next deployment; build from source for an exact revision.

### Current snapshot (standalone JBang)

```bash
# Install JBang (if not already installed)
curl -Ls https://sh.jbang.dev | bash -s - app setup        # Linux/macOS
iex "& { $(iwr -useb https://ps.jbang.dev) } app setup"    # Windows PowerShell

# Install the current snapshot globally
jbang app install camel-kit@luigidemasi/camel-kit

# Verify
camel-kit --help
```

### Run the current snapshot without installing

```bash
jbang run camel-kit@luigidemasi/camel-kit init my-integration --ai claude
```

### Stable 0.3.1 (Camel JBang plugin)

If you already use [Camel JBang](https://camel.apache.org/manual/camel-jbang.html), install camel-kit as a plugin:

```bash
camel plugin add kit \
  --gav io.github.luigidemasi:camel-jbang-plugin-kit:0.3.1 \
  --description "Design Apache Camel Integrations with AI"

# Stable 0.3.1 provides init for bob, gemini, and claude
camel kit init my-integration --ai claude
```

### Build from Source (development version)

Build from source to use the current snapshot without waiting for the published snapshot artifact:

```bash
git clone https://github.com/luigidemasi/camel-kit.git
cd camel-kit
./mvnw clean install -DskipTests

# Install the development version globally via JBang
jbang app install --name camel-kit --force \
  camel-kit-main/src/main/jbang/main/CamelKit.java

# Or install the matching Camel plugin from the local Maven repository
camel plugin add kit \
  --gav io.github.luigidemasi:camel-jbang-plugin-kit:0.3.2-SNAPSHOT \
  --description "Design Apache Camel Integrations with AI"

# Verify
camel-kit --help
```

To also build the [Knowledge MCP server](https://github.com/luigidemasi/camel-kit-knowledge) (documentation search, CVE tracking, component catalog):

```bash
git clone https://github.com/luigidemasi/camel-kit-knowledge.git
cd camel-kit-knowledge
./mvnw clean install -DskipTests
```

---

## Quick Start

The examples below use the current `0.3.2-SNAPSHOT` channel.

```bash
# 1. Create a new project (choose your AI assistant)
camel-kit init my-integration             # IBM Bob 2 (default)
camel-kit init my-integration --ai claude   # Anthropic Claude Code
camel-kit init my-integration --ai bob      # IBM Bob 1 legacy
camel-kit init my-integration --ai bob2     # IBM Bob 2
camel-kit init my-integration --ai gemini   # Google Gemini CLI
camel-kit init my-integration --ai codex    # OpenAI Codex CLI
camel-kit init my-integration --ai copilot  # GitHub Copilot CLI
camel-kit init my-integration --ai pi       # Pi
camel-kit init my-integration --ai qwen     # Qwen
camel-kit init my-integration --ai opencode # OpenCode

# 2. Override configuration if needed
camel-kit init my-integration --ai claude -p "camel.main.version=4.18.4"

# 3. Open in your AI assistant
cd my-integration

# 4. Start designing
/camel-start

# For GitHub Copilot CLI, ask Copilot: "Use the /camel-start skill."
# Run /skills list if you need to inspect available project skills.

# For Codex CLI, trust the repository, then run $camel-start.
# Use /skills to inspect project skills and /mcp to inspect MCP servers.

# For Pi, install the MCP adapter, trust the project, then run /skill:camel-start.
# pi install npm:pi-mcp-adapter@2.11.0
```

---

## Supported AI Agents

`init` generates the row selected by `--ai`; these are alternative target layouts, not one combined project tree.

| Agent | Init Flag | Primary Generated Assets | MCP Config |
|-------|-----------|--------------------------|------------|
| Anthropic Claude Code | `--ai claude` | `CLAUDE.md` + `.claude/commands/` + `.claude/skills/` | `.mcp.json` |
| IBM Bob 1 legacy | `--ai bob` | `.bob/custom_modes.yaml` + rules + monolithic gate skills | `.bob/mcp.json` |
| IBM Bob 2 (default) | `--ai bob2` | `.bob/custom_modes.yaml` + rules + shared `.bob/skills/` + scoped `.bob/agents/` + `.bob/personas/` | `.bob/mcp.json` |
| Google Gemini CLI | `--ai gemini` | `GEMINI.md` + `.gemini/commands/` + `.gemini/skills/` + `.gemini/agents/` | `.gemini/settings.json` |
| OpenAI Codex CLI | `--ai codex` | `AGENTS.md` + `.agents/skills/` + `.codex/agents/` | `.codex/config.toml` |
| GitHub Copilot CLI | `--ai copilot` | `.github/copilot-instructions.md` + `.github/agents/` + `.github/skills/` | `.github/mcp.json` |
| Pi | `--ai pi` | `AGENTS.md` + `.pi/skills/` + `.pi/prompts/` + guard extension/policy | `.mcp.json` via `pi-mcp-adapter` |
| Qwen | `--ai qwen` | `QWEN.md` + `.qwen/commands/` + `.qwen/skills/` + `.qwen/agents/` + `.qwen/camel-kit-personas/` | `.qwen/settings.json` |
| OpenCode | `--ai opencode` | `AGENTS.md` + `.opencode/commands/` + `.opencode/skills/` + `.opencode/agents/` + `.opencode/camel-kit-personas/` | `opencode.json` (default) |

Most targets receive the shared skill guides plus agent-specific traits. Bob 1 legacy is the exception: it runs without subagents and replaces the shared pipeline skills with monolithic gate variants. Bob 2 keeps the shared skills and uses native subagents. Codex CLI and GitHub Copilot CLI use native project skills and custom agents instead of Camel-Kit slash-command stubs. Pi uses native project skills and prompt templates, with MCP provided by `pi-mcp-adapter`. [Architecture Guide →](docs/architecture.md)

---

## Key Features

### Pipeline

- **4-step orchestrated pipeline** — brainstorm or migrate, plan, execute, then validate. Execute performs the environment probe, dependency-wave implementation, adversarial/spec/quality reviews, and applicable smoke and internal runtime verification before the final static validation gate. [Learn more →](docs/user-guide.md)
- **Local Ship controller** — `/camel-ship` delegates to the configured `camel-kit ship` or `camel kit ship` command. The controller, rather than the AI harness, owns run state, stage transitions, validation evidence, and guarded publication, with `always`, `smart`, and `never` oversight. [Learn more →](docs/commands.md#camel-ship)
- **Environment probe** — validates the target environment (dependency resolution, Docker services, runtime startup) before implementation begins. Mechanical failures are auto-fixed; architectural failures trigger re-planning. [Learn more →](docs/architecture.md)
- **Wave analysis** — the plan analyzer uses structured task metadata, logical dependencies, and file overlap to group independent tasks into parallel execution waves.
- **Deterministic staleness detection** — `doc check`, `doc stale`, and `doc unstale` CLI commands manage pipeline artifact validity via structured YAML frontmatter, with `--cascade` for automatic downstream propagation. [Learn more →](docs/commands.md)

### Runtime Verification

- **3-phase verification loop** — build, run Citrus integration tests with Testcontainers, report results. Error taxonomy classifies failures and routes fixes to the right skill (implementation, test re-generation, or re-planning). [Learn more →](docs/user-guide.md)

### Migration

- **Multi-platform migration** — migrate from MuleSoft 3.x/4.x, Microsoft BizTalk, legacy Apache Camel 2.x/3.x, or JBoss Fuse to modern Camel 4.x with YAML DSL. [Learn more →](docs/user-guide.md)

### Graph Intelligence

- **9 parsers + 2 post-processors** — build a queryable property graph of your codebase covering Java classes, Camel routes (XML, YAML, Java DSL, Groovy), Maven dependencies, configuration properties, MuleSoft flows, DataWeave scripts, and BizTalk orchestrations. [Learn more →](docs/architecture.md)
- **DI-aware analysis** — detects `@Inject`, `@Autowired`, `@Value`, `@ConfigProperty`, `@Component`, `@Service` annotations and traces dependencies across interface boundaries. Inspired by [Chinthareddy, "Reliable Graph-RAG for Codebases"](https://arxiv.org/abs/2601.08773) (2026). [Learn more →](docs/architecture.md)
- **Migration context** — `graph migration-context <routeId>` performs a bounded local graph traversal (breadth-first, depth 3 by default, capped at 50 related nodes) and reports detected routes, components, services, artifacts, properties, and inferred-node warnings. It neither queries the Knowledge MCP nor guarantees a complete dependency chain. [Learn more →](docs/commands.md)
- **PropertyBindingSupport analysis** — understands Camel's `#class:`, `#bean:`, `#autowired` property syntax that instantiates and wires beans from `application.properties`. [Learn more →](docs/architecture.md)

### Knowledge & MCP

- **MCP integration** — real-time catalog queries, route validation, security analysis, documentation lookup, and Citrus test-generation metadata via MCP servers. [Learn more →](docs/architecture.md)
- **Knowledge layer** — hybrid BM25 + vector search over Apache Camel documentation, component catalogs, release notes, and CVE advisories ingested from `apache/camel-website`, with best-effort NVD enrichment. The target-specific MCP tool contract is defined in the workflow manifest. [Learn more →](docs/architecture.md)
- **DataMapper** — automatic data transformation with two engines: XSLT for complex schema-driven mappings, Groovy for simple field-level transformations. [Learn more →](docs/architecture.md)

### Multi-Agent

- **9 AI targets** — Camel-Kit supports Claude Code, IBM Bob 1 legacy, IBM Bob 2, Gemini CLI, OpenAI Codex CLI, GitHub Copilot CLI, Pi, Qwen, and OpenCode. Agent-specific traits adapt the shared skills to each target; Bob 1 uses monolithic gate variants instead. [Learn more →](docs/architecture.md)

---

## Documentation

- **[User Guide](docs/user-guide.md)** — workflows, migration, verification, DataMapper
- **[Command Reference](docs/commands.md)** — CLI options, AI-agent workflows, graph subcommands
- **[Architecture Guide](docs/architecture.md)** — skills, MCP, graph intelligence, pipeline internals
- **[Contributing](CONTRIBUTING.md)** — development setup, how to add skills

Workflow contributors should treat `camel-kit-core/src/main/resources/workflow/camel-kit-workflow.yaml` as the source of truth for command, skill, stage, artifact, and MCP tool metadata before updating generated templates or reference docs.

---

## License

Apache License 2.0 — see [LICENSE](LICENSE).

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## Acknowledgments

Camel-Kit is built on ideas pioneered by the **[GitHub Spec-Kit](https://github.com/github/spec-kit)** team. We are grateful for their open approach to spec-driven development with AI assistants.
