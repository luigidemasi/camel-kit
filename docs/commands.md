# Camel-Kit Command Reference

This document provides detailed reference for all Camel-Kit commands.

## Table of Contents

- [CLI Commands](#cli-commands)
  - [camel-kit init](#camel-kit-init)
- [Slash Commands](#slash-commands)
  - [/camel-project](#camel-project)
  - [/camel-flow](#camel-flow)
  - [/camel-migrate](#camel-migrate)
  - [/camel-implement](#camel-implement)
  - [/camel-validate](#camel-validate)
  - [/camel-test](#camel-test)
  - [/camel-wanaku](#camel-wanaku)

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
| `--camel-version`, `-v` | `4.18.0` | Apache Camel version to target |
| `--citrus-version` | `4.9.2` | Citrus Framework version for test schemas |
| `--here` | `false` | Initialize in current directory |
| `--no-fetch` | `false` | Skip external catalog fetching |
| `--silent` | `false` | Suppress all output (no banner, no TUI, no progress, no summary) — useful for CI/scripted environments |

**Examples:**

```bash
# Create new project for IBM Project Bob
camel-kit init my-integration --ai bob

# Create new project for Gemini CLI
camel-kit init my-integration --ai gemini

# Create new project for Claude Code
camel-kit init my-integration --ai claude

# Use specific Camel version
camel-kit init my-integration --camel-version 4.18.0

# Use specific Citrus version
camel-kit init my-integration --citrus-version 4.9.2

# Initialize in current directory
camel-kit init --here --ai bob

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
├── .bob/commands/               # AI agent slash commands
│   ├── camel-project.md
│   ├── camel-flow.md
│   ├── camel-implement.md
│   ├── camel-validate.md
│   ├── camel-test.md
│   └── camel-migrate.md
├── .mcp.json                    # Claude Code MCP configuration
├── .bob/mcp.json                # IBM Bob MCP configuration
└── .gemini/mcp.json             # Gemini CLI MCP configuration
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

### /camel-project

**(Optional)** Define your integration landscape and identify all flows.

**Usage:**

```
/camel-project
```

**Interactive flow:**

1. **Business Purpose** - What problem does this integration solve? (1-2 sentences)
2. **Systems** - List system names and their role (source/sink)
3. **Flows** - Describe data flows as "Move [data] from [source] to [sink]"

**Output:**

- Saves to `.camel-kit/project.md`

---

### /camel-flow

Define a flow's business requirements, technical design, and data contracts.

**Usage:**

```
/camel-flow <flow-name>
```

**Interactive flow:**

1. **Flow Identification** - Extract core intent
2. **Source System** - Where data comes from (uses MCP `camel_catalog_components` to search)
3. **Processing Steps** - EIPs required; data transformation uses DataMapper/XSLT-saxon by default (schemas requested); `unmarshal`/`marshal` only as fallback when no schemas are available
4. **Sink System** - Where data goes to
5. **Error Handling** - Dead Letter Channel and retry strategy

**Conditional questions (asked only when relevant):**

- **Data transformation** - If transformation is needed: asks for source/target schemas; prefers DataMapper/XSLT-saxon; falls back to `unmarshal`/`marshal` only when schemas are unavailable
- **Circuit Breaker** - Asked only if source or sink is an external HTTP/REST service
- **Idempotent Consumer** - Asked only if source is a message broker or deduplication is needed
- **Transactions** - Asked only if the flow writes to more than one external system
- **Performance** - Asked only if high throughput or low latency was mentioned
- **Security** - Asked only if security, PII, or compliance was mentioned
- **Monitoring** - Asked only if observability was mentioned

**MCP Tools Used (all MANDATORY when MCP is configured, all pass `CAMEL_VERSION`):**
- `camel_catalog_components` + `camel_catalog_component_doc` — before any component is suggested (Q2, Q4)
- `camel_catalog_dataformats` + `camel_catalog_dataformat_doc` — before any data format is chosen (Q1)
- `camel_catalog_eips` + `camel_catalog_eip_doc` — before any EIP is suggested (Q3)
- `camel_catalog_languages` + `camel_catalog_language_doc` — before any expression language is chosen (Q3)

**Output:**

- Creates `.camel-kit/flows/<flow-name>/<flow-name>.tdd.md`

---

### /camel-migrate

Migrate an existing integration from another platform to Apache Camel. Detects the source vendor automatically, then runs a two-phase analysis (Business Analyst + Integration Architect) to produce the same artifacts that `/camel-project` + `/camel-flow` produce — so `/camel-implement` requires no changes.

**Usage:**

```
/camel-migrate
```

**Supported source platforms:**

| Platform | Versions | Detection method |
|----------|---------|-----------------|
| MuleSoft Mule | 3.x, 4.x | XML namespace `mulesoft.org`, `pom.xml` groupId `org.mule` / `com.mulesoft` |

**How it works (generic orchestration):**

1. Scans **all** project artifacts (XML, build files, properties, docs, Docker/K8s, source, tests).
2. Detects vendor and version from the full scan content.
3. Builds a pre-populated analysis summary (purpose, SLA, security, failure behaviour, deployment target) extracted from the artifacts — without asking the user.
4. Confirms the summary; only asks about genuine gaps (typically just API compatibility).
5. Delegates to the vendor sub-skill, passing the confirmed summary so it never re-asks confirmed questions.

**MuleSoft Mule sub-skill (Phase 1 — Business Analyst):**

1. Parses Mule XML and builds a flow inventory.
2. Flags proprietary connectors and asks the user how to handle each one (using `pom.xml` dependencies to pre-suggest replacements).
3. Fills any remaining gaps not already in the analysis summary.
4. Produces `.camel-kit/business-requirements.md` and `.camel-kit/constitution.md`.

**MuleSoft Mule sub-skill (Phase 2 — Integration Architect):**

1. Maps each Mule component to its catalog-verified Camel equivalent (calls `camel_catalog_component_doc` for every component).
2. Converts DataWeave transformations into TDD Section 3 field mapping tables.
3. Asks only what the XML cannot answer (DataWeave intent, missing endpoints, auth mechanisms, retry strategy).
4. Produces one TDD file per Mule flow:
   - `.camel-kit/flows/{flow-name}/{flow-name}.tdd.md`

**Output:**

```
.camel-kit/
├── business-requirements.md      # BRD — identical format to /camel-project output
├── constitution.md               # Same as /camel-project output
└── flows/
    ├── {flow-name-1}/
    │   └── {flow-name-1}.tdd.md  # TDD — identical format to /camel-flow output
    └── {flow-name-2}/
        └── {flow-name-2}.tdd.md
```

**Next steps after `/camel-migrate`:**

```
/camel-implement {flow-name}      # Same as greenfield from here
/camel-validate {flow-name}
/camel-test {flow-name}
```

**Proprietary connectors handled:**

| Connector | Suggested Alternatives |
|-----------|----------------------|
| Anypoint MQ | Amazon SQS, Azure Service Bus, RabbitMQ, ActiveMQ |
| Object Store | Infinispan, Redis, Caffeine cache, JPA |
| SAP Connector | `camel-sap` (if licensed), SAP REST/SOAP APIs |
| Workday | Workday REST API via `camel-http` |
| NetSuite | NetSuite REST/SOAP APIs via `camel-http` |

For connectors with no direct equivalent, the command stops and asks the user before proceeding.

---

### /camel-implement

Generate Camel YAML DSL from the flow definition with automated validation.

**Usage:**

```
/camel-implement <flow-name>
```

**Prerequisites:**

- Flow definition must exist (`/camel-flow` first)

**Process:**

1. **Load Flow** - Read flow definition and component catalog
2. **Component Lookup** - Verify components exist in catalog (uses MCP `camel_catalog_component_doc`)
3. **Generate YAML** - Transform design to Camel YAML DSL
4. **MCP Validation** - Automatically validate with MCP tools (if available)
5. **Maven Validation** - Run Maven validation loop if needed
6. **Output** - Write to `<flow-name>.camel.yaml`

**Automatic MCP Validation:**

When the Camel MCP server is configured, the command automatically validates the generated route using `camel_route_context` (structure analysis) and `camel_validate_route` (URI validation). All component names, options, and parameters are checked against the catalog. The AI agent fixes validation errors automatically in a validate→fix→re-query→retry loop (up to 3 attempts).

**Generation constraints:**

| Constraint | Rule |
|-----------|------|
| `unmarshal`/`marshal` | Not added by default. Included only when the TDD explicitly requires typed Java object processing and no XSLT transformation covers it. |
| DataMapper/XSLT | Preferred over `unmarshal` for JSON↔JSON, JSON↔XML, and XML↔XML transformations when schemas are available. |
| Global `onException` | Must be declared as top-level elements **before** any `- route:` block. Route-scoped error handling (`errorHandler:`, `doTry`/`doCatch`) stays inside the route. |
| Jakarta EE namespaces | When Camel version ≥ 4.0, `jakarta.*` packages are used instead of `javax.*` for all Jakarta EE APIs (Servlet, JPA, JMS, Bean Validation, JAX-RS, etc.). Java SE packages (`javax.sql.*`, `javax.xml.*`) are not affected. |
| HTTP header cleanup | When the route has both an HTTP consumer (`platform-http`, `servlet`, `jetty`) and an HTTP producer (`http`, `https`), a `removeHeaders("CamelHttp*")` step is inserted before each outbound HTTP call. |
| `to` vs `toD` | Any `${...}` Simple expression in a `to` URI or `parameters:` value is never evaluated — use `toD` with dynamic values inlined in the URI string. `{{...}}` property placeholders are safe in `to`. |
| JSON DataMapper ordering | Never place `unmarshal: json:` before an xslt-saxon DataMapper step (`useJsonBody: true`) — it converts the body to a `Map` which cannot be passed as a JSON string to the XSLT param. Place `unmarshal: json:` after the DataMapper step if needed. |
| JSON XSLT pattern | Generated JSON DataMapper XSLTs must use `json-to-xml($paramName)` (explicit `xsl:param`) — never `json-to-xml(.)` (context node). XSLT 3.0 has no direct JSON-to-JSON function; the W3C lossless XML intermediate is required. |

**Output:**

```yaml
# Global onException (if defined in TDD) — MUST come before routes
- onException:
    exception:
      - com.example.ValidationException
    handled:
      constant:
        expression: "true"
    steps:
      - to:
          uri: "kafka:{{kafka.topic.invalid}}"

- route:
    id: order-ingestion
    description: Consume orders from Kafka and persist to database

    errorHandler:
      deadLetterChannel:
        deadLetterUri: "kafka:{{kafka.topic.dlq}}"

    from:
      uri: "kafka:{{kafka.topic.orders}}"
      steps:
        - filter:
            simple: "${body.totalAmount} >= 50"
            steps:
              - to:
                  uri: "jpa:{{jpa.entity.order}}"
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

### /camel-validate

Validate route specifications for correctness, security, and compliance.

**Usage:**

```
/camel-validate              # Validate all flows
/camel-validate <flow-name>  # Validate specific flow
/camel-validate --strict     # Treat warnings as errors
```

**Validation categories:**

| Category | Code Prefix | MCP Tool | Examples |
|----------|-------------|----------|----------|
| Completeness | `COMP-*` | - | Source/sink defined, error handling |
| URI Validation | `CORR-*` | `camel_validate_route` | Valid components, valid options, required params |
| Security | `SEC-*` | `camel_route_harden_context` | Credentials, encryption, authentication |
| Constitution | `CONST-*` | - | Naming, circuit breakers |
| Dependencies | `DEP-*` | - | direct: endpoints, circular deps |

**MCP-Enhanced Validation:**

When the Camel MCP server is configured, validation adds route structure analysis (`camel_route_context`), URI validation (`camel_validate_route`), and 47 automated security checks (`camel_route_harden_context`). See [User Guide — Validation](user-guide.md#validation) for details.

**Validation report:**

Saves detailed report to `.camel-kit/validation-report.md`.

---

### /camel-test

Generate Citrus integration tests for routes with automated validation.

**Usage:**

```
/camel-test <flow-name>      # Generate tests for one flow
/camel-test --all            # Generate tests for all flows
```

**Prerequisites:**

- Citrus schemas must be cached (downloaded during `camel-kit init`)

**MCP Tools Used:**
- `camel_route_context` - Analyze route to determine test strategy
- `camel_catalog_component_doc` - Get component details for test mocks

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

### /camel-wanaku

Generate Wanaku rules files to expose Camel routes as MCP tools via the Wanaku MCP Router's Camel Integration Capability.

**Usage:**

```
/camel-wanaku
```

**Prerequisites:**

- At least one generated Camel route (`.camel.yaml`) from `/camel-implement`

**Process:**

1. **Identify Routes** — Parse all `.camel.yaml` files, extract route IDs and endpoints
2. **Determine Exposure** — Classify each route as tool, resource, or skip (event-driven)
3. **Map Parameters** — Map route input parameters to Camel headers or body
4. **Generate Rules** — Produce `{flow-name}.wanaku-rules.yaml` with tool/resource definitions
5. **Deployment Instructions** — Provide commands for deploying to Wanaku (data store, CLI, or kubectl)

**Exposure strategy:**

| Route Source | Exposed As | Reason |
|---|---|---|
| `platform-http:`, `rest:`, `servlet:` | Tool | Request/response callable by AI |
| `direct:` (standalone) | Tool | Directly invocable |
| `file:`, `sql:` SELECT | Resource | Read-only data retrieval |
| `kafka:`, `jms:`, `timer:` | Skip | Event-driven, not callable |

**Output:**

```yaml
# {flow-name}.wanaku-rules.yaml
mcp:
  tools:
    - lookup-order:
        route:
          id: "order-lookup"
        description: "Look up an order by its ID"
        properties:
          - name: orderId
            type: string
            description: "The unique order identifier"
            required: true
            mapping:
              type: header
              name: orderId
```

**Deploying to Wanaku:**

```bash
# Upload to Wanaku data store
wanaku data-store add --read-from-file order-api.camel.yaml
wanaku data-store add --read-from-file order-api.wanaku-rules.yaml

# Verify tools are registered
wanaku tools list
```

The [Camel Integration Capability (CIC)](https://wanaku.ai/docs/camel-integration-capability/) will automatically deploy your routes after registration — no manual pod or Kubernetes steps needed.

---

All commands use the Apache Camel MCP Server when available. See [MCP Integration](user-guide.md#mcp-integration) for details, or [Architecture Guide](architecture.md#mcp-integration-internal-details) for tool parameters and internals.

---

## Command Cheat Sheet

```bash
# CLI
camel-kit init my-project --ai bob     # Create project with MCP config

# Greenfield slash commands (in AI assistant)
/camel-project                         # Define landscape (optional)
/camel-flow order-ingestion            # Define and design flow

# Migration slash commands (in AI assistant)
/camel-migrate                         # Migrate from MuleSoft (or other supported platforms)

# Shared slash commands
/camel-implement order-ingestion       # Generate YAML with MCP validation
/camel-validate                        # Check specs and run security analysis
/camel-test order-ingestion            # Generate tests with validation

# Wanaku deployment
/camel-wanaku                          # Generate Wanaku rules to expose routes as MCP tools
```
