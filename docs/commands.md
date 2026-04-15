# Camel-Kit Command Reference

This document is the reference for all Camel-Kit commands: the `camel-kit` CLI and the slash commands used inside AI coding assistants.

## Table of Contents

- [CLI Commands](#cli-commands)
  - [camel-kit init](#camel-kit-init)
- [Slash Commands](#slash-commands)
  - [/camel-design](#camel-design)
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
| `--camel-version`, `-v` | latest Red Hat Build version | Apache Camel version to target |
| `--citrus-version` | `4.9.2` | Citrus Framework version for test schemas |
| `--here` | `false` | Initialize in current directory |
| `--offline` | `false` | Download MCP server and catalog JARs for fully offline operation |
| `--no-fetch` | `false` | Skip external catalog fetching |
| `--silent` | `false` | Suppress all output (no banner, no TUI, no progress, no summary) -- useful for CI/scripted environments |

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
camel-kit init my-integration --camel-version 4.14.0

# Initialize in current directory
camel-kit init --here --ai bob

# Download everything for offline use
camel-kit init my-integration --ai claude --offline

# Skip catalog fetch (faster, offline)
camel-kit init my-integration --ai bob --no-fetch
```

**TUI experience:**

On terminals that support a native image protocol (Kitty, iTerm2, Sixel), `camel-kit init` displays a split-screen TUI while the project is being created: the logo on the left, a live task list with animated spinners and green ticks on the right. The TUI exits automatically when all tasks complete. Falls back to coloured inline output on unsupported terminals.

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

---

## Slash Commands

These commands are used inside your AI coding assistant after project initialization. There are six user-invocable slash commands.

Four additional skills (`/camel-implement`, `/camel-validate`, `/camel-test`, `/camel-knowledge`) are internal -- they are orchestrated automatically by `/camel-execute` and should not be run directly.

---

### /camel-design

**Purpose:** Interactive design session that turns integration ideas into fully formed design specs through collaborative dialogue.

**When to use:** Starting any new integration project. For migrations, use `/camel-migrate` instead.

**Produces:**
- `.camel-kit/business-requirements.md` (BRD)
- `.camel-kit/flows/{flow-name}/{flow-name}.tdd.md` (one TDD per flow)
- `docs/constitution.md`

**Example:**

```
/camel-design
```

**How it works:**

1. **Detect project type** -- greenfield or migration (based on keywords like "create", "build" vs "migrate", "convert")
2. **Load context** -- reads `docs/constitution.md` and `.camel-kit/config.yaml` if they exist
3. **Run interview or discovery** -- Socratic interview (one question at a time) for greenfield; artifact scanning and confirmation for migration
4. **Select Camel version** -- presents Red Hat supported versions for selection
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
- `camel_rh_build_component_info` -- check Red Hat support status
- `camel_knowledge_search` -- search Red Hat docs for guidance

---

### /camel-plan

**Purpose:** Create a detailed implementation plan from an approved design spec.

**When to use:** After `/camel-design` has produced an approved design spec. Usually invoked automatically by `/camel-design`, but can also be run directly if a design spec already exists.

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
4. **Verification phase** -- runs `/camel-verify` as a final validation (build, start, diagnose, fix). Verification is optional -- failure does not block completion
5. **Completion summary** -- reports task status, review results, and verification outcome

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
| `/camel-validate` | Validate routes for correctness, security, and constitution compliance |
| `/camel-test` | Generate Citrus integration tests |
| `/camel-knowledge` | Query Red Hat Build documentation for component support and guidance |

---

### /camel-migrate

**Purpose:** Migrate an existing integration from another platform to Red Hat Build of Apache Camel.

**When to use:** When you have an existing MuleSoft, Camel 2.x/3.x, or Red Hat Fuse project to migrate.

**Produces:** Same as `/camel-design` -- BRD and TDD files, plus a design spec tailored to the migration.

**Example:**

```
/camel-migrate
```

**Supported source platforms:**

| Platform | Versions | Detection method |
|----------|----------|-----------------|
| MuleSoft Mule | 3.x, 4.x | XML namespace `mulesoft.org`, `pom.xml` groupId `org.mule` / `com.mulesoft` |
| Apache Camel | 2.x, 3.x | `pom.xml` with `org.apache.camel` dependencies (older version) |
| Red Hat Fuse | 6.x, 7.x | `pom.xml` with `org.jboss.fuse` or `fuse-` BOMs |

**How it works:**

This is a shortcut into `/camel-design` with the project type pre-set to **migration**. It runs a two-phase analysis:

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

---

### /camel-verify

**Purpose:** Runtime verification feedback loop that builds, starts, diagnoses errors, applies fixes, and retries until the application runs correctly or the iteration limit is reached.

**When to use:** After implementation is complete and you want to verify the project actually runs. Can be used standalone or is run automatically as the final phase of `/camel-execute`.

**Produces:**
- Verification report (printed to console)
- Fixes applied to project files (pom.xml, route YAML, properties, etc.)

**Example:**

```
/camel-verify
```

**How it works (5-phase loop):**

1. **Environment Preparation** -- checks for Maven (`./mvnw`), Docker, JDK, Camel CLI. Starts external services via `docker compose up -d` if a `docker-compose.yaml` exists. Gracefully degrades when tools are unavailable.

2. **Build Verification** -- runs `./mvnw compile` and enters an iteration loop (max 15 attempts per phase) if the build fails. Each error is classified, routed to the appropriate fix target, and retried. Skipped entirely for JBang runtime.

3. **Startup Verification** -- starts the application using the runtime-specific command (`./mvnw quarkus:dev`, `./mvnw spring-boot:run`, or `camel run`) and watches for success or failure patterns in the logs. Errors are classified and fixed in an iteration loop (max 15 attempts).

4. **Behavioral Verification** -- sends test data to the running application, compares actual output against expected output using semantic comparison (field-by-field, ignoring key ordering and whitespace). Mismatches are classified and fixed in an iteration loop (max 15 attempts).

5. **Report** -- structured summary of all phases, fixes applied, issues found, and any skipped checks with reasons.

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

---

## Command Cheat Sheet

```bash
# CLI
camel-kit init my-project --ai claude

# Greenfield (in AI assistant)
/camel-design                        # Design integration
/camel-plan                          # Create implementation plan
/camel-execute                       # Implement, validate, test, verify

# Migration
/camel-migrate                       # Analyze legacy project
/camel-plan                          # Plan migration
/camel-execute                       # Execute migration

# Verification (standalone)
/camel-verify                        # Build, start, diagnose, fix
```
