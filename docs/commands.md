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
- [MCP Integration](#mcp-integration)

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
| `--camel-version`, `-v` | `4.14.5` | Apache Camel version to target |
| `--citrus-version` | `4.9.2` | Citrus Framework version for test schemas |
| `--here` | `false` | Initialize in current directory |
| `--no-fetch` | `false` | Skip external catalog fetching |

**Examples:**

```bash
# Create new project for IBM Project Bob
camel-kit init my-integration --ai bob

# Create new project for Gemini CLI
camel-kit init my-integration --ai gemini

# Create new project for Claude Code
camel-kit init my-integration --ai claude

# Use specific Camel version
camel-kit init my-integration --camel-version 4.14.5

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
│   └── camel-test.md
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

**MCP Tools Used:**
- `camel_catalog_components` - Search components by category
- `camel_catalog_component_doc` - Get component documentation

**Output:**

- Creates `.camel-kit/flows/<flow-name>/<flow-name>.tdd.md`

---

### /camel-migrate

Migrate an existing integration from another platform to Apache Camel. Detects the source vendor automatically, then runs a two-phase analysis (Business Analyst + Integration Architect) to produce the same artifacts that `/camel-project` + `/camel-flow` produce — so `/camel-implement` requires no changes.

**Usage:**

```
/camel-migrate [path-to-export]
```

| Argument | Description |
|----------|-------------|
| `path-to-export` | Optional path to the source export: a single XML file, a project directory, or a ZIP archive. If omitted, the command asks for it. |

**Supported source platforms:**

| Platform | Versions | Detection method |
|----------|---------|-----------------|
| MuleSoft Mule | 3.x, 4.x | XML namespace `mulesoft.org`, `pom.xml` groupId `org.mule` / `com.mulesoft` |

**Phase 1 — Business Analyst:**

1. Parses all Mule XML files and builds a flow inventory.
2. Flags proprietary connectors (Anypoint MQ, Object Store, SAP, Workday, etc.) and asks the user how to handle each one.
3. Asks business questions that cannot be answered from the XML (purpose, SLA, compliance, failure behaviour).
4. Produces:
   - `.camel-kit/business-requirements.md`
   - `.camel-kit/constitution.md`

**Phase 2 — Integration Architect:**

1. Maps each Mule component to its Camel equivalent using the bundled mapping guide.
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

When the Camel MCP server is configured, the command automatically validates the generated route:

**Step 1: Route Structure Analysis**
```
MCP Tool: camel_route_context

Analyzing generated route...
✅ Components detected: [kafka, sql, http]
✅ EIPs used: [unmarshal, validate, choice]
✅ All components valid for Camel version
```

**Step 2: URI and Route Validation**
```
MCP Tool: camel_validate_route

Validating: kafka:orders?brokers={{kafka.brokers}}
  ✅ Component 'kafka' exists
  ✅ All options valid
  ✅ Required parameters present

✅ All endpoint URIs valid (3/3 passed)
```

**MCP Tools Used:**
- `camel_catalog_component_doc` - Get component configuration details
- `camel_route_context` - Extract components and EIPs from generated route
- `camel_validate_route` - Validate all endpoint URIs against catalog schema

**MCP Route Validation:**

The command uses the MCP `camel_validate_route` tool to validate the generated route:

```
MCP Tool: camel_validate_route
Parameters:
  - route: <entire YAML route content>
  - version: {camel-version}
```

Validation checks:
- All endpoint URIs exist in catalog
- Component options are valid
- Required parameters are present
- Catches typos and suggests corrections

The AI agent fixes validation errors automatically until the route is valid.

**Generation constraints:**

| Constraint | Rule |
|-----------|------|
| `unmarshal`/`marshal` | Not added by default. Included only when the TDD explicitly requires typed Java object processing and no XSLT transformation covers it. |
| DataMapper/XSLT | Preferred over `unmarshal` for JSON↔JSON, JSON↔XML, and XML↔XML transformations when schemas are available. |
| Global `onException` | Must be declared as top-level elements **before** any `- route:` block. Route-scoped error handling (`errorHandler:`, `doTry`/`doCatch`) stays inside the route. |
| Jakarta EE namespaces | When Camel version ≥ 4.0, `jakarta.*` packages are used instead of `javax.*` for all Jakarta EE APIs (Servlet, JPA, JMS, Bean Validation, JAX-RS, etc.). Java SE packages (`javax.sql.*`, `javax.xml.*`) are not affected. |

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

When the Camel MCP server is configured, validation includes:

**1. Route Structure Analysis**
- **Tool**: `camel_route_context`
- **Checks**: Extracts all components and EIPs, verifies they exist in the catalog
- **Example**: Detects if 'kafak' should be 'kafka'

**2. URI Validation**
- **Tool**: `camel_validate_route`
- **Checks**: Validates all endpoint URIs against catalog schema
- **Catches**: Typos, unknown options, missing required parameters
- **Example**: `kafka:test?brokerList=...` → Suggests `brokers` instead of `brokerList`

**3. Security Analysis (47 Automated Checks)**
- **Tool**: `camel_route_harden_context`
- **Categories**:
  - Credential exposure (hardcoded passwords, API keys)
  - Encryption (HTTP vs HTTPS, TLS configuration)
  - Authentication (missing auth configuration)
  - Input validation (SQL injection, XSS risks)
  - Data exposure (sensitive data in logs)
  - Compliance (GDPR, PCI-DSS, HIPAA concerns)

**MCP Tools Used:**
- `camel_validate_route` - Validate endpoint URIs against catalog
- `camel_route_harden_context` - Run 47 automated security checks

**Output format:**

With MCP:
```
✅ Route structure: VALID
✅ URI validation: VALID (3/3 endpoints)
⚠️  Security: 45/47 checks passed

Security Issues:
  Line 12: HTTP instead of HTTPS
  Risk: Unencrypted communication
  Fix: Change to https://api.example.com
```

Without MCP:
```
== COMPLETENESS ==
[OK] order-ingestion: source defined
[OK] order-ingestion: sink defined
[ERROR] order-ingestion: error handling NOT defined

== CORRECTNESS ==
[OK] kafka component valid (Camel 4.14.x)
[WARN] kafak component - did you mean 'kafka'?

VALIDATION FAILED - 2 errors, 1 warning
```

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

## MCP Integration

Camel-Kit integrates with the **Apache Camel MCP (Model Context Protocol) Server** to provide real-time catalog queries and validation directly within your AI coding assistant.

### What is the Camel MCP Server?

The Camel MCP server (`camel-jbang-mcp`) exposes the complete Apache Camel catalog through the Model Context Protocol, allowing AI assistants to:

- **Query components** - Search and filter 300+ Camel components by category
- **Get documentation** - Retrieve always-current component docs for your exact Camel version
- **Validate routes** - Check endpoint URIs against the catalog schema, catch typos
- **Analyze security** - Run 47 automated security checks on routes
- **Extract context** - Analyze routes to identify components and EIPs used

### Configuration

The MCP server is automatically configured when you run `camel-kit init`:

- **Claude Code**: `.mcp.json` in project root
- **IBM Bob**: `.bob/mcp.json`
- **Gemini CLI**: `.gemini/mcp.json`

The AI assistant automatically uses MCP tools when available. No additional configuration needed!

### MCP Tools by Command

| Command | MCP Tools Used | Purpose |
|---------|---------------|---------|
| `/camel-project` | `camel_version_list` | List Camel versions with LTS status |
| `/camel-flow` | `camel_catalog_components`<br>`camel_catalog_component_doc` | Search components by category<br>Get component documentation |
| `/camel-migrate` | `camel_catalog_components`<br>`camel_catalog_component_doc` | Search Camel equivalents for Mule components<br>Get component documentation for TDD |
| `/camel-implement` | `camel_catalog_component_doc`<br>`camel_route_context`<br>`camel_validate_route` | Get component configuration<br>Analyze route structure<br>Validate endpoint URIs |
| `/camel-validate` | `camel_validate_route`<br>`camel_route_harden_context` | Validate URIs and options<br>47 automated security checks |
| `/camel-test` | `camel_route_context`<br>`camel_catalog_component_doc` | Analyze route for test strategy<br>Get component details for mocks |

### Benefits

- **60-70% token savings** - AI assistant queries MCP server instead of loading full catalog
- **Always current** - Documentation matches your exact Camel version
- **Better validation** - Catches typos and configuration errors before runtime
- **Security analysis** - Automated checks for credentials, encryption, authentication
- **Faster workflow** - No Maven needed for basic validation

### For More Details

See [MCP Tools Reference](mcp-tools-reference.md) for complete documentation including tool parameters, response schemas, and examples.

---

## Command Cheat Sheet

```bash
# CLI
camel-kit init my-project --ai bob     # Create project with MCP config

# Greenfield slash commands (in AI assistant)
/camel-project                         # Define landscape (optional)
/camel-flow order-ingestion            # Define and design flow

# Migration slash commands (in AI assistant)
/camel-migrate path/to/mule-project/   # Migrate from MuleSoft (or other supported platforms)

# Shared slash commands
/camel-implement order-ingestion       # Generate YAML with MCP validation
/camel-validate                        # Check specs and run security analysis
/camel-test order-ingestion            # Generate tests with validation
```
