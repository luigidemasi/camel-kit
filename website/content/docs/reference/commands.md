---
title: Command Reference
weight: 1
---

## CLI Commands

### camel-kit init

Initialize a new Camel-Kit project.

**Usage:**

```bash
camel-kit init <project-name> [options]
camel-kit init --here [options]
```

**Options:**

| Option | Default | Description |
|--------|---------|-------------|
| `--ai`, `-a` | `bob` | AI coding assistant to configure (bob, gemini, claude) |
| `--camel-version`, `-v` | `4.18.0` | Apache Camel version to target |
| `--citrus-version` | `4.9.2` | Citrus Framework version for test schemas |
| `--here` | `false` | Initialize in current directory |
| `--no-fetch` | `false` | Skip external catalog fetching |
| `--silent` | `false` | Suppress all output — useful for CI/scripted environments |

**Examples:**

```bash
# Create new project for IBM Project Bob
camel-kit init my-integration --ai bob

# Use specific Camel version
camel-kit init my-integration --camel-version 4.18.0

# Initialize in current directory
camel-kit init --here --ai bob

# Skip catalog fetch (faster, offline)
camel-kit init my-integration --ai bob --no-fetch
```

**Output structure:**

```
my-integration/
├── mvnw                         # Maven Wrapper
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
├── .gemini/mcp.json             # Gemini CLI MCP configuration
└── .camel-kit/
    ├── config.yaml              # Project configuration
    ├── constitution.md          # Best practices
    ├── .cache/                  # Downloaded catalogs and schemas
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

**Output:** `.camel-kit/project.md`

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
3. **Processing Steps** - EIPs required; data transformation uses DataMapper/XSLT-saxon by default
4. **Sink System** - Where data goes to
5. **Error Handling** - Dead Letter Channel and retry strategy

**Conditional questions (asked only when relevant):**

- **Data transformation** - If transformation is needed: asks for source/target schemas
- **Circuit Breaker** - Asked only if source or sink is an external HTTP/REST service
- **Idempotent Consumer** - Asked only if source is a message broker or deduplication is needed
- **Transactions** - Asked only if the flow writes to more than one external system

**MCP Tools Used (all MANDATORY when MCP is configured):**
- `camel_catalog_components` + `camel_catalog_component_doc` — before any component is suggested
- `camel_catalog_dataformats` + `camel_catalog_dataformat_doc` — before any data format is chosen
- `camel_catalog_eips` + `camel_catalog_eip_doc` — before any EIP is suggested
- `camel_catalog_languages` + `camel_catalog_language_doc` — before any expression language is chosen

**Output:** `.camel-kit/flows/<flow-name>/<flow-name>.tdd.md`

---

### /camel-migrate

Migrate an existing integration from another platform to Apache Camel.

**Usage:**

```
/camel-migrate
```

**How it works:**

1. Scans all project artifacts (XML, build files, properties, docs, Docker/K8s, source, tests)
2. Detects vendor and version from the full scan content
3. Builds a pre-populated analysis summary extracted from the artifacts
4. Confirms the summary; only asks about genuine gaps
5. Delegates to the vendor sub-skill

**Supported source platforms:**

| Platform | Versions | Detection method |
|----------|---------|-----------------|
| MuleSoft Mule | 3.x, 4.x | XML namespace `mulesoft.org`, `pom.xml` groupId `org.mule` / `com.mulesoft` |

**Output:**

```
.camel-kit/
├── business-requirements.md      # BRD
├── constitution.md
└── flows/
    ├── {flow-name-1}/
    │   └── {flow-name-1}.tdd.md  # TDD per flow
    └── {flow-name-2}/
        └── {flow-name-2}.tdd.md
```

See [Migration Workflow](/docs/getting-started/migration) for details.

---

### /camel-implement

Generate Camel YAML DSL from the flow definition with automated validation.

**Usage:**

```
/camel-implement <flow-name>
```

**Process:**

1. **Load Flow** - Read flow definition and component catalog
2. **Component Lookup** - Verify components exist in catalog (uses MCP `camel_catalog_component_doc`)
3. **Generate YAML** - Transform design to Camel YAML DSL
4. **MCP Validation** - Automatically validate with MCP tools (if available)
5. **Maven Validation** - Run Maven validation loop if needed
6. **Output** - Write to `<flow-name>.camel.yaml`

**Generation constraints:**

| Constraint | Rule |
|-----------|------|
| `unmarshal`/`marshal` | Only when TDD explicitly requires typed Java object processing |
| DataMapper/XSLT | Preferred over `unmarshal` for transformations when schemas available |
| Global `onException` | Must be declared as top-level elements before any `- route:` block |
| Jakarta EE namespaces | When Camel version >= 4.0, `jakarta.*` packages used instead of `javax.*` |
| HTTP header cleanup | `removeHeaders("CamelHttp*")` inserted when route has both HTTP consumer and producer |
| `to` vs `toD` | Use `toD` with `${...}` Simple expressions; `{{...}}` property placeholders safe in `to` |

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
| URI Validation | `CORR-*` | `camel_validate_route` | Valid components, valid options |
| Security | `SEC-*` | `camel_route_harden_context` | Credentials, encryption, authentication |
| Constitution | `CONST-*` | - | Naming, circuit breakers |
| Dependencies | `DEP-*` | - | direct: endpoints, circular deps |

---

### /camel-test

Generate Citrus integration tests for routes with automated validation.

**Usage:**

```
/camel-test <flow-name>      # Generate tests for one flow
/camel-test --all            # Generate tests for all flows
```

**MCP Tools Used:**
- `camel_route_context` - Analyze route to determine test strategy
- `camel_catalog_component_doc` - Get component details for test mocks

**Output:**

- Test file: `test/<flow-name>.camel.it.yaml`
- Test data: `test/data/`
- Dependencies: `test/jbang.properties`
- Test config: `test/application.test.properties`

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

- Rules file: `{flow-name}.wanaku-rules.yaml`

**Deploying to Wanaku:**

```bash
# Upload to Wanaku data store
wanaku data-store add --read-from-file order-api.camel.yaml
wanaku data-store add --read-from-file order-api.wanaku-rules.yaml

# Verify tools are registered
wanaku tools list
```

See [Wanaku MCP Router Documentation](https://wanaku.ai/docs/) for details on the Camel Integration Capability.

---

## Command Cheat Sheet

```bash
# CLI
camel-kit init my-project --ai bob     # Create project with MCP config

# Greenfield slash commands (in AI assistant)
/camel-project                         # Define landscape (optional)
/camel-flow order-ingestion            # Define and design flow

# Migration slash commands (in AI assistant)
/camel-migrate                         # Migrate from MuleSoft

# Shared slash commands
/camel-implement order-ingestion       # Generate YAML with MCP validation
/camel-validate                        # Check specs and run security analysis
/camel-test order-ingestion            # Generate tests with validation

# Wanaku deployment
/camel-wanaku                          # Generate Wanaku rules to expose routes as MCP tools
```
