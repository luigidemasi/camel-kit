# Camel-Kit Architecture Guide

This document describes Camel-Kit's internal architecture for contributors and extenders. For user-facing documentation, see the [User Guide](user-guide.md) and [Command Reference](commands.md).

## Table of Contents

- [Overview](#overview)
- [Skills Architecture](#skills-architecture)
  - [What Are Skills](#what-are-skills)
  - [Skill Structure](#skill-structure)
  - [How Component Knowledge Works](#how-component-knowledge-works)
  - [How Commands Use Skills](#how-commands-use-skills)
  - [Claude Code Skill Standard](#claude-code-skill-standard)
- [MCP Integration (Internal Details)](#mcp-integration-internal-details)
  - [Configuration](#configuration)
  - [Available MCP Tools](#available-mcp-tools)
  - [Tool Usage by Skill](#tool-usage-by-skill)
  - [Detailed Tool Reference](#detailed-tool-reference)
  - [Tool Invocation Flow](#tool-invocation-flow)
  - [Token Savings Statistics](#token-savings-statistics)
- [Skills + MCP: How They Work Together](#skills--mcp-how-they-work-together)
- [Extending Camel-Kit](#extending-camel-kit)
  - [Adding a New Command Skill](#adding-a-new-command-skill)
  - [Adding a New Migration Vendor](#adding-a-new-migration-vendor)
  - [Future Enhancements](#future-enhancements)
- [References](#references)

---

## Overview

Camel-Kit combines two mechanisms to give AI agents accurate, efficient access to the Apache Camel ecosystem:

1. **Skills** — bundled command and workflow skills that guide the AI agent through design, implementation, validation, and testing workflows
2. **MCP Server** — real-time queries against the live Camel catalog for component documentation, validation, and security analysis (on-demand, always current)

The MCP server is the **primary source** of component knowledge. Instead of bundling documentation for all 396+ Camel components (which would bloat context windows), component information is fetched on-demand via MCP tool calls only when the AI agent needs it. This achieves a **99% reduction** in context usage compared to loading all component catalogs upfront.

---

## Skills Architecture

### What Are Skills

Camel Kit uses [Anthropic Agent Skills](https://platform.claude.com/docs/en/agents-and-tools/agent-skills/overview) to provide structured workflows for designing, implementing, and validating Apache Camel integrations.

- **Command skills** (like `camel-flow`, `camel-implement`) are user-invocable slash commands that guide the AI agent through interactive workflows
- **Workflow skills** (like `camel-migrate-mule`) are internal skills loaded by commands to handle specific tasks (e.g., vendor-specific migration logic)
- **Shared skills** (like `datamapper-canonicalize`) provide reusable logic used across multiple workflows

Component-specific knowledge (documentation, configuration options, URI syntax) is **not bundled as skills**. Instead, it is fetched on-demand from the Camel MCP server at runtime via tools like `camel_catalog_component_doc`. This avoids bloating the AI agent's context window with 396+ component catalogs and ensures documentation always matches the target Camel version.

### Skill Structure

Skills are stored in `camel-kit-core/src/main/resources/skills/` and copied to the AI agent's skills folder during `camel-kit init`:

```
{agent-folder}/skills/
├── camel-project/SKILL.md         # Define integration landscape
├── camel-flow/                    # Design a flow
│   ├── SKILL.md
│   └── guides/                    # Sub-guides loaded on-demand
│       ├── integration-patterns.md
│       ├── eip-catalog.md
│       └── ...
├── camel-implement/               # Generate YAML DSL
│   ├── SKILL.md
│   └── guides/
├── camel-validate/                # Validate routes
│   ├── SKILL.md
│   └── guides/
├── camel-test/SKILL.md            # Generate tests
├── camel-migrate/SKILL.md         # Migration orchestrator
├── camel-migrate-mule/            # MuleSoft migration sub-skill
│   ├── SKILL.md
│   └── guides/
└── shared/                        # Shared utilities
    └── datamapper-canonicalize.md
```

Where `{agent-folder}` is:
- `.bob/skills/` for IBM Project Bob
- `.gemini/skills/` for Gemini CLI
- `.claude/skills/` for Claude Code

#### SKILL.md Format

```markdown
---
name: camel-flow
description: Design a Camel integration flow
user-invocable: true
---

# /camel-flow

> Design an integration flow's requirements and technical design.

## Workflow
1. Step one...
2. Step two...

## MCP Integration
- `camel_catalog_components` — search components by category
- `camel_catalog_component_doc` — get component documentation
```

### How Component Knowledge Works

Instead of bundling 396+ component skill files (which would add ~40K+ tokens to the AI agent's context index), Camel-Kit relies on the **MCP server** for all component-specific knowledge:

| Need | MCP Tool | Token Cost |
|------|----------|------------|
| Search components by category | `camel_catalog_components` | ~100 tokens |
| Get component documentation | `camel_catalog_component_doc` | ~200 tokens |
| Get data format options | `camel_catalog_dataformat_doc` | ~200 tokens |
| Get EIP documentation | `camel_catalog_eip_doc` | ~200 tokens |
| Validate endpoint URIs | `camel_validate_route` | ~100 tokens |

**Example flow:**
1. User runs `/camel-flow order-processing`
2. User mentions "Kafka" and "SQL"
3. AI agent calls `camel_catalog_component_doc("kafka")` via MCP → gets full Kafka documentation
4. AI agent calls `camel_catalog_component_doc("sql")` via MCP → gets full SQL documentation
5. Context contains only what's needed (~400 tokens), not all 396 component catalogs

This on-demand approach means component documentation is always current for the target Camel version, and the AI agent's context stays small and focused.

### How Commands Use Skills

#### /camel-flow
When user describes an integration:
1. AI agent reads `camel-flow/SKILL.md` for the workflow
2. Calls MCP `camel_catalog_components` to search for matching components
3. Calls MCP `camel_catalog_component_doc` to get component details
4. Loads sub-guides (e.g., `guides/eip-catalog.md`) only when relevant EIPs are discussed
5. Produces a TDD spec file

#### /camel-implement
When generating YAML:
1. AI agent reads `camel-implement/SKILL.md` for the workflow
2. Reads the TDD spec from `/camel-flow`
3. Calls MCP `camel_catalog_component_doc` for each component's options
4. Generates YAML DSL with correct syntax and parameters
5. Calls MCP `camel_validate_route` to validate the generated route
6. Fixes errors in a validate→fix→retry loop

### Claude Code Skill Standard

Camel Kit follows the [Claude Code Skill Standard](https://code.claude.com/docs/en/skills):

```yaml
---
name: camel-flow
description: Design a Camel integration flow with trigger keywords
user-invocable: true   # User-invocable command
---
```

```yaml
---
name: camel-migrate-mule
description: MuleSoft Mule migration sub-skill
user-invocable: false  # Only loaded by camel-migrate command
---
```

---

## MCP Integration (Internal Details)

For the user-facing overview of MCP (what it does, benefits, how to configure it), see [User Guide — MCP Integration](user-guide.md#mcp-integration). This section documents the internal tool details for contributors.

### Configuration

The MCP server is configured in project-specific configuration files created during `camel-kit init`:

| AI Assistant | Config File |
|-------------|-------------|
| Claude Code | `.mcp.json` |
| IBM Bob | `.bob/mcp.json` |
| Gemini CLI | `.gemini/mcp.json` |

**Content:**
```json
{
  "mcpServers": {
    "camel": {
      "command": "jbang",
      "args": [
        "-Dquarkus.log.level=WARN",
        "org.apache.camel:camel-jbang-mcp:4.18.0:runner"
      ],
      "description": "Apache Camel MCP Server - Component catalog, validation, and security analysis"
    }
  }
}
```

**Created by:** `camel-kit-core/src/main/java/io/github/luigidemasi/camelkit/command/InitCommand.java` — `createMcpConfigs()` (Lines 320-376)

### Available MCP Tools

The Camel MCP server (camel-jbang-mcp:4.18.0) provides **15 tools** organized into 6 categories:

#### 1. Catalog Exploration (8 tools)

| Tool Name | Purpose |
|-----------|---------|
| `camel_catalog_components` | List Camel components with filtering by name, label, runtime |
| `camel_catalog_component_doc` | Get comprehensive component documentation |
| `camel_catalog_dataformats` | List data formats (JSON, XML, CSV, etc.) |
| `camel_catalog_dataformat_doc` | Get data format configuration options |
| `camel_catalog_languages` | List expression languages (Simple, JsonPath, XPath, JQ) |
| `camel_catalog_language_doc` | Get expression language documentation |
| `camel_catalog_eips` | List Enterprise Integration Patterns |
| `camel_catalog_eip_doc` | Get EIP documentation and configuration |

#### 2. Kamelet Catalog (2 tools)

| Tool Name | Purpose |
|-----------|---------|
| `camel_catalog_kamelets` | List available Kamelets with filtering |
| `camel_catalog_kamelet_doc` | Get Kamelet documentation and dependencies |

#### 3. Route Understanding (1 tool)

| Tool Name | Purpose |
|-----------|---------|
| `camel_route_context` | Extract components and EIPs from route (YAML/XML/Java) |

#### 4. Security Analysis (1 tool)

| Tool Name | Purpose |
|-----------|---------|
| `camel_route_harden_context` | Analyze routes for security concerns (47 checks) |

#### 5. Validation and Transformation (2 tools)

| Tool Name | Purpose |
|-----------|---------|
| `camel_validate_route` | Validate endpoint URIs against catalog schema |
| `camel_transform_route` | Convert routes between YAML and XML formats |

#### 6. Version Management (1 tool)

| Tool Name | Purpose |
|-----------|---------|
| `camel_version_list` | List Camel versions with LTS status and JDK requirements |

### Tool Usage by Skill

| Skill | Tools Used | Count |
|-------|------------|-------|
| **camel-project** | `camel_version_list` | 1 |
| **camel-flow** | `camel_catalog_components`, `camel_catalog_component_doc`, `camel_catalog_dataformats`, `camel_catalog_dataformat_doc`, `camel_catalog_eips`, `camel_catalog_eip_doc`, `camel_catalog_languages`, `camel_catalog_language_doc` | 8 |
| **camel-migrate** | Same as camel-flow (Phase 2) | 8 |
| **camel-implement** | `camel_catalog_component_doc`, `camel_catalog_dataformat_doc`, `camel_catalog_eip_doc`, `camel_catalog_language_doc`, `camel_route_context`, `camel_validate_route` | 6 |
| **camel-validate** | `camel_validate_route`, `camel_route_harden_context` | 2 |
| **camel-test** | `camel_route_context`, `camel_catalog_component_doc` | 2 |

### Detailed Tool Reference

#### camel_version_list

List available Camel versions with release dates, JDK requirements, and LTS status.

**Used in:** camel-project (SKILL.md Lines 183, 209)

**Parameters:**
```json
{}                          // Returns all versions
{ "version": "4.18.0" }    // Details for specific version
```

**Example output:**
```
Recent Versions:
  4.18.0 (LTS) - Released 2025-01-15 - JDK 17+ - Recommended
  4.17.0       - Released 2024-12-10 - JDK 17+
  4.16.0 (LTS) - Released 2024-11-05 - JDK 17+
```

**Fallback:** If MCP not available, suggests default version 4.18.0

---

#### camel_catalog_components

Search and list Camel components by category, name, or label.

**Used in:** camel-flow (SKILL.md Lines 169, 300)

**Parameters:**
```json
{
  "category": "messaging",
  "version": "4.18.0"
}
```

**Example output:**
```
Messaging Components:
  - kafka (Apache Kafka messaging)
  - amqp (AMQP 1.0 messaging)
  - jms (JMS messaging)
  - activemq (Apache ActiveMQ)
  - rabbitmq (RabbitMQ)
  - aws2-sqs (AWS Simple Queue Service)
```

**Fallback:** Prompts user to specify component manually

---

#### camel_catalog_component_doc

Retrieve comprehensive documentation for a specific component.

**Used in:** camel-flow, camel-implement, camel-test

**Invocations across skills:**
- **camel-flow** — after user selects source/sink component (Lines 180, 311)
- **camel-implement** — component options lookup and application.properties generation (Lines 177, 196)
- **camel-test** — test mock setup for Testcontainers (Line 177)

**Parameters:**
```json
{
  "name": "kafka",
  "version": "4.18.0"
}
```

**Example output:**
```
Component: kafka
Description: Send and receive messages from Apache Kafka

URI Syntax: kafka:topic

Component Options:
  - brokers (string): Kafka broker addresses
  - groupId (string): Consumer group ID

Endpoint Options:
  - topic (string, required): Topic name
  - autoOffsetReset (string): earliest, latest

Maven: org.apache.camel:camel-kafka:4.18.0
```

**Fallback:** Prompt user to specify component details manually

---

#### camel_validate_route

Validate Camel endpoint URIs against the catalog schema, catch typos and unknown options.

**Used in:** camel-implement, camel-validate

**Invocations across skills:**
- **camel-implement** — pre-validation of source/sink/DLQ URIs (Lines 390-420), complete route validation after generation (Line 867)
- **camel-validate** — URI validation phase for all endpoints (Lines 215-233)

**Parameters (single URI):**
```json
{
  "uri": "kafka:topic-name?brokers=localhost:9092",
  "version": "4.18.0"
}
```

**Parameters (full route):**
```json
{
  "route": "<entire YAML route content>",
  "version": "4.18.0"
}
```

**Example output (valid):**
```
Component: kafka (exists in catalog)
Path parameter: topic-name (valid)
Options: brokers (valid, type: string)
```

**Example output (typo):**
```
Component 'kafak' not found
Did you mean: 'kafka'?
Suggestion: Use 'kafka:test-topic'
```

---

#### camel_route_context

Given a Camel route (YAML, XML, or Java DSL), extracts all components and EIPs used.

**Used in:** camel-implement, camel-test

**Invocations across skills:**
- **camel-implement** — post-generation analysis to verify route structure (Line 855)
- **camel-test** — determine test strategy and mock requirements (Line 122)

**Parameters:**
```json
{
  "route": "<entire YAML content>",
  "version": "4.18.0"
}
```

**Example output:**
```
Components detected: [kafka, sql, http]
EIPs used: [unmarshal, validate, choice, filter]
Data formats: [json]
All components valid for Camel 4.18.0
```

---

#### camel_route_harden_context

Analyze routes for security concerns — 47 automated checks.

**Used in:** camel-validate (SKILL.md Line 471)

**Parameters:**
```json
{
  "route": "<entire YAML route>",
  "version": "4.18.0"
}
```

**Example output:**
```
Security Analysis (47 Checks):

Passed (45):
  - No hardcoded credentials
  - HTTPS used for external calls
  - Input validation present
  ...

Warnings (2):
  Line 12: HTTP instead of HTTPS
    Risk: Unencrypted communication
    Fix: Change http://api.example.com to https://api.example.com

  Line 24: Potential SQL injection risk
    Risk: Direct string concatenation in SQL
    Fix: Use parameterized queries with :#parameter

Score: 45/47 (95.7%)
Risk Level: LOW
```

**Security checks include:** credential exposure, encryption, authentication, input validation (SQL injection, XSS), data exposure in logs, compliance (GDPR, PCI-DSS, HIPAA), error handling (information disclosure).

### Tool Invocation Flow

The **AI agent** invokes MCP tools, NOT the Java code. Here's the complete flow:

```
1. Configuration Phase (during `camel-kit init`)
   ─────────────────────────────────────────────
   User runs: camel-kit init my-project --ai bob
     → InitCommand.java createMcpConfigs()
     → Creates .bob/mcp.json, .mcp.json, .gemini/mcp.json

2. Agent Reads Skill
   ──────────────────
   User runs: /camel-flow order-processing
     → Agent reads .bob/commands/camel-flow.md
     → Skill says: "If MCP available: Use camel_catalog_components"
     → Agent checks: .bob/mcp.json exists → MCP available

3. Agent Starts MCP Server
   ────────────────────────
   Agent executes: jbang -Dquarkus.log.level=WARN \
                     org.apache.camel:camel-jbang-mcp:4.18.0:runner
     → MCP Server starts (Quarkus application)
     → Exposes 15 tools via Model Context Protocol

4. Agent Invokes MCP Tool
   ───────────────────────
   Agent calls: camel_catalog_components { "category": "messaging", "version": "4.18.0" }
     → MCP Server queries Camel 4.18.0 catalog
     → Returns: ["kafka", "amqp", "jms", "rabbitmq", ...]

5. Agent Uses Results
   ──────────────────
   Agent presents: "I found these messaging components: kafka, amqp, jms..."

6. Graceful Fallback
   ──────────────────
   If .bob/mcp.json missing:
     → Skill detects MCP not available
     → Prompts user for component name and details
     → Continues with degraded functionality
```

### Token Savings Statistics

#### By Skill

| Skill | Without MCP | With MCP | Savings |
|-------|-------------|----------|---------|
| camel-project | N/A | N/A | 0% (version list is small) |
| camel-flow | ~3000 tokens | ~1200 tokens | **60%** |
| camel-implement | ~4000 tokens | ~1600 tokens | **60%** |
| camel-validate | ~5000 tokens | ~1500 tokens | **70%** |
| camel-test | ~2500 tokens | ~1250 tokens | **50%** |

**Average token savings:** ~60% across all skills

#### By Tool

| Tool | Skill Count | Total Invocations | Primary Use Case |
|------|-------------|-------------------|------------------|
| `camel_catalog_component_doc` | 3 | ~8 | Component documentation |
| `camel_validate_route` | 2 | ~8 | URI and route validation |
| `camel_catalog_components` | 1 | ~2 | Component discovery |
| `camel_route_context` | 2 | ~2 | Route analysis |
| `camel_route_harden_context` | 1 | ~1 | Security analysis |
| `camel_version_list` | 1 | ~2 | Version management |

**Total invocations per workflow:** ~23 MCP tool calls

---

## Skills + MCP: How They Work Together

Skills provide **workflow structure** (what steps to follow, what questions to ask, what artifacts to produce), while MCP provides **component knowledge** (documentation, validation, security analysis). Neither mechanism works well alone:

| Use Case | Mechanism | Token Cost | Why |
|----------|-----------|------------|-----|
| Workflow guidance | Skills | ~2-5k tokens per command | Structured step-by-step process |
| Component search | MCP | ~100 tokens per query | On-demand, version-specific |
| Component docs | MCP | ~200 tokens per query | Full documentation without bundling |
| URI validation | MCP | ~100 tokens per call | Real-time correctness checks |
| Security analysis | MCP | ~300 tokens per call | 47 automated checks |

**Token efficiency:**

| Approach | Token Cost |
|----------|------------|
| Without skills or MCP | ~500k tokens (all 396 component catalogs loaded upfront) |
| With MCP only (current) | ~1-6k tokens (on-demand queries for only the components needed) |
| **Overall savings** | **99% reduction** |

**Example: Full workflow for a Kafka-to-SQL integration**

| Phase | What happens | Tokens |
|-------|-------------|--------|
| Flow design (`/camel-flow`) | Skill guides the interview; MCP `camel_catalog_components` searches by category, `camel_catalog_component_doc` retrieves Kafka and SQL docs | ~300 |
| Implementation (`/camel-implement`) | Skill guides YAML generation; MCP `camel_catalog_component_doc` for component options, `camel_route_context` + `camel_validate_route` for validation | ~800 |
| Validation (`/camel-validate`) | Skill guides the checklist; MCP `camel_route_harden_context` runs 47 security checks | ~300 |
| **Total** | | **~1,400 tokens** |

---

## Extending Camel-Kit

### Adding a New Command Skill

1. Create a skill directory in `camel-kit-core/src/main/resources/skills/`:

```
skills/camel-yourcommand/
├── SKILL.md              # Main workflow with YAML frontmatter
└── guides/               # Optional sub-guides loaded on-demand
    └── specific-topic.md
```

2. Follow the SKILL.md format (see [Skill Structure](#skill-structure))
3. Add MCP tool usage instructions for any component lookups or validation
4. Register the command in `InitCommand.createCommandTemplates()`
5. Update `docs/commands.md`

### Adding a New Migration Vendor

1. Create a vendor sub-skill: `skills/camel-migrate-{vendor}/SKILL.md`
2. Add vendor-specific guides in `skills/camel-migrate-{vendor}/guides/`
3. Update the orchestrator (`skills/camel-migrate/SKILL.md`) with detection rules
4. Follow the two-phase pattern: Phase 1 (Business Analyst) → Phase 2 (Integration Architect)
5. See `camel-migrate-mule/` for the reference implementation

### Future Enhancements

- **Skill Recommendations** — add command intelligence to suggest components ("For message queues, consider: kafka, jms, or amqp")

---

## References

- [Anthropic Agent Skills](https://platform.claude.com/docs/en/agents-and-tools/agent-skills/overview)
- [Claude Code Skills](https://code.claude.com/docs/en/skills)
- [Apache Camel Components](https://camel.apache.org/components/latest/)
- [Camel Component Catalog](https://camel.apache.org/manual/component-dsl.html)
- [Official Camel MCP Documentation](https://raw.githubusercontent.com/apache/camel-website/refs/heads/main/content/blog/2026/02/camel-jbang-mcp/index.md)
- [MCP Specification](https://modelcontextprotocol.io/)
- [Camel JBang Documentation](https://camel.apache.org/manual/camel-jbang.html)

---

**Last Updated:** 2026-03-11
**Camel Version:** 4.18.0
**MCP Server:** camel-jbang-mcp:4.18.0
