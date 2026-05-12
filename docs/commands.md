# Camel-Kit Command Reference

This document is the reference for all Camel-Kit commands: the `camel-kit` CLI and the slash commands used inside AI coding assistants.

## Table of Contents

- [CLI Commands](#cli-commands)
  - [camel-kit init](#camel-kit-init)
- [Slash Commands](#slash-commands)
  - [/camel-brainstorm](#camel-brainstorm)
  - [/camel-plan](#camel-plan)
  - [/camel-execute](#camel-execute)
  - [/camel-migrate](#camel-migrate)
  - [/camel-verify](#camel-verify)
- [Command Cheat Sheet](#command-cheat-sheet)

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
| `--ai`, `-a` | `bob` | AI coding assistant to configure (`bob`, `gemini`, `claude`, `qwen`, `opencode`) |
| `--camel-version`, `-v` | latest version | Apache Camel version to target |
| `--citrus-version` | `4.9.2` | Citrus Framework version for test schemas |
| `--here` | `false` | Initialize in current directory |
| `--no-fetch` | `false` | Skip external catalog fetching |
| `-p`, `--property` | -- | Override a config property (repeatable). Example: `-p "camel.version=4.18.0"` |
| `-c`, `--config` | `~/.camel-kit/config.properties` | Path to a custom config properties file |
| `--source-platform` | `auto` | Source platform for migration: `mulesoft`, `camel`, `biztalk`, `auto` |
| `--force` | `false` | Overwrite existing project without prompting (skips overwrite detection) |
| `--silent` | `false` | Suppress all output (no banner, no TUI, no progress, no summary) -- useful for CI/scripted environments |
| `-V`, `--version` | -- | Print camel-kit version and exit |

**Examples:**

```bash
# Create new project for IBM Project Bob
camel-kit init my-integration --ai bob

# Create new project for Gemini CLI
camel-kit init my-integration --ai gemini

# Create new project for Claude Code
camel-kit init my-integration --ai claude

# Create new project for Qwen
camel-kit init my-integration --ai qwen

# Create new project for OpenCode
camel-kit init my-integration --ai opencode

# Use a specific Camel version
camel-kit init my-integration --camel-version 4.18.0

# Initialize in current directory
camel-kit init --here --ai bob

# Override config properties via CLI
camel-kit init my-integration --ai claude -p "camel.version=4.18.0"

# Override multiple properties
camel-kit init my-integration --ai claude -p "camel.version=4.18.0" -p "quarkus.bom.version=3.30.0"

# Use a custom config file
camel-kit init my-integration --ai claude -c /path/to/my-config.properties

# Explicitly declare MuleSoft source platform
camel-kit init my-integration --ai claude --source-platform mulesoft

# Explicitly declare BizTalk source platform
camel-kit init my-integration --ai claude --source-platform biztalk

# Skip catalog fetch (faster)
camel-kit init my-integration --ai bob --no-fetch

# Overwrite an existing project
camel-kit init my-integration --ai claude --force

# Check version
camel-kit --version
```

**Prerequisite check:**

On startup, `init` checks for required tools and reports their status:

```text
Prerequisites:
  Java 17+          ✓ (21.0.3)
  JBang             ✓ (0.136.0)
  Camel JBang       ✓ (4.18.1)
  Camel test plugin ✗ (not found — /camel-verify will skip test phase)
```

The check is non-blocking -- it warns but never fails the init. Design and planning work without Camel JBang; only execution and verification need it.

**Overwrite detection:**

If the target directory already contains `AGENTS.md` or `.camel-kit/`, init warns and exits:

```text
⚠ Project already initialized
  Directory: /path/to/my-integration
  Found:     AGENTS.md
  Found:     .camel-kit/

  To overwrite: --force
  Example:      camel-kit init my-integration --ai claude --force
```

Use `--force` to overwrite an existing project.

**TUI experience:**

On terminals that support a native image protocol (Kitty, iTerm2, Sixel), `camel-kit init` displays a split-screen TUI while the project is being created: the logo on the left, a live task list with animated spinners and green ticks on the right. The TUI exits automatically when all tasks complete. Falls back to coloured inline output on unsupported terminals.

**Configuration Override:**

`camel-kit init` reads configuration from a 3-layer cascade. Later layers override earlier ones:

| Priority | Source | Description |
|----------|--------|-------------|
| 1 (lowest) | Built-in `distribution.properties` | Bundled defaults inside the camel-kit JAR |
| 2 | `~/.camel-kit/config.properties` (or `-c path`) | User-level config file. Created manually by the user; not required. Override the default path with `-c`. |
| 3 (highest) | `-p key=value` CLI flags | Per-invocation overrides. Repeatable. |

Any property from `distribution.properties` can be overridden at layers 2 or 3. Common overrides:

| Property | Default | Description |
|----------|---------|-------------|
| `camel.version` | `4.20.0` | Apache Camel version for generated projects |
| `springboot.bom.version` | `4.20.0` | Spring Boot BOM version |
| `quarkus.bom.version` | `3.33.0` | Quarkus platform BOM version |
| `camel.mcp.version` | `4.20.0` | Camel MCP server version |

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
├── docs/
│   └── flows/                   # Flow definitions
├── .camel-kit/
│   ├── config.yaml              # Project configuration
│   ├── project-graph.json       # Auto-detected project graph
│   ├── .cache/                  # Downloaded catalogs and schemas
│   │   ├── components-{version}.json
│   │   ├── kamelets-{version}.json
│   │   ├── camelYamlDsl-{version}.json
│   │   └── citrus/{version}/    # Citrus JSON schemas
│   └── templates/               # Reference templates
├── .mcp.json                    # Claude Code MCP configuration
├── .bob/mcp.json                # IBM Bob MCP configuration
├── .gemini/mcp.json             # Gemini CLI MCP configuration
├── .qwen/mcp.json               # Qwen MCP configuration
└── .opencode/mcp.json           # OpenCode MCP configuration
```

The MCP configuration file created depends on the `--ai` option chosen.

### camel-kit graph

Project graph analysis and query commands. The project graph is automatically detected during `camel-kit init` and stored in `.camel-kit/project-graph.json`. It contains nodes (routes, components, services, artifacts, properties, processors) and edges (dependencies, references, configurations) extracted from source code, build files, and configuration files.

**Usage:**

```bash
camel-kit graph <subcommand> [options]
```

**Subcommands:**

| Subcommand | Description |
|------------|-------------|
| `stats` | Show graph statistics (node counts by type, edge counts by type) |
| `find <query>` | Find nodes by name, type, or pattern |
| `neighbors <nodeId>` | Show direct neighbors of a node |
| `impact <nodeId>` | Show all downstream dependencies of a node |
| `route-topology <routeId>` | Show the topology of a specific route |
| `project-norms` | Analyze project-wide patterns and conventions |
| `project-context` | Collect comprehensive project context for analysis |
| `route-context <routeId>` | Collect route-specific context with component details |
| `migration-context <routeId>` | Collect migration context for a route with expanded graph traversal |

**migration-context command:**

The `migration-context` command performs BFS expansion from a route through interface boundaries (REST endpoints, queues, beans, config) to collect the complete migration context.

```bash
camel-kit graph migration-context <routeId> [--depth N]
```

**Parameters:**

| Parameter | Default | Description |
|-----------|---------|-------------|
| `<routeId>` | required | Route ID to analyze (without `route:` prefix) |
| `--depth N` | `3` | BFS expansion depth for graph traversal |

**Output format:**

Returns structured JSON with the complete context needed for migration analysis:

```json
{
  "route": "processOrders",
  "runtime": "spring-boot",
  "components": ["kafka", "http", "bean"],
  "routes": [
    {
      "id": "processOrders",
      "from": "kafka:orders",
      "file": "src/main/resources/routes/orders.camel.yaml"
    }
  ],
  "services": [
    {
      "class": "com.example.OrderService",
      "bean": true,
      "beanName": "orderService"
    }
  ],
  "artifacts": [
    {
      "groupId": "org.apache.camel",
      "artifactId": "camel-kafka",
      "version": "4.14.4"
    }
  ],
  "properties": [
    {
      "key": "camel.component.kafka.brokers",
      "value": "localhost:9092",
      "edgeType": "CONFIGURES",
      "target": "component:kafka"
    }
  ],
  "warnings": [
    {
      "type": "synthetic-node",
      "name": "UnknownService",
      "reason": "Node was inferred, not parsed from source"
    }
  ]
}
```

**Use case:**

The `/camel-migrate` skill uses this command during migration analysis:

1. Graph expansion identifies all routes, components, services, and artifacts connected to the target route
2. Component list is passed to `camel_docs_component` MCP tool for documentation lookup
3. Migration skill receives both structural context (from graph) and semantic context (from MCP) for accurate migration planning

**Example:**

```bash
# Analyze the processOrders route with default depth
camel-kit graph migration-context processOrders

# Expand to depth 5 for complex route dependencies
camel-kit graph migration-context processOrders --depth 5
```

---

## Slash Commands

These commands are used inside your AI coding assistant after project initialization. There are six user-invocable slash commands.

Four additional skills (`/camel-implement`, `/camel-verify`, `/camel-test`, `/camel-knowledge`) are internal -- they are orchestrated automatically by `/camel-execute` and should not be run directly.

---

### /camel-brainstorm

**Purpose:** Interactive design session that turns integration ideas into fully formed design specs through collaborative dialogue.

**When to use:** Starting any new integration project. For migrations, use `/camel-migrate` instead.

**Produces:**
- `.camel-kit/business-requirements.md` (BRD)
- `.camel-kit/flows/{flow-name}/{flow-name}.tdd.md` (one TDD per flow)
- `docs/constitution.md`

**Example:**

```
/camel-brainstorm
```

**How it works:**

1. **Detect project type** -- greenfield or migration (based on keywords like "create", "build" vs "migrate", "convert")
2. **Load context** -- reads `docs/constitution.md` and `.camel-kit/config.yaml` if they exist
3. **Run interview or discovery** -- Socratic interview (one question at a time) for greenfield; artifact scanning and confirmation for migration
4. **Select Camel version** -- presents available versions for selection
5. **Design flows** -- for each flow, verifies components, EIPs, data formats, and languages against the MCP catalog. Asks conditional questions only when relevant:
   - Circuit breaker configuration (if HTTP components detected)
   - Idempotent consumer (if message broker components detected)
   - Transaction boundaries (if multiple sinks detected)
6. **Assemble design spec** -- compiles the full BRD and TDD files
7. **Self-review** -- scans for placeholders, contradictions, unverified components
8. **User approval** -- presents the spec and waits for explicit approval
9. **Automatic transition** -- after approval, invokes `/camel-plan` automatically (the user does not need to run it manually)

**MCP tools used:**

- `camel_catalog_component` -- verify component exists
- `camel_catalog_eip` -- verify EIP exists
- `camel_catalog_dataformat` -- verify data format exists
- `camel_catalog_language` -- verify expression language exists

---

### /camel-plan

**Purpose:** Create a detailed implementation plan from an approved design spec.

**When to use:** After `/camel-brainstorm` has produced an approved design spec. Usually invoked automatically by `/camel-brainstorm`, but can also be run directly if a design spec already exists.

**Produces:**
- `docs/implementation-plan.md`

**Example:**

```
/camel-plan
```

**How it works:**

1. **Scope check** -- if the spec covers many independent subsystems (10+ flows), suggests breaking into separate plans
2. **Load task template** -- selects the appropriate template (greenfield, migration, or testing)
3. **Decompose into tasks** -- breaks the spec into bite-sized implementation tasks, each with:
   - Exact file paths to create or modify
   - Guide files to load and MCP tools to call
   - Two-stage review specification (spec compliance, then code quality)
   - Verification commands with expected output
4. **Self-review** -- checks spec coverage, scans for placeholders, validates guide references
5. **User approval** -- presents the plan and waits for explicit approval
6. **Automatic transition** -- after approval, invokes `/camel-execute` automatically

The plan is a recipe, not the meal -- it contains instructions on how to generate code, not the generated code itself.

---

### /camel-execute

**Purpose:** Execute the approved implementation plan by dispatching subagents per task with two-stage review after each.

**When to use:** After `/camel-plan` has produced an approved implementation plan. Usually invoked automatically by `/camel-plan`, but can also be run directly if a plan already exists.

**Produces:**
- Route YAML files (`*.camel.yaml`)
- `application.properties`
- `pom.xml` dependencies
- XSLT/DataMapper transformations
- Docker Compose files (if external services needed)
- Citrus test files
- Verification report

**Example:**

```
/camel-execute
```

**How it works:**

1. **Read plan** -- extracts all tasks from `docs/implementation-plan.md`
2. **Per-task loop** (autonomous, uninterrupted -- the user approved the entire plan):
   - **Dispatch implementer** -- fresh subagent with full task context, guide paths, and MCP parameters
   - **Spec compliance review** -- verifies the generated artifacts match the design spec exactly
   - **Code quality review** -- checks constitution rules, security, and anti-patterns
   - If review fails, the implementer fixes and re-submits until both reviews pass
3. **Cross-cutting review** -- after all tasks complete, reviews all generated routes together for consistency
4. **Verification phase** -- dispatches `/camel-verify` internally (build, start, diagnose, fix). Verification is optional -- failure does not block completion
5. **Completion summary** -- reports task status, review results, and verification outcome

After `/camel-execute` completes, the pipeline continues to `/camel-validate` as the final quality gate.

**Agent-specific execution:**

| Agent | Execution Model |
|-------|----------------|
| Claude Code | Dispatches fresh subagents per task (isolated context) |
| IBM Project Bob | Switches between custom modes (brainstorm, plan, implement, validate, test) |
| Gemini CLI, Qwen, OpenCode | Inline execution within the same session |

**Orchestrated internal skills:**

During execution, `/camel-execute` dispatches these internal skills as needed. They are never invoked directly by the user:

| Internal Skill | Role |
|---|---|
| `/camel-implement` | Generate Camel YAML DSL routes from TDD specifications |
| `/camel-verify` | Runtime verification loop: build, start, diagnose errors, apply fixes |
| `/camel-test` | Generate Citrus integration tests |
| `/camel-knowledge` | Query documentation for component support and guidance |

---

### /camel-migrate

**Purpose:** Migrate an existing integration from another platform to Apache Camel.

**When to use:** When you have an existing MuleSoft, Camel 2.x/3.x, JBoss Fuse, or BizTalk project to migrate.

**Produces:** Same as `/camel-brainstorm` -- BRD and TDD files, plus a design spec tailored to the migration.

**Example:**

```
/camel-migrate
```

**Supported source platforms:**

| Platform | Versions | Detection method |
|----------|----------|-----------------|
| MuleSoft Mule | 3.x, 4.x | XML namespace `mulesoft.org`, `pom.xml` groupId `org.mule` / `com.mulesoft` |
| Apache Camel | 2.x, 3.x | `pom.xml` with `org.apache.camel` dependencies (older version) |
| JBoss Fuse | 6.x, 7.x | `pom.xml` with `org.jboss.fuse` or `fuse-` BOMs |
| Microsoft BizTalk | 3.x, 4.x | XML namespace `schemas.microsoft.com/BizTalk`, `.odx` / `.btm` / `.btp` files |

**Graph-accelerated analysis:**

MuleSoft migrations benefit from automatic project graph analysis. The graph detects MuleSoft XML files via namespace sniffing (`mulesoft.org/schema/mule`) and parses all flows, sub-flows, connectors, endpoints, transforms, error handlers, and DataWeave scripts into graph nodes. The migration skill gets instant flow topology -- connectors used per flow, sub-flow call chains, DataWeave complexity -- without manual XML deep-dives. DataWeave `.dwl` files are analyzed for function definitions, field access patterns, and content types to identify complex transformations that need manual attention.

**How it works:**

This is a shortcut into `/camel-brainstorm` with the project type pre-set to **migration**. It runs a two-phase analysis:

**Phase 1 -- Discovery and confirmation:**

1. Scans all project artifacts (XML, build files, properties, docs, Docker/K8s, source, tests)
2. Detects vendor and version from the scanned content
3. Builds a pre-populated analysis summary (purpose, SLA, security, failure behaviour, deployment target)
4. Confirms the summary with the user; only asks about genuine gaps
5. Walks through each migration concern one at a time (deprecated components, platform changes, DataWeave conversions, proprietary connectors)

**Phase 2 -- Design:**

1. Maps each source component to its catalog-verified Camel equivalent
2. Converts DataWeave transformations into TDD field mapping tables
3. Asks only what the source artifacts cannot answer
4. Produces BRD and one TDD per flow

After both phases, the pipeline continues the same as greenfield: version selection, design assembly, user approval, then automatic transition to `/camel-plan`.

**Mule-to-Camel component mapping highlights:**

| Mule Component | Camel Equivalent | Notes |
|---|---|---|
| HTTP Listener | `platform-http` | Port configured via `application.properties` |
| HTTP Request | `http` / `https` | Outbound HTTP calls |
| JMS / ActiveMQ | `jms` / `activemq` | Drop-in replacement |
| Database (select/insert) | `sql` / `jdbc` | `sql` for simple queries, `jdbc` for batch |
| File / FTP / SFTP | `file` / `ftp` / `sftp` | Direct equivalents |
| Kafka | `kafka` | Direct equivalent |
| Choice Router | `choice` EIP | Content-Based Router |
| DataWeave Transform | XSLT via `xslt-saxon` | DataMapper pattern |
| Scatter-Gather | `multicast` EIP | Parallel dispatch |
| For Each | `split` EIP | Collection iteration |
| Sub Flow / Flow Reference | `direct:` route | Internal routing |
| Set Payload / Set Variable | `setBody` / `setHeader` EIP | Mule variables map to Camel headers |
| Email (SMTP/IMAP/POP3) | `smtp` / `imap` / `pop3` (+ `s` variants) | All from `camel-mail` artifact |
| Salesforce Connector | `salesforce` | Full API support |
| REST Consumer (RAML) | `rest` + `openapi` | Import OpenAPI spec |

**Proprietary connectors (require user decision):**

| Mule Connector | Suggested Alternatives |
|---|---|
| Anypoint MQ | Amazon SQS, Azure Service Bus, RabbitMQ, ActiveMQ |
| Object Store | Infinispan, Redis, Caffeine cache, JPA |
| SAP Connector | `camel-sap` (if licensed), SAP REST/SOAP APIs |
| Workday | Workday REST API via `camel-http` |
| NetSuite | NetSuite REST/SOAP APIs via `camel-http` |
| ServiceNow | `camel-servicenow` (check API coverage) |

For connectors with no direct equivalent, the command stops and asks the user before proceeding.

**BizTalk-to-Camel adapter mapping highlights:**

| BizTalk Adapter | Camel Equivalent | Notes |
|---|---|---|
| FILE | `file` | Direct drop-in replacement |
| FTP / FTPS | `ftp` / `ftps` | Connection pooling supported |
| SFTP | `sftp` | SSH key authentication |
| SQL | `sql` / `jdbc` | `sql` for queries, `jdbc` for batch |
| WCF-BasicHttp | `cxf` | SOAP 1.1/1.2 support |

---

### /camel-verify

> **Internal skill** -- dispatched automatically by `/camel-execute` as Step 3.5. Not intended for standalone use. For quality checks after the pipeline completes, use `/camel-validate`.

**Purpose:** Runtime verification feedback loop that builds, starts, diagnoses errors, applies fixes, and retries until the application runs correctly or the iteration limit is reached.

**When to use:** This skill is invoked internally by `/camel-execute` during its verification phase. Users should not run it directly; use `/camel-validate` for post-pipeline quality validation.

**Produces:**
- Verification report (printed to console)
- Fixes applied to project files (pom.xml, route YAML, properties, etc.)

**Example:**

```
/camel-verify
```

**How it works (3-phase loop):**

1. **Build Verification** -- compiles the project with `./mvnw` (skipped for JBang runtime). Classifies build errors and auto-fixes (missing dependencies, version conflicts) or routes to `camel-implement`/`camel-validate`. Up to 15 iterations.
2. **Test Verification** -- runs Citrus integration tests via `camel test run`. Citrus tests are self-contained: Testcontainers start external services, `camel:jbang:run` starts the Camel integration, send/receive actions validate behavior. Classifies test failures and routes to `camel-implement` (route fix), `camel-test` (test re-generation), or self-repair. Up to 15 iterations.
3. **Report** -- structured summary with phase outcomes, fixes applied, and issues found.

**Error classification (14 patterns):**

| Category | Examples | Fix Target |
|---|---|---|
| Missing dependency | `ClassNotFoundException`, missing Camel component | Self-repair (add to pom.xml) |
| Third-party dependency | `cannot find symbol` for non-Camel class | Self-repair |
| Version incompatibility | `NoSuchMethodError`, `AbstractMethodError` | Self-repair (align BOM) |
| Build tool | Unknown lifecycle phase, missing plugin | Escalate to user |
| Route creation | `FailedToCreateRouteException` | `/camel-implement` (re-generate route) |
| Unknown component | `NoSuchEndpointException` | `/camel-implement` |
| Wrong endpoint options | `ResolveEndpointFailedException` | `/camel-validate` |
| Missing bean | `NoSuchBeanException` | `/camel-implement` |
| Injection failure | `UnsatisfiedDependencyException` | `/camel-implement` |
| External service | `Connection refused` | Self-repair (restart Docker service) |
| Quarkus augmentation | `io.quarkus.builder.BuildException` | Escalate to user |
| Expression failure | `ExpressionEvaluationException` | `/camel-implement` |
| Type conversion | `TypeConversionException` | `/camel-implement` |
| XSLT transformation | `XPathException`, `TransformerException` | `/camel-implement` |

Each phase has an independent iteration budget of 15 attempts. If the same error recurs after a fix attempt, the loop short-circuits and escalates to the user. Unclassified errors are also escalated with the raw log output.

During **Test Verification** (Phase 2), test failures may also route to `/camel-test` for test re-generation. For persistent **architectural** failures, the loop triggers the **re-plan** flow per `camel-execute/guides/re-plan-loop.md`.

---

## Command Cheat Sheet

```bash
# CLI
camel-kit init my-project --ai claude

# Greenfield (in AI assistant)
/camel-brainstorm                        # Design integration
/camel-plan                          # Create implementation plan
/camel-execute                       # Implement, test, verify (internal)
/camel-validate                      # Final quality gate

# Migration
/camel-migrate                       # Analyze legacy project
/camel-plan                          # Plan migration
/camel-execute                       # Execute migration
/camel-validate                      # Final quality gate
```
