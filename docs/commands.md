# Camel-Kit Command Reference

This document is the reference for all Camel-Kit commands: the `camel-kit` CLI and the slash commands used inside AI coding assistants.

## Table of Contents

- [CLI Commands](#cli-commands)
  - [camel-kit init](#camel-kit-init)
  - [camel-kit graph](#camel-kit-graph)
  - [camel-kit doc](#camel-kit-doc)
  - [camel-kit nextId](#camel-kit-nextid)
- [Slash Commands](#slash-commands)
  - [/camel-brainstorm](#camel-brainstorm)
  - [/camel-plan](#camel-plan)
  - [/camel-execute](#camel-execute)
  - [/camel-migrate](#camel-migrate)
  - [/camel-validate](#camel-validate)
  - [/camel-ship](#camel-ship)
  - [/camel-debug](#camel-debug)
  - [/camel-verify (internal)](#camel-verify)
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

### camel-kit nextId

Generate the next sequential pipeline ID and create the pipeline directory.

**Usage:**

```bash
camel-kit nextId <slug>
```

**Arguments:**

| Argument | Required | Description |
|---|---|---|
| `<slug>` | Yes | Pipeline slug — lowercase alphanumeric with hyphens (e.g., `order-processing`) |

**Behavior:**

1. Scans `docs/camel-kit/` for existing `NNN-*` directories
2. Finds the maximum `NNN` value (or 0 if none exist)
3. Creates `docs/camel-kit/<NNN+1>-<slug>/` directory
4. Prints the generated pipeline ID to stdout

**Examples:**

```bash
# First pipeline in a project
$ camel-kit nextId order-processing
001-order-processing

# Subsequent pipelines
$ camel-kit nextId inventory-sync
002-inventory-sync
```

**Exit Codes:**

| Code | Meaning |
|---|---|
| 0 | Success — pipeline ID generated and directory created |
| 1 | Error — invalid slug or directory creation failure |

### camel-kit doc

Pipeline document staleness management. Replaces the text-marker-based staleness system with deterministic YAML frontmatter operations.

**Usage:**

```bash
camel-kit doc <subcommand> [options]
```

**Subcommands:**

| Subcommand | Description |
|------------|-------------|
| `init --by <skill> --from <source> <file>` | Add provenance frontmatter metadata to a document |
| `check <file>` | Query document staleness status — outputs JSON to stdout |
| `stale --reason "..." [--cascade] <file>` | Mark a document as stale |
| `unstale <file>` | Clear staleness from a document |

**Frontmatter Schema:**

Each pipeline artifact carries YAML frontmatter with two namespaced blocks:

```yaml
---
staleness:
  stale: false
  since: null
  reason: null
generated:
  at: "2026-05-13T09:00:00Z"
  by: camel-plan
  from: design-spec.md
---
```

- `staleness` — mutable, written by `doc stale` and `doc unstale`
- `generated` — immutable after creation, written by `doc init` when the pipeline skill produces the artifact
- `generated.from` enables data-driven cascade without hardcoded pipeline topology

**`doc check` output:**

```json
{
  "file": "docs/camel-kit/007/implementation-plan.md",
  "stale": true,
  "since": "2026-05-13T10:00:00Z",
  "reason": "design-spec.md was refined",
  "generated": {
    "at": "2026-05-13T09:00:00Z",
    "by": "camel-plan",
    "from": "design-spec.md"
  }
}
```

Exit code 0 for successful execution regardless of staleness. Non-zero for errors only. Documents without frontmatter return `{"stale": false, ...null fields}`.

**`doc stale` options:**

| Option | Required | Description |
|--------|----------|-------------|
| `--reason "..."` | Yes | Reason for marking the document stale (audit trail) |
| `--cascade` | No | Propagate staleness to downstream artifacts via `generated.from` chain |

**Cascade behavior:**

When `--cascade` is used, the command walks sibling files in the same directory. For each file whose `generated.from` matches the target filename, it marks that file stale and recursively continues down the chain.

**`doc init` options:**

| Option | Required | Description |
|--------|----------|-------------|
| `--by <skill>` | Yes | Skill that generated this artifact (e.g., `camel-plan`) |
| `--from <source>` | Yes | Source artifact this was generated from (e.g., `design-spec.md`) |

`doc init` is idempotent — if the file already has frontmatter, it is preserved unchanged. This makes it safe to call unconditionally after every save.

**Examples:**

```bash
# Add provenance metadata after generating an artifact
camel-kit doc init --by camel-plan --from design-spec.md docs/camel-kit/001-order-processing/implementation-plan.md

# Check if a document is stale
camel-kit doc check docs/camel-kit/001-order-processing/implementation-plan.md

# Mark stale with cascade to all downstream artifacts
camel-kit doc stale --reason "design spec was amended" --cascade docs/camel-kit/001-order-processing/design-spec.md

# Clear staleness after regeneration
camel-kit doc unstale docs/camel-kit/001-order-processing/implementation-plan.md
```

**Exit Codes:**

| Code | Meaning |
|---|---|
| 0 | Success — command completed (for `check`: regardless of staleness) |
| 1 | Error — file not found, I/O failure, or invalid arguments (e.g., blank `--reason`) |

---

## Slash Commands

These commands are used inside your AI coding assistant after project initialization. The entry point is `/camel-start`, which routes to the right pipeline skill. Skills are organized into tiers:

**Tier 1 — Pipeline:** `/camel-brainstorm`, `/camel-plan`, `/camel-execute`, `/camel-migrate`, `/camel-validate` — the five pipeline steps, invoked via the `/camel-start` decision tree or directly via slash command.

**Tier 2 — Standalone utilities:** `/camel-ship`, `/camel-knowledge`, `/camel-debug` — can be invoked at any point without affecting pipeline state.

**Internal:** `/camel-implement`, `/camel-verify`, `/camel-test`, `/camel-design` — subagent-only, dispatched automatically by pipeline skills. Not intended for direct use.

---

### /camel-brainstorm

**Purpose:** Interactive design session that turns integration ideas into fully formed design specs through collaborative dialogue.

**When to use:** Starting any new integration project. For migrations, use `/camel-migrate` instead.

**Produces:**
- `.camel-kit/business-requirements.md` (BRD)
- `.camel-kit/flows/{flow-name}/{flow-name}.tdd.md` (one TDD per flow)
- `docs/constitution.md`

**Examples:**

```bash
# Start a new design (chained mode — auto-invokes plan after approval)
/camel-brainstorm

# Start a new design in standalone mode (if design-spec.md does not exist)
/camel-brainstorm 001-order-processing

# Amend an existing design spec (if design-spec.md already exists)
/camel-brainstorm 001-order-processing
```

**Standalone and amend mode:**

When invoked with a `<PIPELINE_ID>` argument:
- If `design-spec.md` does not exist → runs the full interview in standalone mode (no auto-transition to plan)
- If `design-spec.md` already exists → enters **amend mode**: loads the existing spec, lets you modify it, marks downstream artifacts stale, then stops

**How it works:**

1. **Detect invocation mode** -- check for `<PIPELINE_ID>` argument and existing design spec
2. **Detect project type** -- greenfield or migration (based on keywords like "create", "build" vs "migrate", "convert")
3. **Load context** -- reads `docs/constitution.md` and `.camel-kit/config.yaml` if they exist
4. **Run interview or discovery** -- Socratic interview (one question at a time) for greenfield; artifact scanning and confirmation for migration
5. **Select Camel version** -- presents available versions for selection
6. **Design flows** -- for each flow, verifies components, EIPs, data formats, and languages against the MCP catalog. Asks conditional questions only when relevant:
   - Circuit breaker configuration (if HTTP components detected)
   - Idempotent consumer (if message broker components detected)
   - Transaction boundaries (if multiple sinks detected)
7. **Assemble design spec** -- compiles the full BRD and TDD files
8. **Self-review** -- scans for placeholders, contradictions, unverified components
9. **User approval** -- presents the spec and waits for explicit approval
10. **Transition** -- in chained mode, invokes `/camel-plan` automatically after approval. In standalone/amend mode, writes output and stops.

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
- `docs/camel-kit/<PIPELINE_ID>/implementation-plan.md`

**Example:**

```bash
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

1. **Read plan** -- extracts all tasks from `docs/camel-kit/<PIPELINE_ID>/implementation-plan.md`
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

### /camel-validate

> **Tier 1 pipeline step.** Final stage after execute — produces a comprehensive quality report.

**Purpose:** Static quality analysis of generated Camel routes across multiple dimensions: schema validation, endpoint verification, constitution compliance, security analysis, and anti-pattern detection.

**When to use:** After `/camel-execute` completes (standalone or as Stage 3 in the `/camel-ship` pipeline). Use whenever you have generated routes that need quality validation before shipping.

**Produces:**
- Validation report saved to `docs/validation-report-YYYY-MM-DD_HH-mm.md`
- Categorized findings: PASS / FAIL / WARN per quality dimension
- Constitution compliance check (all 7 rules)
- Recommendations for priority fixes

**Example:**
```
/camel-validate
```

---

### /camel-ship

**Purpose:** Run the full pipeline autonomously (brainstorm → plan → execute → validate → stamp) with configurable oversight.

**When to use:** When you want the AI to run the entire pipeline end-to-end with minimal intervention.

**Arguments:**

| Argument | Default | Description |
|---|---|---|
| `[input-file]` | none | Requirements document, design spec, or brainstorm notes |
| `--ask` | `smart` | Oversight level: `always`, `smart`, or `never` |
| `--resume` | false | Continue from `.camel-kit/pipeline.json` with staleness detection |
| `--start-from <stage>` | none | Skip to stage: `brainstorm`, `plan`, `execute`, `validate` |

**Examples:**

```bash
# Run full pipeline from requirements doc
/camel-ship requirements.md

# Run with always-ask oversight
/camel-ship requirements.md --ask always

# Resume a previously interrupted pipeline
/camel-ship --resume

# Start from execution (design spec and plan must already exist)
/camel-ship --start-from execute
```

**Staleness detection on resume:**

When `--resume` is used, camel-ship scans all pipeline artifacts for staleness markers (`⚠️ **STALE**`). If stale artifacts are found, it automatically re-runs from the earliest stale stage instead of continuing from the stored `currentStage`. This handles the case where `/camel-brainstorm <PIPELINE_ID>` amended the design spec between sessions — downstream artifacts are automatically regenerated.

---

### /camel-debug

**Purpose:** Ad-hoc troubleshooting for broken Camel routes outside of a pipeline run. Follows a structured STOP → PRESERVE → DIAGNOSE → FIX → GUARD workflow.

**When to use:** When a route was working but is now broken, or when a user needs help debugging a Camel application outside of an active pipeline. For build/test failures during pipeline execution, `/camel-execute` dispatches `camel-verify` automatically.

**Examples:**

```bash
# Debug a broken route
/camel-debug

# Debug with context
/camel-debug my route is failing with a ClassNotFoundException
```

**How it works (5-step workflow):**

1. **STOP** -- gather context (runtime, Camel version, error message, recent changes). Do NOT modify files yet.
2. **PRESERVE** -- capture current state via `git status`/`git diff`. Warn if uncommitted changes exist.
3. **DIAGNOSE** -- reproduce the error, classify it against the error taxonomy, verify components via MCP catalog, inspect route structure. Diagnosis steps run as subagents to keep verbose output out of the main context.
4. **FIX** -- explain the proposed fix, apply minimal targeted changes, verify the fix resolves the issue. Up to 5 fix attempts before escalating.
5. **GUARD** -- suggest a preventive measure (test, validation rule, CI check) to prevent recurrence.

**Error classification:** Reuses the same 14-pattern error taxonomy as `/camel-verify`:

| Category | Examples | Fix Target |
|---|---|---|
| Missing dependency | `ClassNotFoundException`, missing Camel component | Self-repair (add to pom.xml) |
| Route creation | `FailedToCreateRouteException` | Re-generate route |
| Wrong endpoint options | `ResolveEndpointFailedException` | Re-validate via MCP catalog |
| Expression failure | `ExpressionEvaluationException` | Fix expression |
| External service | `Connection refused` | Fix service configuration |
| Unclassified | No matching pattern | Escalate to user |

**Subagent isolation:** Diagnosis dispatches three subagents (route analyzer, MCP verifier, log analyzer) to keep raw diagnostic output out of the main conversation. Only the structured diagnosis report flows back.

---

### /camel-verify

> **Internal skill** -- dispatched automatically by `/camel-execute`. Not intended for standalone use. For quality checks after the pipeline completes, use `/camel-validate`.

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

# Greenfield (in AI assistant) — chained mode
/camel-brainstorm                        # Design → auto plan → auto execute → auto validate

# Greenfield — standalone mode (each stage independently)
/camel-brainstorm 001-order-processing   # Design (standalone, no auto-transition)
/camel-plan 001-order-processing         # Plan from existing spec (standalone)
/camel-execute 001-order-processing      # Execute from existing plan (standalone)
/camel-validate 001-order-processing     # Validate (standalone)

# Re-iteration (amend existing design, marks downstream stale)
/camel-brainstorm 001-order-processing   # Amend design-spec.md → downstream marked stale

# Autonomous pipeline
/camel-ship requirements.md              # Full pipeline end-to-end
/camel-ship --resume                     # Resume with staleness detection
/camel-ship --start-from execute         # Skip to execution stage

# Migration
/camel-migrate                           # Analyze legacy project → auto plan → auto execute

# Validate (standalone, any project)
/camel-validate                          # Validate routes in current project

# Debug (ad-hoc troubleshooting)
/camel-debug                             # Diagnose and fix a broken route
```
